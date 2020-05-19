package com.fleetpin.graphql.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.fleetpin.graphql.aws.lambda.admin.User;
import com.fleetpin.graphql.aws.lambda.exceptions.AccessDeniedError;
import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LambdaGraphQLTest {
    @Mock(lenient = true)
    private GraphQL graphQL;
    private String body;
    private String token;

    @BeforeEach
    @SuppressWarnings("unchecked") // No.
    void setUp() throws IOException {
        when(graphQL.executeAsync(any(UnaryOperator.class))).thenReturn(
                CompletableFuture.completedFuture(ExecutionResultImpl.newExecutionResult().build())
        );

        body = readResourceAsString("simple_graphql_request.json");
        token = readResourceAsString("token.txt");
    }

    @Test
    void expiredToken() throws Exception {
        final var expectedBody = readResourceAsString("access_denied_response.json");
        final var input = new APIGatewayV2ProxyRequestEvent();
        input.setBody(body);
        input.setHeaders(Map.of("Authorization", token));
        final var unauthorizedHandler = new UnauthorizedGraphHandler();

        final var response = unauthorizedHandler.handleRequest(input, null);

        assertEquals(200, response.getStatusCode());
        JSONAssert.assertEquals(expectedBody, response.getBody(), true);
    }

    @Test
    void goodToken() throws Exception {
        final var expectedBody = readResourceAsString("simple_graphql_response.json");
        final var input = new APIGatewayV2ProxyRequestEvent();
        input.setBody(body);
        input.setHeaders(Map.of("Authorization", token));
        final var authorizedHandler = new AuthorizedGraphHandler(graphQL);

        final var response = authorizedHandler.handleRequest(input, null);

        assertEquals(200, response.getStatusCode());
        JSONAssert.assertEquals(expectedBody, response.getBody(), true);
    }

    private static String readResourceAsString(final String resourcePath) throws IOException {
        return Files.readString(Path.of(ClassLoader.getSystemResource(resourcePath).getPath()));
    }

    private static class UnauthorizedGraphHandler extends LambdaGraphQL<User, NoopGraphQLContext> {
        private final NoopGraphQLContext noopGraphQLContext;
        private final CompletableFuture<User> validateFuture;

        public UnauthorizedGraphHandler() throws Exception {
            noopGraphQLContext = new NoopGraphQLContext();
            validateFuture = CompletableFuture.failedFuture(new AccessDeniedError());
        }

        @Override
        protected GraphQL buildGraphQL() {
            return null;
        }

        @Override
        protected CompletableFuture<User> validate(final String authHeader) {
            return validateFuture;
        }

        @Override
        protected NoopGraphQLContext buildContext(final User user, final GraphQLQuery query) {
            return noopGraphQLContext;
        }
    }

    private static class AuthorizedGraphHandler extends LambdaGraphQL<User, NoopGraphQLContext> {
        private final NoopGraphQLContext noopGraphQLContext;
        private final CompletableFuture<User> validateFuture;
        private final GraphQL graphQL;

        public AuthorizedGraphHandler(final GraphQL graphQL) {
            super(graphQL);
            this.graphQL = graphQL;
            noopGraphQLContext = new NoopGraphQLContext();
            validateFuture = CompletableFuture.completedFuture(new User() {
                @Override
                public String getId() {
                    return "f57f3d27-8fcf-4c76-8358-e16ed57839d1";
                }

                @Override
                public AttributeValue getExtraUserInfo() {
                    return null;
                }
            });
        }

        @Override
        protected GraphQL buildGraphQL() {
            return graphQL;
        }

        @Override
        protected CompletableFuture<User> validate(final String authHeader) {
            return validateFuture;
        }

        @Override
        protected NoopGraphQLContext buildContext(final User user, final GraphQLQuery query) {
            return noopGraphQLContext;
        }
    }

    private static class NoopGraphQLContext implements ContextGraphQL {
        @Override
        public void start(final CompletionStage<?> complete) {
            // won't do anything
        }
    }
}
