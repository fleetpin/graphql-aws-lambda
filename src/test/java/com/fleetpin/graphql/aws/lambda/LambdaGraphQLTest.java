package com.fleetpin.graphql.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.fleetpin.graphql.aws.lambda.admin.User;
import graphql.GraphQL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaGraphQLTest {
    private static String BODY;
    private static String EXPIRED_TOKEN;

    @BeforeEach
    void setUp() throws IOException {
        BODY = readResourceAsString("simple_graphql_request.json");
        EXPIRED_TOKEN = readResourceAsString("expired_token.txt");
    }

    @Test
    void expiredToken() throws Exception {
        final var input = new APIGatewayV2ProxyRequestEvent();
        input.setBody(BODY);
        input.setHeaders(Map.of("Authorization", EXPIRED_TOKEN));
        final var unauthorizedHandler = new UnauthorizedGraphHandler();

        final var response = unauthorizedHandler.handleRequest(input, null);

        assertEquals(401, response.getStatusCode());
    }

    private static String readResourceAsString(final String resourcePath) throws IOException {
        return Files.readString(Path.of(ClassLoader.getSystemResource(resourcePath).getPath()));
    }

    private class UnauthorizedGraphHandler extends LambdaGraphQL<User, NoopGraphQLContext> {
        private final NoopGraphQLContext noopGraphQLContext;
        private final CompletableFuture<User> validateFuture;

        public UnauthorizedGraphHandler() throws Exception {
            noopGraphQLContext = new NoopGraphQLContext();
            validateFuture = CompletableFuture.failedFuture(new RuntimeException("Bad token"));
        }

        @Override
        protected GraphQL buildGraphQL() {
            return null;
        }

        @Override
        protected CompletableFuture<User> validate(final String authHeader) {
            return validateFuture; // This should roughly mimic the apis behaviour
        }

        @Override
        protected NoopGraphQLContext buildContext(final User user, final GraphQLQuery query) {
            return noopGraphQLContext;
        }
    }

    private class NoopGraphQLContext implements ContextGraphQL {
        @Override
        public void start(final CompletionStage<?> complete) {
            // won't do anything
        }
    }
}
