# Changelog

## v0.25.6 – belastbare Staffel- und Providerpräferenzen – 2026-08-21

- Eine animeweite Providerpräferenz gilt als Standard für alle Staffeln; eine staffelbezogene Auswahl überschreibt sie gezielt.
- Auswahlchips zeigen ausschließlich Provider, deren konkrete Staffel für Deutschland bestätigt ist. Allgemeine Katalogreferenzen erzeugen keine auswählbaren Provider.
- Gespeicherte Präferenzen auf nicht mehr bestätigte Staffelprovider werden nicht erzwungen; die App fällt auf einen bestätigten Provider beziehungsweise die automatische Auswahl zurück und kennzeichnet die ungültige Vorgabe.
- Solange für eine Staffel noch kein Mapping existiert, prüft die generische Discovery-Pipeline Crunchyroll zuerst und anschließend die übrigen Referenzen; daraus wird weiterhin erst nach einem echten Staffelcheck ein bestätigtes Mapping.
- Staffelchips entstehen aus verifizierten kanonischen Staffeln und bestätigten Provider-Mappings. Mehrere alte, ausschließlich aus Release-Backfill stammende Staffelnummern werden nicht mehr als reale Staffelstruktur ausgegeben.
- Regressionstests decken Phantomstaffeln, verteilte Provider-Staffeln, animeweite Standards, Staffel-Ausnahmen und ungültig gewordene Präferenzen ab.

## v0.25.5 – Release-Lifecycle, Staffelprovider und Anime-Katalog – 2026-08-21

- Alte unbestätigte Releases bleiben nicht mehr sieben Tage als enge aktive Provider-Wächter bestehen. Ein Nachfolger beendet den alten engen Watcher nach einer kurzen Karenz; spätestens nach 24 Stunden wird er `STALE_UNCONFIRMED`.
- `STALE_UNCONFIRMED` erfindet keine Verfügbarkeit, sendet keine technische Fehlermeldung und bleibt für späteren Historien-Backfill erhalten.
- Technische Providerfehler werden persistent providerweit gezählt. Eine sichtbare Meldung ist erst nach mindestens drei aufeinanderfolgenden Fehlern über mindestens zehn Minuten möglich; ein sechsstündiger Provider-Cooldown verhindert Meldungsfluten.
- `AVAILABLE` und `NOT_AVAILABLE_YET` setzen den technischen Fehlerzustand zurück.
- Kanonische Anime-Staffeln, Provider-Staffel-Mappings und manuelle Providerpräferenzen werden getrennt in Room 26 gespeichert.
- Providerwahl ist pro Staffel persistent. Ohne Nutzerauswahl wird Crunchyroll nur bevorzugt, wenn die konkrete Staffel im deutschen Mapping bestätigt ist.
- JustWatch-Aktualisierungen überschreiben manuelle Providerwahlen nicht. Checker, Staffelzuordnung und Providerlink verwenden dieselbe effektive Auswahl.
- Entdecken ist auf eindeutig erkannte Anime, Anime-Filme und belegte Live-Action-Adaptionen begrenzt. Gewöhnliche Filme und Serien aus dem breiteren JustWatch-Katalog werden ausgeblendet.
- Genre- und Anbieterfilter im Entdecken-Bereich werden nur aus dem zulässigen Anime-Katalog gebildet.
- JustWatch-Katalogabruf und titelweise Providerprüfungen verwenden getrennte, rate-limitierte Instanzen; eine Providerwarteschlange blockiert Entdecken nicht mehr.
- Ein erfolgreicher Katalogabruf setzt den UI-Fehlerzustand nun korrekt auf `null`; Genres und Provideranreicherung laufen nachgelagert und können den Katalogerfolg nicht mehr verfälschen.
- Regressionstests decken Release-Lifecycle, Providerfehler, Staffelwahl und die Anime-Kataloggrenze ab.

## v0.25.4 – Provider-first Verfügbarkeitsprüfung – 2026-08-16

- JustWatch Deutschland dient ausschließlich als Titel- und Anbieterresolver; konkrete Episoden werden direkt beim ausgewählten Anbieter geprüft.
- Öffentliche Direktadapter für Crunchyroll, ADN, Netflix, Disney+ und ANIVERSE ergänzen den AniWorld-Fallback.
- Provider-, Staffel- und Episodenidentitäten sowie belegte Ziel-URLs werden stabil in Room wiederverwendet.
- `NOT_AVAILABLE_YET` bleibt still; technische Providerfehler und bestätigte Verfügbarkeit verwenden getrennte Statussemantik.
- Historische Providerimporte erzeugen keine Alarme, Benachrichtigungen oder künstlichen Fälligkeiten.

## v0.25.3 – Ergebnisbenachrichtigungen und prüfbare Release-Statistik – 2026-08-16

- Fälligkeit und Start einer Providerprüfung laufen ohne Nutzerbenachrichtigung.
- Neu bestätigte Verfügbarkeit meldet Titel, Folge und Anbieter genau einmal; echte technische Prüffehler melden Titel und Fehlertext.
- Historisch nachgeladene, bereits abgelaufene Verschiebungen erzeugen keine verspäteten Push-Meldungen.
- Die operative Verschiebungsseite blendet providerbestätigte abgeschlossene Fälle aus; die vollständigen Datensätze bleiben in der Statistik erhalten.
- Alle sieben Statistik-Karten öffnen eine smartphonegerechte Liste. Zähler und Liste stammen aus derselben Datenmenge.
- Mitternachts-Platzhalter werden nicht als normale Uhrzeit dargestellt; eine belegte Titel-/Staffelzeit kann intern als `DERIVED_TITLE_PATTERN` abgeleitet werden, echte Mitternacht erfordert `EXACT_MIDNIGHT`.

## v0.25.2 – deutsche Metadaten und vollständige dynamische Aktualisierung – 2026-08-14

- JustWatch-Handlungen werden unabhängig vom `Accept-Language`-Header sprachlich geprüft; englische reale Quelltexte werden im Diagnosebuild deutsch übersetzt und als `TRANSLATED_FROM_JUSTWATCH` gekennzeichnet.
- Room 25 speichert Originaltext, erkannte Originalsprache und Herkunft der deutschen Fassung; Migration 24→25 erhält Favoriten und vorhandene Daten.
- HTML-Entities werden für Handlung, Genres und Studios dekodiert.
- Genres werden deutsch kanonisiert und semantisch dedupliziert.
- Ein leerer Studioabschnitt wird ausgeblendet; Studios werden niemals erfunden.
- Episodenkarten werden unabhängig von mehreren Provider-/Historienzeilen auf genau eine Karte je Episodennummer reduziert.
- Release-Sprache und Status werden in der normalen Detailansicht deutsch dargestellt.
- Der historische Providerimport ist als sekundäre Aktion „Historische Termine aktualisieren“ benannt.
- Anbieter, Aktuelle Season, Dub-Releases und Statistik besitzen nun ebenfalls Pull-to-Refresh mit relevanter Providerprüfung ohne Scheduler-/Notification-Erzeugung.
- 251 Unit-Tests prüfen zusätzlich Übersetzungspolitik, Entity-Decoding, Genre-Normalisierung, Episodendubletten und Sichtbarkeit der Verfügbarkeitsaktion.

## v0.25.1 – JustWatch-Metadaten und mobile Episodenaktionen – 2026-08-13

- Handlung, Genres und – soweit öffentlich tatsächlich vorhanden – Produktionsstudio werden über ein eindeutiges JustWatch-DE-Match nachgeladen und in Room gespeichert.
- Gespeicherte stabile JustWatch-IDs haben Vorrang; mehrdeutige Treffer übernehmen keine fremden Metadaten.
- JustWatch-Metadaten verändern keinen Episoden-Verfügbarkeitsstatus.
- Bestehende Titel können beim Öffnen beziehungsweise manuellen Aktualisieren nachträglich angereichert werden.
- Room 24 ergänzt `description` und `studios` am JustWatch-Katalogcache; Migration 23→24 erhält Favoriten und vorhandene Daten.
- Episodenkarten zeigen Aktionen untereinander über die Kartenbreite.
- „Verfügbarkeit jetzt prüfen“ erscheint nur bei den neuesten relevanten, noch nicht bestätigten Episoden und verschwindet nach `AVAILABLE`.
- Pull-to-Refresh wurde auf Detailseite und Entdecken ergänzt; Start, Kalender, Favoriten, News und Verschiebungen verwenden dieselbe bestehende Material-3-Mechanik.
- 244 JVM-Tests erfolgreich; Update auf dem Testgerät behielt 38 Favoriten.

- v0.25.0: Mehrwöchige und offene Pausen setzen den bisherigen Release-Rhythmus außer Kraft; ein neuer Sendetag oder eine neue Uhrzeit wird erst durch einen realen Quelltermin nach der Wiederaufnahme übernommen.

- v0.25.0: Verschiebungen erscheinen titelweit und prominent rot auf Start, Suche, Entdecken, Kalender, Favoriten und Details; die eigentliche Terminänderung bleibt streng nach Staffel, Episode und Sprache getrennt.
- v0.25.0: Der Crunchyroll-Historienimport lädt vollständige, begrenzte Episodenlisten statt der kleinen CMS-Standardseite; bestätigte historische Provider-Releases erscheinen global als verfügbar.

## v0.25.0 – Verschiebungen – 2026-08-13

- Neue Kategorie „Entdecken & Mehr → Verschiebungen“ mit Room-basierter Liste und interner Detailseite.
- Reale AniWorld-Verschiebungsseite als regelmäßig synchronisierte, gespeicherte Quelle integriert.
- Ursprünglicher Termin, optionaler Ersatztermin, Grund, Sprache, Quelle und Prüfzeitpunkt werden ohne erfundene Werte gespeichert.
- Eindeutig zugeordnete Releases stoppen den alten Due-/AUTO-/Fallback-Plan; bekannte Ersatztermine werden neu geplant.
- GER SUB und GER DUB bleiben strikt getrennt; kombinierte Quellmeldungen erzeugen zwei semantische Zuordnungen.
- Revisionen deduplizieren unveränderte Meldungen und erlauben Benachrichtigungen nur bei erstmaliger oder relevanter Änderung.
- Bereits bestätigte Verfügbarkeit wird nicht durch eine Verschiebungsmeldung überschrieben.
- Room-Schema 22 und reale HTML-Parser-Fixture vom 13.08.2026 ergänzt.

## v0.24.9 – automatischer Favoriten-Historienbackfill – 2026-08-12

- Neu hinzugefügte und wiederhergestellte Favoriten erhalten automatisch einen persistenten historischen Backfill.
- Room speichert pro Favorit `PENDING`, `RUNNING`, `COMPLETED` oder `RETRY_REQUIRED`; der Vorgang wird nach App- und Geräteneustarts fortgesetzt.
- Crunchyroll verwendet bekannte Series-IDs/URLs oder eine generische exakte Titelsuche; ADN-Show-IDs werden auch aus realen Anbieterlinks aufgelöst.
- `COMPLETED` wird nur nach einem echten historischen Import oder einer sicheren Anreicherung gesetzt.
- Der Backfill erzeugt keine Alarme, Notifications, AUTO-Prüfungen oder AniWorld-T+10-Fallbacks.
- Room 21 migriert alle bereits aktiven Favoriten automatisch nach `PENDING`.

## v0.24.8 – Favoritenklassifizierung und Historie – 2026-08-12

- Historische Crunchyroll-/ADN-Releases werden wieder für `Aktuell`, `Demnächst` und `Abgeschlossen` ausgewertet.
- UI-Klassifizierung und schedulbare Favoriten-Releases besitzen getrennte DAO-Abfragen.
- Historische Releases bleiben von Alarmen, Notifications, AUTO-Watcher, WorkManager und T+10-Fallback ausgeschlossen.
- Ein neuer konkreter Zukunftstermin verschiebt einen historischen Favoriten automatisch von „Abgeschlossen“ nach „Demnächst“.
- Room- und Classifier-Regressionstests sichern beide Datenwege ab.

## v0.24.7 – direkter Provider beendet Fallback – 2026-08-12

- Direkter Crunchyroll-/ADN-Erfolg beendet AUTO-Prüfung, WorkManager-Auftrag, Due-Alarm und T+10-AniWorld-Fallback terminal.
- Availability-Benachrichtigung wird unmittelbar aus dem direkten Providerergebnis erzeugt und semantisch dedupliziert.
- Fallback-Worker liest unmittelbar vor dem Netzwerkzugriff Room erneut und beendet sich bei bereits bestätigtem `AVAILABLE`.
- AniWorld-Fallback ist ausschließlich nach technischem `CHECK_FAILED` zulässig; ein erfolgreich ausgewertetes `NOT_AVAILABLE_YET` bleibt beim direkten Anbieter.
- GER_SUB und GER_DUB beenden nur den jeweils semantisch passenden Release-Lebenszyklus.
- Historische Importe bleiben vollständig von Alarmen, Workern, Watchern und Benachrichtigungen ausgeschlossen.
- Neue Regressionstests für direkten Erfolg, T+10-Grenze, Race Guard, technischen Fehler und Sprachtrennung.

## v0.24.6 – generischer Crunchyroll-Katalog und Monatshistorie – 2026-08-12

- Nicht auswertbare React-HTML-Shell durch einen anonymen, strukturierten Crunchyroll-Katalogclient ersetzt.
- Kein Benutzerkonto, keine Cookies sowie keinerlei Playback-, Manifest-, DRM- oder Downloadzugriff.
- Watch-URL, vorhandene Series-ID oder exakte Titelsuche lösen generisch die stabile Crunchyroll-Series-ID auf.
- Series → Seasons → Episodes wird für alle Crunchyroll-Kandidaten ausgewertet; das frühere Zwölferlimit entfällt.
- GER_SUB und GER_DUB werden ausschließlich aus `subtitle_locales` beziehungsweise `audio_locale` erzeugt.
- Historie verwendet nur echte Providerzeitpunkte; Sentinelwerte aus 9998/9999 werden verworfen.
- Monatsimport persistiert Crunchyroll-Historie nur innerhalb des ausgewählten Monats.
- Reale Fremdtitelregression: BLACK TORCH ohne vorgegebenen Serienlink selbst auf `GT00377907` aufgelöst.
- 207 JVM-Tests sowie 36 Gerätetests erfolgreich.

## DIAGNOSETEST V24.3 – Historie und korrigierte Statuslogik – 2026-08-11

- `CHECK_FAILED` als neutraler technischer Fehler ohne laufenden oder eingefrorenen Verspätungszähler umgesetzt.
- Verspätungszähler ausschließlich für `NOT_AVAILABLE_YET`; bei `AVAILABLE` nur mit realem `firstAvailableAt` eingefroren.
- Öffentlichen Crunchyroll-Historienparser für konkrete Staffel, Episode, Datum, Sprache, Watch-ID und echte URL ergänzt.
- Historische Providertermine in Room 19 dauerhaft gekennzeichnet und deduplizierend importiert beziehungsweise angereichert.
- Historische Termine von Due-Alarmen, Notifications, AUTO-Prüfung, WorkManager und AniWorld-T+10-Fallback ausgeschlossen.
- Kalender zeigt historische Termine ohne erfundene Uhrzeit und mit klarer Kennzeichnung.
- Regression für „A Livid Lady…“ auf dem Gerät geprüft: `CHECK_FAILED` bestätigt ausdrücklich keine Verzögerung.
- 198 Unit-Tests sowie 8 Room-Migrationstests auf dem verbundenen SM-S928B erfolgreich.

## DIAGNOSETEST V24 AUTO – verbindliche Availability-Strategie – 2026-08-11

- Generischen versuchsabhängigen Provider-Backoff vollständig entfernt.
- AUTO driftfrei ab `expectedAt` umgesetzt: 0–5 Min 30 s, 5–10 Min 1 Min, 10–60 Min 5 Min, 1–4 Std 30 Min, danach 1 Std.
- Kurzfristige AUTO-Ticks über einzelne Exact-Alarme; WorkManager führt nur den jeweiligen Netzwerkcheck aus.
- Watcher strikt auf aktive Favoriten begrenzt.
- Automatischer Abbruch bei `AVAILABLE`, `POSTPONED`, `DELAYED` oder `DELAYED_CONFIRMED`.
- Verschiebungen vor oder während der Prüfung annullieren alte Availability-Alarme.
- Vorhandener Verschiebungsgrund und neuer Termin werden ohne Erfindungen in die Benachrichtigung übernommen.
- Manuelle Profile 30 s, 1, 2, 5, 10, 15, 30 und 60 Minuten ergänzt.
- Due-Alarm, T+10-AniWorld-Fallback, Provider-Marktsemantik und V23-Matchingregressionen beibehalten.

## DIAGNOSETEST V24 – expliziter deutscher Anbietermarkt – 2026-08-11

- `providerMarket` in Room-Schema 17 ergänzt und JustWatch-Diagnosedaten verbindlich mit `DE` gespeichert.
- Bestehende JustWatch-Anbieterreferenzen bei der Migration auf den deutschen Markt zurückgeführt.
- Physische DVD-, Blu-ray-, Buch- und Shopangebote sowohl bei der Migration als auch vor jedem Provider-Sync bereinigt.
- Detailansicht um Markt, JustWatch-Quelle und Zeitpunkt der Anbieterprüfung ergänzt.
- V23-Jahressemantik, Stable-ID-Matching, Dara-san-/One-Piece-Regressionsschutz und due+upcoming-Sync beibehalten.
- Slime, MAO und Bumpkin nach realer Migration weiterhin mit ihren gelieferten deutschen Referenzanbietern vorhanden.
- 181 Unit-Tests und 6 Room-Migrationstests direkt auf dem verbundenen Android-Gerät erfolgreich.
- Nächster realer Favoritenrelease Clevatess S2E6 (GER SUB und GER DUB) für 12.08.2026, 14:00 Uhr samt +10-Minuten-Fallback vorbereitet; die tatsächliche Auslösung lag außerhalb des kurzen V24-Prüflaufs.

## DIAGNOSETEST V23 – konservative Provideranreicherung – 2026-08-11

- Serienstartjahr von Staffel- und Releasejahr semantisch getrennt.
- Nur sicher gespeicherte JustWatch-Serienjahre als harte Jahresbarriere beibehalten.
- Bereits gespeicherte stabile JustWatch-ID bei späteren Staffeln bevorzugt wiederverwendet.
- Lokalisierte, englische und Romaji-Titel über eine übereinstimmende stabile JustWatch-ID konservativ verbunden.
- Jahreszusätze wie `MAO (2026)` nur bei passendem Kandidatenjahr auf die Basisform reduziert.
- `One Piece` und `One Piece (2023)` bleiben auch ohne harte Jahresvorgabe getrennt.
- NoMatch-/Ambiguous-Diagnose um Jahrherkunft, Kandidaten, Scores und Ablehnungsgründe erweitert.
- Physische Angebote bei neuer Persistenz ausgeschlossen und alte physische Providerreferenzen beim Sync bereinigt.
- Slime, MAO und Bumpkin im realen JustWatch-Diagnoselauf eindeutig identifiziert.
- 180 Tests erfolgreich; V22-Newsrobustheit und due+upcoming-Sync unverändert erhalten.

## DIAGNOSETEST V22 – robuster News-Detailzustand – 2026-08-11

- Explizite Zustände `Loading`, `Found` und `NotFound` für die interne Newsdetailseite eingeführt.
- Ungültige oder nicht mehr vorhandene Meldungs-IDs enden nicht mehr in einem dauerhaften Ladeindikator.
- Verständliche Meldung „Meldung nicht mehr verfügbar“ und Rückkehrschaltfläche zur News-Liste ergänzt.
- Reguläre Room-Offlinedetails und getrennte HTTPS-Quellenbuttons aus V21 unverändert beibehalten.
- Zwei gezielte Regressionstests ergänzt; gesamter Stand: 171 erfolgreiche Tests.

## DIAGNOSETEST V21 – interne Newsdetailseite – 2026-08-09

- News-Karten von direkter Browsernavigation auf `news/{announcementId}` umgestellt.
- Offline lesbare Detailansicht direkt aus Room ergänzt.
- Kategorie, Titel, Zusammenfassung, Bild, Zeit, Staffel, Termine, Releasefenster, Grund, Provider und Quellen werden nur bei real vorhandenen Werten angezeigt.
- Anime2You-Originalartikel und zusätzliche Nachweise als getrennte HTTPS-Quellenbuttons umgesetzt.
- Unsichere oder fehlende URLs erzeugen keine Browseraktion.
- Interne Zurücknavigation führt zur News-Liste; Browser-Zurück führt zurück zur Detailseite.
- V20-Favoriten-, RSS-, Dedup-, Dara-san-, One-Piece- und Provider-Sync-Regressionspfade beibehalten.

## DIAGNOSETEST V20 – Releasezustand und echte News – 2026-08-09

- „Abgeschlossen“ von Anime-Endstatus auf „letzter Release vorbei, kein nächster konkreter Termin“ umgestellt.
- Staffelankündigungen ohne Datum strikt von Kalender und Favoriten-Releasestatus getrennt.
- „News & Meldungen“ im Drawer aktiviert.
- Offiziellen Anime2You-Artikel-RSS-Feed mit Größenlimit, User-Agent, Cache, Retry und Backoff angebunden.
- Meldungen in Room-Schema 16 strukturiert gespeichert; Quelle und Veröffentlichungszeit bleiben sichtbar.
- Konservative Kategorien für neue Anime, Staffeln, Termine, Verschiebungen, Streaming, Dub, Produktion und Fortsetzungen ergänzt.
- AniWorld-Terminänderungen als zweite redaktionelle Bestätigung eingebunden und nach Sachverhalt/Staffel/Zeitnähe dedupliziert.
- Dara-san-, One-Piece-, due+upcoming-, Exact-Alarm-, Fallback- und Deep-Link-Regressionspfade beibehalten.

## DIAGNOSETEST V19 – konservative Provideranreicherung – 2026-08-09

- Seasonbasis mit 78 realen AniWorld-Titeln unverändert beibehalten.
- Sicher gematchte Providerreferenzen zusätzlich zur optionalen Kataloganreicherung verwendet.
- Generische Titeläquivalenz für sprachliche Varianten wie „of Reiwa“/„of the Reiwa Era“ ergänzt.
- Jahr und Format bleiben harte Kompatibilitätssignale; unsicheres Fuzzy-Matching bleibt ausgeschlossen.
- `One Piece` und `One Piece (2023)` bleiben für Anreicherung getrennt.
- Randfälle der Seasonheuristik einschließlich Rückkehr nach Datenlücke getestet.
- V18 im sichtbaren App-Changelog ergänzt.

## DIAGNOSETEST V18 – eindeutiger Due-Pfad und reale Seasonbasis – 2026-08-09

- Parallelen WorkManager-Due-Timer entfernt; Exact Alarm ist der einzige zeitkritische Due-Auslöser.
- WorkManager auf Provider-Retry, Recovery und Synchronisation begrenzt.
- „Aktuelle Season“ direkt aus laufenden AniWorld-Releasezyklen aufgebaut.
- Laufende AniWorld-Titel bleiben auch ohne JustWatch-Match sichtbar; JustWatch reichert nur an.
- Zukunfts-only-, beendete und reine Katalogtitel aus der aktuellen Season ausgeschlossen.
- App-Changelog um V16 und V17 ergänzt.
- Providerfilter, Deep-Link, T+10-Fallback, Zeitfelder sowie letzter/nächster Release unverändert beibehalten.

## DIAGNOSETEST V17 – autonomer Exact-Alarm-Watcher – 2026-08-09

- Aktualisierten 17-Uhr-Realtest ausgewertet und falsche Fallback-Anbieterauswahl behoben.
- Zentrale Streaming-Provider-Policy nun auch in der Persistenz verwendet; DVD, Blu-ray und Händler können keinen AVAILABLE-Anbieter mehr liefern.
- Crunchyroll-Parser um öffentliche snake_case-Felder (`episode_number`, `season_number`, `subtitle_locales`, `audio_locale`) erweitert.
- Strukturierte Live-Diagnose für HTTP-Status, Antworttyp, Antwortgröße und Parserstufe ergänzt.
- Due-Zustellung direkt im Exact-Alarm-Wakeup statt erst nach WorkManager-Start ausgeführt.
- Zweiten generischen Wake-up bei `expectedAt + 10 Minuten` für den AniWorld-Fallback ergänzt.
- WorkManager als begrenztes Retry- und Recovery-System beibehalten.
- `sourceAvailableAt` getrennt von `firstAvailableAt` und `lastCheckedAt` in Room-Schema 15 gespeichert.
- Due- und Available-Notifications mit Deep-Link auf die konkrete Staffel, Episode und Sprache versehen.
- Detailansicht zeigt letzten und nächsten Release gleichzeitig samt Providerstatus und Zeitstempeln.
- Reale V16-Historie dokumentiert: Due 15:02:14, AniWorld-Erkennung 15:13:17, Crunchyroll `PARSER_CHANGED`.

## DIAGNOSETEST V16 – Hintergrundabnahme und Statusklarheit – 2026-08-09

- JustWatch bleibt ausschließlich Anbieter- und Metadatenquelle; Episodenentscheidungen verwenden nur direkten Providercheck oder den AniWorld-Fallback.
- Leere Anbietertexte in Verfügbarkeitsmeldungen verhindert und Sprachfassung aus konkreten Episodenflags abgeleitet.
- Kalender um letzte Prüfung, Anbieter, ersten Erkennungszeitpunkt, Fehler- und Fallbackstatus ergänzt.
- Tagesbegrüßung dynamisch an die lokale Gerätezeit gebunden.
- Aktuelle Season auf reales AniWorld-Fenster vor und nach dem aktuellen Zeitpunkt erweitert und Trefferzahl sichtbar gemacht.
- Verschobene Releases in der Statistik als anklickbare Detailansicht umgesetzt.
- Version `0.16.0-v16-diagnostic` für den realen gesperrten 15-Uhr-Hintergrundtest vorbereitet.

## DIAGNOSETEST V15 – Releasekette und produktive Drawer-Bereiche – 2026-08-09

- Fachlich stabilen Notification-Key aus Anime, Staffel, Episode, Sprache und Ereignis eingeführt.
- Atomaren Room-Claim gegen parallelen AlarmManager-/WorkManager-Doppelversand ergänzt.
- Legacy-Deduplizierung für V14-Deliveries und den realen fehlerhaften `aniworld:episode-6`-Datensatz ergänzt.
- AniWorld-Kalenderidentität auf den tatsächlichen Serienlink beschränkt.
- JustWatch verbindlich auf Anbieterermittlung beschränkt; keine Episoden-Negativaussage mehr.
- Direkten Streaminganbietercheck vor den AniWorld-Fallback gesetzt.
- AniWorld-Fallback auf konkrete Episodenzeilen der Serienübersicht und lokale Sprachflaggen umgestellt.
- `firstAvailableAt` als „Erstmals erkannt“ samt geplantem Termin und erkannter Verzögerung sichtbar gemacht.
- Anbieter, aktuelle Season, neue Dub-Releases, Release-Statistik und Changelog als reale Drawer-Routen aktiviert.
- News bleibt mangels realer Quelle deaktiviert; „Heiß erwartet“ bleibt bis zur eindeutigen Popularitätssemantik deaktiviert.

## DIAGNOSETEST V10 – Favoritenüberwachung – 2026-08-04

- Kalenderkarten über die echte Room-Anime-ID zur Detailseite navigierbar gemacht.
- Eindeutige One-Time-WorkRequests je favorisiertem Release ergänzt.
- Neuplanung bei Favoriten-, Sprach-, Kalender- und Verschiebungsänderungen sowie App-/Geräteneustart.
- Persistente Tabellen für geplante Releasejobs und deduplizierte Benachrichtigungszustellungen ergänzt.
- Allgemeine JustWatch-Titelzuordnung und konkrete Staffel-/Episodenprüfung fachlich getrennt.
- Konkrete DE-Angebote sowie Audio-/Untertitelsprachen bestimmen GER SUB und GER DUB getrennt.
- Netzwerkfehler bleiben Fehlerzustände und werden niemals als Nichtverfügbarkeit gespeichert.
- Statusfolge `SCHEDULED → DUE → CHECKING → PENDING_CONFIRMATION/DELAYED/AVAILABLE` verdrahtet.
- Release-Due-, Verfügbarkeits-, Verspätungs- und Verschiebungsbenachrichtigungen mit Duplikatschutz ergänzt.
- Lokalen Fake-/Diagnoseimport und Demo-Benachrichtigungsweg aus der installierbaren UI entfernt.
- Reale Job-/Prüf-/Versanddiagnose in Detailseite und Einstellungen ergänzt.
- Room-Schema 12 samt Migration 11→12 und Regressionstests ergänzt.
- 125 lokale Tests sowie Debug- und Instrumentation-APK erfolgreich gebaut.
- Echte Geräte-/Terminabnahme noch offen, da das zuvor verbundene Gerät während der Abnahme getrennt wurde.

## DIAGNOSETEST V9 – Kalenderkarten und JustWatch-Abdeckung – 2026-08-03

- Kalendertermine verwenden dieselbe responsive Titelkarte wie die Startseite.
- Jede Tageskarte zeigt Cover, Titel, Folge, GER-SUB/GER-DUB, lokale Uhrzeit und sekundengenauen Countdown.
- GER-SUB und GER-DUB bleiben auch am selben Tag getrennte Termine.
- Technische Quellen-, Worker- und Diagnoseangaben aus der normalen Kalenderansicht entfernt.
- Anbieter werden titel-/staffelbezogen aus realen JustWatch-DE-Angeboten übernommen.
- Sichere Titelzuordnung um Akzent-, Satzzeichen- und Staffelzusatz-Normalisierung erweitert.
- Mehrdeutige Treffer werden nur aufgelöst, wenn genau ein Kandidat deutsche Angebote besitzt.
- Live-Gerätetest: 69 von 79 Titeln eindeutig zugeordnet; 3 mehrdeutig und 7 ohne sicheren Treffer.
- 121 Unit-Tests ohne Fehler; Debug-APK erfolgreich gebaut und auf einem Galaxy-Gerät geprüft.

## Diagnose-Testbuild V8 – episodengenauer Provider-Diagnosepfad – 2026-08-03

- AniWorld bleibt primäre Quelle für deutsche GER-SUB-/GER-DUB-Termine und Verschiebungen.
- AniWorld-Kalendercover werden aus den öffentlichen Karten übernommen, validiert und im Anime-Cache gespeichert.
- Jeder zukünftige Kalendereintrag zeigt einen sekundengenauen, lifecycle-sicheren Countdown aus der festen Zielzeit.
- AnimeRadar, AniList und AniSearch sind im aktiven Pfad deaktiviert und bleiben nur als inaktive Fallback-Bausteine im Quellcode.
- Neutrale JustWatch-Partner-Schnittstelle ergänzt; ohne freigegebenen Zugang lautet der Produktstatus verbindlich `SOURCE_NOT_CONFIGURED`.
- Lokalen Provider-Diagnosedatensatz ausschließlich im Debug-Build aktiviert und in der UI unübersehbar gekennzeichnet.
- Room-Schema v10 um Titelmatches, Angebote und episodengenaue Providerverfügbarkeit erweitert.
- Allgemeine Anbieterlistungen und bestätigte Episodenverfügbarkeit strikt getrennt.
- GER SUB, GER DUB und beide Sprachfassungen als getrennte Zustände gespeichert.
- Fehler werden als `CHECK_FAILED` behandelt und niemals als „nicht verfügbar“ interpretiert.
- Ansteigendes Prüfintervall 0/5/10/20/30/60 Minuten ergänzt.
- Getrennte WorkManager-Jobs `anisentinel.justwatch-provider-sync` und `anisentinel.provider-availability-sync` aktiviert.
- Diagnoseanzeige für eindeutige/mehrdeutige Zuordnungen und Episodenprüfungen ergänzt.

## Diagnose-Testbuild V7 – deutscher AniWorld-Kalender – 2026-08-03

- Öffentlichen AniWorld-Animekalender und die Verschiebungsseite kontrolliert angebunden.
- Ausschließlich GER SUB (`japanese-german.svg`) und GER DUB (`german.svg`) übernommen.
- Englische und andere Sprachfassungen vollständig verworfen.
- GER SUB und GER DUB anhand von Sprachfassung, Staffel, Episode und Zeit getrennt gespeichert.
- AniWorld-Originalzeit und korrigierte Zeit mit verbindlichen −10 Minuten gespeichert.
- 23:59 wird ebenfalls korrigiert und zusätzlich als möglicher Tagesendmarker protokolliert.
- Doppelte gleichsprachige HTML-Zeilen vor Room dedupliziert.
- Room-Schema v9 um Quellenreferenzen, Originalzeit, Sprachfassung und Änderungshistorie erweitert.
- Verschiebungen nur bei eindeutigem Titel-, Staffel-, Episoden- und Sprachmatch angewendet.
- Historisierte Releases vor späterem Bereichsersatz und CASCADE-Verlust geschützt.
- Getrennte WorkManager-Jobs für AniWorld-Kalender und AniWorld-Verschiebungen ergänzt.
- Startseite quellenneutral in „Anime-Katalog“ umbenannt.
- Internationalen AnimeRadar-Zugang und zugrunde liegende AniList-Termindaten transparent getrennt.
- Reale Geräteprüfung: 128 internationale Termine, 195 deutsche Termine, eine eindeutige Sub-Verschiebung.


## Diagnose-Testbuild V6 – AnimeRadar End-to-End – 2026-08-03

- AnimeRadar-Kalenderadapter in Debug/DIAGNOSETEST standardmäßig aktiviert.
- Öffentlichen POST-Endpunkt `/api/anilist` mit echter Pagination und strikter Validierung angebunden.
- AnimeRadar als primäre Releasequelle, AniList ausschließlich als Ausfall-Fallback koordiniert.
- Keine erfundene deutsche oder Provider-Verfügbarkeit aus AniList-Terminen abgeleitet.
- Vollständige Ergebnisse quellenbezogen und transaktional in Room ersetzt; Fehler erhalten den Cache.
- Automatischen Start- und Hintergrundabruf auf die aktuelle Woche begrenzt.
- Diagnoseanzeige um Quelle, Empfangs-/Speicherzahl, Abrufzeit und WorkManager-Status ergänzt.
- Debug-Hintergrundjob unabhängig von der produktiven Live-Quellen-Einstellung aktiviert.
- Echte AnimeRadar-Fixture und Parser-, Fallback- und Room-Regressionstests ergänzt.
- 105 Unit-Tests erfolgreich; Galaxy-S24-Ultra-Lauf mit 128/128 Terminen bestätigt.

> AniList-403-Diagnose: gemeinsamer GraphQL-Client liest jetzt Erfolgs- und Fehlerbody,
> Retry-After, Rate-Limit-Header und Request-ID. 403 wird als Dienstabschaltung, IP-Sperre,
> Queryfehler oder Zugriffssperre klassifiziert. Kalender- und Katalogpfad teilen zentralen
> Cooldown; kalenderorientierte Metadaten speisen Room, Start und Entdecken gemeinsam.

> Leere-App-Regression: Kalender zeigt jetzt Lade-, HTTP-/GraphQL-/Netzwerkfehler,
> Retry-After, Wiederholen und letzten erfolgreichen Stand. Katalogfehler enthalten die
> konkrete Ursache. Fehlgeschlagene Kalenderabrufe lassen vorhandene Room-Einträge intakt;
> ein Regressionstest sichert dies ab.

> Quellenwahrheit nachgeschärft: AniList-Ausstrahlung, AniSearch-deutscher Release und
> bestätigte Provider-Verfügbarkeit sind getrennte Domainzustände. Englische AniList-Titel
> werden nicht mehr als `titleGerman` gespeichert. Kalenderkarten zeigen Releaseart,
> Quellenlink und den expliziten deutschen Bestätigungsstatus.

> Automatischer Kalender: AniList-`airingSchedule` lädt beim Start vier Wochen rückwirkend
> und acht Wochen zukünftig sowie beim Monatswechsel den gewählten Monat. Termine werden
> dauerhaft in `episode_releases` gespeichert und ehrlich als AniList-Ausstrahlungsdaten
> gekennzeichnet. Die systematische AniSearch-Quellenprüfung steht in Dokument 15.

> Zusatz zum Korrekturstand 2026-08-02: Titelsuche statt URL-Zwang, URL-Import nur
> als Diagnosefunktion, differenzierter Importstatus, vorbereitete transaktionale
> Kalenderpersistenz und explizite Source-Discovery-Schnittstelle. Ein unbestätigter
> Monatsparser bleibt bis zu echten gespeicherten Fixtures sicher deaktiviert.

## [Unreleased] – 2026-08-02

- bereitgestelltes AniSentinel-Motiv als tatsächliches Raster-Launcher-Icon eingebunden;
  Schriftzug und Slogan werden für kleine Launcher-Masken ausgelassen
- AniSearch-Open-Source-Vergleich dokumentiert, ohne fremden Implementierungscode zu kopieren
- JSON-LD-first-AniSearch-Parser mit präzisen deutschen DOM-Fallbacks
- AniSearch-Suchresultate werden über die stabile ID normalisiert und dedupliziert
- kontrollierter AniSearch-HTTP-Transport mit 30-Minuten-Cache, vier Sekunden Mindestabstand,
  User-Agent, Kill-Switch, begrenztem Retry/Backoff und differenzierten HTTP-Zuständen
- Kalender liest Tages- und Monatsdaten ausschließlich aus `episode_releases` statt
  `AnimeEntity.nextAiringAt`
- mehrere historische und zukünftige Episoden desselben Anime bleiben separat darstellbar
- sichtbarer AniSearch-URL-Import im Kalender mit Lade-, Erfolgs-, Blockiert- und Fehlerzustand
- Kalendereinträge zeigen deutsche Titel, Episodennummer, Quelle, Anbieter und direkten Quellenlink
- ein leerer Room-Zeitraum wird ehrlich als „noch nicht vollständig geladen“ ausgewiesen
- vollständige Android-Branding-Ressourcen: Adaptive, Round, Monochrom, Legacy,
  Android-12-Splash, Drawer-Emblem und eigenes Notification-Symbol
- DOM-basierter manueller AniSearch-HTML-Import für öffentliche Detailseiten
- strikte HTTPS-/Host-/Detail-URL-Validierung und ehrliche Parserfehler
- deutsche Titel/Beschreibungen bleiben primär und AniList-Felder werden nur ergänzend erhalten
- erkannte Anbieterlinks werden normalisiert und automatisch als `ProviderReference` gespeichert
- konkrete Episoden- oder Serien-URL hat im Crunchyroll-Checker Vorrang vor dem Kalender
- Evidence-Typen `EPISODE_PAGE`, `SERIES_PAGE`, `RELEASE_CALENDAR`, `SEARCH_RESULT`
- Room-Schema v5 mit `episode_releases`, Evidence-Type und atomarem Marker gegen doppelte
  Verfügbarkeitsbenachrichtigungen
- 67 lokale Unit-/Robolectric-Tests erfolgreich

### Offen

- Der Live-Abruf von AniSearch war technisch blockiert; daher noch kein echter HTML-Fixture
  und kein praktisch belegter Realimport. Der manuelle Importpfad benötigt noch eine UI.
- Kalender-UI liest noch nicht primär aus `episode_releases`.
- WorkManager, Backoff-Ausführung und echte einmalige Release-Benachrichtigung fehlen noch.
- Die Branding-Ressourcen bauen erfolgreich; Installer-/Launcher-/Splash-Screenshots erfordern
  Installation auf Emulator oder Gerät.

## [0.10.0] – 2026-08-01

- strukturierter `ProviderChecker` und differenzierte Prüfergebnisse
- erster defensiver Checker für den öffentlichen deutschen Crunchyroll-Simulcast-Kalender
- konkrete Episodennummer wird geprüft; Serientreffer allein bestätigt keine Verfügbarkeit
- Netzwerk- und Loginfehler werden nicht als „Titel nicht gefunden“ ausgegeben
- Room-Schema v4 mit Anbieterzuordnungen, Prüfergebnissen und `firstAvailableAt`
- Detailseite beobachtet gespeicherte Anbieterzustände, Prüfzeitpunkt und Quellenlink
- manueller Prüfpfad ist nur für eine echte gespeicherte Crunchyroll-Zuordnung aktiv
- vier Fixture-Tests für verfügbar, Episode fehlt, Loginwand und Netzwerkfehler

### Noch blockiert

- AniSearch-Animezugriff benötigt weiterhin eine projektspezifische API-Vereinbarung.
- Ohne überprüfbare Anbieterzuordnung wird kein Checker ausgeführt.
- WorkManager und produktive Verfügbarkeitsbenachrichtigungen sind noch nicht aktiviert.

## [0.9.1] – 2026-07-30

- zentraler `ReleaseStatusResolver`: vergangene Termine werden als `RELEASE_TIME_REACHED`
  statt dauerhaft als `SCHEDULED` dargestellt
- `AVAILABLE` entsteht nur aus einer tatsächlich bestätigten Anbieterprüfung
- getrennte Domainmodelle für Metadatenquelle und Streaming-Verfügbarkeit
- separate Felder für Gesamtfolgenzahl und nächste Episodennummer
- direkte, überprüfbare AniList-URL auf der Detailseite
- Anbieterbereich zeigt bis zu einer echten Prüfung ausdrücklich „noch nicht geprüft“
- interaktiver Kalender mit lokaler Zeitzone, Datumsauswahl, Room-Zeitfensterabfrage,
  Monatsnavigation und Markierungen für gespeicherte Termine
- automatische Aktualisierung wird bei überschrittenen Cacheterminen bevorzugt
- dynamische Beschriftungen für heute, morgen und nächste Kalenderwoche
- Regressionstests für vergangene/zukünftige Termine, Verfügbarkeit, Jahreswechsel und
  Room-Zeitfenster

## [0.9.0] – 2026-07-30

- Produktive Routen verwenden keine Fake-Anime mehr.
- Home und Entdecken lesen ausschließlich den echten AniList-Room-Cache.
- Kalender zeigt bis zu bestätigten echten Terminen einen ehrlichen Leerzustand.
- Detailseiten laden ausschließlich persistierte echte Datensätze.
- Favoriten lösen ihre Anime direkt aus Room statt aus dem Fake-Repository auf.
- Der Datenquellen-Schalter und der produktive Fake-Hinweis wurden entfernt.
- AniSearch wurde über den offiziellen API-Link geprüft: Anime-Metadaten erfordern
  derzeit eine projektspezifische Schnittstellenvereinbarung. Ohne Zugangsdaten wird
  weder Scraping noch eine scheinbar funktionierende AniSearch-Suche ausgeliefert.

## [0.8.2] – 2026-07-30

- About-Seite beschreibt Demo- und AniList-Live-Modus korrekt.
- AniSearch wird ausdrücklich als noch nicht angebunden gekennzeichnet.
- Die Benutzer-APK wird als `INSTALLIEREN.apk` ausgeliefert.
- Die Android-Test-APK liegt ausschließlich im Unterordner `tests`.
- Eine Installationsanleitung verhindert die Verwechslung beider APK-Typen.
- Ein AniSearch-Scraper bleibt deaktiviert, bis Nutzungsbedingungen und Crawler-Regeln
  verlässlich geprüft und dokumentiert werden können.

## [0.8.1] – 2026-07-30

### Korrigiert

- unbekannte AniList-Ausstrahlungstermine bleiben nullable und werden nicht aus `updatedAt` erfunden
- getrennte `sourceUpdatedAt`-/`cachedAt`-Felder und Room-Schema v3 mit Migration `2 → 3`
- Trending-Ergebnisse werden transaktional als geordneter Snapshot ersetzt, ohne Favoriten-Anime zu löschen
- Live-Details zeigen echte Metadaten, aber keine erfundenen Provider-, Studio-, Genre- oder Episodenverfügbarkeiten
- unbekannte Detail-IDs zeigen Lade-/Fehlerzustände statt eines kurz sichtbaren Fake-Titels
- Live- und Demo-Inhalte sind auf der Startseite getrennt

### Cache und Tests

- 30-Minuten-TTL für automatische Aktualisierung und Beachtung von HTTP `Retry-After`
- Cover-Loader mit HTTP-Prüfung, Zeitlimits, Speicher-/Diskcache und Downsampling
- Snapshot-/Favoriten-Regressionstest und instrumentierter Migrationstest `2 → 3`

### Bekannte Grenze

- Ein erfolgreicher TLS-End-to-End-Abruf von AniList konnte in der isolierten Emulatorumgebung nicht bestätigt werden.

Alle relevanten Änderungen an AniSentinel werden in dieser Datei dokumentiert.

## [0.8.0] – 2026-07-30

### Hinzugefügt

- erste echte öffentliche AniList-GraphQL-Metadatenquelle
- typisierte Erfolgs-, HTTP-, Netzwerk- und ungültige Antwortzustände
- nullable-sichere DTOs und Mapper für Titel, Beschreibungen, Cover und nächste Ausstrahlung
- Room-Cache mit Schema v2 und Migration `1 → 2`
- Debug-Umschaltung zwischen stabilen Fake-Daten und AniList-Live-Metadaten
- cache-first Startseite mit Lade-, Refresh-, Offline- und Fehlerzuständen
- manuelles Aktualisieren sowie reale Cover mit abstraktem Fallback

### Sicherheit und Grenzen

- AniList wird ausschließlich für öffentliche Metadaten genutzt
- keine AniSearch- oder Anbieterabfrage
- keine Streaming-, Download-, Playback- oder DRM-Funktion
- Zeitlimits, klarer User-Agent und maximal 12 Titel pro manueller Anfrage

### Tests

- 55 lokale Unit-/Robolectric-Tests erfolgreich
- 22 instrumentierte Tests auf Android 15/API 35 erfolgreich: `OK (22 tests)`
- Parser-, Nullfeld-, Fallback-, Mapper- und Live-/Fehlerzustandstests ergänzt
- acht bestehende Golden-Vergleiche weiterhin erfolgreich

## [0.7.8] – 2026-07-30

### Behoben

- nach erstmaligem Erteilen der Android-13-Benachrichtigungsberechtigung wird die angeforderte Demo sofort gesendet
- Ablehnung erzeugt keine Notification und zeigt einen verständlichen Wiederholungs-/Einstellungsweg
- fehlende Golden-Assets lassen den zugehörigen Regressionstest nun verbindlich fehlschlagen

### Hinzugefügt

- getrennte Karten für die gespeicherte Benachrichtigungspräferenz und die lokale Testaktion
- zentraler `NotificationCoordinator` für Sprache, Präferenzen, Domain-Engine und Android-Ausgabe
- vollständiger About-Screen als Golden sowie 150-%-Hero-Golden
- echter instrumentierter Erstberechtigungs- und Ablehnungstest

### Geändert

- Golden-Dateien heißen entsprechend ihrem tatsächlichen Hero-/Breitformat-Inhalt
- Kanalnamen werden nach einem Sprachwechsel erneut lokalisiert
- About-Large-Font-Test scrollt bis zum letzten Abschnitt

### Tests

- 50 lokale Unit-/Robolectric-Tests erfolgreich
- 21 instrumentierte Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich: `OK (21 tests)`
- acht verbindliche Golden-Vergleiche erfolgreich; keine Diff-Datei erzeugt

## [0.7.7] – 2026-07-30

### Hinzugefügt

- vier lokale Android-Benachrichtigungskanäle für Erinnerungen, Releases, Verspätungen und Systemmeldungen
- Android-13-Berechtigungsfluss, lokalisierte Demo-Benachrichtigung und App-öffnender `PendingIntent`
- sechs eingecheckte Golden-Screenshots für Smartphone/Tablet, Hell/Dunkel und Deutsch/Englisch
- echte Pixelvergleiche mit 2-%-Toleranz und magentafarbenem Diff-Bild bei Abweichungen
- zwei instrumentierte Lesbarkeitstests mit realer Schriftvergrößerung auf 150 %

### Geändert

- Versionsanzeige der About-Seite stammt zentral aus `BuildConfig.VERSION_NAME`
- UI-Tests setzen Sprache, Theme, Favorit und Benachrichtigungen vor/nach jedem Test zurück
- Benachrichtigungen bleiben vollständig lokal und nutzen weiterhin nur Fake-Ereignisse

### Tests

- 50 lokale Unit-/Robolectric-Tests erfolgreich
- 17 instrumentierte Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich: `OK (17 tests)`
- alle sechs Golden-Varianten pixelweise geprüft; keine Diff-Datei erzeugt

## [0.7.6] – 2026-07-30

### Hinzugefügt

- echte, deutsch/englisch lokalisierte „Über AniSentinel“-Unterseite
- Angaben zu Version, Build, Entwicklungsstatus, Datenschutz, Fake-Quellen und Technik
- instrumentierter Sprachtest über Einstellungen, Entdecken, Kalender und Activity-Recreation
- instrumentierter Favoriten-Persistenztest über Recreation und Navigation
- reproduzierbare PNG-Ausgabe der Screenshot-Smoke-Tests

### Geändert

- About-Ziele in Einstellungen und Drawer navigieren auf die echte Unterseite
- Notification Engine wird als Flow anhand der gespeicherten Sprache erzeugt
- WatchProfile-Domain enthält nur noch neutrale IDs und keine deutschen Anzeigenamen
- transparenter Hero-Container setzt seine Content-Farbe explizit theme-konform

### Tests

- 50 lokale Unit-/Robolectric-Tests erfolgreich
- zehn instrumentierte Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich:
  `OK (10 tests)`
- zwei geprüfte Screenshot-Ausgaben für dunkles Smartphone- und helles Tabletlayout

## [0.7.5] – 2026-07-30

### Behoben

- „Alle anzeigen“ erscheint nur noch bei vorhandener, echter Aktion
- Kalender verwendet lokalisierte Monats- und Wochentagsnamen bei weiterhin montagsbasierter Woche
- Genre-Chips und Coverbeschreibung sind vollständig lokalisiert
- Watch-Profil verwendet denselben Status „Aktiv“ wie Theme und Sprache

### Geändert

- Notification Engine erhält austauschbare deutsche und englische Textkopien
- Navigation und Home-Liste besitzen stabile Testtags für gerätefeste UI-Tests
- UI-Tests scrollen explizit zu nicht sichtbaren Lazy-List-Inhalten

### Tests

- 49 lokale Unit-/Robolectric-Tests erfolgreich
- sieben instrumentierte UI-/Screenshot-Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich
- Gradles UTP-Transport ist in der Sandbox gestört; die identischen APKs wurden deshalb
  direkt über `AndroidJUnitRunner` ausgeführt: `OK (7 tests)`

## [0.7.4] – 2026-07-30

### Behoben

- doppelte Einträge und funktionslose Navigationspfeile aus den Einstellungen entfernt
- sichtbare deutsche Kotlin-Texte aus Settings, Detail- und Anime-Karten ausgelagert
- Watch-Profil-IDs durch lokalisierte, nutzerfreundliche Namen ersetzt
- initialer App-Inhalt wartet auf den ersten DataStore-Zustand und vermeidet Theme-Flashes

### Geändert

- Einstellungen sind in App, Überwachung und weitere Bereiche gegliedert
- Benachrichtigungs- und Anbieteroptionen sind bis zur Implementierung deaktiviert
- nicht navigierbare Bereiche zeigen „Demnächst“ statt eines Pfeils
- Akzent- und Statusfarben verwenden verstärkt semantische Theme-Rollen
- sieben instrumentierte UI-/Screenshot-Tests vorhanden, darunter ein Dubletten-Regressionsfall

### Tests

- 46 lokale Unit-/Robolectric-Tests erfolgreich
- sieben instrumentierte Tests erfolgreich kompiliert
- keine Ausführung instrumentierter Tests möglich, da lokal weder AVD noch System-Image installiert ist

## [0.7.3] – 2026-07-30

### Hinzugefügt

- vollständiges helles Material-3-Farbschema
- englische String-Ressourcen und laufzeitfähiger Sprachwechsel
- Root-`AppViewModel` als globale, reaktive Einstellungsquelle

### Geändert

- Theme-Wechsel zwischen Hell, Dunkel und System wirkt sofort in der gesamten App
- Hero-Verläufe verwenden Theme-Farbrollen statt fest codierter dunkler Farben
- noch nicht implementierte Drawer-Ziele sind sichtbar deaktiviert und als „Demnächst“ markiert
- Benachrichtigungs- und Anbieteroptionen kommunizieren ihren Vorbereitungsstatus

### Tests

- 46 lokale Unit-/Robolectric-Tests erfolgreich
- Theme-Auflösung für Hell, Dunkel und System explizit getestet
- Screenshot-Smoke-Tests erfassen nun jeweils ein dunkles und ein helles Layout

## [0.7.2] – 2026-07-30

### Kritisch behoben

- Anime-Metadaten verwenden Room-`@Upsert` statt SQLite-`REPLACE`
- Metadatenaktualisierungen löschen dadurch keine abhängigen Favoriten mehr
- das Öffnen einer Detailseite verwendet nur noch `INSERT IGNORE` und schreibt
  vorhandene Anime-Metadaten nicht erneut
- alle weiteren bisherigen `REPLACE`-Upserts wurden auf nicht destruktives `@Upsert` umgestellt

### Tests

- expliziter Cascade-Regressionstest: Favorit überlebt eine Anime-Metadatenaktualisierung
- 43 lokale Unit-/Robolectric-Tests erfolgreich

## [0.7.1] – 2026-07-30

### Behoben

- Favoritenansicht liest ausschließlich aktivierte Favoriten aus Room
- Detailseite beobachtet den Favoriteneintrag des geöffneten Anime direkt
- Favoritenstatus besitzt einen Ladezustand und wird anschließend optimistisch aktualisiert
- erneutes Favorisieren erzeugt dank Primärschlüssel keinen zweiten Datensatz
- Entfernen eines Favoriten aktualisiert Detail- und Favoritenansicht automatisch
- Sprach- und Watch-Profiländerungen bewahren den ursprünglichen `createdAt`-Wert
- individuelles Favoritenprofil und globales Standardprofil sind getrennt
- aktive Sprachfassung ist als ausgewählter Material-3-Filterchip sichtbar
- gefülltes Herz kennzeichnet einen aktiven Favoriten
- Kalenderfilter ist bis zur Implementierung sichtbar deaktiviert

### Tests

- 42 lokale Unit-/Robolectric-Tests erfolgreich
- instrumentierte UI-/Screenshot-Tests kompilieren erfolgreich

## [0.7.0] – 2026-07-30

### Hinzugefügt

- deutlich sichtbarer Detailseiten-CTA in der Hero-Karte
- persistentes Favorisieren über Room
- lokale OmU-/Dub-/Beide-Auswahl und Watch-Profilwahl auf der Detailseite
- aktive DataStore-Bedienung für Theme, Sprache, Benachrichtigungen, Watch-Profil und Provider
- WatchProfileSelector mit den Profilen Schnell, Ausgeglichen und Sparsam
- prioritätsbasierte automatische Profilwahl mit Akku- und Live-Monitoring-Regeln
- explizite Dub- und Sub-Provider-Szenarien

### Geändert

- „Bald verfügbar“ beginnt nach dem bereits in der Hero-Karte hervorgehobenen Titel
- 39 lokale Tests erfolgreich

## [0.6.0] – 2026-07-30

### Hinzugefügt

- lokale Notification Engine für Reminder, verfügbare und verspätete Releases,
  Providerfehler und Wartungsmodus
- Fake-Provider-Szenarien für Verzögerung, HTTP-Fehler, Wartung, Sprachen und Regionen
- Accessibility-Semantik, Überschriften, Inhaltsbeschreibungen und stabile UI-Test-Tags
- Compose-UI-Tests für Navigation, Hero-Karte, Detailseite, Einstellungen und Drawer
- Screenshot-Smoke-Tests für Smartphone- und Tabletbreiten

### Tests

- 34 lokale Unit-/Robolectric-Tests erfolgreich
- sechs instrumentierte UI-/Screenshot-Tests kompilieren erfolgreich

## [0.5.0] – 2026-07-30

### Hinzugefügt

- Repository-Verträge für Anime, Releases, Provider, News und Einstellungen
- Preferences DataStore für Theme, Sprache, Benachrichtigungen, Watch-Profil und Provider
- navigierbare Anime-Detailseite mit Episoden, Synopsis, Sprachen, Provider und Verlauf
- deterministischer Fake-Provider ohne Netzwerkzugriffe
- Watcher Engine mit Scheduler, ProviderCheck, StatusMachine und NotificationEvent
- Tests für Fake-Provider, Scheduler und Watcher Engine

### Geändert

- Anime-Karten öffnen jetzt die lokale Detailseite
- Versionsstrategie auf semantische Versionierung umgestellt

## [0.4.1] – 2026-07-30

### Behoben

- Statuschip in der Hero-Karte wird auf schmalen Geräten nicht mehr auf Zeichenbreite
  zusammengedrückt
- Watcher-Hinweis und Release-Status besitzen jetzt getrennte, horizontal lesbare Zeilen
- Statuschips sind grundsätzlich auf eine Textzeile begrenzt

## [0.4.0] – 2026-07-30

### Hinzugefügt

- sekundengenauer, lifecycle-sicherer Release-Countdown
- Anzeige von Wochen, Tagen, Stunden, Minuten und Sekunden
- driftfreie Berechnung aus festem Release-`Instant` und aktueller Systemzeit
- automatischer Übergang in die aktive Watcher-Phase am Nullpunkt
- Tests für Wochen-, Tages- und Stundengrenzen, Nullpunkt, vergangene Termine und Zeitzonen

## [0.3.0] – 2026-07-30

### Hinzugefügt

- Room mit KSP, Schemaexport und `AppContainer`
- lokales Favoriten-Repository
- Hero-Karte und Release-Countdown
- aufgewertete Statuskacheln
- animierter Sentinel-Avatar
- Room-/Robolectric-Tests

## [0.2.0] – 2026-07-30

### Hinzugefügt

- Jetpack Compose und Material 3
- Navigation für Start, Kalender, Favoriten, Entdecken und Einstellungen
- Hamburger-Drawer
- Dark-Designsystem und Fake-Daten

## [0.1.0] – 2026-07-30

### Hinzugefügt

- initiales Gradle-Projekt mit Package `de.anisentinel.app`
- Domainmodelle und Grundstruktur
# v0.10.0 – Live-Cache-Korrekturstand (2026-08-02)

- Persistente, app-neustartfeste Cooldowns für AniList und AniSearch ergänzt.
- Rate-Limit-, Dienststörungs- und IP-Sperrantworten respektieren den gespeicherten nächsten Abrufzeitpunkt.
- Erfolgsgebundene Cachebereinigung auf vier Wochen Vergangenheit und acht Wochen Zukunft ergänzt.
- Favoriten und bestätigte Anbieterhistorie werden von der Bereinigung geschützt.
- Fehlerpfade behalten vorhandene Room-Daten unverändert bei.
- Keine fremden Kalender-Snapshots oder erfundenen Ersatzdaten eingebettet.
- 76 Unit-Tests sowie Debug- und Instrumentierungs-APK erfolgreich gebaut.
- WorkManager bleibt wegen lokaler Gradle/PKIX-Abhängigkeitsauflösung offen; Details im Validierungsbericht.
# v0.10.0 – technische Diagnoseimport-Abgabe (2026-08-02)

- Lokalen JSON-Dateiimport über Android `OpenDocument` ergänzt.
- Import ausdrücklich als Entwickler-/Diagnosefunktion gekennzeichnet.
- Schema v1 validiert Quelle, Erzeugungszeitpunkt, Rechtehinweis, IDs, Zeitpunkte und HTTPS-Quellen.
- Vollständige Vorabvalidierung und einzelne Room-Transaktion verhindern Teilimporte.
- Room-Schema v6 ergänzt `anime_external_ids` zur späteren Quellenzusammenführung.
- Austauschbares `GermanMetadataSource`-Interface vorbereitet; kein produktiver AniSearch-Adapter aktiviert.
- README-Aussagen zu Fake-Daten, HTML-Dateiimport und produktiver AniSearch-Nutzung korrigiert.
- 78 Unit-Tests sowie beide APK-Artefakte erfolgreich gebaut; kein Gerät verbunden.
- Keine APK als fertiges Produkt oder Testkandidat ausgeliefert.
# v0.10.0 – robuster Diagnosetest auf realem Gerät (2026-08-02)

- Lokalen JSON-Import nach Einstellungen → Entwickler und Diagnose verschoben.
- Activity-eigenen `OpenDocument`-Launcher für stabilen Lifecycle ergänzt.
- Import vollständig auf `Dispatchers.IO` verschoben.
- Limits: 5 MB, 500 Anime, 5.000 Releases, begrenzte Texte/URLs.
- Strukturierte Rechtebestätigung in UI und JSON-Schema ergänzt.
- Reservierte interne IDs `local-import:<importId>:<externalId>` verhindern Live-Datenüberschreibung.
- Doppelte External-/Release-IDs und unbekannte Anime-Verweise werden vor Room abgelehnt.
- External-ID-Mappingkonflikte können bestehende Zuordnungen nicht umbiegen.
- Importbatch-Tabelle in Room-Schema v7 speichert Quelle, Rechtehinweis, Erzeugungs-/Importzeit und Zeitraum.
- Lokalisierte Importfehler und vollständige Erfolgsdaten ergänzt.
- Kalender öffnet nach Import/Neustart den ersten importierten Termin.
- Lokale Daten erscheinen nun auch auf Start und Entdecken.
- Technische Batch-ID aus normaler Kalender-UI entfernt.
- 84 lokale Tests erfolgreich; realer Migrationstest auf Galaxy S24 Ultra erfolgreich.
- Reale Import-, Kalender- und Neustartpersistenz auf Galaxy S24 Ultra bestätigt.
- Veraltete Navigation-/Golden-Tests bleiben offen und sind im Gerätebericht präzise dokumentiert.
# Diagnose-Testbuild V2 – 2026-08-02

- Automatischen Sprung von Einstellungen zum Kalender entfernt.
- Diagnoseimport über Dataset-ID und SHA-256-Inhaltshash idempotent gemacht.
- Room-Schema auf Version 8 erweitert.
- Lokale Room-Titel auf Start und Entdecken unabhängig vom Live-Quellenfehler sichtbar gemacht.
- Technische Live-Fehlercodes durch verständliche Oberflächentexte ersetzt.
- Navigationstests an die aktuelle Oberfläche angepasst; 11/11 auf realem Gerät bestanden.
- Zwei platzsparende 720p-Geräteclips ergänzt.
# Diagnose-Testbuild V3 – 2026-08-02

- Migration 7 → 8 für beliebig viele vorhandene Importbatches repariert.
- Migrationstests für null, einen und mehrere Altimporte ergänzt.
- Raw-JSON-Hash durch kanonischen fachlichen Inhaltshash ersetzt.
- Regressionstests für Formatierungsidentität und echte Inhaltskonflikte ergänzt.
- Technischen External-ID-Namensraum auf `LOCAL_DIAGNOSTIC` stabilisiert.
- README auf den validierten Diagnose- und tatsächlichen Produktstand aktualisiert.
- Kurzen 720p-Nachweis für Folge 2 am 2. August ergänzt.
- Deaktivierbare WorkManager-Infrastruktur für den erlaubten AniList-Kalender ergänzt.
- AniSearch- und Provider-Hintergrundjobs bleiben bis zur Quellenfreigabe deaktiviert.
# Diagnose-Testbuild V4 – 2026-08-02

- UTC-Tagesgrenzen im AniList-Kalenderpfad durch die lokale Nutzerzeitzone ersetzt.
- Gemeinsame `CalendarTimeWindow`-Berechnung für Source und Repository eingeführt.
- Tests für Berlin-Mitternacht, Monatswechsel sowie Sommerzeitbeginn und -ende ergänzt.
- Persistenten WorkManager-Status mit Versuchszahl, letztem Erfolg und nächstem Retry ergänzt.
- Hintergrundstatus lokalisiert im Kalender sichtbar gemacht.
- Exponentielle Retry-Berechnung separat getestet und auf 24 Stunden begrenzt.
- Separate Regressionstests bestätigen, dass HTTP 403 und 429 bestehende Room-Termine erhalten.
- AniSearch- und Provider-Hintergrundjobs bleiben deaktiviert.

# Diagnose-Testbuild V5 – 2026-08-03

- Exakte lokale Mitternacht in der AniList-Query eingeschlossen.
- Dynamische gemeinsame Gerätezeitzone für Quelle, Repository, Worker und UI eingeführt.
- Netzwerkaktualisierung, frischer Cache und Retry fachlich getrennt.
- Serverseitige und persistente Cooldowns in `retryNotBefore` übernommen.
- Pagination-Sicherheitsgrenze als unvollständigen, nicht schreibenden Abruf behandelt.
- Quellenneutrales Release-Modell eingeführt; AniList-Titel werden nicht als Deutsch ausgegeben.
- Echten WorkManager-`WorkInfo`-Status beobachtbar gemacht.
- Debug-only Diagnosejob und 720p-Nachweis für `RUNNING → RETRY` ergänzt.
- 97 lokale Tests, 4 Migrationstests und 11 Navigationstests erfolgreich.
# v0.11.0-diagnostic

- Release-spezifischer Anbietercheck mit eindeutigem WorkManager-Namen.
- JustWatch-Titeltreffer und Episodenverfügbarkeit werden getrennt bewertet.
- Öffentlicher Crunchyroll-Episodenchecker ohne Login-, Playback-, DRM- oder Manifestzugriffe.
- AniWorld-Metadatenfallback zehn Minuten nach dem Solltermin.
- `DELAYED_CONFIRMED` nur nach zwei negativen, unabhängigen Prüfungen; Fehler bleiben unbestätigt.
- AlarmManager-Wecksignal plus WorkManager-Ausführung für gesperrte und inaktive Geräte.
- Neuplanung nach Neustart, App-Update, Zeit- und Zeitzonenänderung.
- Benachrichtigungen enthalten, soweit vorhanden, Titel, Staffel und Folge.
- Countdown wird nach Ablauf nicht mehr als `00:00:00` dargestellt.
- Favoritenfilter verwenden reale Zeitgrenzen; Sortierung wird in DataStore gespeichert.
- Parser- und Status-Regressionsprüfungen ergänzt.
# v0.14.0-diagnostic

- echten V13-Gerätecrash aus dem Android-Crashpuffer analysiert und dokumentiert
- doppelte Compose-LazyColumn-Schlüssel durch stabile JustWatch-IDs ersetzt
- Such- und Discover-Ergebnisse zusätzlich anhand ihrer JustWatch-ID dedupliziert
- `Aktuell` und `Demnächst` auf Anime-Ebene gegenseitig exklusiv gemacht
- Discover von der vollständigen Anime- und Releasehistorie entkoppelt
- Discover verwendet nur aktive AniWorld-Releases für Laufend- und Sprachfilter
- parallele Discover-Refreshes dedupliziert und veraltete Suchläufe abgebrochen
- Regressionstests für doppelte interne Anime-IDs und überlappende Favoriten ergänzt

# v0.13.0-diagnostic

- Startseite und Releasezähler auf echte `ANIWORLD_CALENDAR`-Titel mit GER SUB/GER DUB begrenzt.
- Globale JustWatch-Suche vom Genre-Browser getrennt; kein erzwungenes `ani` bei Suchtexten.
- Unicode-/Akzentnormalisierung ergänzt (`pokemon` entspricht `Pokémon`).
- Eigene Katalogkarten ohne künstliche Episode 0, Releasecountdown oder Releasestatus eingeführt.
- DVD-, Blu-ray- und Buchangebote aus primären Anbieterlisten entfernt.
- Favoriten-Anbietersortierung verwendet einen stabilen primären Streaminganbieter.
- AniWorld-Synchronisierung bewahrt bereits vergangene normale Releases dauerhaft.
- Discover-Aktualisierung auf zwei begrenzte Katalogrequests reduziert statt serieller Titel-Provider-Vollabfrage.
- Regressionstests für Katalogtrennung, Suche, Anbieterfilter und Releasehistorie ergänzt.

# v0.12.0-diagnostic

- Bottom Navigation auf fünf einzeilige, adaptive Labels korrigiert und auf 1080×2340 sowie 720×1560 geprüft.
- Favoritenfilter verwenden den lokalen Kalendertag: `Aktuell` ist heute, `Demnächst` beginnt morgen.
- `Abgeschlossen` kombiniert fehlende aktive Termine mit bekanntem Serienende statt nur `STOPPED` zu prüfen.
- Filterabhängige Leerzustände ergänzt.
- Sechs persistierte Favoritensortierungen ergänzt.
- Eigenständiger `DiscoverViewModel` und realer JustWatch-DE-Genre-/Titelcache in Room.
- Reale Genre-, Typ-, Sprach-, Anbieter- und Sortierfilter im Entdecken-Bereich.
- Globale Startseitensuche für aktuelle, ältere und abgeschlossene JustWatch-DE-Titel.
- Reale Poster und Anbieter aus JustWatch werden gespeichert und angezeigt.
- Verschiebungskarten zeigen alten Termin, neuen Termin und vorhandenen Grund.
- Room-Migrationen 12→13 und 13→14 sowie Schemaexport ergänzt.
# v0.24.2-v24-auto-provider-probes

- experimentelle, gekapselte Crunchyroll-CMS-, Crunchyroll-Public-Web- und ADN-DE-Metadatenprobes ergänzt
- `AVAILABLE`, `NOT_AVAILABLE_YET` und technische `CHECK_FAILED`-Ergebnisse fachlich getrennt
- öffentliche Crunchyroll-Serie ohne Login real mit Staffel, Episode, Watch-ID und deutschem Untertitel validiert
- `/de/videos/new` ausschließlich als `RELEASE_SIGNAL` modelliert; Dub-/Untertitelnachträge setzen niemals allein `AVAILABLE`
- ADN strikt auf `X-Target-Distribution: de` begrenzt; `vostde` und `vde` separat ausgewertet
- stabile Series-/Season-/Episode-IDs und Provider-URLs über Room-Schema 18 persistiert
- manuellen historischen Providercheck ohne Alarm, AUTO, Benachrichtigung oder `expectedAt`-Änderung ergänzt
- echte Episoden-/Serien-URLs per `ACTION_VIEW` aus Episodenkarten öffnbar; keine geratenen URLs
- sekundengenauen laufenden und eingefrorenen Verzögerungstimer ergänzt
- 194 JVM-Tests und 7 Room-Migrationstests auf dem realen Gerät erfolgreich
# v0.24.4-v24-provider-history

- anonymen ADN-DE-Historienimport hinter dem bestehenden Providerinterface ergänzt
- ausschließlich explizite Episoden-Veröffentlichungsdaten übernommen; keine Rhythmus- oder Simulcast-Ableitung
- `vostde` und `vde` als getrennte GER-SUB-/GER-DUB-Historieneinträge persistiert
- Quellenpriorität und sichtbare Konfliktmarkierung für historische Termine ergänzt
- historische Importe von AUTO, WorkManager, Alarmen und Benachrichtigungen ausgeschlossen
- öffentliche Providerlinks vor `ACTION_VIEW` validiert und fehlersicher geöffnet
- Room-Schema 19→20 samt neun realen Gerätemigrationstests ergänzt
- 202 JVM-Tests und ein echter anonymer ADN-Live-Diagnosetest erfolgreich
- monatsbezogenen Crunchyroll-/ADN-Historienabgleich im Kalender ergänzt; kein unerwarteter Monatssprung
- öffentliche Crunchyroll-Watch- und Suchauflösung nur bei exaktem Titelmatch ergänzt
- automatische Crunchyroll-Historienergänzung beim Öffnen einer sicher zugeordneten Serie ergänzt
- Release-Verlauf nach AniWorld, Crunchyroll und ADN gruppiert
- bestätigte Episodenverfügbarkeit gegen spätere technische Downgrades geschützt
- vorhandene fehlerhaft herabgestufte Verfügbarkeiten beim App-Start repariert
- 204 JVM-Tests erfolgreich
# v0.24.5-v24-crunchyroll-series-id

- Crunchyroll-Series-ID als stabilen Primärschlüssel für öffentliche Serienseiten festgelegt
- lokalisierte, relative, sluglose und vollständige `/series/{G…}`-URLs vereinheitlicht
- öffentlich eingebettete `series_id`- und `seriesId`-Felder aus Watch-Seiten erkannt
- kanonische URL `https://www.crunchyroll.com/de/series/{seriesId}` ergänzt
- Titelsuche nur noch als Fallback; gefundene reale hrefs werden dauerhaft gespeichert
- vier reale deutsche Crunchyroll-Serienseiten ohne Login auf SM-S928B mit HTTP 200 validiert
- Regressionstests für Red River, Victoria of Many Faces, The Oblivious Saint und I Want to Love You ergänzt
- 205 JVM-Tests, 9 Room-Migrationstests sowie Crunchyroll-/ADN-Livediagnosen erfolgreich
