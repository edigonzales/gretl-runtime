# Taskgebundener Abschluss ohne externe Dienste

Voraussetzung: Java 25 und eine gebaute GRETL-Distribution. Vom Repositoryroot
oder vom entpackten Distributionsroot:

```sh
GRETL="$(pwd)/bin/gretl"
if [ ! -x "$GRETL" ]; then
  GRETL="$(pwd)/gretl-dist/target/dist/bin/gretl"
fi
WORK="$(mktemp -d)"
"$GRETL" check examples/task-finalizers/job.groovy
"$GRETL" plan examples/task-finalizers/job.groovy
"$GRETL" run examples/task-finalizers/job.groovy --param output="$WORK/success" --report "$WORK/success.json" --work-dir "$WORK/success-work"
gzip -dc "$WORK/success/finalized.gz" | cmp examples/task-finalizers/input.txt -
test -f "$WORK/success/record.txt"
test -f "$WORK/success/cleanup.txt"
```

`importData` und `secondOwner` teilen `release`. Nach beiden Ownern läuft dessen
Voraussetzung `prepareFinalizer`, dann `release` einmal und danach `record`.
Die Liste `['release', 'record']` erzeugt selbst keine Reihenfolge: `record`
verwendet ausdrücklich `after: 'release'`. `prepareFinalizer` prüft, dass die
Ownerressourcen bereits geschlossen sind. Globale Cleanuparbeit folgt zuletzt.

Der Fehlerfall erwartet ausdrücklich Exitcode **10**, obwohl der taskgebundene
Abschluss und das globale Cleanup erfolgreich sind:

```sh
set +e
"$GRETL" run examples/task-finalizers/job.groovy --param output="$WORK/failure" --param mode=fail --report "$WORK/failure.json" --work-dir "$WORK/failure-work"
CODE=$?
set -e
test "$CODE" -eq 10
gzip -dc "$WORK/failure/finalized.gz" | cmp examples/task-finalizers/input.txt -
test -f "$WORK/failure/record.txt"
test -f "$WORK/failure/cleanup.txt"
```

Der Run bleibt FAILED mit `synthetic-owner-failure` als Primärursache.
`secondOwner` bleibt NOT_RUN und aktiviert nichts; der von `importData`
aktivierte gemeinsame `release` läuft trotzdem. `trace.txt` zeigt im Erfolgsfall
`importData, secondOwner, prepareFinalizer, release, record, cleanup`, im Fehlerfall
dieselbe Folge ohne `secondOwner`. Die JSON-Berichte unterscheiden deklarierte
von aktivierenden Ownern und die tatsächlichen Ausführungsrollen.

Für ein eigenständiges vorkompiliertes JAR, aus demselben Startverzeichnis:

```sh
"$GRETL" package examples/task-finalizers/job-package.json --output "$WORK/job.jar"
java -jar "$WORK/job.jar" --param output="$WORK/jar" --report "$WORK/jar.json" --work-dir "$WORK/jar-work"
gzip -dc "$WORK/jar/finalized.gz" | cmp examples/task-finalizers/input.txt -
```

Das JAR enthält den lokalen Includeabschluss als Klassen und benötigt beim
Start weder den Quellbaum noch Maven. `mode=wait` dient ausschliesslich einer
kontrollierten kooperativen Abbruchprobe: erst auf die Datei `started` warten,
dann dem eigenen Testprozess SIGTERM senden. Finalizer und Cleanup teilen bei
Abbruch oder `finishJob` das begrenzte Abschlussbudget. SIGKILL oder ein JVM-Absturz
sind keine Finalizergarantie.

`finalizedBy` gibt kein Ergebnisrecht. Ein ausdrücklich gesetztes
`dependsOn: 'importData'` würde nach einem Ownerfehler auch den Finalizer
blockieren. Ein direktes Ziel `release` benötigt keinen Owner und ist ein normaler
Task. Globale `cleanup`-Aktionen bleiben für den gesamten Run zuständig.

Die IDE bietet eine eigene beschriftete Kante und den Ownerdialog an. Shared-
Quellen bleiben ohne exklusiven Schreibfootprint schreibgeschützt; die Wahl eines
Lesekontexts hebt das nicht auf. Den gespeicherten Job separat mit der Runtime
ausführen. Die ausführliche Featureanleitung steht im zugehörigen Quellcheckout
unter [Finalizer-Guide](https://github.com/edigonzales/gretl-runtime/blob/main/docs/guides/task-finalizers.md).
