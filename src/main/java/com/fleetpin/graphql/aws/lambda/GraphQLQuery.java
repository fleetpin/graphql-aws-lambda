package com.fleetpin.graphql.aws.lambda;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GraphQLQuery {

	private final String operationName;
	private final String query;
	private final Map<String, Object> variables;

	@JsonCreator
	public GraphQLQuery(@JsonProperty("operationName") String operationName, @JsonProperty("query") String query,
			@JsonProperty("variables") Map<String, Object> variables) {
		this.operationName = operationName;
		this.query = query;
		this.variables = variables;
	}

	public String getOperationName() {
		return operationName;
	}

	public String getQuery() {
		return query;
	}

	public Map<String, Object> getVariables() {
		if(variables == null) {
			return Collections.emptyMap();
		}
		return variables;
	}

}
