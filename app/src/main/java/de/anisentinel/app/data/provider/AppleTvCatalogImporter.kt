package de.anisentinel.app.data.provider

import de.anisentinel.app.BuildConfig
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.AnimeSeasonEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.local.ReleaseSourceReferenceEntity
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Reads only public German Apple TV show pages. JustWatch is preferred discovery. */
class AppleTvCatalogClient(
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val pageSize: Int = 50
) {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Pair<Instant, AppleTvPublicCatalog>>()

    init { require(pageSize in 1..50) }

    suspend fun load(url: String, aliases: Set<String>): AppleTvPublicCatalog = mutex.withLock {
        require(AppleTvPublicCatalogParser.isAppleShowUrl(url)) { "APPLE_TV_SHOW_URL_INVALID" }
        cache[url]?.takeIf {
            clock.instant().isBefore(it.first.plusSeconds(
                if (it.second.completeness == AppleTvCatalogCompleteness.COMPLETE) 6 * 60 * 60 else 10 * 60
            ))
        }
            ?.second?.takeIf { JustWatchTitleMatcher.isConservativeEquivalent(aliases, it.title) }
            ?.let { return@withLock it }
        val response = transport.get(url, mapOf(
            "Accept" to "text/html",
            "Accept-Language" to "de-DE,de;q=0.9",
            "User-Agent" to "AniSentinel/${BuildConfig.VERSION_NAME} (+https://github.com/schmitt627235-prog/AniSentinel)"
        ))
        require(response.status in 200..299 && AppleTvPublicCatalogParser.isAppleShowUrl(response.finalUrl)) {
            "APPLE_TV_SHOW_HTTP_${response.status}"
        }
        val visibleCatalog = AppleTvPublicCatalogParser.parse(response.body, response.finalUrl, aliases)
            ?: error("APPLE_TV_TITLE_NOT_VERIFIED")
        val catalog = runCatching {
            loadAllPublicEpisodes(response.body, visibleCatalog)
        }.getOrNull() ?: visibleCatalog
        cache[url] = clock.instant() to catalog
        catalog
    }

    /** Uses only the public show's episode-metadata endpoint, never playback data. */
    private suspend fun loadAllPublicEpisodes(html: String, catalog: AppleTvPublicCatalog): AppleTvPublicCatalog? {
        val params = AppleTvPublicEpisodeMetadata.pageParameters(html) ?: return null
        val seasons = catalog.seasons.associate { it.number to it.id }
        if (seasons.isEmpty() || seasons.values.any(String::isBlank)) return null
        val episodes = mutableListOf<AppleTvPublicEpisode>()
        var expectedTotal: Int? = null
        while (expectedTotal == null || episodes.size < expectedTotal) {
            if (episodes.size >= 10_000) return null
            val apiUrl = AppleTvPublicEpisodeMetadata.pageUrl(catalog.showId, params, episodes.size, pageSize)
            val response = transport.get(apiUrl, mapOf(
                "Accept" to "application/json",
                "Origin" to "https://tv.apple.com",
                "User-Agent" to "AniSentinel/${BuildConfig.VERSION_NAME} (+https://github.com/schmitt627235-prog/AniSentinel)"
            ))
            if (response.status !in 200..299 ||
                !AppleTvPublicEpisodeMetadata.isEpisodeResponseUrl(response.finalUrl, catalog.showId)) return null
            val page = AppleTvPublicEpisodeMetadata.parsePage(response.body, catalog.showId, seasons)
                ?: return null
            if (expectedTotal != null && page.total != expectedTotal) return null
            expectedTotal = page.total
            if (page.episodes.isEmpty() || page.episodes.size > pageSize) return null
            episodes += page.episodes
            if (episodes.size > expectedTotal) return null
        }
        if (episodes.map { it.id }.toSet().size != episodes.size ||
            catalog.seasons.any { season ->
                episodes.count { it.seasonNumber == season.number } != season.advertisedEpisodeCount
            }) return null
        val visibleById = catalog.episodes.associateBy { it.id }
        return catalog.copy(
            episodes = episodes.map { episode ->
                episode.copy(duration = visibleById[episode.id]?.duration ?: episode.duration)
            },
            completeness = AppleTvCatalogCompleteness.COMPLETE
        )
    }
}

class AppleTvCatalogImporter(
    private val dao: AniSentinelDao,
    private val client: AppleTvCatalogClient = AppleTvCatalogClient(),
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun importFromJustWatch(animeId: String, aliases: Set<String>): HistoricalImportResult {
        val references = dao.providerReferences(animeId).filter { reference ->
            reference.source in setOf("UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", OFFICIAL_PUBLIC_SOURCE) &&
                isAppleTvStoreProvider(reference.provider) &&
                AppleTvPublicCatalogParser.isAppleShowUrl(reference.seriesUrl.orEmpty()) &&
                reference.providerMarket.equals("DE", true)
        }
        val urls = references.mapNotNull { it.seriesUrl }.distinct()
        if (urls.size != 1) return HistoricalImportResult.Failed(
            if (urls.isEmpty()) "APPLE_TV_JUSTWATCH_DE_REFERENCE_MISSING" else "APPLE_TV_JUSTWATCH_REFERENCE_AMBIGUOUS"
        )
        val catalog = runCatching { client.load(urls.single(), aliases) }
            .getOrElse { return HistoricalImportResult.Failed(it.message ?: "APPLE_TV_PUBLIC_PAGE_FAILED") }
        return persist(animeId, catalog)
    }

    /** Explicit official URL fallback when JustWatch DE omits the provider. */
    suspend fun importFromOfficialPage(animeId: String, url: String, aliases: Set<String>): HistoricalImportResult {
        val catalog = runCatching { client.load(url, aliases) }
            .getOrElse { return HistoricalImportResult.Failed(it.message ?: "APPLE_TV_PUBLIC_PAGE_FAILED") }
        if (catalog.episodes.isEmpty()) return HistoricalImportResult.Failed("APPLE_TV_PUBLIC_EPISODES_NOT_VISIBLE")
        val result = persist(animeId, catalog)
        if (result is HistoricalImportResult.Success) {
            dao.upsertProviderReference(ProviderReferenceEntity(
                animeId, "Apple TV", catalog.showUrl, OFFICIAL_PUBLIC_SOURCE,
                catalog.showUrl, clock.instant().epochSecond, "DE"
            ))
        }
        return result
    }

    private suspend fun persist(animeId: String, catalog: AppleTvPublicCatalog): HistoricalImportResult {
        if (catalog.episodes.isEmpty()) return HistoricalImportResult.Failed("APPLE_TV_PUBLIC_EPISODES_NOT_VISIBLE")
        val previousAppleEpisodes = dao.episodeReleasesForAnime(animeId).count {
            it.isHistoricalImport && it.provider.equals("Apple TV", true)
        }
        if (catalog.completeness != AppleTvCatalogCompleteness.COMPLETE &&
            catalog.episodes.size < previousAppleEpisodes) {
            return HistoricalImportResult.Failed("APPLE_TV_PUBLIC_CATALOG_INCOMPLETE")
        }
        val now = clock.instant().epochSecond
        val source = "APPLE_TV_PUBLIC_PAGE_${catalog.completeness.name}"
        val releases = catalog.episodes.map { episode ->
            val releaseId = "apple-tv-history:$animeId:${catalog.showId}:s${episode.seasonNumber}:e${episode.number}:${episode.id}"
            EpisodeReleaseEntity(
                releaseId, animeId, episode.number, episode.title, null, "Apple TV",
                source, catalog.showUrl, episode.url, now, episode.seasonNumber,
                releaseStatus = "CATALOGUED", isHistoricalImport = true,
                releaseTimePrecision = "UNKNOWN", historicalSourcePriority = HistoricalSourcePolicy.PROVIDER_EPISODE,
                providerEpisodeDescription = episode.description, providerEpisodeDuration = episode.duration
            )
        }
        val sourceReferences = releases.zip(catalog.episodes).map { (release, episode) ->
            ReleaseSourceReferenceEntity(
                "apple-tv-ref:${release.sourceReleaseId}", release.sourceReleaseId,
                source, episode.id, episode.url, now
            )
        }
        val confirmedSeasons = catalog.seasons.ifEmpty {
            catalog.episodes.map { episode ->
                AppleTvPublicSeason(episode.seasonNumber, "", "Staffel ${episode.seasonNumber}", 0)
            }.distinctBy { it.number }
        }
        val mappings = confirmedSeasons.map { season ->
            ProviderSeasonMappingEntity(
                animeId, season.number, "Apple TV", season.number, catalog.showId,
                season.id.takeIf(String::isNotBlank), catalog.showUrl, "DE", true, now,
                season.label, catalog.showId
            )
        }
        dao.replaceHistoricalProviderCatalogIfSeriesChanged(
            animeId, "Apple TV", catalog.showId, releases, sourceReferences,
            emptyList<AnimeSeasonEntity>(), mappings
        )
        return HistoricalImportResult.Success(catalog.episodes.size, releases.size, 0)
    }

    companion object {
        const val OFFICIAL_PUBLIC_SOURCE = "OFFICIAL_APPLE_TV_DE_PUBLIC_PAGE"
        fun isAppleTvStoreProvider(name: String): Boolean =
            name.trim().equals("Apple TV", true) || name.trim().equals("Apple TV Store", true)
    }
}

/** Explicitly verified DE show pages used only when JustWatch omits the provider. */
object AppleTvOfficialPageRegistry {
    fun confirmedUrl(animeId: String, germanTitle: String?): String? =
        if (animeId == "aniworld:detektiv-conan" && germanTitle.equals("Detektiv Conan", true))
            "https://tv.apple.com/de/show/detektiv-conan/umc.cmc.o4e5fbtkmgjivlpghedf8a6x"
        else null
}
