package de.anisentinel.app.data.anisearch

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.util.Log
import de.anisentinel.app.data.anticipated.AnticipatedLoadResult
import de.anisentinel.app.data.anticipated.AnticipatedTitlesRepository
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

    @Test fun dynamicFutureTitlesRunInConservativeStages() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = AnticipatedTitlesRepository(context)
        val loaded = repository.load(force = true)
        require(loaded is AnticipatedLoadResult.Success) { "AniList future list unavailable: $loaded" }
        val dynamic = loaded.titles.take(20)
        require(dynamic.size >= 5) { "Not enough dynamic future titles: ${dynamic.size}" }
        val stages = listOf(3, 5, dynamic.size)
        var completed = 0
        for (target in stages) {
            while (completed < target) {
                val title = dynamic[completed]
                val result = repository.enrichDach(title)
                Log.i(
                    "AniSearchBuild18Live",
                    "index=$completed aniListId=${title.identity.aniListId} title=${title.title} " +
                        "aliases=${title.identity.titles.joinToString("|")} status=${result.dachLicenseStatus} " +
                        "aniSearchId=${result.identity.aniSearchId} publisher=${result.dachProvider} " +
                        "period=${result.dachAvailableFrom ?: result.dachAvailablePeriod} " +
                        "source=${result.dachSource} reason=${result.dachCheckMessage}"
                )
                completed++
                val reason = result.dachCheckMessage.orEmpty()
                if (reason.contains("Rate", ignoreCase = true) || reason.contains("429") ||
                    reason.contains("Anfragelimit", ignoreCase = true) ||
                    reason.contains("gesperrt", ignoreCase = true)
                ) return@runBlocking
            }
            Log.i("AniSearchBuild18Live", "stage-complete count=$completed")
        }
    }
}
