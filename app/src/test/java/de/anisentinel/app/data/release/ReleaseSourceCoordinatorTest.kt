package de.anisentinel.app.data.release

import de.anisentinel.app.data.anisearch.ReleaseCalendarSource
import de.anisentinel.app.data.anisearch.ReleaseSourceKind
import de.anisentinel.app.data.anisearch.SourceCalendarFetchResult
import de.anisentinel.app.data.anisearch.SourceEpisodeRelease
import de.anisentinel.app.data.anisearch.SourceFailureReason
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSourceCoordinatorTest {
    private val start = LocalDate.of(2026, 8, 3)
    private val end = start.plusWeeks(1)

    @Test fun `AnimeRadar complete result has priority and skips fallback`() = runBlocking {
        var fallbackCalls = 0
        val primary = source(SourceCalendarFetchResult.Complete(listOf(release(ReleaseSourceKind.ANIME_RADAR))))
        val fallback = object : ReleaseCalendarSource {
            override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate): SourceCalendarFetchResult {
                fallbackCalls++
                return SourceCalendarFetchResult.Complete(listOf(release(ReleaseSourceKind.ANILIST_FALLBACK)))
            }
        }

        val result = ReleaseSourceCoordinator(primary, fallback).fetchRange(start, end)

        assertEquals(0, fallbackCalls)
        assertEquals(ReleaseSourceKind.ANIME_RADAR,
            (result as SourceCalendarFetchResult.Complete).releases.single().sourceKind)
    }

    @Test fun `unavailable AnimeRadar uses AniList fallback`() = runBlocking {
        val primary = source(SourceCalendarFetchResult.Unavailable(
            SourceFailureReason.NOT_CONFIGURED, "ANIME_RADAR_DISABLED_PENDING_PERMISSION_AND_FIXTURES"))
        val fallback = source(SourceCalendarFetchResult.Complete(listOf(release(ReleaseSourceKind.ANILIST_FALLBACK))))

        val result = ReleaseSourceCoordinator(primary, fallback).fetchRange(start, end)

        assertEquals(ReleaseSourceKind.ANILIST_FALLBACK,
            (result as SourceCalendarFetchResult.Complete).releases.single().sourceKind)
    }

    @Test fun `disabled AnimeRadar adapter never performs a productive fetch`() = runBlocking {
        val result = AnimeRadarCalendarSource(enabled = false).fetchRange(start, end)
        assertTrue(result is SourceCalendarFetchResult.Unavailable)
        assertEquals(SourceFailureReason.NOT_CONFIGURED,
            (result as SourceCalendarFetchResult.Unavailable).reason)
    }

    private fun source(result: SourceCalendarFetchResult) = object : ReleaseCalendarSource {
        override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate) = result
    }

    private fun release(kind: ReleaseSourceKind) = SourceEpisodeRelease(
        sourceKind = kind,
        sourceReleaseId = "source:1:1",
        anisearchId = null,
        titleGerman = null,
        episodeNumber = 1,
        releaseAtEpochSeconds = 1_800_000_000,
        provider = null,
        sourceUrl = "https://example.invalid/source",
        providerUrl = null
    )
}
