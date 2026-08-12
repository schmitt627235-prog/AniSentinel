package de.anisentinel.app.data.provider

import de.anisentinel.app.domain.repository.Provider
import de.anisentinel.app.domain.repository.ProviderCheckRequest
import de.anisentinel.app.domain.repository.ProviderCheckResult
import de.anisentinel.app.domain.repository.ProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeProviderRepository(
    private val behavior: FakeProviderBehavior = FakeProviderBehavior.SCHEDULED
) : ProviderRepository {
    override fun providers(): Flow<List<Provider>> = flowOf(listOf(
        Provider("fake-cr", "Crunchyroll (Demo)", true, true),
        Provider("fake-adn", "ADN (Demo)", true, true),
        Provider("fake-aniverse", "aniverse (Demo)", false, true)
    ))

    override suspend fun check(request: ProviderCheckRequest): ProviderCheckResult = when (behavior) {
        FakeProviderBehavior.AVAILABLE -> ProviderCheckResult.Available(request.checkedAt, .99)
        FakeProviderBehavior.UNAVAILABLE -> ProviderCheckResult.Unavailable(request.checkedAt, .9)
        FakeProviderBehavior.ERROR -> ProviderCheckResult.Error(request.checkedAt, "HTTP_503", true)
        FakeProviderBehavior.DELAYED -> ProviderCheckResult.Delayed(request.checkedAt, request.checkedAt.plusSeconds(3_600))
        FakeProviderBehavior.MAINTENANCE -> ProviderCheckResult.Maintenance(request.checkedAt, 900)
        FakeProviderBehavior.MULTILINGUAL -> ProviderCheckResult.Available(
            request.checkedAt, .98,
            setOf(de.anisentinel.app.domain.model.LanguagePreference.SUB, de.anisentinel.app.domain.model.LanguagePreference.DUB),
            request.region
        )
        FakeProviderBehavior.REGION_RESTRICTED -> if (request.region == "DE") {
            ProviderCheckResult.Available(request.checkedAt, .97, region = "DE")
        } else ProviderCheckResult.Error(request.checkedAt, "REGION_RESTRICTED", false)
        FakeProviderBehavior.SCHEDULED -> if (request.episode % 2 == 0) {
            ProviderCheckResult.Available(request.checkedAt, .95)
        } else ProviderCheckResult.Unavailable(request.checkedAt, .85)
    }
}

enum class FakeProviderBehavior {
    SCHEDULED, AVAILABLE, UNAVAILABLE, ERROR, DELAYED, MAINTENANCE, MULTILINGUAL, REGION_RESTRICTED
}
