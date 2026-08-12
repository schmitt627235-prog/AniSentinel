# AniSentinel v0.13.0 – Validierungsbericht DIAGNOSETEST V13

Stand: 09.08.2026

## Ergebnis

V13 trennt die Releaseansicht fachlich vom JustWatch-Katalog. Die Startseite liest nur Anime, die über reale `ANIWORLD_CALENDAR`-Zeilen mit `GER_SUB` oder `GER_DUB` verfügen. Such- und Discover-Titel verwenden eine eigene `CatalogAnimeCard` und zeigen weder `Folge 0` noch künstlichen Countdown oder Releasestatus.

## Umgesetzte Korrekturen

- globale JustWatch-Suche ohne erzwungenes Genre `ani`
- Suchnormalisierung über Unicode NFD; `pokemon`, `Pokemon`, `Pokémon`, `POKEMON` und `POKÉMON` sind äquivalent
- echte JustWatch-Antworten werden nicht erneut durch `String.contains()` verworfen
- Startliste und Releasezähler werden aus realen AniWorld-Releases gebildet
- eigenständiges Katalog-UI-Modell `CatalogAnimeItem` und `CatalogAnimeCard`
- physische DVD-/Blu-ray-/Buchhändler aus der primären Anbieterzeile entfernt
- `Anbieter A–Z` und `Anbieter Z–A` nutzen den stabilen ersten Streaminganbieter; Gleichstände werden gegensinnig aufgelöst
- `Aktuell` bleibt kalendertagsbasiert; `Demnächst` beginnt strikt nach dem heutigen Tag
- AniWorld-Range-Sync löscht keine bereits vergangenen normalen Releases mehr
- vergangene Monatsnavigation bleibt ohne künstliche Untergrenze möglich
- Discover führt statt einer seriellen Vollabfrage nur einen Genre- und einen begrenzten Katalogrequest aus

## Automatische Validierung

```text
./gradlew.bat testDebugUnitTest assembleDebug
BUILD SUCCESSFUL
139 Tests
0 Fehler
0 übersprungen
```

Neue Regressionen prüfen ausdrücklich:

- Pokémon-Suchnormalisierung
- Ausschluss physischer Anbieter
- gegenläufige Provider-Sortierung
- Ausschluss reiner JustWatch-Titel aus dem Releasekatalog
- Erhalt normaler vergangener AniWorld-Releases beim späteren Sync

## Reales Zielgerät

```text
Gerät: Samsung SM-S928B
Paket: de.anisentinel.app
Version: 0.13.0-v13-diagnostic (25)
Installation: adb install -r erfolgreich
```

Geprüfter Ablauf:

1. V13 installiert und mit vorhandenem V12-Datenbestand gestartet.
2. Startseite zeigte 78 echte Release-Titel.
3. Suche `pokemon` lieferte `Pokémon` und `Pokémon Meisterdetektiv Pikachu`.
4. Startseitenzähler blieb nach der Suche unverändert bei 78.
5. Pokémon wurde als `Serie · 1997` mit Streaminganbietern und ohne `Folge 0` dargestellt.
6. Discover wurde geöffnet, reale Daten wurden geladen und Filter horizontal bedient.
7. 12 Sekunden Discover-Lauf nach finaler Installation: keine `FATAL EXCEPTION`, kein Paket-`AndroidRuntime`-Absturz.
8. Kalenderhistorie 07.08.2026 zeigte einen real gespeicherten GER-SUB-Termin samt Verschiebung vom 31.07. auf den 07.08.
9. Anbieter A–Z und Z–A wurden auf dem Gerät ausgewählt; die Sortierlogik ist zusätzlich deterministisch getestet.

## Ehrlich verbleibender Punkt

Der V12-Bestand hatte normale Releases vom 08.08.2026 bereits vor V13 gelöscht. AniWorld stellte diese Daten beim V13-Test nicht mehr im aktuellen öffentlichen Fenster bereit. V13 erzeugt ausdrücklich keine Ersatzdaten und kann diesen bereits verlorenen Tag deshalb nicht rückwirkend rekonstruieren. Der DAO-Regressionstest und die neue Löschbedingung beweisen, dass einmal importierte vergangene Termine ab V13 bei späteren Synchronisierungen erhalten bleiben.

Nicht erneut zeitabhängig nachgewiesen wurden eine neue Live-Terminverschiebung während des kurzen Testfensters und eine gesperrte Releasebenachrichtigung zum exakten Fälligkeitszeitpunkt. Die bestehende Verschiebungshistorie blieb sichtbar.

## Nachweise

- `V13-Startseite-Releasezaehler.png`
- `V13-Suche-pokemon-Pokemon.png`
- `V13-Katalogtitel-ohne-Folge-0.png` (identisch mit Suchnachweis)
- `V13-Discover-responsive.png`
- `V13-Kalenderhistorie-07-08-2026.png`
- `V13-Favoriten-Anbieter-A-Z.png`
- `V13-Favoriten-Anbieter-Z-A.png`
- `AniSentinel-V13-Geräteclip-720p.mp4`
- `AniSentinel-V13-Discover-logcat.txt`

## Datenquellen und Grenzen

Sichtbare Inhalte stammen aus real gespeicherten AniWorld- und JustWatch-Daten. Es wurden keine Fake-Titel, Fake-Provider, Fake-Episoden oder simulierten Verfügbarkeiten ergänzt. JustWatch bleibt im Diagnosebuild eine inoffizielle, abschaltbare Integration. Keine Streaming-, Download-, DRM- oder Login-Funktion wurde implementiert.
