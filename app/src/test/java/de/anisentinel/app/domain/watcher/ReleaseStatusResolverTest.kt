package de.anisentinel.app.domain.watcher

import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.AvailabilityStatus
import de.anisentinel.app.domain.model.ProviderAvailability
import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.domain.model.StreamingProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseStatusResolverTest {
    private val now = Instant.parse("2026-07-30T18:00:00Z")
    private val resolver = ReleaseStatusResolver(Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `past release is reached and never automatically available`() {
        val anime = anime(now.minusSeconds(60))
        assertEquals(ReleaseStatus.RELEASE_TIME_REACHED, resolver.resolve(anime))
    }

    @Test
    fun `future release remains scheduled`() {
        assertEquals(ReleaseStatus.SCHEDULED, resolver.resolve(anime(now.plusSeconds(60))))
    }

    @Test
    fun `only verified availability resolves to available`() {
        val availability = ProviderAvailability(
            StreamingProvider.CRUNCHYROLL,
            5,
            AvailabilityStatus.AVAILABLE,
            "https://example.invalid/episode",
            now
        )
        assertEquals(
            ReleaseStatus.AVAILABLE,
            resolver.resolve(anime(now.minusSeconds(60)).copy(streamingAvailability = listOf(availability)))
        )
    }

    @Test
    fun `next calendar week survives year boundary`() {
        assertEquals(
            ReleaseDateRelation.NEXT_WEEK,
            ReleaseDateClassifier.classify(
                LocalDate.of(2027, 1, 4),
                LocalDate.of(2026, 12, 31)
            )
        )
    }

    private fun anime(releaseAt: Instant?) = Anime(
        id = "real",
        title = "Real",
        subtitle = "",
        provider = "",
        expectedReleaseAt = releaseAt,
        episode = 5,
        status = ReleaseStatus.UNKNOWN,
        accentSeed = 1,
        source = "ANILIST"
    )
}
