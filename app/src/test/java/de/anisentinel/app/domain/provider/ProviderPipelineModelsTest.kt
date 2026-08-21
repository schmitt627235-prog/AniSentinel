package de.anisentinel.app.domain.provider

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProviderPipelineModelsTest {
    private fun checked(found: Boolean, sub: Boolean? = false, dub: Boolean? = false) =
        ProviderEpisodeCheckResult.Checked(ProviderEpisodeAvailability("p", 1, 2, found, sub, dub,
            if (found) Instant.EPOCH else null, null, Instant.EPOCH, "TEST", null))

    @Test fun subAndDubRemainSeparate() {
        assertEquals(EpisodeAvailabilityStatus.AVAILABLE_GER_SUB, EpisodeStatusResolver.resolve("GER_SUB", checked(true, sub = true)))
        assertEquals(EpisodeAvailabilityStatus.AVAILABLE_GER_DUB, EpisodeStatusResolver.resolve("GER_DUB", checked(true, dub = true)))
        assertEquals(EpisodeAvailabilityStatus.AVAILABLE_GER_SUB_AND_DUB, EpisodeStatusResolver.resolve("GER_SUB", checked(true, true, true)))
    }

    @Test fun missingEpisodeAndFailureNeverClaimDelay() {
        assertEquals(EpisodeAvailabilityStatus.PROVIDER_EPISODE_NOT_FOUND, EpisodeStatusResolver.resolve("GER_SUB", checked(false)))
        assertEquals(EpisodeAvailabilityStatus.CHECK_FAILED, EpisodeStatusResolver.resolve("GER_SUB",
            ProviderEpisodeCheckResult.Failed("NETWORK_ERROR", Instant.EPOCH, true)))
    }

    @Test fun foundEpisodeWithoutLanguageEvidenceStaysUnknown() {
        assertEquals(
            EpisodeAvailabilityStatus.EPISODE_FOUND_LANGUAGE_UNKNOWN,
            EpisodeStatusResolver.resolve("GER_DUB", checked(true, sub = null, dub = null))
        )
    }

    @Test fun physicalOffersCanNeverBecomeConfirmedEpisodeProvider() {
        val providers = listOf("Amazon DVD / Blu-ray", "Thalia", "Netflix", "Crunchyroll")
        assertEquals(listOf("Crunchyroll", "Netflix"), StreamingProviderPolicy.visible(providers))
        assertEquals("Crunchyroll", StreamingProviderPolicy.confirmedDisplayProvider(providers))
        assertNull(StreamingProviderPolicy.confirmedDisplayProvider(listOf("Amazon DVD / Blu-ray", "Buecher")))
    }

    @Test fun crunchyrollAmazonChannelIsCollapsedButAniverseChannelRemainsDistinct() {
        assertEquals(
            listOf("ANIVERSE Amazon Channel", "Crunchyroll"),
            StreamingProviderPolicy.visible(
                listOf("Crunchyroll", "Crunchyroll Amazon Channel", "ANIVERSE Amazon Channel")
            )
        )
    }

    @Test fun providerMarketIsStrictlyGermanForTheAppCatalog() {
        assertTrue(ProviderMarketPolicy.isAppMarket("DE"))
        assertFalse(ProviderMarketPolicy.isAppMarket("FR"))
        assertFalse(ProviderMarketPolicy.isAppMarket(null))
    }
}
