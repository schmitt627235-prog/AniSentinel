package de.anisentinel.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleTvPublicCatalogParserTest {
    private val showId = "umc.cmc.o4e5fbtkmgjivlpghedf8a6x"
    private val url = "https://tv.apple.com/de/show/detektiv-conan/$showId"

    @Test fun parsesOnlyPubliclyRenderedConanEpisodesAndMarksPartial() {
        val catalog = AppleTvPublicCatalogParser.parse(page(), url, setOf("Detektiv Conan"))!!
        assertEquals(showId, catalog.showId)
        assertEquals(AppleTvCatalogCompleteness.PARTIAL, catalog.completeness)
        assertEquals(listOf(2, 3), catalog.episodes.map { it.number })
        assertEquals(listOf(1, 1), catalog.episodes.map { it.seasonNumber })
        assertEquals("Kleiner Mann ganz gro?", catalog.episodes.first().title)
        assertEquals("24 Min.", catalog.episodes.first().duration)
        assertEquals("Anfang der Geschichte.", catalog.episodes.first().description)
        assertTrue(catalog.episodes.first().url.contains("/de/episode/"))
    }

    @Test fun rejectsForeignShowLinksAndSpinOffMatches() {
        assertNull(AppleTvPublicCatalogParser.parse(page(), url, setOf("Detektiv Conan: Zero's Tea Time")))
        assertNull(AppleTvPublicCatalogParser.parse(page(), "https://example.com/de/show/x/$showId", setOf("Detektiv Conan")))
        val wrongShow = page().replace("showId=$showId", "showId=umc.cmc.other")
        assertTrue(AppleTvPublicCatalogParser.parse(wrongShow, url, setOf("Detektiv Conan"))!!.episodes.isEmpty())
    }

    @Test fun appleTvStoreAndAppleTvPlusStaySeparate() {
        assertTrue(AppleTvCatalogImporter.isAppleTvStoreProvider("Apple TV Store"))
        assertTrue(AppleTvCatalogImporter.isAppleTvStoreProvider("Apple TV"))
        assertFalse(AppleTvCatalogImporter.isAppleTvStoreProvider("Apple TV+"))
        assertFalse(AppleTvCatalogImporter.isAppleTvStoreProvider("Apple TV Plus"))
    }

    @Test fun durationComesFromInnerMetadataNotTheWholeEpisodeCard() {
        val nested = page().replace(
            "<div class=\"metadata\">24 Min.</div>",
            "<div class=\"metadata\"><div class=\"progress-and-attribution\"><div class=\"metadata\">24 Min.</div></div></div>"
        )
        val catalog = AppleTvPublicCatalogParser.parse(nested, url, setOf("Detektiv Conan"))!!
        assertEquals("24 Min.", catalog.episodes.first().duration)
    }

    @Test fun embeddedPublicSeasonIdsAreKeptWithoutInventingTheirEpisodes() {
        val html = page() + """
            <script type="application/json">{"seasons":[{"episodeCount":41,"id":"umc.cmc.seasonone","seasonNumber":1,"title":"Staffel 1"},{"episodeCount":27,"id":"umc.cmc.seasonthirtyone","seasonNumber":31,"title":"Staffel 31"}]}</script>
        """.trimIndent()
        val catalog = AppleTvPublicCatalogParser.parse(html, url, setOf("Detektiv Conan"))!!
        assertEquals(listOf(1, 31), catalog.seasons.map { it.number })
        assertEquals("umc.cmc.seasonthirtyone", catalog.seasons.last().id)
        assertEquals(2, catalog.episodes.size)
        assertEquals(AppleTvCatalogCompleteness.PARTIAL, catalog.completeness)
    }

    private fun page() = """
        <h1>Detektiv Conan</h1>
        <select data-testid="accessory-button-select">
          <option>Staffel 1</option><option>Staffel 2</option>
        </select>
        <a href="https://tv.apple.com/de/episode/kleiner-mann/umc.cmc.ep2?showId=$showId">
          <div class="tag">FOLGE 2</div><div class="title">Kleiner Mann ganz gro?</div>
          <div class="description">Anfang der Geschichte.</div><div class="metadata">24 Min.</div>
        </a>
        <a href="https://tv.apple.com/de/episode/doppelgaengerin/umc.cmc.ep3?showId=$showId">
          <div class="tag">FOLGE 3</div><div class="title">Die Doppelg?ngerin</div>
        </a>
    """.trimIndent()
}
