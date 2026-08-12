package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.JustWatchTitleMatch
import de.anisentinel.app.domain.provider.MatchConfidence
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Test
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.domain.provider.JustWatchOffer
import de.anisentinel.app.domain.provider.JustWatchSourceResult
import de.anisentinel.app.domain.provider.MonetizationType
import java.time.Instant

class JustWatchTitleMatcherTest {
    private fun match(title: String, year: Int?) =
        JustWatchTitleMatch(title, null, title, null, year, "SHOW", MatchConfidence.HIGH)

    @Test
    fun `season suffix can match one otherwise identical title`() {
        val result = JustWatchTitleMatcher.decide(
            "Example Hero Staffel 2", 2026, "SHOW",
            listOf(match("Example Hero", 2026))
        )

        assertTrue(result is TitleMatchDecision.Unique)
    }

    @Test
    fun `similar candidates with no clear winner remain ambiguous`() {
        val result = JustWatchTitleMatcher.decide(
            "Example Hero Season 2", null, "SHOW",
            listOf(match("Example Hero Staffel 2", null), match("Example Hero 2nd Season", null))
        )

        assertTrue(result is TitleMatchDecision.Ambiguous)
    }
    private fun candidate(id: String, year: Int = 2026) = JustWatchTitleMatch(id, null, "Test Anime", null, year, "SHOW", MatchConfidence.HIGH)

    @Test fun selectsOnlyUniqueCompatibleMatch() {
        assertTrue(JustWatchTitleMatcher.decide("Test Anime", 2026, "SHOW", listOf(candidate("1"))) is TitleMatchDecision.Unique)
    }

    @Test fun refusesAmbiguousMatch() {
        assertTrue(JustWatchTitleMatcher.decide("Test Anime", 2026, "SHOW", listOf(candidate("1"), candidate("2"))) is TitleMatchDecision.Ambiguous)
    }

    @Test fun refusesWrongYear() {
        assertTrue(JustWatchTitleMatcher.decide("Test Anime", 2025, "SHOW", listOf(candidate("1"))) is TitleMatchDecision.NoMatch)
    }

    @Test fun equivalentEraWordingMatchesWithoutTitleSpecificAlias() {
        val result = JustWatchTitleMatcher.decide(
            "Dara-san of Reiwa", 2026, "SHOW",
            listOf(match("Dara-san of the Reiwa Era", 2026))
        )
        assertTrue(result is TitleMatchDecision.Unique)
        assertTrue(JustWatchTitleMatcher.isConservativeEquivalent(
            listOf("Dara-san of Reiwa", "Reiwa no Dara-san"), "Dara-san of the Reiwa Era"
        ))
    }

    @Test fun yearSuffixedFranchiseTitleIsNotConservativeEnrichmentMatch() {
        assertFalse(JustWatchTitleMatcher.isConservativeEquivalent(listOf("One Piece"), "One Piece (2023)"))
    }

    @Test fun continuationReleaseYearDoesNotBlockSlimeSeriesMatch() {
        val result = JustWatchTitleMatcher.decide(
            "That Time I Got Reincarnated as a Slime", null, "SHOW",
            listOf(match("That Time I Got Reincarnated as a Slime", 2018))
        )
        assertTrue(result is TitleMatchDecision.Unique)
    }

    @Test fun continuationReleaseYearDoesNotBlockBumpkinSeriesMatch() {
        val result = JustWatchTitleMatcher.decide(
            "From Old Country Bumpkin to Master Swordsman", null, "SHOW",
            listOf(match("From Old Country Bumpkin to Master Swordsman", 2025))
        )
        assertTrue(result is TitleMatchDecision.Unique)
    }

    @Test fun localizedAndEnglishAliasesOfSameStableIdCanSelectEnglishTitle() {
        val localized = JustWatchTitleMatch("stable-id", null, "Vom Landei zum Schwertheiligen", null, 2025, "SHOW", MatchConfidence.HIGH)
        val english = JustWatchTitleMatch("stable-id", null, "From Old Country Bumpkin to Master Swordsman", null, 2025, "SHOW", MatchConfidence.HIGH)
        val result = JustWatchTitleMatcher.decide(
            "From Old Country Bumpkin to Master Swordsman", null, "SHOW", listOf(localized, english)
        )
        assertTrue(result is TitleMatchDecision.Unique)
        assertEquals("stable-id", (result as TitleMatchDecision.Unique).match.justWatchId)
    }

    @Test fun sameTopStableIdAcrossLocalesBridgesLocalizedAndRomajiTitles() {
        val localized = JustWatchTitleMatch("slime-id", null, "Meine Wiedergeburt als Schleim in einer anderen Welt", null, 2018, "SHOW", MatchConfidence.HIGH)
        val romaji = JustWatchTitleMatch("slime-id", null, "Tensei Shitara Suraimu Datta Ken", null, 2018, "SHOW", MatchConfidence.HIGH)
        val result = JustWatchTitleMatcher.stableCrossLocaleTop(
            "That Time I Got Reincarnated as a Slime", null, "SHOW", listOf(localized), listOf(romaji)
        )
        assertEquals("slime-id", result?.justWatchId)
    }

    @Test fun crossLocaleTopCannotOverrideExplicitYearSuffix() {
        val localized = match("MAO", 2025)
        val english = match("MAO", 2025)
        assertEquals(null, JustWatchTitleMatcher.stableCrossLocaleTop(
            "MAO (2026)", null, "SHOW", listOf(localized), listOf(english)
        ))
    }

    @Test fun parenthesizedYearMatchesOnlyCandidateWithSameSeriesYear() {
        assertTrue(JustWatchTitleMatcher.decide(
            "MAO (2026)", null, "SHOW", listOf(match("MAO", 2026))
        ) is TitleMatchDecision.Unique)
        assertTrue(JustWatchTitleMatcher.decide(
            "MAO (2026)", null, "SHOW", listOf(match("MAO", 2025))
        ) is TitleMatchDecision.NoMatch)
    }

    @Test fun onePieceRemakeDoesNotMatchWithoutHardYear() {
        assertTrue(JustWatchTitleMatcher.decide(
            "One Piece", null, "SHOW", listOf(match("One Piece (2023)", 2023))
        ) is TitleMatchDecision.NoMatch)
    }

    @Test fun onlyStoredMatchYearBecomesHardSeriesStartYear() {
        val inferred = providerMatchContext(2026, 1_786_000_000L, null)
        assertEquals(null, inferred.seriesStartYear)
        assertEquals("NONE", inferred.hardYearOrigin)
        val stored = providerMatchContext(2026, 1_786_000_000L, 2018)
        assertEquals(2018, stored.seriesStartYear)
        assertEquals("STORED_SERIES_START_YEAR", stored.hardYearOrigin)
    }

    @Test fun repositoryAcceptsSingleSourceValidatedStableIdWithOffer() {
        val candidate = JustWatchTitleMatch("slime-id", null, "Tensei Shitara Suraimu Datta Ken", null, 2018, "SHOW", MatchConfidence.HIGH)
        val offer = JustWatchOffer("slime-id", "crunchyroll", "Crunchyroll", null, null,
            MonetizationType.FLATRATE, null, emptySet(), emptySet(), "https://example.test", Instant.EPOCH)
        val result = sourceValidatedUnique(JustWatchSourceResult.Success(listOf(candidate), listOf(offer)))
        assertEquals("slime-id", result?.match?.justWatchId)
    }

    @Test fun providerSyncKeepsDueAndUpcomingTitlesInSameBoundedBatch() {
        val due = release("due", 1)
        val upcoming = release("upcoming", 2)
        val duplicateUpcoming = release("due", 3)
        val selected = providerSyncCandidates(listOf(due), listOf(upcoming, duplicateUpcoming), 10)
        assertTrue(selected.map { it.animeId } == listOf("due", "upcoming"))
    }

    private fun release(animeId: String, episode: Int) = EpisodeReleaseEntity(
        "$animeId:$episode", animeId, episode, null, episode.toLong(), null,
        "ANIWORLD_CALENDAR", null, null, 1, seasonNumber = 1, releaseLanguage = "GER_SUB"
    )
}
