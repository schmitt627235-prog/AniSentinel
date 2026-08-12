package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.ProviderAvailabilityResult
import de.anisentinel.app.domain.provider.ProviderCheckRequest
import de.anisentinel.app.domain.provider.EvidenceType
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrunchyrollCalendarCheckerTest {
    private val checkedAt = Instant.parse("2026-07-30T20:00:00Z")
    private val request = ProviderCheckRequest(
        "frieren", "Frieren – Nach dem Ende der Reise", 7,
        LocalDate.of(2026, 7, 30), null
    )

    @Test
    fun `series and expected episode confirm availability`() = runBlocking {
        val checker = checker("""<article><h3>Frieren – Nach dem Ende der Reise</h3><p>Folge 7 Verfügbar</p></article>""")
        val result = checker.checkAvailability(request)
        assertTrue(result is ProviderAvailabilityResult.Available)
        result as ProviderAvailabilityResult.Available
        assertEquals(7, result.episodeNumber)
        assertTrue(result.pageUrl.startsWith("https://www.crunchyroll.com/de/simulcastcalendar"))
        assertEquals(checkedAt, result.checkedAt)
        assertEquals(EvidenceType.RELEASE_CALENDAR, result.evidenceType)
    }

    @Test
    fun `series without expected episode stays missing`() = runBlocking {
        val result = checker("<h3>Frieren – Nach dem Ende der Reise</h3><p>Folge 6 Verfügbar</p>")
            .checkAvailability(request)
        assertTrue(result is ProviderAvailabilityResult.TitleFoundEpisodeMissing)
    }

    @Test
    fun `network failure is not title not found`() = runBlocking {
        val checker = CrunchyrollCalendarChecker(Clock.fixed(checkedAt, ZoneOffset.UTC)) {
            throw IOException("offline")
        }
        assertTrue(checker.checkAvailability(request) is ProviderAvailabilityResult.NetworkError)
    }

    @Test
    fun `login wall is classified separately`() = runBlocking {
        val result = checker("<main>Bitte anmelden, um Inhalte zu sehen</main>")
            .checkAvailability(request)
        assertTrue(result is ProviderAvailabilityResult.LoginRequired)
    }

    private fun checker(html: String) = CrunchyrollCalendarChecker(
        Clock.fixed(checkedAt, ZoneOffset.UTC)
    ) { html }
}
