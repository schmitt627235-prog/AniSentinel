package de.anisentinel.app.data.anisearch

import java.time.LocalDate

enum class ReleaseSourceKind {
    ANIME_RADAR,
    ANIWORLD_CALENDAR,
    ANIWORLD_SCHEDULE_CHANGE,
    ANILIST_FALLBACK,
    ANISEARCH_METADATA,
    LOCAL_DIAGNOSTIC,
    PROVIDER_CONFIRMATION
}

data class SourceEpisodeRelease(
    val sourceKind: ReleaseSourceKind,
    val sourceReleaseId: String,
    val anisearchId: String?,
    val aniListId: Int? = null,
    val titleGerman: String?,
    val episodeNumber: Int?,
    val releaseAtEpochSeconds: Long?,
    val provider: String?,
    val sourceUrl: String,
    val providerUrl: String?,
    val titleEnglish: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val totalEpisodes: Int? = null
)

enum class SourceFailureReason { HTTP, RATE_LIMITED, NETWORK, INVALID_RESPONSE, PAGINATION_LIMIT, NOT_CONFIGURED }

sealed interface SourceCalendarFetchResult {
    data class Complete(
        val releases: List<SourceEpisodeRelease>,
        val sourceKind: ReleaseSourceKind = releases.firstOrNull()?.sourceKind
            ?: ReleaseSourceKind.ANILIST_FALLBACK
    ) : SourceCalendarFetchResult
    data class Unavailable(
        val reason: SourceFailureReason,
        val diagnostic: String,
        val retryNotBeforeEpochSeconds: Long? = null
    ) : SourceCalendarFetchResult
}

interface ReleaseCalendarSource {
    suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate): SourceCalendarFetchResult
}

interface AniSearchCalendarSource : ReleaseCalendarSource

/** Safe default until a real public page and stored fixtures have been verified. */
object UnverifiedAniSearchCalendarSource : AniSearchCalendarSource {
    override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate) =
        SourceCalendarFetchResult.Unavailable(
            SourceFailureReason.NOT_CONFIGURED,
            "No verified public AniSearch calendar source and fixture configured"
        )
}
