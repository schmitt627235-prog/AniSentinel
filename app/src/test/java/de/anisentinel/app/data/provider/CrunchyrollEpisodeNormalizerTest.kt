package de.anisentinel.app.data.provider

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class CrunchyrollEpisodeNormalizerTest {
    private fun episode(
        seasonId: String, season: Int, providerNumber: Int, sequence: Int?, id: String,
        audio: String? = "ja-JP", subtitles: Set<String> = setOf("de-DE")
    ) = CrunchyrollCatalogEpisode(
        "series", seasonId, season, id, providerNumber, sequence, "Episode $providerNumber",
        audio, subtitles, Instant.parse("2026-01-01T00:00:00Z").plusSeconds((sequence ?: 0) * 86400L),
        "available", "https://www.crunchyroll.com/watch/$id"
    )

    @Test fun exactStableEpisodeIdHasHighestPriority() {
        val rows = listOf(episode("s4", 4, 60, 1, "A"), episode("s4", 4, 1, 2, "B"))
        val result = CrunchyrollEpisodeNormalizer.resolve(rows, 4, 1, "GER_SUB", "s4", "B")
        assertEquals(CrunchyrollNormalizationStatus.MATCHED, result.status)
        assertEquals("B", result.episode?.episodeId)
    }

    @Test fun exactLocalNumberMatchesWithinUniqueSeasonId() {
        val rows = listOf(episode("s2", 2, 1, 26, "A"), episode("s2", 2, 2, 27, "B"))
        val result = CrunchyrollEpisodeNormalizer.resolve(rows, 2, 2, "GER_SUB")
        assertEquals("B", result.episode?.episodeId)
        assertEquals(2, result.localEpisodeNumber)
    }

    @Test fun globalNumbersCanUseStableSequenceOrdinal() {
        val rows = listOf(episode("s4", 4, 60, 60, "A"), episode("s4", 4, 61, 61, "B"))
        val result = CrunchyrollEpisodeNormalizer.resolve(rows, 4, 2, "GER_SUB", "s4")
        assertEquals(CrunchyrollNormalizationStatus.MATCHED, result.status)
        assertEquals("B", result.episode?.episodeId)
        assertEquals(61, result.globalEpisodeNumber)
    }

    @Test fun sameSeasonNumberWithMultipleSeasonIdsIsAmbiguousWithoutStableId() {
        val rows = listOf(episode("sub-season", 4, 1, 1, "A"), episode("dub-season", 4, 1, 1, "B", "de-DE", emptySet()))
        val result = CrunchyrollEpisodeNormalizer.resolve(rows, 4, 1, "GER_SUB")
        assertEquals(CrunchyrollNormalizationStatus.AMBIGUOUS, result.status)
    }

    @Test fun expectedLanguageIsRequiredOnConcreteEpisode() {
        val rows = listOf(episode("s1", 1, 1, 1, "A", "ja-JP", setOf("en-US")))
        val result = CrunchyrollEpisodeNormalizer.resolve(rows, 1, 1, "GER_SUB", "s1")
        assertEquals(CrunchyrollNormalizationStatus.NOT_FOUND, result.status)
    }

    @Test fun specialsWithoutSafeSequenceNeverProducePositiveMatch() {
        val rows = listOf(episode("specials", 0, 101, null, "A"), episode("specials", 0, 102, null, "B"))
        val result = CrunchyrollEpisodeNormalizer.resolve(rows, 0, 1, "GER_SUB", "specials")
        assertEquals(CrunchyrollNormalizationStatus.NOT_FOUND, result.status)
    }
}
