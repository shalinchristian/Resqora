package com.resqora.resqora_backend.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiKeyStartupLoggerTest {
    @Test
    void keyFoundNamesTheModelButNeverTheKey() {
        String message = new AiKeyStartupLogger("AQ.secret-key-value", "gemini-3.5-flash-lite", "", "openai/gpt-oss-20b").message();
        assertEquals("AI key found (Gemini model gemini-3.5-flash-lite)", message);
        assertFalse(message.contains("AQ."));
        assertFalse(message.contains("secret"));
    }

    @Test
    void keyMissing() {
        assertEquals("AI key missing: AI summary and chat disabled", new AiKeyStartupLogger(" ", "m", null, "g").message());
    }

    @Test
    void groqKeyFoundNamesTheModelButNeverTheKey() {
        String message = new AiKeyStartupLogger("", "m", "gsk_secret-value", "openai/gpt-oss-20b").groqMessage();
        assertEquals("Groq key found (model openai/gpt-oss-20b): dashboard chat uses Groq", message);
        assertFalse(message.contains("gsk_"));
    }

    @Test
    void groqKeyMissingFallsBackToGemini() {
        assertEquals("Groq key missing: dashboard chat falls back to Gemini",
                new AiKeyStartupLogger("k", "m", " ", "g").groqMessage());
    }
}
