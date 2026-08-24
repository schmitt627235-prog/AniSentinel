package de.anisentinel.app.domain.release

import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.EpisodeReleaseEntity
import de.anisentinel.app.data.local.ReleasePostponementEntity

/** Source-independent identity used whenever release facts from different sources are joined. */
data class ReleaseIdentity(
    val animeId: String,
    val season: Int,
    val episode: Int,
    val language: String?,
    val provider: String? = null,
    val globalEpisode: Int? = null
) {
    fun sameEpisode(other: ReleaseIdentity, includeProvider: Boolean = false): Boolean =
        animeId == other.animeId && season == other.season && episode == other.episode &&
            normalizedLanguage(language) == normalizedLanguage(other.language) &&
            (!includeProvider || normalizedProvider(provider) == normalizedProvider(other.provider))

    /** Postponement feeds may identify an episode without repeating its language track. */
    fun sameEpisodeAllowingUnspecifiedLanguage(other: ReleaseIdentity): Boolean {
        val ownLanguage = normalizedLanguage(language)
        val otherLanguage = normalizedLanguage(other.language)
        return animeId == other.animeId && season == other.season && episode == other.episode &&
            (ownLanguage == null || otherLanguage == null || ownLanguage == otherLanguage)
    }

    companion object {
        fun from(release: EpisodeReleaseEntity, provider: String? = null) = ReleaseIdentity(
            release.animeId, release.seasonNumber ?: 1, release.episodeNumber ?: 0,
            release.releaseLanguage, provider ?: release.provider
        )

        fun from(postponement: ReleasePostponementEntity): ReleaseIdentity? {
            val animeId = postponement.animeId ?: return null
            val episode = postponement.episodeNumber ?: return null
            val language = postponement.releaseLanguage ?: if (
                postponement.source.contains("ANIWORLD", ignoreCase = true)
            ) "GER_SUB" else null
            return ReleaseIdentity(animeId, postponement.seasonNumber ?: 1, episode, language)
        }

        private fun normalizedLanguage(value: String?): String? = value
            ?.takeUnless { it.equals("UNSPECIFIED", true) || it.isBlank() }
            ?.uppercase()

        private fun normalizedProvider(value: String?): String? = value
            ?.replace("CRUNCHYROLL AMAZON CHANNEL", "CRUNCHYROLL", ignoreCase = true)
            ?.trim()?.uppercase()
    }
}

enum class AvailabilityEvidence(val strength: Int) {
    UNKNOWN(0), DERIVED_FROM_LATER_EPISODE(1), HISTORICAL_PROVIDER_CATALOG(2),
    ANIWORLD_FALLBACK(3), PROVIDER_DIRECT(4);

    companion object {
        fun from(row: EpisodeProviderAvailabilityEntity): AvailabilityEvidence = when {
            row.evidenceType.orEmpty().contains("INFERRED", true) ||
                row.evidenceType.orEmpty().contains("DERIVED", true) -> DERIVED_FROM_LATER_EPISODE
            row.evidenceType.orEmpty().contains("HISTOR", true) -> HISTORICAL_PROVIDER_CATALOG
            row.providerId.equals("ANIWORLD_FALLBACK", true) ||
                row.evidenceType.equals("ANIWORLD_FALLBACK", true) -> ANIWORLD_FALLBACK
            row.status.startsWith("AVAILABLE_") -> PROVIDER_DIRECT
            else -> UNKNOWN
        }
    }
}
