# AniSearch-Kalenderquellen: systematische Prüfung

Stand: 2026-08-02

## Ergebnis

AniSearch bleibt als primäre Quelle für deutsche Metadaten und Anbieterhinweise vorgesehen.
Für einen automatischen Episodenkalender konnte in diesem Durchlauf jedoch kein öffentlich
abrufbarer und anhand echter Antworten verifizierbarer AniSearch-Datenweg belegt werden.
Deshalb ist als konkrete betriebsfähige Kalenderalternative AniLists öffentliche GraphQL-
`airingSchedule`-Abfrage implementiert. Sie befüllt Room ohne Einzelimport für vier Wochen
rückwirkend und acht Wochen zukünftig. Jeder Datensatz ist als `ANILIST_AIRING_SCHEDULE`
gekennzeichnet und verlinkt direkt auf den AniList-Titel; er wird nicht als deutscher
Anbieter-Release ausgegeben.

## Geprüfte AniSearch-Wege und technische Ergebnisse

| Kandidat | konkrete URL / Muster | Ergebnis |
|---|---|---|
| robots.txt | `https://www.anisearch.de/robots.txt` | lokaler TLS-Fehler `SEC_E_NO_CREDENTIALS`, HTTP 000; Web-Indexer meldet robots-Sperre |
| Sitemap | `https://www.anisearch.de/sitemap.xml` | gleicher TLS-Fehler, keine Antwort/Fixture |
| Anime-Index | `https://www.anisearch.de/anime`, `/anime/index` | gleicher TLS-Fehler |
| vermutete Episodenliste | `https://www.anisearch.de/anime/episodes` | gleicher TLS-Fehler; kein belegtes Format |
| vermuteter Kalender | `https://www.anisearch.de/calendar`, `/kalender` | gleicher TLS-Fehler; kein belegtes Format |
| vermutetes RSS | `https://www.anisearch.de/rss`, `/anime/rss` | gleicher TLS-Fehler; kein belegtes Releasefeed-Format |
| saisonale Ansicht | `https://www.anisearch.de/anime/season` | gleicher TLS-Fehler |
| bekannte Suche | `https://www.anisearch.de/anime/index/?...&text=...` | Struktur aus Emby-Referenz belegt; dient der Titelsuche, nicht Wochenreleases |
| nach Datum sortierter Index | `https://www.anisearch.de/anime/index/page-1?char=all&synchro=de&sort=date&order=desc&view=4` | RSS-Bridge belegt Katalogeinträge/Detailtexte; kein konkreter Episodenkalender |
| Detailseite | `https://www.anisearch.de/anime/{id},...` | Parser/Referenzen belegen Metadaten und Anbieterlinks, aber keinen allgemeinen Wochenzeitraum |
| Episodenseite je Titel | `/anime/{id}/episodes` (Emby-Referenz) | titelgebundene Episodenmetadaten; kein kalenderweiter Einstieg ohne Massencrawl |

Es wurden keine Schutzmaßnahmen, Logins oder CAPTCHAs umgangen. Aus HTTP-000-Antworten wurden
keine Fixtures erfunden. Das Scheitern ist für die Codex-Windows-Umgebung reproduzierbar,
beweist aber ausdrücklich **nicht**, dass AniSearch auf normalen Android-Geräten unerreichbar
ist. `SEC_E_NO_CREDENTIALS` stammt aus Windows Schannel und kann durch Zertifikatsspeicher,
Sandbox- oder Prozessanmeldeinformationen verursacht werden; DNS oder eine serverseitige
AniSearch-Sperre wurden dadurch nicht isoliert nachgewiesen. Die Android-App verwendet den
Android-Netzwerkstack und kann daher ein anderes Ergebnis liefern.

Zur Gerätevalidierung liegt `AniSearchDeviceConnectivityTest` als Instrumentationstest bei.
Er klassifiziert einen echten öffentlichen Suchabruf auf dem angeschlossenen Gerät. In diesem
Durchlauf stand kein Android-Gerät zur Verfügung; deshalb wird kein Geräteresultat behauptet.

## GitHub-Referenzen

- RSS-Bridge `AnisearchBridge.php`: 30-Minuten-Cache, `li.btype0`, Detailseitenabruf;
  der Feed basiert auf einem nach Datum sortierten Anime-Katalog, nicht auf Episodenairings.
- MediaBrowser/Emby AniSearch: Suche, Detail- und titelgebundene Episodenpfade; keine
  kalenderweite Wochenquelle festgestellt.
- modb-app AniSearch: Downloader/Converter, JSON-LD/DOM-Fallbacks und echte Fixtures für
  Metadaten. Kein belegter Releasekalender; AGPL-Code wurde nicht übernommen.
- Crypto90: öffentlicher Listenabruf mit User-Agent; der Downloadteil ist ausgeschlossen.

## Implementierte Alternative

`AniListCalendarSource` fragt paginiert:

```graphql
airingSchedules(airingAt_greater: $from, airingAt_lesser: $until, sort: TIME)
```

ab und speichert pro Ausstrahlung `sourceReleaseId`, Anime-ID, Episode, `expectedAt`,
`metadataSource`, AniList-Quellenlink und `fetchedAt`. Monatswechsel lösen zusätzlich eine
Synchronisierung des gewählten Monats aus. Room-Upserts erhalten historische Episoden.

## Noch offen

- AniList liefert nicht zuverlässig deutsche Titel und keine bestätigte deutsche
  Anbieter-Verfügbarkeit. Die UI darf diese Daten daher nicht so bezeichnen.
- Sobald AniSearch wieder kontrolliert erreichbar ist, müssen echte Antworten aus Tages-,
  Wochen-, RSS-, Saison- und Episodenansichten gespeichert und erst danach Parser aktiviert
  werden.
- Eine spätere ID-Zuordnung kann AniList-Termine mit AniSearchs deutschen Metadaten verbinden,
  ohne AniSearch durch einen Massencrawl zu belasten.
