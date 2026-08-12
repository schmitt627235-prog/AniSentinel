# Implementierungsroadmap

## Phase 0 – Projektgrundlage
- Android-Projekt erstellen
- Compose, Hilt, Room, DataStore, Navigation
- CI: build, lint, unit tests
- Designsystem und String-Ressourcen

## Phase 1 – klickbarer lokaler Prototyp
- Start, Kalender, Favoriten, Entdecken, Einstellungen
- Hamburger-Menü
- Detailseite
- Avatar-Auswahl + eigenes lokales Bild
- ausschließlich Fake-Daten

## Phase 2 – Domain und Datenbank
- Entities, DAO, Repositories
- Favorisieren
- Watch-Profile und Phasen
- Prüfverlauf
- lokale Benachrichtigungen

## Phase 3 – Release-Wächter mit Fake-Provider
- Scheduler
- Foreground Live-Watcher
- Zustandsmaschine
- Tests für Intervalle und Übergänge

## Phase 4 – AniList
- Suche
- Metadatenimport
- Cover/Details
- Caching

## Phase 5 – deutsche Releasequelle
- AniSearch-Prototyp nur hinter Feature Flag
- Parser-Fixtures
- konservatives Rate Limit
- manuelle Korrekturmöglichkeit

## Phase 6 – Crunchyroll-Checker
- Serien-/Episoden-Mapping
- DE/Sprachen
- Release-Kalender/öffentliche Metadaten
- Error-/Rate-Limit-Handling

## Phase 7 – ADN
- gleiche Plugin-Struktur
- Metadaten und Freigabestatus getrennt

## Phase 8 – News
- Crunchyroll News RSS
- Anime2You/weitere erlaubte Quellen
- Matcher und Vertrauensstufen

## Phase 9 – Stabilisierung
- Akku-/Datentests
- Accessibility
- Datenschutzseite
- Export/Import
- Beta mit kleinen Nutzerzahlen

## Nicht im MVP
- Server-Synchronisierung
- Communitykonten
- Serien/Filme außerhalb Anime
- X-API als Kernfunktion
- Netflix/Disney+/Prime-Minutenmonitoring
