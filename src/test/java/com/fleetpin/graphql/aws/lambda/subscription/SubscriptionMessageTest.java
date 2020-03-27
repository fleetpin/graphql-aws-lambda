package com.fleetpin.graphql.aws.lambda.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SubscriptionMessageTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testFormatOfSubscriptionPing() throws JsonProcessingException {
        Assertions.assertEquals(
                "{\"type\":\"connection_ping\",\"payload\":null,\"id\":null}",
                mapper.writeValueAsString(new SubscriptionPing())
        );
    }

    @Test
    public void testFormatOfSubscriptionPong() throws JsonProcessingException {
        Assertions.assertEquals(
                "{\"type\":\"connection_pong\",\"payload\":null,\"id\":null}",
                mapper.writeValueAsString(new SubscriptionPong())
        );
    }

}
