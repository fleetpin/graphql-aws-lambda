package com.fleetpin.graphql.aws.lambda;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fleetpin.graphql.aws.lambda.admin.Admin;
import com.fleetpin.graphql.aws.lambda.admin.User;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseKeepAlive;
import com.fleetpin.graphql.database.manager.dynamo.DynamoDbManager;
import graphql.GraphQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiAsyncClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public abstract class LambdaAdminSource<U extends User, E> implements RequestHandler<E, Void> {

    private static final Logger logger = LoggerFactory.getLogger(LambdaAdminSource.class);

    private final Admin<U> admin;
    private final DynamoDbManager manager;
    private final ApiGatewayManagementApiAsyncClient gatewayApi;
    private final long sentMessageTimeout;

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
                .withLastSeenTimeout(Long.parseLong(System.getenv(Constants.ENV_LAST_SEEN_TIMEOUT)))
                .build();

        sentMessageTimeout = Long.parseLong(System.getenv(Constants.ENV_SENT_MESSAGE_TIMEOUT));
    }

    protected Admin<U> getAdmin() {
        return admin;
    }

    protected CompletableFuture<Void> pingConnection(final String connectionId) {
        return gatewayApi
                .postToConnection(b -> b
                        .overrideConfiguration(c -> c
                                .apiCallTimeout(Duration.ofMillis(sentMessageTimeout))
                                .apiCallAttemptTimeout(Duration.ofMillis(sentMessageTimeout))
                        )
                        .connectionId(connectionId)
                        .data(SdkBytes.fromString(getPingMessage(), StandardCharsets.UTF_8))
                )
                .exceptionally(error -> {
                    logger.warn("Disconnecting {}", connectionId);

                    try {
                        admin.disconnect(connectionId);
                    } catch (ExecutionException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }

                    return null;
                })
                .thenAccept(response -> {
                    if (response == null) {
                        return;
                    }

                    admin.verified(connectionId);
                });
    }

    private String getPingMessage() {
        try {
            return manager.getMapper().writeValueAsString(new SubscriptionResponseKeepAlive());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected abstract GraphQL.Builder buildGraphQL() throws Exception;
    protected abstract DynamoDbManager builderManager();

}
