package com.medislot.repository;

import com.medislot.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    long countByConversationId(Long conversationId);

    Optional<ConversationMessage> findTopByConversationIdOrderByCreatedAtDesc(Long conversationId);
}
