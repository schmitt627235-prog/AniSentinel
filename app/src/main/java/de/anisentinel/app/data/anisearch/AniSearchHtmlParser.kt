package de.anisentinel.app.data.anisearch

import java.net.URI
import org.jsoup.Jsoup

data class AniSearchImport(
    val anisearchId: String,
    val titleGerman: String,
    val descriptionGerman: String,
    val sourceUrl: String,
    val coverUrl: String?,
    val providers: List<AniSearchProviderHint>,
    val totalEpisodes: Int? = null,
    val releaseYear: Int? = null,
    val mediaType: String? = null,
    val status: String? = null,
    val synonyms: Set<String> = emptySet()
)

data class AniSearchSearchHit(val anisearchId: String, val title: String, val sourceUrl: String)

data class AniSearchProviderHint(
    val normalizedProvider: String,
    val providerUrl: String
)

sealed interface AniSearchParseResult {
    data class Success(val value: AniSearchImport) : AniSearchParseResult
    data class InvalidSource(val reason: String) : AniSearchParseResult
    data class UnsupportedLayout(val reason: String) : AniSearchParseResult
}

/** Parses user-supplied HTML only. Network transport deliberately lives outside this class. */
object AniSearchHtmlParser {
    private val idPattern = Regex("/anime/(\\d+)(?:[/?#,-]|$)")

    fun parse(html: String, sourceUrl: String): AniSearchParseResult {
        val uri = runCatching { URI(sourceUrl) }.getOrNull()
            ?: return AniSearchParseResult.InvalidSource("INVALID_URL")
        val host = uri.host?.lowercase()
        if (uri.scheme != "https" || (host != "anisearch.de" && host != "www.anisearch.de")) {
            return AniSearchParseResult.InvalidSource("NOT_ANISEARCH_HTTPS")
        }
        val id = idPattern.find(uri.path.orEmpty())?.groupValues?.get(1)
            ?: return AniSearchParseResult.InvalidSource("NOT_AN_ANIME_DETAIL_URL")
        val document = Jsoup.parse(html, sourceUrl)
        val jsonLd = document.select("script[type=application/ld+json]")
            .map { it.data() }.firstOrNull { it.contains("\"@type\"") }.orEmpty()
        val title = listOf(
            jsonString(jsonLd, "name"),
            document.selectFirst(".title [lang=de], .title strong")?.text(),
            document.selectFirst("h1")?.text(),
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.title().substringBefore(" | ").substringBefore(" - ")
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
            ?: return AniSearchParseResult.UnsupportedLayout("GERMAN_TITLE_NOT_FOUND")
        val description = listOf(
            document.selectFirst("#desc-de")?.text(),
            document.selectFirst("[itemprop=description][lang=de]")?.text(),
            document.selectFirst("[itemprop=description]")?.text(),
            jsonString(jsonLd, "description"),
            document.selectFirst(".description, .textblock, #description")?.text(),
            document.selectFirst("meta[name=description]")?.attr("content")
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
            ?: return AniSearchParseResult.UnsupportedLayout("GERMAN_DESCRIPTION_NOT_FOUND")
        val cover = listOf(
            document.selectFirst("meta[property=og:image]")?.absUrl("content"),
            document.selectFirst("img#details-cover, img[itemprop=image]")?.absUrl("src"),
            jsonString(jsonLd, "image")
        ).firstOrNull { !it.isNullOrBlank() }
        val providers = document.select("a[href]").mapNotNull { link ->
            val url = link.absUrl("href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val provider = normalizeProvider(link.text(), url) ?: return@mapNotNull null
            AniSearchProviderHint(provider, url)
        }.distinctBy { it.normalizedProvider to it.providerUrl }
        val startDate = jsonString(jsonLd, "startDate")
        val synonyms = document.select(".title strong, .title .grey, .synonyms, #text-synonyms")
            .map { it.text().trim() }.filter(String::isNotBlank).toSet() - title
        return AniSearchParseResult.Success(AniSearchImport(
            anisearchId = id,
            titleGerman = title,
            descriptionGerman = description,
            sourceUrl = sourceUrl,
            coverUrl = cover,
            providers = providers,
            totalEpisodes = jsonInt(jsonLd, "numberOfEpisodes"),
            releaseYear = startDate?.take(4)?.toIntOrNull(),
            mediaType = document.selectFirst(".infoblock .type, ul.xlist .type")?.text()?.trim(),
            status = document.selectFirst(".status")?.text()?.trim(),
            synonyms = synonyms
        ))
    }

    fun parseSearchResults(html: String, pageUrl: String): List<AniSearchSearchHit> {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return emptyList()
        if (uri.scheme != "https" || uri.host?.lowercase() !in setOf("anisearch.de", "www.anisearch.de")) {
            return emptyList()
        }
        return Jsoup.parse(html, pageUrl).select("li.btype0 a[href*=/anime/], th.showpop a[href*=/anime/], a[href*=/anime/][lang=de]")
            .mapNotNull { anchor ->
                val url = anchor.absUrl("href")
                val id = idPattern.find(URI(url).path.orEmpty())?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val title = anchor.selectFirst("span.title")?.text()?.trim()
                    ?: anchor.text().trim()
                title.takeIf(String::isNotBlank)?.let { AniSearchSearchHit(id, it, url) }
            }.distinctBy(AniSearchSearchHit::anisearchId)
    }

    private fun jsonString(json: String, key: String): String? = Regex(
        "\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\""
    ).find(json)?.groupValues?.get(1)?.replace("\\/", "/")
        ?.replace("\\n", " ")?.replace("\\\"", "\"")

    private fun jsonInt(json: String, key: String): Int? = Regex(
        "\\\"${Regex.escape(key)}\\\"\\s*:\\s*(?:\\\")?(\\d+)(?:\\\")?"
    ).find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun normalizeProvider(label: String, url: String): String? {
        val value = "$label $url".lowercase()
        return when {
            "crunchyroll" in value -> "CRUNCHYROLL"
            "netflix" in value -> "NETFLIX"
            "amazon" in value || "primevideo" in value || "prime-video" in value -> "AMAZON_PRIME_VIDEO"
            "aniverse" in value -> "ANIVERSE"
            else -> null
        }
    }
}
