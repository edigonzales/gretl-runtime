# Task-finalizers: Arbeitsstand

Auftragsbasis und feste P0-Phasebase:
`9faf347f739e836976dd566bd31a65fc4ff91ac4` (lokales `main` und `origin/main`).
Branch `codex/task-finalizers`, eigener Worktree
`/Users/stefan/sources/gretl-runtime-task-finalizers`.
Der Ausgangsbaum war sauber; fremde Worktrees bleiben unverändert.
Delta zum Recherchepin `31ca88ef4b4a5a4a219907156886f1da19adc644`:
ausschliesslich die fünf neuen Auftragsdateien, keine Produktänderung.

## Lokaler Testvertrag

Der ausdrückliche Auftrag in EXECUTION_PLAN §1.1/§8 ersetzt für diese lokalen
Phasengates die pauschalen Rootvollgates durch risikobasierte gezielte Tests.
AGENTS.md, CI- und Releasepflichten bleiben unverändert; hier wird keine
Releasequalifikation behauptet. JDK Temurin 25.0.2, Mavenwrapper 3.10.0.
Eigener durch Copy-on-write kopierter Maven-Cache unter
`/Users/stefan/.cache/gretl-task-finalizers/m2`; Reviews erhalten eigene Kopien.
Keine Tests übersprungen, keine Assertions abgeschwächt.

## P0 — REVIEWED_ACCEPTED

Gelesen: normative Dateien, SOURCE_BASELINE, Rootanleitungen, PlanSemantics,
ExecutionPlanner, SequentialJobRunner, Kontrollzugriffe in BaseContext und
OperationInvoker sowie bestehende Plan-/Failure-/Budget- und Includetests.
Includes und vollständiges statisches Include-Tooling sind integrierter Bestand.
`finalizedBy` fehlt in DslVocabulary und in der Planrelation.

Ausgeführt im Featureworktree:

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-task-finalizers/m2 -pl gretl-runner,gretl-tooling-core,gretl-groovy -am -Dtest=ExecutionPlannerTest,FailureArbiterTest,BudgetPrecedenceTest,IncludeSemanticsTest,FinalizerBaselineTest -Dsurefire.failIfNoSpecifiedTests=false test
python3 -m unittest discover -s tools/task-finalizers -v
```

Ergebnis: Maven Exit 0; 38 JUnitfälle (10 Plan, 9 Failure, 11 Budget,
7 Include-Semantik, 1 erwartete Red-Probe), Fehler/Skips jeweils 0.
Die Red-Probe bestätigt UNKNOWN_OPTION mit finalizedBy und Originalzeile 1;
kein Taskbody läuft. Drei Adaptertests erfolgreich, einschliesslich negativer
Auftrags-/SHA-/Digest- und fehlender/staler/Nulltestbelege.
Originale: `/Users/stefan/.cache/gretl-task-finalizers/evidence/P0/`
(`baseline-command.json`, `baseline.log`, `baseline-exit.txt`, `test-summary.json`,
gesicherte XMLs). Kein clean ausgeführt.

Reviewmechanik aus tools/include-tooling für diesen Auftrag adaptiert;
historische Gates unverändert. PATH-opencode ist 1.18.22; effektives Binary:
`/Users/stefan/.cache/gretl-script-includes/opencode-v2/node_modules/.bin/opencode`
meldet `opencode v2.2.5`. Lokale Hilfe unterstützt standalone, JSON und Agenten.
Vorhandene DeepSeek-Anmeldung und deepseek/deepseek-flash sind verfügbar.
Offizielle DeepSeek-Preisseite am 5. November 2026 bestätigt
DeepSeek-V4.1-Flash-1017 für diesen Alias. Sessionidentität und tatsächliche
Prüffähigkeit sind erst durch den folgenden Review zu beweisen.

Geprüfter Kandidat `0ea7d59dc0e1157e411d33e0218430ff40ccc30f`.
Originalreview und extern validiertes Gate:
`/Users/stefan/.cache/gretl-task-finalizers/review/P0-attempt-1/`
(`review.md`, `verdict.json`, `session-export.json`, `validated.json`).
Session `ses_e53d26e8affecEpZldJikg4jWB`, OpenCode 2.2.5 / DeepSeek Flash;
eigene Testausführung und eigene Java-/Adaptergegenprobe, keine Findings.

## P1 — REVIEWED_ACCEPTED

Feste Phasebase `0ea7d59dc0e1157e411d33e0218430ff40ccc30f`.
Neue immutable finalizedBy-Links und kompatible Alt-Konstruktoren, Auflösung
beim Freeze einschliesslich Eigentums-/Duplikat-/Selbstlinkvalidierung.
PlanSemantics bildet normale Auswahl und rekursiven Kandidatenabschluss;
TaskSchedule trennt tatsächlichen und möglichen Abschlussbedarf, hält
selektierte Ownerbarrieren ein und erkennt Aktivierungsblockaden in der
nominalen Erfolgsplanung. Datenrechte bleiben dependsOn-gebunden.

Gezielte Tests:

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-task-finalizers/m2 -pl gretl-runner,gretl-tooling-core,gretl-job-runtime -am -Dtest=TaskScheduleTest,PlanSemanticsTest,FinalizerDefinitionTest,ExecutionPlannerTest,JobDefinitionBuilderTest,FailureArbiterTest,BudgetPrecedenceTest,ContextAccessTest,CloseStackTest,DependencySourceEditsTest,PlanGraphRendererTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Belege: `/Users/stefan/.cache/gretl-task-finalizers/evidence/P1/`, zusätzlich
`P1-red.log` (fehlender neuer Vertrag) und Entwicklungslogs im Elternverzeichnis.
Gateergebnis: 82 JUnitfälle, 0 Fehler/Skips, Exit 0.
13 Schedulingtests enthalten konkrete Traces und 150 deterministisch generierte
DAG-/Statusabläufe; vier Java-Definitions-/Alt-API-/Runnerguardtests.
Die generierten Fälle deckten einen blockierten späteren Finalizer auf, dessen
nutzlose Voraussetzungen die aktive Arbeit festhalten konnten; korrigiert und
als eigener konkreter Regressionstest ergänzt. Ein noch normal vorgesehener D
bleibt im Drain für einen später ausgelösten G verfügbar.

Übergangsgrenzen ausdrücklich: Runner lehnt Finalizerpläne vor Nebenwirkungen
mit FINALIZER_EXECUTION_NOT_IMPLEMENTED ab (P2 entfernt dies). Renderer und
Dependency-Editweg lehnen die neue Kante ausdrücklich ab, anstatt sie als AFTER
zu verarbeiten (P3/P4 ergänzen sie). DSL bleibt bis P3 geschlossen.
Keine P2-/DSL-/JAR-/GUIlieferung behauptet.

Geprüfter P1-Kandidat `bbd37bdfb73e902a0a2b073e7a8f929b1cce3fd9`.
Originale und validiertes Gate unter
`/Users/stefan/.cache/gretl-task-finalizers/review/P1-attempt-1/`.
Unabhängiger Reviewer führte die 82 Tests sowie eine eigene Java-Gegenprobe
mit 63 Assertions aus; ABGENOMMEN ohne Findings. Sessionidentität und
unveränderte getrackte Bytes durch den Adapter validiert.

## P2 — REVIEW_PENDING

Feste Phasebase `bbd37bdfb73e902a0a2b073e7a8f929b1cce3fd9`.
Ein Runner konsumiert TaskSchedule; kein zweiter Closure-Ausführungspfad.
TaskExecutionControl trägt die wirksame Kontrolle durch Frame, Contexts,
Konfiguration, innere Invocation, ResourceClose und FailureArbiter. Normale
Arbeit nutzt Run-/Taskfristen; nach beobachteter Cancellation/Finish teilen
neue Abschluss-Tasks und globale Cleanups das vorhandene einmalige Fenster.
Lokale Abschluss-Timeouts canceln dieses Budgetobjekt nicht. Runabsicht und
ursprüngliche Fehler bleiben erhalten. Tasksetup nach RUNNING terminalisiert
korrekt und aktiviert Finalizer. Nur behandelte Interrupts werden am Taskende
konsumiert; der äussere finally stellt sie auch bei Fatalfehlern wieder her.

Gezieltes Gate (JDK 25, Exit 0, 186 JUnitfälle, 0 Fehler/Skips):

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-task-finalizers/m2 -pl gretl-job-runtime -am -Dtest=TaskScheduleTest,PlanSemanticsTest,FinalizerDefinitionTest,TaskFinalizerRunnerTest,ExecutionPlannerTest,JobDefinitionBuilderTest,FailureArbiterTest,BudgetPrecedenceTest,LocalBudgetRegressionTest,ScopedControlTest,ContextAccessTest,CloseStackTest,SequentialJobRunnerTest,OperationInvocationRunnerTest,SetupFailureTest,ResultStoreTest,RunReportWriterTest,CancelCauseReportTest,LibraryScopeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Originale `/Users/stefan/.cache/gretl-task-finalizers/evidence/P2/`:
`gate-final.log`, `summary.json`, gesicherte Ziel-XMLs. Erwartete anfängliche
Runnerguard-Red-Probe und Entwicklungslogs liegen im Elternverzeichnis.
31 neue Runnerfälle prüfen unter anderem zwei wirkliche Invocationwege nach
Cancellation, Finish, Sharedowner, Ketten, spätere normale Voraussetzungen,
Setup-/Prädikat-/Konfigurations-/Closefehler, Resultrechte, Secrets, geschlossene
Contexts, lokale und gemeinsame Fristen, Cleanup-Priorität sowie alte/neue
Interrupts und Fatalweitergabe. Die P1-Nichtunterstützungsprobe wurde durch die
positiven Runnerverträge ersetzt. Globale Cleanup-Reihenfolge bleibt umgekehrt.
Neue Ereignisse erfassen Aktivierungen und ausgeführte Rollen; Serialisierung
und öffentliche DSL-/Paket-/Planausgabe bleiben P3.

Erster Kandidat `1dc9c8f5febf272db4bdee4511511408dc960f71`:
Review `/Users/stefan/.cache/gretl-task-finalizers/review/P2-attempt-1/`
fordert NACHARBEIT, MAJOR P2-01 (F04/F10/F12/F22). Eine Cancellation beim
Öffnen des Taskscopes vor RUNNING aktivierte fälschlich den Finalizer.
Die eigene Startgrenzen-Red-Probe und die ausführbare Reviewerprobe bestätigen
diesen Defekt am ersten Kandidaten. Keine Abnahme dieses SHAs behauptet.

Korrektur: letzte wirksame Kontrollprüfung nach Scopeöffnung unmittelbar vor
ResultStore.start; erst danach Schedulerstart bestätigen. Ein unterdrückter
Start wird NOT_RUN und aktiviert nichts. Der reine Scheduler erlaubt eine
gezielte vorstartliche Unterdrückung eines bereits fälligen Finalizers, ohne
Geschwister zu unterdrücken. Frischer Interrupt und lokaler Timeout vor Start
lassen auch dessen nachfolgende Kette inaktiv. Finish vor Start separat geprüft.

Nachtest desselben vollständigen kleinen P2-Befehls: Exit 0, 190 JUnitfälle,
0 Fehler/Skips, 34 Runnerfälle und 14 Schedulerfälle. Frische Originale separat
unter `/Users/stefan/.cache/gretl-task-finalizers/evidence/P2-revision1/`:
`start-boundary-red.log`, `start-boundary-final.log`, `summary.json`, Ziel-XMLs.
Die ursprünglichen Belege des ersten Kandidaten bleiben separat erhalten.

Nächste Arbeit: korrigierter Kandidatencommit und frischer unabhängiger
P2-Korrekturreview gegen dieselbe Phasebase. P3 erst nach validiertem
ABGENOMMEN; P3–P5: PLANNED.
