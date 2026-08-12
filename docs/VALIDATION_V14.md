# AniSentinel v0.14.0 – V14-Validierung

Stand: 9. August 2026, Diagnosebuild `0.14.0-v14-diagnostic` (VersionCode 26).

## Behobene Absturzursache

Der echte V13-Geräteabsturz wurde aus Androids Crashpuffer gesichert. Compose meldete beim Scrollen:

```text
java.lang.IllegalArgumentException: Key "search:justwatch:ts372486" was already used.
```

Ein weiterer reproduzierter Schlüssel war `justwatch:tm1448177`. Verschiedene JustWatch-Katalogzeilen
konnten auf dieselbe interne Anime-ID zeigen. Suche und Entdecken verwendeten diese interne ID trotzdem
als `LazyColumn`-Schlüssel. V14 führt deshalb die JustWatch-ID als `stableKey`, dedupliziert danach und
verwendet für beide Listen eindeutige Schlüssel.

Zusätzlich wurden parallele Katalogaktualisierungen zusammengeführt, veraltete Suchjobs abgebrochen und
Entdecken auf aktive AniWorld-Releases statt auf die gesamte Releasehistorie begrenzt.

## Favoritenphasen

`Aktuell` und `Demnächst` sind jetzt exklusiv:

- `Aktuell`: mindestens ein Release am heutigen Tag.
- `Demnächst`: kein heutiges, aber mindestens ein zukünftiges Release.

Der Regressionstest deckt einen Favoriten mit heutigem und zukünftigem Termin ab. Auf dem Testgerät zeigte
`Aktuell` unter anderem „The World's Strongest Rearguard“ und „Mushoku Tensei“, `Demnächst` dagegen
„Skeleton Knight in Another World“. Die sichtbaren Mengen überschnitten sich nicht.

## Automatisierte Validierung

```text
./gradlew testDebugUnitTest assembleDebug --no-daemon
BUILD SUCCESSFUL
30 Testsuiten, 141 Tests, 0 Fehler, 0 übersprungen
```

## Reale Gerätevalidierung

Gerät per ADB, App-Prozess während des gesamten finalen Laufs: PID 4440.

- Suche: 26 Durchläufe mit Pokémon, Naruto, Attack on Titan, One Piece, Dragon Ball und Demon Slayer;
  jeder Durchlauf bis zum Listenende und zurück, ungefähr 15 Minuten.
- Entdecken: 19 vollständige Zyklen über Alle, Action, Animation, Serien, Filme, Laufend sowie drei
  Sortierungen; jeweils bis zum Listenende und zurück, 12:24 Minuten.
- 20 vollständige Navigationsrunden über alle fünf Hauptbereiche.
- 10 Hintergrund-/Vordergrundwechsel.
- 5 Bildschirm-Aus-/Einschaltzyklen.
- Gesamtdauer der kumulierten Belastung über 30 Minuten.
- Prozess-ID blieb 4440; kein Crash, kein ANR, kein Prozessneustart.
- Finaler Android-Crashpuffer leer; keine Treffer für `FATAL EXCEPTION`, `ANR`, `OutOfMemoryError`,
  `SIGABRT`, `SIGSEGV` oder LMK-Prozessbeendigung.

Die Protokolle und der ursprüngliche V13-Stacktrace liegen im Ausgabeordner. Der Geräteclip wurde mit
720 × 1560 Pixeln aufgenommen, um das Gesamtpaket sicher unter 512 MB zu halten.

## Weiterhin offene reale Abnahmen

- Der natürliche Ablauf an einem echten Releasezeitpunkt einschließlich Hintergrundbenachrichtigung ist
  noch nicht über einen vollständigen realen Zeitverlauf abgenommen.
- Eine Anbieteranzeige belegt eine titel-/staffelbezogene JustWatch-DE-Zuordnung. Sie ist ohne eindeutige
  episodenspezifische Evidenz keine Behauptung, dass eine konkrete Folge bereits abrufbar ist.
- Die aktive Diagnosekonfiguration bleibt AniWorld plus JustWatch. AniList, AnimeRadar und AniSearch sind
  in diesem Pfad deaktiviert.
