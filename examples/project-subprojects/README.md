# Explizite Subprojekte und lokale Namen

`settings.gretl` bindet `:source` und `:checks:local` ein. Letzteres liegt unter
`checks/local/`; ein Elternjob unter `checks/` wird nicht automatisch eingebunden.
Beide Jobs besitzen einen lokalen Task `run` und eine eigene `input.txt`.

Vom Repository-Root nach dem Distributionsbuild:

```sh
gretl-dist/target/dist/bin/gretl check examples/project-subprojects
gretl-dist/target/dist/bin/gretl run examples/project-subprojects --target :source:run
gretl-dist/target/dist/bin/gretl run examples/project-subprojects --target :checks:local:run
```

Jeder Aufruf wählt ein Ziel im gemeinsamen Projektmodell. Ein ausdrücklich
übergebener Scriptdateipfad bleibt ein unabhängiger Einzeljob. Die Jobs lesen
lokale synthetische Daten. Das [Pipelinebeispiel](../project-pipeline/README.md)
zeigt Datenabhängigkeiten und ein ausführbares Projekt-JAR; die
[Subprojektanleitung](../../docs/implementation/project-subprojects/README.md)
erklärt Namens-, Pfad- und Paketverträge.
