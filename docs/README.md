# GRETL Runtime

Der [Einstieg im Repository](../README.md) zeigt einen ausführbaren Job und
seine Paketierung. Die [Taskreferenz](reference/reference.adoc) beschreibt
verfügbare Operationen und ihre Felder.

## Jobs schreiben und betreiben

- [Projekte und Subprojekte](guides/projects.md)
- [Gradle-Jobs migrieren](guides/gradle-migration.md)
- [Scriptfragmente mit `apply from:`](guides/script-includes.md)
- [Taskgebundene Finalizer und globales Cleanup](guides/task-finalizers.md)
- [Job-JARs, Paketformate und Validatorfunktionen](guides/packaging.md)
- [Konfiguration, Pfade, Secrets und Runberichte](guides/runtime-configuration.md)
- [Generisches Runtime-Image und OpenShift](../gretl-dist/src/main/docker/README.md)
- [Texteditor und interaktiver Graph für VS Code](../vscode/gretl-vscode/README.md)

## Entwickeln und prüfen

- [Runtimeverträge](architecture/runtime-contracts.md), [Module](architecture/module-boundaries.md) und [statisches Tooling](architecture/tooling.md)
- [Build, Distribution und Dokumentationsprüfung](development/build.md)
- [Teststrategie und Backing-Services](development/testing.md)
- [Taskreferenz generieren und rendern](reference/README.md)
- [Artefakte prüfen und veröffentlichen](development/publication.md)
