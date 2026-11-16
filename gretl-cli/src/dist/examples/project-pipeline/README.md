# Lokale Projektpipeline (Java 25)

Vom entpackten Distributionsroot:

```sh
bin/gretl run examples/project-pipeline --help
bin/gretl check examples/project-pipeline
bin/gretl plan examples/project-pipeline --graph mermaid
bin/gretl run examples/project-pipeline --param :output=output/script.txt
bin/gretl package examples/project-pipeline/job-package.json --output project-pipeline.jar
java -jar project-pipeline.jar --param :output=output/packaged.txt
cmp examples/project-pipeline/prepare/data/input.txt output/packaged.txt
```

Gzip und Extract arbeiten ausschliesslich mit synthetischen lokalen Daten.
Drei gleichnamige lokale Tasks bilden einen einzigen qualifizierten Graphen.
Das Schema-3-JAR enthält die kompilierten Skripte und die ausgewählte statische
Datei und läuft ohne die Distribution oder den Quellbaum. Der Ausgabeparameter
ist relativ zum ursprünglichen Start-CWD; file ist projektbezogen. Der gemeinsame
Run erzeugt einen Bericht und Ereignislog unter gretl-reports. Hilfe und Planung
führen keine Tasks oder Cleanup aus. Vertrauenswürdiger Script-Toplevel kann
bei der Definition laufen; beim Packen wird er nicht ausgeführt.
