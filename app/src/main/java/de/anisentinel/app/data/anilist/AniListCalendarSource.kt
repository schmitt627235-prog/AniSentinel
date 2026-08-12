package de.anisentinel.app.data.anilist

import de.anisentinel.app.data.anisearch.ReleaseSourceKind
import de.anisentinel.app.data.anisearch.SourceCalendarFetchResult
import de.anisentinel.app.data.anisearch.SourceEpisodeRelease
import de.anisentinel.app.data.anisearch.SourceFailureReason
import de.anisentinel.app.data.anisearch.ReleaseCalendarSource
import java.time.LocalDate
import org.json.JSONObject

class AniListCalendarSource(
    private val httpClient: AniListGraphQlHttpClient = AniListGraphQlHttpClient(),
    private val request: (suspend (String) -> String)? = null,
    private val timeZoneProvider: DeviceTimeZoneProvider = SystemDeviceTimeZoneProvider
) : ReleaseCalendarSource {
    override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate): SourceCalendarFetchResult {
        val window = calendarTimeWindow(start, endExclusive, timeZoneProvider.currentZoneId())
        val from = window.fromEpochSeconds - 1
        val until = window.untilEpochSeconds
        val releases = mutableListOf<SourceEpisodeRelease>()
        var page = 1
        var hasNext: Boolean
        do {
            val body = JSONObject().put("query", QUERY).put(
                "variables", JSONObject().put("page", page).put("from", from).put("until", until)
            ).toString()
            val response = request?.let { loader ->
                runCatching { GraphQlHttpResult.Success(loader(body), 200, emptyMap()) }.getOrElse { error ->
                    return SourceCalendarFetchResult.Unavailable(SourceFailureReason.NETWORK, error.message ?: "ANILIST_NETWORK_OR_JSON_ERROR")
                }
            } ?: httpClient.execute("CALENDAR:$page:$from:$until", body)
            val responseBody = when (response) {
                is GraphQlHttpResult.Success -> response.body
                is GraphQlHttpResult.HttpFailure -> return SourceCalendarFetchResult.Unavailable(
                    if (response.statusCode == 429) SourceFailureReason.RATE_LIMITED else SourceFailureReason.HTTP,
                    httpClient.classify(response)?.diagnostic ?: "ANILIST_HTTP_${response.statusCode}",
                    maxOf(
                        response.rateLimitResetEpochSeconds ?: 0L,
                        httpClient.retryNotBeforeEpochSeconds()
                    ).takeIf { it > 0L }
                )
                is GraphQlHttpResult.NetworkFailure -> return SourceCalendarFetchResult.Unavailable(
                    SourceFailureReason.NETWORK,
                    httpClient.classify(response)?.diagnostic ?: "ANILIST_NETWORK_ERROR"
                )
            }
            val root = runCatching { JSONObject(responseBody) }.getOrElse { error ->
                return SourceCalendarFetchResult.Unavailable(SourceFailureReason.INVALID_RESPONSE, "INVALID_JSON:${error.message}")
            }
            if (root.has("errors")) return SourceCalendarFetchResult.Unavailable(SourceFailureReason.INVALID_RESPONSE, "ANILIST_GRAPHQL_ERROR")
            val pageNode = root.getJSONObject("data").getJSONObject("Page")
            val rows = pageNode.getJSONArray("airingSchedules")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val media = row.getJSONObject("media")
                val id = media.getInt("id")
                val title = media.getJSONObject("title")
                val english = title.optString("english").takeIf(String::isNotBlank)
                val romaji = title.optString("romaji").takeIf(String::isNotBlank)
                val native = title.optString("native").takeIf(String::isNotBlank)
                val cover = media.optJSONObject("coverImage")
                val episode = row.optInt("episode").takeIf { it > 0 }
                val airingAt = row.getLong("airingAt")
                releases += SourceEpisodeRelease(
                    sourceKind = ReleaseSourceKind.ANILIST_FALLBACK,
                    sourceReleaseId = "anilist:$id:${episode ?: airingAt}",
                    anisearchId = null,
                    aniListId = id,
                    titleGerman = null,
                    episodeNumber = episode,
                    releaseAtEpochSeconds = airingAt,
                    provider = null,
                    sourceUrl = "https://anilist.co/anime/$id",
                    providerUrl = null,
                    titleEnglish = english,
                    titleRomaji = romaji,
                    titleNative = native,
                    description = media.optString("description").takeIf(String::isNotBlank),
                    coverUrl = cover?.optString("extraLarge")?.takeIf(String::isNotBlank)
                        ?: cover?.optString("large")?.takeIf(String::isNotBlank),
                    bannerUrl = media.optString("bannerImage").takeIf(String::isNotBlank),
                    season = media.optString("season").takeIf(String::isNotBlank),
                    seasonYear = media.optInt("seasonYear").takeIf { it > 0 },
                    totalEpisodes = media.optInt("episodes").takeIf { it > 0 }
                )
            }
            hasNext = pageNode.getJSONObject("pageInfo").optBoolean("hasNextPage", false)
            if (hasNext && page >= MAX_PAGES) {
                return SourceCalendarFetchResult.Unavailable(
                    SourceFailureReason.PAGINATION_LIMIT,
                    "ANILIST_PAGINATION_LIMIT_REACHED"
                )
            }
            page++
        } while (hasNext)
        return SourceCalendarFetchResult.Complete(
            releases.distinctBy { it.sourceReleaseId },
            ReleaseSourceKind.ANILIST_FALLBACK
        )
    }

    companion object {
        internal const val MAX_PAGES = 20
        private const val QUERY = """
            query Calendar(${'$'}page: Int!, ${'$'}from: Int!, ${'$'}until: Int!) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}until, sort: TIME) {
                  episode airingAt
                  media {
                    id siteUrl format status episodes season seasonYear
                    title { romaji english native }
                    description(asHtml: false)
                    coverImage { extraLarge large }
                    bannerImage
                  }
                }
              }
            }
        """
    }
}
