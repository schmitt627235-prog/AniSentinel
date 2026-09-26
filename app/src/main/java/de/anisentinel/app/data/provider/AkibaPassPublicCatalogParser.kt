package de.anisentinel.app.data.provider

import java.net.URI
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.jsoup.Jsoup

data class AkibaPassProduct(
    val id: String,
    val title: String,
    val url: String
)

data class AkibaPassEpisode(
    val id: String,
    val number: Int,
    val title: String,
    val description: String?,
    val duration: String?,
    val language: String?,
    val url: String
)

data class AkibaPassSeason(
    val product: AkibaPassProduct,
    val label: String,
    val seasonNumber: Int,
    val partNumber: Int?,
    val episodes: List<AkibaPassEpisode>,
    val purchasable: Boolean = false,
    val availableFrom: LocalDate? = null
)

/** Reads only publicly visible catalog metadata. A listed episode is not proof of playback availability. */
object AkibaPassPublicCatalogParser {
    private val seasonLabel = Regex("(?:Season|Staffel)\\s*(\\d+)(?:[.,](\\d+))?", RegexOption.IGNORE_CASE)
    private val episodeLabel = Regex("(?:^|\\W)S\\s*(\\d+)\\s*E\\s*0*(\\d+)(?:\\W|$)", RegexOption.IGNORE_CASE)

    fun products(html: String): List<AkibaPassProduct> {
        val document = Jsoup.parse(html, "https://www.akibapass.tv/products")
        return document.select("li.js-collection-item.item-type-package").mapNotNull { card ->
            val link = card.selectFirst("a.browse-item-link[href*=/products/]") ?: return@mapNotNull null
            val url = link.absUrl("href").substringBefore('?')
            val id = productId(url) ?: return@mapNotNull null
            val title = card.selectFirst(".browse-item-title strong")?.text()?.trim()
                ?: link.selectFirst("img[alt]")?.attr("alt")?.trim()
            title?.takeIf(String::isNotBlank)?.let { AkibaPassProduct(id, it, url) }
        }.distinctBy(AkibaPassProduct::id)
    }

    /** Exact alias and season match; do not silently substitute an OVA, movie or spin-off. */
    fun matchingSeasonProducts(
        products: List<AkibaPassProduct>, aliases: Set<String>, requestedSeason: Int? = null
    ): List<AkibaPassProduct> {
        val normalizedAliases = aliases.map(::normalizeTitle).filter(String::isNotBlank).toSet()
        if (normalizedAliases.isEmpty()) return emptyList()
        return products.filter { product ->
            val match = seasonLabel.find(product.title) ?: return@filter false
            val number = match.groupValues[1].toIntOrNull() ?: return@filter false
            if (requestedSeason != null && number != requestedSeason) return@filter false
            val base = product.title.substring(0, match.range.first)
                .trimEnd(' ', '-', ':')
                .replace(Regex("\\s*\\((?:DE|OmU|DE\\+OmU|OmU\\+DE)\\)$", RegexOption.IGNORE_CASE), "")
            normalizeTitle(base) in normalizedAliases
        }
    }

    fun season(html: String, productUrl: String): AkibaPassSeason? {
        val id = productId(productUrl) ?: return null
        val document = Jsoup.parse(html, productUrl)
        val title = document.selectFirst("h1.collection-title")?.text()?.trim().orEmpty()
        val labelMatch = seasonLabel.find(title) ?: return null
        val season = labelMatch.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val part = labelMatch.groupValues[2].toIntOrNull()
        val episodes = document.select(".episode-container li.js-collection-item.item-type-video")
            .mapNotNull { card ->
                val episodeId = card.attr("data-item-id").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null
                val link = card.selectFirst("a.browse-item-link[href*=/videos/]")
                    ?: return@mapNotNull null
                val url = link.absUrl("href").substringBefore('?')
                if (!validEpisodeUrl(url)) return@mapNotNull null
                val heading = card.selectFirst(".browse-item-title strong")?.text()?.trim().orEmpty()
                val episodeMatch = episodeLabel.find(heading) ?: return@mapNotNull null
                if (episodeMatch.groupValues[1].toIntOrNull() != season) return@mapNotNull null
                val number = episodeMatch.groupValues[2].toIntOrNull()?.takeIf { it > 0 }
                    ?: return@mapNotNull null
                val tooltip = card.selectFirst(".tooltip .transparent")?.text()?.trim().orEmpty()
                val description = card.select(".tooltip .transparent p")
                    .map { it.text().trim() }
                    .firstOrNull { it.isNotBlank() && !it.startsWith("Sprache:", true) &&
                        !it.startsWith("Untertitel:", true) && !it.startsWith("Laufzeit:", true) }
                val language = when {
                    tooltip.contains("Untertitel: Deutsch", true) || heading.contains("(OmU)", true) -> "GER_SUB"
                    tooltip.contains("Sprache: Deutsch", true) || heading.contains("(DE)", true) -> "GER_DUB"
                    else -> null
                }
                val titlePart = heading.substringAfter(episodeMatch.value, "").trim(' ', '-', ':')
                    .ifBlank { heading }
                AkibaPassEpisode(
                    episodeId, number, titlePart,
                    description,
                    card.selectFirst(".duration-container")?.text()?.trim()?.takeIf(String::isNotBlank),
                    language, url
                )
            }.distinctBy(AkibaPassEpisode::id)
        if (episodes.isEmpty()) return null
        val checkout = document.select("a[href*='/checkout/$id?']")
            .any { it.absUrl("href").startsWith("https://www.akibapass.tv/checkout/$id?") }
        val availableFrom = Regex("Ab\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s+verfügbar", RegexOption.IGNORE_CASE)
            .find(document.selectFirst(".collection-description")?.text().orEmpty())
            ?.groupValues?.getOrNull(1)
            ?.let { runCatching { LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.uuuu")) }.getOrNull() }
        return AkibaPassSeason(
            AkibaPassProduct(id, title, productUrl), labelMatch.value, season, part,
            episodes, checkout, availableFrom
        )
    }

    private fun productId(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme != "https" || uri.host != "www.akibapass.tv") return null
        uri.path.removePrefix("/products/").takeIf { uri.path.startsWith("/products/") &&
            it.matches(Regex("[a-z0-9-]+")) }
    }.getOrNull()

    private fun validEpisodeUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "www.akibapass.tv" &&
            uri.path.matches(Regex("/packages/[a-z0-9-]+/videos/[a-z0-9-]+"))
    }.getOrDefault(false)

    private fun normalizeTitle(value: String): String = java.text.Normalizer
        .normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
}
