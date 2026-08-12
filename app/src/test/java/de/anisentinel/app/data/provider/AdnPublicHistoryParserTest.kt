package de.anisentinel.app.data.provider

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdnPublicHistoryParserTest {
    @Test fun importsOnlyExplicitPublicEpisodeDatesAndGermanVersions() {
        val parsed = AdnPublicHistoryParser.parse("""
            {"videos":[
              {"id":"ep-7","shortNumber":"7","season":"Staffel 2","name":"Folge 7",
               "languages":["vostde","vde"],"publicationDate":"2025-03-03"},
              {"id":"ep-8","shortNumber":"8","season":2,"languages":["vostf"],
               "publicationDate":"2025-03-10"}
            ]}
        """.trimIndent())
        assertEquals(1, parsed.episodes.size)
        val episode = parsed.episodes.single()
        assertEquals("ep-7", episode.episodeId)
        assertEquals(2, episode.seasonNumber)
        assertEquals(7, episode.episodeNumber)
        assertEquals(setOf("GER_SUB", "GER_DUB"), episode.releaseLanguages)
        assertEquals("publicationDate", episode.dateSourceField)
        assertEquals(Instant.parse("2025-03-02T23:00:00Z"), episode.releasedAt)
    }

    @Test fun missingEpisodeDateStaysExplicitlyUnknown() {
        val parsed = AdnPublicHistoryParser.parse("""
            {"videos":[{"id":"ep-1","shortNumber":1,"season":1,"languages":["vostde"]}]}
        """.trimIndent())
        assertEquals(1, parsed.episodes.size)
        assertNull(parsed.episodes.single().releasedAt)
        assertTrue(parsed.observedDateFields.isEmpty())
    }

    @Test fun providerEpisodeSourceWinsAndEqualPriorityConflictsAreNotSilent() {
        assertTrue(HistoricalSourcePolicy.conflicts(100, 200))
        assertEquals(300, HistoricalSourcePolicy.PROVIDER_EPISODE)
        assertEquals(200, HistoricalSourcePolicy.OFFICIAL_PROVIDER_ANNOUNCEMENT)
    }
}
