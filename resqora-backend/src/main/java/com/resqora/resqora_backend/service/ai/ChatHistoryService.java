package com.resqora.resqora_backend.service.ai;

import com.resqora.resqora_backend.dto.ai.ChatHistoryResponse;
import com.resqora.resqora_backend.entity.ChatHistoryMessage;
import com.resqora.resqora_backend.entity.ChatHistoryMessage.Scope;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.AuthenticatedUserNotFoundException;
import com.resqora.resqora_backend.repository.ChatHistoryRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.ResumeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class ChatHistoryService {
    public static final int MAX_MESSAGES = 20;
    private final ChatHistoryRepository repository;
    private final UserRepository users;
    private final ResumeService resumes;
    private final ZoneId zone;

    public ChatHistoryService(ChatHistoryRepository repository, UserRepository users, ResumeService resumes,
                              @Value("${resqora.timezone:Asia/Kolkata}") String timezone) {
        this.repository = repository;
        this.users = users;
        this.resumes = resumes;
        this.zone = ZoneId.of(timezone);
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse history(Long resumeId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        if (resumeId != null) resumes.findOwnedResume(resumeId, authentication);
        List<ChatHistoryMessage> messages = messages(userId, resumeId);
        return new ChatHistoryResponse(messages.stream().skip(Math.max(0, messages.size() - MAX_MESSAGES))
                .map(m -> new ChatHistoryResponse.Message(m.getRole(), m.getContent(),
                        m.getCreatedAt().atZone(zone).toOffsetDateTime())).toList());
    }

    @Transactional
    public void clear(Long resumeId, Authentication authentication) {
        lockActiveUser((Long) authentication.getPrincipal());
        if (resumeId != null) resumes.findOwnedResume(resumeId, authentication);
        repository.deleteAllInBatch(messages((Long) authentication.getPrincipal(), resumeId));
    }

    // Called only AFTER a nonblank AI reply. The pair and trim commit atomically; failures/limits
    // never leave an orphan user turn. No network request or daily usage accounting happens here.
    @Transactional
    public void saveReply(Long resumeId, String message, String reply, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        User user = lockActiveUser(userId);
        Resume resume = resumeId == null ? null : resumes.findOwnedResume(resumeId, authentication);
        Scope scope = resumeId == null ? Scope.DASHBOARD : Scope.RESUME;
        Instant now = Instant.now();
        repository.saveAllAndFlush(List.of(
                new ChatHistoryMessage(user, resume, scope, "user", message.trim(), now),
                new ChatHistoryMessage(user, resume, scope, "assistant", reply, now)));
        List<ChatHistoryMessage> messages = messages(userId, resumeId);
        if (messages.size() > MAX_MESSAGES) {
            repository.deleteAllInBatch(messages.subList(0, messages.size() - MAX_MESSAGES));
        }
    }

    // Same row lock as account deletion. Also serialises concurrent replies and Clear across servers.
    private User lockActiveUser(Long userId) {
        return users.findByIdForUpdate(userId).filter(u -> !u.isDeleted())
                .orElseThrow(AuthenticatedUserNotFoundException::new);
    }

    private List<ChatHistoryMessage> messages(Long userId, Long resumeId) {
        return repository.findByUserIdAndScopeAndResumeIdOrderByCreatedAtAscIdAsc(
                userId, resumeId == null ? Scope.DASHBOARD : Scope.RESUME, resumeId);
    }
}
