package com.resqora.resqora_backend.service.ai;

// Anything that can answer a prompt. GeminiAiClient is the real one; tests use a fake.
public interface AiClient {
    boolean isConfigured();

    // systemInstruction holds our rules; prompt holds the task plus the user's data in delimited blocks.
    // Returns plain text.
    String generateText(String systemInstruction, String prompt);
}
