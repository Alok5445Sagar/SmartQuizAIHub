package com.smartquiz.config;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.smartquiz.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Maps every error to an RFC 7807 problem response with the correct status.
 * A {@code message} property mirrors {@code detail} for older clients.
 * Unexpected errors are logged with a stack trace and never expose internal messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String GENERIC_ERROR = "An unexpected error occurred. Please try again later.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("{} on {}: {}", ex.getStatus().value(), request.getRequestURI(), ex.getMessage());
        }
        ResponseEntity<Object> response = respond(ex.getStatus(), ex.getMessage(), request, null);
        if (ex.getRetryAfterSeconds() > 0) {
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                    .body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler({BadCredentialsException.class, InternalAuthenticationServiceException.class,
            UsernameNotFoundException.class, DisabledException.class})
    public ResponseEntity<Object> handleBadCredentials(Exception ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "Invalid credentials", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(lastPathNode(v.getPropertyPath().toString()), v.getMessage()));
        return respond(HttpStatus.BAD_REQUEST, summarize(errors), request, errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "The request conflicts with existing data", request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid request value", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR, request, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errors.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        return problemEntity(HttpStatus.BAD_REQUEST, summarize(errors), request.getDescription(false), errors);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(@NonNull HandlerMethodValidationException ex,
                                                                            @NonNull HttpHeaders headers,
                                                                            @NonNull HttpStatusCode status,
                                                                            @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e ->
                errors.putIfAbsent(r.getMethodParameter().getParameterName(), e.getDefaultMessage())));
        return problemEntity(HttpStatus.BAD_REQUEST, summarize(errors), request.getDescription(false), errors);
    }

    /** Spring MVC's own exceptions (bad JSON, missing params, 405, 415, 413...) keep their status and gain a message. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, @NonNull HttpHeaders headers,
                                                          @NonNull HttpStatusCode statusCode, @NonNull WebRequest request) {
        if (body instanceof ProblemDetail pd
                && (pd.getProperties() == null || !pd.getProperties().containsKey("message"))) {
            pd.setProperty("message", pd.getDetail());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> respond(HttpStatus status, String detail, HttpServletRequest request, Map<String, String> errors) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return ResponseEntity.status(status).contentType(MediaType.TEXT_EVENT_STREAM).body(formatSseError(detail));
        }
        return problemEntity(status, detail, request.getRequestURI(), errors);
    }

    private ResponseEntity<Object> problemEntity(HttpStatus status, String detail, String instance, Map<String, String> errors) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        if (instance != null) {
            pd.setInstance(URI.create(instance.startsWith("uri=") ? instance.substring(4) : instance));
        }
        pd.setProperty("message", detail);
        if (errors != null && !errors.isEmpty()) {
            pd.setProperty("errors", errors);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }

    private String summarize(Map<String, String> errors) {
        if (errors.isEmpty()) {
            return "Validation failed";
        }
        Map.Entry<String, String> first = errors.entrySet().iterator().next();
        return first.getKey() + ": " + first.getValue();
    }

    private String lastPathNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    /** SSE frame for errors raised inside streaming endpoints (real newlines, single line payload). */
    public static String formatSseError(String message) {
        String safe = (message == null || message.isBlank()) ? GENERIC_ERROR : message.replace("\r", " ").replace("\n", " ");
        return "event: error\ndata: " + safe + "\n\n";
    }
}
