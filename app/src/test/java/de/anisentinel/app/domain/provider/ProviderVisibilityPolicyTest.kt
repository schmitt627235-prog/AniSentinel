package de.anisentinel.app.domain.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderVisibilityPolicyTest {
    @Test
    fun legacyDefaultKeepsAllSupportedProvidersEnabled() {
        assertEquals(
            ProviderVisibilityPolicy.supportedProviderIds,
            ProviderVisibilityPolicy.enabledProviderIds(emptySet())
        )
        assertTrue(ProviderVisibilityPolicy.isAnimeVisible(emptyList(), emptySet()))
    }

    @Test
    fun disabledSingleProviderHidesExclusiveTitle() {
        assertFalse(
            ProviderVisibilityPolicy.isAnimeVisible(
                providers = listOf("Netflix"),
                disabledProviderIds = setOf("netflix")
            )
        )
    }

    @Test
    fun multiProviderTitleRemainsVisibleWithEnabledProvider() {
        val disabled = setOf("adn")
        val providers = listOf("Crunchyroll", "Animation Digital Network")

        assertTrue(ProviderVisibilityPolicy.isAnimeVisible(providers, disabled))
        assertEquals(listOf("Crunchyroll"), ProviderVisibilityPolicy.visibleProviders(providers, disabled))
    }

    @Test
    fun titleIsHiddenWhenAllItsProvidersAreDisabled() {
        assertFalse(
            ProviderVisibilityPolicy.isAnimeVisible(
                providers = listOf("Crunchyroll", "ADN"),
                disabledProviderIds = setOf("crunchyroll", "adn")
            )
        )
    }

    @Test
    fun providerAliasesUseCanonicalIds() {
        assertEquals("crunchyroll", ProviderVisibilityPolicy.canonicalId("Crunchyroll Amazon Channel"))
        assertEquals("adn", ProviderVisibilityPolicy.canonicalId("Animation Digital Network"))
        assertEquals("disney_plus", ProviderVisibilityPolicy.canonicalId("Disney Plus"))
        assertEquals("akiba_pass", ProviderVisibilityPolicy.canonicalId("AKIBA PASS"))
        assertEquals("apple_tv", ProviderVisibilityPolicy.canonicalId("Apple TV Store"))
        assertEquals(null, ProviderVisibilityPolicy.canonicalId("Apple TV+"))
    }

    @Test
    fun newProvidersCanBeManagedIndependently() {
        assertTrue(ProviderVisibilityPolicy.supportedProviderIds.containsAll(setOf("akiba_pass", "apple_tv")))
        assertEquals("AKIBA PASS", ProviderVisibilityPolicy.displayName("akiba_pass"))
        assertEquals("Apple TV", ProviderVisibilityPolicy.displayName("apple_tv"))
        assertEquals(
            listOf("AKIBA PASS"),
            ProviderVisibilityPolicy.visibleProviders(listOf("AKIBA PASS", "Apple TV"), setOf("apple_tv"))
        )
        assertFalse(ProviderVisibilityPolicy.isAnimeVisible(listOf("Apple TV"), setOf("apple_tv")))
    }
}
