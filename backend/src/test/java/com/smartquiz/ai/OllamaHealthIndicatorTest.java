package com.smartquiz.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

@DisplayName("Ollama health indicator")
class OllamaHealthIndicatorTest {

    private HttpServer server;
    private final AiCallGuard guard = new AiCallGuard(new AiCallGuard.Settings(1, 5, Duration.ofSeconds(1),
            Duration.ofSeconds(1), 1, Duration.ofMillis(1), 50f, 4, 10, Duration.ofSeconds(30)));

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        guard.shutdown();
    }

    private String startStub(String tagsJson) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = tagsJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    @DisplayName("UP when Ollama answers and the model is pulled")
    void upWhenModelPresent() throws Exception {
        String url = startStub("{\"models\":[{\"name\":\"qwen2.5:3b\"}]}");
        Health health = new OllamaHealthIndicator(url, "qwen2.5:3b", guard, new ObjectMapper()).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("modelAvailable", true).containsEntry("circuit", "CLOSED");
    }

    @Test
    @DisplayName("DEGRADED when the model has not been pulled (accepts the :latest tag)")
    void degradedWhenModelMissing() throws Exception {
        String url = startStub("{\"models\":[{\"name\":\"qwen2.5:latest\"}]}");
        assertThat(new OllamaHealthIndicator(url, "qwen2.5", guard, new ObjectMapper()).health().getStatus())
                .isEqualTo(Status.UP);
        assertThat(new OllamaHealthIndicator(url, "llama3", guard, new ObjectMapper()).health().getStatus())
                .isEqualTo(OllamaHealthIndicator.DEGRADED);
    }

    @Test
    @DisplayName("DEGRADED (not DOWN) when Ollama is unreachable")
    void degradedWhenUnreachable() {
        Health health = new OllamaHealthIndicator("http://127.0.0.1:1", "qwen2.5:3b", guard, new ObjectMapper()).health();
        assertThat(health.getStatus()).isEqualTo(OllamaHealthIndicator.DEGRADED);
        assertThat(health.getDetails()).containsEntry("reachable", false);
    }
}
