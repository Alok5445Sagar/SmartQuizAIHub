package com.smartquiz.exception;

import org.springframework.http.HttpStatus;

/** Base class for errors that map to a specific HTTP status with a client-safe message. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final long retryAfterSeconds;

    protected ApiException(HttpStatus status, String message) {
        this(status, message, null, 0);
    }

    protected ApiException(HttpStatus status, String message, Throwable cause) {
        this(status, message, cause, 0);
    }

    protected ApiException(HttpStatus status, String message, Throwable cause, long retryAfterSeconds) {
        super(message, cause);
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Seconds a client should wait before retrying; 0 means no Retry-After header. */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
