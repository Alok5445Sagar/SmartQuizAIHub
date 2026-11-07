package com.smartquiz.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.repository.MCQRepository;

/**
 * Opt-in check against a real local Ollama. Run with:
 * OLLAMA_IT=true OLLAMA_MODEL=qwen2.5 mvn test -Dtest=AIServiceOllamaLiveIT
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_IT", matches = "true")
@DisplayName("AIService against a live Ollama")
class AIServiceOllamaLiveIT {

    private static AiCallGuard testGuard() {
        return new AiCallGuard(new AiCallGuard.Settings(1, 5, java.time.Duration.ofMinutes(5), java.time.Duration.ofMinutes(5),
                1, java.time.Duration.ofMillis(1), 50f, 4, 10, java.time.Duration.ofSeconds(30)));
    }

    @Test
    void generatesAQuestionWithLocalModel() {
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5:3b");

        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder().baseUrl(baseUrl).build())
                .defaultOptions(OllamaOptions.builder().model(model).build())
                .build();

        MCQRepository repo = mock(MCQRepository.class);
        when(repo.findNonDraftByStackAndTopic(any(), any(), any(), anyCollection())).thenReturn(Collections.emptyList());

        AIService service = new AIService(ChatClient.builder(chatModel));
        ReflectionTestUtils.setField(service, "mcqRepository", repo);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "model", model);
        ReflectionTestUtils.setField(service, "numCtx", 8192);
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.30d);
        ReflectionTestUtils.setField(service, "generateRetryMultiplier", 2);
        ReflectionTestUtils.setField(service, "generateMinAttempts", 2);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1200);
        ReflectionTestUtils.setField(service, "aiCallGuard", testGuard());
        ReflectionTestUtils.setField(service, "maxRequestBytes", 30000);
        service.init();

        TechnologyStack stack = new TechnologyStack();
        stack.setStackId(1L);
        stack.setStackName("Core Java");
        Topic topic = new Topic();
        topic.setTopicId(2L);
        topic.setTopicName("Collections Framework");

        List<MCQDTO.CreateRequest> generated = service.generateQuestions(
                stack, topic, "Medium", 1, 1L, 2L, "MCQ", Collections.emptyList());
        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getCorrectAnswer()).matches("[A-D]");

        String answer = service.answerGeneralQuery("In one sentence, what is a HashMap?", List.of(), "it", "SME");
        assertThat(answer).doesNotContain("[Fallback mode]").isNotBlank();
        System.out.println("LIVE MCQ: " + generated.get(0).getQuestionStem());
        System.out.println("LIVE CHAT: " + answer);
    }
}
