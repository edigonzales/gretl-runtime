# Ausgangsbasis und Designherkunft: GRETL Task-Finalizer

**5. November 2026 · Begleitbericht zu `task-finalizers` 1.0**

Dieser Bericht dokumentiert gelesene Quellen und daraus abgeleitete Anschlussstellen. Er ist kein zusätzliches Kriterienregister und kein Produktreview. Normativ ist [EXECUTION_PLAN.md](EXECUTION_PLAN.md), der Einstieg steht in [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md), der Zweitreview in [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md).

## 1. Festgehaltener Stand und Aussagegrenze

`origin/main` wurde über den GitHub-Connector mit dem vollständigen Commit **`31ca88ef4b4a5a4a219907156886f1da19adc644`** gelesen. Die Commitmeldung lautet `docs(include-tooling): record independently accepted P4 delivery`; der Parent ist `f081fdc42d49198baaa77cd321e8c12b9f07a880`.

Dieser neue Auftrag startet somit nicht mehr auf dem frühen Stand mit rein partieller Include-Unterstützung. Im gelesenen aktuellen LSP-Protokoll existieren bereits Sourcecontext-, Membership-, Ownergeneration- und Kontexttokenfelder. Die ursprünglichen Include-/Tooling-Abnahmen sind Ausgangsbelege, keine Abnahme eines zukünftigen Finalizerfeatures.

Für dieses Paket wurden Repositorydateien und offizielle Dokumentation gelesen sowie die erzeugten Dokumente und ihre Verpackung geprüft. **Kein Produktcode wurde geändert; keine Maven-, Runtime-, LSP-, native GUI- oder unabhängigen Modellreview-Tests des neuen Features wurden ausgeführt.** Die Dateiliste unten ist eine gezielte Anschlussstellenanalyse, kein vollständiger Repositoryaudit.

## 2. Konkrete Codebefunde

| Gelesene Quelle | Belegter Ausgangsstand | Konsequenz für den neuen Auftrag |
|---|---|---|
| `PlanSemantics` | JDK-only-Datenmodell mit DEPENDS_ON und AFTER; Zielabschluss über Datenvorgänger, aktive Kanten und Toposort; Datenrechte separat. | Neue Finalizerrelation, Kandidatenabschluss und aktivierungsabhängige Schedulingregeln ergänzen, nicht als AFTER tarnen. |
| `SequentialJobRunner` | Sequentielle Schleife stoppt bei Fehler/Cancellation/Finish; restliche ausgewählte Tasks werden NOT_RUN; danach globale Cleanups. | Selektiver Finalizerdrain benötigt runlokalen Zustand statt pauschaler Restterminalisierung. |
| `BaseContext` | Execution-, Cancellation-, Budget- und Closepfade beziehen sich direkt auf `environment.control()`. | Ein Abschlussmodus nur im Runnerloop wäre unzureichend. |
| `TaskContextImpl` | Resultzugriff über ResultStore; normale/innere Operationaufrufe prüfen Execution; finishJob speichert Runabsicht. | Gleiches TaskAPI verwenden, wirksame Kontrolle mitführen, Resultrechte nicht aufweichen. |
| `OperationInvoker` | Eigene Contexts, Vorbereitung, Handler, Catch/Close/FailureArbiter greifen mehrfach auf RunControl zu. | Erfolgreiche Abschlussoperation nicht durch alten Cancellation-Snapshot verfälschen; gesamte Pipeline testen. |
| `FailureArbiter` | Explizite WorkCompletion-/Control-/Closewerte; Failure, Cancellation, Finish und Result werden deterministisch getrennt. | Per-Task-Semantik erhalten und passenden Kontrollsnapshot einspeisen; Runaggregation separat. |
| `RunControl` | Erstes Cancellationreason bleibt gespeichert; completionBudget wird einmalig mit 30 Sekunden erstellt. | Vorhandenes gemeinsames Abschlussfenster nutzen, keine neue Frist je Finalizer. |
| `JobRuntime.run` | Shutdownhook fordert Cancellation an und wartet auf dieselbe completionBudget-Restzeit; gemeinsamer Script-/JAR-Ausführungspfad. | Kein paralleler Shutdownfinalizer und keine unbeabsichtigte Deadlineverlängerung. |
| `JobDslModel` | Gemeinsamer Definitionszustand für Fragmente; Projektindex und qualifizierte Referenzen; registerLinks kennt bisher Daten-/Afterlinks. | Finalizerlinks über bestehenden Scope-/Sourcevertrag anschliessen. |
| `GretlProtocol` | Basisversion 1 mit vorhandenen Includecontext-/Owner-/Membershipdaten, Graph-/Edit-/Navigationrequests. | Neue Kantenfähigkeit hinzufügen, aktuelle Schutzfelder nicht verlieren. |
| `tools/include-tooling/review.py` | Pythonadapter mit eigenem Worktree, CLI2, Modellzuordnungsprüfung, Policy und Verdictvalidierung; Auftrag/Kriterien/Digest sind aber auf include-tooling festgelegt. | Erfolgreiche Mechanik gezielt adaptieren, nicht unverändert falschen Auftrag starten oder ein drittes allgemeines Framework bauen. |
| `AGENTS.md` | Zwei vollständige Rootgates und Schutz historischer Aufträge/Fremdänderungen. | Explizite Nutzerpräzisierung zu gezielten lokalen Tests im neuen Auftrag festhalten; Root-/CI-/Releasegate selbst nicht ändern. |

Das gelesene Runner-Testverzeichnis enthält u. a. `ExecutionPlannerTest`, `JobDefinitionBuilderTest`, `FailureArbiterTest`, `BudgetPrecedenceTest`, `ContextAccessTest`, `CloseStackTest` und `FakeMonotonicClock`. Daraus wird keine Vollständigkeit des Testbestands und kein bestandenes neues Testergebnis abgeleitet. Codex liest die tatsächlich benötigten Fälle vor der Selektion.

## 3. Was dieses Paket neu entscheidet

Die folgenden Punkte sind **neue GRETL-Designentscheidungen**, keine aus den Quellen zitierte bestehende Funktion:

1. Aktivierung durch die erreichte RUNNING-/Terminalgrenze; SKIPPED zählt, NOT_RUN nicht.
2. Geteilte Finalizer einmal pro Run nach allen ausgewählten Ownern; direkte Auswahl bleibt möglich.
3. Bedarfsgesteuerte Finalizer-Voraussetzungen, explizite Erkennung von Aktivierungsblockaden und normale echte Datenvoraussetzungen auch im Drain.
4. Keine impliziten Resultrechte aus FINALIZED_BY und keine pauschale Erfolgspflicht des Owners ohne zusätzliches dependsOn.
5. Wirksame Task-/Invocationkontrolle getrennt von der weiterhin festgehaltenen Runabbruchabsicht.
6. Normale Run-/Taskfristen bei Erfolg bzw. fachlichem Fehler; vorhandenes einmaliges completionBudget erst für Cancellation/Finish-Abschlussarbeit.
7. Globale Cleanups bleiben separat; der vorhandene begrenzte Abschlusszeitraum wird nicht pro Finalizer erweitert.
8. Finalizerkanten Owner→Finalizer, aber Headeredit am Owner; vollständige Integration in bestehende Include-/Sharedsource-/Postimageguards.
9. Eigenständiger neuer Phasenauftrag P0–P5 mit gezielter Testauswahl und autonomen unabhängigen Reviews.

Diese Entscheidungen schliessen die in der Diskussion noch offenen Grenzfälle. Die Umsetzung muss sie prüfen und darf ihre Grenzen nicht nachträglich als vollständige Gradlekompatibilität darstellen.

## 4. Gradle als Referenz, nicht als unbesehen übernommene Runtime

Die offizielle Gradle-Dokumentation beschreibt `finalizedBy` als taskbezogene Finalisierung: Finalizertasks werden mit dem zu finalisierenden Task in den Graph aufgenommen und können nach dessen Fehlschlag ausgeführt werden; als weiterer Gradlefall wird UP-TO-DATE genannt. Dieses Grundkonzept motiviert die neue GRETL-Oberfläche.

Die Quelle ersetzt **nicht** die konkrete GRETL-Definition für NOT_RUN, when-false, mehrfache Owner, Datenrechte, lazy Voraussetzungen, Fehlerpriorität oder begrenzte Shutdownarbeit. GRETL bekommt insbesondere kein neues Incremental-/UP-TO-DATE-System. Abweichungen oder zusätzliche Bedingungen des neuen Vertrags sind im Plan ausdrücklich festgelegt.

## 5. OpenCode und Modellzuordnung

Die beim Abruf gelesene offizielle OpenCode2-Kommandodokumentation bestätigt `run`, explizites Modell, JSON-Ausgabe, Dateianhänge, Agentauswahl sowie Hilfe-/Auth-/Modellkommandos. Die Berechtigungsdokumentation beschreibt aktions-/ressourcenbezogene Regeln. Die tatsächlich lokal installierte Version und effektive Policy müssen trotzdem vor dem Review geprüft werden.

Die offizielle DeepSeek-Tabelle ordnet beim Abruf am **5. November 2026** `deepseek-flash` dem Modell **DeepSeek-V4.1-Flash-1017** zu. Der Alias ist kein ewiger Versionspin; der Auftrag verlangt erneute Verifikation am tatsächlichen Ausführungstag und tatsächliche Sessionmetadaten. Vorhandene lokale Anmeldung wird weiterverwendet, ohne vorgeschriebenen zusätzlichen Credentialexport.

## 6. Gepinnte Repositoryquellen

Alle folgenden Produktlinks beziehen sich auf denselben untersuchten SHA. Neuere Änderungen muss Codex in P0 als Delta prüfen.

- [Main-Commit](https://github.com/edigonzales/gretl-runtime/commit/31ca88ef4b4a5a4a219907156886f1da19adc644)
- [Repository-Anweisungen](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/AGENTS.md)
- [PlanSemantics](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-language-contract/src/main/java/ch/so/agi/gretl/language/PlanSemantics.java)
- [SequentialJobRunner](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/SequentialJobRunner.java)
- [TaskContextImpl](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/TaskContextImpl.java)
- [BaseContext](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/BaseContext.java)
- [OperationInvoker](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/OperationInvoker.java)
- [FailureArbiter](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/FailureArbiter.java)
- [RunControl](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/RunControl.java)
- [JobRuntime und Shutdownhook](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-job-runtime/src/main/java/ch/so/agi/gretl/job/runtime/JobRuntime.java)
- [JobDslModel](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/JobDslModel.java)
- [Aktuelles LSP-Protokoll](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/GretlProtocol.java)
- [Vorhandener Reviewadapter](https://github.com/edigonzales/gretl-runtime/blob/31ca88ef4b4a5a4a219907156886f1da19adc644/tools/include-tooling/review.py)
- [Runner-Testbestand](https://github.com/edigonzales/gretl-runtime/tree/31ca88ef4b4a5a4a219907156886f1da19adc644/gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner)

## 7. Externe Primärquellen und Verfahrensvorlage

- [Gradle: Controlling Task Execution / Finalizer tasks](https://docs.gradle.org/current/userguide/controlling_task_execution.html#sec:finalizer_tasks)
- [OpenCode2: Commands](https://opencode.ai/v2/docs/cli/commands/)
- [OpenCode2: Permissions](https://opencode.ai/v2/docs/permissions/)
- [DeepSeek: Models & Pricing / Modellaliaszuordnung](https://api-docs.deepseek.com/quick_start/pricing/)

Als Verfahrensvorlage wurde insbesondere der vorhandene Include-Tooling-Reviewprompt aus dem vorherigen Spezifikationspaket gelesen. Übernommen sind echte Code-/Zielprüfung, frische Session und Revision, unabhängige Gegenprobe, risikobasierte Tests und autonome Fortsetzung. Fachliche Include-Implementierungsaufträge werden nicht erneut gestartet.
