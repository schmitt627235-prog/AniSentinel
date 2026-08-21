package de.anisentinel.app.data.provider

import de.anisentinel.app.data.local.ProviderPreferenceEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.local.ProviderSeasonMappingEntity

object ProviderSelectionPolicy {
    data class Result(val references: List<ProviderReferenceEntity>, val reason: String)

    fun select(
        seasonNumber: Int,
        references: List<ProviderReferenceEntity>,
        mappings: List<ProviderSeasonMappingEntity>,
        preferences: List<ProviderPreferenceEntity>
    ): Result {
        if (references.isEmpty()) return Result(emptyList(), "NO_PROVIDER_REFERENCE")
        val availableProviders = mappings.filter {
            it.canonicalSeasonNumber == seasonNumber && it.region == "DE" && it.available
        }.map { normalize(it.provider) }.toSet()
        fun reference(provider: String) = references.firstOrNull { normalize(it.provider) == normalize(provider) }

        preferences.firstOrNull { it.seasonNumber == seasonNumber }
            ?.let { preference -> reference(preference.provider)?.let { return Result(listOf(it), "USER_SEASON") } }
        preferences.firstOrNull { it.seasonNumber == 0 && normalize(it.provider) in availableProviders }
            ?.let { preference -> reference(preference.provider)?.let { return Result(listOf(it), "USER_ANIME") } }
        references.firstOrNull {
            normalize(it.provider) == "crunchyroll" && normalize(it.provider) in availableProviders
        }?.let { return Result(listOf(it), "AUTO_CRUNCHYROLL") }
        references.firstOrNull { normalize(it.provider) in availableProviders }
            ?.let { return Result(listOf(it), "AUTO_CONFIRMED_DACH") }

        // No provider-season mapping exists yet: probe references once to discover the mapping.
        return if (mappings.none { it.canonicalSeasonNumber == seasonNumber }) {
            Result(references, "DISCOVERY")
        } else Result(emptyList(), "NO_CONFIRMED_DACH_PROVIDER")
    }

    private fun normalize(value: String) = value.lowercase()
        .replace(" amazon channel", "")
        .replace(Regex("[^a-z0-9]+"), "")
}
