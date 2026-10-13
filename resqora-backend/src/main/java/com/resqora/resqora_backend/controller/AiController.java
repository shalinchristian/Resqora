package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ai.AiRequest;
import com.resqora.resqora_backend.dto.ai.AiResponse;
import com.resqora.resqora_backend.service.ai.AiService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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

    @PostMapping("/{resumeId}/ai")
    public AiResponse runAiAction(
            @PathVariable Long resumeId,
            @Valid @RequestBody AiRequest request,
            Authentication authentication) {
        return aiService.run(resumeId, request, authentication);
    }
}
