package com.smartquiz.repository;

import com.smartquiz.entity.MCQ;
import com.smartquiz.entity.MCQ.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MCQRepository extends JpaRepository<MCQ, Long> {

    Page<MCQ> findByCreatorEnterpriseId(String enterpriseId, Pageable pageable);

    Page<MCQ> findByReviewerEnterpriseIdAndStatus(String enterpriseId, Status status, Pageable pageable);

    Page<MCQ> findAll(Pageable pageable);

    List<MCQ> findByCreatorEnterpriseId(String enterpriseId);

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
}