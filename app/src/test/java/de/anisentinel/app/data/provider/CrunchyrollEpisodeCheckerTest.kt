package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.ProviderEpisodeCheckResult
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CrunchyrollEpisodeCheckerTest {
    private val checkedAt = Instant.parse("2026-08-09T10:00:00Z")

    @Test fun parsesEpisodeAndGermanSubtitleEvidence() {
        val html = """
            <html><body>Frieren Episode 6 Season 2</body>
            <script>{"episodeNumber":6,"seasonNumber":2,"subtitleLocales":["de-DE","en-US"],"audioLocales":["ja-JP"]}</script></html>
        """.trimIndent()
        val result = CrunchyrollPublicPageParser.parse(html, "https://www.crunchyroll.com/de/series/X/frieren", "Frieren", 2, 6, checkedAt)
        val availability = (result as ProviderEpisodeCheckResult.Checked).availability
        assertTrue(availability.episodeFound)
        assertEquals(true, availability.germanSubAvailable)
        assertEquals(false, availability.germanDubAvailable)
    }

    @Test fun parserAmbiguityDoesNotBecomeUnavailable() {
        val result = CrunchyrollPublicPageParser.parse("<html>Frieren</html>", "https://www.crunchyroll.com/de/series/X/frieren", "Frieren", 2, 6, checkedAt)
        assertEquals("PARSER_CHANGED", (result as ProviderEpisodeCheckResult.Failed).code)
    }

    @Test fun parsesPublicSnakeCaseEpisodePayload() {
        val html = """
            <html><body>Mushoku Tensei: Jobless Reincarnation</body>
            <script>{"episode_number":"7","season_number":3,"subtitle_locales":["de-DE","en-US"],"audio_locale":"ja-JP"}</script></html>
        """.trimIndent()
        val result = CrunchyrollPublicPageParser.parse(
            html, "https://www.crunchyroll.com/watch/G9DU9NK45", "Mushoku Tensei: Jobless Reincarnation", 3, 7, checkedAt
        )
        val availability = (result as ProviderEpisodeCheckResult.Checked).availability
        assertTrue(availability.episodeFound)
        assertEquals(true, availability.germanSubAvailable)
        assertEquals(false, availability.germanDubAvailable)
    }

    @Test fun liveParserFailureCarriesStructuredResponseDiagnostic() {
        val result = CrunchyrollPublicPageParser.parse(
            "<html>generic shell</html>", "https://www.crunchyroll.com/watch/X", "Mushoku Tensei", 3, 7,
            checkedAt, 200, "text/html; charset=utf-8"
        )
        assertEquals(
            "PARSER_CHANGED|http=200|type=text/html|bytes=26|stage=TITLE_AND_EPISODE_MISSING",
            (result as ProviderEpisodeCheckResult.Failed).code
        )
    }

    @Test fun provenEpisodeBeyondTotalIsNegativeEvidence() {
        val html = "<html>Frieren<script>{\"totalEpisodeCount\":5}</script></html>"
        val result = CrunchyrollPublicPageParser.parse(html, "https://www.crunchyroll.com/de/series/X/frieren", "Frieren", 1, 6, checkedAt)
        assertFalse((result as ProviderEpisodeCheckResult.Checked).availability.episodeFound)
    }

    @Test fun parsesRealPublicGermanSeriesPageShapeWithoutLogin() {
        // Reduced fixture from the public Red River page, validated 2026-08-11.
        val html = """
            <html><body><h1>Red River</h1><section><h4>Staffel 1</h4>
            <a href="/de/watch/GE00379379JAJP/the-one-i-cant-love">Abspielen Episode 6 - Der Mann, in den ich mich nicht verlieben darf</a>
            <h3>E6 - Der Mann, in den ich mich nicht verlieben darf</h3><span>Untertitel</span>
            <dl><dt>Audio:</dt><dd>Japanese</dd><dt>Untertitel:</dt><dd>Deutsch, English</dd></dl>
            </section></body></html>
        """.trimIndent()
        val result = CrunchyrollPublicPageParser.parse(
            html, "https://www.crunchyroll.com/de/series/GT00378099/red-river", "Red River", 1, 6, checkedAt,
            200, "text/html"
        )
        val availability = (result as ProviderEpisodeCheckResult.Checked).availability
        assertTrue(availability.episodeFound)
        assertEquals(true, availability.germanSubAvailable)
        assertEquals("https://www.crunchyroll.com/de/watch/GE00379379JAJP/the-one-i-cant-love", availability.episodeUrl)
    }
}
