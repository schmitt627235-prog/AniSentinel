package de.anisentinel.app.domain.watcher

/** Product schedule for one favorited release. Times are derived from expectedAt to avoid drift. */
object AvailabilityWatchStrategy {
    const val AUTOMATIC = "automatic"
    val profileIds = listOf(
        AUTOMATIC, "30s", "1m", "2m", "5m", "10m", "15m", "30m", "1h"
    )

    fun nextProfileId(current: String?): String =
        profileIds[(profileIds.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % profileIds.size }]

    fun intervalSeconds(profileId: String?, elapsedSeconds: Long): Long {
        val elapsed = elapsedSeconds.coerceAtLeast(0)
        return when (profileId) {
            "30s" -> 30
            "1m" -> 60
            "2m" -> 120
            "5m" -> 300
            "10m" -> 600
            "15m" -> 900
            "30m" -> 1_800
            "1h" -> 3_600
            else -> when {
                elapsed < 5 * 60 -> 30
                elapsed < 10 * 60 -> 60
                elapsed < 60 * 60 -> 5 * 60
                elapsed < 4 * 60 * 60 -> 30 * 60
                else -> 60 * 60
            }
        }
    }

    fun nextCheckAt(expectedAt: Long, checkedAt: Long, profileId: String?): Long {
        val interval = intervalSeconds(profileId, checkedAt - expectedAt)
        val elapsed = (checkedAt - expectedAt).coerceAtLeast(0)
        // Anchor every check to expectedAt. A delayed process therefore skips missed ticks
        // instead of accumulating drift by repeatedly adding to the previous execution time.
        val completedSlots = elapsed / interval
        return expectedAt + (completedSlots + 1) * interval
    }

    fun isTerminal(status: String): Boolean =
        status == "AVAILABLE" || status == "POSTPONED" || status == "DELAYED" || status == "STALE_UNCONFIRMED" ||
            status == "DELAYED_CONFIRMED" || status.startsWith("AVAILABLE_")
}
