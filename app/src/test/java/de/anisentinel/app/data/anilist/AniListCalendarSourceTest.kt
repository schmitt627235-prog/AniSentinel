package de.anisentinel.app.data.anilist

import de.anisentinel.app.data.anisearch.SourceCalendarFetchResult
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AniListCalendarSourceTest {
    @Test fun parsesMultipleReleasesAndKeepsSourceLinks() = runBlocking {
        val json = """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"airingSchedules":[
          {"episode":5,"airingAt":1785592800,"media":{"id":101,"title":{"romaji":"Titel A","english":"Title A","native":"A"}}},
          {"episode":6,"airingAt":1786197600,"media":{"id":101,"title":{"romaji":"Titel A","english":"Title A","native":"A"}}},
          {"episode":2,"airingAt":1785679200,"media":{"id":202,"title":{"romaji":"Titel B","english":null,"native":"B"}}}
        ]}}}"""
        val result = AniListCalendarSource(request = { json }).fetchRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)
        )
        assertTrue(result is SourceCalendarFetchResult.Complete)
        val releases = (result as SourceCalendarFetchResult.Complete).releases
        assertEquals(3, releases.size)
        assertEquals(2, releases.count { it.anisearchId == null && it.sourceUrl.contains("anilist.co/anime/101") })
        assertEquals(2, releases.mapNotNull { it.episodeNumber }.filter { it in 5..6 }.size)
    }

    @Test fun `query includes release exactly at local midnight`() = runBlocking {
        var requestBody = ""
        val empty = """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"airingSchedules":[]}}}"""
        AniListCalendarSource(
            request = { body -> requestBody = body; empty },
            timeZoneProvider = DeviceTimeZoneProvider { java.time.ZoneId.of("Europe/Berlin") }
        ).fetchRange(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3))
        val variables = org.json.JSONObject(requestBody).getJSONObject("variables")
        assertEquals(
            java.time.Instant.parse("2026-08-01T21:59:59Z").epochSecond,
            variables.getLong("from")
        )
    }

    @Test fun `timezone provider is read for every request`() = runBlocking {
        var zone = java.time.ZoneId.of("Europe/Berlin")
        val fromValues = mutableListOf<Long>()
        val empty = """{"data":{"Page":{"pageInfo":{"hasNextPage":false},"airingSchedules":[]}}}"""
        val source = AniListCalendarSource(
            request = { body -> fromValues += org.json.JSONObject(body).getJSONObject("variables").getLong("from"); empty },
            timeZoneProvider = DeviceTimeZoneProvider { zone }
        )
        source.fetchRange(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3))
        zone = java.time.ZoneId.of("Asia/Tokyo")
        source.fetchRange(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3))
        assertTrue(fromValues[0] != fromValues[1])
    }

    @Test fun `page limit with more pages is an incomplete failure`() = runBlocking {
        var calls = 0
        val page = """{"data":{"Page":{"pageInfo":{"hasNextPage":true},"airingSchedules":[]}}}"""
        val result = AniListCalendarSource(request = { calls++; page }).fetchRange(
            LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 8)
        )

        assertEquals(AniListCalendarSource.MAX_PAGES, calls)
        assertTrue(result is SourceCalendarFetchResult.Unavailable)
        assertEquals(
            de.anisentinel.app.data.anisearch.SourceFailureReason.PAGINATION_LIMIT,
            (result as SourceCalendarFetchResult.Unavailable).reason
        )
    }
}
