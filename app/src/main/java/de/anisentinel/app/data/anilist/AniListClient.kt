package de.anisentinel.app.data.anilist

import org.json.JSONObject

class AniListClient(
    private val httpClient: AniListGraphQlHttpClient = AniListGraphQlHttpClient()
) {
    suspend fun trendingAnime(limit: Int = 12): AniListResult {
        val body = JSONObject()
            .put("query", QUERY)
            .put("variables", JSONObject().put("perPage", limit.coerceIn(1, 25)))
            .toString()
        return when (val response = httpClient.execute("TRENDING", body)) {
            is GraphQlHttpResult.Success -> parse(response.body)
            is GraphQlHttpResult.HttpFailure -> AniListResult.HttpError(
                response.statusCode, response.retryAfterSeconds,
                requireNotNull(httpClient.classify(response))
            )
            is GraphQlHttpResult.NetworkFailure -> AniListResult.NetworkError(
                "${response.type}:${response.message.orEmpty()}"
            )
        }
    }

    internal fun parse(json: String): AniListResult = try {
        val root = JSONObject(json)
        if (root.has("errors")) {
            AniListResult.InvalidResponse("graphql_errors")
        } else {
            val media = root.getJSONObject("data")
                .getJSONObject("Page")
                .getJSONArray("media")
            AniListResult.Success(
                buildList {
                    for (index in 0 until media.length()) {
                        val item = media.getJSONObject(index)
                        val title = item.optJSONObject("title")
                        val cover = item.optJSONObject("coverImage")
                        val airing = item.optJSONObject("nextAiringEpisode")
                        add(AniListMediaDto(
                            id = item.getInt("id"),
                            titleRomaji = title?.nullableString("romaji"),
                            titleEnglish = title?.nullableString("english"),
                            titleNative = title?.nullableString("native"),
                            description = item.nullableString("description"),
                            coverUrl = cover?.nullableString("extraLarge")
                                ?: cover?.nullableString("large"),
                            bannerUrl = item.nullableString("bannerImage"),
                            season = item.nullableString("season"),
                            seasonYear = item.nullableInt("seasonYear"),
                            episodes = item.nullableInt("episodes"),
                            nextEpisode = airing?.nullableInt("episode"),
                            nextAiringAt = airing?.nullableLong("airingAt"),
                            updatedAt = item.optLong("updatedAt", 0L)
                        ))
                    }
                }
            )
        }
    } catch (error: Exception) {
        AniListResult.InvalidResponse(error.message ?: "invalid_json")
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)

    private fun JSONObject.nullableInt(name: String): Int? =
        if (isNull(name) || !has(name)) null else optInt(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (isNull(name) || !has(name)) null else optLong(name)

    companion object {
        private const val QUERY = """
            query TrendingAnime(${'$'}perPage: Int!) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media(type: ANIME, isAdult: false, sort: TRENDING_DESC, status_in: [RELEASING, NOT_YET_RELEASED]) {
                  id
                  title { romaji english native }
                  description(asHtml: false)
                  coverImage { extraLarge large }
                  bannerImage
                  season
                  seasonYear
                  episodes
                  updatedAt
                  nextAiringEpisode { episode airingAt }
                }
              }
            }
        """
    }
}
