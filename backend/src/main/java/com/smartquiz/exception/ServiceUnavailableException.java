package com.smartquiz.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }

    public ServiceUnavailableException(String message, long retryAfterSeconds) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message, null, Math.max(1, retryAfterSeconds));
    }
}
