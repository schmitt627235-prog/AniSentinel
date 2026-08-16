package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.JustWatchCatalogTitleEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.provider.JustWatchTitleMatcher
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProviderSummary(val name: String, val titleCount: Int)
data class DubReleaseItem(val release: EpisodeReleaseEntity, val title: String, val coverUrl: String?)
enum class ReleaseStatisticCategory { TODAY, THIS_WEEK, GER_SUB, GER_DUB, AVAILABLE, DELAYED, POSTPONED }
data class ReleaseStatisticItem(
    val stableId: String,
    val animeId: String?,
    val title: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val language: String?,
    val expectedAt: Long?,
    val releaseTimePrecision: String,
    val provider: String?,
    val status: String,
    val postponement: de.anisentinel.app.data.local.ReleasePostponementEntity? = null
)
data class ReleaseStatistics(
    val today: Int = 0,
    val thisWeek: Int = 0,
    val germanSub: Int = 0,
    val germanDub: Int = 0,
    val confirmedAvailable: Int = 0,
    val delayed: Int = 0,
    val postponed: Int = 0
)
internal fun releaseStatisticsFrom(items: Map<ReleaseStatisticCategory, List<ReleaseStatisticItem>>) = ReleaseStatistics(
    today = items[ReleaseStatisticCategory.TODAY].orEmpty().size,
    thisWeek = items[ReleaseStatisticCategory.THIS_WEEK].orEmpty().size,
    germanSub = items[ReleaseStatisticCategory.GER_SUB].orEmpty().size,
    germanDub = items[ReleaseStatisticCategory.GER_DUB].orEmpty().size,
    confirmedAvailable = items[ReleaseStatisticCategory.AVAILABLE].orEmpty().size,
    delayed = items[ReleaseStatisticCategory.DELAYED].orEmpty().size,
    postponed = items[ReleaseStatisticCategory.POSTPONED].orEmpty().size
)
data class SecondaryUiState(
    val catalog: List<JustWatchCatalogTitleEntity> = emptyList(),
    val currentSeasonTitles: List<CatalogAnimeItem> = emptyList(),
    val providers: List<ProviderSummary> = emptyList(),
    val dubReleases: List<DubReleaseItem> = emptyList(),
    val statistics: ReleaseStatistics = ReleaseStatistics(),
    val scheduleChanges: List<de.anisentinel.app.data.local.ReleaseScheduleHistorySummary> = emptyList(),
    val statisticItems: Map<ReleaseStatisticCategory, List<ReleaseStatisticItem>> = emptyMap()
)

object CurrentSeasonResolver {
    private const val CYCLE_WINDOW = 21 * 86_400L
    private const val RECENT_RELEASE = 8 * 86_400L

    fun activeAnimeIds(releases: List<EpisodeReleaseEntity>, now: Long): Set<String> = releases
        .filter { it.metadataSource == "ANIWORLD_CALENDAR" && it.expectedAt != null }
        .groupBy { it.animeId to it.seasonNumber }
        .filterValues { cycle ->
            val times = cycle.mapNotNull { it.expectedAt }
            val recentPast = times.filter { it in (now - CYCLE_WINDOW)..now }
            val nearFuture = times.filter { it > now && it <= now + CYCLE_WINDOW }
            val nextRealRelease = cycle
                .filter { (it.expectedAt ?: Long.MAX_VALUE) in (now + 1)..(now + RECENT_RELEASE) }
                .minByOrNull { it.expectedAt ?: Long.MAX_VALUE }
            (recentPast.isNotEmpty() && nearFuture.isNotEmpty()) ||
                (recentPast.size >= 2 && recentPast.max() >= now - RECENT_RELEASE) ||
                ((nextRealRelease?.episodeNumber ?: 0) > 1)
        }
        .keys
        .mapTo(mutableSetOf()) { it.first }
}

class SecondaryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AniSentinelApplication).container
    private val dao = container.database.aniSentinelDao()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val today = java.time.LocalDate.now()
                container.aniWorldReleaseRepository.syncCalendar(today.minusDays(7), today.plusDays(35))
                container.aniWorldReleaseRepository.syncScheduleChanges()
                container.justWatchCatalogRepository.refreshGenres()
                val now = Instant.now().epochSecond
                dao.dueFavoriteReleases(now, now - 8 * 86_400)
                    .filter { release -> dao.episodeProviderAvailability(release.sourceReleaseId).none { it.status.startsWith("AVAILABLE") } }
                    .sortedByDescending { it.expectedAt }
                    .take(2)
                    .forEach { container.providerPipelineRepository.diagnoseHistoricalEpisode(it.sourceReleaseId) }
            } finally { _refreshing.value = false }
        }
    }

    private data class BaseData(
        val catalog: List<JustWatchCatalogTitleEntity>,
        val releases: List<EpisodeReleaseEntity>,
        val anime: List<AnimeEntity>,
        val availability: List<EpisodeProviderAvailabilityEntity>,
        val providerReferences: List<ProviderReferenceEntity>
    )
    private val base = combine(
        dao.observeKnownAnimeJustWatchCatalogTitles(),
        dao.observeAllEpisodeReleases(),
        dao.observeAnime(),
        dao.observeAllEpisodeProviderAvailability(),
        dao.observeJustWatchProviderReferences()
    ) { catalog, releases, anime, availability, references ->
        BaseData(catalog, releases, anime, availability, references)
    }
    val state = combine(base, dao.observeAllReleaseScheduleHistory(), dao.observeReleasePostponements()) { data, history, postponements ->
        buildState(data.catalog, data.releases, data.anime, data.availability, data.providerReferences, postponements)
            .copy(scheduleChanges = history)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecondaryUiState())

    private fun buildState(
        catalog: List<JustWatchCatalogTitleEntity>,
        releases: List<EpisodeReleaseEntity>,
        anime: List<AnimeEntity>,
        availability: List<EpisodeProviderAvailabilityEntity>,
        providerReferences: List<ProviderReferenceEntity>,
        postponements: List<de.anisentinel.app.data.local.ReleasePostponementEntity>
    ): SecondaryUiState {
        val now = Instant.now().epochSecond
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val dayStart = today.atStartOfDay(zone).toEpochSecond()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay(zone).toEpochSecond()
        val weekEnd = java.time.Instant.ofEpochSecond(weekStart).atZone(zone).plusWeeks(1).toEpochSecond()
        val animeById = anime.associateBy { it.id }
        val activeIds = CurrentSeasonResolver.activeAnimeIds(releases, now)
        val catalogByAnime = catalog.filter { it.internalAnimeId != null }.groupBy { it.internalAnimeId!! }
        val referencesByAnime = providerReferences.groupBy { it.animeId }
        val currentSeasonTitles = activeIds.mapNotNull { animeId ->
            val sourceAnime = animeById[animeId] ?: return@mapNotNull null
            val knownTitles = listOfNotNull(
                sourceAnime.titleGerman, sourceAnime.titleEnglish, sourceAnime.titleRomaji, sourceAnime.titleNative
            )
            val enrichment = catalogByAnime[animeId].orEmpty().filter {
                JustWatchTitleMatcher.isConservativeEquivalent(knownTitles, it.title) &&
                    (sourceAnime.seasonYear == null || it.releaseYear == null || sourceAnime.seasonYear == it.releaseYear) &&
                    it.contentType.equals("SHOW", ignoreCase = true)
            }
            CatalogAnimeItem(
                stableKey = animeId,
                id = animeId,
                title = sourceAnime.titleGerman,
                subtitle = listOfNotNull(sourceAnime.season, sourceAnime.seasonYear?.toString()).joinToString(" · "),
                providers = StreamingProviderPolicy.visible(
                    enrichment.flatMap { it.providers.split(',').filter(String::isNotBlank) } +
                        referencesByAnime[animeId].orEmpty().map { it.provider }
                ),
                coverUrl = enrichment.firstNotNullOfOrNull { it.coverUrl } ?: sourceAnime.coverUrl
            )
        }.sortedBy { it.title.lowercase() }
        val providerCounts = catalog.flatMap { row -> row.providers.split(',').filter(String::isNotBlank).distinct().map { it to row.justWatchId } }
            .groupBy({ it.first }, { it.second })
            .map { ProviderSummary(it.key, it.value.distinct().size) }
            .filter { it.name in StreamingProviderPolicy.visible(listOf(it.name)) }
            .sortedByDescending { it.titleCount }
        val dubs = releases.filter { it.releaseLanguage == "GER_DUB" }
            .sortedByDescending { it.expectedAt }
            .map { DubReleaseItem(it, animeById[it.animeId]?.titleGerman ?: it.animeId, animeById[it.animeId]?.coverUrl) }
        val availabilityByRelease = availability.groupBy { it.releaseId }
        val releaseItems = releases.map { release ->
            val evidence = availabilityByRelease[release.sourceReleaseId].orEmpty()
                .filter { it.firstAvailableAt != null || it.status.startsWith("AVAILABLE_") }
                .maxByOrNull { it.lastCheckedAt }
            ReleaseStatisticItem(
                release.sourceReleaseId, release.animeId,
                animeById[release.animeId]?.titleGerman ?: release.animeId,
                release.seasonNumber, release.episodeNumber, release.releaseLanguage,
                release.expectedAt, release.releaseTimePrecision, evidence?.providerName ?: release.provider,
                if (evidence != null) "AVAILABLE" else release.releaseStatus
            )
        }
        val postponedItems = postponements.sortedByDescending { it.detectedAt }.map { row ->
            ReleaseStatisticItem(
                "postponement:${row.postponementId}", row.animeId, row.title, row.seasonNumber,
                row.episodeNumber, row.releaseLanguage, row.newExpectedAt, "DERIVED", null,
                if (row.isActive) "POSTPONED" else "ARCHIVED", row
            )
        }
        val statisticItems = mapOf(
            ReleaseStatisticCategory.TODAY to releaseItems.filter { it.expectedAt in dayStart until (dayStart + 86_400) },
            ReleaseStatisticCategory.THIS_WEEK to releaseItems.filter { it.expectedAt in weekStart until weekEnd },
            ReleaseStatisticCategory.GER_SUB to releaseItems.filter { it.language == "GER_SUB" },
            ReleaseStatisticCategory.GER_DUB to releaseItems.filter { it.language == "GER_DUB" },
            ReleaseStatisticCategory.AVAILABLE to releaseItems.filter { it.status == "AVAILABLE" },
            ReleaseStatisticCategory.DELAYED to releaseItems.filter { it.status.contains("DELAYED") },
            ReleaseStatisticCategory.POSTPONED to postponedItems
        ).mapValues { (_, items) -> items.sortedByDescending { it.expectedAt ?: Long.MIN_VALUE } }
        return SecondaryUiState(
            catalog = catalog,
            currentSeasonTitles = currentSeasonTitles,
            providers = providerCounts,
            dubReleases = dubs,
            statistics = releaseStatisticsFrom(statisticItems),
            statisticItems = statisticItems
        )
    }
}

internal fun JustWatchCatalogTitleEntity.secondaryCatalogItem() = internalAnimeId?.let { animeId ->
    CatalogAnimeItem(
        stableKey = justWatchId,
        id = animeId,
        title = title,
        subtitle = listOfNotNull(if (contentType == "MOVIE") "Film" else "Serie", releaseYear?.toString()).joinToString(" · "),
        providers = StreamingProviderPolicy.visible(providers.split(',').filter(String::isNotBlank)),
        coverUrl = coverUrl
    )
}
