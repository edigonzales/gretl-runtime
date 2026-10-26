# M1-Prüfbericht — Java-API und sequenzieller Runner

> Historischer Meilensteinbericht: Aussagen und Modulnamen gelten für den hier
> dokumentierten Commit. Aktueller Zuschnitt: [Modulgrenzen](module-boundaries.md).

> **Historischer Bericht.** Dieser Bericht dokumentiert den Stand von M1
> (`0.1.0-M1`). M2 ergänzt den Groovy-Adapter und ist im
> [M2-Bericht](m2-report.md) dokumentiert; die aktuellen Testzahlen stehen in
> der [Build-Anleitung](build.md).

**Datum:** 18. Oktober 2026  
**Meilenstein:** M1 gemäss `docs/spec/10-implementierungsplan-fuer-coding-agenten.md`  
**Spezifikationsbasis:** 4.1  
**Projektversion:** `0.1.0-M1`  
**Ergebnis:** M1 ist für den beschriebenen Java-Core vollständig umgesetzt und
getestet. Groovy-DSL, CLI, Fachtasks und die Produkt-Loggingpipeline bleiben
ausdrücklich spätere Meilensteine.

## 1. Lieferumfang

Neu hinzugekommen ist das Produktmodul **`gretl-runner`** mit 64 Java-Quellen
und 87 Klassen im Artefakt `gretl-runner-0.1.0-M1.jar`. Es hängt nur von
`gretl-api` und dem JDK ab; `jdeps` zeigt ausschliesslich `java.base` und das
API-Modul. Es gibt keine Groovy-, SLF4J-/Logback-, GeoTools-, INTERLIS-,
pgJDBC- oder Gradle-Abhängigkeit.

Der Runner ist bewusst **sequenziell, prozesslokal und ohne Persistenz**:

- ein JobRun, ein Workspace, ein `ResultStore`;
- keine Dauerhaftigkeit über den Run hinaus;
- kein Taskcache, keine Up-to-date-Prüfung, keine automatische Wiederaufnahme,
  kein automatischer Vollretry;
- kein Workerprotokoll und keine Plugin-Isolation;
- keine Ausführung von Publisher-, XtfToGpkg-, GeoTools- oder
  INTERLIS-Fachlogik.

Die M0-Proben, die API, das Erweiterungs-JAR und die GeoTools-Testprobe bleiben
unverändert erhalten und laufen weiterhin im Build.

## 2. Implementierte Kernrollen

### Definition, Registry und Planung

| Typ | Aufgabe |
|---|---|
| `JobDefinitionBuilder` | geordnete Registrierung, TaskRef-Ausgabe, Forward-Referenzen, einmaliges Freeze, Ablehnung späterer Registrierung (`DYNAMIC_TASK_REGISTRATION_UNSUPPORTED`) |
| `JobDefinition`, `TaskDefinition`, `TaskRegistration`, `TaskLink`, `CleanupDefinition` | eingefrorene, unveränderliche Definitionsformen mit Quelle, Bedingung, Timeout und aufgelösten Vorgängern |
| `OperationRegistry` | ServiceLoader-Katalog mit Herkunft, Duplikatprüfung für ID und Symbol, keine Handlererzeugung beim Laden |
| `ExecutionPlanner`, `ExecutionPlan`, `PlanEdge` | Zielauswahl, transitive `dependsOn`-Hülle, `after` nur unter ausgewählten Tasks, Zyklusprüfung des vollständigen und des ausgewählten Graphen, stabile Kahn-Sortierung nach Registrierungsindex, getrennte Datenvorgängermengen |
| `ParameterResolver`, `ParameterDefinition`, `ParameterSources`, `ResolvedParameters` | deklarierte Typen `text`/`boolean`/`integer`/`path`, Defaults, Quellenpriorität, Pflichtprüfung, Pfadnormalisierung gegen das ursprüngliche Start-CWD, unbekannte explizite Namen als Fehler, unbekannte Runtime-Config-Einträge ignoriert |

### Ausführung, Ergebnisse und Fehler

| Typ | Aufgabe |
|---|---|
| `SequentialJobRunner` | Auswahlhülle in stabiler Reihenfolge, einmalige Taskausführung, Fail-fast, Skip, NO_DATA, Cancellation, Cleanup, Retention und terminales `JobOutcome` |
| `ResultStore`, `ResultSlot`, `TaskStatus`, `TaskCompletion` | Status und Wertpräsenz getrennt; Besitz über TaskRef und Definition; Datenvorgängerprüfung; `RESULT_NOT_PRODUCED`, `RESULT_NOT_AVAILABLE`, `UNDECLARED_RESULT_DEPENDENCY`, `FOREIGN_OR_UNKNOWN_TASK_REF`, `RESULT_TYPE_MISMATCH` |
| `OperationInvoker`, `RequestPreparation`, `InvocationOutcome` | frische Invocation vor Requestbereitung, Phasen `configuration`/`execution`/`cleanup`, frische Handler, Request-/Resultattypprüfung, `NULL_OPERATION_RESULT`, Closefehler, Cause-/Suppressed-Erhalt |
| `FailureArbiter`, `FailureInfo` | feste Präzedenz von Wert, Fehler, Finish, Cancel und Closefehlern; begrenzter Fehlergraph mit Cause-/Suppressed-Ketten |
| `RunControl`, `CancellationToken`, `TimeBudget` | erster Cancel-Grund bleibt stabil, Finish unabhängig gespeichert, monotones Budget, Kindbudget, kontrollierter Abbruch |
| `CloseStack`, `ResourceScope` | LIFO-Freigabe, Objektidentität einmal, alle Freigaben werden versucht, echte Closefehler werden gesammelt |

### Kontext, Workspace und Ereignisse

| Typ | Aufgabe |
|---|---|
| `BaseContext`, `TaskContextImpl`, `OperationContextImpl`, `CleanupContextImpl` | ACTIVE/CLOSING/CLOSED, Parameter/Config/Pfad/WorkPath, Secret-/Sensitive-Zugang, Cancellation, Budget, ResourceScope, Progress und Diagnostic |
| `TaskContextImpl` | `result`/`resultOptional`, synchrones `call`, `finishJob`; nach dem Scopeende `CONTEXT_CLOSED`; `ctx.run`-Groovy-Form folgt in M2 |
| `OperationContextImpl` | nur OperationContext; kein Resultat-/Taskstartzugriff für Handler |
| `CleanupContextImpl` | eingeschränkter Scope ohne Taskstart, `call` oder `finishJob` |
| `WorkspaceManager`, `Workspace` | Run-/Task-/Invocation-/Cleanup-Verzeichnisse unter dem eigenen Runroot; Retention `NEVER`/`ON_FAILURE`/`ALWAYS` nur für den eigenen Runbereich |
| `RunEvent`, `RunEventSink`, `ContextLogger` | interne, nichtsteuernde Lifecycle- und Taskmeldungen, Run-/Task-/Invocation-Identität, späte Logs als `gretl.late_log`; Sinkfehler verändern den Runstatus nicht |
| `RunInput`, `SecretRegistry`, `MonotonicClock` | explizites internes Constructor Wiring, Run-Secretregister und deterministische Testuhren |

`OperationTaskBody` und `RequestPreparation` sind der Java-interne typisierte
Taskpfad. Die Groovy-Fassade, `TaskConfigurationScope`, `ResultView` und
`ctx.run` werden erst in M2 ergänzt.

## 3. Tatsächlich ausgeführte Prüfungen

Alle Befehle wurden mit Temurin `25.0.4.1+1` und dem Repository-Wrapper
Maven `3.10.0` ausgeführt. Die Standardprüfung benötigt keine externen Dienste.

```sh
./mvnw -o clean verify
```

Ergebnis: **148 Tests**, 0 Fehler, 0 Fehlschläge, 0 übersprungen,
`BUILD SUCCESS`.

| Modul | Tests |
|---|---|
| `gretl-api` | 17 |
| `gretl-runner` | 115 |
| `gretl-m0-qualification` | 16 |
| **Standard gesamt** | **148** |

```sh
./mvnw -o -Pgeotools-probe verify
```

Ergebnis: **152 Tests**, `BUILD SUCCESS` (148 Standard + 4 GeoTools).
Der gezielte GeoTools-Aufruf `-pl gretl-geotools-probe -am` umfasst 21 Tests
(17 API + 4 GeoTools).

Ein vollständiger Lauf mit leerem lokalem Maven-Repository wurde ebenfalls
ausgeführt:

```sh
rm -rf /tmp/m1-fresh-final3
./mvnw -Pgeotools-probe verify -Dmaven.repo.local=/tmp/m1-fresh-final3
```

Ergebnis: **152 Tests**, `BUILD SUCCESS`; Maven, Plugins und Testbibliotheken
wurden ausschliesslich aus den dokumentierten Repositories aufgelöst.

Weitere Nachweise:

```sh
./mvnw -pl gretl-runner -am test          # 115 Runner-Tests
./mvnw -pl gretl-api dependency:tree -Dscope=compile
./mvnw -pl gretl-runner dependency:tree -Dscope=compile
jdeps --multi-release 25 --ignore-missing-deps \
  --class-path gretl-api/target/gretl-api-0.1.0-M1.jar \
  -summary gretl-runner/target/gretl-runner-0.1.0-M1.jar
```

`jdeps` meldet für den Runner nur `java.base` und `gretl-api`. Das
Runner-Manifest trägt `Automatic-Module-Name: ch.so.agi.gretl.runtime.runner`.

### Testklassen des Runners

| Testklasse | Tests | Schwerpunkt |
|---|---:|---|
| `JobDefinitionBuilderTest` | 11 | Registrierung, Freeze, Forward-Refs, fremde Refs, Defaults, Cleanup, Parameter |
| `ExecutionPlannerTest` | 10 | Zielhülle, Stabilität, `after`, Zyklus, unbekannte Ziele, Duplikate |
| `OperationRegistryTest` | 6 | ServiceLoader, Duplikate, unbekannte Symbole, Providerdefekte |
| `ParameterResolverTest` | 10 | Typen, Defaults, Quellen, Pflichtmodus, Pfade, Fehler |
| `ResultStoreTest` | 12 | Status/Slot, Besitz, Datenvorgänger, Skip/NoValue, Fehler, NOT_RUN |
| `FailureArbiterTest` | 9 | Präzedenz von Wert, Fehler, Finish, Cancel und Closefehlern |
| `FailureInfoTest` | 4 | Cause-/Suppressed-Graph, Zyklus, Kürzung, Null |
| `RunControlTest` | 7 | Cancel, Finish, Timeout, Kindbudget, Priorität |
| `CloseStackTest` | 4 | LIFO, Duplikat, alle Freigaben, Idempotenz |
| `SequentialJobRunnerTest` | 19 | Reihenfolge, Fail-fast, Skip, Finish, Cancel, Timeout, Retention, Events, Workspace-Datei |
| `OperationInvocationRunnerTest` | 16 | `ctx.call`, frische Handler, Konfigurationsfehler, Nullresultat, Void, Closefehler, Cleanup, Kontextende |
| `ContextAccessTest` | 3 | Parameter/Config/Pfade/Secrets/Report/Progress, geschlossener Context |
| `WorkspaceRetentionTest` | 4 | Run-/Task-/Invocation-/Cleanup-Verzeichnisse und Retention |

## 4. Abgleich mit den M1-Abnahmefällen

### TC-R (Definition und Plan)

| Fall | Status |
|---|---|
| TC-R01 Ziel mit transitiven Vorgängern | bestanden (`ExecutionPlannerTest`, Runner-Test) |
| TC-R02 mehrere freie Tasks, stabile Reihenfolge | bestanden |
| TC-R03 `after` auf unselektierten Task | bestanden: keine Auswahlwirkung |
| TC-R04 `after` zwischen ausgewählten Tasks | bestanden: Reihenfolge, keine Datenkante |
| TC-R05 doppelte ID, unbekannte Referenz, Zyklus | bestanden: Definition-/Planfehler vor Taskarbeit |
| TC-R06 Zyklus durch ausgewählte `after`-Kante | bestanden: Kantenart im Fehlertext |
| TC-R07 `check`/`plan` ohne Taskclosure | strukturell bestanden: Planner ist rein; keine Groovy-Closure vorhanden |
| TC-R08 Top-level-Code beim Laden | nicht anwendbar ohne Groovy-Adapter (M2) |
| TC-R09 Registrierung im laufenden Body | bestanden als Freeze-/Nachregistrierungsfehler |
| TC-R10 keine Ziele und keine Defaults | bestanden: `MISSING_TARGET` |

### TC-K (Kernklassen und Übergänge)

| Fall | Status |
|---|---|
| TC-K01 Registryfreeze / verspätete Registrierung | bestanden |
| TC-K02 stabile Toposortierung unabhängig von Map-Reihenfolge | bestanden |
| TC-K03 Statusstore vor Beginn, Skip, Void, Wert, Fehler | bestanden |
| TC-K04 Predicate false und Konfigurationsfehler | bestanden: keine Invocation bei Skip; Konfigurationsfehler mit Invocation-ID und Phase `configuration` |
| TC-K05 Handlerwert, danach Closefehler | bestanden: Wert wird nicht als Erfolgsresultat registriert |
| TC-K06 innerer `ctx.call`-Kontext und äusserer Kontext | bestanden für Java-Kontext/Invocation; ThreadLocal-MDC folgt M3 |
| TC-K07 Finish, Fehler, Cancel und Closefehler | bestanden für die Task-/Run-Präzedenz |
| TC-K08 mehrere Job-Cleanups nach Erfolg/Fehler/Cancel | bestanden: umgekehrte Reihenfolge, Fehlerpräzedenz |
| TC-K09 Berichtpfad nach Facharbeit nicht beschreibbar | nicht Teil von M1: `RunReporter`/JSON-Bericht folgt M3 |
| TC-K10 Workspace-Retention und Diagnoseartefakte | Retention bestanden; Diagnoseartefakte/Reportgrenzen folgen M3 |

### TC-E (Fehler, Stop, Freigaben) — M1-relevanter Kern

| Fall | Status |
|---|---|
| TC-E01 unbehandelte Custom-Exception | bestanden: FAILED, Exitcode 10, Fail-fast |
| TC-E02 schreiben und danach scheitern | bestanden: eine Ausführung, Datei bleibt, kein Retry |
| TC-E03 dieselbe Exception in Action bewusst gefangen | bestanden: Action SUCCEEDED, Invocation FAILED |
| TC-E04 ERROR-Diagnostic ohne Exception | bestanden: Diagnose allein ändert den Status nicht |
| TC-E05 Operation erfolgreich, Nachprüfung fehlerhaft | bestanden: Task FAILED, Invocation SUCCEEDED |
| TC-E06 `finishJob` nach leerer Liste | bestanden: NO_DATA, Exitcode 0, Cleanup |
| TC-E07 Finishsignal versehentlich gefangen | bestanden: weiterer `ctx.call` blockiert, NO_DATA |
| TC-E08 lokales Cleanup scheitert nach NO_DATA | bestanden: FAILED, Cleanupfehler sichtbar |
| TC-E09 Primärfehler und mehrere Cleanupfehler | bestanden: Primärursache und Secondary-Liste |
| TC-E10 Job-Cleanup scheitert nach Erfolg | bestanden: FAILED, Taskhistorie bleibt |
| TC-E11 kooperative Cancellation/Deadline | bestanden: CANCELLED, Exitcode 20 |
| TC-E12 Fehler vor nachfolgender Cancellation | bestanden: Fehler bleibt primär |
| TC-E13 Producerdatei im Workspace | bestanden: Consumer liest die Datei vor Runende |
| TC-E14 später nicht deklarierter Parameterzugriff | teilweise: unbekannter Parameter im Kontext ist ein Fehler; die Groovy-späte Closure folgt M2 |
| TC-E15 Lauf ohne Publisher-/INTERLIS-/GeoTools-JARs | bestanden: Runner-Abhängigkeiten nur API/JDK |

### TC-D (Java-Kernäquivalente)

| Fall | Status |
|---|---|
| TC-D04 Action-null, Void-Operation, unerwartetes Nicht-Void-null | bestanden für den Java-Pfad |
| TC-D05 Resultat eines übersprungenen Tasks | bestanden: `result` scheitert, `resultOptional` ist leer |
| TC-D06 unbekannte/fremde Referenz oder Zugriff ohne `dependsOn` | bestanden |
| TC-D07 Action mit Schleife und `ctx.run` | Java-Äquivalent mit mehreren synchronen `ctx.call`-Aufrufen; Groovy-`ctx.run` folgt M2 |
| TC-D10 separat gebautes FileSize-JAR | M0-Nachweis bleibt bestehen |
| TC-D12/TC-D15/TC-D17, TC-GR01–TC-GR08 | M2, nicht behauptet |

### API, Logging und Build

- Die 27 API-Typen und ihre M0-/M1-Teilprüfungen bleiben bestanden.
- TC-A01 bis TC-A10 sind nur insoweit abgedeckt, wie sie API-/Invoker-/Context-
  Verhalten ohne Groovy betreffen; die vollständige M1-Zuordnung ist in
  Kapitel 09 nicht als eigene API-Abnahme definiert.
- TC-L01 bis TC-L24 sind **nicht** als M1-Nachweis behauptet. Der Runner
  liefert interne `RunEvent`-Objekte und einen minimalen `ContextLogger`;
  JSON-/Text-Encoder, Redaction, Eventdatei, Runbericht, MDC und EHI folgen M3.
- BLD-001 bis BLD-008 bleiben erfüllt; BLD-007 bleibt für M3/M6 offen.

## 5. Nachweisarten und Grenzen

| Stufe | Geprüft | Nicht bewiesen |
|---|---|---|
| Quellkompilation | 17 API- und 64 Runner-Quellen mit `--release 25`; JAR-Major 69 | Laufzeitsemantik nur durch die Tests, nicht durch Kompilation |
| Metadaten | Parent, Abhängigkeitsbäume, JAR-Manifeste, `jdeps`, 87 Runner-Klassen, Testklassen | Ein JAR-Manifest beweist keinen korrekten Plan oder Cleanup |
| Klassenladen | ServiceLoader-Provider, API/JDK-only Runner, frische Handler, frische Kontexte | Klassenladen beweist keine Fehlerpräzedenz |
| Reale Ausführung | Planung, Task-/Invocation-/Context-Leben, Ergebniszugriff, Fail-fast, Skip, NO_DATA, Cancellation, Timeout, Cleanup, Retention, Events, echte Dateien | Keine Groovy-Quellen, keine CLI, keine echten HTTP-/SQL-/INTERLIS-Tasks |

Die Tests verwenden lokale temporäre Verzeichnisse und keine externen Dienste.
Die Runtime ist ein Java-Core; ein „grüner“ Lauf ist kein Nachweis der späteren
Groovy-DSL, CLI oder Fachtasks.

## 6. Verbleibende Arbeit und Risiken

### M2 — Groovy-Anwenderfassade (nächster Schritt)

- `gretl-groovy` mit `GretlScript extends Script`, Scriptbasisklasse,
  Map-first-Überladungen und frischem Binding;
- `TaskConfigurationScope` mit `=`-Zuweisungen, `DELEGATE_ONLY`,
  Konverter/Setter, reservierten Helfern und konkreten Feldfehlern;
- gespeicherte Closures für `task`, `action`, `when`, `defaults` und `cleanup`;
- `ResultView` aus `OutputField` und String-ID-`result`;
- Parameterdeklaration im Script, `parameter(...)`, `file`/`path`/`workPath`;
- `ctx.run`, Closure-Arität 0/1, Quell-/Zeilenpositionen und CORE-040;
- TC-GR01–TC-GR08 und TC-D12/TC-D15/TC-D17.

### M3 — CLI, Fachtasks und Produktlogging

- CLI `run`/`check`/`plan`/`help`/`operations`, Runtimekonfiguration,
  Parameterdatei/CLI-Quellen, Secrets, Workspace-/Reportpfade;
- JSON-Runbericht, Text-/JSONL-Logging, Redaction, MDC, Eventdatei,
  FailureFormatter und TC-L-Fälle;
- HttpDownload, Extract, SqlExecutor, echte PostgreSQL-Integration und
  lokaler HTTP-Testserver.

### Weitere Grenzen

- Kein `RunReporter`/JSON-Abschlussbericht in M1; Outcome ist ein internes
  Objekt.
- Keine Produkt-Redaction: `FailureInfo.message` enthält derzeit rohe
  Exception-Texte; Secret-/Config-Dumps werden im M1-Code nicht erzeugt, aber
  auch nicht durch eine vollständige Sanitizerpipeline geschützt.
- Keine Berichtsgrenzen/Artefaktverwaltung; `Diagnostic` wird gespeichert, aber
  nicht als Report serialisiert.
- Keine harte Prozessgrenze für blockierende Bibliotheksaufrufe; Cancellation
  ist kooperativ.
- `workPath` prüft absolute Pfade/Traversal, aber noch keine Symlink-Fallen
  im erzeugten Pfad; M3-Härtung offen.
- `RunControl`-Cleanupbudget ist das Jobbudget; die separate 30-Sekunden-
  Nachfrist folgt mit dem CLI-/Reporting-Abschluss.
- Keine Linux-x86-64-Produktionsdistribution, kein Job-Fat-JAR, kein Native
  Image, kein Publisher/XtfToGpkg und keine Fachanwenderstudie.
- `CloseStack` sammelt auch `Error`-Freigabefehler und versucht weitere
  Freigaben; das ist bewusst robust, aber kein OOM-/Kill-Rollbackversprechen.

## 7. Fazit

Der Java-Kern von M1 ist vollständig implementiert: Planung, Taskdefinition,
Registry, Parameter, Ausführung, Ergebnisse, Kontexte, Fehlerpräzedenz,
Cancellation, Cleanup, Workspace und interne Ereignisse sind vorhanden und
durch **115 Runner-Tests** abgedeckt. Zusammen mit den bestehenden
API-/M0-Tests besteht `./mvnw verify` mit **148 Tests**; das GeoTools-Profil
besteht mit **152 Tests**. Ein frischer Repositorylauf bestätigt dies.

**M1 ist abgeschlossen.** Der nächste Schritt ist M2: die Groovy-6-
Anwenderfassade und der CORE-040-Harness. Er wurde in diesem Auftrag nicht
begonnen.
