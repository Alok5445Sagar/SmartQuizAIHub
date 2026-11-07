package com.smartquiz.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@DisplayName("Active SSE streams")
class ActiveStreamsTest {

    /** Records completion instead of needing a servlet response. */
    static final class RecordingEmitter extends SseEmitter {
        final AtomicInteger completions = new AtomicInteger();

        @Override
        public synchronized void complete() {
            completions.incrementAndGet();
        }
    }

    @Test
    @DisplayName("counts open streams per type and closes them all on shutdown, before the web server drains")
    void closesStreamsOnStop() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ActiveStreams streams = new ActiveStreams(registry);
        streams.start();
        RecordingEmitter chat = new RecordingEmitter();
        RecordingEmitter ai = new RecordingEmitter();
        RecordingEmitter gone = new RecordingEmitter();

        streams.register(ActiveStreams.Type.PARTICIPANT_CHAT, chat);
        streams.register(ActiveStreams.Type.AI_CHAT, ai);
        streams.register(ActiveStreams.Type.AI_CHAT, gone);
        streams.unregister(gone);

        assertThat(registry.get("smartquiz.sse.connections").tag("type", "participant-chat").gauge().value()).isEqualTo(1.0);
        assertThat(registry.get("smartquiz.sse.connections").tag("type", "ai-chat").gauge().value()).isEqualTo(1.0);
        // Must stop before Spring's graceful web-server shutdown (phase DEFAULT_PHASE - 1024).
        assertThat(streams.getPhase()).isGreaterThan(org.springframework.context.SmartLifecycle.DEFAULT_PHASE - 1024);

        streams.stop();

        assertThat(chat.completions.get()).isEqualTo(1);
        assertThat(ai.completions.get()).isEqualTo(1);
        assertThat(gone.completions.get()).isZero();
        assertThat(streams.isRunning()).isFalse();
        assertThat(registry.get("smartquiz.sse.connections").tag("type", "ai-chat").gauge().value()).isZero();

        RecordingEmitter late = new RecordingEmitter();
        streams.register(ActiveStreams.Type.AI_CHAT, late);
        assertThat(late.completions.get()).as("streams opened during shutdown are closed at once").isEqualTo(1);
    }
}
