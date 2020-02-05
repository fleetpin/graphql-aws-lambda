package com.fleetpin.graphql.aws.lambda.subscription;

import graphql.GraphQLError;

public class SubscriptionResponseError extends WebsocketMessage<GraphQLError>{

	public SubscriptionResponseError(String id, GraphQLError error) {
		setId(id);
		setPayload(error);
	}

}
