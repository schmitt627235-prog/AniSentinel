# Android-Hintergrundstrategie

## Grundsatz
Die App schläft fast immer. Sie plant nur den nächsten relevanten Termin.

## WorkManager
Verwenden für:
- tägliche Favoriten-/Kalenderaktualisierung
- Newsfeeds
- Intervall >= 15 Minuten
- nachholbare Prüfungen

Hinweis: Periodische Arbeit ist nicht exakt und kann durch Doze verzögert werden.

## Foreground Service
Nur für vom Nutzer bewusst aktivierte Live-Überwachung mit kurzen Intervallen.
- sichtbare Benachrichtigung
- zeigt Titel, Folge, nächste Prüfung, Stop-Schaltfläche
- beendet sich bei Fund, Verschiebung, Ablauf oder Nutzerstopp

## Akku-/Datenschutzprofile
- Sparsam
- Ausgeglichen (Standard)
- Schnell
- Benutzerdefiniert

## Datenverbrauch
- nur JSON/HTML/Headers
- keine Cover in Hintergrundjobs
- Netzwerkanfragen zusammenfassen
- optional nur WLAN

## Neustart/Zeitzonenwechsel
- nach Reboot Watcher neu planen
- bei Zeitzonen- oder Uhrzeitänderung alle erwarteten lokalen Zeiten neu berechnen
- geplante Uhrzeiten als Instant plus Ursprungszeitzone speichern
