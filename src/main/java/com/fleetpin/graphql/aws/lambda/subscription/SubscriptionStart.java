package com.fleetpin.graphql.aws.lambda.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fleetpin.graphql.aws.lambda.GraphQLQuery;

public class SubscriptionStart extends WebsocketMessage<GraphQLQuery>{

	@JsonCreator
	public SubscriptionStart(String id, GraphQLQuery payload) {
		setId(id);
		setPayload(payload);
	}
	
}
