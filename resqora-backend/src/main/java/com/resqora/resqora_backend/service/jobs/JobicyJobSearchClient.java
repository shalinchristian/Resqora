package com.resqora.resqora_backend.service.jobs;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Calls Jobicy's free public remote-jobs API (no key needed):
// GET https://jobicy.com/api/v2/remote-jobs?count=50&geo=apac&tag=java
// Jobicy's terms: credit Jobicy as the source and link every job to its original Jobicy URL.
@Component
public class JobicyJobSearchClient implements JobSearchClient {
    public static final String DEFAULT_BASE_URL = "https://jobicy.com/api/v2";
    public static final String SOURCE = "Jobicy";
    static final Duration CACHE_TTL = Duration.ofMinutes(30);
    static final int FETCH_COUNT = 50;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record CachedJobs(List<JobPosting> jobs, Instant fetchedAt) { }

    private final RestClient restClient;
    private final Clock clock;
    // tag ("" = no tag) -> last Jobicy answer, so we don't call Jobicy on every request
    private final Map<String, CachedJobs> cache = new ConcurrentHashMap<>();

    @Autowired
    public JobicyJobSearchClient(@Value("${resqora.jobs.jobicy-base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this(baseUrl, Clock.systemUTC());
    }

    JobicyJobSearchClient(String baseUrl, Clock clock) {
        this.clock = clock;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return true; // public API, nothing to configure
    }

    // Jobicy takes one tag, so search by the first keyword; fall back to all APAC jobs if that tag finds nothing.
    // The service ranks the results by skill match.
    @Override
    public List<JobPosting> search(List<String> keywords, int maxResults) {
        String tag = keywords.isEmpty() ? "" : keywords.get(0).toLowerCase(Locale.ROOT);
        List<JobPosting> jobs = fetch(tag);
        if (jobs.isEmpty() && !tag.isEmpty()) {
            jobs = fetch("");
        }
        return jobs;
    }

    private List<JobPosting> fetch(String tag) {
        CachedJobs cached = cache.get(tag);
        Instant now = clock.instant();
        if (cached != null && cached.fetchedAt().isAfter(now.minus(CACHE_TTL))) {
            return cached.jobs();
        }
        String response = restClient.get()
                .uri(uri -> {
                    uri.path("/remote-jobs").queryParam("count", FETCH_COUNT).queryParam("geo", "apac");
                    if (!tag.isEmpty()) {
                        uri.queryParam("tag", tag);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(String.class);

        List<JobPosting> jobs = new ArrayList<>();
        for (JsonNode job : JSON.readTree(response).path("jobs")) {
            String url = text(job.path("url"));
            if (url == null) {
                continue; // Jobicy requires linking to the original job, so skip jobs without one
            }
            jobs.add(new JobPosting(
                    text(job.path("jobTitle")),
                    text(job.path("companyName")),
                    text(job.path("jobGeo")),
                    plainText(text(job.path("jobDescription"))),
                    url));
        }
        cache.put(tag, new CachedJobs(List.copyOf(jobs), now));
        return jobs;
    }

    private static String plainText(String html) {
        if (html == null) {
            return null;
        }
        return HtmlUtils.htmlUnescape(html.replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").trim();
    }

    private static String text(JsonNode node) {
        return node.isString() ? HtmlUtils.htmlUnescape(node.asString()) : null;
    }
}
