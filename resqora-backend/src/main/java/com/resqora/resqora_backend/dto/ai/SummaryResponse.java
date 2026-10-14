package com.resqora.resqora_backend.dto.ai;

// cached is true when we returned the saved summary and didn't call the AI
public record SummaryResponse(String summary, boolean cached) {
}
