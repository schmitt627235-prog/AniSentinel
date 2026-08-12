package de.anisentinel.app.domain.watcher

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchProfileSelectorTest {
    private val selector = WatchProfileSelector()

    @Test
    fun `confirmed live monitoring selects fast profile`() {
        assertEquals(
            "fast",
            selector.select(context(live = true)).id
        )
    }

    @Test
    fun `missing confirmation falls back to balanced`() {
        assertEquals(
            "balanced",
            selector.select(context(live = false)).id
        )
    }

    @Test
    fun `battery saver disables fast profile`() {
        assertEquals(
            "balanced",
            selector.select(
                context(live = true).copy(batteryMode = BatteryMode.SAVER)
            ).id
        )
    }

    private fun context(live: Boolean) = WatchContext(
        minutesUntilRelease = 10,
        batteryMode = BatteryMode.NORMAL,
        networkMode = NetworkMode.ANY,
        liveMonitoringConfirmed = live
    )
}
