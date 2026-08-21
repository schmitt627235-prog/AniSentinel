package de.anisentinel.app.data.provider

import de.anisentinel.app.data.local.ProviderPreferenceEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSelectionPolicyTest {
    private val references = listOf(ref("ADN"), ref("Crunchyroll"), ref("ANIVERSE"))

    @Test fun crunchyrollWinsAutomaticallyOnlyWhenConcreteSeasonIsConfirmed() {
        val result = ProviderSelectionPolicy.select(2, references, listOf(mapping(2, "Crunchyroll")), emptyList())
        assertEquals("Crunchyroll", result.references.single().provider)
        assertEquals("AUTO_CRUNCHYROLL", result.reason)
    }

    @Test fun explicitSeasonPreferenceOverridesCrunchyroll() {
        val result = ProviderSelectionPolicy.select(
            2, references, listOf(mapping(2, "Crunchyroll"), mapping(2, "ADN")),
            listOf(ProviderPreferenceEntity("anime", 2, "ADN", 1))
        )
        assertEquals("ADN", result.references.single().provider)
    }

    @Test fun animePreferenceOnlyAppliesWhenProviderOffersSeason() {
        val result = ProviderSelectionPolicy.select(
            2, references, listOf(mapping(2, "Crunchyroll")),
            listOf(ProviderPreferenceEntity("anime", 0, "ADN", 1))
        )
        assertEquals("Crunchyroll", result.references.single().provider)
    }

    @Test fun providerGapCreatesNoPhantomProvider() {
        val result = ProviderSelectionPolicy.select(1, references, listOf(mapping(1, "ADN", available = false)), emptyList())
        assertTrue(result.references.isEmpty())
        assertEquals("NO_CONFIRMED_DACH_PROVIDER", result.reason)
    }

    private fun ref(provider: String) = ProviderReferenceEntity("anime", provider, "https://example/$provider", "TEST", null, 1, "DE")
    private fun mapping(season: Int, provider: String, available: Boolean = true) = ProviderSeasonMappingEntity(
        "anime", season, provider, season, provider, null, "https://example/$provider", "DE", available, 1
    )
}
