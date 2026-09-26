package de.anisentinel.app.data.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.anisentinel.app.data.local.AniSentinelDatabase
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.domain.provider.ProviderMetadataProbeRequest
import de.anisentinel.app.domain.provider.ProviderMetadataProbeResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AkibaPassCatalogImporterTest {
    private lateinit var database: AniSentinelDatabase
    private val clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun importsTwoRealTitleShapesWithoutMixingSeasonsOrInventingAvailability() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf("fruits" to "Fruits Basket", "haikyu" to "Haikyu!!").map { (id, title) ->
            AnimeEntity(id, null, null, title, null, null, null, "", null, null, null, null, null, 1)
        })
        val index = """
            <li class="js-collection-item item-type-package"><a class="browse-item-link" href="https://www.akibapass.tv/products/fruits-basket-de-season-1-1"></a><div class="browse-item-title"><strong>Fruits Basket (DE) - Season 1.1</strong></div></li>
            <li class="js-collection-item item-type-package"><a class="browse-item-link" href="https://www.akibapass.tv/products/haikyu-omu-season-1-1"></a><div class="browse-item-title"><strong>Haikyu!! (OmU) - Season 1.1</strong></div></li>
            <li class="js-collection-item item-type-package"><a class="browse-item-link" href="https://www.akibapass.tv/products/haikyu-ova-season-1"></a><div class="browse-item-title"><strong>Haikyu!! OVA - Season 1</strong></div></li>
        """.trimIndent()
        val calls = mutableListOf<String>()
        val transport = ProviderMetadataTransport { url, headers ->
            calls += url
            assertTrue(headers["User-Agent"].orEmpty().startsWith("AniSentinel/"))
            assertEquals("text/html", headers["Accept"])
            val body = when {
                url.endsWith("/products") -> index
                url.endsWith("/fruits-basket-de-season-1-1") -> product("Fruits Basket (DE) - Season 1.1", "fruits-basket-de-season-1-1", "Fruits Basket", "DE", "fruit-1", "Sprache: Deutsch", "Fruits Basket startet.")
                url.endsWith("/haikyu-omu-season-1-1") -> product("Haikyu!! (OmU) - Season 1.1", "haikyu-omu-season-1-1", "Haikyu!!", "OmU", "haikyu-1", "Sprache: Japanisch / Untertitel: Deutsch", "Hinata startet.")
                else -> error("Unexpected URL: $url")
            }
            MetadataHttpResponse(200, body, url, "text/html")
        }
        val importer = AkibaPassCatalogImporter(dao, AkibaPassCatalogClient(transport, clock), clock)
        assertTrue(importer.importByTitle("fruits", setOf("Fruits Basket")) is HistoricalImportResult.Success)
        assertTrue(importer.importByTitle("haikyu", setOf("Haikyu!!")) is HistoricalImportResult.Success)
        val fruit = dao.episodeReleasesForAnime("fruits").single()
        val haikyu = dao.episodeReleasesForAnime("haikyu").single()
        assertEquals("Fruits Basket startet.", fruit.providerEpisodeDescription)
        assertEquals("23:40", fruit.providerEpisodeDuration)
        assertEquals("GER_DUB", fruit.releaseLanguage)
        assertEquals("GER_SUB", haikyu.releaseLanguage)
        assertTrue(haikyu.providerUrl!!.contains("/packages/haikyu-omu-season-1-1/videos/"))
        assertEquals("Season 1.1", dao.providerMappingsForAnimeProvider("fruits", "AKIBA PASS").single().providerSeasonLabel)
        assertEquals(3, calls.size) // shared index, one product page per verified title
    }

    @Test fun probeChecksLanguageAndPurchaseEvidenceBeforeConfirming() = runBlocking {
        val productUrl = "https://www.akibapass.tv/products/haikyu-omu-season-1-1"
        val index = """
            <li class="js-collection-item item-type-package"><a class="browse-item-link" href="$productUrl"></a><div class="browse-item-title"><strong>Haikyu!! (OmU) - Season 1.1</strong></div></li>
        """.trimIndent()
        val page = product(
            "Haikyu!! (OmU) - Season 1.1", "haikyu-omu-season-1-1", "Haikyu!!", "OmU",
            "haikyu-1", "Sprache: Japanisch / Untertitel: Deutsch", "Hinata startet."
        )
        val transport = ProviderMetadataTransport { url, _ ->
            MetadataHttpResponse(200, if (url.endsWith("/products")) index else page, url, "text/html")
        }
        val adapter = AkibaPassMetadataAdapter(AkibaPassCatalogClient(transport, clock), clock)
        val subRequest = ProviderMetadataProbeRequest("haikyu", "Haikyu!!", 1, 1, "GER_SUB")
        val sub = adapter.probe(subRequest, null)
        assertTrue(sub is ProviderMetadataProbeResult.Available)
        assertEquals("GER_SUB", (sub as ProviderMetadataProbeResult.Available).availability.let {
            if (it.germanSubAvailable == true) "GER_SUB" else null
        })
        assertTrue(adapter.probe(subRequest.copy(expectedLanguage = "GER_DUB"), null) is ProviderMetadataProbeResult.NotAvailableYet)
    }

    private fun product(
        title: String, id: String, name: String, language: String, episodeId: String,
        languageText: String, description: String
    ): String = """
        <h1 class="collection-title">$title</h1>
        <div class="collection-description">Ab 02.02.2023 verf?gbar!</div>
        <a href="https://www.akibapass.tv/checkout/$id?rent=1">Leihen</a>
        <section class="episode-container"><ul>
          <li class="js-collection-item item-type-video" data-item-id="$episodeId">
            <a class="browse-item-link" href="/packages/$id/videos/$id-s1e01"></a>
            <div class="browse-item-title"><strong>$name - S1E01 - Beginn ($language)</strong></div>
            <div class="duration-container">23:40</div>
            <div class="tooltip"><div class="transparent"><p>$languageText</p><p>$description</p></div></div>
          </li>
        </ul></section>
    """.trimIndent()
}
