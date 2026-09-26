package de.anisentinel.app.data.provider

import java.net.URI
import org.jsoup.Jsoup

enum class AppleTvCatalogCompleteness { COMPLETE, PARTIAL, UNAVAILABLE }

data class AppleTvPublicEpisode(
    val seasonNumber: Int,
    val number: Int,
    val id: String,
    val title: String,
    val description: String?,
    val duration: String?,
    val url: String
)

data class AppleTvPublicSeason(
    val number: Int,
    val id: String,
    val label: String,
    val advertisedEpisodeCount: Int
)

data class AppleTvPublicCatalog(
    val showId: String,
    val title: String,
    val showUrl: String,
    val episodes: List<AppleTvPublicEpisode>,
    val seasons: List<AppleTvPublicSeason>,
    val completeness: AppleTvCatalogCompleteness
)

/** Only episode cards rendered in Apple's public German show page are evidence. */
object AppleTvPublicCatalogParser {
    private val showIdPattern = Regex("umc\\.cmc\\.[a-z0-9]+", RegexOption.IGNORE_CASE)
    private val episodeNumberPattern = Regex("(?:FOLGE|EPISODE)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val seasonNumberPattern = Regex("(?:Staffel|Season)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val publicSeasonMetadataPattern = Regex(
        "\\{\"episodeCount\":(\\d+),\"id\":\"(umc\\.cmc\\.[a-z0-9]+)\",\"seasonNumber\":(\\d+),\"title\":\"([^\"]+)\"\\}"
    )

    fun isAppleShowUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == "tv.apple.com" &&
            uri.path.startsWith("/de/show/") && showIdPattern.containsMatchIn(uri.path)
    }.getOrDefault(false)

    fun parse(html: String, pageUrl: String, aliases: Set<String>): AppleTvPublicCatalog? {
        if (!isAppleShowUrl(pageUrl)) return null
        val document = Jsoup.parse(html, pageUrl)
        val title = document.selectFirst("h1")?.text()?.trim().orEmpty()
        if (title.isBlank() || !JustWatchTitleMatcher.isConservativeEquivalent(aliases, title)) return null
        val showId = showIdPattern.find(URI(pageUrl).path)?.value ?: return null
        // Apple embeds the selector's season IDs and counts in public page data.
        // Counts are not episode cards and must never be expanded into fake episodes.
        val seasons = publicSeasonMetadataPattern.findAll(html).mapNotNull { match ->
            val count = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val number = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            if (number <= 0 || count < 0) return@mapNotNull null
            AppleTvPublicSeason(number, match.groupValues[2], match.groupValues[4], count)
        }.distinctBy { it.id }.toList()
        val options = document.select("select[data-testid=accessory-button-select] option")
            .mapNotNull { seasonNumberPattern.find(it.text())?.groupValues?.get(1)?.toIntOrNull() }
        // The public page renders cards for only the selected season. Other option labels
        // are not proof that their episode lists have been delivered.
        val selectedSeason = document.selectFirst("select[data-testid=accessory-button-select] option[selected]")
            ?.text()?.let { seasonNumberPattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?: options.firstOrNull()
        val episodes = document.select("a[href*='/de/episode/']").mapNotNull { anchor ->
            val url = anchor.absUrl("href")
            val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.host != "tv.apple.com" ||
                !uri.path.startsWith("/de/episode/") ||
                uri.rawQuery.orEmpty().split('&').none { it == "showId=$showId" }) return@mapNotNull null
            val id = showIdPattern.find(uri.path)?.value ?: return@mapNotNull null
            val number = anchor.selectFirst(".tag")?.text()?.let {
                episodeNumberPattern.find(it)?.groupValues?.get(1)?.toIntOrNull()
            } ?: return@mapNotNull null
            val season = selectedSeason ?: return@mapNotNull null
            if (season <= 0 || number <= 0) return@mapNotNull null
            val episodeTitle = anchor.selectFirst(".title")?.text()?.trim().orEmpty()
            if (episodeTitle.isBlank()) return@mapNotNull null
            AppleTvPublicEpisode(
                season, number, id, episodeTitle,
                anchor.selectFirst(".description")?.text()?.trim()?.takeIf(String::isNotBlank),
                (anchor.selectFirst(".progress-and-attribution .metadata")
                    ?: anchor.select(".metadata").lastOrNull())
                    ?.text()?.trim()?.takeIf(String::isNotBlank),
                url
            )
        }.distinctBy { Triple(it.seasonNumber, it.number, it.id) }
        return AppleTvPublicCatalog(
            showId, title, pageUrl, episodes, seasons,
            if (episodes.isEmpty()) AppleTvCatalogCompleteness.UNAVAILABLE else AppleTvCatalogCompleteness.PARTIAL
        )
    }
}
