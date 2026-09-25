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

    suspend fun enrichUpcoming(
        animeId: String,
        aliases: Iterable<String>,
        year: Int?,
        format: String?,
        seasonNumber: Int?
    ): UpcomingJustWatchEnrichment? {
        val normalizedAliases = aliases.map(String::trim).filter(String::isNotBlank)
            .distinctBy(JustWatchUpcomingMatchPolicy::normalize)
        val cachedEntity = dao.justWatchCatalogTitleForAnime(animeId)
        val cached = cachedEntity?.takeIf {
            JustWatchUpcomingMatchPolicy.isSafeCandidate(
                normalizedAliases, year, contentType = if (format.equals("MOVIE", true)) "MOVIE" else "SHOW",
                seasonNumber, it.title, it.releaseYear, it.contentType
            )
        }?.let {
            UpcomingJustWatchEnrichment(it.justWatchId, it.title, it.providers.split(',').filter(String::isNotBlank).toSet(), it.justWatchUrl)
        }
        if (cached != null && Instant.now().epochSecond - (cachedEntity?.fetchedAt ?: 0) < 86_400) return cached
        val contentType = if (format.equals("MOVIE", true)) "MOVIE" else "SHOW"
        for (alias in normalizedAliases.take(6)) {
            val result = source.search(query = alias, contentTypes = setOf(contentType), first = 10)
            if (result !is JustWatchCatalogResult.Success) continue
            val selected = JustWatchUpcomingMatchPolicy.uniqueCandidate(
                aliases = normalizedAliases, query = alias, year = year,
                contentType = contentType, seasonNumber = seasonNumber, candidates = result.titles
            ) ?: continue
            persistSearchResults(listOf(selected), forcedAnimeId = animeId)
            // JustWatch establishes provider availability and contributes aliases. It
            // must not replace the canonical work title (a shorter parent-series match
            // would otherwise turn a spin-off into a different anime).
            return UpcomingJustWatchEnrichment(selected.justWatchId, selected.title, selected.providers, selected.justWatchUrl)
        }
        return cached
    }

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
        val anime = if (forcedAnimeId != null) emptyList() else titles.map { title ->
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
        if (anime.isNotEmpty()) dao.upsertAnime(anime)
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
        MetadataTextNormalizer.normalizeGenres(genres).joinToString(","), coverUrl, justWatchUrl,
        providers.sorted().joinToString(","),
        providerUrls.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" },
        germanSubAvailable, germanDubAvailable, fetchedAt.epochSecond,
        "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", popularityRank,
        MetadataTextNormalizer.decode(description), studios.mapNotNull(MetadataTextNormalizer::decode).sorted().joinToString("\n"),
        MetadataTextNormalizer.decode(descriptionOriginal), descriptionOriginalLanguage, descriptionGermanSource
    )

    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")
}

data class UpcomingJustWatchEnrichment(
    val justWatchId: String,
    val germanTitle: String,
    val providers: Set<String>,
    val justWatchUrl: String?
)

object JustWatchUpcomingMatchPolicy {
    fun uniqueCandidate(
        aliases: Iterable<String>, query: String, year: Int?, contentType: String,
        seasonNumber: Int?, candidates: List<JustWatchCatalogTitle>
    ): JustWatchCatalogTitle? {
        val aliasKeys = aliases.map(::normalize).filter(String::isNotBlank).toSet()
        val queryKey = normalize(query)
        val compatible = candidates.filter { candidate ->
            candidate.contentType.equals(contentType, true) &&
                (year == null || candidate.releaseYear == null || kotlin.math.abs(year - candidate.releaseYear) <= 1) &&
                !installmentConflict(seasonNumber, candidate.title)
        }.distinctBy { it.justWatchId }
        val exact = compatible.filter { normalize(it.title) in aliasKeys }
        if (exact.size == 1) return exact.single()
        // A translated result cannot be verified by its title alone. Accept a singleton only
        // when both sides explicitly identify the same installment; this rejects unrelated
        // singleton search results such as Clevatess for Apothecary Diaries Season 3.
        return compatible.singleOrNull()?.takeIf {
            queryKey in aliasKeys && seasonNumber != null && installmentNumber(it.title) == seasonNumber
        }
    }

    fun isSafeCandidate(
        aliases: Iterable<String>, year: Int?, contentType: String, seasonNumber: Int?,
        candidateTitle: String, candidateYear: Int?, candidateContentType: String
    ): Boolean {
        if (!candidateContentType.equals(contentType, true)) return false
        if (year != null && candidateYear != null && kotlin.math.abs(year - candidateYear) > 1) return false
        if (normalize(candidateTitle) in aliases.map(::normalize).toSet()) return true
        return seasonNumber != null && installmentNumber(candidateTitle) == seasonNumber
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun installmentConflict(expected: Int?, candidate: String): Boolean {
        if (expected == null) return false
        val actual = installmentNumber(candidate)
        return actual != null && actual != expected
    }

    private fun installmentNumber(candidate: String): Int? = listOf(
            Regex("(?i)(?:season|staffel)\\s*(\\d+)"),
            Regex("(?i)(\\d+)(?:st|nd|rd|th)\\s+season"), Regex("第\\s*(\\d+)\\s*期")
        ).firstNotNullOfOrNull { it.find(candidate)?.groupValues?.getOrNull(1)?.toIntOrNull() }
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
