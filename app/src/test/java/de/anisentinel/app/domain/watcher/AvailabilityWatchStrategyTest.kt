package de.anisentinel.app.domain.watcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityWatchStrategyTest {
    @Test fun `automatic profile follows every exact product boundary`() {
        val s = AvailabilityWatchStrategy
        assertEquals(30, s.intervalSeconds("automatic", 0))
        assertEquals(30, s.intervalSeconds("automatic", 299))
        assertEquals(60, s.intervalSeconds("automatic", 300))
        assertEquals(60, s.intervalSeconds("automatic", 599))
        assertEquals(300, s.intervalSeconds("automatic", 600))
        assertEquals(300, s.intervalSeconds("automatic", 3_599))
        assertEquals(1_800, s.intervalSeconds("automatic", 3_600))
        assertEquals(1_800, s.intervalSeconds("automatic", 14_399))
        assertEquals(3_600, s.intervalSeconds("automatic", 14_400))
    }

    @Test fun `all manual profiles expose their requested interval`() {
        assertEquals(
            listOf(30L, 60L, 120L, 300L, 600L, 900L, 1_800L, 3_600L),
            listOf("30s", "1m", "2m", "5m", "10m", "15m", "30m", "1h")
                .map { AvailabilityWatchStrategy.intervalSeconds(it, 99_999) }
        )
    }

    @Test fun `next tick is anchored to expected time and skips missed ticks`() {
        assertEquals(1_030, AvailabilityWatchStrategy.nextCheckAt(1_000, 1_001, "automatic"))
        assertEquals(1_360, AvailabilityWatchStrategy.nextCheckAt(1_000, 1_301, "automatic"))
        assertEquals(1_120, AvailabilityWatchStrategy.nextCheckAt(1_000, 1_061, "1m"))
    }

    @Test fun `available delayed and postponed are terminal`() {
        assertTrue(AvailabilityWatchStrategy.isTerminal("AVAILABLE"))
        assertTrue(AvailabilityWatchStrategy.isTerminal("AVAILABLE_GER_SUB"))
        assertTrue(AvailabilityWatchStrategy.isTerminal("POSTPONED"))
        assertTrue(AvailabilityWatchStrategy.isTerminal("DELAYED_CONFIRMED"))
        assertFalse(AvailabilityWatchStrategy.isTerminal("OVERDUE_UNCONFIRMED"))
    }
}
