# Taskfinalizer: Anforderungszuordnung

Diese Zuordnung beschreibt den implementierten Umfang und die gezielten
Nachweise; den Status der unabhängigen Phasengates führt der
[Arbeitsstand](WORK_QUEUE.md). Der Schlusskandidat und seine aktuellen Rohbelege
stehen im externen Reviewhandoff. Historische Belege bleiben an ihrem SHA;
identischer Produktcode wird ausdrücklich zugeordnet und nicht umetikettiert.

## Code- und Testeinstiege

- [PlanSemantics](../../../gretl-language-contract/src/main/java/ch/so/agi/gretl/language/PlanSemantics.java) und [TaskSchedule](../../../gretl-language-contract/src/main/java/ch/so/agi/gretl/language/TaskSchedule.java): reine Auswahl und zustandsabhängige Bereitschaft; [TaskScheduleTest](../../../gretl-language-contract/src/test/java/ch/so/agi/gretl/language/TaskScheduleTest.java).
- [SequentialJobRunner](../../../gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/SequentialJobRunner.java) und [TaskExecutionControl](../../../gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/TaskExecutionControl.java): tatsächliche Ausführung und wirksame Kontrolle; [TaskFinalizerRunnerTest](../../../gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner/TaskFinalizerRunnerTest.java).
- [GroovyFinalizerTest](../../../gretl-groovy/src/test/java/ch/so/agi/gretl/runtime/groovy/GroovyFinalizerTest.java): öffentliche DSL und Includeverträge.
- [FinalizerJobIT](../../../gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/FinalizerJobIT.java): 21 echte Distributions-/SCRIPT-/JARprozesse, inklusive beobachtetem Start und SIGTERM.
- [TaskFinalizerToolingTest](../../../gretl-tooling-core/src/test/java/ch/so/agi/gretl/tooling/edit/TaskFinalizerToolingTest.java), [IncludeEditsTest](../../../gretl-tooling-core/src/test/java/ch/so/agi/gretl/tooling/edit/IncludeEditsTest.java) und [TaskFinalizerServerIT](../../../gretl-lsp/src/test/java/ch/so/agi/gretl/lsp/TaskFinalizerServerIT.java): Core, Originalquellen, sichere Edits und echte stdio-JAR.
- [Beispielprüfung](../../../tools/task-finalizers/verify_examples.py): dokumentierte Erfolg-/Fehlerbefehle aus Checkout und Distribution, JAR und unveränderter gespeicherter GUI-Quelltext im separaten SCRIPTprozess.

## F01–F30

| Kriterium | Implementierung / entscheidender Nachweis |
|---|---|
| F01 DSL / Java | TaskRegistration/TaskDefinition/Builder und GretlScript; FinalizerDefinitionTest, GroovyFinalizerTest, FinalizerJobIT |
| F02 Identität / Fehler | Freezevalidierung und PlanSemantics: fremd, unbekannt, doppelt, self; Definitions-, Groovy- und Toolingtests |
| F03 Auswahlhülle | PlanSemantics-Fixpunkt nur über Datenvorgänger und Finalizer; TaskScheduleTest, Toolingpreview; nicht ausgewählte Owner fehlen |
| F04 Aktivierung | TaskSchedule.started/completed; erreichte terminale Owner aktivieren, NOT_RUN nicht; Runner- und generierte Scheduletraces |
| F05 einmal / Shared | ausgewählte Ownerbarriere und einmalige Taskidentität; TaskScheduleTest, TaskFinalizerRunnerTest, Projekt-SCRIPT/JAR-IT |
| F06 Ketten / Doppelrolle | aktivierter F kann G aktivieren, direktes F ohne Owner, NORMAL+FINALIZER ohne Wiederholung; Schedule-/Runnertraces |
| F07 Ordnung / Daten | eigene Relation, explizites AFTER und echte DEPENDS_ON-Barriere; Schedule-/Runner-/Resultregressionen |
| F08 früher Planfehler | Unionzyklen und nominaler Aktivierungsdeadlock vor Run; TaskScheduleTest, öffentlicher FinalizerJobIT, Core-/stdio-Diagnose |
| F09 gezielter Drain | normale Arbeit bleibt fail-fast; nur aktivierter Abschluss mit noch benötigten Voraussetzungen; Runnertraces |
| F10 Ownergrenze | letzte Kontrolle unmittelbar vor ResultStore.start; Scope-/Setup-/when-/Konfigurations-/Closefälle; TaskFinalizerRunnerTest |
| F11 normaler Task | derselbe Invoker und Taskscope für Action, getypten Task und innere Operation; Runner-/Groovy-/Gzipprozessfälle |
| F12 Abbruch / Timeout | effektive lokale Kontrolle, eigene Deadlines und neue Interrupts; Runnerfälle sowie echter SIGTERM in SCRIPT/JAR nach Readydatei |
| F13 finishJob | Abschlussdrain vor globalem Cleanup, unveränderte Fehlerpriorität; TaskFinalizerRunnerTest, GroovyFinalizerTest |
| F14 gemeinsames Budget | einmaliges RunControl.completionBudget, keine Verlängerung pro Finalizer; Budget-/Runnerfälle mit kontrollierter Uhr |
| F15 Invocationkontrolle | Framekontrolle in BaseContext, Task-/OperationContext und OperationInvoker bis Konfiguration/innerem Call/Close; Runnerfälle |
| F16 Fehlerpriorität | erste fachliche Ursache, sekundäre Finalizer-/Closefehler, Status und Reportrefs; FailureArbiterTest, Runnerfälle, öffentlicher JAR-Fehlerfall |
| F17 Ergebnisrechte | nur Datenpredecessors, keine Finalizer-/AFTER-Rechte auch bei resultOptional; Schedule-, Groovy-, Runner- und Toolingtests |
| F18 Ressourcen / Cleanup | Ownerscope vor Aktivierung geschlossen, eigene Finalizerscopes, LIFO-Cleanup danach; Runnertraces, Beispielmarker und Events |
| F19 Includes / Projekte | vorhandene Scope-/Anwendungskontexte und Original-Source-Maps; Groovy-, Projekt-SCRIPT/JAR-, Core-/stdiofälle |
| F20 Artefaktparität | derselbe Scheduler/Runner in SCRIPT/JAR; tatsächliches sourcefreies JAR aus fremdem CWD; alte FragmentJarProcessTest-Fälle. IMAGE-/Launcherpfad unverändert, daher kein neuer Containerclaim |
| F21 Introspektion / Paket | check/plan/Hilfe ohne Konfiguration/Body/Cleanup; tatsächliche SCRIPT/JAR-Kontrollen, Includeformat 3 unverändert, Hash-/Klassenprüfung im Pakettesthelfer |
| F22 Erklärbarkeit | FINALIZED_BY-Planbeziehungen, normalSelected, Owner/Trigger/Rollen, Events und primäre/sekundäre Fehlerrefs; Report-/Graph-/Prozessassertions |
| F23 Altverhalten | Legacy ExecutionPlanner-, Budget-, Failure- und FragmentJarProcessTest-Regressionen; bestehende Include-/Edit-/Wirefälle; keine zweite Engine |
| F24 Editor | eigene Taskreferenz, Optioncompletion/Signatur, Hover/Definition/References/Rename und Literaldiagnosen; TaskFinalizerToolingTest, TaskFinalizerServerIT |
| F25 Modeler / Vorschau | eigene Owner→F-Kante ohne Datenport, normal/conditional aus gemeinsamem Plan; Core-/Wireassertions plus installierter nativer Defaultpreview |
| F26 sichere Writes | Ownerdialog, Add/Remove/Disconnect, Rename/Delete, Setter/Batch; Shared-/Coverage-/Postimagefälle und native CRLF-/Unicode-Byteprüfung |
| F27 Host / Snapshot | Graphschema 2 und Featureversion 1, Basis-/Katalogversion 1 erhalten; alte Schemas abgewiesen, Owner-/Kontext-/Sourcevector-/Sessionguards; tatsächliche Hostcapturemethode und Wire-Stalefälle |
| F28 Ende zu Ende | echter stdio-Server, installierter VSIX mit F2/Save/Workspace-Undo/Redo und Sharedrefusal; saved-fixture-Manifest aus P4 separat in P5 ausgeführt; echte Prozess-Cancellation aus P3 |
| F29 Beispiel / Anleitung | kanonisches Beispiel und Distributionskopie, Erfolg und erwarteter Exit 10 mit Abschlussbelegen; verify_examples, Referenzsyntax-/Link-/Renderchecks |
| F30 unabhängige Gates | frische OpenCode2-/DeepSeek-Sessions je Phase, eigene neue Gegenproben, exakte SHA-/Digest-/Modell-/Sessionbindung und validierte positive XMLs; Schlussreview kumulativ seit Auftragsbasis |

## Herkunft und Grenzen

P4-Nativebelege gehören zu `e572807e6c7c4b0b2bda6a8c8892114028455ed2`.
Die spätere Dokumentations-/Beispielprüfung verändert diesen Produktcode nicht.
Der dort gespeicherte Quelltext unter `gui/P4-e572807/saved-fixture` bleibt samt
Manifest unverändert. Für die separate Runtimeprobe werden die ebenfalls
aufgezeichneten Datenbytes ausdrücklich als Jobrootressource `input.txt`
bereitgestellt: `file('input.txt')` ist relativ zur Einzeljobbasis, nicht zum
Fragmentordner. Diese Inputbereitstellung verändert keine Groovydatei und wird
in `gui-input-provisioning.json` belegt.

Die zwei CatalogSemanticParityTest-Fixturefehler des zusätzlichen breiten
Entwicklungslaufs wurden unverändert am P3-Commit reproduziert. Sie werden weder
als PASS noch als neue P5-Regression ausgegeben; siehe WORK_QUEUE. Root-/Dienst- /
GeoTools- und Releasevollgates werden durch diese gezielte Featurequalifikation
nicht als ausgeführt behauptet. Keine Veröffentlichung, kein Push/Merge und
keine Ausführung produktiver Jobs gehören zu dieser Abnahme.
