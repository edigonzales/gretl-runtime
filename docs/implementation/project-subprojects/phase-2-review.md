# Phase 2 – Independent Review, Versuch 01

- Phase base SHA: `761ffa31ca9314f246fbd1420d72f5dbf1dae7ca`.
- Reviewed candidate SHA: `3e684678aeafb0a9606a79712344adbc8cd70406`.
- Reviewer/Model/Session: unabhängiger Codex-Review-Agent B, eigene Sitzung `/root/independent_phase2_attempt01`. Gemäss System GPT-6-basiert; eine genauere konfigurierte Modellkennung ist nicht offengelegt und wird nicht geraten. B hat diese Implementierung nicht geschrieben und keine Produkt-, Test-, Fixture- oder Normkorrektur und keinen Commit vorgenommen.
- Abschluss UTC: 2026-10-29T02:52:43.487081+00:00.
- Eigener neuer detached Prüf-Worktree: `/Users/stefan/sources/gretl-runtime-phase-2-review-01`; alle Prüfkommandos mit explizitem CWD in diesem Worktree. HEAD vor und nach der Prüfung exakt die Kandidaten-SHA; `git symbolic-ref -q HEAD` erwartungsgemäss Exit 1; getrackter Baum sauber.
- Vollständiger geprüfter Phasendiff: `761ffa31ca9314f246fbd1420d72f5dbf1dae7ca..3e684678aeafb0a9606a79712344adbc8cd70406`: **40 Dateien, 1886 Additions, 56 Deletions**. Keine Verkürzung auf einen Teilfix oder einen beweglichen Branch.
- Externe eigene Evidenzwurzel E: `/Users/stefan/sources/gretl-runtime-phase-2-evidence-20261029/independent-attempt-01/`.

## Identität, Umfang und Norm

Beide SHAs wurden als Commitobjekte nachgewiesen; `git merge-base --is-ancestor` liefert Exit 0. B erzeugte selbst den neuen Worktree mit `git worktree add --detach /Users/stefan/sources/gretl-runtime-phase-2-review-01 3e684678aeafb0a9606a79712344adbc8cd70406` (Exit 0). E/`identity.json` und `final-identity.json` dokumentieren tatsächliche Identität, CWD, UTC, Versionen, Ausgaben und Exitcodes. `git diff --check` über den gesamten Phasendiff: Exit 0. E/`full-phase.diff` hat SHA-256 `7080d069d9b2ddd6b6602e867fb89ad3be71e4b858c627f6478c2110ac610f88`.

Gelesen und abgeglichen wurden zuerst beide AGENTS-Dateien, danach die vollständige Subprojekt-Spezifikation **1.1**, insbesondere §§3–4, 7–9 und 12, die Runtime-/DSL-/Paketierungs-/Java-API-/Kernklassen-/Loggingverträge 03/04/12/14/15/16 und `module-boundaries.md`, `testing.md`, `job-artifact-tests.md`. Die archivierte Phase-1-Review akzeptiert `1935a95f73c45028a04316150e165ec0a61a52a5`. Dessen Differenz zur vorliegenden Phasenbasis enthält ausschliesslich `phase-1-review.md`; dessen Hash stimmt mit dem Handoff überein. Phasenbericht und kompletter Evidence-Index des Kandidaten wurden einschliesslich früher roter Entwicklungsbefehle und ihrer anschliessenden Korrekturen geprüft.

Geprüft wird ausschliesslich **Phase 2: gemeinsamer Script-Projektlauf** und Erhalt der bestehenden Einzeldatei-/JAR-Verträge. Die gesamte Änderung umfasst die Definition-/Scope-/Quellenzuordnung im Groovy-Adapter, ProjectJobSource und kleinen Runtime-Dispatcher, additive Parameter-/Quellenmetadaten, neue Tests, Meldungen und dokumentiertes lokales Pipelinebeispiel. Projektpaketierung, Schema 3, loadCompiledProject und Phase-3-Gates sind ausdrücklich ausserhalb dieser Prüfung und wurden nicht begonnen.

## Eigene Ausführung und Belege

Umgebung: Eclipse Temurin OpenJDK `25.0.2+10-LTS`, unveränderter Wrapper mit Maven `3.10.0`, echtes Groovy `6.0.0`, UTF-8, macOS `27.0.1` aarch64, Europe/Zurich. Keine Testselektion, Skipflags, Disabled- oder Assumption-Ausnahme im Pflichtgate.

| Unabhängig ausgeführter Befehl | Exit | UTC / Evidenz |
| --- | ---: | --- |
| `./mvnw -B clean verify` | 0 | `2026-10-29T02:42:49.961596+00:00` bis `2026-10-29T02:48:29.306337+00:00`; E/`clean-verify-command.json`, `clean-verify.log`, `reports/` |
| `python3 scripts/audit-job-evidence.py --source 3e684678aeafb0a9606a79712344adbc8cd70406` | 0 | `2026-10-29T02:48:50.320061+00:00` bis `2026-10-29T02:48:53.164877+00:00`; E/`audit-command.json`, `audit.log`, `qualification/` |
| `python3 /Users/stefan/sources/gretl-runtime-phase-2-evidence-20261029/independent-attempt-01/independent-probes.py` | 0 | **19 zusätzliche echte Distributionsprozesse, 92 strikte Assertions**; vollständige argv/CWD/UTC/Exitcodes in E/`independent-probes-result.json` und pro Fall `probes/*/execution.json`; Quellen und Logs erhalten |
| Eigene Python-Hash-/XML-/Handoffprüfung (`python3 -`) | 0 | E/`foreign-evidence-verification.json`, `foreign-provenance-verification.json` |
| `python3 /Users/stefan/sources/gretl-runtime-phase-2-evidence-20261029/independent-attempt-01/archive-own-evidence.py` | 0 | E/`own-evidence-result.json`, eigene aktive XML-Auswertung, erneute Prüfung aller gestarteten JAR-Hashes, direkte ZIP-/Outputprüfung und selektive Archivierung |

Die eigenen Hilfsproben hatten zunächst einen reinen Reviewer-Authoringfehler: `--language` statt der bestehenden Option `--lang`. Das Produkt lehnte korrekt vor Runbeginn ab; der ursprüngliche Versuch ist in `probes-authoring-error/` und `probe-authoring-error.json` erhalten (Hilfsscript Exit 1, CLI Exit 2). Die Archivierungsroutine adressierte anfangs `target/qualification` statt des tatsächlichen `gretl-integration-tests/target/qualification` und endete mit Exit 1, bevor Artefaktprüfung begann (`archive-authoring-error.json`). Beide anschliessend korrigierten externen Hilfsscripts bestehen; kein Produktorakel wurde gelockert und keine getrackte Datei geändert.

Ausgezählt wurden die **120 aktiven JUnit-XML der normalen Root-POM-Module**, keine fremden/inaktiven Profilreports: **651 Tests, 0 Failures, 0 Errors, 0 Skips**. Es sind 626 erhaltene bisherige und 25 neue JUnit-Fälle; parametrisierte Varianten innerhalb einzelner Methoden werden nicht als zusätzliche JUnit-Fälle erfunden.

| Aktives Modul | Tests | Failures / Errors / Skips |
| --- | ---: | --- |
| gretl-api | 17 | 0 / 0 / 0 |
| gretl-runner | 187 | 0 / 0 / 0 |
| gretl-groovy | 86 | 0 / 0 / 0 |
| gretl-i18n | 4 | 0 / 0 / 0 |
| gretl-logging | 38 | 0 / 0 / 0 |
| gretl-packager | 12 | 0 / 0 / 0 |
| gretl-database-tasks | 21 | 0 / 0 / 0 |
| gretl-io-tasks | 54 | 0 / 0 / 0 |
| gretl-cli | 59 | 0 / 0 / 0 |
| gretl-job-runtime | 25 | 0 / 0 / 0 |
| gretl-ilivalidator | 3 | 0 / 0 / 0 |
| gretl-interlis-tasks | 34 | 0 / 0 / 0 |
| examples/file-size-extension | 0 | 0 / 0 / 0 |
| gretl-dist | 13 | 0 / 0 / 0 |
| gretl-integration-tests | 98 | 0 / 0 / 0 |

## Pflicht-Testfallmatrix Phase 2

Alle folgenden Ergebnisse beruhen auf selbst ausgeführtem Produktcode und gelesenen fachlichen Assertions. Testnamen bezeichnen zusätzlich die im eigenen Pflichtlauf ausgeführten Komponenten.

| ID | Ergebnis | Eigenständig geprüftes Orakel |
| --- | --- | --- |
| PRJ-101 | Erfüllt | GroovyProjectLoaderTest und ProjectScriptLocalJobIT: gleiche job.groovy-Basenamen, identische lokale run-Namen und verschiedene Toplevel-/Bodywerte bleiben getrennt. Root/nested Scope und Binding-Isolation; eigene Prozesspipeline bestätigt kanonische IDs. |
| PRJ-102 | Erfüllt | CliProjectRunTest führt echten lokalen HTTP-ZIP-Download, Extract und Import aus und prüft Payload, Reihenfolge, eine Run-ID. ProjectScriptLocalJobIT und eigene Gzip/Extract-Dateipipeline liefern unabhängig gelesene Bytes und genau einen Bericht/Start/Terminal. |
| PRJ-103 | Erfüllt | ProjectScopeTest, veröffentlichter Prozessfixture und eigene Auswahl `:consumer:expandA`: nur dependsOn-Hülle; keine nachgelagerte Ausgabedatei oder unverbundene Action. |
| PRJ-104 | Erfüllt | Typisierte Cross-Project-Ausgänge über vorwärts geladene Includes, tatsächliche Download-/Extract-/Gzip-Dateien. Fehlende Datenkante liefert konkret UNDECLARED_RESULT_DEPENDENCY statt eines erfolgreichen Werts. |
| PRJ-105 | Erfüllt | after selektiert den Vorgänger nicht. Eigene separate Prozesse bestätigen, dass weder result noch resultOptional allein aufgrund von after zugreifen darf; Code 10 und konkrete Diagnose im Bericht. |
| PRJ-106 | Erfüllt | Consumer vor Producer, vollständiger unselektierter Datenzyklus, unbekannte Links, doppelte kanonische Tasks mit beiden Originalzeilen, fremde TaskRef-Herkunft. Eigene negative Prozesse bestätigen insbesondere UNKNOWN_TASK_REFERENCE und DEPENDENCY_CYCLE vor Runbeginn. |
| PRJ-107 | Erfüllt | ProjectScopeTest liest zwei verschiedene sql/update.sql über when, Konfiguration, Action, ctx.file, verschachteltes ctx.run und typisierten CleanupContext. Eigene Datei-/Gzip-/Extract-Probe liest getrennte Inhalte `ä-A` / `B-ü` aus verschiedenen Projektverzeichnissen und den eigenen Consumerpfad. |
| PRJ-108 | Erfüllt | Root/lokal/fremd, Long/Boolean/Path, Deklarationsdefault, Pflichtwert eines unselektierten Projekts, CLI > Parameterdatei > Runtime > Default, globale Config-/Secret-Aliase. Unbekannte explizite lokale/qualifizierte Parameter und ungültige Konversion scheitern vor Run. |
| PRJ-109 | Erfüllt | Zwei gleichnamige, qualifizierte Cleanups laufen im gemeinsamen Abschluss einmal in umgekehrter Registrierung bei Erfolg, Fehler, NO_DATA und Cancellation. Eigene Pipeline bestätigt `b/ca`; eigener Cleanupfehler hat ursprüngliche Datei und Zeile 3. |
| PRJ-110 | Erfüllt | Help/check/plan/mermaid registrieren qualifizierte Ziele/Parameter, erzeugen keine Task-/Cleanup-Arbeit, keinen HTTP-Aufruf und keine Runartefakte. Eigene drei Prozessvarianten bestätigen dies. Vertrauenswürdiges Toplevel bleibt ausführbar wie dokumentiert. |
| PRJ-111 | Erfüllt | Direkter Scriptpfad ignoriert absichtlich ungültige settings.gretl; alte Doppelpunkt-Tasknamen und Punkt-Parameter funktionieren weiter. Bestehende CLI-/DSL-/Runner-/Schema-1/2-/JAR-Suites sind unverändert und vollständig erfolgreich. |
| PRJ-112 | Erfüllt | Originalpfad und tatsächliche Zeile bei Registrierung, Compile/Toplevel, when, Feldkonfiguration, Action und Cleanup. Produktprozessfehler `:publish:run` meldet Originalzeile 3; kein virtueller Groovy-Dateiname in nutzbarer Fehlerdiagnose. Qualifizierte Taskevents, eine Run-ID und ein Start/Terminal. |
| PRJ-113 | Erfüllt | Gemeinsamer Runner bewahrt Fail-fast/NOT_RUN, NO_DATA, Taskfehler und kontrollierte Cancellation. Eigener NO_DATA-Prozess beendet normale Arbeit, behält Cleanup und Code 0; bestehende reale Cancellation-/SIGTERM-JAR-Regressionen bleiben grün. |
| PRJ-114 | Erfüllt | Unbekanntes Settingsdefault, unbekanntes qualifiziertes CLI-Ziel, unqualifiziertes CLI-Ziel und fehlende Settings scheitern mit klaren Codes vor Runbeginn. Eigene Proben ergänzen fehlende Settingsdefaults ohne CLI-Ziel und prüfen fehlende Runartefakte. |
| PRJ-115 | Erfüllt | ProjectHashTest bestätigt Stabilität/Root-Unabhängigkeit, Änderung durch Settings-/Skriptbytes und Ausschluss beliebiger Daten, nested Settings und unselektierter Skripte. Eigene unabhängige Python-Rekonstruktion der Domain und 8-Byte-Pfad-/Byte-Längen stimmt exakt mit dem realen Projektreport überein. |

PRJ-001–007 wurden als unveränderte Parser-/Value-Object-Regressionen mit ausgeführt. Die zusätzliche eigene Settings-Dateiaktion wird abgelehnt, Marker bleibt abwesend; ein enthaltenes Script-Symlink wird vor Runbeginn zurückgewiesen. PRJ-201–212 wurden als Phase-3-Anforderungen nicht qualifiziert.

## Architektur, Scope und Regressionen

Der gesamte Produktdiff wurde selbst gelesen. loadProject erzeugt genau einen JobDefinitionBuilder und ProjectTaskIndex, einen Compilerloader mit derselben CompilerConfiguration und je Script ein frisches Binding/Modell. Root zuerst, dann explizite Include-Reihenfolge; eindeutige virtuelle Namen; genau ein finales builder.freeze. Closures behalten ihr ursprüngliches Modell und der Loader wird nicht vorzeitig geschlossen. Der Index besitzt ausschliesslich definitionseigene TaskRefs, Operationstypen und Quellen; seine additive Linkexistenzprüfung ersetzt weder Builder noch Planner. Es entstehen keine fertigen Teil-JobDefinitions oder verschachtelten Runs.

ProjectPath bleibt der zentrale Übersetzer für lokale/absolute Namen; `file` wird in der Groovy-Fassade projektrelativ, während Java-ContextView.file seinen globalen jobRoot-Vertrag behält. CleanupScope implementiert den eingeschränkten CleanupContext, sodass auch dessen expliziter typisierter Closureparameter den eigenen Dateiscope erhält; kein result/ctx.run wurde dort freigegeben. Parameterauflösung, path/workPath und globale betriebliche Aliase verwenden den gemeinsamen bestehenden Mechanismus.

Die kleinen Additionen an ParameterDefinition/RuntimeConfig für kanonische Parameternamen und das neutrale SourceLocatedFailure-Metadatum sind im Phasenbericht begründet und gezielt getestet. Der Runner bekommt keine Groovy-Abhängigkeit. Die vorab gebundene Quellidentität verhindert ein erneutes Lesen nach Taskarbeit; der Hashvertrag selbst ist ausdrücklich auf Definitionsdateien beschränkt. Fehler behalten wirkliche Throwabletypen, Ursachen und vorhandene Originalpositionen; keine fiktiven Tasks oder Traces.

API, Taskmodule, POMs/Module/Dependencies/Buildprofile, SPI, ExecutionPlanner, SequentialJobRunner, ResultStore, RunInput, WorkspaceManager, ParameterResolver, OperationRegistry und eigentliche Loggingimplementierung sind im Phasendiff unverändert. Packager und JobJarMain wurden nicht für Projekte erweitert. Es gibt keine neue globale Registry, neue Engine oder neue Mavenpflicht für Jobautoren. Vorhandene fachliche Testassertions wurden nicht entfernt, umbenannt oder abgeschwächt.

## Tatsächliche JAR- und Prozessartefakte

Der eigene Audit bestätigt den sauberen Distributionskatalog für exakt candidateSha, alle Distributionsprüfsummen und **82 echte JAR- sowie 14 bisherige Script-Ausführungen, kein IMAGE**. B hat unabhängig zusätzlich jeden der 82 tatsächlich gestarteten JAR-Hashes neu berechnet und die zugehörigen command.json auf wirkliche `-jar`-Starts geprüft. Dies sind bestehende Einzeljob-Regressionen, keine behaupteten Projekt-JARs. Die sieben neuen Projekt-/Single-Prozesse aus ProjectScriptLocalJobIT liegen separat in E/`project-script-evidence/` und wurden auf Distributionsmanifesthash, Runidentität, Reihenfolge, Reports, Originalfehler und echte Dateiausgabe geprüft.

Direkt aus eigenen JAR-Bytes und unabhängig erzeugten Nutzdateien geprüft und in E/`artifact-samples/` kopiert:

- IO-Extract-JAR SHA-256 `e088202cfd0695737f7c2a2c854e194ae3ab302df1a48f518c5b7f8b9ffcf226`; echte Einstiegsklasse, Jobmetadaten Schema 1, Dependencyinventar Schema 2, genau ein Logback-SLF4J-Provider, kein CLI-/Packagercode. Entpackte payload.txt bytegleich zur kanonischen Eingabe, SHA-256 `7d876edbee47051cfe7aa5fa596f5ffbdb441c872000efe83c34aac66ec8fa6f`.
- Minimaler Erfolgs-/Fachfehlerjob starten dasselbe unveränderte JAR SHA-256 `1619e8f7ffc9b46b89e0d279cdd97d15137165d6e9d1938bb75c389d6c6993c9`. Erfolg schreibt exakt `minimal package ü\n`; Fehler Code 10 mit `selected-package-business-failure`; beide schreiben `cleaned`. Gelöschte Quellkopie, fremdes CWD, leeres Maven-HOME und PATH `/gretl-no-build-tools` sind nachgewiesen. Metadaten, Inventar, Klasse und Provider wurden direkt aus beiden JARs gelesen.
- Der neue veröffentlichte Projektprozess produziert bytegenau die synthetische UTF-8-Quelle, SHA-256 `cc5cf7ec6abe720fb07bc14a9242e32d9ada9b78047ce2e97e7be2ec5f3f0ec4`. Der vollständige Plan enthält `:prepare:run`, `:checks:local:run`, `:publish:run`; das Teilziel enthält nur die ersten beiden. E/`own-evidence-result.json` hält konkrete Originalartefaktpfade, Hashes, Run-IDs und alle sieben Prozessbelege fest.

Das unveränderte DistributionIT qualifiziert darüber hinaus Schema-1-Parameter/Help/Plan/Run und das Verbot der Toplevel-Ausführung beim Packen sowie Schema-2-Ressourcen-/Retentionfälle. Bestehende SelectedPackage-/PackageArtifactAssertions prüfen ausgewählte Provider/Services, Klassenpfadisolation, fremde Dependencies und unabhängige Fachreader. Keine Coverage der nicht instrumentierten Produktprozesse wird erfunden.

## Fremde Übergabeevidenz und eigene Archivbindung

As Handoff SHA-256 wurde selbst neu ermittelt und stimmt exakt: `0a2ef7ca1cb251e1d43d4ea06f69a1fa16c6c80798d397662c119ffa4d5b2c22`. Spezifikation, Implementierungsbericht und Evidence-Index stimmen mit den Bytes des Kandidatencheckouts überein; alle **851 Quell-/Test-/Fixture-/Buildinputs** aus before-inputs.json ebenfalls.

Beide aktuellen A-Archive before-final-normal und after-normal wurden vollständig neu gehasht: **je 3423 Dateien**, keine Abweichung, je 120 XML mit 651/0/0/0. Manifesthashes: Vorlauf `27e5db27e62ee42d7633aac35f7b9e70022f6c4aa27fb4733662735ab68f7408`, Nachlauf `74f1580315774b775a860c55d77cc331ec2a5e315d4ec3991e924a71525def8d`. Vorlaufsummary und Katalog tragen ehrlich phaseBaseSha/dirty=true; Nachlauf exakt candidateSha/dirty=false. Zusätzlich wurden die je 82 gestarteten JARs aus beiden Archiven erneut gegen ihre execution-SHA geprüft. Dies sind fremde Nachweise; sie ersetzen die oben dokumentierte eigene Reproduktion nicht.

E enthält die eigenen Pflichtlogs/Metadaten, alle aktiven XML, Auditdaten, ausgewählte tatsächliche JARs, redigierte Reports/Logs/Nutzdateien, den vollständigen Diff und zusätzliche synthetische Proben. Produkt-Rohkonfigurationen/Secretdateien aus JAR-Szenarien wurden nicht pauschal kopiert. E/`SHA256SUMS.json` bindet **383 Dateien**, SHA-256 `1753c4b3a48fec400f13f97fe7adfedaf5a310f956aaf37240df06b6776bd96e`.

## Findings und nicht qualifizierte Grenzen

Im geprüften Phase-2-Umfang bestehen **keine offenen CRITICAL-, MAJOR- oder MINOR-Findings**. Deshalb gibt es keine Produktkorrektur mit Datei:Zeile/Reproduktion anzufordern. Die beiden dokumentierten Reviewer-Hilfsscriptfehler sind keine Produktbefunde.

Nicht ausgeführt: `./mvnw -B -Pgeotools-probe,backing-services clean verify` (erst Gate 3), Projekt-Schema-3-/Fat-JAR-Qualifikation, realer IMAGE-/Windows-/Linux-/Native-/Produktionslauf. Diese Nichtumfänge werden nicht als erfolgreich qualifiziert ausgegeben. Vertrauenswürdiges Groovy-Toplevel bleibt ausführbar; Pfadprüfungen sind keine allgemeine Sandbox und keine atomare Garantie gegen gleichzeitig ausgetauschte Quelldateien. Kein neuer Dienst oder externes System wurde konfiguriert.

## Verdict

**PASS**

Alle 15 Pflichtorakel der Phase 2 und die vollständigen normalen Regressionen bestehen auf der exakten Kandidaten-SHA unabhängig. Der gemeinsame Builder-/Scope-/Runvertrag, negative Fälle, Originalquellen, fachliche Dateiergebnisse und echte bestehende JAR-Prozesse wurden selbst geprüft. Pflichtgate und exakter Audit sind erfolgreich; keine wesentliche offene Abweichung wurde gefunden. Diese Entscheidung gilt ausschliesslich für `3e684678aeafb0a9606a79712344adbc8cd70406`. A muss diesen Bericht byteidentisch in einem ausschliesslichen Review-Archivierungscommit übernehmen; jede nachfolgende Produkt-/Test-/Fixture-/Normänderung erfordert einen neuen Kandidaten und erneute unabhängige Prüfung. Phase 3 wird durch diesen Bericht nicht implementiert oder gestartet und bleibt gemäss aktuellem Auftrag verboten.
