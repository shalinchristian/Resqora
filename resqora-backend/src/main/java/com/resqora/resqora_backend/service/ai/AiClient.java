package com.resqora.resqora_backend.service.ai;

// Anything that can turn a prompt into a JSON answer. GeminiAiClient is the real one; tests use a fake.
public interface AiClient {
    boolean isConfigured();

    // Returns the model's answer: a JSON string like {"text": "...", "items": ["..."]}
    String generateJson(String prompt);
}
