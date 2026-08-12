package de.anisentinel.app.data.anilist

import java.time.LocalDate
import java.time.ZoneId

data class CalendarTimeWindow(val fromEpochSeconds: Long, val untilEpochSeconds: Long)

fun calendarTimeWindow(
    start: LocalDate,
    endExclusive: LocalDate,
    zoneId: ZoneId
): CalendarTimeWindow {
    require(endExclusive.isAfter(start)) { "END_MUST_BE_AFTER_START" }
    return CalendarTimeWindow(
        start.atStartOfDay(zoneId).toInstant().epochSecond,
        endExclusive.atStartOfDay(zoneId).toInstant().epochSecond
    )
}
