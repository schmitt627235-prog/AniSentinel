package de.anisentinel.app.data.anilist

import de.anisentinel.app.data.settings.SourceCooldownStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Clock
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class NetworkFailureType { TIMEOUT, TLS, DNS, IO }

sealed interface GraphQlHttpResult {
    data class Success(val body: String, val statusCode: Int, val headers: Map<String, List<String>>) : GraphQlHttpResult
    data class HttpFailure(
        val statusCode: Int,
        val body: String?,
        val retryAfterSeconds: Long?,
        val rateLimitRemaining: Int?,
        val rateLimitResetEpochSeconds: Long?,
        val requestId: String?
    ) : GraphQlHttpResult
    data class NetworkFailure(val type: NetworkFailureType, val message: String?) : GraphQlHttpResult
}

sealed interface AniListFailure {
    val diagnostic: String
    data class ServiceUnavailable(override val diagnostic: String) : AniListFailure
    data class IpBlocked(override val diagnostic: String) : AniListFailure
    data class RateLimited(val retryAfterSeconds: Long?, override val diagnostic: String) : AniListFailure
    data class InvalidQuery(override val diagnostic: String) : AniListFailure
    data class AccessDenied(override val diagnostic: String) : AniListFailure
    data class Network(val type: NetworkFailureType, override val diagnostic: String) : AniListFailure
    data class UnknownHttpFailure(val statusCode: Int, override val diagnostic: String) : AniListFailure
}

class AniListGraphQlHttpClient(
    private val endpoint: String = "https://graphql.anilist.co",
    private val clock: Clock = Clock.systemUTC(),
    private val transport: suspend (String, String) -> GraphQlHttpResult = ::executeConnection,
    private val cooldownStore: SourceCooldownStore? = null
) {
    private val mutex = Mutex()
    private var nextAllowedAtEpochSeconds = 0L

    suspend fun execute(requestKey: String, jsonBody: String): GraphQlHttpResult = mutex.withLock {
        val now = clock.instant().epochSecond
        nextAllowedAtEpochSeconds = maxOf(
            nextAllowedAtEpochSeconds,
            cooldownStore?.nextAllowedAt("anilist") ?: 0L
        )
        if (now < nextAllowedAtEpochSeconds) {
            return GraphQlHttpResult.HttpFailure(
                429, "Local AniList cooldown active", nextAllowedAtEpochSeconds - now,
                0, nextAllowedAtEpochSeconds, null
            )
        }
        val result = transport(endpoint, jsonBody)
        if (result is GraphQlHttpResult.HttpFailure) {
            val delay = result.retryAfterSeconds
                ?: result.rateLimitResetEpochSeconds?.minus(now)?.coerceAtLeast(0)
            if (result.statusCode == 429 || classify(result) is AniListFailure.ServiceUnavailable || classify(result) is AniListFailure.IpBlocked) {
                nextAllowedAtEpochSeconds = now + (delay ?: if (result.statusCode == 429) 60 else 30 * 60)
                cooldownStore?.setNextAllowedAt("anilist", nextAllowedAtEpochSeconds)
            }
        }
        result
    }

    suspend fun retryNotBeforeEpochSeconds(): Long = mutex.withLock {
        maxOf(nextAllowedAtEpochSeconds, cooldownStore?.nextAllowedAt("anilist") ?: 0L)
    }

    fun classify(result: GraphQlHttpResult): AniListFailure? = when (result) {
        is GraphQlHttpResult.Success -> null
        is GraphQlHttpResult.NetworkFailure -> AniListFailure.Network(result.type, result.message.orEmpty())
        is GraphQlHttpResult.HttpFailure -> {
            val message = parseErrors(result.body).ifBlank { result.body.orEmpty() }
            val normalized = message.lowercase()
            when {
                result.statusCode == 429 -> AniListFailure.RateLimited(result.retryAfterSeconds, message)
                result.statusCode == 403 && ("temporarily disabled" in normalized || "stability issues" in normalized) -> AniListFailure.ServiceUnavailable(message)
                result.statusCode == 403 && ("blocked" in normalized || "ip address" in normalized) -> AniListFailure.IpBlocked(message)
                result.statusCode == 400 -> AniListFailure.InvalidQuery(message)
                result.statusCode == 401 || result.statusCode == 403 -> AniListFailure.AccessDenied(message)
                else -> AniListFailure.UnknownHttpFailure(result.statusCode, message)
            }
        }
    }

    private fun parseErrors(body: String?): String = runCatching {
        val errors = JSONObject(body.orEmpty()).optJSONArray("errors") ?: return@runCatching ""
        buildList {
            for (index in 0 until errors.length()) add(errors.getJSONObject(index).optString("message"))
        }.filter(String::isNotBlank).joinToString(" | ")
    }.getOrDefault("")

    companion object {
        private suspend fun executeConnection(endpoint: String, jsonBody: String): GraphQlHttpResult = withContext(Dispatchers.IO) {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.doInput = true
                connection.doOutput = true
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("User-Agent", "AniSentinel/0.10.0 (Android; public metadata)")
                connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val success = status in 200..299
                val stream = if (success) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (success) GraphQlHttpResult.Success(body.orEmpty(), status, connection.headerFields.filterKeys { it != null })
                else GraphQlHttpResult.HttpFailure(
                    status, body, connection.getHeaderField("Retry-After")?.toLongOrNull(),
                    connection.getHeaderField("X-RateLimit-Remaining")?.toIntOrNull(),
                    connection.getHeaderField("X-RateLimit-Reset")?.toLongOrNull(),
                    connection.getHeaderField("CF-Ray") ?: connection.getHeaderField("X-Request-Id")
                )
            } catch (error: SocketTimeoutException) {
                GraphQlHttpResult.NetworkFailure(NetworkFailureType.TIMEOUT, error.message)
            } catch (error: SSLException) {
                GraphQlHttpResult.NetworkFailure(NetworkFailureType.TLS, error.message)
            } catch (error: UnknownHostException) {
                GraphQlHttpResult.NetworkFailure(NetworkFailureType.DNS, error.message)
            } catch (error: IOException) {
                GraphQlHttpResult.NetworkFailure(NetworkFailureType.IO, error.message)
            } finally { connection.disconnect() }
        }
    }
}
