package com.smartquiz.repository;

import com.smartquiz.entity.Quiz;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long> {
    Optional<Quiz> findByQuizCode(String quizCode);
    Page<Quiz> findAllByOrderByPublishedAtDesc(Pageable pageable);
    List<Quiz> findByStatusOrderByPublishedAtDesc(Quiz.Status status);
}
