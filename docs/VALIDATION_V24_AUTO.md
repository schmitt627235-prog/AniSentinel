# AniSentinel v0.24.1 – Validierungsbericht DIAGNOSETEST V24 AUTO

## Umgesetzte Produktstrategie

AUTO berechnet jeden Folgetermin aus dem unveränderlichen `expectedAt` und der aktuellen Zeit:

```text
0–5 Minuten   → 30 Sekunden
5–10 Minuten  → 1 Minute
10–60 Minuten → 5 Minuten
1–4 Stunden   → 30 Minuten
danach         → 1 Stunde
```

Verpasste Ticks werden übersprungen und nicht nachgeholt. Dadurch entsteht weder Countdown- noch
Scheduler-Drift. Der frühere Versuchszähler `0/5/10/20/30/60` und sein maximales Versuchslimit
wurden entfernt.

## Android-Ausführung

- Due und T+10-Fallback bleiben separate Exact-Alarme.
- Jeder kurzfristige AUTO-Tick ist ein einzelner Exact-Alarm.
- Der Alarm startet einen einmaligen WorkManager-Netzcheck; WorkManager bestimmt nicht das
  Intervall und verwendet dafür keinen generischen Retry-Backoff.
- Nach dem Check plant die App den nächsten Tick erneut aus `expectedAt`.
- Android darf Alarme aufgrund eigener Energie-/Herstellerregeln verzögern; die App ersetzt die
  Produktstaffel deshalb nicht durch ein falsches periodisches WorkManager-Intervall.

## Begrenzung und Abbruch

- Es werden ausschließlich Releases aktiver Favoriten überwacht.
- Deaktivieren eines Favoriten entfernt Due-, Fallback- und Availability-Alarme.
- `AVAILABLE`, `POSTPONED`, `DELAYED` und `DELAYED_CONFIRMED` sind terminal.
- Ein vor `expectedAt` importierter Verschiebungsstatus verhindert die Planung am alten Termin.
- Eine während AUTO erkannte Verschiebung stoppt den laufenden Availability-Alarm sofort.
- Release-History behält `previousAt`; ein real vorhandenes `revisedAt` bleibt nachvollziehbar.
- Benachrichtigungen übernehmen Anime, Episode, Staffel, vorhandenen Grund und vorhandenen neuen
  Termin. Fehlende Angaben werden nicht erfunden.

## Manuelle Profile

Auswählbar sind `30s`, `1m`, `2m`, `5m`, `10m`, `15m`, `30m`, `1h` und `automatic`.
Die Auswahl wird sowohl global in DataStore als auch je Favorit gespeichert.

## Automatisierte Prüfung

- Vollständiger JVM-Teststand: **183 Tests**.
- Android-Room-Migrationstests direkt auf dem Gerät: **6/6 erfolgreich**.
- Neue Grenztests prüfen 0/5/10/60 Minuten und 4 Stunden sekundengenau.
- Tests prüfen alle acht manuellen Intervalle, Driftfreiheit und terminale Zustände.
- Room-Migrationstests bleiben unverändert Bestandteil der Android-Instrumentierungs-APK.

## Reale Live-Abnahme

Clevatess S2E6 GER SUB und GER DUB am 12.08.2026 um 14:00 Uhr bleiben der reale nächste
Abnahmepunkt. Due- und T+10-Alarme bleiben erhalten. Erwartete AUTO-Sequenz:

```text
14:00–14:05 → 30-Sekunden-Ticks
14:05–14:10 → 1-Minuten-Ticks
ab 14:10    → 5-Minuten-Ticks plus unabhängiger AniWorld-Fallback
```

Da dieser Zeitpunkt beim Build noch in der Zukunft liegt und kein langer Wartetest erlaubt ist,
wird die tatsächliche Live-Ausführung nicht vorzeitig als bestanden behauptet.

## Geräteclip

Der beigefügte 25-Sekunden-Clip wurde direkt auf dem Gerät in 720 × 1560 aufgenommen. Er zeigt
das Watch-Profil in den Einstellungen und den Wechsel von `Automatisch` über 30 Sekunden,
1 Minute, 2 Minuten und weitere manuelle Intervalle. Die MP4-Struktur enthält `ftyp`, `mdat` und
einen vollständig geschriebenen `moov`-Block.
