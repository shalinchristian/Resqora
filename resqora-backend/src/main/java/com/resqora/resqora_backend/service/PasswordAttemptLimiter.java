package com.resqora.resqora_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Same idea as LoginAttemptLimiter, for password checks made by a logged-in user (DELETE /api/auth/me):
// counts WRONG passwords per user inside a fixed window. In memory, so it resets on restart.
@Component
public class PasswordAttemptLimiter {
    private record Window(Instant start, int failures) {
    }

    private final Map<Long, Window> byUser = new ConcurrentHashMap<>();
    private final int maxPerUser;
    private final Duration window;
    private Clock clock = Clock.systemUTC();

    public PasswordAttemptLimiter(
            @Value("${resqora.account.max-password-attempts:5}") int maxPerUser,
            @Value("${resqora.account.password-window-minutes:15}") long windowMinutes) {
        this.maxPerUser = maxPerUser;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public long windowMinutes() {
        return window.toMinutes();
    }

    // 429 when blocked; shared by change-password and delete-account (one counter per user)
    public void rejectIfBlocked(Long userId) {
        if (isBlocked(userId)) {
            throw new com.resqora.resqora_backend.exception.TooManyPasswordAttemptsException(
                    retryAfterSeconds(userId), windowMinutes());
        }
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(Long userId) {
        Window w = byUser.get(userId);
        return w != null && !expired(w) && w.failures() >= maxPerUser;
    }

    // seconds until the window ends (Retry-After); at least 1
    public long retryAfterSeconds(Long userId) {
        Window w = byUser.get(userId);
        if (w == null) {
            return 1;
        }
        return Math.max(1, Duration.between(clock.instant(), w.start().plus(window)).toSeconds() + 1);
    }

    public void recordFailure(Long userId) {
        byUser.compute(userId, (k, w) -> w == null || expired(w)
                ? new Window(clock.instant(), 1)
                : new Window(w.start(), w.failures() + 1));
    }

    public void recordSuccess(Long userId) {
        byUser.remove(userId);
    }

    private boolean expired(Window w) {
        return !clock.instant().isBefore(w.start().plus(window));
    }
}
