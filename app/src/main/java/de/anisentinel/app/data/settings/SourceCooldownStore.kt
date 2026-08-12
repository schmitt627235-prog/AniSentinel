package de.anisentinel.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sourceCooldownDataStore by preferencesDataStore("source_cooldowns")

class SourceCooldownStore(private val context: Context) {
    suspend fun nextAllowedAt(source: String): Long =
        context.sourceCooldownDataStore.data.first()[key(source)] ?: 0L

    suspend fun setNextAllowedAt(source: String, epochSeconds: Long) {
        context.sourceCooldownDataStore.edit { it[key(source)] = epochSeconds }
    }

    private fun key(source: String) = longPreferencesKey("next_allowed_${source.lowercase()}")
}
