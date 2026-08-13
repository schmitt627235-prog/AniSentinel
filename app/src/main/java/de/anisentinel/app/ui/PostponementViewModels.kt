package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.local.ReleasePostponementEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostponementsViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AniSentinelApplication).container
    private val dao = container.database.aniSentinelDao()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    val items = dao.observeReleasePostponements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                container.aniWorldReleaseRepository.syncScheduleChanges()
                container.newsRepository.refresh(force = true)
            } finally { _refreshing.value = false }
        }
    }
}

class PostponementDetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val dao = (application as AniSentinelApplication).container.database.aniSentinelDao()
    val item = dao.observeReleasePostponement(savedStateHandle.get<String>("postponementId").orEmpty())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as ReleasePostponementEntity?)
}
