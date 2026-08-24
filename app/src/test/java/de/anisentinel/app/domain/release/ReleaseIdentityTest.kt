package de.anisentinel.app.domain.release

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.anisentinel.app.data.local.ReleasePostponementEntity
import org.junit.Assert.assertEquals

class ReleaseIdentityTest {

    @Test
    fun postponementWithoutLanguageStillMatchesConcreteEpisodeTrack() {
        val shift = ReleaseIdentity("anime", 3, 20, null)
        val release = ReleaseIdentity("anime", 3, 20, "GER_SUB")
        assertTrue(shift.sameEpisodeAllowingUnspecifiedLanguage(release))
        assertFalse(shift.sameEpisode(release))
    }
    @Test fun sourceIdsDoNotChangeSemanticEpisodeIdentity() {
        val calendar = release("aniworld:title:s1:e20:sub")
        val provider = release("crunchyroll:series:episode-20")
        assertTrue(ReleaseIdentity.from(calendar).sameEpisode(ReleaseIdentity.from(provider)))
    }

    @Test fun anotherEpisodeCannotBorrowPostponementOrCountdown() {
        assertFalse(ReleaseIdentity.from(release("e19", 19)).sameEpisode(ReleaseIdentity.from(release("e20", 20))))
    }

    @Test fun unspecifiedIsNeverExposedAsDifferentLanguageIdentity() {
        assertTrue(ReleaseIdentity("a", 1, 1, null).sameEpisode(ReleaseIdentity("a", 1, 1, "UNSPECIFIED")))
    }

    @Test fun exactEpisodePostponementCanReplaceOnlyItsOwnCountdownTarget() {
        val episode20 = release("e20", 20)
        val shift = ReleasePostponementEntity(
            "p20", episode20.sourceReleaseId, episode20.animeId, "Anime", 1, 20, "GER_SUB",
            1_000, 2_000, "Pause", "LATER", "ANIWORLD", "https://example.test", null,
            900, 900, true, 1, 0
        )
        assertTrue(ReleaseIdentity.from(shift)!!.sameEpisode(ReleaseIdentity.from(episode20)))
        assertFalse(ReleaseIdentity.from(shift)!!.sameEpisode(ReleaseIdentity.from(release("e19", 19))))
        assertEquals(2_000L, shift.newExpectedAt)
    }

    private fun release(id: String, episode: Int = 20) = EpisodeReleaseEntity(
        id, "anime", episode, null, 1000, "Crunchyroll", "TEST", null, null, 900,
        seasonNumber = 1, releaseLanguage = "GER_SUB"
    )
}
