package de.anisentinel.app.ui

import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPreferenceUiPolicyTest {
    @Test fun seasonOptionsContainOnlyConfirmedGermanMappings() {
        val mappings = listOf(
            mapping(1, "Crunchyroll", available = true),
            mapping(1, "ADN", available = false),
            mapping(1, "Netflix", available = true, region = "US"),
            mapping(2, "ADN", available = true)
        )

        assertEquals(listOf("Crunchyroll"), ProviderPreferenceUiPolicy.providersForSeason(1, mappings))
    }

    @Test fun animeOptionsAreUnionOfConfirmedGermanMappings() {
        val mappings = listOf(
            mapping(1, "Crunchyroll", true),
            mapping(2, "ADN", true),
            mapping(3, "Netflix", false)
        )

        assertEquals(listOf("ADN", "Crunchyroll"), ProviderPreferenceUiPolicy.providersForAnime(mappings))
    }

    @Test fun directProviderReferencesAreSelectableBeforeCatalogImport() {
        val references = listOf(
            reference("Crunchyroll", "https://www.crunchyroll.com/watch/EPISODE"),
            reference("Animation Digital Network", "https://animationdigitalnetwork.com/de/video/1-title"),
            reference("Crunchyroll Amazon Channel", "https://amazon.example/channel"),
            reference("Netflix", "https://netflix.example/title"),
            reference("Disney Plus", "https://disney.example/title"),
            reference("ANIVERSE Amazon Channel", "https://amazon.example/aniverse")
        )

        assertEquals(
            listOf("ADN", "ANIVERSE Amazon Channel", "Crunchyroll", "Disney+", "Netflix"),
            ProviderPreferenceUiPolicy.providersForAnime(emptyList(), references)
        )
    }

    @Test fun unavailableSavedSeasonPreferenceIsInvalid() {
        val mappings = listOf(mapping(2, "ADN", false), mapping(2, "Crunchyroll", true))

        assertTrue(ProviderPreferenceUiPolicy.isInvalidSeasonPreference("ADN", 2, mappings))
        assertFalse(ProviderPreferenceUiPolicy.isInvalidSeasonPreference("Crunchyroll", 2, mappings))
    }

    @Test fun animePreferenceBecomesInvalidWhenProviderHasNoConfirmedGermanSeason() {
        val mappings = listOf(mapping(1, "ADN", false), mapping(1, "Crunchyroll", true))

        assertTrue(ProviderPreferenceUiPolicy.isInvalidAnimePreference("ADN", mappings))
        assertFalse(ProviderPreferenceUiPolicy.isInvalidAnimePreference("Crunchyroll", mappings))
    }

    @Test fun selectedCrunchyrollDoesNotUseCalendarNavigationUntilCatalogIsLoaded() {
        assertEquals(
            emptyList<Int>(),
            ProviderPreferenceUiPolicy.seasonsForProvider(
                "Crunchyroll", listOf(1, 2), emptyList(), emptyList()
            )
        )
    }

    @Test fun selectedCrunchyrollUsesMappingsAndHistoricalRowsWithoutLosingASeason() {
        val releases = listOf(
            release(2, 1, "Crunchyroll"),
            release(3, 1, "ADN")
        )
        assertEquals(
            listOf(1, 2),
            ProviderPreferenceUiPolicy.seasonsForProvider(
                "Crunchyroll", listOf(1, 2, 3),
                listOf(mapping(1, "Crunchyroll", true)), releases
            )
        )
    }

    @Test fun automaticCrunchyrollAndExplicitCrunchyrollSeeTheSameCatalogEpisodes() {
        val releases = listOf(
            release(2, 1, "Crunchyroll"),
            release(2, 2, "Crunchyroll"),
            release(2, 1, "ADN")
        )
        val automatic = ProviderPreferenceUiPolicy.releasesForProviderSeason(releases, 2, "Crunchyroll")
        val explicit = ProviderPreferenceUiPolicy.releasesForProviderSeason(releases, 2, "Crunchyroll")

        assertEquals(listOf(1, 2), ProviderPreferenceUiPolicy.episodeCatalogRows(automatic, false).mapNotNull { it.episodeNumber })
        assertEquals(automatic, explicit)
    }

    @Test fun automaticCrunchyrollAndExplicitCrunchyrollUseTheSameSeasonStructure() {
        val mappings = listOf(mapping(1, "Crunchyroll", true), mapping(2, "Crunchyroll", true))
        val releases = listOf(release(1, 1, "Crunchyroll"), release(2, 1, "Crunchyroll"))

        val automatic = ProviderPreferenceUiPolicy.seasonsForProvider(
            "Crunchyroll", listOf(1, 2, 3), mappings, releases
        )
        val explicit = ProviderPreferenceUiPolicy.seasonsForProvider(
            "Crunchyroll", listOf(1, 2, 3), mappings, releases
        )

        assertEquals(listOf(1, 2), automatic)
        assertEquals(automatic, explicit)
    }

    @Test fun airingAdjacentAniWorldSeasonDoesNotBecomeProviderCatalogSeason() {
        val mappings = listOf(mapping(1, "Crunchyroll", true))
        val releases = listOf(
            release(1, 12, "Crunchyroll"),
            scheduledRelease(2, 11)
        )

        assertEquals(
            listOf(1),
            ProviderPreferenceUiPolicy.seasonsForProvider(
                "Crunchyroll", listOf(1, 2), mappings, releases
            )
        )
    }

    @Test fun automaticModeMergesOnlyConfirmedProviderCatalogs() {
        val mappings = listOf(mapping(1, "Crunchyroll", true), mapping(2, "ADN", true))
        val releases = listOf(release(3, 1, "Crunchyroll"), scheduledRelease(32, 1))

        assertEquals(
            listOf(1, 2, 3),
            ProviderPreferenceUiPolicy.seasonsForProvider(null, (1..32).toList(), mappings, releases)
        )
        assertEquals(
            listOf(1),
            ProviderPreferenceUiPolicy.releasesForProviderSeason(releases, 3, null)
                .mapNotNull { it.episodeNumber }
        )
    }

    @Test fun distantCalendarSeasonIsNotAppendedToCrunchyrollCatalog() {
        val mappings = (1..6).map { mapping(it, "Crunchyroll", true) }
        val releases = (1..6).map { release(it, 1, "Crunchyroll") } + scheduledRelease(32, 47)

        assertEquals(
            (1..6).toList(),
            ProviderPreferenceUiPolicy.seasonsForProvider(
                "Crunchyroll", (1..6).toList() + 32, mappings, releases
            )
        )
    }

    @Test fun adnScopingRemainsUnchanged() {
        val releases = listOf(release(1, 1, "ADN"), release(1, 1, "Crunchyroll"))

        assertEquals(
            listOf("ADN"),
            ProviderPreferenceUiPolicy.releasesForProviderSeason(releases, 1, "ADN")
                .mapNotNull { it.provider }
        )
    }

    @Test fun conanCatalogSeasonsKeepEqualSeasonNumbersSeparate() {
        val mappings = listOf(
            mapping(1, "Crunchyroll", true, catalogId = "GW4HM7NV3", label = "HD Remaster"),
            mapping(1, "Crunchyroll", true, catalogId = "G6JQVM3ER", label = "Detective Conan")
        )

        val options = ProviderPreferenceUiPolicy.catalogSeasonsForProvider("Crunchyroll", mappings)

        assertEquals(2, options.size)
        assertEquals(setOf("GW4HM7NV3", "G6JQVM3ER"), options.map { it.catalogId }.toSet())
        assertEquals(2, options.map { it.key }.distinct().size)
    }

    @Test fun automaticModeUnionsConfirmedProviderCatalogsWithoutDuplicateSeasonChips() {
        val mappings = listOf(
            mapping(1, "Crunchyroll", true, catalogId = "first"),
            mapping(1, "Crunchyroll", true, catalogId = "second"),
            mapping(1, "Apple TV", true, catalogId = "apple"),
            mapping(2, "Apple TV", true, catalogId = "apple")
        )
        assertEquals(4, ProviderPreferenceUiPolicy.automaticUnionSeasons(mappings).size)
        assertEquals(listOf(1, 2), ProviderPreferenceUiPolicy.catalogSeasonsForProvider("Apple TV", mappings).map { it.seasonNumber })
        assertEquals(2, ProviderPreferenceUiPolicy.catalogSeasonsForProvider("Crunchyroll", mappings).size)
        assertEquals("Apple TV", ProviderPreferenceUiPolicy.selectableReference("Apple TV Store"))
        assertEquals(null, ProviderPreferenceUiPolicy.selectableReference("Apple TV+"))
    }

    @Test fun conanEpisodeCollisionIsScopedByCatalogIdentity() {
        val oldCatalog = release(1, 1, "Crunchyroll", "GW4HM7NV3")
        val currentCatalog = release(1, 1, "Crunchyroll", "G6JQVM3ER")
        val appleCatalog = release(1, 1, "Apple TV", "umc.cmc.o4e5fbtkmgjivlpghedf8a6x")

        assertEquals(
            setOf(oldCatalog, currentCatalog),
            ProviderPreferenceUiPolicy.releasesForProviderSeason(
                listOf(oldCatalog, currentCatalog, appleCatalog), 1, "Crunchyroll"
            ).toSet()
        )
        assertEquals(
            setOf(oldCatalog, currentCatalog, appleCatalog),
            ProviderPreferenceUiPolicy.releasesForProviderSeason(
                listOf(oldCatalog, currentCatalog, appleCatalog), 1, null
            ).toSet()
        )

        assertEquals(
            listOf(oldCatalog),
            ProviderPreferenceUiPolicy.releasesForProviderSeason(
                listOf(oldCatalog, currentCatalog), 1, "Crunchyroll", "GW4HM7NV3"
            )
        )
        assertEquals(
            listOf(currentCatalog),
            ProviderPreferenceUiPolicy.releasesForProviderSeason(
                listOf(oldCatalog, currentCatalog), 1, "Crunchyroll", "G6JQVM3ER"
            )
        )
    }

    @Test fun conanThreeCatalogsKeepEpisodeIdentitySeparateUntilCanonicalMatchIsProven() {
        val first = release(1, 2, "Crunchyroll", "GW4HM7NV3")
            .copy(episodeTitle = "Kleiner Mann ganz gro?")
        val second = release(1, 2, "Crunchyroll", "G6JQVM3ER")
            .copy(episodeTitle = "Kleiner Mann ganz gro?", providerUrl = "https://crunchyroll.example/other")
        val apple = release(1, 2, "Apple TV", "umc.cmc.o4e5fbtkmgjivlpghedf8a6x")
            .copy(episodeTitle = "Kleiner Mann ganz gro?", providerUrl = "https://tv.apple.com/de/episode/confirmed")
        val different = release(1, 2, "Apple TV", "other")
            .copy(episodeTitle = "Anderer Episodentitel")
        val merged = ProviderPreferenceUiPolicy.confirmedEpisodeCards(listOf(first, second, apple))
        assertEquals(3, merged.size)
        assertEquals(3, merged.flatMap { it.releases }.mapNotNull { it.providerUrl }.distinct().size)
        val unsafe = ProviderPreferenceUiPolicy.confirmedEpisodeCards(listOf(first, different))
        assertEquals(2, unsafe.size)
    }

    @Test fun canonicalSeason32KeepsOneCardForProviderLanguageVariants() {
        val german = release(32, 1214, "Crunchyroll", "G6JQVM3ER").copy(
            sourceReleaseId = "crunchyroll-history:anime:G6JQVM3ER:s1:e1214:ger_sub",
            episodeTitle = "Die Wahrheit",
            providerUrl = "https://www.crunchyroll.com/watch/G123/episode"
        )
        val english = german.copy(sourceReleaseId =
            "crunchyroll-history:anime:G6JQVM3ER:s1:e1214:eng_sub")
        val cards = ProviderPreferenceUiPolicy.confirmedEpisodeCards(listOf(german, english))
        assertEquals(1, cards.size)
        assertEquals(2, cards.single().releases.size)
        assertEquals(32, cards.single().seasonNumber)
    }

    private fun mapping(
        season: Int, provider: String, available: Boolean, region: String = "DE",
        catalogId: String = provider, label: String? = null
    ) = ProviderSeasonMappingEntity(
        "anime", season, provider, season, catalogId, null, null, region, available, 1,
        providerSeasonLabel = label, providerCatalogId = catalogId
    )

    private fun reference(provider: String, url: String) =
        ProviderReferenceEntity("anime", provider, url, "JUSTWATCH", null, 1)

    private fun release(
        season: Int, episode: Int, provider: String, catalogId: String? = null
    ) = EpisodeReleaseEntity(
        sourceReleaseId = if (catalogId == null) "${provider.lowercase()}:s$season:e$episode"
            else "crunchyroll-history:anime:$catalogId:s$season:e$episode:ger_sub",
        animeId = "anime",
        episodeNumber = episode,
        episodeTitle = null,
        expectedAt = 1,
        provider = provider,
        metadataSource = "TEST",
        sourceUrl = "https://example.test/${catalogId ?: provider}",
        providerUrl = "https://example.test/$provider/$episode",
        fetchedAt = 1,
        seasonNumber = season,
        isHistoricalImport = true
    )

    private fun scheduledRelease(season: Int, episode: Int) = EpisodeReleaseEntity(
        sourceReleaseId = "aniworld:s$season:e$episode",
        animeId = "anime",
        episodeNumber = episode,
        episodeTitle = null,
        expectedAt = 1,
        provider = null,
        metadataSource = "ANIWORLD_CALENDAR",
        sourceUrl = "https://example.test/calendar",
        providerUrl = null,
        fetchedAt = 1,
        seasonNumber = season,
        isHistoricalImport = false
    )
}
