package de.anisentinel.app.data.provider

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AkibaPassPublicCatalogParserTest {
    @Test fun matchesExactAliasAndSeasonWithoutSpinOffOrOva() {
        val products = listOf(
            AkibaPassProduct("haikyu-omu-season-1-1", "Haikyu!! (OmU) - Season 1.1", "https://www.akibapass.tv/products/haikyu-omu-season-1-1"),
            AkibaPassProduct("haikyu-de-season-1-2", "Haikyu!! (DE) - Season 1.2", "https://www.akibapass.tv/products/haikyu-de-season-1-2"),
            AkibaPassProduct("haikyu-season-2", "Haikyu!! - Season 2", "https://www.akibapass.tv/products/haikyu-season-2"),
            AkibaPassProduct("haikyu-ova-season-1", "Haikyu!! OVA - Season 1", "https://www.akibapass.tv/products/haikyu-ova-season-1"),
            AkibaPassProduct("haikyu-junior-season-1", "Haikyu!! Junior - Season 1", "https://www.akibapass.tv/products/haikyu-junior-season-1")
        )
        assertEquals(
            listOf("haikyu-omu-season-1-1", "haikyu-de-season-1-2"),
            AkibaPassPublicCatalogParser.matchingSeasonProducts(products, setOf("Haikyu!!"), 1).map { it.id }
        )
    }

    @Test fun productIndexKeepsOnlyActualProducts() {
        val html = """
            <li class="js-collection-item item-type-package">
              <a class="browse-item-link" href="https://www.akibapass.tv/products/haikyu-omu-season-1-1"></a>
              <div class="browse-item-title"><strong>Haikyu!! (OmU) - Season 1.1</strong></div>
            </li>
            <li class="js-collection-item item-type-video">
              <a class="browse-item-link" href="https://www.akibapass.tv/products/not-a-product"></a>
            </li>
        """.trimIndent()
        val products = AkibaPassPublicCatalogParser.products(html)
        assertEquals(1, products.size)
        assertEquals("haikyu-omu-season-1-1", products.single().id)
    }

    @Test fun parsesProviderPartAndDirectEpisodeLinkWithoutAssumingAvailability() {
        val html = """
            <h1 class="collection-title">Haikyu!! (OmU) - Season 1.1</h1>
            <section class="episode-container"><ul>
              <li class="js-collection-item item-type-video" data-item-id="3182076">
                <a class="browse-item-link" href="/packages/haikyu-omu-season-1-1/videos/haikyu-s1e01-ende-und-anfang-omu"></a>
                <div class="browse-item-title"><strong>Haikyu!! - S1E01 - Ende und Anfang (OmU)</strong></div>
                <div class="duration-container">24:32</div>
                <div class="tooltip"><div class="transparent"><p>Sprache: Japanisch / Untertitel: Deutsch</p><p>Shoyo beginnt seine Volleyballreise.</p></div></div>
              </li>
              <li class="js-collection-item item-type-video" data-item-id="trailer">
                <a class="browse-item-link" href="/packages/haikyu-omu-season-1-1/videos/haikyu-s1-trailer-de"></a>
                <div class="browse-item-title"><strong>Trailer</strong></div>
              </li>
            </ul></section>
        """.trimIndent()
        val season = requireNotNull(AkibaPassPublicCatalogParser.season(
            html, "https://www.akibapass.tv/products/haikyu-omu-season-1-1"
        ))
        assertEquals(1, season.seasonNumber)
        assertEquals(1, season.partNumber)
        assertEquals("Season 1.1", season.label)
        assertEquals(1, season.episodes.size)
        assertEquals("GER_SUB", season.episodes.single().language)
        assertEquals("Shoyo beginnt seine Volleyballreise.", season.episodes.single().description)
        assertEquals("24:32", season.episodes.single().duration)
        assertEquals("https://www.akibapass.tv/packages/haikyu-omu-season-1-1/videos/haikyu-s1e01-ende-und-anfang-omu", season.episodes.single().url)
    }

    @Test fun rejectsWrongSeasonAndForeignEpisodeHosts() {
        val html = """
            <h1 class="collection-title">Fruits Basket (DE) - Season 2</h1>
            <section class="episode-container"><ul>
              <li class="js-collection-item item-type-video" data-item-id="1">
                <a class="browse-item-link" href="https://foreign.example/packages/fruits/videos/episode-1"></a>
                <div class="browse-item-title"><strong>Fruits Basket - S2E01 - Test (DE)</strong></div>
              </li>
              <li class="js-collection-item item-type-video" data-item-id="2">
                <a class="browse-item-link" href="/packages/fruits-basket/videos/episode-2"></a>
                <div class="browse-item-title"><strong>Fruits Basket - S1E02 - Test (DE)</strong></div>
              </li>
            </ul></section>
        """.trimIndent()
        assertNull(AkibaPassPublicCatalogParser.season(
            html, "https://www.akibapass.tv/products/fruits-basket-de-season-2"
        ))
    }

    @Test fun fruitsBasketPackageIsConfirmedOnlyWithCheckoutAndPastAvailabilityDate() {
        val html = """
            <h1 class="collection-title">Fruits Basket (DE) - Season 1.1</h1>
            <div class="collection-description">Ab 02.02.2023 verfügbar! Sprache: Deutsch</div>
            <a href="https://www.akibapass.tv/checkout/fruits-basket-de-season-1-1?rent=1">Leihen</a>
            <section class="episode-container"><ul>
              <li class="js-collection-item item-type-video" data-item-id="fruit-1">
                <a class="browse-item-link" href="/packages/fruits-basket-de-season-1-1/videos/fruits-basket-s1e01-de"></a>
                <div class="browse-item-title"><strong>Fruits Basket - S1E01 - Wir sehen uns (DE)</strong></div>
                <div class="duration-container">23:40</div>
                <div class="tooltip"><div class="transparent">Sprache: Deutsch / Beschreibung</div></div>
              </li>
            </ul></section>
        """.trimIndent()
        val season = requireNotNull(AkibaPassPublicCatalogParser.season(
            html, "https://www.akibapass.tv/products/fruits-basket-de-season-1-1"
        ))
        assertTrue(season.purchasable)
        assertEquals(LocalDate.of(2023, 2, 2), season.availableFrom)
        assertEquals("GER_DUB", season.episodes.single().language)
        assertEquals("23:40", season.episodes.single().duration)
    }

    @Test fun haikyuListedEpisodeWithoutCheckoutIsNotConfirmedAvailable() {
        val html = """
            <h1 class="collection-title">Haikyu!! (OmU) - Season 1.1</h1>
            <section class="episode-container"><ul>
              <li class="js-collection-item item-type-video" data-item-id="haikyu-1">
                <a class="browse-item-link" href="/packages/haikyu-omu-season-1-1/videos/haikyu-s1e01-omu"></a>
                <div class="browse-item-title"><strong>Haikyu!! - S1E01 - Ende und Anfang (OmU)</strong></div>
              </li>
            </ul></section>
        """.trimIndent()
        val season = requireNotNull(AkibaPassPublicCatalogParser.season(
            html, "https://www.akibapass.tv/products/haikyu-omu-season-1-1"
        ))
        assertFalse(season.purchasable)
        assertEquals("GER_SUB", season.episodes.single().language)
    }
}
