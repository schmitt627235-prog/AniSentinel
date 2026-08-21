package de.anisentinel.app.data.provider

import android.util.Log
import de.anisentinel.app.domain.provider.JustWatchOffer
import de.anisentinel.app.domain.provider.JustWatchPartnerSource
import de.anisentinel.app.domain.provider.JustWatchCatalogSource
import de.anisentinel.app.domain.provider.JustWatchCatalogResult
import de.anisentinel.app.domain.provider.JustWatchCatalogTitle
import de.anisentinel.app.domain.provider.JustWatchGenre
import de.anisentinel.app.domain.provider.JustWatchSourceResult
import de.anisentinel.app.domain.provider.JustWatchTitleMatch
import de.anisentinel.app.domain.provider.MatchConfidence
import de.anisentinel.app.domain.provider.MonetizationType
import de.anisentinel.app.domain.provider.ProviderMarketPolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Debug-only real GraphQL probe. This class is absent from release APKs. */
class UnofficialJustWatchDiagnosticSource : JustWatchPartnerSource, JustWatchCatalogSource {
    private var lastRequestAtMillis: Long = 0
    override suspend fun lookup(title: String, year: Int?, contentType: String, seasonNumber: Int?, episodeNumber: Int): JustWatchSourceResult =
        withContext(Dispatchers.IO) {
            try {
                val search = post(searchBody(title, "de"))
                var nodes = search.optJSONObject("data")?.optJSONObject("popularTitles")
                    ?.optJSONArray("edges").objects().mapNotNull { it.optJSONObject("node") }
                val localizedCandidates = nodes.mapNotNull { it.toMatch() }
                var candidates = localizedCandidates
                var decision = JustWatchTitleMatcher.decide(title, year, contentType, candidates)
                if (decision !is TitleMatchDecision.Unique) {
                    val english = post(searchBody(title, "en"))
                    val englishNodes = english.optJSONObject("data")?.optJSONObject("popularTitles")
                        ?.optJSONArray("edges").objects().mapNotNull { it.optJSONObject("node") }
                    val englishCandidates = englishNodes.mapNotNull { it.toMatch() }
                    nodes = (nodes + englishNodes).distinctBy { it.optString("id") }
                    // Keep localized aliases for matching even when they share one stable JustWatch ID.
                    candidates = (candidates + englishCandidates).distinctBy { "${it.justWatchId}\u0000${it.title}" }
                    decision = JustWatchTitleMatcher.decide(title, year, contentType, candidates)
                    if (decision is TitleMatchDecision.NoMatch) {
                        JustWatchTitleMatcher.stableCrossLocaleTop(
                            title, year, contentType, localizedCandidates, englishCandidates
                        )?.let { decision = TitleMatchDecision.Unique(it) }
                    }
                }
                val simplifiedTitle = simplifySearchTitle(title)
                if (decision is TitleMatchDecision.NoMatch && simplifiedTitle != title) {
                    val simplified = post(searchBody(simplifiedTitle, "en"))
                    val simplifiedNodes = simplified.optJSONObject("data")?.optJSONObject("popularTitles")
                        ?.optJSONArray("edges").objects().mapNotNull { it.optJSONObject("node") }
                    val simplifiedCandidates = simplifiedNodes.mapNotNull { it.toMatch() }
                    nodes = (nodes + simplifiedNodes).distinctBy { it.optString("id") }
                    candidates = (candidates + simplifiedCandidates).distinctBy { "${it.justWatchId}\u0000${it.title}" }
                    // The simplified value is search input only. Matching keeps the original title so
                    // a suffix such as "(2026)" remains a conservative identity signal.
                    decision = JustWatchTitleMatcher.decide(title, year, contentType, candidates)
                }
                if (decision is TitleMatchDecision.Ambiguous) {
                    val candidatesWithGermanOffers = decision.candidates.filter { candidate ->
                        nodes.firstOrNull { it.optString("id") == candidate.justWatchId }
                            ?.optJSONArray("offers")?.length()?.let { it > 0 } == true
                    }
                    if (candidatesWithGermanOffers.size == 1) {
                        decision = TitleMatchDecision.Unique(candidatesWithGermanOffers.single())
                    }
                }
                Log.i(TAG, "search title=${title.take(60)} candidates=${candidates.size} decision=${decision::class.simpleName}")
                if (decision !is TitleMatchDecision.Unique) {
                    Log.i(TAG, "rejections ${JustWatchTitleMatcher.rejectionReasons(title, year, contentType, candidates)}")
                }
                var catalogTitles = nodes.mapIndexedNotNull { index, node -> node.toCatalogTitle(index) }
                if (candidates.isEmpty()) return@withContext JustWatchSourceResult.Success(emptyList(), emptyList(), catalogTitles = catalogTitles)
                if (decision !is TitleMatchDecision.Unique) return@withContext JustWatchSourceResult.Success(candidates, emptyList(), catalogTitles = catalogTitles)
                val selected = decision.match
                catalogTitles = catalogTitles.map { row ->
                    if (row.justWatchId == selected.justWatchId) enrichFromPublicPage(row) else row
                }
                delay(1_200)
                val selectedNode = nodes.single { it.optString("id") == selected.justWatchId }
                val selectedMatches = listOf(selected)
                val showOffers = selectedNode.optJSONArray("offers").objects().mapNotNull {
                    it.toOffer(selected.justWatchId, null, null)
                }
                if (episodeNumber <= 0) {
                    Log.i(TAG, "providers title=${title.take(60)} show=${showOffers.size} titleOnly=true")
                    return@withContext JustWatchSourceResult.Success(
                        selectedMatches, showOffers, catalogTitles = catalogTitles
                    )
                }
                val seasons = post(seasonsBody(selected.justWatchId))
                    .optJSONObject("data")?.optJSONObject("node")?.optJSONArray("seasons").objects()
                val wantedSeason = seasons.firstOrNull {
                    it.optJSONObject("content")?.nullableInt("seasonNumber") == (seasonNumber ?: 1)
                } ?: return@withContext JustWatchSourceResult.Success(selectedMatches, showOffers, seasonFound = false, episodeFound = false, catalogTitles = catalogTitles)
                val seasonOffers = wantedSeason.optJSONArray("offers").objects().mapNotNull {
                    it.toOffer(selected.justWatchId, seasonNumber ?: 1, null)
                }
                Log.i(TAG, "providers title=${title.take(60)} show=${showOffers.size} season=${seasonOffers.size}")
                delay(1_200)
                val episodes = post(episodesBody(wantedSeason.getString("id")))
                    .optJSONObject("data")?.optJSONObject("node")?.optJSONArray("episodes").objects()
                val wantedEpisode = episodes.firstOrNull {
                    it.optJSONObject("content")?.nullableInt("episodeNumber") == episodeNumber
                } ?: return@withContext JustWatchSourceResult.Success(selectedMatches, showOffers + seasonOffers, seasonFound = true, episodeFound = false, catalogTitles = catalogTitles)
                val offers = wantedEpisode.optJSONArray("offers").objects().mapNotNull { offer ->
                    offer.toOffer(selected.justWatchId, seasonNumber, episodeNumber)
                }
                Log.i(TAG, "episode title=${title.take(60)} season=${seasonNumber ?: 1} episode=$episodeNumber offers=${offers.size}")
                JustWatchSourceResult.Success(selectedMatches, showOffers + seasonOffers + offers, seasonFound = true, episodeFound = true, catalogTitles = catalogTitles)
            } catch (e: HttpStatusException) {
                Log.w(TAG, "HTTP ${e.status}: ${e.message}")
                JustWatchSourceResult.Failed("HTTP_${e.status}", e.status == 429 || e.status >= 500)
            } catch (e: Exception) {
                Log.w(TAG, "request failed", e)
                JustWatchSourceResult.Failed("${e::class.simpleName}:${e.message.orEmpty().take(80)}", true)
            }
        }

    override suspend fun title(justWatchId: String): JustWatchCatalogResult = withContext(Dispatchers.IO) {
        try {
            val node = post(body("GetTitleNode", TITLE_QUERY, nodeVariables(justWatchId)))
                .optJSONObject("data")?.optJSONObject("node")
                ?: return@withContext JustWatchCatalogResult.Success()
            val title = node.toCatalogTitle()?.let(::enrichFromPublicPage)
                ?: return@withContext JustWatchCatalogResult.Success()
            JustWatchCatalogResult.Success(titles = listOf(title))
        } catch (e: HttpStatusException) {
            JustWatchCatalogResult.Failed("HTTP_${e.status}", e.status == 429 || e.status >= 500)
        } catch (e: Exception) {
            JustWatchCatalogResult.Failed("${e::class.simpleName}:${e.message.orEmpty().take(80)}", true)
        }
    }

    override suspend fun genres(): JustWatchCatalogResult = withContext(Dispatchers.IO) {
        try {
            val json = post(body("GetGenres", GENRES_QUERY, JSONObject().put("language", "de")))
            val genres = json.optJSONObject("data")?.optJSONArray("genres").objects().mapNotNull { row ->
                val id = row.optString("shortName").takeIf(String::isNotBlank) ?: return@mapNotNull null
                val label = row.optString("translation").takeIf(String::isNotBlank) ?: return@mapNotNull null
                JustWatchGenre(id, label)
            }
            JustWatchCatalogResult.Success(genres = genres)
        } catch (e: HttpStatusException) {
            Log.w(TAG, "genres HTTP ${e.status}: ${e.message}")
            JustWatchCatalogResult.Failed("HTTP_${e.status}", e.status == 429 || e.status >= 500)
        } catch (e: Exception) {
            Log.w(TAG, "genres request failed", e)
            JustWatchCatalogResult.Failed("${e::class.simpleName}:${e.message.orEmpty().take(80)}", true)
        }
    }

    override suspend fun search(
        query: String?,
        genreIds: Set<String>,
        contentTypes: Set<String>,
        offset: Int,
        first: Int,
        sort: String
    ): JustWatchCatalogResult = withContext(Dispatchers.IO) {
        try {
            val filter = JSONObject().apply {
                put("includeTitlesWithoutUrl", true)
                put("objectTypes", JSONArray().apply { contentTypes.forEach(::put) })
                if (!query.isNullOrBlank()) put("searchQuery", query.trim())
                if (genreIds.isNotEmpty()) put("genres", JSONArray().apply { genreIds.forEach(::put) })
            }
            val variables = JSONObject().apply {
                put("first", first.coerceIn(1, 50)); put("offset", offset.coerceAtLeast(0))
                put("country", ProviderMarketPolicy.GERMANY); put("language", "de"); put("filter", filter)
            }
            val json = post(body("GetCatalogTitles", CATALOG_QUERY, variables))
            val titles = json.optJSONObject("data")?.optJSONObject("popularTitles")
                ?.optJSONArray("edges").objects().mapIndexedNotNull { index, edge ->
                    edge.optJSONObject("node")?.toCatalogTitle(offset + index)
                }
            JustWatchCatalogResult.Success(titles = titles)
        } catch (e: HttpStatusException) {
            Log.w(TAG, "catalog HTTP ${e.status}: ${e.message}")
            JustWatchCatalogResult.Failed("HTTP_${e.status}", e.status == 429 || e.status >= 500)
        } catch (e: Exception) {
            Log.w(TAG, "catalog request failed", e)
            JustWatchCatalogResult.Failed("${e::class.simpleName}:${e.message.orEmpty().take(80)}", true)
        }
    }

    @Synchronized
    private fun post(body: JSONObject): JSONObject {
        val waitMillis = (lastRequestAtMillis + MIN_REQUEST_INTERVAL_MS - System.currentTimeMillis()).coerceAtLeast(0)
        if (waitMillis > 0) Thread.sleep(waitMillis)
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 25_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "AniSentinel/0.10.0 V9 JustWatch feasibility diagnostic")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                lastRequestAtMillis = System.currentTimeMillis()
                if (status !in 200..299) throw HttpStatusException(status, text.take(160))
                val json = JSONObject(text)
                if (json.has("errors")) throw IllegalStateException("GRAPHQL_ERRORS:${json.getJSONArray("errors").toString().take(160)}")
                return json
            } catch (e: Exception) {
                last = e
                if (e is HttpStatusException && e.status !in listOf(429, 500, 502, 503, 504)) throw e
                Thread.sleep((1L shl attempt) * 1_000L)
            }
        }
        throw last ?: IllegalStateException("UNKNOWN_NETWORK_ERROR")
    }

    private fun JSONObject.toMatch(): JustWatchTitleMatch? {
        val content = optJSONObject("content") ?: return null
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = content.optString("title").takeIf { it.isNotBlank() } ?: return null
        val tmdb = content.optJSONObject("externalIds")?.optString("tmdbId")?.toLongOrNull()
        return JustWatchTitleMatch(id, tmdb, title, null, content.nullableInt("originalReleaseYear"),
            optString("objectType", "SHOW"), MatchConfidence.MEDIUM)
    }

    private fun JSONObject.toOffer(showId: String, season: Int?, episode: Int?): JustWatchOffer? {
        val pkg = optJSONObject("package") ?: return null
        val providerId = pkg.optString("packageId").ifBlank { pkg.optString("id") }.takeIf { it.isNotBlank() } ?: return null
        val type = runCatching { MonetizationType.valueOf(optString("monetizationType", "UNKNOWN")) }.getOrDefault(MonetizationType.UNKNOWN)
        return JustWatchOffer(showId, providerId, pkg.optString("clearName", providerId), season, episode, type,
            optString("presentationType").takeIf { it.isNotBlank() }, optJSONArray("audioLanguages").strings(),
            optJSONArray("subtitleLanguages").strings(), optString("standardWebURL").takeIf { it.isNotBlank() }, Instant.now())
    }

    private fun JSONObject.toCatalogTitle(popularityRank: Int? = null): JustWatchCatalogTitle? {
        val content = optJSONObject("content") ?: return null
        val id = optString("id").takeIf(String::isNotBlank) ?: return null
        val title = content.optString("title").takeIf(String::isNotBlank) ?: return null
        val offers = optJSONArray("offers").objects()
        val providerUrls = offers.mapNotNull { offer ->
            val name = offer.optJSONObject("package")?.optString("clearName")?.takeIf(String::isNotBlank)
            val url = offer.optString("standardWebURL").takeIf(String::isNotBlank)
            if (name != null && url != null) name to url else null
        }.toMap()
        val audio = offers.flatMap { it.optJSONArray("audioLanguages").strings() }.toSet()
        val subtitles = offers.flatMap { it.optJSONArray("subtitleLanguages").strings() }.toSet()
        return JustWatchCatalogTitle(
            justWatchId = id,
            title = title,
            releaseYear = content.nullableInt("originalReleaseYear"),
            contentType = optString("objectType", "SHOW"),
            genres = content.optJSONArray("genres").objects().mapNotNull { it.optString("shortName").takeIf(String::isNotBlank) }.toSet(),
            coverUrl = content.optString("posterUrl").takeIf(String::isNotBlank)?.let(::posterUrl),
            justWatchUrl = content.optString("fullPath").takeIf(String::isNotBlank)?.let { "https://www.justwatch.com$it" },
            providers = providerUrls.keys,
            providerUrls = providerUrls,
            germanSubAvailable = subtitles.any(::isGerman).takeIf { audio.isNotEmpty() || subtitles.isNotEmpty() },
            germanDubAvailable = audio.any(::isGerman).takeIf { audio.isNotEmpty() || subtitles.isNotEmpty() },
            fetchedAt = Instant.now(),
            popularityRank = popularityRank
        )
    }

    private fun enrichFromPublicPage(title: JustWatchCatalogTitle): JustWatchCatalogTitle {
        val url = title.justWatchUrl ?: return title
        return runCatching {
            val metadata = JustWatchPublicMetadataParser.parse(get(url))
            val synopsis = GermanSynopsisResolver.resolve(metadata.description, ::translateToGerman)
            title.copy(
                description = synopsis?.german,
                genres = MetadataTextNormalizer.normalizeGenres(title.genres + metadata.genres).toSet(),
                studios = metadata.studios.mapNotNull(MetadataTextNormalizer::decode).toSet(),
                descriptionOriginal = synopsis?.original ?: MetadataTextNormalizer.decode(metadata.description),
                descriptionOriginalLanguage = synopsis?.originalLanguage ?: MetadataTextNormalizer.detectedLanguage(metadata.description),
                descriptionGermanSource = synopsis?.source
            )
        }.onFailure { Log.w(TAG, "public metadata failed id=${title.justWatchId}", it) }
            .getOrDefault(title)
    }

    /** Translates a real source synopsis; failures return no text instead of exposing English in the German UI. */
    private fun translateToGerman(value: String): String? = runCatching {
        val encoded = URLEncoder.encode(value, Charsets.UTF_8.name())
        val response = get("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=de&dt=t&q=$encoded")
        val rows = JSONArray(response).optJSONArray(0) ?: return@runCatching null
        MetadataTextNormalizer.decode((0 until rows.length()).joinToString("") { index ->
            rows.optJSONArray(index)?.optString(0).orEmpty()
        })
    }.onFailure { Log.w(TAG, "German synopsis translation failed", it) }.getOrNull()

    @Synchronized
    private fun get(url: String): String {
        val waitMillis = (lastRequestAtMillis + MIN_REQUEST_INTERVAL_MS - System.currentTimeMillis()).coerceAtLeast(0)
        if (waitMillis > 0) Thread.sleep(waitMillis)
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 25_000
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9")
        connection.setRequestProperty("User-Agent", "AniSentinel/0.25.1 JustWatch metadata diagnostic")
        val status = connection.responseCode
        val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        lastRequestAtMillis = System.currentTimeMillis()
        if (status !in 200..299) throw HttpStatusException(status, text.take(160))
        return text
    }

    private fun posterUrl(path: String) = "https://images.justwatch.com" + path
        .replace("{profile}", "s166")
        .replace("{format}", "jpg")

    private fun isGerman(code: String) = code.equals("de", true) || code.startsWith("de-", true) || code.startsWith("de_", true)

    private fun searchBody(title: String, language: String) = body("GetSearchTitles", SEARCH_QUERY, JSONObject().apply {
        put("first", 10); put("country", ProviderMarketPolicy.GERMANY); put("language", language)
        put("searchTitlesFilter", JSONObject().apply {
            put("searchQuery", title); put("includeTitlesWithoutUrl", true); put("objectTypes", JSONArray().put("SHOW"))
        })
    })
    private fun seasonsBody(id: String) = body("GetTitleNode", SEASONS_QUERY, nodeVariables(id))
    private fun episodesBody(id: String) = body("GetTitleNode", EPISODES_QUERY, nodeVariables(id))
    private fun nodeVariables(id: String) = JSONObject().apply { put("nodeId", id); put("country", ProviderMarketPolicy.GERMANY); put("language", "de") }
    private fun body(operation: String, query: String, variables: JSONObject) = JSONObject().apply {
        put("operationName", operation); put("variables", variables); put("query", query)
    }
    private fun simplifySearchTitle(value: String) = value
        .replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
        .replace(Regex("\\s+(staffel|season)\\s+\\d+$", RegexOption.IGNORE_CASE), "")
        .trim()
    private fun JSONArray?.objects() = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings() = if (this == null) emptySet() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
    private fun JSONObject.nullableInt(name: String) = if (has(name) && !isNull(name)) optInt(name) else null

    private class HttpStatusException(val status: Int, message: String) : Exception(message)

    companion object {
        private const val TAG = "AniSentinel-JustWatch"
        private const val MIN_REQUEST_INTERVAL_MS = 1_200L
        private const val ENDPOINT = "https://apis.justwatch.com/graphql"
        private const val OFFER = "id monetizationType presentationType standardWebURL subtitleLanguages audioLanguages package { id packageId clearName technicalName shortName }"
        private val DETAILS = """fragment AniSentinelTitle on MovieOrShowOrSeasonOrEpisode { id objectId objectType content(country: ${'$'}country, language: ${'$'}language) { title originalReleaseYear ... on MovieOrShowContent { fullPath posterUrl genres { shortName } externalIds { tmdbId } } ... on SeasonContent { seasonNumber } ... on EpisodeContent { seasonNumber episodeNumber } } ... on Show { totalSeasonCount } ... on Season { totalEpisodeCount } offers(country: ${'$'}country, platform: WEB, filter: {bestOnly: false}) { $OFFER } }"""
        private val SEARCH_QUERY = """query GetSearchTitles(${'$'}searchTitlesFilter: TitleFilter!, ${'$'}country: Country!, ${'$'}language: Language!, ${'$'}first: Int!) { popularTitles(country: ${'$'}country, filter: ${'$'}searchTitlesFilter, first: ${'$'}first, sortBy: POPULAR, offset: 0) { edges { node { ...AniSentinelTitle } } } } $DETAILS"""
        private val SEASONS_QUERY = """query GetTitleNode(${'$'}nodeId: ID!, ${'$'}country: Country!, ${'$'}language: Language!) { node(id: ${'$'}nodeId) { ... on Show { seasons(sortDirection: ASC) { ...AniSentinelTitle } } } } $DETAILS"""
        private val EPISODES_QUERY = """query GetTitleNode(${'$'}nodeId: ID!, ${'$'}country: Country!, ${'$'}language: Language!) { node(id: ${'$'}nodeId) { ... on Season { episodes(sortDirection: ASC) { ...AniSentinelTitle } } } } $DETAILS"""
        private val TITLE_QUERY = """query GetTitleNode(${'$'}nodeId: ID!, ${'$'}country: Country!, ${'$'}language: Language!) { node(id: ${'$'}nodeId) { ...AniSentinelTitle } } $DETAILS"""
        private val GENRES_QUERY = """query GetGenres(${'$'}language: Language!) { genres { shortName translation(language: ${'$'}language) } }"""
        private val CATALOG_QUERY = """query GetCatalogTitles(${'$'}country: Country!, ${'$'}language: Language!, ${'$'}first: Int!, ${'$'}offset: Int!, ${'$'}filter: TitleFilter!) { popularTitles(country: ${'$'}country, filter: ${'$'}filter, first: ${'$'}first, sortBy: POPULAR, offset: ${'$'}offset) { edges { node { id objectType content(country: ${'$'}country, language: ${'$'}language) { title originalReleaseYear fullPath posterUrl genres { shortName } } offers(country: ${'$'}country, platform: WEB, filter: {bestOnly: true}) { standardWebURL subtitleLanguages audioLanguages package { packageId clearName } } } } } }"""
    }
}
