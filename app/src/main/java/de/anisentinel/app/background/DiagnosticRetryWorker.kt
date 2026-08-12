package de.anisentinel.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.anisentinel.app.AniSentinelApplication
import java.time.Instant
import kotlinx.coroutines.delay

/** Debug-build evidence path only. It performs no network or source request. */
class DiagnosticRetryWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val store = (applicationContext as AniSentinelApplication).container.backgroundSyncStatusStore
        val now = Instant.now().epochSecond
        store.markRunning(attempt = 1, now = now)
        delay(4_000)
        store.markRetry(attempt = 1, now = now, nextRetry = now + 30 * 60)
        return Result.success()
    }
}
