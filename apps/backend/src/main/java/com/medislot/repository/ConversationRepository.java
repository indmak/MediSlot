package com.medislot.repository;

import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationScope;
import com.medislot.entity.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByAppointmentIdAndScope(Long appointmentId, ConversationScope scope);

    List<Conversation> findByAppointmentId(Long appointmentId);

    /** 已关闭但尚未入知识库的会话（用于自动生成问诊记录 MD）。 */
    List<Conversation> findByScopeAndStatusAndKbDocumentIdIsNull(ConversationScope scope, ConversationStatus status);
}
