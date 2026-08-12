package de.anisentinel.app.background

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRetryScheduleTest {
    @Test fun `retry delay grows exponentially from thirty minutes`() {
        assertEquals(1_800L, backgroundRetryDelaySeconds(0))
        assertEquals(3_600L, backgroundRetryDelaySeconds(1))
        assertEquals(7_200L, backgroundRetryDelaySeconds(2))
    }

    @Test fun `retry delay is capped at one day`() {
        assertEquals(86_400L, backgroundRetryDelaySeconds(20))
    }
}
