package de.anisentinel.app.domain.model

enum class ReleaseSourceType {
    ANIME_RADAR,
    ANIWORLD_CALENDAR,
    ANIWORLD_SCHEDULE_CHANGE,
    ANILIST_AIRING,
    ANISEARCH_GERMAN_RELEASE,
    PROVIDER_CONFIRMED,
    LOCAL_DIAGNOSTIC,
    UNKNOWN;

    companion object {
        fun fromMetadataSource(value: String): ReleaseSourceType = when {
            value.startsWith("LOCAL_DIAGNOSTIC:") -> LOCAL_DIAGNOSTIC
            else -> when (value) {
            "ANIME_RADAR" -> ANIME_RADAR
            "ANIWORLD_CALENDAR" -> ANIWORLD_CALENDAR
            "ANIWORLD_SCHEDULE_CHANGE" -> ANIWORLD_SCHEDULE_CHANGE
            "ANILIST_AIRING_SCHEDULE" -> ANILIST_AIRING
            "ANISEARCH_GERMAN_RELEASE" -> ANISEARCH_GERMAN_RELEASE
            "PROVIDER_CONFIRMED" -> PROVIDER_CONFIRMED
            else -> UNKNOWN
            }
        }
    }
}
