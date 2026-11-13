# Repository-Anweisungen

Implementiere den ausdrücklich beauftragten Umfang. Prüfe zuerst Gitstatus,
aktuellen Commit, betroffene Implementierung und bestehende Tests. Bewahre
fremde Änderungen und arbeite auf einem eigenen Branch. Funktionale API-,
DSL-, Paket- und Fehlerverträge bleiben erhalten.

Die [Dokumentation](docs/README.md) beschreibt den aktuellen Stand. Wähle
lokale Tests nach dem betroffenen Verhalten und seinen Verbrauchern. Prüfe
Dokumentationslinks mit `python3.12 scripts/check-docs.py`; bei geänderten
Beispielen oder Renderpfaden führe die betroffenen Wege gemäss
[Buildanleitung](docs/development/build.md) aus.

Die [vollständige Qualifikation](docs/development/testing.md) verwendet JDK 25,
Node 22, Docker und Python 3.12. Starte den folgenden Befehl im Repository-Root.

```sh
python3.12 scripts/verify-runtime.py
```

Ein Reaktorlauf prüft die SCRIPT-Fachsuite mit 50 Standardoperationen und
echten Diensten sowie gezielte eigenständige JAR- und IMAGE-Prozesse. IMAGE
startet externe Groovy-Jobs mit read-only Quellen. Ersetze Pflichtprüfungen
nicht durch Teiltests, Skips oder schwächere Assertions. Sichere notwendige
Berichte vor einem weiteren `clean` und dokumentiere Befehl, SHA und Ergebnis.

Die CI sichert die qualifizierte ZIP- und TAR-Distribution mit SHA-256 und
prüft nach dem Snapshot-Deploy auf `main` dieselben Archivbytes per HTTP.
Push, Merge, Release und produktive Jobs benötigen einen ausdrücklichen
Auftrag. Das generische Image wird lokal gebaut und geprüft.
