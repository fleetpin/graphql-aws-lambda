package com.fleetpin.graphql.aws.lambda.exceptions;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;

public class AccessDeniedError extends RuntimeException implements GraphQLError {
    private static final String DEFAULT_MESSAGE = AccessDeniedError.class.getSimpleName();
    private final String message;

    public AccessDeniedError() {
        this(DEFAULT_MESSAGE);
    }

    @SuppressWarnings("unused") // Someone will use you. :^)
    public AccessDeniedError(final Throwable cause) {
        this(DEFAULT_MESSAGE, cause);
    }

    public AccessDeniedError(final String message) {
        super(message);
        this.message = message;
    }

    public AccessDeniedError(final String message, final Throwable cause) {
        super(message, cause);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return List.of();
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorType.ValidationError;
    }
}
