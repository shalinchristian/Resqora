package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.ChatHistoryMessage;
import com.resqora.resqora_backend.entity.ChatHistoryMessage.Scope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistoryMessage, Long> {
    // null resumeId means IS NULL here (dashboard history), not "any resume"
    List<ChatHistoryMessage> findByUserIdAndScopeAndResumeIdOrderByCreatedAtAscIdAsc(
            Long userId, Scope scope, Long resumeId);

    void deleteByUserId(Long userId);
}
