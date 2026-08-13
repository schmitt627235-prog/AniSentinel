package de.anisentinel.app.data.provider

import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.JustWatchCatalogTitleEntity
import de.anisentinel.app.data.local.JustWatchGenreEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.domain.provider.JustWatchCatalogResult
import de.anisentinel.app.domain.provider.JustWatchCatalogSource
import de.anisentinel.app.domain.provider.JustWatchCatalogTitle
import java.time.Instant
import java.text.Normalizer

class JustWatchCatalogRepository(
    private val dao: AniSentinelDao,
    private val source: JustWatchCatalogSource
) {
    fun observeGenres() = dao.observeJustWatchGenres()
    fun observeKnownAnimeTitles() = dao.observeKnownAnimeJustWatchCatalogTitles()
    fun observeAllCachedTitles() = dao.observeJustWatchCatalogTitles()

    suspend fun refreshGenres(): JustWatchCatalogResult {
        val result = source.genres()
        if (result is JustWatchCatalogResult.Success && result.genres.isNotEmpty()) {
            val now = Instant.now().epochSecond
            dao.upsertJustWatchGenres(result.genres.map {
                JustWatchGenreEntity(it.id, it.label, now, "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC")
            })
        }
        return result
    }

    suspend fun search(query: String?, genreIds: Set<String> = emptySet()): JustWatchCatalogResult {
        // Explicit global searches cover the full German catalog. Genre filters
        // are supplied only by Discover.
        val result = source.search(query, genreIds)
        if (result is JustWatchCatalogResult.Success && result.titles.isNotEmpty()) {
            persistSearchResults(result.titles)
        }
        return result
    }

    suspend fun backfillMetadata(animeId: String): JustWatchCatalogResult {
        val anime = dao.anime(animeId) ?: return JustWatchCatalogResult.Failed("ANIME_NOT_FOUND", false)
        val stableId = dao.justWatchMatches(animeId)
            .firstOrNull { it.status == "MATCHED" && !it.justWatchId.isNullOrBlank() }
            ?.justWatchId
            ?: dao.justWatchCatalogTitleForAnime(animeId)?.justWatchId
        val result = if (stableId != null) {
            source.title(stableId)
        } else {
            source.search(query = anime.titleGerman, first = 10)
        }
        if (result !is JustWatchCatalogResult.Success) return result
        val selected = if (stableId != null) {
            result.titles.singleOrNull { it.justWatchId == stableId }
        } else {
            JustWatchMetadataMatchPolicy.uniqueCandidate(
                anime.titleGerman, anime.seasonYear, "SHOW", result.titles
            )
        } ?: return JustWatchCatalogResult.Failed("METADATA_MATCH_NOT_UNIQUE", false)
        persistSearchResults(listOf(selected), forcedAnimeId = animeId)
        selected.description?.takeIf(String::isNotBlank)?.let {
            dao.updateAnimeDescription(animeId, it, Instant.now().epochSecond)
        }
        return JustWatchCatalogResult.Success(titles = listOf(selected))
    }

    private suspend fun persistSearchResults(titles: List<JustWatchCatalogTitle>, forcedAnimeId: String? = null) {
        val now = Instant.now().epochSecond
        val existing = dao.allAnime().associateBy { normalize(it.titleGerman.ifBlank { it.titleEnglish.orEmpty() }) }
        val anime = titles.map { title ->
            existing[normalize(title.title)] ?: AnimeEntity(
                id = "justwatch:${title.justWatchId}", anilistId = null, anisearchId = null,
                titleGerman = title.title, titleEnglish = null, titleRomaji = null, titleNative = null,
                description = "", coverUrl = title.coverUrl, bannerUrl = null, season = null,
                seasonYear = title.releaseYear, totalEpisodes = null, updatedAt = now,
                nextAiringAt = null, nextEpisode = null, sourceUpdatedAt = title.fetchedAt.epochSecond,
                cachedAt = now
            )
        }.distinctBy { it.id }
        val idByTitle = anime.associateBy { normalize(it.titleGerman) }
        dao.upsertAnime(anime)
        dao.upsertJustWatchCatalogTitles(titles.map { title ->
            title.toEntity(forcedAnimeId ?: idByTitle[normalize(title.title)]?.id)
        })
        titles.forEach { title ->
            val animeId = forcedAnimeId ?: idByTitle[normalize(title.title)]?.id ?: return@forEach
            title.providerUrls.forEach { (provider, url) ->
                dao.upsertProviderReference(ProviderReferenceEntity(
                    animeId, provider, url, "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC",
                    title.justWatchUrl, title.fetchedAt.epochSecond,
                    de.anisentinel.app.domain.provider.ProviderMarketPolicy.GERMANY
                ))
            }
        }
    }

    private fun JustWatchCatalogTitle.toEntity(internalAnimeId: String?) = JustWatchCatalogTitleEntity(
        justWatchId, internalAnimeId, title, releaseYear, contentType,
        genres.sorted().joinToString(","), coverUrl, justWatchUrl,
        providers.sorted().joinToString(","),
        providerUrls.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" },
        germanSubAvailable, germanDubAvailable, fetchedAt.epochSecond,
        "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", popularityRank,
        description, studios.sorted().joinToString("\n")
    )

    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")
}

object JustWatchMetadataMatchPolicy {
    fun uniqueCandidate(
        title: String,
        year: Int?,
        contentType: String,
        candidates: List<JustWatchCatalogTitle>
    ): JustWatchCatalogTitle? {
        val normalized = normalizeValue(title)
        val matches = candidates.filter { candidate ->
            normalizeValue(candidate.title) == normalized &&
                candidate.contentType.equals(contentType, ignoreCase = true) &&
                (year == null || candidate.releaseYear == null || candidate.releaseYear == year)
        }.distinctBy { it.justWatchId }
        return matches.singleOrNull()
    }

    private fun normalizeValue(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")
}
