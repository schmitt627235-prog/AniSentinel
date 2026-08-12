package de.anisentinel.app.data.release

import de.anisentinel.app.data.anisearch.ReleaseCalendarSource
import de.anisentinel.app.data.anisearch.ReleaseSourceKind
import de.anisentinel.app.data.anisearch.SourceCalendarFetchResult
import de.anisentinel.app.data.anisearch.SourceEpisodeRelease
import de.anisentinel.app.data.anisearch.SourceFailureReason
import de.anisentinel.app.data.anilist.calendarTimeWindow
import de.anisentinel.app.data.anilist.DeviceTimeZoneProvider
import de.anisentinel.app.data.anilist.SystemDeviceTimeZoneProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class ReleaseStatus { SCHEDULED, DUE, CONFIRMED_AVAILABLE, DELAYED, CANCELLED, UNKNOWN }

data class AnimeRadarCalendarEntry(
    val radarId: String?,
    val aniListId: Long?,
    val titleGerman: String?,
    val titleEnglish: String?,
    val titleRomaji: String?,
    val titleNative: String?,
    val episodeNumber: Int?,
    val releaseAt: Instant,
    val sourceTimeZone: String?,
    val format: String?,
    val season: String?,
    val seasonYear: Int?,
    val countryOfOrigin: String?,
    val coverUrl: String?,
    val detailUrl: String?,
    val provider: String?,
    val providerUrl: String?,
    val language: String?,
    val status: ReleaseStatus
)

sealed interface AnimeRadarHttpResult {
    data class Success(val body: String) : AnimeRadarHttpResult
    data class Failure(val statusCode: Int?, val diagnostic: String, val retryNotBefore: Long?) : AnimeRadarHttpResult
}

class AnimeRadarClient(
    private val endpoint: String = "https://www.animeradar.de/api/anilist",
    private val clock: Clock = Clock.systemUTC(),
    private val transport: (suspend (String) -> AnimeRadarHttpResult)? = null
) {
    suspend fun post(body: String): AnimeRadarHttpResult = transport?.invoke(body) ?: withContext(Dispatchers.IO) {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 25_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "AniSentinel-Diagnose/0.10.0 (Android; limited calendar test)")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status in 200..299) AnimeRadarHttpResult.Success(response)
            else {
                val retrySeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                AnimeRadarHttpResult.Failure(
                    status,
                    "ANIME_RADAR_HTTP_$status",
                    retrySeconds?.let { clock.instant().epochSecond + it }
                )
            }
        } catch (error: IOException) {
            AnimeRadarHttpResult.Failure(null, "ANIME_RADAR_NETWORK:${error.javaClass.simpleName}", null)
        } finally {
            connection.disconnect()
        }
    }
}

data class ParsedAnimeRadarPage(val entries: List<AnimeRadarCalendarEntry>, val hasNextPage: Boolean)

class AnimeRadarCalendarParser {
    fun parse(json: String): ParsedAnimeRadarPage {
        val page = JSONObject(json).getJSONObject("data").getJSONObject("Page")
        val rows = page.getJSONArray("airingSchedules")
        val entries = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val media = row.getJSONObject("media")
                val titles = media.getJSONObject("title")
                val aniListId = media.getLong("id")
                val english = titles.optString("english").takeIf(String::isNotBlank)
                val romaji = titles.optString("romaji").takeIf(String::isNotBlank)
                val slug = slugify(english ?: romaji ?: aniListId.toString())
                add(
                    AnimeRadarCalendarEntry(
                        radarId = row.optLong("id").takeIf { it > 0 }?.toString(),
                        aniListId = aniListId,
                        titleGerman = null,
                        titleEnglish = english,
                        titleRomaji = romaji,
                        titleNative = titles.optString("native").takeIf(String::isNotBlank),
                        episodeNumber = row.optInt("episode").takeIf { it > 0 },
                        releaseAt = Instant.ofEpochSecond(row.getLong("airingAt")),
                        sourceTimeZone = "UTC",
                        format = media.optString("format").takeIf(String::isNotBlank),
                        season = media.optString("season").takeIf(String::isNotBlank),
                        seasonYear = media.optInt("seasonYear").takeIf { it > 0 },
                        countryOfOrigin = media.optString("countryOfOrigin").takeIf(String::isNotBlank),
                        coverUrl = media.optJSONObject("coverImage")?.optString("extraLarge")?.takeIf(String::isNotBlank)
                            ?: media.optJSONObject("coverImage")?.optString("large")?.takeIf(String::isNotBlank),
                        detailUrl = "https://www.animeradar.de/anime/$aniListId/$slug",
                        provider = null,
                        providerUrl = null,
                        language = null,
                        status = ReleaseStatus.SCHEDULED
                    )
                )
            }
        }
        return ParsedAnimeRadarPage(entries, page.getJSONObject("pageInfo").optBoolean("hasNextPage"))
    }

    private fun slugify(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(120)
}

class AnimeRadarCalendarSource(
    private val client: AnimeRadarClient = AnimeRadarClient(),
    private val parser: AnimeRadarCalendarParser = AnimeRadarCalendarParser(),
    private val timeZoneProvider: DeviceTimeZoneProvider = SystemDeviceTimeZoneProvider,
    private val enabled: Boolean = false
) : ReleaseCalendarSource {
    override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate): SourceCalendarFetchResult {
        if (!enabled) return unavailable("ANIME_RADAR_DISABLED_IN_THIS_BUILD")
        val window = calendarTimeWindow(start, endExclusive, timeZoneProvider.currentZoneId())
        val entries = mutableListOf<AnimeRadarCalendarEntry>()
        var page = 1
        var hasNext: Boolean
        do {
            val body = requestBody(page, window.fromEpochSeconds - 1, window.untilEpochSeconds)
            val response = client.post(body)
            if (response is AnimeRadarHttpResult.Failure) return SourceCalendarFetchResult.Unavailable(
                if (response.statusCode == 429) SourceFailureReason.RATE_LIMITED
                else if (response.statusCode != null) SourceFailureReason.HTTP else SourceFailureReason.NETWORK,
                response.diagnostic,
                response.retryNotBefore
            )
            val parsed = runCatching { parser.parse((response as AnimeRadarHttpResult.Success).body) }
                .getOrElse { return SourceCalendarFetchResult.Unavailable(
                    SourceFailureReason.INVALID_RESPONSE,
                    "ANIME_RADAR_INVALID_RESPONSE:${it.javaClass.simpleName}"
                ) }
            entries += parsed.entries
            hasNext = parsed.hasNextPage
            if (hasNext && page >= MAX_PAGES) return SourceCalendarFetchResult.Unavailable(
                SourceFailureReason.PAGINATION_LIMIT,
                "ANIME_RADAR_PAGINATION_LIMIT_REACHED"
            )
            page++
        } while (hasNext)

        val releases = entries.distinctBy { Triple(it.aniListId, it.episodeNumber, it.releaseAt) }.map { entry ->
            SourceEpisodeRelease(
                sourceKind = ReleaseSourceKind.ANIME_RADAR,
                sourceReleaseId = "animeradar:${entry.radarId ?: "${entry.aniListId}:${entry.releaseAt.epochSecond}"}",
                anisearchId = null,
                aniListId = entry.aniListId?.toInt(),
                titleGerman = entry.titleGerman,
                episodeNumber = entry.episodeNumber,
                releaseAtEpochSeconds = entry.releaseAt.epochSecond,
                provider = null,
                sourceUrl = entry.detailUrl ?: "https://www.animeradar.de/kalender",
                providerUrl = null,
                titleEnglish = entry.titleEnglish,
                titleRomaji = entry.titleRomaji,
                titleNative = entry.titleNative,
                coverUrl = entry.coverUrl,
                season = entry.season,
                seasonYear = entry.seasonYear
            )
        }
        return SourceCalendarFetchResult.Complete(releases, ReleaseSourceKind.ANIME_RADAR)
    }

    private fun unavailable(message: String) = SourceCalendarFetchResult.Unavailable(
        SourceFailureReason.NOT_CONFIGURED, message
    )

    private fun requestBody(page: Int, from: Long, until: Long) = JSONObject()
        .put("query", QUERY)
        .put("variables", JSONObject().put("page", page).put("from", from).put("until", until))
        .toString()

    companion object {
        // One week currently needs only a handful of pages. The larger hard ceiling
        // permits an explicit month view without allowing an unbounded crawl.
        internal const val MAX_PAGES = 40
        private const val QUERY = """
            query Calendar(${'$'}page: Int!, ${'$'}from: Int!, ${'$'}until: Int!) {
              Page(page: ${'$'}page, perPage: 20) {
                pageInfo { currentPage hasNextPage lastPage total perPage }
                airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}until, sort: TIME) {
                  id episode airingAt
                  media {
                    id siteUrl format status episodes season seasonYear countryOfOrigin
                    title { romaji english native }
                    coverImage { extraLarge large }
                    genres studios { nodes { name } }
                  }
                }
              }
            }
        """
    }
}

class AniListFallbackCalendarSource(private val source: ReleaseCalendarSource) : ReleaseCalendarSource {
    override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate) = source.fetchRange(start, endExclusive)
}

class ReleaseSourceCoordinator(
    private val primary: ReleaseCalendarSource,
    private val fallback: ReleaseCalendarSource
) : ReleaseCalendarSource {
    override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate): SourceCalendarFetchResult =
        when (val primaryResult = primary.fetchRange(start, endExclusive)) {
            is SourceCalendarFetchResult.Complete -> primaryResult
            is SourceCalendarFetchResult.Unavailable -> fallback.fetchRange(start, endExclusive)
        }
}
