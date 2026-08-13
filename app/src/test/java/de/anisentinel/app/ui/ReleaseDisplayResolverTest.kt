package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.*
import org.junit.Test

class ReleaseDisplayResolverTest {
    private fun release(ep: Int, season: Int, at: Long, language: String = "GER_SUB") =
        EpisodeReleaseEntity("r-$season-$ep", "anime", ep, null, at, null, "TEST", null, null, at,
            seasonNumber = season, releaseStatus = "AVAILABLE", releaseLanguage = language)

    @Test fun rejectsOldMislabeledSeasonAndInfersPreviousWeeklySlotWithSameTime() {
        val next = release(18, 4, 1_786_719_600L)
        val wrong = release(1, 4, 1_677_884_400L)
        val result = ReleaseDisplayResolver.previousFor(listOf(wrong, next), next, next.expectedAt!! - 3600)!!
        assertTrue(result.inferred)
        assertEquals(17, result.release.episodeNumber)
        assertEquals(next.expectedAt!! - 7 * 24 * 60 * 60, result.release.expectedAt)
    }

    @Test fun usesRealCompatiblePreviousEpisodeInsteadOfInference() {
        val next = release(18, 4, 2_000_000)
        val previous = release(17, 4, 2_000_000 - 604_800)
        val result = ReleaseDisplayResolver.previousFor(listOf(previous, next), next, 1_999_999)!!
        assertFalse(result.inferred)
        assertEquals(previous, result.release)
    }

    @Test fun neverUsesDifferentLanguageAsPreviousRelease() {
        val next = release(18, 4, 2_000_000)
        val dub = release(17, 4, 1_900_000, "GER_DUB")
        assertTrue(ReleaseDisplayResolver.previousFor(listOf(dub, next), next, 1_999_999)!!.inferred)
    }

    @Test fun oneOffDelayNeverChangesRegularWeeklyTimeInference() {
        val regularSlot = 1_786_719_600L // 17:00 local in the fixture
        val delayedNext = release(18, 4, regularSlot + 30 * 60)
        val result = ReleaseDisplayResolver.previousFor(
            listOf(delayedNext), delayedNext, regularSlot - 1, regularSlot
        )!!
        assertTrue(result.inferred)
        assertEquals(regularSlot - 7 * 24 * 60 * 60, result.release.expectedAt)
    }

    @Test fun nextRegularEpisodeKeepsSourceScheduleAfterPreviousOneOffDelay() {
        val regularEpisode19 = release(19, 4, 1_787_324_400L)
        val delayedEpisode18 = release(18, 4, 1_786_721_400L)
        val result = ReleaseDisplayResolver.previousFor(
            listOf(delayedEpisode18, regularEpisode19),
            regularEpisode19,
            regularEpisode19.expectedAt!! - 60,
            regularEpisode19.expectedAt
        )!!
        // A stored exceptional release may be shown as real history, but the next
        // episode's source time remains its own regular 17:00 schedule.
        assertEquals(regularEpisode19.expectedAt, 1_787_324_400L)
        assertEquals(18, result.release.episodeNumber)
    }
}
