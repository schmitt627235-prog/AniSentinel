package de.anisentinel.app.data.anisearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniSearchHtmlParserTest {
    @Test
    fun parsesGermanMetadataAndProviderFromDetailDocument() {
        val html = """
            <html><head>
              <meta property="og:image" content="https://cdn.example/cover.jpg">
              <script type="application/ld+json">
                {"@type":"TVSeries","name":"Deutscher Titel","numberOfEpisodes":"12","startDate":"2026-04-01"}
              </script>
            </head><body>
              <h1>Deutscher Titel</h1>
              <section itemprop="description">Eine deutsche Beschreibung.</section>
              <a href="https://www.crunchyroll.com/de/series/ABC123/serie">Crunchyroll</a>
            </body></html>
        """.trimIndent()
        val result = AniSearchHtmlParser.parse(html, "https://www.anisearch.de/anime/12345/test")
        assertTrue(result is AniSearchParseResult.Success)
        val value = (result as AniSearchParseResult.Success).value
        assertEquals("12345", value.anisearchId)
        assertEquals("Deutscher Titel", value.titleGerman)
        assertEquals("CRUNCHYROLL", value.providers.single().normalizedProvider)
        assertEquals(12, value.totalEpisodes)
        assertEquals(2026, value.releaseYear)
    }

    @Test
    fun rejectsNonAniSearchSource() {
        val result = AniSearchHtmlParser.parse("<h1>Titel</h1>", "https://example.org/anime/1")
        assertEquals(AniSearchParseResult.InvalidSource("NOT_ANISEARCH_HTTPS"), result)
    }

    @Test
    fun parsesAndDeduplicatesSearchHits() {
        val html = """
            <li class="btype0"><a href="/anime/3633,test" lang="de"><span class="title">Testserie</span></a></li>
            <th class="showpop"><a href="/anime/3633,test">Testserie</a></th>
        """.trimIndent()
        val hits = AniSearchHtmlParser.parseSearchResults(
            html, "https://www.anisearch.de/anime/index/?text=Testserie"
        )
        assertEquals(1, hits.size)
        assertEquals("3633", hits.single().anisearchId)
    }
}
