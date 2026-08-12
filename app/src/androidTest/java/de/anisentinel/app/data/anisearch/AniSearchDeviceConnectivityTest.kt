package de.anisentinel.app.data.anisearch

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a normal networked Android device to distinguish host sandbox TLS from AniSearch. */
@RunWith(AndroidJUnit4::class)
class AniSearchDeviceConnectivityTest {
    @Test fun publicSearchProducesAnExplicitTransportResult() = runBlocking {
        val result = AniSearchHttpTransport(ApplicationProvider.getApplicationContext())
            .searchAnime("Frieren")
        assertFalse(result is AniSearchFetchResult.InvalidUrl)
    }
}
