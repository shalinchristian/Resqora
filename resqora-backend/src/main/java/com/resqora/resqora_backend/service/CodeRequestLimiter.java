package com.resqora.resqora_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Limits how often one IP address can ask for codes (sign-up, resend, forgot password),
// so nobody can use Resqora to flood inboxes or burn through the free email quota.
// Counters live in memory, so they reset on restart.
@Component
public class CodeRequestLimiter {
    private record Window(Instant start, int count) {
    }

    private final Map<String, Window> byIp = new ConcurrentHashMap<>();
    private final int maxPerIp;
    private final Duration window;
    private Clock clock = Clock.systemUTC();

    public CodeRequestLimiter(
            @Value("${resqora.email.max-code-requests-per-ip:10}") int maxPerIp,
            @Value("${resqora.email.code-request-window-minutes:60}") long windowMinutes) {
        this.maxPerIp = maxPerIp;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // counts this request; returns seconds to wait if the IP is over the limit, or 0 if it may continue
    public long tryAcquire(String ip) {
        Instant now = clock.instant();
        Window w = byIp.compute(ip, (k, old) -> old == null || !now.isBefore(old.start().plus(window))
                ? new Window(now, 1)
                : new Window(old.start(), old.count() + 1));
        if (w.count() <= maxPerIp) {
            return 0;
        }
        return Math.max(1, Duration.between(now, w.start().plus(window)).toSeconds() + 1);
    }
}
