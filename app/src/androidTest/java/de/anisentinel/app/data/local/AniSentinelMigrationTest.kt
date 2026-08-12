package de.anisentinel.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AniSentinelMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AniSentinelDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3PreservesAnimeAndFavorite() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO anime (
                    id, anilistId, anisearchId, titleGerman, titleEnglish, titleRomaji,
                    titleNative, description, coverUrl, bannerUrl, season, seasonYear,
                    totalEpisodes, updatedAt, nextAiringAt, nextEpisode
                ) VALUES ('kept', 42, NULL, 'Kept', NULL, NULL, NULL, '', NULL, NULL,
                    NULL, NULL, NULL, 100, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO favorites (
                    animeId, enabled, languagePreference, monitoringProfileId,
                    notifyAvailable, notifyDelayed, notifyPostponed, createdAt
                ) VALUES ('kept', 1, 'BOTH', NULL, 1, 1, 1, 100)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            3,
            true,
            AniSentinelDatabase.MIGRATION_2_3
        ).use { database ->
            database.query("SELECT sourceUpdatedAt, cachedAt FROM anime WHERE id = 'kept'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(true, it.isNull(0))
                assertEquals(true, it.isNull(1))
            }
            database.query("SELECT COUNT(*) FROM favorites WHERE animeId = 'kept'").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate7To8SupportsNoImportBatches() {
        assertMigration7To8(batchCount = 0)
    }

    @Test
    fun migrate7To8SupportsOneImportBatch() {
        assertMigration7To8(batchCount = 1)
    }

    @Test
    fun migrate7To8SupportsMultipleImportBatches() {
        assertMigration7To8(batchCount = 3)
    }

    @Test
    fun migrate11To12CreatesPersistentNotificationTables() {
        val databaseName = "migration-11-12"
        helper.createDatabase(databaseName, 11).close()

        helper.runMigrationsAndValidate(
            databaseName,
            12,
            true,
            AniSentinelDatabase.MIGRATION_11_12
        ).use { database ->
            database.query("SELECT COUNT(*) FROM scheduled_release_notifications").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM notification_deliveries").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate16To17AddsAndBackfillsGermanProviderMarket() {
        val databaseName = "migration-16-17"
        helper.createDatabase(databaseName, 16).apply {
            execSQL(
                "INSERT INTO anime (id, anilistId, anisearchId, titleGerman, titleEnglish, titleRomaji, titleNative, description, coverUrl, bannerUrl, season, seasonYear, totalEpisodes, updatedAt, nextAiringAt, nextEpisode, sourceUpdatedAt, cachedAt) VALUES ('market-test', NULL, NULL, 'Test', NULL, NULL, NULL, '', NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, NULL)"
            )
            execSQL(
                "INSERT INTO provider_references (animeId, provider, seriesUrl, source, sourceUrl, lastConfirmedAt) VALUES ('market-test', 'Crunchyroll', NULL, 'UNOFFICIAL_JUSTWATCH_DIAGNOSTIC', NULL, 100)"
            )
            execSQL(
                "INSERT INTO provider_references (animeId, provider, seriesUrl, source, sourceUrl, lastConfirmedAt) VALUES ('market-test', 'Amazon DVD / Blu-ray', NULL, 'UNOFFICIAL_JUSTWATCH_DIAGNOSTIC', NULL, 100)"
            )
            close()
        }
        helper.runMigrationsAndValidate(
            databaseName, 17, true, AniSentinelDatabase.MIGRATION_16_17
        ).use { database ->
            database.query("SELECT providerMarket FROM provider_references WHERE animeId = 'market-test'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals("DE", it.getString(0))
                assertEquals(1, it.count)
            }
        }
    }

    @Test
    fun migration17To18AddsStableProviderMetadataIdentities() {
        val databaseName = "migration-17-18"
        helper.createDatabase(databaseName, 17).close()
        helper.runMigrationsAndValidate(databaseName, 18, true, AniSentinelDatabase.MIGRATION_17_18).use { database ->
            database.query("SELECT COUNT(*) FROM provider_metadata_identities").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun migration18To19AddsHistoricalReleaseSafetyFields() {
        val databaseName = "migration-18-19"
        helper.createDatabase(databaseName, 18).close()
        helper.runMigrationsAndValidate(databaseName, 19, true, AniSentinelDatabase.MIGRATION_18_19).use { database ->
            database.query("PRAGMA table_info(episode_releases)").use { cursor ->
                val names = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(true, "isHistoricalImport" in names)
                assertEquals(true, "historicalReleasedAt" in names)
                assertEquals(true, "releaseTimePrecision" in names)
            }
        }
    }

    @Test
    fun migration19To20AddsHistoricalConflictMetadata() {
        val databaseName = "migration-19-20"
        helper.createDatabase(databaseName, 19).close()
        helper.runMigrationsAndValidate(databaseName, 20, true, AniSentinelDatabase.MIGRATION_19_20).use { database ->
            database.query("PRAGMA table_info(episode_releases)").use { cursor ->
                val names = buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(true, "historicalSourcePriority" in names)
                assertEquals(true, "historicalConflict" in names)
            }
        }
    }

    @Test
    fun migration20To21QueuesEnabledFavoritesForHistoricalBackfill() {
        val databaseName = "migration-20-21"
        helper.createDatabase(databaseName, 20).apply {
            execSQL("INSERT INTO anime (id, titleGerman, description, updatedAt) VALUES ('fav', 'Favorit', '', 1)")
            execSQL("INSERT INTO favorites (animeId, enabled, languagePreference, monitoringProfileId, notifyAvailable, notifyDelayed, notifyPostponed, createdAt) VALUES ('fav', 1, 'BOTH', NULL, 1, 1, 1, 42)")
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 21, true, AniSentinelDatabase.MIGRATION_20_21).use { database ->
            database.query("SELECT status, requestedAt FROM favorite_history_backfills WHERE animeId='fav'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals("PENDING", it.getString(0))
                assertEquals(42L, it.getLong(1))
            }
        }
    }

    private fun assertMigration7To8(batchCount: Int) {
        val databaseName = "migration-7-8-$batchCount"
        helper.createDatabase(databaseName, 7).apply {
            repeat(batchCount) { index ->
                execSQL(
                    """
                    INSERT INTO local_import_batches (
                        importId, source, generatedAt, importedAt, rightsNotice,
                        animeCount, releaseCount, earliestReleaseAt, latestReleaseAt
                    ) VALUES (
                        'import-$index', 'legacy source', 100, 200, 'legacy rights',
                        1, 2, 300, 400
                    )
                    """.trimIndent()
                )
            }
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            8,
            true,
            AniSentinelDatabase.MIGRATION_7_8
        ).use { database ->
            database.query(
                "SELECT importId, datasetId, contentHash FROM local_import_batches ORDER BY importId"
            ).use { cursor ->
                assertEquals(batchCount, cursor.count)
                while (cursor.moveToNext()) {
                    val expected = "legacy:${cursor.getString(0)}"
                    assertEquals(expected, cursor.getString(1))
                    assertEquals(expected, cursor.getString(2))
                }
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-2-3"
    }
}
