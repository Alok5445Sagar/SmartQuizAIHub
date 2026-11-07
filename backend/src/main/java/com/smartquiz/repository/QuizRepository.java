package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByQuizCode(String quizCode);

    @EntityGraph(attributePaths = {"stack", "topic", "topic.stack", "createdBy"})
    Page<Quiz> findAllByOrderByPublishedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"stack", "topic", "topic.stack", "createdBy"})
    List<Quiz> findByStatusOrderByPublishedAtDesc(Quiz.Status status);

    /** Rows of [topicId, stackName, topicName, quizCount], one per topic that has hosted quizzes. */
    @Query("""
        SELECT t.topicId, s.stackName, t.topicName, COUNT(q)
        FROM Quiz q JOIN q.topic t LEFT JOIN q.stack s
        GROUP BY t.topicId, s.stackName, t.topicName
    """)
    List<Object[]> countByTopic();
}