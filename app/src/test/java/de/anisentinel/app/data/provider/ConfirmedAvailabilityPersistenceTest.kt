package de.anisentinel.app.data.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedAvailabilityPersistenceTest {
    @Test fun laterParserFailureCannotDowngradeConfirmedEpisode() {
        assertTrue(shouldPreserveConfirmedAvailability("AVAILABLE_GER_SUB", 100L, "CHECK_FAILED"))
        assertTrue(shouldPreserveConfirmedAvailability("CHECK_FAILED", 100L, "CHECK_FAILED"))
    }

    @Test fun unconfirmedEpisodeStillAcceptsNewNegativeOrAvailableResult() {
        assertFalse(shouldPreserveConfirmedAvailability("NOT_AVAILABLE_YET", null, "CHECK_FAILED"))
        assertFalse(shouldPreserveConfirmedAvailability("AVAILABLE_GER_SUB", 100L, "AVAILABLE_GER_SUB"))
    }
}
