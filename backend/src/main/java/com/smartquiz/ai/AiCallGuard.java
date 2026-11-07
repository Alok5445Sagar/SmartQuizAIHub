package com.smartquiz.ai;

import java.net.ConnectException;
import java.net.SocketException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.smartquiz.exception.ApiException;
import com.smartquiz.exception.ServiceUnavailableException;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import jakarta.annotation.PreDestroy;

/**
 * Every call to the local model goes through here. Order: circuit breaker (fail fast while Ollama is down)
 * → bulkhead (CPU inference: one call at a time, a short bounded queue) → time limit → retry on connection errors.
 */
@Component
public class AiCallGuard implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(AiCallGuard.class);

    /** Tunables; values come from app.ai.* properties (see application.properties). */
    public record Settings(int maxConcurrent, int queueSize, Duration queueWait, Duration callTimeout,
                           int retryMaxAttempts, Duration retryWait,
                           float failureRateThreshold, int minimumCalls, int slidingWindow, Duration openWait) {
    }

    private final Settings settings;
    private final CircuitBreaker circuitBreaker;
    private final Bulkhead bulkhead;
    private final TimeLimiter timeLimiter;
    private final Retry retry;
    private final ExecutorService executor;
    private final AtomicInteger inFlight = new AtomicInteger();
    /** Set by Micrometer when the guard is bound to a registry; metrics are skipped until then. */
    private volatile MeterRegistry meterRegistry;

    @org.springframework.beans.factory.annotation.Autowired
    public AiCallGuard(@Value("${app.ai.max-concurrent:1}") int maxConcurrent,
                       @Value("${app.ai.queue-size:5}") int queueSize,
                       @Value("${app.ai.queue-wait:180s}") Duration queueWait,
                       @Value("${app.ai.call-timeout:300s}") Duration callTimeout,
                       @Value("${app.ai.retry.max-attempts:2}") int retryMaxAttempts,
                       @Value("${app.ai.retry.wait:500ms}") Duration retryWait,
                       @Value("${app.ai.circuit.failure-rate:50}") float failureRate,
                       @Value("${app.ai.circuit.minimum-calls:4}") int minimumCalls,
                       @Value("${app.ai.circuit.sliding-window:10}") int slidingWindow,
                       @Value("${app.ai.circuit.open-wait:30s}") Duration openWait) {
        this(new Settings(maxConcurrent, queueSize, queueWait, callTimeout, retryMaxAttempts, retryWait,
                failureRate, minimumCalls, slidingWindow, openWait));
    }

    public AiCallGuard(Settings settings) {
        this.settings = settings;
        this.circuitBreaker = CircuitBreaker.of("ollama", CircuitBreakerConfig.custom()
                .failureRateThreshold(settings.failureRateThreshold())
                .minimumNumberOfCalls(settings.minimumCalls())
                .slidingWindowSize(settings.slidingWindow())
                .waitDurationInOpenState(settings.openWait())
                .permittedNumberOfCallsInHalfOpenState(1)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Our own business errors (bad input etc.) say nothing about Ollama's health.
                .ignoreExceptions(ApiException.class)
                .build());
        this.bulkhead = Bulkhead.of("ollama", BulkheadConfig.custom()
                .maxConcurrentCalls(Math.max(1, settings.maxConcurrent()))
                .maxWaitDuration(settings.queueWait())
                .fairCallHandlingStrategyEnabled(true)
                .build());
        this.timeLimiter = TimeLimiter.of("ollama", TimeLimiterConfig.custom()
                .timeoutDuration(settings.callTimeout())
                .cancelRunningFuture(true)
                .build());
        this.retry = Retry.of("ollama", RetryConfig.custom()
                .maxAttempts(Math.max(1, settings.retryMaxAttempts()))
                .waitDuration(settings.retryWait())
                .retryOnException(AiCallGuard::isTransientTransportError)
                .build());
        AtomicInteger threadCount = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(Math.max(1, settings.maxConcurrent()), runnable -> {
            Thread thread = new Thread(runnable, "ai-call-" + threadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public <T> T execute(Supplier<T> call) {
        if (!circuitBreaker.tryAcquirePermission()) {
            record("circuit_open", 0);
            throw unavailable();
        }
        long start = System.nanoTime();
        try {
            T result = runInBulkhead(call);
            long elapsed = System.nanoTime() - start;
            circuitBreaker.onResult(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS, result);
            record("success", elapsed);
            return result;
        } catch (BulkheadFullException ex) {
            circuitBreaker.releasePermission();
            record("busy", System.nanoTime() - start);
            throw new ServiceUnavailableException("The AI assistant is busy with other requests. Please try again shortly.",
                    Math.max(5, settings.callTimeout().toSeconds() / 4));
        } catch (RuntimeException ex) {
            long elapsed = System.nanoTime() - start;
            circuitBreaker.onError(elapsed, java.util.concurrent.TimeUnit.NANOSECONDS, ex);
            if (ex instanceof AiTimeoutException) {
                record("timeout", elapsed);
                throw new ServiceUnavailableException(
                        "Local AI model timed out. Try fewer questions or increase AI_CALL_TIMEOUT.", ex);
            }
            record("error", elapsed);
            throw ex;
        }
    }

    /**
     * smartquiz.ai.calls (timer, tag outcome: success, error, timeout, busy, circuit_open) includes queue time;
     * smartquiz.ai.circuit.state is 0 closed, 1 open, 2 half-open, 3 other; smartquiz.ai.calls.in.flight
     * counts running plus queued calls.
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("smartquiz.ai.circuit.state", circuitBreaker, cb -> switch (cb.getState()) {
                    case CLOSED -> 0;
                    case OPEN -> 1;
                    case HALF_OPEN -> 2;
                    default -> 3;
                })
                .description("Ollama circuit breaker: 0 closed, 1 open, 2 half-open, 3 other")
                .register(registry);
        Gauge.builder("smartquiz.ai.calls.in.flight", inFlight, AtomicInteger::get)
                .description("AI calls running or waiting for the model")
                .register(registry);
        this.meterRegistry = registry;
    }

    private void record(String outcome, long nanos) {
        MeterRegistry registry = meterRegistry;
        if (registry != null) {
            Timer.builder("smartquiz.ai.calls")
                    .description("Calls to the local model, including time spent waiting for a free slot")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(nanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /** Raw timeout signal; counted by the circuit breaker, then reported to clients as 503. */
    static final class AiTimeoutException extends RuntimeException {
        AiTimeoutException(Throwable cause) {
            super("AI call timed out", cause);
        }
    }

    private <T> T runInBulkhead(Supplier<T> call) {
        // Bound the queue: running + waiting calls may not exceed maxConcurrent + queueSize; reject the rest at once.
        if (inFlight.incrementAndGet() > settings.queueSize() + settings.maxConcurrent()) {
            inFlight.decrementAndGet();
            throw BulkheadFullException.createBulkheadFullException(bulkhead);
        }
        try {
            bulkhead.acquirePermission();
        } catch (RuntimeException ex) {
            inFlight.decrementAndGet();
            throw ex;
        }
        try {
            Supplier<T> retried = Retry.decorateSupplier(retry, call);
            return timeLimiter.executeFutureSupplier(() -> CompletableFuture.supplyAsync(retried, executor));
        } catch (TimeoutException ex) {
            throw new AiTimeoutException(ex);
        } catch (ExecutionException ex) {
            throw unwrap(ex.getCause());
        } catch (RuntimeException ex) {
            throw unwrap(ex);
        } catch (Exception ex) {
            throw new IllegalStateException("AI call failed", ex);
        } finally {
            bulkhead.onComplete();
            inFlight.decrementAndGet();
        }
    }

    private RuntimeException unwrap(Throwable throwable) {
        Throwable t = throwable;
        while ((t instanceof java.util.concurrent.CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t instanceof RuntimeException rt ? rt : new IllegalStateException(t);
    }

    private ServiceUnavailableException unavailable() {
        log.debug("Ollama circuit is {}; rejecting AI call", circuitBreaker.getState());
        return new ServiceUnavailableException(
                "The AI service is temporarily unavailable. Please try again later.",
                Math.max(1, settings.openWait().toSeconds()));
    }

    /** Retry only connection-level hiccups; a missing model or an invalid request will not fix itself. */
    static boolean isTransientTransportError(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConnectException) {
                return true;
            }
            if (t instanceof SocketException) {
                String msg = t.getMessage() == null ? "" : t.getMessage().toLowerCase(Locale.ROOT);
                if (msg.contains("reset") || msg.contains("broken pipe")) {
                    return true;
                }
            }
            if (t == t.getCause()) {
                break;
            }
        }
        return false;
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    public int availableSlots() {
        return bulkhead.getMetrics().getAvailableConcurrentCalls();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
