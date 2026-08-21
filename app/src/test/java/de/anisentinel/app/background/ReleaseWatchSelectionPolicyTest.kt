package de.anisentinel.app.background

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseWatchSelectionPolicyTest {
    private val now = 1_000_000L

    @Test fun oldFridayReleaseDoesNotRemainActiveOnTuesday() {
        val friday = release("friday", 7, now - 4 * 86_400)
        val nextFriday = release("next-friday", 8, now + 3 * 86_400)
        val selection = ReleaseWatchSelectionPolicy.select(listOf(friday, nextFriday), now)
        assertEquals(listOf("next-friday"), selection.active.map { it.sourceReleaseId })
        assertTrue("friday" in selection.staleReleaseIds)
    }

    @Test fun dueReleaseRetainsShortGraceEvenWhenSuccessorIsKnown() {
        val due = release("due", 7, now - 60)
        val next = release("next", 8, now + 7 * 86_400)
        assertEquals(setOf("due", "next"), ReleaseWatchSelectionPolicy.select(listOf(due, next), now)
            .active.mapTo(mutableSetOf()) { it.sourceReleaseId })
    }

    @Test fun successorSupersedesUnconfirmedReleaseAfterGrace() {
        val old = release("old", 7, now - ReleaseWatchSelectionPolicy.SUCCESSOR_SUPERSESSION_GRACE_SECONDS - 1)
        val next = release("next", 8, now + 6 * 86_400)
        val selection = ReleaseWatchSelectionPolicy.select(listOf(old, next), now)
        assertEquals(listOf("next"), selection.active.map { it.sourceReleaseId })
        assertTrue("old" in selection.staleReleaseIds)
    }

    private fun release(id: String, episode: Int, expectedAt: Long) = EpisodeReleaseEntity(
        id, "anime", episode, null, expectedAt, "Crunchyroll", "TEST", null, null, now,
        seasonNumber = 1, releaseLanguage = "GER_SUB"
    )
}
