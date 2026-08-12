package de.anisentinel.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.anilist.CalendarSyncResult
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.first

class ReleaseCalendarSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AniSentinelApplication
        val diagnosticSourceEnabled = app.resources.getBoolean(de.anisentinel.app.R.bool.anime_radar_enabled)
        if (!diagnosticSourceEnabled && !app.container.settingsRepository.settings.first().liveDataEnabled) {
            app.container.backgroundSyncStatusStore.markDisabled()
            return Result.success()
        }
        val now = Instant.now().epochSecond
        app.container.backgroundSyncStatusStore.markRunning(runAttemptCount, now)
        val today = LocalDate.now(app.container.deviceTimeZoneProvider.currentZoneId())
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (val result =
            app.container.releaseCalendarRepository.sync(
                weekStart,
                weekStart.plusWeeks(1)
            )
        ) {
            is CalendarSyncResult.UpdatedFromNetwork -> {
                app.container.backgroundSyncStatusStore.markSuccess(
                    Instant.now().epochSecond,
                    result.sourceKind.name,
                    result.receivedCount,
                    result.storedCount
                )
                Result.success()
            }
            is CalendarSyncResult.CacheFresh -> {
                app.container.backgroundSyncStatusStore.markCacheFresh(
                    Instant.now().epochSecond,
                    result.sourceDataAtEpochSeconds
                )
                Result.success()
            }
            is CalendarSyncResult.RetryRequired -> {
                val retryAt = maxOf(
                    Instant.now().epochSecond + backgroundRetryDelaySeconds(runAttemptCount),
                    result.retryNotBeforeEpochSeconds ?: 0L
                )
                app.container.backgroundSyncStatusStore.markRetry(runAttemptCount, now, retryAt)
                Result.retry()
            }
        }
    }
}
