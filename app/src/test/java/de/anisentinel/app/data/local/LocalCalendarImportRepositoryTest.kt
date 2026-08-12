package de.anisentinel.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalCalendarImportRepositoryTest {
    private lateinit var database: AniSentinelDatabase
    private lateinit var repository: LocalCalendarImportRepository
    private lateinit var dao: AniSentinelDao

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.aniSentinelDao()
        repository = LocalCalendarImportRepository(dao)
    }

    @After fun close() = database.close()

    @Test fun `authorized diagnostic document imports atomically`() = runBlocking {
        val result = repository.import(validDocument().byteInputStream())
        assertTrue(result is LocalCalendarImportResult.Imported)
        val internalId = dao.observeAnime().first().single().id
        assertTrue(internalId.startsWith("local-import:"))
        assertEquals(
            internalId,
            dao.externalId(LocalCalendarImportRepository.LOCAL_DIAGNOSTIC_SOURCE, "test-dataset-v1:42")
                ?.internalAnimeId
        )
        assertEquals(java.time.Instant.parse("2026-08-02T12:00:00Z").epochSecond, dao.latestImportBatch()?.generatedAt)
        assertEquals("Owned diagnostic dataset", dao.latestImportBatch()?.rightsNotice)
        val start = LocalDate.of(2026, 8, 1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
        assertEquals(2, dao.observeEpisodeReleasesForWindow(start, start + 7 * 86_400).first().size)
    }

    @Test fun `missing rights notice rolls back entire document`() = runBlocking {
        val invalid = validDocument().replace("USER_OWNED_OR_AUTHORIZED", "")
        assertTrue(repository.import(invalid.byteInputStream()) is LocalCalendarImportResult.Invalid)
        assertTrue(dao.observeAnime().first().isEmpty())
    }

    @Test fun `wrong schema version is rejected`() = runBlocking {
        assertInvalid(validDocument().replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "UNSUPPORTED_SCHEMA_VERSION")
    }

    @Test fun `http source url is rejected`() = runBlocking {
        assertInvalid(validDocument().replace("https://example.org/anime/42", "http://example.org/anime/42"), "HTTPS_URL_REQUIRED")
    }

    @Test fun `duplicate external id is rejected before writes`() = runBlocking {
        val duplicate = validDocument().replace(
            "\"anime\":[{",
            "\"anime\":[{\"externalId\":\"42\",\"titleGerman\":\"Duplikat\",\"sourceUrl\":\"https://example.org/a\"},{"
        )
        assertInvalid(duplicate, "DUPLICATE_EXTERNAL_ID")
    }

    @Test fun `unknown release anime is rejected before writes`() = runBlocking {
        assertInvalid(validDocument().replace("\"animeExternalId\":\"42\"", "\"animeExternalId\":\"missing\""), "UNKNOWN_ANIME_EXTERNAL_ID")
    }

    @Test fun `identical second import is idempotent`() = runBlocking {
        assertTrue(repository.import(validDocument().byteInputStream()) is LocalCalendarImportResult.Imported)
        val second = repository.import(validDocument().byteInputStream())
        assertTrue(second is LocalCalendarImportResult.AlreadyImported)
        assertEquals(1, dao.observeAnime().first().size)
    }

    @Test fun `formatting and json field order do not change content identity`() = runBlocking {
        assertTrue(repository.import(validDocument().byteInputStream()) is LocalCalendarImportResult.Imported)
        val reformatted = JSONObject(validDocument()).let { root ->
            JSONObject()
                .put("releases", root.getJSONArray("releases"))
                .put("anime", root.getJSONArray("anime"))
                .put("rights", root.getJSONObject("rights"))
                .put("generatedAt", root.getString("generatedAt"))
                .put("source", root.getString("source"))
                .put("datasetId", root.getString("datasetId"))
                .put("schemaVersion", root.getInt("schemaVersion"))
                .toString(4)
        }
        assertTrue(repository.import(reformatted.byteInputStream()) is LocalCalendarImportResult.AlreadyImported)
    }

    @Test fun `real content change with same dataset id conflicts`() = runBlocking {
        assertTrue(repository.import(validDocument().byteInputStream()) is LocalCalendarImportResult.Imported)
        val changed = validDocument().replace("Eigener Testtitel", "Inhaltlich geändert")
        val result = repository.import(changed.byteInputStream())
        assertEquals("DATASET_CONTENT_CONFLICT", (result as LocalCalendarImportResult.Invalid).reason)
    }

    @Test fun `oversized file is rejected`() = runBlocking {
        val bytes = ByteArray(LocalCalendarImportRepository.MAX_FILE_BYTES + 1) { 'x'.code.toByte() }
        val result = repository.import(bytes.inputStream())
        assertEquals("FILE_TOO_LARGE", (result as LocalCalendarImportResult.Invalid).reason)
        assertTrue(dao.observeAnime().first().isEmpty())
    }

    private suspend fun assertInvalid(document: String, reason: String) {
        val result = repository.import(document.byteInputStream())
        assertEquals(reason, (result as LocalCalendarImportResult.Invalid).reason)
        assertTrue(dao.observeAnime().first().isEmpty())
    }

    private fun validDocument() = """
        {"schemaVersion":1,"datasetId":"test-dataset-v1","source":"test","generatedAt":"2026-08-02T12:00:00Z",
        "rights":{"basis":"USER_OWNED_OR_AUTHORIZED","notice":"Owned diagnostic dataset","redistributionAllowed":false},
        "anime":[{"externalId":"42","internalAnimeId":"anilist:1","titleGerman":"Eigener Testtitel","sourceUrl":"https://example.org/anime/42"}],
        "releases":[
        {"sourceReleaseId":"test:42:1","animeExternalId":"42","episodeNumber":1,"expectedAt":"2026-08-02T12:00:00Z","sourceUrl":"https://example.org/release/1"},
        {"sourceReleaseId":"test:42:2","animeExternalId":"42","episodeNumber":2,"expectedAt":"2026-08-03T12:00:00Z","sourceUrl":"https://example.org/release/2"}]}
    """.trimIndent()
}
