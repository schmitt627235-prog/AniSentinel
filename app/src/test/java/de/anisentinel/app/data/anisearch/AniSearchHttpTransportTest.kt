package de.anisentinel.app.data.anisearch

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AniSearchHttpTransportTest {
    @Test fun searchEncodesTitleAndUsesPublicAnimeIndex() = runBlocking {
        var requested = ""
        val transport = AniSearchHttpTransport(
            context = ApplicationProvider.getApplicationContext(),
            loader = { url -> requested = url; AniSearchHttpResponse(200, "<html>ok</html>") },
            minimumRequestIntervalMillis = 0
        )
        transport.searchAnime("Frieren & Fern")
        assertTrue(requested.startsWith("https://www.anisearch.de/anime/index/"))
        assertTrue(requested.contains("text=Frieren+%26+Fern"))
    }

    @Test fun rateLimitReturnsImmediatelyWithoutRetry() = runBlocking {
        var requests = 0
        val transport = AniSearchHttpTransport(
            context = ApplicationProvider.getApplicationContext(),
            loader = { requests++; AniSearchHttpResponse(429, "", retryAfterSeconds = 60) },
            minimumRequestIntervalMillis = 0
        )
        val result = transport.searchAnime("rate-limit-${System.nanoTime()}")
        assertTrue(result is AniSearchFetchResult.RateLimited)
        assertEquals(1, requests)
    }
}
