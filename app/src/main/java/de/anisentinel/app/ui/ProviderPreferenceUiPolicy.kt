package de.anisentinel.app.ui

import de.anisentinel.app.data.local.ProviderSeasonMappingEntity

/** Keeps preference controls limited to providers verified for German availability. */
object ProviderPreferenceUiPolicy {
    fun providersForSeason(
        seasonNumber: Int,
        mappings: List<ProviderSeasonMappingEntity>
    ): List<String> = mappings
        .filter {
            it.canonicalSeasonNumber == seasonNumber &&
                it.region.equals("DE", ignoreCase = true) &&
                it.available
        }
        .map { it.provider }
        .distinctBy(::normalize)
        .sortedBy(::normalize)

    fun providersForAnime(mappings: List<ProviderSeasonMappingEntity>): List<String> = mappings
        .filter { it.region.equals("DE", ignoreCase = true) && it.available }
        .map { it.provider }
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
        mappings: List<ProviderSeasonMappingEntity>
    ): Boolean = preference != null && providersForAnime(mappings)
        .none { normalize(it) == normalize(preference) }

    private fun normalize(value: String) = value.trim().lowercase()
        .replace(" amazon channel", "")
        .replace(Regex("[^a-z0-9]+"), "")
}
