# AniSentinel v0.25.16 Build 17 – gestufte AniSearch-Live-Stichprobe

Zeitpunkt: 24.08.2026, reales Samsung SM-S928B, installierter `versionCode 63`.

## Ablauf und Ergebnis

Die Prüfung wurde wie gefordert gestuft begonnen: zuerst drei Titel, erst danach wären fünf und anschließend zwanzig Titel zulässig gewesen. AniSearch antwortete in der ersten Stufe mit HTTP 429 und ohne `Retry-After`. AniSentinel setzte deshalb den globalen exponentiellen Standard-Backoff auf 1.800 Sekunden, behielt vorhandene Daten und setzte keinen negativen Lizenzstatus.

- Tatsächliche AniSearch-Netzwerkantwort: HTTP 429
- Erfolgreiche AniSearch-Liveantworten: 0
- Weitere HTTP-Requests nach 429: 0; weitere Aufrufe wurden lokal durch den Cooldown abgefangen
- Cachetreffer mit bestätigtem DACH-Beleg: 1 (The Apothecary Diaries Season 3)
- Stufe 3: abgebrochen nach 429
- Stufe 5: aus Schutzgründen nicht gestartet
- Stufe 20: aus Schutzgründen nicht gestartet

Damit ist die Live-Matchquote unter den aktuellen Zugriffsbedingungen nicht seriös messbar. `UNKNOWN` wird nicht künstlich vermieden; ein falscher Match oder falscher Negativstatus wäre schlechter als dieser Abbruch.

## Dynamisch aus AniList gewählte 20 Kandidaten

Die Kandidaten stammen aus dem auf dem Gerät gespeicherten echten AniList-`NOT_YET_RELEASED`-Datensatz. English, Romaji, Native und Synonyme gehen lokal dedupliziert in die Variantenerzeugung ein. Nach dem 429 wurden sie nicht einzeln gegen AniSearch gesendet.

| # | AniList-ID | Primärtitel / wichtige Varianten | AniSearch-Treffer | Confidence | DACH-Ergebnis |
|---:|---:|---|---|---:|---|
| 1 | 195516 | The Apothecary Diaries Season 3 / Kusuriya no Hitorigoto 3rd Season / 薬屋のひとりごと 第3期 | ID 20704 aus verifiziertem Cache | 100 | `DACH_CONFIRMED`, Crunchyroll, ab 10.2026 |
| 2 | 195604 | Black Clover Season 2 / Black Clover 2nd Season / ブラッククローバー 第2期 | nicht live ermittelbar | – | `DACH_UNKNOWN`, 429 |
| 3 | 195539 | Cyberpunk: Edgerunners 2 / Edgerunners II / サイバーパンク: エッジランナーズ2 | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 4 | 198966 | Dandadan 3rd Season / DAN DA DAN Season 3 / ダンダダン 第3期 | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 5 | 133007 | Puella Magi Madoka Magica Movie 4 / Walpurgisnacht Rising | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 6 | 171952 | Kage no Jitsuryokusha: Zankyou-hen / The Eminence in Shadow: Lost Echoes | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 7 | 181641 | Alya Sometimes Hides Her Feelings in Russian Season 2 / Roshidere 2 | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 8 | 178031 | Delicious in Dungeon Season 2 / Dungeon Meshi 2nd Season | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 9 | 209939 | Frieren Season 3 Golden Land Arc / Sousou no Frieren 3rd Season | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 10 | 186712 | Bocchi the Rock! 2nd Season / BTR2 | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 11 | 160275 | Made in Abyss: Mezameru Shinpi / Made in Abyss 3 | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 12 | 159042 | Reincarnated as a Sword Season 2 / Tensei Shitara Ken Deshita 2nd Season | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 13 | 143103 | Witch on the Holy Night / Mahoutsukai no Yoru / 魔法使いの夜 | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 14 | 213702 | Witch Hat Atelier Season 2 / Tongari Boushi no Atelier 2nd Season | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 15 | 195200 | Demon Slayer Infinity Castle Part 2 / Mugenjou-hen Movie 2 | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 16 | 189323 | Shangri-La Frontier Season 3 / 3rd Season | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 17 | 189123 | Blue Box Season 2 / Ao no Hako 2nd Season | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 18 | 177704 | MASHLE Season 3 / Sanma Taisou Shinkakusha Saishuu Shiken-hen | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 19 | 178467 | Tsukimichi Moonlit Fantasy Season 3 / Tsuki ga Michibiku Isekai Douchuu 3rd Season | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |
| 20 | 204429 | Chainsaw Man: Assassins Arc / Chainsaw Man: Shikaku-hen | wegen Cooldown nicht angefragt | – | `DACH_UNKNOWN` |

## Statistik

- Future-Titel für die Stichprobe ausgewählt: 20
- Sicher aus vorhandenem verifiziertem Cache gematcht: 1
- Erfolgreich neu live auf AniSearch gematcht: 0
- `DACH_CONFIRMED`: 1 (Cachebeleg)
- `DACH_NOT_LICENSED_YET`: 0
- `DACH_UNKNOWN`: 19 unter den aktuellen Zugriffsbedingungen
- HTTP 429: 1
- Weitere Live-Requests nach 429: 0
- Parserfehler: 0 (es lag keine erfolgreiche neue Detailantwort zum Parsen vor)

Eine spätere Wiederholung darf erst nach Ablauf des Cooldowns erfolgen. Erst bei erfolgreicher 3er-Stufe wird auf fünf und danach auf zwanzig Live-Titel erweitert.
