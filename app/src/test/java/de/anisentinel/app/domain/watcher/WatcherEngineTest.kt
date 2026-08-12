package de.anisentinel.app.domain.watcher

import de.anisentinel.app.data.provider.FakeProviderBehavior
import de.anisentinel.app.data.provider.FakeProviderRepository
import de.anisentinel.app.domain.model.LanguagePreference
import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.domain.model.WatchPhase
import de.anisentinel.app.domain.model.WatchProfile
import de.anisentinel.app.domain.repository.ProviderCheckRequest
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherEngineTest {
    private val releaseAt = Instant.parse("2026-07-30T18:00:00Z")
    private val profile = WatchProfile(
        id = "test",
        phases = listOf(WatchPhase(0, null, 60)),
        stopAfterSeconds = 3_600,
        liveMonitoringAllowed = true
    )

    @Test
    fun `available result stops scheduling and emits one event`() = runBlocking {
        val result = engine(FakeProviderBehavior.AVAILABLE).dispatch(
            request(releaseAt.plusSeconds(10)),
            ReleaseStatus.CHECKING,
            releaseAt,
            profile
        )

        assertEquals(ReleaseStatus.AVAILABLE, result.visibleStatus)
        assertNull(result.nextCheckAt)
        assertTrue(result.notification is NotificationEvent.EpisodeAvailable)
    }

    @Test
    fun `unavailable after release becomes delayed`() = runBlocking {
        val result = engine(FakeProviderBehavior.UNAVAILABLE).dispatch(
            request(releaseAt.plusSeconds(10)),
            ReleaseStatus.CHECKING,
            releaseAt,
            profile
        )

        assertEquals(ReleaseStatus.DELAYED_UNCONFIRMED, result.visibleStatus)
        assertTrue(result.notification is NotificationEvent.ReleaseDelayed)
        assertEquals(releaseAt.plusSeconds(70), result.nextCheckAt)
    }

    @Test
    fun `unavailable before release preserves status`() = runBlocking {
        val result = engine(FakeProviderBehavior.UNAVAILABLE).dispatch(
            request(releaseAt.minusSeconds(10)),
            ReleaseStatus.PRECHECK,
            releaseAt,
            profile
        )

        assertEquals(ReleaseStatus.PRECHECK, result.visibleStatus)
        assertNull(result.notification)
    }

    @Test
    fun `provider error never becomes unavailable`() = runBlocking {
        val result = engine(FakeProviderBehavior.ERROR).dispatch(
            request(releaseAt.plusSeconds(10)),
            ReleaseStatus.CHECKING,
            releaseAt,
            profile
        )

        assertEquals(ReleaseStatus.CHECKING, result.visibleStatus)
        assertTrue(result.sourceError)
        assertTrue(result.notification is NotificationEvent.ProviderError)
    }

    @Test
    fun `successful check has no source error`() = runBlocking {
        val result = engine(FakeProviderBehavior.AVAILABLE).dispatch(
            request(releaseAt),
            ReleaseStatus.CHECKING,
            releaseAt,
            profile
        )
        assertFalse(result.sourceError)
    }

    @Test
    fun `scheduler returns null after profile end`() {
        val next = ProfileWatchScheduler().nextCheckAt(
            releaseAt,
            releaseAt.plusSeconds(3_600),
            profile
        )
        assertNull(next)
    }

    @Test
    fun `scheduler uses active phase interval`() {
        val next = ProfileWatchScheduler().nextCheckAt(
            releaseAt,
            releaseAt.plusSeconds(120),
            profile
        )
        assertEquals(releaseAt.plusSeconds(180), next)
    }

    private fun engine(behavior: FakeProviderBehavior) = WatcherEngine(
        FakeProviderRepository(behavior),
        ProfileWatchScheduler()
    )

    private fun request(at: Instant) = ProviderCheckRequest(
        animeId = "atlas",
        episode = 11,
        language = LanguagePreference.BOTH,
        providerId = "fake-cr",
        checkedAt = at
    )
}
