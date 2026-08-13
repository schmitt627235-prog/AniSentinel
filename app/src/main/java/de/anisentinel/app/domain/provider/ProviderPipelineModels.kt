package de.anisentinel.app.domain.provider

import java.time.Instant

enum class MatchConfidence { EXACT_ID, HIGH, MEDIUM, LOW }
enum class MonetizationType { FLATRATE, BUY, RENT, ADS, FREE, CHANNEL, UNKNOWN }
enum class EpisodeAvailabilityStatus {
    SCHEDULED, DUE, CHECKING, OVERDUE_UNCONFIRMED, DELAYED_CONFIRMED,
    AVAILABLE_GER_SUB, AVAILABLE_GER_DUB,
    AVAILABLE_GER_SUB_AND_DUB, EPISODE_FOUND_LANGUAGE_UNKNOWN,
    JUSTWATCH_EPISODE_NOT_LISTED, PROVIDER_EPISODE_NOT_FOUND,
    EPISODE_FOUND_NO_OFFERS, OFFERS_FOUND_NO_GERMAN_LANGUAGE,
    MATCH_AMBIGUOUS, SOURCE_UNREACHABLE, API_RESPONSE_INVALID,
    PROVIDER_CHECK_FAILED, LOGIN_REQUIRED, REGION_BLOCKED, PARSER_CHANGED,
    ANIWORLD_FALLBACK_WAITING, ANIWORLD_FALLBACK_NOT_FOUND, NOT_AVAILABLE_YET, CHECK_FAILED
}

data class ProviderMetadataProbeRequest(
    val animeId: String, val title: String, val seasonNumber: Int?, val episodeNumber: Int,
    val expectedLanguage: String?, val market: String = ProviderMarketPolicy.GERMANY,
    val providerUrl: String? = null, val expectedAt: Instant? = null
)

data class ProviderMetadataIdentity(
    val provider: String, val market: String, val seriesId: String,
    val seasonId: String? = null, val episodeId: String? = null,
    val sourceUrl: String? = null, val seasonNumber: Int? = null
)

sealed interface ProviderMetadataProbeResult {
    val checkedAt: Instant
    data class Available(
        val availability: ProviderEpisodeAvailability,
        val identity: ProviderMetadataIdentity,
        override val checkedAt: Instant = availability.checkedAt
    ) : ProviderMetadataProbeResult
    data class NotAvailableYet(
        val identity: ProviderMetadataIdentity?, override val checkedAt: Instant,
        val diagnostic: String
    ) : ProviderMetadataProbeResult
    data class CheckFailed(
        val code: String, override val checkedAt: Instant, val retryable: Boolean
    ) : ProviderMetadataProbeResult
}

interface ProviderMetadataAdapter {
    val adapterId: String
    fun supports(providerName: String): Boolean
    suspend fun probe(request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity?): ProviderMetadataProbeResult
}

data class JustWatchTitleMatch(
    val justWatchId: String,
    val tmdbId: Long?,
    val title: String,
    val originalTitle: String?,
    val releaseYear: Int?,
    val contentType: String,
    val matchConfidence: MatchConfidence
)

data class JustWatchGenre(val id: String, val label: String)

data class JustWatchCatalogTitle(
    val justWatchId: String,
    val title: String,
    val releaseYear: Int?,
    val contentType: String,
    val genres: Set<String>,
    val coverUrl: String?,
    val justWatchUrl: String?,
    val providers: Set<String>,
    val providerUrls: Map<String, String>,
    val germanSubAvailable: Boolean?,
    val germanDubAvailable: Boolean?,
    val fetchedAt: Instant,
    val popularityRank: Int? = null,
    val description: String? = null,
    val studios: Set<String> = emptySet()
)

sealed interface JustWatchCatalogResult {
    data class Success(val genres: List<JustWatchGenre> = emptyList(), val titles: List<JustWatchCatalogTitle> = emptyList()) : JustWatchCatalogResult
    data class Failed(val code: String, val retryable: Boolean) : JustWatchCatalogResult
    data object SourceNotConfigured : JustWatchCatalogResult
}

interface JustWatchCatalogSource {
    suspend fun genres(): JustWatchCatalogResult
    suspend fun title(justWatchId: String): JustWatchCatalogResult =
        JustWatchCatalogResult.SourceNotConfigured
    suspend fun search(
        query: String? = null,
        genreIds: Set<String> = emptySet(),
        contentTypes: Set<String> = setOf("SHOW", "MOVIE"),
        offset: Int = 0,
        first: Int = 30,
        sort: String = "POPULAR"
    ): JustWatchCatalogResult
}

object UnconfiguredJustWatchCatalogSource : JustWatchCatalogSource {
    override suspend fun genres() = JustWatchCatalogResult.SourceNotConfigured
    override suspend fun search(query: String?, genreIds: Set<String>, contentTypes: Set<String>, offset: Int, first: Int, sort: String) =
        JustWatchCatalogResult.SourceNotConfigured
}

data class JustWatchOffer(
    val justWatchId: String,
    val providerId: String,
    val providerName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val monetizationType: MonetizationType,
    val presentationType: String?,
    val audioLanguages: Set<String>,
    val subtitleLanguages: Set<String>,
    val offerUrl: String?,
    val fetchedAt: Instant
)

sealed interface JustWatchSourceResult {
    data class Success(
        val matches: List<JustWatchTitleMatch>,
        val offers: List<JustWatchOffer>,
        val seasonFound: Boolean? = null,
        val episodeFound: Boolean? = null,
        val catalogTitles: List<JustWatchCatalogTitle> = emptyList()
    ) : JustWatchSourceResult
    data class SourceNotConfigured(val diagnostic: String = "SOURCE_NOT_CONFIGURED") : JustWatchSourceResult
    data class Failed(val code: String, val retryable: Boolean) : JustWatchSourceResult
}

interface JustWatchPartnerSource {
    suspend fun lookup(
        title: String,
        year: Int?,
        contentType: String,
        seasonNumber: Int?,
        episodeNumber: Int,
    ): JustWatchSourceResult
}

object UnconfiguredJustWatchPartnerSource : JustWatchPartnerSource {
    override suspend fun lookup(title: String, year: Int?, contentType: String, seasonNumber: Int?, episodeNumber: Int) =
        JustWatchSourceResult.SourceNotConfigured()
}

data class ProviderEpisodeAvailability(
    val providerId: String,
    val seasonNumber: Int?,
    val episodeNumber: Int,
    val episodeFound: Boolean,
    val germanSubAvailable: Boolean?,
    val germanDubAvailable: Boolean?,
    val availableSince: Instant?,
    val episodeUrl: String?,
    val checkedAt: Instant,
    val evidenceType: String,
    val evidenceUrl: String?
)

sealed interface ProviderEpisodeCheckResult {
    data class Checked(val availability: ProviderEpisodeAvailability) : ProviderEpisodeCheckResult
    data class Failed(val code: String, val checkedAt: Instant, val retryable: Boolean) : ProviderEpisodeCheckResult
}

interface ProviderEpisodeChecker {
    suspend fun checkEpisode(
        providerId: String,
        title: String,
        seasonNumber: Int?,
        episodeNumber: Int,
        expectedLanguage: String?,
        providerUrl: String? = null,
        expectedAt: Instant? = null
    ): ProviderEpisodeCheckResult
}

object UnconfiguredProviderEpisodeChecker : ProviderEpisodeChecker {
    override suspend fun checkEpisode(providerId: String, title: String, seasonNumber: Int?, episodeNumber: Int, expectedLanguage: String?, providerUrl: String?, expectedAt: Instant?) =
        ProviderEpisodeCheckResult.Failed("SOURCE_NOT_CONFIGURED", Instant.now(), false)
}

object StreamingProviderPolicy {
    private val physicalMarkers = listOf(
        "dvd", "blu-ray", "blu ray", "bücher", "buecher", "book",
        "medimops", "thalia", "hugendubel", "jpc", "zavvi", "zoxs"
    )
    private val preferred = listOf("crunchyroll", "netflix", "prime video", "disney+", "adn")

    fun isStreaming(provider: String): Boolean = provider.isNotBlank() &&
        physicalMarkers.none { it in provider.lowercase() }

    fun visible(providers: Iterable<String>): List<String> = providers
        .filter(::isStreaming)
        .distinct()
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun confirmedDisplayProvider(providers: Iterable<String>): String? = providers
        .filter(::isStreaming)
        .distinct()
        .minWithOrNull(compareBy<String> { provider ->
            preferred.indexOfFirst { it in provider.lowercase() }.let { if (it < 0) Int.MAX_VALUE else it }
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it })
}

object ProviderMarketPolicy {
    const val GERMANY = "DE"

    fun isAppMarket(market: String?): Boolean = market == GERMANY
}

object EpisodeStatusResolver {
    fun resolve(expectedLanguage: String?, result: ProviderEpisodeCheckResult): EpisodeAvailabilityStatus = when (result) {
        is ProviderEpisodeCheckResult.Failed -> EpisodeAvailabilityStatus.CHECK_FAILED
        is ProviderEpisodeCheckResult.Checked -> with(result.availability) {
            if (!episodeFound) EpisodeAvailabilityStatus.PROVIDER_EPISODE_NOT_FOUND
            else if (germanSubAvailable == true && germanDubAvailable == true) EpisodeAvailabilityStatus.AVAILABLE_GER_SUB_AND_DUB
            else if (expectedLanguage == "GER_DUB" && germanDubAvailable == true) EpisodeAvailabilityStatus.AVAILABLE_GER_DUB
            else if (expectedLanguage == "GER_SUB" && germanSubAvailable == true) EpisodeAvailabilityStatus.AVAILABLE_GER_SUB
            else if (germanDubAvailable == true) EpisodeAvailabilityStatus.AVAILABLE_GER_DUB
            else if (germanSubAvailable == true) EpisodeAvailabilityStatus.AVAILABLE_GER_SUB
            else EpisodeAvailabilityStatus.EPISODE_FOUND_LANGUAGE_UNKNOWN
        }
    }
}
