# AniSentinel v0.24.7 – Validierung Provider-/Fallback-Lebenszyklus

## Ergebnis

Direkte Crunchyroll- und ADN-Evidenz besitzt nun Vorrang vor AniWorld. Ein bestätigtes `AVAILABLE` wird sofort verarbeitet, die Benachrichtigung dauerhaft dedupliziert und sämtliche weitere Planung einschließlich `RELEASE_FALLBACK` beendet.

## Korrekturen

- zentrale terminale Abbruchfunktion für WorkManager-Prüfung, Due-, Availability- und Fallback-Alarm
- explizite Kennzeichnung eines ausgelösten Fallback-Workers
- Room-Race-Guard vor jedem bereits ausgelösten T+10-Fallback
- AniWorld-Fallback ausschließlich nach technischem `CHECK_FAILED` und frühestens T+10
- kein Fallback nach belastbarem `NOT_AVAILABLE_YET`
- strukturierte Crunchyroll-/ADN-Evidenz löst dieselbe Sofortverarbeitung aus wie der direkte Providerchecker
- direkte Evidenz gewinnt vor später gespeicherter AniWorld-Evidenz
- getrennte Zuordnung und Beendigung für `GER_SUB` und `GER_DUB`
- zeitabhängiger Navigationstest verwendet die tatsächlich erwartete Tagesbegrüßung
- sichtbare Versionsangabe gekürzt, damit die Info-Seite responsiv bleibt

## Automatisierte Validierung

Stand 12. August 2026:

```text
214 JVM-Tests: erfolgreich
9 Room-Migrationstests auf Samsung SM-S928B: erfolgreich
4 Crunchyroll-Livediagnosetests: erfolgreich
1 ADN-Livediagnosetest: erfolgreich
1 AniSearch-Konnektivitätstest: erfolgreich
10 Compose-Golden-/Barrierefreiheitstests: erfolgreich
11 Compose-Navigationstests: erfolgreich
assembleDebug: erfolgreich
assembleDebugAndroidTest: erfolgreich
```

Der Crunchyroll-Livetest umfasst einen zuvor nicht vorgegebenen Titel und validiert die generische Series-ID-Auflösung, Staffel-/Episodenabfrage sowie dauerhafte Speicherung historischer Termine in Room.

## Historische Schutzregeln

Historische Imports bleiben von Due-/Availability-/Verschiebungsbenachrichtigungen, Exact Alarms, AUTO-Watcher, WorkManager-Retries und T+10-Fallback ausgeschlossen.

## Offener externer Schritt

Für die GitHub-Veröffentlichung existiert im angemeldeten Nutzerkonto noch kein AniSentinel-Repository. Vor dem Anlegen ist die Entscheidung öffentlich oder privat erforderlich.
