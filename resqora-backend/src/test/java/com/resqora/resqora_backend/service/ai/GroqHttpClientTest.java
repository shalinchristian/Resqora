package com.resqora.resqora_backend.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroqHttpClientTest {
    @Test
    void requestPutsRulesInTheSystemMessageAndHidesReasoning() {
        Map<String, Object> body = GroqHttpClient.requestBody("openai/gpt-oss-20b", "rules", "data");
        assertEquals("openai/gpt-oss-20b", body.get("model"));
        assertEquals(List.of(Map.of("role", "system", "content", "rules"), Map.of("role", "user", "content", "data")),
                body.get("messages"));
        assertEquals(1000, body.get("max_completion_tokens"));
        assertEquals("low", body.get("reasoning_effort"));
        assertEquals(false, body.get("include_reasoning"));
        // other models don't get gpt-oss-only fields
        assertFalse(GroqHttpClient.requestBody("llama-3.1-8b-instant", "r", "d").containsKey("reasoning_effort"));
    }

    @Test
    void configuredOnlyWithAKeyAndBlankSettingsUseDefaults() {
        assertFalse(new GroqHttpClient("", null, null).isConfigured());
        assertFalse(new GroqHttpClient("  ", "", "").isConfigured());
        assertTrue(new GroqHttpClient("k", null, null).isConfigured());
    }
}
