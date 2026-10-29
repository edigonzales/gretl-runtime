# GRETL-Job-Subprojekte

Die [Spezifikation 1.1](gretl-runtime-subprojekte-spezifikation.md) beschreibt
einen gemeinsamen Taskgraphen aus mehreren Jobskripten. Die
[Phase-0-Baseline](phase-0-baseline.md) und ihr
[unabhängiger Review](phase-0-review.md) bilden den freigegebenen Ausgangspunkt.

Phase 1 ergänzt im vorhandenen Modul `gretl-groovy` das unveränderliche
Projektmodell, logische Namenspfade, eindeutige virtuelle Groovy-Quellnamen
und den Parser. Phase 2 ergänzt den gemeinsamen Script-Projektlauf und
die CLI-Einbindung. Das Projekt-Fat-JAR gehört zu Phase 3. Der jeweilige Stand wird erst durch den unabhängigen
Phasenreview freigegeben.

Eine `settings.gretl` enthält nur konstante, einfach oder doppelt zitierte
Stringargumente an `include` und `defaults`, jeweils höchstens eine Anweisung:

```groovy
include 'source', ':checks:local'
defaults ':checks:local:run'
```

`include 'a'` und `include ':a'` bezeichnen dasselbe Projekt. Segmente erfüllen
`[A-Za-z][A-Za-z0-9_-]*`; Schrägstriche, leere Segmente und Traversal werden
abgelehnt. `:a:b` verweist auf `a/b/job.groovy` und bindet keinen Elternjob
automatisch ein. Ein vorhandener Root-Job wird zuerst aufgenommen, danach
folgen die Includes in ihrer angegebenen Reihenfolge. Ohne Includes ist ein
Root-Job Pflicht. Ohne Settings-Defaults benötigt ein Projektaufruf ein explizites Ziel.
`defaults` in den einzelnen Jobskripten bestimmen den Projektlauf nicht.

Der Parser akzeptiert Kommentare und Leerzeilen, prüft UTF-8 und maximal
256 KiB **Bytes**, Symlinks, reale Root-Zugehörigkeit und reguläre Jobdateien.
Settings werden nur bis zum Groovy-AST konvertiert und per Allowlist gelesen;
es gibt keine Bytecodekompilation, Instanziierung oder Ausführung. Jobskripte
werden dabei nicht geladen. Methoden, Imports, Klassen, Annotationen,
Zuweisungen, Interpolation, Ausdrücke und Closures sind in Settings unzulässig.
Fehler nennen Code, Originalpfad, Zeile und verfügbare Spalte. Die Positionen
der Defaults bleiben für die Prüfung ihrer Tasknamen nach dem gemeinsamen Laden erhalten.

Dies ist keine allgemeine Sandbox für ausführbaren Jobcode oder dessen
Dateisystemzugriffe. Die Sicherheitsprüfung bezieht sich auf das deklarative
Projektformat und seine Definitionspfade.

Das [lokale Formatbeispiel](../../../examples/project-subprojects/README.md)
ist bereits mit dem bisherigen Einzeldateiaufruf je Skript ausführbar und wird
gegen Parser und unveränderten Einzeljob-Loader geprüft. Das neue
[Pipelinebeispiel](../../../examples/project-pipeline/README.md) läuft als ein
Projekt mit echten lokalen Operationen. Ein direkter Dateipfad startet weiterhin
den Einzeldateimodus, ein Verzeichnis erfordert eine `settings.gretl` unmittelbar
darin. Bestehende Schema-1/2-JARs verwenden denselben bisherigen Startpfad;
Schema-3-Projektpakete sind erst Gegenstand von Phase 3.

```sh
gretl run examples/project-pipeline --param :output=output.txt
gretl run examples/project-pipeline --target :checks:local:run --param :output=unused.txt
gretl run examples/project-pipeline --help
gretl check examples/project-pipeline
gretl plan examples/project-pipeline --graph mermaid
```

Im Projektmodus wird `action('run')` in `prepare/job.groovy` als `:prepare:run`
registriert. Lokale Task-, Parameter- und Cleanup-Namen erfüllen dieselbe
Segmentregel wie Include-Pfade. Ein unqualifizierter Verweis bleibt im eigenen
Projekt; `:prepare:run` ist ein absoluter Verweis. Root-Namen beginnen ebenfalls
mit `:`, etwa `:output`. CLI-Ziele und ausdrücklich übergebene Parameter müssen
vollständig qualifiziert sein. Parameterdatei und Runtimekonfig verwenden diese
Namen als JSON-Schlüssel. Die gemeinsame Priorität lautet CLI, Parameterdatei,
Runtimekonfig, Deklarationsdefault. Required und Typkonversion gelten für die
gesamte Definition; `check`, `plan` und Hilfe benötigen keine Pflichtwerte.
`config(alias)` und `secret(alias)` behalten globale Aliase.

Alle Skripte registrieren sequenziell Root zuerst und dann in Include-Reihenfolge
in denselben Builder. Sie teilen Compilerloader und Operation-Registry, erhalten
aber eigene Bindings und Scopes. Erst danach wird einmal eingefroren und mit dem
bestehenden Planner ein Graph geplant. `dependsOn` selektiert Vorgänger;
`after` ordnet nur bereits ausgewählte Tasks. `result` und `resultOptional`
benötigen weiterhin echte Datenabhängigkeiten, auch über Projektgrenzen hinweg.
Taskkonfiguration und Cleanup werden bei Hilfe und Planung nicht ausgeführt.
Ausführbarer Top-Level-Groovy-Code kann dabei wie im Einzeldateimodus laufen.

`file('sql/update.sql')` und `ctx.file(...)` beziehen sich auf das eigene
Projektverzeichnis, einschliesslich `when`, Action, innerem `ctx.run` und Cleanup.
Ein deklarierter `path`-Parameter bezieht sich auf das ursprüngliche Start-CWD;
absolute Pfade bleiben absolut. Temporäre Ausgaben gehören in `workPath(...)`.
Alle Tasks teilen Run-ID, ResultStore, Parameterauflösung, Workspace- und
Cleanup-Lebenszyklus sowie einen Runbericht mit qualifizierten Tasknamen.
Fehler nennen die ursprünglichen Jobdateien und Zeilen. Der Projektquellhash
umfasst Settings und eingebundene Jobskripte mit relativen Namen und Rohbytes;
Daten, Ressourcen und zusätzliche Hilfsklassen sind kein Bestandteil dieses
Quellhashs. Er ist kein Hash der gesamten Abhängigkeitswelt.

GRETL-Job-Subprojekte führen weder Gradle- noch Maven-Builds aus. Es gibt keine
verschachtelten Runs und keine neue `.gretl`-Endung für Jobskripte;
`settings.gretl` ist die Projektdatei, die Jobs bleiben `job.groovy`.
