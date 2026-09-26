package de.anisentinel.app.data.anticipated

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import de.anisentinel.app.data.anisearch.AniSearchSearchHit
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import de.anisentinel.app.data.anilist.GraphQlHttpResult
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class AnticipatedTitlesTest {
    @Test fun anticipatedQueryRequestsFutureAnimeByPopularityAndRequiredFields() {
        val repository = AnticipatedTitlesRepository(ApplicationProvider.getApplicationContext())
        val body = JSONObject(repository.requestBody(3))
        val query = body.getString("query")
        assertEquals(3, body.getJSONObject("variables").getInt("page"))
        assertTrue(query.contains("type: ANIME"))
        assertTrue(query.contains("status: NOT_YET_RELEASED"))
        assertTrue(query.contains("sort: POPULARITY_DESC"))
        listOf("favourites", "episodes", "nextAiringEpisode", "seasonYear", "startDate").forEach {
            assertTrue("Query must contain $it", query.contains(it))
        }
    }

    @Test fun paginationMergesAllSuccessfulPages() = runBlocking {
        val requestedPages = mutableListOf<Int>()
        val repository = AnticipatedTitlesRepository(
            ApplicationProvider.getApplicationContext(),
            aniListRequest = { _, body ->
                val page = JSONObject(body).getJSONObject("variables").getInt("page")
                requestedPages += page
                GraphQlHttpResult.Success(pageResponse(page, hasNext = page == 1), 200, emptyMap())
            }
        )
        val result = repository.fetchAllPages() as GraphQlHttpResult.Success
        val media = JSONObject(result.body).getJSONObject("data").getJSONObject("Page").getJSONArray("media")
        assertEquals(listOf(1, 2), requestedPages)
        assertEquals(listOf(1, 2), (0 until media.length()).map { media.getJSONObject(it).getInt("id") })
    }

    @Test fun laterRateLimitKeepsAlreadyReceivedAniListTitles() = runBlocking {
        val repository = AnticipatedTitlesRepository(
            ApplicationProvider.getApplicationContext(),
            aniListRequest = { _, body ->
                val page = JSONObject(body).getJSONObject("variables").getInt("page")
                if (page == 1) GraphQlHttpResult.Success(pageResponse(1, hasNext = true), 200, emptyMap())
                else GraphQlHttpResult.HttpFailure(429, null, 60, 0, null, null)
            }
        )
        val result = repository.fetchAllPages() as GraphQlHttpResult.Success
        val parsed = repository.parse(result.body, observedAt = 123) as AnticipatedLoadResult.Success
        assertEquals(listOf("Title 1"), parsed.titles.map { it.title })
        assertEquals(listOf("true"), result.headers["X-AniSentinel-Partial"])
        assertEquals(DachLicenseStatus.UNKNOWN, parsed.titles.single().dachLicenseStatus)
    }

    @Test fun mapsExtendedAniListFieldsWithoutDachData() {
        val repository = AnticipatedTitlesRepository(ApplicationProvider.getApplicationContext())
        val parsed = repository.parse(pageResponse(7, hasNext = false), observedAt = 123) as AnticipatedLoadResult.Success
        val title = parsed.titles.single()
        assertEquals(70_000, title.popularity)
        assertEquals(7_000, title.favourites)
        assertEquals(24, title.episodes)
        assertEquals(8, title.nextAiringEpisode)
        assertEquals(1_800_000_007L, title.nextAiringAt)
        assertEquals(DachLicenseStatus.UNKNOWN, title.dachLicenseStatus)
    }

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
            nativeTitle = "Re:Monster ?2?",
            identity = UpcomingAnimeIdentity("anilist:1", 1, null, null,
                setOf("Re:Monster 2nd Season", "Re:Monster Dai 2 Ki"), null, 2)
        )
        val variants = AniSearchFutureMatcher.searchVariants(value)
        assertTrue("Re:Monster 2nd Season" in variants)
        assertTrue("Re:Monster Season 2" in variants)
        assertTrue("Re:Monster ?2?" in variants)
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

    @Test fun detailInstallmentConflictIsRejectedGenerically() {
        val value = title(1, "Example Season 3 Cour 1", "NOT_YET_RELEASED", 1).copy(
            identity = UpcomingAnimeIdentity("anilist:1", 1, null, null, setOf("Example Season 3 Cour 1"), null, 3)
        )
        assertTrue(AniSearchFutureMatcher.hasInstallmentConflict(value, setOf("Beispiel Staffel 2 Cour 1")))
        assertTrue(AniSearchFutureMatcher.hasInstallmentConflict(value, setOf("Beispiel Staffel 3 Cour 2")))
        assertFalse(AniSearchFutureMatcher.hasInstallmentConflict(value, setOf("Vollst?ndig ?bersetzter Titel")))
    }

    @Test fun aniListJapanStartNeverBecomesDachAvailability() {
        val media = JSONObject().apply {
            put("id", 42); put("idMal", JSONObject.NULL)
            put("title", JSONObject().put("romaji", "Generic Future").put("english", "Generic Future").put("native", "??"))
            put("synonyms", JSONArray())
            put("startDate", JSONObject().put("year", 2027).put("month", 1).put("day", 8))
            put("relations", JSONObject().put("edges", JSONArray()))
            put("coverImage", JSONObject())
            put("status", "NOT_YET_RELEASED"); put("popularity", 100); put("trending", 10)
            put("season", "WINTER"); put("seasonYear", 2027); put("format", "TV")
            put("studios", JSONObject().put("nodes", JSONArray()))
        }
        val json = JSONObject().put("data", JSONObject().put("Page", JSONObject().put("media", JSONArray().put(media)))).toString()
        val repository = AnticipatedTitlesRepository(ApplicationProvider.getApplicationContext())
        val parsed = repository.parse(json, observedAt = 123) as AnticipatedLoadResult.Success
        assertEquals(LocalDate.of(2027, 1, 8), parsed.titles.single().startDate)
        assertEquals(null, parsed.titles.single().dachAvailableFrom)
        assertEquals(DachLicenseStatus.UNKNOWN, parsed.titles.single().dachLicenseStatus)
    }

    @Test fun dachUsesAvailabilityDateNotDetectionDate() {
        val confirmed = title(1,"A","NOT_YET_RELEASED",1).copy(dachLicenseStatus=DachLicenseStatus.CONFIRMED, dachProvider="Crunchyroll", dachAvailableFrom=LocalDate.of(2026,10,3), sourceObservedAt=1_795_000_000, firstDetectedAt=1_795_000_000)
        assertEquals("DACH ? ab 03.10.2026 ? Anbieter: Crunchyroll ? Best?tigt", AnticipatedDachFormatter.format(confirmed))
        assertEquals("DACH-Status derzeit nicht ermittelbar", AnticipatedDachFormatter.format(title(2,"B","NOT_YET_RELEASED",1)))
        assertEquals("Noch nicht in der DACH-Region lizenziert", AnticipatedDachFormatter.format(title(3,"C","NOT_YET_RELEASED",1).copy(dachLicenseStatus = DachLicenseStatus.NOT_LICENSED_YET)))
    }

    @Test fun laterDachConfirmationReplacesNegativeVisibleState() {
        val updated = AnticipatedDachResolver.apply(
            title(1,"A","NOT_YET_RELEASED",1),
            AnticipatedDachEvidence("Crunchyroll", LocalDate.of(2026,10,3), "OFFICIAL", 1_795_000_000, 100)
        )
        assertEquals(DachLicenseStatus.CONFIRMED, updated.dachLicenseStatus)
        assertEquals("DACH ? ab 03.10.2026 ? Anbieter: Crunchyroll ? Best?tigt", AnticipatedDachFormatter.format(updated))
        assertFalse(AnticipatedDachFormatter.format(updated).contains("Noch nicht"))
    }

    private fun title(id:Int, name:String, status:String, popularity:Int) = AnticipatedTitle(
        UpcomingAnimeIdentity("anilist:$id", id, null, null, setOf(name), null, null), name, null, null,
        coverUrl=null, description=null, season="FALL", seasonYear=2026, startDate=LocalDate.of(2026,10,1), status=status,
        popularity=popularity, trending=0, studio=null, format="TV", sourceObservedAt=1, firstDetectedAt=1, updatedAt=1
    )

    private fun pageResponse(id: Int, hasNext: Boolean): String {
        val media = JSONObject().apply {
            put("id", id); put("idMal", JSONObject.NULL)
            put("title", JSONObject().put("romaji", "Title $id").put("english", "Title $id").put("native", "Title $id"))
            put("synonyms", JSONArray())
            put("startDate", JSONObject().put("year", 2027).put("month", 1).put("day", id.coerceAtMost(28)))
            put("relations", JSONObject().put("edges", JSONArray()))
            put("coverImage", JSONObject())
            put("status", "NOT_YET_RELEASED"); put("popularity", id * 10_000); put("favourites", id * 1_000); put("trending", id)
            put("season", "WINTER"); put("seasonYear", 2027); put("format", "TV"); put("episodes", 24)
            put("nextAiringEpisode", JSONObject().put("episode", 8).put("airingAt", 1_800_000_000L + id))
            put("studios", JSONObject().put("nodes", JSONArray()))
        }
        return JSONObject().put("data", JSONObject().put("Page", JSONObject()
            .put("pageInfo", JSONObject().put("hasNextPage", hasNext))
            .put("media", JSONArray().put(media)))).toString()
    }
}
