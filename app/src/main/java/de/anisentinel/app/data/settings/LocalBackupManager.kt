package de.anisentinel.app.data.settings

import android.content.Context
import de.anisentinel.app.BuildConfig
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.FavoriteEntity
import de.anisentinel.app.domain.provider.ProviderVisibilityPolicy
import de.anisentinel.app.domain.repository.AppSettings
import de.anisentinel.app.domain.repository.ThemePreference
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

enum class BackupSection(val wireName: String) {
    FAVORITES("favorites"), GENERAL("generalSettings"), NOTIFICATIONS("notificationSettings"),
    CALENDAR("calendarSettings"), PROVIDERS("providerSettings"), WATCH_PROFILE("watchProfile"),
    THEME_LANGUAGE("themeLanguage"), ANTICIPATED_TITLES("anticipatedTitles");
    companion object {
        val all = entries.toSet()
        fun fromWire(value: String) = entries.firstOrNull { it.wireName == value }
    }
}

data class BackupPreview(val createdAt: String, val sections: Set<BackupSection>, internal val data: JSONObject)

sealed interface BackupResult {
    data class Success(val favorites: Int) : BackupResult
    data class Preview(val backup: BackupPreview) : BackupResult
    data class Invalid(val reason: String) : BackupResult
}

class LocalBackupManager(
    private val context: Context,
    private val settingsRepository: DataStoreSettingsRepository,
    private val dao: AniSentinelDao
) {
    private val anticipatedCache get() = context.getSharedPreferences("anticipated_titles_cache", Context.MODE_PRIVATE)

    suspend fun export(output: OutputStream, sections: Set<BackupSection>): BackupResult {
        if (sections.isEmpty()) return BackupResult.Invalid("NO_SECTIONS_SELECTED")
        val settings = settingsRepository.settings.first()
        val favorites = if (BackupSection.FAVORITES in sections) dao.activeFavorites() else emptyList()
        val data = JSONObject()
        if (BackupSection.FAVORITES in sections) data.put("favorites", JSONArray().apply { favorites.forEach { put(it.json()) } })
        if (BackupSection.GENERAL in sections) data.put("generalSettings", JSONObject().put("liveDataEnabled", settings.liveDataEnabled))
        if (BackupSection.NOTIFICATIONS in sections) data.put("notificationSettings", JSONObject()
            .put("notificationsEnabled", settings.notificationsEnabled)
            .put("releaseDueNotificationsEnabled", settings.releaseDueNotificationsEnabled))
        if (BackupSection.CALENDAR in sections) data.put("calendarSettings", JSONObject()
            .put("showSub", settings.calendarShowSub).put("showDub", settings.calendarShowDub)
            .put("showPast", settings.calendarShowPast).put("favoritesOnly", settings.calendarFavoritesOnly))
        if (BackupSection.PROVIDERS in sections) data.put("providerSettings", JSONObject()
            .put("preferredProviderIds", JSONArray(settings.preferredProviderIds.toList()))
            .put("disabledProviderIds", JSONArray(settings.disabledProviderIds.toList())))
        if (BackupSection.WATCH_PROFILE in sections) data.put("watchProfile", JSONObject().put("watchProfileId", settings.watchProfileId))
        if (BackupSection.THEME_LANGUAGE in sections) data.put("themeLanguage", JSONObject()
            .put("theme", settings.theme.name).put("languageTag", settings.languageTag))
        if (BackupSection.ANTICIPATED_TITLES in sections) data.put("anticipatedTitles", JSONObject().apply {
            anticipatedCache.getString("json_v3", null)?.let { put("json", it) }
            put("storedAt", anticipatedCache.getLong("stored_at_v3", 0L))
        })
        val root = JSONObject().put("schemaVersion", 1).put("app", "AniSentinel")
            .put("appVersion", BuildConfig.VERSION_NAME).put("createdAt", Instant.now().toString())
            .put("includedSections", JSONArray(sections.sortedBy { it.ordinal }.map { it.wireName })).put("data", data)
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
        return BackupResult.Success(favorites.size)
    }

    fun inspect(input: InputStream): BackupResult {
        val root = runCatching { JSONObject(input.bufferedReader(Charsets.UTF_8).use { it.readText() }) }
            .getOrElse { return BackupResult.Invalid("INVALID_JSON") }
        if (root.optInt("schemaVersion", -1) != 1 || root.optString("app") != "AniSentinel") return BackupResult.Invalid("UNSUPPORTED_SCHEMA")
        val created = root.optString("createdAt").takeIf(String::isNotBlank) ?: return BackupResult.Invalid("MISSING_CREATED_AT")
        val array = root.optJSONArray("includedSections") ?: return BackupResult.Invalid("MISSING_SECTIONS")
        val sections = buildSet {
            for (i in 0 until array.length()) add(BackupSection.fromWire(array.optString(i)) ?: return BackupResult.Invalid("UNKNOWN_SECTION"))
        }
        if (sections.isEmpty()) return BackupResult.Invalid("NO_SECTIONS_SELECTED")
        val data = root.optJSONObject("data") ?: return BackupResult.Invalid("MISSING_DATA")
        if (!validate(data, sections)) return BackupResult.Invalid("INVALID_SECTION_DATA")
        return BackupResult.Preview(BackupPreview(created, sections, data))
    }

    suspend fun restore(preview: BackupPreview, requested: Set<BackupSection>): BackupResult {
        val selected = requested intersect preview.sections
        if (selected.isEmpty()) return BackupResult.Invalid("NO_SECTIONS_SELECTED")
        var settings: AppSettings = settingsRepository.settings.first()
        if (BackupSection.GENERAL in selected) settings = settings.copy(liveDataEnabled = preview.data.getJSONObject("generalSettings").getBoolean("liveDataEnabled"))
        if (BackupSection.NOTIFICATIONS in selected) preview.data.getJSONObject("notificationSettings").let {
            settings = settings.copy(notificationsEnabled = it.getBoolean("notificationsEnabled"), releaseDueNotificationsEnabled = it.getBoolean("releaseDueNotificationsEnabled"))
        }
        if (BackupSection.CALENDAR in selected) preview.data.getJSONObject("calendarSettings").let {
            settings = settings.copy(calendarShowSub = it.getBoolean("showSub"), calendarShowDub = it.getBoolean("showDub"), calendarShowPast = it.getBoolean("showPast"), calendarFavoritesOnly = it.getBoolean("favoritesOnly"))
        }
        if (BackupSection.PROVIDERS in selected) preview.data.getJSONObject("providerSettings").let {
            settings = settings.copy(preferredProviderIds = it.getJSONArray("preferredProviderIds").strings(), disabledProviderIds = it.getJSONArray("disabledProviderIds").strings())
        }
        if (BackupSection.WATCH_PROFILE in selected) settings = settings.copy(watchProfileId = preview.data.getJSONObject("watchProfile").getString("watchProfileId"))
        if (BackupSection.THEME_LANGUAGE in selected) preview.data.getJSONObject("themeLanguage").let {
            settings = settings.copy(theme = ThemePreference.valueOf(it.getString("theme")), languageTag = it.getString("languageTag"))
        }
        if (selected.any { it !in setOf(BackupSection.FAVORITES, BackupSection.ANTICIPATED_TITLES) }) settingsRepository.replaceUserSettings(settings)
        if (BackupSection.ANTICIPATED_TITLES in selected) preview.data.getJSONObject("anticipatedTitles").let { cache ->
            val json = cache.optString("json").takeIf(String::isNotBlank)
            val editor = anticipatedCache.edit().remove("json_v3").remove("stored_at_v3")
            if (json != null) editor.putString("json_v3", json).putLong("stored_at_v3", cache.getLong("storedAt"))
            editor.apply()
        }
        var count = 0
        if (BackupSection.FAVORITES in selected) preview.data.getJSONArray("favorites").let { favorites ->
            val restoredFavorites = (0 until favorites.length()).map { favorites.getJSONObject(it).favorite()!! }
            restoredFavorites.forEach { favorite ->
                // Favorites reference anime(id). A user-data backup may be restored before
                // the catalogue is downloaded, so its required parent can still be absent.
                if (dao.anime(favorite.animeId) == null) {
                    dao.upsertAnime(listOf(favorite.placeholderAnime()))
                }
                dao.upsertFavorite(favorite)
            }
            count = restoredFavorites.size
        }
        return BackupResult.Success(count)
    }

    private fun validate(data: JSONObject, sections: Set<BackupSection>) = runCatching {
        sections.forEach { section ->
            require(data.has(section.wireName))
            when (section) {
                BackupSection.FAVORITES -> data.getJSONArray("favorites").let { for (i in 0 until it.length()) requireNotNull(it.optJSONObject(i)?.favorite()) }
                BackupSection.GENERAL -> data.getJSONObject("generalSettings").getBoolean("liveDataEnabled")
                BackupSection.NOTIFICATIONS -> data.getJSONObject("notificationSettings").run { getBoolean("notificationsEnabled"); getBoolean("releaseDueNotificationsEnabled") }
                BackupSection.CALENDAR -> data.getJSONObject("calendarSettings").run { val sub=getBoolean("showSub"); val dub=getBoolean("showDub"); require(sub||dub); getBoolean("showPast"); getBoolean("favoritesOnly") }
                BackupSection.PROVIDERS -> data.getJSONObject("providerSettings").run { getJSONArray("preferredProviderIds").strings(); require(getJSONArray("disabledProviderIds").strings().all(ProviderVisibilityPolicy.supportedProviderIds::contains)) }
                BackupSection.WATCH_PROFILE -> require(data.getJSONObject("watchProfile").getString("watchProfileId").isNotBlank())
                BackupSection.THEME_LANGUAGE -> data.getJSONObject("themeLanguage").run { ThemePreference.valueOf(getString("theme")); require(getString("languageTag").isNotBlank()) }
                BackupSection.ANTICIPATED_TITLES -> data.getJSONObject("anticipatedTitles").run {
                    optString("json").takeIf(String::isNotBlank)?.let { JSONObject(it) }
                    require(getLong("storedAt") >= 0L)
                }
            }
        }
    }.isSuccess

    private fun FavoriteEntity.json() = JSONObject().put("animeId", animeId).put("enabled", enabled)
        .put("languagePreference", languagePreference).put("monitoringProfileId", monitoringProfileId)
        .put("notifyAvailable", notifyAvailable).put("notifyDelayed", notifyDelayed)
        .put("notifyPostponed", notifyPostponed).put("createdAt", createdAt)
    private fun JSONObject.favorite() = runCatching { FavoriteEntity(getString("animeId").also { require(it.isNotBlank()) }, getBoolean("enabled"), getString("languagePreference").also { require(it in setOf("SUB","DUB","BOTH")) }, optString("monitoringProfileId").takeIf(String::isNotBlank), getBoolean("notifyAvailable"), getBoolean("notifyDelayed"), getBoolean("notifyPostponed"), getLong("createdAt")) }.getOrNull()
    private fun FavoriteEntity.placeholderAnime() = AnimeEntity(
        id = animeId,
        anilistId = animeId.removePrefix("anilist-").toIntOrNull(),
        anisearchId = null,
        titleGerman = animeId,
        titleEnglish = null,
        titleRomaji = null,
        titleNative = null,
        description = "",
        coverUrl = null,
        bannerUrl = null,
        season = null,
        seasonYear = null,
        totalEpisodes = null,
        updatedAt = 0L
    )
    private fun JSONArray.strings() = buildSet { for (i in 0 until length()) add(getString(i)) }
}
