package de.anisentinel.app.domain.repository

import de.anisentinel.app.domain.model.Anime
import de.anisentinel.app.domain.model.LanguagePreference
import de.anisentinel.app.domain.model.ReleaseStatus
import java.time.Instant
import kotlinx.coroutines.flow.Flow

interface AnimeRepository {
    fun observeAnime(): Flow<List<Anime>>
    suspend fun anime(id: String): Anime?
    suspend fun setFavorite(id: String, favorite: Boolean)
}

interface ReleaseRepository {
    fun observeUpcoming(from: Instant, until: Instant): Flow<List<Release>>
    suspend fun history(animeId: String): List<ReleaseHistoryItem>
}

interface ProviderRepository {
    fun providers(): Flow<List<Provider>>
    suspend fun check(request: ProviderCheckRequest): ProviderCheckResult
}

interface NewsRepository {
    fun observeNews(animeId: String? = null): Flow<List<NewsItem>>
}

/** Replaceable contract. A production adapter requires an explicitly permitted data path. */
interface GermanMetadataSource {
    suspend fun metadata(externalId: String): GermanMetadataResult
}

sealed interface GermanMetadataResult {
    data class Available(
        val titleGerman: String?,
        val descriptionGerman: String?,
        val providerHints: List<String>,
        val sourceUrl: String
    ) : GermanMetadataResult
    data class Unavailable(val reason: String) : GermanMetadataResult
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(theme: ThemePreference)
    suspend fun setLanguage(languageTag: String)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setWatchProfileId(id: String)
    suspend fun setPreferredProviders(ids: Set<String>)
    suspend fun setLiveDataEnabled(enabled: Boolean)
}

data class Release(
    val animeId: String,
    val episode: Int,
    val expectedAt: Instant,
    val language: LanguagePreference,
    val providerId: String,
    val status: ReleaseStatus
)

data class ReleaseHistoryItem(
    val checkedAt: Instant,
    val status: ReleaseStatus,
    val source: String,
    val confidence: Double
)

data class Provider(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val isFake: Boolean
)

data class ProviderCheckRequest(
    val animeId: String,
    val episode: Int,
    val language: LanguagePreference,
    val providerId: String,
    val checkedAt: Instant,
    val region: String = "DE"
)

sealed interface ProviderCheckResult {
    val checkedAt: Instant

    data class Available(
        override val checkedAt: Instant,
        val confidence: Double,
        val languages: Set<LanguagePreference> = setOf(LanguagePreference.BOTH),
        val region: String = "DE"
    ) : ProviderCheckResult

    data class Unavailable(
        override val checkedAt: Instant,
        val confidence: Double
    ) : ProviderCheckResult

    data class Error(
        override val checkedAt: Instant,
        val code: String,
        val retryable: Boolean = true
    ) : ProviderCheckResult

    data class Delayed(
        override val checkedAt: Instant,
        val expectedAt: Instant?
    ) : ProviderCheckResult

    data class Maintenance(
        override val checkedAt: Instant,
        val retryAfterSeconds: Long
    ) : ProviderCheckResult
}

data class NewsItem(
    val id: String,
    val title: String,
    val publishedAt: Instant,
    val official: Boolean
)

enum class ThemePreference { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val languageTag: String = "de",
    val notificationsEnabled: Boolean = true,
    val watchProfileId: String = "automatic",
    val preferredProviderIds: Set<String> = emptySet(),
    val liveDataEnabled: Boolean = false
)
