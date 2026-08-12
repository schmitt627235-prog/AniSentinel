package de.anisentinel.app.domain.provider

import de.anisentinel.app.domain.model.StreamingProvider
import java.time.Instant
import java.time.LocalDate

data class ProviderCheckRequest(
    val animeId: String,
    val title: String,
    val expectedEpisode: Int?,
    val expectedDate: LocalDate,
    val seriesUrl: String?,
    val episodeUrl: String? = null,
    val expectedEpisodeTitle: String? = null
)

enum class EvidenceType { EPISODE_PAGE, SERIES_PAGE, RELEASE_CALENDAR, SEARCH_RESULT }

sealed interface ProviderAvailabilityResult {
    val provider: StreamingProvider
    val checkedAt: Instant

    data class Available(
        override val provider: StreamingProvider,
        val episodeNumber: Int?,
        val titleFound: String?,
        val pageUrl: String,
        val evidenceType: EvidenceType,
        override val checkedAt: Instant
    ) : ProviderAvailabilityResult
    data class TitleFoundEpisodeMissing(
        override val provider: StreamingProvider,
        val expectedEpisode: Int?,
        val pageUrl: String,
        override val checkedAt: Instant
    ) : ProviderAvailabilityResult
    data class TitleNotFound(override val provider: StreamingProvider, override val checkedAt: Instant) : ProviderAvailabilityResult
    data class LoginRequired(override val provider: StreamingProvider, override val checkedAt: Instant) : ProviderAvailabilityResult
    data class RegionBlocked(override val provider: StreamingProvider, override val checkedAt: Instant) : ProviderAvailabilityResult
    data class TemporarilyUnavailable(override val provider: StreamingProvider, val reason: String?, override val checkedAt: Instant) : ProviderAvailabilityResult
    data class ParseError(override val provider: StreamingProvider, override val checkedAt: Instant) : ProviderAvailabilityResult
    data class NetworkError(override val provider: StreamingProvider, override val checkedAt: Instant) : ProviderAvailabilityResult
}

interface ProviderChecker {
    val provider: StreamingProvider
    suspend fun checkAvailability(request: ProviderCheckRequest): ProviderAvailabilityResult
}
