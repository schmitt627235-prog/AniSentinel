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
    data class BackupUiState(
        val busy: Boolean = false,
        val message: String? = null,
        val lastBackupAt: Long? = null,
        val exportSections: Set<de.anisentinel.app.data.settings.BackupSection> = de.anisentinel.app.data.settings.BackupSection.all,
        val preview: de.anisentinel.app.data.settings.BackupPreview? = null,
        val restoreSections: Set<de.anisentinel.app.data.settings.BackupSection> = emptySet()
    )
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
    private val backupManager = de.anisentinel.app.data.settings.LocalBackupManager(getApplication(), repository, dao)
    private val _backupState = MutableStateFlow(BackupUiState())
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()
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

    fun toggleReleaseDueNotifications() {
        viewModelScope.launch {
            repository.setReleaseDueNotificationsEnabled(!settings.value.releaseDueNotificationsEnabled)
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

    fun toggleProviderVisibility(providerId: String) {
        val disabled = settings.value.disabledProviderIds.toMutableSet()
        if (!disabled.add(providerId)) disabled.remove(providerId)
        viewModelScope.launch { repository.setDisabledProviders(disabled) }
    }

    fun toggleCalendarSub() { viewModelScope.launch { repository.setCalendarShowSub(!settings.value.calendarShowSub) } }
    fun toggleCalendarDub() { viewModelScope.launch { repository.setCalendarShowDub(!settings.value.calendarShowDub) } }
    fun toggleCalendarPast() { viewModelScope.launch { repository.setCalendarShowPast(!settings.value.calendarShowPast) } }
    fun toggleCalendarFavoritesOnly() { viewModelScope.launch { repository.setCalendarFavoritesOnly(!settings.value.calendarFavoritesOnly) } }

    fun toggleExportSection(section: de.anisentinel.app.data.settings.BackupSection) {
        _backupState.value = _backupState.value.copy(exportSections = _backupState.value.exportSections.toggle(section), message = null)
    }
    fun selectAllExportSections(selected: Boolean) {
        _backupState.value = _backupState.value.copy(exportSections = if (selected) de.anisentinel.app.data.settings.BackupSection.all else emptySet(), message = null)
    }
    fun toggleRestoreSection(section: de.anisentinel.app.data.settings.BackupSection) {
        _backupState.value = _backupState.value.copy(restoreSections = _backupState.value.restoreSections.toggle(section), message = null)
    }
    fun selectAllRestoreSections(selected: Boolean) {
        val available = _backupState.value.preview?.sections.orEmpty()
        _backupState.value = _backupState.value.copy(restoreSections = if (selected) available else emptySet(), message = null)
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(busy = true, message = null)
            val result = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { backupManager.export(it, _backupState.value.exportSections) }
                    ?: de.anisentinel.app.data.settings.BackupResult.Invalid("FILE_NOT_WRITABLE")
            }.getOrElse { de.anisentinel.app.data.settings.BackupResult.Invalid("EXPORT_FAILED") }
            _backupState.value = when (result) {
                is de.anisentinel.app.data.settings.BackupResult.Success -> _backupState.value.copy(busy = false, message = "BACKUP_CREATED:${result.favorites}", lastBackupAt = System.currentTimeMillis())
                is de.anisentinel.app.data.settings.BackupResult.Invalid -> _backupState.value.copy(busy = false, message = result.reason)
                is de.anisentinel.app.data.settings.BackupResult.Preview -> _backupState.value.copy(busy = false, message = "EXPORT_FAILED")
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(busy = true, message = null)
            val result = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { backupManager.inspect(it) }
                    ?: de.anisentinel.app.data.settings.BackupResult.Invalid("FILE_NOT_READABLE")
            }.getOrElse { de.anisentinel.app.data.settings.BackupResult.Invalid("IMPORT_FAILED") }
            _backupState.value = when (result) {
                is de.anisentinel.app.data.settings.BackupResult.Preview -> _backupState.value.copy(busy = false, preview = result.backup, restoreSections = result.backup.sections, message = null)
                is de.anisentinel.app.data.settings.BackupResult.Invalid -> _backupState.value.copy(busy = false, message = result.reason)
                is de.anisentinel.app.data.settings.BackupResult.Success -> _backupState.value.copy(busy = false, message = "IMPORT_FAILED")
            }
        }
    }

    fun restoreSelectedBackup() {
        val preview = _backupState.value.preview ?: return
        viewModelScope.launch {
            _backupState.value = _backupState.value.copy(busy = true)
            val result = backupManager.restore(preview, _backupState.value.restoreSections)
            _backupState.value = when (result) {
                is de.anisentinel.app.data.settings.BackupResult.Success -> _backupState.value.copy(busy = false, preview = null, restoreSections = emptySet(), message = "BACKUP_RESTORED:${result.favorites}")
                is de.anisentinel.app.data.settings.BackupResult.Invalid -> _backupState.value.copy(busy = false, message = result.reason)
                is de.anisentinel.app.data.settings.BackupResult.Preview -> _backupState.value.copy(busy = false, message = "IMPORT_FAILED")
            }
        }
    }

    fun deleteLocalUserData() {
        viewModelScope.launch {
            dao.deleteLocalUserData()
            repository.resetUserSettings()
            _backupState.value = BackupUiState(message = "LOCAL_DATA_DELETED")
        }
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

private fun <T> Set<T>.toggle(value: T): Set<T> = toMutableSet().apply { if (!add(value)) remove(value) }
