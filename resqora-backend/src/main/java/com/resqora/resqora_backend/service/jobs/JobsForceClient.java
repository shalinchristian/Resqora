package com.resqora.resqora_backend.service.jobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

/** Keyless public API; no retries. Two pages supply up to 40 candidates for five picks. */
@Component
public class JobsForceClient {
    public static final String DEFAULT_BASE_URL = "https://jobsforceindia.com/api/public/v1";
    static final int PER_PAGE = 20, MAX_PAGES = 2;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final RestClient http;
    private final Clock clock;
    private final Deque<Instant> requests = new ArrayDeque<>();
    private Instant blockedUntil = Instant.MIN;

    @Autowired
    public JobsForceClient(@Value("${resqora.jobs.jobsforce-base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this(baseUrl, Clock.systemUTC());
    }
    JobsForceClient(String baseUrl, Clock clock) {
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    public List<JobsForcePosting> search(JobsForceQuery query) {
        List<JobsForcePosting> jobs = new ArrayList<>();
        int page = 1;
        for (int fetched = 0; fetched < MAX_PAGES; fetched++) {
            reserveRequest();
            final int current = page;
            String body;
            try {
                body = http.get().uri(uri -> {
                    uri.path("/jobs").queryParam("q", "{q}").queryParam("per_page", PER_PAGE).queryParam("page", current);
                    if (query.city() != null) uri.queryParam("city", "{city}");
                    if (query.fresher()) uri.queryParam("fresher", true);
                    if (query.internship()) uri.queryParam("job_type", "internship");
                    if (query.experienceYears() != null) uri.queryParam("experience_years", query.experienceYears());
                    Map<String, Object> values = new HashMap<>();
                    values.put("q", query.text());
                    if (query.city() != null) values.put("city", query.city());
                    return uri.build(values);
                }).retrieve().body(String.class);
            } catch (RestClientResponseException failure) {
                if (failure.getStatusCode().value() == 429) cooldown(failure);
                throw failure;
            }
            JsonNode response = JSON.readTree(body);
            if (!response.path("results").isArray()) throw new IllegalStateException("Invalid JobsForce response");
            for (JsonNode job : response.path("results")) {
                String url = validUrl(text(job.path("url"))), apply = validUrl(text(job.path("apply_url")));
                if (url == null && apply == null) continue;
                JsonNode location = job.path("location");
                List<String> places = new ArrayList<>();
                for (String part : List.of("city", "state", "country")) {
                    String value = text(location.path(part));
                    if (value != null && !value.isBlank()) places.add(value);
                }
                if (location.path("remote").asBoolean(false)) places.add("Remote");
                LocalDate posted = null;
                try { posted = LocalDate.parse(Objects.requireNonNullElse(text(job.path("posted_date")), "")); }
                catch (DateTimeException ignored) { }
                // Exact source attribution. Never substitute hiring.name/employer for display.
                jobs.add(new JobsForcePosting(text(job.path("title")), text(job.path("hiring").path("display")),
                        text(location.path("city")), places.isEmpty() ? null : String.join(", ", places),
                        Objects.requireNonNullElse(text(job.path("summary")), ""), url, apply, posted));
            }
            int next = response.path("next_page").asInt(0);
            if (!response.path("has_more").asBoolean(false) || next <= page || next > 10) break;
            page = next;
        }
        return List.copyOf(jobs);
    }
    private synchronized void reserveRequest() {
        Instant now = clock.instant();
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(now.minusSeconds(60))) requests.removeFirst();
        // Below the provider's 120/min ceiling, leaving headroom for other clients sharing our IP.
        if (now.isBefore(blockedUntil) || requests.size() >= 100) throw new IllegalStateException("JobsForce request budget exhausted");
        requests.addLast(now);
    }
    private synchronized void cooldown(RestClientResponseException failure) {
        long seconds = 60;
        try {
            String retry = failure.getResponseHeaders() == null ? null : failure.getResponseHeaders().getFirst("Retry-After");
            if (retry != null) seconds = Math.max(60, Long.parseLong(retry));
        } catch (NumberFormatException ignored) { }
        blockedUntil = clock.instant().plusSeconds(Math.min(seconds, 86400));
    }
    static String validUrl(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(value.strip());
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null ? value.strip() : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }
    private static String text(JsonNode node) { return node.isString() ? node.asString() : null; }
}
