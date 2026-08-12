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
            watchProfileId = values[Keys.WATCH_PROFILE] ?: "automatic",
            preferredProviderIds = decodeProviders(values[Keys.PROVIDERS].orEmpty()),
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

    override suspend fun setWatchProfileId(id: String) {
        require(id.isNotBlank())
        context.aniSentinelDataStore.edit { it[Keys.WATCH_PROFILE] = id }
    }

    override suspend fun setPreferredProviders(ids: Set<String>) {
        context.aniSentinelDataStore.edit {
            it[Keys.PROVIDERS] = ids.filter(String::isNotBlank).sorted().joinToString(",")
        }
    }

    override suspend fun setLiveDataEnabled(enabled: Boolean) {
        context.aniSentinelDataStore.edit { it[Keys.LIVE_DATA] = enabled }
    }

    suspend fun setFavoritesSort(value: String) {
        context.aniSentinelDataStore.edit { it[Keys.FAVORITES_SORT] = value }
    }

    private fun decodeProviders(value: String): Set<String> =
        value.split(',').filter(String::isNotBlank).toSet()

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val LANGUAGE = stringPreferencesKey("language")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val WATCH_PROFILE = stringPreferencesKey("watch_profile_id")
        val PROVIDERS = stringPreferencesKey("preferred_provider_ids")
        val LIVE_DATA = booleanPreferencesKey("live_data_enabled")
        val FAVORITES_SORT = stringPreferencesKey("favorites_sort")
    }
}
