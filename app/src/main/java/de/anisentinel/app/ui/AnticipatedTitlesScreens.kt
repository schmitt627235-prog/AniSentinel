package de.anisentinel.app.ui

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import de.anisentinel.app.R
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.anticipated.*
import de.anisentinel.app.data.image.CoverImageLoader
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AnnouncementEntity
import de.anisentinel.app.data.local.CatalogEntryEntity
import de.anisentinel.app.data.news.Anime2YouTitleNewsResult
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import java.time.ZoneId

data class AnticipatedUiState(
    val loading: Boolean = true,
    val titles: List<AnticipatedTitle> = emptyList(),
    val favoriteAniListIds: Set<Int> = emptySet(),
    val titleNews: Map<Int, List<AnnouncementEntity>> = emptyMap(),
    val newsLoading: Set<Int> = emptySet(),
    val newsFailures: Set<Int> = emptySet(),
    val error: String? = null
)

class AnticipatedTitlesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AnticipatedTitlesRepository(application)
    private val container = (application as AniSentinelApplication).container
    private val dao = container.database.aniSentinelDao()
    private val _state = MutableStateFlow(AnticipatedUiState())
    val state: StateFlow<AnticipatedUiState> = _state
    init {
        viewModelScope.launch {
            container.favoritesRepository.observeFavorites().collectLatest { favorites ->
                _state.value = _state.value.copy(favoriteAniListIds = favorites.mapNotNull { it.anilistId }.toSet())
            }
        }
        refresh()
    }
    fun refresh(force: Boolean = false) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        _state.value = when (val result = repository.load(force)) {
            is AnticipatedLoadResult.Success -> {
                syncFavoriteMetadata(result.titles)
                _state.value.copy(loading = false, titles = result.titles, error = null)
            }
            is AnticipatedLoadResult.Failure -> _state.value.copy(loading = false, error = result.reason)
        }
    }

    fun refreshDetail(aniListId: Int) = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        when (val result = repository.load(force = true)) {
            is AnticipatedLoadResult.Failure -> {
                _state.value = _state.value.copy(loading = false, error = result.reason)
            }
            is AnticipatedLoadResult.Success -> {
                syncFavoriteMetadata(result.titles)
                _state.value = _state.value.copy(loading = false, titles = result.titles, error = null)
            }
        }
        _state.value.titles.firstOrNull { it.identity.aniListId == aniListId }?.let { refreshNews(it, force = true) }
    }

    fun loadNews(title: AnticipatedTitle) = viewModelScope.launch { refreshNews(title, force = true) }

    fun toggleFavorite(title: AnticipatedTitle) = viewModelScope.launch {
        val enabled = title.identity.aniListId !in _state.value.favoriteAniListIds
        val anime = title.toAnimeEntity()
        if (enabled) {
            dao.upsertAnime(listOf(anime))
        }
        container.favoritesRepository.setFavoriteEnabled(anime.id, enabled, "BOTH", "standard")
        // Rebuild the complete mapping so every future favorite receives its own stable
        // position. Using position 0 for every title violated the catalog's unique index
        // and caused all but the first seasonal favorite to be dropped.
        syncFavoriteMetadata(_state.value.titles)
    }

    private suspend fun syncFavoriteMetadata(titles: List<AnticipatedTitle>) {
        val activeIds = dao.activeFavorites().mapTo(mutableSetOf()) { it.animeId }
        val indexedUpdates = titles.mapIndexedNotNull { position, title ->
            title.takeIf { "anilist:${it.identity.aniListId}" in activeIds }
                ?.let { position to it.toAnimeEntity() }
        }
        val updates = indexedUpdates.map { it.second }
        if (updates.isNotEmpty()) dao.upsertAnime(updates)
        val fetchedAt = java.time.Instant.now().epochSecond
        dao.replaceCatalogEntries(
            UPCOMING_FAVORITES,
            indexedUpdates.map { (position, anime) ->
                CatalogEntryEntity(UPCOMING_FAVORITES, anime.id, position, fetchedAt)
            }
        )
    }

    private suspend fun refreshNews(title: AnticipatedTitle, force: Boolean) {
        val id = title.identity.aniListId
        _state.value = _state.value.copy(newsLoading = _state.value.newsLoading + id)
        // Persist the AniList identity first. JustWatch is enrichment only and must never
        // create a second anime record for the same upcoming title.
        dao.upsertAnime(listOf(title.toAnimeEntity()))
        val enrichment = container.justWatchCatalogRepository.enrichUpcoming(
            animeId = "anilist:$id",
            aliases = title.identity.titles,
            year = title.startDate?.year ?: title.seasonYear,
            format = title.format,
            seasonNumber = title.identity.seasonNumber
        )
        val enriched = if (enrichment == null) title else title.copy(
            germanTitle = enrichment.germanTitle,
            identity = title.identity.copy(titles = title.identity.titles + enrichment.germanTitle),
            justWatchProviders = enrichment.providers,
            justWatchUrl = enrichment.justWatchUrl
        )
        _state.value = _state.value.copy(titles = _state.value.titles.map { if (it.identity.aniListId == id) enriched else it })
        when (val result = container.newsRepository.searchForTitle(enriched.identity.titles, force = force)) {
            is Anime2YouTitleNewsResult.Success -> _state.value = _state.value.copy(
                titleNews = _state.value.titleNews + (id to result.items),
                newsFailures = _state.value.newsFailures - id,
                newsLoading = _state.value.newsLoading - id
            )
            is Anime2YouTitleNewsResult.Failure -> _state.value = _state.value.copy(
                newsFailures = _state.value.newsFailures + id,
                newsLoading = _state.value.newsLoading - id
            )
        }
    }

    companion object { const val UPCOMING_FAVORITES = "UPCOMING_FAVORITES" }
}

private fun AnticipatedTitle.toAnimeEntity(): AnimeEntity {
    val now = java.time.Instant.now().epochSecond
    return AnimeEntity(
        id = "anilist:${identity.aniListId}", anilistId = identity.aniListId, anisearchId = null,
        // AnimeEntity predates nullable localized titles. An empty value means that no
        // verified German title is known; English/Romaji remain in their own fields.
        titleGerman = germanTitle.orEmpty(), titleEnglish = englishTitle, titleRomaji = title,
        titleNative = nativeTitle, description = description.orEmpty(), coverUrl = coverUrl, bannerUrl = null,
        season = season, seasonYear = seasonYear, totalEpisodes = episodes, updatedAt = now,
        nextAiringAt = startDate?.atStartOfDay(ZoneId.systemDefault())?.toEpochSecond(),
        nextEpisode = nextAiringEpisode, sourceUpdatedAt = sourceObservedAt, cachedAt = now
    )
}

@Composable
fun AnticipatedTitlesScreen(padding: PaddingValues, onMenu: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: AnticipatedTitlesViewModel = viewModel(); val state by vm.state.collectAsState()
    var filter by remember { mutableStateOf("Alle") }
    val groups = remember(state.titles) { listOf("Alle") + state.titles.map(::futurePeriod).distinct() }
    val visible = if (filter == "Alle") state.titles else state.titles.filter { futurePeriod(it) == filter }
    val listState = rememberLazyListState()
    AniSentinelPullToRefresh(state.loading, { vm.refresh(force = true) }, Modifier.fillMaxSize().padding(padding)) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onMenu) { Icon(Icons.Outlined.Menu, stringResource(R.string.open_menu)) }
            Text(stringResource(R.string.anticipated_titles), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp).weight(1f))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { label ->
                val count = if (label == "Alle") state.titles.size else state.titles.count { futurePeriod(it) == label }
                FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text("$label ($count)") })
            }
        }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(stringResource(R.string.anticipated_refresh_failed), color = MaterialTheme.colorScheme.error) }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(visible, key = { it.identity.aniListId }) { title ->
                val rank = visible.indexOfFirst { it.identity.aniListId == title.identity.aniListId } + 1
                Card(Modifier.fillMaxWidth().clickable { onOpen(title.identity.aniListId) }) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AnticipatedCover(title.coverUrl, title.title, Modifier.width(105.dp).height(150.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("#$rank", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                            Text(title.germanTitle ?: title.englishTitle ?: title.title, style = MaterialTheme.typography.titleMedium)
                            Text("🔥 ${formatPopularity(title.popularity)} ${stringResource(R.string.anticipated_users)}")
                            Text("${stringResource(R.string.anticipated_start)}: ${futureStart(title)}")
                            IconButton(onClick = { vm.toggleFavorite(title) }) {
                                Icon(
                                    if (title.identity.aniListId in state.favoriteAniListIds) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (title.identity.aniListId in state.favoriteAniListIds) "Entfavorisieren" else "Favorisieren"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
fun AnticipatedTitleDetailScreen(padding: PaddingValues, aniListId: Int, onBack: () -> Unit) {
    val vm: AnticipatedTitlesViewModel = viewModel(); val state by vm.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val title = state.titles.firstOrNull { it.identity.aniListId == aniListId }
    LaunchedEffect(title?.identity?.aniListId) { title?.let(vm::loadNews) }
    AniSentinelPullToRefresh(
        refreshing = state.loading,
        onRefresh = { vm.refreshDetail(aniListId) },
        modifier = Modifier.fillMaxSize().padding(padding)
    ) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, stringResource(R.string.settings_back)) }; Text(stringResource(R.string.anticipated_details), style = MaterialTheme.typography.headlineMedium) }
        if (title == null) { if (state.loading) CircularProgressIndicator() else Text(stringResource(R.string.anticipated_not_found)); return@Column }
        AnticipatedCover(title.coverUrl, title.title, Modifier.fillMaxWidth().height(360.dp))
        Text(title.germanTitle ?: title.englishTitle ?: title.title, style = MaterialTheme.typography.headlineSmall)
        title.nativeTitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("🔥 ${formatPopularity(title.popularity)} ${stringResource(R.string.anticipated_users)}")
        InfoCard(stringResource(R.string.anticipated_start), futureStart(title))
        InfoCard(stringResource(R.string.anticipated_format), title.format ?: stringResource(R.string.anticipated_unknown))
        InfoCard(stringResource(R.string.anticipated_studio), title.studio ?: stringResource(R.string.anticipated_unknown))
        title.sequelOfTitle?.let { InfoCard(stringResource(R.string.anticipated_sequel_of), it) }
        Button(onClick = { vm.toggleFavorite(title) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (aniListId in state.favoriteAniListIds) "Favorisiert" else "Favorisieren")
        }
        InfoCard("Verfügbarkeit", buildString {
            if (title.justWatchProviders.isEmpty()) append("Streaminganbieter derzeit noch nicht bestätigt")
            else append("Streaming DACH: ${title.justWatchProviders.sorted().joinToString(" · ")}")
            append("\nMonitoring: ${if (aniListId in state.favoriteAniListIds) "Aktiv" else "Inaktiv"}")
        })
        title.description?.let { InfoCard(stringResource(R.string.synopsis), it.replace(Regex("<[^>]+>"), "")) }
        Text("Aktuelle News", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val news = state.titleNews[aniListId].orEmpty()
        if (aniListId in state.newsLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                Text("Release-News werden automatisch aktualisiert.")
            }
        } else if (aniListId in state.newsFailures) {
            Text("News konnten derzeit nicht geladen werden.", color = MaterialTheme.colorScheme.error)
        } else if (news.isEmpty()) {
            Text("Derzeit keine release-relevanten News zu diesem Titel gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else news.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(releaseNewsLabels(item.type).joinToString(" · "), color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    Text("Anime2You · ${java.time.Instant.ofEpochSecond(item.publishedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    item.summary?.take(240)?.let { Text(it) }
                    val url = item.sourceUrls.lines().firstOrNull { it.startsWith("https://www.anime2you.de/") }
                    if (url != null) TextButton(onClick = { uriHandler.openUri(url) }) { Text("Artikel öffnen") }
                }
            }
        }
    }
    }
}

private fun releaseNewsLabels(value: String): List<String> = value.split('+').mapNotNull {
    when (it) {
        "RELEASE_DATE" -> "Starttermin"
        "POSTPONEMENT" -> "Verschoben"
        "STREAMING_PROVIDER" -> "Streaminganbieter"
        "DACH_LICENSE" -> "DACH-Lizenz"
        "NO_DACH_STREAMING_LICENSE" -> "Keine DACH-Streaminglizenz"
        "PHYSICAL_RELEASE_ONLY" -> "DVD / Blu-ray"
        "TRAILER_TEASER" -> "Trailer / Teaser"
        else -> null
    }
}

@Composable private fun InfoCard(label: String, value: String) { Card { Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(label, fontWeight = FontWeight.Bold); Text(value) } } }

@Composable
private fun AnticipatedCover(url: String?, title: String, modifier: Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            url?.let { runCatching { CoverImageLoader.load(context, it)?.asImageBitmap() }.getOrNull() }
        }
    }
    if (image != null) {
        Image(requireNotNull(image), title, modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(title.take(2).uppercase(), fontWeight = FontWeight.Bold)
        }
    }
}
private fun formatPopularity(value: Int) = NumberFormat.getIntegerInstance(java.util.Locale.GERMANY).format(value)
private fun futureStart(title: AnticipatedTitle) = title.startDate?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: futurePeriod(title)
private fun futurePeriod(title: AnticipatedTitle): String = when (title.season) { "FALL" -> "Herbst ${title.seasonYear ?: ""}"; "WINTER" -> "Winter ${title.seasonYear ?: ""}"; "SPRING" -> "Frühling ${title.seasonYear ?: ""}"; "SUMMER" -> "Sommer ${title.seasonYear ?: ""}"; else -> "Später / TBA" }
