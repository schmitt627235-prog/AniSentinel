package de.anisentinel.app.domain.watcher

enum class ScheduleInterruptionKind {
    ONE_OFF_SHIFT,
    HIATUS_WITH_KNOWN_RETURN,
    HIATUS_WITH_UNKNOWN_RETURN
}

data class ScheduleInterruptionDecision(
    val kind: ScheduleInterruptionKind,
    val mayReusePreviousCadenceAfterAffectedEpisode: Boolean,
    val mustWaitForSourcedReturnSlot: Boolean
)

/**
 * Keeps isolated delays separate from real broadcast interruptions.
 * A resumed weekday/time is never invented: a long interruption establishes a
 * new cadence only through a newly sourced calendar release.
 */
object ReleaseCadencePolicy {
    private const val HIATUS_THRESHOLD_SECONDS = 14L * 24 * 60 * 60

    fun classify(originalExpectedAt: Long?, revisedExpectedAt: Long?): ScheduleInterruptionDecision {
        if (revisedExpectedAt == null) return ScheduleInterruptionDecision(
            ScheduleInterruptionKind.HIATUS_WITH_UNKNOWN_RETURN,
            mayReusePreviousCadenceAfterAffectedEpisode = false,
            mustWaitForSourcedReturnSlot = true
        )
        val gap = if (originalExpectedAt == null) Long.MAX_VALUE
        else kotlin.math.abs(revisedExpectedAt - originalExpectedAt)
        return if (gap >= HIATUS_THRESHOLD_SECONDS) {
            ScheduleInterruptionDecision(
                ScheduleInterruptionKind.HIATUS_WITH_KNOWN_RETURN,
                mayReusePreviousCadenceAfterAffectedEpisode = false,
                mustWaitForSourcedReturnSlot = true
            )
        } else {
            ScheduleInterruptionDecision(
                ScheduleInterruptionKind.ONE_OFF_SHIFT,
                mayReusePreviousCadenceAfterAffectedEpisode = true,
                mustWaitForSourcedReturnSlot = false
            )
        }
    }
}
