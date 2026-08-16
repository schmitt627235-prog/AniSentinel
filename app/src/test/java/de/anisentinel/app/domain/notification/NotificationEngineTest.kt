package de.anisentinel.app.domain.notification

import de.anisentinel.app.domain.watcher.NotificationEvent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationEngineTest {
    private val engine = NotificationEngine()

    @Test
    fun `available event uses stable episode id`() {
        val result = engine.create(
            NotificationEvent.EpisodeAvailable("atlas", 11),
            NotificationPreferences()
        )
        assertEquals("available:atlas:11", result?.stableId)
        assertEquals(NotificationChannel.RELEASES, result?.channel)
    }

    @Test
    fun `disabled available notifications are suppressed`() {
        val result = engine.create(
            NotificationEvent.EpisodeAvailable("atlas", 11),
            NotificationPreferences(available = false)
        )
        assertNull(result)
    }

    @Test
    fun `reminder uses reminder channel`() {
        val result = engine.create(
            NotificationEvent.ReleaseReminder(
                "atlas",
                11,
                Instant.parse("2026-07-30T18:00:00Z")
            ),
            NotificationPreferences()
        )
        assertEquals(NotificationChannel.REMINDERS, result?.channel)
    }

    @Test
    fun `release due stays silent`() {
        val result = engine.create(
            NotificationEvent.ReleaseDue("atlas", 11),
            NotificationPreferences()
        )

        assertNull(result)
    }

    @Test
    fun `real availability adds provider and language evidence`() {
        val result = engine.create(
            NotificationEvent.EpisodeAvailable("atlas", 11, "Crunchyroll", "Deutsch (Sub)", "Atlas of Ash", 2),
            NotificationPreferences()
        )

        assertTrue(result?.message?.contains("Atlas of Ash") == true)
        assertTrue(result?.message?.contains("Folge 11") == true)
        assertTrue(result?.message?.contains("Crunchyroll") == true)
        assertTrue(result?.message?.contains("Deutsch (Sub)") == true)
    }

    @Test
    fun `available notification carries concrete release target`() {
        val available = engine.create(NotificationEvent.EpisodeAvailable("anime:id", 6, "Crunchyroll", "GER_SUB", "Titel", 2), NotificationPreferences())

        assertEquals("anime:id", available?.targetAnimeId)
        assertEquals(2, available?.targetSeason)
        assertEquals(6, available?.targetEpisode)
        assertEquals("GER_SUB", available?.targetLanguage)
    }

    @Test
    fun `technical provider error contains anime title and failure text`() {
        val event = NotificationEvent.ProviderError("atlas", "fake-cr", true, "Atlas")
        val result = engine.create(event, NotificationPreferences())
        assertEquals("Atlas", result?.title)
        assertTrue(result?.message?.contains("Anbieterprüfung fehlgeschlagen") == true)
    }

    @Test
    fun `maintenance message includes retry minutes`() {
        val result = engine.create(
            NotificationEvent.ProviderMaintenance("fake-cr", 900),
            NotificationPreferences(providerErrors = true)
        )
        assertTrue(result?.message?.contains("15 Minuten") == true)
    }

    @Test
    fun `english copy produces english notification text`() {
        val result = NotificationEngine(EnglishNotificationCopy).create(
            NotificationEvent.EpisodeAvailable("atlas", 11),
            NotificationPreferences()
        )

        assertEquals("New episode available", result?.title)
        assertTrue(result?.message?.contains("Episode 11") == true)
    }

    @Test
    fun `notification copy follows language tag`() {
        assertEquals(EnglishNotificationCopy, notificationCopyFor("en-US"))
        assertEquals(GermanNotificationCopy, notificationCopyFor("de-DE"))
    }
}
