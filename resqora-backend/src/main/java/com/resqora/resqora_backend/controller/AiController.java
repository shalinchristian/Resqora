package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ai.ChatHistoryResponse;
import com.resqora.resqora_backend.dto.ai.ChatRequest;
import com.resqora.resqora_backend.dto.ai.ChatResponse;
import com.resqora.resqora_backend.dto.ai.SummaryResponse;
import com.resqora.resqora_backend.service.ai.AiService;
import com.resqora.resqora_backend.service.ai.ChatHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
public class AiController {
    private final AiService aiService;
    private final ChatHistoryService chatHistoryService;

    public AiController(AiService aiService, ChatHistoryService chatHistoryService) {
        this.aiService = aiService;
        this.chatHistoryService = chatHistoryService;
    }

    @GetMapping("/{resumeId}/summary")
    public SummaryResponse summary(@PathVariable Long resumeId, Authentication authentication) {
        return aiService.summary(resumeId, authentication);
    }

    @PostMapping("/{resumeId}/chat")
    public ChatResponse chat(
            @PathVariable Long resumeId,
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        return aiService.chat(resumeId, request, authentication);
    }

    @GetMapping("/{resumeId}/chat/history")
    public ChatHistoryResponse history(@PathVariable Long resumeId, Authentication authentication) {
        return chatHistoryService.history(resumeId, authentication);
    }

    @DeleteMapping("/{resumeId}/chat/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(@PathVariable Long resumeId, Authentication authentication) {
        chatHistoryService.clear(resumeId, authentication);
    }
}
