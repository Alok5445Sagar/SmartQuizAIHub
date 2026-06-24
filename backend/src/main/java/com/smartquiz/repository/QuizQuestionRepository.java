package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
    List<QuizQuestion> findByQuizOrderByQuestionOrderAsc(Quiz quiz);

    @Query("""
        SELECT DISTINCT qq.question.questionId
        FROM QuizQuestion qq
        WHERE qq.quiz.topic.topicId = :topicId
    """)
    List<Long> findDistinctQuestionIdsByTopicId(@Param("topicId") Long topicId);

    @Query("""
        SELECT DISTINCT qq.question.questionId
        FROM QuizQuestion qq
        WHERE qq.question.questionId IN :questionIds
    """)
    List<Long> findDistinctUsedQuestionIdsIn(@Param("questionIds") List<Long> questionIds);
}
