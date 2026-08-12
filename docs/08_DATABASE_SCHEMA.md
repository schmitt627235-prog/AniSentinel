# Lokales Datenmodell (Room)

## AnimeEntity
- id: UUID/String
- anilistId: Int?
- anisearchId: String?
- titleGerman, titleEnglish, titleRomaji, titleNative
- description
- coverUrl, bannerUrl
- season, seasonYear
- totalEpisodes
- updatedAt

## ProviderSeriesEntity
- animeId
- provider
- providerSeriesId
- providerSeasonId
- seriesUrl
- region
- lastVerifiedAt

## FavoriteEntity
- animeId
- enabled
- languagePreference
- monitoringProfileId
- notifyAvailable
- notifyDelayed
- notifyPostponed
- createdAt

## EpisodeEntity
- animeId
- seasonNumber
- episodeNumber
- title
- expectedReleaseAt
- lastUnavailableAt
- firstAvailableAt
- providerEpisodeId
- providerEpisodeUrl
- status
- confidence

## WatchProfileEntity
- id
- name
- isDefault
- stopAfterMinutes
- liveMonitoringAllowed

## WatchPhaseEntity
- profileId
- startOffsetSeconds
- endOffsetSeconds nullable
- intervalSeconds

## CheckHistoryEntity
- id
- episodeKey
- provider
- checkedAt
- outcome
- httpStatus nullable
- confidence
- errorCode nullable

## NewsItemEntity
- sourceId
- externalId/url hash
- title
- publishedAt
- matchedAnimeId
- matchedEpisode
- delayClassification
- newExpectedReleaseAt nullable
- confidence

## UserPreferences
DataStore für Darstellung, Benachrichtigungen, Standardprofil, aktive Quellen und Sprache.
