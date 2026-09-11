package de.anisentinel.app.data.anticipated

import android.content.Context
import android.util.Log
import de.anisentinel.app.data.anilist.AniListGraphQlHttpClient
import de.anisentinel.app.data.anilist.GraphQlHttpResult
import de.anisentinel.app.data.anisearch.AniSearchFetchResult
import de.anisentinel.app.data.anisearch.AniSearchHtmlParser
import de.anisentinel.app.data.anisearch.AniSearchHttpTransport
import de.anisentinel.app.data.anisearch.AniSearchParseResult
import de.anisentinel.app.data.settings.SourceCooldownStore
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import org.json.JSONObject
import org.json.JSONArray

enum class DachLicenseStatus { UNKNOWN, NOT_LICENSED_YET, CONFIRMED }

data class AnticipatedDachEvidence(
    val provider: String?,
    val availableFrom: LocalDate?,
    val source: String,
    val observedAt: Long,
    val priority: Int
)

data class UpcomingAnimeIdentity(
    val canonicalId: String,
    val aniListId: Int,
    val malId: Int?,
    val aniSearchId: String? = null,
    val titles: Set<String>,
    val sequelOf: Int?,
    val seasonNumber: Int?
)

data class AnticipatedTitle(
    val identity: UpcomingAnimeIdentity,
    val title: String,
    val englishTitle: String?,
    val nativeTitle: String?,
    val germanTitle: String? = null,
    val coverUrl: String?,
    val description: String?,
    val season: String?,
    val seasonYear: Int?,
    val startDate: LocalDate?,
    val status: String,
    val popularity: Int,
    val trending: Int,
    val studio: String?,
    val format: String?,
    val sequelOfTitle: String? = null,
    val dachLicenseStatus: DachLicenseStatus = DachLicenseStatus.UNKNOWN,
    val dachProvider: String? = null,
    val dachAvailableFrom: LocalDate? = null,
    val dachAvailablePeriod: String? = null,
    val dachSource: String? = null,
    val dachCheckMessage: String? = null,
    val dachSourcePriority: Int = Int.MIN_VALUE,
    val sourceObservedAt: Long,
    val firstDetectedAt: Long,
    val updatedAt: Long,
    val favourites: Int = 0,
    val episodes: Int? = null,
    val nextAiringEpisode: Int? = null,
    val nextAiringAt: Long? = null
)

sealed interface AnticipatedLoadResult {
    data class Success(val titles: List<AnticipatedTitle>, val fromCache: Boolean) : AnticipatedLoadResult
    data class Failure(val reason: String) : AnticipatedLoadResult
}

object AnticipatedRanking {
    fun rank(items: Iterable<AnticipatedTitle>, today: LocalDate): List<AnticipatedTitle> = items
        .filter { it.status == "NOT_YET_RELEASED" && (it.startDate == null || it.startDate.isAfter(today)) }
        .distinctBy { it.identity.canonicalId }
        .sortedWith(compareByDescending<AnticipatedTitle> { it.popularity }.thenBy { it.identity.aniListId })
}

object UpcomingIdentityResolver {
    fun canonicalTitle(value: String): String = value.lowercase()
        .replace(Regex("第\\s*(\\d+)\\s*期"), " season $1 ")
        .replace(Regex("dai\\s*(\\d+)\\s*ki"), " season $1 ")
        .replace(Regex("(\\d+)(st|nd|rd|th)\\s+season"), " season $1 ")
        .replace(Regex("season\\s*(\\d+)"), " season $1 ")
        .replace(Regex("staffel\\s*(\\d+)"), " season $1 ")
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), "-")

    fun sameFutureTitle(first: Iterable<String>, second: Iterable<String>): Boolean {
        val a = first.map(::canonicalTitle).filter(String::isNotBlank).toSet()
        return second.map(::canonicalTitle).any { candidate ->
            candidate in a || a.any { base ->
                candidate == "$base-cour-1" || candidate == "$base-part-1" ||
                    base == "$candidate-cour-1" || base == "$candidate-part-1"
            }
        }
    }
}

data class AniSearchMatchCandidate(
    val hit: de.anisentinel.app.data.anisearch.AniSearchSearchHit,
    val confidence: Int
)

/** Conservative title/season/cour matcher. UNKNOWN is preferred over a plausible wrong season. */
object AniSearchFutureMatcher {
    fun searchVariants(title: AnticipatedTitle): List<String> = buildSet {
        listOfNotNull(title.englishTitle, title.title, title.nativeTitle).forEach { add(it.trim()) }
        title.identity.titles.forEach { add(it.trim()) }
        toList().forEach { value ->
            add(value.replace(Regex("(?i)(\\d+)(st|nd|rd|th) season"), "Season $1"))
            add(value.replace(Regex("(?i)season\\s*(\\d+)"), "Dai $1 Ki"))
            add(value.replace(Regex("(?i)\\bpart\\s*(\\d+)"), "Cour $1"))
            add(value.replace(Regex("(?i)\\bcour\\s*(\\d+)"), "Part $1"))
        }
    }.filter { it.length >= 2 && !it.equals("null", true) }.distinct()

    fun select(title: AnticipatedTitle, hits: Collection<de.anisentinel.app.data.anisearch.AniSearchSearchHit>): AniSearchMatchCandidate? {
        val scored = hits.distinctBy { it.anisearchId }.mapNotNull { hit ->
            score(title, hit.title)?.let { AniSearchMatchCandidate(hit, it) }
        }.sortedByDescending { it.confidence }
        val best = scored.firstOrNull() ?: return null
        if (best.confidence < 85) return null
        if (scored.getOrNull(1)?.let { best.confidence - it.confidence < 8 } == true) return null
        return best
    }

    /** Detail pages may use a translated title, but explicit installment metadata must agree. */
    fun hasInstallmentConflict(title: AnticipatedTitle, detailTitles: Iterable<String>): Boolean {
        val expectedSeason = title.identity.seasonNumber ?: installment(title.identity.titles)
        val actualSeason = installment(detailTitles)
        if (expectedSeason != null && actualSeason != null && expectedSeason != actualSeason) return true
        val expectedPart = part(title.identity.titles)
        val actualPart = part(detailTitles)
        return expectedPart != null && actualPart != null && expectedPart != actualPart
    }

    private fun score(title: AnticipatedTitle, candidate: String): Int? {
        val expectedSeason = title.identity.seasonNumber ?: installment(title.identity.titles)
        val candidateSeason = installment(listOf(candidate))
        if (expectedSeason != null && candidateSeason != null && expectedSeason != candidateSeason) return null
        val expectedPart = part(title.identity.titles)
        val candidatePart = part(listOf(candidate))
        if (expectedPart != null && candidatePart != null && expectedPart != candidatePart) return null
        if (UpcomingIdentityResolver.sameFutureTitle(title.identity.titles, listOf(candidate))) return 100
        val candidateTokens = baseTokens(candidate)
        val similarities = title.identity.titles.map { source ->
            val sourceTokens = baseTokens(source)
            if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) 0.0
            else sourceTokens.intersect(candidateTokens).size.toDouble() / sourceTokens.union(candidateTokens).size
        }
        val similarity = similarities.maxOrNull() ?: 0.0
        return (similarity * 90).toInt() + if (expectedSeason != null && expectedSeason == candidateSeason) 10 else 0
    }

    private fun installment(values: Iterable<String>): Int? = values.firstNotNullOfOrNull { value ->
        listOf(
            Regex("(?i)(?:season|staffel|dai)\\s*(\\d+)"),
            Regex("(?i)(\\d+)(?:st|nd|rd|th)\\s+season"),
            Regex("第\\s*(\\d+)\\s*期")
        ).firstNotNullOfOrNull { it.find(value)?.groupValues?.get(1)?.toIntOrNull() }
    }

    private fun part(values: Iterable<String>): Int? = values.firstNotNullOfOrNull { value ->
        Regex("(?i)(?:part|cour)\\s*(\\d+)|(\\d+)(?:st|nd|rd|th)\\s+cour").find(value)?.groupValues
            ?.drop(1)?.firstNotNullOfOrNull(String::toIntOrNull)
    }

    private fun baseTokens(value: String): Set<String> = UpcomingIdentityResolver.canonicalTitle(value)
        .split('-').filter { it.isNotBlank() && it !in setOf("season", "staffel", "part", "cour") && it.toIntOrNull() == null }.toSet()
}

object AnticipatedDachFormatter {
    fun format(title: AnticipatedTitle): String = when (title.dachLicenseStatus) {
        DachLicenseStatus.UNKNOWN -> buildString {
            append("DACH-Status derzeit nicht ermittelbar")
            title.dachCheckMessage?.takeUnless { it.startsWith("DACH-Status derzeit nicht ermittelbar") }
                ?.let { append(" · $it") }
        }
        DachLicenseStatus.NOT_LICENSED_YET -> "Noch nicht in der DACH-Region lizenziert"
        DachLicenseStatus.CONFIRMED -> buildString {
        append("DACH")
        title.dachAvailableFrom?.let { append(" · ab ${it.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}") }
            ?: title.dachAvailablePeriod?.let { append(" · ab $it") }
        title.dachProvider?.let { append(" · Anbieter: $it") }
        append(" · Bestätigt")
        }
    }
}

object AnticipatedDachResolver {
    /** A confirmed newer/stronger source replaces the previous visible negative state. */
    fun apply(title: AnticipatedTitle, evidence: AnticipatedDachEvidence): AnticipatedTitle {
        if (title.dachLicenseStatus == DachLicenseStatus.CONFIRMED && evidence.priority < title.dachSourcePriority) return title
        return title.copy(
            dachLicenseStatus = DachLicenseStatus.CONFIRMED,
            dachProvider = evidence.provider,
            dachAvailableFrom = evidence.availableFrom,
            dachSource = evidence.source,
            dachSourcePriority = evidence.priority,
            sourceObservedAt = evidence.observedAt,
            firstDetectedAt = if (title.dachLicenseStatus == DachLicenseStatus.CONFIRMED) title.firstDetectedAt else evidence.observedAt,
            updatedAt = evidence.observedAt
        )
    }
}

class AnticipatedTitlesRepository(
    private val context: Context,
    private val client: AniListGraphQlHttpClient = AniListGraphQlHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val aniSearch: AniSearchHttpTransport = AniSearchHttpTransport(
        context,
        cooldownStore = SourceCooldownStore(context.applicationContext)
    ),
    private val aniListRequest: (suspend (String, String) -> GraphQlHttpResult)? = null
) {
    private val cache = context.getSharedPreferences("anticipated_titles_cache", Context.MODE_PRIVATE)
    suspend fun load(force: Boolean = false): AnticipatedLoadResult {
        val now = clock.instant().epochSecond
        val cached = cache.getString("json_v3", null) ?: bundledFallback()
        val cachedAt = cache.getLong("stored_at_v3", 0)
        if (!force && cached != null && now - cachedAt < 86_400) return parse(cached, cachedAt, true)
        return when (val response = fetchAllPages()) {
            is GraphQlHttpResult.Success -> {
                val parsed = parse(response.body, now, false)
                if (parsed is AnticipatedLoadResult.Success) cache.edit().putString("json_v3", response.body).putLong("stored_at_v3", now).apply()
                parsed
            }
            else -> cached?.let { parse(it, cachedAt, true) } ?: AnticipatedLoadResult.Failure("ANILIST_UNAVAILABLE")
        }
    }

    private fun bundledFallback(): String? = runCatching {
        context.assets.open("anticipated_titles_fallback.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            .takeIf(String::isNotBlank)
    }.getOrNull()

    internal suspend fun fetchAllPages(): GraphQlHttpResult {
        val combined = JSONArray()
        for (page in 1..20) {
            val body = requestBody(page)
            val response = aniListRequest?.invoke("ANTICIPATED_POPULARITY_PAGE_$page", body)
                ?: client.execute("ANTICIPATED_POPULARITY_PAGE_$page", body)
            if (response !is GraphQlHttpResult.Success) {
                val diagnostic = when (response) {
                    is GraphQlHttpResult.HttpFailure -> "http=${response.statusCode} retryAfter=${response.retryAfterSeconds} body=${response.body.orEmpty().take(500)}"
                    is GraphQlHttpResult.NetworkFailure -> "network=${response.type} message=${response.message.orEmpty().take(500)}"
                    is GraphQlHttpResult.Success -> "http=${response.statusCode}"
                }
                Log.w(TAG, "ANILIST_ANTICIPATED page=$page result=${response::class.simpleName} $diagnostic retained=${combined.length()}")
                return if (combined.length() > 0) combinedResponse(combined, partial = true) else response
            }
            val pageObject = runCatching {
                val root = JSONObject(response.body)
                if (root.has("errors")) error("ANILIST_GRAPHQL_ERROR")
                root.getJSONObject("data").getJSONObject("Page")
            }.getOrElse {
                return if (combined.length() > 0) combinedResponse(combined, partial = true)
                else GraphQlHttpResult.NetworkFailure(de.anisentinel.app.data.anilist.NetworkFailureType.IO, "INVALID_ANTICIPATED_PAGE")
            }
            val pageItems = pageObject.getJSONArray("media")
            for (index in 0 until pageItems.length()) combined.put(pageItems.get(index))
            Log.i(TAG, "ANILIST_ANTICIPATED page=$page http=${response.statusCode} received=${pageItems.length()} total=${combined.length()}")
            if (!pageObject.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false).orFalse()) break
        }
        return combinedResponse(combined, partial = false)
    }

    internal fun requestBody(page: Int): String = JSONObject()
        .put("query", QUERY)
        .put("variables", JSONObject().put("page", page))
        .toString()

    private fun combinedResponse(media: JSONArray, partial: Boolean) = GraphQlHttpResult.Success(
            JSONObject().put("data", JSONObject().put("Page", JSONObject().put("media", media))).toString(),
            200,
            if (partial) mapOf("X-AniSentinel-Partial" to listOf("true")) else emptyMap()
        )

    suspend fun enrichDach(title: AnticipatedTitle): AnticipatedTitle {
        if (title.dachLicenseStatus == DachLicenseStatus.CONFIRMED) return title
        if (title.dachLicenseStatus == DachLicenseStatus.NOT_LICENSED_YET &&
            clock.instant().epochSecond - title.sourceObservedAt < 7 * 86_400
        ) return title
        var hit: de.anisentinel.app.data.anisearch.AniSearchSearchHit? = cachedMatch(title)
        var matchConfidence: Int? = hit?.let { 100 }
        var searchFailure: AniSearchFetchResult? = null
        val collectedHits = linkedMapOf<String, de.anisentinel.app.data.anisearch.AniSearchSearchHit>()
        for (query in if (hit == null) AniSearchFutureMatcher.searchVariants(title) else emptyList()) {
            when (val search = aniSearch.searchAnime(query)) {
                is AniSearchFetchResult.Success -> {
                    AniSearchHtmlParser.parseSearchResults(search.html, search.sourceUrl).forEach { collectedHits[it.anisearchId] = it }
                    AniSearchFutureMatcher.select(title, collectedHits.values)?.let { selected ->
                        hit = selected.hit
                        matchConfidence = selected.confidence
                    }
                }
                else -> {
                    searchFailure = search
                    break
                }
            }
            if (hit != null) break
        }
        if (hit == null) AniSearchFutureMatcher.select(title, collectedHits.values)?.let { hit = it.hit; matchConfidence = it.confidence }
        if (hit == null) return title.copy(
            dachCheckMessage = searchFailure?.toDachMessage() ?: "Kein sicherer AniSearch-Treffer gefunden"
        )
        val detail = aniSearch.fetchDetail(requireNotNull(hit).sourceUrl)
        if (detail !is AniSearchFetchResult.Success) return title.copy(dachCheckMessage = detail.toDachMessage())
        val parsed = AniSearchHtmlParser.parse(detail.html, detail.sourceUrl)
        if (parsed !is AniSearchParseResult.Success) return title.copy(dachCheckMessage = "AniSearch-Seite konnte nicht ausgewertet werden")
        if (!detailCompatible(title, parsed.value)) return title.copy(
            dachCheckMessage = "AniSearch-Treffer widerspricht Jahr oder Format"
        )
        saveMatch(title, requireNotNull(hit), matchConfidence ?: 85)
        val now = clock.instant().epochSecond
        if (!parsed.value.regionalReleaseBlockPresent) return title.copy(
            identity = title.identity.copy(aniSearchId = parsed.value.anisearchId),
            dachCheckMessage = "DACH-Status derzeit nicht ermittelbar: AniSearch-Lizenzblock fehlt"
        )
        if (!parsed.value.dachLicensed) {
            val notLicensed = title.copy(
                identity = title.identity.copy(
                    aniSearchId = parsed.value.anisearchId,
                    titles = title.identity.titles + parsed.value.titleGerman + parsed.value.synonyms
                ),
                germanTitle = parsed.value.titleGerman,
                dachLicenseStatus = DachLicenseStatus.NOT_LICENSED_YET,
                dachProvider = null,
                dachAvailableFrom = null,
                dachAvailablePeriod = null,
                dachSource = detail.sourceUrl,
                dachSourcePriority = 70,
                dachCheckMessage = null,
                sourceObservedAt = now,
                updatedAt = now
            )
            cache.edit().putString("dach_${title.identity.aniListId}", JSONObject().apply {
                put("licensed", false); put("aniSearchId", parsed.value.anisearchId)
                put("germanTitle", parsed.value.titleGerman); put("source", detail.sourceUrl)
                put("observedAt", now); put("firstDetectedAt", now)
            }.toString()).apply()
            return notLicensed
        }
        val updated = AnticipatedDachResolver.apply(
            title.copy(
                identity = title.identity.copy(aniSearchId = parsed.value.anisearchId, titles = title.identity.titles + parsed.value.titleGerman + parsed.value.synonyms),
                germanTitle = parsed.value.titleGerman
            ),
            AnticipatedDachEvidence(parsed.value.dachPublisher, parsed.value.dachAvailableFrom, detail.sourceUrl, now, 70)
        ).copy(dachAvailablePeriod = parsed.value.dachAvailablePeriod, dachCheckMessage = null)
        cache.edit().putString("dach_${title.identity.aniListId}", JSONObject().apply {
            put("licensed", true)
            put("aniSearchId", parsed.value.anisearchId); put("germanTitle", parsed.value.titleGerman)
            parsed.value.dachPublisher?.let { put("provider", it) }
            put("source", detail.sourceUrl); put("observedAt", now)
            put("firstDetectedAt", updated.firstDetectedAt)
            parsed.value.dachAvailableFrom?.let { put("availableFrom", it.toString()) }
            parsed.value.dachAvailablePeriod?.let { put("period", it) }
        }.toString()).apply()
        return updated
    }

    private fun detailCompatible(title: AnticipatedTitle, value: de.anisentinel.app.data.anisearch.AniSearchImport): Boolean {
        val expectedYear = title.startDate?.year ?: title.seasonYear
        if (expectedYear != null && value.releaseYear != null && kotlin.math.abs(expectedYear - value.releaseYear) > 1) return false
        val expectedMovie = title.format?.uppercase() == "MOVIE"
        val actualType = value.mediaType?.lowercase().orEmpty()
        val actualMovie = "movie" in actualType || "film" in actualType
        val actualSeries = "series" in actualType || "serie" in actualType || actualType == "tv"
        if (expectedMovie && actualSeries) return false
        if (!expectedMovie && title.format != null && actualMovie) return false
        if (AniSearchFutureMatcher.hasInstallmentConflict(title, value.synonyms + value.titleGerman)) return false
        return true
    }

    private fun cachedMatch(title: AnticipatedTitle): de.anisentinel.app.data.anisearch.AniSearchSearchHit? {
        title.identity.aniSearchId?.let { id ->
            val source = title.dachSource?.takeIf { "/anime/$id" in it } ?: "https://www.anisearch.de/anime/$id"
            return de.anisentinel.app.data.anisearch.AniSearchSearchHit(id, title.germanTitle ?: title.title, source)
        }
        return cache.getString("match_${title.identity.aniListId}", null)?.let { raw -> runCatching {
            val value = JSONObject(raw)
            de.anisentinel.app.data.anisearch.AniSearchSearchHit(value.getString("id"), value.getString("title"), value.getString("url"))
        }.getOrNull() }
    }

    private fun saveMatch(title: AnticipatedTitle, hit: de.anisentinel.app.data.anisearch.AniSearchSearchHit, confidence: Int) {
        cache.edit().putString("match_${title.identity.aniListId}", JSONObject().apply {
            put("id", hit.anisearchId); put("title", hit.title); put("url", hit.sourceUrl)
            put("confidence", confidence); put("observedAt", clock.instant().epochSecond)
        }.toString()).apply()
    }

    internal fun parse(json: String, observedAt: Long, fromCache: Boolean = false): AnticipatedLoadResult = runCatching {
        val media = JSONObject(json).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        val items = buildList {
            for (index in 0 until media.length()) {
                val item = media.getJSONObject(index); val titles = item.getJSONObject("title")
                val synonyms = item.optJSONArray("synonyms")
                val aliases = buildSet {
                    listOf("romaji", "english", "native").mapNotNull { titles.nullableString(it) }.forEach(::add)
                    if (synonyms != null) for (i in 0 until synonyms.length()) synonyms.optString(i).takeIf { it.isNotBlank() && it != "null" }?.let(::add)
                }
                val romaji = titles.nullableString("romaji") ?: titles.nullableString("english") ?: continue
                val dateJson = item.optJSONObject("startDate")
                val date = dateJson?.let { fuzzyDate(it) }
                val prequel = item.optJSONObject("relations")?.optJSONArray("edges")?.let { edges ->
                    (0 until edges.length()).firstNotNullOfOrNull { i -> edges.getJSONObject(i).takeIf { it.optString("relationType") == "PREQUEL" }?.getJSONObject("node") }
                }
                val seasonNumber = aliases.mapNotNull { Regex("(?i)(?:season|dai)\\s*(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }.firstOrNull()
                val id = item.getInt("id")
                var anticipated = AnticipatedTitle(
                    UpcomingAnimeIdentity("anilist:$id", id, item.optInt("idMal").takeIf { it > 0 }, null, aliases, prequel?.optInt("id")?.takeIf { it > 0 }, seasonNumber),
                    romaji, titles.nullableString("english"), titles.nullableString("native"),
                    coverUrl = item.optJSONObject("coverImage")?.optString("extraLarge")?.takeIf(String::isNotBlank),
                    description = item.optString("description").takeIf(String::isNotBlank), season = item.optString("season").takeIf(String::isNotBlank),
                    seasonYear = item.optInt("seasonYear").takeIf { it > 0 }, startDate = date, status = item.optString("status"),
                    popularity = item.optInt("popularity"), trending = item.optInt("trending"),
                    studio = item.optJSONObject("studios")?.optJSONArray("nodes")?.let { if (it.length() > 0) it.getJSONObject(0).optString("name") else null },
                    format = item.optString("format").takeIf(String::isNotBlank), sequelOfTitle = prequel?.optJSONObject("title")?.optString("romaji"),
                    sourceObservedAt = observedAt, firstDetectedAt = observedAt, updatedAt = observedAt,
                    favourites = item.optInt("favourites"),
                    episodes = item.optInt("episodes").takeIf { it > 0 },
                    nextAiringEpisode = item.optJSONObject("nextAiringEpisode")?.optInt("episode")?.takeIf { it > 0 },
                    nextAiringAt = item.optJSONObject("nextAiringEpisode")?.optLong("airingAt")?.takeIf { it > 0 }
                )
                cache.getString("dach_$id", null)?.let { raw -> runCatching {
                    val saved = JSONObject(raw)
                    val cachedBase = anticipated.copy(
                            identity = anticipated.identity.copy(aniSearchId = saved.optString("aniSearchId").takeIf(String::isNotBlank)),
                            germanTitle = saved.optString("germanTitle").takeIf(String::isNotBlank),
                            dachAvailablePeriod = saved.optString("period").takeIf(String::isNotBlank),
                            dachSource = saved.optString("source").takeIf(String::isNotBlank)
                        )
                    anticipated = if (!saved.optBoolean("licensed", true)) {
                        cachedBase.copy(
                            dachLicenseStatus = DachLicenseStatus.NOT_LICENSED_YET,
                            dachSourcePriority = 70,
                            sourceObservedAt = saved.getLong("observedAt"),
                            firstDetectedAt = saved.optLong("firstDetectedAt", saved.getLong("observedAt")),
                            updatedAt = saved.getLong("observedAt")
                        )
                    } else AnticipatedDachResolver.apply(
                        cachedBase,
                        AnticipatedDachEvidence(
                            saved.optString("provider").takeIf(String::isNotBlank),
                            saved.optString("availableFrom").takeIf(String::isNotBlank)?.let(LocalDate::parse),
                            saved.getString("source"), saved.getLong("observedAt"), 70
                        )
                    ).copy(firstDetectedAt = saved.optLong("firstDetectedAt", saved.getLong("observedAt")))
                } }
                add(anticipated)
            }
        }
        AnticipatedRanking.rank(items, LocalDate.now(clock)).let { ranked ->
            Log.i(TAG, "ANILIST_ANTICIPATED mapped=${items.size} displayed=${ranked.size} fromCache=$fromCache")
            AnticipatedLoadResult.Success(ranked, fromCache)
        }
    }.getOrElse { AnticipatedLoadResult.Failure("INVALID_ANILIST_RESPONSE") }

    private fun fuzzyDate(json: JSONObject): LocalDate? {
        val year=json.optInt("year"); val month=json.optInt("month"); val day=json.optInt("day")
        return runCatching { if (year>0 && month>0 && day>0) LocalDate.of(year, Month.of(month), day) else null }.getOrNull()
    }

    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null
        else optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun Boolean?.orFalse() = this ?: false

    private fun AniSearchFetchResult.toDachMessage(): String = when (this) {
        is AniSearchFetchResult.RateLimited -> "AniSearch-Anfragelimit erreicht – später erneut ziehen"
        is AniSearchFetchResult.AccessBlocked -> "AniSearch blockiert den automatischen Abruf"
        is AniSearchFetchResult.TemporarilyUnavailable -> "AniSearch momentan nicht erreichbar"
        is AniSearchFetchResult.Disabled -> "AniSearch-Abgleich deaktiviert"
        is AniSearchFetchResult.NotFound -> "AniSearch-Seite nicht gefunden"
        is AniSearchFetchResult.InvalidUrl -> "Ungültige AniSearch-Quelle"
        is AniSearchFetchResult.Success -> "DACH-Status noch nicht bestätigt"
    }

    companion object {
        private const val TAG = "AniSentinelAniList"
        private const val QUERY = """
          query AnticipatedTitles(${'$'}page: Int!) { Page(page: ${'$'}page, perPage: 50) { pageInfo { hasNextPage } media(type: ANIME, isAdult: false, status: NOT_YET_RELEASED, sort: POPULARITY_DESC) {
            id idMal status popularity favourites trending format episodes season seasonYear description(asHtml: false)
            title { romaji english native } synonyms coverImage { extraLarge }
            startDate { year month day } studios(isMain: true) { nodes { name } }
            nextAiringEpisode { episode airingAt }
            relations { edges { relationType node { id title { romaji } } } }
          } } }
        """
    }
}
