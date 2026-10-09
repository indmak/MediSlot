package com.medislot.repository;

import com.medislot.entity.Conversation;
import com.medislot.entity.ConversationScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByAppointmentIdAndScope(Long appointmentId, ConversationScope scope);

    List<Conversation> findByAppointmentId(Long appointmentId);
}
