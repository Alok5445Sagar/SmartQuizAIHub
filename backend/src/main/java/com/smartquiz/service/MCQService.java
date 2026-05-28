package com.smartquiz.service;

import com.smartquiz.dto.MCQDTO;
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

    public MCQDTO.Response createMCQ(String creatorId, MCQDTO.CreateRequest req) {
        User creator = userRepository.findByEnterpriseId(creatorId).orElseThrow();
        TechnologyStack stack = stackRepository.findById(req.getStackId()).orElseThrow();
        Topic topic = topicRepository.findById(req.getTopicId()).orElseThrow();

        MCQ mcq = new MCQ();
        mcq.setQuestionStem(req.getQuestionStem());
        mcq.setStack(stack);
        mcq.setTopic(topic);
        mcq.setDifficulty(MCQ.Difficulty.valueOf(req.getDifficulty()));
        mcq.setOptionA(req.getOptionA());
        mcq.setOptionB(req.getOptionB());
        mcq.setOptionC(req.getOptionC());
        mcq.setOptionD(req.getOptionD());
        mcq.setCorrectAnswer(req.getCorrectAnswer());
        mcq.setCreator(creator);
        mcq.setStatus(req.isSendForReview() ? MCQ.Status.READY_FOR_REVIEW : MCQ.Status.DRAFT);

        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
    }

    public MCQDTO.Response updateMCQ(String updaterId, Long questionId, MCQDTO.UpdateRequest req, boolean isAdmin) {
        MCQ mcq = mcqRepository.findById(questionId).orElseThrow();

        // Only creator can edit DRAFT, Admin can edit any non-draft
        if (!isAdmin && !mcq.getCreator().getEnterpriseId().equals(updaterId)) {
            throw new RuntimeException("Not authorized to edit this MCQ");
        }
        if (!isAdmin && mcq.getStatus() != MCQ.Status.DRAFT && mcq.getStatus() != MCQ.Status.REJECTED) {
            throw new RuntimeException("Can only edit DRAFT or REJECTED questions");
        }
        if (isAdmin && mcq.getStatus() != MCQ.Status.DRAFT
                && mcq.getStatus() != MCQ.Status.REJECTED
                && mcq.getStatus() != MCQ.Status.READY_FOR_REVIEW) {
            throw new RuntimeException("Admin can only edit DRAFT, REJECTED or READY_FOR_REVIEW questions");
        }

        TechnologyStack stack = stackRepository.findById(req.getStackId()).orElseThrow();
        Topic topic = topicRepository.findById(req.getTopicId()).orElseThrow();

        mcq.setQuestionStem(req.getQuestionStem());
        mcq.setStack(stack);
        mcq.setTopic(topic);
        mcq.setDifficulty(MCQ.Difficulty.valueOf(req.getDifficulty()));
        mcq.setOptionA(req.getOptionA());
        mcq.setOptionB(req.getOptionB());
        mcq.setOptionC(req.getOptionC());
        mcq.setOptionD(req.getOptionD());
        mcq.setCorrectAnswer(req.getCorrectAnswer());
        if (req.isSendForReview()) {
            mcq.setStatus(MCQ.Status.READY_FOR_REVIEW);
            mcq.setReviewerComments(null);
        }

        return MCQDTO.Response.fromEntity(mcqRepository.save(mcq));
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
        String[] expectedHeaders = {"Stack_Name","Topic_Name","Difficulty","Question_Stem","Option_A","Option_B","Option_C","Option_D","Correct_Answer"};
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
        List<String> validAnswers = List.of("A", "B", "C", "D");

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String stackName  = getCellValue(row, 0);
            String topicName  = getCellValue(row, 1);
            String difficulty = getCellValue(row, 2);
            String stem       = getCellValue(row, 3);
            String optA       = getCellValue(row, 4);
            String optB       = getCellValue(row, 5);
            String optC       = getCellValue(row, 6);
            String optD       = getCellValue(row, 7);
            String correct    = getCellValue(row, 8);

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
            if (!validAnswers.contains(correct)) {
                errors.add("Row " + rowNum + ": Invalid Correct_Answer '" + correct + "'. Must be A, B, C, or D.");
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

            MCQ mcq = new MCQ();
            mcq.setQuestionStem(stem);
            mcq.setStack(stackOpt.get());
            mcq.setTopic(topicOpt.get());
            mcq.setDifficulty(MCQ.Difficulty.valueOf(difficulty));
            mcq.setOptionA(optA);
            mcq.setOptionB(optB);
            mcq.setOptionC(optC);
            mcq.setOptionD(optD);
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
            String[] headers = {"Stack_Name", "Topic_Name", "Difficulty", "Question_Stem",
                    "Option_A", "Option_B", "Option_C", "Option_D", "Correct_Answer"};
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
            sample.createCell(0).setCellValue("Spring Boot");
            sample.createCell(1).setCellValue("Spring Boot Auto-Configuration");
            sample.createCell(2).setCellValue("Medium");
            sample.createCell(3).setCellValue("What annotation enables auto-configuration in Spring Boot?");
            sample.createCell(4).setCellValue("@Component");
            sample.createCell(5).setCellValue("@EnableAutoConfiguration");
            sample.createCell(6).setCellValue("@Configuration");
            sample.createCell(7).setCellValue("@SpringBootApplication");
            sample.createCell(8).setCellValue("B");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Template_MCQs.xlsx")
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
}
