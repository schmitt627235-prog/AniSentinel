package de.anisentinel.app.data.provider

import java.net.URI
import java.net.URLEncoder
import org.json.JSONObject

/** Anonymous catalogue metadata exposed by Apple's public DE show page.
 * Never retain the page's short-lived request parameters or inspect playables.
 */
internal object AppleTvPublicEpisodeMetadata {
    private const val API_HOST = "uts-api.itunes.apple.com"
    private val requestKeys = listOf("utscf", "utsk", "caller", "sf", "v", "pfm", "locale")

    fun pageParameters(html: String): Map<String, String>? {
        val start = html.indexOf("\"requiredParamsMap\"")
        if (start < 0) return null
        val candidate = html.substring(start, minOf(html.length, start + 2_000))
        val raw = Regex("\"Default\"\\s*:\\s*(\\{[^{}]*\\})")
            .find(candidate)?.groupValues?.get(1) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val values = requestKeys.associateWith { json.optString(it).trim() }
        if (values.values.any(String::isBlank) || values["locale"] != "de-DE" ||
            values["sf"]?.all(Char::isDigit) != true || values["caller"] != "web") return null
        return values
    }

    fun pageUrl(showId: String, params: Map<String, String>, offset: Int, count: Int): String {
        require(Regex("umc\\.cmc\\.[a-z0-9]+", RegexOption.IGNORE_CASE).matches(showId))
        require(offset >= 0 && count in 1..50)
        val query = (params + mapOf(
            "nextToken" to "$offset:$count",
            "includeSeasonSummary" to "false",
            "selectedSeasonEpisodesOnly" to "false"
        )).entries.joinToString("&") { (key, value) ->
            "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}" 
        }
        return "https://$API_HOST/uts/v3/shows/$showId/episodes?$query"
    }

    fun isEpisodeResponseUrl(value: String, showId: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == API_HOST &&
            uri.path == "/uts/v3/shows/$showId/episodes"
    }.getOrDefault(false)

    data class Page(val total: Int, val episodes: List<AppleTvPublicEpisode>)

    /** Reads only `data.episodes`; playback/offer fields in the response are ignored. */
    fun parsePage(body: String, showId: String, knownSeasons: Map<Int, String>): Page? = runCatching {
        val data = JSONObject(body).getJSONObject("data")
        val total = data.getInt("totalEpisodeCount")
        if (total < 0 || total > 10_000) return null
        val values = data.getJSONArray("episodes")
        val episodes = (0 until values.length()).map { index ->
            val item = values.getJSONObject(index)
            val seasonNumber = item.getInt("seasonNumber")
            val number = item.getInt("episodeNumber")
            val seasonId = item.getString("seasonId")
            val id = item.getString("id")
            val title = item.getString("title").trim()
            val url = item.getString("url")
            val uri = URI(url)
            require(seasonNumber > 0 && number > 0 && title.isNotBlank())
            require(item.getString("showId") == showId)
            require(Regex("umc\\.cmc\\.[a-z0-9]+", RegexOption.IGNORE_CASE).matches(id))
            require(uri.scheme == "https" && uri.host == "tv.apple.com" &&
                uri.path.startsWith("/de/episode/") && uri.path.endsWith("/$id") &&
                uri.rawQuery.orEmpty().split('&').any { it == "showId=$showId" })
            require(knownSeasons[seasonNumber] == seasonId)
            val durationSeconds = item.optInt("duration", 0)
            AppleTvPublicEpisode(
                seasonNumber, number, id, title,
                item.optString("description").trim().takeIf(String::isNotBlank),
                durationSeconds.takeIf { it > 0 }?.let { "${(it + 30) / 60} Min." },
                url
            )
        }
        Page(total, episodes)
    }.getOrNull()
}
