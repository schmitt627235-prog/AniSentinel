package de.anisentinel.app.background

import android.util.Log
import java.time.Instant

/** Timestamped diagnostics for real-device latency analysis; contains no account or device identifiers. */
object ProviderCheckTrace {
    private const val TAG = "AniSentinelProviderTrace"

    fun event(releaseId: String, stage: String, at: Instant = Instant.now(), detail: String? = null) {
        val safeDetail = detail?.replace('\n', ' ')?.take(240)
        Log.i(TAG, buildString {
            append("releaseId=").append(releaseId)
            append(" stage=").append(stage)
            append(" at=").append(at)
            if (!safeDetail.isNullOrBlank()) append(" detail=").append(safeDetail)
        })
    }
}
