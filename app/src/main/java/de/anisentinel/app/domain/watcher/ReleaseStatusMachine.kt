package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.ReleaseStatus

enum class WatchEvent {
    BEGIN_PRECHECK,
    BEGIN_CHECKING,
    EPISODE_AVAILABLE,
    DEADLINE_MISSED,
    POSSIBLE_DELAY_FOUND,
    OFFICIAL_DELAY_FOUND,
    SOURCE_ERROR,
    STOP
}

data class StatusTransition(
    val visibleStatus: ReleaseStatus,
    val sourceError: Boolean = false
)

object ReleaseStatusMachine {
    fun enterReleasePhase(current: ReleaseStatus): StatusTransition {
        val precheck = transition(current, WatchEvent.BEGIN_PRECHECK)
        return transition(precheck.visibleStatus, WatchEvent.BEGIN_CHECKING)
    }

    fun transition(current: ReleaseStatus, event: WatchEvent): StatusTransition {
        if (event == WatchEvent.SOURCE_ERROR) {
            return StatusTransition(visibleStatus = current, sourceError = true)
        }
        if (event == WatchEvent.STOP) {
            return StatusTransition(ReleaseStatus.STOPPED)
        }

        val next = when (current to event) {
            ReleaseStatus.SCHEDULED to WatchEvent.BEGIN_PRECHECK -> ReleaseStatus.PRECHECK
            ReleaseStatus.PRECHECK to WatchEvent.BEGIN_CHECKING -> ReleaseStatus.CHECKING
            ReleaseStatus.CHECKING to WatchEvent.EPISODE_AVAILABLE -> ReleaseStatus.AVAILABLE
            ReleaseStatus.CHECKING to WatchEvent.DEADLINE_MISSED -> ReleaseStatus.DELAYED_UNCONFIRMED
            ReleaseStatus.DELAYED_UNCONFIRMED to WatchEvent.POSSIBLE_DELAY_FOUND ->
                ReleaseStatus.POSSIBLY_POSTPONED
            ReleaseStatus.POSSIBLY_POSTPONED to WatchEvent.OFFICIAL_DELAY_FOUND ->
                ReleaseStatus.OFFICIALLY_POSTPONED
            else -> current
        }
        return StatusTransition(next)
    }
}
