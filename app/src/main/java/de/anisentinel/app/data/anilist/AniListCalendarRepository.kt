package de.anisentinel.app.data.anilist

import de.anisentinel.app.data.anisearch.ReleaseCalendarSource
import de.anisentinel.app.data.anisearch.SourceCalendarFetchResult
import de.anisentinel.app.data.anisearch.SourceFailureReason
import de.anisentinel.app.data.anisearch.ReleaseSourceKind
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface CalendarSyncResult {
    data class UpdatedFromNetwork(
        val releaseCount: Int,
        val fetchedAtEpochSeconds: Long,
        val sourceKind: ReleaseSourceKind = ReleaseSourceKind.ANILIST_FALLBACK,
        val receivedCount: Int = releaseCount,
        val storedCount: Int = releaseCount
    ) : CalendarSyncResult
    data class CacheFresh(val sourceDataAtEpochSeconds: Long) : CalendarSyncResult
    data class RetryRequired(
        val reason: SourceFailureReason,
        val diagnostic: String,
        val retryNotBeforeEpochSeconds: Long?
    ) : CalendarSyncResult
}

class AniListCalendarRepository(
    private val dao: AniSentinelDao,
    private val source: ReleaseCalendarSource,
    private val clock: Clock = Clock.systemUTC(),
    private val timeZoneProvider: DeviceTimeZoneProvider = SystemDeviceTimeZoneProvider
) {
    private val syncMutex = Mutex()

    suspend fun sync(start: LocalDate, endExclusive: LocalDate): CalendarSyncResult = syncMutex.withLock {
        val now = clock.instant().epochSecond
        val zoneId = timeZoneProvider.currentZoneId()
        val requestedWindow = calendarTimeWindow(start, endExclusive, zoneId)
        val latest = listOfNotNull(
            dao.latestReleaseFetch(
                requestedWindow.fromEpochSeconds,
                requestedWindow.untilEpochSeconds,
                "ANIME_RADAR"
            ),
            dao.latestReleaseFetch(
            requestedWindow.fromEpochSeconds,
            requestedWindow.untilEpochSeconds,
            "ANILIST_AIRING_SCHEDULE"
            )
        ).maxOrNull()
        if (latest != null && now - latest < 30 * 60) {
            return CalendarSyncResult.CacheFresh(latest)
        }
        val result = source.fetchRange(start, endExclusive)
        if (result is SourceCalendarFetchResult.Unavailable) {
            return CalendarSyncResult.RetryRequired(
                result.reason, result.diagnostic, result.retryNotBeforeEpochSeconds
            )
        }
        result as SourceCalendarFetchResult.Complete
        fun animeId(release: de.anisentinel.app.data.anisearch.SourceEpisodeRelease): String =
            release.aniListId?.let { "anilist:$it" }
                ?: release.sourceReleaseId.substringBeforeLast(':')
        val anime = result.releases.groupBy(::animeId).map { (id, releases) ->
            val release = releases.first()
            val next = releases.filter { (it.releaseAtEpochSeconds ?: Long.MIN_VALUE) >= now }
                .minByOrNull { it.releaseAtEpochSeconds ?: Long.MAX_VALUE }
            val numericId = release.aniListId ?: id.substringAfter("anilist:").toIntOrNull()
            val existing = dao.anime(id)
            existing?.copy(
                anilistId = numericId,
                titleEnglish = existing.titleEnglish ?: release.titleEnglish,
                titleRomaji = existing.titleRomaji ?: release.titleRomaji,
                titleNative = existing.titleNative ?: release.titleNative,
                description = existing.description.ifBlank { release.description.orEmpty() },
                coverUrl = existing.coverUrl ?: release.coverUrl,
                bannerUrl = existing.bannerUrl ?: release.bannerUrl,
                season = existing.season ?: release.season,
                seasonYear = existing.seasonYear ?: release.seasonYear,
                totalEpisodes = existing.totalEpisodes ?: release.totalEpisodes,
                updatedAt = now,
                nextAiringAt = next?.releaseAtEpochSeconds,
                nextEpisode = next?.episodeNumber,
                sourceUpdatedAt = now,
                cachedAt = now
            ) ?: AnimeEntity(id, numericId, null, "", release.titleEnglish, release.titleRomaji, release.titleNative,
                release.description.orEmpty(), release.coverUrl, release.bannerUrl, release.season,
                release.seasonYear, release.totalEpisodes, now, next?.releaseAtEpochSeconds,
                next?.episodeNumber, now, now)
        }
        val rows = result.releases.mapNotNull { release -> release.releaseAtEpochSeconds?.let { at ->
            EpisodeReleaseEntity(release.sourceReleaseId, animeId(release),
                release.episodeNumber, null, at, release.provider, "ANILIST_AIRING_SCHEDULE",
                release.sourceUrl, release.providerUrl, now)
        } }
        val metadataSource = when (result.sourceKind) {
            ReleaseSourceKind.ANIME_RADAR -> "ANIME_RADAR"
            else -> "ANILIST_AIRING_SCHEDULE"
        }
        val sourceRows = rows.map { it.copy(metadataSource = metadataSource) }
        dao.replaceReleaseSourceRange(
            requestedWindow.fromEpochSeconds,
            requestedWindow.untilEpochSeconds,
            metadataSource,
            anime,
            sourceRows,
            now
        )
        // Cleanup happens only after a successful fetch and write. Favorites and confirmed
        // availability history are explicitly protected by the DAO query.
        val today = LocalDate.now(clock.withZone(zoneId))
        val retentionWindow = calendarTimeWindow(
            today.minusWeeks(4),
            today.plusWeeks(8).plusDays(1),
            zoneId
        )
        dao.deleteUnprotectedReleasesOutsideWindow(
            retentionWindow.fromEpochSeconds,
            retentionWindow.untilEpochSeconds
        )
        CalendarSyncResult.UpdatedFromNetwork(
            result.releases.size,
            now,
            result.sourceKind,
            result.releases.size,
            sourceRows.size
        )
    }
}
