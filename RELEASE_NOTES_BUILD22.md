# AniSentinel v0.25.16 – Build 22

## Änderungen

- Detektiv Conan vereint die bestätigten Crunchyroll- und Apple-TV-Inhalte auf einer Detailseite, ohne interne Katalog-IDs oder Prüfwerkzeuge in der normalen Oberfläche zu zeigen.
- Die beiden Crunchyroll-Kataloge bleiben intern unterscheidbar. Die aktuell laufende Staffel wird als „32 (Aktuell)“ angezeigt.
- Apple-TV-Staffeln und -Folgen werden aus der öffentlichen deutschen Apple-TV-Ansicht einschließlich der nachgeladenen Metadatenseiten übernommen. Die Nummerierung bleibt wie bei Apple; nicht gelistete Folgen werden nicht ergänzt. Ein Eintrag bedeutet nicht automatisch Apple-TV+-Abo-Verfügbarkeit.
- Die Anbieterverwaltung enthält jetzt eigene Schalter für AKIBA PASS und Apple TV.

## Validierung

- JVM-Tests und Debug-Build bestanden.
- APK mit `adb install -r` auf dem Testgerät aktualisiert; vorhandene App-Daten blieben erhalten.
- Die Anbieterverwaltung zeigte beide neuen Einträge. Der Nutzer bestätigte die Apple-TV-Folgenansicht auf dem Gerät.

Diese APK ist ein Debug-Build. Bei abweichender Signatur ist kein direktes Update möglich; vor einer bewusst ausgeführten Neuinstallation zuerst ein App-Backup erstellen.
