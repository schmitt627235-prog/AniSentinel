package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.*
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

data class MetadataHttpResponse(val status: Int, val body: String, val finalUrl: String, val contentType: String? = null)

fun interface ProviderMetadataTransport {
    suspend fun get(url: String, headers: Map<String, String>): MetadataHttpResponse
}

class PublicProviderMetadataTransport : ProviderMetadataTransport {
    override suspend fun get(url: String, headers: Map<String, String>): MetadataHttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "AniSentinel/0.24.2 provider-metadata-diagnostic (Android; no login; no playback)")
            connection.setRequestProperty("Accept", "application/json,text/html,application/xhtml+xml")
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            MetadataHttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(), connection.url.toString(), connection.contentType)
        } finally { connection.disconnect() }
    }
}

/** Anonymous ADN catalogue metadata only; never calls login, player, manifest or DRM endpoints. */
class AdnMetadataAdapter(
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC()
) : ProviderMetadataAdapter {
    override val adapterId = "ADN_STRUCTURED_METADATA_PROBE"
    override fun supports(providerName: String) = providerName.equals("ADN", true) ||
        providerName.contains("Animation Digital Network", true)

    override suspend fun probe(request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity?): ProviderMetadataProbeResult {
        val now = clock.instant()
        if (request.market != ProviderMarketPolicy.GERMANY) return ProviderMetadataProbeResult.CheckFailed("ADN_MARKET_NOT_DE", now, false)
        return try {
            val showIdentity = identity ?: findShow(request, now) ?: return ProviderMetadataProbeResult.CheckFailed("ADN_TITLE_NOT_MATCHED", now, false)
            val url = "https://gw.api.animationdigitalnetwork.com/video/show/${showIdentity.seriesId}?maxAgeCategory=18&limit=-1&order=asc"
            val response = transport.get(url, mapOf("X-Target-Distribution" to "de"))
            if (response.status !in 200..299) return failed("ADN_HTTP_${response.status}", now, response.status >= 500 || response.status == 429)
            parseEpisodes(response.body, request, showIdentity.copy(sourceUrl = response.finalUrl), now)
        } catch (_: Exception) { failed("ADN_RESPONSE_INVALID", now, true) }
    }

    private suspend fun findShow(request: ProviderMetadataProbeRequest, now: Instant): ProviderMetadataIdentity? {
        val query = URLEncoder.encode(request.title, Charsets.UTF_8.name())
        val url = "https://gw.api.animationdigitalnetwork.com/show/catalog?search=$query&limit=20&offset=0"
        val response = transport.get(url, mapOf("X-Target-Distribution" to "de"))
        if (response.status !in 200..299) return null
        val candidates = collectObjects(response.body).mapNotNull { obj ->
            val id = string(obj, "id") ?: string(obj, "showId") ?: return@mapNotNull null
            val title = string(obj, "title") ?: string(obj, "name") ?: return@mapNotNull null
            Triple(id, title, normalized(title))
        }.distinctBy { it.first }
        val target = normalized(request.title)
        val exact = candidates.filter { it.third == target }
        val selected = exact.singleOrNull() ?: candidates.filter { titleSimilarity(target, it.third) >= .86 }.singleOrNull() ?: return null
        return ProviderMetadataIdentity("ADN", "DE", selected.first, sourceUrl = response.finalUrl)
    }

    internal fun parseEpisodes(body: String, request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity, now: Instant): ProviderMetadataProbeResult {
        val episode = collectObjects(body).firstOrNull { obj ->
            val number = int(obj, "shortNumber") ?: int(obj, "episodeNumber") ?: int(obj, "number")
            val season = int(obj, "season") ?: int(obj, "seasonNumber")
            number == request.episodeNumber && (request.seasonNumber == null || season == null || season == request.seasonNumber)
        } ?: return ProviderMetadataProbeResult.NotAvailableYet(identity, now, "ADN_EPISODE_NOT_LISTED")
        val languages = stringSet(episode.opt("languages"))
        val sub = "vostde" in languages
        val dub = "vde" in languages
        val episodeId = string(episode, "id") ?: string(episode, "videoId")
        val resolved = identity.copy(episodeId = episodeId)
        val expectedPresent = when (request.expectedLanguage) { "GER_SUB" -> sub; "GER_DUB" -> dub; else -> sub || dub }
        if (!expectedPresent) return ProviderMetadataProbeResult.NotAvailableYet(resolved, now, "ADN_EXPECTED_LANGUAGE_NOT_LISTED")
        val episodeUrl = episodeId?.let { "https://animationdigitalnetwork.com/de/video/-/$it" } ?: identity.sourceUrl
        return ProviderMetadataProbeResult.Available(ProviderEpisodeAvailability(
            "ADN", request.seasonNumber, request.episodeNumber, true, sub, dub, null, episodeUrl,
            now, "OFFICIAL_STRUCTURED_METADATA", identity.sourceUrl
        ), resolved)
    }

    private fun failed(code: String, now: Instant, retryable: Boolean) = ProviderMetadataProbeResult.CheckFailed(code, now, retryable)
}

/** Crunchyroll structured CMS diagnostic. Anonymous 401 responses are preserved as technical failure. */
class CrunchyrollMetadataAdapter(
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val catalogClient: CrunchyrollAnonymousCatalogClient = CrunchyrollAnonymousCatalogClient()
) : ProviderMetadataAdapter {
    override val adapterId = "CRUNCHYROLL_STRUCTURED_METADATA_PROBE"
    override fun supports(providerName: String) = providerName.contains("Crunchyroll", true) &&
        !providerName.contains("Amazon", true) && !providerName.contains("Channel", true)

    override suspend fun probe(request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity?): ProviderMetadataProbeResult {
        val now = clock.instant()
        return try {
            val seriesId = identity?.seriesId ?: catalogClient.resolveSeries(identity?.sourceUrl ?: request.providerUrl, request.title)
                ?: return ProviderMetadataProbeResult.CheckFailed("CRUNCHYROLL_SERIES_NOT_IDENTIFIED", now, false)
            val catalog = catalogClient.loadSeries(seriesId)
            val normalized = CrunchyrollEpisodeNormalizer.resolve(
                catalog.episodes, request.seasonNumber, request.episodeNumber, request.expectedLanguage,
                identity?.seasonId, identity?.episodeId
            )
            if (normalized.status == CrunchyrollNormalizationStatus.AMBIGUOUS || normalized.status == CrunchyrollNormalizationStatus.PARSER_FAILED) {
                return ProviderMetadataProbeResult.CheckFailed(
                    "CRUNCHYROLL_EPISODE_${normalized.status.name}:${normalized.diagnostic}", now, false
                )
            }
            if (normalized.status == CrunchyrollNormalizationStatus.NOT_FOUND) return ProviderMetadataProbeResult.NotAvailableYet(
                ProviderMetadataIdentity("CRUNCHYROLL", "DE", seriesId, sourceUrl = catalog.seriesUrl), now,
                "CRUNCHYROLL_EPISODE_NOT_LISTED:${normalized.diagnostic}"
            )
            val matching = requireNotNull(normalized.episode)
            val stable = ProviderMetadataIdentity(
                provider = "CRUNCHYROLL", market = "DE", seriesId = seriesId,
                seasonId = matching.seasonId, episodeId = matching.episodeId,
                sourceUrl = catalog.seriesUrl, seasonNumber = matching.seasonNumber
            )
            val catalogSaysAvailable = matching.availabilityStatus?.lowercase() in setOf("available", "premium_only", "free")
            if (!catalogSaysAvailable && (matching.availableAt == null || matching.availableAt.isAfter(now))) return ProviderMetadataProbeResult.NotAvailableYet(
                stable, now, "CRUNCHYROLL_EPISODE_NOT_AVAILABLE_YET"
            )
            ProviderMetadataProbeResult.Available(ProviderEpisodeAvailability(
                "CRUNCHYROLL", matching.seasonNumber, matching.episodeNumber, true,
                "GER_SUB" in matching.releaseLanguages, "GER_DUB" in matching.releaseLanguages,
                matching.availableAt, matching.episodeUrl, now, "OFFICIAL_ANONYMOUS_CATALOG_METADATA", catalog.seriesUrl
            ), stable)
        } catch (error: Exception) {
            ProviderMetadataProbeResult.CheckFailed(error.message ?: "CRUNCHYROLL_CATALOG_RESPONSE_INVALID", now, true)
        }
    }

    internal fun parseEpisodes(body: String, request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity, now: Instant, url: String): ProviderMetadataProbeResult {
        val episode = collectObjects(body).firstOrNull { int(it, "episode_number") == request.episodeNumber }
            ?: return ProviderMetadataProbeResult.NotAvailableYet(identity, now, "CRUNCHYROLL_EPISODE_NOT_LISTED")
        val audio = string(episode, "audio_locale").orEmpty().lowercase()
        val subtitles = stringSet(episode.opt("subtitle_locales"))
        val sub = subtitles.any { it == "de" || it.startsWith("de-") }
        val dub = audio == "de" || audio.startsWith("de-")
        val episodeId = string(episode, "id")
        val resolved = identity.copy(episodeId = episodeId, sourceUrl = url)
        val expectedPresent = when (request.expectedLanguage) { "GER_SUB" -> sub; "GER_DUB" -> dub; else -> sub || dub }
        if (!expectedPresent) return ProviderMetadataProbeResult.NotAvailableYet(resolved, now, "CRUNCHYROLL_EXPECTED_LANGUAGE_NOT_LISTED")
        return ProviderMetadataProbeResult.Available(ProviderEpisodeAvailability(
            "CRUNCHYROLL", request.seasonNumber, request.episodeNumber, true, sub, dub, null,
            episodeId?.let { "https://www.crunchyroll.com/watch/$it" }, now, "OFFICIAL_STRUCTURED_METADATA", url
        ), resolved)
    }
}

/** Public German series-page probe. No account, cookies, playback, manifests or DRM. */
class CrunchyrollPublicWebAdapter(
    private val checker: ProviderEpisodeChecker = CrunchyrollEpisodeChecker(),
    private val clock: Clock = Clock.systemUTC()
) : ProviderMetadataAdapter {
    override val adapterId = "CRUNCHYROLL_PUBLIC_WEB_PROBE"
    override fun supports(providerName: String) = providerName.contains("Crunchyroll", true) &&
        !providerName.contains("Amazon", true) && !providerName.contains("Channel", true)

    override suspend fun probe(request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity?): ProviderMetadataProbeResult {
        val now = clock.instant()
        val seriesUrl = identity?.sourceUrl ?: request.providerUrl
        val seriesId = identity?.seriesId ?: seriesUrl?.let(::crunchyrollSeriesId)
        if (seriesUrl == null || seriesId == null) return ProviderMetadataProbeResult.CheckFailed(
            "CRUNCHYROLL_PUBLIC_SERIES_URL_NOT_IDENTIFIED", now, false
        )
        val stable = ProviderMetadataIdentity("CRUNCHYROLL_PUBLIC_WEB", "DE", seriesId, sourceUrl = seriesUrl)
        return when (val result = checker.checkEpisode(
            "Crunchyroll", request.title, request.seasonNumber, request.episodeNumber,
            request.expectedLanguage, seriesUrl, request.expectedAt
        )) {
            is ProviderEpisodeCheckResult.Failed -> ProviderMetadataProbeResult.CheckFailed(result.code, result.checkedAt, result.retryable)
            is ProviderEpisodeCheckResult.Checked -> {
                val available = result.availability
                val expectedLanguageFound = when (request.expectedLanguage) {
                    "GER_SUB" -> available.germanSubAvailable == true
                    "GER_DUB" -> available.germanDubAvailable == true
                    else -> available.germanSubAvailable == true || available.germanDubAvailable == true
                }
                if (!available.episodeFound) ProviderMetadataProbeResult.NotAvailableYet(stable, available.checkedAt, "CRUNCHYROLL_PUBLIC_EPISODE_NOT_LISTED")
                else if (!expectedLanguageFound) ProviderMetadataProbeResult.NotAvailableYet(stable, available.checkedAt, "CRUNCHYROLL_PUBLIC_LANGUAGE_NOT_CONFIRMED")
                else ProviderMetadataProbeResult.Available(available.copy(evidenceType = "OFFICIAL_PUBLIC_WEB_METADATA"), stable)
            }
        }
    }

    companion object {
        internal fun crunchyrollSeriesId(reference: String): String? {
            val decoded = reference.replace("\\/", "/")
            return Regex("/(?:[a-z]{2}(?:-[a-z]{2})?/)?series/(G[A-Z0-9]+)(?:[/\\?#]|$)", RegexOption.IGNORE_CASE)
                .find(decoded)?.groupValues?.getOrNull(1)?.uppercase()
                ?: Regex("[\\\"'](?:series_id|seriesId)[\\\"']\\s*[:=]\\s*[\\\"'](G[A-Z0-9]+)[\\\"']", RegexOption.IGNORE_CASE)
                    .find(decoded)?.groupValues?.getOrNull(1)?.uppercase()
        }

        internal fun canonicalSeriesUrl(seriesId: String): String =
            "https://www.crunchyroll.com/de/series/${seriesId.uppercase()}"
    }
}

/** A /de/videos/new item is deliberately only a trigger and has no availability field. */
data class CrunchyrollReleaseSignal(
    val title: String, val seriesId: String, val seriesUrl: String,
    val visibleLanguageLabel: String?, val detectedAt: Instant
)

object CrunchyrollNewReleaseSignalParser {
    fun parse(html: String, baseUrl: String, detectedAt: Instant): List<CrunchyrollReleaseSignal> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("a[href*=/series/]").mapNotNull { link ->
            val url = link.absUrl("href").ifBlank { link.attr("href") }
            val id = CrunchyrollPublicWebAdapter.crunchyrollSeriesId(url) ?: return@mapNotNull null
            val title = link.attr("aria-label").ifBlank { link.text() }.trim()
            if (title.isBlank()) return@mapNotNull null
            val nearby = link.parent()?.text().orEmpty()
            val label = when {
                nearby.contains("Untertitel | Synchro", true) -> "Untertitel | Synchro"
                nearby.contains("Synchro", true) -> "Synchro"
                nearby.contains("Untertitel", true) -> "Untertitel"
                else -> null
            }
            CrunchyrollReleaseSignal(title, id, url, label, detectedAt)
        }.distinctBy { it.seriesId }
    }
}

private fun collectObjects(body: String): List<JSONObject> {
    val root: Any = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
    val result = mutableListOf<JSONObject>()
    fun visit(value: Any?) { when (value) {
        is JSONObject -> { result += value; value.keys().forEachRemaining { visit(value.opt(it)) } }
        is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
    } }
    visit(root)
    return result
}

private fun string(obj: JSONObject, key: String): String? = obj.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank)
private fun int(obj: JSONObject, key: String): Int? = when (val value = obj.opt(key)) { is Number -> value.toInt(); is String -> value.toDoubleOrNull()?.toInt(); else -> null }
private fun stringSet(value: Any?): Set<String> = when (value) {
    is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it)?.toString()?.lowercase() }.toSet()
    is String -> value.split(',').map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
    else -> emptySet()
}
private fun normalized(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
private fun titleSimilarity(a: String, b: String): Double { val aa=a.split(' ').filter(String::isNotBlank).toSet(); val bb=b.split(' ').filter(String::isNotBlank).toSet(); return if (aa.isEmpty() || bb.isEmpty()) 0.0 else aa.intersect(bb).size.toDouble()/aa.union(bb).size }
