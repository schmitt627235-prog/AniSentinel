package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.ProviderMetadataIdentity
import de.anisentinel.app.domain.provider.ProviderMetadataProbeRequest
import de.anisentinel.app.domain.provider.ProviderMetadataProbeResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicProviderEpisodeAdaptersTest {
    private val now = Instant.parse("2026-08-16T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val subRequest = ProviderMetadataProbeRequest("anime", "Example", 2, 6, "GER_SUB")

    @Test fun netflixUsesJustWatchResolvedProviderUrlAndConfirmsConcreteEpisode() = runBlocking {
        val html = """<html><body><h1>Example</h1><section>Staffel 2 Episode 6 · Deutsche Untertitel</section></body></html>"""
        val adapter = NetflixPublicEpisodeAdapter(transport(html, "https://www.netflix.com/title/82760630"), clock)
        val result = adapter.probe(subRequest.copy(providerUrl = "https://www.netflix.com/title/82760630"), null)
        assertTrue(result is ProviderMetadataProbeResult.Available)
        assertEquals("82760630", (result as ProviderMetadataProbeResult.Available).identity.seriesId)
    }

    @Test fun netflixTitlePresenceAloneNeverConfirmsEpisode() = runBlocking {
        val html = """<html><body><h1>Example</h1><p>Anime 2026</p></body></html>"""
        val result = NetflixPublicEpisodeAdapter(transport(html, "https://www.netflix.com/title/82760630"), clock)
            .probe(subRequest.copy(providerUrl = "https://www.netflix.com/title/82760630"), null)
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun disneyPersistsEntityAndProviderSeasonMapping() = runBlocking {
        val id = "bb33d0c2-b077-4bc0-a549-d2ca27d4afa8"
        val html = """<html><body><h1>Medalist</h1><div>S2:F6 Deutsche Untertitel</div></body></html>"""
        val result = DisneyPlusPublicEpisodeAdapter(transport(html, "https://www.disneyplus.com/de-de/browse/entity-$id"), clock)
            .probe(subRequest.copy(title = "Medalist", providerUrl = "https://www.disneyplus.com/de-de/browse/entity-$id"), null)
        assertTrue(result is ProviderMetadataProbeResult.Available)
        val identity = (result as ProviderMetadataProbeResult.Available).identity
        assertEquals(id, identity.seriesId)
        assertEquals(2, identity.seasonNumber)
    }

    @Test fun aniverseRequiresChannelAndConcreteEpisode() = runBlocking {
        val url = "https://www.primevideo.com/-/de/detail/0JMTO64DI3TE8B5CP74DUI1UAP?tr=de"
        val html = """<html><body><h1>Akane-banashi</h1><p>aniverse kostenlos testen</p><div>Staffel 2 Folge 6 · Deutsche Synchro</div></body></html>"""
        val result = AniversePublicEpisodeAdapter(transport(html, url), clock)
            .probe(subRequest.copy(expectedLanguage = "GER_DUB", providerUrl = url), null)
        assertTrue(result is ProviderMetadataProbeResult.Available)
    }

    @Test fun primeEpisodeFromAnotherChannelDoesNotConfirmAniverse() = runBlocking {
        val url = "https://www.primevideo.com/-/de/detail/0JMTO64DI3TE8B5CP74DUI1UAP?tr=de"
        val html = """<html><body><div>Staffel 2 Folge 6 · Deutsche Synchro</div><p>Crunchyroll Channel</p></body></html>"""
        val result = AniversePublicEpisodeAdapter(transport(html, url), clock)
            .probe(subRequest.copy(expectedLanguage = "GER_DUB", providerUrl = url), null)
        assertTrue(result is ProviderMetadataProbeResult.NotAvailableYet)
    }

    @Test fun specificProviderEpisodeLinkOutranksSeriesLink() {
        val html = """<a href="https://provider.example/episode/6">S2 E6 Deutsche Untertitel</a>"""
        val result = PublicEpisodePageParser.parse(
            "TEST", html, subRequest, ProviderMetadataIdentity("TEST", "DE", "series", sourceUrl = "https://provider.example/series"),
            now, "https://provider.example/series"
        ) as ProviderMetadataProbeResult.Available
        assertEquals("https://provider.example/episode/6", result.availability.episodeUrl)
    }

    private fun transport(body: String, finalUrl: String) = ProviderMetadataTransport { _, _ ->
        MetadataHttpResponse(200, body, finalUrl, "text/html")
    }
}
