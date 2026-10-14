package com.resqora.resqora_backend.dto.ai;

public record AiUsageResponse(int usedToday, int limit, int remainingToday) {
}
