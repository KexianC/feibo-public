package com.hzmwmt.domain.service.tts;

import com.hzmwmt.types.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TtsUsageGuardTest {

    @Test
    void enforcesPerUserRequestBudget() {
        TtsUsageGuard guard = new TtsUsageGuard(2, 1, 100, 60_000);

        try (TtsUsageGuard.Permit ignored = guard.acquire(1L, 10)) {
            // The permit is released after provider work completes.
        }

        assertThrows(RateLimitException.class, () -> guard.acquire(1L, 10));
        assertDoesNotThrow(() -> {
            try (TtsUsageGuard.Permit ignored = guard.acquire(2L, 10)) {
                // A different authenticated user owns a separate budget.
            }
        });
    }

    @Test
    void enforcesPerUserCharacterBudget() {
        TtsUsageGuard guard = new TtsUsageGuard(2, 10, 10, 60_000);

        try (TtsUsageGuard.Permit ignored = guard.acquire(1L, 8)) {
            // First segment fits.
        }

        assertThrows(RateLimitException.class, () -> guard.acquire(1L, 3));
    }

    @Test
    void releasesGlobalConcurrencyPermit() {
        TtsUsageGuard guard = new TtsUsageGuard(1, 10, 100, 60_000);
        TtsUsageGuard.Permit first = guard.acquire(1L, 5);

        assertThrows(RateLimitException.class, () -> guard.acquire(2L, 5));
        first.close();

        assertDoesNotThrow(() -> {
            try (TtsUsageGuard.Permit ignored = guard.acquire(2L, 5)) {
                // Capacity is available again after close.
            }
        });
    }
}
