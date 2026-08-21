package de.anisentinel.app.background

import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.provider.shouldUseAniWorldFallback
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFallbackLifecycleTest {
    @Test fun directAvailableNeverUsesAniWorldFallback() {
        assertFalse(shouldUseAniWorldFallback(true, false, false, true))
        assertFalse(shouldUseAniWorldFallback(true, false, true, true))
    }

    @Test fun parsedNotAvailableYetStillUsesSafetyFallbackAtTPlusTen() {
        assertTrue(shouldUseAniWorldFallback(false, true, false, true))
    }

    @Test fun technicalFailureUsesFallbackOnlyAtTPlusTen() {
        assertFalse(shouldUseAniWorldFallback(false, false, true, false))
        assertTrue(shouldUseAniWorldFallback(false, false, true, true))
    }

    @Test fun fallbackDoesNotRunBeforeTPlusTenRegardlessOfDirectResult() {
        assertFalse(shouldUseAniWorldFallback(false, true, false, false))
        assertFalse(shouldUseAniWorldFallback(false, false, true, false))
    }

    @Test fun fallbackRaceGuardRecognizesPersistedDirectAvailability() {
        assertTrue(releaseAlreadyAvailable("CHECKING", listOf(availability("AVAILABLE_GER_SUB", 1_000))))
        assertTrue(releaseAlreadyAvailable("AVAILABLE", emptyList()))
        assertFalse(releaseAlreadyAvailable("PENDING_CONFIRMATION", listOf(availability("CHECK_FAILED", null))))
    }

    @Test fun subAndDubRowsRemainLanguageDistinct() {
        val sub = availability("AVAILABLE_GER_SUB", 1_000).copy(germanSubAvailable = true)
        val dub = availability("AVAILABLE_GER_DUB", 1_001).copy(
            availabilityId = "dub", germanDubAvailable = true, germanSubAvailable = false
        )
        assertTrue(releaseAlreadyAvailable("CHECKING", listOf(sub)))
        assertTrue(releaseAlreadyAvailable("CHECKING", listOf(dub)))
    }

    @Test fun structuredDirectEvidenceWinsOverAniWorldFallback() {
        val fallback = availability("AVAILABLE_GER_SUB", 1_100).copy(
            availabilityId = "fallback", source = "ANIWORLD_CALENDAR_FALLBACK_V15",
            providerName = "AniWorld", germanSubAvailable = true, lastCheckedAt = 1_100
        )
        val crunchyroll = availability("AVAILABLE_GER_SUB", 1_000).copy(
            availabilityId = "structured", source = "CRUNCHYROLL_STRUCTURED_METADATA_PROBE",
            germanSubAvailable = true
        )
        val selected = selectAvailableEvidence("GER_SUB", listOf(fallback, crunchyroll))
        assertTrue(selected?.providerName == "Crunchyroll")
    }

    @Test fun availabilitySelectionNeverCrossesSubAndDub() {
        val sub = availability("AVAILABLE_GER_SUB", 1_000).copy(germanSubAvailable = true)
        val dub = availability("AVAILABLE_GER_DUB", 1_001).copy(
            availabilityId = "dub", germanDubAvailable = true, source = "ADN_STRUCTURED_METADATA_PROBE"
        )
        assertTrue(selectAvailableEvidence("GER_SUB", listOf(sub, dub))?.availabilityId == "sub")
        assertTrue(selectAvailableEvidence("GER_DUB", listOf(sub, dub))?.availabilityId == "dub")
    }

    private fun availability(status: String, firstAvailableAt: Long?) = EpisodeProviderAvailabilityEntity(
        availabilityId = "sub", releaseId = "release", providerId = "CRUNCHYROLL",
        providerName = "Crunchyroll", seasonNumber = 1, episodeNumber = 6, status = status,
        germanSubAvailable = false, germanDubAvailable = false, monetizationType = null,
        firstAvailableAt = firstAvailableAt, lastUnavailableAt = null, lastCheckedAt = 1_000,
        nextCheckAt = null, checkAttempt = 0, providerUrl = null, evidenceType = "TEST",
        evidenceUrl = null, errorCode = null, source = "DIRECT_PROVIDER_CHECK"
    )
}
