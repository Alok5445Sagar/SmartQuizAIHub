package com.smartquiz.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.exception.ServiceUnavailableException;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.repository.MCQRepository;

/**
 * Unit tests for AIService against a mocked Ollama ChatModel (no Spring context, no network).
 */
@DisplayName("AIService with Ollama ChatModel")
class AIServiceOllamaTest {

    private ChatModel chatModel;
    private MCQRepository mcqRepository;
    private AIService aiService;
    private TechnologyStack stack;
    private Topic topic;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        when(chatModel.getDefaultOptions()).thenReturn(OllamaOptions.builder().model("qwen2.5:3b").build());
        mcqRepository = mock(MCQRepository.class);
        when(mcqRepository.findNonDraftByStackAndTopic(any(), any(), any(), anyCollection()))
                .thenReturn(Collections.emptyList());

        aiService = new AIService(ChatClient.builder(chatModel));
        ReflectionTestUtils.setField(aiService, "mcqRepository", mcqRepository);
        ReflectionTestUtils.setField(aiService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(aiService, "model", "qwen2.5:3b");
        ReflectionTestUtils.setField(aiService, "numCtx", 8192);
        ReflectionTestUtils.setField(aiService, "similarityThreshold", 0.30d);
        ReflectionTestUtils.setField(aiService, "generateRetryMultiplier", 1);
        ReflectionTestUtils.setField(aiService, "generateMinAttempts", 1);
        ReflectionTestUtils.setField(aiService, "maxOutputTokens", 1200);
        ReflectionTestUtils.setField(aiService, "aiCallGuard", testGuard());
        ReflectionTestUtils.setField(aiService, "maxRequestBytes", 30000);
        aiService.init();

        stack = new TechnologyStack();
        stack.setStackId(1L);
        stack.setStackName("Core Java");
        topic = new Topic();
        topic.setTopicId(2L);
        topic.setTopicName("Collections Framework");
        topic.setStack(stack);
    }

    private AiCallGuard guard;

    private AiCallGuard testGuard() {
        // Small window so a few failures open the breaker; no retry delay.
        guard = new AiCallGuard(new AiCallGuard.Settings(1, 5, Duration.ofSeconds(5), Duration.ofSeconds(5),
                1, Duration.ofMillis(1), 50f, 2, 4, Duration.ofSeconds(30)));
        return guard;
    }

    @AfterEach
    void shutdownGuard() {
        if (guard != null) {
            guard.shutdown();
        }
    }

    private void modelReplies(String... contents) {
        var stubbing = when(chatModel.call(any(Prompt.class)));
        for (String content : contents) {
            stubbing = stubbing.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
        }
    }

    @Test
    @DisplayName("generates MCQs from a JSON array and sends Ollama options")
    void generateMcq_parsesJsonAndUsesOllamaOptions() {
        modelReplies("""
                [{"questionStem":"Which collection keeps insertion order and rejects duplicates?",
                  "optionA":"HashSet","optionB":"LinkedHashSet","optionC":"TreeMap","optionD":"ArrayList",
                  "correctAnswer":"B"}]
                """);

        List<MCQDTO.CreateRequest> result = aiService.generateQuestions(
                stack, topic, "Medium", 1, 1L, 2L, "MCQ", Collections.emptyList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCorrectAnswer()).isEqualTo("B");
        assertThat(result.get(0).getQuestionType()).isEqualTo("MCQ");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel).call(captor.capture());
        OllamaOptions options = (OllamaOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("qwen2.5:3b");
        assertThat(options.getNumCtx()).isEqualTo(8192);
    }

    @Test
    @DisplayName("generates MSQs with multiple sorted answers wrapped in a code fence")
    void generateMsq_parsesFencedJson() {
        modelReplies("""
                ```json
                {"questions":[{"questionStem":"Which interfaces extend Collection?",
                  "optionA":"List","optionB":"Map","optionC":"Set","optionD":"Iterator","optionE":"Queue",
                  "correctAnswer":"E, A, C"}]}
                ```
                """);

        List<MCQDTO.CreateRequest> result = aiService.generateQuestions(
                stack, topic, "Easy", 1, 1L, 2L, "MSQ", Collections.emptyList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCorrectAnswer()).isEqualTo("A,C,E");
        assertThat(result.get(0).getOptionE()).isEqualTo("Queue");
    }

    @Test
    @DisplayName("parses the model's similarity JSON when the deterministic check passes")
    void checkSimilarity_usesModelVerdict() {
        MCQ existing = new MCQ();
        existing.setQuestionId(42L);
        existing.setQuestionStem("How does a HashMap resolve bucket collisions?");
        existing.setOptionA("Chaining");
        existing.setOptionB("Sorting");
        existing.setOptionC("Locking");
        existing.setOptionD("Copying");
        existing.setCorrectAnswer("A");
        when(mcqRepository.findNonDraftByStackAndTopic(any(), any(), isNull(), anyCollection()))
                .thenReturn(List.of(existing));
        modelReplies("[{\"id\": 42, \"similarity\": 55}]");

        SimilarityCheckResponse response = aiService.checkSimilarity(
                "What happens internally when two keys land in the same map slot?",
                "Linked entries", "Exception", "Resize", "Nothing", null, null, 1L, 2L, null);

        assertThat(response.isHasSimilar()).isTrue();
        assertThat(response.getSimilarQuestions()).extracting(SimilarityCheckResponse.SimilarQuestion::getQuestionId)
                .containsExactly(42L);
        assertThat(response.getSimilarQuestions().get(0).getSimilarityPercent()).isEqualTo(55);
    }

    @Test
    @DisplayName("maps a connection failure to a clear Ollama-unavailable message")
    void connectionRefused_mapsToClearMessage() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("I/O error on POST request: Connection refused"));

        assertThatThrownBy(() -> aiService.generateQuestions(
                stack, topic, "Medium", 1, 1L, 2L, "MCQ", Collections.emptyList()))
                .hasMessageContaining("Ollama");
    }

    @Test
    @DisplayName("generation asks Ollama for schema-constrained JSON; chat does not")
    void generation_usesJsonSchemaFormat() {
        modelReplies("""
                {"questions":[{"questionStem":"Which map keeps keys sorted?","optionA":"HashMap","optionB":"TreeMap",
                  "optionC":"IdentityHashMap","optionD":"WeakHashMap","correctAnswer":"B"}]}
                """, "Plain chat answer");

        aiService.generateQuestions(stack, topic, "Medium", 1, 1L, 2L, "MCQ", Collections.emptyList());
        aiService.answerGeneralQuery("Hi", List.of(), "sme", "SME");

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        Mockito.verify(chatModel, Mockito.times(2)).call(captor.capture());
        Object generationFormat = ((OllamaOptions) captor.getAllValues().get(0).getOptions()).getFormat();
        assertThat(generationFormat).isEqualTo(AIService.questionsSchema(false));
        assertThat(((OllamaOptions) captor.getAllValues().get(1).getOptions()).getFormat()).isNull();
    }

    @Test
    @DisplayName("malformed JSON falls back to the loose text parser")
    void malformedJson_fallsBackToLooseParser() {
        modelReplies("""
                1. Which collection allows duplicate elements?
                A) Set
                B) List
                C) SortedSet
                D) EnumSet
                Answer: B
                """);

        List<MCQDTO.CreateRequest> result = aiService.generateQuestions(
                stack, topic, "Easy", 1, 1L, 2L, "MCQ", Collections.emptyList());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOptionB()).isEqualTo("List");
        assertThat(result.get(0).getCorrectAnswer()).isEqualTo("B");
    }

    @Test
    @DisplayName("once the breaker opens: chat serves the fallback, generation returns 503 without calling Ollama")
    void openBreaker_fallbackAndFastFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("I/O error: Connection refused"));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> aiService.generateQuestions(
                    stack, topic, "Medium", 1, 1L, 2L, "MCQ", Collections.emptyList()))
                    .isInstanceOf(ServiceUnavailableException.class);
        }
        assertThat(guard.circuitState()).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
        Mockito.clearInvocations(chatModel);

        assertThat(aiService.answerGeneralQuery("Explain Java streams", List.of(), "sme", "SME")).contains("[Fallback mode]");
        assertThatThrownBy(() -> aiService.generateQuestions(
                stack, topic, "Medium", 1, 1L, 2L, "MCQ", Collections.emptyList()))
                .isInstanceOfSatisfying(ServiceUnavailableException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(30));
        Mockito.verify(chatModel, Mockito.never()).call(any(Prompt.class));
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    @DisplayName("streaming chat relays each model chunk in order and returns the full answer")
    void streamChat_relaysChunksInOrder() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(reactor.core.publisher.Flux.just(chunk("Java "), chunk("streams "), chunk("are lazy.")));
        List<String> deltas = new java.util.ArrayList<>();

        String answer = aiService.streamGeneralQuery("What are streams?", List.of(), "sme", "SME", deltas::add, () -> false);

        assertThat(deltas).containsExactly("Java ", "streams ", "are lazy.");
        assertThat(answer).isEqualTo("Java streams are lazy.");
    }

    @Test
    @DisplayName("streaming chat stops pulling tokens once the client cancels")
    void streamChat_stopsWhenCancelled() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(reactor.core.publisher.Flux.just(chunk("one "), chunk("two "), chunk("three ")));
        List<String> deltas = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();

        assertThatThrownBy(() -> aiService.streamGeneralQuery("Count", List.of(), "sme", "SME",
                d -> { deltas.add(d); cancelled.set(true); }, cancelled::get))
                .isInstanceOfSatisfying(AIService.StreamCancelledException.class,
                        ex -> assertThat(ex.getPartialAnswer()).isEqualTo("one "));
        assertThat(deltas).containsExactly("one ");
    }

    @Test
    @DisplayName("a failure after some tokens surfaces the partial answer")
    void streamChat_failureMidStream() {
        when(chatModel.stream(any(Prompt.class))).thenReturn(reactor.core.publisher.Flux.concat(
                reactor.core.publisher.Flux.just(chunk("Partial ")),
                reactor.core.publisher.Flux.error(new RuntimeException("connection reset by peer"))));

        assertThatThrownBy(() -> aiService.streamGeneralQuery("Q", List.of(), "sme", "SME", d -> { }, () -> false))
                .isInstanceOfSatisfying(AIService.StreamInterruptedException.class,
                        ex -> assertThat(ex.getPartialAnswer()).isEqualTo("Partial "));
    }

    @Test
    @DisplayName("when Ollama is down before any token, the offline fallback is streamed as one delta")
    void streamChat_fallbackWhenDown() {
        when(chatModel.stream(any(Prompt.class)))
                .thenReturn(reactor.core.publisher.Flux.error(new RuntimeException("Connection refused")));
        List<String> deltas = new java.util.ArrayList<>();

        String answer = aiService.streamGeneralQuery("Explain Java streams", List.of(), "sme", "SME", deltas::add, () -> false);

        assertThat(answer).contains("[Fallback mode]");
        assertThat(deltas).containsExactly(answer);
    }

    @Test
    @DisplayName("chat falls back to offline guidance when Ollama is down")
    void chat_fallsBackWhenOllamaDown() {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        String answer = aiService.answerGeneralQuery("Explain Java streams", List.of(), "sme", "SME");

        assertThat(answer).contains("[Fallback mode]");
        assertThat(aiService.getActiveModelName()).isEqualTo("qwen2.5:3b");
    }
}
