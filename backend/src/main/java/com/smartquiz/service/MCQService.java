package com.smartquiz.service;

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
    @Autowired @org.springframework.context.annotation.Lazy private AIService aiService;

    public MCQDTO.Response createMCQ(String creatorId, MCQDTO.CreateRequest req) {
        User creator = userRepository.findByEnterpriseId(creatorId).orElseThrow();
        TechnologyStack stack = stackRepository.findById(req.getStackId()).orElseThrow();
        Topic topic = topicRepository.findById(req.getTopicId()).orElseThrow();

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
            throw new RuntimeException(
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
        
        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    public MCQDTO.Response updateMCQ(String updaterId, Long questionId, MCQDTO.UpdateRequest req, boolean isAdmin) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow();

        // Only creator can edit DRAFT, Admin can edit any non-draft
        if (!isAdmin && !mcq.getCreator().getEnterpriseId().equals(updaterId)) {
            throw new RuntimeException("Not authorized to edit this MCQ");
        }
        if (!isAdmin) {
            // SME can only edit their own DRAFT or REJECTED questions
            if (!mcq.getCreator().getEnterpriseId().equals(updaterId)) {
                throw new RuntimeException("Not authorized to edit this MCQ");
            }
            if (mcq.getStatus() != MCQ.Status.DRAFT && mcq.getStatus() != MCQ.Status.REJECTED) {
                throw new RuntimeException("Can only edit DRAFT or REJECTED questions");
            }
        } else {
            // Admin can edit any non-draft question.
            // For DRAFT, only the admin who created it can edit.
            if (mcq.getStatus() == MCQ.Status.DRAFT) {
                if (!mcq.getCreator().getEnterpriseId().equals(updaterId)) {
                    throw new RuntimeException("Admin can edit DRAFT questions only if they are the creator.");
                }
            }
        }

        TechnologyStack stack = stackRepository.findById(req.getStackId()).orElseThrow();
        Topic topic = topicRepository.findById(req.getTopicId()).orElseThrow();

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
        if (!mcq.isAiGenerated()
            && mcqRepository.existsByQuestionStemAndStackAndTopicNonDraft(
                normalizedStem, req.getStackId(), req.getTopicId())
            && mcqRepository.existsByQuestionStemAndStackAndTopicExcluding(
                normalizedStem, req.getStackId(), req.getTopicId(), questionId)) {
            throw new RuntimeException(
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

    public void deleteMCQ(String requesterId, Long questionId, boolean isAdmin) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow();

        if (!isAdmin) {
            if (!mcq.getCreator().getEnterpriseId().equals(requesterId)) {
                throw new RuntimeException("Not authorized to delete this MCQ");
            }
            if (mcq.getStatus() != MCQ.Status.DRAFT && mcq.getStatus() != MCQ.Status.REJECTED) {
                throw new RuntimeException("Can only delete DRAFT or REJECTED questions");
            }
        } else {
            if (mcq.getStatus() == MCQ.Status.DRAFT) {
                throw new RuntimeException("Admin cannot delete DRAFT questions. Only the creator can delete a DRAFT.");
            }
        }

        mcqRepository.delete(mcq);
    }

    public Page<MCQDTO.Response> getMyQuestions(
        String enterpriseId, int page, int size,
        Long stackId, Long topicId, String difficulty, String status) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        MCQ.Difficulty difficultyEnum = null;
        if (difficulty != null && !difficulty.isBlank()) {
            try { difficultyEnum = MCQ.Difficulty.valueOf(difficulty); } catch (Exception ignored) {}
        }

        MCQ.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = MCQ.Status.valueOf(status); } catch (Exception ignored) {}
        }

        return mcqRepository.findByCreatorWithFilters(
                enterpriseId, stackId, topicId, difficultyEnum, statusEnum, pageable)
                .map(MCQDTO.Response::fromEntity);
    }

    public Page<MCQDTO.Response> getMyPendingReviews(String enterpriseId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        return mcqRepository.findByReviewerEnterpriseIdAndStatus(enterpriseId, MCQ.Status.UNDER_REVIEW, pageable)
                .map(MCQDTO.Response::fromEntity);
    }

    public Page<MCQDTO.Response> getAllMCQs(
            int page, int size,
            Long stackId, Long topicId, String difficulty, String status, String search) {

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

        return mcqRepository.findAllWithFilters(
                stackId, topicId, difficultyEnum, statusEnum, searchParam, pageable)
                .map(MCQDTO.Response::fromEntity);
    }

    public MCQDTO.Response getMCQById(Long questionId) {
        return MCQDTO.Response.fromEntity(mcqRepository.findById(questionId).orElseThrow());
    }

    public MCQDTO.Response assignReviewer(Long questionId, String reviewerEnterpriseId) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow();
        User reviewer = userRepository.findByEnterpriseId(reviewerEnterpriseId).orElseThrow();
        mcq.setReviewer(reviewer);
        mcq.setStatus(MCQ.Status.UNDER_REVIEW);
        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    public MCQDTO.Response reviewMCQ(String reviewerId, Long questionId, MCQDTO.ReviewRequest req) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow();
        if (!mcq.getReviewer().getEnterpriseId().equals(reviewerId)) {
            throw new RuntimeException("Not authorized to review this MCQ");
        }

        if ("APPROVE".equals(req.getAction())) {
            mcq.setStatus(MCQ.Status.APPROVED);
        } else if ("REJECT".equals(req.getAction())) {
            if (req.getComments() == null || req.getComments().isBlank()) {
                throw new RuntimeException("Comments are mandatory for rejection");
            }
            mcq.setStatus(MCQ.Status.REJECTED);
            mcq.setReviewerComments(req.getComments());
        }

        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    public List<Map<String, Object>> getReviewers(Long stackId, String excludeEnterpriseId) {
        List<User> users = userStackMappingRepository.findUsersByStackIdExcluding(stackId, excludeEnterpriseId);
        // Also add admins
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.ADMIN && !u.getEnterpriseId().equals(excludeEnterpriseId))
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

    public List<MCQDTO.Response> bulkUpload(String creatorId, MultipartFile file) throws IOException {
    User creator = userRepository.findByEnterpriseId(creatorId).orElseThrow();
    List<MCQDTO.Response> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();

    // Validate file extension
    String filename = file.getOriginalFilename();
    if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
        throw new RuntimeException("Invalid file type. Please upload an .xlsx or .xls file.");
    }

    try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
        Sheet sheet = workbook.getSheetAt(0);

        if (sheet.getLastRowNum() < 1) {
            throw new RuntimeException("The file is empty. Please use the template and add at least one question row.");
        }

        // Validate header row
        Row headerRow = sheet.getRow(0);
        String[] expectedHeaders = {"Stack_Name","Topic_Name","Difficulty","Question_Type","Question_Stem","Option_A","Option_B","Option_C","Option_D","Option_E","Option_F","Correct_Answer"};
        if (headerRow == null) {
            throw new RuntimeException("Header row is missing. Please download and use the provided template.");
        }
        for (int i = 0; i < expectedHeaders.length; i++) {
            String actual = getCellValue(headerRow, i);
            if (!expectedHeaders[i].equalsIgnoreCase(actual.trim())) {
                throw new RuntimeException(
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
            if ("MSQ".equalsIgnoreCase(questionType)) {
                String[] answers = correct.split(",");
                if (answers.length < 2) {
                    errors.add("Row " + rowNum + ": MSQ requires at least 2 correct answers separated by comma (e.g. A,C).");
                    continue;
                }
                boolean allValid = true;
                for (String ans : answers) {
                    if (!validAnswers.contains(ans.trim())) {
                        errors.add("Row " + rowNum + ": Invalid answer '" + ans.trim() + "' in Correct_Answer. Must be A, B, C, D, E or F.");
                        allValid = false;
                        break;
                    }
                }
                if (!allValid) continue;
            } 
            else {
                if (!validAnswers.contains(correct)) {
                    errors.add("Row " + rowNum + ": Invalid Correct_Answer '" + correct + "'. Must be A, B, C, or D.");
                    continue;
                }
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
            SimilarityCheckResponse similarityResponse = aiService.checkSimilarity(
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
            results.add(MCQDTO.Response.fromEntity(mcqRepository.save(mcq)));
        }
    }

    // If nothing imported and there are errors, throw with details
    if (results.isEmpty() && !errors.isEmpty()) {
        throw new RuntimeException("No questions were imported. Errors found:\n" + String.join("\n", errors));
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
                throw new RuntimeException("MCQ requires exactly one correct answer from A to D.");
            }
            String answer = parsed.iterator().next();
            if (!List.of("A", "B", "C", "D").contains(answer)) {
                throw new RuntimeException("MCQ correct answer must be one of A, B, C, D.");
            }
            return answer;
        }

        if (parsed.size() < 2) {
            throw new RuntimeException("MSQ requires at least 2 correct answers.");
        }

        for (String answer : parsed) {
            String optionValue = optionMap.get(answer);
            if (optionValue == null || optionValue.isBlank()) {
                throw new RuntimeException("MSQ correct answer '" + answer + "' must map to a non-empty option.");
            }
        }

        return orderedOptions.stream()
                .filter(parsed::contains)
                .collect(Collectors.joining(","));
    }
}
