package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.ReleaseStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseCountdownTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `splits weeks days and clock units`() {
        val release = now.plus(
            Duration.ofDays(10)
                .plusHours(8)
                .plusMinutes(24)
                .plusSeconds(17)
        )

        val result = calculate(release)

        assertEquals(1, result.weeks)
        assertEquals(3, result.days)
        assertEquals(8, result.hours)
        assertEquals(24, result.minutes)
        assertEquals(17, result.seconds)
    }

    @Test
    fun `handles exact day and hour boundaries`() {
        val day = calculate(now.plus(Duration.ofDays(1)))
        val hour = calculate(now.plus(Duration.ofHours(1)))

        assertEquals(1, day.days)
        assertEquals(0, day.hours)
        assertEquals(1, hour.hours)
        assertEquals(0, hour.minutes)
    }

    @Test
    fun `stops at zero for exact and past releases`() {
        val exact = calculate(now)
        val past = calculate(now.minusSeconds(30))

        assertEquals(0, exact.totalSeconds)
        assertTrue(exact.isElapsed)
        assertEquals(0, past.totalSeconds)
        assertTrue(past.isElapsed)
    }

    @Test
    fun `future release is not elapsed`() {
        assertFalse(calculate(now.plusSeconds(1)).isElapsed)
    }

    @Test
    fun `instant calculation remains correct across daylight saving change`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val beforeChange = ZonedDateTime.of(2026, 3, 29, 1, 30, 0, 0, berlin)
        val afterChange = ZonedDateTime.of(2026, 3, 29, 3, 30, 0, 0, berlin)

        val result = ReleaseCountdown.calculate(
            releaseAt = afterChange.toInstant(),
            clock = Clock.fixed(beforeChange.toInstant(), berlin)
        )

        assertEquals(1, result.hours)
        assertEquals(0, result.minutes)
    }

    @Test
    fun `release point enters checking phase`() {
        val result = ReleaseStatusMachine.enterReleasePhase(ReleaseStatus.SCHEDULED)

        assertEquals(ReleaseStatus.CHECKING, result.visibleStatus)
    }

    private fun calculate(release: Instant) = ReleaseCountdown.calculate(
        releaseAt = release,
        clock = Clock.fixed(now, ZoneId.of("Europe/Berlin"))
    )
}
