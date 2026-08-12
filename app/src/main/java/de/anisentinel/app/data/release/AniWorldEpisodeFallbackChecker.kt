package de.anisentinel.app.data.release

import de.anisentinel.app.domain.provider.ProviderEpisodeAvailability
import de.anisentinel.app.domain.provider.ProviderEpisodeCheckResult
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class AniWorldEpisodeFallbackChecker(
    private val clock: Clock = Clock.systemUTC(),
    private val loader: suspend (String) -> AniWorldEpisodePage = ::loadPage
) {
    suspend fun check(
        animePath: String?,
        seasonNumber: Int?,
        episodeNumber: Int,
        expectedLanguage: String?
    ): ProviderEpisodeCheckResult {
        val checkedAt = clock.instant()
        val slug = animePath?.let(::safeAnimeSlug)
            ?: return ProviderEpisodeCheckResult.Failed("ANIWORLD_FALLBACK_URL_MISSING", checkedAt, false)
        val url = AniWorldCalendarParser.CALENDAR_URL
        val page = try { loader(url) } catch (_: Exception) {
            return ProviderEpisodeCheckResult.Failed("ANIWORLD_FALLBACK_FAILED", checkedAt, true)
        }
        if (page.status == 404) return notFound(url, seasonNumber, episodeNumber, checkedAt)
        if (page.status !in 200..299 || page.html.isBlank()) {
            return ProviderEpisodeCheckResult.Failed("ANIWORLD_FALLBACK_HTTP_${page.status}", checkedAt, page.status >= 500)
        }
        return AniWorldEpisodePageParser.parseCalendar(
            page.html, url, slug, seasonNumber, episodeNumber, expectedLanguage, checkedAt
        )
    }

    private fun safeAnimeSlug(value: String): String? {
        val candidate = if (value.startsWith("https://")) value else "https://aniworld.to/anime/stream/${value.trim('/')}"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host?.lowercase() != "aniworld.to" || !uri.path.startsWith("/anime/stream/")) return null
        return uri.path.substringAfter("/anime/stream/").substringBefore('/').takeIf(String::isNotBlank)
    }

    private fun notFound(url: String, season: Int?, episode: Int, checkedAt: Instant) =
        ProviderEpisodeCheckResult.Checked(
            ProviderEpisodeAvailability(
                providerId = "ANIWORLD_FALLBACK",
                seasonNumber = season,
                episodeNumber = episode,
                episodeFound = false,
                germanSubAvailable = null,
                germanDubAvailable = null,
                availableSince = null,
                episodeUrl = null,
                checkedAt = checkedAt,
                evidenceType = "ANIWORLD_FALLBACK",
                evidenceUrl = url
            )
        )

    companion object {
        private suspend fun loadPage(url: String): AniWorldEpisodePage = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 25_000
                connection.setRequestProperty("User-Agent", "AniSentinel-DIAGNOSETEST/0.11.0 (public episode metadata fallback; no login)")
                connection.setRequestProperty("Accept", "text/html")
                val status = connection.responseCode
                AniWorldEpisodePage(
                    status,
                    (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                )
            } finally { connection.disconnect() }
        }
    }
}

data class AniWorldEpisodePage(val status: Int, val html: String)

object AniWorldEpisodePageParser {
    fun parseCalendar(
        html: String,
        url: String,
        animeSlug: String,
        season: Int?,
        episode: Int,
        expectedLanguage: String?,
        checkedAt: Instant
    ): ProviderEpisodeCheckResult {
        val document = Jsoup.parse(html, url)
        val expectedPath = "/anime/stream/$animeSlug/staffel-${season ?: 1}/episode-$episode"
        val matchingCards = document.select("section.calendarList div.seriesListContainer > div").filter { card ->
            card.selectFirst("a[href]")?.attr("href")?.substringBefore('?') == expectedPath
        }
        if (matchingCards.isEmpty()) {
            return ProviderEpisodeCheckResult.Failed("ANIWORLD_FALLBACK_CALENDAR_ENTRY_MISSING", checkedAt, true)
        }
        val matchingLanguageCards = matchingCards.filter { card ->
            val flags = card.select("img.flag").flatMap { listOf(it.attr("src"), it.attr("data-src"), it.attr("title"), it.attr("alt")) }
            when (expectedLanguage) {
                "GER_SUB" -> flags.any { it.contains("japanese-german", true) || it.contains("deutsch", true) && it.contains("untertitel", true) }
                "GER_DUB" -> flags.any { Regex("(?:^|/)german\\.svg$", RegexOption.IGNORE_CASE).containsMatchIn(it) || it.contains("deutsche sprache", true) }
                else -> true
            }
        }
        val onlineCard = matchingLanguageCards.firstOrNull { card ->
            card.select("[title]").any { it.attr("title").contains("Stream online", true) }
        }
        val available = onlineCard != null
        val sourceAvailableAt = onlineCard?.text()?.let { text ->
            Regex("Neu!?\\s*(\\d{1,2}):(\\d{2})\\s*Uhr", RegexOption.IGNORE_CASE).find(text)?.let { match ->
                val zone = java.time.ZoneId.systemDefault()
                var candidate = checkedAt.atZone(zone).toLocalDate()
                    .atTime(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                    .atZone(zone).toInstant()
                if (candidate.isAfter(checkedAt.plusSeconds(300))) candidate = candidate.minusSeconds(86_400)
                candidate
            }
        }
        val sub = available && expectedLanguage == "GER_SUB"
        val dub = available && expectedLanguage == "GER_DUB"
        return checked(
            java.net.URI(url).resolve(expectedPath).toString(), season, episode, available,
            sub.takeIf { available }, dub.takeIf { available }, checkedAt, sourceAvailableAt
        )
    }

    fun parse(
        html: String,
        url: String,
        season: Int?,
        episode: Int,
        expectedLanguage: String?,
        checkedAt: Instant
    ): ProviderEpisodeCheckResult {
        val document = Jsoup.parse(html, url)
        val candidateLinks = document.select("a[href*=/anime/stream/][href*=/episode-$episode]")
            .filter { it.attr("href").substringAfter("episode-$episode").let { tail -> tail.isBlank() || tail.startsWith('?') || tail.startsWith('#') } }
            .sortedByDescending { it.closest("tr") != null }
            .distinctBy { it.attr("href").substringBefore('?').substringBefore('#') }
        val exact = season?.let { requested -> candidateLinks.firstOrNull { "/staffel-$requested/episode-$episode" in it.attr("href") } }
        val chosen = exact ?: candidateLinks.singleOrNull()
        val episodeToken = Regex("(?:S${season ?: 1}E$episode|staffel[-/]\\d+.+episode[-/]$episode)", RegexOption.IGNORE_CASE)
        val found = chosen != null || episodeToken.containsMatchIn(document.text()) || episodeToken.containsMatchIn(html)
        if (!found) return ProviderEpisodeCheckResult.Failed("ANIWORLD_FALLBACK_PARSER_CHANGED", checkedAt, true)
        if (candidateLinks.size > 1 && chosen == null) {
            return ProviderEpisodeCheckResult.Failed("ANIWORLD_FALLBACK_EPISODE_AMBIGUOUS", checkedAt, true)
        }
        val evidenceScope = chosen?.closest("tr") ?: chosen?.parent() ?: document
        val flagPaths = evidenceScope.select("img.flag, img[src*=german], img[data-src*=german]")
            .flatMap { listOf(it.attr("src"), it.attr("data-src"), it.attr("title"), it.attr("alt")) }
        val sub = flagPaths.any { it.contains("japanese-german", true) || it.contains("untertitel", true) }
        val dub = flagPaths.any { value ->
            Regex("(?:^|/)german\\.svg$", RegexOption.IGNORE_CASE).containsMatchIn(value) ||
                value.contains("deutsche synchro", true) || value.contains("deutscher dub", true)
        }
        val evidenceUrl = chosen?.absUrl("href")?.takeIf(String::isNotBlank) ?: url
        return checked(evidenceUrl, season, episode, true, sub.takeIf { it || dub }, dub.takeIf { it || sub }, checkedAt)
    }

    private fun checked(
        url: String,
        season: Int?,
        episode: Int,
        found: Boolean,
        sub: Boolean?,
        dub: Boolean?,
        checkedAt: Instant,
        sourceAvailableAt: Instant? = null
    ) = ProviderEpisodeCheckResult.Checked(
        ProviderEpisodeAvailability(
            providerId = "ANIWORLD_FALLBACK",
            seasonNumber = season,
            episodeNumber = episode,
            episodeFound = found,
            germanSubAvailable = sub,
            germanDubAvailable = dub,
            availableSince = sourceAvailableAt,
            episodeUrl = null,
            checkedAt = checkedAt,
            evidenceType = "ANIWORLD_FALLBACK",
            evidenceUrl = url
        )
    )
}
