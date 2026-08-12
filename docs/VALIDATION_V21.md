# AniSentinel v0.21.0 – Validierungsbericht

Datum: 2026-08-09  
Build: `0.21.0-v21-diagnostic` (`versionCode 33`)  
Room: Schema 16, unverändert

## Build und Tests

- `testDebugUnitTest assembleDebug`: erfolgreich.
- 169 Tests: 169 erfolgreich, 0 Fehler.
- Quellen-/URL-Zuordnung für Anime2You und AniWorld getestet.
- Fehlende und nicht-HTTPS-fähige URLs erzeugen keine Browseraktion.
- V20-Favoritenstatus, RSS-Parser, Quellen-Dedup und No-Fake-Kalendertrennung bleiben grün.
- Dara-san-, One-Piece- und due+upcoming-Provider-Sync-Regressionen bleiben grün.

## Navigation

Die News-Liste enthält keinen `LocalUriHandler` mehr. Ein Kartenklick navigiert ausschließlich über:

```text
news/{announcementId}
```

Die ID wird URL-kodiert; das vollständige Objekt wird nicht über die Route serialisiert. Der
`NewsDetailViewModel` liest den Datensatz über `announcementId` direkt aus Room.

## Interne Detailansicht

Je nach tatsächlich gespeicherten Werten werden angezeigt:

- Kategorie und Titel,
- RSS-Zusammenfassung,
- optionales Bild,
- Veröffentlichungszeit,
- Staffel,
- alter und neuer Termin,
- Releasefenster und Grund,
- Provider,
- eine oder mehrere Quellen.

Null- oder Leerwerte werden nicht durch Platzhalterfakten ersetzt. Ein nicht ladbares Bild lässt
den gesamten Textinhalt weiter nutzbar.

## Browser- und Zurückverhalten auf dem realen Gerät

V21 wurde auf Samsung SM-S928B installiert. Nach Klick auf die reale Meldung
`AKIBA ANIME ab sofort auf Prime Video und weiteren Plattformen` blieb der Fensterfokus auf:

```text
de.anisentinel.app/de.anisentinel.app.MainActivity
```

Die interne Seite zeigte Zusammenfassung, Veröffentlichungszeit, `Quellen: Anime2You` und den
separaten Button `Originalartikel bei Anime2You öffnen`. Erst ein bewusster Klick auf diesen Button
öffnete Firefox. Android-Zurück führte zurück zu AniSentinel; die interne Zurückschaltfläche führte
zur News-Liste. Im Lauf trat keine `FATAL EXCEPTION` und kein App-ANR auf.

## Offline-Eigenschaft

Liste und Detailansicht beobachten ausschließlich persistierte `announcements` aus Room. Das Öffnen
einer Detailseite startet keinen RSS- oder Artikelabruf. Lediglich das freiwillige Öffnen eines
externen Quellenlinks benötigt Netzwerk. Es wird kein vollständiger Artikel gescrapt und es wurden
keine Login-, Account- oder Kommentarfunktionen nachgebaut.

## Unveränderte Produktregeln

Anime2You bleibt redaktionelle RSS-Quelle, nicht Episode-Availability-Quelle. AniWorld bleibt
Releasekalender und T+10-Fallback. JustWatch bleibt Titel-/Providerzuordnung. Exact Alarm,
Fallbackalarm, Deep-Link, Current Season und Favoritenklassifizierung wurden nicht umgebaut.
