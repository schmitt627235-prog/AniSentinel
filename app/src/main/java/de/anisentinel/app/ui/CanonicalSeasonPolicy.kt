package de.anisentinel.app.ui

import de.anisentinel.app.data.local.AnimeSeasonEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.ProviderSeasonMappingEntity

/** Prevents unverified legacy release rows from permanently creating season chips. */
object CanonicalSeasonPolicy {
    fun visibleSeasons(
        seasons: List<AnimeSeasonEntity>,
        mappings: List<ProviderSeasonMappingEntity>,
        releases: List<EpisodeReleaseEntity> = emptyList(),
        activeSeasonNumber: Int? = null
    ): List<Int> {
        val verified = seasons
            .filterNot { it.source == "RELEASE_BACKFILL" }
            .map { it.canonicalSeasonNumber }
        val providerConfirmed = mappings
            .filter { it.region == "DE" && it.lastConfirmedAt > 0 }
            .map { it.canonicalSeasonNumber }
        val providerHistoryConfirmed = releases
            .filter {
                it.isHistoricalImport &&
                    !it.provider.isNullOrBlank() &&
                    it.metadataSource != "ANIWORLD_CALENDAR" &&
                    (!it.providerUrl.isNullOrBlank() || !it.sourceUrl.isNullOrBlank())
            }
            .mapNotNull { it.seasonNumber }
        val authoritative = (
            verified + providerConfirmed + providerHistoryConfirmed + listOfNotNull(activeSeasonNumber)
        ).filter { it > 0 }.distinct().sorted()
        if (authoritative.isNotEmpty()) return authoritative

        // A single legacy season is a safe compatibility fallback. Multiple legacy-only
        // rows are ambiguous and must not become phantom season chips.
        val legacy = seasons.filter { it.source == "RELEASE_BACKFILL" }
            .map { it.canonicalSeasonNumber }.filter { it > 0 }.distinct().sorted()
        return legacy.takeIf { it.size == 1 }.orEmpty()
    }
}
