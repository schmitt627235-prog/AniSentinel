package de.anisentinel.app.domain.watcher

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ReleaseDateRelation { TODAY, TOMORROW, THIS_WEEK, NEXT_WEEK, LATER, PAST }

object ReleaseDateClassifier {
    fun classify(releaseDate: LocalDate, today: LocalDate): ReleaseDateRelation {
        if (releaseDate.isBefore(today)) return ReleaseDateRelation.PAST
        if (releaseDate == today) return ReleaseDateRelation.TODAY
        if (releaseDate == today.plusDays(1)) return ReleaseDateRelation.TOMORROW
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val nextMonday = thisMonday.plusWeeks(1)
        val followingMonday = nextMonday.plusWeeks(1)
        return when {
            releaseDate.isBefore(nextMonday) -> ReleaseDateRelation.THIS_WEEK
            releaseDate.isBefore(followingMonday) -> ReleaseDateRelation.NEXT_WEEK
            else -> ReleaseDateRelation.LATER
        }
    }
}
