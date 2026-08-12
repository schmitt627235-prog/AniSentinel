# AniSentinel v0.24.2 – Validierungsbericht V24 Provider-Probes

## Ergebnis

Die Diagnose-App enthält drei gekapselte, parallel zum bisherigen Checker laufende Probes:

```text
CRUNCHYROLL_STRUCTURED_METADATA_PROBE
CRUNCHYROLL_PUBLIC_WEB_PROBE
ADN_STRUCTURED_METADATA_PROBE
```

Sie werden noch nicht als alleinige primäre Freigabequelle verwendet. AniWorld bleibt der
T+10-Fallback. JustWatch bleibt Titel-/Providerzuordnung und ist kein Episodennachweis.

Technische Referenzen: `https://github.com/crunchy-labs/crunchyroll-rs` und
`https://github.com/anidl/multi-downloader-nx`. Übernommen wurden ausschließlich öffentlich
nachvollziehbare Metadatenmuster, kein Playback-/Downloadcode.

## Statussemantik

- `AVAILABLE_*`: konkrete Episode und erwartete deutsche Sprachfassung belegt.
- `NOT_AVAILABLE_YET`: Providerantwort erfolgreich ausgewertet, Ziel-Episode oder Zielsprache fehlt.
- `CHECK_FAILED`: technischer Abruf-, Auth-, Schema-, Parser- oder sicherer Zuordnungsfehler.
- Verschiebungen bleiben als `POSTPONED`/`DELAYED` getrennt und stoppen AUTO.

## Crunchyroll

Der strukturierte CMS-Pfad `/content/v2/cms/seasons/{seasonId}/episodes` ist implementiert.
Der reale anonyme Abruf antwortete jedoch mit `content.error.invalid_auth_token`. Ohne Nutzerlogin,
Account-Cookies oder fremde Zugangsdaten wird dies ehrlich als
`CRUNCHYROLL_ANONYMOUS_METADATA_AUTH_REQUIRED` gespeichert.

Als zweiter Weg ist `CrunchyrollPublicWebAdapter` implementiert. Er verwendet ausschließlich
öffentliche deutsche Serienseiten. Real validiert am 11.08.2026:

```text
Serie: Red River
Series-ID: GT00378099
Staffel: 1
Episode: 6
Watch-ID: GE00379379JAJP
Audio: Japanese
Untertitel: Deutsch u. weitere
Ergebnis für GER_SUB: AVAILABLE
```

Die Seite war ohne Login erreichbar. Die reduzierte reale Seitenform ist als Regressionstest
gesichert. GER_DUB wird aus dieser Seite nicht abgeleitet.

`https://www.crunchyroll.com/de/videos/new` wurde ebenfalls real geprüft. Dort stehen aktuelle
Titel, Series-IDs sowie allgemeine Kennzeichnungen wie `Untertitel` oder
`Untertitel | Synchro`. Diese Seite erzeugt im Code ausschließlich `CrunchyrollReleaseSignal`.
Jedes Signal muss anschließend über bekannte Series-ID → Staffel → Episode → erwartete Sprache
bestätigt werden. Der Negativtest „älterer Titel erscheint wegen Dub-Nachtrag, Ziel-Episode fehlt“
endet mit `NOT_AVAILABLE_YET`.

## ADN Deutschland

Verwendet werden ausschließlich anonyme Metadatenendpunkte:

```text
GET https://gw.api.animationdigitalnetwork.com/show/catalog
GET https://gw.api.animationdigitalnetwork.com/video/show/{showId}?maxAgeCategory=18&limit=-1&order=asc
X-Target-Distribution: de
```

Der reale DE-Test lieferte für `Demon King Daimao` die Show-ID `1133` und Episoden 1–12 mit
konkreten Episoden-IDs. Die gelieferten Sprachcodes werden so ausgewertet:

```text
vostde → GER_SUB
vde    → GER_DUB
```

Episode 6 war als GER_SUB und GER_DUB positiv auswertbar. Episode 13 bildet den realen
„noch nicht gelistet“-Fall und wird als `NOT_AVAILABLE_YET` getestet. Andere Märkte werden vor
jedem Netzaufruf abgewiesen. Es gibt keine ADN-FR→DE-Ableitung.

## Persistenz und AUTO

Room-Schema 18 ergänzt `provider_metadata_identities` mit Anime-ID, Adapter, Markt, Series-/Show-ID,
Staffel, Episode, Season-ID, Episode-ID, Quell-URL und Prüfzeit. Die sichere ID wird bei kurzen
AUTO-Intervallen wiederverwendet. Die bestehende favoritengebundene Staffel bleibt unverändert:

```text
0–5 min 30 s · 5–10 min 1 min · 10–60 min 5 min · 1–4 h 30 min · danach 1 h
```

`NOT_AVAILABLE_YET` und `CHECK_FAILED` lassen AUTO weiterlaufen. `AVAILABLE` und bestätigte
Verschiebungen stoppen. Der Verzögerungstimer läuft sekundengenau ab `expectedAt`, friert bei
`firstAvailableAt` ein und stoppt bei Verschiebung.

## Historischer Diagnosepfad und Deep-Link

„Verfügbarkeit jetzt prüfen“ ruft dieselben Provideradapter auf, ändert aber weder `expectedAt`
noch Release-Status, Alarmplanung oder Benachrichtigungen. Angezeigt bzw. gespeichert werden
Provider, IDs, Ergebnis, Prüfzeit und technische Fehlerursache. Eine reale gespeicherte HTTPS-
Episoden-URL hat beim Öffnen Vorrang vor der Serien-URL. Android öffnet sie über
`Intent.ACTION_VIEW`; ohne reale URL wird kein Link geraten.

## Sicherheit und ausgeschlossene Funktionen

Nicht enthalten: Login, Nutzerkonto, Cookies eines Nutzers, Wiedergabe, Stream-/Manifestzugriff,
Download, Entschlüsselung, DRM-Umgehung oder CAPTCHA-/Schutzumgehung.

## Automatisierte Prüfung

- `testDebugUnitTest`: **194/194 erfolgreich**.
- `assembleDebug`: erfolgreich.
- Room-Migrationen auf Samsung SM-S928B: **7/7 erfolgreich**, einschließlich 17→18.
- APK installiert und `MainActivity` gestartet; kein FATAL-/Room-Migrationsfehler im frischen Logcat.
- Der Compose-Gesamtlauf wurde vom Samsung-System unmittelbar mit `permissions revoked` beendet;
  dies ist kein App-Stacktrace und wird daher nicht als bestandener UI-Test ausgewiesen.

## Offene Punkte

- Crunchyroll-CMS bleibt ohne zulässige anonyme Authentifizierung Diagnose-Fehler; Public Web ist
  der reale anonyme Alternativweg.
- Public-Web-Markup muss bei Änderungen weiterhin mit echten Fixtures nachgezogen werden.
- Die Probes bleiben bis zu mehreren echten Live-Releases parallel und diagnostisch.
- Clevatess S2E6 GER SUB/DUB bleibt der nächste zeitgebundene Live-Abnahmepunkt.
