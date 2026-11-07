package com.smartquiz.service;

import org.springframework.security.access.prepost.PreAuthorize;

import com.smartquiz.exception.NotFoundException;
import com.smartquiz.exception.ConflictException;
import com.smartquiz.exception.BusinessRuleException;
import com.smartquiz.exception.UnauthorizedException;

import com.smartquiz.dto.QuizDTO;
import com.smartquiz.entity.*;
import com.smartquiz.repository.*;
import com.smartquiz.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private static final Logger logger = LoggerFactory.getLogger(QuizService.class);

    private static final int DEFAULT_QUIZ_TOTAL_QUESTIONS = 15;
    private static final int DEFAULT_QUIZ_MCQ_COUNT = 10;
    private static final int DEFAULT_QUIZ_MSQ_COUNT = 5;
    private static final int DEFAULT_QUIZ_DURATION_MINUTES = 15;
    private static final long PARTICIPANT_DASHBOARD_SLOW_THRESHOLD_MS = 250;

    private final Random random = new Random();

    @Value("${app.quiz.public-base-url:http://localhost:3000}")
    private String quizPublicBaseUrl;

    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private QuizAttemptRepository quizAttemptRepository;
    @Autowired private QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    @Autowired private MCQRepository mcqRepository;
    @Autowired private MCQTranslationRepository mcqTranslationRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.smartquiz.security.LoginAttemptGuard loginAttemptGuard;
    @Autowired private JwtUtils jwtUtils;

    @PreAuthorize("hasRole('ADMIN')")

    public List<QuizDTO.QuestionChoice> getApprovedQuestionsByFilters(List<Long> stackIds, List<Long> topicIds) {
        List<Long> safeStackIds = stackIds == null ? Collections.emptyList() : stackIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> safeTopicIds = topicIds == null ? Collections.emptyList() : topicIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

        if (safeStackIds.isEmpty() && safeTopicIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one topic or subtopic before loading approved questions");
        }

        return mcqRepository.findApprovedByStackIdsAndTopicIds(
                        MCQ.Status.APPROVED,
                        safeStackIds,
                        safeStackIds.isEmpty(),
                        safeTopicIds,
                        safeTopicIds.isEmpty())
                .stream()
                .map(QuizDTO.QuestionChoice::from)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")

    public QuizDTO.AutoPickResponse autoPickQuestions(QuizDTO.AutoPickRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Auto-pick request is required");
        }

        int totalQuestions = resolveTotalQuestions(request.getTotalQuestions());
        int mcqCount = resolveTypeCount(request.getMcqCount(), DEFAULT_QUIZ_MCQ_COUNT, "MCQ");
        int msqCount = resolveTypeCount(request.getMsqCount(), DEFAULT_QUIZ_MSQ_COUNT, "MSQ");
        validateTypeComposition(totalQuestions, mcqCount, msqCount);

        int easyCount = normalizeDifficultyCount(request.getEasyCount(), "Easy");
        int mediumCount = normalizeDifficultyCount(request.getMediumCount(), "Medium");
        int hardCount = normalizeDifficultyCount(request.getHardCount(), "Hard");

        if (easyCount + mediumCount + hardCount != totalQuestions) {
            throw new BusinessRuleException("Difficulty distribution must total exactly " + totalQuestions + " questions");
        }

        List<Long> safeStackIds = request.getStackIds() == null
            ? new ArrayList<>()
            : request.getStackIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        List<Long> safeTopicIds = request.getTopicIds() == null
            ? new ArrayList<>()
            : request.getTopicIds().stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());

        if (request.getTopicId() != null && !safeTopicIds.contains(request.getTopicId())) {
            safeTopicIds.add(request.getTopicId());
        }

        if (safeStackIds.isEmpty() && safeTopicIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one topic or subtopic before auto-pick");
        }

        List<MCQ> approvedQuestions = mcqRepository.findApprovedByStackIdsAndTopicIds(
            MCQ.Status.APPROVED,
            safeStackIds,
            safeStackIds.isEmpty(),
            safeTopicIds,
            safeTopicIds.isEmpty());

        if (approvedQuestions.size() < totalQuestions) {
            throw new BusinessRuleException("Not enough approved questions to auto-pick a quiz");
        }

        long availableMcq = approvedQuestions.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MCQ).count();
        long availableMsq = approvedQuestions.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MSQ).count();
        if (availableMcq < mcqCount || availableMsq < msqCount) {
            throw new BusinessRuleException("Auto-pick requires at least " + mcqCount + " approved MCQ and " + msqCount + " approved MSQ questions");
        }

        List<Long> candidateIds = approvedQuestions.stream().map(MCQ::getQuestionId).collect(Collectors.toList());
        Set<Long> usedQuestionIds = new HashSet<>(quizQuestionRepository.findDistinctUsedQuestionIdsIn(candidateIds));
        List<MCQ> unusedQuestions = approvedQuestions.stream()
                .filter(q -> !usedQuestionIds.contains(q.getQuestionId()))
                .collect(Collectors.toList());

        List<MCQ> selected = selectWithTypeFirstDifficultyFallback(unusedQuestions, totalQuestions, mcqCount, msqCount, easyCount, mediumCount, hardCount);
        if (selected == null) {
            selected = selectWithTypeFirstDifficultyFallback(approvedQuestions, totalQuestions, mcqCount, msqCount, easyCount, mediumCount, hardCount);
        }

        if (selected == null || selected.size() != totalQuestions) {
            throw new BusinessRuleException("Unable to auto-pick questions for the requested type composition");
        }

        List<Long> selectedIds = selected.stream().map(MCQ::getQuestionId).collect(Collectors.toList());
        int reusedSelectedCount = (int) selected.stream().filter(q -> usedQuestionIds.contains(q.getQuestionId())).count();
        int unusedSelectedCount = totalQuestions - reusedSelectedCount;

        return new QuizDTO.AutoPickResponse(selectedIds, selectedIds.size(), unusedSelectedCount, reusedSelectedCount);
    }

    private List<MCQ> selectWithTypeFirstDifficultyFallback(List<MCQ> pool,
                                                            int totalQuestions,
                                                            int mcqCount,
                                                            int msqCount,
                                                            int easyCount,
                                                            int mediumCount,
                                                            int hardCount) {
        if (pool == null || pool.size() < totalQuestions) {
            return null;
        }

        List<MCQ> mcqPool = pool.stream()
                .filter(q -> q.getQuestionType() == MCQ.QuestionType.MCQ)
                .collect(Collectors.toCollection(ArrayList::new));
        List<MCQ> msqPool = pool.stream()
                .filter(q -> q.getQuestionType() == MCQ.QuestionType.MSQ)
                .collect(Collectors.toCollection(ArrayList::new));

        if (mcqPool.size() < mcqCount || msqPool.size() < msqCount) {
            return null;
        }

        EnumMap<MCQ.Difficulty, Integer> remainingDifficulty = new EnumMap<>(MCQ.Difficulty.class);
        remainingDifficulty.put(MCQ.Difficulty.Easy, easyCount);
        remainingDifficulty.put(MCQ.Difficulty.Medium, mediumCount);
        remainingDifficulty.put(MCQ.Difficulty.Hard, hardCount);

        List<MCQ> selected = new ArrayList<>(totalQuestions);
        List<MCQ> selectedMcq = pickByTypeWithDifficultyFallback(mcqPool, mcqCount, remainingDifficulty);
        if (selectedMcq == null) {
            return null;
        }

        List<MCQ> selectedMsq = pickByTypeWithDifficultyFallback(msqPool, msqCount, remainingDifficulty);
        if (selectedMsq == null) {
            return null;
        }

        selected.addAll(selectedMcq);
        selected.addAll(selectedMsq);
        Collections.shuffle(selected, random);
        return selected;
    }

    private List<MCQ> pickByTypeWithDifficultyFallback(List<MCQ> candidates,
                                                       int targetCount,
                                                       EnumMap<MCQ.Difficulty, Integer> remainingDifficulty) {
        List<MCQ> mutableCandidates = new ArrayList<>(candidates);
        List<MCQ> selected = new ArrayList<>(targetCount);

        for (int i = 0; i < targetCount; i++) {
            EnumMap<MCQ.Difficulty, List<MCQ>> byDifficulty = new EnumMap<>(MCQ.Difficulty.class);
            byDifficulty.put(MCQ.Difficulty.Easy, new ArrayList<>());
            byDifficulty.put(MCQ.Difficulty.Medium, new ArrayList<>());
            byDifficulty.put(MCQ.Difficulty.Hard, new ArrayList<>());

            for (MCQ candidate : mutableCandidates) {
                byDifficulty.get(candidate.getDifficulty()).add(candidate);
            }

            MCQ.Difficulty preferred = null;
            int bestRemaining = Integer.MIN_VALUE;
            for (MCQ.Difficulty difficulty : List.of(MCQ.Difficulty.Easy, MCQ.Difficulty.Medium, MCQ.Difficulty.Hard)) {
                if (byDifficulty.get(difficulty).isEmpty()) {
                    continue;
                }
                int remaining = remainingDifficulty.getOrDefault(difficulty, 0);
                if (remaining > 0 && remaining > bestRemaining) {
                    bestRemaining = remaining;
                    preferred = difficulty;
                }
            }

            if (preferred == null) {
                int maxAvailable = -1;
                for (MCQ.Difficulty difficulty : List.of(MCQ.Difficulty.Easy, MCQ.Difficulty.Medium, MCQ.Difficulty.Hard)) {
                    int available = byDifficulty.get(difficulty).size();
                    if (available > maxAvailable) {
                        maxAvailable = available;
                        preferred = difficulty;
                    }
                }
            }

            if (preferred == null || byDifficulty.get(preferred).isEmpty()) {
                return null;
            }

            List<MCQ> poolForPick = byDifficulty.get(preferred);
            MCQ picked = poolForPick.get(random.nextInt(poolForPick.size()));
            selected.add(picked);
            mutableCandidates.removeIf(q -> Objects.equals(q.getQuestionId(), picked.getQuestionId()));
            remainingDifficulty.put(preferred, remainingDifficulty.getOrDefault(preferred, 0) - 1);
        }

        return selected;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDTO.PublishedQuizSummary publishQuiz(String adminEnterpriseId, QuizDTO.PublishRequest request) {
        if (request.getQuestionIds() == null || request.getQuestionIds().isEmpty()) {
            throw new BusinessRuleException("Please select quiz questions");
        }

        List<Long> distinctQuestionIds = request.getQuestionIds().stream().distinct().collect(Collectors.toList());
        int totalQuestions = resolveTotalQuestions(request.getTotalQuestions() != null ? request.getTotalQuestions() : distinctQuestionIds.size());
        int mcqCount = resolveTypeCount(request.getMcqCount(), DEFAULT_QUIZ_MCQ_COUNT, "MCQ");
        int msqCount = resolveTypeCount(request.getMsqCount(), DEFAULT_QUIZ_MSQ_COUNT, "MSQ");
        int durationMinutes = resolveDurationMinutes(request.getDurationMinutes());
        validateTypeComposition(totalQuestions, mcqCount, msqCount);

        if (distinctQuestionIds.size() != totalQuestions) {
            throw new BusinessRuleException("Quiz must include exactly " + totalQuestions + " questions");
        }

        User admin = userRepository.findByEnterpriseId(adminEnterpriseId).orElseThrow(() -> new NotFoundException("Admin not found"));

        List<MCQ> selectedQuestions = mcqRepository.findAllById(distinctQuestionIds);
        if (selectedQuestions.size() != distinctQuestionIds.size()) {
            throw new NotFoundException("One or more selected questions were not found");
        }

        MCQ representativeQuestion = selectedQuestions.get(0);
        Topic topic = representativeQuestion.getTopic();

        for (MCQ question : selectedQuestions) {
            if (question.getStatus() != MCQ.Status.APPROVED) {
                throw new BusinessRuleException("Only APPROVED questions can be published in a quiz");
            }
        }

        long selectedMcqCount = selectedQuestions.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MCQ).count();
        long selectedMsqCount = selectedQuestions.stream().filter(q -> q.getQuestionType() == MCQ.QuestionType.MSQ).count();
        if (selectedMcqCount != mcqCount || selectedMsqCount != msqCount) {
            throw new BusinessRuleException("Quiz must include exactly " + mcqCount + " MCQ and " + msqCount + " MSQ questions");
        }

        Quiz quiz = new Quiz();
        quiz.setQuizCode(generateUniqueQuizCode());
        quiz.setTopic(topic);
        quiz.setStack(topic.getStack());
        quiz.setCreatedBy(admin);
        quiz.setPublishedAt(LocalDateTime.now());
        quiz.setStatus(Quiz.Status.PUBLISHED);
        quiz.setDurationMinutes(durationMinutes);
        String defaultTitle = selectedQuestions.stream()
            .map(MCQ::getTopic)
            .filter(Objects::nonNull)
            .map(Topic::getTopicId)
            .distinct()
            .count() > 1
            ? "Multi-Topic Quiz"
            : topic.getStack().getStackName() + " - " + topic.getTopicName() + " Quiz";
        quiz.setTitle((request.getTitle() == null || request.getTitle().isBlank()) ? defaultTitle : request.getTitle().trim());

        Quiz savedQuiz = quizRepository.save(quiz);

        Map<Long, Integer> orderMap = new HashMap<>();
        for (int i = 0; i < distinctQuestionIds.size(); i++) {
            orderMap.put(distinctQuestionIds.get(i), i + 1);
        }

        List<QuizQuestion> quizQuestions = selectedQuestions.stream()
                .sorted(Comparator.comparingInt(q -> orderMap.getOrDefault(q.getQuestionId(), Integer.MAX_VALUE)))
                .map(question -> {
                    QuizQuestion qq = new QuizQuestion();
                    qq.setQuiz(savedQuiz);
                    qq.setQuestion(question);
                    qq.setQuestionOrder(orderMap.get(question.getQuestionId()));
                    return qq;
                })
                .collect(Collectors.toList());

        quizQuestionRepository.saveAll(quizQuestions);

        return QuizDTO.PublishedQuizSummary.from(savedQuiz, buildQuizLink(savedQuiz.getQuizCode()), quizQuestions.size(), savedQuiz.getDurationMinutes());
    }

    @PreAuthorize("hasRole('ADMIN')")

    public Page<QuizDTO.PublishedQuizSummary> getPublishedQuizzes(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"));
        return quizRepository.findAllByOrderByPublishedAtDesc(pageable)
                .map(quiz -> {
                    int questionCount = quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(quiz).size();
                    return QuizDTO.PublishedQuizSummary.from(quiz, buildQuizLink(quiz.getQuizCode()), questionCount, quiz.getDurationMinutes());
                });
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDTO.PublishedQuizSummary closeQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (quiz.getStatus() == Quiz.Status.CLOSED) {
            throw new ConflictException("Quiz is already closed");
        }
        quiz.setStatus(Quiz.Status.CLOSED);
        quiz.setClosedAt(LocalDateTime.now());
        Quiz updated = quizRepository.save(quiz);
        int questionCount = quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(updated).size();
        return QuizDTO.PublishedQuizSummary.from(updated, buildQuizLink(updated.getQuizCode()), questionCount, updated.getDurationMinutes());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDTO.PublishedQuizSummary reopenQuiz(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (quiz.getStatus() == Quiz.Status.PUBLISHED) {
            throw new ConflictException("Quiz is already published");
        }

        quiz.setStatus(Quiz.Status.PUBLISHED);
        quiz.setClosedAt(null);
        Quiz reopened = quizRepository.save(quiz);
        int questionCount = quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(reopened).size();
        return QuizDTO.PublishedQuizSummary.from(reopened, buildQuizLink(reopened.getQuizCode()), questionCount, reopened.getDurationMinutes());
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public QuizDTO.PublishedQuizSummary rehostClosedQuiz(String adminEnterpriseId, Long quizId) {
        Quiz closedQuiz = quizRepository.findById(quizId).orElseThrow(() -> new NotFoundException("Quiz not found"));
        if (closedQuiz.getStatus() != Quiz.Status.CLOSED) {
            throw new BusinessRuleException("Only CLOSED quizzes can be hosted again");
        }

        User admin = userRepository.findByEnterpriseId(adminEnterpriseId)
                .orElseThrow(() -> new NotFoundException("Admin not found"));

        List<QuizQuestion> closedQuizQuestions = quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(closedQuiz);
        if (closedQuizQuestions.isEmpty()) {
            throw new BusinessRuleException("Cannot host again because the quiz has no configured questions");
        }

        Quiz newQuiz = new Quiz();
        newQuiz.setQuizCode(generateUniqueQuizCode());
        newQuiz.setTitle(closedQuiz.getTitle() + " (Rehosted)");
        newQuiz.setStack(closedQuiz.getStack());
        newQuiz.setTopic(closedQuiz.getTopic());
        newQuiz.setCreatedBy(admin);
        newQuiz.setPublishedAt(LocalDateTime.now());
        newQuiz.setStatus(Quiz.Status.PUBLISHED);
        newQuiz.setDurationMinutes(closedQuiz.getDurationMinutes() == null ? DEFAULT_QUIZ_DURATION_MINUTES : closedQuiz.getDurationMinutes());

        Quiz savedQuiz = quizRepository.save(newQuiz);

        List<QuizQuestion> clonedQuestions = new ArrayList<>();
        for (QuizQuestion existing : closedQuizQuestions) {
            QuizQuestion clone = new QuizQuestion();
            clone.setQuiz(savedQuiz);
            clone.setQuestion(existing.getQuestion());
            clone.setQuestionOrder(existing.getQuestionOrder());
            clonedQuestions.add(clone);
        }
        quizQuestionRepository.saveAll(clonedQuestions);

        return QuizDTO.PublishedQuizSummary.from(savedQuiz, buildQuizLink(savedQuiz.getQuizCode()), clonedQuestions.size(), savedQuiz.getDurationMinutes());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public byte[] exportLeaderboardExcel(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId).orElseThrow(() -> new NotFoundException("Quiz not found"));
        List<QuizAttempt> attempts = quizAttemptRepository.findLeaderboardByQuiz(quiz);

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Leaderboard");

            int rowIndex = 0;
            Row header = sheet.createRow(rowIndex++);
            header.createCell(0).setCellValue("Rank");
            header.createCell(1).setCellValue("Participant Name");
            header.createCell(2).setCellValue("Employee ID");
            header.createCell(3).setCellValue("Score");
            header.createCell(4).setCellValue("Total Questions");
            header.createCell(5).setCellValue("Submitted At");

            int rank = 1;
            for (QuizAttempt attempt : attempts) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(rank++);
                row.createCell(1).setCellValue(attempt.getParticipantName());
                row.createCell(2).setCellValue(attempt.getParticipantEnterpriseId() == null ? "" : attempt.getParticipantEnterpriseId());
                row.createCell(3).setCellValue(attempt.getScore());
                row.createCell(4).setCellValue(attempt.getTotalQuestions());
                row.createCell(5).setCellValue(attempt.getSubmittedAt() == null ? "" : attempt.getSubmittedAt().toString());
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to export leaderboard", ex);
        }
    }

    public QuizDTO.PublicQuizResponse getPublicQuiz(String quizCode, String participantEnterpriseId, String participantName, String languageCode) {
        Quiz quiz = getQuizByCodeOrThrow(quizCode);
        String normalizedLanguage = normalizeLanguageCode(languageCode);
        List<QuizQuestion> quizQuestions = quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(quiz);

        Set<Long> questionIds = quizQuestions.stream()
            .map(QuizQuestion::getQuestion)
            .filter(Objects::nonNull)
            .map(MCQ::getQuestionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<Long, MCQTranslation> translationByQuestionId = new HashMap<>();
        if (!"en".equals(normalizedLanguage) && !questionIds.isEmpty()) {
            for (Long questionId : questionIds) {
                mcqTranslationRepository.findByMcqQuestionIdAndLanguageCode(questionId, normalizedLanguage)
                    .ifPresent(translation -> translationByQuestionId.put(questionId, translation));
            }
        }

        List<QuizDTO.ParticipantQuestion> questions = quizQuestions.stream()
                .map(QuizQuestion::getQuestion)
                .map(question -> QuizDTO.ParticipantQuestion.from(question, translationByQuestionId.get(question.getQuestionId())))
                .collect(Collectors.toList());

        boolean canAttempt = quiz.getStatus() == Quiz.Status.PUBLISHED;
        boolean alreadyAttempted = false;
        String participantNotice = null;

        String normalizedEnterpriseId = participantEnterpriseId == null ? null : participantEnterpriseId.trim();
        String normalizedName = normalizeName(participantName);

        if (canAttempt) {
            if (normalizedEnterpriseId != null && !normalizedEnterpriseId.isBlank()) {
                alreadyAttempted = quizAttemptRepository.existsByQuizAndParticipantEnterpriseId(quiz, normalizedEnterpriseId);
            } else if (normalizedName != null) {
                alreadyAttempted = quizAttemptRepository.existsGuestByQuizAndParticipantName(quiz, normalizedName);
            }

            if (alreadyAttempted) {
                canAttempt = false;
                participantNotice = getLocalizedMessage(normalizedLanguage, "already_attempted");
            }
        }

        if (!canAttempt && participantNotice == null && quiz.getStatus() != Quiz.Status.PUBLISHED) {
            participantNotice = getLocalizedMessage(normalizedLanguage, "quiz_closed");
        }

        return new QuizDTO.PublicQuizResponse(
                quiz.getQuizId(),
                quiz.getQuizCode(),
                quiz.getTitle(),
                quiz.getStack().getStackName(),
                quiz.getTopic().getTopicName(),
                quiz.getStatus().name(),
                canAttempt,
                alreadyAttempted,
                participantNotice,
                questions.size(),
                quiz.getDurationMinutes() == null ? DEFAULT_QUIZ_DURATION_MINUTES : quiz.getDurationMinutes(),
                canAttempt ? questions : Collections.emptyList()
        );
    }

    public QuizDTO.PublicQuizResponse getPublicQuiz(String quizCode) {
        return getPublicQuiz(quizCode, null, null, "en");
    }

    private String normalizeLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return "en";
        }
        String normalized = languageCode.trim().toLowerCase(Locale.ROOT);
        if (List.of("en", "es", "fr", "ro").contains(normalized)) {
            return normalized;
        }
        return "en";
    }

    public QuizDTO.ParticipantLoginResponse loginParticipant(String quizCode, QuizDTO.ParticipantLoginRequest request) {
        getQuizByCodeOrThrow(quizCode);
        if (request.getEnterpriseId() == null || request.getEnterpriseId().isBlank()) {
            throw new BusinessRuleException("Enterprise ID is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessRuleException("Password is required");
        }

        try {
            Authentication authentication = loginAttemptGuard.authenticate(request.getEnterpriseId().trim(), request.getPassword());
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new UnauthorizedException("Invalid credentials");
            }
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid credentials");
        }

        User user = userRepository.findByEnterpriseId(request.getEnterpriseId().trim())
                .orElseThrow(() -> new NotFoundException("User not found"));
        String token = jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());

        return new QuizDTO.ParticipantLoginResponse(
            user.getEnterpriseId(),
            user.getFullName(),
            token,
            "EMPLOYEE",
            normalizeLanguageCode(user.getPreferredLanguage())
        );
    }

    public QuizDTO.ParticipantLoginResponse loginParticipantPortal(QuizDTO.ParticipantLoginRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Login request is required");
        }

        if (request.getEnterpriseId() != null && !request.getEnterpriseId().isBlank()) {
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new BusinessRuleException("Password is required for employee login");
            }

            try {
                loginAttemptGuard.authenticate(request.getEnterpriseId().trim(), request.getPassword());
            } catch (AuthenticationException ex) {
                throw new UnauthorizedException("Invalid credentials");
            }

            User user = userRepository.findByEnterpriseId(request.getEnterpriseId().trim())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
            String token = jwtUtils.generateToken(user.getEnterpriseId(), user.getRole().name());
            return new QuizDTO.ParticipantLoginResponse(
                user.getEnterpriseId(),
                user.getFullName(),
                token,
                "EMPLOYEE",
                normalizeLanguageCode(user.getPreferredLanguage())
            );
        }

        String guestName = normalizeName(request.getParticipantName());
        if (guestName == null) {
            throw new BusinessRuleException("Participant name is required for guest login");
        }

        return new QuizDTO.ParticipantLoginResponse(null, guestName, null, "GUEST", "en");
    }

    public QuizDTO.ParticipantPortalDashboardResponse getParticipantPortalDashboard(String participantEnterpriseId,
                                                                                    String participantName,
                                                                                    String languageCode) {
        long methodStartedAt = System.nanoTime();
        String normalizedLanguage = normalizeLanguageCode(languageCode);
        String normalizedEnterpriseId = participantEnterpriseId == null ? null : participantEnterpriseId.trim();
        String normalizedName = normalizeName(participantName);

        if ((normalizedEnterpriseId == null || normalizedEnterpriseId.isBlank()) && normalizedName == null) {
            throw new BusinessRuleException("Participant identity is required");
        }

        long liveQuizFetchStartedAt = System.nanoTime();
        List<Quiz> liveQuizzes = quizRepository.findByStatusOrderByPublishedAtDesc(Quiz.Status.PUBLISHED);
        long liveQuizFetchMs = (System.nanoTime() - liveQuizFetchStartedAt) / 1_000_000;
        List<QuizDTO.ParticipantPortalQuizCard> liveQuizCards = liveQuizzes.stream()
            .map(quiz -> new QuizDTO.ParticipantPortalQuizCard(
                quiz.getQuizId(),
                quiz.getQuizCode(),
                quiz.getTitle(),
                quiz.getStack() == null ? "" : quiz.getStack().getStackName(),
                quiz.getTopic() == null ? "" : quiz.getTopic().getTopicName(),
                quiz.getStatus().name(),
                quiz.getDurationMinutes(),
                quiz.getPublishedAt(),
                quiz.getClosedAt()
            ))
            .collect(Collectors.toList());

        long attemptFetchStartedAt = System.nanoTime();
        List<QuizAttempt> attempts;
        String effectiveParticipantType;
        if (normalizedEnterpriseId != null && !normalizedEnterpriseId.isBlank()) {
            attempts = quizAttemptRepository.findByParticipantEnterpriseIdOrderBySubmittedAtDesc(normalizedEnterpriseId);
            effectiveParticipantType = "EMPLOYEE";
        } else {
            attempts = quizAttemptRepository.findGuestAttemptsByParticipantName(normalizedName);
            effectiveParticipantType = "GUEST";
        }
        long attemptFetchMs = (System.nanoTime() - attemptFetchStartedAt) / 1_000_000;

        List<QuizDTO.ParticipantAttemptHistoryRow> attemptRows = attempts.stream()
            .map(attempt -> new QuizDTO.ParticipantAttemptHistoryRow(
                attempt.getAttemptId(),
                attempt.getQuiz() == null ? null : attempt.getQuiz().getQuizId(),
                attempt.getQuiz() == null ? "" : attempt.getQuiz().getQuizCode(),
                attempt.getQuiz() == null ? "" : attempt.getQuiz().getTitle(),
                attempt.getQuiz() == null || attempt.getQuiz().getStack() == null ? "" : attempt.getQuiz().getStack().getStackName(),
                attempt.getQuiz() == null || attempt.getQuiz().getTopic() == null ? "" : attempt.getQuiz().getTopic().getTopicName(),
                attempt.getScore(),
                attempt.getTotalQuestions(),
                attempt.getTimeTakenSeconds(),
                attempt.getSubmittedAt()
            ))
            .collect(Collectors.toList());

        Map<String, TopicSuggestionAccumulator> suggestionAccumulators = new HashMap<>();
        for (QuizAttempt attempt : attempts) {
            if (attempt.getQuiz() == null || attempt.getQuiz().getTopic() == null) {
                continue;
            }
            String stackName = attempt.getQuiz().getStack() == null ? "" : attempt.getQuiz().getStack().getStackName();
            String topicName = attempt.getQuiz().getTopic().getTopicName();
            String key = stackName + "::" + topicName;
            TopicSuggestionAccumulator acc = suggestionAccumulators.computeIfAbsent(key, unused ->
                new TopicSuggestionAccumulator(stackName, topicName)
            );

            int total = safeInt(attempt.getTotalQuestions());
            int score = safeInt(attempt.getScore());
            double percent = total <= 0 ? 0.0 : (score * 100.0) / total;
            acc.sumPercent += percent;
            acc.count += 1;
        }

        long suggestionBuildStartedAt = System.nanoTime();
        List<QuizDTO.ParticipantSuggestion> suggestions = suggestionAccumulators.values().stream()
            .map(acc -> {
                double avg = acc.count <= 0 ? 0.0 : acc.sumPercent / acc.count;
                String message = avg < 50.0
                    ? getLocalizedMessage(normalizedLanguage, "suggestion_low")
                    : avg < 70.0
                        ? getLocalizedMessage(normalizedLanguage, "suggestion_mid")
                        : getLocalizedMessage(normalizedLanguage, "suggestion_high");
                return new QuizDTO.ParticipantSuggestion(acc.stackName, acc.topicName, message, round2(avg));
            })
            .sorted(Comparator.comparing(QuizDTO.ParticipantSuggestion::getAverageScorePercent))
            .limit(6)
            .collect(Collectors.toList());
        long suggestionBuildMs = (System.nanoTime() - suggestionBuildStartedAt) / 1_000_000;

        long displayNameLookupStartedAt = System.nanoTime();
        String displayName = effectiveParticipantType.equals("EMPLOYEE")
            ? userRepository.findByEnterpriseId(normalizedEnterpriseId).map(User::getFullName).orElse(normalizedEnterpriseId)
            : normalizedName;
        long displayNameLookupMs = (System.nanoTime() - displayNameLookupStartedAt) / 1_000_000;

        long totalMs = (System.nanoTime() - methodStartedAt) / 1_000_000;
        if (totalMs >= PARTICIPANT_DASHBOARD_SLOW_THRESHOLD_MS) {
            logger.warn(
                "Participant dashboard slow path: total={}ms liveQuizFetch={}ms attemptFetch={}ms suggestionBuild={}ms displayNameLookup={}ms participantType={} liveQuizCount={} attemptCount={}",
                totalMs,
                liveQuizFetchMs,
                attemptFetchMs,
                suggestionBuildMs,
                displayNameLookupMs,
                effectiveParticipantType,
                liveQuizCards.size(),
                attemptRows.size()
            );
        } else {
            logger.debug(
                "Participant dashboard timing: total={}ms liveQuizFetch={}ms attemptFetch={}ms suggestionBuild={}ms displayNameLookup={}ms participantType={} liveQuizCount={} attemptCount={}",
                totalMs,
                liveQuizFetchMs,
                attemptFetchMs,
                suggestionBuildMs,
                displayNameLookupMs,
                effectiveParticipantType,
                liveQuizCards.size(),
                attemptRows.size()
            );
        }

        return new QuizDTO.ParticipantPortalDashboardResponse(
            displayName,
            effectiveParticipantType.equals("EMPLOYEE") ? normalizedEnterpriseId : null,
            effectiveParticipantType,
            liveQuizCards.size(),
            attemptRows.size(),
            liveQuizCards,
            attemptRows,
            suggestions
        );
    }

    private String getLocalizedMessage(String languageCode, String messageKey) {
        String code = normalizeLanguageCode(languageCode);
        switch (messageKey) {
            case "already_attempted":
                return switch (code) {
                    case "es" -> "Ya intentaste este cuestionario. Puedes ver la tabla de clasificacion.";
                    case "fr" -> "Vous avez deja tente ce quiz. Vous pouvez consulter le classement.";
                    case "ro" -> "Ai incercat deja acest test. Poti vedea clasamentul.";
                    default -> "You have already attempted this quiz. You can view the leaderboard.";
                };
            case "quiz_closed":
                return switch (code) {
                    case "es" -> "Este cuestionario esta cerrado y no puede aceptar nuevos intentos.";
                    case "fr" -> "Ce quiz est ferme et ne peut pas accepter de nouvelles tentatives.";
                    case "ro" -> "Acest test este inchis si nu poate accepta incercari noi.";
                    default -> "This quiz is closed and cannot accept new attempts.";
                };
            case "suggestion_low":
                return switch (code) {
                    case "es" -> "Se recomienda encarecidamente repasar este tema antes del proximo intento.";
                    case "fr" -> "Une revision approfondie de ce sujet est fortement recommandee avant la prochaine tentative.";
                    case "ro" -> "Este recomandata o revizuire serioasa a acestui subiect inainte de urmatoarea incercare.";
                    default -> "Strongly recommended revision for this topic before next attempt.";
                };
            case "suggestion_mid":
                return switch (code) {
                    case "es" -> "Practica este tema para mejorar la velocidad y la precision.";
                    case "fr" -> "Pratiquez ce sujet pour ameliorer la vitesse et la precision.";
                    case "ro" -> "Exerseaza acest subiect pentru a imbunatati viteza si acuratetea.";
                    default -> "Practice this topic to improve speed and accuracy.";
                };
            case "suggestion_high":
                return switch (code) {
                    case "es" -> "Mantenga el ritmo con una revision periodica.";
                    case "fr" -> "Maintenez l elan avec des revisions periodiques.";
                    case "ro" -> "Mentine ritmul prin revizuire periodica.";
                    default -> "Maintain momentum with periodic revision.";
                };
            default:
                return "";
        }
    }

    @Transactional
    public QuizDTO.AttemptResultResponse submitAttempt(String quizCode, QuizDTO.SubmitAttemptRequest request) {
        return submitAttempt(quizCode, request, null);
    }

    /**
     * Submits an attempt. {@code authenticatedEnterpriseId} comes from the caller's JWT: when present the attempt
     * is recorded for that employee; when absent it is a guest attempt keyed by name.
     */
    @Transactional
    public QuizDTO.AttemptResultResponse submitAttempt(String quizCode, QuizDTO.SubmitAttemptRequest request,
                                                       String authenticatedEnterpriseId) {
        Quiz quiz = getQuizByCodeOrThrow(quizCode);

        if (quiz.getStatus() != Quiz.Status.PUBLISHED) {
            throw new BusinessRuleException("This quiz is closed and cannot accept new attempts");
        }

        String normalizedName = normalizeName(request.getParticipantName());
        if (normalizedName == null) {
            throw new BusinessRuleException("Participant name is required");
        }

        String participantEnterpriseId = null;
        String participantName = normalizedName;

        String claimedEnterpriseId = request.getParticipantEnterpriseId() == null ? null : request.getParticipantEnterpriseId().trim();
        if (authenticatedEnterpriseId == null && claimedEnterpriseId != null && !claimedEnterpriseId.isBlank()) {
            throw new UnauthorizedException("Employee login is required to submit with enterprise ID");
        }
        if (authenticatedEnterpriseId != null && claimedEnterpriseId != null && !claimedEnterpriseId.isBlank()
                && !claimedEnterpriseId.equals(authenticatedEnterpriseId)) {
            throw new UnauthorizedException("Employee session does not match the selected enterprise ID");
        }

        if (authenticatedEnterpriseId != null) {
            String enterpriseId = authenticatedEnterpriseId;

            User user = userRepository.findByEnterpriseId(enterpriseId)
                    .orElseThrow(() -> new NotFoundException("Employee not found"));
            participantEnterpriseId = user.getEnterpriseId();
            participantName = user.getFullName();

            if (quizAttemptRepository.existsByQuizAndParticipantEnterpriseId(quiz, participantEnterpriseId)) {
                throw new ConflictException("You have already attempted this quiz");
            }
        } else {
            if (quizAttemptRepository.existsGuestByQuizAndParticipantName(quiz, participantName)) {
                throw new ConflictException("A participant with this name has already attempted this quiz");
            }
        }

        List<QuizQuestion> quizQuestions = quizQuestionRepository.findByQuizOrderByQuestionOrderAsc(quiz);
        if (quizQuestions.isEmpty()) {
            throw new BusinessRuleException("Quiz has no questions configured");
        }

        Map<Long, MCQ> questionMap = quizQuestions.stream()
                .map(QuizQuestion::getQuestion)
                .collect(Collectors.toMap(MCQ::getQuestionId, q -> q));

        List<QuizDTO.AnswerRequest> answers = request.getAnswers() == null ? Collections.emptyList() : request.getAnswers();
        int maxDurationSeconds = Math.max(0, (quiz.getDurationMinutes() == null ? DEFAULT_QUIZ_DURATION_MINUTES : quiz.getDurationMinutes()) * 60);
        int elapsedSeconds = request.getElapsedSeconds() == null ? 0 : Math.max(0, request.getElapsedSeconds());
        if (maxDurationSeconds > 0 && elapsedSeconds > maxDurationSeconds) {
            elapsedSeconds = maxDurationSeconds;
        }
        boolean autoSubmitted = Boolean.TRUE.equals(request.getAutoSubmitted()) || (maxDurationSeconds > 0 && elapsedSeconds >= maxDurationSeconds);

        if (!autoSubmitted && answers.size() != quizQuestions.size()) {
            throw new BusinessRuleException("All quiz questions must be attempted before submission");
        }

        Set<Long> submittedQuestionIds = answers.stream()
                .map(QuizDTO.AnswerRequest::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!autoSubmitted && (submittedQuestionIds.size() != quizQuestions.size() || !questionMap.keySet().containsAll(submittedQuestionIds))) {
            throw new BusinessRuleException("All quiz questions must be attempted before submission");
        }

        int correctCount = 0;
        List<QuizAttemptAnswer> answerEntities = new ArrayList<>();

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(quiz);
        attempt.setParticipantName(participantName);
        attempt.setParticipantEnterpriseId(participantEnterpriseId);
        attempt.setTotalQuestions(quizQuestions.size());
        attempt.setCorrectCount(0);
        attempt.setScore(0);
        attempt.setTimeTakenSeconds(elapsedSeconds);
        attempt.setAutoSubmitted(autoSubmitted);
        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);

        for (QuizDTO.AnswerRequest answer : answers) {
            MCQ question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }

            if (!autoSubmitted && (answer.getSelectedAnswers() == null || answer.getSelectedAnswers().isEmpty())) {
                throw new BusinessRuleException("All quiz questions must be attempted before submission");
            }

            String selected = normalizeSelectedAnswers(answer.getSelectedAnswers());
            if (!autoSubmitted && selected.isBlank()) {
                throw new BusinessRuleException("All quiz questions must be attempted before submission");
            }
            String expected = normalizeSelectedAnswers(Arrays.asList(question.getCorrectAnswer().split(",")));
            boolean isCorrect = !selected.isBlank() && selected.equals(expected);
            if (isCorrect) {
                correctCount++;
            }

            QuizAttemptAnswer attemptAnswer = new QuizAttemptAnswer();
            attemptAnswer.setAttempt(savedAttempt);
            attemptAnswer.setQuestion(question);
            attemptAnswer.setSelectedAnswers(selected);
            attemptAnswer.setCorrect(isCorrect);
            answerEntities.add(attemptAnswer);
        }

        if (!answerEntities.isEmpty()) {
            quizAttemptAnswerRepository.saveAll(answerEntities);
        }

        savedAttempt.setCorrectCount(correctCount);
        savedAttempt.setScore(correctCount);
        QuizAttempt updatedAttempt = quizAttemptRepository.save(savedAttempt);

        return new QuizDTO.AttemptResultResponse(
                updatedAttempt.getAttemptId(),
                updatedAttempt.getParticipantName(),
                updatedAttempt.getParticipantEnterpriseId(),
                updatedAttempt.getScore(),
                updatedAttempt.getTotalQuestions(),
                updatedAttempt.getCorrectCount(),
            updatedAttempt.getTimeTakenSeconds(),
            updatedAttempt.isAutoSubmitted(),
                updatedAttempt.getSubmittedAt()
        );
    }

    public QuizDTO.LeaderboardResponse getLeaderboard(String quizCode) {
        Quiz quiz = getQuizByCodeOrThrow(quizCode);
        List<QuizAttempt> attempts = quizAttemptRepository.findLeaderboardByQuiz(quiz);
        List<QuizAttemptAnswer> answers = attempts.isEmpty()
            ? Collections.emptyList()
            : quizAttemptAnswerRepository.findByAttemptIn(attempts);

        Map<Long, Integer> attemptedByAttemptId = answers.stream()
            .filter(answer -> answer.getAttempt() != null)
            .collect(Collectors.groupingBy(
                answer -> answer.getAttempt().getAttemptId(),
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));

        List<QuizDTO.LeaderboardEntry> entries = new ArrayList<>();
        int rank = 1;
        for (QuizAttempt attempt : attempts) {
            int attemptedCount = attemptedByAttemptId.getOrDefault(attempt.getAttemptId(), safeInt(attempt.getTotalQuestions()));
            int correctCount = safeInt(attempt.getCorrectCount());
            int wrongCount = Math.max(0, attemptedCount - correctCount);
            entries.add(new QuizDTO.LeaderboardEntry(
                    rank++,
                rankBadge(rank - 1),
                    attempt.getParticipantName(),
                    attempt.getParticipantEnterpriseId(),
                    attempt.getScore(),
                    attempt.getTotalQuestions(),
                attemptedCount,
                correctCount,
                wrongCount,
                    attempt.getTimeTakenSeconds() == null ? 0 : attempt.getTimeTakenSeconds(),
                    attempt.getSubmittedAt()
            ));
        }

        return new QuizDTO.LeaderboardResponse(
                quiz.getQuizCode(),
                quiz.getTitle(),
                quiz.getStatus().name(),
                attempts.size(),
                entries
        );
    }

            @PreAuthorize("hasRole('ADMIN')")

            public QuizDTO.TopicLeaderboardResponse getTopicLeaderboard(Long topicId, String participantType) {
            Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new NotFoundException("Topic not found"));

            String normalizedType = normalizeParticipantTypeOrAll(participantType);
            List<Quiz> quizzesForTopic = quizRepository.findAll().stream()
                .filter(quiz -> quiz.getTopic() != null && Objects.equals(quiz.getTopic().getTopicId(), topicId))
                .collect(Collectors.toList());

            List<QuizAttempt> attempts = quizzesForTopic.isEmpty()
                ? Collections.emptyList()
                : quizAttemptRepository.findAll().stream()
                    .filter(attempt -> attempt.getQuiz() != null && Objects.equals(attempt.getQuiz().getTopic().getTopicId(), topicId))
                    .filter(attempt -> matchesParticipantTypeOrAll(attempt, normalizedType))
                    .collect(Collectors.toList());

            List<QuizAttemptAnswer> answers = attempts.isEmpty()
                ? Collections.emptyList()
                : quizAttemptAnswerRepository.findByAttemptIn(attempts);

            List<AggregatedParticipantLeaderboard> aggregated = buildAggregatedLeaderboard(attempts, answers);
            List<QuizDTO.TopicLeaderboardEntry> rows = new ArrayList<>();
            int rank = 1;
            for (AggregatedParticipantLeaderboard entry : aggregated) {
                rows.add(new QuizDTO.TopicLeaderboardEntry(
                    rank,
                    rankBadge(rank),
                    entry.participantName,
                    entry.participantEnterpriseId,
                    entry.quizzesPlayed(),
                    entry.attemptedCount,
                    entry.correctCount,
                    entry.wrongCount,
                    round2(entry.scorePercent()),
                    entry.averageTimeTakenSeconds()
                ));
                rank++;
            }

            return new QuizDTO.TopicLeaderboardResponse(
                topicId,
                topic.getStack() == null ? "" : topic.getStack().getStackName(),
                topic.getTopicName(),
                normalizedType,
                rows.size(),
                rows
            );
            }

            @PreAuthorize("hasRole('ADMIN')")

            public QuizDTO.OverallLeaderboardResponse getOverallLeaderboard(String participantType) {
            String normalizedType = normalizeParticipantTypeOrAll(participantType);

            List<QuizAttempt> attempts = quizAttemptRepository.findAll().stream()
                .filter(attempt -> matchesParticipantTypeOrAll(attempt, normalizedType))
                .collect(Collectors.toList());

            List<QuizAttemptAnswer> answers = attempts.isEmpty()
                ? Collections.emptyList()
                : quizAttemptAnswerRepository.findByAttemptIn(attempts);

            List<AggregatedParticipantLeaderboard> aggregated = buildAggregatedLeaderboard(attempts, answers);
            List<QuizDTO.OverallLeaderboardEntry> rows = new ArrayList<>();
            int rank = 1;
            for (AggregatedParticipantLeaderboard entry : aggregated) {
                rows.add(new QuizDTO.OverallLeaderboardEntry(
                    rank,
                    rankBadge(rank),
                    entry.participantName,
                    entry.participantEnterpriseId,
                    entry.quizzesPlayed(),
                    entry.topicsCovered(),
                    entry.attemptedCount,
                    entry.correctCount,
                    entry.wrongCount,
                    round2(entry.scorePercent()),
                    entry.averageTimeTakenSeconds()
                ));
                rank++;
            }

            return new QuizDTO.OverallLeaderboardResponse(normalizedType, rows.size(), rows);
            }

    @PreAuthorize("hasRole('ADMIN')")

    public List<QuizDTO.HostedTopicOption> getHostedTopicsForLeaderboard() {
        Map<Long, TopicHostedAggregate> hostedByTopic = new HashMap<>();

        for (Quiz quiz : quizRepository.findAll()) {
            if (quiz.getTopic() == null || quiz.getTopic().getTopicId() == null) {
                continue;
            }

            Long topicId = quiz.getTopic().getTopicId();
            TopicHostedAggregate aggregate = hostedByTopic.computeIfAbsent(topicId, unused ->
                new TopicHostedAggregate(
                    topicId,
                    quiz.getStack() == null ? "" : quiz.getStack().getStackName(),
                    quiz.getTopic().getTopicName()
                )
            );
            aggregate.quizzesHosted += 1;
        }

        return hostedByTopic.values().stream()
            .sorted(Comparator
                .comparing((TopicHostedAggregate item) -> item.quizzesHosted).reversed()
                .thenComparing(item -> item.stackName)
                .thenComparing(item -> item.topicName))
            .map(item -> new QuizDTO.HostedTopicOption(item.topicId, item.stackName, item.topicName, item.quizzesHosted))
            .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")

    public QuizDTO.ParticipantTopicAnalyticsResponse getParticipantTopicAnalytics(String participantType) {
        String normalizedType = normalizeParticipantTypeOrAll(participantType);

        List<Quiz> quizzes = quizRepository.findAll();
        List<QuizAttempt> attempts = quizAttemptRepository.findAll().stream()
            .filter(attempt -> matchesParticipantTypeOrAll(attempt, normalizedType))
            .collect(Collectors.toList());

        List<QuizAttemptAnswer> answers = quizzes.isEmpty()
            ? Collections.emptyList()
            : quizAttemptAnswerRepository.findByAttemptQuizIn(quizzes);
        Set<Long> filteredAttemptIds = attempts.stream().map(QuizAttempt::getAttemptId).collect(Collectors.toSet());
        List<QuizAttemptAnswer> filteredAnswers = answers.stream()
            .filter(answer -> answer.getAttempt() != null && filteredAttemptIds.contains(answer.getAttempt().getAttemptId()))
            .collect(Collectors.toList());

        Map<Long, Integer> hostedByTopicId = new HashMap<>();
        for (Quiz quiz : quizzes) {
            if (quiz.getTopic() == null || quiz.getTopic().getTopicId() == null) {
                continue;
            }
            hostedByTopicId.merge(quiz.getTopic().getTopicId(), 1, Integer::sum);
        }

        Map<String, ParticipantTopicAccumulator> byParticipantTopic = new HashMap<>();
        for (QuizAttempt attempt : attempts) {
            Quiz attemptQuiz = attempt.getQuiz();
            if (attemptQuiz == null || attemptQuiz.getTopic() == null || attemptQuiz.getTopic().getTopicId() == null) {
                continue;
            }

            Long topicId = attemptQuiz.getTopic().getTopicId();
            String participantKey = buildParticipantKey(attempt);
            String aggregateKey = participantKey + "::" + topicId;

            ParticipantTopicAccumulator accumulator = byParticipantTopic.computeIfAbsent(
                    aggregateKey,
                    key -> new ParticipantTopicAccumulator(attempt, attemptQuiz)
            );

            accumulator.attendedQuizIds.add(attemptQuiz.getQuizId());
            accumulator.correctAnswers += safeInt(attempt.getCorrectCount());
            accumulator.totalQuestions += safeInt(attempt.getTotalQuestions());
        }

        List<QuizDTO.ParticipantTopicAnalyticsRow> rows = byParticipantTopic.values().stream()
                .map(accumulator -> {
                    int hosted = hostedByTopicId.getOrDefault(accumulator.topicId, 0);
                    int attended = accumulator.attendedQuizIds.size();
                    double engagementPercent = hosted <= 0 ? 0.0 : round2((attended * 100.0) / hosted);
                    double performancePercent = accumulator.totalQuestions <= 0
                            ? 0.0
                            : round2((accumulator.correctAnswers * 100.0) / accumulator.totalQuestions);

                    return new QuizDTO.ParticipantTopicAnalyticsRow(
                            accumulator.participantName,
                            accumulator.participantEnterpriseId,
                            accumulator.participantType,
                            accumulator.stackName,
                            accumulator.topicName,
                            hosted,
                            attended,
                            accumulator.correctAnswers,
                            accumulator.totalQuestions,
                            engagementPercent,
                            performancePercent,
                            buildRecommendation(accumulator.topicName, accumulator.stackName, engagementPercent, performancePercent)
                    );
                })
                .sorted(Comparator
                        .comparing(QuizDTO.ParticipantTopicAnalyticsRow::getPerformancePercent)
                        .thenComparing(QuizDTO.ParticipantTopicAnalyticsRow::getEngagementPercent)
                        .thenComparing(QuizDTO.ParticipantTopicAnalyticsRow::getParticipantName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(QuizDTO.ParticipantTopicAnalyticsRow::getTopicName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

            Map<Long, Long> answerCountByAttemptId = filteredAnswers.stream()
                .filter(answer -> answer.getAttempt() != null)
                .collect(Collectors.groupingBy(answer -> answer.getAttempt().getAttemptId(), Collectors.counting()));

            int completedAttempts = (int) attempts.stream()
                .filter(attempt -> {
                    int total = safeInt(attempt.getTotalQuestions());
                    long answered = answerCountByAttemptId.getOrDefault(attempt.getAttemptId(), 0L);
                    return total > 0 && answered >= total;
                })
                .count();

            double avgScorePercent = attempts.stream().mapToDouble(this::scorePercent).average().orElse(0.0);
            double maxScorePercent = attempts.stream().mapToDouble(this::scorePercent).max().orElse(0.0);
            double minScorePercent = attempts.stream().mapToDouble(this::scorePercent).min().orElse(0.0);

            QuizDTO.QuizAnalyticsSummary quizAnalytics = new QuizDTO.QuizAnalyticsSummary(
                quizzes.size(),
                (int) attempts.stream().map(this::buildParticipantKey).distinct().count(),
                round2(avgScorePercent),
                round2(maxScorePercent),
                round2(minScorePercent),
                attempts.isEmpty() ? 0.0 : round2((completedAttempts * 100.0) / attempts.size())
            );

            Map<String, TopicAggregate> topicAggregateMap = new HashMap<>();
            for (QuizAttempt attempt : attempts) {
                Quiz attemptQuiz = attempt.getQuiz();
                if (attemptQuiz == null || attemptQuiz.getTopic() == null) {
                continue;
                }
                String key = (attemptQuiz.getStack() == null ? "" : attemptQuiz.getStack().getStackName()) + "::" + attemptQuiz.getTopic().getTopicName();
                TopicAggregate aggregate = topicAggregateMap.computeIfAbsent(key, unused -> new TopicAggregate(
                        attemptQuiz.getTopic().getTopicId(),
                    attemptQuiz.getStack() == null ? "" : attemptQuiz.getStack().getStackName(),
                    attemptQuiz.getTopic().getTopicName()
                ));
                aggregate.attemptCount++;
                aggregate.scoreSum += scorePercent(attempt);
                if (scorePercent(attempt) < 50.0) {
                aggregate.learnersBelow50.add(buildParticipantKey(attempt));
                }
            }

            List<QuizDTO.TopicPerformanceRow> topicPerformance = topicAggregateMap.values().stream()
                .map(aggregate -> {
                    double average = aggregate.attemptCount == 0 ? 0.0 : aggregate.scoreSum / aggregate.attemptCount;
                    return new QuizDTO.TopicPerformanceRow(
                            aggregate.topicId,
                        aggregate.stackName,
                        aggregate.topicName,
                        round2(average),
                        topicStatus(average),
                        aggregate.attemptCount
                    );
                })
                .sorted(Comparator.comparing(QuizDTO.TopicPerformanceRow::getAverageScorePercent))
                .collect(Collectors.toList());

            Map<Long, QuestionAggregate> questionAggregateMap = new HashMap<>();
            for (QuizAttemptAnswer answer : filteredAnswers) {
                if (answer.getQuestion() == null) {
                continue;
                }
                MCQ question = answer.getQuestion();
                QuestionAggregate aggregate = questionAggregateMap.computeIfAbsent(
                    question.getQuestionId(),
                    unused -> new QuestionAggregate(question)
                );
                aggregate.attemptedCount++;
                if (!answer.isCorrect()) {
                aggregate.incorrectCount++;
                }
            }

            List<QuizDTO.QuestionAnalyticsRow> questionAnalytics = questionAggregateMap.values().stream()
                .map(aggregate -> {
                    double successRate = aggregate.attemptedCount == 0
                        ? 0.0
                        : ((aggregate.attemptedCount - aggregate.incorrectCount) * 100.0) / aggregate.attemptedCount;
                    return new QuizDTO.QuestionAnalyticsRow(
                        aggregate.questionId,
                        aggregate.questionStem,
                        aggregate.stackName,
                        aggregate.topicName,
                        aggregate.attemptedCount,
                        aggregate.incorrectCount,
                        round2(successRate),
                        questionDifficultySignal(successRate, aggregate.attemptedCount)
                    );
                })
                .sorted(Comparator.comparing(QuizDTO.QuestionAnalyticsRow::getAttemptedCount).reversed())
                .collect(Collectors.toList());

            Map<String, EngagementUserAggregate> userAggregateMap = new HashMap<>();
            Map<String, EngagementTopicAggregate> engagementTopicMap = new HashMap<>();
            Map<Long, EngagementQuizAggregate> engagementQuizMap = new HashMap<>();

            for (QuizAttempt attempt : attempts) {
                Quiz attemptQuiz = attempt.getQuiz();
                if (attemptQuiz == null) {
                continue;
                }
                String participantKey = buildParticipantKey(attempt);

                EngagementUserAggregate userAggregate = userAggregateMap.computeIfAbsent(
                    participantKey,
                    unused -> new EngagementUserAggregate(attempt.getParticipantName(), attempt.getParticipantEnterpriseId())
                );
                userAggregate.attemptsCount++;
                userAggregate.quizIds.add(attemptQuiz.getQuizId());
                if (attemptQuiz.getTopic() != null && attemptQuiz.getTopic().getTopicId() != null) {
                userAggregate.topicIds.add(attemptQuiz.getTopic().getTopicId());
                }

                if (attemptQuiz.getTopic() != null) {
                String topicKey = (attemptQuiz.getStack() == null ? "" : attemptQuiz.getStack().getStackName()) + "::" + attemptQuiz.getTopic().getTopicName();
                EngagementTopicAggregate topicAggregate = engagementTopicMap.computeIfAbsent(
                    topicKey,
                    unused -> new EngagementTopicAggregate(
                        attemptQuiz.getStack() == null ? "" : attemptQuiz.getStack().getStackName(),
                        attemptQuiz.getTopic().getTopicName())
                );
                topicAggregate.attemptsCount++;
                topicAggregate.participantKeys.add(participantKey);
                }

                EngagementQuizAggregate quizAggregate = engagementQuizMap.computeIfAbsent(
                    attemptQuiz.getQuizId(),
                    unused -> new EngagementQuizAggregate(attemptQuiz)
                );
                quizAggregate.attemptsCount++;
                quizAggregate.participantKeys.add(participantKey);
            }

            List<QuizDTO.EngagementUserRow> activeParticipants = userAggregateMap.values().stream()
                .map(aggregate -> new QuizDTO.EngagementUserRow(
                    aggregate.participantName,
                    aggregate.participantEnterpriseId,
                    aggregate.attemptsCount,
                    aggregate.quizIds.size(),
                    aggregate.topicIds.size()))
                .sorted(Comparator.comparing(QuizDTO.EngagementUserRow::getAttemptsCount).reversed())
                .limit(10)
                .collect(Collectors.toList());

            List<QuizDTO.EngagementTopicRow> topTopics = engagementTopicMap.values().stream()
                .map(aggregate -> new QuizDTO.EngagementTopicRow(
                    aggregate.stackName,
                    aggregate.topicName,
                    aggregate.attemptsCount,
                    aggregate.participantKeys.size()))
                .sorted(Comparator.comparing(QuizDTO.EngagementTopicRow::getAttemptsCount).reversed())
                .limit(10)
                .collect(Collectors.toList());

            List<QuizDTO.EngagementQuizRow> topQuizzes = engagementQuizMap.values().stream()
                .map(aggregate -> new QuizDTO.EngagementQuizRow(
                    aggregate.quizId,
                    aggregate.quizCode,
                    aggregate.title,
                    aggregate.stackName,
                    aggregate.topicName,
                    aggregate.participantKeys.size(),
                    aggregate.attemptsCount))
                .sorted(Comparator.comparing(QuizDTO.EngagementQuizRow::getParticipantsCount).reversed())
                .limit(10)
                .collect(Collectors.toList());

            QuizDTO.UserEngagementSummary userEngagement = new QuizDTO.UserEngagementSummary(
                userAggregateMap.size(),
                activeParticipants,
                topTopics,
                topQuizzes
            );

            List<QuizDTO.KnowledgeGapInsight> knowledgeGaps = topicAggregateMap.values().stream()
                .map(aggregate -> {
                    double average = aggregate.attemptCount == 0 ? 0.0 : aggregate.scoreSum / aggregate.attemptCount;
                    return new QuizDTO.KnowledgeGapInsight(
                        aggregate.stackName,
                        aggregate.topicName,
                        round2(average),
                        aggregate.attemptCount,
                        aggregate.learnersBelow50.size(),
                        buildKnowledgeGapMessage(aggregate.stackName, aggregate.topicName, average, aggregate.learnersBelow50.size())
                    );
                })
                .filter(gap -> gap.getAverageScorePercent() < 60.0)
                .sorted(Comparator.comparing(QuizDTO.KnowledgeGapInsight::getAverageScorePercent))
                .limit(8)
                .collect(Collectors.toList());

        int totalHosted = hostedByTopicId.values().stream().mapToInt(Integer::intValue).sum();
            return new QuizDTO.ParticipantTopicAnalyticsResponse(
                normalizedType,
                totalHosted,
                rows.size(),
                rows,
                quizAnalytics,
                topicPerformance,
                questionAnalytics,
                userEngagement,
                knowledgeGaps
            );
    }

    private Quiz getQuizByCodeOrThrow(String quizCode) {
        return quizRepository.findByQuizCode(quizCode)
                .orElseThrow(() -> new NotFoundException("Quiz not found"));
    }

    private String normalizeParticipantType(String participantType) {
        String type = participantType == null ? "EMPLOYEE" : participantType.trim().toUpperCase(Locale.ROOT);
        if (!type.equals("EMPLOYEE") && !type.equals("GUEST")) {
            throw new BusinessRuleException("Participant type must be EMPLOYEE or GUEST");
        }
        return type;
    }

    private String normalizeParticipantTypeOrAll(String participantType) {
        String type = participantType == null ? "ALL" : participantType.trim().toUpperCase(Locale.ROOT);
        if (!type.equals("ALL") && !type.equals("EMPLOYEE") && !type.equals("GUEST")) {
            throw new BusinessRuleException("Participant type must be ALL, EMPLOYEE or GUEST");
        }
        return type;
    }

    private boolean matchesParticipantType(QuizAttempt attempt, String participantType) {
        boolean isGuest = attempt.getParticipantEnterpriseId() == null || attempt.getParticipantEnterpriseId().isBlank();
        return participantType.equals("GUEST") ? isGuest : !isGuest;
    }

    private boolean matchesParticipantTypeOrAll(QuizAttempt attempt, String participantType) {
        if ("ALL".equals(participantType)) {
            return true;
        }
        return matchesParticipantType(attempt, participantType);
    }

    private String buildParticipantKey(QuizAttempt attempt) {
        if (attempt.getParticipantEnterpriseId() == null || attempt.getParticipantEnterpriseId().isBlank()) {
            return "GUEST::" + (attempt.getParticipantName() == null ? "Unknown Guest" : attempt.getParticipantName().trim().toLowerCase(Locale.ROOT));
        }
        return "EMPLOYEE::" + attempt.getParticipantEnterpriseId().trim().toLowerCase(Locale.ROOT);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String rankBadge(int rank) {
        if (rank == 1) return "GOLD";
        if (rank == 2) return "SILVER";
        if (rank == 3) return "BRONZE";
        return "";
    }

    private List<AggregatedParticipantLeaderboard> buildAggregatedLeaderboard(List<QuizAttempt> attempts,
                                                                               List<QuizAttemptAnswer> answers) {
        Map<Long, Integer> attemptedByAttemptId = answers.stream()
                .filter(answer -> answer.getAttempt() != null)
                .collect(Collectors.groupingBy(
                        answer -> answer.getAttempt().getAttemptId(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                ));

        Map<String, AggregatedParticipantLeaderboard> byParticipant = new HashMap<>();
        for (QuizAttempt attempt : attempts) {
            String participantKey = buildParticipantKey(attempt);
            AggregatedParticipantLeaderboard aggregate = byParticipant.computeIfAbsent(
                    participantKey,
                    unused -> new AggregatedParticipantLeaderboard(attempt.getParticipantName(), attempt.getParticipantEnterpriseId())
            );

            int attemptedCount = attemptedByAttemptId.getOrDefault(attempt.getAttemptId(), safeInt(attempt.getTotalQuestions()));
            int correctCount = safeInt(attempt.getCorrectCount());
            int wrongCount = Math.max(0, attemptedCount - correctCount);

            aggregate.attemptedCount += attemptedCount;
            aggregate.correctCount += correctCount;
            aggregate.wrongCount += wrongCount;
            aggregate.totalTimeTakenSeconds += safeInt(attempt.getTimeTakenSeconds());
            aggregate.attemptCount += 1;

            if (attempt.getQuiz() != null) {
                aggregate.quizIds.add(attempt.getQuiz().getQuizId());
                if (attempt.getQuiz().getTopic() != null && attempt.getQuiz().getTopic().getTopicId() != null) {
                    aggregate.topicIds.add(attempt.getQuiz().getTopic().getTopicId());
                }
            }

            LocalDateTime submittedAt = attempt.getSubmittedAt();
            if (submittedAt != null && (aggregate.firstSubmittedAt == null || submittedAt.isBefore(aggregate.firstSubmittedAt))) {
                aggregate.firstSubmittedAt = submittedAt;
            }
        }

        return byParticipant.values().stream()
                .sorted(Comparator
                        .comparing(AggregatedParticipantLeaderboard::scorePercent).reversed()
                        .thenComparing(AggregatedParticipantLeaderboard::averageTimeTakenSeconds)
                        .thenComparing(aggregate -> aggregate.firstSubmittedAt == null ? LocalDateTime.MAX : aggregate.firstSubmittedAt))
                .collect(Collectors.toList());
    }

    private String buildRecommendation(String topicName,
                                       String stackName,
                                       double engagementPercent,
                                       double performancePercent) {
        if (performancePercent < 60.0 && engagementPercent < 60.0) {
            return "Revise " + topicName + " (" + stackName + ") and attend more quizzes in this subtopic.";
        }
        if (performancePercent < 60.0) {
            return "Revise " + topicName + " (" + stackName + ") to improve correctness.";
        }
        if (engagementPercent < 60.0) {
            return "Attend more hosted quizzes for " + topicName + " to improve engagement.";
        }
        return "Good progress. Continue practicing " + topicName + ".";
    }

    private double scorePercent(QuizAttempt attempt) {
        int total = safeInt(attempt.getTotalQuestions());
        if (total <= 0) {
            return 0.0;
        }
        return (safeInt(attempt.getScore()) * 100.0) / total;
    }

    private String topicStatus(double averageScorePercent) {
        if (averageScorePercent >= 75.0) {
            return "Strong";
        }
        if (averageScorePercent >= 55.0) {
            return "Needs Improvement";
        }
        return "Weak";
    }

    private String questionDifficultySignal(double successRatePercent, int attemptedCount) {
        if (attemptedCount < 3) {
            return "Insufficient Data";
        }
        if (successRatePercent >= 85.0) {
            return "Too Easy";
        }
        if (successRatePercent <= 35.0) {
            return "Too Hard";
        }
        if (successRatePercent <= 55.0) {
            return "Needs Improvement";
        }
        return "Balanced";
    }

    private String buildKnowledgeGapMessage(String stackName,
                                            String topicName,
                                            double averageScorePercent,
                                            int learnersBelow50) {
        if (averageScorePercent < 50.0) {
            return "Most learners are scoring below 50% in " + topicName + " (" + stackName + "). This is a major knowledge gap.";
        }
        if (averageScorePercent < 60.0) {
            return "" + learnersBelow50 + " learner(s) are below 50% in " + topicName + ". Recommend focused revision and targeted practice.";
        }
        return "Monitor " + topicName + " for emerging gaps.";
    }


    private String normalizeSelectedAnswers(List<String> answers) {
        if (answers == null || answers.isEmpty()) {
            return "";
        }
        Set<String> normalized = new HashSet<>();
        for (String value : answers) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim().toUpperCase();
            if (trimmed.matches("^[A-F]$")) {
                normalized.add(trimmed);
            }
        }
        List<String> ordered = new ArrayList<>(normalized);
        Collections.sort(ordered);
        return String.join(",", ordered);
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    private String generateUniqueQuizCode() {
        for (int i = 0; i < 25; i++) {
            String candidate = randomCode(10);
            if (quizRepository.findByQuizCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique quiz code. Try again.");
    }

    private String randomCode(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String buildQuizLink(String quizCode) {
        String trimmedBaseUrl = quizPublicBaseUrl == null
                ? "http://localhost:3000"
                : quizPublicBaseUrl.trim();

        if (trimmedBaseUrl.endsWith("/")) {
            trimmedBaseUrl = trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1);
        }

        return trimmedBaseUrl + "/quiz/" + quizCode;
    }

    private int normalizeDifficultyCount(Integer value, String label) {
        if (value == null || value < 0) {
            throw new BusinessRuleException(label + " count must be zero or greater");
        }
        return value;
    }

    private int resolveTotalQuestions(Integer requestedTotal) {
        if (requestedTotal == null) {
            return DEFAULT_QUIZ_TOTAL_QUESTIONS;
        }
        if (requestedTotal <= 0) {
            throw new BusinessRuleException("Total questions must be greater than zero");
        }
        return requestedTotal;
    }

    private int resolveTypeCount(Integer requestedValue, int defaultValue, String label) {
        if (requestedValue == null) {
            return defaultValue;
        }
        if (requestedValue < 0) {
            throw new BusinessRuleException(label + " count must be zero or greater");
        }
        return requestedValue;
    }

    private void validateTypeComposition(int totalQuestions, int mcqCount, int msqCount) {
        if (mcqCount + msqCount != totalQuestions) {
            throw new BusinessRuleException("MCQ + MSQ must total exactly " + totalQuestions + " questions");
        }
    }

    private int resolveDurationMinutes(Integer requestedDurationMinutes) {
        if (requestedDurationMinutes == null) {
            return DEFAULT_QUIZ_DURATION_MINUTES;
        }
        if (requestedDurationMinutes <= 0) {
            throw new BusinessRuleException("Quiz duration must be greater than zero minutes");
        }
        return requestedDurationMinutes;
    }

    private static class ParticipantTopicAccumulator {
        private final String participantName;
        private final String participantEnterpriseId;
        private final String participantType;
        private final Long topicId;
        private final String stackName;
        private final String topicName;
        private final Set<Long> attendedQuizIds = new HashSet<>();
        private int correctAnswers;
        private int totalQuestions;

        private ParticipantTopicAccumulator(QuizAttempt attempt, Quiz quiz) {
            this.participantName = attempt.getParticipantName();
            this.participantEnterpriseId = attempt.getParticipantEnterpriseId();
            this.participantType = (attempt.getParticipantEnterpriseId() == null || attempt.getParticipantEnterpriseId().isBlank())
                    ? "GUEST"
                    : "EMPLOYEE";
            this.topicId = quiz.getTopic().getTopicId();
            this.stackName = quiz.getStack() == null ? "" : quiz.getStack().getStackName();
            this.topicName = quiz.getTopic().getTopicName();
        }
    }

    private static class TopicAggregate {
        private final Long topicId;
        private final String stackName;
        private final String topicName;
        private int attemptCount;
        private double scoreSum;
        private final Set<String> learnersBelow50 = new HashSet<>();

        private TopicAggregate(Long topicId, String stackName, String topicName) {
            this.topicId = topicId;
            this.stackName = stackName;
            this.topicName = topicName;
        }
    }

    private static class QuestionAggregate {
        private final Long questionId;
        private final String questionStem;
        private final String stackName;
        private final String topicName;
        private int attemptedCount;
        private int incorrectCount;

        private QuestionAggregate(MCQ question) {
            this.questionId = question.getQuestionId();
            this.questionStem = question.getQuestionStem();
            this.stackName = question.getStack() == null ? "" : question.getStack().getStackName();
            this.topicName = question.getTopic() == null ? "" : question.getTopic().getTopicName();
        }
    }

    private static class EngagementUserAggregate {
        private final String participantName;
        private final String participantEnterpriseId;
        private int attemptsCount;
        private final Set<Long> quizIds = new HashSet<>();
        private final Set<Long> topicIds = new HashSet<>();

        private EngagementUserAggregate(String participantName, String participantEnterpriseId) {
            this.participantName = participantName;
            this.participantEnterpriseId = participantEnterpriseId;
        }
    }

    private static class EngagementTopicAggregate {
        private final String stackName;
        private final String topicName;
        private int attemptsCount;
        private final Set<String> participantKeys = new HashSet<>();

        private EngagementTopicAggregate(String stackName, String topicName) {
            this.stackName = stackName;
            this.topicName = topicName;
        }
    }

    private static class EngagementQuizAggregate {
        private final Long quizId;
        private final String quizCode;
        private final String title;
        private final String stackName;
        private final String topicName;
        private int attemptsCount;
        private final Set<String> participantKeys = new HashSet<>();

        private EngagementQuizAggregate(Quiz quiz) {
            this.quizId = quiz.getQuizId();
            this.quizCode = quiz.getQuizCode();
            this.title = quiz.getTitle();
            this.stackName = quiz.getStack() == null ? "" : quiz.getStack().getStackName();
            this.topicName = quiz.getTopic() == null ? "" : quiz.getTopic().getTopicName();
        }
    }

    private static class AggregatedParticipantLeaderboard {
        private final String participantName;
        private final String participantEnterpriseId;
        private int attemptedCount;
        private int correctCount;
        private int wrongCount;
        private int totalTimeTakenSeconds;
        private int attemptCount;
        private final Set<Long> quizIds = new HashSet<>();
        private final Set<Long> topicIds = new HashSet<>();
        private LocalDateTime firstSubmittedAt;

        private AggregatedParticipantLeaderboard(String participantName, String participantEnterpriseId) {
            this.participantName = participantName;
            this.participantEnterpriseId = participantEnterpriseId;
        }

        private int quizzesPlayed() {
            return quizIds.size();
        }

        private int topicsCovered() {
            return topicIds.size();
        }

        private int averageTimeTakenSeconds() {
            return attemptCount <= 0 ? 0 : (int) Math.round(totalTimeTakenSeconds * 1.0 / attemptCount);
        }

        private double scorePercent() {
            return attemptedCount <= 0 ? 0.0 : (correctCount * 100.0) / attemptedCount;
        }
    }

    private static class TopicHostedAggregate {
        private final Long topicId;
        private final String stackName;
        private final String topicName;
        private long quizzesHosted;

        private TopicHostedAggregate(Long topicId, String stackName, String topicName) {
            this.topicId = topicId;
            this.stackName = stackName;
            this.topicName = topicName;
        }
    }

    private static class TopicSuggestionAccumulator {
        private final String stackName;
        private final String topicName;
        private double sumPercent;
        private int count;

        private TopicSuggestionAccumulator(String stackName, String topicName) {
            this.stackName = stackName;
            this.topicName = topicName;
        }
    }

}
