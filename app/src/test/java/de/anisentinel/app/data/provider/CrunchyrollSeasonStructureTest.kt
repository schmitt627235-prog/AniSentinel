package de.anisentinel.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class CrunchyrollSeasonStructureTest {
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
}
