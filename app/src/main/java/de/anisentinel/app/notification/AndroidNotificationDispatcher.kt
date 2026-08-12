package de.anisentinel.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.anisentinel.app.MainActivity
import de.anisentinel.app.R
import de.anisentinel.app.domain.notification.LocalNotification
import de.anisentinel.app.domain.notification.NotificationChannel as DomainChannel

class AndroidNotificationDispatcher(private val context: Context) {
    fun createChannels(languageTag: String? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val localizedContext = languageTag?.let { tag ->
            val configuration = context.resources.configuration
            val localized = android.content.res.Configuration(configuration)
            localized.setLocale(java.util.Locale.forLanguageTag(tag))
            context.createConfigurationContext(localized)
        } ?: context
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            DomainChannel.entries.map { channel ->
                NotificationChannel(
                    channelId(channel),
                    localizedContext.getString(channelName(channel)),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            }
        )
    }

    fun canPostNotifications(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED)

    fun dispatch(notification: LocalNotification): Boolean {
        if (!canPostNotifications()) return false
        val openApp = PendingIntent.getActivity(
            context,
            notification.stableId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                notification.targetAnimeId?.let { animeId ->
                    data = android.net.Uri.Builder().scheme("anisentinel").authority("release")
                        .appendPath(animeId)
                        .appendPath((notification.targetSeason ?: 0).toString())
                        .appendPath((notification.targetEpisode ?: 0).toString())
                        .appendPath(notification.targetLanguage ?: "UNSPECIFIED")
                        .build()
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val built = NotificationCompat.Builder(context, channelId(notification.channel))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context)
            .notify(notification.stableId.hashCode(), built)
        return true
    }

    private fun channelId(channel: DomainChannel): String = when (channel) {
        DomainChannel.REMINDERS -> "anisentinel_reminders"
        DomainChannel.RELEASES -> "anisentinel_releases"
        DomainChannel.DELAYS -> "anisentinel_delays"
        DomainChannel.SYSTEM -> "anisentinel_system"
    }

    private fun channelName(channel: DomainChannel): Int = when (channel) {
        DomainChannel.REMINDERS -> R.string.notification_channel_reminders
        DomainChannel.RELEASES -> R.string.notification_channel_releases
        DomainChannel.DELAYS -> R.string.notification_channel_delays
        DomainChannel.SYSTEM -> R.string.notification_channel_system
    }
}
