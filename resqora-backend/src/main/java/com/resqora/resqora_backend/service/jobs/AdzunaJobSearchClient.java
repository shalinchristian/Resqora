package com.resqora.resqora_backend.service.jobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// Calls Adzuna's job search API:
// GET https://api.adzuna.com/v1/api/jobs/{country}/search/1?app_id=..&app_key=..&what_or=..&results_per_page=..
@Component
public class AdzunaJobSearchClient implements JobSearchClient {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String appId;
    private final String appKey;
    private final String country;
    private final RestClient restClient;

    public AdzunaJobSearchClient(
            @Value("${resqora.jobs.adzuna-app-id:}") String appId,
            @Value("${resqora.jobs.adzuna-app-key:}") String appKey,
            @Value("${resqora.jobs.country:in}") String country) {
        this.appId = appId;
        this.appKey = appKey;
        this.country = country;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.adzuna.com/v1/api")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
    }

    @Override
    public List<JobPosting> search(List<String> keywords, int maxResults) {
        String response = restClient.get()
                .uri(uri -> uri.path("/jobs/{country}/search/1")
                        .queryParam("app_id", appId)
                        .queryParam("app_key", appKey)
                        .queryParam("what_or", String.join(" ", keywords))
                        .queryParam("results_per_page", maxResults)
                        .queryParam("content-type", "application/json")
                        .build(country))
                .retrieve()
                .body(String.class);

        List<JobPosting> jobs = new ArrayList<>();
        for (JsonNode result : JSON.readTree(response).path("results")) {
            jobs.add(new JobPosting(
                    text(result.path("title")),
                    text(result.path("company").path("display_name")),
                    text(result.path("location").path("display_name")),
                    text(result.path("description")),
                    text(result.path("redirect_url"))));
        }
        return jobs;
    }

    private static String text(JsonNode node) {
        return node.isString() ? node.asString() : null;
    }
}
