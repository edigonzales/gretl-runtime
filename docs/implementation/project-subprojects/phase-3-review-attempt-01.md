# Phase 3 – Independent Review, attempt 01

- Phase base SHA: `0a39bf1d70a1478e01f2cddb7ba6c8af80db963a`.
- Reviewed candidate SHA: `71430e42e5d764628d48abc5837b55d50b8766fd`.
- Reviewer: unabhängiger Review-Agent B, Codex/GPT-6-Familie, Session `independent_phase3_attempt01`; keine Beteiligung an der Implementierung.
- Abschlusszeit UTC: 2026-10-29T06:24:39.664610+00:00.
- Eigener neuer detached Worktree: `/Users/stefan/sources/gretl-runtime-phase-3-review-01`. `git rev-parse HEAD` ergab exakt die Kandidaten-SHA; `git symbolic-ref -q HEAD` Exit 1; `git merge-base --is-ancestor` Exit 0. Anfangs- und Abschlussbaum sind sauber. Kein getrackter Code, Test, Fixture oder Normtext wurde geändert; kein Commit wurde erstellt.
- Vollständiger geprüfter Diff: `0a39bf1d70a1478e01f2cddb7ba6c8af80db963a..71430e42e5d764628d48abc5837b55d50b8766fd`, 58 Dateien, 1638 Additionen/122 Löschungen. Vollständig archiviert als `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01/full-phase.diff`, SHA256 `95362d9f5786e3e0de21d45f60b08b707611974413ac09d89c1159ea226460b3`. Keine Verkürzung auf den letzten Commit.
- Eigene Evidenzwurzel: `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01`. Der äussere Bericht wurde ausserhalb des Review-Worktrees geschrieben.

## Prüfbasis und Herkunft

Gelesen: beide AGENTS-Dateien, vollständige Subprojekt-Spezifikation v1.1 (1105 Zeilen, einschliesslich §§7–9 und Reviewprompt), relevante unveränderte Runtime-/DSL-/Paketierungs-/API-/Kernklassen-/Loggingverträge 03/04/12/14/15/16 sowie Modul-, Test- und Job-Artefaktregeln. Die phasenspezifischen Änderungen und ihre unveränderten Compiler-/Runner-/Loggingnachbarn wurden direkt geprüft. Alte Phasenberichte werden als historische Nachweise ihres damaligen Auftrags behandelt.

Der übergebene Handoff `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/phase-3-handoff.json` hat die erwartete SHA256 `ad7f7802c466163caaa84a63153f73c07933bf6ec53ef9f5b984e470683bd00f`. Alle drei referenzierten Dokumentbytes stimmen mit dem Kandidaten überein; alle 881 Code-/Test-/Fixture-/Buildinputs des Index stimmen. Einzelprüfung: `handoff-verification.json`. Die vier übergebenen Archivmanifest-Prüfsummen stimmen ebenfalls. Die roten Entwicklungsstände und der abgebrochene Exit-143-Lauf sind ausdrücklich keine Gatebelege dieser Review. A's grüne Labels ersetzen die nachfolgenden eigenen Ausführungen nicht.

Umgebung: Temurin OpenJDK 25.0.2+10-LTS; Maven 3.10.0 aus dem Repositorywrapper; Groovy 6.0.0; macOS arm64. Docker Client/Engine 29.8.1, Docker Desktop 4.93.0, Linux/arm64-Server. Eigene Ausgaben: `java-version.txt`, `docker-version.txt`, `initial-provenance.json`. Die vorhandenen amd64-PostGIS-Images wurden auf dem arm64-Daemon emuliert; dies führte zu keinem Gatefehler.

## Unabhängig ausgeführte Pflichtgates

Alle Kommandos liefen im oben bezeichneten Worktree, auf derselben unveränderten SHA. Die vier Pflichtschritte liefen sequenziell. Vor dem zweiten Maven-`clean` wurden aktive XML, Logs, Qualifikation, tatsächliche JARs, Prozesse, Reports und Fachoutputs des normalen Laufs extern archiviert. Es gab keine Skip-/Selektionsflags und keine parallelen Mavenläufe im Review-Checkout.

| Schritt | Exakter Befehl | UTC Beginn | UTC Ende | Exit | Log |
|---|---|---|---|---|---|
| normal | `./mvnw -B clean verify` | 2026-10-29T06:06:54.971838+00:00 | 2026-10-29T06:13:42.286667+00:00 | 0 | `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01/normal-maven.log` |
| normal-audit | `python3 scripts/audit-job-evidence.py --source 71430e42e5d764628d48abc5837b55d50b8766fd` | 2026-10-29T06:14:07.606563+00:00 | 2026-10-29T06:14:10.553899+00:00 | 0 | `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01/normal-audit.log` |
| full-maven | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 2026-10-29T06:14:14.864897+00:00 | 2026-10-29T06:23:39.627735+00:00 | 0 | `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01/full-maven.log` |
| full-audit | `python3 scripts/audit-job-evidence.py --full --source 71430e42e5d764628d48abc5837b55d50b8766fd` | 2026-10-29T06:23:39.628367+00:00 | 2026-10-29T06:23:45.454819+00:00 | 0 | `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01/full-audit.log` |

Die Logdatei des ersten Schritts heisst `normal-maven.log`; zu allen Schritten liegen `*-command.json` mit SHA/CWD/UTC/Exit vor. Der reproduzierbare Orchestrator für Audit/Archivierung/Vollgate liegt unter `continue-gates.py`.

| Gate | Tests | Failures | Errors | Skips | Manifest SHA256 |
|---|---:|---:|---:|---:|---|
| Normal | 667 | 0 | 0 | 0 | `c2b91409d69953ff8c708fb32e1f9ca9dc0bea9eaa28894618568f7af61eb03c` |
| GeoTools + Backing-Services | 723 | 0 | 0 | 0 | `485f982b29d6e87113a2ab6edb98a53678b6f2e3376a079f19d6c690c6e5053d` |

`normal/result.json` und `full/result.json` enthalten jede aktive XML-Datei und ihre Zahlen. Der normale Lauf zählt ausschliesslich die Root-Reactor-Module, der Vollgate zusätzlich `gretl-geotools-probe`; inaktive alte GeoTools-XML werden nicht als normale Tests gezählt. Alle archivierten Dateien wurden nach der Archivierung erneut gegen ihr Manifest gehasht. Beide Audit-Summaries binden `sourceRevision` an `71430e42e5d764628d48abc5837b55d50b8766fd` und `sourceDirty=false`. Der normale Audit belegt 23 erfolgreiche lokale JAR-Operationen; der vollständige Audit alle 44 vorhandenen Operationen. Coverage betrifft die instrumentierten Test-JVMs, keine erfundene Coverage der separaten Jobprozesse.

## Vollständige Phase-3-Matrix

| ID | Ergebnis | Selbst geprüfter Nachweis / Orakel |
|---|---|---|
| PRJ-201 | PASS | ProjectPackageDescriptorTest, unveränderte PackageDescriptorTest und eigene echte Paketierung. Schema 3 mit festem settingsFile; unbekannte Felder/Mischungen abgewiesen; alte Konstruktoren und Schemas 1/2 nutzbar. |
| PRJ-202 | PASS | ProjectCompilerTest und ProjectArtifactLocalJobIT: fünf getrennte wirkliche Entryklassen einschliesslich Root/nested/package-Deklaration, Helper, Quellen und unterschiedliche gleichnamige Ressourcen im tatsächlichen JAR. |
| PRJ-203 | PASS | Markerproben beim wirklichen Packen, strikter Settingsparser, verbotene Helper-Scriptbodies und doppelte Klassen. Eigener Settings-Codeversuch wird vor Ausführung abgewiesen, Marker fehlt. |
| PRJ-204 | PASS | Echte Script-/JAR-Prozesse mit identischem unabhängig gelesenem Inhalt und Taskgraph, fremdem CWD, gelöschter kompilierter Quellkopie, leerem HOME/.m2 und PATH ohne Buildtools. Eigener neuer Projektbestand bestätigt bytegleichen Inhalt und identischen Mermaid-Graph. |
| PRJ-205 | PASS | JAR-Version auch ohne Entryklasse; Help/Plan ohne eingebettete Quelltexte und ohne Runworkspace/Ressourcenmaterialisierung; erfolgreicher Run des source-stripped JAR. Globale Hilfe/Version bleiben vor Jobladen. |
| PRJ-206 | PASS | Archivierte echte materialisierte Projektpfade, unterschiedliche sql/update.sql, scoped when/file/ctx.file/ctx.run; eigener zweiter Ressourcenbestand mit unterscheidbaren Bytes. |
| PRJ-207 | PASS | ProjectMetadataTest, CompiledProjectLoaderTest, reale beschädigte JSON-/Metadaten-/Ressourcen-JARs: strikte neue Metadaten, Exit 2 ohne run.started, Task, Cleanup oder Report; angefangener Workspace entfernt. Schema-1-Reader bleibt kompatibel. |
| PRJ-208 | PASS | Unveränderte Single-CLI/DSL/JAR-/Distribution-/SelectedPackage-Regressionen mit Parametern, Targets, Help/Plan/Run, Ressourcen/Retention. Eigenes ZIP-Lesen bestätigt Deskriptorschemas 1 und 2 mit internen Jobmetadaten Schema 1. |
| PRJ-209 | FAIL | Die gelieferten einfachen Actionfehler-/Cleanup-/NO_DATA-/Retentiontests sind grün; eigene echte Handlerfehler innerhalb ctx.run zeigen aber virtuelle Quellnamen im frühen Operationsereignis und dem Invocation-Fehlergraph des gemeinsamen Reports. Siehe MAJOR-1. |
| PRJ-210 | PASS | Gemeinsamer isolierter CompilationUnit- und Assemblypfad, ProjectPackageIsolationTest, bestehende Konflikt-/Hostverunreinigungs-/Extensiontests, tatsächliche JAR-Hashes und Dependencyinventare/Provider-/Service-/Loggingprovider-Einträge. |
| PRJ-211 | PASS | Projekt-/Single-Requests im gleichen ArtifactJobRunner; elf nicht startende IMAGE-Adaptertests mit Projektpfad und fehlender Voraussetzung ohne JAR-Fallback. Keine reale GRETL-IMAGE-Qualifikation behauptet. |
| PRJ-212 | PASS | Beide eigenen unselektierten Gesamtgates und beide Audits Exit 0; vollständiger Audit belegt positive JAR-Invocations aller 44 bisherigen Operationen. |

## Phasen-1/2- und Bestandsregressionen

| IDs | Ergebnis | Nachweis |
|---|---|---|
| PRJ-001, PRJ-002 | PASS | GretlProjectParserTest: kanonische Root-/Include-/Nested-Reihenfolge. |
| PRJ-003, PRJ-004 | PASS | Ungültige/doppelte/fehlende Includes, ausführbarer Settings-AST und Markerproben werden abgefangen; eigene echte Package-Gegenprobe. |
| PRJ-005, PRJ-006, PRJ-007 | PASS | ProjectPathTest/ProjectSourceNamesTest/Parser: Kollisionsfreiheit, Symlinks/Root-Escape, Größenlimit, qualifizierte Defaults. |
| PRJ-101, PRJ-102 | PASS | GroovyProjectLoaderTest, CliProjectRunTest, ProjectScriptLocalJobIT und Projekt-JAR: getrennte Bodies/Bindings, eine Definition, eine runId/ein Report, echte Operationen. |
| PRJ-103 | PASS | Targethülle ohne nachgelagerte/unverbundene Tasks, eigene identische Script-/JAR-Mermaidgraphen. |
| PRJ-104, PRJ-105 | PASS | Typisierte Cross-Project-Resultate und negative result/resultOptional-Zugriffe bei fehlender dependsOn-Kante; eigene after-only-JAR-Gegenprobe Exit 10 mit UNDECLARED_RESULT_DEPENDENCY. |
| PRJ-106, PRJ-107 | PASS | Vorwärtsreferenzen, vollständige Zyklen, scoped Datei-/Contextauflösung in allen relevanten Closuretypen. |
| PRJ-108 | PASS | Qualifizierte Root-/lokale/fremde Parameter, Pflichtstatus/Typen und CLI-/Datei-/Runtimepriorität, unveränderte Aliase. |
| PRJ-109 | PASS | Ein gemeinsamer Cleanupabschluss mit umgekehrter Reihenfolge; Erfolg, Fehler, NO_DATA und kooperativer Abbruch in Bestands- und JAR-Tests. |
| PRJ-110, PRJ-111 | PASS | Help/Check/Plan ohne gespeicherte Taskarbeit, ursprünglicher expliziter Einzeldateipfad ohne Namensänderung. |
| PRJ-112 | FAIL | Gelieferte SourceLocation-/Eventtests sind grün, aber der erweiterte echte innere Handlerfehler verletzt die ursprüngliche Quellortanforderung wie MAJOR-1. Kein allgemeines Bestehen dieses Vertrags behauptet. |
| PRJ-113, PRJ-114, PRJ-115 | PASS | Fail-fast/NO_DATA/Cancellation, Ziele vor Runbeginn und deterministischer unverändert eingerahmter Definitionshash. |

ExecutionPlannerTest (10), ResultStoreTest (12), SequentialJobRunnerTest (19), GroovyLoaderTest (13), DistributionIT (13), die alten fachlichen Taskmodule und Logging-/API-/CLI-Suites bestehen. Der Vollgate ergänzt echte PostgreSQL/PostGIS-, FTP-/S3- und GeoToolsprüfungen. Die Auswahlpakete behalten unabhängige Datei-/Datenbank-/Transferleser. Keine fachliche Bestandsassertion wurde im Phasendiff entfernt oder geschwächt.

## Architektur, Scope und Artefakte

Keine Änderungen an gretl-api, Runner, Planner, ResultStore, Logging, Fach-Taskmodulen, POMs oder normativem docs/spec-Bestand. Keine neuen Module, Abhängigkeiten, Buildprofile, Registry-Globals, zweite DAG-Engine oder nested Runs. Die wenigen neuen Klassen haben getrennte Aufgaben: kompilierte Entrybeschreibung im Groovy-Modul, gemeinsame Quellhashutility und projektbezogener Compiler-/Resultatträger im Packager. Die geschachtelte CompiledProject.Entry und additive JobSource.projectMode-Methode sind kleine, im Phasenbericht begründete Varianten. Keine unnötige Build-/Runtimeabstraktion gefunden.

JobCompiler.compileSources wird von beiden Compilerwegen verwendet, mit selektiertem URLClassLoader und Plattformparent; Compiler und Programmsicht stammen aus derselben Hülle. Kein Script.run/Instanziieren beim Packen. ProjectCompiler ordnet tatsächliche AST-Scriptknoten den eindeutigen virtuellen Quellen zu und weist zusätzliche Helper-Scriptbodies zurück. JobPackageBuilder.buildProject verzweigt nur die Metadaten und benutzt denselben konfliktprüfenden Assembler samt Lizenz-/Service-/Groovy-/Dependencylogik. Deskriptor 3 wird korrekt von internen Projektmetadaten 2 und unverändertem Dependencyinventar 2 getrennt.

GroovyJobLoader.loadProjectScripts stellt Binding inklusive Modell und Operationssymbolen vor den Binding-Konstruktor, registriert alle Entries in denselben Builder/Index und friert genau einmal nach den Settingsdefaults ein. Normale Singlepfade bleiben nutzbar. JobJarMain lädt vorkompilierte Klassen ohne Source-/Settingsbedarf und verwendet die bestehende sichere Materialisierung und gemeinsamen JobRuntime/Runnerabschluss. `keepWork`/Materialisierungsfehler bleiben wie geprüft; der nachfolgende Fehlerbefund betrifft die Diagnose eines frühen inneren Operationsfehlers.

`normal-project-artifact-inspection.json` dokumentiert die neun neuen Szenarioausführungen mit tatsächlichem JAR-Hash, Entry-/Source-/Ressourceninventaren, Provider-/Loggingprovidern und Taskidentitäten. Script und detached-JAR liefern die UTF-8-Bytes `download-A
|import-C
|9|true|helper`, beide SHA256 `579ac83adcbabf904d21070d947a01fa70f329a05409cf28441d4e502e635947`, und Cleanupbytes `ivd`. `legacy-jar-inspection.json` dokumentiert separat echte erfolgreiche Schema-1/2-Artefakte. Alle ursprünglichen gestarteten JAR-Hashes werden von beiden Audits erneut berechnet.

## Eigene zusätzliche Gegenproben

`python3 /Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01/counterprobes.py` läuft gegen die unveränderte extern archivierte normale Distribution. Die vollständigen argv/CWD/UTC/Exitcodes stehen in `counterprobes-02/commands.json`; Ergebnis in `counterprobes-02/result.json`. Ein neuer kleiner Projektbestand belegt frühes @Field-Binding mit lexikalisch erfasstem Operationssymbol, identische Mermaidgraphen und reale unterschiedliche lokale Dateien, Defaults nur aus Settings, Source-Löschung und JAR-Start mit PATH `/independent-no-build-tools`. JAR SHA256 `a46a25efe80a0a5ef046e2af900812e73b44c7b36f6ed0a42dbb81c10834058c`. Unqualifiziertes Target scheitert vor Run mit PROJECT_TARGET_INVALID; after-only-resultOptional scheitert mit UNDECLARED_RESULT_DEPENDENCY; ausführbare Settings scheitern ohne Marker.

Der erste externe Harnesslauf (`counterprobes/`, `counterprobes-attempt01.py`) hatte einen Fehler im von B geschriebenen Testscript: unmittelbarer @Field-Zugriff in DELEGATE_ONLY-Konfiguration statt lexikalischer Capturevariable. Dies wurde ausschliesslich im externen Harness korrigiert; alte Outputs bleiben erhalten. Es ist kein Produktfinding und kein Pflichtgate.

Die ergänzende unabhängige Handlerfehlerprobe nutzte den normalen Produktlauncher sowie echte `java -jar`-Prozesse, ohne Maven/Testclasspath. Exakte argv und UTC/Exit: `nested-cause-probe/commands.json` und `caught-commands.json`; jeweilige Quellfixtures, tatsächliche JARs, `jar-hashes.json`, stdout/stderr, Reports und Eventdateien sind erhalten. `nested-cause-analysis.json` bindet Ergebnisse und Einzeldatei-SHAs an den Kandidaten.

## Offenes Finding

**MAJOR-1 — Innere Handlerfehler werden vor der Projekt-Quellortübersetzung eingefroren und emittiert.**

Betroffene Grenze: `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/GroovyContextFacade.java:140`; das spätere Remapping in `GroovyTaskAdapter.java:107` kommt für das bereits in `gretl-runner/.../OperationInvoker.java:133` erzeugte und in Zeile 149 emitierte FailureInfo zu spät. Der neue kompilierte Projektpfad verwendet diese vorhandene Grenze ebenfalls und erfüllt den Phase-3-Quellvertrag daher nicht.

Reproduktion auf `71430e42e5d764628d48abc5837b55d50b8766fd`: Projekt mit `include 'import'`, Default `:import:run` und `import/job.groovy`:

```groovy
action('run') { ctx ->
    ctx.run(Gzip) {
        dataFile = file('missing.txt')
        gzipFile = workPath('result.gz')
    }
}
```

Paketieren gelingt Exit 0; Script und echtes Projekt-JAR enden korrekt mit Exit 10. Das frühe `gretl.operation.failed.source.file` und die über `execution.invocations[0].errorRef` referenzierte Reportursache lauten jedoch `GretlProject_b0d72043e3c8dae8062267e2a7563ef76a8184e7714640f3a111673e3c8a37f3.groovy:2`, statt `import/job.groovy:2`. Nur der spätere Taskfehler hat den Originalpfad. Die Variante mit bewusstem try/catch in der Action endet zulässig mit SUCCEEDED/Exit 0; dann ist der einzige Fehlergraph im Report weiterhin der virtuelle Name (`:3`), ohne korrigierten Taskfehler. Beide Varianten sind in Script und JAR tatsächlich reproduziert. Fehlertyp GzipTaskException, Task-ID und Kontrollfluss bleiben erhalten; die ursprüngliche Quelldatei des inneren Aufrufs ist gerade im dafür bestimmten Beleg verloren.

Nachweise: `nested-cause-analysis.json`, `nested-cause-probe/{script,jar,caught-script,caught-jar}/report.json` und deren `gretl-reports/*.events.jsonl`. Beispielsweise verweist die JAR-Invocation auf `f0162295-c282-4964-a74c-81670f7d8a62`; caught-JAR auf `35ac0424-484b-488d-bf19-82fe308977e8`. Erwartet ist die Original-Projektquelle auch im ersten Operationsereignis und dessen eingefrorenem Fehlergraph. Verbindlich: Subprojektvertrag §§3.7, 4.2, 7.2 PRJ-209; CORE-032 und LOG-050. Dies ist eine funktionale Lücke eines Pflichtvertrags und mehr als eine kosmetische Stackdarstellung.

Erforderliche Korrektur durch A: Projektquellzuordnung muss bereits vor Erzeugung/Emission des inneren FailureInfo wirksam sein, ohne Groovy-/Fachabhängigkeit oder globale Registry in den neutralen Runner einzuführen. Ein Catch nach caller.invoke allein repariert das bereits emitierte Ereignis nicht. Regressionen müssen echte Handlerexceptions sowohl unbehandelt als auch bewusst gefangen prüfen, im Script und im vorkompilierten Projekt-JAR, mit Originalpfad und tatsächlicher Zeile im Operationsereignis sowie der referenzierten Reportursache. Danach neuer Kandidatencommit und erneute unabhängige Prüfung gegen dieselbe Basis.

Keine weiteren bestätigten CRITICAL-/MAJOR-/MINOR-Findings.

## Grenzen und Urteil

Kein echter GRETL-IMAGE-Start, Native-Image-, Windows-/Linux-Produkt- oder Produktionslauf; das ist gemäss aktuellem Scope keine offene Pflichtprüfung. Die IMAGE-Adaptertests und die vorgeschriebenen echten lokalen Backing-Services wurden geprüft. Es bestehen keine Umgebungsblocker; alle vier Pflichtschritte wurden selbst ausgeführt. Vertrauenswürdiger Groovy-Toplevel und Compilererweiterungen bleiben innerhalb des bestehenden Sicherheitsvertrags; keine allgemeine Sandbox behauptet.

Gesamtevidenzindex: `/Users/stefan/sources/gretl-runtime-phase-3-evidence-20261029/independent-attempt-01/evidence-sha256.json`, SHA256 `82622e4315fcecc1cdbd9742c5f12627f625be35159ecee7bc12ba96d81594fe`. Die beiden Archivmanifeste verketten die vollständigen eigenen XML-/Artefaktbytes; die externen Zusatzproben sind einzeln gehasht.

**Verdict: FAIL.** Beide Pflichtgates und Audits sind grün, aber MAJOR-1 verletzt den verpflichtenden Originalquellenvertrag. Die Phase ist mit dieser Kandidaten-SHA nicht freigegeben. A darf ausschliesslich diese Phase korrigieren und muss danach einen neuen Kandidaten von unveränderter phaseBaseSha vorlegen; dieser Bericht bleibt unverändert erhalten.
