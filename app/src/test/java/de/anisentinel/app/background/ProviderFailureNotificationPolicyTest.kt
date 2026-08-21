package de.anisentinel.app.background

import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFailureNotificationPolicyTest {
    @Test fun technicalDirectFailureIsRecognizedButDoesNotImmediatelyNotify() {
        assertTrue(hasTechnicalProviderFailure(listOf(row("CHECK_FAILED", "NETFLIX_PUBLIC_WEB"))))
        val evaluated = ProviderFailureNotificationPolicy.evaluate(
            listOf(row("CHECK_FAILED", "NETFLIX_PUBLIC_WEB", attempt = 1)), null, 1_000
        )
        assertFalse(evaluated.shouldNotify)
    }

    @Test fun notAvailableYetIsSilent() {
        assertFalse(hasTechnicalProviderFailure(listOf(row("NOT_AVAILABLE_YET", "DISNEY_PLUS_PUBLIC_WEB"))))
    }

    @Test fun resolverFailureIsNotProviderFailure() {
        assertFalse(hasTechnicalProviderFailure(listOf(row("PROVIDER_CHECK_FAILED", "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC"))))
    }

    @Test fun directSuccessSuppressesStaleTechnicalFailure() {
        assertFalse(hasTechnicalProviderFailure(listOf(
            row("CHECK_FAILED", "CRUNCHYROLL_STRUCTURED_METADATA_PROBE"),
            row("AVAILABLE_GER_SUB", "CRUNCHYROLL_PUBLIC_WEB_PROBE")
        )))
    }

    @Test fun persistentFailureNotifiesOnlyAfterThreeAttemptsAndTenMinutes() {
        val rows = listOf(row("CHECK_FAILED", "CRUNCHYROLL_PUBLIC_WEB_PROBE", attempt = 3))
        val first = ProviderFailureNotificationPolicy.evaluate(rows, null, 1_000).nextState!!
        val second = ProviderFailureNotificationPolicy.evaluate(rows, first, 1_300).nextState!!
        val third = ProviderFailureNotificationPolicy.evaluate(rows, second, 1_601)
        assertTrue(third.shouldNotify)
    }

    @Test fun conclusiveResultResetsProviderFailure() {
        val failure = ProviderFailureNotificationPolicy.evaluate(
            listOf(row("CHECK_FAILED", "CRUNCHYROLL_PUBLIC_WEB_PROBE")), null, 1_000
        ).nextState!!
        val success = ProviderFailureNotificationPolicy.evaluate(
            listOf(row("AVAILABLE_GER_SUB", "CRUNCHYROLL_PUBLIC_WEB_PROBE")), failure, 1_100
        )
        assertFalse(success.shouldNotify)
        assertTrue("provider" in success.resetProviderKeys)
    }

    private fun row(status: String, source: String, attempt: Int = 1) = EpisodeProviderAvailabilityEntity(
        "id:$source", "release", source, "Provider", 1, 1, status,
        null, null, null, null, null, 1, null, attempt,
        null, "TEST", null, null, source
    )
}
