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

    @Test fun invalidSeasonPreferenceFallsBackToConfirmedCrunchyroll() {
        val result = ProviderSelectionPolicy.select(
            2, references, listOf(mapping(2, "Crunchyroll")),
            listOf(ProviderPreferenceEntity("anime", 2, "ADN", 1))
        )
        assertEquals("Crunchyroll", result.references.single().provider)
        assertEquals("AUTO_CRUNCHYROLL", result.reason)
    }

    @Test fun seasonOverrideWinsWhileAnimePreferenceAppliesToOtherSeasons() {
        val mappings = listOf(
            mapping(1, "ADN"), mapping(1, "Crunchyroll"),
            mapping(2, "ADN"), mapping(2, "Crunchyroll")
        )
        val preferences = listOf(
            ProviderPreferenceEntity("anime", 0, "ADN", 1),
            ProviderPreferenceEntity("anime", 2, "Crunchyroll", 2)
        )
        assertEquals("ADN", ProviderSelectionPolicy.select(1, references, mappings, preferences).references.single().provider)
        assertEquals("Crunchyroll", ProviderSelectionPolicy.select(2, references, mappings, preferences).references.single().provider)
    }

    @Test fun providerGapCreatesNoPhantomProvider() {
        val result = ProviderSelectionPolicy.select(1, references, listOf(mapping(1, "ADN", available = false)), emptyList())
        assertTrue(result.references.isEmpty())
        assertEquals("NO_CONFIRMED_DACH_PROVIDER", result.reason)
    }

    @Test fun discoveryProbesCrunchyrollBeforeOtherUnconfirmedReferences() {
        val result = ProviderSelectionPolicy.select(
            1,
            listOf(ref("Animation Digital Network"), ref("Crunchyroll"), ref("Netflix")),
            emptyList(),
            emptyList()
        )

        assertEquals("DISCOVERY", result.reason)
        assertEquals("Crunchyroll", result.references.first().provider)
        assertEquals(3, result.references.size)
    }

    private fun ref(provider: String) = ProviderReferenceEntity("anime", provider, "https://example/$provider", "TEST", null, 1, "DE")
    private fun mapping(season: Int, provider: String, available: Boolean = true) = ProviderSeasonMappingEntity(
        "anime", season, provider, season, provider, null, "https://example/$provider", "DE", available, 1
    )
}
