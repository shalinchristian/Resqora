package com.resqora.resqora_backend.service.jobs;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JobRecommendationCacheTest {
    @Test void perResumeCacheExpiresAtTwelveHoursNotAtTwentyFour() {
        var clock = new JobsForceClientTest.MutableClock(); var calls = new AtomicInteger();
        try (var cache = new JobRecommendationCache(clock)) {
            cache.getOrLoad(1L, () -> { calls.incrementAndGet(); return List.of(); });
            clock.now = clock.now.plus(Duration.ofHours(12)).minusSeconds(1);
            cache.getOrLoad(1L, () -> { calls.incrementAndGet(); return List.of(); }); assertEquals(1, calls.get());
            clock.now = clock.now.plusSeconds(1);
            cache.getOrLoad(1L, () -> { calls.incrementAndGet(); return List.of(); }); assertEquals(2, calls.get());
            cache.getOrLoad(2L, () -> { calls.incrementAndGet(); return List.of(); }); assertEquals(3, calls.get());
        }
    }
    @Test void expiredEntriesAreRemovedEvenWithoutAnotherLookup() {
        var clock = new JobsForceClientTest.MutableClock();
        try (var cache = new JobRecommendationCache(clock)) {
            cache.getOrLoad(1L, List::of); clock.now = clock.now.plus(Duration.ofHours(13));
            cache.removeExpired(); assertEquals(0, cache.size());
        }
    }
    @Test void failuresAreNegativelyCachedOnlyForOneMinute() {
        var clock = new JobsForceClientTest.MutableClock(); var calls = new AtomicInteger();
        try (var cache = new JobRecommendationCache(clock)) {
            for (int i = 0; i < 5; i++) assertTrue(cache.getOrLoad(1L, () -> { calls.incrementAndGet(); throw new IllegalStateException(); }).isEmpty());
            assertEquals(1, calls.get()); clock.now = clock.now.plusSeconds(60);
            cache.getOrLoad(1L, () -> { calls.incrementAndGet(); return List.of(); }); assertEquals(2, calls.get());
        }
    }
    @Test void concurrentRequestsForSameResumeAreSingleFlight() throws Exception {
        try (var cache = new JobRecommendationCache(); var executor = Executors.newFixedThreadPool(8)) {
            var calls = new AtomicInteger(); List<Callable<List<JobsForcePosting>>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) tasks.add(() -> cache.getOrLoad(1L, () -> { calls.incrementAndGet(); return List.of(); }));
            for (var task : executor.invokeAll(tasks)) assertTrue(task.get().isEmpty());
            assertEquals(1, calls.get());
        }
    }
    @Test void cacheIsBounded() {
        try (var cache = new JobRecommendationCache()) {
            for (long id = 0; id < 1005; id++) cache.getOrLoad(id, List::of);
            assertEquals(1000, cache.size());
        }
    }
}
