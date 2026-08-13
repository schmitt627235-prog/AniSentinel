package de.anisentinel.app.data.provider

enum class CrunchyrollNormalizationStatus { MATCHED, AMBIGUOUS, NOT_FOUND, PARSER_FAILED }

data class CrunchyrollNormalizedEpisode(
    val status: CrunchyrollNormalizationStatus,
    val episode: CrunchyrollCatalogEpisode? = null,
    val localSeasonNumber: Int? = null,
    val localEpisodeNumber: Int? = null,
    val globalEpisodeNumber: Int? = null,
    val diagnostic: String? = null
)

object CrunchyrollEpisodeNormalizer {
    fun resolve(
        episodes: List<CrunchyrollCatalogEpisode>,
        requestedSeason: Int?,
        requestedEpisode: Int,
        expectedLanguage: String?,
        knownSeasonId: String? = null,
        knownEpisodeId: String? = null
    ): CrunchyrollNormalizedEpisode {
        if (episodes.isEmpty()) return CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.NOT_FOUND, diagnostic = "EMPTY_CATALOG")

        knownEpisodeId?.let { id ->
            val matches = episodes.filter { it.episodeId == id && (expectedLanguage == null || expectedLanguage in it.releaseLanguages) }
            return unique(matches, requestedEpisode, "EPISODE_ID")
        }

        val seasonScoped = when {
            knownSeasonId != null -> episodes.filter { it.seasonId == knownSeasonId }
            requestedSeason != null -> {
                val seasonIds = episodes.filter { it.seasonNumber == requestedSeason }.map { it.seasonId }.distinct()
                if (seasonIds.size > 1) return CrunchyrollNormalizedEpisode(
                    CrunchyrollNormalizationStatus.AMBIGUOUS,
                    diagnostic = "MULTIPLE_SEASON_IDS_FOR_NUMBER"
                )
                episodes.filter { it.seasonNumber == requestedSeason }
            }
            else -> episodes
        }
        if (seasonScoped.isEmpty()) return CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.NOT_FOUND, diagnostic = "SEASON_NOT_FOUND")

        val languageScoped = seasonScoped.filter { expectedLanguage == null || expectedLanguage in it.releaseLanguages }
        if (languageScoped.isEmpty()) return CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.NOT_FOUND, diagnostic = "LANGUAGE_NOT_FOUND")

        val exact = languageScoped.filter { it.episodeNumber == requestedEpisode }
        if (exact.isNotEmpty()) return unique(exact, requestedEpisode, "LOCAL_OR_PROVIDER_NUMBER")

        // Some catalogue seasons expose global numbers. Only derive a local ordinal when
        // every concrete episode has a stable, unique sequence and the requested ordinal
        // identifies exactly one episode. Different audio variants collapse by episode ID.
        val concrete = languageScoped.distinctBy { it.episodeId }
            .sortedWith(compareBy<CrunchyrollCatalogEpisode> { it.sequenceNumber ?: Int.MAX_VALUE }
                .thenBy { it.availableAt }
                .thenBy { it.episodeId })
        if (concrete.any { it.sequenceNumber == null } || concrete.mapNotNull { it.sequenceNumber }.distinct().size != concrete.size) {
            return CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.NOT_FOUND, diagnostic = "NO_SAFE_LOCAL_ORDINAL")
        }
        val derived = concrete.getOrNull(requestedEpisode - 1)
            ?: return CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.NOT_FOUND, diagnostic = "LOCAL_ORDINAL_OUT_OF_RANGE")
        return CrunchyrollNormalizedEpisode(
            CrunchyrollNormalizationStatus.MATCHED, derived, derived.seasonNumber,
            requestedEpisode, derived.episodeNumber, "SEQUENCE_DERIVED_LOCAL_NUMBER"
        )
    }

    private fun unique(matches: List<CrunchyrollCatalogEpisode>, localEpisode: Int, diagnostic: String): CrunchyrollNormalizedEpisode {
        val unique = matches.distinctBy { it.episodeId }
        return when (unique.size) {
            0 -> CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.NOT_FOUND, diagnostic = diagnostic)
            1 -> unique.single().let {
                CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.MATCHED, it, it.seasonNumber, localEpisode, it.episodeNumber, diagnostic)
            }
            else -> CrunchyrollNormalizedEpisode(CrunchyrollNormalizationStatus.AMBIGUOUS, diagnostic = "${diagnostic}_MULTIPLE_EPISODES")
        }
    }
}
