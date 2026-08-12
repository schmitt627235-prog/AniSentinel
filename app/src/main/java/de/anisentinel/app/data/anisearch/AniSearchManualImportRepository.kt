package de.anisentinel.app.data.anisearch

import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.ProviderReferenceEntity
import java.time.Clock

class AniSearchManualImportRepository(
    private val dao: AniSentinelDao,
    private val clock: Clock = Clock.systemUTC(),
    private val transport: AniSearchHttpTransport? = null
) {
    suspend fun search(query: String): AniSearchSearchResult {
        val fetcher = transport ?: return AniSearchSearchResult.Failed(AniSearchFetchResult.Disabled("NO_TRANSPORT"))
        return when (val fetched = fetcher.searchAnime(query)) {
            is AniSearchFetchResult.Success -> AniSearchSearchResult.Success(
                AniSearchHtmlParser.parseSearchResults(fetched.html, fetched.sourceUrl), fetched.fromCache
            )
            else -> AniSearchSearchResult.Failed(fetched)
        }
    }

    suspend fun importUrl(sourceUrl: String): AniSearchFetchImportResult {
        val fetcher = transport ?: return AniSearchFetchImportResult.FetchFailed(
            AniSearchFetchResult.Disabled("NO_TRANSPORT")
        )
        return when (val fetched = fetcher.fetchDetail(sourceUrl)) {
            is AniSearchFetchResult.Success -> when (val parsed = importHtml(sourceUrl, fetched.html)) {
                is AniSearchParseResult.Success -> AniSearchFetchImportResult.Imported(
                    parsed.value, fetched.fromCache, calendarReleaseCount = 0,
                    providerCount = parsed.value.providers.size,
                    metadataImported = true,
                    warnings = listOf("NO_VERIFIED_EPISODE_RELEASE_ON_DETAIL_PAGE"),
                    sourceUrl = parsed.value.sourceUrl
                )
                else -> AniSearchFetchImportResult.ParseFailed(parsed)
            }
            else -> AniSearchFetchImportResult.FetchFailed(fetched)
        }
    }

    suspend fun importHtml(sourceUrl: String, html: String): AniSearchParseResult {
        val result = AniSearchHtmlParser.parse(html, sourceUrl)
        if (result !is AniSearchParseResult.Success) return result
        val parsed = result.value
        val now = clock.instant().epochSecond
        val id = "anisearch:${parsed.anisearchId}"
        val existing = dao.anime(id)
        dao.upsertAnime(listOf(
            AnimeEntity(
                id = id,
                anilistId = existing?.anilistId,
                anisearchId = parsed.anisearchId,
                titleGerman = parsed.titleGerman,
                titleEnglish = existing?.titleEnglish,
                titleRomaji = existing?.titleRomaji,
                titleNative = existing?.titleNative,
                description = parsed.descriptionGerman,
                coverUrl = parsed.coverUrl ?: existing?.coverUrl,
                bannerUrl = existing?.bannerUrl,
                season = existing?.season,
                seasonYear = parsed.releaseYear ?: existing?.seasonYear,
                totalEpisodes = parsed.totalEpisodes ?: existing?.totalEpisodes,
                updatedAt = now,
                nextAiringAt = existing?.nextAiringAt,
                nextEpisode = existing?.nextEpisode,
                sourceUpdatedAt = now,
                cachedAt = now
            )
        ))
        parsed.providers.forEach { provider ->
            dao.upsertProviderReference(
                ProviderReferenceEntity(
                    animeId = id,
                    provider = provider.normalizedProvider,
                    seriesUrl = provider.providerUrl,
                    source = "ANISEARCH_MANUAL_HTML",
                    sourceUrl = parsed.sourceUrl,
                    lastConfirmedAt = now
                )
            )
        }
        return result
    }
}

sealed interface AniSearchFetchImportResult {
    data class Imported(
        val anime: AniSearchImport,
        val fromCache: Boolean,
        val calendarReleaseCount: Int,
        val providerCount: Int,
        val metadataImported: Boolean,
        val warnings: List<String>,
        val sourceUrl: String
    ) : AniSearchFetchImportResult
    data class FetchFailed(val result: AniSearchFetchResult) : AniSearchFetchImportResult
    data class ParseFailed(val result: AniSearchParseResult) : AniSearchFetchImportResult
}

sealed interface AniSearchSearchResult {
    data class Success(val hits: List<AniSearchSearchHit>, val fromCache: Boolean) : AniSearchSearchResult
    data class Failed(val result: AniSearchFetchResult) : AniSearchSearchResult
}
