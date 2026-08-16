package de.anisentinel.app.domain.watcher

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseTimePolicyTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun release(id: String, at: String, precision: String = "EXACT") = EpisodeReleaseEntity(
        id, "anime", 2, null, Instant.parse(at).epochSecond, "Crunchyroll", "TEST", null, null,
        1, seasonNumber = 1, releaseLanguage = "GER_SUB", releaseTimePrecision = precision
    )

    @Test fun `date-only midnight derives recurring title time`() {
        val regular = release("regular", "2026-08-01T15:00:00Z")
        val unknown = release("unknown", "2026-08-08T22:00:00Z", "DATE")
        val resolved = ReleaseTimePolicy.resolve(unknown, listOf(regular), zone)!!
        assertEquals("2026-08-09T15:00:00Z", Instant.ofEpochSecond(resolved.epochSecond).toString())
        assertEquals("DERIVED_TITLE_PATTERN", resolved.precision)
    }

    @Test fun `explicit real midnight remains exact`() {
        val midnight = release("midnight", "2026-08-08T22:00:00Z", "EXACT_MIDNIGHT")
        assertEquals(midnight.expectedAt, ReleaseTimePolicy.resolve(midnight, emptyList(), zone)?.epochSecond)
    }

    @Test fun `unknown midnight without evidence remains date-only`() {
        val unknown = release("unknown", "2026-08-08T22:00:00Z", "DATE")
        assertEquals("DATE", ReleaseTimePolicy.resolve(unknown, emptyList(), zone)?.precision)
    }
}
