package de.anisentinel.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseStatisticsTest {
    private fun item(id: String) = ReleaseStatisticItem(id, "anime", "Anime", 1, 1, "GER_SUB", 1,
        "EXACT", "Crunchyroll", "AVAILABLE")

    @Test fun `every counter is exactly the size of its visible list`() {
        val items = ReleaseStatisticCategory.entries.associateWith { category ->
            List(category.ordinal + 1) { item("${category.name}:$it") }
        }
        val stats = releaseStatisticsFrom(items)
        assertEquals(items.getValue(ReleaseStatisticCategory.TODAY).size, stats.today)
        assertEquals(items.getValue(ReleaseStatisticCategory.THIS_WEEK).size, stats.thisWeek)
        assertEquals(items.getValue(ReleaseStatisticCategory.GER_SUB).size, stats.germanSub)
        assertEquals(items.getValue(ReleaseStatisticCategory.GER_DUB).size, stats.germanDub)
        assertEquals(items.getValue(ReleaseStatisticCategory.AVAILABLE).size, stats.confirmedAvailable)
        assertEquals(items.getValue(ReleaseStatisticCategory.DELAYED).size, stats.delayed)
        assertEquals(items.getValue(ReleaseStatisticCategory.POSTPONED).size, stats.postponed)
    }
}
