package com.fleetpin.graphql.aws.lambda;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStrategy;
import graphql.execution.ExecutionStrategyParameters;
import graphql.execution.NonNullableFieldWasNullException;

public class InterceptExecutionStrategy extends ExecutionStrategy {

	@Override
	public CompletableFuture<ExecutionResult> execute(ExecutionContext executionContext, ExecutionStrategyParameters parameters) throws NonNullableFieldWasNullException {
		String subscription = parameters.getFields().getKeys().get(0);
		return CompletableFuture.completedFuture(new ExecutionResultImpl(subscription, new ArrayList<>())); 
	}

}

