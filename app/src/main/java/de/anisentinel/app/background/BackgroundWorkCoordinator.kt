package de.anisentinel.app.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundWorkCoordinator {
    const val RELEASE_CALENDAR_WORK = "anisentinel.release-calendar-sync"
    const val ANIWORLD_CALENDAR_WORK = "anisentinel.aniworld-calendar-sync"
    const val ANIWORLD_SCHEDULE_CHANGES_WORK = "anisentinel.aniworld-schedule-changes-sync"
    private const val LEGACY_ANILIST_CALENDAR_WORK = "anisentinel.anilist-calendar-sync"
    const val ANISEARCH_METADATA_WORK = "anisentinel.anisearch-metadata-sync"
    const val PROVIDER_AVAILABILITY_WORK = "anisentinel.justwatch-episode-offer-sync"
    const val JUSTWATCH_PROVIDER_SYNC_WORK = "anisentinel.justwatch-diagnostic-sync"
    const val DIAGNOSTIC_RETRY_WORK = "anisentinel.diagnostic-retry-proof"

    fun runDiagnosticRetryProof(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DIAGNOSTIC_RETRY_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DiagnosticRetryWorker>().build()
        )
    }

    fun reconcile(context: Context, liveSourcesEnabled: Boolean, aniWorldEnabled: Boolean = false) {
        val manager = WorkManager.getInstance(context)
        if (!liveSourcesEnabled) {
            manager.cancelUniqueWork(RELEASE_CALENDAR_WORK)
            manager.cancelUniqueWork(LEGACY_ANILIST_CALENDAR_WORK)
            manager.cancelUniqueWork(ANIWORLD_CALENDAR_WORK)
            manager.cancelUniqueWork(ANIWORLD_SCHEDULE_CHANGES_WORK)
            manager.cancelUniqueWork(ANISEARCH_METADATA_WORK)
            manager.cancelUniqueWork(PROVIDER_AVAILABILITY_WORK)
            manager.cancelUniqueWork(JUSTWATCH_PROVIDER_SYNC_WORK)
            return
        }

        // Only AniWorld + the approved/diagnostic JustWatch pipeline are active.
        manager.cancelUniqueWork(RELEASE_CALENDAR_WORK)
        manager.cancelUniqueWork(LEGACY_ANILIST_CALENDAR_WORK)

        if (aniWorldEnabled) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            manager.enqueueUniquePeriodicWork(
                ANIWORLD_CALENDAR_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AniWorldCalendarSyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .addTag("anisentinel.aniworld-calendar").build()
            )
            manager.enqueueUniquePeriodicWork(
                ANIWORLD_SCHEDULE_CHANGES_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AniWorldScheduleChangesSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                    .addTag("anisentinel.aniworld-changes").build()
            )
        } else {
            manager.cancelUniqueWork(ANIWORLD_CALENDAR_WORK)
            manager.cancelUniqueWork(ANIWORLD_SCHEDULE_CHANGES_WORK)
        }

        if (de.anisentinel.app.BuildConfig.UNOFFICIAL_JUSTWATCH_DIAGNOSTIC_ENABLED) {
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            manager.enqueueUniquePeriodicWork(
                JUSTWATCH_PROVIDER_SYNC_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<JustWatchProviderSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).addTag(JUSTWATCH_PROVIDER_SYNC_WORK).build()
            )
            manager.enqueueUniquePeriodicWork(
                PROVIDER_AVAILABILITY_WORK, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ProviderEpisodeAvailabilitySyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).addTag(PROVIDER_AVAILABILITY_WORK).build()
            )
        } else {
            manager.cancelUniqueWork(JUSTWATCH_PROVIDER_SYNC_WORK)
            manager.cancelUniqueWork(PROVIDER_AVAILABILITY_WORK)
        }
        // AniSearch remains disabled until explicitly enabled.
        manager.cancelUniqueWork(ANISEARCH_METADATA_WORK)
        manager.cancelUniqueWork(LEGACY_ANILIST_CALENDAR_WORK)
    }
}
