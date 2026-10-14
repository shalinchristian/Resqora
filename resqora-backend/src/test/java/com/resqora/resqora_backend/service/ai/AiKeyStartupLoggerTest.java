package com.resqora.resqora_backend.service.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AiKeyStartupLoggerTest {
    @Test
    void keyFoundNamesTheModelButNeverTheKey() {
        String message = new AiKeyStartupLogger("AQ.secret-key-value", "gemini-3.5-flash-lite").message();
        assertEquals("AI key found (Gemini model gemini-3.5-flash-lite)", message);
        assertFalse(message.contains("AQ."));
        assertFalse(message.contains("secret"));
    }

    @Test
    void keyMissing() {
        assertEquals("AI key missing: AI summary and chat disabled", new AiKeyStartupLogger(" ", "m").message());
    }
}
