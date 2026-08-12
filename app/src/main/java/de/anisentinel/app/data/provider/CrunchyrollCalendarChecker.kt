package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.model.StreamingProvider
import de.anisentinel.app.domain.provider.ProviderAvailabilityResult
import de.anisentinel.app.domain.provider.ProviderCheckRequest
import de.anisentinel.app.domain.provider.ProviderChecker
import de.anisentinel.app.domain.provider.EvidenceType
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CrunchyrollCalendarChecker(
    private val clock: Clock = Clock.systemUTC(),
    private val loader: suspend (String) -> String = ::loadPage
) : ProviderChecker {
    override val provider = StreamingProvider.CRUNCHYROLL

    override suspend fun checkAvailability(request: ProviderCheckRequest): ProviderAvailabilityResult {
        val checkedAt = clock.instant()
        val url = request.episodeUrl ?: request.seriesUrl
            ?: "https://www.crunchyroll.com/de/simulcastcalendar?date=${request.expectedDate}&filter=premium"
        val html = try { loader(url) } catch (_: Exception) {
            return ProviderAvailabilityResult.NetworkError(provider, checkedAt)
        }
        val evidence = when (url) {
            request.episodeUrl -> EvidenceType.EPISODE_PAGE
            request.seriesUrl -> EvidenceType.SERIES_PAGE
            else -> EvidenceType.RELEASE_CALENDAR
        }
        return CrunchyrollCalendarParser.parse(html, request, url, evidence, checkedAt)
    }

    companion object {
        private suspend fun loadPage(url: String): String = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "AniSentinel/0.10.0 availability-checker")
                when (connection.responseCode) {
                    401, 403 -> throw SecurityException("access_denied")
                    in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
                    else -> error("http_${connection.responseCode}")
                }
            } finally { connection.disconnect() }
        }
    }
}

object CrunchyrollCalendarParser {
    fun parse(
        html: String,
        request: ProviderCheckRequest,
        pageUrl: String,
        evidenceType: EvidenceType,
        checkedAt: Instant
    ): ProviderAvailabilityResult {
        val provider = StreamingProvider.CRUNCHYROLL
        val text = html.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")
        val normalized = normalize(text)
        if (normalized.contains("anmelden") && !normalized.contains("verfugbar")) {
            return ProviderAvailabilityResult.LoginRequired(provider, checkedAt)
        }
        if (!normalized.contains(normalize(request.title))) {
            return ProviderAvailabilityResult.TitleNotFound(provider, checkedAt)
        }
        val episode = request.expectedEpisode
        val episodeFound = episode == null || Regex("folge(?:n)?\\s+(?:\\d+[-–])?$episode(?:\\D|$)")
            .containsMatchIn(normalized)
        return if (episodeFound && normalized.contains("verfugbar")) {
            ProviderAvailabilityResult.Available(provider, episode, request.title, pageUrl, evidenceType, checkedAt)
        } else {
            ProviderAvailabilityResult.TitleFoundEpisodeMissing(provider, episode, pageUrl, checkedAt)
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
