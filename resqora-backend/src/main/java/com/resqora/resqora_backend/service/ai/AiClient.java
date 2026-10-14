package com.resqora.resqora_backend.service.ai;

// anything that can answer a prompt. GeminiAiClient is the real one, tests use a fake
public interface AiClient {
    boolean isConfigured();

    // systemInstruction has our rules, prompt has the task plus the user's data in delimited blocks.
    // returns plain text
    String generateText(String systemInstruction, String prompt);
}
