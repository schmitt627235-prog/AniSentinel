package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.ProviderEpisodeAvailability
import de.anisentinel.app.domain.provider.ProviderMarketPolicy
import de.anisentinel.app.domain.provider.ProviderMetadataAdapter
import de.anisentinel.app.domain.provider.ProviderMetadataIdentity
import de.anisentinel.app.domain.provider.ProviderMetadataProbeRequest
import de.anisentinel.app.domain.provider.ProviderMetadataProbeResult
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Verifies scheduled episodes against public AKIBA PASS products, not AniWorld season data. */
class AkibaPassMetadataAdapter(
    private val client: AkibaPassCatalogClient = AkibaPassCatalogClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.of("Europe/Berlin")
) : ProviderMetadataAdapter {
    override val adapterId = "AKIBA_PASS_PUBLIC_PRODUCT_PROBE"

    override fun supports(providerName: String): Boolean = providerName.contains("akiba", true)

    override suspend fun probe(
        request: ProviderMetadataProbeRequest,
        identity: ProviderMetadataIdentity?
    ): ProviderMetadataProbeResult {
        val now = clock.instant()
        if (request.market != ProviderMarketPolicy.GERMANY)
            return ProviderMetadataProbeResult.CheckFailed("AKIBA_MARKET_NOT_DE", now, false)
        val seasonNumber = request.seasonNumber?.takeIf { it > 0 }
            ?: return ProviderMetadataProbeResult.CheckFailed("AKIBA_SEASON_UNKNOWN", now, false)
        val products = runCatching { client.products() }.getOrElse {
            return ProviderMetadataProbeResult.CheckFailed(it.message ?: "AKIBA_INDEX_FAILED", now, true)
        }
        val aliases = request.titleAliases + request.title
        val matched = AkibaPassPublicCatalogParser.matchingSeasonProducts(products, aliases, seasonNumber)
        if (matched.isEmpty())
            return ProviderMetadataProbeResult.CheckFailed("AKIBA_EXACT_TITLE_NOT_IDENTIFIED", now, false)
        val ordered = matched.sortedWith(compareByDescending<AkibaPassProduct> { it.id == identity?.seriesId }
            .thenByDescending { it.title.contains("DE+OmU", true) || it.title.contains("OmU+DE", true) })
        var confirmedProduct: ProviderMetadataIdentity? = null
        for (product in ordered) {
            val catalog = runCatching { client.season(product) }.getOrElse {
                return ProviderMetadataProbeResult.CheckFailed(it.message ?: "AKIBA_PRODUCT_FAILED", now, true)
            }
            val stable = ProviderMetadataIdentity(
                "AKIBA PASS", "DE", product.id,
                "s${catalog.seasonNumber}p${catalog.partNumber ?: 0}",
                sourceUrl = product.url, seasonNumber = catalog.seasonNumber
            )
            confirmedProduct = stable
            if (!catalog.purchasable || catalog.availableFrom?.isAfter(LocalDate.ofInstant(now, zoneId)) == true)
                continue
            val episode = catalog.episodes.firstOrNull { it.number == request.episodeNumber &&
                (request.expectedLanguage == null || it.language == request.expectedLanguage) }
                ?: continue
            return ProviderMetadataProbeResult.Available(
                ProviderEpisodeAvailability(
                    "AKIBA PASS", seasonNumber, episode.number, true,
                    episode.language == "GER_SUB", episode.language == "GER_DUB",
                    catalog.availableFrom?.atStartOfDay(zoneId)?.toInstant(), episode.url,
                    now, "OFFICIAL_PUBLIC_PRODUCT_PAGE", product.url
                ), stable.copy(episodeId = episode.id)
            )
        }
        return ProviderMetadataProbeResult.NotAvailableYet(
            confirmedProduct, now, "AKIBA_EPISODE_OR_LANGUAGE_NOT_CONFIRMED"
        )
    }
}
