package de.anisentinel.app.data.anilist

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarTimeWindowTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test fun `midnight boundary uses Berlin instead of UTC`() {
        val window = calendarTimeWindow(
            LocalDate.of(2026, 8, 2),
            LocalDate.of(2026, 8, 3),
            berlin
        )
        assertEquals(Instant.parse("2026-08-01T22:00:00Z").epochSecond, window.fromEpochSeconds)
        assertEquals(Instant.parse("2026-08-02T22:00:00Z").epochSecond, window.untilEpochSeconds)
    }

    @Test fun `0030 Berlin release belongs to requested local day`() {
        val release = Instant.parse("2026-08-01T22:30:00Z").epochSecond
        val window = calendarTimeWindow(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3), berlin)
        assertEquals(true, release in window.fromEpochSeconds until window.untilEpochSeconds)
    }

    @Test fun `month boundary uses local calendar boundary`() {
        val window = calendarTimeWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), berlin)
        assertEquals(Instant.parse("2026-07-31T22:00:00Z").epochSecond, window.fromEpochSeconds)
        assertEquals(Instant.parse("2026-08-31T22:00:00Z").epochSecond, window.untilEpochSeconds)
    }

    @Test fun `spring daylight saving day contains 23 hours`() {
        val window = calendarTimeWindow(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 3, 30), berlin)
        assertEquals(23, Duration.ofSeconds(window.untilEpochSeconds - window.fromEpochSeconds).toHours())
    }

    @Test fun `autumn daylight saving day contains 25 hours`() {
        val window = calendarTimeWindow(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 26), berlin)
        assertEquals(25, Duration.ofSeconds(window.untilEpochSeconds - window.fromEpochSeconds).toHours())
    }
}
