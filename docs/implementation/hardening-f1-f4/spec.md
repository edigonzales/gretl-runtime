# GRETL Runtime: Korrekturspezifikation F1–F4

**Version:** 1.1 — konsolidiert nach der Nachprüfung  
**Datum:** 26. Oktober 2026  
**Repository:** `edigonzales/gretl-runtime`  
**Ursprüngliche Analysebaseline:** `288137f116f2d861a1648b5eeaf889d463a5ea6c`  
**Dokumentierter Nachprüfstand:** `bc044302829ede67ead27038bfdf2be90d0b0063`  
**Verbindlicher Ablageort:** `docs/implementation/hardening-f1-f4/spec.md`  
**Adressaten:** implementierender LLM-Coding-Agent, unabhängiger LLM-Reviewer und verantwortlicher Maintainer.

## 0. Auftrag und Status dieses Dokuments

Dieses Dokument spezifiziert ausschliesslich die vier Findings aus der erneuten Repositoryanalyse:

| Finding | Problem | Gewünschtes Ergebnis |
|---|---|---|
| F1 | Vorhandene Eltern-/Wurzelverzeichnisse werden bei der Vorbereitung auf private Rechte umgestellt. | Bestehende Rechte bleiben unverändert; neu erzeugte run-eigene Verzeichnisse und Diagnosedateien werden gezielt geschützt. |
| F2 | Lokale Task-Timeouts werden nicht durchgängig über Cancellation und Abschlussgrenzen beobachtet. | Ein abgelaufenes Taskbudget führt spätestens an der nächsten kontrollierten Grenze zum Abbruch; kein erfolgreiches Ergebnis und keine weitere normale Arbeit. |
| F3 | Task-Workspacefehler können den begonnenen Run vor Cleanup und Bericht verlassen. | Gewöhnliche Setup-/I/O-Fehler durchlaufen denselben kontrollierten Fehlerabschluss wie andere Ausführungsfehler. |
| F4 | Bibliotheksmeldungen über SLF4J/JUL verlieren Task-, Invocation- und Operation-Zuordnung. | Die Meldung trägt den verlässlich bekannten Kontext zum Entstehungszeitpunkt; es gibt keinen Übertrag auf den nächsten Task. |

Version 1.0 beruhte auf der Analysebaseline. Diese Version 1.1 übernimmt zusätzlich die Ergebnisse des bereitgestellten Berichts `gretl-runtime-nachpruefung-bc044302.md` und konsolidiert die betroffenen Sollverträge, Methodenaufträge, Tests und Prompts. Sie ist die **vollständige Spezifikation**, kein weiterer Startprompt und kein blosses Addendum. Die Repository-Spezifikation 4.1 wird dadurch nicht neu versioniert.

Der Nachprüfbericht enthält Quellcodebefunde, geprüfte CI-Evidenz und isolierte Kernproben mit ausdrücklich begrenztem Nachweisumfang. Für diese Dokumentrevision wurden diese Unterlagen ausgewertet; es wurde weder ein neuer Repositorystand abgerufen noch ein neuer Produktbuild ausgeführt. Der angegebene Nachprüfstand ist deshalb **kein behaupteter aktueller HEAD**. **Die Erstellung dieser Datei korrigiert keinen Produktcode und erteilt keine unabhängige Implementierungsfreigabe.**

Massgeblich bleiben die Repository-Anweisungen, die Spezifikation 4.1 und die inzwischen umgesetzten Modul-/Paketierungsverträge. Dieses Dokument präzisiert beziehungsweise korrigiert deren Verhalten nur für F1–F4. Es startet keine neue MVP- oder Architekturphase. Insbesondere bleiben der vorhandene Taskbestand und die geprüften Jobpakete erhalten.

Die Wörter **MUSS**, **DARF NICHT** und **SOLL** sind Anforderungsbegriffe. Ein bewusst abweichender Umsetzungsvorschlag benötigt eine begründete Entscheidung und eine unabhängige Prüfung vor seiner Freigabe. Private Hilfsmethodennamen sind dort flexibel, wo dieses Dokument nur eine mögliche technische Aufteilung zeigt; Zuständigkeiten, Beobachtbarkeit und Abnahmekriterien sind es nicht.

### 0.1 Wiederaufnahme nach der Nachprüfung

Am dokumentierten Nachprüfstand fehlte im Repository die eigentliche `spec.md`; vorhanden waren der Startprompt und Phase-A-Arbeitsberichte. Der Implementierer hatte diesen fehlenden Detailbestand ausdrücklich dokumentiert. Diese Datei schliesst die Übergabelücke. Der Startprompt ist lediglich der Einstieg; bei der Ausführung muss der Agent diese Spezifikation vollständig lesen. [N1, N2]

| Bereich | Dokumentierter Stand bei `bc044302…` | Verbleibender Auftrag |
|---|---|---|
| F1 | Ursprünglicher Rechtefehler im untersuchten Umfang korrigiert; Eigentums- und Prozesstests vorhanden. | Geeignete Umsetzung beibehalten, alle F1-Pflichten zuordnen und am neuen Kandidaten erneut nachweisen; kein zweiter Fix ohne Bedarf. |
| F2 / R1 | Aktive Checkpoints verbessert, aber gespeicherte Tokens/Budgets abgeschlossener Scopes können spätere Tasks abbrechen. | Lebensdauer ausgegebener Handles und nebenwirkungsfreie Abfragen präzisieren und korrigieren; F2-02, F2-08 und Abschnitt 4.3.1. |
| F2 / R2 | CANCELLED/20 wird ausgegeben, die konkrete Abbruchursache geht im Abschluss teilweise verloren. | Unveränderliche Cause-Snapshots bis Report und Terminalereignis durchreichen; F2-07 und Abschnitt 4.3.2. |
| F3 / R3 | Task-Setupfehler führen zu Cleanup/Report; eine schon gescheiterte exklusive Run-Leaf-Eröffnung wird aber ebenfalls als begonnener Run behandelt. | Startgrenze gemäss F3-01 wiederherstellen und Setupdiagnose gemäss F3-02 strukturiert ausgeben. |
| F4 | Noch nicht implementiert; Phase B war bis Gate A gesperrt. | Nach bestandenem Gate A vollständig umsetzen, einschliesslich des gemeinsamen Script-/JAR-Einstiegs. |

Diese Standangaben ersetzen keine erneute Prüfung am tatsächlich vorliegenden HEAD. Insbesondere werden weder grüne CI noch die frühere Nachanalyse nachträglich als formales Gate-A-`PASS` umgedeutet. Modellverschiedenheit, Reviewumfang und Kandidatenbindung bleiben nach Abschnitt 9 nachzuweisen.

### 0.2 Vorrang und Änderungsgrenze

Version 1.1 ersetzt Version 1.0 dieses Korrekturdokuments. Sämtliche bisherigen Anforderungs- und Testkennungen bleiben erhalten; neue Testfälle werden nur angehängt. Für F1–F4 sind die hier präzisierten Regeln massgeblich, insbesondere bei Abweichungen einzelner vorhandener Tests oder früherer Implementierungsberichte.

Ein Test, der bereits den abweichenden Istzustand als Soll festschreibt, darf nicht unangetastet bleiben, nur weil er grün ist. Eine solche Änderung braucht einen konkret benannten Vertragsbezug, das alte und neue Orakel sowie unabhängige Prüfung. Historische Berichte werden nicht umgeschrieben. Eine ausdrückliche, später beschlossene Maintainerentscheidung kann einen Vertrag ändern; ein Coding-Agent darf das nicht stillschweigend tun.

Es werden keine bereits geeigneten Lösungen neu implementiert. Die Ergänzungen sind ein begrenzter Abschluss von Phase A und eine präzise Übergabe an Phase B, keine neue Architekturrunde.

## 1. Umfang, Grenzen und Quellenstand

### 1.1 Was umgesetzt wird

Die vier Korrekturen werden in **zwei abgeschlossenen Phasen** geliefert:

1. **Phase A: F1–F3 — Rechte, Budgets und verlässlicher Runabschluss.**
2. **Phase B: F4 — Bibliothekskorrelation und vollständige Abschlussqualifikation.**

Jede Phase endet mit einer Gegenprüfung durch **ein anderes LLM-Modell in einer separaten Sitzung**. Ohne dessen nachweisbares `PASS` darf die nächste Phase nicht beginnen. Ein fehlendes Review ist `BLOCKED`, nicht stillschweigend `PASS`.

### 1.2 Was ausdrücklich nicht umgesetzt wird

Keine neue DSL, keine neuen CLI-Schalter, keine neuen Tasks, kein Publisher-Port, kein Native Image, kein Daemon, keine DAG-Parallelisierung, keine Workerplattform, keine verteilte Trace-Infrastruktur und kein OpenTelemetry-Exporter. Ebenso keine automatische Wiederaufnahme, kein automatischer Vollretry und keine Änderung der fachlichen Transaktionssemantik vorhandener Tasks.

Es gibt keine Major-Updates von Java, Groovy, Maven, SLF4J, Logback oder Fachbibliotheken als Nebenarbeit. Maven bleibt konventionell. Ein neues allgemeines Dateisystem-, Fehlerworkflow- oder Kontextpropagationsframework ist nicht erforderlich.

Ein echter IMAGE-Lauf bleibt ausserhalb dieses Auftrags. Die bestehende Vorbereitung dafür muss erhalten bleiben. Die verbindlichen Prozessnachweise erfolgen über Scriptstart und regulär gebaute Job-JARs.

### 1.3 Beziehung zu bestehendem Code

| Bereich | Relevante Klassen/Methoden am Ausgangsstand |
|---|---|
| F1 | `gretl-job-runtime`: `JobRuntime.run`, `prepareDirectory`, `setPrivateDirectory`; `gretl-runner`: `WorkspaceManager.openRun`, `applyRetention`; `gretl-logging`: `RunReportWriter.write`, `EventFileSink` |
| F2 | `RunControl`, `BaseContext.cancellation`, `TaskContextImpl`, `SequentialJobRunner.executeTask/completeTask`, `OperationInvoker.invoke`, `FailureArbiter` |
| F3 | `SequentialJobRunner.run/executeTask/runCleanups`, `ResultStore`, `WorkspaceManager`, `JobRuntime.run`, `RunReportWriter` |
| F4 | `RunEventSink`, `RunEnvironment`, Task-/Invocationgrenzen, `LogbackBridge.LibraryAppender.append`, `LoggingPipeline.libraryEvent` |

Die vollständigen Quellpfade und commitfesten Quellen stehen in Abschnitt 13. Der Implementierer MUSS vor Beginn `HEAD`, Branch, Arbeitsverzeichnisänderungen und einen möglichen Unterschied zur Ausgangsbasis erfassen. Er DARF keinen inzwischen korrigierten Zustand zurücksetzen, um diese Spezifikation buchstabengetreu auf alten Code anzuwenden. Bei neuerem Code wird der jeweilige Befund zuerst erneut geprüft.

## 2. Gemeinsame Leitplanken

### G-01 — Bestehende Grenzen erhalten

`gretl-api` bleibt JDK-only. Runner und Taskbibliotheken erhalten keine Abhängigkeit auf Logback oder die Authoring-CLI. `gretl-job-runtime` bleibt der gemeinsame Ausführungsweg für Script und Job-JAR. Die Taskbibliotheken bleiben unabhängig von Runner, Groovy und Packager. Die bestehenden Dependency-/Artefaktprüfungen bleiben aktiv.

### G-02 — Steuerung und Beobachtung trennen

Ein Logevent oder eine ERROR-Diagnose macht einen Task nicht automatisch fehlerhaft. Ein bewusst behandelter Fachfehler darf weiterhin eine erfolgreiche Action ergeben. Ein kontrolliert festgestellter Cancel/Timeout ist hingegen eine Laufzeitsteuerung und darf nicht durch Fangen seiner Exception aufgehoben werden.

Ein nachgewiesener fachlicher Effekt wird durch einen fehlgeschlagenen Run nicht rückgängig. Es gibt keine neue globale Registry offener Business-Pflichten.

### G-03 — Kein Erfolg ohne seine Voraussetzungen

Ein Resultat wird erst nach erforderlicher Ressourcenfreigabe und der letzten relevanten Budgetprüfung freigegeben. Fachfehler, kontrollierter Abbruch und notwendige Freigabefehler werden mit definierter Präzedenz behandelt. Ein Setupfehler nach Runbeginn ist kein vorgetäuschter erfolgreicher Skip.

### G-04 — Fehlernachweise müssen am richtigen Artefakt entstehen

Mindestens ein enger Regressionstest pro Finding MUSS auf dem Ausgangsstand den jeweiligen Fehler zeigen und auf dem korrigierten Stand bestehen. Die roten Nachweise dürfen nicht nur wegen fehlender neuer Klassen oder veränderter Testschnittstellen scheitern. Bestehende API beziehungsweise produktnahe Fixtures verwenden; nötigenfalls eine kleine separate Baseline-Probe erstellen.

Zusätzlich müssen die angegebenen Prozessexperimente über den produktiven Scriptlauncher und den regulären Packager/Job-JAR-Einstieg laufen. Direktes Aufrufen eines Handlers im Testclasspath ersetzt diese Nachweise nicht.

### G-05 — Ehrliche Freigabe

Keine abgeschwächten Assertions, keine `skipTests`-/`skipITs`-Freigabe und kein stiller Wechsel auf einen einfacheren Ausführungspfad. Ein Reviewer prüft selbst Code und Evidenz. Die Behauptung eines Implementierers, ein Test sei grün, ist kein unabhängiger Nachweis.

## 3. F1: Rechte und Eigentum an Laufzeitverzeichnissen

### 3.1 Befund und gewünschte Grenze

`JobRuntime.prepareDirectory()` ruft am Ausgangsstand nach `Files.createDirectories(path)` auch für bereits vorhandene Verzeichnisse `setPrivateDirectory(path)` auf. Betroffen sind `workRoot`, `reportRoot` und der Elternpfad eines expliziten Reports. Damit können Rechte eines gemeinsam genutzten Verzeichnisses verändert werden. [S1]

Die Korrektur lautet nicht, den Schutz privater Daten vollständig zu entfernen. Sie unterscheidet **vom Betreiber bereitgestellte Pfade** und **für diesen Run neu erzeugte Objekte**.

### 3.2 Verbindliche Anforderungen

**F1-01 — Vorhandene Wurzeln bleiben unter Betreiberhoheit.**

Bei bestehenden `workRoot`-/`reportRoot`-Verzeichnissen und Elternverzeichnissen eines expliziten Reports DARF die Runtime weder Modusbits noch Eigentümer, Gruppe oder ACLs verändern. Das gilt auch dann, wenn der ausführende Benutzer diese Rechte verändern könnte. Eine zu restriktive Konfiguration wird als Zugriffsfehler gemeldet, nicht mittels `chmod`, `chown` oder ACL-Umbau repariert.

`Files.isWritable` kann als Vorprüfung dienen. Entscheidend bleibt das Ergebnis der tatsächlichen Dateisystemoperation; eine erfolgreiche Vorprüfung garantiert keinen späteren Zugriff.

**F1-02 — Fehlende konfigurierte Verzeichnisse dürfen angelegt werden.**

Die bisherige Möglichkeit, fehlende konfigurierte Verzeichnisse anzulegen, bleibt erhalten. Bestehende Vorfahren werden dabei nicht geändert. Die sichere Standardvorgabe ist, neu angelegte Verzeichnisse auf POSIX-Dateisystemen mit owner-only Startrechten anzulegen. Das ist keine nachträgliche Umstellung bereits vorhandener Wurzeln. Eine gemeinsame Gruppenfreigabe richtet der Betreiber durch vorheriges Anlegen ein.

Erzeugungsattribute sind nach Möglichkeit atomar beim Erstellen zu verwenden. Ein Muster `exists → create → chmod` gilt nicht als Nachweis, dass das geänderte Verzeichnis wirklich dem eigenen Erzeugungsvorgang gehört. Die JDK-Datei-API unterstützt Erzeugungsattribute für neu angelegte Verzeichnisse und Dateien. [S8]

**F1-03 — Run-Workspace ist exklusiv neu.**

`WorkspaceManager.openRun(runId)` MUSS das finale `<workRoot>/<runId>` neu und exklusiv anlegen. Existiert diese Leaf bereits als Verzeichnis, Datei oder Symlink, wird die Eröffnung abgelehnt. Der Inhalt wird nicht übernommen, umbenannt, gelöscht oder in seinen Rechten geändert. Es gibt keine automatische Wiederaufnahme und keinen stillen Wechsel auf eine andere Run-ID.

Auf einem POSIX-Dateisystem erhält die neu erzeugte Leaf die Rechte `rwx------` bereits bei ihrer Anlage. Task-/Invocation-/Cleanup-Unterverzeichnisse bleiben innerhalb dieses eigenen Bereichs. Die Retention darf ausschliesslich den nachweislich für diesen Run erzeugten Bereich bearbeiten.

**F1-04 — Diagnoseausgaben schützen ihre eigenen Dateien.**

Report-Tempdateien, endgültige Reports und Ereignisdateien werden auf POSIX-Dateisystemen mit `rw-------` erzeugt beziehungsweise durch eine entsprechend geschützte neue Datei ersetzt. Dazu wird nicht das Elternverzeichnis privat gemacht. Das atomare Ersetzen eines ausdrücklich als Ausgabe gewählten Reportfiles bleibt zulässig; fremde Geschwisterdateien sind unberührt.

Eine Ereignisdatei wird exklusiv angelegt. Eine schon vorhandene gleichnamige Datei wird nicht kommentarlos geleert. Private Startattribute sind gegenüber erst nachträglich gesetzten Rechten vorzuziehen, damit keine vermeidbare offene Schreibphase entsteht.

**F1-05 — Plattform- und Symlinkgrenzen explizit halten.**

Ein vom Betreiber konfigurierter Root darf weiterhin ein vertrauenswürdig konfigurierter Symlink auf ein Verzeichnis sein, sofern die bisherige Pfadnutzung dies erlaubt. Dessen Zielrechte werden nicht verändert. Die exklusive Run-Leaf selbst darf dagegen nicht bereits als Symlink vorliegen.

Für nicht-POSIX-Dateisysteme wird der tatsächliche Plattformmechanismus verwendet; die Runtime behauptet dort keine bewiesene `0700`-/`0600`-Semantik. Kein allgemeiner Windows-ACL-Manager wird gebaut. Ein echter `IOException` auf einem POSIX-Dateisystem darf nicht als vermeintlich fehlende POSIX-Unterstützung verschluckt werden. Nichtunterstützung und Zugriffsfehler sind getrennte Fälle.

Diese Regeln sind kein vollständiges Sandboxversprechen gegen einen bösartigen Prozess, der vertrauenswürdige Elternpfade gleichzeitig austauscht. Der Auftrag verlangt keine allgemeine SecureDirectoryStream-Plattform.

**F1-06 — Keine fremden Pfade bei Fehlern bereinigen.**

Eröffnung abgelehnt bedeutet: kein Workspace-Eigentum und keine Retention auf diesem Pfad. Bei späteren Fehlern richtet sich die Retention nach dem bestehenden `KeepWork`-Vertrag. Die Behebung darf nicht zur unbeabsichtigten Löschung eines gemeinsam genutzten Elternverzeichnisses führen.

### 3.3 Klassen- und Methodenauftrag

| Stelle | Konkrete Änderung |
|---|---|
| `JobRuntime.prepareDirectory(Path, String)` | In `ensureConfiguredDirectory` umbenennen oder gleichwertig begrenzen. Verzeichnis sicher anlegen/prüfen; kein nachträgliches `setPosixFilePermissions` auf vorhandene Konfigurationspfade. Bestehende Fehlermeldungen sinnvoll weiterverwenden. |
| `JobRuntime.setPrivateDirectory` | Die jetzige Anwendung auf fremde Roots entfernen. Nicht lediglich die Methode an eine andere Stelle verschieben. |
| `WorkspaceManager.openRun(UUID)` | Exklusive Leaf-Anlage, private Erzeugungsattribute, klarer Kollisionsfehler; `Workspace` erst nach erfolgreicher Anlage herausgeben. |
| `WorkspaceManager.applyRetention` | Nur tatsächlich eigenes Workspaceobjekt akzeptieren; bestehende Wurzel nicht rekursiv bearbeiten. |
| `RunReportWriter.write` | Geschützte Tempdatei und bestehende Replace-Semantik erhalten. Parent unverändert lassen. Erzeugungsfehler und Schreibfehler weitergeben. |
| `EventFileSink` | Erzeugung der Datei mit atomaren privaten Startattributen prüfen; `CREATE_NEW`-Eigenschaft erhalten; keine Elternrechte verändern. |

Eine kleine lokale JDK-Hilfsmethode ist erlaubt. Ein neues Shared-Utility-Modul nur zur Vermeidung weniger Zeilen Dateierzeugung ist nicht erforderlich. Die gleiche Rechtepolicy wird über gemeinsame Tests und diesen Vertrag gesichert, nicht durch eine neue öffentliche Task-API.

### 3.4 Tests F1

| Test-ID | Szenario und unabhängiges Orakel |
|---|---|
| TC-F1-01 | Vorhandene `workRoot` und `reportRoot` mit `0770` verwenden. Vor/nach Run dieselben Rechte; vorhandene Markerbytes bleiben unverändert. |
| TC-F1-02 | `--report` in einem separaten vorhandenen `0750`-/`0770`-Elternverzeichnis. Elternrechte unverändert, Report lesbar und eigene Datei auf POSIX `0600`. |
| TC-F1-03 | Root ohne eigene Schreibberechtigung in einem unprivilegierten POSIX-Test. Kontrollierter Startfehler; kein Versuch einer Rechtekorrektur. Ein unter root ausgeführter Scheintest genügt nicht. |
| TC-F1-04 | Neues Run-Verzeichnis unter vorhandenem Root. Mit `KeepWork.ALWAYS` private Rechte und Lage prüfen; danach nur testseitig löschen. |
| TC-F1-05 | Vorgegebene Run-ID kollidiert mit vorhandener Leaf. Parametrisiert: Verzeichnis mit Marker, Datei, Symlink. Alle drei werden unverändert zurückgelassen. |
| TC-F1-06 | Fehlende konfigurierte Verzeichnisse mit existierendem Vorfahren. Neu angelegte Pfade nutzbar, vorhandener Vorfahr unverändert; keine pauschale Root-Öffnung. |
| TC-F1-07 | Bestehender konfigurierter Symlinkroot auf ein Verzeichnis. Zielrechte unverändert; eine bereits existierende Symlink-Run-Leaf wird abgelehnt. |
| TC-F1-08 | Report und Ereignisdatei im gemeinsamen Root. Eigene Dateirechte kontrollieren, Geschwisterdatei und deren Rechte unverändert; bereits vorhandene Ereignisdatei wird nicht überschrieben. |
| TC-F1-09 | Nicht-POSIX-Fallback über vorhandenen portablen Testweg oder echte Windows-CI prüfen. Fehlende POSIX-Unterstützung nicht mit einem allgemeinen I/O-Fehler verwechseln. Kein neuer Produktionsprovider nur für diesen Test. |
| TC-F1-10 | Retention bei Erfolg und Fehler. Nur das eigene Runverzeichnis wird gemäss `KeepWork` entfernt/behalten; Root und fremde Leafs bleiben bestehen. |

Die Rechteprüfungen sind mindestens auf Linux/POSIX verpflichtend. Ein dokumentierter Skip auf einer zusätzlichen nicht-POSIX-Testmaschine ersetzt dieses Gate nicht. Für Gruppe/Owner werden unveränderte Werte geprüft, soweit die Plattform sie anbietet; es braucht dafür keine privilegierten Änderungen an echten Benutzerkonten.

## 4. F2: Einheitliche Taskbudgets und kooperativer Abbruch

### 4.1 Befund

Der Runner erzeugt ein Child-Budget pro Task, während `BaseContext.cancellation()` nur die globale Runsteuerung liefert. Der Abschluss verwendet ebenfalls keinen vollständigen Snapshot des wirksamen Taskbudgets. So kann ein Task bei gültigem Runbudget sein eigenes Zeitlimit überschreiten und dennoch einen normalen Return als Erfolg veröffentlichen. [S2–S4]

Die Lösung bleibt kooperativ. Ein in fremdem Code blockierter Thread wird nicht hart gestoppt. Die zugesagte Garantie lautet: **Sobald die Runtime beziehungsweise der Adapter wieder eine kontrollierte Grenze erreicht, wird das wirksame Zeitlimit ausgewertet und weitere normale Arbeit verhindert.**

### 4.2 Verbindliche Anforderungen

**F2-01 — Ein wirksames Budget pro Task.**

Das Taskbudget ist das Minimum aus verbleibendem Runbudget und lokalem Tasklimit. Der Beginn dieses lokalen Budgets liegt beim Start des ausgewählten Tasks, vor Predicate und Taskkonfiguration. Es wird weder beim inneren `ctx.run` noch beim Handlerstart neu gestartet. Ohne lokales Limit gilt das geerbte Runbudget. Zeitmessung bleibt monoton.

Ein normaler Taskabschluss umfasst die notwendige Ressourcenfreigabe. Läuft das Budget erst während dieser Freigabe ab, wird danach kein erfolgreiches Resultat mehr veröffentlicht. Eine bereits festgestellte Cancellation verhindert hingegen nicht, dass erforderliche Freigaben bestmöglich unter der Abschlussregel ausgeführt werden.

**F2-02 — CancellationToken und Budget beschreiben denselben Arbeitsbereich.**

Task- und Operationkontexte erhalten einen wirksamen Token, der Run-Cancellation, Interrupt und Ablauf des zugehörigen Taskbudgets berücksichtigt. `context.budget()` und `context.cancellation()` dürfen sich nicht in der Frage widersprechen, ob für diesen Task noch normale Arbeit zulässig ist.

`isCancellationRequested()` und `reason()` dürfen den Zustand ohne Exception abfragen. Eine kontrollierte Abbruchprüfung mit `throwIfCancellationRequested()` beziehungsweise einem Runnercheckpoint macht den Abbruch bindend. Bei lokalem Ablauf wird `TIMEOUT` in der Runsteuerung gelatcht, sodass auch die weiteren ausgewählten Zweige gestoppt werden. Die erste festgehaltene Cancel-Ursache bleibt stabil.

Ein scoped Token oder Budget darf nach dem Ende seines Task-/Operationskontexts keinen späteren Runzustand mehr verändern. Das gilt **auch für ein während der Ausführung bereits entnommenes Handle**, nicht nur für erneute Contextgetter. Insbesondere darf das nachträgliche Polling einer alten Taskdeadline den nächsten Task nicht abbrechen. Bindende Prüfungen eines geschlossenen Scopes scheitern als `ContextAccessException` mit Code `CONTEXT_CLOSED`, bevor sie irgendeine Cancel-/Timeoutbeobachtung verändern.

Die normalen Abfragen `isCancellationRequested()`, `reason()`, `remaining()` und `expired()` sind reine Beobachtungen: Sie werfen keine Kontrollsignale und setzen weder `cancelReason` noch einen Zustand „Cancellation bereits beobachtet“. Während eines aktiven Scopes spiegeln sie die wirksame Run-/Taskkontrolle wider. Nach dessen Schliessung lesen gespeicherte Handles ausschliesslich einen eingefrorenen Abschluss-Snapshot ihres eigenen Scopes. Sie lesen dann weder die fortlaufende Uhr noch eine spätere Run-Cancellation. Ein vorher erfolgreich abgeschlossener Scope bleibt dadurch auch dann ohne Cancel-Ursache, wenn ein späterer Task abgebrochen wird.

Die Lebensdauerprüfung muss in den herausgegebenen Handles sitzen oder von ihnen zuverlässig verwendet werden. Eine Prüfung allein in `BaseContext.cancellation()`/`budget()` vor dem Zurückgeben des Handles erfüllt diesen Vertrag nicht. Die genaue Lebensdauertabelle steht in Abschnitt 4.3.1.

Die reine Anfrage `requestCancel` und die kontrollierte Feststellung sind zu unterscheiden: Ein bereits unbehandelter Fachfehler darf nicht durch eine lediglich parallel eingegangene Abbruchanfrage verdeckt werden. Dafür genügt ein kleiner interner Beobachtungszustand; keine globale fachliche Fehlerregistry.

**F2-03 — Feste Checkpoints.**

Das wirksame Budget und der Abbruchzustand werden mindestens geprüft:

1. vor dem Predicate;
2. nach normalem Predicate-Return, auch bei `false`;
3. vor Beginn einer Taskaction beziehungsweise Operationkonfiguration;
4. nach normaler Konfiguration/Requestprüfung, vor dem Start der Fachoperation;
5. vor jedem weiteren synchronen Operationsaufruf innerhalb einer Action;
6. nach normalem Handler-/Action-Return;
7. nach notwendigem Ressourcen-Close, vor Resultatfreigabe und Terminalentscheidung;
8. vor Beginn des nächsten normalen Tasks.

Die doppelte Prüfung um Close ist beabsichtigt: Der erste Check stoppt normale Weiterarbeit; der zweite verhindert eine verspätete Erfolgsfreigabe. Auf Fehlerpfaden wird die Ursache zuerst gesichert und nicht durch eine unbedacht in `finally` geworfene Timeout-Exception ersetzt.

**F2-04 — Abbruch kann nicht durch Catch aufgehoben werden.**

Wird ein kontrollierter Cancel/Timeout im Script gefangen, bleiben weitere `ctx.run`-/`call`-Aufrufe gesperrt. Auch ein nachfolgender normaler Return macht den Task nicht erfolgreich. Ein nach dem Abbruch entstandener nichtleerer Rückgabewert wird nicht im `ResultStore` veröffentlicht.

Das ändert nicht die Semantik gewöhnlicher bewusst gefangener Fachexceptions. Solche Exceptions setzen weiterhin keinen versteckten Runfehler.

**F2-05 — Chronologie und Fehlerpräzedenz explizit.**

Die Entscheidung basiert auf kontrollierten Beobachtungen, nicht auf Vermutungen über den physikalisch früheren Ablauf zweier Threads. Die folgende Tabelle ist verbindlich:

| Beobachteter Ablauf | Ergebnis |
|---|---|
| Normaler Return, kein Abbruch und kein Closefehler | SUCCEEDED; Ergebnis erst nach Close freigeben. |
| Normaler Return, lokales Budget am Checkpoint abgelaufen | Task/Invocation CANCELLED, `TIMEOUT`; Run 20; kein Ergebnis. |
| Predicate liefert `false`, Budget am nachfolgenden Checkpoint abgelaufen | CANCELLED, nicht regulär SKIPPED. |
| Echte unbehandelte Exception zuerst erfasst; Timeout erst danach festgestellt | FAILED; ursprüngliche Exception primär; keine Umdeutung in Timeout. |
| Cancellation zuerst kontrolliert festgestellt; danach normale Rückgabe oder Abbruch-/Closefehler | CANCELLED; Nebenursachen erhalten. |
| Handler kehrt rechtzeitig zurück, sein erforderliches Close wirft zuerst einen echten Fehler | FAILED; erster Closefehler primär. |
| Handler kehrt rechtzeitig zurück, Close gelingt, Budget ist danach abgelaufen | CANCELLED; kein Resultat. |
| Action fängt gewöhnlichen Fachfehler, Budget bleibt gültig, Action/Close gelingen | Action SUCCEEDED; der frühere innere Fehler bleibt in dessen eigener History. |
| `finishJob` vorliegt und am Kontrollcheckpoint auch wirksamer Cancel/Timeout erkannt wird | Cancel hat Vorrang vor NO_DATA; erforderliche Freigaben bleiben aktiv. |

Die Implementierung darf die Tabelle nicht allein durch `if (cancelRequested) return cancelled` vor jeder Exceptionauswertung ersetzen. Bereits abgeschlossene frühere Tasks bleiben in ihrer History erfolgreich; nur der überfällige aktive Task und noch nicht gestartete Arbeit sind betroffen.

**F2-06 — Cleanup hat eine eigene gemeinsame Nachfrist.**

Job-Cleanup und Abschluss behalten das bestehende gemeinsame Nachfristbudget, standardmässig 30 Sekunden. Dieses Budget wird einmal erzeugt, nicht pro Cleanupaktion erneuert. Das abgelaufene Arbeitsbudget darf den Cleanup-Body nicht automatisch überspringen.

Erforderliche bereits registrierte Ressourcenfreigaben werden weiterhin bestmöglich versucht. Nicht kooperierendes Close kann die Frist blockieren; die harte Grenze bleibt beim externen Supervisor. Kein `Thread.stop`, keine zusätzliche JVM pro Task und keine Garantie sicheren Rollbacks nach Kill.

Ein an einer kontrollierten Grenze festgestellter Interrupt wird als Abbruch behandelt. Falls das Flag für eigene Cleanup-Arbeit vorübergehend geklärt wird, ist die Entscheidung zu dokumentieren und vor Rückkehr wiederherzustellen. Fachadapter dürfen InterruptedException nicht als leeren erfolgreichen Datensatz behandeln.

**F2-07 — Diagnose bleibt mit bestehenden Statuswerten lesbar.**

Die bestehenden Status- und Exitcodefamilien bleiben bestehen: `CANCELLED`/20 für primären kontrollierten Abbruch, `FAILED`/10 für gewöhnliche primäre Ausführungsfehler. Es gibt keinen neuen Taskstatus und keinen neuen Timeout-Exitcode.

Die erste kontrolliert festgestellte **primäre** Cancel-Ursache wird unveränderlich mit dem jeweiligen terminalen Outcome gespeichert. Sie darf weder aus einer Exceptionmeldung noch aus einem konfigurierten Timeout im Plan rekonstruiert werden.

Für den konsolidierten Vertrag wird die Ausgabe festgelegt:

- Im bestehenden Report: additives Feld `cancelReason` bei `execution`, jedem Task und jeder Invocation, mit Enumtext wie `TIMEOUT`, `INTERRUPTED` oder `REQUESTED`.
- Für ein Outcome mit Status `CANCELLED` ist dieses Feld nicht leer; für andere Statuswerte wird es einheitlich als `null` geschrieben. Das vorhandene Feld `reason` für Skip-/NOT_RUN-Gründe bleibt unverändert.
- Im zugehörigen terminalen Run-/Task-/Invocationereignis: Attribut `cancel_reason` mit genau derselben Ursache, wenn dessen Outcome `CANCELLED` ist. Für andere Statuswerte wird dieses Attribut nicht gesetzt.

Es werden keine neuen Statuswerte, Exitcodes oder Terminaleventnamen eingeführt. Die neuen Felder sind additive schema-1-Ergänzungen; vorhandene Felder werden weder entfernt noch umbenannt. Ein kontrollierter Abbruch darf weiterhin ohne künstlichen Fehlerstack auskommen. Die Ursache muss trotzdem explizit vorhanden sein.

Die Ursache ist auf den eigenen Abschluss bezogen: Ein vorher erfolgreich beendeter Task wird nicht rückwirkend mit der Cancel-Ursache des nachfolgenden Tasks versehen. Bei primärem Fachfehler bleibt `FAILED` massgeblich; spätere Abbruchinformationen können begrenzte Nebeninformationen bleiben, werden aber nicht als primäre Cancel-Ursache ausgegeben. Siehe Abschnitt 4.3.2.

**F2-08 — Keine versteckte Verlängerung oder Ausführungsgarantie.**

`remaining()` darf nicht negativ werden; ein fehlendes Budget bleibt unbegrenzt. Der Zeitursprung muss zur verwendeten monotonen Uhr passen. Keine zweite Wallclock-Deadline und kein neues Budget pro Poll. Reine Zeitberechnung gehört nicht in `observeCancel(...)`: insbesondere DARF `RunControl.childBudget().expired()` beziehungsweise `.remaining()` keinen globalen Abbruch auslösen. Ein bindender Checkpoint wertet das Budget aus und beobachtet den Abbruch ausdrücklich.

Es wird kein zusätzlicher Scheduler eingeführt, der eine willkürliche JVM-Bibliothek sicher hart unterbrechen soll. Die Abgrenzung zwischen reiner Abfrage, bindendem Checkpoint und gespeichertem terminalem Snapshot ist mit den bestehenden wenigen Klassen zu lösen.

### 4.3 Technische Umsetzung bis zur Methodengrenze

Eine kleine interne Klasse genügt als Referenzentwurf:

```java
// gretl-runner; keine neue öffentliche gretl-api-Schnittstelle.
final class ScopedCancellationToken implements CancellationToken {
    // owner stellt internen Scopezustand und Abschlusssnapshot bereit.
    // Ein blosser Run-/Budgetverweis würde gespeicherte Handles nicht absichern.
    ScopedCancellationToken(BaseContext owner, RunControl runControl,
                            TimeBudget effectiveBudget);

    public boolean isCancellationRequested();
    public Optional<CancelReason> reason();
    public void throwIfCancellationRequested();
}
```

Der Ausschnitt bezeichnet Signaturen, nicht einen bereits kompilierbaren Klassenrumpf. Die tatsächliche Implementierung kann einen bestehenden internen Typ erweitern, solange die folgenden Verantwortlichkeiten erhalten bleiben:

| Klasse/Methoden | Verpflichtung |
|---|---|
| `RunControl.requestCancel`, `checkBeforeWork`, `snapshot` | Stabile erste Ursache; keine Rücknahme; globales Budget bleibt erhalten. Kontrollierte Feststellung von blosser Anfrage unterscheiden, soweit für Präzedenz erforderlich. |
| `RunControl.childBudget` | Einmalig bestimmtes lokales Limit, begrenzt durch Runbudget; reine `remaining`-/`expired`-Berechnung, keine Mutation der Abbruchsteuerung und keine Verlängerung bei inneren Aufrufen. |
| `BaseContext.cancellation`, `budget`, `markClosed` | Scoped Handles ausgeben, deren Lebensdauer auch bei Speicherung eingehalten wird. Bei Schliessung eigenen terminalen Lesesnapshot einfrieren. Keine nachträgliche Mutation durch alte Handles. Cleanup erhält keine bereits abgelaufene Arbeitskontrolle. |
| `TaskContextImpl.call/invoke` | Vor jeder neuen Operation den zugehörigen scoped Check ausführen. |
| `SequentialJobRunner.executeTask` | Lokales Budget vor Predicate/Body festlegen; Checkpoints auch bei Predicate-false und normalem Return. |
| `SequentialJobRunner.completeTask` | Ergebnis nicht vor erforderlichem Close und finaler Budgetprüfung abschliessen. |
| `OperationInvoker.invoke` | Budget des umgebenden Tasks für Konfiguration und Ausführung verwenden; Ergebnis-/Close-/Cancelentscheidung in einer gemeinsamen Logik. |
| `FailureArbiter.resolve` | Explizite WorkCompletion und wirksamen ControlSnapshot auswerten; Tabelle aus F2-05 abbilden. Keine Uhr und keine Logzeilen in diesem Arbiter auswerten. |
| `TaskCompletion`, `TaskOutcome`, `InvocationOutcome`, `JobOutcome` | Primäre `CancelReason` als terminalen Snapshot tragen und Konsistenz zum Status prüfen; keine spätere globale Neuauswertung. |
| `RunReportWriter`, Task-/Invocation-/Run-Terminalpfade | `cancelReason` beziehungsweise `cancel_reason` aus dem jeweiligen Outcome übernehmen; tatsächliche JSON-Strukturen testen. |

Die Invocation- und Taskentscheidung dürfen nicht zwei auseinanderlaufende Kopien derselben Präzedenzregeln erhalten. Eine kleine gemeinsame interne Hilfsmethode oder vorhandene Arbiterlogik wiederverwenden. Eine bereits erfolgreich abgeschlossene Invocation wird nicht rückwirkend umetikettiert, wenn eine spätere Invocation derselben Action in einen Timeout läuft.

**Wichtig bei Fehlergraphen:** Erst zusätzliche Closefehler als suppressed beziehungsweise sekundär anbinden, dann den unveränderlichen `FailureInfo`-Snapshot erzeugen. Die Snapshotreihenfolge muss in den berührten Pfaden geprüft werden, damit die neue Abschlusslogik keine gerade erfassten Ursachen verliert.

### 4.3.1 R1: Lebensdauer bereits ausgegebener Handles

Der konkrete Gegenbeweis am Nachprüfstand ist: A hat eine Sekunde lokales Budget und endet rechtzeitig. B hat zehn Sekunden. Nach zwei Sekunden wird ein in A gespeicherter Token beziehungsweise ein Budget abgefragt; dadurch wird B fälschlich abgebrochen. Die Nachprüfung enthält drei isolierte Proben für Polling, bindende Prüfung und Budgetzugriff. [N1, N3]

Der verbindliche Zustandsvertrag lautet:

| Zustand des ursprünglichen Contexts | Reine Token-/Budgetabfrage | Bindende Prüfung auf dem gespeicherten Token |
|---|---|---|
| `ACTIVE` | Aktuellen wirksamen Zustand lesen, ohne Runsteuerung oder Beobachtungsreihenfolge zu verändern. | Aktuelles Budget/Cancel prüfen; berechtigten Abbruch latchen und Kontrollsignal auslösen. |
| `CLOSING` | Gleiches Verhalten innerhalb dieses noch nicht abgeschlossenen Scopes; erforderliche Freigaben bleiben bestmöglich aktiv. | Relevante kontrollierte Prüfung bleibt möglich; normale neue Arbeit bleibt nach bestehendem Closevertrag gesperrt. |
| `CLOSED` | Unveränderlichen eigenen Abschlusssnapshot zurückgeben. Nicht erneut Uhr, RunControl oder anderen Context lesen. | `CONTEXT_CLOSED`, ohne Timeout-Latch, ohne Interruptflagänderung und ohne neue Arbeit. |

Für ein begrenztes Budget wird beim Scopeende der zuletzt reine aktuelle Restzeitwert eingefroren, mindestens `Duration.ZERO`; `expired()` bleibt danach dessen eigener Abschlusswert. Ohne Limit bleibt `remaining()` leer und `expired()` falsch. Ein regulär erfolgreicher Scope liefert nach Schliessung für Cancellation weiterhin `false` und eine leere Ursache. Ein als abgebrochen abgeschlossener Scope behält seine damalige Ursache. Ein neu angefordertes Cancel aus B verändert diesen A-Snapshot nicht.

Der Context kann pro Scope ein Token-/Budget-Handle wiederverwenden. Alternativ dürfen mehrere Handles denselben internen Lebensdauer-/Snapshotzustand lesen. Es braucht keinen öffentlichen neuen Typ und keine globale Handleliste. Der Snapshot darf nach `CLOSED` keinen aktiven Zugriff auf den gesamten Run benötigen.

Die vorhandenen bindenden Checkpoints führen weiterhin zur unveränderlichen ersten Cancel-Ursache. Ein aktiver reiner Poll kann `true`/`TIMEOUT` anzeigen, ohne schon die Präzedenz „Cancellation zuerst kontrolliert festgestellt“ zu erzeugen; spätestens der nächste bindende Checkpoint macht den Ablauf zum kontrollierten Abbruch. Das verhindert, dass allein die Reihenfolge unverbindlicher Diagnoseabfragen einen zuerst erfassten Fachfehler umdeutet.

Ein fehlgeschlagener bindender Zugriff auf ein geschlossenes Handle darf als gewöhnliche `ContextAccessException` den **aufrufenden** Task scheitern lassen, wenn dessen Code diese Exception nicht behandelt. Das ist kein Abbruch durch das alte Budget. Die Gegenprobe muss diese Exception gezielt behandeln und dann nachweisen, dass B weiterarbeiten kann.

### 4.3.2 R2: Ursache vom Checkpoint bis zum Report erhalten

Am Nachprüfstand wird `TaskCompletion.cancelled(...)` ohne Cause-Wert erzeugt. Reale Timeoutreports zeigen dadurch `CANCELLED`/20, aber keine konkrete Abbruchursache. Die Aufgabe ist nicht, einen synthetischen Fehlergraphen zu erzeugen, sondern das bereits bekannte Steuerungsresultat korrekt zu transportieren. [N1, N4]

Die interne Übergabe ist verbindlich:

```text
bindender Checkpoint / explizites kontrolliertes Abbruchsignal
  → erste CancelReason stabil festhalten
  → InvocationCompletion und TaskCompletion mit eigener Ursache
  → InvocationOutcome und TaskOutcome einfrieren
  → JobOutcome bei CANCELLED mit zugehöriger primärer Ursache
  → vorhandene Report- und Terminaleventpfade aus diesen Werten speisen
```

Eine mögliche interne Factorysignatur ist:

```java
static TaskCompletion cancelled(
        CancelReason reason, List<FailureInfo> secondaryFailures);
```

Die tatsächlichen Records dürfen ein internes `Optional<CancelReason>` verwenden. Die Konstruktion muss für `CANCELLED` ohne Ursache fehlschlagen; eine unbegründete Defaultursache wie `REQUESTED` ist kein Ersatz für korrektes Weiterreichen. Alle produktiven Konstruktionstellen und Testfixtures sind anzupassen. Die öffentliche `gretl-api` bleibt unverändert.

Für denselben betroffenen Task/Run müssen Report und terminale Ereignisse dieselbe Ursache enthalten. Bei Run-Cancellation vor dem ersten Task trägt der Run die Ursache; nicht gestartete Tasks bleiben `NOT_RUN` mit ihrem bisherigen Grund und ohne eigene Cancel-Ursache. Eine erfolgreich abgeschlossene erste Invocation bekommt keine Cause einer späteren Invocation derselben Action.

Beispiel für den relevanten **Reportauszug**, nicht für eine neue Reportstruktur:

```json
{
  "execution": {
    "status": "CANCELLED",
    "exitCode": 20,
    "cancelReason": "TIMEOUT",
    "tasks": [
      {
        "taskId": "caughtTimeout",
        "status": "CANCELLED",
        "reason": null,
        "cancelReason": "TIMEOUT",
        "hasValue": false
      }
    ]
  },
  "errors": []
}
```

JSON-Assertions müssen die wirklichen Felder prüfen, einschliesslich `REQUESTED` und `INTERRUPTED`, nicht nur `TIMEOUT` im Plantext suchen. Ein Schema-1-Leser, der unbekannte additive Felder ignoriert, bleibt verwendbar. Interne Reportverträge und Beispiele sind entsprechend nachzuführen.

### 4.4 Tests F2

| Test-ID | Szenario und Orakel |
|---|---|
| TC-F2-01 | Gültiges Runbudget, kürzeres lokales Taskbudget; FakeClock im Body über Deadline setzen, normal zurückgeben. Task CANCELLED, Run 20, kein Ergebnis, Nachfolger NOT_RUN. |
| TC-F2-02 | Derselbe lokale Ablauf in einer Operation; `context.cancellation().throwIfCancellationRequested()` erkennt TIMEOUT, obwohl global noch Zeit bleibt. |
| TC-F2-03 | Predicate überschreitet lokales Limit und liefert `false`. Kein regulärer Skip; Action/Handler wurde nie ausgeführt. |
| TC-F2-04 | Konfigurationsclosure überschreitet lokales Limit. Handlerzähler bleibt null; Aufruf hat ID und klaren Abbruchzustand. |
| TC-F2-05 | Mehrere synchrone Aufrufe in einer Action verbrauchen zusammen das Taskbudget. Vor weiterer Invocation kein Neustart des Limits; keine zusätzliche Handlerarbeit. |
| TC-F2-06 | Runlimit kürzer als lokales Limit. Runbudget bleibt massgeblich; zweiter Task erhält keine neue volle Runlaufzeit. |
| TC-F2-07 | Script fängt Timeoutsignal und versucht danach weitere Operation sowie normalen Return. Keine weitere Operation, kein erfolgreiches Resultat, Run 20. |
| TC-F2-08 | Echte Fachexception wird zuerst erfasst, anschliessend läuft die Uhr ab oder Cancel wird angefragt. Fehler und Cause bleiben primär, Exitcode 10. |
| TC-F2-09 | Cancel zuerst kontrolliert erkannt, danach Closefehler. Exitcode 20, Closefehler diagnostisch erhalten, keine Erfolgsmeldung. |
| TC-F2-10 | Close überschreitet nach rechtzeitigem Handlerreturn das Budget und gelingt. Kein Resultat; Timeout wird am Abschlusscheckpoint erkannt. Variante: zuerst echter Closefehler, danach erkannter Ablauf → FAILED. |
| TC-F2-11 | Zwei Job-Cleanups nach Timeout benutzen dieselbe Nachfrist. Beide werden bei kooperativ kurzer Arbeit versucht; das Arbeitsbudget wird nicht wieder verwendet. |
| TC-F2-12 | Unterbrechung in der kontrollierten Ausführung. Abbruch, Cleanupversuch und dokumentierte Interruptflag-Behandlung; kein erfolgreicher leerer Return. |
| TC-F2-13 | Behandelter gewöhnlicher Fachfehler ohne Timeout. Umgebende Action bleibt erfolgreich; keine neue globale Business-Fehlerpflicht. |
| TC-F2-14 | Kein Timeout beziehungsweise ausreichend langes Budget. Bestehende Erfolgs-/NO_DATA-/Predicate-Skip-Semantik unverändert; restliche Zeit nie negativ. |
| TC-F2-15 | Token in A mit Limit 1 s speichern; A vor Ablauf erfolgreich beenden; B mit Limit 10 s starten und FakeClock auf 2 s setzen. Alter reiner Poll bleibt beim eigenen Abschlusswert und beeinflusst B nicht. Bindender Altzugriff meldet genau CONTEXT_CLOSED; nach gezieltem Catch kann B erfolgreich enden. Kein TIMEOUT-Latch aus A. |
| TC-F2-16 | Dasselbe Lebensdauerexperiment parametrisiert für `budget.remaining`, `budget.expired` und Handles einer bereits abgeschlossenen inneren Invocation. Abschlusssnapshot bleibt unverändert; nachfolgende Invocation beziehungsweise B bleibt innerhalb des eigenen Budgets erfolgreich. |
| TC-F2-17 | Aktive reine Token-/Budgetabfragen nach Ablauf zeigen den wirksamen Zustand, verändern aber weder Run-Cancel-Latch noch Beobachtungspräzedenz. Erst bindender Checkpoint beobachtet Cancel. Separater Fall: zuerst vom Runner erfasste echte Exception bleibt primär trotz späterer bindender Timeoutprüfung. |
| TC-F2-18 | TIMEOUT, REQUESTED und INTERRUPTED jeweils bis tatsächlichem Report und Terminalereignis verfolgen. CANCELLED hat eine konkrete unveränderliche Cause; Code 20; Report `cancelReason` und Event `cancel_reason` stimmen überein. Eine leere Fehlerliste bei reinem Kontrollabbruch ist zulässig. |
| TC-F2-19 | Vorheriger Task und erste Invocation erfolgreich, spätere Invocation abgebrochen. Frühere Outcomes behalten Status und leeres `cancelReason`; abgespeicherte Handles ändern sich nicht. Auch Run-Cancel vor erstem Task sowie Taskcancel mit Close-Nebenfehler prüfen. |
| TC-F2-20 | Früher unbehandelter Fachfehler oder Closefehler, danach Cancel/Timeout und gegebenenfalls finishJob. FAILED/10 und ursprüngliche Ursache bleiben primär; neues `cancelReason` ist nicht fälschlich primär gesetzt. Bestehende Präzedenz-Gegenproben aus dem Nachprüfstand erhalten. |

Unit- und Runner-Tests verwenden die vorhandene injizierbare monotone Uhr. Reale Wartezeiten sind dort unzulässig. Prozess-Tests dürfen kurze reale Arbeit mit deutlichem Abstand zum Grenzwert verwenden, prüfen aber primär den Zustand und den fehlenden Nachfolger, nicht millisekundengenaue Laufzeiten.

## 5. F3: Kontrollierter Abschluss bei Workspace-/Setupfehlern

### 5.1 Befund und Abgrenzung

Am Ausgangsstand wird das Task-Arbeitsverzeichnis vor dem geschützten Taskbody angelegt. Eine dabei auftretende `IOException` wird in der Run-Schleife als `UncheckedIOException` weitergeworfen. Dadurch wird `runCleanups()` nicht erreicht. Der gemeinsame Job-Einstieg schreibt seinen Bericht erst nach Rückgabe eines `JobOutcome`. Ein gewöhnlicher Dateisystemfehler kann somit Cleanup und diesen Bericht umgehen. [S1, S2]

Dieser Abschnitt verlangt keinen Bericht nach `SIGKILL`, VM-Absturz oder Stromausfall. Er verlangt die kontrollierte Behandlung gewöhnlicher Fehler, solange die JVM läuft und die betreffenden Abschlussressourcen noch nutzbar sind.

### 5.2 Verbindliche Anforderungen

**F3-01 — Runbeginn eindeutig festlegen.**

Die Registrierung des Jobs, Parameter-/Planprüfung und Vorbereitung der konfigurierten Roots liegen vor dem begonnenen Run. Ein Run gilt hier als begonnen, nachdem seine exklusive Workspace-Leaf erfolgreich eröffnet und der Laufzeitkontext eingerichtet wurde und bevor der erste Task gestartet wird. `gretl.run.started` markiert diesen Übergang genau einmal.

Scheitert schon diese Vorbereitung, wird ein Start-/Umgebungsfehler mit dem vorhandenen Startcode 2 gemeldet. Es gibt kein `gretl.run.started`, kein `gretl.run.finished` für einen begonnenen Run, keinen Taskstart und **keinen Aufruf der registrierten Job-Cleanup-Bodies**. Eine sichere Startdiagnose bleibt erforderlich. Ein regulärer Bericht eines begonnenen Runs wird nicht erfunden; ein eventuell schon eröffneter Logkanal wird ordentlich geschlossen.

Insbesondere gilt dies für eine schon belegte exklusive `<workRoot>/<runId>`-Leaf als Datei, Verzeichnis oder Symlink sowie einen I/O-/Securityfehler bei deren Eröffnung. Eine Kollision wird nicht als FAILED/10 und nicht als interne Invariantenverletzung mit Code 40 umgedeutet. Vorhandene Marker, Rechte und fremde Leafs bleiben erhalten. Die Zuordnung erfolgt am tatsächlichen Vorbereitungsübergang, nicht durch pauschales Umdeuten jeder späteren `IOException`.

**Präzisierung nach R3:** Ein gebildeter Plan oder eine bereits vergebene Run-ID allein ist noch kein Runbeginn. Die Vorprüfung der Roots in `JobRuntime` ersetzt auch nicht den Fehlervertrag der anschliessenden exklusiven Leaf-Eröffnung im Runner. Ein vorbereiteter, aber noch nicht begonnener Lauf darf kein Job-Cleanup mit potenziellen externen Wirkungen auslösen. [N1, N5]

**F3-02 — Task-Setup gehört zur beobachtbaren Taskausführung.**

Sobald ein ausgewählter Task an die Reihe kommt und der Vorab-Abbruchcheck erfolgreich ist, wird seine Ausführung registriert. Der nachfolgende Workspaceaufbau liegt im kontrollierten Ausführungsbereich. Schlägt er fehl:

- genau dieser Task erhält `FAILED`;
- seine Fachkonfiguration, sein Predicate und sein Body werden nicht ausgeführt, sofern die benötigte Vorbereitung noch nicht gelungen war;
- die terminale Taskmeldung besitzt strukturiert `phase = "task-setup"`; der betroffene Taskeintrag im Report ergänzt `failurePhase = "task-setup"`, und die ursprüngliche I/O-Ursache bleibt im Fehlergraphen sichtbar;
- bereits abgeschlossene Tasks bleiben unverändert;
- die verbleibenden ausgewählten Tasks werden `NOT_RUN` mit `FAIL_FAST`;
- der Run wird gewöhnlich `FAILED`/10, nicht pauschal `INTERNAL_ERROR`/40.

Ein Task darf dabei ein Start-/Endepaar für den Versuch haben, ohne dass dies behauptet, sein Fachhandler sei gestartet worden. Die Ereignisphase macht den Unterschied deutlich. `failurePhase` ist eine additive Berichtsangabe für den bekannten Setupfehler; andere Taskfälle dürfen das neue Feld als `null` führen. Eine allgemeine Refaktorierung aller Fachphasen ist nicht Teil dieses Auftrags. Ein Stackframe allein oder eine Fehlermeldung mit dem Wort „workspace“ erfüllt den strukturierten Nachweis nicht.

**F3-03 — Ein Abschlussweg nach Runbeginn.**

Jeder gewöhnliche Fehler innerhalb des begonnenen Runs wird zunächst in einen expliziten Zustand überführt. Danach gibt es genau einen Abschlussweg für:

1. noch offene Task-/Invocationressourcen;
2. verbliebene Taskzustände;
3. registriertes Job-Cleanup;
4. endgültige fachliche Outcome-Bildung;
5. Retention des eigenen Workspaces;
6. Rückgabe an den gemeinsamen Job-Einstieg und dessen Bericht-/Prozessabschluss.

Keine Rückgabe und kein gewöhnliches `throw` darf diese Kette unbeabsichtigt umgehen. Ein `finally`, das nur den Logger schliesst, erfüllt den Vertrag nicht. Ebenso unzulässig sind mehrere konkurrierende `finally`-Blöcke, die Cleanup oder einen Runabschluss doppelt auslösen.

Es ist kein neuer universeller Finalizer-Service erforderlich. Eine lesbare Aufteilung von `SequentialJobRunner.run()` in Ausführung und Abschluss mit begrenztem lokalen Zustand genügt.

**F3-04 — Cleanup darf nicht unnötig vom kaputten Workspace abhängen.**

Der Ausfall eines Taskverzeichnisses allein darf nicht verhindern, dass registrierte Cleanupaktionen laufen, die dieses Verzeichnis gar nicht benötigen. Cleanup erfolgt weiterhin in umgekehrter Registrierungsreihenfolge mit dem gemeinsamen Nachfristbudget.

Ein Cleanupkontext muss Logging und bekannte externe Pfade benutzen können, ohne zuerst zwingend sein eigenes Unterverzeichnis auf der möglicherweise defekten Platte anzulegen. Ein kleiner sinnvoller Weg ist, den Cleanup-Arbeitspfad zunächst nur zu berechnen und das Verzeichnis erst bei `workPath(...)` anzulegen.

Fordert eine Cleanupaktion tatsächlich einen nicht mehr erzeugbaren Workpfad an, erhält sie den echten I/O-Fehler. Dieser wird als sekundärer Cleanupfehler festgehalten und verhindert nicht den Versuch der übrigen registrierten Cleanups. Kein stiller Wechsel in `/tmp` und keine Aussage, Cleanup sei vollständig erfolgreich gewesen.

Wenn eine beliebige Cleanupaktion selbst nie zurückkehrt, bleibt die kooperative Grenze bestehen. Dieser Auftrag baut keine sichere Zwangsbeendigung fremden Codes.

**F3-05 — Ursachen werden nicht durch Abschlussfehler verdeckt.**

Ein Workspacefehler des aktiven Tasks bleibt die primäre Ursache. Spätere Cleanup-, Retention- oder Berichtfehler werden mit eigener Phase und soweit möglich als Nebenursachen festgehalten. Der Fehlergraph wird erst nach Anbindung der betreffenden Ursachen eingefroren. Fehlertexte und Pfade werden weiterhin durch die bestehende Redaction geführt.

Tatsächliche Core-Invariantenfehler bleiben von gewöhnlichen Task-/I/O-Fehlern unterscheidbar. `VirtualMachineError`, `ThreadDeath` und harter Kill erhalten kein uneinlösbares Best-effort-Erfolgsversprechen. Die Korrektur darf solche Zustände nicht beliebig als normale Fachfehler wegfangen.

**F3-06 — Bericht und Abschluss auch für den fehlerhaften Versuch.**

Liefert der Runner nach einem Setupfehler ein fehlgeschlagenes `JobOutcome`, MUSS der gemeinsame Job-Einstieg den normalen Berichtweg verwenden. Bei funktionierendem Reportspeicher enthält der Bericht mindestens:

- Run-ID und Quellidentität;
- tatsächlich ausgewählten Plan;
- Zustände der vorherigen, fehlerhaften und nicht gestarteten Tasks;
- den ursprünglichen Fehler samt Cause und kontrolliert begrenzten Nebenfehlern;
- tatsächlichen Status und Exitcode;
- nachvollziehbare Cleanup-/Loggingdiagnosen.

Für den begonnenen Run entsteht genau ein terminales `gretl.run.finished`. Für diesen F3-Fall darf es keinen erfolgreichen terminalen Runstatus geben. Es wird kein neuer zweiter Terminaleventtyp als Umgehung eingeführt.

Scheitert zusätzlich das Schreiben des Berichts, wird dies über den noch funktionierenden sicheren Ausgabekanal gemeldet. Es wird weder das Vorhandensein der Datei behauptet noch endlos auf denselben defekten Pfad geschrieben. Die ursprüngliche Task-/Workspaceursache bleibt in der verfügbaren Diagnose erhalten. Ohne funktionierenden Ausgabekanal ist eine vollständige Diagnose nicht garantiert; der Prozess darf daraus aber keinen Erfolg machen.

Die Zuständigkeit für das terminale Ereignis muss beim berührten Abschlussweg eindeutig sein. Bestehende Prozessabschlussregeln werden nicht durch ein zweites, konkurrierendes Abschlussereignis überschrieben. Eine gegebenenfalls nötige kleine interne Abstimmung zwischen Runner und `JobRuntime` darf nicht in neue öffentliche Task-API oder CLI-Schalter ausweichen.

**F3-07 — Keine neue Facharbeit als Fehlerbehandlung.**

Weder der Setupfehler noch ein fehlender Report starten eine Wiederholung des Jobs oder einer bereits ausgeführten Operation. Das Cleanup darf keine Success-Quittierung ersetzen. Bereits geschriebene externe Testmarker beziehungsweise Daten bleiben als mögliche Teilwirkungen erhalten und werden im Test ausdrücklich geprüft.

### 5.3 Klassen- und Methodenauftrag

| Stelle | Konkrete Verantwortung nach der Korrektur |
|---|---|
| `SequentialJobRunner.run` | Run-Vorbereitung und begonnene Ausführung trennen. Normale Setupfehler in einen Outcome-Pfad überführen. Cleanup und Abschluss genau einmal erreichen. Umgebung erst danach schliessen. |
| `SequentialJobRunner.executeTask` | Taskversuch vor dem geschützten Workspaceaufbau registrieren; keinen ungeschützten `IOException`-Ausgang lassen, der den ganzen Runabschluss umgeht. |
| `ResultStore.start/complete` | Den Setupfehlerversuch ohne Resultat korrekt als FAILED speichern; keine doppelte Completion und keine verbleibenden RUNNING-/PENDING-Zustände am Runende. |
| `OperationInvoker.invoke` | Entsprechende I/O-Vorbereitung innerer Invocations in denselben kontrollierten Fehlerpfad einordnen; keine Phantom-Erfolgseinträge. |
| `WorkspaceManager.Workspace` | Pfadberechnung und erforderliche Verzeichniserzeugung soweit nötig trennen. Keine unabhängige zweite Retentionlogik. |
| `SequentialJobRunner.runCleanups` / `CleanupContextImpl` | Cleanupkontext ohne unnötigen eager I/O-Zwang ermöglichen. Für jede registrierte Aktion einmaliger Versuch; Fehler sammeln; Reihenfolge erhalten. |
| `JobRuntime.run` | Für zurückgegebene fehlgeschlagene Outcomes denselben Berichtweg wie für Erfolg verwenden. Bridge, Logging und Shutdownhook genau einmal beziehungsweise idempotent finalisieren. |
| `RunReportWriter` | Einen normalen failed Report serialisieren; keine neue Notfall-Reportstruktur mit anderen Pflichtfeldern erfinden. |

Für die strukturierte Task-Setupdiagnose kann eine kleine interne `TaskSetupException extends RuntimeException implements DiagnosticCarrier` verwendet werden. Sie trägt Task-ID und einen stabilen Code wie `TASK_WORKSPACE_PREPARATION_FAILED`, mit der `IOException` als Cause. Die Phase wird im Event beziehungsweise im vorhandenen Diagnosekanal angegeben; `DiagnosticInfo` muss nicht nur dafür global erweitert werden.

**Schematischer Ablauf, kein Ersatz für die existierenden Typen:**

```text
Plan und Startkonfiguration validieren
Eigenen Workspace exklusiv eröffnen                  # noch Startfehler möglich
Runkontext eröffnen; run.started
try:
    für die ausgewählten Tasks:
        Kontrollcheckpoint
        Taskversuch starten
        try:
            Taskworkspace vorbereiten
            Predicate / späte Konfiguration / Body
        catch gewöhnlicher Arbeits-/Setupfehler:
            Ursache sichern
        finally:
            erforderliche Taskressourcen schliessen
        Taskentscheidung gemäss F2 bilden und genau einmal speichern
        bei Fehler/Cancel/Finish keine weitere normale Arbeit starten
finally für jeden begonnenen Run:
    noch offene Statuswerte terminalisieren
    Job-Cleanup in umgekehrter Reihenfolge versuchen
    fachliches JobOutcome mit Primär-/Nebenursachen bilden
    eigene Retention bestmöglich anwenden
    Umgebung schliessen
JobRuntime: Bericht und eindeutiger Prozessabschluss aus dem Outcome
```

Wenn `openRun` nicht gelungen ist, darf dieser Ablauf keinen Cleanup- oder Retentionbesitz an einem fremden Pfad annehmen. Es braucht keine öffentlich konfigurierbaren Failure-Injection-Schalter.

### 5.3.1 R3: Vorbereitungsfehler nicht als begonnenen Run behandeln

Am Nachprüfstand fängt `SequentialJobRunner.run` bereits den Fehler aus `WorkspaceManager.openRun`, erzeugt danach trotzdem ein `RunEnvironment` und sendet `run.started`. Ein Test schreibt sogar Job-Cleanup nach gescheiterter Runeröffnung als Soll fest. Dieses Verhalten widerspricht F3-01; es ist nicht die gewünschte Reparatur von Task-Setupfehlern. [N5]

Die minimale Korrektur trennt beide Übergänge:

1. `openRun` muss erfolgreich einen exklusiv erworbenen Workspace liefern, bevor der begonnene Outcome-Pfad betreten wird.
2. Ein gewöhnlicher Fehler **dieses Aufrufs** wird als Startfehler nach aussen gegeben. Falls zur eindeutigen Modulübergabe nötig, genügt ein kleiner Typ im Runnerartefakt, beispielsweise `RunStartException` mit stabiler Kennung `RUN_WORKSPACE_PREPARATION_FAILED` und unveränderter Cause. Kein neuer Typ in der öffentlichen Task-SPI.
3. `JobRuntime` übersetzt genau diesen Fehler in seine sichere Startdiagnose und Code 2. Der bestehende `finally` für schon vorbereitete Bridge-/Logging-/Shutdownressourcen bleibt wirksam. Er ruft keine Job-Cleanup-Bodies auf.
4. Fehler der **späteren** Task-/Invocationvorbereitung bleiben im begonnenen Run und durchlaufen die bereits reparierten F3-Abschlusspfade mit FAILED/10 beziehungsweise normaler Action-Catch-Semantik.

Der Test `SetupFailureTest.failedRunWorkspaceStillRunsNonFileCleanupsAndFinishes` darf nicht als Freigabeorakel erhalten bleiben. Er wird explizit gegen F3-01 abgeglichen: Für den Fehler vor Runbeginn erwartet die direkte Runnerprobe den definierten Startfehlertyp; der gemeinsame Prozesseinstieg erwartet Code 2, kein Runstart und keinen Cleanupmarker. Name und Assertions müssen diese Grenze widerspiegeln.

Der legitime Nachweis „Cleanup ohne Workspace funktioniert“ bleibt erhalten, jedoch mit korrekter Voraussetzung: Der Run wurde erfolgreich eröffnet, danach beschädigt Task A beziehungsweise eine deterministische Testnaht den Workspace. Dann muss nicht dateibezogenes Cleanup nach dem Folgefehler weiterhin laufen. Beide Szenarien sind getrennt zu prüfen; der eine Test darf den anderen nicht ersetzen.

Ein Workspace, dessen exklusive Eröffnung noch nicht gelungen ist, darf niemals durch Retention übernommen werden. Eine etwa notwendige Bereinigung unvollständiger **eigener** Startressourcen bleibt eng auf nachgewiesen selbst erstellte Objekte begrenzt und ist kein Job-Cleanup.

### 5.4 Tests F3

| Test-ID | Szenario und Orakel |
|---|---|
| TC-F3-01 | Task A schreibt einen Marker; Verzeichnisvorbereitung von B scheitert deterministisch. A bleibt SUCCEEDED, B FAILED, C NOT_RUN; Cleanupmarker vorhanden; Runbericht FAILED/10. |
| TC-F3-02 | Schon das erste Taskverzeichnis kann nicht angelegt werden, nachdem der Run begonnen hat. Kein Handler läuft; registrierte externe Cleanupaktion wird dennoch versucht. |
| TC-F3-03 | Exklusive Workspaceeröffnung vor Runbeginn scheitert: vorhandene Leaf als Datei, Verzeichnis mit Marker und Symlink sowie kontrollierter Eröffnungs-I/O-Fehler. Direkter Runner meldet Startfehler, produktiver Einstieg Code 2; weder `run.started`/`run.finished` noch Taskstart oder Job-Cleanup. Keine Löschung/Rechteänderung bestehender Leafs. |
| TC-F3-04 | B-Setupfehler plus mehrere Cleanups, von denen eines scheitert. Alle noch kooperativ ausführbaren Aktionen genau einmal in umgekehrter Reihenfolge; B-Ursache bleibt primär. |
| TC-F3-05 | Workspace während Run verloren/unbenutzbar; eine Cleanupaktion benötigt nur Logger/externe Datei, eine andere `workPath`. Erstere funktioniert, letztere wird mit konkretem I/O-Fehler dokumentiert; weitere Cleanupaktionen werden versucht. |
| TC-F3-06 | B-Setupfehler plus Retentionfehler. Kein Überschreiben der Primärursache; Eigentumsgrenze erhalten; kein automatischer Retry. |
| TC-F3-07 | B-Setupfehler bei unabhängig nutzbarem Reportroot. Bericht wird tatsächlich geschrieben und mit einem unabhängigen JSON-Leser geprüft; Cause und alle ausgewählten Taskzustände vorhanden. |
| TC-F3-08 | B-Setupfehler und anschliessend defekter Reportpfad. Nichtnull-Exitcode, sichere ursprüngliche Fehlerdiagnose und Berichtfehlermeldung, kein behaupteter Reporterfolg und keine Endlosschleife. |
| TC-F3-09 | Verzeichnisvorbereitung einer inneren Invocation scheitert. Umgebende Action sieht die reale Exception; bei unbehandeltem Fehler fail-fast, bei bewusstem Catch normale Catch-Semantik ohne versteckte Fehlerpflicht. |
| TC-F3-10 | F1/F2/F3-Prozessfälle erzeugen pro begonnenem Run genau einen Runabschluss. Keine terminalen doppelten Taskereignisse und keine RUNNING-Zustände im abgeschlossenen Report. |
| TC-F3-11 | Kontext-/Loggerabschluss nach Erfolg, Failure, Timeout und Setupfehler. Spätere Zugriffe dürfen nicht unter einem neuen Run-/Taskkontext erscheinen; Hooks/Bridges werden entfernt. |
| TC-F3-12 | Beide Prozesseinstiege: A erfolgreich, Taskworkspace B blockiert. Reales JSONL besitzt am fehlerhaften Task `phase = "task-setup"`; Taskeintrag im tatsächlichen Report enthält `failurePhase = "task-setup"`, ursprüngliche Cause und FAILED/10. Keine alleinige Stack-/Textsuche als Nachweis. |

Für TC-F3-01 ist eine Datei an der Stelle des erwarteten nächsten Taskverzeichnisses ein geeigneter deterministischer Dateisystemfehler. Dazu können vorhandene testseitige Kontextinformationen genutzt werden. Eine rein interne package-private Testnaht ist alternativ erlaubt. Rootabhängige `chmod`-Effekte und zufällige „Platte voll“-Experimente sind keine belastbaren Standardtests.

## 6. F4: Korrekte Korrelation von Bibliothekslogs

### 6.1 Befund und Ziel

`LogbackBridge.LibraryAppender.append()` übernimmt Level, Nachricht, Logger und Fehlergraph. Die folgende `LoggingPipeline.libraryEvent()` setzt jedoch Task-, Invocation- und Operation-ID leer. Damit sind unter anderem Bibliotheksmeldungen innerhalb mehrerer synchroner Operationen nicht ihrem eigentlichen Aufruf zugeordnet. [S5, S6]

Die Korrektur ergänzt vorhandene Identitäten; sie baut weder einen Tracer noch einen allgemeinen Thread-/Executorwrapper. Der aktuelle sequentielle Ausführungsweg liefert bereits die erforderlichen klaren Scopegrenzen.

### 6.2 Verbindliche Anforderungen

**F4-01 — Identität wird am Ursprung gebunden.**

Eine Bibliotheksmeldung, die synchron innerhalb einer Taskaction entsteht, erhält mindestens deren Run-/Task-ID. Eine Meldung innerhalb von Operationskonfiguration, Handler oder erforderlichem Operations-Close erhält zusätzlich Invocation- und Operation-ID. Es werden die tatsächlichen IDs des bestehenden Ausführungsmodells verwendet, keine parallel erfundenen Korrelationsnummern.

| Ursprung der Meldung | Run | Task | Invocation | Operation |
|---|---|---|---|---|
| Allgemeine Bibliotheksmeldung während eines Runs ohne aktiven Arbeitsscope | ja | leer | leer | leer |
| Actionbody vor/nach innerem Fachaufruf | ja | aktive Action | leer | leer |
| Predicate eines Tasks | ja | aktiver Task | leer | leer |
| Operationskonfiguration und Handler | ja | zugehöriger Task | aktiver Aufruf | aktiver Operationtyp |
| Ressourcen-Close der Operation | ja | zugehöriger Task | derselbe Aufruf | derselbe Operationtyp |
| Job-Cleanup | ja | leer | leer | leer |

Ein vorhandener Cleanupname kann als freigegebenes Attribut erhalten bleiben. Er darf nicht als angeblich echter Task oder Invocation ausgegeben werden. Der Eventname `gretl.library.log` und die bestehenden JSON-Felder bleiben erhalten.

**F4-02 — Echte verschachtelte Scopes, keine globale „aktuelle Aufgabe“.**

Scopes werden vor dem jeweiligen Codebereich geöffnet und in `finally` beziehungsweise `try-with-resources` zuverlässig geschlossen. Ein innerer Aufruf setzt vorübergehend seine Invocation-ID; nach seinem Ende gilt wieder der Taskscope. Nach Taskende existiert kein Taskscope mehr.

Kein statisches veränderliches `currentTask`-/`lastInvocation`-Feld und kein Nachschlagen des zuletzt gestarteten Tasks beim Formatieren. Dieselbe Loggerinstanz darf in verschiedenen Tasks verwendet werden und muss dennoch die aktuelle, korrekt gebundene Identität liefern.

**F4-03 — Runner bleibt backendfrei.**

Die öffentliche Task-SPI wird nicht geändert. Im Runner genügt ein kleiner interner Scopehook; die eigentliche MDC-Bindung lebt im Loggingmodul. Der bestehende `RunEventSink` muss weiterhin als Lambda mit genau einer abstrakten `emit`-Methode verwendbar sein.

Die folgende Ergänzung ist der bevorzugte minimale Entwurf:

```java
package ch.so.agi.gretl.runtime.runner;

import ch.so.agi.gretl.runtime.api.ExecutionIdentity;
import java.util.Optional;

@FunctionalInterface
public interface RunEventSink {
    void emit(RunEvent event);

    default Scope openScope(
            ExecutionIdentity identity,
            Optional<String> operationId) {
        return Scope.NO_OP;
    }

    @FunctionalInterface
    interface Scope extends AutoCloseable {
        Scope NO_OP = () -> { };
        @Override void close();
    }

    RunEventSink NO_OP = event -> { };
}
```

Das ist eine interne Infrastrukturgrenze im vorhandenen Runnerartefakt, keine neue Custom-Task-SPI. Eine gleich kleine, begründete Alternative ist zulässig, darf aber keine Modulrichtung umkehren und keine zweite Eventpipeline erzeugen.

**F4-04 — MDC wird eng und reversibel benutzt.**

Der Loggingadapter darf eine kleine reservierte Schlüsselmenge verwenden, beispielsweise `gretl.run_id`, `gretl.job_name`, `gretl.task_id`, `gretl.invocation_id`, `gretl.operation_id`.

Beim Eintritt werden die bisherigen Werte **dieser Schlüssel** gesichert. Nicht belegte tiefere IDs werden explizit entfernt; ein Taskscope darf nicht die Invocation-ID eines vorigen Aufrufs behalten. Beim Austritt werden nur die eigenen Schlüssel auf ihre vorherigen Werte zurückgesetzt. Fremde MDC-Felder werden weder exportiert noch pauschal gelöscht. Ein alleiniger `MDC.clear()`-Aufruf oder `putCloseable` ohne Wiederherstellung überschreibener Werte genügt für Verschachtelung nicht. [S9, S10]

Die Scopes sind threadgebunden. Doppelte Schliessung soll folgenlos sein; eine erkennbare falsche Threadnutzung wird intern diagnostiziert und darf insbesondere nicht den Kontext eines fremden Threads „bereinigen“. Der produktive sequentielle Weg schliesst jeweils auf dem Eigentümerthread.

**F4-05 — Der Event-Snapshot ist massgebend.**

`LibraryAppender.append(ILoggingEvent)` liest die dem Event zugehörige MDC-Map und erzeugt daraus einen unveränderlichen, validierten Identitätssnapshot. Die Pipeline übernimmt diesen Snapshot statt beim späteren Schreiben `MDC.get(...)` auf dem Konsumenten-/Ausgabethread aufzurufen. Logback stellt dafür `getMDCPropertyMap()` bereit. [S11]

Im derzeit synchronen Bridgepfad wird der Snapshot noch innerhalb des Ursprungsscopes erstellt. Wird ein Event in einem Test verzögert weitergereicht, muss es im Ursprungsscope für deferred processing vorbereitet beziehungsweise vollständig materialisiert worden sein. Ein erst nach Scopewechsel erzeugter Snapshot wäre kein korrekter Test.

Die Run-ID des Snapshots muss zur Pipeline passen. Bei leerem, beschädigtem oder fremdem Run-Snapshot wird keine falsche Task-/Invocationzuordnung erfunden. Zulässig ist dann eine klar unkorrelierte Runmeldung mit leerem Arbeitsscope; widersprüchliche Fremd-IDs werden nicht durchgereicht. Keine unbeschränkte Übernahme sämtlicher MDC-Werte in JSON oder Report.

**F4-06 — Grenzen bei Threads und späteren Meldungen ehrlich halten.**

Ein Kind-/Poolthread bekommt nicht automatisch einen verlässlichen GRETL-Arbeitsscope. Ohne ausdrücklich übertragenen Ursprungskontext bleibt seine Meldung ohne Task-/Invocation-ID. Insbesondere darf eine verspätete Meldung während Task B nicht anhand eines globalen aktiven Tasks B zugeordnet werden. Die Logback-MDC-Dokumentation weist auf die threadlokale Natur und fehlende automatische Übernahme in Kindthreads hin. [S9]

Ein nachweislich bereits im Scope von A eingefrorenes Event darf später weiterhin A zugeordnet werden. Das ist etwas anderes als nachträgliches Erraten. Dieser Auftrag führt weder automatische Executorinstrumentierung noch `InheritableThreadLocal` als scheinbare Universallösung ein.

**F4-07 — Fehler und Datenschutz bleiben erhalten.**

Causes, Suppressed Exceptions, Begrenzungen, Redaction und die bestehenden Levelregeln bleiben erhalten. Das MDC enthält keine Requests, Secrets, JDBC-URLs oder vollständigen Parameterbestände.

Ein fehlgeschlagener innerer Aufruf darf weiterhin durch eine Action behandelt werden. Sein Bibliothekslog ist dann korrekt diesem inneren Aufruf zugeordnet, ohne allein dadurch den Action-/Runstatus zu ändern.

Der Scopehook ist eine Loggingfunktion, kein fachliches `ResourceScope`-Close. Ein gewöhnlicher Fehler beim Öffnen/Schliessen eines Logging-Scopes darf nicht den Fachfehler ersetzen. Die Implementierung muss teilweise gesetzte eigene Werte bestmöglich zurücksetzen, Beobachtbarkeit als beeinträchtigt kenntlich machen und unzuverlässige IDs nicht weiterverwenden. Kein rekursives Loggen einer Loggingstörung über denselben defekten Weg.

**F4-08 — JUL erhält denselben nachweisbaren Kontext.**

Die bestehende JUL→SLF4J→Logback-Brücke bleibt bestehen. Eine synchron über `java.util.logging` erzeugte Meldung innerhalb des Arbeitsscopes muss denselben Identitätsnachweis haben wie eine SLF4J-Meldung. Asynchron fremd weitergereichte JUL-Records bekommen ohne ausdrücklichen Snapshot keine nachträglich erratene Ursprungstask-ID.

Es gibt weiterhin genau einen qualifizierten SLF4J-Provider und keine Bridgeschleife. Native Ausgaben auf beliebigem `System.out`/`System.err` sind durch diese Korrektur nicht automatisch strukturiert.

### 6.3 Klassen- und Methodenauftrag

| Stelle | Auftrag |
|---|---|
| `RunEventSink` | Optionalen backendfreien Scopehook mit No-op-Default ergänzen; Lambda-Kompatibilität beibehalten. |
| `RunEnvironment` | Kleine sichere Delegation zum Scopehook anbieten. Gewöhnliche Loggingstörung nicht mit Fachfehler vermischen. |
| `SequentialJobRunner` | Run-/Taskscope um Predicate, Action und notwendiges Close binden; Job-Cleanup nicht unter dem zuletzt aktiven Task ausführen. |
| `OperationInvoker.invoke` | Invocationscope vor Preparation öffnen, bis nach erforderlicher Freigabe erhalten und danach den Taskscope wiederherstellen. Factory-/Konfigurationsfehler ebenfalls zuordenbar halten. |
| `LoggingPipeline.openScope` | Einen kleinen internen `MdcExecutionScope` erzeugen, keine globale Taskregistry. |
| `JobRuntime.run` / weiterleitende `RunEventSink`-Adapter | Neben `emit` auch `openScope` delegieren. Das Zurückhalten des finalen Runereignisses darf den Scopehook nicht durch einen Lambda-No-op verlieren lassen. |
| `LogbackBridge.LibraryAppender.append` | Identitäten aus dem Eventmaterial erfassen und validieren; Snapshot, Logzeit, Level, Logger und Fehlergraph an Pipeline geben. |
| `LoggingPipeline.libraryEvent` | Überladung mit Identitätssnapshot und ursprünglichem Eventzeitpunkt; bestehender aufruferloser Pfad bleibt bewusst run-only. |
| `EventJson` / Formatierer | Bereits existierende ID-Felder befüllen; keine zweite benutzerseitige JSON-Struktur einführen. |

Für den Snapshot genügt intern ein kleiner Record aus `ExecutionIdentity` und optionaler Operation-ID. Der Quellzeitpunkt sollte aus `ILoggingEvent` stammen; die Pipeline darf bei der Weiterverarbeitung nicht unbegründet einen späteren Zeitpunkt als Entstehungszeit ausgeben. Es ist keine neue Queue oder ein AsyncAppender einzuführen.

Die neue Logscopeschliessung bleibt von erforderlicher Fachressourcenfreigabe getrennt. Andernfalls würde ein MDC-Problem über den Task-`CloseStack` wieder einen fachlichen Fehler erzeugen, was F4-07 widerspräche.

**Integrationshinweis nach Phase A:** Der dokumentierte `JobRuntime`-Stand umhüllt die Loggingpipeline mit einem `RunEventSink`-Lambda, um das eine Run-Terminalereignis erst nach dem erforderlichen Report auszugeben. Ein Lambda, das nur `emit` weitergibt, würde den neuen Default-`openScope` auf No-op belassen. Bei Einführung von F4 ist dieser Adapter deshalb ausdrücklich mitzunehmen: ein kleiner delegierender Adapter mit beiden Methoden genügt. Terminalhoheit, Zeitpunkt und Einmaligkeit bleiben erhalten. Das ist eine Integrationsanforderung, kein im Nachprüfstand bereits ausgeführter neuer Fehlernachweis.

### 6.4 Tests F4

| Test-ID | Szenario und Orakel |
|---|---|
| TC-F4-01 | Zwei normale Tasks nutzen denselben SLF4J-Logger. Beide Meldungen tragen unterschiedliche korrekte Task-IDs; Run-ID ist gleich. |
| TC-F4-02 | Zwei innere Operationsaufrufe einer Action. Jede Meldung trägt ihre eigene Invocation-/Operation-ID; die umgebende Action-ID bleibt identisch. |
| TC-F4-03 | Meldungen vor dem inneren Aufruf, währenddessen und danach. Aussen nur Taskscope, innen Invocation, danach wieder nur Taskscope. |
| TC-F4-04 | Librarylog während Konfiguration, Handler und Ressourcen-Close. Alle besitzen den richtigen Invocationkontext; Konfigurationsfehler werden nicht kontextlos. |
| TC-F4-05 | Exception im inneren Handler, von der Action gefangen. Scope wird wiederhergestellt; Action darf erfolgreich enden. |
| TC-F4-06 | Unbehandelter Fehler beziehungsweise Timeout. Job-Cleanup und nachfolgender separater Run erben keine alte Task-/Invocation-ID. |
| TC-F4-07 | Fremder MDC-Schlüssel vor und während des Scopes. Er bleibt für seinen Eigentümer erhalten, wird aber nicht ungeprüft exportiert; reservierte GRETL-Werte werden korrekt restauriert. |
| TC-F4-08 | Event im A-Scope materialisieren, nach Wechsel zu B ausgeben. Es bleibt A zugeordnet; Verarbeitung darf nicht den aktuellen MDC von B lesen. |
| TC-F4-09 | Kind-/Poolthread ohne ausdrücklich übertragenen Scope loggt verspätet, während B aktiv ist. Keine Zuordnung zu B und keine erfundene Invocation. Mit Latches/Barriers statt zufälligem Sleep koordinieren. |
| TC-F4-10 | Dasselbe Kontrollszenario über JUL. Exakte Identitäten und genau eine Meldung, keine Bridgeschleife und kein zweiter Provider. |
| TC-F4-11 | Throwable mit Cause und Suppressed, die einen registrierten Dummy-Secretwert enthalten. Struktur erhalten; Secret in Text und JSONL maskiert. |
| TC-F4-12 | Leere/fehlerhafte/fremde Run-ID im Event-Snapshot. Keine fremde Taskzuordnung, kein Formatierungsabbruch des Jobs; definierter run-only Fallback. |
| TC-F4-13 | No-op-Sink und absichtlich störender Scopehook. Runner bleibt backendfrei; kontrollierte Loggingdegradation verändert keinen sonst erfolgreichen Fachstatus. Kein Scopeübertrag nach dem Fehler. |
| TC-F4-14 | Über den realen gemeinsamen `JobRuntime`-Einstieg inklusive seines Terminalevent-Filters ausführen, als Script und als reguläres Job-JAR. SLF4J/JUL behalten Task-/Invocation-IDs; genau ein finales Runereignis nach Reportabschluss. Eine direkte Pipeline-Unitprobe ersetzt diesen Weiterleitungsnachweis nicht. |

Tests müssen passende Bibliothekslevel ausdrücklich aktivieren oder ausreichend hohe Testlevel verwenden. Eine wegen des Rootlevels gar nicht erzeugte Testmeldung ist kein Beleg für korrekte Korrelation. Für die strukturierten Assertions werden die wirklichen JSONL-Zeilen geparst, nicht lediglich `String.contains("task")` geprüft.

## 7. Gemeinsamer Test- und Evidenzaufbau

### 7.1 Bestehende Infrastruktur nutzen

Die vorhandenen Runner-, Logging-, Job-Runtime- und Artefakttests sind die Basis. Neue Tests gehören in das zuständige Modul beziehungsweise in die vorhandene prozessbasierte Jobtestsuite. Es entsteht keine zweite Runtime in Testcode.

Ein kleiner Testprovider darf eine Dummyoperation anbieten, die gezielt loggt, einen Marker schreibt, Ressourcen registriert oder eine vorgegebene Exception auslöst. Er wird als echtes Erweiterungs-JAR gebaut und für den Pakettest regulär aufgenommen. Er gehört nicht in den Standardtaskkatalog. Reale Dateisystem-/Prozesswirkungen werden ausserhalb des Jobprozesses geprüft.

Deterministische Failpoints bleiben testseitig oder package-private injizierbar. Keine produktiven Flags wie `--fail-workspace`, keine Abhängigkeit der Runtime auf JUnit und keine Nutzung produktiver Systeme.

### 7.2 Zusätzliche übergreifende Prüffälle

| Test-ID | Umfang und Freigabebedingung |
|---|---|
| TC-G-01 | POSIX-Prozesstest F1 über Scriptlauncher und reguläres Job-JAR. Dieselben bestehenden Elternrechte und privaten neuen Dateien in beiden Modi. |
| TC-G-02 | Lokaler Tasktimeout über beide Einstiegspfade. Nachfolgermarker fehlt, Cleanupmarker vorhanden, Report CANCELLED/20 mit `cancelReason = "TIMEOUT"`; korrespondierende Terminalereignisse tragen `cancel_reason = "TIMEOUT"`. Keine millisekundengenaue Timingassertion. |
| TC-G-03 | Deterministischer Task-Setupfehler über beide Einstiegspfade. Vorheriger Effekt bleibt, nachfolgender Effekt fehlt, Cleanup läuft, Report beschreibt FAILED/10 und `failurePhase = "task-setup"`. Zusätzlich den vorgelagerten Startfehler ohne Run-/Cleanup-Ereignisse klar unterscheiden. |
| TC-G-04 | Echter SLF4J-/JUL-loggender Testprovider in einem regulären Auswahlpaket. Task-/Invocationidentitäten und ServiceLoader-Funktion ohne Testclasspath nachgewiesen. |
| TC-G-05 | Bestehender Standard- und vollständiger CI-Prüfpfad inklusive Artefaktaudits. Alle bisherigen 44 Operationen behalten ihren geforderten fachlichen Nachweis. |
| TC-G-06 | Kein neues Backend in `gretl-api`/Runner, keine CLI-/Packagerabhängigkeit in Job-Runtime/Tasks, weiterhin selektive Jobpakete und genau ein Loggingprovider. |

### 7.3 Prozessmatrix

Die Testanwendung läuft in einer frischen JVM. Für jedes Szenario werden Exitcode, stdout/stderr, Ereignisdatei, Runbericht und fachliche Marker getrennt festgehalten.

Ein Job-JAR wird durch den produktiven Packager gebaut und als eigenes `java -jar` ohne Testclasspath gestartet. Dabei sollen die vorhandenen Isolationsbedingungen erhalten bleiben: fremdes CWD, leeres HOME soweit vereinbart, kein Maven im Runtime-PATH und keine Abhängigkeit auf die ursprüngliche Scriptdatei. Paket- und Distributionshashes beziehen sich auf das tatsächlich gestartete Artefakt.

Die IMAGE-Adaptertests bleiben erhalten, behaupten aber keinen ausgeführten Image-/OpenShift-Nachweis. Der Auftrag verlangt auch keine zusätzliche Kubernetesplattform. Die F1-Korrektur muss dennoch mit einem vom Betreiber vorbereiteten gemeinsamen Volume und einer unprivilegierten UID vereinbar bleiben.

### 7.4 Red/Green und Umgebungsfehler

Pro F1–F4 wird mindestens ein enger Fehlernachweis gespeichert:

- **Rot:** Ausgangscode plus Probe; Fehler ist das beanstandete Verhalten und nicht ein Build-/Classpathproblem.
- **Grün:** Kandidat mit derselben fachlichen Assertion.
- **Interpretation:** Warum dieser Test das jeweilige Finding abdeckt.

Bei einem späteren Repositorystand kann die rote Probe entfallen, wenn eine schon vor diesem Auftrag vorhandene Korrektur am konkreten Code samt Test belegt ist. Dann wird das Finding `ALREADY_FIXED` dokumentiert, nicht künstlich neu implementiert. Der zugehörige grüne Test und die unabhängige Prüfung bleiben erforderlich.

Eine fehlende Testvoraussetzung wird `NOT_RUN` beziehungsweise `BLOCKED`. Sie wird nicht in Erfolg umbenannt. Ein vom Test erwarteter Fachfehler gilt nur als bestanden, wenn der Test selbst erfolgreich die konkrete Fehlerursache und die geforderten Nebenwirkungen geprüft hat.

### 7.5 Standardbefehle

Die folgenden Befehle entsprechen dem vorhandenen Prüfpfad. Sie sind am eingefrorenen Kandidaten mit JDK 25 und dem Repositorywrapper auszuführen. Es wird nichts veröffentlicht.

```sh
CANDIDATE_SHA=$(git rev-parse HEAD)
git status --short
java -version
./mvnw --version

./mvnw -B clean verify
python3 scripts/audit-job-evidence.py --source "$CANDIDATE_SHA"

# Standard-Evidenz vor dem folgenden clean ausserhalb der target-Verzeichnisse sichern.
./mvnw -B -Pgeotools-probe,backing-services clean verify
python3 scripts/audit-job-evidence.py --full --source "$CANDIDATE_SHA"
```

Der vollständige Pfad setzt die im Repository dokumentierte Container-/Dienstumgebung voraus. Keine stillen Skips fehlender Backing Services. Wenn ein anderer dokumentierter Wrapperaufruf nötig wird, ist der konkrete Unterschied festzuhalten; keine neuen Profile nur zur Ausblendung bestehender Tests.

Der Maintainer muss weder Snapshot-Deployment noch Release-Publishing für diesen Auftrag autorisieren. Entsprechende CI-Schritte werden nicht lokal nachgeahmt. Bei Ausführung vorhandener CI-Workflows wird das schreibende Publishing getrennt vom lesenden Qualifikationsnachweis behandelt.

### 7.6 Evidenzablage

Die Implementierungs- und Reviewberichte können im Repository unter folgendem neuen Pfad abgelegt werden; historische Berichte des vorherigen improvement-plan werden nicht überschrieben:

```text
 docs/implementation/hardening-f1-f4/
   spec.md
   baseline.md
   status.md
   phase-a-implementation.md
   phase-a-review.md
   phase-b-implementation.md
   phase-b-review.md
   acceptance-matrix.md
```

Ausführliche Logs, Testberichte und Paketartefakte gehören in ein CI-/Evidenzarchiv, nicht als grosse Binärdateien in Git. Relevante Metadaten:

```json
{
  "phase": "A",
  "candidateSha": "<vollstaendiger-gepruefter-commit>",
  "sourceDirty": false,
  "implementerModel": "<tatsaechliches-modell>",
  "reviewerModel": "<anderes-tatsaechliches-modell>",
  "reviewSession": "<separate-sitzungskennung>",
  "verdict": "PENDING",
  "checks": [
    {
      "testId": "TC-F1-01",
      "status": "NOT_RUN",
      "command": "<tatsaechlicher-befehl>",
      "evidence": "<pfad-oder-ci-artefakt>"
    }
  ]
}
```

Dies ist eine Berichtsvorlage, keine bestehende Testevidenz. Echte Modellnamen und Sitzungskennungen werden nur eingetragen, wenn sie bekannt sind. `unknown` ist ehrlicher als eine erfundene Identität, erfüllt aber nicht automatisch den Nachweis eines anderen Modells.

## 8. Umsetzung in zwei Phasen

### 8.1 Phase A — F1, F2 und F3

**Einstieg:** Repositoryzustand und Quellenstand dokumentiert; diese Datei gelesen; bestehende öffentliche API und Modulgrenzen bekannt.

**Wiederaufnahme dieser Revision:** Die vorhandene Phase-A-Implementierung ist Arbeitsgrundlage. Zu Beginn eine Matrix aus allen F1–F3-Anforderungen und den tatsächlich vorhandenen Tests erstellen. Geeignete F1- und aktive Budget-/Cleanupkorrekturen erhalten; bei bereits behobenen Einzelpunkten `ALREADY_FIXED` mit Beleg notieren. Für R1–R3 die dokumentierten Gegenproben am neuen Ausgangsstand prüfen. Ein historischer grüner Build von `bc044302…` ist kein Freigabenachweis der neuen Änderungen.

**Arbeit in dieser Reihenfolge:**

1. Enge rote Reproduktionen für die noch vorhandenen F1–F3-Abweichungen erstellen, insbesondere gespeicherte Handles, fehlende Cancel-Ursache und falsche Runstartgrenze. Bestehende passende Tests erweitern, nicht ersetzen. Für vorhandene Tests mit widersprüchlichem Orakel Abschnitt 0.2 und 5.3.1 anwenden.
2. Besitz-/Rechtegrenze nach F1 korrigieren. Die exklusive Workspace-Leaf ist die Grundlage der weiteren Fehlerbehandlung.
3. Gespeicherte Handle-Lebensdauer, reine Beobachtung und bindende Checkpoints nach F2 vereinheitlichen. Cause-Snapshots bis zum Report durchreichen. Präzedenztests mit FakeClock erhalten; keine erneute grundlegende Runnerneukonstruktion.
4. Startgrenze vor exklusiver Workspaceeröffnung richtigstellen; die bereits reparierten Tasksetup-/Cleanup-/Berichtpfade nach Runbeginn erhalten. Strukturierte Setup-Phase nachweisen.
5. Script- und JAR-Prozessregressionen TC-G-01 bis TC-G-03 durchführen.
6. Standardbuild und Standardartefaktaudit ausführen. Ergebnisse und Änderungen an Dokumentverträgen festhalten.
7. Kandidatenstand einfrieren und an den unabhängigen Reviewer übergeben.

**Gate A:**

- Alle anwendbaren TC-F1-, TC-F2- und TC-F3-Fälle bestehen, einschliesslich TC-F2-15 bis TC-F2-20 und TC-F3-12.
- Die Restbefunde R1–R3 sind einzeln geschlossen; ein alter Token/Timeout kann weder B abbrechen noch frühere Erfolgsoutcomes nachträglich verändern.
- Ein fehlgeschlagener Workspace-Start und ein Task-Setupfehler nach Runbeginn sind durch unterschiedliche echte Orakel belegt.
- Verpflichtende POSIX-Prüfungen und beide Prozesseinstiege sind nachgewiesen.
- Standardprüfung, Standardartefaktaudit und Modulgrenzen sind grün.
- Der Reviewer hat selbst zusätzliche Gegenproben für alle drei Findings eingebracht oder gleichwertige bestehende Nachweise konkret unabhängig überprüft.
- Es liegt ein unabhängiges `PASS` ohne offene Pflichtabweichung vor.

F4 darf im Bericht von Phase A ausdrücklich als noch offene, für Phase B vorgesehene Arbeit stehen. Das gilt nicht als Verzicht darauf. Eine unerwartete, ausserhalb des Umfangs liegende Beobachtung wird separat vermerkt; sie wird nicht ohne Entscheidung in diesen Auftrag hineingezogen.

### 8.2 Phase B — F4 und Gesamtfreigabe

**Einstieg:** Gate A ist für den aktuellen Code-/Teststand bestanden. Bei zwischenzeitlich verändertem Phase-A-Code sind die betroffenen Nachweise erneut erforderlich.

**Arbeit:**

1. Rote F4-Probe mit zwei Tasks und einem inneren Aufruf erzeugen.
2. Backendfreien Scopehook und Logback-MDC-Implementierung ergänzen.
3. Alle Scopes über Konfiguration, Handler, Ressourcen-Close, Taskende und Job-Cleanup korrekt binden.
4. Snapshot-/JUL-/Threadgrenzen, Fehler-/Redactionpfade und die Scopeweiterleitung im terminalfilternden `JobRuntime`-Adapter testen.
5. TC-G-04 und TC-G-06 am wirklich paketierten Testprovider prüfen.
6. Alle Phase-A-Tests erneut ausführen; F4 darf insbesondere deren Cleanup-/Fehlerverhalten nicht verändern.
7. Standard- und vollständige Freigabesuite mit ihren Artefaktaudits ausführen; Evidenz vor jedem `clean` sichern.
8. Gesamtmatrix F1–F4 vervollständigen und Kandidat für unabhängiges Review einfrieren.

**Gate B / Abschluss:**

- Alle vier Findings sind korrigiert oder nachvollziehbar schon behoben, mit den geforderten Tests.
- Der vollständige bestehende fachliche Nachweisbestand ist erhalten; keine Operation wurde zur Vereinfachung entfernt.
- Kein neues öffentliches Taskinterface, kein Loggingbackend im Runner und keine zweite Script-/JAR-Ausführungslogik.
- Der unabhängige Reviewer gibt für den genauen abschliessenden Kandidaten `PASS`.
- Der Maintainer kann anhand kurzer Berichte, Testkommandos und Artefakthashes erkennen, was tatsächlich geprüft wurde.

**Stoppregel:** Bei `FAIL` werden nur die betroffenen Korrekturen und Tests nachgearbeitet und erneut geprüft. Bei `BLOCKED` wird die fehlende Voraussetzung benannt. Beide Zustände verbieten die Freigabe; ein Agent darf sie nicht durch einen eigenen Reviewtext überschreiben.

## 9. Unabhängige Gegenprüfung durch ein anderes LLM

### 9.1 Was unabhängig bedeutet

Der Reviewer arbeitet in einer neuen Sitzung mit einem **anderen Modell als der Implementierer**. Eine zweite Rolle im gleichen Chat oder die Aussage „ich habe mich selbst kritisch geprüft“ genügt nicht. Ein gleiches Modell in einem zweiten Agenten ist zwar eine zusätzliche Prüfung, ersetzt hier aber nicht den ausdrücklich geforderten anderen Modelllauf.

Der Reviewer bekommt Spezifikation, Ausgangs-SHA, Kandidaten-SHA, Buildvoraussetzungen und Zugriff auf das Repository sowie die Roh-Evidenz. Er übernimmt weder Analyse noch `PASS`-Behauptungen des Implementierers als Fakt. Er liest den relevanten Code und Diff selbst.

Eine isolierte saubere Worktree ist vorzuziehen. Neue Gegenproben darf der Reviewer dort hinzufügen und ausführen. Produktivcode repariert er im Review nicht selbst; sonst wäre die Freigabe seiner eigenen Reparatur wieder eine Selbstprüfung. Er beschreibt den erforderlichen Fix, der Implementierer setzt ihn um, anschliessend erfolgt die erneute Gegenprüfung.

Wenn die aktuelle Agentenumgebung kein anderes Modell ausführen kann, erstellt sie ein vollständiges Handoff mit Reviewerprompt und markiert das Gate als `BLOCKED_REVIEW`. Sie behauptet keine ausgeführte Gegenprüfung und beginnt nicht die nächste Phase. Der Maintainer kann den Reviewerauftrag in einer anderen LLM-Umgebung starten.

### 9.2 Pflichtumfang des Reviewers

Der Reviewer MUSS:

1. Spezifikationsversion, Ausgangs-/Kandidaten-SHA und sauberen beziehungsweise genau dokumentierten Sourcezustand prüfen.
2. Die Implementierung aller im jeweiligen Gate betroffenen Anforderungen direkt am Code nachvollziehen.
3. Die entscheidenden Tests selbst ausführen. Ein kurzer `mvn test` ohne produktive Artefaktstarts genügt nicht für die Prozessanforderungen.
4. Mindestens einen adversarialen Randfall pro betroffenem Finding unabhängig untersuchen. Für Gate A gehören die gespeicherten Token-/Budgethandles nach Scopeende, echte Cancel-Cause-Ausgaben sowie die exklusive Leaf-Kollision vor Runbeginn ausdrücklich dazu. Für Gate B zusätzlich verschachtelte Scopes, verspätete Bibliotheksereignisse und der reale weiterleitende JobRuntime-Sink.
5. Testqualität prüfen: echte Wirkung statt Mock-Erfolg, richtige Cause statt beliebigem nonzero Exitcode, echte JSON-Feldprüfung statt Textsuche.
6. Öffentliche API, Modulgraph, Taskkatalog, Paketidentitäten und Job-JAR-Isolation auf unbeabsichtigte Änderungen prüfen.
7. Prüfen, dass Quellen nicht nach dem Test unbemerkt verändert und Artefakte nicht nachträglich mit übersprungenen Tests neu gebaut wurden.
8. Eindeutig `PASS`, `FAIL` oder `BLOCKED` erklären und alle offenen Punkte mit Pfad, Methode, Wirkung und nötigem Nachweis aufführen.

Für Gate B prüft der Reviewer zusätzlich den vollständigen Freigabebuild und Artefaktaudit am exakten Kandidaten. Muss dieser lange Nachweis in CI laufen, ist ein von ihm veranlasster beziehungsweise gezielt verifizierter Lauf zulässig. Er prüft Run-SHA, Befehle, Exitcodes, vollständige Testberichte, eventuelle Skips und Artefakthashes. Ein allgemeines grünes GitHub-Badge ist kein Ersatz. Die engen Gegenproben führt er dennoch selbst in seiner Reviewumgebung aus.

### 9.3 Berichtsvorlage

```markdown
# Gegenprüfung Phase A/B

## Identität und Prüfgegenstand
- Spezifikationsversion und SHA-256 der tatsächlich verwendeten `spec.md`:
- Baseline-SHA und dokumentierter Nachprüfstand:
- Kandidaten-SHA:
- Sauberer Sourcezustand / dokumentierter Diff:
- Implementierermodell:
- Reviewermodell und separate Sitzung:
- JDK, Maven, Betriebssystem und Dateisystem:

## Urteil
PASS | FAIL | BLOCKED

## Selbst ausgeführte Prüfungen
| Test / Befehl | Ergebnis | Evidenz |
|---|---|---|

## Eigene Gegenproben
Szenario, unabhängiges Orakel, tatsächlich beobachtetes Ergebnis.

## Findings
| ID | Schwere | Datei/Methode | Reproduktion/Wirkung | Erforderliche Korrektur |
|---|---|---|---|---|

## Abgleich mit Anforderungen
Jede im Gate enthaltene F1-/F2-/F3-/F4-Anforderung und jeder Pflichtnachweis
mit Status, Codebeleg und Testbeleg. Nicht ausgeführte Prüfungen benennen.

## Grenzen
Was wurde nicht geprüft? Warum verhindert dies das Gate oder ist ausserhalb
seines ausdrücklich festgelegten Umfangs?
```

Ein `PASS` mit einem noch offenen Pflichtfehler ist unzulässig. Hinweise ausserhalb dieses Auftrags dürfen als nichtblockierende Folgebeobachtungen dokumentiert werden, sofern sie kein reales Scheitern einer hier zugesagten Eigenschaft darstellen.

### 9.4 Bindung an den geprüften Stand

Die Freigabe gilt für den geprüften Produkt-, Test-, Build- und Vertragsstand. Nachträgliche Änderungen daran verlangen einen erneuten passenden Test-/Reviewlauf. Ein separater Commit, der ausschliesslich den bereits erstellten Reviewbericht oder Evidenzindex hinzufügt, darf den Review auf den unmittelbar vorherigen geprüften Codecommit beziehen; dieser Unterschied wird ausdrücklich ausgewiesen.

Modellname, Sitzung, Commit und Testevidenz sind getrennte Angaben. Die Formulierung „unabhängig geprüft“ ohne diese Herkunftsangaben genügt für den Abschluss nicht.

## 10. Startprompt für den implementierenden Coding-Agent

Den folgenden Prompt nach Ablage dieser Datei als `docs/implementation/hardening-f1-f4/spec.md` verwenden:

```text
Du arbeitest im Repository edigonzales/gretl-runtime.

Lies zuerst AGENTS.md, die Repository-Spezifikation und vollständig
 docs/implementation/hardening-f1-f4/spec.md.

Implementiere ausschliesslich die dort beschriebenen Korrekturen F1 bis F4.
Es ist kein neuer Architekturentwurf und kein Auftrag für weitere Tasks,
Publisher, Native Image, Parallelisierung oder einen neuen Loggingstack.
Erhalte bestehende Nutzeränderungen, öffentliche Task-API, Modulgrenzen,
Taskkatalog und fachliche Artefakttests.

Beginne mit der Vervollständigung von Phase A (F1–F3). Massgeblich ist diese
vollständige spec.md Version 1.1, nicht allein der alte Startprompt.
Prüfe HEAD gegen Analysebaseline und Nachprüfstand bc044302829ede67ead27038bfdf2be90d0b0063.
Erhalte die bereits geeigneten Korrekturen. Erstelle eine Anforderungs-/Testmatrix.
Schliesse insbesondere R1 (gespeicherte Tokens/Budgets nach Scopeende),
R2 (konkrete cancelReason im Report und cancel_reason im Event) und
R3 (Startfehler vor eigener Run-Leaf versus Setupfehler nach Runbeginn).
Führe die engen roten Nachweise für noch vorhandene Fehler aus, ergänze
Regressionen und prüfe Script- und Job-JAR-Start. Bestehende Tests mit
abweichender Startsemantik sind begründet an F3-01 anzupassen, nicht zur
stillschweigenden Änderung des Sollvertrags zu verwenden.
Nutze FakeClock für enge Timeouttests und deterministische I/O-Fehler.
Keine Root-abhängigen Scheintests, keine produktiven Failure-Injection-Flags.

Arbeite bis zu einem getesteten Phase-A-Kandidaten, nicht nur bis zu einem
Plan. Dokumentiere die wirklichen Versionen, Befehle, Testergebnisse,
Artefakte und verbleibenden Grenzen unter
 docs/implementation/hardening-f1-f4/.

Lass danach ein anderes LLM-Modell in einer separaten Sitzung den
Kandidaten unabhängig gemäss Abschnitt 9 prüfen. Gib ihm die Spezifikation,
Baseline, Kandidaten-SHA und Roh-Evidenz. Es soll Code und Tests selbst
prüfen, eigene Gegenproben ausführen und PASS/FAIL/BLOCKED liefern.
Ein separater Reviewer derselben Modellidentität erfüllt den verlangten
anderen Modelllauf nicht. Erfinde weder Modellname noch Reviewbericht.

Falls kein anderes Modell verfügbar ist: Erzeuge das Review-Handoff und
markiere BLOCKED_REVIEW. Beginne Phase B nicht. Erkläre klar, was tatsächlich
implementiert und geprüft wurde und welches Review noch aussteht.

Nach echtem PASS für Phase A führe Phase B (F4) aus. Binde Librarylogs über
einen kleinen backendfreien Scopehook und sichere Ursprungssnapshots;
keine globalen currentTask-Felder und keine neue Task-Logging-API.
Achte darauf, dass der terminalfilternde RunEventSink in JobRuntime auch
openScope weiterleitet; ein emit-only-Lambda darf die Korrelation nicht verlieren.
Führe anschliessend alle vorgeschriebenen Prüfungen, beide Maven-Gates und
beide Artefaktaudits durch. Sichere Evidenz vor clean.

Auch Phase B braucht eine unabhängige Gegenprüfung durch ein anderes
Modell. Nach FAIL repariert der Implementierer und der Reviewer prüft den
neuen Kandidaten erneut. Ein interner Selbstcheck ersetzt kein Gate.

Ändere keine fachlichen Assertions nur für einen grünen Build. Keine
Produktionszugriffe, Secrets, Releases oder Deployments. Kein automatischer
Retry bereits ausgeführter Fachoperationen.

Schliesse mit dem exakten geprüften Stand, Gate-Urteilen, Ergebnissen der
Regressionen und jeder nicht erbrachten Prüfung ab. Beginne jetzt mit der
Repository- und Baselineprüfung und setze danach Phase A um.
```

## 11. Prompt für den unabhängigen LLM-Reviewer

Dieser Auftrag wird in einer separaten Umgebung mit einem anderen Modell gestartet. Phase und Kandidaten-SHA werden vor der Übergabe konkret eingesetzt.

```text
Du bist der unabhängige Reviewer für GRETL Runtime, nicht der Implementierer.
Du prüfst Phase <A oder B> am Kandidaten <vollständige SHA> gegen
Baseline 288137f116f2d861a1648b5eeaf889d463a5ea6c beziehungsweise die
im Baselinebericht nachvollziehbar festgehaltene neuere Ausgangsbasis.

Lies AGENTS.md und vollständig
 docs/implementation/hardening-f1-f4/spec.md (Version 1.1).
Erfasse den Hash der tatsächlich verwendeten Spezifikation. Prüfe auch
die einschlägigen bestehenden API-/Runtime-/Loggingverträge und die
Restbefunde R1–R3 aus der Nachprüfung von bc044302829ede67ead27038bfdf2be90d0b0063.
Die Beschreibung und Testerfolgsmeldungen des Implementierers sind
Hinweise, keine unabhängigen Beweise.

Verifiziere deine vom Implementierer abweichende Modellidentität und
arbeite in einer separaten Sitzung, vorzugsweise sauberer Worktree.
Ist das nicht erfüllt oder ist der Kandidat nicht verfügbar, erkläre
BLOCKED statt eine unabhängige Prüfung zu behaupten.

Lies die betroffenen Klassen und den tatsächlichen Diff. Prüfe besonders:
- F1: keine Rechteänderung bestehender Elternpfade; private neue Leafs;
  Kollision und Symlink erzeugen keinen fremden Retentionbesitz.
- F2: lokales statt nur globales Budget; Konfiguration/Predicate-false/
  Return/Close als Grenzen; Catch hebt Cancellation nicht auf;
  echte Erstfehler bleiben primär, normales Fach-Catch bleibt erlaubt.
  Prüfe bereits ausgegebene Tokens UND Budgets nach Task-/Invocationende;
  reine Abfragen dürfen keine neue globale Cancellation auslösen. Prüfe
  echte cancelReason-/cancel_reason-Felder in Report und JSONL, nicht nur Code 20.
- F3: nach Runbeginn kein gewöhnlicher Setupfehler an Cleanup und Report
  vorbei; Cleanup ohne Workspacenutzung bleibt möglich; keine Doppelung.
  Vor Runbeginn abgelehnte exklusive Leaf dagegen: Code 2, kein run.started,
  kein Job-Cleanup, keine fremde Retention. Eine korrigierte Testbenennung
  allein genügt nicht. Prüfe die strukturierte task-setup-Phase im Artefakt.
- F4 in Phase B: richtige IDs im Event zum Ursprung; Verschachtelung und
  Wiederherstellung; SLF4J und JUL; kein globaler letzter Task, keine
  Falschzuordnung verspäteter Threads, keine verlorenen Causes/Secrets.
  Der gemeinsame Script-/JAR-Einstieg muss den Scopehook tatsächlich weitergeben,
  auch wenn sein Sink das Run-Terminalereignis bis nach dem Report zurückhält.

Führe die relevanten Tests selbst aus. Ergänze mindestens eine gezielte
Gegenprobe pro betroffenem Finding. Eine fachliche Fehlersimulation muss
mit ihrem eigenen Orakel bestehen; ein beliebiger Startfehler genügt nicht.
Kontrolliere tatsächliche Berichte, JSONL, Dateirechte, Marker und Artefakte.
Für Phase B prüfe zusätzlich die vollständige bestehende Freigabesuite
und die Artefaktaudits am genauen SHA; CI-Evidenz muss individuell geprüft
werden, nicht nur anhand eines grünen Badges.

Repariere keinen Produktivcode im Review. Dokumentiere Findings mit
Datei/Methode, Reproduktion, erwarteter und beobachteter Wirkung. Neue
Testproben darfst du in deiner isolierten Umgebung ergänzen.

Schreibe phase-a-review.md beziehungsweise phase-b-review.md gemäss
Abschnitt 9 mit Modell/Sitzung, SHA, selbst ausgeführten Befehlen und
Roh-Evidenz. Urteil genau PASS, FAIL oder BLOCKED. Keine Pflichtabweichung
und keine fehlende Pflichtprüfung hinter einem PASS verstecken.

Bei FAIL geht der Befund an den Implementierer. Erst einen tatsächlich
korrigierten Kandidaten nachtesten und freigeben; keine Selbstfreigabe
einer von dir vorgenommenen Produktivkorrektur.
```

## 12. Findings-Abgleich und abschliessende Definition of Done

### 12.1 Nachverfolgungsmatrix

Die folgende Matrix ist der Abschlussmassstab. „Offen“ bezeichnet den noch erforderlichen **Freigabenachweis am neuen Kandidaten**, nicht zwingend fehlenden Code. Der dokumentierte Zwischenstand steht in Abschnitt 0.1. Die rechte Spalte wird erst nach tatsächlicher Umsetzung und Gegenprüfung mit den konkreten Belegen befüllt. Diese Dokumentrevision setzt keinen Implementierungsstatus auf grün.

| Finding / Anforderung | Verpflichtender Nachweis | Gate | Status beim Erstellen dieser Spezifikation |
|---|---|---|---|
| F1-01 / F1-02: bestehende Roots unverändert, neue Pfade gezielt anlegen | TC-F1-01/02/03/06, TC-G-01 | A | Offen |
| F1-03 / F1-06: exklusives Eigentum und begrenzte Retention | TC-F1-04/05/10 | A | Offen |
| F1-04: Report-/Eventdateien statt Elternrechte schützen | TC-F1-02/08, TC-G-01 | A | Offen |
| F1-05: Symlink- und Plattformgrenzen | TC-F1-07/09; obligatorisches POSIX-Gate | A | Offen |
| F2-01 / F2-02: geerbtes Budget und wirksamer Token | TC-F2-01/02/05/06/14/15 | A | Offen |
| R1 / F2-02 / F2-08: Lebensdauer gespeicherter Task-/Invocationhandles und reine Beobachtung | TC-F2-15/16/17/19, eigener Gegenbeweis des Reviewers | A | Offen |
| F2-03: vollständige Checkpoints | TC-F2-03/04/10, TC-G-02 | A | Offen |
| F2-04: Cancel bleibt nach Catch bindend | TC-F2-07 | A | Offen |
| F2-05: Erstfehler-/Abbruchpräzedenz | TC-F2-08/09/10/13/20 | A | Offen |
| F2-06: eigenes gemeinsames Cleanupbudget, Interrupt | TC-F2-11/12 | A | Offen |
| R2 / F2-07: konkrete primäre Cancel-Ursache bis in reale Artefakte | TC-F2-18/19/20, TC-G-02 | A | Offen |
| F2-08: monotone reine Zeitberechnung, keine neuen Limits pro Poll | TC-F2-01/06/14/16/17 | A | Offen |
| R3 / F3-01 / F3-02: Startfehler versus Task-Setupfehler; strukturiertes Phasenfeld | TC-F3-01/02/03/09/12, TC-G-03 | A | Offen |
| F3-03 / F3-04: einmaliger Abschluss und unabhängiges Cleanup | TC-F3-04/05/10/11 | A | Offen |
| F3-05 / F3-06: Ursachen, Report, sichere Fehlerausgabe | TC-F3-06/07/08, TC-G-03 | A | Offen |
| F3-07: keine automatische Wiederholung oder Rückabwicklung | TC-F3-01/06, TC-G-03 | A | Offen |
| F4-01 / F4-02: richtige Scopeidentität und Verschachtelung | TC-F4-01/02/03/04/05 | B | Offen |
| F4-03: kleine backendfreie Grenze und Scopeweiterleitung im Produktstart | TC-F4-13/14, TC-G-04/06 | B | Offen |
| F4-04: fremdes MDC erhalten, eigene Werte restaurieren | TC-F4-06/07 | B | Offen |
| F4-05 / F4-06: Ursprungssnapshot und Threadgrenzen | TC-F4-08/09/12 | B | Offen |
| F4-07 / F4-08: Ursachen, Redaction, normale Fehlersemantik und JUL | TC-F4-05/10/11/13, TC-G-04 | B | Offen |
| G-01 bis G-05: vorhandene Architektur und echte Evidenz | Red/Green, TC-G-01 bis TC-G-06, unabhängige Berichte | A/B | Offen |

### 12.2 Freigabekriterien

Der Auftrag ist erst abgeschlossen, wenn:

1. F1–F4 einschliesslich R1–R3 am geprüften Kandidaten nachvollziehbar korrigiert sind und die gesamte obige Matrix Belege enthält;
2. beide Phasen ein echtes unabhängiges `PASS` eines anderen LLM-Modells besitzen;
3. Script- und Job-JAR-Regressionsnachweise vorliegen;
4. der Standard- und vollständige fachliche Build samt Artefaktaudits grün sind;
5. bestehende API-/Modul-/Paketierungsgrenzen und fachliche Tests erhalten sind;
6. diese vollständige `spec.md` unter dem angegebenen Pfad vorhanden ist und die einschlägigen Repositorydokumente aktualisiert sind, ohne die gesamte Spezifikation oder historische Reviewberichte neu zu schreiben;
7. verbleibende Grenzen wie kooperativer Abbruch, nicht-POSIX-Rechte und fehlende automatische Threadkontextübertragung ehrlich dokumentiert sind.

Der Abschlussbericht MUSS für jedes der vier Findings auf konkrete Codeänderungen, Tests und Reviewerbelege verweisen. Eine lange Liste neu erstellter Klassen oder eine hohe Coveragezahl allein ist kein Abschlussnachweis.

### 12.3 Selbstkontrolle dieses Spezifikationsdokuments

Beim Erstellen wird geprüft, ob jedes Finding einen eindeutigen Sollvertrag, betroffene Klassen/Methoden, Fehlergrenzen, Regressionen und ein Review-Gate besitzt. Diese Dokumentprüfung ist von der späteren Implementierungsabnahme und der unabhängigen Gegenprüfung zu unterscheiden.

Folgende besonders fehleranfällige Übergänge sind ausdrücklich enthalten:

- F1 entfernt das pauschale Root-`chmod`, ohne den Schutz neuer Dateien zu verlieren.
- F2 behandelt auch normale Rückkehr, Predicate-false, Konfiguration und Close; es verlangt keinen unmöglichen harten Threadstopp.
- R1 sichert bereits ausgegebene Handles ab; eine Prüfung nur beim Getter reicht nicht. Reine Abfragen mutieren weder Runsteuerung noch Präzedenz.
- R2 sichert die primäre Cancel-Ursache als Snapshot bis in Report und Terminalereignis; frühere Erfolge werden nicht umetikettiert.
- R3 zieht die Grenze vor Runbeginn an der erfolgreichen exklusiven Workspaceeröffnung und wahrt Cleanup nach echten späteren Setupfehlern.
- F3 verlangt Cleanup ohne unnötigen Workspacezwang und behauptet bei ausgefallenem Reportspeicher keinen garantierten Bericht.
- F4 bindet Identität am Eventursprung, erhält fremde MDC-Schlüssel und ordnet unbekannte Threads nicht dem letzten Task zu. Die interne Terminalevent-Weiterleitung muss den Scopehook erhalten.
- Fachlich gefangene Fehler und kontrollierter Cancel bleiben verschiedene Dinge.
- Kein unabhängiges Modell verfügbar bedeutet eine explizite Gateblockade, kein erfundenes Review.

### 12.4 Prüfstand der gelieferten Datei

Diese Revision wurde als Markdown-Dokument geprüft: eindeutige Anforderungs-/Testkennungen, Erhalt aller bisherigen Kennungen, Nachverfolgungsmatrix, vorhandene Hauptabschnitte, geschlossene Codeblöcke, JSON-Beispiele und ausdrücklich ausgeschriebene Referenzen. Die konsolidierte Datei enthält **34 Anforderungen und 62 Prüffälle**. Neu sind TC-F2-16 bis TC-F2-20, TC-F3-12 und TC-F4-14; TC-F2-15 und die betroffenen vorhandenen Orakel sind präzisiert.

Der Dokumentabgleich bestätigt, dass die drei Restbefunde aus dem bereitgestellten Nachprüfbericht in Sollvertrag, Klassen-/Methodenauftrag, Tests, Phasengates und Reviewerprompt eingeordnet sind. F1 bleibt vollständig enthalten, F4 bleibt eine eigene nachgelagerte Pflichtphase. Die grüne historische CI wird weder entwertet noch als Beweis der noch fehlenden Gegenproben verwendet.

Dies ist ausschliesslich eine Konsistenzprüfung der Spezifikation. Für diese Revision wurden keine neuen Produktivkorrekturen vorgenommen, keine Regressionen gegen eine neue Implementierung ausgeführt und kein anderer LLM-Modelllauf durchgeführt. Die noch erforderlichen Freigaben in Abschnitt 12.1 bleiben offen. Die Quellen wurden aus dem bereitgestellten Dokumentbestand übernommen; ihre Commitbindung bezeichnet historische Stände, keine erneute Onlineprüfung.

## 13. Quellen und unveränderliche Referenzen

Die Quellen beschreiben den geprüften Ausgangscode beziehungsweise die verwendeten Plattformmechanismen. Die Sollverträge und Tests dieses Dokuments sind daraus abgeleitete Entwurfsentscheidungen; sie werden nicht als bereits implementierte Eigenschaften dargestellt.

**S1 — Gemeinsamer Job-Einstieg und Verzeichnisvorbereitung**  
[JobRuntime.java, Ausgangscommit](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-job-runtime/src/main/java/ch/so/agi/gretl/job/runtime/JobRuntime.java). Relevante Methoden: `run`, `prepareDirectory`, `setPrivateDirectory`.

**S2 — Sequenzieller Runner und Taskabschluss**  
[SequentialJobRunner.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/SequentialJobRunner.java).

**S3 — Kontext, Cancellation und Zeitbudget**  
[BaseContext.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/BaseContext.java), [RunControl.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/RunControl.java) und [FailureArbiter.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/FailureArbiter.java).

**S4 — Invocationbesitz und Ressourcenabschluss**  
[OperationInvoker.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/OperationInvoker.java) und [WorkspaceManager.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/WorkspaceManager.java).

**S5 — Bibliotheksbridge**  
[LogbackBridge.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-logging/src/main/java/ch/so/agi/gretl/runtime/logging/LogbackBridge.java).

**S6 — Pipeline, Eventformat und Report**  
[LoggingPipeline.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-logging/src/main/java/ch/so/agi/gretl/runtime/logging/LoggingPipeline.java), [EventJson.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-logging/src/main/java/ch/so/agi/gretl/runtime/logging/EventJson.java), [RunReportWriter.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-logging/src/main/java/ch/so/agi/gretl/runtime/logging/RunReportWriter.java) und [EventFileSink.java](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/gretl-logging/src/main/java/ch/so/agi/gretl/runtime/logging/EventFileSink.java).

**S7 — Bestehende Modul-/Test-/CI-Verträge**  
[Modulgrenzen](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/docs/implementation/module-boundaries.md), [Job-Artefakttests](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/docs/implementation/job-artifact-tests.md), [CI-Workflow](https://github.com/edigonzales/gretl-runtime/blob/288137f116f2d861a1648b5eeaf889d463a5ea6c/.github/workflows/publish-snapshots.yml). Die dortigen Publish-Schritte sind keine Aufforderung zum Deployment im Rahmen dieses Auftrags.

**S8 — JDK 25 Dateierzeugung**  
[Java SE 25: Files](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html). Relevant sind `createDirectory`, `createDirectories`, `createTempFile`, `newByteChannel` und die atomaren Erzeugungsattribute für tatsächlich neue Objekte.

**S9 — Threadlokales MDC und Scopelebensdauer**  
[Logback-Handbuch: Mapped Diagnostic Context](https://logback.qos.ch/manual/mdc.html).

**S10 — MDC-Operationen und Wiederherstellung**  
[SLF4J: MDC API](https://www.slf4j.org/apidocs/org/slf4j/MDC.html). `putCloseable` entfernt seinen Schlüssel beim Close; eine vorhandene überschriebene äussere Belegung muss für korrekte Verschachtelung gesondert wiederhergestellt werden.

**S11 — Ereignisgebundene Kontextdaten**  
[Logback: ILoggingEvent](https://logback.qos.ch/apidocs/ch.qos.logback.classic/ch/qos/logback/classic/spi/ILoggingEvent.html). Relevant sind `getMDCPropertyMap`, `getInstant` und `prepareForDeferredProcessing`.


### 13.1 Ergänzende Quellen der Nachprüfung

Diese Referenzen sind der bereitgestellten Nachprüfung entnommen. Die isolierten damaligen Kernproben liefen unter JDK 21; der dort separat geprüfte vollständige Produktnachweis stammt aus Java-25-CI. Die beiden Nachweisarten dürfen im neuen Implementierungsbericht nicht vermischt werden. Die Zusatzdateien können als historische Evidenz beigelegt werden; **zum Verständnis des Sollvertrags ist diese `spec.md` vollständig**.

**N1 — Bereitgestellte Nachprüfung und Probenbestand**  
`gretl-runtime-nachpruefung-bc044302.md` und `gretl-runtime-nachpruefung-bc044302-nachweise.zip`, vom 26. Oktober 2026. R1: veraltete Token-/Budgethandles; R2: verlorene Cancel-Ursache; R3: Runstartgrenze; separate Feststellung, dass F4 noch aussteht. Keine formale Bescheinigung eines anderen Reviewermodells.

**N2 — Dokumentierte Übergabelücke und Phase-A-Arbeit**  
[baseline.md](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/docs/implementation/hardening-f1-f4/baseline.md) und [phase-a-implementation.md](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/docs/implementation/hardening-f1-f4/phase-a-implementation.md).

**N3 — Handle-Lebensdauer und Budgetmutation am Nachprüfstand**  
[BaseContext.java](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/BaseContext.java) und [RunControl.java](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/RunControl.java).

**N4 — Abschlussdaten und Report am Nachprüfstand**  
[TaskCompletion.java](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/TaskCompletion.java) und [RunReportWriter.java](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/gretl-logging/src/main/java/ch/so/agi/gretl/runtime/logging/RunReportWriter.java).

**N5 — Startgrenze, Tasksetup und gemeinsamer Prozesseinstieg am Nachprüfstand**  
[SequentialJobRunner.java](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/SequentialJobRunner.java), [SetupFailureTest.java](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/gretl-runner/src/test/java/ch/so/agi/gretl/runtime/runner/SetupFailureTest.java) und [JobRuntime.java](https://github.com/edigonzales/gretl-runtime/blob/bc044302829ede67ead27038bfdf2be90d0b0063/gretl-job-runtime/src/main/java/ch/so/agi/gretl/job/runtime/JobRuntime.java).

**N6 — Historischer CI-Nachweis, keine Freigabe neuer Änderungen**  
[Workflowlauf 40328837715](https://github.com/edigonzales/gretl-runtime/actions/runs/40328837715) für `bc044302829ede67ead27038bfdf2be90d0b0063`. Der bereitgestellte Nachprüfbericht dokumentiert Standard- und vollständige Qualifikation dieses Stands. Der nächste Kandidat benötigt seine eigenen passenden Nachweise.
