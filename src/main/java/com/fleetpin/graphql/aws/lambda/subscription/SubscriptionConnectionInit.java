package com.fleetpin.graphql.aws.lambda.subscription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SubscriptionConnectionInit extends WebsocketMessage<SubscriptionConnectionInit.ConnectionPayload>{

	@JsonCreator
	public SubscriptionConnectionInit(String id, ConnectionPayload payload) {
		this.setId(id);
		this.setPayload(payload);
	}
	
	public static class ConnectionPayload {
		@JsonProperty("Authorization")
		private String authorization;
		
		
		@JsonCreator
		public ConnectionPayload(String authorization) {
			this.authorization = authorization;
		}



		public String getAuthorization() {
			return authorization;
		}
	}
	
}
