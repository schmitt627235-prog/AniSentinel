package de.anisentinel.app.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataTextNormalizerTest {
    @Test fun decodesNamedAndNumericHtmlEntities() {
        assertEquals("It's Action & Abenteuer", MetadataTextNormalizer.decode("It&#x27;s Action &amp; Abenteuer"))
    }

    @Test fun detectsGermanAndEnglishDescriptions() {
        assertEquals("de", MetadataTextNormalizer.detectedLanguage("Der Held ist mit seiner Freundin auf einer Reise."))
        assertEquals("en", MetadataTextNormalizer.detectedLanguage("The hero is on a journey with his friends."))
        assertNull(MetadataTextNormalizer.detectedLanguage(null))
    }

    @Test fun semanticallyDeduplicatesGenres() {
        assertEquals(
            listOf("Action & Abenteuer", "Komödie", "Science-Fiction"),
            MetadataTextNormalizer.normalizeGenres(listOf("Action &amp; Abenteuer", "Action & Abenteuer", "Komödien", "Science Fiction"))
        )
    }

    @Test fun englishSynopsisUsesMarkedGermanTranslation() {
        val result = GermanSynopsisResolver.resolve("The hero is on a journey with his friends.") {
            "Der Held ist mit seinen Freunden auf einer Reise."
        }!!
        assertEquals("en", result.originalLanguage)
        assertEquals("TRANSLATED_FROM_JUSTWATCH", result.source)
        assertEquals("Der Held ist mit seinen Freunden auf einer Reise.", result.german)
    }

    @Test fun germanSynopsisIsNotTranslated() {
        var calls = 0
        val result = GermanSynopsisResolver.resolve("Der Held ist mit seiner Freundin auf einer Reise.") { calls++; null }!!
        assertEquals(0, calls)
        assertEquals("JUSTWATCH_DE", result.source)
    }
}
