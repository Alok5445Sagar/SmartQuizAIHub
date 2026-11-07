package com.smartquiz.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.smartquiz.BaseTest;
import com.smartquiz.ai.AIService;
import com.smartquiz.entity.User;
import com.smartquiz.security.JwtUtils;
import com.smartquiz.service.ChatMemoryService;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai.stream-timeout=700ms")
@DisplayName("AI chat streaming endpoint")
class AIChatStreamControllerTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AIService aiService;
    @Autowired private JwtUtils jwtUtils;
    // Stream work runs on another thread and cannot see this test's uncommitted rows, so chat persistence is mocked.
    @MockitoBean private ChatMemoryService chatMemoryService;

    private String token;

    @BeforeEach
    void setUp() {
        Mockito.reset(aiService);
        User sme = createSME("stream.sme", "Stream SME");
        token = "Bearer " + jwtUtils.generateToken(sme.getEnterpriseId(), "SME");
        when(chatMemoryService.resolveSessionId(anyString(), any())).thenReturn("session-1");
        when(chatMemoryService.getRecentHistoryForPrompt(anyString(), anyString(), anyInt())).thenReturn(java.util.List.of());
    }

    @SuppressWarnings("unchecked")
    private void streamAnswers(java.util.function.BiFunction<Consumer<String>, BooleanSupplier, String> behaviour) {
        doAnswer(inv -> behaviour.apply(inv.getArgument(4, Consumer.class), inv.getArgument(5, BooleanSupplier.class)))
                .when(aiService).streamGeneralQuery(anyString(), anyList(), anyString(), anyString(), any(), any());
    }

    private String perform() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .header("Authorization", token)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"What are streams?\",\"sessionId\":\"session-1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5_000);
        return result.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("emits deltas in order, then done; saves the user message first and the answer last")
    void streamsDeltasThenDone() throws Exception {
        streamAnswers((onDelta, cancelled) -> {
            onDelta.accept("Hel");
            onDelta.accept("lo");
            return "Hello";
        });

        String body = perform();

        int first = body.indexOf("\"delta\":\"Hel\"");
        int second = body.indexOf("\"delta\":\"lo\"");
        int done = body.indexOf("event:done");
        assertThat(first).isGreaterThanOrEqualTo(0);
        assertThat(second).isGreaterThan(first);
        assertThat(done).isGreaterThan(second);

        var order = Mockito.inOrder(chatMemoryService);
        order.verify(chatMemoryService).saveUserMessage("stream.sme", "session-1", "What are streams?");
        order.verify(chatMemoryService).saveAssistantMessage("stream.sme", "session-1", "Hello");
    }

    @Test
    @DisplayName("a mid-stream failure sends an error event and saves the partial answer with a marker")
    void midStreamFailure() throws Exception {
        streamAnswers((onDelta, cancelled) -> {
            onDelta.accept("Partial ");
            throw new AIService.StreamInterruptedException("Partial ", new RuntimeException("reset"));
        });

        String body = perform();

        assertThat(body).contains("\"delta\":\"Partial \"").contains("event:error").doesNotContain("event:done");
        verify(chatMemoryService).saveAssistantMessage(eq("stream.sme"), eq("session-1"), eq("Partial " + AIController.INTERRUPTED_MARKER));
    }

    @Test
    @DisplayName("when the client goes away (emitter timeout) the model stream is cancelled")
    void disconnectCancelsStream() throws Exception {
        AtomicBoolean sawCancel = new AtomicBoolean();
        streamAnswers((onDelta, cancelled) -> {
            onDelta.accept("start ");
            long deadline = System.currentTimeMillis() + 5_000;
            while (!cancelled.getAsBoolean() && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            sawCancel.set(cancelled.getAsBoolean());
            throw new AIService.StreamCancelledException("start ");
        });

        MvcResult result = mockMvc.perform(post("/api/ai/chat/stream")
                        .header("Authorization", token)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Long answer please\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        // MockMvc does not fire async timeouts itself; simulate the container timing the request out.
        Thread.sleep(200);
        ((org.springframework.mock.web.MockAsyncContext) result.getRequest().getAsyncContext())
                .getListeners().forEach(listener -> {
                    try {
                        listener.onTimeout(new jakarta.servlet.AsyncEvent(result.getRequest().getAsyncContext()));
                    } catch (Exception ignored) {
                    }
                });

        verify(chatMemoryService, timeout(3_000)).saveAssistantMessage(eq("stream.sme"), eq("session-1"),
                startsWith("start "));
        assertThat(sawCancel.get()).isTrue();
    }
}
