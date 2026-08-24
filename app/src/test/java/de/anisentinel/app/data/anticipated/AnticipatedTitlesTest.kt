package de.anisentinel.app.data.anticipated

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.anisentinel.app.data.anisearch.AniSearchSearchHit

class AnticipatedTitlesTest {
    @Test fun rankingKeepsOnlyFutureAndUsesPopularity() {
        val items = listOf(title(1, "A", "NOT_YET_RELEASED", 100_000), title(2, "B", "RELEASING", 200_000), title(3, "C", "NOT_YET_RELEASED", 50_000))
        assertEquals(listOf("A", "C"), AnticipatedRanking.rank(items, LocalDate.of(2026, 8, 24)).map { it.title })
    }

    @Test fun higherPopularityRanksFirst() {
        assertEquals("B", AnticipatedRanking.rank(listOf(title(1,"A","NOT_YET_RELEASED",90_000), title(2,"B","NOT_YET_RELEASED",120_000)), LocalDate.of(2026,8,24)).first().title)
    }

    @Test fun seasonAliasesResolveWithoutTitleHardcode() {
        assertTrue(UpcomingIdentityResolver.sameFutureTitle(listOf("Re:Monster 2nd Season"), listOf("Re:Monster Dai 2 Ki")))
        assertTrue(UpcomingIdentityResolver.sameFutureTitle(listOf("Black Clover Season 2"), listOf("Black Clover: Staffel 2")))
        assertTrue(UpcomingIdentityResolver.sameFutureTitle(listOf("The Apothecary Diaries Season 3"), listOf("The Apothecary Diaries Season 3 - Cour 1")))
        assertFalse(UpcomingIdentityResolver.sameFutureTitle(listOf("One Piece"), listOf("One Piece Season 2")))
    }

    @Test fun searchVariantsContainAllAniListTitlesAndSeasonAliases() {
        val value = title(1, "Re:Monster 2nd Season", "NOT_YET_RELEASED", 1).copy(
            englishTitle = "Re:Monster Season 2",
            nativeTitle = "Re:Monster 第2期",
            identity = UpcomingAnimeIdentity("anilist:1", 1, null, null,
                setOf("Re:Monster 2nd Season", "Re:Monster Dai 2 Ki"), null, 2)
        )
        val variants = AniSearchFutureMatcher.searchVariants(value)
        assertTrue("Re:Monster 2nd Season" in variants)
        assertTrue("Re:Monster Season 2" in variants)
        assertTrue("Re:Monster 第2期" in variants)
        assertTrue("Re:Monster Dai 2 Ki" in variants)
    }

    @Test fun safeAliasMatchAcceptsDaiKi() {
        val value = title(1, "Re:Monster 2nd Season", "NOT_YET_RELEASED", 1).copy(
            identity = UpcomingAnimeIdentity("anilist:1", 1, null, null, setOf("Re:Monster 2nd Season"), null, 2)
        )
        val result = AniSearchFutureMatcher.select(value, listOf(AniSearchSearchHit("99", "Re:Monster Dai 2 Ki", "https://www.anisearch.de/anime/99,test")))
        assertEquals("99", result?.hit?.anisearchId)
    }

    @Test fun safeMatchRejectsWrongSeasonAndAmbiguousCandidates() {
        val value = title(1, "Serie X Season 3", "NOT_YET_RELEASED", 1).copy(
            identity = UpcomingAnimeIdentity("anilist:1", 1, null, null, setOf("Serie X Season 3"), null, 3)
        )
        assertEquals(null, AniSearchFutureMatcher.select(value, listOf(
            AniSearchSearchHit("1", "Serie X Season 1", "https://www.anisearch.de/anime/1,a"),
            AniSearchSearchHit("2", "Serie X Season 2", "https://www.anisearch.de/anime/2,b")
        )))
        assertEquals(null, AniSearchFutureMatcher.select(value, listOf(
            AniSearchSearchHit("3", "Serie X Season 3", "https://www.anisearch.de/anime/3,a"),
            AniSearchSearchHit("4", "Serie X Staffel 3", "https://www.anisearch.de/anime/4,b")
        )))
    }

    @Test fun dachUsesAvailabilityDateNotDetectionDate() {
        val confirmed = title(1,"A","NOT_YET_RELEASED",1).copy(dachLicenseStatus=DachLicenseStatus.CONFIRMED, dachProvider="Crunchyroll", dachAvailableFrom=LocalDate.of(2026,10,3), sourceObservedAt=1_795_000_000, firstDetectedAt=1_795_000_000)
        assertEquals("DACH · ab 03.10.2026 · Anbieter: Crunchyroll · Bestätigt", AnticipatedDachFormatter.format(confirmed))
        assertEquals("DACH-Status derzeit nicht ermittelbar", AnticipatedDachFormatter.format(title(2,"B","NOT_YET_RELEASED",1)))
        assertEquals("Noch nicht in der DACH-Region lizenziert", AnticipatedDachFormatter.format(title(3,"C","NOT_YET_RELEASED",1).copy(dachLicenseStatus = DachLicenseStatus.NOT_LICENSED_YET)))
    }

    @Test fun laterDachConfirmationReplacesNegativeVisibleState() {
        val updated = AnticipatedDachResolver.apply(
            title(1,"A","NOT_YET_RELEASED",1),
            AnticipatedDachEvidence("Crunchyroll", LocalDate.of(2026,10,3), "OFFICIAL", 1_795_000_000, 100)
        )
        assertEquals(DachLicenseStatus.CONFIRMED, updated.dachLicenseStatus)
        assertEquals("DACH · ab 03.10.2026 · Anbieter: Crunchyroll · Bestätigt", AnticipatedDachFormatter.format(updated))
        assertFalse(AnticipatedDachFormatter.format(updated).contains("Noch nicht"))
    }

    private fun title(id:Int, name:String, status:String, popularity:Int) = AnticipatedTitle(
        UpcomingAnimeIdentity("anilist:$id", id, null, null, setOf(name), null, null), name, null, null,
        coverUrl=null, description=null, season="FALL", seasonYear=2026, startDate=LocalDate.of(2026,10,1), status=status,
        popularity=popularity, trending=0, studio=null, format="TV", sourceObservedAt=1, firstDetectedAt=1, updatedAt=1
    )
}
