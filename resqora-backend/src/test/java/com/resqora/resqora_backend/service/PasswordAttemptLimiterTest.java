package com.resqora.resqora_backend.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordAttemptLimiterTest {
    @Test
    void blocksAfterFiveFailuresUntilWindowEndsAndSuccessClears() {
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(5, 15);
        Instant start = Instant.parse("2026-10-14T06:00:00Z");
        limiter.setClock(Clock.fixed(start, ZoneOffset.UTC));
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure(1L);
        }
        assertFalse(limiter.isBlocked(1L));
        limiter.recordFailure(1L);
        assertTrue(limiter.isBlocked(1L));
        assertFalse(limiter.isBlocked(2L));
        assertEquals(901, limiter.retryAfterSeconds(1L));

        limiter.setClock(Clock.fixed(start.plus(Duration.ofMinutes(15)), ZoneOffset.UTC));
        assertFalse(limiter.isBlocked(1L));

        limiter.recordFailure(3L);
        limiter.recordSuccess(3L);
        assertFalse(limiter.isBlocked(3L));
    }

    @Test
    void messageUsesTheConfiguredWindow() {
        PasswordAttemptLimiter limiter = new PasswordAttemptLimiter(1, 30);
        limiter.recordFailure(9L);
        var e = org.junit.jupiter.api.Assertions.assertThrows(
                com.resqora.resqora_backend.exception.TooManyPasswordAttemptsException.class,
                () -> limiter.rejectIfBlocked(9L));
        assertEquals("Too many wrong passwords. Try again in 30 minutes.", e.getMessage());
    }
}
