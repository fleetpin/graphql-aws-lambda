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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionConnectionInit;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseAccept;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseConnectionError;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseError;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionStart;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionStop;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionTerminate;
import com.fleetpin.graphql.aws.lambda.subscription.WebsocketMessage;
import com.fleetpin.graphql.dynamodb.manager.DynamoDbManager;
import com.google.common.annotations.VisibleForTesting;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQL.Builder;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public abstract class LambdaSubscriptionControl<U> implements RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent>{


	private final ApiGatewayManagementApiClient gatewayApi;
	private final GraphQL graph;
	private final String subscriptionTable;
	private final DynamoDbManager manager;

	public LambdaSubscriptionControl(String subscriptionTable, String gatewayUri) throws Exception {
		prepare();
		this.manager = builderManager();
		this.graph = buildGraphQL().subscriptionExecutionStrategy(new InterceptExecutionStrategy()).build();
		this.subscriptionTable = subscriptionTable;
		if(gatewayUri == null) {
			gatewayApi = null;
		}else {
			URI endpoint = new URI(gatewayUri);
			this.gatewayApi = ApiGatewayManagementApiClient.builder().endpointOverride(endpoint).build();
		}
	}




	@Override
	public APIGatewayV2ProxyResponseEvent handleRequest(APIGatewayV2ProxyRequestEvent input, Context context) {
		try {
			switch(input.getRequestContext().getEventType()) {
			case "CONNECT" :  {
				break;
			}
			case "DISCONNECT" :

				Map<String, AttributeValue> keyConditions = new HashMap<>();
				keyConditions.put(":connectionId", AttributeValue.builder().s(input.getRequestContext().getConnectionId()).build());

				List<Map<String, AttributeValue>> items = manager.getDynamoDbAsyncClient().query(t -> t.tableName(subscriptionTable).keyConditionExpression("connectionId = :connectionId").expressionAttributeValues(keyConditions)).get().items();

				for(var item: items) {
					var key = new HashMap<>(item);
					key.keySet().retainAll(Arrays.asList("connectionId", "id"));
					manager.getDynamoDbAsyncClient().deleteItem(t -> t.tableName(subscriptionTable).key(key));
				}
				break;

			case "MESSAGE": 
				WebsocketMessage<?> graphQuery = manager.getMapper().readValue(input.getBody(), WebsocketMessage.class);
				process(input.getRequestContext().getConnectionId(), graphQuery);
				break;

			default: throw new RuntimeException("unknown event " + input.getRequestContext().getEventType());
			}

			var toReturn = new APIGatewayV2ProxyResponseEvent();
			toReturn.setStatusCode(200);
			return toReturn;	


		}catch (Exception e) {
			throw new RuntimeException(e);
		}
	}



	@VisibleForTesting
	public void process(String connectionId, WebsocketMessage<?> graphQuery) throws JsonProcessingException, InterruptedException, ExecutionException {
		if(graphQuery instanceof SubscriptionConnectionInit) {
			String response = "";
			try {
				String authHeader = ((SubscriptionConnectionInit) graphQuery).getPayload().getAuthorization();
				CompletableFuture<U> userContext = validateUser(authHeader);
				U user = userContext.get();
				if(user != null) {
					Map<String, AttributeValue> item = new HashMap<>();
					item.put("connectionId", AttributeValue.builder().s(connectionId).build());
					item.put("id", AttributeValue.builder().s("auth").build());
					item.put("user", AttributeValue.builder().s(extractUserId(user)).build());

					var additional = extraUserInfo(user);
					if(additional != null) {
						item.put("aditional", additional);
					}
					item.put("ttl", AttributeValue.builder().n(Long.toString(Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli())).build()); //if connection still there in a week just delete
					manager.getDynamoDbAsyncClient().putItem(t -> t.tableName(subscriptionTable).item(item)).get();
					response = manager.getMapper().writeValueAsString(new SubscriptionResponseAccept());
				}else {
					response = manager.getMapper().writeValueAsString(new SubscriptionResponseConnectionError("No token"));
				}
			} catch (Exception e) {
				e.printStackTrace();
				response = manager.getMapper().writeValueAsString(new SubscriptionResponseConnectionError(e.getMessage()));
			}
			final String sendResponse = response;
			sendMessage(connectionId,sendResponse);

		}else if(graphQuery instanceof SubscriptionStart) {
			try {
				GraphQLQuery query = ((SubscriptionStart) graphQuery).getPayload();
				ExecutionResult result = graph.execute(builder -> builder.query(query.getQuery()).operationName(query.getOperationName()).variables(query.getVariables()));
				String subscription = result.getData();
				subscription = mapName(subscription);
				Map<String, AttributeValue> item = new HashMap<>();
				item.put("connectionId", AttributeValue.builder().s(connectionId).build());
				item.put("id", AttributeValue.builder().s(graphQuery.getId()).build());
				item.put("subscription", AttributeValue.builder().s(subscription + ":" + buildSubscriptionId(subscription, query.getVariables())).build());
				item.put("query", manager.toAttributes(query));
				item.put("ttl", AttributeValue.builder().n(Long.toString(Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli())).build()); //if connection still there in a week just delete
				manager.getDynamoDbAsyncClient().putItem(t -> t.tableName(subscriptionTable).item(item)).get();
			} catch (Exception e) {
				GraphQLError error = GraphqlErrorBuilder.newError().message(e.getMessage()).build();
				String response = manager.getMapper().writeValueAsString(new SubscriptionResponseError(graphQuery.getId(), error));
				sendMessage(connectionId, response);

			}

		}else if(graphQuery instanceof SubscriptionStop) {
			Map<String, AttributeValue> item = new HashMap<>();
			item.put("connectionId", AttributeValue.builder().s(connectionId).build());
			item.put("id", AttributeValue.builder().s(graphQuery.getId()).build());
			manager.getDynamoDbAsyncClient().deleteItem(t -> t.tableName(subscriptionTable).key(item)).get();

		}else if(graphQuery instanceof SubscriptionTerminate) {
			Map<String, AttributeValue> keyConditions = new HashMap<>();
			keyConditions.put(":connectionId", AttributeValue.builder().s(connectionId).build());

			List<Map<String, AttributeValue>> items = manager.getDynamoDbAsyncClient().query(t -> t.tableName(subscriptionTable).keyConditionExpression("connectionId = :connectionId").expressionAttributeValues(keyConditions)).get().items();

			for(var item: items) {
				manager.getDynamoDbAsyncClient().deleteItem(t -> t.tableName(subscriptionTable).key(item)).get();
			}
		}

	}




	@VisibleForTesting
	protected void sendMessage(String connectionId, String message) {
		gatewayApi.postToConnection(b -> b.connectionId(connectionId).data(SdkBytes.fromString(message, StandardCharsets.UTF_8)));
	}
	
	protected abstract void prepare() throws Exception;
	protected abstract Builder buildGraphQL() throws Exception;
	protected abstract DynamoDbManager builderManager();
	
	public abstract CompletableFuture<U> validateUser(String authHeader);
	public abstract String extractUserId(U user);
	public abstract AttributeValue extraUserInfo(U user);
	public abstract String buildSubscriptionId(String subscription, Map<String, Object> variables);


	public String mapName(String queryName) {
		return queryName;
	}



	
}