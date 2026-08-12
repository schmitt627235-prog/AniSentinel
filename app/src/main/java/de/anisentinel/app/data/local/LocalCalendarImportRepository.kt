package de.anisentinel.app.data.local

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface LocalCalendarImportResult {
    sealed interface Success : LocalCalendarImportResult {
        val importId: String
        val animeCount: Int
        val releaseCount: Int
        val source: String
        val generatedAt: Instant
        val importedAt: Instant
        val earliestReleaseAt: Instant
        val latestReleaseAt: Instant
        val rightsNotice: String
    }
    data class Imported(
        override val importId: String, override val animeCount: Int, override val releaseCount: Int,
        override val source: String, override val generatedAt: Instant, override val importedAt: Instant,
        override val earliestReleaseAt: Instant, override val latestReleaseAt: Instant,
        override val rightsNotice: String
    ) : Success
    data class AlreadyImported(
        override val importId: String, override val animeCount: Int, override val releaseCount: Int,
        override val source: String, override val generatedAt: Instant, override val importedAt: Instant,
        override val earliestReleaseAt: Instant, override val latestReleaseAt: Instant,
        override val rightsNotice: String
    ) : Success
    data class Invalid(val reason: String) : LocalCalendarImportResult
}

class LocalCalendarImportRepository(
    private val dao: AniSentinelDao,
    private val clock: Clock = Clock.systemUTC()
) {
    private val mutex = Mutex()

    suspend fun import(input: InputStream): LocalCalendarImportResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val parsed = runCatching { parse(readLimited(input)) }
                .getOrElse { return@withLock LocalCalendarImportResult.Invalid(it.message ?: "INVALID_DOCUMENT") }
            dao.importBatch(parsed.batch.datasetId)?.let { existing ->
                if (existing.contentHash == parsed.batch.contentHash) {
                    return@withLock existing.toAlreadyImported()
                }
                return@withLock LocalCalendarImportResult.Invalid("DATASET_CONTENT_CONFLICT")
            }
            for (mapping in parsed.ids) {
                val existing = dao.externalId(mapping.source, mapping.externalId)
                if (existing != null && existing.internalAnimeId != mapping.internalAnimeId) {
                    return@withLock LocalCalendarImportResult.Invalid("EXTERNAL_ID_MAPPING_CONFLICT")
                }
            }
            runCatching {
                dao.importDiagnosticCalendar(parsed.anime, parsed.ids, parsed.releases, parsed.batch)
                LocalCalendarImportResult.Imported(
                    parsed.batch.importId, parsed.batch.animeCount, parsed.batch.releaseCount,
                    parsed.batch.source, Instant.ofEpochSecond(parsed.batch.generatedAt),
                    Instant.ofEpochSecond(parsed.batch.importedAt),
                    Instant.ofEpochSecond(parsed.batch.earliestReleaseAt),
                    Instant.ofEpochSecond(parsed.batch.latestReleaseAt), parsed.batch.rightsNotice
                )
            }.getOrElse { LocalCalendarImportResult.Invalid("DATABASE_TRANSACTION_FAILED") }
        }
    }

    private fun readLimited(input: InputStream): String {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (output.size() + count > MAX_FILE_BYTES) error("FILE_TOO_LARGE")
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    private fun parse(raw: String): ParsedImport {
        val root = runCatching { JSONObject(raw) }.getOrElse { error("INVALID_JSON") }
        require(root.optInt("schemaVersion", -1) == 1) { "UNSUPPORTED_SCHEMA_VERSION" }
        val source = root.required("source", MAX_SOURCE_LENGTH)
        val datasetId = root.required("datasetId", MAX_ID_LENGTH)
        val generatedAt = runCatching { Instant.parse(root.required("generatedAt", 64)) }
            .getOrElse { error("INVALID_GENERATED_AT") }
        val rights = root.optJSONObject("rights") ?: error("RIGHTS_CONFIRMATION_REQUIRED")
        require(rights.optString("basis") == "USER_OWNED_OR_AUTHORIZED") { "RIGHTS_CONFIRMATION_REQUIRED" }
        require(!rights.optBoolean("redistributionAllowed", true)) { "REDISTRIBUTION_NOT_ALLOWED" }
        val rightsNotice = rights.required("notice", MAX_RIGHTS_LENGTH)
        val animeArray = root.getJSONArray("anime")
        val releasesArray = root.getJSONArray("releases")
        require(animeArray.length() in 1..MAX_ANIME) { "ANIME_COUNT_INVALID" }
        require(releasesArray.length() in 1..MAX_RELEASES) { "RELEASE_COUNT_INVALID" }

        val importId = UUID.randomUUID().toString()
        val stableNamespace = normalizeId(datasetId)
        val importedAt = clock.instant()
        val anime = mutableListOf<AnimeEntity>()
        val ids = mutableListOf<AnimeExternalIdEntity>()
        val idByExternal = mutableMapOf<String, String>()
        for (index in 0 until animeArray.length()) {
            val item = animeArray.getJSONObject(index)
            val externalId = item.required("externalId", MAX_ID_LENGTH)
            require(externalId !in idByExternal) { "DUPLICATE_EXTERNAL_ID" }
            val internalId = "local-import:$stableNamespace:${normalizeId(externalId)}"
            idByExternal[externalId] = internalId
            val sourceUrl = item.requiredHttpsUrl("sourceUrl")
            val german = item.optional("titleGerman", MAX_TITLE_LENGTH)
            val english = item.optional("titleEnglish", MAX_TITLE_LENGTH)
            val romaji = item.optional("titleRomaji", MAX_TITLE_LENGTH)
            require(german != null || english != null || romaji != null) { "TITLE_REQUIRED" }
            anime += AnimeEntity(
                internalId, null, null, german.orEmpty(), english, romaji,
                item.optional("titleNative", MAX_TITLE_LENGTH),
                item.optional("description", MAX_DESCRIPTION_LENGTH).orEmpty(),
                item.optionalHttpsUrl("coverUrl"), null, null, null,
                item.optInt("totalEpisodes").takeIf { it > 0 }, importedAt.epochSecond,
                cachedAt = importedAt.epochSecond
            )
            ids += AnimeExternalIdEntity(
                LOCAL_DIAGNOSTIC_SOURCE,
                "$datasetId:$externalId",
                internalId,
                sourceUrl
            )
        }

        val releases = mutableListOf<EpisodeReleaseEntity>()
        val rawReleaseIds = mutableSetOf<String>()
        for (index in 0 until releasesArray.length()) {
            val item = releasesArray.getJSONObject(index)
            val rawReleaseId = item.required("sourceReleaseId", MAX_ID_LENGTH)
            require(rawReleaseIds.add(rawReleaseId)) { "DUPLICATE_RELEASE_ID" }
            val externalId = item.required("animeExternalId", MAX_ID_LENGTH)
            val animeId = idByExternal[externalId] ?: error("UNKNOWN_ANIME_EXTERNAL_ID")
            val expectedAt = runCatching { Instant.parse(item.required("expectedAt", 64)) }
                .getOrElse { error("INVALID_RELEASE_AT") }
            releases += EpisodeReleaseEntity(
                "local-import:$stableNamespace:$rawReleaseId", animeId,
                item.optInt("episodeNumber").takeIf { it > 0 },
                item.optional("episodeTitle", MAX_TITLE_LENGTH), expectedAt.epochSecond,
                item.optional("provider", MAX_TITLE_LENGTH), "LOCAL_DIAGNOSTIC:$importId",
                item.requiredHttpsUrl("sourceUrl"), item.optionalHttpsUrl("providerUrl"), importedAt.epochSecond
            )
        }
        val earliest = releases.minOf { requireNotNull(it.expectedAt) }
        val latest = releases.maxOf { requireNotNull(it.expectedAt) }
        val batch = LocalImportBatchEntity(
            importId, datasetId,
            canonicalContent(
                schemaVersion = 1,
                datasetId = datasetId,
                source = source,
                generatedAt = generatedAt,
                rightsBasis = rights.optString("basis"),
                redistributionAllowed = rights.optBoolean("redistributionAllowed"),
                rightsNotice = rightsNotice,
                anime = anime,
                ids = ids,
                releases = releases
            ).sha256(),
            source, generatedAt.epochSecond, importedAt.epochSecond, rightsNotice,
            anime.size, releases.size, earliest, latest
        )
        return ParsedImport(anime, ids, releases, batch)
    }

    private fun normalizeId(value: String) = value.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        .take(MAX_ID_LENGTH).also { require(it.isNotBlank()) { "INVALID_EXTERNAL_ID" } }
    private fun JSONObject.required(name: String, max: Int) = getString(name).trim().also {
        require(it.isNotBlank()) { "${name.uppercase()}_REQUIRED" }
        require(it.length <= max) { "TEXT_TOO_LONG" }
    }
    private fun JSONObject.optional(name: String, max: Int) = optString(name).trim()
        .takeIf(String::isNotBlank)?.also { require(it.length <= max) { "TEXT_TOO_LONG" } }
    private fun JSONObject.requiredHttpsUrl(name: String) = required(name, MAX_URL_LENGTH).also(::requireHttps)
    private fun JSONObject.optionalHttpsUrl(name: String) = optional(name, MAX_URL_LENGTH)?.also(::requireHttps)
    private fun requireHttps(value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank()) { "HTTPS_URL_REQUIRED" }
    }
    private data class ParsedImport(
        val anime: List<AnimeEntity>, val ids: List<AnimeExternalIdEntity>,
        val releases: List<EpisodeReleaseEntity>, val batch: LocalImportBatchEntity
    )
    private fun canonicalContent(
        schemaVersion: Int,
        datasetId: String,
        source: String,
        generatedAt: Instant,
        rightsBasis: String,
        redistributionAllowed: Boolean,
        rightsNotice: String,
        anime: List<AnimeEntity>,
        ids: List<AnimeExternalIdEntity>,
        releases: List<EpisodeReleaseEntity>
    ): String = buildString {
        fun field(value: Any?) {
            val text = value?.toString() ?: "<null>"
            append(text.length).append(':').append(text).append('|')
        }
        field(schemaVersion); field(datasetId); field(source); field(generatedAt.toString())
        field(rightsBasis); field(redistributionAllowed); field(rightsNotice)
        anime.sortedBy { it.id }.forEach { item ->
            field("anime"); field(item.id); field(item.titleGerman); field(item.titleEnglish)
            field(item.titleRomaji); field(item.titleNative); field(item.description)
            field(item.coverUrl); field(item.totalEpisodes)
        }
        ids.sortedWith(compareBy(AnimeExternalIdEntity::source, AnimeExternalIdEntity::externalId))
            .forEach { item ->
                field("externalId"); field(item.source); field(item.externalId)
                field(item.internalAnimeId); field(item.sourceUrl)
            }
        releases.sortedBy { it.sourceReleaseId }.forEach { item ->
            field("release"); field(item.sourceReleaseId); field(item.animeId)
            field(item.episodeNumber); field(item.episodeTitle); field(item.expectedAt)
            field(item.provider); field(item.sourceUrl); field(item.providerUrl)
        }
    }
    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun LocalImportBatchEntity.toAlreadyImported() = LocalCalendarImportResult.AlreadyImported(
        importId, animeCount, releaseCount, source, Instant.ofEpochSecond(generatedAt),
        Instant.ofEpochSecond(importedAt), Instant.ofEpochSecond(earliestReleaseAt),
        Instant.ofEpochSecond(latestReleaseAt), rightsNotice
    )
    companion object {
        const val LOCAL_DIAGNOSTIC_SOURCE = "LOCAL_DIAGNOSTIC"
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
        const val MAX_ANIME = 500
        const val MAX_RELEASES = 5_000
        const val MAX_TITLE_LENGTH = 300
        const val MAX_DESCRIPTION_LENGTH = 20_000
        const val MAX_URL_LENGTH = 2_048
        const val MAX_ID_LENGTH = 200
        const val MAX_SOURCE_LENGTH = 200
        const val MAX_RIGHTS_LENGTH = 2_000
    }
}
