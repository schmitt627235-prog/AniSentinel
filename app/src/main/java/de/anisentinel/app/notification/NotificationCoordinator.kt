package de.anisentinel.app.notification

import de.anisentinel.app.domain.notification.NotificationEngine
import de.anisentinel.app.domain.notification.NotificationPreferences
import de.anisentinel.app.domain.notification.notificationCopyFor
import de.anisentinel.app.domain.repository.SettingsRepository
import de.anisentinel.app.domain.watcher.NotificationEvent
import kotlinx.coroutines.flow.first

class NotificationCoordinator(
    private val settingsRepository: SettingsRepository,
    private val dispatcher: AndroidNotificationDispatcher
) {
    suspend fun dispatch(event: NotificationEvent, isTest: Boolean = false): Boolean {
        val settings = settingsRepository.settings.first()
        if (!isTest && !settings.notificationsEnabled) return false
        dispatcher.createChannels(settings.languageTag)
        val notification = NotificationEngine(notificationCopyFor(settings.languageTag))
            .create(event, NotificationPreferences())
            ?: return false
        return dispatcher.dispatch(notification)
    }

    suspend fun refreshChannelNames() {
        dispatcher.createChannels(settingsRepository.settings.first().languageTag)
    }
}
