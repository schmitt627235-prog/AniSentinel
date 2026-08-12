# Implementierungsnotizen – Android-Prototyp

## Version 0.10.0

- Provider-Referenz und Verfügbarkeit getrennt persistiert (Room v4)
- Crunchyroll-Checker nutzt ausschließlich den öffentlichen Simulcast-Kalender
- keine Login-, Playback-, Manifest- oder DRM-Endpunkte
- positive Bestätigung verlangt Titel, erwartete Episode und sichtbares „Verfügbar“
- `firstAvailableAt` bleibt bei späteren positiven Prüfungen unverändert
- manuelle Prüfung ist ohne echte `ProviderReferenceEntity` nicht möglich
- AniSearch, automatische Zuordnung, WorkManager und Benachrichtigung bleiben externe/offene Punkte

## Version 0.9.1

- Status wird beim Lesen und Darstellen anhand einer injizierbaren `Clock` aufgelöst
- Terminüberschreitung bestätigt keine Anbieter-Verfügbarkeit
- Kalenderabfragen arbeiten mit lokalen Tagesgrenzen als Unix-Zeitfenster in Room
- Monatsmarkierungen entstehen ausschließlich aus gespeicherten echten Terminen
- AniList ist als `MetadataSource` mit direkter URL modelliert
- `ProviderAvailability` trennt Anbieter, Status, Episodennummer, URL und Prüfzeitpunkt
- 61 lokale Tests erfolgreich; Debug- und Android-Test-APK erfolgreich gebaut

## Version 0.9.0

- installierbare Anwendung auf echte AniList-Daten und ehrliche Leerzustände umgestellt
- Fake-Daten bleiben als Test-/Preview-Infrastruktur im Quellcode, sind aber nicht mehr
  über normale App-Routen erreichbar
- AniSearch-Website und offizieller API-Einstieg geprüft; der öffentliche Entwicklungsstand
  bietet keine Anime-Metadatenendpunkte, individuelle Schnittstellen werden über
  `api@anisearch.com` vereinbart
- kein undokumentiertes HTML-Scraping als Ersatz für die offizielle Schnittstelle

## Version 0.8.2

- widerspruchsfreie About-/Quellentexte
- eindeutige Benutzer-APK und getrennte Testartefakte
- AniSearch-Liveimport bewusst nicht vorgetäuscht: keine dokumentierte API und keine
  verlässlich abrufbaren Crawler-Regeln in der aktuellen Prüfungsumgebung

## Version 0.8.1

- nullable Releasezeitpunkte und getrennte Quell-/Cachezeitstempel
- Room v3 mit transaktionalem, geordnetem Trending-Snapshot
- ehrliche AniList-Detaildarstellung ohne abgeleitete Providerverfügbarkeit
- Live-/Demo-Trennung auf Home sowie expliziter Empty State
- Cache-TTL, `Retry-After`-Sperre und robuster Cover-Cache mit Downsampling
- lokaler Snapshot-Regressions- und instrumentierter Migrationstest
- Build, lokale Tests und Android-Test-APK erfolgreich; Live-TLS weiterhin nicht praktisch bestätigt

Stand: 30.07.2026

## Version 0.8.0

- öffentlicher GraphQL-POST an `https://graphql.anilist.co`
- kompakte Trending-Anfrage mit maximal zwölf nicht-erwachsenen Anime
- Verbindungs-/Lesezeitlimits, User-Agent und `Retry-After`-Erfassung
- DTO/Mapper behandeln fehlende Titel, Episodenzahl, Beschreibung, Cover und Airing-Daten
- AniList-IDs werden intern als `anilist:{id}` gespeichert
- Room v2 ergänzt `nextAiringAt` und `nextEpisode`; Migration 1→2 bleibt nicht destruktiv
- `CachedAniListRepository` schreibt erfolgreiche Antworten per `@Upsert`
- `HomeViewModel` zeigt Cache sofort und aktualisiert nur beim Aktivieren/manuellen Refresh
- Debug-Schalter hält den bisherigen Fake-Modus als deterministische Standardansicht
- externe Bilder werden ohne zusätzliche Build-Abhängigkeit geladen; Fehler nutzen `FakeCover`
- 55 lokale und 22 instrumentierte Tests erfolgreich
- Emulatorumgebung konnte den TLS-Aufbau zu AniList nicht abschließen; der instrumentierte
  Test bestätigt deshalb den realen Requestpfad und den lokalisierten Fehlerzustand.

## Version 0.7.8

- `MainActivity` besitzt den Activity-Result-Launcher für die Notification-Berechtigung
- erfolgreiche Erstfreigabe setzt die ursprünglich angeforderte Demo ohne zweiten Klick fort
- Ablehnung bleibt absturzfrei und bietet Wiederholung beziehungsweise Android-Einstellungen
- Notification-Präferenz und lokale Testnotification sind getrennte Settings-Aktionen
- `NotificationCoordinator` ist die einzige Produktionsverdrahtung von Settings, Engine und Dispatcher
- Kanalnamen werden mit der aktiven In-App-Sprache neu registriert
- Golden-Vergleiche brechen bei fehlender Referenz ab
- sechs Hero-Referenzen wurden korrekt benannt; About und 150-%-Hero ergänzen die Matrix
- About wird bei 150 % bis zum letzten Abschnitt gescrollt und geprüft
- 50 lokale und 21 instrumentierte Tests erfolgreich

## Version 0.7.7

- `AndroidNotificationDispatcher` bildet Domain-Kanäle auf vier echte Android-Kanäle ab
- Demo-Ereignis bleibt lokal, ist deutsch/englisch lokalisiert und öffnet die bestehende App
- Laufzeitberechtigung wird ab Android 13 nur bei Bedarf angefragt
- About-Version wird ausschließlich aus der Build-Konfiguration gelesen
- sechs Golden-Assets decken Phone/Tablet, Hell/Dunkel und Deutsch/Englisch ab
- Vergleich markiert Pixel mit einer Kanalabweichung über 32; maximal 2 % dürfen abweichen
- bei Fehlschlag wird zusätzlich ein magentafarbenes `_diff.png` geschrieben
- 150-%-Tests verwenden tatsächlich `Density(..., fontScale = 1.5f)`
- UI-Testzustand wird für Sprache, Theme, Favorit und Benachrichtigungen isoliert
- 50 lokale und 17 instrumentierte Tests erfolgreich

## Version 0.7.6

- erste zuvor deaktivierte Unterseite umgesetzt: „Über AniSentinel“
- About ist aus Einstellungen und Drawer erreichbar
- Version/Build, Status, Datenschutz, Fake-Daten, Produktgrenzen und Technik lokalisiert
- gespeicherter `languageTag` wählt reaktiv deutsche oder englische Notification-Kopie
- WatchProfile-Domain von deutschen Präsentationsnamen bereinigt
- instrumentierter Sprachtest prüft Englisch in Settings, Discover, Kalender und nach Recreation
- TalkBack-Coverbeschreibung wird im englischen UI-Test geprüft
- instrumentierter Favoritentest prüft Recreation und anschließende Navigation
- Screenshot-Smoke-Tests speichern PNGs für dunkles Smartphone und helles Tablet
- visuelle Prüfung der PNGs deckte eine falsche Hero-Content-Farbe auf und führte zur Korrektur

## Version 0.7.5

- optionaler `SectionHeader`: Aktionslabel nur zusammen mit echter Callback-Aktion
- Settings-Gruppen enthalten kein wirkungsloses „Alle anzeigen“ mehr
- Kalender lokalisiert Monat und Wochentage aus dem aktiven Gebietsschema
- Montag bleibt unabhängig von der Sprache erster Wochentag
- lokalisierte Genre-Chips und TalkBack-Coverbeschreibung
- injizierbare deutsche und englische Texte für die lokale Notification Engine
- konsistenter Status „Aktiv“ für Theme, Sprache und Watch-Profil
- stabile Navigations- und Scroll-Testtags
- Pixel-6-AVD mit Android 15/API 35 eingerichtet
- alle sieben instrumentierten Tests direkt über `AndroidJUnitRunner` bestanden

## Version 0.7.4

- Einstellungs-Dubletten und funktionslose Pfeile entfernt
- aktive Optionen und deaktivierte „Demnächst“-Bereiche klar getrennt
- Einstellungsgruppen: App, Überwachung und weitere Bereiche
- Benachrichtigungs- und Anbieteroptionen sind nicht länger scheinbar wirksam bedienbar
- Settings-, Detail-, Episoden-, Datums- und Watch-Profiltexte vollständig lokalisiert
- direkte Akzentfarben an kontrastkritischen Text-, Icon- und Statusstellen durch Theme-Rollen ersetzt
- Hauptinhalt wird erst nach dem ersten DataStore-Wert angezeigt
- UI-Regressionstest stellt sicher, dass zentrale Einstellungen exakt einmal erscheinen

## Version 0.7.3

- Root-`AppViewModel` beobachtet DataStore außerhalb einzelner Screens
- Hell-, Dunkel- und System-Theme werden global und unmittelbar angewendet
- gespeicherte Sprache erzeugt eine lokalisierte Compose-Konfiguration
- vollständige englische Ressourcen für die bestehenden String-Schlüssel
- sekundäre Drawer-Platzhalter sind nicht interaktiv und als „Demnächst“ markiert
- unfertige Benachrichtigungs- und Anbieteroptionen zeigen „In Vorbereitung“
- Screenshot-Smoke-Tests decken ein dunkles Smartphone- und ein helles Tabletlayout ab

## Version 0.5.0

- vollständige Repository-Verträge für Anime, Releases, Provider, News und Einstellungen
- Preferences DataStore für Theme, Sprache, Benachrichtigungen, Watch-Profil und Provider
- navigierbare, vollständig lokale Detailseite
- Fake-Provider mit deterministischen Available-/Unavailable-/Error-Ergebnissen
- Watcher Engine mit klarer Pipeline:
  `Scheduler → WatchProfile → ProviderCheck → StatusMachine → NotificationEvent`
- keine Requests, Parser oder echten Providerzugriffe

## Version 0.6.0

- lokale Notification Engine mit stabilen IDs, Kanälen und Nutzerpräferenzen
- zusätzliche Fake-Szenarien: verspätet, HTTP-Fehler, Wartung, mehrsprachig,
  regional eingeschränkt
- Compose-UI-Tests für Navigation, Hero, Detailseite, Einstellungen und Drawer
- Screenshot-Smoke-Tests für 360 dp und 800 dp
- Accessibility-Basis mit Überschriftensemantik, Inhaltsbeschreibungen und Test-Tags
- weiterhin keine echten Provider, Requests oder Android-Systembenachrichtigungen

## Version 0.7.0

- Detailseite über Hero-CTA und alle Anime-Karten sichtbar erreichbar
- Favoritenstatus wird über Room gespeichert
- Sprachfassung und Watch-Profil sind auf der Detailseite bedienbar
- Einstellungen schreiben Theme, Sprache, Benachrichtigungen, Watch-Profil und Provider
  unmittelbar in DataStore und zeigen den gespeicherten Wert
- Watcher verwaltet Schnell-, Ausgeglichen- und Sparsam-Profile mit Prioritäten
- automatische Profilwahl berücksichtigt Live-Bestätigung und Akkusparmodus
- Hero-Titel wird nicht mehr direkt in „Bald verfügbar“ dupliziert
- weiterhin ausschließlich Fake-Daten und keine Netzwerkanfragen

## Version 0.7.1

- eine Room-Flow-Wahrheit für Detail- und Favoritenansicht
- direkter `observeFavorite(animeId)`-Flow mit explizitem Ladezustand
- optimistische Favoritenumschaltung mit Rücknahme bei Schreibfehlern
- exakt ein Favoritendatensatz pro Anime; Deaktivierung statt Duplikat
- `createdAt` bleibt bei Sprach-/Profiländerungen unverändert
- Favoritenliste zeigt nur aktivierte Einträge und besitzt einen Leerzustand
- Tabs filtern tatsächlich nach aktuellem, geplantem und beendetem Status
- globales Standardprofil bleibt getrennt vom individuellen Favoritenprofil
- Sprache ist vor dem Favorisieren eine Vorauswahl und wird beim Favorisieren persistiert
- wirkungsloser Kalenderfilter sichtbar deaktiviert

## Version 0.7.2

- kritischen SQLite-`REPLACE`/`ON DELETE CASCADE`-Fehler behoben
- Eltern- und Konfigurationsdatensätze verwenden Room-`@Upsert`
- Detailöffnung fügt fehlende Fake-Metadaten ausschließlich per `INSERT IGNORE` ein
- vorhandene Anime-Datensätze werden beim Öffnen nicht aktualisiert
- Regressionstest aktualisiert Anime-Metadaten und bestätigt anschließend:
  Favorit vorhanden, aktiviert, genau einmal gelistet und Metadaten aktualisiert
- keine `REPLACE`-Verwendung mehr im Produktionscode

## Umgesetzt

- Gradle-Kotlin-DSL-Projekt mit Application-ID und Namespace `de.anisentinel.app`
- Android 8.0+ (`minSdk 26`), Compile/Target SDK 35, Java 17
- Jetpack Compose, Material 3 und Navigation Compose
- helles und dunkles Designsystem aus `docs/11_DESIGN_SYSTEM.md`:
  - dunkles Navy für Hintergrund und Oberflächen
  - Violett als Primary, Cyan als Secondary
  - semantische Farben für Erfolg, Warnung, Verspätung und Verschiebung
  - große Rundungen, Statuschips und klare Typografie
- Hauptnavigation:
  - Start
  - Kalender
  - Favoriten
  - Entdecken
  - Einstellungen
- Responsive Navigation:
  - Bottom Navigation auf kompakten Displays
  - Navigation Rail ab 720 dp Breite
- Hamburger-Drawer mit Haupt- und Sekundärbereichen
- ausschließlich lokale Fake-Daten
- unterscheidbare abstrakte, programmatisch erzeugte Platzhalter statt Anime-Bildern aus den Referenzen
- Hero-Karte mit dem nächsten Release und sekundengenauem Countdown
- Countdown aus festem `Instant` und aktueller Systemzeit ohne Drift
- lifecycle-sichere Aktualisierung nur im sichtbaren `STARTED`-Zustand
- adaptive Darstellung von Wochen bis Sekunden; Sekunden bleiben immer sichtbar
- automatischer Statuswechsel zu `CHECKING` am Nullpunkt
- Hero-Status und Watcher-Hinweis bleiben auf schmalen Geräten getrennt und horizontal lesbar
- Statuskacheln mit Symbolen und dezenten Farbverläufen
- pulsierender Sentinel-Avatar im Header
- alle produktrelevanten UI-Texte als Android-String-Ressourcen
- Domainmodelle für Anime, Watch-Profile, Watch-Phasen, Sprachpräferenz und Release-Status
- konservative Statusmaschine; Quellenfehler überschreiben den sichtbaren Status nicht
- vollständige lokale Room-Grundlage mit KSP, Schemaexport und App-Container:
  - `AnimeEntity`
  - `FavoriteEntity`
  - `EpisodeEntity`
  - `WatchProfileEntity`
  - `WatchPhaseEntity`
- DAO und abstrakte Datenbank
- lokales Favoriten-Repository
- `lastUnavailableAt` und `firstAvailableAt` im Episodenmodell vorbereitet
- Unit-Tests für Phasengrenzen, Mindestintervall, Profilende, Statusübergänge,
  Countdown-Grenzen und Zeitzonen
- Robolectric-/Room-Tests für Favoritenfilter, Verfügbarkeitsfenster und Watch-Phasen

## Bewusst nicht umgesetzt

- Login, Konto oder Abmelden
- echte AniList-, AniSearch-, Anbieter- oder News-Anfragen
- Streaming, Playback, Downloads oder DRM
- Hintergrundarbeit, WorkManager oder Foreground Service
- Übernahme der KI-generierten Anime-Cover als App-Assets

## Validierung

Ausgeführt:

```powershell
.\gradlew.bat --no-daemon assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin
```

Ergebnis: `BUILD SUCCESSFUL`, 50 lokale Tests erfolgreich. Zehn instrumentierte
UI-/Screenshot-Tests wurden auf einem Pixel-6-AVD mit Android 15/API 35 ausgeführt:
`OK (10 tests)`. Gradles UTP-Ergebnistransport startet in der Sandbox nicht zuverlässig;
deshalb wurden dieselben von Gradle erzeugten App- und Test-APKs per ADB installiert und
direkt mit `androidx.test.runner.AndroidJUnitRunner` vollständig ausgeführt.

## Offene Punkte für den nächsten Durchlauf

1. Vollständige Golden-Matrix, Pixel-Diff-Schwellen und einen CI-Emulator einrichten.
2. TalkBack auf einem realen bzw. emulierten Gerät systematisch prüfen.
3. Android-Benachrichtigungskanäle hinter die lokale Notification Engine setzen.
4. Lokale Anime-, Release- und News-Repositories ergänzen.
5. Deaktivierte Drawer-Unterseiten implementieren.

## Technischer Hinweis

Das Projekt ist Kotlin-only. Room erzeugt seine Implementierung über KSP als Kotlin-Code;
Java-Kompilierungsaufgaben bleiben daher deaktiviert.
