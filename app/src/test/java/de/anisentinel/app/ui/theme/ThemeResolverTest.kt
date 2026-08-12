package de.anisentinel.app.ui.theme

import de.anisentinel.app.domain.repository.ThemePreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeResolverTest {
    @Test
    fun `dark always resolves dark`() {
        assertTrue(resolveDarkTheme(ThemePreference.DARK, systemDark = false))
    }

    @Test
    fun `light always resolves light`() {
        assertFalse(resolveDarkTheme(ThemePreference.LIGHT, systemDark = true))
    }

    @Test
    fun `system follows both system states`() {
        assertTrue(resolveDarkTheme(ThemePreference.SYSTEM, systemDark = true))
        assertFalse(resolveDarkTheme(ThemePreference.SYSTEM, systemDark = false))
    }
}
