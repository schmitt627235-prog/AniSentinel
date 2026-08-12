package de.anisentinel.app

import android.app.Application
import de.anisentinel.app.data.local.AppContainer
import de.anisentinel.app.background.BackgroundWorkCoordinator
import de.anisentinel.app.background.FavoriteHistoryBackfillCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AniSentinelApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container.androidNotificationDispatcher.createChannels()
        applicationScope.launch {
            container.database.aniSentinelDao().repairMalformedAniWorldEpisodeIdentities()
            container.database.aniSentinelDao().repairConfirmedAvailabilityDowngrades()
            FavoriteHistoryBackfillCoordinator.reconcile(this@AniSentinelApplication)
            container.favoriteReleaseScheduler.reconcileAll()
        }
        applicationScope.launch {
            container.settingsRepository.settings.collectLatest { settings ->
                val aniWorldEnabled = resources.getBoolean(R.bool.aniworld_enabled)
                val sourceSyncEnabled = aniWorldEnabled
                BackgroundWorkCoordinator.reconcile(this@AniSentinelApplication, sourceSyncEnabled, aniWorldEnabled)
                if (!sourceSyncEnabled) container.backgroundSyncStatusStore.markDisabled()
            }
        }
    }
}
