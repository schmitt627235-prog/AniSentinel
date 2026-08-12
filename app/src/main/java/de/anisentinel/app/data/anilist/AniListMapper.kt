package de.anisentinel.app.data.anilist

import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.domain.model.MetadataSource
import de.anisentinel.app.domain.watcher.ReleaseStatusResolver
import java.time.Clock
import java.time.Instant

fun AniListMediaDto.toEntity(nowEpochSeconds: Long): AnimeEntity = AnimeEntity(
    id = "anilist:$id",
    anilistId = id,
    anisearchId = null,
    titleGerman = "",
    titleEnglish = titleEnglish ?: titleRomaji ?: titleNative ?: "Anime #$id",
    titleRomaji = titleRomaji,
    titleNative = titleNative,
    description = description.orEmpty(),
    coverUrl = coverUrl,
    bannerUrl = bannerUrl,
    season = season,
    seasonYear = seasonYear,
    totalEpisodes = episodes,
    updatedAt = nowEpochSeconds,
    nextAiringAt = nextAiringAt,
    nextEpisode = nextEpisode,
    sourceUpdatedAt = updatedAt.takeIf { it > 0 },
    cachedAt = nowEpochSeconds
)

fun AnimeEntity.toDomain(clock: Clock = Clock.systemUTC()): Anime {
    val releaseAt = nextAiringAt?.let(Instant::ofEpochSecond)
    val isAniList = anilistId != null
    val base = Anime(
    id = id,
    title = titleGerman.takeIf { it.isNotBlank() } ?: titleEnglish ?: titleRomaji ?: titleNative.orEmpty(),
    subtitle = seasonYear?.let { year -> "${season.orEmpty()} $year".trim() }.orEmpty(),
    provider = "",
    expectedReleaseAt = releaseAt,
    episode = nextEpisode ?: totalEpisodes ?: 0,
    status = ReleaseStatus.UNKNOWN,
    accentSeed = anilistId ?: id.hashCode(),
    coverUrl = coverUrl,
    description = description,
    source = if (isAniList) "ANILIST" else "ANIWORLD",
    metadataSource = if (isAniList) MetadataSource.ANILIST else MetadataSource.ANIWORLD,
    metadataSourceUrl = anilistId?.let { "https://anilist.co/anime/$it" },
    totalEpisodes = totalEpisodes,
    nextEpisodeNumber = nextEpisode
    )
    return base.copy(status = ReleaseStatusResolver(clock).resolve(base))
}
