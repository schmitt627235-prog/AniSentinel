# Changelog

## 0.25.16 Build 22 (versionCode 68)

- Conan: Beide Crunchyroll-Kataloge und der best?tigte Apple-TV-Katalog bleiben intern getrennt, erscheinen aber in der normalen Provider- und Staffelnavigation ohne technische Katalog-IDs.
- Der ?ffentliche Apple-TV-Katalog wird staffelweise vollst?ndig ?ber Apples eigene Metadaten-Paginierung ermittelt. Fehlende Apple-Folgen werden nicht aus anderen Quellen erg?nzt; eine teilweise Antwort ersetzt keinen vollst?ndigeren Bestand.
- Die laufende Conan-Staffel von Crunchyroll erscheint als ?32 (Aktuell)?; urspr?ngliche Provideridentit?t und Links bleiben erhalten.
- Apple TV und AKIBA PASS sind nun in der Anbieterverwaltung separat ein- und ausschaltbar.
- Debug-APK und JVM-Tests lokal gepr?ft; Updateinstallation auf dem Android-Ger?t ohne L?schen von App-Daten getestet.

## 0.25.16 Build 21 (versionCode 67)

- AKIBA-PASS-Katalog ?ber ?ffentliche Produkt- und Staffelseiten integriert: exakter Titel-/Staffelabgleich, Sprachfassung, Folge, Beschreibung, Laufzeit und direkter Anbieterlink. Blo? gelistete, noch nicht buchbare Produkte gelten nicht als verf?gbar.
- Verifizierte AKIBA-PASS-Folgen werden als anbieterspezifische historische Eintr?ge gespeichert und auf Detailseiten angezeigt. Room-Migration 28?29 erg?nzt optionale Beschreibungs- und Laufzeitfelder.
- Anbieteranfragen verwenden einen transparenten AniSentinel-User-Agent sowie einen begrenzten Cache; Konto-, Checkout- und Player-Endpunkte bleiben unber?hrt.
- Apple TV bleibt ein dokumentierter offener Punkt: Die ?ffentliche Conan-Seite zeigt Staffeln, aber nur einen Teil der Episoden; deshalb kein irref?hrender vollst?ndiger Apple-TV-Katalog und noch keine Conan-Zusammenf?hrung mit Apple TV.
- Debug-Signatur ist lokal wiederverwendbar konfiguriert; Schl?ssel und Zugangsdaten werden nicht ver?ffentlicht. Gezielte AKIBA-PASS-Tests (8), Room-Migrationstest und Debug-Build bestanden; Updateinstallation und Start auf dem angeschlossenen Android-Ger?t erfolgreich.

## 0.25.16

- **Build 19 / versionCode 65:** Die Future-Detailseite aktualisiert Anime2You-Meldungen beim ?ffnen automatisch; ein manueller Pull-to-Refresh bleibt m?glich.
- Die Detailansicht verwendet eine globale positive News-Whitelist: Trailer/Teaser, Starttermine, Verschiebungen, Disc-Releases sowie konkrete Anbieter- und Lizenzmeldungen.
- Kooperationen, Interviews, Autoren-Aussagen, redaktionelle Meinungen, Merchandising, Figuren, CDs, Soundtracks und Rankings bleiben aus Future-Detailseiten ausgeschlossen; der allgemeine News-Bereich bleibt davon unber?hrt.
- Bei Fortsetzungen muss die im Artikel genannte Staffel mit der Staffel des AniList-Titels ?bereinstimmen. Unnummerierte Franchise-Meldungen und Meldungen zu ?lteren Staffeln werden nicht mehr einer Fortsetzung zugeordnet; eigenst?ndige Titel ohne Staffelzusatz bleiben zul?ssig.
- Englische, Romaji-, native und alternative Schreibweisen werden f?r Anime2You-Abfragen normalisiert. Dazu geh?ren Staffelvarianten, ausgeschriebene Ordinalzahlen, `Dai San Maku`, kurze Franchise-Aliase und die Schreibvariante `Russiya-go`/`Russia-go`.
- Veraltete AniSearch-DACH-Cachedaten k?nnen Future-Titel nicht mehr umbenennen; konservatives JustWatch-Matching verhindert ebenfalls fremde Titel?bernahmen.
- Gezielte Anime2You-Parser-, Klassifizierungs-, Titel- und Staffeltests sowie die vollst?ndige JVM-Testsuite bestanden; Debug-APK wurde gebaut. Die abschlie?ende Build-19-Updateinstallation wurde wegen einer abweichenden lokalen Debug-Signatur von Android sicher abgelehnt; es wurden weder App noch App-Daten gel?scht.
- **Build-18-Hotfix:** Saisonale Favoriten erhalten nun eindeutige Katalogpositionen. Zuvor wurde jeder vorgemerkte Zukunftstitel auf Position `0` geschrieben, wodurch in ?Favoriten ? Season? nur ein Titel ?brig blieb.
- Die Season-Zuordnung wird bei jedem erfolgreichen Laden der erwarteten Titel aus allen aktiven AniList-Favoriten neu aufgebaut. Dadurch wird auch ein bereits fehlerhafter lokaler Bestand automatisch repariert.
- Ein Regressionstest best?tigt Speicherung, Reihenfolge und Abruf von 120 saisonalen Favoriten.
- **Aktueller Build 18 / versionCode 64:** Eine frische Installation zeigt ?Hei? erwartete Titel? auch w?hrend der derzeitigen AniList-GraphQL-St?rung aus einem mitgelieferten, zuletzt erfolgreich gepr?ften AniList-Grundbestand. Erfolgreiche Liveantworten ersetzen ihn automatisch.
- Der AniList-Bestand ist als eigener Bereich im JSON-Backup ausw?hlbar und wird nach einer Neuinstallation wiederhergestellt.
- Backup-Restore-Absturz behoben: Fehlende Anime-Elterndatens?tze werden vor ihren Favoriten angelegt, sodass Room-Fremdschl?ssel nach einer Neuinstallation nicht mehr verletzt werden.
- Anime2You-News werden titelbezogen ?ber die reale WordPress-Suche statt ausschlie?lich aus dem begrenzten neuesten RSS-Feed ermittelt; Varianten, Normalisierung, URL-Deduplizierung und technische Fehlerzust?nde wurden erg?nzt.
- Die automatische AniSearch-DACH-Anreicherung ist im regul?ren Produktivabruf pausiert: Der erste reale Inhaltsrequest erhielt HTTP 429 ohne `Retry-After`. Es findet keine Umgehung statt. Bei einem zul?ssigen projektspezifischen API-Zugang wird die vorbereitete Integration wieder aktiviert.
- **Build 18 / versionCode 64:** AniSearch-DACH-Abruf auf eine wiederverwendete Cookie-Session mit kontrollierten Redirects und einmaligem Index-Warm-up umgestellt.
- F?r jede echte AniSearch-Anfrage gelten nun global mindestens sechs Sekunden Abstand, h?chstens eine aktive Anfrage sowie ein sofortiger globaler Stopp bei HTTP 429 einschlie?lich `Retry-After` beziehungsweise konservativem Backoff.
- Der produktive DACH-Seed wurde entfernt; DACH-Best?tigungen entstehen nur noch durch die generische Live-/Cache-Pipeline.
- Cachetreffer umgehen neue Netzwerkzugriffe; explizite Staffel-/Cour-Widerspr?che werden zus?tzlich auf der Detailseite verworfen.
- Session-, Cookie-, Abstand-, Parallelit?ts-, 429-, Cache-, Regionalblock- und Japan-/DACH-Zeittrennung sind durch Regressionstests abgesichert.
- **Build 17 / versionCode 63:** AniList?AniSearch?DACH-Verkn?pfung vervollst?ndigt.
- Alle englischen, Romaji-, nativen und synonymen AniList-Titel werden als deduplizierte Suchvarianten genutzt; generische Season-/Staffel-/Dai-Ki-/Part-/Cour-Aliase bleiben erhalten.
- Konservative Trefferwertung verwirft Staffel-/Cour-Widerspr?che und gleichwertig mehrdeutige Kandidaten statt einen falschen AniSearch-Titel zu erzwingen.
- AniSearch-Matches werden separat gecacht; bekannte IDs f?hren direkt zur Detailseite und vermeiden erneute Titelsuchen.
- Nur ein vorhandener regionaler `ul.xlist.row.simple.infoblock` ohne deutschen Eintrag erzeugt `DACH_NOT_LICENSED_YET`; fehlender Block, HTTP-/Parser-/Matchfehler bleiben `DACH_UNKNOWN`.
- Deutscher Publisher und DACH-Verf?gbarkeitsbeginn werden ausschlie?lich aus demselben deutschen L?ndereintrag gelesen; Beobachtungs- und Erkennungszeit bleiben getrennt.
- Die ung?ltige generierte AniSearch-Such-URL im manuellen Detailbutton wurde entfernt; ohne sichere ID ?ffnet die neutrale AniSearch-Anime?bersicht.
- AniSearch-Anfragen laufen prozessweit seriell. `Retry-After` wird beachtet; ohne Header greift ein exponentieller Backoff von 30 Minuten bis maximal 24 Stunden. Nach 429 startet keine weitere AniSearch-Anfrage.
- Aliasvarianten werden lokal dedupliziert und nur nach einer erfolgreichen, aber ergebnislosen Suche nacheinander probiert; Netzwerkfehler erzeugen kein Varianten-Fanout.
- Drawer-Bereich ?Hei? erwartete Titel? mit echter Liste und Detailnavigation aktiviert.
- Noch nicht gestartete Anime werden ?ber AniLists dokumentierte GraphQL-API geladen und nachvollziehbar nach AniList-Popularity sortiert.
- Laufende sowie beendete Titel werden ausgeschlossen; Saisonfilter entstehen dynamisch aus den realen Daten.
- Kanonische Future-Identit?t, generische Staffelalias-Aufl?sung und getrennte DACH-Zeitfelder erg?nzt.
- Moderater 24-Stunden-Cache beh?lt g?ltige Future-Daten bei Netzwerk- oder Parserfehlern.
- DACH-Angaben bleiben ohne belastbaren Beleg ehrlich unbest?tigt; sp?tere h?her priorisierte Best?tigungen ersetzen den sichtbaren Negativstatus.

## 0.25.15

- Selektive Backup- und Restore-Kategorien mit ?Alles ausw?hlen? und ?Alles abw?hlen? erg?nzt.
- Compose-Activity-Aufl?sung korrigiert, sodass Androids Datei- und App-Einstellungsdialoge zuverl?ssig ?ffnen.
- Restore zeigt validierte enthaltene Bereiche vor jeder ?nderung und ?berschreibt nur die best?tigte Auswahl.
- Redundante ?Aktiv?-Zus?tze an bedienbaren Einstellungskarten entfernt.

- Kalender-Einstellungen f?r Sub, Dub, vergangene Termine und Favoritenfilter.
- Globaler, r?ckw?rtskompatibler Anbieter-Sichtbarkeitsfilter f?r Crunchyroll, ADN, Netflix, Disney+ und aniverse.
- Versioniertes lokales JSON-Backup und sicher validierter Merge-Restore ?ber Androids Dateiauswahl.
- Datenschutzseite mit realem Berechtigungsstatus, Systemeinstellungslink und best?tigter L?schung lokaler Nutzerdaten.
- Alle vier bisherigen Platzhalter unter Einstellungen als echte Unterseiten aktiviert.

## 0.25.14

- Gemeinsamen `ReleaseDisplayState` f?r Home, Favoriten und Detail eingef?hrt; Episode, Verschiebung und Countdown stammen aus derselben kanonischen Releaseidentit?t.
- Veraltete ?ltere Kalenderzeilen k?nnen einen verschobenen, neueren Release nicht mehr auf der Startseite verdr?ngen.
- Detail-Verschiebungskarte komprimiert, technische Sprachcodes durch verst?ndliche Texte ersetzt.
- Anime2You-Verweise werden als gemeinsame Herkunft via AniWorld statt als unabh?ngige Doppelbest?tigung dargestellt.
- Regressionstests f?r Bookworm-artige Daten, identische Home-/Detailaufl?sung sowie getrennte Sub-/Dub-Verschiebungen erg?nzt.

## 0.25.13

- AniWorld-Sprachregel vereinheitlicht: explizites Dub wird GER_DUB, alle anderen AniWorld-Kalender- und Verschiebungseintr?ge GER_SUB.
- Verschiebungen bleiben von aktuellem Sub-Release getrennt und nennen Staffel, Folge und Sprache.
- Offensichtlich widerspr?chliche R?ckw?rtsdaten bei als verz?gert markierten AniWorld-Eintr?gen werden chronologisch plausibilisiert (z. B. Chainsmoker Cat 27.08. auf 03.09.).

## v0.25.12 ? episodengenaue Verschiebungen ? 2026-08-24

- Explizit gelieferte Verschiebungsfolgen werden niemals aus dem aktuellen Providerfortschritt umnummeriert.
- Kompakte Verschiebungshinweise erscheinen auf Releasekarten nur f?r genau die dort dargestellte Episode und nennen Staffel sowie Folge.
- Damit bleibt ?Slime? S4E17 GER_DUB am 28.08. eine Dub-Verschiebung und wird nicht f?lschlich als S4E20 ausgegeben.

## v0.25.11 ? kanonischer aktueller Releasezustand ? 2026-08-24

- Providerneutral gespeicherte Kalenderzeilen mit bereits best?tigtem `AVAILABLE`-Status z?hlen nun global als best?tigter aktueller Release.
- ?Letzter Release? wird innerhalb der aktuellen Staffel sprach?bergreifend aus der h?chsten best?tigten Episode bestimmt; ein ?lterer Dub-Termin verdr?ngt nicht mehr die neuere OmU-Folge.
- Aktive Verschiebungen werden gegen diesen kanonischen Staffelstand aufgel?st, sodass n?chster Release, Countdown und Verschiebung dieselbe Episode und denselben Ersatztermin verwenden.
- Regressionstest f?r den real beobachteten Zustand ?S3E19 verf?gbar, S3E16 Dub ebenfalls vorhanden? erg?nzt.

## v0.25.8 ? einheitliche Providerpr?fung und T+10-Sicherheitsfallback ? 2026-08-21

- Alle von JustWatch Deutschland best?tigten und direkt unterst?tzten Anbieter werden je f?lliger Episode gepr?ft; die Nutzerpr?ferenz steuert Darstellung und Deep-Link, nicht mehr den Umfang der Pr?fung.
- Direkte Pr?fergebnisse unterscheiden verf?gbar, technisch erfolgreich noch nicht verf?gbar und technisch nicht auswertbar. Ein Parserfehler wird nicht als fehlende Episode ausgegeben.
- AniWorld wird exakt ab zehn Minuten nach dem erwarteten Termin als unabh?ngiger Sicherheitsbeleg gepr?ft, auch nach einem direkten negativen Ergebnis. Direkte Providerpr?fungen laufen nach einer Fallback-Best?tigung weiter.
- Availability und Releasezustand werden f?r fachlich identische Episoden ?ber Anime, Staffel, Episode und Sprachfassung abgeglichen. Dadurch beenden auch Belege unter einer anderen Quell-ID falsche Verz?gerungsanzeigen.
- Der Netflix-Webparser wertet zus?tzlich strukturierte eingebettete Staffel-, Episoden-, ID- und URL-Metadaten aus. Nicht eindeutig auswertbare Seiten liefern `CHECK_FAILED` statt eines Falschnegativs.
- Historische Pr?f- und Erkennungszeitpunkte zeigen global Datum und Uhrzeit.
- Regressionstests decken BLACK-TORCH-artige Quell-ID-Dubletten, Chainsmoker Cat S1E8, T+10-Grenzen und Room-Statusabgleich ab.

## v0.25.7 ? lange Serien und Provider-Staffelkataloge ? 2026-08-21

- Nutzer k?nnen ADN oder Crunchyroll als animeweiten bevorzugten Anbieter w?hlen, sobald JustWatch Deutschland den direkten Anbieter f?r den Titel best?tigt; Amazon-Channel- und sonstige Shopangebote werden nicht als Direktadapter ausgegeben.
- Die Anbieterwahl steuert nun tats?chlich Episodenstatus, historische Katalogzeilen und Deep-Links. Daten eines anderen Providers werden nach einer manuellen Auswahl nicht mehr untergemischt.
- Bei einer Providerwahl verwendet die Staffelauswahl dessen eigene best?tigte Katalogstruktur. ADN-Abschnitte werden als Sagas bezeichnet; AniWorld-Staffelnummern werden nicht mehr blind mit Provider-Staffelnummern gleichgesetzt.
- Eine Crunchyroll-Auswahl st??t die ?ffentliche Katalogaufl?sung ?ber die bereits gespeicherte reale Anbieter-URL an. Bei technisch nicht aufl?sbarem Katalog werden keine ADN-Daten als Crunchyroll-Ergebnis ausgegeben.
- Echte historische ADN- und Crunchyroll-Metadaten best?tigen ihre kanonischen Staffeln und erzeugen transaktional deutsche Provider-Staffel-Mappings.
- Die Staffelauswahl bleibt f?r lange Serien sichtbar und ist horizontal scrollbar; der aktuelle Kalender-Arc wird neben historisch best?tigten Staffeln erhalten.
- Reine AniWorld-Kalenderzeilen oder mehrdeutige alte Backfill-Zeilen erzeugen weiterhin keine Phantomstaffeln.
- Der automatische Crunchyroll-Historienimport versucht nach einer nicht aufl?sbaren direkten Provider-URL zus?tzlich die exakte ?ffentliche Titelsuche.
- Wenn dieselbe Staffel bei Crunchyroll und ADN best?tigt ist und keine manuelle Vorgabe besteht, bleibt Crunchyroll die automatische Priorit?t.

## v0.25.6 ? belastbare Staffel- und Providerpr?ferenzen ? 2026-08-21

- Eine animeweite Providerpr?ferenz gilt als Standard f?r alle Staffeln; eine staffelbezogene Auswahl ?berschreibt sie gezielt.
- Auswahlchips zeigen ausschlie?lich Provider, deren konkrete Staffel f?r Deutschland best?tigt ist. Allgemeine Katalogreferenzen erzeugen keine ausw?hlbaren Provider.
- Gespeicherte Pr?ferenzen auf nicht mehr best?tigte Staffelprovider werden nicht erzwungen; die App f?llt auf einen best?tigten Provider beziehungsweise die automatische Auswahl zur?ck und kennzeichnet die ung?ltige Vorgabe.
- Solange f?r eine Staffel noch kein Mapping existiert, pr?ft die generische Discovery-Pipeline Crunchyroll zuerst und anschlie?end die ?brigen Referenzen; daraus wird weiterhin erst nach einem echten Staffelcheck ein best?tigtes Mapping.
- Staffelchips entstehen aus verifizierten kanonischen Staffeln und best?tigten Provider-Mappings. Mehrere alte, ausschlie?lich aus Release-Backfill stammende Staffelnummern werden nicht mehr als reale Staffelstruktur ausgegeben.
- Regressionstests decken Phantomstaffeln, verteilte Provider-Staffeln, animeweite Standards, Staffel-Ausnahmen und ung?ltig gewordene Pr?ferenzen ab.

## v0.25.5 ? Release-Lifecycle, Staffelprovider und Anime-Katalog ? 2026-08-21

- Alte unbest?tigte Releases bleiben nicht mehr sieben Tage als enge aktive Provider-W?chter bestehen. Ein Nachfolger beendet den alten engen Watcher nach einer kurzen Karenz; sp?testens nach 24 Stunden wird er `STALE_UNCONFIRMED`.
- `STALE_UNCONFIRMED` erfindet keine Verf?gbarkeit, sendet keine technische Fehlermeldung und bleibt f?r sp?teren Historien-Backfill erhalten.
- Technische Providerfehler werden persistent providerweit gez?hlt. Eine sichtbare Meldung ist erst nach mindestens drei aufeinanderfolgenden Fehlern ?ber mindestens zehn Minuten m?glich; ein sechsst?ndiger Provider-Cooldown verhindert Meldungsfluten.
- `AVAILABLE` und `NOT_AVAILABLE_YET` setzen den technischen Fehlerzustand zur?ck.
- Kanonische Anime-Staffeln, Provider-Staffel-Mappings und manuelle Providerpr?ferenzen werden getrennt in Room 26 gespeichert.
- Providerwahl ist pro Staffel persistent. Ohne Nutzerauswahl wird Crunchyroll nur bevorzugt, wenn die konkrete Staffel im deutschen Mapping best?tigt ist.
- JustWatch-Aktualisierungen ?berschreiben manuelle Providerwahlen nicht. Checker, Staffelzuordnung und Providerlink verwenden dieselbe effektive Auswahl.
- Entdecken ist auf eindeutig erkannte Anime, Anime-Filme und belegte Live-Action-Adaptionen begrenzt. Gew?hnliche Filme und Serien aus dem breiteren JustWatch-Katalog werden ausgeblendet.
- Genre- und Anbieterfilter im Entdecken-Bereich werden nur aus dem zul?ssigen Anime-Katalog gebildet.
- JustWatch-Katalogabruf und titelweise Providerpr?fungen verwenden getrennte, rate-limitierte Instanzen; eine Providerwarteschlange blockiert Entdecken nicht mehr.
- Ein erfolgreicher Katalogabruf setzt den UI-Fehlerzustand nun korrekt auf `null`; Genres und Provideranreicherung laufen nachgelagert und k?nnen den Katalogerfolg nicht mehr verf?lschen.
- Regressionstests decken Release-Lifecycle, Providerfehler, Staffelwahl und die Anime-Kataloggrenze ab.

## v0.25.4 ? Provider-first Verf?gbarkeitspr?fung ? 2026-08-16

- JustWatch Deutschland dient ausschlie?lich als Titel- und Anbieterresolver; konkrete Episoden werden direkt beim ausgew?hlten Anbieter gepr?ft.
- ?ffentliche Direktadapter f?r Crunchyroll, ADN, Netflix, Disney+ und ANIVERSE erg?nzen den AniWorld-Fallback.
- Provider-, Staffel- und Episodenidentit?ten sowie belegte Ziel-URLs werden stabil in Room wiederverwendet.
- `NOT_AVAILABLE_YET` bleibt still; technische Providerfehler und best?tigte Verf?gbarkeit verwenden getrennte Statussemantik.
- Historische Providerimporte erzeugen keine Alarme, Benachrichtigungen oder k?nstlichen F?lligkeiten.

## v0.25.3 ? Ergebnisbenachrichtigungen und pr?fbare Release-Statistik ? 2026-08-16

- F?lligkeit und Start einer Providerpr?fung laufen ohne Nutzerbenachrichtigung.
- Neu best?tigte Verf?gbarkeit meldet Titel, Folge und Anbieter genau einmal; echte technische Pr?ffehler melden Titel und Fehlertext.
- Historisch nachgeladene, bereits abgelaufene Verschiebungen erzeugen keine versp?teten Push-Meldungen.
- Die operative Verschiebungsseite blendet providerbest?tigte abgeschlossene F?lle aus; die vollst?ndigen Datens?tze bleiben in der Statistik erhalten.
- Alle sieben Statistik-Karten ?ffnen eine smartphonegerechte Liste. Z?hler und Liste stammen aus derselben Datenmenge.
- Mitternachts-Platzhalter werden nicht als normale Uhrzeit dargestellt; eine belegte Titel-/Staffelzeit kann intern als `DERIVED_TITLE_PATTERN` abgeleitet werden, echte Mitternacht erfordert `EXACT_MIDNIGHT`.

## v0.25.2 ? deutsche Metadaten und vollst?ndige dynamische Aktualisierung ? 2026-08-14

- JustWatch-Handlungen werden unabh?ngig vom `Accept-Language`-Header sprachlich gepr?ft; englische reale Quelltexte werden im Diagnosebuild deutsch ?bersetzt und als `TRANSLATED_FROM_JUSTWATCH` gekennzeichnet.
- Room 25 speichert Originaltext, erkannte Originalsprache und Herkunft der deutschen Fassung; Migration 24?25 erh?lt Favoriten und vorhandene Daten.
- HTML-Entities werden f?r Handlung, Genres und Studios dekodiert.
- Genres werden deutsch kanonisiert und semantisch dedupliziert.
- Ein leerer Studioabschnitt wird ausgeblendet; Studios werden niemals erfunden.
- Episodenkarten werden unabh?ngig von mehreren Provider-/Historienzeilen auf genau eine Karte je Episodennummer reduziert.
- Release-Sprache und Status werden in der normalen Detailansicht deutsch dargestellt.
- Der historische Providerimport ist als sekund?re Aktion ?Historische Termine aktualisieren? benannt.
- Anbieter, Aktuelle Season, Dub-Releases und Statistik besitzen nun ebenfalls Pull-to-Refresh mit relevanter Providerpr?fung ohne Scheduler-/Notification-Erzeugung.
- 251 Unit-Tests pr?fen zus?tzlich ?bersetzungspolitik, Entity-Decoding, Genre-Normalisierung, Episodendubletten und Sichtbarkeit der Verf?gbarkeitsaktion.

## v0.25.1 ? JustWatch-Metadaten und mobile Episodenaktionen ? 2026-08-13

- Handlung, Genres und ? soweit ?ffentlich tats?chlich vorhanden ? Produktionsstudio werden ?ber ein eindeutiges JustWatch-DE-Match nachgeladen und in Room gespeichert.
- Gespeicherte stabile JustWatch-IDs haben Vorrang; mehrdeutige Treffer ?bernehmen keine fremden Metadaten.
- JustWatch-Metadaten ver?ndern keinen Episoden-Verf?gbarkeitsstatus.
- Bestehende Titel k?nnen beim ?ffnen beziehungsweise manuellen Aktualisieren nachtr?glich angereichert werden.
- Room 24 erg?nzt `description` und `studios` am JustWatch-Katalogcache; Migration 23?24 erh?lt Favoriten und vorhandene Daten.
- Episodenkarten zeigen Aktionen untereinander ?ber die Kartenbreite.
- ?Verf?gbarkeit jetzt pr?fen? erscheint nur bei den neuesten relevanten, noch nicht best?tigten Episoden und verschwindet nach `AVAILABLE`.
- Pull-to-Refresh wurde auf Detailseite und Entdecken erg?nzt; Start, Kalender, Favoriten, News und Verschiebungen verwenden dieselbe bestehende Material-3-Mechanik.
- 244 JVM-Tests erfolgreich; Update auf dem Testger?t behielt 38 Favoriten.

- v0.25.0: Mehrw?chige und offene Pausen setzen den bisherigen Release-Rhythmus au?er Kraft; ein neuer Sendetag oder eine neue Uhrzeit wird erst durch einen realen Quelltermin nach der Wiederaufnahme ?bernommen.

- v0.25.0: Verschiebungen erscheinen titelweit und prominent rot auf Start, Suche, Entdecken, Kalender, Favoriten und Details; die eigentliche Termin?nderung bleibt streng nach Staffel, Episode und Sprache getrennt.
- v0.25.0: Der Crunchyroll-Historienimport l?dt vollst?ndige, begrenzte Episodenlisten statt der kleinen CMS-Standardseite; best?tigte historische Provider-Releases erscheinen global als verf?gbar.

## v0.25.0 ? Verschiebungen ? 2026-08-13

- Neue Kategorie ?Entdecken & Mehr ? Verschiebungen? mit Room-basierter Liste und interner Detailseite.
- Reale AniWorld-Verschiebungsseite als regelm??ig synchronisierte, gespeicherte Quelle integriert.
- Urspr?nglicher Termin, optionaler Ersatztermin, Grund, Sprache, Quelle und Pr?fzeitpunkt werden ohne erfundene Werte gespeichert.
- Eindeutig zugeordnete Releases stoppen den alten Due-/AUTO-/Fallback-Plan; bekannte Ersatztermine werden neu geplant.
- GER SUB und GER DUB bleiben strikt getrennt; kombinierte Quellmeldungen erzeugen zwei semantische Zuordnungen.
- Revisionen deduplizieren unver?nderte Meldungen und erlauben Benachrichtigungen nur bei erstmaliger oder relevanter ?nderung.
- Bereits best?tigte Verf?gbarkeit wird nicht durch eine Verschiebungsmeldung ?berschrieben.
- Room-Schema 22 und reale HTML-Parser-Fixture vom 13.08.2026 erg?nzt.

## v0.24.9 ? automatischer Favoriten-Historienbackfill ? 2026-08-12

- Neu hinzugef?gte und wiederhergestellte Favoriten erhalten automatisch einen persistenten historischen Backfill.
- Room speichert pro Favorit `PENDING`, `RUNNING`, `COMPLETED` oder `RETRY_REQUIRED`; der Vorgang wird nach App- und Ger?teneustarts fortgesetzt.
- Crunchyroll verwendet bekannte Series-IDs/URLs oder eine generische exakte Titelsuche; ADN-Show-IDs werden auch aus realen Anbieterlinks aufgel?st.
- `COMPLETED` wird nur nach einem echten historischen Import oder einer sicheren Anreicherung gesetzt.
- Der Backfill erzeugt keine Alarme, Notifications, AUTO-Pr?fungen oder AniWorld-T+10-Fallbacks.
- Room 21 migriert alle bereits aktiven Favoriten automatisch nach `PENDING`.

## v0.24.8 ? Favoritenklassifizierung und Historie ? 2026-08-12

- Historische Crunchyroll-/ADN-Releases werden wieder f?r `Aktuell`, `Demn?chst` und `Abgeschlossen` ausgewertet.
- UI-Klassifizierung und schedulbare Favoriten-Releases besitzen getrennte DAO-Abfragen.
- Historische Releases bleiben von Alarmen, Notifications, AUTO-Watcher, WorkManager und T+10-Fallback ausgeschlossen.
- Ein neuer konkreter Zukunftstermin verschiebt einen historischen Favoriten automatisch von ?Abgeschlossen? nach ?Demn?chst?.
- Room- und Classifier-Regressionstests sichern beide Datenwege ab.

## v0.24.7 ? direkter Provider beendet Fallback ? 2026-08-12

- Direkter Crunchyroll-/ADN-Erfolg beendet AUTO-Pr?fung, WorkManager-Auftrag, Due-Alarm und T+10-AniWorld-Fallback terminal.
- Availability-Benachrichtigung wird unmittelbar aus dem direkten Providerergebnis erzeugt und semantisch dedupliziert.
- Fallback-Worker liest unmittelbar vor dem Netzwerkzugriff Room erneut und beendet sich bei bereits best?tigtem `AVAILABLE`.
- AniWorld-Fallback ist ausschlie?lich nach technischem `CHECK_FAILED` zul?ssig; ein erfolgreich ausgewertetes `NOT_AVAILABLE_YET` bleibt beim direkten Anbieter.
- GER_SUB und GER_DUB beenden nur den jeweils semantisch passenden Release-Lebenszyklus.
- Historische Importe bleiben vollst?ndig von Alarmen, Workern, Watchern und Benachrichtigungen ausgeschlossen.
- Neue Regressionstests f?r direkten Erfolg, T+10-Grenze, Race Guard, technischen Fehler und Sprachtrennung.

## v0.24.6 ? generischer Crunchyroll-Katalog und Monatshistorie ? 2026-08-12

- Nicht auswertbare React-HTML-Shell durch einen anonymen, strukturierten Crunchyroll-Katalogclient ersetzt.
- Kein Benutzerkonto, keine Cookies sowie keinerlei Playback-, Manifest-, DRM- oder Downloadzugriff.
- Watch-URL, vorhandene Series-ID oder exakte Titelsuche l?sen generisch die stabile Crunchyroll-Series-ID auf.
- Series ? Seasons ? Episodes wird f?r alle Crunchyroll-Kandidaten ausgewertet; das fr?here Zw?lferlimit entf?llt.
- GER_SUB und GER_DUB werden ausschlie?lich aus `subtitle_locales` beziehungsweise `audio_locale` erzeugt.
- Historie verwendet nur echte Providerzeitpunkte; Sentinelwerte aus 9998/9999 werden verworfen.
- Monatsimport persistiert Crunchyroll-Historie nur innerhalb des ausgew?hlten Monats.
- Reale Fremdtitelregression: BLACK TORCH ohne vorgegebenen Serienlink selbst auf `GT00377907` aufgel?st.
- 207 JVM-Tests sowie 36 Ger?tetests erfolgreich.

## DIAGNOSETEST V24.3 ? Historie und korrigierte Statuslogik ? 2026-08-11

- `CHECK_FAILED` als neutraler technischer Fehler ohne laufenden oder eingefrorenen Versp?tungsz?hler umgesetzt.
- Versp?tungsz?hler ausschlie?lich f?r `NOT_AVAILABLE_YET`; bei `AVAILABLE` nur mit realem `firstAvailableAt` eingefroren.
- ?ffentlichen Crunchyroll-Historienparser f?r konkrete Staffel, Episode, Datum, Sprache, Watch-ID und echte URL erg?nzt.
- Historische Providertermine in Room 19 dauerhaft gekennzeichnet und deduplizierend importiert beziehungsweise angereichert.
- Historische Termine von Due-Alarmen, Notifications, AUTO-Pr?fung, WorkManager und AniWorld-T+10-Fallback ausgeschlossen.
- Kalender zeigt historische Termine ohne erfundene Uhrzeit und mit klarer Kennzeichnung.
- Regression f?r ?A Livid Lady?? auf dem Ger?t gepr?ft: `CHECK_FAILED` best?tigt ausdr?cklich keine Verz?gerung.
- 198 Unit-Tests sowie 8 Room-Migrationstests auf dem verbundenen SM-S928B erfolgreich.

## DIAGNOSETEST V24 AUTO ? verbindliche Availability-Strategie ? 2026-08-11

- Generischen versuchsabh?ngigen Provider-Backoff vollst?ndig entfernt.
- AUTO driftfrei ab `expectedAt` umgesetzt: 0?5 Min 30 s, 5?10 Min 1 Min, 10?60 Min 5 Min, 1?4 Std 30 Min, danach 1 Std.
- Kurzfristige AUTO-Ticks ?ber einzelne Exact-Alarme; WorkManager f?hrt nur den jeweiligen Netzwerkcheck aus.
- Watcher strikt auf aktive Favoriten begrenzt.
- Automatischer Abbruch bei `AVAILABLE`, `POSTPONED`, `DELAYED` oder `DELAYED_CONFIRMED`.
- Verschiebungen vor oder w?hrend der Pr?fung annullieren alte Availability-Alarme.
- Vorhandener Verschiebungsgrund und neuer Termin werden ohne Erfindungen in die Benachrichtigung ?bernommen.
- Manuelle Profile 30 s, 1, 2, 5, 10, 15, 30 und 60 Minuten erg?nzt.
- Due-Alarm, T+10-AniWorld-Fallback, Provider-Marktsemantik und V23-Matchingregressionen beibehalten.

## DIAGNOSETEST V24 ? expliziter deutscher Anbietermarkt ? 2026-08-11

- `providerMarket` in Room-Schema 17 erg?nzt und JustWatch-Diagnosedaten verbindlich mit `DE` gespeichert.
- Bestehende JustWatch-Anbieterreferenzen bei der Migration auf den deutschen Markt zur?ckgef?hrt.
- Physische DVD-, Blu-ray-, Buch- und Shopangebote sowohl bei der Migration als auch vor jedem Provider-Sync bereinigt.
- Detailansicht um Markt, JustWatch-Quelle und Zeitpunkt der Anbieterpr?fung erg?nzt.
- V23-Jahressemantik, Stable-ID-Matching, Dara-san-/One-Piece-Regressionsschutz und due+upcoming-Sync beibehalten.
- Slime, MAO und Bumpkin nach realer Migration weiterhin mit ihren gelieferten deutschen Referenzanbietern vorhanden.
- 181 Unit-Tests und 6 Room-Migrationstests direkt auf dem verbundenen Android-Ger?t erfolgreich.
- N?chster realer Favoritenrelease Clevatess S2E6 (GER SUB und GER DUB) f?r 12.08.2026, 14:00 Uhr samt +10-Minuten-Fallback vorbereitet; die tats?chliche Ausl?sung lag au?erhalb des kurzen V24-Pr?flaufs.

## DIAGNOSETEST V23 ? konservative Provideranreicherung ? 2026-08-11

- Serienstartjahr von Staffel- und Releasejahr semantisch getrennt.
- Nur sicher gespeicherte JustWatch-Serienjahre als harte Jahresbarriere beibehalten.
- Bereits gespeicherte stabile JustWatch-ID bei sp?teren Staffeln bevorzugt wiederverwendet.
- Lokalisierte, englische und Romaji-Titel ?ber eine ?bereinstimmende stabile JustWatch-ID konservativ verbunden.
- Jahreszus?tze wie `MAO (2026)` nur bei passendem Kandidatenjahr auf die Basisform reduziert.
- `One Piece` und `One Piece (2023)` bleiben auch ohne harte Jahresvorgabe getrennt.
- NoMatch-/Ambiguous-Diagnose um Jahrherkunft, Kandidaten, Scores und Ablehnungsgr?nde erweitert.
- Physische Angebote bei neuer Persistenz ausgeschlossen und alte physische Providerreferenzen beim Sync bereinigt.
- Slime, MAO und Bumpkin im realen JustWatch-Diagnoselauf eindeutig identifiziert.
- 180 Tests erfolgreich; V22-Newsrobustheit und due+upcoming-Sync unver?ndert erhalten.

## DIAGNOSETEST V22 ? robuster News-Detailzustand ? 2026-08-11

- Explizite Zust?nde `Loading`, `Found` und `NotFound` f?r die interne Newsdetailseite eingef?hrt.
- Ung?ltige oder nicht mehr vorhandene Meldungs-IDs enden nicht mehr in einem dauerhaften Ladeindikator.
- Verst?ndliche Meldung ?Meldung nicht mehr verf?gbar? und R?ckkehrschaltfl?che zur News-Liste erg?nzt.
- Regul?re Room-Offlinedetails und getrennte HTTPS-Quellenbuttons aus V21 unver?ndert beibehalten.
- Zwei gezielte Regressionstests erg?nzt; gesamter Stand: 171 erfolgreiche Tests.

## DIAGNOSETEST V21 ? interne Newsdetailseite ? 2026-08-09

- News-Karten von direkter Browsernavigation auf `news/{announcementId}` umgestellt.
- Offline lesbare Detailansicht direkt aus Room erg?nzt.
- Kategorie, Titel, Zusammenfassung, Bild, Zeit, Staffel, Termine, Releasefenster, Grund, Provider und Quellen werden nur bei real vorhandenen Werten angezeigt.
- Anime2You-Originalartikel und zus?tzliche Nachweise als getrennte HTTPS-Quellenbuttons umgesetzt.
- Unsichere oder fehlende URLs erzeugen keine Browseraktion.
- Interne Zur?cknavigation f?hrt zur News-Liste; Browser-Zur?ck f?hrt zur?ck zur Detailseite.
- V20-Favoriten-, RSS-, Dedup-, Dara-san-, One-Piece- und Provider-Sync-Regressionspfade beibehalten.

## DIAGNOSETEST V20 ? Releasezustand und echte News ? 2026-08-09

- ?Abgeschlossen? von Anime-Endstatus auf ?letzter Release vorbei, kein n?chster konkreter Termin? umgestellt.
- Staffelank?ndigungen ohne Datum strikt von Kalender und Favoriten-Releasestatus getrennt.
- ?News & Meldungen? im Drawer aktiviert.
- Offiziellen Anime2You-Artikel-RSS-Feed mit Gr??enlimit, User-Agent, Cache, Retry und Backoff angebunden.
- Meldungen in Room-Schema 16 strukturiert gespeichert; Quelle und Ver?ffentlichungszeit bleiben sichtbar.
- Konservative Kategorien f?r neue Anime, Staffeln, Termine, Verschiebungen, Streaming, Dub, Produktion und Fortsetzungen erg?nzt.
- AniWorld-Termin?nderungen als zweite redaktionelle Best?tigung eingebunden und nach Sachverhalt/Staffel/Zeitn?he dedupliziert.
- Dara-san-, One-Piece-, due+upcoming-, Exact-Alarm-, Fallback- und Deep-Link-Regressionspfade beibehalten.

## DIAGNOSETEST V19 ? konservative Provideranreicherung ? 2026-08-09

- Seasonbasis mit 78 realen AniWorld-Titeln unver?ndert beibehalten.
- Sicher gematchte Providerreferenzen zus?tzlich zur optionalen Kataloganreicherung verwendet.
- Generische Titel?quivalenz f?r sprachliche Varianten wie ?of Reiwa?/?of the Reiwa Era? erg?nzt.
- Jahr und Format bleiben harte Kompatibilit?tssignale; unsicheres Fuzzy-Matching bleibt ausgeschlossen.
- `One Piece` und `One Piece (2023)` bleiben f?r Anreicherung getrennt.
- Randf?lle der Seasonheuristik einschlie?lich R?ckkehr nach Datenl?cke getestet.
- V18 im sichtbaren App-Changelog erg?nzt.

## DIAGNOSETEST V18 ? eindeutiger Due-Pfad und reale Seasonbasis ? 2026-08-09

- Parallelen WorkManager-Due-Timer entfernt; Exact Alarm ist der einzige zeitkritische Due-Ausl?ser.
- WorkManager auf Provider-Retry, Recovery und Synchronisation begrenzt.
- ?Aktuelle Season? direkt aus laufenden AniWorld-Releasezyklen aufgebaut.
- Laufende AniWorld-Titel bleiben auch ohne JustWatch-Match sichtbar; JustWatch reichert nur an.
- Zukunfts-only-, beendete und reine Katalogtitel aus der aktuellen Season ausgeschlossen.
- App-Changelog um V16 und V17 erg?nzt.
- Providerfilter, Deep-Link, T+10-Fallback, Zeitfelder sowie letzter/n?chster Release unver?ndert beibehalten.

## DIAGNOSETEST V17 ? autonomer Exact-Alarm-Watcher ? 2026-08-09

- Aktualisierten 17-Uhr-Realtest ausgewertet und falsche Fallback-Anbieterauswahl behoben.
- Zentrale Streaming-Provider-Policy nun auch in der Persistenz verwendet; DVD, Blu-ray und H?ndler k?nnen keinen AVAILABLE-Anbieter mehr liefern.
- Crunchyroll-Parser um ?ffentliche snake_case-Felder (`episode_number`, `season_number`, `subtitle_locales`, `audio_locale`) erweitert.
- Strukturierte Live-Diagnose f?r HTTP-Status, Antworttyp, Antwortgr??e und Parserstufe erg?nzt.
- Due-Zustellung direkt im Exact-Alarm-Wakeup statt erst nach WorkManager-Start ausgef?hrt.
- Zweiten generischen Wake-up bei `expectedAt + 10 Minuten` f?r den AniWorld-Fallback erg?nzt.
- WorkManager als begrenztes Retry- und Recovery-System beibehalten.
- `sourceAvailableAt` getrennt von `firstAvailableAt` und `lastCheckedAt` in Room-Schema 15 gespeichert.
- Due- und Available-Notifications mit Deep-Link auf die konkrete Staffel, Episode und Sprache versehen.
- Detailansicht zeigt letzten und n?chsten Release gleichzeitig samt Providerstatus und Zeitstempeln.
- Reale V16-Historie dokumentiert: Due 15:02:14, AniWorld-Erkennung 15:13:17, Crunchyroll `PARSER_CHANGED`.

## DIAGNOSETEST V16 ? Hintergrundabnahme und Statusklarheit ? 2026-08-09

- JustWatch bleibt ausschlie?lich Anbieter- und Metadatenquelle; Episodenentscheidungen verwenden nur direkten Providercheck oder den AniWorld-Fallback.
- Leere Anbietertexte in Verf?gbarkeitsmeldungen verhindert und Sprachfassung aus konkreten Episodenflags abgeleitet.
- Kalender um letzte Pr?fung, Anbieter, ersten Erkennungszeitpunkt, Fehler- und Fallbackstatus erg?nzt.
- Tagesbegr??ung dynamisch an die lokale Ger?tezeit gebunden.
- Aktuelle Season auf reales AniWorld-Fenster vor und nach dem aktuellen Zeitpunkt erweitert und Trefferzahl sichtbar gemacht.
- Verschobene Releases in der Statistik als anklickbare Detailansicht umgesetzt.
- Version `0.16.0-v16-diagnostic` f?r den realen gesperrten 15-Uhr-Hintergrundtest vorbereitet.

## DIAGNOSETEST V15 ? Releasekette und produktive Drawer-Bereiche ? 2026-08-09

- Fachlich stabilen Notification-Key aus Anime, Staffel, Episode, Sprache und Ereignis eingef?hrt.
- Atomaren Room-Claim gegen parallelen AlarmManager-/WorkManager-Doppelversand erg?nzt.
- Legacy-Deduplizierung f?r V14-Deliveries und den realen fehlerhaften `aniworld:episode-6`-Datensatz erg?nzt.
- AniWorld-Kalenderidentit?t auf den tats?chlichen Serienlink beschr?nkt.
- JustWatch verbindlich auf Anbieterermittlung beschr?nkt; keine Episoden-Negativaussage mehr.
- Direkten Streaminganbietercheck vor den AniWorld-Fallback gesetzt.
- AniWorld-Fallback auf konkrete Episodenzeilen der Serien?bersicht und lokale Sprachflaggen umgestellt.
- `firstAvailableAt` als ?Erstmals erkannt? samt geplantem Termin und erkannter Verz?gerung sichtbar gemacht.
- Anbieter, aktuelle Season, neue Dub-Releases, Release-Statistik und Changelog als reale Drawer-Routen aktiviert.
- News bleibt mangels realer Quelle deaktiviert; ?Hei? erwartet? bleibt bis zur eindeutigen Popularit?tssemantik deaktiviert.

## DIAGNOSETEST V10 ? Favoriten?berwachung ? 2026-08-04

- Kalenderkarten ?ber die echte Room-Anime-ID zur Detailseite navigierbar gemacht.
- Eindeutige One-Time-WorkRequests je favorisiertem Release erg?nzt.
- Neuplanung bei Favoriten-, Sprach-, Kalender- und Verschiebungs?nderungen sowie App-/Ger?teneustart.
- Persistente Tabellen f?r geplante Releasejobs und deduplizierte Benachrichtigungszustellungen erg?nzt.
- Allgemeine JustWatch-Titelzuordnung und konkrete Staffel-/Episodenpr?fung fachlich getrennt.
- Konkrete DE-Angebote sowie Audio-/Untertitelsprachen bestimmen GER SUB und GER DUB getrennt.
- Netzwerkfehler bleiben Fehlerzust?nde und werden niemals als Nichtverf?gbarkeit gespeichert.
- Statusfolge `SCHEDULED ? DUE ? CHECKING ? PENDING_CONFIRMATION/DELAYED/AVAILABLE` verdrahtet.
- Release-Due-, Verf?gbarkeits-, Versp?tungs- und Verschiebungsbenachrichtigungen mit Duplikatschutz erg?nzt.
- Lokalen Fake-/Diagnoseimport und Demo-Benachrichtigungsweg aus der installierbaren UI entfernt.
- Reale Job-/Pr?f-/Versanddiagnose in Detailseite und Einstellungen erg?nzt.
- Room-Schema 12 samt Migration 11?12 und Regressionstests erg?nzt.
- 125 lokale Tests sowie Debug- und Instrumentation-APK erfolgreich gebaut.
- Echte Ger?te-/Terminabnahme noch offen, da das zuvor verbundene Ger?t w?hrend der Abnahme getrennt wurde.

## DIAGNOSETEST V9 ? Kalenderkarten und JustWatch-Abdeckung ? 2026-08-03

- Kalendertermine verwenden dieselbe responsive Titelkarte wie die Startseite.
- Jede Tageskarte zeigt Cover, Titel, Folge, GER-SUB/GER-DUB, lokale Uhrzeit und sekundengenauen Countdown.
- GER-SUB und GER-DUB bleiben auch am selben Tag getrennte Termine.
- Technische Quellen-, Worker- und Diagnoseangaben aus der normalen Kalenderansicht entfernt.
- Anbieter werden titel-/staffelbezogen aus realen JustWatch-DE-Angeboten ?bernommen.
- Sichere Titelzuordnung um Akzent-, Satzzeichen- und Staffelzusatz-Normalisierung erweitert.
- Mehrdeutige Treffer werden nur aufgel?st, wenn genau ein Kandidat deutsche Angebote besitzt.
- Live-Ger?tetest: 69 von 79 Titeln eindeutig zugeordnet; 3 mehrdeutig und 7 ohne sicheren Treffer.
- 121 Unit-Tests ohne Fehler; Debug-APK erfolgreich gebaut und auf einem Galaxy-Ger?t gepr?ft.

## Diagnose-Testbuild V8 ? episodengenauer Provider-Diagnosepfad ? 2026-08-03

- AniWorld bleibt prim?re Quelle f?r deutsche GER-SUB-/GER-DUB-Termine und Verschiebungen.
- AniWorld-Kalendercover werden aus den ?ffentlichen Karten ?bernommen, validiert und im Anime-Cache gespeichert.
- Jeder zuk?nftige Kalendereintrag zeigt einen sekundengenauen, lifecycle-sicheren Countdown aus der festen Zielzeit.
- AnimeRadar, AniList und AniSearch sind im aktiven Pfad deaktiviert und bleiben nur als inaktive Fallback-Bausteine im Quellcode.
- Neutrale JustWatch-Partner-Schnittstelle erg?nzt; ohne freigegebenen Zugang lautet der Produktstatus verbindlich `SOURCE_NOT_CONFIGURED`.
- Lokalen Provider-Diagnosedatensatz ausschlie?lich im Debug-Build aktiviert und in der UI un?bersehbar gekennzeichnet.
- Room-Schema v10 um Titelmatches, Angebote und episodengenaue Providerverf?gbarkeit erweitert.
- Allgemeine Anbieterlistungen und best?tigte Episodenverf?gbarkeit strikt getrennt.
- GER SUB, GER DUB und beide Sprachfassungen als getrennte Zust?nde gespeichert.
- Fehler werden als `CHECK_FAILED` behandelt und niemals als ?nicht verf?gbar? interpretiert.
- Ansteigendes Pr?fintervall 0/5/10/20/30/60 Minuten erg?nzt.
- Getrennte WorkManager-Jobs `anisentinel.justwatch-provider-sync` und `anisentinel.provider-availability-sync` aktiviert.
- Diagnoseanzeige f?r eindeutige/mehrdeutige Zuordnungen und Episodenpr?fungen erg?nzt.

## Diagnose-Testbuild V7 ? deutscher AniWorld-Kalender ? 2026-08-03

- ?ffentlichen AniWorld-Animekalender und die Verschiebungsseite kontrolliert angebunden.
- Ausschlie?lich GER SUB (`japanese-german.svg`) und GER DUB (`german.svg`) ?bernommen.
- Englische und andere Sprachfassungen vollst?ndig verworfen.
- GER SUB und GER DUB anhand von Sprachfassung, Staffel, Episode und Zeit getrennt gespeichert.
- AniWorld-Originalzeit und korrigierte Zeit mit verbindlichen ?10 Minuten gespeichert.
- 23:59 wird ebenfalls korrigiert und zus?tzlich als m?glicher Tagesendmarker protokolliert.
- Doppelte gleichsprachige HTML-Zeilen vor Room dedupliziert.
- Room-Schema v9 um Quellenreferenzen, Originalzeit, Sprachfassung und ?nderungshistorie erweitert.
- Verschiebungen nur bei eindeutigem Titel-, Staffel-, Episoden- und Sprachmatch angewendet.
- Historisierte Releases vor sp?terem Bereichsersatz und CASCADE-Verlust gesch?tzt.
- Getrennte WorkManager-Jobs f?r AniWorld-Kalender und AniWorld-Verschiebungen erg?nzt.
- Startseite quellenneutral in ?Anime-Katalog? umbenannt.
- Internationalen AnimeRadar-Zugang und zugrunde liegende AniList-Termindaten transparent getrennt.
- Reale Ger?tepr?fung: 128 internationale Termine, 195 deutsche Termine, eine eindeutige Sub-Verschiebung.


## Diagnose-Testbuild V6 ? AnimeRadar End-to-End ? 2026-08-03

- AnimeRadar-Kalenderadapter in Debug/DIAGNOSETEST standardm??ig aktiviert.
- ?ffentlichen POST-Endpunkt `/api/anilist` mit echter Pagination und strikter Validierung angebunden.
- AnimeRadar als prim?re Releasequelle, AniList ausschlie?lich als Ausfall-Fallback koordiniert.
- Keine erfundene deutsche oder Provider-Verf?gbarkeit aus AniList-Terminen abgeleitet.
- Vollst?ndige Ergebnisse quellenbezogen und transaktional in Room ersetzt; Fehler erhalten den Cache.
- Automatischen Start- und Hintergrundabruf auf die aktuelle Woche begrenzt.
- Diagnoseanzeige um Quelle, Empfangs-/Speicherzahl, Abrufzeit und WorkManager-Status erg?nzt.
- Debug-Hintergrundjob unabh?ngig von der produktiven Live-Quellen-Einstellung aktiviert.
- Echte AnimeRadar-Fixture und Parser-, Fallback- und Room-Regressionstests erg?nzt.
- 105 Unit-Tests erfolgreich; Galaxy-S24-Ultra-Lauf mit 128/128 Terminen best?tigt.

> AniList-403-Diagnose: gemeinsamer GraphQL-Client liest jetzt Erfolgs- und Fehlerbody,
> Retry-After, Rate-Limit-Header und Request-ID. 403 wird als Dienstabschaltung, IP-Sperre,
> Queryfehler oder Zugriffssperre klassifiziert. Kalender- und Katalogpfad teilen zentralen
> Cooldown; kalenderorientierte Metadaten speisen Room, Start und Entdecken gemeinsam.

> Leere-App-Regression: Kalender zeigt jetzt Lade-, HTTP-/GraphQL-/Netzwerkfehler,
> Retry-After, Wiederholen und letzten erfolgreichen Stand. Katalogfehler enthalten die
> konkrete Ursache. Fehlgeschlagene Kalenderabrufe lassen vorhandene Room-Eintr?ge intakt;
> ein Regressionstest sichert dies ab.

> Quellenwahrheit nachgesch?rft: AniList-Ausstrahlung, AniSearch-deutscher Release und
> best?tigte Provider-Verf?gbarkeit sind getrennte Domainzust?nde. Englische AniList-Titel
> werden nicht mehr als `titleGerman` gespeichert. Kalenderkarten zeigen Releaseart,
> Quellenlink und den expliziten deutschen Best?tigungsstatus.

> Automatischer Kalender: AniList-`airingSchedule` l?dt beim Start vier Wochen r?ckwirkend
> und acht Wochen zuk?nftig sowie beim Monatswechsel den gew?hlten Monat. Termine werden
> dauerhaft in `episode_releases` gespeichert und ehrlich als AniList-Ausstrahlungsdaten
> gekennzeichnet. Die systematische AniSearch-Quellenpr?fung steht in Dokument 15.

> Zusatz zum Korrekturstand 2026-08-02: Titelsuche statt URL-Zwang, URL-Import nur
> als Diagnosefunktion, differenzierter Importstatus, vorbereitete transaktionale
> Kalenderpersistenz und explizite Source-Discovery-Schnittstelle. Ein unbest?tigter
> Monatsparser bleibt bis zu echten gespeicherten Fixtures sicher deaktiviert.

## [Unreleased] ? 2026-08-02

- bereitgestelltes AniSentinel-Motiv als tats?chliches Raster-Launcher-Icon eingebunden;
  Schriftzug und Slogan werden f?r kleine Launcher-Masken ausgelassen
- AniSearch-Open-Source-Vergleich dokumentiert, ohne fremden Implementierungscode zu kopieren
- JSON-LD-first-AniSearch-Parser mit pr?zisen deutschen DOM-Fallbacks
- AniSearch-Suchresultate werden ?ber die stabile ID normalisiert und dedupliziert
- kontrollierter AniSearch-HTTP-Transport mit 30-Minuten-Cache, vier Sekunden Mindestabstand,
  User-Agent, Kill-Switch, begrenztem Retry/Backoff und differenzierten HTTP-Zust?nden
- Kalender liest Tages- und Monatsdaten ausschlie?lich aus `episode_releases` statt
  `AnimeEntity.nextAiringAt`
- mehrere historische und zuk?nftige Episoden desselben Anime bleiben separat darstellbar
- sichtbarer AniSearch-URL-Import im Kalender mit Lade-, Erfolgs-, Blockiert- und Fehlerzustand
- Kalendereintr?ge zeigen deutsche Titel, Episodennummer, Quelle, Anbieter und direkten Quellenlink
- ein leerer Room-Zeitraum wird ehrlich als ?noch nicht vollst?ndig geladen? ausgewiesen
- vollst?ndige Android-Branding-Ressourcen: Adaptive, Round, Monochrom, Legacy,
  Android-12-Splash, Drawer-Emblem und eigenes Notification-Symbol
- DOM-basierter manueller AniSearch-HTML-Import f?r ?ffentliche Detailseiten
- strikte HTTPS-/Host-/Detail-URL-Validierung und ehrliche Parserfehler
- deutsche Titel/Beschreibungen bleiben prim?r und AniList-Felder werden nur erg?nzend erhalten
- erkannte Anbieterlinks werden normalisiert und automatisch als `ProviderReference` gespeichert
- konkrete Episoden- oder Serien-URL hat im Crunchyroll-Checker Vorrang vor dem Kalender
- Evidence-Typen `EPISODE_PAGE`, `SERIES_PAGE`, `RELEASE_CALENDAR`, `SEARCH_RESULT`
- Room-Schema v5 mit `episode_releases`, Evidence-Type und atomarem Marker gegen doppelte
  Verf?gbarkeitsbenachrichtigungen
- 67 lokale Unit-/Robolectric-Tests erfolgreich

### Offen

- Der Live-Abruf von AniSearch war technisch blockiert; daher noch kein echter HTML-Fixture
  und kein praktisch belegter Realimport. Der manuelle Importpfad ben?tigt noch eine UI.
- Kalender-UI liest noch nicht prim?r aus `episode_releases`.
- WorkManager, Backoff-Ausf?hrung und echte einmalige Release-Benachrichtigung fehlen noch.
- Die Branding-Ressourcen bauen erfolgreich; Installer-/Launcher-/Splash-Screenshots erfordern
  Installation auf Emulator oder Ger?t.

## [0.10.0] ? 2026-08-01

- strukturierter `ProviderChecker` und differenzierte Pr?fergebnisse
- erster defensiver Checker f?r den ?ffentlichen deutschen Crunchyroll-Simulcast-Kalender
- konkrete Episodennummer wird gepr?ft; Serientreffer allein best?tigt keine Verf?gbarkeit
- Netzwerk- und Loginfehler werden nicht als ?Titel nicht gefunden? ausgegeben
- Room-Schema v4 mit Anbieterzuordnungen, Pr?fergebnissen und `firstAvailableAt`
- Detailseite beobachtet gespeicherte Anbieterzust?nde, Pr?fzeitpunkt und Quellenlink
- manueller Pr?fpfad ist nur f?r eine echte gespeicherte Crunchyroll-Zuordnung aktiv
- vier Fixture-Tests f?r verf?gbar, Episode fehlt, Loginwand und Netzwerkfehler

### Noch blockiert

- AniSearch-Animezugriff ben?tigt weiterhin eine projektspezifische API-Vereinbarung.
- Ohne ?berpr?fbare Anbieterzuordnung wird kein Checker ausgef?hrt.
- WorkManager und produktive Verf?gbarkeitsbenachrichtigungen sind noch nicht aktiviert.

## [0.9.1] ? 2026-07-30

- zentraler `ReleaseStatusResolver`: vergangene Termine werden als `RELEASE_TIME_REACHED`
  statt dauerhaft als `SCHEDULED` dargestellt
- `AVAILABLE` entsteht nur aus einer tats?chlich best?tigten Anbieterpr?fung
- getrennte Domainmodelle f?r Metadatenquelle und Streaming-Verf?gbarkeit
- separate Felder f?r Gesamtfolgenzahl und n?chste Episodennummer
- direkte, ?berpr?fbare AniList-URL auf der Detailseite
- Anbieterbereich zeigt bis zu einer echten Pr?fung ausdr?cklich ?noch nicht gepr?ft?
- interaktiver Kalender mit lokaler Zeitzone, Datumsauswahl, Room-Zeitfensterabfrage,
  Monatsnavigation und Markierungen f?r gespeicherte Termine
- automatische Aktualisierung wird bei ?berschrittenen Cacheterminen bevorzugt
- dynamische Beschriftungen f?r heute, morgen und n?chste Kalenderwoche
- Regressionstests f?r vergangene/zuk?nftige Termine, Verf?gbarkeit, Jahreswechsel und
  Room-Zeitfenster

## [0.9.0] ? 2026-07-30

- Produktive Routen verwenden keine Fake-Anime mehr.
- Home und Entdecken lesen ausschlie?lich den echten AniList-Room-Cache.
- Kalender zeigt bis zu best?tigten echten Terminen einen ehrlichen Leerzustand.
- Detailseiten laden ausschlie?lich persistierte echte Datens?tze.
- Favoriten l?sen ihre Anime direkt aus Room statt aus dem Fake-Repository auf.
- Der Datenquellen-Schalter und der produktive Fake-Hinweis wurden entfernt.
- AniSearch wurde ?ber den offiziellen API-Link gepr?ft: Anime-Metadaten erfordern
  derzeit eine projektspezifische Schnittstellenvereinbarung. Ohne Zugangsdaten wird
  weder Scraping noch eine scheinbar funktionierende AniSearch-Suche ausgeliefert.

## [0.8.2] ? 2026-07-30

- About-Seite beschreibt Demo- und AniList-Live-Modus korrekt.
- AniSearch wird ausdr?cklich als noch nicht angebunden gekennzeichnet.
- Die Benutzer-APK wird als `INSTALLIEREN.apk` ausgeliefert.
- Die Android-Test-APK liegt ausschlie?lich im Unterordner `tests`.
- Eine Installationsanleitung verhindert die Verwechslung beider APK-Typen.
- Ein AniSearch-Scraper bleibt deaktiviert, bis Nutzungsbedingungen und Crawler-Regeln
  verl?sslich gepr?ft und dokumentiert werden k?nnen.

## [0.8.1] ? 2026-07-30

### Korrigiert

- unbekannte AniList-Ausstrahlungstermine bleiben nullable und werden nicht aus `updatedAt` erfunden
- getrennte `sourceUpdatedAt`-/`cachedAt`-Felder und Room-Schema v3 mit Migration `2 ? 3`
- Trending-Ergebnisse werden transaktional als geordneter Snapshot ersetzt, ohne Favoriten-Anime zu l?schen
- Live-Details zeigen echte Metadaten, aber keine erfundenen Provider-, Studio-, Genre- oder Episodenverf?gbarkeiten
- unbekannte Detail-IDs zeigen Lade-/Fehlerzust?nde statt eines kurz sichtbaren Fake-Titels
- Live- und Demo-Inhalte sind auf der Startseite getrennt

### Cache und Tests

- 30-Minuten-TTL f?r automatische Aktualisierung und Beachtung von HTTP `Retry-After`
- Cover-Loader mit HTTP-Pr?fung, Zeitlimits, Speicher-/Diskcache und Downsampling
- Snapshot-/Favoriten-Regressionstest und instrumentierter Migrationstest `2 ? 3`

### Bekannte Grenze

- Ein erfolgreicher TLS-End-to-End-Abruf von AniList konnte in der isolierten Emulatorumgebung nicht best?tigt werden.

Alle relevanten ?nderungen an AniSentinel werden in dieser Datei dokumentiert.

## [0.8.0] ? 2026-07-30

### Hinzugef?gt

- erste echte ?ffentliche AniList-GraphQL-Metadatenquelle
- typisierte Erfolgs-, HTTP-, Netzwerk- und ung?ltige Antwortzust?nde
- nullable-sichere DTOs und Mapper f?r Titel, Beschreibungen, Cover und n?chste Ausstrahlung
- Room-Cache mit Schema v2 und Migration `1 ? 2`
- Debug-Umschaltung zwischen stabilen Fake-Daten und AniList-Live-Metadaten
- cache-first Startseite mit Lade-, Refresh-, Offline- und Fehlerzust?nden
- manuelles Aktualisieren sowie reale Cover mit abstraktem Fallback

### Sicherheit und Grenzen

- AniList wird ausschlie?lich f?r ?ffentliche Metadaten genutzt
- keine AniSearch- oder Anbieterabfrage
- keine Streaming-, Download-, Playback- oder DRM-Funktion
- Zeitlimits, klarer User-Agent und maximal 12 Titel pro manueller Anfrage

### Tests

- 55 lokale Unit-/Robolectric-Tests erfolgreich
- 22 instrumentierte Tests auf Android 15/API 35 erfolgreich: `OK (22 tests)`
- Parser-, Nullfeld-, Fallback-, Mapper- und Live-/Fehlerzustandstests erg?nzt
- acht bestehende Golden-Vergleiche weiterhin erfolgreich

## [0.7.8] ? 2026-07-30

### Behoben

- nach erstmaligem Erteilen der Android-13-Benachrichtigungsberechtigung wird die angeforderte Demo sofort gesendet
- Ablehnung erzeugt keine Notification und zeigt einen verst?ndlichen Wiederholungs-/Einstellungsweg
- fehlende Golden-Assets lassen den zugeh?rigen Regressionstest nun verbindlich fehlschlagen

### Hinzugef?gt

- getrennte Karten f?r die gespeicherte Benachrichtigungspr?ferenz und die lokale Testaktion
- zentraler `NotificationCoordinator` f?r Sprache, Pr?ferenzen, Domain-Engine und Android-Ausgabe
- vollst?ndiger About-Screen als Golden sowie 150-%-Hero-Golden
- echter instrumentierter Erstberechtigungs- und Ablehnungstest

### Ge?ndert

- Golden-Dateien hei?en entsprechend ihrem tats?chlichen Hero-/Breitformat-Inhalt
- Kanalnamen werden nach einem Sprachwechsel erneut lokalisiert
- About-Large-Font-Test scrollt bis zum letzten Abschnitt

### Tests

- 50 lokale Unit-/Robolectric-Tests erfolgreich
- 21 instrumentierte Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich: `OK (21 tests)`
- acht verbindliche Golden-Vergleiche erfolgreich; keine Diff-Datei erzeugt

## [0.7.7] ? 2026-07-30

### Hinzugef?gt

- vier lokale Android-Benachrichtigungskan?le f?r Erinnerungen, Releases, Versp?tungen und Systemmeldungen
- Android-13-Berechtigungsfluss, lokalisierte Demo-Benachrichtigung und App-?ffnender `PendingIntent`
- sechs eingecheckte Golden-Screenshots f?r Smartphone/Tablet, Hell/Dunkel und Deutsch/Englisch
- echte Pixelvergleiche mit 2-%-Toleranz und magentafarbenem Diff-Bild bei Abweichungen
- zwei instrumentierte Lesbarkeitstests mit realer Schriftvergr??erung auf 150 %

### Ge?ndert

- Versionsanzeige der About-Seite stammt zentral aus `BuildConfig.VERSION_NAME`
- UI-Tests setzen Sprache, Theme, Favorit und Benachrichtigungen vor/nach jedem Test zur?ck
- Benachrichtigungen bleiben vollst?ndig lokal und nutzen weiterhin nur Fake-Ereignisse

### Tests

- 50 lokale Unit-/Robolectric-Tests erfolgreich
- 17 instrumentierte Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich: `OK (17 tests)`
- alle sechs Golden-Varianten pixelweise gepr?ft; keine Diff-Datei erzeugt

## [0.7.6] ? 2026-07-30

### Hinzugef?gt

- echte, deutsch/englisch lokalisierte ??ber AniSentinel?-Unterseite
- Angaben zu Version, Build, Entwicklungsstatus, Datenschutz, Fake-Quellen und Technik
- instrumentierter Sprachtest ?ber Einstellungen, Entdecken, Kalender und Activity-Recreation
- instrumentierter Favoriten-Persistenztest ?ber Recreation und Navigation
- reproduzierbare PNG-Ausgabe der Screenshot-Smoke-Tests

### Ge?ndert

- About-Ziele in Einstellungen und Drawer navigieren auf die echte Unterseite
- Notification Engine wird als Flow anhand der gespeicherten Sprache erzeugt
- WatchProfile-Domain enth?lt nur noch neutrale IDs und keine deutschen Anzeigenamen
- transparenter Hero-Container setzt seine Content-Farbe explizit theme-konform

### Tests

- 50 lokale Unit-/Robolectric-Tests erfolgreich
- zehn instrumentierte Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich:
  `OK (10 tests)`
- zwei gepr?fte Screenshot-Ausgaben f?r dunkles Smartphone- und helles Tabletlayout

## [0.7.5] ? 2026-07-30

### Behoben

- ?Alle anzeigen? erscheint nur noch bei vorhandener, echter Aktion
- Kalender verwendet lokalisierte Monats- und Wochentagsnamen bei weiterhin montagsbasierter Woche
- Genre-Chips und Coverbeschreibung sind vollst?ndig lokalisiert
- Watch-Profil verwendet denselben Status ?Aktiv? wie Theme und Sprache

### Ge?ndert

- Notification Engine erh?lt austauschbare deutsche und englische Textkopien
- Navigation und Home-Liste besitzen stabile Testtags f?r ger?tefeste UI-Tests
- UI-Tests scrollen explizit zu nicht sichtbaren Lazy-List-Inhalten

### Tests

- 49 lokale Unit-/Robolectric-Tests erfolgreich
- sieben instrumentierte UI-/Screenshot-Tests auf Pixel-6-AVD mit Android 15/API 35 erfolgreich
- Gradles UTP-Transport ist in der Sandbox gest?rt; die identischen APKs wurden deshalb
  direkt ?ber `AndroidJUnitRunner` ausgef?hrt: `OK (7 tests)`

## [0.7.4] ? 2026-07-30

### Behoben

- doppelte Eintr?ge und funktionslose Navigationspfeile aus den Einstellungen entfernt
- sichtbare deutsche Kotlin-Texte aus Settings, Detail- und Anime-Karten ausgelagert
- Watch-Profil-IDs durch lokalisierte, nutzerfreundliche Namen ersetzt
- initialer App-Inhalt wartet auf den ersten DataStore-Zustand und vermeidet Theme-Flashes

### Ge?ndert

- Einstellungen sind in App, ?berwachung und weitere Bereiche gegliedert
- Benachrichtigungs- und Anbieteroptionen sind bis zur Implementierung deaktiviert
- nicht navigierbare Bereiche zeigen ?Demn?chst? statt eines Pfeils
- Akzent- und Statusfarben verwenden verst?rkt semantische Theme-Rollen
- sieben instrumentierte UI-/Screenshot-Tests vorhanden, darunter ein Dubletten-Regressionsfall

### Tests

- 46 lokale Unit-/Robolectric-Tests erfolgreich
- sieben instrumentierte Tests erfolgreich kompiliert
- keine Ausf?hrung instrumentierter Tests m?glich, da lokal weder AVD noch System-Image installiert ist

## [0.7.3] ? 2026-07-30

### Hinzugef?gt

- vollst?ndiges helles Material-3-Farbschema
- englische String-Ressourcen und laufzeitf?higer Sprachwechsel
- Root-`AppViewModel` als globale, reaktive Einstellungsquelle

### Ge?ndert

- Theme-Wechsel zwischen Hell, Dunkel und System wirkt sofort in der gesamten App
- Hero-Verl?ufe verwenden Theme-Farbrollen statt fest codierter dunkler Farben
- noch nicht implementierte Drawer-Ziele sind sichtbar deaktiviert und als ?Demn?chst? markiert
- Benachrichtigungs- und Anbieteroptionen kommunizieren ihren Vorbereitungsstatus

### Tests

- 46 lokale Unit-/Robolectric-Tests erfolgreich
- Theme-Aufl?sung f?r Hell, Dunkel und System explizit getestet
- Screenshot-Smoke-Tests erfassen nun jeweils ein dunkles und ein helles Layout

## [0.7.2] ? 2026-07-30

### Kritisch behoben

- Anime-Metadaten verwenden Room-`@Upsert` statt SQLite-`REPLACE`
- Metadatenaktualisierungen l?schen dadurch keine abh?ngigen Favoriten mehr
- das ?ffnen einer Detailseite verwendet nur noch `INSERT IGNORE` und schreibt
  vorhandene Anime-Metadaten nicht erneut
- alle weiteren bisherigen `REPLACE`-Upserts wurden auf nicht destruktives `@Upsert` umgestellt

### Tests

- expliziter Cascade-Regressionstest: Favorit ?berlebt eine Anime-Metadatenaktualisierung
- 43 lokale Unit-/Robolectric-Tests erfolgreich

## [0.7.1] ? 2026-07-30

### Behoben

- Favoritenansicht liest ausschlie?lich aktivierte Favoriten aus Room
- Detailseite beobachtet den Favoriteneintrag des ge?ffneten Anime direkt
- Favoritenstatus besitzt einen Ladezustand und wird anschlie?end optimistisch aktualisiert
- erneutes Favorisieren erzeugt dank Prim?rschl?ssel keinen zweiten Datensatz
- Entfernen eines Favoriten aktualisiert Detail- und Favoritenansicht automatisch
- Sprach- und Watch-Profil?nderungen bewahren den urspr?nglichen `createdAt`-Wert
- individuelles Favoritenprofil und globales Standardprofil sind getrennt
- aktive Sprachfassung ist als ausgew?hlter Material-3-Filterchip sichtbar
- gef?lltes Herz kennzeichnet einen aktiven Favoriten
- Kalenderfilter ist bis zur Implementierung sichtbar deaktiviert

### Tests

- 42 lokale Unit-/Robolectric-Tests erfolgreich
- instrumentierte UI-/Screenshot-Tests kompilieren erfolgreich

## [0.7.0] ? 2026-07-30

### Hinzugef?gt

- deutlich sichtbarer Detailseiten-CTA in der Hero-Karte
- persistentes Favorisieren ?ber Room
- lokale OmU-/Dub-/Beide-Auswahl und Watch-Profilwahl auf der Detailseite
- aktive DataStore-Bedienung f?r Theme, Sprache, Benachrichtigungen, Watch-Profil und Provider
- WatchProfileSelector mit den Profilen Schnell, Ausgeglichen und Sparsam
- priorit?tsbasierte automatische Profilwahl mit Akku- und Live-Monitoring-Regeln
- explizite Dub- und Sub-Provider-Szenarien

### Ge?ndert

- ?Bald verf?gbar? beginnt nach dem bereits in der Hero-Karte hervorgehobenen Titel
- 39 lokale Tests erfolgreich

## [0.6.0] ? 2026-07-30

### Hinzugef?gt

- lokale Notification Engine f?r Reminder, verf?gbare und versp?tete Releases,
  Providerfehler und Wartungsmodus
- Fake-Provider-Szenarien f?r Verz?gerung, HTTP-Fehler, Wartung, Sprachen und Regionen
- Accessibility-Semantik, ?berschriften, Inhaltsbeschreibungen und stabile UI-Test-Tags
- Compose-UI-Tests f?r Navigation, Hero-Karte, Detailseite, Einstellungen und Drawer
- Screenshot-Smoke-Tests f?r Smartphone- und Tabletbreiten

### Tests

- 34 lokale Unit-/Robolectric-Tests erfolgreich
- sechs instrumentierte UI-/Screenshot-Tests kompilieren erfolgreich

## [0.5.0] ? 2026-07-30

### Hinzugef?gt

- Repository-Vertr?ge f?r Anime, Releases, Provider, News und Einstellungen
- Preferences DataStore f?r Theme, Sprache, Benachrichtigungen, Watch-Profil und Provider
- navigierbare Anime-Detailseite mit Episoden, Synopsis, Sprachen, Provider und Verlauf
- deterministischer Fake-Provider ohne Netzwerkzugriffe
- Watcher Engine mit Scheduler, ProviderCheck, StatusMachine und NotificationEvent
- Tests f?r Fake-Provider, Scheduler und Watcher Engine

### Ge?ndert

- Anime-Karten ?ffnen jetzt die lokale Detailseite
- Versionsstrategie auf semantische Versionierung umgestellt

## [0.4.1] ? 2026-07-30

### Behoben

- Statuschip in der Hero-Karte wird auf schmalen Ger?ten nicht mehr auf Zeichenbreite
  zusammengedr?ckt
- Watcher-Hinweis und Release-Status besitzen jetzt getrennte, horizontal lesbare Zeilen
- Statuschips sind grunds?tzlich auf eine Textzeile begrenzt

## [0.4.0] ? 2026-07-30

### Hinzugef?gt

- sekundengenauer, lifecycle-sicherer Release-Countdown
- Anzeige von Wochen, Tagen, Stunden, Minuten und Sekunden
- driftfreie Berechnung aus festem Release-`Instant` und aktueller Systemzeit
- automatischer ?bergang in die aktive Watcher-Phase am Nullpunkt
- Tests f?r Wochen-, Tages- und Stundengrenzen, Nullpunkt, vergangene Termine und Zeitzonen

## [0.3.0] ? 2026-07-30

### Hinzugef?gt

- Room mit KSP, Schemaexport und `AppContainer`
- lokales Favoriten-Repository
- Hero-Karte und Release-Countdown
- aufgewertete Statuskacheln
- animierter Sentinel-Avatar
- Room-/Robolectric-Tests

## [0.2.0] ? 2026-07-30

### Hinzugef?gt

- Jetpack Compose und Material 3
- Navigation f?r Start, Kalender, Favoriten, Entdecken und Einstellungen
- Hamburger-Drawer
- Dark-Designsystem und Fake-Daten

## [0.1.0] ? 2026-07-30

### Hinzugef?gt

- initiales Gradle-Projekt mit Package `de.anisentinel.app`
- Domainmodelle und Grundstruktur
# v0.10.0 ? Live-Cache-Korrekturstand (2026-08-02)

- Persistente, app-neustartfeste Cooldowns f?r AniList und AniSearch erg?nzt.
- Rate-Limit-, Dienstst?rungs- und IP-Sperrantworten respektieren den gespeicherten n?chsten Abrufzeitpunkt.
- Erfolgsgebundene Cachebereinigung auf vier Wochen Vergangenheit und acht Wochen Zukunft erg?nzt.
- Favoriten und best?tigte Anbieterhistorie werden von der Bereinigung gesch?tzt.
- Fehlerpfade behalten vorhandene Room-Daten unver?ndert bei.
- Keine fremden Kalender-Snapshots oder erfundenen Ersatzdaten eingebettet.
- 76 Unit-Tests sowie Debug- und Instrumentierungs-APK erfolgreich gebaut.
- WorkManager bleibt wegen lokaler Gradle/PKIX-Abh?ngigkeitsaufl?sung offen; Details im Validierungsbericht.
# v0.10.0 ? technische Diagnoseimport-Abgabe (2026-08-02)

- Lokalen JSON-Dateiimport ?ber Android `OpenDocument` erg?nzt.
- Import ausdr?cklich als Entwickler-/Diagnosefunktion gekennzeichnet.
- Schema v1 validiert Quelle, Erzeugungszeitpunkt, Rechtehinweis, IDs, Zeitpunkte und HTTPS-Quellen.
- Vollst?ndige Vorabvalidierung und einzelne Room-Transaktion verhindern Teilimporte.
- Room-Schema v6 erg?nzt `anime_external_ids` zur sp?teren Quellenzusammenf?hrung.
- Austauschbares `GermanMetadataSource`-Interface vorbereitet; kein produktiver AniSearch-Adapter aktiviert.
- README-Aussagen zu Fake-Daten, HTML-Dateiimport und produktiver AniSearch-Nutzung korrigiert.
- 78 Unit-Tests sowie beide APK-Artefakte erfolgreich gebaut; kein Ger?t verbunden.
- Keine APK als fertiges Produkt oder Testkandidat ausgeliefert.
# v0.10.0 ? robuster Diagnosetest auf realem Ger?t (2026-08-02)

- Lokalen JSON-Import nach Einstellungen ? Entwickler und Diagnose verschoben.
- Activity-eigenen `OpenDocument`-Launcher f?r stabilen Lifecycle erg?nzt.
- Import vollst?ndig auf `Dispatchers.IO` verschoben.
- Limits: 5 MB, 500 Anime, 5.000 Releases, begrenzte Texte/URLs.
- Strukturierte Rechtebest?tigung in UI und JSON-Schema erg?nzt.
- Reservierte interne IDs `local-import:<importId>:<externalId>` verhindern Live-Daten?berschreibung.
- Doppelte External-/Release-IDs und unbekannte Anime-Verweise werden vor Room abgelehnt.
- External-ID-Mappingkonflikte k?nnen bestehende Zuordnungen nicht umbiegen.
- Importbatch-Tabelle in Room-Schema v7 speichert Quelle, Rechtehinweis, Erzeugungs-/Importzeit und Zeitraum.
- Lokalisierte Importfehler und vollst?ndige Erfolgsdaten erg?nzt.
- Kalender ?ffnet nach Import/Neustart den ersten importierten Termin.
- Lokale Daten erscheinen nun auch auf Start und Entdecken.
- Technische Batch-ID aus normaler Kalender-UI entfernt.
- 84 lokale Tests erfolgreich; realer Migrationstest auf Galaxy S24 Ultra erfolgreich.
- Reale Import-, Kalender- und Neustartpersistenz auf Galaxy S24 Ultra best?tigt.
- Veraltete Navigation-/Golden-Tests bleiben offen und sind im Ger?tebericht pr?zise dokumentiert.
# Diagnose-Testbuild V2 ? 2026-08-02

- Automatischen Sprung von Einstellungen zum Kalender entfernt.
- Diagnoseimport ?ber Dataset-ID und SHA-256-Inhaltshash idempotent gemacht.
- Room-Schema auf Version 8 erweitert.
- Lokale Room-Titel auf Start und Entdecken unabh?ngig vom Live-Quellenfehler sichtbar gemacht.
- Technische Live-Fehlercodes durch verst?ndliche Oberfl?chentexte ersetzt.
- Navigationstests an die aktuelle Oberfl?che angepasst; 11/11 auf realem Ger?t bestanden.
- Zwei platzsparende 720p-Ger?teclips erg?nzt.
# Diagnose-Testbuild V3 ? 2026-08-02

- Migration 7 ? 8 f?r beliebig viele vorhandene Importbatches repariert.
- Migrationstests f?r null, einen und mehrere Altimporte erg?nzt.
- Raw-JSON-Hash durch kanonischen fachlichen Inhaltshash ersetzt.
- Regressionstests f?r Formatierungsidentit?t und echte Inhaltskonflikte erg?nzt.
- Technischen External-ID-Namensraum auf `LOCAL_DIAGNOSTIC` stabilisiert.
- README auf den validierten Diagnose- und tats?chlichen Produktstand aktualisiert.
- Kurzen 720p-Nachweis f?r Folge 2 am 2. August erg?nzt.
- Deaktivierbare WorkManager-Infrastruktur f?r den erlaubten AniList-Kalender erg?nzt.
- AniSearch- und Provider-Hintergrundjobs bleiben bis zur Quellenfreigabe deaktiviert.
# Diagnose-Testbuild V4 ? 2026-08-02

- UTC-Tagesgrenzen im AniList-Kalenderpfad durch die lokale Nutzerzeitzone ersetzt.
- Gemeinsame `CalendarTimeWindow`-Berechnung f?r Source und Repository eingef?hrt.
- Tests f?r Berlin-Mitternacht, Monatswechsel sowie Sommerzeitbeginn und -ende erg?nzt.
- Persistenten WorkManager-Status mit Versuchszahl, letztem Erfolg und n?chstem Retry erg?nzt.
- Hintergrundstatus lokalisiert im Kalender sichtbar gemacht.
- Exponentielle Retry-Berechnung separat getestet und auf 24 Stunden begrenzt.
- Separate Regressionstests best?tigen, dass HTTP 403 und 429 bestehende Room-Termine erhalten.
- AniSearch- und Provider-Hintergrundjobs bleiben deaktiviert.

# Diagnose-Testbuild V5 ? 2026-08-03

- Exakte lokale Mitternacht in der AniList-Query eingeschlossen.
- Dynamische gemeinsame Ger?tezeitzone f?r Quelle, Repository, Worker und UI eingef?hrt.
- Netzwerkaktualisierung, frischer Cache und Retry fachlich getrennt.
- Serverseitige und persistente Cooldowns in `retryNotBefore` ?bernommen.
- Pagination-Sicherheitsgrenze als unvollst?ndigen, nicht schreibenden Abruf behandelt.
- Quellenneutrales Release-Modell eingef?hrt; AniList-Titel werden nicht als Deutsch ausgegeben.
- Echten WorkManager-`WorkInfo`-Status beobachtbar gemacht.
- Debug-only Diagnosejob und 720p-Nachweis f?r `RUNNING ? RETRY` erg?nzt.
- 97 lokale Tests, 4 Migrationstests und 11 Navigationstests erfolgreich.
# v0.11.0-diagnostic

- Release-spezifischer Anbietercheck mit eindeutigem WorkManager-Namen.
- JustWatch-Titeltreffer und Episodenverf?gbarkeit werden getrennt bewertet.
- ?ffentlicher Crunchyroll-Episodenchecker ohne Login-, Playback-, DRM- oder Manifestzugriffe.
- AniWorld-Metadatenfallback zehn Minuten nach dem Solltermin.
- `DELAYED_CONFIRMED` nur nach zwei negativen, unabh?ngigen Pr?fungen; Fehler bleiben unbest?tigt.
- AlarmManager-Wecksignal plus WorkManager-Ausf?hrung f?r gesperrte und inaktive Ger?te.
- Neuplanung nach Neustart, App-Update, Zeit- und Zeitzonen?nderung.
- Benachrichtigungen enthalten, soweit vorhanden, Titel, Staffel und Folge.
- Countdown wird nach Ablauf nicht mehr als `00:00:00` dargestellt.
- Favoritenfilter verwenden reale Zeitgrenzen; Sortierung wird in DataStore gespeichert.
- Parser- und Status-Regressionspr?fungen erg?nzt.
# v0.14.0-diagnostic

- echten V13-Ger?tecrash aus dem Android-Crashpuffer analysiert und dokumentiert
- doppelte Compose-LazyColumn-Schl?ssel durch stabile JustWatch-IDs ersetzt
- Such- und Discover-Ergebnisse zus?tzlich anhand ihrer JustWatch-ID dedupliziert
- `Aktuell` und `Demn?chst` auf Anime-Ebene gegenseitig exklusiv gemacht
- Discover von der vollst?ndigen Anime- und Releasehistorie entkoppelt
- Discover verwendet nur aktive AniWorld-Releases f?r Laufend- und Sprachfilter
- parallele Discover-Refreshes dedupliziert und veraltete Suchl?ufe abgebrochen
- Regressionstests f?r doppelte interne Anime-IDs und ?berlappende Favoriten erg?nzt

# v0.13.0-diagnostic

- Startseite und Releasez?hler auf echte `ANIWORLD_CALENDAR`-Titel mit GER SUB/GER DUB begrenzt.
- Globale JustWatch-Suche vom Genre-Browser getrennt; kein erzwungenes `ani` bei Suchtexten.
- Unicode-/Akzentnormalisierung erg?nzt (`pokemon` entspricht `Pok?mon`).
- Eigene Katalogkarten ohne k?nstliche Episode 0, Releasecountdown oder Releasestatus eingef?hrt.
- DVD-, Blu-ray- und Buchangebote aus prim?ren Anbieterlisten entfernt.
- Favoriten-Anbietersortierung verwendet einen stabilen prim?ren Streaminganbieter.
- AniWorld-Synchronisierung bewahrt bereits vergangene normale Releases dauerhaft.
- Discover-Aktualisierung auf zwei begrenzte Katalogrequests reduziert statt serieller Titel-Provider-Vollabfrage.
- Regressionstests f?r Katalogtrennung, Suche, Anbieterfilter und Releasehistorie erg?nzt.

# v0.12.0-diagnostic

- Bottom Navigation auf f?nf einzeilige, adaptive Labels korrigiert und auf 1080?2340 sowie 720?1560 gepr?ft.
- Favoritenfilter verwenden den lokalen Kalendertag: `Aktuell` ist heute, `Demn?chst` beginnt morgen.
- `Abgeschlossen` kombiniert fehlende aktive Termine mit bekanntem Serienende statt nur `STOPPED` zu pr?fen.
- Filterabh?ngige Leerzust?nde erg?nzt.
- Sechs persistierte Favoritensortierungen erg?nzt.
- Eigenst?ndiger `DiscoverViewModel` und realer JustWatch-DE-Genre-/Titelcache in Room.
- Reale Genre-, Typ-, Sprach-, Anbieter- und Sortierfilter im Entdecken-Bereich.
- Globale Startseitensuche f?r aktuelle, ?ltere und abgeschlossene JustWatch-DE-Titel.
- Reale Poster und Anbieter aus JustWatch werden gespeichert und angezeigt.
- Verschiebungskarten zeigen alten Termin, neuen Termin und vorhandenen Grund.
- Room-Migrationen 12?13 und 13?14 sowie Schemaexport erg?nzt.
# v0.24.2-v24-auto-provider-probes

- experimentelle, gekapselte Crunchyroll-CMS-, Crunchyroll-Public-Web- und ADN-DE-Metadatenprobes erg?nzt
- `AVAILABLE`, `NOT_AVAILABLE_YET` und technische `CHECK_FAILED`-Ergebnisse fachlich getrennt
- ?ffentliche Crunchyroll-Serie ohne Login real mit Staffel, Episode, Watch-ID und deutschem Untertitel validiert
- `/de/videos/new` ausschlie?lich als `RELEASE_SIGNAL` modelliert; Dub-/Untertitelnachtr?ge setzen niemals allein `AVAILABLE`
- ADN strikt auf `X-Target-Distribution: de` begrenzt; `vostde` und `vde` separat ausgewertet
- stabile Series-/Season-/Episode-IDs und Provider-URLs ?ber Room-Schema 18 persistiert
- manuellen historischen Providercheck ohne Alarm, AUTO, Benachrichtigung oder `expectedAt`-?nderung erg?nzt
- echte Episoden-/Serien-URLs per `ACTION_VIEW` aus Episodenkarten ?ffnbar; keine geratenen URLs
- sekundengenauen laufenden und eingefrorenen Verz?gerungstimer erg?nzt
- 194 JVM-Tests und 7 Room-Migrationstests auf dem realen Ger?t erfolgreich
# v0.24.4-v24-provider-history

- anonymen ADN-DE-Historienimport hinter dem bestehenden Providerinterface erg?nzt
- ausschlie?lich explizite Episoden-Ver?ffentlichungsdaten ?bernommen; keine Rhythmus- oder Simulcast-Ableitung
- `vostde` und `vde` als getrennte GER-SUB-/GER-DUB-Historieneintr?ge persistiert
- Quellenpriorit?t und sichtbare Konfliktmarkierung f?r historische Termine erg?nzt
- historische Importe von AUTO, WorkManager, Alarmen und Benachrichtigungen ausgeschlossen
- ?ffentliche Providerlinks vor `ACTION_VIEW` validiert und fehlersicher ge?ffnet
- Room-Schema 19?20 samt neun realen Ger?temigrationstests erg?nzt
- 202 JVM-Tests und ein echter anonymer ADN-Live-Diagnosetest erfolgreich
- monatsbezogenen Crunchyroll-/ADN-Historienabgleich im Kalender erg?nzt; kein unerwarteter Monatssprung
- ?ffentliche Crunchyroll-Watch- und Suchaufl?sung nur bei exaktem Titelmatch erg?nzt
- automatische Crunchyroll-Historienerg?nzung beim ?ffnen einer sicher zugeordneten Serie erg?nzt
- Release-Verlauf nach AniWorld, Crunchyroll und ADN gruppiert
- best?tigte Episodenverf?gbarkeit gegen sp?tere technische Downgrades gesch?tzt
- vorhandene fehlerhaft herabgestufte Verf?gbarkeiten beim App-Start repariert
- 204 JVM-Tests erfolgreich
# v0.24.5-v24-crunchyroll-series-id

- Crunchyroll-Series-ID als stabilen Prim?rschl?ssel f?r ?ffentliche Serienseiten festgelegt
- lokalisierte, relative, sluglose und vollst?ndige `/series/{G?}`-URLs vereinheitlicht
- ?ffentlich eingebettete `series_id`- und `seriesId`-Felder aus Watch-Seiten erkannt
- kanonische URL `https://www.crunchyroll.com/de/series/{seriesId}` erg?nzt
- Titelsuche nur noch als Fallback; gefundene reale hrefs werden dauerhaft gespeichert
- vier reale deutsche Crunchyroll-Serienseiten ohne Login auf SM-S928B mit HTTP 200 validiert
- Regressionstests f?r Red River, Victoria of Many Faces, The Oblivious Saint und I Want to Love You erg?nzt
- 205 JVM-Tests, 9 Room-Migrationstests sowie Crunchyroll-/ADN-Livediagnosen erfolgreich
# v0.25.10 ? Kanonische Release-Identit?t ? 2026-08-24

- Zentrale, quellenunabh?ngige `ReleaseIdentity` f?r Anime, Staffel, Episode und Sprachfassung erg?nzt; Quell-IDs gelten nicht mehr als fachliche Episodenidentit?t.
- ?Letzter Release? priorisiert jetzt die h?chste semantisch best?tigte Folge und den st?rksten Beleg statt lediglich des j?ngsten Datums.
- Detail-Countdown und Verschiebungshinweis sind an exakt denselben n?chsten Release gebunden; abgelaufene abgeschlossene Hinweise werden auf regul?ren Karten ausgeblendet.
- AniSentinels Erkennungszeitpunkt wird nicht mehr als Provider-Verz?gerung ausgegeben.
- Direkte, AniWorld-, historische und aus sp?teren Episoden abgeleitete Belege sind fachlich unterscheidbar; Ableitungen werden in der UI als wahrscheinlich statt direkt best?tigt bezeichnet.
- Neue standardm??ig deaktivierte Einstellung ?Bei geplantem Release benachrichtigen?: ausschlie?lich Favoriten, genau am Termin; Providerpr?fungen laufen auch bei ausgeschaltetem Hinweis weiter.
- Doppelte Zeiteinheit ?Uhr Uhr? in Release- und Pr?fzeittexten beseitigt.
