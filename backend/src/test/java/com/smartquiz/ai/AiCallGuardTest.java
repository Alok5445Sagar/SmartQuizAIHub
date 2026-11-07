package com.smartquiz.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.smartquiz.exception.BusinessRuleException;
import com.smartquiz.exception.ServiceUnavailableException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

@DisplayName("AiCallGuard (bulkhead, time limit, retry, circuit breaker)")
class AiCallGuardTest {

    private AiCallGuard guard;

    private AiCallGuard guard(int queueSize, Duration timeout, int minimumCalls) {
        guard = new AiCallGuard(new AiCallGuard.Settings(1, queueSize, Duration.ofSeconds(5), timeout,
                2, Duration.ofMillis(10), 50f, minimumCalls, 4, Duration.ofSeconds(30)));
        return guard;
    }

    @AfterEach
    void tearDown() {
        if (guard != null) {
            guard.shutdown();
        }
    }

    @Test
    @DisplayName("repeated timeouts open the breaker; further calls fail fast with 503 and Retry-After")
    void timeoutsOpenTheBreaker() {
        AiCallGuard g = guard(5, Duration.ofMillis(100), 2);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> g.execute(() -> sleep(1_000)))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("timed out");
        }
        assertThat(g.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> g.execute(calls::incrementAndGet))
                .isInstanceOfSatisfying(ServiceUnavailableException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(30))
                .hasMessageContaining("temporarily unavailable");
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("business errors do not count against Ollama's health")
    void businessErrorsIgnored() {
        AiCallGuard g = guard(5, Duration.ofSeconds(2), 2);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> g.execute(() -> { throw new BusinessRuleException("bad input"); }))
                    .isInstanceOf(BusinessRuleException.class);
        }
        assertThat(g.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("connection errors are retried, other failures are not")
    void retriesOnlyConnectionErrors() {
        AiCallGuard g = guard(5, Duration.ofSeconds(2), 10);
        AtomicInteger attempts = new AtomicInteger();
        String result = g.execute(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException(new ConnectException("Connection refused"));
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);

        AtomicInteger badAttempts = new AtomicInteger();
        assertThatThrownBy(() -> g.execute(() -> {
            badAttempts.incrementAndGet();
            throw new IllegalStateException("model not found");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(badAttempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a second concurrent call waits for the first one")
    void secondCallQueues() throws Exception {
        AiCallGuard g = guard(5, Duration.ofSeconds(5), 10);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();

        CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> g.execute(() -> {
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            firstStarted.countDown();
            await(releaseFirst);
            concurrent.decrementAndGet();
            return "first";
        }));
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<String> second = CompletableFuture.supplyAsync(() -> g.execute(() -> {
            maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            concurrent.decrementAndGet();
            return "second";
        }));
        Thread.sleep(200);
        assertThat(second).isNotDone();

        releaseFirst.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("second");
        assertThat(maxConcurrent.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("with a full queue, extra calls are rejected immediately as busy")
    void fullQueueRejects() throws Exception {
        AiCallGuard g = guard(0, Duration.ofSeconds(5), 10);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Object> running = CompletableFuture.supplyAsync(() -> g.execute(() -> {
            started.countDown();
            await(release);
            return null;
        }));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> g.execute(() -> "never"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("busy");

        release.countDown();
        running.get(5, TimeUnit.SECONDS);
        assertThat(g.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("records call outcomes, circuit state and queue depth as metrics")
    void recordsMetrics() throws Exception {
        AiCallGuard g = guard(0, Duration.ofMillis(100), 3);
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        g.bindTo(registry);

        g.execute(() -> "ok");
        assertThatThrownBy(() -> g.execute(() -> { throw new IllegalStateException("boom"); }));
        assertThatThrownBy(() -> g.execute(() -> sleep(1_000)));
        // Two failures out of three calls (minimum 3): the breaker is now open.
        assertThatThrownBy(() -> g.execute(() -> "rejected"));

        assertThat(registry.get("smartquiz.ai.calls").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("smartquiz.ai.calls").tag("outcome", "error").timer().count()).isEqualTo(1);
        assertThat(registry.get("smartquiz.ai.calls").tag("outcome", "timeout").timer().count()).isEqualTo(1);
        assertThat(registry.get("smartquiz.ai.calls").tag("outcome", "circuit_open").timer().count()).isEqualTo(1);
        // 0 = closed, 1 = open, 2 = half-open
        assertThat(registry.get("smartquiz.ai.circuit.state").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("smartquiz.ai.calls.in.flight").gauge().value()).isZero();
    }

    @Test
    @DisplayName("busy rejections are counted separately")
    void busyMetric() throws Exception {
        AiCallGuard g = guard(0, Duration.ofSeconds(5), 10);
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        g.bindTo(registry);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Object> running = CompletableFuture.supplyAsync(() -> g.execute(() -> {
            started.countDown();
            await(release);
            return null;
        }));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.get("smartquiz.ai.calls.in.flight").gauge().value()).isEqualTo(1.0);
        assertThatThrownBy(() -> g.execute(() -> "never")).isInstanceOf(ServiceUnavailableException.class);
        release.countDown();
        running.get(5, TimeUnit.SECONDS);

        assertThat(registry.get("smartquiz.ai.calls").tag("outcome", "busy").timer().count()).isEqualTo(1);
    }

    private static String sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "late";
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
