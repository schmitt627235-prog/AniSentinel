# AniSearch-Integrationsrecherche

## Aktuelle Implementierungsgrenze

Titelsuche und kontrollierter Detailseitenimport sind implementiert. Eine Detailseite liefert
keinen verifizierten konkreten Episodentermin; deshalb wird bewusst kein erfundener
`episode_releases`-Datensatz erzeugt und die UI meldet dies ausdrücklich. Eine eigene
`AniSearchCalendarSource`-Schnittstelle ist vorbereitet. Die automatische Zeitraumssynchronisierung
bleibt deaktiviert, bis eine reale öffentliche AniSearch-Tages-, Wochen-, Kalender- oder RSS-Seite
und echte gespeicherte HTML-Fixtures verifiziert sind. Ein Serienstartdatum wird niemals als
Episodenrelease ausgegeben.

Stand: 2026-08-02

## Untersuchte Referenzen

- RSS-Bridge `AnisearchBridge.php`: CSS-basierte Listen- und Detailauswertung,
  `li.btype0`, `div.details-text`, `img#details-cover`, 30-Minuten-Cache.
- MediaBrowser `Emby.Plugins.AniSearch`: Such-URL, persistente AniSearch-ID,
  sprachgebundene Titel, `#desc-de`, Bild-, Genre- und Bewertungsfelder.
- manami-project/modb-app AniSearch-Modul: getrennte Downloader-/Converter-Schichten,
  JSON-LD-first, OpenGraph-/XPath-Fallbacks, 200-/404-Behandlung und umfangreiche
  gespeicherte HTML-Fixtures. Das Projekt steht unter AGPL-3.0; es wurde kein Code kopiert.
- Crypto90 `parseAniSearchPopular20.py`: öffentlicher Listenabruf mit User-Agent.
  Download- und Drittanbieter-Automation sind für AniSentinel ausdrücklich ausgeschlossen.

## Übernommene Architekturideen

1. Transport und Parser sind getrennt.
2. Strukturierte JSON-LD-Daten haben Vorrang; DOM/OpenGraph dienen als Fallback.
3. AniSearch-ID und direkte Quellen-URL werden dauerhaft gespeichert.
4. Suchtreffer werden über `/anime/<id>,<slug>` normalisiert und anhand der ID dedupliziert.
5. Deutsche Beschreibung wird bevorzugt über `#desc-de` beziehungsweise
   `[itemprop=description][lang=de]` gelesen.
6. Automatische Abrufe verwenden 30-Minuten-Dateicache, mindestens vier Sekunden Abstand,
   einen aussagekräftigen User-Agent und höchstens einen Retry.
7. 401/403, 404, 429 und 5xx bleiben unterscheidbare Fehlerzustände.
8. Anbieterlinks werden nur übernommen, wenn sie tatsächlich in der AniSearch-Seite stehen.

## Nicht übernommen

- keine Regex-Auswertung eines kompletten HTML-Dokuments als Hauptparser
- keine globalen veränderlichen Suchlisten
- kein Login, keine Cookiesitzung und keine CAPTCHA-/Schutzumgehung
- kein Massencrawl und keine parallelen AniSearch-Anfragen
- keine Download-, Streaming-, Playback- oder DRM-Funktion
- kein Quellcode aus AGPL-/anderen Projekten

## Referenzen

- https://github.com/RSS-Bridge/rss-bridge/blob/master/bridges/AnisearchBridge.php
- https://github.com/MediaBrowser/Emby.Plugins.AniSearch
- https://github.com/ipkpjersi/modb-app/tree/main/anisearch
- https://github.com/Crypto90/anime-loads-automation/blob/master/parseAniSearchPopular20.py
