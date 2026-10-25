# Unabhängiger Prüfbericht – Phase 1

Phase: 1 – Funktion und Nachweise absichern  
Prüfdatum: 25. Oktober 2026  
Implementierungsagent: Agent A (`/root`)  
Unabhängiger Prüfagent: Agent B (`/root/phase1_review`), keine Implementierungsbeteiligung  
Ausgangscommit: `230f2882bcdde15f552f7959aed157a0a6301fa6`  
Geprüfter Kandidatencommit: `79867a03ada856011ffb2d3dc52da44b78bcb5a1`  
Eigener Checkout: `/tmp/gretl-phase1-review-79867a0`  
Eigene Nachweise: `/tmp/gretl-phase1-review-evidence/`

**Urteil: FAIL.** Die beiden vorgeschriebenen eigenen Builds bestehen. Das in der öffentlichen Hilfe von `Ili2gpkgImport` angebotene Beispiel verletzt jedoch P1-08: Es setzt ein nicht angebotenes Feld und scheitert unmittelbar. Es gibt keine Freigabe für Phase 2. Dieses Urteil gilt ausschliesslich für den genannten Kandidaten; eine spätere Korrektur ist darin nicht enthalten.

## Prüfstand und Vorgehen

HEAD und sauberer Arbeitsbaum wurden vor der Prüfung selbst kontrolliert. Der Prüfer hat keine Produkt- oder Testdateien geändert. Eigene Prüfprogramme, Arbeitskopien und Outputs liegen ausschliesslich im Nachweisverzeichnis. Ein von einem eigenen CLI-Aufruf erzeugtes `gretl-reports`-Verzeichnis wurde vollständig dorthin verschoben; der Kandidatenarbeitsbaum ist zum Abschluss sauber.

Gelesen und abgeglichen wurden `AGENTS.md`, `docs/spec/AGENTS.md`, der vollständige aktualisierte Umsetzungsplan mit 34 Kriterien, die einschlägigen Verträge aus 03/04/06/07/09/12/14/15/16 sowie Zielbild, Einstieg und Implementierungshinweise. Das vollständige Kandidatendiff, alle geänderten Produktklassen, neue Testklassen, Fixtures, POM-/CI-Änderungen und die Dokumentationszuordnung wurden geprüft. Der Implementierungsbericht wurde nur zur Orientierung verwendet.

Der Ausgangscommit unterscheidet sich von `e668a51b9589fbea9a1e003c8dd18e28726bd430` ausschliesslich durch den ursprünglichen Plan. Die Produktbaseline ist identisch. Ihre gesicherten XML-Dateien wurden selbst ausgewertet: Standard **384 / 0 / 0 / 0**, Profile **416 / 0 / 1 / 0** (Tests / Failures / Errors / Skips). Der damalige FTP-Cleanupfehler wird nicht als behobener Produktfehler ausgegeben. Quellen der vorliegenden Feld-/Abbruchfehler wurden zusätzlich direkt aus Git nach `baseline-sources/` gesichert. Sie belegen die falsche CSV/GPKG-Feldzuordnung, den nicht pfadfähigen Validator-Konverter, das nur im Schema-Zweig vorhandene SRS-Feld, den breiten Db2Db-Catch und den nicht wartenden Hook.

## Prüfumgebung und eigene Befehle

Temurin OpenJDK **25.0.2+10-LTS**, Maven Wrapper **3.10.0**, Groovy **6.0.0**, macOS **27.0.1 ARM64**, UTF-8, Hostlocale en_US und Europe/Zurich. Docker Desktop **4.93.0**, Engine **29.8.1**, Linux ARM64. PostgreSQL/PostGIS wird über das bereits festgelegte AMD64-Image unter Emulation verwendet. Die Produkt-Jobprozesse setzen UTC und deutsche Sprache; der Runner entfernt ambienten Testclasspath und JVM-Injektionsoptionen. Exakter Umgebungsbeleg: `environment.txt`.

Alle Shellbefehle liefen mit dem Review-Checkout als Arbeitsverzeichnis. Die Pflichtbefehle wurden selbst ausgeführt:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Der erste Lauf wurde vor dem zweiten `clean` mit `python3 /tmp/gretl-phase1-review-evidence/preserve.py standard` gesichert. Der zweite wurde entsprechend nach `profiles/` gesichert. Vollständige Logs: `standard.log`, `profiles.log`; XML, HTML/XML-Coverage und gestartete JARs/Prozesslogs/Reports/Outputs liegen getrennt in `standard/` und `profiles/`.

Weitere eigene Prüfprogramme wurden tatsächlich ausgeführt:

```sh
python3 /tmp/gretl-phase1-review-evidence/independent-probes.py
python3 /tmp/gretl-phase1-review-evidence/geometry-probe.py
python3 /tmp/gretl-phase1-review-evidence/database-probe.py
python3 /tmp/gretl-phase1-review-evidence/cancellation-probe.py
python3 /tmp/gretl-phase1-review-evidence/audit_inventory.py
python3 /tmp/gretl-phase1-review-evidence/audit_original_assertions.py
python3 /tmp/gretl-phase1-review-evidence/audit_artifacts.py standard profiles
python3 /tmp/gretl-phase1-review-evidence/audit_coverage.py
```

Die eigenen Harnesses wurden vor ihrer abschliessenden Auswertung korrigiert: Reportstatus liegt unter `execution`, das Original-XTF enthält zwei BoFlaechen-Zeilen mit fehlender optionaler Geometrie, GPKG/WKB kann Big Endian verwenden, PostgreSQL muss nach dem Init-Neustart TCP-bereit sein und Db2Db-Fachcodes werden nicht als allgemeine `FailureInfo.code` serialisiert. Die zwischenzeitlichen Harnessfehler sind keine als bestanden versteckten Produktprüfungen; sie bleiben in den eigenen Arbeitsdateien nachvollziehbar. Massgeblich sind die unten bezeichneten abgeschlossenen Nachweise in `independent2/`, insbesondere `database3/`.

## Je Abnahmekriterium

| ID | Ergebnis | Eigener Nachweis und Bewertung |
|---|---|---|
| P1-01 | PASS | Baseline aus Git und archivierten XML selbst abgeglichen; `operation-inventory.json` enthält alle 44 Operationen mit Provider-, Input- und Resultatverträgen. `OperationMetadataTest` besteht in beiden eigenen Läufen. Kompatibilitätsmatrix mit gretl-next-Referenz `08bd11e97a54f32f8702d34ff16fb39bec6ea046` abgeglichen: schmaler IliValidator und PreparedStatement-/Scriptvertrag des SqlExecutor sind ausdrücklich getrennt von alten nicht zugesagten Optionen. |
| P1-02 | PASS | `ValidatorLocalJobIT`: CSV ohne Tabelle, GPKG mit Tabelle, fehlende und falsche Tabelle sowie unzulässiges CSV-Feld laufen als echte JARs. Eigene zusätzliche leere Tabelle ergibt `INVALID_CONFIGURATION_VALUE` für `tableName`; eigene andere fehlende Tabelle erscheint in der fachlichen Validierungsdiagnose. Belege `independent2/table-negatives/`. |
| P1-03 | PASS | Vier Validatorarten führen Path- und String-Repositories erfolgreich aus. Integerwert wird vor Enginearbeit abgewiesen. Eigenes Gegenbeispiel verwendet einen Repository-String mit Steuerzeichen: `modelDirectories`-Konfigurationsfehler, kein Engine-Validierungslog. Belege `independent2/repository-control/`. Gemeinsamer Konverter erhält zulässige Repositorystrings statt sie in lokale Pfade umzudeuten. |
| P1-04 | PASS | Zwei regulär paketierte JAR-Jobs mit 2056/21781; SQLite liest `gpkg_geometry_columns` und importierte Fachzeilen. Eigene Wiederholung prüft die konkreten 2 BoFlaechen- und 3 GebaeudeArt-Zeilen; ungültiger SRS-Text scheitert fachlich. Zusätzlicher eigener XTF-Import mit echter Geometrie wird unabhängig binär gelesen: GPKG-Header-SRID 2056 und alle fünf erwarteten Polygonvertices erhalten. Belege `independent2/srs-probes/` und `real-geometry/geometry-content.json`. Keine Behauptung einer Koordinatentransformation. |
| P1-05 | PASS | `DatabaseArtifactIT` synchronisiert an tatsächlicher aktiver PostgreSQL-Abfrage, prüft CANCELLED/20, NOT_RUN, unveränderten Zielinhalt und fehlende offene Transaktion. Eigene zusätzliche Datenbankprüfung kontrolliert vorher/nachher exakt `1/red`, `2/Grün`, danach null verbleibende Sessions. Echter SQL-Fehler bleibt FAILED/10 und erhält die PostgreSQL-Ursache. Beleg `independent2/database3/oracle.json`. Der spezifische Cancellation-Catch liegt vor dem allgemeinen RuntimeException-Catch. |
| P1-06 | PASS | Eigener kompletter Lauf von `CancellationLocalJobIT` für Script und JAR: kooperativer Interrupt, SIGTERM, Timeout/Reaping und vorheriger echter Fehler. Run-/Cleanup-Terminalereignisse jeweils einmal; OS143 wird von GRETL20 getrennt. Eigene vier zusätzliche Prozesse lassen Cleanup absichtlich scheitern: primärer Cancel bleibt CANCELLED/20, vorheriger echter Fehler bleibt FAILED/10; alle vier OS143, Abschluss in weniger als 0,04 s nach Signal. `RunControlTest` belegt das gemeinsame unabhängige monotone 30-s-Budget. Hook wartet nur auf den bestehenden Abschluss, kein zweites Cleanup. Belege `independent2/cancellation-negative/`. |
| P1-07 | PASS | `CorrelationTest` plus erhaltene Logging-/Redaction-/Konfigurationstests bestehen. Eigene echte JAR-Prozesse prüfen Konfigurationsvorrang auch vor ungültigem ENV-Fallback, Jobname, ENV-Fallback, neuen Lauf ohne Korrelation, Steuerzeichenablehnung vor Runbeginn und verworfenen Provider-Überschreibversuch. Top-level `correlation_id`, Kontextattribut und Reportidentität sind konsistent; keine erfundenen Tracefelder. Belege `independent2/correlation/`. |
| P1-08 | FAIL | Die öffentlichen DSL-/Validator-/DB-Pfade und unabhängigen Fachorakel bestehen. Die PostGIS-Geometrieablehnung wird im eigenen Profillauf geprüft; eigene Geography(Point,4326)-Probe bestätigt dieselbe konkrete Diagnose und unveränderte Koordinate/SRID. Die geforderte Konsistenz der Hilfe ist jedoch durch **F1** verletzt: `Ili2gpkgImport` zeigt ein nicht ausführbares Beispiel mit unbekanntem Feld `database`. |
| P1-09 | PASS | JaCoCo 0.8.14 erzeugt reproduzierbare Line-/Branchberichte und Aggregatgruppen für alle 13 produktiven Module. Eigene XML-Auswertung siehe Coverage-Tabelle und `coverage-summary.json`. Test-JVMs sind instrumentiert; Launcher-/Packager-/Job-JVMs sind ausdrücklich uninstrumentiert. Keine Anrechnung dieser Fachprüfungen als Coverage und keine künstliche Prozenthürde. |
| P1-10 | FAIL | Standard- und vollständiger Profilbuild bestehen selbst ausgeführt mit den unten ausgewiesenen Testzahlen und ohne Skips. Die zusätzliche Bedingung eines unabhängigen PASS kann wegen F1/P1-08 für diesen Kandidaten nicht erteilt werden. Kein Pflichtfall wurde deaktiviert oder wegen Docker übersprungen. |
| P1-11 | PASS | Eigener Originalabgleich: alle 386 Fixture-Dateien mit korrektem SHA-256/Byteumfang; alle 86 aktiven Testmethoden zugeordnet, zusätzlich der ausdrücklich als inaktiv beschriebene FTP-Fall; alle 205 Originalassertionen stimmen im Inventar überein. Die 22 übernommenen Validator-Dateien sind entweder byteidentisch (11) oder nur in Whitespace/JSON-Formatierung verschieden (11). Original-Gzip-XML ist byteidentisch. Noch offene Varianten sind klar Phase 2 zugeordnet; Scopeausnahmen sind konkret begründet. Nicht blockierende Metadateninkonsistenz D1 siehe unten. |
| P1-12 | PASS | Kanonische `jobs/archive`, `validators`, `gpkg-import`, `database`; regulärer Kandidaten-Packager, echte separate `java -jar`-Starts, frische Outputs, Dateiumleitung beider Ströme und Auswertung nach Prozessende. SHA-256, Manifest, Argumentlisten und sämtliche Produktklassen aus 11 Laufzeitmodulen in allen durch `ArtifactJobRunner` gestarteten JARs selbst abgeglichen. Datei-/SQLite-/JDBC-Orakel unabhängig vom Jobprozess. Eigene absichtlich falsche Expected-Bytes und fehlende Actual-Datei werden nach Exit0 zurückgewiesen; beschädigtes JAR mit OS1 erfüllt den fachlichen CSV-Negativfall nicht. `artifact-audit.json` und `independent2/archive-oracle/`. |

## Finding F1 – Öffentliches Ili2gpkgImport-Beispiel ist ungültig

Pflichtbezug: P1-08, Phase-1-Arbeit 2; zusätzlich DSL-007 und OPS-010.  
Codebeleg: `gretl-interlis-tasks/src/main/java/ch/so/agi/gretl/tasks/formats/Ili2DbOperations.java:135`.

Die gebaute Distribution zeigt bei `operations Ili2gpkgImport --lang de` folgendes Beispiel:

```groovy
task('run', Ili2gpkgImport) {
    database = 'db'
}
```

Für GPKG ist `database` kein angebotenes Feld; erforderlich sind `databaseFile` und geeignete Transferdaten. Das unveränderte Hilfebeispiel wurde mit `--target run` über den öffentlichen Startpfad ausgeführt:

```sh
gretl-dist/target/dist/bin/gretl run /tmp/gretl-phase1-review-evidence/help-example/job.groovy --target run --report /tmp/gretl-phase1-review-evidence/help-example/report.json --work-dir /tmp/gretl-phase1-review-evidence/help-example/work --log-format json
```

Tatsächliches Ergebnis: OS10, Run FAILED/10, `UNKNOWN_CONFIGURATION_FIELD`, Feld `database`; keine fachliche Engineausführung. Hilfe, Jobquelle, stderr, Exitstatus und Bericht sind unter `help-example/` gesichert.

Notwendige Korrektur: GPKG-spezifisches, mit passenden Eingaben ausführbares Beispiel anbieten und das ausgegebene `OperationType.example()` selbst über den öffentlichen Artefaktweg prüfen. Der erfolgreiche vorhandene Fixture-Job ersetzt dieses Hilfeorakel nicht.

## Tests, Regressionen und Dienstnachweise

| Eigener Rootlauf | Tests | Failures | Errors | Skips | Ergebnis |
|---|---:|---:|---:|---:|---|
| Standard | 412 | 0 | 0 | 0 | BUILD SUCCESS |
| geotools-probe,backing-services | 446 | 0 | 0 | 0 | BUILD SUCCESS |

Die Zählung stammt aus `TEST-*.xml` der aktiven Module, ohne doppelte Zählung von Failsafe-Zusammenfassungen und ohne alte GeoTools-targets im Standardlauf.

| Modul | Standard | Vollprofil |
|---|---:|---:|
| gretl-api | 17 | 17 |
| gretl-basic-tasks | 51 | 51 |
| gretl-cli | 62 | 62 |
| gretl-core-tasks | 23 | 23 |
| gretl-dist | 11 | 11 |
| gretl-geotools-probe | 0 | 4 |
| gretl-groovy | 49 | 49 |
| gretl-i18n | 4 | 4 |
| gretl-ilivalidator | 3 | 3 |
| gretl-integration-tests | 25 | 55 |
| gretl-interlis-tasks | 25 | 25 |
| gretl-logging | 18 | 18 |
| gretl-packager | 8 | 8 |
| gretl-runner | 116 | 116 |

PostgreSQL/PostGIS, FTP und S3/Floci wurden im eigenen Profil tatsächlich verwendet. `DatabaseArtifactIT`, `Db2DbPostgresIT`, `DuckDbFederationPostgresIT`, `PostgresSqlExecutorIT`, Format-/ili2db-Datenbankfälle sowie `FtpTaskFamilyIT` und `S3TaskFamilyIT` bestehen mit unabhängigen JDBC-/FTP-/S3-Clients. Die FTP-/S3-Nachweise stammen in Phase 1 aus den erhaltenen CLI-Komponententests; ihr vollständiger JAR-Port ist Phase 2. Kein GRETL-Produktimage wurde gestartet. Die eigene zusätzliche PostgreSQL-Instanz ist mit Image und Container-ID in `independent2/database3/container.txt` dokumentiert und nach den Prüfungen gestoppt.

Frühere Phasen dieses Verbesserungsplans existieren noch nicht. Die vorhandenen Runtime-, Groovy-, Logging-, Provider-, Distributions- und Fachtests bleiben im Reactor erhalten und wurden hier erneut ausgeführt. Die GeoTools-Probe ist der gesonderte technische Nachweis und wird nicht mit dem ili2gpkg-Nachweis verwechselt. Es wird keine neue Linux-x86-64- oder Produktionsfreigabe aus dem lokalen macOS-Lauf abgeleitet.

## Coverage-Messumfang und Grenzen

Quelle der folgenden eigenen Auswertung: `profiles/gretl-integration-tests/site/jacoco-aggregate/jacoco.xml`. Werte: covered / total.

| Produktmodul | Lines | Branches |
|---|---:|---:|
| gretl-api | 110 / 114 | 23 / 26 |
| gretl-runner | 1796 / 2078 | 754 / 1061 |
| gretl-groovy | 498 / 733 | 148 / 238 |
| gretl-i18n | 24 / 31 | 13 / 20 |
| gretl-logging | 923 / 1103 | 384 / 590 |
| gretl-packager | 508 / 698 | 204 / 310 |
| gretl-job-runtime | 0 / 150 | 0 / 52 |
| gretl-dist | 20 / 26 | 5 / 8 |
| gretl-cli | 1080 / 1326 | 379 / 561 |
| gretl-basic-tasks | 2058 / 2647 | 732 / 1240 |
| gretl-core-tasks | 1099 / 1382 | 288 / 488 |
| gretl-ilivalidator | 115 / 175 | 20 / 42 |
| gretl-interlis-tasks | 2821 / 3664 | 1086 / 1819 |

`standard/` enthält den ersten unabhängigen Berichtssatz. `coverage-summary.json` enthält beide Auswertungen. Surefire und Failsafe schreiben ihre Test-JVM-Beiträge; das Aggregat ordnet diese den Produktklassen und Quellen zu. Die separate Job-/Launcher-/Packagerausführung und eigener dynamischer Jobbytecode werden nicht gemessen. Besonders `gretl-job-runtime` steht deshalb transparent auf 0/150 Lines und 0/52 Branches; seine erfolgreiche JAR-Ausführung ist ein fachlicher Prozessnachweis, kein gemessener Coveragewert. Testanzahl, Fachorakel und Coverageprozente sind getrennte Aussagen.

## Jobartefakte, tatsächliche Startidentitäten und Ausgaben

Die folgende Liste stammt aus selbst erzeugten `execution.json`, abgeglichen mit den tatsächlichen Bytes. JAR-Zeilen nennen die wirklich gestartete JAR-Prüfsumme; SCRIPT-Zeilen die geladenen Scriptbytes, kein daneben lediglich gebautes JAR. Die tatsächlichen Kommandoargumente stehen in `command.json`, die Dienstendpoints ohne Secrets ebenfalls in `execution.json` beziehungsweise `artifact-audit.json`. Für jeden Lauf bleiben Report, getrennte Prozesslogs, Quellkopie und relevante Outputs unter dem genannten Buildverzeichnis erhalten.

| Build | Belegverzeichnis unter job-evidence | Modus | SHA-256 | OS-Exit | Timeout |
|---|---|---|---|---:|---|
| standard | cancel-false-false-dab9fd57-1d68-49bf-874b-f4c859b54829 | JAR | `b12d2db3e9ad7844c9e398e313f2f33c611f0a6e644ff98e5625ebda51cd3a7a` | 20 | False |
| standard | cancel-false-true-21696f19-2caa-4ff3-961b-540b420df01e | JAR | `b92650eb2ec50dadc9631cbcd32d7bdfad75767f3863033ecf545d6f48eb8607` | 143 | False |
| standard | cancel-true-false-f60ec240-489d-4af5-90ed-2e5afec3d6fe | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 | False |
| standard | cancel-true-true-b1f93c2b-86df-483e-9a99-b1d52df1309b | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| standard | csv-unsupported-table-effc1825-1f59-4c3a-9293-fe70875a25f4 | JAR | `03f38f1a878a291b7b0700facb27b6f44149344c534e98b4ef2002df923d5157` | 10 | False |
| standard | failed-shutdown-false-5b7de13d-e41b-499c-83e5-3d23e3f228d5 | JAR | `879bd371d926bbdec3b7e4ee09e0aad90a1583a595ec4dfd4c498d2aadc16146` | 143 | False |
| standard | failed-shutdown-true-9ff8e7e5-6927-461d-8738-7e5c465e1e6f | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| standard | gpkg-missing-table-3072f908-f67e-4eaf-929b-3cd0d4184239 | JAR | `371dc271d973d7ebf2c8d082ccda14ade906e7d732bc030f8cfccbae36ff5ae4` | 10 | False |
| standard | gpkg-srid-05b4a957-d85c-40a7-aa85-047dec8d3924 | JAR | `3745202a039869bf15da243745c47c4ca7d91d367c32e0b01c11c8c38d587500` | 0 | False |
| standard | gpkg-srid-4317a848-5a69-429d-af59-6f472f839547 | JAR | `fde09d2353142808bba7e7d453d6aed15595d8790cf03d47aae60c21f39f9194` | 0 | False |
| standard | invalid-csv-a684aeec-263e-44e0-b201-ca91e70a511d | JAR | `5fa1e39287f2ab5fc8aec74542d73335914f0ccac3cf2f19ea09e4e55eafe5b6` | 10 | False |
| standard | invalid-model-repository-3e0ce812-d0bb-4fda-acd3-b9fdf6ce2713 | JAR | `626cfbd4c36b0921a55ad1602b59c4fef1ef153f97625a3ad5c1807d7f1bbf71` | 10 | False |
| standard | original-gzip-524a0398-b9cc-42ab-b9dd-f2cee76e92ac | JAR | `8fa79e6e93786d91f44b3c6136cee615b099175cf647d7725081e71ee3896fa0` | 0 | False |
| standard | runner-timeout-false-6b85429b-f714-4657-9924-3d63f2d46886 | JAR | `ffc4d790c7df03718b354d4fb41a233f0a7f4765069eb66626ed88f29cce3f17` | 143 | True |
| standard | runner-timeout-true-e710c013-a21a-4a02-b461-7bb275057162 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | True |
| standard | valid-csv-path-ca71931a-7cfe-4b5d-a8ed-5ddc03b44cbd | JAR | `4eb45603e3416aeadc31366e9bb4071b46ee2aa0ee464293071e3b743b39c833` | 0 | False |
| standard | valid-csv-string-1418ed57-05ed-4a18-868f-728c86e56bd3 | JAR | `5d143fbc996dd13c3efcd3db26c2da76b891bf4731925527766c2840dd1555e2` | 0 | False |
| standard | valid-gpkg-path-8e4456c0-de6f-43fb-b1ad-285e0b0e38c6 | JAR | `b73d3ccdb964766327f2e368eb9315e82171ca192b2d3fcca31a5df4ff86fbe3` | 0 | False |
| standard | valid-gpkg-string-bd59049c-77e9-4da8-aa05-65119cc8c7e4 | JAR | `aa3530df8984493ce47c06e09ef4ec3ca07768f61eb5bf14fbeb6844c9c2c3dc` | 0 | False |
| standard | valid-json-path-2829a698-08fd-4874-8243-185270b38dd3 | JAR | `20085f8bf82e57f725d3013240eea4e1633809d8ff0393ebd756c18113ccd3ef` | 0 | False |
| standard | valid-json-string-4068c374-5369-459a-a77c-a611eb066fd7 | JAR | `cc4c720e5b161afe39c4e2ab16a0ec7f7a8aed3f9281fd040090989c7aed11a8` | 0 | False |
| standard | valid-shp-path-88466220-34ad-4167-934b-89cea370eb2e | JAR | `51d1d3d4c1b3c69ef7d60fd9b25542159889db4b7bf607cc0ac27150b66eedee` | 0 | False |
| standard | valid-shp-string-d16fc9a6-965e-42da-9508-ade1a515f5c2 | JAR | `65f1baf98c619115f5a63a8fddc2edc672da76842b70c08d6348873e8adc0253` | 0 | False |
| standard | wrong-gpkg-table-d587738b-cfcd-4058-9aa8-516287986e01 | JAR | `67d7b82cfec7211a9bb28b126d88367dd696a8cf2323f099fd5508271904945c` | 10 | False |
| profiles | cancel-false-false-bfa3b015-ba6e-48e2-aed3-ed37fd3e0ea8 | JAR | `bfa541d65be2119121bc397c4e3e503411121d2627d1cf715f3ba46ba111922e` | 20 | False |
| profiles | cancel-false-true-e8dd5465-028f-40b5-95d8-3f0a54fbe875 | JAR | `05f6d7d8baceb78215711eb285ca8106c79b15ed8c37280db6eeadd80711d244` | 143 | False |
| profiles | cancel-true-false-a7170e70-bc44-409d-8e59-344ce45e2df4 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 | False |
| profiles | cancel-true-true-77fe1bbd-1d56-4514-9ef3-3b11e60625e8 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| profiles | csv-unsupported-table-813660b1-3edc-49b7-ac32-c73beec94833 | JAR | `1a5be14d4c26684f521d06d163aa64a02fa016981738c5f2489db0b4b5add1d4` | 10 | False |
| profiles | database-cancel-0e26129a-1027-462d-8f11-0240439a648a | JAR | `d6c8a4bf71b1c0c0893db85fafbefc8f7cc46969db1d747d4f1dd87a6a31c3ba` | 143 | False |
| profiles | database-chain-d50d2322-721c-45c4-9fe1-093d1a8b4a25 | JAR | `d64abfc37d1112cf134cf94da67da955b2378b3df1edf1dee5941aa2c044ad1b` | 0 | False |
| profiles | failed-shutdown-false-c34e4892-8465-49c1-a6de-c056bbabf39b | JAR | `9f5baa37073a0e8a8d8c1dfcf3a6c1f1de84409a43efad11bcca4e9afaf3adc6` | 143 | False |
| profiles | failed-shutdown-true-c95150e4-63f1-4a65-b02e-e28d65392695 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | False |
| profiles | gpkg-missing-table-233d17f5-3616-4c70-9627-20d11fa2056a | JAR | `7d220b5e86e3070df14e03add268b0258e1dac537e71690a02e691a20ec255a3` | 10 | False |
| profiles | gpkg-srid-46c88307-b6d6-49ff-89fe-cc5f71cb7398 | JAR | `e07631000513a3eeefb2e00190399429a19b6d0f28b91b1ef4b3a85b7071f6aa` | 0 | False |
| profiles | gpkg-srid-c4fc760b-7157-4505-9291-834ccbf9b030 | JAR | `d6affe0ea3f5cc8802ed221504ddbef1167a9b4dd7768eb2f055d4bf4b3b1240` | 0 | False |
| profiles | invalid-csv-d205abe5-380a-4b32-bbff-17322ad8be0c | JAR | `5a51ea06bd7744ee50bbc3c9f6f80306cfbff44edae763395c758fe2229ba7f8` | 10 | False |
| profiles | invalid-model-repository-d4a92eeb-34b9-4da7-a370-a58dadc06530 | JAR | `e22c51e04616d10700b5d2332a8a263e76f0337aa8db81b5b7ec35c6da905aff` | 10 | False |
| profiles | original-gzip-9ec660c8-7225-4548-be97-a2967085d229 | JAR | `6ea076480dd76c55a1012bbcb411f23b50e535c06063b3b6905926bc225566ff` | 0 | False |
| profiles | postgis-geometry-contract-523936c7-8c42-45e2-9376-035e2b9f1cde | JAR | `d02ef4fc6f99c31b86ed8bf0e044552758910164afc178dac9a195e8d8fe1255` | 10 | False |
| profiles | runner-timeout-false-8c85e351-7ed8-44d4-9ce4-1ac46bbecd4c | JAR | `ad4301ad69c2c26d01a68e8c75b4d25efa7ca50e4d7b163141f24d0fe6a53989` | 143 | True |
| profiles | runner-timeout-true-0967c459-9521-47b0-8113-94484ff4f3e8 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 | True |
| profiles | valid-csv-path-ed26bc1f-af46-43ae-9bab-a3978bce4190 | JAR | `2c598ba55ce01646d4da7c35ea67dbf7b026924b6ef6886f6ae9520264c53155` | 0 | False |
| profiles | valid-csv-string-02580cf3-dcd9-459f-ad79-365b598fa474 | JAR | `70305c6fd0370f4bf579f52169ff5447b2b85cf7bd7471c28d79adcb144328ae` | 0 | False |
| profiles | valid-gpkg-path-4f5bdfe4-302a-499e-b639-dff8d96ca2e9 | JAR | `d2201014f9be608cdc4c58f6902408b38b78dd599420a520f584837a5a08a62b` | 0 | False |
| profiles | valid-gpkg-string-63c646b3-7fc5-49d0-b19f-0ff3546c4a17 | JAR | `09185e6a0709a049dc53f90bd874c939b7ef854c619ced1a1ac69a9eac769a89` | 0 | False |
| profiles | valid-json-path-1974bf0c-ec01-45ec-98c0-8207980719db | JAR | `281a461a4a5cff331dfa67fc5902f526420ec1780e239a5f0dfccad081c1e528` | 0 | False |
| profiles | valid-json-string-8b4254a2-c61d-409c-a140-f225009a73c5 | JAR | `bd8ee729c1cde43880d3202cd5d3cd3febc967cb30e50980ce8ce9471248290c` | 0 | False |
| profiles | valid-shp-path-5f0d38d6-fa53-4d8f-8d13-0cd6e082eae3 | JAR | `01cbe6fbdb712b53d1c0706a42452b2cceaaca3826981b6d7220658f18fcd624` | 0 | False |
| profiles | valid-shp-string-b89d0d4f-3939-4c13-aebb-81f0cb627d20 | JAR | `8b9e2ba4326393dd7408ff8ddf5a58cc6dbf455476e1c9e9f5da3a9407baa8ba` | 0 | False |
| profiles | wrong-gpkg-table-ffea7c26-f697-4551-acdb-3e512886c353 | JAR | `724768e6d4978dde0fe7809df104ee5781811ef46e7ae85579c8d905e6b18c91` | 10 | False |

Zusätzliche eigene JARs und Gegenbeispiele:

| Nachweis | Tatsächliches JAR-SHA-256 |
|---|---|
| Tabellenvertrag | `6ffa47d915461c7666b81f2ba9109dd161d2cc82202c07559806970af3837435` |
| Repository-Steuerzeichen | `90c65d5453967e0393541b371ab1b83ab02520c6d2f4a21f8404da02cef00606` |
| SRS-positiv/ungültig | `1d6af7a6165692e311f6dffc66227a6a3f0b4f28972dd86a761da2aa823c83fc` |
| Tatsächliche Polygonvertices und Header-SRID | `26915a6d56512a3ebaa4b66608276e1c9d55f2cec9f4b399c461f46bdcafadf5` |
| Korrelation/Vorrang/Trennung | `001dc3c68ac07c0136ca9886e37b1dffc618e3d033a9b82291f7b6c9c234871e` |
| Archiv mit absichtlich falschem/fehlendem Outputorakel | `8180894ab867d88bf2ae99362b243db0c441203f78124ba98d3898ace9130f1a` |
| Reale DB-Kette, SQL-Fehler, Geography und Cancellation | `e2c0f6535d5a699828a488ca62f6b9f8935a92d75da1a9b7e016053ce26f3cc0` |
| SIGTERM plus sekundärer Cleanupfehler | `7f8b59753fcd523a897d5ddb89d93708aa84a9840be812d11d29718d9d9891d1` |

Die eigenen Negativfälle benötigen konkrete fachliche Diagnosen beziehungsweise erhaltene Ursachen; ein beliebiger Prozessstartfehler genügt nicht. Das absichtlich ungültige JAR wurde als Startfehler mit OS1 erkannt und nicht als erwartete fachliche Validierung gezählt.

## JAR und Image-Status, Grenzen, offene Punkte

JAR: Die Phase-1-Szenarien sind an echten Kandidatenartefakten fachlich geprüft; das Hilfe-Finding bleibt offen. Die vollständige 44er-JAR-Abdeckung und vier neuen Taskmodule sind ausdrücklich Phase 2, gezielte Paketierung ist Phase 3. Diese späteren Gates wurden hier nicht vorgezogen.

IMAGE: Gemeinsame Request-/Resultatform und getrennte Endpointplätze sind vorbereitet. Der ausführbare Adapter und seine vollständigen nicht startenden Vertragstests gehören laut Phase-1-Arbeit 7 und Phase 2 zum folgenden Gate. Eine explizite IMAGE-Auswahl scheitert im aktuellen Runner klar statt auf JAR zurückzufallen. Der Image-Modus ist in Phase 1 noch nicht als fertig implementiert oder qualifiziert bewertet. **GRETL-Image tatsächlich ausgeführt: nein.** Die verpflichtenden Datenbank-/FTP-/S3-Testcontainer sind davon getrennt und wurden tatsächlich ausgeführt.

Nicht blockierende Dokumentationsbeobachtung D1: Die historische FTP-Zeile hat trotz ausdrücklich inaktivem Methoden-/Scope-/Statustext `historicallyDisabled:false`. Das Boolean sollte angeglichen werden. Der Fall wird weder im Bericht noch in dieser Abnahme als aktive Originalabdeckung gezählt; die 86 aktiven Methoden und ihre Assertions sind vollständig erhalten. Dies ist kein zusätzlicher Verlust eines Pflichtszenarios.

Offenes Finding gegen Pflichtkriterien: **F1 (P1-08); daraus keine Erfüllung des unabhängigen PASS in P1-10.**  
Urteil: **FAIL.**  
Begründung: Grüne Pflichtbuilds und belastbare Fach-/Prozess-/Coveragebelege ersetzen die verlangte korrekte öffentliche Hilfe nicht. Vor Beginn von Phase 2 sind Korrektur, sauberer neuer Kandidatencommit und erneute unabhängige Prüfung erforderlich.
