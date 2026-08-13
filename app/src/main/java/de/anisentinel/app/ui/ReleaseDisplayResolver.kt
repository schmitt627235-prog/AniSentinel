package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity

data class PreviousReleaseDisplay(val release: EpisodeReleaseEntity, val inferred: Boolean)

object ReleaseDisplayResolver {
    private const val WEEK_SECONDS = 7 * 24 * 60 * 60L
    private const val PLAUSIBLE_SEASON_WINDOW = 26 * WEEK_SECONDS

    fun previousFor(
        releases: List<EpisodeReleaseEntity>,
        next: EpisodeReleaseEntity?,
        nowEpoch: Long,
        regularScheduleAnchorEpoch: Long? = null
    ): PreviousReleaseDisplay? {
        val past = releases.filter { (it.expectedAt ?: Long.MAX_VALUE) <= nowEpoch }
        if (next == null) return past.maxByOrNull { it.expectedAt ?: Long.MIN_VALUE }
            ?.let { PreviousReleaseDisplay(it, false) }

        val nextAt = next.expectedAt ?: return null
        val cadenceAnchor = regularScheduleAnchorEpoch ?: nextAt
        val previous = past.asSequence().filter {
            it.seasonNumber == next.seasonNumber &&
                it.releaseLanguage == next.releaseLanguage &&
                (it.episodeNumber ?: Int.MAX_VALUE) < (next.episodeNumber ?: Int.MIN_VALUE) &&
                nextAt - (it.expectedAt ?: Long.MIN_VALUE) in 1..PLAUSIBLE_SEASON_WINDOW
        }.maxWithOrNull(compareBy<EpisodeReleaseEntity> { it.episodeNumber ?: Int.MIN_VALUE }
            .thenBy { it.expectedAt ?: Long.MIN_VALUE })
        if (previous != null) return PreviousReleaseDisplay(previous, false)

        val episode = next.episodeNumber?.takeIf { it > 1 } ?: return null
        return PreviousReleaseDisplay(
            next.copy(
                sourceReleaseId = "display-inference:${next.sourceReleaseId}",
                episodeNumber = episode - 1,
                expectedAt = cadenceAnchor - WEEK_SECONDS,
                listedAt = next.listedAt?.let { cadenceAnchor - WEEK_SECONDS + (it - nextAt) },
                provider = null,
                providerUrl = null,
                releaseStatus = "EXPECTED_UNCONFIRMED",
                isHistoricalImport = false,
                historicalReleasedAt = null,
                metadataSource = "SCHEDULE_CADENCE_INFERENCE"
            ),
            true
        )
    }

    fun isPlausibleForCurrentSeason(
        release: EpisodeReleaseEntity,
        next: EpisodeReleaseEntity?
    ): Boolean {
        if (next == null) return true
        val releaseAt = release.expectedAt ?: return false
        val nextAt = next.expectedAt ?: return false
        return release.seasonNumber == next.seasonNumber &&
            kotlin.math.abs(nextAt - releaseAt) <= PLAUSIBLE_SEASON_WINDOW
    }
}
