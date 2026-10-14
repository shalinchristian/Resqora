package com.resqora.resqora_backend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTest {

    @Test
    void blocksAfterLimitAndUnblocksAfterWindow() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(3, 100, 15);
        Instant start = Instant.parse("2026-10-13T10:00:00Z");
        limiter.setClock(Clock.fixed(start, ZoneOffset.UTC));

        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("A@x.com", "1.1.1.1");
        }
        assertTrue(limiter.isBlocked("a@x.com", "9.9.9.9")); // email is case-insensitive

        limiter.setClock(Clock.fixed(start.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
        assertFalse(limiter.isBlocked("a@x.com", "9.9.9.9"));
    }

    @Test
    void successClearsEmailCounter() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(2, 100, 15);
        limiter.recordFailure("b@x.com", "1.1.1.1");
        limiter.recordFailure("b@x.com", "1.1.1.1");
        assertTrue(limiter.isBlocked("b@x.com", "1.1.1.1"));
        limiter.recordSuccess("b@x.com");
        assertFalse(limiter.isBlocked("b@x.com", "1.1.1.1"));
    }
}
