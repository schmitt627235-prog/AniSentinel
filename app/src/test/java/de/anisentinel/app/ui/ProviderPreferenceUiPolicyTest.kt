package de.anisentinel.app.ui

import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPreferenceUiPolicyTest {
    @Test fun seasonOptionsContainOnlyConfirmedGermanMappings() {
        val mappings = listOf(
            mapping(1, "Crunchyroll", available = true),
            mapping(1, "ADN", available = false),
            mapping(1, "Netflix", available = true, region = "US"),
            mapping(2, "ADN", available = true)
        )

        assertEquals(listOf("Crunchyroll"), ProviderPreferenceUiPolicy.providersForSeason(1, mappings))
    }

    @Test fun animeOptionsAreUnionOfConfirmedGermanMappings() {
        val mappings = listOf(
            mapping(1, "Crunchyroll", true),
            mapping(2, "ADN", true),
            mapping(3, "Netflix", false)
        )

        assertEquals(listOf("ADN", "Crunchyroll"), ProviderPreferenceUiPolicy.providersForAnime(mappings))
    }

    @Test fun unavailableSavedSeasonPreferenceIsInvalid() {
        val mappings = listOf(mapping(2, "ADN", false), mapping(2, "Crunchyroll", true))

        assertTrue(ProviderPreferenceUiPolicy.isInvalidSeasonPreference("ADN", 2, mappings))
        assertFalse(ProviderPreferenceUiPolicy.isInvalidSeasonPreference("Crunchyroll", 2, mappings))
    }

    @Test fun animePreferenceBecomesInvalidWhenProviderHasNoConfirmedGermanSeason() {
        val mappings = listOf(mapping(1, "ADN", false), mapping(1, "Crunchyroll", true))

        assertTrue(ProviderPreferenceUiPolicy.isInvalidAnimePreference("ADN", mappings))
        assertFalse(ProviderPreferenceUiPolicy.isInvalidAnimePreference("Crunchyroll", mappings))
    }

    private fun mapping(season: Int, provider: String, available: Boolean, region: String = "DE") =
        ProviderSeasonMappingEntity("anime", season, provider, season, null, null, null, region, available, 1)
}
