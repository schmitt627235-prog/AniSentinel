package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SemanticAvailabilityCheckTest {
    @Test
    fun availableCheckFromDuplicateSourceStopsTopCardDelayForSameRealEpisode() {
        val top = release("aniworld:black-torch:s1:e7:dub")
        val duplicate = release("provider:black-torch:s1:e7:dub")
        val available = availability(duplicate.sourceReleaseId)

        val selected = semanticAvailabilityCheck(top, listOf(top, duplicate), listOf(available))

        assertNotNull(selected)
        assertEquals("AVAILABLE_GER_DUB", selected?.status)
    }

    @Test
    fun differentEpisodeDoesNotConfirmTopCard() {
        val top = release("aniworld:black-torch:s1:e7:dub")
        val other = release("provider:black-torch:s1:e8:dub", episode = 8)

        assertEquals(null, semanticAvailabilityCheck(top, listOf(top, other), listOf(availability(other.sourceReleaseId, 8))))
    }

    private fun release(id: String, episode: Int = 7) = EpisodeReleaseEntity(
        sourceReleaseId = id,
        animeId = "aniworld:black-torch",
        episodeNumber = episode,
        episodeTitle = null,
        expectedAt = 1_000,
        provider = "Crunchyroll",
        metadataSource = "ANIWORLD_CALENDAR",
        sourceUrl = "https://aniworld.example/calendar",
        providerUrl = null,
        fetchedAt = 900,
        seasonNumber = 1,
        releaseLanguage = "GER_DUB"
    )

    private fun availability(releaseId: String, episode: Int = 7) = EpisodeProviderAvailabilityEntity(
        availabilityId = "availability:$releaseId:crunchyroll",
        releaseId = releaseId,
        providerId = "CRUNCHYROLL",
        providerName = "Crunchyroll",
        seasonNumber = 1,
        episodeNumber = episode,
        status = "AVAILABLE_GER_DUB",
        germanSubAvailable = false,
        germanDubAvailable = true,
        monetizationType = null,
        firstAvailableAt = 1_010,
        lastUnavailableAt = null,
        lastCheckedAt = 1_020,
        nextCheckAt = null,
        checkAttempt = 0,
        providerUrl = "https://provider.example/watch/7",
        evidenceType = "PROVIDER_DIRECT",
        evidenceUrl = "https://provider.example/watch/7",
        errorCode = null,
        source = "CRUNCHYROLL_PUBLIC_WEB"
    )
}
