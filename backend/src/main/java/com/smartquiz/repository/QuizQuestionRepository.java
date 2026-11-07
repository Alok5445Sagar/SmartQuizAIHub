package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {
    @EntityGraph(attributePaths = {"question", "question.stack", "question.topic", "question.topic.stack",
            "question.creator", "question.reviewer"})
    List<QuizQuestion> findByQuizOrderByQuestionOrderAsc(Quiz quiz);

    long countByQuiz(Quiz quiz);

    /** Rows of [quizId, questionCount] for the given quizzes. */
    @Query("SELECT qq.quiz.quizId, COUNT(qq) FROM QuizQuestion qq WHERE qq.quiz IN :quizzes GROUP BY qq.quiz.quizId")
    List<Object[]> countByQuizIn(@Param("quizzes") java.util.Collection<Quiz> quizzes);

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
