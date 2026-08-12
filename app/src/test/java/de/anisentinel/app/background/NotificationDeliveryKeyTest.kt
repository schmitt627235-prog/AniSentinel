package de.anisentinel.app.background

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationDeliveryKeyTest {
    @Test
    fun sameEpisodeWithChangedSourceReleaseIdKeepsDueKey() {
        val original = release("aniworld:title:s2:e6:1000")
        val resynced = release("aniworld:title:s2:e6:1300")

        assertEquals(
            semanticNotificationDeliveryId(original, "RELEASE_DUE"),
            semanticNotificationDeliveryId(resynced, "RELEASE_DUE")
        )
    }

    @Test
    fun languageEpisodeAndEventRemainDistinct() {
        val sub = release("sub")
        val dub = sub.copy(sourceReleaseId = "dub", releaseLanguage = "GER_DUB")
        val nextEpisode = sub.copy(sourceReleaseId = "next", episodeNumber = 7)

        assertNotEquals(semanticNotificationDeliveryId(sub, "RELEASE_DUE"), semanticNotificationDeliveryId(dub, "RELEASE_DUE"))
        assertNotEquals(semanticNotificationDeliveryId(sub, "RELEASE_DUE"), semanticNotificationDeliveryId(nextEpisode, "RELEASE_DUE"))
        assertNotEquals(semanticNotificationDeliveryId(sub, "RELEASE_DUE"), semanticNotificationDeliveryId(sub, "AVAILABLE_GER_SUB"))
    }

    private fun release(sourceId: String) = EpisodeReleaseEntity(
        sourceReleaseId = sourceId,
        animeId = "aniworld:you-and-i-are-polar-opposites",
        episodeNumber = 6,
        episodeTitle = null,
        expectedAt = 1_000,
        provider = null,
        metadataSource = "ANIWORLD_CALENDAR",
        sourceUrl = null,
        providerUrl = null,
        fetchedAt = 900,
        seasonNumber = 2,
        releaseLanguage = "GER_SUB"
    )
}
