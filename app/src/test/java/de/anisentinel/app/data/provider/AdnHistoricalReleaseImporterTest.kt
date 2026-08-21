package de.anisentinel.app.data.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.anisentinel.app.data.local.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdnHistoricalReleaseImporterTest {
    private lateinit var db: AniSentinelDatabase
    private lateinit var dao: AniSentinelDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.aniSentinelDao()
    }

    @After fun close() = db.close()

    @Test fun realMetadataShapePersistsHistoryButNeverEntersWatcherQueries() = runBlocking {
        dao.upsertAnime(listOf(AnimeEntity(
            "adn-anime", null, null, "ADN Anime", null, null, null, "", null, null,
            null, null, null, 1
        )))
        dao.upsertFavorite(FavoriteEntity("adn-anime", true, "BOTH", "automatic", true, true, true, 1))
        val transport = ProviderMetadataTransport { url, _ -> MetadataHttpResponse(200, FIXTURE, url, "application/json") }
        val importer = AdnHistoricalReleaseImporter(
            dao, transport, Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC)
        )
        val result = importer.diagnoseAndImport("adn-anime", "1133")
        assertEquals("IMPORTED", result.result)
        assertEquals(2, result.imported)
        assertEquals(2, dao.observeEpisodeReleasesForAnime("adn-anime").first().size)
        assertTrue(dao.observeEpisodeReleasesForAnime("adn-anime").first().all { it.isHistoricalImport && it.metadataSource == "ADN_PUBLIC_METADATA" })
        assertEquals(
            listOf(1),
            dao.observeAnimeSeasons("adn-anime").first().map { it.canonicalSeasonNumber }
        )
        assertEquals(
            listOf("ADN"),
            dao.observeProviderSeasonMappings("adn-anime").first()
                .filter { it.available && it.region == "DE" }
                .map { it.provider }
        )
        assertTrue(dao.dueFavoriteReleases(Instant.now().epochSecond, 0).isEmpty())
        assertTrue(dao.scheduledReleaseNotifications().isEmpty())
    }

    private companion object {
        const val FIXTURE = """{"videos":[{"id":"ep-1","shortNumber":1,"season":1,"name":"Folge 1","languages":["vostde","vde"],"releaseDate":"2025-03-03"}]}"""
    }
}
