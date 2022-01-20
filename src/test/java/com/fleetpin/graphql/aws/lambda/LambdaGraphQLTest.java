package com.fleetpin.graphql.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.fleetpin.graphql.aws.lambda.admin.User;
import com.fleetpin.graphql.aws.lambda.exceptions.AccessDeniedError;
import com.google.common.io.ByteStreams;

import graphql.ExecutionResultImpl;
import graphql.GraphQL;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LambdaGraphQLTest {
    @Mock(lenient = true)
    private GraphQL graphQL;
    private String body;
    private String token;

    @BeforeEach
    @SuppressWarnings("unchecked")
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
        assertEquals(2, response.getHeaders().size());
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
        assertEquals(2, response.getHeaders().size());
        JSONAssert.assertEquals(expectedBody, response.getBody(), true);
    }

    @Test
    void gzippedResponse() throws Exception {
        final var expectedBody = readResourceAsString("simple_graphql_response.json");
        final var input = new APIGatewayV2ProxyRequestEvent();
        input.setBody(body);
        input.setHeaders(Map.of("Authorization", token, "Accept-Encoding", "gzip"));
        final var randomGraphHandler = new RandomGraphHandler(graphQL);

        final var response = randomGraphHandler.handleRequest(input, null);

        assertEquals(200, response.getStatusCode());
        assertEquals(2, response.getHeaders().size());
        assertTrue(response.getHeaders().get("content-type").compareToIgnoreCase("gzip; charset=utf-8") == 0);

        var responseBodyGzipped = Base64.decodeBase64(response.getBody());

        try (final GZIPInputStream gzipInput = new GZIPInputStream(new ByteArrayInputStream(responseBodyGzipped))) {
            var result = new String(gzipInput.readAllBytes(), StandardCharsets.UTF_8);
            JSONAssert.assertEquals(expectedBody, result, true);
        } catch (IOException e) {
            throw new UncheckedIOException("Error while decompression!", e);
        }

    }

    private static String readResourceAsString(final String resourcePath) throws IOException {
        return new String(ByteStreams.toByteArray(ClassLoader.getSystemResourceAsStream(resourcePath)));
    }

    private static class RandomGraphHandler extends LambdaGraphQL<User, NoopGraphQLContext> {
        private final NoopGraphQLContext noopGraphQLContext;
        private final CompletableFuture<User> validateFuture;
        private final GraphQL graphQL;

        public RandomGraphHandler(final GraphQL graphQL) {
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

                @Override
                public String toString() {
                    return "someuser";
                }
            });
        }

        @Override
        public boolean enableGzipCompression() { return true; }

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
                
                @Override
                public String toString() {
                	return "someuser";
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
