package de.anisentinel.app.data.anilist

import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.CatalogEntryEntity
import de.anisentinel.app.domain.model.Anime
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class CachedAniListRepository(
    private val dao: AniSentinelDao,
    private val client: AniListClient,
    private val clock: Clock = Clock.systemUTC()
) {
    private var retryAllowedAt: Long = 0

    fun observeAnime(): Flow<List<Anime>> = combine(
        dao.observeAnime(),
        dao.observeJustWatchProviderReferences()
    ) { entities, references ->
        val providers = references.groupBy { it.animeId }
        entities.map { entity ->
            entity.toDomain().copy(
                provider = de.anisentinel.app.domain.provider.StreamingProviderPolicy
                    .visible(providers[entity.id].orEmpty().map { it.provider }).joinToString(" · ")
            )
        }
    }

    fun observeActiveAniWorldAnime(fromEpochSeconds: Long): Flow<List<Anime>> = combine(
        dao.observeActiveAniWorldAnime(fromEpochSeconds),
        dao.observeJustWatchProviderReferences()
    ) { entities, references ->
        val providers = references.groupBy { it.animeId }
        entities.map { entity ->
            entity.toDomain().copy(
                provider = de.anisentinel.app.domain.provider.StreamingProviderPolicy
                    .visible(providers[entity.id].orEmpty().map { it.provider }).joinToString(" · ")
            )
        }
    }

    suspend fun refresh(force: Boolean = true): CatalogRefreshResult = withContext(Dispatchers.IO) {
        val now = clock.instant().epochSecond
        if (now < retryAllowedAt) {
            return@withContext CatalogRefreshResult.RateLimited(retryAllowedAt - now)
        }
        val lastFetch = dao.latestCatalogFetch(CATALOG_TRENDING)
        val hasExpiredRelease =
            dao.expiredCatalogReleaseCount(CATALOG_TRENDING, now) > 0
        if (!force && !hasExpiredRelease && lastFetch != null && now - lastFetch < CACHE_TTL_SECONDS) {
            return@withContext CatalogRefreshResult.CacheFresh
        }
        when (val result = client.trendingAnime()) {
            is AniListResult.Success -> {
                val fetchedAt = clock.instant().epochSecond
                val entities = result.media.map { it.toEntity(fetchedAt) }
                dao.replaceCatalog(
                    catalogType = CATALOG_TRENDING,
                    anime = entities,
                    entries = entities.mapIndexed { index, entity ->
                        CatalogEntryEntity(
                            catalogType = CATALOG_TRENDING,
                            animeId = entity.id,
                            position = index,
                            fetchedAt = fetchedAt
                        )
                    }
                )
                CatalogRefreshResult.Success(result.media.size)
            }
            is AniListResult.HttpError -> {
                if (result.code == 429) {
                    retryAllowedAt = now + (result.retryAfterSeconds ?: DEFAULT_RETRY_SECONDS)
                    CatalogRefreshResult.RateLimited(retryAllowedAt - now)
                } else {
                    CatalogRefreshResult.Error(result.failure.userMessage())
                }
            }
            is AniListResult.InvalidResponse ->
                CatalogRefreshResult.Error("Ungültige AniList-Antwort: ${result.reason}")
            is AniListResult.NetworkError ->
                CatalogRefreshResult.Error("Netzwerk nicht erreichbar: ${result.reason}")
        }
    }

    companion object {
        const val CATALOG_TRENDING = "ANILIST_TRENDING"
        const val CACHE_TTL_SECONDS = 30 * 60L
        const val DEFAULT_RETRY_SECONDS = 60L
    }
}

private fun AniListFailure.userMessage(): String = when (this) {
    is AniListFailure.ServiceUnavailable -> "AniList API vorübergehend deaktiviert: $diagnostic"
    is AniListFailure.IpBlocked -> "AniList-Netzwerkzugriff abgewiesen: $diagnostic"
    is AniListFailure.RateLimited -> "AniList-Anfragelimit erreicht; Retry-After=${retryAfterSeconds ?: "unbekannt"}"
    is AniListFailure.InvalidQuery -> "ANILIST_QUERY_INVALID: $diagnostic"
    is AniListFailure.AccessDenied -> "AniList-Zugriff verweigert: $diagnostic"
    is AniListFailure.Network -> "AniList-Netzwerkfehler $type: $diagnostic"
    is AniListFailure.UnknownHttpFailure -> "AniList HTTP $statusCode: $diagnostic"
}

sealed interface CatalogRefreshResult {
    data class Success(val count: Int) : CatalogRefreshResult
    data object CacheFresh : CatalogRefreshResult
    data class RateLimited(val retryAfterSeconds: Long) : CatalogRefreshResult
    data class Error(val message: String) : CatalogRefreshResult
}
