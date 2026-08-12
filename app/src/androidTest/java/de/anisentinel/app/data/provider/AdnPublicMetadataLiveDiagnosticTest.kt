package de.anisentinel.app.data.provider

import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Real anonymous metadata probe. It never calls login, player, playback, token or DRM endpoints. */
@RunWith(AndroidJUnit4::class)
class AdnPublicMetadataLiveDiagnosticTest {
    @Test fun anonymousGermanMetadataReportsWhetherExactEpisodeDatesExist() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val dao = db.aniSentinelDao()
            dao.upsertAnime(listOf(AnimeEntity(
                "adn-live-diagnostic", null, null, "ADN Live Diagnostic", null, null, null,
                "", null, null, null, null, null, System.currentTimeMillis() / 1000
            )))
            val result = AdnHistoricalReleaseImporter(dao).diagnoseAndImport("adn-live-diagnostic", "1133")
            Log.i("AniSentinelADN", result.toString())
            assertFalse(result.result.isBlank())
        } finally { db.close() }
    }
}
