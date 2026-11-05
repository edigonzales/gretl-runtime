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

## P0 — REVIEW_PENDING

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

Nächste Arbeit: Kandidatencommit, frischer autonomer P0-Review mit eigener
Testausführung/Gegenprobe, maschinelle Validierung. P1 bleibt bis ABGENOMMEN gesperrt.

P1–P5: PLANNED; keine Featurelieferung behauptet.
