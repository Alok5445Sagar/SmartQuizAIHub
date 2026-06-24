package com.smartquiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "participant_direct_messages",
    indexes = {
        @Index(name = "idx_pdm_conversation", columnList = "sender_enterprise_id,recipient_enterprise_id,created_at,message_id"),
        @Index(name = "idx_pdm_read_state", columnList = "sender_enterprise_id,recipient_enterprise_id,read_at,message_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDirectMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "sender_enterprise_id", nullable = false, length = 100)
    private String senderEnterpriseId;

    @Column(name = "recipient_enterprise_id", nullable = false, length = 100)
    private String recipientEnterpriseId;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_content_type", length = 120)
    private String attachmentContentType;

    @Column(name = "attachment_size_bytes")
    private Long attachmentSizeBytes;

    @Column(name = "attachment_storage_path", length = 512)
    private String attachmentStoragePath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (deliveredAt == null) {
            deliveredAt = createdAt;
        }
    }
}
