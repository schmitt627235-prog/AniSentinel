# News- und Verschiebungserkennung

## Ziel
Nicht nur erkennen, dass eine Folge fehlt, sondern möglichst erklären, ob und warum sie verschoben wurde.

## Verarbeitung
1. Neue Feed-/Newsitems seit letzter Prüfung laden.
2. Titelvarianten normalisieren.
3. Anime über exakte IDs, Titel, Synonyme und Episodennummer matchen.
4. Verzögerungsbegriffe erkennen.
5. Datum/Uhrzeit extrahieren.
6. Quelle klassifizieren.
7. Meldungen deduplizieren.

## Schlüsselbegriffe
Deutsch:
- verschoben
- verspätet
- später
- technische Probleme
- Programmänderung
- neuer Termin
- fällt aus

Englisch:
- delayed
- postponed
- schedule change
- technical issues
- rescheduled

## Vertrauensstufen
- `OFFICIAL`: Anbieter, Lizenzgeber, offizieller Anime-/Publisheraccount
- `EDITORIAL`: etablierte Nachrichtenseite
- `SOCIAL_HINT`: unbestätigter Social-Hinweis

## Regeln
- Social-Hinweis niemals als offiziell darstellen.
- Ein Artikel über den Anime allgemein ist keine Verschiebungsmeldung.
- Bei erkanntem neuem Termin Nutzer informieren und Watcher neu planen.
- Originalquelle und Veröffentlichungszeit anzeigen.
