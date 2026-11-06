# Includejobs im Texteditor und Modeler

Mit Java 25 und der gebauten Distribution vom Repositoryroot:

```sh
include_demo_dir="$(mktemp -d)"
python3 -c 'import json,sys; print(json.dumps({"schemaVersion":1,"runtime":{"reportRoot":sys.argv[1]}}))' "$include_demo_dir/reports" > "$include_demo_dir/runtime.json"
./gretl-dist/target/dist/bin/gretl plan examples/include-tooling/job.groovy
./gretl-dist/target/dist/bin/gretl run examples/include-tooling/job.groovy --work-dir "$include_demo_dir/work" --keep-work always --config "$include_demo_dir/runtime.json" --report "$include_demo_dir/report.json"
python3 examples/include-tooling/verify.py "$include_demo_dir/work"
```

`prepare` komprimiert die lokale UTF-8-Datei; `finish` komprimiert dieses Ergebnis
nochmals. Die Prüfung dekomprimiert zweimal und vergleicht jedes Originalbyte.
`dependsOn` gibt das Resultatrecht; die Includeordnung ersetzt keine Datenkante.
Die Ausgabe liegt im behaltenen Run-Arbeitsverzeichnis und wird nicht produktiv
veröffentlicht. Das Beispiel benötigt weder Netzwerk noch Dienste.

Im installierten GRETL-VSIX `job.groovy` öffnen und **GRETL: Open Modeler** starten.
Der statische Graph ist vollständig und enthält beide Tasks mit ihren tatsächlichen
Fragmentdateien. Include-Literale und Namensreferenzen unterstützen Definition,
Referenzen, Hover und Completion. **Reveal selection** öffnet die Originalquelle
mit dem Besitzerkontext. Die IDE startet keinen Job und speichert nichts automatisch.

**Rename declaration**, native Rename und **Configure task** arbeiten auf den
Originaldateien. Wenn der Modeler weitere Zielquellen anfordert, diese öffnen und
den Entwurf gegen den aktualisierten Graphen erneut bestätigen. Ein nativer
Mehrdateien-Edit verwendet einen WorkspaceEdit. **Undo text**/**Redo text** bedienen
dessen tatsächliche Quelldatei; **Save changed sources** speichert die geänderten
Dateien des letzten Modeleredits. Layout-Undo bleibt getrennt.

Dasselbe physische Fragment darf in zwei Besitzerkontexten gelesen werden.
**GRETL: Select Source Context** wählt die Texteditoransicht; ein Modelerpanel hält
seine eigene Auswahl mit Scope und Entry. Shared-/unbekannte Write-Footprints
werden abgelehnt, auch wenn nur ein zu ändernder Use in einer Shareddatei liegt.
Unabhängige eindeutige Edits bleiben möglich. Normale Groovy-/Gradledateien erhalten
keine globale GRETL-Sprachassoziation. Dirty-Fragmente werden ohne Speichern
synchronisiert; ein noch nicht zugeordnetes Fragment wird kein erratener Rootjob.
**GRETL: Use Active File as Runtime Job** registriert diese andere Rolle ausdrücklich.

Details und bewusste Grenzen stehen in der
[Featureanleitung](../../docs/guides/script-includes.md) und im
[Tooling-Architektur](../../docs/architecture/tooling.md).
