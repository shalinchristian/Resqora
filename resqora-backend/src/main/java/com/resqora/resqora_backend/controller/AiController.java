package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ai.ChatRequest;
import com.resqora.resqora_backend.dto.ai.ChatResponse;
import com.resqora.resqora_backend.dto.ai.SummaryResponse;
import com.resqora.resqora_backend.service.ai.AiService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
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
}
