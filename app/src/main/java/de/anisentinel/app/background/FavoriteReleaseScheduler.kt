package de.anisentinel.app.background

import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.ScheduledReleaseNotificationEntity
import java.time.Clock

class FavoriteReleaseScheduler(
    private val context: Context,
    private val dao: AniSentinelDao,
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun reconcileAll() {
        val now = clock.instant().epochSecond
        val desiredRows = mutableListOf<Pair<de.anisentinel.app.data.local.EpisodeReleaseEntity, String>>()
        dao.activeFavorites().forEach { favorite ->
            val selection = ReleaseWatchSelectionPolicy.select(
                dao.futureFavoriteReleases(favorite.animeId, favorite.languagePreference, now - 7 * 86_400), now
            )
            selection.staleReleaseIds.forEach { releaseId ->
                dao.updateReleaseStatus(releaseId, "STALE_UNCONFIRMED")
                cancelAllReleaseWatchScheduling(releaseId)
            }
            desiredRows += selection.active.map { it to favorite.languagePreference }
        }
        val desired = desiredRows.associateBy { it.first.sourceReleaseId }
        val scheduled = dao.scheduledReleaseNotifications()
        // One-time V17 cleanup: remove legacy WorkManager Due timers. V18 uses only Exact Alarm
        // for the time-critical Due event; WorkManager remains limited to recovery and retries.
        WorkManager.getInstance(context).cancelAllWorkByTag(LEGACY_DUE_WORK_TAG)
        scheduled.filter { it.workName.startsWith(LEGACY_WORK_PREFIX) }.forEach {
            WorkManager.getInstance(context).cancelUniqueWork(it.workName)
        }
        scheduled.filter { it.releaseId !in desired }.forEach { stale ->
            cancelAlarm(stale.releaseId)
            dao.deleteScheduledReleaseNotification(stale.releaseId)
        }
        desired.values.sortedBy { it.first.expectedAt }.forEachIndexed { index, (release, language) ->
            val eventAt = requireNotNull(release.expectedAt)
            val workName = alarmName(release.sourceReleaseId)
            if (eventAt > now) {
                scheduleAlarm(release.sourceReleaseId, eventAt)
            } else {
                // Never register an exact alarm in the past: Android dispatches all such alarms
                // immediately after process/package restart and can stall the foreground UI.
                alarmIntent(release.sourceReleaseId, PendingIntent.FLAG_NO_CREATE)?.let { due ->
                    context.getSystemService(AlarmManager::class.java).cancel(due)
                    due.cancel()
                }
                // Cancel one-time work left by an older build before registering the new,
                // deliberately sparse catch-up slot.
                cancelAvailabilityCheck(release.sourceReleaseId)
                scheduleAvailabilityCheck(release.sourceReleaseId, now + 10L + index * 60L)
            }
            // V25.4: no separate T+10 gate. The due check always tries the direct provider first;
            // AniWorld is selected inside the pipeline only after a technical/unsupported result.
            cancelFallbackAlarm(release.sourceReleaseId)
            dao.upsertScheduledReleaseNotifications(listOf(ScheduledReleaseNotificationEntity(
                release.sourceReleaseId, release.animeId, eventAt, language, workName, now
            )))
        }
    }

    suspend fun cancelAnime(animeId: String) {
        dao.scheduledReleaseNotifications().filter { it.animeId == animeId }.forEach {
            if (it.workName.startsWith(LEGACY_WORK_PREFIX)) {
                WorkManager.getInstance(context).cancelUniqueWork(it.workName)
            }
            cancelAlarm(it.releaseId)
        }
        dao.deleteScheduledReleaseNotificationsForAnime(animeId)
    }

    fun scheduleAvailabilityCheck(releaseId: String, eventAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = requireNotNull(alarmIntent(releaseId, PendingIntent.FLAG_UPDATE_CURRENT, check = true))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, eventAt * 1_000L, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, eventAt * 1_000L, operation)
        }
    }

    fun cancelAvailabilityCheck(releaseId: String) {
        alarmIntent(releaseId, PendingIntent.FLAG_NO_CREATE, check = true)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        WorkManager.getInstance(context).cancelUniqueWork(
            ProviderEpisodeAvailabilitySyncWorker.checkWorkName(releaseId)
        )
    }

    fun cancelFallbackAlarm(releaseId: String) {
        alarmIntent(releaseId, PendingIntent.FLAG_NO_CREATE, fallback = true)?.let { fallback ->
            context.getSystemService(AlarmManager::class.java).cancel(fallback)
            fallback.cancel()
        }
    }

    /** Terminal cancellation after AVAILABLE/POSTPONED/DELAYED. */
    suspend fun cancelAllReleaseWatchScheduling(releaseId: String) {
        cancelAvailabilityCheck(releaseId)
        cancelFallbackAlarm(releaseId)
        alarmIntent(releaseId, PendingIntent.FLAG_NO_CREATE)?.let { due ->
            context.getSystemService(AlarmManager::class.java).cancel(due)
            due.cancel()
        }
        dao.deleteScheduledReleaseNotification(releaseId)
    }

    private fun scheduleAlarm(releaseId: String, eventAt: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = requireNotNull(alarmIntent(releaseId, PendingIntent.FLAG_UPDATE_CURRENT))
        val triggerAtMillis = eventAt * 1_000L
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }

    private fun scheduleFallbackAlarm(releaseId: String, eventAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val operation = requireNotNull(alarmIntent(releaseId, PendingIntent.FLAG_UPDATE_CURRENT, fallback = true))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, eventAt * 1_000L, operation)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, eventAt * 1_000L, operation)
        }
    }

    private fun cancelAlarm(releaseId: String) {
        alarmIntent(releaseId, PendingIntent.FLAG_NO_CREATE)?.let { operation ->
            context.getSystemService(AlarmManager::class.java).cancel(operation)
            operation.cancel()
        }
        cancelFallbackAlarm(releaseId)
        cancelAvailabilityCheck(releaseId)
    }

    private fun alarmIntent(releaseId: String, mode: Int, fallback: Boolean = false, check: Boolean = false): PendingIntent? = PendingIntent.getBroadcast(
        context,
        releaseId.hashCode() xor when { fallback -> FALLBACK_REQUEST_MASK; check -> CHECK_REQUEST_MASK; else -> 0 },
        Intent(context, FavoriteReleaseAlarmReceiver::class.java)
            .setAction(when { fallback -> ACTION_RELEASE_FALLBACK; check -> ACTION_RELEASE_CHECK; else -> ACTION_RELEASE_DUE })
            .putExtra(KEY_RELEASE_ID, releaseId),
        mode or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        const val KEY_RELEASE_ID = "release_id"
        const val ACTION_RELEASE_DUE = "de.anisentinel.app.action.RELEASE_DUE"
        const val ACTION_RELEASE_FALLBACK = "de.anisentinel.app.action.RELEASE_FALLBACK"
        const val ACTION_RELEASE_CHECK = "de.anisentinel.app.action.RELEASE_CHECK"
        private const val FALLBACK_REQUEST_MASK = 0x4f1bbc
        private const val CHECK_REQUEST_MASK = 0x21a770
        private const val LEGACY_WORK_PREFIX = "anisentinel.favorite-release."
        private const val LEGACY_DUE_WORK_TAG = "anisentinel.favorite-release"
        fun alarmName(releaseId: String) = "anisentinel.exact-alarm.$releaseId"
    }
}
