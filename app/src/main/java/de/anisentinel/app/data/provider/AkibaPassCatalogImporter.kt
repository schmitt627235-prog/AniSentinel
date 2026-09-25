package de.anisentinel.app.data.provider

import de.anisentinel.app.BuildConfig
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.AnimeSeasonEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import de.anisentinel.app.data.local.ReleaseSourceReferenceEntity
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Public product pages only; never touches account, checkout or playback endpoints. */
class AkibaPassCatalogClient(
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC()
) {
    private val requestHeaders = mapOf(
        "Accept" to "text/html",
        "User-Agent" to "AniSentinel/${BuildConfig.VERSION_NAME} (+https://github.com/schmitt627235-prog/AniSentinel)"
    )
    private val mutex = Mutex()
    private var indexCache: Pair<Instant, List<AkibaPassProduct>>? = null
    private val seasonCache = mutableMapOf<String, Pair<Instant, AkibaPassSeason>>()

    suspend fun products(): List<AkibaPassProduct> = mutex.withLock {
        indexCache?.takeIf { clock.instant().isBefore(it.first.plusSeconds(6 * 60 * 60)) }
            ?.second?.let { return@withLock it }
        val url = "https://www.akibapass.tv/products"
        val response = transport.get(url, requestHeaders)
        require(response.status in 200..299 && isPublicAkibaUrl(response.finalUrl)) {
            "AKIBA_INDEX_HTTP_${response.status}"
        }
        val products = AkibaPassPublicCatalogParser.products(response.body)
        require(products.isNotEmpty()) { "AKIBA_INDEX_EMPTY" }
        indexCache = clock.instant() to products
        products
    }

    suspend fun season(product: AkibaPassProduct): AkibaPassSeason = mutex.withLock {
        seasonCache[product.id]
            ?.takeIf { clock.instant().isBefore(it.first.plusSeconds(6 * 60 * 60)) }
            ?.second?.let { return@withLock it }
        require(isPublicAkibaUrl(product.url) && URI(product.url).path == "/products/${product.id}") {
            "AKIBA_PRODUCT_URL_INVALID"
        }
        val response = transport.get(product.url, requestHeaders)
        require(response.status in 200..299 && isPublicAkibaUrl(response.finalUrl)) {
            "AKIBA_PRODUCT_HTTP_${response.status}"
        }
        val season = AkibaPassPublicCatalogParser.season(response.body, product.url)
            ?: error("AKIBA_PRODUCT_NO_EPISODES")
        seasonCache[product.id] = clock.instant() to season
        season
    }

    private fun isPublicAkibaUrl(url: String): Boolean = runCatching {
        URI(url).let { it.scheme == "https" && it.host == "www.akibapass.tv" }
    }.getOrDefault(false)
}

class AkibaPassCatalogImporter(
    private val dao: AniSentinelDao,
    private val client: AkibaPassCatalogClient = AkibaPassCatalogClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.of("Europe/Berlin")
) {
    suspend fun importByTitle(animeId: String, aliases: Set<String>): HistoricalImportResult {
        val products = runCatching { client.products() }
            .getOrElse { return HistoricalImportResult.Failed(it.message ?: "AKIBA_INDEX_FAILED") }
        val matches = AkibaPassPublicCatalogParser.matchingSeasonProducts(products, aliases)
        if (matches.isEmpty()) return HistoricalImportResult.Failed("AKIBA_EXACT_TITLE_NOT_IDENTIFIED")
        // Prefer one bilingual package to two separate language packages for the same part.
        val selected = matches.groupBy { product ->
            Regex("(?:Season|Staffel)\\s*(\\d+)(?:[.,](\\d+))?", RegexOption.IGNORE_CASE)
                .find(product.title)?.let { it.groupValues[1] to it.groupValues[2] }
        }.values.flatMap { group ->
            group.filter { it.title.contains("DE+OmU", true) || it.title.contains("OmU+DE", true) }
                .ifEmpty { group }
        }
        val parsed = mutableListOf<AkibaPassSeason>()
        for (product in selected) {
            val season = runCatching { client.season(product) }
                .getOrElse { return HistoricalImportResult.Failed(it.message ?: "AKIBA_PRODUCT_FAILED") }
            parsed += season
        }
        val now = clock.instant()
        val available = parsed.filter { season ->
            season.purchasable && (season.availableFrom == null ||
                !season.availableFrom.isAfter(LocalDate.ofInstant(now, zoneId)))
        }
        if (available.isEmpty()) return HistoricalImportResult.Failed("AKIBA_NO_CONFIRMED_PURCHASABLE_SEASONS")
        val rows = mutableListOf<EpisodeReleaseEntity>()
        val refs = mutableListOf<ReleaseSourceReferenceEntity>()
        val mappings = mutableListOf<ProviderSeasonMappingEntity>()
        for ((key, group) in available.groupBy { it.seasonNumber to it.partNumber }) {
            val (seasonNumber, partNumber) = key
            val catalogId = "s${seasonNumber}p${partNumber ?: 0}"
            val representative = group.first()
            mappings += ProviderSeasonMappingEntity(
                animeId, seasonNumber, "AKIBA PASS", seasonNumber,
                representative.product.id, catalogId, representative.product.url,
                "DE", true, now.epochSecond, representative.label, catalogId
            )
            for (season in group) for (episode in season.episodes) {
                val language = episode.language ?: continue
                val releaseId = "akiba-history:$animeId:$catalogId:${season.product.id}:s$seasonNumber:e${episode.number}:${language.lowercase()}"
                rows += EpisodeReleaseEntity(
                    releaseId, animeId, episode.number, episode.title, null, "AKIBA PASS",
                    "AKIBA_PASS_PUBLIC_PRODUCT", season.product.url, episode.url,
                    now.epochSecond, seasonNumber, releaseStatus = "AVAILABLE",
                    releaseLanguage = language, isHistoricalImport = true,
                    releaseTimePrecision = "UNKNOWN", historicalSourcePriority = HistoricalSourcePolicy.PROVIDER_EPISODE,
                    providerEpisodeDescription = episode.description,
                    providerEpisodeDuration = episode.duration
                )
                refs += ReleaseSourceReferenceEntity(
                    "akiba-ref:$releaseId", releaseId, "AKIBA_PASS_PUBLIC_PRODUCT",
                    episode.id, season.product.url, now.epochSecond
                )
            }
        }
        if (rows.isEmpty()) return HistoricalImportResult.Failed("AKIBA_NO_LANGUAGE_IDENTIFIED_EPISODES")
        dao.replaceHistoricalProviderCatalogIfSeriesChanged(
            animeId, "AKIBA PASS", available.first().product.id, rows, refs,
            mappings.map { it.canonicalSeasonNumber }.distinct().map {
                AnimeSeasonEntity(animeId, it, "AKIBA_PASS_PUBLIC_PRODUCT", now.epochSecond)
            }, mappings
        )
        return HistoricalImportResult.Success(parsed.sumOf { it.episodes.size }, rows.size, 0)
    }
}
