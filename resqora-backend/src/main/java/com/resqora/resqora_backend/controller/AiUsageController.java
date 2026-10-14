package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.ai.AiUsageResponse;
import com.resqora.resqora_backend.service.ai.AiService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiUsageController {
    private final AiService aiService;

    public AiUsageController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/api/ai/usage")
    public AiUsageResponse usage(Authentication authentication) {
        return aiService.usage(authentication);
    }
}
