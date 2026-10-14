package com.resqora.resqora_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// simple in-memory brute force protection for /api/auth/login.
// counts failed logins per email and per IP in a fixed window (15 min by default).
// a successful login clears the email's counter. all in memory so it resets on restart
@Component
public class LoginAttemptLimiter {

    private record Window(Instant start, int failures) {
    }

    private final Map<String, Window> byEmail = new ConcurrentHashMap<>();
    private final Map<String, Window> byIp = new ConcurrentHashMap<>();
    private final int maxPerEmail;
    private final int maxPerIp;
    private final Duration window;
    private Clock clock = Clock.systemUTC();

    public LoginAttemptLimiter(
            @Value("${resqora.login.max-attempts-per-email:5}") int maxPerEmail,
            @Value("${resqora.login.max-attempts-per-ip:20}") int maxPerIp,
            @Value("${resqora.login.window-minutes:15}") long windowMinutes) {
        this.maxPerEmail = maxPerEmail;
        this.maxPerIp = maxPerIp;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    // for tests: move time forward without sleeping
    void setClock(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String email, String ip) {
        return failures(byEmail, key(email)) >= maxPerEmail || failures(byIp, ip) >= maxPerIp;
    }

    // seconds until the block ends (for Retry-After), at least 1
    public long retryAfterSeconds(String email, String ip) {
        long seconds = 1;
        if (failures(byEmail, key(email)) >= maxPerEmail) {
            seconds = Math.max(seconds, remaining(byEmail.get(key(email))));
        }
        if (failures(byIp, ip) >= maxPerIp) {
            seconds = Math.max(seconds, remaining(byIp.get(ip)));
        }
        return seconds;
    }

    private long remaining(Window w) {
        return w == null ? 0 : Math.max(0, Duration.between(clock.instant(), w.start().plus(window)).toSeconds() + 1);
    }

    public void recordFailure(String email, String ip) {
        increment(byEmail, key(email));
        increment(byIp, ip);
    }

    public void recordSuccess(String email) {
        byEmail.remove(key(email));
    }

    private int failures(Map<String, Window> map, String key) {
        if (key == null) {
            return 0;
        }
        Window w = map.get(key);
        if (w == null || expired(w)) {
            return 0;
        }
        return w.failures();
    }

    private void increment(Map<String, Window> map, String key) {
        if (key == null) {
            return;
        }
        map.compute(key, (k, w) -> w == null || expired(w)
                ? new Window(clock.instant(), 1)
                : new Window(w.start(), w.failures() + 1));
    }

    private boolean expired(Window w) {
        return !clock.instant().isBefore(w.start().plus(window));
    }

    private static String key(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
