package de.anisentinel.app.ui

import de.anisentinel.app.data.local.EpisodeReleaseEntity

object EpisodeCardResolver {
    fun visibleEpisodeNumbers(totalEpisodes: Int, releases: List<EpisodeReleaseEntity>): List<Int> =
        ((1..totalEpisodes.coerceAtLeast(0)) + releases.mapNotNull { it.episodeNumber })
            .filter { it > 0 }
            .distinct()
            .sorted()
}

object AvailabilityActionPolicy {
    fun showCheck(releaseId: String?, newestRelevantReleaseIds: Set<String>, confirmedAvailable: Boolean): Boolean =
        releaseId != null && releaseId in newestRelevantReleaseIds && !confirmedAvailable
}

enum class ProviderSummaryStatus { UNKNOWN, NOT_CHECKED, AVAILABLE, NOT_AVAILABLE_YET, CHECK_FAILED }

data class ProviderSummaryPresentation(val status: ProviderSummaryStatus, val provider: String? = null)

object ProviderSummaryResolver {
    fun resolve(
        hasProviderReference: Boolean,
        titleStatus: String?,
        confirmedEpisodeProviders: List<String>
    ): ProviderSummaryPresentation {
        val confirmedProvider = confirmedEpisodeProviders.firstOrNull { it.isNotBlank() }
        if (confirmedProvider != null) {
            return ProviderSummaryPresentation(ProviderSummaryStatus.AVAILABLE, confirmedProvider)
        }
        return when {
            !hasProviderReference -> ProviderSummaryPresentation(ProviderSummaryStatus.UNKNOWN)
            titleStatus == null -> ProviderSummaryPresentation(ProviderSummaryStatus.NOT_CHECKED)
            titleStatus.startsWith("AVAILABLE") -> ProviderSummaryPresentation(ProviderSummaryStatus.AVAILABLE)
            titleStatus == "NOT_AVAILABLE_YET" -> ProviderSummaryPresentation(ProviderSummaryStatus.NOT_AVAILABLE_YET)
            else -> ProviderSummaryPresentation(ProviderSummaryStatus.CHECK_FAILED)
        }
    }
}
