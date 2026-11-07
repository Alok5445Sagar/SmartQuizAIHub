package com.smartquiz.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reports whether Ollama is reachable and the configured model is pulled. Ollama being down is reported as
 * DEGRADED (not DOWN) and the indicator is kept out of the readiness group, so the app stays in service
 * for everything that does not need AI.
 */
@Component("ollama")
public class OllamaHealthIndicator implements HealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED", "AI features are unavailable");

    private final String baseUrl;
    private final String model;
    private final AiCallGuard aiCallGuard;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaHealthIndicator(@Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
                                 @Value("${app.ai.model:qwen2.5:3b}") String model,
                                 AiCallGuard aiCallGuard,
                                 ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.aiCallGuard = aiCallGuard;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public Health health() {
        Health.Builder builder;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                builder = Health.status(DEGRADED).withDetail("reachable", true).withDetail("httpStatus", response.statusCode());
            } else {
                boolean modelAvailable = hasModel(objectMapper.readTree(response.body()));
                builder = (modelAvailable ? Health.up() : Health.status(DEGRADED))
                        .withDetail("reachable", true)
                        .withDetail("modelAvailable", modelAvailable);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            builder = Health.status(DEGRADED).withDetail("reachable", false);
        } catch (Exception ex) {
            builder = Health.status(DEGRADED).withDetail("reachable", false).withDetail("error", ex.getClass().getSimpleName());
        }
        return builder
                .withDetail("model", model)
                .withDetail("circuit", aiCallGuard.circuitState().name())
                .withDetail("freeSlots", aiCallGuard.availableSlots())
                .build();
    }

    private boolean hasModel(JsonNode tags) {
        for (JsonNode m : tags.path("models")) {
            String name = m.path("name").asText("");
            if (name.equals(model) || name.equals(model + ":latest")) {
                return true;
            }
        }
        return false;
    }
}
