package com.resqora.resqora_backend.dto.ai;

// remainingToday / limit = messages left today and the daily limit of THIS chat (resume chat and
// dashboard chat have separate limits)
public record ChatResponse(String reply, int remainingToday, int limit) {
}
