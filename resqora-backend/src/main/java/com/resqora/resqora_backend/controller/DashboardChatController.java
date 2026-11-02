package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ai.ChatResponse;
import com.resqora.resqora_backend.dto.ai.DashboardChatRequest;
import com.resqora.resqora_backend.service.ai.AiService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardChatController {
    private final AiService aiService;

    public DashboardChatController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody DashboardChatRequest request, Authentication authentication) {
        return aiService.dashboardChat(request, authentication);
    }
}
