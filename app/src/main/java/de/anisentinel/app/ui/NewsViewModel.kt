package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.local.AnnouncementEntity
import de.anisentinel.app.data.news.NewsSyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewsUiState(
    val loading: Boolean = true,
    val items: List<AnnouncementEntity> = emptyList(),
    val errorCode: String? = null
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AniSentinelApplication).container.newsRepository
    private val loading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    val state = combine(repository.observeNews(), loading, error) { items, busy, failure ->
        NewsUiState(busy, items, failure)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsUiState())

    init { refresh() }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            loading.value = true
            when (val result = repository.refresh(force = force)) {
                is NewsSyncResult.Success -> error.value = null
                is NewsSyncResult.Failed -> error.value = result.code
            }
            loading.value = false
        }
    }
}

sealed interface NewsDetailState {
    data object Loading : NewsDetailState
    data class Found(val announcement: AnnouncementEntity) : NewsDetailState
    data object NotFound : NewsDetailState
}

internal fun announcementDetailState(announcement: AnnouncementEntity?): NewsDetailState =
    announcement?.let(NewsDetailState::Found) ?: NewsDetailState.NotFound

class NewsDetailViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    private val announcementId: String = savedStateHandle["announcementId"] ?: ""
    val state = (application as AniSentinelApplication).container.database.aniSentinelDao()
        .observeAnnouncement(announcementId)
        .map(::announcementDetailState)
        .onStart { emit(NewsDetailState.Loading) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsDetailState.Loading)
}
