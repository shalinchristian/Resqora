package com.resqora.resqora_backend.service.ai;

import com.resqora.resqora_backend.exception.AiUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Calls Groq's OpenAI-compatible Chat Completions API (POST /chat/completions) with plain HTTP.
// The key goes only in the Authorization header and is never logged.
@Component
public class GroqHttpClient implements GroqClient {
    public static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";
    public static final String DEFAULT_MODEL = "openai/gpt-oss-20b";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    @Autowired
    public GroqHttpClient(
            @Value("${resqora.ai.groq-api-key:}") String apiKey,
            @Value("${resqora.ai.groq-model:" + DEFAULT_MODEL + "}") String model,
            @Value("${resqora.ai.groq-base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        // same timeouts as Gemini: a slow provider must never hang our request threads
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String generateText(String systemInstruction, String prompt) {
        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(requestBody(model, systemInstruction, prompt)))
                    .retrieve()
                    .body(String.class);
            JsonNode text = JSON.readTree(response).path("choices").path(0).path("message").path("content");
            if (!text.isString() || text.asString().isBlank()) {
                throw new AiUnavailableException();
            }
            return text.asString();
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // network error/timeout, 4xx/5xx from Groq (e.g. 429 rate limit), or unreadable response
            throw new AiUnavailableException(exception);
        }
    }

    // Rules in the "system" message, user data (already in delimited blocks) in the "user" message.
    static Map<String, Object> requestBody(String model, String systemInstruction, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemInstruction),
                Map.of("role", "user", "content", prompt)));
        body.put("temperature", 0.3);
        // the reply limit; gpt-oss also spends part of it on hidden reasoning, hence low effort below
        body.put("max_completion_tokens", 1000);
        if (model.startsWith("openai/gpt-oss")) {
            body.put("reasoning_effort", "low");
            body.put("include_reasoning", false);
        }
        return body;
    }
}
