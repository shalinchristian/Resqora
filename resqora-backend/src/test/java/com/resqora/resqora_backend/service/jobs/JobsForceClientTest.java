package com.resqora.resqora_backend.service.jobs;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class JobsForceClientTest {
    private HttpServer server;
    private String base;
    private String body;
    private int status;
    private final List<String> queries = new ArrayList<>();
    static final String JOB = """
        {"title":"Java Developer","hiring":{"type":"agency","name":"Do not use","employer":"confidential","display":"  JobsForce India for a confidential client & team  "},
        "location":{"city":"Pune","state":"Maharashtra","country":"India","remote":false},
        "summary":"Java and SQL","posted_date":"2026-11-10","url":"https://jobsforceindia.com/job/java","apply_url":"https://jobsforceindia.com/apply/java"}
        """;
    @BeforeEach void start() throws Exception {
        status = 200; body = "{\"results\":[" + JOB + "],\"has_more\":false,\"next_page\":null}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/public/v1/jobs", exchange -> {
            queries.add(exchange.getRequestURI().getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (status == 429) exchange.getResponseHeaders().add("Retry-After", "90");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/public/v1";
    }
    @AfterEach void stop() { server.stop(0); }
    private JobsForceQuery query() { return new JobsForceQuery("Java Developer", List.of("Spring Boot", "SQL"), "Pune", false, false, 2.5); }
    @Test void realResponseShapeAndExactDisplayPassThrough() {
        var jobs = new JobsForceClient(base).search(query());
        assertEquals(1, jobs.size()); var job = jobs.getFirst();
        assertEquals("  JobsForce India for a confidential client & team  ", job.hiringName());
        assertEquals("Pune, Maharashtra, India", job.location()); assertEquals(LocalDate.of(2026,11,10), job.postedAt());
        assertEquals("https://jobsforceindia.com/apply/java", job.applyUrl());
        assertTrue(queries.getFirst().contains("q=Java Developer Spring Boot SQL"));
        assertTrue(queries.getFirst().contains("experience_years=2.5")); assertTrue(queries.getFirst().contains("per_page=20"));
    }
    @Test void absentDisplayNeverUsesNameOrInventsCompany() {
        body = body.replace("\"display\":\"  JobsForce India for a confidential client & team  \",", "");
        // display is the last member in fixture
        body = body.replace(",\"display\":\"  JobsForce India for a confidential client & team  \"", "");
        assertNull(new JobsForceClient(base).search(query()).getFirst().hiringName());
    }
    @Test void urlFilterKeepsApplyOnlyAndRejectsUnsafeSchemes() {
        body = "{\"results\":[" + JOB.replace("https://jobsforceindia.com/job/java", "") + ","
                + JOB.replace("https://jobsforceindia.com/job/java", "javascript:alert(1)")
                .replace("https://jobsforceindia.com/apply/java", "") + "]}";
        var jobs = new JobsForceClient(base).search(query()); assertEquals(1, jobs.size()); assertNull(jobs.getFirst().url());
    }
    @Test void fetchesAtMostTwoPagesOfTwentyCandidates() {
        body = "{\"results\":[" + JOB + "],\"has_more\":true,\"next_page\":2}";
        assertEquals(2, new JobsForceClient(base).search(query()).size());
        assertEquals(2, queries.size()); assertTrue(queries.get(1).contains("page=2"));
    }
    @Test void fresherAndInternshipAreExplicitFilters() {
        new JobsForceClient(base).search(new JobsForceQuery("Intern", List.of("C++", "C#"), null, true, true, null));
        assertTrue(queries.getFirst().contains("fresher=true")); assertTrue(queries.getFirst().contains("job_type=internship"));
        assertTrue(queries.getFirst().contains("C++ C#"));
    }
    @Test void providerFailureHasNoRetry() {
        status = 503; assertThrows(RuntimeException.class, () -> new JobsForceClient(base).search(query())); assertEquals(1, queries.size());
    }
    @Test void malformedResponseIsNotMistakenForSuccess() {
        body = "{\"jobs\":[]}"; assertThrows(RuntimeException.class, () -> new JobsForceClient(base).search(query()));
    }
    @Test void rateLimitHonorsRetryAfterAndDoesNotRetry() {
        MutableClock clock = new MutableClock(); JobsForceClient client = new JobsForceClient(base, clock);
        status = 429; assertThrows(RuntimeException.class, () -> client.search(query()));
        status = 200; clock.now = clock.now.plusSeconds(61);
        assertThrows(RuntimeException.class, () -> client.search(query())); assertEquals(1, queries.size());
        clock.now = clock.now.plusSeconds(30); client.search(query()); assertEquals(2, queries.size());
    }
    @Test void localSlidingWindowStaysBelow120RequestsPerMinute() {
        MutableClock clock = new MutableClock(); JobsForceClient client = new JobsForceClient(base, clock);
        body = "{\"results\":[]}";
        for (int i = 0; i < 100; i++) client.search(query());
        assertThrows(RuntimeException.class, () -> client.search(query())); assertEquals(100, queries.size());
        clock.now = clock.now.plusSeconds(60); client.search(query()); assertEquals(101, queries.size());
    }
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-11T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
