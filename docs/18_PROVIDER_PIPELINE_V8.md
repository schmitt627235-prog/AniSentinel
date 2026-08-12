# Provider-Pipeline V8

## Verbindlicher Hauptpfad

`AniWorld-Termin → AniWorld-Verschiebung → JustWatch-DE-Zuordnung → direkte Episodenprüfung`

AnimeRadar und AniList bleiben internationale Fallbacks beziehungsweise ID-/Metadatenhilfen.
AniSearch bleibt optional. Keine dieser Quellen bestätigt eine deutsche Providerverfügbarkeit.

## JustWatch-Zugang

Der Produktadapter ist ausschließlich für einen offiziellen oder ausdrücklich freigegebenen
Partnerzugang vorgesehen. Im vorliegenden Workspace liegen keine solchen Zugangsdaten vor.
Deshalb meldet die App ehrlich `SOURCE_NOT_CONFIGURED`. Undokumentierte Webrequests,
Browser-Tokens und Scraping hinter Anmeldung werden nicht verwendet.

Im Debug-Diagnosebuild validiert ein lokaler Datensatz den internen Datenfluss. Seine Datensätze,
Links und UI-Hinweise tragen `LOCAL_DIAGNOSTIC_DATASET` beziehungsweise `local-diagnostic://`.
Sie sind kein Nachweis einer realen Listung oder Episodenverfügbarkeit.

Offizielle Dokumentation:

- `https://apis.justwatch.com/docs/content_partner/`
- `https://apis.justwatch.com/docs/api/`
- `https://partners.justwatch.com/`

Die Partner-API besitzt passende Routen für Titelangebote sowie Staffel-/Episodenangebote nach
JustWatch-/TMDb-ID oder Titel, Jahr und Staffel. Jede Anfrage benötigt jedoch den individuellen
Partner-Token, der erst nach Vertragsabschluss ausgegeben wird. Der Token darf nicht als Secret
in eine öffentliche APK eingebettet werden; für die Produktaktivierung ist ein kontrollierter
AniSentinel-Backend-Proxy beziehungsweise ein ausdrücklich freigegebenes mobiles Zugangsmodell nötig.

## Persistenz und Status

- `justwatch_title_matches`: eindeutige, mehrdeutige oder fehlende Zuordnung
- `justwatch_offers`: allgemeine Angebote; setzen nie automatisch Verfügbarkeit
- `episode_provider_availability`: konkreter Release + Anbieter + Staffel + Episode + Sprache
- Status: `SCHEDULED`, `DUE`, `DELAYED`, `AVAILABLE_GER_SUB`, `AVAILABLE_GER_DUB`,
  `AVAILABLE_GER_SUB_AND_DUB`, `CHECK_FAILED`
- `firstAvailableAt` bleibt beim Upsert unverändert; `lastUnavailableAt` und `lastCheckedAt`
  dokumentieren das ehrliche Prüfzeitfenster.

Netzwerk-, Parser- und Konfigurationsfehler ergeben `CHECK_FAILED`, niemals `DELAYED` oder
„Episode fehlt“. Folgeprüfungen verwenden 0, 5, 10, 20, 30 und maximal 60 Minuten Abstand.
