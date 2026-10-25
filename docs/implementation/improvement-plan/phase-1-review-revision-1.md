# Unabhängiger Prüfbericht – Phase 1, Revision 1

Phase: 1 – Funktion und Nachweise absichern  
Prüfdatum: 25. Oktober 2026  
Implementierungsagent: Agent A (`/root`)  
Unabhängiger Prüfagent: Agent B (`/root/phase1_review`), keine Implementierungsbeteiligung  
Ausgangscommit: `230f2882bcdde15f552f7959aed157a0a6301fa6`  
Erster, mit FAIL bewerteter Kandidat: `79867a03ada856011ffb2d3dc52da44b78bcb5a1`  
Jetzt geprüfter Kandidatencommit: `df0f00d7f74ea285c7c9eb476389d8cb55ee4aa2`  
Eigener sauberer detached Checkout: `/tmp/gretl-phase1-review-df0f00d`  
Eigene Nachweise dieser Revision: `/tmp/gretl-phase1-revision-review-evidence/`

**Urteil: FAIL.** F1 und die nicht blockierende Beobachtung D1 aus dem ersten Review sind geschlossen. Der eigene Standardbuild besteht; der eigene Pflichtprofillauf scheitert jedoch am FTP-Cleanupfehler **F2 (P1-10)**. Der nachgelagerte GeoTools-Reactorbaustein wird dadurch nicht ausgeführt. Es gibt kein PASS und keine Freigabe für Phase 2.

## Prüfstand, Unabhängigkeit und Revisionsumfang

HEAD und sauberer Arbeitsbaum wurden vor und nach der Prüfung selbst kontrolliert. Der Prüfer hat weder Produkt- noch Testdateien des Kandidaten geändert. Alle zusätzlichen Programme und Outputs liegen ausserhalb des Checkouts im genannten Nachweisverzeichnis. Der reine spätere Berichtscommit `ec55c86` ist nicht der Produktprüfstand dieses Urteils.

Repository-Anweisungen, der vollständige aktualisierte Plan mit 34 Kriterien und die einschlägigen normativen Verträge waren Grundlage der ersten unabhängigen Prüfung. Sie gelten unverändert weiter: Der erneute Bytevergleich bestätigt alle 19 Dateien unter `docs/spec/` und den aktuellen Plan als identisch. Die erneute Prüfung umfasst das gesamte Revisionsdiff, die betroffenen öffentlichen Beispiele, alle P1-Kriterien, die Originalzuordnung und die erneut ausgeführten Regressionen. `phase1.diff` und `revision.diff` sichern den vollständigen Änderungsstand. Der Revisionsbericht von A wurde als Orientierung verwendet, nicht als Erfolgsbeleg.

Seit dem ersten geprüften Kandidaten ändern sich genau eine Produktklasse (`Ili2DbOperations`, ausschliesslich Beispieltexte), ein neuer ausführbarer Hilfe-JAR-Test in `ValidatorLocalJobIT`, die entsprechende Referenzeinbindung und das korrigierte historische FTP-Boolean. Der erste Implementierungsbericht und der erste FAIL-Bericht wurden zusätzlich abgelegt; der FAIL-Bericht ist bytegleich erhalten. Anforderungen, Buildkonfiguration und übrige Produkt-/Testpfade wurden dabei nicht geändert. Belege: `changed-code-paths.json`, `inventory-revision-audit.json` und `revision.diff`.

Die Ausgangsbaseline bleibt belegt: Produktcode von `230f2882` und `e668a51b9589fbea9a1e003c8dd18e28726bd430` ist identisch; der frühere Unterschied war der Plan. Die im ersten Review selbst ausgewerteten Baseline-XML ergaben Standard 384/0/0/0 und Profil 416/0/1/0. Der historische FTP-Cleanupfehler wird nicht als behobener Produktfehler behauptet. Diese Revision hat auch die entsprechenden Tests tatsächlich ohne Fehler erneut ausgeführt.

## Eigene Prüfumgebung und Befehle

Temurin OpenJDK **25.0.2+10-LTS**, Maven Wrapper **3.10.0**, Groovy **6.0.0**, macOS **27.0.1 ARM64**, UTF-8, Hostlocale en_US, Europe/Zurich. Docker Desktop **4.93.0**, Engine **29.8.1**, Linux ARM64. Die Produktprozesse verwenden UTC und deutsche Sprache; die bekannten Testclasspath-/JVM-Injektionsvariablen werden entfernt. Die bereits festgelegte PostgreSQL/PostGIS-AMD64-Testinstanz läuft auf Docker Desktop unter Emulation. Vollständiger Beleg: `environment.txt`.

Alle Shellbefehle wurden mit dem neuen Review-Checkout als Arbeitsverzeichnis ausgeführt. Die beiden verbindlichen Rootbefehle wurden selbst ausgeführt. Der Standardlauf endet erfolgreich; der Profillauf endet mit BUILD FAILURE und überspringt danach den GeoTools-Reactorbaustein:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Logs: `standard.log` und `profiles.log`. Der erste Stand wurde ausdrücklich vor dem zweiten `clean` nach `standard/` gesichert; der zweite nach `profiles/`. Beide Bereiche enthalten Surefire-/Failsafe-XML, Coverage-HTML/XML und die durch `ArtifactJobRunner` gebauten und gestarteten JARs samt Prozesslogs, Reports und Ausgaben.

Zusätzlich selbst ausgeführt:

```sh
python3 /tmp/gretl-phase1-revision-review-evidence/help-probe.py
python3 /tmp/gretl-phase1-revision-review-evidence/preserve.py standard
python3 /tmp/gretl-phase1-revision-review-evidence/preserve.py profiles
python3 /tmp/gretl-phase1-revision-review-evidence/audit_original_assertions.py
python3 /tmp/gretl-phase1-revision-review-evidence/audit_artifacts.py standard profiles
python3 /tmp/gretl-phase1-revision-review-evidence/audit_regressions.py
python3 /tmp/gretl-phase1-revision-review-evidence/audit_outcomes.py standard profiles
python3 /tmp/gretl-phase1-revision-review-evidence/audit_coverage.py
```

## F1 und D1 – erneuter eigener Nachweis der Korrektur

**F1: geschlossen.** Der eigene Hilfeharness liest die tatsächlich gebaute CLI-Ausgabe mit `operations Ili2gpkgImport --lang de`. Er übernimmt sämtliche Bytes nach `Beispiel:` unverändert als `job.groovy`; der Jobtext wird weder ergänzt noch umgeschrieben. Das ausgegebene Rezept lautet:

```groovy
parameter('output', type: 'path', required: true, description: 'GeoPackage output')
task('run', Ili2gpkgImport) {
    databaseFile = parameter('output')
    dataFiles = [file('data.xtf')]
    modelDirectories = [file('models')]
    defaultSrsCode = '2056'
}
defaults 'run'
```

Die mitgelieferten Testressourcen werden unter den ausdrücklich im Rezept genannten Namen `data.xtf` und `models/Beispiel2.ili` bereitgestellt. Der reguläre Packager der gebauten Kandidatendistribution erzeugt das JAR. Ein separater `java -jar`-Prozess startet es aus dem fremden Arbeitsverzeichnis, ausschliesslich mit Runtimekonfiguration, Outputparameter, Report- und Logoptionen. Es wird kein Ziel angehängt; das ausgegebene `defaults` ist selbst wirksam. Der JAR-Prozess erhält keinen Testclasspath.

Eigenes Ergebnis: OS0, GRETL SUCCEEDED/0. Ein unabhängiger Python-SQLite-Leser prüft **die konkreten Inhalte**, nicht bloss Existenz oder Dateigrösse:

- `gpkg_geometry_columns`: `boflaechen`, `form`, `CURVEPOLYGON`, **2056**.
- `boflaechen`: genau `(5.0, null, null)` und `(null, 'Gebaeude', null)` für Fläche/Art/Form.
- `gebaeudeart`: genau die drei ursprünglichen Art-/Beschreibungseinträge, einschliesslich des mehrzeiligen Textes.

Das Original-XTF enthält in diesem Rezept keine befüllte optionale Geometrie. Dieser Test behauptet daher keine Koordinatentransformation; der zusätzliche unabhängige Polygon-/WKB-Nachweis aus dem ersten Review bleibt gesondert erhalten.

SHA-256 der unverändert übernommenen CLI-Quellbytes: `4a615c556f129a4b7511bd3fcf98dd60482660e9ee13550f107b6a613b3918d8`.  
SHA-256 des tatsächlich gestarteten JARs: `4d7c3f7c4e96cbef9916a1147ca28636a4beae642fa0a3e94e7047dae02629d0`.

`actual-help-jar/` enthält die rohe CLI-Ausgabe, bytegleiche Quelle, Descriptor, Paketlogs, JAR, tatsächliche Argumentliste, Prozesslogs, Runbericht und `evidence.json`. Manifest und `Ili2DbOperations`-Bytecode wurden mit dem Kandidatenbuild abgeglichen. Zusätzlich besteht der neue dauerhafte JAR-Regressionstest in beiden eigenen Rootläufen. Die Referenz bindet jetzt das vorhandene ausführbare GPKG-Rezept ein.

**D1: geschlossen.** Der historische FTP-Fall trägt nun `historicallyDisabled:true`; er wird nicht zur aktiven Originalabdeckung gezählt. Der erneute Inventarabgleich findet genau 86 aktive Methoden und diesen einen deaktivierten historischen Fall.

## Je Abnahmekriterium

| ID | Ergebnis | Eigener Nachweis |
|---|---|---|
| P1-01 | PASS | Ausgangsbaseline und vollständiges 44er-Inventar erneut abgeglichen; `OperationMetadataTest` besteht in beiden neuen Läufen. Kompatibilitätsmatrix und schmale IliValidator-/SqlExecutor-Verträge sind unverändert. Die gretl-next-Referenz bleibt `08bd11e97a54f32f8702d34ff16fb39bec6ea046`. |
| P1-02 | PASS | Neue Kandidaten-JARs aus `ValidatorLocalJobIT`: gültige CSV ohne Tabelle, GPKG mit Tabelle, fehlende/falsche Tabelle und unzulässiges CSV-Feld. Fachliche Logs und konkrete Konfigurationsdiagnosen werden geprüft. Zusätzliche eigene leere/falsche Tabellenwerte aus dem ersten Review bleiben als Nachweise des unveränderten Vertrags erhalten. |
| P1-03 | PASS | Alle vier Validatorarten führen Path- und String-Repositories auf dem neuen Artefakt aus. Ungültiger Integerwert wird vor Enginearbeit abgewiesen. Die eigene zusätzliche Steuerzeichen-Repositoryprobe des ersten Reviews betrifft unveränderten Konvertercode; sie bleibt ausdrücklich als früherer, nicht erneut behaupteter Lauf dokumentiert. |
| P1-04 | PASS | Neue JAR-Läufe mit 2056 und 21781 sowie unabhängigen SQLite-Abfragen von SRID und importierten Daten. Zusätzlich prüft der eigene unverändert aus CLI-Hilfe gebaute JAR-Job konkrete Datensätze und SRID 2056. Der frühere zusätzliche WKB-/Polygon- und ungültige SRS-Nachweis bleibt erhalten; Engine und Feldkonverter sind unverändert. |
| P1-05 | PASS | `DatabaseArtifactIT` des neuen Profilbuilds: aktive reale PostgreSQL-Abfrage als Checkpoint, CANCELLED/20, Folgetask NOT_RUN, keine Umklassifizierung, unveränderte Zielzeilen und keine offene Transaktion. Db2Db-/FailureArbiter-Regressionen bestehen. Die frühere eigene SQL-Fehler-/Rollback-/Sessionprobe ergänzt den unveränderten Codebeleg. |
| P1-06 | PASS | Alle acht `CancellationLocalJobIT`-Fälle erneut auf dem neuen Kandidaten: Script und JAR, kooperativer Interrupt, SIGTERM, Timeout/Reaping und echter Fehler vor SIGTERM. Konsistente Terminaldaten und Reports; OS143 wird von GRETL20 getrennt. Gemeinsames 30-s-Budget bleibt durch `RunControlTest` belegt. Frühere eigene Zusatzprozesse mit absichtlichem sekundärem Cleanupfehler bleiben Regressionsevidenz des unveränderten Abschlusswegs. |
| P1-07 | PASS | Korrelation, Konfigurationsvorrang, Wertevalidierung und Lauftrennung in `CorrelationTest` erneut erfolgreich. Logging-/Redaction-/Reportregressionen bestehen. Die vorherigen eigenen echten JAR-Proben für ungültigen ENV-Fallback unter gültiger Konfiguration, Provider-Überschreibversuch und getrennte Läufe betreffen unveränderten Code. |
| P1-08 | PASS | F1 ist durch die oben dokumentierte eigene tatsächliche CLI-Hilfe/JAR-/SQLite-Prüfung geschlossen. Referenz und ausführbares Rezept stimmen überein. Die neuen öffentlichen DSL- und DB-Artefaktprüfungen besitzen unabhängige Orakel; PostGIS geometry wird mit der konkreten fachlichen Diagnose ausdrücklich abgewiesen. Der frühere zusätzliche Geography-/SRID-Nachweis bleibt gesichert. |
| P1-09 | PASS | Eigene neue Line-/Branchberichte und Aggregatgruppen für sämtliche 13 produktiven Module; JaCoCo 0.8.14 unverändert gepinnt. Messgrenzen und uninstrumentierte Subprozesse ausdrücklich unten ausgewiesen. |
| P1-10 | FAIL | Eigener Standardlauf 413/0/0/0. Eigener Profilaufruf 443/0/1/0, BUILD FAILURE: `FtpTaskFamilyIT.wrongPasswordFailsWithoutLeakingSecret` scheitert im Lease-Cleanup. Der danach im Reactor stehende GeoTools-Baustein wird SKIPPED, seine vier Tests sind nicht ausgeführt. Sie zählen nicht als vier JUnit-Skips. Keine Freigabe; F2 siehe unten. |
| P1-11 | PASS | Originalinventar erneut selbst geprüft: 386 Dateien mit SHA-256/Byteumfang, 86 aktive Methoden vollständig zugeordnet, 205 Originalassertionen unverändert erfasst. D1 korrigiert. Keine Szenarien oder Erwartungen wurden entfernt; weiterhin offene Varianten und begründete Scopeausnahmen bleiben für Phase 2 sichtbar. |
| P1-12 | PASS | Kanonische Fixtures und gemeinsamer Runner auf neuen Kandidatenartefakten vollständig erneut ausgeführt. Datei-/Transformationsjob, echte DB-Kette, fachlicher Negativfall und korrigierte DSL-Fälle starten als echte JAR-Prozesse. Alle durch `ArtifactJobRunner` gestarteten Identitäten und deren 469 Produktklassen aus elf Laufzeitmodulen wurden selbst geprüft. Der eigene zusätzliche JAR-Start aus tatsächlicher Hilfe ergänzt dies. Falsche/fehlende Outputs und beschädigter Launcher wurden im ersten Review durch eigene Gegenbeispiele sicher zurückgewiesen; diese Assertions und Runnerverträge sind unverändert. |

## Finding F2 – FTP-Dienst stirbt beim Authentifizierungsnegativfall; Pflichtlauf scheitert

Pflichtbezug: P1-10 und verbindlicher Rootaufruf mit `geotools-probe,backing-services`.  
Betroffene Stellen: `gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/FtpFixture.java:188` (Lease-Cleanup), `FtpFixture.java:231` (nicht erreichbarer Dienst), `FtpTaskFamilyIT.java:45` (AfterEach).

Der neue eigene Profilaufruf endet mit **BUILD FAILURE**. Der Fehler betrifft `FtpTaskFamilyIT.wrongPasswordFailsWithoutLeakingSecret`, Klasse insgesamt 4 Tests/0 Failures/1 Error/0 Skips:

```text
java.lang.IllegalStateException: Could not clean FTP lease /home/gretl/c093b83c4e9d
Caused by: java.io.IOException: FTP fixture is not reachable
Caused by: java.net.ConnectException: Connection refused
```

Der durch die Fixture bereits redigierte Serverlog wurde vor jeder weiteren Bereinigung **bytegleich** gesichert. Sein Schluss zeigt unmittelbar nach `FAIL LOGIN`:

```text
process has died, quitting
```

Das ist ein eigener Beleg für einen ausgefallenen FTP-Prozess; die alleinige Erklärung eines verlorenen Hostport-Mappings ist damit nicht nachgewiesen. Eine genaue Bootstrap-/PID-Ursache wird aus diesem Log allein nicht behauptet. Beim zusätzlichen Docker-Capture war der Container bereits entfernt; `docker inspect` und `docker logs` melden `No such object`. Ein damaliger Exit-/OOM-Zustand konnte deshalb nicht mehr unabhängig ausgelesen werden.

Service-Evidenz: `ftp-failure-live/service-evidence/ftp-f288eb7139c5462e475621a0bc4c0cae06fac830628f6d5aad6a448f26ebe9ff.log`. SHA-256: `528544ca911622ced9317fa071f598d7ad79dc8c5ad2ad4c744b3189e38d6416`. Unter `ftp-failure-live/` liegen ausserdem die unveränderten FtpTaskFamilyIT-XML/Textreports, Docker-Captureversuche, Capturezeit und Prüfsummen. Der abschliessende Berichtssatz enthält die Service-Evidenz zusätzlich unter `profiles/service-evidence/`.

Die übrigen IT-Klassen wurden trotz des Failsafe-Testfehlers weiter ausgeführt und geprüft. Erst Failsafe `verify` beendet den Reactor; `gretl-geotools-probe` ist danach **SKIPPED**. Daher 443 tatsächlich ausgeführte XML-Tests statt 447 und 0 JUnit-Skips, aber vier nicht ausgeführte GeoTools-Pflichtfälle. Dieser Unterschied wird nicht als erfolgreicher Gesamtprofilnachweis ausgegeben.

Notwendige Korrektur: Die lokale FTP-Fixture muss den echten Authentifizierungsnegativfall und ihre Ressourcenlebensdauer zuverlässig tragen. Der Serverprozessausfall ist zu beheben beziehungsweise der Fixture-Start-/Besitzvertrag belastbar zu korrigieren. Ein Unterdrücken des Cleanupfehlers, Deaktivieren des Negativfalls oder blosses Wiederholen bis zu einem grünen Lauf genügt nicht. Danach sind ein neuer sauberer Kandidat und die erneute unabhängige Pflichtprüfung erforderlich. Der Prüfer hat weder den Kandidaten verändert noch den Lauf wiederholt.

## Tests und übernommene Regressionen

| Eigener Rootlauf auf dem neuen Kandidaten | Tests | Failures | Errors | Skips | Ergebnis |
|---|---:|---:|---:|---:|---|
| Standard | 413 | 0 | 0 | 0 | BUILD SUCCESS |
| geotools-probe,backing-services | 443 | 0 | 1 | 0 | BUILD FAILURE; GeoTools-Reactorbaustein nicht ausgeführt |

Die Zählung stammt selbst aus den gesicherten `TEST-*.xml`, nur aus aktiven Reactormodulen, ohne doppelte Failsafe-Zusammenfassungen und ohne alte GeoTools-targets im Standardlauf.

| Modul | Standard | Vollprofil |
|---|---:|---:|
| gretl-api | 17 | 17 |
| gretl-basic-tasks | 51 | 51 |
| gretl-cli | 62 | 62 |
| gretl-core-tasks | 23 | 23 |
| gretl-dist | 11 | 11 |
| gretl-groovy | 49 | 49 |
| gretl-i18n | 4 | 4 |
| gretl-ilivalidator | 3 | 3 |
| gretl-integration-tests | 26 | 56 |
| gretl-interlis-tasks | 25 | 25 |
| gretl-logging | 18 | 18 |
| gretl-packager | 8 | 8 |
| gretl-runner | 116 | 116 |

`regression-audit.json` vergleicht Testklasse und Testfallname mit den eigenen früheren Reports. Im Standardlauf fehlt keine Regression. Im Profillauf fehlen genau die vier GeoTools-Testausführungen, weil Maven nach dem Failsafe-Fehler stoppt; keine Testquelle wurde dafür deaktiviert. In beiden Konfigurationen kommt genau `ValidatorLocalJobIT.publishedGeoPackageExampleImportsTransferData` hinzu. Die vier fehlenden Ausführungen werden ausdrücklich als nicht erbracht ausgewiesen und nicht aus dem früheren grünen Kandidaten übernommen.

PostgreSQL/PostGIS, FTP und S3/Floci wurden im eigenen neuen Profil tatsächlich verwendet. Die Fachtests lesen die Zustände über unabhängige JDBC-/FTP-/S3-Clients. FTP/S3 sind in Phase 1 weiterhin erhaltene CLI-Komponententests; deren vollständiger JAR-Port gehört zu Phase 2. Die neue DB-Artefaktkette läuft bereits als separater JAR-Prozess. Die GeoTools-Probe bleibt ein gesonderter technischer Pflichtnachweis und wurde auf diesem Kandidaten wegen des Reactorabbruchs nicht erneut ausgeführt; ein ili2gpkg-GeoPackage wird dafür nicht als Ersatz ausgegeben.

## Eigene frühere Gegenbeispiele des unveränderten Bereichs

Die erste unabhängige Prüfung ist unverändert unter `docs/implementation/improvement-plan/phase-1-review-initial.md` abgelegt. Ihre vollständigen Belege liegen unter `/tmp/gretl-phase1-review-evidence/`. Die folgenden zusätzlichen eigenen Gegenbeispiele wurden damals tatsächlich ausgeführt und werden hier als Belege des unveränderten Bereichs eingeordnet, nicht als neue Läufe umetikettiert:

- `independent2/table-negatives/`: leere und andere fehlende Tabelle; `repository-control/`: Steuerzeichenwert vor Enginearbeit abgewiesen.
- `independent2/srs-probes/`: positiver Import und ungültiger SRS-Text; `real-geometry/geometry-content.json`: eigener befüllter Polygonimport, GPKG-Header-SRID 2056 und exakt unabhängig dekodierte WKB-Vertices.
- `independent2/database3/oracle.json`: Datenbankkette, echter SQL-Fehler FAILED/10, Geography(Point,4326) ausdrücklich abgewiesen und unverändert, kontrollierter Db2Db-Abbruch CANCELLED/20 bei OS143, Zielzeilen erhalten, null verbleibende Sessions.
- `independent2/cancellation-negative/`: Script und JAR mit SIGTERM plus absichtlichem Cleanupfehler; Cancel bleibt primär, vorheriger echter Fehler bleibt FAILED/10; jeweils ein Run- und Cleanup-Terminalereignis.
- `independent2/correlation/`: Konfiguration vor ENV, getrennte Läufe und abgewiesener Provider-Überschreibversuch.
- `independent2/archive-oracle/`: Nach erfolgreichem Prozess werden absichtlich falsche Expected-Bytes und ein fehlender Actual-Output vom unabhängigen Orakel zurückgewiesen. Ein absichtlich beschädigtes JAR mit OS1 und fehlender fachlicher Diagnose erfüllt den CSV-Negativfall nicht.

Die dabei geprüften Ausführungs-, Feldkonversions- und Fehlerpfade sowie Fixtureinhalte sind unverändert; in `Ili2DbOperations` ändern sich ausschliesslich Beispieltexte. Die neuen Artefaktprüfungen bestätigen diese unveränderten Fach-/Prozesspfade auf dem neuen Commit. Der fehlgeschlagene Pflichtprofillauf wird dadurch nicht ersetzt. Bekannte Grenzen des kooperativen Abbruchs bei SIGKILL oder dauerhaft blockierenden Fremdbibliotheken bleiben unverändert; es wird keine stärkere Cleanup- oder Rollbackgarantie behauptet.

## Coverage-Messumfang und Grenzen

Eigene Quelle: `profiles/gretl-integration-tests/site/jacoco-aggregate/jacoco.xml`. Werte covered / total; beide vollständigen Auswertungen stehen in `coverage-summary.json`.

| Produktmodul | Lines | Branches |
|---|---:|---:|
| gretl-api | 110 / 114 | 23 / 26 |
| gretl-runner | 1796 / 2078 | 754 / 1061 |
| gretl-groovy | 498 / 733 | 148 / 238 |
| gretl-i18n | 24 / 31 | 13 / 20 |
| gretl-logging | 923 / 1103 | 385 / 590 |
| gretl-packager | 508 / 698 | 204 / 310 |
| gretl-job-runtime | 0 / 150 | 0 / 52 |
| gretl-dist | 20 / 26 | 5 / 8 |
| gretl-cli | 1080 / 1326 | 379 / 561 |
| gretl-basic-tasks | 2058 / 2647 | 732 / 1240 |
| gretl-core-tasks | 1104 / 1382 | 288 / 488 |
| gretl-ilivalidator | 115 / 175 | 20 / 42 |
| gretl-interlis-tasks | 2824 / 3667 | 1092 / 1825 |

Surefire-/Failsafe-Test-JVMs sind instrumentiert. Separat gestartete Packager-, Launcher- und Job-JVMs sind ausdrücklich **nicht instrumentiert** und werden nicht als gemessene Abdeckung ausgegeben. Der Aggregatbericht gruppiert die verfügbaren Test-JVM-Beiträge für sämtliche produktiven Module, einschliesslich der transparenten Nullwerte für `gretl-job-runtime` (0/150 Lines, 0/52 Branches). Dynamischer Jobbytecode ist kein Produktmodul. Testanzahl und fachliche Prozessnachweise sind von Coveragewerten getrennt; es gibt keine willkürliche Prozenthürde.

## Tatsächlich verwendete Jobidentitäten

`artifact-audit.json` enthält die selbst nachgerechneten SHA-256, Prozessmodi, Exit-/Timeoutstatus und Dienstendpoints. Bei allen 45 durch `ArtifactJobRunner` gestarteten JARs stimmen sämtliche 469 Produktklassen aus elf Laufzeitmodulen byteweise mit dem neuen Kandidatenbuild überein. Manifest und Argumentlisten bestätigen `java -jar` ohne Testclasspath. `outcome-audit.json` prüft zusätzlich pro Lauf genau ein terminales Runereignis, übereinstimmende Run-Identitäten/Statuswerte, konkrete Negativdiagnosen, Archivbytes sowie GeoPackage-SRID und Fachzeilenzahlen. Die 8 SCRIPT-Zeilen nennen den SHA der tatsächlich geladenen Scriptbytes, kein daneben lediglich gebautes JAR. Jeder Lauf verwendet eine frische Quellkopie und eigene Outputs; beide Ausgabeströme werden per Dateiumleitung erst nach Prozessende ausgewertet.

| Build | Verzeichnis unter job-evidence | Modus | SHA-256 | OS-Exit | Timeout |
|---|---|---|---|---:|---|
| standard | cancel-false-false-88cf4f09-9dea-46ae-9696-91521937fce6 | JAR | `9c7fa0dca9d512f85060549cd4fb47e591444e4378619cee6afe22c071d3cef5` | 20 | False |
| standard | cancel-false-true-c657d986-740c-45a2-a90e-3fe60607177d | JAR | `ab1118b5390d2a41ab9e851f6d20c15c7ad03a193902e91b2a80bea14e9b2928` | 143 | False |
| standard | cancel-true-false-585d1ade-b8fb-4fff-874f-a1ba3b62a688 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 | False |
| standard | cancel-true-true-c016ce96-47a6-4773-aef3-7435e7d6141b | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| standard | csv-unsupported-table-8e390458-914d-407f-8358-dc3d9ebac90c | JAR | `3c79f7f298e9fbbd92ed38eedc5230a1e9c255f5c973035e28114a9eff178998` | 10 | False |
| standard | failed-shutdown-false-c4e841c6-239d-45d2-930a-08a24b301541 | JAR | `eae82c4c385ee0a44e4bd5d80a1387380d7685fc9116080a0b6b725aa68ad8f5` | 143 | False |
| standard | failed-shutdown-true-e1121ec8-74d3-4d40-89b8-098a4d9f06d4 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| standard | gpkg-help-example-8172ec24-7de4-4746-93e9-5bb8682a147c | JAR | `f79c392ed2489d6f2445acc8500665601c7b8be886e5600fba191d2ae384475f` | 0 | False |
| standard | gpkg-missing-table-481ef82b-fba7-405e-986b-77da9c328088 | JAR | `d9dec63c3c677f85d42cce441e5f144cc714d69a9c3e0850b3e31e9e853cc9ce` | 10 | False |
| standard | gpkg-srid-9d94605d-4d7d-4737-addb-d68c8e7c3fef | JAR | `0e98a93ae527160801ec517b72be5ec10f6debe614d7439a2fcb78016a7876ea` | 0 | False |
| standard | gpkg-srid-9e496094-8b22-4676-b1ec-29270e472fbb | JAR | `c4da3f03fa5e588f31affc785f69fee3cd4ec2c5fb64971691346f2607161256` | 0 | False |
| standard | invalid-csv-dd9932ec-7f53-47cd-bb8e-affd6d85866b | JAR | `a3153d872f80dfbcba5fef3c8eccd01a7e22228a7944698f17d2b103c95b6953` | 10 | False |
| standard | invalid-model-repository-faadfbdc-6f07-4d02-8316-817c8ebefd8f | JAR | `92749a0887bae3c981d82c75e39568c50c218ff0ffc7d412fa1b6c2dba762a8b` | 10 | False |
| standard | original-gzip-9b2bc660-e60a-440d-b0ba-5776a4aa21bd | JAR | `79c531801380ce0f696408187c1deefdd083e9afb099596b06c0527e1be76164` | 0 | False |
| standard | runner-timeout-false-2e10033d-9249-4d30-b956-ba06331a977c | JAR | `44120a3f3222a78bf55a13c8d78c8b581d11d6af141f966df4fe3be004a45ae5` | 143 | True |
| standard | runner-timeout-true-9612a099-d4dd-4a43-b52a-61daa5237076 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | True |
| standard | valid-csv-path-2d9d1182-3c6d-450e-a00b-d5016b3b7170 | JAR | `90931ea3a2f2ac8b14794e6ce6a858caf741b169c59a8989b7fe0cbdffa028e7` | 0 | False |
| standard | valid-csv-string-71cccdbc-cb9a-4a12-966c-d558b8215887 | JAR | `331538dd293e1227fa47873b70c900659db398d37e86ddf495e3e871d3ec1708` | 0 | False |
| standard | valid-gpkg-path-9107f80d-eeac-4830-a286-170c5945b266 | JAR | `fc5385387498701f80cec5d20b16c331f87e053f6c53eb174a033893fe90a263` | 0 | False |
| standard | valid-gpkg-string-13ec14f7-4608-433c-8a9b-49c805c9b729 | JAR | `3d690ea43538220d2e80ad8920a53805cf30cf1889c91e2a3da019254a867f79` | 0 | False |
| standard | valid-json-path-f4071cd6-27a0-4272-a659-142fdc252509 | JAR | `0d73c6f5cac6996ed6bb63d403e66df2c7899f0913f32e95b3b4ce5c64193e80` | 0 | False |
| standard | valid-json-string-c3ff6702-078c-44f6-ac8f-1aae6b95d899 | JAR | `a39d243c50cf2c3c3ebe0b81c6ec759df6aa37c9d202f53fa4440422727cba27` | 0 | False |
| standard | valid-shp-path-7ebd0d26-dce0-4151-8b64-ed0756c4ac49 | JAR | `f481570fedad0402d43c3ea17440c49230d1edb0b367e53c09c2b913b032a3a7` | 0 | False |
| standard | valid-shp-string-f66dc729-f7de-4b5a-aa52-91cb0d04d3ec | JAR | `9ef38f4ab34124996c8ca6ebfe6e340b1b39f5f636476bb80f2bcaf5385cae21` | 0 | False |
| standard | wrong-gpkg-table-c8e78e53-23d6-4c68-85fe-488bb053dfd9 | JAR | `5cffc618ed3d9fc4e22106fc7916d12a7b10fc7b775c9dfd5ad4872db7351df7` | 10 | False |
| profiles | cancel-false-false-e269e913-b261-4220-a45f-399533b993f1 | JAR | `2e942de359771140c3d0a410079be3e7668fec238f9fa8ae155b730a19fbf85a` | 20 | False |
| profiles | cancel-false-true-02c15072-bf3f-44e3-b29a-31a78aad5857 | JAR | `4ec6abc647264711ade290b57273b86ac52fe48df6cd5e8e3cc62ad989038879` | 143 | False |
| profiles | cancel-true-false-2901f5a3-45d4-4280-96cc-80a092c8701e | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 | False |
| profiles | cancel-true-true-73a76b87-433d-4c7c-8d20-09ca3821230e | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| profiles | csv-unsupported-table-a7324d9e-89f5-44b5-897e-e4420cf421c5 | JAR | `0e45394548638224954ea8774829e6e954a286e227c340ef036cb9da933a3229` | 10 | False |
| profiles | database-cancel-435b2a55-7903-4c63-8903-a661b8796fa0 | JAR | `29a3a0581b6533a9867756b313805cd27288f92a7eedb1b19146a7b75a0eb782` | 143 | False |
| profiles | database-chain-02ee66f5-8f8e-4ae5-81fa-adf50fc3b3fd | JAR | `58b6f9d352ee9d6bed1bb16a056b0e52b0d45ed59339f3935bfc3063a725da94` | 0 | False |
| profiles | failed-shutdown-false-822d604d-0485-4d92-b978-1e6c821f0992 | JAR | `9d7af17d389a87d18da57f93b137fabe1eb40ca2cc86c3888140796241c99ed0` | 143 | False |
| profiles | failed-shutdown-true-46da78c8-d177-4234-baeb-80a868bdb967 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| profiles | gpkg-help-example-63443dd9-7fce-4648-8d3a-1220dcd45de5 | JAR | `b935176ab85cbc035021f4b8ae04be42d18ce189018e7df9e774d4ac2d077d62` | 0 | False |
| profiles | gpkg-missing-table-38b7693a-996f-4739-86ba-bfc14cc93fac | JAR | `b40029ed1a37c481184e5404a317a814fbd336e0bf8f5a6459337b511b4b83b1` | 10 | False |
| profiles | gpkg-srid-f3cd490e-2702-4d59-b4bf-d27c650d5b9a | JAR | `6efa9feb8f272e879e6ee49bf154b5940663228566a3adf23ee0a82e4ea264b0` | 0 | False |
| profiles | gpkg-srid-f4a7a193-22a3-4675-9ad8-ff08eb5a78bc | JAR | `59eebb6bc5d39feacd1fbc2e9073287c0273fda8ff9e030ea77e03f3463a439c` | 0 | False |
| profiles | invalid-csv-58104c4e-bdd8-4612-9616-ff145dce7f2e | JAR | `6949b82568bf32a55857ae1e538538a22c4685e3b00bf4348eabc35992758507` | 10 | False |
| profiles | invalid-model-repository-07846dd3-4f12-47ef-aee6-52574acbd296 | JAR | `7963a546ca3ccc57b20c7996db76386474ee5fccaf2f3d75c1f18dfd369c7ab8` | 10 | False |
| profiles | original-gzip-55c3e6f1-b690-4f31-8dc7-157c4f9ef34a | JAR | `46f2f68a8346dd3cf4a97b774f627a093d4e74561409a77bf66150bba15df3b9` | 0 | False |
| profiles | postgis-geometry-contract-65645567-4f60-4e01-9100-7c12b53943fd | JAR | `7e848f7f1668f698f0f3183ad0c38365fab9c4cf3cf75d2ba10e3470ddf93385` | 10 | False |
| profiles | runner-timeout-false-ffe0dfc0-f777-46a1-b718-bc19a9fe399b | JAR | `48058c57ca27a1f434fa22f9bfa45cd628c1e5b87e053f73ee5009c61e3904a1` | 143 | True |
| profiles | runner-timeout-true-b1964f98-3990-492f-b6db-c97e424c65a3 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | True |
| profiles | valid-csv-path-a51be9cb-299f-43a2-8c28-7ecb5061bf55 | JAR | `1a8c733beac5c714173d6283d5a2c7d5e045440323ac8319dd5344a88c2046ee` | 0 | False |
| profiles | valid-csv-string-122a4963-f451-4022-8f38-54e6edeb92fc | JAR | `d71155770ee5afc469c5062b3e1483c0a593eacc5e4cd0f5d94ff2c68f9b2bb8` | 0 | False |
| profiles | valid-gpkg-path-9ff2c85a-fb33-4cc0-96f4-0e3046aad512 | JAR | `0c6e6c8d81612c11fd9966ad1b85193032b177d47ca6675f0baf3ae484fd0701` | 0 | False |
| profiles | valid-gpkg-string-99c9c424-9f62-48ed-934e-13408886aec1 | JAR | `b375cf507d6a602750337e8fe18c5c99ca3f94fedbd8f248b70cee8b4338a84a` | 0 | False |
| profiles | valid-json-path-dbe36172-c94c-40cf-94e9-4e380ba201ae | JAR | `7323007c97091702daa97b3c3c03461458276dd7b8873308e763ffd00a591367` | 0 | False |
| profiles | valid-json-string-32dce773-330d-4828-b045-126577227080 | JAR | `75f7fefcba2d70c1fbc2a16b319658826e50074d90dac9b6ea8b9b7c3f3b87f4` | 0 | False |
| profiles | valid-shp-path-ea7999ea-6363-459d-a763-7a8f49343850 | JAR | `c6ec7525f985efbc98a10d022090bec603515d4016ce3be8b8067b142c00195c` | 0 | False |
| profiles | valid-shp-string-df8dc4b5-b81e-49c1-a43d-5ed3b4fdf161 | JAR | `f4da1fb7f3b21ad588f4bdb7995b0fabc4b35fe05d37d443917ed496225da815` | 0 | False |
| profiles | wrong-gpkg-table-4ee8f155-3601-4a7c-9f1a-5084264dab2a | JAR | `158f704af72b5125a3c3440b126db756feb054dacf8a36bf3ac5aa137f138b0d` | 10 | False |

Der zusätzliche eigene JAR aus tatsächlich ausgegebener CLI-Hilfe steht separat unter `actual-help-jar/`, SHA-256 `4d7c3f7c4e96cbef9916a1147ca28636a4beae642fa0a3e94e7047dae02629d0`. Der dort gespeicherte Prozess erhält nur die erwarteten Runtimeargumente, keine Testklassen. Logs, Reports, Output und Paketidentität bleiben erhalten.

## JAR-/Image-Status und Abschluss

JAR: Die Phase 1-JAR-Szenarien und die korrigierte öffentliche Hilfe sind erfolgreich geprüft; eine vollständige Phase 1-Abnahme scheitert dennoch an F2. Die vollständige 44er-JAR-Abdeckung, vier neuen Taskmodule und gemeinsame Jobausführung sind weiterhin Phase 2; gezielte Paketierung und Gesamtabnahme sind Phase 3. Es wird kein späteres Gate vorweggenommen.

IMAGE: Die in Phase 1 geforderte gemeinsame Request-/Resultatschnittstelle und getrennte Endpointplätze sind vorbereitet. Der ausführbare Image-Adapter und dessen vollständige nicht startende Vertragstests folgen gemäss der phasenspezifischen Planung in Phase 2. Explizites IMAGE wird aktuell klar abgelehnt statt auf JAR zurückzufallen. Es wird kein ausgeführter oder bereits vollständig implementierter Image-Modus behauptet. **GRETL-Produktimage tatsächlich ausgeführt: nein.** Die verpflichtenden Datenbank-/FTP-/S3-Testcontainer wurden davon getrennt tatsächlich ausgeführt.

Die lokale Prüfung gilt für die ausgewiesene macOS-Entwicklungsumgebung und Artefakte; sie behauptet keinen zusätzlichen Linux-x86-64-CI-Lauf oder eine produktive Inbetriebnahme. Früherer FAIL und dessen Belege bleiben erhalten. Der neue Kandidat behebt F1/D1, erhält wegen F2 aber ebenfalls FAIL.

Offene Findings gegen Phase-1-Pflichtkriterien: **F2 (P1-10); vollständige Profilprüfung und nachgelagerte GeoTools-Ausführung nicht bestanden/erbracht**.  
Urteil: **FAIL** für `df0f00d7f74ea285c7c9eb476389d8cb55ee4aa2`.  
Begründung: F1 und D1 sind nachweislich korrigiert. Der selbst ausgeführte Pflichtprofillauf scheitert jedoch im eigenen Lauf nachweislich an der FTP-Fixture; zudem bleiben die vier nachgelagerten GeoTools-Tests unausgeführt. Der frühere gleiche Baselinefehler und grüne Läufe von A ersetzen diesen fehlgeschlagenen unabhängigen Nachweis nicht. Es wurde weder erneut gestartet noch der Fehler unterdrückt. Eine Korrektur und ein neuer sauberer Kandidat mit erneuter unabhängiger Prüfung sind erforderlich. **Phase 2 bleibt gesperrt.**
