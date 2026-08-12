package de.anisentinel.app.ui

import de.anisentinel.app.data.local.AnnouncementEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NewsDetailStateTest {
    @Test fun resolvedMissingRoomRowIsNotFoundRatherThanLoading() {
        assertSame(NewsDetailState.NotFound, announcementDetailState(null))
    }

    @Test fun resolvedRoomRowIsFound() {
        val announcement = AnnouncementEntity(
            "id", "key", null, "Titel", "Zusammenfassung", "OTHER", null,
            null, null, null, null, null, 1, "Anime2You", "https://anime2you.de/news/1", null, 2
        )
        assertEquals(NewsDetailState.Found(announcement), announcementDetailState(announcement))
    }
}
