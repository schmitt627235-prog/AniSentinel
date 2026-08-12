# AnimeRadar-Quellenrecherche und Diagnoseintegration

Stand: 3. August 2026. Diese Dokumentation beschreibt ausschließlich den beobachteten
öffentlichen Kalenderdatenweg und stellt keine Aussage über eine Erlaubnis zur Nutzung in
einer öffentlichen Produktversion dar.

## Verifizierter öffentlicher Datenweg

- Kalenderseite: `https://www.animeradar.de/kalender`
- Kalenderabruf: `POST https://www.animeradar.de/api/anilist`
- `GET` antwortet mit HTTP 405; der Adapter verwendet deshalb nur POST.
- Der Abruf funktionierte im normalen Browser und auf dem verbundenen Android-Testgerät ohne Login.
- Am Prüftag zeigte die Kalenderseite 127 Einträge auf sieben Seiten. Der spätere Gerätelauf
  lieferte dynamisch 128 Einträge. Die App zeigt immer den tatsächlich empfangenen Wert.

Der POST-Body enthält eine GraphQL-Abfrage mit `page`, `perPage`, `airingAt_greater` und
`airingAt_lesser`. Verwendete Antwortfelder:

```text
data.Page.pageInfo.currentPage / hasNextPage / lastPage / total / perPage
data.Page.airingSchedules.id / episode / airingAt
media.id / siteUrl / format / status / episodes / season / seasonYear / countryOfOrigin
media.title.romaji / english / native
media.coverImage.extraLarge / large
media.genres
media.studios.nodes.name
```

`airingAt` ist ein Unix-Zeitstempel in Sekunden. Die App speichert den Zeitpunkt als Instant
und formatiert ihn erst in der Gerätezeitzone. Die AniList-ID wird als stabile externe ID,
die Schedule-ID als Release-ID verwendet.

## Fachliche Grenzen

Die Daten belegen einen geplanten internationalen Ausstrahlungstermin. Sie belegen nicht
automatisch eine deutsche Synchronfassung, einen deutschen Simulcast oder die konkrete
Verfügbarkeit bei einem Streaminganbieter. Deshalb bleiben `provider`, `language` und
`titleGerman` leer, wenn der Release-Datensatz diese Angaben nicht ausdrücklich liefert.

Die sichtbaren Anbieter- und Dub-Filter werden nicht als Episodenbeweis übernommen. Der
zusätzlich beobachtete Endpunkt `/api/streaming-provider-options` ist keine Bestätigung, dass
eine konkrete Folge bei einem Anbieter verfügbar ist.

## Betriebs- und Sicherheitsregeln

- AnimeRadar ist nur in Debug/DIAGNOSETEST standardmäßig aktiv.
- Release setzt `anime_radar_enabled=false`, bis die Drittverwendung geklärt ist.
- Pro Seite werden höchstens 20 Einträge angefordert; maximal 40 Seiten je explizitem Abruf.
- Der automatische Start- und Hintergrundabruf ist auf die aktuelle Kalenderwoche begrenzt.
- Erfolgreiche Daten werden 30 Minuten lokal als frisch behandelt.
- Requests verwenden User-Agent, Zeitlimits, Retry-After und exponentielles Backoff.
- Erst vollständig validierte Pagination wird transaktional in Room geschrieben.
- Bei HTTP-, Parser-, Pagination- oder Fallbackfehlern bleiben vorhandene Room-Daten bestehen.
- AniList wird nur bei nicht erreichbarer oder unvollständiger AnimeRadar-Antwort versucht.
- AniSearch bleibt in diesem Datenweg deaktivierter optionaler Metadaten-Fallback.

## Nachweis

Parser-Tests verwenden die echte Fixture
`app/src/test/resources/fixtures/animeradar_calendar_page_2026-08-03.json`. Der Gerätelauf
bestätigte POST → 128 empfangene Einträge → 128 gespeicherte Einträge → sichtbare Titel und
persistente Room-Daten.

