package com.resqora.resqora_backend.service.ai;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiServiceDayTest {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Test
    void dayStartsAtMidnightIstNotUtc() {
        // 2026-10-13 20:00 UTC = 2026-10-14 01:30 IST -> the IST day started at 2026-10-13 18:30 UTC
        assertEquals(Instant.parse("2026-10-13T18:30:00Z"),
                AiService.startOfDay(Instant.parse("2026-10-13T20:00:00Z"), IST));
        // 2026-10-13 17:00 UTC = 22:30 IST same day -> day started 2026-10-12 18:30 UTC
        assertEquals(Instant.parse("2026-10-12T18:30:00Z"),
                AiService.startOfDay(Instant.parse("2026-10-13T17:00:00Z"), IST));
    }

    @Test
    void retryAfterCountsToNextIstMidnight() {
        // 2026-10-14 06:30 UTC = 12:00 IST -> 12 h to midnight IST
        assertEquals(12 * 3600, AiService.secondsUntilNextMidnight(Instant.parse("2026-10-14T06:30:00Z"), IST));
        // 1 second before midnight IST
        assertEquals(1, AiService.secondsUntilNextMidnight(Instant.parse("2026-10-14T18:29:59Z"), IST));
    }
}
