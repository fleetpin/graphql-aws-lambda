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

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fleetpin.graphql.builder.SchemaBuilder;
import com.google.common.annotations.VisibleForTesting;
import graphql.GraphQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;

public abstract class LambdaGraphQL<U, C extends ContextGraphQL> implements RequestHandler<APIGatewayV2ProxyRequestEvent,
        APIGatewayV2ProxyResponseEvent> {
    private static final Logger logger = LoggerFactory.getLogger(LambdaGraphQL.class);
    private static final Map<String, String> RESPONSE_HEADERS = Map.of(
            "Access-Control-Allow-Origin", "*",
            "content-type", "application/json; charset=utf-8"
    );

    private final ObjectMapper mapper;
    private GraphQL build;

    public LambdaGraphQL() throws Exception {
        this.build = buildGraphQL();
        this.mapper = builderObjectMapper();
    }

    @Override
    public APIGatewayV2ProxyResponseEvent handleRequest(
            final APIGatewayV2ProxyRequestEvent input,
            final com.amazonaws.services.lambda.runtime.Context context // Gets confused with ContextGraphQL otherwise
    ) {
        try {
            final var query = mapper.readValue(input.getBody(), GraphQLQuery.class);

            final var user = validate(input.getHeaders().get(AUTHORIZATION)).get();

            final C graphContext = buildContext(user, query);
            final var queryResponse = build.executeAsync(builder -> builder.query(query.getQuery())
                    .operationName(query.getOperationName())
                    .variables(query.getVariables())
                    .context(graphContext));
            graphContext.start(queryResponse);

            final ObjectNode serializedQueryResponse = mapper.valueToTree(queryResponse.get());
            if (serializedQueryResponse.get(Constants.GRAPHQL_ERRORS_FIELD).isEmpty()) {
                serializedQueryResponse.remove(Constants.GRAPHQL_ERRORS_FIELD);
            }

            final var response = new APIGatewayV2ProxyResponseEvent();
            response.setStatusCode(200);
            response.setHeaders(RESPONSE_HEADERS);
            response.setBody(serializedQueryResponse.toString());

            return response;
        } catch (final ExecutionException e) {
            logger.error("Failed to validate user", e);

            final var badResponse = new APIGatewayV2ProxyResponseEvent();
            badResponse.setStatusCode(200);

            final var response = new ObjectNode(JsonNodeFactory.instance);
            response.putArray(Constants.GRAPHQL_ERRORS_FIELD).add(Constants.GRAPHQL_ACCESS_DENIED);

            badResponse.setBody(response.toString());

            return badResponse;
        } catch (final Throwable e) {
            logger.error("Failed to invoke graph", e);
            throw new RuntimeException(e);
        } finally {
            LambdaCache.evict();
        }
    }

    protected ObjectMapper builderObjectMapper() {
        return SchemaBuilder.MAPPER;
    }

    protected abstract GraphQL buildGraphQL() throws Exception;

    protected abstract CompletableFuture<U> validate(String authHeader);

    protected abstract C buildContext(U user, GraphQLQuery query);

    @VisibleForTesting
    protected void updateGraphQL() throws Exception {
        this.build = buildGraphQL();
    }
}
