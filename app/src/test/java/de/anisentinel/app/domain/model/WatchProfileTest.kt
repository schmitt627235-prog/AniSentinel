package de.anisentinel.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WatchProfileTest {
    private val profile = WatchProfile(
        id = "automatic",
        phases = listOf(
            WatchPhase(0, 10 * 60, 60),
            WatchPhase(10 * 60, 30 * 60, 5 * 60),
            WatchPhase(30 * 60, 60 * 60, 10 * 60),
            WatchPhase(60 * 60, null, 30 * 60)
        ),
        stopAfterSeconds = 24 * 60 * 60,
        liveMonitoringAllowed = true
    )

    @Test
    fun `selects phases at exact boundaries`() {
        assertEquals(60L, profile.phaseAt(0)?.intervalSeconds)
        assertEquals(5L * 60, profile.phaseAt(10 * 60)?.intervalSeconds)
        assertEquals(10L * 60, profile.phaseAt(30 * 60)?.intervalSeconds)
        assertEquals(30L * 60, profile.phaseAt(60 * 60)?.intervalSeconds)
    }

    @Test
    fun `stops at configured profile end`() {
        assertNull(profile.phaseAt(24 * 60 * 60))
        assertNull(profile.phaseAt(25 * 60 * 60))
    }

    @Test
    fun `rejects intervals below product minimum`() {
        assertThrows(IllegalArgumentException::class.java) {
            WatchPhase(0, 60, 29)
        }
    }
}
