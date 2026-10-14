package com.resqora.resqora_backend.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// One line at startup saying whether the Gemini key reached the app. Never logs the key or any part of it.
@Component
public class AiKeyStartupLogger {
    private static final Logger log = LoggerFactory.getLogger(AiKeyStartupLogger.class);

    private final String apiKey;
    private final String model;

    public AiKeyStartupLogger(
            @Value("${resqora.ai.gemini-api-key:}") String apiKey,
            @Value("${resqora.ai.gemini-model:gemini-3.5-flash-lite}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logAiKeyStatus() {
        log.info(message());
    }

    String message() {
        return apiKey != null && !apiKey.isBlank()
                ? "AI key found (Gemini model " + model + ")"
                : "AI key missing: AI summary and chat disabled";
    }
}
