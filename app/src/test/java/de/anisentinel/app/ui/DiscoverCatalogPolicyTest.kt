package de.anisentinel.app.ui

import de.anisentinel.app.data.local.JustWatchCatalogTitleEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverCatalogPolicyTest {
    @Test fun rejectsOrdinaryJustWatchMoviesEvenWhenTheyAreAnimation() {
        assertFalse(DiscoverCatalogPolicy.isVisible(row("IF: Imaginäre Freunde", "MOVIE", "Animation", "Amazon Video"), emptySet()))
    }

    @Test fun acceptsAnimeMoviesAndShowsWithExplicitAnimeEvidence() {
        assertTrue(DiscoverCatalogPolicy.isVisible(row("Anime Film", "MOVIE", "Anime,Fantasy", "Netflix"), emptySet()))
        assertTrue(DiscoverCatalogPolicy.isVisible(row("Anime Serie", "SHOW", "Fantasy", "Crunchyroll"), emptySet()))
    }

    @Test fun acceptsProvenLiveActionAdaptations() {
        assertTrue(DiscoverCatalogPolicy.isVisible(
            row("Adaption", "MOVIE", "Drama", "Netflix", "Live-Action-Verfilmung des bekannten Manga."), emptySet()
        ))
    }

    @Test fun keepsTitlesConfirmedByTheReleasePipeline() {
        val row = row("Aktueller Titel", "SHOW", "Drama", "Netflix", id = "justwatch:current")
        assertTrue(DiscoverCatalogPolicy.isVisible(row, setOf("justwatch:current")))
        assertTrue(DiscoverCatalogPolicy.isVisible(row.copy(internalAnimeId = "aniworld:current"), emptySet()))
    }

    private fun row(
        title: String,
        type: String,
        genres: String,
        providers: String,
        description: String? = null,
        id: String = "justwatch:test"
    ) = JustWatchCatalogTitleEntity(
        "jw", id, title, 2026, type, genres, null, null, providers, "", null, null,
        1, "TEST", null, description
    )
}
