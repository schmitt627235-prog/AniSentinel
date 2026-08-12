# AniSentinel v0.24.4 – Validierung der Provider-Historie

## Ergebnis

Der historische ADN-Datenweg wurde als gekapselter, anonymer Diagnoseimport
implementiert und auf einem realen Samsung SM-S928B geprüft. Er benötigt weder
Konto noch Token, Login, Cookies, Playback-, Manifest-, DRM- oder Downloadzugriff.

## Reale ADN-Prüfung

- Endpunkt: `https://gw.api.animationdigitalnetwork.com/video/show/1133?maxAgeCategory=18&limit=-1&order=asc`
- Markt-Header: `X-Target-Distribution: de`
- Ergebnis: `IMPORTED`
- Episoden empfangen: 12
- Episoden mit explizitem Datum: 12
- beobachtetes Datumsfeld: `releaseDate`
- erzeugte historische Sprachzeilen: 24
- Sprachabbildung: `vostde` → `GER_SUB`, `vde` → `GER_DUB`
- Konflikte: 0

Der Live-Test verwendete eine isolierte In-Memory-Room-Datenbank. Er hat weder
produktive App-Daten noch Kalendertermine des Nutzers verändert.

## Schutzregeln

- Nur explizit pro Episode gelieferte Providerdaten werden importiert.
- Fehlende Datumsfelder bleiben unbekannt; es wird kein Veröffentlichungsrhythmus abgeleitet.
- Zukünftige Daten werden durch den Historienimport nicht als Historie übernommen.
- Historische Zeilen tragen Quelle, Original-URL und Abrufzeitpunkt.
- Gleichrangige oder höher priorisierte abweichende Bestände werden nicht überschrieben.
- Historische Einträge lösen keine Due-/Availability-Benachrichtigung, keinen Alarm,
  keinen AUTO-Timer, keinen WorkManager-Job und keinen T+10-Fallback aus.
- Der aktuelle AniWorld-Kalenderpfad bleibt unverändert.
- Eine offizielle ADN-News-Quelle wird erst dann automatisch importiert, wenn ein
  konkreter, episodescharfer öffentlicher Beleg vorliegt. Es werden keine Daten erfunden.

## Testnachweis

- JVM: 204/204 erfolgreich
- Room-Migrationen auf SM-S928B: 9/9 erfolgreich
- anonymer ADN-Live-Diagnosetest auf SM-S928B: 1/1 erfolgreich
- Debug-APK: erfolgreich gebaut und installiert

## Sichtbarer Kalender- und Providerbestand

- manueller, monatsbezogener Historienabgleich im Kalender ergänzt
- der gewählte Monat bleibt nach dem Sync unverändert
- aktuelle und kommende AniWorld-Termine werden nicht ersetzt oder gelöscht
- realer Gerätebestand nach Import: 1.431 historische ADN-Sprachtermine aus zwei
  bereits sicher gespeicherten ADN-Zuordnungen
- Crunchyroll wird nur nach stabiler Series-ID oder exakt aufgelöster öffentlicher
  Serienseite importiert; fehlgeschlagene Auflösungen werden nicht als Erfolg ausgegeben
- beim Öffnen einer sicher zugeordneten Crunchyroll-Detailseite wird deren Historie
  automatisch und benachrichtigungsfrei ergänzt
- der Release-Verlauf gruppiert gespeicherte Termine nach AniWorld, Crunchyroll und ADN

## Reale Statusreparatur vom 07.08.2026

`That Time I Got Reincarnated as a Slime`, S4E17, GER_SUB besaß bereits einen
persistierten `firstAvailableAt`, war aber durch einen späteren Parserfehler auf
`CHECK_FAILED` zurückgestuft worden. V0.24.4 behandelt bestätigte Verfügbarkeit
nun monoton: technische Folgefehler dürfen eine bestätigte Episode nicht mehr
herabstufen. Der vorhandene Gerätebestand wurde beim App-Start repariert und als
`AVAILABLE_GER_SUB` / `AVAILABLE` verifiziert.

## Open-Source-Recherche

Als technische Referenz wurde `anidl/multi-downloader-nx` geprüft. Dessen
ADN-Modul dokumentiert den öffentlichen Katalog-/Show-Metadatenweg, den
DE-Distributionsheader sowie die Sprachcodes `vostde` und `vde`. AniSentinel
übernimmt davon ausschließlich die anonym erreichbaren Metadatenprinzipien;
Download-, Authentifizierungs- und Playback-Funktionen sind nicht enthalten.

## Offene Punkte

- weitere reale ADN-Titel mit abweichenden Katalogformen regressiv prüfen
- belastbare öffentliche ADN-Ankündigungen nur als eigene, niedriger priorisierte
  Quelle ergänzen, sofern sie exakte Episode und Datum nennen
- historische Crunchyroll- und ADN-Konflikte in einer eigenen Diagnoseansicht bündeln
