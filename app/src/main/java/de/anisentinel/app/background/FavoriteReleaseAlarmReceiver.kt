package de.anisentinel.app.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Exact Alarm wake-up; network work is handed to a one-shot worker. */
class FavoriteReleaseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val releaseId = intent.getStringExtra(FavoriteReleaseScheduler.KEY_RELEASE_ID) ?: return
        if (intent.action == FavoriteReleaseScheduler.ACTION_RELEASE_DUE) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try { handleReleaseDue(context, releaseId) } finally { pending.finish() }
            }
            return
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            ProviderEpisodeAvailabilitySyncWorker.checkWorkName(releaseId), ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ProviderEpisodeAvailabilitySyncWorker>()
                .setInputData(Data.Builder()
                    .putString(FavoriteReleaseScheduler.KEY_RELEASE_ID, releaseId)
                    .putBoolean(ProviderEpisodeAvailabilitySyncWorker.KEY_FALLBACK_TRIGGER, intent.action == FavoriteReleaseScheduler.ACTION_RELEASE_FALLBACK)
                    .build())
                .build()
        )
    }
}
