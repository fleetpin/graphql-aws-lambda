package com.fleetpin.graphql.aws.lambda.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionResponseConnectionError extends WebsocketMessage<Object>{

	@JsonCreator
	public SubscriptionResponseConnectionError(@JsonProperty("error") String error) {
		setPayload(error);
	}
	
}
