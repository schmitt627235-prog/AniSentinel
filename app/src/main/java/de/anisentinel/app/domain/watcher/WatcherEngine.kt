package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.domain.model.WatchProfile
import de.anisentinel.app.domain.repository.ProviderCheckRequest
import de.anisentinel.app.domain.repository.ProviderCheckResult
import de.anisentinel.app.domain.repository.ProviderRepository
import java.time.Instant

interface WatchScheduler {
    fun nextCheckAt(releaseAt: Instant, now: Instant, profile: WatchProfile): Instant?
}

class ProfileWatchScheduler : WatchScheduler {
    override fun nextCheckAt(
        releaseAt: Instant,
        now: Instant,
        profile: WatchProfile
    ): Instant? {
        val secondsAfterRelease = (now.epochSecond - releaseAt.epochSecond).coerceAtLeast(0)
        val phase = profile.phaseAt(secondsAfterRelease) ?: return null
        return now.plusSeconds(phase.intervalSeconds)
    }
}

sealed interface NotificationEvent {
    data class ReleaseDue(val animeId: String, val episode: Int, val animeTitle: String? = null, val season: Int? = null, val language: String? = null) : NotificationEvent
    data class ReleaseReminder(
        val animeId: String,
        val episode: Int,
        val releaseAt: Instant
    ) : NotificationEvent
    data class EpisodeAvailable(
        val animeId: String,
        val episode: Int,
        val provider: String? = null,
        val language: String? = null,
        val animeTitle: String? = null,
        val season: Int? = null,
        val firstDetectedAt: Instant? = null
    ) : NotificationEvent
    data class ReleaseDelayed(val animeId: String, val episode: Int, val animeTitle: String? = null, val season: Int? = null) : NotificationEvent
    data class OfficiallyPostponed(
        val animeId: String,
        val episode: Int,
        val animeTitle: String? = null,
        val season: Int? = null,
        val reason: String? = null,
        val revisedAt: Instant? = null
    ) : NotificationEvent
    data class ProviderError(
        val animeId: String,
        val providerId: String,
        val retryable: Boolean
    ) : NotificationEvent
    data class ProviderMaintenance(
        val providerId: String,
        val retryAfterSeconds: Long
    ) : NotificationEvent
}

data class WatcherResult(
    val visibleStatus: ReleaseStatus,
    val nextCheckAt: Instant?,
    val notification: NotificationEvent?,
    val sourceError: Boolean
)

class WatcherEngine(
    private val providerRepository: ProviderRepository,
    private val scheduler: WatchScheduler
) {
    suspend fun dispatch(
        request: ProviderCheckRequest,
        currentStatus: ReleaseStatus,
        releaseAt: Instant,
        profile: WatchProfile
    ): WatcherResult {
        return when (val checkResult = providerRepository.check(request)) {
            is ProviderCheckResult.Available -> WatcherResult(
                visibleStatus = ReleaseStatus.AVAILABLE,
                nextCheckAt = null,
                notification = NotificationEvent.EpisodeAvailable(
                    request.animeId,
                    request.episode
                ),
                sourceError = false
            )
            is ProviderCheckResult.Unavailable -> {
                val status = if (request.checkedAt.isAfter(releaseAt)) {
                    ReleaseStatus.DELAYED_UNCONFIRMED
                } else {
                    currentStatus
                }
                WatcherResult(
                    visibleStatus = status,
                    nextCheckAt = scheduler.nextCheckAt(releaseAt, request.checkedAt, profile),
                    notification = if (status == ReleaseStatus.DELAYED_UNCONFIRMED) {
                        NotificationEvent.ReleaseDelayed(request.animeId, request.episode)
                    } else {
                        null
                    },
                    sourceError = false
                )
            }
            is ProviderCheckResult.Error -> WatcherResult(
                visibleStatus = currentStatus,
                nextCheckAt = scheduler.nextCheckAt(releaseAt, request.checkedAt, profile),
                notification = NotificationEvent.ProviderError(
                    request.animeId,
                    request.providerId,
                    retryable = checkResult.retryable
                ),
                sourceError = true
            )
            is ProviderCheckResult.Delayed -> WatcherResult(
                visibleStatus = ReleaseStatus.DELAYED_UNCONFIRMED,
                nextCheckAt = scheduler.nextCheckAt(releaseAt, request.checkedAt, profile),
                notification = NotificationEvent.ReleaseDelayed(
                    request.animeId,
                    request.episode
                ),
                sourceError = false
            )
            is ProviderCheckResult.Maintenance -> WatcherResult(
                visibleStatus = currentStatus,
                nextCheckAt = request.checkedAt.plusSeconds(checkResult.retryAfterSeconds),
                notification = NotificationEvent.ProviderMaintenance(
                    request.providerId,
                    retryAfterSeconds = checkResult.retryAfterSeconds
                ),
                sourceError = true
            )
        }
    }
}
