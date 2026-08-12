# AniSentinel v0.20.0 – Validierungsbericht

Datum: 2026-08-09  
Build: `0.20.0-v20-diagnostic` (`versionCode 32`)  
Room: Schema 16

## Automatische Prüfung

- `testDebugUnitTest assembleDebug`: erfolgreich.
- 167 Tests: 167 erfolgreich, 0 Fehler.
- Parserfixture bildet den offiziellen Anime2You-Artikel-RSS-Aufbau ab.
- Positiv geprüft: neuer Termin, Dub-Ankündigung, Staffelankündigung ohne Termin.
- Eine Staffelankündigung ohne Datum erzeugt ausschließlich eine News-Zeile.
- Quellendeduplizierung führt Anime2You und AniWorld in einer Meldung zusammen.
- V19-Regressionen für Dara-san, One Piece und gemeinsamen due+upcoming-Sync bleiben grün.

## Favoritenzustand

`Abgeschlossen` bedeutet jetzt:

```text
mindestens ein Release vor dem heutigen lokalen Kalendertag
UND kein Release heute
UND kein konkreter zukünftiger Release
```

`STOPPED` und `totalEpisodes` sind nicht mehr erforderlich. Ein konkreter neuer Termin verschiebt
den Titel automatisch nach `Demnächst`. Eine News ohne Datum kann dies nicht tun, weil
`announcements` und `episode_releases` getrennt gespeichert und verarbeitet werden.

## Realer Anime2You-Gerätetest

Auf Samsung SM-S928B wurde V20 über ADB installiert und die Room-Migration 15→16 ausgeführt.
Der offizielle Feed `https://www.anime2you.de/news/feed/` lieferte real 25 Artikel. In der App waren
unter anderem aktuelle Artikel mit Kategorie, Titel, Veröffentlichungszeit und `Quelle: Anime2You`
sichtbar. Die Datenbank bestätigte `user_version = 16` und 25 persistierte Meldungen.

Der Transport verwendet:

- ausschließlich den öffentlichen offiziellen Artikel-RSS-Feed,
- keinen Login und keine Schutzumgehung,
- 2-MB-Antwortlimit,
- aussagekräftigen User-Agent,
- drei begrenzte Versuche mit Backoff,
- 15-Minuten-Abrufbegrenzung und Room-Cache,
- ehrliche Fehlercodes; bestehende Meldungen werden nie wegen eines Abruffehlers gelöscht.

## Struktur und Quellenabgleich

Gespeichert werden unter anderem Typ, Staffel, alter/neuer Termin, Releasefenster, Grund, Anbieter,
Veröffentlichungszeit, Quellen, Quellen-URLs, Bild-URL und Abrufzeit. Nicht im Artikel oder in einer
Terminänderung vorhandene Werte bleiben `null`.

AniWorld-Terminänderungen werden als strukturierte zweite Quelle übernommen. Meldungen werden über
normalisierten Anime-Bezug, Typ, Staffel und eine Wochen-Nähe dedupliziert. Bei einer Übereinstimmung
bleibt eine Karte bestehen; beide Quellen und URLs werden gespeichert.

## Unveränderte Grenzen

Anime2You ist keine Episodenverfügbarkeitsquelle. JustWatch bleibt Titel-/Providerzuordnung. Eine
konkrete Episode wird weiterhin nur durch den direkten Anbietercheck oder frühestens ab T+10 durch
den GER-SUB-/GER-DUB-AniWorld-Fallback bestätigt. Der öffentliche Crunchyroll-Datenweg liefert noch
keinen stabilen direkten Episoden-/Sprachnachweis; Parserfehler werden nicht als Nichtverfügbarkeit
umgedeutet. Keine Streaming-, Download-, DRM- oder Login-Funktion wurde ergänzt.
