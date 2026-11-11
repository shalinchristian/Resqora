package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ai.ChatHistoryResponse;
import com.resqora.resqora_backend.dto.ai.ChatResponse;
import com.resqora.resqora_backend.dto.ai.DashboardChatRequest;
import com.resqora.resqora_backend.service.ai.AiService;
import com.resqora.resqora_backend.service.ai.ChatHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardChatController {
    private final AiService aiService;
    private final ChatHistoryService chatHistoryService;

    public DashboardChatController(AiService aiService, ChatHistoryService chatHistoryService) {
        this.aiService = aiService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody DashboardChatRequest request, Authentication authentication) {
        return aiService.dashboardChat(request, authentication);
    }

    @GetMapping("/api/chat/history")
    public ChatHistoryResponse history(Authentication authentication) {
        return chatHistoryService.history(null, authentication);
    }

    @DeleteMapping("/api/chat/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(Authentication authentication) {
        chatHistoryService.clear(null, authentication);
    }
}
