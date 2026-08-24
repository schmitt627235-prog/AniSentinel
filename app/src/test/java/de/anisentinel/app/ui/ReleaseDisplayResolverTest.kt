package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.ReleasePostponementEntity
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

    @Test fun highestSemanticallyConfirmedEpisodeDrivesLastRelease() {
        val next = release(20, 1, 3_000_000)
        val stale = release(16, 1, 2_700_000)
        val newest = release(19, 1, 2_900_000)
        val result = ReleaseDisplayResolver.previousFor(
            listOf(stale, newest, next), next, 2_950_000, checks = listOf(available(newest))
        )!!
        assertEquals(19, result.release.episodeNumber)
    }

    @Test fun confirmedProviderEpisodeWinsEvenWhenItsImportedTimestampLooksFutureDated() {
        val staleCalendar = release(16, 3, 2_000_000)
        val confirmed19 = release(19, 3, 4_000_000)
        val result = ReleaseDisplayResolver.latestConfirmed(
            listOf(staleCalendar, confirmed19), listOf(available(confirmed19)), 3, "GER_SUB"
        )
        assertEquals(19, result?.episodeNumber)
    }

    @Test fun historicalProviderReleaseWithoutSeparateCheckStillDrivesLatestRelease() {
        val episode19 = release(19, 3, 4_000_000).copy(
            isHistoricalImport = true,
            metadataSource = "CRUNCHYROLL_PUBLIC_HISTORY",
            provider = "Crunchyroll",
            releaseStatus = "AVAILABLE_GER_SUB"
        )
        assertEquals(
            19,
            ReleaseDisplayResolver.latestConfirmed(listOf(episode19), emptyList(), 3, "GER_SUB")?.episodeNumber
        )
    }

    @Test fun providerNeutralCalendarRowWithPersistedAvailableStatusDrivesCurrentSeason() {
        val sub19 = release(19, 3, 4_000_000).copy(
            provider = null,
            metadataSource = "ANIWORLD_CALENDAR",
            releaseStatus = "AVAILABLE",
            releaseLanguage = "GER_SUB"
        )
        val dub16 = release(16, 3, 3_900_000, "GER_DUB")
        assertEquals(
            19,
            ReleaseDisplayResolver.latestConfirmed(
                listOf(dub16, sub19), emptyList(), season = 3, language = null
            )?.episodeNumber
        )
    }

    @Test fun canonicalNextIgnoresStaleOlderFutureRowAndUsesPostponedEpisode() {
        val stale17 = release(17, 3, 2_100).copy(releaseStatus = "SCHEDULED")
        val available19 = release(19, 3, 1_900).copy(releaseStatus = "AVAILABLE")
        val episode20 = release(20, 3, 2_000).copy(releaseStatus = "RESCHEDULED")
        val shift = postponement(20, "GER_SUB", 2_000, 3_000)

        val result = ReleaseDisplayResolver.nextFor(
            listOf(stale17, available19, episode20), listOf(shift), nowEpoch = 1_950
        )!!

        assertEquals(20, result.identity.episode)
        assertEquals("GER_SUB", result.identity.language)
        assertEquals(3_000, result.effectiveExpectedAt)
        assertEquals(3_000, result.countdownTarget)
        assertEquals("p-20-GER_SUB", result.postponement?.postponementId)
    }

    @Test fun homeAndDetailResolverReturnExactlySameCanonicalIdentity() {
        val releases = listOf(
            release(19, 3, 1_900).copy(releaseStatus = "AVAILABLE"),
            release(20, 3, 2_000).copy(releaseStatus = "RESCHEDULED")
        )
        val shifts = listOf(postponement(20, "GER_SUB", 2_000, 3_000))
        val home = ReleaseDisplayResolver.nextFor(releases, shifts, nowEpoch = 1_950)
        val detail = ReleaseDisplayResolver.nextFor(releases, shifts, nowEpoch = 1_950)
        assertEquals(home, detail)
    }

    @Test fun dubPostponementNeverChangesNextSubRelease() {
        val sub20 = release(20, 4, 3_000, "GER_SUB").copy(releaseStatus = "SCHEDULED")
        val sub19 = release(19, 4, 1_800, "GER_SUB").copy(releaseStatus = "AVAILABLE")
        val dub17 = release(17, 4, 2_000, "GER_DUB").copy(releaseStatus = "SCHEDULED")
        val shift = postponement(17, "GER_DUB", 2_000, 2_500)
        val result = ReleaseDisplayResolver.nextFor(listOf(sub19, dub17, sub20), listOf(shift), nowEpoch = 1_900)!!
        assertEquals(20, result.identity.episode)
        assertEquals("GER_SUB", result.identity.language)
        assertEquals(3_000, result.countdownTarget)
        assertNull(result.postponement)
    }

    private fun postponement(ep: Int, language: String, old: Long, new: Long) = ReleasePostponementEntity(
        "p-$ep-$language", "r-3-$ep", "anime", "Anime", if (ep == 20) 3 else 4, ep, language,
        old, new, "TV", "DELAYED", "ANIWORLD", "https://aniworld.to/source", null,
        1, 1, true, 1, 0
    )

    private fun available(release: EpisodeReleaseEntity) = EpisodeProviderAvailabilityEntity(
        "a:${release.sourceReleaseId}", release.sourceReleaseId, "CRUNCHYROLL", "Crunchyroll",
        release.seasonNumber, release.episodeNumber, "AVAILABLE_GER_SUB", true, false, null,
        2_900_100, null, 2_900_100, null, 0, null, "PROVIDER_DIRECT", null, null, "TEST"
    )
}
