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
import de.anisentinel.app.data.anticipated.*
import de.anisentinel.app.data.image.CoverImageLoader
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AnticipatedUiState(val loading: Boolean = true, val titles: List<AnticipatedTitle> = emptyList(), val error: String? = null)

class AnticipatedTitlesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AnticipatedTitlesRepository(application)
    private val _state = MutableStateFlow(AnticipatedUiState())
    val state: StateFlow<AnticipatedUiState> = _state
    private val enrichmentMutex = Mutex()
    private val attemptedDachIds = mutableSetOf<Int>()
    init { refresh() }
    fun refresh(force: Boolean = false) = viewModelScope.launch {
        if (force) attemptedDachIds.clear()
        _state.value = _state.value.copy(loading = true)
        _state.value = when (val result = repository.load(force)) {
            is AnticipatedLoadResult.Success -> AnticipatedUiState(false, result.titles)
            is AnticipatedLoadResult.Failure -> _state.value.copy(loading = false, error = result.reason)
        }
        if (_state.value.error == null) enrichVisible(_state.value.titles.take(5).map { it.identity.aniListId })
    }
    fun enrichDach(aniListId: Int) = viewModelScope.launch {
        val current = _state.value.titles.firstOrNull { it.identity.aniListId == aniListId } ?: return@launch
        val enriched = repository.enrichDach(current)
        _state.value = _state.value.copy(titles = _state.value.titles.map { if (it.identity.aniListId == aniListId) enriched else it })
    }

    fun refreshDetail(aniListId: Int) = viewModelScope.launch {
        attemptedDachIds.remove(aniListId)
        _state.value = _state.value.copy(loading = true, error = null)
        when (val result = repository.load(force = true)) {
            is AnticipatedLoadResult.Failure -> {
                _state.value = _state.value.copy(loading = false, error = result.reason)
            }
            is AnticipatedLoadResult.Success -> {
                val requested = result.titles.firstOrNull { it.identity.aniListId == aniListId }
                val enriched = requested?.let { repository.enrichDach(it) }
                _state.value = AnticipatedUiState(
                    loading = false,
                    titles = result.titles.map { if (it.identity.aniListId == aniListId && enriched != null) enriched else it }
                )
                attemptedDachIds.add(aniListId)
            }
        }
    }

    /**
     * Resolve DACH evidence lazily for cards the user can actually see. Requests are
     * serialized and deduplicated so scrolling does not turn into an AniSearch crawl.
     */
    fun enrichVisible(aniListIds: List<Int>) = viewModelScope.launch {
        enrichmentMutex.withLock {
            aniListIds.distinct().forEach { id ->
                val current = _state.value.titles.firstOrNull { it.identity.aniListId == id } ?: return@forEach
                if (current.dachLicenseStatus == DachLicenseStatus.CONFIRMED || !attemptedDachIds.add(id)) return@forEach
                val enriched = repository.enrichDach(current)
                _state.value = _state.value.copy(
                    titles = _state.value.titles.map { if (it.identity.aniListId == id) enriched else it }
                )
                if (enriched.dachCheckMessage?.contains("Anfragelimit") == true ||
                    enriched.dachCheckMessage?.contains("blockiert") == true
                ) return@withLock
            }
        }
    }
}

@Composable
fun AnticipatedTitlesScreen(padding: PaddingValues, onMenu: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: AnticipatedTitlesViewModel = viewModel(); val state by vm.state.collectAsState()
    var filter by remember { mutableStateOf("Alle") }
    val groups = remember(state.titles) { listOf("Alle") + state.titles.map(::futurePeriod).distinct() }
    val visible = if (filter == "Alle") state.titles else state.titles.filter { futurePeriod(it) == filter }
    val listState = rememberLazyListState()
    val visibleAniListIds by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                (item.key as? Int)
            }
        }
    }
    LaunchedEffect(visibleAniListIds) {
        if (visibleAniListIds.isNotEmpty()) vm.enrichVisible(visibleAniListIds)
    }
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
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
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
                            Text(AnticipatedDachFormatter.format(title), color = MaterialTheme.colorScheme.secondary)
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
    LaunchedEffect(aniListId, state.titles.isNotEmpty()) {
        if (state.titles.isNotEmpty()) vm.enrichDach(aniListId)
    }
    val title = state.titles.firstOrNull { it.identity.aniListId == aniListId }
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
        InfoCard("DACH", AnticipatedDachFormatter.format(title))
        val aniSearchUrl = title.dachSource
            ?: title.identity.aniSearchId?.let { "https://www.anisearch.de/anime/$it" }
            ?: "https://www.anisearch.de/anime/index"
        Button(
            onClick = { runCatching { uriHandler.openUri(aniSearchUrl) } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.anticipated_open_anisearch))
        }
        title.description?.let { InfoCard(stringResource(R.string.synopsis), it.replace(Regex("<[^>]+>"), "")) }
    }
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
