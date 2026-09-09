package com.assetmanagement.geotag.application;

import com.assetmanagement.shared.exception.BusinessException;
import com.assetmanagement.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeotagHistorySyncServiceTest {

    @Test
    void firstWindowIsSevenDays() {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        assertEquals(now.minus(Duration.ofDays(7)), GeotagHistorySyncService.windowStart(null, now));
    }

    @Test
    void incrementalWindowUsesOverlap() {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        Instant pulledTo = Instant.parse("2026-09-08T09:00:00Z");
        assertEquals(pulledTo.minus(Duration.ofMinutes(10)), GeotagHistorySyncService.windowStart(pulledTo, now));
    }

    @Test
    void backoffGrowsThenCaps() {
        assertEquals(Duration.ofMinutes(2), GeotagHistorySyncService.backoff(1));
        assertEquals(Duration.ofMinutes(8), GeotagHistorySyncService.backoff(2));
        assertEquals(Duration.ofMinutes(30), GeotagHistorySyncService.backoff(3));
        assertEquals(Duration.ofMinutes(30), GeotagHistorySyncService.backoff(9));
    }

    @Test
    void detectsRateLimit() {
        assertTrue(GeotagHistorySyncService.looksRateLimited(new BusinessException(ErrorCode.VALIDATION_ERROR, "HTTP 429")));
        assertTrue(GeotagHistorySyncService.looksRateLimited(new RuntimeException("请求过于频繁")));
        assertFalse(GeotagHistorySyncService.looksRateLimited(new RuntimeException("timeout")));
    }
}
