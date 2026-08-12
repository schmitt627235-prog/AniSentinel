package de.anisentinel.app.data.provider

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CrunchyrollAnonymousCatalogClientTest {
    private val transport = CrunchyrollCatalogTransport { url ->
        when {
            "/objects/GEWATCHDE" in url -> response(url, """{"data":[{"series_id":"GSERIES"}]}""")
            "/series/GSERIES/seasons" in url -> response(url, """{"data":[
                {"id":"S-JA","season_number":2,"audio_locale":"ja-JP"},
                {"id":"S-DE","season_number":2,"audio_locale":"de-DE"}
            ]}""")
            "/seasons/S-JA/episodes" in url -> response(url, """{"data":[
                {"id":"E-SUB","season_number":2,"episode_number":6,"title":"Sub",
                 "audio_locale":"ja-JP","subtitle_locales":["de-DE"],
                 "premium_available_date":"2026-08-09T15:30:00Z","availability_status":"premium_only"},
                {"id":"E-PLACEHOLDER","season_number":2,"episode_number":7,
                 "audio_locale":"ja-JP","subtitle_locales":["de-DE"],
                 "premium_available_date":"9998-11-30T17:45:00Z"}
            ]}""")
            "/seasons/S-DE/episodes" in url -> response(url, """{"data":[
                {"id":"E-DUB","season_number":2,"episode_number":3,"title":"Dub",
                 "audio_locale":"de-DE","subtitle_locales":[],
                 "premium_available_date":"2026-08-10T17:00:00Z","availability_status":"premium_only"}
            ]}""")
            else -> MetadataHttpResponse(404, "{}", url, "application/json")
        }
    }

    @Test fun resolvesWatchIdWithoutTitleHardcodeAndKeepsSubDubSeparate() = runBlocking {
        val client = CrunchyrollAnonymousCatalogClient(transport)
        assertEquals("GSERIES", client.resolveSeries("https://www.crunchyroll.com/watch/GEWATCHDE", "Anything"))
        val rows = client.loadSeries("GSERIES").episodes
        assertEquals(setOf("GER_SUB"), rows.single { it.episodeId == "E-SUB" }.releaseLanguages)
        assertEquals(setOf("GER_DUB"), rows.single { it.episodeId == "E-DUB" }.releaseLanguages)
        assertEquals(6, rows.single { it.episodeId == "E-SUB" }.episodeNumber)
    }

    @Test fun rejectsSentinelDatesInsteadOfInventingHistory() = runBlocking {
        val row = CrunchyrollAnonymousCatalogClient(transport).loadSeries("GSERIES").episodes
            .single { it.episodeId == "E-PLACEHOLDER" }
        assertNull(row.availableAt)
        assertTrue("GER_SUB" in row.releaseLanguages)
    }

    private fun response(url: String, body: String) = MetadataHttpResponse(200, body, url, "application/json")
}
