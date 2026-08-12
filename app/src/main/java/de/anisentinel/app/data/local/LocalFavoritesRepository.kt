package de.anisentinel.app.data.local

import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun observeFavorites(): Flow<List<AnimeEntity>>
    fun observeFavorite(animeId: String): Flow<FavoriteEntity?>
    suspend fun setFavorite(favorite: FavoriteEntity)
    suspend fun setFavoriteEnabled(
        animeId: String,
        enabled: Boolean,
        languagePreference: String,
        monitoringProfileId: String?
    )
    suspend fun updateFavoriteConfiguration(
        animeId: String,
        languagePreference: String,
        monitoringProfileId: String?
    )
}

class LocalFavoritesRepository(
    private val dao: AniSentinelDao,
    private val onChanged: suspend (animeId: String, enabled: Boolean) -> Unit = { _, _ -> }
) : FavoritesRepository {
    override fun observeFavorites(): Flow<List<AnimeEntity>> = dao.observeFavorites()
    override fun observeFavorite(animeId: String): Flow<FavoriteEntity?> =
        dao.observeFavorite(animeId)

    override suspend fun setFavorite(favorite: FavoriteEntity) {
        dao.upsertFavorite(favorite)
        onChanged(favorite.animeId, favorite.enabled)
    }

    override suspend fun setFavoriteEnabled(
        animeId: String,
        enabled: Boolean,
        languagePreference: String,
        monitoringProfileId: String?
    ) {
        val existing = dao.favorite(animeId)
        dao.upsertFavorite(
            FavoriteEntity(
                animeId = animeId,
                enabled = enabled,
                languagePreference = languagePreference,
                monitoringProfileId = monitoringProfileId,
                notifyAvailable = existing?.notifyAvailable ?: true,
                notifyDelayed = existing?.notifyDelayed ?: true,
                notifyPostponed = existing?.notifyPostponed ?: true,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
        )
        onChanged(animeId, enabled)
    }

    override suspend fun updateFavoriteConfiguration(
        animeId: String,
        languagePreference: String,
        monitoringProfileId: String?
    ) {
        val existing = dao.favorite(animeId) ?: return
        dao.upsertFavorite(
            existing.copy(
                languagePreference = languagePreference,
                monitoringProfileId = monitoringProfileId
            )
        )
        onChanged(animeId, true)
    }
}
