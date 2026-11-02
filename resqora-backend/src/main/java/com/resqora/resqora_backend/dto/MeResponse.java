package com.resqora.resqora_backend.dto;

// aiEnabled = the user's AI privacy setting (false: summary and chat are refused, nothing goes to an AI)
public record MeResponse(Long id, String email, String name, boolean aiEnabled) {
}
