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

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fleetpin.graphql.aws.lambda.admin.Admin;
import com.fleetpin.graphql.aws.lambda.admin.User;
import com.fleetpin.graphql.aws.lambda.subscription.*;
import com.fleetpin.graphql.database.manager.dynamo.DynamoDbManager;
import com.google.common.annotations.VisibleForTesting;
import graphql.GraphQL;
import graphql.GraphQL.Builder;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public abstract class LambdaSubscriptionControl<U extends User> implements RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent>{

	private final ApiGatewayManagementApiClient gatewayApi;
	private final DynamoDbManager manager;
	private final Admin<U> admin;

	public LambdaSubscriptionControl(
			final String subscriptionTable,
			final String gatewayUri
	) throws Exception {
		this(subscriptionTable, gatewayUri, Collections.emptyMap());
	}

	public LambdaSubscriptionControl(
			final String subscriptionTable,
			final String gatewayUri,
			final Map<String, String> subscriptionNameMapping
	) throws Exception {
		prepare();
		this.manager = builderManager();
		final GraphQL graph = buildGraphQL().subscriptionExecutionStrategy(new InterceptExecutionStrategy()).build();

		if (gatewayUri == null) {
			gatewayApi = null;
		} else {
			URI endpoint = new URI(gatewayUri);
			this.gatewayApi = ApiGatewayManagementApiClient.builder().endpointOverride(endpoint).build();
		}

		this.admin = new Admin.AdminBuilder<U>()
				.withGraph(graph)
				.withSubscriptionTable(subscriptionTable)
				.withManager(manager)
				.withSubscriptionNameMapping(subscriptionNameMapping)
				.withLastSeenTimeout(Long.parseLong(
						System.getenv("LAST_SEEN_TIMEOUT") != null ?
								System.getenv("LAST_SEEN_TIMEOUT") :
								Duration.ofMinutes(15).toMillis() + ""
						)
				)
				.build();

	}

	@Override
	public APIGatewayV2ProxyResponseEvent handleRequest(APIGatewayV2ProxyRequestEvent input, Context context) {
		try {
			switch(input.getRequestContext().getEventType()) {
			case "CONNECT" :
				break;
				case "DISCONNECT" :

				disconnect(input);
				break;

			case "MESSAGE": 
				SubscriptionMessage<?> graphQuery = manager.getMapper().readValue(input.getBody(), SubscriptionMessage.class);
				process(input.getRequestContext().getConnectionId(), graphQuery);
				break;

			default: throw new RuntimeException("unknown event " + input.getRequestContext().getEventType());
			}

			var toReturn = new APIGatewayV2ProxyResponseEvent();
			toReturn.setStatusCode(200);
			return toReturn;	


		}catch (Exception e) {
			throw new RuntimeException(e);
		}finally {
			LambdaCache.evict();
		}
	}

	private void disconnect(final APIGatewayV2ProxyRequestEvent input) throws InterruptedException, ExecutionException {
		admin.disconnect(input.getRequestContext().getConnectionId());
	}


	@VisibleForTesting
	public void process(String connectionId, SubscriptionMessage<?> graphQuery) throws JsonProcessingException, InterruptedException, ExecutionException {
		if (graphQuery instanceof SubscriptionConnectionInit) {
			final var authHeader = ((SubscriptionConnectionInit) graphQuery).getPayload().getAuthorization();
			final var userContext = validateUser(authHeader);
			final var message = admin.connect(connectionId, userContext.get());

			if (message != null) {
				sendMessage(connectionId, manager.getMapper().writeValueAsString(message));
			}
		} else if (graphQuery instanceof SubscriptionStart) {
			final var query = ((SubscriptionStart) graphQuery).getPayload();
			final var id = graphQuery.getId();
			final var message = admin.subscribe(connectionId, id, query, this::buildSubscriptionId);

			if (message != null) {
				sendMessage(connectionId, manager.getMapper().writeValueAsString(message));
			}
		} else if (graphQuery instanceof SubscriptionStop) {
			admin.unsubscribe(connectionId, graphQuery.getId());
		} else if (graphQuery instanceof SubscriptionTerminate) {
			admin.disconnect(connectionId);
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
	public abstract String buildSubscriptionId(String subscription, Map<String, Object> variables);

}