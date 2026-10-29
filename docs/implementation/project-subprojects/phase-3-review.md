# Phase 3 – Independent Review, attempt 02

- Phase base SHA: `0a39bf1d70a1478e01f2cddb7ba6c8af80db963a`.
- Reviewed candidate SHA: `f0e65db8add4328957e56939c11e2809d4247afd`.
- Reviewer: unabhängiger Review-Agent B; Codex/GPT-6-Familie laut Sessioninstruktionen, genaue Modellvariante nicht exponiert; Session `/root/independent_phase3_attempt02`. Keine Implementierungsbeteiligung oder Code-Fixes.
- Abschluss UTC: 2026-10-29T07:30:40.366290+00:00.
- Eigener neuer detached Worktree: `/Users/stefan/sources/gretl-runtime-phase-3-review-02`. Anfangs und abschliessend exakt candidateSha und sauber; keine getrackte Datei geändert, kein Commit. Das A-Arbeitsverzeichnis wurde nicht bearbeitet.
- Vollständiger geprüfter kumulativer Diff: `0a39bf1d70a1478e01f2cddb7ba6c8af80db963a..f0e65db8add4328957e56939c11e2809d4247afd`, 65 Dateien, 2104 Additionen/124 Löschungen. Beide Commits vorhanden, Basis ist Vorfahr (Exit 0); detached-Prüfung Exit 1. Kein HEAD~1-Ersatz.
- Vollständiger Diff: `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-02/cumulative.diff`, SHA256 `4e5a4a5a0cad60a18d561e0dc5c66b78fb2b277781fe6eeab98569b99077ff1d`. Eigene Evidenzwurzel: `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-02`.

## Prüfbasis, Protokoll und Herkunft

Zuerst beide AGENTS-Dateien, danach die vollständige Subprojekt-Spezifikation v1.1 (1105 Zeilen, insbesondere Phasen-/Testgates sowie Abschnitte 9/12) gelesen. Relevante Normverträge 03/04/12/14/15/16 und Modul-, Test- und Artefaktregeln wurden mit den geänderten Produktionsklassen und ihren Nachbarn abgeglichen. Alle Repositorypfade wurden rootrelativ interpretiert; ausschliesslich Phase 3 wurde geprüft.

Die Phase-2-Freigabe für 3e684678aeafb0a9606a79712344adbc8cd70406 ist im Basiscommit ausschliesslich als Reviewdatei archiviert; deren SHA256 e5f1eeb468c91c2f7490793b26d4a9730f4f869c5b8936740a2aed8f5af44607 stimmt. Der frühere Phase-3-Review ist byteidentisch mit SHA256 8fe40dcb3f71a61b68f091a69a610277fa7108907bc88fbbe15da403b70ab37a erhalten. Sein Befund wurde neu geprüft, keine frühere Freigabe übernommen.

A-Handoff: `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/attempt-02/phase-3-handoff.json`, erwartete und gemessene SHA256 `e9db543ee863cb98b3919f123648bad56472f45bccd8ba6eb8c8fe6be065aa18`. Alle drei gebundenen Dokumente und alle 882 eingefrorenen Code-/Test-/Fixture-/Buildinputs stimmen mit diesem Kandidaten überein. Alle vier A-Archivmanifeste und sämtliche 16’948 Dateieinträge wurden erneut gehasht (identische Hardlinks je Inode dedupliziert). Details: `a-handoff-independent-verification.json`. Eigener zusätzlicher Inputindex enthält 862 ausgewählte Quell-/Test-/Builddateien; seine Anfangs- und Abschlussprüfung ist grün. Diese A-Belege ersetzen keinen eigenen Test.

Umgebung: Temurin OpenJDK 25.0.2+10-LTS, Maven 3.10.0 über den Repositorywrapper, Groovy 6.0.0; macOS 27.0.1 aarch64. Docker Desktop 4.93.0, Engine 29.8.1, Linux/arm64. Gepinnte amd64-PostGIS-Images liefen unter Emulation; keine daraus resultierende Prüfung wurde übersprungen. `environment.json`, `git-provenance.json`, `start-state.json` und `final-state.json` binden die tatsächlichen Zustände.

## Selbst ausgeführte Pflichtgates

Die vier Pflichtschritte liefen sequenziell im eigenen Worktree mit unverändertem Kandidaten, ohne Skip-/Selektionsflags. Das Normalarchiv wurde vor dem zweiten clean vollständig erstellt und wiedergeprüft. Es gab keinen parallelen Mavenlauf im Reviewworktree. Eigene zusätzliche Gegenproben benutzten eine separat entpackte, gehashte normale Distribution und nur externe synthetische Verzeichnisse.

| Schritt | Exakter argv | UTC Beginn | UTC Ende | Exit |
|---|---|---|---|---|
| normal | `./mvnw -B clean verify` | 2026-10-29T07:09:31.462954+00:00 | 2026-10-29T07:16:41.399277+00:00 | 0 |
| normal-audit | `python3 scripts/audit-job-evidence.py --source f0e65db8add4328957e56939c11e2809d4247afd` | 2026-10-29T07:16:56.740617+00:00 | 2026-10-29T07:16:59.710903+00:00 | 0 |
| full | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 2026-10-29T07:17:03.166155+00:00 | 2026-10-29T07:26:52.013383+00:00 | 0 |
| full-audit | `python3 scripts/audit-job-evidence.py --full --source f0e65db8add4328957e56939c11e2809d4247afd` | 2026-10-29T07:26:52.025415+00:00 | 2026-10-29T07:26:57.875224+00:00 | 0 |

Logs: `normal.log`, `normal-audit.log`, `full.log`, `full-audit.log`; zu jedem Schritt vollständiges `*-command.json` mit argv/CWD/SHA/UTC/Exit. `continue_gates.py` und `archive_gate.py` enthalten die reproduzierbare eigene Durchführung.

| Gate | Aktive Tests | Failures | Errors | Skips | Archivmanifest SHA256 |
|---|---:|---:|---:|---:|---|
| Normal | 672 | 0 | 0 | 0 | `2ee2e869b52ad43be58df8c15025c9b9fa99991f9a5ba6c00df90efbea4c32e8` |
| GeoTools + Backing-Services | 728 | 0 | 0 | 0 | `5d9d94c9878746004932ac5f8b868e2b039608928d918a4ce426cefa004c8630` |

Die externen Archive `normal/` (3415 Dateien) und `full/` (4503 Dateien) enthalten aktive XML und Logs, Qualifikation, gestartete JARs, Prozessbefehle/-berichte, Event-/Runreports und unabhängige Fachoutputs. Jede XML mit Einzelzahlen steht in `*-archive-verification.json`. Nur aktive Rootmodule werden normal gezählt; GeoTools kommt ausschliesslich beim Vollgate hinzu. Rohkonfigurationen und Secretdateien wurden nicht pauschal archiviert. Sämtliche Archivdateien wurden nach dem Kopieren und nochmals nach beiden Gates gegen SHA256SUMS.json geprüft; Hardlinks überleben den folgenden clean. `final-archive-reverification.json` belegt die abschliessende Nachprüfung.

Beide Audits binden sourceRevision an `f0e65db8add4328957e56939c11e2809d4247afd` und sourceDirty=false. Normal wurden 23 positive lokale Operationen belegt; das Vollgate belegt positive echte JAR-Invocations aller 44 Standardoperationen sowie die zwei separat ausgewiesenen Extension-IDs example.file-size und fixture.scope (46 positive IDs insgesamt). Separate Job-/Launcherprozesse sind nicht instrumentiert; für sie wird keine Coverage behauptet. Die JaCoCo-Werte der Test-JVMs bleiben separat in der Qualifikation erhalten.

## Vollständige Phase-3-Matrix

| ID | Ergebnis | Selbst geprüfter Nachweis und fachliches Orakel |
|---|---|---|
| PRJ-201 | PASS | ProjectPackageDescriptorTest und alte Descriptor-/Compilerfälle: Schema 3 getrennt, festes settingsFile, verbotene Mischungen/unbekannte Felder mit Codes; alte Schemas/Konstruktoren weiter nutzbar. |
| PRJ-202 | PASS | ProjectCompilerTest/ProjectArtifactLocalJobIT und eigener ZIP-Reader: fünf unterschiedliche echte Entryklassen einschliesslich package/root/nested, Helper, Quellen und unterscheidbare Ressourcen; keine Kollision. |
| PRJ-203 | PASS | Wirkliche Paketierung ohne Toplevel-/Taskmarker; deklarative Settings, versteckte Helper-Scriptbodies und doppelte Klassen abgewiesen. Eigener Settings-Codeversuch erzeugt keinen Marker. |
| PRJ-204 | PASS | Echte Script-/JAR-Prozesse liefern unabhängig gelesene identische UTF-8-Bytes und denselben qualifizierten Graphen; fremdes CWD, gelöschte Sourcekopie, leeres HOME/.m2 und PATH ohne Buildtools. |
| PRJ-205 | PASS | Tatsächliche Control-JARs: Version ohne Entryklasse; Help/Plan ohne Quellenprovenienz und ohne Materialisierung/Taskarbeit; source-stripped-JAR führt denselben Run aus. Globale Hilfe/Version bleiben früh. |
| PRJ-206 | PASS | Unterschiedliche download/import/validation-nested sql/update.sql in realem JAR und behaltenem Workspace; scoped when/file/ctx.file/ctx.run. Eigener zweiter Ressourcenbestand mit consumer-B-Bytes. |
| PRJ-207 | PASS | Strikter neuer Metadatenreader, leere/doppelte/inkonsistente Entries, Klassen, Typen, Defaults/Quellen/Pfade; wirkliche beschädigte JSON-/Metadata-/Resource-JARs Exit 2 ohne Run/Task/Cleanup/Report, eigener Setupbereich entfernt. |
| PRJ-208 | PASS | Unveränderte alte CLI/DSL/Distribution/Single-JAR-Suites mit Params/Targets/Help/Plan/Run/Retention; eigener JAR-Reader bestätigt tatsächlich erfolgreiche Deskriptorschemas 1 und 2 mit internen Metadaten Schema 1. |
| PRJ-209 | PASS | Echte ungefangene und bewusst gefangene Gzip-Handlerfehler in Script/JAR: erstes operation.failed und Invocation-errorRef zeigen Originalquelle/Zeile. Gefangen SUCCEEDED/0, ungefangen FAILED/10; Cause, Cleanup, NO_DATA, Teilziel und Retention real geprüft. Eigene zusätzliche Vierprozessprobe und neutraler Java-Harness bestanden. |
| PRJ-210 | PASS | Gemeinsame isolierte CompilationUnit/Assembly, Host-Packagerklassen ausgeschlossen, Grape wirkungslos; bestehende Class-/Resource-Konflikte und Extension-/Provider-/Service-Merges bleiben geprüft. Alle tatsächlichen JAR-Hashes und Inventare erneut gelesen. |
| PRJ-211 | PASS | Derselbe ArtifactJobRunner für Projekt-/Singlejobs mit kompatiblem Request-Konstruktor; elf nicht startende IMAGE-Adaptertests einschliesslich Projektpfad und fehlender Voraussetzung ohne Fallback. Kein realer IMAGE-Nachweis behauptet. |
| PRJ-212 | PASS | Beide selbst ausgeführten unselektierten Gesamtgates sowie beide SHA-Audits Exit 0, 672/728 aktive Tests ohne Fehler/Skips. Vollgate mit echten PostgreSQL/PostGIS-/FTP-/S3-Diensten und separater GeoTools-Probe; 44 positive JAR-Operationen. |

## Vollständige Phasen-1/2-Regressionsmatrix

| ID | Ergebnis | Nachweis |
|---|---|---|
| PRJ-001 | PASS | Parser: Root zuerst, kanonische Includes und deklarierte Reihenfolge/Quellpositionen. |
| PRJ-002 | PASS | Parser: zwei Includes ohne Root, verschachtelte Datei ohne impliziten Elternjob. |
| PRJ-003 | PASS | Parser: ungültige/leere/doppelte/fehlende Includes mit konkretem Code und Originalposition. |
| PRJ-004 | PASS | Parser-AST-Allowlist/Markerfälle; zusätzliche eigene echte Settings-Paketgegenprobe ohne Nebenwirkung. |
| PRJ-005 | PASS | ProjectPath/ProjectSourceNames: :a_b und :a:b haben unterschiedliche tatsächlich geladene Klassen. |
| PRJ-006 | PASS | Parser: Root-/Settings-/Verzeichnis-/Dateisymlinks, Root-Escape, 256-KiB-Rohbytegrenze und fehlerhaftes UTF-8. |
| PRJ-007 | PASS | Syntax qualifizierter Defaults und Settingspositionen ohne Taskcode. |
| PRJ-101 | PASS | GroovyProjectLoaderTest und reale Prozesse: gleiche Basenamen/lokale IDs, separate Bindings/Bodies, eine Definition. |
| PRJ-102 | PASS | CliProjectRunTest/ProjectScriptLocalJobIT/Projekt-JAR: wirkliche Operationskette, exakte Reihenfolge, eine runId/ein Report. |
| PRJ-103 | PASS | ProjectScopeTest und echte Teilziel-JARs: ausschliesslich transitive dependsOn-Hülle, keine Nachfolger. |
| PRJ-104 | PASS | Typisierte Cross-Project-Ausgänge; fehlende Datenkante abgewiesen, resultOptional verdeckt sie nicht. |
| PRJ-105 | PASS | after selektiert keinen Vorgänger und erlaubt keinen Resultatzugriff; eigene Prozessprobe mit unerlaubt selektierbarem other-Body bleibt korrekt. |
| PRJ-106 | PASS | Vorwärtsreferenzen, unbekannte Links/TaskRefs und vollständige Datenzyklen einschliesslich unselektierter Zyklen. |
| PRJ-107 | PASS | ProjectScopeTest und echte Ressourcen: file/ctx.file/when/Task/Action/innerer Aufruf/Cleanup behalten den Projektscope. |
| PRJ-108 | PASS | Root-/lokale/fremde Parameter, Typen/Required/Defaults, CLI-/Datei-/Runtimepriorität und globale Aliase. |
| PRJ-109 | PASS | Ein gemeinsamer Cleanup in umgekehrter Reihenfolge; Erfolg, Fehler, NO_DATA und Cancellation; echte Bytes ivd. |
| PRJ-110 | PASS | Jobhilfe/Check/Mermaidplan registrieren korrekt ohne Taskkonfiguration/-Action/Cleanup; dokumentierter Toplevelvertrag. |
| PRJ-111 | PASS | Expliziter Einzeldateipfad bleibt unqualifiziert; alte CLI-/DSL-Assertions unverändert. |
| PRJ-112 | PASS | Originale Compile-/Toplevel-/when-/Konfigurations-/Action-/Cleanupquellen sowie neue frühe Handler-/Invocationbelege mit tatsächlicher Zeile; keine doppelten Runereignisse. |
| PRJ-113 | PASS | Bestehende Runner-/Action-/SIGTERM-/Cancellation-JAR-Regressionen erhalten Fail-fast/NO_DATA/Fehlerkontrolle. |
| PRJ-114 | PASS | Ungültige/unbekannte Defaults/Targets vor run.started; eigenes unqualifiziertes JAR-Target Exit 2 mit PROJECT_TARGET_INVALID. |
| PRJ-115 | PASS | ProjectHashTest: eingerahmte Settings-/Entryrohbytes, stabil und rootunabhängig; Ressourcen ändern den Definitionshash nicht. |

Die neutralen API-/Runner-/Logging-/Context-/ResultStore-/Planner- und fachlichen Bestandsfälle wurden im vollständigen Reactor erneut ausgeführt. Die bestehenden Datei-/XTF-/XLSX-/GPKG-/Parquetleser sowie JDBC-/FTP-/S3-Clients prüfen Inhalte ausserhalb der Jobprozesse. Beispielsweise DatabaseArtifactIT prüft typisierte Werte, Unicode, Geometrie/SRID, positive Föderation und Cancellation-Rollback statt bloss Prozessstatus. Keine fachliche Altassertion wurde im Phasendiff gelöscht oder abgeschwächt.

## Architektur, Originalquellen und Scope

Der kumulative Produktionsdiff erhält die neutrale öffentliche gretl-api, Planner, ResultStore, RunInput, Workspace-/Parameter-/Registrysemantik, Fachmodule, grundlegendes Logging sowie POMs/Module/Dependencies/Profile. Gemeinsame JobCompiler.compileSources-Kompilation mit selektiertem Plattformparent-URLClassLoader und gemeinsame kollisionsprüfende Assembly bleiben erhalten. Kein Script.run/Instanziieren beim Packen; ProjectCompiler ordnet echte AST-Scriptknoten den eindeutigen Quellen zu. Keine neue Engine, nested Runs oder globale Projektregistry. Descriptor 3, interne Projektmetadaten 2 und unverändertes Dependencyinventar 2 bleiben getrennt.

GroovyJobLoader.loadProjectScripts setzt pro Entry ein eigenes Binding vor dem Konstruktor, verwendet einen gemeinsamen Builder/Index und genau ein abschliessendes freeze. PackagedProjectJobSource verwendet vorkompilierte Klassen und die vorhandene sichere Materialisierung; der normale Einzeljobpfad bleibt nutzbar. Die kleine additive JobSource.projectMode-Methode hält die qualifizierte Zielprüfung im gemeinsamen Runtimepfad. Die neuen Records/Utilityklassen haben eng begrenzte Zuständigkeiten.

Die nach Review 01 im Phasenbericht ausdrücklich begründete Kernabweichung wurde kritisch neu geprüft: JobDefinition/Builder kopieren eine sprachneutrale Map<String,Path>; alte Konstruktoren liefern leer. GroovyJobLoader übergibt seine vorhandene immutable Quellmap. SequentialJobRunner.java:32 bindet sie lexikalisch um denselben bestehenden execute-Pfad. FailureInfo.java:39–44 hält ausschliesslich einen unveränderlichen ScopedValue-Schlüssel; Werte sind runlokal, keine statische Datenregistry. FailureInfo.java:60 ff. übersetzt bei Snapshotaufnahme bekannte Frame-/Source-Dateien; Klassen, Methoden, Zeilen, unbekannte Frames, Causes, Suppressed-Struktur und Grenzen bleiben erhalten. Kein Groovy-/Tasktyp gelangt in den Runner. Dieser begrenzte Eingriff schliesst die frühe Diagnosegrenze, ohne Fachkontrolle oder SPI zu ändern.

RunnerSourceMappingTest/FailureInfoTest und mein separater Java-Harness prüfen defensive Kopien/alten Konstruktor, frühe Logs und spätere Ressourcen-/Cleanupfehler, verschachtelte leere/andere Scopes, Wiederherstellung nach Ausnahme und folgenden neutralen Run. Der eigene Harness startet ausschliesslich mit gretl-api/gretl-runner/gretl-i18n: zusätzlich simultane A/B-Threadbindungen, Cause/Suppressed, unbekannter Libraryframe, unverändertes Original-Throwable und neutraler Capture nach Scopeende sind grün. Ein anfänglicher externer Harness-Javacaufruf hatte einen falschen lib/*-Classpath; das erhaltene compile-missing-classpath.log ist kein Produkt- oder Gatefehler. Ausschliesslich der externe Harness-Classpath wurde korrigiert.

## Selbst gelesene Artefakte und zusätzliche Gegenproben

`normal-independent-artifact-inspection.json` beschreibt 13 Projektfälle und 47 erfolgreiche alte Schema-1/2-JARs. Das entsprechende Vollgatedokument beschreibt 13 Projektfälle und 118 erfolgreiche Alt-JARs. Tatsächlich gestartete JARs wurden erneut gehasht und geöffnet, ihre Entryklassen, unterschiedlichen SQL-Ressourcen, Metadaten, Provider und genau ein Loggingprovider geprüft. Script, detached-JAR und source-stripped-JAR liefern `download-A\n|import-C\n|9|true|helper`, SHA256 `579ac83adcbabf904d21070d947a01fa70f329a05409cf28441d4e502e635947`, Cleanupbytes `ivd`. Gemeinsame Projektquellidentität stimmt in Scriptreport, JAR-Metadaten und JAR-Report überein. Retained validation/nested/sql/update.sql enthält wirklich validation-B plus LF. Controlprotokolle wurden gegen die jeweils tatsächlich gestarteten stripped/no-entry-JAR-Hashes geprüft.

Mein eigener neuer synthetischer Bestand unter `counterprobes/` verwendet andere Scripts und Ressourcen, package review.consumer, gleichnamige lokale run-Tasks, eine Cross-Project-Resultkante, after ohne Selektion und ein gemeinsames Cleanup. Die normale Distribution wurde aus meinem eigenen archivierten ZIP separat entpackt; alle 93 Katalogartefakte und SHA256SUMS wurden erneut geprüft (`probe-distribution-verification.json`). Vollständige argv/CWD/UTC/Exitcodes aller sieben Prozessaufrufe stehen in counterprobes/commands.json. Keine Maven-/Testclasspath-Ausführung der Jobs.

Der eigene regulär gebaute und tatsächlich gestartete JAR hat SHA256 `1366a9e407b757e4223ef2ac6bd8c074bfdb5eaf14cac2df02c56f9b677bde85`. Nach Löschen der Sourcekopie laufen zwei JAR-Prozesse mit fremdem CWD, leerem HOME und PATH=/independent-no-build-tools. Ungefangen/gefangen jeweils im Script und JAR: das erste operation.failed und der über Invocation.errorRef referenzierte GzipTaskException-Graph zeigen import/job.groovy:7. Gefangen bleibt SUCCEEDED/0, ungefangen FAILED/10; Cleanup und on-failure-Retention stimmen. Vier unabhängig gelesene Outputdateien haben SHA256 `26b13834d08472dda0f7f9d3735fcdd85dec7a46a57cda3338900a0e4cc38487`; alle enthalten den echten Exceptiontyp und consumer-B-Ressourcenbytes. In allen strukturierten file-Feldern fehlen virtuelle GretlProject-Namen. SourceFile-Klassenbezeichner bleiben als technische Klassenidentität korrekt erhalten. Die zusätzliche Settings-Codeprobe wird ohne Marker abgewiesen; unqualifiziertes JAR-Target scheitert vor Run mit Code 2. Die gelieferten vier realen Handlerregressionen zeigen analog Originalquelle import/job.groovy:10.

Zusatzprobenmanifest: `custom-probes-SHA256SUMS.json`, SHA256 `2c6da94e85704b9e04c356c615c471928526d596e265f8883721331d1ecca79b`. Gesamtevidenzindex: `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-02/evidence-index.json`, SHA256 `985435f7570bd0a7ce5f524673cc0afa74f42e7cc0fd9a6715461faa4258d655`. Ein eigener zusätzlicher Reader verlangte zunächst irrtümlich exakte Mengenidentität mit den 44 Standardoperationen; die zwei positiven Extension-IDs waren der einzige Unterschied. Der externe Reader wurde auf die vorgeschriebene Inklusionsprüfung korrigiert, ohne Produkt-/Teständerung; die offiziellen Pflichtaudits waren durchgehend Exit 0 (supplemental-inspector-correction.json). Die beiden Gate-Manifeste und dieses Zusatzmanifest binden die tatsächlichen Bytes; Archivdateien und Zusatzbelege wurden eigenständig nachgeprüft.

## Findings, Grenzen und Entscheidung

Keine bestätigten offenen CRITICAL-, MAJOR- oder MINOR-Findings. Der historische MAJOR-1 ist auf diesem Kandidaten durch eigenständige Codeprüfung, vier eigene echte Gegenprozesse und die neutralen Scopeproben geschlossen. Es wurde kein Code-Fix durch B vorgenommen.

Keine notwendige Pflichtprüfung blieb offen. Kein realer GRETL-IMAGE-Start, Native-Image-, Windows-/Linux-Produkt-, Produktions- oder Deploymentnachweis; diese Wege gehören nicht zum aktuellen Auftrag. Die IMAGE-Adapterregressionen und die echten lokalen Backing-Services sind geprüft. Vertrauenswürdiger Groovy-Toplevel und Compilererweiterungen bleiben innerhalb des dokumentierten Vertrags; Settings-/Pfadprüfungen sind keine allgemeine Sandbox oder atomare Quellsnapshotgarantie. Coverage separater Prozesse wird nicht behauptet.

**Verdict: PASS.** Ausschliesslich für `f0e65db8add4328957e56939c11e2809d4247afd` auf der festen Basis `0a39bf1d70a1478e01f2cddb7ba6c8af80db963a`: sämtliche Pflichtschritte selbst erfolgreich, vollständige Matrix und relevante Bestandsregressionen grün, keine offenen CRITICAL-/MAJOR-Befunde. Die Freigabe wird erst durch unveränderte Archivierung dieses Berichts in einem ausschliesslichen Review-Archivierungscommit übernommen; jede spätere Code-/Test-/Fixture-/Normänderung braucht einen neuen Kandidaten und neue unabhängige Prüfung.
