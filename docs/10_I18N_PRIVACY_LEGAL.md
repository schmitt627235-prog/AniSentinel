# Internationalisierung, Datenschutz und rechtliche Leitplanken

## Sprache
Version 1 zeigt Deutsch. Alle Strings müssen dennoch von Beginn an in Android-Ressourcen liegen. Keine hartcodierten UI-Texte.
Später `values-en` ergänzen.

## Datenschutz
- standardmäßig kein Konto
- lokale Datenbank
- eigenes Profilbild bleibt lokal
- keine Analyse-/Tracking-SDKs im MVP
- Diagnoseexport nur nach Nutzeraktion und ohne Cookies/Tokens
- Daten löschen/exportieren in Einstellungen

## Rechtliche Leitplanken
- nur öffentliche Metadaten und legale Anbieterlinks
- keine Streams, DRM, Downloads oder Umgehung von Zugriffskontrollen
- Nutzungsbedingungen und robots.txt vor Aktivierung eines Scrapers prüfen
- Quelle und Attribution anzeigen
- Anbieterlogos nur gemäß jeweiliger Markenregeln
- AI-Mockups im Referenzordner sind Designkonzepte, keine finalen Assets

## Scraping
Für undokumentierte Webseiten:
- minimaler Abruf
- Caching
- eindeutiger User-Agent mit Kontaktoption, sobald veröffentlicht
- Backoff bei Fehlern
- keine Umgehung von Bot-Schutzmaßnahmen
- Quelle per Feature-Flag deaktivierbar
