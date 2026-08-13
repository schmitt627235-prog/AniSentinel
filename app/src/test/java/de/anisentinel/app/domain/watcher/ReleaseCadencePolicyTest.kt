package de.anisentinel.app.domain.watcher

import org.junit.Assert.*
import org.junit.Test

class ReleaseCadencePolicyTest {
    private val day = 24L * 60 * 60

    @Test fun shortOneOffDelayDoesNotChangeFollowingRegularCadence() {
        val result = ReleaseCadencePolicy.classify(1_000_000, 1_000_000 + 30 * 60)
        assertEquals(ScheduleInterruptionKind.ONE_OFF_SHIFT, result.kind)
        assertTrue(result.mayReusePreviousCadenceAfterAffectedEpisode)
        assertFalse(result.mustWaitForSourcedReturnSlot)
    }

    @Test fun multiWeekPauseInvalidatesOldCadenceEvenWithKnownReturnDate() {
        val result = ReleaseCadencePolicy.classify(1_000_000, 1_000_000 + 28 * day)
        assertEquals(ScheduleInterruptionKind.HIATUS_WITH_KNOWN_RETURN, result.kind)
        assertFalse(result.mayReusePreviousCadenceAfterAffectedEpisode)
        assertTrue(result.mustWaitForSourcedReturnSlot)
    }

    @Test fun unknownReturnNeverCreatesSyntheticCountdown() {
        val result = ReleaseCadencePolicy.classify(1_000_000, null)
        assertEquals(ScheduleInterruptionKind.HIATUS_WITH_UNKNOWN_RETURN, result.kind)
        assertFalse(result.mayReusePreviousCadenceAfterAffectedEpisode)
        assertTrue(result.mustWaitForSourcedReturnSlot)
    }

    @Test fun resumedShowMayUseDifferentSourcedWeekdayAndTimeWithoutOldCadenceProjection() {
        val oldSlot = 1_000_000L
        val sourcedReturnSlot = oldSlot + 60 * day + 3 * 60 * 60
        val result = ReleaseCadencePolicy.classify(oldSlot, sourcedReturnSlot)
        assertEquals(ScheduleInterruptionKind.HIATUS_WITH_KNOWN_RETURN, result.kind)
        assertTrue(result.mustWaitForSourcedReturnSlot)
        assertNotEquals(oldSlot % (7 * day), sourcedReturnSlot % (7 * day))
    }
}
