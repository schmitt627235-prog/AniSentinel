package de.anisentinel.app.domain.watcher

import de.anisentinel.app.data.local.EpisodeReleaseEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class ResolvedReleaseTime(val epochSecond: Long, val precision: String)

/** Prevents a date-only midnight placeholder from becoming a claimed release time. */
object ReleaseTimePolicy {
    fun resolve(
        release: EpisodeReleaseEntity,
        history: List<EpisodeReleaseEntity>,
        zoneId: ZoneId
    ): ResolvedReleaseTime? {
        val epoch = release.expectedAt ?: return null
        val local = Instant.ofEpochSecond(epoch).atZone(zoneId)
        val isPlaceholder = release.releaseTimePrecision == "DATE" ||
            (local.toLocalTime() == LocalTime.MIDNIGHT && release.releaseTimePrecision != "EXACT_MIDNIGHT")
        if (!isPlaceholder) return ResolvedReleaseTime(epoch, release.releaseTimePrecision)
        val usualTime = history.asSequence()
            .filter { it.animeId == release.animeId && it.seasonNumber == release.seasonNumber }
            .filter { it.releaseLanguage == release.releaseLanguage && it.expectedAt != null }
            .filter { it.releaseTimePrecision == "EXACT" || it.releaseTimePrecision.startsWith("DERIVED") }
            .map { Instant.ofEpochSecond(requireNotNull(it.expectedAt)).atZone(zoneId).toLocalTime() }
            .filter { it != LocalTime.MIDNIGHT }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: return ResolvedReleaseTime(epoch, "DATE")
        return ResolvedReleaseTime(local.toLocalDate().atTime(usualTime).atZone(zoneId).toEpochSecond(), "DERIVED_TITLE_PATTERN")
    }
}
