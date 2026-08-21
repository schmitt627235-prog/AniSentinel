package de.anisentinel.app.ui

import de.anisentinel.app.data.local.AnimeSeasonEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.ProviderSeasonMappingEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalSeasonPolicyTest {
    @Test fun verifiedSeasonSuppressesPhantomLegacySeasons() {
        val seasons = listOf(1, 2, 3, 4).map {
            AnimeSeasonEntity("anime", it, if (it == 1) "CRUNCHYROLL_PUBLIC" else "RELEASE_BACKFILL", 1)
        }
        assertEquals(listOf(1), CanonicalSeasonPolicy.visibleSeasons(seasons, emptyList()))
    }

    @Test fun ambiguousLegacyOnlySeasonsDoNotCreateChips() {
        val seasons = listOf(1, 2, 3, 4).map { AnimeSeasonEntity("anime", it, "RELEASE_BACKFILL", 1) }
        assertEquals(emptyList<Int>(), CanonicalSeasonPolicy.visibleSeasons(seasons, emptyList()))
    }

    @Test fun onePieceStyleProviderSubsetsProduceGlobalCanonicalUnion() {
        val mappings = listOf(
            mapping(1, "Crunchyroll"), mapping(2, "Crunchyroll"), mapping(3, "Crunchyroll"),
            mapping(4, "ADN"), mapping(5, "ADN"), mapping(6, "ADN", available = false)
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), CanonicalSeasonPolicy.visibleSeasons(emptyList(), mappings))
    }

    @Test fun singleLegacySeasonRemainsCompatibleUntilVerification() {
        assertEquals(
            listOf(1),
            CanonicalSeasonPolicy.visibleSeasons(listOf(AnimeSeasonEntity("anime", 1, "RELEASE_BACKFILL", 1)), emptyList())
        )
    }

    @Test fun realProviderHistoryRestoresLegacySeasonChoicesWithoutTrustingRawCalendarRows() {
        val seasons = listOf(1, 2, 3, 4).map {
            AnimeSeasonEntity("anime", it, "RELEASE_BACKFILL", 1)
        }
        val providerHistory = listOf(1, 2, 3).map { season -> historicalRelease(season) }

        assertEquals(
            listOf(1, 2, 3),
            CanonicalSeasonPolicy.visibleSeasons(seasons, emptyList(), providerHistory)
        )
    }

    @Test fun activeCalendarSeasonIsKeptBesideConfirmedProviderHistory() {
        assertEquals(
            listOf(1, 2, 23),
            CanonicalSeasonPolicy.visibleSeasons(
                emptyList(),
                emptyList(),
                listOf(historicalRelease(1), historicalRelease(2)),
                activeSeasonNumber = 23
            )
        )
    }

    private fun mapping(season: Int, provider: String, available: Boolean = true) =
        ProviderSeasonMappingEntity("anime", season, provider, season, null, null, null, "DE", available, 1)

    private fun historicalRelease(season: Int) = EpisodeReleaseEntity(
        sourceReleaseId = "provider-history:s$season:e1",
        animeId = "anime",
        episodeNumber = 1,
        episodeTitle = null,
        expectedAt = 1,
        provider = "ADN",
        metadataSource = "ADN_PUBLIC_METADATA",
        sourceUrl = "https://provider.example/catalog",
        providerUrl = "https://provider.example/episode",
        fetchedAt = 1,
        seasonNumber = season,
        releaseStatus = "AVAILABLE",
        releaseLanguage = "GER_DUB",
        isHistoricalImport = true,
        historicalReleasedAt = 1
    )
}
