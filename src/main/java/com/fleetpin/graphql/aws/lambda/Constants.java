package com.fleetpin.graphql.aws.lambda;

import java.util.Map;

import static com.google.common.net.HttpHeaders.*;

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

    public static final String GRAPHQL_ACCESS_DENIED = "AccessDeniedError";
    public static final String GRAPHQL_ERRORS_FIELD = "errors";
    public static final Map<String, String> GRAPHQL_RESPONSE_HEADERS = Map.of(
            ACCESS_CONTROL_ALLOW_ORIGIN, "*",
            CONTENT_TYPE, "application/json; charset=utf-8"
    );
    public static final Map<String, String> GRAPHQL_GZIP_RESPONSE_HEADERS = Map.of(
            ACCESS_CONTROL_ALLOW_ORIGIN, "*",
            CONTENT_TYPE, "application/json; charset=utf-8",
            CONTENT_ENCODING, "gzip"
    );
}
