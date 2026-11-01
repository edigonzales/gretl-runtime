# Teststrategie und Backing-Services

Die verbindlichen Prüfungen werden aus dem Root mit Java 25 und dem Wrapper
aufgerufen:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Der normale Reactor enthält Unit-, Komponenten-, Distributions- und lokale
fachliche JAR-Jobtests (`*JobIT`). Das Dienstprofil ergänzt die vollständigen
`*IT` gegen echte Container; fehlendes Docker ist ein Fehler. Die getrennte
GeoTools-Probe nimmt keine GeoTools-JARs in die Standarddistribution auf.

| Prüfung | Nachweis |
| --- | --- |
| Neutrale API / Runner / DSL / Logging | Modul-Unit-Tests, externe FileSize-SPI, Distributions-IT |
| Eigenständig minimaler Validatorclasspath | ValidatorClasspathLocalJobIT, eigenes Taskartefakt und Maven-Laufzeitgraph |
| Ausgewählte Zusatzfunktion | FunctionLocalJobIT, tatsächlicher HTTP-Aufruf und konkrete negative Diagnosen |
| HTTP, Curl, ZIP/GZIP, XSLT | IoLocalJobIT, ValidatorLocalJobIT; unabhängige Request- und Dateiprüfung |
| CSV, JSON, GPKG, SHP-Validierung | ValidatorLocalJobIT mit allen ursprünglichen Fehlerdiagnosen |
| Excel, AV, DXF, GPKG-Konversion | FormatLocalJobIT; unabhängige XLSX-/DBF-/SHP-/Textleser |
| Lokale INTERLIS-Dateidatenbanken und NGK | InterlisLocalJobIT; JDBC, XTF-Reader, konkrete Validierungsdiagnose |
| DuckDB SQL, Parquet-/GPKG-Import und Export | DuckDbLocalJobIT, ursprüngliche Daten und SQL-Dateien |
| PostgreSQL/PostGIS, Db2Db, positive Föderation | DatabaseArtifactIT; JDBC sowie Cancellation/Rollback |
| Formatimport/-export mit PostgreSQL | FormatArtifactPostgresIT, ursprüngliche typisierte Werte, unabhängige Datei-/JDBC-Prüfung |
| Sieben ili2pg-Operationen, Schema-/Dataset-/Metakonfigurationsvarianten | InterlisArtifactIT mit ursprünglichen Modellen und Transfers |
| FTP und S3 | ArtifactServicesIT; unabhängige FTP-/AWS-Clients |
| Abbruch, SIGTERM und Abschlussreihenfolge | CancellationLocalJobIT und DatabaseArtifactIT |
| Ausgewählte vollständige Pakete, externe Extension, Hostverunreinigung und Lauf ohne Buildumgebung | SelectedPackageLocalJobIT, SelectedPackageArtifactIT und DistributionIT; [Auswahlvertrag](job-packaging.md) |
| IMAGE-Vorbereitung | ImageExecutionAdapterTest ohne echten GRETL-Image-Start |

Die älteren `PostgresSqlExecutorIT`, `Db2DbPostgresIT`,
`DuckDbFederationPostgresIT`, `FormatPostgresIT`, `FtpTaskFamilyIT` und
`S3TaskFamilyIT` bleiben zusätzliche CLI-Komponentenprüfungen. Sie zählen nicht
als separater JAR-Nachweis. `PackagedSqlJobPostgresIT` bleibt eine zusätzliche
ältere JAR-Regression.

Die Originalzuordnung mit unveränderten Quellhashes, Originalassertionen und
Dateiprüfsummen steht im [historischen Szenarioinventar](../history/provenance/original-scenarios.json).
Die aktuelle Zuordnung, alle Varianten und konkret begründete Anpassungen
stehen im [historischen Portnachweis](../history/provenance/scenario-mapping.json).
Die [Jobtest-Verträge](job-artifact-tests.md) erklären Artefaktbau, separate
Prozesse, Evidenz und die spätere ausdrückliche IMAGE-Konfiguration.

PostgreSQL/PostGIS: digestgepinntes `postgis/postgis:16-3.4`, pro Test isoliertes
Schema und `currentSchema=<Lease>,public`; Erweiterungen `postgis` und
`uuid-ossp` wie in den ursprünglichen Datenbankfixtures. Cleanup entfernt das
Schema einschliesslich abhängiger Objekte. FTP: digestgepinntes
`delfer/alpine-ftp-server`, dynamischer Control-Port, reservierter passiver
Port und direkt im Vordergrund laufender vsftpd-Master. Ein abgewiesener Login
darf den Server nicht beenden. Leases löschen ihr Remoteverzeichnis vollständig.
S3: Floci 1.10.0 mit eigener Source-/Target-Bucket pro Lease und vollständigem
Cleanup. Dessen Bibliothek verwendet `floci/floci:latest` ohne Digest-Override;
dies wird ausdrücklich nicht als gepinnt ausgewiesen. Die lokale S3-Emulation
prüft keine produktive AWS-Zugriffsberechtigung. Testzugänge sind synthetisch.

Die Tests laufen sequenziell pro Failsafe-JVM. Die Standarddistribution wird im
Reactor vor den Jobtests gebaut. Docker-Dienste bleiben auch bei zurückgestellter
GRETL-Image-Ausführung obligatorisch. IMAGE ist keine aktuelle CI-Pflichtmatrix.
Beide CI-Wege archivieren Reports, Prüfsummen, Konsolenlogs und Ergebnisse je
getestetem Commit; Secretquellen bleiben ausgeschlossen.

JaCoCo misst die Test-JVMs und aggregiert die aktiven Produktmodule. Nicht
instrumentierte Job-/Launcherprozesse werden separat fachlich geprüft und
liefern keine erfundene Coverage. Testanzahl und Codeabdeckung werden getrennt
in den SHA-gebundenen Gateberichten angegeben; siehe [Build](build.md).

Nach jedem CI-Gate läuft `scripts/audit-job-evidence.py --source <Commit-SHA>`;
der vollständige Lauf ergänzt `--full`. Der Audit prüft die vorhandenen
verifizierten Artefakte, ohne neue JARs zu bauen. Das separate manuelle
`image-qualification.yml` ist vorbereitet und wurde in diesem Auftrag nicht
ausgeführt. Die zusätzlichen Paketqualifikationen bleiben ausdrücklich JAR.

## Beispielquellen und Drift

Bei gemeinsamen Pfaden ist `examples/` die kanonische Quelle. Die Distribution
behält gezielte Kopien unter `gretl-dist/src/main/dist/examples/`, damit sie ohne
Checkout ausführbar ist. `ExampleConsistencyTest` prüft im Standard-Reaktor alle
16 bisherigen Paare auf Vorhandensein und alle gemeinsamen Dateien auf Bytegleichheit;
neu hinzugefügte gemeinsame Pfade werden ebenfalls verglichen. Eine absichtlich
beschädigte beziehungsweise fehlende Scriptkopie muss den Test scheitern lassen.
Ein zusätzlicher Kopiergenerator oder eine Umstellung des Distributionsbuilds ist
für diesen kleinen Bestand nicht erforderlich.

Die einzige Textausnahme ist `project-pipeline/README.md`: der Checkout erklärt
Build und Start über `./gretl-dist/target/dist/bin/gretl`, die ausgelieferte Fassung
beginnt beim entpackten Root mit `bin/gretl`. Beide beschreiben denselben Graphen;
die Job-, Settings-, Ressourcen- und Paketdateien sind bytegleich. Beim Beispiel
`download-extract/job.groovy` ist auch der optionale Overwrite-Kommentar in beiden
Kopien identisch. `cleanup_doc_checks.py` führt Download/Extract mit einem lokalen
HTTP-Testserver sowie die Projektpipeline jeweils aus Checkout und Distribution
aus, vergleicht alle Ausgabebytes und prüft erfolgreiche Reports. Damit beruht die
bewusste README-Trennung auf zwei tatsächlich ausgeführten Kontexten.
