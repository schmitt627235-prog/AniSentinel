package de.anisentinel.app.data.provider

import de.anisentinel.app.data.local.*
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

data class HistoricalProviderEpisode(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val releaseDate: LocalDate,
    val releaseLanguages: Set<String>,
    val providerEpisodeId: String?,
    val providerEpisodeUrl: String
)

sealed interface HistoricalImportResult {
    data class Success(val parsed: Int, val inserted: Int, val enriched: Int) : HistoricalImportResult
    data class Failed(val code: String) : HistoricalImportResult
}

object CrunchyrollPublicHistoryParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun parse(html: String, seriesUrl: String): List<HistoricalProviderEpisode> {
        val document = Jsoup.parse(html, seriesUrl)
        var season: Int? = null
        val rows = mutableListOf<HistoricalProviderEpisode>()
        document.getAllElements().forEach { element ->
            if (element.tagName() in setOf("h2", "h3", "h4", "button")) {
                Regex("(?:Staffel|Season)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(element.text())?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { season = it }
            }
            if (element.tagName() != "a" || !element.attr("href").contains("/watch/")) return@forEach
            val episode = Regex("(?:Episode|Folge|E)\\s*(\\d+)(?:\\D|$)", RegexOption.IGNORE_CASE)
                .find(element.text())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@forEach
            val context = nearestEpisodeContext(element) ?: return@forEach
            val date = Regex("(\\d{2}\\.\\d{2}\\.\\d{4})").find(context.text())
                ?.groupValues?.getOrNull(1)?.let(::parseDate) ?: return@forEach
            val languages = buildSet {
                if (context.text().contains("Untertitel", true)) add("GER_SUB")
                if (context.text().contains("Synchro", true)) add("GER_DUB")
            }
            if (languages.isEmpty()) return@forEach
            val url = element.absUrl("href").takeIf(String::isNotBlank) ?: return@forEach
            val watchId = Regex("/watch/([A-Z0-9]+)", RegexOption.IGNORE_CASE)
                .find(url)?.groupValues?.getOrNull(1)
            val title = element.text().substringAfter('-', "").trim().takeIf(String::isNotBlank)
            rows += HistoricalProviderEpisode(season ?: 1, episode, title, date, languages, watchId, url)
        }
        return rows.distinctBy {
            listOf(it.seasonNumber, it.episodeNumber, it.providerEpisodeId, it.releaseDate, it.releaseLanguages.sorted())
        }
    }

    private fun nearestEpisodeContext(link: Element): Element? {
        var current: Element? = link
        repeat(5) {
            val candidate = current ?: return null
            if (Regex("\\d{2}\\.\\d{2}\\.\\d{4}").containsMatchIn(candidate.text()) &&
                (candidate.text().contains("Untertitel", true) || candidate.text().contains("Synchro", true))) return candidate
            current = candidate.parent()
        }
        return null
    }

    private fun parseDate(value: String): LocalDate? = try { LocalDate.parse(value, dateFormatter) }
    catch (_: DateTimeParseException) { null }
}

class CrunchyrollHistoricalReleaseImporter(
    private val dao: AniSentinelDao,
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val catalogClient: CrunchyrollAnonymousCatalogClient = CrunchyrollAnonymousCatalogClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    /** Resolve a public Crunchyroll series generically; no user supplied or title-specific URL required. */
    suspend fun importByTitle(
        animeId: String, title: String,
        fromEpochSeconds: Long? = null, toEpochSecondsExclusive: Long? = null
    ): HistoricalImportResult {
        val seriesUrl = resolveSeriesByExactPublicSearch(title)
            ?: return HistoricalImportResult.Failed("CRUNCHYROLL_PUBLIC_EXACT_TITLE_NOT_IDENTIFIED")
        return import(animeId, seriesUrl, fromEpochSeconds, toEpochSecondsExclusive)
    }

    suspend fun importFromProviderUrl(
        animeId: String, title: String, providerUrl: String,
        fromEpochSeconds: Long? = null, toEpochSecondsExclusive: Long? = null
    ): HistoricalImportResult {
        val host = runCatching { URI(providerUrl).host?.lowercase() }.getOrNull()
        if (host != "crunchyroll.com" && host?.endsWith(".crunchyroll.com") != true)
            return HistoricalImportResult.Failed("CRUNCHYROLL_PUBLIC_URL_INVALID")
        val seriesId = runCatching { catalogClient.resolveSeries(providerUrl, title) }.getOrNull()
            ?: return HistoricalImportResult.Failed("CRUNCHYROLL_PUBLIC_SERIES_URL_NOT_IDENTIFIED")
        return importResolved(animeId, seriesId, fromEpochSeconds, toEpochSecondsExclusive)
    }

    private suspend fun resolveSeriesByExactPublicSearch(title: String): String? {
        val query = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
        val response = runCatching { transport.get("https://www.crunchyroll.com/de/search?q=$query", emptyMap()) }.getOrNull()
            ?: return null
        if (response.status !in 200..299) return null
        val wanted = normalizedTitle(title)
        val document = Jsoup.parse(response.body, response.finalUrl)
        return document.select("a[href*=/series/]").firstNotNullOfOrNull { link ->
            val candidate = link.attr("aria-label").ifBlank { link.text() }
            link.absUrl("href").takeIf {
                normalizedTitle(candidate) == wanted && CrunchyrollPublicWebAdapter.crunchyrollSeriesId(it) != null
            }
        } ?: CrunchyrollPublicWebAdapter.crunchyrollSeriesId(response.body)
            ?.let(CrunchyrollPublicWebAdapter::canonicalSeriesUrl)
    }

    private fun normalizedTitle(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    suspend fun import(
        animeId: String, seriesUrl: String,
        fromEpochSeconds: Long? = null, toEpochSecondsExclusive: Long? = null
    ): HistoricalImportResult {
        val host = runCatching { URI(seriesUrl).host?.lowercase() }.getOrNull()
        if (host != "crunchyroll.com" && host?.endsWith(".crunchyroll.com") != true)
            return HistoricalImportResult.Failed("CRUNCHYROLL_PUBLIC_SERIES_URL_INVALID")
        val seriesId = CrunchyrollPublicWebAdapter.crunchyrollSeriesId(seriesUrl)
            ?: return HistoricalImportResult.Failed("CRUNCHYROLL_PUBLIC_SERIES_ID_MISSING")
        return importResolved(animeId, seriesId, fromEpochSeconds, toEpochSecondsExclusive)
    }

    private suspend fun importResolved(
        animeId: String, seriesId: String,
        fromEpochSeconds: Long?, toEpochSecondsExclusive: Long?
    ): HistoricalImportResult {
        val catalog = runCatching { catalogClient.loadSeries(seriesId) }
            .getOrElse { return HistoricalImportResult.Failed(it.message ?: "CRUNCHYROLL_ANONYMOUS_CATALOG_FAILED") }
        val now = clock.instant()
        val historical = catalog.episodes.filter { episode ->
            val epoch = episode.availableAt?.epochSecond
            episode.availableAt?.isBefore(now) == true && episode.releaseLanguages.isNotEmpty() &&
                (fromEpochSeconds == null || (epoch != null && epoch >= fromEpochSeconds)) &&
                (toEpochSecondsExclusive == null || (epoch != null && epoch < toEpochSecondsExclusive))
        }
        if (historical.isEmpty()) return HistoricalImportResult.Failed("CRUNCHYROLL_CATALOG_NO_PAST_LANGUAGE_DATED_EPISODES")
        val rows = mutableListOf<EpisodeReleaseEntity>()
        val references = mutableListOf<ReleaseSourceReferenceEntity>()
        var inserted = 0
        var enriched = 0
        for (episode in historical) for (language in episode.releaseLanguages) {
            val dateEpoch = requireNotNull(episode.availableAt).epochSecond
            val existing = dao.semanticProviderRelease(animeId, episode.seasonNumber, episode.episodeNumber, language, "Crunchyroll")
            val conflict = HistoricalSourcePolicy.conflicts(existing?.historicalReleasedAt ?: existing?.expectedAt, dateEpoch)
            val releaseId = existing?.sourceReleaseId
                ?: "crunchyroll-history:$animeId:s${episode.seasonNumber}:e${episode.episodeNumber}:${language.lowercase()}"
            if (conflict && (existing?.historicalSourcePriority ?: 0) >= HistoricalSourcePolicy.PROVIDER_EPISODE) {
                rows += requireNotNull(existing).copy(historicalConflict = true)
                references += ReleaseSourceReferenceEntity(
                    "cr-history-ref:$releaseId", releaseId, "CRUNCHYROLL_ANONYMOUS_CATALOG_HISTORICAL",
                    episode.episodeId, catalog.seriesUrl, now.epochSecond
                )
                enriched++
                continue
            }
            val row = existing?.copy(
                episodeTitle = existing.episodeTitle ?: episode.title,
                providerUrl = episode.episodeUrl,
                releaseStatus = "AVAILABLE",
                isHistoricalImport = true,
                historicalReleasedAt = dateEpoch,
                releaseTimePrecision = "EXACT",
                historicalSourcePriority = HistoricalSourcePolicy.PROVIDER_EPISODE,
                historicalConflict = conflict
            ) ?: EpisodeReleaseEntity(
                releaseId, animeId, episode.episodeNumber, episode.title, dateEpoch, "Crunchyroll",
                "CRUNCHYROLL_ANONYMOUS_CATALOG_HISTORICAL", catalog.seriesUrl, episode.episodeUrl,
                now.epochSecond, episode.seasonNumber, releaseStatus = "AVAILABLE",
                releaseLanguage = language, isHistoricalImport = true,
                historicalReleasedAt = dateEpoch, releaseTimePrecision = "EXACT",
                historicalSourcePriority = HistoricalSourcePolicy.PROVIDER_EPISODE,
                historicalConflict = conflict
            )
            rows += row
            references += ReleaseSourceReferenceEntity(
                "cr-history-ref:$releaseId", releaseId, "CRUNCHYROLL_ANONYMOUS_CATALOG_HISTORICAL",
                episode.episodeId, catalog.seriesUrl, now.epochSecond
            )
            if (existing == null) inserted++ else enriched++
        }
        dao.importHistoricalProviderReleases(rows, references)
        dao.upsertProviderMetadataIdentity(ProviderMetadataIdentityEntity(
            "provider-identity:$animeId:CRUNCHYROLL_STRUCTURED_METADATA_PROBE:DE", animeId,
            "CRUNCHYROLL_STRUCTURED_METADATA_PROBE", "DE", seriesId, null, null, null, null,
            catalog.seriesUrl, now.epochSecond
        ))
        return HistoricalImportResult.Success(catalog.episodes.size, inserted, enriched)
    }
}
