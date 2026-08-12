package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.ReleaseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseStatusMachineTest {
    @Test
    fun `happy path reaches available`() {
        val precheck = ReleaseStatusMachine.transition(
            ReleaseStatus.SCHEDULED,
            WatchEvent.BEGIN_PRECHECK
        )
        val checking = ReleaseStatusMachine.transition(
            precheck.visibleStatus,
            WatchEvent.BEGIN_CHECKING
        )
        val available = ReleaseStatusMachine.transition(
            checking.visibleStatus,
            WatchEvent.EPISODE_AVAILABLE
        )

        assertEquals(ReleaseStatus.PRECHECK, precheck.visibleStatus)
        assertEquals(ReleaseStatus.CHECKING, checking.visibleStatus)
        assertEquals(ReleaseStatus.AVAILABLE, available.visibleStatus)
        assertFalse(available.sourceError)
    }

    @Test
    fun `delay evidence progresses conservatively`() {
        val delayed = ReleaseStatusMachine.transition(
            ReleaseStatus.CHECKING,
            WatchEvent.DEADLINE_MISSED
        )
        val possible = ReleaseStatusMachine.transition(
            delayed.visibleStatus,
            WatchEvent.POSSIBLE_DELAY_FOUND
        )
        val official = ReleaseStatusMachine.transition(
            possible.visibleStatus,
            WatchEvent.OFFICIAL_DELAY_FOUND
        )

        assertEquals(ReleaseStatus.DELAYED_UNCONFIRMED, delayed.visibleStatus)
        assertEquals(ReleaseStatus.POSSIBLY_POSTPONED, possible.visibleStatus)
        assertEquals(ReleaseStatus.OFFICIALLY_POSTPONED, official.visibleStatus)
    }

    @Test
    fun `source error never overwrites visible status`() {
        val result = ReleaseStatusMachine.transition(
            ReleaseStatus.CHECKING,
            WatchEvent.SOURCE_ERROR
        )

        assertEquals(ReleaseStatus.CHECKING, result.visibleStatus)
        assertTrue(result.sourceError)
    }

    @Test
    fun `stop works from every state`() {
        ReleaseStatus.entries.forEach { status ->
            assertEquals(
                ReleaseStatus.STOPPED,
                ReleaseStatusMachine.transition(status, WatchEvent.STOP).visibleStatus
            )
        }
    }
}
