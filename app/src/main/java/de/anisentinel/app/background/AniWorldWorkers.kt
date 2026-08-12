package de.anisentinel.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.release.AniWorldSyncResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.Instant

class AniWorldCalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AniSentinelApplication
        if (!app.resources.getBoolean(de.anisentinel.app.R.bool.aniworld_enabled)) return Result.success()
        val today = LocalDate.now(app.container.deviceTimeZoneProvider.currentZoneId())
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when (app.container.aniWorldReleaseRepository.syncCalendar(start, start.plusWeeks(2))) {
            is AniWorldSyncResult.Success -> {
                app.container.favoriteReleaseScheduler.reconcileAll()
                Result.success()
            }
            is AniWorldSyncResult.Failure -> Result.retry()
        }
    }
}

class AniWorldScheduleChangesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AniSentinelApplication
        if (!app.resources.getBoolean(de.anisentinel.app.R.bool.aniworld_enabled)) return Result.success()
        val startedAt = Instant.now().epochSecond
        return when (app.container.aniWorldReleaseRepository.syncScheduleChanges()) {
            is AniWorldSyncResult.Success -> {
                val dao = app.container.database.aniSentinelDao()
                dao.releaseScheduleHistorySince(startedAt).forEach { change ->
                    val release = dao.release(change.releaseId) ?: return@forEach
                    val favorite = dao.favorite(release.animeId)
                    if (favorite?.enabled == true && favorite.notifyPostponed) {
                        dao.updateReleaseStatus(release.sourceReleaseId, "POSTPONED")
                        app.container.favoriteReleaseScheduler.cancelAvailabilityCheck(release.sourceReleaseId)
                        deliverOnce(
                            app, release, "POSTPONED",
                            de.anisentinel.app.domain.watcher.NotificationEvent.OfficiallyPostponed(
                                release.animeId,
                                release.episodeNumber ?: 0,
                                dao.anime(release.animeId)?.titleGerman,
                                release.seasonNumber,
                                change.reason,
                                change.revisedAt?.let(java.time.Instant::ofEpochSecond)
                            )
                        )
                    }
                }
                app.container.favoriteReleaseScheduler.reconcileAll()
                Result.success()
            }
            is AniWorldSyncResult.Failure -> Result.retry()
        }
    }
}
