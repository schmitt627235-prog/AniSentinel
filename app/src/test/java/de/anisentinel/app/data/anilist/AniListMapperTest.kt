package de.anisentinel.app.data.anilist

import de.anisentinel.app.domain.model.ReleaseStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AniListMapperTest {
    @Test
    fun `valid response maps nullable fields and airing data`() {
        val result = AniListClient().parse(VALID_JSON) as AniListResult.Success
        val media = result.media.single()

        assertEquals(16498, media.id)
        assertEquals("Attack on Titan", media.titleEnglish)
        assertEquals(26, media.nextEpisode)
        assertEquals(1_800_000_000L, media.nextAiringAt)
        assertNull(media.episodes)
    }

    @Test
    fun `invalid json becomes typed invalid response`() {
        assertTrue(AniListClient().parse("{broken") is AniListResult.InvalidResponse)
    }

    @Test
    fun `graphql errors become typed invalid response`() {
        assertTrue(
            AniListClient().parse("""{"errors":[{"message":"bad"}]}""") is
                AniListResult.InvalidResponse
        )
    }

    @Test
    fun `mapper preserves live identifiers cover and next airing`() {
        val dto = (AniListClient().parse(VALID_JSON) as AniListResult.Success).media.single()
        val entity = dto.toEntity(1_700_000_000L)
        val domain = entity.toDomain()

        assertEquals("anilist:16498", domain.id)
        assertEquals("https://img.test/cover.jpg", domain.coverUrl)
        assertEquals(Instant.ofEpochSecond(1_800_000_000L), domain.expectedReleaseAt)
        assertEquals(26, domain.episode)
        assertEquals(ReleaseStatus.SCHEDULED, domain.status)
        assertEquals("ANILIST", domain.source)
    }

    @Test
    fun `missing title and airing receive safe fallback`() {
        val dto = AniListMediaDto(
            id = 7,
            titleRomaji = null,
            titleEnglish = null,
            titleNative = null,
            description = null,
            coverUrl = null,
            bannerUrl = null,
            season = null,
            seasonYear = null,
            episodes = null,
            nextEpisode = null,
            nextAiringAt = null,
            updatedAt = 0
        )
        val domain = dto.toEntity(123).toDomain()

        assertEquals("Anime #7", domain.title)
        assertEquals(ReleaseStatus.UNKNOWN, domain.status)
        assertNull(domain.expectedReleaseAt)
    }

    private companion object {
        const val VALID_JSON = """
            {
              "data": {
                "Page": {
                  "media": [{
                    "id": 16498,
                    "title": {
                      "romaji": "Shingeki no Kyojin",
                      "english": "Attack on Titan",
                      "native": null
                    },
                    "description": "Humanity fights.",
                    "coverImage": {
                      "extraLarge": "https://img.test/cover.jpg",
                      "large": null
                    },
                    "bannerImage": null,
                    "season": "SPRING",
                    "seasonYear": 2026,
                    "episodes": null,
                    "updatedAt": 1700000000,
                    "nextAiringEpisode": {
                      "episode": 26,
                      "airingAt": 1800000000
                    }
                  }]
                }
              }
            }
        """
    }
}
