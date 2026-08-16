package de.anisentinel.app.data.provider

import org.jsoup.Jsoup
import java.util.Locale

object MetadataTextNormalizer {
    fun decode(value: String?): String? = value
        ?.let { Jsoup.parseBodyFragment(it).text() }
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)

    fun detectedLanguage(value: String?): String? {
        val text = decode(value)?.lowercase(Locale.ROOT) ?: return null
        val words = Regex("[\\p{L}']+").findAll(text).map { it.value }.toList()
        if (words.isEmpty()) return "und"
        val german = words.count { it in germanWords }
        val english = words.count { it in englishWords }
        return when {
            (german >= 1 || text.any { it in "äöüß" }) && german >= english -> "de"
            english >= 1 && english > german -> "en"
            else -> "und"
        }
    }

    fun normalizeGenres(values: Iterable<String>, labels: Map<String, String> = emptyMap()): List<String> {
        val seen = linkedSetOf<String>()
        return values.mapNotNull { raw ->
            val decoded = decode(labels[raw] ?: raw) ?: return@mapNotNull null
            val canonical = genreAliases[decoded.lowercase(Locale.GERMAN)] ?: decoded
            val key = canonical.lowercase(Locale.GERMAN)
                .replace("komödien", "komödie")
                .replace(Regex("[^\\p{L}0-9]+"), "")
            canonical.takeIf { seen.add(key) }
        }
    }

    private val germanWords = setOf("der", "die", "das", "und", "ist", "eine", "einer", "mit", "von", "für", "als", "auf", "sich", "sein", "ihre", "seine", "wird")
    private val englishWords = setOf("the", "and", "is", "are", "a", "an", "with", "from", "for", "as", "on", "his", "her", "their", "becomes")
    private val genreAliases = mapOf(
        "action & adventure" to "Action & Abenteuer",
        "action & abenteuer" to "Action & Abenteuer",
        "comedy" to "Komödie",
        "komödien" to "Komödie",
        "science fiction" to "Science-Fiction",
        "sci-fi" to "Science-Fiction"
    )
}

data class GermanSynopsis(
    val original: String,
    val originalLanguage: String,
    val german: String,
    val source: String
)

object GermanSynopsisResolver {
    fun resolve(originalValue: String?, translate: (String) -> String?): GermanSynopsis? {
        val original = MetadataTextNormalizer.decode(originalValue) ?: return null
        val language = MetadataTextNormalizer.detectedLanguage(original) ?: "und"
        if (language == "de") return GermanSynopsis(original, language, original, "JUSTWATCH_DE")
        val translated = MetadataTextNormalizer.decode(translate(original)) ?: return null
        return translated.takeIf { MetadataTextNormalizer.detectedLanguage(it) == "de" }
            ?.let { GermanSynopsis(original, language, it, "TRANSLATED_FROM_JUSTWATCH") }
    }
}
