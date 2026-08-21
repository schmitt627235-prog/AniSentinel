package de.anisentinel.app.background

import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.ProviderFailureStateEntity

object ProviderFailureNotificationPolicy {
    const val MIN_CONSECUTIVE_FAILURES = 3
    const val MIN_FAILURE_DURATION_SECONDS = 10 * 60L
    const val PROVIDER_NOTIFICATION_COOLDOWN_SECONDS = 6 * 60 * 60L

    data class Evaluation(
        val providerKey: String?,
        val nextState: ProviderFailureStateEntity?,
        val shouldNotify: Boolean,
        val resetProviderKeys: Set<String> = emptySet()
    )

    fun evaluate(
        rows: List<EpisodeProviderAvailabilityEntity>,
        previous: ProviderFailureStateEntity?,
        now: Long
    ): Evaluation {
        val direct = rows.filterNot {
            it.source == "ANIWORLD_CALENDAR_FALLBACK_V15" ||
                it.source == "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC"
        }
        val keys = direct.mapNotNull(::providerKey).toSet()
        val providerKey = direct.mapNotNull(::providerKey).groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key
        val conclusive = direct.any { it.status.startsWith("AVAILABLE_") || it.status == "NOT_AVAILABLE_YET" }
        val technical = !conclusive && direct.any { it.status == "CHECK_FAILED" || it.status == "PROVIDER_CHECK_FAILED" }
        if (!technical || providerKey == null) return Evaluation(null, null, false, keys)

        val sameStreak = previous?.providerKey == providerKey
        val next = ProviderFailureStateEntity(
            providerKey = providerKey,
            consecutiveFailures = if (sameStreak) previous!!.consecutiveFailures + 1 else 1,
            firstFailureAt = if (sameStreak) previous!!.firstFailureAt else now,
            lastFailureAt = now,
            lastErrorCode = direct.firstNotNullOfOrNull { it.errorCode },
            lastNotifiedAt = previous?.takeIf { sameStreak }?.lastNotifiedAt
        )
        val oldEnough = now - next.firstFailureAt >= MIN_FAILURE_DURATION_SECONDS
        val outsideCooldown = next.lastNotifiedAt == null ||
            now - next.lastNotifiedAt >= PROVIDER_NOTIFICATION_COOLDOWN_SECONDS
        return Evaluation(providerKey, next, next.consecutiveFailures >= MIN_CONSECUTIVE_FAILURES && oldEnough && outsideCooldown)
    }

    fun providerKey(row: EpisodeProviderAvailabilityEntity): String? = row.providerName
        .takeIf(String::isNotBlank)
        ?.lowercase()
        ?.replace(Regex("[^a-z0-9]+"), "-")
        ?.trim('-')
        ?.takeIf(String::isNotBlank)
}
