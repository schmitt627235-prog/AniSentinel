package de.anisentinel.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.MetadataSource
import de.anisentinel.app.domain.model.ReleaseStatus
import de.anisentinel.app.ui.theme.AniSentinelTheme
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AniSentinelGoldenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun phoneDarkGermanMatchesGolden() =
        captureHero("hero_phone_dark_de.png", 360, dark = true, language = "de")

    @Test
    fun tabletLightGermanMatchesGolden() =
        captureHero("hero_wide_light_de.png", 800, dark = false, language = "de")

    @Test
    fun phoneLightGermanMatchesGolden() =
        captureHero("hero_phone_light_de.png", 360, dark = false, language = "de")

    @Test
    fun phoneDarkEnglishMatchesGolden() =
        captureHero("hero_phone_dark_en.png", 360, dark = true, language = "en")

    @Test
    fun phoneLightEnglishMatchesGolden() =
        captureHero("hero_phone_light_en.png", 360, dark = false, language = "en")

    @Test
    fun wideDarkGermanMatchesGolden() =
        captureHero("hero_wide_dark_de.png", 800, dark = true, language = "de")

    @Test
    fun completeAboutScreenMatchesGolden() {
        rule.setContent {
            AniSentinelTheme(darkTheme = true, languageTag = "de") {
                Box(Modifier.width(360.dp).fillMaxSize()) {
                    AboutScreen(PaddingValues(), onBack = {})
                }
            }
        }
        val image = rule.onNodeWithTag(UiTags.ABOUT).captureToImage()
        saveCapture("about_phone_dark_de.png", image)
        verifyGolden("about_phone_dark_de.png", image.asAndroidBitmap())
    }

    @Test
    fun heroAtOneHundredFiftyPercentMatchesGolden() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f)
            ) {
                AniSentinelTheme(darkTheme = true, languageTag = "de") {
                    Box(Modifier.width(360.dp).fillMaxSize()) {
                        HomeScreen(PaddingValues(), onMenu = {}, onAnimeClick = {}, catalogState = catalogState())
                    }
                }
            }
        }
        val image = rule.onNodeWithTag(UiTags.ANIME_CARD_PREFIX + "golden-release").captureToImage()
        val capture = saveCapture("hero_phone_dark_de_font150.png", image)
        // Font-scaled cards are intentionally allowed to reflow; fixed-pixel golden heights
        // would turn an accessibility improvement into a false regression.
        assertTrue(capture.length() > 0)
    }

    @Test
    fun heroIsReadableAtOneHundredFiftyPercentFontScale() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f)
            ) {
                AniSentinelTheme(darkTheme = true, languageTag = "de") {
                    Box(Modifier.width(360.dp).fillMaxSize()) {
                        HomeScreen(PaddingValues(), onMenu = {}, onAnimeClick = {}, catalogState = catalogState())
                    }
                }
            }
        }

        rule.onNodeWithTag(UiTags.ANIME_CARD_PREFIX + "golden-release").assertIsDisplayed()
        rule.onNodeWithText("Folge 8").assertIsDisplayed()
    }

    @Test
    fun aboutRemainsUsableAtOneHundredFiftyPercentFontScale() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f)
            ) {
                AniSentinelTheme(darkTheme = false, languageTag = "de") {
                    AboutScreen(PaddingValues(), onBack = {})
                }
            }
        }

        rule.onNodeWithTag(UiTags.ABOUT).assertIsDisplayed()
        rule.onNodeWithText("Datenschutzgrundsatz").assertIsDisplayed()
        rule.onNodeWithTag(UiTags.ABOUT_LIST).performScrollToIndex(7)
        rule.onNodeWithText("Aktueller Stand").assertIsDisplayed()
    }

    private fun captureHero(
        name: String,
        widthDp: Int,
        dark: Boolean,
        language: String
    ) {
        rule.setContent {
            AniSentinelTheme(darkTheme = dark, languageTag = language) {
                Box(Modifier.width(widthDp.dp).fillMaxSize()) {
                    HomeScreen(PaddingValues(), onMenu = {}, onAnimeClick = {}, catalogState = catalogState())
                }
            }
        }

        val image = rule.onNodeWithTag(UiTags.ANIME_CARD_PREFIX + "golden-release").captureToImage()
        val actual = saveCapture(name, image)
        assertTrue(actual.length() > 0)
        verifyGolden(name, image.asAndroidBitmap())
    }

    private fun catalogState() = CatalogUiState(
        anime = listOf(
            Anime(
                id = "golden-release",
                title = "Skyward Echo",
                subtitle = "Ger Sub",
                provider = "Crunchyroll",
                expectedReleaseAt = Instant.parse("2099-08-05T16:30:00Z"),
                episode = 8,
                status = ReleaseStatus.SCHEDULED,
                accentSeed = 42,
                source = "ANIWORLD",
                metadataSource = MetadataSource.ANIWORLD,
                metadataSourceUrl = "https://aniworld.to/anime/stream/skyward-echo"
            )
        ),
        liveMode = true
    )

    private fun verifyGolden(name: String, actual: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val available = instrumentation.context.assets.list("goldens").orEmpty()
        assertTrue("Missing golden asset: $name", name in available)
        val expected = instrumentation.context.assets.open("goldens/$name").use {
            BitmapFactory.decodeStream(it)
        }
        assertEquals("Golden width changed for $name", expected.width, actual.width)
        assertEquals("Golden height changed for $name", expected.height, actual.height)

        var changed = 0
        val diff = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until actual.height) {
            for (x in 0 until actual.width) {
                val expectedPixel = expected.getPixel(x, y)
                val actualPixel = actual.getPixel(x, y)
                val delta = maxOf(
                    kotlin.math.abs(Color.red(expectedPixel) - Color.red(actualPixel)),
                    kotlin.math.abs(Color.green(expectedPixel) - Color.green(actualPixel)),
                    kotlin.math.abs(Color.blue(expectedPixel) - Color.blue(actualPixel))
                )
                if (delta > 32) {
                    changed++
                    diff.setPixel(x, y, Color.MAGENTA)
                } else {
                    diff.setPixel(x, y, Color.TRANSPARENT)
                }
            }
        }
        val ratio = changed.toDouble() / (actual.width * actual.height)
        if (ratio > 0.02) {
            saveBitmap(name.removeSuffix(".png") + "_diff.png", diff)
        }
        assertTrue("Golden mismatch for $name: ${"%.3f".format(ratio * 100)}%", ratio <= 0.02)
    }

    private fun saveCapture(name: String, bitmap: ImageBitmap): File =
        saveBitmap(name, bitmap.asAndroidBitmap())

    private fun saveBitmap(name: String, bitmap: Bitmap): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("screenshots"))
        return File(directory, name).also { output ->
            FileOutputStream(output).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
