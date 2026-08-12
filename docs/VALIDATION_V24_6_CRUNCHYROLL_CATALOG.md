# AniSentinel v0.24.6 – Validierung Crunchyroll-Katalog

## Ergebnis

Der Fehler `CRUNCHYROLL_PUBLIC_HISTORY_NOT_PARSEABLE` entstand, weil die öffentliche Serienseite
auf dem Android-Gerät nur eine generische React-Shell ohne Episodendaten liefert. v0.24.6 verwendet
den anonym erreichbaren strukturierten Katalogweg. Es werden ausschließlich Metadaten gelesen;
Login, Cookies, Playback, Streams, Manifeste, DRM und Downloads sind nicht Bestandteil des Adapters.

## Generischer Datenweg

1. Vorhandene Serien-URL oder stabile Series-ID übernehmen.
2. Eine Watch-ID über öffentliche Objektmetadaten zur Series-ID auflösen.
3. Fehlt beides, eine exakte Katalogtitelsuche durchführen.
4. `series → seasons → episodes` laden.
5. GER_SUB nur bei deutschem `subtitle_locale`, GER_DUB nur bei deutschem `audio_locale` erzeugen.
6. Historie nur aus expliziten Providerfeldern übernehmen; 9998/9999-Sentinelwerte verwerfen.
7. Nur Termine des gewählten Kalendermonats persistieren.

## Reale Geräteabnahme am 12.08.2026

- Unbekannter Abnahmetitel `BLACK TORCH` wurde selbstständig als `GT00377907` erkannt; auch die
  reine Titelsuche ergab dieselbe ID.
- Eine Staffel, sechs Episoden und sechs deutsch relevante Episodenmetadaten geladen.
- Room-End-to-End-Test speicherte sechs exakte historische Datensätze.
- Reale App-Synchronisation August 2026: 66 Serien geprüft, 91 Crunchyroll-Termine sichtbar.
- BLACK TORCH Folge 6: 08.08.2026, 15:00 Uhr lokal, GER_SUB, echte Watch-URL, verfügbar.
- The Oblivious Saint: Folgen 6 und 7 mit echten Providerzeitpunkten und Watch-URLs gespeichert.

## Tests

- JVM: 207/207 erfolgreich.
- Daten-, Live- und Migrationstests auf Gerät: 15/15.
- Golden-/Accessibility-Tests auf Gerät: 10/10.
- Navigation-/Compose-Tests auf Gerät: 11/11.

Die UI-Klassen wurden getrennt ausgeführt, weil das Samsung-Testgerät beim Berechtigungswiderruf
zwischen Testklassen den instrumentierten Prozess systemseitig beendet. Getrennt sind alle 36
Gerätetests erfolgreich.
