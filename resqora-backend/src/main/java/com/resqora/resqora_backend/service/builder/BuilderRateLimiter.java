package com.resqora.resqora_backend.service.builder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Per-user caps on the builder's CPU-heavy calls (file export, parse an upload), so one account can't
// hammer the free instance. Fixed windows in memory, like CodeRequestLimiter; they reset on restart.
@Component
public class BuilderRateLimiter {
    public enum Action { EXPORT, PARSE }

    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Map<Action, Integer> limits;
    private final Duration window;
    private Clock clock = Clock.systemUTC();

    public BuilderRateLimiter(
            @Value("${resqora.builder.max-exports-per-window:30}") int maxExports,
            @Value("${resqora.builder.max-parses-per-window:10}") int maxParses,
            @Value("${resqora.builder.window-minutes:10}") long windowMinutes) {
        this.limits = Map.of(Action.EXPORT, maxExports, Action.PARSE, maxParses);
        this.window = Duration.ofMinutes(windowMinutes);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // counts this call; returns seconds to wait if the user is over the limit, or 0 if it may go ahead
    public long tryAcquire(Action action, Long userId) {
        Instant now = clock.instant();
        // drop expired windows now and then so the map can't grow without bound
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> !now.isBefore(w.start().plus(window)));
        }
        Window w = windows.compute(action + ":" + userId, (k, old) -> old == null || !now.isBefore(old.start().plus(window))
                ? new Window(now, 1)
                : new Window(old.start(), old.count() + 1));
        if (w.count() <= limits.get(action)) {
            return 0;
        }
        return Math.max(1, Duration.between(now, w.start().plus(window)).toSeconds() + 1);
    }
}
