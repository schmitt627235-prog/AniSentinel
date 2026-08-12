package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.ReleaseStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseDelayPolicyTest {
    @Test fun onlyConfirmedAbsenceRunsDelay() {
        assertEquals(ReleaseDelayMode.RUNNING, ReleaseDelayPolicy.mode(ReleaseStatus.NOT_AVAILABLE_YET))
        assertEquals(ReleaseDelayMode.HIDDEN, ReleaseDelayPolicy.mode(ReleaseStatus.PROVIDER_CHECK_FAILED))
        assertEquals(ReleaseDelayMode.HIDDEN, ReleaseDelayPolicy.mode(ReleaseStatus.PENDING_CONFIRMATION))
        assertEquals(ReleaseDelayMode.HIDDEN, ReleaseDelayPolicy.mode(ReleaseStatus.OFFICIALLY_POSTPONED))
    }

    @Test fun availabilityFreezesOnlyWithDetectionTimestamp() {
        assertEquals(ReleaseDelayMode.FROZEN, ReleaseDelayPolicy.mode(ReleaseStatus.AVAILABLE, Instant.EPOCH))
        assertEquals(ReleaseDelayMode.HIDDEN, ReleaseDelayPolicy.mode(ReleaseStatus.AVAILABLE))
    }
}
