# Datenquellen und Verantwortlichkeiten

Stand der Recherche: 30.07.2026. Vor Implementierung jeweils erneut prüfen.

## 1. AniList – primäre Metadatenquelle
Offizielle GraphQL-API: `https://graphql.anilist.co`
Dokumentation: `https://docs.anilist.co/`

Verwendung:
- IDs
- Titelvarianten
- Cover und Banner
- Beschreibung
- Genres, Tags, Studios
- Season/Jahr
- Episodenanzahl
- Beziehungen
- japanischer Airing Schedule als Hilfsinformation

Nicht als alleinige Quelle verwenden für:
- deutsche Anbieter
- deutsche Untertitel/Synchronfassung
- tatsächliche deutsche Freischaltzeit

Caching:
- Stammdaten 7 Tage
- aktuelle Airing-/Season-Daten 6–24 Stunden
- Cover über Coil-Diskcache

## 2. AniSearch – mögliche deutsche Kalender- und Lizenzquelle
AniSearch hat nach bisheriger Recherche keine öffentlich dokumentierte API. Nutzung daher nur defensiv, abschaltbar und unter Beachtung von Nutzungsbedingungen/robots.txt.

Mögliche Daten:
- deutscher Titel
- deutsche Lizenz-/Anbieterhinweise
- deutscher Kalender
- Dub-/OmU-Hinweise
- AniSearch-ID als externe Referenz

Parserpriorität:
1. JSON-LD
2. OpenGraph
3. DOM/CSS/XPath
4. Regex nur als letzter Fallback

Regeln:
- Roh-HTML optional als Debug-Fixture speichern, nicht unbegrenzt.
- sehr konservatives Rate-Limit
- Kalender höchstens gezielt bzw. 1–2 Mal täglich aktualisieren
- Detailseiten nur bei fehlenden/veralteten Daten
- ETag/Last-Modified respektieren, falls vorhanden
- Parser per Remote-/lokalem Feature-Flag abschaltbar

## 3. Anbieter-Checker – tatsächliche Verfügbarkeit
Jeder Anbieter besitzt ein eigenes Modul. Checker prüfen ausschließlich öffentliche Metadaten bzw. die sichtbare Episoden-/Serienstruktur. Keine Videodaten, DRM- oder Playback-Tokens.

### Crunchyroll
Offizieller Release-Kalender ist auf der Website verfügbar.
Priorität:
1. öffentliche Release-Kalender-/Episodenmetadaten
2. Serien-/Staffel-/Episodenseite
3. interne JSON-Metadaten nur nach gesonderter Prüfung und als instabile Quelle

Ergebnis muss Region DE und gewünschte Sprachfassung berücksichtigen.

### ADN
Prüfen, ob öffentliche Serien-/Episodenmetadaten für die deutsche Distribution nutzbar sind. Vorhandene Episode ist nicht automatisch freigeschaltet; Status und Veröffentlichungszeit müssen getrennt werden.

### Netflix, Disney+, Prime Video, aniverse
Schwieriger und nicht für V1 priorisieren. Zunächst nur sichtbare Katalog-/Titelseiten oder lizenzierte Partnerdaten verwenden. Bei Unsicherheit Status als `CATALOG_DETECTED` oder `UNKNOWN`, nicht als sicher abspielbar.

### AKIBA PASS TV und kleinere Anbieter
Individueller HTML-/JSON-Checker, wenn öffentliche Episodenlisten stabil erreichbar sind.

## 4. News und Verschiebungen
Quellenpriorität:
1. offizielle Anbieter-News/RSS
2. offizieller Anbieter- oder Publisher-Account
3. etablierte deutsche Anime-Nachrichtenseiten, z. B. Anime2You
4. X/weitere Social-Plattformen nur ergänzend

Crunchyroll News weist eigene RSS-Feeds aus. RSS bevorzugen, weil klein und stabil.

News nicht im 30-Sekunden-Takt laden. Sinnvolle Zeitpunkte:
- kurz vor Release
- zum Release
- +10 Minuten
- +30 Minuten
- +60 Minuten
- danach alle 30–60 Minuten

## 5. X.com
Optionales Modul. Nicht als Kernabhängigkeit planen.
- offizielle API kann Kosten und Limits besitzen
- öffentliche Seiten sind dynamisch und blockierbar
- nur offizielle Accounts als hohe Vertrauensstufe
- beliebige Nutzerposts höchstens als unbestätigten Hinweis darstellen

## Quellenregister
Siehe `schemas/source_registry.yaml`.
# Implementierungsstand v0.8.0

AniList ist die erste echte Quelle und liefert ausschließlich öffentliche Anime-Metadaten.
Die Android-App sendet einen begrenzten GraphQL-POST an `https://graphql.anilist.co`,
speichert erfolgreiche Antworten in Room und zeigt bei Netzwerkfehlern vorhandene Cache-
Daten weiter an. Die Live-Quelle ist im Debug-Build umschaltbar; Fake-Daten bleiben der
deterministische Standard.

Nicht angebunden sind AniSearch, Streaminganbieter, Newsquellen oder Playback-Endpunkte.
Die AniList-Anbindung darf nicht zur Verfügbarkeitsbestätigung eines deutschen Releases
verwendet werden.
