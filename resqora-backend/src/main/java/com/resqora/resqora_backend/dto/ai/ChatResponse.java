package com.resqora.resqora_backend.dto.ai;

// remainingToday / limit = the user's chat messages left today and the daily total (resume + dashboard chat)
public record ChatResponse(String reply, int remainingToday, int limit) {
}
