package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.repository.Provider
import de.anisentinel.app.domain.repository.ProviderCheckRequest
import de.anisentinel.app.domain.repository.ProviderCheckResult
import de.anisentinel.app.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Honest production placeholder until a real provider checker is implemented.
 * It never converts missing provider data into availability.
 */
class UnavailableProviderRepository : ProviderRepository {
    override fun providers(): Flow<List<Provider>> = flowOf(emptyList())

    override suspend fun check(request: ProviderCheckRequest): ProviderCheckResult =
        ProviderCheckResult.Error(
            checkedAt = request.checkedAt,
            code = "PROVIDER_NOT_CONFIGURED",
            retryable = false
        )
}
