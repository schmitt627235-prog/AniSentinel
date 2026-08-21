package de.anisentinel.app.data.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.data.local.AniSentinelDatabase
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.release.AniWorldEpisodeFallbackChecker
import de.anisentinel.app.data.release.AniWorldEpisodePage
import de.anisentinel.app.domain.provider.JustWatchOffer
import de.anisentinel.app.domain.provider.JustWatchSourceResult
import de.anisentinel.app.domain.provider.JustWatchTitleMatch
import de.anisentinel.app.domain.provider.JustWatchPartnerSource
import de.anisentinel.app.domain.provider.MatchConfidence
import de.anisentinel.app.domain.provider.MonetizationType
import de.anisentinel.app.domain.provider.ProviderEpisodeAvailability
import de.anisentinel.app.domain.provider.ProviderMetadataAdapter
import de.anisentinel.app.domain.provider.ProviderMetadataIdentity
import de.anisentinel.app.domain.provider.ProviderMetadataProbeRequest
import de.anisentinel.app.domain.provider.ProviderMetadataProbeResult
import de.anisentinel.app.domain.provider.UnconfiguredProviderEpisodeChecker
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProviderFirstPipelineTest {
    private lateinit var database: AniSentinelDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test fun directProviderSuccessIsPersistedBeforeAndWithoutAniWorldFallback() = runBlocking {
        val now = Instant.parse("2026-08-16T12:00:00Z")
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "anime", null, null, "Example", "Example", null, null, "", null, null,
            "SUMMER", 2026, 12, now.epochSecond
        )))
        dao.upsertEpisodeReleases(listOf(
            EpisodeReleaseEntity(
                "release", "anime", 6, null, now.minusSeconds(60).epochSecond, "Netflix",
                "ANIWORLD_CALENDAR", "https://aniworld.to/kalender", "https://www.netflix.com/title/82760630",
                now.epochSecond, seasonNumber = 2, releaseLanguage = "GER_SUB"
            ),
            EpisodeReleaseEntity(
                "release-5", "anime", 5, null, now.minusSeconds(7 * 86_400).epochSecond, "Netflix",
                "ANIWORLD_CALENDAR", "https://aniworld.to/kalender", "https://www.netflix.com/title/82760630",
                now.epochSecond, seasonNumber = 2, releaseLanguage = "GER_SUB"
            ),
            EpisodeReleaseEntity(
                "release-4", "anime", 4, null, now.minusSeconds(14 * 86_400).epochSecond, "Netflix",
                "ANIWORLD_CALENDAR", "https://aniworld.to/kalender", "https://www.netflix.com/title/82760630",
                now.epochSecond, seasonNumber = 2, releaseLanguage = "GER_SUB"
            )
        ))
        dao.upsertEpisodeProviderAvailability(listOf(EpisodeProviderAvailabilityEntity(
            "availability:release-4:netflix", "release-4", "netflix", "Netflix", 2, 4,
            "NOT_AVAILABLE_YET", false, false, null, null, now.minusSeconds(1_000).epochSecond,
            now.minusSeconds(1_000).epochSecond, null, 1, "https://www.netflix.com/title/82760630",
            "OFFICIAL_PUBLIC_CATALOG_METADATA", "https://www.netflix.com/title/82760630", null,
            "NETFLIX_PUBLIC_WEB"
        )))
        var fallbackCalls = 0
        val fallback = AniWorldEpisodeFallbackChecker(loader = {
            fallbackCalls++
            AniWorldEpisodePage(500, "")
        })
        val source = object : JustWatchPartnerSource {
            override suspend fun lookup(
                title: String, year: Int?, contentType: String,
                seasonNumber: Int?, episodeNumber: Int
            ) = JustWatchSourceResult.Success(
                    matches = listOf(JustWatchTitleMatch("jw", null, "Example", null, 2026, "SHOW", MatchConfidence.HIGH)),
                    offers = listOf(JustWatchOffer(
                        "jw", "netflix", "Netflix", null, null, MonetizationType.FLATRATE, null,
                        emptySet(), emptySet(), "https://www.netflix.com/title/82760630", now
                    ))
                )
        }
        val direct = object : ProviderMetadataAdapter {
            override val adapterId = "NETFLIX_PUBLIC_WEB"
            override fun supports(providerName: String) = providerName.equals("Netflix", true)
            override suspend fun probe(request: ProviderMetadataProbeRequest, identity: ProviderMetadataIdentity?) =
                ProviderMetadataProbeResult.Available(
                    ProviderEpisodeAvailability(
                        "NETFLIX", 2, 6, true, true, false, now,
                        "https://www.netflix.com/watch/episode-6", now,
                        "OFFICIAL_PUBLIC_CATALOG_METADATA", "https://www.netflix.com/title/82760630"
                    ),
                    ProviderMetadataIdentity("NETFLIX", "DE", "82760630", episodeId = "episode-6")
                )
        }
        val repository = ProviderPipelineRepository(
            dao, source, UnconfiguredProviderEpisodeChecker, fallback, listOf(direct)
        )

        val run = repository.checkEpisode("release", now)

        assertEquals(0, fallbackCalls)
        assertEquals(0, run.failed)
        assertEquals("AVAILABLE", dao.release("release")?.releaseStatus)
        val availability = dao.episodeProviderAvailability("release").single { it.source == direct.adapterId }
        assertTrue(availability.status.startsWith("AVAILABLE_"))
        assertEquals("https://www.netflix.com/watch/episode-6", availability.providerUrl)
        val inferred = dao.episodeProviderAvailability("release-5").single()
        assertEquals("AVAILABLE_GER_SUB", inferred.status)
        assertEquals("INFERRED_FROM_LATER_PROVIDER_EPISODE", inferred.evidenceType)
        assertEquals("AVAILABLE", dao.release("release-5")?.releaseStatus)
        assertEquals("NOT_AVAILABLE_YET", dao.episodeProviderAvailability("release-4").single().status)
    }
}
