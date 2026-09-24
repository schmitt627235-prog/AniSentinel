package de.anisentinel.app.data.release

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDatabase
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AniWorldReleaseRepositoryTest {
    private lateinit var database: AniSentinelDatabase
    private val zone = ZoneId.of("Europe/Berlin")
    private val clock = Clock.fixed(Instant.parse("2100-01-01T00:00:00Z"), ZoneId.of("UTC"))

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AniSentinelDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun matchedEnglishTitleWithEmptyGermanTitleDoesNotCrashOrInventProviderSeason() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "anilist:212888", 212888, null, "", "Overgeared", "Temppal: Item no Chikara",
            null, "", null, null, null, null, null, 1
        )))
        val result = repository(calendar(day("27.09.2026", card("Overgeared", "S01E01", "23:59"))))
            .syncCalendar(LocalDate.parse("2026-09-21"), LocalDate.parse("2026-10-12"), true)

        assertTrue(result is AniWorldSyncResult.Success)
        val release = dao.episodeReleasesForAnime("anilist:212888").single()
        assertEquals("ANIWORLD_CALENDAR", release.metadataSource)
        assertEquals(1, release.seasonNumber)
        assertEquals(1, release.episodeNumber)
        assertFalse(release.isHistoricalImport)
        assertTrue(dao.providerSeasonMappings("anilist:212888", 1).isEmpty())
    }

    @Test fun newSeasonAndMalformedCardsDoNotBlockValidEntry() = runBlocking {
        val html = calendar(day("01.10.2026",
            card("Bad Time", "S01E01", "25:99") +
                card("Unknown Season", "Special", "18:00") +
                card("No Time", "S01E01", "") +
                card("New Season Anime", "S02E01", "18:10")
        ))
        val result = repository(html).syncCalendar(
            LocalDate.parse("2026-09-21"), LocalDate.parse("2026-10-12"), true
        )

        assertEquals(1, (result as AniWorldSyncResult.Success).stored)
        val dao = database.aniSentinelDao()
        val release = dao.episodeReleasesForAnime("aniworld:new-season-anime").single()
        assertEquals(2, release.seasonNumber)
        assertTrue(dao.providerSeasonMappings(release.animeId, 2).isEmpty())
    }

    @Test fun emptyOrOutOfRangePageKeepsExistingCalendarHistory() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "old", null, null, "Old", null, null, null, "", null, null,
            null, null, null, 1
        )))
        dao.upsertEpisodeReleases(listOf(EpisodeReleaseEntity(
            "old-release", "old", 1, null,
            LocalDate.parse("2026-09-10").atStartOfDay(zone).toEpochSecond(), null,
            "ANIWORLD_CALENDAR", null, null, 1, seasonNumber = 1
        )))

        val noEntries = repository("<section class='calendarList'><h3>24.09.2026</h3></section>")
            .syncCalendar(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"), true)
        assertTrue(noEntries is AniWorldSyncResult.Failure)
        val laterPage = repository(calendar(day("01.10.2026", card("October Anime", "S01E01", "18:10"))))
            .syncCalendar(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-01"), true)
        assertEquals(0, (laterPage as AniWorldSyncResult.Success).stored)
        assertEquals(1, dao.episodeReleasesForAnime("old").size)
    }

    private fun repository(html: String): AniWorldReleaseRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val transport = AniWorldHttpTransport(context, clock) { 200 to html }
        return AniWorldReleaseRepository(database.aniSentinelDao(), transport, zone, clock)
    }

    private fun calendar(vararg days: String) = days.joinToString("\n")
    private fun day(date: String, cards: String) =
        "<section class='calendarList'><h3>$date</h3><div class='seriesListContainer'>$cards</div></section>"
    private fun card(title: String, seasonEpisode: String, time: String) =
        "<div><a href='/anime/stream/${title.lowercase().replace(' ', '-')}'><h3 class='seriesTitle'>$title</h3>" +
            "<small>$seasonEpisode</small><small>$time Uhr</small></a></div>"
}
