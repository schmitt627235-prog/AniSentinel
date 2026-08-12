package de.anisentinel.app.domain.model

import java.time.Instant

enum class ReleaseStatus {
    SCHEDULED,
    RELEASE_TIME_REACHED,
    PRECHECK,
    CHECKING,
    PENDING_CONFIRMATION,
    NOT_AVAILABLE_YET,
    PROVIDER_CHECK_FAILED,
    AVAILABLE,
    NOT_FOUND,
    DELAYED_UNCONFIRMED,
    POSSIBLY_POSTPONED,
    OFFICIALLY_POSTPONED,
    UNKNOWN,
    STOPPED
}

enum class LanguagePreference { SUB, DUB, BOTH }

enum class MetadataSource { ANIWORLD, ANILIST, ANISEARCH, TEST }

enum class StreamingProvider {
    CRUNCHYROLL, NETFLIX, PRIME_VIDEO, DISNEY_PLUS, ADN, ANIVERSE, AKIBA_PASS, OTHER
}

enum class AvailabilityStatus { AVAILABLE, NOT_FOUND, UNKNOWN }

data class ProviderAvailability(
    val provider: StreamingProvider,
    val episodeNumber: Int?,
    val status: AvailabilityStatus,
    val pageUrl: String?,
    val checkedAt: Instant
)

data class WatchPhase(
    val startOffsetSeconds: Long,
    val endOffsetSeconds: Long?,
    val intervalSeconds: Long
) {
    init {
        require(startOffsetSeconds >= 0) { "Start must not be negative" }
        require(endOffsetSeconds == null || endOffsetSeconds > startOffsetSeconds) {
            "End must be after start"
        }
        require(intervalSeconds >= 30) { "Intervals shorter than 30 seconds are unsupported" }
    }

    fun contains(offsetSeconds: Long): Boolean =
        offsetSeconds >= startOffsetSeconds &&
            (endOffsetSeconds == null || offsetSeconds < endOffsetSeconds)
}

data class WatchProfile(
    val id: String,
    val phases: List<WatchPhase>,
    val stopAfterSeconds: Long,
    val liveMonitoringAllowed: Boolean
) {
    init {
        require(stopAfterSeconds > 0)
        require(phases.isNotEmpty())
    }

    fun phaseAt(offsetSeconds: Long): WatchPhase? =
        if (offsetSeconds >= stopAfterSeconds) null else phases.firstOrNull { it.contains(offsetSeconds) }
}

data class Anime(
    val id: String,
    val title: String,
    val subtitle: String,
    val provider: String,
    val expectedReleaseAt: Instant?,
    val episode: Int,
    val status: ReleaseStatus,
    val accentSeed: Int,
    val coverUrl: String? = null,
    val description: String? = null,
    val source: String = "FAKE",
    val metadataSource: MetadataSource =
        if (source == "ANILIST") MetadataSource.ANILIST else MetadataSource.TEST,
    val metadataSourceUrl: String? = null,
    val streamingAvailability: List<ProviderAvailability> = emptyList(),
    val totalEpisodes: Int? = null,
    val nextEpisodeNumber: Int? = null,
    val releaseTimePrecision: String = "EXACT"
)
