package de.anisentinel.app.data.anisearch

import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import de.anisentinel.app.data.settings.SourceCooldownStore

@RunWith(RobolectricTestRunner::class)
class AniSearchHttpTransportTest {
    @Before fun resetCooldown() {
        AniSearchHttpTransport.resetGlobalCooldownForTests()
        runBlocking {
            SourceCooldownStore(ApplicationProvider.getApplicationContext()).clear("anisearch")
        }
    }

    @Test fun transparentUserAgentAndMinimalHeadersAreUsed() {
        val headers = PersistentAniSearchWebSession.requestHeaders("0.25.16")
        assertEquals(
            "AniSentinel/0.25.16 (+https://github.com/schmitt627235-prog/AniSentinel)",
            headers["User-Agent"]
        )
        assertEquals("text/html", headers["Accept"])
        assertEquals("de-DE,de;q=0.9,en;q=0.7", headers["Accept-Language"])
        assertEquals(setOf("User-Agent", "Accept", "Accept-Language"), headers.keys)
    }

    @Test fun searchEncodesTitleAndUsesPublicAnimeIndex() = runBlocking {
        val requested = mutableListOf<String>()
        val transport = transport(session = AniSearchWebSession { url ->
            requested += url
            AniSearchHttpResponse(200, "<html>ok</html>")
        }, interval = 0)
        transport.searchAnime("Frieren & Fern")
        assertEquals("https://www.anisearch.de/anime/index", requested.first())
        assertTrue(requested.last().contains("text=Frieren+%26+Fern"))
    }

    @Test fun persistentSessionKeepsCookieBetweenRequests() {
        val session = PersistentAniSearchWebSession()
        val uri = URI("https://www.anisearch.de/anime/index")
        session.absorbCookies(uri, mapOf("Set-Cookie" to listOf("anisession=abc; Path=/")))
        val outgoing = session.requestCookieHeaders(URI("https://www.anisearch.de/anime/123,test"))
        assertTrue(outgoing.values.flatten().any { it.contains("anisession=abc") })
    }

    @Test fun warmupSearchAndDetailReuseOneSession() = runBlocking {
        val requested = mutableListOf<String>()
        val session = AniSearchWebSession { url ->
            requested += url
            AniSearchHttpResponse(200, "<html>ok</html>")
        }
        val transport = transport(session, interval = 0)
        transport.searchAnime("Session Test ${System.nanoTime()}")
        transport.fetchDetail("https://www.anisearch.de/anime/987654,session-test")
        assertEquals(1, requested.count { it == "https://www.anisearch.de/anime/index" })
        assertEquals(3, requested.size)
    }

    @Test fun sixSecondMinimumAppliesToEveryRealRequest() = runBlocking {
        val waits = mutableListOf<Long>()
        val transport = transport(
            session = AniSearchWebSession { AniSearchHttpResponse(200, "<html>ok</html>") },
            interval = 6_000,
            sleeper = { waits += it }
        )
        transport.searchAnime("spacing-${System.nanoTime()}")
        transport.searchAnime("spacing-second-${System.nanoTime()}")
        assertEquals(listOf(6_000L, 6_000L), waits)
    }

    @Test fun requestMutexPreventsParallelAniSearchNetworkCalls() = runBlocking {
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val session = AniSearchWebSession {
            val current = active.incrementAndGet()
            maximum.updateAndGet { old -> maxOf(old, current) }
            delay(10)
            active.decrementAndGet()
            AniSearchHttpResponse(200, "<html>ok</html>")
        }
        val transport = transport(session, interval = 0)
        val first = async { transport.searchAnime("parallel-a-${System.nanoTime()}") }
        val second = async { transport.searchAnime("parallel-b-${System.nanoTime()}") }
        first.await(); second.await()
        assertEquals(1, maximum.get())
    }

    @Test fun rateLimitStopsQueueAndUsesRetryAfter() = runBlocking {
        var requests = 0
        val store = SourceCooldownStore(ApplicationProvider.getApplicationContext())
        store.clear("anisearch")
        val transport = transport(
            session = AniSearchWebSession { requests++; AniSearchHttpResponse(429, "", retryAfterSeconds = 60) },
            interval = 0,
            cooldownStore = store
        )
        val result = transport.searchAnime("rate-limit-${System.nanoTime()}")
        assertTrue(result is AniSearchFetchResult.RateLimited)
        assertEquals(60L, (result as AniSearchFetchResult.RateLimited).retryAfterSeconds)
        assertEquals(AniSearchRateLimitOrigin.REMOTE_HTTP_429, result.origin)
        assertEquals(result.nextAllowedAt, store.nextAllowedAt("anisearch"))
        val second = transport.searchAnime("second-title-${System.nanoTime()}") as AniSearchFetchResult.RateLimited
        assertEquals(AniSearchRateLimitOrigin.LOCAL_COOLDOWN, second.origin)
        assertEquals(1, requests)
    }

    @Test fun manualDebugResetAllowsExactlyOneNewNormalRequest() = runBlocking {
        var requests = 0
        val store = SourceCooldownStore(ApplicationProvider.getApplicationContext())
        store.clear("anisearch")
        val transport = transport(
            session = AniSearchWebSession {
                requests++
                if (requests == 1) AniSearchHttpResponse(429, "", retryAfterSeconds = 60)
                else AniSearchHttpResponse(200, "<html>ok</html>")
            },
            interval = 0,
            cooldownStore = store,
            warmUpEnabled = false
        )
        val first = transport.searchAnime("reset-first-${System.nanoTime()}") as AniSearchFetchResult.RateLimited
        assertEquals(AniSearchRateLimitOrigin.REMOTE_HTTP_429, first.origin)
        val blocked = transport.searchAnime("reset-blocked-${System.nanoTime()}") as AniSearchFetchResult.RateLimited
        assertEquals(AniSearchRateLimitOrigin.LOCAL_COOLDOWN, blocked.origin)
        assertEquals(1, requests)
        assertTrue(transport.clearAniSearchCooldownForDebug())
        assertEquals(0L, store.nextAllowedAt("anisearch"))
        assertTrue(transport.searchAnime("reset-allowed-${System.nanoTime()}") is AniSearchFetchResult.Success)
        assertEquals(2, requests)
    }

    @Test fun robotsDisallowedRedirectPathsAreRecognized() {
        listOf("/r/x", "/rr/x", "/redirect/x", "/usercp/x", "/images/usbndeo/x").forEach {
            assertTrue(PersistentAniSearchWebSession.isRobotsDisallowed(URI("https://www.anisearch.de$it")))
        }
        assertTrue(!PersistentAniSearchWebSession.isRobotsDisallowed(URI("https://www.anisearch.de/anime/index/")))
        assertTrue(!PersistentAniSearchWebSession.isRobotsDisallowed(URI("https://www.anisearch.de/anime/123,test")))
    }

    @Test fun retryAfterHttpDateIsSupported() {
        val clock = Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC)
        assertEquals(120L, PersistentAniSearchWebSession.parseRetryAfter("Wed, 26 Aug 2026 10:02:00 GMT", clock))
    }

    @Test fun validCacheAvoidsWarmupAndContentNetworkRequests() = runBlocking {
        var requests = 0
        val transport = AniSearchHttpTransport(
            context = ApplicationProvider.getApplicationContext(),
            session = AniSearchWebSession { requests++; AniSearchHttpResponse(200, "<html>cached</html>") },
            cacheTtlSeconds = 3_600,
            minimumRequestIntervalMillis = 0
        )
        val query = "cache-${System.nanoTime()}"
        assertTrue(transport.searchAnime(query) is AniSearchFetchResult.Success)
        val cached = transport.searchAnime(query) as AniSearchFetchResult.Success
        assertTrue(cached.fromCache)
        assertEquals(2, requests) // one warmup and one content request
    }

    private fun transport(
        session: AniSearchWebSession,
        interval: Long,
        sleeper: suspend (Long) -> Unit = {},
        cooldownStore: SourceCooldownStore? = null,
        warmUpEnabled: Boolean = true,
        debugBuild: () -> Boolean = { true }
    ) = AniSearchHttpTransport(
        context = ApplicationProvider.getApplicationContext(),
        clock = Clock.fixed(Instant.parse("2026-08-26T10:00:00Z"), ZoneOffset.UTC),
        session = session,
        cacheTtlSeconds = 0,
        minimumRequestIntervalMillis = interval,
        sleeper = sleeper,
        cooldownStore = cooldownStore,
        warmUpEnabled = warmUpEnabled,
        debugBuild = debugBuild
    )
}
