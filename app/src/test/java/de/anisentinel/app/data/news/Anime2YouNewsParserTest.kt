package de.anisentinel.app.data.news

import de.anisentinel.app.data.local.AnnouncementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Anime2YouNewsParserTest {
    @Test fun parsesRealRssShapeWithoutInventingDates() {
        val xml = checkNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/anime2you_news_feed_2026-08-09.xml"))
            .bufferedReader().use { it.readText() }
        val items = Anime2YouRssParser.parse(xml)
        assertEquals(2, items.size)
        assertEquals(AnnouncementType.NEW_DATE, items[0].type)
        assertEquals(null, items[0].newDate)
        assertEquals(AnnouncementType.DUB_CONFIRMED, items[1].type)
        assertEquals("Crunchyroll", items[1].provider)
        assertTrue(items.all { it.source == "Anime2You" && it.sourceUrl.startsWith("https://www.anime2you.de/") })
    }

    @Test fun sameFactFromTwoSourcesIsOneRowWithBothConfirmations() {
        val first = row("Anime2You", "https://anime2you.example/item")
        val second = row("AniWorld", "https://aniworld.example/change")
        val merged = AnnouncementDeduplicator.merge(first, second)
        assertEquals(listOf("Anime2You", "AniWorld"), merged.sources.lines())
        assertEquals(2, merged.sourceUrls.lines().size)
    }

    @Test fun announcementWithoutDateIsNotARelease() {
        val item = Anime2YouRssParser.parse("""
            <rss><channel><item><title>Serie erhält zweite Staffel</title>
            <link>https://www.anime2you.de/news/example/</link><guid>x</guid>
            <pubDate>Sun, 09 Aug 2026 12:00:00 +0200</pubDate></item></channel></rss>
        """.trimIndent()).single()
        assertEquals(AnnouncementType.NEW_SEASON, item.type)
        assertEquals(null, item.newDate)
    }

    private fun row(source: String, url: String) = AnnouncementEntity(
        "id-$source", "same-fact", "anime", "Titel verschoben", null, "DELAY", 2,
        1, 2, null, null, null, 3, source, url, null, 4
    )
}
