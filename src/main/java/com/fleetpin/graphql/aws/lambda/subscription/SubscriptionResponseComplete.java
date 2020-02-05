package com.fleetpin.graphql.aws.lambda.subscription;

public class SubscriptionResponseComplete extends WebsocketMessage<Object>{

	public SubscriptionResponseComplete(String id) {
		setId(id);
	}
	
}
