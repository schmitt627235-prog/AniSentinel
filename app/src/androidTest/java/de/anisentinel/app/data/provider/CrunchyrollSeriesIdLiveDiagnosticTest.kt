package de.anisentinel.app.data.provider

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDatabase
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Anonymous public-page diagnostic. It never requests playback, auth, manifests or DRM data. */
@RunWith(AndroidJUnit4::class)
class CrunchyrollSeriesIdLiveDiagnosticTest {
    private fun anonymousCatalogRequest(url: String, token: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 15_000; c.readTimeout = 25_000
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "application/json")
            check(c.responseCode in 200..299) { "HTTP ${c.responseCode}: ${c.errorStream?.bufferedReader()?.readText()}" }
            c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun anonymousToken(deviceId: String): String {
        val c = URL("https://www.crunchyroll.com/auth/v1/token").openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"; c.doOutput = true
            c.connectTimeout = 15_000; c.readTimeout = 25_000
            c.setRequestProperty("Authorization", "Basic dC1rZGdwMmg4YzNqdWI4Zm4wZnE6eWZMRGZNZnJZdktYaDRKWFMxTEVJMmNDcXUxdjVXYW4=")
            c.setRequestProperty("ETP-Anonymous-ID", deviceId)
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val form = "grant_type=client_id&scope=offline_access&device_id=${URLEncoder.encode(deviceId, "UTF-8")}&device_type=Android"
            c.outputStream.use { it.write(form.toByteArray()) }
            check(c.responseCode in 200..299) { "token HTTP ${c.responseCode}: ${c.errorStream?.bufferedReader()?.readText()}" }
            JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getString("access_token")
        } finally { c.disconnect() }
    }

    @Test fun fourObservedGermanSeriesUrlsRemainResolvableByStableId() = runBlocking {
        val examples = linkedMapOf(
            "https://www.crunchyroll.com/de/series/GT00378099/red-river" to "GT00378099",
            "https://www.crunchyroll.com/de/series/GT00378126/victoria-of-many-faces" to "GT00378126",
            "https://www.crunchyroll.com/de/series/GT00378125/the-oblivious-saint-cant-contain-her-power" to "GT00378125",
            "https://www.crunchyroll.com/de/series/GT00374354/i-want-to-love-you-till-your-dying-day" to "GT00374354"
        )
        val transport = PublicProviderMetadataTransport()
        examples.forEach { (url, expectedId) ->
            assertEquals(expectedId, CrunchyrollPublicWebAdapter.crunchyrollSeriesId(url))
            val response = transport.get(url, mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/138.0 Mobile Safari/537.36 AniSentinel/0.24.6"
            ))
            if (expectedId == "GT00378125") {
                File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "crunchyroll-GT00378125.html")
                    .writeText(response.body)
            }
            Log.i("AniSentinelCrunchyroll", "id=$expectedId status=${response.status} final=${response.finalUrl} bytes=${response.body.length} idInBody=${response.body.contains(expectedId)}")
            assertTrue("HTTP ${response.status} for $url", response.status in 200..399)
        }
    }

    @Test fun anonymousCatalogReturnsSeasonsAndEpisodesWithoutUserLogin() = runBlocking {
        val token = anonymousToken(UUID.randomUUID().toString())
        val seasons = anonymousCatalogRequest(
            "https://www.crunchyroll.com/content/v2/cms/series/GT00378125/seasons?locale=de-DE", token
        )
        val seasonId = JSONObject(seasons).getJSONArray("data").getJSONObject(0).getString("id")
        val episodes = anonymousCatalogRequest(
            "https://www.crunchyroll.com/content/v2/cms/seasons/$seasonId/episodes?locale=de-DE", token
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.cacheDir, "crunchyroll-seasons.json").writeText(seasons)
        File(context.cacheDir, "crunchyroll-episodes.json").writeText(episodes)
        Log.i("AniSentinelCrunchyroll", "anonymousCatalog seasonId=$seasonId seasonsBytes=${seasons.length} episodesBytes=${episodes.length}")
        assertTrue(JSONObject(episodes).getJSONArray("data").length() > 0)
    }

    @Test fun resolvesUnprovidedWatchUrlAndLoadsFullSeriesGenerically() = runBlocking {
        // Deliberately not one of the four series links supplied by the user.
        val client = CrunchyrollAnonymousCatalogClient()
        val seriesId = client.resolveSeries(
            "https://www.crunchyroll.com/watch/GE00377909DEDE",
            "BLACK TORCH"
        )
        assertTrue("Watch URL was not resolved to a series", !seriesId.isNullOrBlank())
        val catalog = client.loadSeries(requireNotNull(seriesId))
        val titleOnlySeriesId = client.resolveSeries(null, "BLACK TORCH")
        assertEquals(seriesId, titleOnlySeriesId)
        Log.i(
            "AniSentinelCrunchyroll",
            "unknownTitle=BLACK TORCH seriesId=$seriesId seasons=${catalog.episodes.map { it.seasonId }.distinct().size} " +
                "episodes=${catalog.episodes.size} germanRows=${catalog.episodes.count { it.releaseLanguages.isNotEmpty() }}"
        )
        assertTrue(catalog.episodes.isNotEmpty())
        assertTrue(catalog.episodes.any { it.releaseLanguages.isNotEmpty() && it.availableAt != null })
    }

    @Test fun unprovidedTitlePersistsExactCrunchyrollHistoryInRoom() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val dao = db.aniSentinelDao()
            dao.upsertAnime(listOf(AnimeEntity(
                "cr-live-black-torch", null, null, "BLACK TORCH", null, null, null,
                "", null, null, null, null, null, System.currentTimeMillis() / 1000
            )))
            val result = CrunchyrollHistoricalReleaseImporter(dao).importFromProviderUrl(
                "cr-live-black-torch", "BLACK TORCH", "https://www.crunchyroll.com/watch/GE00377909DEDE"
            )
            assertTrue(result is HistoricalImportResult.Success)
            val rows = dao.observeEpisodeReleasesForAnime("cr-live-black-torch").first()
            Log.i("AniSentinelCrunchyroll", "blackTorchHistory rows=${rows.size} result=$result")
            assertTrue(rows.isNotEmpty())
            assertTrue(rows.all {
                it.isHistoricalImport && it.provider == "Crunchyroll" &&
                    it.releaseTimePrecision == "EXACT" && it.historicalReleasedAt != null &&
                    it.releaseLanguage in setOf("GER_SUB", "GER_DUB") &&
                    it.providerUrl?.contains("/watch/") == true
            })
        } finally { db.close() }
    }
}
