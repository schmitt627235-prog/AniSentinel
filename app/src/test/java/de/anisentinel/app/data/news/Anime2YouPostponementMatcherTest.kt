package de.anisentinel.app.data.news

import de.anisentinel.app.data.local.ReleasePostponementEntity
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class Anime2YouPostponementMatcherTest {
    private val shift = ReleasePostponementEntity("p", null, "anime", "Example Anime", 2, 6, "GER_SUB", 100, 200, "TV", "DELAYED", "ANIWORLD", "https://aniworld.to/source", null, 1, 1, true, 1, 0)
    private fun news(title: String, summary: String) = AnnouncementCandidate(title, title, summary, AnnouncementType.DELAY, 2, publishedAt = Instant.EPOCH, source = "Anime2You", sourceUrl = "https://www.anime2you.de/news/example")

    @Test fun streamingDelayCanConfirmMatchingTitle() = assertTrue(Anime2YouPostponementMatcher.matches(shift, news("»Example Anime«: Simulcast verschoben", "Folge 6 erscheint später im TV und Stream.")))
    @Test fun blurayDelayNeverConfirmsEpisodeShift() = assertFalse(Anime2YouPostponementMatcher.matches(shift, news("»Example Anime«: Blu-ray verschoben", "Das zweite Volume erscheint später.")))
    @Test fun differentTitleNeverConfirms() = assertFalse(Anime2YouPostponementMatcher.matches(shift, news("»Different Anime«: Simulcast verschoben", "Episode erscheint später im TV.")))
}
