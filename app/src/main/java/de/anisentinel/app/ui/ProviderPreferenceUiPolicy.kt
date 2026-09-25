package de.anisentinel.app.ui

import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity

/** Keeps preference controls limited to providers verified for German availability. */
object ProviderPreferenceUiPolicy {
    data class CatalogSeason(
        val provider: String,
        val catalogId: String,
        val seasonNumber: Int,
        val label: String?
    ) {
        val key: String = "$provider|$catalogId|$seasonNumber"
    }

    fun catalogSeasonsForProvider(
        provider: String?,
        mappings: List<ProviderSeasonMappingEntity>
    ): List<CatalogSeason> = mappings.asSequence()
        .filter {
            it.available && it.region.equals("DE", ignoreCase = true) &&
                (provider == null || canonicalName(it.provider).equals(provider, ignoreCase = true))
        }
        .map {
            CatalogSeason(
                provider = canonicalName(it.provider),
                catalogId = it.providerCatalogId,
                seasonNumber = it.canonicalSeasonNumber,
                label = it.providerSeasonLabel
            )
        }
        .filter { it.seasonNumber > 0 }
        .distinctBy { it.key }
        .sortedWith(compareBy<CatalogSeason> { it.provider.lowercase() }
            .thenBy { it.catalogId.lowercase() }.thenBy { it.seasonNumber })
        .toList()

    fun seasonsForProvider(
        provider: String?,
        canonicalSeasons: List<Int>,
        mappings: List<ProviderSeasonMappingEntity>,
        releases: List<EpisodeReleaseEntity>
    ): List<Int> {
        val mapped = mappings.asSequence()
            .filter {
                it.available && it.region.equals("DE", ignoreCase = true) &&
                    (provider == null || canonicalName(it.provider).equals(provider, ignoreCase = true))
            }
            .map { it.canonicalSeasonNumber }
        val catalogued = releases.asSequence()
            .filter {
                it.isHistoricalImport && !it.provider.isNullOrBlank() &&
                    (provider == null || canonicalName(it.provider.orEmpty()).equals(provider, ignoreCase = true))
            }
            .mapNotNull { it.seasonNumber }
        return (mapped + catalogued)
            .filter { it > 0 }
            .distinct()
            .sorted()
            .toList()
    }

    fun releasesForProviderSeason(
        releases: List<EpisodeReleaseEntity>,
        seasonNumber: Int,
        provider: String?,
        providerCatalogId: String? = null
    ): List<EpisodeReleaseEntity> = releases.filter { release ->
        (release.seasonNumber ?: 1) == seasonNumber &&
            release.isHistoricalImport && !release.provider.isNullOrBlank() &&
            (provider == null || canonicalName(release.provider.orEmpty()).equals(provider, ignoreCase = true)) &&
            (providerCatalogId == null || releaseBelongsToCatalog(release, providerCatalogId))
    }

    internal fun releaseBelongsToCatalog(release: EpisodeReleaseEntity, catalogId: String): Boolean =
        release.sourceReleaseId.contains(":$catalogId:", ignoreCase = true) ||
            release.sourceUrl?.contains(catalogId, ignoreCase = true) == true

    fun episodeCatalogRows(
        releases: List<EpisodeReleaseEntity>,
        useHistoricalEpisodes: Boolean
    ): List<EpisodeReleaseEntity> = releases.filter { release ->
        useHistoricalEpisodes || release.isHistoricalImport ||
            (!release.sourceReleaseId.startsWith("crunchyroll-history:") &&
                !release.sourceReleaseId.startsWith("adn-history:"))
    }

    /** An undated provider catalogue row may be shown only with direct positive evidence. */
    fun isVisibleCatalogEpisode(release: EpisodeReleaseEntity, now: Long): Boolean =
        (release.expectedAt != null && release.expectedAt <= now) ||
            (release.isHistoricalImport && release.expectedAt == null &&
                release.releaseStatus.startsWith("AVAILABLE"))

    fun providersForSeason(
        seasonNumber: Int,
        mappings: List<ProviderSeasonMappingEntity>
    ): List<String> = mappings
        .filter {
            it.canonicalSeasonNumber == seasonNumber &&
                it.region.equals("DE", ignoreCase = true) &&
                it.available
        }
        .map { canonicalName(it.provider) }
        .distinctBy(::normalize)
        .sortedBy(::normalize)

    fun providersForAnime(
        mappings: List<ProviderSeasonMappingEntity>,
        references: List<ProviderReferenceEntity> = emptyList()
    ): List<String> = (mappings
        .filter { it.region.equals("DE", ignoreCase = true) && it.available }
        .map { canonicalName(it.provider) } + references.mapNotNull { selectableReference(it.provider) })
        .distinctBy(::normalize)
        .sortedBy(::normalize)

    fun isInvalidSeasonPreference(
        preference: String?,
        seasonNumber: Int,
        mappings: List<ProviderSeasonMappingEntity>
    ): Boolean = preference != null && providersForSeason(seasonNumber, mappings)
        .none { normalize(it) == normalize(preference) }

    fun isInvalidAnimePreference(
        preference: String?,
        mappings: List<ProviderSeasonMappingEntity>,
        references: List<ProviderReferenceEntity> = emptyList()
    ): Boolean = preference != null && providersForAnime(mappings, references)
        .none { normalize(it) == normalize(preference) }

    /** Providers with an implemented public metadata path are selectable for every title. */
    fun selectableReference(provider: String): String? = when {
        provider.contains("Crunchyroll", true) -> "Crunchyroll"
        provider.equals("ADN", true) || provider.contains("Animation Digital Network", true) &&
            !provider.contains("Amazon", true) -> "ADN"
        provider.contains("Netflix", true) -> "Netflix"
        provider.contains("Disney", true) -> "Disney+"
        provider.contains("Aniverse", true) -> if (provider.contains("Amazon", true))
            "ANIVERSE Amazon Channel" else "ANIVERSE"
        provider.contains("Akiba", true) -> "AKIBA PASS"
        else -> null
    }

    fun canonicalName(provider: String): String = selectableReference(provider) ?: provider

    private fun normalize(value: String) = value.trim().lowercase()
        .replace(" amazon channel", "")
        .replace(Regex("[^a-z0-9]+"), "")
}
