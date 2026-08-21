package de.anisentinel.app.ui

import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity

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
        else -> null
    }

    fun canonicalName(provider: String): String = selectableReference(provider) ?: provider

    private fun normalize(value: String) = value.trim().lowercase()
        .replace(" amazon channel", "")
        .replace(Regex("[^a-z0-9]+"), "")
}
