package de.anisentinel.app.data.release

import de.anisentinel.app.data.anisearch.ReleaseSourceKind
import de.anisentinel.app.data.anisearch.SourceCalendarFetchResult
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnimeRadarCalendarParserTest {
    private val fixture = requireNotNull(
        javaClass.classLoader?.getResource("fixtures/animeradar_calendar_page_2026-08-03.json")
    ).readText()

    @Test fun `real public response fixture maps only delivered fields`() {
        val page = AnimeRadarCalendarParser().parse(fixture)
        val first = page.entries.first()
        assertEquals(2, page.entries.size)
        assertEquals(137653L, first.aniListId)
        assertEquals(152, first.episodeNumber)
        assertEquals("Renegade Immortal", first.titleEnglish)
        assertNull(first.titleGerman)
        assertNull(first.provider)
        assertNull(first.language)
        assertEquals(ReleaseStatus.SCHEDULED, first.status)
    }

    @Test fun `enabled source maps real fixture to AnimeRadar releases`() = runBlocking {
        val source = AnimeRadarCalendarSource(
            client = AnimeRadarClient(transport = { AnimeRadarHttpResult.Success(fixture) }),
            enabled = true
        )
        val result = source.fetchRange(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10))
        assertTrue(result is SourceCalendarFetchResult.Complete)
        val complete = result as SourceCalendarFetchResult.Complete
        assertEquals(ReleaseSourceKind.ANIME_RADAR, complete.sourceKind)
        assertEquals(2, complete.releases.size)
        assertTrue(complete.releases.all { it.titleGerman == null && it.provider == null })
    }
}
