package de.anisentinel.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import de.anisentinel.app.data.anilist.AniListCalendarRepository
import de.anisentinel.app.data.anisearch.SourceCalendarFetchResult
import de.anisentinel.app.data.anisearch.SourceFailureReason
import de.anisentinel.app.data.anilist.CalendarSyncResult
import de.anisentinel.app.data.anisearch.ReleaseCalendarSource
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class AniSentinelDaoTest {
    private lateinit var database: AniSentinelDatabase
    private lateinit var dao: AniSentinelDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.aniSentinelDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `favorite relation exposes only enabled favorites`() = runBlocking {
        dao.upsertAnime(
            listOf(
                anime("favorite", "Favorite"),
                anime("disabled", "Disabled")
            )
        )
        dao.upsertFavorite(favorite("favorite", enabled = true))
        dao.upsertFavorite(favorite("disabled", enabled = false))

        assertEquals(listOf("favorite"), dao.observeFavorites().first().map { it.id })
    }

    @Test
    fun `episode persists honest availability window`() = runBlocking {
        dao.upsertAnime(listOf(anime("atlas", "Atlas of Ash")))
        dao.upsertEpisodes(
            listOf(
                EpisodeEntity(
                    animeId = "atlas",
                    seasonNumber = 1,
                    episodeNumber = 11,
                    title = "Das letzte Archiv",
                    expectedReleaseAt = 1_000,
                    lastUnavailableAt = 1_100,
                    firstAvailableAt = 1_400,
                    providerEpisodeId = null,
                    providerEpisodeUrl = null,
                    status = "AVAILABLE",
                    confidence = .95
                )
            )
        )

        val episode = dao.episodesForAnime("atlas").single()
        assertEquals(1_100L, episode.lastUnavailableAt)
        assertEquals(1_400L, episode.firstAvailableAt)
        assertNull(episode.providerEpisodeUrl)
    }

    @Test
    fun `failed calendar refresh preserves cached releases`() = runBlocking {
        dao.upsertAnime(listOf(anime("anilist:1", "Gespeicherter Titel")))
        dao.upsertEpisodeReleases(
            listOf(
                EpisodeReleaseEntity(
                    "anilist:1:7", "anilist:1", 7, null, 1_800_000_000,
                    null, "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/1", null, 1
                )
            )
        )
        val failingSource = object : ReleaseCalendarSource {
            override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate) =
                SourceCalendarFetchResult.Unavailable(SourceFailureReason.RATE_LIMITED, "HTTP_429_RETRY_AFTER_60")
        }

        val result = AniListCalendarRepository(dao, failingSource).sync(
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1)
        )

        assertTrue(result is CalendarSyncResult.RetryRequired)
        assertEquals(1, dao.observeEpisodeReleasesForWindow(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun `http 403 calendar refresh preserves cached releases`() = runBlocking {
        dao.upsertAnime(listOf(anime("anilist:403", "Gespeicherter 403 Titel")))
        dao.upsertEpisodeReleases(
            listOf(
                EpisodeReleaseEntity(
                    "anilist:403:1", "anilist:403", 1, null, 1_800_000_100,
                    null, "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/403", null, 1
                )
            )
        )
        val failingSource = object : ReleaseCalendarSource {
            override suspend fun fetchRange(start: LocalDate, endExclusive: LocalDate) =
                SourceCalendarFetchResult.Unavailable(SourceFailureReason.HTTP, "ANILIST_HTTP_403")
        }

        val result = AniListCalendarRepository(dao, failingSource).sync(
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1)
        )

        assertTrue(result is CalendarSyncResult.RetryRequired)
        assertEquals(1, dao.observeEpisodeReleasesForWindow(0, Long.MAX_VALUE).first().size)
    }

    @Test
    fun `complete range replacement removes withdrawn AniList release only`() = runBlocking {
        dao.upsertAnime(listOf(anime("anilist:1", "One"), anime("local:1", "Local")))
        dao.upsertEpisodeReleases(
            listOf(
                EpisodeReleaseEntity("anilist:1:5", "anilist:1", 5, null, 1_100, null,
                    "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/1", null, 1),
                EpisodeReleaseEntity("anilist:1:6", "anilist:1", 6, null, 1_200, null,
                    "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/1", null, 1),
                EpisodeReleaseEntity("local:1:1", "local:1", 1, null, 1_150, null,
                    "LOCAL_DIAGNOSTIC:test", "https://example.invalid/local", null, 1)
            )
        )

        dao.replaceReleaseSourceRange(
            1_000, 1_300, "ANILIST_AIRING_SCHEDULE", emptyList(),
            listOf(EpisodeReleaseEntity("anilist:1:5", "anilist:1", 5, null, 1_100, null,
                "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/1", null, 2)),
            1_000
        )

        val stored = dao.observeEpisodeReleasesForWindow(1_000, 1_300).first()
        assertEquals(setOf("anilist:1:5", "local:1:1"), stored.map { it.sourceReleaseId }.toSet())
    }

    @Test
    fun `complete empty response clears only AniList range`() = runBlocking {
        dao.upsertAnime(listOf(anime("anilist:2", "Two"), anime("local:2", "Local")))
        dao.upsertEpisodeReleases(listOf(
            EpisodeReleaseEntity("anilist:2:1", "anilist:2", 1, null, 2_100, null,
                "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/2", null, 1),
            EpisodeReleaseEntity("local:2:1", "local:2", 1, null, 2_100, null,
                "LOCAL_DIAGNOSTIC:test", "https://example.invalid/local", null, 1)
        ))

        dao.replaceReleaseSourceRange(2_000, 2_200, "ANILIST_AIRING_SCHEDULE", emptyList(), emptyList(), 2_000)

        assertEquals(listOf("local:2:1"),
            dao.observeEpisodeReleasesForWindow(2_000, 2_200).first().map { it.sourceReleaseId })
    }

    @Test
    fun `shifted release and next airing use earliest future instant`() = runBlocking {
        val base = anime("anilist:3", "Three").copy(nextAiringAt = 3_050, nextEpisode = 4)
        dao.upsertAnime(listOf(base))
        dao.upsertEpisodeReleases(listOf(
            EpisodeReleaseEntity("anilist:3:4", "anilist:3", 4, null, 3_050, null,
                "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/3", null, 1)
        ))

        dao.replaceReleaseSourceRange(
            3_000, 4_000, "ANILIST_AIRING_SCHEDULE", listOf(base),
            listOf(
                EpisodeReleaseEntity("anilist:3:4", "anilist:3", 4, null, 3_400, null,
                    "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/3", null, 2),
                EpisodeReleaseEntity("anilist:3:5", "anilist:3", 5, null, 3_600, null,
                    "ANILIST_AIRING_SCHEDULE", "https://anilist.co/anime/3", null, 2)
            ),
            3_200
        )

        assertEquals(3_400L, dao.anime("anilist:3")?.nextAiringAt)
        assertEquals(4, dao.anime("anilist:3")?.nextEpisode)
        dao.replaceReleaseSourceRange(3_000, 4_000, "ANILIST_AIRING_SCHEDULE", emptyList(), emptyList(), 4_100)
        assertNull(dao.anime("anilist:3")?.nextAiringAt)
        assertNull(dao.anime("anilist:3")?.nextEpisode)
    }

    @Test
    fun `replacing profile removes stale phases`() = runBlocking {
        val profile = WatchProfileEntity("auto", "Automatisch", true, 1_440, true)
        dao.replaceProfile(
            profile,
            listOf(
                WatchPhaseEntity("auto", 0, 600, 60),
                WatchPhaseEntity("auto", 600, null, 300)
            )
        )
        dao.replaceProfile(
            profile,
            listOf(WatchPhaseEntity("auto", 0, null, 600))
        )

        assertEquals(listOf(600L), dao.phasesForProfile("auto").map { it.intervalSeconds })
    }

    @Test
    fun `favoriting same anime twice keeps one record`() = runBlocking {
        dao.upsertAnime(listOf(anime("atlas", "Atlas of Ash")))
        dao.upsertFavorite(favorite("atlas", true))
        dao.upsertFavorite(favorite("atlas", true))

        assertEquals(1, dao.favoriteRecordCount("atlas"))
        assertEquals(listOf("atlas"), dao.observeFavorites().first().map { it.id })
    }

    @Test
    fun `disabled favorite disappears from favorites flow`() = runBlocking {
        dao.upsertAnime(listOf(anime("atlas", "Atlas of Ash")))
        dao.upsertFavorite(favorite("atlas", true))
        dao.upsertFavorite(favorite("atlas", false))

        assertEquals(emptyList<AnimeEntity>(), dao.observeFavorites().first())
        assertEquals(false, dao.favorite("atlas")?.enabled)
    }

    @Test
    fun `configuration update preserves original createdAt`() = runBlocking {
        dao.upsertAnime(listOf(anime("atlas", "Atlas of Ash")))
        val repository = LocalFavoritesRepository(dao)
        dao.upsertFavorite(favorite("atlas", true).copy(createdAt = 42))

        repository.updateFavoriteConfiguration("atlas", "DUB", "fast")

        val stored = dao.favorite("atlas")
        assertEquals(42L, stored?.createdAt)
        assertEquals("DUB", stored?.languagePreference)
        assertEquals("fast", stored?.monitoringProfileId)
    }

    @Test
    fun `favorite survives anime metadata upsert`() = runBlocking {
        val original = anime("atlas", "Atlas of Ash")
        dao.upsertAnime(listOf(original))
        dao.upsertFavorite(favorite("atlas", true))

        dao.upsertAnime(
            listOf(original.copy(titleGerman = "Atlas of Ash – Aktualisiert"))
        )

        val storedFavorite = dao.favorite("atlas")
        assertNotNull(storedFavorite)
        assertTrue(storedFavorite!!.enabled)
        assertEquals(1, dao.favoriteRecordCount("atlas"))
        assertEquals(
            listOf("atlas"),
            dao.observeFavorites().first().map { it.id }
        )
        assertEquals(
            "Atlas of Ash – Aktualisiert",
            dao.observeAnime().first().single { it.id == "atlas" }.titleGerman
        )
    }

    @Test
    fun `catalog refresh replaces snapshot and preserves favorite anime`() = runBlocking {
        val first = anime("first", "First").copy(anilistId = 1)
        val favorite = anime("favorite", "Favorite").copy(anilistId = 2)
        dao.replaceCatalog(
            "TRENDING",
            listOf(first, favorite),
            listOf(
                CatalogEntryEntity("TRENDING", "first", 0, 10),
                CatalogEntryEntity("TRENDING", "favorite", 1, 10)
            )
        )
        dao.upsertFavorite(favorite("favorite", true))

        val latest = anime("latest", "Latest").copy(anilistId = 3)
        dao.replaceCatalog(
            "TRENDING",
            listOf(latest),
            listOf(CatalogEntryEntity("TRENDING", "latest", 0, 20))
        )

        assertEquals(listOf("latest"), dao.observeCatalog("TRENDING").first().map { it.id })
        assertEquals(1, dao.catalogEntryCount("TRENDING"))
        assertEquals("favorite", dao.anime("favorite")?.id)
        assertTrue(dao.favorite("favorite")?.enabled == true)
    }

    @Test
    fun `release window returns only matching real dates`() = runBlocking {
        dao.upsertAnime(
            listOf(
                anime("first", "First").copy(nextAiringAt = 100),
                anime("second", "Second").copy(nextAiringAt = 150),
                anime("outside", "Outside").copy(nextAiringAt = 200)
            )
        )

        assertEquals(
            listOf("first", "second"),
            dao.observeAnimeForWindow(100, 200).first().map { it.id }
        )
        assertEquals(
            emptyList<AnimeEntity>(),
            dao.observeAnimeForWindow(300, 400).first()
        )
    }

    @Test
    fun `aniworld range replacement preserves schedule history`() = runBlocking {
        val anime = anime("aniworld:test", "Test")
        val release = EpisodeReleaseEntity(
            "aniworld:test:s1:e2:1000:ger_sub", anime.id, 2, null, 1_000,
            null, "ANIWORLD_CALENDAR", "https://aniworld.to/animekalender", null, 900,
            seasonNumber = 1, listedAt = 1_600, adjustmentMinutes = -10,
            releaseLanguage = "GER_SUB"
        )
        dao.upsertAnime(listOf(anime))
        dao.upsertEpisodeReleases(listOf(release))
        dao.upsertReleaseScheduleHistory(listOf(ReleaseScheduleHistoryEntity(
            "history:1", release.sourceReleaseId, 400, 1_000,
            "ANIWORLD_SCHEDULE_CHANGE", "Pause", "Sub", 900,
            "https://aniworld.to/support/frage/anime-verschiebungen", null
        )))

        dao.replaceAniWorldReleaseRange(0, 2_000, emptyList(), emptyList(), emptyList(), 500)

        assertEquals(1, dao.releaseScheduleHistoryCount())
        assertEquals(1, dao.releaseRowsForSource("ANIWORLD_CALENDAR").size)
    }

    @Test
    fun `malformed episode identity is removed and favorite moves to canonical anime`() = runBlocking {
        val canonicalAnime = anime("aniworld:real-series", "Real Series")
        val malformedAnime = anime("aniworld:episode-6", "Real Series")
        val canonicalRelease = EpisodeReleaseEntity(
            "aniworld:real-series:s2:e6:1000:ger_sub", canonicalAnime.id, 6, null, 1_000,
            null, "ANIWORLD_CALENDAR", "https://aniworld.to/animekalender", null, 900,
            seasonNumber = 2, releaseLanguage = "GER_SUB"
        )
        val malformedRelease = canonicalRelease.copy(
            sourceReleaseId = "aniworld:episode-6:s2:e6:1000:ger_sub",
            animeId = malformedAnime.id
        )
        dao.upsertAnime(listOf(canonicalAnime, malformedAnime))
        dao.upsertEpisodeReleases(listOf(canonicalRelease, malformedRelease))
        dao.upsertFavorite(FavoriteEntity(
            malformedAnime.id, true, "GER_SUB", null, true, true, true, 900
        ))

        assertEquals(1, dao.repairMalformedAniWorldEpisodeIdentities())

        assertEquals(listOf(canonicalRelease.sourceReleaseId),
            dao.releaseRowsForSource("ANIWORLD_CALENDAR").map { it.release.sourceReleaseId })
        assertTrue(dao.favorite(canonicalAnime.id)?.enabled == true)
        assertEquals(null, dao.favorite(malformedAnime.id))
    }

    @Test
    fun `episode provider availability persists first detection across upsert`() = runBlocking {
        val anime = anime("aniworld:persist", "Persist")
        val release = EpisodeReleaseEntity("aniworld:persist:1", anime.id, 1, null, 1_000, null,
            "ANIWORLD_CALENDAR", "https://aniworld.to/animekalender", null, 900,
            seasonNumber = 1, releaseLanguage = "GER_SUB")
        dao.upsertAnime(listOf(anime)); dao.upsertEpisodeReleases(listOf(release))
        val first = EpisodeProviderAvailabilityEntity(
            "availability:1", release.sourceReleaseId, "provider", "Provider", 1, 1,
            "AVAILABLE_GER_SUB", true, false, "FLATRATE", 1_100, null, 1_100, null, 0,
            "https://example.org/episode", "PUBLIC_EPISODE_METADATA", "https://example.org/evidence", null,
            "DIRECT_PROVIDER_CHECK")
        dao.upsertEpisodeProviderAvailability(listOf(first))
        dao.upsertEpisodeProviderAvailability(listOf(first.copy(lastCheckedAt = 1_200)))

        val stored = dao.episodeProviderAvailability(release.sourceReleaseId).single()
        assertEquals(1_100L, stored.firstAvailableAt)
        assertEquals(1_200L, stored.lastCheckedAt)
        assertEquals("AVAILABLE_GER_SUB", stored.status)
    }

    @Test
    fun `scheduled favorite release and delivery survive repeated upsert`() = runBlocking {
        val anime = anime("aniworld:notify", "Notify")
        val release = EpisodeReleaseEntity("aniworld:notify:1", anime.id, 1, null, 2_000, null,
            "ANIWORLD_CALENDAR", "https://aniworld.to/animekalender", null, 900,
            seasonNumber = 1, releaseLanguage = "GER_SUB")
        dao.upsertAnime(listOf(anime))
        dao.upsertEpisodeReleases(listOf(release))
        dao.upsertFavorite(favorite(anime.id, true).copy(languagePreference = "SUB"))
        val scheduled = ScheduledReleaseNotificationEntity(
            release.sourceReleaseId, anime.id, 2_000, "SUB",
            "anisentinel.favorite-release.${release.sourceReleaseId}", 1_000
        )
        dao.upsertScheduledReleaseNotifications(listOf(scheduled))
        dao.upsertScheduledReleaseNotifications(listOf(scheduled.copy(scheduledAt = 1_100)))
        val delivery = NotificationDeliveryEntity(
            "${release.sourceReleaseId}:RELEASE_DUE", release.sourceReleaseId,
            anime.id, "RELEASE_DUE", 2_000, 42
        )
        dao.upsertNotificationDelivery(delivery)
        dao.upsertNotificationDelivery(delivery.copy(deliveredAt = 2_000))

        assertEquals(1, dao.scheduledReleaseNotifications().size)
        assertEquals(delivery, dao.notificationDelivery(delivery.deliveryId))
        assertEquals(listOf(release.sourceReleaseId), dao.dueFavoriteReleases(2_000, 0).map { it.sourceReleaseId })
    }

    @Test
    fun `favorite language filters sub and dub release jobs`() = runBlocking {
        val anime = anime("aniworld:language", "Language")
        dao.upsertAnime(listOf(anime))
        dao.upsertEpisodeReleases(listOf(
            EpisodeReleaseEntity("sub", anime.id, 2, null, 3_000, null, "ANIWORLD_CALENDAR", null, null, 1, releaseLanguage = "GER_SUB"),
            EpisodeReleaseEntity("dub", anime.id, 1, null, 3_100, null, "ANIWORLD_CALENDAR", null, null, 1, releaseLanguage = "GER_DUB")
        ))

        assertEquals(listOf("sub"), dao.futureFavoriteReleases(anime.id, "SUB", 0).map { it.sourceReleaseId })
        assertEquals(listOf("dub"), dao.futureFavoriteReleases(anime.id, "DUB", 0).map { it.sourceReleaseId })
        assertEquals(2, dao.futureFavoriteReleases(anime.id, "BOTH", 0).size)
    }

    @Test
    fun `historical favorite release is classification-only and never schedulable`() = runBlocking {
        val title = anime("crunchyroll:history-favorite", "Historischer Favorit")
        val historical = EpisodeReleaseEntity(
            "crunchyroll:history-favorite:12", title.id, 12, null, 1_000, "Crunchyroll",
            "CRUNCHYROLL_PUBLIC", "https://www.crunchyroll.com/de/series/TEST/title",
            "https://www.crunchyroll.com/de/watch/TEST/episode", 2_000,
            seasonNumber = 1, releaseLanguage = "GER_SUB", isHistoricalImport = true,
            historicalReleasedAt = 1_000
        )
        dao.upsertAnime(listOf(title))
        dao.upsertEpisodeReleases(listOf(historical))
        dao.upsertFavorite(favorite(title.id, true).copy(languagePreference = "SUB"))

        assertEquals(listOf(historical.sourceReleaseId),
            dao.observeFavoriteReleasesForClassification().first().map { it.sourceReleaseId })
        assertTrue(dao.observeSchedulableFavoriteReleases().first().isEmpty())
        assertTrue(dao.futureFavoriteReleases(title.id, "SUB", 0).isEmpty())
        assertTrue(dao.dueFavoriteReleases(2_000, 0).isEmpty())
    }

    @Test
    fun `future release remains schedulable beside historical favorite history`() = runBlocking {
        val title = anime("favorite:next-season", "Neue Staffel")
        val historical = EpisodeReleaseEntity(
            "favorite:history", title.id, 12, null, 1_000, "ADN", "ADN_PUBLIC_METADATA",
            null, null, 2_000, seasonNumber = 1, releaseLanguage = "GER_SUB",
            isHistoricalImport = true, historicalReleasedAt = 1_000
        )
        val future = EpisodeReleaseEntity(
            "favorite:future", title.id, 1, null, 9_000, "Crunchyroll", "ANIWORLD_CALENDAR",
            null, null, 2_000, seasonNumber = 2, releaseLanguage = "GER_SUB"
        )
        dao.upsertAnime(listOf(title))
        dao.upsertEpisodeReleases(listOf(historical, future))
        dao.upsertFavorite(favorite(title.id, true).copy(languagePreference = "SUB"))

        assertEquals(2, dao.observeFavoriteReleasesForClassification().first().size)
        assertEquals(listOf(future.sourceReleaseId),
            dao.observeSchedulableFavoriteReleases().first().map { it.sourceReleaseId })
        assertEquals(listOf(future.sourceReleaseId),
            dao.futureFavoriteReleases(title.id, "SUB", 2_001).map { it.sourceReleaseId })
    }

    @Test
    fun `aniworld sync preserves ordinary releases that are already historical`() = runBlocking {
        val title = anime("aniworld:history", "Historie")
        val historical = EpisodeReleaseEntity(
            "aniworld:history:1", title.id, 1, null, 100, null,
            "ANIWORLD_CALENDAR", "https://aniworld.to/animekalender", null, 100,
            releaseLanguage = "GER_SUB"
        )
        dao.upsertAnime(listOf(title))
        dao.upsertEpisodeReleases(listOf(historical))

        dao.replaceAniWorldReleaseRange(0, 500, emptyList(), emptyList(), emptyList(), 200)

        assertNotNull(dao.release(historical.sourceReleaseId))
    }

    @Test
    fun `active release catalog excludes justwatch-only titles`() = runBlocking {
        val releaseTitle = anime("aniworld:active", "Aktiver Release")
        val catalogOnly = anime("justwatch:old", "Alter Katalogtitel")
        dao.upsertAnime(listOf(releaseTitle, catalogOnly))
        dao.upsertEpisodeReleases(listOf(EpisodeReleaseEntity(
            "aniworld:active:1", releaseTitle.id, 1, null, 1_000, null,
            "ANIWORLD_CALENDAR", null, null, 1, releaseLanguage = "GER_DUB"
        )))

        assertEquals(listOf(releaseTitle.id), dao.observeActiveAniWorldAnime(0).first().map { it.id })
    }

    private fun anime(id: String, title: String) = AnimeEntity(
        id = id,
        anilistId = null,
        anisearchId = null,
        titleGerman = title,
        titleEnglish = null,
        titleRomaji = null,
        titleNative = null,
        description = "",
        coverUrl = null,
        bannerUrl = null,
        season = null,
        seasonYear = null,
        totalEpisodes = null,
        updatedAt = 1
    )

    private fun favorite(id: String, enabled: Boolean) = FavoriteEntity(
        animeId = id,
        enabled = enabled,
        languagePreference = "BOTH",
        monitoringProfileId = null,
        notifyAvailable = true,
        notifyDelayed = true,
        notifyPostponed = true,
        createdAt = 1
    )
}
