# Lokale Projektpipeline

Dieses ausführbare Scriptbeispiel benötigt die gebaute Standarddistribution und Java 25.
Es komprimiert lokale synthetische UTF-8-Daten mit `Gzip`, entpackt sie mit `Extract`
und kopiert das Ergebnis an einen expliziten Ausgabeort. Netzwerk oder Fachzugänge
sind nicht erforderlich. Die automatisierte Prozessprobe `ProjectScriptLocalJobIT`
verwendet genau diesen Bestand aus einem fremden CWD mit Leerzeichen im Pfad.

Vom Repository-Root:

```sh
./mvnw -B clean verify
./gretl-dist/target/dist/bin/gretl run examples/project-pipeline --help
./gretl-dist/target/dist/bin/gretl check examples/project-pipeline
./gretl-dist/target/dist/bin/gretl plan examples/project-pipeline --graph mermaid
./gretl-dist/target/dist/bin/gretl run examples/project-pipeline --param :output=target/project-output.txt
cmp examples/project-pipeline/prepare/data/input.txt target/project-output.txt
```

`settings.gretl` legt die Includes und das einzige Defaultziel fest. Alle drei
lokalen Tasks heissen `run`; ihre vollständigen Namen sind `:prepare:run`,
`:checks:local:run` und `:publish:run`. Nur `dependsOn` selektiert Vorgänger;
Cross-Project-Resultate werden erst beim Taskstart gelesen. Alle Tasks teilen
sich einen Run, dessen Ereignisse und Bericht unter `gretl-reports/` entstehen.
Beide lokalen Cleanups `release` werden im gemeinsamen Abschluss einmal ausgeführt.

```sh
./gretl-dist/target/dist/bin/gretl run examples/project-pipeline --target :checks:local:run --param :output=target/unused.txt
```

Dieser Aufruf führt nur Kompression und Prüfung aus, keine Publikation. Der
Pflichtparameter bleibt für die gesamte Definition erforderlich. Bei `help`,
`check` und `plan` darf er offen bleiben. Relative `path`-Parameter beziehen sich
auf das Start-CWD; `file('data/input.txt')` bezieht sich hier auf `prepare/`.
Temporäre Ausgaben liegen in den Taskworkspaces. Quellen werden als vertrauenswürdiger
Groovy-Code geladen; Top-Level-Code kann bereits bei Hilfe und Planung laufen.

Ein ausdrücklich übergebener Dateipfad startet weiterhin den Einzeldateimodus.

Das mitgelieferte `job-package.json` verwendet Schema 3 und wählt nur das
IO-Modul. Aus dem Repository-Root:

```sh
./gretl-dist/target/dist/bin/gretl package examples/project-pipeline/job-package.json --output target/project-pipeline.jar
java -jar target/project-pipeline.jar --help
java -jar target/project-pipeline.jar --plan
java -jar target/project-pipeline.jar --param :output=target/packaged-output.txt
cmp examples/project-pipeline/prepare/data/input.txt target/packaged-output.txt
```

Das JAR enthält kompilierte Einstiege aller Includes und die ausdrücklich
gewählte Ressource `prepare/data/input.txt`. Beim Packen laufen weder Settings
noch Script-Toplevel oder Tasks. Zur Ausführung braucht das JAR Java 25 und
seine Laufzeitparameter, aber weder Checkout noch GRETL, Maven oder Gradle.
Relative `path`-Parameter beziehen sich auch dort auf das Start-CWD. Statische
Ressourcen werden erst beim Run im eigenen Workspace materialisiert; `--help`,
`--version` und `--plan` erzeugen keinen Ressourcenbestand. Jobhilfe/Plan dürfen
weiterhin den vertrauenswürdigen Script-Toplevel zur Registrierung ausführen.
Schemen 1/2 bleiben der Paketweg für Einzeldateijobs; Schema 3 verlangt
`settingsFile: settings.gretl` und verbietet `entryScript`.
