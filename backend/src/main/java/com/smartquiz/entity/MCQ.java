package com.smartquiz.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "mcqs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MCQ {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "question_stem", nullable = false, columnDefinition = "TEXT")
    private String questionStem;

    @Column(name = "question_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionType questionType = QuestionType.MCQ;

    @Column(name = "option_a", nullable = false)
    private String optionA;

    @Column(name = "option_b", nullable = false)
    private String optionB;

    @Column(name = "option_c", nullable = false)
    private String optionC;

    @Column(name = "option_d", nullable = false)
    private String optionD;

    @Column(name = "option_e")
    private String optionE;

    @Column(name = "option_f")
    private String optionF;

    // For MCQ: single letter e.g. "A"
    // For MSQ: comma-separated e.g. "A,C" or "B,D,E"
    @Column(name = "correct_answer", nullable = false)
    private String correctAnswer;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @ManyToOne
    @JoinColumn(name = "stack_id", nullable = false)
    private TechnologyStack stack;

    @ManyToOne
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne
    @JoinColumn(name = "creator_enterprise_id", nullable = false)
    private User creator;

    @ManyToOne
    @JoinColumn(name = "reviewer_enterprise_id")
    private User reviewer;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status = Status.DRAFT;

    @Column(name = "reviewer_comments", columnDefinition = "TEXT")
    private String reviewerComments;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Difficulty {
        Easy, Medium, Hard
    }

    public enum Status {
        DRAFT, READY_FOR_REVIEW, UNDER_REVIEW, APPROVED, REJECTED
    }

    public enum QuestionType {
        MCQ, MSQ
    }
}
