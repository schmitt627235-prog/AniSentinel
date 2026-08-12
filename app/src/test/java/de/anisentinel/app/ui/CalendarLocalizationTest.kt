package de.anisentinel.app.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarLocalizationTest {
    @Test
    fun `german calendar starts with localized monday`() {
        assertTrue(weekdayLabels(Locale.GERMAN).first().startsWith("Mo"))
    }

    @Test
    fun `english calendar uses english weekdays and starts on monday`() {
        val labels = weekdayLabels(Locale.ENGLISH)

        assertEquals("Mon", labels.first())
        assertFalse(labels.contains("Mo"))
    }
}
