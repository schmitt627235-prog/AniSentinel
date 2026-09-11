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

    @Test fun titleNewsMatchesAliasesAndSortsNewestFirst() {
        val old = row("Anime2You", "https://www.anime2you.de/news/old").copy(
            announcementId = "old", title = "Neues zu Kusuriya no Hitorigoto", publishedAt = 10
        )
        val current = row("Anime2You", "https://www.anime2you.de/news/new").copy(
            announcementId = "new", title = "Die Tagebücher der Apothekerin: Staffel 3 bestätigt", publishedAt = 20
        )
        val unrelated = row("Anime2You", "https://www.anime2you.de/news/other").copy(
            announcementId = "other", title = "Ein anderer Anime", publishedAt = 30
        )
        assertEquals(
            listOf("new", "old"),
            Anime2YouTitleNewsMatcher.matching(
                listOf(old, unrelated, current),
                listOf("Die Tagebücher der Apothekerin", "Kusuriya no Hitorigoto")
            ).map { it.announcementId }
        )
    }

    @Test fun persistentNewsCacheHasBoundedLifetime() {
        assertTrue(Anime2YouNewsCachePolicy.isFresh(1_000, 1_899))
        assertTrue(!Anime2YouNewsCachePolicy.isFresh(1_000, 1_900))
        assertTrue(!Anime2YouNewsCachePolicy.isFresh(null, 1_100))
    }

    @Test fun titleVariantsIncludeOriginalAndArticleFreeFormWithoutDuplicates() {
        assertEquals(
            listOf("The Ancient Magus' Bride", "Ancient Magus' Bride", "魔法使いの嫁"),
            Anime2YouTitleVariants.build(listOf(" The Ancient Magus' Bride ", "the ancient magus' bride", "魔法使いの嫁", "null", ""))
        )
    }

    @Test fun normalizationHandlesUnicodeCaseAndPunctuationGenerically() {
        assertEquals("café au lait season 2", Anime2YouTitleNormalizer.normalize("  CAFÉ—au: Lait! Season-2  "))
        assertEquals(
            null,
            Anime2YouTitleNewsMatcher.rejectionReason("Neuigkeiten zu »SPY × FAMILY«", null, listOf("Spy x Family"))
        )
        assertEquals("TITLE_MISMATCH", Anime2YouTitleNewsMatcher.rejectionReason("Neuigkeiten zu One Piece", null, listOf("Frieren")))
    }

    @Test fun searchHtmlParsesAndCanonicalizesUniqueArticles() {
        val html = """
            <html><body>
              <div class="tdb_module_loop"><h3 class="entry-title"><a href="https://www.anime2you.de/news/123/example/?tracking=x">Anime: Titel – neue Staffel</a></h3>
                <time datetime="2026-09-10T12:30:00+02:00"></time><div class="td-excerpt">Kurze Vorschau.</div></div>
              <div class="tdb_module_loop"><h3 class="entry-title"><a href="https://www.anime2you.de/news/123/example/">Anime: Titel – neue Staffel</a></h3></div>
            </body></html>
        """.trimIndent()
        val page = Anime2YouSearchParser.parse(html)
        assertEquals(2, page.rawCount)
        assertEquals(1, page.items.size)
        assertEquals("https://www.anime2you.de/news/123/example/", page.items.single().sourceUrl)
        assertEquals("Kurze Vorschau.", page.items.single().summary)
    }

    @Test fun nativeSynonymAlternativeAndPunctuationVariantsCanMatch() {
        val variants = listOf("Bocchi the Rock!", "ぼっち・ざ・ろっく！", "Bocchi, the Rock", "Alternative Name")
        variants.forEach { variant ->
            assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason("Artikel zu $variant: Trailer", null, variants))
        }
    }

    @Test fun staleOrNegativeTitleCacheNeverBlocksAQuery() {
        assertTrue(!Anime2YouTitleNewsCachePolicy.isFresh(1_000, 1_001, resultCount = 0))
        assertTrue(!Anime2YouTitleNewsCachePolicy.isFresh(1_000, 1_001, resultCount = 2, version = 1))
        assertTrue(Anime2YouTitleNewsCachePolicy.isFresh(1_000, 1_001, resultCount = 2))
    }

    @Test fun technicalFailureIsDifferentFromSuccessfulEmptyNews() {
        val failure: Anime2YouTitleNewsResult = Anime2YouTitleNewsResult.Failure("NETWORK_ERROR")
        val empty: Anime2YouTitleNewsResult = Anime2YouTitleNewsResult.Success(emptyList(), Anime2YouSearchMetrics())
        assertTrue(failure is Anime2YouTitleNewsResult.Failure)
        assertTrue(empty is Anime2YouTitleNewsResult.Success)
    }

    private fun row(source: String, url: String) = AnnouncementEntity(
        "id-$source", "same-fact", "anime", "Titel verschoben", null, "DELAY", 2,
        1, 2, null, null, null, 3, source, url, null, 4
    )
}
