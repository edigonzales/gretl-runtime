# GRETL Runtime

GRETL Runtime führt Groovy-Jobs mit einem sequenziellen Taskgraphen auf Java 25 aus.
Der aktuelle Entwicklungsstand ist `6.0.0-SNAPSHOT`. Die Standarddistribution
enthält 44 Operationen für HTTP, FTP, S3, Archive, SQL, DuckDB, INTERLIS und
Dateiformate. Eigene Actions und Java-Operationen verwenden denselben Runner.
SCRIPT ist der Hauptweg; ein Job kann zusätzlich als vorkompiliertes Job-JAR
oder als externes Script im generischen Runtime-Image laufen.

## Aus dem Git-Checkout starten

JDK 25 und der Maven-Wrapper werden für den Build benötigt. Docker wird für
das vollständige Dienstprofil benötigt. Vom Repository-Root:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Der Standardweg benötigt kein Docker und startet lokale Fachjobs als
SCRIPT-Prozesse. Die vollständige Qualifikation aller Auslieferungswege
läuft am sauberen Commit mit:

```sh
scripts/verify-job-matrix.sh --mode ALL --scope full --output ../gretl-all-full
```

Jeder Modus besitzt eigene Fach-/Operationsnachweise. Das
[generische Image](gretl-dist/src/main/docker/README.md) führt externe
Groovy-Dateien oder Projekte aus und unterstützt die OpenShift-UID-/
Volume-Bedingungen; konkrete Jobs werden nicht ins Image eingebaut.

Der Build erzeugt `gretl-dist/target/dist/` und ZIP-/TAR-Distributionen.
Das mitgelieferte Minimalbeispiel kopiert eine synthetische Textdatei:

```groovy
parameter('output', type: 'path', required: true, description: 'Output text file')
action('write') {
    def target = parameter('output')
    java.nio.file.Files.createDirectories(target.parent)
    java.nio.file.Files.copy(file('message.txt'), target)
}
defaults 'write'
```

<!-- cleanup-example: checkout -->
```sh
gretl_demo_dir="$(mktemp -d)"
printf '{"schemaVersion":1,"runtime":{"reportRoot":"%s"}}\n' "$gretl_demo_dir/reports" > "$gretl_demo_dir/runtime.json"
./gretl-dist/target/dist/bin/gretl run gretl-dist/src/main/dist/examples/minimal-action/job.groovy --param output="$gretl_demo_dir/message.txt" --config "$gretl_demo_dir/runtime.json" --work-dir "$gretl_demo_dir/work" --report "$gretl_demo_dir/script-report.json"
cmp gretl-dist/src/main/dist/examples/minimal-action/message.txt "$gretl_demo_dir/message.txt"
```

`file('message.txt')` ist relativ zum Jobverzeichnis. Ein `path`-Parameter
bezieht sich auf das ursprüngliche Startverzeichnis; hier ist er absolut.

## Entpackte Scriptdistribution

Die folgenden Befehle gelten **im Distributionsroot**, der `bin/`, `lib/` und
`examples/` enthält. Im Checkout ist dies nach dem Build auch
`gretl-dist/target/dist/`.

<!-- cleanup-example: distribution -->
```sh
gretl_demo_dir="$(mktemp -d)"
printf '{"schemaVersion":1,"runtime":{"reportRoot":"%s"}}\n' "$gretl_demo_dir/reports" > "$gretl_demo_dir/runtime.json"
./bin/gretl run examples/minimal-action/job.groovy --param output="$gretl_demo_dir/message.txt" --config "$gretl_demo_dir/runtime.json" --work-dir "$gretl_demo_dir/work" --report "$gretl_demo_dir/script-report.json"
cmp examples/minimal-action/message.txt "$gretl_demo_dir/message.txt"
```

Die Scriptdistribution benötigt zur Ausführung Java 25. Ein expliziter
Scriptdateipfad startet einen Einzeljob. Ein Projektverzeichnis benötigt eine
`settings.gretl`; seine Tasks und Parameter haben qualifizierte Namen.

## Job-JAR bauen und starten

Auch diese Paketierbefehle gelten im Distributionsroot. Das Minimalpaket wählt
mit Deskriptorschema 2 keine Standardtaskmodule aus.

<!-- cleanup-example: jar -->
```sh
gretl_demo_dir="$(mktemp -d)"
printf '{"schemaVersion":1,"runtime":{"reportRoot":"%s"}}\n' "$gretl_demo_dir/reports" > "$gretl_demo_dir/runtime.json"
./bin/gretl package examples/minimal-action/job-package.json --output "$gretl_demo_dir/job.jar"
java -jar "$gretl_demo_dir/job.jar" --param output="$gretl_demo_dir/jar-message.txt" --config "$gretl_demo_dir/runtime.json" --work-dir "$gretl_demo_dir/work" --report "$gretl_demo_dir/jar-report.json"
cmp examples/minimal-action/message.txt "$gretl_demo_dir/jar-message.txt"
```

Das fertige JAR benötigt Java 25 und seine Laufzeitwerte. Paketieren führt
keine Jobtasks aus. Schemas 1 und 2 bleiben für Einzeljobs unterstützt;
Schema 3 bündelt Projekte mit `settings.gretl`. Details stehen im
[Paketierungsvertrag](docs/implementation/job-packaging.md).

## Dokumentation und Grenzen

Die [aktuelle Dokumentation](docs/README.md) führt zu Build, Tests, Modulen,
Projekten und der [Taskreferenz](docs/reference/reference.adoc).
Die [lokale Projektpipeline](examples/project-pipeline/README.md) zeigt einen
gemeinsamen Taskgraphen und ein Schema-3-JAR ohne externe Dienste.

Publisher, XtfToGpkg und Native Image sind nicht geliefert. Die Runtime enthält
keinen Scheduler und keine Control Plane. Ausführbare Groovy-Jobs sind
vertrauenswürdiger Anwendungscode; Top-Level-Code kann beim Definitionsladen
für Hilfe, Check und Planung laufen. Plattform- und Releasefreigaben brauchen
jeweils eigene, an einen Commit gebundene Nachweise.


Lokale [Scriptfragmente mit `apply from:`](docs/implementation/script-includes/README.md)
teilen den bestehenden Job-/Subprojektscope und funktionieren auch im
vorkompilierten Job-JAR. Das [lokale Beispiel](examples/script-includes/README.md)
prüft den vollständigen Inhalt nach Gzip-Dekompression. Die IDE-Sicht bleibt
partiell; vollständige Includeauflösung und dateiübergreifendes Rename sind
nicht zugesichert.

[Taskgebundene Finalizer](docs/implementation/task-finalizers/README.md) ergänzen
den normalen Taskgraphen um bedingte Abschlussarbeit. Das lokale
[ausführbare Beispiel](examples/task-finalizers/README.md) zeigt Erfolg,
Ownerfehler, gemeinsamen Finalizer und globales Cleanup.
