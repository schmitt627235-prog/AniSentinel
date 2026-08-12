package de.anisentinel.app.data.anilist

data class AniListMediaDto(
    val id: Int,
    val titleRomaji: String?,
    val titleEnglish: String?,
    val titleNative: String?,
    val description: String?,
    val coverUrl: String?,
    val bannerUrl: String?,
    val season: String?,
    val seasonYear: Int?,
    val episodes: Int?,
    val nextEpisode: Int?,
    val nextAiringAt: Long?,
    val updatedAt: Long
)

sealed interface AniListResult {
    data class Success(val media: List<AniListMediaDto>) : AniListResult
    data class HttpError(val code: Int, val retryAfterSeconds: Long?, val failure: AniListFailure) : AniListResult
    data class InvalidResponse(val reason: String) : AniListResult
    data class NetworkError(val reason: String) : AniListResult
}
