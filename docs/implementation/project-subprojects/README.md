# GRETL-Job-Subprojekte

Die [Spezifikation 1.1](gretl-runtime-subprojekte-spezifikation.md) beschreibt
einen später gemeinsamen Taskgraphen aus mehreren Jobskripten. Die
[Phase-0-Baseline](phase-0-baseline.md) und ihr
[unabhängiger Review](phase-0-review.md) bilden den freigegebenen Ausgangspunkt.

Phase 1 ergänzt im vorhandenen Modul `gretl-groovy` das unveränderliche
Projektmodell, logische Namenspfade, eindeutige virtuelle Groovy-Quellnamen
und den Parser. Die CLI- und Runner-Einbindung gehört zu Phase 2, das
Projekt-Fat-JAR zu Phase 3. Der jeweilige Stand wird erst durch den unabhängigen
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
Root-Job Pflicht. Ohne Settings-Defaults wird ein späterer Projektaufruf ein
explizites Ziel benötigen.

Der Parser akzeptiert Kommentare und Leerzeilen, prüft UTF-8 und maximal
256 KiB **Bytes**, Symlinks, reale Root-Zugehörigkeit und reguläre Jobdateien.
Settings werden nur bis zum Groovy-AST konvertiert und per Allowlist gelesen;
es gibt keine Bytecodekompilation, Instanziierung oder Ausführung. Jobskripte
werden dabei nicht geladen. Methoden, Imports, Klassen, Annotationen,
Zuweisungen, Interpolation, Ausdrücke und Closures sind in Settings unzulässig.
Fehler nennen Code, Originalpfad, Zeile und verfügbare Spalte. Die Positionen
der Defaults bleiben für die spätere Prüfung ihrer Tasknamen erhalten.

Dies ist keine allgemeine Sandbox für ausführbaren Jobcode oder dessen
Dateisystemzugriffe. Die Sicherheitsprüfung bezieht sich auf das deklarative
Projektformat und seine Definitionspfade.

Das [lokale Formatbeispiel](../../../examples/project-subprojects/README.md)
ist bereits mit dem bisherigen Einzeldateiaufruf je Skript ausführbar und wird
gegen Parser und unveränderten Einzeljob-Loader geprüft. `run|check|plan`
mit einem Projektverzeichnis, qualifizierte Taskregistrierung und
Schema-3-Projektpakete sind in Phase 1 noch nicht verfügbar. Die bestehenden
Einzeldatei- und Schema-1/2-JAR-Wege bleiben erhalten.

GRETL-Job-Subprojekte führen weder Gradle- noch Maven-Builds aus. Es gibt keine
verschachtelten Runs und keine neue `.gretl`-Endung für Jobskripte;
`settings.gretl` ist die Projektdatei, die Jobs bleiben `job.groovy`.
