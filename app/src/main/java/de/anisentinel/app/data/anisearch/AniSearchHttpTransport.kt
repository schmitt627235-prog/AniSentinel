package de.anisentinel.app.data.anisearch

import android.content.Context
import android.util.Log
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
            Log.i(LOG_TAG, "cache-hit url=$sourceUrl")
            return AniSearchFetchResult.Success(cache.readText(), sourceUrl, fromCache = true)
        }
        val nowSeconds = clock.instant().epochSecond
        val nextAllowed = cooldownStore?.nextAllowedAt("anisearch") ?: 0L
        val effectiveNextAllowed = maxOf(nextAllowed, globalNextAllowedAtSeconds)
        if (nowSeconds < effectiveNextAllowed) {
            Log.i(LOG_TAG, "cooldown-hit remaining=${effectiveNextAllowed - nowSeconds}s url=$sourceUrl")
            return AniSearchFetchResult.RateLimited(effectiveNextAllowed - nowSeconds)
        }
        repeat(2) { attempt ->
            val response = requestMutex.withLock {
                val cooldownRemaining = globalNextAllowedAtSeconds - clock.instant().epochSecond
                if (cooldownRemaining > 0) {
                    Log.i(LOG_TAG, "cooldown-hit-after-lock remaining=${cooldownRemaining}s url=$sourceUrl")
                    return AniSearchFetchResult.RateLimited(cooldownRemaining)
                }
                val waitMillis = (lastRequestAtMillis + minimumRequestIntervalMillis - clock.millis())
                    .coerceAtLeast(0)
                if (waitMillis > 0) delay(waitMillis)
                lastRequestAtMillis = clock.millis()
                Log.i(LOG_TAG, "request-start url=$sourceUrl")
                runCatching { loader(sourceUrl) }.onFailure {
                    Log.w(LOG_TAG, "request-failed type=${it.javaClass.simpleName} url=$sourceUrl")
                }.getOrNull()
            } ?: return AniSearchFetchResult.TemporarilyUnavailable(null)
            when (response.code) {
                in 200..299 -> {
                    if (response.body.isBlank()) return AniSearchFetchResult.TemporarilyUnavailable(response.code)
                    cache.writeText(response.body)
                    globalRateLimitCount = 0
                    Log.i(LOG_TAG, "success code=${response.code} bytes=${response.body.length} url=$sourceUrl")
                    return AniSearchFetchResult.Success(response.body, sourceUrl, fromCache = false)
                }
                401, 403 -> {
                    Log.w(LOG_TAG, "access-blocked code=${response.code} url=$sourceUrl")
                    return AniSearchFetchResult.AccessBlocked(response.code)
                }
                404 -> return AniSearchFetchResult.NotFound
                // Interactive requests must never freeze the UI while waiting out a rate limit.
                // A later explicit user action may retry; the cached result remains available.
                429 -> {
                    Log.w(LOG_TAG, "rate-limited retryAfter=${response.retryAfterSeconds} url=$sourceUrl")
                    globalRateLimitCount = (globalRateLimitCount + 1).coerceAtMost(6)
                    val exponentialBackoff = (30L * 60L * (1L shl (globalRateLimitCount - 1))).coerceAtMost(24L * 60L * 60L)
                    val cooldownUntil = clock.instant().epochSecond + (response.retryAfterSeconds ?: exponentialBackoff)
                    globalNextAllowedAtSeconds = maxOf(globalNextAllowedAtSeconds, cooldownUntil)
                    cooldownStore?.setNextAllowedAt("anisearch", cooldownUntil)
                    return AniSearchFetchResult.RateLimited(response.retryAfterSeconds ?: exponentialBackoff)
                }
                in 500..599 -> if (attempt == 0) delay(5_000)
                    else {
                        Log.w(LOG_TAG, "unavailable code=${response.code} url=$sourceUrl")
                        return AniSearchFetchResult.TemporarilyUnavailable(response.code)
                    }
                else -> {
                    Log.w(LOG_TAG, "unexpected code=${response.code} url=$sourceUrl")
                    return AniSearchFetchResult.TemporarilyUnavailable(response.code)
                }
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
        private const val LOG_TAG = "AniSearchTransport"
        private val DETAIL_PATH = Regex("/anime/\\d+(?:[,/].*)?")
        private val requestMutex = Mutex()
        private var lastRequestAtMillis = 0L
        @Volatile private var globalNextAllowedAtSeconds = 0L
        @Volatile private var globalRateLimitCount = 0

        internal fun resetGlobalCooldownForTests() {
            globalNextAllowedAtSeconds = 0L
            globalRateLimitCount = 0
            lastRequestAtMillis = 0L
        }

        private suspend fun load(url: String): AniSearchHttpResponse = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36 " +
                        "AniSentinel/0.25.16 (+https://github.com/schmitt627235-prog/AniSentinel)"
                )
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
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
