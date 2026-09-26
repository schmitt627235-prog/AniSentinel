package de.anisentinel.app.data.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.anisentinel.app.data.local.AniSentinelDatabase
import de.anisentinel.app.data.local.AnimeEntity
import de.anisentinel.app.domain.provider.JustWatchCatalogResult
import de.anisentinel.app.domain.provider.JustWatchCatalogSource
import de.anisentinel.app.domain.provider.JustWatchCatalogTitle
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppleTvCanonicalIdentityTest {
    private lateinit var database: AniSentinelDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AniSentinelDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun englishJustWatchAliasKeepsOneCanonicalConanDetailAndAppleReference() = runBlocking {
        val dao = database.aniSentinelDao()
        dao.upsertAnime(listOf(AnimeEntity(
            "canonical-conan", null, null, "Detektiv Conan", "Detective Conan", null, null,
            "", null, null, null, 1996, null, 1
        )))
        val appleUrl = "https://tv.apple.com/de/show/detektiv-conan/umc.cmc.o4e5fbtkmgjivlpghedf8a6x"
        val title = JustWatchCatalogTitle(
            "justwatch-conan", "Detective Conan", 1996, "SHOW", emptySet(), null,
            "https://www.justwatch.com/de/Serie/Detektiv-Conan", setOf("Apple TV"),
            mapOf("Apple TV" to appleUrl), null, null, Instant.parse("2026-09-26T12:00:00Z")
        )
        val source = object : JustWatchCatalogSource {
            override suspend fun genres() = JustWatchCatalogResult.Success()
            override suspend fun search(
                query: String?, genreIds: Set<String>, contentTypes: Set<String>,
                offset: Int, first: Int, sort: String
            ) = JustWatchCatalogResult.Success(titles = listOf(title))
        }
        assertTrue(JustWatchCatalogRepository(dao, source).search("Detective Conan") is JustWatchCatalogResult.Success)
        assertEquals(listOf("canonical-conan"), dao.allAnime().map { it.id })
        assertEquals(appleUrl, dao.providerReferences("canonical-conan").single().seriesUrl)
    }
}
