package com.resqora.resqora_backend.dto.ai;

// remainingToday and limit are for this chat only (resume chat and dashboard chat have separate limits)
public record ChatResponse(String reply, int remainingToday, int limit) {
}
