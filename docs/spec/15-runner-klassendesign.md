# 15 — Kernklassen, Verantwortlichkeiten und Ausführungssequenzen

**Version:** 4.1, 18. Oktober 2026.  
**Normativ für:** technische Schnittstellen zwischen den Kernkomponenten und die Übergänge, an denen Ergebnisse, Fehler, Ressourcen und Logs entstehen.  
**Nicht das Ziel:** jede private Hilfsmethode vorgeben oder leere Klassen für ferne Backends erzeugen. Öffentliche SPI: [14](14-java-api-vertraege.md). Fachsemantik: [03](03-runtime-vertraege.md). DSL: [04](04-job-dsl-und-custom-tasks.md).

## CORE-001 — Abhängigkeitsrichtung

`gretl-api` enthält JDK-Typen. `gretl-runner` implementiert Planung und Ausführung ohne Groovy oder Fachbibliotheken. `gretl-groovy` übersetzt Scripts in Definitionen und Request-Preparations. `gretl-cli` übernimmt Startkonfiguration und Prozessabschluss. Ein kleines `gretl-logging`-Modul beziehungsweise ein entsprechend abgegrenztes Paket bündelt Backend, Redaction und Encoder. Taskbibliotheken enthalten ausschliesslich ihre fachlichen Adapter und Abhängigkeiten.

Die folgende Tabelle beschreibt Rollen. Eine Rolle kann als paketprivate Klasse oder begründete interne Komponente umgesetzt werden. Es gibt keine Pflicht zu einem Modul pro Tabellenzeile. Die Zuordnung einer Zustandshoheit darf nicht zwischen mehreren Komponenten dupliziert werden.

## CORE-010 — Klassenkatalog und Zustandshoheit

| Klasse | Eigentum / Verantwortung | Nicht ihre Aufgabe |
|---|---|---|
| `OperationRegistry` | Unveränderlicher Katalog geprüfter Providerdefinitionen samt Herkunft. | Handler starten, Dependencies herunterladen. |
| `JobDefinitionBuilder` | Geordnete Registrierung und Referenzausgabe; einmaliges Freeze. | Taskkonfiguration auswerten. |
| `ParameterResolver` | Einmalige Typisierung, Defaults, Quellenpriorität und Pflichtprüfung. | Secrets lesen oder den Scriptbody wiederholen. |
| `ExecutionPlanner` | Referenzprüfung, Zielhülle, Kantenarten und stabile Toposortierung. | Resultate erraten oder erzeugen. |
| `SequentialJobRunner` | Taskstartfolge, Outcomes, Job-Cleanup, endgültige fachliche Runentscheidung. | SQL-Transaktionen oder Publisher-Recovery. |
| `OperationInvoker` | Jeder konkrete Fachaufruf: neue Invocation, Request, Handler, Freigabe, Resultat. | Öffentlichen DAG erweitern. |
| `ResultStore` | Zustände und rohe Resultate dieses Runs, Herkunfts-/Zugriffsprüfung. | Reflection, JSON-Persistenz, Resume. |
| `RunControl` | Finishanforderung, kooperative Cancellation und monotones Budget. | Gefangene Fachexceptions zu globalen Fehlerpflichten machen. |
| `CloseStack` | Registrierte Closeables, LIFO-Freigabe, Liste von Freigabefehlern. | Loggerseverity in Erfolg/Fehler übersetzen. |
| `FailureArbiter` | Deterministische Kombination von Arbeitsergebnis, echten Fehlern, Control und Closefehlern. | Fachliche Reparaturentscheidungen. |
| `WorkspaceManager` | Eigene Run-/Task-/Invocation-Verzeichnisse und Retention. | Externe Ausgabebestände aufräumen. |
| `RunReporter` | Sanitisiertes Ergebnisdokument atomar als Datei bereitstellen. | Vollständige Taskresultate dumpen. |
| `RunEventSink` | Nichtsteuernde Lifecycleereignisse mit Kontext und Zeiten entgegennehmen. | Runstatus aus ERROR-Logs ableiten. |
| `FailureFormatter` | Begrenzter Exceptiongraph, stabile Fehlerreferenzen, Sanitizing und einmalige Detailausgabe. | Eine Business-Obligationsregistry führen. |

Mutable Runobjekte gehören einem Ausführungsthread. Nur lesende Cancellation und die ausdrücklich threadsicheren Log-/Progresszugänge gehen an Hilfsthreads. Keine globalen `currentTask`-Felder. Ein processweiter Bridge-Installationszustand ist ein Loggingbelang, nicht der Container aller Laufzeitdienste.

## CORE-011 — Verbindliche interne Datenformen

Die folgenden Modelle dürfen geschachtelte Records/Enums sein. Ihre Komponenten und Invarianten sind verbindlich, ihr genauer Dateizuschnitt nicht.

### Definition und Plan

- `JobDefinition`: `UUID definitionId`, geordnete `List<TaskDefinition<?>> tasks`, geordnete `List<ParameterDefinition> parameters`, `List<String> defaultTargets`, `List<CleanupDefinition> cleanups`, `Path jobRoot`. Collections eingefroren.
- `TaskDefinition<R>`: ausgegebene `TaskRef<R>`, Registrierungsindex, Beschreibung, `List<TaskLink> dependsOn`, `List<TaskLink> after`, optionales Predicate, optionales Timeout, `TaskBody<R> body`, optionaler `OperationType<?,R>` für dokumentierte Ausgänge und `SourceLocation`.
- `TaskLink`: entweder registrierte Referenzidentität oder vorläufiger Stringname. Auflösung erfolgt erst nach vollständiger Registrierung. Eine Stringliste ist keine Zeichenliste.
- `TaskBody<R>`: `R execute(TaskContext context) throws Exception`. **Kein Groovy-Typ.** Der Groovyadapter kapselt eine gespeicherte Closure dahinter.
- `TaskCondition`: `boolean test(TaskContext context) throws Exception`. Der Groovyadapter prüft Boolean strikt vor Übergabe; Groovy-Truth wird nicht als bedingungslose Konversion verwendet.
- `CleanupDefinition`: Name, Registrierungsindex, `CleanupBody` und Quelle. `CleanupBody.execute(CleanupContext)` ist void und darf Exception werfen.
- `SourceLocation`: Dateiidentität, optionale einsbasierte Zeile und Spalte. Unbekannt bleibt unbekannt; keine erfundene Zeile 1.
- `ExecutionPlan`: `definitionId`, ausdrücklich angeforderte Zielnamen, stabile geordnete Liste ausgewählter Tasks, aufgelöste Datenvorgängermengen und Kanten mit Typ `DEPENDS_ON` beziehungsweise `AFTER`.

### Resultate und Outcomes

Das Resultat ist **nicht** ein alleinstehendes `Optional<Object>`:

```java
sealed interface ResultSlot {
    record Value(Object value) implements ResultSlot {
        public Value { java.util.Objects.requireNonNull(value); }
    }
    enum NoValue implements ResultSlot { INSTANCE }
}

enum TaskStatus { PENDING, RUNNING, SUCCEEDED, SKIPPED, FAILED, CANCELLED, NOT_RUN }
enum RunStatus { CREATED, RUNNING, SUCCEEDED, NO_DATA, FAILED, CANCELLED }
```

`TaskExecutionState` hält während des Runs Status, optionale Start-/Endzeiten, monotone Dauer, Slot und optionale Fehlerreferenz. `TaskOutcome` ist dessen unveränderlicher **terminaler** Snapshot. `NOT_RUN` hat keine Start-/Endzeit und eine Dauer von 0 Sekunden; der explizite Status unterscheidet dies von einem sehr kurzen ausgeführten Task. Ein Predicate-Skip wurde bereits als Task begonnen und darf Zeiten besitzen, besitzt aber keine Invocation und keinen Wert. Unselektierte Tasks fehlen in der Outcome-Liste; sie werden nicht als fehlgeschlagen erfunden.

Ein `ResultSlot.Value` ist nur bei SUCCEEDED erlaubt. SUCCEEDED mit `NoValue` ist ausdrücklich möglich. SKIPPED besitzt einen Grund, FAILED eine echte Fehlerreferenz. Die Kombination `(PENDING, NoValue)` ist durch den Status von `(SUCCEEDED, NoValue)` unterscheidbar. Ein Feld mit nullable=true in einem vorhandenen Resultat ändert den Slot nicht.

`InvocationOutcome` enthält Invocation-ID, Eltern-Task-ID, Operation-ID, Status, Phasen-/Start-/Enddaten und eine optionale Fehlerreferenz. Die rohen Ergebnisse innerer Aufrufe gehen direkt an die Action zurück. Nicht jedes innere Ergebnis wird zusätzlich bis Runende gespeichert.

`FailureInfo` ist ein sanitisiertes Diagnoseobjekt, keine freigegebene Java-Serialization des Throwable: ID, Kategorie, Typ, sichere Meldung, Quellenposition, beschriebene Cause-/Suppressed-Beziehungen und Truncationflags. Der tatsächliche Throwable bleibt während Fehlerbehandlung unverändert für Java-Catch verfügbar. Die Formatierung ist in Kapitel 16 begrenzt.

`JobOutcome` enthält Run-ID, Jobidentität, Status, Exitcode, Ziele, TaskOutcomes, explizite Diagnosen und höchstens eine primäre Fehlerreferenz plus Nebenursachen. Ein Berichtfehler nach Ende der fachlichen Runnerarbeit wird vom CLI-Abschluss als Runfehler ergänzt; abgeschlossene Taskhistorie bleibt bestehen.

## CORE-020 — Methoden der zentralen Klassen

Signaturen sind hier technische Portverträge. Die dazugehörigen Typen sind oben beschrieben; diese Tabelle ist kein Auftrag, nicht implementierte Methoden mit Erfolgsstubs anzulegen.

| Klasse / Methode | Verhalten und prüfbare Nachbedingung |
|---|---|
| `OperationRegistry` / `static OperationRegistry load(ClassLoader loader)` | Liefert eingefrorenen Katalog; ServiceLoader nur in diesem Schritt, Herkunft mit erfassen. Mehrfachsymbol/ID mit beiden Bibliotheken melden. |
| `OperationRegistry` / `OperationType<?,?> requireSymbol(String symbol)` | Genau eine Definition oder Definitionsfehler; kein Lazy-Download. |
| `JobDefinitionBuilder` / `<R> TaskRef<R> register(TaskRegistration<R> input)` | Neue Referenz und monotone Registrierungsposition; doppelte ID sofort melden. |
| `JobDefinitionBuilder` / `void registerParameter(ParameterDefinition input)` | Typ-/Optionsprüfung ohne Wertauflösung. |
| `JobDefinitionBuilder` / `JobDefinition freeze()` | Einmaliger Übergang; spätere Änderungen `DYNAMIC_TASK_REGISTRATION_UNSUPPORTED`. |
| `ParameterResolver` / `ResolvedParameters resolve(JobDefinition definition, ParameterSources sources, ParameterMode mode)` | Liefert `ResolvedParameters` mit Werten und Herkunft; mode RUN verlangt Vollständigkeit, CHECK/PLAN erlauben fehlende Pflichtwerte. HELP verwendet diese Methode nicht. |
| `ExecutionPlanner` / `ExecutionPlan plan(JobDefinition definition, List<String> requestedTargets)` | Seiteneffektfreier Plan nach CORE-021. Keine Builder/Closures/Secrets. |
| `SequentialJobRunner` / `JobOutcome run(JobDefinition definition, ExecutionPlan plan, RunInput input)` | Ein Run, sequentiell; ruft Cleanup auch nach kontrolliertem Fehler auf. Resultiert in terminalem JobOutcome. |
| `OperationInvoker` / `<C,R> R invoke(OperationType<C,R> type, RequestPreparation<C> prepare, TaskFrame parent) throws Exception` | Neue Invocation vor Requestbereitung; normaler Javawert nach erfolgreicher Freigabe oder dieselbe Exception. |
| `ResultStore` / `void start(TaskRef<?> ref, Instant wallTime, long ticks)` | PENDING → RUNNING genau einmal. |
| `ResultStore` / `void complete(TaskRef<?> ref, TaskCompletion completion)` | Nur gültiger terminaler Übergang; doppeltes Complete ist interner Fehler. |
| `ResultStore` / `<R> R require(TaskRef<?> consumer, TaskRef<R> producer)` | Prüft Definition, Datenvorgänger, Status, Slot und Typ in definierter Reihenfolge; liefert R. |
| `ResultStore` / `<R> Optional<R> optional(TaskRef<?> consumer, TaskRef<R> producer)` | Gleiche Vorprüfungen; nur Skip/erfolgreich ohne Wert ergibt leer. |
| `CloseStack` / `<T extends AutoCloseable> T manage(T resource)` | Objektidentität einmal übernehmen und zurückgeben. |
| `CloseStack` / `List<Throwable> closeAll()` | Alle Ressourcen rückwärts schliessen, Liste echter Fehler zurückgeben, idempotenter interner zweiter Close ohne erneute Freigabe. |
| `RunControl` / `void requestCancel(CancelReason reason)` | Erste festgestellte Cancellation merken, keine Rücknahme. |
| `RunControl` / `void requestFinish(String reason)` | Jobfinish merken und internes Kontrollsignal erzeugen. |
| `RunControl` / `void checkBeforeWork()` | Neue Arbeit nach Finish/Cancel/Budgetablauf verhindern; kein fachlicher Retry. |
| `FailureArbiter` / `TaskCompletion resolve(WorkCompletion work, ControlSnapshot control, List<Throwable> closeErrors)` | Entscheidet nach CORE-024; kennt keine Publisher-/SQL-Codes. |
| `WorkspaceManager` / `Workspace openRun(UUID runId) throws IOException` | Privaten neuen Runordner erzeugen, niemals bestehenden fremden Ordner wiederverwenden. |
| `WorkspaceManager` / `Path openInvocation(TaskFrame parent, String invocationId) throws IOException` | Neuen privaten Unterbereich zurückgeben. Keine taskgesteuerte Pfadverkettung von IDs. |
| `WorkspaceManager` / `void applyRetention(Workspace workspace, JobOutcome result, KeepWork mode) throws IOException` | Nur eigene Arbeitsdaten; kein Löschen von Reports oder externen Fachoutputs. |
| `RunReporter` / `void write(JobOutcome outcome, ReportingState state, Path destination) throws IOException` | Temporär schreiben und ersetzen; liefert Erfolg oder IOException, nie falsche Erfolgsmeldung. |

`RequestPreparation<C>` hat genau `C prepare(InvocationFrame frame) throws Exception`. Ein `InvocationFrame` enthält seinen OperationContext, den lesenden Eltern-Taskbezug für die Groovy-Resultatfassade, seine frische Workspacebasis, Lifecyclephase und eigenen CloseStack. `TaskFrame` enthält Definition, TaskContext, Taskworkspace und Task-CloseStack. Beide sind interne Lebensdauerobjekte, keine öffentlich serialisierbaren Fachresultate. Der Groovyadapter verknüpft den inneren Scope mit diesem Frame, bevor er Defaults oder Konfiguration auswertet.

`TaskRegistration<R>` enthält dieselben deklarativen Felder wie TaskDefinition ohne von der Registry erst zu vergebende Referenz und Registrierungsposition. `TaskCompletion` enthält den gewünschten terminalen Taskstatus, Slot, Grund und optionalen tatsächlichen Throwable. `WorkCompletion` unterscheidet normalen Return, echte Exception und internes Finish-/Cancelsignal; `ControlSnapshot` speichert die kontrolliert festgestellte Reihenfolge. Diese begrenzten internen Typen dürfen Records oder lokale versiegelte Varianten sein; die Zustände dürfen nicht allein aus Optional oder einer Lognachricht rekonstruiert werden.

`RunInput` enthält vorbereitete Parameter, immutable taskConfig, Secretzugang, Workspace, Clock und MonotonicClock, RunControl, Logger-/Eventschnittstellen. Dies ist internes Constructor Wiring, kein öffentlicher Service-Locator. Die CLI baut es explizit; Tests können kleine Fakes injizieren. Kein Spring-Container erforderlich.

### CORE-021 — Planungsalgorithmus

1. Gesamte Taskregistrierung auf doppelte Namen und alle unbekannten Links prüfen, auch unselektierte.
2. Referenzidentitäten zuordnen. Vorwärtsreferenzen über Strings sind jetzt bekannt.
3. Vollständigen Datenabhängigkeitsgraphen auf Zyklen prüfen; Zykluspfad mit Kantenarten ausgeben.
4. CLI-Ziele verwenden, sonst Defaults. Ohne Ziele: Fehler. Mehrfach angegebene gleiche Ziele deterministisch auf den ersten Eintrag reduzieren.
5. Transitive Hülle ausschliesslich aus `dependsOn` bilden.
6. `after` nur zwischen bereits ausgewählten Tasks hinzufügen.
7. Kombinierten ausgewählten Graphen erneut prüfen.
8. Kahn-Sortierung mit PriorityQueue nach Registrierungsindex oder gleichwertigem stabilen Algorithmus. Keine HashMap-Iterationsreihenfolge als Vertrag.
9. Datenvorgängermengen separat vom kombinierten Reihenfolgegraphen behalten. `after` allein erlaubt keinen Resultatzugriff.

Planbildung ist für denselben Definitionbestand und dieselben Ziele deterministisch. Clock, Dateisystem, Thread-Scheduler und zukünftige Resultate beeinflussen sie nicht. Erforderliche Eingabedateien einer Fachoperation werden hier noch nicht geöffnet.

### CORE-022 — Sequenz eines typisierten Tasks

```text
SequentialJobRunner
  → RunControl.checkBeforeWork
  → TaskFrame öffnen / ResultStore.start / task.started
  → when auswerten
      false → task.skipped; KEIN Builder, KEIN Handler, KEINE Invocation
      true  → TaskBody des Groovy-/Java-Adapters
                → OperationInvoker.invoke(type, preparation, taskFrame)
                    → InvocationFrame öffnen / operation.started (phase=configuration)
                    → preparation(frame): frischer Builder, Defaults, Closure, build
                    → Requestklasse prüfen
                    → frischen Handler erzeugen
                    → phase=execution / Handler.execute
                    → obersten Resultattyp prüfen
                    → phase=cleanup / alle Invocation-Ressourcen freigeben
                    → FailureArbiter
                    → operation.finished oder operation.failed / Context schliessen
                    → Wert liefern oder originale Exception weitergeben
  → eigene Task-Ressourcen schliessen
  → FailureArbiter
  → erst jetzt ResultStore.complete
  → task.finished / task.failed / task.cancelled
```

Die Invocation-ID existiert bereits während Konfiguration und Factoryerzeugung. Ein fehlerhafter Setter ist kein verschwundener Fehler vor dem gemessenen Aufruf. Typisierte Tasks haben genau einen Fachaufruf, nicht einen zweiten versteckten Aufruf durch doppelte Adapterverkabelung.

Die Ergebnisregistrierung liegt **hinter** notwendiger Ressourcenfreigabe. Ein erfolgreicher Handler mit fehlerhaftem Writer-close ist kein erfolgreicher Producer. Eine bereits geschriebene Datei kann trotzdem existieren; dies ist keine Transaktionsgarantie.

### CORE-023 — Action und untergeordnete Aufrufe

Die Action startet mit eigenem TaskFrame. Jeder `ctx.run` verwendet denselben Invoker wie der typisierte Task und erzeugt einen eigenen InvocationFrame. Unqualifizierte `workPath`-/`log`-Helfer im inneren Konfigurationsblock sind auf diesen inneren Frame gebunden. Lexikalisch eingefangenes äusseres `ctx` bleibt das äussere Objekt; die Dokumentation verwendet deshalb darin unqualifizierte Helfer.

Eine fehlgeschlagene Invocation darf WARN-/Fehlerdetailereignisse erzeugen und ihre Exception weitergeben. Fängt die Action diese bewusst und endet normal, wird die Action erfolgreich. Der Run hat keine hinterlassene Business-Fehlerpflicht. Ein oberer Span beziehungsweise Taskstatus wird nicht allein wegen eines gescheiterten Kindes auf Fehler gesetzt.

Ein Javaaufruf `TaskContext.call(type, request)` kapselt einen bereits validierten Request als Preparation. Ein Groovyaufruf kapselt dagegen das spätere Bauen. Beide verwenden dieselben Kontext-, Handler-, Close- und Ereignisregeln.

### CORE-024 — Fehler, Finish, Cancellation und Cleanup

| Primäre Arbeit / Ereignisreihenfolge | Close/Cleanup | Ergebnis |
|---|---|---|
| Normaler Wert | Erfolg | SUCCEEDED mit Wert |
| Action-null oder explizite Void-Operation | Erfolg | SUCCEEDED ohne Wert |
| Nicht-Void-Handler liefert null | Beliebig | FAILED, `NULL_OPERATION_RESULT`; erforderliche Freigaben trotzdem versucht |
| Unbehandelte Fachexception | Erfolg oder weiterer Fehler | FAILED; ursprünglicher Fehler primär, weitere suppressed |
| Erfolgreicher Body | Closefehler | FAILED; erster Closefehler primär |
| `finishJob` | Erfolg | Aktueller Task SKIPPED/JOB_FINISHED; Run nach Job-Cleanup NO_DATA |
| `finishJob` | Closefehler | FAILED; Finishsignal ist keine echte Primärexception |
| Cancel kontrolliert zuerst erkannt | Danach Abbruch-/Closefehler | CANCELLED, Code 20; Nebenursachen erhalten |
| Echter unbehandelter Fehler zuerst erkannt | Danach Cancel | FAILED, Code 10; Cancel verdeckt Fehler nicht |
| Innerer Fehler bewusst behandelt | Action und deren Close erfolgreich | Action SUCCEEDED |

Es gilt der Zeitpunkt der **kontrollierten Feststellung**, nicht eine spekulative Reihenfolge unkorrelierter Uhrzeiten. `FailureArbiter` bekommt explizite WorkCompletion/Control-Werte. Er analysiert keine Logzeilen. Arbeitsergebnisse und Kontrollsignale dürfen in getrennten lokalen Variablen gehalten werden; ein universelles Workflow-Stateframework ist nicht erforderlich.

Cleanup wird pro begonnenem Run in umgekehrter Registrierung ausgeführt. Es erhält einen eingeschränkten CleanupContext und ein eigenes gemeinsames Nachfristbudget; standardmässig höchstens 30 Sekunden für Job-Cleanup und Flush zusammen, soweit die kooperativen Aufrufe dies erlauben. Ein bereits gesetzter Abbruch des Arbeitsbudgets überspringt Cleanup nicht automatisch. Ein nicht kooperierendes Close kann auch diese Frist blockieren; der externe Prozesssupervisor ist die harte Grenze.

Bei jedem Close werden weitere Freigaben versucht. Das zuerst vorhandene echte Throwable bleibt erhalten. Bei einem aus InterruptedException entstandenen Cancel merkt die Runtime den Interruptedzustand; sie kann für eigene Cleanup-/Flush-Aufrufe das Flag einmal klären und stellt es vor der Rückgabe wieder her. Adapter dürfen InterruptedException nicht als leere Datenmenge interpretieren.

`VirtualMachineError`, `ThreadDeath` und ein harter Prozessabbruch sind keine zuverlässig behandelbaren Fachfehler. Best-effort-Minimaldiagnose, kein grosser Heapdump/Throwablegraph unter OOM, keine behauptete kontrollierte Bereinigung. Gewöhnliche Libraryexceptions und AssertionError in einem Task werden dagegen ohne fachliche Sonderregeln erfasst. Ein konkret erkannter Core-Invariantenbruch ist Code 40, nicht jede beliebige Pluginexception.

### CORE-025 — Prozessabschluss und Bericht

Nach Taskarbeit: Job-Cleanup → fachliches JobOutcome → Workspace-Retention gemäss Vertrag → Runbericht schreiben → `run.finished` mit endgültigem Exitcode → Konsolensinks/optionalem Export einen begrenzten Flush geben → Prozessende. Logging bleibt bis zum Ende der erforderlichen Freigaben aktiv.

Ein erforderlicher Berichtfehler nach erfolgreicher Facharbeit ergibt Code 10. Es wird eine sichere `report.write_failed`-Meldung auf dem noch funktionierenden Ausgabekanal ausgegeben; kein Endlosversuch, diesen Fehler in dieselbe kaputte Datei zu schreiben. Eine bereits vorhandene echte Primärursache wird dadurch nicht ersetzt. Reine Telemetrieexportfehler ändern dagegen gemäss Kapitel 16 nicht den fachlichen Status.

Der Bericht beschreibt seinen Zustand zum Zeitpunkt seiner Erzeugung. Ein danach auftretender Flushfehler wird zusätzlich best effort in einer sicheren Abschlussmeldung beziehungsweise einem atomaren Berichtupdate ausgewiesen; daraus wird keine erfolgreiche Remotezustellgarantie. Reconcile-/Resume-Dateien entstehen nicht.

## CORE-030 — Groovy-Klassen und konkrete Portsignaturen

Die Java-SPI bleibt frei von Groovy. Folgende Rollen leben im Adapter:

| Klasse | Kernmethoden | Verantwortung |
|---|---|---|
| `GretlScript extends Script` | Map-first `task`, `action`, `parameter`; `defaults`, `cleanup`; eigenes `println(Object)` | Definitionen registrieren. Keine Fachkonfiguration im Toplevel. `println` wird als Anwendernachricht sicher geroutet, nicht als Status interpretiert. |
| `GroovyJobLoader` | `JobDefinition load(Path script, DefinitionEnvironment env)` | ClassLoader, Compilerkonfiguration und frisches Binding pro Definition; Originaldateinamen erhalten. |
| `GroovyTaskAdapter` | `TaskBody<R> typedBody(type, closure, location)`; `TaskBody<?> actionBody(closure, location)` | Closures speichern und erst im richtigen Frame binden. |
| `TaskConfigurationScope` | `getProperty(String)`, `setProperty(String,Object)`; feste Helper | Nur InputField-Zuweisungen, dokumentierte Werte, Symbole und Contexthelper. |
| `ActionScope` | feste Helper und Zugriff auf `ctx` | Normale Groovy-Action; keine Registrierung. |
| `CleanupScope` | erlaubte Teilmenge der Helper | Keine neuen Tasks und kein `ctx.run`. |
| `GroovyContextFacade` | `run(OperationType,Closure)`, `result(String)`, `result(TaskRef)`, gemeinsame Helper | Java- und Groovy-Ergebnisformen ausdrücklich trennen. |
| `ResultView` | `getProperty(String)`, ablehnendes `setProperty` | Nur ausdrücklich angebotene OutputField-Accessor-Funktionen. |
| `CompilerSettings` | `CompilerConfiguration create()` | Gemeinsame Konfiguration für Script und späteren Packager. Grape aus; keine native Sondersemantik. |

`GretlScript` und `GroovyContextFacade` implementieren die Map-first-Signaturen aus DSL-012. Top-level `parameter(Map,name)` registriert; einargumentiges Lesen ist erst im Ausführungsscope möglich. Bei registrierten Operationtypen zeigt das Binding deren Werte an, nicht eine nachgeladene Gradleklasse.

### CORE-031 — Bindungsalgorithmus

1. Registrierte Closure unverändert aufbewahren.
2. Beim Task-/Invocationstart `rehydrate(scope, original.owner, original.thisObject)` beziehungsweise eine nachweislich äquivalente Kopie erzeugen.
3. Auf dieser Kopie `Closure.DELEGATE_ONLY` setzen.
4. Bei maximal null Parametern `call()`; bei einem Parameter `call(contextFacade)`; bei mehr Parametern Definitions-/DSLfehler. Die implizite `it`-Form zählt als ein Parameter.
5. Bei typisierter Konfiguration Defaults über dieselben Converter/Setter setzen, Closure genau einmal ausführen, Pflichtfelder prüfen, `builder.build()` aufrufen.
6. Keine zusätzliche Auswertung durch Hilfe, Plan, Check, Metadatengenerierung oder Logging.

`DELEGATE_ONLY` ändert keine lexikalischen Variablen. `def url=...` kann weiterhin die Eingabeeigenschaft verdecken. Keine angebliche vollständige Groovy-Sandbox. Die offizielle [Closure-Dokumentation](https://docs.groovy-lang.org/latest/html/documentation/core-closures.html) beschreibt diesen Unterschied zwischen Closure-Kontext und Variablenbindung; die hier entworfene Fassade muss real getestet werden.

Propertyauflösung ist deterministisch:

- `getProperty`: dokumentiertes gesetztes Feld beziehungsweise Default; dann ausdrücklich erlaubtes `ctx`/Operation-Symbol; für notwendige Groovy-Infrastruktur wie `metaClass` die eng begrenzte Basisklassenbehandlung. Kein Owner-/Bindingfallback für unbekannte Felder.
- `setProperty`: ausschliesslich registriertes InputField, anschliessend dessen Converter und Setter. Unbekannt → gezielter Fehler. `ctx`, `delegate`, `owner`, `thisObject`, `metaClass`, `class` und interne Registrierungszustände sind nicht schreibbare Settings.
- `invokeMethod`: nur tatsächlich angebotene Helper und Groovy-Basismethoden; kein Erraten eines `url(...)`-Setters als zweite DSL. Eine fehlende Methode nennt die `name = wert`-Form, wenn der Name ein bekanntes Eingabefeld ist.
- `ResultView.getProperty`: nur OutputFields; keine freie Bean-/Recordreflexion. `.class`/`.metaClass` sind keine fachlichen Ausgänge.

Helpernamen als Methoden und fachliche Feldnamen als Properties müssen nicht pauschal gegenseitig verboten werden, wenn die Syntax sie eindeutig trennt. Der Registrytest prüft nur die festgelegten Reservierungen, keine willkürlich wachsende Sperrliste.

### CORE-032 — Fehler und Quellenpositionen

Konfigurationsfehler werden am Ort der falschen Closureanweisung erfasst. Die Registrierungszeile bleibt zusätzlich bekannt, ersetzt aber nicht die tatsächliche Fehlerzeile. Bei originalen Groovy-Stackframes werden Jobdatei, Zeile und Klasse erhalten. Nur technische Reflectionhüllen wie InvocationTargetException dürfen am Adapterübergang entfernt werden; Fachwrapper mit Zusatzinformation bleiben in der Cause-Kette.

Normale Ausgabe: verständliche erste Fehlermeldung, Task, Feld, konkrete Hilfe und Fehler-ID. JSON-/Runbericht: gleiche Ursache mit strukturierten Positionen. Keine gesamte Scriptdatei im Log. Der spätere Packager muss SourceFile-/Zeileninformationen und die Jobidentität bewahren, nicht absolute Buildhostpfade.

## CORE-040 — Kleine ausführbare Referenzprobe statt zweiter Runtime

Vor Freigabe von M2 entsteht ein eigener kleiner Testharness mit genau der wirklichen Basisklasse, den wirklichen Map-first-Methoden und der wirklichen Konfigurationsfassade. Testoperationen arbeiten nur mit lokalen Zahlen/Dateien. Sie sind ausdrücklich Fixtures und werden nicht als HttpDownload oder Publisher ausgegeben.

Der Harness beweist: Registrierung ohne Auswertung; Defaultanwendung; Assignmentkonversion; einmaliger Konfigurationslauf; Stringresultat und ResultView; null-/einparametrige Closures; neue Invocationpfade bei ctx.run; Fehlermeldung bei unbekanntem Feld; frische Scopes in zwei nacheinander geladenen Definitionen; kein Stateübertrag zwischen gleichnamigen Tasks.

Der öffentliche Java-Referenzbestand wurde separat auf Kompilierbarkeit geprüft. **Die echte Groovy-6-Ausführung dieses Harness ist hier noch nicht erbracht.** Das ist ein offen ausgewiesenes Implementierungsgate, kein Grund, ein konzeptionell anderes Ersatzframework zu bauen. Details und Nachweise stehen in [17](17-findings-und-pruefprotokoll.md).

## CORE-050 — Bewusster Implementierungsspielraum

Nicht normiert werden Dateinamen privater Utilityklassen, die konkrete PriorityQueue-Implementierung, ob ein interner Record geschachtelt liegt und welches kleine Testframework den Nachweis organisiert. Normiert bleiben Zustandshoheit, Ergebnisse, Sequenz, Fehlerpräzedenz, Herkunft, Quellenpositionen und Logginggrenzen.

Eine Klasse pro Event, ein generisches Reactor-/Actor-System, eine Dependency-Injection-Plattform oder ein universeller Exception-Workflow sind nicht erforderlich. Ein kleiner synchroner Runner mit gut geschnittenen Methoden ist das Ziel.
