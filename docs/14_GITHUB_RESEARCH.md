# GitHub-Recherche: AniSearch-Implementierungen

Diese Projekte dienen nur als technische Referenz. Code nicht ungeprüft kopieren; Lizenz, Aktualität und Nutzungsbedingungen prüfen.

## RSS-Bridge
Repository: `https://github.com/RSS-Bridge/rss-bridge`
Referenzdatei: `bridges/AnisearchBridge.php`

Erkannte Idee:
- HTML laden
- DOM/CSS-Selektoren
- strukturierte Feeditems erzeugen
- Caching gegen wiederholte Abrufe

## Emby AniSearch Plugin
Repository: `https://github.com/MediaBrowser/Emby.Plugins.AniSearch`
Wichtige Dateien:
- `Emby.Plugins.AniSearch/api.cs`
- `AniSearchSeriesProvider.cs`
- `AniSearchEpisodenProvider.cs`

Erkannte Idee:
- AniSearch besitzt keine vom Projekt verwendete öffentliche API
- Such-/Detailseiten laden
- Regex-/HTML-Auswertung
- AniSearch-ID als externe Provider-ID speichern

## modb-app
Repository: `https://github.com/ipkpjersi/modb-app`
Wichtige Dateien:
- `anisearch/README.md`
- `AnisearchDownloader.kt`
- `AnisearchAnimeConverter.kt`
- `AnisearchCrawler.kt`

Erkannte Idee:
- Download und Konvertierung trennen
- Raw HTML zuerst speichern/verarbeiten
- JSON-LD/OpenGraph/XPath/HTML nutzen
- Crawler bewusst langsam ausführen

Aus früherer Codeanalyse wurde ein sehr konservativer Takt von ungefähr 7,5–12,5 Sekunden zwischen Abrufen als Schutz vor IP-Sperren beschrieben. Vor einer Verwendung im Mobilclient erneut im aktuellen Quellcode verifizieren. Für AniSentinel trotzdem deutlich weniger und gezielter abrufen.

## Crypto90/anime-loads-automation
Repository: `https://github.com/Crypto90/anime-loads-automation`
Referenz: `parseAniSearchPopular20.py`

Nur als einfacher Nachweis relevant, dass öffentliche HTML-Seiten mit User-Agent und Regex verarbeitet wurden. Der Download-Automationsanteil ist für AniSentinel ausdrücklich nicht relevant und darf nicht übernommen werden.

## Architekturfolgerung für AniSentinel
- Mobile Clients greifen nur gezielt auf wenige Favoriten zu.
- Parserhierarchie: JSON-LD > OpenGraph > DOM/XPath > Regex.
- Parser und Downloader trennen.
- Cache und Rate Limits zwingend.
- Für größere Nutzerzahlen wäre später ein optionaler zentraler Dienst sinnvoll, aber nicht Teil von V1.
