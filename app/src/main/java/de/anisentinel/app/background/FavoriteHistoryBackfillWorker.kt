package de.anisentinel.app.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.local.FavoriteHistoryBackfillEntity
import de.anisentinel.app.data.provider.HistoricalImportResult
import java.time.Instant
import java.util.concurrent.TimeUnit

object FavoriteHistoryBackfillCoordinator {
    private const val WORK_PREFIX = "anisentinel.favorite-history-backfill."
    const val TAG = "anisentinel.favorite-history-backfill"

    suspend fun request(context: Context, animeId: String, resetCompleted: Boolean = false) {
        val app = context.applicationContext as AniSentinelApplication
        val dao = app.container.database.aniSentinelDao()
        val favorite = dao.favorite(animeId) ?: return
        if (!favorite.enabled) return
        val existing = dao.favoriteHistoryBackfill(animeId)
        if (existing?.status == "COMPLETED" && !resetCompleted) return
        val now = Instant.now().epochSecond
        dao.upsertFavoriteHistoryBackfill(
            FavoriteHistoryBackfillEntity(
                animeId, "PENDING", existing?.requestedAt ?: now,
                existing?.lastAttemptAt, null, null, existing?.provider,
                existing?.importedReleaseCount ?: 0, null
            )
        )
        enqueue(context, animeId)
    }

    suspend fun reconcile(context: Context) {
        val app = context.applicationContext as AniSentinelApplication
        app.container.database.aniSentinelDao()
            .favoritesNeedingHistoryBackfill(Instant.now().epochSecond)
            .forEach { animeId -> request(context, animeId) }
    }

    fun cancel(context: Context, animeId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_PREFIX + animeId)
    }

    private fun enqueue(context: Context, animeId: String) {
        val input = Data.Builder().putString(FavoriteHistoryBackfillWorker.KEY_ANIME_ID, animeId).build()
        val request = OneTimeWorkRequestBuilder<FavoriteHistoryBackfillWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_PREFIX + animeId, ExistingWorkPolicy.KEEP, request
        )
    }
}

class FavoriteHistoryBackfillWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val animeId = inputData.getString(KEY_ANIME_ID) ?: return Result.failure()
        val app = applicationContext as AniSentinelApplication
        val container = app.container
        val dao = container.database.aniSentinelDao()
        val favorite = dao.favorite(animeId)
        if (favorite?.enabled != true) return Result.success()
        val anime = dao.anime(animeId) ?: return retry(animeId, "ANIME_NOT_FOUND")
        val now = Instant.now().epochSecond
        val previous = dao.favoriteHistoryBackfill(animeId)
        dao.upsertFavoriteHistoryBackfill(FavoriteHistoryBackfillEntity(
            animeId, "RUNNING", previous?.requestedAt ?: now, now, null, null,
            previous?.provider, previous?.importedReleaseCount ?: 0, null
        ))

        var references = dao.providerReferences(animeId)
        if (references.none { isCrunchyroll(it.provider) || isAdn(it.provider) }) {
            container.providerPipelineRepository.syncTitleProviders()
            references = dao.providerReferences(animeId)
        }
        val identities = dao.providerMetadataIdentities(animeId)
        var imported = 0
        val providers = mutableListOf<String>()
        val errors = mutableListOf<String>()

        if (references.any { isCrunchyroll(it.provider) } || identities.any { it.provider.contains("CRUNCHYROLL") }) {
            providers += "Crunchyroll"
            val titleAliases = (dao.justWatchMatches(animeId)
                .filter { it.status == "MATCHED" }.map { it.title } +
                listOfNotNull(dao.justWatchCatalogTitleForAnime(animeId)?.title)).toSet()
            val knownUrl = identities.firstOrNull { it.provider.contains("CRUNCHYROLL") }?.sourceUrl
                ?: references.firstOrNull { isCrunchyroll(it.provider) && it.seriesUrl?.contains("crunchyroll.com") == true }?.seriesUrl
            val firstResult = if (knownUrl != null) container.crunchyrollHistoricalReleaseImporter.importFromProviderUrl(
                animeId, anime.titleGerman, knownUrl, titleAliases = titleAliases
            ) else container.crunchyrollHistoricalReleaseImporter.importByTitle(
                animeId, anime.titleGerman, titleAliases = titleAliases
            )
            val result = if (firstResult is HistoricalImportResult.Failed && knownUrl != null) {
                container.crunchyrollHistoricalReleaseImporter.importByTitle(
                    animeId, anime.titleGerman, titleAliases = titleAliases
                )
            } else firstResult
            when (result) {
                is HistoricalImportResult.Success -> imported += result.inserted + result.enriched
                is HistoricalImportResult.Failed -> errors += result.code
            }
        }

        if (references.any { isAdn(it.provider) } || identities.any { it.provider.contains("ADN") }) {
            providers += "ADN"
            val showId = identities.firstOrNull { it.provider.contains("ADN") }?.seriesId
                ?: references.firstNotNullOfOrNull { reference ->
                    reference.seriesUrl?.takeIf { isAdn(reference.provider) }
                        ?.let { Regex("/de/video/(\\d+)(?:-|/)").find(it)?.groupValues?.getOrNull(1) }
                }
            if (showId == null) errors += "ADN_SHOW_ID_NOT_IDENTIFIED"
            else {
                val result = container.adnHistoricalReleaseImporter.diagnoseAndImport(animeId, showId)
                if (result.result == "IMPORTED" || result.result == "IMPORTED_WITH_CONFLICTS") {
                    imported += result.imported + result.enriched
                } else errors += result.result
            }
        }

        if (providers.isNotEmpty() && imported > 0) {
            dao.upsertFavoriteHistoryBackfill(FavoriteHistoryBackfillEntity(
                animeId, "COMPLETED", previous?.requestedAt ?: now, now, now, null,
                providers.joinToString("+"), imported, errors.takeIf { it.isNotEmpty() }?.joinToString("|")
            ))
            // Historical rows are deliberately not passed to FavoriteReleaseScheduler.
            return Result.success()
        }
        return retry(animeId, when {
            providers.isEmpty() -> "SUPPORTED_PROVIDER_NOT_IDENTIFIED"
            errors.isNotEmpty() -> errors.joinToString("|")
            else -> "NO_EXACT_HISTORICAL_RELEASE_IMPORTED"
        })
    }

    private suspend fun retry(animeId: String, code: String): Result {
        val dao = (applicationContext as AniSentinelApplication).container.database.aniSentinelDao()
        val now = Instant.now().epochSecond
        val previous = dao.favoriteHistoryBackfill(animeId)
        dao.upsertFavoriteHistoryBackfill(FavoriteHistoryBackfillEntity(
            animeId, "RETRY_REQUIRED", previous?.requestedAt ?: now, now, null,
            now + retryDelaySeconds(runAttemptCount), previous?.provider,
            previous?.importedReleaseCount ?: 0, code
        ))
        return if (runAttemptCount < MAX_WORK_RETRIES) Result.retry() else Result.success()
    }

    private fun retryDelaySeconds(attempt: Int): Long =
        (30L * 60L * (1L shl attempt.coerceIn(0, 5))).coerceAtMost(24L * 60L * 60L)

    private fun isCrunchyroll(provider: String) = provider.equals("Crunchyroll", true)
    private fun isAdn(provider: String) = provider.equals("ADN", true) || provider.contains("Animation Digital Network", true)

    companion object {
        const val KEY_ANIME_ID = "anime_id"
        private const val MAX_WORK_RETRIES = 5
    }
}
