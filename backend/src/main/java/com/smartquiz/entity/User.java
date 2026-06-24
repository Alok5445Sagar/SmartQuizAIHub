package com.smartquiz.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "enterprise_id", nullable = false, unique = true)
    private String enterpriseId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage = "en";

    public enum Role {
        SME, ADMIN, PARTICIPANT
    }
}
