package com.fleetpin.graphql.aws.lambda;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fleetpin.graphql.aws.lambda.admin.Admin;
import com.fleetpin.graphql.aws.lambda.admin.User;
import com.fleetpin.graphql.database.manager.dynamo.DynamoDbManager;
import graphql.GraphQL;

public abstract class LambdaAdminSource<U extends User, E> implements RequestHandler<E, Void> {

    private final Admin<U> admin;

    public LambdaAdminSource(final String subscriptionTable) throws Exception {
        final GraphQL graph = buildGraphQL().subscriptionExecutionStrategy(new InterceptExecutionStrategy()).build();

        final DynamoDbManager manager = builderManager();

        admin = new Admin<>(
                graph,
                subscriptionTable,
                manager,
                Long.parseLong(System.getenv("LAST_SEEN_TIMEOUT"))
        );
    }

    protected Admin<U> getAdmin() {
        return admin;
    }

    protected abstract GraphQL.Builder buildGraphQL() throws Exception;
    protected abstract DynamoDbManager builderManager();
}
