# Release-Wächter

## Konfigurierbare Intervalle
Unterstützte Auswahl:
- 30 Sekunden
- 1 Minute
- 2 Minuten
- 5 Minuten
- 10 Minuten
- 15 Minuten
- 30 Minuten
- 1 Stunde
- Automatisch empfohlen

## Empfohlenes Automatikprofil
- 5 Minuten vor Termin: Termin und News vorprüfen
- 0–10 Minuten: jede Minute
- 10–30 Minuten: alle 5 Minuten
- 30–60 Minuten: alle 10 Minuten
- 1–6 Stunden: alle 30 Minuten
- danach: jede Stunde
- Standardende nach 24 Stunden oder bei offiziellem neuen Termin

## Benutzerdefinierte Phasen
Nutzer kann globales Standardprofil definieren und pro Favorit überschreiben.
Beispiel:
```text
Phase 1: 0–10 Minuten, alle 30 Sekunden
Phase 2: 10–30 Minuten, alle 5 Minuten
Phase 3: 30–60 Minuten, alle 10 Minuten
Phase 4: danach alle 30 Minuten
Ende: nach 12 Stunden
```

## Android-Ausführung
- 30 Sekunden bis wenige Minuten: nur aktiver, sichtbarer Foreground Service
- 15 Minuten und länger: WorkManager geeignet, aber nicht sekundengenau
- einmaliges Starten kurz vor Release: Alarm/WorkManager je nach Plattformregeln
- keine Dauerschleife über den ganzen Tag

## Optimierungen
- nur Favoriten
- bekannte Serien-/Staffel-ID direkt verwenden, nicht jedes Mal suchen
- gleichzeitige Prüfungen desselben Anbieters bündeln
- kein Laden von Bildern/Videos im Hintergrund
- News separat und deutlich seltener prüfen
- exponentielles Backoff bei Fehlern

## Zeitgenauigkeit
Speichern:
- `lastUnavailableAt`
- `firstAvailableAt`

Anzeige:
„Verfügbar erkannt um 18:15 Uhr; Veröffentlichung lag zwischen 18:10 und 18:15 Uhr.“
Nicht behaupten, die exakte Server-Freischaltzeit zu kennen.

## Zustandsübergänge
```text
SCHEDULED -> PRECHECK -> CHECKING
CHECKING -> AVAILABLE
CHECKING -> DELAYED_UNCONFIRMED
DELAYED_UNCONFIRMED -> POSSIBLY_POSTPONED
POSSIBLY_POSTPONED -> OFFICIALLY_POSTPONED
jeder Zustand -> UNKNOWN bei Quellenfehler, ohne bisherigen Status zu überschreiben
```
