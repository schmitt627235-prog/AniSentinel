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

    @Test fun equivalentSeasonWordingMatchesReleaseArticles() {
        assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason(
            "Konkreter Termin von »Black Clover Second Season« + Visual", null,
            listOf("Black Clover Season 2")
        ))
        assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason(
            "Starttermin der dritten Staffel von »The Apothecary Diaries« + Trailer", null,
            listOf("The Apothecary Diaries Season 3")
        ))
        val searchVariants = Anime2YouTitleVariants.build(listOf("Black Clover 2nd Season", "Black Clover Season 2"))
        assertTrue("Black Clover 2nd Season" in searchVariants)
        assertTrue("Black Clover Season 2" in searchVariants)
        assertTrue("The Apothecary Diaries" in Anime2YouTitleVariants.searchQueries(
            listOf("The Apothecary Diaries Season 3")
        ))
    }

    @Test fun commonRomajiTranscriptionVariantMatches() {
        assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason(
            "Termin von »Tokidoki Bosotto Russia-go de Dereru Tonari no Alya-san Season 2«",
            null,
            listOf("Tokidoki Bosotto Russiya-go de Dereru Tonari no Alya-san Season 2")
        ))
        assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason(
            "Start der zweiten »Alya Hides Her Feelings«-Staffel verschoben",
            null,
            listOf("Alya Sometimes Hides Her Feelings in Russian Season 2")
        ))
        assertTrue(Anime2YouReleaseNewsClassifier.classify(
            "Neue Alya-Figur angekündigt", "Release im Januar 2027 bei einem Figurenhändler."
        ).isEmpty())
        assertTrue(Anime2YouReleaseNewsClassifier.classify(
            "Neue Character-Song-CD zu Alya", "Die CD erscheint im Februar 2027."
        ).isEmpty())
    }

    @Test fun colonFranchiseTitleGetsSeasonSafeShortAlias() {
        val variants = Anime2YouTitleVariants.build(listOf(
            "Tsukimichi: Moonlit Fantasy Season 3",
            "Tsuki ga Michibiku Isekai Douchuu Dai San Maku"
        ))
        assertTrue("Tsukimichi Season 3" in variants)
        assertEquals(
            Anime2YouTitleNormalizer.normalize("Tsukimichi Season 3"),
            Anime2YouTitleNormalizer.normalize("Tsukimichi Dai San Maku")
        )
        assertEquals("TITLE_MISMATCH", Anime2YouTitleNewsMatcher.rejectionReason(
            "Termin des zweiten Volumes der zweiten »TSUKIMICHI«-Staffel", null, variants
        ))
        assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason(
            "Start der dritten »TSUKIMICHI«-Staffel steht fest", null, variants
        ))
    }

    @Test fun sequelRejectsUnnumberedFirstSeasonAndEditorialArticles() {
        val sequel = listOf("Witch Hat Atelier Season 2", "Tongari Boushi no Atelier 2nd Season")
        assertEquals("TITLE_MISMATCH", Anime2YouTitleNewsMatcher.rejectionReason(
            "Start der Fantasy-Serie »Witch Hat Atelier« verschoben", null, sequel
        ))
        assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason(
            "Erster Teaser zur zweiten Staffel von »Witch Hat Atelier«", null, sequel
        ))
        assertTrue(Anime2YouReleaseNewsClassifier.classify(
            "»Witch Hat Atelier«-Autorin verspricht: Das Warten auf Staffel 2 lohnt sich",
            "Die erste Staffel ist bei Crunchyroll verfügbar."
        ).isEmpty())
        assertTrue(Anime2YouReleaseNewsClassifier.classify(
            "Neue Kooperation zu Witch Hat Atelier", "Die Serie startet 2026 bei Crunchyroll."
        ).isEmpty())
        assertTrue(ReleaseNewsCategory.TRAILER_TEASER in Anime2YouReleaseNewsClassifier.classify(
            "Erster Teaser zur zweiten Staffel von Witch Hat Atelier", "Das Video wurde veröffentlicht."
        ))
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

    @Test fun articleContentClassifiesOnlyReleaseRelevantFacts() {
        assertTrue(ReleaseNewsCategory.RELEASE_DATE in Anime2YouReleaseNewsClassifier.classify(
            "Starttermin steht fest", "Die Serie startet im Oktober 2027 bei Crunchyroll in Deutschland."
        ))
        assertTrue(ReleaseNewsCategory.POSTPONEMENT in Anime2YouReleaseNewsClassifier.classify(
            "Anime verschoben", "Der neue Termin ist der 5. Januar 2027."
        ))
        assertTrue(ReleaseNewsCategory.STREAMING_PROVIDER in Anime2YouReleaseNewsClassifier.classify(
            "Crunchyroll zeigt die Serie", "Der Simulcast ist für Deutschland bestätigt."
        ))
        assertTrue(ReleaseNewsCategory.NO_DACH_STREAMING_LICENSE in Anime2YouReleaseNewsClassifier.classify(
            "Keine Streaminglizenz", "Kein Simulcast für Deutschland ist geplant."
        ))
        assertTrue(ReleaseNewsCategory.STREAMING_PROVIDER in Anime2YouReleaseNewsClassifier.classify(
            "Netflix zeigt Fool Night weltweit exklusiv", "Der Anime wurde für das Netflix-Programm angekündigt."
        ))
        assertEquals("TITLE_MISMATCH", Anime2YouTitleNewsMatcher.rejectionReason(
            "Netflix zeigt Witch Hat Atelier weltweit exklusiv", null, listOf("Witch Hat Atelier Season 2")
        ))
        assertEquals(null, Anime2YouTitleNewsMatcher.rejectionReason(
            "Netflix zeigt Witch Hat Atelier Staffel 2 weltweit exklusiv", null, listOf("Witch Hat Atelier Season 2")
        ))
        assertTrue(ReleaseNewsCategory.PHYSICAL_RELEASE_ONLY in Anime2YouReleaseNewsClassifier.classify(
            "Deutscher Disc-Release", "Die Komplettbox erscheint auf DVD und Blu-ray."
        ))
        assertTrue(ReleaseNewsCategory.PHYSICAL_RELEASE_ONLY in Anime2YouReleaseNewsClassifier.classify(
            "Releaseplan und Steelbook vorgestellt", "Die Collector's Edition erscheint später."
        ))
        assertTrue(ReleaseNewsCategory.TRAILER_TEASER in Anime2YouReleaseNewsClassifier.classify(
            "Neuer Trailer und Visual", "Das Video zeigt neue Charaktere."
        ))
        assertTrue(Anime2YouReleaseNewsClassifier.classify(
            "Neue Figuren vorgestellt", "Merchandise zur Serie wurde präsentiert."
        ).isEmpty())
        assertTrue(Anime2YouReleaseNewsClassifier.classify(
            "Neues Merchandise zu Clevatess", "Die Serie läuft bei Crunchyroll. Neue Figuren und CDs wurden vorgestellt."
        ).isEmpty())
    }

    @Test fun articleParserUsesBodyInAdditionToHeadline() {
        val article = Anime2YouArticleParser.parse("""
            <article><h1 class="entry-title">Neuer Trailer veröffentlicht</h1>
            <time datetime="2026-09-12T10:00:00+02:00"></time>
            <div class="td-post-content"><p>Der Simulcast startet im Oktober 2026 bei Netflix in Deutschland.</p></div></article>
        """.trimIndent(), "https://www.anime2you.de/news/123/test/")
        assertEquals("Neuer Trailer veröffentlicht", article.title)
        assertTrue(article.text.contains("Netflix"))
        assertTrue(ReleaseNewsCategory.DACH_LICENSE in Anime2YouReleaseNewsClassifier.classify(article.title, article.text))
    }

    private fun row(source: String, url: String) = AnnouncementEntity(
        "id-$source", "same-fact", "anime", "Titel verschoben", null, "DELAY", 2,
        1, 2, null, null, null, 3, source, url, null, 4
    )
}
