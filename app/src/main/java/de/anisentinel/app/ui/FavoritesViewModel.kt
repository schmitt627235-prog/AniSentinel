package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.anilist.toDomain
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.ReleasePostponementEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class FavoritesFilter { ALL, CURRENT, UPCOMING, COMPLETED, SEASON }
enum class FavoritesSort {
    NEXT_RELEASE, LATEST_RELEASE, TITLE_ASC, TITLE_DESC, PROVIDER_ASC, PROVIDER_DESC
}

data class FavoritesUiState(
    val loading: Boolean = true,
    val filter: FavoritesFilter = FavoritesFilter.ALL,
    val sort: FavoritesSort = FavoritesSort.NEXT_RELEASE,
    val favorites: List<Anime> = emptyList(),
    val hasAnyFavorites: Boolean = false,
    val refreshing: Boolean = false,
    val postponementsByAnime: Map<String, List<ReleasePostponementEntity>> = emptyMap(),
    val seasonMetadata: Map<String, Pair<String?, Int?>> = emptyMap()
)

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository =
        (application as AniSentinelApplication).container.favoritesRepository
    private val settings = (application as AniSentinelApplication).container.settingsRepository
    private val dao = (application as AniSentinelApplication).container.database.aniSentinelDao()
    private val filter = MutableStateFlow(FavoritesFilter.ALL)
    private val refreshing = MutableStateFlow(false)

    private val favoriteData = combine(
        repository.observeFavorites(),
        dao.observeFavoriteReleasesForClassification(),
        dao.observeJustWatchProviderReferences(),
        dao.observeReleasePostponements(),
        dao.observeCatalog(AnticipatedTitlesViewModel.UPCOMING_FAVORITES)
    ) { entities, releases, references, postponements, upcoming ->
        arrayOf(entities, releases, references, postponements, upcoming)
    }

    @Suppress("UNCHECKED_CAST")
    val state = combine(favoriteData, filter, settings.favoritesSort, refreshing, settings.settings) { data, selected, storedSort, busy, appSettings ->
        val entities = data[0] as List<de.anisentinel.app.data.local.AnimeEntity>
        val releases = data[1] as List<EpisodeReleaseEntity>
        val references = data[2] as List<de.anisentinel.app.data.local.ProviderReferenceEntity>
        val postponements = data[3] as List<ReleasePostponementEntity>
        val upcoming = data[4] as List<de.anisentinel.app.data.local.AnimeEntity>
        val seasonFavoriteIds = upcoming.mapTo(mutableSetOf()) { it.id }
        val providers = references.groupBy { it.animeId }
        val releasesByAnime = releases.groupBy { it.animeId }
        val postponementsByAnime = postponements.filter { it.isActive && it.animeId != null }
            .groupBy { requireNotNull(it.animeId) }
        val now = Instant.now().epochSecond
        val allFavorites = entities.map { entity ->
            val base = entity.toDomain()
            val display = ReleaseDisplayResolver.nextFor(
                releasesByAnime[entity.id].orEmpty(), postponementsByAnime[entity.id].orEmpty(), nowEpoch = now
            )
            base.copy(
                provider = de.anisentinel.app.domain.provider.ProviderVisibilityPolicy.visibleProviders(
                    providers[entity.id].orEmpty().map { it.provider }, appSettings.disabledProviderIds
                ).joinToString(" ? "),
                expectedReleaseAt = display?.countdownTarget?.let(Instant::ofEpochSecond) ?: base.expectedReleaseAt,
                episode = display?.identity?.episode ?: base.episode,
                status = when {
                    display?.postponement != null -> de.anisentinel.app.domain.model.ReleaseStatus.OFFICIALLY_POSTPONED
                    display != null -> de.anisentinel.app.domain.watcher.ReleaseStatusResolver().resolve(Instant.ofEpochSecond(display.countdownTarget))
                    else -> base.status
                }
            )
        }
        val all = allFavorites.filter { anime ->
            de.anisentinel.app.domain.provider.ProviderVisibilityPolicy.isAnimeVisible(
                providers[anime.id].orEmpty().map { it.provider }, appSettings.disabledProviderIds
            )
        }
        val sort = storedSort.toFavoritesSort()
        val today = LocalDate.now(ZoneId.systemDefault())
        FavoritesUiState(
            loading = false,
            filter = selected,
            sort = sort,
            favorites = all.filter { anime ->
                when (selected) {
                    FavoritesFilter.SEASON -> anime.id in seasonFavoriteIds
                    FavoritesFilter.ALL -> true
                    else -> anime.id !in seasonFavoriteIds && FavoriteReleaseClassifier.matches(anime, releasesByAnime[anime.id].orEmpty(), selected, today, ZoneId.systemDefault())
                }
            }.sortedWith(FavoritesSorter.comparator(sort)),
            hasAnyFavorites = allFavorites.isNotEmpty(),
            refreshing = busy,
            postponementsByAnime = postponementsByAnime,
            seasonMetadata = upcoming.associate { it.id to (it.season to it.seasonYear) }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FavoritesUiState()
    )

    fun selectFilter(selected: FavoritesFilter) {
        filter.value = selected
    }

    fun selectSort(selected: FavoritesSort) {
        viewModelScope.launch { settings.setFavoritesSort(selected.name) }
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            try {
                val container = (getApplication<Application>() as AniSentinelApplication).container
                val visibleIds = state.value.favorites.take(8).mapTo(mutableSetOf()) { it.id }
                withTimeoutOrNull(30_000) {
                    if (visibleIds.isNotEmpty()) {
                        container.providerPipelineRepository.syncTitleProviders(
                            animeIds = visibleIds,
                            limit = visibleIds.size
                        )
                    }
                }
            } finally { refreshing.value = false }
        }
    }

}

object FavoritesSorter {
    fun comparator(sort: FavoritesSort): Comparator<Anime> = when (sort) {
            FavoritesSort.NEXT_RELEASE -> compareBy(nullsLast()) { it.expectedReleaseAt }
            FavoritesSort.LATEST_RELEASE -> compareByDescending<Anime> { it.expectedReleaseAt != null }
                .thenByDescending { it.expectedReleaseAt }
            FavoritesSort.TITLE_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            FavoritesSort.TITLE_DESC -> compareByDescending<Anime> { it.title.lowercase() }
            FavoritesSort.PROVIDER_ASC -> compareBy<Anime> { it.primaryStreamingProvider().lowercase() }
                .thenBy { it.title.lowercase() }
            FavoritesSort.PROVIDER_DESC -> compareByDescending<Anime> { it.primaryStreamingProvider().lowercase() }
                .thenByDescending { it.title.lowercase() }
    }

}

private fun String.toFavoritesSort(): FavoritesSort = when (this) {
        "TITLE" -> FavoritesSort.TITLE_ASC
        "PROVIDER" -> FavoritesSort.PROVIDER_ASC
        else -> runCatching { FavoritesSort.valueOf(this) }.getOrDefault(FavoritesSort.NEXT_RELEASE)
}

internal fun Anime.primaryStreamingProvider(): String = provider.split('?')
    .map(String::trim)
    .filter(String::isNotBlank)
    .let(StreamingProviderPolicy::visible)
    .firstOrNull().orEmpty()

object FavoriteReleaseClassifier {
    fun matches(
        anime: Anime,
        releases: List<EpisodeReleaseEntity>,
        filter: FavoritesFilter,
        today: LocalDate,
        zoneId: ZoneId
    ): Boolean {
        val dates = releases.mapNotNull { row ->
            row.expectedAt?.let { Instant.ofEpochSecond(it).atZone(zoneId).toLocalDate() }
        }
        val hasToday = dates.any { it == today }
        val hasFuture = dates.any { it.isAfter(today) }
        return when (filter) {
            FavoritesFilter.ALL -> true
            FavoritesFilter.CURRENT -> hasToday
            FavoritesFilter.UPCOMING -> !hasToday && hasFuture
            FavoritesFilter.COMPLETED -> {
                // UI label "Abgeschlossen" means no next concrete release is known. It is a
                // release-cycle state, not a claim that the anime itself has permanently ended.
                dates.any { it.isBefore(today) } && dates.none { !it.isBefore(today) }
            }
            FavoritesFilter.SEASON -> false
        }
    }
}

object UpcomingSeasonPolicy {
    fun cycleStart(season: String?, year: Int?): Int? = when {
        year == null -> null
        season == "WINTER" -> year - 1
        season in setOf("SPRING", "SUMMER", "FALL") -> year
        else -> null
    }

    fun cycleLabel(start: Int): String = "Season $start/${(start + 1).toString().takeLast(2)}"

    fun seasonLabel(season: String?, year: Int?): String = when (season) {
        "SPRING" -> "Fr?hling ${year ?: ""}".trim()
        "SUMMER" -> "Sommer ${year ?: ""}".trim()
        "FALL" -> "Herbst ${year ?: ""}".trim()
        "WINTER" -> "Winter ${year ?: ""}".trim()
        else -> "Termin noch offen"
    }
}
