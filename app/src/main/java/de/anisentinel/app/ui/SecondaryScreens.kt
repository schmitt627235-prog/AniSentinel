package de.anisentinel.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.compose.viewModel
import de.anisentinel.app.R
import de.anisentinel.app.data.image.CoverImageLoader
import de.anisentinel.app.data.local.AnnouncementEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun SecondaryHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun ProvidersScreen(padding: PaddingValues, onBack: () -> Unit, onAnimeClick: (String) -> Unit, vm: SecondaryViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SecondaryHeader(selected ?: stringResource(R.string.drawer_providers), if (selected == null) onBack else ({ selected = null })) }
        if (selected == null) {
            items(state.providers, key = { "provider:${it.name}" }) { provider ->
                Card(Modifier.fillMaxWidth().clickable { selected = provider.name }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(provider.name, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.provider_title_count, provider.titleCount), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        } else {
            val titles = state.catalog.filter { selected in it.providers.split(',') }.mapNotNull { it.secondaryCatalogItem() }.distinctBy { it.stableKey }
            items(titles, key = { "provider-title:${it.stableKey}" }) { item -> CatalogAnimeCard(item, Modifier.fillMaxWidth()) { onAnimeClick(item.id) } }
        }
    }
}

@Composable
fun CurrentSeasonScreen(padding: PaddingValues, onBack: () -> Unit, onAnimeClick: (String) -> Unit, vm: SecondaryViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val titles = state.currentSeasonTitles
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SecondaryHeader(stringResource(R.string.drawer_season), onBack) }
        item { Text(stringResource(R.string.current_season_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text(stringResource(R.string.current_season_count, titles.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
        items(titles, key = { "season:${it.stableKey}" }) { item -> CatalogAnimeCard(item, Modifier.fillMaxWidth()) { onAnimeClick(item.id) } }
    }
}

@Composable
fun DubReleasesScreen(padding: PaddingValues, onBack: () -> Unit, onAnimeClick: (String) -> Unit, vm: SecondaryViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm") }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SecondaryHeader(stringResource(R.string.drawer_dubs), onBack) }
        items(state.dubReleases, key = { "dub:${it.release.sourceReleaseId}" }) { item ->
            Card(Modifier.fillMaxWidth().clickable { onAnimeClick(item.release.animeId) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.season_episode, item.release.seasonNumber ?: 1, item.release.episodeNumber ?: 0))
                    Text("GER DUB", color = MaterialTheme.colorScheme.secondary)
                    item.release.expectedAt?.let { Text(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(formatter)) }
                    item.release.provider?.let { Text(stringResource(R.string.release_provider, it)) }
                }
            }
        }
    }
}

@Composable
fun NewsScreen(padding: PaddingValues, onBack: () -> Unit, onNewsClick: (String) -> Unit, vm: NewsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm") }
    AniSentinelPullToRefresh(state.loading, { vm.refresh(true) }, Modifier.fillMaxSize().padding(padding)) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SecondaryHeader(stringResource(R.string.drawer_news), onBack) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.news_explanation), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.loading && state.items.isEmpty()) item { CircularProgressIndicator() }
        state.errorCode?.let { code -> item { Text(stringResource(R.string.news_error, code), color = MaterialTheme.colorScheme.error) } }
        if (!state.loading && state.items.isEmpty() && state.errorCode == null) item { Text(stringResource(R.string.news_empty)) }
        items(state.items, key = { "news:${it.announcementId}" }) { news ->
            Card(
                Modifier.fillMaxWidth().clickable { onNewsClick(news.announcementId) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(newsTypeLabel(news.type), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(news.title, style = MaterialTheme.typography.titleLarge)
                    news.summary?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 4) }
                    news.provider?.let { Text(stringResource(R.string.news_provider, it)) }
                    news.newDate?.let { revised ->
                        val old = news.oldDate?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(formatter) }
                        val next = Instant.ofEpochSecond(revised).atZone(ZoneId.systemDefault()).format(formatter)
                        Text(if (old != null) "$old → $next" else next, color = MaterialTheme.colorScheme.primary)
                    }
                    news.reason?.takeIf { it != news.summary }?.let { Text(it) }
                    Text(Instant.ofEpochSecond(news.publishedAt).atZone(ZoneId.systemDefault()).format(formatter))
                    Text(stringResource(R.string.news_source, news.sources.replace("\n", " · ")), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
    }
}

internal data class NewsSourceLink(val source: String, val url: String)

internal fun newsSourceLinks(news: AnnouncementEntity): List<NewsSourceLink> {
    val sources = news.sources.lines().filter(String::isNotBlank)
    val urls = news.sourceUrls.lines().filter(String::isNotBlank)
    return urls.mapIndexedNotNull { index, url ->
        url.takeIf { it.startsWith("https://") }?.let {
            NewsSourceLink(sources.getOrNull(index) ?: sources.firstOrNull().orEmpty(), it)
        }
    }
}

@Composable
fun NewsDetailScreen(padding: PaddingValues, onBack: () -> Unit, vm: NewsDetailViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm") }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SecondaryHeader(stringResource(R.string.news_detail_title), onBack) }
        when (val resolved = state) {
            NewsDetailState.Loading -> item { CircularProgressIndicator() }
            NewsDetailState.NotFound -> item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.news_not_found), style = MaterialTheme.typography.titleLarge)
                    Button(onClick = onBack) { Text(stringResource(R.string.news_back_to_list)) }
                }
            }
            is NewsDetailState.Found -> {
            val detail = resolved.announcement
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    detail.imageUrl?.takeIf(String::isNotBlank)?.let { NewsRemoteImage(it, detail.title) }
                    Text(newsTypeLabel(detail.type), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(detail.title, style = MaterialTheme.typography.headlineMedium)
                    detail.summary?.takeIf(String::isNotBlank)?.let { Text(it) }
                    Text(stringResource(R.string.news_published, Instant.ofEpochSecond(detail.publishedAt).atZone(ZoneId.systemDefault()).format(formatter)))
                    detail.seasonNumber?.let { Text(stringResource(R.string.news_season, it)) }
                    detail.oldDate?.let { Text(stringResource(R.string.news_old_date, Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(formatter))) }
                    detail.newDate?.let { Text(stringResource(R.string.news_new_date, Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(formatter))) }
                    detail.releaseWindow?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.news_release_window, it)) }
                    detail.reason?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.news_reason, it)) }
                    detail.provider?.takeIf(String::isNotBlank)?.let { Text(stringResource(R.string.news_provider, it)) }
                    Text(stringResource(R.string.news_sources, detail.sources.replace("\n", " · ")), color = MaterialTheme.colorScheme.secondary)
                }
            }
            items(newsSourceLinks(detail), key = { "source:${it.source}:${it.url}" }) { link ->
                Button(onClick = { uriHandler.openUri(link.url) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (link.source == "Anime2You") stringResource(R.string.news_open_original) else stringResource(R.string.news_open_source, link.source))
                }
            }
            }
        }
    }
}

@Composable
private fun NewsRemoteImage(url: String, title: String) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching { CoverImageLoader.load(context, url)?.asImageBitmap() }.getOrNull()
        }
    }
    image?.let {
        Image(it, title, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun newsTypeLabel(type: String): String = stringResource(when (type) {
    "NEW_ANIME" -> R.string.news_type_new_anime
    "NEW_SEASON" -> R.string.news_type_new_season
    "DELAY" -> R.string.news_type_delay
    "NEW_DATE" -> R.string.news_type_new_date
    "SIMULCAST_CONFIRMED" -> R.string.news_type_streaming
    "DUB_CONFIRMED" -> R.string.news_type_dub
    "PRODUCTION_BREAK" -> R.string.news_type_production
    "CONTINUATION_CONFIRMED" -> R.string.news_type_continuation
    else -> R.string.news_type_other
})

@Composable
fun ReleaseStatisticsScreen(padding: PaddingValues, onBack: () -> Unit, vm: SecondaryViewModel = viewModel()) {
    val stats by vm.state.collectAsState()
    var showPostponed by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm") }
    val rows = listOf(
        R.string.stats_today to stats.statistics.today, R.string.stats_week to stats.statistics.thisWeek,
        R.string.stats_sub to stats.statistics.germanSub, R.string.stats_dub to stats.statistics.germanDub,
        R.string.stats_available to stats.statistics.confirmedAvailable, R.string.stats_delayed to stats.statistics.delayed,
        R.string.stats_postponed to stats.statistics.postponed
    )
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SecondaryHeader(if (showPostponed) stringResource(R.string.stats_postponed) else stringResource(R.string.drawer_stats), if (showPostponed) ({ showPostponed = false }) else onBack) }
        if (showPostponed) {
            items(stats.scheduleChanges, key = { "change:${it.historyId}" }) { change ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(change.titleGerman, style = MaterialTheme.typography.titleMedium)
                        Text("S${change.seasonNumber ?: 1} · E${change.episodeNumber ?: 0} · ${change.releaseLanguage ?: "–"}")
                        Text("${change.previousAt?.let { Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).format(formatter) } ?: "–"} → ${Instant.ofEpochSecond(change.revisedAt).atZone(ZoneId.systemDefault()).format(formatter)}")
                        Text(stringResource(R.string.schedule_change_reason_label, change.reason ?: stringResource(R.string.metadata_unavailable)))
                        Text(stringResource(R.string.schedule_change_source_label, "AniWorld"), color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        } else {
        items(rows, key = { "stat:${it.first}" }) { row ->
            Card(Modifier.fillMaxWidth().then(if (row.first == R.string.stats_postponed) Modifier.clickable { showPostponed = true } else Modifier), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(row.first)); Text(row.second.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        }
    }
}

@Composable
fun ChangelogScreen(padding: PaddingValues, onBack: () -> Unit) {
    val releases = listOf(
        stringResource(R.string.changelog_v24_title) to stringResource(R.string.changelog_v24_body),
        stringResource(R.string.changelog_v23_title) to stringResource(R.string.changelog_v23_body),
        stringResource(R.string.changelog_v22_title) to stringResource(R.string.changelog_v22_body),
        stringResource(R.string.changelog_v21_title) to stringResource(R.string.changelog_v21_body),
        stringResource(R.string.changelog_v20_title) to stringResource(R.string.changelog_v20_body),
        stringResource(R.string.changelog_v19_title) to stringResource(R.string.changelog_v19_body),
        stringResource(R.string.changelog_v18_title) to stringResource(R.string.changelog_v18_body),
        stringResource(R.string.changelog_v17_title) to stringResource(R.string.changelog_v17_body),
        stringResource(R.string.changelog_v16_title) to stringResource(R.string.changelog_v16_body),
        stringResource(R.string.changelog_v15_title) to stringResource(R.string.changelog_v15_body),
        stringResource(R.string.changelog_v14_title) to stringResource(R.string.changelog_v14_body),
        stringResource(R.string.changelog_v13_title) to stringResource(R.string.changelog_v13_body)
    )
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SecondaryHeader(stringResource(R.string.drawer_changelog), onBack) }
        items(releases, key = { "changelog:${it.first}" }) { release ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(release.first, style = MaterialTheme.typography.titleLarge); Text(release.second) }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
