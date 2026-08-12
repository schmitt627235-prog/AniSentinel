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
    fun `release due starts provider check message`() {
        val result = engine.create(
            NotificationEvent.ReleaseDue("atlas", 11),
            NotificationPreferences()
        )

        assertEquals("due:atlas:11", result?.stableId)
        assertTrue(result?.message?.contains("Anbieterprüfung") == true)
    }

    @Test
    fun `real availability adds provider and language evidence`() {
        val result = engine.create(
            NotificationEvent.EpisodeAvailable("atlas", 11, "Crunchyroll", "Deutsch (Sub)"),
            NotificationPreferences()
        )

        assertTrue(result?.message?.contains("Crunchyroll") == true)
        assertTrue(result?.message?.contains("Deutsch (Sub)") == true)
    }

    @Test
    fun `due and available notifications carry concrete release target`() {
        val due = engine.create(NotificationEvent.ReleaseDue("anime:id", 6, "Titel", 2, "GER_SUB"), NotificationPreferences())
        val available = engine.create(NotificationEvent.EpisodeAvailable("anime:id", 6, "Crunchyroll", "GER_SUB", "Titel", 2), NotificationPreferences())

        listOf(due, available).forEach {
            assertEquals("anime:id", it?.targetAnimeId)
            assertEquals(2, it?.targetSeason)
            assertEquals(6, it?.targetEpisode)
        }
        assertEquals("GER_SUB", due?.targetLanguage)
        assertEquals("GER_SUB", available?.targetLanguage)
    }

    @Test
    fun `provider errors are opt in`() {
        val event = NotificationEvent.ProviderError("atlas", "fake-cr", true)
        assertNull(engine.create(event, NotificationPreferences()))
        assertTrue(
            engine.create(
                event,
                NotificationPreferences(providerErrors = true)
            ) != null
        )
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
