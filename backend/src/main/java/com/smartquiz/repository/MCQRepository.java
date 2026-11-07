package com.smartquiz.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.MCQ.Status;
import java.util.Collection;

@Repository
public interface MCQRepository extends JpaRepository<MCQ, Long> {

    Page<MCQ> findByCreatorEnterpriseId(String enterpriseId, Pageable pageable);

    Page<MCQ> findByReviewerEnterpriseIdAndStatus(String enterpriseId, Status status, Pageable pageable);

    Page<MCQ> findAll(Pageable pageable);

    List<MCQ> findByCreatorEnterpriseId(String enterpriseId);

    List<MCQ> findByTopicTopicIdAndStatusOrderByCreatedAtDesc(Long topicId, MCQ.Status status);

    @Query("""
        SELECT m FROM MCQ m
        WHERE m.status = :status
        AND (:stackIdsEmpty = true OR m.stack.stackId IN :stackIds)
        AND (:topicIdsEmpty = true OR m.topic.topicId IN :topicIds)
        ORDER BY m.createdAt DESC
    """)
    List<MCQ> findApprovedByStackIdsAndTopicIds(
        @Param("status") MCQ.Status status,
        @Param("stackIds") Collection<Long> stackIds,
        @Param("stackIdsEmpty") boolean stackIdsEmpty,
        @Param("topicIds") Collection<Long> topicIds,
        @Param("topicIdsEmpty") boolean topicIdsEmpty
    );

    @Query("""
        SELECT m FROM MCQ m
        WHERE m.creator.enterpriseId = :enterpriseId
        AND (:stackId IS NULL OR m.stack.stackId = :stackId)
        AND (:topicId IS NULL OR m.topic.topicId = :topicId)
        AND (:difficulty IS NULL OR m.difficulty = :difficulty)
        AND (:status IS NULL OR m.status = :status)
    """)
    Page<MCQ> findByCreatorWithFilters(
        @Param("enterpriseId") String enterpriseId,
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId,
        @Param("difficulty") MCQ.Difficulty difficulty,
        @Param("status") MCQ.Status status,
        Pageable pageable
    );

    /** Rows of [status, count] for one creator, with the same optional filters as the My Questions list. */
    @Query("""
        SELECT m.status, COUNT(m) FROM MCQ m
        WHERE m.creator.enterpriseId = :enterpriseId
        AND (:stackId IS NULL OR m.stack.stackId = :stackId)
        AND (:topicId IS NULL OR m.topic.topicId = :topicId)
        AND (:difficulty IS NULL OR m.difficulty = :difficulty)
        GROUP BY m.status
    """)
    List<Object[]> countByStatusForCreator(
        @Param("enterpriseId") String enterpriseId,
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId,
        @Param("difficulty") MCQ.Difficulty difficulty
    );

    @Query("""
        SELECT m FROM MCQ m
        WHERE (:stackId IS NULL OR m.stack.stackId = :stackId)
        AND (:topicId IS NULL OR m.topic.topicId = :topicId)
        AND (:difficulty IS NULL OR m.difficulty = :difficulty)
        AND (:status IS NULL OR m.status = :status)
        AND (:search IS NULL OR LOWER(m.questionStem) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.stack.stackName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.creator.enterpriseId) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Page<MCQ> findAllWithFilters(
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId,
        @Param("difficulty") MCQ.Difficulty difficulty,
        @Param("status") MCQ.Status status,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("""
        SELECT
            COUNT(m),
            SUM(CASE WHEN m.status = com.smartquiz.entity.MCQ$Status.READY_FOR_REVIEW THEN 1 ELSE 0 END),
            SUM(CASE WHEN m.status = com.smartquiz.entity.MCQ$Status.UNDER_REVIEW THEN 1 ELSE 0 END),
            SUM(CASE WHEN m.status = com.smartquiz.entity.MCQ$Status.APPROVED THEN 1 ELSE 0 END)
        FROM MCQ m
        WHERE (:stackId IS NULL OR m.stack.stackId = :stackId)
        AND (:topicId IS NULL OR m.topic.topicId = :topicId)
        AND (:difficulty IS NULL OR m.difficulty = :difficulty)
        AND (:search IS NULL OR LOWER(m.questionStem) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.stack.stackName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.creator.enterpriseId) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    Object[] getQuestionBankStats(
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId,
        @Param("difficulty") MCQ.Difficulty difficulty,
        @Param("search") String search
    );

    @Query("""
        SELECT COUNT(m)
        FROM MCQ m
        WHERE (:stackId IS NULL OR m.stack.stackId = :stackId)
        AND (:topicId IS NULL OR m.topic.topicId = :topicId)
        AND (:difficulty IS NULL OR m.difficulty = :difficulty)
        AND (:search IS NULL OR LOWER(m.questionStem) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.stack.stackName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.creator.enterpriseId) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    long countAllWithFilters(
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId,
        @Param("difficulty") MCQ.Difficulty difficulty,
        @Param("search") String search
    );

    @Query("""
        SELECT COUNT(m)
        FROM MCQ m
        WHERE m.status = :status
        AND (:stackId IS NULL OR m.stack.stackId = :stackId)
        AND (:topicId IS NULL OR m.topic.topicId = :topicId)
        AND (:difficulty IS NULL OR m.difficulty = :difficulty)
        AND (:search IS NULL OR LOWER(m.questionStem) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.stack.stackName) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(m.creator.enterpriseId) LIKE LOWER(CONCAT('%', :search, '%')))
    """)
    long countByStatusWithFilters(
        @Param("status") MCQ.Status status,
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId,
        @Param("difficulty") MCQ.Difficulty difficulty,
        @Param("search") String search
    );

    @Query("""
        SELECT COUNT(m) > 0 FROM MCQ m
        WHERE LOWER(TRIM(m.questionStem)) = LOWER(TRIM(:questionStem))
        AND m.stack.stackId = :stackId
        AND m.topic.topicId = :topicId
    """)
    boolean existsByQuestionStemAndStackAndTopic(
        @Param("questionStem") String questionStem,
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId
    );

    @Query("""
        SELECT COUNT(m) > 0 FROM MCQ m
        WHERE LOWER(TRIM(m.questionStem)) = LOWER(TRIM(:questionStem))
        AND m.stack.stackId = :stackId
        AND m.topic.topicId = :topicId
        AND m.questionId != :excludeId
    """)
    boolean existsByQuestionStemAndStackAndTopicExcluding(
        @Param("questionStem") String questionStem,
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId,
        @Param("excludeId") Long excludeId
    );

    @Query("""
    SELECT COUNT(m) > 0 FROM MCQ m
    WHERE LOWER(TRIM(m.questionStem)) = LOWER(TRIM(:questionStem))
    AND m.stack.stackId = :stackId
    AND m.topic.topicId = :topicId
    AND m.status != com.smartquiz.entity.MCQ$Status.DRAFT
    """)
    boolean existsByQuestionStemAndStackAndTopicNonDraft(
        @Param("questionStem") String questionStem,
        @Param("stackId") Long stackId,
        @Param("topicId") Long topicId
    );

    @Query("""
        SELECT m FROM MCQ m
        WHERE m.stack.stackId = :stackId
        AND m.topic.topicId = :topicId
        AND (:excludeId IS NULL OR m.questionId != :excludeId)
        AND m.status IN :statuses
    """)
    List<MCQ> findNonDraftByStackAndTopic(@Param("stackId") Long stackId,
                                           @Param("topicId") Long topicId,
                                           @Param("excludeId") Long excludeId,
                                           @Param("statuses") Collection<MCQ.Status> statuses);
}