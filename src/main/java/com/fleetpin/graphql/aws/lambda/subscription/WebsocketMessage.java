package com.fleetpin.graphql.aws.lambda.subscription;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

/**
 * https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({ 
		@Type(value = SubscriptionConnectionInit.class, name = "connection_init"), //GQL_CONNECTION_INIT 
		@Type(value = SubscriptionStart.class, name = "start"), //GQL_START
		@Type(value = SubscriptionStop.class, name = "stop"), //GQL_STOP
		@Type(value = SubscriptionTerminate.class, name = "connection_terminate"), //GQL_CONNECTION_TERMINATE
		@Type(value = SubscriptionResponseConnectionError.class, name = "connection_error"), //GQL_CONNECTION_ERROR
		@Type(value = SubscriptionResponseAccept.class, name = "connection_ack"), //GQL_CONNECTION_ACK
		@Type(value = SubscriptionResponseData.class, name = "data"), //GQL_DATA
		@Type(value = SubscriptionResponseError.class, name = "error"), //GQL_ERROR
		@Type(value = SubscriptionResponseComplete.class, name = "complete"), //GQL_COMPLETE
		@Type(value = SubscriptionResponseKeepAlive.class, name = "ka"), //GQL_CONNECTION_KEEP_ALIVE
})
@JsonIgnoreProperties("type")
public class WebsocketMessage<T> {
	private T payload;
	private String id;

	public T getPayload() {
		return payload;
	}

	public String getId() {
		return id;
	}

	void setPayload(T payload) {
		this.payload = payload;
	}

	void setId(String id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [payload=" + payload + ", id=" + id + "]";
	}

	
	
	
	

}
