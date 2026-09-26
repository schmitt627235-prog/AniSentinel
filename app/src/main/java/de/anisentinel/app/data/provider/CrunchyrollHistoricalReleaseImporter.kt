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
        fromEpochSeconds: Long? = null, toEpochSecondsExclusive: Long? = null,
        titleAliases: Set<String> = emptySet()
    ): HistoricalImportResult {
        val seriesIds = resolveCatalogIdentities(title, titleAliases)
        if (seriesIds.isEmpty()) return HistoricalImportResult.Failed("CRUNCHYROLL_EXACT_TITLE_NOT_IDENTIFIED")
        return importResolvedCatalogs(animeId, seriesIds, fromEpochSeconds, toEpochSecondsExclusive)
    }

    suspend fun importFromProviderUrl(
        animeId: String, title: String, providerUrl: String,
        fromEpochSeconds: Long? = null, toEpochSecondsExclusive: Long? = null,
        titleAliases: Set<String> = emptySet()
    ): HistoricalImportResult {
        val host = runCatching { URI(providerUrl).host?.lowercase() }.getOrNull()
        if (host != "crunchyroll.com" && host?.endsWith(".crunchyroll.com") != true)
            return HistoricalImportResult.Failed("CRUNCHYROLL_PUBLIC_URL_INVALID")
        // JustWatch's title match establishes which Crunchyroll edition belongs to this
        // work. This matters when Crunchyroll exposes a remaster and the currently airing
        // catalogue as separate series. Resolve its concrete series/watch target first;
        // title matching remains the safe fallback when the offer has no usable identity.
        val directSeriesId = runCatching {
            catalogClient.resolveSeries(reference = providerUrl, title = title)
        }.getOrNull()
        val titleSeriesIds = resolveCatalogIdentities(title, titleAliases)
        // JustWatch already confirmed this provider offer for the matched work. Preserve
        // its concrete catalogue and add any exact-title catalogues (for example separate
        // remaster/current catalogues) instead of discarding the direct identity.
        val seriesIds = (listOfNotNull(directSeriesId) + titleSeriesIds).distinct()
        if (seriesIds.isEmpty()) return HistoricalImportResult.Failed("CRUNCHYROLL_EXACT_TITLE_NOT_IDENTIFIED")
        return importResolvedCatalogs(animeId, seriesIds, fromEpochSeconds, toEpochSecondsExclusive)
    }

    private suspend fun resolveCatalogIdentities(title: String, aliases: Set<String>): List<String> {
        // The AniWorld title identifies the requested work. A broader JustWatch alias can
        // legitimately point at a related main series (for example a spin-off without its
        // subtitle), so it must never override or invalidate an exact provider match for
        // the primary title.
        val primary = runCatching { catalogClient.resolveSeriesAll(title) }.getOrDefault(emptyList())
        val aliasMatches = mutableListOf<String>()
        for (alias in aliases.filter(String::isNotBlank).distinct()) {
            if (normalizedTitle(alias) == normalizedTitle(title)) continue
            // A shortened alias can name the parent series instead of the requested
            // sequel/spin-off. Keep translated alternatives, but reject aliases that
            // merely remove distinctive words from the primary title.
            if (CrunchyrollSeriesIdentityPolicy.isBroaderAlias(title, alias)) continue
            runCatching { catalogClient.resolveSeriesAll(alias) }
                .getOrDefault(emptyList()).let(aliasMatches::addAll)
        }
        return (primary + aliasMatches).distinct()
    }

    /**
     * Official public watch/series pages are a metadata-only fallback when the anonymous
     * structured object lookup is temporarily unavailable. No login, playback or DRM data.
     */
    private suspend fun resolveSeriesFromPublicPage(providerUrl: String, title: String): String? {
        val response = runCatching { transport.get(providerUrl, emptyMap()) }.getOrNull()
        if (response != null && response.status in 200..299) {
            CrunchyrollPublicWebAdapter.crunchyrollSeriesId(response.finalUrl)?.let { return it }
            embeddedSeriesId(response.body)?.let { return it }
            val wanted = normalizedTitle(title)
            Jsoup.parse(response.body, response.finalUrl).select("a[href*=/series/]")
                .firstOrNull { link ->
                    val candidate = link.attr("aria-label").ifBlank { link.text() }
                    normalizedTitle(candidate) == wanted
                }?.absUrl("href")?.let(CrunchyrollPublicWebAdapter::crunchyrollSeriesId)
                ?.let { return it }
        }
        return resolveSeriesByExactPublicSearch(title)
            ?.let(CrunchyrollPublicWebAdapter::crunchyrollSeriesId)
    }

    private fun embeddedSeriesId(body: String): String? =
        Regex("[\\\"'](?:series_id|seriesId)[\\\"']\\s*[:=]\\s*[\\\"'](G[A-Z0-9]+)[\\\"']", RegexOption.IGNORE_CASE)
            .find(body.replace("\\/", "/"))?.groupValues?.getOrNull(1)?.uppercase()

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
        }
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
        return importResolvedCatalogs(animeId, listOf(seriesId), fromEpochSeconds, toEpochSecondsExclusive)
    }

    private suspend fun importResolvedCatalogs(
        animeId: String,
        seriesIds: List<String>,
        fromEpochSeconds: Long?,
        toEpochSecondsExclusive: Long?
    ): HistoricalImportResult {
        val prepared = mutableListOf<PreparedCatalog>()
        val assignedSeriesIds = ConfirmedCrunchyrollCatalogPolicy.forAnime(animeId, seriesIds)
        for (seriesId in assignedSeriesIds) {
            when (val result = prepareCatalog(animeId, seriesId, fromEpochSeconds, toEpochSecondsExclusive)) {
                is PreparedResult.Failed -> return HistoricalImportResult.Failed(result.code)
                is PreparedResult.Success -> prepared += result.catalog
            }
        }
        dao.replaceHistoricalProviderCatalogs(
            animeId = animeId,
            provider = "Crunchyroll",
            releases = prepared.flatMap { it.releases },
            references = prepared.flatMap { it.references },
            seasons = prepared.flatMap { it.seasons }.distinctBy { it.canonicalSeasonNumber },
            mappings = prepared.flatMap { it.mappings },
            identities = prepared.map { it.identity }
        )
        return HistoricalImportResult.Success(
            prepared.sumOf { it.parsed }, prepared.sumOf { it.releases.size }, 0
        )
    }

    private sealed interface PreparedResult {
        data class Success(val catalog: PreparedCatalog) : PreparedResult
        data class Failed(val code: String) : PreparedResult
    }

    private data class PreparedCatalog(
        val parsed: Int,
        val releases: List<EpisodeReleaseEntity>,
        val references: List<ReleaseSourceReferenceEntity>,
        val seasons: List<AnimeSeasonEntity>,
        val mappings: List<ProviderSeasonMappingEntity>,
        val identity: ProviderMetadataIdentityEntity
    )

    private suspend fun prepareCatalog(
        animeId: String, seriesId: String,
        fromEpochSeconds: Long?, toEpochSecondsExclusive: Long?
    ): PreparedResult {
        val catalog = runCatching { catalogClient.loadSeries(seriesId) }
            .getOrElse { return PreparedResult.Failed(it.message ?: "CRUNCHYROLL_ANONYMOUS_CATALOG_FAILED") }
        val now = clock.instant()
        val historical = catalog.episodes.filter { episode ->
            val epoch = episode.availableAt?.epochSecond
            episode.availableAt?.isBefore(now) == true && episode.releaseLanguages.isNotEmpty() &&
                (fromEpochSeconds == null || (epoch != null && epoch >= fromEpochSeconds)) &&
                (toEpochSecondsExclusive == null || (epoch != null && epoch < toEpochSecondsExclusive))
        }
        if (historical.isEmpty()) return PreparedResult.Failed("CRUNCHYROLL_CATALOG_NO_PAST_LANGUAGE_DATED_EPISODES")
        val rows = mutableListOf<EpisodeReleaseEntity>()
        val references = mutableListOf<ReleaseSourceReferenceEntity>()
        for (episode in historical) for (language in episode.releaseLanguages) {
            val dateEpoch = requireNotNull(episode.availableAt).epochSecond
            val canonicalSeason = ConfirmedCrunchyrollCatalogPolicy.canonicalSeasonNumber(
                animeId, seriesId, episode.seasonNumber
            )
            val releaseId = "crunchyroll-history:$animeId:$seriesId:s${episode.seasonNumber}:e${episode.episodeNumber}:${language.lowercase()}"
            val row = EpisodeReleaseEntity(
                releaseId, animeId, episode.episodeNumber, episode.title, dateEpoch, "Crunchyroll",
                "CRUNCHYROLL_ANONYMOUS_CATALOG_HISTORICAL", catalog.seriesUrl, episode.episodeUrl,
                now.epochSecond, canonicalSeason, releaseStatus = "AVAILABLE",
                releaseLanguage = language, isHistoricalImport = true,
                historicalReleasedAt = dateEpoch, releaseTimePrecision = "EXACT",
                historicalSourcePriority = HistoricalSourcePolicy.PROVIDER_EPISODE,
                historicalConflict = false
            )
            rows += row
            references += ReleaseSourceReferenceEntity(
                "cr-history-ref:$releaseId", releaseId, "CRUNCHYROLL_ANONYMOUS_CATALOG_HISTORICAL",
                episode.episodeId, catalog.seriesUrl, now.epochSecond
            )
        }
        val confirmedSeasons = historical.map { it.seasonNumber }.filter { it > 0 }.distinct()
        val seasonRows = confirmedSeasons.map { providerSeason ->
                AnimeSeasonEntity(animeId, ConfirmedCrunchyrollCatalogPolicy.canonicalSeasonNumber(
                    animeId, seriesId, providerSeason
                ), "CRUNCHYROLL_ANONYMOUS_CATALOG", now.epochSecond)
            }
        val mappingRows = confirmedSeasons.map { seasonNumber ->
                val season = historical.first { it.seasonNumber == seasonNumber }
                ProviderSeasonMappingEntity(
                    animeId = animeId,
                    canonicalSeasonNumber = ConfirmedCrunchyrollCatalogPolicy.canonicalSeasonNumber(
                        animeId, seriesId, seasonNumber
                    ),
                    provider = "Crunchyroll",
                    providerSeasonNumber = seasonNumber,
                    providerSeriesId = seriesId,
                    providerSeasonId = season.seasonId,
                    providerSeriesUrl = catalog.seriesUrl,
                    region = "DE",
                    available = true,
                    lastConfirmedAt = now.epochSecond,
                    providerSeasonLabel = ConfirmedCrunchyrollCatalogPolicy.seasonLabel(
                        animeId, seriesId, seasonNumber, season.seasonTitle
                    ),
                    providerCatalogId = seriesId
                )
            }
        val identity = ProviderMetadataIdentityEntity(
            "provider-identity:$animeId:CRUNCHYROLL_STRUCTURED_METADATA_PROBE:DE:$seriesId", animeId,
            "CRUNCHYROLL_STRUCTURED_METADATA_PROBE", "DE", seriesId, null, null, null, null,
            catalog.seriesUrl, now.epochSecond
        )
        return PreparedResult.Success(PreparedCatalog(
            catalog.episodes.size, rows, references, seasonRows, mappingRows, identity
        ))
    }
}

object CrunchyrollSeriesIdentityPolicy {
    fun select(primaryMatch: String?, aliasMatches: List<String>): String? =
        primaryMatch ?: aliasMatches.distinct().singleOrNull()

    fun selectAll(primaryMatch: String?, aliasMatches: List<String>): List<String> =
        (listOfNotNull(primaryMatch) + aliasMatches).distinct()

    fun isBroaderAlias(primaryTitle: String, alias: String): Boolean {
        val primaryTokens = titleTokens(primaryTitle)
        val aliasTokens = titleTokens(alias)
        return aliasTokens.isNotEmpty() && aliasTokens.size < primaryTokens.size &&
            primaryTokens.containsAll(aliasTokens)
    }

    private fun titleTokens(value: String): Set<String> = Normalizer.normalize(
        value.lowercase(), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .split(Regex("[^a-z0-9]+"))
        .filter(String::isNotBlank)
        .toSet()
}

/** Confirmed catalogue assignments for works whose public offers/search are ambiguous. */
object ConfirmedCrunchyrollCatalogPolicy {
    fun forAnime(animeId: String, discovered: List<String>): List<String> = when (animeId) {
        "aniworld:azur-lane-slow-ahead" -> listOf("GQWH0MXPQ")
        "aniworld:detektiv-conan" -> listOf("GW4HM7NV3", "G6JQVM3ER")
        else -> discovered.distinct()
    }

    // Explicitly confirmed by the user for the current Conan catalogue. Keep the
    // provider's season number and ID unchanged in ProviderSeasonMappingEntity.
    fun canonicalSeasonNumber(animeId: String, seriesId: String, providerSeasonNumber: Int): Int =
        if (animeId == "aniworld:detektiv-conan" && seriesId == "G6JQVM3ER" && providerSeasonNumber == 1) 32
        else providerSeasonNumber

    fun seasonLabel(animeId: String, seriesId: String, providerSeasonNumber: Int, original: String?): String? =
        if (canonicalSeasonNumber(animeId, seriesId, providerSeasonNumber) == 32 &&
            animeId == "aniworld:detektiv-conan" && seriesId == "G6JQVM3ER") "Staffel 32 (Aktuell)"
        else original
}
