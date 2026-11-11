package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.ChatHistoryMessage;
import com.resqora.resqora_backend.entity.ChatHistoryMessage.Scope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatHistoryRepository extends JpaRepository<ChatHistoryMessage, Long> {
    // A null resumeId becomes IS NULL for dashboard history, not a wildcard.
    List<ChatHistoryMessage> findByUserIdAndScopeAndResumeIdOrderByCreatedAtAscIdAsc(
            Long userId, Scope scope, Long resumeId);

    void deleteByUserId(Long userId);
}
