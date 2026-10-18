# 03 — Allgemeine Runtime-Verträge

**Version:** Spezifikationsvorschlag 4.1, 18. Oktober 2026.  
**Normativ für:** Planung, Aufruf, Ergebnisse, Fehler, Ressourcen und Runabschluss.  
**Gilt gleichermassen für:** SqlExecutor, mitgelieferte Hilfsoperationen und eigene Custom-Tasks; später auch Publisher. Seine Implementierung und Auslieferung gehören nicht zum MVP.

Die Runtime besitzt kein fachliches Publikations-, Datenbank- oder Synchronisationsmodell. Vollständige Java-Signaturen stehen in [14](14-java-api-vertraege.md), Klassen und Aufrufsequenzen in [15](15-runner-klassendesign.md), Loggingregeln in [16](16-logging-und-telemetrie.md). Der DSL-Vertrag steht in [04](04-job-dsl-und-custom-tasks.md), die CLI in [06](06-betrieb-und-auslieferung.md).

## 1. Begriffe und Grenzen

| Begriff | Bedeutung |
|---|---|
| JobDefinition | Nach dem Laden eingefrorene Registrierung eines Jobs. |
| JobRun | Konkreter Lauf mit Run-ID, Parametern, Plan und Arbeitsbereich. |
| TaskDefinition | Benannter DAG-Knoten mit Metadaten und genau einem ausführbaren Body. |
| TaskRef<R> | Referenz auf das mögliche Resultat eines Tasks dieses Jobs. |
| ParameterDefinition | Beim Laden deklarierter öffentlicher Jobparameter mit Name, Beschreibung, Werttyp und optionaler Vorgabe. |
| ResultView | Lesende Groovy-Sicht auf die ausdrücklich beschriebenen Ausgänge eines typisierten Tasks. |
| Operation / Custom-Task-Typ | Wiederverwendbare Fachimplementierung mit Request, synchronem Aufruf und Resultat. |
| Invocation | Ein konkreter Aufruf einer Operation, auch innerhalb einer Action. |
| TaskContext | Kontext eines benannten Tasks, einschliesslich zulässigem Resultatzugriff. |
| OperationContext | Logger, Parameter-/Konfigurationszugang, Deadline, Arbeitsbereich und Diagnosekanal für einen Aufruf. |

### RUN-001 — Abhängigkeit des Kerns

Die öffentliche API und der Runnerkern hängen nicht von Gradle, Groovy, GeoTools, JTS oder INTERLIS ab. Groovy-Typen gehören in den DSL-Adapter. Fachtypen gehören in die Taskbibliotheken.

Das bedeutet nicht, dass jede Fachbibliothek intern nur neutrale DTOs verwenden darf. Innerhalb einer JVM dürfen ihre eigenen Requests und Resultate passende Java-Typen enthalten. Für langlebige Task-APIs sind stabile Werte, kopierte Sammlungen und Dateiartefakte meist zweckmässiger als offene bibliotheksspezifische Ressourcen.

Der Core führt eine Operation über den allgemeinen Invoker aus. Deren interne Helfer sind gewöhnliche Java-Aufrufe; es besteht keine Pflicht, jede Publisherphase oder jeden SQL-Schritt als eigene Runtimeoperation zu registrieren. `ctx.run` ist für sichtbare Zusammensetzung im Job vorgesehen, nicht als allgemeiner Servicebus innerhalb aller Bibliotheken.

### RUN-002 — Prozessmodell

Ein CLI-Jobstart erzeugt eine frische JVM mit einem JobRun. Der öffentliche Taskgraph läuft sequenziell. Es gibt keinen persistenten Daemon und keine gemeinsam verwendeten Scriptinstanzen verschiedener Runs.

Tasks laufen zunächst in dieser JVM. Ein gesonderter Prozess kann eine bewusst implementierte Eigenschaft eines bestimmten Adapters sein. Sein Protokoll und seine Abnahme werden erst definiert, wenn diese Grenze tatsächlich benötigt wird. Der MVP verlangt weder einen Workerpool noch universelle Request-/Result-Codecs.

## 2. Laden und Planen

### RUN-010 — Ladephase

Beim Laden registriert das Script Jobparameter, Tasknamen, Operationstypen, Abhängigkeiten, Reihenfolgen, Bedingungen, Cleanup und Standardziele. Die Bodies und Konfigurationsclosures der Tasks werden gespeichert und nicht ausgeführt. Die anwenderseitigen Tasknamen werden intern auf TaskRefs dieser JobDefinition abgebildet; Jobautoren müssen diese Referenzen nicht als Variablen verwalten.

Nach dem Laden wird die Registrierung eingefroren. Nachregistrierung aus einem Taskbody ist `DYNAMIC_TASK_REGISTRATION_UNSUPPORTED`. Statische Schleifen über bekannte Werte dürfen Tasks erzeugen; zur Laufzeit gefundene Dateien werden in einem bestehenden Task verarbeitet.

Tasktyp und `dependsOn` stehen im Registrierungskopf. Fachliche Einstellungen mit `=` stehen im gespeicherten Konfigurationsblock. Der Runner führt diesen Block nicht teilweise oder zweimal aus, um Metadaten und Laufzeitwerte zu trennen. Er leitet keine Graphkanten durch AST-Suche oder versuchsweises Ausführen von `result(...)`-Zugriffen ab.

Der Scriptmodus lädt Quelltext und kompiliert ihn automatisch. Deklarierte beziehungsweise importierte Groovy-Helfer können unter dem Jobroot mit dazu passender Packagestruktur liegen; der Scriptloader nimmt diesen Root als lokale Quellbasis auf. Dafür werden keine Bibliotheken aus dem Netz geladen. Das Job-Fat-JAR instanziiert eine vorkompilierte Scriptklasse. Beide führen denselben Registrierungspfad aus und erhalten denselben Kontext. Ein vorkompilierter Job ist kein vorab ausgeführter DAG.

### RUN-011 — Graph und Zielauswahl

1. Doppelte Task-IDs und unbekannte Referenzen in der vollständigen Registrierung ablehnen.
2. Den vollständigen `dependsOn`-Graph auf Zyklen prüfen.
3. Explizite CLI-Ziele verwenden, andernfalls die registrierten Standardziele. Ohne beides ist `run` ein Konfigurationsfehler.
4. Die transitive `dependsOn`-Hülle dieser Ziele auswählen.
5. `after`-Kanten nur aufnehmen, wenn beide Tasks ausgewählt sind.
6. Den kombinierten ausgewählten Graph auf Zyklen prüfen und stabil topologisch sortieren.

Bei mehreren freien Tasks entscheidet die Registrierungsreihenfolge. Die Reihenfolge mehrerer CLI-Ziele ersetzt keine Abhängigkeit.

`after` zieht keinen Task in den Plan und erlaubt keinen Resultatzugriff. Ein Zyklusbericht nennt die beteiligten Tasks und die Kantenarten. Nicht ausgewählte `after`-Kanten haben keine Ausführungswirkung; eine unbekannte Referenz bleibt dennoch ein Definitionsfehler.

### RUN-012 — `check` und `plan`

`check` lädt das Script und prüft die verfügbare Definition. `plan` bildet zusätzlich den ausgewählten Ausführungsplan. Beide prüfen ausdrücklich bereitgestellte Parameter und deklarierte Vorgaben nach RUN-013. Noch fehlende Pflichtparameter werden als offen angezeigt; sie verhindern diese beiden Befehle nicht. Keiner der Befehle führt Taskbodys, Taskkonfigurationen oder Cleanup aus.

Beliebiger Groovy-Toplevel-Code kann trotzdem selbst Dateisystem- oder Netzwerkzugriffe programmieren. Dies ist keine Sandbox. Jobautoren müssen Facharbeit in die gespeicherten Bodies verlagern. Ein wiederverwendbarer Fachaufruf über den Runtimekontext ist in der Ladephase unzulässig.

Erst zur Laufzeit ermittelbare **Task-Eingaben** werden beim Taskstart geprüft. Sie sind von öffentlichen Jobparametern zu unterscheiden: Letztere haben einen vorher bekannten Vertrag und werden vor einem Run auf Vollständigkeit geprüft. Der Planner erfindet keine Dummywerte. Ein erfolgreicher Plan ist daher kein Nachweis, dass ein späterer Download, eine SQL-Transaktion oder eine Publikation erfolgreich sein wird.

`gretl run job.groovy --help` lädt die Jobdefinition und zeigt ihre Parameter, Beschreibungen und Ziele, ohne Parameterwerte oder Secrets aufzulösen und ohne Taskarbeit. Globales `--help` und `--version` werden weiterhin vor Providerinitialisierung und Groovy-Laden beantwortet. Auch die Jobhilfe ist keine Sandbox gegen absichtlich programmierte Nebenwirkungen im Script-Toplevel.

Der Packager darf weder `check` noch `plan` durch Ausführen des Jobs zur Bestimmung seines Paketbestands verwenden. Sein eigener Buildvertrag steht in Kapitel 12.

### RUN-013 — Deklarierte und typisierte Jobparameter

Öffentliche Parameter werden beim Laden mit Name, obligatorischer Beschreibung, `type`, `required` und optionalem `defaultValue` deklariert. `type` ist standardmässig `text`, `required` standardmässig `false`. Erlaubte Typen sind `text`, `boolean`, `integer` und `path`. Namen folgen Kapitel 06. Doppelte Namen, unbekannte Deklarationsoptionen, leere Beschreibungen und die Kombination `required: true` mit vorhandenem `defaultValue` sind Definitionsfehler.

Vor einem Run wird genau ein unveränderlicher, typisierter Parameterbestand erzeugt. Die Quellen, Prioritäten und Diagnosen regelt Kapitel 06. Der allgemeine Vertrag lautet:

- Unbekannte ausdrücklich per CLI oder Parameterdatei übergebene Namen sind Fehler vor Runbeginn.
- Zusätzliche Werte im gemeinsamen `runtimeconfig.params`-Bestand, die dieser Job nicht deklariert, werden ignoriert. Sie bilden keine versteckte zweite Parameter-API.
- Bereitgestellte Werte und Vorgaben werden vor dem Run typisiert. `text` ergibt String, `boolean` Boolean, `integer` einen vorzeichenbehafteten 64-Bit-Long und `path` einen normalisierten Path.
- Eine optionale Deklaration ohne Wert und ohne Vorgabe ergibt `null`. Ein leerer String bleibt bei einem optionalen Textparameter ein Wert; er ist bei einem verpflichtenden Textparameter unzulässig.
- Boolean-Transporttext ist exakt `true` oder `false`. Integer-Transporttext besteht aus optionalem Vorzeichen und Dezimalziffern und muss in den Long-Wertebereich passen.
- Ein Pathparameter verlangt nicht leeren Pfadtext. Relative Pfade werden gegen das ursprüngliche Startverzeichnis aufgelöst. Diese Konversion prüft weder Existenz noch Dateityp und erzeugt keine Eingabe- oder Ausgabedatei.
- Deklarierte Vorgaben sind Strings oder zum angegebenen Typ passende einfache Werte. Pfadtext bleibt im Jobcode portabel; ein Path wird erst bei der Auflösung erzeugt.
- `run` prüft sämtliche verpflichtenden Jobparameter vor jeder Taskarbeit, unabhängig vom gewählten Teilziel. `check` und `plan` prüfen vorhandene Werte und Vorgaben, zeigen aber fehlende Pflichtwerte als offen. Jobhilfe zeigt nur die Definition.

`parameter('name')` in einem Task-/Action-Scope liefert den typisierten Wert. Der Java-Kontext bietet denselben Bestand über `parameter(String)` und eine geprüfte Überladung `parameter(String, Class<T>)`. Ein unbekannter Name ist immer ein Fehler. `ctx.param` und `ctx.paramOr` gehören nicht mehr zur API; Vorgaben werden in der Parameterdeklaration ausgedrückt.

`config(alias)` liest ausschliesslich den fachlich allgemeinen Abschnitt `taskConfig[alias]` gemäss Kapitel 06. Es bietet keinen Zugriff auf den Runtimekonfigurations-Root und damit keinen rohen Nebenkanal zu `runtimeconfig.params`. Ein fachlicher Alias namens `params` benötigt deshalb kein zusätzliches Verbot; er bezeichnet weiterhin nur `taskConfig.params`.

Das Lesen von Parameterwerten im Script-Toplevel ist unzulässig. Dort bezeichnet `parameter(name, ...)` ausschliesslich die Deklaration. Secretzugang bleibt eine eigene späte Kontextfunktion und wird nicht als Parameterstandardwert eingebaut.

## 3. Taskstart und synchrone Aufrufe

### RUN-020 — Typisierter Task

Nach Abschluss seiner Vorgänger durchläuft ein typisierter Task diese Schritte:

1. `when` auswerten, sofern vorhanden; das Resultat muss Boolean sein.
2. Bei `false`: `SKIPPED/PREDICATE_FALSE`, kein Builder und kein Fachaufruf.
3. Frischen Builder und einen aufrufgebundenen Konfigurationsscope erzeugen, dokumentierte Feldvorgaben anwenden und die gespeicherte Konfigurationsclosure genau einmal auswerten.
4. Request erzeugen und validieren.
5. Operation synchron ausführen.
6. Erforderliche aufrufeigene Ressourcen freigeben.
7. Erfolgreiches Ergebnis registrieren oder den unbehandelten Fehler melden.

Ein `return` im Konfigurationsblock ist kein Skip. Fehlende Pflichtfelder, unbekannte Einstellungen oder unbekannte Helper führen zu einer konkreten Diagnose mit Taskname, Quelle und soweit verfügbar Zeile. Die betreffende Fachoperation beginnt erst nach erfolgreicher Requestvalidierung. Frei programmierte direkte Seiteneffekte in einer Closure bleiben Verantwortung des vertrauenswürdigen Scriptcodes.

Der Groovy-Adapter verbindet den Builder über ausdrücklich beschriebene Feldkonversionen und Setter mit der DSL. Der Runnerkern kennt keine konkreten Feldnamen und keine Publisher-, SQL- oder INTERLIS-Builder. Ein eigener Task erhält denselben allgemeinen Vertrag.

### RUN-021 — Action

Eine Action ist normaler ausführbarer Groovy-Code. Sie darf Bedingungen, Schleifen, lokale Variablen, `try/catch/finally`, Bibliotheksaufrufe und synchrone `ctx.run(...)`-Aufrufe enthalten.

Erfolg bezieht sich auf den gesamten Body einschliesslich erforderlicher lokaler Freigaben. Scheitert nach einer gelungenen Operation die Nachprüfung, ist die Action fehlgeschlagen. Die Runtime versucht keine automatische Rückabwicklung der bereits gelungenen Operation.

Eine Action darf eine Fachexception bewusst behandeln und anschliessend normal zurückkehren. Dann kann sie erfolgreich sein. Keine Publisher-, SQL- oder andere Fachoperation kann ausserhalb dieses Vertrags eine versteckte globale Fehlerpflicht hinterlassen.

### RUN-022 — `ctx.run`

Jeder synchrone Fachaufruf erhält eine Invocation-ID, den Eltern-Task, ein eigenes Arbeitsunterverzeichnis und den geerbten Kontext. Er ist kein neuer DAG-Knoten und führt keine impliziten Tasks aus.

Der Aufruf konfiguriert einen frischen Builder, validiert und führt genau einmal aus. Ein normaler Return liefert den Wert an den aufrufenden Code. Eine Exception wird an diesen Code weitergegeben. Es gibt keinen automatischen Retry und keinen stillen Backendwechsel nach möglicherweise eingetretenen Seiteneffekten.

Die Runtime erfasst Beginn und Ende des Aufrufs. Ein fehlgeschlagener untergeordneter Aufruf kann in den Ereignissen sichtbar bleiben, während die übergeordnete Action nach bewusster Fehlerbehandlung erfolgreich endet. Die Runentscheidung wird nicht aus dem Vorhandensein irgendeines ERROR-Logeintrags abgeleitet.

## 4. Resultate

### RUN-030 — Explizite Datenabhängigkeit

`result('taskName')` ist die normale Groovy-Oberfläche. Sie löst den Namen gegen die aktuelle JobDefinition auf und liest ein bereits erfolgreich erzeugtes Resultat aus einer direkten oder transitiven `dependsOn`-Kette des aktuellen Tasks. Sie wartet nicht auf zukünftige Arbeit und fügt keine Kante hinzu. `ctx.result(ref)` liefert für fortgeschrittenen Java-/Groovy-Code nach derselben Prüfung das unveränderte Javaresultat.

| Situation | Diagnose |
|---|---|
| Referenz gehört zu anderer JobDefinition oder ist unbekannt | `FOREIGN_OR_UNKNOWN_TASK_REF` |
| Produzent ist kein deklarierter Datenvorgänger | `UNDECLARED_RESULT_DEPENDENCY` |
| Produzent ist regulär übersprungen oder erfolgreich ohne Wert | `RESULT_NOT_PRODUCED` |
| Produzent ist nicht erfolgreich abgeschlossen | `RESULT_NOT_AVAILABLE` |
| Bekannt deklarierter Resultattyp passt nicht | `RESULT_TYPE_MISMATCH` |

Eine TaskRef gehört zur JobDefinition; die Ergebnisablage gehört zum JobRun. Ergebnisse eines vergangenen Runs dürfen nicht in einen neuen Lauf hineingetragen werden.

`resultOptional('taskName')` beziehungsweise `ctx.resultOptional(ref)` liefert bei einem regulären Skip **oder bei erfolgreichem Abschluss ohne Wert** ein leeres Optional. Es verdeckt weder einen unbekannten Task noch einen Fehler oder eine fehlende Datenabhängigkeit. Die Groovyform enthält bei vorhandenen Werten dieselbe Sicht wie `result('taskName')`; die Javaform enthält den unveränderten Wert. Wer einen Wert benötigt, verwendet `result`.

Ein normaler Return von `null` aus einer Action beziehungsweise einer mit `Void.class` deklarierten Operation bedeutet Erfolg ohne Wert. Es ist weder Skip noch No-data-Abbruch. Ein nicht-Void-Handler muss dagegen ein Resultat des deklarierten Typs liefern; null ergibt `NULL_OPERATION_RESULT` und Code 10. Diese Präzisierung schützt ausdrücklich angebotene Ausgänge vor unbeabsichtigtem Verschwinden. Status und Wertpräsenz werden unabhängig modelliert: `TaskStatus` plus `ResultSlot.Value` beziehungsweise `ResultSlot.NoValue` gemäss Kapitel 15, niemals ein einzelnes Optional für den gesamten Taskzustand.

### RUN-034 — Context und Ergebnislebensdauer auf Methodenebene

`TaskContext`, `OperationContext`, `CleanupContext` und `ContextView` sind in Kapitel 14 vollständig definiert. Kein Groovytyp tritt über diese Java-Grenze. Contextinstanzen sind aufrufgebunden; ConfigBuilder, Fachhandler und Invocationverzeichnis werden frisch erzeugt. Nach dem Schliessen ist keine neue Facharbeit oder Ressourcenregistrierung zulässig. Ein nachlaufender Logeintrag erhält nie versehentlich die Identität des nächsten Tasks.

Der Invoker prüft den obersten Resultattyp, schliesst erforderliche Ressourcen und erst dann registriert der Runner ein erfolgreiches Producerresultat. `OutputField.read` bleibt dagegen lazy beim tatsächlichen Consumerzugriff. Eine Accessorexception oder unerlaubtes null ist ein konkret diagnostizierter Providervertragsfehler des Consumers; sie schreibt keinen bereits erfolgreichen Producerstatus rückwirkend um. Die Klasse `ResultStore` erzwingt Datenabhängigkeit und Jobdefinitionszugehörigkeit.

### RUN-031 — Lebensdauer und Besitz

Resultate werden bis zum Ende des Runs aufbewahrt. Im selben JVM-Prozess ist keine JSON-Serialisierung erforderlich. Der Core erstellt keine generische tiefe Kopie beliebiger Java-Objektgraphen. Taskautoren dokumentieren die Besitzregeln; unveränderliche Records und defensiv kopierte Listen sind der Standard für die mitgelieferten Bibliotheken.

Offene Connections, ResultSets, Streams oder Datastores sind keine zulässigen Resultate der mitgelieferten Tasks. Sie werden im erzeugenden Aufruf geschlossen. Erzeugte Dateien bleiben für spätere Tasks zugänglich und werden nicht schon beim Producerende gelöscht.

Ein Path ist keine Garantie für unveränderlichen Dateiinhalt. Wenn ein Custom-Task besondere Integritäts- oder Besitzregeln braucht, prüft er diese selbst. Der Runner erzwingt keine generellen Publikationsmanifeste oder Hashverträge.

### RUN-032 — Berichte sind keine Ergebnisdatenbank

Beliebige Taskresultate werden weder automatisch geloggt noch reflektiv in JSON serialisiert. Das vermeidet unbeabsichtigte Secrets in Berichten und eine versteckte Persistenzanforderung an alle Custom-Tasks.

Der Runbericht enthält Taskstatus, Zeiten, Operationstyp und ausdrücklich gemeldete Diagnosen beziehungsweise Artefaktverweise. Er ist kein allgemeiner Resume-Speicher. Ein fachlicher Datensatz, der dauerhaft benötigt wird, gehört in den dafür vorgesehenen Fachspeicher.

### RUN-033 — Dokumentierte Ergebnisfelder

Bei einem typisierten Task bietet `result('download').file` eine lesende ResultView. Ihr Provider beschreibt jedes angebotene Feld mit Name, Werttyp, Beschreibung, Nullierbarkeit und einer ausdrücklich zugewiesenen Zugriffsfunktion. Ein unbekanntes Feld erzeugt `UNKNOWN_RESULT_FIELD` mit den verfügbaren Feldnamen; Zuweisungen an diese Sicht sind `READ_ONLY_RESULT`.

Ein Accessor wird erst nach dem erfolgreichen Produzentenabschluss und erst bei Zugriff ausgewertet. Er muss ohne eigene Facharbeit lesend sein. Der Adapter reflektiert keine beliebigen Getter, kopiert keinen vollständigen Objektgraphen und serialisiert weder das Ergebnis noch ein Secret. Eine ResultView wird nicht als Ersatz für das eigentliche Javaresultat in der Ergebnisablage gespeichert.

Eine Action liefert gewöhnliche JVM-Werte. Bei ihr gibt `result('actionName')` diesen Wert direkt zurück; ein Stringtoken bleibt ein String und eine URI bleibt eine URI. Eine wiederverwendbare Operation verwendet dagegen ausdrücklich dokumentierte Ausgänge. Wer den gesamten typisierten Fachwert benötigt, verwendet den Expertenzugang `ctx.result(ref)`.

Ein dokumentiertes, modebedingt nullierbares Ergebnisfeld bedeutet nicht, dass der Task kein Ergebnis hatte. Beispiel: Bei ZIP ist `ExtractResult.file` null, während `files`, `directory` und `bytes` reguläre Ausgänge sind. `resultOptional` ist in diesem Fall nicht leer. Die genauen Fachfelder stehen in Kapitel 04.

## 5. Zustände und Kontrollfluss

### RUN-040 — Task- und Runzustände

Ein ausgewählter Task durchläuft `PENDING → RUNNING`. Ein zusätzlicher interner READY-Zustand ist zulässig, aber kein benötigter öffentlicher Vertrag.

| Terminaler Taskzustand | Bedeutung |
|---|---|
| `SUCCEEDED` | Body und erforderliche Freigaben erfolgreich; Wert optional. |
| `SKIPPED` | Bewusst ausgelassen, mit konkretem Grund; kein Wert. |
| `FAILED` | Unbehandelter Fehler. |
| `CANCELLED` | Kontrolliert festgestellter Abbruch. |
| `NOT_RUN` | Ausgewählt, aber wegen Fail-fast, Abbruch oder Jobende nicht gestartet. |

Ein regulärer Skip erfüllt die Reihenfolge. Ein Nachfolger darf starten und muss ein optionales Resultat bewusst behandeln. Ein Fehler oder Cancel erfüllt die Datenvoraussetzung nicht.

JobRun-Zustände sind CREATED, RUNNING sowie terminal SUCCEEDED, NO_DATA, FAILED und CANCELLED. NO_DATA wird nur durch `finishJob` angefordert. Ein Job mit ausschliesslich regulären Skips kann trotzdem SUCCEEDED sein.

Nicht ausgewählte Tasks sind keine Fehler. Vor Ausführungsbeginn verworfene CLI-/Definitionsaufrufe erhalten eine Fehlerdiagnose, aber keinen erfundenen bereits gelaufenen Task.

### RUN-041 — Fail-fast

Nach dem ersten unbehandelten Taskfehler startet der MVP keine weitere normale Taskarbeit. Das gilt auch für unabhängige ausgewählte Zweige. Cleanup bleibt möglich. Noch nicht gestartete Tasks tragen `NOT_RUN` mit Grund `FAIL_FAST`, `DEPENDENCY_FAILED`, `JOB_FINISHED` oder `CANCELLED_BEFORE_START`.

`--continue`, automatische Wiederaufnahme und öffentliche DAG-Parallelität gehören nicht zum MVP.

### RUN-042 — Erfolgreiches Ende wegen fehlender Daten

`ctx.finishJob(reason)` fordert ein sofortiges erfolgreiches Ende des normalen Jobablaufs an. Ein typischer Aufrufer hat zuvor erfolgreich festgestellt, dass eine Datenquelle keine verarbeitbaren Dateien enthält. Ein Transportfehler ist keine solche Feststellung.

Die Runtime speichert die Stopanforderung und löst ein internes Kontrollsignal aus. Der aktuelle Task wird `SKIPPED/JOB_FINISHED`, nachfolgende ausgewählte Tasks werden `NOT_RUN/JOB_FINISHED`. Nach erfolgreichem Cleanup endet der Run mit NO_DATA und Exitcode 0.

`finally` und registriertes Cleanup laufen weiter. Ein tatsächlicher Freigabe- oder anderer unbehandelter Fehler hat Vorrang vor dem beabsichtigten erfolgreichen Ende. Ein Finishsignal darf nicht einen fehlerhaften Ressourcenabschluss verdecken.

Die gespeicherte Stopanforderung verhindert weitere Aufrufe über `ctx.run` und weitere Taskstarts, auch wenn Scriptcode das Kontrollsignal versehentlich fängt. Beliebige direkte Java-Seiteneffekte kann vertrauenswürdiger Code weiterhin selbst programmieren; daraus wird keine Sandboxbehauptung abgeleitet.

Diese unabhängige Stopanforderung ist allgemeine Runtimekontrolle. Sie ist ausdrücklich kein Muster für fachliche Publisher- oder SQL-Pflichten.

## 6. Ressourcen und Abbruch

### RUN-050 — Lokale Ressourcen

`try/finally`, `try-with-resources` beziehungsweise Groovys `withCloseable` sind die bevorzugten Mittel für lokale Lebensdauer. Ein kleiner `ResourceScope` darf zusätzlich Closeables sammeln und in umgekehrter Erwerbsreihenfolge schliessen.

Liegt bereits eine echte primäre Exception vor, bleiben Freigabefehler zusätzliche Ursachen. Ohne primären Fehler wird ein erforderlicher Freigabefehler zur primären Ursache; die übrigen registrierten Freigaben werden dennoch versucht. Das interne Finishsignal ist keine echte Fehlerursache und darf eine fehlerhafte Freigabe nicht verbergen.

Eine fachliche Action darf auch die Exception eines fehlgeschlagenen synchronen Aufrufs einschliesslich dessen Cleanupfehler bewusst behandeln. Der Core unterhält dafür keine zusätzliche Fehlerregistry. Unbehandelte Fehler der abschliessenden Task-/Jobfreigabe bleiben hingegen Runfehler.

### RUN-051 — Job-Cleanup

`cleanup('name') { ... }` wird beim Laden registriert; ein ausdrücklich benannter Kontextparameter bleibt für fortgeschrittenen Code möglich. Nach einem begonnenen Run laufen die registrierten Aktionen in umgekehrter Reihenfolge bei Erfolg, NO_DATA, Fehler und kooperativem Abbruch.

Sie laufen nicht bei `plan`, `check`, einem Definitionsfehler vor Runbeginn oder nach einem unkontrollierbaren Kill. Der eingeschränkte CleanupContext erlaubt Logging, Parameter-/Konfigurationszugriff und bekannte Pfade, aber kein Registrieren oder Starten neuer Tasks und kein `ctx.run`.

Fachliche Quittierungen, etwa das Löschen erfolgreich verarbeiteter Eingangsdaten, gehören als normale Erfolgsschritte in den Job. Cleanup kann auch nach einem Fehler laufen und wäre dafür oft der falsche Ort.

Ein unbehandelter Job-Cleanupfehler macht einen sonst erfolgreichen beziehungsweise NO_DATA-Run zu FAILED. Bereits abgeschlossene Taskhistorie wird dadurch nicht umgeschrieben. Bei einer vorhandenen primären Fehlerursache bleiben weitere Fehler erhalten. Vollständige `finalizedBy`-Kompatibilität ist nicht vorgesehen.

### RUN-052 — Cancellation und Timeout

Der Kontext bietet CancellationToken und ein geerbtes Zeitbudget. Eine lokale Deadline kann nur strenger sein als die des Jobs. Die Runtime berechnet verstrichene Zeit monoton; ein neuer Task darf das gesamte Jobbudget nicht neu starten.

Adapter prüfen Cancellation an geeigneten Phasengrenzen und verwenden begrenzte HTTP-, JDBC- oder Prozesszeitlimits. InterruptedException wird nicht als normaler Erfolg verschluckt. Es gibt keine Verwendung von `Thread.stop`.

**Im MVP ist dies kooperativer Abbruch.** Die Runtime kann einen blockierten, nicht kooperierenden Bibliotheksaufruf in derselben JVM nicht zuverlässig hart beenden und gleichzeitig weitere sichere Jobarbeit garantieren. Ein externer Aufrufer kann die gesamte JVM nach einer Nachfrist beenden. Diese Grenze ist in CLI-Hilfe und Betriebshandbuch sichtbar.

Ein kontrollierter Cancel/Timeout verhindert weitere normale Arbeit und liefert Exitcode 20. Nach einem harten Kill können Abschlussbericht und Cleanup fehlen. Die Runtime erfindet daraus weder fachlichen Rollback noch eine erfolgreiche Fertigstellung.

## 7. Fehler und Diagnosen

### RUN-060 — Einheitliche Fehlersemantik

Ein nicht behandelter Taskfehler ist ein Taskfehler, unabhängig davon, welche Bibliothek ihn auslöst. Der Bericht enthält mindestens Fehlertyp, Meldung, Ursache, Task-/Invocation-ID und bei Scriptfehlern soweit vorhanden Quelldatei und Zeile.

Eine fehlgeschlagene Operation kann bereits Effekte erzeugt haben. Der Custom-Task darf diese Information als fachliche Exception und als explizite Diagnose melden. Der Runner interpretiert daraus keinen allgemein gültigen Wiederholungs- oder Reparaturplan.

Beispiel: Publisher meldet, dass Dateien bereits ersetzt wurden und eine Metadatenanfrage scheiterte. SqlExecutor meldet einen Verbindungsabbruch während commit. Beide können Exitcode 10 auslösen; ihre taskseitigen Details unterscheiden sich. Ein Scheduler darf daraus nicht ohne Fachvertrag einen Vollretry ableiten.

### RUN-061 — Kleiner generischer Diagnosekanal

Der gemeinsame Context bietet `report(Diagnostic)` für ausdrücklich freigegebene Informationen. Ein Diagnostic enthält:

- Severity INFO, WARN oder ERROR;
- einen frei benannten, taskseitig verantworteten Code;
- verständlichen Text;
- eine begrenzte Map aus Stringschlüsseln und Stringwerten;
- optionale Verweise auf bereits vorhandene Diagnoseartefakte.

Der Core begrenzt Grössen und speichert die Daten, wertet aber Codes und Details fachlich nicht aus. Ein ERROR-Diagnostic setzt den Taskstatus nicht selbständig auf FAILED; dafür wird eine Exception geworfen. Diese Trennung ist erforderlich, damit ein behandelter Fachfehler nicht heimlich eine andere Runsemantik erhält.

Der MVP braucht kein automatisch serialisiertes Exceptionobjekt, keine zentrale Liste geschäftlicher Pflichten und kein verpflichtendes Side-effect-Enum für alle Tasks. Taskautoren dürfen ein solches Enum in ihrer eigenen API verwenden.

Secrets gehören nicht in Details, Exceptionmeldungen oder Artefaktverweise. Die Runtime kann bekannte Secretwerte maskieren, aber nicht jede frei programmierte Codierung eines Geheimnisses erkennen. Die mitgelieferten Adapter müssen ihre Log- und Fehlerpfade gezielt prüfen.

### RUN-062 — Exitcodes und Präzedenz

| Code | Bedeutung |
|---|---|
| 0 | SUCCEEDED oder bewusstes NO_DATA nach erfolgreicher Freigabe. |
| 2 | CLI-, Runtimekonfigurations-, Scriptlade- oder Planfehler vor Beginn der Taskausführung. |
| 10 | Unbehandelter Task-/Requestkonfigurations-/Fachfehler oder erforderlicher Cleanupfehler nach Runbeginn. |
| 20 | Primär kontrollierter Cancel oder Timeout. |
| 40 | Nachgewiesener interner Runnerfehler, etwa verletzte Invariante. Nicht für gewöhnliche Pluginexceptions. |

Der zuerst festgestellte echte Ausführungsfehler bleibt primär; eine danach angeforderte Cancellation versteckt ihn nicht. Wird Cancellation zuerst kontrolliert festgestellt, bleiben nachfolgende Abbruch-/Cleanupfehler sekundär. Ein gewünschtes NO_DATA hat gegenüber echten Fehlern keinen Vorrang. Ein interner Runnerdefekt wird ausdrücklich als solcher berichtet und kann Code 40 bestimmen; er darf keine bereits bekannten Fachdiagnosen löschen.

Ein fehlender verpflichtender Jobparameter, ein unbekannter ausdrücklich übergebener Parameter oder ein ungültiger bereitgestellter Parameterwert wird vor Runbeginn mit Code 2 gemeldet. Ein erst im Taskbody ausgeführter Zugriff auf einen nicht deklarierten Namen sowie eine nach späten Berechnungen ungültige oder fehlende Task-Eingabe ergibt Code 10 mit Kategorie CONFIGURATION und Phase EXECUTION. Der Runner führt nicht alle Bodies vorab aus, um solche Programmier- oder Facheingabefehler zu suchen.

Codes 30 und 31 sind nicht vergeben. Es gibt weder Publikations- noch SQL-spezifische globale Fehlerpräzedenz. Ein Betreiber verwendet bei Bedarf den allgemeinen Bericht und die Diagnosen seiner Taskbibliothek.

## 8. Registry und Erweiterungen

### RUN-070 — Kleiner Erweiterungsvertrag

Eine OperationType-Definition enthält ID, DSL-Symbol, Beschreibung, Request-/Resulttyp, Factories für frischen Builder und Handler, ein minimales Beispiel sowie ausdrücklich beschriebene Eingabe- und Ergebnisfelder. Ein `OperationProvider` liefert solche Definitionen. `InputField` verbindet ein dokumentiertes Feld mit dessen Konversion und Setter; `OutputField` verbindet einen dokumentierten Ausgang mit dessen Accessor. Kapitel 04 zeigt die Java-SPI.

Die Registry wird einmal vor dem Scriptlauf aus den ausdrücklich bereitgestellten Bibliotheken aufgebaut. Doppelte IDs oder DSL-Symbole sind Fehler; Classpathreihenfolge bestimmt keinen Gewinner. Das MVP bietet nicht mehrere konkurrierende Versionen derselben Operation im selben Run an.

Handler werden erst für den konkreten Aufruf erzeugt. Provider sollen bei ihrer Registrierung weder eine Engine starten noch Fachzugänge öffnen. Die CLI behandelt `--help` und `--version` vor Groovy-Laden und Providerinitialisierung.

`gretl operations [Symbol]` liest die Metadaten für allgemeine Taskhilfe, Eingaben, Ausgänge und Beispiel. Eigene separat gelieferte Bibliotheken erhalten dieselbe Darstellung wie mitgelieferte Tasks. Die Registry prüft auch doppelte Feldnamen, Konflikte mit reservierten DSL-Namen und unvollständige Metadaten. Sie errät keine zusätzlichen Felder aus Reflection.

Der MVP enthält in Lieferstufe A HttpDownload, Extract und SqlExecutor, in B zusätzlich IliValidator. Weitere GRETL-Tasks dürfen später regulärer Teil der Standarddistribution werden. Dieser Lieferumfang ist keine dauerhafte Positivliste der SPI. Publisher und XtfToGpkg werden im MVP nicht implementiert oder mit einem funktionslosen Symbol vorgetäuscht. Die Publisheranforderungen bleiben als späterer Fachvertrag dokumentiert.

### RUN-071 — Abhängigkeiten und spätere Prozesse

Eigene Bibliotheken laufen im gemeinsam qualifizierten Klassenpfad. Die Runtime löst während des Jobs keine Mavenkoordinaten auf und lädt keine beliebigen zusätzlichen JARs aus einer Taskdefinition nach.

Ein realer Dependencykonflikt wird zuerst durch eine passende Version oder Adapteranpassung gelöst. Eine gezielte getrennte JVM ist eine mögliche Alternative. Diese darf keine automatische transparente Wiederholung nach Effekten verursachen. Offene Connections oder beliebige JVM-Objektgraphen sind keine geeignete Prozessschnittstelle.

Falls ein Prozessadapter umgesetzt wird, muss er seinen konkreten Request, Antwortstatus, Streams, Limits, Secretübergabe, Abbruch und Ressourcenbesitz testen. Ein Exitcode 0 ohne erforderliches Ergebnis genügt dann nicht. Das ist eine bedingte Implementierungspflicht dieses Adapters, kein Auftrag, bereits im generischen MVP ein Workerframework zu bauen.

## 8a. Beobachtbarkeit als Teil des allgemeinen Ablaufs

### RUN-080 — Lebenszyklusereignisse und echte Fehler

Der Runner und `OperationInvoker` erzeugen die Start-/Terminalereignisse zentral; Custom-Tasks müssen nicht selbst passende Paare schreiben. Requestkonfiguration, Fachausführung, lokale Freigabe und Job-Cleanup erhalten unterscheidbare Phasen. Die Ereignisse enthalten Run-, Task- und Invocation-IDs, Dauer und eindeutig zuordenbare Quellstände. Kein Request-/Resultatdump und keine fachliche Interpretation von Severity.

Eine innerlich fehlgeschlagene, anschliessend bewusst behandelte Operation bleibt mit `outcome=FAILED` als untergeordnetes Ereignis sichtbar, während ihre Action erfolgreich sein kann. `FailureArbiter` bestimmt den Kontrollfluss ausschliesslich aus echten Exceptions und Runtimekontrolle. `RunEventSink` beobachtet ihn. Details zu Fehler-ID, Wiederverwendung statt mehrfacher Stacktraces und Ursachen-/Suppressed-Ketten stehen in Kapitel 16.

### RUN-081 — Fortschritt und Berichtsgrenzen

`OperationContext.progress(Progress)` meldet begrenzten, typisierten Fortschritt. Ein Heartbeat zeigt nur laufenden Prozess, nicht fachlichen Fortschritt oder garantierte Gesundheit. Fortschritts- und Logaufrufe ändern den Taskstatus nicht. `OperationContext.sensitive(String)` registriert einen dynamisch erhaltenen Token im rungebundenen Maskierer, ohne ihn zu einem öffentlich serialisierten Ergebnis zu machen.

Der JSON-Runbericht ist ein erforderliches lokales Produktartefakt, kein Resume-Store. Die fortlaufende Ereignisdatei ist begrenzt; initiale Pfadfehler sind Startfehler, spätere rein telemetrische Ausfälle werden ausdrücklich als Degradation gemeldet und lösen keinen ETL-Retry aus. Die konkrete Abgrenzung zwischen erforderlichem Abschlussbericht und optionaler Telemetriezustellung ist in LOG-031 und CORE-025 verbindlich.

### RUN-082 — Ein technischer Abschlussweg

Der technische Abschluss folgt CORE-025: Job-Cleanup, Outcome, Workspace-Retention, Abschlussbericht, terminales Runereignis, begrenzter Flush. Das gemeinsame Cleanup-/Flush-Nachfristbudget beträgt im MVP 30 Sekunden, soweit die Aufrufe kooperieren. Keine harte Zeitgarantie für blockierte In-Process-Bibliotheken oder OS-Ausgabekanäle. Bereits geschriebene Fachresultate bleiben trotz nachfolgendem Berichtfehler reale Wirkungen.

## 9. Prüfbarkeit und Grenzen

Der Core muss mit einer trivialen Taskbibliothek vollständig testbar sein, auch wenn Publisher und GeoTools physisch nicht auf dem Testklassenpfad liegen. Derselbe Core muss anschliessend mit den echten Fachbibliotheken funktionieren.

Planung, späte Konfiguration, Resultate, Fehlerbehandlung und Cleanup haben deterministische Tests. Bibliotheksintegration und Fat-JAR-Paketierung werden gegen die tatsächlich ausgelieferten Artefakte geprüft. Ein schöner Runbericht ersetzt keine fachliche Outputprüfung.

Die Runtime garantiert keine sichere Sandbox, keine globale Transaktion über Tasks, keine automatische Idempotenz und keine Wiederherstellung nach einem Kill. Ihre Aufgabe ist ein verständlicher, zuverlässiger Ausführungspfad innerhalb dieser ausdrücklich benannten Grenzen.
