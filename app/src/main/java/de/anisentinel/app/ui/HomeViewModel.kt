package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.data.local.ReleasePostponementEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.ZoneId

data class CatalogUiState(
    val anime: List<Anime> = emptyList(),
    val liveMode: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val showingCachedData: Boolean = false,
    val postponementsByAnime: Map<String, List<ReleasePostponementEntity>> = emptyMap()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AniSentinelApplication).container
    private val operation = MutableStateFlow(OperationState())

    val state: StateFlow<CatalogUiState> = combine(
        container.settingsRepository.settings,
        container.aniListRepository.observeActiveAniWorldAnime(
            LocalDate.now().minusWeeks(4).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        ),
        container.database.aniSentinelDao().observeReleasePostponements(),
        operation
    ) { settings, cached, postponements, current ->
        CatalogUiState(
            anime = cached,
            liveMode = true,
            loading = current.loading && cached.isEmpty(),
            refreshing = current.loading && cached.isNotEmpty(),
            error = current.error,
            showingCachedData = cached.isNotEmpty() && current.error != null,
            postponementsByAnime = postponements.filter { it.isActive && it.animeId != null }
                .groupBy { requireNotNull(it.animeId) }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CatalogUiState(liveMode = true)
    )

    init {
        bootstrap()
    }

    private fun bootstrap() {
        if (operation.value.loading) return
        viewModelScope.launch {
            operation.value = OperationState(loading = true)
            val today = LocalDate.now()
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (getApplication<android.app.Application>().resources.getBoolean(de.anisentinel.app.R.bool.aniworld_enabled)) {
                operation.value = when (container.aniWorldReleaseRepository.syncCalendar(weekStart, weekStart.plusWeeks(2))) {
                    is de.anisentinel.app.data.release.AniWorldSyncResult.Success -> {
                        container.aniWorldReleaseRepository.syncScheduleChanges()
                        container.favoriteReleaseScheduler.reconcileAll()
                        container.providerPipelineRepository.syncTitleProviders()
                        container.database.aniSentinelDao().repairMalformedAniWorldEpisodeIdentities()
                        OperationState()
                    }
                    is de.anisentinel.app.data.release.AniWorldSyncResult.Failure -> OperationState(error = "ANIWORLD_TEMPORARILY_UNAVAILABLE")
                }
            }
        }
    }

    fun refresh(force: Boolean = true) {
        if (operation.value.loading) return
        viewModelScope.launch {
            operation.value = OperationState(loading = true)
            val today = LocalDate.now()
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            operation.value = when (container.aniWorldReleaseRepository.syncCalendar(start, start.plusWeeks(2))) {
                is de.anisentinel.app.data.release.AniWorldSyncResult.Success -> {
                    container.providerPipelineRepository.syncTitleProviders()
                    container.providerPipelineRepository.checkDueEpisodes()
                    container.favoriteReleaseScheduler.reconcileAll()
                    container.database.aniSentinelDao().repairMalformedAniWorldEpisodeIdentities()
                    OperationState()
                }
                is de.anisentinel.app.data.release.AniWorldSyncResult.Failure -> OperationState(error = "ANIWORLD_TEMPORARILY_UNAVAILABLE")
            }
        }
    }

    private data class OperationState(
        val loading: Boolean = false,
        val error: String? = null
    )
}
