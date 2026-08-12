# Technische Architektur

## Zielarchitektur
Clean Architecture mit klaren Grenzen:

```text
UI / Compose
  ↓
ViewModels
  ↓
Use Cases / Domain
  ↓
Repository Interfaces
  ↓
Local Room + Remote Data Sources + Provider Plugins
```

## Module
```text
:app
:core:model
:core:database
:core:network
:core:designsystem
:core:notifications
:core:scheduling
:feature:home
:feature:calendar
:feature:favorites
:feature:discover
:feature:details
:feature:settings
:feature:watcher
:data:anilist
:data:anisearch
:data:news
:provider:crunchyroll
:provider:adn
```
Für einen schnellen MVP kann zunächst ein einzelnes App-Modul mit entsprechender Paketstruktur verwendet und später modularisiert werden.

## Zentrale Interfaces
```kotlin
interface ProviderChecker {
    val provider: StreamingProvider
    suspend fun checkEpisode(request: EpisodeCheckRequest): AvailabilityResult
}

interface MetadataSource {
    suspend fun search(query: String): List<AnimeMetadata>
    suspend fun getAnime(externalId: ExternalId): AnimeMetadata?
}

interface ReleaseScheduleSource {
    suspend fun getExpectedReleases(range: ClosedRange<Instant>): List<ExpectedRelease>
}

interface DelayNewsSource {
    suspend fun findRelevantNews(request: DelayNewsRequest): List<DelayNewsItem>
}
```

## Standardisiertes Ergebnis
Jede Quelle liefert:
- Quellenschlüssel
- Zeitpunkt
- Erfolg/Fehler
- Rohstatus
- normalisierten Status
- Confidence 0.0–1.0
- URL, falls legal öffentlich öffnbar
- optionale Begründung

## Fehlerbehandlung
- Netzwerkfehler ≠ nicht verfügbar
- HTTP 403/429 löst Backoff und temporäre Deaktivierung aus
- Parserfehler werden protokolliert, aber nicht als Release-Verzögerung gewertet
- Ergebnisse besitzen Ablaufzeit

## Feature Flags
- jede Quelle separat aktivierbar
- experimentelle Checker standardmäßig aus
- X-Modul standardmäßig aus
- AniSearch-Parser schnell abschaltbar
