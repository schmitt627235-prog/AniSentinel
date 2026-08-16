package de.anisentinel.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import de.anisentinel.app.R
import de.anisentinel.app.domain.model.LanguagePreference
import de.anisentinel.app.domain.watcher.ReleaseStatusResolver
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
private fun ReleaseHistoryCard(
    heading: String,
    release: de.anisentinel.app.data.local.EpisodeReleaseEntity,
    checks: List<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity>,
    inferred: Boolean = false
) {
    val check = checks.filter { it.releaseId == release.sourceReleaseId }
        .maxWithOrNull(compareBy<de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity> {
            if (it.status.startsWith("AVAILABLE_")) 1 else 0
        }.thenBy { it.lastCheckedAt })
    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(heading, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("S${release.seasonNumber ?: 1} · Folge ${release.episodeNumber ?: 0} · ${localizedReleaseLanguage(release.releaseLanguage)}")
            release.expectedAt?.let {
                val value = java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault())
                Text(if (release.releaseTimePrecision == "DATE") value.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) else value.format(formatter))
            }
            if (release.isHistoricalImport) Text(stringResource(R.string.historical_provider_date, release.provider ?: "Provider"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (inferred) Text(stringResource(R.string.previous_release_time_inferred), color = MaterialTheme.colorScheme.tertiary)
            Text(localizedReleaseStatus(check?.status ?: release.releaseStatus))
            ReleaseDelayLabel(
                release.expectedAt?.let(java.time.Instant::ofEpochSecond),
                when {
                    release.releaseStatus in listOf("POSTPONED", "RESCHEDULED") -> de.anisentinel.app.domain.model.ReleaseStatus.OFFICIALLY_POSTPONED
                    check?.status?.startsWith("AVAILABLE_") == true -> de.anisentinel.app.domain.model.ReleaseStatus.AVAILABLE
                    check?.status == "NOT_AVAILABLE_YET" -> de.anisentinel.app.domain.model.ReleaseStatus.NOT_AVAILABLE_YET
                    check?.status == "CHECK_FAILED" -> de.anisentinel.app.domain.model.ReleaseStatus.PROVIDER_CHECK_FAILED
                    else -> de.anisentinel.app.domain.model.ReleaseStatus.PENDING_CONFIRMATION
                },
                check?.firstAvailableAt?.let(java.time.Instant::ofEpochSecond)
            )
            check?.providerName?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.release_provider, it)) }
            check?.sourceAvailableAt?.let { Text(stringResource(R.string.source_available_at, java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))) }
            check?.firstAvailableAt?.let { Text(stringResource(R.string.first_detected_at, java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))) }
            check?.lastCheckedAt?.let { Text(stringResource(R.string.calendar_last_provider_check, java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))) }
            if (check?.status == "CHECK_FAILED") Text(stringResource(R.string.provider_check_failed_neutral), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AnimeDetailScreen(
    scaffoldPadding: PaddingValues,
    animeId: String,
    onBack: () -> Unit,
    focusedSeason: Int? = null,
    focusedEpisode: Int? = null,
    focusedLanguage: String? = null
) {
    val detailViewModel: DetailViewModel = viewModel()
    val state by detailViewModel.state.collectAsState()
    if (state.loading) {
        DetailLoadingState(scaffoldPadding, onBack, loading = true)
        return
    }
    val anime = state.anime
    if (anime == null) {
        DetailLoadingState(scaffoldPadding, onBack, loading = false)
        return
    }
    val isAniList = anime.source == "ANILIST"
    val context = LocalContext.current
    val resolvedStatus = ReleaseStatusResolver().resolve(anime)

    Column(
        Modifier
            .fillMaxSize()
            .testTag(UiTags.DETAIL)
            .padding(scaffoldPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.details), style = MaterialTheme.typography.titleLarge)
        }
        AniSentinelPullToRefresh(
            refreshing = state.metadataRefreshing || state.providerChecking,
            onRefresh = detailViewModel::refreshVisibleData,
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (focusedEpisode != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.notification_release_target), style = MaterialTheme.typography.titleMedium)
                            Text("S${focusedSeason ?: 1} · Folge $focusedEpisode · ${focusedLanguage ?: "–"}")
                        }
                    }
                }
            }
            val nowEpoch = java.time.Instant.now().epochSecond
            val futureReleases = state.releases.filter { (it.expectedAt ?: Long.MIN_VALUE) > nowEpoch }
            val focusedRelease = if (focusedEpisode != null) futureReleases.firstOrNull {
                it.episodeNumber == focusedEpisode &&
                    (focusedSeason == null || it.seasonNumber == focusedSeason) &&
                    (focusedLanguage == null || it.releaseLanguage == focusedLanguage)
            } else null
            val nextRelease = focusedRelease ?: futureReleases.minWithOrNull(
                compareBy<de.anisentinel.app.data.local.EpisodeReleaseEntity> { it.expectedAt ?: Long.MAX_VALUE }
                    .thenByDescending { it.episodeNumber ?: Int.MIN_VALUE }
            )
            val regularScheduleAnchor = nextRelease?.let { release ->
                state.postponements.firstOrNull { shift ->
                    shift.seasonNumber == release.seasonNumber &&
                        shift.episodeNumber == release.episodeNumber &&
                        shift.releaseLanguage == release.releaseLanguage
                }?.originalExpectedAt
            }
            val lastRelease = ReleaseDisplayResolver.previousFor(
                state.releases,
                nextRelease,
                nowEpoch,
                regularScheduleAnchor
            )
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    nextRelease?.let { release -> ReleaseHistoryCard(stringResource(R.string.next_release), release, state.episodeChecks) }
                    lastRelease?.let { display ->
                        ReleaseHistoryCard(
                            stringResource(if (display.inferred) R.string.previous_expected_release else R.string.last_release),
                            display.release,
                            state.episodeChecks,
                            display.inferred
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimeCover(anime, Modifier.size(width = 124.dp, height = 172.dp))
                    Column(
                        Modifier.weight(1f).padding(start = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(anime.title, style = MaterialTheme.typography.headlineMedium)
                        CompactPostponementNotice(state.postponements)
                        ReleaseCountdownLabel(anime.expectedReleaseAt)
                        StatusChip(resolvedStatus)
                        Button(
                            onClick = detailViewModel::toggleFavorite,
                            enabled = state.favorite != null,
                            modifier = Modifier.testTag(UiTags.FAVORITE_BUTTON)
                        ) {
                            if (state.favorite == null) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (state.favorite == true) {
                                        Icons.Filled.Favorite
                                    } else {
                                        Icons.Outlined.FavoriteBorder
                                    },
                                    contentDescription = null
                                )
                            }
                            Text(
                                stringResource(
                                    if (state.favorite == true) {
                                        R.string.remove_favorite
                                    } else {
                                        R.string.favorite
                                    }
                                ),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.synopsis)) {
                    val justWatchDescription = state.justWatchMetadata?.description
                        ?.takeIf { de.anisentinel.app.data.provider.MetadataTextNormalizer.detectedLanguage(it) == "de" }
                    val fallbackDescription = anime.description
                        ?.takeIf { de.anisentinel.app.data.provider.MetadataTextNormalizer.detectedLanguage(it) == "de" }
                    Text(
                        justWatchDescription
                            ?: fallbackDescription
                            ?: stringResource(R.string.metadata_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.justWatchMetadata?.descriptionGermanSource == "TRANSLATED_FROM_JUSTWATCH") {
                        Text(stringResource(R.string.synopsis_translated_notice), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.languages)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            onClick = { detailViewModel.setLanguage(LanguagePreference.SUB) },
                            label = { Text(stringResource(R.string.language_sub)) },
                            selected = state.language == LanguagePreference.SUB,
                            leadingIcon = { Icon(Icons.Outlined.Language, null) }
                        )
                        FilterChip(
                            onClick = { detailViewModel.setLanguage(LanguagePreference.DUB) },
                            label = { Text(stringResource(R.string.language_german)) },
                            selected = state.language == LanguagePreference.DUB
                        )
                        FilterChip(
                            onClick = { detailViewModel.setLanguage(LanguagePreference.BOTH) },
                            label = { Text(stringResource(R.string.language_both)) },
                            selected = state.language == LanguagePreference.BOTH
                        )
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.watch_profile)) {
                    Button(onClick = detailViewModel::cycleWatchProfile) {
                        Text(localizedWatchProfile(state.watchProfileId))
                    }
                    Text(
                        stringResource(R.string.saved_locally),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                DetailSection(stringResource(R.string.release_monitoring)) {
                    val scheduled = state.scheduledRelease
                    Text(
                        if (state.favorite != true) stringResource(R.string.release_job_not_favorite)
                        else if (scheduled == null) stringResource(R.string.release_job_not_scheduled)
                        else stringResource(
                            R.string.release_job_scheduled,
                            java.time.Instant.ofEpochSecond(scheduled.eventAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
                        ),
                        color = if (scheduled != null) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.latestEpisodeCheck?.let { check ->
                        Text(stringResource(R.string.episode_check_status, localizedReleaseStatus(check.status)))
                        Text(stringResource(R.string.episode_check_target, check.seasonNumber ?: 1, check.episodeNumber ?: 0))
                        check.providerName.takeIf(String::isNotBlank)?.let {
                            Text(stringResource(R.string.release_provider, it))
                        }
                        check.firstAvailableAt?.let { detectedAt ->
                            Text(stringResource(R.string.first_detected_at, detectedAt.localTimeText()))
                            state.scheduledRelease?.eventAt?.let { expectedAt ->
                                Text(stringResource(R.string.planned_at, expectedAt.localTimeText()))
                                val delayMinutes = ((detectedAt - expectedAt) / 60).coerceAtLeast(0)
                                Text(stringResource(R.string.detected_delay_minutes, delayMinutes))
                            }
                        }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.metadata_source)) {
                    Text(stringResource(if (isAniList) R.string.source_anilist_metadata else R.string.source_aniworld_metadata))
                    anime.metadataSourceUrl?.let { url ->
                        Button(
                            onClick = {
                                context.openProviderUrlSafely(url)
                            }
                        ) {
                            Text(stringResource(R.string.open_source))
                        }
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.providers)) {
                    val providerSummary = ProviderSummaryResolver.resolve(
                        hasProviderReference = state.providerReference != null,
                        titleStatus = state.providerAvailability?.status,
                        confirmedEpisodeProviders = state.episodeChecks
                            .filter { it.status.startsWith("AVAILABLE") }
                            .sortedByDescending { it.lastCheckedAt }
                            .map { it.providerName }
                    )
                    val crunchyrollSeriesUrl = state.providerMetadataIdentities
                        .firstOrNull { it.provider.contains("CRUNCHYROLL") && it.sourceUrl?.startsWith("https://") == true }
                        ?.sourceUrl
                        ?: state.providerReferences.firstOrNull {
                            it.provider == "CRUNCHYROLL" && it.seriesUrl?.startsWith("https://") == true
                        }?.seriesUrl
                    val adnIdentity = state.providerMetadataIdentities.firstOrNull {
                        it.provider.startsWith("ADN") && it.providerMarket == "DE"
                    }
                    Text(
                        when (providerSummary.status) {
                            ProviderSummaryStatus.UNKNOWN -> stringResource(R.string.provider_unknown)
                            ProviderSummaryStatus.NOT_CHECKED -> stringResource(R.string.provider_not_checked)
                            ProviderSummaryStatus.AVAILABLE -> providerSummary.provider?.let {
                                stringResource(R.string.provider_episode_availability_confirmed, it)
                            } ?: stringResource(R.string.status_available)
                            ProviderSummaryStatus.NOT_AVAILABLE_YET -> stringResource(R.string.status_not_available_yet)
                            ProviderSummaryStatus.CHECK_FAILED -> stringResource(R.string.status_provider_check_failed)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    state.providerReference?.let { reference ->
                        reference.providerMarket?.let { market ->
                        val confirmedAt = reference.lastConfirmedAt?.let {
                            java.time.Instant.ofEpochSecond(it)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                        } ?: stringResource(R.string.provider_market_time_unknown)
                        Text(
                            stringResource(R.string.provider_market_diagnostic, market, confirmedAt),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        }
                    }
                    state.providerAvailability?.let { availability ->
                        Text(stringResource(R.string.last_checked_epoch, availability.checkedAt))
                        availability.providerUrl?.let { url ->
                            Button(onClick = { context.openProviderUrlSafely(url) }) {
                                Text(stringResource(R.string.open_provider))
                            }
                        }
                    }
                    if (state.providerReference?.provider == "CRUNCHYROLL") {
                        Button(onClick = detailViewModel::checkProviderNow, enabled = !state.providerChecking) {
                            Text(stringResource(R.string.check_provider_now))
                        }
                    }
                    crunchyrollSeriesUrl?.let { url ->
                        Button(
                            onClick = { detailViewModel.importCrunchyrollHistory(url) },
                            enabled = !state.historyImportRunning
                        ) {
                            Text(stringResource(if (state.historyImportRunning) R.string.historical_import_running else R.string.historical_update_action))
                        }
                    }
                    state.historyImportResult?.let { result ->
                        val parts = result.split(':')
                        Text(
                            if (parts.firstOrNull() == "OK") stringResource(
                                R.string.historical_import_success,
                                parts.getOrNull(1)?.toIntOrNull() ?: 0,
                                parts.getOrNull(2)?.toIntOrNull() ?: 0,
                                parts.getOrNull(3)?.toIntOrNull() ?: 0
                            ) else stringResource(R.string.historical_import_failed),
                            color = if (parts.firstOrNull() == "OK") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }
                    adnIdentity?.let { identity ->
                        Button(
                            onClick = { detailViewModel.diagnoseAndImportAdnHistory(identity.seriesId) },
                            enabled = !state.historyImportRunning
                        ) { Text(stringResource(R.string.adn_history_diagnostic_action)) }
                    }
                    state.adnHistoryDiagnostics?.let { diagnostic ->
                        val dateFields = diagnostic.observedDateFields.joinToString()
                        Text(stringResource(R.string.adn_history_diagnostic_completed))
                        Text(stringResource(R.string.adn_history_diagnostic_counts,
                            diagnostic.episodeCount, diagnostic.datedEpisodeCount,
                            diagnostic.imported, diagnostic.enriched, diagnostic.conflicts))
                        Text(stringResource(R.string.adn_history_diagnostic_fields,
                            if (dateFields.isBlank()) stringResource(R.string.none) else dateFields))
                        Text(stringResource(R.string.provider_series_id, diagnostic.showId))
                    }
                }
            }
            item {
                DetailSection(stringResource(R.string.genres)) {
                    val genres = de.anisentinel.app.data.provider.MetadataTextNormalizer.normalizeGenres(
                        state.justWatchMetadata?.genres.orEmpty().split(','), state.justWatchGenreLabels
                    )
                    Text(genres.takeIf(List<String>::isNotEmpty)?.joinToString(" · ")
                        ?: stringResource(R.string.metadata_unavailable))
                }
            }
            if (!state.justWatchMetadata?.studios.isNullOrBlank()) item {
                DetailSection(stringResource(R.string.studios)) {
                    val studios = state.justWatchMetadata?.studios.orEmpty().lines()
                        .mapNotNull(de.anisentinel.app.data.provider.MetadataTextNormalizer::decode).distinct()
                    Text(studios.joinToString(" · "))
                }
            }
            item { SectionHeader(stringResource(R.string.episodes)) }
            val newestRelevantReleaseIds = state.releases
                .filter { (it.expectedAt ?: Long.MAX_VALUE) <= java.time.Instant.now().epochSecond }
                .sortedByDescending { it.expectedAt ?: Long.MIN_VALUE }
                .take(2)
                .mapTo(mutableSetOf()) { it.sourceReleaseId }
            val visibleEpisodes = if (isAniList) listOfNotNull(anime.episode.takeIf { it > 0 })
                else EpisodeCardResolver.visibleEpisodeNumbers(anime.episode, state.releases)
            items(visibleEpisodes) { episode ->
                val concreteCheck = state.episodeChecks
                    .filter { it.episodeNumber == episode }
                    .maxByOrNull { it.lastCheckedAt }
                val historicalRelease = state.releases
                    .filter {
                        it.episodeNumber == episode &&
                            (it.expectedAt ?: Long.MAX_VALUE) <= java.time.Instant.now().epochSecond &&
                            ReleaseDisplayResolver.isPlausibleForCurrentSeason(it, nextRelease)
                    }
                    .maxByOrNull { it.expectedAt ?: Long.MIN_VALUE }
                val identity = state.providerMetadataIdentities.firstOrNull { it.episodeNumber == episode }
                    ?: state.providerMetadataIdentities.firstOrNull { it.episodeNumber == null }
                val providerDeepLink = concreteCheck?.providerUrl?.takeIf { it.startsWith("https://") }
                    ?: state.providerReferences.firstOrNull { it.seriesUrl?.startsWith("https://") == true }?.seriesUrl
                val concreteOfferProviders = state.justWatchOffers
                    .filter { it.episodeNumber == episode }
                    .map { it.providerName }
                    .distinct()
                val availabilityText = when {
                    concreteCheck?.status?.startsWith("AVAILABLE") == true ->
                        stringResource(R.string.episode_available_at, concreteCheck.providerName)
                    historicalRelease?.releaseStatus?.startsWith("AVAILABLE") == true ->
                        stringResource(
                            R.string.episode_available_at,
                            historicalRelease.provider ?: state.providerReferences.firstOrNull()?.provider ?: "Provider"
                        )
                    concreteOfferProviders.isNotEmpty() -> stringResource(R.string.episode_not_confirmed_checked)
                    concreteCheck != null ->
                        stringResource(R.string.episode_not_confirmed_checked)
                    state.providerReferences.isNotEmpty() -> stringResource(R.string.episode_not_confirmed_checked)
                    else -> stringResource(R.string.provider_availability_unknown)
                }
                val isConfirmedAvailable = concreteCheck?.status?.startsWith("AVAILABLE") == true ||
                    historicalRelease?.releaseStatus?.startsWith("AVAILABLE") == true
                val showAvailabilityCheck = AvailabilityActionPolicy.showCheck(
                    historicalRelease?.sourceReleaseId, newestRelevantReleaseIds, isConfirmedAvailable
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.PlaylistPlay,
                            null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            stringResource(R.string.episode_number, episode),
                            Modifier.padding(start = 12.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(availabilityText)
                            concreteCheck?.firstAvailableAt?.let { detectedAt ->
                                Text(
                                    stringResource(R.string.first_detected_at, detectedAt.localTimeText()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                    if (historicalRelease != null || providerDeepLink != null || identity != null) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            identity?.seriesId?.let { Text(stringResource(R.string.provider_series_id, it), style = MaterialTheme.typography.bodySmall) }
                            identity?.seasonId?.let { Text(stringResource(R.string.provider_season_id, it), style = MaterialTheme.typography.bodySmall) }
                            identity?.episodeId?.let { Text(stringResource(R.string.provider_episode_id, it), style = MaterialTheme.typography.bodySmall) }
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                historicalRelease?.takeIf { showAvailabilityCheck }?.let { release ->
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !state.providerChecking,
                                        onClick = { detailViewModel.diagnoseHistoricalEpisode(release.sourceReleaseId) }
                                    ) {
                                        Text(stringResource(if (state.providerChecking) R.string.diagnostic_check_running else R.string.diagnostic_check_now))
                                    }
                                }
                                providerDeepLink?.let { url ->
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { context.openProviderUrlSafely(url) }
                                    ) {
                                        Text(stringResource(R.string.open_at_provider))
            }
        }
        }
    }
}
                    }
                }
            item {
                DetailSection(stringResource(R.string.release_history)) {
                    val sourceGroups = state.releases.groupBy { release ->
                        when {
                            release.isHistoricalImport -> release.provider ?: "Provider"
                            release.metadataSource == "ANIWORLD_CALENDAR" -> "AniWorld"
                            else -> release.provider ?: release.metadataSource
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Timeline, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (sourceGroups.isEmpty()) {
                                Text(stringResource(if (isAniList) R.string.metadata_unavailable else R.string.release_history_empty))
                            } else {
                                sourceGroups.toSortedMap().forEach { (source, releases) ->
                                    val historical = releases.count { it.isHistoricalImport }
                                    Text(
                                        stringResource(R.string.release_history_source_count, source, releases.size),
                                        color = if (historical > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
}

private fun Long.localTimeText(): String = java.time.Instant.ofEpochSecond(this)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

@Composable
private fun localizedReleaseLanguage(value: String?): String = stringResource(
    when (value) {
        "GER_SUB" -> R.string.release_language_sub
        "GER_DUB" -> R.string.release_language_dub
        else -> R.string.none
    }
)

@Composable
internal fun localizedReleaseStatus(value: String?): String = stringResource(
    when {
        value == "SCHEDULED" -> R.string.release_status_scheduled
        value?.startsWith("AVAILABLE") == true -> R.string.release_status_available
        value == "NOT_AVAILABLE_YET" -> R.string.release_status_not_available
        value == "CHECK_FAILED" -> R.string.release_status_check_failed
        value in setOf("POSTPONED", "RESCHEDULED", "DELAYED", "DELAYED_CONFIRMED") -> R.string.release_status_postponed
        else -> R.string.release_status_not_available
    }
)

private fun android.content.Context.openProviderUrlSafely(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) return false
    return runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

@Composable
private fun DetailLoadingState(
    scaffoldPadding: PaddingValues,
    onBack: () -> Unit,
    loading: Boolean
) {
    Column(
        Modifier.fillMaxSize().padding(scaffoldPadding).background(
            MaterialTheme.colorScheme.background
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.details), style = MaterialTheme.typography.titleLarge)
        }
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
        } else {
            Text(
                stringResource(R.string.detail_not_found),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun localizedWatchProfile(profileId: String): String = stringResource(
    when (profileId) {
        "30s" -> R.string.profile_30_seconds
        "1m" -> R.string.profile_1_minute
        "2m" -> R.string.profile_2_minutes
        "5m" -> R.string.profile_5_minutes
        "10m" -> R.string.profile_10_minutes
        "15m" -> R.string.profile_15_minutes
        "30m" -> R.string.profile_30_minutes
        "1h" -> R.string.profile_1_hour
        "automatic" -> R.string.profile_automatic
        else -> R.string.profile_automatic
    }
)

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
