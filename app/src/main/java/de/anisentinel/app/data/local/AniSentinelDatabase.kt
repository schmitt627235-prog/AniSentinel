package de.anisentinel.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AnimeEntity::class,
        FavoriteEntity::class,
        FavoriteHistoryBackfillEntity::class,
        EpisodeEntity::class,
        WatchProfileEntity::class,
        WatchPhaseEntity::class,
        CatalogEntryEntity::class,
        ProviderReferenceEntity::class,
        ProviderAvailabilityEntity::class,
        EpisodeReleaseEntity::class,
        ReleaseSourceReferenceEntity::class,
        ReleaseScheduleHistoryEntity::class,
        AnimeExternalIdEntity::class,
        LocalImportBatchEntity::class
        ,JustWatchTitleMatchEntity::class,
        JustWatchOfferEntity::class,
        EpisodeProviderAvailabilityEntity::class,
        ScheduledReleaseNotificationEntity::class,
        NotificationDeliveryEntity::class
        ,JustWatchCatalogTitleEntity::class
        ,JustWatchGenreEntity::class
        ,AnnouncementEntity::class
        ,ProviderMetadataIdentityEntity::class
        ,ReleasePostponementEntity::class
    ],
    version = 25,
    exportSchema = true
)
abstract class AniSentinelDatabase : RoomDatabase() {
    abstract fun aniSentinelDao(): AniSentinelDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE anime ADD COLUMN nextAiringAt INTEGER")
                database.execSQL("ALTER TABLE anime ADD COLUMN nextEpisode INTEGER")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE anime ADD COLUMN sourceUpdatedAt INTEGER")
                database.execSQL("ALTER TABLE anime ADD COLUMN cachedAt INTEGER")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS catalog_entries (
                        catalogType TEXT NOT NULL,
                        animeId TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(catalogType, animeId),
                        FOREIGN KEY(animeId) REFERENCES anime(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catalog_entries_animeId " +
                        "ON catalog_entries(animeId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_catalog_entries_catalogType_position " +
                        "ON catalog_entries(catalogType, position)"
                )
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS provider_references (
                        animeId TEXT NOT NULL, provider TEXT NOT NULL,
                        seriesUrl TEXT, source TEXT NOT NULL, sourceUrl TEXT,
                        lastConfirmedAt INTEGER,
                        PRIMARY KEY(animeId, provider),
                        FOREIGN KEY(animeId) REFERENCES anime(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_provider_references_animeId ON provider_references(animeId)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS provider_availability (
                        animeId TEXT NOT NULL, provider TEXT NOT NULL,
                        episodeKey INTEGER NOT NULL, episodeNumber INTEGER,
                        status TEXT NOT NULL, providerUrl TEXT, checkedAt INTEGER NOT NULL,
                        firstAvailableAt INTEGER, errorReason TEXT,
                        PRIMARY KEY(animeId, provider, episodeKey),
                        FOREIGN KEY(animeId) REFERENCES anime(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_provider_availability_animeId ON provider_availability(animeId)")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE provider_availability ADD COLUMN evidenceType TEXT")
                database.execSQL("ALTER TABLE provider_availability ADD COLUMN availabilityNotificationSentAt INTEGER")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS episode_releases (
                        sourceReleaseId TEXT NOT NULL,
                        animeId TEXT NOT NULL,
                        episodeNumber INTEGER,
                        episodeTitle TEXT,
                        expectedAt INTEGER,
                        provider TEXT,
                        metadataSource TEXT NOT NULL,
                        sourceUrl TEXT,
                        providerUrl TEXT,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(sourceReleaseId),
                        FOREIGN KEY(animeId) REFERENCES anime(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_releases_animeId ON episode_releases(animeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_releases_expectedAt ON episode_releases(expectedAt)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS anime_external_ids (
                        source TEXT NOT NULL,
                        externalId TEXT NOT NULL,
                        internalAnimeId TEXT NOT NULL,
                        sourceUrl TEXT,
                        PRIMARY KEY(source, externalId),
                        FOREIGN KEY(internalAnimeId) REFERENCES anime(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_anime_external_ids_internalAnimeId " +
                        "ON anime_external_ids(internalAnimeId)"
                )
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_import_batches (
                        importId TEXT NOT NULL,
                        source TEXT NOT NULL,
                        generatedAt INTEGER NOT NULL,
                        importedAt INTEGER NOT NULL,
                        rightsNotice TEXT NOT NULL,
                        animeCount INTEGER NOT NULL,
                        releaseCount INTEGER NOT NULL,
                        earliestReleaseAt INTEGER NOT NULL,
                        latestReleaseAt INTEGER NOT NULL,
                        PRIMARY KEY(importId)
                    )
                    """.trimIndent()
                )
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE local_import_batches_new (
                        importId TEXT NOT NULL,
                        datasetId TEXT NOT NULL,
                        contentHash TEXT NOT NULL,
                        source TEXT NOT NULL,
                        generatedAt INTEGER NOT NULL,
                        importedAt INTEGER NOT NULL,
                        rightsNotice TEXT NOT NULL,
                        animeCount INTEGER NOT NULL,
                        releaseCount INTEGER NOT NULL,
                        earliestReleaseAt INTEGER NOT NULL,
                        latestReleaseAt INTEGER NOT NULL,
                        PRIMARY KEY(importId)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO local_import_batches_new (
                        importId, datasetId, contentHash, source, generatedAt, importedAt,
                        rightsNotice, animeCount, releaseCount, earliestReleaseAt, latestReleaseAt
                    )
                    SELECT importId, 'legacy:' || importId, 'legacy:' || importId, source,
                        generatedAt, importedAt, rightsNotice, animeCount, releaseCount,
                        earliestReleaseAt, latestReleaseAt
                    FROM local_import_batches
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE local_import_batches")
                database.execSQL("ALTER TABLE local_import_batches_new RENAME TO local_import_batches")
                database.execSQL(
                    "CREATE UNIQUE INDEX index_local_import_batches_datasetId " +
                        "ON local_import_batches(datasetId)"
                )
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN seasonNumber INTEGER")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN listedAt INTEGER")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN adjustmentMinutes INTEGER")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN originalTimeWasEndOfDayMarker INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN releaseStatus TEXT NOT NULL DEFAULT 'SCHEDULED'")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN releaseLanguage TEXT")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS release_source_references (
                        referenceId TEXT NOT NULL, releaseId TEXT NOT NULL,
                        sourceKind TEXT NOT NULL, externalId TEXT, sourceUrl TEXT,
                        fetchedAt INTEGER NOT NULL, PRIMARY KEY(referenceId),
                        FOREIGN KEY(releaseId) REFERENCES episode_releases(sourceReleaseId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX index_release_source_references_releaseId ON release_source_references(releaseId)")
                database.execSQL("CREATE INDEX index_release_source_references_sourceKind_externalId ON release_source_references(sourceKind, externalId)")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS release_schedule_history (
                        historyId TEXT NOT NULL, releaseId TEXT NOT NULL,
                        previousAt INTEGER, revisedAt INTEGER NOT NULL,
                        changeSource TEXT NOT NULL, reason TEXT, releaseType TEXT,
                        detectedAt INTEGER NOT NULL, sourceUrl TEXT NOT NULL,
                        evidenceUrl TEXT, PRIMARY KEY(historyId),
                        FOREIGN KEY(releaseId) REFERENCES episode_releases(sourceReleaseId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX index_release_schedule_history_releaseId ON release_schedule_history(releaseId)")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS justwatch_title_matches (matchId TEXT NOT NULL, animeId TEXT NOT NULL, justWatchId TEXT, tmdbId TEXT, title TEXT NOT NULL, releaseYear INTEGER, contentType TEXT NOT NULL, confidence TEXT NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL, fetchedAt INTEGER NOT NULL, diagnosticMessage TEXT, PRIMARY KEY(matchId), FOREIGN KEY(animeId) REFERENCES anime(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_justwatch_title_matches_animeId ON justwatch_title_matches(animeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_justwatch_title_matches_justWatchId ON justwatch_title_matches(justWatchId)")
                database.execSQL("CREATE TABLE IF NOT EXISTS justwatch_offers (offerId TEXT NOT NULL, matchId TEXT NOT NULL, providerId TEXT NOT NULL, providerName TEXT NOT NULL, seasonNumber INTEGER, episodeNumber INTEGER, monetizationType TEXT NOT NULL, presentationType TEXT, audioLanguages TEXT NOT NULL, subtitleLanguages TEXT NOT NULL, offerUrl TEXT, fetchedAt INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(offerId), FOREIGN KEY(matchId) REFERENCES justwatch_title_matches(matchId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_justwatch_offers_matchId ON justwatch_offers(matchId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_justwatch_offers_providerId ON justwatch_offers(providerId)")
                database.execSQL("CREATE TABLE IF NOT EXISTS episode_provider_availability (availabilityId TEXT NOT NULL, releaseId TEXT NOT NULL, providerId TEXT NOT NULL, providerName TEXT NOT NULL, seasonNumber INTEGER, episodeNumber INTEGER, status TEXT NOT NULL, germanSubAvailable INTEGER, germanDubAvailable INTEGER, monetizationType TEXT, firstAvailableAt INTEGER, lastUnavailableAt INTEGER, lastCheckedAt INTEGER NOT NULL, nextCheckAt INTEGER, checkAttempt INTEGER NOT NULL, providerUrl TEXT, evidenceType TEXT NOT NULL, evidenceUrl TEXT, errorCode TEXT, source TEXT NOT NULL, PRIMARY KEY(availabilityId), FOREIGN KEY(releaseId) REFERENCES episode_releases(sourceReleaseId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_provider_availability_releaseId ON episode_provider_availability(releaseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_provider_availability_providerId ON episode_provider_availability(providerId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_provider_availability_status ON episode_provider_availability(status)")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM episode_provider_availability WHERE source = 'LOCAL_DIAGNOSTIC_DATASET' OR evidenceUrl LIKE 'local-diagnostic://%'")
                database.execSQL("DELETE FROM justwatch_offers WHERE source = 'LOCAL_DIAGNOSTIC_DATASET'")
                database.execSQL("DELETE FROM justwatch_title_matches WHERE source = 'LOCAL_DIAGNOSTIC_DATASET'")
                database.execSQL("DELETE FROM anime_external_ids WHERE source = 'JUSTWATCH' AND sourceUrl LIKE 'local-diagnostic://%'")
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS scheduled_release_notifications (releaseId TEXT NOT NULL, animeId TEXT NOT NULL, eventAt INTEGER NOT NULL, language TEXT NOT NULL, workName TEXT NOT NULL, scheduledAt INTEGER NOT NULL, PRIMARY KEY(releaseId), FOREIGN KEY(releaseId) REFERENCES episode_releases(sourceReleaseId) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_release_notifications_animeId ON scheduled_release_notifications(animeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_release_notifications_eventAt ON scheduled_release_notifications(eventAt)")
                database.execSQL("CREATE TABLE IF NOT EXISTS notification_deliveries (deliveryId TEXT NOT NULL, releaseId TEXT NOT NULL, animeId TEXT NOT NULL, eventType TEXT NOT NULL, deliveredAt INTEGER NOT NULL, notificationId INTEGER NOT NULL, PRIMARY KEY(deliveryId))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_deliveries_releaseId ON notification_deliveries(releaseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notification_deliveries_animeId ON notification_deliveries(animeId)")
            }
        }
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS justwatch_catalog_titles (justWatchId TEXT NOT NULL, internalAnimeId TEXT, title TEXT NOT NULL, releaseYear INTEGER, contentType TEXT NOT NULL, genres TEXT NOT NULL, coverUrl TEXT, justWatchUrl TEXT, providers TEXT NOT NULL, providerUrls TEXT NOT NULL, germanSubAvailable INTEGER, germanDubAvailable INTEGER, fetchedAt INTEGER NOT NULL, source TEXT NOT NULL, popularityRank INTEGER, PRIMARY KEY(justWatchId))")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_justwatch_catalog_titles_internalAnimeId ON justwatch_catalog_titles(internalAnimeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_justwatch_catalog_titles_title ON justwatch_catalog_titles(title)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_justwatch_catalog_titles_fetchedAt ON justwatch_catalog_titles(fetchedAt)")
                database.execSQL("CREATE TABLE IF NOT EXISTS justwatch_genres (genreId TEXT NOT NULL, label TEXT NOT NULL, fetchedAt INTEGER NOT NULL, source TEXT NOT NULL, PRIMARY KEY(genreId))")
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE justwatch_catalog_titles ADD COLUMN popularityRank INTEGER")
            }
        }
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE episode_provider_availability ADD COLUMN sourceAvailableAt INTEGER")
            }
        }
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS announcements (announcementId TEXT NOT NULL, dedupeKey TEXT NOT NULL, animeId TEXT, title TEXT NOT NULL, summary TEXT, type TEXT NOT NULL, seasonNumber INTEGER, oldDate INTEGER, newDate INTEGER, releaseWindow TEXT, reason TEXT, provider TEXT, publishedAt INTEGER NOT NULL, sources TEXT NOT NULL, sourceUrls TEXT NOT NULL, imageUrl TEXT, fetchedAt INTEGER NOT NULL, PRIMARY KEY(announcementId))")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_announcements_dedupeKey ON announcements(dedupeKey)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_announcements_publishedAt ON announcements(publishedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_announcements_animeId ON announcements(animeId)")
            }
        }
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE provider_references ADD COLUMN providerMarket TEXT")
                database.execSQL("DELETE FROM provider_references WHERE instr(lower(provider), 'dvd') > 0 OR instr(lower(provider), 'blu-ray') > 0 OR instr(lower(provider), 'blu ray') > 0 OR instr(lower(provider), 'bücher') > 0 OR instr(lower(provider), 'buecher') > 0 OR instr(lower(provider), 'medimops') > 0 OR instr(lower(provider), 'thalia') > 0 OR instr(lower(provider), 'hugendubel') > 0 OR instr(lower(provider), 'jpc') > 0 OR instr(lower(provider), 'zavvi') > 0 OR instr(lower(provider), 'zoxs') > 0")
                database.execSQL("UPDATE provider_references SET providerMarket = 'DE' WHERE source = 'UNOFFICIAL_JUSTWATCH_DIAGNOSTIC'")
            }
        }
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS provider_metadata_identities (identityId TEXT NOT NULL, animeId TEXT NOT NULL, provider TEXT NOT NULL, providerMarket TEXT NOT NULL, seriesId TEXT NOT NULL, seasonNumber INTEGER, episodeNumber INTEGER, seasonId TEXT, episodeId TEXT, sourceUrl TEXT, lastCheckedAt INTEGER NOT NULL, PRIMARY KEY(identityId), FOREIGN KEY(animeId) REFERENCES anime(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_provider_metadata_identities_animeId ON provider_metadata_identities(animeId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_provider_metadata_identities_animeId_provider_providerMarket ON provider_metadata_identities(animeId, provider, providerMarket)")
            }
        }
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN isHistoricalImport INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN historicalReleasedAt INTEGER")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN releaseTimePrecision TEXT NOT NULL DEFAULT 'EXACT'")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_episode_releases_isHistoricalImport ON episode_releases(isHistoricalImport)")
            }
        }
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN historicalSourcePriority INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE episode_releases ADD COLUMN historicalConflict INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_history_backfills (
                        animeId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        requestedAt INTEGER NOT NULL,
                        lastAttemptAt INTEGER,
                        completedAt INTEGER,
                        nextAttemptAt INTEGER,
                        provider TEXT,
                        importedReleaseCount INTEGER NOT NULL,
                        resultCode TEXT,
                        PRIMARY KEY(animeId),
                        FOREIGN KEY(animeId) REFERENCES favorites(animeId)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_history_backfills_status ON favorite_history_backfills(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_history_backfills_nextAttemptAt ON favorite_history_backfills(nextAttemptAt)")
                database.execSQL("""
                    INSERT OR IGNORE INTO favorite_history_backfills (
                        animeId, status, requestedAt, lastAttemptAt, completedAt,
                        nextAttemptAt, provider, importedReleaseCount, resultCode
                    )
                    SELECT animeId, 'PENDING', createdAt, NULL, NULL, NULL, NULL, 0, NULL
                    FROM favorites WHERE enabled = 1
                """.trimIndent())
            }
        }
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS release_postponements (
                        postponementId TEXT NOT NULL, releaseId TEXT, animeId TEXT,
                        title TEXT NOT NULL, seasonNumber INTEGER, episodeNumber INTEGER,
                        releaseLanguage TEXT, originalExpectedAt INTEGER, newExpectedAt INTEGER,
                        reason TEXT, direction TEXT NOT NULL, source TEXT NOT NULL,
                        sourceUrl TEXT NOT NULL, evidenceUrl TEXT, detectedAt INTEGER NOT NULL,
                        lastCheckedAt INTEGER NOT NULL, isActive INTEGER NOT NULL,
                        revision INTEGER NOT NULL, notifiedRevision INTEGER NOT NULL,
                        PRIMARY KEY(postponementId)
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_release_postponements_releaseId ON release_postponements(releaseId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_release_postponements_animeId ON release_postponements(animeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_release_postponements_detectedAt ON release_postponements(detectedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_release_postponements_isActive ON release_postponements(isActive)")
            }
        }
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE release_postponements ADD COLUMN confirmationStatus TEXT NOT NULL DEFAULT 'SINGLE_SOURCE'")
                database.execSQL("ALTER TABLE release_postponements ADD COLUMN secondarySource TEXT")
                database.execSQL("ALTER TABLE release_postponements ADD COLUMN secondarySourceUrl TEXT")
            }
        }
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE justwatch_catalog_titles ADD COLUMN description TEXT")
                database.execSQL("ALTER TABLE justwatch_catalog_titles ADD COLUMN studios TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE justwatch_catalog_titles ADD COLUMN descriptionOriginal TEXT")
                database.execSQL("ALTER TABLE justwatch_catalog_titles ADD COLUMN descriptionOriginalLanguage TEXT")
                database.execSQL("ALTER TABLE justwatch_catalog_titles ADD COLUMN descriptionGermanSource TEXT")
            }
        }
    }
}
