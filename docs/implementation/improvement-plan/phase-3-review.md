# Unabhängiger Prüfbericht – Phase 3 und kumulative Gesamtabnahme

Phase: 3, einschliesslich aller 34 Kriterien der Phasen 1–3  
Prüfdatum: 25. Oktober 2026  
Implementierungsagent: Agent A (`/root`)  
Unabhängiger Prüfagent: Agent B (`/root/phase3_review`), keine Implementierungsbeteiligung an Phase 3  
Gesamtausgang: `230f2882bcdde15f552f7959aed157a0a6301fa6`  
Analysierter Produktstand: `e668a51b9589fbea9a1e003c8dd18e28726bd430`  
Phasenausgang: `a8512a3d44ed0746c3aa0eeddcc7fefac274cff2`  
Geprüfter Produktkandidat: **`27adb5baa3006627e66ce819e345ca70cda477bb`**  
Sauberer eigener Checkout: `/tmp/gretl-phase3-review-worktree`  
Eigenes Nachweisverzeichnis: `/tmp/gretl-phase3-review-evidence/`

**Urteil: PASS für Phase 3 und die kumulative Gesamtabnahme aller 34 Kriterien auf dem oben bezeichneten Kandidaten.** Alle Pflichtnachweise sind belastbar. Es bestehen keine offenen materiellen Findings gegen die Abnahmekriterien. Die unten erläuterte überzählige macOS-Metadatenzeile im ergänzenden Inventar wird ausdrücklich nicht als vorhandene fachliche Fixture gezählt.

**JAR: fachlich verifiziert. IMAGE: vorbereitet und ohne realen Image-Lauf geprüft; noch nicht ausgeführt.** Agent B hat weder ein GRETL-Produktimage gebaut noch gestartet. Die verpflichtenden PostgreSQL/PostGIS-, FTP- und S3-Testdienste sind im verifizierten vollständigen CI-Gate tatsächlich verwendet worden.

## Unabhängigkeit, Vorgaben und Prüfstand

Ich habe den menschlichen Originalauftrag aus der Attachmentdatei selbst gelesen, ebenso Root-AGENTS.md, docs/spec/AGENTS.md, den vollständigen Drei-Phasen-Plan mit 12 + 11 + 11 Kriterien, die relevanten normativen Verträge 4.1 und die vorhandenen Implementierungs-/Prüfberichte. Schwerpunkt der normativen Prüfung waren RUN-001/010–013/020–022/030/040–062/070–082, die öffentliche DSL und ihre Feld-/Pfadgrenzen, OPS-011/012/020–022, API-Metadaten/Cancellation, CORE-024/025, Logging/Korrelation, BLD-001–008 sowie PKG-019–080. As Bericht diente als Orientierung. Anforderungen, Diff, Code, XML, Logs und Artefaktbytes wurden selbst geprüft.

Der eigene detached Worktree wurde exakt auf 27adb5b angelegt und blieb bis zum Abschluss sauber. Eigentum 501:0, effektiver Prozess 501:20; keine Gruppen-/Rechteanpassung am Kandidaten, keine Produkt-, Test- oder Buildänderung. Alle Zusatzprogramme und Ergebnisse liegen ausserhalb des Produktworktrees. Das gesamte Phasendiff ist als `phase-3.diff` erhalten. Die zwei AGENTS-Dateien sind die einzigen AGENTS.md im Kandidaten.

Der eigene Gitvergleich bestätigt: e668a51 → 230f288 fügt ausschliesslich den Plan hinzu. Phase 1 wurde unabhängig auf 52675c526a0edad52d7005c1d7e874fb7d3ca276 freigegeben, Phase 2 nach Korrekturen auf fda17afdc2fc4a8a0b9ee94f7d2922af08da4454. Ihre Berichte sind erhalten; Phase 3 beginnt erst nach deren PASS. Der Hauptcheckoutstand 54ddf86 fügt gegenüber dem geprüften Produktkandidaten nur phase-3-implementation.md und phase-3-ci-verification.json hinzu. Diese beiden Dateien wurden gelesen; sie werden nicht als alleiniger Beweis oder als anderer Produktstand ausgegeben.

## Eigene Prüfumgebung und tatsächlich verwendeter Prüfweg

Eigene Zusatzprüfungen: macOS 27.0.1 ARM64, Temurin 25.0.2+10-LTS, Maven 3.10.0 über den gemeinsamen Wrapper, UTF-8. `environment.json` enthält die eigenen Java-/Wrapper-/OS-/Identitäts-/Checkoutabfragen. Die Javainstallation wurde ausdrücklich über ihren absoluten SDKMAN-Pfad verwendet; der anfänglich unkonfigurierte macOS-Systemstarter `/usr/bin/java` ist kein Testlauf.

Für die beiden vollständigen Root-Gates wurde der im Originalauftrag ausdrücklich erlaubte CI-Nachweis verwendet. Agent B hat diese Gates nicht nochmals lokal ausgeführt. Er hat Run, Jobschritte, vollständiges Joblog und Artefaktmetadaten selbst frisch über die GitHub-Verbindung abgefragt:

- [Run 40226691230](https://github.com/edigonzales/gretl-runtime/actions/runs/40226691230), Attempt 1, head_sha exakt 27adb5baa3006627e66ce819e345ca70cda477bb, Status completed/success.
- Job 121269452785: beide exakten Root-Gates, beide Artefaktaudits und alle Evidenzuploads success.
- `./mvnw -B clean verify`: **470 Tests, 0 Failures, 0 Errors, 0 Skips**, BUILD SUCCESS.
- `./mvnw -B -Pgeotools-probe,backing-services clean verify`: **525 Tests, 0 Failures, 0 Errors, 0 Skips**, BUILD SUCCESS.
- Linux-CI: Ubuntu 24.04.5 x86-64, Temurin 25.0.4+1, Wrapper/Maven 3.10.0. Das Log enthält genau die beiden vollständigen Rootbefehle und zwei BUILD SUCCESS.

Der nur für main vorgesehene Snapshot-Deployschritt ist auf diesem Arbeitsbranch erwartungsgemäss skipped. Das ist kein übersprungener Test oder Releasegate. Es wurde kein IMAGE-Workflow ausgelöst.

GitHub-Antworten sind in `github-0.json`, `github-1.json`, `github-2.json`, `github-logs.json` erhalten; das selbst abgefragte vollständige dekodierte Log in `github-workflow.log` (SHA-256 `9a7c9baa7f698d9b991a7facbe60791bec36791dbd449099c4bd6772770a9091`). Die von A zuvor heruntergeladenen Original-ZIPs wurden erst nach eigener Prüfung ihrer Grössen und Digests gegen die frisch abgefragten GitHub-Metadaten verwendet. Jede extrahierte Datei wurde ausserdem byteweise gegen ihren ZIP-Eintrag geprüft. Die Originalarchive und extrahierten Daten sind durch eigene Hardlinks unter `ci/` gesichert, unabhängig von der weiteren Lebensdauer von As Pfaden.

| CI-Artefakt | ID | ZIP-SHA-256 | Geprüfte Dateieinträge |
| --- | ---: | --- | ---: |
| standard | 12612195488 | `af21d6dd219aaec7b3454306c5c82be1a3028e56559910a957ab0b6595d35b0c` | 2852 |
| full | 12612087393 | `be3e7e1e3cbf413ad93951c8ff0bacf02ef46406916711b1e9a20965f790d863` | 3542 |
| packages | 12611607458 | `1ff62b0b284acf412068997de1c54f38d36c9c17054aba87b10e645288daf91d` | 8 |

`archive-and-test-audit.json` enthält die unabhängige Archiv- und XML-Zählung. `audit-ci.py` / `ci-content-audit.json` prüfen die Distributionsinputs, alle vorhandenen Ausführungsinventare, Reports und repräsentativen JAR-Bytes. Der Root-Auditor wurde zusätzlich im CI selbst nach jedem Gate erfolgreich ausgeführt; sein unveränderter Kandidatencode berechnet dort auch die Hashes aller breiten Job-JARs, bevor die breiten Binärdateien aus Platzgründen nicht hochgeladen werden. Die CI-Artefakte enthalten deren Ausführungshashes, Eingabeinventare, fachliche Outputs und Reports. Die sieben zusätzlichen Paket-JARs und die volle Distribution sind als Binärdateien hochgeladen und von B selbst erneut gehasht worden. Diese Beweisgrenzen werden nicht als lokales Nachhashen sämtlicher 148 Binär-JARs ausgegeben.

## Eigene unabhängige Gegenproben und Artefaktprüfung

`independent-probe.py` baut eine zusätzliche echte Java-Klasse `review.Unrelated` und ein Host-JAR. Derselbe Schema-2-Descriptor ohne Modulauswahl wird in zwei separaten Authoringprozessen regulär paketiert: einmal nur mit der Kandidatendistribution, einmal mit dem zusätzlichen JAR auf dem tatsächlichen `-cp`. Beide enthalten exakt dieselben zwölf Maven-Eingaben samt Hashes; die Zusatzklasse, alle Taskprovider, Fachengines, CLI und Packager fehlen dem Job-JAR. Die Hashidentität des gesamten ZIP ist nicht gefordert und wird nicht behauptet; die fachliche Artefaktauswahl und Eingabehashes sind identisch.

Ein anschliessender ausdrücklicher `import review.Unrelated` scheitert trotz des tatsächlich zusätzlich bestückten Host-Classpaths mit Exit 2, PACKAGE_COMPILE_FAILED und dem konkreten Import. Es entsteht kein undeclared.jar. Der Compiler kann die fehlende Deklaration folglich nicht aus dem Authoringhost kaschieren. Die Toplevel-Schreibmarkierung bleibt bei beiden erfolgreichen Paketierungen und der negativen Kompilation aus.

Danach wird die vollständige Quellkopie gelöscht. Der zuvor gebaute Job startet mit absolutem `java -jar`, fremdem CWD mit Leerzeichen, leerem HOME und PATH `/gretl-no-build-tools`, ohne JAVA_HOME oder Buildwerkzeug. Das externe CLASSPATH wird absichtlich auf das Zusatz-JAR gesetzt; es ist beim java-jar-Start keine Bibliothekszufuhr. Ergebnis: OS 0, Report SUCCEEDED/0, exakte UTF-8-Datei `Eigene unabhängige Prüfung ü\n`; erst beim Start entsteht die Toplevelmarkierung. Es entsteht kein Maven-Cache. Befehle, stdout/stderr, JARs, Inventare, Quelllöschung und Ergebnisse stehen unter `own-probe-r2/`.

Eigene tatsächlich gestartete JAR-SHA-256: clean `e0477e3acedaa8548e297e2c6f652ac156e0527402020f9badc2e8bae484dabe`; zusätzlich aus verschmutztem Host erzeugt `a7ca578465b3a6ff349f757398bfa147454b0ae60a9aa45856a5181559364019`.

Zusätzlich startet `run-ci-packages.py` vier der unveränderten hochgeladenen CI-Paket-JARs selbst aus einer fremden, leeren Laufumgebung ohne Buildtools oder Quelle:

- Schmaler IliValidator: reale lokale Validierung, `validation done` und erfolgreicher Report.
- INTERLIS: native SQLite aus dem JAR; read-only Python-SQLite liefert exakt Kägi, Cantaluppi, Schor, Meile und Weber. Unabhängiges ZIP/XML-Lesen des Exceloutputs liefert 309 physische Zeilen, entsprechend letzter nullbasierter Zeile 308, und sieben Headerzellen. Die ausgewählte Zusatzfunktion führt genau einen von meinem Hostserver gezählten HTTP-Aufruf aus.
- IO: Download und ZIP-Ausgabe jeweils exakt dieselben 20 Originalbytes; die ressourcenbasierte XSLT liefert Grundstück 1505.
- Externe Extension: FileSize liefert 5 und der echte transitive Groovy-Extension-Aufruf liefert den im CI-Artefakt eingebetteten eindeutigen Nonce.

Ein weiterer eigener java-jar-Start des unveränderten Datenbankpakets wählt ausschliesslich dessen bereits eingebauten `selected-duck`-Task. Die native DuckDB liefert exakt `value,label` und `7,sieben` aus dem Paket. Dieser zusätzliche lokale Nativebeleg ersetzt nicht den vollständigen PostgreSQL-/Db2Db-Paketfall im CI. Dessen unabhängige JDBC-Assertions prüfen red/Grün und die Transaktionskette. Alle fünf Originalpaketstarts sind unter `own-ci-packages/` mit Befehlen, Logs, Reports und Outputs erhalten. Kein GRETL-Container wurde dafür verwendet.

`minimal-validator.py` stellt ausserdem einen eigenen Classpath aus der vollständigen Maven-Hülle des allgemeinen INTERLIS-Taskartefakts zusammen (45 Eingaben, keine Organisationsfunktionen, kein Runner/Groovy/CLI/Packager). Ein separat kompilierter Kandidaten-Componentprobe führt CSV, JSON, GPKG und SHP positiv aus. Die konkrete CSV-Negativprobe verlangt beide Originaldiagnosen (`value <x> is not a number`, `value gruen is not a member of the enumeration`) und Exit 10. Klassenanwesenheits-Gegenkontrollen des Probeprogramms bestätigen die fehlenden fremden Bündel. Dies ist ausdrücklich ein Komponentenbeleg zusätzlich zur breiten JAR-Suite.

Eigene Archiv-/Outputaudits lesen die CI-GeoPackages unabhängig read-only: 2056 und 21781, boflaechen/form/CURVEPOLYGON, Fachwerte `(5.0,null)` und `(null,Gebaeude)`. XLSX-, DBF-, SHP-/PRJ- und Original-XML-Prüfungen bestehen. Absichtlich falsche erwartete Zeilen und ein fehlendes read-only GeoPackage werden zurückgewiesen; der fehlende Datensatz erzeugt keine scheinbare Ausgabedatei. Belege: `audit-outputs.py`, `output-audit.json`.

Die eigene erste Hilfsauditversion nutzte eine auf dem lokalen Python nicht vorhandene `hashlib.file_digest`-Funktion; nach Umstellung auf SHA-256 über Bytes bestand der vollständige Audit. Der erste eigene Job lief bereits erfolgreich, sein nachfolgendes Prüfprogramm griff jedoch irrtümlich auf `report.status` statt `report.execution.status` zu. Der korrigierte vollständige Wiederholungslauf steht getrennt unter own-probe-r2. Ein anfänglich zu pauschaler eigener ClassNotFound-Audit wurde auf den ausdrücklich erwarteten defekten Pluginfall präzisiert: nur `missing.SelectedIoxPlugin` im dafür bestimmten Zusatzfunktionsszenario darf diese Diagnose tragen. Dies waren Hilfsprogrammfehler, keine fehlgeschlagenen oder übersprungenen Roottests und keine Produktänderungen.

## Alle 34 Abnahmekriterien

Jede Zeile bewertet den finalen Kandidaten. Die genannten Tests sind im selbst verifizierten CI dieses Kandidaten erfolgreich. Ergänzende eigene Proben sind ausdrücklich bezeichnet.

| ID | Ergebnis | Eigener Nachweis und Bewertung |
| --- | --- | --- |
| P1-01 | erfüllt | Baseline und exakter Gitvergleich geprüft; eingefrorenes 44er-Inventar, OperationMetadataTest und konkrete SqlExecutor-/IliValidator-Kompatibilitätsgrenzen gelesen. Eigenes operations-Kommando bestätigt alle 44 angebotenen Symbole. |
| P1-02 | erfüllt | Öffentliche Validator-JARs mit CSV ohne tableName, GPKG mit tableName sowie fehlender/falscher Tabelle und unzulässigem CSV-Feld. Konkrete MISSING_REQUIRED_FIELD/UNKNOWN_CONFIGURATION_FIELD bzw. fachliche Tabellendiagnose im Report; passende Source-Metadaten. |
| P1-03 | erfüllt | Acht positive JAR-Varianten aller vier Formate mit Path/String; Repositorynegativfall INVALID_CONFIGURATION_VALUE vor Engineoutput. Converter und eigenes minimales Vier-Format-Componentprogramm geprüft. |
| P1-04 | erfüllt | Tatsächliche JARs mit defaultSrsCode 2056/21781, per eigenem read-only SQLite-Orakel nachgelesene SRS- und Fachzeilen. Öffentliche Hilfe-Beispielregression weiterhin grün. |
| P1-05 | erfüllt | Db2Db fängt TaskCancelledException vor RuntimeException. Echte DB-JAR-Kette/aktive Query-Synchronisation, SIGTERM, Report CANCELLED/20 bei OS143, NOT_RUN, JDBC-Rollback und keine idle-in-transaction-Session im erfolgreichen aktuellen CI-Test. FailureArbiter-Regressionsbestand erhalten. |
| P1-06 | erfüllt | Alle Script-/JAR-Kombinationen, kooperativer Interrupt/SIGTERM und Timeout/Reaping in CancellationLocalJobIT. Kontrollierte Checkpoints, Cleanupmarken, genau ein Terminalereignis. RunControlTest belegt ein einziges monotones 30-s-Abschlussbudget; Hook wartet nur auf bestehenden Abschluss. |
| P1-07 | erfüllt | CorrelationTest prüft Config vor ENV, 256/257-Zeichengrenze, Steuerzeichen, drei getrennte Runs und Jobname. Aktuelle Log-/Redaction-/Reporttests grün; eigene Inhaltsprüfung aller 152 CI-Prozesslogs/-reports findet bekannte Testsecrets nicht. |
| P1-08 | erfüllt | Öffentliche DSL-/Hilfe-/Beispielpfade und Negative erhalten. PostGIS-Geometry-Föderation wird konkret mit DUCKDB_GEOMETRY_UNSUPPORTED abgewiesen, ursprüngliche Koordinaten/SRID per separater JDBC-Prüfung unverändert; kein verlorener oder erfundener Geometry-Erfolg. |
| P1-09 | erfüllt | Eigene XML-Zählung der JaCoCo-Berichte aller 13 aktiven Produktmodule, separate Line-/Branchwerte. Die subprocess-JVMs sind ausdrücklich nicht gemessen. |
| P1-10 | erfüllt | Beide exakten finalen Root-Gates samt Logs/XML/Artefakten selbst verifiziert, 0 Fehler/Errors/Skips; erhaltene unabhängige Phase-1-Abnahme und jetzige kumulative Bewertung. |
| P1-11 | erfüllt | Eigener neuer Originalaudit: 87 Methoden/Fälle, 205 originale Assertions, feste Sourcehashes, 386 Originaldateien inventarisiert; sechs konkrete Scopeausnahmen, keine unerklärte fachliche Auslassung. |
| P1-12 | erfüllt | ArtifactJobRunner verwendet regulären Kandidatenpackager und separaten java-jar-Prozess, leere Ausgaben und externe Orakel. Archiv-/Transformations-, Datenbank-, Fachnegativ- und korrigierte DSL-Jobs in aktuellen Reports. Eigene falsche Erwartung/fehlendes Ergebnis werden zurückgewiesen. |
| P2-01 | erfüllt | Tatsächliche Services und OperationMetadataTest ergeben genau IO13 / DB3 / INTERLIS27 / IliValidator1; alle alten DSL-/Resultatverträge bleiben anhand eingefrorener Metadaten stabil. |
| P2-02 | erfüllt | Eigener Distributionsscan: genau vier eindeutige OperationProvider; keine alten Basic-/Core-Provider. Standard-functions-Artefakt enthält die 18er-Auswahl, keinen zweiten Taskprovider. Reale Meldungs-/Hilferessourcen funktionieren in eigenen Paketstarts. |
| P2-03 | erfüllt | Aktueller aufgelöster Distributionsgraph mit Maven-Identitäten selbst durchlaufen; API JDK-only zusätzlich per jdeps (nur java.base). Runner nur API/i18n, Tasks ohne Runner/Groovy/CLI/Packager/Loggingbackend. Maven-Enforcer und azyklischer Reactor bestanden. |
| P2-04 | erfüllt | Eigene Graphprüfung: kein direkter oder transitiver CLI-/Packagerpfad aus job-runtime. Codepfad CLI → JobRuntime und JobJarMain → executePackaged gelesen; gemeinsame Parameter-, Config-, Cancellation- und Abschlussregressionen aktuell grün. |
| P2-05 | erfüllt | Selbst ausgeführter eigenständiger 45-JAR-Taskgraph ohne Organisationsbündel; CSV/JSON/GPKG/SHP und fachliches CSV-Negativorakel erfolgreich. Keine Simulation durch einzelne Entfernung aus einem vollen Runtimeclasspath. |
| P2-06 | erfüllt | Eigener realer HTTP-Funktionsaufruf aus CI-INTERLIS-JAR; aktuelle FunctionLocalJobIT für CSV und schmalen IliValidator mit HTTP-ja/nein, fehlender Funktion, defekter Auswahl und Namenskollision. Fehler werden eindeutig diagnostiziert und nicht als Erfolg behandelt. |
| P2-07 | erfüllt | Regressionaudit: alle Phase-2-Fach-/Script-/JAR-/IMAGE-Vertragsfälle erhalten. Phase-1-Korrekturen, Parameter, Pfadbasen, Korrelation, Redaktion, Fehler, Abbruch, Reports und echte DB-Orakel bestehen erneut am finalen Kandidaten. |
| P2-08 | erfüllt | Java-/Maven-Migration, verschobene öffentliche Einstiegspunkte, Modulübersicht und Beispiele gelesen. FileSize wird im aktuellen CI frisch gegen die API gebaut, deklariert paketiert und ausgeführt; eigene Ausführung bestätigt FileSize5 und transitiven Groovy-Helper. |
| P2-09 | erfüllt | Beide Root-Gates vollständig erfolgreich; aktuelle produktive Dependency-/Service-/Artefaktgrenzen selbst geprüft. Vorherige unabhängige Phase-2-Abnahme erhalten, hier kumulativ bestätigt. |
| P2-10 | erfüllt | Eigene Zuordnungsaudits: 81 Fälle im Umfang unter 87 Originalfällen, alle Zielmethoden im erfolgreichen finalen XML. 220 Bestandsfixtures bytegleich; 129 davon bytegleich zum Original. Alle 44 Operationen mit erfolgreichen aktuellen JAR-Invocations, fachliche Typ-/Geometrie-/Datei-/Dataset-/FTP-/S3-Orakel und Originalnegative geprüft. |
| P2-11 | erfüllt | Gemeinsam verwendete Requests/Fixtures/Assertions, kein kopierter IMAGE-Fachbestand. Source und neun aktuelle nicht startende Adaptertests für Argumente, Pfade, Rechte, Netzwerk, Secrets, Parameter, Reports, Exit, Timeout/Stop/Reaping geprüft; opaque Werte bleiben Text, lokale Paths werden übersetzt. Kein echter Produktcontainerstart. |
| P3-01 | erfüllt | PKG-019/Descriptorimplementierung stimmen überein: Schema1 voller qualifizierter Katalog; Schema2 fehlend/leer ohne Standardtasks, Funktionen explizit. Strikte Typ-/Versions-/Koordinaten-/Duplikatfehler und Legacy-DistributionIT bestanden; eigener fehlender Auswahlfall läuft minimal. |
| P3-02 | erfüllt | Eigener tatsächlicher zusätzlicher Host-Classpath mit neu kompiliertem JAR: identische zwölf Maven-Eingaben/Hashes. Isolierter Compiler sieht undeclared Import nicht. Umbenannte Inputs und manipulierte Katalogbytes haben aktuelle eigene XML-Nachweise; Identität stammt aus Maven, nicht Substrings. |
| P3-03 | erfüllt | Alle sieben Tabellenfälle im aktuellen CI mit Fachorakeln, einschliesslich Legacydescriptor. Sieben reale Paket-JARs selbst gehasht/strukturell geprüft; fünf originale CI-Pakete zusätzlich selbst gestartet. Inputinventare und bewusste Abwesenheiten sind belegt. |
| P3-04 | erfüllt | Eindeutige Operation-/Loggingservices, Groovy-Extension und Meldungen geprüft; eigene native SQLite-/DuckDB-Ausführung aus finalen JARs, CI auf Linux. Breite 44er-Suite einschliesslich INTERLIS-only PostgreSQL-/ili2pg-Pfaden grün; JDBC-Treiber ist jetzt eigenes Runtimeinput des INTERLIS-Moduls. |
| P3-05 | erfüllt | Frisch erzeugte externe Mavenidentität und kontrolliertes Repository, provider fehlt dem Testhost; Compiler importiert erst nach deklarierter Auflösung und bündelt transitive Bibliothek. Eigene unabhängige undeclared-Import-Gegenprobe trotz extra Host-JAR sowie eigener externer JAR-Start bestanden. |
| P3-06 | erfüllt | Eigene Toplevelmarke beim Packen aus, erst beim Start vorhanden; Quelle gelöscht, fremdes CWD/leer HOME/PATH ohne Werkzeuge und Ressourceninhalt geprüft. Aktuelle Paket-, Erfolgs-, Fehler- und Cancellationfälle erhalten; reale Fachservices im CI kontrolliert bereitgestellt. |
| P3-07 | erfüllt | Eigener Scan und CLI: volle Distribution mit 44 Operationen und 18 Funktionen. Alle sieben JARs ohne GretlCli/JobPackager/MavenCli, aber mit gemeinsamer Job-Runtime; zusätzliche Authoringresolver nur beim Paketbau. |
| P3-08 | erfüllt | Frühe Regressionen erneut im exakten Commit-CI; unabhängige eigene Hash-/XML-/Coverage-/Graph-/Outcomeaudits beziehen sich auf diesen Kandidaten. 93 Inputhashes, 118 SHA256SUMS und CI-ZIP-Digests geprüft. |
| P3-09 | erfüllt | Aktuelle Pflichthilfe, ausführbare Minimal-/Legacybeispiele, Build-/Testanleitung, Modulschnitt, Migration, Kompatibilitäts- und Paketvertrag sind konsistent; DistributionIT prüft Beispiele. Dieser unveränderliche Bericht erteilt Phase3 und kumulativ PASS. Die ergänzende .DS_Store-Inventarzeile wird unten transparent abgegrenzt. |
| P3-10 | erfüllt | Vollständige migrierte Suite in finalem CI: 148 JAR + vier SCRIPT; alle 44 angebotenen Operationen positiv. Eigene Inventar-/Original-/Output-/Outcomeaudits und alle zugeordneten Methoden im XML; sieben Zusatzpakete ersetzen die breite Suite nicht. |
| P3-11 | erfüllt | Strikter gemeinsamer IMAGE-Modus, explizite Referenz/Launcher/User/Netz und manuell aktivierbarer Workflow vorbereitet. Neun nicht startende Adaptertests erfolgreich; echte JAR-Testdienste obligatorisch und aktuell ausgeführt. Kein Pflicht-IMAGE-Matrixeintrag, kein JAR-Fallback oder erfolgreicher IMAGE-Skip. Abschlussstatus ausdrücklich getrennt. |

## Regressionen, Originale und fachliche Nachweise

`audit-original.py` wurde nach eigener Prüfung seines string-/kommentarsensitiven Assertionparsers auf den neuen Kandidaten angewendet. Alle Originalquellhashes, sämtliche 386 Originaldateihashes, 205 Originalassertionstexte und 220 Phase-2-Fixturehashes stimmen. Frisch direkt von GitHub am Originalcommit abgefragte CsvImportTest- und S3UploadTest-Quellen stimmen zusätzlich exakt mit dem lokalen Originalarchiv überein. Unter 87 Originalfällen liegen 81 im vereinbarten Umfang; die sechs Ausnahmen betreffen DatabaseDocumentExport, einen nicht angebotenen Oracle-Treiberpfad, zwei Raster- und zwei ausdrücklich ausgeschlossene Publisherfälle. Der historisch inaktive FTP-Fall wird als solcher geführt und heute durch reale lokale Transfer-/Negativtests belegt. 129 Bestandsdateien stimmen bytegleich mit dem Originalarchiv überein. Alle in-scope-Zielmethoden erscheinen im erfolgreichen finalen XML.

Die Quellprüfung umfasst unter anderem typisierte CSV-/SHP-/JSON-Importwerte, genaue CSV-Zeilen, Geometrie/SRID, GPKG-Namen und -Datasets, Schemaoptionen/MetaConfig/ilidata, lokale und mehrfache INTERLIS-Dateien, AV-/DXF-Inhalte, Excel, HTTP Auth/Multipart/Binärdaten, S3-Schlüssel/Inhalte/Kopie/Download/Löschung und FTP-Transfers mit abgewiesener Anmeldung. Die Assertions verwenden unabhängige Leser oder Clients ausserhalb des Jobprozesses. Die mit den neuen Deskriptoren ergänzten 13 Fixturewurzeln ändern keine der 220 bisherigen Fachdateien oder Erwartungen.

`regression-audit.json` vergleicht die aktuellen XMLs mit den gespeicherten unabhängigen Phase-2-XMLs. Ein Testname fehlt: der alte packagesResolvedDependencyThatIsNotOnTheRuntimeClasspath. Das Diff zeigt seinen bisherigen Fake-Maven/Markerclass-Test. Seine Fähigkeit ist durch den stärkeren frischen externen Provider mit tatsächlicher Mavenauflösung, Import vor Kompilierung, transitiver Bibliothek, ausgeführtem Groovy-Modul und Hostgegenprobe vollständig erhalten. Die drei Katalogtests ergänzen ihn. Keine fachliche Zusage entfällt; alle übrigen Phase-2-Testnamen sind erhalten. Das Standardgate enthält netto elf, das Vollgate netto zwölf zusätzliche Tests. Testzahlenänderungen werden nicht mit verlorener Coverage gleichgesetzt.

`audit-outcomes.py` prüft alle 152 vollständigen CI-Ausführungen selbst: **115 SUCCEEDED, 30 gezielt FAILED, sieben CANCELLED**; jeweils genau ein run.finished passend zum Report. Einziger absichtlicher fehlender Klassenfall ist der ausgewählte defekte Pluginname missing.SelectedIoxPlugin im dafür bestimmten Konfigurationsnegativtest. Kein Start-/Classpathfehler ersetzt CSV-/Dataset-/FTP-/S3-/AV-Fachnegative. CSV fordert beide Originalmeldungen; FTP fordert FtpTaskException und abgewiesene Anmeldung, S3 NoSuchBucket, Datasetvalidierung validate failed, Tabellenfälle ihre konkreten Konfigurationscodes. Bekannte Testsecrets fehlen in allen gesicherten stderr-Logs und Reports.

Cancellationreports unterscheiden OS143 und GRETL20; echte vorherige Fehler bleiben GRETL FAILED/10. Kontrollierte Quell-/DB-Checkpoints, Cleanupmarken, nicht gestartete Nachfolger, DB-Rollback und fehlende offene Transaktion stehen in Source/aktuellem XML und den Prozessbelegen. Die sieben CANCELLED-Fälle sind erwartete Testresultate, keine übersprungenen Tests.

## Tests und Coverage

| Aktiver Modulbaustein | Standard | Vollprofil |
| --- | ---: | ---: |
| gretl-api | 17 | 17 |
| gretl-cli | 52 | 52 |
| gretl-database-tasks | 21 | 21 |
| gretl-dist | 12 | 12 |
| gretl-geotools-probe | 0 | 4 |
| gretl-groovy | 49 | 49 |
| gretl-i18n | 4 | 4 |
| gretl-ilivalidator | 3 | 3 |
| gretl-integration-tests | 71 | 122 |
| gretl-interlis-tasks | 31 | 31 |
| gretl-io-tasks | 54 | 54 |
| gretl-job-runtime | 10 | 10 |
| gretl-logging | 18 | 18 |
| gretl-packager | 12 | 12 |
| gretl-runner | 116 | 116 |
| **Gesamt** | **470** | **525** |

Je Gate: 0 Failures, 0 Errors, 0 Skips. Eigene Zusatzprüfungen sind in diesen Roottestzahlen nicht enthalten.

JaCoCo 0.8.14 misst die instrumentierten Surefire-/Failsafe-Test-JVMs. Die Summe der frühen Einzelmodulberichte beträgt in beiden Gates 9333 gedeckte / 5183 ungemessene Zeilen (64,29 %) und 3433 / 3324 Branches (50,81 %). Diese von A zitierte Summe enthält noch nicht die zusätzlichen späteren In-Process-Beiträge des Integrationsmoduls. Der tatsächlich kombinierte Aggregatebericht liefert im Standard **10121 / 14516 Lines (69,72 %), 3763 / 6757 Branches (55,69 %)**; im Vollprofil **11219 / 14516 Lines (77,29 %), 4140 / 6757 Branches (61,27 %)**. Die folgende Tabelle verwendet dieses vollständige Vollprofilaggregat. Der unveränderte Coverage-Anhang hat SHA-256 `655694f821be07330015d5082b51bd3d2a882b6160750812d891b6513da56f4d`. `phase-3-review-coverage.json` bewahrt beide Messsichten und beide Gates. Die Aggregation ist implementiert und umfasst alle 13 Produktgruppen; die Abweichung ist kein verlorener oder hinzugeschätzter Messbeitrag. Separat gestartete Launcher-, Compiler-/Packager- und Job-JVMs sind nicht instrumentiert. Deren reale Fachprüfungen erhöhen diese Quoten nicht. Es gibt keine neue Prozenthürde. Alle 13 Produktberichte und das Aggregat sind im CI-Archiv erhalten.

| Produktives Modul | Lines covered/total | Branches covered/total |
| --- | ---: | ---: |
| gretl-api | 110 / 114 | 23 / 26 |
| gretl-cli | 338 / 458 | 104 / 174 |
| gretl-database-tasks | 1208 / 1516 | 418 / 714 |
| gretl-dist | 20 / 100 | 5 / 64 |
| gretl-groovy | 498 / 733 | 148 / 238 |
| gretl-i18n | 24 / 31 | 13 / 20 |
| gretl-ilivalidator | 130 / 207 | 28 / 68 |
| gretl-interlis-tasks | 2854 / 3699 | 1112 / 1859 |
| gretl-io-tasks | 1947 / 2511 | 602 / 1014 |
| gretl-job-runtime | 795 / 1118 | 286 / 483 |
| gretl-logging | 923 / 1103 | 384 / 590 |
| gretl-packager | 576 / 848 | 263 / 446 |
| gretl-runner | 1796 / 2078 | 754 / 1061 |

## Tatsächlich verwendete JAR-Identitäten und Umfang

Standard: 70 JAR + vier SCRIPT; Vollprofil: 148 JAR + vier SCRIPT. Das zusätzliche erfolgreiche externe Provider-JAR liefert eine 45. Operations-ID example.file-size; die **44 angebotenen Standardoperationen** sind alle positiv abgedeckt. Alle Ausführungshashes, Endpoints ohne Secrets, Status, Dauer und Operationszuordnung stehen in meinem `ci-content-audit.json`; die Originalbelege liegen unter `ci/full/gretl-integration-tests/target/job-evidence/`. Die Hashliste wird aus den aktuellen Ausführungsbelegen und Reports gebildet, nicht aus As vorbereiteter Markdowntabelle.

Die volle getestete Distributions-ZIP hat SHA-256 **`31a01f189f7e4c2cd0201702f9b5ee7e0649143496895557920dc3cf98e1d3a3`**. Ihr sauberer sourceRevision-Wert ist exakt 27adb5b. Sämtliche 93 Bibliotheksinputs und 118 SHA256SUMS-Einträge stimmen aus den tatsächlichen ZIP-Bytes. Klassen-/Servicescan und Paketinventare ergänzen diese Provenienz; unnötige CLI-/Packager-/Fachartefakte werden nicht allein anhand der JAR-Grösse beurteilt.

| Aktuelles vollständiges CI-Paket | Input-JARs | Tatsächlich gestarteter/hochgeladener JAR-SHA-256 |
| --- | ---: | --- |
| selected-database | 16 | `0b6c7595ca218b9ac1cd7d497e7458750cc03c13b3d56d2d270e090fdd3eae86` |
| selected-external | 14 | `32a3997657218ba974efc9afe1cb986761f407b7aebc002ccc7353a3696ca49a` |
| selected-interlis | 63 | `5bc1548f77b9c720ae810fa7337e0be8df0995588f5054852ba07b3b7d35fbc5` |
| selected-io | 45 | `f70a63c990037de5bf6d950eeb352f8bbc64d529d0ab071d736a30da014b8e98` |
| selected-minimal-failure | 12 | `f5df5228bf0aafabdc8c7c8965c617fc041f9cd242a8db5c98f21e48124fa3d2` |
| selected-minimal-success | 12 | `f5df5228bf0aafabdc8c7c8965c617fc041f9cd242a8db5c98f21e48124fa3d2` |
| selected-narrow-validator | 25 | `e7de224a8e77cc66ab06c4dbaff096884caedc2656265b818017357d7816cde0` |

Die Inputhüllen folgen dem qualifizierten Mavenkatalog. Action enthält keine Standardprovider oder Fachengines. IO enthält AWS/FTP/Saxon samt XSLT-Ressource und keine DB-/INTERLIS-Engines. DB enthält PostgreSQL/DuckDB, keinen IO-/INTERLIS-Provider. Schmaler IliValidator enthält keine ili2db-/AV-/POI-Hülle. INTERLIS enthält PostgreSQL-/SQLite-/DuckDB-Ressourcen gemäss seinen Engines und bei gewählter Option die Standardfunktionen, aber keine IO-Provider. Die externe Extension ergänzt genau ihre zwei Fixtureartefakte. Der klassifizierte Standardfunktionsbestand ist eine explizite Auswahl, kein zufälliger Hostfund. Native DuckDB-Ressourcen besitzen teilweise Namen wie libduckdb_java.so_osx_universal; sie wurden zusätzlich zu üblichen .so/.dylib/.dll-Pfaden geprüft.

Die Legacy-Schema-1-DistributionIT qualifiziert den vollen Katalog einschliesslich der deklarierten FileSize-Extension über den kontrollierten Repositoryweg. Hilfe, Plan, Parameterüberschreibung, fehlerhafte Parameter, späte Aufgabenwerte und mehrere echte Starts bleiben erhalten. Das Legacyartefakt ist ein regulär im CI gebautes, separat gestartetes JAR; es wird nicht als achtes hochgeladenes Selected-Package-JAR ausgegeben.

## IMAGE-Vorbereitung und Grenzen

ImageExecutionAdapter ist ein implementierter Transportadapter mit austauschbarer Enginegrenze. Settings verlangen explizites Image, absoluten Produktlauncher, benanntes Dienstnetz und nicht-root UID/GID; inspect fixiert die unveränderliche Image-ID. Mounts trennen read-only Job/Config/Secrets von Output/Work/Reports/CWD; lokale Paths, opaque FTP-Serverpfade/Identitäten und Host-/Image-Dienstendpoints werden differenziert übersetzt. Environmentsecrets erscheinen als referenzierte Variablennamen, nicht als Werte in der Dockerargumentliste. Timeout/Cancel verwenden stop, reap-en den Client und räumen verbliebene Container auf. Fehlende Voraussetzungen scheitern vor dem Start; es gibt weder Fallback noch grünen Skip.

Die neun aktuellen Adaptertests liefen in beiden Rootgates erfolgreich ohne echten Produktcontainer. Die gemeinsame breite Fachsuite bleibt strikt am gewählten JAR-/IMAGE-Modus; nur die ausdrücklich zusätzlichen Packagerqualifikationen wählen JAR und die dokumentierten vier Scriptregressionen wählen SCRIPT. Der neue manuelle Workflow startet später bei ausdrücklicher Aktivierung denselben Fachbestand mit expliziter Imagereferenz, Launcher, Dienstnetz und User. Die Push-/Releasepflichtmatrix verwendet heute JAR. PostgreSQL-/FTP-/S3-Testcontainer sind davon getrennt und bleiben Pflicht.

**GRETL-Produktimage tatsächlich gebaut: nein. Tatsächlich ausgeführt: nein.** Dieses PASS behauptet keine bereits bestandene Image-E2E-Qualifikation, keine produktive Inbetriebnahme, keine native GRETL-Binärdatei oder universelle Offlinegarantie. Publisher, gretl-control-*, XtfToGpkg und neue GeoTools-Fachfunktionen wurden nicht hinzugefügt.

## Ergänzende Inventarabweichung und Urteil

Das ergänzende phase-3-fixture-provenance.json enthält 238 Zeilen. Im sauberen Kandidaten existieren **237** davon mit passenden Hashes; zusätzlich steht dort jobs/.DS_Store mit einem lokalen macOS-Hash. Diese Datei ist nicht eingecheckt und keine Jobquelle, kein Modell, keine SQL-Datei, keine Eingabe oder fachliche Erwartung. `supplementary-manifest-note.json` hält den Befund fest. Die eigenständig erneut geprüfte normative Originalzuordnung, die 220 erhaltenen Fachdateien und sämtliche fachlichen Zusatzdateien sind vollständig. Kein Test nutzt die fehlende Metadatei, kein Ergebnis oder Pflichtnachweis hängt davon ab.

Ich stufe diese eine überzählige Betriebssystem-Metadatenzeile als redaktionelle Unschärfe eines ergänzenden Inventarexports ein, nicht als fehlende fachliche Fixture oder Verletzung einer Abnahmefähigkeit. Der aktuelle Bericht korrigiert die tatsächliche Zählung ausdrücklich und gibt die Datei nicht als geprüften Produktbestand aus. Es werden dadurch weder Kriterien gelockert noch fachliche Nachweise ersetzt oder verschoben. Eine redaktionelle Entfernung dieser Zeile wäre sinnvoll, ist aber keine technische Vorbedingung des hier geprüften Produkts. Der Kandidat wurde dafür nicht geändert.

**Offene Findings gegen Pflichtkriterien: keine.**

**Urteil: PASS für Phase 3 und die kumulative Gesamtabnahme aller 34 Kriterien auf `27adb5baa3006627e66ce819e345ca70cda477bb`.** Die Entscheidung stützt sich auf selbst verifiziertes exaktes CI, vollständige Logs/XML und kryptografisch gebundene Artefakte, eigene Original-/Regressions-/Graph-/Outcome-/Outputaudits sowie zusätzliche tatsächliche Paket- und Nativeausführungen. Alle geforderten JAR-Nachweise und die IMAGE-Vorbereitung sind erfüllt; der reale GRETL-Image-Lauf bleibt wie beauftragt unausgeführt.
