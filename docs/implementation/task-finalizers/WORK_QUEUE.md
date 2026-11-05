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

## P2 — REVIEWED_ACCEPTED

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

Korrigierter P2-Kandidat `9aa1205b44cb9ea583a3e94c79746940aa23e679`:
ABGENOMMEN im frischen Korrekturreview und maschinell validiert.
Originale `/Users/stefan/.cache/gretl-task-finalizers/review/P2-attempt-2/`.
Eigene Reviewer-Ausführung: 190 Tests, neue Drain-/Startgrenzenprobe mit 38
Assertions; keine offenen Findings. Das ablehnende erste Urteil ist separat
als `rejection-validated.json` identitätsgeprüft, keine nachträgliche Freigabe.

## P3 — REVIEWED_ACCEPTED

Feste Phasebase `9aa1205b44cb9ea583a3e94c79746940aa23e679`.
Öffentlicher Task-/Action-Header, vorhandene Linknormalisierung und
Include-/Projektindex erweitert. Finalizer behalten ihre eigene Scope- und
Ressourcenbasis. Additive SourceLocatedFailure-Metadaten erklären fehlerhafte
Finalizerdefinitionen am Ownerfragment. Neue immutable TaskFinalization-Daten
trennen normale Auswahl, ausgewählte deklarierte Owner, aktive Trigger und
wirkliche Rollen. Report-Schema 1 erhält diese Daten und explizite primäre/
sekundäre Fehlerrefs; bestehende Felder bleiben erhalten. Keine Paketformat-
oder Bundleversionsänderung: Descriptor 1/2/3 und bestehende JAR-Metadaten
1/2/3 bleiben gültig. Textplan und Mermaid/PlantUML zeigen finalizedBy in
Owner→Finalizer-Richtung und markieren bedingte Abschlusskandidaten.

Beispielgrundlage `examples/task-finalizers/` läuft mit echtem Gzip-Finalizer,
Ownerfehler, Sharedowner und globalem Cleanup. Vollständige Nutzerdokumentation
und Distributionskopie folgen in P5; keine IDElieferung vor P4 behauptet.
Die P0-UNKNOWN_OPTION-Probe ist jetzt der positive öffentliche Fehler-/Finalizer-
Vertrag. Die bisherigen Renderer-Ablehnungsproben wurden durch echte getrennte
Kanten-/Kandidatenassertions ersetzt.

Vorabgate mit JDK 25: 106 ausgewählte Unitfälle und 8 FinalizerJobIT-Fälle,
Exit 0, Fehler/Skips 0. Die ITs starten 21 echte Produktprozesse: SCRIPT und
quellenfreie Single-/Project-JARs bei Erfolg/Ownerfehler; Sharedfragment mit
zwei Ressourcenbasen; SCRIPT- und JAR-Introspektion ohne Bodyeffekt;
Aktivierungsdeadlock vor Nebenwirkungen; fehlende kompilierte Finalizerklasse
als präziser Startfehler; Fragmentzeile und ursprünglicher Fehler trotz
Finalizerfehler; SIGTERM nach beobachtetem Ownerstart in SCRIPT und JAR.
Vier bestehende Legacy-/Include-JAR-Fälle liefen zusätzlich erfolgreich mit
Source-Compiler-Wächter; der finale Kandidatenlauf führt sie erneut aus.

Die gemeinsame Pakettestprüfung erwartete bisher nur JAR-Schema 1/2 und wies
das bereits vorhandene Includeformat 3 ab. Sie prüft nun dessen Eingabeclosure,
Hash, Quellen-/Klassenidentität und Include-Kanten ausdrücklich; die alten
Schema-1/2-Assertions und Modul-/Provider-/Ressourcenprüfungen bleiben bestehen.
Die feste Szenariendeklaration enthält die neuen Tests samt tatsächlichen
Startzahlen; alte Zeilen wurden nicht abgeschwächt.

Vorabbelege `/Users/stefan/.cache/gretl-task-finalizers/evidence/P3/`
(`gate-precommit.log`, vorherige Red-/Entwicklungslogs im Elternverzeichnis).
Nächste Arbeit: sauberer Kandidatencommit, SHA-gebundener vollständiger gezielter
P3-Lauf, frischer unabhängiger Review. P4 erst nach validiertem ABGENOMMEN.
P4–P5: PLANNED.


P3-Produktgate am sauberen Commit
`3d1994e69b7cabd7ed4beb139a67dce410af159a`: 122 Tests (inklusive acht ITs),
21 wirkliche Prozesse, Exit 0, Fehler/Skips 0. Frische Distribution mit exakt
diesem sourceRevision und sourceDirty=false; XMLs, 21 Prozessablagen, Reports,
JARs und Katalog-/Manifestidentität extern gesichert unter
`/Users/stefan/.cache/gretl-task-finalizers/evidence/P3/candidate-3d1994e/`.

Anschliessend ausschliesslich Reviewtransport korrigiert: generierte
`.opencode/`-Konfiguration über einen prozesslokalen core.excludesFile-Eintrag
aus Gitstatus für Buildkinder ausnehmen. Keine globale/shared Gitkonfiguration
verändert, keine Produktbytes ausgeschlossen. Vier Adaptertests inklusive
echtem Git-Negativtest beweisen, dass getrackte Produktänderungen sichtbar
bleiben und die normale Umgebung unverändert bleibt. Frühere Produktbelege
bleiben dem SHA 3d1994e zugeordnet; der frische Review baut und testet den neuen
Transportkandidaten selbst vollständig. Produkt-/Testcode ausserhalb dieses
Adapters bleibt gegenüber 3d1994e bytegleich.


P3-Review 1 am Transportkandidaten
`0d48eeb46490b964c1e60897401d1996fe065d2c`: NACHARBEIT,
MAJOR P3-REVIEW-01. Alle 122 Produktprüfungen und die neue öffentliche
Reviewerprobe bestanden; der verpflichtende Python-Extratest war unter der
geerbten Review-Gitkonfiguration rot. Seine ungescopte Positivkontrolle erbte
selbst schon core.excludesFile und sah `.opencode/` deshalb nicht.

Korrektur ausschliesslich im Test: beide normalen Gitkontrollen erhalten eine
Umgebung ohne die geerbten GIT_CONFIG_COUNT/KEY_/VALUE_-Overrides. Die gescopte
Kontrolle und die strenge AM-product.txt-Assertion bleiben unverändert.
Vier Tests bestehen normal sowie in einer tatsächlich geerbten Reviewumgebung;
Beleg `evidence/P3/transport-fix/inherited-environment-tests.log` unter dem
externen Taskcache. Kein Produkt-/Runtime-/JAR-/Reporterbyte gegenüber dem
geprüften P3-Produkt verändert. Neuer Kandidat und frische unabhängige Prüfung
gegen dieselbe P3-Phasebase folgen; P4 bleibt gesperrt.


P3-Abschluss: unabhängiger Review 2 hat `b507e03f204c8201119e721518f84a38ea3e7780`
mit ABGENOMMEN beurteilt und am selben SHA alle 122 Produktfälle, vier
Adaptertests und eine eigene öffentliche SCRIPT-/JAR-Gegenprobe ausgeführt.
Modell, Session, frische XMLs, eigene Gegenprobe und unveränderte getrackte Bytes
sind maschinell validiert: externer Cache `review/P3-attempt-2/validated.json`.
P4 wurde erst danach begonnen.

## P4 — IMPLEMENTED

Feste Phasebase `b507e03f204c8201119e721518f84a38ea3e7780`.
Finalizer als eigene Taskreferenz, ownergerichtete Graphkante, ursprüngliche
Fragmentanker für Diagnosen und Queries; Erfolgsplanvorschau mit normaler und
bedingter Auswahl aus PlanSemantics. Kein Ergebnisrecht durch Finalizerkanten.
Ownerdialog und ausdrückliche Verbindungsart, gezieltes Disconnect, Rename und
Löschschutz; Add/Remove schreiben den deklarierenden Owner. Setter und Batch
unterliegen denselben Sharedsource-/Coverage-/Postimageprüfungen.
Graphschema 2 verhindert die Interpretation der neuen Enum durch alte strikte
Clients; Server bietet taskFinalizersVersion=1 und die VSIX prüft diese Fähigkeit
plus Graphschema beim Verbindungsaufbau. Basisprotokoll/Katalog/Viewstate bleiben 1.

Entwicklungsprüfungen im externen Cache `evidence/`: P4-targeted-development2.log
mit 68 Corefällen und 27 echten stdio-Fällen, 0 Fehler/Skips; danach zusätzlicher
Self-Finalizer-mit-Result-Regressionstest und erneuter vollständiger gezielter
Lauf. TypeScript und 83 Clientfälle erfolgreich (P4-client-development2.log).
Vorherige fehlgeschlagene Entwicklungsläufe bleiben erhalten.

Ein zusätzlich gestarteter breiter Entwicklungslauf (*Test) scheiterte an den
beiden unveränderten CatalogSemanticParityTest-Fällen: deren Repositoryfixture
verweigert die bereits seit dem Includeauftrag erforderliche Ownerinventur.
Dasselbe gezielte CatalogSemanticParityTest-Gate scheitert unverändert am
sauberen P3-Commit b507e03 im externen P4-baseline-Worktree, Beleg
`evidence/P4-baseline-catalog.log`. Kein Katalogprodukt-/Testfix ausserhalb des
Auftrags, keine abgeschwächten Assertions. Das P4-Gate wählt ausdrücklich die
betroffenen Core-/Query-/Edit-/Wiretests und läuft mit frischem Server/Katalogbau.
Native VSIX-Belege und unabhängiger Review stehen noch aus; kein P4-PASS.
