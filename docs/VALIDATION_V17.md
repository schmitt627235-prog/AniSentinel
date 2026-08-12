# AniSentinel DIAGNOSETEST V17 – Validierungsbericht

Stand: 2026-08-09, Gerät `R3CX4056Q8L`, Europe/Berlin

## Ergebnis

- Debug-APK `0.17.0-v17-diagnostic` (`versionCode 29`) erfolgreich gebaut und installiert.
- 153 Unit-Tests erfolgreich (`testDebugUnitTest`).
- Room-Schema 15 mit `sourceAvailableAt`; Migration des installierten V16-Bestands erfolgreich.
- `USE_EXACT_ALARM` ist auf dem Testgerät erteilt.
- Für zukünftige Releases existieren je ein `RELEASE_DUE`- und ein exakt zehn Minuten späteres `RELEASE_FALLBACK`-Wake-up.
- Der Notification-Deep-Link öffnet die konkrete Staffel, Episode und Sprachfassung.
- Provider-Retries enden nach zwölf gespeicherten Versuchen; WorkManager erzeugt keinen zweiten unendlichen Retrypfad.

## Reale V16-Historie als Migrationsgrundlage

Für „The World’s Strongest Rearguard“, Staffel 1, Folge 6 wurde auf dem Gerät festgehalten:

- geplanter Termin: 15:00 Uhr
- Fälligkeitszustellung: 15:02:14 Uhr (alter V16-/WorkManager-Weg)
- direkter Crunchyroll-Check: `CHECK_FAILED / PARSER_CHANGED`
- AniWorld-Fallback: `AVAILABLE_GER_SUB`
- erste Erkennung: 15:13:17 Uhr
- Verfügbarkeitszustellung: 15:13:24 Uhr

Diese Historie belegt den früheren Zeitversatz und begründet den V17-Wechsel auf zwei Exact-Alarme.
Sie ist kein nachträglicher Beweis für die sekundengenaue Zustellung eines zukünftigen V17-Alarms.

## Aktualisierter 17-Uhr-Realtest

Referenz: „Mushoku Tensei: Jobless Reincarnation“, Staffel 3, Folge 7, GER SUB.

- Due-Wakeup und Due-Notification trafen um 17:00 Uhr exakt ein.
- Der direkte Crunchyroll-Check endete um 17:00 und 17:05 mit `PARSER_CHANGED`.
- AniWorld meldete die Folge um 17:05 positiv; gemäß der verbindlichen T+10-Regel war der Fallback erst um 17:10 zulässig.
- Der erste zulässige Fallback um 17:10 setzte sofort `AVAILABLE_GER_SUB` und versandte die Notification.
- Die damalige Notification nannte fälschlich `Amazon DVD / Blu-ray`, weil der erste ungefilterte JustWatch-Anbieter übernommen wurde.

Der Korrekturbuild wurde auf demselben persistenten Datensatz erneut ausgeführt. Danach enthält Room:

```text
ANIWORLD_FALLBACK | Crunchyroll | AVAILABLE_GER_SUB
DIRECT_PROVIDER_CHECK | Crunchyroll | CHECK_FAILED
PARSER_CHANGED|http=200|type=text/html|bytes=273256|stage=TITLE_AND_EPISODE_MISSING
```

Physische Angebote werden nun vor Persistenz des AVAILABLE-Ergebnisses entfernt. Crunchyroll wird
aus den gespeicherten echten Streamingreferenzen bevorzugt. Der öffentliche Crunchyroll-Watch-Aufruf
lieferte HTTP 200 und eine 273.256 Zeichen große HTML-App-Shell, aber weder Titel- noch
Episodenmetadaten. Snake_case-Metadaten kann der erweiterte Parser auswerten; im realen Response
waren jedoch auch diese nicht enthalten. Daher ist für diesen Livefall noch keine belastbare direkte
Bestätigung möglich. Es wurden keine Login-, Playback-, DRM- oder Schutzmechanismen umgangen.

## Fachliche Grenzen

- JustWatch ordnet nur Serie/Staffel und deutsche Anbieter zu. Fehlende JustWatch-Episoden sind keine Negativaussage.
- Der öffentliche Crunchyroll-Datenweg war im realen Fall nicht auswertbar; die strukturierte Diagnose nennt HTTP 200, `text/html`, Antwortgröße und fehlende Titel-/Episodenmetadaten. Schutzmaßnahmen wurden nicht umgangen.
- AniWorld darf erst ab `expectedAt + 10 Minuten` und nur für die passende GER-SUB-/GER-DUB-Zeile bestätigen.
- `sourceAvailableAt` wird nur aus einer expliziten Quellenzeit wie `Neu! HH:mm Uhr` gesetzt; sonst bleibt es `null`.
- Nach ausgeschöpften Prüfversuchen bleibt ein technischer Misserfolg `OVERDUE_UNCONFIRMED`, nicht „bestätigt verspätet“.
- `USE_EXACT_ALARM` und die Diagnosequellen müssen vor einer öffentlichen Veröffentlichung separat geprüft werden.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='C:\Users\handi\AppData\Local\Android\Sdk'
.\gradlew.bat testDebugUnitTest assembleDebug
```
