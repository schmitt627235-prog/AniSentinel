package de.anisentinel.app.data.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.anisentinel.app.data.local.AniSentinelDatabase
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppleTvCatalogImporterTest {
    private lateinit var database: AniSentinelDatabase
    private val clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC)
    private val showId = "umc.cmc.o4e5fbtkmgjivlpghedf8a6x"
    private val url = "https://tv.apple.com/de/show/detektiv-conan/$showId"

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun officialFallbackIsRestrictedToTheVerifiedCanonicalConanIdentity() {
        assertEquals(url, AppleTvOfficialPageRegistry.confirmedUrl(
            "aniworld:detektiv-conan", "Detektiv Conan"
        ))
        assertNull(AppleTvOfficialPageRegistry.confirmedUrl(
            "aniworld:detektiv-conan", "Detektiv Conan: Spin-off"
        ))
        assertNull(AppleTvOfficialPageRegistry.confirmedUrl("other:detektiv-conan", "Detektiv Conan"))
    }

    @Test fun germanJustWatchReferenceImportsOnlyPublicAppleCardsWithoutAvailabilityClaim() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "conan", null, null, "Detektiv Conan", null, null, null, "", null, null,
            null, null, null, 1
        )))
        dao.upsertProviderReference(ProviderReferenceEntity(
            "conan", "Apple TV", url, "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", url, 1, "DE"
        ))
        val html = """
            <h1>Detektiv Conan</h1>
            <select data-testid="accessory-button-select"><option>Staffel 1</option><option>Staffel 2</option></select>
            <a href="https://tv.apple.com/de/episode/kleiner-mann/umc.cmc.ep2?showId=$showId">
              <div class="tag">FOLGE 2</div><div class="title">Kleiner Mann ganz groß</div>
              <div class="description">Öffentlich bestätigte Folge.</div><div class="metadata">24 Min.</div>
            </a>
        """.trimIndent()
        val client = AppleTvCatalogClient(ProviderMetadataTransport { requested, _ ->
            assertEquals(url, requested)
            MetadataHttpResponse(200, html, url, "text/html")
        }, clock)
        val result = AppleTvCatalogImporter(dao, client, clock)
            .importFromJustWatch("conan", setOf("Detektiv Conan"))
        assertTrue(result is HistoricalImportResult.Success)
        val release = dao.episodeReleasesForAnime("conan").single()
        assertEquals("CATALOGUED", release.releaseStatus)
        assertEquals("APPLE_TV_PUBLIC_PAGE_PARTIAL", release.metadataSource)
        assertEquals(null, release.releaseLanguage)
        assertEquals(2, release.episodeNumber)
        assertEquals("Öffentlich bestätigte Folge.", release.providerEpisodeDescription)
        assertTrue(release.providerUrl!!.contains("/de/episode/"))
        assertEquals(listOf(1), dao.providerMappingsForAnimeProvider("conan", "Apple TV").map { it.canonicalSeasonNumber })
    }

    @Test fun appleTvPlusReferenceIsNotAppleTvStoreEvidence() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "conan", null, null, "Detektiv Conan", null, null, null, "", null, null,
            null, null, null, 1
        )))
        dao.upsertProviderReference(ProviderReferenceEntity(
            "conan", "Apple TV+", url, "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", url, 1, "DE"
        ))
        val result = AppleTvCatalogImporter(dao, AppleTvCatalogClient(ProviderMetadataTransport { _, _ ->
            error("Must not fetch Apple TV+ as Store")
        }, clock), clock).importFromJustWatch("conan", setOf("Detektiv Conan"))
        assertEquals("APPLE_TV_JUSTWATCH_DE_REFERENCE_MISSING", (result as HistoricalImportResult.Failed).code)
    }

    @Test fun officialGermanPageWorksWithoutJustWatchAndStaysCataloguedOnly() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "conan", null, null, "Detektiv Conan", null, null, null, "", null, null,
            null, null, null, 1
        )))
        val html = """
            <h1>Detektiv Conan</h1>
            <select data-testid="accessory-button-select"><option>Staffel 1</option></select>
            <a href="https://tv.apple.com/de/episode/kleiner-mann/umc.cmc.ep2?showId=$showId">
              <div class="tag">FOLGE 2</div><div class="title">Kleiner Mann ganz groß</div>
            </a>
            <script type="application/json">{"seasons":[{"episodeCount":41,"id":"umc.cmc.seasonone","seasonNumber":1,"title":"Staffel 1"},{"episodeCount":27,"id":"umc.cmc.seasonthirtyone","seasonNumber":31,"title":"Staffel 31"}]}</script>
        """.trimIndent()
        val importer = AppleTvCatalogImporter(dao, AppleTvCatalogClient(
            ProviderMetadataTransport { _, _ -> MetadataHttpResponse(200, html, url, "text/html") }, clock
        ), clock)
        assertTrue(importer.importFromOfficialPage("conan", url, setOf("Detektiv Conan")) is HistoricalImportResult.Success)
        assertEquals("CATALOGUED", dao.episodeReleasesForAnime("conan").single().releaseStatus)
        assertEquals(listOf(1, 31), dao.providerMappingsForAnimeProvider("conan", "Apple TV")
            .map { it.providerSeasonNumber })
        assertEquals("umc.cmc.seasonthirtyone", dao.providerMappingsForAnimeProvider("conan", "Apple TV")
            .last().providerSeasonId)
        assertEquals(
            AppleTvCatalogImporter.OFFICIAL_PUBLIC_SOURCE,
            dao.providerReferences("conan").single().source
        )
    }

    @Test fun publicMetadataPaginationImportsEveryConfirmedSeasonEpisode() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "conan", null, null, "Detektiv Conan", null, null, null, "", null, null,
            null, null, null, 1
        )))
        val seasonOne = "umc.cmc.seasonone"
        val seasonTwo = "umc.cmc.seasontwo"
        val html = """
            <h1>Detektiv Conan</h1>
            <select data-testid="accessory-button-select"><option>Staffel 1</option><option>Staffel 2</option></select>
            <a href="https://tv.apple.com/de/episode/first/umc.cmc.ep1?showId=$showId">
              <div class="tag">FOLGE 1</div><div class="title">Erste Folge</div>
            </a>
            <script type="application/json">{"seasons":[{"episodeCount":2,"id":"$seasonOne","seasonNumber":1,"title":"Staffel 1"},{"episodeCount":1,"id":"$seasonTwo","seasonNumber":2,"title":"Staffel 2"}],"requiredParamsMap":{"Default":{"utscf":"public-config","utsk":"ephemeral","caller":"web","sf":"143443","v":"100","pfm":"web","locale":"de-DE"}}}</script>
        """.trimIndent()
        fun episode(id: String, season: Int, seasonId: String, number: Int) = """
            {"id":"$id","showId":"$showId","seasonId":"$seasonId","seasonNumber":$season,"episodeNumber":$number,"title":"Folge $number","description":"Bestätigt","duration":1440,"url":"https://tv.apple.com/de/episode/confirmed/$id?showId=$showId"}
        """.trimIndent()
        val firstPage = """{"data":{"totalEpisodeCount":3,"episodes":[
            ${episode("umc.cmc.ep1", 1, seasonOne, 1)},
            ${episode("umc.cmc.ep2", 1, seasonOne, 2)}],"playables":{"ignored":true}}} """
        val secondPage = """{"data":{"totalEpisodeCount":3,"episodes":[
            ${episode("umc.cmc.ep3", 2, seasonTwo, 1)}]}} """
        val requestedPages = mutableListOf<String>()
        val client = AppleTvCatalogClient(ProviderMetadataTransport { requested, _ ->
            if (requested == url) MetadataHttpResponse(200, html, url, "text/html")
            else {
                assertTrue(AppleTvPublicEpisodeMetadata.isEpisodeResponseUrl(requested, showId))
                requestedPages += requested
                val body = when {
                    requested.contains("nextToken=0%3A2") -> firstPage
                    requested.contains("nextToken=2%3A2") -> secondPage
                    else -> error("Unexpected page")
                }
                MetadataHttpResponse(200, body, requested, "application/json")
            }
        }, clock, pageSize = 2)
        val catalog = client.load(url, setOf("Detektiv Conan"))
        assertEquals(AppleTvCatalogCompleteness.COMPLETE, catalog.completeness)
        assertEquals(3, catalog.episodes.size)
        assertEquals(listOf(1, 1, 2), catalog.episodes.map { it.seasonNumber })
        assertEquals(2, requestedPages.size)
        val result = AppleTvCatalogImporter(dao, client, clock)
            .importFromOfficialPage("conan", url, setOf("Detektiv Conan"))
        assertTrue(result is HistoricalImportResult.Success)
        assertEquals(3, dao.episodeReleasesForAnime("conan").size)
        assertTrue(dao.episodeReleasesForAnime("conan").all {
            it.metadataSource == "APPLE_TV_PUBLIC_PAGE_COMPLETE" && it.releaseStatus == "CATALOGUED"
        })
    }
}
