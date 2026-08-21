package de.anisentinel.app.data.provider

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

data class JustWatchPublicMetadata(
    val description: String?,
    val genres: Set<String>,
    val studios: Set<String>
)

/** Parses only metadata explicitly exposed in a JustWatch public title page. */
object JustWatchPublicMetadataParser {
    fun parse(html: String): JustWatchPublicMetadata {
        val document = Jsoup.parse(html)
        val candidates = Regex("<script\\b([^>]*)>(.*?)</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(html)
            .filter { match -> match.groupValues[1].contains("application/ld+json", ignoreCase = true) }
            .flatMap { match -> jsonObjects(match.groupValues[2].trim()).asSequence() }
            .toList()
        val title = candidates.firstOrNull { row ->
            row.optString("@type") in setOf("TVSeries", "Movie", "CreativeWork", "TVSeason")
        } ?: candidates.firstOrNull()
        val description = listOfNotNull(
            MetadataTextNormalizer.decode(title?.optString("description")),
            MetadataTextNormalizer.decode(document.selectFirst("meta[name=description]")?.attr("content"))
        ).firstOrNull(::isEditorialSynopsis)
        val genres = MetadataTextNormalizer.normalizeGenres(title?.values("genre").orEmpty()).toSet()
        val studios = buildSet {
            addAll(title?.names("productionCompany").orEmpty())
            addAll(title?.names("productionCompanies").orEmpty())
            addAll(title?.names("productionStudio").orEmpty())
        }
        return JustWatchPublicMetadata(description, genres, studios.mapNotNull(MetadataTextNormalizer::decode).toSet())
    }

    /** Rejects JustWatch SEO/availability copy; it is not an editorial plot synopsis. */
    fun isEditorialSynopsis(value: String): Boolean {
        val normalized = value.lowercase()
        return listOf(
            "im stream online", "auf netflix", "prime video", "disney+", "kostenlos option",
            "streaming-anbieter", "jetzt streamen", "wo kann man", "online anschauen"
        ).none(normalized::contains)
    }

    private fun jsonObjects(raw: String): List<JSONObject> = runCatching {
        when (val value = org.json.JSONTokener(raw).nextValue()) {
            is JSONObject -> listOf(value)
            is JSONArray -> (0 until value.length()).mapNotNull(value::optJSONObject)
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    private fun JSONObject.values(key: String): Set<String> = when (val value = opt(key)) {
        is String -> setOf(value.trim()).filter(String::isNotBlank).toSet()
        is JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).trim().takeIf(String::isNotBlank) }.toSet()
        else -> emptySet()
    }

    private fun JSONObject.names(key: String): Set<String> {
        fun name(value: Any?): String? = when (value) {
            is JSONObject -> value.optString("name").trim().takeIf(String::isNotBlank)
            is String -> value.trim().takeIf(String::isNotBlank)
            else -> null
        }
        return when (val value = opt(key)) {
            is JSONArray -> (0 until value.length()).mapNotNull { name(value.opt(it)) }.toSet()
            else -> setOfNotNull(name(value))
        }
    }
}
