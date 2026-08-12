package de.anisentinel.app.ui

import de.anisentinel.app.data.local.AnnouncementEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class NewsSourceLinksTest {
    @Test fun sourceNamesRemainAlignedWithRealUrls() {
        val links = newsSourceLinks(news("Anime2You\nAniWorld", "https://anime2you.de/article\nhttps://aniworld.to/evidence"))
        assertEquals(
            listOf(
                NewsSourceLink("Anime2You", "https://anime2you.de/article"),
                NewsSourceLink("AniWorld", "https://aniworld.to/evidence")
            ),
            links
        )
    }

    @Test fun missingOrUnsafeSourceUrlCreatesNoBrowserAction() {
        assertEquals(emptyList<NewsSourceLink>(), newsSourceLinks(news("Anime2You", "")))
        assertEquals(emptyList<NewsSourceLink>(), newsSourceLinks(news("Anime2You", "javascript:alert(1)")))
    }

    private fun news(sources: String, urls: String) = AnnouncementEntity(
        "id", "key", null, "Titel", "Zusammenfassung", "OTHER", null,
        null, null, null, null, null, 1, sources, urls, null, 2
    )
}
