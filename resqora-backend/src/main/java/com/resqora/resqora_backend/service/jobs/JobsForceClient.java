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

/**
 * public API, no key, no retries. two pages give up to 40 candidates for five picks.
 * search summaries hardly ever mention skills, so details() reads the full listing for skill matching.
 */
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
                    if (query.fresher()) uri.queryParam("fresher", true);
                    if (query.internship()) uri.queryParam("job_type", "internship");
                    if (query.experienceYears() != null) uri.queryParam("experience_years", query.experienceYears());
                    return uri.build(Map.of("q", query.text()));
                }).retrieve().body(String.class);
            } catch (RuntimeException failure) {
                if (failure instanceof RestClientResponseException http && http.getStatusCode().value() == 429) cooldown(http);
                // if the second page fails, keep the twenty we already got
                if (jobs.isEmpty()) throw failure;
                break;
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
                // show the source exactly as given, don't swap in hiring.name/employer
                jobs.add(new JobsForcePosting(text(job.path("title")), text(job.path("hiring").path("display")),
                        text(location.path("city")), places.isEmpty() ? null : String.join(", ", places),
                        Objects.requireNonNullElse(text(job.path("summary")), ""), url, apply, posted,
                        validId(text(job.path("id")))));
            }
            int next = response.path("next_page").asInt(0);
            if (!response.path("has_more").asBoolean(false) || next <= page || next > 10) break;
            page = next;
        }
        return List.copyOf(jobs);
    }
    /**
     * the full listing's description, responsibilities, qualifications and skills as one text. only used
     * for local skill matching (never stored or returned). uses the same request budget, throws on failure.
     */
    public String details(String id) {
        if (validId(id) == null) return null;
        reserveRequest();
        String body;
        try {
            body = http.get().uri("/jobs/{id}", id).retrieve().body(String.class);
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 429) cooldown(failure);
            throw failure;
        }
        JsonNode job = JSON.readTree(body);
        List<String> parts = new ArrayList<>();
        String description = text(job.path("description"));
        if (description != null) parts.add(description);
        for (String field : List.of("responsibilities", "qualifications", "skills", "skills_preferred")) {
            for (JsonNode item : job.path(field)) {
                String value = text(item);
                if (value != null && !value.isBlank()) parts.add(value);
            }
        }
        return String.join(" ", parts);
    }
    static String validId(String id) {
        return id != null && id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,199}") ? id : null;
    }
    private synchronized void reserveRequest() {
        Instant now = clock.instant();
        while (!requests.isEmpty() && !requests.peekFirst().isAfter(now.minusSeconds(60))) requests.removeFirst();
        // under their 120/min limit, with some room left for anything else on our IP
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
