# AniSentinel DIAGNOSETEST V16 – kurzfristiger Validierungsbericht

Stand: 2026-08-09  
Version: `0.16.0-v16-diagnostic` (`versionCode 28`)

## Ziel

V16 ist der installierbare Kandidat für den realen Hintergrundtest des favorisierten Releases
„The World’s Strongest Rearguard“, S1 E6 GER SUB, am 09.08.2026 um 15:00 Uhr.

## Vor dem Test bestätigt

- APK als Update installiert; Room-Daten und Favoriten blieben erhalten.
- Genau ein geplanter Releasejob für den genannten fachlichen Release um 15:00 Uhr.
- Keine fehlerhaften `aniworld:episode-*`-Releases in Room.
- JustWatch entscheidet nicht über konkrete Episodenverfügbarkeit.
- Direkter Anbietercheck läuft vor dem AniWorld-Fallback ab `expectedAt + 10 Minuten`.
- Ein semantischer Notification-Key und atomarer Room-Claim verhindern parallelen Doppelversand.
- App wird nach dem kurzen Stabilitätscheck nur in den Hintergrund gebracht und das Gerät gesperrt;
  es erfolgt ausdrücklich kein Force-Stop.

## V16-Korrekturen

- Leere Anbieter werden als „noch nicht eindeutig bestätigt“ bezeichnet.
- „Sub und Dub“ wird nur aus beiden positiven Flags derselben konkreten Episodenprüfung erzeugt.
- Kalender zeigt nach Fälligkeit letzte Prüfung, Ergebnis/Anbieter, ersten Erkennungszeitpunkt und
  Fehler-/Fallbackstatus.
- Begrüßung folgt der lokalen Gerätezeit und wird minütlich neu ausgewertet.
- Aktuelle Season verlangt reale AniWorld-Releases im aktuellen Broadcastfenster und zeigt die Anzahl.
- Verschobene Releases öffnen eine Detailliste mit Werk, Staffel, Episode, Sprache, altem/neuem
  Termin, Grund und Quelle.

## Automatisierte Validierung

```text
gradlew.bat testDebugUnitTest assembleDebug
BUILD SUCCESSFUL
147 Tests, 0 Fehler
```

## Bewusst offene Punkte nach dem kurzfristigen Build

- JustWatch-Handlung und Studios erfordern eine Erweiterung des Katalogmodells und eine Room-Migration;
  sie wurden nicht ungeprüft oder unter Zeitdruck erfunden.
- Providerzuordnung „Dara-san of Reiwa“ benötigt einen neuen realen JustWatch-Abruf und wird nicht
  durch eine hart codierte Sonderregel verfälscht.
- Der tatsächliche Due-/Available-Hintergrundversand wird erst durch das reale 15-Uhr-Ereignis
  bestätigt; dieser Bericht behauptet das Ergebnis nicht vorzeitig.

## Kurzer Gerätecheck

Der finale Kandidat absolvierte 5 Minuten mit 20 gezielten Navigations-/Scrollrunden bei konstanter
App-PID `12386`. Logcat enthielt `0` Crash-/ANR-Marker. Anschließend wurde nur HOME und danach die
Sperrtaste ausgelöst. Geräteendzustand: `Dozing`; kein Force-Stop.
