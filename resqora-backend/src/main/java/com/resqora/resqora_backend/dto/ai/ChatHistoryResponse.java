package com.resqora.resqora_backend.dto.ai;

import java.time.OffsetDateTime;
import java.util.List;

public record ChatHistoryResponse(List<Message> messages) {
    public record Message(String role, String content, OffsetDateTime createdAt) { }
}
