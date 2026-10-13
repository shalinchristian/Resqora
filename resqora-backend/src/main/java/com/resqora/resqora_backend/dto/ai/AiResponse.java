package com.resqora.resqora_backend.dto.ai;

import java.util.List;

public record AiResponse(AiAction action, String text, List<String> items, int remainingToday) {
}
