package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.domain.release.AvailabilityEvidence
import de.anisentinel.app.domain.release.ReleaseIdentity

data class PreviousReleaseDisplay(val release: EpisodeReleaseEntity, val inferred: Boolean)

object ReleaseDisplayResolver {
    private const val WEEK_SECONDS = 7 * 24 * 60 * 60L
    private const val PLAUSIBLE_SEASON_WINDOW = 26 * WEEK_SECONDS

    fun previousFor(
        releases: List<EpisodeReleaseEntity>,
        next: EpisodeReleaseEntity?,
        nowEpoch: Long,
        regularScheduleAnchorEpoch: Long? = null,
        checks: List<EpisodeProviderAvailabilityEntity> = emptyList()
    ): PreviousReleaseDisplay? {
        val past = releases.filter { (it.expectedAt ?: Long.MAX_VALUE) <= nowEpoch }
        val confirmed = releases.mapNotNull { release ->
            val identity = ReleaseIdentity.from(release)
            val evidence = checks.filter { check ->
                releases.any { candidate ->
                    candidate.sourceReleaseId == check.releaseId &&
                        ReleaseIdentity.from(candidate).sameEpisode(identity)
                }
            }.filter { it.firstAvailableAt != null || it.status.startsWith("AVAILABLE_") }
                .maxByOrNull { AvailabilityEvidence.from(it).strength }
            evidence?.let { release to it }
        }
        val strongestLatest = confirmed.maxWithOrNull(
            compareBy<Pair<EpisodeReleaseEntity, EpisodeProviderAvailabilityEntity>> {
                it.first.seasonNumber ?: 1
            }.thenBy { it.first.episodeNumber ?: 0 }
                .thenBy { AvailabilityEvidence.from(it.second).strength }
                .thenBy { it.second.lastCheckedAt }
        )?.first
        if (next == null) return (strongestLatest ?: past.maxByOrNull { it.expectedAt ?: Long.MIN_VALUE })
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
        val confirmedForCycle = strongestLatest?.takeIf {
            it.seasonNumber == next.seasonNumber && it.releaseLanguage == next.releaseLanguage &&
                (it.episodeNumber ?: Int.MAX_VALUE) < (next.episodeNumber ?: Int.MIN_VALUE)
        }
        if (confirmedForCycle != null) return PreviousReleaseDisplay(confirmedForCycle, false)
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

    fun latestConfirmed(
        releases: List<EpisodeReleaseEntity>,
        checks: List<EpisodeProviderAvailabilityEntity>,
        season: Int? = null,
        language: String? = null
    ): EpisodeReleaseEntity? {
        return releases.asSequence()
            .filter { release -> season == null || (release.seasonNumber ?: 1) == season }
            .filter { release -> language == null || release.releaseLanguage == language }
            .mapNotNull { release ->
                val identity = ReleaseIdentity.from(release)
                val semanticIds = releases.asSequence()
                    .filter { ReleaseIdentity.from(it).sameEpisode(identity) }
                    .mapTo(mutableSetOf()) { it.sourceReleaseId }
                val evidence = checks.filter { it.releaseId in semanticIds }
                    .filter { it.firstAvailableAt != null || it.status.startsWith("AVAILABLE_") }
                    .maxByOrNull { AvailabilityEvidence.from(it).strength }
                // AVAILABLE is itself a persisted provider/watcher result. Calendar rows are
                // deliberately provider-neutral and must still drive current-season progress.
                val persistedConfirmed = release.releaseStatus.startsWith("AVAILABLE")
                if (evidence == null && !persistedConfirmed) null else Triple(
                    release,
                    evidence?.let(AvailabilityEvidence::from)?.strength
                        ?: AvailabilityEvidence.HISTORICAL_PROVIDER_CATALOG.strength,
                    evidence?.lastCheckedAt ?: release.fetchedAt
                )
            }
            .maxWithOrNull(
                compareBy<Triple<EpisodeReleaseEntity, Int, Long>> { it.first.seasonNumber ?: 1 }
                    .thenBy { it.first.episodeNumber ?: 0 }
                    .thenBy { it.second }
                    .thenBy { it.third }
            )?.first
    }
}
