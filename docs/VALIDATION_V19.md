# AniSentinel v0.19.0 – Validierungsbericht

Datum: 2026-08-09  
Build: `0.19.0-v19-diagnostic` (`versionCode 31`)

## Ergebnis

- `testDebugUnitTest assembleDebug`: erfolgreich.
- 162 Unit-/Robolectric-/Room-Tests: 162 erfolgreich, 0 Fehler.
- APK auf Samsung SM-S928B über ADB installiert und gestartet.
- Keine `FATAL EXCEPTION` und kein App-ANR im geprüften Lauf.
- Die reale AniWorld-Seasonbasis bleibt bei 78 Titeln.

## Verbindlicher Dara-san-Referenzfall

Realer Gerätefluss:

```text
AniWorld: Dara-san of Reiwa
Releasejahr aus expectedAt: 2026
Format: SHOW
JustWatch: Dara-san of the Reiwa Era (2026, SHOW)
Entscheidung: Unique
```

Der Treffer wurde in Room als `ts517665` gespeichert. Der Katalogdatensatz enthält:

- Crunchyroll
- Crunchyroll Amazon Channel
- JustWatch-URL und konkrete Anbieter-URLs
- GER SUB vorhanden; GER DUB nicht behauptet

Die Providerreferenzen werden auf Startseite, Kalender und aktueller Season nur als
Titel-/Staffelzuordnung genutzt. JustWatch bleibt ausdrücklich **keine** Episodenverfügbarkeitsprüfung.

## Schutz vor Fehlzuordnungen

- Positivtest: `Dara-san of Reiwa` ↔ `Dara-san of the Reiwa Era`, Jahr 2026, Serie.
- Negativtest: `One Piece` ↔ `One Piece (2023)` bleibt ohne Match.
- Jahr wird aus dem Anime übernommen oder, wenn dort nicht vorhanden, aus `expectedAt` abgeleitet.
- Format muss kompatibel sein.
- Unsichere Kandidaten bleiben `NoMatch` oder `Ambiguous`; kein erzwungener Ersatztreffer.

## Providerpipeline

Der regelmäßige Abgleich verarbeitet fällige und kommende reale AniWorld-Releases dedupliziert.
Die JustWatch-Suche läuft titelbasiert und begrenzt; unnötige Staffel-/Episodenabfragen wurden aus
diesem Schritt entfernt. Konkrete Episode und Sprachfassung werden weiterhin nur über den direkten
offiziellen Providercheck bestätigt. Ist dieser technisch nicht möglich, greift AniWorld frühestens
ab `expectedAt + 10 Minuten` als GER-SUB-/GER-DUB-Fallback. Vorhandene Room-Daten werden bei
Netzwerk- oder Parserfehlern nicht gelöscht.

## Bekannte technische Grenze

Die öffentlich erreichbare Crunchyroll-Seite liefert im Diagnosekontext weiterhin nicht stabil die
für einen belastbaren direkten Episoden-/Sprachnachweis benötigten strukturierten Metadaten. Ein
Parserfehler wird deshalb ehrlich gespeichert; er wird weder als „nicht verfügbar“ noch als
JustWatch-Episodenurteil ausgegeben. Streaming, Downloads, DRM- oder Login-Funktionen sind nicht
enthalten.
