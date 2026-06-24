package com.smartquiz.repository;

import com.smartquiz.entity.MCQTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MCQTranslationRepository extends JpaRepository<MCQTranslation, Long> {

    Optional<MCQTranslation> findByMcqQuestionIdAndLanguageCode(Long questionId, String languageCode);

    List<MCQTranslation> findByMcqQuestionIdOrderByLanguageCodeAsc(Long questionId);

    List<MCQTranslation> findByMcqQuestionIdInAndLanguageCode(List<Long> questionIds, String languageCode);
}
