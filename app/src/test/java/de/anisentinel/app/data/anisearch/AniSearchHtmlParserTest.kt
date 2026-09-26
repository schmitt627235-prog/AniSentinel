package de.anisentinel.app.data.anisearch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    @Test
    fun parsesConfirmedGermanFuturePublisherAndAvailabilityMonth() {
        val html = """
            <html><body><h1>Die Tagebücher der Apothekerin: Staffel 3 - Cour 1</h1>
            <div itemprop="description">Künftige Staffel.</div><ul class="xlist row simple infoblock">
              <li><img src="https://cdn.anisearch.de/media/country/de.webp" class="flag" alt="Deutsch" title="Deutsch">
              <div class="title" lang="de"><strong>Die Tagebücher der Apothekerin: Staffel 3 - Cour 1</strong></div>
              <div class="status"><span class="header">Status:</span> Zukünftig</div>
              <div class="released"><span class="header">Veröffentlicht:</span> 10.2026 ‑ ?</div>
              <div class="company"><span class="header">Publisher:</span> <a href="company/1258,crunchyroll">Crunchyroll</a></div></li>
            </ul></body></html>
        """.trimIndent()
        val value = (AniSearchHtmlParser.parse(html, "https://www.anisearch.de/anime/20704,test") as AniSearchParseResult.Success).value
        assertTrue(value.dachLicensed)
        assertTrue(value.regionalReleaseBlockPresent)
        assertEquals("Crunchyroll", value.dachPublisher)
        assertEquals(null, value.dachAvailableFrom)
        assertEquals("10.2026", value.dachAvailablePeriod)
        assertEquals("20704", value.anisearchId)
    }

    @Test
    fun confirmsDachReleaseWithoutRequiringPublisher() {
        val html = """
            <html><body><h1>Lizenzierter Titel</h1>
            <div itemprop="description">Beschreibung.</div><ul class="xlist row simple infoblock">
              <li><img src="https://cdn.anisearch.de/media/country/de.webp" class="flag" alt="Deutsch" title="Deutsch">
              <div class="title" lang="de"><strong>Lizenzierter Titel</strong></div>
              <div class="status">Status: Zukünftig</div>
              <div class="released">Veröffentlicht: Herbst 2026 - ?</div></li>
            </ul></body></html>
        """.trimIndent()
        val value = (AniSearchHtmlParser.parse(html, "https://www.anisearch.de/anime/99999,test") as AniSearchParseResult.Success).value
        assertTrue(value.dachLicensed)
        assertEquals(null, value.dachPublisher)
        assertEquals("Herbst 2026", value.dachAvailablePeriod)
    }

    @Test
    fun germanFlagOutsideRegionalInfoblockDoesNotConfirmLicense() {
        val html = """
            <html><body><h1>Titel</h1><div itemprop="description">Beschreibung.</div>
            <img src="https://cdn.anisearch.de/media/country/de.webp" class="flag" alt="Deutsch" title="Deutsch">
            </body></html>
        """.trimIndent()
        val value = (AniSearchHtmlParser.parse(html, "https://www.anisearch.de/anime/99998,test") as AniSearchParseResult.Success).value
        assertTrue(!value.dachLicensed)
        assertTrue(!value.regionalReleaseBlockPresent)
    }

    @Test
    fun missingGermanEntryInPresentRegionalBlockIsAValidNegativeObservation() {
        val html = """
            <html><body><h1>Unlizenzierter Future-Titel</h1><div itemprop="description">Beschreibung.</div>
            <ul class="xlist row simple infoblock"><li><img class="flag" src="/media/country/jp.webp" alt="Japanisch"></li></ul>
            </body></html>
        """.trimIndent()
        val value = (AniSearchHtmlParser.parse(html, "https://www.anisearch.de/anime/99997,test") as AniSearchParseResult.Success).value
        assertTrue(value.regionalReleaseBlockPresent)
        assertTrue(!value.dachLicensed)
    }
}
