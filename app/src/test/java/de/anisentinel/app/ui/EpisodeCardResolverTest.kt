package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class EpisodeCardResolverTest {
    @Test fun multipleSourceRowsProduceOneCardPerEpisode() {
        val rows = listOf(release("aniworld:7"), release("crunchyroll:7"), release("history:7"))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), EpisodeCardResolver.visibleEpisodeNumbers(7, rows))
    }

    @Test fun availabilityCheckOnlyForNewestUnconfirmedDueRelease() {
        assertTrue(AvailabilityActionPolicy.showCheck("due", setOf("due"), false))
        assertFalse(AvailabilityActionPolicy.showCheck("due", setOf("due"), true))
        assertFalse(AvailabilityActionPolicy.showCheck("old", setOf("due"), false))
        assertFalse(AvailabilityActionPolicy.showCheck(null, setOf("due"), false))
    }

    @Test fun confirmedEpisodeOverridesStaleUncheckedTitleSummary() {
        assertEquals(
            ProviderSummaryPresentation(ProviderSummaryStatus.AVAILABLE, "Crunchyroll"),
            ProviderSummaryResolver.resolve(true, null, listOf("Crunchyroll"))
        )
    }

    @Test fun titleWithoutEpisodeEvidenceRemainsNotChecked() {
        assertEquals(
            ProviderSummaryPresentation(ProviderSummaryStatus.NOT_CHECKED),
            ProviderSummaryResolver.resolve(true, null, emptyList())
        )
    }

    private fun release(id: String) = EpisodeReleaseEntity(
        sourceReleaseId = id, animeId = "anime", episodeNumber = 7, episodeTitle = null, expectedAt = 1L,
        provider = "Crunchyroll", metadataSource = "TEST", sourceUrl = "https://example.test",
        providerUrl = null, fetchedAt = 1L
    )
}
