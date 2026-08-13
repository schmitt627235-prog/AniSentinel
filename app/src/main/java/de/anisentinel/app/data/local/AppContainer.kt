package de.anisentinel.app.data.local

import android.content.Context
import androidx.room.Room
import de.anisentinel.app.data.settings.DataStoreSettingsRepository
import de.anisentinel.app.data.provider.UnavailableProviderRepository
import de.anisentinel.app.domain.watcher.ProfileWatchScheduler
import de.anisentinel.app.domain.watcher.WatcherEngine
import de.anisentinel.app.notification.AndroidNotificationDispatcher
import de.anisentinel.app.notification.NotificationCoordinator
import de.anisentinel.app.data.anilist.AniListClient
import de.anisentinel.app.data.anilist.CachedAniListRepository
import de.anisentinel.app.data.anilist.AniListCalendarRepository
import de.anisentinel.app.data.anilist.AniListCalendarSource
import de.anisentinel.app.data.anilist.AniListGraphQlHttpClient
import de.anisentinel.app.data.anilist.SystemDeviceTimeZoneProvider
import de.anisentinel.app.data.provider.CrunchyrollCalendarChecker
import de.anisentinel.app.data.provider.CrunchyrollEpisodeChecker
import de.anisentinel.app.data.provider.ProviderAvailabilityRepository
import de.anisentinel.app.data.provider.ProviderPipelineRepository
import de.anisentinel.app.data.provider.JustWatchCatalogRepository
import de.anisentinel.app.domain.provider.UnconfiguredJustWatchPartnerSource
import de.anisentinel.app.BuildConfig
import de.anisentinel.app.domain.provider.JustWatchPartnerSource
import de.anisentinel.app.data.anisearch.AniSearchManualImportRepository
import de.anisentinel.app.data.anisearch.AniSearchHttpTransport
import de.anisentinel.app.data.settings.SourceCooldownStore
import de.anisentinel.app.data.settings.BackgroundSyncStatusStore
import de.anisentinel.app.data.release.AnimeRadarCalendarSource
import de.anisentinel.app.data.release.AniListFallbackCalendarSource
import de.anisentinel.app.data.release.ReleaseSourceCoordinator
import de.anisentinel.app.data.release.AniWorldHttpTransport
import de.anisentinel.app.data.release.AniWorldReleaseRepository
import de.anisentinel.app.data.release.AniWorldEpisodeFallbackChecker
import de.anisentinel.app.background.FavoriteReleaseScheduler
import de.anisentinel.app.background.FavoriteHistoryBackfillCoordinator
import de.anisentinel.app.data.news.Anime2YouNewsRepository
import de.anisentinel.app.data.provider.CrunchyrollHistoricalReleaseImporter
import de.anisentinel.app.data.provider.AdnHistoricalReleaseImporter
import de.anisentinel.app.data.provider.CrunchyrollAnonymousCatalogClient

class AppContainer(context: Context) {
    val database: AniSentinelDatabase = Room.databaseBuilder(
        context.applicationContext,
        AniSentinelDatabase::class.java,
        "anisentinel.db"
    ).addMigrations(
        AniSentinelDatabase.MIGRATION_1_2,
        AniSentinelDatabase.MIGRATION_2_3,
        AniSentinelDatabase.MIGRATION_3_4,
        AniSentinelDatabase.MIGRATION_4_5
        ,AniSentinelDatabase.MIGRATION_5_6,
        AniSentinelDatabase.MIGRATION_6_7,
        AniSentinelDatabase.MIGRATION_7_8
        ,AniSentinelDatabase.MIGRATION_8_9,
        AniSentinelDatabase.MIGRATION_9_10,
        AniSentinelDatabase.MIGRATION_10_11,
        AniSentinelDatabase.MIGRATION_11_12
        ,AniSentinelDatabase.MIGRATION_12_13
        ,AniSentinelDatabase.MIGRATION_13_14
        ,AniSentinelDatabase.MIGRATION_14_15
        ,AniSentinelDatabase.MIGRATION_15_16
        ,AniSentinelDatabase.MIGRATION_16_17
        ,AniSentinelDatabase.MIGRATION_17_18
        ,AniSentinelDatabase.MIGRATION_18_19
        ,AniSentinelDatabase.MIGRATION_19_20
        ,AniSentinelDatabase.MIGRATION_20_21
        ,AniSentinelDatabase.MIGRATION_21_22
        ,AniSentinelDatabase.MIGRATION_22_23
        ,AniSentinelDatabase.MIGRATION_23_24
    ).build()

    val newsRepository = Anime2YouNewsRepository(database.aniSentinelDao())

    val favoriteReleaseScheduler = FavoriteReleaseScheduler(context.applicationContext, database.aniSentinelDao())
    val favoritesRepository: LocalFavoritesRepository =
        LocalFavoritesRepository(database.aniSentinelDao()) { animeId, enabled ->
            if (enabled) {
                FavoriteHistoryBackfillCoordinator.request(context.applicationContext, animeId)
                favoriteReleaseScheduler.reconcileAll()
            } else {
                FavoriteHistoryBackfillCoordinator.cancel(context.applicationContext, animeId)
                favoriteReleaseScheduler.cancelAnime(animeId)
            }
        }

    val settingsRepository = DataStoreSettingsRepository(context.applicationContext)
    val backgroundSyncStatusStore = BackgroundSyncStatusStore(context.applicationContext)
    val providerRepository = UnavailableProviderRepository()
    private val sourceCooldownStore = SourceCooldownStore(context.applicationContext)
    private val aniListHttpClient = AniListGraphQlHttpClient(cooldownStore = sourceCooldownStore)
    val aniListRepository = CachedAniListRepository(
        database.aniSentinelDao(),
        AniListClient(aniListHttpClient)
    )
    val deviceTimeZoneProvider = SystemDeviceTimeZoneProvider
    private val releaseSourceCoordinator = ReleaseSourceCoordinator(
        primary = AnimeRadarCalendarSource(
            enabled = context.resources.getBoolean(de.anisentinel.app.R.bool.anime_radar_enabled)
        ),
        fallback = AniListFallbackCalendarSource(
            AniListCalendarSource(aniListHttpClient, timeZoneProvider = deviceTimeZoneProvider)
        )
    )
    val releaseCalendarRepository = AniListCalendarRepository(
        database.aniSentinelDao(),
        releaseSourceCoordinator,
        timeZoneProvider = deviceTimeZoneProvider
    )
    val aniWorldReleaseRepository = AniWorldReleaseRepository(
        database.aniSentinelDao(),
        AniWorldHttpTransport(context.applicationContext),
        deviceTimeZoneProvider.currentZoneId()
    )
    val aniSearchManualImportRepository =
        AniSearchManualImportRepository(
            database.aniSentinelDao(),
            transport = AniSearchHttpTransport(context.applicationContext, cooldownStore = sourceCooldownStore)
        )
    val localCalendarImportRepository = LocalCalendarImportRepository(database.aniSentinelDao())
    val providerAvailabilityRepository = ProviderAvailabilityRepository(
        database.aniSentinelDao(),
        CrunchyrollCalendarChecker()
    )
    private val justWatchSource = loadJustWatchDiagnosticSource()
    val justWatchCatalogSource: de.anisentinel.app.domain.provider.JustWatchCatalogSource =
        justWatchSource as? de.anisentinel.app.domain.provider.JustWatchCatalogSource
            ?: de.anisentinel.app.domain.provider.UnconfiguredJustWatchCatalogSource
    val justWatchCatalogRepository = JustWatchCatalogRepository(
        database.aniSentinelDao(), justWatchCatalogSource
    )
    private val crunchyrollCatalogClient = CrunchyrollAnonymousCatalogClient()
    val providerPipelineRepository = ProviderPipelineRepository(
        database.aniSentinelDao(),
        justWatchSource,
        CrunchyrollEpisodeChecker(),
        AniWorldEpisodeFallbackChecker(),
        listOf(
            de.anisentinel.app.data.provider.CrunchyrollMetadataAdapter(catalogClient = crunchyrollCatalogClient),
            de.anisentinel.app.data.provider.CrunchyrollPublicWebAdapter(),
            de.anisentinel.app.data.provider.AdnMetadataAdapter()
        )
    )
    val crunchyrollHistoricalReleaseImporter = CrunchyrollHistoricalReleaseImporter(
        database.aniSentinelDao(), catalogClient = crunchyrollCatalogClient
    )
    val adnHistoricalReleaseImporter = AdnHistoricalReleaseImporter(database.aniSentinelDao())
    val watcherEngine = WatcherEngine(providerRepository, ProfileWatchScheduler())
    val androidNotificationDispatcher =
        AndroidNotificationDispatcher(context.applicationContext)
    val notificationCoordinator = NotificationCoordinator(
        settingsRepository,
        androidNotificationDispatcher
    )

    private fun loadJustWatchDiagnosticSource(): JustWatchPartnerSource {
        if (!BuildConfig.UNOFFICIAL_JUSTWATCH_DIAGNOSTIC_ENABLED) return UnconfiguredJustWatchPartnerSource
        return runCatching {
            Class.forName("de.anisentinel.app.data.provider.UnofficialJustWatchDiagnosticSource")
                .getDeclaredConstructor().newInstance() as JustWatchPartnerSource
        }.getOrDefault(UnconfiguredJustWatchPartnerSource)
    }
}
