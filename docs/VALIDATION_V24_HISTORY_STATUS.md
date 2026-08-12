# AniSentinel v0.24.3 – Validierung Historie und Statuslogik

## Ergebnis

- Build: `assembleDebug` erfolgreich.
- Unit-Tests: 198/198 erfolgreich.
- Room-Migrationstests: 8/8 direkt auf SM-S928B erfolgreich.
- APK installiert und gestartet; bestehende lokale Daten blieben bei der Migration erhalten.
- Konkreter Gerätefall „A Livid Lady's Guide …“, Staffel 1 Folge 6: `CHECK_FAILED` wird neutral als technische Störung angezeigt; kein Verspätungszähler und keine Verzögerungsbehauptung.

## Statussemantik

| Status | Anzeige des Verspätungszählers |
|---|---|
| `NOT_AVAILABLE_YET` | läuft ab dem geplanten Termin |
| `AVAILABLE` + `firstAvailableAt` | am realen Erkennungszeitpunkt eingefroren |
| `CHECK_FAILED` | verborgen; technische Fehlerursache und Prüfzeitpunkt bleiben sichtbar |
| geplant / ausstehend / verschoben | verborgen |

## Historischer Crunchyroll-Import

Der Import akzeptiert ausschließlich öffentliche Crunchyroll-Serienseiten und übernimmt nur Episoden,
bei denen Staffel, Episode, konkretes öffentliches Datum, konkrete Sprachkennzeichnung und echte HTTPS-URL
auswertbar sind. Fehlende Werte werden nicht geraten. Bereits vorhandene semantisch gleiche Releases werden
angereichert; neue Einträge werden nach Anime, Staffel, Episode, Sprache und Provider stabil identifiziert.

Die Kennzeichnung `isHistoricalImport` wird in allen Scheduler- und Workerpfaden hart geprüft. Historische
Termine erzeugen weder Due- noch Availability-Benachrichtigungen, keine Exact-Alarme, keine AUTO-Schleife,
keinen WorkManager-Providercheck und keinen T+10-Fallback. Sie bleiben von der normalen Fensterbereinigung
ausgenommen und damit in alten Kalendermonaten persistent.

## Bewusste Grenze

Wenn Crunchyroll eine öffentliche Serienseite ohne belastbares Episodendatum oder ohne konkrete
Sprachinformation ausliefert, bricht der Import ehrlich mit Diagnosecode ab. Ein allgemeines Signal von
`/de/videos/new` wird weiterhin niemals als Episodenverfügbarkeit oder historisches Releasedatum verwendet.
