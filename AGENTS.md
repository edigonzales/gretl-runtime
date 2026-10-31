# Repository-Anweisungen

Implementiere ausschliesslich den ausdrücklich beauftragten Umfang. Prüfe zuerst
Gitstatus, aktuellen Commit, betroffene Implementierung und bestehende Tests.
Bewahre fremde Änderungen und verwende einen eigenen Arbeitsbranch.

Der Einstieg in die geltenden Anleitungen ist [docs/README.md](docs/README.md).
Abgeschlossene Spezifikationen, Agentenprompts und Meilensteinberichte sind
historische Quellen; sie sind keine neuen Implementierungsaufträge.
Funktionale API-, DSL-, Paket- und Fehlerverträge bleiben erhalten.

Führe mit JDK 25 beide vollständigen Gates aus:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Docker ist für das vollständige Dienstprofil erforderlich. Keine stillen
Testskips, keine abgeschwächten Assertions und kein Ersatz durch Teiltests.
Sichere Berichte vor einem weiteren `clean`; dokumentiere Befehle, SHA und
Ergebnisse. Dokumentationsänderungen benötigen passende Link-, Beispiel- und
Renderprüfungen gemäss [Buildanleitung](docs/implementation/build.md).

Für den laufenden Cleanupauftrag gilt zusätzlich unverändert
[Cleanup-Spezifikation 1.2](docs/maintenance/cleanup/spec-v2.md) mit den
unabhängigen OpenCode/DeepSeek-Gates. Keine automatische Veröffentlichung:
Push, Merge, Release und Ausführung produktiver Jobs benötigen einen Auftrag.
