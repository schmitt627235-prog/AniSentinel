package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.anisearch.AniSearchFetchImportResult
import de.anisentinel.app.data.anisearch.AniSearchSearchHit
import de.anisentinel.app.data.anisearch.AniSearchSearchResult
import de.anisentinel.app.domain.model.ReleaseSourceType
import de.anisentinel.app.background.BackgroundWorkCoordinator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import de.anisentinel.app.data.local.ReleasePostponementEntity
import kotlinx.coroutines.launch

data class CalendarReleaseItem(
    val sourceReleaseId: String,
    val animeId: String,
    val displayTitle: String,
    val episodeNumber: Int?,
    val releaseAt: Instant,
    val provider: String?,
    val metadataSource: String,
    val sourceUrl: String?,
    val providerUrl: String?,
    val sourceType: ReleaseSourceType,
    val availabilityConfirmed: Boolean
    ,val coverUrl: String? = null
    ,val listedAt: Instant? = null
    ,val adjustmentMinutes: Int? = null
    ,val releaseStatus: String = "SCHEDULED"
    ,val previousAt: Instant? = null
    ,val releaseLanguage: String? = null
    ,val providerCheckStatus: String? = null
    ,val providerName: String? = null
    ,val providerDiagnosticOnly: Boolean = false
    ,val scheduleChangeReason: String? = null
    ,val lastCheckedAt: Instant? = null
    ,val firstAvailableAt: Instant? = null
    ,val sourceAvailableAt: Instant? = null
    ,val providerErrorCode: String? = null
    ,val fallbackStatus: String? = null
    ,val isHistoricalImport: Boolean = false
    ,val releaseTimePrecision: String = "EXACT"
    ,val postponements: List<ReleasePostponementEntity> = emptyList()
)

data class ScheduleChangeUiItem(
    val id: String,
    val title: String,
    val episodeNumber: Int?,
    val previousAt: Instant?,
    val revisedAt: Instant,
    val releaseType: String?,
    val reason: String?
)

enum class CalendarCoverage { NOT_LOADED, LOCAL_RELEASE_DATA }
enum class AniSearchImportStatus { IDLE, LOADING, IMPORTED, LOCAL_IMPORTED, EMPTY, BLOCKED, RATE_LIMITED, FAILED }

data class CalendarUiState(
    val displayedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val releasesForSelectedDate: List<CalendarReleaseItem> = emptyList(),
    val releaseDatesInMonth: Set<LocalDate> = emptySet(),
    val coverage: CalendarCoverage = CalendarCoverage.NOT_LOADED,
    val importStatus: AniSearchImportStatus = AniSearchImportStatus.IDLE,
    val importedTitle: String? = null,
    val importCalendarCount: Int = 0,
    val importAnimeCount: Int = 0,
    val importProviderCount: Int = 0,
    val searchHits: List<AniSearchSearchHit> = emptyList(),
    val syncLoading: Boolean = false,
    val syncError: String? = null,
    val lastSuccessfulSync: Instant? = null,
    val backgroundSyncState: String = "DISABLED",
    val backgroundRunAttemptCount: Int = 0,
    val backgroundLastAttempt: Instant? = null,
    val backgroundLastSuccess: Instant? = null,
    val backgroundNextRetry: Instant? = null,
    val backgroundSourceDataAt: Instant? = null,
    val workManagerState: String = "NOT_SCHEDULED",
    val diagnosticWorkManagerState: String = "NOT_SCHEDULED",
    val calendarSource: String? = null,
    val receivedCount: Int = 0,
    val storedCount: Int = 0,
    val aniWorldCount: Int = 0,
    val aniWorldWorkState: String = "NOT_SCHEDULED",
    val scheduleChangesWorkState: String = "NOT_SCHEDULED",
    val scheduleChangeCount: Int = 0,
    val latestScheduleChanges: List<ScheduleChangeUiItem> = emptyList(),
    val justWatchLiveStatus: String = "SOURCE_NOT_CONFIGURED",
    val justWatchMatchedCount: Int = 0,
    val justWatchAmbiguousCount: Int = 0,
    val providerCheckCount: Int = 0,
    val providerCheckFailedCount: Int = 0,
    val historySyncRunning: Boolean = false,
    val historySyncSummary: String? = null,
    val loading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AniSentinelApplication).container
    private val dao = container.database.aniSentinelDao()
    private fun currentZone() = container.deviceTimeZoneProvider.currentZoneId()
    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val importState = MutableStateFlow(ImportUiState())
    private val syncState = MutableStateFlow(CalendarSyncUiState())
    private val historySyncState = MutableStateFlow(HistorySyncUiState())

    private data class HistorySyncUiState(val running: Boolean = false, val summary: String? = null)

    private data class CalendarSyncUiState(
        val loading: Boolean = false,
        val error: String? = null,
        val lastSuccess: Instant? = null
    )

    init {
        val today = LocalDate.now()
        val weekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        syncRange(weekStart, weekStart.plusWeeks(1))
        viewModelScope.launch {
            dao.latestImportBatch()?.let { batch ->
                val first = Instant.ofEpochSecond(batch.earliestReleaseAt).atZone(currentZone()).toLocalDate()
                month.value = YearMonth.from(first)
                selectedDate.value = first
            }
        }
    }

    private data class ImportUiState(
        val status: AniSearchImportStatus = AniSearchImportStatus.IDLE,
        val title: String? = null,
        val calendarCount: Int = 0,
        val providerCount: Int = 0,
        val animeCount: Int = 0,
        val hits: List<AniSearchSearchHit> = emptyList()
    )

    private val selectedReleases = selectedDate.flatMapLatest { date ->
        combine(
            dao.observeEpisodeReleasesWithAnimeForWindow(date.startEpoch(), date.plusDays(1).startEpoch()),
            dao.observeReleasePostponements()
        ) { rows, postponements -> rows.mapNotNull { row ->
                de.anisentinel.app.domain.watcher.ReleaseTimePolicy.resolve(
                    row.release, rows.map { it.release }, container.deviceTimeZoneProvider.currentZoneId()
                )?.let { resolved ->
                    val at = resolved.epochSecond
                    val relevantChecks = row.episodeAvailability.filter {
                        it.source == "DIRECT_PROVIDER_CHECK" || it.source == "ANIWORLD_CALENDAR_FALLBACK_V15"
                    }
                    val latestEpisodeAvailability = relevantChecks.maxWithOrNull(
                        compareBy<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity> {
                            if (it.status.startsWith("AVAILABLE_")) 1 else 0
                        }.thenBy { it.lastCheckedAt }
                    )
                    val latestCheck = relevantChecks.maxByOrNull { it.lastCheckedAt }
                    val fallback = relevantChecks.filter { it.source == "ANIWORLD_CALENDAR_FALLBACK_V15" }.maxByOrNull { it.lastCheckedAt }
                    CalendarReleaseItem(
                        row.release.sourceReleaseId, row.release.animeId,
                        row.anime.titleGerman.takeIf(String::isNotBlank)
                            ?: row.anime.titleEnglish
                            ?: row.anime.titleRomaji
                            ?: row.anime.titleNative
                            ?: "–",
                        row.release.episodeNumber, Instant.ofEpochSecond(at),
                        latestEpisodeAvailability?.providerName
                            ?: de.anisentinel.app.domain.provider.StreamingProviderPolicy
                                .visible(row.providerReferences.map { it.provider })
                                .joinToString(" · ").takeIf { it.isNotBlank() },
                        row.release.metadataSource, row.release.sourceUrl, row.release.providerUrl,
                        ReleaseSourceType.fromMetadataSource(row.release.metadataSource),
                        row.availability.any { availability ->
                            availability.episodeNumber == row.release.episodeNumber &&
                                availability.status == "AVAILABLE"
                        } || row.episodeAvailability.any { it.status.startsWith("AVAILABLE_") },
                        row.anime.coverUrl,
                        row.release.listedAt?.let(Instant::ofEpochSecond),
                        row.release.adjustmentMinutes,
                        row.release.releaseStatus,
                        row.history.maxByOrNull { it.detectedAt }?.previousAt?.let(Instant::ofEpochSecond),
                        row.release.releaseLanguage,
                        latestEpisodeAvailability?.status,
                        latestEpisodeAvailability?.providerName,
                        false,
                        row.history.maxByOrNull { it.detectedAt }?.reason,
                        latestCheck?.lastCheckedAt?.let(Instant::ofEpochSecond),
                        latestEpisodeAvailability?.firstAvailableAt?.let(Instant::ofEpochSecond),
                        latestEpisodeAvailability?.sourceAvailableAt?.let(Instant::ofEpochSecond),
                        latestCheck?.errorCode,
                        fallback?.let { it.status + (it.errorCode?.let { code -> " · $code" } ?: "") }
                    ).copy(
                        isHistoricalImport = row.release.isHistoricalImport,
                        releaseTimePrecision = resolved.precision,
                        postponements = postponements.filter { it.isActive && it.animeId == row.release.animeId }
                    )
                }
            } }
    }
    private data class MonthRows(
        val releases: List<de.anisentinel.app.data.local.EpisodeReleaseEntity>,
        val scheduleChangeCount: Int
    )
    private val monthReleasesOnly = month.flatMapLatest { value ->
        val start = value.atDay(1)
        dao.observeEpisodeReleasesForWindow(start.startEpoch(), value.plusMonths(1).atDay(1).startEpoch())
    }
    private val monthReleases = combine(
        monthReleasesOnly,
        dao.observeReleaseScheduleHistoryCount()
    ) { releases, historyCount -> MonthRows(releases, historyCount) }

    private data class CalendarAuxiliary(
        val imported: ImportUiState,
        val sync: CalendarSyncUiState,
        val background: de.anisentinel.app.data.settings.BackgroundSyncStatus,
        val workManagerState: String,
        val diagnosticWorkManagerState: String
        ,val aniWorldWorkState: String
        ,val scheduleChangesWorkState: String
        ,val latestHistory: List<de.anisentinel.app.data.local.ReleaseScheduleHistorySummary>
        ,val providerDiagnostics: ProviderDiagnostics
        ,val historySync: HistorySyncUiState
    )
    private data class ProviderDiagnostics(val matched: Int, val ambiguous: Int, val checks: Int, val failed: Int)
    private val providerDiagnostics = combine(
        dao.observeMatchedJustWatchCount(), dao.observeAmbiguousJustWatchCount(),
        dao.observeEpisodeProviderAvailabilityCount(), dao.observeFailedProviderCheckCount()
    ) { matched, ambiguous, checks, failed -> ProviderDiagnostics(matched, ambiguous, checks, failed) }
    private fun observeUniqueWork(name: String): Flow<String> = callbackFlow {
        val manager = WorkManager.getInstance(getApplication<Application>())
        val liveData = manager.getWorkInfosForUniqueWorkLiveData(name)
        val observer = Observer<List<WorkInfo>> { work ->
            val relevant = work.firstOrNull { !it.state.isFinished } ?: work.lastOrNull()
            trySend(relevant?.state?.name ?: "NOT_SCHEDULED")
        }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }
    private val workManagerState = observeUniqueWork(BackgroundWorkCoordinator.RELEASE_CALENDAR_WORK)
    private val diagnosticWorkManagerState = observeUniqueWork(BackgroundWorkCoordinator.DIAGNOSTIC_RETRY_WORK)
    private val aniWorldWorkState = observeUniqueWork(BackgroundWorkCoordinator.ANIWORLD_CALENDAR_WORK)
    private val scheduleChangesWorkState = observeUniqueWork(BackgroundWorkCoordinator.ANIWORLD_SCHEDULE_CHANGES_WORK)
    private data class BackgroundUi(
        val status: de.anisentinel.app.data.settings.BackgroundSyncStatus,
        val releaseWorkState: String,
        val diagnosticWorkState: String
        ,val aniWorldWorkState: String
        ,val scheduleChangesWorkState: String
    )
    private data class WorkStates(
        val release: String,
        val diagnostic: String,
        val aniWorld: String,
        val changes: String
    )
    private val workStates = combine(
        workManagerState,
        diagnosticWorkManagerState,
        aniWorldWorkState,
        scheduleChangesWorkState
    ) { release, diagnostic, aniWorld, changes ->
        WorkStates(release, diagnostic, aniWorld, changes)
    }
    private val backgroundState = combine(
        container.backgroundSyncStatusStore.status,
        workStates
    ) { status, work ->
        BackgroundUi(status, work.release, work.diagnostic, work.aniWorld, work.changes)
    }
    private val auxiliaryState = combine(
        importState,
        syncState,
        backgroundState,
        combine(
            combine(dao.observeLatestReleaseScheduleHistory(), providerDiagnostics) { history, diagnostics -> history to diagnostics },
            historySyncState
        ) { historyAndDiagnostics, historySync -> historyAndDiagnostics to historySync }
    ) { imported, sync, background, historyBundle ->
        val historyAndDiagnostics = historyBundle.first
        CalendarAuxiliary(
            imported,
            sync,
            background.status,
            background.releaseWorkState,
            background.diagnosticWorkState
            ,background.aniWorldWorkState
            ,background.scheduleChangesWorkState
            ,historyAndDiagnostics.first
            ,historyAndDiagnostics.second
            ,historyBundle.second
        )
    }

    val state = combine(month, selectedDate, selectedReleases, monthReleases, auxiliaryState) {
            displayed, selected, releases, monthData, auxiliary ->
        val monthRows = monthData.releases
        val imported = auxiliary.imported
        val sync = auxiliary.sync
        val background = auxiliary.background
        CalendarUiState(
            displayedMonth = displayed,
            selectedDate = selected,
            releasesForSelectedDate = releases,
            releaseDatesInMonth = monthRows.mapNotNull { row ->
                row.expectedAt?.let { Instant.ofEpochSecond(it).atZone(currentZone()).toLocalDate() }
            }.toSet(),
            coverage = if (monthRows.isEmpty()) CalendarCoverage.NOT_LOADED else CalendarCoverage.LOCAL_RELEASE_DATA,
            importStatus = imported.status,
            importedTitle = imported.title,
            importCalendarCount = imported.calendarCount,
            importAnimeCount = imported.animeCount,
            importProviderCount = imported.providerCount,
            searchHits = imported.hits,
            syncLoading = sync.loading,
            syncError = sync.error,
            lastSuccessfulSync = sync.lastSuccess,
            backgroundSyncState = background.state,
            backgroundRunAttemptCount = background.runAttemptCount,
            backgroundLastAttempt = background.lastAttemptAtEpochSeconds?.let(Instant::ofEpochSecond),
            backgroundLastSuccess = background.lastSuccessAtEpochSeconds?.let(Instant::ofEpochSecond),
            backgroundNextRetry = background.nextRetryAtEpochSeconds?.let(Instant::ofEpochSecond),
            backgroundSourceDataAt = background.sourceDataAtEpochSeconds?.let(Instant::ofEpochSecond),
            workManagerState = auxiliary.workManagerState,
            diagnosticWorkManagerState = auxiliary.diagnosticWorkManagerState,
            calendarSource = background.sourceKind,
            receivedCount = background.receivedCount,
            storedCount = background.storedCount,
            aniWorldCount = monthRows.count { it.metadataSource == "ANIWORLD_CALENDAR" },
            aniWorldWorkState = auxiliary.aniWorldWorkState,
            scheduleChangesWorkState = auxiliary.scheduleChangesWorkState,
            scheduleChangeCount = monthData.scheduleChangeCount,
            latestScheduleChanges = auxiliary.latestHistory.map { history ->
                ScheduleChangeUiItem(
                    history.historyId,
                    history.titleGerman,
                    history.episodeNumber,
                    history.previousAt?.let(Instant::ofEpochSecond),
                    Instant.ofEpochSecond(history.revisedAt),
                    history.releaseType,
                    history.reason
                )
            },
            justWatchLiveStatus = container.providerPipelineRepository.liveJustWatchStatus,
            justWatchMatchedCount = auxiliary.providerDiagnostics.matched,
            justWatchAmbiguousCount = auxiliary.providerDiagnostics.ambiguous,
            providerCheckCount = auxiliary.providerDiagnostics.checks,
            providerCheckFailedCount = auxiliary.providerDiagnostics.failed,
            historySyncRunning = auxiliary.historySync.running,
            historySyncSummary = auxiliary.historySync.summary,
            loading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun importAniSearchUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            importState.value = ImportUiState(status = AniSearchImportStatus.LOADING)
            importState.value = when (val result = container.aniSearchManualImportRepository.importUrl(url.trim())) {
                is AniSearchFetchImportResult.Imported -> ImportUiState(
                    AniSearchImportStatus.IMPORTED, result.anime.titleGerman,
                    result.calendarReleaseCount, result.providerCount
                )
                is AniSearchFetchImportResult.FetchFailed -> {
                    val blocked = result.result is de.anisentinel.app.data.anisearch.AniSearchFetchResult.AccessBlocked
                    ImportUiState(if (blocked) AniSearchImportStatus.BLOCKED else AniSearchImportStatus.FAILED)
                }
                is AniSearchFetchImportResult.ParseFailed -> ImportUiState(AniSearchImportStatus.FAILED)
            }
        }
    }

    fun searchAniSearch(query: String) {
        if (query.trim().length < 2) return
        viewModelScope.launch {
            importState.value = ImportUiState(status = AniSearchImportStatus.LOADING)
            importState.value = when (val result = container.aniSearchManualImportRepository.search(query)) {
                is AniSearchSearchResult.Success -> if (result.hits.isEmpty()) {
                    ImportUiState(AniSearchImportStatus.EMPTY)
                } else {
                    ImportUiState(hits = result.hits)
                }
                is AniSearchSearchResult.Failed -> ImportUiState(
                    when (result.result) {
                        is de.anisentinel.app.data.anisearch.AniSearchFetchResult.AccessBlocked -> AniSearchImportStatus.BLOCKED
                        is de.anisentinel.app.data.anisearch.AniSearchFetchResult.RateLimited -> AniSearchImportStatus.RATE_LIMITED
                        else -> AniSearchImportStatus.FAILED
                    }
                )
            }
        }
    }

    fun select(date: LocalDate) { selectedDate.value = date }
    fun previousMonth() = showMonth(month.value.minusMonths(1))
    fun nextMonth() = showMonth(month.value.plusMonths(1))
    fun today() = showMonth(YearMonth.now(), LocalDate.now())
    private fun showMonth(value: YearMonth, selected: LocalDate = value.atDay(1)) {
        month.value = value
        selectedDate.value = selected
        syncRange(value.atDay(1), value.plusMonths(1).atDay(1))
    }
    private fun syncRange(start: LocalDate, endExclusive: LocalDate) {
        viewModelScope.launch {
            syncState.value = syncState.value.copy(loading = true, error = null)
            if (getApplication<Application>().resources.getBoolean(de.anisentinel.app.R.bool.aniworld_enabled)) {
                syncState.value = when (val result = container.aniWorldReleaseRepository.syncCalendar(start, endExclusive)) {
                is de.anisentinel.app.data.release.AniWorldSyncResult.Success -> {
                    container.aniWorldReleaseRepository.syncScheduleChanges()
                    container.favoriteReleaseScheduler.reconcileAll()
                    container.providerPipelineRepository.syncTitleProviders()
                    container.providerPipelineRepository.checkDueEpisodes()
                    container.backgroundSyncStatusStore.markSuccess(
                        Instant.now().epochSecond,
                        "ANIWORLD_CALENDAR",
                        result.received,
                        result.stored
                    )
                    CalendarSyncUiState(
                        loading = false,
                        lastSuccess = Instant.now()
                    )
                }
                is de.anisentinel.app.data.release.AniWorldSyncResult.Failure ->
                    syncState.value.copy(loading = false, error = result.diagnostic)
                }
            } else {
                syncState.value = CalendarSyncUiState(false, "ANIWORLD_DISABLED")
            }
        }
    }

    fun retryCalendarSync() {
        val value = month.value
        syncRange(value.atDay(1), value.plusMonths(1).atDay(1))
    }

    /** Imports only already resolved DE provider identities; it performs no title guessing. */
    fun syncHistoricalProviders() {
        if (historySyncState.value.running) return
        viewModelScope.launch {
            val targetMonth = month.value
            val targetStart = targetMonth.atDay(1).startEpoch()
            val targetEnd = targetMonth.plusMonths(1).atDay(1).startEpoch()
            historySyncState.value = HistorySyncUiState(running = true)
            var sources = 0
            var inserted = 0
            var enriched = 0
            var failed = 0
            var adnImported = 0
            var crunchyrollImported = 0
            dao.germanProviderMetadataIdentities().forEach { identity ->
                when {
                    identity.provider.contains("ADN", ignoreCase = true) -> {
                        val result = container.adnHistoricalReleaseImporter.diagnoseAndImport(identity.animeId, identity.seriesId)
                        sources++
                        inserted += result.imported
                        enriched += result.enriched
                        if (result.result == "IMPORTED") adnImported += result.imported + result.enriched
                        if (result.result != "IMPORTED") failed++
                    }
                    identity.provider.contains("CRUNCHYROLL", ignoreCase = true) && !identity.sourceUrl.isNullOrBlank() -> {
                        sources++
                        when (val result = container.crunchyrollHistoricalReleaseImporter.import(
                            identity.animeId, identity.sourceUrl, targetStart, targetEnd
                        )) {
                            is de.anisentinel.app.data.provider.HistoricalImportResult.Success -> {
                                inserted += result.inserted
                                enriched += result.enriched
                                crunchyrollImported += result.inserted + result.enriched
                            }
                            is de.anisentinel.app.data.provider.HistoricalImportResult.Failed -> failed++
                        }
                    }
                }
            }
            val resolvedCrunchyrollAnime = dao.germanProviderMetadataIdentities()
                .filter { it.provider.contains("CRUNCHYROLL", true) }
                .mapTo(mutableSetOf()) { it.animeId }
            dao.crunchyrollHistoryCandidates().filterNot { it.animeId in resolvedCrunchyrollAnime }.forEach { candidate ->
                sources++
                when (val result = container.crunchyrollHistoricalReleaseImporter.importFromProviderUrl(
                    candidate.animeId, candidate.title, candidate.offerUrl, targetStart, targetEnd
                )) {
                    is de.anisentinel.app.data.provider.HistoricalImportResult.Success -> {
                        inserted += result.inserted
                        enriched += result.enriched
                        crunchyrollImported += result.inserted + result.enriched
                    }
                    is de.anisentinel.app.data.provider.HistoricalImportResult.Failed -> failed++
                }
            }
            val adnInMonth = dao.historicalReleaseCount("ADN", targetStart, targetEnd)
            val crunchyrollInMonth = dao.historicalReleaseCount("Crunchyroll", targetStart, targetEnd)
            historySyncState.value = HistorySyncUiState(summary =
                "${targetMonth.monthValue.toString().padStart(2, '0')}.${targetMonth.year}: " +
                    "ADN $adnInMonth · Crunchyroll $crunchyrollInMonth · $sources Serien geprüft · " +
                    "$inserted neu · $enriched ergänzt · $failed ohne importierbare Historie"
            )
        }
    }

    fun refreshDisplayedMonth() {
        val value = month.value
        syncRange(value.atDay(1), value.plusMonths(1).atDay(1))
    }
    fun runDiagnosticRetryProof() {
        val debuggable = getApplication<Application>().applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            BackgroundWorkCoordinator.runDiagnosticRetryProof(getApplication())
        }
    }
    private fun LocalDate.startEpoch(): Long = atStartOfDay(currentZone()).toEpochSecond()
}
