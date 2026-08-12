package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.ReleaseStatus
import java.time.Instant

enum class ReleaseDelayMode { HIDDEN, RUNNING, FROZEN }

object ReleaseDelayPolicy {
    fun mode(status: ReleaseStatus, firstAvailableAt: Instant? = null): ReleaseDelayMode = when {
        status == ReleaseStatus.NOT_AVAILABLE_YET -> ReleaseDelayMode.RUNNING
        status == ReleaseStatus.AVAILABLE && firstAvailableAt != null -> ReleaseDelayMode.FROZEN
        else -> ReleaseDelayMode.HIDDEN
    }
}
