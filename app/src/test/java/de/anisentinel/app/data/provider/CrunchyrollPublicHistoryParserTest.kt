package de.anisentinel.app.data.provider

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrunchyrollPublicHistoryParserTest {
    @Test fun parsesConcreteEpisodeDateAndLanguageWithoutGuessing() {
        val rows = CrunchyrollPublicHistoryParser.parse(FIXTURE, "https://www.crunchyroll.com/de/series/GT123/test")
        assertEquals(2, rows.size)
        assertEquals(setOf("GER_SUB", "GER_DUB"), rows.map { it.releaseLanguages.single() }.toSet())
        assertTrue(rows.all { it.seasonNumber == 2 && it.episodeNumber == 6 })
        assertTrue(rows.all { it.releaseDate == LocalDate.of(2026, 8, 1) })
        assertTrue(rows.all { it.providerEpisodeUrl.endsWith("/watch/GABC12345/folge-6") })
    }

    @Test fun ignoresRowsWithoutPublicDateOrConcreteLanguage() {
        val html = "<h4>Staffel 1</h4><div><a href='/watch/GX/episode-1'>Episode 1</a></div>"
        assertTrue(CrunchyrollPublicHistoryParser.parse(html, "https://www.crunchyroll.com/de/series/G/test").isEmpty())
    }

    private companion object {
        const val FIXTURE = """
            <h4>Staffel 2</h4>
            <article><span>01.08.2026</span><span>Untertitel</span>
              <a href="/de/watch/GABC12345/folge-6">Episode 6 - Veröffentlichung</a></article>
            <article><span>01.08.2026</span><span>Synchro</span>
              <a href="/de/watch/GABC12345/folge-6">Episode 6 - Veröffentlichung</a></article>
        """
    }
}
