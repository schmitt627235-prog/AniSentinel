package de.anisentinel.app.data.release

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniWorldParserTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun realCalendarFixtureDeduplicatesLanguagesAndSubtractsTenMinutes() {
        val html = javaClass.classLoader!!.getResource("fixtures/aniworld_calendar_2026-08-03.html")!!.readText()
        val entries = AniWorldCalendarParser().parse(html, Instant.parse("2026-08-03T00:00:00Z"), zone)
        val liar = entries.single { it.title == "Liar Game" && it.episodeNumber == 18 }

        assertEquals("2026-08-03T16:10:00Z", liar.listedAt.toString())
        assertEquals("2026-08-03T16:00:00Z", liar.releaseAt.toString())
        assertEquals(-10, liar.adjustmentMinutes)
        assertEquals("GER_SUB", liar.releaseLanguage)
        assertTrue(liar.coverUrl?.startsWith("https://aniworld.to/public/img/cover/liar-game") == true)
        assertEquals(1, entries.count { it.title == "Liar Game" && it.episodeNumber == 18 })
        assertFalse(entries.any { it.releaseLanguage !in setOf("GER_SUB", "GER_DUB") })
    }

    @Test
    fun endOfDayMarkerStillSubtractsTenMinutesAndIsFlagged() {
        val html = """
            <section class='calendarList'><h3>Montag, 03.08.2026</h3>
            <div class='seriesListContainer'><div><a href='/anime/stream/test'>
            <h3 class='seriesTitle'>Test Anime</h3><small>S01E02 <img class='flag' src='/public/img/japanese-german.svg'></small><small>~ 23:59 Uhr</small>
            </a></div></div></section>
        """.trimIndent()
        val entry = AniWorldCalendarParser().parse(html, Instant.EPOCH, zone).single()
        assertTrue(entry.originalTimeWasEndOfDayMarker)
        assertEquals(10 * 60, entry.listedAt.epochSecond - entry.releaseAt.epochSecond)
    }

    @Test
    fun realChangeFixtureParsesKnownAndUnknownReplacementHonestly() {
        val html = javaClass.classLoader!!.getResource("fixtures/aniworld_schedule_changes_2026-08-03.html")!!.readText()
        val changes = AniWorldScheduleChangeParser().parse(html, Instant.parse("2026-08-03T00:00:00Z"), zone)
        val slime = changes.first { it.title == "That Time I Got Reincarnated as a Slime" && it.releaseType == "Sub" }

        assertEquals("2026-07-31", slime.previousDate.toString())
        assertEquals("2026-08-07", slime.revisedDate.toString())
        assertEquals("DELAYED", slime.direction)
        assertTrue(changes.filter { it.revisedDate == null }.all { it.direction == "DELAYED" })
        assertTrue(changes.any { it.title.contains("RE:Zero") })
    }

    @Test
    fun currentRealChangeFixtureKeepsSubAndDubSeparate() {
        val html = javaClass.classLoader!!.getResource("fixtures/aniworld_schedule_changes_live_2026-08-13.html")!!.readText()
        val changes = AniWorldScheduleChangeParser().parse(html, Instant.parse("2026-08-13T00:00:00Z"), zone)
        val iruma = changes.single { it.title == "Welcome to Demon School! Iruma-Kun" }
        assertEquals("Sub+Dub", iruma.releaseType)
        assertEquals("2026-08-08", iruma.previousDate.toString())
        assertEquals("2026-08-15", iruma.revisedDate.toString())
        assertTrue(changes.any { it.title == "That Time I Got Reincarnated as a Slime" && it.releaseType == "Dub" })
        val slimeSub = changes.single {
            it.title == "That Time I Got Reincarnated as a Slime" && it.episodeNumber == 18
        }
        assertEquals("Sub", slimeSub.releaseType)
        assertEquals(30, slimeSub.relativeDelayMinutes)
        assertEquals("2026-08-14", slimeSub.previousDate.toString())
        val snowball = changes.single { it.title == "Snowball Earth" }
        assertEquals("Dub", snowball.releaseType)
        assertEquals(null, snowball.revisedDate)
    }

    @Test
    fun normalizationDoesNotReplaceSeasonAndEpisodeMatching() {
        assertEquals(normalizeAnimeTitle("Example Season 2"), normalizeAnimeTitle("Example Staffel 2"))
        assertFalse(normalizeAnimeTitle("Example Season 2") == normalizeAnimeTitle("Example Season 3"))
    }

    @Test
    fun germanSubAndOlderGermanDubOnSameDayRemainSeparate() {
        val html = """
            <section class='calendarList'><h3>Montag, 03.08.2026</h3><div class='seriesListContainer'>
              <div><a href='/anime/stream/example'><h3 class='seriesTitle'>Example</h3>
                <small>S02E05 <img class='flag' src='/public/img/japanese-german.svg'></small><small>18:10 Uhr</small></a></div>
              <div><a href='/anime/stream/example'><h3 class='seriesTitle'>Example</h3>
                <small>S02E02 <img class='flag' src='/public/img/german.svg'></small><small>20:00 Uhr</small></a></div>
              <div><a href='/anime/stream/example'><h3 class='seriesTitle'>Example</h3>
                <small>S02E05 <img class='flag' src='/public/img/japanese-english.svg'></small><small>18:10 Uhr</small></a></div>
            </div></section>
        """.trimIndent()
        val entries = AniWorldCalendarParser().parse(html, Instant.EPOCH, zone)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.episodeNumber == 5 && it.releaseLanguage == "GER_SUB" })
        assertTrue(entries.any { it.episodeNumber == 2 && it.releaseLanguage == "GER_DUB" })
    }

    @Test
    fun scheduleChangeCanOnlyMatchItsOwnGermanLanguageVariant() {
        assertTrue(aniWorldLanguageMatches("Sub", "GER_SUB"))
        assertTrue(aniWorldLanguageMatches("Dub", "GER_DUB"))
        assertFalse(aniWorldLanguageMatches("Dub", "GER_SUB"))
        assertFalse(aniWorldLanguageMatches("Sub", "GER_DUB"))
        assertFalse(aniWorldLanguageMatches(null, "GER_SUB"))
        assertTrue(aniWorldLanguageMatches("Sub+Dub", "GER_SUB"))
        assertTrue(aniWorldLanguageMatches("Sub+Dub", "GER_DUB"))
    }

    @Test
    fun seriesIdentityNeverUsesEpisodeLinkWhenCardContainsMultipleAnchors() {
        val html = """
            <section class='calendarList'><h3>Sonntag, 09.08.2026</h3>
              <div class='seriesListContainer'><div>
                <a href='/anime/stream/you-and-i-are-polar-opposites/staffel/2/episode-6'>Episode 6</a>
                <h3 class='seriesTitle'><a href='/anime/stream/you-and-i-are-polar-opposites'>You and I Are Polar Opposites</a></h3>
                <small>S02E06 <img class='flag' src='/public/img/japanese-german.svg'></small><small>11:03 Uhr</small>
              </div></div>
            </section>
        """.trimIndent()

        val entry = AniWorldCalendarParser().parse(html, Instant.EPOCH, zone).single()

        assertEquals("you-and-i-are-polar-opposites", entry.externalId)
        assertFalse(entry.externalId!!.startsWith("episode-"))
    }
}
