package de.anisentinel.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Source
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.anisentinel.app.R
import de.anisentinel.app.BuildConfig
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.MainActivity
import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.domain.model.ReleaseSourceType
import de.anisentinel.app.domain.watcher.CountdownParts
import de.anisentinel.app.domain.watcher.ReleaseCountdown
import de.anisentinel.app.domain.watcher.ReleaseStatusMachine
import de.anisentinel.app.ui.theme.Cyan400
import de.anisentinel.app.ui.theme.Orange400
import de.anisentinel.app.ui.theme.Red400
import de.anisentinel.app.ui.theme.Teal400
import de.anisentinel.app.ui.theme.Violet400
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import java.time.Clock

@Composable
private fun ScreenContainer(
    scaffoldPadding: PaddingValues,
    onMenu: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppHeader(onMenu)
        content(PaddingValues(horizontal = 20.dp, vertical = 8.dp))
    }
}

@Composable
private fun DynamicGreeting() {
    var localHour by remember { mutableIntStateOf(java.time.LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            localHour = java.time.LocalTime.now().hour
            kotlinx.coroutines.delay(60_000)
        }
    }
    val greeting = when (localHour) {
        in 5..10 -> R.string.home_greeting_morning
        in 11..17 -> R.string.home_greeting_day
        else -> R.string.home_greeting_evening
    }
    PageTitle(stringResource(greeting), stringResource(R.string.home_subtitle))
}

@Composable
fun HomeScreen(
    scaffoldPadding: PaddingValues,
    onMenu: () -> Unit,
    onAnimeClick: (String) -> Unit,
    catalogState: CatalogUiState = CatalogUiState(),
    onRefresh: () -> Unit = {},
    searchState: GlobalSearchUiState = GlobalSearchUiState(),
    onSearchQuery: (String) -> Unit = {},
    onSearch: () -> Unit = {}
) {
    val showingSearch = searchState.query.trim().length >= 2 && searchState.searched
    ScreenContainer(scaffoldPadding, onMenu) { contentPadding ->
        AniSentinelPullToRefresh(catalogState.refreshing, onRefresh) {
        LazyColumn(
            modifier = Modifier.testTag(UiTags.HOME_LIST),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DynamicGreeting() }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.global_search_scope), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = searchState.query,
                            onValueChange = onSearchQuery,
                            modifier = Modifier.fillMaxWidth().testTag("global_search_input"),
                            label = { Text(stringResource(R.string.global_search_label)) },
                            singleLine = true,
                            trailingIcon = {
                                if (searchState.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        )
                        Button(
                            onClick = onSearch,
                            enabled = searchState.query.trim().length >= 2 && !searchState.loading,
                            modifier = Modifier.testTag("global_search_button")
                        ) { Text(stringResource(R.string.global_search_action)) }
                        searchState.error?.let { Text(stringResource(R.string.global_search_error), color = MaterialTheme.colorScheme.error) }
                        if (showingSearch && searchState.results.isEmpty() && !searchState.loading && searchState.error == null) {
                            Text(stringResource(R.string.global_search_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { CatalogStatusCard(catalogState, onRefresh) }
            if (!showingSearch && !catalogState.loading && catalogState.error == null && catalogState.anime.isEmpty()) {
                item { Text(stringResource(R.string.live_empty)) }
            }
            if (showingSearch) items(searchState.results, key = { "search:${it.stableKey}" }) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    searchState.postponementsByAnime[item.id].orEmpty().forEach { PostponementCard(it, Modifier.fillMaxWidth()) }
                    CatalogAnimeCard(item, Modifier.fillMaxWidth()) { onAnimeClick(item.id) }
                }
            } else items(catalogState.anime, key = { it.id }) { anime ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    catalogState.postponementsByAnime[anime.id].orEmpty().forEach { PostponementCard(it, Modifier.fillMaxWidth()) }
                    AnimeCard(anime, Modifier.fillMaxWidth()) { onAnimeClick(anime.id) }
                }
            }
        }
        }
    }
}

@Composable
fun HomeRoute(
    scaffoldPadding: PaddingValues,
    onMenu: () -> Unit,
    onAnimeClick: (String) -> Unit
) {
    val viewModel: HomeViewModel = viewModel()
    val searchViewModel: GlobalSearchViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val searchState by searchViewModel.state.collectAsState()
    HomeScreen(
        scaffoldPadding, onMenu, onAnimeClick, state, viewModel::refresh,
        searchState, searchViewModel::setQuery, searchViewModel::search
    )
}

@Composable
private fun CatalogStatusCard(state: CatalogUiState, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.testTag(UiTags.CATALOG_STATUS),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.loading || state.refreshing) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.live_catalog), style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        state.loading -> stringResource(R.string.live_loading)
                        state.showingCachedData -> stringResource(R.string.live_cached_error)
                        state.error != null -> stringResource(R.string.live_unavailable_friendly)
                        state.anime.isEmpty() -> stringResource(R.string.live_empty)
                        else -> stringResource(R.string.live_success, state.anime.size)
                    },
                    color = if (state.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onRefresh, enabled = !state.loading && !state.refreshing) {
                Text(stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun NextReleaseHero(
    anime: de.anisentinel.app.domain.model.Anime,
    onOpenDetails: () -> Unit,
    onReleaseReached: () -> Unit
) {
    val releaseAt = requireNotNull(anime.expectedReleaseAt)
    val lifecycleOwner = LocalLifecycleOwner.current
    var countdown by remember(releaseAt) {
        mutableStateOf(ReleaseCountdown.calculate(releaseAt))
    }
    var zeroHandled by remember(releaseAt) { mutableStateOf(false) }

    LaunchedEffect(releaseAt, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                countdown = ReleaseCountdown.calculate(
                    releaseAt = releaseAt,
                    clock = Clock.systemUTC()
                )
                if (countdown.isElapsed && !zeroHandled) {
                    zeroHandled = true
                    onReleaseReached()
                }
                val millisToNextSecond = 1_000 - (System.currentTimeMillis() % 1_000)
                delay(millisToNextSecond)
            }
        }
    }
    Card(
        modifier = Modifier.testTag(UiTags.HERO),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimeCover(anime, Modifier.size(width = 86.dp, height = 112.dp))
            Column(
                Modifier.weight(1f).padding(start = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    stringResource(R.string.next_release_in),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelLarge
                )
                CountdownDisplay(countdown)
                Text(
                    stringResource(R.string.title_episode, anime.title, anime.episode),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(R.string.watcher_active),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.labelLarge
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    StatusChip(de.anisentinel.app.domain.watcher.ReleaseStatusResolver().resolve(anime))
                }
                androidx.compose.material3.TextButton(onClick = onOpenDetails) {
                    Text(stringResource(R.string.open_details))
                }
            }
        }
    }
}

@Composable
private fun CountdownDisplay(parts: CountdownParts) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 270.dp) {
            val text = when {
                parts.weeks > 0 -> stringResource(
                    R.string.countdown_compact_weeks,
                    parts.weeks,
                    parts.days,
                    parts.hours,
                    parts.minutes,
                    parts.seconds
                )
                parts.days > 0 -> stringResource(
                    R.string.countdown_compact_days,
                    parts.days,
                    parts.hours,
                    parts.minutes,
                    parts.seconds
                )
                else -> stringResource(
                    R.string.countdown_compact,
                    parts.hours,
                    parts.minutes,
                    parts.seconds
                )
            }
            Text(text, style = MaterialTheme.typography.titleLarge)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (parts.weeks > 0) {
                        CountdownUnit(
                            if (parts.weeks == 1L) {
                                stringResource(R.string.countdown_week, parts.weeks)
                            } else {
                                stringResource(R.string.countdown_weeks, parts.weeks)
                            }
                        )
                    }
                    if (parts.weeks > 0 || parts.days > 0) {
                        CountdownUnit(
                            if (parts.days == 1L) {
                                stringResource(R.string.countdown_day, parts.days)
                            } else {
                                stringResource(R.string.countdown_days, parts.days)
                            }
                        )
                    }
                    CountdownUnit(stringResource(R.string.countdown_hours, parts.hours))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountdownUnit(stringResource(R.string.countdown_minutes, parts.minutes))
                    CountdownUnit(stringResource(R.string.countdown_seconds, parts.seconds))
                }
            }
        }
    }
}

@Composable
private fun CountdownUnit(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = .18f), MaterialTheme.colorScheme.surfaceVariant)
                    )
                )
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}

@Composable
fun CalendarScreen(
    scaffoldPadding: PaddingValues,
    onMenu: () -> Unit,
    onAnimeClick: (String) -> Unit = {}
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val calendarViewModel: CalendarViewModel = viewModel()
    val state by calendarViewModel.state.collectAsState()
    var aniSearchQuery by rememberSaveable { mutableStateOf("") }
    var aniSearchUrl by rememberSaveable { mutableStateOf("") }
    var showManualImport by rememberSaveable { mutableStateOf(false) }
    ScreenContainer(scaffoldPadding, onMenu) { contentPadding ->
        AniSentinelPullToRefresh(state.syncLoading, calendarViewModel::refreshDisplayedMonth) {
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { PageTitle(stringResource(R.string.nav_calendar), stringResource(R.string.calendar_subtitle)) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.syncLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Text(
                                if (state.syncLoading) stringResource(R.string.calendar_sync_loading)
                                else if (state.syncError != null) stringResource(R.string.calendar_sync_failed)
                                else stringResource(R.string.calendar_sync_ready),
                                modifier = Modifier.weight(1f).padding(start = if (state.syncLoading) 10.dp else 0.dp),
                                fontWeight = FontWeight.Bold
                            )
                            Button(onClick = calendarViewModel::retryCalendarSync, enabled = !state.syncLoading) {
                                Text(stringResource(R.string.refresh))
                            }
                        }
                        state.syncError?.let {
                            Text(stringResource(R.string.calendar_sync_error_detail, it), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.historical_calendar_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.historical_calendar_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = calendarViewModel::syncHistoricalProviders,
                            enabled = !state.historySyncRunning
                        ) {
                            if (state.historySyncRunning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                if (state.historySyncRunning) stringResource(R.string.historical_import_running)
                                else stringResource(
                                    R.string.historical_calendar_sync_month,
                                    state.displayedMonth.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar(Char::uppercase),
                                    state.displayedMonth.year
                                )
                            )
                        }
                        state.historySyncSummary?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                    }
                }
            }
            item {
            if (false) { // Legacy AniSearch controls intentionally disabled in the active source path.
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.anisearch_primary_source), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.anisearch_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.anisearch_calendar_unavailable), color = MaterialTheme.colorScheme.tertiary)
                        OutlinedTextField(
                            value = aniSearchQuery,
                            onValueChange = { aniSearchQuery = it },
                            label = { Text(stringResource(R.string.anisearch_search_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { calendarViewModel.searchAniSearch(aniSearchQuery) },
                            enabled = aniSearchQuery.trim().length >= 2 && state.importStatus != AniSearchImportStatus.LOADING
                        ) {
                            Text(stringResource(if (state.importStatus == AniSearchImportStatus.LOADING) R.string.importing else R.string.search_anisearch))
                        }
                        state.searchHits.forEach { hit ->
                            OutlinedButton(
                                onClick = { calendarViewModel.importAniSearchUrl(hit.sourceUrl) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(hit.title) }
                        }
                        when (state.importStatus) {
                            AniSearchImportStatus.IMPORTED -> Column {
                                Text(stringResource(R.string.anisearch_imported, state.importedTitle.orEmpty()))
                                Text(stringResource(R.string.anisearch_import_metadata_success))
                                Text(stringResource(R.string.anisearch_import_calendar_count, state.importCalendarCount))
                                if (state.importCalendarCount == 0) Text(stringResource(R.string.anisearch_import_no_calendar_date), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.anisearch_import_provider_count, state.importProviderCount))
                            }
                            AniSearchImportStatus.LOCAL_IMPORTED -> Column {
                                Text(stringResource(R.string.local_import_success))
                                Text(stringResource(R.string.local_import_counts, state.importAnimeCount, state.importCalendarCount))
                                Text(stringResource(R.string.local_import_label), color = MaterialTheme.colorScheme.tertiary)
                            }
                            AniSearchImportStatus.BLOCKED -> Text(stringResource(R.string.anisearch_access_blocked), color = MaterialTheme.colorScheme.error)
                            AniSearchImportStatus.RATE_LIMITED -> Text(stringResource(R.string.anisearch_rate_limited), color = MaterialTheme.colorScheme.error)
                            AniSearchImportStatus.EMPTY -> Text(stringResource(R.string.anisearch_no_search_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AniSearchImportStatus.FAILED -> Text(stringResource(R.string.anisearch_import_failed), color = MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                        TextButton(onClick = { showManualImport = !showManualImport }) {
                            Text(stringResource(R.string.anisearch_manual_diagnostic))
                        }
                        if (showManualImport) {
                            OutlinedTextField(
                                value = aniSearchUrl,
                                onValueChange = { aniSearchUrl = it },
                                label = { Text(stringResource(R.string.anisearch_url)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = { calendarViewModel.importAniSearchUrl(aniSearchUrl) },
                                enabled = aniSearchUrl.isNotBlank() && state.importStatus != AniSearchImportStatus.LOADING
                            ) { Text(stringResource(R.string.import_anisearch)) }
                        }
                    }
                }
            }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.displayedMonth.month.getDisplayName(TextStyle.FULL, locale)
                            .replaceFirstChar(Char::uppercase) +
                            " ${state.displayedMonth.year}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = calendarViewModel::previousMonth) {
                        Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.previous_month))
                    }
                    IconButton(onClick = calendarViewModel::nextMonth) {
                        Icon(Icons.Outlined.ChevronRight, stringResource(R.string.next_month))
                    }
                }
            }
            item {
                MonthGrid(
                    month = state.displayedMonth,
                    selectedDate = state.selectedDate,
                    releaseDates = state.releaseDatesInMonth,
                    onSelect = calendarViewModel::select
                )
            }
            item {
                Text(
                    if (state.coverage == CalendarCoverage.NOT_LOADED) {
                        stringResource(R.string.release_period_not_loaded)
                    } else if (state.releasesForSelectedDate.isEmpty()) {
                        stringResource(
                            R.string.no_releases_for_date,
                            state.selectedDate.format(
                                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
                            )
                        )
                    } else {
                        state.selectedDate.format(
                            java.time.format.DateTimeFormatter.ofPattern("EEEE, dd. MMMM yyyy", locale)
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.releasesForSelectedDate, key = { it.sourceReleaseId }) { release ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    release.postponements.forEach { PostponementCard(it, Modifier.fillMaxWidth()) }
                    AnimeCard(
                    anime = de.anisentinel.app.domain.model.Anime(
                        id = release.sourceReleaseId,
                        title = release.displayTitle,
                        subtitle = release.releaseLanguage.orEmpty(),
                        provider = release.provider.orEmpty(),
                        expectedReleaseAt = release.releaseAt,
                        episode = release.episodeNumber ?: 0,
                        status = when {
                            release.providerCheckStatus == "NOT_AVAILABLE_YET" -> de.anisentinel.app.domain.model.ReleaseStatus.NOT_AVAILABLE_YET
                            release.providerCheckStatus == "CHECK_FAILED" -> de.anisentinel.app.domain.model.ReleaseStatus.PROVIDER_CHECK_FAILED
                            release.releaseStatus == "CHECKING" -> de.anisentinel.app.domain.model.ReleaseStatus.CHECKING
                            release.releaseStatus == "PENDING_CONFIRMATION" -> de.anisentinel.app.domain.model.ReleaseStatus.PENDING_CONFIRMATION
                            release.releaseStatus == "AVAILABLE" -> de.anisentinel.app.domain.model.ReleaseStatus.AVAILABLE
                            release.releaseStatus in listOf("DELAYED", "DELAYED_CONFIRMED") -> de.anisentinel.app.domain.model.ReleaseStatus.DELAYED_UNCONFIRMED
                            release.releaseStatus == "OVERDUE_UNCONFIRMED" -> de.anisentinel.app.domain.model.ReleaseStatus.PENDING_CONFIRMATION
                            release.releaseStatus in listOf("POSTPONED", "RESCHEDULED") -> de.anisentinel.app.domain.model.ReleaseStatus.OFFICIALLY_POSTPONED
                            release.releaseStatus == "DUE" -> de.anisentinel.app.domain.model.ReleaseStatus.RELEASE_TIME_REACHED
                            else -> de.anisentinel.app.domain.model.ReleaseStatus.SCHEDULED
                        },
                        accentSeed = release.animeId.hashCode(),
                        coverUrl = release.coverUrl,
                        source = "ANIWORLD",
                        metadataSource = de.anisentinel.app.domain.model.MetadataSource.ANIWORLD,
                        releaseTimePrecision = release.releaseTimePrecision
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onAnimeClick(release.animeId) },
                    auxiliaryLabel = buildString {
                        if (release.isHistoricalImport) append(stringResource(R.string.historical_provider_date, release.provider ?: "Provider"))
                        release.releaseLanguage?.let { language ->
                            if (isNotEmpty()) append("\n")
                            append(stringResource(
                                R.string.release_language,
                                if (language == "GER_DUB") "Deutsch (Dub)" else "Deutsch (Sub)"
                            ))
                        }
                        if (release.previousAt != null) {
                            if (isNotEmpty()) append("\n")
                            append(stringResource(
                                R.string.release_postponed_from_to,
                                release.previousAt.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm")),
                                release.releaseAt.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
                            ))
                            release.scheduleChangeReason?.takeIf(String::isNotBlank)?.let {
                                append("\n").append(stringResource(R.string.release_postponed_reason, it))
                            }
                        }
                        release.lastCheckedAt?.let {
                            if (isNotEmpty()) append("\n")
                            append(stringResource(R.string.calendar_last_provider_check,
                                it.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))))
                        }
                        release.providerName?.takeIf(String::isNotBlank)?.let {
                            append("\n").append(stringResource(R.string.calendar_available_provider, it))
                        }
                        release.firstAvailableAt?.let {
                            append("\n").append(stringResource(R.string.first_detected_at,
                                it.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))))
                        }
                        release.sourceAvailableAt?.let {
                            append("\n").append(stringResource(R.string.source_available_at,
                                it.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))))
                        }
                        if (!release.availabilityConfirmed && release.providerErrorCode != null) {
                            append("\n").append(stringResource(R.string.calendar_provider_check_failed))
                            release.fallbackStatus?.let { append("\n").append(stringResource(R.string.calendar_fallback_status, it)) }
                        }
                    }.takeIf(String::isNotBlank)
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    releaseDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val first = month.atDay(1)
    val leading = first.dayOfWeek.value - 1
    val days = List(leading) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels(locale).forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val day = week.getOrNull(index)
                    Box(
                        Modifier.weight(1f).height(46.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val selected = day == selectedDate
                            Column(
                                Modifier
                                    .size(40.dp)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        CircleShape
                                    )
                                    .clickable { onSelect(day) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(day.dayOfMonth.toString(), fontWeight = if (selected) FontWeight.Bold else null)
                                if (day in releaseDates) {
                                    Text("•", color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun weekdayLabels(locale: Locale): List<String> =
    DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale) }

@Composable
fun FavoritesScreen(
    scaffoldPadding: PaddingValues,
    onMenu: () -> Unit,
    onAnimeClick: (String) -> Unit
) {
    val favoritesViewModel: FavoritesViewModel = viewModel()
    val favoritesState by favoritesViewModel.state.collectAsState()
    ScreenContainer(scaffoldPadding, onMenu) { contentPadding ->
        AniSentinelPullToRefresh(favoritesState.refreshing, favoritesViewModel::refresh) {
        val tabs = listOf(R.string.tab_all, R.string.tab_current, R.string.tab_upcoming, R.string.tab_completed)
        val filters = FavoritesFilter.entries
        LazyColumn(
            modifier = Modifier.testTag(UiTags.FAVORITES_LIST),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { PageTitle(stringResource(R.string.nav_favorites), stringResource(R.string.favorites_subtitle)) }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, tab ->
                        AssistChip(
                            onClick = { favoritesViewModel.selectFilter(filters[index]) },
                            label = { Text(stringResource(tab)) },
                            leadingIcon = {
                                Icon(
                                    if (index == 3) Icons.Outlined.CheckCircle else Icons.Outlined.FavoriteBorder,
                                    contentDescription = null
                                )
                            },
                            colors = if (favoritesState.filter == filters[index]) {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            } else AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FavoritesSort.entries.forEach { sort ->
                        AssistChip(
                            onClick = { favoritesViewModel.selectSort(sort) },
                            label = { Text(stringResource(when (sort) {
                                FavoritesSort.NEXT_RELEASE -> R.string.favorites_sort_next_release
                                FavoritesSort.LATEST_RELEASE -> R.string.favorites_sort_latest_release
                                FavoritesSort.TITLE_ASC -> R.string.favorites_sort_title_asc
                                FavoritesSort.TITLE_DESC -> R.string.favorites_sort_title_desc
                                FavoritesSort.PROVIDER_ASC -> R.string.favorites_sort_provider_asc
                                FavoritesSort.PROVIDER_DESC -> R.string.favorites_sort_provider_desc
                            })) },
                            colors = if (favoritesState.sort == sort) {
                                AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            } else AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
            if (favoritesState.loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            } else if (favoritesState.favorites.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.FavoriteBorder,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            if (!favoritesState.hasAnyFavorites) {
                                Text(stringResource(R.string.favorites_empty), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.favorites_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(
                                    stringResource(when (favoritesState.filter) {
                                        FavoritesFilter.ALL -> R.string.favorites_empty
                                        FavoritesFilter.CURRENT -> R.string.favorites_empty_current
                                        FavoritesFilter.UPCOMING -> R.string.favorites_empty_upcoming
                                        FavoritesFilter.COMPLETED -> R.string.favorites_empty_completed
                                    }),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            items(favoritesState.favorites) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    favoritesState.postponementsByAnime[it.id].orEmpty().forEach { shift ->
                        PostponementCard(shift, Modifier.fillMaxWidth())
                    }
                    AnimeCard(it, Modifier.fillMaxWidth(), onClick = { onAnimeClick(it.id) })
                }
            }
        }
        }
    }
}

@Composable
fun DiscoverScreen(
    scaffoldPadding: PaddingValues,
    onMenu: () -> Unit,
    onAnimeClick: (String) -> Unit
) {
    val discoverViewModel: DiscoverViewModel = viewModel()
    val state by discoverViewModel.state.collectAsState()
    ScreenContainer(scaffoldPadding, onMenu) { contentPadding ->
        AniSentinelPullToRefresh(state.loading, discoverViewModel::refresh) {
        LazyColumn(
            modifier = Modifier.testTag(UiTags.DISCOVER_LIST),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { PageTitle(stringResource(R.string.nav_discover), stringResource(R.string.discover_genre_subtitle)) }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error -> item {
                Text(stringResource(R.string.discover_real_data_error, error), color = MaterialTheme.colorScheme.error)
            } }
            item {
                Text(stringResource(R.string.discover_genres), style = MaterialTheme.typography.titleLarge)
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.selectedGenre == null,
                        onClick = { discoverViewModel.selectGenre(null) },
                        label = { Text(stringResource(R.string.tab_all)) }
                    )
                    state.genres.forEach { genre ->
                        FilterChip(
                            selected = state.selectedGenre == genre.genreId,
                            onClick = { discoverViewModel.selectGenre(genre.genreId) },
                            label = { Text(genre.label) }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscoverTypeFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.typeFilter == filter,
                            onClick = { discoverViewModel.selectType(filter) },
                            label = { Text(stringResource(filter.labelResource)) }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiscoverSort.entries.forEach { sort ->
                        AssistChip(
                            onClick = { discoverViewModel.selectSort(sort) },
                            label = { Text(stringResource(sort.labelResource)) },
                            colors = if (state.sort == sort) AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            else AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
            if (state.providers.isNotEmpty()) item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.providerFilter == null,
                        onClick = { discoverViewModel.selectProvider(null) },
                        label = { Text(stringResource(R.string.discover_all_providers)) }
                    )
                    state.providers.forEach { provider ->
                        FilterChip(
                            selected = state.providerFilter == provider,
                            onClick = { discoverViewModel.selectProvider(provider) },
                            label = { Text(provider) }
                        )
                    }
                }
            }
            if (!state.loading && state.titles.isEmpty()) {
                item { Text(stringResource(R.string.discover_no_real_genre_titles), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.titles, key = { "discover:${it.stableKey}" }) {
                title ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.postponementsByAnime[title.id].orEmpty().forEach { PostponementCard(it, Modifier.fillMaxWidth()) }
                    CatalogAnimeCard(title, Modifier.fillMaxWidth()) {
                        onAnimeClick(title.id)
                    }
                }
            }
        }
        }
    }
}

private val DiscoverTypeFilter.labelResource: Int get() = when (this) {
    DiscoverTypeFilter.ALL -> R.string.tab_all
    DiscoverTypeFilter.SHOWS -> R.string.discover_filter_shows
    DiscoverTypeFilter.MOVIES -> R.string.discover_filter_movies
    DiscoverTypeFilter.RUNNING -> R.string.discover_filter_running
    DiscoverTypeFilter.COMPLETED -> R.string.discover_filter_completed
    DiscoverTypeFilter.GER_SUB -> R.string.discover_filter_ger_sub
    DiscoverTypeFilter.GER_DUB -> R.string.discover_filter_ger_dub
}

private val DiscoverSort.labelResource: Int get() = when (this) {
    DiscoverSort.RELEVANCE -> R.string.discover_sort_relevance
    DiscoverSort.POPULARITY -> R.string.discover_sort_popularity
    DiscoverSort.NEWEST -> R.string.discover_sort_newest
    DiscoverSort.OLDEST -> R.string.discover_sort_oldest
    DiscoverSort.TITLE_ASC -> R.string.favorites_sort_title_asc
    DiscoverSort.TITLE_DESC -> R.string.favorites_sort_title_desc
}

private data class SettingsItem(val title: Int, val subtitle: Int, val icon: ImageVector)

@Composable
fun SettingsScreen(
    scaffoldPadding: PaddingValues,
    onMenu: () -> Unit,
    onAboutClick: () -> Unit
) {
    val settingsViewModel: SettingsViewModel = viewModel()
    val settings by settingsViewModel.settings.collectAsState()
    val monitoringDiagnostics by settingsViewModel.monitoringDiagnostics.collectAsState()
    val context = LocalContext.current
    val activity = LocalView.current.context as? MainActivity
    val dispatcher = (context.applicationContext as AniSentinelApplication)
        .container.androidNotificationDispatcher
    var notificationPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val requestNotificationPermission = {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionDenied = false
            notificationPermissionDenied = !dispatcher.canPostNotifications()
        } else if (
            notificationPermissionDenied &&
            activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
        } else {
            activity?.requestNotificationPermission { granted ->
                notificationPermissionDenied = !granted
                if (granted) {
                    notificationPermissionDenied = !dispatcher.canPostNotifications()
                }
            }
        }
        Unit
    }
    val futureItems = listOf(
        SettingsItem(
            R.string.settings_calendar,
            R.string.settings_calendar_subtitle,
            Icons.Outlined.CalendarMonth
        ),
        SettingsItem(
            R.string.settings_backup,
            R.string.settings_backup_subtitle,
            Icons.Outlined.Backup
        ),
        SettingsItem(
            R.string.settings_privacy,
            R.string.settings_privacy_subtitle,
            Icons.Outlined.PrivacyTip
        )
    )
    Box(Modifier.fillMaxSize().testTag(UiTags.SETTINGS)) {
    ScreenContainer(scaffoldPadding, onMenu) { contentPadding ->
        LazyColumn(
            modifier = Modifier.testTag(UiTags.SETTINGS_LIST),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { PageTitle(stringResource(R.string.nav_settings), stringResource(R.string.settings_subtitle)) }
            item { SectionHeader(stringResource(R.string.settings_group_app)) }
            item {
                SettingsActionCard(
                    title = stringResource(R.string.settings_appearance),
                    value = when (settings.theme) {
                        de.anisentinel.app.domain.repository.ThemePreference.SYSTEM ->
                            stringResource(R.string.theme_system)
                        de.anisentinel.app.domain.repository.ThemePreference.DARK ->
                            stringResource(R.string.theme_dark)
                        de.anisentinel.app.domain.repository.ThemePreference.LIGHT ->
                            stringResource(R.string.theme_light)
                    },
                    icon = Icons.Outlined.Palette,
                    status = stringResource(R.string.active),
                    onClick = settingsViewModel::cycleTheme
                )
            }
            item {
                SettingsActionCard(
                    title = stringResource(R.string.settings_language),
                    value = stringResource(
                        if (settings.languageTag == "de") {
                            R.string.language_german
                        } else {
                            R.string.settings_language_english
                        }
                    ),
                    icon = Icons.Outlined.Language,
                    status = stringResource(R.string.active),
                    modifier = Modifier.testTag(UiTags.SETTINGS_LANGUAGE),
                    onClick = settingsViewModel::toggleLanguage
                )
            }
            item { SectionHeader(stringResource(R.string.settings_group_monitoring)) }
            item {
                SettingsActionCard(
                    title = stringResource(R.string.watch_profile),
                    value = localizedWatchProfile(settings.watchProfileId),
                    icon = Icons.Outlined.Shield,
                    status = stringResource(R.string.active),
                    onClick = settingsViewModel::cycleWatchProfile
                )
            }
            item {
                SettingsActionCard(
                    title = stringResource(R.string.settings_notifications),
                    value = stringResource(
                        if (settings.notificationsEnabled && dispatcher.canPostNotifications()) {
                            R.string.notifications_on
                        } else {
                            R.string.notifications_off
                        }
                    ),
                    icon = Icons.Outlined.NotificationsNone,
                    status = stringResource(
                        if (settings.notificationsEnabled && dispatcher.canPostNotifications()) R.string.active
                        else R.string.inactive
                    ),
                    modifier = Modifier.testTag(UiTags.NOTIFICATION_TOGGLE),
                    onClick = settingsViewModel::toggleNotifications
                )
            }
            item {
                SettingsActionCard(
                    title = stringResource(R.string.notification_demo),
                    value = stringResource(
                        if (notificationPermissionDenied) {
                            R.string.notification_permission_denied_hint
                        } else {
                            R.string.notification_demo_hint
                        }
                    ),
                    icon = Icons.Outlined.NotificationsNone,
                    status = stringResource(
                        if (dispatcher.canPostNotifications()) R.string.active else R.string.inactive
                    ),
                    modifier = Modifier.testTag(UiTags.NOTIFICATION_DEMO),
                    onClick = requestNotificationPermission
                )
            }
            item {
                val provider = stringResource(R.string.provider_not_checked)
                SettingsActionCard(
                    title = stringResource(R.string.drawer_providers),
                    value = provider,
                    icon = Icons.Outlined.Source,
                    status = stringResource(R.string.coming_soon),
                    enabled = false,
                    onClick = {}
                )
            }
            item { SectionHeader(stringResource(R.string.monitoring_diagnostics)) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(stringResource(R.string.diagnostic_active_favorites, monitoringDiagnostics.activeFavorites))
                        Text(stringResource(R.string.diagnostic_scheduled_jobs, monitoringDiagnostics.scheduledJobs))
                        Text(stringResource(R.string.diagnostic_deliveries, monitoringDiagnostics.deliveries))
                        monitoringDiagnostics.latestCheck?.let { check ->
                            Text(stringResource(R.string.episode_check_status, check.status))
                            Text(stringResource(R.string.episode_check_target, check.seasonNumber ?: 1, check.episodeNumber ?: 0))
                            check.providerName.takeIf(String::isNotBlank)?.let {
                                Text(stringResource(R.string.release_provider, it))
                            }
                        }
                        monitoringDiagnostics.latestDeliveries.forEach { delivery ->
                            Text(
                                "${delivery.eventType}: ${delivery.deliveredAt.diagnosticTime()} · ${delivery.deliveryId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        monitoringDiagnostics.latestChecks.forEach { check ->
                            Text(
                                buildString {
                                    append("${check.source}: ${check.status} · ${check.lastCheckedAt.diagnosticTime()}")
                                    check.errorCode?.let { append(" · $it") }
                                    check.nextCheckAt?.let { append(" · Retry ${it.diagnosticTime()}") }
                                    check.firstAvailableAt?.let { append(" · erkannt ${it.diagnosticTime()}") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.settings_group_more)) }
            items(futureItems) { item ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(stringResource(item.title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(item.subtitle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            stringResource(R.string.coming_soon),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Card(
                    onClick = onAboutClick,
                    modifier = Modifier.testTag(UiTags.ABOUT_ENTRY),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(
                                stringResource(R.string.settings_about),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(R.string.settings_about_subtitle),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Outlined.ArrowForwardIos,
                            contentDescription = stringResource(R.string.open_about),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

private fun Long.diagnosticTime(): String = java.time.Instant.ofEpochSecond(this)
    .atZone(java.time.ZoneId.systemDefault())
    .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm:ss"))

@Composable
private fun localImportErrorText(code: String): String = stringResource(
    when (code) {
        "UNSUPPORTED_SCHEMA_VERSION" -> R.string.local_import_error_schema
        "RIGHTS_CONFIRMATION_REQUIRED", "REDISTRIBUTION_NOT_ALLOWED" -> R.string.local_import_error_rights
        "UNKNOWN_ANIME_EXTERNAL_ID" -> R.string.local_import_error_unknown_anime
        "DUPLICATE_RELEASE_ID", "DUPLICATE_EXTERNAL_ID" -> R.string.local_import_error_duplicate
        "HTTPS_URL_REQUIRED" -> R.string.local_import_error_url
        "FILE_TOO_LARGE", "ANIME_COUNT_INVALID", "RELEASE_COUNT_INVALID", "TEXT_TOO_LONG" -> R.string.local_import_error_limits
        "EXTERNAL_ID_MAPPING_CONFLICT" -> R.string.local_import_error_mapping
        "DATASET_CONTENT_CONFLICT" -> R.string.local_import_error_dataset_conflict
        "DATABASE_TRANSACTION_FAILED" -> R.string.local_import_error_database
        "INVALID_JSON", "INVALID_GENERATED_AT", "INVALID_RELEASE_AT" -> R.string.local_import_error_format
        else -> R.string.local_import_error_generic
    }
)

@Composable
private fun SettingsActionCard(
    title: String,
    value: String,
    icon: ImageVector,
    status: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                status,
                color = if (enabled) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
