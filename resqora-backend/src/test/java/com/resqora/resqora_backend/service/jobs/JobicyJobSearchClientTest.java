package com.resqora.resqora_backend.service.jobs;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Local stub HTTP server standing in for Jobicy: no real network
class JobicyJobSearchClientTest {
    private static final String BODY = """
            {"jobCount":2,"jobs":[
              {"id":1,"url":"https://jobicy.com/jobs/1-java-dev","jobTitle":"Java Developer &amp; Lead","companyName":"Acme",
               "jobGeo":"APAC","jobDescription":"<p>Java and <b>Spring Boot</b></p>"},
              {"id":2,"jobTitle":"No URL job","companyName":"Beta","jobGeo":"India","jobDescription":"x"}]}
            """;

    private HttpServer server;
    private final List<String> queries = new ArrayList<>();
    private String body = BODY;
    private String baseUrl;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/remote-jobs", exchange -> {
            queries.add(exchange.getRequestURI().getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void parsesJobsKeepsOriginalUrlAndSkipsJobsWithoutOne() {
        List<JobPosting> jobs = new JobicyJobSearchClient(baseUrl).search(List.of("Java", "Spring"), 10);
        assertEquals(1, jobs.size());
        JobPosting job = jobs.get(0);
        assertEquals("Java Developer & Lead", job.title());
        assertEquals("Acme", job.company());
        assertEquals("APAC", job.location());
        assertEquals("Java and Spring Boot", job.description());
        assertEquals("https://jobicy.com/jobs/1-java-dev", job.url());
        assertEquals("count=50&geo=apac&tag=java", queries.get(0));
    }

    @Test
    void secondSearchWithin30MinutesUsesTheCache() {
        MutableClock clock = new MutableClock();
        JobicyJobSearchClient client = new JobicyJobSearchClient(baseUrl, clock);
        client.search(List.of("Java"), 10);
        client.search(List.of("Java"), 10);
        assertEquals(1, queries.size());
        clock.now = clock.now.plus(Duration.ofMinutes(31));
        client.search(List.of("Java"), 10);
        assertEquals(2, queries.size());
    }

    @Test
    void emptyTagResultFallsBackToAllApacJobs() {
        body = "{\"jobs\":[]}";
        new JobicyJobSearchClient(baseUrl).search(List.of("Kotlin"), 10);
        assertEquals(List.of("count=50&geo=apac&tag=kotlin", "count=50&geo=apac"), queries);
    }

    @Test
    void networkFailureThrows() {
        server.stop(0);
        assertThrows(RuntimeException.class, () -> new JobicyJobSearchClient(baseUrl).search(List.of("Java"), 10));
    }

    @Test
    void needsNoKey() {
        assertTrue(new JobicyJobSearchClient(baseUrl).isConfigured());
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-14T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
