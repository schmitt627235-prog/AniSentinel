package de.anisentinel.app.data.anisearch

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import de.anisentinel.app.data.settings.SourceCooldownStore
import java.io.File
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
    data class RateLimited(
        val retryAfterSeconds: Long?,
        val origin: AniSearchRateLimitOrigin,
        val nextAllowedAt: Long
    ) : AniSearchFetchResult
    data class TemporarilyUnavailable(val httpCode: Int?) : AniSearchFetchResult
    data class InvalidUrl(val reason: String) : AniSearchFetchResult
}

enum class AniSearchRateLimitOrigin { LOCAL_COOLDOWN, REMOTE_HTTP_429 }

data class AniSearchHttpResponse(val code: Int, val body: String, val retryAfterSeconds: Long? = null)

/** One reusable browser-like HTTP session. Cookies survive search/detail redirects in this instance. */
fun interface AniSearchWebSession {
    suspend fun request(url: String): AniSearchHttpResponse
}

internal class PersistentAniSearchWebSession(
    private val clock: Clock = Clock.systemUTC(),
    appVersion: String = "unknown"
) : AniSearchWebSession {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
    private val requestHeaders = requestHeaders(appVersion)

    override suspend fun request(url: String): AniSearchHttpResponse = withContext(Dispatchers.IO) {
        var current = URI(url)
        repeat(MAX_REDIRECTS + 1) {
            val connection = current.toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = false
                requestHeaders.forEach(connection::setRequestProperty)
                requestCookieHeaders(current).forEach { (name, values) ->
                    if (name != null && values.isNotEmpty()) connection.setRequestProperty(name, values.joinToString("; "))
                }
                val code = connection.responseCode
                absorbCookies(current, connection.headerFields)
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: return@withContext AniSearchHttpResponse(code, "")
                    val redirect = current.resolve(location)
                    if (isRobotsDisallowed(redirect)) {
                        Log.w("AniSearchTransport", "redirect-blocked robots-path=${redirect.path} url=$redirect")
                        return@withContext AniSearchHttpResponse(403, "")
                    }
                    current = redirect
                    return@repeat
                }
                val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
                return@withContext AniSearchHttpResponse(code, body, parseRetryAfter(connection.getHeaderField("Retry-After"), clock))
            } finally {
                connection.disconnect()
            }
        }
        AniSearchHttpResponse(599, "")
    }

    internal fun absorbCookies(uri: URI, headers: Map<String?, List<String>>) {
        cookieManager.put(uri, headers)
    }

    internal fun requestCookieHeaders(uri: URI): Map<String, List<String>> = cookieManager.get(uri, emptyMap())

    companion object {
        internal fun requestHeaders(appVersion: String): Map<String, String> = linkedMapOf(
            "User-Agent" to "AniSentinel/$appVersion (+https://github.com/schmitt627235-prog/AniSentinel)",
            "Accept" to "text/html",
            "Accept-Language" to "de-DE,de;q=0.9,en;q=0.7"
        )

        internal fun parseRetryAfter(value: String?, clock: Clock): Long? {
            value?.trim()?.toLongOrNull()?.let { return it.coerceAtLeast(0) }
            val at = runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull() ?: return null
            return (at.epochSecond - clock.instant().epochSecond).coerceAtLeast(0)
        }

        internal fun isRobotsDisallowed(uri: URI): Boolean = DISALLOWED_PATH_PREFIXES.any { prefix ->
            val path = uri.path.orEmpty()
            path == prefix.dropLast(1) || path.startsWith(prefix)
        }

        private const val MAX_REDIRECTS = 5
        private val DISALLOWED_PATH_PREFIXES = listOf("/r/", "/rr/", "/redirect/", "/usercp/", "/images/usbndeo/")
    }
}

class AniSearchHttpTransport(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val enabled: () -> Boolean = { true },
    private val session: AniSearchWebSession = PersistentAniSearchWebSession(
        clock,
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty().ifBlank { "unknown" }
    ),
    private val cacheTtlSeconds: Long = 30 * 60,
    private val minimumRequestIntervalMillis: Long = 6_000,
    private val cooldownStore: SourceCooldownStore? = null,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val warmUpEnabled: Boolean = true,
    private val debugBuild: () -> Boolean = {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
) {
    private val cacheDirectory = File(context.cacheDir, "anisearch-html").apply { mkdirs() }
    @Volatile private var sessionInitialized = false

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
            return AniSearchFetchResult.Success(cache.readText(), sourceUrl, true)
        }
        localCooldown(sourceUrl)?.let { return it }
        repeat(2) { attempt ->
            val response = requestMutex.withLock {
                localCooldown(sourceUrl)?.let { return it }
                if (warmUpEnabled && !sessionInitialized) {
                    val warmUp = requestNetworkLocked(INDEX_URL, "session-warmup")
                    if (warmUp.code !in 200..299 || warmUp.body.isBlank()) return@withLock warmUp
                    sessionInitialized = true
                }
                requestNetworkLocked(sourceUrl, "content")
            }
            when (response.code) {
                in 200..299 -> {
                    if (response.body.isBlank()) return AniSearchFetchResult.TemporarilyUnavailable(response.code)
                    cache.writeText(response.body)
                    globalRateLimitCount = 0
                    return AniSearchFetchResult.Success(response.body, sourceUrl, false)
                }
                401, 403 -> return AniSearchFetchResult.AccessBlocked(response.code)
                404 -> return AniSearchFetchResult.NotFound
                429 -> return applyRateLimit(response.retryAfterSeconds, sourceUrl)
                in 500..599 -> if (attempt == 0) sleeper(5_000) else return AniSearchFetchResult.TemporarilyUnavailable(response.code)
                else -> return AniSearchFetchResult.TemporarilyUnavailable(response.code)
            }
        }
        return AniSearchFetchResult.TemporarilyUnavailable(null)
    }

    private suspend fun requestNetworkLocked(url: String, kind: String): AniSearchHttpResponse {
        val waitMillis = (lastRequestAtMillis + minimumRequestIntervalMillis - clock.millis()).coerceAtLeast(0)
        if (waitMillis > 0) sleeper(waitMillis)
        // Record the logical completion of the wait. This also keeps deterministic
        // tests correct when their injected sleeper does not advance the Clock.
        lastRequestAtMillis = clock.millis()
        Log.i(LOG_TAG, "request-start kind=$kind url=$url")
        return runCatching { session.request(url) }.onFailure {
            Log.w(LOG_TAG, "request-failed type=${it.javaClass.simpleName} url=$url")
        }.getOrNull() ?: AniSearchHttpResponse(599, "")
    }

    private suspend fun localCooldown(url: String): AniSearchFetchResult.RateLimited? {
        val now = clock.instant().epochSecond
        val until = maxOf(cooldownStore?.nextAllowedAt("anisearch") ?: 0L, globalNextAllowedAtSeconds)
        val remaining = (until - now).takeIf { it > 0 } ?: return null
        Log.i(LOG_TAG, "rate-limit origin=LOCAL_COOLDOWN NO_HTTP_REQUEST remainingSeconds=$remaining nextAllowedAt=$until url=$url")
        return AniSearchFetchResult.RateLimited(remaining, AniSearchRateLimitOrigin.LOCAL_COOLDOWN, until)
    }

    private suspend fun applyRateLimit(retryAfterSeconds: Long?, url: String): AniSearchFetchResult.RateLimited {
        globalRateLimitCount = (globalRateLimitCount + 1).coerceAtMost(6)
        val fallback = (30L * 60L * (1L shl (globalRateLimitCount - 1))).coerceAtMost(24L * 60L * 60L)
        val seconds = retryAfterSeconds ?: fallback
        val until = clock.instant().epochSecond + seconds
        globalNextAllowedAtSeconds = maxOf(globalNextAllowedAtSeconds, until)
        cooldownStore?.setNextAllowedAt("anisearch", until)
        Log.w(LOG_TAG, "rate-limit origin=REMOTE_HTTP_429 httpCode=429 retryAfterHeaderSeconds=$retryAfterSeconds cooldownSeconds=$seconds nextAllowedAt=$until url=$url")
        return AniSearchFetchResult.RateLimited(seconds, AniSearchRateLimitOrigin.REMOTE_HTTP_429, until)
    }

    /** Manual test hook. It issues no request and is disabled in release builds. */
    suspend fun clearAniSearchCooldownForDebug(): Boolean {
        if (!debugBuild()) return false
        cooldownStore?.clear("anisearch")
        globalNextAllowedAtSeconds = 0L
        globalRateLimitCount = 0
        Log.i(LOG_TAG, "debug-cooldown-reset source=anisearch NO_HTTP_REQUEST")
        return true
    }

    private fun validateDetailUrl(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return "INVALID_URL"
        if (uri.scheme != "https" || uri.host?.lowercase() !in HOSTS) return "NOT_ANISEARCH_HTTPS"
        if (!DETAIL_PATH.matches(uri.path.orEmpty())) return "NOT_AN_ANIME_DETAIL_URL"
        return null
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256").digest(toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val HOSTS = setOf("anisearch.de", "www.anisearch.de")
        private const val LOG_TAG = "AniSearchTransport"
        private const val INDEX_URL = "https://www.anisearch.de/anime/index"
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
    }
}
