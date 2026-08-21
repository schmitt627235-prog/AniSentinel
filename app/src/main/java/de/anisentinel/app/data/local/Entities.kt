package de.anisentinel.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: String,
    val anilistId: Int?,
    val anisearchId: String?,
    val titleGerman: String,
    val titleEnglish: String?,
    val titleRomaji: String?,
    val titleNative: String?,
    val description: String,
    val coverUrl: String?,
    val bannerUrl: String?,
    val season: String?,
    val seasonYear: Int?,
    val totalEpisodes: Int?,
    val updatedAt: Long,
    val nextAiringAt: Long? = null,
    val nextEpisode: Int? = null,
    val sourceUpdatedAt: Long? = null,
    val cachedAt: Long? = null
)

@Entity(
    tableName = "anime_external_ids",
    primaryKeys = ["source", "externalId"],
    foreignKeys = [ForeignKey(
        entity = AnimeEntity::class,
        parentColumns = ["id"],
        childColumns = ["internalAnimeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("internalAnimeId")]
)
data class AnimeExternalIdEntity(
    val source: String,
    val externalId: String,
    val internalAnimeId: String,
    val sourceUrl: String?
)

@Entity(tableName = "local_import_batches", indices = [Index(value = ["datasetId"], unique = true)])
data class LocalImportBatchEntity(
    @PrimaryKey val importId: String,
    val datasetId: String,
    val contentHash: String,
    val source: String,
    val generatedAt: Long,
    val importedAt: Long,
    val rightsNotice: String,
    val animeCount: Int,
    val releaseCount: Int,
    val earliestReleaseAt: Long,
    val latestReleaseAt: Long
)

@Entity(
    tableName = "catalog_entries",
    primaryKeys = ["catalogType", "animeId"],
    foreignKeys = [
        ForeignKey(
            entity = AnimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["animeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("animeId"), Index(value = ["catalogType", "position"], unique = true)]
)
data class CatalogEntryEntity(
    val catalogType: String,
    val animeId: String,
    val position: Int,
    val fetchedAt: Long
)

@Entity(
    tableName = "provider_references",
    primaryKeys = ["animeId", "provider"],
    foreignKeys = [ForeignKey(
        entity = AnimeEntity::class,
        parentColumns = ["id"],
        childColumns = ["animeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("animeId")]
)
data class ProviderReferenceEntity(
    val animeId: String,
    val provider: String,
    val seriesUrl: String?,
    val source: String,
    val sourceUrl: String?,
    val lastConfirmedAt: Long?,
    val providerMarket: String? = null
)

@Entity(
    tableName = "provider_availability",
    primaryKeys = ["animeId", "provider", "episodeKey"],
    foreignKeys = [ForeignKey(
        entity = AnimeEntity::class,
        parentColumns = ["id"],
        childColumns = ["animeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("animeId")]
)
data class ProviderAvailabilityEntity(
    val animeId: String,
    val provider: String,
    val episodeKey: Int,
    val episodeNumber: Int?,
    val status: String,
    val providerUrl: String?,
    val checkedAt: Long,
    val firstAvailableAt: Long?,
    val errorReason: String?,
    val evidenceType: String? = null,
    val availabilityNotificationSentAt: Long? = null
)

@Entity(
    tableName = "justwatch_title_matches",
    foreignKeys = [ForeignKey(entity = AnimeEntity::class, parentColumns = ["id"], childColumns = ["animeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animeId"), Index("justWatchId")]
)
data class JustWatchTitleMatchEntity(
    @PrimaryKey val matchId: String,
    val animeId: String,
    val justWatchId: String?,
    val tmdbId: String?,
    val title: String,
    val releaseYear: Int?,
    val contentType: String,
    val confidence: String,
    val status: String,
    val source: String,
    val fetchedAt: Long,
    val diagnosticMessage: String?
)

@Entity(
    tableName = "justwatch_catalog_titles",
    indices = [Index("internalAnimeId"), Index("title"), Index("fetchedAt")]
)
data class JustWatchCatalogTitleEntity(
    @PrimaryKey val justWatchId: String,
    val internalAnimeId: String?,
    val title: String,
    val releaseYear: Int?,
    val contentType: String,
    val genres: String,
    val coverUrl: String?,
    val justWatchUrl: String?,
    val providers: String,
    val providerUrls: String,
    val germanSubAvailable: Boolean?,
    val germanDubAvailable: Boolean?,
    val fetchedAt: Long,
    val source: String,
    val popularityRank: Int?,
    val description: String? = null,
    val studios: String = "",
    val descriptionOriginal: String? = null,
    val descriptionOriginalLanguage: String? = null,
    val descriptionGermanSource: String? = null
)

@Entity(tableName = "justwatch_genres")
data class JustWatchGenreEntity(
    @PrimaryKey val genreId: String,
    val label: String,
    val fetchedAt: Long,
    val source: String
)

@Entity(
    tableName = "justwatch_offers",
    foreignKeys = [ForeignKey(entity = JustWatchTitleMatchEntity::class, parentColumns = ["matchId"], childColumns = ["matchId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("matchId"), Index("providerId")]
)
data class JustWatchOfferEntity(
    @PrimaryKey val offerId: String,
    val matchId: String,
    val providerId: String,
    val providerName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val monetizationType: String,
    val presentationType: String?,
    val audioLanguages: String,
    val subtitleLanguages: String,
    val offerUrl: String?,
    val fetchedAt: Long,
    val source: String
)

data class CrunchyrollHistoryCandidate(
    val animeId: String,
    val title: String,
    val offerUrl: String
)

@Entity(
    tableName = "episode_provider_availability",
    foreignKeys = [ForeignKey(entity = EpisodeReleaseEntity::class, parentColumns = ["sourceReleaseId"], childColumns = ["releaseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("releaseId"), Index("providerId"), Index("status")]
)
data class EpisodeProviderAvailabilityEntity(
    @PrimaryKey val availabilityId: String,
    val releaseId: String,
    val providerId: String,
    val providerName: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val status: String,
    val germanSubAvailable: Boolean?,
    val germanDubAvailable: Boolean?,
    val monetizationType: String?,
    val firstAvailableAt: Long?,
    val lastUnavailableAt: Long?,
    val lastCheckedAt: Long,
    val nextCheckAt: Long?,
    val checkAttempt: Int,
    val providerUrl: String?,
    val evidenceType: String,
    val evidenceUrl: String?,
    val errorCode: String?,
    val source: String,
    val sourceAvailableAt: Long? = null
)

@Entity(
    tableName = "provider_metadata_identities",
    foreignKeys = [ForeignKey(entity = AnimeEntity::class, parentColumns = ["id"], childColumns = ["animeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animeId"), Index(value = ["animeId", "provider", "providerMarket"], unique = true)]
)
data class ProviderMetadataIdentityEntity(
    @PrimaryKey val identityId: String,
    val animeId: String,
    val provider: String,
    val providerMarket: String,
    val seriesId: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val seasonId: String?,
    val episodeId: String?,
    val sourceUrl: String?,
    val lastCheckedAt: Long
)

/** Canonical season known for an anime, independent from any provider catalogue numbering. */
@Entity(
    tableName = "anime_seasons",
    primaryKeys = ["animeId", "canonicalSeasonNumber"],
    foreignKeys = [ForeignKey(entity = AnimeEntity::class, parentColumns = ["id"], childColumns = ["animeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animeId")]
)
data class AnimeSeasonEntity(
    val animeId: String,
    val canonicalSeasonNumber: Int,
    val source: String,
    val confirmedAt: Long
)

/** Provider catalogue season mapped to a canonical anime season for the German market. */
@Entity(
    tableName = "provider_season_mappings",
    primaryKeys = ["animeId", "canonicalSeasonNumber", "provider"],
    foreignKeys = [ForeignKey(entity = AnimeEntity::class, parentColumns = ["id"], childColumns = ["animeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animeId"), Index(value = ["animeId", "canonicalSeasonNumber"])]
)
data class ProviderSeasonMappingEntity(
    val animeId: String,
    val canonicalSeasonNumber: Int,
    val provider: String,
    val providerSeasonNumber: Int?,
    val providerSeriesId: String?,
    val providerSeasonId: String?,
    val providerSeriesUrl: String?,
    val region: String,
    val available: Boolean,
    val lastConfirmedAt: Long,
    val providerSeasonLabel: String? = null
)

/** seasonNumber 0 is the anime-wide default; positive values are explicit season overrides. */
@Entity(
    tableName = "provider_preferences",
    primaryKeys = ["animeId", "seasonNumber"],
    foreignKeys = [ForeignKey(entity = AnimeEntity::class, parentColumns = ["id"], childColumns = ["animeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("animeId")]
)
data class ProviderPreferenceEntity(
    val animeId: String,
    val seasonNumber: Int,
    val provider: String,
    val updatedAt: Long
)

/** Persistent provider-wide technical failure streak used to suppress transient push spam. */
@Entity(tableName = "provider_failure_states")
data class ProviderFailureStateEntity(
    @PrimaryKey val providerKey: String,
    val consecutiveFailures: Int,
    val firstFailureAt: Long,
    val lastFailureAt: Long,
    val lastErrorCode: String?,
    val lastNotifiedAt: Long?
)

@Entity(
    tableName = "episode_releases",
    foreignKeys = [ForeignKey(
        entity = AnimeEntity::class,
        parentColumns = ["id"],
        childColumns = ["animeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("animeId"), Index("expectedAt"), Index("isHistoricalImport")]
)
data class EpisodeReleaseEntity(
    @PrimaryKey val sourceReleaseId: String,
    val animeId: String,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val expectedAt: Long?,
    val provider: String?,
    val metadataSource: String,
    val sourceUrl: String?,
    val providerUrl: String?,
    val fetchedAt: Long,
    val seasonNumber: Int? = null,
    val listedAt: Long? = null,
    val adjustmentMinutes: Int? = null,
    val originalTimeWasEndOfDayMarker: Boolean = false,
    val releaseStatus: String = "SCHEDULED",
    val releaseLanguage: String? = null,
    val isHistoricalImport: Boolean = false,
    val historicalReleasedAt: Long? = null,
    val releaseTimePrecision: String = "EXACT",
    val historicalSourcePriority: Int = 0,
    val historicalConflict: Boolean = false
)

@Entity(
    tableName = "release_source_references",
    foreignKeys = [ForeignKey(
        entity = EpisodeReleaseEntity::class,
        parentColumns = ["sourceReleaseId"],
        childColumns = ["releaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("releaseId"), Index(value = ["sourceKind", "externalId"])]
)
data class ReleaseSourceReferenceEntity(
    @PrimaryKey val referenceId: String,
    val releaseId: String,
    val sourceKind: String,
    val externalId: String?,
    val sourceUrl: String?,
    val fetchedAt: Long
)

@Entity(
    tableName = "release_schedule_history",
    foreignKeys = [ForeignKey(
        entity = EpisodeReleaseEntity::class,
        parentColumns = ["sourceReleaseId"],
        childColumns = ["releaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("releaseId")]
)
data class ReleaseScheduleHistoryEntity(
    @PrimaryKey val historyId: String,
    val releaseId: String,
    val previousAt: Long?,
    val revisedAt: Long,
    val changeSource: String,
    val reason: String?,
    val releaseType: String?,
    val detectedAt: Long,
    val sourceUrl: String,
    val evidenceUrl: String?
)

data class ReleaseScheduleHistorySummary(
    val historyId: String,
    val releaseId: String,
    val titleGerman: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseLanguage: String?,
    val previousAt: Long?,
    val revisedAt: Long,
    val releaseType: String?,
    val reason: String?,
    val sourceUrl: String
)

data class ReleaseScheduleHistoryNewsRow(
    val animeId: String,
    val titleGerman: String,
    val seasonNumber: Int?,
    val previousAt: Long?,
    val revisedAt: Long,
    val reason: String?,
    val detectedAt: Long,
    val sourceUrl: String
)

@Entity(
    tableName = "release_postponements",
    indices = [Index("releaseId"), Index("animeId"), Index("detectedAt"), Index("isActive")]
)
data class ReleasePostponementEntity(
    @PrimaryKey val postponementId: String,
    val releaseId: String?,
    val animeId: String?,
    val title: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val releaseLanguage: String?,
    val originalExpectedAt: Long?,
    val newExpectedAt: Long?,
    val reason: String?,
    val direction: String,
    val source: String,
    val sourceUrl: String,
    val evidenceUrl: String?,
    val detectedAt: Long,
    val lastCheckedAt: Long,
    val isActive: Boolean,
    val revision: Int,
    val notifiedRevision: Int,
    val confirmationStatus: String = "SINGLE_SOURCE",
    val secondarySource: String? = null,
    val secondarySourceUrl: String? = null
)

@Entity(
    tableName = "announcements",
    indices = [Index(value = ["dedupeKey"], unique = true), Index("publishedAt"), Index("animeId")]
)
data class AnnouncementEntity(
    @PrimaryKey val announcementId: String,
    val dedupeKey: String,
    val animeId: String?,
    val title: String,
    val summary: String?,
    val type: String,
    val seasonNumber: Int?,
    val oldDate: Long?,
    val newDate: Long?,
    val releaseWindow: String?,
    val reason: String?,
    val provider: String?,
    val publishedAt: Long,
    val sources: String,
    val sourceUrls: String,
    val imageUrl: String?,
    val fetchedAt: Long
)

data class EpisodeReleaseWithAnime(
    @Embedded val release: EpisodeReleaseEntity,
    @Relation(parentColumn = "animeId", entityColumn = "id")
    val anime: AnimeEntity,
    @Relation(parentColumn = "animeId", entityColumn = "animeId")
    val providerReferences: List<ProviderReferenceEntity>,
    @Relation(parentColumn = "animeId", entityColumn = "animeId")
    val availability: List<ProviderAvailabilityEntity>,
    @Relation(parentColumn = "sourceReleaseId", entityColumn = "releaseId")
    val history: List<ReleaseScheduleHistoryEntity>
    ,@Relation(parentColumn = "sourceReleaseId", entityColumn = "releaseId")
    val episodeAvailability: List<EpisodeProviderAvailabilityEntity>
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = AnimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["animeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("animeId")]
)
data class FavoriteEntity(
    @PrimaryKey val animeId: String,
    val enabled: Boolean,
    val languagePreference: String,
    val monitoringProfileId: String?,
    val notifyAvailable: Boolean,
    val notifyDelayed: Boolean,
    val notifyPostponed: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "favorite_history_backfills",
    foreignKeys = [ForeignKey(
        entity = FavoriteEntity::class,
        parentColumns = ["animeId"],
        childColumns = ["animeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("status"), Index("nextAttemptAt")]
)
data class FavoriteHistoryBackfillEntity(
    @PrimaryKey val animeId: String,
    val status: String,
    val requestedAt: Long,
    val lastAttemptAt: Long?,
    val completedAt: Long?,
    val nextAttemptAt: Long?,
    val provider: String?,
    val importedReleaseCount: Int,
    val resultCode: String?
)

@Entity(
    tableName = "scheduled_release_notifications",
    foreignKeys = [ForeignKey(
        entity = EpisodeReleaseEntity::class,
        parentColumns = ["sourceReleaseId"],
        childColumns = ["releaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("animeId"), Index("eventAt")]
)
data class ScheduledReleaseNotificationEntity(
    @PrimaryKey val releaseId: String,
    val animeId: String,
    val eventAt: Long,
    val language: String,
    val workName: String,
    val scheduledAt: Long
)

@Entity(
    tableName = "notification_deliveries",
    indices = [Index("releaseId"), Index("animeId")]
)
data class NotificationDeliveryEntity(
    @PrimaryKey val deliveryId: String,
    val releaseId: String,
    val animeId: String,
    val eventType: String,
    val deliveredAt: Long,
    val notificationId: Int
)

@Entity(
    tableName = "episodes",
    primaryKeys = ["animeId", "seasonNumber", "episodeNumber"],
    indices = [Index("animeId")]
)
data class EpisodeEntity(
    val animeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val expectedReleaseAt: Long,
    val lastUnavailableAt: Long?,
    val firstAvailableAt: Long?,
    val providerEpisodeId: String?,
    val providerEpisodeUrl: String?,
    val status: String,
    val confidence: Double
)

@Entity(tableName = "watch_profiles")
data class WatchProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean,
    val stopAfterMinutes: Int,
    val liveMonitoringAllowed: Boolean
)

@Entity(
    tableName = "watch_phases",
    primaryKeys = ["profileId", "startOffsetSeconds"],
    indices = [Index("profileId")]
)
data class WatchPhaseEntity(
    val profileId: String,
    val startOffsetSeconds: Long,
    val endOffsetSeconds: Long?,
    val intervalSeconds: Long
)
