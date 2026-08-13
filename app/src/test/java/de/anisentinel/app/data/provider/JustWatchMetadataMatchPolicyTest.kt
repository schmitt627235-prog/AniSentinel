package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.JustWatchCatalogTitle
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JustWatchMetadataMatchPolicyTest {
    @Test
    fun acceptsSingleExactGermanCatalogMatch() {
        val match = JustWatchMetadataMatchPolicy.uniqueCandidate(
            "Dara-san of the Reiwa Era", 2026, "SHOW",
            listOf(title("jw:1", "Dara-san of the Reiwa Era", 2026))
        )
        assertEquals("jw:1", match?.justWatchId)
    }

    @Test
    fun rejectsAmbiguousMatches() {
        val candidates = listOf(title("jw:1", "One Piece", 1999), title("jw:2", "One Piece", 1999))
        assertNull(JustWatchMetadataMatchPolicy.uniqueCandidate("One Piece", 1999, "SHOW", candidates))
    }

    @Test
    fun doesNotMixOnePieceWith2023Title() {
        assertNull(JustWatchMetadataMatchPolicy.uniqueCandidate(
            "One Piece", 1999, "SHOW", listOf(title("jw:2023", "One Piece (2023)", 2023))
        ))
    }

    private fun title(id: String, name: String, year: Int) = JustWatchCatalogTitle(
        id, name, year, "SHOW", emptySet(), null, null, emptySet(), emptyMap(),
        null, null, Instant.EPOCH
    )
}
