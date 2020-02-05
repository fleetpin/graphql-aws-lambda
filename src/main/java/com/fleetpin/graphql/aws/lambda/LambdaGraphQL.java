package com.fleetpin.graphql.aws.lambda;

import static com.google.common.net.HttpHeaders.AUTHORIZATION;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fleetpin.graphql.builder.SchemaBuilder;

import graphql.ExecutionResult;
import graphql.GraphQL;

public abstract class LambdaGraphQL<U, C extends ContextGraphQL> implements RequestHandler<APIGatewayV2ProxyRequestEvent, APIGatewayV2ProxyResponseEvent>{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(LambdaGraphQL.class);

	private final GraphQL build;
	private ObjectMapper mapper;

	public LambdaGraphQL() throws Exception {
		this.build = buildGraphQL();
		this.mapper = builderObjectMapper();
	}
	
	

	@Override
	public APIGatewayV2ProxyResponseEvent handleRequest(APIGatewayV2ProxyRequestEvent input, com.amazonaws.services.lambda.runtime.Context context) {
		try {
			GraphQLQuery query = mapper.readValue(input.getBody(), GraphQLQuery.class);
			
			var toReturn = new APIGatewayV2ProxyResponseEvent();
			CompletableFuture<ExecutionResult> result =	validate(input.getHeaders().get(AUTHORIZATION)).handle((user, userError) -> {
				if(userError != null) {
					LOGGER.error("Failed to validate user", userError);
					toReturn.setStatusCode(401);
					return CompletableFuture.completedFuture((ExecutionResult) null);
				}

				C graphContext = buildContext(query.getVariables(), user, query);
				var target = build.executeAsync(builder -> builder.query(query.getQuery()).operationName(query.getOperationName()).variables(query.getVariables()).context(graphContext));
				graphContext.start(target);
				return target;
			}).thenCompose(t -> t);
			toReturn.setStatusCode(200);
			var headers = new HashMap<String, String>();
			headers.put("Access-Control-Allow-Origin", "*");
			headers.put("content-type", "application/json; charset=utf-8");
			toReturn.setHeaders(headers);
			var r = result.get();
			if(r != null) {
				//remove empty array to match apollo
				ObjectNode tree = mapper.valueToTree(r);
				if(tree.get("errors").isEmpty()) {
					tree.remove("errors");
				}
				toReturn.setBody(mapper.writeValueAsString(tree));
			}else {
				toReturn.setBody("");
			}
			return toReturn;
		}catch (Throwable e) {
			LOGGER.error("Failed to invoke graph", e);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	protected ObjectMapper builderObjectMapper() {
		return SchemaBuilder.MAPPER;
	}
	
	protected abstract GraphQL buildGraphQL() throws Exception;

	protected abstract CompletableFuture<U> validate(String authHeader);

	protected abstract C buildContext(Map<String, Object> variables, U user, GraphQLQuery query);

}

