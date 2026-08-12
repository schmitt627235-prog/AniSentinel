# AniWorld-Quellenrecherche – 3. August 2026

## Verifizierte öffentliche Seiten

- Kalender: `https://aniworld.to/animekalender`
- Verschiebungen: `https://aniworld.to/support/frage/anime-verschiebungen`
- Beide Seiten waren ohne Login aufrufbar. AniSentinel verwendet keine Wiedergabe-, Download-,
  Manifest- oder DRM-Funktion und folgt keinen Streamingendpunkten.
- Der Diagnoseadapter führt je Quelle einen begrenzten HTML-GET aus, setzt einen User-Agent,
  verwendet Zeitlimits und einen 30-Minuten-Cache.

## Kalenderstruktur

Die reale Fixture enthält `section.calendarList` mit einem vollständigen Datum, einzelnen Karten,
Titel, `SxxExx`, Uhrzeit, relativer Anime-URL und Sprachflaggen. Sichtbare Karten werden im HTML
teilweise mehrfach für verschiedene Sprachfassungen wiederholt.

AniSentinel übernimmt ausschließlich:

```text
japanese-german.svg → GER_SUB
german.svg          → GER_DUB
```

`japanese-english.svg` und alle übrigen Flaggen werden verworfen. Gleichsprachige Duplikate
werden über normalisierten Titel, Staffel, Episode, korrigierte Zeit und Sprachfassung entfernt.
GER SUB und GER DUB bleiben eigenständig. Dadurch können beispielsweise am selben Tag eine neue
Sub-Folge und eine ältere Dub-Folge derselben Staffel getrennt erscheinen.

## Zeitregel

```text
AniSentinel-Releasezeit = AniWorld-Originalzeit − 10 Minuten
```

Originalzeit, korrigierte Zeit, `adjustmentMinutes=-10`, Abrufzeit, Quelle und Sprachfassung werden
gespeichert. Auch 23:59 wird zu 23:49; zusätzlich wird
`originalTimeWasEndOfDayMarker=true` gespeichert.

## Verschiebungen

Die Seite enthält Titelblöcke, Staffel/Episode, alte und neue Daten, Sub/Dub, Richtung, Gründe und
teilweise externe Beleglinks. Ein `?` wird nicht in einen erfundenen Termin umgewandelt.

Ein Override benötigt zwingend einen eindeutigen Match aus normalisiertem Titel, Staffel, Episode
und Sprachfassung. Sub kann nur GER_SUB und Dub nur GER_DUB ändern. Mehrdeutige oder fehlende
Treffer bleiben unangetastet. Alter und neuer Termin werden dauerhaft in
`release_schedule_history` gespeichert. Historisierte Release-IDs sind vor späteren
Bereichslöschungen geschützt.

## Reale Diagnosewerte

Der Gerätelauf vom 3. August 2026 zeigte 195 deduplizierte deutsche GER-SUB-/GER-DUB-Termine im
August und eine eindeutig zugeordnete Sub-Verschiebung für „That Time I Got Reincarnated as a
Slime“, Staffel 4, Folge 17: 31. Juli → 7. August. Die Dub-Zeile wurde nicht fälschlich auf den
Sub-Termin angewendet, weil kein exakt passender GER-DUB-Kalendereintrag für dieselbe Folge vorlag.

