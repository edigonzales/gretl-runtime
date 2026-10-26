# Gegenprüfung Phase A – Revision 1

## Identität und Prüfgegenstand

- Spezifikation: `docs/implementation/hardening-f1-f4/spec.md`, Version 1.1, SHA-256 `2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.
- Analysebaseline: `288137f116f2d861a1648b5eeaf889d463a5ea6c`; Nachprüfstand: `bc044302829ede67ead27038bfdf2be90d0b0063`; vorheriger, abgelehnter Kandidat: `291a00136f6e7406e70a78f7902f6b280f275ad9`.
- Geprüfter Revisionskandidat: `b7ada7598e2750112836162827c36d15586aacee`.
- Rootzustand vor und nach den Nachweisen: HEAD entspricht dem Kandidaten; `git status --short` leer. Eigene Tests liefen in der neuen, aus `git archive b7ada759…` erzeugten Kopie `/private/tmp/gretl-gate-a-review-r1-QaYP96`.
- Implementierermodell: laut Übergabe GPT-6-basiert, genauer Modellname unbekannt; keine erfundene Providerattestierung.
- Reviewermodell: tatsächlich konfiguriert `gpt-5.6-sol`; separate Agentensitzung `/root/gate_a_v11`, ohne Implementierungshistorie. Die erneute Prüfung folgt dem eigenen initialen FAIL und übernimmt die Reparaturbehauptung nicht als Beweis.
- Toolchain: JDK `25.0.3-tem`, Repositorywrapper Maven 3.10.0, macOS-Host. POSIX zusätzlich in digestgepinntem JDK-25-Linuxcontainer als UID/GID 65534, Netzwerk aus, kein `GRETL_IMAGE`.

## Urteil

**PASS**

Der einzige Befund des ersten Reviews, A-F2-01, ist im neuen Kandidaten geschlossen. Das effektive lokale Taskbudget wird jetzt vor `results.start` und dem synchronen `gretl.task.started`-Ereignis erzeugt. Eine eigene, gegenüber dem Kandidatentest variierte FakeClock-Probe bestätigt: Zeitverbrauch im synchronen Startsink gehört zum Tasklimit; nach 251 ms bei 250-ms-Limit werden Run und Task `CANCELLED/TIMEOUT`, Predicate und Body bleiben unberührt, Cleanup läuft genau einmal.

Die Änderung ist auf diese Budgetgrenze, ihre Regression und die unveränderte historische FAIL-Dokumentation begrenzt. Die erneut ausgeführten F1–F3-Suiten, Linuxproben, der sourcegebundene Standardbuild, Prozessnachweise, Audit und das Archiv sind grün. Es bleiben keine offenen Pflichtabweichungen für Gate A. Phase B/F4 darf auf diesem exakten Stand beginnen.

Wie bereits im initialen Review gilt Spezifikation 1.1: reine Abfragen von Token/Budget mutieren weder Cancel-Latch noch Beobachtungspräzedenz. Der Kandidat erfüllt dies; die frühere gegenteilige Interpretation ist kein Sollvertrag.

## Selbst ausgeführte Prüfungen

| Test / Befehl | Ergebnis | Evidenz |
|---|---|---|
| SHA/Status/Diff `291a001…..b7ada759…` direkt geprüft | sauber; Produktdiff ist ausschließlich das Vorziehen der Budgeterzeugung; zusätzlich Regression und Berichte | Sitzungsausgabe; `phase-a-review-291a001.md`, `phase-a-revision-1-implementation.md` |
| Isoliert: `./mvnw -B -pl gretl-runner,gretl-logging,gretl-job-runtime -am -Dtest=ReviewerTaskBudgetStartProbeTest,ScopedControlTest,LocalBudgetRegressionTest,BudgetPrecedenceTest,SetupFailureTest,StartBoundaryTest,WorkPathOwnershipTest,WorkspaceOwnershipTest,CancelCauseReportTest,DiagnosticFilePermissionsTest,SetupCompletionTest -Dsurefire.failIfNoSpecifiedTests=false test` | PASS; 65 Tests, 0 Fehler/Skips | `reviewer-r1-targeted-tests.log` |
| Eigene `ReviewerTaskBudgetStartProbeTest` mit 250/251 ms | PASS; Run/Task TIMEOUT, Predicate 0, Body 0, Cleanup 1 | im obigen Log; Reviewerquelle nur in isolierter Kopie |
| Isoliert `python3 reviewer-linux-posix.py` | PASS; unprivilegierte Ownership-/Modus-/Symlink-/ZIP-FS- und Diagnosefileprüfungen | `reviewer-r1-linux-PosixQualification.log`, `reviewer-r1-linux-DiagnosticPosixQualification.log` |
| Neuen Root-Standardlog und Audit einzeln geprüft | BUILD SUCCESS, 6:42; SHA `b7ada759…`, `sourceDirty=false`, 553 Tests, 0/0/0, 21 erfolgreiche Job-JAR-Operationen | `phase-a-revised-standard.log`, `phase-a-revised-audit.json` |
| Neue Root-Linuxprobe gelesen | beide Qualifikationsklassen Returncode 0 | `phase-a-revised-linux-result.log` |
| Archiv, Hash und Manifest kontrolliert | 3163 Dateien, 90 gespeicherte JAR-Pfade, 16 eindeutige JARs; Hash stimmt | `phase-a-revised-archive-result.json`, `phase-a-b7ada75-standard-manifest.json` |

Standardarchiv: `/tmp/gretl-hardening-v11-evidence/phase-a-b7ada75-standard.tar.gz`, SHA-256 `5598d388a7262e2a7e4cd34d72ae611367ce85001c8cfd57c6cd095382950364`.

## Eigene Gegenproben

### F1

Die neue isolierte Linuxprobe wurde nicht bloß aus der vorherigen Kandidatenevidenz übernommen. Unter UID/GID 65534 blieben fremde Eltern und Symlinkziele unverändert, nicht beschreibbare Roots wurden nicht repariert, neue Run-/Diagnoseobjekte waren privat, und der nicht-POSIX-Fallback blieb kollisionssicher. Workspace-/WorkPath-Ownershiptests bestanden erneut.

### F2

Die unabhängige Startbudgetprobe verwendet andere Grenzwerte als der neue Autorentest und prüft zusätzlich Run- und Task-Cause. Sie wäre auf `291a001…` mit `SUCCEEDED` rot gewesen und ist auf `b7ada759…` grün.

Daneben liefen die alten Task- und Invocation-Token-/Budgethandle-Fälle erneut: eingefrorene reine Reads nach Scopeende, `CONTEXT_CLOSED` vor bindender Altprüfung, keine Mutation von Task B. Die Pure-Poll-Fälle behalten eine zuerst erfasste echte Fachursache als FAILED/10. TIMEOUT, REQUESTED und INTERRUPTED erreichen weiterhin reale Reportfelder `cancelReason` und Terminalattribute `cancel_reason`; Catch, Predicate-false, Konfiguration, Return und Close bleiben kontrollierte Grenzen.

### F3

Startleaf-Kollisionen als Verzeichnis, Datei oder Symlink bleiben Code-2-Startfehler ohne `run.started`, Task, Job-Cleanup oder fremde Retention. Spätere Task-/Invocation-Setupfehler bleiben FAILED/10 mit strukturierter `task-setup`-Phase und einmaligem Abschluss. Die im ersten Review eigens geprüfte Kombination B-Setupfehler plus späterer Reportfehler blieb wegen des auf vier Produktzeilen begrenzten Diffs unverändert; die Kandidatensuiten für Setup/Report wurden dennoch erneut ausgeführt. Die frühe Setupursache bleibt primär, der Reportfehler separat diagnostiziert.

## Findings

Keine offenen Findings.

Der frühere Befund A-F2-01 ist geschlossen:

- vorher: `childBudget` nach `results.start` und `gretl.task.started`; unabhängige Probe beobachtete fälschlich SUCCEEDED;
- jetzt: `childBudget` vor beiden Übergängen; unabhängige Probe beobachtet CANCELLED/TIMEOUT und keine normale Arbeit;
- keine andere Produktlogik wurde in der Revision verändert.

## Abgleich mit Anforderungen

| Bereich | Status | Beleg |
|---|---|---|
| F1-01/02 Roots und sichere Neuanlage | PASS | Ownership-/Platformtests, unprivilegierte Linuxprobe |
| F1-03/06 exklusive Leaf und Retentionbesitz | PASS | StartBoundary/WorkspaceOwnership/WorkPathOwnership |
| F1-04 Diagnosefiles | PASS | DiagnosticFilePermissions und native Linuxprobe |
| F1-05 Symlink-/Plattformgrenze | PASS | Linux- und ZIP-FS-Qualifikation |
| F2-01 Budget ab ausgewähltem Taskstart | PASS | korrigierte Methodengrenze, Autorentest und unabhängige 250/251-ms-Probe |
| F2-02/08 scoped Handles und reine Polls | PASS | ScopedControlTest, 14 Fälle; keine Mutation durch reine Abfrage |
| F2-03/04 Checkpoints und bindender Catch | PASS | LocalBudgetRegressionTest einschließlich Predicate/Konfiguration/Return/Close |
| F2-05 Fehlerpräzedenz | PASS | BudgetPrecedenceTest und Pure-Poll-Erstfehlerfälle |
| F2-06 Cleanupbudget/Interrupt | PASS | gemeinsame Nachfrist- und Interrupttests |
| F2-07 echte Causes in Artefakten | PASS | CancelCauseReportTest und Hardening-Prozessfälle |
| F3-01 Startgrenze | PASS | StartBoundaryTest, SetupCompletionTest, Prozessfälle |
| F3-02 strukturierter Tasksetupfehler | PASS | SetupFailureTest und tatsächliche JSONL-/Reportprozesse |
| F3-03/04 einmaliger Abschluss/lazy Cleanupworkspace | PASS | SetupFailure-/Completion- und Prozessnachweise |
| F3-05/06 Primärursache/Reportfehler | PASS | frühere unabhängige Kombinationsprobe plus unveränderter Produktpfad und erneute Suites |
| F3-07 kein Retry/Rollback | PASS | Fail-fast- und Markerorakel |
| Gate-A-Prozesse und Modulgrenzen | PASS | Standardbuild, 16 Hardening SCRIPT/JAR-Prozesse, Enforcer, sourcegebundener Audit |

R1 (alte Handles), R2 (Cancel-Cause bis Artefakt) und R3 (Startfehler versus Tasksetup) sind geschlossen.

## Grenzen

- Keine Produktänderung durch den Reviewer; die eigene Testquelle liegt nur in der temporären Archivkopie.
- Der Reviewer führte keinen weiteren Root-Mavenlauf aus. Er prüfte den nach der Revision neu erzeugten, sourcegebundenen Root-Standardlog, Audit, Linuxnachweis, Manifest und Archiv; eigene Mavenläufe waren isoliert.
- Der Audit trägt `full=false`. Der vollständige Profilbuild samt Full-Audit ist nach Spezifikation 1.1 zusätzlich für Gate B Pflicht, nicht für Gate A.
- F4 wurde noch nicht geprüft oder implementiert und bleibt Gegenstand von Phase B.

Die Freigabe gilt ausschließlich für Produkt-, Test- und Vertragsstand `b7ada7598e2750112836162827c36d15586aacee`. Spätere Änderungen an Phase-A-Code oder -Tests erfordern den passenden erneuten Nachweis.
