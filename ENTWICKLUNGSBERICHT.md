# AniSentinel – Entwicklungs-, Änderungs- und Validierungsbericht

Alle bisherigen Einzelberichte sind hier zusammengeführt. Der neueste Stand steht oben, der älteste unten.

README.md, CHANGELOG.md und SOURCES.md bleiben als eigenständige Projektdokumente bestehen.

---

## AniSentinel v0.25.16 Build 21 – AKIBA PASS und Apple-TV-Grenze

### Anlass und Umsetzung

- Die bisherige Providerliste sollte um AKIBA PASS und Apple TV erweitert werden. AKIBA PASS bietet öffentliche Produktseiten mit einzelnen Staffeln, Episoden und Sprachfassungen. Ein Listeneintrag allein beweist noch keine tatsächliche Buchbarkeit.
- Der AKIBA-PASS-Adapter gleicht Titel und Staffel konservativ ab, liest nur öffentliche Katalogseiten und importiert ausschließlich bestätigte buchbare, bereits begonnene Staffeln. Episoden erhalten direkte Anbieterlinks; Beschreibung und Laufzeit werden optional gespeichert. Die neue Room-Migration 28→29 erhält bestehende Daten.
- Eine gemeinsame Client-Instanz mit Mutex und sechsstündigem Cache vermeidet parallele Wiederholungen. Der User-Agent nennt AniSentinel und seine Version; Login, Checkout, Playback und geschützte Endpunkte werden nicht abgefragt.
- Die öffentliche Apple-TV-Conan-Seite nennt zwar die Staffeln 1–5, 8–12, 30 und 31, liefert im initialen HTML aber nur sechs konkrete Episodenkarten. Die öffentliche iTunes-Suche lieferte für Conan keine vollständige Episodenliste. Ohne belastbaren rechtmäßigen Katalogzugang wird Apple TV nicht als vollständig integrierter Episodenanbieter ausgegeben. Die geplante Conan-Zusammenführung mit beiden Crunchyroll-Katalogen bleibt offen.

### Validierung und Veröffentlichung

- Acht gezielte AKIBA-PASS-Unit-Tests und ein manueller Android-Room-Instrumentierungstest für Migration 28→29 bestanden. Öffentliche AKIBA-PASS-Seiten wurden als Einzelsnapshots geprüft; die Parser unterscheiden buchbar von nur gelistet.
- Debug-APK erfolgreich gebaut und mit dem dauerhaft lokal gesicherten Debug-Schlüssel signiert. Die Updateinstallation per `adb install -r` und der App-Start auf `R3CX4056Q8L` waren erfolgreich. Bei der vorherigen, ausdrücklich genehmigten Neuinstallation gingen die damaligen App-Daten verloren; das vom Nutzer erstellte Backup wird von ihm selbst wiederhergestellt.
- Für Build 21 wird der Versionscode 67 verwendet. Der Signaturschlüssel liegt außerhalb des Repositories und wird nicht hochgeladen. Apple TV ist ausdrücklich noch nicht fertig; kein Teilstatus wird als volle Episodenabdeckung dargestellt.

---

## AniSentinel v0.25.16 Build 19 – Future-News und staffelgenaue Anime2You-Zuordnung

### Anlass

- Auf Detailseiten erwarteter Titel erschienen teils thematisch verwandte, für die konkrete Veröffentlichung aber irrelevante Anime2You-Beiträge: Autoren-Aussagen, Kooperationen, redaktionelle Rückblicke oder Meldungen zur ersten Staffel einer angezeigten Fortsetzung.
- Gleichzeitig sollten echte Anbieter-/Lizenzbestätigungen nicht verloren gehen und die Detailseite beim Öffnen ohne zusätzlichen manuellen Refresh aktuelle Meldungen laden.

### Umsetzung und Begründung

- Die Future-Detailseite stößt die titelbezogene Anime2You-Aktualisierung beim Öffnen automatisch an. Pull-to-Refresh bleibt als bewusster erneuter Abruf erhalten.
- Für Future-Detailseiten gilt global eine positive Whitelist. Zugelassen sind nur Trailer/Teaser, konkrete Starttermine, Verschiebungen/Pausen/neue Termine, Disc-Releases sowie konkrete Anbieter- oder Lizenzmeldungen.
- Anbieter-/Lizenzmeldungen erkennen unter anderem Providername zusammen mit `Simulcast`, `weltweit`, `auf Abruf`, `Lizenz`, `Programm`, `Katalog` oder `exklusiv`. Eine bloße Providernennung im Fließtext reicht nicht.
- Disc-Signale umfassen DVD, Blu-ray, Disc, Steelbook, Volume, Komplettbox, Collector's Edition und Releaseplan.
- Kooperationen, Kollaborationen, Interviews, Versprechen von Autoren, redaktionelle Wertungen, Merchandising, Figuren, CDs, Soundtracks, Rankings und ähnliche Inhalte werden nur auf Future-Detailseiten verworfen. Der allgemeine News-Bereich behält seine breitere Nachrichtenauswahl.
- Nennt der AniList-Titel eine konkrete Staffel oder Fortsetzung, muss auch der Anime2You-Artikel genau diese Staffel bezeichnen. Eine unnummerierte Franchise-Meldung wird nicht vorschnell einer Fortsetzung zugewiesen. Titel ohne Staffel-/Fortsetzungsangabe benötigen dagegen weiterhin keine künstliche Staffelnummer.
- Titelvarianten wurden generisch erweitert: englische, Romaji-, native und synonyme Namen, deutsche JustWatch-Namen, ausgeschriebene und numerische Staffelvarianten, `Dai San Maku`, kurze Franchise-Aliase sowie `Russiya-go`/`Russia-go` werden abgeglichen.
- Alte AniSearch-DACH-Cachedaten werden nicht mehr in den Future-Titelstamm zurückgemischt. Dadurch kann ein früherer Fremdtreffer einen AniList-Titel nicht mehr umbenennen. JustWatch-Anreicherung akzeptiert ebenfalls nur konservativ passende Titel.

### Validierung

- Gezielter Testlauf `Anime2YouNewsParserTest`: bestanden.
- Abgedeckte Regressionen: Witch Hat Atelier Staffel 2 gegen Beiträge zur ersten Staffel, Trailer/Teaser, Anbieter-/Lizenzmeldungen, Kooperationen, Autoren-Aussagen, Disc-Signale und exakter Staffelabgleich.
- `assembleDebug`: erfolgreich.
- ADB-Gerät `R3CX4056Q8L`: als `device` erkannt.
- Der zuvor inhaltlich identische Stand wurde erfolgreich per `adb install -r` installiert und gestartet. Nach der Versionscode-Anhebung auf Build 19 lehnte Android die abschließende Updateinstallation wegen einer abweichenden lokalen Debug-Signatur mit `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ab.
- Es wurde bewusst weder deinstalliert noch wurden App-Daten gelöscht. Die Build-19-APK bleibt für eine Neuinstallation verwendbar; für ein datenerhaltendes Update ist derselbe Signaturschlüssel wie bei der installierten Debug-App erforderlich.

---

## AniSentinel v0.25.16 Build 18 – aktueller Abschlussstand

- **Season-Favoriten-Hotfix vom 11.09.2026:** Die Katalogtabelle erzwingt eine eindeutige Position je Katalog, beim Favorisieren eines erwarteten Titels wurde jedoch immer Position `0` verwendet. Deshalb war in „Favoriten → Season“ nur ein Eintrag sichtbar. AniSentinel baut die Zuordnung nun aus allen aktiven AniList-Favoriten mit ihren tatsächlichen Listenpositionen vollständig neu auf; das repariert auch vorhandene Bestände beim nächsten Laden. Ein Room-Regressionstest mit 120 Einträgen bestand. Debug-APK und gezielter Testlauf waren erfolgreich, das Update wurde per `adb install -r` installiert und ohne Datenlöschung gestartet.
- „Heiß erwartete Titel“ nutzt weiterhin AniLists offizielle GraphQL-API mit `NOT_YET_RELEASED` und `POPULARITY_DESC`. Der reale Gerätetest vom 11.09.2026 lieferte für Seite 1 HTTP 403 mit AniLists eigener Meldung, die öffentliche API sei wegen schwerer Stabilitätsprobleme vorübergehend deaktiviert.
- Damit Neuinstallationen und GitHub-Builds während dieser externen Störung nicht `Alle (0)` anzeigen, enthält die APK den letzten erfolgreich geprüften AniList-Grundbestand: 699 empfangene Rohdatensätze, davon 694 nach dem bestehenden Future-Filter sichtbar. Ein erfolgreicher Liveabruf überschreibt den lokalen Cache weiterhin regulär.
- Der AniList-Bestand wurde als eigener, auswählbarer Backupbereich ergänzt. Alte Schema-1-Backups ohne diesen Bereich bleiben lesbar.
- Der nach einer Neuinstallation reproduzierte Restore-Absturz war eine Room-Fremdschlüsselverletzung: Favoriten wurden importiert, obwohl der referenzierte Anime-Elterndatensatz noch nicht erneut geladen war. Der Restore legt nur bei fehlendem Elternsatz einen minimalen Platzhalter an und speichert danach den Favoriten; bestehende vollständige Anime werden nicht überschrieben.
- Anime2You-News werden nun pro Titel über `https://www.anime2you.de/?s=<Titelvariante>` gesucht. Englische, Romaji-, native, synonyme und alternative Titel werden normalisiert, seriell geprüft, anhand kanonischer News-URLs zusammengeführt und mit Ablehnungsgründen protokolliert. Negative oder technisch fehlgeschlagene Ergebnisse werden nicht als gültiger Negativcache behandelt.
- AniSearch wurde aus dem regulären automatischen Produktivabruf genommen. Beim kontrollierten Gerätetest antwortete bereits der erste echte Inhaltsrequest mit HTTP 429 ohne `Retry-After`; AniSentinel setzte daraufhin 1.800 Sekunden globalen Cooldown und unterband Folgezugriffe. Da für Anime-Metadaten keine allgemein freigegebene offizielle API vorliegt, werden weder Rate-Limit noch Bot-Schutz umgangen. Sobald ein zulässiger projektspezifischer AniSearch-API-Zugang vorliegt, kann der erhaltene Parser-, Matching-, Cache- und DACH-Auswertungspfad wieder aktiviert werden.
- Gezielte Tests für Backup/Restore, AniList-Future-Daten und Anime2You bestanden. `assembleDebug` war erfolgreich; die Debug-APK wurde per `adb install -r` auf dem Samsung SM-S928B installiert. Ein simulierter Erststart ohne privaten AniList-Cache zeigte auf dem Gerät `Alle (694)`.

---

## AniSentinel v0.25.16 Build 17 – AniList → AniSearch → DACH

### Ausgangslage und weiterverwendete Komponenten

- Ausgangsstand war Build 16 (`versionCode 62`); Zielstand ist Build 17 (`versionCode 63`) bei unverändertem `versionName 0.25.16`.
- Weiterverwendet wurden AniLists paginierte `NOT_YET_RELEASED`-Abfrage, Popularity-Sortierung, Future-Datenmodell, 24-Stunden-AniList-Cache, Compose-Liste, Detailseite, Pull-to-Refresh, der kontrollierte AniSearch-HTTP-Transport und der vorhandene HTML-Parser.
- Start, Kalender, Favoriten, Entdecken, Einstellungen, Backup/Restore, Notifications, Providerchecks, ReleaseDisplayResolver und AniWorld-Fallback wurden nicht umgebaut.

### Ergänzte Verbindung

- `AniSearchFutureMatcher` erzeugt Suchvarianten aus English, Romaji, Native und sämtlichen AniList-Synonymen. `2nd Season`, `Season 2`, `Staffel 2`, `Dai 2 Ki`, `第2期`, `Part` und `Cour` werden als generische Identitätsmerkmale behandelt und nicht titelbezogen hart codiert.
- Suchergebnisse werden gesammelt und konservativ bewertet. Explizite Staffel-/Cour-Widersprüche werden ausgeschlossen; ein Mindestwert und ein Abstand zum zweitbesten Kandidaten verhindern erzwungene Mehrdeutigkeitsmatches.
- Sichere Matches werden AniList-ID-bezogen gecacht. Beim nächsten Aufruf wird die bekannte AniSearch-Detail-URL direkt geladen, sodass keine erneute Titelsuche nötig ist.
- Detailmetadaten werden zusätzlich auf grobe Jahres- und Formatwidersprüche geprüft. Bei Widerspruch bleibt der Status `DACH_UNKNOWN`.
- Die DACH-Auswertung akzeptiert ausschließlich `ul.xlist.row.simple.infoblock`. Die deutsche Flagge muss innerhalb eines solchen Blocks liegen. Publisher und `Veröffentlicht` stammen aus demselben `<li>`.
- Ein erfolgreich ausgewerteter regionaler Block ohne deutschen Eintrag ergibt `DACH_NOT_LICENSED_YET`. Fehlender Block, unsicherer Match, HTTP-Fehler, Sperre oder Parserfehler ergeben `DACH_UNKNOWN`; gültige Cachewerte werden dabei nicht gelöscht.
- Ein globaler Mutex erlaubt maximal eine aktive AniSearch-Anfrage. Nach HTTP 429 wird `Retry-After` übernommen oder ein exponentieller Backoff von zunächst 30 Minuten bis maximal 24 Stunden gesetzt und im vorhandenen `SourceCooldownStore` persistiert. Netzwerkfehler starten keine weiteren Aliasrequests; Varianten werden nur nach einer erfolgreichen, aber ergebnislosen Suchantwort fortgesetzt.
- `dachAvailableFrom`, `sourceObservedAt` und `firstDetectedAt` werden getrennt persistiert. Das Prüfdatum wird nicht als deutscher Verfügbarkeitsbeginn ausgegeben.

### Referenz und Lizenz

- Als technische Referenz wurde `ipkpjersi/modb-app` geprüft, insbesondere die Trennung aus Konfiguration/Crawler/Konverter sowie der XPath `//ul[@class='xlist row simple infoblock']` und JSON-LD/DOM-Fallbacks.
- Das Referenzprojekt steht unter AGPL-3.0. Es wurde kein Quellcode kopiert; AniSentinel verwendet eine eigenständige Jsoup-Implementierung und dokumentiert lediglich die übernommenen Parserkonzepte.

### Validierung

- Neue Tests decken Suchvariantenerzeugung, `Re:Monster 2nd Season` ↔ `Re:Monster Dai 2 Ki`, falsche Staffeln, mehrdeutige Treffer, deutschen Eintrag im regionalen Block, fehlenden Publisher, Flagge außerhalb des Blocks und einen vorhandenen Block ohne deutschen Eintrag ab.
- Fokussierte Parser-/Future-Tests: erfolgreich.
- Vollständige Suite: 355 Unit-/Robolectric-Tests, 0 Fehler, 0 übersprungen.
- `assembleDebug` erfolgreich; Installation als Update auf Samsung SM-S928B erfolgreich; Gerät bestätigt `versionName 0.25.16`, `versionCode 63`.
- Gestufte Liveprüfung begann regelkonform mit drei sichtbaren Titeln. Ein gültiger Apothekerin-Beleg kam aus Cache; AniSearch antwortete anschließend real mit HTTP 429 ohne `Retry-After`. Der globale Backoff wurde auf 1.800 Sekunden gesetzt und weitere Requests wurden als Cooldown-Treffer unterdrückt. Der Status blieb `DACH_UNKNOWN`; kein Negativstatus wurde erzeugt.
- Wegen des realen 429 wurden die 5er- und 20er-Netzwerkstufen bewusst nicht gestartet. Die 20 dynamischen AniList-Kandidaten und der Abbruchgrund sind in `BUILD17_LIVE_STICHPROBE.md` dokumentiert. Dieses ehrliche Abbruchergebnis ersetzt keine erfolgreiche 20-Titel-Matchquote.
- GitHub wurde nicht verändert.

---

## AniSentinel v0.25.16 – Heiß erwartete Titel

### Umsetzung und Begründung

- Der bisher deaktivierte Drawer-Eintrag besitzt nun die Route `anticipated`; „Demnächst“ entfällt.
- `AnticipatedTitlesRepository` ruft AniLists dokumentierte GraphQL-API mit `type: ANIME`, `status: NOT_YET_RELEASED` und `sort: POPULARITY_DESC` ab. Dadurch beruht der Rang auf echtem Nutzerinteresse und nicht auf einer erfundenen Formel.
- Ein zusätzlicher lokaler Filter verwirft alle Datensätze, deren Status nicht `NOT_YET_RELEASED` ist, sowie bekannte Startdaten, die nicht mehr in der Zukunft liegen.
- Der 24-Stunden-Cache verhindert aggressive Abfragen. Schlägt ein Refresh fehl, wird der letzte gültige Datensatz weiter angezeigt und nicht gelöscht.
- `UpcomingAnimeIdentity` hält AniList-/MAL-IDs, Aliase, Vorgänger und Staffelnummer getrennt. Die generische Aliasnormalisierung erkennt Schreibweisen wie `2nd Season`, `Dai 2 Ki` und `第2期`, ohne Titel-Hardcodes.
- Liste und Detailseite zeigen reale Cover, Popularity, geplanten Start, Format, Studio und Fortsetzungsbezug. Saisonfilter werden ausschließlich aus vorhandenen Daten erzeugt.
- DACH-Status, tatsächlicher Verfügbarkeitsbeginn, Beobachtungszeit und erster Erkennungszeitpunkt sind getrennt. Höher priorisierte spätere Bestätigungen ersetzen den vorherigen sichtbaren Negativstatus.
- AniSearch wird nicht als offizielle API behandelt: öffentliche Such-/Detailseiten werden mit User-Agent, Cache, Rate-Limit und ehrlichem Abbruch bei Sperren ausgewertet. Ein verifizierter echter Beleg für „Die Tagebücher der Apothekerin: Staffel 3 – Cour 1“ dient als quelloffene Fixture; die Zuordnung erfolgt dennoch ausschließlich über die generische Alias-/Staffelidentität. Weitere Titel werden begrenzt im Hintergrund beziehungsweise beim Öffnen angereichert. AnimeSchedule blieb mangels verifiziertem zulässigem Abrufweg deaktiviert.
- Die bestehende Release-, Kalender-, Provider-, Verschiebungs-, Benachrichtigungs- und Einstellungslogik wurde für dieses Feature nicht umgebaut.

### Tests und Geräteprüfung

- 348 Unit-/Robolectric-Tests erfolgreich, darunter Future-Filter, Popularity-Sortierung, Aliasabgleich, DACH-Negativstatus, spätere DACH-Bestätigung und Trennung von Verfügbarkeits- und Erkennungsdatum.
- `assembleDebug` erfolgreich; Paketversion v0.25.16, Build 62.
- Installation als Update auf dem Samsung SM-S928B erfolgreich, vorhandene App-Daten blieben bestehen.
- Navigationspfad real geprüft: Drawer → Heiß erwartete Titel → reale Liste → The Apothecary Diaries Season 3 → Detailseite → Zurück → Liste.
- Reale Liste zeigte u. a. dynamische Filter, Popularitätswerte, zukünftige Startdaten und den ehrlichen DACH-Status; Detailseite zeigte Format, Studio und Vorgängerbezug.
- GitHub wurde nicht verändert.

---

## AniSentinel v0.25.15 – Korrektur Backup und Datenschutz

- Ursache des wirkungslosen Backup-Buttons behoben: Ein Compose-Context-Wrapper wird nun sicher bis zur tatsächlichen `MainActivity` aufgelöst.
- Export und Import öffnen real Androids Storage Access Framework über `CreateDocument(application/json)` und `OpenDocument`.
- Sieben einzeln auswählbare Bereiche: Favoriten, allgemeine Einstellungen, Benachrichtigungen, Kalender, Anbieter, Watch-Profil sowie Theme/Sprache.
- „Alles auswählen“ und „Alles abwählen“ für Export und Restore; ohne Auswahl bleibt die jeweilige Hauptaktion deaktiviert.
- Neues Schema 1 enthält `appVersion`, `includedSections` und strikt getrennte Datenobjekte. Technische Caches und Gerätedaten fehlen vollständig.
- Restore liest und validiert die gesamte Datei, zeigt danach eine Auswahlvorschau und verändert nur bestätigte Bereiche. Favoriten werden zusammengeführt.
- Android-App-Info wird über einen auflösbaren `ACTION_APPLICATION_DETAILS_SETTINGS`-Intent geöffnet; Fehler werden abgefangen. Der Berechtigungsstatus wird bei Rückkehr neu gelesen.
- Redundante „Aktiv“-Zusätze wurden bei bedienbaren Einstellungskarten entfernt; informative Zustände bleiben bestehen.

### Tests und reales Gerät

- 343 Unit-/Robolectric-Tests erfolgreich, keine Fehler.
- `assembleDebug` erfolgreich; Ziel bleibt v0.25.15/Build 61.
- Reales JSON-Backup im Android-Dateidialog gespeichert: 12.965 Byte, Schema 1, alle sieben erwarteten Bereiche, keine Cache-/Gerätedaten.
- Restore-Dateidialog, Inhaltsvorschau, Alles an/ab und vollständiger Restore erfolgreich geprüft.
- Datenschutz-Löschdialog: Abbrechen und Bestätigen geprüft. Aktive Favoriten wechselten kontrolliert von 37 auf 0 und wurden unmittelbar aus dem geprüften Backup auf 37 wiederhergestellt.
- Android-App-Info geöffnet; Rückkehr zu AniSentinel ohne Absturz erfolgreich.
- Anbieterfilter unverändert gelassen.
- GitHub nicht verändert.

---

## AniSentinel v0.25.15 – vier Einstellungsbereiche (lokaler Prüfstand)

### Auftrag und Scope

Ausgehend von v0.25.14/Build 60 wurden ausschließlich die vier bereits vorhandenen Bereiche unter **Einstellungen** produktiv angebunden: Kalender, Sync & Backup, Datenschutz und Anbieter. Releaseauflösung, Staffel-/Arc-Logik, Verschiebungen, T+10-Fallback und direkte Providerparser wurden nicht umgebaut.

### Kalender

- Persistente DataStore-Schalter für OmU/Deutsch untertitelt, deutsche Synchro, vergangene Termine und „Nur Favoriten“.
- Mindestens eine Sprachfassung bleibt immer aktiv.
- Die Filter verändern nur sichtbare Kalenderdaten und löschen keine Room-Einträge.
- Sprach-, Favoriten- und globaler Anbieterfilter werden gemeinsam angewendet.

### Anbieter

- Globale Schalter für Crunchyroll, ADN, Netflix, Disney+ und aniverse.
- Neue zentrale `ProviderVisibilityPolicy` normalisiert Provider-Aliase, ermittelt aktive Anbieter und filtert Titel sowie sichtbare Providerangaben konsistent.
- Home, Kalender, Favoriten, Entdecken und Suche verwenden dieselbe Policy.
- `disabledProviderIds = emptySet()` bedeutet rückwärtskompatibel „alle aktiv“; die bisherige Anbieterpräferenz bleibt fachlich getrennt.
- Ausschalten löscht weder Favoriten noch Provider-/Release-Daten. Bei Wiedereinschalten werden passende Daten wieder sichtbar.
- Availability-Benachrichtigungen respektieren die globale Sichtbarkeit, ohne die direkte Prüfarchitektur zu verändern.

### Sync & Backup

- Versioniertes JSON-Schema 1 mit App-Kennung, Erstellungszeit, Nutzerpräferenzen und Favoriten.
- Export über `ACTION_CREATE_DOCUMENT`, Import über `ACTION_OPEN_DOCUMENT`; keine Speicherberechtigung erforderlich.
- Restore validiert zuerst das vollständige Dokument. Erst danach werden Einstellungen ersetzt und Favoriten konfliktfrei zusammengeführt.
- Ungültiges JSON, falsches Schema oder ungültige Pflichtwerte verursachen keinen Teilimport.
- Provider-/HTTP-Caches, Parserantworten, Diagnosezustände und externe Kalenderdaten werden nicht exportiert.

### Datenschutz

- Transparente Übersicht über lokal gespeicherte Favoriten, Einstellungen und wiederbeschaffbare Release-/Providerdaten.
- Dynamischer Status der Android-Benachrichtigungsberechtigung und Link in die System-App-Einstellungen.
- Bestätigungspflichtige Löschung von Favoriten, Einstellungen und Benachrichtigungs-/Deduplizierungszuständen; wiederbeschaffbare Release- und Providerdaten bleiben erhalten.
- Hinweis auf ein lokales Backup vor der Löschung.

### Sicherheit und Datenschutz

- Backup-Dateinamen werden zusätzlich durch `.gitignore` ausgeschlossen.
- Reale Datenbanken, WAL/SHM, ADB-Dumps, Nutzersicherungen und private Medien werden weder Bestandteil des späteren Quellpakets noch einer Veröffentlichung.
- GitHub wurde in diesem Arbeitsdurchlauf bislang nicht verändert; die vorgeschriebene neue Freigabe steht noch aus.

### Validierung

- Zielstand: `versionName 0.25.15`, `versionCode 61`.
- `compileDebugKotlin`: erfolgreich.
- `testDebugUnitTest`: 341 Tests, 341 erfolgreich, 0 Fehler, 0 übersprungen.
- `assembleDebug`: erfolgreich.
- Neue Regressionstests decken Defaultmigration, Einzel-/Mehranbieterfilter, Aliasnormalisierung, Backupinhalt und atomaren Fehlerfall ab.
- Reales Gerät: Samsung SM-S928B, Updateinstallation mit `adb install -r` erfolgreich; vorhandene App-Daten wurden nicht gelöscht.
- Installierte Paketversion über Android bestätigt: `versionName=0.25.15`, `versionCode=61`.
- Kalender, Sync & Backup, Datenschutz und Anbieter wurden über die reale Compose-Oberfläche geöffnet und inhaltlich geprüft.
- Providerpersistenz: Netflix testweise deaktiviert, App vollständig beendet und neu gestartet; Zustand blieb deaktiviert. Anschließend wurde Netflix wieder aktiviert, sodass alle fünf Anbieter wie vor dem Test aktiv sind.
- Die vor der Prüfung vorhandenen 37 Favoriten blieben beim Update und beim Providerfiltertest erhalten; die Filteraktion enthält keine Favoritenmutation.

### Auswirkungen

Die App erhält vier nutzbare lokale Einstellungsbereiche, ohne den stabilen Release- und Providerkern zu verändern. Nutzer können ihre sichtbaren Inhalte einschränken, den Zustand portabel sichern, lokale Daten transparent verwalten und behalten dabei alle gespeicherten Favoriten und wiederbeschaffbaren Daten.

---

## Ursprünglicher Bericht: V0.25.14_FOLGEPRUEFUNG_2026-08-24.md

# AniSentinel v0.25.14 – lokale Folgeprüfung

## Grundlage

- Verbindliche Basis: AniSentinel `0.25.14`, VersionCode `60`.
- Ausgewertet wurde `AniSentinel_v0.25.14_Pruefbericht_fuer_Codex.md` vom 24.08.2026.
- Bereits in v0.25.14 vorhandene Lösungen wurden nicht erneut implementiert.

## Lokale Änderungen

### Dauerhafte Veröffentlichungsregel

`AGENTS.md` verlangt nun ausdrücklich für jeden neuen Arbeitsdurchlauf eine neue Nutzerfreigabe vor Commit, Push, Remote-Branch, Pull Request, Tag, GitHub-Release oder Asset-Upload. Frühere Freigaben gelten nur für den damals konkret geprüften Stand.

Die Datenschutz-Ausschlüsse für Room-Datenbanken, WAL/SHM, ADB-Dumps, App-Datensicherungen, Cookies, Tokens, Secrets, Browserprofile, private Medien, `local.properties` und Build-Caches sind dort ebenfalls festgeschrieben.

### Zusätzlicher globaler Regressionstest

`ReleaseDisplayResolverTest` prüft nun mehrere gleichzeitig aktive Verschiebungen desselben Anime:

- S4E17 Deutsche Synchro behält ihren eigenen neuen Termin.
- S4E18 Deutsche Synchro behält ihren eigenen neuen Termin.
- S4E20 OmU/Deutsch untertitelt behält ihren eigenen neuen Termin.
- Der kanonische Hauptrelease bleibt S4E20 GER_SUB.
- Sein Countdown verwendet ausschließlich den Termin von S4E20.
- Die Verschiebungen anderer Episoden und Sprachtracks werden nicht vermischt.

Die bisher auf einen Bookworm-artigen Staffel-3-Test zugeschnittene Postponement-Testhilfe akzeptiert dafür nun eine explizite Staffel. Das ist ausschließlich Testinfrastruktur und keine titelbezogene Produktionslogik.

## Validierung

- `testDebugUnitTest`: 334 Tests, 334 erfolgreich, 0 Fehler.
- `assembleDebug`: erfolgreich.
- Produktionscode und Appverhalten wurden in diesem Folgeprüfschritt nicht verändert.
- Keine Geräteinstallation war erforderlich, da die erzeugte App funktional weiterhin v0.25.14 Build 60 entspricht.

## Unverändert erhalten

- zentraler `ReleaseDisplayResolver` und `ReleaseDisplayState`;
- identischer kanonischer Zustand für Home, Favoriten und Detail;
- Sub-/Dub-Trennung;
- kompakte Verschiebungskarte;
- AniWorld/Anime2You als gemeinsame Herkunft;
- Disc-/Blu-ray-Ausschluss;
- Due-Notification ausschließlich optional für Favoriten;
- Verschiebungsnotification und Deduplizierung;
- T+10-AniWorld-Fallback;
- getrennte Verfügbarkeitsbelege.

## GitHub-Status

GitHub wurde in diesem Arbeitsdurchlauf nicht verändert. Es wurden weder Commit noch Push, Tag, Release oder Asset-Upload ausgeführt.

## Für eine spätere Veröffentlichung vorgesehen

- `AGENTS.md`
- `app/src/test/java/de/anisentinel/app/ui/ReleaseDisplayResolverTest.kt`
- `V0.25.14_FOLGEPRUEFUNG_2026-08-24.md`

Da kein Produktionscode geändert wurde, wurde noch kein neuer App-Versionsstand erzeugt. Ein Versionssprung soll erst zusammen mit einer tatsächlichen Produktänderung oder nach ausdrücklicher Nutzerentscheidung erfolgen.

---

## Ursprünglicher Bericht: V0.25.14_LOKALER_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.14 – lokaler Änderungs- und Validierungsbericht

## Status

- Geplanter Versionsstand: `0.25.14`
- VersionCode: `60`
- Der auf dem Gerät geprüfte Stand wurde vom Nutzer zur Veröffentlichung freigegeben.

## Behobene Fehler

### Einheitliche Releaseauflösung

- `ReleaseDisplayState` als zentrale Renderstruktur eingeführt.
- `ReleaseDisplayResolver.nextFor()` bestimmt Releaseidentität, Episode, Sprachfassung, ursprünglichen Termin, effektiven Termin, Verschiebung und Countdownziel gemeinsam.
- `ReleaseDisplayResolver.effectiveReleases()` wendet Verschiebungen semantisch nach Anime, Staffel, Episode und Sprache an.
- Home, Favoriten und Detailseite verwenden denselben Resolver.
- Eine Karte kann deshalb nicht mehr eine Verschiebung von Episode X mit Episode oder Countdown von Y kombinieren.

### Bookworm-Regression

Der reale Gerätezustand enthält weiterhin ältere Zeilen, aber die UI zeigt nun konsistent:

- Startseite: S3, Folge 20, neuer Termin 05.09.2026 11:30, Countdown auf diesen Termin.
- Favoriten: derselbe Zustand.
- Detailseite: derselbe nächste Release; letzter Release S3E19.
- Die alte E17-Zeile verdrängt E20 nicht mehr.

### Sub-/Dub-Trennung

- Eine separate Dub-Verschiebung bleibt ein eigener Fakt.
- Sie wird weder auf die nächste Sub-Episode umnummeriert noch als deren Countdownziel verwendet.
- Staffel, Folge und verständliche Sprachbezeichnung bleiben Bestandteil des Hinweises.

### Kompakte Verschiebungskarte

- Auf Detailseiten wird der bereits bekannte Anime-Titel nicht wiederholt.
- Technische Werte `GER_SUB`/`GER_DUB` werden als „OmU / Deutsch untertitelt“ beziehungsweise „Deutsche Synchro“ dargestellt.
- Alter und neuer Termin stehen kompakt in einer Zeile mit Pfeil.
- Grund und Quellenherkunft bleiben sichtbar, sofern vorhanden.
- Technische Diagnoseinformationen bleiben aus der Standardkarte entfernt.

### AniWorld-/Anime2You-Herkunft

- Eine passende Anime2You-Meldung wird nicht mehr als unabhängige zweite Bestätigung gezählt, wenn AniWorld dieselbe redaktionelle Herkunft weitergibt.
- Gespeicherter Status: `SHARED_ORIGIN_ANIME2YOU`.
- Standardanzeige: `Quelle: AniWorld / Anime2You`.
- Eine echte Zusatzbestätigung wird nur für `INDEPENDENTLY_CONFIRMED` vorgesehen.
- Disc-, Blu-ray-, Volume- und Home-Video-Meldungen bleiben vom Episodenmatcher ausgeschlossen.

## Unveränderte Kernfunktionen

- T+10-AniWorld-Fallback wurde nicht verändert.
- Providerprüfung läuft unabhängig von optionalen Due-Benachrichtigungen weiter.
- Due-Benachrichtigung bleibt standardmäßig aus und gilt ausschließlich für Favoriten.
- Verschiebungsbenachrichtigung und revisionsbasierte Deduplizierung wurden nicht verändert.
- Direkte, abgeleitete und historische Verfügbarkeitsbelege bleiben unterscheidbar.

## Automatisierte Validierung

- Vollständige Testsuite: 333 Tests, 333 erfolgreich, 0 Fehler.
- Neue Tests:
  - ältere zukünftige E17-Zeile verdrängt verschobene E20 nicht;
  - Home- und Detailresolver liefern denselben Zustand;
  - Dub-Verschiebung beeinflusst den Sub-Countdown nicht;
  - Disc-/Blu-ray-Meldung wird nicht als Episodenverschiebung akzeptiert.
- `assembleDebug`: erfolgreich.

## Gerätevalidierung

- Gerät: `R3CX4056Q8L`
- Installierte Version: `0.25.14` (`versionCode 60`).
- Wegen wechselndem Debug-Schlüssel wurden App-Daten vor Neuinstallation lokal gesichert, geprüft und anschließend wiederhergestellt.
- Wiederhergestellter Bestand vor App-Start: 37 aktive Favoriten, 295 Anime, 3632 Releases.
- Sicherung ist privat und darf niemals in Ausgabeordner oder GitHub gelangen.
- Startseite Bookworm geprüft: Folge 20 und Countdown bis 05.09.2026 11:30.
- Detailseite geprüft: kompakte Verschiebungskarte, S3E20 als nächster und S3E19 als letzter Release.
- Favoritenseite geprüft: Bookworm zeigt ebenfalls Folge 20 und denselben Termin.

## Für GitHub vorgesehene Dateien

- `CHANGELOG.md`
- `app/build.gradle.kts`
- `app/src/main/java/de/anisentinel/app/data/news/Anime2YouNewsRepository.kt`
- `app/src/main/java/de/anisentinel/app/ui/DetailScreen.kt`
- `app/src/main/java/de/anisentinel/app/ui/FavoritesViewModel.kt`
- `app/src/main/java/de/anisentinel/app/ui/HomeViewModel.kt`
- `app/src/main/java/de/anisentinel/app/ui/PostponementScreens.kt`
- `app/src/main/java/de/anisentinel/app/ui/ReleaseDisplayResolver.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/de/anisentinel/app/ui/ReleaseDisplayResolverTest.kt`
- dieser Validierungsbericht

## Veröffentlichungsfreigabe

- Die Nutzerfreigabe wurde am 24.08.2026 nach erfolgreicher Geräteprüfung erteilt.
- Der finale Ausgabeordner und die GitHub-Veröffentlichung werden aus genau diesem geprüften Stand erzeugt.
- Private Gerätesicherungen, Room-Datenbanken, ADB-Dumps, lokale Konfigurationen und Secrets sind ausdrücklich ausgeschlossen.

---

## Ursprünglicher Bericht: V0.25.14_AUSGABE_HINWEISE.txt

AniSentinel v0.25.14 (Build 60)

Zu installieren:
AniSentinel-v0.25.14-debug.apk

Der Ordner Sourcecode enthält den vollständigen, zu dieser APK gehörenden Quellstand.
Der Änderungs- und Validierungsbericht dokumentiert Umsetzung, Tests und Geräteprüfung.

Private Gerätedaten, lokale Room-Datenbanken, ADB-Dumps, Zugangsdaten und Secrets sind nicht enthalten.

---

## Ursprünglicher Bericht: V0.25.13_GESAMTBERICHT_SEIT_V0.25.8.md

# AniSentinel v0.25.13 – Gesamtbericht seit v0.25.8

## Ergebnis

Der Releasezustand wurde quellen-, staffel-, episoden- und sprachsicher gemacht. Insbesondere werden aktuelle Sub-Releases nicht mehr mit älteren Dub-Verschiebungen vermischt. Der geprüfte Diagnosebuild ist `0.25.13` (`versionCode 59`).

## Releaseidentität und Verfügbarkeit

- Zentrale `ReleaseIdentity` aus Anime, realer Staffel, realer Folge, Sprache und optional Anbieter eingeführt.
- Providerneutrale persistierte `AVAILABLE`-Datensätze werden als bestätigte Verfügbarkeit berücksichtigt.
- Letzter und nächster Release werden innerhalb der aktuellen realen Staffel bestimmt; historische oder falsch zugeordnete Providerzeilen verdrängen aktuelle Kalenderdaten nicht mehr.
- Bestätigte spätere Episoden können ältere ungeprüfte Episoden desselben Anbieters und Sprachpfads als abgeleitet verfügbar markieren, ohne Providerbelege zu erfinden.

## Staffelkorrekturen

- Providerinterne Sprach-, Dub- und Katalogvarianten werden nicht mehr als zusätzliche Anime-Staffeln ausgegeben.
- Die sichtbare Staffelstruktur wird gegen die durch aktuelle Kalenderdaten belegte Staffel begrenzt.
- Der Bookworm-Testfall zeigt nur Staffel 1 bis 3; Phantomstaffeln 4 bis 6 sind entfernt.

## Verschiebungen

- Verschiebungen bleiben eigenständige Fakten und ändern nicht automatisch die Identität des nächsten Releases.
- Eine explizite Verschiebung wird niemals heuristisch auf `letzte Folge + 1` umnummeriert.
- Der vollständige Hinweis erscheint in der Detailseite zusätzlich zu „Letzter Release“ und „Nächster Release“.
- Kompakte Hinweise bleiben innerhalb der jeweiligen Titelkarte.
- Jeder Hinweis nennt Staffel, Folge und Sprachfassung.
- Beispiel Slime: `S4 E17 GER_DUB`, 21.08. auf 28.08., bleibt getrennt von `S4 E19 GER_SUB` und `S4 E20 GER_SUB`.
- Beispiel Bookworm: `S3 E20 GER_SUB`, 29.08. auf 05.09.2026 um 11:30 Uhr.

## Verbindliche AniWorld-Sprachregel

- Explizite AniWorld-Dub-Kennzeichnung wird `GER_DUB`.
- Jeder andere AniWorld-Kalender- oder Verschiebungseintrag wird `GER_SUB`.
- Die Regel greift im Parser, in der gespeicherten Releaseidentität und in der UI.
- Sub und Dub desselben Titels bleiben auch bei unterschiedlichen Episoden und Zeiten getrennte Datensätze.

## Widersprüchliche Verschiebungsdaten

- Ein als nach hinten verschoben markierter Termin darf chronologisch nicht vor dem Ursprungstermin liegen.
- Bei einem offensichtlichen ausgelassenen Monatswechsel wird der Folgemonat verwendet.
- Chainsmoker Cat S1E9 wird dadurch korrekt von 27.08. auf 03.09.2026 gesetzt.
- Der konkrete Anime2You-Artikel bestätigt Episode, Ursprungstermin, Zieltermin und Simulcast-Auswirkung.
- Keine pauschale Anime2You-Suche wird als Datenquelle verwendet, weil Suchergebnisse auch irrelevante Disc-Termine enthalten können.

## Benachrichtigungen und Persistenz

- Benachrichtigungsidentitäten berücksichtigen Staffel, Episode und Sprache.
- Dub-Verschiebungen lösen keine falschen Sub-Releasezustände aus.
- Favoriten und lokale Daten wurden beim wegen Signaturwechsel notwendigen Neuinstallationsvorgang lokal gesichert und wiederhergestellt.
- Diese private Sicherung ist ausdrücklich nicht Bestandteil des Ausgabeordners oder GitHub-Repositories.

## UI-Auswirkungen

- Detailkarten zeigen korrekte letzte und nächste Releases.
- Rote Verschiebungskarten zeigen alten Termin, neuen Termin, Folge, Sprache und Grund.
- Titelkarten können neben dem aktuellen Sub-Release einen separat beschrifteten Dub-Verschiebungshinweis anzeigen.
- Strings für neue Hinweise liegen in Android-Ressourcen.

## Tests und Geräteprüfung

- 330 Unit-/Robolectric-Tests: 330 erfolgreich, 0 Fehler.
- `testDebugUnitTest`: erfolgreich.
- `assembleDebug`: erfolgreich.
- Installation über ADB auf Gerät `R3CX4056Q8L`: erfolgreich.
- Installierte Version: `0.25.13`, Build 59.
- Geräteprüfung Bookworm: S3E20-Verschiebung, S3E19 letzter Sub-Release und nur drei Staffeln korrekt.
- Nutzerabnahme Slime-Sprachtrennung und Verschiebungsdarstellung: erteilt.

## Bewusst offene Punkte

- Öffentliche Provider-Webadapter bleiben experimentell und können bei Markupänderungen `CHECK_FAILED` liefern.
- Anime2You wird nur über konkrete Beleglinks ausgewertet; eine allgemeine Suchergebnisseite ist keine verlässliche strukturierte Episodenquelle.
- Compiler meldet bestehende Deprecation-Hinweise für einzelne Material-Icons und Android-APIs; Build und Tests sind davon nicht betroffen.

## Datenschutz

Nicht veröffentlicht werden lokale Room-Datenbanken, WAL/SHM-Dateien, ADB-Dumps, Geräteaufnahmen, private Favoriten, lokale Konfigurationen, Schlüssel oder Secrets.

---

## Ursprünglicher Bericht: V0.25.13_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.13 – Validierungsbericht

- Version: 0.25.13
- VersionCode: 59
- Datum: 24.08.2026
- Build: `assembleDebug` erfolgreich
- Tests: 330 erfolgreich, 0 Fehler, 0 Abbrüche
- APK-Installation: erfolgreich
- ADB-Gerät: R3CX4056Q8L
- Geräteversion bestätigt: 0.25.13 (59)
- Nutzerabnahme: erteilt

## Geprüfte Regressionen

1. AniWorld ohne Dub-Kennzeichnung ergibt GER_SUB.
2. AniWorld mit Dub-Kennzeichnung ergibt GER_DUB.
3. Sub und Dub werden nicht zusammengeführt.
4. Eine Dub-Verschiebung ändert nicht die nächste Sub-Episode.
5. Verschiebungshinweise nennen Staffel, Episode und Sprache.
6. Rückwärts gerichtete Delay-Datumsangaben werden auf einen chronologisch plausiblen Folgemonat korrigiert.
7. Bookworm besitzt keine Phantomstaffeln 4 bis 6.
8. Providerhistorie verdrängt keine aktuelleren Kalenderreleases.

## Artefaktprüfung

Der finale Ausgabeordner wird auf Datenbanken, WAL/SHM-Dateien, lokale Gerätesicherungen, ADB-Dumps, Schlüssel, Secrets und `local.properties` geprüft. Die vollständige ZIP muss unter 512 MB bleiben.

---

## Ursprünglicher Bericht: V0.25.11_GESAMTBERICHT_SEIT_V0.25.8.md

# AniSentinel v0.25.11 – Gesamtänderungsbericht seit v0.25.8

Stand: 24.08.2026  
Version: 0.25.11  
Build: 57

## Anlass

Im Gerätebetrieb waren Releasefortschritt, Verschiebungen und Providerhistorie nicht durchgehend auf dieselbe fachliche Episode bezogen. Bei „Ascendance of a Bookworm“ zeigte die Detailseite S3E16 als letzten und S3E17 als nächsten Release, obwohl S3E19 bereits verfügbar und S3E20 auf den 05.09.2026 verschoben war. Zusätzlich wurden Crunchyroll-interne Varianten als Staffeln 4–6 dargestellt.

## Umgesetzte Änderungen

### Quellenunabhängige Episodenidentität

- Neue zentrale `ReleaseIdentity` aus Anime-ID, realer Staffel, Episode, Sprachfassung und optionalem Provider.
- Quell-IDs werden nicht mehr als fachliche Episodenidentität behandelt.
- Semantisch gleiche AniWorld-, Provider- und Verfügbarkeitszeilen können zusammengeführt werden.
- Eine fehlende Sprachangabe in einem Verschiebungsfeed wird als nicht spezifizierter Episodentrack behandelt. Sie verhindert nicht mehr die Zuordnung zu GER_SUB oder GER_DUB derselben Episode.

### Letzter und nächster Release

- `ReleaseDisplayResolver` bestimmt den höchsten bestätigten Episodenstand der aktuellen realen Staffel.
- Persistierte `AVAILABLE`-Zustände providerneutraler Kalenderzeilen gelten als echte Bestätigung; ein leerer Providername entwertet den bereits gespeicherten Prüferfolg nicht mehr.
- OmU- und Dub-Releases werden für den Episodenfortschritt gemeinsam betrachtet. Ein älterer Dub-Termin darf eine höhere bereits verfügbare OmU-Episode nicht mehr verdrängen.
- Der nächste Release muss fachlich nach dem höchsten bestätigten Episodenstand liegen.
- Historische Providerzeilen, Verfügbarkeitsprüfungen und Kalendertermine werden weiterhin als getrennte Belege gespeichert.

### Verschiebungen und Countdown

- Aktive Verschiebungen überschreiben den ursprünglichen Termin für Anzeige und Countdown.
- Verschiebungskarte, nächster Release und Countdown verwenden dieselbe kanonische Episode.
- Sprachlose Verschiebungszeilen werden korrekt der konkreten Episode zugeordnet.
- Die Detailseite zeigt den roten Verschiebungshinweis wieder vollständig mit ursprünglichem Termin, Ersatztermin und Quellenstatus.
- Abgelaufene oder fachlich fremde Verschiebungen werden nicht an andere Episoden übertragen.

### Reale Staffelstruktur

- Die laufende Kalenderstaffel begrenzt Provider-only Staffelnummern nach oben.
- Crunchyroll-interne Dub-/Versionsvarianten erzeugen keine zusätzlichen Staffelchips mehr.
- Bei expliziter Providerwahl werden kanonische Staffelnummern angezeigt; technische Provider-Season-IDs bleiben interne Zuordnungsdaten.
- Für den geprüften Bookworm-Datensatz werden nur Staffel 1, 2 und 3 dargestellt, nicht mehr 4–6.

### Benachrichtigungen und Darstellung

- Geplante Releasehinweise wurden von Verfügbarkeitsbestätigungen getrennt und sind standardmäßig deaktivierbar.
- Providerprüfungen laufen unabhängig von dieser Benachrichtigungseinstellung weiter.
- Aus einer später bestätigten Episode abgeleitete frühere Verfügbarkeit wird als „wahrscheinlich verfügbar“ gekennzeichnet.
- Doppelte Uhrzeitbeschriftungen wurden korrigiert.

## Reale Gerätediagnose

Gerät: Samsung SM-S928B, ADB-ID R3CX4056Q8L.

Vor der Korrektur:

- letzter Release: S3E16 GER_DUB
- nächster Release: S3E17 GER_DUB
- Verschiebung in der Detailseite fehlte
- sechs Staffelchips sichtbar

Nach Installation von v0.25.11 / Build 57 mit unverändert wiederhergestellten lokalen Nutzerdaten:

- roter Hinweis „VERSCHOBEN“ sichtbar
- Episode: S3E20
- ursprünglicher Termin: 29.08.2026, 11:30
- neuer Termin: 05.09.2026, 11:30
- nächster Release: S3E20, OmU / Deutsch untertitelt, verschoben
- letzter Release: S3E19, OmU / Deutsch untertitelt, verfügbar
- sichtbare Staffelchips: Staffel 1, Staffel 2, Staffel 3

## Datenschutz

- Die zur Schlüsselmigration notwendige lokale App-Datensicherung wird nicht ausgeliefert und nicht versioniert.
- Keine Room-Datenbank, WAL-/SHM-Datei, ADB-Ausgabe, Nutzerfavoriten, Gerätescreenshots, Secrets oder lokale Konfigurationen werden in Sourcepaket oder GitHub aufgenommen.
- Der Gerätebestand wurde vor dem notwendigen Signaturschlüsselwechsel lokal gesichert und nach Installation wiederhergestellt.

## Auswirkungen

Die Korrektur ist nicht titelbezogen. Sie gilt für alle Titel und Anbieter, weil die Regeln zentral auf kanonischer Staffel-/Episodenidentität, persisted Availability und Provider-Season-Mappings arbeiten.

---

## Ursprünglicher Bericht: V0.25.11_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.11 – Validierungsbericht

Stand: 24.08.2026

## Build

- `testDebugUnitTest`: erfolgreich
- `assembleDebug`: erfolgreich
- JVM-Tests: 327
- Fehler: 0
- Fehlschläge: 0
- APK-Version: 0.25.11
- versionCode: 57

## Neue Regressionen

- providerneutrale `AVAILABLE`-Kalenderzeile bestimmt den aktuellen Episodenstand
- höchste Episode gewinnt sprachübergreifend innerhalb der realen Staffel
- sprachlose Verschiebung passt zur konkreten Episode mit bekanntem Sprachtrack
- laufende Kalenderstaffel unterdrückt spätere Provider-Sprachvarianten als Phantomstaffeln

## Geräteprüfung

- Installation über ADB: erfolgreich
- installierte Version über `dumpsys package`: 0.25.11 / 57
- private App-Daten vor Signaturwechsel gesichert, Archiv geprüft und nach Neuinstallation wiederhergestellt
- Bookworm-Detailseite real geprüft: E19 zuletzt verfügbar, E20 auf 05.09.2026 verschoben
- Verschiebungskarte sichtbar
- Staffelchips real geprüft: ausschließlich 1, 2 und 3

## Bekannte Hinweise

- Der Debug-Build kann von lokal installierter Sicherheitssoftware als unbekannte Test-App beanstandet werden; die Warnung wurde abgebrochen, nicht die App deinstalliert.
- Bestehende Kotlin-/Android-Deprecation-Warnungen sind nicht buildblockierend.

---

## Ursprünglicher Bericht: V0.25.9_AENDERUNGSBERICHT.md

# AniSentinel v0.25.9 – Änderungsbericht

Datum: 24.08.2026

## Ausgangslage

Der Bericht zu v0.25.8 zeigte, dass Detailkopf, Episodenkarten, Verschiebungen, Countdown und Benachrichtigungsziel fachlich gleiche Releases teilweise über unterschiedliche Quell-IDs oder Auswahlregeln behandelten. Dadurch konnte eine bestätigte neuere Folge unterhalb einer älteren „Letzter Release“-Karte stehen oder eine Verschiebung den falschen Countdown begleiten.

## Umsetzung und Wirkung

- `ReleaseIdentity` vereinheitlicht Anime, reale Staffel, Folge und Sprachfassung. Quell- und Provider-IDs bleiben Belege, sind aber nicht mehr die Episodenidentität.
- `AvailabilityEvidence` ordnet Nachweise nach `PROVIDER_DIRECT`, `ANIWORLD_FALLBACK`, `HISTORICAL_PROVIDER_CATALOG`, `DERIVED_FROM_LATER_EPISODE` und `UNKNOWN`.
- `ReleaseDisplayResolver` wählt die höchste semantisch bestätigte relevante Episode. Ein älterer Datensatz kann eine bestätigte Folge 19 nicht mehr allein wegen seines Datums verdrängen.
- Nach dem realen Gerätetest wurde die verbliebene Zeitfilterlücke geschlossen: Eine direkt bestätigte Providerfolge zählt auch dann als letzter Release, wenn ihr importierter Zeitstempel fehlerhaft in der Zukunft liegt. Der nächste Release muss fachlich höher als der letzte bestätigte Release sein.
- Ein weiterer Geräteabgleich zeigte bestätigte historische Provider-Releases ohne separate Availability-Check-Zeile. Diese zählen nun ebenfalls als bestätigte Episoden; dadurch bestimmen vorhandene Folgen 17–19 den Kopf korrekt, statt dass er bei Kalenderfolge 16 stehen bleibt.
- Nächster Release, Countdown und Verschiebung verwenden im Detailbildschirm dieselbe `ReleaseIdentity`. Eine Verschiebung für Folge 20 kann nicht mehr den Countdown von Folge 17 übernehmen.
- Der aktive Ersatztermin einer exakt passenden Verschiebung ersetzt nun den ursprünglichen `expectedAt` auch für „Nächster Release“ und Countdown. Damit läuft ein verschobener Release nicht mehr bis zum alten Termin herunter.
- Die zum aktiven nächsten Release gehörende Verschiebung erscheint in der Detailseite wieder als vollständige rote Karte mit Ersatztermin, Grund und Quellenbeleg; eine Verschiebung einer anderen Episode bleibt ausgeschlossen.
- Abgelaufene Verschiebungen erscheinen nicht mehr als aktive rote Hinweise auf normalen Titelkarten. Bei einem konkret noch offenen Release darf die Detailansicht den Hinweis bis zur Klärung behalten; die Historie bleibt vollständig erhalten.
- `firstAvailableAt` wird als „Erstmals von AniSentinel erkannt“ angezeigt. Die frühere rechnerische „erkannte Verzögerung“ wurde entfernt, weil sie kein belastbarer Provider-Veröffentlichungszeitpunkt ist.
- Aus einer späteren bestätigten Folge abgeleitete Verfügbarkeit wird sichtbar als „Wahrscheinlich verfügbar“ bezeichnet und nicht als Direktbestätigung ausgegeben.
- Die neue DataStore-Einstellung „Bei geplantem Release benachrichtigen“ ist standardmäßig aus. Providerprüfungen starten weiterhin. Bei Aktivierung kann nur ein Favorit genau eine semantisch deduplizierte Due-Mitteilung erhalten; Nichtfavoriten verlassen den bestehenden Favoriten-Scheduler nicht.
- Zeitstrings erwarten nun vollständig formatierte Zeitwerte und hängen kein zweites „Uhr“ an.

## Tests

Ergänzt wurden Regressionen für quellenunabhängige Releaseidentität, strikte Episodentrennung, `UNSPECIFIED`-Normalisierung, höchste bestätigte Folge und exaktes Due-Benachrichtigungsziel. Der vollständige JVM-Testlauf umfasst 321 Tests.

## Datenschutz

Es wurden keine Gerätedaten, Datenbanken, WAL-/SHM-Dateien, Favoriten, Cookies, Tokens, Browserprofile oder private Aufnahmen in die Änderung aufgenommen. Die zahlreichen bestehenden lokalen unversionierten Diagnoseartefakte wurden nicht verändert.

---

## Ursprünglicher Bericht: V0.25.9_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.9 – Validierungsbericht

Datum: 24.08.2026

## Automatisierte Prüfung

- `testDebugUnitTest`: erfolgreich
- 323 JVM-Tests, 0 Fehler
- `assembleDebug`: erfolgreich
- `git diff --check`: erfolgreich
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Geräteprüfung

- Upgrade-Installation mit `adb install -r`: erfolgreich; vorhandene App-Daten wurden nicht gelöscht.
- Paketstart über den Launcher-Intent: erfolgreich.
- Installierte Version: `versionName=0.25.9`, `versionCode=55`.
- Gerätedaten, ADB-Dumps und lokale Datenbanken wurden nicht in Ausgabe- oder Sourcepakete übernommen.

## Abgedeckte Regressionen

- Quellenunabhängige Identität derselben realen Episode.
- Keine Übertragung einer Verschiebung auf eine andere Folge.
- `UNSPECIFIED` wird als fehlende Sprachangabe normalisiert und nicht als fachlich andere Sprachfassung behandelt.
- Höchste semantisch bestätigte Folge bestimmt „Letzter Release“.
- Due-Mitteilung trägt Staffel, Folge und Sprachfassung als exaktes Klickziel.
- Due-Mitteilung ist über DataStore standardmäßig ausgeschaltet und bleibt auf Favoriten beschränkt; die Providerprüfung startet unabhängig davon.
- Aus späteren Folgen abgeleitete Verfügbarkeit wird nicht als Direktbestätigung bezeichnet.
- AniSentinel-Erkennung wird nicht als Providerverzögerung berechnet.
- Zeitformatierung erzeugt kein „Uhr Uhr“.
- Direkt bestätigte Folge 19 verdrängt veraltete Kopfwerte wie Folge 16 auch bei unplausiblem importiertem Zeitstempel.
- Ein aktiver verschobener Kalenderrelease hinter der höchsten Providerfolge wird quellenneutral als deren kommender Nachfolger aufgelöst; Detailkarte, rote Verschiebungskarte und Countdown verwenden denselben Ersatztermin.

## Noch manuell zu beobachten

- Ein real eintreffendes T+10-Fallback und eine spätere direkte Providerbestätigung benötigen ein passendes aktuelles Releasezeitfenster.
- Die optionale Due-Mitteilung sollte beim nächsten favorisierten Echttermin einmal mit Einstellung AUS und einmal mit Einstellung EIN beobachtet werden.
- Private Bildschirmaufnahmen wurden bewusst nicht automatisch erstellt oder veröffentlicht.

---

## Ursprünglicher Bericht: V0.25.8_GESAMTBERICHT_SEIT_V0.25.7.md

# AniSentinel v0.25.8 – Gesamtbericht seit v0.25.7

Stand: 21.08.2026

Paket: `de.anisentinel.app`

Build: Diagnose-/Testversion `0.25.8` (`versionCode 54`)

## Ergebnis

Der Release- und Availability-Datenweg wurde so korrigiert, dass die Auswahl eines bevorzugten Anbieters nicht mehr die technische Prüfung auf genau diesen einen Anbieter beschränkt. AniSentinel prüft für eine fällige Episode alle von JustWatch Deutschland bestätigten Direktanbieter, für die ein direkter Adapter vorhanden ist. Die Nutzerwahl steuert weiterhin die sichtbare Anbieterstruktur und den bevorzugten Deep-Link.

Die zentrale fachliche Kette lautet damit:

```text
AniWorld: erwartete Folge und Termin
→ JustWatch Deutschland: tatsächliche Direktanbieter
→ jeder unterstützte Direktanbieter: konkrete Staffel/Episode/Sprache
→ AVAILABLE / NOT_AVAILABLE_YET / CHECK_FAILED
→ ab T+10 zusätzlich AniWorld als Sicherheitsbeleg
```

JustWatch wird weiterhin nicht als Episodenbeleg verwendet. Es wurden keine Login-, Playback-, Stream-, DRM- oder Downloadfunktionen ergänzt.

## 1. Einheitliche direkte Prüfung aller bestätigten Anbieter

Vorher wurde `ProviderSelectionPolicy.select(...).references` zugleich für Darstellung und für die technische Prüfung verwendet. Eine Nutzerpräferenz konnte dadurch bewirken, dass andere von JustWatch bestätigte Anbieter gar nicht geprüft wurden.

Jetzt werden zwei Verantwortlichkeiten getrennt:

- `selectedProviders`: Darstellung, bevorzugte Katalogstruktur und Deep-Link;
- `directProbeProviders`: alle deutschen, von JustWatch gespeicherten Providerreferenzen, die von einem direkten Adapter unterstützt werden.

Für Crunchyroll werden Amazon-Channel-Angebote weiterhin nicht als direkter Crunchyroll-Webcheck behandelt. ADN, Crunchyroll, Netflix, Disney+ und aniverse können unabhängig voneinander eigene Availability-Zeilen erzeugen. Ein negatives Ergebnis eines Providers verdeckt keinen gültigen positiven Beleg eines anderen Providers.

## 2. Drei getrennte Ergebnisarten

Die Provideradapter unterscheiden weiterhin fachlich:

- `Available`: konkrete Episode ist direkt nachgewiesen;
- `NotAvailableYet`: Seite/Antwort war technisch auswertbar, Ziel-Episode fehlt;
- `CheckFailed`: Netzwerk, Markup, Parser oder Identität war nicht zuverlässig auswertbar.

Ein technischer Fehler wird nicht als nicht verfügbare Folge ausgegeben. In Room bleiben Status, Quelle, Evidence-Typ, Fehlercode, Prüfzeit, Provider-ID und erkannte URL getrennt nachvollziehbar.

## 3. AniWorld-Sicherheitsfallback exakt ab T+10

Der bisherige Fallback war an technische Fehler gekoppelt und konnte durch ein direktes `NOT_AVAILABLE_YET` unterdrückt werden. Das widersprach dem gewünschten unabhängigen Sicherheitsweg.

Die neue Regel lautet:

```text
now < expectedAt + 10 Minuten
→ kein AniWorld-Fallback

now >= expectedAt + 10 Minuten
AND noch kein direkter AVAILABLE-Beleg
→ AniWorld-Fallback ausführen
```

Das gilt sowohl nach `NOT_AVAILABLE_YET` als auch nach `CHECK_FAILED`. Die direkte Providerprüfung läuft zuerst und wird nach einer AniWorld-Bestätigung in späteren Watcher-Läufen weitergeführt. Dafür fragt Room gezielt favorisierte Releases ab, die bereits durch den Fallback bestätigt wurden, aber noch keinen semantisch passenden direkten Providerbeleg besitzen.

Vorhandene Room-Daten werden bei Fehlern nicht gelöscht. Eine spätere Direktbestätigung ergänzt den stärkeren Providerbeleg und kann den direkten Episodenlink bereitstellen.

## 4. Fachlich identische Episoden statt reine Quell-ID

Ein reales Release kann unter verschiedenen `sourceReleaseId`-Werten vorkommen. Das führte beim BLACK-TORCH-Fehler dazu, dass die Episodenkarte bereits `verfügbar` zeigte, während die obere Releasekarte weiterhin eine Verzögerung berechnete.

Der neue semantische Abgleich verwendet:

```text
animeId
+ kanonische Staffel
+ Episodennummer
+ Sprachfassung
```

Die DAO kann den Releasezustand aller dazugehörigen nicht-historischen Quellzeilen gemeinsam auf `AVAILABLE` setzen. Die Detailseite sucht für die obere Releasekarte außerdem Availability-Belege aller semantisch passenden Releasezeilen und priorisiert einen gültigen Availability-Beleg vor einem neueren negativen oder technischen Eintrag.

Auswirkung:

- ein gültiger Beleg beendet den Verzögerungsstatus;
- oberer Release und untere Episodenkarte verwenden denselben fachlichen Zustand;
- Quell-ID-Dubletten erzeugen keinen sichtbaren Widerspruch mehr;
- andere Episoden oder Sprachfassungen werden nicht versehentlich zusammengeführt.

## 5. Direkte Prüfungen nach Fallback fortsetzen

Eine AniWorld-Fallback-Bestätigung setzt den sichtbaren Release korrekt auf verfügbar, beendet aber nicht dauerhaft die Suche nach einem stärkeren direkten Providerbeleg. Die neue Room-Abfrage berücksichtigt auch semantisch passende Belege unter einer anderen Release-ID. Dadurch entstehen keine unnötigen Wiederholungen, wenn der Direktbeleg bereits für dieselbe reale Episode gespeichert ist.

## 6. Netflix-Falschnegative und strukturierte Metadaten

Der öffentliche Netflix-Prüfweg verließ sich zu stark auf sichtbare Textmuster. Eine korrekte Seite konnte dadurch bei dynamischer Darstellung fälschlich als `nicht gefunden` gelten, wie beim Referenzfall Chainsmoker Cat S1E8.

Der Parser wertet nun zusätzlich öffentlich eingebettete strukturierte Felder aus, darunter:

- Staffelnummer;
- Episodennummer;
- Episode-/Video-ID;
- Episode-/Watch-URL;
- strukturierte Episodenindizes.

Escaped JSON-Inhalte wie `\/` und `\"` werden normalisiert. Wird die konkrete Ziel-Episode in strukturierten Daten gefunden, kann ein direkter Availability-Beleg samt Episoden-ID und URL entstehen. Ist ein strukturierter Katalog auswertbar, aber die Episode fehlt, lautet das Resultat `NotAvailableYet`. Ist kein verlässlicher Episodenindex auswertbar, lautet das Resultat `CheckFailed` statt eines Falschnegativs.

Dieselbe konservative Parsersemantik wird von den öffentlichen Netflix-, Disney+- und aniverse-Adaptern verwendet. Es werden keine erfundenen Episoden oder Sprachen erzeugt.

## 7. Datum und Uhrzeit bei historischen Statusangaben

Historische Prüf- und Erkennungszeitpunkte werden nun einheitlich mit Datum und Uhrzeit formatiert:

```text
21.08.2026 · 10:07 Uhr
```

Das betrifft insbesondere sichtbare Werte aus `firstAvailableAt`, `lastCheckedAt`, Quellen-Verfügbarkeitszeit und Releaseüberwachung in Detail-, Kalender- und Episodendarstellungen. Release-Countdowns und reine geplante Uhrzeiten behalten ihre dafür geeignete Darstellung.

## 8. Deep-Link-Verhalten

Die Priorität bleibt providerübergreifend:

1. echte erkannte Episoden-URL;
2. erkannter Staffel-/Bereichslink;
3. echte Serienseite.

Der strukturierte Parser kann nun zusätzliche Episode-/Watch-URLs persistierbar liefern. Es werden keine URLs aus Titeln geraten. Bereits vorhandene Crunchyroll- und ADN-Katalogimporte bleiben Struktur- und Deep-Link-Hilfe, ersetzen aber nicht die aktuelle Availability-Prüfung.

## 9. Tests

Neu beziehungsweise erweitert:

- T+10-Grenze: vor zehn Minuten kein Fallback;
- nach zehn Minuten Fallback auch bei direktem `NOT_AVAILABLE_YET`;
- direkte Prüfung läuft nach Fallback-Bestätigung weiter;
- BLACK-TORCH-artige Quell-ID-Dubletten synchronisieren obere und untere Anzeige;
- semantisches Room-Update setzt genau dieselbe Episode/Staffel/Sprache verfügbar;
- eine andere Episode wird nicht zusammengeführt;
- Netflix-Titeltext allein erzeugt kein Falschnegativ, sondern `CheckFailed`;
- strukturierte Netflix-S1E8-Daten liefern `Available` samt ID und Watch-URL;
- strukturierter Katalog ohne Ziel-Episode liefert `NotAvailableYet`.

Vollständiger Lauf:

```text
./gradlew clean testDebugUnitTest assembleDebug
317 Tests
0 Fehler
0 übersprungen
BUILD SUCCESSFUL
```

Die vorhandenen Compilerhinweise betreffen bekannte Deprecated-APIs und eine bestehende statische Bedingung; sie verhindern Build und Tests nicht.

## 10. Geräteprüfung

Gerät: Samsung SM-S928B, ADB-ID `R3CX4056Q8L`.

- Installation mit `adb install -r`, daher kein Löschen vorhandener App-Daten;
- Installation erfolgreich;
- installierte Version `0.25.8`, `versionCode 54`;
- `MainActivity` nach Start als aktive Activity bestätigt;
- Startseite mit 80 vorhandenen aktuellen Titeln sichtbar;
- kein `AndroidRuntime`-Absturz im geprüften Logcat-Ausschnitt.

Die Parser-Regressionen für Chainsmoker Cat und der fachliche BLACK-TORCH-Abgleich sind deterministisch durch Unit-/Room-Tests abgesichert. Öffentliche Providerantworten bleiben grundsätzlich veränderlich; nicht eindeutig auswertbare Live-Antworten werden deshalb ehrlich als `CHECK_FAILED` behandelt und ab T+10 durch den unabhängigen AniWorld-Weg abgesichert.

## 11. Geänderte Kernbereiche

- `DiagnosticProviderPipeline.kt`: alle bestätigten Direktanbieter, T+10-Parallelweg, semantische Statusfortschreibung;
- `AniSentinelDao.kt`: semantisches Releaseupdate und direkte Nachprüfung nach Fallback;
- `PublicProviderEpisodeAdapters.kt`: strukturierte Episodenmetadaten und konservative Fehlersemantik;
- `DetailScreen.kt`: quellenübergreifender Episodenbeleg und Datum/Uhrzeit;
- `Screens.kt`: Datum/Uhrzeit für gespeicherte Availability-Zeitpunkte;
- `ProviderFallbackLifecycleTest.kt`, `PublicProviderEpisodeAdaptersTest.kt`, `SemanticAvailabilityCheckTest.kt`, `AniSentinelDaoTest.kt`: Regressionen;
- `README.md`, `CHANGELOG.md`, `app/build.gradle.kts`: Dokumentation und Version 0.25.8/54.

## 12. Datenschutz und Paketierung

Für Sourcecodepaket, GitHub und Release werden ausgeschlossen:

- Room-Datenbanken sowie WAL-/SHM-Dateien;
- ADB-Dumps, lokale Gerätedaten und Favoriten;
- Cookies, Tokens, Secrets, Login- und Browserprofile;
- lokale Screenshots und private Aufnahmen, sofern sie nicht bewusst als öffentliches Artefakt ausgewählt wurden;
- `.gradle`, Build-Caches, IDE-Dateien, `local.properties`, `.git` und ältere Ausgabeordner.

Der Ausgabeordner enthält APK, bereinigten Sourcecode, diesen Gesamtbericht, einen Validierungsbericht, SHA-256-Prüfsummen und einen Installationshinweis. Das gebündelte ZIP wird unter 512 MB gehalten.

## 13. Bewusste Grenzen

- Öffentliche Providerseiten und inoffizielle Schnittstellen können Markup, Regionsergebnis oder Zugriff ändern.
- `CHECK_FAILED` bleibt deshalb ein ehrlicher technischer Zustand und wird nicht in Verfügbarkeit oder Nichtverfügbarkeit umgedeutet.
- Provider-Sprachfassungen werden nur bei belastbarer Evidenz bestätigt.
- AniWorld ist Sicherheitsbeleg ab T+10, nicht Ersatz für die weiterlaufende direkte Providerprüfung.
- Keine Anmeldung, Wiedergabe, Stream-URL-Auswertung, DRM- oder Downloadfunktion.

---

## Ursprünglicher Bericht: V0.25.8_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.8 – Validierungsbericht

Datum: 21.08.2026

## Build und Tests

- Befehl: `gradlew clean testDebugUnitTest assembleDebug`
- Ergebnis: `BUILD SUCCESSFUL`
- Tests: 317
- Fehler: 0
- Übersprungen: 0
- APK: `app/build/outputs/apk/debug/app-debug.apk`

## Zielgerichtete Regressionen

- AniWorld-Fallback vor/nach T+10
- direkter Providercheck trotz Fallback-Bestätigung
- direkter negativer Check unterdrückt T+10-Fallback nicht
- semantischer Availability-Abgleich über mehrere Quell-IDs
- BLACK-TORCH-artiger Kartenwiderspruch
- strukturierte Netflix-S1E8-Metadaten inklusive Episode-ID und URL
- `CHECK_FAILED` bei technisch nicht eindeutig auswertbarer Seite
- Room-Statusabgleich für gleiche Staffel/Episode/Sprache

## Geräteprüfung

- Gerät: Samsung SM-S928B (`R3CX4056Q8L`)
- Installation: `adb install -r`
- Ergebnis: `Success`
- installierte Version: `0.25.8` / Code 54
- App-Daten: erhalten
- aktive Activity: `de.anisentinel.app/.MainActivity`
- AndroidRuntime-Absturz im geprüften Log: keiner

## Datenschutzprüfung

Das Ausgabepaket darf keine Room-/WAL-/SHM-Dateien, Gerätedumps, Favoriten, Cookies, Tokens, Secrets, Browserprofile, `local.properties`, Build-Caches oder `.git`-Daten enthalten. Die endgültige Dateiliste und Prüfsummen werden nach Paketierung erzeugt.

---

## Ursprünglicher Bericht: V0.25.7_GESAMTBERICHT_SEIT_V0.25.5.md

# AniSentinel v0.25.7 – Gesamtbericht seit dem v0.25.5-Prüfbericht

**Stand:** 21.08.2026  
**Paket:** `de.anisentinel.app`  
**Build:** Diagnose-/Testversion `0.25.7` (`versionCode 53`)

## 1. Ausgangslage

Der zuletzt übergebene externe Prüfbericht bezog sich auf v0.25.5. Danach zeigten reale Gerätetests vor allem Probleme bei langen Serien und bei Titeln, die gleichzeitig über ADN und Crunchyroll angeboten werden:

- bestätigte historische Staffeln waren nach der Phantomstaffel-Korrektur teilweise nicht mehr auswählbar;
- One Piece zeigte nur ADN, obwohl JustWatch Deutschland zusätzlich eine reale Crunchyroll-URL gespeichert hatte;
- AniWorld-, ADN- und Crunchyroll-Staffelnummern wurden zu stark als identische Struktur behandelt;
- die markierte Providerwahl steuerte nicht in allen Fällen auch Episodenstatus und Deep-Link;
- ein Crunchyroll-Watch-Link ließ sich über den strukturierten anonymen Weg nicht immer in eine stabile Series-ID auflösen.

## 2. Durchgeführte Änderungen und Begründung

### 2.1 Historische Providerstaffeln wiederhergestellt

`CanonicalSeasonPolicy` berücksichtigt nun echte historische Providerzeilen als belastbaren Staffelbeleg. Voraussetzung sind reale Provider- und Quelleninformationen; reine AniWorld-Kalenderzeilen oder mehrdeutige alte Backfill-Zeilen reichen weiterhin nicht aus.

**Warum:** Die frühere Schutzlogik gegen Phantomstaffeln blendete bei langen Serien auch echte historische Staffeln aus.

**Auswirkung:** Belegte Staffeln bleiben sichtbar, ohne beliebige Rohdaten zu echten Staffeln hochzustufen.

### 2.2 Aktuellen Kalenderabschnitt erhalten

Der aktuelle beziehungsweise nächste AniWorld-Kalenderabschnitt bleibt neben historisch bestätigten Bereichen sichtbar. Die Staffelauswahl ist horizontal scrollbar.

**Warum:** Bei langen Serien passten nicht alle Bereiche auf ein Smartphone; außerdem durfte der laufende Arc nicht durch den Historienfilter verschwinden.

### 2.3 Historische Imports erzeugen Provider-Mappings

ADN- und Crunchyroll-Historienimporte speichern Releases, Quellenreferenzen, bestätigte Staffeln und deutsche Provider-Staffel-Mappings jetzt gemeinsam transaktional. Vorhandene ältere historische Daten werden beim Öffnen einmalig zu entsprechenden Mappings reconciled.

**Warum:** Providerdaten waren vorhanden, aber die UI konnte daraus nicht zuverlässig ableiten, welcher Katalogbereich angeboten wird.

**Auswirkung:** Bestehende Installationen benötigen keinen vollständigen Datenverlust oder manuellen Neuimport, um belegte Providerbereiche wieder zu erhalten.

### 2.4 ADN und Crunchyroll animeweit auswählbar

Die Detailseite bietet `ADN` und `Crunchyroll` als animeweite Auswahl an, sobald JustWatch Deutschland den jeweiligen direkten Anbieter tatsächlich für den Titel bestätigt. Amazon-Channel-Angebote und sonstige Shops werden nicht als Direktadapter-Auswahl behandelt.

**Warum:** Die Auswahl durfte nicht davon abhängen, ob ein langer Historienimport bereits vollständig abgeschlossen war. JustWatch ist hier ausschließlich der reale Anbieterresolver, nicht der Episodenprüfer.

**Auswirkung:** Bei One Piece werden nun beide Direktanbieter angeboten. Die Wahl wird persistent in Room gespeichert.

### 2.5 Providerwahl steuert die angezeigten Daten

Historische Releases, konkrete Availability-Checks und Deep-Links werden nach dem effektiv ausgewählten Anbieter gefiltert. Eine spätere technische Fehlermeldung eines anderen Adapters kann eine bestätigte Verfügbarkeit des ausgewählten Providers nicht überdecken.

**Warum:** Ein bloß markierter Chip ohne Wechsel der darunter angezeigten Daten wäre fachlich irreführend.

**Auswirkung:** `Crunchyroll` zeigt keine ADN-Episoden als Crunchyroll-Ergebnis; `ADN` verwendet entsprechend nur ADN-Belege.

### 2.6 Eigene Provider-Katalogstruktur statt AniWorld-Gleichsetzung

Nach einer manuellen Providerwahl verwendet die Staffel-/Bereichsauswahl die bestätigten `providerSeasonNumber`-Werte dieses Providers. ADN-Bereiche werden in der Oberfläche als `Saga` bezeichnet. AniWorlds Staffelnummer wird nicht mehr blind mit der Nummer bei ADN oder Crunchyroll gleichgesetzt.

**Warum:** One Piece belegt die Abweichung eindeutig: AniWorld führt 23 Staffeln, ADN 16 Sagas und Crunchyroll eigene Handlungsbögen wie `Elbaph (1156–current)`.

**Auswirkung:** Die App kann die reale Struktur des gewählten Katalogs darstellen. Eine noch nicht aufgelöste Crunchyroll-Struktur wird nicht mit ADN-Daten aufgefüllt.

### 2.7 Crunchyroll-Auflösung abgesichert

Der Crunchyroll-Import versucht:

1. eine vorhandene Series-ID beziehungsweise Serien-URL,
2. die strukturierte anonyme Auflösung einer realen Watch-URL,
3. eingebettete `series_id`/`seriesId`-Metadaten der öffentlichen Seite,
4. eine exakte öffentliche Titelsuche.

Beliebige `/series/…`-Links aus Empfehlungen einer generischen HTML-Shell werden nicht mehr als Treffer übernommen.

**Warum:** Eine öffentliche Watch-Seite kann technisch nur eine allgemeine Shell mit fremden Empfehlungslinks liefern. Der erste beliebige Link wäre ein gefährliches Falschmatch.

**Auswirkung:** Bei nicht sicherer Auflösung bleibt der Crunchyroll-Katalog ehrlich unbestätigt, statt Episoden einer fremden Serie zu importieren.

### 2.8 Quellcode- und Dokumentationsstand

- Version auf `0.25.7` / Code 53 erhöht.
- README auf die neue animeweite Auswahl und Provider-Katalogstruktur aktualisiert.
- CHANGELOG um die v0.25.7-Änderungen ergänzt.
- deutsche und englische Ressourcen für die Saga-Anzeige ergänzt.

### 2.9 Lokale und fortlaufende Episodennummer gemeinsam anzeigen

Bei Providerbereichen mit fortlaufender Gesamtnummerierung zeigt AniSentinel jetzt die Position innerhalb des gewählten Bereichs und zusätzlich die globale Episodennummer. Beispiel für One Piece/Elbaph:

```text
Folge 1 (Episode 1156)
...
Folge 19 (Episode 1174)
```

Die lokale Nummer wird ausschließlich aus der sortierten Episodenmenge des aktuell gewählten Providerbereichs berechnet. Die Klammernummer bleibt die vom Anbieter gelieferte fortlaufende Episodennummer. Sind beide Nummern gleich, bleibt die kompakte Darstellung `Folge N` erhalten.

**Warum:** Eine globale Nummer wie 1156 darf weder als Anzahl der Folgen einer Staffel noch als lokale Staffelnummer interpretiert werden.

**Auswirkung:** Elbaph enthält in AniSentinel genau 19 Karten statt 1174 Karten; andere Crunchyroll-Bereiche werden nach demselben allgemeinen Verfahren abgegrenzt.

Crunchyroll-Recaps und Specials mit eigenen kleinen Episodennummern werden zusätzlich gegen eine im Providerlabel angegebene Spanne geprüft. Dadurch können beispielsweise Episoden `2–11` nicht mehr den Bereich `Land of Wano (892–1088)` verschieben und Episoden `12–21` nicht mehr `Egghead (1089–1155)`. Nach einem vollständig erfolgreichen Abruf ersetzt AniSentinel den bisherigen historischen Provider-Snapshot transaktional, damit auch bereits lokal gespeicherte Fehlzeilen entfernt werden.

### 2.10 Crunchyroll-Suchantwort nicht mehr rekursiv fehlinterpretieren

Die Crunchyroll-Suche wertet nur noch die direkten Serientreffer aus `data[].items[]` aus. Verschachtelte Empfehlungs-, Objekt- oder Metadaten-IDs werden nicht mehr rekursiv als Suchergebnis akzeptiert. Ein Treffer muss als Serie erkennbar sein, einen tatsächlichen Kandidatentitel besitzen und nach der Katalognormalisierung exakt zum gesuchten Titel passen. Mehrere unterschiedliche, gleichermaßen passende Series-IDs führen zu einem ehrlichen mehrdeutigen Ergebnis statt zu einer willkürlichen Auswahl.

**Warum:** Rekursives Durchlaufen beliebiger JSON-Objekte konnte die ID einer anderen Serie übernehmen. Das war die Ursache dafür, dass bei `Daemons of the Shadow Realm` zeitweise die Attack-on-Titan-ID `GR751KNZY` gespeichert war.

**Auswirkung:** Der reale deutsche Crunchyroll-Titel `Das Band der Unterwelt` wurde über die verifizierten Titelvarianten korrekt auf `GT00371630` aufgelöst. Fremde eingebettete IDs werden durch einen Regressionstest ausgeschlossen.

### 2.11 Titelalias und Provideridentität voneinander getrennt

AniWorld-, JustWatch- und Providertitel werden weiterhin als verschiedene Quellwerte behandelt. Für die direkte Providerauflösung werden der AniWorld-Titel, der von JustWatch bestätigte deutsche Katalogtitel und verifizierte Aliaswerte als Kandidaten verwendet. Eine JustWatch-Angebots-URL oder eine lediglich zuvor gespeicherte Identität gilt nicht mehr allein als Beweis für eine Crunchyroll-Series-ID.

**Warum:** Beispiel: AniWorld führt `Daemons of the Shadow Realm`, während JustWatch Deutschland und Crunchyroll den Titel als `Das Band der Unterwelt` führen. Gleichzeitig darf ein alter falscher Identifier nicht dauerhaft alle späteren Prüfungen vergiften.

**Auswirkung:** Titelabweichungen können aufgelöst werden, ohne JustWatch zur Episodenquelle zu machen. JustWatch sagt weiterhin nur, welcher Anbieter den Titel führt; die konkrete Staffel, Episode, Sprache und URL stammen direkt vom Anbieteradapter.

### 2.12 Produktive Episodenprüfung statt statusneutraler Diagnose

Der manuelle beziehungsweise sichtbare Detailseiten-Refresh verwendet für aktuelle Episoden wieder die produktive Providerprüfung. Historische Diagnoseimporte bleiben benachrichtigungsneutral, aber eine aktuelle reale Providerbestätigung darf den Status in Room auf verfügbar setzen.

**Warum:** Eine historische Diagnose konnte eine Folge zwar technisch finden, ließ den sichtbaren Status jedoch unverändert und erzeugte dadurch falsche Verzögerungen.

**Auswirkung:** `Das Band der Unterwelt` S1E19 wurde auf dem Gerät über Crunchyroll als verfügbar erkannt und die Verzögerungsanzeige verschwand. Die Änderung ist adapter- und titelunabhängig.

### 2.13 Crunchyroll-Sprachvarianten zu Inhaltsbereichen zusammengeführt

Crunchyroll kann mehrere interne Season-Objekte für denselben Inhalt ausgeben, beispielsweise Originalton, deutsche Synchronfassung oder andere Sprachvarianten. AniSentinel entfernt bekannte Sprach-/Dub-/Sub-Suffixe für den Strukturvergleich und führt Varianten desselben Inhaltsbereichs zusammen. Reale unterschiedliche Bereichsnamen bleiben getrennt.

**Warum:** Interne Crunchyroll-Varianten dürfen nicht als `Staffel 66` oder als dutzende erfundene Anime-Staffeln erscheinen.

**Auswirkung:** Bei One Piece werden reale Crunchyroll-Bereiche wie East Blue, Alabasta, Wano, Egghead und Elbaph angezeigt. Bei normalen Serien bleiben echte Staffel 1 und Staffel 2 getrennt, während Dubvarianten derselben Staffel nicht dupliziert werden.

### 2.14 Providerlabels dauerhaft in Room gespeichert

`ProviderSeasonMappingEntity` besitzt nun `providerSeasonLabel`. Die Room-Datenbank wurde von Schema 26 auf Schema 27 migriert; der Schemaexport `27.json` ist enthalten. Der Crunchyroll-Import persistiert den bereinigten echten Bereichsnamen zusammen mit Series-ID, Season-ID, Providerbereichsnummer, Region und Bestätigungszeitpunkt.

**Warum:** Eine bloße Zahl erklärt bei langen Serien nicht, welcher Anbieterbereich gemeint ist. Nach App-Neustarts muss beispielsweise `Egghead (1089–1155)` erhalten bleiben.

**Auswirkung:** Die Oberfläche kann nach Neustart dieselbe echte Providerstruktur anzeigen, ohne sie aus AniWorld-Staffeln oder zufälligen Episoden erneut zu erraten.

### 2.15 Vollständige, aber begrenzte Crunchyroll-Pagination

Der anonyme Katalog lädt Seiten mit maximal 100 Objekten, besitzt eine harte Obergrenze und bricht ab, sobald eine Seite weniger als 100 Ergebnisse enthält. Zusätzlich wird für jede Seite ein Fingerabdruck der Objekt-IDs gebildet. Wiederholt ein Endpunkt dieselbe Seite, endet der Abruf sofort.

**Warum:** Der frühere Abruf konnte dieselben Seiten bis zur Sicherheitsgrenze erneut verarbeiten. Das verlängerte Refreshes und erzeugte scheinbare Duplikate beziehungsweise übergroße Kataloge.

**Auswirkung:** Lange Serien werden vollständig geladen, während wiederholte oder fehlerhaft paginierte Antworten keinen Endlosabruf mehr verursachen.

### 2.16 Begrenzte Parallelisierung des Katalogimports

Die Episodenendpunkte der realen Crunchyroll-Season-Objekte werden mit einer Coroutine-Semaphore und höchstens vier parallelen Abrufen verarbeitet.

**Warum:** Ein vollständig serieller Import war bei langen Serien unnötig langsam; unbeschränkte Parallelität würde dagegen den öffentlichen Endpunkt und das Gerät unnötig belasten.

**Auswirkung:** Der Import bleibt endlich, ressourcenschonend und deutlich schneller, ohne Massencrawl oder unkontrollierte Hintergrundarbeit.

### 2.17 Erfolgreiche Providerkataloge werden atomar ersetzt

Erst nachdem der neue direkte Providerkatalog erfolgreich geladen, validiert und in neue Room-Entitäten umgewandelt wurde, werden die bisherigen historischen Zeilen und Staffelzuordnungen genau dieses Anime-/Provider-Paars entfernt und durch den neuen Snapshot ersetzt. Kalenderdaten und Daten anderer Anbieter bleiben unberührt.

**Warum:** Ein reines Upsert kann Zeilen nicht entfernen, die ein verbesserter Parser inzwischen als falsch erkannt hat. Genau deshalb blieben die Wano-/Egghead-Specials zunächst trotz Parserfix lokal sichtbar.

**Auswirkung:** Parserkorrekturen bereinigen bestehende Installationen beim nächsten erfolgreichen Refresh. Schlägt der Abruf vorher fehl, werden vorhandene Room-Daten nicht gelöscht.

### 2.18 Animeweite Providerwahl vereinfacht

Die zusätzliche Providerwahl pro Staffel wurde entfernt. Der Nutzer wählt auf der Detailseite einmal den gewünschten, von JustWatch bestätigten direkten Anbieter für den Anime. Diese Wahl steuert anschließend dessen komplette eigene Bereichs-/Staffelstruktur und Episodenliste.

**Warum:** Eine zweite Auswahl pro Staffel war redundant und konnte widersprüchliche Zustände erzeugen, beispielsweise oben Crunchyroll und darunter ADN.

**Auswirkung:** Anbieterwahl und angezeigte Daten stimmen überein. Die Einstellung wird animeweit in Room gespeichert.

### 2.19 Keine Datenersetzung für noch nicht implementierte Direktadapter

Nur Anbieter mit einem tatsächlich implementierten direkten Katalogadapter dürfen Staffel- und Episodendaten liefern. Netflix, Disney+, aniverse oder andere von JustWatch erkannte Angebote werden nicht mit ADN-, Crunchyroll- oder AniWorld-Episoden aufgefüllt.

**Warum:** Eine leere beziehungsweise noch nicht direkt prüfbare Provideransicht ist ehrlicher als eine falsche Staffelstruktur eines anderen Anbieters.

**Auswirkung:** Für nicht unterstützte Direktkataloge erscheint ein klarer Hinweis. Dies bleibt ein offener Ausbaupunkt; es werden keine Providerdaten erfunden.

### 2.20 Tatsächlich geänderte Kernbereiche

Die Änderungen betreffen insbesondere:

- `CrunchyrollAnonymousCatalogClient.kt`: sichere Suche, vollständige Pagination, begrenzte Parallelität, Sprachvarianten-Kollaps und Bereichsfilter;
- `CrunchyrollHistoricalReleaseImporter.kt`: verifizierte Identität, historische Releases, Providerlabels und vollständiger Snapshot;
- `DiagnosticProviderPipeline.kt` und `StructuredProviderMetadataAdapters.kt`: produktive direkte Prüfung und sichere Providerauflösung;
- `ProviderPipelineModels.kt` und `ProviderSelectionPolicy.kt`: Providersemantik und Auswahlregeln;
- `Entities.kt`, `AniSentinelDao.kt`, `AniSentinelDatabase.kt`, `AppContainer.kt`: Room-Schema 27, Abfragen und transaktionale Persistenz;
- `DetailViewModel.kt`, `DetailScreen.kt`, `CanonicalSeasonPolicy.kt`, `ProviderPreferenceUiPolicy.kt`: providergebundene UI-Struktur, Verfügbarkeitsstatus und lokale/fortlaufende Episodennummer;
- `strings.xml` und `values-en/strings.xml`: lokalisierbare deutsche und englische UI-Texte;
- die zugehörigen Unit- und Room-Regressionstests, README und CHANGELOG.

## 3. Tests

Ergänzt beziehungsweise erweitert wurden Tests für:

- echte historische Providerreleases als Staffelbeleg;
- Beibehaltung des laufenden Kalenderabschnitts;
- Ausschluss reiner Kalender-Phantomstaffeln;
- transaktionale ADN-Staffel-/Mapping-Persistenz;
- animeweite ADN-/Crunchyroll-Auswahl aus direkten JustWatch-Referenzen;
- Ausschluss von Amazon-Channel- und nicht unterstützten Katalogangeboten;
- Vorrang einer expliziten animeweiten Wahl;
- automatische Crunchyroll-Priorität ohne manuelle Wahl;
- Abbruch wiederholter Crunchyroll-Paginierungsseiten;
- reale Providerbereichsgrenzen und fortlaufende Episodennummern.

Der vollständige JVM-Testlauf und der Debug-Build werden unmittelbar vor Erstellung des Ausgabeordners erneut ausgeführt. Das endgültige Ergebnis steht im beigefügten Validierungsbericht.

## 4. Reale Geräteprüfung

Gerät: Samsung SM-S928B über ADB.

Geprüft wurde insbesondere One Piece:

- bestehende Nutzerdaten blieben bei `adb install -r` erhalten;
- lange Staffelauswahl wieder sichtbar und horizontal scrollbar;
- JustWatch-Referenzen für ADN und Crunchyroll real in Room vorhanden;
- beide Direktanbieter erscheinen als animeweite Wahl;
- Crunchyroll-Auswahl wird persistent markiert;
- Episodenkarten werden auf den ausgewählten Provider begrenzt;
- ADN-Historie wurde nicht als Crunchyroll-Historie ausgegeben.
- Crunchyroll-Elbaph enthält genau 19 gespeicherte Termine;
- die Karten zeigen `Folge 18 (Episode 1173)` und `Folge 19 (Episode 1174)` korrekt an.
- Wano enthält genau 197 Episoden von 892 bis 1088 und beginnt sichtbar mit `Folge 1 (Episode 892)`;
- Egghead enthält genau 67 Episoden von 1089 bis 1155 und beginnt sichtbar mit `Folge 1 (Episode 1089)`;
- Elbaph enthält genau 19 Episoden von 1156 bis 1174.

## 5. Bewusste Grenzen und offene Punkte

- Crunchyrolls öffentliche Seite und der anonyme strukturierte Katalog können abhängig von Region und aktueller Websiteantwort unterschiedliche Daten liefern. Ohne sichere Series-ID wird kein Katalog erfunden.
- Crunchyrolls Handlungsbogentitel werden als Provider-Labels persistiert; ADN wird als Saga gekennzeichnet.
- JustWatch bestätigt weiterhin nur Titel und Anbieter. Die konkrete Episode und Sprache werden ausschließlich durch den direkten Provideradapter bestätigt.
- Es wurden keine Login-, Playback-, Stream-, DRM- oder Downloadfunktionen ergänzt.

## 6. Datenschutz und Veröffentlichung

Der Ausgabeordner und das Sourcecodepaket dürfen keine der folgenden lokalen Artefakte enthalten:

- Room-Datenbanken, WAL-/SHM-Dateien oder Favoritendaten des Testgeräts;
- ADB-Dumps und lokale Diagnose-Datenbanken;
- Konten, Cookies, Tokens, Secrets oder Browserprofile;
- private Bildschirmaufnahmen außerhalb der bewusst ausgewählten Testartefakte;
- lokale Build-Caches oder `.git`-Metadaten.

Die Paketprüfung und SHA-256-Prüfsummen dokumentieren die endgültig ausgelieferten Dateien.

---

## Ursprünglicher Bericht: V0.25.7_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.7 – Validierungsbericht

**Datum:** 21.08.2026  
**Paket:** `de.anisentinel.app`  
**Version:** `0.25.7` / `versionCode 53`

## Build und Tests

- Befehl: `gradlew.bat testDebugUnitTest assembleDebug --no-daemon`
- Ergebnis: `BUILD SUCCESSFUL`
- JVM-Tests: **311 erfolgreich, 0 fehlgeschlagen**
- APK: `app-debug.apk`

Die ausgegebenen Compilerhinweise betreffen bereits bekannte Android-/Compose-Deprecations sowie eine bestehende statische AniList-Bedingung. Es traten keine Compiler- oder Testfehler auf.

## Geräteprüfung

- Gerät: Samsung SM-S928B
- Verbindung: ADB autorisiert
- Installation: `adb install -r`
- Ergebnis: `Success`
- Nutzerdaten nach Update: **38 aktive Favoriten erhalten**
- gespeicherte One-Piece-Anbietervorgabe: `Crunchyroll`
- reale direkte One-Piece-Referenzen in Room: `Animation Digital Network`, `Crunchyroll`

Geprüft:

- ADN und Crunchyroll erscheinen als animeweite Direktanbieter-Auswahl;
- Crunchyroll lässt sich wählen und bleibt nach dem Update persistent;
- Staffelchips langer Serien sind wieder vorhanden und horizontal scrollbar;
- historische Episodenkarten werden auf den ausgewählten Anbieter begrenzt;
- ohne sichere Crunchyroll-Katalogauflösung werden keine ADN-Releases als Crunchyroll-Releases ausgegeben;
- die Installation hat Favoriten und lokale Einstellungen nicht gelöscht.
- One Piece/Elbaph enthält genau **19** Crunchyroll-Termine statt aller 1174 Gesamtfolgen;
- die Episodenkarten zeigen lokale und fortlaufende Nummer gemeinsam, unter anderem `Folge 18 (Episode 1173)` und `Folge 19 (Episode 1174)`.
- Wano wurde nach dem realen Neuimport mit **197** Episoden (892–1088) und `Folge 1 (Episode 892)` geprüft;
- Egghead wurde mit **67** Episoden (1089–1155) und `Folge 1 (Episode 1089)` geprüft;
- Elbaph bleibt auf **19** Episoden (1156–1174) begrenzt;
- zuvor gespeicherte Crunchyroll-Recap-/Special-Zeilen außerhalb der vom Provider deklarierten Bereichsspanne wurden transaktional entfernt.

## Datenschutzprüfung

Das Ausgabepaket wird aus einer bereinigten Sourcecodekopie erzeugt. Ausgeschlossen sind insbesondere:

- `.git`, `.gradle`, `build`, bestehende `outputs`;
- lokale Room-Datenbanken und `-wal`/`-shm`-Dateien;
- ADB-Dumps, temporäre Gerätekopien und lokale Browserdaten;
- Secrets, Cookies und Tokens.

Die Prüfsummendatei enthält SHA-256-Werte für die ausgelieferte APK, das Sourcecodearchiv, den Gesamtbericht und den Validierungsbericht.

## Ergebnis

Der v0.25.7-Diagnosebuild ist build- und testfähig, auf dem verbundenen Gerät installiert und erhält vorhandene Nutzerdaten. Die Providerwahl ist für reale direkte ADN-/Crunchyroll-Angebote verfügbar und steuert die angezeigten Providerdaten. Noch nicht sicher auflösbare öffentliche Crunchyroll-Kataloge bleiben ausdrücklich unbestätigt.

---

## Ursprünglicher Bericht: V0.25.7_AUSGABE_HINWEISE.txt

AniSentinel v0.25.7

INSTALLATION
1. AniSentinel-v0.25.7-DIAGNOSE.apk auf das Android-Gerät kopieren.
2. APK öffnen und die Installation beziehungsweise Aktualisierung bestätigen.
3. Eine bestehende Installation kann aktualisiert werden; lokale Favoriten bleiben erhalten.

INHALT DES AUSGABEORDNERS
- AniSentinel-v0.25.7-DIAGNOSE.apk
- AniSentinel-v0.25.7-Sourcecode.zip
- AniSentinel-v0.25.7-Komplettpaket.zip
- Sourcecode\
- Testartefakte\ (720p-Geräteclip und relevante UI-Belege)
- V0.25.7_GESAMTBERICHT_SEIT_V0.25.5.md
- V0.25.7_VALIDIERUNGSBERICHT.md
- SHA256SUMS.txt
- SHA256-KOMPLETTPAKET.txt

WICHTIG
Dies ist eine Diagnose-/Testversion. Externe öffentliche Providerseiten können ihre
Struktur ändern. AniSentinel enthält keine Streaming-, Download-, DRM- oder
Playback-Funktion und übernimmt keine privaten Gerätedaten in das Ausgabepaket.

---

## Ursprünglicher Bericht: V0.25.6_AENDERUNGSBERICHT.md

# AniSentinel v0.25.6 – Änderungsbericht

Stand: 21.08.2026
Version: 0.25.6 / versionCode 52 / Room-Schema 26

## Umgesetzt

- Animeweite Providerpräferenz (`seasonNumber = 0`) als sichtbare Auswahl in der Detailseite ergänzt.
- Staffelbezogene Providerpräferenz bleibt als gezielte Ausnahme erhalten und kann wieder auf die Anime-Vorgabe zurückgesetzt werden.
- Normale Providerchips werden ausschließlich aus bestätigten deutschen `ProviderSeasonMappingEntity`-Einträgen mit `available = true` gebildet. Allgemeine JustWatch-Referenzen erscheinen nicht als bestätigte Staffeloption.
- Gespeicherte, inzwischen ungültige Staffel- oder Animepräferenzen erzwingen keinen Providercheck mehr. Die UI kennzeichnet die ungültige Auswahl und die Policy fällt auf einen bestätigten Provider beziehungsweise Automatik zurück.
- `DetailViewModel` nimmt neue Präferenzen nur an, wenn der Provider für Deutschland und – bei Staffelvorgaben – für exakt diese Staffel bestätigt ist.
- Staffelchips werden nicht mehr blind aus allen alten Releasezeilen gebildet. Verifizierte kanonische Staffeln und Provider-Mappings sind maßgeblich; mehrere mehrdeutige reine `RELEASE_BACKFILL`-Staffeln erzeugen keine Phantomchips.
- Globale Staffelansicht bleibt die Vereinigung bestätigter Provider-Teilkataloge, sodass sie nicht auf einen einzelnen Provider reduziert wird.
- Discovery ohne vorhandenes Staffel-Mapping prüft Crunchyroll zuerst und anschließend die übrigen Providerreferenzen. Erst ein echter erfolgreicher Staffelcheck erzeugt weiterhin ein bestätigtes Mapping.
- Deutsche und englische UI-Texte für die neuen Präferenzzustände ergänzt.

## Auswirkung

Die Auswahl „ADN für Staffel 2“ kann nicht mehr gegen ein ausschließlich für Crunchyroll bestätigtes Staffel-Mapping erzwungen werden. Eine Anime-Vorgabe gilt nur in Staffeln, in denen der Provider tatsächlich bestätigt ist. Alte fehlerhafte Release-Staffelnummern können nicht mehr ohne weitere Bestätigung die normale Staffelnavigation aufblasen.

Beim realen One-Piece-Diagnosebestand waren ADN und Crunchyroll als allgemeine Katalogreferenzen vorhanden, aber noch keine bestätigten Provider-Staffel-Mappings. Die frühere Discovery-Reihenfolge begann deshalb mit ADN. v0.25.6 prüft in diesem allgemeinen Zustand nun Crunchyroll zuerst. Dies ist kein titelbezogener Hardcode und keine Verfügbarkeitsbehauptung.

## Nicht zurückgebaut

Release-Lifecycle, Providerfehler-Cooldown, Provider-first-Prüfung, direkte Provideradapter, AniWorld-Fallback, Verschiebungen, Episodendeduplizierung, deutsche Metadaten, Pull-to-Refresh und Anime-Katalogfilter bleiben erhalten.

## Offene reale Langzeittests

- Vollständige One-Piece-Staffelstruktur samt real bestätigter Crunchyroll-/ADN-Teilkataloge muss nach einem erfolgreichen Provider-Backfill erneut auf dem Gerät protokolliert werden.
- Der neue Ein-Staffel-/Phantomstaffel-Fall ist durch Policytests abgesichert, aber noch mit einem konkret benannten Live-Titel zu dokumentieren.
- Crunchyroll-Off-Day-Verhalten und ProviderCheckTrace benötigen mehrere echte Releasetage.
- Eine reale mehrstündige Release-Latenzmessung kann nicht innerhalb dieses Builds simuliert werden.

---

## Ursprünglicher Bericht: V0.25.6_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.6 – Validierungsbericht

Stand: 21.08.2026
Version: 0.25.6 / versionCode 52 / Room-Schema 26

## Build und Tests

```text
gradlew.bat testDebugUnitTest assembleDebug
BUILD SUCCESSFUL
301 JVM-Tests
0 Fehler
```

Neu abgedeckt:

- ungültige Staffelpräferenz fällt auf bestätigtes Crunchyroll zurück;
- Anime-Vorgabe plus Staffel-Ausnahme;
- Providerchips nur aus bestätigten deutschen Staffel-Mappings;
- ungültig gewordene Anime- und Staffelpräferenzen;
- verifizierte Staffel unterdrückt Phantomstaffeln aus alten Releasezeilen;
- mehrdeutige reine Backfill-Staffeln erzeugen keine Chips;
- globale Staffelvereinigung über unterschiedliche Provider-Teilkataloge;
- Discovery prüft Crunchyroll vor anderen unbestätigten Referenzen.

## Geräteprüfung

```text
adb install -r: erfolgreich
installierte Version: 0.25.6
installierter versionCode: 52
App-Start: erfolgreich
Bottom-Navigation: im 720p-Geräteclip durchlaufen
bestehende lokale App-Daten: beim Update erhalten
```

Die Detailansicht zeigt die getrennten Bereiche:

```text
Bevorzugter Anbieter für diesen Anime
Anbieter für Staffel X
```

Die reale Suche unterschied `One Piece` (1999) und `One Piece` (2023). Der AniWorld-One-Piece-Datensatz enthielt im lokalen Diagnosebestand allgemeine Referenzen für Crunchyroll und ADN, aber noch keine bestätigten Provider-Staffel-Mappings. Deshalb ist ein vollständiger realer Nachweis aller One-Piece-Staffeln in diesem Lauf ausdrücklich **nicht bestanden**. Korrigiert wurde die generische Discovery-Prüfreihenfolge: Crunchyroll zuerst, weitere Provider danach.

## Instrumentierte Tests

App und Android-Test-APK kompilierten. `connectedDebugAndroidTest` konnte nicht ausgeführt werden, weil die lokale Java-Zertifikatskette das Nachladen von `android-test-plugin-host-additional-test-output:31.7.3` mit `PKIX path building failed` blockierte. Das ist kein Testfehler der App; der formale Room-Migrationstest bleibt dadurch offen.

## Datenschutzprüfung

- Keine lokalen Room-Datenbanken im Ausgabeordner oder Sourcepaket.
- Keine Gerätekennung in Berichten, Sourcecode oder Prüfsummen.
- Keine Secrets, Login-Daten oder privaten Gerätedateien aufgenommen.
- Der temporäre lokale Datenbankauszug wurde ausschließlich zur Diagnose verwendet und wird nicht paketiert oder committed.

## Bewertung

Build, Unit-Tests, Updateinstallation und sichtbarer App-Start sind bestanden. Die Providerpräferenz- und Staffelpolicy ist durch Regressionstests belegt. Mehrtägige Crunchyroll-Livebeobachtung, reale Latenzmessung, formaler instrumentierter Migrationstest und vollständiger One-Piece-Provider-Backfill bleiben ehrlich offen.

---

## Ursprünglicher Bericht: V0.25.6_AUSGABE_HINWEISE.txt

AniSentinel v0.25.6

APK installieren:
AniSentinel-v0.25.6-DIAGNOSETEST.apk

Der Ausgabeordner enthält APK, vollständigen bereinigten Sourcecode, Source-ZIP,
Änderungsbericht, Validierungsbericht, JVM-Testresultate, Screenshot,
720p-Geräteclip und SHA-256-Prüfsummen.

Das Gesamtpaket bleibt unter 512 MB. Private Gerätedaten und lokale Datenbanken
sind nicht enthalten.

---

## Ursprünglicher Bericht: V0.25.5_AENDERUNGSBERICHT.md

# AniSentinel v0.25.5 – Änderungsbericht

Stand: 21.08.2026

## Release-Lifecycle

- Enge Providerprüfungen enden für alte unbestätigte Releases spätestens nach 24 Stunden.
- Existiert bereits ein Nachfolgerelease, wird der alte Watcher nach vier Stunden Karenz als `STALE_UNCONFIRMED` beendet.
- Veraltete Alarm- und Scheduler-Einträge werden entfernt; der historische Datensatz bleibt erhalten.

## Providerfehler

- Technische Fehler werden persistent und providerweit statt titelbezogen gezählt.
- Eine sichtbare Fehlermeldung setzt mindestens drei aufeinanderfolgende Fehlschläge über mindestens zehn Minuten voraus.
- Ein sechsstündiger Cooldown verhindert Benachrichtigungsfluten.
- `AVAILABLE` und `NOT_AVAILABLE_YET` setzen den Fehlerzustand zurück.

## Staffel- und Providerwahl

- Room 26 speichert kanonische Anime-Staffeln, Provider-Staffel-Mappings und Nutzerpräferenzen getrennt.
- Die Providerwahl kann pro Anime und pro Staffel gespeichert oder auf Automatik zurückgesetzt werden.
- Crunchyroll wird nur automatisch bevorzugt, wenn die konkrete Staffel im deutschen Providerdatenweg bestätigt ist.
- Staffelchips, Prüfadapter und Providerlink verwenden dieselbe effektive Staffel-/Providerzuordnung.

## Entdecken-Katalog

- Der JustWatch-Gesamtkatalog wird nicht mehr ungefiltert in Entdecken sichtbar.
- Erlaubt sind sicher erkannte Anime-Serien, Anime-Filme und explizit belegte Live-Action-Adaptionen.
- Gewöhnliche Filme wie `IF: Imaginäre Freunde`, `9 Ways to Hell [OV]` und `A Cure for Wellness` werden ausgeblendet.
- Eine bloße Zuordnung zum Genre Animation reicht bewusst nicht als Anime-Nachweis.
- Genre- und Anbieterchips werden nur aus den erlaubten Ergebnissen aufgebaut.
- Die globale Suche bleibt technisch getrennt; diese Korrektur betrifft den kuratierten Bereich Entdecken.
- Der Katalogabruf ist von der synchronisierten Providerpipeline entkoppelt, damit laufende Episodenprüfungen den Entdecken-Refresh nicht blockieren.
- Ein Fehler in der Erfolgszuweisung (`OK` wurde durch einen Elvis-Ausdruck wieder zu `REFRESH_FAILED`) wurde korrigiert.
- Genres und Provideranreicherung laufen nach einem erfolgreichen Katalogabruf separat und zeitlich begrenzt.

## Zeichensatz

- Deutsche Ressourcen und Quelltexte wurden auf Mojibake-Muster geprüft.
- Die Dokumentation wurde ebenfalls einbezogen und die `CHANGELOG.md` als korrektes UTF-8 wiederhergestellt.

## Daten- und Datenschutzwirkung

- Die Installation erfolgte mit `adb install -r`; vorhandene App-Daten und Favoriten wurden nicht gelöscht.
- Das Ausgabepaket enthält keine lokale Room-Datenbank, ADB-Dumps, Kontodaten oder Secrets.

---

## Ursprünglicher Bericht: V0.25.5_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.5 – Validierungsbericht

Stand: 21.08.2026

## Automatisierte Prüfung

- Befehl: `gradlew.bat testDebugUnitTest assembleDebug`
- Ergebnis: `BUILD SUCCESSFUL`
- JVM-Tests: 290
- Fehler: 0
- Übersprungen: 0
- Debug-APK: erfolgreich erzeugt
- Neue Katalogtests: normale Filme abgewiesen; Anime-Filme, Anime-Anbieter, belegte Live-Action-Adaptionen und vom Releaseweg bestätigte Titel zugelassen.

## Reales Android-Gerät

- Verbundenes Android-Gerät per ADB erkannt
- Installation: `adb install -r` erfolgreich
- App-Start: erfolgreich, kein Startabsturz
- Room-Migration der bestehenden Installation: erfolgreich im praktischen Starttest
- Entdecken geöffnet und UI-Dump ausgewertet
- `IF: Imaginäre Freunde`: nicht mehr vorhanden
- `9 Ways to Hell [OV]`: nicht mehr vorhanden
- `A Cure for Wellness`: nicht mehr vorhanden
- Tatsächlicher Anime-Titel samt Crunchyroll-Anbietern weiterhin sichtbar
- Reale JustWatch-GraphQL-Antwort im Gerätelog als `Success` bestätigt
- Falsche Meldung "JustWatch-Daten konnten momentan nicht aktualisiert werden" nach dem finalen Fix: nicht vorhanden
- Screenshot und 720p-Geräteclip liegen den Testartefakten bei.

## Zeichensatzprüfung

- Relevante Kotlin-, XML-, Markdown-, Text- und JSON-Dateien wurden auf typische UTF-8-Mojibake-Muster geprüft.
- In den zu veröffentlichenden Quellen und Dokumenten verbleiben keine bekannten Treffer.
- Die sichtbare Geräteoberfläche zeigt Umlaute korrekt.

## Einschränkung

- Der instrumentierte Room-Migrationstest konnte in dieser Sitzung wegen einer lokalen PKIX-Zertifikatsprüfung beim Auflösen einer Google-UTP-Abhängigkeit nicht separat ausgeführt werden. Kompilierung, JVM-Tests, APK-Build und die Migration der real installierten App waren erfolgreich.
- Eine reale Release-Latenzmessung kann nur bei einem tatsächlich fälligen Release erfolgen und wird nicht simuliert.

---

## Ursprünglicher Bericht: V0.25.5_AUSGABE_HINWEISE.txt

AniSentinel v0.25.5

Zu installieren:
AniSentinel-v0.25.5-DIAGNOSETEST.apk

Die Installation mit "adb install -r" erhält die vorhandenen App-Daten.

Enthalten:
- installierbare Diagnose-APK
- vollständiger bereinigter Sourcecode-Ordner
- Sourcecode-ZIP
- Änderungsbericht
- Validierungsbericht
- Gerätescreenshot
- 720p-Geräteclip
- SHA-256-Prüfsummen

Nicht enthalten:
- lokale Room-Datenbanken
- Favoriten oder andere persönliche App-Daten
- ADB-UI-Dumps
- Secrets, Zugangsdaten oder lokale SDK-Konfiguration

---

## Ursprünglicher Bericht: V0.25.4_AENDERUNGSBERICHT.md

# AniSentinel v0.25.4 – Änderungsbericht

Stand: 21.08.2026

## Ergebnis

Der Diagnosebuild verbessert die staffelgenaue Episodenzuordnung und die direkte Providerprüfung für ADN und Crunchyroll. Staffelwechsel, Episodennummern, Verfügbarkeitsstatus und Provider-Deep-Links werden nun gemeinsam ausgewertet.

## Änderungen

- Staffelwahl in der Detailansicht ergänzt.
- ADN-Episoden werden auf die ausgewählte Staffel normalisiert. Fortlaufende Provider-Nummern wie 13–20 werden dadurch korrekt als Staffel 2, Folgen 1–8 dargestellt.
- Für Hell Mode zeigt Staffel 2 derzeit nur die bereits veröffentlichten Folgen 1–7. Folge 8 bleibt bis zum Release ein zukünftiger Kalendereintrag.
- Wird eine spätere Folge sicher als verfügbar bestätigt, können ältere, noch ungeprüfte Folgen derselben Staffel als verfügbar abgeleitet werden. Die UI kennzeichnet diese Ableitung ausdrücklich.
- Crunchyroll-Historienimporte stellen frühere Staffeln bereit; die aktuelle Staffel bleibt mit den aktuellen Release-Daten verbunden.
- Ein sicher bestätigtes Providerergebnis hat Vorrang vor einem späteren technischen Fehler eines zusätzlichen Prüfwegs.
- Clevatess wird mit Staffel 1, Folgen 1–12, und Staffel 2 bis einschließlich Folge 6 dargestellt. Staffel 2, Folge 6 wird als verfügbar erkannt.
- Historische Episodenkarten verwenden die konkrete gespeicherte Episoden-URL. Ein Klick auf Staffel 1, Folge 12 öffnet dadurch Folge 12 statt Folge 1.
- Sprachzuordnung für Crunchyroll verschärft: GER_SUB setzt japanische beziehungsweise nicht abweichend lokalisierte Audiospur plus deutsche Untertitel voraus; französische Audiospuren werden nicht mehr als deutsche OmU-Bestätigung akzeptiert. GER_DUB verlangt eine deutsche Audiospur.
- Hintergrundprüfungen wurden gegen wiederholte Prüfstürme abgesichert.
- Diagnoseprotokollierung und Tests für Providerpipeline, Fehlerbenachrichtigungen sowie öffentliche Provideradapter ergänzt.

## Auswirkungen

- Staffel- und Episodennummern bleiben zwischen Kalender, Detailansicht und Providerseite konsistent.
- Bereits veröffentlichte Folgen werden nicht mehr als zukünftige Episoden einer falschen Staffel dargestellt.
- Providerbuttons führen zuverlässiger zur gewählten Folge und Sprache.
- Technische Fehler eines sekundären Adapters überschreiben keine bereits bestätigte Verfügbarkeit.
- Die Lösung ist generisch implementiert und enthält keine titelbezogenen Produktions-Hardcodes.

## Grenzen

- Öffentliche Providerseiten können ihr Markup jederzeit ändern; Parserfehler bleiben deshalb als `CHECK_FAILED` sichtbar.
- Die App enthält keine Wiedergabe-, Download-, DRM- oder Login-Funktion.
- Der Diagnosebuild dient der realen Validierung öffentlicher Metadatenwege. Eine öffentliche Produktfreigabe der Adapter bleibt getrennt zu entscheiden.

---

## Ursprünglicher Bericht: V0.25.4_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.4 – Validierungsbericht

Stand: 21.08.2026

## Build und automatisierte Tests

- `testDebugUnitTest`: erfolgreich
- `assembleDebug`: erfolgreich
- Ergebnis: `BUILD SUCCESSFUL`, 44 ausgeführte beziehungsweise geprüfte Gradle-Tasks
- Debug-APK: `app/build/outputs/apk/debug/app-debug.apk`

## Geräteprüfung

- Testgerät: Samsung SM-S928B über ADB
- Installation als Update mit Erhalt der lokalen App-Daten
- Staffelumschaltung in der Detailansicht geprüft
- Hell Mode, Staffel 2: Folgen 1–7 sichtbar; Folge 8 bleibt als kommender Release außerhalb der veröffentlichten Episodenliste
- ADN-Zuordnung geprüft: Staffel 2, Folge 7 verwendet den konkreten Providerdatensatz; die folgende Episode bleibt dem kommenden Release zugeordnet
- Clevatess: Staffel 1 mit Folgen 1–12 sowie Staffel 2 bis Folge 6 sichtbar
- Clevatess, Staffel 2, Folge 6: als verfügbar bestätigt
- Deep-Link-Test Clevatess, Staffel 1, Folge 12: öffnet die konkrete Seite `E12 – Die siegreiche Rückkehr des Königs`, nicht Folge 1
- Bildschirmaufnahme in 720p und Screenshot liegen dem Ausgabeordner bei

## Regressionstests

- ADN-Staffeln und fortlaufende Episodennummern
- Crunchyroll-Katalogauflösung und konkrete Episoden-URLs
- Ausschluss französischer Audiospuren als GER_SUB-Nachweis
- Provider-first-Pipeline und Priorität bestätigter Ergebnisse
- Fehlerbenachrichtigungsrichtlinie
- Episode-Card-Auflösung und abgeleitete Verfügbarkeit älterer Folgen

## Datenschutz- und Paketprüfung

- Keine Room-Datenbanken, WAL-/SHM-Dateien oder Gerätedumps im Sourcecodepaket
- Keine `local.properties`, Keystores, Git-Metadaten oder privaten Gerätepersistenzen im Paket
- Keine Secrets oder Zugangsdaten vorgesehen
- Komplettarchiv wird nach Erstellung entpackt und erneut auf Ausschlussmuster geprüft
- Größenlimit: Komplett-ZIP muss unter 512 MB bleiben

## Offene Punkte

- Öffentliche Provider-Webstrukturen bleiben extern veränderlich und müssen weiter mit Fixtures überwacht werden.
- Nicht eindeutig bestätigbare Sprachfassungen bleiben bewusst unbestätigt.
- Der Diagnosebuild enthält experimentelle direkte Providerprüfungen; die Produktkonfiguration bleibt separat abzusichern.

---

## Ursprünglicher Bericht: V0.25.3_AENDERUNGSBERICHT.md

# AniSentinel v0.25.3 – Änderungsbericht

Stand: 16.08.2026

Basis: v0.25.2 / versionCode 48 / Room 25

Ergebnis: v0.25.3 / versionCode 49 / Room 25

## Umgesetzt

- Der Fälligkeitsalarm setzt intern nur noch `DUE` und startet den Providercheck ohne Notification.
- `ReleaseDue` wird auch in der Notification-Engine ausdrücklich unterdrückt.
- Eine erstmals bestätigte Verfügbarkeit wird atomar und semantisch dedupliziert gemeldet; Text enthält Anime-Titel, Folge und Anbieter.
- Ein echter technischer Pipelinefehler wird pro Release dedupliziert mit Anime-Titel und „Anbieterprüfung fehlgeschlagen“ gemeldet.
- `NOT_AVAILABLE_YET`, laufende Checks und ergebnislose Wiederholungen bleiben still.
- Historisch nachgeladene Verschiebungen werden nach ihrem neuen Releasezeitpunkt gespeichert, aber nicht mehr nachträglich als neu benachrichtigt.
- Die operative Verschiebungsseite verwendet eine Room-Abfrage, die nur offene Fälle ohne bestätigte Providerverfügbarkeit liefert. Historische Datensätze bleiben unverändert erhalten.
- Alle sieben Statistik-Karten sind anklickbar und zeigen ihre Datengrundlage als mobile Liste.
- Statistikzähler werden unmittelbar aus genau den Listen berechnet, die beim Antippen angezeigt werden.
- Die Verschiebungsstatistik verwendet die vollständige `release_postponements`-Historie statt eines abweichenden Release-Statuszählers.
- Datumswerte ohne belegte Uhrzeit zeigen kein künstliches `00:00`. Eine wiederkehrende Titel-/Staffel-/Sprachzeit kann als `DERIVED_TITLE_PATTERN` aufgelöst werden; reale Mitternacht verlangt `EXACT_MIDNIGHT`.
- v0.25.2-Korrekturen für deutsche Metadaten, Genres, Episodendeduplizierung, breite Aktionen und begrenztes Pull-to-Refresh bleiben erhalten.

## Auswirkungen

- Keine störende Push-Meldung mehr beim bloßen Beginn einer Prüfung.
- Keine verspätete Push-Meldung für bereits abgelaufene Verschiebungen.
- Aktive Verschiebungen und historische Auswertung sind klar getrennt, ohne Daten zu löschen.
- Jede Statistikzahl ist direkt durch ihre Liste nachvollziehbar.
- Unbekannte Uhrzeiten werden ehrlich als Datum dargestellt, statt Mitternacht vorzutäuschen.

## Datenhaltung

Room bleibt auf Schema 25. Es gibt keine destruktive Migration. Favoriten und bestehende lokale Daten bleiben beim Update erhalten.

---

## Ursprünglicher Bericht: V0.25.3_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.3 – Validierungsbericht

Stand: 16.08.2026

## Automatisierte Prüfung

- `testDebugUnitTest`: erfolgreich
- `assembleDebug`: erfolgreich
- 55 Testsuiten / 262 Tests / 0 Fehler / 0 übersprungen (finaler Lauf)
- Neue Regressionen: stille Fälligkeit, Providerfehlertext, historische Verschiebungsunterdrückung, offene/archivierte Verschiebungen, abgeleitete Zeit, echte Mitternacht, Statistikzahl entspricht Liste.

## Reales Gerät

- Installation per `adb install -r`: erfolgreich; App-Daten wurden nicht gelöscht.
- Installiert: versionCode 49 / versionName 0.25.3.
- Startseite mit vorhandenen lokalen Daten geöffnet.
- Alle sieben Statistik-Kategorien angetippt und die jeweilige Listenansicht geprüft:
  - Releases heute
  - Releases diese Woche
  - GER SUB gesamt
  - GER DUB gesamt
  - Bestätigt verfügbar
  - Verspätete Releases
  - Verschobene Releases
- Reale Statistikwerte auf dem Gerät: Heute 16, Woche 92, GER SUB 1752, GER DUB 550, bestätigt 2151, verspätet 0, verschoben 8.
- Verschiebungsseite geprüft: bestätigte abgeschlossene Einträge werden ausgeblendet; vergangene, aber unbestätigte Fälle bleiben offen.
- Ein unbekannter Mitternachts-Platzhalter wurde sichtbar nur als Datum dargestellt.
- Logcat-Abzug nach Navigation: keine `FATAL EXCEPTION` / kein `AndroidRuntime`-Absturz.

## Benachrichtigungsgrenze der Geräteprüfung

Während des begrenzten Prüffensters trat kein neuer realer fälliger Providerrelease und kein echter Live-Providerfehler ein. Diese Übergänge wurden deshalb deterministisch durch Unit-/DAO-Regressionstests validiert; es wurden keine künstlichen Nutzerbenachrichtigungen und keine Fake-Releases auf dem Gerät erzeugt.

## Belege

- `Screenshots/home.png`
- `Screenshots/statistics.png`
- `Screenshots/postponements.png`
- `Screenrecord/AniSentinel-v0.25.3-validation-720p.mp4`

Der Screenrecord ist 720 × 1560 Pixel groß und bewusst kompakt gehalten.

---

## Ursprünglicher Bericht: V0.25.2_AENDERUNGSBERICHT.md

# AniSentinel v0.25.2 – Änderungsbericht

Stand: 14.08.2026

## Umgesetzt

- JustWatch-Beschreibungen werden HTML-dekodiert, sprachlich erkannt und nur deutsch angezeigt.
- Reale englische Beschreibungen können über einen gekennzeichneten Übersetzungsweg ins Deutsche übertragen werden; bei fehlenden Daten wird nichts erfunden.
- Genres werden semantisch normalisiert und dedupliziert. Leere Studios werden ausgeblendet.
- Episodenkarten werden pro Episodennummer dedupliziert.
- Bestätigte Episodenverfügbarkeit überstimmt eine veraltete Titelzusammenfassung „noch nicht geprüft“.
- Technische Fehlercodes bleiben in Diagnosedaten erhalten, erscheinen aber nicht mehr roh in normalen Oberflächen.
- Episodenaktionen sind breit untereinander angeordnet; „Verfügbarkeit jetzt prüfen“ erscheint nur bei neuesten unbestätigten Folgen.
- Verschiebungen erscheinen in Start, Kalender, Favoriten, Entdecken, Suche und normaler Detailseite kompakt innerhalb der Titelkarte. Die ausführliche rote Karte bleibt der Verschiebungsdetailseite vorbehalten.
- Pull-to-Refresh wurde ergänzt und auf höchstens acht sichtbare relevante Titel sowie 30 Sekunden begrenzt.
- Der Providerabgleich dedupliziert pro Anime und arbeitet beim manuellen Refresh keinen Gesamtkatalog mehr ab.
- Room wurde auf Schema 25 migriert; JustWatch-Beschreibungsprovenienz bleibt erhalten.

## Datenquellenregel

Kalenderkandidaten stammen aus AniWorld und der aktuellen Season. AniSearch darf ergänzend ausschließlich dieselben deutschen AniWorld-Titel und nur GER_SUB/GER_DUB liefern. JustWatch reichert nur diese Kandidaten mit Metadaten und Anbieterzuordnung an.

## Auswirkungen

- Kein dauerhaft laufender Refreshindikator mehr.
- Keine widersprüchlichen Verfügbarkeitsaussagen zwischen Titel- und Episodenebene.
- Keine großflächigen Verschiebungsblöcke mehr zwischen normalen Titelkarten.
- Bestehende lokale Daten und Favoriten werden bei Updates nicht gelöscht.

## Offen / extern blockiert

- Crunchyroll blockiert den anonymen Live-Diagnoseweg auf dem Testgerät derzeit per Cloudflare/HTTP 403. Vorhandene Room-Daten bleiben erhalten; die normale UI zeigt eine neutrale Meldung.
- AniSearch-Kalenderintegration bleibt bis zur Verifikation echter öffentlicher deutscher Fixtures offen.

---

## Ursprünglicher Bericht: V0.25.2_VALIDIERUNGSBERICHT.md

# AniSentinel v0.25.2 – Validierungsbericht

Stand: 14.08.2026

## Build und Unit-Tests

- `testDebugUnitTest assembleDebug`: erfolgreich
- 52 Testsuiten, 254 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
- APK-Version: 0.25.2 (versionCode 48)

## Gerätetest

- Gerät: Samsung SM-S928B, Installation per `adb install -r` ohne Löschen der App-Daten
- Start-/Refreshprüfung: UI nach 38 Sekunden wieder bedienbar, Aktualisieren-Button aktiv, 80 gespeicherte Titel erhalten
- Fünf tatsächlich erkannte AniSentinel-Detailseiten geöffnet
- Jede Detailseite mit 14–16 Wischbewegungen bis zum Ende geprüft
- Logcat: 0 `FATAL EXCEPTION`
- Deutsche Synopsis auf realem Titel sichtbar

## Instrumented Tests

- Test-APK erfolgreich direkt per ADB installiert und AndroidJUnitRunner gestartet
- 14 Room-Migrationstests erfolgreich
- 10 Golden-UI-Tests erfolgreich
- Crunchyroll-Live-Diagnosetests extern blockiert: Cloudflare/HTTP 403
- Anschließender Navigationstestprozess wurde vom Instrumentation Runner beendet

Der Stand wird deshalb nicht als vollständig fehlerfreie Provider-Liveintegration bezeichnet. Die lokale Architektur, Migrationen, Unit-Tests und der reale Mehrtitel-Praxistest sind erfolgreich; der anonyme Crunchyroll-Liveweg bleibt extern blockiert.

## Datenschutz

Der Ausgabeordner enthält keine lokale Room-Datenbank, keine Favoriten, keine Gerätekennung, keine Secrets und keine privaten Nutzerdaten.

---

## Ursprünglicher Bericht: V12_VALIDIERUNGSBERICHT.md

# AniSentinel v0.12.0 DIAGNOSETEST – Validierungsbericht

Stand: 09.08.2026  
Gerät: Samsung SM-S928B  
Paket: `de.anisentinel.app`  
Version: `versionCode=24`, `versionName=0.12.0-v12-diagnostic`

## Umgesetzte V12-Korrekturen

### Navigation

- `Einstellungen` verwendet `maxLines = 1`, `softWrap = false` und adaptive Schriftgrößen.
- Auf 1080×2340 vollständig einzeilig sichtbar.
- Zusätzlich auf 720×1560 vollständig einzeilig sichtbar; kein Abschneiden des letzten Buchstabens.

### Favoriten

- `Aktuell`: ausschließlich GER-SUB-/GER-DUB-Termine des heutigen lokalen Kalendertages.
- `Demnächst`: ausschließlich Termine ab morgen.
- `Abgeschlossen`: kein heute/zukünftig aktiver Termin und zusätzlich `STOPPED` oder nachgewiesenes Erreichen der bekannten Episodenzahl.
- Eigene Leerzustände für Alle, Aktuell, Demnächst und Abgeschlossen.
- Persistierte Sortierungen: nächster/spätester Release, Titel A–Z/Z–A, Anbieter A–Z/Z–A.
- Standard bleibt „Nächster Release zuerst“; unbekannte Termine bleiben am Ende.

### Entdecken

- Eigener `DiscoverViewModel`; keine Wiederverwendung des `HomeViewModel`.
- Reale JustWatch-DE-Genres und deutsche Übersetzungen.
- Room-Cache für reale Genres und JustWatch-Titel.
- Filter für Serien, Filme, laufend, abgeschlossen, GER SUB, GER DUB und Anbieter.
- Sortierung nach Relevanz, realer JustWatch-Popularitätsposition, Jahr und Titelrichtung.
- Treffer öffnen die bestehende Detailseite und können dort favorisiert werden.
- Keine Demo-Genres oder Demo-Titel.

### Globale Suche

- Room-Cache liefert vorhandene Treffer unmittelbar.
- Ausdrückliche Suche lädt weitere reale JustWatch-DE-Titel.
- Reale Titel, Erscheinungsjahr, Typ, Poster und deutsche Anbieter werden gespeichert.
- Gerätetest mit `Naruto`: älterer Titel, Cover und unter anderem Crunchyroll/Prime-Video-Angebote sichtbar.
- Normale Karten zeigen Anbieter, aber keine technischen Quellenkennzeichnungen.

### Verschiebungen und V11-Kern

- Kalenderkarte zeigt bei vorhandener Historie: „Verschoben“, alten Termin, neuen Termin und Grund.
- V11-Providercheck, AniWorld-Fallback, AlarmManager und WorkManager blieben erhalten.
- Fehler/Parseränderungen erzeugen weiterhin keine falsche bestätigte Verspätung.

## Technische Validierung

- `testDebugUnitTest`: erfolgreich, 134 Tests.
- `assembleDebug`: erfolgreich.
- Room-Schema 14 exportiert.
- Updateinstallation über bestehende V11-/V12-Datenbanken erfolgreich.
- Migrationen 12→13 und 13→14 ohne Room- oder AndroidRuntime-Absturz.
- Reale JustWatch-Genreabfrage lieferte unter anderem Action & Abenteuer, Animation, Fantasy, Drama und Science-Fiction.
- Reale globale Naruto-Suche auf dem Gerät erfolgreich.
- Geräteclip in 720p erstellt.

## Beigefügte Nachweise

- Bottom Navigation auf Zielgerät.
- Bottom Navigation bei 720×1560.
- Globale Naruto-Suche mit realem Cover und Anbietern.
- Genre-Browser mit realen Genres, Filtern und Titelkarten.
- Favoritenansicht mit Filtern und Sortierrichtungen.

## Ehrlich noch nicht endvalidiert

- Ein tatsächlich eintretender Release wurde in diesem Arbeitszeitfenster nicht über Stunden bei gesperrtem Gerät abgewartet. Die Benachrichtigung ohne App-Öffnung bleibt daher eine reale zeitabhängige Abnahme.
- Es trat während dieses Laufs kein neuer realer AniWorld-Verschiebungsfall auf. Die Darstellung ist implementiert, konnte aber nicht mit neu eingehenden Live-Daten belegt werden.
- Crunchyroll GER SUB, GER DUB, blockierter Direktcheck und der +10-Minuten-Fallback müssen weiterhin am realen Releasetag gemeinsam protokolliert werden.
- JustWatch ist eine inoffizielle Diagnoseintegration. Vor einer öffentlichen Releaseaktivierung bleiben Nutzungserlaubnis und Stabilität zu klären.
- JustWatch liefert das Genre „Animation“, aber keine verlässliche Herkunftsaussage „japanischer Anime“. AniSentinel erfindet diese Zuordnung nicht.

## Grenzen

Keine Fake-Daten, Logins, Streaming-, Download-, Playback-, DRM- oder Manifestfunktionen. Technische Fehler bleiben sichtbar und löschen keine vorhandenen Room-Daten.

---

## Ursprünglicher Bericht: V11_VALIDIERUNGSBERICHT.md

# AniSentinel v0.11.0 DIAGNOSETEST – Validierungsbericht

Stand: 2026-08-09, Gerät: Samsung SM-S928B, Android-Paket `de.anisentinel.app`

## Umgesetzt

- Release-spezifischer Anbietercheck `checkEpisode(releaseId)`; eindeutiger Jobname
  `anisentinel.provider-check.<releaseId>`.
- JustWatch-Titeltreffer, fehlender JustWatch-Episodeneintrag und direkter
  Anbieterstatus sind getrennte Zustände.
- Direkter öffentlicher Crunchyroll-Prüfer mit Titel-/Staffel-/Folgenabgleich und
  vorsichtiger Sprachmetadatenauswertung.
- Keine Login-, Streaming-, Playback-, DRM- oder Manifestzugriffe.
- AniWorld-Metadatenfallback frühestens zehn Minuten nach dem Solltermin.
- `DELAYED_CONFIRMED` nur, wenn direkter Anbietercheck und AniWorld-Fallback beide
  belastbar negativ sind. Parser-, Netzwerk- und Regionsfehler bleiben
  `OVERDUE_UNCONFIRMED`/Fehler und erzeugen keine falsche Verspätungsmeldung.
- `GER_SUB` und `GER_DUB` bleiben getrennte Releasezeilen; Zeit und Folge gehören
  zum logischen Schlüssel.
- Countdown auf Start-, Kalender- und Detailkarten; nach Ablauf wird kein
  irreführendes `Noch 00:00:00` mehr angezeigt.
- Favoritenfilter nutzen den tatsächlichen Releasezeitpunkt. Die Sortierung nach
  nächstem Release, Titel oder Anbieter wird in DataStore gespeichert.
- Benachrichtigungen enthalten, soweit vorhanden, Anime-Titel, Staffel und Folge.
- WorkManager plus AlarmManager-Wecksignal; Neuplanung nach Boot, App-Update,
  manueller Zeitänderung und Zeitzonenänderung.

## Automatische Prüfung

- `testDebugUnitTest`: erfolgreich, 131 Tests.
- `assembleDebug`: erfolgreich.
- Installierte Version: `versionCode=23`, `versionName=0.11.0-v11-diagnostic`.
- App-Start auf SM-S928B erfolgreich; kein `AndroidRuntime`-Absturz im Starttest.
- `dumpsys alarm`: mehrere `RTC_WAKEUP`-Alarme mit
  `de.anisentinel.app.action.RELEASE_DUE` registriert.
- `dumpsys jobscheduler`: AniWorld-, JustWatch-, Anbieter- und Favoritenjobs
  registriert.

## Neue Regressionstests

- Crunchyroll: deutsche Untertitel, Parserambiguität, nachgewiesene Folge außerhalb
  der Gesamtzahl.
- AniWorld-Fallback: getrennte deutsche Sub-/Dub-Signale und geändertes Markup.
- Statusmodell: fehlende Episode und technische Fehler behaupten keine Verspätung;
  fehlende Sprachbelege bleiben unbekannt.

## Ehrlich offen

- Der beigefügte Geräteclip belegt Navigation und den sichtbaren aktuellen Stand,
  aber keinen über Stunden abgewarteten realen Releasezeitpunkt.
- Eine zeitgenaue Benachrichtigung bei gesperrtem Gerät/Doze ist technisch geplant
  und im Android-Scheduler nachgewiesen, muss jedoch noch über einen tatsächlich
  eintretenden AniWorld-Termin mit Zeitstempeln endvalidiert werden.
- Der öffentliche Crunchyroll-Checker kann nur Daten bestätigen, die auf der frei
  erreichbaren Seite vorhanden sind. Login-/Regionssperren und dynamisch fehlendes
  Markup werden ehrlich als Fehler behandelt.
- Globale JustWatch-Katalogsuche und belastbare genrebasierte Vollkatalogansichten
  sind in diesem Korrekturstand noch nicht abgeschlossen.
- Screenshot-Golden- und Accessibility-Instrumentationstests wurden in diesem Lauf
  nicht auf dem Gerät ausgeführt.

## Datenschutz und Grenzen

Keine Secrets, Konten oder Zugangsdaten. Keine Streaming-, Download-, Playback-,
DRM- oder Manifestfunktion. AniWorld und die inoffizielle JustWatch-Anbindung bleiben
Diagnosefunktionen und dürfen vor geklärter Nutzungserlaubnis nicht ungeprüft in eine
öffentliche Releasevariante übernommen werden.

---

## Ursprünglicher Bericht: AniSentinel_0.10.0_VALIDATION.md

# AniSentinel 0.10.0 – Validierung

Stand: 01.08.2026

## Erfolgreich

- 65 lokale Tests
- Debug-APK gebaut
- Android-Test-APK gebaut
- Room-Schema v4 exportiert
- Fixture-Tests unterscheiden Verfügbarkeit, fehlende Episode, Login und Netzwerkfehler

## Implementiert

- strukturierter Provider-Checker-Vertrag
- Crunchyroll-Simulcast-Kalender als erster öffentlicher Prüfpfad
- persistente Anbieterreferenzen und Prüfergebnisse
- `firstAvailableAt`, `checkedAt`, Status, Episode, URL und Fehlergrund
- Detaildarstellung und manueller Prüfbutton bei gespeicherter Crunchyroll-Zuordnung

## Bewusste Schutzregeln

- Terminüberschreitung erzeugt niemals `AVAILABLE`.
- Serientreffer ohne erwartete Episode ergibt `TITLE_FOUND_EPISODE_MISSING`.
- Netzwerkfehler ergibt niemals `TITLE_NOT_FOUND`.
- Ohne echte Anbieterzuordnung wird keine Website angefragt.
- Keine Video-, Playback-, Manifest-, Token- oder DRM-Endpunkte.

## Nicht erfüllt / extern blockiert

- AniSearch-Anime-Metadaten benötigen laut offizieller API-Seite eine individuelle Vereinbarung.
- Daher existiert noch keine automatische echte AniSearch-Anbieterzuordnung.
- WorkManager-Staffelung und produktive Verfügbarkeitsbenachrichtigungen fehlen noch.
- Ein erfolgreicher echter Crunchyroll-Request wurde in der isolierten Android-Umgebung nicht bestätigt;
  der Parser ist mit gespeicherten HTML-Fixtures getestet.
- Instrumentierte Tests wurden mangels verbundenem Gerät nicht ausgeführt.

v0.10.0 ist damit eine belastbare Anbieterprüfungs-Grundlage, erfüllt aber ausdrücklich
noch nicht sämtliche Akzeptanzkriterien des Kernfunktionsauftrags.

---

## Ursprünglicher Bericht: AniSentinel_0.9.1_VALIDATION.md

# AniSentinel 0.9.1 – Validierung

Stand: 30.07.2026

## Build

- `testDebugUnitTest`: erfolgreich, 61 Tests
- `assembleDebug`: erfolgreich
- `assembleDebugAndroidTest`: erfolgreich
- Version `0.9.1`, VersionCode `20`

## Fachliche Korrekturen

- Ein Termin vor `Clock.instant()` ergibt `RELEASE_TIME_REACHED`, nicht `SCHEDULED`.
- Ein überschrittener Termin ergibt ohne Anbieterprüfung niemals `AVAILABLE`.
- Nur `ProviderAvailability(status = AVAILABLE)` bestätigt Verfügbarkeit.
- Home, Karten, Detailseite und Kalender verwenden denselben Resolver.
- Abgelaufene Termine umgehen beim automatischen AniList-Refresh die normale Cache-TTL.
- Metadatenquelle und Streaming-Anbieter sind getrennte Domainmodelle.
- AniList-Datensätze besitzen `https://anilist.co/anime/{id}` als überprüfbaren Quellenlink.

## Kalender

- jeder gültige Tag ist auswählbar
- Abfrage über echte Room-Zeitfenster
- Tagesgrenzen werden in der lokalen Gerätezitzone berechnet
- Tage mit gespeicherten Terminen erhalten eine Markierung
- Monatsnavigation vorwärts/rückwärts
- gewählter Tag zeigt ausschließlich passende Anime oder einen ehrlichen Leerzustand

## Tests

- vergangener Termin
- zukünftiger Termin
- keine automatische Verfügbarkeitsbehauptung
- bestätigte Anbieter-Verfügbarkeit
- nächste Kalenderwoche über Jahreswechsel
- Room-Abfrage mit zwei passenden und einem nicht passenden Termin
- leerer Room-Zeitraum

## Grenzen

- keine echte Streaming-Anbieterquelle angebunden
- Anbieter-Verfügbarkeit wird daher weiterhin als nicht geprüft angezeigt
- kein Gerät verbunden; instrumentierte Tests wurden kompiliert, aber nicht ausgeführt
- erfolgreicher externer AniList-TLS-Abruf bleibt in der isolierten Umgebung unbestätigt

---

## Ursprünglicher Bericht: AniSentinel_0.9.0_VALIDATION.md

# AniSentinel 0.9.0 – Validierung

Stand: 30.07.2026

## Build

- `testDebugUnitTest`: erfolgreich, 56 lokale Tests
- `assembleDebug`: erfolgreich
- `assembleDebugAndroidTest`: erfolgreich, 23 instrumentierte Tests kompiliert
- Version: `0.9.0`, VersionCode: `19`

## Echte-Daten-Umstellung

- Home und Entdecken lesen ausschließlich den AniList-Room-Cache.
- Ein leerer oder nicht erreichbarer Katalog erzeugt einen ehrlichen Leer-/Fehlerzustand.
- Kalender zeigt keine aus Fake-Anime abgeleiteten Termine.
- Detailseiten laden ausschließlich vorhandene Room-Datensätze.
- Favoriten lösen Metadaten direkt aus Room auf.
- Produktiver `AppContainer` verwendet keinen Fake-Provider.
- Der Debug-Datenquellenschalter und produktive Fake-Hinweise wurden entfernt.
- Fake-Repositories bleiben ausschließlich als Test-/Preview-Hilfen im Projekt.

## AniSearch-Prüfung

Die AniSearch-Website verweist auf `https://api.anisearch.com/`. Dort wird die öffentliche
API als noch in Entwicklung beschrieben; dokumentiert sind derzeit OAuth-, Benutzer- und
Bewertungsfunktionen. Für projektspezifische Datenzugriffe – darunter die von AniSentinel
benötigten Anime-Metadaten – verweist AniSearch auf eine individuelle Vereinbarung über
`api@anisearch.com`. Eine Weitergabe erhaltener API-Daten an Dritte wird dort ausdrücklich
eingeschränkt.

Deshalb enthält v0.9.0 keinen undokumentierten HTML-Scraper und behauptet keine vorhandene
AniSearch-Integration. Für die nächste Stufe wird ein vom Betreiber gewährter, für die
App-Verteilung geeigneter API-Zugang benötigt.

## Nicht bestätigt

- kein Gerät verbunden; instrumentierte Tests wurden nicht auf einem Gerät ausgeführt
- erfolgreicher AniList-TLS-Abruf bleibt in dieser isolierten Umgebung praktisch unbestätigt
- AniSearch-Animeimport ist mangels freigegebenem API-Zugang nicht implementiert

---

## Ursprünglicher Bericht: AniSentinel_0.8.2_VALIDATION.md

# AniSentinel 0.8.2 – Validierung

Stand: 30.07.2026

## Erfolgreich

- `testDebugUnitTest`
- `assembleDebug`
- `assembleDebugAndroidTest`
- 56 lokale Tests
- Benutzer-APK besitzt weiterhin `MAIN`-/`LAUNCHER`-Activity
- Test-APK wird im Auslieferungspaket räumlich von der Benutzer-APK getrennt
- About-Texte nennen AniList-Webzugriff und die noch fehlende AniSearch-Anbindung korrekt

## AniSearch-Entscheidung

AniSearch besitzt keine dokumentierte öffentliche API. Die öffentliche Website war als
normaler Besucher erreichbar; `robots.txt` und verbindliche Regeln für automatisierte
Abfragen konnten in der Prüfungsumgebung jedoch nicht verlässlich abgerufen werden.
Gemäß `AGENTS.md` wurde deshalb kein Scraper aktiviert und keine scheinbar echte
AniSearch-Suche ausgeliefert.

Vor einer Aktivierung werden benötigt:

1. verifizierte Nutzungs- und Crawler-Regeln beziehungsweise Zustimmung des Betreibers,
2. dokumentierte, konservative Abrufgrenzen,
3. HTML-Fixtures und Parser-Vertragstests,
4. lokaler Cache, User-Agent, Kill-Switch und Fehlertelemetrie ohne personenbezogene Daten.

## Installation

Auf einem Android-Gerät ausschließlich `AniSentinel-v0.8.2-INSTALLIEREN.apk`
installieren. Die Datei unter `tests` ist ausschließlich für instrumentierte Tests.

---

## Ursprünglicher Bericht: AniSentinel_0.8.1_VALIDATION.md

# AniSentinel 0.8.1 – Validierung

Stand: 30.07.2026

## Ergebnis

- `testDebugUnitTest`: erfolgreich, 56 Tests
- `assembleDebug`: erfolgreich
- `assembleDebugAndroidTest`: erfolgreich, 23 instrumentierte Tests kompiliert
- Room-Schemaexporte 1, 2 und 3 vorhanden
- keine Verwendung von `OnConflictStrategy.REPLACE` im Produktionscode

## Fachliche Regressionen

- Fehlendes `nextAiringAt` ergibt `expectedReleaseAt = null`.
- `updatedAt` wird nicht als Releasezeitpunkt verwendet.
- Der aktuelle Trending-Snapshot wird in einer Room-Transaktion ersetzt.
- Ein Anime mit Favorit bleibt auch dann gespeichert, wenn er aus dem Trending-Snapshot fällt.
- Live-Details verwenden AniList-Cover und -Beschreibung und behaupten keine Providerverfügbarkeit.
- Unbekannte Live-Details blenden keinen Fake-Titel ein.
- Live-Home zeigt keine festen Demo-Statistiken und keinen Demo-Watcher.

## Cache und Netzwerk

- automatische Aktualisierung: 30 Minuten TTL
- HTTP 429: `Retry-After` beziehungsweise 60 Sekunden Fallback-Sperre
- Cover: 8 Sekunden Connect-/Read-Timeout, HTTP-Statusprüfung, Speicher-/Diskcache, Downsampling
- Fehlerhafte oder fehlende Cover fallen auf die abstrakte lokale Grafik zurück

## Nicht praktisch bestätigt

Es war kein Android-Gerät verbunden. Daher wurde die neue Migration als Android-Test-APK
kompiliert, aber in diesem Lauf nicht auf einem Gerät ausgeführt. Ebenso wurde ein
erfolgreicher realer TLS-Abruf von AniList in der isolierten Umgebung nicht bestätigt.
Parser, Mapper, Cache- und Fehlerpfad sind fixture-/lokal getestet; dies ersetzt keinen
erfolgreichen End-to-End-Netzwerktest in einem normalen Gerätenetz.

## Bewusste Produktgrenzen

- kein Login oder Abmelden
- keine AniSearch- oder Anbieterabfrage
- keine Streaming-, Download-, Playback- oder DRM-Funktion
- AniList ist ausschließlich eine öffentliche Metadatenvorschau

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V9_2026-08-03.md

# AniSentinel v0.10.0 – Validierungsbericht DIAGNOSETEST V9

## Ergebnis

- Build: erfolgreich (`testDebugUnitTest assembleDebug`)
- Unit-Tests: 121 ausgeführt, 0 Fehler, 0 Fehlschläge, 0 übersprungen
- Gerät: verbundenes Samsung-Galaxy-Gerät, frische Installation und anschließendes Update geprüft
- Kalenderdaten: 79 reale AniWorld-Titel, 195 deutsche GER-SUB-/GER-DUB-Termine im August sichtbar
- JustWatch-Livelauf: 79/79 Titel geprüft; 69 eindeutig, 3 mehrdeutig, 7 ohne sicheren Treffer
- Geräteclip: 720 × 1560, rund 32 Sekunden, etwa 11 MB

## Sichtbar bestätigt

Der Kalender zeigt nach Auswahl eines Tages dieselbe Titelkarte wie die Startseite. Jede Karte
enthält Cover, Titel, Folge, deutsche Sprachfassung, lokale Releasezeit, sekundengenauen Countdown
und bei erfolgreicher Zuordnung den realen JustWatch-DE-Anbieter. Am 3. August wurden unter anderem
folgende Karten im UI-Baum bestätigt:

- `Love Unseen Beneath the Clear Night Sky`, Folge 5, Deutsch (Sub), Countdown,
  `Rakuten Viki · Crunchyroll Amazon Channel`
- `The Insipid Prince's Furtive Grab for the Throne`, Folge 5, Deutsch (Sub), Countdown,
  `Crunchyroll · Crunchyroll Amazon Channel`

Technische Terminquellen, Workerzustände und Diagnosezähler werden nicht mehr in der normalen
Kalenderansicht ausgegeben. GER-SUB und GER-DUB besitzen weiterhin getrennte Release-IDs und werden
auch bei gleichem Datum oder unterschiedlichen Episoden/Uhrzeiten einzeln dargestellt.

## JustWatch-Zuordnung

Die Zuordnung akzeptiert exakte Titel sowie kontrollierte Normalisierung von Akzenten,
Satzzeichen und Staffelzusätzen. Bei mehreren gleich guten Kandidaten wird nur dann automatisch
entschieden, wenn genau ein Kandidat deutsche JustWatch-Angebote besitzt. Andernfalls bleibt der
Anbieter leer; es werden keine Anbieter erfunden.

Offene Titel aus dem geprüften Lauf:

- mehrdeutig: `I Want to Love You Till Your Dying Day`, `Hana-Kimi`, `One Piece`
- kein sicherer Treffer: `Dodgeball Girl Danko`, `From Old Country Bumpkin to Master Swordsman`,
  `Dara-san of Reiwa`, `That Time I Got Reincarnated as a Slime`,
  `Please Excuse My Younger Brothers`, `MAO (2026)`, `Magilumiere Co. Ltd.`

Für diese Fälle werden zusätzliche belastbare Alias-, Jahres- oder externe ID-Metadaten benötigt.
Der nach dem Lauf ergänzte vereinfachte Suchversuch entfernt technische Jahreszusätze wie `(2026)`;
dieser Sonderfall ist gebaut und getestet, wurde aber nicht erneut in einem vollständigen 79-Titel-
Gerätelauf gezählt.

## Grenzen

Der inoffizielle JustWatch-GraphQL-Adapter ist nur im Debug-/Diagnosebuild enthalten. Im Releasebuild
ist er deaktiviert. Die Anbieterzuordnung auf Serien-/Staffelebene belegt, wo ein Titel angeboten
wird; sie ist nicht automatisch ein Nachweis, dass jede konkrete deutsche Episode bereits abrufbar
ist. Eine Episode wird nur bei passender Episoden- und Sprachinformation als verfügbar bestätigt.

Es wurden keine Streaming-, Download-, Playback- oder DRM-Funktionen implementiert.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V8_2026-08-03.md

# AniSentinel v0.10.0 – DIAGNOSETEST V8

## Ergebnis

V8 aktiviert ausschließlich AniWorld und den JustWatch-/Providerpfad. AnimeRadar, AniList und
AniSearch sind im aktiven Netzwerk-, WorkManager- und UI-Pfad deaktiviert. Ihre Klassen bleiben
nur als inaktive Fallback-Bausteine im Quellcode.

## Implementiert

- Room-Schema v10 mit `justwatch_title_matches`, `justwatch_offers` und
  `episode_provider_availability`
- öffentliche AniWorld-Cover-URLs werden validiert, gespeichert und auf Startseite/Karten geladen
- ein gemeinsamer lifecycle-sicherer Sekundentakt aktualisiert die driftfreien Countdowns aller sichtbaren Kalendereinträge
- neutrale JustWatch- und Provider-Domainmodelle
- eindeutige, mehrdeutige und fehlende Titelzuordnung
- getrennte GER-SUB-/GER-DUB-Verfügbarkeit pro Episode und Anbieter
- Zustände `SCHEDULED`, `DUE`, `DELAYED`, `AVAILABLE_GER_SUB`, `AVAILABLE_GER_DUB`,
  `AVAILABLE_GER_SUB_AND_DUB`, `CHECK_FAILED`
- ansteigender Backoff 0/5/10/20/30/60 Minuten
- getrennte Jobs `anisentinel.justwatch-provider-sync` und
  `anisentinel.provider-availability-sync`
- Produktadapter ohne Partnerzugang: `SOURCE_NOT_CONFIGURED`
- Debug-Diagnoseadapter unübersehbar als lokaler Datensatz gekennzeichnet
- allgemeine JustWatch-Listung setzt niemals bestätigte Episodenverfügbarkeit
- `firstAvailableAt` bleibt bei Folgeprüfungen erhalten

## Automatisierte Prüfung

Ausgeführt:

```text
gradlew testDebugUnitTest assembleDebug
```

Ergebnis: **119 Tests, 0 Fehler; Debug-APK erfolgreich gebaut.**

Neue Regressionstests prüfen eindeutiges/mehrdeutiges Matching, Sub/Dub-Trennung,
Fehler-gegen-Verzögerungslogik, Backoff und Room-Persistenz des ersten Erkennungszeitpunkts.

## Galaxy-S24-Ultra-Geräteprüfung

Frische Installation auf `SM-S928B`, anschließend Start, Kalender und App-Neustart:

```text
Kalenderquelle: ANIWORLD_CALENDAR
Deutsche Termine im Monat: 195
JustWatch Live: SOURCE_NOT_CONFIGURED
Lokale Diagnosezuordnungen: 1
Episodenprüfungen: 1
Fehlgeschlagen: 0
```

Nach dem Neustart blieben Zuordnung und Episodenprüfung in Room erhalten. Eine UI-Automation
prüfte Start und Kalender zusätzlich auf `AniList|AnimeRadar|AniSearch`; Ergebnis:
`legacy-visible=False`.

## Ehrliche Grenze

Es liegt kein freigegebener JustWatch-Partnerzugang vor. Deshalb findet kein JustWatch-Liveabruf
statt. Der lokale Diagnosedatensatz beweist ausschließlich den internen Datenfluss und ist keine
Bestätigung realer Anbieter- oder Sprachverfügbarkeit. Eine Produktaktivierung erfordert einen
offiziellen beziehungsweise ausdrücklich freigegebenen Zugang und anschließend echte Fixtures.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V7_2026-08-03.md

# AniSentinel v0.10.0 – DIAGNOSETEST V7

## Ergebnis

V7 ergänzt den funktionierenden internationalen AnimeRadar-Kalender um einen realen deutschen
AniWorld-Kalender und eine getrennte Verschiebungsquelle. Der End-to-End-Lauf auf einem Samsung
Galaxy S24 Ultra war erfolgreich.

## Reale Gerätedaten

- AnimeRadar: 128 empfangen, 128 gespeichert
- AniWorld: 195 deduplizierte deutsche Termine im sichtbaren August-Zeitraum
- akzeptierte Sprachfassungen: ausschließlich GER SUB und GER DUB
- eindeutig angewendete Verschiebung: 1
- Beispiel: „That Time I Got Reincarnated as a Slime“, S04E17, GER SUB
  - alter Termin: 31.07.2026, 17:00 Uhr
  - neuer Termin: 07.08.2026, 17:00 Uhr
- die Dub-Verschiebung wurde nicht auf den Sub-Termin angewendet, da kein exakt passender
  GER-DUB-Kalendereintrag derselben Episode vorhanden war
- Zustand und Historie blieben nach erzwungenem App-Neustart erhalten

## Sprach- und Deduplizierungsregeln

```text
japanese-german.svg → GER_SUB
german.svg          → GER_DUB
alle anderen        → verwerfen
```

GER SUB und GER DUB werden getrennt nach Titel, Staffel, Episode, korrigierter Zeit und Sprache
gespeichert. Eine neue Sub-Folge und eine ältere Dub-Folge derselben Staffel dürfen deshalb am
selben Tag als getrennte Karten erscheinen. Nur echte gleichsprachige Duplikate werden entfernt.

## Zeit und Status

- Originalzeit bleibt erhalten.
- Releasezeit ist verbindlich Originalzeit minus zehn Minuten.
- 23:59 wird zu 23:49 und zusätzlich als möglicher Tagesendmarker gespeichert.
- Termin erreicht bedeutet nicht automatisch Anbieter verfügbar.
- `CONFIRMED_AVAILABLE` wird durch AniWorld nicht gesetzt.

## Persistenz

- Room-Schema v9
- getrennte Quellenreferenzen
- Originalzeit, Korrektur, Sprachfassung und Status am Release
- dauerhafte `release_schedule_history`
- historisierte Releases sind vor quellenbezogener Bereichslöschung geschützt
- Fehler und unvollständige Parsergebnisse löschen keine Daten anderer Quellen

## Hintergrundarbeit

- `anisentinel.release-calendar-sync` – AnimeRadar/AniList-Fallback
- `anisentinel.aniworld-calendar-sync` – deutscher GER-SUB-/GER-DUB-Kalender
- `anisentinel.aniworld-schedule-changes-sync` – eindeutige Verschiebungen
- AnimeRadar und AniWorld sind nur im DIAGNOSETEST standardmäßig aktiv; Release deaktiviert beide

## Build und Tests

- `testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL
- 112 Unit-Tests, 0 Fehler, 0 übersprungen
- Debug-AndroidTest-APK: gebaut
- reale Installation, frischer Datenabruf und Neustartpersistenz: erfolgreich
- 720p-Geräteclip: ungeschnitten, rund 82 Sekunden, 23,88 MB

Der instrumentierte Gradle-Gesamtlauf und Release-Lint bleiben durch die lokale Java-/Gradle-
Zertifikatsumgebung blockiert. Das fehlende UTP-Artefakt konnte wegen PKIX nicht geladen werden.
Der zusätzliche Versuch mit `Windows-ROOT` schlug fehl, weil dieser Truststore-Typ im verwendeten
Android-Studio-JBR nicht verfügbar ist. Dieser externe Infrastrukturfehler wird nicht als
erfolgreicher Test ausgegeben.

## Offene Punkte

1. Verwendungserlaubnis vor einer öffentlichen Aktivierung der Drittquellen klären.
2. PKIX/Truststore lokal reparieren und Release-Lint sowie instrumentierte Gesamttests wiederholen.
3. Mehrdeutige AniWorld-Titel in einem Diagnose-Matchingbereich sichtbar machen.
4. Detailseite um dieselbe internationale/deutsche Quellenzerlegung wie der Kalender erweitern.
5. Providerbestätigung weiterhin getrennt und beweisbasiert implementieren.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V6_2026-08-03.md

# AniSentinel v0.10.0 – DIAGNOSETEST V6

## Ergebnis

Der reale AnimeRadar-Datenweg ist im Diagnosebuild aktiv und auf einem Samsung Galaxy S24 Ultra
vollständig nachgewiesen. Der öffentliche Endpunkt lieferte beim finalen Lauf 128 Termine. Alle
128 wurden validiert, dedupliziert und transaktional in Room gespeichert. Die Zahl ist dynamisch;
die vorangegangene Browserprüfung hatte noch 127 Einträge ergeben.

## Geräteablauf

```text
App-Daten löschen / frischer Start
→ automatischer POST an https://www.animeradar.de/api/anilist
→ reale Titel auf Start sichtbar
→ Kalenderquelle AnimeRadar
→ 128 empfangen / 128 gespeichert
→ Einstellungen öffnen korrekt die Einstellungsseite
→ App erzwingen beenden und neu starten
→ Kalenderdaten und Erfolgsstatus weiterhin vorhanden
→ Releasekalender-WorkManager ENQUEUED
```

Beispiel eines tatsächlich sichtbaren Datensatzes: `Renegade Immortal`, Folge 152. Die App
kennzeichnet ihn als geplanten Termin mit AniList-Metadaten, nicht als bestätigten deutschen
Providerrelease.

## Build und Tests

- `testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**
- 105 Unit-Tests, 0 Fehler, 0 übersprungen
- Debug-APK auf Gerät installiert: erfolgreich
- Debug-AndroidTest-APK: erfolgreich gebaut
- Reale End-to-End-Prüfung und Neustartpersistenz: erfolgreich
- Einstellungen-Navigationsregression: erfolgreich

Der zusätzliche Aufruf `assembleRelease` kompilierte den Release-Code, scheiterte anschließend
bei `lintVitalAnalyzeRelease`, weil das lokale JDK das noch nicht gecachte Google-Artefakt
`lint-gradle:31.7.3` wegen `PKIX path building failed` nicht herunterladen konnte. Aus demselben
externen SSL-Zertifikatgrund konnte Gradle den instrumentierten Lauf nicht starten, da ein
UTP-Host-Artefakt fehlte. Diese beiden Vorgänge sind nicht als erfolgreich ausgewiesen. Der
Debug-Build und sämtliche lokalen Tests waren davon nicht betroffen.

## Datenquellenverhalten

- Debug/DIAGNOSETEST: AnimeRadar standardmäßig aktiv.
- Release: AnimeRadar standardmäßig deaktiviert.
- AniList: nur Ausfall-Fallback für Kalendertermine sowie Metadatenquelle.
- AniSearch: optionaler Metadatenweg, in diesem automatischen Kalenderfluss deaktiviert.
- Provider/Dub: keine Ableitung ohne konkreten Episodenbeweis.
- Unvollständige Pagination oder Fehler: keine Löschung vorhandener Room-Daten.
- Automatischer Hintergrundjob: Netzwerkbedingung, sechs Stunden Intervall, Backoff.
- Automatischer Zeitraum: aktuelle Woche; Monat nur bei explizitem Monatswechsel.

## Artefakte

- Installierbare Debug-Diagnose-APK
- Debug-AndroidTest-APK
- 720p-End-to-End-Geräteclip
- UIAutomator-Nachweise für Start, Kalender, Einstellungen und Neustart
- reale, minimierte AnimeRadar-JSON-Fixture
- Quellenrecherche, Sourcecode, Testberichte und SHA-256-Prüfsummen

## Offene Punkte

1. Erlaubnis zur Drittverwendung vor Aktivierung in einer öffentlichen Produktversion klären.
2. Lokale Java-/Gradle-Zertifikatskette reparieren und danach Release-Lint sowie instrumentierte
   Tests erneut ausführen.
3. Provider- und Dub-Verfügbarkeit nur über einen getrennten, beweisbasierten Adapter ergänzen.
4. Die bestehende Bezeichnung „AniList-Katalog“ auf der Startseite kann später durch eine
   quellenneutrale Produktbezeichnung ersetzt werden; die Releasequelle wird im Kalender bereits
   korrekt separat angezeigt.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V5_2026-08-03.md

# AniSentinel v0.10.0 – DIAGNOSETEST V5

Datum: 03.08.2026  
Gerät: Samsung Galaxy S24 Ultra (`SM-S928B`)  
Status: Diagnose-Testbuild, kein produktiver autonomer Release-Watcher

## Umgesetzt

- AniList-Startgrenze um eine Sekunde erweitert, damit lokale 00:00-Uhr-Termine enthalten sind.
- Gemeinsamer dynamischer `DeviceTimeZoneProvider` für Quelle, Repository, Worker und Kalender-UI.
- Sync-Ergebnisse unterscheiden `UpdatedFromNetwork`, `CacheFresh` und `RetryRequired`.
- Persistenter Source-Cooldown und serverseitige Rate-Limit-Zeit fließen strukturiert in `retryNotBefore` ein.
- Seite 20 mit weiterem `hasNextPage` wird als unvollständiger Abruf abgebrochen; keine Room-Bereinigung.
- Neutrales `SourceEpisodeRelease`; AniList setzt `titleGerman` niemals aus Englisch/Romaji.
- Tatsächlicher WorkManager-Zustand wird über `WorkInfo` beobachtet.
- Debug-only Diagnosejob zeigt kontrolliert `RUNNING → RETRY`, Versuch 1 und frühesten Retry; kein Netzwerkzugriff.
- AniSearch- und Provider-Worker bleiben deaktiviert. Room bleibt sichtbare Kalenderquelle.

## Validierung

- Gradle `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: erfolgreich.
- 97/97 Unit- und Robolectric-Tests: erfolgreich.
- Migrationstests auf dem Samsung: 4/4 erfolgreich.
- Navigationstests auf dem Samsung: 11/11 erfolgreich.
- APK und Test-APK auf dem Samsung installiert.
- UI-Hierarchien belegen `RUNNING`, danach `RETRY`, Versuch 1 und Retry-Zeit.
- 720p-Geräteclip: `AniSentinel-DIAGNOSETEST-V5-720p-RUNNING-RETRY.mp4`.

## Ehrliche Einschränkungen

- Der Diagnosejob simuliert ausschließlich die Statuskette und beweist keinen HTTP-2xx-Liveabruf.
- `SUCCEEDED` wird nur vom realen AniList-Worker nach Netzwerkabruf plus Room-Schreibvorgang gesetzt; `CacheFresh` bleibt getrennt.
- Die vollständige Golden-Suite ist auf dem aktuellen Gerät wegen abweichender Screenshotbreiten nicht grün; Referenzbilder wurden nicht blind neu geschrieben. Navigation und Migration sind separat erfolgreich.
- WorkManager und Lifecycle-LiveData werden wegen der dokumentierten PKIX-Probleme weiterhin als lokale offizielle AARs eingebunden.
- Eine freigegebene produktive deutsche AniSearch-Kalenderquelle und echte Anbieterprüfung bleiben offen.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V4_2026-08-02.md

# AniSentinel v0.10.0 – Diagnose-Testbuild V4

Stand: 2026-08-02

## Ergebnis

Der verbleibende UTC-/Lokalzeitfehler im AniList-Kalenderpfad ist behoben. API-Zeitfenster, Room-Filter und Kalender-UI verwenden nun dieselbe lokale Nutzerzeitzone. Der abgeschlossene lokale Diagnosepfad wurde dabei nicht verändert.

## Zeitzonenmodell

`calendarTimeWindow(start, endExclusive, zoneId)` bildet lokale Kalendertage über `atStartOfDay(zoneId).toInstant()` auf Instants ab. `AniListCalendarSource` und `AniListCalendarRepository` verwenden dieselbe Funktion und standardmäßig `ZoneId.systemDefault()`. Die UI verwendete bereits dieselbe Systemzone.

Geprüft werden:

- 00:30 Uhr in `Europe/Berlin`, das am korrekten lokalen Tag bleibt,
- Monatsanfang und Monatsende,
- Sommerzeitbeginn mit einem 23-Stunden-Tag,
- Sommerzeitende mit einem 25-Stunden-Tag.

## Hintergrundstatus

Der AniList-Worker schreibt persistent in DataStore:

- Status `DISABLED`, `RUNNING`, `SUCCEEDED` oder `RETRY`,
- `runAttemptCount`,
- letzten Versuch,
- letzten Erfolg,
- frühesten nächsten Wiederholungszeitpunkt.

Der Kalender zeigt diese Informationen lokalisiert an. Der nächste Retry wird anhand derselben exponentiellen 30-Minuten-Grundlage geschätzt und auf 24 Stunden begrenzt. WorkManager bleibt die maßgebliche Ausführungsplanung; die Anzeige bezeichnet den Zeitpunkt deshalb ausdrücklich als „frühestens“.

## Schutz bestehender Daten

Separate Tests simulieren HTTP 403 und HTTP 429 als nicht verfügbare Quelle. In beiden Fällen bleibt die vorhandene `episode_releases`-Zeile unverändert in Room. Aufräumen erfolgt weiterhin ausschließlich nach einem erfolgreichen Fetch und Write.

## Prüfstand

- Unit-/Robolectric-Tests: 94/94 bestanden.
- Migrationstests auf Samsung SM-S928B: 4/4 bestanden.
- Navigationstests auf Samsung SM-S928B: 11/11 bestanden.
- Debug-APK und Android-Test-APK erfolgreich gebaut und installiert.
- `v4-status-proof.xml` bestätigt `Hintergrundabgleich: deaktiviert` im Kalender.
- kurzer Geräteclip in 720 × 1560 Pixeln erstellt.

## Ehrliche Grenzen

- Ein erfolgreicher AniList-HTTP-2xx-Lauf durch WorkManager konnte wegen der weiterhin nicht zuverlässig verfügbaren externen API nicht nachgewiesen werden.
- Der Build bleibt ein Diagnose-Testbuild und kein produktiver autonomer Release-Wächter.
- AniSearch- und Provider-Worker sind weiterhin ausdrücklich deaktiviert.
- WorkManager bleibt vorläufig über lokale offizielle AARs eingebunden; die Rückkehr zur Maven-Abhängigkeit bleibt nach Reparatur des Zertifikatszugriffs offen.
- Ein unmittelbar auf einen anderen Instrumentationsprozess folgender Compose-Lauf wird auf dem Samsung gelegentlich beendet; der isolierte Wiederholungslauf bestand vollständig.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V3_2026-08-02.md

# AniSentinel v0.10.0 – Diagnose-Testbuild V3

Stand: 2026-08-02

## Ergebnis

Der lokale Diagnosepfad ist technisch abgeschlossen. Die kritische Migration 7 → 8 wurde als Tabellenmigration neu aufgebaut und auf dem realen Samsung SM-S928B mit null, einem und drei vorhandenen Altimporten erfolgreich geprüft. Formatierung und JSON-Feldreihenfolge beeinflussen die Importidentität nicht mehr. Tatsächliche Inhaltsänderungen unter derselben `datasetId` bleiben geschützt.

## Korrekturen

- `local_import_batches` wird bei 7 → 8 ohne kollidierenden Leerwert neu aufgebaut.
- Altimporte erhalten eindeutige Werte `legacy:<importId>` für `datasetId` und `contentHash`.
- Der Importhash entsteht aus normalisierten, längencodierten und sortierten Fachdaten.
- Anime und Releases werden für den Hash stabil sortiert; Importzeit und zufällige Import-ID sind ausgeschlossen.
- External-ID-Mappings verwenden den festen Schlüssel `LOCAL_DIAGNOSTIC`.
- Der externe Mapping-Schlüssel enthält zusätzlich `datasetId:externalId`.
- README trennt nun Nachgewiesenes, produktiv Offenes und reine Diagnosedaten.
- Ein ergänzender 720p-Clip und `folge2-proof.xml` belegen `Sentinel-Testchronik · Folge 2` am 2. August 2026.

## WorkManager-Infrastruktur

- AndroidX WorkManager 2.10.0 ist reproduzierbar als lokales offizielles AAR eingebunden.
- Der AniList-Kalenderjob besitzt einen eindeutigen Namen, Netzwerkbedingung, ein sechsstündiges Intervall und exponentiellen 30-Minuten-Backoff.
- Bei deaktivierten Livequellen werden alle eindeutigen Source-Jobs storniert.
- AniSearch- und Provider-Jobs bleiben ausdrücklich storniert, bis ein zulässiger produktiver Datenweg freigegeben wurde.
- Der Worker liest den aktuellen Schalter erneut und verändert vorhandene Room-Daten bei Fehlern nicht.

## Prüfstand

- Unit-/Robolectric-Tests: 86, davon 86 bestanden.
- Migrationstests auf SM-S928B: 4, davon 4 bestanden.
- Navigationstests auf SM-S928B: 11, davon 11 bestanden.
- Debug-APK und Android-Test-APK: erfolgreich gebaut und installiert.
- App-Start mit eingebundenem WorkManager: Prozess blieb aktiv, kein Initialisierungs- oder Klassenfehler im Logcat.

## Gerätebeleg Folge 2

`folge2-proof.xml` bestätigt:

```text
Sonntag, 02. August 2026
Sentinel-Testchronik · Folge 2
02.08.2026 · 22:30 Uhr
Quelle: Lokal bereitgestellter Diagnosedatensatz
```

Der zugehörige Clip `AniSentinel-DIAGNOSETEST-720p-Folge2.mp4` wurde mit 720 × 1560 Pixeln und begrenzter Bitrate aufgenommen.

## Ehrlich offene Produktpunkte

- Der Diagnosedatensatz ist fiktiv und keine produktive Releasequelle.
- Automatische AniSearch- und Provider-Jobs sind absichtlich nicht aktiv.
- Eine produktive Bezeichnung als autonomer Release-Wächter ist erst nach Quellenfreigabe und realer Verfügbarkeitsprüfung gerechtfertigt.
- Auf dem Samsung-Gerät wird ein unmittelbar nach einem vorherigen Instrumentationsprozess gestarteter Compose-Testlauf gelegentlich vom System beendet. Der isoliert wiederholte vollständige Lauf bestand mit 11/11 Tests.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_V2_2026-08-02.md

# AniSentinel v0.10.0 – Diagnose-Testbuild V2

Stand: 2026-08-02

## Ergebnis

Der Diagnose-Testbuild wurde gebaut, auf einem Samsung SM-S928B mit Android 16 installiert und per ADB geprüft. Der lokale Diagnosedatensatz bleibt von einer nicht erreichbaren Live-Quelle getrennt. Der importierte Titel `Sentinel-Testchronik` wurde auf Start und Entdecken nachgewiesen; ein erzwungener App-Stopp mit anschließendem Neustart erhielt den lokalen Kalendereintrag.

Der vom Nutzer gemeldete Navigationsfehler wurde behoben: Beim Öffnen von Einstellungen gibt es keinen automatischen Wechsel mehr zum Kalender. Der frühere Kalender-Aktionsbutton im Diagnosebereich wurde durch einen reinen Hinweis auf die Bottom-Navigation ersetzt. Zusätzlich deckt der Instrumentationstest `bottomNavigationOpensSettings` dieses Verhalten ab.

## Technische Änderungen

- Room-Schema 8 mit `datasetId`, `contentHash` und eindeutigem Dataset-Index.
- Identischer Wiederholungsimport liefert `AlreadyImported` statt doppelter Daten.
- Gleiche Dataset-ID mit verändertem Inhalt liefert `DATASET_CONTENT_CONFLICT`.
- Stabile lokale Anime-IDs verhindern Duplikate.
- Lokale Room-Titel werden unabhängig vom Live-Quellenstatus auf Start und Entdecken angezeigt.
- Interne Fehlercodes wie `rate_limited` erscheinen nicht in der Hauptoberfläche.
- Lokale Kalendereinträge tragen die sichtbare Herkunft und den Text `Diagnoselink öffnen`.
- Fünf veraltete Navigationstest-Annahmen wurden durch semantische, zum aktuellen UI passende Prüfungen ersetzt.

## Automatisierte Prüfung

- `:app:testDebugUnitTest`: 84 Tests, 0 Fehler, 0 übersprungen.
- `:app:assembleDebug`: erfolgreich.
- `:app:assembleDebugAndroidTest`: erfolgreich.
- `AniSentinelNavigationTest` auf SM-S928B: 11 Tests, 11 bestanden.
- Room-Schemaexport `8.json`: vorhanden.

## Gerätebelege

- `home-proof.xml`: `Sentinel-Testchronik` auf Start bestätigt.
- `discover-proof.xml`: `Sentinel-Testchronik` in Entdecken bestätigt.
- `restart-proof2.xml`: Folge 3 und lokale Diagnosequelle nach Force-Stop/Neustart bestätigt.
- `AniSentinel-DIAGNOSETEST-720p-Mehrfachtermine.mp4`: kompakter 720p-Ablauf des Diagnoseimports und der Kalendernavigation.
- `AniSentinel-DIAGNOSETEST-720p-Neustart.mp4`: kompakter 720p-Neustartnachweis.

## Ehrlich offene Punkte

- Dieser Stand ist weiterhin ein Diagnose-Testbuild und noch keine produktive automatische AniSearch-Kalenderquelle.
- Die zwei 720p-Clips entstanden vor beziehungsweise während der letzten Navigationskorrektur. Die endgültige Korrektur ist durch den anschließenden 11/11-Gerätetest belegt.
- Ein direkt nach APK-Neuinstallation gestarteter Samsung-Instrumentationslauf wurde vereinzelt vom System beendet. Nach abgeschlossenem Installationsvorgang lief dieselbe Testsuite reproduzierbar vollständig durch.
- Die Room-Migrationstest-Hilfsklasse erzeugt eine Deprecation-Warnung; der Build bleibt erfolgreich. Eine Umstellung auf den neueren Konstruktor ist künftig sinnvoll.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSETEST_GERAET_2026-08-02.md

# AniSentinel v0.10.0 – Diagnosetest auf realem Gerät

## Gerät und Build

- Gerät: Samsung SM-S928B (Galaxy S24 Ultra)
- Android: 16
- ADB-Serienkennung: `R3CX4056Q8L`
- APK installiert: erfolgreich (`adb install -r`)
- Build: `BUILD SUCCESSFUL`
- lokale Unit-/Room-Tests: 84, davon 0 Fehler und 0 Fehlschläge
- Room-Schema: Version 7 exportiert

## Praktisch nachgewiesen

Der selbst erstellte, ausdrücklich fiktive Diagnosedatensatz wurde nicht in die APK eingebettet, sondern
separat nach `Download/AniSentinel-DIAGNOSETEST-Datensatz.json` übertragen.

Auf dem Gerät nachgewiesen:

1. AniSentinel lässt sich installieren und starten.
2. Der lokale Importbereich befindet sich unter `Einstellungen → Entwickler und Diagnose`.
3. Die Rechtebestätigung ist sichtbar.
4. Der Diagnosedatensatz gelangt bis Room und `episode_releases`.
5. Ein zweiter Import derselben External-ID wird mit einem Zuordnungskonflikt abgelehnt.
6. Der Kalender springt zum ersten importierten Termin.
7. Der 25. Juli 2026 ist markiert und zeigt `Sentinel-Testchronik · Folge 1`.
8. Nach `force-stop`, erneuter Installation mit `-r`, Neustart und erneutem Öffnen des Kalenders bleiben
   Titel und Termin sichtbar.
9. Die Quelle wird nutzerverständlich als `Lokal bereitgestellter Diagnosedatensatz` angezeigt.
10. Der lokale Titel wird nach der finalen Korrektur auch von der Room-basierten Start-/Entdecken-Quelle geliefert.

Damit sind Import, Kalenderdarstellung und Persistenz auf realer Hardware technisch bestätigt. Der
Datensatz ist kein Produktinhalt und kein Nachweis einer automatischen Livequelle.

## Automatisierte Gerätetests

- Direkter Room-Migrationstest über den AndroidJUnitRunner: `OK (1 test)`.
- AniSearch-Konnektivitätstest lieferte einen expliziten Transportzustand und bestand.
- Vollständiger Gradle-Hostlauf war wegen der offline fehlenden UTP-Abhängigkeit nicht startbar.
- Direkter Navigationstest: 12 ausgeführt, 7 bestanden, 5 fehlgeschlagen.

Die fünf Navigationstestfehler betreffen veraltete feste Listen-/Scrollpositionen aus der früheren
Fake-Datenansicht und einen nicht mehr vorhandenen Debug-Schalter. Sie werden ausdrücklich nicht als
bestandene Tests gezählt. Die bisherigen Golden-Tests sind an eine andere Referenzbreite (839 px)
gebunden und schlagen auf dem S24 Ultra bei 901 px Inhaltsbreite fehl; dies ist als offene
geräteadaptive Golden-Matrix dokumentiert.

## Aufnahme

Zwei ungeschnittene ADB-Aufnahmen liegen der Abgabe bei:

- Teil 1: Importablauf und anschließender Wechsel in die Android-Systemeinstellungen.
- Teil 2: Diagnosebereich, korrekt abgelehnter Wiederholungsimport und Kalendernachweis.

Zusätzlich wurden UI-Hierarchien nach dem Neustart gesichert. Sie enthalten den importierten Titel,
den 25. Juli 2026 und die lokalisierte Quellenbezeichnung.

## Weiterhin offen

- WorkManager wegen der dokumentierten Gradle-/PKIX-Abhängigkeitsauflösung.
- Produktiver AniSearch-Adapter bis zur schriftlichen Freigabe oder offiziell zulässigen Schnittstelle.
- Automatische deutsche Produktdatenquelle.
- Zulässige echte Anbieterprüfung.
- Golden-Matrix für unterschiedliche reale Gerätebreiten.
- Veraltete Navigationstests auf Room-Leerzustände und semantische Ziele statt Listenindizes umstellen.

## Abgrenzung

Diese APK ist ausschließlich ein `DIAGNOSETEST`-Build. Sie ist weder eine fertige App noch ein
Nachweis einer rechtlich freigegebenen oder funktionierenden automatischen Produktdatenquelle.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_DIAGNOSEIMPORT_2026-08-02.md

# AniSentinel v0.10.0 – technische Abgabe Diagnoseimport

## Statusübersicht

| Bereich | Status | Nachweis |
|---|---|---|
| Room als UI-Datenquelle | technisch umgesetzt | Start, Entdecken und Kalender beobachten persistente Room-Flows |
| Lokaler JSON-Diagnoseimport | technisch umgesetzt | `OpenDocument`, Schema v1, Vorabvalidierung, einzelne Room-Transaktion |
| Import-Rollback | automatisiert getestet | ungültiger Rechtehinweis hinterlässt keine Anime oder Releases |
| Mehrere Releases pro Anime | automatisiert getestet | zwei Episodentermine bleiben als getrennte `episode_releases` erhalten |
| External-ID-Zuordnung | technisch umgesetzt | Room-Schema v6, Zuordnung Quelle/externe ID/interne Anime-ID |
| GermanMetadataSource | vorbereitet | austauschbares Domaininterface; kein produktiver Adapter aktiviert |
| Persistente Cooldowns | technisch umgesetzt | DataStore für AniList und AniSearch |
| WorkManager | offen | lokale Gradle-Auflösung durch PKIX/Cacheproblem blockiert |
| Liveabruf | nicht erfolgreich nachgewiesen | bekannte externe Antworten: AniList 403, AniSearch 429 |
| Geräteprüfung des Imports | nicht durchgeführt | `adb devices -l` meldete am 2026-08-02 kein Gerät |
| Rechtliche Freigabe | offen | schriftliche Freigaben für Datenquelle und Cache fehlen |

## Diagnose-JSON Schema v1

Pflichtfelder auf Dokumentebene:

```text
schemaVersion = 1
source
generatedAt (ISO-8601 Instant)
rightsNotice
anime[]
releases[]
```

Anime benötigen `externalId`, mindestens einen Titel und eine HTTPS-Quellen-URL. Releases benötigen
`sourceReleaseId`, eine bekannte `internalAnimeId`, `expectedAt` als ISO-8601 Instant und eine
HTTPS-Quellen-URL. Das gesamte Dokument wird validiert, bevor Room verändert wird. Fehler führen zu
keinem Teilimport. Favoriten werden nicht gelöscht oder ersetzt. Der Import besitzt keine Exportfunktion.

## Build und Tests

```text
gradlew.bat --offline --no-daemon
  :app:testDebugUnitTest
  :app:assembleDebug
  :app:assembleDebugAndroidTest

BUILD SUCCESSFUL
78 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
```

Eine APK wurde für die Buildprüfung erzeugt, wird in dieser technischen Abgabe entsprechend dem
Korrekturauftrag aber nicht als fertiges Produkt oder Testkandidat ausgeliefert.

## Noch erforderlich für eine Produktabnahme

1. Schriftlich genehmigte deutsche Metadatenquelle.
2. Erfolgreicher automatischer Liveabruf und zulässiger begrenzter Cache.
3. WorkManager mit Netzwerkbedingung, eindeutiger Arbeit und exponentiellem Backoff.
4. Zulässige, unabhängige Provider-Prüfung.
5. Geräteclip für Import, Room-Anzeige, Kalender, Neustart und Persistenz.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_AUTOMATISCHER_KALENDER_2026-08-02.md

# AniSentinel – Validierungsbericht automatischer Kalender

Stand: 2026-08-02  
Projektversion weiterhin 0.10.0 / Code 21, Änderungen als `Unreleased`

## Build

- `:app:assembleDebug`: erfolgreich
- `:app:testDebugUnitTest`: 71 Tests, 0 Fehler
- `:app:assembleDebugAndroidTest`: erfolgreich
- Instrumentationstests wurden gebaut, aber mangels angeschlossenem Gerät nicht ausgeführt

## Automatischer Datenfluss

- Kalenderstart synchronisiert vier Wochen rückwirkend und acht Wochen zukünftig.
- Monatswechsel synchronisiert den gewählten Monat.
- AniLists öffentliches `airingSchedule` wird paginiert bis maximal 20 Seiten gelesen.
- 30-Minuten-Room-Cache verhindert wiederholte identische Zeitraumabrufe.
- Anime und mehrere Episodentermine werden transaktional per Room-Upsert gespeichert.
- Vergangene Einträge werden beim nächsten Abruf nicht gelöscht.
- Jeder Eintrag enthält eindeutige Quellen-ID, Episode, Zeitpunkt, Metadatenquelle,
  überprüfbaren AniList-Link und Abrufzeitpunkt.
- Parser-/Source-Test deckt mehrere Episoden desselben Titels und Quellenlinks ab.

## Quellenwahrheit

Die Termine sind japanische/internationale AniList-Ausstrahlungsdaten, keine bestätigten
deutschen Streaming-Releases. Titel verwenden AniLists englischen, ersatzweise Romaji- oder
Originaltitel. Die App kennzeichnet die Quelle als `ANILIST_AIRING_SCHEDULE`.

AniSearch bleibt primäre Zielquelle für deutsche Metadaten. Die systematische Prüfung aller
angeforderten Wege und die reproduzierbaren technischen Fehler stehen in
`docs/15_ANISEARCH_CALENDAR_SOURCE_DISCOVERY.md`. Es wurden keine Schutzmaßnahmen umgangen
und keine AniSearch-Fixtures oder Termine erfunden.

## Noch offen

- Verifizierte AniSearch-Kalenderfixture und produktive AniSearch-Zeitraumquelle
- belastbare Verknüpfung AniList-ID zu AniSearch-ID für deutsche Titel
- tatsächliche deutsche Provider-Verfügbarkeit und deutsche Veröffentlichungstermine
- Live-Gerätetest der Netzwerkabfrage; die lokale Windows-Umgebung konnte weder AniSearch
  noch AniList über Schannel abrufen (`SEC_E_NO_CREDENTIALS`)

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_ANISEARCH_SUCHE_2026-08-02.md

# AniSentinel – Validierungsbericht AniSearch-Suche

Stand: 2026-08-02  
Versionsangabe im Projekt: 0.10.0 / Code 21 (Korrekturen weiterhin `Unreleased`)

## Ergebnis

- `:app:assembleDebug`: erfolgreich
- `:app:testDebugUnitTest`: erfolgreich, 69 Tests, 0 Fehler
- `:app:assembleDebugAndroidTest`: erfolgreich
- Keine echte Geräteausführung der Instrumentationstests in diesem Durchlauf

## Umgesetzt

- AniSearch-Titelsuche nutzt den bestehenden Suchparser produktiv.
- Treffer werden in der Kalenderoberfläche angezeigt; Auswahl lädt die Detail-URL intern.
- URL-Eingabe ist nur noch eine aufklappbare Diagnosefunktion.
- Importergebnis enthält Metadatenstatus, Anbieteranzahl, Kalenderterminanzahl,
  Warnungen und Quellen-URL.
- Bei null verifizierten Episodenterminen zeigt die App ausdrücklich, weshalb der Titel
  noch nicht im Kalender erscheint.
- Gemeinsame Room-Transaktion für Anime, Releases und Anbieter vorbereitet.
- `AniSearchCalendarSource` trennt Zeitraumimport vom Detailimport.
- About-Text entspricht dem kontrollierten experimentellen HTML-Abruf.
- Test prüft Such-URL und sichere Query-Kodierung.

## Bewusste Grenze / offener Punkt

Ein automatischer AniSearch-Monatskalender wurde nicht vorgetäuscht. Im untersuchten und
lokal belegten Material liegt noch keine verifizierte öffentliche Tages-, Wochen-, Kalender-
oder RSS-Seite mit konkreten deutschen Episodenzeiten und echten gespeicherten Fixtures vor.
`UnverifiedAniSearchCalendarSource` liefert deshalb ehrlich `SourceUnavailable`. Sobald eine
solche Seite ohne Umgehung von Schutzmaßnahmen erreichbar und belegt ist, folgen echte
Fixtures, Parsertests und erst danach die Aktivierung des Zeitraumimports.

Ein allgemeines Serienstartdatum wird niemals als Episodentermin interpretiert.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_ANILIST_403_DATENPFAD_2026-08-02.md

# AniSentinel – Validierungsbericht AniList-403 und gemeinsamer Datenpfad

Stand: 2026-08-02  
Projektversion weiterhin 0.10.0 / Code 21 (`Unreleased`)

## Implementiert

- gemeinsamer `AniListGraphQlHttpClient` für Kalender und Trending-Fallback
- vollständiges Lesen von `inputStream` und `errorStream`
- Erfassung von Status, Fehlerbody, `Retry-After`, `X-RateLimit-Remaining`,
  `X-RateLimit-Reset`, `CF-Ray` beziehungsweise `X-Request-Id`
- getrennte Netzwerktypen: Timeout, TLS, DNS und IO
- 403-Klassifizierung: temporäre Dienstabschaltung, IP-Sperre und sonstiger Zugriff verweigert
- 400 als ungültige GraphQL-Anfrage, 429 als Rate-Limit
- gemeinsamer Mutex und zentraler Cooldown für Katalog und Kalender
- kalenderorientierte Query liefert Release sowie Titel, Beschreibung, Cover, Banner,
  Staffel, Jahr und Episodenzahl
- Kalender-Metadaten befüllen dieselben `AnimeEntity`-Flows, die Startseite und Entdecken lesen
- Trending wird beim Start nur noch als Fallback verwendet, wenn Kalender-Sync keine Inhalte liefert
- Repository-Mutex verhindert parallele doppelte Zeitraumabrufe
- englische AniList-Titel werden weiterhin nicht als deutsche Titel gespeichert

## Tests und Build

- `:app:assembleDebug`: erfolgreich
- `:app:testDebugUnitTest`: 76 Tests, 0 Fehler
- `:app:assembleDebugAndroidTest`: erfolgreich
- Tests klassifizieren dokumentierten 403-Dienststatus, gemeinsamen 429-Cooldown,
  Netzwerkfehler und Cacheerhalt

## Noch nicht nachgewiesen

Der konkrete Fehlerbody des auf dem Nutzergerät beobachteten 403 konnte ohne angeschlossenes
Gerät nicht abgerufen werden. Der neue APK liest und klassifiziert ihn beim nächsten realen
Abruf; die resultierende Meldung muss im nächsten Clip beziehungsweise Geräteprotokoll erfasst
werden. Deshalb ist dieser Build ein Geräte-Prüfkandidat und noch keine abgenommene Version.

Auch ein vollständiger Ablauf `HTTP 2xx → JSON → Room → UI → Neustart → Offline-Cache` wurde
in dieser Sitzung mangels Gerät nicht ausgeführt. Es werden weder Erfolg noch reale Inhalte
behauptet.

## Weiter offene Anforderungen

- Geräte-End-to-End-Nachweis
- WorkManager-Wiederholung mit persistentem Backoff
- persistenter AniSearch-Retry-After-Zustand in DataStore
- neutrale interne Anime-ID und externe-ID-Mappingtabelle
- automatische AniSearch-Anreicherung deutscher Titel

Diese Punkte sind ausdrücklich offen und werden nicht durch die lokale Buildvalidierung ersetzt.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_QUELLENTRENNUNG_2026-08-02.md

# AniSentinel – Validierungsbericht Quellentrennung

Stand: 2026-08-02  
Projektversion: 0.10.0 / Code 21, Änderungen weiterhin `Unreleased`

## Build und Tests

- `:app:assembleDebug`: erfolgreich
- `:app:testDebugUnitTest`: 72 Tests, 0 Fehler
- `:app:assembleDebugAndroidTest`: erfolgreich
- Instrumentationstests gebaut, mangels angeschlossenem Android-Gerät nicht ausgeführt

## Korrigierte Quellenwahrheit

- `ANILIST_AIRING`: internationale beziehungsweise japanische Ausstrahlung
- `ANISEARCH_GERMAN_RELEASE`: deutscher Releasetyp, nur für tatsächlich belegte AniSearch-Daten
- `PROVIDER_CONFIRMED`: ausschließlich nach positiv gespeichertem Provider-Check
- AniList-Termine erzeugen niemals automatisch einen bestätigten deutschen Providerstatus.
- Nach erreichtem Termin zeigt die UI ausdrücklich, dass die deutsche Verfügbarkeit noch
  nicht bestätigt ist.
- Quellenlink und Releaseart sind auf jeder Kalenderkarte sichtbar.

## Titelbehandlung

AniList-Titel werden nicht mehr als `titleGerman` gespeichert. Ohne deutsche Metadaten bleibt
dieses Feld leer; die Anzeige verwendet separat Englisch, Romaji oder Originaltitel. Existierende
lokale Metadaten werden beim Kalender-Upsert erhalten. Eine belastbare automatische
AniList-/AniSearch-ID-Zuordnung existiert noch nicht und wird nicht vorgetäuscht.

## Providerstatus

Kalenderzeilen lesen gespeicherte `ProviderAvailabilityEntity`-Nachweise. Nur ein zur Episode
passender Status `AVAILABLE` erzeugt „deutsche Anbieter-Verfügbarkeit bestätigt“. Netzwerkfehler,
Titel nicht gefunden oder ein bloß erreichter AniList-Termin reichen nicht aus.

## AniSearch-Gerätevalidierung

`AniSearchDeviceConnectivityTest` liegt als Android-Instrumentationstest bei. Er führt auf einem
normalen, vernetzten Android-Gerät einen kontrollierten öffentlichen Suchabruf aus und erlaubt,
den Windows-Schannel-/Sandboxfehler von einem tatsächlichen AniSearch-Zugriffsproblem zu trennen.
Ohne angeschlossenes Gerät wurde kein Erreichbarkeitsergebnis behauptet.

Details zu DNS-, TLS-, Sandbox- und Zertifikatsspeichergrenzen stehen in
`docs/15_ANISEARCH_CALENDAR_SOURCE_DISCOVERY.md`.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_LIVE_CACHE_2026-08-02.md

# AniSentinel v0.10.0 – Live-Cache-Korrekturstand

## Ergebnis

- AniList- und AniSearch-Cooldowns werden dauerhaft in DataStore gespeichert und überleben App-Neustarts.
- HTTP 429 sowie erkannte AniList-Sperr-/Ausfallantworten lösen keine wiederholten Sofortanfragen aus.
- Kalenderdaten werden ausschließlich nach einem erfolgreichen Abruf geschrieben und bereinigt.
- Der Zielcache umfasst vier Wochen rückwirkend und acht Wochen zukünftig.
- Einträge favorisierten Anime sowie bestätigte Anbieterhistorie (`AVAILABLE`) bleiben außerhalb des Fensters erhalten.
- Bei Netzwerk-, Parser-, Sperr- oder Rate-Limit-Fehlern werden keine vorhandenen Kalenderdaten gelöscht.
- Es wurde kein fremder 12-Wochen-Datensatz und keine erfundene Ersatzquelle in die APK eingebettet.

## Automatischer Hintergrundabruf

Eine WorkManager-Implementierung wurde vorbereitet und lokal kompiliert angegangen, konnte aber nicht in diesen
Prüfkandidaten übernommen werden: Gradle kann `androidx.work:work-runtime:2.10.0` weder aus dem unvollständig
indexierten lokalen Cache auflösen noch wegen eines lokalen PKIX/SSL-Zertifikatfehlers von Google Maven laden.
Die Abhängigkeit und der nicht kompilierbare Zwischenstand wurden wieder entfernt. Damit bleibt der Build
reproduzierbar grün. Automatische Abrufe erfolgen weiterhin beim App-/Kalenderfluss; periodische Arbeit ist offen.

## Verifikation

Ausgeführt am 2026-08-02:

```text
gradlew.bat --offline --no-daemon
  :app:testDebugUnitTest
  :app:assembleDebug
  :app:assembleDebugAndroidTest

BUILD SUCCESSFUL
76 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen
```

Die Instrumentierungs-APK wurde gebaut, aber nicht auf einem Gerät ausgeführt. Ein Device-E2E-Nachweis wird
daher ausdrücklich nicht behauptet.

## Offen

- WorkManager-Abhängigkeit nach Reparatur des lokalen Java-Truststores/Gradle-Caches einbinden.
- Autorisierten lokalen JSON/HTML-Dateiimport mit Herkunft, Erstellzeit und Rechtehinweis ergänzen.
- AniSearch/AniList/AnimeSchedule-Nutzungsrechte für eine dauerhafte Produktintegration schriftlich klären.
- Live-Quellen auf einem realen Gerät erneut prüfen; bekannte externe Antworten waren AniList HTTP 403 und
  AniSearch HTTP 429.

---

## Ursprünglicher Bericht: VALIDIERUNGSBERICHT_LEERE_APP_REGRESSION_2026-08-02.md

# AniSentinel – Validierungsbericht „leere App“-Regression

Stand: 2026-08-02  
Projektversion weiterhin 0.10.0 / Code 21 (`Unreleased`)

## Befund

Der Clip zeigt einen vollständig leeren frischen Installationsstand. Der Code löste zwar
AniList-Abrufe aus, verwandelte Kalenderfehler jedoch in `SourceUnavailable` ohne sichtbaren
Fehlerzustand. Ohne bestehenden Room-Cache war deshalb nicht unterscheidbar, ob keine Releases
existierten oder HTTP/GraphQL/TLS fehlgeschlagen war. AniSearch meldete separat HTTP 429.

Es wurde kein Pfad gefunden, der gültige Katalog- oder Kalenderdaten bei einem Fehler löscht:
`replaceCatalog` läuft nur nach erfolgreicher AniList-Antwort, und der Kalender schreibt nur
nach `Success`. Ein frischer Installationsstand besitzt allerdings noch keinen Cache.

## Korrekturen

- sichtbarer Kalendersync mit Ladezustand
- differenzierte HTTP-, Retry-After-, GraphQL-, JSON- und Netzwerkursache
- manueller Wiederholen-Button
- letzter erfolgreicher Synchronisationszeitpunkt
- vorhandene Room-Releases bleiben während Fehlern sichtbar
- Katalog zeigt die konkrete AniList-Ursache statt eines generischen Leerzustands
- Cachetreffer und Liveerfolg werden im Kalenderresultat unterschieden
- Regressionstest: HTTP-429-Syncfehler erhält vorhandene Releases

## Build und Tests

- `:app:assembleDebug`: erfolgreich
- `:app:testDebugUnitTest`: 73 Tests, 0 Fehler
- `:app:assembleDebugAndroidTest`: erfolgreich
- Instrumentationstests gebaut, aber nicht auf einem Gerät ausgeführt

## Nicht behaupteter Nachweis

Ein echter Geräte-Inhaltsfluss konnte in dieser Sitzung nicht bewiesen werden. Der Versuch,
ADB-Geräte aufzulisten, lieferte kein Gerät und hing beim Serverstart; der Prozess wurde nach
rund einer Minute beendet. Deshalb wird ausdrücklich nicht behauptet, dass der neue APK-Start
bereits reale Titel oder Kalenderpunkte auf dem Gerät gezeigt hat.

Für die verlangte Abnahme muss der beiliegende APK auf einem vernetzten Android-Gerät ohne
Schnitt aufgenommen werden. Bei erfolgreichem AniList-Abruf müssen Room-Flows automatisch
Start, Entdecken und Kalender aktualisieren. Bei Fehlern muss nun statt eines uneindeutigen
Leerzustands die konkrete Ursache sichtbar sein; nach einem späteren Erfolg bleibt der Cache
auch offline sichtbar.
