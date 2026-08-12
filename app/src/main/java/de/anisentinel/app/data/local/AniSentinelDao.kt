package de.anisentinel.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AniSentinelDao {
    @Query("""
        UPDATE episode_provider_availability
        SET status = CASE
                WHEN (SELECT releaseLanguage FROM episode_releases WHERE sourceReleaseId = releaseId) = 'GER_DUB' THEN 'AVAILABLE_GER_DUB'
                WHEN (SELECT releaseLanguage FROM episode_releases WHERE sourceReleaseId = releaseId) = 'GER_SUB' THEN 'AVAILABLE_GER_SUB'
                ELSE 'AVAILABLE_GER_SUB_AND_DUB'
            END,
            germanSubAvailable = CASE WHEN (SELECT releaseLanguage FROM episode_releases WHERE sourceReleaseId = releaseId) = 'GER_DUB' THEN germanSubAvailable ELSE 1 END,
            germanDubAvailable = CASE WHEN (SELECT releaseLanguage FROM episode_releases WHERE sourceReleaseId = releaseId) = 'GER_SUB' THEN germanDubAvailable ELSE 1 END,
            nextCheckAt = NULL
        WHERE firstAvailableAt IS NOT NULL AND status NOT LIKE 'AVAILABLE_%'
    """)
    suspend fun restoreConfirmedAvailabilityRows()

    @Query("""
        UPDATE episode_releases SET releaseStatus = 'AVAILABLE'
        WHERE sourceReleaseId IN (
            SELECT DISTINCT releaseId FROM episode_provider_availability WHERE firstAvailableAt IS NOT NULL
        ) AND isHistoricalImport = 0
    """)
    suspend fun restoreConfirmedReleaseStatuses()

    @Transaction
    suspend fun repairConfirmedAvailabilityDowngrades() {
        restoreConfirmedAvailabilityRows()
        restoreConfirmedReleaseStatuses()
    }
    @Query("""
        SELECT r.animeId, a.titleGerman, r.seasonNumber, h.previousAt, h.revisedAt,
               h.reason, h.detectedAt, h.sourceUrl
        FROM release_schedule_history h
        INNER JOIN episode_releases r ON r.sourceReleaseId = h.releaseId
        INNER JOIN anime a ON a.id = r.animeId
        ORDER BY h.detectedAt DESC
        LIMIT 100
    """)
    suspend fun releaseScheduleHistoryForNews(): List<ReleaseScheduleHistoryNewsRow>

    @Query("SELECT * FROM announcements ORDER BY publishedAt DESC")
    fun observeAnnouncements(): Flow<List<AnnouncementEntity>>

    @Query("SELECT * FROM announcements WHERE announcementId = :announcementId LIMIT 1")
    fun observeAnnouncement(announcementId: String): Flow<AnnouncementEntity?>

    @Query("SELECT * FROM announcements WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun announcementByDedupeKey(dedupeKey: String): AnnouncementEntity?

    @Upsert
    suspend fun upsertAnnouncements(items: List<AnnouncementEntity>)

    @Query("SELECT * FROM anime ORDER BY titleGerman")
    fun observeAnime(): Flow<List<AnimeEntity>>

    @Query("""
        SELECT DISTINCT anime.* FROM anime
        INNER JOIN episode_releases er ON er.animeId = anime.id
        WHERE er.metadataSource = 'ANIWORLD_CALENDAR'
          AND er.releaseLanguage IN ('GER_SUB', 'GER_DUB')
          AND er.expectedAt IS NOT NULL
          AND er.expectedAt >= :fromEpochSeconds
        ORDER BY anime.titleGerman COLLATE NOCASE
    """)
    fun observeActiveAniWorldAnime(fromEpochSeconds: Long): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM provider_references WHERE source = 'UNOFFICIAL_JUSTWATCH_DIAGNOSTIC' ORDER BY provider")
    fun observeJustWatchProviderReferences(): Flow<List<ProviderReferenceEntity>>

    @Query(
        """
        SELECT anime.* FROM anime
        INNER JOIN catalog_entries ON catalog_entries.animeId = anime.id
        WHERE catalog_entries.catalogType = :catalogType
        ORDER BY catalog_entries.position
        """
    )
    fun observeCatalog(catalogType: String): Flow<List<AnimeEntity>>

    @Query(
        """
        SELECT * FROM anime
        WHERE nextAiringAt >= :startEpochSeconds AND nextAiringAt < :endEpochSeconds
        ORDER BY nextAiringAt
        """
    )
    fun observeAnimeForWindow(
        startEpochSeconds: Long,
        endEpochSeconds: Long
    ): Flow<List<AnimeEntity>>

    @Query("SELECT * FROM anime WHERE anilistId IS NOT NULL ORDER BY updatedAt DESC")
    fun observeAniListAnime(): Flow<List<AnimeEntity>>

    @Query("SELECT COUNT(*) FROM anime WHERE anilistId IS NOT NULL")
    suspend fun aniListAnimeCount(): Int

    @Query("SELECT * FROM anime WHERE id = :id LIMIT 1")
    suspend fun anime(id: String): AnimeEntity?

    @Query(
        """
        SELECT anime.* FROM anime
        INNER JOIN favorites ON favorites.animeId = anime.id
        WHERE favorites.enabled = 1
        ORDER BY anime.titleGerman
        """
    )
    fun observeFavorites(): Flow<List<AnimeEntity>>

    @Query("SELECT COUNT(*) FROM favorites WHERE enabled = 1")
    fun observeActiveFavoriteCount(): Flow<Int>

    @Query("SELECT * FROM favorites WHERE animeId = :animeId LIMIT 1")
    fun observeFavorite(animeId: String): Flow<FavoriteEntity?>

    @Query("SELECT * FROM favorites WHERE animeId = :animeId LIMIT 1")
    suspend fun favorite(animeId: String): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE enabled = 1")
    suspend fun activeFavorites(): List<FavoriteEntity>

    @Query("SELECT * FROM episode_releases WHERE sourceReleaseId = :releaseId LIMIT 1")
    suspend fun release(releaseId: String): EpisodeReleaseEntity?

    @Query("SELECT * FROM episode_releases WHERE animeId = :animeId AND isHistoricalImport = 0 AND expectedAt IS NOT NULL AND expectedAt >= :fromEpochSeconds AND ((:languagePreference = 'BOTH') OR (:languagePreference = 'SUB' AND releaseLanguage = 'GER_SUB') OR (:languagePreference = 'DUB' AND releaseLanguage = 'GER_DUB')) ORDER BY expectedAt")
    suspend fun futureFavoriteReleases(animeId: String, languagePreference: String, fromEpochSeconds: Long): List<EpisodeReleaseEntity>

    @Query("SELECT er.* FROM episode_releases er INNER JOIN favorites f ON f.animeId = er.animeId WHERE f.enabled = 1 AND er.isHistoricalImport = 0 AND er.expectedAt IS NOT NULL AND er.expectedAt <= :nowEpochSeconds AND er.expectedAt >= :oldestEpochSeconds AND ((f.languagePreference = 'BOTH') OR (f.languagePreference = 'SUB' AND er.releaseLanguage = 'GER_SUB') OR (f.languagePreference = 'DUB' AND er.releaseLanguage = 'GER_DUB')) ORDER BY er.expectedAt")
    suspend fun dueFavoriteReleases(nowEpochSeconds: Long, oldestEpochSeconds: Long): List<EpisodeReleaseEntity>

    @Query("SELECT COUNT(*) FROM favorites WHERE animeId = :animeId")
    suspend fun favoriteRecordCount(animeId: String): Int

    @Query("SELECT * FROM episodes WHERE animeId = :animeId ORDER BY episodeNumber")
    suspend fun episodesForAnime(animeId: String): List<EpisodeEntity>

    @Upsert
    suspend fun upsertAnime(anime: List<AnimeEntity>)

    @Upsert
    suspend fun upsertCatalogEntries(entries: List<CatalogEntryEntity>)

    @Query("DELETE FROM catalog_entries WHERE catalogType = :catalogType")
    suspend fun deleteCatalogEntries(catalogType: String)

    @Query("SELECT COUNT(*) FROM catalog_entries WHERE catalogType = :catalogType")
    suspend fun catalogEntryCount(catalogType: String): Int

    @Query("SELECT MAX(fetchedAt) FROM catalog_entries WHERE catalogType = :catalogType")
    suspend fun latestCatalogFetch(catalogType: String): Long?

    @Query(
        """
        SELECT COUNT(*) FROM anime
        INNER JOIN catalog_entries ON catalog_entries.animeId = anime.id
        WHERE catalog_entries.catalogType = :catalogType
          AND anime.nextAiringAt IS NOT NULL
          AND anime.nextAiringAt <= :nowEpochSeconds
        """
    )
    suspend fun expiredCatalogReleaseCount(catalogType: String, nowEpochSeconds: Long): Int

    @Query("SELECT * FROM provider_references WHERE animeId = :animeId")
    fun observeProviderReferences(animeId: String): Flow<List<ProviderReferenceEntity>>

    @Query("SELECT * FROM provider_references WHERE animeId = :animeId ORDER BY provider")
    suspend fun providerReferences(animeId: String): List<ProviderReferenceEntity>

    @Query("SELECT * FROM release_source_references WHERE releaseId = :releaseId ORDER BY fetchedAt DESC")
    suspend fun releaseSourceReferences(releaseId: String): List<ReleaseSourceReferenceEntity>

    @Query("SELECT * FROM provider_availability WHERE animeId = :animeId ORDER BY checkedAt DESC")
    fun observeProviderAvailability(animeId: String): Flow<List<ProviderAvailabilityEntity>>

    @Query("SELECT * FROM provider_availability WHERE animeId = :animeId AND provider = :provider AND episodeKey = :episodeKey LIMIT 1")
    suspend fun providerAvailability(animeId: String, provider: String, episodeKey: Int): ProviderAvailabilityEntity?

    @Upsert
    suspend fun upsertProviderReference(reference: ProviderReferenceEntity)

    @Query("DELETE FROM provider_references WHERE animeId = :animeId AND provider = :provider")
    suspend fun deleteProviderReference(animeId: String, provider: String)

    @Query("""
        DELETE FROM provider_references
        WHERE instr(lower(provider), 'dvd') > 0
           OR instr(lower(provider), 'blu-ray') > 0
           OR instr(lower(provider), 'blu ray') > 0
           OR instr(lower(provider), 'bücher') > 0
           OR instr(lower(provider), 'buecher') > 0
           OR instr(lower(provider), 'medimops') > 0
           OR instr(lower(provider), 'thalia') > 0
           OR instr(lower(provider), 'hugendubel') > 0
           OR instr(lower(provider), 'jpc') > 0
           OR instr(lower(provider), 'zavvi') > 0
           OR instr(lower(provider), 'zoxs') > 0
    """)
    suspend fun deletePhysicalProviderReferences()

    @Upsert
    suspend fun upsertProviderAvailability(availability: ProviderAvailabilityEntity)

    @Upsert suspend fun upsertJustWatchMatches(matches: List<JustWatchTitleMatchEntity>)
    @Upsert suspend fun upsertJustWatchOffers(offers: List<JustWatchOfferEntity>)
    @Upsert suspend fun upsertEpisodeProviderAvailability(rows: List<EpisodeProviderAvailabilityEntity>)
    @Upsert suspend fun upsertProviderMetadataIdentity(row: ProviderMetadataIdentityEntity)

    @Query("SELECT * FROM provider_metadata_identities WHERE animeId = :animeId AND provider = :provider AND providerMarket = :market LIMIT 1")
    suspend fun providerMetadataIdentity(animeId: String, provider: String, market: String): ProviderMetadataIdentityEntity?

    @Query("SELECT * FROM provider_metadata_identities ORDER BY lastCheckedAt DESC")
    fun observeProviderMetadataIdentities(): Flow<List<ProviderMetadataIdentityEntity>>

    @Query("SELECT * FROM provider_metadata_identities WHERE providerMarket = 'DE' ORDER BY provider, animeId")
    suspend fun germanProviderMetadataIdentities(): List<ProviderMetadataIdentityEntity>

    @Query("""
        SELECT m.animeId AS animeId, a.titleGerman AS title, MIN(o.offerUrl) AS offerUrl
        FROM justwatch_offers o
        INNER JOIN justwatch_title_matches m ON m.matchId = o.matchId
        INNER JOIN anime a ON a.id = m.animeId
        WHERE lower(o.providerName) = 'crunchyroll'
          AND o.offerUrl LIKE 'https://www.crunchyroll.com/%'
          AND m.status = 'MATCHED'
        GROUP BY m.animeId
        ORDER BY MAX(o.fetchedAt) DESC
    """)
    suspend fun crunchyrollHistoryCandidates(): List<CrunchyrollHistoryCandidate>

    @Query("SELECT MIN(expectedAt) FROM episode_releases WHERE isHistoricalImport = 1 AND expectedAt IS NOT NULL")
    suspend fun earliestHistoricalReleaseAt(): Long?

    @Query("SELECT COUNT(*) FROM episode_releases WHERE isHistoricalImport = 1 AND lower(provider) = lower(:provider) AND expectedAt >= :start AND expectedAt < :endExclusive")
    suspend fun historicalReleaseCount(provider: String, start: Long, endExclusive: Long): Int

    @Query("SELECT * FROM provider_metadata_identities WHERE animeId = :animeId ORDER BY lastCheckedAt DESC")
    fun observeProviderMetadataIdentities(animeId: String): Flow<List<ProviderMetadataIdentityEntity>>
    @Upsert suspend fun upsertScheduledReleaseNotifications(rows: List<ScheduledReleaseNotificationEntity>)
    @Upsert suspend fun upsertNotificationDelivery(row: NotificationDeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claimNotificationDelivery(row: NotificationDeliveryEntity): Long

    @Query("DELETE FROM notification_deliveries WHERE deliveryId = :deliveryId")
    suspend fun deleteNotificationDelivery(deliveryId: String)

    @Query("SELECT * FROM scheduled_release_notifications ORDER BY eventAt")
    suspend fun scheduledReleaseNotifications(): List<ScheduledReleaseNotificationEntity>

    @Query("SELECT * FROM scheduled_release_notifications ORDER BY eventAt")
    fun observeScheduledReleaseNotifications(): Flow<List<ScheduledReleaseNotificationEntity>>

    @Query("DELETE FROM scheduled_release_notifications WHERE releaseId = :releaseId")
    suspend fun deleteScheduledReleaseNotification(releaseId: String)

    @Query("DELETE FROM scheduled_release_notifications WHERE animeId = :animeId")
    suspend fun deleteScheduledReleaseNotificationsForAnime(animeId: String)

    @Query("SELECT * FROM notification_deliveries WHERE deliveryId = :deliveryId LIMIT 1")
    suspend fun notificationDelivery(deliveryId: String): NotificationDeliveryEntity?

    @Query("""
        SELECT COUNT(*) FROM notification_deliveries nd
        INNER JOIN episode_releases er ON er.sourceReleaseId = nd.releaseId
        WHERE (nd.eventType = :eventType OR (:eventType = 'EPISODE_AVAILABLE' AND nd.eventType LIKE 'AVAILABLE_%'))
          AND COALESCE(er.seasonNumber, 0) = COALESCE(:seasonNumber, 0)
          AND COALESCE(er.episodeNumber, 0) = COALESCE(:episodeNumber, 0)
          AND COALESCE(er.releaseLanguage, 'UNSPECIFIED') = COALESCE(:releaseLanguage, 'UNSPECIFIED')
          AND (er.animeId = :animeId OR (er.expectedAt IS NOT NULL AND er.expectedAt = :expectedAt))
    """)
    suspend fun semanticNotificationDeliveryCount(
        animeId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
        releaseLanguage: String?,
        expectedAt: Long?,
        eventType: String
    ): Int

    @Query("SELECT * FROM notification_deliveries ORDER BY deliveredAt DESC")
    fun observeNotificationDeliveries(): Flow<List<NotificationDeliveryEntity>>

    @Query("SELECT * FROM episode_provider_availability ORDER BY lastCheckedAt DESC LIMIT 1")
    fun observeLatestEpisodeProviderAvailability(): Flow<List<EpisodeProviderAvailabilityEntity>>

    @Query("UPDATE episode_releases SET releaseStatus = :status WHERE sourceReleaseId = :releaseId")
    suspend fun updateReleaseStatus(releaseId: String, status: String)

    @Query("SELECT * FROM justwatch_title_matches WHERE animeId = :animeId ORDER BY fetchedAt DESC")
    suspend fun justWatchMatches(animeId: String): List<JustWatchTitleMatchEntity>

    @Query("SELECT * FROM justwatch_offers WHERE matchId = :matchId")
    suspend fun justWatchOffers(matchId: String): List<JustWatchOfferEntity>

    @Upsert
    suspend fun upsertJustWatchCatalogTitles(rows: List<JustWatchCatalogTitleEntity>)

    @Query("SELECT * FROM justwatch_catalog_titles ORDER BY title COLLATE NOCASE")
    fun observeJustWatchCatalogTitles(): Flow<List<JustWatchCatalogTitleEntity>>

    @Query("SELECT * FROM justwatch_catalog_titles WHERE internalAnimeId IS NOT NULL ORDER BY title COLLATE NOCASE")
    fun observeKnownAnimeJustWatchCatalogTitles(): Flow<List<JustWatchCatalogTitleEntity>>

    @Upsert
    suspend fun upsertJustWatchGenres(rows: List<JustWatchGenreEntity>)

    @Query("SELECT * FROM justwatch_genres ORDER BY label COLLATE NOCASE")
    fun observeJustWatchGenres(): Flow<List<JustWatchGenreEntity>>

    @Query("""
        SELECT offers.* FROM justwatch_offers AS offers
        INNER JOIN justwatch_title_matches AS matches ON matches.matchId = offers.matchId
        WHERE matches.animeId = :animeId
        ORDER BY offers.providerName, offers.seasonNumber, offers.episodeNumber
    """)
    fun observeJustWatchOffersForAnime(animeId: String): Flow<List<JustWatchOfferEntity>>

    @Query("SELECT * FROM episode_provider_availability WHERE releaseId = :releaseId ORDER BY lastCheckedAt DESC")
    suspend fun episodeProviderAvailability(releaseId: String): List<EpisodeProviderAvailabilityEntity>

    @Query("SELECT epa.* FROM episode_provider_availability epa INNER JOIN episode_releases er ON er.sourceReleaseId = epa.releaseId WHERE er.animeId = :animeId ORDER BY epa.lastCheckedAt DESC")
    fun observeEpisodeProviderAvailabilityForAnime(animeId: String): Flow<List<EpisodeProviderAvailabilityEntity>>

    @Query("SELECT * FROM episode_provider_availability ORDER BY lastCheckedAt DESC")
    fun observeAllEpisodeProviderAvailability(): Flow<List<EpisodeProviderAvailabilityEntity>>

    @Query("SELECT COUNT(*) FROM justwatch_title_matches WHERE status = 'AMBIGUOUS'")
    fun observeAmbiguousJustWatchCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM episode_provider_availability WHERE status = 'CHECK_FAILED'")
    fun observeFailedProviderCheckCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM episode_provider_availability")
    fun observeEpisodeProviderAvailabilityCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM justwatch_title_matches WHERE status = 'MATCHED'")
    fun observeMatchedJustWatchCount(): Flow<Int>

    @Query("SELECT * FROM episode_releases WHERE metadataSource = 'ANIWORLD_CALENDAR' AND expectedAt IS NOT NULL AND expectedAt <= :now AND (expectedAt >= :oldest OR sourceReleaseId IN (SELECT releaseId FROM episode_provider_availability WHERE nextCheckAt IS NOT NULL AND nextCheckAt <= :now)) ORDER BY expectedAt LIMIT :limit")
    suspend fun dueAniWorldReleases(now: Long, oldest: Long, limit: Int = 100): List<EpisodeReleaseEntity>

    @Query("SELECT * FROM episode_releases WHERE metadataSource = 'ANIWORLD_CALENDAR' AND expectedAt IS NOT NULL AND expectedAt > :now ORDER BY expectedAt LIMIT :limit")
    suspend fun nextAniWorldReleases(now: Long, limit: Int = 1): List<EpisodeReleaseEntity>

    @Query("SELECT * FROM episode_provider_availability WHERE availabilityId = :id LIMIT 1")
    suspend fun episodeAvailability(id: String): EpisodeProviderAvailabilityEntity?

    @Query("SELECT * FROM episode_releases WHERE expectedAt >= :startEpochSeconds AND expectedAt < :endEpochSeconds ORDER BY expectedAt")
    fun observeEpisodeReleasesForWindow(
        startEpochSeconds: Long,
        endEpochSeconds: Long
    ): Flow<List<EpisodeReleaseEntity>>

    @Transaction
    @Query("SELECT * FROM episode_releases WHERE expectedAt >= :startEpochSeconds AND expectedAt < :endEpochSeconds ORDER BY expectedAt")
    fun observeEpisodeReleasesWithAnimeForWindow(
        startEpochSeconds: Long,
        endEpochSeconds: Long
    ): Flow<List<EpisodeReleaseWithAnime>>

    /** UI history: historical provider releases are real evidence for favorite classification. */
    @Query("SELECT er.* FROM episode_releases er INNER JOIN favorites f ON f.animeId = er.animeId WHERE f.enabled = 1 AND er.releaseLanguage IN ('GER_SUB', 'GER_DUB') ORDER BY er.expectedAt")
    fun observeFavoriteReleasesForClassification(): Flow<List<EpisodeReleaseEntity>>

    /** Background safety boundary: historical imports must never become scheduler input. */
    @Query("SELECT er.* FROM episode_releases er INNER JOIN favorites f ON f.animeId = er.animeId WHERE f.enabled = 1 AND er.isHistoricalImport = 0 AND er.releaseLanguage IN ('GER_SUB', 'GER_DUB') ORDER BY er.expectedAt")
    fun observeSchedulableFavoriteReleases(): Flow<List<EpisodeReleaseEntity>>

    @Query("SELECT * FROM episode_releases ORDER BY expectedAt")
    fun observeAllEpisodeReleases(): Flow<List<EpisodeReleaseEntity>>

    @Query("SELECT * FROM episode_releases WHERE animeId = :animeId ORDER BY expectedAt DESC")
    fun observeEpisodeReleasesForAnime(animeId: String): Flow<List<EpisodeReleaseEntity>>

    @Query("""
        SELECT * FROM episode_releases
        WHERE metadataSource = 'ANIWORLD_CALENDAR'
          AND releaseLanguage IN ('GER_SUB', 'GER_DUB')
          AND expectedAt >= :fromEpochSeconds
        ORDER BY expectedAt
    """)
    fun observeActiveAniWorldReleases(fromEpochSeconds: Long): Flow<List<EpisodeReleaseEntity>>

    @Upsert
    suspend fun upsertEpisodeReleases(releases: List<EpisodeReleaseEntity>)

    @Query("SELECT * FROM episode_releases WHERE animeId = :animeId AND COALESCE(seasonNumber, 0) = COALESCE(:seasonNumber, 0) AND episodeNumber = :episodeNumber AND releaseLanguage = :releaseLanguage AND lower(COALESCE(provider, '')) = lower(:provider) ORDER BY isHistoricalImport ASC LIMIT 1")
    suspend fun semanticProviderRelease(animeId: String, seasonNumber: Int?, episodeNumber: Int, releaseLanguage: String, provider: String): EpisodeReleaseEntity?

    @Transaction
    suspend fun importHistoricalProviderReleases(
        releases: List<EpisodeReleaseEntity>,
        references: List<ReleaseSourceReferenceEntity>
    ) {
        upsertEpisodeReleases(releases)
        upsertReleaseSourceReferences(references)
    }

    @Upsert
    suspend fun upsertReleaseSourceReferences(references: List<ReleaseSourceReferenceEntity>)

    @Upsert
    suspend fun upsertReleaseScheduleHistory(history: List<ReleaseScheduleHistoryEntity>)

    @Query("SELECT COUNT(*) FROM release_schedule_history")
    fun observeReleaseScheduleHistoryCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM release_schedule_history")
    suspend fun releaseScheduleHistoryCount(): Int

    @Query("SELECT * FROM release_schedule_history WHERE detectedAt >= :sinceEpochSeconds ORDER BY detectedAt")
    suspend fun releaseScheduleHistorySince(sinceEpochSeconds: Long): List<ReleaseScheduleHistoryEntity>

    @Query(
        """
        SELECT h.historyId, h.releaseId, a.titleGerman, r.seasonNumber, r.episodeNumber, r.releaseLanguage,
               h.previousAt, h.revisedAt, h.releaseType, h.reason, h.sourceUrl
        FROM release_schedule_history h
        INNER JOIN episode_releases r ON r.sourceReleaseId = h.releaseId
        INNER JOIN anime a ON a.id = r.animeId
        ORDER BY h.detectedAt DESC, h.historyId
        LIMIT 3
        """
    )
    fun observeLatestReleaseScheduleHistory(): Flow<List<ReleaseScheduleHistorySummary>>

    @Query(
        """
        SELECT h.historyId, h.releaseId, a.titleGerman, r.seasonNumber, r.episodeNumber, r.releaseLanguage,
               h.previousAt, h.revisedAt, h.releaseType, h.reason, h.sourceUrl
        FROM release_schedule_history h
        INNER JOIN episode_releases r ON r.sourceReleaseId = h.releaseId
        INNER JOIN anime a ON a.id = r.animeId
        ORDER BY h.detectedAt DESC, h.historyId
        """
    )
    fun observeAllReleaseScheduleHistory(): Flow<List<ReleaseScheduleHistorySummary>>

    @Query("SELECT * FROM anime")
    suspend fun allAnime(): List<AnimeEntity>

    @Transaction
    @Query("SELECT * FROM episode_releases WHERE metadataSource = :source")
    suspend fun releaseRowsForSource(source: String): List<EpisodeReleaseWithAnime>

    @Query("DELETE FROM episode_releases WHERE sourceReleaseId = :releaseId")
    suspend fun deleteEpisodeRelease(releaseId: String)

    @Query("DELETE FROM favorites WHERE animeId = :animeId")
    suspend fun deleteFavorite(animeId: String)

    @Query("""
        DELETE FROM episode_releases
        WHERE animeId LIKE 'aniworld:episode-%'
          AND EXISTS (
              SELECT 1 FROM episode_releases canonical
              WHERE canonical.animeId NOT LIKE 'aniworld:episode-%'
                AND canonical.seasonNumber IS episode_releases.seasonNumber
                AND canonical.episodeNumber IS episode_releases.episodeNumber
                AND canonical.releaseLanguage IS episode_releases.releaseLanguage
                AND canonical.expectedAt IS episode_releases.expectedAt
          )
    """)
    suspend fun deleteMalformedAniWorldDuplicateReleases(): Int

    @Query("""
        DELETE FROM favorites
        WHERE animeId LIKE 'aniworld:episode-%'
          AND animeId NOT IN (SELECT animeId FROM episode_releases)
    """)
    suspend fun deleteMalformedAniWorldOrphanFavorites(): Int

    @Transaction
    suspend fun repairMalformedAniWorldEpisodeIdentities(): Int {
        val rows = releaseRowsForSource("ANIWORLD_CALENDAR")
        var repaired = 0
        rows.groupBy {
            listOf(
                it.release.seasonNumber,
                it.release.episodeNumber,
                it.release.releaseLanguage,
                it.release.expectedAt
            )
        }.values.forEach { candidates ->
            val canonical = candidates.firstOrNull {
                !it.release.animeId.startsWith("aniworld:episode-")
            } ?: return@forEach
            candidates.filter {
                it.release.animeId.startsWith("aniworld:episode-") &&
                    it.release.sourceReleaseId != canonical.release.sourceReleaseId
            }.forEach { malformed ->
                val malformedFavorite = favorite(malformed.release.animeId)
                val canonicalFavorite = favorite(canonical.release.animeId)
                if (malformedFavorite?.enabled == true && canonicalFavorite?.enabled != true) {
                    upsertFavorite(malformedFavorite.copy(animeId = canonical.release.animeId))
                }
                deleteEpisodeRelease(malformed.release.sourceReleaseId)
                deleteFavorite(malformed.release.animeId)
                repaired++
            }
        }
        repaired += deleteMalformedAniWorldDuplicateReleases()
        deleteMalformedAniWorldOrphanFavorites()
        return repaired
    }

    @Query(
        """
        DELETE FROM episode_releases
        WHERE expectedAt >= :fromEpochSeconds
          AND expectedAt < :untilEpochSeconds
          AND expectedAt >= :preserveBeforeEpochSeconds
          AND metadataSource = :metadataSource
          AND sourceReleaseId NOT IN (SELECT releaseId FROM release_schedule_history)
        """
    )
    suspend fun deleteReleaseSourceRange(
        fromEpochSeconds: Long,
        untilEpochSeconds: Long,
        preserveBeforeEpochSeconds: Long,
        metadataSource: String
    ): Int

    @Query(
        """
        UPDATE anime
        SET nextAiringAt = (
                SELECT MIN(er.expectedAt)
                FROM episode_releases AS er
                WHERE er.animeId = anime.id AND er.expectedAt >= :nowEpochSeconds
            ),
            nextEpisode = (
                SELECT er.episodeNumber
                FROM episode_releases AS er
                WHERE er.animeId = anime.id AND er.expectedAt >= :nowEpochSeconds
                ORDER BY er.expectedAt ASC
                LIMIT 1
            )
        """
    )
    suspend fun refreshNextAiring(nowEpochSeconds: Long)

    @Transaction
    suspend fun replaceReleaseSourceRange(
        fromEpochSeconds: Long,
        untilEpochSeconds: Long,
        metadataSource: String,
        anime: List<AnimeEntity>,
        releases: List<EpisodeReleaseEntity>,
        nowEpochSeconds: Long
    ) {
        upsertAnime(anime)
        deleteReleaseSourceRange(fromEpochSeconds, untilEpochSeconds, nowEpochSeconds, metadataSource)
        upsertEpisodeReleases(releases)
        refreshNextAiring(nowEpochSeconds)
    }

    @Transaction
    suspend fun replaceAniWorldReleaseRange(
        fromEpochSeconds: Long,
        untilEpochSeconds: Long,
        anime: List<AnimeEntity>,
        releases: List<EpisodeReleaseEntity>,
        references: List<ReleaseSourceReferenceEntity>,
        nowEpochSeconds: Long
    ) {
        upsertAnime(anime)
        deleteReleaseSourceRange(fromEpochSeconds, untilEpochSeconds, nowEpochSeconds, "ANIWORLD_CALENDAR")
        upsertEpisodeReleases(releases)
        upsertReleaseSourceReferences(references)
        refreshNextAiring(nowEpochSeconds)
    }

    @Upsert
    suspend fun upsertExternalIds(ids: List<AnimeExternalIdEntity>)

    @Query("SELECT * FROM anime_external_ids WHERE source = :source AND externalId = :externalId LIMIT 1")
    suspend fun externalId(source: String, externalId: String): AnimeExternalIdEntity?

    @Upsert
    suspend fun upsertImportBatch(batch: LocalImportBatchEntity)

    @Query("SELECT * FROM local_import_batches ORDER BY importedAt DESC LIMIT 1")
    suspend fun latestImportBatch(): LocalImportBatchEntity?

    @Query("SELECT * FROM local_import_batches WHERE datasetId = :datasetId LIMIT 1")
    suspend fun importBatch(datasetId: String): LocalImportBatchEntity?

    @Transaction
    suspend fun importDiagnosticCalendar(
        anime: List<AnimeEntity>,
        externalIds: List<AnimeExternalIdEntity>,
        releases: List<EpisodeReleaseEntity>,
        batch: LocalImportBatchEntity
    ) {
        upsertAnime(anime)
        upsertExternalIds(externalIds)
        upsertEpisodeReleases(releases)
        upsertImportBatch(batch)
    }

    @Query("SELECT MAX(fetchedAt) FROM episode_releases WHERE expectedAt >= :startEpochSeconds AND expectedAt < :endEpochSeconds AND metadataSource = :source")
    suspend fun latestReleaseFetch(
        startEpochSeconds: Long,
        endEpochSeconds: Long,
        source: String
    ): Long?

    @Query(
        """
        DELETE FROM episode_releases
        WHERE (expectedAt < :startEpochSeconds OR expectedAt >= :endEpochSeconds)
          AND isHistoricalImport = 0
          AND animeId NOT IN (SELECT animeId FROM favorites WHERE enabled = 1)
          AND NOT EXISTS (
              SELECT 1 FROM provider_availability
              WHERE provider_availability.animeId = episode_releases.animeId
                AND provider_availability.episodeNumber = episode_releases.episodeNumber
                AND provider_availability.status = 'AVAILABLE'
          )
        """
    )
    suspend fun deleteUnprotectedReleasesOutsideWindow(
        startEpochSeconds: Long,
        endEpochSeconds: Long
    ): Int

    @Transaction
    suspend fun upsertAniSearchCalendarData(
        anime: List<AnimeEntity>,
        releases: List<EpisodeReleaseEntity>,
        providers: List<ProviderReferenceEntity>
    ) {
        upsertAnime(anime)
        upsertEpisodeReleases(releases)
        providers.forEach { upsertProviderReference(it) }
    }

    @Query("UPDATE provider_availability SET availabilityNotificationSentAt = :sentAt WHERE animeId = :animeId AND provider = :provider AND episodeKey = :episodeKey AND availabilityNotificationSentAt IS NULL")
    suspend fun markAvailabilityNotificationSent(
        animeId: String,
        provider: String,
        episodeKey: Int,
        sentAt: Long
    ): Int

    @Transaction
    suspend fun replaceCatalog(
        catalogType: String,
        anime: List<AnimeEntity>,
        entries: List<CatalogEntryEntity>
    ) {
        upsertAnime(anime)
        deleteCatalogEntries(catalogType)
        upsertCatalogEntries(entries)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAnimeIfAbsent(anime: AnimeEntity)

    @Upsert
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Upsert
    suspend fun upsertEpisodes(episodes: List<EpisodeEntity>)

    @Upsert
    suspend fun upsertWatchProfile(profile: WatchProfileEntity)

    @Upsert
    suspend fun upsertWatchPhases(phases: List<WatchPhaseEntity>)

    @Transaction
    suspend fun replaceProfile(profile: WatchProfileEntity, phases: List<WatchPhaseEntity>) {
        upsertWatchProfile(profile)
        deletePhases(profile.id)
        upsertWatchPhases(phases)
    }

    @Query("DELETE FROM watch_phases WHERE profileId = :profileId")
    suspend fun deletePhases(profileId: String)

    @Query("SELECT * FROM watch_phases WHERE profileId = :profileId ORDER BY startOffsetSeconds")
    suspend fun phasesForProfile(profileId: String): List<WatchPhaseEntity>
}
