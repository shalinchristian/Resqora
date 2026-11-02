package com.resqora.resqora_backend.service.ai;

// provider for the dashboard chat. GroqHttpClient is the real one, tests use a fake.
// kept separate from AiClient (Gemini) so each can be swapped or faked on its own
public interface GroqClient {
    boolean isConfigured();

    String generateText(String systemInstruction, String prompt);
}
