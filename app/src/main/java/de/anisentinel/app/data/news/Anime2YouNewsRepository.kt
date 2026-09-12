package de.anisentinel.app.data.news

import android.util.Log
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.AnnouncementEntity
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

enum class AnnouncementType {
    NEW_ANIME, NEW_SEASON, DELAY, NEW_DATE, SIMULCAST_CONFIRMED, DUB_CONFIRMED,
    PRODUCTION_BREAK, CONTINUATION_CONFIRMED, OTHER
}

sealed interface NewsSyncResult {
    data class Success(val received: Int, val stored: Int, val fetchedAt: Instant) : NewsSyncResult
    data class Failed(val code: String, val message: String?) : NewsSyncResult
}

internal data class AnnouncementCandidate(
    val externalId: String,
    val title: String,
    val summary: String?,
    val type: AnnouncementType,
    val seasonNumber: Int?,
    val oldDate: Instant? = null,
    val newDate: Instant? = null,
    val releaseWindow: String? = null,
    val reason: String? = null,
    val provider: String? = null,
    val publishedAt: Instant,
    val source: String,
    val sourceUrl: String,
    val imageUrl: String? = null,
    val animeId: String? = null,
    val releaseCategories: Set<ReleaseNewsCategory> = emptySet()
)

enum class ReleaseNewsCategory {
    RELEASE_DATE, POSTPONEMENT, STREAMING_PROVIDER, DACH_LICENSE,
    NO_DACH_STREAMING_LICENSE, PHYSICAL_RELEASE_ONLY, TRAILER_TEASER
}

class Anime2YouNewsRepository(
    private val dao: AniSentinelDao,
    private val transport: Anime2YouNewsTransport = Anime2YouNewsTransport()
) {
    private val mutex = Mutex()
    private var lastAttemptAt: Instant? = null

    fun observeNews(): Flow<List<AnnouncementEntity>> = dao.observeAnnouncements()

    private data class CachedTitleNews(val storedAt: Long, val items: List<AnnouncementEntity>)
    private val titleNewsCache = mutableMapOf<String, CachedTitleNews>()

    suspend fun searchForTitle(
        titles: Iterable<String>,
        now: Instant = Instant.now(),
        force: Boolean = false
    ): Anime2YouTitleNewsResult = mutex.withLock {
        val aliases = Anime2YouTitleVariants.build(titles)
        val queries = Anime2YouTitleVariants.searchQueries(aliases)
        if (queries.isEmpty()) return@withLock Anime2YouTitleNewsResult.Success(emptyList(), Anime2YouSearchMetrics())
        val cacheKey = "v${Anime2YouTitleNewsCachePolicy.VERSION}:" + aliases.map(Anime2YouTitleNormalizer::normalize).sorted().joinToString("|")
        titleNewsCache[cacheKey]?.takeIf { !force && Anime2YouTitleNewsCachePolicy.isFresh(it.storedAt, now.epochSecond, it.items.size) }?.let {
            Log.d(TAG, "ANIME2YOU_CACHE_HIT key=$cacheKey count=${it.items.size}")
            return@withLock Anime2YouTitleNewsResult.Success(it.items, Anime2YouSearchMetrics(cacheHit = true, accepted = it.items.size))
        }
        Log.d(TAG, "ANIME2YOU_CACHE_HIT key=$cacheKey hit=false")
        val merged = linkedMapOf<String, AnnouncementCandidate>()
        var raw = 0
        var parsed = 0
        var rejected = 0
        var successfulResponses = 0
        var lastFailure: String? = null
        queries.take(MAX_TITLE_QUERIES).forEach { query ->
            Log.d(TAG, "ANIME2YOU_QUERY query=$query")
            when (val response = transport.search(query)) {
                is Anime2YouHttpResult.Failure -> lastFailure = response.reason
                is Anime2YouHttpResult.Success -> {
                    successfulResponses++
                    Log.d(TAG, "ANIME2YOU_HTTP_STATUS status=${response.status} bytes=${response.body.toByteArray().size} url=${response.url}")
                    val searchPage = runCatching { Anime2YouSearchParser.parse(response.body) }.getOrElse {
                        lastFailure = "PARSE_ERROR:${it.message}"
                        Log.d(TAG, "ANIME2YOU_REJECTED_RESULTS reason=PARSE_ERROR")
                        return@forEach
                    }
                    raw += searchPage.rawCount
                    parsed += searchPage.items.size
                    searchPage.items.forEach { candidate ->
                        val reason = Anime2YouTitleNewsMatcher.rejectionReason(candidate.title, candidate.summary, aliases)
                        val url = Anime2YouSearchParser.canonicalUrl(candidate.sourceUrl)
                        when {
                            reason != null -> {
                                rejected++
                                Log.d(TAG, "ANIME2YOU_REJECTED_RESULTS reason=$reason title=${candidate.title}")
                            }
                            url == null -> {
                                rejected++
                                Log.d(TAG, "ANIME2YOU_REJECTED_RESULTS reason=INVALID_URL title=${candidate.title}")
                            }
                            url in merged -> Log.d(TAG, "ANIME2YOU_REJECTED_RESULTS reason=DUPLICATE url=$url")
                            else -> {
                                when (val article = transport.article(url)) {
                                    is Anime2YouHttpResult.Failure -> {
                                        rejected++
                                        lastFailure = article.reason
                                        Log.d(TAG, "ANIME2YOU_REJECTED_RESULTS reason=ARTICLE_${article.reason} url=$url")
                                    }
                                    is Anime2YouHttpResult.Success -> {
                                        val detail = Anime2YouArticleParser.parse(article.body, url)
                                        val detailReason = Anime2YouTitleNewsMatcher.rejectionReason(
                                            detail.title, detail.text, aliases
                                        )
                                        val categories = Anime2YouReleaseNewsClassifier.classify(detail.title, detail.text)
                                        if (detailReason != null) {
                                            rejected++
                                            Log.d(TAG, "ANIME2YOU_REJECTED_RESULTS reason=$detailReason url=$url")
                                        } else if (categories.isEmpty()) {
                                            rejected++
                                            Log.d(TAG, "ANIME2YOU_REJECTED_RESULTS reason=NOT_RELEASE_RELEVANT url=$url")
                                        } else {
                                            merged[url] = candidate.copy(
                                                title = detail.title.ifBlank { candidate.title },
                                                summary = detail.relevantSummary,
                                                publishedAt = detail.publishedAt ?: candidate.publishedAt,
                                                sourceUrl = url,
                                                releaseCategories = categories
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
        Log.d(TAG, "ANIME2YOU_RAW_RESULTS count=$raw")
        Log.d(TAG, "ANIME2YOU_PARSED_RESULTS count=$parsed")
        Log.d(TAG, "ANIME2YOU_ACCEPTED_RESULTS count=${merged.size}")
        if (successfulResponses == 0 || (merged.isEmpty() && lastFailure != null)) {
            return@withLock Anime2YouTitleNewsResult.Failure(lastFailure ?: "NETWORK_ERROR")
        }
        val entities = merged.values.map { it.toEntity(now) }.sortedByDescending { it.publishedAt }
        if (entities.isNotEmpty()) {
            dao.upsertAnnouncements(entities)
            titleNewsCache[cacheKey] = CachedTitleNews(now.epochSecond, entities)
        }
        Anime2YouTitleNewsResult.Success(
            entities,
            Anime2YouSearchMetrics(raw, parsed, entities.size, rejected, cacheHit = false)
        )
    }

    suspend fun refresh(now: Instant = Instant.now(), force: Boolean = false): NewsSyncResult = mutex.withLock {
        val cachedAt = dao.latestAnime2YouNewsFetch()
        if (!force && Anime2YouNewsCachePolicy.isFresh(cachedAt, now.epochSecond)) {
            return@withLock NewsSyncResult.Success(0, 0, Instant.ofEpochSecond(requireNotNull(cachedAt)))
        }
        if (!force && lastAttemptAt?.isAfter(now.minusSeconds(15 * 60)) == true) {
            return@withLock NewsSyncResult.Success(0, 0, now)
        }
        lastAttemptAt = now
        val xml = transport.fetch() ?: return@withLock NewsSyncResult.Failed(
            "ANIME2YOU_UNAVAILABLE", transport.lastError
        )
        val rssCandidates = runCatching { Anime2YouRssParser.parse(xml) }.getOrElse {
            return@withLock NewsSyncResult.Failed("ANIME2YOU_PARSER_CHANGED", it.message)
        }
        val aniWorldCandidates = dao.releaseScheduleHistoryForNews().map { history ->
            AnnouncementCandidate(
                externalId = "aniworld:${history.animeId}:${history.detectedAt}",
                title = history.titleGerman,
                summary = history.reason,
                type = if (history.previousAt != null) AnnouncementType.DELAY else AnnouncementType.NEW_DATE,
                seasonNumber = history.seasonNumber,
                oldDate = history.previousAt?.let(Instant::ofEpochSecond),
                newDate = Instant.ofEpochSecond(history.revisedAt),
                reason = history.reason,
                publishedAt = Instant.ofEpochSecond(history.detectedAt),
                source = "AniWorld",
                sourceUrl = history.sourceUrl,
                animeId = history.animeId
            )
        }
        val candidates = rssCandidates + aniWorldCandidates
        var stored = 0
        candidates.forEach { candidate ->
            val incoming = candidate.toEntity(now)
            val existing = dao.announcementByDedupeKey(incoming.dedupeKey)
            val merged = AnnouncementDeduplicator.merge(existing, incoming)
            dao.upsertAnnouncements(listOf(merged))
            stored++
        }
        val editorial = rssCandidates.filter(Anime2YouPostponementMatcher::isStreamingScheduleReport)
        dao.activeReleasePostponements().forEach { postponement ->
            val confirmations = editorial.filter { Anime2YouPostponementMatcher.matches(postponement, it) }
            if (confirmations.size == 1) {
                dao.upsertReleasePostponements(listOf(postponement.copy(
                    // AniWorld commonly carries the same Anime2You report. This records
                    // provenance, not an independent second confirmation.
                    confirmationStatus = "SHARED_ORIGIN_ANIME2YOU",
                    secondarySource = "ANIME2YOU",
                    secondarySourceUrl = confirmations.single().sourceUrl
                )))
            }
        }
        NewsSyncResult.Success(candidates.size, stored, now)
    }

    companion object {
        private const val TAG = "AniSentinel-Anime2You"
        private const val MAX_TITLE_QUERIES = 8
    }
}

data class Anime2YouSearchMetrics(
    val raw: Int = 0,
    val parsed: Int = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val cacheHit: Boolean = false
)

sealed interface Anime2YouTitleNewsResult {
    data class Success(val items: List<AnnouncementEntity>, val metrics: Anime2YouSearchMetrics) : Anime2YouTitleNewsResult
    data class Failure(val reason: String) : Anime2YouTitleNewsResult
}

object Anime2YouNewsCachePolicy {
    private const val MAX_AGE_SECONDS = 15 * 60L
    fun isFresh(fetchedAt: Long?, now: Long): Boolean =
        fetchedAt != null && fetchedAt <= now && now - fetchedAt < MAX_AGE_SECONDS
}

object Anime2YouTitleNewsCachePolicy {
    const val VERSION = 3
    private const val MAX_AGE_SECONDS = 6 * 60 * 60L
    fun isFresh(storedAt: Long, now: Long, resultCount: Int, version: Int = VERSION): Boolean =
        version == VERSION && resultCount > 0 && storedAt <= now && now - storedAt < MAX_AGE_SECONDS
}

object Anime2YouTitleNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.GERMAN)
        .replace('×', 'x')
        .replace(Regex("\\brussiya(?=-go\\b)"), "russia")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .let(::canonicalizeInstallment)

    private fun canonicalizeInstallment(value: String): String {
        var normalized = value
            .replace(Regex("\\b(?:erste[rsn]?) staffel\\b"), "season 1")
            .replace(Regex("\\b(?:zweite[rsn]?) staffel\\b"), "season 2")
            .replace(Regex("\\b(?:dritte[rsn]?) staffel\\b"), "season 3")
            .replace(Regex("\\b(?:vierte[rsn]?) staffel\\b"), "season 4")
            .replace(Regex("\\b(?:second|zweite[rsn]?) season\\b"), "season 2")
            .replace(Regex("\\b(?:third|dritte[rsn]?) season\\b"), "season 3")
            .replace(Regex("\\b(?:fourth|vierte[rsn]?) season\\b"), "season 4")
            .replace(Regex("\\bdai\\s+san\\s+maku\\b"), "season 3")
            .replace(Regex("\\b(\\d+)(?:st|nd|rd|th) season\\b"), "season ${'$'}1")
            .replace(Regex("\\bstaffel\\s*(\\d+)\\b"), "season ${'$'}1")
            .replace(Regex("\\berste[rsn]?\\b"), "1")
            .replace(Regex("\\bzweite[rsn]?\\b"), "2")
            .replace(Regex("\\bdritte[rsn]?\\b"), "3")
            .replace(Regex("\\bvierte[rsn]?\\b"), "4")
            .replace(Regex("\\bstaffel\\b"), "season")
        normalized = normalized.replace(Regex("\\s+"), " ").trim()
        return normalized
    }
}

object Anime2YouTitleVariants {
    fun build(titles: Iterable<String>): List<String> = buildSet {
        titles.map(String::trim).filter { it.length >= 2 && !it.equals("null", true) }.forEach { title ->
            add(title)
            title.replace(Regex("^(?:the|a|an)\\s+", RegexOption.IGNORE_CASE), "")
                .trim().takeIf { it.length >= 2 && it != title }?.let(::add)
            val shortName = title.substringBefore(':').trim()
            val installment = installmentNumber(title)
            if (shortName != title && shortName.length >= 3 && installment != null) {
                add("$shortName Season $installment")
            }
        }
    // Keep genuinely different search spellings (for example "2nd Season" and "Season 2").
    // Anime2You's WordPress search may return different results for them; semantic
    // installment canonicalization is only used later when validating a result.
    }.distinctBy { it.lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ").trim() }
        .filter { Anime2YouTitleNormalizer.normalize(it).length >= 2 }

    fun searchQueries(aliases: Iterable<String>): List<String> = buildSet {
        val values = aliases.toList()
        addAll(values)
        values.forEach { title ->
            title.replace(
                Regex("(?i)\\s+(?:(?:season|staffel)\\s*\\d+|\\d+(?:st|nd|rd|th)\\s+season|(?:second|third|fourth)\\s+season|第\\s*\\d+\\s*期)\\s*$"),
                ""
            ).trim().takeIf { it.length >= 4 && it != title }?.let(::add)
        }
    }.distinctBy { it.lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ").trim() }

    private fun installmentNumber(title: String): Int? {
        val direct = Regex("(?i)(?:season|staffel)\\s*(\\d+)|(\\d+)(?:st|nd|rd|th)\\s+season")
            .find(title)?.groupValues?.drop(1)?.firstNotNullOfOrNull(String::toIntOrNull)
        if (direct != null) return direct
        return when {
            Regex("(?i)\\bdai\\s+san\\s+maku\\b").containsMatchIn(title) -> 3
            else -> null
        }
    }
}

object Anime2YouTitleNewsMatcher {
    fun matching(items: Iterable<AnnouncementEntity>, titles: Iterable<String>): List<AnnouncementEntity> {
        val aliases = Anime2YouTitleVariants.build(titles).map(Anime2YouTitleNormalizer::normalize).filter { it.length >= 4 }.distinct()
        if (aliases.isEmpty()) return emptyList()
        return items.asSequence()
            .filter { "Anime2You" in it.sources.lines() }
            .filter { item ->
                val text = Anime2YouTitleNormalizer.normalize("${item.title} ${item.summary.orEmpty()}")
                aliases.any { alias -> matchesAlias(text, alias) }
            }
            .distinctBy { it.announcementId }
            .sortedByDescending { it.publishedAt }
            .toList()
    }

    fun rejectionReason(title: String, summary: String?, aliases: Iterable<String>): String? {
        val normalizedAliases = Anime2YouTitleVariants.build(aliases).map(Anime2YouTitleNormalizer::normalize).filter { it.length >= 4 }
        val text = Anime2YouTitleNormalizer.normalize("$title ${summary.orEmpty()}")
        val expectedSeasons = normalizedAliases.mapNotNull(::installmentNumber).toSet()
        val actualSeason = installmentNumber(text)
        if (expectedSeasons.size == 1 && actualSeason != null && actualSeason !in expectedSeasons) return "TITLE_MISMATCH"
        // If the AniList title identifies a concrete installment, the article must identify the
        // same installment as well. An unnumbered franchise article is not safe to assign to it.
        if (expectedSeasons.singleOrNull() != null && actualSeason == null) return "TITLE_MISMATCH"
        return if (normalizedAliases.any { matchesAlias(text, it) }) null else "TITLE_MISMATCH"
    }

    private fun installmentNumber(normalized: String): Int? {
        val tokens = normalized.split(' ')
        val seasonIndex = tokens.indexOf("season").takeIf { it >= 0 } ?: return null
        return (1..12).asSequence().flatMap { distance ->
            sequenceOf(seasonIndex - distance, seasonIndex + distance)
        }.filter { it in tokens.indices }.mapNotNull { tokens[it].toIntOrNull() }.firstOrNull()
    }

    private fun matchesAlias(text: String, alias: String): Boolean {
        if (text.contains(alias) || alias.contains(text)) return true
        val aliasTokens = alias.split(' ').filter { it.length >= 2 }.toSet()
        if (aliasTokens.size < 2) return false
        val textTokens = text.split(' ').toSet()
        if (aliasTokens.all(textTokens::contains)) return true
        val noise = setOf("the", "a", "an", "in", "of", "and", "her", "his", "season", "staffel")
        val core = aliasTokens.filterNot { it in noise || it.all(Char::isDigit) }.toSet()
        val overlap = core.count(textTokens::contains)
        return core.size >= 3 && overlap >= 3 && overlap * 2 >= core.size
    }
}

sealed interface Anime2YouHttpResult {
    data class Success(val status: Int, val body: String, val url: String) : Anime2YouHttpResult
    data class Failure(val reason: String) : Anime2YouHttpResult
}

internal data class Anime2YouArticle(
    val title: String,
    val text: String,
    val relevantSummary: String?,
    val publishedAt: Instant?
)

internal object Anime2YouArticleParser {
    fun parse(html: String, baseUrl: String): Anime2YouArticle {
        val document = Jsoup.parse(html, baseUrl)
        val title = document.selectFirst("h1.entry-title, h1.tdb-title-text")?.text()?.trim().orEmpty()
        val body = document.selectFirst(".td-post-content, .tdb_single_content, article")
        val text = body?.text()?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val published = document.selectFirst("time[datetime]")?.attr("datetime")
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        val relevant = text.split(Regex("(?<=[.!?])\\s+"))
            .filter { Anime2YouReleaseNewsClassifier.classify(title, it).isNotEmpty() }
            .joinToString(" ").take(420).takeIf(String::isNotBlank)
        return Anime2YouArticle(title, text, relevant, published)
    }
}

object Anime2YouReleaseNewsClassifier {
    private val postponement = Regex("verschob|verzöger|verspät|pause|unterbrech|neuer termin|wiederaufnahme", RegexOption.IGNORE_CASE)
    private val releaseDate = Regex("start(?:et|termin|datum)?|erscheint|ausstrahlung|sendestart|ab dem|premiere|veröffentlich", RegexOption.IGNORE_CASE)
    private val streaming = Regex("stream|simulcast|crunchyroll|netflix|disney\\+|adn|aniverse|amazon(?: prime)? video", RegexOption.IGNORE_CASE)
    private val dach = Regex("deutschland|deutschsprach|dach|hierzulande|deutschen raum|deutsche lizenz|deutscher simulcast", RegexOption.IGNORE_CASE)
    private val noDach = Regex("keine? (?:deutsche |dach[- ]?)?(?:streaming)?lizenz|kein simulcast (?:in|für) (?:deutschland|den deutschsprachigen raum)|nicht (?:in deutschland|im deutschsprachigen raum) verfügbar", RegexOption.IGNORE_CASE)
    private val physical = Regex("dvd|blu[ -]?ray|disc|home video|heimvideo|komplettbox|steelbook|collector'?s edition|volume|releaseplan", RegexOption.IGNORE_CASE)
    private val irrelevantOnly = Regex("merchandise|figur|\\bcd\\b|soundtrack|gewinnspiel|ranking|verkaufszahl|interview|sprecher|cast|manga|game|spiel", RegexOption.IGNORE_CASE)
    private val editorialOnly = Regex("autor(?:in)?|schöpfer|verspricht|warten.+lohnt|soll.+maßstäbe setzen|statement|kommentar", RegexOption.IGNORE_CASE)
    private val cooperationOnly = Regex("kooperation|kollaboration|collaboration|crossover|wirbt für|werbekampagne", RegexOption.IGNORE_CASE)
    private val trailerTeaser = Regex("trailer|teaser", RegexOption.IGNORE_CASE)

    fun classify(title: String, articleText: String): Set<ReleaseNewsCategory> {
        val text = "$title $articleText"
        val explicitlyNoDach = noDach.containsMatchIn(text)
        val hasTrailerOrTeaser = trailerTeaser.containsMatchIn(title)
        val hasPhysicalRelease = physical.containsMatchIn(title)
        val hasProviderOrLicenseAnnouncement =
            (streaming.containsMatchIn(title) && Regex("zeigt|streamt|simulcast|anbieter|lizenz|lizenziert|weltweit|auf abruf|programm|katalog|exklusiv|bei\\s+(?:crunchyroll|netflix|disney\\+|adn|aniverse|amazon)", RegexOption.IGNORE_CASE).containsMatchIn(title)) ||
                (dach.containsMatchIn(title) && Regex("lizenz|lizenziert|simulcast|stream", RegexOption.IGNORE_CASE).containsMatchIn(title)) ||
                explicitlyNoDach
        val hasDatedRelease = releaseDate.containsMatchIn(text) && Regex("\\b(?:19|20)\\d{2}\\b|\\b\\d{1,2}\\.\\s*(?:januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember)|frühling|sommer|herbst|winter", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasConcreteReleaseSignal = postponement.containsMatchIn(text) ||
            hasDatedRelease || hasTrailerOrTeaser || hasPhysicalRelease || hasProviderOrLicenseAnnouncement
        // Provider names are commonly mentioned incidentally in merchandise, cast and trailer
        // articles. Such a mention must not turn an otherwise irrelevant article into release news.
        if (irrelevantOnly.containsMatchIn(title) || editorialOnly.containsMatchIn(title) || cooperationOnly.containsMatchIn(title)) return emptySet()
        if (!hasConcreteReleaseSignal) return emptySet()
        if (Regex("trailer|visual|poster|charakterbild", RegexOption.IGNORE_CASE).containsMatchIn(title) &&
            !hasConcreteReleaseSignal) return emptySet()
        val categories = buildSet {
            if (postponement.containsMatchIn(text)) add(ReleaseNewsCategory.POSTPONEMENT)
            if (releaseDate.containsMatchIn(text) && (Regex("\\b(?:19|20)\\d{2}\\b|\\b\\d{1,2}\\.\\s*(?:januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember)|frühling|sommer|herbst|winter", RegexOption.IGNORE_CASE).containsMatchIn(text))) add(ReleaseNewsCategory.RELEASE_DATE)
            if (streaming.containsMatchIn(text)) add(ReleaseNewsCategory.STREAMING_PROVIDER)
            if (!explicitlyNoDach && dach.containsMatchIn(text) && streaming.containsMatchIn(text)) add(ReleaseNewsCategory.DACH_LICENSE)
            if (explicitlyNoDach) add(ReleaseNewsCategory.NO_DACH_STREAMING_LICENSE)
            if (hasPhysicalRelease) add(ReleaseNewsCategory.PHYSICAL_RELEASE_ONLY)
            if (hasTrailerOrTeaser) add(ReleaseNewsCategory.TRAILER_TEASER)
        }
        return categories
    }
}

internal data class Anime2YouSearchPage(val rawCount: Int, val items: List<AnnouncementCandidate>)

internal object Anime2YouSearchParser {
    fun parse(html: String): Anime2YouSearchPage {
        val document = Jsoup.parse(html, "https://www.anime2you.de/")
        val links = document.select("h3.entry-title a[href], h2.entry-title a[href]")
        val items = links.mapNotNull { link ->
            val title = link.text().trim()
            val url = canonicalUrl(link.absUrl("href")) ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            val module = link.closest(".tdb_module_loop, .td_module_wrap") ?: link.parent()
            val published = module?.selectFirst("time[datetime]")?.attr("datetime")?.let(::parseIsoDate)
                ?: Instant.EPOCH
            val summary = module?.selectFirst(".td-excerpt, .td_module_wrap .td-excerpt")?.text()?.trim()?.takeIf(String::isNotBlank)
            val image = module?.selectFirst("[data-img-url]")?.attr("data-img-url")?.takeIf(String::isNotBlank)
            AnnouncementCandidate(
                externalId = url, title = title, summary = summary, type = Anime2YouRssParser.classify(title.lowercase(Locale.GERMAN)),
                seasonNumber = Regex("(?:staffel|season)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(title)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                publishedAt = published, source = "Anime2You", sourceUrl = url, imageUrl = image
            )
        }.distinctBy { it.sourceUrl }
        return Anime2YouSearchPage(links.size, items)
    }

    fun canonicalUrl(value: String): String? = runCatching {
        val url = URL(value)
        if (url.protocol != "https" || !url.host.equals("www.anime2you.de", true) || !url.path.startsWith("/news/")) null
        else "https://www.anime2you.de${url.path.trimEnd('/')}/"
    }.getOrNull()

    private fun parseIsoDate(value: String): Instant? = runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
}

internal object Anime2YouPostponementMatcher {
    private val shiftSignals = Regex("verschob|verzöger|verspät|später|neuer termin|pause|wiederaufnahme", RegexOption.IGNORE_CASE)
    private val physicalSignals = Regex("dvd|blu[ -]?ray|disc|volume|komplettbox|heimvideo", RegexOption.IGNORE_CASE)
    private val episodeSignals = Regex("stream|simulcast|tv|episode|folge|staffel|ausstrahlung|wiederaufnahme", RegexOption.IGNORE_CASE)

    fun isStreamingScheduleReport(candidate: AnnouncementCandidate): Boolean {
        val text = "${candidate.title} ${candidate.summary.orEmpty()}"
        return shiftSignals.containsMatchIn(text) && episodeSignals.containsMatchIn(text) && !physicalSignals.containsMatchIn(text)
    }

    fun matches(postponement: de.anisentinel.app.data.local.ReleasePostponementEntity, candidate: AnnouncementCandidate): Boolean {
        if (!isStreamingScheduleReport(candidate)) return false
        val sourceSubject = normalizedSubject(candidate.title)
        val wanted = normalizedSubject(postponement.title)
        val titleMatches = sourceSubject.contains(wanted) || wanted.contains(sourceSubject)
        val seasonMatches = candidate.seasonNumber == null || postponement.seasonNumber == null || candidate.seasonNumber == postponement.seasonNumber
        return titleMatches && seasonMatches
    }
}

class Anime2YouNewsTransport(
    private val endpoint: String = "https://www.anime2you.de/news/feed/"
) {
    @Volatile var lastError: String? = null
        private set

    suspend fun search(query: String): Anime2YouHttpResult = withContext(Dispatchers.IO) {
        val target = "https://www.anime2you.de/?s=${URLEncoder.encode(query, Charsets.UTF_8.name())}"
        runCatching {
            val connection = URL(target).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "AniSentinel/0.25.16 (+https://github.com/schmitt627235-prog/AniSentinel)")
            connection.setRequestProperty("Accept", "text/html")
            connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
            connection.useCaches = true
            val code = connection.responseCode
            val finalUrl = connection.url.toString()
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (bytes.size > 3_000_000) error("RESPONSE_TOO_LARGE")
            Log.d("AniSentinel-Anime2You", "ANIME2YOU_HTTP_STATUS status=$code bytes=${bytes.size} url=$finalUrl")
            if (code !in 200..299) Anime2YouHttpResult.Failure("HTTP_$code")
            else Anime2YouHttpResult.Success(code, bytes.toString(Charsets.UTF_8), finalUrl)
        }.getOrElse { error ->
            Log.d("AniSentinel-Anime2You", "ANIME2YOU_HTTP_STATUS error=${error.message} url=$target")
            Anime2YouHttpResult.Failure(error.message ?: "NETWORK_ERROR")
        }
    }

    suspend fun article(url: String): Anime2YouHttpResult = withContext(Dispatchers.IO) {
        val canonical = Anime2YouSearchParser.canonicalUrl(url)
            ?: return@withContext Anime2YouHttpResult.Failure("INVALID_ARTICLE_URL")
        requestHtml(canonical)
    }

    private fun requestHtml(target: String): Anime2YouHttpResult = runCatching {
        val connection = URL(target).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "AniSentinel/0.25.16 (+https://github.com/schmitt627235-prog/AniSentinel)")
        connection.setRequestProperty("Accept", "text/html")
        connection.setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
        connection.useCaches = true
        val code = connection.responseCode
        val finalUrl = connection.url.toString()
        val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.use { it.readBytes() } ?: ByteArray(0)
        if (bytes.size > 3_000_000) error("RESPONSE_TOO_LARGE")
        if (code !in 200..299) Anime2YouHttpResult.Failure("HTTP_$code")
        else Anime2YouHttpResult.Success(code, bytes.toString(Charsets.UTF_8), finalUrl)
    }.getOrElse { Anime2YouHttpResult.Failure(it.message ?: "NETWORK_ERROR") }

    suspend fun fetch(): String? = withContext(Dispatchers.IO) {
        repeat(3) { attempt ->
            val result = runCatching {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 12_000
                connection.readTimeout = 18_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "AniSentinel/0.20 (Android; news reader; contact: local prototype)")
                connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
                connection.useCaches = true
                val code = connection.responseCode
                if (code !in 200..299) error("HTTP_$code")
                val bytes = connection.inputStream.use { input ->
                    val data = input.readBytes()
                    if (data.size > 2_000_000) error("RESPONSE_TOO_LARGE")
                    data
                }
                bytes.toString(Charsets.UTF_8)
            }
            if (result.isSuccess) {
                lastError = null
                return@withContext result.getOrThrow()
            }
            lastError = result.exceptionOrNull()?.message
            if (attempt < 2) Thread.sleep(500L * (1L shl attempt))
        }
        null
    }
}

internal object Anime2YouRssParser {
    fun parse(xml: String): List<AnnouncementCandidate> {
        val document = Jsoup.parse(xml, "https://www.anime2you.de", Parser.xmlParser())
        return document.select("item").mapNotNull { item ->
            val title = item.selectFirst("title")?.text()?.trim().orEmpty()
            val sourceUrl = item.selectFirst("link")?.text()?.trim().orEmpty()
            val published = item.selectFirst("pubDate")?.text()?.trim()?.let(::parseDate)
            if (title.isBlank() || sourceUrl.isBlank() || published == null) return@mapNotNull null
            val rawDescription = item.selectFirst("description")?.text()?.trim()
            val summary = rawDescription?.let { Jsoup.parse(it).text().takeIf(String::isNotBlank) }
            val normalizedTitle = title.lowercase(Locale.GERMAN)
            val searchable = "$normalizedTitle ${summary.orEmpty()}".lowercase(Locale.GERMAN)
            // Classification is intentionally title-only. Editorial prose may mention German TV,
            // another provider or a past dub without the article announcing any of those things.
            val type = classify(normalizedTitle)
            val season = Regex("(?:staffel|season)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(searchable)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val provider = listOf("Crunchyroll", "Netflix", "Amazon Prime Video", "aniverse", "ADN", "Disney+")
                .firstOrNull { searchable.contains(it.lowercase(Locale.GERMAN)) }
            val guid = item.selectFirst("guid")?.text()?.trim().orEmpty().ifBlank { sourceUrl }
            val image = item.getElementsByTag("media:content").firstOrNull()?.attr("url")
                ?.takeIf(String::isNotBlank)
            AnnouncementCandidate(
                externalId = guid,
                title = title,
                summary = summary,
                type = type,
                seasonNumber = season,
                provider = provider,
                publishedAt = published,
                source = "Anime2You",
                sourceUrl = sourceUrl,
                imageUrl = image
            )
        }.distinctBy { it.externalId }
    }

    private fun parseDate(value: String): Instant? = runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    }.getOrNull()

    internal fun classify(text: String): AnnouncementType = when {
        Regex("verschob|verzöger|neuer termin statt").containsMatchIn(text) -> AnnouncementType.DELAY
        Regex("produktionspause|pause|unterbrech").containsMatchIn(text) -> AnnouncementType.PRODUCTION_BREAK
        Regex("synchro|dub|deutsche sprachfassung|auf deutsch").containsMatchIn(text) -> AnnouncementType.DUB_CONFIRMED
        Regex("simulcast|streaming|streamt|auf (?:prime video|netflix|crunchyroll|aniverse|adn)|verfügbar").containsMatchIn(text) -> AnnouncementType.SIMULCAST_CONFIRMED
        Regex("starttermin|termin .*steht fest|startet am|ab dem ").containsMatchIn(text) -> AnnouncementType.NEW_DATE
        Regex("fortsetzung|weitere staffel").containsMatchIn(text) -> AnnouncementType.CONTINUATION_CONFIRMED
        Regex("staffel \\d+|neue staffel|zweite staffel|dritte staffel|vierte staffel").containsMatchIn(text) -> AnnouncementType.NEW_SEASON
        Regex("erhält (?:eine )?anime|anime-adaption|anime angekündigt").containsMatchIn(text) -> AnnouncementType.NEW_ANIME
        else -> AnnouncementType.OTHER
    }
}

object AnnouncementDeduplicator {
    fun merge(existing: AnnouncementEntity?, incoming: AnnouncementEntity): AnnouncementEntity {
        if (existing == null) return incoming
        val sources = (existing.sources.lines() + incoming.sources.lines()).filter(String::isNotBlank).distinct()
        val urls = (existing.sourceUrls.lines() + incoming.sourceUrls.lines()).filter(String::isNotBlank).distinct()
        return existing.copy(
            sources = sources.joinToString("\n"),
            sourceUrls = urls.joinToString("\n"),
            fetchedAt = maxOf(existing.fetchedAt, incoming.fetchedAt),
            summary = existing.summary ?: incoming.summary,
            imageUrl = existing.imageUrl ?: incoming.imageUrl,
            animeId = existing.animeId ?: incoming.animeId,
            seasonNumber = existing.seasonNumber ?: incoming.seasonNumber,
            oldDate = existing.oldDate ?: incoming.oldDate,
            newDate = existing.newDate ?: incoming.newDate,
            releaseWindow = existing.releaseWindow ?: incoming.releaseWindow,
            reason = existing.reason ?: incoming.reason,
            provider = existing.provider ?: incoming.provider
        )
    }
}

private fun AnnouncementCandidate.toEntity(fetchedAt: Instant): AnnouncementEntity {
    // A weekly proximity bucket prevents unrelated later changes from collapsing while allowing
    // Anime2You and AniWorld reports of the same current event to become confirmations.
    val keyMaterial = listOf(
        normalizedSubject(title), releaseCategories.sortedBy { it.name }.joinToString("+").ifBlank { type.name }, seasonNumber ?: "", publishedAt.epochSecond / 604_800
    ).joinToString("|")
    val dedupeKey = sha256(keyMaterial)
    return AnnouncementEntity(
        announcementId = "$source:${sha256(externalId).take(20)}",
        dedupeKey = dedupeKey,
        animeId = animeId,
        title = title,
        summary = summary,
        type = releaseCategories.sortedBy { it.name }.joinToString("+").ifBlank { type.name },
        seasonNumber = seasonNumber,
        oldDate = oldDate?.epochSecond,
        newDate = newDate?.epochSecond,
        releaseWindow = releaseWindow,
        reason = reason,
        provider = provider,
        publishedAt = publishedAt.epochSecond,
        sources = source,
        sourceUrls = sourceUrl,
        imageUrl = imageUrl,
        fetchedAt = fetchedAt.epochSecond
    )
}

private fun normalizedSubject(title: String): String {
    val quotedSubject = Regex("»([^«]+)«").find(title)?.groupValues?.getOrNull(1)
    return (quotedSubject ?: title).lowercase(Locale.GERMAN)
    .replace(Regex("[^a-z0-9äöüß]+"), " ")
    .replace(Regex("\\b(?:verschoben|starttermin|termin|trailer|visual|angekündigt|steht fest)\\b"), " ")
    .replace(Regex("\\s+"), " ").trim()
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
