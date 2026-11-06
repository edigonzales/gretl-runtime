# Repository-Anweisungen

Implementiere den ausdrücklich beauftragten Umfang. Prüfe zuerst Gitstatus,
aktuellen Commit, betroffene Implementierung und bestehende Tests. Bewahre
fremde Änderungen und arbeite auf einem eigenen Branch. Funktionale API-,
DSL-, Paket- und Fehlerverträge bleiben erhalten.

Die [Dokumentation](docs/README.md) beschreibt den aktuellen Stand. Wähle
lokale Tests nach dem betroffenen Verhalten und seinen Verbrauchern. Prüfe
Dokumentationslinks mit `python3 scripts/check-docs.py`; bei geänderten
Beispielen oder Renderpfaden führe die betroffenen Wege gemäss
[Buildanleitung](docs/development/build.md) aus.

Die [vollständige Qualifikation](docs/development/testing.md) verwendet
JDK 25 und Docker für das Dienstprofil. Die beiden Rootläufe lauten:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

CI und Release verwenden ihre vollständigen Modus- und Artefaktgates.
Ersetze keine geforderte Prüfung durch Teiltests, Skips oder schwächere
Assertions. Sichere Berichte vor einem weiteren `clean`; dokumentiere
Befehle, SHA und Ergebnisse. Push, Merge, Release und produktive Jobs
benötigen einen ausdrücklichen Auftrag.
