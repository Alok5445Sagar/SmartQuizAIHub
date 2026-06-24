package com.smartquiz.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "user_stack_mapping")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStackMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "enterprise_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "stack_id", nullable = false)
    private TechnologyStack stack;
}
