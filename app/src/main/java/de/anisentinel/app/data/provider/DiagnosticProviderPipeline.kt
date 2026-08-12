package de.anisentinel.app.data.provider

import de.anisentinel.app.data.local.AnimeExternalIdEntity
import de.anisentinel.app.data.local.AniSentinelDao
import de.anisentinel.app.data.local.EpisodeProviderAvailabilityEntity
import de.anisentinel.app.data.local.JustWatchOfferEntity
import de.anisentinel.app.data.local.JustWatchTitleMatchEntity
import de.anisentinel.app.data.local.ProviderReferenceEntity
import de.anisentinel.app.data.local.ProviderMetadataIdentityEntity
import de.anisentinel.app.data.local.JustWatchCatalogTitleEntity
import de.anisentinel.app.domain.provider.*
import de.anisentinel.app.data.release.AniWorldEpisodeFallbackChecker
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class ProviderPipelineRun(val matched: Int, val checked: Int, val failed: Int)

class ProviderPipelineRepository(
    private val dao: AniSentinelDao,
    private val source: JustWatchPartnerSource,
    private val checker: ProviderEpisodeChecker,
    private val aniWorldFallback: AniWorldEpisodeFallbackChecker,
    private val metadataAdapters: List<ProviderMetadataAdapter> = emptyList(),
) {
    private val syncMutex = Mutex()
    private var lastCompletedAt: Instant? = null
    val liveJustWatchStatus: String = "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC"

    suspend fun sync(now: Instant = Instant.now()): ProviderPipelineRun = syncTitleProviders(now)

    suspend fun syncTitleProviders(now: Instant = Instant.now()): ProviderPipelineRun = syncMutex.withLock {
        if (lastCompletedAt?.isAfter(now.minusSeconds(10 * 60)) == true) return@withLock ProviderPipelineRun(0, 0, 0)
        dao.deletePhysicalProviderReferences()
        val due = dao.dueAniWorldReleases(now.epochSecond, now.minusSeconds(7 * 86_400).epochSecond, 100)
        val upcoming = dao.nextAniWorldReleases(now.epochSecond, 150)
        val releases = providerSyncCandidates(due, upcoming, 150)
        if (releases.isEmpty()) return@withLock ProviderPipelineRun(0, 0, 0)
        var matched = 0; var checked = 0; var failed = 0
        for (release in releases) {
            val anime = dao.anime(release.animeId) ?: continue
            dao.providerReferences(release.animeId)
                .filterNot { StreamingProviderPolicy.isStreaming(it.provider) }
                .forEach { dao.deleteProviderReference(it.animeId, it.provider) }
            val storedMatch = dao.justWatchMatches(release.animeId)
                .firstOrNull { it.status == "MATCHED" && it.justWatchId != null }
            val context = providerMatchContext(anime.seasonYear, release.expectedAt, storedMatch?.releaseYear)
            // Provider discovery is title-level only. Do not query JustWatch seasons/episodes here;
            // concrete episode availability is exclusively checked by the official provider path.
            val lookup = source.lookup(anime.titleGerman, context.seriesStartYear, "SHOW", null, 0)
            if (lookup !is JustWatchSourceResult.Success) { failed++; continue }
            val decision = storedMatch?.let { stored ->
                lookup.matches.firstOrNull { it.justWatchId == stored.justWatchId }
                    ?.let(TitleMatchDecision::Unique)
            } ?: sourceValidatedUnique(lookup)
                ?: JustWatchTitleMatcher.decide(anime.titleGerman, context.seriesStartYear, "SHOW", lookup.matches)
            when (decision) {
                is TitleMatchDecision.Unique -> {
                    val matchId = "jw-match:${release.animeId}"
                    val candidate = decision.match
                    dao.upsertJustWatchMatches(listOf(JustWatchTitleMatchEntity(matchId, release.animeId,
                        candidate.justWatchId, candidate.tmdbId?.toString(), candidate.title, candidate.releaseYear,
                        candidate.contentType, candidate.matchConfidence.name, "MATCHED",
                        "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", now.epochSecond, null)))
                    dao.upsertExternalIds(listOf(AnimeExternalIdEntity("JUSTWATCH", candidate.justWatchId, release.animeId,
                        lookup.offers.firstOrNull()?.offerUrl)))
                    lookup.catalogTitles.firstOrNull { it.justWatchId == candidate.justWatchId }?.let { catalog ->
                        dao.upsertJustWatchCatalogTitles(listOf(catalog.toEntity(release.animeId)))
                    }
                    val offers = lookup.offers.filter {
                        it.justWatchId == candidate.justWatchId && StreamingProviderPolicy.isStreaming(it.providerName)
                    }
                    dao.upsertJustWatchOffers(offers.map { offer -> JustWatchOfferEntity(
                        "jw-offer:${release.animeId}:${offer.providerId}:${offer.seasonNumber}:${offer.episodeNumber}:${offer.monetizationType}", matchId,
                        offer.providerId, offer.providerName, offer.seasonNumber, offer.episodeNumber,
                        offer.monetizationType.name, offer.presentationType, offer.audioLanguages.sorted().joinToString(","),
                        offer.subtitleLanguages.sorted().joinToString(","), offer.offerUrl, offer.fetchedAt.epochSecond,
                        "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC") })
                    offers.distinctBy { it.providerId }.forEach { offer ->
                        dao.upsertProviderReference(ProviderReferenceEntity(
                            release.animeId, offer.providerName, offer.offerUrl,
                            "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", offer.offerUrl, now.epochSecond,
                            ProviderMarketPolicy.GERMANY
                        ))
                    }
                    matched++
                }
                is TitleMatchDecision.Ambiguous -> {
                    dao.upsertJustWatchMatches(listOf(JustWatchTitleMatchEntity("jw-ambiguous:${release.animeId}", release.animeId,
                        null, null, anime.titleGerman, anime.seasonYear, "SHOW", "LOW", "AMBIGUOUS",
                        "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", now.epochSecond,
                        providerMatchDiagnostic(anime.id, anime.titleGerman, context, lookup.matches, "AMBIGUOUS"))))
                }
                TitleMatchDecision.NoMatch -> dao.upsertJustWatchMatches(listOf(
                    JustWatchTitleMatchEntity("jw-nomatch:${release.animeId}", release.animeId,
                        null, null, anime.titleGerman, context.seriesStartYear, "SHOW", "LOW", "NO_MATCH",
                        "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", now.epochSecond,
                        providerMatchDiagnostic(anime.id, anime.titleGerman, context, lookup.matches, "NO_MATCH"))
                ))
            }
        }
        lastCompletedAt = now
        ProviderPipelineRun(matched, checked, failed)
    }

    suspend fun checkEpisode(releaseId: String, now: Instant = Instant.now()): ProviderPipelineRun =
        syncMutex.withLock {
            val release = dao.release(releaseId) ?: return@withLock ProviderPipelineRun(0, 0, 1)
            checkSingleRelease(release, now)
        }

    /** Manual historical diagnostic: no release status, expectedAt, scheduler or notification mutation. */
    suspend fun diagnoseHistoricalEpisode(releaseId: String, now: Instant = Instant.now()): ProviderPipelineRun =
        syncMutex.withLock {
            val release = dao.release(releaseId) ?: return@withLock ProviderPipelineRun(0, 0, 1)
            val episode = release.episodeNumber ?: return@withLock ProviderPipelineRun(0, 0, 1)
            val anime = dao.anime(release.animeId) ?: return@withLock ProviderPipelineRun(0, 0, 1)
            val references = dao.providerReferences(release.animeId)
            runMetadataProbes(release, anime.titleGerman, episode, references, now)
            val rows = dao.episodeProviderAvailability(releaseId).filter { row ->
                metadataAdapters.any { it.adapterId == row.source }
            }
            ProviderPipelineRun(0, rows.size, rows.count { it.status == EpisodeAvailabilityStatus.CHECK_FAILED.name })
        }

    suspend fun checkDueEpisodes(now: Instant = Instant.now()): ProviderPipelineRun = syncMutex.withLock {
        val releases = dao.dueFavoriteReleases(now.epochSecond, now.minusSeconds(7 * 86_400).epochSecond)
            .groupBy(::releaseKey)
            .values
            .map { duplicates -> duplicates.minBy { if (it.animeId.startsWith("aniworld:episode-")) 1 else 0 } }
        releases.fold(ProviderPipelineRun(0, 0, 0)) { total, release ->
            val run = checkSingleRelease(release, now)
            ProviderPipelineRun(total.matched + run.matched, total.checked + run.checked, total.failed + run.failed)
        }
    }

    private suspend fun checkSingleRelease(
        release: de.anisentinel.app.data.local.EpisodeReleaseEntity,
        now: Instant
    ): ProviderPipelineRun {
        val episode = release.episodeNumber ?: return ProviderPipelineRun(0, 0, 1)
        val anime = dao.anime(release.animeId) ?: return ProviderPipelineRun(0, 0, 1)
        val storedMatch = dao.justWatchMatches(release.animeId)
            .firstOrNull { it.status == "MATCHED" && it.justWatchId != null }
        val context = providerMatchContext(anime.seasonYear, release.expectedAt, storedMatch?.releaseYear)
        dao.updateReleaseStatus(release.sourceReleaseId, "CHECKING")

        val lookup = source.lookup(anime.titleGerman, context.seriesStartYear, "SHOW", release.seasonNumber, episode)
        val success = lookup as? JustWatchSourceResult.Success
        if (success == null) {
            persistEpisodeState(
                release,
                "PROVIDER_CHECK_FAILED",
                now,
                errorCode = (lookup as? JustWatchSourceResult.Failed)?.code ?: "JUSTWATCH_UNAVAILABLE"
            )
        }

        val selectedId = storedMatch?.justWatchId ?: success?.matches?.let { matches ->
            (JustWatchTitleMatcher.decide(anime.titleGerman, context.seriesStartYear, "SHOW", matches) as? TitleMatchDecision.Unique)
                ?.match?.justWatchId
        }
        // JustWatch is deliberately provider discovery only. Its episode offers are neither positive
        // nor negative evidence for a concrete provider episode. The official provider checker below
        // is the sole primary source for season, episode and language availability.
        success?.offers.orEmpty()
            .filter { it.justWatchId == selectedId }
            .distinctBy { it.providerId }
            .forEach { offer ->
                dao.upsertProviderReference(ProviderReferenceEntity(
                    release.animeId, offer.providerName, offer.offerUrl,
                    "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", offer.offerUrl, now.epochSecond,
                    ProviderMarketPolicy.GERMANY
                ))
            }

        val providerReferences = dao.providerReferences(release.animeId)
        runMetadataProbes(release, anime.titleGerman, episode, providerReferences, now)
        val directCandidates = providerReferences.filter {
            it.provider.contains("Crunchyroll", ignoreCase = true) &&
                !it.provider.contains("Amazon", ignoreCase = true) &&
                !it.provider.contains("Channel", ignoreCase = true)
        }
        var directNegative = false
        var directChecked = false
        directCandidates.forEach { reference ->
            val result = checker.checkEpisode(
                providerId = reference.provider,
                title = anime.titleGerman,
                seasonNumber = release.seasonNumber,
                episodeNumber = episode,
                expectedLanguage = release.releaseLanguage,
                providerUrl = reference.seriesUrl,
                expectedAt = release.expectedAt?.let(Instant::ofEpochSecond)
            )
            persistCheck(
                release.sourceReleaseId,
                release.seasonNumber,
                episode,
                release.releaseLanguage,
                reference.provider,
                reference.seriesUrl,
                result,
                now,
                release.expectedAt
            )
            when (result) {
                is ProviderEpisodeCheckResult.Checked -> {
                    directChecked = true
                    if (!result.availability.episodeFound) directNegative = true
                }
                is ProviderEpisodeCheckResult.Failed -> Unit
            }
        }
        val directFailed = !directChecked && dao.episodeProviderAvailability(release.sourceReleaseId)
            .filterNot { it.source == "ANIWORLD_CALENDAR_FALLBACK_V15" || it.source == "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC" }
            .none { it.status == EpisodeAvailabilityStatus.NOT_AVAILABLE_YET.name || it.status.startsWith("AVAILABLE_") }

        if (isExpectedLanguageAvailable(
                release.sourceReleaseId, release.releaseLanguage,
                excludedSources = setOf("ANIWORLD_CALENDAR_FALLBACK_V15")
            )) {
            dao.updateReleaseStatus(release.sourceReleaseId, "AVAILABLE")
            return ProviderPipelineRun(if (selectedId != null) 1 else 0, 1, 0)
        }

        val directRows = dao.episodeProviderAvailability(release.sourceReleaseId)
            .filterNot { it.source == "ANIWORLD_CALENDAR_FALLBACK_V15" || it.source == "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC" }
        val directConclusiveNegative = directRows.any { it.status == EpisodeAvailabilityStatus.NOT_AVAILABLE_YET.name }
        if (directConclusiveNegative) {
            dao.updateReleaseStatus(release.sourceReleaseId, "PENDING_CONFIRMATION")
            return ProviderPipelineRun(if (selectedId != null) 1 else 0, 1, 0)
        }

        val expectedAt = release.expectedAt ?: now.epochSecond
        val fallbackAt = expectedAt + 10 * 60
        if (now.epochSecond < fallbackAt) {
            persistEpisodeState(release, "ANIWORLD_FALLBACK_WAITING", now)
            dao.updateReleaseStatus(release.sourceReleaseId, "PENDING_CONFIRMATION")
            return ProviderPipelineRun(if (selectedId != null) 1 else 0, 0, if (directFailed) 1 else 0)
        }

        // AniWorld is a technical fallback only. A successfully parsed negative provider response
        // remains NOT_AVAILABLE_YET and continues through the direct provider schedule.
        if (!shouldUseAniWorldFallback(
                directAvailable = false,
                directConclusiveNegative = directConclusiveNegative || directChecked,
                directFailed = directFailed,
                atOrAfterFallbackTime = true
            )) {
            dao.updateReleaseStatus(release.sourceReleaseId, "PENDING_CONFIRMATION")
            return ProviderPipelineRun(if (selectedId != null) 1 else 0, 1, 0)
        }

        val sourceReference = dao.releaseSourceReferences(release.sourceReleaseId)
            .firstOrNull { it.sourceKind == "ANIWORLD_CALENDAR" }
        val fallback = aniWorldFallback.check(
            sourceReference?.externalId,
            release.seasonNumber,
            episode,
            release.releaseLanguage
        )
        val displayProvider = de.anisentinel.app.domain.provider.StreamingProviderPolicy
            .confirmedDisplayProvider(directCandidates.map { it.provider })
            ?: de.anisentinel.app.domain.provider.StreamingProviderPolicy
                .confirmedDisplayProvider(providerReferences.map { it.provider })
            ?: ""
        val displayProviderUrl = providerReferences
            .firstOrNull { it.provider.equals(displayProvider, ignoreCase = true) }
            ?.seriesUrl
        persistCheck(
            release.sourceReleaseId,
            release.seasonNumber,
            episode,
            release.releaseLanguage,
            displayProvider,
            displayProviderUrl,
            fallback,
            now,
            release.expectedAt,
            providerIdOverride = "ANIWORLD_FALLBACK",
            persistenceSource = "ANIWORLD_CALENDAR_FALLBACK_V15"
        )
        if (isExpectedLanguageAvailable(release.sourceReleaseId, release.releaseLanguage)) {
            dao.updateReleaseStatus(release.sourceReleaseId, "AVAILABLE")
            return ProviderPipelineRun(if (selectedId != null) 1 else 0, 1, 0)
        }
        val fallbackNegative = fallback is ProviderEpisodeCheckResult.Checked && !fallback.availability.episodeFound
        val finalStatus = if (directNegative && fallbackNegative) "DELAYED_CONFIRMED" else "OVERDUE_UNCONFIRMED"
        dao.updateReleaseStatus(release.sourceReleaseId, finalStatus)
        return ProviderPipelineRun(if (selectedId != null) 1 else 0, 1, if (finalStatus == "OVERDUE_UNCONFIRMED") 1 else 0)
    }

    /** Experimental probes run beside the established path and never promote a release by themselves. */
    private suspend fun runMetadataProbes(
        release: de.anisentinel.app.data.local.EpisodeReleaseEntity,
        title: String,
        episode: Int,
        references: List<ProviderReferenceEntity>,
        now: Instant
    ) = coroutineScope {
        metadataAdapters.flatMap { adapter ->
            references.filter { adapter.supports(it.provider) && ProviderMarketPolicy.isAppMarket(it.providerMarket) }
                .take(1)
                .map { reference -> async {
                    val cached = dao.providerMetadataIdentity(release.animeId, adapter.adapterId, ProviderMarketPolicy.GERMANY)
                        ?.let { ProviderMetadataIdentity(it.provider, it.providerMarket, it.seriesId, it.seasonId, it.episodeId, it.sourceUrl, it.seasonNumber) }
                    val request = ProviderMetadataProbeRequest(
                        release.animeId, title, release.seasonNumber, episode, release.releaseLanguage,
                        ProviderMarketPolicy.GERMANY, reference.seriesUrl, release.expectedAt?.let(Instant::ofEpochSecond)
                    )
                    Triple(adapter, reference, adapter.probe(request, cached))
                } }
        }.awaitAll().forEach { (adapter, reference, result) ->
            persistMetadataProbe(release, adapter, reference, result, now)
        }
    }

    private suspend fun persistMetadataProbe(
        release: de.anisentinel.app.data.local.EpisodeReleaseEntity,
        adapter: ProviderMetadataAdapter,
        reference: ProviderReferenceEntity,
        result: ProviderMetadataProbeResult,
        now: Instant
    ) {
        val identity = when (result) {
            is ProviderMetadataProbeResult.Available -> result.identity
            is ProviderMetadataProbeResult.NotAvailableYet -> result.identity
            is ProviderMetadataProbeResult.CheckFailed -> null
        }
        identity?.let {
            dao.upsertProviderMetadataIdentity(ProviderMetadataIdentityEntity(
                "provider-identity:${release.animeId}:${adapter.adapterId}:DE", release.animeId,
                adapter.adapterId, "DE", it.seriesId, release.seasonNumber, release.episodeNumber, it.seasonId, it.episodeId,
                it.sourceUrl, result.checkedAt.epochSecond
            ))
        }
        val availability = (result as? ProviderMetadataProbeResult.Available)?.availability
        val status = when (result) {
            is ProviderMetadataProbeResult.Available -> EpisodeStatusResolver.resolve(
                release.releaseLanguage, ProviderEpisodeCheckResult.Checked(result.availability)
            ).name
            is ProviderMetadataProbeResult.NotAvailableYet -> EpisodeAvailabilityStatus.NOT_AVAILABLE_YET.name
            is ProviderMetadataProbeResult.CheckFailed -> EpisodeAvailabilityStatus.CHECK_FAILED.name
        }
        val id = "availability:${release.sourceReleaseId}:${adapter.adapterId}"
        val old = dao.episodeAvailability(id)
        val available = status.startsWith("AVAILABLE_")
        val preserveConfirmed = shouldPreserveConfirmedAvailability(old?.status, old?.firstAvailableAt, status)
        val persistedStatus = if (preserveConfirmed) requireNotNull(old).status else status
        dao.upsertEpisodeProviderAvailability(listOf(EpisodeProviderAvailabilityEntity(
            id, release.sourceReleaseId, adapter.adapterId, reference.provider,
            release.seasonNumber, release.episodeNumber, persistedStatus,
            if (preserveConfirmed) old?.germanSubAvailable else availability?.germanSubAvailable,
            if (preserveConfirmed) old?.germanDubAvailable else availability?.germanDubAvailable, null,
            old?.firstAvailableAt ?: if (available) result.checkedAt.epochSecond else null,
            if (result is ProviderMetadataProbeResult.NotAvailableYet) result.checkedAt.epochSecond else old?.lastUnavailableAt,
            result.checkedAt.epochSecond,
            if (available || preserveConfirmed) null else release.expectedAt?.let {
                de.anisentinel.app.domain.watcher.AvailabilityWatchStrategy.nextCheckAt(it, now.epochSecond, "automatic")
            }, if (available) 0 else (old?.checkAttempt ?: 0) + 1,
            availability?.episodeUrl ?: reference.seriesUrl,
            availability?.evidenceType ?: "STRUCTURED_METADATA_PROBE",
            availability?.evidenceUrl ?: identity?.sourceUrl ?: reference.sourceUrl,
            when (result) {
                is ProviderMetadataProbeResult.CheckFailed -> result.code
                is ProviderMetadataProbeResult.NotAvailableYet -> result.diagnostic
                is ProviderMetadataProbeResult.Available -> null
            }, adapter.adapterId, availability?.availableSince?.epochSecond ?: old?.sourceAvailableAt
        )))
    }

    private suspend fun isExpectedLanguageAvailable(
        releaseId: String,
        language: String?,
        acceptedSources: Set<String>? = null,
        excludedSources: Set<String> = emptySet()
    ): Boolean =
        dao.episodeProviderAvailability(releaseId).any {
            (acceptedSources == null || it.source in acceptedSources) &&
            it.source !in excludedSources &&
            when (language) {
                "GER_SUB" -> it.germanSubAvailable == true
                "GER_DUB" -> it.germanDubAvailable == true
                else -> it.status.startsWith("AVAILABLE_")
            }
        }

    private fun releaseKey(release: de.anisentinel.app.data.local.EpisodeReleaseEntity): String =
        listOf(release.seasonNumber, release.episodeNumber, release.releaseLanguage, release.expectedAt)
            .joinToString("|")

    private suspend fun persistEpisodeState(release: de.anisentinel.app.data.local.EpisodeReleaseEntity, status: String, now: Instant, errorCode: String? = null) {
        val old = dao.episodeAvailability("availability:${release.sourceReleaseId}:unresolved")
        val attempt = (old?.checkAttempt ?: 0) + 1
        dao.upsertEpisodeProviderAvailability(listOf(EpisodeProviderAvailabilityEntity(
            "availability:${release.sourceReleaseId}:unresolved", release.sourceReleaseId, "UNRESOLVED", "",
            release.seasonNumber, release.episodeNumber, status, null, null, null, null,
            if (status == "PROVIDER_EPISODE_NOT_FOUND") now.epochSecond else old?.lastUnavailableAt,
            now.epochSecond, release.expectedAt?.let {
                de.anisentinel.app.domain.watcher.AvailabilityWatchStrategy.nextCheckAt(it, now.epochSecond, "automatic")
            }, attempt,
            null, "JUSTWATCH_EPISODE_QUERY", release.sourceUrl, errorCode,
            "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC"
        )))
        val overdueSeconds = now.epochSecond - (release.expectedAt ?: now.epochSecond)
        dao.updateReleaseStatus(
            release.sourceReleaseId,
            if (status == "CHECKING") "CHECKING"
            else if (overdueSeconds > 0) "OVERDUE_UNCONFIRMED"
            else "PENDING_CONFIRMATION"
        )
    }

    private suspend fun persistJustWatchOffer(releaseId: String, season: Int?, episode: Int, language: String?, offer: JustWatchOffer, now: Instant) {
        val id = "availability:$releaseId:${offer.providerId}"
        val old = dao.episodeAvailability(id)
        val germanAudio = offer.audioLanguages.any(::isGerman)
        val germanSubtitles = offer.subtitleLanguages.any(::isGerman)
        val status = when {
            germanAudio && germanSubtitles -> EpisodeAvailabilityStatus.AVAILABLE_GER_SUB_AND_DUB
            germanAudio -> EpisodeAvailabilityStatus.AVAILABLE_GER_DUB
            germanSubtitles -> EpisodeAvailabilityStatus.AVAILABLE_GER_SUB
            offer.audioLanguages.isEmpty() && offer.subtitleLanguages.isEmpty() -> EpisodeAvailabilityStatus.EPISODE_FOUND_LANGUAGE_UNKNOWN
            else -> EpisodeAvailabilityStatus.OFFERS_FOUND_NO_GERMAN_LANGUAGE
        }
        val available = status.name.startsWith("AVAILABLE_")
        val attempt = if (available) 0 else (old?.checkAttempt ?: 0) + 1
        dao.upsertEpisodeProviderAvailability(listOf(EpisodeProviderAvailabilityEntity(
            id, releaseId, offer.providerId, offer.providerName, season, episode, status.name,
            germanSubtitles, germanAudio, offer.monetizationType.name,
            old?.firstAvailableAt ?: if (available) now.epochSecond else null,
            if (available) old?.lastUnavailableAt else now.epochSecond, now.epochSecond,
            null, attempt,
            offer.offerUrl, "JUSTWATCH_EPISODE_OFFER", offer.offerUrl,
            null, "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC")))
    }

    private fun isGerman(code: String): Boolean = code.lowercase().let {
        it == "de" || it == "ger" || it.startsWith("de-") || it.startsWith("de_")
    }

    private fun de.anisentinel.app.domain.provider.JustWatchCatalogTitle.toEntity(internalAnimeId: String?) =
        JustWatchCatalogTitleEntity(
            justWatchId, internalAnimeId, title, releaseYear, contentType,
            genres.sorted().joinToString(","), coverUrl, justWatchUrl,
            providers.sorted().joinToString(","),
            providerUrls.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}\t${it.value}" },
            germanSubAvailable, germanDubAvailable, fetchedAt.epochSecond,
            "UNOFFICIAL_JUSTWATCH_DIAGNOSTIC", popularityRank
        )

    private suspend fun persistCheck(
        releaseId: String,
        season: Int?,
        episode: Int,
        language: String?,
        providerName: String,
        providerUrl: String?,
        result: ProviderEpisodeCheckResult,
        now: Instant,
        expectedAt: Long?,
        providerIdOverride: String? = null,
        persistenceSource: String = "DIRECT_PROVIDER_CHECK"
    ) {
        val checked = (result as? ProviderEpisodeCheckResult.Checked)?.availability
        val providerId = providerIdOverride ?: checked?.providerId ?: providerName.ifBlank { "UNRESOLVED" }
        val id = "availability:$releaseId:$providerId"
        val old = dao.episodeAvailability(id)
        val status = EpisodeStatusResolver.resolve(language, result)
        val available = status.name.startsWith("AVAILABLE_")
        val preserveConfirmed = shouldPreserveConfirmedAvailability(old?.status, old?.firstAvailableAt, status.name)
        val persistedStatus = if (preserveConfirmed) requireNotNull(old).status else status.name
        val attempt = if (available) 0 else (old?.checkAttempt ?: 0) + 1
        dao.upsertEpisodeProviderAvailability(listOf(EpisodeProviderAvailabilityEntity(
            id, releaseId, providerId, providerName, season, episode, persistedStatus,
            if (preserveConfirmed) old?.germanSubAvailable else checked?.germanSubAvailable,
            if (preserveConfirmed) old?.germanDubAvailable else checked?.germanDubAvailable, null,
            old?.firstAvailableAt ?: if (available) now.epochSecond else null,
            if (!available && result is ProviderEpisodeCheckResult.Checked) now.epochSecond else old?.lastUnavailableAt,
            now.epochSecond, if (available || preserveConfirmed) null else expectedAt?.let {
                de.anisentinel.app.domain.watcher.AvailabilityWatchStrategy.nextCheckAt(it, now.epochSecond, "automatic")
            }, attempt,
            checked?.episodeUrl ?: providerUrl, checked?.evidenceType ?: "CHECK_FAILED",
            checked?.evidenceUrl ?: providerUrl, (result as? ProviderEpisodeCheckResult.Failed)?.code,
            persistenceSource, checked?.availableSince?.epochSecond ?: old?.sourceAvailableAt)))
    }
}

internal fun shouldPreserveConfirmedAvailability(oldStatus: String?, firstAvailableAt: Long?, newStatus: String): Boolean =
    (oldStatus?.startsWith("AVAILABLE_") == true || firstAvailableAt != null) && !newStatus.startsWith("AVAILABLE_")

internal fun shouldUseAniWorldFallback(
    directAvailable: Boolean,
    directConclusiveNegative: Boolean,
    directFailed: Boolean,
    atOrAfterFallbackTime: Boolean
): Boolean = !directAvailable && !directConclusiveNegative && directFailed && atOrAfterFallbackTime

internal fun providerSyncCandidates(
    due: List<de.anisentinel.app.data.local.EpisodeReleaseEntity>,
    upcoming: List<de.anisentinel.app.data.local.EpisodeReleaseEntity>,
    limit: Int
): List<de.anisentinel.app.data.local.EpisodeReleaseEntity> =
    (due + upcoming).distinctBy { it.animeId }.take(limit)

internal fun sourceValidatedUnique(result: JustWatchSourceResult.Success): TitleMatchDecision.Unique? {
    val candidate = result.matches.singleOrNull() ?: return null
    if (result.offers.none { it.justWatchId == candidate.justWatchId }) return null
    return TitleMatchDecision.Unique(candidate)
}

internal data class ProviderMatchContext(
    val seriesStartYear: Int?,
    val seasonYear: Int?,
    val releaseExpectedYear: Int?,
    val hardYearOrigin: String
)

internal fun providerMatchContext(seasonYear: Int?, expectedAt: Long?, storedSeriesStartYear: Int?): ProviderMatchContext =
    ProviderMatchContext(
        seriesStartYear = storedSeriesStartYear,
        seasonYear = seasonYear,
        releaseExpectedYear = expectedAt?.let(Instant::ofEpochSecond)?.atZone(ZoneOffset.UTC)?.year,
        hardYearOrigin = if (storedSeriesStartYear != null) "STORED_SERIES_START_YEAR" else "NONE"
    )

internal fun providerMatchDiagnostic(
    animeId: String,
    title: String,
    context: ProviderMatchContext,
    candidates: List<JustWatchTitleMatch>,
    result: String
): String = buildString {
    append("animeId=$animeId; title=$title; matchYear=${context.seriesStartYear}; origin=${context.hardYearOrigin}; ")
    append("seasonYear=${context.seasonYear}; releaseExpectedYear=${context.releaseExpectedYear}; result=$result; candidates=")
    append(JustWatchTitleMatcher.rejectionReasons(title, context.seriesStartYear, "SHOW", candidates))
}
