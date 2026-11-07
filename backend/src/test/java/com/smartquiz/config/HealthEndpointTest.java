package com.smartquiz.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.smartquiz.BaseTest;

@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.ai.ollama.base-url=http://127.0.0.1:1")
@DisplayName("Health endpoints")
class HealthEndpointTest extends BaseTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("readiness is UP with the database even though Ollama is unreachable")
    void readinessIgnoresOllama() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("liveness and overall health stay UP while AI is degraded")
    void livenessAndOverall() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
