package de.anisentinel.app.domain.watcher

import java.time.Clock
import java.time.Duration
import java.time.Instant

data class CountdownParts(
    val weeks: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val isElapsed: Boolean
) {
    val totalSeconds: Long
        get() = weeks * SECONDS_PER_WEEK +
            days * SECONDS_PER_DAY +
            hours * SECONDS_PER_HOUR +
            minutes * SECONDS_PER_MINUTE +
            seconds

    companion object {
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 60L * SECONDS_PER_MINUTE
        const val SECONDS_PER_DAY = 24L * SECONDS_PER_HOUR
        const val SECONDS_PER_WEEK = 7L * SECONDS_PER_DAY
    }
}

object ReleaseCountdown {
    fun calculate(
        releaseAt: Instant,
        clock: Clock = Clock.systemUTC()
    ): CountdownParts {
        val remainingSeconds = Duration.between(clock.instant(), releaseAt)
            .seconds
            .coerceAtLeast(0)

        var rest = remainingSeconds
        val weeks = rest / CountdownParts.SECONDS_PER_WEEK
        rest %= CountdownParts.SECONDS_PER_WEEK
        val days = rest / CountdownParts.SECONDS_PER_DAY
        rest %= CountdownParts.SECONDS_PER_DAY
        val hours = rest / CountdownParts.SECONDS_PER_HOUR
        rest %= CountdownParts.SECONDS_PER_HOUR
        val minutes = rest / CountdownParts.SECONDS_PER_MINUTE
        val seconds = rest % CountdownParts.SECONDS_PER_MINUTE

        return CountdownParts(
            weeks = weeks,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            isElapsed = !releaseAt.isAfter(clock.instant())
        )
    }
}
