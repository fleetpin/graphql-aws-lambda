package com.fleetpin.graphql.aws.lambda;

public class Constants {
    private Constants() {}

    public static final String CONNECTION_ID = "connectionId";
    public static final String ID = "id";
    public static final String USER = "user";
    public static final String ADDITIONAL = "aditional";
    public static final String AUTH = "auth";
    public static final String TTL = "ttl";
    public static final String SUBSCRIPTION = "subscription";
    public static final String QUERY = "query";
    public static final String LAST_SEEN = "lastSeen";

    public static final String ENV_LAST_SEEN_TIMEOUT = "ENV_LAST_SEEN_TIMEOUT";
    public static final String ENV_SENT_MESSAGE_TIMEOUT = "ENV_SENT_MESSAGE_TIMEOUT";

    public static final String GRAPHQL_ERRORS_FIELD = "errors";
}
