package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.WatchPhase
import de.anisentinel.app.domain.model.WatchProfile

enum class BatteryMode { NORMAL, SAVER }
enum class NetworkMode { ANY, WIFI_ONLY }

data class WatchContext(
    val minutesUntilRelease: Long,
    val batteryMode: BatteryMode,
    val networkMode: NetworkMode,
    val liveMonitoringConfirmed: Boolean
)

data class PrioritizedWatchProfile(
    val profile: WatchProfile,
    val priority: Int,
    val requiresLiveConfirmation: Boolean = false,
    val disabledInBatterySaver: Boolean = false
)

class WatchProfileSelector(
    profiles: List<PrioritizedWatchProfile> = defaultProfiles
) {
    private val ordered = profiles.sortedByDescending { it.priority }

    fun select(context: WatchContext): WatchProfile = ordered.first {
        (!it.requiresLiveConfirmation || context.liveMonitoringConfirmed) &&
            (!it.disabledInBatterySaver || context.batteryMode != BatteryMode.SAVER)
    }.profile

    companion object {
        val defaultProfiles = listOf(
            PrioritizedWatchProfile(
                profile = WatchProfile(
                    "fast",
                    listOf(WatchPhase(0, null, 30)),
                    stopAfterSeconds = 3_600,
                    liveMonitoringAllowed = true
                ),
                priority = 30,
                requiresLiveConfirmation = true,
                disabledInBatterySaver = true
            ),
            PrioritizedWatchProfile(
                profile = WatchProfile(
                    "balanced",
                    listOf(WatchPhase(0, null, 300)),
                    stopAfterSeconds = 24 * 3_600,
                    liveMonitoringAllowed = false
                ),
                priority = 20
            ),
            PrioritizedWatchProfile(
                profile = WatchProfile(
                    "economical",
                    listOf(WatchPhase(0, null, 900)),
                    stopAfterSeconds = 24 * 3_600,
                    liveMonitoringAllowed = false
                ),
                priority = 10
            )
        )
    }
}
