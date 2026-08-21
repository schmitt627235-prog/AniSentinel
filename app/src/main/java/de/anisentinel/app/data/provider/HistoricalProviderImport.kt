package de.anisentinel.app.data.provider

import de.anisentinel.app.data.local.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

object HistoricalSourcePolicy {
    const val PROVIDER_EPISODE = 300
    const val OFFICIAL_PROVIDER_ANNOUNCEMENT = 200

    fun conflicts(existingEpoch: Long?, candidateEpoch: Long): Boolean =
        existingEpoch != null && existingEpoch != candidateEpoch
}

data class AdnHistoricalEpisode(
    val episodeId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String?,
    val releaseLanguages: Set<String>,
    val releasedAt: Instant?,
    val dateSourceField: String?
)

data class AdnHistoryDiagnostics(
    val showId: String,
    val episodeCount: Int,
    val datedEpisodeCount: Int,
    val imported: Int,
    val enriched: Int,
    val conflicts: Int,
    val observedDateFields: Set<String>,
    val sourceUrl: String,
    val checkedAt: Instant,
    val result: String
)

object AdnPublicHistoryParser {
    private val exactDateFields = listOf(
        "releaseDate", "publicationDate", "publishedAt", "availabilityStartDate",
        "availableFrom", "startDate", "releaseAt", "publicationAt"
    )

    data class Parsed(val episodes: List<AdnHistoricalEpisode>, val observedDateFields: Set<String>)

    fun parse(body: String): Parsed {
        val objects = collectJsonObjects(body)
        val observed = objects.flatMap { obj -> exactDateFields.filter(obj::has) }.toSet()
        val rawEpisodes = objects.mapNotNull { obj ->
            val id = text(obj, "id") ?: text(obj, "videoId") ?: return@mapNotNull null
            val episode = number(obj, "shortNumber") ?: number(obj, "episodeNumber") ?: return@mapNotNull null
            val languages = languageSet(obj.opt("languages"))
            if (languages.none { it == "vostde" || it == "vde" }) return@mapNotNull null
            val dateEntry = exactDateFields.firstNotNullOfOrNull { key ->
                obj.opt(key)?.takeUnless { it == JSONObject.NULL }?.let { value -> parseInstant(value)?.let { key to it } }
            }
            AdnHistoricalEpisode(
                id,
                number(obj, "season") ?: number(obj, "seasonNumber") ?: 1,
                episode,
                text(obj, "name") ?: text(obj, "title"),
                buildSet {
                    if ("vostde" in languages) add("GER_SUB")
                    if ("vde" in languages) add("GER_DUB")
                },
                dateEntry?.second,
                dateEntry?.first
            )
        }.distinctBy { listOf(it.episodeId, it.seasonNumber, it.episodeNumber) }
        val firstProviderNumberBySeason = rawEpisodes.groupBy { it.seasonNumber }
            .mapValues { (season, episodes) ->
                if (season > 1 && episodes.size > 1) episodes.minOf { it.episodeNumber } else 1
            }
        val episodes = rawEpisodes.map { episode ->
            val firstProviderNumber = firstProviderNumberBySeason.getValue(episode.seasonNumber)
            episode.copy(episodeNumber = episode.episodeNumber - firstProviderNumber + 1)
        }
        return Parsed(episodes, observed)
    }

    private fun parseInstant(value: Any): Instant? = when (value) {
        is Number -> value.toLong().let { if (it > 10_000_000_000L) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it) }
        else -> value.toString().let { raw ->
            runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
                ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneId.of("Europe/Berlin")).toInstant() }.getOrNull()
        }
    }

    private fun collectJsonObjects(body: String): List<JSONObject> {
        val root: Any = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        val result = mutableListOf<JSONObject>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> { result += value; value.keys().forEachRemaining { visit(value.opt(it)) } }
                is JSONArray -> for (index in 0 until value.length()) visit(value.opt(index))
            }
        }
        visit(root)
        return result
    }

    private fun text(obj: JSONObject, key: String): String? =
        obj.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank)

    private fun number(obj: JSONObject, key: String): Int? {
        val raw = obj.opt(key)
        return when (raw) {
            is Number -> raw.toInt()
            else -> Regex("\\d+").find(raw?.toString().orEmpty())?.value?.toIntOrNull()
        }
    }

    private fun languageSet(value: Any?): Set<String> = when (value) {
        is JSONArray -> (0 until value.length()).map { value.optString(it).lowercase() }.toSet()
        is String -> value.split(',').map { it.trim().lowercase() }.toSet()
        else -> emptySet()
    }
}

class AdnHistoricalReleaseImporter(
    private val dao: AniSentinelDao,
    private val transport: ProviderMetadataTransport = PublicProviderMetadataTransport(),
    private val clock: Clock = Clock.systemUTC()
) {
    suspend fun diagnoseAndImport(animeId: String, showId: String): AdnHistoryDiagnostics {
        val now = clock.instant()
        if (!showId.matches(Regex("[A-Za-z0-9_-]+"))) return failed(showId, now, "ADN_SHOW_ID_INVALID")
        val url = "https://gw.api.animationdigitalnetwork.com/video/show/$showId?maxAgeCategory=18&limit=-1&order=asc"
        val response = runCatching { transport.get(url, mapOf("X-Target-Distribution" to "de")) }
            .getOrElse { return failed(showId, now, "ADN_PUBLIC_METADATA_NETWORK_FAILED", url) }
        if (response.status !in 200..299) return failed(showId, now, "ADN_PUBLIC_METADATA_HTTP_${response.status}", response.finalUrl)
        val parsed = runCatching { AdnPublicHistoryParser.parse(response.body) }
            .getOrElse { return failed(showId, now, "ADN_PUBLIC_METADATA_INVALID_JSON", response.finalUrl) }
        val dated = parsed.episodes.filter { it.releasedAt != null && !it.releasedAt.isAfter(now) }
        if (dated.isEmpty()) return AdnHistoryDiagnostics(
            showId, parsed.episodes.size, 0, 0, 0, 0, parsed.observedDateFields,
            response.finalUrl, now, "NO_EXACT_PUBLIC_EPISODE_DATE"
        )
        val rows = mutableListOf<EpisodeReleaseEntity>()
        val references = mutableListOf<ReleaseSourceReferenceEntity>()
        var inserted = 0; var enriched = 0; var conflicts = 0
        for (episode in dated) for (language in episode.releaseLanguages) {
            val epoch = requireNotNull(episode.releasedAt).epochSecond
            val existing = dao.semanticProviderRelease(animeId, episode.seasonNumber, episode.episodeNumber, language, "ADN")
            val releaseId = existing?.sourceReleaseId ?: "adn-history:$animeId:s${episode.seasonNumber}:e${episode.episodeNumber}:${language.lowercase()}"
            val conflict = HistoricalSourcePolicy.conflicts(existing?.historicalReleasedAt ?: existing?.expectedAt, epoch)
            if (conflict && (existing?.historicalSourcePriority ?: 0) >= HistoricalSourcePolicy.PROVIDER_EPISODE) {
                conflicts++
                references += reference(releaseId, episode, response.finalUrl, now)
                continue
            }
            val episodeUrl = "https://animationdigitalnetwork.com/de/video/-/${episode.episodeId}"
            rows += existing?.copy(
                episodeTitle = existing.episodeTitle ?: episode.title,
                expectedAt = epoch,
                providerUrl = episodeUrl,
                releaseStatus = "AVAILABLE",
                isHistoricalImport = true,
                historicalReleasedAt = epoch,
                releaseTimePrecision = if (episode.releasedAt.atZone(ZoneId.of("Europe/Berlin")).toLocalTime() == java.time.LocalTime.MIDNIGHT) "DATE" else "EXACT",
                historicalSourcePriority = HistoricalSourcePolicy.PROVIDER_EPISODE,
                historicalConflict = conflict
            ) ?: EpisodeReleaseEntity(
                releaseId, animeId, episode.episodeNumber, episode.title, epoch, "ADN",
                "ADN_PUBLIC_METADATA", response.finalUrl, episodeUrl, now.epochSecond,
                episode.seasonNumber, releaseStatus = "AVAILABLE", releaseLanguage = language,
                isHistoricalImport = true, historicalReleasedAt = epoch,
                releaseTimePrecision = if (episode.releasedAt.atZone(ZoneId.of("Europe/Berlin")).toLocalTime() == java.time.LocalTime.MIDNIGHT) "DATE" else "EXACT",
                historicalSourcePriority = HistoricalSourcePolicy.PROVIDER_EPISODE,
                historicalConflict = conflict
            )
            references += reference(releaseId, episode, response.finalUrl, now)
            if (existing == null) inserted++ else enriched++
        }
        val confirmedSeasons = dated.map { it.seasonNumber }.filter { it > 0 }.distinct()
        dao.importHistoricalProviderCatalog(
            rows,
            references,
            confirmedSeasons.map {
                AnimeSeasonEntity(animeId, it, "ADN_PUBLIC_METADATA", now.epochSecond)
            },
            confirmedSeasons.map {
                ProviderSeasonMappingEntity(
                    animeId = animeId,
                    canonicalSeasonNumber = it,
                    provider = "ADN",
                    providerSeasonNumber = it,
                    providerSeriesId = showId,
                    providerSeasonId = null,
                    providerSeriesUrl = null,
                    region = "DE",
                    available = true,
                    lastConfirmedAt = now.epochSecond
                )
            }
        )
        dao.upsertProviderMetadataIdentity(ProviderMetadataIdentityEntity(
            "provider-identity:$animeId:ADN_PUBLIC_METADATA:DE", animeId, "ADN_PUBLIC_METADATA",
            "DE", showId, null, null, null, null, response.finalUrl, now.epochSecond
        ))
        return AdnHistoryDiagnostics(showId, parsed.episodes.size, dated.size, inserted, enriched, conflicts,
            parsed.observedDateFields, response.finalUrl, now, if (conflicts > 0) "IMPORTED_WITH_CONFLICTS" else "IMPORTED")
    }

    private fun reference(releaseId: String, episode: AdnHistoricalEpisode, sourceUrl: String, now: Instant) =
        ReleaseSourceReferenceEntity("adn-history-ref:$releaseId", releaseId, "ADN_PUBLIC_METADATA", episode.episodeId, sourceUrl, now.epochSecond)

    private fun failed(showId: String, now: Instant, code: String, url: String = "") =
        AdnHistoryDiagnostics(showId, 0, 0, 0, 0, 0, emptySet(), url, now, code)
}
