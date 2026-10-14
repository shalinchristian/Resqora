package com.resqora.resqora_backend.service.ai;

import com.resqora.resqora_backend.exception.AiUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Calls Google's Gemini REST API (generateContent) with plain HTTP using Spring's RestClient.
@Component
public class GeminiAiClient implements AiClient {
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public GeminiAiClient(
            @Value("${resqora.ai.gemini-api-key:}") String apiKey,
            @Value("${resqora.ai.gemini-model:gemini-3.5-flash-lite}") String model,
            @Value("${resqora.ai.gemini-base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        // Timeouts so a slow AI never hangs our request threads
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().baseUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl).requestFactory(requestFactory).build();
    }

    // tests / defaults
    public GeminiAiClient(String apiKey, String model) {
        this(apiKey, model, DEFAULT_BASE_URL);
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String generateText(String systemInstruction, String prompt) {
        // Rules go in systemInstruction (not in the user turn), so text inside the data can't replace them
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.3, "maxOutputTokens", 800));
        try {
            String response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(JSON.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            JsonNode text = JSON.readTree(response)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isString() || text.asString().isBlank()) {
                throw new AiUnavailableException();
            }
            return text.asString();
        } catch (AiUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // network error/timeout, 4xx/5xx from Google (e.g. 429 quota), or unreadable response
            throw new AiUnavailableException(exception);
        }
    }
}
