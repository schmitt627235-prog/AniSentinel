package de.anisentinel.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrunchyrollSeasonStructureTest {
    @Test fun confirmedAzurAssignmentRejectsMainSeriesCatalog() {
        assertEquals(
            listOf("GQWH0MXPQ"),
            ConfirmedCrunchyrollCatalogPolicy.forAnime(
                "aniworld:azur-lane-slow-ahead", listOf("G9VHN9P49")
            )
        )
    }

    @Test fun confirmedConanAssignmentAlwaysKeepsBothCatalogs() {
        assertEquals(
            listOf("GW4HM7NV3", "G6JQVM3ER"),
            ConfirmedCrunchyrollCatalogPolicy.forAnime(
                "aniworld:detektiv-conan", listOf("GW4HM7NV3")
            )
        )
    }
    private fun episode(number: Int, title: String) = CrunchyrollCatalogEpisode(
        seriesId = "series",
        seasonId = "season",
        seasonNumber = 1,
        episodeId = "episode-$number",
        episodeNumber = number,
        sequenceNumber = null,
        title = null,
        audioLocale = "ja-JP",
        subtitleLocales = setOf("de-DE"),
        availableAt = null,
        availabilityStatus = null,
        episodeUrl = "https://www.crunchyroll.com/watch/episode-$number",
        seasonTitle = title
    )

    @Test
    fun languageVariantsCollapseIntoOneContentSeason() {
        val numbers = CrunchyrollSeasonStructure.contentSeasonNumbers(
            linkedMapOf(
                "jp" to "Daemons of the Shadow Realm",
                "de" to "Daemons of the Shadow Realm (German Dub)",
                "fr" to "Daemons of the Shadow Realm - French Dub"
            ),
            mapOf("jp" to 1, "de" to 2, "fr" to 66)
        )

        assertEquals(mapOf("jp" to 1, "de" to 1, "fr" to 1), numbers)
    }

    @Test
    fun realSeasonTitlesRemainSeparate() {
        val numbers = CrunchyrollSeasonStructure.contentSeasonNumbers(
            linkedMapOf(
                "s1-jp" to "HELL MODE Season 1",
                "s1-de" to "HELL MODE Season 1 (German Dub)",
                "s2-jp" to "HELL MODE Season 2"
            ),
            mapOf("s1-jp" to 1, "s1-de" to 2, "s2-jp" to 3)
        )

        assertEquals(mapOf("s1-jp" to 1, "s1-de" to 1, "s2-jp" to 2), numbers)
    }

    @Test
    fun declaredClosedRangeRejectsRecapNumbersOutsideWano() {
        val title = "Land of Wano (892-1088)"

        assertEquals(false, CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(2, title)))
        assertEquals(true, CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(892, title)))
        assertEquals(true, CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(1088, title)))
        assertEquals(false, CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(1089, title)))
    }

    @Test
    fun declaredCurrentRangeRejectsSpecialNumbersOutsideEggheadAndElbaph() {
        assertEquals(
            false,
            CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(12, "Egghead (1089-1155)"))
        )
        assertEquals(
            true,
            CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(1089, "Egghead (1089-1155)"))
        )
        assertEquals(
            false,
            CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(40, "Elbaph (1156-current)"))
        )
        assertEquals(
            true,
            CrunchyrollSeasonStructure.containsDeclaredEpisode(episode(1156, "Elbaph (1156-current)"))
        )
    }

    @Test
    fun exactPrimarySeriesWinsOverDifferentJustWatchAliasSeries() {
        assertEquals(
            "slow-ahead-series",
            CrunchyrollSeriesIdentityPolicy.select(
                primaryMatch = "slow-ahead-series",
                aliasMatches = listOf("azur-lane-main-series")
            )
        )
    }

    @Test
    fun oneUnambiguousAliasRemainsAValidFallback() {
        assertEquals(
            "translated-title-series",
            CrunchyrollSeriesIdentityPolicy.select(
                primaryMatch = null,
                aliasMatches = listOf("translated-title-series", "translated-title-series")
            )
        )
        assertEquals(
            null,
            CrunchyrollSeriesIdentityPolicy.select(
                primaryMatch = null,
                aliasMatches = listOf("series-a", "series-b")
            )
        )
    }

    @Test
    fun shortenedParentTitleIsNotAcceptedAsSpinOffAlias() {
        assertTrue(
            CrunchyrollSeriesIdentityPolicy.isBroaderAlias(
                "Azur Lane - Slow Ahead!", "Azur Lane"
            )
        )
        assertFalse(
            CrunchyrollSeriesIdentityPolicy.isBroaderAlias(
                "Das Band der Unterwelt", "Daemons of the Shadow Realm"
            )
        )
    }

    @Test
    fun multipleExactCrunchyrollCatalogsAreKeptForOneAnime() {
        assertEquals(
            listOf("GW4HM7NV3", "G6JQVM3ER"),
            CrunchyrollSeriesIdentityPolicy.selectAll(
                primaryMatch = "GW4HM7NV3",
                aliasMatches = listOf("G6JQVM3ER", "GW4HM7NV3")
            )
        )
    }
}
