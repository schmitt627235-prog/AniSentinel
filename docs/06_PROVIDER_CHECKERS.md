# Provider-Checker

## EpisodeCheckRequest
- interne Anime-ID
- externe Anbieter-Serie-ID
- Staffel-ID, falls vorhanden
- Episodennummer
- gewünschte Fassung: OmU, Deutsch, beide
- Region: DE
- erwarteter Termin

## AvailabilityResult
- provider
- seriesMatched
- episodeFound
- available
- regionMatched
- audioLanguages
- subtitleLanguages
- releaseAt, falls Quelle meldet
- episodeUrl
- checkedAt
- confidence
- evidence list
- error

## Entscheidungslogik
`available=true` nur, wenn genügend Evidenz vorliegt, beispielsweise:
- konkrete Episode existiert
- für DE sichtbar
- Freigabezeit erreicht bzw. Status verfügbar
- gewünschte Sprachfassung bestätigt oder klar dem Staffelobjekt zugeordnet

## Confidence-Beispiele
- 0.95–1.0: offizielle Episodenmetadaten mit DE und Sprachfassung
- 0.80–0.94: öffentliche Episodenseite eindeutig verfügbar
- 0.60–0.79: Episode in Katalogdaten, Freigabestatus nicht vollkommen eindeutig
- unter 0.60: nicht automatisch als verfügbar melden

## Rate Limits
Jeder Checker definiert:
- minimale Zeit zwischen Requests
- Cache TTL
- maximale parallele Requests
- Retry-After-Unterstützung
- Circuit Breaker bei 403/429

## Teststrategie
- gespeicherte anonymisierte HTML-/JSON-Fixtures
- Tests für vorhanden, upcoming, verfügbar, Sprachfassung fehlt, Region fehlt, Layout geändert
- keine Live-Netzwerktests in normaler CI
