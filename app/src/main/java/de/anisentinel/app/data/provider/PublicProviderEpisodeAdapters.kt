package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.ProviderEpisodeAvailability
import de.anisentinel.app.domain.provider.ProviderMarketPolicy
import de.anisentinel.app.domain.provider.ProviderMetadataAdapter
import de.anisentinel.app.domain.provider.ProviderMetadataIdentity
import de.anisentinel.app.domain.provider.ProviderMetadataProbeRequest
import de.anisentinel.app.domain.provider.ProviderMetadataProbeResult
import java.time.Clock
import java.time.Instant
import org.jsoup.Jsoup

/** Public catalogue metadata only. No login, playback, manifest, licence or DRM endpoint is used. */
class NetflixPublicEpisodeAdapter(
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC()
) : ProviderMetadataAdapter {
    override val adapterId = "NETFLIX_PUBLIC_WEB"
    override fun supports(providerName: String) = providerName.contains("Netflix", true)

    override suspend fun probe(
        request: ProviderMetadataProbeRequest,
        identity: ProviderMetadataIdentity?
    ): ProviderMetadataProbeResult {
        val now = clock.instant()
        if (!ProviderMarketPolicy.isAppMarket(request.market)) return failed("NETFLIX_MARKET_NOT_DE", now, false)
        val reference = identity?.sourceUrl ?: request.providerUrl
            ?: return failed("NETFLIX_TITLE_URL_NOT_RESOLVED", now, false)
        return try {
            val response = transport.get(reference, mapOf("Accept-Language" to "de-DE,de;q=0.9"))
            if (response.status !in 200..299) return failed("NETFLIX_HTTP_${response.status}", now, response.status >= 500 || response.status == 429)
            val seriesId = identity?.seriesId ?: netflixTitleId(response.finalUrl) ?: netflixTitleId(response.body)
                ?: return failed("NETFLIX_TITLE_ID_NOT_RESOLVED", now, false)
            val stable = ProviderMetadataIdentity("NETFLIX", "DE", seriesId, sourceUrl = canonicalNetflixUrl(seriesId))
            PublicEpisodePageParser.parse(
                providerId = "NETFLIX", body = response.body, request = request,
                identity = stable, checkedAt = now, pageUrl = response.finalUrl,
                unavailableMarkers = listOf("isn’t available to watch in your country", "ist in deinem land derzeit nicht verfügbar")
            )
        } catch (error: Exception) {
            failed("NETFLIX_PUBLIC_PAGE_INVALID:${error.javaClass.simpleName}", now, true)
        }
    }

    companion object {
        internal fun netflixTitleId(value: String): String? =
            Regex("(?:/title/|\\\"videoId\\\"\\s*:\\s*\\\")(\\d{5,12})", RegexOption.IGNORE_CASE)
                .find(value.replace("\\/", "/"))?.groupValues?.getOrNull(1)

        internal fun canonicalNetflixUrl(id: String) = "https://www.netflix.com/title/$id"
    }

    private fun failed(code: String, at: Instant, retryable: Boolean) =
        ProviderMetadataProbeResult.CheckFailed(code, at, retryable)
}

class DisneyPlusPublicEpisodeAdapter(
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC()
) : ProviderMetadataAdapter {
    override val adapterId = "DISNEY_PLUS_PUBLIC_WEB"
    override fun supports(providerName: String) = providerName.contains("Disney", true)

    override suspend fun probe(request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity?): ProviderMetadataProbeResult {
        val now = clock.instant()
        if (!ProviderMarketPolicy.isAppMarket(request.market)) return failed("DISNEY_PLUS_MARKET_NOT_DE", now, false)
        val reference = identity?.sourceUrl ?: request.providerUrl
            ?: return failed("DISNEY_PLUS_TITLE_URL_NOT_RESOLVED", now, false)
        return try {
            val response = transport.get(reference, mapOf("Accept-Language" to "de-DE,de;q=0.9"))
            if (response.status !in 200..299) return failed("DISNEY_PLUS_HTTP_${response.status}", now, response.status >= 500 || response.status == 429)
            val entityId = identity?.seriesId ?: disneyEntityId(response.finalUrl) ?: disneyEntityId(response.body)
                ?: return failed("DISNEY_PLUS_ENTITY_NOT_RESOLVED", now, false)
            val stable = ProviderMetadataIdentity(
                "DISNEY_PLUS", "DE", entityId, seasonId = request.seasonNumber?.toString(),
                sourceUrl = canonicalDisneyUrl(entityId), seasonNumber = request.seasonNumber
            )
            PublicEpisodePageParser.parse("DISNEY_PLUS", response.body, request, stable, now, response.finalUrl)
        } catch (error: Exception) {
            failed("DISNEY_PLUS_PUBLIC_PAGE_INVALID:${error.javaClass.simpleName}", now, true)
        }
    }

    companion object {
        internal fun disneyEntityId(value: String): String? =
            Regex("(?:entity-|entity/)([0-9a-f]{8}-[0-9a-f-]{27,36})", RegexOption.IGNORE_CASE)
                .find(value.replace("\\/", "/"))?.groupValues?.getOrNull(1)?.lowercase()

        internal fun canonicalDisneyUrl(id: String) = "https://www.disneyplus.com/de-de/browse/entity-$id"
    }

    private fun failed(code: String, at: Instant, retryable: Boolean) =
        ProviderMetadataProbeResult.CheckFailed(code, at, retryable)
}

class AniversePublicEpisodeAdapter(
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC()
) : ProviderMetadataAdapter {
    override val adapterId = "ANIVERSE_PRIME_PUBLIC_WEB"
    override fun supports(providerName: String) = providerName.contains("Aniverse", true)

    override suspend fun probe(request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity?): ProviderMetadataProbeResult {
        val now = clock.instant()
        if (!ProviderMarketPolicy.isAppMarket(request.market)) return failed("ANIVERSE_MARKET_NOT_DE", now, false)
        val reference = identity?.sourceUrl ?: request.providerUrl
            ?: return failed("ANIVERSE_TITLE_URL_NOT_RESOLVED", now, false)
        return try {
            val response = transport.get(reference, mapOf("Accept-Language" to "de-DE,de;q=0.9"))
            if (response.status !in 200..299) return failed("ANIVERSE_HTTP_${response.status}", now, response.status >= 500 || response.status == 429)
            val contentId = identity?.seriesId ?: primeContentId(response.finalUrl) ?: primeContentId(response.body)
                ?: return failed("ANIVERSE_CONTENT_ID_NOT_RESOLVED", now, false)
            val pageText = Jsoup.parse(response.body).text()
            if (!pageText.contains("aniverse", true)) {
                return ProviderMetadataProbeResult.NotAvailableYet(
                    ProviderMetadataIdentity("ANIVERSE", "DE", contentId, sourceUrl = canonicalPrimeUrl(contentId)),
                    now, "ANIVERSE_CHANNEL_NOT_CONFIRMED"
                )
            }
            val stable = ProviderMetadataIdentity(
                "ANIVERSE", "DE", contentId, seasonId = request.seasonNumber?.toString(),
                sourceUrl = canonicalPrimeUrl(contentId), seasonNumber = request.seasonNumber
            )
            PublicEpisodePageParser.parse(
                "ANIVERSE", response.body, request, stable, now, response.finalUrl,
                unavailableMarkers = listOf("Dieses Video ist derzeit nicht verfügbar", "temporarily unavailable")
            )
        } catch (error: Exception) {
            failed("ANIVERSE_PUBLIC_PAGE_INVALID:${error.javaClass.simpleName}", now, true)
        }
    }

    companion object {
        internal fun primeContentId(value: String): String? =
            Regex("/detail/([A-Z0-9]{10,20})", RegexOption.IGNORE_CASE)
                .find(value.replace("\\/", "/"))?.groupValues?.getOrNull(1)?.uppercase()

        internal fun canonicalPrimeUrl(id: String) = "https://www.primevideo.com/-/de/detail/${id.uppercase()}?tr=de"
    }

    private fun failed(code: String, at: Instant, retryable: Boolean) =
        ProviderMetadataProbeResult.CheckFailed(code, at, retryable)
}

internal object PublicEpisodePageParser {
    fun parse(
        providerId: String,
        body: String,
        request: ProviderMetadataProbeRequest,
        identity: ProviderMetadataIdentity,
        checkedAt: Instant,
        pageUrl: String,
        unavailableMarkers: List<String> = emptyList()
    ): ProviderMetadataProbeResult {
        val document = Jsoup.parse(body, pageUrl)
        val text = document.text().replace('\u00a0', ' ')
        val unavailable = unavailableMarkers.any { text.contains(it, true) }
        val episodePattern = episodePattern(request.seasonNumber, request.episodeNumber)
        val episodeMatch = episodePattern.find(text) ?: episodePattern.find(body.replace("\\/", "/"))
        val structured = structuredEpisode(body, request.seasonNumber, request.episodeNumber)
        val stable = identity.copy(
            seasonId = identity.seasonId ?: request.seasonNumber?.toString(),
            episodeId = structured?.episodeId ?: episodeMatch?.groups?.get("episodeId")?.value,
            seasonNumber = identity.seasonNumber ?: request.seasonNumber,
            sourceUrl = identity.sourceUrl ?: pageUrl
        )
        if (unavailable) {
            return ProviderMetadataProbeResult.NotAvailableYet(stable, checkedAt, "${providerId}_EPISODE_NOT_AVAILABLE")
        }
        if (episodeMatch == null && structured == null) {
            // A dynamic shell without an inspectable episode index cannot prove absence.
            // Only a successfully parsed catalogue containing other episodes may produce
            // the conclusive NOT_AVAILABLE result.
            return if (containsStructuredEpisodeIndex(body)) {
                ProviderMetadataProbeResult.NotAvailableYet(stable, checkedAt, "${providerId}_EPISODE_NOT_AVAILABLE")
            } else {
                ProviderMetadataProbeResult.CheckFailed(
                    "${providerId}_PUBLIC_EPISODE_INDEX_NOT_PARSEABLE", checkedAt, true
                )
            }
        }
        val context = structured?.context ?: episodeMatch?.let { match ->
            val contextStart = (match.range.first - 220).coerceAtLeast(0)
            val contextEnd = (match.range.last + 420).coerceAtMost(text.lastIndex)
            if (text.isEmpty()) "" else text.substring(contextStart, contextEnd + 1)
        }.orEmpty()
        val globalLanguage = text.take(2_000)
        val sub = hasGermanSub(context) || hasGermanSub(globalLanguage)
        val dub = hasGermanDub(context) || hasGermanDub(globalLanguage)
        val expectedLanguagePresent = when (request.expectedLanguage) {
            "GER_SUB" -> sub
            "GER_DUB" -> dub
            else -> sub || dub || request.expectedLanguage == null
        }
        if (!expectedLanguagePresent) {
            return ProviderMetadataProbeResult.NotAvailableYet(stable, checkedAt, "${providerId}_EXPECTED_LANGUAGE_NOT_CONFIRMED")
        }
        val episodeUrl = document.selectFirst("a[href]:matchesOwn((?i)(S\\d+\\s*[:·-]?\\s*(?:F|E|Folge|Episode)\\s*${request.episodeNumber}|(?:Folge|Episode)\\s*${request.episodeNumber}))")
            ?.absUrl("href")?.takeIf(String::isNotBlank)
            ?: structured?.episodeUrl
            ?: stable.sourceUrl
        return ProviderMetadataProbeResult.Available(
            ProviderEpisodeAvailability(
                providerId, request.seasonNumber, request.episodeNumber, true, sub, dub,
                null, episodeUrl, checkedAt, "OFFICIAL_PUBLIC_CATALOG_METADATA", pageUrl
            ),
            stable.copy(sourceUrl = stable.sourceUrl ?: pageUrl)
        )
    }

    private fun episodePattern(season: Int?, episode: Int): Regex {
        val seasonPart = season?.let { "(?:S(?:taffel)?\\s*0?$it\\s*[:·/\\-]?\\s*)" } ?: "(?:S(?:taffel)?\\s*\\d+\\s*[:·/\\-]?\\s*)?"
        return Regex(
            "(?<episodeId>[A-Z0-9_-]{6,30}\\s+)?(?:$seasonPart(?:F|E|Folge|Episode)\\s*0?$episode\\b|(?:Folge|Episode)\\s*0?$episode\\b)",
            setOf(RegexOption.IGNORE_CASE)
        )
    }

    private data class StructuredEpisode(
        val episodeId: String?,
        val episodeUrl: String?,
        val context: String
    )

    private val episodeNumberField = Regex(
        "\"(?:episodeNumber|episode_number|episode)\"\\s*:\\s*\"?(\\d+)\"?",
        RegexOption.IGNORE_CASE
    )
    private val seasonNumberField = Regex(
        "\"(?:seasonNumber|season_number|season)\"\\s*:\\s*\"?(\\d+)\"?",
        RegexOption.IGNORE_CASE
    )
    private val episodeIdField = Regex(
        "\"(?:episodeId|episode_id|videoId|id)\"\\s*:\\s*\"([^\"}]{4,80})",
        RegexOption.IGNORE_CASE
    )
    private val episodeUrlField = Regex(
        "\"(?:episodeUrl|episode_url|watchUrl|url)\"\\s*:\\s*\"(https?:[^\"]+)",
        RegexOption.IGNORE_CASE
    )

    private fun normalizedStructuredBody(body: String): String =
        body.replace("\\/", "/").replace("\\\"", "\"")

    private fun containsStructuredEpisodeIndex(body: String): Boolean =
        episodeNumberField.find(normalizedStructuredBody(body)) != null

    private fun structuredEpisode(body: String, season: Int?, episode: Int): StructuredEpisode? {
        val normalized = normalizedStructuredBody(body)
        return episodeNumberField.findAll(normalized)
            .filter { it.groupValues[1].toIntOrNull() == episode }
            .mapNotNull { match ->
                val start = (match.range.first - 1_200).coerceAtLeast(0)
                val end = (match.range.last + 1_200).coerceAtMost(normalized.lastIndex)
                if (end < start) return@mapNotNull null
                val context = normalized.substring(start, end + 1)
                val parsedSeasons = seasonNumberField.findAll(context)
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                    .toSet()
                if (season != null && parsedSeasons.isNotEmpty() && season !in parsedSeasons) {
                    return@mapNotNull null
                }
                StructuredEpisode(
                    episodeId = episodeIdField.find(context)?.groupValues?.getOrNull(1),
                    episodeUrl = episodeUrlField.find(context)?.groupValues?.getOrNull(1),
                    context = Jsoup.parse(context).text().ifBlank { context }
                )
            }
            .firstOrNull()
    }

    private fun hasGermanSub(value: String) = listOf(
        "deutsche untertitel", "deutsch untertitelt", "untertitel: deutsch", "untertitel deutsch",
        "german subtitles", "vostde", "ger_sub"
    ).any { value.contains(it, true) }

    private fun hasGermanDub(value: String) = listOf(
        "deutsche synchro", "deutsch synchronisiert", "audio: deutsch", "audiosprache deutsch",
        "german audio", "deutsche fassung", "vde", "ger_dub", "dt./ov"
    ).any { value.contains(it, true) }
}
