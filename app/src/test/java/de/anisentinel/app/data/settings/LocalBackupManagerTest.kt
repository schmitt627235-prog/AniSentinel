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

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AniSentinelDatabase::class.java)
            .allowMainThreadQueries().build()
        settings = DataStoreSettingsRepository(context)
        settings.resetUserSettings()
        manager = LocalBackupManager(settings, database.aniSentinelDao())
    }

    @After
    fun tearDown() = runBlocking {
        settings.resetUserSettings()
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
