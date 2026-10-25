# Unabhängiger Prüfbericht – Phase 2, Revision 1

Phase: 2 – Modulgrenzen bereinigen  
Prüfdatum: 25. Oktober 2026  
Implementierungsagent: Agent A (`/root`)  
Unabhängiger Prüfagent: Agent B (`/root/phase2_review`), keine Implementierungsbeteiligung  
Phasenausgang: `b0499d3`  
Vorheriger abgenommener Phase-1-Produktstand: `52675c526a0edad52d7005c1d7e874fb7d3ca276`  
Erster Phase-2-Kandidat: `7db4ab8b97d1aa8adf60bc9b137adf24c65e2a02` (unabhängig FAIL)  
Jetzt geprüfter Produktkandidat: `fda17afdc2fc4a8a0b9ee94f7d2922af08da4454`  
Revisions-Implementierungsbericht: anschliessender Dokumentationscommit `49bcf6d1d816925df2b0bccbc9f21017b57bf111`  
Eigene neue Nachweise: `/tmp/gretl-phase2-revision-1-review-evidence/`

**Urteil: PASS für Phase 2 auf `fda17afdc2fc4a8a0b9ee94f7d2922af08da4454`.** F1 und F2 sind durch eigene Gegenproben und beide vollständig neu ausgeführten Rootprüfungen geschlossen. Alle elf Kriterien sind erfüllt; offene Pflichtfindings bestehen nicht. Beide Worktrees behielten die ursprünglich problematische Gruppe 501:0. Alle 44 Operationen sind erneut fachlich durch erfolgreich ausgeführte JAR-Jobs belegt. Kein GRETL-Produktimage wurde gebaut oder gestartet. Phase 3 ist damit freigegeben, aber durch diesen Bericht noch nicht abgenommen.

## Unabhängigkeit und unveränderlicher Prüfstand

Zwei frische detached Worktrees wurden vor den Tests und nach Abschluss auf exakt dem Kandidaten und Git-Sauberkeit geprüft:

- `/tmp/gretl-phase2-review-fda17af-standard`
- `/tmp/gretl-phase2-review-fda17af-profiles`

**Keine Gruppenanpassung, keine Änderung von Produkt-/Testdateien oder POMs.** Die Checkoutverzeichnisse haben vor und nach den Builds Unix-Eigentum 501:0; der Prozess läuft effektiv als 501:20. Neue Zusatzprogramme und Probedaten liegen nur im externen Nachweisverzeichnis. Die beiden Rootläufe haben getrennte Targets und Artefakte. Reports, Coverage, Distributionen samt Archiven und Jobnachweise sind nach `standard/` und `profiles/` durch Hardlinks gesichert; dadurch bleiben die geprüften Bytes bei späterem Worktreeabbau erhalten.

Der ursprüngliche eigene Bericht unter `/tmp/gretl-phase2-review-evidence/phase-2-review.md` und die Repositorykopie `phase-2-review-initial.md` wurden erneut bytegleich geprüft: SHA256 `4c5ec549541ebee65a116c83fd34ba3452cdb7df9ebef2f4750987d9193f6595`. Die früheren FAIL-Befunde werden nicht rückwirkend umgedeutet. Neue Revisionsnachweise sind getrennt abgelegt.

AGENTS, vollständiger Plan, relevante normative Spezifikation 4.1 und vorherige Berichte sind aus der ersten eigenen Prüfung bekannt; die unveränderten normativen Dokumente und das vollständige Revisionsdiff wurden selbst erneut abgeglichen. Bericht A bleibt Orientierung, kein Erfolgsbeleg. Das Revisionsdiff verändert keine Produktbibliothek, Mavenabhängigkeit, POM, DSL, normativen Vertrag oder kanonische Fixturedatei. Änderungen betreffen den testseitigen Request-/IMAGE-Adapter, lokale Pfadmarkierungen in Tests und Dokumentation. **344 bestehende Assertionausdrücke in elf geänderten fachlichen Testdateien sind byteidentisch**. Alle **488 neu gebauten Produktklassen einschliesslich des einen Distributionshelfers** stimmen bytegleich mit dem ersten eigenen Kandidatenbuild überein. Die frühere statische Prüfung des gesamten Phasendiffs bleibt deshalb aussagekräftig; Build-, Dependency-, Original-, Artefakt- und Regressionsergebnisse unten wurden neu erhoben.

Belege: `revision.diff`, `revision-audit.json`, `business-assertion-regression.json`, `production-bytecode-equivalence.json`, `environment.json`, `final-status.json`.

## Eigene Prüfumgebung und Befehle

Temurin OpenJDK 25.0.2+10-LTS, Maven Wrapper 3.10.0, Groovy 6.0.0, JaCoCo 0.8.14, macOS 27.0.1 ARM64, UTF-8, Hostlocale en_US und Europe/Zurich. Jobprozesse verwenden deutsche Sprache und UTC. Docker Desktop 4.93.0 / Engine 29.8.1 / Linux ARM64. PostGIS läuft mit dem festgelegten AMD64-Image unter Emulation. Vollständige selbst abgefragte Daten: `environment.json`.

Beide exakten Rootbefehle wurden jeweils im eigenen sauberen Kandidatencheckout vollständig ausgeführt:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

- Standard: **BUILD SUCCESS; 459 Tests, 0 Failures, 0 Errors, 0 Skips**.
- Vollprofil: **BUILD SUCCESS; 513 Tests, 0 Failures, 0 Errors, 0 Skips**.

Keine vorgängige Gruppenreparatur, kein fehlgeschlagener Rootlauf, keine Tests mit Skip-Flags, keine Assumptions oder stillen Fallbacks. Logs `standard.log`/`profiles.log`, originale XML und HTML/XML-Coverage sind erhalten. Die Zahlen stammen aus den eigenen XMLs, nicht aus A-Berichten.

Zusätzlich selbst ausgeführt: Maven `dependency:tree` mit `-pl gretl-api,gretl-runner,gretl-io-tasks,gretl-database-tasks,gretl-interlis-tasks,gretl-ilivalidator,gretl-job-runtime -am -Dscope=runtime -DoutputType=json -DoutputFile=target/review-runtime-tree.json`, API-`jdeps`, eigenständige IMAGE-Gegenprobe, minimaler Validatorclasspath mit negativen Erweiterungsauswahlen sowie eigene Original-, Assertion-, Distributions-, Output-, Klassen-/Hash- und Regressionaudits. Die erhaltenen Skripte und `commands.json` dokumentieren die Aufrufe.

## Erneute Prüfung von F1 und F2

### F1 geschlossen – lokale Paths und opaque/remote Strings getrennt

Der gemeinsame Request trägt lokale Parameter-/Environmentwerte ausdrücklich als `Path`, Text weiterhin als `String`. JAR serialisiert Paths an der CLI-/JSON-Grenze unverändert zu Hosttext. IMAGE übersetzt nur lokale Paths und die normativ bekannten Runtime-/Termination-Pfadfelder nach den Mounts. Taskkonfiguration kann lokale Paths verschachtelt enthalten; Strings werden nur anhand expliziter Dienstendpoints übersetzt. Die Runtimeidentität und GRETL-Identitäts-ENV-Werte werden als opaque behandelt.

Meine **neue unabhängige** Gegenprobe kompiliert die unveränderten Kandidatenadapterquellen separat gegen die eigene gebaute Kandidatendistribution und verwendet eine aufzeichnende Fake-Engine. Sie prüft:

- FTP-Serververzeichnis `/home/gretl/lease with spaces` bleibt unverändert, während `server=127.0.0.1:3333` zu `ftp:21` und der lokale Path-Ausgang zu `/outputs/result x.txt` wird.
- Relative lokale Parameter behalten die vereinbarte CWD-relative Form; absolute lokale Paths werden anhand der Mounts übersetzt.
- Lokale Environment-Paths, verschachtelte Fachkonfigurations-Paths und Listen werden zu `/job/data x.csv`; fachliche Serverpfade und sonstige opaque Slash-Strings bleiben erhalten.
- Config-Korrelation `/ci/review/42`, Config-Jobname `/ci/job`, ENV-Korrelation `/ci/env/42` und ENV-Jobname bleiben exakt erhalten.
- Relative `runtime.workRoot=work` wird relativ zur Konfigurationsbasis nach `/work` abgebildet; reportRoot wird `/reports`; Termination-Pfad wird `/outputs/termination.json`.
- Secretdateien erhalten read-only Mounts; Environmentsecrets bleiben unverändert und stehen nicht als Werte in der Argumentliste. JDBC-Endpoints samt Query werden korrekt übersetzt.
- Nicht gemountete lokale Paths in **Parameter, ENV und Fachkonfiguration** werden weiterhin mit dem konkreten `Unmapped IMAGE path` abgewiesen. Eine nicht gemappte localhost-URL bleibt ein Fehler.
- Die JAR-JSON-Serialisierung enthält die ursprünglichen Hostpfade; es entsteht keine zweite fachliche Szenariosammlung.

Ergebnis: alle Kontrollen bestanden. **Nur drei Fake-Engine-inspect-Aufrufe, kein Start oder Docker-Controlaufruf.** Belege: `image-probe/IndependentImageProbe.java`, `commands.json`, `0.*.log` und `1.stdout.log`. Die neun aktuellen Adaptervertragstests bestehen zusätzlich in beiden vollständigen Rootläufen und erhalten Startargumente, Rechte, Mounts, Secrets, Ergebnisabbildung, Timeout, Stop, Reaping und Cleanup.

### F2 geschlossen – effektive Prozessidentität bei unverändertem Checkout 501:0

Der Produkt-Vertragstest verwendet jetzt JDK `UnixSystem` für die tatsächliche UID/GID. Die eigene Gegenprobe meldet **effective=501:20, checkout=501:0**. Damit wird genau die ursprüngliche problematische Umgebung erhalten. In beiden normalen Rootläufen instanziieren und bestehen alle neun Adaptertests ohne Gruppenänderung oder Sonderflag. Der Fehler ist nicht durch eine passend gemachte Prüfumgebung verdeckt. Belege: beide XMLs `TEST-ch.so.agi.gretl.it.ImageExecutionAdapterTest.xml`, `image-probe/1.stdout.log`, `environment.json`, `final-status.json`.

## Jedes Phase-2-Abnahmekriterium

| ID | Ergebnis | Eigener aktueller Nachweis |
| --- | --- | --- |
| P2-01 | erfüllt | Vier tatsächliche Taskartefakte und eindeutige Provider, 13 / 3 / 27 / 1; eingefrorener Metadatenvertrag für alle 44 IDs/Symbole/Eingaben/Resultatausgänge in beiden neuen Rootläufen grün. Produktbytecode unverändert, dokumentierte Java-Typumzüge erhalten. OperationMetadataTest, `distribution-audit.json`, Artefaktklassenaudit. |
| P2-02 | erfüllt | Vier Provider genau einmal im echten Distributionsbestand, keine alten Basic-/Core-Provider. Standard-functions-Artefakt ressourcenrein und ohne OperationProvider, genau die bisherigen 18 Funktionsklassen. 114 Distributionschecksummen und alle 93 Bibliotheken geprüft. Hilfen, Meldungen und tatsächliche JARs funktionieren. |
| P2-03 | erfüllt | Neu aufgelöste transitive Produktionsbäume: API JDK-only (jdeps nur java.base), Runner intern nur API/i18n und extern leer, vier Taskmodule intern nur API/i18n. Keine produktive Groovy-/Runner-/CLI-/Packager-/Loggingbackend-Abhängigkeit in Tasks. Graph/Reactormodule azyklisch. `production-dependency-trees.json`, `production-dependency-audit.json`, Enforcer und `api-jdeps.txt`. |
| P2-04 | erfüllt | Job-Runtime hängt ausschliesslich auf den vorgesehenen API-/Runner-/Groovy-/Logging-/i18n-Pfad; weder direkt noch transitiv auf CLI/Packager. Gemeinsamer Start-/Abschlusspfad unverändert und neu über Skript-/JAR-Regressionen ausgeführt. CLI- und JobJarMain-Produktklassen bytegleich zum ersten unabhängig inspizierten Stand. |
| P2-05 | erfüllt | Separat zusammengestellter Runtimegraph des allgemeinen Formatmoduls enthält keine Organisationsbündel. Alle vier Formate laufen im neuen Root auf dem eigenständigen Classpath. Zusätzlich eigene erneut kompilierte minimale CSV-Probe mit ClassNotFound-Kontrollen für Organisationsfunktion, Runner, Groovy und CLI. `minimal-validator/`, ValidatorClasspathLocalJobIT. |
| P2-06 | erfüllt | Neue tatsächliche JAR-Läufe führen die HTTP-Zusatzfunktion in CsvValidator und schmalem IliValidator mit Server-Aufrufzähler aus. HTTP-Nein, fehlende Funktion, defekte Auswahl und Kollision diagnostiziert. Eigene neue minimale Gegenproben prüfen falschen Klassentyp, defekten Service und zwei unterschiedliche Klassen mit demselben qualifizierten Funktionsnamen. Funktionsmatrix und schmale DSL unverändert. |
| P2-07 | erfüllt | Alle 413/447 akzeptierten Phase-1- und alle 456/510 bisherigen Phase-2-Testfälle erhalten und neu grün; drei neue Adapterfälle kommen hinzu. Parameter-/Pfadbasen, Korrelation, Redaktion, Fehler, Cleanup, Cancellation/SIGTERM, OS143 versus GRETL20/10, Terminaldaten und echte DB-Rollbackkontrolle bestehen. `regression-audit.json`, `revision-test-regression.json`, eigene neue XML und JAR-Reports. |
| P2-08 | erfüllt | Unveränderte Migration und 103 öffentliche Einstiegspunkte bleiben gültig, neu gebaute Klassen identisch. Externe FileSize-Extension wird im Root separat gegen das veröffentlichbare API gebaut und als Extension mit Hilfe und Ergebnis verwendet. DistributionIT, neue Beispielartefakte, ursprünglicher eigener Java-Migrationsaudit plus Bytevergleich. |
| P2-09 | erfüllt | Beide exakten Rootwege auf dem neuen sauberen Kandidaten erfolgreich, ohne Gruppenreparatur, ohne Fehler oder Skips; aktuelle produktive Dependency-/Artefaktnachweise geprüft. F2 geschlossen; unabhängiges Gesamturteil PASS. |
| P2-10 | erfüllt | Originalaudit erneut: 87 Fälle / 81 im Umfang, 205 Assertionstexte, 220 passende Fixturehashes, 129 bytegleiche Originaldateien, keine unerklärte Auslassung. Alle 44 Operationen haben im neuen Vollprofil erfolgreiche fachliche JAR-Nachweise; Varianten, Typen, Geometrie/SRID, Dataset-/Formatfehler, S3-Inhalte und FTP-Transfers erhalten. `original-audit.json`, `business-assertion-regression.json`, `artifact-audit.json`, `output-audit.json`. |
| P2-11 | erfüllt | Derselbe Request/Szenariobestand und dieselben fachlichen Assertions für JAR/IMAGE; nur lokale Werte typisiert. F1/F2 durch neue unabhängige Gegenprobe und neun bestehende/erweiterte nicht startende Vertragstests belegt. Explizite Image-ID, Start/Mounts/Benutzer/Rechte/Netz/Parameter/Secrets/Reports/Ergebnis/Timeout/Stop/Reaping erhalten. Kein echter GRETL-Image-Start und kein JAR-Fallback oder erfolgreicher Skip. |

## Originale, Regressionen und fachliche Orakel

Der unabhängige Originalparser wurde auf den neuen Worktree erneut ausgeführt und vergleicht tatsächliche Sourcehashes, sämtliche 205 inventarisierten Originalassertions und aktuelle Zielmethoden. Ergebnis: **87 zugeordnete Fälle, 81 im vereinbarten Umfang**, darunter ein historisch inaktiver FTP-Fall mit neuem realem Nachweis; die sechs konkreten Ausnahmen bleiben DatabaseDocumentExport, Oracle-Treiber, zwei Raster- und zwei Publisherfälle. Alle **220 Fixturehashes** stimmen, **129 Dateien** sind bytegleich zum Originalarchiv. Keine neue Interpretation oder Abschwächung der dokumentierten Darstellungsäquivalenzen. Die 344 bestehenden fachlichen Assertions der geänderten Testdateien wurden zusätzlich direkt zwischen beiden Produktcommits verglichen und sind byteidentisch.

Die breiten neuen Läufe prüfen weiterhin Originaldatentypen/-werte, CSV-/JSON-Varianten, Exporte, Geometrie/SRID, INTERLIS-Daten/Baskets/Datasets, NGK-Objektdiagnosen, MetaConfig und lokale Repositories, HTTP-Inhalte/Auth/Multipart, S3-Schlüssel/Inhalte/Kopie/Download/Löschung und lokale FTP-Transfers. Neue unabhängige Dateiprüfung liest GPKG über read-only SQLite, XLSX über ZIP/XML und SHP/DBF direkt: exakte Boflächenwerte und SRID 2056/21781, Excel-Zeilen, 109/127 native Records, SHP-Header/-Dateilänge/PRJ sowie bytegleiches XML. Falsche erwartete Zeilen und ein fehlendes read-only GPKG werden zurückgewiesen und erzeugen keine Scheindatei. `output-audit.json`.

Die XML-Regressionaudits vergleichen die neuen Läufe sowohl mit dem **final akzeptierten** Phase-1-Prüfbestand (413/447) als auch dem ersten eigenen Phase-2-Bestand (456/510). Keine verschwundenen Fälle; die bekannten Paket-/Providerumzüge werden ausdrücklich abgebildet. Es gibt genau drei zusätzliche Adaptermethoden, keine entfernte Assertion oder deaktivierte Pflicht. Der frühere eigene ausführliche Bericht enthält die unveränderte vollständige Phase-1-Kriterienzuordnung; hier wurden deren gesamten verpflichtenden Testwege erneut ausgeführt.

## Tests aus eigenen XMLs

| Modul | Standard | Vollprofil |
| --- | ---: | ---: |
| gretl-api | 17 | 17 |
| gretl-cli | 52 | 52 |
| gretl-database-tasks | 21 | 21 |
| gretl-dist | 11 | 11 |
| gretl-geotools-probe | 0 | 4 |
| gretl-groovy | 49 | 49 |
| gretl-i18n | 4 | 4 |
| gretl-ilivalidator | 3 | 3 |
| gretl-integration-tests | 65 | 115 |
| gretl-interlis-tasks | 31 | 31 |
| gretl-io-tasks | 54 | 54 |
| gretl-job-runtime | 10 | 10 |
| gretl-logging | 18 | 18 |
| gretl-packager | 8 | 8 |
| gretl-runner | 116 | 116 |
| **Gesamt** | **459** | **513** |

Beide Läufe jeweils **0 Failures, 0 Errors, 0 Skips**. Die Testzahlen enthalten keine eigenen externen Zusatzproben und werden nicht als Coveragequote interpretiert.

## Coverage und Messgrenzen

JaCoCo 0.8.14 erzeugt erneut XML/HTML aller 13 produktiven Gruppen. Messumfang sind die instrumentierten Surefire-/Failsafe-JVMs und darin geladene Produktklassen. Separate Launcher-/Packager-/Job-JVMs und die eigenen externen Zusatzprozesse sind **nicht instrumentiert** und werden nicht als gemessene Abdeckung ausgegeben. Die gemeinsame Job-Runtime wird teilweise durch die verschobenen In-Process-Prüfungen gemessen; dies instrumentiert nicht nachträglich ihre JAR-Unterprozesse. Keine neue Prozenthürde und keine Coverageableitung aus Testzahlen. Beide vollständigen Zählerbestände: `coverage-summary.json`; originale XML/HTML in den jeweiligen `site/`-Verzeichnissen.

Vollprofil, covered / total:

| Produktives Modul | Lines | Branches |
| --- | ---: | ---: |
| gretl-api | 110 / 114 | 23 / 26 |
| gretl-runner | 1796 / 2078 | 754 / 1061 |
| gretl-groovy | 498 / 733 | 148 / 238 |
| gretl-i18n | 24 / 31 | 13 / 20 |
| gretl-logging | 923 / 1103 | 385 / 590 |
| gretl-packager | 508 / 698 | 204 / 310 |
| gretl-job-runtime | 795 / 1118 | 286 / 483 |
| gretl-dist | 20 / 26 | 5 / 8 |
| gretl-cli | 338 / 458 | 104 / 174 |
| gretl-database-tasks | 1208 / 1516 | 418 / 714 |
| gretl-io-tasks | 1947 / 2511 | 602 / 1014 |
| gretl-ilivalidator | 130 / 207 | 28 / 68 |
| gretl-interlis-tasks | 2854 / 3699 | 1112 / 1859 |

## Tatsächliche JAR-Artefakte und positive 44er-Abdeckung

Standard: **64 JAR + 4 SCRIPT**; Vollprofil: **141 JAR + 4 SCRIPT**. Alle JARs wurden durch den regulären Packager der jeweils eigenen Kandidatendistribution gebaut und als separate `java -jar`-Prozesse ausgeführt. Kein Testclasspath beim Jobstart. Frische Quellen-/Arbeits-/Ausgabeverzeichnisse pro Start; nur bereits regulär gebaute byteidentische Fixturepakete werden testseitig innerhalb einer JVM per dokumentiertem Hardlink wiederverwendet.

Alle tatsächlichen JAR-Hashes wurden neu aus Bytes geprüft; identische Hardlinks werden ihrem einmal geprüften unveränderlichen Inode zugeordnet. Zehn unterschiedliche JAR-Identitäten im Standard, vierzehn im Vollprofil. In jedem unterschiedlichen JAR stimmen **487 Produktklassen aus zwölf Modulen** mit dem jeweiligen neuen Kandidatenbuild überein. Main-Class, alle **93 Eingabe-JAR-Hashes** im eingebetteten Inventar, Quellenhashes, Reports, Terminalereignisse und Exit-/Abbruchklassifikationen wurden erneut geprüft: **keine Abweichung**. `artifact-audit.json` enthält sämtliche Ausführungen, Argumente, Dauer, Endpoints ohne Secrets und Prüfsummen.

Die folgende Tabelle nennt je Operation eine tatsächlich erfolgreiche JAR-Ausführung des neuen Vollprofils; vollständiger Report jeweils unter `profiles/job-evidence/<Lauf>/reports/report.json`. Die Zahl zählt erfolgreiche Invocations, nicht Testmethoden.

| Operation / ID | Erfolgreiche Invocations | Repräsentativer neuer Lauf | Tatsächlicher JAR-SHA256 |
| --- | ---: | --- | --- |
| Av2ch / `gretl.av2ch` | 2 | `av2ch-set-97274f69-ac3d-44f5-b8d9-aa137a6b2ed9` | `0563be036b419e825cbc07fc40db970059d3c15cf02215a997ab15636c7bcd17` |
| Av2geobau / `gretl.av2geobau` | 2 | `av2geobau-set-2382f7ce-4861-4355-804a-240e3a58ef5d` | `0563be036b419e825cbc07fc40db970059d3c15cf02215a997ab15636c7bcd17` |
| Csv2Excel / `gretl.csv2excel` | 2 | `excel-empty-c7b24fa7-908a-45f3-ba80-cd18d7195c8e` | `0563be036b419e825cbc07fc40db970059d3c15cf02215a997ab15636c7bcd17` |
| CsvExport / `gretl.csv-export` | 1 | `format-csv-export-c5a1714e-9ee3-45d3-bfaa-866d7dc7a272` | `4a10413dccc9502228a1d57564f26626cd3d8798e36e834b2c19c90174e640ac` |
| CsvImport / `gretl.csv-import` | 2 | `format-csv-import-4636fa1e-d76e-432c-a5c5-9daa34cbf455` | `4a10413dccc9502228a1d57564f26626cd3d8798e36e834b2c19c90174e640ac` |
| CsvValidator / `gretl.csv-validator` | 3 | `additional-function-8ba5b35d-1727-4698-ac85-b1a15beab963` | `433d2c5926f3602fdc8e428d8b372941b0fe7cd19e3b7d1df4c6f01821c4afac` |
| Curl / `gretl.curl` | 7 | `io-curl-binary-7fd472af-08eb-4152-b010-db8dd2aec189` | `3e55cdac8fc85f6d9c4777f9aeb0e50ab939f4ac3e855539e6f0e421313a9906` |
| Db2Db / `gretl.db2db` | 5 | `database-chain-9df9ce6c-553c-4f98-b5cb-369c5b603165` | `6d88506ecf35ce02c0b21016659dd6b8b6d0ee6b6b230f9090242180f5cb80f6` |
| DuckDbSqlExecutor / `gretl.duckdb-sql-executor` | 2 | `original-duckdb-047ec008-9ddf-4ae7-937a-61af9f57c586` | `863e733ff8b69e4874e93eeb3d38ee23cddeaf47aa34c829324b8b1318b0c7c5` |
| Extract / `gretl.extract` | 2 | `io-extract-file-066a9aed-9662-498d-ba2c-478b7b24a120` | `3e55cdac8fc85f6d9c4777f9aeb0e50ab939f4ac3e855539e6f0e421313a9906` |
| FtpDelete / `gretl.ftp-delete` | 2 | `ftp-delete-5f15c8b2-4bf3-4e7d-b2b7-45b8e9a5106f` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| FtpDownload / `gretl.ftp-download` | 1 | `ftp-download-224eda19-cb3a-436f-8b50-50a200841db6` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| FtpList / `gretl.ftp-list` | 1 | `ftp-list-result-7640bd5e-e6d6-47c3-99d6-d8b463360faa` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| FtpUpload / `gretl.ftp-upload` | 1 | `ftp-upload-041625c9-1552-4606-9578-c73c14734916` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| Gpkg2Dxf / `gretl.gpkg2dxf` | 1 | `gpkg2dxf-single-2dae3a9f-c2d8-4b1a-861a-cda5c0fe1522` | `0563be036b419e825cbc07fc40db970059d3c15cf02215a997ab15636c7bcd17` |
| Gpkg2Shp / `gretl.gpkg2shp` | 1 | `gpkg2shp-single-c3579734-c9a1-4088-844b-7c04025797dd` | `0563be036b419e825cbc07fc40db970059d3c15cf02215a997ab15636c7bcd17` |
| GpkgExport / `gretl.gpkg-export` | 2 | `format-gpkg-export-1601ee81-0e25-437c-a5c6-24d2b5d27c12` | `4a10413dccc9502228a1d57564f26626cd3d8798e36e834b2c19c90174e640ac` |
| GpkgImport / `gretl.gpkg-import` | 1 | `format-gpkg-import-800e71b1-99ab-4a13-ab56-2402fa8152fa` | `4a10413dccc9502228a1d57564f26626cd3d8798e36e834b2c19c90174e640ac` |
| GpkgValidator / `gretl.gpkg-validator` | 2 | `valid-gpkg-path-2afd3bed-a57b-4873-a456-eff354253daa` | `e54a6b416e6e8f0c08f78986207053e23079d074de9b9373b871cd501dc0a4f6` |
| Gzip / `gretl.gzip` | 1 | `original-gzip-ddeeb667-9b52-45b0-9dc5-0579cf7a55d5` | `fe7e4de97204a112452ea3e3fa2c272949c66195809261e50bf7f0a8e7767b40` |
| HttpDownload / `gretl.http-download` | 1 | `io-download-file-9a2b90ae-34a8-4f88-a6f5-a7bb2fbc1e69` | `3e55cdac8fc85f6d9c4777f9aeb0e50ab939f4ac3e855539e6f0e421313a9906` |
| Ili2duckdbExport / `gretl.ili2duckdb-export` | 1 | `ili-duck-export-single-33bf8737-19f5-45dd-990d-309b34900315` | `7967c933ea6c288a99796dd329e5a31fc2d28812fad48fd4d5d60b983c65604c` |
| Ili2duckdbImport / `gretl.ili2duckdb-import` | 1 | `ili-duck-export-single-33bf8737-19f5-45dd-990d-309b34900315` | `7967c933ea6c288a99796dd329e5a31fc2d28812fad48fd4d5d60b983c65604c` |
| Ili2duckdbImportSchema / `gretl.ili2duckdb-import-schema` | 1 | `ili-duck-export-single-33bf8737-19f5-45dd-990d-309b34900315` | `7967c933ea6c288a99796dd329e5a31fc2d28812fad48fd4d5d60b983c65604c` |
| Ili2gpkgImport / `gretl.ili2gpkg-import` | 5 | `gpkg-help-example-bb959236-3cdd-4390-b9bc-9c587656903e` | `a0b6b3534e2a8a877bcb2bd130c2f013fd2be257beb5f80a61e8817ba98b9e4f` |
| Ili2pgDelete / `gretl.ili2pg-delete` | 2 | `ili2pg-delete-3d34e429-c02f-4e34-8629-105fcb2195e9` | `962518827213f33b008bc0f23ba729e67130855920c61b30c1e68ce458071119` |
| Ili2pgExport / `gretl.ili2pg-export` | 1 | `ili2pg-export-04016848-2ae2-401b-a991-391ec75f6220` | `962518827213f33b008bc0f23ba729e67130855920c61b30c1e68ce458071119` |
| Ili2pgImport / `gretl.ili2pg-import` | 7 | `ili2pg-import-03cc2fc1-e2bf-4758-b29a-167292b85850` | `962518827213f33b008bc0f23ba729e67130855920c61b30c1e68ce458071119` |
| Ili2pgImportSchema / `gretl.ili2pg-import-schema` | 13 | `ili2pg-schema-06252869-77d7-425d-a746-feaf190b513a` | `962518827213f33b008bc0f23ba729e67130855920c61b30c1e68ce458071119` |
| Ili2pgReplace / `gretl.ili2pg-replace` | 4 | `ili2pg-replace-20c9e59a-b454-449f-be32-be51bf1c49ee` | `962518827213f33b008bc0f23ba729e67130855920c61b30c1e68ce458071119` |
| Ili2pgUpdate / `gretl.ili2pg-update` | 1 | `ili2pg-update-b95b5136-4b98-4c3d-8675-17e7ddad03b3` | `962518827213f33b008bc0f23ba729e67130855920c61b30c1e68ce458071119` |
| Ili2pgValidate / `gretl.ili2pg-validate` | 6 | `ili2pg-validate-184e5f8d-a695-4647-9b6b-4cee73e4431b` | `962518827213f33b008bc0f23ba729e67130855920c61b30c1e68ce458071119` |
| IliValidator / `gretl.ilivalidator` | 3 | `additional-function-3e04163f-0010-4697-87ea-5506faead85f` | `433d2c5926f3602fdc8e428d8b372941b0fe7cd19e3b7d1df4c6f01821c4afac` |
| JsonImport / `gretl.json-import` | 2 | `format-json-import-1172f3e0-b80b-4440-b028-739966166b3c` | `4a10413dccc9502228a1d57564f26626cd3d8798e36e834b2c19c90174e640ac` |
| JsonValidator / `gretl.json-validator` | 2 | `valid-json-path-beaeb7c4-db04-4b89-9fe6-9fad911a4a1a` | `e54a6b416e6e8f0c08f78986207053e23079d074de9b9373b871cd501dc0a4f6` |
| S3Bucket2Bucket / `gretl.s3-bucket2bucket` | 2 | `original-s3-copy-95cd2bf2-5b49-4bb5-acf2-0e4f922b5aa8` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| S3Delete / `gretl.s3-delete` | 4 | `original-s3-delete-164e7ee7-8a73-4ac0-a18a-4bcbd457c4e6` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| S3Download / `gretl.s3-download` | 4 | `original-s3-download-e1b636c3-e522-4c08-8108-eaf7bcb12a7d` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| S3Upload / `gretl.s3-upload` | 4 | `original-s3-upload-408ef722-72dc-46e7-a897-34247b16c0f5` | `9755d6a4776725c72e74a184dcbb6c5d3c2ee734d5e98fe66c968821c44ff0e9` |
| ShpExport / `gretl.shp-export` | 1 | `format-shp-export-6fdead38-7248-4fd5-8d0e-7ec67c340ea2` | `4a10413dccc9502228a1d57564f26626cd3d8798e36e834b2c19c90174e640ac` |
| ShpImport / `gretl.shp-import` | 1 | `format-shp-import-51fe53b3-1dbd-47d3-bbb6-3f52bec89f50` | `4a10413dccc9502228a1d57564f26626cd3d8798e36e834b2c19c90174e640ac` |
| ShpValidator / `gretl.shp-validator` | 2 | `valid-shp-path-39949ec7-062a-4921-a41a-73401d9dae8d` | `e54a6b416e6e8f0c08f78986207053e23079d074de9b9373b871cd501dc0a4f6` |
| SqlExecutor / `gretl.sql-executor` | 4 | `database-chain-9df9ce6c-553c-4f98-b5cb-369c5b603165` | `6d88506ecf35ce02c0b21016659dd6b8b6d0ee6b6b230f9090242180f5cb80f6` |
| XslTransformer / `gretl.xsl-transformer` | 3 | `io-xsl-file-bdf94523-5c31-41bf-94f0-bddb80316ae7` | `3e55cdac8fc85f6d9c4777f9aeb0e50ab939f4ac3e855539e6f0e421313a9906` |

Die bisherige Vollkatalog-Paketierung einschliesslich CLI-/Packageranteilen bleibt bewusst Phase-2-Stand. Eine selektive Paketierung, Unabhängigkeit vom zufälligen Hostclasspath und die finalen Phase-3-Paketfälle werden durch dieses PASS noch nicht behauptet.

## Reale Dienste und getrennte IMAGE-Aussage

Im eigenen Vollprofil wurden PostGIS, FTP und Floci/S3 als reale lokale Testcontainer ausgeführt. `docker-snapshot.json` enthält selbst ausgelesene Container-/Imageidentitäten, Hostports und das eigene Sessionlabel; `docker-events.jsonl` hält Start-/Endereignisse fest. Eigene Fach-JARs und getrennte JDBC-/FTP-/S3-Leser prüfen die Ergebnisse, isolierte Schemata/Buckets/Remoteverzeichnisse werden bereinigt. Die Dienste wurden nicht wegen des zurückgestellten GRETL-Image-E2E übersprungen. Ryuk/Alpine-Testhelfer sind Infrastruktur, kein GRETL-Produktimage.

**JAR: fachlich verifiziert.** Alle 44 Operationen besitzen aktuelle erfolgreiche fachliche JAR-Nachweise; relevante Varianten und Negativdiagnosen sind erhalten.

**IMAGE: vorbereitet und ohne realen Image-Lauf geprüft; noch nicht ausgeführt.** Der ausführbare Adapter und seine Konfiguration sind anhand nicht startender Verträge qualifiziert; F1/F2 sind geschlossen. **GRETL-Produktimage tatsächlich gebaut oder gestartet: nein.** Die zurückgestellte reale Image-Qualifikation ist kein fehlender Pflichtnachweis dieses Auftrags. Bei einem später ausdrücklich gewählten IMAGE-Modus bleiben fehlende Referenz, Image oder Containerlaufzeit klare Fehler; es gibt keinen JAR-Fallback und keinen erfolgreichen Skip.

## Offene Findings und Urteil

Offene Findings gegen die elf Pflichtkriterien: **keine**. F1 ist mit echter Unterscheidung lokaler Paths von Server-/Identitätstexten geschlossen; F2 mit effektiver Nutzeridentität im unverändert problematischen Checkout. Beide vollständigen unabhängigen Rootprüfungen, aktuelle Artefakte, Originalorakel, Dependencygrenzen und Regressionen sind nachgewiesen.

**Urteil: PASS für Phase 2 auf `fda17afdc2fc4a8a0b9ee94f7d2922af08da4454`.** Phase 3 darf beginnen. Das Urteil enthält keine Freigabe der noch ausstehenden Phase-3-Paketierung oder einer tatsächlichen GRETL-Image-Ausführung.
