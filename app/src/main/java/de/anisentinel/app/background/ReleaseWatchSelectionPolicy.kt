package de.anisentinel.app.background

import de.anisentinel.app.data.local.EpisodeReleaseEntity

/** Keeps the exact watcher attached only to the currently relevant episode per language. */
object ReleaseWatchSelectionPolicy {
    const val ACTIVE_OVERDUE_WINDOW_SECONDS = 24 * 60 * 60L
    const val SUCCESSOR_SUPERSESSION_GRACE_SECONDS = 4 * 60 * 60L

    data class Selection(
        val active: List<EpisodeReleaseEntity>,
        val staleReleaseIds: Set<String>
    )

    fun select(releases: List<EpisodeReleaseEntity>, now: Long): Selection {
        val eligible = releases.filter {
            !it.isHistoricalImport && it.expectedAt != null &&
                !de.anisentinel.app.domain.watcher.AvailabilityWatchStrategy.isTerminal(it.releaseStatus) &&
                it.releaseStatus != "STALE_UNCONFIRMED"
        }
        val active = mutableListOf<EpisodeReleaseEntity>()
        val stale = mutableSetOf<String>()
        eligible.groupBy { it.releaseLanguage ?: "UNSPECIFIED" }.values.forEach { languageRows ->
            val sorted = languageRows.sortedBy { it.expectedAt }
            val upcoming = sorted.firstOrNull { requireNotNull(it.expectedAt) > now }
            val latestDue = sorted.lastOrNull { requireNotNull(it.expectedAt) <= now }
            val dueStillCurrent = latestDue?.takeIf { due ->
                val age = now - requireNotNull(due.expectedAt)
                val supersededByNextEpisode = upcoming != null &&
                    upcoming.seasonNumber == due.seasonNumber &&
                    (upcoming.episodeNumber ?: Int.MIN_VALUE) > (due.episodeNumber ?: Int.MAX_VALUE)
                age <= ACTIVE_OVERDUE_WINDOW_SECONDS &&
                    !(supersededByNextEpisode && age > SUCCESSOR_SUPERSESSION_GRACE_SECONDS)
            }
            dueStillCurrent?.let(active::add)
            upcoming?.let(active::add)
            sorted.filter { row ->
                requireNotNull(row.expectedAt) <= now && row.sourceReleaseId != dueStillCurrent?.sourceReleaseId
            }.forEach { stale += it.sourceReleaseId }
        }
        return Selection(active.distinctBy { it.sourceReleaseId }, stale)
    }
}
