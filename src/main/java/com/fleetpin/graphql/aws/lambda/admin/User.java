package com.fleetpin.graphql.aws.lambda.admin;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public interface User {

    String getId();

    AttributeValue getExtraUserInfo();

}
