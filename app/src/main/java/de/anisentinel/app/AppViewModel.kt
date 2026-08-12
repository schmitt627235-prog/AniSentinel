package de.anisentinel.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.domain.repository.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val state = (application as AniSentinelApplication)
        .container
        .settingsRepository
        .settings
        .map { AppUiState(settings = it, loaded = true) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppUiState()
        )
}
