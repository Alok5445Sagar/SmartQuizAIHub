package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import com.smartquiz.entity.QuizAttempt;
import com.smartquiz.entity.QuizAttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {
	List<QuizAttemptAnswer> findByAttemptQuizIn(Collection<Quiz> quizzes);
	List<QuizAttemptAnswer> findByAttemptIn(Collection<QuizAttempt> attempts);
}
