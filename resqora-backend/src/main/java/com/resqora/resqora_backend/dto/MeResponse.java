package com.resqora.resqora_backend.dto;

// aiEnabled is the AI privacy switch. if false, summary and chat are refused and nothing goes to an AI
public record MeResponse(Long id, String email, String name, boolean aiEnabled) {
}
