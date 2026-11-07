package com.smartquiz.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}