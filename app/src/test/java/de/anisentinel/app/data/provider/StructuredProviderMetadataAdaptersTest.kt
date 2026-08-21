package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StructuredProviderMetadataAdaptersTest {
    private val now = Instant.parse("2026-08-11T10:00:00Z")
    private val request = ProviderMetadataProbeRequest("anime-1", "Demon King Daimao", 1, 6, "GER_SUB")
    private val adnIdentity = ProviderMetadataIdentity("ADN", "DE", "1133")

    @Test fun adnPositiveRealResponseShapeDistinguishesGermanLanguages() {
        val result = AdnMetadataAdapter(clock = Clock.fixed(now, ZoneOffset.UTC)).parseEpisodes(
            """{"videos":[{"id":4242,"season":1,"shortNumber":"6","languages":["vostde","vde"],"available":true}]}""",
            request, adnIdentity, now
        ) as ProviderMetadataProbeResult.Available
        assertTrue(result.availability.germanSubAvailable == true)
        assertTrue(result.availability.germanDubAvailable == true)
        assertEquals("4242", result.identity.episodeId)
    }

    @Test fun adnMissingEpisodeIsNotAvailableYetNotTechnicalFailure() {
        val result = AdnMetadataAdapter().parseEpisodes(
            """{"videos":[{"id":4242,"season":1,"shortNumber":12,"languages":["vostde","vde"]}]}""",
            request.copy(episodeNumber = 13), adnIdentity, now
        )
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun adnMissingRequestedDubIsNotAvailableYet() {
        val result = AdnMetadataAdapter().parseEpisodes(
            """{"videos":[{"id":4242,"season":1,"shortNumber":6,"languages":["vostde"]}]}""",
            request.copy(expectedLanguage = "GER_DUB"), adnIdentity, now
        )
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun adnFuturePlaceholderIsNotAvailabilityEvidence() {
        val result = AdnMetadataAdapter().parseEpisodes(
            """{"videos":[{"id":4242,"season":1,"shortNumber":6,"languages":["vostde"],"available":false,"availableAt":"2026-08-17T10:00:00Z"}]}""",
            request, adnIdentity, now
        )
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun adnMapsTitleWideSeasonTwoNumbersToLocalEpisodes() {
        val body = """{"videos":[
            {"id":30670,"season":2,"shortNumber":13,"languages":["vostde"],"available":true},
            {"id":30676,"season":2,"shortNumber":19,"languages":["vostde"],"available":true},
            {"id":30677,"season":2,"shortNumber":20,"languages":["vostde"],"available":false,"availableAt":"2026-08-21T16:30:00Z"}
        ]}"""
        val published = AdnMetadataAdapter().parseEpisodes(
            body, request.copy(seasonNumber = 2, episodeNumber = 7), adnIdentity, now
        ) as ProviderMetadataProbeResult.Available
        assertEquals("30676", published.identity.episodeId)
        assertEquals(7, published.availability.episodeNumber)

        val upcoming = AdnMetadataAdapter().parseEpisodes(
            body, request.copy(seasonNumber = 2, episodeNumber = 8), adnIdentity, now
        )
        assertTrue(upcoming is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun adnRejectsNonGermanMarketBeforeNetwork() = runBlocking {
        var called = false
        val adapter = AdnMetadataAdapter(ProviderMetadataTransport { _, _ -> called = true; MetadataHttpResponse(200, "{}", "x") }, Clock.fixed(now, ZoneOffset.UTC))
        val result = adapter.probe(request.copy(market = "FR"), null)
        assertTrue(result is ProviderMetadataProbeResult.CheckFailed)
        assertFalse(called)
    }

    @Test fun crunchyrollAnonymousAuthFailureIsCheckFailed() = runBlocking {
        val catalog = CrunchyrollAnonymousCatalogClient(CrunchyrollCatalogTransport {
            MetadataHttpResponse(401, "{\"code\":\"content.error.invalid_auth_token\"}", it)
        })
        val adapter = CrunchyrollMetadataAdapter(
            ProviderMetadataTransport { _, _ -> MetadataHttpResponse(401, "{\"code\":\"content.error.invalid_auth_token\"}", "https://www.crunchyroll.com/content/v2") },
            Clock.fixed(now, ZoneOffset.UTC),
            catalog
        )
        val result = adapter.probe(request, ProviderMetadataIdentity("CRUNCHYROLL", "DE", "series", "season"))
        assertEquals("CRUNCHYROLL_CATALOG_HTTP_401", (result as ProviderMetadataProbeResult.CheckFailed).code)
    }

    @Test fun crunchyrollFixtureSeparatesEpisodeAndLanguageAbsence() {
        val adapter = CrunchyrollMetadataAdapter()
        val identity = ProviderMetadataIdentity("CRUNCHYROLL", "DE", "series", "season")
        val body = """{"items":[{"id":"G6","episode_number":6,"audio_locale":"ja-JP","subtitle_locales":["de-DE"]}]}"""
        assertTrue(adapter.parseEpisodes(body, request, identity, now, "https://example") is ProviderMetadataProbeResult.Available)
        assertTrue(adapter.parseEpisodes(body, request.copy(expectedLanguage="GER_DUB"), identity, now, "https://example") is ProviderMetadataProbeResult.NotAvailableYet)
        assertTrue(adapter.parseEpisodes(body, request.copy(episodeNumber=7), identity, now, "https://example") is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun publicWebExtractsStableSeriesIdWithoutTitleHardcode() {
        assertEquals("G6NQ5DWZ6", CrunchyrollPublicWebAdapter.crunchyrollSeriesId(
            "https://www.crunchyroll.com/de/series/G6NQ5DWZ6/example-title"
        ))
        assertNull(CrunchyrollPublicWebAdapter.crunchyrollSeriesId("https://www.crunchyroll.com/de/videos/new"))
    }

    @Test fun publicWebExtractsObservedSeriesIdsAcrossLocaleSlugAndEmbeddedJsonForms() {
        val examples = mapOf(
            "/de/series/GT00378099/red-river" to "GT00378099",
            "/series/GT00378126/victoria-of-many-faces" to "GT00378126",
            "https://www.crunchyroll.com/series/GT00378125" to "GT00378125",
            "{\"series_id\":\"GT00374354\"}" to "GT00374354"
        )
        examples.forEach { (reference, expected) ->
            assertEquals(expected, CrunchyrollPublicWebAdapter.crunchyrollSeriesId(reference))
            assertEquals("https://www.crunchyroll.com/de/series/$expected", CrunchyrollPublicWebAdapter.canonicalSeriesUrl(expected))
        }
    }

    @Test fun publicWebMapsParsedMissingEpisodeToNotAvailableYet() = runBlocking {
        val checker = object : ProviderEpisodeChecker {
            override suspend fun checkEpisode(providerId: String, title: String, seasonNumber: Int?, episodeNumber: Int, expectedLanguage: String?, providerUrl: String?, expectedAt: Instant?) =
                ProviderEpisodeCheckResult.Checked(ProviderEpisodeAvailability("CRUNCHYROLL", seasonNumber, episodeNumber, false, null, null, null, providerUrl, now, "PUBLIC_WEB", providerUrl))
        }
        val adapter = CrunchyrollPublicWebAdapter(checker, Clock.fixed(now, ZoneOffset.UTC))
        val result = adapter.probe(request.copy(providerUrl="https://www.crunchyroll.com/de/series/G6NQ5DWZ6/example"), null)
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun publicWebDoesNotInventDubFromEpisodePresence() = runBlocking {
        val checker = object : ProviderEpisodeChecker {
            override suspend fun checkEpisode(providerId: String, title: String, seasonNumber: Int?, episodeNumber: Int, expectedLanguage: String?, providerUrl: String?, expectedAt: Instant?) =
                ProviderEpisodeCheckResult.Checked(ProviderEpisodeAvailability("CRUNCHYROLL", seasonNumber, episodeNumber, true, true, false, null, providerUrl, now, "PUBLIC_WEB", providerUrl))
        }
        val adapter = CrunchyrollPublicWebAdapter(checker)
        val result = adapter.probe(request.copy(expectedLanguage="GER_DUB", providerUrl="https://www.crunchyroll.com/de/series/G6NQ5DWZ6/example"), null)
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun videosNewDubEntryIsSignalOnlyAndMissingExpectedEpisodeRemainsNotAvailable() = runBlocking {
        val signals = CrunchyrollNewReleaseSignalParser.parse(
            """<article><a href="/de/series/G6NQ5DWZ6/old-anime">Old Anime</a><span>Untertitel | Synchro</span></article>""",
            "https://www.crunchyroll.com/de/videos/new", now
        )
        assertEquals(1, signals.size)
        assertEquals("Untertitel | Synchro", signals.single().visibleLanguageLabel)
        val checker = object : ProviderEpisodeChecker {
            override suspend fun checkEpisode(providerId: String, title: String, seasonNumber: Int?, episodeNumber: Int, expectedLanguage: String?, providerUrl: String?, expectedAt: Instant?) =
                ProviderEpisodeCheckResult.Checked(ProviderEpisodeAvailability("CRUNCHYROLL", seasonNumber, episodeNumber, false, null, null, null, providerUrl, now, "PUBLIC_WEB", providerUrl))
        }
        val result = CrunchyrollPublicWebAdapter(checker).probe(
            request.copy(title="Old Anime", seasonNumber=2, episodeNumber=6, expectedLanguage="GER_SUB", providerUrl=signals.single().seriesUrl), null
        )
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }
}
