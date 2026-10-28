# Phase 0 – Baseline und Ist-Nachweise

## Auftrag, Rollen und Git-Basis

- Auftrag: ausschliesslich Phase 0 der [Subprojekt-Spezifikation 1.1](gretl-runtime-subprojekte-spezifikation.md), Abschnitt 8. Kein Featurecode und kein Beginn von Phase 1.
- Implementierungsagent A: Codex, aktuelle Sitzung. Die unabhängige Review erfolgt durch einen separat gestarteten Agenten mit frischem Kontext in einem detached Worktree. A erteilt keine Gate-Freigabe.
- `phaseBaseSha`: `db1ea316ccc72f946fe244942bae2dc9fb485672`.
- Analysierter Produktstand der Spezifikation: `69a55e3ddde25b97325e4463a70d40f75a5e06d4`. Der Diff zu phaseBaseSha enthält ausschliesslich den zuvor irrtümlich abgelegten Umsetzungsplan; Produktcode, Tests und Build sind identisch.
- Implementierungsbranch: `codex/project-subprojects-phase-0`.
- Beim Einstieg waren die Löschung von `gretl-runtime-umsetzungsplan.md` und die neue `gretl-runtime-subprojekte-spezifikation.md` bereits als Benutzeränderung vorhanden. Diese ausdrücklich bereitgestellte Ersetzung wird unverändert mit versioniert. Sie ersetzt nicht den historischen Improvement-Plan unter `../improvement-plan/`.
- Vor dem ersten Commit gelesen: Root-/Spec-AGENTS, vollständige Subprojekt-Spezifikation einschliesslich Abschnitten 7–12; Spec-README und Kapitel 01, 03, 04, 06, 07 (einschliesslich BLD-001–008), 09, 10, 12–16 sowie `module-boundaries.md`, `testing.md` und `job-artifact-tests.md`.
- Die endgültige `candidateSha` wird nach dem Commit im externen Handoff festgehalten. Dieser Bericht erzeugt keine Git-Selbstreferenz. Der unverändert archivierte Bericht von B nennt später die tatsächlich geprüfte SHA.

## Umgebung und Reproduzierbarkeit

Arbeitsverzeichnis: `/Users/stefan/sources/gretl-runtime`. Nachweise ausserhalb des Checkouts: `/Users/stefan/sources/gretl-runtime-phase-0-evidence-20261028/` (im Folgenden `E/`). Dieser lokale Archivbereich übersteht Maven `clean`; es handelt sich um verfügbare lokale Dateien, keinen behaupteten CI-Lauf. `environment.json`, `pom-inventory.json` und `source-fingerprints.json` liegen dort. Nach jedem Lauf werden JUnit-XML, Coverage, JAR-Identitäten, Reports und freigegebene Prozessausgaben gesichert. Rohkonfiguration und private Secret-/Workspacebestände werden nicht pauschal archiviert. `SHA256SUMS.json` bindet die gesicherten Nachweise; `job.jar` und Distributionsarchive bleiben als lokale Binärnachweise ausserhalb von Git erhalten.

| Merkmal | Tatsächlicher Stand |
| --- | --- |
| JDK und Build-JVM | Eclipse Temurin OpenJDK 25.0.2+10-LTS, Java-Featureversion 25 |
| Java-Home | `/Users/stefan/.sdkman/candidates/java/25.0.2-tem` |
| Maven | Wrapper 3.3.4, Maven 3.10.0 (`c43a36b8d67be7e0805a411bc0898af1a51f5472`) |
| Wrapper-Distribution-SHA256 | `1f6d9909266510f039f59aa0e13dcd2c66da85f043e56276e41f2918f8bddaff` |
| Groovy / Bytecode / Encoding | POM: 6.0.0; CompilerSettings: Ziel 25, UTF-8, GretlScript, Grape-AST-Transformation deaktiviert |
| Betriebssystem | macOS 27.0.1, aarch64; Docker-Daemon Linux arm64 |
| Locale / Zeitzone | Maven: en_US / Europe/Zurich / UTF-8; ArtifactJobRunner-JAR-Prozesse explizit de / UTC / UTF-8 |
| Docker | Client/Engine 29.8.1, API 1.56, Desktop 4.93.0; Context desktop-linux |
| Testmodus | bestehender Default JAR; kein GRETL-IMAGE-Start |

Die vorhandene JDK-Patchversion wird dokumentiert und unverändert benutzt; Phase 0 aktualisiert keine Dependencies oder Toolchains. Dies ist ein Entwicklungsnachweis unter macOS ARM64, keine neue Linux-x86-64-, Windows-, Native- oder Produktionsfreigabe. Die vorhandenen CI-Workflows verwenden Ubuntu und Java 25 sowie dieselben zwei Maven-Aufrufe und anschliessenden Evidenzaudits. Ein neuer CI-Lauf wird hier nicht behauptet. Der vorhandene Maven-Cache wird verwendet; dies ist kein Nachweis eines leeren Dependencycaches.

## POM- und Modulbestand

Ein Root-Parent/Aggregator (`gretl-runtime-parent:0.1.0-SNAPSHOT`), 15 reguläre Reactormodule; `geotools-probe` ergänzt genau ein Testmodul. Insgesamt 17 getrackte POMs inklusive Root. Das externe FileSize-Fixture erbt bewusst nicht vom Produktparent. Ignorierte Quellcaches unter `build/` und `docs/reference/.thoth/` zählen nicht zum Reactor oder zur Reportauswertung.

| Modul | Verantwortung / interne Produktionskanten |
| --- | --- |
| `gretl-api` | JDK-only SPI und Metadaten; keine Produktionsdependency |
| `gretl-i18n` | Meldungen; keine Produktionsdependency |
| `gretl-runner` | Definition/Plan/Results/Ausführung; API, i18n |
| `gretl-groovy` | Script und DSL; API, Runner, i18n, Groovy/Core-JSON |
| `gretl-logging` | Logging und Report; Runner, i18n, SLF4J/Logback/JUL-Brücke |
| `gretl-job-runtime` | gemeinsamer Script-/JAR-Ausführungsweg; API, i18n, Runner, Groovy, Logging |
| `gretl-packager` | Authoring/Compiler/JAR; API, Groovy, i18n, Logging |
| `gretl-cli` | Argumente/Authoring; Job-Runtime, API, Runner, Groovy, Logging, Packager |
| `gretl-io-tasks` | 13 IO-Operationen; API, i18n und eigene Fachdependencies |
| `gretl-database-tasks` | 3 Datenbankoperationen; API, i18n, PostgreSQL/DuckDB |
| `gretl-interlis-tasks` | 27 INTERLIS-/Format-/AV-Operationen; API, i18n und eigene Engines |
| `gretl-ilivalidator` | schmaler Validator; API, i18n und eigene Validatorbibliotheken |
| `examples/file-size-extension` | unabhängig gebautes Erweiterungsfixture; API provided |
| `gretl-dist` | Standarddistribution, versionierter Paketkatalog, Standardfunktionen |
| `gretl-integration-tests` | Artefakt-/Diensttests; explizite Reactorkante zur Distribution, aggregierte Coverage |
| `gretl-geotools-probe` | nur im gleichnamigen Profil; GeoTools 35.2 testseitig, keine Produktkatalogerweiterung |

`backing-services` erweitert die bestehenden Failsafe-Includes von `*JobIT` auf `*IT`; es nimmt das Integrationstestmodul nicht doppelt auf. `publish-snapshots` konfiguriert nur das Veröffentlichungsrepository; es wird für diese Baseline nicht aktiviert. Keine neuen Profile, Plugins oder Produktmodule.

## Tatsächliche öffentliche DSL-Signaturen und künftige Eingriffspunkte

Quellbasis jeweils `src/main/java/` des genannten Moduls. Diese Bestandsaufnahme ist keine Implementierung der vorgeschlagenen neuen Signaturen.

`gretl-groovy`, Package `ch.so.agi.gretl.runtime.groovy`:

```java
// GretlScript
void parameter(Map<String, ?> options, String name);
Object parameter(String name); // Toplevel-Zugriff wird als TOPLEVEL_PARAMETER_READ abgelehnt
<C, R> TaskRef<R> task(String id, OperationType<C, R> type, Closure<?> configure);
<C, R> TaskRef<R> task(Map<String, ?> options, String id,
                      OperationType<C, R> type, Closure<?> configure);
TaskRef<Object> action(String id, Closure<?> body);
TaskRef<Object> action(Map<String, ?> options, String id, Closure<?> body);
void defaults(Object... taskNamesOrRefs);
void cleanup(String name, Closure<?> body);
void println(Object value);
void println();
JobDefinition freeze();

// GroovyJobLoader
JobDefinition load(Path scriptFile, DefinitionEnvironment environment);
JobDefinition loadCompiled(String entryClass, Path scriptLabel, DefinitionEnvironment environment);
JobDefinition loadCompiled(String entryClass, Path scriptLabel,
                           DefinitionEnvironment environment, ClassLoader classLoader);

// GroovyContextFacade (Ausführungsscope)
Object parameter(String name);
<T> T parameter(String name, Class<T> type);
Path file(String path);
Path path(String path);
Path workPath(String path) throws IOException;
Object result(String taskName);
Optional<Object> resultOptional(String taskName);
<R> R result(TaskRef<R> ref);
<R> Optional<R> resultOptional(TaskRef<R> ref);
<C, R> R run(OperationType<C, R> type, Closure<?> configure) throws Exception;
void finishJob(String reason);
```

Die weiteren Fassadenhelper sind `config`, `secret`, `sensitive`, `log`, `report`, `progress`, `cancellation`, `budget`, `resources` und `println`. `TaskConfigurationScope` bindet bekannte InputFields über `getProperty/setProperty`, Actions ergänzen Resultat-/Finishzugriff. `CleanupScope` bietet eine eigene eingeschränkte Oberfläche, keinen Operationstart. Die kanonische Form bleibt `feld = wert`, Stringlinks, Map-first-Überladungen und späte Closureauswertung.

| Quellstelle | Verifizierter Ist-Zustand / Bedeutung für spätere Phasen |
| --- | --- |
| `GroovyJobLoader.java`, `JobDslModel.java`, `GretlScript.java` | ein Builder/Binding/Taskindex pro geladenem Skript, `run()` registriert und anschliessend `freeze()`; noch kein Project-Modell oder `loadProject` |
| `GroovyContextFacade.java` | `file` und Parameter unverändert an inneren Kontext; Stringresultate über lokale Task-/Operationmaps, rohe TaskRef-Überladungen separat |
| `SourceLocations.java` | `current(Path)`, `from(Throwable,Path)`, `fallback(Path)`; Matching bislang über Basename, unbekannte Zeile 0 |
| `gretl-runner/.../JobDefinitionBuilder.java` | eine UUID, `register`, `registerParameter`, `defaults`, `cleanup`, genau ein `freeze`; Vorwärtslinks und unbekannte Defaults dort geprüft |
| `JobDefinition.java`, `ExecutionPlanner.java`, `ResultStore.java` | fremde Definitionen abgewiesen; stabile transitive dependsOn-Hülle und selektierte after-Kanten; Ergebniszugriff über Datenvorgänger |
| `BaseContext.java` | `file` gegen globalen jobRoot, `path` gegen startCwd; keine Unterprojektbasis |
| `gretl-job-runtime/.../job/runtime/JobSource.java`, `ScriptJobSource.java` | tatsächliches Package `ch.so.agi.gretl.job.runtime`; `displayFile`, `jobRoot`, `prepareRun`, `jobName`, `sha256`, `load` bereits vorhanden |
| `JobRuntime.java:138` | privates `jobCommand(JobOptions)` verlangt eine reguläre Datei; danach gemeinsamer `jobCommand(JobSource,JobOptions)`, Parameterresolver und Planner, ein SequentialJobRunner |
| `gretl-packager/.../PackageDescriptor.java` | nur Schemas 1/2, `entryPath`, `sourcePaths`, `resourcePaths`, kein `settingsFile`; Schema-2-Auswahl bleibt erhalten |
| `JobCompiler.java:67`, `JobPackageBuilder.java:256` | Mehrfachskripte ergeben PACKAGE_MULTIPLE_SCRIPTS, gemeinsame isolierte CompilerSettings, Metadaten enthalten Schema 1 und eine entryClass |
| `gretl-job-runtime/.../job/JobJarMain.java:131` | JobMetadata Schema 1; PackagedJobSource lädt eine vorkompilierte Einstiegsklasse und materialisiert Ressourcen nur für Run |
| `gretl-integration-tests/.../it/ArtifactJobRunner.java` | Request ohne sourceKind/scriptInput; Scriptstart fest `source/job.groovy`; regulärer Packager und eigener java-jar-Prozess, JAR-Hash, Prozesslogs, Timeout/Cancel, Assertions ausserhalb des Prozesses |

Die Spezifikation beschreibt den Produktstand weiterhin zutreffend. Die konkreten vollständigen Java-Packages werden aus den tatsächlichen Quellen übernommen; Auslassungszeichen in der Spezifikation sind keine alternativen Pakete. Keine Norm-/Signaturänderung in Phase 0. Neue Parser-/Projekt-/Schema-3-Verträge werden erst in ihren Phasen implementiert.

## Explizites Nulländerungsinventar

| Erhaltener Bereich | Vertrag / vorhandene Regressionen |
| --- | --- |
| `gretl-api` einschliesslich OperationProvider und ContextView | JDK-only SPI, Metadaten, TaskRefs, Lebensdauer und SecretValue; ApiMetadataTest, ApiRuntimeTypesTest, ApiIsolationTest und unabhängiges FileSize-JAR |
| Runnerplanung | ExecutionPlannerTest, JobDefinitionBuilderTest: Fremdreferenzen, Vorwärtslinks, vollständige Datenzyklen, after ohne Selektion, stabile Reihenfolge |
| Ergebnisstore | ResultStoreTest, ContextAccessTest: TaskStatus/ResultSlot, direkter/transitiver Datenvorgänger, fremde Referenzen, Optional verschleiert keine Fehler |
| Runnerausführung | SequentialJobRunnerTest und bestehende Lifecycle-/Cleanup-/Cancellationtests: Fail-fast, NO_DATA, bewusst gefangene Fachfehler, ein Run, keine Retries |
| Dateisystem/Lebensdauer | WorkspaceManager, BaseContext, RunInput, ParameterResolver und bestehende Pfad-/Retentiontests; globale Java-file-Basis bleibt jobRoot |
| Vier Taskmodule | 44 Operationen, Requests/Resultate, Fachdependencies und ServiceProvider unverändert; vorhandene Unit-, lokale JAR- und Dienstfälle |
| Logging und Abschluss | bestehende Logging-/Correlation-/Secret-/Shutdowntests; Fehlerursachen, IDs, Exitcodes 0/2/10/20/40, kein OTel-Zwang |
| CLI und Paketierung | alte Dateiaufrufe, Hilfe/Plan/Check, Schemas 1/2 und internes Metadatenschema 1; keine Mavenpflicht im Jobstart |
| Build und Tests | alle POMs, Wrapper, Workflows, Tests, Fixtures und ausführbaren Beispiele unverändert |

Die externen SHA-256-Fingerprints umfassen 801 getrackte Produkt-/Test-/Builddateien; Git-Diff und neuer Kandidat müssen dieselben Bytes belegen. Bestehende fachliche Assertions werden weder verschoben noch abgeschwächt.

## Prüfverfahren und Testfallmatrix

Die Spezifikation vergibt für Phase 0 keine PRJ-Test-IDs. Die folgenden lokalen P0-IDs ordnen die fünf Arbeiten und Gate 0 aus Abschnitt 8 zu; sie erweitern den Funktionsumfang nicht. Keine neuen Produkt-/Testdateien und keine roten Featuretests in dieser reinen Baselinephase.

| Lokale ID | Orakel / Zuordnung | Vorprüfung |
| --- | --- | --- |
| P0-01 | Anweisungen und vollständige Subprojekt-Spezifikation gelesen; Normgrenzen abgeglichen (§8/0 Arbeit 1) | PASS |
| P0-02 | feste Basis, Toolchain, aktive POM-Module, tatsächliche DSL-Signaturen und Ausgangsabweichung inventarisiert (Arbeit 2) | PASS |
| P0-03 | normaler clean-verify-Reactor unter JDK 25; keine verdeckten Testfehler (Arbeit 3) | PASS |
| P0-04 | API/Runner/Taskmodule, Build, Logging und Altverträge als Nulländerungen inventarisiert (Arbeit 4) | PASS |
| P0-05 | Baseline und Evidence ohne Selbstreferenz vorbereitet (Arbeit 5); feste SHA/Nachtest und Review extern nachzuweisen | PASS für Dokumente; verbindlicher Nachtest-/Reviewstatus ausschliesslich im Handoff/B-Bericht |
| P0-FULL | zusätzlicher GeoTools-/Backing-Service-Ausgangsnachweis bei verfügbarer Umgebung (Arbeit 3) | PASS |
| Gate 0 | unabhängiger PASS zur exakten Kandidaten-SHA, anschliessend unverändert separat archiviert | noch keine Freigabe durch A; ausschliesslich B |

Neue Featurefälle aus Abschnitt 7.2 bleiben ausdrücklich **NICHT AUSGEFÜHRT**:

- `PRJ-001`, `PRJ-002`, `PRJ-003`, `PRJ-004`, `PRJ-005`, `PRJ-006`, `PRJ-007`: spätere Phase, noch nicht implementiert.
- `PRJ-101`, `PRJ-102`, `PRJ-103`, `PRJ-104`, `PRJ-105`, `PRJ-106`, `PRJ-107`, `PRJ-108`, `PRJ-109`, `PRJ-110`, `PRJ-111`, `PRJ-112`, `PRJ-113`, `PRJ-114`, `PRJ-115`: spätere Phase, noch nicht implementiert.
- `PRJ-201`, `PRJ-202`, `PRJ-203`, `PRJ-204`, `PRJ-205`, `PRJ-206`, `PRJ-207`, `PRJ-208`, `PRJ-209`, `PRJ-210`, `PRJ-211`, `PRJ-212`: spätere Phase, noch nicht implementiert.

### Standardlauf vor dem Commit

- `./mvnw -B clean verify` → Exit **0**, 604 Tests, 0 Failures, 0 Errors, 0 Skips. Laufzeit 7:45 Minuten. UTC-Zeitfenster 2026-10-28T17:59:22Z bis 18:07:09Z.
- `python3 scripts/audit-job-evidence.py` → Exit **0**. 96 Prozessnachweise: 82 JAR, 14 SCRIPT, 0 IMAGE. Das Standardgate beansprucht keine vollständige positive Abdeckung der dienstabhängigen Operationen.
- Archiv `E/before-standard/`: `console.log`, `audit.log`, `result.json`, `reports/`, `coverage/`, `qualification/summary.json`, `qualification/jar-executions.json`, `artifacts/`, `SHA256SUMS.json`.
- JUnit-Quellen: ausschliesslich aktive Reactormodule und deren `target/surefire-reports/TEST-*.xml` bzw. `target/failsafe-reports/TEST-*.xml`. Alte Reports des hier inaktiven `gretl-geotools-probe` werden ausgeschlossen. Eine erste vorläufige Summe von 682 enthielt dessen 78 alte Fälle und wurde vor der Evidenzübernahme auf 604 korrigiert; der vorhandene Audit bestätigt die bereinigte Summe unabhängig.
- Paket-/Job-SHA je Ausführung: `qualification/jar-executions.json`, zugehöriges `execution.json`; tatsächlich gestartete JARs liegen daneben unter dem archivierten `artifacts/gretl-integration-tests/target/job-evidence/<Szenario>/job.jar`. Der Audit berechnet deren Bytes erneut. Das externe Manifest sichert die Archivdateien zusätzlich.
- Vor dem Kandidatencommit meldet der Katalog erwartungsgemäss `sourceRevision=phaseBaseSha`, `sourceDirty=true`: die vom Benutzer bereitgestellte Spezifikationsersetzung ist noch nicht committet. Diese Vorprüfung wird nicht als sauberer Kandidatennachweis ausgegeben. Der Nachtest nach dem Commit verlangt über `--source` ausdrücklich Kandidaten-SHA und `sourceDirty=false`.

| Modul | Tests im Standardlauf |
| --- | ---: |
| `gretl-api` | 17 |
| `gretl-cli` | 55 |
| `gretl-database-tasks` | 21 |
| `gretl-dist` | 13 |
| `gretl-groovy` | 49 |
| `gretl-i18n` | 4 |
| `gretl-ilivalidator` | 3 |
| `gretl-integration-tests` | 95 |
| `gretl-interlis-tasks` | 34 |
| `gretl-io-tasks` | 54 |
| `gretl-job-runtime` | 24 |
| `gretl-logging` | 38 |
| `gretl-packager` | 12 |
| `gretl-runner` | 185 |

JaCoCo misst die Test-JVMs; separate Launcher-/JAR-Prozesse sind nicht instrumentiert. `coverage/` enthält die tatsächlichen XML-/HTML-Berichte, keine erfundene Coverage der Kindprozesse. Fachliche Orakel der vorhandenen Suite bleiben unabhängige Datei-/Text-/Datenbankprüfungen sowie konkrete Codes, Ursachen, Ereigniszählungen und Workspacezustände, nicht allein Exitcode 0.

### Vollständiger Lauf vor dem Commit

- `./mvnw -B -Pgeotools-probe,backing-services clean verify` → Exit **0**, **660 Tests**, 0 Failures, 0 Errors, 0 Skips; davon 147 im Integrationstestmodul und 4 GeoTools-Proben. Laufzeit 11:38 Minuten, UTC 2026-10-28T18:07:56Z bis 18:19:35Z.
- `python3 scripts/audit-job-evidence.py --full` → Exit **0**. 174 Prozessnachweise: **160 JAR**, **14 SCRIPT**, **0 IMAGE**. Die vollständige positive JAR-Abdeckung aller **44** katalogisierten Produktoperationen wird vom Audit gegen das Operationeninventar geprüft; zusätzlich erscheinen zwei Test-/Extensionoperationen (insgesamt 46 erfolgreiche IDs).
- Archiv `E/before-full/` mit derselben Struktur wie `before-standard/`; getrennte XML-Dateien, Quellenrevision, Zeitfenster und Prüfsummen. `qualification/summary.json` enthält Distributionsarchive und deren SHA-256; `qualification/jar-executions.json` die individuellen Job-SHAs, Status, Szenarien und Operationen. Das saubere Kandidatengate wird trotz dieser erfolgreichen Vorprüfung nach dem Commit erneut ausgeführt.

## Nachtest und unabhängige Übergabe

Nach dem Kandidatencommit werden Standardgate und zusätzlich auch der vollständige Lauf auf dessen unveränderter SHA wiederholt. Jedes Archiv hat ein eigenes `result.json` mit Befehl, Exitcode, Start-/Endzeit und Git-HEAD. Die Nachtests und Audits erhalten die Verzeichnisse `E/after-standard/` und `E/after-full/`; der Audit verwendet dann `--source <candidateSha>` und bei vollständigem Lauf zusätzlich `--full`.

`E/phase-0-handoff.json` wird erst nach Ermittlung der vollständigen Kandidaten-SHA ausserhalb von Git erstellt. Er verweist auf diesen Bericht, Evidence-Index, Vor- und Nachtests, prüfsummengebundene Logs/XML/JARs und die Spezifikation. Die endgültige Review prüft den gesamten Diff `phaseBaseSha..candidateSha` in einem eigenen detached Worktree. Der getrackte Evidence-Index wird nicht nachträglich für die eigene Commit-SHA editiert. B führt mindestens `./mvnw -B clean verify` selbst aus; nur sein eigener unverändert archivierter PASS-Bericht schliesst Gate 0. Dieser Auftrag endet danach und beginnt keine Phase 1.

## Bekannte Vorbefunde und Grenzen

- Keine nachgewiesenen Produkt-/Testfehler aus beiden ausgeführten Gates; keine Kriterien gelockert, keine Tests deaktiviert, keine Skip-Flags benutzt. Vorprüfung, Kandidatennachtest und unabhängige Review werden separat ausgewiesen.
- Bereits vor Featureänderungen sichtbar: javac-Hinweise zu Serialisierung, deprecated Fachbibliotheks-APIs und unbenutzten try-with-resources-Variablen; JDK-Hinweise auf native DuckDB-/SQLite-Zugriffe; Log4j2-StatusLogger-Fallback im Testprozess. Diese protokollierten Hinweise sind keine neuen Subprojektfehler und werden in Phase 0 nicht durch Änderungen an Dependencies oder Logging behoben.
- Die Distributionsassembly warnt vor einem über 100 Zeichen langen tar-Eintrag und verlangt GNU-kompatibles tar. ZIP und die ausgepackte Distribution bleiben separat geprüft. Keine ungeprüfte allgemeine tar-Portabilitätszusage.
- Das vorhandene gepinnte AMD64-PostGIS-Image läuft auf dem ARM64-Dockerhost in Emulation. PostgreSQL/PostGIS, FTP und S3 bleiben echte lokale Testdienste. Die dokumentierte Floci-Emulation nutzt wie bisher ein nicht digestgepinntes `latest`; keine AWS-Produktionsqualifikation.
- Die neuen Featuretests PRJ-001–007, PRJ-101–115 und PRJ-201–212 werden erst in den Phasen 1–3 ausgeführt; Phase 0 behauptet weder `settings.gretl`-Sicherheit noch Verzeichnisstart oder Projekt-JAR-Unterstützung.
- Kein realer GRETL-IMAGE-Lauf, kein aktueller Linux-CI-Lauf, kein frischer leerer Maven-Cache, keine Fachanwender-/Produktionsfreigabe und kein Windows-/Native-Nachweis in dieser Baseline.
- Gate 0 bleibt bis zum unabhängigen PASS auf der exakten, nachgetesteten Kandidaten-SHA offen. Phase 1 ist nicht begonnen. Bei einem nachgewiesenen Baselinefehler wäre vor Featurearbeit eine ausdrückliche Maintainerentscheidung erforderlich; ein solcher Fehler würde nicht still einer späteren Phase zugeschrieben.
