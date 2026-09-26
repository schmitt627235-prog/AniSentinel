# AniSentinel v0.25.16 ? Build 22

## ?nderungen

- Detektiv Conan vereint die best?tigten Crunchyroll- und Apple-TV-Inhalte auf einer Detailseite, ohne interne Katalog-IDs oder Pr?fwerkzeuge in der normalen Oberfl?che zu zeigen.
- Die beiden Crunchyroll-Kataloge bleiben intern unterscheidbar. Die aktuell laufende Staffel wird als ?32 (Aktuell)? angezeigt.
- Apple-TV-Staffeln und -Folgen werden aus der ?ffentlichen deutschen Apple-TV-Ansicht einschlie?lich der nachgeladenen Metadatenseiten ?bernommen. Die Nummerierung bleibt wie bei Apple; nicht gelistete Folgen werden nicht erg?nzt. Ein Eintrag bedeutet nicht automatisch Apple-TV+-Abo-Verf?gbarkeit.
- Die Anbieterverwaltung enth?lt jetzt eigene Schalter f?r AKIBA PASS und Apple TV.

## Validierung

- JVM-Tests und Debug-Build bestanden.
- APK mit `adb install -r` auf dem Testger?t aktualisiert; vorhandene App-Daten blieben erhalten.
- Die Anbieterverwaltung zeigte beide neuen Eintr?ge. Der Nutzer best?tigte die Apple-TV-Folgenansicht auf dem Ger?t.

Diese APK ist ein Debug-Build. Bei abweichender Signatur ist kein direktes Update m?glich; vor einer bewusst ausgef?hrten Neuinstallation zuerst ein App-Backup erstellen.
