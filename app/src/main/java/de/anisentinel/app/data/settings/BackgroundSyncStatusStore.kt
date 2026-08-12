package de.anisentinel.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backgroundSyncDataStore by preferencesDataStore("background_sync_status")

data class BackgroundSyncStatus(
    val state: String = "DISABLED",
    val runAttemptCount: Int = 0,
    val lastAttemptAtEpochSeconds: Long? = null,
    val lastSuccessAtEpochSeconds: Long? = null,
    val nextRetryAtEpochSeconds: Long? = null,
    val sourceDataAtEpochSeconds: Long? = null,
    val sourceKind: String? = null,
    val receivedCount: Int = 0,
    val storedCount: Int = 0
)

class BackgroundSyncStatusStore(private val context: Context) {
    val status: Flow<BackgroundSyncStatus> = context.backgroundSyncDataStore.data.map { values ->
        BackgroundSyncStatus(
            state = values[STATE] ?: "DISABLED",
            runAttemptCount = values[ATTEMPT] ?: 0,
            lastAttemptAtEpochSeconds = values[LAST_ATTEMPT],
            lastSuccessAtEpochSeconds = values[LAST_SUCCESS],
            nextRetryAtEpochSeconds = values[NEXT_RETRY],
            sourceDataAtEpochSeconds = values[SOURCE_DATA_AT],
            sourceKind = values[SOURCE_KIND],
            receivedCount = values[RECEIVED_COUNT] ?: 0,
            storedCount = values[STORED_COUNT] ?: 0
        )
    }

    suspend fun markRunning(attempt: Int, now: Long) = context.backgroundSyncDataStore.edit {
        it[STATE] = "RUNNING"; it[ATTEMPT] = attempt; it[LAST_ATTEMPT] = now; it.remove(NEXT_RETRY)
    }

    suspend fun markSuccess(
        now: Long,
        sourceKind: String = "UNKNOWN",
        receivedCount: Int = 0,
        storedCount: Int = 0
    ) = context.backgroundSyncDataStore.edit {
        it[STATE] = "SUCCEEDED"; it[ATTEMPT] = 0; it[LAST_SUCCESS] = now; it[SOURCE_DATA_AT] = now
        it[SOURCE_KIND] = sourceKind; it[RECEIVED_COUNT] = receivedCount; it[STORED_COUNT] = storedCount
        it.remove(NEXT_RETRY)
    }

    suspend fun markCacheFresh(checkedAt: Long, sourceDataAt: Long) = context.backgroundSyncDataStore.edit {
        it[STATE] = "CACHE_FRESH"; it[ATTEMPT] = 0; it[LAST_ATTEMPT] = checkedAt
        it[SOURCE_DATA_AT] = sourceDataAt; it.remove(NEXT_RETRY)
    }

    suspend fun markRetry(attempt: Int, now: Long, nextRetry: Long) = context.backgroundSyncDataStore.edit {
        it[STATE] = "RETRY"; it[ATTEMPT] = attempt; it[LAST_ATTEMPT] = now; it[NEXT_RETRY] = nextRetry
    }

    suspend fun markDisabled() = context.backgroundSyncDataStore.edit {
        it[STATE] = "DISABLED"; it[ATTEMPT] = 0; it.remove(NEXT_RETRY)
    }

    private companion object {
        val STATE = stringPreferencesKey("state")
        val ATTEMPT = intPreferencesKey("run_attempt_count")
        val LAST_ATTEMPT = longPreferencesKey("last_attempt_at")
        val LAST_SUCCESS = longPreferencesKey("last_success_at")
        val NEXT_RETRY = longPreferencesKey("next_retry_at")
        val SOURCE_DATA_AT = longPreferencesKey("source_data_at")
        val SOURCE_KIND = stringPreferencesKey("source_kind")
        val RECEIVED_COUNT = intPreferencesKey("received_count")
        val STORED_COUNT = intPreferencesKey("stored_count")
    }
}
