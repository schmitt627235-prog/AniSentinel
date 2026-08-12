package de.anisentinel.app.data.provider

import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.ProviderAvailabilityEntity
import de.anisentinel.app.domain.provider.ProviderAvailabilityResult
import de.anisentinel.app.domain.provider.ProviderCheckRequest

class ProviderAvailabilityRepository(
    private val dao: AniSentinelDao,
    private val crunchyroll: CrunchyrollCalendarChecker
) {
    suspend fun checkCrunchyroll(request: ProviderCheckRequest): ProviderAvailabilityResult {
        val result = crunchyroll.checkAvailability(request)
        val episodeKey = request.expectedEpisode ?: -1
        val existing = dao.providerAvailability(request.animeId, "CRUNCHYROLL", episodeKey)
        val available = result as? ProviderAvailabilityResult.Available
        dao.upsertProviderAvailability(
            ProviderAvailabilityEntity(
                animeId = request.animeId,
                provider = "CRUNCHYROLL",
                episodeKey = episodeKey,
                episodeNumber = request.expectedEpisode,
                status = result.statusName(),
                providerUrl = when (result) {
                    is ProviderAvailabilityResult.Available -> result.pageUrl
                    is ProviderAvailabilityResult.TitleFoundEpisodeMissing -> result.pageUrl
                    else -> null
                },
                checkedAt = result.checkedAt.epochSecond,
                firstAvailableAt = if (available != null) {
                    existing?.firstAvailableAt ?: result.checkedAt.epochSecond
                } else existing?.firstAvailableAt,
                errorReason = when (result) {
                    is ProviderAvailabilityResult.NetworkError -> "NETWORK_ERROR"
                    is ProviderAvailabilityResult.ParseError -> "PARSE_ERROR"
                    is ProviderAvailabilityResult.TemporarilyUnavailable -> result.reason
                    else -> null
                },
                evidenceType = available?.evidenceType?.name,
                availabilityNotificationSentAt = existing?.availabilityNotificationSentAt
            )
        )
        return result
    }

    private fun ProviderAvailabilityResult.statusName(): String = when (this) {
        is ProviderAvailabilityResult.Available -> "AVAILABLE"
        is ProviderAvailabilityResult.TitleFoundEpisodeMissing -> "TITLE_FOUND_EPISODE_MISSING"
        is ProviderAvailabilityResult.TitleNotFound -> "TITLE_NOT_FOUND"
        is ProviderAvailabilityResult.LoginRequired -> "LOGIN_REQUIRED"
        is ProviderAvailabilityResult.RegionBlocked -> "REGION_BLOCKED"
        is ProviderAvailabilityResult.TemporarilyUnavailable -> "TEMPORARILY_UNAVAILABLE"
        is ProviderAvailabilityResult.ParseError -> "CHECK_FAILED"
        is ProviderAvailabilityResult.NetworkError -> "NETWORK_ERROR"
    }
}
