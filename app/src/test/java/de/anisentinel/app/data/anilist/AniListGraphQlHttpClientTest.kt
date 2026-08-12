package de.anisentinel.app.data.anilist

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListGraphQlHttpClientTest {
    private val clock = Clock.fixed(Instant.ofEpochSecond(1_000), ZoneOffset.UTC)

    @Test fun classifiesDocumentedTemporaryShutdownFrom403Body() {
        val client = AniListGraphQlHttpClient(clock = clock, transport = { _, _ -> error("unused") })
        val response = GraphQlHttpResult.HttpFailure(
            403, """{"errors":[{"status":403,"message":"API temporarily disabled due to stability issues"}]}""",
            null, null, null, "request-1"
        )
        assertTrue(client.classify(response) is AniListFailure.ServiceUnavailable)
    }

    @Test fun appliesSharedRetryAfterCooldown() = runBlocking {
        var calls = 0
        val client = AniListGraphQlHttpClient(clock = clock, transport = { _, _ ->
            calls++
            GraphQlHttpResult.HttpFailure(429, "rate limited", 120, 0, 1_120, null)
        })
        client.execute("first", "{}")
        val second = client.execute("second", "{}")
        assertEquals(1, calls)
        assertTrue(second is GraphQlHttpResult.HttpFailure && second.retryAfterSeconds == 120L)
    }

    @Test fun distinguishesDnsTlsAndTimeoutFailures() {
        val client = AniListGraphQlHttpClient(clock = clock, transport = { _, _ -> error("unused") })
        assertEquals(
            NetworkFailureType.DNS,
            (client.classify(GraphQlHttpResult.NetworkFailure(NetworkFailureType.DNS, "host")) as AniListFailure.Network).type
        )
    }
}
