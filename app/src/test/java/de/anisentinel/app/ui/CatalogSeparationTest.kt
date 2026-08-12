package de.anisentinel.app.ui

import org.junit.Test
import org.junit.Assert.assertEquals
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.ReleaseStatus

class CatalogSeparationTest {
    @Test
    fun pokemonQueriesNormalizeToSameValue() {
        val queries = listOf("pokemon", "Pokemon", "Pokémon", "POKEMON", "POKÉMON")
        assertEquals(setOf("pokemon"), queries.map(CatalogTextNormalizer::normalize).toSet())
    }

    @Test
    fun physicalOffersAreNotPrimaryProviders() {
        assertEquals(
            listOf("Crunchyroll", "Netflix", "Prime Video"),
            StreamingProviderPolicy.visible(
                listOf("Amazon DVD / Blu-ray", "Buecher", "Medimops", "Thalia", "Netflix", "Crunchyroll", "Prime Video")
            )
        )
    }

    @Test
    fun providerSortDirectionsAreVisiblyOpposite() {
        val rows = listOf("ADN", "Crunchyroll", "Netflix", "Prime Video").map(::anime)
        assertEquals(
            listOf("ADN", "Crunchyroll", "Netflix", "Prime Video"),
            rows.sortedWith(FavoritesSorter.comparator(FavoritesSort.PROVIDER_ASC)).map { it.provider }
        )
        assertEquals(
            listOf("Prime Video", "Netflix", "Crunchyroll", "ADN"),
            rows.sortedWith(FavoritesSorter.comparator(FavoritesSort.PROVIDER_DESC)).map { it.provider }
        )
    }

    @Test
    fun catalogLazyKeysRemainUniqueWhenTitlesShareInternalAnimeId() {
        val rows = listOf(
            CatalogAnimeItem("ts372486", "justwatch:shared", "Pokémon", "Serie", emptyList(), null),
            CatalogAnimeItem("tm1448177", "justwatch:shared", "Pokémon", "Film", emptyList(), null)
        )
        assertEquals(2, rows.map { it.stableKey }.distinct().size)
    }

    private fun anime(provider: String) = Anime(
        id = provider, title = provider, subtitle = "", provider = provider,
        expectedReleaseAt = null, episode = 0, status = ReleaseStatus.UNKNOWN,
        accentSeed = 0
    )
}
