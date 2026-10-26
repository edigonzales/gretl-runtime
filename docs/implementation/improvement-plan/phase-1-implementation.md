# Phase 1: Implementierungsbericht

Datum: 25. Oktober 2026. Implementierung: Agent A (`/root`). Unabhängige Prüfung: separat zu beauftragen, noch kein PASS.

Ausgangscommit: `230f2882bcdde15f552f7959aed157a0a6301fa6`. Kandidatencommit: `79867a03ada856011ffb2d3dc52da44b78bcb5a1`. Branch: `codex/improvement-plan`.

Der Kandidat wurde nach den Prüfungen aus dem unveränderten geprüften Produkt-/Teststand erstellt. Der Arbeitsbaum ist sauber. Die anfangs vorhandene aktualisierte Planfassung wurde unverändert mitversioniert. Phase 2 und Phase 3 sind noch nicht begonnen.

## Änderungen und Abgrenzung

- CSV verlangt kein tableName; GPKG bietet es als Pflichtfeld. Alle vier Formatvalidatoren akzeptieren Path- und String-Modellverzeichnisse. Ungültige Werte scheitern vor der Engine. Ili2gpkgImport bietet defaultSrsCode über die öffentliche DSL an.
- Db2Db propagiert TaskCancelledException unverändert. RunControl ist für den Shutdown-Thread sichtbar; Job-Cleanup und Shutdown-Warten teilen ein unabhängiges monotones 30-Sekunden-Budget. Der Hook führt keinen zweiten Abschluss aus.
- Runtimekonfiguration und GRETL_CORRELATION_ID liefern die validierte Korrelation mit Vorrang der Konfiguration an Logkontext, JSON-Ereignisse und Bericht. Fehlender Wert wird nicht erfunden.
- Direkte PostgreSQL-Quellspalten mit PostGIS geometry/geography werden vor der DuckDB-Übernahme ausdrücklich mit DUCKDB_GEOMETRY_UNSUPPORTED abgewiesen. Das ist die im Plan erlaubte getestete Ablehnung statt eines unbelegten Geometry-/SRID-Versprechens.
- ArtifactJobRunner paketiert über die reguläre gebaute Distribution und startet separate java -jar-Prozesse ohne Testclasspath. Er kopiert Fixtures frisch, hält Expected-Daten ausserhalb der Outputs und bewahrt SHA-256, Logs, Reports, Dauer und Abbruch-/Timeoutstatus auf. OS-Dateiumleitung erfasst beide Ströme ohne Pipe-Deadlock; erst nach Prozessende wird ausgewertet.
- Kanonische Jobs und unabhängige Datei-/SQLite-/PostgreSQL-Orakel sichern die Phase-1-Szenarien. Vorhandene CLI-Komponententests bleiben erhalten und werden nicht als neue JAR-Nachweise gezählt.
- JaCoCo 0.8.14 erzeugt Modul- und aggregierte Line-/Branch-Berichte. CI führt die beiden Root-Wege aus und sichert Reports/Logs. Es gibt kein Prozentgate und keine Image-Ausführung.
- Originalinventar: 87 Szenariozeilen (86 aktive Testannotationen sowie der historisch auskommentierte FTP-Fall), 386 Fixture-Dateien mit SHA-256. Neue Originalvarianten bleiben bis zur vollständigen Phase-2-Migration ausdrücklich offen. Das 44er-Operationeninventar wird gegen Namen, Provider, Eingaben und Resultatverträge geprüft.

## Umgebung

Temurin 25.0.2+10-LTS (Java-25-Linie des Root-Enforcers), Maven Wrapper 3.10.0, Groovy 6.0.0, macOS 27.0.1 ARM64, UTF-8. Docker Desktop/Engine 29.8.1, Linux ARM64, 6 CPUs und 11.67 GiB. Diese lokale Verifikation behauptet keinen neuen Linux-x86-64-CI-Lauf und keine neue Patchrelease-Qualifikation. Historische M0-Berichte verwendeten 25.0.4.1+1; der hier tatsächlich verwendete Patch ist ausdrücklich dokumentiert. Jobprozesse setzen UTC, UTF-8 und GRETL_LANGUAGE=de; Ambient-GRETL-Identität und JVM-Classpathoptionen werden entfernt.

Originalreferenzen: sogis/gretl `a9222dc0ffd08f8f76570e998101db041822df71`, gretl-next `08bd11e97a54f32f8702d34ff16fb39bec6ea046`. Sie liegen als exakte Referenzstände unter `/tmp/gretl-improvement-evidence/`. Toolchain-/Arbeitsstandprotokoll: `environment.txt`.

## Tatsächliche Prüfungen

| Befehl aus Repository-Root | Ergebnis | Tests | Failures / Errors / Skips |
|---|---|---:|---|
| `./mvnw -B clean verify` | BUILD SUCCESS | 412 | 0 / 0 / 0 |
| `./mvnw -B -Pgeotools-probe,backing-services clean verify` | BUILD SUCCESS | 446 | 0 / 0 / 0 |

Logs: `/tmp/gretl-improvement-evidence/phase1/verify.log` und `profiles.log`. Die XML-Reports und Artefaktnachweise sind vor dem jeweils nächsten clean unter `standard/` beziehungsweise `profiles/` gesichert. Gezählt werden nur die tatsächlich aktiven Reactormodule; alte GeoTools-targets aus einem früheren Profillauf sind kein Bestandteil eines Standardlaufs.

| Modul | Standard | Vollständiges Profil |
|---|---:|---:|
| gretl-api | 17 | 17 |
| gretl-runner | 116 | 116 |
| gretl-groovy | 49 | 49 |
| gretl-i18n | 4 | 4 |
| gretl-logging | 18 | 18 |
| gretl-packager | 8 | 8 |
| gretl-basic-tasks | 51 | 51 |
| gretl-core-tasks | 23 | 23 |
| gretl-cli | 62 | 62 |
| gretl-ilivalidator | 3 | 3 |
| gretl-interlis-tasks | 25 | 25 |
| gretl-dist | 11 | 11 |
| gretl-integration-tests | 25 | 55 |
| gretl-geotools-probe | 0 | 4 |

Entwicklungsprüfungen: `./mvnw -B -pl gretl-dist -am package`, Generierung der AsciiDoc-Referenz über `gretl-dist/target/dist/bin/gretl operations --format adoc --lang de --output docs/reference/generated`, anschliessend `./mvnw -B verify` und `./mvnw -B -Pgeotools-probe,backing-services verify`. Ein Metadatentest schlug zunächst wegen nicht garantierter Providerreihenfolge fehl; der Vergleich erfolgt nun nach Operation-ID. Die unveränderte Baseline hatte einen FTP-Cleanup-Verbindungsfehler (416 Tests, ein Error). Die späteren vollständigen Läufe bestanden; die Fehlerursache wird nicht als behoben behauptet, zusätzliche FTP-Serverdiagnostik wird im Fehlerfall gesichert. Ein früherer sauberer Standardlauf wurde nach der letzten Härtung der Testprozess-Umgebung wiederholt. Keine Pflichtprüfung wurde deaktiviert oder durch einen Skip ersetzt.

Git-Prüfung: `git -c core.whitespace=cr-at-eol diff --cached --check`. Das Original-XML behält absichtlich seine CRLF-Bytes; die byteidentische Archivprüfung würde eine Normalisierung des Fixtures nicht als unveränderte Originaldatei ausgeben.

## Kriterienzuordnung

Die ausführbare Zuordnung steht in `requirement-tests.md`; diese Einschätzung von Agent A ersetzt nicht Agent Bs Urteil.

| ID | Nachweis bei Agent A |
|---|---|
| P1-01 | baseline.md; operation-inventory.json; OperationMetadataTest; Original-/Kompatibilitätsmatrix |
| P1-02 | ValidatorLocalJobIT: CSV ohne Tabelle, GPKG mit Tabelle, falsche/fehlende Tabelle, CSV weist Tabelle zurück |
| P1-03 | ValidatorLocalJobIT: CSV/JSON/GPKG/SHP mit Path und String; ungültiger Repositorywert vor Enginearbeit |
| P1-04 | ValidatorLocalJobIT: echte Ili2gpkgImport-JARs; unabhängiges SQLite-Orakel für 2056/21781 und Fachzeilen |
| P1-05 | DatabaseArtifactIT: aktive PostgreSQL-Abfrage als Checkpoint, SIGTERM, CANCELLED/20, Rollback, NOT_RUN, keine offene Transaktion; erhaltene FailureArbiter-Tests |
| P1-06 | CancellationLocalJobIT: Script und JAR, Interrupt und SIGTERM, genau ein Cleanup/Runterminal, JSON-Bericht; vorheriger Fehler bleibt FAILED/10; RunControlTest prüft gemeinsames unabhängiges Budget |
| P1-07 | CorrelationTest; vorhandene LoggingPipelineTest-/RuntimeConfigTest-/Redaction-Regressionen; LogContext und RunReportWriter |
| P1-08 | ValidatorLocalJobIT und DatabaseArtifactIT; DUCKDB_GEOMETRY_UNSUPPORTED mit unverändertem PostGIS-Ausgang; generierte Referenz und kanonische Jobs |
| P1-09 | JaCoCo-Berichte in Modul-targets und jacoco-aggregate; Messgrenze dokumentiert; Subprozesse ausdrücklich uninstrumentiert |
| P1-10 | Zwei vollständige Root-Prüfbefehle; gesicherte XML-Reports; unabhängiger Prüfbericht mit PASS erforderlich |
| P1-11 | original-scenarios.json/.md: fester Originalcommit, Methoden und Assertionen, Fixture-Hashes und Scope; Abgleich des Fortschritts im Implementierungsbericht |
| P1-12 | ArtifactJobRunner; jobs/archive, validators, gpkg-import, database; externe Prozesse ohne Testclasspath; unabhängige Datei-/JDBC-Orakel und execution.json mit JAR-SHA-256 |

## Coverage: Messwerte getrennt von Testzahlen

Quelle: `profiles/gretl-integration-tests/site/jacoco-aggregate/jacoco.xml`, neben den Modulberichten. Spalten nennen gemessene Zeilen/Branches als covered / total.

| Produktmodul | Line covered / total | Branch covered / total |
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
| gretl-core-tasks | 1099 / 1382 | 288 / 488 |
| gretl-ilivalidator | 115 / 175 | 20 / 42 |
| gretl-interlis-tasks | 2821 / 3664 | 1086 / 1819 |

Surefire- und Failsafe-Test-JVMs werden instrumentiert. Separat gestartete Launcher, Packager und Job-JVMs werden **nicht** instrumentiert; ihre Fachprüfungen zählen nicht als gemessene Coverage. gretl-job-runtime besitzt daher einen transparenten Nullwert im Messbericht, obwohl es in den JAR-Prozessen ausgeführt wird. Der gemeinsame Aggregatbericht ordnet die verfügbaren Test-JVM-Beiträge den Produktklassen zu. Dynamisch erzeugter Jobbytecode ist kein Produktmodul und erhält keinen erfundenen Messwert.

## Jobartefakte und Ergebnisse

Jeder nachfolgende Lauf hat eine frische Arbeitskopie und einen eigenen Outputbereich. Das Original-XML wird byteweise verglichen; CSV-Fehler benötigen die beiden originalen fachlichen Diagnosen. GeoPackage wird über eine eigene SQLite-Verbindung gelesen; 2056 und 21781 prüfen die Übertragung des expliziten SRS-Codes, keine Koordinatentransformation. PostgreSQL-Zustände und Rollback werden unabhängig über JDBC geprüft. Db2Db-Abbruch synchronisiert über die tatsächlich aktive Abfrage in pg_stat_activity; SIGTERM-/Action-Fälle verwenden WatchService-Dateicheckpoints. Ein erwarteter fachlicher Fehler benötigt Status und konkrete Diagnose; beliebige Startfehler erfüllen das Orakel nicht.

Belegwurzel: `/tmp/gretl-improvement-evidence/phase1/profiles/job-evidence/`. Pro Verzeichnis liegen `execution.json`, `report.json`, getrennte Prozesslogs, der tatsächlich gestartete JAR-Bestand sowie relevante Outputs. SCRIPT-Zeilen nennen den tatsächlich geladenen Script-SHA-256; das daneben gebaute JAR wird dabei nicht als gestartetes Artefakt ausgegeben.

| Szenario | Modus | Verzeichnis | Tatsächliche Artefaktidentität (SHA-256) | Exit / Timeout |
|---|---|---|---|
| cancel | JAR | cancel-false-false-0f534e6e-6839-4c4b-8ff6-23318c11b7cd | `f344506b4cea92e8a91ab08fe424185e5df54118a567ffcc0f71511fa134fed7` | 20 / False |
| cancel | JAR | cancel-false-true-3025f2e9-73e8-4e0e-9d83-95cb87c54aff | `d5622f0ff2825f24440b5826a118e248bf86cd4409b7e46f3c61fc3d069b1733` | 143 / False |
| cancel | SCRIPT | cancel-true-false-e18f0962-85d9-4ad3-a024-2dfdfb5e2c7c | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 / False |
| cancel | SCRIPT | cancel-true-true-dc21df20-669d-433f-b68f-79f1a4c95961 | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 / False |
| csv-unsupported-table | JAR | csv-unsupported-table-6c3b69df-10b5-4c6a-9650-586c3d5cfa64 | `fd49f1b3a454278666456496f1e4005a661da3f14baf9c6177a490fee528c6db` | 10 / False |
| database | JAR | database-cancel-969bd5cc-f751-463c-9dc5-ceb606e34006 | `629e3ee83e1018005ebf6d8141cf8961472ca618488a3df5694eb8698df09277` | 143 / False |
| database | JAR | database-chain-d8a7ea75-7cf1-4f79-b21e-f2315b4419f4 | `9770548c61678611aa21b9cce5971f6a8f99f695beef5d3541625a6e3b3a5e7f` | 0 / False |
| failed-shutdown | JAR | failed-shutdown-false-f7aad872-b847-4821-8961-9597800d5ff2 | `87fca24079103ef547dc9ad6fbe78ba4722fbbd49767af2efb7a2ce7b6cf581f` | 143 / False |
| failed-shutdown | SCRIPT | failed-shutdown-true-7f14a2b0-3126-4dad-b56a-d6374e4d8615 | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 / False |
| gpkg-missing-table | JAR | gpkg-missing-table-dd303f37-7569-4010-ae64-c5fc7c02251d | `b8495695b1d72c5c9909606097f0a6b828bad3e8dea6c939dd6f4664b59c7716` | 10 / False |
| gpkg-srid | JAR | gpkg-srid-1d7765c9-2c81-429b-8e89-65f37a37b989 | `bc9043e67eba3a9d2f9fa27a5009e3c3d99557b39fc1696240d5880770da535c` | 0 / False |
| gpkg-srid | JAR | gpkg-srid-e8d0f48f-4825-4765-9212-3b32cc0e5aa7 | `b6f463b0ff70eaf6efa296c1f3afe9591b191902cea8a939086c117338bd05f0` | 0 / False |
| invalid-csv | JAR | invalid-csv-eee310c8-1912-49b2-b044-2e064777c61f | `736dc460a0c03c6c323af394e5b8bab90933a2621d9c5ebcd936c50601c78960` | 10 / False |
| invalid-model-repository | JAR | invalid-model-repository-ff1ded66-56af-4eb2-8407-26aec88599e5 | `2ef46578d570dc933d257e3b424aaca93191700a1765a7599470cf435974bcf5` | 10 / False |
| original-gzip | JAR | original-gzip-dd156f39-3661-4a03-babb-ed86f8cec8ae | `d217e6ed929fa9c926798d17a95de9bc05aef9cf4cda1bee296a6eb44b91a8be` | 0 / False |
| database | JAR | postgis-geometry-contract-306fbace-8720-433a-a6cb-f9f3c88c930c | `58dc4560039bfe0e5cbe739c07268f55f6d7c2e3db03495954bb4f62e9f1e3b2` | 10 / False |
| runner-timeout | JAR | runner-timeout-false-607a71e2-8ef9-4b8a-82dc-7ee55ad4f8ff | `cf260c4984053774bea7a1766b2800e828dc9e363222e0a041c52e995b5c5b96` | 143 / True |
| runner-timeout | SCRIPT | runner-timeout-true-a9675b27-83b2-498e-960b-48b350532b2f | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 / True |
| valid-csv-path | JAR | valid-csv-path-4d63fa3d-d6ed-4fb6-86b8-a80d98ff40af | `6c3359672e754c8417f8392fc1dc15c4f697c4b0aa3e2424197bf928aa2acbd2` | 0 / False |
| valid-csv-string | JAR | valid-csv-string-465ccfcc-b8ac-4ff5-8f07-4975debf0505 | `24df047b765eb1fa45ed61c7017511aa87780d65fbaa0dde28a5a816d87730f9` | 0 / False |
| valid-gpkg-path | JAR | valid-gpkg-path-42dba081-90c4-46be-8028-d2b66ed8d306 | `db3c8b269fcc14141ed33706350bb2a0a63d7353f22fe7313132c5074bb57f81` | 0 / False |
| valid-gpkg-string | JAR | valid-gpkg-string-07db6644-e50c-45ec-a127-6103661c057c | `9dd5d0dd20a25092e8ca5208cb2c56aef36608da83b5a8b2f78f7d4d568b0a36` | 0 / False |
| valid-json-path | JAR | valid-json-path-69a77bd2-88ef-4785-8e54-6f05bc8ca917 | `35ed882712ac70568202ebf48d15c409c3ed74e8d95873aff98c58b1e0233a55` | 0 / False |
| valid-json-string | JAR | valid-json-string-34f8f5d1-2eec-4388-b654-f9a0465c74da | `4290392edc0a39c57122e994790014e86cb7f584668eafec8031e5493a773a3d` | 0 / False |
| valid-shp-path | JAR | valid-shp-path-c3e05388-8c06-4aa3-964b-61a58c77bd37 | `d6d3d2c8d7b3bbaaaf2859bc38773b5ec0b9c08609caffeffedb7c8a522c889b` | 0 / False |
| valid-shp-string | JAR | valid-shp-string-0488f93a-18a7-4b02-8fab-2f4b0a9e399b | `d0467d622baf4cb995c801e894c32424fbf17208510f1afd7cefc26574d73175` | 0 / False |
| wrong-gpkg-table | JAR | wrong-gpkg-table-ed869e06-ea20-4d91-91f6-9abdadb81626 | `e8224c0909a308a439ae2f2dab04bf033621f6e3ef8dc92313aaf01600c11889` | 10 / False |

Dienstendpoints stehen je Lauf in `execution.json`; Passwörter liegen nicht in der Endpunktliste oder Prozessargumenten. PostgreSQL/PostGIS-, FTP- und S3-Testcontainer wurden im Profil tatsächlich ausgeführt. Zusätzliche vorhandene Launcher-/Packager-/CLI-Nachweise sind in ihren eigenen XML-Reports erhalten.

## Status und noch nicht beauftragte Folgearbeit dieser Phase

JAR: **Phase-1-Szenarien fachlich verifiziert**. Der vollständige 44er-JAR-Bestand ist das nachfolgende Phase-2-Gate und wird hier nicht als erfüllt ausgegeben.

IMAGE: Der gemeinsame Request-/Resultatvertrag berücksichtigt spätere Endpoints und Konfiguration. Der ausführbare Image-Adapter ist Phase 2 und hier noch nicht fertig. Ein ausdrücklich gewähltes IMAGE fällt aktuell klar aus, ohne JAR-Fallback. GRETL-Image tatsächlich ausgeführt: **nein**.

Unabhängige Phase-1-Abnahme: **offen**, bis Agent B diesen Kandidaten mit PASS bewertet hat. Erst danach darf Phase 2 beginnen. gretl-control-*, Publisher, neue GeoTools-Fachtasks, Native und produktive Jobs wurden nicht verändert oder ausgeführt.
