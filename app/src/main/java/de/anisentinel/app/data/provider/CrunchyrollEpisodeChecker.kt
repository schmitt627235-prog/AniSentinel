package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.ProviderEpisodeAvailability
import de.anisentinel.app.domain.provider.ProviderEpisodeCheckResult
import de.anisentinel.app.domain.provider.ProviderEpisodeChecker
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Checks public Crunchyroll title/episode pages only. It never logs in, requests
 * playback manifests, DRM data or protected media endpoints.
 */
class CrunchyrollEpisodeChecker(
    private val clock: Clock = Clock.systemUTC(),
    private val loader: suspend (String) -> PublicProviderPage = ::loadPublicPage
) : ProviderEpisodeChecker {
    override suspend fun checkEpisode(
        providerId: String,
        title: String,
        seasonNumber: Int?,
        episodeNumber: Int,
        expectedLanguage: String?,
        providerUrl: String?,
        expectedAt: Instant?
    ): ProviderEpisodeCheckResult {
        val checkedAt = clock.instant()
        if (!providerId.contains("crunchyroll", ignoreCase = true) ||
            providerId.contains("amazon", ignoreCase = true) ||
            providerId.contains("channel", ignoreCase = true)
        ) return ProviderEpisodeCheckResult.Failed("PROVIDER_NOT_SUPPORTED", checkedAt, false)

        val startUrl = providerUrl?.takeIf { it.startsWith("https://") }
            ?: return ProviderEpisodeCheckResult.Failed("CRUNCHYROLL_PUBLIC_URL_MISSING", checkedAt, true)
        val page = try {
            loader(startUrl)
        } catch (error: ProviderAccessException) {
            return ProviderEpisodeCheckResult.Failed(error.code, checkedAt, error.retryable)
        } catch (_: Exception) {
            return ProviderEpisodeCheckResult.Failed("PROVIDER_CHECK_FAILED", checkedAt, true)
        }
        if (URI(page.finalUrl).host?.lowercase()?.let { it == "crunchyroll.com" || it.endsWith(".crunchyroll.com") } != true) {
            return ProviderEpisodeCheckResult.Failed("PROVIDER_REDIRECT_NOT_CRUNCHYROLL", checkedAt, false)
        }
        return CrunchyrollPublicPageParser.parse(
            page.html, page.finalUrl, title, seasonNumber, episodeNumber, checkedAt,
            page.httpStatus, page.responseType
        )
    }

    companion object {
        private suspend fun loadPublicPage(url: String): PublicProviderPage = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 25_000
                connection.setRequestProperty("User-Agent", "AniSentinel/0.11.0 public episode availability checker (Android; no login)")
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                when (val status = connection.responseCode) {
                    401 -> throw ProviderAccessException("LOGIN_REQUIRED", false)
                    403, 451 -> throw ProviderAccessException("REGION_BLOCKED", false)
                    429 -> throw ProviderAccessException("HTTP_429", true)
                    in 200..299 -> PublicProviderPage(
                        connection.url.toString(),
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() },
                        status,
                        connection.contentType
                    )
                    else -> throw ProviderAccessException("HTTP_$status", status >= 500)
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}

data class PublicProviderPage(
    val finalUrl: String,
    val html: String,
    val httpStatus: Int? = null,
    val responseType: String? = null
)

class ProviderAccessException(val code: String, val retryable: Boolean) : Exception(code)

object CrunchyrollPublicPageParser {
    fun parse(
        html: String,
        pageUrl: String,
        expectedTitle: String,
        seasonNumber: Int?,
        episodeNumber: Int,
        checkedAt: Instant,
        httpStatus: Int? = null,
        responseType: String? = null
    ): ProviderEpisodeCheckResult {
        if (html.isBlank()) return failed("PARSER_CHANGED", checkedAt, httpStatus, responseType, html.length, "EMPTY_BODY")
        val document = Jsoup.parse(html, pageUrl)
        val visible = normalize(document.text())
        val raw = html.replace("\\/", "/")
        val titleMatches = titleTokens(expectedTitle).any { it.length >= 5 && visible.contains(it) }
        val episodeMatches = listOf(
            Regex("\\\"episodeNumber\\\"\\s*:\\s*$episodeNumber(?:\\D|$)"),
            Regex("\\\"episode_number\\\"\\s*:\\s*\\\"?$episodeNumber\\\"?(?:\\D|$)"),
            Regex("\\bfolge\\s+$episodeNumber(?:\\D|$)", RegexOption.IGNORE_CASE),
            Regex("\\bepisode\\s+$episodeNumber(?:\\D|$)", RegexOption.IGNORE_CASE),
            Regex("(?:^|[/_-])episode[-_ ]?$episodeNumber(?:\\D|$)", RegexOption.IGNORE_CASE)
        ).any { it.containsMatchIn(raw) || it.containsMatchIn(document.text()) }
        val seasonMatches = seasonNumber == null || listOf(
            Regex("\\\"seasonNumber\\\"\\s*:\\s*$seasonNumber(?:\\D|$)"),
            Regex("\\\"season_number\\\"\\s*:\\s*\\\"?$seasonNumber\\\"?(?:\\D|$)"),
            Regex("\\bstaffel\\s+$seasonNumber(?:\\D|$)", RegexOption.IGNORE_CASE),
            Regex("\\bseason\\s+$seasonNumber(?:\\D|$)", RegexOption.IGNORE_CASE)
        ).any { it.containsMatchIn(raw) || it.containsMatchIn(document.text()) }

        if (!titleMatches && !episodeMatches) return failed(
            "PARSER_CHANGED", checkedAt, httpStatus, responseType, html.length, "TITLE_AND_EPISODE_MISSING"
        )
        if (!episodeMatches || !seasonMatches) {
            val total = Regex("\\\"totalEpisodeCount\\\"\\s*:\\s*(\\d+)")
                .find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
            return if (total != null && episodeNumber > total) {
                checked(pageUrl, seasonNumber, episodeNumber, false, null, null, checkedAt)
            } else failed("PARSER_CHANGED", checkedAt, httpStatus, responseType, html.length, "SEASON_OR_EPISODE_MISSING")
        }

        val germanSub = languageArrayContainsGerman(raw, "subtitleLocales") ||
            languageArrayContainsGerman(raw, "subtitleLanguages") ||
            languageArrayContainsGerman(raw, "subtitle_locales") ||
            Regex("deutsche\\s+untertitel", RegexOption.IGNORE_CASE).containsMatchIn(document.text()) ||
            Regex("untertitel\\s*:?\\s*(?:deutsch|german)", RegexOption.IGNORE_CASE).containsMatchIn(document.text())
        val germanDub = languageArrayContainsGerman(raw, "audioLocales") ||
            languageArrayContainsGerman(raw, "audioLanguages") ||
            languageFieldIsGerman(raw, "audio_locale") ||
            Regex("deutsche\\s+(?:synchro|audio|tonspur)", RegexOption.IGNORE_CASE).containsMatchIn(document.text()) ||
            Regex("audio\\s*:?\\s*(?:deutsch|german)", RegexOption.IGNORE_CASE).containsMatchIn(document.text())
        val languageMetadataPresent = Regex("(subtitleLocales|subtitleLanguages|subtitle_locales|audioLocales|audioLanguages|audio_locale)")
            .containsMatchIn(raw)
        val concreteEpisodeUrl = document.select("a[href*=/watch/]").firstOrNull { link ->
            Regex("(?:episode|e)\\s*$episodeNumber(?:\\D|$)", RegexOption.IGNORE_CASE).containsMatchIn(link.text()) ||
                Regex("(?:episode|e)[-_ ]?$episodeNumber(?:\\D|$)", RegexOption.IGNORE_CASE).containsMatchIn(link.attr("href"))
        }?.absUrl("href")?.takeIf(String::isNotBlank) ?: pageUrl
        return checked(
            concreteEpisodeUrl, seasonNumber, episodeNumber, true,
            germanSub.takeIf { it || languageMetadataPresent },
            germanDub.takeIf { it || languageMetadataPresent },
            checkedAt
        )
    }

    private fun checked(
        url: String,
        season: Int?,
        episode: Int,
        found: Boolean,
        sub: Boolean?,
        dub: Boolean?,
        checkedAt: Instant
    ) = ProviderEpisodeCheckResult.Checked(
        ProviderEpisodeAvailability(
            providerId = "CRUNCHYROLL",
            seasonNumber = season,
            episodeNumber = episode,
            episodeFound = found,
            germanSubAvailable = sub,
            germanDubAvailable = dub,
            availableSince = null,
            episodeUrl = url,
            checkedAt = checkedAt,
            evidenceType = "OFFICIAL_PROVIDER",
            evidenceUrl = url
        )
    )

    private fun failed(
        code: String,
        checkedAt: Instant,
        httpStatus: Int? = null,
        responseType: String? = null,
        responseBytes: Int? = null,
        parserStage: String? = null
    ): ProviderEpisodeCheckResult.Failed {
        val diagnosticCode = if (httpStatus == null && responseType == null) code else buildString {
            append(code)
            httpStatus?.let { append("|http=").append(it) }
            responseType?.substringBefore(';')?.let { append("|type=").append(it) }
            responseBytes?.let { append("|bytes=").append(it) }
            parserStage?.let { append("|stage=").append(it) }
        }
        return ProviderEpisodeCheckResult.Failed(diagnosticCode, checkedAt, code == "PARSER_CHANGED")
    }

    private fun languageArrayContainsGerman(raw: String, field: String): Boolean {
        val content = Regex("\\\"$field\\\"\\s*:\\s*\\[([^]]*)]", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.getOrNull(1) ?: return false
        return Regex("\\\"(?:de|de-DE|de_DE|ger)\\\"", RegexOption.IGNORE_CASE).containsMatchIn(content)
    }

    private fun languageFieldIsGerman(raw: String, field: String): Boolean =
        Regex("\\\"$field\\\"\\s*:\\s*\\\"(?:de|de-DE|de_DE|ger)\\\"", RegexOption.IGNORE_CASE)
            .containsMatchIn(raw)

    private fun titleTokens(value: String): List<String> = value
        .split(Regex("[:|–—-]"))
        .map(::normalize)
        .filter(String::isNotBlank)

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
