# GRETL Runtime

GRETL Runtime führt Groovy-Jobs mit einem sequenziellen Taskgraphen auf Java 25 aus.
Der aktuelle Entwicklungsstand ist `6.0.0-SNAPSHOT`. Die Standarddistribution
enthält 51 Operationen für HTTP, FTP, SFTP, S3, Archive, SQL, DuckDB, INTERLIS und
Dateiformate. Eigene Actions und Java-Operationen verwenden denselben Runner.
SCRIPT ist der Hauptweg; ein Job kann zusätzlich als vorkompiliertes Job-JAR
oder als externes Script im generischen Runtime-Image laufen.


Der [Migrationsleitfaden](docs/guides/gradle-migration.md) zeigt die Übertragung
von Gradle-Jobs mit SQL, Archiven, SFTP und Groovy-Taskfamilien.

## Aus dem Git-Checkout starten

Mit JDK 25 baut der Gradle-Wrapper die Standarddistribution.

```sh
./gradlew installDist
./gradlew test
```

Die Installation liegt unter `gretl-cli/build/install/gretl-runtime/`.
Die vollständige Qualifikation mit Docker und Node 22 startet über
`python3.12 scripts/verify-runtime.py`.
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

<!-- checked-example: checkout -->
```sh
gretl_demo_dir="$(mktemp -d)"
printf '{"schemaVersion":1,"runtime":{"reportRoot":"%s"}}\n' "$gretl_demo_dir/reports" > "$gretl_demo_dir/runtime.json"
./gretl-cli/build/install/gretl-runtime/bin/gretl run gretl-dist/src/main/dist/examples/minimal-action/job.groovy --param output="$gretl_demo_dir/message.txt" --config "$gretl_demo_dir/runtime.json" --work-dir "$gretl_demo_dir/work" --report "$gretl_demo_dir/script-report.json"
cmp gretl-dist/src/main/dist/examples/minimal-action/message.txt "$gretl_demo_dir/message.txt"
```

`file('message.txt')` ist relativ zum Jobverzeichnis. Ein `path`-Parameter
bezieht sich auf das ursprüngliche Startverzeichnis; hier ist er absolut.

## Entpackte Scriptdistribution

Die folgenden Befehle gelten **im Distributionsroot**, der `bin/`, `lib/` und
`examples/` enthält. Im Checkout ist dies nach dem Build auch
`gretl-cli/build/install/gretl-runtime/`.

<!-- checked-example: distribution -->
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

Auch diese Paketierbefehle gelten im Distributionsroot. Jedes Job-JAR enthält die vollständige Standardlaufzeit.

<!-- checked-example: jar -->
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
[Paketierungsvertrag](docs/guides/packaging.md).

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


Lokale [Scriptfragmente mit `apply from:`](docs/guides/script-includes.md)
teilen den bestehenden Job-/Subprojektscope und funktionieren auch im
vorkompilierten Job-JAR. Das [lokale Beispiel](examples/script-includes/README.md)
prüft den vollständigen Inhalt nach Gzip-Dekompression. Der Editor analysiert statische Includes und kann eindeutige Quellen
dateiübergreifend bearbeiten. Gemeinsam genutzte Fragmente bleiben dabei
schreibgeschützt.

[Taskgebundene Finalizer](docs/guides/task-finalizers.md) ergänzen
den normalen Taskgraphen um bedingte Abschlussarbeit. Das lokale
[ausführbare Beispiel](examples/task-finalizers/README.md) zeigt Erfolg,
Ownerfehler, gemeinsamen Finalizer und globales Cleanup.

Die [Lizenzhinweise](LICENSES/) enthalten die MIT-Texte für übernommene
GRETL-Quellen und Testfixtures sowie die [Quellenangaben](LICENSES/source-adoption.json).
