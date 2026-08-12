package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.model.LanguagePreference
import de.anisentinel.app.domain.repository.ProviderCheckRequest
import de.anisentinel.app.domain.repository.ProviderCheckResult
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProviderRepositoryTest {
    @Test
    fun `all exposed providers are explicitly fake`() = runBlocking {
        val providers = FakeProviderRepository().providers().first()
        assertTrue(providers.all { it.isFake })
        assertEquals(3, providers.size)
    }

    @Test
    fun `scheduled behavior deterministically finds even episode`() = runBlocking {
        val result = FakeProviderRepository().check(request(8))
        assertTrue(result is ProviderCheckResult.Available)
    }

    @Test
    fun `scheduled behavior deterministically misses odd episode`() = runBlocking {
        val result = FakeProviderRepository().check(request(7))
        assertTrue(result is ProviderCheckResult.Unavailable)
    }

    @Test
    fun `multilingual result exposes dub and sub`() = runBlocking {
        val result = FakeProviderRepository(FakeProviderBehavior.MULTILINGUAL)
            .check(request(8)) as ProviderCheckResult.Available
        assertTrue(result.languages.contains(LanguagePreference.DUB))
        assertTrue(result.languages.contains(LanguagePreference.SUB))
    }

    @Test
    fun `region restriction is not retryable`() = runBlocking {
        val result = FakeProviderRepository(FakeProviderBehavior.REGION_RESTRICTED)
            .check(request(8).copy(region = "US")) as ProviderCheckResult.Error
        assertEquals("REGION_RESTRICTED", result.code)
        assertTrue(!result.retryable)
    }

    @Test
    fun `maintenance provides retry window`() = runBlocking {
        val result = FakeProviderRepository(FakeProviderBehavior.MAINTENANCE)
            .check(request(8)) as ProviderCheckResult.Maintenance
        assertEquals(900, result.retryAfterSeconds)
    }

    @Test
    fun `dub first can be represented independently`() = runBlocking {
        val result = FakeProviderRepository(FakeProviderBehavior.MULTILINGUAL)
            .check(request(8).copy(language = LanguagePreference.DUB))
        assertTrue(result is ProviderCheckResult.Available)
    }

    @Test
    fun `sub first can be represented independently`() = runBlocking {
        val result = FakeProviderRepository(FakeProviderBehavior.MULTILINGUAL)
            .check(request(8).copy(language = LanguagePreference.SUB))
        assertTrue(result is ProviderCheckResult.Available)
    }

    private fun request(episode: Int) = ProviderCheckRequest(
        animeId = "demo",
        episode = episode,
        language = LanguagePreference.BOTH,
        providerId = "fake-cr",
        checkedAt = Instant.parse("2026-07-30T18:00:00Z")
    )
}
