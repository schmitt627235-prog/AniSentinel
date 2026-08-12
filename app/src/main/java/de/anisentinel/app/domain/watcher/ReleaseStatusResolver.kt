package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.AvailabilityStatus
import de.anisentinel.app.domain.model.ReleaseStatus
import java.time.Clock
import java.time.Instant

class ReleaseStatusResolver(private val clock: Clock = Clock.systemUTC()) {
    fun resolve(anime: Anime): ReleaseStatus {
        anime.streamingAvailability.firstOrNull {
            it.status == AvailabilityStatus.AVAILABLE
        }?.let { return ReleaseStatus.AVAILABLE }
        return resolve(anime.expectedReleaseAt, clock.instant())
    }

    fun resolve(expectedReleaseAt: Instant?, now: Instant = clock.instant()): ReleaseStatus =
        when {
            expectedReleaseAt == null -> ReleaseStatus.UNKNOWN
            expectedReleaseAt.isAfter(now) -> ReleaseStatus.SCHEDULED
            else -> ReleaseStatus.RELEASE_TIME_REACHED
        }
}
