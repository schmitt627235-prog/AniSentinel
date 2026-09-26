package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.JustWatchCatalogTitle
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JustWatchUpcomingMatchPolicyTest {
    @Test fun uniqueGermanCandidateEnrichesAniListIdentity() {
        val result = JustWatchUpcomingMatchPolicy.uniqueCandidate(
            listOf("Kusuriya no Hitorigoto 3rd Season", "The Apothecary Diaries Season 3"),
            "The Apothecary Diaries Season 3", 2026, "SHOW", 3,
            listOf(candidate("Die Tageb?cher der Apothekerin: Staffel 3", 2026, "SHOW"))
        )
        assertEquals("Die Tageb?cher der Apothekerin: Staffel 3", result?.title)
    }

    @Test fun ambiguityWrongSeasonAndWrongFormatAreRejected() {
        val aliases = listOf("Example Season 3")
        assertNull(JustWatchUpcomingMatchPolicy.uniqueCandidate(
            aliases, aliases.single(), 2027, "SHOW", 3,
            listOf(candidate("Beispiel Staffel 3", 2027, "SHOW", "a"), candidate("Beispiel ? Dritte Staffel", 2027, "SHOW", "b"))
        ))
        assertNull(JustWatchUpcomingMatchPolicy.uniqueCandidate(
            aliases, aliases.single(), 2027, "SHOW", 3,
            listOf(candidate("Example Season 2", 2027, "SHOW"))
        ))
        assertNull(JustWatchUpcomingMatchPolicy.uniqueCandidate(
            aliases, aliases.single(), 2027, "SHOW", 3,
            listOf(candidate("Example Season 3", 2027, "MOVIE"))
        ))
        assertNull(JustWatchUpcomingMatchPolicy.uniqueCandidate(
            listOf("The Apothecary Diaries Season 3"), "The Apothecary Diaries Season 3", 2026, "SHOW", 3,
            listOf(candidate("Clevatess", 2026, "SHOW"))
        ))
    }

    private fun candidate(title: String, year: Int, type: String, id: String = "id") = JustWatchCatalogTitle(
        id, title, year, type, emptySet(), null, null, emptySet(), emptyMap(), null, null, Instant.EPOCH
    )
}
