package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.provider.JustWatchTitleMatch

sealed interface TitleMatchDecision {
    data class Unique(val match: JustWatchTitleMatch) : TitleMatchDecision
    data class Ambiguous(val candidates: List<JustWatchTitleMatch>) : TitleMatchDecision
    data object NoMatch : TitleMatchDecision
}

object JustWatchTitleMatcher {
    fun decide(title: String, seriesStartYear: Int?, contentType: String, candidates: List<JustWatchTitleMatch>): TitleMatchDecision {
        val compatible = candidates.filter {
            titleScore(title, it.title, it.releaseYear) >= SAFE_MATCH_SCORE &&
                (seriesStartYear == null || it.releaseYear == null || seriesStartYear == it.releaseYear) &&
                it.contentType.equals(contentType, true)
        }.sortedByDescending { titleScore(title, it.title, it.releaseYear) }
        if (compatible.isEmpty()) return TitleMatchDecision.NoMatch
        val bestScore = titleScore(title, compatible.first().title, compatible.first().releaseYear)
        val equallyGood = compatible.filter { bestScore - titleScore(title, it.title, it.releaseYear) < MIN_SCORE_GAP }
        return when (equallyGood.size) {
            1 -> TitleMatchDecision.Unique(equallyGood.single())
            else -> TitleMatchDecision.Ambiguous(equallyGood)
        }
    }

    internal fun titleScore(requested: String, candidate: String, candidateYear: Int? = null): Double {
        val left = normalize(requested)
        val right = normalize(candidate)
        if (left == right) return 1.0
        val requestedSuffixYear = YEAR_SUFFIX.find(left)?.groupValues?.get(1)?.toIntOrNull()
        val candidateSuffixYear = YEAR_SUFFIX.find(right)?.groupValues?.get(1)?.toIntOrNull()
        val leftWithoutYear = left.replace(YEAR_SUFFIX, "").trim()
        val rightWithoutYear = right.replace(YEAR_SUFFIX, "").trim()
        if (requestedSuffixYear != null && requestedSuffixYear == candidateYear && leftWithoutYear == right) return 0.99
        if (requestedSuffixYear == null && candidateSuffixYear != null && left == rightWithoutYear) return 0.0
        val leftCore = removeSeasonSuffix(left)
        val rightCore = removeSeasonSuffix(right)
        if (leftCore == rightCore) return 0.96
        if (leftCore.length >= 8 && rightCore.length >= 8 &&
            (leftCore.startsWith(rightCore) || rightCore.startsWith(leftCore))) return 0.90
        val leftTokens = leftCore.split(' ').filter { it.length > 1 }.toSet()
        val rightTokens = rightCore.split(' ').filter { it.length > 1 }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        return leftTokens.intersect(rightTokens).size.toDouble() / leftTokens.union(rightTokens).size
    }

    fun isConservativeEquivalent(requestedTitles: Collection<String>, candidate: String): Boolean =
        requestedTitles.filter(String::isNotBlank).any { titleScore(it, candidate) >= SAFE_ENRICHMENT_SCORE }

    internal fun rejectionReasons(
        title: String,
        seriesStartYear: Int?,
        contentType: String,
        candidates: List<JustWatchTitleMatch>
    ): String = candidates.joinToString(" | ") { candidate ->
        val score = titleScore(title, candidate.title, candidate.releaseYear)
        val reasons = buildList {
            if (!candidate.contentType.equals(contentType, true)) add("CONTENT_TYPE")
            if (seriesStartYear != null && candidate.releaseYear != null && seriesStartYear != candidate.releaseYear) add("YEAR_MISMATCH")
            if (score < SAFE_MATCH_SCORE) add("TITLE_SCORE")
        }.ifEmpty { listOf("AMBIGUOUS") }
        "${candidate.title} (${candidate.releaseYear ?: "?"}, ${candidate.contentType}, score=${"%.3f".format(java.util.Locale.ROOT, score)}):${reasons.joinToString("+")}"
    }

    internal fun stableCrossLocaleTop(
        requestedTitle: String,
        seriesStartYear: Int?,
        contentType: String,
        localized: List<JustWatchTitleMatch>,
        english: List<JustWatchTitleMatch>
    ): JustWatchTitleMatch? {
        val localTop = localized.firstOrNull() ?: return null
        val englishTop = english.firstOrNull() ?: return null
        if (localTop.justWatchId != englishTop.justWatchId) return null
        if (!localTop.contentType.equals(contentType, true) || !englishTop.contentType.equals(contentType, true)) return null
        val candidateYear = englishTop.releaseYear ?: localTop.releaseYear
        if (seriesStartYear != null && candidateYear != null && seriesStartYear != candidateYear) return null
        val suffixYear = YEAR_SUFFIX.find(normalize(requestedTitle))?.groupValues?.get(1)?.toIntOrNull()
        if (suffixYear != null && candidateYear != suffixYear) return null
        return englishTop
    }

    private fun removeSeasonSuffix(value: String) = value
        .replace(Regex("\\s+(staffel|season)\\s+\\d+$"), "")
        .replace(Regex("\\s+\\d+(st|nd|rd|th)\\s+season$"), "")
        .trim()

    private fun normalize(value: String) = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\bof the ([a-z0-9]+) era\\b"), "of $1")
        .replace(Regex("\\s+"), " ")
        .trim()

    private const val SAFE_MATCH_SCORE = 0.78
    private const val SAFE_ENRICHMENT_SCORE = 0.95
    private const val MIN_SCORE_GAP = 0.08
    private val YEAR_SUFFIX = Regex("\\s+(?:\\()?((?:19|20)\\d{2})(?:\\))?$")
}
