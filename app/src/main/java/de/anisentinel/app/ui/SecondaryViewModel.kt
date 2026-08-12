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

data class ProviderSummary(val name: String, val titleCount: Int)
data class DubReleaseItem(val release: EpisodeReleaseEntity, val title: String, val coverUrl: String?)
data class ReleaseStatistics(
    val today: Int = 0,
    val thisWeek: Int = 0,
    val germanSub: Int = 0,
    val germanDub: Int = 0,
    val confirmedAvailable: Int = 0,
    val delayed: Int = 0,
    val postponed: Int = 0
)
data class SecondaryUiState(
    val catalog: List<JustWatchCatalogTitleEntity> = emptyList(),
    val currentSeasonTitles: List<CatalogAnimeItem> = emptyList(),
    val providers: List<ProviderSummary> = emptyList(),
    val dubReleases: List<DubReleaseItem> = emptyList(),
    val statistics: ReleaseStatistics = ReleaseStatistics(),
    val scheduleChanges: List<de.anisentinel.app.data.local.ReleaseScheduleHistorySummary> = emptyList()
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
    private val dao = (application as AniSentinelApplication).container.database.aniSentinelDao()

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
    val state = combine(base, dao.observeAllReleaseScheduleHistory()) { data, history ->
        buildState(data.catalog, data.releases, data.anime, data.availability, data.providerReferences)
            .copy(scheduleChanges = history)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SecondaryUiState())

    private fun buildState(
        catalog: List<JustWatchCatalogTitleEntity>,
        releases: List<EpisodeReleaseEntity>,
        anime: List<AnimeEntity>,
        availability: List<EpisodeProviderAvailabilityEntity>,
        providerReferences: List<ProviderReferenceEntity>
    ): SecondaryUiState {
        val now = Instant.now().epochSecond
        val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond()
        val weekEnd = dayStart + 7 * 86_400
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
        val availableReleaseIds = availability.filter { it.status.startsWith("AVAILABLE_") }.mapTo(mutableSetOf()) { it.releaseId }
        return SecondaryUiState(
            catalog = catalog,
            currentSeasonTitles = currentSeasonTitles,
            providers = providerCounts,
            dubReleases = dubs,
            statistics = ReleaseStatistics(
                today = releases.count { it.expectedAt in dayStart until (dayStart + 86_400) },
                thisWeek = releases.count { it.expectedAt in dayStart until weekEnd },
                germanSub = releases.count { it.releaseLanguage == "GER_SUB" },
                germanDub = releases.count { it.releaseLanguage == "GER_DUB" },
                confirmedAvailable = releases.count { it.sourceReleaseId in availableReleaseIds },
                delayed = releases.count { it.releaseStatus.contains("DELAYED") },
                postponed = releases.count { it.releaseStatus == "POSTPONED" }
            )
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
