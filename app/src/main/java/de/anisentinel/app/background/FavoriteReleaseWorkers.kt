package de.anisentinel.app.background

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.local.NotificationDeliveryEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.domain.watcher.NotificationEvent
import java.time.Instant

internal suspend fun handleReleaseDue(context: Context, releaseId: String) {
        val app = context.applicationContext as AniSentinelApplication
        val dao = app.container.database.aniSentinelDao()
    val release = dao.release(releaseId) ?: return
    ProviderCheckTrace.event(
        releaseId, "DUE_ALARM_HANDLED",
        detail = "expectedAt=${release.expectedAt ?: "unknown"}"
    )
        if (release.isHistoricalImport) {
            app.container.favoriteReleaseScheduler.cancelAvailabilityCheck(releaseId)
            dao.deleteScheduledReleaseNotification(releaseId)
            return
        }
        dao.favorite(release.animeId)?.takeIf { it.enabled } ?: return
        release.episodeNumber ?: return
        dao.updateReleaseStatus(releaseId, "DUE")
        // Due is an internal state transition. Provider checks start silently; users are only
        // notified about a newly confirmed availability or a genuine technical check failure.
        dao.deleteScheduledReleaseNotification(releaseId)
        val request = OneTimeWorkRequestBuilder<ProviderEpisodeAvailabilitySyncWorker>()
            .setInputData(Data.Builder().putString(FavoriteReleaseScheduler.KEY_RELEASE_ID, releaseId).build())
            .build()
    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            ProviderEpisodeAvailabilitySyncWorker.checkWorkName(releaseId), ExistingWorkPolicy.REPLACE, request
    )
    ProviderCheckTrace.event(releaseId, "PROVIDER_WORK_ENQUEUED")
}

internal suspend fun deliverOnce(
    app: AniSentinelApplication,
    release: EpisodeReleaseEntity,
    eventType: String,
    event: NotificationEvent
): Boolean {
    if (release.isHistoricalImport) return false
    val dao = app.container.database.aniSentinelDao()
    val now = Instant.now().epochSecond
    val deliveryId = semanticNotificationDeliveryId(release, eventType)
    // Also honours deliveries written by V14 and earlier. Matching the immutable airing instant in
    // addition to animeId safely covers the observed legacy parser bug that produced animeId
    // `aniworld:episode-6` for the same S2E6 calendar entry.
    if (dao.semanticNotificationDeliveryCount(
            release.animeId, release.seasonNumber, release.episodeNumber,
            release.releaseLanguage, release.expectedAt, eventType
        ) > 0
    ) return false
    val claim = NotificationDeliveryEntity(
        deliveryId, release.sourceReleaseId, release.animeId, eventType, now, deliveryId.hashCode()
    )
    // The INSERT IGNORE is the atomic claim. It prevents two workers (for example AlarmManager and
    // WorkManager) from both dispatching before either has persisted its delivery.
    if (dao.claimNotificationDelivery(claim) == -1L) return false
    if (!app.container.notificationCoordinator.dispatch(event)) {
        dao.deleteNotificationDelivery(deliveryId)
        return false
    }
    ProviderCheckTrace.event(release.sourceReleaseId, "NOTIFICATION_DISPATCHED", detail = "type=$eventType")
    return true
}

internal fun semanticNotificationDeliveryId(
    release: EpisodeReleaseEntity,
    eventType: String
): String = listOf(
    "notification-v2",
    release.animeId,
    "s${release.seasonNumber ?: 0}",
    "e${release.episodeNumber ?: 0}",
    release.releaseLanguage ?: "UNSPECIFIED",
    eventType
).joinToString(":")
