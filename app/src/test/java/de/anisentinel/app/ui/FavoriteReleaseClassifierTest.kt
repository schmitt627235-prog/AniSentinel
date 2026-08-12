package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.ReleaseStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class FavoriteReleaseClassifierTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 8, 9)
    private val anime = Anime("a", "Titel", "", "Crunchyroll", null, 4, ReleaseStatus.UNKNOWN, 1, totalEpisodes = 4)

    @Test fun currentMeansWholeLocalCalendarDay() {
        val morning = release("today", today, 0, 1)
        val evening = release("evening", today, 23, 59)
        assertTrue(FavoriteReleaseClassifier.matches(anime, listOf(morning), FavoritesFilter.CURRENT, today, zone))
        assertTrue(FavoriteReleaseClassifier.matches(anime, listOf(evening), FavoritesFilter.CURRENT, today, zone))
        assertFalse(FavoriteReleaseClassifier.matches(anime, listOf(release("yesterday", today.minusDays(1))), FavoritesFilter.CURRENT, today, zone))
    }

    @Test fun upcomingStartsTomorrowNotLaterToday() {
        assertFalse(FavoriteReleaseClassifier.matches(anime, listOf(release("today", today, 23, 59)), FavoritesFilter.UPCOMING, today, zone))
        assertTrue(FavoriteReleaseClassifier.matches(anime, listOf(release("tomorrow", today.plusDays(1))), FavoritesFilter.UPCOMING, today, zone))
    }

    @Test fun animeWithTodayAndFutureReleaseIsCurrentOnly() {
        val releases = listOf(
            release("today", today, 20, 0, episode = 6),
            release("next-week", today.plusWeeks(1), 20, 0, episode = 7)
        )
        assertTrue(FavoriteReleaseClassifier.matches(anime, releases, FavoritesFilter.CURRENT, today, zone))
        assertFalse(FavoriteReleaseClassifier.matches(anime, releases, FavoritesFilter.UPCOMING, today, zone))
    }

    @Test fun completedMeansPastReleaseWithoutNextConcreteDate() {
        val finalPast = release("final", today.minusDays(1), episode = 4)
        assertTrue(FavoriteReleaseClassifier.matches(anime, listOf(finalPast), FavoritesFilter.COMPLETED, today, zone))
        assertFalse(FavoriteReleaseClassifier.matches(anime, listOf(finalPast, release("future", today.plusDays(2), episode = 5)), FavoritesFilter.COMPLETED, today, zone))
        assertTrue(FavoriteReleaseClassifier.matches(anime.copy(totalEpisodes = null), listOf(finalPast), FavoritesFilter.COMPLETED, today, zone))
    }

    @Test fun announcementWithoutReleaseCannotMoveFavoriteToUpcoming() {
        val past = release("last-known", today.minusWeeks(1), episode = 12)
        assertTrue(FavoriteReleaseClassifier.matches(anime, listOf(past), FavoritesFilter.COMPLETED, today, zone))
        assertFalse(FavoriteReleaseClassifier.matches(anime, listOf(past), FavoritesFilter.UPCOMING, today, zone))
    }

    @Test fun concreteNewSeasonReleaseMovesFavoriteToUpcoming() {
        val releases = listOf(
            release("season-one", today.minusWeeks(2), episode = 12),
            release("season-two", today.plusMonths(2), episode = 1)
        )
        assertFalse(FavoriteReleaseClassifier.matches(anime, releases, FavoritesFilter.COMPLETED, today, zone))
        assertTrue(FavoriteReleaseClassifier.matches(anime, releases, FavoritesFilter.UPCOMING, today, zone))
    }

    @Test fun historicalReleaseWithoutFutureIsCompleted() {
        val historical = release("cr-history", today.minusMonths(3), episode = 12)
            .copy(isHistoricalImport = true, metadataSource = "CRUNCHYROLL_PUBLIC")
        assertTrue(FavoriteReleaseClassifier.matches(anime, listOf(historical), FavoritesFilter.COMPLETED, today, zone))
        assertFalse(FavoriteReleaseClassifier.matches(anime, listOf(historical), FavoritesFilter.UPCOMING, today, zone))
    }

    @Test fun concreteFutureReleaseMovesHistoricalFavoriteToUpcoming() {
        val historical = release("adn-history", today.minusMonths(2), episode = 12)
            .copy(isHistoricalImport = true, metadataSource = "ADN_PUBLIC_METADATA")
        val future = release("new-season", today.plusMonths(1), episode = 1)
        assertFalse(FavoriteReleaseClassifier.matches(anime, listOf(historical, future), FavoritesFilter.COMPLETED, today, zone))
        assertTrue(FavoriteReleaseClassifier.matches(anime, listOf(historical, future), FavoritesFilter.UPCOMING, today, zone))
    }

    private fun release(id: String, date: LocalDate, hour: Int = 12, minute: Int = 0, episode: Int = 1) =
        EpisodeReleaseEntity(
            id, "a", episode, null, date.atTime(hour, minute).atZone(zone).toEpochSecond(),
            null, "ANIWORLD_CALENDAR", "https://aniworld.to/animekalender", null, 0,
            1, null, 10, false, "SCHEDULED", "GER_SUB"
        )
}
