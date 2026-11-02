package com.resqora.resqora_backend.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// One line at startup saying whether the AI keys reached the app. Never logs a key or any part of it.
@Component
public class AiKeyStartupLogger {
    private static final Logger log = LoggerFactory.getLogger(AiKeyStartupLogger.class);

    private final String apiKey;
    private final String model;
    private final String groqApiKey;
    private final String groqModel;

    public AiKeyStartupLogger(
            @Value("${resqora.ai.gemini-api-key:}") String apiKey,
            @Value("${resqora.ai.gemini-model:gemini-3.5-flash-lite}") String model,
            @Value("${resqora.ai.groq-api-key:}") String groqApiKey,
            @Value("${resqora.ai.groq-model:" + GroqHttpClient.DEFAULT_MODEL + "}") String groqModel) {
        this.apiKey = apiKey;
        this.model = model;
        this.groqApiKey = groqApiKey;
        this.groqModel = groqModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logAiKeyStatus() {
        log.info(message());
        log.info(groqMessage());
    }

    String message() {
        return present(apiKey)
                ? "AI key found (Gemini model " + model + ")"
                : "AI key missing: AI summary and chat disabled";
    }

    String groqMessage() {
        return present(groqApiKey)
                ? "Groq key found (model " + groqModel + "): dashboard chat uses Groq"
                : "Groq key missing: dashboard chat falls back to Gemini";
    }

    private static boolean present(String key) {
        return key != null && !key.isBlank();
    }
}
