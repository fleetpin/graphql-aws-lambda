package com.fleetpin.graphql.aws.lambda.admin;

import com.fleetpin.graphql.aws.lambda.GraphQLQuery;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionMessage;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseAccept;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseConnectionError;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseError;
import com.fleetpin.graphql.database.manager.dynamo.DynamoDbManager;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.fleetpin.graphql.aws.lambda.Constants.*;

/**
 * Manages the administration functions of users subscriptions
 */
public class Admin<U extends User> {

    private final GraphQL graph;
    private final String subscriptionTable;
    private final DynamoDbManager manager;
    private final long lastSeenTimeout;
    private final Map<String, String> subscriptionNameMapping;

    public Admin(
            final GraphQL graph,
            final String subscriptionTable,
            final DynamoDbManager manager,
            final long lastSeenTimeout
    ) {
        this.graph = graph;
        this.subscriptionTable = subscriptionTable;
        this.manager = manager;
        this.lastSeenTimeout = lastSeenTimeout;
        this.subscriptionNameMapping = Collections.emptyMap();
    }

    public Admin(
            final GraphQL graph,
            final String subscriptionTable,
            final DynamoDbManager manager,
            final long lastSeenTimeout,
            final Map<String, String> subscriptionNameMapping
    ) {
        this.graph = graph;
        this.subscriptionTable = subscriptionTable;
        this.manager = manager;
        this.lastSeenTimeout = lastSeenTimeout;
        this.subscriptionNameMapping = subscriptionNameMapping;
    }

    /**
     * Registers the user as connected
     *
     * @param connectionId the id of the connection
     * @param user the user
     *
     * @return the response message
     */
    public SubscriptionMessage<Object> connect(final String connectionId, final U user) {
        try {
            if (user != null) {
                final Map<String, AttributeValue> item = new HashMap<>();

                item.put(CONNECTION_ID, AttributeValue.builder().s(connectionId).build());
                item.put(ID, AttributeValue.builder().s(AUTH).build());
                item.put(USER, AttributeValue.builder().s(user.getId()).build());

                final var additional = user.getExtraUserInfo();

                if (additional != null) {
                    item.put(ADDITIONAL, additional);
                }

                item.put(
                                TTL,
                                AttributeValue
                                        .builder()
                                        .n(Long
                                                .toString(Instant.now().plus(7, ChronoUnit.DAYS)
                                                        .toEpochMilli()))
                                        .build()
                        ); // if connection still there in a week just delete

                item.put(
                        LAST_SEEN,
                        AttributeValue.builder().n(Long.toString(Instant.now().toEpochMilli())).build()
                );

                manager.getDynamoDbAsyncClient().putItem(t -> t.tableName(subscriptionTable).item(item)).get();

                return new SubscriptionResponseAccept();
            } else {
                return new SubscriptionResponseConnectionError("No token");
            }
        } catch (Exception e) {
            return new SubscriptionResponseConnectionError(e.getMessage());
        }
    }

    /**
     * checks if a connection is there
     *
     * @param connectionId the id of the connection
     *
     * @return true if connected
     */
    public boolean isConnected(final String connectionId) throws ExecutionException, InterruptedException {
        final Map<String, AttributeValue> keyConditions = new HashMap<>();

        keyConditions.put(":" + CONNECTION_ID, AttributeValue
                .builder()
                .s(connectionId)
                .build());

        final var request = QueryRequest
                .builder()
                .tableName(subscriptionTable)
                .keyConditionExpression(CONNECTION_ID + " = :" + CONNECTION_ID)
                .expressionAttributeValues(keyConditions)
                .build();

        final var response = manager.getDynamoDbAsyncClient().query(request).get();

        return !response.items().isEmpty();
    }

    /**
     * Removes all subscriptions for this connection
     *
     * @param connectionId the id of the connection
     */
    public void disconnect(final String connectionId) throws ExecutionException, InterruptedException {
        final List<Map<String, AttributeValue>> items = getConnections(connectionId);

        for(var item: items) {
            final var key = new HashMap<>(item);

            key.keySet().retainAll(Arrays.asList(CONNECTION_ID, ID));

            manager.getDynamoDbAsyncClient().deleteItem(t -> t.tableName(subscriptionTable).key(key)).get();
        }
    }

    /**
     * Subscribes the user to a subscription
     *
     * @param connectionId the id of the connection
     * @param queryId the id of the graph query
     * @param query the graph query
     *
     * @return the response message
     */
    public SubscriptionMessage<?> subscribe(
            final String connectionId,
            final String queryId,
            final GraphQLQuery query,
            final SubscriptionIdBuilder idBuilder
    ) {
        try {
            final ExecutionResult result = graph
                    .execute(
                            builder -> builder
                                    .query(query.getQuery())
                                    .operationName(query.getOperationName())
                                    .variables(query.getVariables())
                    );

            if (!result.getErrors().isEmpty()) {
                final GraphQLError error = result.getErrors().get(0); // might hide other errors but can then be worked through

                return new SubscriptionResponseError(queryId, error);
            } else {
                final String subscription = mapSubscriptionName(result.getData());
                final Map<String, AttributeValue> item = new HashMap<>();

                item.put(CONNECTION_ID, AttributeValue.builder().s(connectionId).build());
                item.put(ID, AttributeValue.builder().s(queryId).build());
                item.put(SUBSCRIPTION, AttributeValue.builder().s(subscription + ":" + idBuilder.build(subscription, query.getVariables())).build());
                item.put(QUERY, manager.toAttributes(query));
                item.put(TTL, AttributeValue.builder().n(Long.toString(Instant.now().plus(7, ChronoUnit.DAYS).toEpochMilli())).build()); //if connection still there in a week just delete
                item.put(LAST_SEEN, AttributeValue.builder().n(Long.toString(Instant.now().toEpochMilli())).build());

                manager.getDynamoDbAsyncClient().putItem(t -> t.tableName(subscriptionTable).item(item)).get();
            }
        } catch (Exception e) {
            final GraphQLError error = GraphqlErrorBuilder.newError().message(e.getMessage()).build();

            return new SubscriptionResponseError(queryId, error);
        }

        return null;
    }

    /**
     * checks if user is subscribed
     *
     * @param connectionId the connection id
     * @param queryId the id of the subscription query
     *
     * @return true is user is subscribed
     */
    public boolean isSubscribed(final String connectionId, final String queryId) throws ExecutionException, InterruptedException {
        final Map<String, AttributeValue> item = new HashMap<>();

        item.put(CONNECTION_ID, AttributeValue.builder().s(connectionId).build());
        item.put("id", AttributeValue.builder().s(queryId).build());

        final var result = manager.getDynamoDbAsyncClient().getItem(t -> t.tableName(subscriptionTable).key(item)).get();

        return result.hasItem();
    }

    /**
     * Unsubscribes user from a particular subscription
     *
     * @param connectionId the connection id
     * @param queryId the query id
     */
    public void unsubscribe(final String connectionId, final String queryId) throws ExecutionException, InterruptedException {
        final Map<String, AttributeValue> item = new HashMap<>();

        item.put(CONNECTION_ID, AttributeValue.builder().s(connectionId).build());
        item.put("id", AttributeValue.builder().s(queryId).build());

        manager.getDynamoDbAsyncClient().deleteItem(t -> t.tableName(subscriptionTable).key(item)).get();
    }

    /**
     * Verifies that the connections are active, removes them if they are not
     * Returns the connection ids of the still active connections which you should ping
     *
     * @return a stream of connectionIds that are still active
     */
    public List<String> verify() {
        final var request = ScanRequest
                .builder()
                .tableName(subscriptionTable)
                .projectionExpression("connectionId, lastSeen")
                .build();
        final var now = Instant.now().toEpochMilli();

        try {
            return manager
                    .getDynamoDbAsyncClient()
                    .scan(request)
                    .get()
                    .items()
                    .stream()
                    .map(item -> {
                        if (verify(item, now)) {
                            return item;
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .map(item -> item.get("connectionId").s())
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Verifies that the connection is still active so that we do not disconnect it
     *
     * @param connectionId the id of the connection
     */
    public void verified(final String connectionId) throws ExecutionException, InterruptedException {
        final var now = Instant.now().toEpochMilli();
        final Map<String, AttributeValueUpdate> updates = new HashMap<>();

        final var connections = getConnections(connectionId);

        updates.put(
                LAST_SEEN,
                AttributeValueUpdate
                        .builder()
                        .action(AttributeAction.PUT)
                        .value(
                                AttributeValue
                                        .builder()
                                        .n(String.valueOf(now))
                                        .build()
                        )
                        .build()
        );

        for (var connection : connections) {
            final var id = connection.get(ID).s();
            final Map<String, AttributeValue> key = new HashMap<>();

            key.put(CONNECTION_ID, AttributeValue.builder().s(connectionId).build());
            key.put(ID, AttributeValue.builder().s(id).build());

            final var request = UpdateItemRequest
                    .builder()
                    .key(key)
                    .attributeUpdates(updates)
                    .tableName(subscriptionTable)
                    .build();

            try {
                manager.getDynamoDbAsyncClient().updateItem(request).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private List<Map<String, AttributeValue>> getConnections(final String connectionId) throws InterruptedException, ExecutionException {
        final Map<String, AttributeValue> keyConditions = new HashMap<>();

        keyConditions.put(":" + CONNECTION_ID, AttributeValue
                .builder()
                .s(connectionId)
                .build());

        return manager
                .getDynamoDbAsyncClient()
                .query(t -> t
                        .tableName(subscriptionTable)
                        .keyConditionExpression("connectionId = :connectionId")
                        .expressionAttributeValues(keyConditions))
                .get()
                .items();
    }

    private boolean verify(final Map<String, AttributeValue> item, final long now) {
        final var lastSeen = Long.parseLong(item.get("lastSeen").n());

        if (now - lastSeenTimeout < lastSeen) {
            return true;
        }

        try {
            disconnect(item.get("connectionId").s());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

    private String mapSubscriptionName(final String name) {
        return subscriptionNameMapping.getOrDefault(name, name);
    }

    public interface SubscriptionIdBuilder {
        String build(String subscription, Map<String, Object> variables);
    }

}
