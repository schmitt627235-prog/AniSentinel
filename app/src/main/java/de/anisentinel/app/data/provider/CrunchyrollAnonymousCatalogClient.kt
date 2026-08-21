package de.anisentinel.app.data.provider

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CrunchyrollCatalogEpisode(
    val seriesId: String,
    val seasonId: String,
    val seasonNumber: Int,
    val episodeId: String,
    val episodeNumber: Int,
    val sequenceNumber: Int?,
    val title: String?,
    val audioLocale: String?,
    val subtitleLocales: Set<String>,
    val availableAt: Instant?,
    val availabilityStatus: String?,
    val episodeUrl: String,
    val seasonTitle: String? = null
) {
    val releaseLanguages: Set<String> = buildSet {
        // Crunchyroll exposes one episode object per audio version. German subtitles
        // on a French/English audio object do not make that object the German OmU link.
        val originalAudio = audioLocale == null || audioLocale.equals("ja", true) || audioLocale.startsWith("ja-", true)
        if (originalAudio && subtitleLocales.any { it.equals("de", true) || it.startsWith("de-", true) }) add("GER_SUB")
        if (audioLocale.equals("de", true) || audioLocale?.startsWith("de-", true) == true) add("GER_DUB")
    }
}

data class CrunchyrollCatalogSeries(
    val seriesId: String,
    val seriesUrl: String,
    val episodes: List<CrunchyrollCatalogEpisode>
)

fun interface CrunchyrollCatalogTransport {
    suspend fun get(url: String): MetadataHttpResponse
}

/**
 * Anonymous public catalogue metadata only. No account, cookies, playback, manifests or DRM.
 * The short-lived anonymous token is kept in memory and reused until shortly before expiry.
 */
class CrunchyrollAnonymousCatalogTransport(
    private val clock: Clock = Clock.systemUTC()
) : CrunchyrollCatalogTransport {
    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAt: Instant = Instant.EPOCH
    private val anonymousId = UUID.randomUUID().toString()

    override suspend fun get(url: String): MetadataHttpResponse = withContext(Dispatchers.IO) {
        request(url, token())
    }

    @Synchronized
    private fun token(): String {
        val now = clock.instant()
        cachedToken?.takeIf { now.isBefore(tokenExpiresAt.minusSeconds(60)) }?.let { return it }
        val connection = URL("https://www.crunchyroll.com/auth/v1/token").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            // Public anonymous catalogue client credential used by Crunchyroll's web clients.
            connection.setRequestProperty("Authorization", "Basic dC1rZGdwMmg4YzNqdWI4Zm4wZnE6eWZMRGZNZnJZdktYaDRKWFMxTEVJMmNDcXUxdjVXYW4=")
            connection.setRequestProperty("ETP-Anonymous-ID", anonymousId)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val form = "grant_type=client_id&scope=offline_access&device_id=${URLEncoder.encode(anonymousId, "UTF-8")}&device_type=Android"
            connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "CRUNCHYROLL_ANONYMOUS_TOKEN_HTTP_$status" }
            val json = JSONObject(body)
            cachedToken = json.getString("access_token")
            tokenExpiresAt = now.plusSeconds(json.optLong("expires_in", 300).coerceAtLeast(60))
            return requireNotNull(cachedToken)
        } finally { connection.disconnect() }
    }

    private fun request(url: String, token: String): MetadataHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            MetadataHttpResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(), connection.url.toString(), connection.contentType)
        } finally { connection.disconnect() }
    }

    private companion object {
        const val USER_AGENT = "AniSentinel/0.24.8 anonymous-public-catalog (Android; no login; metadata only)"
    }
}

class CrunchyrollAnonymousCatalogClient(
    private val transport: CrunchyrollCatalogTransport = CrunchyrollAnonymousCatalogTransport()
) {
    suspend fun resolveSeries(reference: String?, title: String?): String? {
        reference?.let(CrunchyrollPublicWebAdapter::crunchyrollSeriesId)?.let { return it }
        val episodeId = reference?.let {
            Regex("/watch/([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)?.uppercase()
        }
        if (episodeId != null) {
            val response = transport.get("https://www.crunchyroll.com/content/v2/cms/objects/$episodeId?locale=de-DE")
            if (response.status in 200..299) firstDataObject(response.body)?.optString("series_id")?.takeIf(String::isNotBlank)?.let { return it }
        }
        val query = title?.takeIf(String::isNotBlank) ?: return null
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val response = transport.get("https://www.crunchyroll.com/content/v2/discover/search?q=$encoded&n=20&type=series&locale=de-DE")
        check(response.status in 200..299) { "CRUNCHYROLL_CATALOG_HTTP_${response.status}" }
        val wanted = normalizedCatalogTitle(query)
        return directSearchResults(response.body)
            .filter { it.optString("type").equals("series", true) || it.has("series_metadata") || it.has("series_id") }
            .mapNotNull { obj ->
                val metadata = obj.optJSONObject("series_metadata")
                val id = obj.optString("id").takeIf { it.startsWith("G") }
                    ?: metadata?.optString("series_id")?.takeIf(String::isNotBlank)
                    ?: obj.optString("series_id").takeIf(String::isNotBlank)
                val candidateTitle = obj.optString("title").takeIf(String::isNotBlank) ?: return@mapNotNull null
                id?.let { Triple(it, candidateTitle, normalizedCatalogTitle(candidateTitle)) }
            }
            .filter { it.third == wanted }
            .distinctBy { it.first }
            .singleOrNull()?.first
    }

    suspend fun loadSeries(seriesId: String): CrunchyrollCatalogSeries {
        // The CMS defaults to a small page. Historical availability must load the
        // complete series in one bounded request, otherwise later episodes are
        // incorrectly shown as unconfirmed across the whole app.
        val seasonsUrl = "https://www.crunchyroll.com/content/v2/cms/series/$seriesId/seasons?locale=de-DE"
        val seasons = loadAllPages(seasonsUrl)
        val seasonTitles = linkedMapOf<String, String?>()
        val seasonOrder = linkedMapOf<String, Int>()
        data class SeasonDescriptor(val id: String, val number: Int, val title: String?)
        val descriptors = mutableListOf<SeasonDescriptor>()
        for (index in 0 until seasons.length()) {
            val season = seasons.optJSONObject(index) ?: continue
            val seasonId = season.optString("id").takeIf(String::isNotBlank) ?: continue
            val seasonNumber = season.optInt("season_number", season.optInt("season_sequence_number", 0))
            seasonTitles[seasonId] = season.optString("title").takeIf(String::isNotBlank)
            seasonOrder[seasonId] = season.optInt("season_sequence_number", index + 1)
            descriptors += SeasonDescriptor(seasonId, seasonNumber, seasonTitles[seasonId])
        }
        // Long-running titles can expose dozens of language/arc season objects. A small,
        // bounded parallelism keeps refresh finite without creating an unbounded crawl.
        val semaphore = Semaphore(4)
        val rawEpisodes = coroutineScope {
            descriptors.map { descriptor -> async {
                semaphore.withPermit {
                    val rows = loadAllPages(
                        "https://www.crunchyroll.com/content/v2/cms/seasons/${descriptor.id}/episodes?locale=de-DE"
                    )
                    (0 until rows.length()).mapNotNull { rowIndex ->
                        parseEpisode(rows.optJSONObject(rowIndex), seriesId, descriptor.id, descriptor.number, descriptor.title)
                    }
                }
            } }.awaitAll().flatten()
        }
        val contentSeasonById = CrunchyrollSeasonStructure.contentSeasonNumbers(seasonTitles, seasonOrder)
        val episodes = rawEpisodes.map { episode ->
            episode.copy(
                seasonNumber = contentSeasonById[episode.seasonId] ?: episode.seasonNumber,
                seasonTitle = CrunchyrollSeasonStructure.displayTitle(episode.seasonTitle)
            )
        }.filter(CrunchyrollSeasonStructure::containsDeclaredEpisode)
        return CrunchyrollCatalogSeries(seriesId, CrunchyrollPublicWebAdapter.canonicalSeriesUrl(seriesId), episodes.distinctBy {
            listOf(it.seasonId, it.episodeId, it.audioLocale)
        })
    }

    /** Loads every anonymous CMS page with a hard safety bound (no unbounded crawl). */
    private suspend fun loadAllPages(baseUrl: String): org.json.JSONArray {
        val result = org.json.JSONArray()
        var offset = 0
        val pageFingerprints = mutableSetOf<String>()
        repeat(20) {
            val response = transport.get("$baseUrl&n=100&start=$offset")
            check(response.status in 200..299) { "CRUNCHYROLL_CATALOG_HTTP_${response.status}" }
            val page = dataArray(response.body)
            val fingerprint = (0 until page.length()).joinToString("|") { index ->
                page.optJSONObject(index)?.optString("id").orEmpty()
            }
            if (fingerprint.isNotBlank() && !pageFingerprints.add(fingerprint)) return result
            for (index in 0 until page.length()) result.put(page.opt(index))
            if (page.length() < 100) return result
            offset += page.length()
        }
        return result
    }

    private fun parseEpisode(
        obj: JSONObject?, seriesId: String, seasonId: String, fallbackSeason: Int, seasonTitle: String?
    ): CrunchyrollCatalogEpisode? {
        obj ?: return null
        val episodeId = obj.optString("id").takeIf(String::isNotBlank) ?: return null
        val episodeNumber = number(obj.opt("episode_number")) ?: number(obj.opt("episode")) ?: return null
        val seasonNumber = number(obj.opt("season_number")) ?: fallbackSeason
        val subtitles = obj.optJSONArray("subtitle_locales").toStringSet()
        return CrunchyrollCatalogEpisode(
            seriesId, seasonId, seasonNumber, episodeId, episodeNumber,
            number(obj.opt("sequence_number")) ?: number(obj.opt("episode_sequence_number")),
            obj.optString("title").takeIf(String::isNotBlank),
            obj.optString("audio_locale").takeIf(String::isNotBlank), subtitles,
            providerAvailableAt(obj), obj.optString("availability_status").takeIf(String::isNotBlank),
            "https://www.crunchyroll.com/watch/$episodeId", seasonTitle
        )
    }

    private fun providerAvailableAt(obj: JSONObject): Instant? = listOf(
        "premium_available_date", "available_date", "availability_starts"
    ).firstNotNullOfOrNull { key ->
        obj.optString(key).takeIf { it.isNotBlank() && !it.startsWith("9998-") && !it.startsWith("9999-") }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
    }
}

/** Collapses Crunchyroll audio/dub season variants into content seasons. */
object CrunchyrollSeasonStructure {
    private val declaredEpisodeRange = Regex(
        "\\((\\d+)\\s*[-–—]\\s*(\\d+|current)\\)",
        RegexOption.IGNORE_CASE
    )
    private val variantSuffix = Regex(
        "\\s*[\\[(](?:[^)\\]]*?(?:dub|sub|audio|synchro|untertitel|deutsch|german|english|french|spanish|italian|portuguese|hindi|arabic)[^)\\]]*)[)\\]]\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val trailingVariant = Regex(
        "\\s*[-â€“â€”]\\s*(?:german|deutsch|english|french|spanish|italian|portuguese|hindi|arabic)(?:\\s+(?:dub|audio|synchro|sub))?\\s*$",
        RegexOption.IGNORE_CASE
    )

    fun displayTitle(title: String?): String? = title?.let { value ->
        var clean = value.trim()
        do {
            val previous = clean
            clean = clean.replace(variantSuffix, "").replace(trailingVariant, "").trim()
        } while (clean != previous)
        clean.takeIf(String::isNotBlank)
    }

    /**
     * Crunchyroll occasionally places recap/special rows with small local numbers in a
     * content arc whose public label declares a global episode range. Those rows must
     * not shift the local numbering of the real arc episodes.
     */
    fun containsDeclaredEpisode(episode: CrunchyrollCatalogEpisode): Boolean {
        val match = episode.seasonTitle?.let(declaredEpisodeRange::find) ?: return true
        val first = match.groupValues[1].toInt()
        val last = match.groupValues[2].toIntOrNull()
        return episode.episodeNumber >= first && (last == null || episode.episodeNumber <= last)
    }

    fun contentSeasonNumbers(
        titlesBySeasonId: Map<String, String?>,
        orderBySeasonId: Map<String, Int>
    ): Map<String, Int> {
        val ordered = titlesBySeasonId.keys.sortedWith(
            compareBy<String> { orderBySeasonId[it] ?: Int.MAX_VALUE }.thenBy { it }
        )
        val numberByKey = linkedMapOf<String, Int>()
        return ordered.associateWith { id ->
            val clean = displayTitle(titlesBySeasonId[id]) ?: "season:${orderBySeasonId[id] ?: id}"
            val key = normalizedCatalogTitle(clean)
            numberByKey.getOrPut(key) { numberByKey.size + 1 }
        }
    }
}

private fun JSONArray?.toStringSet(): Set<String> = if (this == null) emptySet() else
    (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()

private fun number(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is String -> value.toDoubleOrNull()?.toInt()
    else -> null
}

private fun dataArray(body: String): JSONArray = JSONObject(body).optJSONArray("data") ?: JSONArray()
private fun firstDataObject(body: String): JSONObject? = dataArray(body).optJSONObject(0)

/**
 * Crunchyroll search responses contain result groups whose direct `items` are the
 * actual hits. Recursively walking the document is unsafe: recommendation and
 * metadata objects can contain unrelated series IDs and titles.
 */
private fun directSearchResults(body: String): List<JSONObject> {
    val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: return emptyList()
    val result = mutableListOf<JSONObject>()
    for (index in 0 until data.length()) {
        val group = data.optJSONObject(index) ?: continue
        val items = group.optJSONArray("items")
        if (items != null) {
            for (itemIndex in 0 until items.length()) items.optJSONObject(itemIndex)?.let(result::add)
        } else if (group.optString("type").equals("series", true)) {
            // Kept for the documented flat response variant.
            result += group
        }
    }
    return result
}

private fun normalizedCatalogTitle(value: String): String = java.text.Normalizer.normalize(
    value.lowercase(), java.text.Normalizer.Form.NFD
).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
