/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.fleetpin.graphql.aws.lambda;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.reactivestreams.Publisher;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseData;
import com.fleetpin.graphql.dynamodb.manager.DynamoDbManager;
import com.google.common.annotations.VisibleForTesting;

import graphql.ExecutionResult;
import graphql.GraphQL;
import io.reactivex.rxjava3.core.Flowable;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiAsyncClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionResponse;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

public abstract class LambdaSubscriptionSource<E, T> implements RequestHandler<E, Void>{

	private final DynamoDbManager manager;
	private final ApiGatewayManagementApiAsyncClient gatewayApi;
	private final GraphQL graph;

	
	private final LambdaCache<String, CompletableFuture<GetItemResponse>> userCache;
	private final LambdaCache<String, CompletableFuture<QueryResponse>> organisationCache;
	private final String subscriptionTable;
	
	

	public LambdaSubscriptionSource(String subscriptionId, String subscriptionTable, String apiUri) throws Exception {
		prepare();
		this.subscriptionTable = subscriptionTable;
		this.manager = builderManager();
		this.graph = buildGraphQL();
		if(apiUri ==null) {
			gatewayApi = null;
		}else {
			URI endpoint = new URI(apiUri);
			this.gatewayApi = ApiGatewayManagementApiAsyncClient.builder().endpointOverride(endpoint).build();
		}
		
		//TODO: make configurable
		organisationCache = new LambdaCache<>(Duration.ofMinutes(2), lookupId -> {
			Map<String, AttributeValue> keyConditions = new HashMap<>();
			keyConditions.put(":subscription", AttributeValue.builder().s(subscriptionId + ":" + lookupId).build());
			return manager.getDynamoDbAsyncClient().query(t -> t.tableName(subscriptionTable).indexName("subscription").keyConditionExpression("subscription = :subscription").expressionAttributeValues(keyConditions));	
				
		});



		userCache = new LambdaCache<>(Duration.ofSeconds(20), connectionId -> {
    		Map<String, AttributeValue> key = new HashMap<>();
    		key.put("connectionId", AttributeValue.builder().s(connectionId).build());
    		key.put("id", AttributeValue.builder().s("auth").build());
    		return manager.getDynamoDbAsyncClient().getItem(t -> t.tableName(subscriptionTable).key(key));
		});					
		
	}

	protected abstract void prepare() throws Exception;
	protected abstract GraphQL buildGraphQL() throws Exception;
	protected abstract DynamoDbManager builderManager();
	

	public abstract CompletableFuture<ContextGraphQL> buildContext(Flowable<T> publisher, String userId, AttributeValue additionalUserInfo, Map<String, Object> variables);
	public abstract String buildSubscriptionId(T type);
	

	@VisibleForTesting
	protected CompletableFuture<?> process(T t) throws InterruptedException, ExecutionException, IOException {



		return organisationCache.get(buildSubscriptionId(t)).thenCompose(items -> {
			List<CompletableFuture<Void>> parts = new ArrayList<>();
			for(var item: items.items()) {
				String connectionId = item.get("connectionId").s();
				String id = item.get("id").s();
				GraphQLQuery query = manager.convertTo(item.get("query"), GraphQLQuery.class);
				parts.add(processUpdate(connectionId, id, query, t));
			}
			return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new));
		});

	}



	private CompletableFuture<Void> processUpdate(String connectionId, String id, GraphQLQuery query, T t) {
		return userCache.get(connectionId).thenCompose(user -> {
			if(user.item() == null || user.item().isEmpty()) {
				//not authenticated
				return CompletableFuture.completedFuture(null);
			}
			Flowable<T> publisher = Flowable.fromCallable(() -> t);


			return buildContext(publisher, user.item().get("user").s(), user.item().get("aditional"), query.getVariables()).thenCompose(context -> {
				CompletableFuture<ExecutionResult> toReturn = graph.executeAsync(builder -> builder.query(query.getQuery()).operationName(query.getOperationName()).variables(query.getVariables()).context(context));
				context.start(toReturn);
				return toReturn.thenCompose(r -> {
					if(!r.getErrors().isEmpty()) {
						try {
							SubscriptionResponseData data = new SubscriptionResponseData(id, r);
							String sendResponse = manager.getMapper().writeValueAsString(data);
							sendMessage(connectionId, sendResponse);
						} catch (JsonProcessingException e) {
							throw new UncheckedIOException(e);
						}


						return CompletableFuture.completedFuture(null);
					}
					Publisher<ExecutionResult> stream = r.getData();
					CompletableFuture<?> future = new CompletableFuture<>();
					context.start(future); //TODO: seems slightly odd placement think should be lower
					var item = Flowable.fromPublisher(stream).blockingFirst();
					SubscriptionResponseData data = new SubscriptionResponseData(id, item);
					try {
						String sendResponse = manager.getMapper().writeValueAsString(data);
						return sendMessage(connectionId, sendResponse).handle((response, error) -> {
							future.complete(null);
							if(error != null) {
								if(error instanceof GoneException || error.getCause() instanceof GoneException) {
									//delete user info
									return deleteUser(user);
								}
							}
							return future;
						}).thenCompose(promise -> promise).thenApply(__ -> null);
					} catch (JsonProcessingException e) {
						throw new UncheckedIOException(e);
					}
				});
			});

		});



	}


	private CompletableFuture<Void> deleteUser(GetItemResponse user) {
		Map<String, AttributeValue> keyConditions = new HashMap<>();
		keyConditions.put(":connectionId", user.item().get("connectionId"));

		return manager.getDynamoDbAsyncClient().query(t -> t.tableName(subscriptionTable)
				.keyConditionExpression("connectionId = :connectionId").expressionAttributeValues(keyConditions))
				.thenCompose(items -> {
					
					var futures = items.items().stream().map(item -> {
						var key = new HashMap<>(item);
						key.keySet().retainAll(Arrays.asList("connectionId", "id"));
						return manager.getDynamoDbAsyncClient().deleteItem(t -> t.tableName(subscriptionTable).key(key));
					}).toArray(CompletableFuture[]::new);
					return CompletableFuture.allOf(futures);
				});

		
	}

	@VisibleForTesting
	protected CompletableFuture<PostToConnectionResponse> sendMessage(String connectionId, String sendResponse) {
		return gatewayApi.postToConnection(b -> b.connectionId(connectionId).data(SdkBytes.fromString(sendResponse , StandardCharsets.UTF_8)));
	}



}
