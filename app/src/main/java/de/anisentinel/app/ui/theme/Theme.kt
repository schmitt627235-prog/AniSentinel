package de.anisentinel.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import android.app.Activity
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import java.util.Locale
import de.anisentinel.app.domain.repository.ThemePreference

val Navy950 = Color(0xFF050811)
val Navy900 = Color(0xFF090E1B)
val Navy800 = Color(0xFF111827)
val Violet400 = Color(0xFFA970FF)
val Violet500 = Color(0xFF8B5CF6)
val Cyan400 = Color(0xFF22D3EE)
val Teal400 = Color(0xFF2DD4BF)
val Orange400 = Color(0xFFFB923C)
val Red400 = Color(0xFFF87171)
val Slate300 = Color(0xFFCBD5E1)
val Slate500 = Color(0xFF64748B)

private val AniSentinelDarkColors = darkColorScheme(
    primary = Violet400,
    onPrimary = Color(0xFF190A32),
    primaryContainer = Color(0xFF2A1750),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Cyan400,
    onSecondary = Color(0xFF002F35),
    secondaryContainer = Color(0xFF073B46),
    onSecondaryContainer = Color(0xFFB6F4FF),
    tertiary = Teal400,
    background = Navy950,
    onBackground = Color(0xFFF8FAFC),
    surface = Navy900,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate300,
    outline = Color(0xFF334155),
    error = Red400
)

private val AniSentinelLightColors = lightColorScheme(
    primary = Color(0xFF6D35C5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF26005B),
    secondary = Color(0xFF006878),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFA9EDFF),
    onSecondaryContainer = Color(0xFF001F26),
    tertiary = Color(0xFF006B5F),
    background = Color(0xFFF7F7FC),
    onBackground = Color(0xFF191A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191A20),
    surfaceVariant = Color(0xFFE9E8F0),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF797680),
    error = Color(0xFFBA1A1A)
)

fun resolveDarkTheme(preference: ThemePreference, systemDark: Boolean): Boolean =
    when (preference) {
        ThemePreference.SYSTEM -> systemDark
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }

@Composable
fun AniSentinelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    languageTag: String = "de",
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = Configuration(baseConfiguration).apply {
        setLocale(Locale.forLanguageTag(languageTag))
    }
    val localizedContext = baseContext.createConfigurationContext(localizedConfiguration)
    val view = LocalView.current
    val colors = if (darkTheme) AniSentinelDarkColors else AniSentinelLightColors

    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.navigationBarColor = colors.background.value.toInt()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AniSentinelTypography,
            shapes = AniSentinelShapes,
            content = content
        )
    }
}
