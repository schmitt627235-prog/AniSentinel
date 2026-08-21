package de.anisentinel.app.ui

import de.anisentinel.app.data.local.JustWatchCatalogTitleEntity
import java.util.Locale

/** Keeps Discover anime-focused even when an upstream genre filter is ignored or broadened. */
object DiscoverCatalogPolicy {
    private val animeProviders = setOf("crunchyroll", "adn", "animation digital network", "aniverse")
    private val animeGenres = setOf("anime", "japanimation")
    private val adaptationMarkers = listOf(
        "live-action-adaption", "live action adaption", "live-action-adaptation",
        "live action adaptation", "live-action-verfilmung", "live action verfilmung"
    )

    fun isVisible(row: JustWatchCatalogTitleEntity, releaseAnimeIds: Set<String>): Boolean {
        val animeId = row.internalAnimeId ?: return false
        if (animeId in releaseAnimeIds || !animeId.startsWith("justwatch:")) return true

        val genres = row.csvGenres().map { it.lowercase(Locale.ROOT) }.toSet()
        if (genres.any(animeGenres::contains)) return true

        val providers = row.csvProviders().map { it.lowercase(Locale.ROOT) }
        if (providers.any { provider -> animeProviders.any { marker -> marker in provider } }) return true

        val evidence = listOfNotNull(row.description, row.descriptionOriginal)
            .joinToString(" ").lowercase(Locale.ROOT)
        return adaptationMarkers.any(evidence::contains) ||
            (("live-action" in evidence || "live action" in evidence) &&
                ("anime" in evidence || "manga" in evidence))
    }
}
