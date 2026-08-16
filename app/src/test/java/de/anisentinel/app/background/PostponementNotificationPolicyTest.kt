package de.anisentinel.app.background

import de.anisentinel.app.data.local.ReleasePostponementEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostponementNotificationPolicyTest {
    private fun row(newAt: Long?, active: Boolean = true) = ReleasePostponementEntity(
        "p", "r", "a", "Anime", 1, 2, "GER_SUB", 100, newAt, null,
        "DELAYED", "ANIWORLD", "https://example.org", null, 100, 100, active, 1, 0
    )

    @Test fun `historical postponement imported after revised release stays silent`() =
        assertFalse(shouldNotifyPostponement(row(200), 201))

    @Test fun `future active postponement can notify`() =
        assertTrue(shouldNotifyPostponement(row(300), 201))

    @Test fun `archived postponement stays silent`() =
        assertFalse(shouldNotifyPostponement(row(300, false), 201))
}
