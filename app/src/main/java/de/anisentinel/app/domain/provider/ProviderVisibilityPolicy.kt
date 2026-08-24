package de.anisentinel.app.domain.provider

object ProviderVisibilityPolicy {
    val supportedProviderIds = linkedSetOf("crunchyroll", "adn", "netflix", "disney_plus", "aniverse")

    fun canonicalId(value: String): String? = when {
        value.contains("crunchyroll", true) -> "crunchyroll"
        value.equals("adn", true) || value.contains("animation digital network", true) -> "adn"
        value.contains("netflix", true) -> "netflix"
        value.contains("disney", true) -> "disney_plus"
        value.contains("aniverse", true) -> "aniverse"
        else -> null
    }

    fun displayName(id: String): String = when (id) {
        "crunchyroll" -> "Crunchyroll"
        "adn" -> "ADN"
        "netflix" -> "Netflix"
        "disney_plus" -> "Disney+"
        "aniverse" -> "aniverse"
        else -> id
    }

    fun enabledProviderIds(disabledProviderIds: Set<String>): Set<String> =
        supportedProviderIds - disabledProviderIds

    fun isProviderEnabled(provider: String, disabledProviderIds: Set<String>): Boolean =
        canonicalId(provider)?.let { it !in disabledProviderIds } == true

    fun visibleProviders(providers: Iterable<String>, disabledProviderIds: Set<String>): List<String> =
        providers.filter { isProviderEnabled(it, disabledProviderIds) }
            .map(StreamingProviderPolicy::displayName)
            .distinctBy(String::lowercase)
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun isAnimeVisible(providers: Iterable<String>, disabledProviderIds: Set<String>): Boolean {
        val resolved = providers.mapNotNull(::canonicalId).toSet()
        return if (disabledProviderIds.isEmpty()) true else resolved.any { it !in disabledProviderIds }
    }
}
