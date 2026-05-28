package com.smartquiz.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Entity
@Table(name = "technology_stacks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechnologyStack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stack_id")
    private Long stackId;

    @Column(name = "stack_name", nullable = false, unique = true)
    private String stackName;

    @OneToMany(mappedBy = "stack", cascade = CascadeType.ALL)
    private List<Topic> topics;
}
