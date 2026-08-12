# AniSentinel v0.22.0 – Validierungsbericht

Datum: 2026-08-11  
Build: `0.22.0-v22-diagnostic` (`versionCode 34`)  
Room: Schema 16, unverändert

## Ziel und Umsetzung

V22 ist absichtlich ein kleiner Robustheitsstand. Die interne Newsdetailseite unterscheidet nun
explizit zwischen `Loading`, `Found` und `NotFound`. Liefert Room für eine ungültige, gelöschte oder
nicht mehr vorhandene `announcementId` keinen Datensatz, wird der Ladezustand beendet. Die UI zeigt
„Meldung nicht mehr verfügbar“ und bietet eine direkte Rückkehr zu „News & Meldungen“ an.

Der reguläre V21-Datenweg bleibt unverändert:

```text
News-Karte → news/{announcementId} → persistierter Room-Datensatz → interne Detailansicht
```

Nur separate, validierte HTTPS-Quellenbuttons dürfen weiterhin einen externen Browser öffnen.

## Build und automatische Tests

- `testDebugUnitTest assembleDebug --no-daemon`: erfolgreich.
- 35 Testsuiten, 171 Tests, 0 Fehler, 0 Fehlschläge.
- Neue Regressionstests prüfen die Abbildung `null → NotFound` und Datensatz → `Found`.
- Die bestehenden News-, Favoriten-, Kalender-, Provider-, Exact-Alarm-, Fallback- und Deep-Link-
  Tests bleiben grün.

## Abgrenzung

- Keine Änderung an Room-Schema oder Migrationen.
- Keine Änderung an AniWorld-, JustWatch- oder Providerprüfregeln.
- Keine erfundenen Meldungsinhalte oder Ersatzdaten.
- Kein umfangreicher Langzeit- oder Stresstest in diesem kleinen Korrekturstand.

## Geräteprüfung

Die finale Diagnose-APK wurde auf dem angeschlossenen Samsung SM-S928B installiert. Der gezielte
Gerätelauf öffnete „News & Meldungen“, danach die reale Meldung „UNIQLO und YOASOBI stellen neue
T-Shirt-Kollektion vor“ intern als „Meldungsdetails“. Zusammenfassung, Veröffentlichungszeit,
Quellenangabe und der separate Quellenbutton waren vorhanden. Android-Zurück führte zur Liste;
anschließend wurde die App beendet und erfolgreich neu gestartet.

Der Lauf wurde in 720p aufgezeichnet. Logcat enthält für diesen Ablauf 0 `FATAL EXCEPTION` und
0 App-ANRs. Der nicht vorhandene Datensatz wird zusätzlich deterministisch im Unit-Test abgedeckt,
da die Produktionsnavigation keine beliebige interne News-ID als öffentliche Deep-Link-Schnittstelle
exportiert.
