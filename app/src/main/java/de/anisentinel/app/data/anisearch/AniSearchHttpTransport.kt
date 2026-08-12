package de.anisentinel.app.data.anisearch

import android.content.Context
import de.anisentinel.app.data.settings.SourceCooldownStore
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface AniSearchFetchResult {
    data class Success(val html: String, val sourceUrl: String, val fromCache: Boolean) : AniSearchFetchResult
    data class Disabled(val reason: String = "ANISEARCH_DISABLED") : AniSearchFetchResult
    data class AccessBlocked(val httpCode: Int) : AniSearchFetchResult
    data object NotFound : AniSearchFetchResult
    data class RateLimited(val retryAfterSeconds: Long?) : AniSearchFetchResult
    data class TemporarilyUnavailable(val httpCode: Int?) : AniSearchFetchResult
    data class InvalidUrl(val reason: String) : AniSearchFetchResult
}

data class AniSearchHttpResponse(
    val code: Int,
    val body: String,
    val retryAfterSeconds: Long? = null
)

class AniSearchHttpTransport(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val enabled: () -> Boolean = { true },
    private val loader: suspend (String) -> AniSearchHttpResponse = ::load,
    private val cacheTtlSeconds: Long = 30 * 60,
    private val minimumRequestIntervalMillis: Long = 4_000,
    private val cooldownStore: SourceCooldownStore? = null
) {
    private val cacheDirectory = File(context.cacheDir, "anisearch-html").apply { mkdirs() }

    suspend fun fetchDetail(sourceUrl: String): AniSearchFetchResult {
        if (!enabled()) return AniSearchFetchResult.Disabled()
        validateDetailUrl(sourceUrl)?.let { return AniSearchFetchResult.InvalidUrl(it) }
        return fetch(sourceUrl)
    }

    suspend fun searchAnime(query: String): AniSearchFetchResult {
        if (!enabled()) return AniSearchFetchResult.Disabled()
        val normalized = query.trim()
        if (normalized.length < 2) return AniSearchFetchResult.InvalidUrl("SEARCH_QUERY_TOO_SHORT")
        val encoded = URLEncoder.encode(normalized, Charsets.UTF_8.name())
        return fetch("https://www.anisearch.de/anime/index/?char=all&page=1&text=$encoded&smode=2&sort=title&order=asc&view=2&title=de,en")
    }

    private suspend fun fetch(sourceUrl: String): AniSearchFetchResult {
        val cache = File(cacheDirectory, sourceUrl.sha256() + ".html")
        if (cache.isFile && clock.millis() - cache.lastModified() <= cacheTtlSeconds * 1_000) {
            return AniSearchFetchResult.Success(cache.readText(), sourceUrl, fromCache = true)
        }
        val nowSeconds = clock.instant().epochSecond
        val nextAllowed = cooldownStore?.nextAllowedAt("anisearch") ?: 0L
        if (nowSeconds < nextAllowed) return AniSearchFetchResult.RateLimited(nextAllowed - nowSeconds)
        repeat(2) { attempt ->
            val response = requestMutex.withLock {
                val waitMillis = (lastRequestAtMillis + minimumRequestIntervalMillis - clock.millis())
                    .coerceAtLeast(0)
                if (waitMillis > 0) delay(waitMillis)
                lastRequestAtMillis = clock.millis()
                runCatching { loader(sourceUrl) }.getOrNull()
            } ?: return AniSearchFetchResult.TemporarilyUnavailable(null)
            when (response.code) {
                in 200..299 -> {
                    if (response.body.isBlank()) return AniSearchFetchResult.TemporarilyUnavailable(response.code)
                    cache.writeText(response.body)
                    return AniSearchFetchResult.Success(response.body, sourceUrl, fromCache = false)
                }
                401, 403 -> return AniSearchFetchResult.AccessBlocked(response.code)
                404 -> return AniSearchFetchResult.NotFound
                // Interactive requests must never freeze the UI while waiting out a rate limit.
                // A later explicit user action may retry; the cached result remains available.
                429 -> {
                    cooldownStore?.setNextAllowedAt(
                        "anisearch",
                        clock.instant().epochSecond + (response.retryAfterSeconds ?: 30 * 60)
                    )
                    return AniSearchFetchResult.RateLimited(response.retryAfterSeconds)
                }
                in 500..599 -> if (attempt == 0) delay(5_000)
                    else return AniSearchFetchResult.TemporarilyUnavailable(response.code)
                else -> return AniSearchFetchResult.TemporarilyUnavailable(response.code)
            }
        }
        return AniSearchFetchResult.TemporarilyUnavailable(null)
    }

    private fun validateDetailUrl(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return "INVALID_URL"
        if (uri.scheme != "https" || uri.host?.lowercase() !in HOSTS) return "NOT_ANISEARCH_HTTPS"
        if (!DETAIL_PATH.matches(uri.path.orEmpty())) return "NOT_AN_ANIME_DETAIL_URL"
        return null
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val HOSTS = setOf("anisearch.de", "www.anisearch.de")
        private val DETAIL_PATH = Regex("/anime/\\d+(?:[,/].*)?")
        private val requestMutex = Mutex()
        private var lastRequestAtMillis = 0L

        private suspend fun load(url: String): AniSearchHttpResponse = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "AniSentinel/0.10.0 (Android; public AniSearch metadata; no login)"
                )
                val code = connection.responseCode
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                AniSearchHttpResponse(
                    code = code,
                    body = body,
                    retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
