# AniSentinel DIAGNOSETEST V18 – Validierungsbericht

Stand: 2026-08-09, physisches Android-Testgerät, Europe/Berlin

## Build und Tests

- Version `0.18.0-v18-diagnostic`, VersionCode 30.
- Debug-APK erfolgreich gebaut und als Upgrade installiert.
- 158 Unit-Tests erfolgreich.
- Room-Schema bleibt kompatibel auf Version 15; keine destruktive Migration.

## Eindeutiger Due-Pfad

- Neue Releases erhalten keinen `FavoriteReleaseDueWorker` mehr.
- Exact `RELEASE_DUE` ist der einzige zeitkritische Fälligkeitsauslöser.
- Exact `RELEASE_FALLBACK` bei T+10 bleibt erhalten.
- V17-Altjobs werden über Namen und früheren WorkManager-Tag storniert.
- Geräteprüfung nach Upgrade: `FavoriteReleaseDueWorker`-Jobs = 0.
- Exact Due- und Fallbackalarme sind im AlarmManager weiterhin vorhanden.
- WorkManager bleibt für Provider-Retry, Recovery und Synchronisation.

## Notification-Deep-Link

Der reale Geräteaufruf auf Mushoku Tensei S3E7 GER SUB öffnete die konkrete Detailansicht. Sichtbar:

```text
Release aus Benachrichtigung
S3 · Folge 7 · GER_SUB
Nächster Release: S3 Folge 8
Letzter Release: S3 Folge 7
AVAILABLE_GER_SUB
Anbieter: Crunchyroll
Erstmals erkannt: 17:10
Letzte Prüfung
```

Der Nachweis liegt in `v18-deeplink.xml`.

## Aktuelle Season

- Primärbasis sind ausschließlich reale `ANIWORLD_CALENDAR`-Releases.
- JustWatch ist optionale Anreicherung und keine Aufnahmebedingung.
- Ein Zyklus gilt als aktiv, wenn vergangener und kommender Termin innerhalb des laufenden
  21-Tage-Zyklus vorliegen, kürzlich mehrere Episoden erschienen oder der nächste reale Termin
  innerhalb acht Tagen eine Episodennummer größer 1 besitzt.
- Zukunfts-only-Staffelstarts bei Episode 1, beendete Zyklen und reine Katalogzeilen werden ausgeschlossen.
- Potenziell falsche Kataloganreicherungen werden nur bei normalisiert gleichem Titel übernommen;
  `One Piece (2023)` kann dadurch nicht allein über eine Katalogzuordnung die Seasonkarte bestimmen.
- Realer Gerätebestand: 78 aktive Titel statt zuvor 12.
- Die Liste wurde bis zum alphabetischen Ende gescrollt; Prozess blieb aktiv, kein Absturz.

## Providerpfad

- StreamingProviderPolicy und korrigierter Crunchyroll-Fallback bleiben aktiv.
- Physische Händler können keinen bestätigten AVAILABLE-Anbieter liefern.
- Der direkte öffentliche Crunchyroll-Weg bleibt ehrlich offen: HTTP 200 lieferte im Referenzfall
  eine HTML-App-Shell ohne Titel-/Episodenmetadaten. Keine titelbezogenen Sonderregeln und keine
  Login-, Playback-, DRM- oder Schutzumgehung wurden ergänzt.

## Unverändert beibehalten

- getrennte `sourceAvailableAt`, `firstAvailableAt`, `lastCheckedAt`
- begrenzte Provider-Retries
- letzter und nächster Release
- anklickbare Verschiebungsstatistik
- dynamische Begrüßung
- kompakte Dub-Liste
- App-Changelog enthält V17, V16, V15, V14 und V13
