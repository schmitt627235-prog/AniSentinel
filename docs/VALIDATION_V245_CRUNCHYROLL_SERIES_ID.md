# AniSentinel v0.24.5 – Crunchyroll-Series-ID-Validierung

## Korrektur

Crunchyroll-Serien werden nicht über selbst erzeugte Slugs identifiziert. Der
stabile Schlüssel ist die reale Series-ID im öffentlichen Pfad:

```text
/series/{G...}
```

Die Erkennung akzeptiert vollständige, relative, lokalisierte und sluglose
URLs sowie öffentlich eingebettete `series_id`-/`seriesId`-Felder. Nach einer
sicheren Auflösung werden ID und reale beziehungsweise kanonische Serien-URL
in Room gespeichert und bei späteren Checks wiederverwendet.

## Reale Geräteprüfung

Gerät: Samsung SM-S928B, deutscher App-Netzwerkpfad, ohne Login.

```text
GT00378099 · HTTP 200 · /de/series/GT00378099/red-river
GT00378126 · HTTP 200 · /de/series/GT00378126/victoria-of-many-faces
GT00378125 · HTTP 200 · /de/series/GT00378125/the-oblivious-saint-cant-contain-her-power
GT00374354 · HTTP 200 · /de/series/GT00374354/i-want-to-love-you-till-your-dying-day
```

Alle vier Antworten wurden über denselben `PublicProviderMetadataTransport`
wie die App geladen. Es wurden keine Auth-, Playback-, Manifest-, Stream-,
Download- oder DRM-Endpunkte verwendet.

## Status

- JVM: 205/205 erfolgreich
- Room-Migrationen auf SM-S928B: 9/9 erfolgreich
- Crunchyroll-Series-ID-Livetest: 1/1 erfolgreich
- anonymer ADN-Metadaten-Livetest: 1/1 erfolgreich
- Debug-APK gebaut, installiert und gestartet

## Grenzen

Eine aufgelöste Series-ID bestätigt nur die Serienidentität. `AVAILABLE` wird
weiterhin erst gesetzt, wenn Staffel, Episode und erwartete Sprachfassung auf
dem konkreten Providerweg bestätigt wurden. Eine Series-ID allein ist kein
Verfügbarkeitsnachweis.
