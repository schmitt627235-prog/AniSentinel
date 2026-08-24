package de.anisentinel.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.anisentinel.app.domain.repository.AppSettings
import de.anisentinel.app.domain.repository.SettingsRepository
import de.anisentinel.app.domain.repository.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.aniSentinelDataStore by preferencesDataStore(name = "anisentinel_settings")

class DataStoreSettingsRepository(
    private val context: Context
) : SettingsRepository {
    val favoritesSort: Flow<String> = context.aniSentinelDataStore.data.map {
        it[Keys.FAVORITES_SORT] ?: "NEXT_RELEASE"
    }
    override val settings: Flow<AppSettings> = context.aniSentinelDataStore.data.map { values ->
        AppSettings(
            theme = values[Keys.THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
            languageTag = values[Keys.LANGUAGE] ?: "de",
            notificationsEnabled = values[Keys.NOTIFICATIONS] ?: true,
            releaseDueNotificationsEnabled = values[Keys.RELEASE_DUE_NOTIFICATIONS] ?: false,
            watchProfileId = values[Keys.WATCH_PROFILE] ?: "automatic",
            preferredProviderIds = decodeProviders(values[Keys.PROVIDERS].orEmpty()),
            disabledProviderIds = decodeProviders(values[Keys.DISABLED_PROVIDERS].orEmpty()),
            calendarShowSub = values[Keys.CALENDAR_SUB] ?: true,
            calendarShowDub = values[Keys.CALENDAR_DUB] ?: true,
            calendarShowPast = values[Keys.CALENDAR_PAST] ?: true,
            calendarFavoritesOnly = values[Keys.CALENDAR_FAVORITES_ONLY] ?: false,
            liveDataEnabled = values[Keys.LIVE_DATA] ?: false
        )
    }

    override suspend fun setTheme(theme: ThemePreference) {
        context.aniSentinelDataStore.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setLanguage(languageTag: String) {
        require(languageTag.isNotBlank())
        context.aniSentinelDataStore.edit { it[Keys.LANGUAGE] = languageTag }
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.aniSentinelDataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
    }

    override suspend fun setReleaseDueNotificationsEnabled(enabled: Boolean) {
        context.aniSentinelDataStore.edit { it[Keys.RELEASE_DUE_NOTIFICATIONS] = enabled }
    }

    override suspend fun setWatchProfileId(id: String) {
        require(id.isNotBlank())
        context.aniSentinelDataStore.edit { it[Keys.WATCH_PROFILE] = id }
    }

    override suspend fun setPreferredProviders(ids: Set<String>) {
        context.aniSentinelDataStore.edit {
            it[Keys.PROVIDERS] = ids.filter(String::isNotBlank).sorted().joinToString(",")
        }
    }

    override suspend fun setDisabledProviders(ids: Set<String>) {
        context.aniSentinelDataStore.edit {
            it[Keys.DISABLED_PROVIDERS] = encodeProviders(ids)
        }
    }

    override suspend fun setCalendarShowSub(enabled: Boolean) = updateCalendarLanguages(showSub = enabled)
    override suspend fun setCalendarShowDub(enabled: Boolean) = updateCalendarLanguages(showDub = enabled)
    override suspend fun setCalendarShowPast(enabled: Boolean) { context.aniSentinelDataStore.edit { it[Keys.CALENDAR_PAST] = enabled } }
    override suspend fun setCalendarFavoritesOnly(enabled: Boolean) { context.aniSentinelDataStore.edit { it[Keys.CALENDAR_FAVORITES_ONLY] = enabled } }

    private suspend fun updateCalendarLanguages(showSub: Boolean? = null, showDub: Boolean? = null) {
        context.aniSentinelDataStore.edit { values ->
            val sub = showSub ?: values[Keys.CALENDAR_SUB] ?: true
            val dub = showDub ?: values[Keys.CALENDAR_DUB] ?: true
            if (!sub && !dub) return@edit
            values[Keys.CALENDAR_SUB] = sub
            values[Keys.CALENDAR_DUB] = dub
        }
    }

    override suspend fun replaceUserSettings(settings: AppSettings) {
        context.aniSentinelDataStore.edit { values ->
            values[Keys.THEME] = settings.theme.name
            values[Keys.LANGUAGE] = settings.languageTag
            values[Keys.NOTIFICATIONS] = settings.notificationsEnabled
            values[Keys.RELEASE_DUE_NOTIFICATIONS] = settings.releaseDueNotificationsEnabled
            values[Keys.WATCH_PROFILE] = settings.watchProfileId
            values[Keys.PROVIDERS] = encodeProviders(settings.preferredProviderIds)
            values[Keys.DISABLED_PROVIDERS] = encodeProviders(settings.disabledProviderIds)
            values[Keys.CALENDAR_SUB] = settings.calendarShowSub
            values[Keys.CALENDAR_DUB] = settings.calendarShowDub
            values[Keys.CALENDAR_PAST] = settings.calendarShowPast
            values[Keys.CALENDAR_FAVORITES_ONLY] = settings.calendarFavoritesOnly
            values[Keys.LIVE_DATA] = settings.liveDataEnabled
        }
    }

    override suspend fun resetUserSettings() { context.aniSentinelDataStore.edit { it.clear() } }

    override suspend fun setLiveDataEnabled(enabled: Boolean) {
        context.aniSentinelDataStore.edit { it[Keys.LIVE_DATA] = enabled }
    }

    suspend fun setFavoritesSort(value: String) {
        context.aniSentinelDataStore.edit { it[Keys.FAVORITES_SORT] = value }
    }

    private fun decodeProviders(value: String): Set<String> =
        value.split(',').filter(String::isNotBlank).toSet()

    private fun encodeProviders(ids: Set<String>): String = ids.filter(String::isNotBlank).sorted().joinToString(",")

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val RELEASE_DUE_NOTIFICATIONS = booleanPreferencesKey("release_due_notifications_enabled")
        val WATCH_PROFILE = stringPreferencesKey("watch_profile_id")
        val PROVIDERS = stringPreferencesKey("preferred_provider_ids")
        val DISABLED_PROVIDERS = stringPreferencesKey("disabled_provider_ids")
        val CALENDAR_SUB = booleanPreferencesKey("calendar_show_sub")
        val CALENDAR_DUB = booleanPreferencesKey("calendar_show_dub")
        val CALENDAR_PAST = booleanPreferencesKey("calendar_show_past")
        val CALENDAR_FAVORITES_ONLY = booleanPreferencesKey("calendar_favorites_only")
        val LIVE_DATA = booleanPreferencesKey("live_data_enabled")
        val FAVORITES_SORT = stringPreferencesKey("favorites_sort")
    }
}
