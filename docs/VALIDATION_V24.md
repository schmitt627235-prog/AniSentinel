# AniSentinel v0.24.0 – Validierungsbericht DIAGNOSETEST V24

**Prüfdatum:** 11.08.2026  
**Gerät:** physisches Samsung-Android-Testgerät, verbunden per ADB

**Paket:** `de.anisentinel.app`  
**Version:** `versionCode 36`, `versionName 0.24.0-v24-diagnostic`

## Ergebnis

V24 erfüllt die umsetzbaren Markt- und Regressionsanforderungen des V24-Auftrags. Die deutsche
JustWatch-Marktsemantik wird in Room gespeichert und in der Diagnoseansicht nachvollziehbar
dargestellt. Physische Kaufangebote werden nicht als Streaminganbieter behandelt.

## Build und automatisierte Tests

- `testDebugUnitTest`: **181/181 erfolgreich**, 35 Testsuites, 0 Fehler.
- `assembleDebug`: **erfolgreich**.
- Direkte Android-Instrumentierung `AniSentinelMigrationTest`: **6/6 erfolgreich**.
- Der neue reale Migrationstest V16→V17 prüft `providerMarket`, den Backfill auf `DE`, den Erhalt
  von Crunchyroll und die Entfernung physischer Amazon-DVD-/Blu-ray-Angebote.
- Der Gradle-UTP-Starter konnte wegen eines lokalen PKIX-Zertifikatfehlers beim Abruf einer
  Testabhängigkeit nicht starten. Dieselbe erzeugte Instrumentierungs-APK wurde deshalb direkt mit
  `adb shell am instrument` ausgeführt; das Testergebnis selbst ist vollständig grün.

## Reale Migration und Datenbankprüfung

Die bestehende V23-Gerätedatenbank wurde ohne Löschen der App-Daten durch Installation der V24-APK
auf Schema 17 migriert.

- `PRAGMA user_version`: **17**
- Spalte `provider_references.providerMarket`: **vorhanden**
- Providerreferenzen mit Markt `DE`: **1.019**
- Providerreferenzen mit anderem oder fehlendem Markt: **0**
- Erkannte physische DVD-/Blu-ray-/Buch-/Shopreferenzen nach App-Sync: **0**
- Referenzregressionen: Slime behält Crunchyroll, Bumpkin Amazon Prime Video und MAO (2026)
  Aniverse Amazon Channel.

Die Detailansicht zeigt für gespeicherte Anbieterreferenzen zusätzlich:

```text
Katalogmarkt: DE · Quelle: JustWatch · Stand: <Zeitpunkt>
```

`DE` bezeichnet den Katalogmarkt. Diese Anzeige behauptet weder eine deutsche Synchronfassung
noch die Bestätigung einer konkreten Episode.

## Nächster echter Release und Alarmzustand

Der nächste echte Favoritenrelease während der Prüfung war Clevatess, Staffel 2, Folge 6: GER SUB
und GER DUB jeweils am **12.08.2026 um 14:00 Uhr Europe/Berlin**.

Für beide sprachlich getrennten Release-IDs sind persistente Zeitpläne vorhanden. Androids
AlarmManager enthält jeweils exakte `RELEASE_DUE`-Alarme für 14:00 Uhr und
`RELEASE_FALLBACK`-Alarme für 14:10 Uhr. Zum Prüfzeitpunkt lagen diese Auslösungen noch ungefähr
17 Stunden in der Zukunft; `notification_deliveries` enthielt erwartungsgemäß noch keinen Eintrag.

Der Auftrag schließt einen langen Wartetest aus. Daher wird die spätere reale Auslösung **nicht als
bestanden ausgegeben**. Verifiziert sind Persistenz, getrennte GER-SUB-/GER-DUB-Identitäten, exakte
Alarmregistrierung und keine verfrühte Zustellung. Die Messung nach der tatsächlichen Fälligkeit
bleibt ein offener Live-Abnahmepunkt.

## Geräteclip

- Auflösung: **720 × 1560**
- Dauer: **25 Sekunden**
- Größe: rund **6,75 MB**
- MP4-Struktur: `ftyp`, `mdat` und abschließendes `moov` vorhanden
- Inhalt: Detail-/Providerdiagnose und Rückkehr zur real befüllten Startseite

## Offene Punkte

1. Reale Clevatess-Fälligkeitskette nach dem 12.08.2026 um 14:00 Uhr messen: Due-Alarm,
   Providerprüfung, +10-Minuten-Fallback nur bei Bedarf, Statusübergang und genau eine Zustellung
   pro Release-ID.
2. Nutzungsfreigaben der Diagnose-Drittquellen vor einer öffentlichen Produktversion klären.
3. Direkte Provider-Episodenprüfung weiter ausbauen; ein JustWatch-Katalogangebot bleibt nur die
   Anbieterzuordnung der Serie/Staffel und keine Episodenbestätigung.
