# Aktuelle Dokumentation

Diese Seiten beschreiben den implementierten Stand von GRETL Runtime:

- [Einstieg und ausführbare Beispiele](../README.md)
- [Build, Distribution und Dokumentationswerkzeuge](implementation/build.md)
- [Teststrategie und Backing-Services](implementation/testing.md)
- [Module und Abhängigkeitsgrenzen](implementation/module-boundaries.md)
- [Jobpaketierung und Schemas 1/2/3](implementation/job-packaging.md)
- [Subprojekte und gemeinsamer Taskgraph](implementation/project-subprojects/README.md)
- [SCRIPT-, JAR- und reale IMAGE-Fachtests](implementation/job-artifact-tests.md)
- [Generisches Runtime-Image und OpenShift](../gretl-dist/src/main/docker/README.md)
- [GRETL-Texteditor und interaktiver Graph für VS Code](../vscode/gretl-vscode/README.md)
- [Lokalisierung](architecture/runtime-contracts.md#lokalisierung) und [Plangraphen](architecture/runtime-contracts.md#plangraphen)
- [Validatorfunktionen](implementation/job-packaging.md#validatorfunktionen)
- [Taskreferenz](reference/reference.adoc) und [deren Generierung](reference/README.md)
- [Snapshot-Veröffentlichung und Pflichtgates](implementation/publish-snapshots.md)

## Technische Verträge und historische Quellen

- [Implementierte API-, DSL-, Runner-, Logging- und Sicherheitsverträge](architecture/runtime-contracts.md)
- [Publisher, XtfToGpkg und Native Image: nicht gelieferter Backlog](backlog/publisher-and-native.md)
- [Kuratiertes historisches Wissen, Herkunft und Abschluss-SHAs](history/README.md)

Die frühere Soll-Spezifikation und abgeschlossene Agentenpläne sind aus dem
aktiven Baum entfernt. Die [Cleanup-Spezifikation](maintenance/cleanup/spec-v2.md)
und das [Verfahren](maintenance/cleanup/README.md) dokumentieren Auswahl und
Nachweise. Für neue Arbeiten gelten die [Repository-Regeln](../AGENTS.md).
