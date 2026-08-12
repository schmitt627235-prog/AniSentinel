# AniSentinel v0.24.8 – Favoriten und historische Releases

## Korrektur

Die Favoritenansicht liest historische und normale deutsche Releases über `observeFavoriteReleasesForClassification()`. Der getrennte Pfad `observeSchedulableFavoriteReleases()` liefert ausschließlich nicht-historische Releases. Die bestehenden Scheduler-Abfragen besitzen weiterhin dieselbe harte `isHistoricalImport = 0`-Grenze.

## Semantik

```text
Release heute                                      → Aktuell
kein Release heute + konkreter zukünftiger Termin → Demnächst
vergangener Release + kein heutiger/Zukunftstermin → Abgeschlossen
```

Ein historischer Crunchyroll-/ADN-Termin zählt als vergangener Release. Ein später importierter konkreter Zukunftstermin verschiebt den Favoriten automatisch nach „Demnächst“.

## Sicherheitsgrenze

Historische Releases bleiben ausgeschlossen von:

- Due-, Availability- und Verschiebungsbenachrichtigungen
- Exact Alarms
- AUTO-Watcher
- WorkManager-Providerprüfungen
- T+10-AniWorld-Fallback

Es ist keine Datenbankmigration erforderlich; Favoriten- und Releaseentitäten werden nicht umgeschrieben.

## Validierung am 12. August 2026

```text
218 JVM-Tests: erfolgreich
9 Room-Migrationstests auf Samsung SM-S928B: erfolgreich
4 Crunchyroll-Livediagnosetests: erfolgreich
1 ADN-Livediagnosetest: erfolgreich
1 AniSearch-Konnektivitätstest: erfolgreich
10 Compose-Golden-/Barrierefreiheitstests: erfolgreich
11 Compose-Navigationstests: erfolgreich
assembleDebug und assembleDebugAndroidTest: erfolgreich
```
