package com.smartquiz.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartquiz.BaseTest;
import com.smartquiz.dto.QuizDTO;
import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import com.smartquiz.entity.QuizQuestion;
import com.smartquiz.entity.TechnologyStack;
import com.smartquiz.entity.Topic;
import com.smartquiz.entity.User;
import com.smartquiz.repository.MCQRepository;
import com.smartquiz.repository.QuizAttemptRepository;
import com.smartquiz.repository.QuizQuestionRepository;
import com.smartquiz.repository.QuizRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@DisplayName("Quiz Admin Controller Integration Tests")
class QuizAdminControllerIntegrationTest extends BaseTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MCQRepository mcqRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAttemptRepository quizAttemptRepository;

    @Test
    @WithMockUser(username = "admin.publish.dynamic", roles = "ADMIN")
    @DisplayName("Publish endpoint should accept dynamic MCQ/MSQ composition")
    void publishEndpoint_shouldAcceptDynamicComposition() throws Exception {
        User admin = createAdmin("admin.publish.dynamic");
        TechnologyStack stack = createStack("Publish Dynamic Stack");
        Topic topic = createTopic("Publish Dynamic Topic", stack);

        List<MCQ> approved = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            approved.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ));
        }
        for (int i = 0; i < 4; i++) {
            approved.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ));
        }

        QuizDTO.PublishRequest request = new QuizDTO.PublishRequest();
        request.setTopicIds(List.of(topic.getTopicId()));
        request.setTitle("Dynamic Publish Quiz");
        request.setTotalQuestions(12);
        request.setMcqCount(8);
        request.setMsqCount(4);
        request.setQuestionIds(approved.stream().map(MCQ::getQuestionId).collect(Collectors.toList()));

        MvcResult result = mockMvc.perform(post("/api/admin/quizzes/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.questionCount").value(12))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Long quizId = body.get("quizId").asLong();
        Quiz quiz = quizRepository.findById(quizId).orElseThrow();
        assertThat(quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(quiz)).hasSize(12);
    }

    @Test
    @WithMockUser(username = "admin.publish.invalid", roles = "ADMIN")
    @DisplayName("Publish endpoint should reject mismatched dynamic composition")
    void publishEndpoint_shouldRejectMismatchedDynamicComposition() throws Exception {
        User admin = createAdmin("admin.publish.invalid");
        TechnologyStack stack = createStack("Publish Invalid Stack");
        Topic topic = createTopic("Publish Invalid Topic", stack);

        List<MCQ> approved = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            approved.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ));
        }
        for (int i = 0; i < 2; i++) {
            approved.add(createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ));
        }

        QuizDTO.PublishRequest request = new QuizDTO.PublishRequest();
        request.setTopicIds(List.of(topic.getTopicId()));
        request.setTitle("Invalid Dynamic Publish Quiz");
        request.setTotalQuestions(6);
        request.setMcqCount(5);
        request.setMsqCount(1);
        request.setQuestionIds(approved.stream().map(MCQ::getQuestionId).collect(Collectors.toList()));

        mockMvc.perform(post("/api/admin/quizzes/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Quiz must include exactly 5 MCQ and 1 MSQ questions"));
    }

            @Test
            @WithMockUser(username = "admin.publish.multitopic", roles = "ADMIN")
            @DisplayName("Publish endpoint should allow selected questions from multiple topics")
            void publishEndpoint_shouldAllowMultipleTopics() throws Exception {
            User admin = createAdmin("admin.publish.multitopic");

            TechnologyStack stackA = createStack("Publish MultiTopic Stack A");
            Topic topicA = createTopic("Publish MultiTopic Topic A", stackA);

            TechnologyStack stackB = createStack("Publish MultiTopic Stack B");
            Topic topicB = createTopic("Publish MultiTopic Topic B", stackB);

            List<MCQ> approved = new ArrayList<>();
            approved.add(createApprovedQuestion(admin, stackA, topicA, MCQ.QuestionType.MCQ));
            approved.add(createApprovedQuestion(admin, stackA, topicA, MCQ.QuestionType.MCQ));
            approved.add(createApprovedQuestion(admin, stackA, topicA, MCQ.QuestionType.MCQ));
            approved.add(createApprovedQuestion(admin, stackB, topicB, MCQ.QuestionType.MCQ));
            approved.add(createApprovedQuestion(admin, stackB, topicB, MCQ.QuestionType.MSQ));
            approved.add(createApprovedQuestion(admin, stackA, topicA, MCQ.QuestionType.MSQ));

            QuizDTO.PublishRequest request = new QuizDTO.PublishRequest();
            request.setTopicIds(List.of(topicA.getTopicId(), topicB.getTopicId()));
            request.setTitle("Cross Topic Publish Quiz");
            request.setTotalQuestions(6);
            request.setMcqCount(4);
            request.setMsqCount(2);
            request.setQuestionIds(approved.stream().map(MCQ::getQuestionId).collect(Collectors.toList()));

            MvcResult result = mockMvc.perform(post("/api/admin/quizzes/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.questionCount").value(6))
                .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            Long quizId = body.get("quizId").asLong();
            Quiz savedQuiz = quizRepository.findById(quizId).orElseThrow();

            List<QuizQuestion> savedQuestions = quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(savedQuiz);
            assertThat(savedQuestions).hasSize(6);

            List<Long> savedQuestionIds = savedQuestions.stream()
                .map(qq -> qq.getQuestion().getQuestionId())
                .collect(Collectors.toList());

            assertThat(savedQuestionIds).containsExactlyElementsOf(
                approved.stream().map(MCQ::getQuestionId).collect(Collectors.toList())
            );
            }

    @Test
    @WithMockUser(username = "admin.autopick.dynamic", roles = "ADMIN")
    @DisplayName("Auto-pick endpoint should accept dynamic composition and difficulty totals")
    void autoPickEndpoint_shouldAcceptDynamicPayload() throws Exception {
        User admin = createAdmin("admin.autopick.dynamic");
        TechnologyStack stack = createStack("AutoPick Dynamic Stack");
        Topic topic = createTopic("AutoPick Dynamic Topic", stack);

        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);
        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Medium);
        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Hard);
        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Medium);
        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard);

        QuizDTO.AutoPickRequest request = new QuizDTO.AutoPickRequest();
        request.setTopicId(topic.getTopicId());
        request.setTotalQuestions(6);
        request.setMcqCount(4);
        request.setMsqCount(2);
        request.setEasyCount(2);
        request.setMediumCount(2);
        request.setHardCount(2);

        mockMvc.perform(post("/api/admin/quizzes/auto-pick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCount").value(6))
                .andExpect(jsonPath("$.questionIds.length()").value(6));
    }

    @Test
    @WithMockUser(username = "admin.autopick.filters", roles = "ADMIN")
    @DisplayName("Auto-pick endpoint should support topicIds and stackIds filters without topicId")
    void autoPickEndpoint_shouldUseTopicIdsAndStackIdsWithoutTopicId() throws Exception {
        User admin = createAdmin("admin.autopick.filters");

        TechnologyStack selectedStack = createStack("AutoPick Filter Stack Selected");
        Topic selectedTopic = createTopic("AutoPick Filter Topic Selected", selectedStack);

        TechnologyStack otherStack = createStack("AutoPick Filter Stack Other");
        Topic otherTopic = createTopic("AutoPick Filter Topic Other", otherStack);

        List<MCQ> selectedPool = new ArrayList<>();
        selectedPool.add(createApprovedQuestion(admin, selectedStack, selectedTopic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy));
        selectedPool.add(createApprovedQuestion(admin, selectedStack, selectedTopic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy));
        selectedPool.add(createApprovedQuestion(admin, selectedStack, selectedTopic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Medium));
        selectedPool.add(createApprovedQuestion(admin, selectedStack, selectedTopic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Hard));
        selectedPool.add(createApprovedQuestion(admin, selectedStack, selectedTopic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Medium));
        selectedPool.add(createApprovedQuestion(admin, selectedStack, selectedTopic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard));

        List<MCQ> otherPool = new ArrayList<>();
        otherPool.add(createApprovedQuestion(admin, otherStack, otherTopic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy));
        otherPool.add(createApprovedQuestion(admin, otherStack, otherTopic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Medium));
        otherPool.add(createApprovedQuestion(admin, otherStack, otherTopic, MCQ.QuestionType.MSQ, MCQ.Difficulty.Hard));

        QuizDTO.AutoPickRequest request = new QuizDTO.AutoPickRequest();
        request.setStackIds(List.of(selectedStack.getStackId()));
        request.setTopicIds(List.of(selectedTopic.getTopicId()));
        request.setTotalQuestions(6);
        request.setMcqCount(4);
        request.setMsqCount(2);
        request.setEasyCount(2);
        request.setMediumCount(2);
        request.setHardCount(2);

        MvcResult result = mockMvc.perform(post("/api/admin/quizzes/auto-pick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCount").value(6))
                .andExpect(jsonPath("$.questionIds.length()").value(6))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Set<Long> pickedIds = new HashSet<>();
        for (JsonNode idNode : body.get("questionIds")) {
            pickedIds.add(idNode.asLong());
        }

        Set<Long> selectedPoolIds = selectedPool.stream().map(MCQ::getQuestionId).collect(Collectors.toSet());
        Set<Long> otherPoolIds = otherPool.stream().map(MCQ::getQuestionId).collect(Collectors.toSet());

        assertThat(pickedIds).isSubsetOf(selectedPoolIds);
        assertThat(pickedIds).doesNotContainAnyElementsOf(otherPoolIds);
    }

    @Test
    @WithMockUser(username = "admin.autopick.invalid", roles = "ADMIN")
    @DisplayName("Auto-pick endpoint should reject invalid dynamic difficulty total")
    void autoPickEndpoint_shouldRejectInvalidDifficultyTotal() throws Exception {
        User admin = createAdmin("admin.autopick.invalid");
        TechnologyStack stack = createStack("AutoPick Invalid Stack");
        Topic topic = createTopic("AutoPick Invalid Topic", stack);

        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);

        QuizDTO.AutoPickRequest request = new QuizDTO.AutoPickRequest();
        request.setTopicId(topic.getTopicId());
        request.setTotalQuestions(6);
        request.setMcqCount(4);
        request.setMsqCount(2);
        request.setEasyCount(1);
        request.setMediumCount(2);
        request.setHardCount(2);

        mockMvc.perform(post("/api/admin/quizzes/auto-pick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Difficulty distribution must total exactly 6 questions"));
    }

    @Test
    @WithMockUser(username = "admin.autopick.typesplit", roles = "ADMIN")
    @DisplayName("Auto-pick endpoint should reject mismatched MCQ/MSQ type split")
    void autoPickEndpoint_shouldRejectMismatchedTypeSplit() throws Exception {
        User admin = createAdmin("admin.autopick.typesplit");
        TechnologyStack stack = createStack("AutoPick TypeSplit Stack");
        Topic topic = createTopic("AutoPick TypeSplit Topic", stack);

        createApprovedQuestion(admin, stack, topic, MCQ.QuestionType.MCQ, MCQ.Difficulty.Easy);

        QuizDTO.AutoPickRequest request = new QuizDTO.AutoPickRequest();
        request.setTopicId(topic.getTopicId());
        request.setTotalQuestions(6);
        request.setMcqCount(4);
        request.setMsqCount(1);
        request.setEasyCount(2);
        request.setMediumCount(2);
        request.setHardCount(2);

        mockMvc.perform(post("/api/admin/quizzes/auto-pick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("MCQ + MSQ must total exactly 6 questions"));
    }

    @Test
    @WithMockUser(username = "admin.reopen.api", roles = "ADMIN")
    @DisplayName("Reopen endpoint should publish a previously closed quiz")
    void reopenEndpoint_shouldReopenClosedQuiz() throws Exception {
        User admin = createAdmin("admin.reopen.api");
        Quiz closedQuiz = createClosedQuizWithQuestions(admin, "Reopen Quiz", 2);

        mockMvc.perform(post("/api/admin/quizzes/{quizId}/reopen", closedQuiz.getQuizId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizId").value(closedQuiz.getQuizId()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        Quiz updated = quizRepository.findById(closedQuiz.getQuizId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(Quiz.Status.PUBLISHED);
    }

    @Test
    @WithMockUser(username = "admin.rehost.api", roles = "ADMIN")
    @DisplayName("Rehost endpoint should create a new live quiz from closed quiz")
    void rehostEndpoint_shouldCreateNewQuiz() throws Exception {
        User admin = createAdmin("admin.rehost.api");
        Quiz closedQuiz = createClosedQuizWithQuestions(admin, "Rehost Quiz", 2);
        createAttempt(closedQuiz, "Legacy Participant", null, 2, 2, 2);

        MvcResult result = mockMvc.perform(post("/api/admin/quizzes/{quizId}/rehost", closedQuiz.getQuizId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Long newQuizId = body.get("quizId").asLong();
        String newQuizCode = body.get("quizCode").asText();

        assertThat(newQuizId).isNotEqualTo(closedQuiz.getQuizId());
        assertThat(newQuizCode).isNotEqualTo(closedQuiz.getQuizCode());
        assertThat(quizAttemptRepository.findLeaderboardByQuiz(closedQuiz)).hasSize(1);

        Quiz newQuiz = quizRepository.findById(newQuizId).orElseThrow();
        assertThat(quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(newQuiz)).hasSize(2);
        assertThat(quizAttemptRepository.findLeaderboardByQuiz(newQuiz)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin.export.api", roles = "ADMIN")
    @DisplayName("Export endpoint should return valid leaderboard Excel")
    void exportEndpoint_shouldReturnExcel() throws Exception {
        User admin = createAdmin("admin.export.api");
        Quiz closedQuiz = createClosedQuizWithQuestions(admin, "Export Quiz", 2);
        createAttempt(closedQuiz, "Excel Participant", "EMP-XLS", 2, 2, 2);

        MvcResult result = mockMvc.perform(get("/api/admin/quizzes/{quizId}/leaderboard/export", closedQuiz.getQuizId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheet("Leaderboard")).isNotNull();
            assertThat(workbook.getSheet("Leaderboard").getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("Leaderboard").getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("Excel Participant");
        }
    }

    @Test
    @WithMockUser(username = "admin.analytics.api", roles = "ADMIN")
    @DisplayName("Participant analytics endpoint should return topic-wise engagement and performance by participant type")
    void participantAnalytics_shouldReturnTopicWiseByParticipantType() throws Exception {
        User admin = createAdmin("admin.analytics.api");
        Quiz quiz = createClosedQuizWithQuestions(admin, "Analytics Quiz", 3);

        createAttempt(quiz, "Employee One", "EMP-AN-1", 2, 3, 2);
        createAttempt(quiz, "Guest One", null, 1, 3, 1);

        mockMvc.perform(get("/api/admin/quizzes/participant-analytics")
                        .param("participantType", "EMPLOYEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantType").value("EMPLOYEE"))
            .andExpect(jsonPath("$.quizAnalytics.totalQuizzesConducted").value(1))
            .andExpect(jsonPath("$.topicPerformance.length()").value(1))
            .andExpect(jsonPath("$.questionAnalytics.length()").value(0))
            .andExpect(jsonPath("$.userEngagement.activeUsers").value(1))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].participantName").value("Employee One"))
                .andExpect(jsonPath("$.rows[0].participantType").value("EMPLOYEE"))
                .andExpect(jsonPath("$.rows[0].hostedQuizCount").value(1))
                .andExpect(jsonPath("$.rows[0].attendedQuizCount").value(1))
                .andExpect(jsonPath("$.rows[0].correctAnswers").value(2))
                .andExpect(jsonPath("$.rows[0].totalQuestions").value(3));

        mockMvc.perform(get("/api/admin/quizzes/participant-analytics")
                        .param("participantType", "GUEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantType").value("GUEST"))
            .andExpect(jsonPath("$.quizAnalytics.totalParticipants").value(1))
            .andExpect(jsonPath("$.topicPerformance.length()").value(1))
            .andExpect(jsonPath("$.questionAnalytics.length()").value(0))
            .andExpect(jsonPath("$.userEngagement.activeUsers").value(1))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].participantName").value("Guest One"))
                .andExpect(jsonPath("$.rows[0].participantType").value("GUEST"));
    }

    private Quiz createClosedQuizWithQuestions(User admin, String title, int questionCount) {
        TechnologyStack stack = createStack("Admin Quiz API Stack " + title);
        Topic topic = createTopic("Admin Quiz API Topic " + title, stack);

        Quiz quiz = new Quiz();
        quiz.setQuizCode("CLSD" + System.nanoTime());
        quiz.setTitle(title);
        quiz.setStack(stack);
        quiz.setTopic(topic);
        quiz.setCreatedBy(admin);
        quiz.setStatus(Quiz.Status.CLOSED);
        quiz.setPublishedAt(LocalDateTime.now().minusHours(2));
        quiz.setClosedAt(LocalDateTime.now().minusHours(1));
        Quiz savedQuiz = quizRepository.save(quiz);

        List<QuizQuestion> quizQuestions = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            MCQ question = createMCQ(admin, stack, topic, MCQ.Status.APPROVED);
            QuizQuestion qq = new QuizQuestion();
            qq.setQuiz(savedQuiz);
            qq.setQuestion(question);
            qq.setQuestionOrder(i + 1);
            quizQuestions.add(qq);
        }
        quizQuestionRepository.saveAll(quizQuestions);

        return savedQuiz;
    }

    private void createAttempt(Quiz quiz,
                               String participantName,
                               String participantEnterpriseId,
                               int score,
                               int totalQuestions,
                               int correctCount) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setParticipantName(participantName);
        attempt.setParticipantEnterpriseId(participantEnterpriseId);
        attempt.setScore(score);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setCorrectCount(correctCount);
        quizAttemptRepository.save(attempt);
    }

    private MCQ createApprovedQuestion(User creator,
                                       TechnologyStack stack,
                                       Topic topic,
                                       MCQ.QuestionType questionType) {
        MCQ mcq = createMCQ(creator, stack, topic, MCQ.Status.APPROVED);
        mcq.setStatus(MCQ.Status.APPROVED);
        mcq.setQuestionType(questionType);
        mcq.setDifficulty(MCQ.Difficulty.Easy);
        if (questionType == MCQ.QuestionType.MSQ) {
            mcq.setOptionE("Option E");
            mcq.setCorrectAnswer("A,C");
        } else {
            mcq.setCorrectAnswer("A");
        }
        return mcqRepository.save(mcq);
    }

    private MCQ createApprovedQuestion(User creator,
                                       TechnologyStack stack,
                                       Topic topic,
                                       MCQ.QuestionType questionType,
                                       MCQ.Difficulty difficulty) {
        MCQ mcq = createApprovedQuestion(creator, stack, topic, questionType);
        mcq.setDifficulty(difficulty);
        return mcqRepository.save(mcq);
    }
}
