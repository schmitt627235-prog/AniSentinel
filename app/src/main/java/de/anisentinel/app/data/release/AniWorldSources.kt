package de.anisentinel.app.data.release

import android.content.Context
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.ReleaseScheduleHistoryEntity
import de.anisentinel.app.data.local.ReleasePostponementEntity
import de.anisentinel.app.data.local.ReleaseSourceReferenceEntity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class AniWorldCalendarEntry(
    val title: String,
    val normalizedTitle: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val listedAt: Instant,
    val releaseAt: Instant,
    val adjustmentMinutes: Int = -10,
    val originalTimeWasEndOfDayMarker: Boolean,
    val releaseLanguage: String,
    val externalId: String?,
    val coverUrl: String?,
    val sourceUrl: String,
    val fetchedAt: Instant
)

data class AniWorldScheduleChange(
    val title: String,
    val normalizedTitle: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val previousDate: LocalDate?,
    val revisedDate: LocalDate?,
    val releaseType: String?,
    val reason: String?,
    val direction: String,
    val sourceUrl: String,
    val evidenceUrl: String?,
    val detectedAt: Instant,
    val relativeDelayMinutes: Int? = null
)

fun normalizeAnimeTitle(value: String): String = value.lowercase(Locale.GERMAN)
    .replace(Regex("\\b(2nd|second)\\s+season\\b"), "staffel2")
    .replace(Regex("\\bseason\\s*(\\d+)\\b"), "staffel$1")
    .replace(Regex("\\bstaffel\\s*(\\d+)\\b"), "staffel$1")
    .replace(Regex("[^a-z0-9äöüß]+"), "")

internal fun aniWorldLanguageMatches(changeType: String?, releaseLanguage: String?): Boolean =
    when (changeType?.uppercase()) {
        "SUB" -> releaseLanguage == "GER_SUB"
        "DUB" -> releaseLanguage == "GER_DUB"
        "SUB+DUB", "DUB+SUB" -> releaseLanguage in setOf("GER_SUB", "GER_DUB")
        else -> false
    }

class AniWorldCalendarParser {
    fun parse(html: String, fetchedAt: Instant, zoneId: ZoneId): List<AniWorldCalendarEntry> {
        val document = Jsoup.parse(html, CALENDAR_URL)
        val parsed = document.select("section.calendarList").flatMap { section ->
            val dateText = section.selectFirst("h3")?.text().orEmpty()
            val date = DATE.find(dateText)?.value?.let {
                LocalDate.parse(it, DateTimeFormatter.ofPattern("dd.MM.uuuu"))
            } ?: return@flatMap emptyList()
            section.select("div.seriesListContainer > div").mapNotNull { card ->
                val title = card.selectFirst("h3.seriesTitle")?.text()?.trim().orEmpty()
                val href = card.selectFirst("h3.seriesTitle a[href*=/anime/stream/], a[href*=/anime/stream/]")
                    ?.attr("href")
                val small = card.select("small").map { it.ownText().trim() }
                val seasonEpisode = SEASON_EPISODE.find(small.joinToString(" "))
                    ?: return@mapNotNull null
                val timeMatch = TIME.find(small.joinToString(" ")) ?: return@mapNotNull null
                val flagPaths = card.select("img.flag").flatMap { image ->
                    listOf(image.attr("data-src"), image.attr("src"))
                }
                val releaseLanguage = when {
                    flagPaths.any { it.contains("japanese-german.svg", ignoreCase = true) } -> "GER_SUB"
                    flagPaths.any { Regex("(?:^|/)german\\.svg$", RegexOption.IGNORE_CASE).containsMatchIn(it) } -> "GER_DUB"
                    else -> return@mapNotNull null
                }
                if (title.isBlank()) return@mapNotNull null
                val coverImage = card.selectFirst("img[alt$=Cover], img[data-src*=/cover/]")
                val coverUrl = coverImage?.let { image ->
                    val candidate = image.attr("data-src").ifBlank { image.attr("src") }
                    if (candidate.startsWith("data:")) null else java.net.URI(CALENDAR_URL).resolve(candidate).toString()
                }?.takeIf { it.startsWith("https://aniworld.to/public/img/cover/") }
                val time = LocalTime.of(timeMatch.groupValues[1].toInt(), timeMatch.groupValues[2].toInt())
                val listed = LocalDateTime.of(date, time).atZone(zoneId).toInstant()
                AniWorldCalendarEntry(
                    title = title,
                    normalizedTitle = normalizeAnimeTitle(title),
                    seasonNumber = seasonEpisode.groupValues[1].toInt(),
                    episodeNumber = seasonEpisode.groupValues[2].toInt(),
                    listedAt = listed,
                    releaseAt = listed.minusSeconds(10 * 60L),
                    originalTimeWasEndOfDayMarker = time == LocalTime.of(23, 59),
                    releaseLanguage = releaseLanguage,
                    externalId = href?.substringAfter("/anime/stream/")?.substringBefore('/')?.takeIf(String::isNotBlank),
                    coverUrl = coverUrl,
                    sourceUrl = CALENDAR_URL,
                    fetchedAt = fetchedAt
                )
            }
        }
        return parsed.distinctBy {
            listOf(it.normalizedTitle, it.seasonNumber, it.episodeNumber, it.releaseAt.epochSecond, it.releaseLanguage)
        }
    }

    companion object {
        const val CALENDAR_URL = "https://aniworld.to/animekalender"
        private val DATE = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")
        private val SEASON_EPISODE = Regex("S(\\d{1,3})E(\\d{1,4})", RegexOption.IGNORE_CASE)
        private val TIME = Regex("(\\d{1,2}):(\\d{2})\\s*Uhr", RegexOption.IGNORE_CASE)
    }
}

class AniWorldScheduleChangeParser {
    fun parse(html: String, now: Instant, zoneId: ZoneId): List<AniWorldScheduleChange> {
        val document = Jsoup.parse(html, CHANGES_URL)
        val paragraph = document.selectFirst("article.supportFAQArticle p") ?: return emptyList()
        val lines = paragraph.html().split(Regex("(?i)<br\\s*/?>"))
            .map { Jsoup.parseBodyFragment(it).text().trim() }
        val evidenceLinks = paragraph.select("a[href]").associateBy({ it.text() }, { it.absUrl("href") })
        var title: String? = null
        var titleReleaseType: String? = null
        var season: Int? = null
        var episode: Int? = null
        val result = mutableListOf<AniWorldScheduleChange>()
        lines.forEachIndexed { index, line ->
            when {
                line.startsWith("⚠") || line.startsWith("🚨") -> {
                    titleReleaseType = Regex("\\[(Sub\\+Dub|Dub\\+Sub|Sub|Dub)]\\s*$", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.get(1)?.replaceFirstChar(Char::uppercase)
                    title = line.replace(Regex("^[⚠️🚨ℹ\\s]+"), "")
                        .replace(Regex("\\s*\\[(Sub\\+Dub|Dub\\+Sub|Sub|Dub)]\\s*$", RegexOption.IGNORE_CASE), "").trim()
                    season = null; episode = null
                }
                SEASON.find(line) != null -> {
                    val match = SEASON.find(line)!!
                    season = match.groupValues[1].toIntOrNull()
                    episode = match.groupValues.getOrNull(2)?.toIntOrNull()
                    titleReleaseType = Regex("\\((Sub\\+Dub|Dub\\+Sub|Sub|Dub)\\)", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.get(1)?.replaceFirstChar(Char::uppercase)
                        ?: titleReleaseType
                }
                CHANGE.find(line) != null && title != null -> {
                    val match = CHANGE.find(line)!!
                    val revisedToken = match.groupValues[3]
                    run {
                        val directionToken = match.groupValues[2]
                        val previous = resolveDate(match.groupValues[1], now, zoneId)
                        val relativeDelayMinutes = Regex("(\\d+)\\s*min", RegexOption.IGNORE_CASE)
                            .find(revisedToken)?.groupValues?.get(1)?.toIntOrNull()
                        val revised = revisedToken.takeUnless { it == "?" || relativeDelayMinutes != null }
                            ?.let { resolveDate(it, now, zoneId) }
                        val type = Regex("\\((Sub\\+Dub|Dub\\+Sub|Sub|Dub)\\)", RegexOption.IGNORE_CASE).find(line)
                            ?.groupValues?.get(1)?.replaceFirstChar(Char::uppercase) ?: titleReleaseType
                        val following = lines.drop(index + 1).takeWhile { it.isNotBlank() && !it.startsWith("-") && !it.startsWith("⚠") && !it.startsWith("🚨") && !it.startsWith("📅") }
                        val reason = following.firstOrNull { !it.startsWith("http") }
                        val evidence = following.firstNotNullOfOrNull { evidenceLinks[it] ?: it.takeIf { v -> v.startsWith("https://") } }
                        result += AniWorldScheduleChange(
                            title!!, normalizeAnimeTitle(title!!), season, episode, previous, revised,
                            type, reason, if (directionToken == "▲") "EARLIER" else "DELAYED",
                            CHANGES_URL, evidence, now, relativeDelayMinutes
                        )
                    }
                }
            }
        }
        return result.distinctBy { listOf(it.normalizedTitle, it.seasonNumber, it.episodeNumber, it.previousDate, it.revisedDate, it.releaseType) }
    }

    private fun resolveDate(value: String, now: Instant, zoneId: ZoneId): LocalDate? {
        val parts = value.split('.')
        if (parts.size < 2) return null
        val monthDay = runCatching { MonthDay.of(parts[1].toInt(), parts[0].toInt()) }.getOrNull() ?: return null
        val today = now.atZone(zoneId).toLocalDate()
        return (today.year - 1..today.year + 1).map { monthDay.atYear(it) }
            .minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, it)) }
    }

    companion object {
        const val CHANGES_URL = "https://aniworld.to/support/frage/anime-verschiebungen"
        private val SEASON = Regex("S(\\d{1,3})(?:\\s*E(\\d{1,4}))?", RegexOption.IGNORE_CASE)
        private val CHANGE = Regex(
            "(\\d{2}\\.\\d{2}\\.?)\\s*([▼▲►])\\s*(\\d{2}\\.\\d{2}\\.?|\\?|vsl\\s+ca\\.?\\s+\\d+\\s*min(?:uten)?\\s+später)",
            RegexOption.IGNORE_CASE
        )
    }
}

sealed interface AniWorldFetchResult {
    data class Success(val html: String, val fetchedAt: Instant, val fromCache: Boolean) : AniWorldFetchResult
    data class Failure(val diagnostic: String) : AniWorldFetchResult
}

class AniWorldHttpTransport(
    context: Context,
    private val clock: Clock = Clock.systemUTC(),
    private val loader: (suspend (String) -> Pair<Int, String>)? = null
) {
    private val cache = File(context.cacheDir, "aniworld-html").apply { mkdirs() }
    suspend fun fetch(url: String): AniWorldFetchResult {
        if (url !in ALLOWED_URLS) return AniWorldFetchResult.Failure("ANIWORLD_URL_REJECTED")
        val file = File(cache, url.hash())
        if (file.isFile && clock.millis() - file.lastModified() < 30 * 60 * 1000) {
            return AniWorldFetchResult.Success(file.readText(), Instant.ofEpochMilli(file.lastModified()), true)
        }
        return mutex.withLock {
            val response = runCatching { loader?.invoke(url) ?: load(url) }.getOrElse {
                return@withLock AniWorldFetchResult.Failure("ANIWORLD_NETWORK:${it.javaClass.simpleName}")
            }
            if (response.first !in 200..299 || response.second.isBlank()) {
                AniWorldFetchResult.Failure("ANIWORLD_HTTP_${response.first}")
            } else {
                file.writeText(response.second)
                AniWorldFetchResult.Success(response.second, clock.instant(), false)
            }
        }
    }

    private fun String.hash() = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    private suspend fun load(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000; connection.readTimeout = 25_000
            connection.setRequestProperty("User-Agent", "AniSentinel-DIAGNOSETEST/0.10.0 (Android; limited public release-calendar check; no login)")
            connection.setRequestProperty("Accept", "text/html")
            val code = connection.responseCode
            code to (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } finally { connection.disconnect() }
    }

    companion object {
        private val mutex = Mutex()
        private val ALLOWED_URLS = setOf(AniWorldCalendarParser.CALENDAR_URL, AniWorldScheduleChangeParser.CHANGES_URL)
    }
}

sealed interface AniWorldSyncResult {
    data class Success(val received: Int, val stored: Int, val changed: Int = 0) : AniWorldSyncResult
    data class Failure(val diagnostic: String) : AniWorldSyncResult
}

class AniWorldReleaseRepository(
    private val dao: AniSentinelDao,
    private val transport: AniWorldHttpTransport,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun syncCalendar(start: LocalDate, endExclusive: LocalDate): AniWorldSyncResult {
        // Repair identities produced by the former broad anchor selector even when
        // the network refresh itself is currently unavailable.
        dao.repairMalformedAniWorldEpisodeIdentities()
        val response = transport.fetch(AniWorldCalendarParser.CALENDAR_URL)
        if (response !is AniWorldFetchResult.Success) return AniWorldSyncResult.Failure((response as AniWorldFetchResult.Failure).diagnostic)
        val all = runCatching { AniWorldCalendarParser().parse(response.html, response.fetchedAt, zoneId) }
            .getOrElse { return AniWorldSyncResult.Failure("ANIWORLD_CALENDAR_PARSE:${it.javaClass.simpleName}") }
        val entries = all.filter { it.releaseAt.atZone(zoneId).toLocalDate() >= start && it.releaseAt.atZone(zoneId).toLocalDate() < endExclusive }
        val existing = dao.allAnime()
        val now = clock.instant().epochSecond
        val animeByKey = existing.groupBy { normalizeAnimeTitle(it.titleGerman.ifBlank { it.titleEnglish ?: it.titleRomaji.orEmpty() }) }
        val anime = entries.map { entry ->
            val matches = animeByKey[entry.normalizedTitle].orEmpty()
            val id = matches.singleOrNull()?.id ?: "aniworld:${entry.externalId ?: entry.normalizedTitle}"
            matches.singleOrNull()?.let { existingAnime ->
                existingAnime.copy(coverUrl = entry.coverUrl ?: existingAnime.coverUrl, updatedAt = now)
            } ?: AnimeEntity(id, null, null, entry.title, null, null, null, "", entry.coverUrl, null, null, null, null, now)
        }.distinctBy { it.id }
        val idByTitle = anime.groupBy { normalizeAnimeTitle(it.titleGerman) }
        val releases = entries.map { entry ->
            val animeId = idByTitle[entry.normalizedTitle]!!.single().id
            val releaseId = "aniworld:${entry.externalId ?: entry.normalizedTitle}:s${entry.seasonNumber}:e${entry.episodeNumber}:${entry.releaseAt.epochSecond}"
            EpisodeReleaseEntity("$releaseId:${entry.releaseLanguage.lowercase()}", animeId, entry.episodeNumber, null, entry.releaseAt.epochSecond,
                null, "ANIWORLD_CALENDAR", entry.sourceUrl, null, entry.fetchedAt.epochSecond,
                entry.seasonNumber, entry.listedAt.epochSecond, entry.adjustmentMinutes,
                entry.originalTimeWasEndOfDayMarker, "SCHEDULED", entry.releaseLanguage)
        }
        val refs = releases.zip(entries).map { (release, entry) ->
            ReleaseSourceReferenceEntity("${release.sourceReleaseId}:aniworld", release.sourceReleaseId,
                "ANIWORLD_CALENDAR", entry.externalId, entry.sourceUrl, entry.fetchedAt.epochSecond)
        }
        val from = start.atStartOfDay(zoneId).toEpochSecond()
        val until = endExclusive.atStartOfDay(zoneId).toEpochSecond()
        dao.replaceAniWorldReleaseRange(from, until, anime, releases, refs, now)
        dao.repairMalformedAniWorldEpisodeIdentities()
        return AniWorldSyncResult.Success(all.size, releases.size)
    }

    suspend fun syncScheduleChanges(): AniWorldSyncResult {
        val response = transport.fetch(AniWorldScheduleChangeParser.CHANGES_URL)
        if (response !is AniWorldFetchResult.Success) return AniWorldSyncResult.Failure((response as AniWorldFetchResult.Failure).diagnostic)
        val changes = runCatching { AniWorldScheduleChangeParser().parse(response.html, response.fetchedAt, zoneId) }
            .getOrElse { return AniWorldSyncResult.Failure("ANIWORLD_CHANGE_PARSE:${it.javaClass.simpleName}") }
        val rows = dao.releaseRowsForSource("ANIWORLD_CALENDAR")
        var applied = 0
        changes.forEach { change ->
            val matchingTitleAnimeIds = rows.asSequence()
                .filter { row ->
                    normalizeAnimeTitle(row.anime.titleGerman.ifBlank {
                        row.anime.titleEnglish ?: row.anime.titleRomaji.orEmpty()
                    }) == change.normalizedTitle
                }
                .map { it.release.animeId }
                .distinct()
                .toList()
            // A shift is title-wide UI information even when no exact episode row exists yet.
            // Associate it only for an unambiguous title match; exact release linking below
            // remains strict about season, episode and language.
            val titleAnimeId = matchingTitleAnimeIds.singleOrNull()
            val languageVariants = when (change.releaseType?.uppercase()) {
                "SUB" -> listOf("GER_SUB")
                "DUB" -> listOf("GER_DUB")
                "SUB+DUB", "DUB+SUB" -> listOf("GER_SUB", "GER_DUB")
                else -> listOf(null)
            }
            languageVariants.forEach { language ->
                val originalDateEpoch = change.previousDate?.atStartOfDay(zoneId)?.toEpochSecond()
                val postponementId = listOf(
                    change.normalizedTitle, change.seasonNumber, change.episodeNumber,
                    language, originalDateEpoch
                ).joinToString(":") { it?.toString() ?: "unknown" }
                val existing = dao.releasePostponement(postponementId)
                val candidates = rows.filter { row ->
                    normalizeAnimeTitle(row.anime.titleGerman.ifBlank { row.anime.titleEnglish ?: row.anime.titleRomaji.orEmpty() }) == change.normalizedTitle &&
                        row.release.seasonNumber == change.seasonNumber &&
                        (change.episodeNumber == null || row.release.episodeNumber == change.episodeNumber) &&
                        (language == null || row.release.releaseLanguage == language) &&
                        (change.previousDate == null ||
                            row.release.expectedAt?.let(Instant::ofEpochSecond)?.atZone(zoneId)?.toLocalDate() == change.previousDate ||
                            existing?.releaseId == row.release.sourceReleaseId)
                }
                val row = existing?.releaseId?.let { id -> rows.firstOrNull { it.release.sourceReleaseId == id } }
                    ?: candidates.singleOrNull()
                val current = row?.release?.expectedAt?.let(Instant::ofEpochSecond)?.atZone(zoneId)
                val siblingTime = rows.asSequence().filter { sibling ->
                    normalizeAnimeTitle(sibling.anime.titleGerman.ifBlank {
                        sibling.anime.titleEnglish ?: sibling.anime.titleRomaji.orEmpty()
                    }) == change.normalizedTitle &&
                        sibling.release.seasonNumber == change.seasonNumber &&
                        (language == null || sibling.release.releaseLanguage == language)
                }.mapNotNull { it.release.expectedAt?.let(Instant::ofEpochSecond)?.atZone(zoneId)?.toLocalTime() }
                    .firstOrNull()
                val localTime = current?.toLocalTime() ?: siblingTime ?: LocalTime.MIDNIGHT
                val previousAt = if (change.relativeDelayMinutes != null && row?.release?.expectedAt != null) {
                    row.release.expectedAt
                } else change.previousDate?.atTime(localTime)?.atZone(zoneId)?.toEpochSecond()
                val revisedAt = when {
                    change.relativeDelayMinutes != null && previousAt != null -> previousAt + change.relativeDelayMinutes * 60L
                    else -> change.revisedDate?.atTime(localTime)?.atZone(zoneId)?.toEpochSecond()
                }
                val changed = existing != null &&
                    (existing.newExpectedAt != revisedAt || existing.reason != change.reason)
                val revision = when {
                    existing == null -> 1
                    changed -> existing.revision + 1
                    else -> existing.revision
                }
                dao.upsertReleasePostponements(listOf(ReleasePostponementEntity(
                    postponementId, row?.release?.sourceReleaseId, row?.release?.animeId ?: titleAnimeId,
                    change.title, change.seasonNumber, change.episodeNumber, language,
                    previousAt, revisedAt, change.reason, change.direction,
                    "ANIWORLD_SCHEDULE_CHANGE", change.sourceUrl, change.evidenceUrl,
                    existing?.detectedAt ?: change.detectedAt.epochSecond,
                    change.detectedAt.epochSecond, true, revision, existing?.notifiedRevision ?: 0
                )))
                if (row != null && !row.release.releaseStatus.startsWith("AVAILABLE")) {
                    if (revisedAt != null) dao.upsertReleaseScheduleHistory(listOf(ReleaseScheduleHistoryEntity(
                        "${row.release.sourceReleaseId}:${change.previousDate}:${change.revisedDate}:${language}",
                        row.release.sourceReleaseId, previousAt, revisedAt, "ANIWORLD_SCHEDULE_CHANGE",
                        change.reason, language, change.detectedAt.epochSecond, change.sourceUrl, change.evidenceUrl
                    )))
                    dao.upsertEpisodeReleases(listOf(row.release.copy(
                        expectedAt = revisedAt ?: row.release.expectedAt,
                        releaseStatus = when {
                            change.direction != "DELAYED" -> "SCHEDULED"
                            revisedAt != null -> "RESCHEDULED"
                            else -> "POSTPONED"
                        }
                    )))
                    applied++
                }
            }
        }
        dao.deleteSupersededUntypedPostponements()
        dao.refreshNextAiring(clock.instant().epochSecond)
        return AniWorldSyncResult.Success(changes.size, applied, applied)
    }
}
