package de.anisentinel.app.data.release

import de.anisentinel.app.domain.provider.ProviderEpisodeCheckResult
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class AniWorldEpisodeFallbackCheckerTest {
    @Test fun keepsGermanSubAndDubSeparate() {
        val html = """
            <html><body>S1E4
              <img class="flag" src="/public/img/flags/japanese-german.svg" />
              <img class="flag" src="/public/img/flags/german.svg" />
              <a href="/anime/stream/test/staffel/1/episode-4">Folge 4</a>
            </body></html>
        """.trimIndent()
        val result = AniWorldEpisodePageParser.parse(html, "https://aniworld.to/anime/stream/test/staffel/1/episode-4", 1, 4, "GER_DUB", Instant.EPOCH)
        val availability = (result as ProviderEpisodeCheckResult.Checked).availability
        assertEquals(true, availability.germanSubAvailable)
        assertEquals(true, availability.germanDubAvailable)
    }

    @Test fun changedMarkupIsRetryableInsteadOfFalseDelay() {
        val result = AniWorldEpisodePageParser.parse("<html>unexpected</html>", "https://aniworld.to/anime/stream/test/staffel/1/episode-4", 1, 4, "GER_SUB", Instant.EPOCH)
        assertEquals("ANIWORLD_FALLBACK_PARSER_CHANGED", (result as ProviderEpisodeCheckResult.Failed).code)
        assertTrue(result.retryable)
    }

    @Test fun realSeriesOverviewResolvesCalendarSeasonMismatchAndScopesLanguageToEpisode() {
        val html = """
          <table><tr><td><a href="/anime/stream/you-and-i-are-polar-opposites/staffel-1/episode-6">Folge 6</a></td>
            <td class="editFunctions"><a href="/anime/stream/you-and-i-are-polar-opposites/staffel-1/episode-6">
              <img class="flag" src="/public/img/japanese-german.svg" title="Mit deutschem Untertitel"></a></td></tr>
            <tr><td><a href="/anime/stream/you-and-i-are-polar-opposites/staffel-1/episode-5">Folge 5</a></td>
            <td><img class="flag" src="/public/img/german.svg"></td></tr></table>
        """.trimIndent()
        val result = AniWorldEpisodePageParser.parse(
            html, "https://aniworld.to/anime/stream/you-and-i-are-polar-opposites",
            2, 6, "GER_SUB", Instant.EPOCH
        ) as ProviderEpisodeCheckResult.Checked

        assertTrue(result.availability.episodeFound)
        assertEquals(true, result.availability.germanSubAvailable)
        assertEquals(false, result.availability.germanDubAvailable)
        assertTrue(result.availability.evidenceUrl!!.endsWith("/staffel-1/episode-6"))
    }

    @Test fun calendarOnlineMarkerConfirmsOnlyExactSeasonEpisodeAndLanguage() {
        val html = """
          <section class="calendarList"><div class="seriesListContainer">
            <div><a href="/anime/stream/you-and-i-are-polar-opposites/staffel-2/episode-6">
              <h3 class="seriesTitle">You and I Are Polar Opposites</h3><small>S02E06
              <img class="flag" data-src="/public/img/japanese-german.svg" title="Episode 6 mit deutschem Untertitel"></small>
              <small>10:40 Uhr <span title="Stream online!"></span></small></a></div>
            <div><a href="/anime/stream/you-and-i-are-polar-opposites/staffel-2/episode-5">
              <small>S02E05 <img class="flag" data-src="/public/img/german.svg"></small><span title="Stream online!"></span></a></div>
          </div></section>
        """.trimIndent()

        val result = AniWorldEpisodePageParser.parseCalendar(
            html, AniWorldCalendarParser.CALENDAR_URL, "you-and-i-are-polar-opposites",
            2, 6, "GER_SUB", Instant.EPOCH
        ) as ProviderEpisodeCheckResult.Checked

        assertTrue(result.availability.episodeFound)
        assertEquals(true, result.availability.germanSubAvailable)
        assertEquals(false, result.availability.germanDubAvailable)
    }

    @Test fun calendarPersistsSourceReportedAvailabilityTimeWhenMarkerExists() {
        val html = """
          <section class="calendarList"><div class="seriesListContainer"><div>
            <a href="/anime/stream/test/staffel-1/episode-6"><small>S01E06
            <img class="flag" data-src="/public/img/japanese-german.svg"></small>
            <small>Neu! 15:04 Uhr <span title="Stream online!"></span></small></a>
          </div></div></section>
        """.trimIndent()
        val checkedAt = Instant.parse("2026-08-09T13:13:00Z")
        val result = AniWorldEpisodePageParser.parseCalendar(
            html, AniWorldCalendarParser.CALENDAR_URL, "test", 1, 6, "GER_SUB", checkedAt
        ) as ProviderEpisodeCheckResult.Checked

        assertEquals(Instant.parse("2026-08-09T13:04:00Z"), result.availability.availableSince)
    }
}
