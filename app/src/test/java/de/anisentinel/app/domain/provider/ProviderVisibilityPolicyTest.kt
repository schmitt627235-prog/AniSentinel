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
    }
}
