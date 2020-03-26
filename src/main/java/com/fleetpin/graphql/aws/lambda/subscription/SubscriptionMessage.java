/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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
		@Type(value = SubscriptionResponseKeepAlive.class, name = "connection_keep_alive"), //GQL_CONNECTION_KEEP_ALIVE
		@Type(value = SubscriptionPong.class, name = "connection_pong")
})
@JsonIgnoreProperties("type")
public class SubscriptionMessage<T> {
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
