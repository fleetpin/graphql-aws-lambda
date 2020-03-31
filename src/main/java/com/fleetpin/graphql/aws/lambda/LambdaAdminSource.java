package com.fleetpin.graphql.aws.lambda;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fleetpin.graphql.aws.lambda.admin.Admin;
import com.fleetpin.graphql.aws.lambda.admin.User;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionPing;
import com.fleetpin.graphql.database.manager.dynamo.DynamoDbManager;
import graphql.GraphQL;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiAsyncClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;

public abstract class LambdaAdminSource<U extends User, E> implements RequestHandler<E, Void> {

    private final Admin<U> admin;
    private final DynamoDbManager manager;
    private final ApiGatewayManagementApiAsyncClient gatewayApi;

    public LambdaAdminSource(final String subscriptionTable, final String gatewayUri) throws Exception {
        final GraphQL graph = buildGraphQL()
                .subscriptionExecutionStrategy(new InterceptExecutionStrategy())
                .build();

        manager = builderManager();

        gatewayApi = ApiGatewayManagementApiAsyncClient
                .builder()
                .endpointOverride(new URI(gatewayUri))
                .build();

        admin = new Admin.AdminBuilder<U>()
                .withGraph(graph)
                .withSubscriptionTable(subscriptionTable)
                .withManager(manager)
                .withLastSeenTimeout(Long.parseLong(System.getenv("LAST_SEEN_TIMEOUT")))
                .build();
    }

    protected Admin<U> getAdmin() {
        return admin;
    }

    protected void pingConnection(final String connectionId) {
        gatewayApi
                .postToConnection(b -> b
                        .connectionId(connectionId)
                        .data(SdkBytes.fromString(getPingMessage(), StandardCharsets.UTF_8))
                );
    }

    private String getPingMessage() {
        try {
            return manager.getMapper().writeValueAsString(new SubscriptionPing());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract GraphQL.Builder buildGraphQL() throws Exception;
    protected abstract DynamoDbManager builderManager();

}
