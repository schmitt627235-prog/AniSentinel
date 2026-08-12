# AniSentinel v0.23.0 – Validierungsbericht

Datum: 2026-08-11  
Build: `0.23.0-v23-diagnostic` (`versionCode 35`)  
Room: Schema 16, unverändert

## Korrektur der Jahressemantik

`seasonYear` und das Jahr aus `expectedAt` werden nicht länger als hartes JustWatch-Serienjahr
verwendet. Die Pipeline führt getrennte Diagnosewerte:

```text
seriesStartYear
seasonYear
releaseExpectedYear
hardYearOrigin
```

Nur das Serienjahr eines bereits sicher gespeicherten `MATCHED`-Datensatzes wird als harte
Jahresschranke wiederverwendet. Ohne ein solches Signal entscheidet ein eindeutiger Titel-/Format-
Match. Staffel- und Releasejahr bleiben sichtbar, blockieren Fortsetzungsstaffeln aber nicht.

## Konservative Titelidentität

- Derselbe stabile JustWatch-Datensatz darf lokalisierte und fremdsprachige Titelvarianten tragen.
- Stimmen deutscher und englischer Suchlauf beim jeweiligen ersten SHOW-Treffer in derselben
  JustWatch-ID überein, darf diese ID fehlende Sprachtitel überbrücken, sofern kein Jahr widerspricht.
- Ein Klammerjahr wird nur entfernt, wenn das Kandidatenjahr exakt dazu passt.
- Ein nackter Franchise-Titel wird nicht automatisch einem Kandidaten mit Jahreszusatz zugeordnet.
- Es wurden keine Titel- oder Provider-Hardcodes ergänzt.

## Diagnose

`NO_MATCH` und `AMBIGUOUS` speichern beziehungsweise protokollieren:

```text
Anime-ID, angefragter Titel, matchYear, Jahrherkunft,
seasonYear, releaseExpectedYear,
Kandidatentitel, Kandidatenjahr, Content-Type, Score,
TITLE_SCORE, YEAR_MISMATCH, CONTENT_TYPE oder AMBIGUOUS
```

## Build und Tests

- `testDebugUnitTest assembleDebug --no-daemon`: erfolgreich.
- 35 Testsuiten, 180 Tests, 0 Fehler, 0 Fehlschläge.
- Neue Tests: Slime-Fortsetzungsjahr, Bumpkin-Fortsetzungsjahr, MAO-Klammerjahr,
  lokalisierte/englische Stable-ID, deutsche/Romaji-Stable-ID, gespeichertes Serienjahr,
  Source-validierter Stable-ID-Match und One-Piece-Negativfall.
- Dara-san, due+upcoming, StreamingProviderPolicy, V22-Newsdetails, Exact Alarm, T+10-Fallback
  und Deep-Link-Regressionspfade bleiben grün.

## Reale Geräte- und JustWatch-Prüfung

Gerät: Samsung SM-S928B. Die finale Diagnose-APK wurde datenbewahrend installiert. Der reale
JustWatch-Diagnoselauf lieferte:

```text
That Time I Got Reincarnated as a Slime
→ Unique, JustWatch-ID ts84230, Serienjahr 2018
→ 19 Rohangebote
→ in Room: Crunchyroll, Crunchyroll Amazon Channel, Netflix sowie weitere reale Angebote

From Old Country Bumpkin to Master Swordsman
→ Unique, JustWatch-ID ts444561, Serienjahr 2025
→ 4 Rohangebote
→ in Room: Amazon Prime Video, Amazon Prime Video with Ads

MAO (2026)
→ Unique, JustWatch-ID ts490432, Serienjahr 2026
→ 2 Rohangebote
→ in Room: Aniverse Amazon Channel
```

Damit sind alle drei Identitäts-/Matchingfehler behoben. Die vom Nutzer erwartete ADN-Zuordnung
für Bumpkin und MAO wurde vom aktuellen deutschen JustWatch-Response am Testzeitpunkt nicht
geliefert. AniSentinel erzeugt deshalb bewusst keine erfundene ADN-Providerreferenz. Dieser Befund
betrifft die aktuelle Katalogantwort, nicht die nun erfolgreiche Titelidentifikation.

Der Geräteclip zeigt unter anderem Slime mit `Anbieter: Crunchyroll`, MAO mit dem real gelieferten
Aniverse Amazon Channel sowie Bumpkin im Kalender mit den real gelieferten Amazon-Angeboten.
Logcat enthielt im Prüflauf 0 `FATAL EXCEPTION` und 0 App-ANRs.

## Unveränderte Grenzen

- JustWatch dient weiterhin nur Titelidentifikation und Providerzuordnung.
- Konkrete Episodenverfügbarkeit wird nicht aus JustWatch-Angeboten abgeleitet.
- Direkter Providercheck und AniWorld-Fallback ab T+10 bleiben getrennte Datenpfade.
- V22-Newsdetail mit `Loading`, `Found`, `NotFound` und Room-Offlinelesbarkeit blieb unverändert.
- Kein langer Stresstest und keine Fake-Daten.
