package com.smartquiz.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Tracks open SSE streams (AI chat answers and participant chat) for the {@code smartquiz.sse.connections}
 * gauge and closes them all when the application shuts down. Without this, graceful shutdown would wait the
 * full timeout for long-lived streams. Stopping in the default (last-started, first-stopped) phase runs before
 * the web server's graceful drain; browsers' EventSource reconnects to the next instance on its own.
 */
@Component
public class ActiveStreams implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ActiveStreams.class);

    public enum Type {
        AI_CHAT("ai-chat"), PARTICIPANT_CHAT("participant-chat");

        private final String tag;

        Type(String tag) {
            this.tag = tag;
        }
    }

    private final Map<SseEmitter, Type> open = new ConcurrentHashMap<>();
    private volatile boolean running;

    public ActiveStreams(MeterRegistry meterRegistry) {
        for (Type type : Type.values()) {
            Gauge.builder("smartquiz.sse.connections", open, streams -> streams.values().stream().filter(type::equals).count())
                    .description("Open server-sent-event streams")
                    .tag("type", type.tag)
                    .register(meterRegistry);
        }
    }

    /** Starts tracking a stream; the caller must call {@link #unregister} when it completes, times out or fails. */
    public void register(Type type, SseEmitter emitter) {
        if (!running) {
            emitter.complete();
            return;
        }
        open.put(emitter, type);
    }

    public void unregister(SseEmitter emitter) {
        open.remove(emitter);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        int count = open.size();
        for (SseEmitter emitter : open.keySet()) {
            try {
                emitter.complete();
            } catch (RuntimeException ex) {
                log.debug("Closing stream during shutdown failed: {}", ex.getMessage());
            }
        }
        open.clear();
        if (count > 0) {
            log.info("Closed {} open stream(s) for shutdown", count);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
