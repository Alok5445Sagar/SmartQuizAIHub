package com.smartquiz.service;

import org.springframework.security.access.prepost.PreAuthorize;

import com.smartquiz.exception.NotFoundException;
import com.smartquiz.exception.ForbiddenException;
import com.smartquiz.exception.ConflictException;
import com.smartquiz.exception.BusinessRuleException;

import com.smartquiz.ai.AIService;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.dto.SimilarityCheckResponse;
import com.smartquiz.entity.*;
import com.smartquiz.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MCQService {

    @Autowired private MCQRepository mcqRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TechnologyStackRepository stackRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private UserStackMappingRepository userStackMappingRepository;
    @Autowired private MCQTranslationRepository mcqTranslationRepository;
    @Autowired @org.springframework.context.annotation.Lazy private AIService aiService;

    private static final Set<String> SUPPORTED_LANGUAGE_CODES = Set.of("en", "es", "fr", "ro");

    @PreAuthorize("hasAnyRole('SME','ADMIN')")

    public MCQDTO.Response createMCQ(String creatorId, MCQDTO.CreateRequest req) {
        User creator = userRepository.findByEnterpriseId(creatorId).orElseThrow(() -> new NotFoundException("User not found"));
        TechnologyStack stack = stackRepository.findById(req.getStackId()).orElseThrow(() -> new NotFoundException("Stack not found"));
        Topic topic = topicRepository.findById(req.getTopicId()).orElseThrow(() -> new NotFoundException("Topic not found"));

        MCQ.QuestionType normalizedType = normalizeQuestionType(req.getQuestionType());
        String normalizedCorrectAnswer = normalizeCorrectAnswer(
            req.getCorrectAnswer(),
            normalizedType,
            req.getOptionA(),
            req.getOptionB(),
            req.getOptionC(),
            req.getOptionD(),
            req.getOptionE(),
            req.getOptionF());

        String normalizedStem = req.getQuestionStem().trim().replaceAll("\\s+", " ");
        // Duplicate check policy:
        // - AI-generated MCQs are validated during generation/create flow.
        // - Manually added MCQs (Add UI) are validated on Edit page/update flow.
        if (req.isAiGenerated() && mcqRepository.existsByQuestionStemAndStackAndTopic(
            normalizedStem, req.getStackId(), req.getTopicId())) {
            throw new ConflictException(
                "Duplicate question: A question with the same stem already exists in this stack and topic.");
        }

        MCQ mcq = new MCQ();
        mcq.setQuestionStem(normalizedStem);
        mcq.setQuestionType(normalizedType);
        mcq.setStack(stack);
        mcq.setTopic(topic);
        mcq.setDifficulty(MCQ.Difficulty.valueOf(req.getDifficulty()));
        mcq.setOptionA(req.getOptionA());
        mcq.setOptionB(req.getOptionB());
        mcq.setOptionC(req.getOptionC());
        mcq.setOptionD(req.getOptionD());
        mcq.setOptionE(req.getOptionE());
        mcq.setOptionF(req.getOptionF());
        mcq.setCorrectAnswer(normalizedCorrectAnswer);
        mcq.setCreator(creator);
        mcq.setStatus(req.isSendForReview() ? MCQ.Status.READY_FOR_REVIEW : MCQ.Status.DRAFT);
        mcq.setAiGenerated(req.isAiGenerated());
        mcq.setBulkUploaded(false);
        
        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    @PreAuthorize("hasAnyRole('SME','ADMIN')")

    public MCQDTO.Response updateMCQ(String updaterId, Long questionId, MCQDTO.UpdateRequest req, boolean isAdmin) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));

        // Only creator can edit DRAFT, Admin can edit any non-draft
        if (!isAdmin && !mcq.getCreator().getEnterpriseId().equals(updaterId)) {
            throw new ForbiddenException("Not authorized to edit this MCQ");
        }
        if (!isAdmin) {
            // SME can only edit their own DRAFT or REJECTED questions
            if (!mcq.getCreator().getEnterpriseId().equals(updaterId)) {
                throw new ForbiddenException("Not authorized to edit this MCQ");
            }
            if (mcq.getStatus() != MCQ.Status.DRAFT && mcq.getStatus() != MCQ.Status.REJECTED) {
                throw new BusinessRuleException("Can only edit DRAFT or REJECTED questions");
            }
        } else {
            // Admin can edit any non-draft question.
            // For DRAFT, only the admin who created it can edit.
            if (mcq.getStatus() == MCQ.Status.DRAFT) {
                if (!mcq.getCreator().getEnterpriseId().equals(updaterId)) {
                    throw new ForbiddenException("Admin can edit DRAFT questions only if they are the creator.");
                }
            }
        }

        TechnologyStack stack = stackRepository.findById(req.getStackId()).orElseThrow(() -> new NotFoundException("Stack not found"));
        Topic topic = topicRepository.findById(req.getTopicId()).orElseThrow(() -> new NotFoundException("Topic not found"));

        MCQ.QuestionType normalizedType = normalizeQuestionType(req.getQuestionType());
        String normalizedCorrectAnswer = normalizeCorrectAnswer(
            req.getCorrectAnswer(),
            normalizedType,
            req.getOptionA(),
            req.getOptionB(),
            req.getOptionC(),
            req.getOptionD(),
            req.getOptionE(),
            req.getOptionF());

        String normalizedStem = req.getQuestionStem().trim().replaceAll("\\s+", " ");
        // Duplicate check policy for updates:
        // Manual MCQs are checked on Edit page/update flow.
        if (!mcq.isAiGenerated() && !mcq.isBulkUploaded()
            && mcqRepository.existsByQuestionStemAndStackAndTopicNonDraft(
                normalizedStem, req.getStackId(), req.getTopicId())
            && mcqRepository.existsByQuestionStemAndStackAndTopicExcluding(
                normalizedStem, req.getStackId(), req.getTopicId(), questionId)) {
            throw new ConflictException(
                "Duplicate question: A question with the same stem already exists in this stack and topic.");
        }

        mcq.setQuestionStem(normalizedStem);
        mcq.setQuestionType(normalizedType);
        mcq.setStack(stack);
        mcq.setTopic(topic);
        mcq.setDifficulty(MCQ.Difficulty.valueOf(req.getDifficulty()));
        mcq.setOptionA(req.getOptionA());
        mcq.setOptionB(req.getOptionB());
        mcq.setOptionC(req.getOptionC());
        mcq.setOptionD(req.getOptionD());
        mcq.setOptionE(req.getOptionE());
        mcq.setOptionF(req.getOptionF());
        mcq.setCorrectAnswer(normalizedCorrectAnswer);


        if (req.isSendForReview()) {
            mcq.setStatus(MCQ.Status.READY_FOR_REVIEW);
            mcq.setReviewerComments(null);
        }

        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    @PreAuthorize("hasAnyRole('SME','ADMIN')")

    public void deleteMCQ(String requesterId, Long questionId, boolean isAdmin) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));

        if (!isAdmin) {
            if (!mcq.getCreator().getEnterpriseId().equals(requesterId)) {
                throw new ForbiddenException("Not authorized to delete this MCQ");
            }
            if (mcq.getStatus() != MCQ.Status.DRAFT && mcq.getStatus() != MCQ.Status.REJECTED) {
                throw new BusinessRuleException("Can only delete DRAFT or REJECTED questions");
            }
        } else {
            if (mcq.getStatus() == MCQ.Status.DRAFT) {
                throw new ForbiddenException("Admin cannot delete DRAFT questions. Only the creator can delete a DRAFT.");
            }
        }

        mcqRepository.delete(mcq);
    }

    public Page<MCQDTO.Response> getMyQuestions(
        String enterpriseId, int page, int size,
        Long stackId, Long topicId, String difficulty, String status, String languageCode) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        MCQ.Difficulty difficultyEnum = null;
        if (difficulty != null && !difficulty.isBlank()) {
            try { difficultyEnum = MCQ.Difficulty.valueOf(difficulty); } catch (Exception ignored) {}
        }

        MCQ.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = MCQ.Status.valueOf(status); } catch (Exception ignored) {}
        }

        Page<MCQ> mcqs = mcqRepository.findByCreatorWithFilters(
                enterpriseId, stackId, topicId, difficultyEnum, statusEnum, pageable)
            ;
        return mapWithTranslations(mcqs, languageCode);
    }

    public Page<MCQDTO.Response> getMyQuestions(
        String enterpriseId, int page, int size,
        Long stackId, Long topicId, String difficulty, String status) {
        return getMyQuestions(enterpriseId, page, size, stackId, topicId, difficulty, status, "en");
    }

        public Page<MCQDTO.Response> getMyPendingReviews(String enterpriseId, int page, int size, String languageCode) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<MCQ> mcqs = mcqRepository.findByReviewerEnterpriseIdAndStatus(enterpriseId, MCQ.Status.UNDER_REVIEW, pageable);
        return mapWithTranslations(mcqs, languageCode);
    }

    @PreAuthorize("hasRole('ADMIN')")

    public Page<MCQDTO.Response> getAllMCQs(
            int page, int size,
            Long stackId, Long topicId, String difficulty, String status, String search, String languageCode) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("updatedAt").descending().and(Sort.by("createdAt").descending()));

        MCQ.Difficulty difficultyEnum = null;
        if (difficulty != null && !difficulty.isBlank()) {
            try { difficultyEnum = MCQ.Difficulty.valueOf(difficulty); } catch (Exception ignored) {}
        }

        MCQ.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = MCQ.Status.valueOf(status); } catch (Exception ignored) {}
        }

        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<MCQ> mcqs = mcqRepository.findAllWithFilters(
            stackId, topicId, difficultyEnum, statusEnum, searchParam, pageable);
        return mapWithTranslations(mcqs, languageCode);
    }

    @PreAuthorize("hasRole('ADMIN')")

    public Page<MCQDTO.Response> getAllMCQs(
            int page, int size,
            Long stackId, Long topicId, String difficulty, String status, String search) {
        return getAllMCQs(page, size, stackId, topicId, difficulty, status, search, "en");
    }

    @PreAuthorize("hasRole('ADMIN')")

    public Map<String, Long> getQuestionBankStats(
            Long stackId, Long topicId, String difficulty, String search) {

        MCQ.Difficulty difficultyEnum = null;
        if (difficulty != null && !difficulty.isBlank()) {
            try { difficultyEnum = MCQ.Difficulty.valueOf(difficulty); } catch (Exception ignored) {}
        }

        String searchParam = (search != null && !search.isBlank()) ? search.trim() : null;

        long total = mcqRepository.countAllWithFilters(stackId, topicId, difficultyEnum, searchParam);
        long readyForReview = mcqRepository.countByStatusWithFilters(
            MCQ.Status.READY_FOR_REVIEW, stackId, topicId, difficultyEnum, searchParam);
        long underReview = mcqRepository.countByStatusWithFilters(
            MCQ.Status.UNDER_REVIEW, stackId, topicId, difficultyEnum, searchParam);
        long approved = mcqRepository.countByStatusWithFilters(
            MCQ.Status.APPROVED, stackId, topicId, difficultyEnum, searchParam);

        Map<String, Long> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("readyForReview", readyForReview);
        stats.put("underReview", underReview);
        stats.put("approved", approved);
        return stats;
    }

    public MCQDTO.Response getMCQById(Long questionId, String languageCode) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        String normalizedLanguage = normalizeLanguageCode(languageCode);
        if ("en".equals(normalizedLanguage)) {
            return MCQDTO.Response.fromEntity(mcq);
        }
        MCQTranslation translation = mcqTranslationRepository
            .findByMcqQuestionIdAndLanguageCode(questionId, normalizedLanguage)
            .orElse(null);
        return MCQDTO.Response.fromEntity(mcq, translation);
    }

    public MCQDTO.Response getMCQById(Long questionId) {
        return getMCQById(questionId, "en");
    }

    private Page<MCQDTO.Response> mapWithTranslations(Page<MCQ> mcqPage, String languageCode) {
        String normalizedLanguage = normalizeLanguageCode(languageCode);
        if ("en".equals(normalizedLanguage)) {
            return mcqPage.map(MCQDTO.Response::fromEntity);
        }

        List<Long> questionIds = mcqPage.getContent().stream()
            .map(MCQ::getQuestionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Map<Long, MCQTranslation> translationByQuestionId = new HashMap<>();
        if (!questionIds.isEmpty()) {
            mcqTranslationRepository.findByMcqQuestionIdInAndLanguageCode(questionIds, normalizedLanguage)
                .forEach(translation -> {
                    if (translation.getMcq() != null && translation.getMcq().getQuestionId() != null) {
                        translationByQuestionId.put(translation.getMcq().getQuestionId(), translation);
                    }
                });
        }

        return mcqPage.map(mcq -> MCQDTO.Response.fromEntity(mcq, translationByQuestionId.get(mcq.getQuestionId())));
    }

    public MCQDTO.TranslationListResponse getTranslations(Long questionId) {
        mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        List<MCQDTO.TranslationResponse> rows = mcqTranslationRepository
            .findByMcqQuestionIdOrderByLanguageCodeAsc(questionId)
            .stream()
            .map(MCQDTO.TranslationResponse::fromEntity)
            .collect(Collectors.toList());
        return new MCQDTO.TranslationListResponse(questionId, rows);
    }

    @PreAuthorize("hasAnyRole('SME','ADMIN')")

    public MCQDTO.TranslationRequest suggestTranslation(Long questionId, String languageCode) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        String normalizedLanguage = normalizeLanguageCode(languageCode);
        if ("en".equals(normalizedLanguage)) {
            throw new BusinessRuleException("English is the source language and does not require translation");
        }

        String prompt = "Translate the following technical multiple-choice question into " + normalizedLanguage +
            ". Preserve technical terms and meaning. Return exactly seven lines in this format only:\n" +
            "QUESTION: ...\nA: ...\nB: ...\nC: ...\nD: ...\nE: ...\nF: ...\n" +
            "If E or F are not present in source, return empty after colon for that line.\n" +
            "Source Question: " + mcq.getQuestionStem() + "\n" +
            "A: " + mcq.getOptionA() + "\n" +
            "B: " + mcq.getOptionB() + "\n" +
            "C: " + mcq.getOptionC() + "\n" +
            "D: " + mcq.getOptionD() + "\n" +
            "E: " + safeString(mcq.getOptionE()) + "\n" +
            "F: " + safeString(mcq.getOptionF());

        String aiText = aiService.answerGeneralQuery(prompt, Collections.emptyList(), "translation", "SME");
        Map<String, String> parsed = parseTranslationLines(aiText);

        return new MCQDTO.TranslationRequest(
            normalizedLanguage,
            parsed.getOrDefault("QUESTION", mcq.getQuestionStem()),
            parsed.getOrDefault("A", mcq.getOptionA()),
            parsed.getOrDefault("B", mcq.getOptionB()),
            parsed.getOrDefault("C", mcq.getOptionC()),
            parsed.getOrDefault("D", mcq.getOptionD()),
            parsed.getOrDefault("E", mcq.getOptionE()),
            parsed.getOrDefault("F", mcq.getOptionF())
        );
    }

    @PreAuthorize("hasAnyRole('SME','ADMIN')")

    public MCQDTO.TranslationResponse saveTranslation(String translatorEnterpriseId, Long questionId, MCQDTO.TranslationRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Translation request is required");
        }
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        User translator = userRepository.findByEnterpriseId(translatorEnterpriseId)
            .orElseThrow(() -> new NotFoundException("User not found"));
        if (translator.getRole() != User.Role.ADMIN
                && !mcq.getCreator().getEnterpriseId().equals(translator.getEnterpriseId())) {
            throw new ForbiddenException("Only the question's creator or an admin can save translations");
        }
        String normalizedLanguage = normalizeLanguageCode(request.getLanguageCode());
        if ("en".equals(normalizedLanguage)) {
            throw new BusinessRuleException("English is the source language and cannot be saved as translation");
        }

        MCQTranslation translation = mcqTranslationRepository
            .findByMcqQuestionIdAndLanguageCode(questionId, normalizedLanguage)
            .orElseGet(MCQTranslation::new);

        translation.setMcq(mcq);
        translation.setLanguageCode(normalizedLanguage);
        translation.setQuestionStem(requireValue(request.getQuestionStem(), "Translated question stem"));
        translation.setOptionA(requireValue(request.getOptionA(), "Option A"));
        translation.setOptionB(requireValue(request.getOptionB(), "Option B"));
        translation.setOptionC(requireValue(request.getOptionC(), "Option C"));
        translation.setOptionD(requireValue(request.getOptionD(), "Option D"));
        translation.setOptionE(blankToNull(request.getOptionE()));
        translation.setOptionF(blankToNull(request.getOptionF()));
        translation.setTranslatedBy(translator);

        return MCQDTO.TranslationResponse.fromEntity(mcqTranslationRepository.save(translation));
    }

    private String normalizeLanguageCode(String languageCode) {
        String code = languageCode == null ? "" : languageCode.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LANGUAGE_CODES.contains(code)) {
            throw new BusinessRuleException("Unsupported language code. Use one of: en, es, fr, ro");
        }
        return code;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(fieldName + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private Map<String, String> parseTranslationLines(String aiText) {
        Map<String, String> result = new HashMap<>();
        if (aiText == null || aiText.isBlank()) {
            return result;
        }
        for (String rawLine : aiText.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            int idx = line.indexOf(':');
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim().toUpperCase(Locale.ROOT);
            String value = line.substring(idx + 1).trim();
            if (List.of("QUESTION", "A", "B", "C", "D", "E", "F").contains(key) && !value.isBlank()) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * Assigns a reviewer. Allowed for READY_FOR_REVIEW questions, or for a DRAFT the requesting admin created.
     * The reviewer must be eligible: an active SME mapped to the question's stack, or an active admin,
     * and never the question's creator.
     */
    @PreAuthorize("hasRole('ADMIN')")
    public MCQDTO.Response assignReviewer(Long questionId, String reviewerEnterpriseId, String requesterEnterpriseId) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        String creatorId = mcq.getCreator().getEnterpriseId();
        boolean readyForReview = mcq.getStatus() == MCQ.Status.READY_FOR_REVIEW;
        boolean ownDraft = mcq.getStatus() == MCQ.Status.DRAFT && creatorId.equals(requesterEnterpriseId);
        if (!readyForReview && !ownDraft) {
            throw new ConflictException("A reviewer can only be assigned to a question that is READY_FOR_REVIEW");
        }

        User reviewer = userRepository.findByEnterpriseId(reviewerEnterpriseId)
                .orElseThrow(() -> new NotFoundException("Reviewer not found"));
        boolean eligible = getReviewers(mcq.getStack().getStackId(), creatorId).stream()
                .anyMatch(r -> reviewer.getEnterpriseId().equals(r.get("enterpriseId")));
        if (!eligible) {
            throw new BusinessRuleException("Reviewer is not eligible for this question's stack");
        }

        mcq.setReviewer(reviewer);
        mcq.setStatus(MCQ.Status.UNDER_REVIEW);
        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    @PreAuthorize("hasAnyRole('SME','ADMIN')")
    public MCQDTO.Response reviewMCQ(String reviewerId, Long questionId, MCQDTO.ReviewRequest req) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow(() -> new NotFoundException("Question not found"));
        if (mcq.getStatus() != MCQ.Status.UNDER_REVIEW || mcq.getReviewer() == null) {
            throw new ConflictException("Only questions UNDER_REVIEW with an assigned reviewer can be reviewed");
        }
        if (!mcq.getReviewer().getEnterpriseId().equals(reviewerId)) {
            throw new ForbiddenException("Not authorized to review this MCQ");
        }

        String comments = req.getComments() == null || req.getComments().isBlank() ? null : req.getComments().trim();
        if ("APPROVE".equals(req.getAction())) {
            mcq.setStatus(MCQ.Status.APPROVED);
        } else if ("REJECT".equals(req.getAction())) {
            if (comments == null) {
                throw new BusinessRuleException("Comments are mandatory for rejection");
            }
            mcq.setStatus(MCQ.Status.REJECTED);
        } else {
            throw new BusinessRuleException("Action must be APPROVE or REJECT");
        }
        mcq.setReviewerComments(comments);

        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> getReviewers(Long stackId, String excludeEnterpriseId) {
        List<User> users = userStackMappingRepository.findUsersByStackIdExcluding(stackId, excludeEnterpriseId).stream()
                .filter(User::isActive)
                .filter(u -> u.getRole() == User.Role.SME || u.getRole() == User.Role.ADMIN)
                .collect(Collectors.toList());
        // Also add admins
        List<User> admins = userRepository.findByRoleInOrderByFullNameAsc(List.of(User.Role.ADMIN)).stream()
                .filter(u -> u.isActive() && !u.getEnterpriseId().equals(excludeEnterpriseId))
                .collect(Collectors.toList());
        
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (User u : users) {
            if (seen.add(u.getEnterpriseId())) {
                Map<String, Object> m = new HashMap<>();
                m.put("enterpriseId", u.getEnterpriseId());
                m.put("fullName", u.getFullName());
                m.put("role", u.getRole().name());
                result.add(m);
            }
        }
        for (User u : admins) {
            if (seen.add(u.getEnterpriseId())) {
                Map<String, Object> m = new HashMap<>();
                m.put("enterpriseId", u.getEnterpriseId());
                m.put("fullName", u.getFullName());
                m.put("role", u.getRole().name());
                result.add(m);
            }
        }
        return result;
    }

    @PreAuthorize("hasAnyRole('SME','ADMIN')")

    public List<MCQDTO.Response> bulkUpload(String creatorId, MultipartFile file) throws IOException {
    User creator = userRepository.findByEnterpriseId(creatorId).orElseThrow(() -> new NotFoundException("User not found"));
    List<MCQDTO.Response> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // Validate file extension
    String filename = file.getOriginalFilename();
    if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
        throw new BusinessRuleException("Invalid file type. Please upload an .xlsx or .xls file.");
    }

    try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
        Sheet sheet = workbook.getSheetAt(0);

        if (sheet.getLastRowNum() < 1) {
            throw new BusinessRuleException("The file is empty. Please use the template and add at least one question row.");
        }

        // Validate header row
        Row headerRow = sheet.getRow(0);
        String[] expectedHeaders = {"Stack_Name","Topic_Name","Difficulty","Question_Type","Question_Stem","Option_A","Option_B","Option_C","Option_D","Option_E","Option_F","Correct_Answer"};
        if (headerRow == null) {
            throw new BusinessRuleException("Header row is missing. Please download and use the provided template.");
        }
        for (int i = 0; i < expectedHeaders.length; i++) {
            String actual = getCellValue(headerRow, i);
            if (!expectedHeaders[i].equalsIgnoreCase(actual.trim())) {
                throw new BusinessRuleException(
                    "Invalid template format. Column " + (i+1) + " should be '" + expectedHeaders[i] +
                    "' but found '" + actual + "'. Please download and use the correct template."
                );
            }
        }

        List<String> validDifficulties = List.of("Easy", "Medium", "Hard");
        List<String> validAnswers = List.of("A", "B", "C", "D", "E", "F");

        // Track stems seen within this file to catch intra-file duplicates
        Set<String> seenInFile = new java.util.HashSet<>();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String stackName    = getCellValue(row, 0);
            String topicName    = getCellValue(row, 1);
            String difficulty   = getCellValue(row, 2);
            String questionType = getCellValue(row, 3);
            String stem = getCellValue(row, 4).trim().replaceAll("\\s+", " ");
            String optA         = getCellValue(row, 5);
            String optB         = getCellValue(row, 6);
            String optC         = getCellValue(row, 7);
            String optD         = getCellValue(row, 8);
            String optE         = getCellValue(row, 9);
            String optF         = getCellValue(row, 10);
            String correct      = getCellValue(row, 11);

            int rowNum = i + 1;

            // Validate required fields
            if (stem == null || stem.isBlank()) {
                errors.add("Row " + rowNum + ": Question_Stem is empty.");
                continue;
            }
            if (stackName == null || stackName.isBlank()) {
                errors.add("Row " + rowNum + ": Stack_Name is empty.");
                continue;
            }
            if (optA.isBlank() || optB.isBlank() || optC.isBlank() || optD.isBlank()) {
                errors.add("Row " + rowNum + ": One or more options (A/B/C/D) are empty.");
                continue;
            }
            if (!validDifficulties.contains(difficulty)) {
                errors.add("Row " + rowNum + ": Invalid Difficulty '" + difficulty + "'. Must be Easy, Medium, or Hard.");
                continue;
            }
            String invalidToken = Arrays.stream(correct.toUpperCase(Locale.ROOT).split("[,\\s]+"))
                    .filter(token -> !token.isBlank() && !validAnswers.contains(token))
                    .findFirst()
                    .orElse(null);
            if (invalidToken != null) {
                errors.add("Row " + rowNum + ": Invalid answer '" + invalidToken + "' in Correct_Answer. Must be A, B, C, D, E or F.");
                continue;
            }
            // Same rules as single create: MCQ needs exactly one of A-D; MSQ needs 2+ answers that point at filled options.
            try {
                correct = normalizeCorrectAnswer(correct, normalizeQuestionType(questionType),
                        optA, optB, optC, optD, optE, optF);
            } catch (BusinessRuleException ex) {
                errors.add("Row " + rowNum + ": " + ex.getMessage());
                continue;
            }

            Optional<TechnologyStack> stackOpt = stackRepository.findByStackName(stackName);
            if (stackOpt.isEmpty()) {
                errors.add("Row " + rowNum + ": Stack_Name '" + stackName + "' not found in the system.");
                continue;
            }

            List<Topic> topics = topicRepository.findByStack(stackOpt.get());
            Optional<Topic> topicOpt = topics.stream()
                    .filter(t -> t.getTopicName().equalsIgnoreCase(topicName))
                    .findFirst();
            if (topicOpt.isEmpty()) {
                errors.add("Row " + rowNum + ": Topic_Name '" + topicName + "' not found under stack '" + stackName + "'.");
                continue;
            }

            // Duplicate check
            // Intra-file duplicate check
            String fileKey = (stackOpt.get().getStackId() + "|" +
                    topicOpt.get().getTopicId() + "|" +
                    stem.trim().toLowerCase());
            if (seenInFile.contains(fileKey)) {
                errors.add("Row " + rowNum + ": Duplicate within this file — same question stem already appears earlier in this upload.");
                continue;
            }
            seenInFile.add(fileKey);

            // Bulk upload duplicate checks must run at upload time against all DB statuses.
            SimilarityCheckResponse similarityResponse;
            try {
                similarityResponse = aiService.checkSimilarity(
                        stem,
                        optA,
                        optB,
                        optC,
                        optD,
                        optE.isBlank() ? null : optE,
                        optF.isBlank() ? null : optF,
                        stackOpt.get().getStackId(),
                        topicOpt.get().getTopicId(),
                        null);
            } catch (RuntimeException ex) {
                if (!isTransientAiFailure(ex)) {
                    throw ex;
                }

                boolean exactDuplicate = mcqRepository.existsByQuestionStemAndStackAndTopic(
                        stem,
                        stackOpt.get().getStackId(),
                        topicOpt.get().getTopicId());
                if (exactDuplicate) {
                    errors.add("Row " + rowNum
                            + ": Duplicate/similar question detected against existing questions"
                            + " (exact stem match while AI service is temporarily throttled).");
                    continue;
                }

                similarityResponse = new SimilarityCheckResponse(false, Collections.emptyList());
            }
            if (similarityResponse.isHasSimilar()) {
                String similarIds = similarityResponse.getSimilarQuestions().stream()
                    .map(q -> String.valueOf(q.getQuestionId()))
                    .collect(Collectors.joining(", "));
                errors.add("Row " + rowNum
                    + ": Duplicate/similar question detected against existing questions"
                    + (similarIds.isBlank() ? "." : " (IDs: " + similarIds + ")."));
                continue;
            }

            MCQ mcq = new MCQ();
            mcq.setQuestionStem(stem);
            mcq.setStack(stackOpt.get());
            mcq.setTopic(topicOpt.get());
            mcq.setDifficulty(MCQ.Difficulty.valueOf(difficulty));
            // Validate question type
            MCQ.QuestionType qType = MCQ.QuestionType.MCQ;
            if ("MSQ".equalsIgnoreCase(questionType)) {
                qType = MCQ.QuestionType.MSQ;
            }
            mcq.setQuestionType(qType);
            mcq.setOptionA(optA);
            mcq.setOptionB(optB);
            mcq.setOptionC(optC);
            mcq.setOptionD(optD);
            if (!optE.isBlank()) mcq.setOptionE(optE);
            if (!optF.isBlank()) mcq.setOptionF(optF);
            mcq.setCorrectAnswer(correct);
            mcq.setCreator(creator);
            mcq.setStatus(MCQ.Status.DRAFT);
            mcq.setBulkUploaded(true);
            results.add(MCQDTO.Response.fromEntity(mcqRepository.save(mcq)));
        }
    }

    // If nothing imported and there are errors, throw with details
    if (results.isEmpty() && !errors.isEmpty()) {
        throw new BusinessRuleException("No questions were imported. Errors found:\n" + String.join("\n", errors));
    }

    // If partial import, attach errors to a custom exception
    if (!errors.isEmpty()) {
        throw new PartialUploadException(results, errors);
    }

    return results;
}

    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("MCQs");
            String[] headers = {"Stack_Name", "Topic_Name", "Difficulty", "Question_Type",
                    "Question_Stem", "Option_A", "Option_B", "Option_C", "Option_D",
                    "Option_E", "Option_F", "Correct_Answer"};
            Row headerRow = sheet.createRow(0);
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(style);
                sheet.setColumnWidth(i, 6000);
            }
            // Sample row
            Row sample = sheet.createRow(1);
            // MCQ sample row
            sample.createCell(0).setCellValue("Spring Boot");
            sample.createCell(1).setCellValue("Spring Boot Auto-Configuration");
            sample.createCell(2).setCellValue("Medium");
            sample.createCell(3).setCellValue("MCQ");
            sample.createCell(4).setCellValue("What annotation enables auto-configuration in Spring Boot?");
            sample.createCell(5).setCellValue("@Component");
            sample.createCell(6).setCellValue("@EnableAutoConfiguration");
            sample.createCell(7).setCellValue("@Configuration");
            sample.createCell(8).setCellValue("@SpringBootApplication");
            sample.createCell(9).setCellValue("");   // Option E (blank for MCQ)
            sample.createCell(10).setCellValue("");  // Option F (blank for MCQ)
            sample.createCell(11).setCellValue("B");

            // MSQ sample row
            Row sample2 = sheet.createRow(2);
            sample2.createCell(0).setCellValue("Core Java");
            sample2.createCell(1).setCellValue("OOP Concepts");
            sample2.createCell(2).setCellValue("Medium");
            sample2.createCell(3).setCellValue("MSQ");
            sample2.createCell(4).setCellValue("Which of the following are OOP principles?");
            sample2.createCell(5).setCellValue("Inheritance");
            sample2.createCell(6).setCellValue("Compilation");
            sample2.createCell(7).setCellValue("Encapsulation");
            sample2.createCell(8).setCellValue("Linking");
            sample2.createCell(9).setCellValue("Polymorphism");
            sample2.createCell(10).setCellValue("");  // Option F (blank)
            sample2.createCell(11).setCellValue("A,C,E");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=template.xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        }
    }

    private String getCellValue(Row row, int idx) {
        Cell cell = row.getCell(idx);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private boolean isTransientAiFailure(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("429")
                || normalized.contains("too many requests")
                || normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("gateway")
                || normalized.contains("request failed")
                || normalized.contains("500")
                || normalized.contains("502")
                || normalized.contains("503")
                || normalized.contains("504");
    }

    private MCQ.QuestionType normalizeQuestionType(String questionType) {
        if (questionType == null || questionType.isBlank()) {
            return MCQ.QuestionType.MCQ;
        }
        return "MSQ".equalsIgnoreCase(questionType.trim()) ? MCQ.QuestionType.MSQ : MCQ.QuestionType.MCQ;
    }

    private String normalizeCorrectAnswer(
            String correctAnswer,
            MCQ.QuestionType questionType,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String optionE,
            String optionF) {

        List<String> orderedOptions = List.of("A", "B", "C", "D", "E", "F");
        Map<String, String> optionMap = new HashMap<>();
        optionMap.put("A", optionA);
        optionMap.put("B", optionB);
        optionMap.put("C", optionC);
        optionMap.put("D", optionD);
        optionMap.put("E", optionE);
        optionMap.put("F", optionF);

        Set<String> parsed = new HashSet<>();
        if (correctAnswer != null) {
            String[] tokens = correctAnswer.toUpperCase().split("[,\\s]+");
            for (String token : tokens) {
                if (token.matches("^[A-F]$")) {
                    parsed.add(token);
                }
            }
        }

        if (questionType == MCQ.QuestionType.MCQ) {
            if (parsed.size() != 1) {
                throw new BusinessRuleException("MCQ requires exactly one correct answer from A to D.");
            }
            String answer = parsed.iterator().next();
            if (!List.of("A", "B", "C", "D").contains(answer)) {
                throw new BusinessRuleException("MCQ correct answer must be one of A, B, C, D.");
            }
            return answer;
        }

        if (parsed.size() < 2) {
            throw new BusinessRuleException("MSQ requires at least 2 correct answers.");
        }

        for (String answer : parsed) {
            String optionValue = optionMap.get(answer);
            if (optionValue == null || optionValue.isBlank()) {
                throw new BusinessRuleException("MSQ correct answer '" + answer + "' must map to a non-empty option.");
            }
        }

        return orderedOptions.stream()
                .filter(parsed::contains)
                .collect(Collectors.joining(","));
    }
}
