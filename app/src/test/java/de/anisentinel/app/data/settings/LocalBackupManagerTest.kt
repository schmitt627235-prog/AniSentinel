package de.anisentinel.app.data.settings

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDatabase
import de.anisentinel.app.data.local.FavoriteEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalBackupManagerTest {
    private lateinit var database: AniSentinelDatabase
    private lateinit var settings: DataStoreSettingsRepository
    private lateinit var manager: LocalBackupManager
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AniSentinelDatabase::class.java)
            .allowMainThreadQueries().build()
        settings = DataStoreSettingsRepository(context)
        settings.resetUserSettings()
        context.getSharedPreferences("anticipated_titles_cache", Context.MODE_PRIVATE).edit().clear().commit()
        manager = LocalBackupManager(context, settings, database.aniSentinelDao())
    }

    @After
    fun tearDown() = runBlocking {
        settings.resetUserSettings()
        context.getSharedPreferences("anticipated_titles_cache", Context.MODE_PRIVATE).edit().clear().commit()
        database.close()
    }

    @Test
    fun exportContainsFavoritesAndUserFilters() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(testAnime()))
        dao.upsertFavorite(testFavorite())
        settings.setDisabledProviders(setOf("netflix"))
        settings.setCalendarShowPast(false)

        val output = ByteArrayOutputStream()
        assertEquals(BackupResult.Success(1), manager.export(output, setOf(BackupSection.FAVORITES, BackupSection.PROVIDERS, BackupSection.CALENDAR)))
        val json = JSONObject(output.toString(Charsets.UTF_8.name()))
        val data = json.getJSONObject("data")

        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals(1, data.getJSONArray("favorites").length())
        assertEquals("netflix", data.getJSONObject("providerSettings").getJSONArray("disabledProviderIds").getString(0))
        assertEquals(false, data.getJSONObject("calendarSettings").getBoolean("showPast"))
        assertTrue(!data.has("notificationSettings"))
    }

    @Test
    fun invalidBackupNeverPartiallyChangesExistingData() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(testAnime()))
        dao.upsertFavorite(testFavorite())
        settings.setDisabledProviders(setOf("adn"))

        val result = manager.inspect(ByteArrayInputStream("{not-json".toByteArray()))

        assertTrue(result is BackupResult.Invalid)
        assertEquals(setOf("adn"), settings.settings.first().disabledProviderIds)
        assertEquals(listOf("backup-anime"), dao.activeFavorites().map { it.animeId })
    }

    @Test
    fun selectiveRestoreChangesOnlyRequestedSection() = runBlocking {
        settings.setDisabledProviders(setOf("netflix")); settings.setCalendarShowPast(false)
        val output = ByteArrayOutputStream()
        manager.export(output, setOf(BackupSection.PROVIDERS, BackupSection.CALENDAR))
        val preview = (manager.inspect(ByteArrayInputStream(output.toByteArray())) as BackupResult.Preview).backup
        settings.setDisabledProviders(setOf("adn")); settings.setCalendarShowPast(true)

        manager.restore(preview, setOf(BackupSection.PROVIDERS))

        assertEquals(setOf("netflix"), settings.settings.first().disabledProviderIds)
        assertTrue(settings.settings.first().calendarShowPast)
    }

    @Test
    fun emptySelectionCannotCreateBackup() = runBlocking {
        assertEquals(BackupResult.Invalid("NO_SECTIONS_SELECTED"), manager.export(ByteArrayOutputStream(), emptySet()))
    }

    @Test
    fun favoritesRestoreAfterFreshInstallCreatesMissingAnimeParent() = runBlocking {
        val backup = """
            {
              "schemaVersion": 1,
              "app": "AniSentinel",
              "createdAt": "2026-09-11T12:00:00Z",
              "includedSections": ["favorites"],
              "data": {
                "favorites": [{
                  "animeId": "anilist-12345",
                  "enabled": true,
                  "languagePreference": "BOTH",
                  "monitoringProfileId": "automatic",
                  "notifyAvailable": true,
                  "notifyDelayed": true,
                  "notifyPostponed": true,
                  "createdAt": 1
                }]
              }
            }
        """.trimIndent()
        val preview = (manager.inspect(ByteArrayInputStream(backup.toByteArray())) as BackupResult.Preview).backup

        assertEquals(BackupResult.Success(1), manager.restore(preview, setOf(BackupSection.FAVORITES)))

        val dao = database.aniSentinelDao()
        assertEquals("anilist-12345", dao.anime("anilist-12345")?.id)
        assertEquals(12345, dao.anime("anilist-12345")?.anilistId)
        assertEquals(listOf("anilist-12345"), dao.activeFavorites().map { it.animeId })
    }

    @Test
    fun anticipatedTitlesCacheSurvivesExportAndFreshInstallRestore() = runBlocking {
        val cache = context.getSharedPreferences("anticipated_titles_cache", Context.MODE_PRIVATE)
        val aniListJson = """{"data":{"Page":{"media":[{"id":12345}]}}}"""
        cache.edit().putString("json_v3", aniListJson).putLong("stored_at_v3", 1_787_602_168L).commit()
        val output = ByteArrayOutputStream()

        assertEquals(BackupResult.Success(0), manager.export(output, setOf(BackupSection.ANTICIPATED_TITLES)))
        val preview = (manager.inspect(ByteArrayInputStream(output.toByteArray())) as BackupResult.Preview).backup
        cache.edit().clear().commit()

        assertEquals(BackupResult.Success(0), manager.restore(preview, setOf(BackupSection.ANTICIPATED_TITLES)))
        assertEquals(aniListJson, cache.getString("json_v3", null))
        assertEquals(1_787_602_168L, cache.getLong("stored_at_v3", 0L))
    }

    private fun testAnime() = AnimeEntity(
        id = "backup-anime", anilistId = null, anisearchId = null, titleGerman = "Backup Anime",
        titleEnglish = null, titleRomaji = null, titleNative = null, description = "", coverUrl = null,
        bannerUrl = null, season = null, seasonYear = null, totalEpisodes = null, updatedAt = 1
    )

    private fun testFavorite() = FavoriteEntity(
        animeId = "backup-anime", enabled = true, languagePreference = "BOTH",
        monitoringProfileId = "automatic", notifyAvailable = true, notifyDelayed = true,
        notifyPostponed = true, createdAt = 1
    )
}
