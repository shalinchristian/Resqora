package com.resqora.resqora_backend.dto.ai;

// cached = true when the saved summary was returned without calling the AI
public record SummaryResponse(String summary, boolean cached) {
}
