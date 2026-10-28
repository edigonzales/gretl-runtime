# Projektformat – Beispiel für Phase 1

Die beiden expliziten Includes in `settings.gretl` werden als `:source` und
`:checks:local` gelesen. Das zweite liegt unter `checks/local/`; ein
`checks/job.groovy` wird dafür nicht benötigt. Beide Skripte besitzen bewusst
den lokalen Tasknamen `run` und eine eigene Datei `input.txt`.

Phase 1 liefert das Projektmodell und den deklarativen Parser. Der gemeinsame
Projektlauf folgt in Phase 2; die vorhandene CLI akzeptiert weiterhin einen
Skriptdateipfad. Das Beispiel ist deshalb derzeit über die beiden bisherigen
Einzeldateiaufrufe ausführbar, aus dem Repository-Root mit einer gebauten
Distribution:

```sh
gretl-dist/target/dist/bin/gretl run examples/project-subprojects/source/job.groovy
gretl-dist/target/dist/bin/gretl run examples/project-subprojects/checks/local/job.groovy
```

Diese zwei Aufrufe sind zwei unabhängige Einzeljobs. Sie lesen ausschliesslich
lokale synthetische Daten und melden deren unterschiedlichen Inhalt. Sie
qualifizieren noch keinen gemeinsamen Projektgraphen oder ein Projekt-JAR.

Format und Phasengrenzen stehen unter
[`docs/implementation/project-subprojects/`](../../docs/implementation/project-subprojects/README.md).
