package com.smartquiz.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Status of a background AI generation job.
 *
 * @param generatedSoFar questions produced by the model so far (updated after each question type)
 * @param generated      questions saved as drafts; final once status is SUCCEEDED
 * @param queuePosition  1-based place in the queue while QUEUED, otherwise null
 * @param questions      the saved drafts, only filled once SUCCEEDED
 */
public record AiGenerationJobResponse(
        String jobId,
        String status,
        int requested,
        Map<String, Integer> requestedByType,
        List<String> selectedTypes,
        int generatedSoFar,
        int generated,
        Long queuePosition,
        List<MCQDTO.Response> questions,
        String message,
        String error,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
