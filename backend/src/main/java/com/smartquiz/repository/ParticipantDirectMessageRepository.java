package com.smartquiz.repository;

import com.smartquiz.entity.ParticipantDirectMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ParticipantDirectMessageRepository extends JpaRepository<ParticipantDirectMessage, Long> {

    @Query("SELECT m FROM ParticipantDirectMessage m " +
            "WHERE (m.senderEnterpriseId = :userA AND m.recipientEnterpriseId = :userB) " +
            "   OR (m.senderEnterpriseId = :userB AND m.recipientEnterpriseId = :userA) " +
            "ORDER BY m.createdAt DESC")
    List<ParticipantDirectMessage> findConversation(
            @Param("userA") String userA,
            @Param("userB") String userB,
            Pageable pageable
    );

    @Modifying
    @Query("UPDATE ParticipantDirectMessage m " +
            "SET m.readAt = :readAt " +
            "WHERE m.senderEnterpriseId = :senderEnterpriseId " +
            "  AND m.recipientEnterpriseId = :recipientEnterpriseId " +
            "  AND m.readAt IS NULL " +
            "  AND (:lastSeenMessageId IS NULL OR m.messageId <= :lastSeenMessageId)")
    int markMessagesAsRead(
            @Param("senderEnterpriseId") String senderEnterpriseId,
            @Param("recipientEnterpriseId") String recipientEnterpriseId,
            @Param("readAt") LocalDateTime readAt,
            @Param("lastSeenMessageId") Long lastSeenMessageId
    );
}
