package com.resqora.resqora_backend.service.jobs;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded process-local cache, single-flight per resume. Never writes JobsForce data to disk/DB. */
@Component
public class JobRecommendationCache implements AutoCloseable {
    static final Duration TTL = Duration.ofHours(12), FAILURE_TTL = Duration.ofMinutes(1);
    static final int MAX_ENTRIES = 1000;
    record Entry(List<JobsForcePosting> jobs, Instant expiresAt) { }
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final ScheduledExecutorService cleaner;
    public JobRecommendationCache() { this(Clock.systemUTC()); }
    JobRecommendationCache(Clock clock) {
        this.clock = clock;
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "jobsforce-cache-expiry"); thread.setDaemon(true); return thread;
        });
        // Proactive deletion without traffic: ~12h 1m retention, safely below the 24h maximum.
        cleaner.scheduleAtFixedRate(this::removeExpired, 1, 1, TimeUnit.MINUTES);
    }
    List<JobsForcePosting> getOrLoad(Long id, Supplier<List<JobsForcePosting>> loader) {
        removeExpired();
        if (!entries.containsKey(id) && entries.size() >= MAX_ENTRIES) {
            entries.entrySet().stream().min(Comparator.comparing(e -> e.getValue().expiresAt()))
                    .ifPresent(e -> entries.remove(e.getKey(), e.getValue()));
        }
        return entries.compute(id, (key, existing) -> {
            if (existing != null && clock.instant().isBefore(existing.expiresAt())) return existing;
            try { return new Entry(List.copyOf(loader.get()), clock.instant().plus(TTL)); }
            catch (RuntimeException failure) {
                // The service logs a sanitized warning. Negative caching prevents outage retry storms.
                return new Entry(List.of(), clock.instant().plus(FAILURE_TTL));
            }
        }).jobs();
    }
    void removeExpired() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(e -> !now.isBefore(e.getValue().expiresAt()));
    }
    int size() { return entries.size(); }
    @PreDestroy public void close() { cleaner.shutdownNow(); entries.clear(); }
}
