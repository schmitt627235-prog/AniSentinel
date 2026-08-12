package de.anisentinel.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.anisentinel.app.AniSentinelApplication
import de.anisentinel.app.data.anilist.toDomain
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.JustWatchCatalogTitleEntity
import de.anisentinel.app.data.local.JustWatchGenreEntity
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.MetadataSource
import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.domain.provider.JustWatchCatalogResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.Normalizer
import java.time.Instant

data class CatalogAnimeItem(
    val stableKey: String,
    val id: String,
    val title: String,
    val subtitle: String,
    val providers: List<String>,
    val coverUrl: String?
)

object CatalogTextNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "")
}

object StreamingProviderPolicy {
    fun visible(providers: List<String>): List<String> =
        de.anisentinel.app.domain.provider.StreamingProviderPolicy.visible(providers)
}

enum class DiscoverTypeFilter { ALL, SHOWS, MOVIES, RUNNING, COMPLETED, GER_SUB, GER_DUB }
enum class DiscoverSort { RELEVANCE, POPULARITY, NEWEST, OLDEST, TITLE_ASC, TITLE_DESC }

data class DiscoverUiState(
    val loading: Boolean = true,
    val genres: List<JustWatchGenreEntity> = emptyList(),
    val selectedGenre: String? = null,
    val typeFilter: DiscoverTypeFilter = DiscoverTypeFilter.ALL,
    val sort: DiscoverSort = DiscoverSort.RELEVANCE,
    val providerFilter: String? = null,
    val providers: List<String> = emptyList(),
    val titles: List<CatalogAnimeItem> = emptyList(),
    val error: String? = null
)

class DiscoverViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AniSentinelApplication).container
    private val repository = container.justWatchCatalogRepository
    private val dao = container.database.aniSentinelDao()
    private val selectedGenre = MutableStateFlow<String?>(null)
    private val typeFilter = MutableStateFlow(DiscoverTypeFilter.ALL)
    private val sort = MutableStateFlow(DiscoverSort.RELEVANCE)
    private val providerFilter = MutableStateFlow<String?>(null)
    private val operation = MutableStateFlow(Operation())
    private var refreshJob: Job? = null

    private data class Filters(val genre: String?, val type: DiscoverTypeFilter, val sort: DiscoverSort, val provider: String?)
    private data class Data(val genres: List<JustWatchGenreEntity>, val catalog: List<JustWatchCatalogTitleEntity>, val releases: List<EpisodeReleaseEntity>)
    private data class Operation(val loading: Boolean = true, val error: String? = null)

    private val filters = combine(selectedGenre, typeFilter, sort, providerFilter, ::Filters)
    private val data = combine(
        repository.observeGenres(),
        repository.observeKnownAnimeTitles(),
        dao.observeActiveAniWorldReleases(Instant.now().epochSecond),
        ::Data
    )

    val state = combine(data, filters, operation) { data, filters, op ->
        val releaseLanguages = data.releases.groupBy { it.animeId }.mapValues { (_, rows) -> rows.mapNotNull { it.releaseLanguage }.toSet() }
        val runningAnimeIds = data.releases.mapTo(mutableSetOf()) { it.animeId }
        val filtered = data.catalog.filter { row ->
            (filters.genre == null || filters.genre in row.csvGenres()) &&
                (filters.provider == null || filters.provider in row.csvProviders()) &&
                when (filters.type) {
                    DiscoverTypeFilter.ALL -> true
                    DiscoverTypeFilter.SHOWS -> row.contentType == "SHOW"
                    DiscoverTypeFilter.MOVIES -> row.contentType == "MOVIE"
                    DiscoverTypeFilter.RUNNING -> row.internalAnimeId in runningAnimeIds
                    DiscoverTypeFilter.COMPLETED -> row.contentType == "SHOW" && row.internalAnimeId !in runningAnimeIds
                    DiscoverTypeFilter.GER_SUB -> row.germanSubAvailable == true || releaseLanguages[row.internalAnimeId].orEmpty().contains("GER_SUB")
                    DiscoverTypeFilter.GER_DUB -> row.germanDubAvailable == true || releaseLanguages[row.internalAnimeId].orEmpty().contains("GER_DUB")
                }
        }
        val sorted = when (filters.sort) {
            DiscoverSort.RELEVANCE -> filtered
            DiscoverSort.POPULARITY -> filtered.sortedWith(compareBy(nullsLast()) { it.popularityRank })
            DiscoverSort.NEWEST -> filtered.sortedByDescending { it.releaseYear ?: Int.MIN_VALUE }
            DiscoverSort.OLDEST -> filtered.sortedBy { it.releaseYear ?: Int.MAX_VALUE }
            DiscoverSort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            DiscoverSort.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
        }
        DiscoverUiState(
            loading = op.loading,
            genres = data.genres.filter { genre -> data.catalog.any { genre.genreId in it.csvGenres() } },
            selectedGenre = filters.genre,
            typeFilter = filters.type,
            sort = filters.sort,
            providerFilter = filters.provider,
            providers = StreamingProviderPolicy.visible(data.catalog.flatMap { it.csvProviders() }),
            titles = sorted.mapNotNull { row -> row.internalAnimeId?.let { row.toCatalogItem(it) } }
                .distinctBy { it.stableKey },
            error = op.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState())

    init { refresh() }
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
        operation.value = Operation(loading = true)
        val genres = repository.refreshGenres()
        val catalog = repository.search(query = null, genreIds = setOf("ani"))
        operation.value = Operation(
            false,
            (genres as? JustWatchCatalogResult.Failed)?.code
                ?: (catalog as? JustWatchCatalogResult.Failed)?.code
        )
        }
    }
    fun selectGenre(id: String?) { selectedGenre.value = id }
    fun selectType(value: DiscoverTypeFilter) { typeFilter.value = value }
    fun selectSort(value: DiscoverSort) { sort.value = value }
    fun selectProvider(value: String?) { providerFilter.value = value }
}

data class GlobalSearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<CatalogAnimeItem> = emptyList(),
    val error: String? = null,
    val searched: Boolean = false
)

class GlobalSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as AniSentinelApplication).container
    private val repository = container.justWatchCatalogRepository
    private val dao = container.database.aniSentinelDao()
    private val query = MutableStateFlow("")
    private val operation = MutableStateFlow(SearchOperation())
    private var searchJob: Job? = null
    private data class SearchOperation(
        val loading: Boolean = false,
        val error: String? = null,
        val searched: Boolean = false,
        val resultIds: Set<String> = emptySet()
    )

    val state = combine(query, repository.observeAllCachedTitles(), operation) { text, catalog, op ->
        val matches = if (!op.searched) emptyList() else catalog
            .filter { it.justWatchId in op.resultIds }
            .sortedWith(compareBy(nullsLast()) { it.popularityRank })
        GlobalSearchUiState(text, op.loading, matches.mapNotNull { row -> row.internalAnimeId?.let { row.toCatalogItem(it) } }, op.error, op.searched)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalSearchUiState())

    fun setQuery(value: String) { query.value = value }
    fun search() {
        val value = query.value.trim()
        if (value.length < 2) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
        operation.value = SearchOperation(loading = true, searched = true)
        val result = repository.search(value)
        operation.value = SearchOperation(
            loading = false,
            error = (result as? JustWatchCatalogResult.Failed)?.code,
            searched = true,
            resultIds = (result as? JustWatchCatalogResult.Success)?.titles.orEmpty().map { it.justWatchId }.toSet()
        )
        }
    }
}

private fun JustWatchCatalogTitleEntity.csvGenres() = genres.split(',').filter(String::isNotBlank).toSet()
private fun JustWatchCatalogTitleEntity.csvProviders() = providers.split(',').filter(String::isNotBlank)
private fun JustWatchCatalogTitleEntity.toCatalogItem(id: String) = CatalogAnimeItem(
    stableKey = justWatchId,
    id = id,
    title = title,
    subtitle = listOfNotNull(if (contentType == "MOVIE") "Film" else "Serie", releaseYear?.toString()).joinToString(" · "),
    providers = StreamingProviderPolicy.visible(csvProviders()),
    coverUrl = coverUrl
)
