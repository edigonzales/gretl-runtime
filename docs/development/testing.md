# Teststrategie und Backing-Services

Wähle für eine lokale Änderung Tests der betroffenen Module, Verträge und
Verbraucher. Prüfe bei selektierten Läufen, dass die erwarteten Suites und
Fälle tatsächlich entdeckt werden. Ein Teiltest ersetzt keine geforderte
vollständige Qualifikation. Dafür gelten vom Repository-Root mit JDK 25:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Der folgende Befehl startet die konsolidierte Prüfung mit JDK 25, Node 22, Docker und Python 3.12.

```sh
python3.12 scripts/verify-runtime.py
```

Das Skript legt ein eigenes Docker-Netz an und startet genau einen
`./mvnw -B -Pgeotools-probe,backing-services,job-image clean verify`-Lauf.
Der Reactor prüft die vollständige SCRIPT-Fachsuite mit 50
Standardoperationen, explizite selbständige JAR-Prozesse und ausgewählte
IMAGE-Prozesse mit externen Jobs. Das Image verwendet dasselbe Testnetz
wie PostgreSQL/PostGIS und S3. Der Aufruf benötigt einen non-root
Hostbenutzer für die privaten Bindmounts. Bei SIGINT oder SIGTERM beendet das
Skript seine Maven-Prozessgruppe mit Fristen für TERM und KILL. Es entfernt
die anhand der Laufkennung zugeordneten Docker-Ressourcen und das private
IMAGE-Arbeitsverzeichnis.

Der normale Reactor enthält Unit-, Komponenten-, Distributions- und lokale
fachliche SCRIPT-Prozesse (`*JobIT`) plus ausdrücklich erhaltene JAR-Paketverträge. Das Dienstprofil ergänzt die vollständigen
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
| Ausgewählte vollständige Pakete, externe Extension, Hostverunreinigung und Lauf ohne Buildumgebung | SelectedPackageLocalJobIT, SelectedPackageArtifactIT und DistributionIT; [Auswahlvertrag](../guides/packaging.md) |
| Reale generische IMAGE-SCRIPT-Läufe und OpenShift-UID | ImageRuntimeIT mit echten externen Jobs, Native-Libraries, PostGIS, HTTP und S3 |

Die Suites `PostgresSqlExecutorIT`, `Db2DbPostgresIT`,
`DuckDbFederationPostgresIT`, `FormatPostgresIT`, `FtpTaskFamilyIT` und
`S3TaskFamilyIT` bleiben zusätzliche CLI-Komponentenprüfungen. Sie zählen nicht
als separater Produktprozess- oder Modusnachweis. `PackagedSqlJobPostgresIT` prüft zusätzlich den paketierten SQL-Job.

Die [Jobtest-Verträge](job-artifact-tests.md) erklären die drei
Prozessarten, die Operationsabdeckung und das Testnetz.

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

Auf macOS kann die FTP-Verbindung mit Docker Desktop IPv4 benötigen. Bei
diesem Verbindungsproblem kann der Lauf mit
`JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true` wiederholt werden.
Dokumentiere die Einstellung. Assertions und Dienstprofil bleiben gleich.

Die Tests laufen in einer Failsafe-JVM. SCRIPT ist der Hauptweg.
JAR und IMAGE werden ausdrücklich für ausgewählte Produktprozesse verwendet.
Das Dienstprofil vergleicht erfolgreiche SCRIPT-Operationen mit
dem [Operationskatalog](../../tests/contracts/operation-baseline.json).
Jede fehlende Operation führt zu einem Buildfehler. Das IMAGE-Profil
verlangt zusätzlich einen frischen Report von `ImageRuntimeIT`.

Der reguläre Workflow `publish-snapshots.yml` verwendet die konsolidierte
Prüfung bei Push auf `main`, bei Pull Requests gegen `main` und bei manuellem
Start. Bei Erfolg enthält das CI-Artefakt die ZIP- und TAR-Distribution mit
SHA-256. Bei Fehlern enthält das Diagnoseartefakt einen Index mit JUnit-Fällen
und begrenzten Logauszügen der zugeordneten SCRIPT-, JAR- und IMAGE-Prozesse.
Bei einem Abbruch werden IMAGE-Auszüge vor dem Löschen des Workspaces
gesichert und als unvollständig zugeordnet markiert. Bekannte Test-Secrets
werden beim Staging maskiert. Der Export lässt verdächtige Zeilen,
Symlinks und ganze Workspaces aus und kennzeichnet Kürzungen.
Lokal schreibt der Verify-Einstieg Diagnosen nach
`target/failure-diagnostics/<Laufkennung>/`. CI bewahrt das Artefakt sieben Tage auf.

JaCoCo misst Test-JVMs und aktive Produktmodule. Separate Launcher-,
Job-JAR- und Containerprozesse liefern fachliche Nachweise, keine erfundene
Codecoverage. Die [Jobtest-Verträge](job-artifact-tests.md) erklären
Prozessstart, Eingaben, Reports und Cleanup.
Testzahlen gelten für den jeweiligen SHA. Tatsächliche Plattform und
nicht ausgeführte Cluster-/Architekturprüfungen gehören in den jeweiligen Prüfbeleg.

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
Kopien identisch. `scripts/check-doc-examples.py` führt Download/Extract mit einem lokalen
HTTP-Testserver sowie die Projektpipeline jeweils aus Checkout und Distribution
aus, vergleicht alle Ausgabebytes und prüft erfolgreiche Reports. Damit beruht die
bewusste README-Trennung auf zwei tatsächlich ausgeführten Kontexten.
