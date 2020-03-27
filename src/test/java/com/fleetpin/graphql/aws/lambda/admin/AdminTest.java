package com.fleetpin.graphql.aws.lambda.admin;

import com.fleetpin.graphql.aws.lambda.GraphQLQuery;
import com.fleetpin.graphql.aws.lambda.subscription.SubscriptionResponseAccept;
import com.fleetpin.graphql.database.manager.dynamo.DynamoDbManager;
import com.fleetpin.graphql.database.manager.test.annotations.TestDatabase;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import org.junit.jupiter.api.Assertions;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.UnaryOperator;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminTest {

    private GraphQL graphQL = mock(GraphQL.class);

    @TestDatabase
    public void canConnect(final DynamoDbManager databaseManager) throws ExecutionException, InterruptedException {
        createDatabase(databaseManager);
        final var admin = createAdmin(databaseManager);
        createUser(admin);
    }

    @TestDatabase
    public void canDisconnect(final DynamoDbManager databaseManager) throws ExecutionException, InterruptedException {
        createDatabase(databaseManager);
        final var admin = createAdmin(databaseManager);
        createUser(admin);

        admin.disconnect("123456");

        Assertions.assertFalse(admin.isConnected("123456"));
    }

    @TestDatabase
    public void canSubscribeToASubscription(final DynamoDbManager databaseManager) throws ExecutionException, InterruptedException {
        when(graphQL.execute((UnaryOperator<ExecutionInput.Builder>) any())).thenReturn(new TestExecutionResult());

        createDatabase(databaseManager);
        final var admin = createAdmin(databaseManager);
        createUser(admin);

        final var query = read("subscription-start.gql");
        final var variables = Map.<String, Object>of(
                "assetId", "5",
                "organisationId", "50",
                "from", "2020-03-26"
        );

        final var graphQuery = new GraphQLQuery("newActivity", query, variables);

        final var message = admin.subscribe("123456", "2", graphQuery, this::buildSubscriptionId);

        Assertions.assertNull(message);
        Assertions.assertTrue(admin.isSubscribed("123456", "2"));
    }

    @TestDatabase
    public void canUnsubscribeFromASubscription(final DynamoDbManager databaseManager) throws ExecutionException, InterruptedException {
        when(graphQL.execute((UnaryOperator<ExecutionInput.Builder>) any())).thenReturn(new TestExecutionResult());

        createDatabase(databaseManager);
        final var admin = createAdmin(databaseManager);
        createUser(admin);

        final var query = read("subscription-start.gql");
        final var variables = Map.<String, Object>of(
                "assetId", "5",
                "organisationId", "50",
                "from", "2020-03-26"
        );

        final var graphQuery = new GraphQLQuery("newActivity", query, variables);

        final var message = admin.subscribe("123456", "2", graphQuery, this::buildSubscriptionId);

        Assertions.assertNull(message);
        Assertions.assertTrue(admin.isSubscribed("123456", "2"));

        admin.unsubscribe("123456", "2");

        Assertions.assertFalse(admin.isSubscribed("123456", "2"));
    }

    @TestDatabase
    public void canVerifyThatConnectionsAreCurrent(final DynamoDbManager databaseManager) throws ExecutionException, InterruptedException {
        createDatabase(databaseManager);
        final var admin = createAdmin(databaseManager);
        createUser(admin);

        {
            final var current = admin.verify();

            Assertions.assertEquals(1, current.size());
        }

        Thread.sleep(300);

        {
            final var current = admin.verify();

            Assertions.assertEquals(0, current.size());
            Assertions.assertFalse(admin.isConnected("123456"));
        }
    }

    @TestDatabase
    public void canUpdateConnectionVerificationStatus(final DynamoDbManager databaseManager) throws ExecutionException, InterruptedException {
        createDatabase(databaseManager);
        final var admin = createAdmin(databaseManager);
        createUser(admin);

        {
            final var current = admin.verify();

            Assertions.assertEquals(1, current.size());
        }

        Thread.sleep(300);

        admin.verified("123456");

        {
            final var current = admin.verify();

            Assertions.assertEquals(1, current.size());
            Assertions.assertTrue(admin.isConnected("123456"));
        }
    }

    private Admin<TestUser> createAdmin(final DynamoDbManager databaseManager) {
        return new Admin<>(graphQL, "subscriptions", databaseManager, 200);
    }

    private void createUser(final Admin<TestUser> admin) throws ExecutionException, InterruptedException {
        final var message = admin.connect("123456", new TestUser());

        Assertions.assertTrue(message instanceof SubscriptionResponseAccept, "Admin returned accepted");
        Assertions.assertTrue(admin.isConnected("123456"));
    }

    private void createDatabase(final DynamoDbManager databaseManager) throws ExecutionException, InterruptedException {
        final List<AttributeDefinition> attributeSchema = new ArrayList<>();
        final List<KeySchemaElement> keySchema = new ArrayList<>();

        attributeSchema.add(AttributeDefinition.builder().attributeName("connectionId").attributeType("S").build());
        attributeSchema.add(AttributeDefinition.builder().attributeName("id").attributeType("S").build());
        keySchema.add(KeySchemaElement.builder().attributeName("connectionId").keyType(KeyType.HASH).build());
        keySchema.add(KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build());

        final CreateTableRequest request = CreateTableRequest
                .builder()
                .tableName("subscriptions")
                .keySchema(keySchema)
                .attributeDefinitions(attributeSchema)
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(5L)
                        .writeCapacityUnits(6L).build())
                .build();

        databaseManager.getDynamoDbAsyncClient().createTable(request).get();
    }

    private String buildSubscriptionId(String subscription, Map<String, Object> variables) {
        return variables.get("organisationId").toString();
    }

    private String read(final String file) {
        try {
            return new String(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(file)).readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class TestUser implements User {
        @Override
        public String getId() {
            return "userId";
        }

        @Override
        public AttributeValue getExtraUserInfo() {
            return null;
        }
    }

    private class TestExecutionResult implements ExecutionResult {

        @Override
        public List<GraphQLError> getErrors() {
            return Collections.emptyList();
        }

        @Override
        public <T> T getData() {
            return (T) "123456";
        }

        @Override
        public boolean isDataPresent() {
            return true;
        }

        @Override
        public Map<Object, Object> getExtensions() {
            return Collections.emptyMap();
        }

        @Override
        public Map<String, Object> toSpecification() {
            return Collections.emptyMap();
        }
    }
}
