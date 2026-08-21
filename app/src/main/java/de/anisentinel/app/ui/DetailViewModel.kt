package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.FavoriteEntity
import de.anisentinel.app.data.local.ProviderAvailabilityEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.ScheduledReleaseNotificationEntity
import de.anisentinel.app.data.local.JustWatchOfferEntity
import de.anisentinel.app.data.local.ReleasePostponementEntity
import de.anisentinel.app.domain.provider.ProviderCheckRequest
import de.anisentinel.app.data.anilist.toDomain
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.LanguagePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val anime: Anime? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val favorite: Boolean? = null,
    val language: LanguagePreference = LanguagePreference.BOTH,
    val watchProfileId: String = "balanced",
    val providerReference: ProviderReferenceEntity? = null,
    val providerReferences: List<ProviderReferenceEntity> = emptyList(),
    val providerAvailability: ProviderAvailabilityEntity? = null,
    val providerChecking: Boolean = false,
    val scheduledRelease: ScheduledReleaseNotificationEntity? = null,
    val latestEpisodeCheck: EpisodeProviderAvailabilityEntity? = null,
    val episodeChecks: List<EpisodeProviderAvailabilityEntity> = emptyList(),
    val justWatchOffers: List<JustWatchOfferEntity> = emptyList()
    ,val providerMetadataIdentities: List<de.anisentinel.app.data.local.ProviderMetadataIdentityEntity> = emptyList()
    ,val releases: List<de.anisentinel.app.data.local.EpisodeReleaseEntity> = emptyList()
    ,val historyImportRunning: Boolean = false
    ,val historyImportResult: String? = null
    ,val adnHistoryDiagnostics: de.anisentinel.app.data.provider.AdnHistoryDiagnostics? = null
    ,val postponements: List<ReleasePostponementEntity> = emptyList()
    ,val justWatchMetadata: de.anisentinel.app.data.local.JustWatchCatalogTitleEntity? = null
    ,val justWatchGenreLabels: Map<String, String> = emptyMap()
    ,val metadataRefreshing: Boolean = false
    ,val metadataRefreshError: String? = null
    ,val canonicalSeasons: List<de.anisentinel.app.data.local.AnimeSeasonEntity> = emptyList()
    ,val providerSeasonMappings: List<de.anisentinel.app.data.local.ProviderSeasonMappingEntity> = emptyList()
    ,val providerPreferences: List<de.anisentinel.app.data.local.ProviderPreferenceEntity> = emptyList()
)

class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val animeId: String = savedStateHandle["animeId"] ?: "skyward"
    private val container = (application as AniSentinelApplication).container
    private var anime: Anime? = null
    private var automaticCrunchyrollHistoryAttempted = false
    private val _state = MutableStateFlow(
        DetailUiState(anime = anime, loading = anime == null)
    )
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cached = container.database.aniSentinelDao().anime(animeId)
            if (cached != null) {
                anime = cached.toDomain()
                _state.value = _state.value.copy(anime = anime, loading = false)
                refreshJustWatchMetadata()
            } else {
                _state.value = _state.value.copy(loading = false, notFound = true)
                return@launch
            }
            container.favoritesRepository.observeFavorite(animeId).collect { favorite ->
                _state.value = _state.value.copy(
                    favorite = favorite?.enabled ?: false,
                    language = favorite?.languagePreference
                        ?.let { runCatching { LanguagePreference.valueOf(it) }.getOrNull() }
                        ?: _state.value.language,
                    watchProfileId = favorite?.monitoringProfileId
                        ?: _state.value.watchProfileId
                )
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeJustWatchCatalogTitleForAnime(animeId).collect { row ->
                _state.value = _state.value.copy(justWatchMetadata = row)
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeJustWatchGenres().collect { rows ->
                _state.value = _state.value.copy(justWatchGenreLabels = rows.associate { it.genreId to it.label })
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeActivePostponementsForAnime(animeId).collect { rows ->
                _state.value = _state.value.copy(postponements = rows)
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeAnimeSeasons(animeId).collect { rows ->
                _state.value = _state.value.copy(canonicalSeasons = rows)
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeProviderSeasonMappings(animeId).collect { rows ->
                _state.value = _state.value.copy(providerSeasonMappings = rows)
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeProviderPreferences(animeId).collect { rows ->
                _state.value = _state.value.copy(providerPreferences = rows)
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeProviderMetadataIdentities(animeId).collect { rows ->
                _state.value = _state.value.copy(providerMetadataIdentities = rows)
                val crunchyroll = rows.firstOrNull {
                    it.provider.contains("CRUNCHYROLL", true) && !it.sourceUrl.isNullOrBlank()
                }
                if (crunchyroll != null && !automaticCrunchyrollHistoryAttempted) {
                    automaticCrunchyrollHistoryAttempted = true
                    importCrunchyrollHistory(crunchyroll.sourceUrl!!)
                }
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeEpisodeReleasesForAnime(animeId).collect { rows ->
                _state.value = _state.value.copy(releases = rows)
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeProviderReferences(animeId).collect {
                _state.value = _state.value.copy(
                    providerReference = it.firstOrNull(),
                    providerReferences = it
                )
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeProviderAvailability(animeId).collect {
                _state.value = _state.value.copy(providerAvailability = it.firstOrNull())
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeScheduledReleaseNotifications().collect { rows ->
                _state.value = _state.value.copy(scheduledRelease = rows.firstOrNull { it.animeId == animeId })
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeEpisodeProviderAvailabilityForAnime(animeId).collect { rows ->
                _state.value = _state.value.copy(
                    latestEpisodeCheck = rows.maxWithOrNull(
                        compareBy<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity> {
                            when {
                                it.status.startsWith("AVAILABLE_") -> 3
                                it.status == "NOT_AVAILABLE_YET" -> 2
                                it.status == "CHECK_FAILED" -> 0
                                else -> 1
                            }
                        }.thenBy { it.lastCheckedAt }
                    ),
                    episodeChecks = rows
                )
            }
        }
        viewModelScope.launch {
            container.database.aniSentinelDao().observeJustWatchOffersForAnime(animeId).collect { rows ->
                _state.value = _state.value.copy(justWatchOffers = rows)
                val offer = rows.firstOrNull {
                    it.providerName.equals("Crunchyroll", true) && !it.offerUrl.isNullOrBlank()
                }
                val currentAnime = anime
                if (offer != null && currentAnime != null && !automaticCrunchyrollHistoryAttempted) {
                    automaticCrunchyrollHistoryAttempted = true
                    viewModelScope.launch {
                        _state.value = _state.value.copy(historyImportRunning = true, historyImportResult = null)
                        val result = container.crunchyrollHistoricalReleaseImporter.importFromProviderUrl(
                            animeId, currentAnime.title, offer.offerUrl!!
                        )
                        _state.value = _state.value.copy(
                            historyImportRunning = false,
                            historyImportResult = when (result) {
                                is de.anisentinel.app.data.provider.HistoricalImportResult.Success ->
                                    "OK:${result.parsed}:${result.inserted}:${result.enriched}"
                                is de.anisentinel.app.data.provider.HistoricalImportResult.Failed -> "ERROR:${result.code}"
                            }
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            container.settingsRepository.settings.collect {
                _state.value = _state.value.copy(watchProfileId = it.watchProfileId)
            }
        }
    }

    fun refreshJustWatchMetadata() {
        if (_state.value.metadataRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(metadataRefreshing = true, metadataRefreshError = null)
            val result = container.justWatchCatalogRepository.backfillMetadata(animeId)
            _state.value = _state.value.copy(
                metadataRefreshing = false,
                metadataRefreshError = (result as? de.anisentinel.app.domain.provider.JustWatchCatalogResult.Failed)?.code
            )
        }
    }

    fun diagnoseHistoricalEpisode(releaseId: String) {
        if (_state.value.providerChecking) return
        viewModelScope.launch {
            _state.value = _state.value.copy(providerChecking = true)
            try { container.providerPipelineRepository.diagnoseHistoricalEpisode(releaseId) }
            finally { _state.value = _state.value.copy(providerChecking = false) }
        }
    }

    fun refreshVisibleData() {
        if (_state.value.providerChecking || _state.value.metadataRefreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                providerChecking = true,
                metadataRefreshing = true,
                metadataRefreshError = null
            )
            try {
                val metadata = container.justWatchCatalogRepository.backfillMetadata(animeId)
                val availableIds = _state.value.episodeChecks
                    .filter { it.status.startsWith("AVAILABLE") }
                    .mapTo(mutableSetOf()) { it.releaseId }
                val now = java.time.Instant.now().epochSecond
                _state.value.releases
                    .filter { release ->
                        release.sourceReleaseId !in availableIds &&
                            (release.expectedAt ?: Long.MAX_VALUE) <= now
                    }
                    .sortedByDescending { it.expectedAt ?: Long.MIN_VALUE }
                    .take(2)
                    .forEach { release ->
                        container.providerPipelineRepository.diagnoseHistoricalEpisode(release.sourceReleaseId)
                    }
                _state.value = _state.value.copy(
                    metadataRefreshError = (metadata as? de.anisentinel.app.domain.provider.JustWatchCatalogResult.Failed)?.code
                )
            } finally {
                _state.value = _state.value.copy(providerChecking = false, metadataRefreshing = false)
            }
        }
    }

    fun refreshSeasonAvailability(seasonNumber: Int) {
        if (_state.value.providerChecking) return
        viewModelScope.launch {
            _state.value = _state.value.copy(providerChecking = true)
            try {
                val now = java.time.Instant.now().epochSecond
                _state.value.releases
                    .filterNot { it.isHistoricalImport || it.sourceReleaseId.startsWith("adn-history:") }
                    .filter { (it.seasonNumber ?: 1) == seasonNumber && (it.expectedAt ?: Long.MAX_VALUE) <= now }
                    .maxByOrNull { it.expectedAt ?: Long.MIN_VALUE }
                    ?.let { container.providerPipelineRepository.diagnoseHistoricalEpisode(it.sourceReleaseId) }
            } finally {
                _state.value = _state.value.copy(providerChecking = false)
            }
        }
    }

    fun importCrunchyrollHistory(seriesUrl: String) {
        if (_state.value.historyImportRunning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(historyImportRunning = true, historyImportResult = null)
            val result = container.crunchyrollHistoricalReleaseImporter.import(animeId, seriesUrl)
            _state.value = _state.value.copy(
                historyImportRunning = false,
                historyImportResult = when (result) {
                    is de.anisentinel.app.data.provider.HistoricalImportResult.Success ->
                        "OK:${result.parsed}:${result.inserted}:${result.enriched}"
                    is de.anisentinel.app.data.provider.HistoricalImportResult.Failed ->
                        "ERROR:${result.code}"
                }
            )
        }
    }

    fun diagnoseAndImportAdnHistory(showId: String) {
        if (_state.value.historyImportRunning) return
        viewModelScope.launch {
            _state.value = _state.value.copy(historyImportRunning = true, adnHistoryDiagnostics = null)
            val result = container.adnHistoricalReleaseImporter.diagnoseAndImport(animeId, showId)
            _state.value = _state.value.copy(historyImportRunning = false, adnHistoryDiagnostics = result)
        }
    }

    fun toggleFavorite() {
        if (anime == null) return
        val previous = _state.value.favorite ?: return
        val enabled = !previous
        _state.value = _state.value.copy(favorite = enabled)
        viewModelScope.launch {
            runCatching {
                container.favoritesRepository.setFavoriteEnabled(
                    animeId = animeId,
                    enabled = enabled,
                    languagePreference = _state.value.language.name,
                    monitoringProfileId = _state.value.watchProfileId
                )
            }.onFailure {
                _state.value = _state.value.copy(favorite = previous)
            }
        }
    }

    fun checkProviderNow() {
        val currentAnime = anime ?: return
        val reference = _state.value.providerReference ?: return
        if (reference.provider != "CRUNCHYROLL" || _state.value.providerChecking) return
        val releaseDate = currentAnime.expectedReleaseAt
            ?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: return
        _state.value = _state.value.copy(providerChecking = true)
        viewModelScope.launch {
            container.providerAvailabilityRepository.checkCrunchyroll(
                ProviderCheckRequest(
                    animeId, currentAnime.title, currentAnime.nextEpisodeNumber,
                    releaseDate, reference.seriesUrl
                )
            )
            _state.value = _state.value.copy(providerChecking = false)
        }
    }

    fun setLanguage(language: LanguagePreference) {
        _state.value = _state.value.copy(language = language)
        updateFavoritePreferences()
    }

    fun setProviderPreference(seasonNumber: Int, provider: String) {
        viewModelScope.launch {
            container.database.aniSentinelDao().upsertProviderPreference(
                de.anisentinel.app.data.local.ProviderPreferenceEntity(
                    animeId, seasonNumber, provider, java.time.Instant.now().epochSecond
                )
            )
        }
    }

    fun clearProviderPreference(seasonNumber: Int) {
        viewModelScope.launch {
            container.database.aniSentinelDao().deleteProviderPreference(animeId, seasonNumber)
        }
    }

    fun cycleWatchProfile() {
        val next = de.anisentinel.app.domain.watcher.AvailabilityWatchStrategy
            .nextProfileId(_state.value.watchProfileId)
        _state.value = _state.value.copy(watchProfileId = next)
        updateFavoritePreferences()
    }

    private fun updateFavoritePreferences() {
        if (_state.value.favorite != true) return
        viewModelScope.launch {
            container.favoritesRepository.updateFavoriteConfiguration(
                animeId = animeId,
                languagePreference = _state.value.language.name,
                monitoringProfileId = _state.value.watchProfileId
            )
        }
    }

    private fun Anime.toEntity() = AnimeEntity(
        id = id,
        anilistId = null,
        anisearchId = null,
        titleGerman = title,
        titleEnglish = null,
        titleRomaji = null,
        titleNative = null,
        description = subtitle,
        coverUrl = coverUrl,
        bannerUrl = null,
        season = null,
        seasonYear = null,
        totalEpisodes = episode,
        updatedAt = System.currentTimeMillis() / 1_000
    )
}
