package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.ReleasePostponementEntity
import de.anisentinel.app.domain.release.AvailabilityEvidence
import de.anisentinel.app.domain.release.ReleaseIdentity

data class PreviousReleaseDisplay(val release: EpisodeReleaseEntity, val inferred: Boolean)

data class ReleaseDisplayState(
    val identity: ReleaseIdentity,
    val release: EpisodeReleaseEntity,
    val originalExpectedAt: Long?,
    val effectiveExpectedAt: Long,
    val postponement: ReleasePostponementEntity?,
    val countdownTarget: Long = effectiveExpectedAt
)

object ReleaseDisplayResolver {
    private const val WEEK_SECONDS = 7 * 24 * 60 * 60L
    private const val PLAUSIBLE_SEASON_WINDOW = 26 * WEEK_SECONDS

    fun effectiveReleases(releases: List<EpisodeReleaseEntity>, postponements: List<ReleasePostponementEntity>): List<EpisodeReleaseEntity> {
        val shifted = releases.map { release ->
            val identity = ReleaseIdentity.from(release)
            val shift = postponements.asSequence().filter { it.isActive && it.newExpectedAt != null }
                .filter { ReleaseIdentity.from(it)?.sameEpisodeAllowingUnspecifiedLanguage(identity) == true }
                .maxByOrNull { it.revision }
            shift?.newExpectedAt?.let { release.copy(expectedAt = it, releaseStatus = "POSTPONED") } ?: release
        }
        val represented = shifted.map { ReleaseIdentity.from(it) }
        val synthetic = postponements.mapNotNull { shift ->
            val identity = ReleaseIdentity.from(shift) ?: return@mapNotNull null
            val target = shift.newExpectedAt ?: return@mapNotNull null
            if (!shift.isActive || represented.any { it.sameEpisodeAllowingUnspecifiedLanguage(identity) }) return@mapNotNull null
            EpisodeReleaseEntity(
                sourceReleaseId = "canonical-postponement:${shift.postponementId}", animeId = identity.animeId,
                episodeNumber = identity.episode, episodeTitle = null, expectedAt = target, provider = null, providerUrl = null,
                metadataSource = shift.source, sourceUrl = shift.sourceUrl, fetchedAt = shift.lastCheckedAt,
                seasonNumber = identity.season, releaseStatus = "POSTPONED", releaseLanguage = identity.language
            )
        }
        return (shifted + synthetic).distinctBy {
            listOf(it.animeId, it.seasonNumber ?: 1, it.episodeNumber ?: 0, it.releaseLanguage, it.expectedAt)
        }
    }

    fun nextFor(
        releases: List<EpisodeReleaseEntity>, postponements: List<ReleasePostponementEntity>,
        checks: List<EpisodeProviderAvailabilityEntity> = emptyList(), nowEpoch: Long,
        focusedSeason: Int? = null, focusedEpisode: Int? = null, focusedLanguage: String? = null
    ): ReleaseDisplayState? {
        val effective = effectiveReleases(releases, postponements)
        val future = effective.filter { (it.expectedAt ?: Long.MIN_VALUE) > nowEpoch }
        val focused = focusedEpisode?.let { episode -> future.firstOrNull {
            it.episodeNumber == episode && (focusedSeason == null || it.seasonNumber == focusedSeason) &&
                (focusedLanguage == null || it.releaseLanguage == focusedLanguage)
        } }
        val provisional = focused ?: future.minByOrNull { it.expectedAt ?: Long.MAX_VALUE }
        val latest = latestConfirmed(releases, checks, provisional?.seasonNumber, null)
        val selected = focused ?: future.asSequence().filter { candidate ->
            latest == null || (candidate.seasonNumber ?: 1) > (latest.seasonNumber ?: 1) ||
                ((candidate.seasonNumber ?: 1) == (latest.seasonNumber ?: 1) &&
                    (candidate.episodeNumber ?: 0) > (latest.episodeNumber ?: 0))
        }.minWithOrNull(compareBy<EpisodeReleaseEntity> { it.expectedAt ?: Long.MAX_VALUE }
            .thenBy { it.episodeNumber ?: Int.MAX_VALUE }) ?: provisional ?: return null
        val identity = ReleaseIdentity.from(selected)
        val shift = postponements.asSequence().filter { it.isActive }
            .filter { ReleaseIdentity.from(it)?.sameEpisodeAllowingUnspecifiedLanguage(identity) == true }
            .maxByOrNull { it.revision }
        val target = selected.expectedAt ?: return null
        return ReleaseDisplayState(identity, selected, shift?.originalExpectedAt, target, shift)
    }

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
