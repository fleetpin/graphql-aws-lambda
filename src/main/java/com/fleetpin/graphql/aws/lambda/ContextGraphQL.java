package com.fleetpin.graphql.aws.lambda;

import java.util.concurrent.CompletableFuture;

import com.fleetpin.graphql.builder.annotations.Context;


@Context
public interface ContextGraphQL{
	void start(CompletableFuture<?> complete);
}
