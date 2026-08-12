package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CurrentSeasonResolverTest {
    private val now = 1_000_000L

    @Test fun activeCycleDoesNotRequireJustWatchCatalogData() {
        val releases = listOf(
            release("running", now - 6 * 86_400, 5),
            release("running", now + 1 * 86_400, 6)
        )
        assertEquals(setOf("running"), CurrentSeasonResolver.activeAnimeIds(releases, now))
    }

    @Test fun futureOnlyAndEndedCyclesAreNotCurrentSeason() {
        val releases = listOf(
            release("future", now + 2 * 86_400, 1),
            release("future", now + 9 * 86_400, 2),
            release("ended", now - 40 * 86_400, 11),
            release("ended", now - 33 * 86_400, 12)
        )
        assertEquals(emptySet<String>(), CurrentSeasonResolver.activeAnimeIds(releases, now))
    }

    @Test fun recentEstablishedCycleRemainsVisibleWhenNextDateIsTemporarilyMissing() {
        val releases = listOf(
            release("recent", now - 7 * 86_400, 5),
            release("recent", now, 6)
        )
        assertEquals(setOf("recent"), CurrentSeasonResolver.activeAnimeIds(releases, now))
    }

    @Test fun upcomingEpisodeAboveOneProvesRunningCycleWithoutStoredHistory() {
        val releases = listOf(release("weekday-running", now + 2 * 86_400, 6))
        assertEquals(setOf("weekday-running"), CurrentSeasonResolver.activeAnimeIds(releases, now))
    }

    @Test fun nonAniWorldCatalogRowsNeverCreateSeasonActivity() {
        val rows = listOf(
            release("one-piece-2023", now - 1_000, 1, "JUSTWATCH_CATALOG"),
            release("one-piece-2023", now + 1_000, 2, "JUSTWATCH_CATALOG")
        )
        assertEquals(emptySet<String>(), CurrentSeasonResolver.activeAnimeIds(rows, now))
    }

    @Test fun scheduledContinuationAfterMissingHistoryIsTreatedAsResumedCycle() {
        val rows = listOf(
            release("returning", now - 60 * 86_400, 5),
            release("returning", now + 3 * 86_400, 6)
        )
        assertEquals(setOf("returning"), CurrentSeasonResolver.activeAnimeIds(rows, now))
    }

    private fun release(animeId: String, at: Long, episode: Int, source: String = "ANIWORLD_CALENDAR") =
        EpisodeReleaseEntity(
            sourceReleaseId = "$animeId:$episode:$at", animeId = animeId, episodeNumber = episode,
            episodeTitle = null, expectedAt = at, provider = null, metadataSource = source,
            sourceUrl = null, providerUrl = null, fetchedAt = now, seasonNumber = 1,
            releaseLanguage = "GER_SUB"
        )
}
