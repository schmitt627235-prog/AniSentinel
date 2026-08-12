package de.anisentinel.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseSourceTypeTest {
    @Test fun mapsPersistedSourcesWithoutInferringProviderAvailability() {
        assertEquals(
            ReleaseSourceType.ANILIST_AIRING,
            ReleaseSourceType.fromMetadataSource("ANILIST_AIRING_SCHEDULE")
        )
        assertEquals(
            ReleaseSourceType.ANISEARCH_GERMAN_RELEASE,
            ReleaseSourceType.fromMetadataSource("ANISEARCH_GERMAN_RELEASE")
        )
        assertEquals(ReleaseSourceType.UNKNOWN, ReleaseSourceType.fromMetadataSource("CRUNCHYROLL"))
    }
}
