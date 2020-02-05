package com.fleetpin.graphql.aws.lambda.subscription;

import graphql.ExecutionResult;

public class SubscriptionResponseData extends WebsocketMessage<ExecutionResult>{

	public SubscriptionResponseData(String id, ExecutionResult data) {
		this.setId(id);
		this.setPayload(data);
	}

}
