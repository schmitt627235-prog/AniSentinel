package de.anisentinel.app.ui

import de.anisentinel.app.data.local.AnimeSeasonEntity
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

    private fun mapping(season: Int, provider: String, available: Boolean = true) =
        ProviderSeasonMappingEntity("anime", season, provider, season, null, null, null, "DE", available, 1)
}
