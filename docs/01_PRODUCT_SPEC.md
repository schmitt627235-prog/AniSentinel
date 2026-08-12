# Produktspezifikation

## Produktname
AniSentinel

## Slogan
Never miss a release.

## Problem
Klassische Anime-Datenbanken zeigen häufig japanische Ausstrahlungstermine, aber nicht zuverlässig, wann eine konkrete Episode in Deutschland mit gewünschter Sprachfassung bei einem legalen Anbieter tatsächlich freigeschaltet wurde. Anbieter können Folgen verspätet veröffentlichen oder Termine kurzfristig verschieben.

## Lösung
AniSentinel kombiniert erwartete deutsche Releaseinformationen mit gezielten Verfügbarkeitsprüfungen bei legalen Streaminganbietern. Nur Favoriten werden überwacht. Bei fehlenden Folgen sucht die App zusätzlich nach offiziellen oder redaktionellen Verschiebungsmeldungen.

## Kernnutzerfluss
1. Nutzer entdeckt oder sucht einen Anime.
2. Nutzer markiert ihn als Favorit.
3. Nutzer wählt OmU, Deutsch oder beide Fassungen.
4. App ermittelt Anbieter und erwarteten deutschen Termin.
5. App plant ein begrenztes Überwachungsfenster.
6. App prüft die konkrete Folge nach gewähltem Takt.
7. Bei Fund: lokale Benachrichtigung und Speicherung des Fundzeitpunkts.
8. Bei Verspätung: News-/Verschiebungsquellen prüfen und Status erklären.

## Statusmodell
- `SCHEDULED`: erwartet, Termin liegt in der Zukunft
- `PRECHECK`: Termin/Quelle kurz vor Release wird validiert
- `CHECKING`: aktive Prüfung läuft
- `AVAILABLE`: Folge wurde in der gewünschten Fassung erkannt
- `DELAYED_UNCONFIRMED`: Termin überschritten, keine offizielle Meldung
- `POSSIBLY_POSTPONED`: redaktioneller oder Social-Hinweis
- `OFFICIALLY_POSTPONED`: offizieller Anbieter-/Publisherhinweis
- `UNKNOWN`: Quelle nicht erreichbar oder Ergebnis uneindeutig
- `STOPPED`: Überwachung beendet

## Hauptnavigation
- Start
- Kalender
- Favoriten
- Entdecken
- Einstellungen

## Zusätzliche Bereiche im Hamburger-Menü
- Anbieter
- Aktuelle Season
- Neue Dub-Releases
- Heiß erwartete Titel
- News & Meldungen
- Release-Statistik
- Changelog
- Über AniSentinel

## Benachrichtigungen
- Folge verfügbar
- Offiziell verschoben
- Erhebliche Verspätung, standardmäßig nach 60 Minuten
- Neue deutsche Synchronfassung
- Keine Meldung bei jeder erfolglosen Prüfung

## Profil
- Benutzername lokal
- eigenes Bild aus Galerie/Kamera optional
- alternativ integrierte AniSentinel-Wächter-Avatare
- kein Konto für Version 1 erforderlich
