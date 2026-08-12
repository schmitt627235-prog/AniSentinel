# AniSentinel DIAGNOSETEST V15 – Validierungsbericht

Stand: 2026-08-09  
Version: `0.15.0-v15-diagnostic` (`versionCode 27`)

## Ergebnis

V15 setzt die fachliche Providerreihenfolge verbindlich um:

1. JustWatch dient nur der Titelidentifikation und Anbieterzuordnung.
2. Der offizielle Anbieter wird auf Staffel, Episode und GER SUB/GER DUB geprüft.
3. Nur wenn dieser Check blockiert oder technisch nicht auswertbar ist, wird frühestens ab
   `expectedAt + 10 Minuten` der exakte AniWorld-Kalendereintrag als Fallback geprüft.

Ein fehlendes JustWatch-Episodenangebot erzeugt im neuen Code weder „nicht verfügbar“ noch eine
Verspätung. Historische `JUSTWATCH_EPISODE_NOT_LISTED`-Zeilen aus V14 bleiben aus Gründen der
Nachvollziehbarkeit im bestehenden Testgerät erhalten, werden aber nicht mehr erzeugt oder ausgewertet.

## Reale Prüfung „You and I Are Polar Opposites“, S2 E6 GER SUB

- JustWatch ordnete `Crunchyroll` und `Crunchyroll Amazon Channel` der Serie zu.
- Der direkte öffentliche Crunchyroll-Abruf erreichte eine Cloudflare-Challenge. Schutzmaßnahmen
  wurden nicht umgangen; das Ergebnis blieb ehrlich `CHECK_FAILED / PARSER_CHANGED`.
- Der öffentliche AniWorld-Kalender enthielt den exakten Serien-Slug, Staffel 2, Episode 6,
  GER-SUB-Sprachflagge und den Marker „Stream online!“.
- Der Fallback speicherte `AVAILABLE_GER_SUB`, Quelle `ANIWORLD_CALENDAR_FALLBACK_V15`.
- `firstAvailableAt=1786269551` blieb bei späteren Prüfungen unverändert erhalten.
- Direkter Prüfnachweis und Fallbacknachweis liegen in getrennten Room-Zeilen; der Fallback
  überschreibt den Crunchyroll-Fehlerzustand nicht.

## Doppelmeldungsfehler

Die V14-Gerätedaten enthielten zwei fachlich identische Releases:

- `aniworld:you-and-i-are-polar-opposites:...`
- `aniworld:episode-6:...`

Ursache war ein zu breiter Anchor-Selektor, der in einer Kalenderkarte den Episodenlink statt des
Serienlinks auswählen konnte. V15 beschränkt den Parser auf `/anime/stream/<serien-slug>`, verwendet
für Zustellungen den semantischen Schlüssel aus Anime, Staffel, Episode, Sprache und Ereignistyp und
beansprucht ihn vor dem Versand atomar in Room. Eine migrationsfreie Selbstreparatur überträgt einen
eventuell am falschen Anime hängenden Favoriten und entfernt den fehlerhaften Release. Alte
WorkManager-Aufträge für `aniworld:episode-*` beenden sich ohne erneute Prüfung oder Planung.

## UI

- Detailansicht und Episodenzeilen zeigen „Erstmals erkannt“, geplanten Termin und erkannte Verzögerung.
- Die Verfügbarkeitsbenachrichtigung enthält ebenfalls den ersten Erkennungszeitpunkt.
- Drawer-Routen „Anbieter“, „Aktuelle Season“, „Neue Dub-Releases“, „Statistik“ und „Changelog“
  verwenden reale Room-Daten.
- News und „Heiß erwartet“ bleiben deaktiviert, solange keine belastbare Quelle beziehungsweise
  eindeutige Popularitätssemantik vorhanden ist.

## Automatisierte Prüfung

Ausgeführt:

```text
gradlew.bat testDebugUnitTest assembleDebug
BUILD SUCCESSFUL
147 Tests, 0 Fehler, 0 übersprungen
```

Wichtige neue Regressionen:

- Favorit überlebt die Reparatur und wechselt auf die kanonische Anime-ID.
- Der falsche `episode-6`-Release wird entfernt.
- Serienidentität wird bei mehreren Links ausschließlich aus dem Serienlink gebildet.
- AniWorld bestätigt nur exakte Staffel, Episode, Sprache und Online-Marker.
- Notification-IDs sind semantisch und Ereignisse werden atomar dedupliziert.
- `firstAvailableAt` bleibt über wiederholte Upserts unverändert.

## Produktgrenzen

Der Build enthält keine Streaming-, Download- oder DRM-Funktion. Er umgeht weder Login,
CAPTCHA noch Bot-Schutz. Die verwendeten Daten sind öffentliche Metadaten; bei blockiertem Zugriff
wird der Fehler sichtbar gespeichert statt Verfügbarkeit zu erfinden. AniList, AnimeRadar und
AniSearch sind im aktiven Diagnosepfad deaktiviert.

## Gerätestresstest

Das finale Protokoll `v15-device-stress.log` und der gefilterte Geräte-Logcat liegen im Ausgabeordner.
Der Lauf bedient alle Haupttabs, scrollt lange Listen wiederholt bis ans Ende und öffnet sekundäre
Drawer-Ziele. Dauer, Iterationen und Crashmarker stehen in der letzten Protokollzeile.

Finales Ergebnis: `1814 Sekunden`, `54 Iterationen`, durchgehend App-PID `11265`,
`0 Crash-/ANR-Marker`. Nach der abschließenden Installation bestätigte die Room-Prüfung:
`0` fehlerhafte `aniworld:episode-*`-Releases, `3` kanonische Serienreleases und genau ein aktivierter
Favorit an der kanonischen Serien-ID. Die zwei historischen V14-`RELEASE_DUE`-Nachweise bleiben als
Auditspur erhalten; V15 erzeugte keine dritte Zustellung.
