# AGENTS.md – Arbeitsregeln für Codex

## Auftrag
Baue AniSentinel schrittweise als wartbare native Android-App. Priorität haben korrekte Architektur, ein funktionierender lokaler Prototyp und die klare Trennung zwischen UI, Domainlogik, Datenquellen und Android-Hintergrundausführung.

## Technologievorschlag
- Kotlin
- Jetpack Compose + Material 3
- Gradle Kotlin DSL
- Minimum SDK zunächst 26 oder höher
- Room
- DataStore Preferences
- Retrofit/OkHttp oder Ktor Client
- Kotlinx Serialization
- WorkManager
- Foreground Service nur für aktiv bestätigte Live-Überwachung
- Hilt oder Koin; Hilt bevorzugt
- Coroutines + Flow
- Coil für Coverbilder

## Nicht tun
- Keine Streaming- oder Downloadfunktion implementieren.
- Keine DRM-, Playback-Token- oder Manifest-Endpunkte untersuchen.
- Keine Zugangsdaten von Streaminganbietern speichern.
- Keine dauerhaften Hintergrundschleifen.
- Keine Anfrage im 30-Sekunden-Takt ohne sichtbare Live-Überwachung.
- Keine undokumentierte Datenquelle als garantiert stabil behandeln.
- Keine AniSearch-Nutzung ohne Rate-Limit, Cache, User-Agent und Abschaltmöglichkeit.
- Keine Anime-Bilder aus den Mockups als Produktionsassets übernehmen; sie sind nur Layoutreferenzen.

## Architekturregeln
- Provider- und Newsquellen hinter Interfaces kapseln.
- UI kennt keine HTML-Selektoren, URLs oder Netzwerkdetails.
- Rohantworten und Parserlogik voneinander trennen.
- Jede Quelle liefert standardisierte Ergebnisse samt `source`, `checkedAt`, `confidence` und Fehlerstatus.
- Zeitangaben intern als `Instant`; Anzeige in lokaler Zeitzone.
- Verfügbarkeit nie aus „Episode existiert“ allein ableiten.
- `lastUnavailableAt` und `firstAvailableAt` speichern, damit Zeitfenster ehrlich angezeigt werden.

## Arbeitsweise
1. Zuerst Mock-Daten und UI.
2. Dann Domainmodell und Room.
3. Dann Scheduler/Watcher mit Fake-Checker.
4. AniList als erste echte Quelle.
5. Anbieter-Checker einzeln, mit Tests und Feature-Flags.
6. News-/Verschiebungsprüfung.
7. Optimierung, Barrierefreiheit, Lokalisierung.

## Definition of Done
- Build erfolgreich.
- Keine Secrets im Repository.
- Unit-Tests für Release-Intervalllogik und Statusübergänge.
- Parser mit gespeicherten Fixtures testbar.
- Netzwerkfehler führen nicht zu falschen „nicht verfügbar“-Meldungen.
- Hintergrundarbeit kann vollständig deaktiviert werden.
