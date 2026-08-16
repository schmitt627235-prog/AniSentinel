package de.anisentinel.app.domain.notification

import de.anisentinel.app.domain.watcher.NotificationEvent

enum class NotificationChannel { REMINDERS, RELEASES, DELAYS, SYSTEM }

data class LocalNotification(
    val stableId: String,
    val channel: NotificationChannel,
    val title: String,
    val message: String,
    val targetAnimeId: String? = null,
    val targetSeason: Int? = null,
    val targetEpisode: Int? = null,
    val targetLanguage: String? = null
)

data class NotificationPreferences(
    val reminders: Boolean = true,
    val available: Boolean = true,
    val delayed: Boolean = true,
    val providerErrors: Boolean = true
)

interface NotificationCopy {
    val reminderTitle: String
    fun reminderMessage(episode: Int): String
    val availableTitle: String
    fun availableMessage(episode: Int): String
    val delayedTitle: String
    fun delayedMessage(episode: Int): String
    val postponedTitle: String
    fun postponedMessage(episode: Int): String
    val providerErrorTitle: String
    val providerErrorMessage: String
    val maintenanceTitle: String
    fun maintenanceMessage(minutes: Long): String
}

object GermanNotificationCopy : NotificationCopy {
    override val reminderTitle = "Release steht bevor"
    override fun reminderMessage(episode: Int) = "Folge $episode erscheint in Kürze."
    override val availableTitle = "Neue Folge verfügbar"
    override fun availableMessage(episode: Int) = "Folge $episode wurde als verfügbar erkannt."
    override val delayedTitle = "Release verspätet"
    override fun delayedMessage(episode: Int) = "Folge $episode ist noch nicht verfügbar."
    override val postponedTitle = "Release offiziell verschoben"
    override fun postponedMessage(episode: Int) =
        "Für Folge $episode liegt eine offizielle Meldung vor."
    override val providerErrorTitle = "Provider vorübergehend nicht erreichbar"
    override val providerErrorMessage = "Die Prüfung wird später erneut versucht."
    override val maintenanceTitle = "Provider im Wartungsmodus"
    override fun maintenanceMessage(minutes: Long) = "Nächster Versuch in $minutes Minuten."
}

object EnglishNotificationCopy : NotificationCopy {
    override val reminderTitle = "Release coming soon"
    override fun reminderMessage(episode: Int) = "Episode $episode will be released soon."
    override val availableTitle = "New episode available"
    override fun availableMessage(episode: Int) = "Episode $episode was detected as available."
    override val delayedTitle = "Release delayed"
    override fun delayedMessage(episode: Int) = "Episode $episode is not available yet."
    override val postponedTitle = "Release officially postponed"
    override fun postponedMessage(episode: Int) =
        "An official update is available for episode $episode."
    override val providerErrorTitle = "Provider temporarily unavailable"
    override val providerErrorMessage = "The check will be retried later."
    override val maintenanceTitle = "Provider under maintenance"
    override fun maintenanceMessage(minutes: Long) = "Next attempt in $minutes minutes."
}

fun notificationCopyFor(languageTag: String): NotificationCopy =
    if (languageTag.startsWith("en", ignoreCase = true)) {
        EnglishNotificationCopy
    } else {
        GermanNotificationCopy
    }

class NotificationEngine(
    private val copy: NotificationCopy = GermanNotificationCopy
) {
    fun create(
        event: NotificationEvent,
        preferences: NotificationPreferences
    ): LocalNotification? = when (event) {
        is NotificationEvent.ReleaseDue -> null
        is NotificationEvent.ReleaseReminder -> if (preferences.reminders) {
            LocalNotification(
                stableId = "reminder:${event.animeId}:${event.episode}",
                channel = NotificationChannel.REMINDERS,
                title = copy.reminderTitle,
                message = copy.reminderMessage(event.episode)
            )
        } else null
        is NotificationEvent.EpisodeAvailable -> if (preferences.available) {
            LocalNotification(
                stableId = "available:${event.animeId}:${event.episode}",
                channel = NotificationChannel.RELEASES,
                title = copy.availableTitle,
                message = buildString {
                    appendContext(event.animeTitle, event.season)
                    append(copy.availableMessage(event.episode))
                    append(" Anbieter: ")
                    append(event.provider?.takeIf(String::isNotBlank) ?: "noch nicht eindeutig bestätigt")
                    append(".")
                    event.language?.let { append(" Sprachfassung: $it.") }
                    event.firstDetectedAt?.let {
                        val time = it.atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                        append(" Erstmals erkannt um $time Uhr.")
                    }
                },
                targetAnimeId = event.animeId, targetSeason = event.season,
                targetEpisode = event.episode, targetLanguage = event.language
            )
        } else null
        is NotificationEvent.ReleaseDelayed -> if (preferences.delayed) {
            LocalNotification(
                stableId = "delayed:${event.animeId}:${event.episode}",
                channel = NotificationChannel.DELAYS,
                title = copy.delayedTitle,
                message = buildString {
                    appendContext(event.animeTitle, event.season)
                    append(copy.delayedMessage(event.episode))
                }
            )
        } else null
        is NotificationEvent.OfficiallyPostponed -> if (preferences.delayed) {
            LocalNotification(
                stableId = "postponed:${event.animeId}:${event.episode}",
                channel = NotificationChannel.DELAYS,
                title = copy.postponedTitle,
                message = buildString {
                    appendContext(event.animeTitle, event.season)
                    append(copy.postponedMessage(event.episode))
                    event.reason?.takeIf(String::isNotBlank)?.let { append(" Grund: $it.") }
                    event.revisedAt?.let {
                        append(" Neuer Termin: ")
                        append(it.atZone(java.time.ZoneId.systemDefault()).format(
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                        ))
                        append(" Uhr.")
                    }
                },
                targetAnimeId = event.animeId,
                targetSeason = event.season,
                targetEpisode = event.episode
            )
        } else null
        is NotificationEvent.ProviderError -> if (preferences.providerErrors) {
            LocalNotification(
                stableId = "provider-error:${event.animeId}:${event.providerId}",
                channel = NotificationChannel.SYSTEM,
                title = event.animeTitle?.takeIf(String::isNotBlank) ?: copy.providerErrorTitle,
                message = "Anbieterprüfung fehlgeschlagen."
            )
        } else null
        is NotificationEvent.ProviderMaintenance -> if (preferences.providerErrors) {
            LocalNotification(
                stableId = "maintenance:${event.providerId}",
                channel = NotificationChannel.SYSTEM,
                title = copy.maintenanceTitle,
                message = copy.maintenanceMessage(event.retryAfterSeconds / 60)
            )
        } else null
    }

    private fun episodeLabel(title: String?, season: Int?, episode: Int): String = buildString {
        appendContext(title, season)
        append("Folge ").append(episode)
    }

    private fun StringBuilder.appendContext(title: String?, season: Int?) {
        if (!title.isNullOrBlank()) append(title).append(" · ")
        season?.let { append("S").append(it).append(" · ") }
    }
}
