package de.anisentinel.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JustWatchPublicMetadataParserTest {
    @Test fun decodesDescriptionAndGenres() {
        val result = JustWatchPublicMetadataParser.parse("""<script type="application/ld+json">{"@type":"TVSeries","description":"It&#x27;s real &amp; verified","genre":["Action &amp; Abenteuer","Action & Abenteuer"]}</script>""")
        assertEquals("It's real & verified", result.description)
        assertEquals(setOf("Action & Abenteuer"), result.genres)
    }
    @Test
    fun parsesDescriptionGenresAndStudiosFromPublicJsonLd() {
        val html = """
            <html><head><script type="application/ld+json">
            {
              "@type":"TVSeries",
              "description":"Eine echte Beschreibung.",
              "genre":["Anime","Fantasy","Anime"],
              "productionCompany":[{"@type":"Organization","name":"Studio Sentinel"}]
            }
            </script></head></html>
        """.trimIndent()

        val result = JustWatchPublicMetadataParser.parse(html)

        assertEquals("Eine echte Beschreibung.", result.description)
        assertEquals(setOf("Anime", "Fantasy"), result.genres)
        assertEquals(setOf("Studio Sentinel"), result.studios)
    }

    @Test
    fun missingStudioDoesNotInventAValue() {
        val result = JustWatchPublicMetadataParser.parse(
            """<meta name="description" content="Nur Beschreibung">"""
        )
        assertEquals("Nur Beschreibung", result.description)
        assertEquals(emptySet<String>(), result.studios)
    }
}
