package de.anisentinel.app.data.news

import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.AnnouncementEntity
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
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
    val animeId: String? = null
)

class Anime2YouNewsRepository(
    private val dao: AniSentinelDao,
    private val transport: Anime2YouNewsTransport = Anime2YouNewsTransport()
) {
    private val mutex = Mutex()
    private var lastAttemptAt: Instant? = null

    fun observeNews(): Flow<List<AnnouncementEntity>> = dao.observeAnnouncements()

    suspend fun refresh(now: Instant = Instant.now(), force: Boolean = false): NewsSyncResult = mutex.withLock {
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
                    confirmationStatus = "MULTI_SOURCE_CONFIRMED",
                    secondarySource = "ANIME2YOU",
                    secondarySourceUrl = confirmations.single().sourceUrl
                )))
            }
        }
        NewsSyncResult.Success(candidates.size, stored, now)
    }
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
        normalizedSubject(title), type.name, seasonNumber ?: "", publishedAt.epochSecond / 604_800
    ).joinToString("|")
    val dedupeKey = sha256(keyMaterial)
    return AnnouncementEntity(
        announcementId = "$source:${sha256(externalId).take(20)}",
        dedupeKey = dedupeKey,
        animeId = animeId,
        title = title,
        summary = summary,
        type = type.name,
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
