package com.smartquiz.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smartquiz.entity.ChatMessage;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserEnterpriseIdOrderByCreatedAtDesc(String enterpriseId, Pageable pageable);

    List<ChatMessage> findByUserEnterpriseIdAndSessionIdOrderByCreatedAtDesc(String enterpriseId, String sessionId, Pageable pageable);

    @Query(value = "SELECT session_id FROM chat_messages WHERE enterprise_id = :enterpriseId AND session_id IS NOT NULL GROUP BY session_id ORDER BY MAX(created_at) DESC", nativeQuery = true)
    List<String> findSessionIdsByRecentActivity(@Param("enterpriseId") String enterpriseId);

    @Modifying
    void deleteByUserEnterpriseIdAndSessionIdIn(String enterpriseId, List<String> sessionIds);
}
