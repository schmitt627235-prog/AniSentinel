package de.anisentinel.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import java.time.Instant
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.domain.watcher.AvailabilityWatchStrategy

class JustWatchProviderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AniSentinelApplication
        if (!de.anisentinel.app.BuildConfig.UNOFFICIAL_JUSTWATCH_DIAGNOSTIC_ENABLED) return Result.success()
        val run = app.container.providerPipelineRepository.syncTitleProviders()
        return if (run.failed > 0 && run.matched == 0) Result.retry() else Result.success()
    }
}

class ProviderEpisodeAvailabilitySyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as AniSentinelApplication
        if (!de.anisentinel.app.BuildConfig.UNOFFICIAL_JUSTWATCH_DIAGNOSTIC_ENABLED) return Result.success()
        val requestedReleaseId = inputData.getString(FavoriteReleaseScheduler.KEY_RELEASE_ID)
        val fallbackTrigger = inputData.getBoolean(KEY_FALLBACK_TRIGGER, false)
        if (requestedReleaseId?.contains("aniworld:episode-") == true) return Result.success()
        val requestedRelease = requestedReleaseId?.let { daoReleaseId ->
            app.container.database.aniSentinelDao().release(daoReleaseId)
        }
        if (requestedReleaseId != null && (requestedRelease == null ||
                requestedRelease.releaseStatus == "STALE_UNCONFIRMED" ||
                AvailabilityWatchStrategy.isTerminal(requestedRelease.releaseStatus))) {
            app.container.favoriteReleaseScheduler.cancelAllReleaseWatchScheduling(requestedReleaseId)
            return Result.success()
        }
        val dao = app.container.database.aniSentinelDao()
        if (requestedReleaseId?.let { dao.release(it)?.isHistoricalImport } == true) {
            app.container.favoriteReleaseScheduler.cancelAvailabilityCheck(requestedReleaseId)
            return Result.success()
        }
        // Race guard: a cancelled T+10 PendingIntent may already have fired. Re-read Room before
        // invoking any network source and never let AniWorld run after direct availability.
        if (fallbackTrigger && requestedReleaseId != null && releaseAlreadyAvailable(
                dao.release(requestedReleaseId)?.releaseStatus,
                dao.episodeProviderAvailability(requestedReleaseId)
            )) {
            app.container.favoriteReleaseScheduler.cancelAllReleaseWatchScheduling(requestedReleaseId)
            return Result.success()
        }
        val now = Instant.now()
        val releaseIds = requestedReleaseId?.let(::listOf) ?: dao
            .dueFavoriteReleases(now.epochSecond, now.minusSeconds(ReleaseWatchSelectionPolicy.ACTIVE_OVERDUE_WINDOW_SECONDS).epochSecond)
            .groupBy { listOf(it.seasonNumber, it.episodeNumber, it.releaseLanguage, it.expectedAt) }
            .values
            .map { rows -> rows.minBy { if (it.animeId.startsWith("aniworld:episode-")) 1 else 0 }.sourceReleaseId }
        val run = if (requestedReleaseId != null) {
            ProviderCheckTrace.event(requestedReleaseId, "WORKER_STARTED", now)
            app.container.providerPipelineRepository.checkEpisode(requestedReleaseId)
        } else {
            app.container.providerPipelineRepository.checkDueEpisodes(now)
        }
        releaseIds.forEach { releaseId ->
            val release = dao.release(releaseId)
            val favorite = release?.let { dao.favorite(it.animeId) }
            val animeTitle = release?.let { dao.anime(it.animeId)?.titleGerman }
            val available = selectAvailableEvidence(
                release?.releaseLanguage,
                dao.episodeProviderAvailability(releaseId)
            )
            if (release != null && available != null) {
                dao.episodeProviderAvailability(releaseId)
                    .mapNotNull(ProviderFailureNotificationPolicy::providerKey)
                    .distinct()
                    .forEach { dao.deleteProviderFailureState(it) }
                ProviderCheckTrace.event(
                    releaseId, "AVAILABLE_READ_FROM_ROOM",
                    detail = "provider=${available.providerName};firstAvailableAt=${available.firstAvailableAt ?: "unknown"}"
                )
                if (favorite?.enabled == true && favorite.notifyAvailable) {
                    deliverOnce(
                        app, release, "EPISODE_AVAILABLE",
                        de.anisentinel.app.domain.watcher.NotificationEvent.EpisodeAvailable(
                            release.animeId, release.episodeNumber ?: 0, available.providerName.takeIf(String::isNotBlank),
                            when {
                                available.germanSubAvailable == true && available.germanDubAvailable == true -> "Deutsch (Sub und Dub)"
                                available.germanDubAvailable == true -> "Deutsch (Dub)"
                                else -> "Deutsch (Sub)"
                            }, animeTitle, release.seasonNumber,
                            available.firstAvailableAt?.let(java.time.Instant::ofEpochSecond)
                        )
                    )
                }
                app.container.favoriteReleaseScheduler.cancelAllReleaseWatchScheduling(releaseId)
            } else if (release != null && favorite?.enabled == true) {
                val providerRows = dao.episodeProviderAvailability(releaseId)
                val tentativeKey = providerRows.mapNotNull(ProviderFailureNotificationPolicy::providerKey)
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                val previousFailure = tentativeKey?.let { dao.providerFailureState(it) }
                val failure = ProviderFailureNotificationPolicy.evaluate(providerRows, previousFailure, Instant.now().epochSecond)
                failure.resetProviderKeys.forEach { dao.deleteProviderFailureState(it) }
                failure.nextState?.let { dao.upsertProviderFailureState(it) }
                if (failure.shouldNotify && failure.nextState != null) {
                    val delivered = deliverOnce(
                        app, release, "PROVIDER_CHECK_FAILED",
                        de.anisentinel.app.domain.watcher.NotificationEvent.ProviderError(
                            failure.providerKey ?: "provider-pipeline",
                            failure.providerKey ?: "provider-pipeline",
                            true,
                            null
                        )
                    )
                    if (delivered) dao.upsertProviderFailureState(
                        failure.nextState.copy(lastNotifiedAt = Instant.now().epochSecond)
                    )
                }
                val refreshed = dao.release(releaseId)
                if (refreshed?.releaseStatus == "DELAYED_CONFIRMED" && favorite.notifyDelayed) {
                    deliverOnce(
                        app, release, "DELAYED_CONFIRMED",
                        de.anisentinel.app.domain.watcher.NotificationEvent.ReleaseDelayed(
                            release.animeId, release.episodeNumber ?: 0, animeTitle, release.seasonNumber
                        )
                    )
                }
                if (refreshed == null || AvailabilityWatchStrategy.isTerminal(refreshed.releaseStatus)) {
                    app.container.favoriteReleaseScheduler.cancelAllReleaseWatchScheduling(releaseId)
                } else {
                    val expectedAt = refreshed.expectedAt
                    if (expectedAt != null) {
                        val checkedAt = Instant.now().epochSecond
                        val next = AvailabilityWatchStrategy.nextCheckAt(
                            expectedAt, checkedAt, favorite.monitoringProfileId ?: AvailabilityWatchStrategy.AUTOMATIC
                        )
                        app.container.favoriteReleaseScheduler.scheduleAvailabilityCheck(releaseId, next)
                    }
                }
            } else if (release != null) {
                app.container.favoriteReleaseScheduler.cancelAvailabilityCheck(releaseId)
            }
        }
        // Each release persists and schedules its own bounded retry sequence. Returning retry here
        // would create a second, effectively unbounded WorkManager retry loop.
        return Result.success()
    }

    companion object {
        const val KEY_FALLBACK_TRIGGER = "fallback_trigger"
        fun checkWorkName(releaseId: String) = "anisentinel.provider-check.$releaseId"
    }
}

internal fun releaseAlreadyAvailable(
    releaseStatus: String?,
    rows: List<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity>
): Boolean = releaseStatus == "AVAILABLE" || releaseStatus?.startsWith("AVAILABLE_") == true ||
    rows.any { it.firstAvailableAt != null || it.status.startsWith("AVAILABLE_") }

internal fun selectAvailableEvidence(
    releaseLanguage: String?,
    rows: List<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity>
): de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity? = rows
    .filter {
        when (releaseLanguage) {
            "GER_SUB" -> it.germanSubAvailable == true
            "GER_DUB" -> it.germanDubAvailable == true
            else -> it.status.startsWith("AVAILABLE_")
        }
    }
    // A direct provider result wins over the later AniWorld safety net. Structured
    // Crunchyroll/ADN probes are direct evidence too and must not be filtered out.
    .sortedWith(
        compareBy<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity> {
            it.source == "ANIWORLD_CALENDAR_FALLBACK_V15"
        }.thenByDescending { it.providerName.isNotBlank() }
            .thenByDescending { it.lastCheckedAt }
    )
    .firstOrNull()

internal fun hasTechnicalProviderFailure(
    rows: List<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity>
): Boolean {
    val direct = rows.filterNot {
        it.source == "ANIWORLD_CALENDAR_FALLBACK_V15" ||
            it.source == "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC"
    }
    if (direct.any { it.status.startsWith("AVAILABLE_") || it.status == "NOT_AVAILABLE_YET" }) return false
    return direct.any { it.status == "CHECK_FAILED" || it.status == "PROVIDER_CHECK_FAILED" }
}
