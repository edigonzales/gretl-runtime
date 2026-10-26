# Gegenprüfung Phase A

## Identität und Prüfgegenstand

- Spezifikation: `docs/implementation/hardening-f1-f4/spec.md`, Version 1.1.
- SHA-256 der tatsächlich verwendeten Spezifikation: `2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.
- Analysebaseline: `288137f116f2d861a1648b5eeaf889d463a5ea6c`; dokumentierter Nachprüfstand: `bc044302829ede67ead27038bfdf2be90d0b0063`.
- Kandidat: `291a00136f6e7406e70a78f7902f6b280f275ad9`.
- Root-Sourcezustand bei Reviewbeginn und nach Archivierung: Kandidaten-SHA, `git status --short` leer. Eigene Proben liefen in der separaten, aus `git archive 291a…` erzeugten Kopie `/private/tmp/gretl-gate-a-review-np88QW`; nur dort wurden Reviewer-Testquellen ergänzt.
- Implementierermodell: laut Übergabe GPT-6-basiert; exakter Modellname unbekannt. Dies ist keine Providerattestierung.
- Reviewermodell: tatsächlich für diesen Agenten konfiguriert `gpt-5.6-sol`; separate Agentensitzung `/root/gate_a_v11`, ohne Implementierungshistorie.
- Toolchain: macOS/Darwin-Host, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`, Repositorywrapper Maven 3.10.0. Verpflichtende POSIX-Probe zusätzlich im digestgepinnten Linux/JDK-25-Container als UID/GID 65534, ohne Netzwerk und ohne `GRETL_IMAGE`.
- Standardarchiv: `/tmp/gretl-hardening-v11-evidence/phase-a-291a001-standard.tar.gz`, SHA-256 `50ecf34000f374107227eb11fc0cc2eb8f8eb5847cc82048fdec3b40fdb0e933`; Manifest `phase-a-291a001-standard-manifest.json`.

## Urteil

**FAIL**

F1 und F3 sowie die Restbefunde R1–R3 bestehen die gelesenen Code- und ausgeführten Verhaltensprüfungen weitgehend. Ein verpflichtender Teil von F2-01 ist jedoch nicht erfüllt: Das lokale Taskbudget beginnt erst nach Registrierung und synchroner Ausgabe von `gretl.task.started`, statt beim Start des ausgewählten Taskversuchs. Eine eigene deterministische Probe reproduziert dadurch einen erfolgreichen Bodylauf nach bereits verbrauchtem lokalen Limit. Ein offener MUSS-Vertrag verbietet PASS; Phase B/F4 darf noch nicht beginnen.

Wichtig zur früheren gegenteiligen Reviewinterpretation: Version 1.1 fordert ausdrücklich, dass `isCancellationRequested()`, `reason()`, `remaining()` und `expired()` reine Abfragen sind. Sie dürfen weder den globalen Cancel-Latch noch die Beobachtungspräzedenz mutieren. Der Kandidat erfüllt diesen korrigierten Vertrag in den geprüften Fällen; daraus entsteht **kein** Finding.

## Selbst ausgeführte Prüfungen

| Test / Befehl | Ergebnis | Evidenz |
|---|---|---|
| `sha256sum docs/implementation/hardening-f1-f4/spec.md`; `git rev-parse HEAD`; `git status --short`; Diff Baseline→Kandidat | Hash/SHA korrekt, Root sauber; betroffenen Produktcode und Tests direkt gelesen | Shellausgabe dieser Sitzung |
| Isoliert: `./mvnw -B -pl gretl-runner,gretl-logging,gretl-job-runtime -am -Dtest=ScopedControlTest,LocalBudgetRegressionTest,BudgetPrecedenceTest,SetupFailureTest,StartBoundaryTest,WorkPathOwnershipTest,WorkspaceOwnershipTest,CancelCauseReportTest,DiagnosticFilePermissionsTest,SetupCompletionTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS; 63 Tests, 0 Fehler, 0 Skips | `reviewer-targeted-tests.log` |
| Eigene kombinierte Probe `ReviewerCombinedFailureProbeTest`: B-Workspace-Setupfehler plus späterer Reportfehler | PASS; B-Body 0 Aufrufe, Cleanup genau einmal, Code 10, beide Fehlerereignisse; `run.finished.error_ref` bleibt bei der früheren Setupursache und wird nicht durch `REPORT_WRITE_FAILED` ersetzt | `reviewer-combined-failure-final.log` |
| Eigene F2-01-Probe `ReviewerTaskBudgetStartProbeTest` | **FAIL reproduziert**: erwartet CANCELLED/kein Body; beobachtet SUCCEEDED/Body ausgeführt | `reviewer-task-budget-start.log` |
| Eigene Linuxprobe aus isolierter Kandidatenkopie: `python3 reviewer-linux-posix.py` | PASS; UID/GID 65534, JDK 25.0.4.1, Image `eclipse-temurin@sha256:ba0f…e5e`, Netzwerk aus; denied root, Owner/Group/Modus, Symlinkroot, ZIP-FS, Report/Event 0600 und Eltern unverändert | `reviewer-linux-PosixQualification.log`, `reviewer-linux-DiagnosticPosixQualification.log` |
| Vom Reviewer einzeln gelesener Root-Standardlog und Audit | BUILD SUCCESS; Audit bindet `sourceRevision=291a…`, `sourceDirty=false`, 552 Tests, 0 Failures/Errors/Skips, `full=false`, 21 erfolgreiche JAR-Operationen | `phase-a-standard.log`, `phase-a-audit.json` |
| Archiv-/Manifestkontrolle | Archiv fertig; 3163 Dateien, 90 gespeicherte Job-JAR-Pfade, 16 eindeutige Job-JARs; SHA oben | `phase-a-archive-result.json`, `phase-a-291a001-standard-manifest.json` |

Der erste Entwurf der kombinierten Reviewerprobe erwartete die Fehlermeldung im deduplizierten Terminalrecord selbst und scheiterte korrekt an `error_ref`. Das Orakel wurde auf Identitätsgleichheit des Setup-Fehlers und Ungleichheit zum Reportfehler präzisiert; der endgültige Lauf ist grün. Dies ist keine Produktänderung.

## Eigene Gegenproben

### F1 – POSIX, fremde Eltern und Symlinks

Die isolierte Linuxprobe führte die produktnahen Qualifikationsklassen unter dem unprivilegierten Benutzer 65534 aus. Vorhandene Eltern blieben unverändert, neue Run-/Diagnoseobjekte waren privat, ein nicht beschreibbarer Root wurde nicht „repariert“, ein Symlinkroot blieb unter Betreiberhoheit und Kollisionen wurden verweigert. Das deckt insbesondere die Gefahr ab, dass ein Root-Test unter privilegiertem Hostbenutzer fälschlich grün wird.

### F2 – alte Handles, pure Polls und Budgetbeginn

`ScopedControlTest` prüfte gespeicherte Task- **und Invocation-Token/Budgethandles**: Nach Scopeende bleiben reine Abfragen eingefroren; bindender Altzugriff liefert `CONTEXT_CLOSED`; Task B bleibt erfolgreich. Die vier Pure-Poll-Varianten zeigten TIMEOUT über Token/Budget, ohne `cancellationObserved` zu setzen; eine danach zuerst erfasste echte `IOException("first-real-failure")` blieb FAILED/10 primär. `CancelCauseReportTest` verfolgte TIMEOUT, REQUESTED und INTERRUPTED als `cancelReason` im real serialisierten Bericht und `cancel_reason` in tatsächlichen terminalen JSONL-Records.

Die zusätzliche Startgrenzenprobe fand dagegen die Abweichung: Beim synchronen `gretl.task.started`-Sink wird die FakeClock um zwei Sekunden vorgerückt, während der Task ein lokales Limit von einer Sekunde besitzt. Weil `childBudget` erst danach erzeugt wird, erhält er eine neue volle Sekunde, startet den Body und endet SUCCEEDED.

### F3 – Startleaf und kombinierter Abschlussfehler

Verzeichnis-, Datei- und Symlinkkollisionen der Runleaf wurden direkt sowie über `JobRuntime` geprüft: Code 2, kein `run.started`/`run.finished`, kein Task-/Cleanupaufruf, Marker/Leaf unverändert. Nach erfolgreicher Runeröffnung liefern Task-Setupfehler dagegen FAILED/10, `phase="task-setup"`, `failurePhase="task-setup"`, Cleanup und genau einen Runabschluss.

Die eigene kombinierte Probe erzeugte erst in A eine Datei an Bs Taskverzeichnis und ersetzte danach den Reportparent durch eine Datei. Beobachtet: A SUCCEEDED, B-Body nicht ausgeführt, Cleanup genau einmal, Reportfehler separat, Code 10; das finale `error_ref` verweist weiterhin auf den Task-Setupfehler. Damit bleibt die frühe Originalursache auch bei nachfolgendem Reportfehler sicher sichtbar.

## Findings

| ID | Schwere | Datei/Methode | Reproduktion/Wirkung | Erforderliche Korrektur |
|---|---|---|---|---|
| A-F2-01 | Gate-blockierend | `gretl-runner/.../SequentialJobRunner.java`, `executeTask`, Zeilen 194–202 am Kandidaten | `results.start` und `environment.emit("gretl.task.started", …)` laufen vor `control.childBudget(task.timeout())`. Reviewerprobe mit 1-s-Limit und synchronem Startsink, der FakeClock um 2 s erhöht: beobachtet SUCCEEDED und Bodyaufruf; erwartet CANCELLED und kein Body. Die synchrone Startregistrierung/-beobachtung wird so vom lokalen Limit ausgenommen. Die danach liegende Workspacevorbereitung verwendet bereits das erzeugte Budget und ist nicht Teil dieses konkreten Befunds. | Lokales effektives Budget am Beginn des ausgewählten Taskversuchs erzeugen, vor `results.start` und dem ersten potentiell zeitverbrauchenden Taskstartereignis, und dasselbe Budget anschließend für Workspace/Predicate/Konfiguration/Body/Close verwenden. Regression mit FakeClock an dieser Grenze ergänzen. Danach neuen Kandidaten vollständig nachtesten und erneut unabhängig prüfen. |

## Abgleich mit Anforderungen

| Bereich | Status | Code-/Testbeleg |
|---|---|---|
| F1-01/02 bestehende Roots, neue Pfade | PASS | `JobRuntime.prepareDirectory`, `WorkspaceManager.createPrivateDirectories`; Linuxprobe und Ownership-/Platformtests |
| F1-03/06 exklusive Leaf, begrenzte Retention | PASS | `WorkspaceManager.openRun/applyRetention`, Identitätsprüfung; Kollision/Replacement/Retentiontests |
| F1-04 Diagnosefiles | PASS | `PrivateDiagnosticFiles`, `EventFileSink`, `RunReportWriter`; native Linuxprobe |
| F1-05 Plattform/Symlink | PASS | POSIX- und ZIP-FS-Proben; Symlinkroot/-leaf-Tests |
| F2-01 effektives Budget ab Taskstart | **FAIL** | `SequentialJobRunner.executeTask:194–202`; `reviewer-task-budget-start.log` |
| F2-02/08 scoped Handles, reine Beobachtung | PASS | `BaseContext`-Snapshot/closed binding, `RunControl.childBudget` ohne Mutation; `ScopedControlTest` |
| F2-03 Checkpoints | PASS, abgesehen von F2-01-Startpunkt | Predicate false, Konfiguration, Factory, Handlerreturn und Close in gezielter Suite |
| F2-04 Catch hebt Cancel nicht auf | PASS | `LocalBudgetRegressionTest` |
| F2-05 Präzedenz | PASS | `FailureArbiter`, `TaskFrame.failureBeforeCancellation`; `BudgetPrecedenceTest`, Pure-Poll-Proben |
| F2-06 gemeinsames Cleanupbudget/Interrupt | PASS | `RunControl.completionBudget`; Cleanup-/Interrupttests |
| F2-07 Cause-Snapshot bis Report/Event | PASS | Outcomes/Writer/Terminalattribute; alle drei CancelReason-Werte in `CancelCauseReportTest` |
| F3-01 Startgrenze | PASS | `openRun` vor `RunEnvironment`/`run.started`, `RunStartException`; StartBoundary/SetupCompletion |
| F3-02 strukturierter Tasksetupfehler | PASS | Catch um `executeTask`, `failurePhase`, Eventphase; Setup- und Prozessnachweise |
| F3-03/04 einmaliger Abschluss, lazy Cleanupworkspace | PASS | gemeinsamer Abschluss und `CleanupContextImpl`; SetupFailuretests |
| F3-05/06 Primärursache, Bericht/Terminal | PASS | kombinierte Reviewerprobe einschließlich späterem Reportfehler |
| F3-07 kein Retry/Rollback | PASS | sequentieller Fail-fast-Pfad und Markerorakel der Prozesssuite |
| G-01..05 / Gate-A-Prozesse | PASS mit Ausnahme des oben genannten F2-01-Vertrags | Standardbuild/Audit, Script-/JAR-Hardeningprozesse, Linux-POSIX, Modul-Enforcer. Der vollständige `full=true` Build ist laut Spezifikation erst Gate B Pflicht. |

R1 ist geschlossen: alte Token und Budgets beeinflussen spätere Scopes nicht. R2 ist geschlossen: konkrete Ursachen stehen in Outcome, Report und JSONL. R3 ist geschlossen: Startleafkollision und später Tasksetupfehler besitzen getrennte Orakel. Diese positiven Ergebnisse heilen A-F2-01 nicht.

## Grenzen

- Keine Produktreparatur vorgenommen; Reviewerquellen existieren nur in der temporären Archivkopie.
- Kein Root-Mavenlauf durch den Reviewer; der eingefrorene Root-Standardlauf und sein sourcegebundener Audit wurden nach Archivierung einzeln gelesen. Eigene Mavenläufe erfolgten isoliert.
- Kein `-Pgeotools-probe,backing-services`-Vollbuild durch den Reviewer; dieser ist nach Spec 1.1 für Gate B zusätzlich Pflicht, nicht für das Phase-A-Gate.
- F4 wurde vertragsgemäss nicht geprüft oder begonnen.

Wegen A-F2-01 ist Phase A nicht freigegeben. Nach einer Implementiererkorrektur ist ein neuer Kandidaten-SHA mit Regression, Standardnachweis und erneuter unabhängiger Gegenprüfung erforderlich.
