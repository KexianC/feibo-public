package com.hzmwmt.types.exception;

/**
 * Raised when current authorization data cannot be loaded reliably. The
 * request must fail closed, but the credential must not be reported as
 * invalid or cleared from the client merely because the database is down.
 */
public class AuthorizationDataUnavailableException extends RuntimeException {

    public AuthorizationDataUnavailableException(Throwable cause) {
        super("Authorization data is temporarily unavailable", cause);
    }
}
