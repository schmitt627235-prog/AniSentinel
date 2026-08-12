package de.anisentinel.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.domain.repository.AppSettings
import de.anisentinel.app.domain.repository.ThemePreference
import de.anisentinel.app.domain.watcher.NotificationEvent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    data class MonitoringDiagnostics(
        val activeFavorites: Int = 0,
        val scheduledJobs: Int = 0,
        val deliveries: Int = 0,
        val latestCheck: de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity? = null,
        val latestChecks: List<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity> = emptyList(),
        val latestDeliveries: List<de.anisentinel.app.data.local.NotificationDeliveryEntity> = emptyList()
    )
    data class DiagnosticImportUiState(
        val loading: Boolean = false,
        val imported: de.anisentinel.app.data.local.LocalCalendarImportResult.Success? = null,
        val alreadyImported: Boolean = false,
        val error: String? = null
    )
    private val repository =
        (application as AniSentinelApplication).container.settingsRepository
    private val notificationCoordinator =
        (application as AniSentinelApplication).container.notificationCoordinator
    private val localImportRepository =
        (application as AniSentinelApplication).container.localCalendarImportRepository
    private val _diagnosticImport = MutableStateFlow(DiagnosticImportUiState())
    val diagnosticImport: StateFlow<DiagnosticImportUiState> = _diagnosticImport.asStateFlow()
    private val dao = (application as AniSentinelApplication).container.database.aniSentinelDao()
    val monitoringDiagnostics = combine(
        dao.observeActiveFavoriteCount(),
        dao.observeScheduledReleaseNotifications(),
        dao.observeNotificationDeliveries(),
        dao.observeLatestEpisodeProviderAvailability()
    ) { favorites, scheduled, deliveries, checks ->
        MonitoringDiagnostics(
            favorites, scheduled.size, deliveries.size, checks.firstOrNull(),
            checks.take(8), deliveries.take(8)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitoringDiagnostics())

    val settings = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings()
    )

    fun cycleTheme() {
        val next = when (settings.value.theme) {
            ThemePreference.SYSTEM -> ThemePreference.DARK
            ThemePreference.DARK -> ThemePreference.LIGHT
            ThemePreference.LIGHT -> ThemePreference.SYSTEM
        }
        viewModelScope.launch { repository.setTheme(next) }
    }

    fun toggleNotifications() {
        viewModelScope.launch {
            repository.setNotificationsEnabled(!settings.value.notificationsEnabled)
        }
    }

    fun toggleLanguage() {
        val next = if (settings.value.languageTag == "de") "en" else "de"
        viewModelScope.launch {
            repository.setLanguage(next)
            notificationCoordinator.refreshChannelNames()
        }
    }

    fun sendDemoNotification() {
        viewModelScope.launch {
            notificationCoordinator.dispatch(
                NotificationEvent.EpisodeAvailable("skyward", 8),
                isTest = true
            )
        }
    }

    fun cycleWatchProfile() {
        val next = de.anisentinel.app.domain.watcher.AvailabilityWatchStrategy
            .nextProfileId(settings.value.watchProfileId)
        viewModelScope.launch { repository.setWatchProfileId(next) }
    }

    fun toggleProvider(providerId: String) {
        val providers = settings.value.preferredProviderIds.toMutableSet()
        if (!providers.add(providerId)) providers.remove(providerId)
        viewModelScope.launch { repository.setPreferredProviders(providers) }
    }

    fun toggleLiveData() {
        viewModelScope.launch {
            repository.setLiveDataEnabled(!settings.value.liveDataEnabled)
        }
    }

    fun importDiagnosticJson(uri: Uri) {
        viewModelScope.launch {
            _diagnosticImport.value = DiagnosticImportUiState(loading = true)
            val input = getApplication<Application>().contentResolver.openInputStream(uri)
            _diagnosticImport.value = if (input == null) {
                DiagnosticImportUiState(error = "FILE_NOT_READABLE")
            } else when (val result = localImportRepository.import(input)) {
                is de.anisentinel.app.data.local.LocalCalendarImportResult.Imported ->
                    DiagnosticImportUiState(imported = result)
                is de.anisentinel.app.data.local.LocalCalendarImportResult.AlreadyImported ->
                    DiagnosticImportUiState(imported = result, alreadyImported = true)
                is de.anisentinel.app.data.local.LocalCalendarImportResult.Invalid ->
                    DiagnosticImportUiState(error = result.reason)
            }
        }
    }
}
