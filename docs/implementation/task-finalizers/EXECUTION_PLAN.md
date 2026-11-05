# GRETL Runtime: taskgebundene Finalizer mit `finalizedBy`

**Verbindliche Spezifikation und phasenweiser Auftrag für Codex**  
**Fassung:** 1.0 · 5. November 2026  
**Auftrag:** `task-finalizers`  
**Repository:** `edigonzales/gretl-runtime`  
**Recherchepin:** `31ca88ef4b4a5a4a219907156886f1da19adc644` auf `main`  
**Arbeitsbranch:** vorzugsweise `codex/task-finalizers`, eigener Worktree  
**Implementierer:** Codex  
**Unabhängiger Reviewer:** OpenCode 2.x / DeepSeek-V4.1-Flash-1017  
**Status:** neuer Implementierungsauftrag. Dieses Dokument ist weder implementierter Produktcode noch eine bereits ausgeführte Produktabnahme.

## 1. Auftrag, Grundlage und Rangfolge

Implementiere `finalizedBy` als **taskgebundene Beziehung zwischen normalen benannten GRETL-Tasks/Actions**. Ein Finalizer besitzt dieselbe Taskidentität, Ausführung, Operationsunterstützung, Ressourcenbehandlung und Berichterstattung wie andere Tasks. Die Finalizer-Beziehung beeinflusst Auswahl, Reihenfolge und Ausführung nach Fehlern. Sie ist weder ein Alias für `cleanup` noch ein versteckter nachträglich ausgeführter Groovy-Callback.

Das bereits vorhandene globale `cleanup` bleibt erhalten. Auch Runtime-Includes, Paketierung, Subprojekte und die inzwischen implementierte Include-Unterstützung im LSP/Modeler bleiben bestehen. Baue auf dem tatsächlichen integrierten Mainstand auf; wiederhole die abgeschlossenen Aufträge `gretl-tooling`, `script-includes` und `include-tooling` nicht.

Die drei aktiven normativen Dateien sind [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md), dieser Plan und [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md). Der Plan bestimmt die fachlichen Anforderungen. [SOURCE_BASELINE.md](SOURCE_BASELINE.md) ist ein informativer Herkunfts-/Codebericht, kein zusätzliches Kriterienregister. Historische Reviews bleiben ihren ursprünglichen SHAs zugeordnet.

**Quellen und neue Entscheidungen auseinanderhalten:** Die Baseline beschreibt gelesenen Code. Die nachfolgenden F-Verträge sind der neu zu implementierende GRETL-Vertrag. Gradle ist die Referenz für das Grundkonzept, nicht die Behauptung vollständiger Gradle-Kompatibilität. Insbesondere `SKIPPED`/`NOT_RUN`, geteilte Finalizer, Ergebnisrechte, Bedarf von Finalizer-Voraussetzungen und Abbruchbudgets werden hier ausdrücklich für GRETL festgelegt.

### 1.1 Harte Arbeitsregeln

Arbeite **P0 → P1 → P2 → P3 → P4 → P5**. Jede Phase braucht erfolgreiche notwendige Tests, einen lokalen Kandidatencommit und eine unabhängige Abnahme genau dieses Commits. Keine folgende Phase vor gültigem Gate. Der Reviewer beurteilt Code **und öffentliche Zielerreichung**, führt selbst relevante Tests aus und entwickelt eine eigene neue Gegenprobe. Codex organisiert den gesamten Reviewtransport und die Nacharbeit autonom.

Der ausdrückliche Nutzerauftrag verlangt risikobasierte Tests, nicht pauschale Volltests nach jeder Phase. Für die lokalen Gates dieses Auftrags gilt deshalb §8 anstelle der historischen pauschalen Rootvollgatepflicht. `AGENTS.md`, CI-, Release- und Publikationsgates selbst werden nicht verändert oder als bestanden bezeichnet. Ein Feature-PASS ist keine Releasefreigabe. Die Testausnahme einmal in P0 dokumentieren, nicht bei jeder Phase erneut diskutieren.

Kein Push, Release oder Merge nach `main` oder in fremde Branches. Keine produktiven GRETL-Jobs, Datenbank-/S3-/FTP-Endpunkte oder Benutzerdateien für Tests. Eigene synthetische Testverzeichnisse verwenden. Keine fremden Worktrees, globalen Providerprofile oder Credentialdateien verändern.

### 1.2 Nicht-Ziele

Keine Parallelisierung, Retry-/Resumeengine, allgemeine Workflowplattform, `shouldRunAfter`, Rollbacktransaktionen, automatische Kompensationslogik, Gradle-Abhängigkeit, neue Jobdefinitionssprache, native-image-Migration oder universelles Reviewframework. Keine `finalizedBy`-Option für innere einzelne Operation-Invocations. Kein allgemeiner Status-/Ergebniszugriff quer durch beliebige Tasks. Keine unbedingte Ausführung bei JVM-Absturz, SIGKILL oder nicht kooperierendem Fremdcode versprechen.

Neue produktive Mavenmodule sind nicht erforderlich: reine logische Regeln gehören in `gretl-language-contract`, ausführende Mechanik in den vorhandenen Runner. Die bereits eingeführte neutrale Script-Syntaxarchitektur nicht zurückbauen.

## 2. Zieloberfläche und Begriffe

### 2.1 Öffentliche Groovy-DSL

```groovy
// Der Finalizer ist ein normaler benannter Task/Action.
action('importData', finalizedBy: 'release') {
    println 'Import wird ausgeführt'
}

action('release') {
    println 'Importbezogene Abschlussarbeit'
}

cleanup('jobEnd') {
    println 'Globale Abschlussarbeit des gesamten Jobs'
}

defaults 'importData'
```

Ebenso unterstützt:

```groovy
action('process', finalizedBy: ['release', 'report']) { }
action('release') { }
action('report', after: 'release') { }
```

Die Liste deklariert mehrere Beziehungen. **Ihre Reihenfolge ist keine zusätzliche Ausführungsabhängigkeit.** Im zweiten Beispiel erzwingt erst `after: 'release'` die Reihenfolge zwischen den beiden Finalizern. Ein Finalizer darf selbst weitere Finalizer besitzen.

Auch typisierte Tasks verwenden denselben Header:

```groovy
task('compress', Gzip, finalizedBy: 'recordCompletion') {
    dataFile = file('data/input.txt')
    gzipFile = workPath('output.txt.gz')
}
action('recordCompletion') { println 'compress ist beendet' }
```

Die konkrete Operationsfixture vor Verwendung gegen den vorhandenen Katalog prüfen. `finalizedBy` gehört in den Task-/Action-Header, nicht in den Konfigurationsbody der Operation.

### 2.2 Drei verschiedene Beziehungen

| Deklaration | Logische Pfeilrichtung | Auswahlwirkung | Ausführungs-/Ergebniswirkung |
|---|---|---|---|
| `B dependsOn A` | `A → B` | Auswahl von B zieht A mit | A vor B; Datenvorgängerrechte nur aus solchen Kanten |
| `B after A` | `A → B` | Keine zusätzliche Auswahl | Reihenfolge, wenn beide ausgewählt; keine Ergebnisrechte |
| `A finalizedBy F` | **`A → F`** | Auswahl von A nimmt F und seine nötigen Voraussetzungen als Kandidaten auf | F wird durch den erreichten A fällig; A darf dafür fehlgeschlagen sein; keine impliziten Ergebnisrechte |

**Wichtige Editregel:** Die `FINALIZED_BY`-Kante zeigt vom auslösenden Task zum Finalizer, aber die Deklaration steht im Header des **Quellknotens A**. Bei `dependsOn`/`after` steht sie im Header des Zielknotens B. Diese Asymmetrie darf weder im Core noch in der GUI verloren gehen.

### 2.3 Begriffe

- **Owner:** Task, dessen `finalizedBy`-Option auf einen Finalizer zeigt; nicht mit dem Include-Source-Owner verwechseln.
- **Kandidat:** statisch ausgewählter Task im erweiterten Plan. Auswahl allein bedeutet noch keine Ausführung.
- **Erreichter Task:** hat den kontrollierten Übergang `PENDING → RUNNING` tatsächlich durchlaufen. Das geschieht vor Setup, `when` und Konfiguration, aber nach der Prüfung, ob dieser Task überhaupt noch gestartet werden darf.
- **Aktivierter Finalizer:** mindestens ein ausgewählter Owner wurde erreicht und ist terminal geworden. Die Aktivierung ist dauerhaft für diesen Run.
- **Finalizer-Voraussetzung:** ein normaler Task, der über `dependsOn` zur Ausführung eines aktivierten Finalizers benötigt wird.
- **Drain:** nach Stop der normalen Arbeit dürfen nur aktivierte Finalizer und die dafür notwendigen, noch nicht erledigten Voraussetzungen weiterlaufen. Keine Wiederaufnahme normaler unabhängiger Tasks.
- **Erfolgsplan:** deterministische Reihenfolge unter der Annahme, dass alle gestarteten Tasks erfolgreich terminal werden. Er ist keine Garantie des tatsächlichen Ablaufs bei Fehlern oder Abbruch.

## 3. Verbindlicher Funktionsvertrag F01–F30

### F01 – DSL und Java-Registrierung

`finalizedBy` ist für `task(...)` und `action(...)` zulässig. Unterstütze dieselben vorhandenen Linkformen wie bei `dependsOn`/`after`: Taskname, gültige TaskRef sowie die entsprechenden flachen Mehrfachformen. Bestehende Null-/Leerlistenbehandlung der Linknormalisierung übernehmen und testen; eine leere Beziehung erzeugt keine Wirkung. Keine neuen inkonsistenten dynamischen DSL-Sonderformen erfinden.

Die Java-Registrierung erhält entsprechende `finalizedBy(...)`-Builderüberladungen. Alte Konstruktoren/Overloads delegieren mit leerer Finalizerliste, soweit sie zum unterstützten Aufrufvertrag gehören. Ein Finalizer ist kein spezieller Tasktyp. Namen werden erst beim Freeze endgültig aufgelöst; namengebundene Vorwärtsreferenzen sind zulässig. Die IDE darf dynamische Linkausdrücke weiterhin als partiell behandeln.

### F02 – Identität und Definitionsvalidierung

Unbekannte Ziele, fremde TaskRefs, doppelte Links innerhalb derselben Beziehung und `A finalizedBy A` sind eindeutige Definitionsfehler mit Task-/Quellbezug. Derselbe Finalizer bei mehreren verschiedenen Ownern ist dagegen erlaubt. Ein Link in `finalizedBy` und ein zusätzlicher gleichgerichteter `after`-Link sind nicht allein wegen derselben Endpunkte doppelt. Bestehende Task-/Parameteridentitäten, Definition-UUIDs und Freeze-Grenzen bleiben gültig.

Keine automatische Anlage fehlender Tasks und keine nachträgliche dynamische Registrierung. Nur TASK-/ACTION-Symbole sind Finalizerziele, weder Parameter, Cleanup noch innere Invocation.

### F03 – Statische Auswahl als kleinster Abschluss

Zuerst die normale Zielmenge N aus expliziten Zielen bzw. Defaults und deren transitiven `dependsOn`-Voraussetzungen bestimmen. Anschliessend die Kandidatenmenge P als kleinsten Fixpunkt bilden: für jeden enthaltenen Task seine Finalizer und für jeden neuen Kandidaten seine `dependsOn`-Voraussetzungen, jeweils rekursiv. Auch Finalizer von Voraussetzungen und Finalizerketten gehören in diesen Abschluss.

`after` selektiert weiterhin nichts. Die Auswahl von F allein selektiert **nicht rückwärts** seine Owner. Im Plan nur Beziehungen aufnehmen, deren beide Endpunkte tatsächlich in P liegen. Unausgewählte Owner dürfen einen Finalizer weder blockieren noch aktivieren.

### F04 – Aktivierung erst durch erreichte Owner

Ein Owner aktiviert seine Finalizer nach seinem terminalen Abschluss genau dann, wenn er zuvor `RUNNING` erreicht hatte. Das umfasst `SUCCEEDED`, `SKIPPED`, `FAILED` und `CANCELLED`. Ein `when: false` wird nach Start geprüft und aktiviert daher Finalizer. Auch Setup-/Konfigurations-/Prädikatfehler nach Start zählen.

`NOT_RUN` aktiviert nichts. Ein Task, dessen Voraussetzung vorher scheitert oder der bereits vor seinem Start durch Fail-fast, Cancellation oder `finishJob` unterdrückt wird, löst keine Finalizer aus. Eine aus anderem Grund bereits aktive Finalizerrolle bleibt erhalten. Kein Finalizer bei Fehlern vor jeder Taskregistrierung/-ausführung, z. B. ungültigem Plan oder gescheitertem Run-Workspace-Start.

### F05 – Einmal pro Run und geteilte Finalizer

Jeder Task wird höchstens einmal gestartet, auch bei mehreren Rollen und Ownern. Für einen Finalizer F gilt eine Abschlussbarriere: **alle ausgewählten Owner von F sind terminal**, einschliesslich `NOT_RUN`. Zusätzlich muss mindestens einer davon F aktiviert haben, ausser F ist unabhängig als normales Ziel bzw. normale Voraussetzung verlangt.

Beispiel: A und B finalisieren F. A läuft und scheitert; B wird aufgrund Fail-fast `NOT_RUN`; dann läuft F einmal. A erfolgreich und B später erfolgreich: F läuft erst nach B. Ist kein Owner erreicht und F nicht unabhängig benötigt, ist F `NOT_RUN/FINALIZER_NOT_TRIGGERED`. Es werden nie alle deklarierten, sondern nur die im aktuellen Plan ausgewählten Owner betrachtet.

### F06 – Ketten, direkte Ziele und Doppelrollen

`A →finalizer F →finalizer G` ist zulässig. Ein gestarteter, fehlgeschlagener oder übersprungener F aktiviert G; ein nie gestarteter F nicht. F darf gleichzeitig explizites Ziel oder Datenvoraussetzung sein. Alle für diesen Plan aktiven Reihenfolge-/Ownerbarrieren gelten trotzdem. Ein bereits abgeschlossener F wird bei einer weiteren Anfrage nie wieder ausgeführt und niemals retried.

Wird nur F direkt gewählt und kein Owner ausgewählt, läuft F wie ein normaler Task samt eigenen Voraussetzungen und eigenen Finalizern. Seine Rolle im Bericht muss ausdrückliche Auswahl von tatsächlicher Finalizeraktivierung unterscheiden können.

### F07 – Reihenfolge und echte Voraussetzungen

F läuft nach seinen ausgewählten Ownern und unter Beachtung der normalen `dependsOn`-/`after`-Reihenfolge. Es gibt weder eine Garantie „unmittelbar als nächste Anweisung“ noch eine implizite Reihenfolge zu allen Nachfolgetasks eines Owners. Bei mehreren zulässigen nächsten Tasks bleibt die Registrierungsreihenfolge deterministischer Tie-breaker.

Ein Finalizer darf eigene Voraussetzungen haben. Voraussetzungen, die ausschliesslich einem noch inaktiven Finalizer dienen, werden nicht vorsorglich als normale Arbeit ausgeführt. Nach Aktivierung dürfen sie auch im Drain laufen. Normale bereits abgeschlossene Voraussetzungen werden nicht wiederholt.

Eine echte `dependsOn`-Voraussetzung mit `FAILED`, `CANCELLED` oder `NOT_RUN` blockiert den Consumer. Eine erreichte `SKIPPED`-Voraussetzung bleibt wie bisher terminal ohne Wert; der Consumer kann laufen, sein erforderlicher `result(...)`-Zugriff kann jedoch scheitern. `after` verlangt nur terminale Reihenfolge, nicht Erfolg.

**Bewusste Grenze:** `A finalizedBy F` allein erlaubt F trotz Fehler von A. Deklariert F zusätzlich `dependsOn: 'A'`, verlangt der Autor damit ausdrücklich auch die erfolgreiche/übersprungene Datenvoraussetzung A. Bei `FAILED`/`CANCELLED`/`NOT_RUN` von A wird F dann blockiert. Diese zusätzliche Erfolgsbedingung nicht still ignorieren; dokumentieren und diagnostisch erklären. Für unbedingtes Aufräumen darf der Finalizer nicht über `dependsOn` an den Erfolg des aufzuräumenden Tasks gebunden werden.

### F08 – Zyklen und Aktivierungsblockaden vor Nebenwirkungen

Bestehende globale `dependsOn`-Definitionsvalidierung beibehalten. Im ausgewählten Kandidatengraphen zusätzlich die Vereinigung aus `DEPENDS_ON`, `AFTER` und `FINALIZED_BY` auf Zyklen prüfen. Selbstbezug gesondert melden. Unausgewählte `after`-Teilgraphen nicht plötzlich strenger behandeln als bisher.

Da Finalizer-Voraussetzungen bedarfsgesteuert starten, reicht reine Topologiesortierung nicht. Auch eine **aktivierungsbedingt blockierte Erfolgsplanung** ist vor jeder Taskausführung ein Planfehler. Kontrollbeispiel:

```text
A ist Ziel; A after D; A finalizedBy F; F dependsOn D.
D ist ausschliesslich Voraussetzung von F.
```

Der reine Reihenfolgegraph ist azyklisch, aber A wartet auf D und D wird erst durch A/F fällig. Diesen Fall mit `FINALIZER_ACTIVATION_DEADLOCK` und beteiligten Kanten ablehnen, statt D heimlich vorzeitig auszuführen oder während des Runs zu hängen. Eine nominale datengebundene Simulation des identischen Schedulingvertrags muss fortschreiten oder vor Runstart einen erklärten Fehler liefern. Keine beliebige Warteschleife.

### F09 – Fail-fast bleibt für normale Arbeit, Drain ist gezielt

Beim ersten normalen Fehler keine unabhängige normale Arbeit mehr starten. Bereits aktivierte Finalizer, ihre nötigen Voraussetzungen und daraus später aktivierte Finalizer dürfen weiterlaufen. Ein Fehler eines Finalizers stoppt nicht andere unabhängige fällige Finalizer. Nur seine echten Datenconsumer werden blockiert.

Noch nicht gestartete normale Tasks dürfen **nicht pauschal sofort** `NOT_RUN` werden, wenn sie als Voraussetzung einer jetzt oder später im aktiven Finalisierungszweig fälligen Aufgabe benötigt werden. Umgekehrt darf das Wiederfreigeben solcher Voraussetzungen nicht die gesamte alte normale Aufgabegruppe reaktivieren. Aktivierungsursachen und Bedarf getrennt führen. Am Ende hat jeder Kandidat genau einen terminalen Status; keine offenen PENDING-Zeilen im fertigen Bericht.

### F10 – Abschlussgrenze des Owners und Startfehler

Finalizer werden erst fällig, nachdem die normale Terminalisierung des Owners einschliesslich seiner Task-/Invocation-ResourceScopes abgeschlossen und dessen Ergebnis/Fehler erfasst ist. Kein Finalizer zwischen Body und `closeResources()` einfügen. Fehler beim Ressourcenschliessen können den Owner fehlschlagen lassen und dürfen nicht verloren gehen.

Startgrenze eindeutig implementieren: Entweder ein Task hat `RUNNING` erreicht und erhält genau ein terminales Taskereignis, oder er ist `NOT_RUN`. Ein Setupfehler nach `results.start(...)` darf Finalizer auslösen. Ein Fehler vor dieser Grenze darf nicht durch einen unzulässigen `ResultStore.complete(FAILED)` auf PENDING kaschiert werden. Keine doppelten started/finished-Ereignisse.

### F11 – Finalizer bleibt ein normal ausführbarer Task

Eigene `when`, `timeout`, Konfiguration, Ressourcen, Logging, typisierte Operation und innere `ctx.call(...)`/GRETL-Invocation funktionieren. `when: false` am Finalizer überspringt seinen Body und ist sichtbar; seine eigenen Finalizer werden aktiviert, weil er erreicht wurde. Die Finalizerbeziehung überschreibt weder das Prädikat noch echte Datenvoraussetzungen.

Es gibt genau einen Runner, dieselbe Task-/Operationspipeline und denselben Run-Workspace. Keine zweite Ausführungsengine für Closure-only-Finalizer. Kein Sondertask in einem Cleanup-Callback, der Berichte, Providerprüfungen oder Scope-Lebensdauer umgeht.

### F12 – Cancellation und Timeouts

Cancellation vor dem Start eines Owners aktiviert dessen Finalizer nicht. Cancellation während eines gestarteten Owners aktiviert sie nach dessen kooperativem Abschluss. Run-/Task-Timeouts und Interrupts behalten ihre bisherigen fachlichen Status-/Reasonverträge, soweit hier nichts ausdrücklich für den Abschlussmodus ergänzt wird.

Der erste Abbruchgrund bleibt erhalten. Ein Finalizer darf im begrenzten Abschlussmodus nicht sofort an der bereits beobachteten Cancellation oder am alten `finishJob` scheitern. Neue lokale Finalizer-Timeouts, neue Interrupts und Ablauf des Abschlussbudgets dürfen dagegen nicht ignoriert werden. Nicht kooperierender Java-/Native-Code kann weiterhin über die Deadline hinaus laufen; kein Thread.stop, kein verwaister Hintergrundthread und keine harte Echtzeitgarantie.

### F13 – `finishJob` und Abschlussarbeit

Ein erreichter Task, der `finishJob(reason)` aufruft, aktiviert seine Finalizer auch dann, wenn der interne Kontrollfluss anderweitig abgefangen wurde; die bestehende RunControl-Absicht bleibt massgeblich. Normale Folgearbeit bleibt aus. Ohne Fehler/Cancellation bleibt das Runergebnis wie bisher `NO_DATA` mit ursprünglichem Grund.

`finishJob` in einem Finalizer darf normale Arbeit stoppen bzw. den bestehenden Finishgrund erhalten, aber nicht bereits fällige Geschwisterfinalizer oder globale Cleanups unterdrücken. Der betreffende Finalizer folgt dem vorhandenen Finished/Skipped-Taskvertrag. Ein Finalizerfehler verhindert ein fälschliches `NO_DATA`/Erfolgsresultat. Keine interne Flagzurücksetzung, um nach Abschlussarbeit wieder normale Tasks zuzulassen.

### F14 – Ein begrenztes Abschlussbudget, kein Budget pro Finalizer

Bei normalem Lauf und bei rein fachlichem Fail-fast **ohne** Cancellation/Finish nutzen Finalizer die bestehende Runfrist sowie ihre lokale Taskfrist. Ein fachlicher Fehler allein setzt nicht ohne Anlass eine neue 30-Sekunden-Frist für möglicherweise reguläre Abschlussoperationen.

Nach Cancellation, beobachtetem Runtimeout oder `finishJob` verwenden die noch fälligen Abschlussaufgaben das vorhandene **einmalige `RunControl.completionBudget()`**. Am Recherchepin sind das 30 Sekunden für kooperativen Abschluss. Finalizer, globale Cleanup-Phase und vorhandener Shutdown-/Report-/Flushpfad teilen dieses Fenster; keine neue volle Frist für jeden Task, keine Reset-Schleife. Eine lokale Finalizerfrist ist zusätzlich durch die verbleibende Abschlusszeit begrenzt. Ein bereits laufender Task wird bei Cancellation nach den bisherigen Regeln terminalisiert, nicht in einem neuen Modus wiederaufgenommen. Seine laufende lokale Deadline wird nie zurückgesetzt; neu startende Abschluss-Tasks verwenden die verbleibende gemeinsame Zeit.

**Offene Konsequenz ausdrücklich dokumentieren:** Finalizer können einen Teil des vorhandenen Abschlussfensters verbrauchen. Globale Cleanup-Versuche folgen weiterhin, ihre Bodies können bei erschöpfter Frist wie bisher an der Budgetprüfung scheitern. Dieser Auftrag verspricht kein zusätzlich reserviertes Zeitkontingent für jede Abschlussaktion. Ohne Finalizer bleiben Zeit- und Shutdownverhalten unverändert. Eine spätere konfigurierbare Budgetpolitik ist nicht nötig, um diesen Auftrag zu erfüllen.

Der Shutdownhook darf nicht parallel Finalizer ausführen. Er fordert Cancellation an und wartet auf denselben Runner-/Reportabschluss. Keine versteckte Verlängerung über das vorhandene Ende hinaus; keine Prüfung mit unkontrollierten langen Sleeps. Budgetablauf ohne gestarteten Finalizer führt zu `NOT_RUN/FINALIZATION_BUDGET_EXHAUSTED`, nicht zu einem fingierten Success.

### F15 – Abschlusskontrolle muss bis in jede Invocation reichen

Die wirksame Ausführungskontrolle ist explizit im TaskFrame bzw. den erzeugten Contexts zu tragen. Taskcontext, Operationcontext, Konfigurationsphase, `when`, `workPath`, `resources`, innere Invocations, Resource-Close und FailureArbiter müssen dieselbe zum Ausführungsmodus passende Kontrolle verwenden.

Ein zusätzlicher Boolean nur in `SequentialJobRunner` reicht nicht: `BaseContext` und `OperationInvoker` greifen am Recherchepin mehrfach direkt auf `environment.control()` zu. Eine erfolgreich abgearbeitete Finalizeroperation darf deshalb bei der Terminalisierung nicht allein wegen einer älteren Run-Cancellation als erneut abgebrochen umgedeutet werden. Die ursprüngliche Run-Cancellation bleibt gleichzeitig für das **Runergebnis** erhalten.

Lokaler Timeout eines Abschluss-Tasks darf nicht das noch verbleibende gemeinsame Abschlussbudget für alle Geschwister sofort logisch canceln. Eine neue tatsächliche Run-Cancellation wird auf Runebene erfasst, aber nicht als alter Flagzustand ungeprüft in jede Finalizer-Operation zurückgespielt. Interruptstatus kontrolliert behandeln und am äusseren Ende korrekt wiederherstellen; nicht beliebige neue Interrupts pauschal löschen.

### F16 – Fehlerpriorität und Runergebnis

Task-/Invocation-Ergebnisse bleiben einzeln wahrheitsgemäss. Der Run aggregiert zusätzlich in deterministischer Beobachtungsreihenfolge:

| Situation | Runergebnis / Fehlerpriorität |
|---|---|
| Owner erfolgreich, Finalizer erfolgreich | bisheriger Erfolg |
| Owner fehlgeschlagen, Finalizer erfolgreich | FAILED; Ownerfehler bleibt primär |
| Owner und Finalizer fehlgeschlagen | FAILED; ursprünglicher Fehler primär, Finalizerfehler zusätzlich |
| Owner erfolgreich, erster Finalizerfehler vor einer Run-Cancellation | FAILED; Finalizerfehler primär |
| Cancellation bereits massgeblich, danach Finalizerfehler | CANCELLED mit ursprünglichem Grund; Finalizerfehler sekundär |
| Früherer echter Fehler, danach Cancellation | FAILED mit früherem Primärfehler, Abbruch-/Abschlussdetails bleiben sichtbar |
| Finish ohne Fehler, danach Finalizerfehler | FAILED statt NO_DATA |
| Finish, danach erster tatsächlicher Timeout/Cancellation | CANCELLED mit diesem Grund, sofern kein früherer echter Fehler vorliegt |
| Finalizer scheitert, weiterer unabhängiger Finalizer ist fällig | weiteren Finalizer versuchen, Fehler nicht überschreiben |

Fatalfehler im bisherigen Sinn (`VirtualMachineError`, `ThreadDeath`) nicht schlucken. Keine Garantie für weitere Finalisierung danach. Ein fehlender notwendiger Abschlusslauf wegen Budgetablauf ist kein erfolgreicher Abschluss; vorhandene Cancellation bleibt CANCELLED, bei Finish ohne frühere Cancellation wird der Timeout explizit als Abbruch erfasst. Keine künstliche Erfolgsaufwertung durch leere Finalizerliste nach einem Fehler.

### F17 – Keine impliziten Ergebnisrechte

`finalizedBy` erzeugt **keine** Datenvorgängerrechte. `result` und `resultOptional` behalten ihre bestehenden Regeln: nur direkt/transitiv erklärte `dependsOn`-Produzenten; ein fehlgeschlagener Produzent liefert kein Ergebnis. `resultOptional` ist kein „Fehler ignorieren“-API.

Ein Finalizer kann Ergebnisse eigener erfolgreicher Datenvoraussetzungen verwenden. Weder Ownerstatus noch Ownerresultate werden über eine globale mutable Map hineingeschmuggelt. In diesem Auftrag kein neues allgemeines Taskstatus-/Failure-Inspection-API notwendig. Für Aufräumen notwendige Pfade/IDs über vorhandene Parameter/Konfiguration oder einen passenden erfolgreichen Vorbereitungstask bereitstellen. Dessen Lebensdauer bewusst planen; `ctx.resources()` eines Owners ist schon geschlossen, wenn sein Finalizer startet.

### F18 – Ressourcen, Cleanup und Lifecycle erhalten

Finalizer sind normale Taskscopes mit eigenen Workspaces/Invocation-IDs; globale Cleanups bleiben separate Cleanups. Reihenfolge: erreichte normale Tasks und fällige Finalizer → verbleibender Drain → globale Cleanups in bisheriger umgekehrter Registrierungsreihenfolge → bisherige Retention-/Report-/Flushbehandlung.

Keine zusätzliche Registrierung von Finalizern als Cleanups. Keine doppelte Ressourcenfreigabe durch denselben Runner, keine Erweiterung bereits geschlossener Contexts und keine Aufhebung der Secret-/ContextClosed-Grenzen. Ein Finalizer kann absichtlich idempotent aufräumen; der Runner behauptet keine Exactly-once-Wirkung in externen Systemen, nur höchstens einen Taskstart pro Run.

### F19 – Includes, Subprojekte und Quellzuordnung

Finalizer-Links folgen denselben vorhandenen Scope-/Namensregeln wie die anderen Tasklinks, einschliesslich qualifizierter Projektverweise und Vorwärtsreferenzen über Includegrenzen. Die Ressource-/Arbeitsbasis eines Finalizers ist die seiner **eigenen Deklaration bzw. ihres Verwendungskontexts**, nicht automatisch die des auslösenden Owners.

Fehler, Hover, Navigation und Plananker nennen die echte Fragmentdatei/Zeile. Eine physisch gemeinsame Fragmentdatei kann verschiedene qualifizierte Tasks definieren; „einmal“ meint Taskidentität im gemeinsamen Run, nicht einmal je Quelldatei. Die vorhandene Include-Bindingisolation bleibt bestehen.

### F20 – SCRIPT-, JAR- und IMAGE-Parität

Dasselbe Scheduling und derselbe Ausführer gelten für Scriptjobs, Projekte und vorkompilierte Job-JARs. Ein Finalizer darf im JAR nicht erst aus einer Groovydatei nachgeladen werden. Neue Pakete laufen ohne ursprünglichen Quellbaum. Alte Pakete ohne Finalizer bleiben les-/startbar.

Das generische IMAGE bleibt der bestehende externe SCRIPT-/Projektweg. Kein spezifisches Job-JAR ins generische Image integrieren und keine zweite Containersemantik. Echte Containerprüfung nur bei einschlägiger Container-/Launcheränderung; der unveränderte IMAGEpfad ist kein Grund für eine Vollmatrix.

### F21 – Definition, Introspektion, Hash und Metadaten

`check`, `plan` und Jobhilfe kennen die neuen Deklarationen, führen aber weder Tasks, Prädikate, Taskkonfigurationen noch Finalizer-/Cleanupbodies aus. Vertrauenswürdiger Runtime-Scripttoplevel verhält sich wie bisher; die IDE führt weiterhin nichts aus.

Quellhashalgorithmen, Benutzer-Paketdeskriptoren und das Include-Bundleformat nicht allein wegen eines neuen Taskheaders ändern. Die kompilierten Scriptbodies registrieren die Beziehung über die DSL. Nur tatsächlich geänderte serialisierte Verträge explizit versionieren. Report-/Plan-/LSP-/Operationskatalogversionen nicht vermischen. Tatsächliche aktuelle Parser in P0 lesen, nicht eine freie Version raten.

### F22 – Plan, Events und Bericht erklären die Finalisierung

Ein Finalizer erscheint im normalen Task-/Invocationbericht. Der Plan enthält deklarierte `FINALIZED_BY`-Kanten mit Owner → Finalizer und kennzeichnet normale Auswahl gegenüber finalizerbedingten Kandidaten. Die tatsächliche Ausführung erfasst Aktivierung durch Owner, Ausführungsrolle (normal, Finalizer, Voraussetzung, gegebenenfalls mehrere Gründe), terminalen Status und nachvollziehbare NOT_RUN-Gründe.

Nicht für jeden auslösenden Owner einen zweiten Taskoutcome erzeugen. Bei gemeinsamem Finalizer deklarierte Owner von wirklich aktivierenden Ownern unterscheiden. Keine neue Taskstatusfamilie erforderlich. Textplan, JSON-/Reportdarstellung sowie vorhandene DOT-/Mermaidrenderer dürfen neue Kanten nicht still als AFTER zeichnen. Maskierung von Secrets und ursprüngliche Source-Maps bewahren.

### F23 – Jobs ohne Finalizer bleiben kompatibel

Für Definitionen ohne Finalizer bleiben Zielauswahl, deterministische Reihenfolge, Fehler-/Abbruchpriorität, Ergebnisrechte, Status und Cleanupreihenfolge unverändert. Eine neue allgemeine Taskschleife ist nur mit dieser Regression zulässig; keine Featureflag-Parallelengine als dauerhafte zweite Wahrheit.

Neue APIs möglichst additiv. Kein willkürliches API-/Paketbreaking und keine neuen Abhängigkeiten der Taskmodule auf Runner/Tooling. Ein nötiger interner Recordumbau darf nicht als Anlass für unrelated Refactoring dienen. Bestehende akzeptierte Include-Unterstützung nicht wieder auf pauschales PARTIAL zurückstufen.

### F24 – Statische Analyse und Texteditor

Der Sprachvertrag erkennt `finalizedBy`; Parser/Headeroptionen, semantische Referenzen, Diagnostics, Hover, Completion, Definition und References werden ergänzt. Neue Referenzart `FINALIZED_BY` ist Taskreferenz, keine Datenbindung. Unbekannte, mehrdeutige und zyklische Ziele an der tatsächlichen Option erklären.

Normale vollständig analysierbare Includejobs bleiben vollständig. Dynamische/kaputte/recoverybehaftete Fälle behalten ehrliche partielle Coverage. Keine Runtime-/Taskhandlerausführung und keine unerlaubte Modulabhängigkeit im LSP. Verwende die vorhandenen Include-Anwendungskontexte statt URI → einziger Scope zu unterstellen.

### F25 – Modelergraph und Zielvorschau

Zeige die Finalizerbeziehung als eigene, beschriftete Kantenart mit korrekter Richtung. Finalizer bleiben normale Task-/Actionknoten, nicht globale Cleanupknoten. Ein Knoten kann zugleich mehrere Rollen haben. Parameter/Cleanups bleiben ausserhalb des normalen Task-DAG wie bisher.

Die statische Zielvorschau benutzt denselben reinen Auswahl-/Erfolgsplanvertrag wie der Runtimeplan. F und seine Voraussetzungen werden für A mit angezeigt, unselektierte Owner nicht. Erkläre bedingte Aktivierung; keine Simulation eines tatsächlich bekannten Erfolgs/Fehlers in der IDE. Reihenfolge-/Aktivierungsfehler werden auch im Tooling fachlich sichtbar.

### F26 – Sichere schreibende Unterstützung

Mindestens über die Details eines Owners Finalizer hinzufügen/entfernen und vorhandene Finalizerkanten gezielt entfernen können. Ein zusätzlicher neuer Draggestus ist nicht Pflicht; wird die bestehende Verbindungsoberfläche verwendet, besitzt sie eine ausdrückliche Finalizerart. Im Core steht die Mutation immer am Ownerheader.

Rename eines Tasks aktualisiert auch gebundene `finalizedBy`-Referenzen. Löschung eines noch referenzierten Finalizers wird blockiert, keine stille Kaskade. Generische `SetTaskOption(finalizedBy, ...)`, Formularbatch, Quick Fix und direkte Core-Intents unterliegen denselben Coverage-, Owner-, Sharedsource- und Postimagebedingungen wie andere strukturelle Änderungen. Add/RemoveDependency dürfen die neue Enum nicht automatisch als AFTER interpretieren.

Die aktuelle Include-Ownership-/Footprintregel bleibt: automatische Änderungen gemeinsam verwendeter Quellen werden kontrolliert abgelehnt; ein Kontextpicker ist keine Schreibberechtigung. Unabhängige sichere lokale Edits bleiben möglich. Comments, Unicode, CRLF, nicht betroffene Strings und Includezeilen bleiben bytegenau erhalten.

### F27 – Protokoll, Snapshot- und Hostschutz

Server und VSIX müssen dieselbe neue Kanten-/Optionsunterstützung besitzen. Ein älterer Client darf Finalizer nicht als gewöhnliches AFTER darstellen oder falsch mutieren. Vorhandene Version-/Bundlevalidierung nutzen und die Featurefähigkeit ausdrücklich kompatibel absichern (z. B. Taskfinalizer-Featureversion oder begründeter Graph-Schemabump). LSP-Basisversion 1 nicht blind umnummerieren; keine falsche Zusage an Clients, die die neue Enum nicht verstehen.

Bestehende Includecontext-, Ownergeneration-, Sourcevector- und Sessionprüfungen bleiben erhalten, auch nach asynchroner nativer Editkonvertierung. Stale Plan nach Änderung eines Finalizer-Literals, einer Fragmentquelle oder eines Verwendungskontexts wird ohne Teilmutation abgewiesen. Native WorkspaceEdits und Undo/Redo der tatsächlich geänderten Dokumente, keine eigene Dateischreibschleife.

### F28 – Wirkliche End-to-End-Nachweise

Mindestens ein echter stdio-LSP-Lauf, ein kleiner installierter VSIX-Benutzerablauf und reale SCRIPT-/JAR-Prozesse mit Finalizern gehören zum Lieferumfang. Der im Editor gespeicherte Fixturetext wird unverändert separat mit der Runtime ausgeführt. Die GUI startet dafür keinen Job selbst. Cancellation-/Shutdownverhalten zusätzlich mit einem kontrollierten echten Prozess prüfen; reine Sleeps oder gemockte Exitcodes genügen nicht.

Ein GUI-Mock ist keine installierte GUIprüfung. Fehlende Ausführungsfähigkeit wird `NOT_EXECUTED`, nicht mit einem früheren PASS oder einer HTMLattrappe ersetzt. Keine vollständige Wiederholung der alten G01–G30-/Dienstmatrix.

### F29 – Anleitung und öffentliches Beispiel

Ein kanonisches synthetisches Beispiel erklärt Erfolg, Ownerfehler, NOT_RUN, gemeinsame Finalizer, Listensemantik, echte Voraussetzungen und Unterschied zu globalem Cleanup. Syntaxbeispiele tatsächlich mitchecken. Ein Fehlerdemo muss den erwarteten Fehlerexit und trotzdem erzeugten Finalizerbeleg prüfen, nicht als erfolgreicher Run umetikettiert werden.

Nutzerdokumentation erklärt Ergebnisrechte, Ressourcenlebensdauer, Sharedsource-Schreibgrenze und kooperatives Abschlussbudget. Bestehende Quickstart-/Distributionskopien gemäss vorhandenem Verfahren synchron halten. Historische Abnahmedateien nicht rückwirkend ändern.

### F30 – Fokussierte, unabhängige und belegte Abnahme

Alle fälligen Kriterien werden durch wirklich ausgeführte, aussagekräftige Prüfungen abgedeckt. Reine Parität zwischen zwei Consumern desselben fehlerhaften Algorithmus genügt nicht: handfest erwartete Abläufe, negative Gegenfälle und eine unabhängige zusätzliche Reviewerprobe verwenden. Jeder Phasenkandidat hat eine frische unabhängige OpenCode-Session am exakten SHA. Gegenproben nicht vom Implementierer als fertige Reviewerurteile vorgeben.

Bei Fixes nur betroffene Tests erneut ausführen, aber die Phase als Ganzes erneut bewerten. Evidenceherkunft, Kandidat, Inputs und Artefakte bleiben nachvollziehbar. Keine vorgefüllten Verdicts, Nulltest-PASS, gelockerten Assertions oder verschwundenen Pflichtprüfungen.

## 4. Präzise Schedulingarchitektur

### 4.1 Warum ein erweiterter Toposort nicht genügt

Die Runtime darf nicht einfach alle ausgewählten Tasks in einer fixen Liste abarbeiten und beim ersten Fehler abbrechen. Ebenso falsch wäre, danach blind alle als Finalizer markierten Knoten auszuführen. Beides verwechselt Kandidaten, tatsächliche Aktivierung und Voraussetzungen.

Es braucht einen kleinen runlokalen Schedulerzustand auf dem schon validierten Plan. Er besitzt nur IDs/Status/Beziehungen, keine Scriptobjekte, Handler, Filesystemzugriffe oder Threads. Ausführung verbleibt in `SequentialJobRunner.executeTask(...)` und `OperationInvoker`.

### 4.2 Empfohlenes reines Modell

Die Namen sind **neue Verantwortungszuschnitte**, keine Behauptung vorhandener APIs. In P0 auf den aktuellen Code abbilden und eng zusammengehörige Records zusammenfassen.

```java
// In gretl-language-contract: ausschliesslich JDK-Daten.
record FinalizerRelations(
    Map<String, List<String>> byOwner,
    Map<String, List<String>> ownersByFinalizer) {}

record PlannedTasks(
    List<String> requestedTargets,
    Set<String> normalDemand,
    List<String> candidateOrder,
    FinalizerRelations finalizers,
    Map<String, Set<String>> dataPredecessors,
    List<PlanSemantics.Edge> activeEdges) {}

enum ScheduleTerminal { SUCCEEDED, SKIPPED, FAILED, CANCELLED, NOT_RUN }
enum ScheduleMode { NORMAL, DRAIN }
```

`PlanSelection` kann diese Daten additiv tragen, statt daneben einen konkurrierenden Gesamtplan zu schaffen. `candidateOrder` ist die deterministische nominale Erfolgsreihenfolge; nicht behaupten, dass jeder Kandidat zur Laufzeit ausgeführt wird. Registrierungsindizes und Zugriffsrechte nicht aus Dateisortierung ableiten.

### 4.3 Ein Schedulervertrag für nominale Vorschau und Runtime

Empfohlene kleine Klasse `TaskSchedule` (oder passend benannter bestehender Vertrag):

```java
TaskSchedule create(PlannedTasks plan);
ScheduleDecision next();
void started(String taskId);
void completed(String taskId, ScheduleTerminal status);
void stopNormalWork(StopCause cause);
void stopCompletionWork(String reason);
boolean finished();
```

`ScheduleDecision` ist z. B. `Execute(taskId, demandReasons)`, `NotRun(taskId, reason)` oder `Done`. Der Ausführer setzt die tatsächliche Startgrenze erst unmittelbar vor der gemeinsamen Taskpipeline; zwischen `next` und `started` eingetretene Cancellation muss noch korrekt berücksichtigt werden. Zulässige Übergänge und höchstens ein laufender Task prüfen. `completed` nach `NOT_RUN` aktiviert nichts; gestartete terminale Tasks aktivieren ihre ausgehenden Finalizer genau einmal.

Vor der Runtime simuliert die reine Planung denselben Scheduler mit nominal erfolgreichen gestarteten Tasks. Sie prüft damit zusätzlich Aktivierungsfortschritt und liefert die Vorschau. Die Runtime verwendet dieselben Bedarf-/Barrierenregeln mit tatsächlichen Outcomes, nicht eine unabhängig implementierte zweite Auswahlsemantik. Die nominale Simulation ist **keine** Ausführung und keine erneute Bewertung von `when`.

### 4.4 Bedarf, potentielle Fortsetzung und Terminalisierung

Interner Zustand mindestens:

- unveränderlicher Kandidatengraph und normale Bedarfsmenge N;
- Taskstatus pro Kandidat und Menge wirklich gestarteter Tasks;
- Menge aktivierter Finalizer sowie aktivierende Owner pro Finalizer;
- Modus NORMAL/DRAIN und Stopursache;
- direkter aktueller Bedarf und noch mögliche spätere Finalisierungsbedarfe.

Der aktuelle Bedarf besteht aus noch erlaubtem normalem Bedarf und den aktivierten Finalizern samt transitiven Datenvoraussetzungen. Im DRAIN bleibt nur letzterer. Der **potentielle Abschlussbedarf** enthält zusätzlich Finalizer, die durch noch erreichbare Aufgaben dieses Abschlusszweiges später aktiviert werden könnten, wiederum mit deren Voraussetzungen. Diese Unterscheidung verhindert, dass eine erst später benötigte gemeinsame Voraussetzung zu früh NOT_RUN wird.

Ein pending Task ist ausführbar, wenn er tatsächlich verlangt ist, alle Datenvoraussetzungen erfolgreich/übersprungen terminal sind, alle ausgewählten AFTER-Vorgänger terminal sind und bei vorhandener Finalizerrolle alle ausgewählten Owner terminal sind. Terminale fehlgeschlagene Datenvoraussetzungen sperren ihn mit nachvollziehbarem Grund. Reine Reihenfolge darf auch durch NOT_RUN erfüllt sein.

Nicht mehr erreichbare Kandidaten werden nachvollziehbar NOT_RUN; das kann Ownerbarrieren anderer Finalizer freigeben. Normaler Fail-fast darf dabei fällige Abschlussvoraussetzungen nicht abschneiden. Keine spätere Wiederbelebung eines einmal terminalisierten Tasks. Bei jedem Schleifendurchlauf Fortschritt erzwingen: Ausführungsentscheidung, neue Terminalisierung oder Ende. Interne unerwartete Blockade ist ein expliziter Runnerfehler mit unverlorener Primärursache, keine Endlosschleife und kein willkürliches Ignorieren einer Kante.

### 4.5 Pflichtbeispiele für die Zustandsmaschine

1. `P →depends A →final F`, P scheitert: A und F NOT_RUN, F nicht aktiviert.
2. A/B teilen F, A scheitert: A FAILED, B NOT_RUN, F genau einmal.
3. A finalisiert F; F benötigt D; A scheitert: D und F dürfen noch laufen. D nicht vorher nur deshalb ausführen, weil F Kandidat ist.
4. A finalisiert F und G; F scheitert: G läuft weiter. G dependsOn F würde G blockieren; G after F nicht.
5. A finalisiert F; F finalisiert G: G nach erreichtem F auch bei F-Fehler, nicht nach ungestartetem F.
6. Gemeinsame normale Voraussetzung D wird später von einem aktiven Finalisierungszweig gebraucht: sie nicht vorher pauschal FAIL_FAST-terminalisieren.
7. A after D / A finalizedBy F / F dependsOn D: aktivierungsbedingte Blockade vor Runstart erkennen.
8. Keine Finalizer: exakt bestehende normale Reihenfolge und Abbruchbehandlung.

Tests prüfen nicht nur die Liste der ausgeführten Namen, sondern Startanzahl, Trigger, Rollen, NOT_RUN-Gründe und erlaubte Ordnungsrelationen. Für unabhängige Tasks keine zufällige strengere Reihenfolge festschreiben, ausser dem ausdrücklich definierten Registrierungstie-breaker.

## 5. Implementierung bis auf Klassen und Methoden

### 5.1 Sprachvertrag und Definitionen

**`DslVocabulary`:** `taskOptions()` um `finalizedBy` ergänzen. Prüfen, wo Taskheaderoptionen als reservierte Konfigurationsfelder gelten. Kein Finalizerhelper im Cleanup-/Invocationkontext. Die Oberfläche erst vollständig freigeben, wenn der Runner sie wirklich ausführt.

**`TaskRegistration` / `TaskDefinition`:** zusätzliche immutable Link-/Ref-Liste. Builderüberladungen passend zu bestehenden Formen; defensive Kopien. Bestehende Konstruktoren kompatibel delegieren. Null-/Typ-/Duplikatprüfung nicht als allgemeines neues DSLsystem umbauen.

**`JobDefinitionBuilder.freeze()` / `createDefinition(...)` / `resolveLinks(...)`:** neue Links mit demselben UUID-/Ref-Eigentum auflösen. Owner/sourcebezogene Diagnosen, Selbstlinkprüfung, keine Namensauflösung während Execution. Die relationale Semantik erst im Plan, nicht durch globale Cleanupregistrierung.

**`GretlScript.task(...)` und `action(...)`:** Header normalisieren und `.finalizedBy(links(...))` setzen. `JobDslModel.registerLinks(...)` und `ProjectTaskIndex.rememberLinks(...)` gezielt erweitern, damit Projekt-/Forwardrefdiagnostik die neue Beziehung kennt. `qualifiedReference` und bestehende Include-Session benutzen. Keine API zum nachträglichen Mutieren gefrorener Tasks.

### 5.2 Reine Planung und Scheduler

**`PlanSemantics.EdgeKind`:** `FINALIZED_BY`. Alle enumabhängigen Fallunterscheidungen bewusst behandeln; insbesondere Konstruktionen „DEPENDS_ON, sonst AFTER“ suchen. Datenclosure bleibt ausschliesslich DEPENDS_ON.

**`PlanSemantics.plan(...)`:** normaler Abschluss, erweiterter Kandidatenabschluss, ausgewählte Ownerbeziehungen, Vereinigungszyklen, nominaler Schedulinglauf. Zeit-/IO-/Taskzustand gehört nicht hinein. Neue methodische Grenzen z. B. `expandCandidates(...)`, `indexFinalizers(...)`, `validateSelectedOrder(...)`, `simulateSuccessfulRun(...)`.

**`TaskSchedule` (neu):** kleine reine Zustandsmaschine gemäss §4. Keine Retry-/Thread-/persistente Queuefunktionalität. Terminalstatus und Startgrenze explizit. `next()` idempotente Entscheidungsabfrage oder klar dokumentierte Zustandsübernahme, nicht versteckt zwei Tasks gleichzeitig starten.

**`ExecutionPlanner.plan(...)`:** DefinitionRefs vor logischer Planung prüfen; `owner.id() → finalizer.id()` korrekt abbilden. Nominale IDs wieder den vorhandenen TaskDefinitions zuordnen. **`ExecutionPlan` / `PlanEdge`:** Kandidaten-/Normal-/Finalizerinformationen unveränderlich transportieren, Bestandsmethoden soweit möglich bewahren. Keine Datenrechte aus neuen Kanten.

### 5.3 Runner und wirksame Ausführungskontrolle

**`SequentialJobRunner.execute(...)`:** fixe for-Schleife durch Konsum des Schedulervertrags ersetzen. Ein Taskausführungsweg, gleiche ResultStore-/Workspace-/Eventsobjekte. Nach Taskterminalisierung Trigger registrieren, Stopbedingungen aktualisieren, gezielt drainen. Globale Cleanup- und Retentionsphase bleibt nach dem Schedulerabschluss. Resttasks nicht mehr pauschal als NOT_RUN abschneiden.

**`executeTask(...)` / `completeTask(...)`:** explizite Ausführungsrolle und wirksame Kontrolle akzeptieren. Taskstart vor taskbezogenem Setup sauber erfassen. ResourceClose und FailureArbiter wie bisher, aber mit dem passenden Kontrollsnapshot. Kein Ressourcen-/Invocationpfad darf Finalizerstatus aus alten globalen Cancel-/Finishflags neu erfinden.

**Empfohlene neue interne Schnittstelle `TaskExecutionControl`:**

```java
TimeBudget budget();
void checkBeforeWork();
void checkCancellation();
ControlSnapshot snapshot();
void observeCancellation(CancelReason reason);
boolean cancellationObserved();
```

Eine normale Implementierung delegiert auf die bestehende `RunControl`/lokale Frist. Eine Abschlussimplementierung unterscheidet Runabsicht von aktueller Task-/Invocationausführung und nutzt bei Cancellation/Finish das einmalige completionBudget. Names/Shape dürfen passend vereinfacht werden. Das Objekt ist interne immutable/gekapselte Infrastruktur, keine durch Jobcode umschaltbare globale Freigabe. Eine `finalizer=true`-Abkürzung, die alle Budgetprüfungen deaktiviert, ist unzulässig.

**`TaskFrame` / `TaskContextImpl` / `OperationContextImpl` / `BaseContext`:** wirksame Kontrolle explizit weitergeben. `BaseContext.checkExecution`, `checkCancellation`, `currentCancellationReason`, `observeCloseFailure` und `cancellation()` prüfen. Closed-/Closing-Verträge bewahren. `TaskContextImpl.finishJob` speichert weiter die Runabsicht; die aktuelle Completion wird konsistent ermittelt.

**`OperationInvoker.invoke(...)`:** nicht nur den Eingangscheck, sondern Konfiguration, Handler, Catchblöcke, Close und `FailureArbiter.resolve` auf die wirksame Taskkontrolle beziehen. Innere `ctx.call` und typisierte GRETL-Operationen müssen in derselben Abschlussphase arbeiten. Identitäten, Typprüfungen, Secretredaktion und Fehlerphasen erhalten.

**`RunControl`:** bestehende Cancellation-/Finishgründe nicht rücksetzen. `completionBudget()` einmalig nutzen; neue kleine Accessoren nur soweit für saubere Kontrollsicht nötig. Keine neue Ablaufengine in RunControl. **`JobRuntime.run(...)`-Shutdownhook:** nur den selben kooperativen Runner anstossen und dieselbe begrenzte Wartezeit beachten; gezielte Regression für SCRIPT und JAR.

**`FailureArbiter`:** bestehende per-Task-Priorität nicht pauschal umschreiben. Die korrekte Kontrollsicht einspeisen. Runaggregation ggf. in eine kleine neue `RunFailureAccumulator`-Klasse auslagern: `recordTaskOutcome`, `recordCleanupFailures`, `finish`; sie erhält die ursprünglich beobachtete Primärursache und alle späteren Ursachen in Reihenfolge. Keine Ableitung aus Logseverity.

### 5.4 ResultStore, Reports und Pakete

**`ResultStore`:** ein Zustand pro Kandidat, höchstens ein Start; Aktivierung ist keine neue Resultslotart. `require` und `optional` behalten die geprüften Rechte/Fehler. Rolle/Trigger bei Bedarf in einem eigenen Runmetadatensatz führen, nicht TaskRefidentitäten ändern. Alte Taskoutcomes bleiben serialisierbar; NOT_RUN muss nachvollziehbar sein.

**`JobOutcome` / `TaskOutcome` / `RunReportWriter` / Eventformat:** aktuelle tatsächliche Felder/Schema vor Änderung lesen. Additive Finalizerinformationen über eine passende schmale Struktur, ohne denselben Task mehrfach aufzuführen oder einen alten Failure zu überschreiben. Schema nur ändern, wenn der vorhandene Vertrag es erfordert; passende Leser/Goldenfiles zusammen anpassen.

**`JobHelpPrinter` / `PlanGraphRenderer`:** neue Kante und bedingte Kandidatenauswahl in Text und unterstützten Graphformaten. Reines Rendererfallback auf AFTER ist ein Fehler. **Packager / JobJarMain:** nach der DSL-/Definitionänderung insbesondere Classpath und alte Signaturen prüfen. Keine neue Bundleversion allein für eine Taskoption. Alte JAR-Fixture weiter ausführen; neue quellenfreie Single-/Projectpakete testen.

### 5.5 Tooling-Core

Vorhandene Module/Methoden tatsächlich lesen, nicht auf den vor Include-Tooling geltenden Parserstand zurücksetzen.

**Syntax/Header:** Optionen korrekt extrahieren, literal gebundene TaskRef-Aliase wie andere Links behandeln. **`SemanticModel.ReferenceKind`:** neue Finalizerreferenz; **`ProjectAnalyzer` bzw. aktuelle Nachfolger:** Linkauflösung im vorhandenen Anwendungskontext, neue Graphkante mit Owner → Finalizer. `dataPredecessors` unverändert. Parameternamensraum ausschliessen.

**Queries:** `CompletionQueries`, `LanguageQueries`, `SignatureQueries` und aktuelle ReferenceIndex-Pfade ergänzen. Completion schlägt nur passende Tasks im richtigen Kontext vor. Definition/References/Rename müssen `finalizedBy`-Literale berücksichtigen. Fehlende/mehrdeutige Ziele an Originalrange diagnostizieren, keine neue Auflösung aus Laufzeit.

**`JobGraphProjector` / `TargetPreviewProjector`:** eigene Kante, normale Knoten, gemeinsamer reiner Plan. Include-Ressourcen-/Container-/Contextdaten erhalten. **`GraphFormProjector`:** Finalizerliste als strukturelle Taskoption; capability auf tatsächliche Änderung beziehen, nicht nur auf ein Description-Feld.

**`EditabilityAnalyzer` / `SemanticEditPlanner`:** `finalizedBy` in wirkungsbezogene Graph-/Referenzklassifikation aufnehmen. Bestehende Include-Footprint-/Ownerguards vor jeder neuen Mutation auswerten. Eine leere Liste, Remove und Batch sind ebenso strukturell. **`HeaderSourceEdits` / `ReferenceSourceEdits` / `EditVerifier`:** am Owner editieren, gebundene Referenzen erhalten, virtuellen Postimageplan einschliesslich neuer Auswahl-/Zyklussemantik prüfen. Keine zusätzlichen Reads am eingefrorenen Postimage vorbei.

Für grafische Bearbeitung genügt ein sauberer `SetTaskOption("finalizedBy", ...)`-Pfad. Falls eine explizite Add-/Remove-Finalizer-Operation einfacher ist, einen klaren Owner/Finalizer-Intent einführen; nicht den bestehenden Producer/Consumer-Dependency-Intent unbemerkt umdrehen. Beide Wege müssen denselben Validator benutzen.

### 5.6 LSP und VS Code

**`GretlProtocol` / aktueller Server-/Querymapper:** neue Kanten-/Optiondaten und capabilitykompatible Verarbeitung. Bestehende contextToken-, Owner-/Sourcegenerationfelder vollständig weitertragen. stdio-Requests weiterhin durch Core prüfen, keine zweite Graphsemantik im Host.

**`vscode/gretl-vscode/src/modeler/graph-model.ts` und aktuelle Schema-/Layoutpfade:** neue Enum ausdrücklich zulassen, korrekte Owner→Finalizer-Darstellung, Label/Legende und unterscheidbare Beziehung. Accessibility nicht nur über Farbe. Layout bleibt eine Darstellung; kein erfundener Datenport für Finalizer.

**Details-/Edit-/Controllerpfade:** Referenzliste am Owner bearbeiten, Löschung einer Finalizerkante am Owner planen. Stale-Antworten, falsche Session-/Ownergeneration und Sharedsourcefälle ablehnen. Native WorkspaceEdit-/Undo-/Savepfade verwenden. Keine Runtimeausführung aus dem Modeler. Bestehende Sprachmitgliedschaft und Beobachtung der Fragmente nicht verändern, ausser unmittelbar erforderliche Tests/Fixes.

## 6. Phasen und Abnahmegates

Jede Phase hat einen abschliessenden unabhängigen Review, nicht einen pro Klasse. Innerhalb einer Phase beliebig kleine Commits; die Phasebase bleibt über Korrekturversuche konstant. Bei neuer SHA frischer Reviewkontext. Selbständige Fortsetzung erst nach `ABGENOMMEN`.

### P0 – Mainbasis, gezielte Baseline und wirklicher Reviewweg

**W01:** Sauberer eigener Featureworktree auf tatsächlichem Main mit vorhandenen Includes und Include-Tooling; SHAs/Delta zum Recherchepin dokumentiert, fremde Dateien unverändert.

**W02:** Kleine tatsächlich ausgeführte Baseline der Plan-/Failure-/Budgetsemantik und eines vorhandenen Include-Toolingpfads. Keine gesamte Produktqualifikation. Fehlendes `finalizedBy` zunächst als erwartete Red-Probe dokumentieren, nicht alte „apply fehlt“-Proben übernehmen.

**W03:** Tatsächlich funktionierender autonomer zweiter Agent: OpenCode 2.x, gewünschtes Modell, isolierter Kandidatenworktree, erlaubte Testausführung, eigene Gegenprobe, vollständiger Abschluss und prüfbares Urteil.

**W04:** Richtiger neuer Auftrag/Digest/Kriterienumfang; Testausnahme dokumentiert; vorhandene Reviewmechanik passend wiederverwendet, keine alten Vollgates eingeschleppt.

Lies `AGENTS.md`, die drei aktiven Dateien, den Baselinebericht sowie die betroffenen Klassen/Tests. Inventar klein halten: `WORK_QUEUE.md` mit Phase, festem Basis-SHA, Kandidat, ausgewählten Tests, Belegen, Findings und nächster Arbeit. Aktuelle Status-/Reviewdateien früherer Aufträge nicht bearbeiten. Bereits angefangenen Featurecode erhalten und gegen diesen Vertrag prüfen.

**Gate:** W01–W04 grün und unabhängig abgenommen. Echte fehlende autonome Infrastruktur blockiert P1; keine manuelle Reviewübergabe als regulärer Ersatz.

### P1 – Definitionsmodell, Auswahl, Barrieren und reine Planung

Implementiere neue immutable Beziehungen, Java-Registrierung/Freeze sowie gemeinsame Graph- und Schedulingregeln. W01–W04 als etablierte Arbeitsweise erhalten. Der Runner darf einen neuen Finalizerplan vor P2 nicht still wie einen gewöhnlichen Plan ohne Finalisierung ausführen; nötigenfalls eine vorübergehende klare Nichtunterstützungsdiagnose, später entfernen. Keine Featureoberfläche als fertig ausgeben.

**Kriterien:** F01/F02 auf Java-Definitionsebene; F03–F09 auf Plan-/Zustandsmaschinenebene; F17/F23 bezüglich Datenclosure und Legacyplanung; F30.

**Tests:** Auswahl, deaktivierte Finalizer ohne Trigger, geteilte Owner, Kette/Doppelrolle, Listensemantik, Zyklen, explizites F-Ziel, Finalizer-Voraussetzungen, Aktivierungsblockade, Legacyordnung. Ein kleines handcodiertes Traceorakel und begrenzte generierte DAG-/Statusfälle ergänzen die Beispiele. Keine reale Operation/Datenbank dafür nötig. Enumconsumer kompilieren; neue Fallzweige nicht still als AFTER behandeln.

**Review:** eigener neuer Plan-/Statusgegenfall; keine Requirementabnahme allein aufgrund Toposort. **Gate:** reine Regeln/Javaoberfläche und direkte Bestandsregressionen grün, unabhängiger P1-PASS. Noch keine DSL-/GUIlieferung behaupten.

### P2 – Runner, Fehlerdrain, Cancellation und Lebensdauer

Binde TaskSchedule in den vorhandenen Runner ein. Implementiere tatsächliche Start-/Terminalgrenze, wirksame Task-/Invocationkontrolle, fällige Voraussetzungen, Budgets, Ergebnis-/Fehleraggregation und globale Cleanupreihenfolge. Java-Testdefinitionen reichen für diese Phase; Groovyoberfläche folgt P3.

**Kriterien:** F04–F18 sowie F22/F23 im Runner-/Eventumfang und F30. Frühere Planverträge bleiben erhalten.

**Tests:** Owner-Erfolg/Fehler/when-false/Setup- und Konfigurationsfehler, ungestarteter Owner, zwei fällige Finalizer trotz erstem Fehler, shared F, Kette, eigene fehlgeschlagene Voraussetzung, finishJob, Cancellation vor/nach Taskstart, lokale Frist und gesamte Abschlussfrist, echte typisierte und innere Invocation unter Abschlusskontrolle, ResourceClose/Secret-/ContextClosed-Verträge, Primär-/Sekundärfehler und Resultrechte.

FakeMonotonicClock, kontrollierte Latches und eigene kleine OperationProvider nutzen; keine langen Sleeps. Die gesamte kleine betroffene Budget-/Failure-/Contextfamilie prüfen, nicht nur den Happy Path. Keine Datenbank-/FTP-/S3suite.

**Review:** mindestens eigener Fehler-/Abbruchgegenfall, der innere Invocation oder Ressourcenschluss umfasst. **Gate:** Java-Runnerpfad vollständig, jeder Kandidat terminal, Legacy-/Budgetregression grün, unabhängiger P2-PASS.

### P3 – Groovy, Includes, Pakete, öffentlicher Plan und Reports

DSLheader freigeben, Projektindex/Scopeauflösung ergänzen, Report-/Planausgabe implementieren. Alle notwendigen Produktmodule frisch aus Kandidat bauen. SCRIPT und quellenfreies JAR wirklich ausführen; introspektive Befehle ohne Bodywirkung nachweisen.

**Kriterien:** F01/F02 öffentlich, F19–F23, relevante F03–F18-Parität, F29-Beispielgrundlage und F30.

**Tests:** namengebundene Vorwärtsref in zwei Fragmenten; zwei Subprojekte mit gemeinsamem Fragment und qualifiziertem Finalizer; eigener Ressourcenroot des Finalizers; Sourcefehler im Fragmentfinalizer; Datenrechte; Typoperationfinalizer. Ein Single-JAR und ein Project-JAR aus fremdem CWD nach Entfernen des Quellbaums, mindestens Erfolg und Ownerfehler mit finalem Artefakt-/Reportinhalt. Ein echter kooperativer Cancellation-/Shutdownprozess mit Startuphandshake, Marker und Deadline. Alter Job ohne Finalizer und altes Paketformat weiterstartbar. Plantext/Graph/JSON und Ausgabe-/Fehlerexit prüfen.

**Review:** eigener Mode-/Paket-/Fehlergegenfall, nicht alle Operationen erneut paketieren. **Gate:** echte öffentliche Laufwege grün, keine nur im Testclasspath funktionierenden Pfade, unabhängiger P3-PASS. Noch keine fertige Modelerunterstützung behaupten.

### P4 – LSP, Modeler und sichere strukturierte Edits

Statische Semantik, Queries, Kantenprojektion, Vorschau, Detailseditor und native Editkette ergänzen. Vorhandene Includecontext-/Owner-/Sharedsourcegrenzen beibehalten. Keine fälschliche globale PARTIAL-/Read-only-Lösung für normale vollständig bekannte Jobs.

**Kriterien:** F24–F27; F19/F23 in Tooling; lesender/schreibender Teil von F28; F30. Runtimeplanung als Testoracle verwenden, zusätzlich konkrete unabhängige Erwartungen.

**Tests:** echter stdio-Server mit Finalizerdiagnosen, Completion, Navigation, References, Rename, Graph und Zielvorschau. Hinzufügen/Entfernen editiert den Owner und nicht den Finalizer. Shared-Footprint, stale Fragment-/Header-/Kontextänderung, generischer Setter und Batch als negative Kontrollen. Ein kleiner installierter VSIX-Ablauf mit Kante, Ownerdetails, Rename, Save und Undo/Redo; gespeicherte Quellen bytegenau prüfen. Kein vollständiger GUIkatalog.

**Review:** eigener Core-/Wiregegenfall und bei geänderter Host-/GUIlogik eine eigene kleine reale Benutzerpfadgegenprobe; bestehende kandidatengleiche umfangreichere Belege dürfen unabhängig geprüft statt verdoppelt werden. **Gate:** normale Finalizerjobs vollständig nutzbar und sicher bearbeitbar, echte Benutzerpfadnachweise, unabhängiger P4-PASS.

### P5 – Beispiel, gezielte Abschlussregression und kumulative Abnahme

Fertigstelle `examples/task-finalizers/` und aktuelle Funktionsdokumentation; überholte aktuelle „nicht unterstützt“-Aussagen korrigieren, historische Spezifikationen/Reviews bewahren. Alle F01–F30 zu konkreten Code-/Testbelegen zuordnen. Ein letzter diffbezogener Interaktionslauf am Schlusskandidaten statt pauschalem Rootclean.

Der tatsächlich in P4 gespeicherte Include-/Finalizerjob läuft unverändert in einem separaten SCRIPTprozess, bei relevanter Paketänderung zusätzlich im JAR. Ergebnisbytes, Taskfolge, Trigger, NOT_RUN-Gründe, Primärfehler und globale Cleanupreihenfolge prüfen. Letzte Korrekturen gezielt nachtesten. Unveränderte aktuelle Belege mit SHA-/Artefakt-/Inputzuordnung wiederverwenden, nicht umetikettieren.

**Kriterien:** kumulativ F01–F30. **Gate:** unabhängiger frischer Schlussreview des gesamten Feature-Deltas seit Auftragsbasis, keine offenen BLOCKER/MAJOR, keine fälligen NOT_EXECUTED, finale Produkt-/Artefaktidentität. Ergebnislabel `TASK_FINALIZERS_WITH_TOOLING_ACCEPTED`. Nicht Releasefreigabe und nicht vollständige Gradle-Kompatibilität.

## 7. Zielgerichtete Anforderungs- und Testmatrix

Die folgenden Gruppen sind reale fachliche Prüfumfänge, keine Pflicht, für jede Zeile eine neue Klasse oder jeden Modus zu duplizieren. Die in §5 vorgeschlagenen neuen Klassennamen sind vor der Implementierung nicht vorhanden. Tatsächliche Testnamen in P0/je Phase erfassen.

| Gruppe | Pflichtfall / entscheidende Assertion | Verträge | Hauptphase |
|---|---|---|---|
| D01 | String/TaskRef/Liste; Vorwärtsref; fehlend/fremd/doppelt/Selbstlink; Task und Action | F01–F02 | P1/P3 |
| S01 | A wählt F und dessen D, aber keine unselektierten Owner; AFTER selektiert nicht | F03 | P1 |
| S02 | A startet/endet vs A NOT_RUN: F nur im ersten Fall; when-false zählt erreicht | F04/F10 | P1/P2 |
| S03 | A und B teilen F; A Fehler, B NOT_RUN; F einmal, keine verfrühte Freigabe | F05 | P1/P2 |
| S04 | F direktes Ziel; F auch normal benötigt; Kette F→G; keine Wiederholung | F06 | P1/P2 |
| S05 | Finalizerliste ohne implizite Reihenfolge; explizites AFTER; echte fehlgeschlagene Datenvoraussetzung blockiert | F07/F17 | P1/P2 |
| S06 | Selbst-/gemischter Zyklus und azyklische Aktivierungsblockade vor jeder Nebenwirkung | F08 | P1/P3 |
| S07 | Fehler stoppt Normalarbeit, aber nicht aktive Finalizer/D; später nötiges D nicht verfrüht NOT_RUN | F09 | P1/P2 |
| R01 | Ownerbody, Prädikat, Konfiguration, Setup nach RUNNING und ResourceClose schlagen jeweils passend fehl | F10/F16/F18 | P2 |
| R02 | F1 Fehler, F2 läuft; F2 AFTER F1 erlaubt; F2 dependsOn F1 blockiert | F07/F09/F16 | P2 |
| R03 | Typisierter F und innere Operation nach Cancellation führen wirklich aus, alte Flags verfälschen Outcome nicht | F11/F12/F15 | P2 |
| R04 | Cancellation vor Ownerstart aktiviert nichts; während Owner aktiviert; ursprünglicher Grund erhalten | F04/F12/F16 | P2 |
| R05 | finishJob im Owner/F; gefangenes internes Signal; kein Wiederstart normaler Arbeit | F13 | P2 |
| R06 | Ein einziges Completionbudget, lokaler Ftimeout, weiterer F, Budgetende und anschliessende Cleanupversuche | F14/F15/F18 | P2 |
| R07 | Primärfehler vorher bleibt, sekundäre Finalizer-/Closefehler erhalten, CANCELLED/NO_DATA/FAILED korrekt | F16 | P2 |
| R08 | `result`/`resultOptional` nur Datenvorgänger; FINALIZED_BY/AFTER allein reichen nicht | F17 | P2/P3 |
| R09 | Keine doppelten Invocation-/Taskstarts; Ownerressourcen vor F geschlossen; ContextClosed/Secrets gültig | F10/F18/F22 | P2 |
| M01 | Fragmentforwardrefs; Sharedsource in zwei Projektscopes; Finalizerressourcen relativ eigenem Besitzer | F19 | P3 |
| M02 | Echte Single-/Project-JARs ohne Sourcebaum; Script/JAR gleiche Wirkung und Fehlerquelle | F20/F21 | P3 |
| M03 | `check/plan/help` ohne Task-/Finalizer-/Cleanupwirkung; vorhandene Metadaten unverändert bedienbar | F21 | P3 |
| M04 | Report/Events/Graph korrekte Kantenrichtung, deklarierte vs aktive Owner, keine offenen PENDING | F22 | P3 |
| M05 | Echte Prozess-Cancellation nach beobachtetem Start; Fmarker, Cleanupmarker, Ende/Report mit Ursache | F12/F14/F28 | P3 |
| B01 | Kein Finalizer: gleicher Bestandsplan, Status, Exit, Reihenfolge, alte Pakete | F23 | P1–P3 |
| T01 | Typisierte statische Include-Fixture COMPLETE; dynamische Gegenkontrolle PARTIAL; keine Ausführung | F24 | P4 |
| T02 | Finalizernavigation/Completion/References; Parameter nicht als Ziel; echte Quellrange | F24 | P4 |
| T03 | Eigentlicher Erfolgsplan und Toolingpreview gleiche Kandidaten/Beziehungen, eigenständiges Traceorakel | F25/F08 | P4 |
| T04 | Finalizer hinzufügen/entfernen schreibt Owner; Rename aktualisiert Links; Delete referenziertes F blockiert | F26 | P4 |
| T05 | Shared-/Partial-/Stale-/Batchumwege abgelehnt; erlaubte lokale Änderungen erhalten | F26/F27 | P4 |
| T06 | Echter stdio-Server und installierter VSIX, Save/Undo/Redo, geeignete Modell-/Artefaktversion | F27/F28 | P4 |
| E01 | Gespeicherte unveränderte GUI-Fixture separat ausgeführt; keine Tasks während IDEbedienung | F28 | P5 |
| E02 | Beispiele und Anleitung korrekt, Budget-/Ressourcen-/Ergebnisgrenzen verständlich | F29 | P5 |
| Q01 | Phasenreview selbst testend, neue Gegenprobe, verifizierte Identität; alle Pflichten belegt | F30 | jede Phase |

### 7.1 Kriterienmenge des Reviewrequests

Eine Anforderung kann in einer frühen Phase nur auf einer bestimmten Ebene abgenommen sein. Das ist im Request ausdrücklich zu benennen, statt vorzeitig vollständige GUI-/JAR-Wirkung zu behaupten.

| Phase | Zu prüfende IDs | Umfang |
|---|---|---|
| P0 | W01–W04 | Workflow/Baseline, keine Featurelieferung |
| P1 | F01–F09, F17, F23, F30 | Java-Definition, reine Planung/Scheduling und Datenclosure |
| P2 | F04–F18, F22–F23, F30 | echte Runner-/Context-/Operationswirkung; Reportserialisierung erst P3 |
| P3 | F01–F03, F08, F16–F23, F29–F30 | öffentliche DSL-/CLI-/Paket-/Reportwirkung, Beispielgrundlage |
| P4 | F19, F23–F28, F30 | statische und schreibende IDE-Unterstützung, native Probe |
| P5 | F01–F30 | kumulative Gesamtabnahme |

Kriterien nicht dynamisch nach Testergebnis verkleinern. Korrekturreview prüft dieselbe Phase gegen dieselbe Phasebase. Neue Tests dürfen zusammengehörige mehrere IDs abdecken; keine starre Mindesttestzahl und kein unnötiges Kreuzprodukt.

## 8. Risikobasierte Testausführung

### 8.1 Grundprinzip

Während eines Fixes nur die betroffenen Fälle/Familien. Vor dem Phasengate alle neuen/berührten Tests dieser Phase und ihre unmittelbar betroffenen Regressionen. Bei Scheduler-/Kontrolländerungen sind bestehende Plan-/Budget-/Failure-/Contexttests relevant und nicht aus Sparsamkeit wegzulassen. Unbeteiligte Fachdienste sind es nicht.

Am Recherchepin wurden u. a. `ExecutionPlannerTest`, `JobDefinitionBuilderTest`, `FailureArbiterTest`, `BudgetPrecedenceTest`, `ContextAccessTest`, `CloseStackTest` und `FakeMonotonicClock` im Runner-Testverzeichnis nachgewiesen. Inhalt und passende Methoden vor Selektion lesen. Neue Gruppen können z. B. `FinalizerPlanTest`, `TaskScheduleTest`, `TaskFinalizerRunnerTest`, `FinalizerControlTest`, `GroovyFinalizerTest`, `FinalizerJobIT`, `FinalizerServerIT` heissen; das sind vorgeschlagene Namen.

Nicht automatisch erforderlich: alle 44 Fachoperationen, gesamte INTERLIS-/GeoTools-/Datenbank-/FTP-/S3matrix, sämtliche alten GUIgates, Performancekampagne, wiederholte Root-`clean verify`-Läufe. Der kleine echte Prozess-/LSP-/VSIX-Nachweis bleibt trotzdem fällig. Ein breiter compiler-/classpath-/Schemaeingriff erweitert gezielt den Umfang; begründen, nicht heimlich alle Tests einschalten.

### 8.2 Belastbare Ausführung statt Scheingrün

Beispiel, anhand tatsächlicher Testnamen und POMs anzupassen:

```sh
./mvnw -B -pl gretl-runner -am \
  -Dtest=TaskFinalizerRunnerTest,FinalizerControlTest,FailureArbiterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`failIfNoSpecifiedTests=false` dient nur unbeteiligten Reactorabhängigkeiten. Im Zielmodul frische XMLs für die wirklich ausgewählten Fälle, Testanzahl > 0, keine Failures/Errors oder unerklärten Skips prüfen. Ein Tippfehler im Filter darf nicht PASS ergeben. Failsafe-ITs mit tatsächlicher `-Dit.test`-Selektion und verify-Bindung starten, ohne unbeabsichtigt alle Surefiretests des gesamten Reaktors zu wiederholen.

Artefaktbootstrapping mit `-DskipTests` kann notwendig sein, ist aber ausdrücklich kein Testnachweis. Produkt-/Testmodule aus aktuellem Kandidat bauen, nicht gleichnamige fremde SNAPSHOTs verwenden. Eigener Maven-Reactor-/Repositorybereich für parallele Feature-/Reviewworktrees oder gleichwertige belegte Isolation. Externe Dependencies dürfen regulär gecacht werden.

Testlogs und XMLs vor `clean` extern sichern. `tee` darf den Exitstatus nicht verdecken. Bei wiederverwendeten Belegen Kandidaten-/Artefakt-/Inputhash und unveränderten betroffenen Code nachweisen; nie eine alte Ausführung als neue ausgeben. Dokumente/Tests nicht passend abschwächen, um den Gatevalidator zufriedenzustellen.

### 8.3 Zeit-/Abbruchtests

FakeMonotonicClock für Deadline-/Prioritätslogik; Latches/Marker für Prozessbereitschaft. Ein Prozess-Abbruch erst nach nachgewiesenem Start des Owners, damit kein falscher NOT_RUN-Fall getestet wird. Externe obere Testdeadline und eigenes Prozessgruppen-Cleanup; nur eigene Kinder beenden. Nicht jeden Timeoutfall als realen 30-Sekunden-Lauf ausführen.

Im Abschlussmodus eine echte vorhandene oder kleine Testoperation ausführen, die Budget/Cancellation und innere Invocation tatsächlich benutzt. Eine reine `println`-Action beweist nicht, dass OperationInvoker und ResourceClose funktionieren. Testorakel prüfen Primärursache, Sekundärfehler, Ereignisanzahl, Resultrechte und ausbleibende normale Arbeit.

### 8.4 GUI-/Hostnachweise

Bestehenden installierten VSIX-/Workbenchweg im isolierten Profil verwenden. Kein neues GUIframework. Eine positive zusammenhängende Sequenz und ein kleiner negativer Shared-/Stale-/Owner-Editfall reichen, soweit sie die veränderten Wege abdecken. Reale Benutzeraktionen, aktuelle Server-/VSIXdigests, Quell-/Bufferzustand und relevante Bilder festhalten. Lesende Inspektionsbefehle ergänzen, ersetzen aber nicht echte Bedienung.

Bei unveränderter UI nach einem reinen Runnerfix ist nicht wieder derselbe ganze GUIfall fällig. Bei geändertem Headeredit oder Kontextguard hingegen die passende kleine Benutzerpfadregression. Im Review unabhängig prüfen, welche Wiederverwendung anhand tatsächlicher Bytes zulässig ist.

### 8.5 Ergebniszustände

`PASS`: fällige Prüfung tatsächlich erfolgreich. `FAIL`: beobachteter Fehler. `NOT_EXECUTED`: fällig, aber nicht durchgeführt; blockiert die Phase. `NOT_REQUIRED`: begründet nicht fällig; keine fingierte Ausführung. Infrastrukturfehler sind keine erfolgreich getestete Fachablehnung. Testzahlen nur aus aktuellen Rohberichten übernehmen.

## 9. Autonomer OpenCode-Review

### 9.1 Vorhandene Mechanik zuerst prüfen

Es existiert inzwischen `tools/include-tooling/review.py` mit `test_review.py`. Der gelesene Helfer besitzt funktionierende Bausteine für frischen Worktree, OpenCode2-Start, vorhandene Anmeldung, Modellzuordnung, Policy, Session-/Testnachweise und Verdictprüfung. Er ist aber an `include-tooling`, dessen drei Dateien, Kriterien und P0–P4 gebunden. `tools/gretl-tooling/review.mjs` bindet wiederum den älteren Toolingauftrag.

**Nicht unverändert den falschen Auftrag starten und nicht dessen historische Gates umbiegen.** Vorhandene lokal verfügbare erfolgreiche Transportmechanik wiederverwenden; einen kleinen auf `task-finalizers` begrenzten Adapter erstellen oder geeignete Funktionen ohne Importnebenwirkungen nutzen. Keine komplette alte Evidenceplattform kopieren und kein neues universelles Reviewprodukt bauen.

Der vorhandene Pythonweg ist zulässig und bevorzugt, wenn er lokal funktioniert. Eine tatsächlich verfügbare Pythonversion oder vorhandene venv verwenden; keine systemweite Pythonänderung, keine Pythonentfernung und keine neue Pflichtinstallation. Adapterspezifische Tests auf Auftrag/SHA/Digest/Session/fehlende Tests begrenzen.

### 9.2 Installation, Modell und Zugang

Tatsächliche lokale Befehle prüfen:

```sh
command -v opencode
opencode --version
opencode run --help
opencode auth list --format json
opencode models
```

Eine Desktopversion 2.x beweist nicht dieselbe CLI auf PATH. Vollständigen tatsächlich verwendeten Binarypfad erfassen. Vorhandene gespeicherte DeepSeek-Anmeldung nutzen; **kein zusätzlich vorgeschriebener API-Key in einer ENV-Variable**, keine Authdatei lesen/ausgeben, kein Credentialexport.

Beim Quellenabruf am 5. November 2026 ordnet die offizielle DeepSeek-Seite `deepseek-flash` der Version `DeepSeek-V4.1-Flash-1017` zu. Beim tatsächlichen Run erneut prüfen. Ein lokaler Wert `deepseek/deepseek-flash` ist nur mit tatsächlicher Provider-/Endpoint-/Sessionzuordnung gültig. Kein stiller Wechsel zu Pro, anderem Aliasstand oder Proxyanbieter. Selbstauskunft des Modells genügt nicht.

Aktuelle offizielle OpenCode2-Dokumentation und lokale Hilfe verwenden. Keine ungeprüften V1-Serverendpunkte oder geratenen Permissionformate. `--standalone` nur, wenn lokal unterstützt und der private Prozess vorhandene Credentials tatsächlich nutzen kann. Keine laufende Benutzersession kapern oder globalen Server neu konfigurieren.

### 9.3 Verbindlicher Ablauf jedes Phasenreviews

1. Codex führt die ausgewählten Feature-/Regressionstests aus und schreibt einen knappen neutralen `review/Px-request.md` mit Scope, festen Kriterien, Phasebase, Commands, Belegen und offenen Grenzen. Kein vorgefülltes Urteil und keine vorgegebene Reviewer-Gegenprobe.
2. Code/Tests/stabilen Request lokal committen. Erst **nach** Commit vollen Kandidaten-SHA lesen; keinen eigenen künftigen SHA in denselben Commit schreiben.
3. Externen Handoff mit Auftrag, Version, Digest, Basis, Phasebase, Kandidat, Worktree-/Evidencezielen, erlaubten Prüfungen und Modellmetadaten erzeugen.
4. Frischen detached Reviewworktree genau am Kandidaten erstellen, neue OpenCode-Session ohne Continue/Fork/alte Gesprächshistorie. Ein neuer Worktree allein ist noch keine neue Session und umgekehrt.
5. Begrenzte effektive Policy erlaubt Lesen, ausgewählte lokale Tests/Builds, externe Gegenproben/Reports ohne Nutzerklicks. Sie verbietet fremde Worktrees, getrackte Produkt-/Erwartungsänderungen, Credentialexport, Push/Merge/Release und produktive Jobs. Shellrechte sind keine Sandbox; getrackte Bytes zusätzlich prüfen. Kein pauschales `--auto` ohne bewiesene Policy.
6. Reviewer liest Plan, eigenen Prompt, Request und Code selbst; führt risikotragende Tests selbst aus, entwickelt eigene Probe, beurteilt alle fälligen Ziele und liefert Originalbericht plus JSON. Codex wartet auf tatsächlichen Prozess-/Sessionabschluss, sichert Stream/Reports und prüft Identität und Ergebnis maschinell.
7. Bei Findings korrigiert **Codex** den Code, ergänzt nötige Regression, testet gezielt, committet neue SHA und startet einen neuen unabhängigen Review. Phasebase unverändert. Kein Selbstreview und kein Wegprompten eines reproduzierbaren Fehlers.
8. Erst gültiges `ABGENOMMEN` schliesst die Phase; danach ohne erneuten Bestätigungsklick weiterarbeiten.

Nach wenigen sinnvollen Versuchen bei echtem Infrastrukturproblem `BLOCKED_REVIEW_INFRA` mit konkreter Ursache setzen; keine Endlosschleife und keine Weiterarbeit an der nächsten Phase. Fehlende GUI-/Toolfähigkeit früh sichtbar machen, aber nicht den Nutzer als regulären Reviewtransport einsetzen. Vorhandene legitime mechanische UI-Gateways dürfen genutzt werden; der Reviewer wählt Hypothese/Aktionen/Assertions selbst.

### 9.4 Handoff und Urteil

Digest über eine nach rootrelativem Pfad sortierte Liste der SHA-256-Werte der **drei aktiven normativen Dateien** bilden. Die Eingabeliste sichern; SOURCE_BASELINE und dieses Paketprüfergebnis sind keine versteckten neuen Kriterien. Kriterienmenge exakt aus §7.1. Reportstatus und tatsächlich gemeldete Modelle gegen externe Prozess-/Sessionmetadaten prüfen.

Beispielstruktur, **kein vorgefülltes Ergebnis**:

```json
{
  "assignment_id": "task-finalizers",
  "spec_version": "1.0",
  "phase": "P2",
  "assignment_base_sha": "ACTUAL_BASE",
  "phase_base_sha": "ACTUAL_PHASE_BASE",
  "head_sha": "ACTUAL_CANDIDATE",
  "reviewed_sha": "ACTUAL_CANDIDATE",
  "requirements_digest": "ACTUAL_DIGEST",
  "review_worktree": "ACTUAL_ABSOLUTE_PATH",
  "opencode_version": "ACTUAL_2_X_VERSION",
  "provider_model": "ACTUAL_PROVIDER_MODEL",
  "session_id": "ACTUAL_FRESH_SESSION",
  "verdict": "ABGENOMMEN|NACHARBEIT|NICHT_PRUEFBAR",
  "criteria": [
    {"id": "F04", "status": "fulfilled", "evidence": ["ACTUAL_REPORT"]}
  ],
  "executed_checks": [
    {"command": "ACTUAL_COMMAND", "exit_code": 0, "assertion": "ACTUAL_ASSERTION", "evidence": ["ACTUAL_LOG"]}
  ],
  "reused_evidence": [],
  "counterprobes": [
    {"authored_by_reviewer": true, "executed": true, "command": "ACTUAL_COMMAND", "exit_code": 0, "assertion": "ACTUAL_ASSERTION", "evidence": ["ACTUAL_LOG"]}
  ],
  "findings": [],
  "required_not_executed": []
}
```

Das einzelne F04-Objekt demonstriert nur die Form; der echte Request verlangt die **vollständige** Phasenmenge. Alle Platzhalter sind im echten Ergebnis unzulässig. Der Validator weist fremde Auftrags-ID, falsche Version, andere SHA, falschen Digest, doppelte/fehlende Kriterien, alte Session, fehlende Test-/Gegenprobenausführung und vorgefüllte Reports zurück.

`ABGENOMMEN`: alle fälligen Ziele erfüllt, eigene echte risikotragende Tests und Gegenprobe, keine offenen BLOCKER/MAJOR oder fehlenden Pflichtprüfungen, unveränderte geprüfte Produktbytes. `NACHARBEIT`: konkreter Vertragsdefekt bzw. notwendiger fehlender Nachweis. `NICHT_PRUEFBAR`: notwendige Umgebung/Identität nicht verfügbar; kein PASS aus plausibler Lektüre. Exitcode 0 oder Wort PASS alleine reicht nie.

### 9.5 Praktischer CLI-Start nach Preflight

```bash
(
  cd "$REVIEW_WORKTREE"
  "$OPENCODE_BIN" run \
    --model "$REVIEW_MODEL" \
    --format json \
    --file "$REVIEW_HANDOFF" \
    --file docs/implementation/task-finalizers/OPENCODE_REVIEW_PROMPT.md \
    "Pruefe den bezeichneten Phasenkandidaten gegen EXECUTION_PLAN.md. Lies den gesamten Phasenumfang, fuehre gezielte Tests und eine eigene neue Gegenprobe aus. Keine Produktfixes. Liefere Originalbericht und maschinenpruefbares Urteil."
) >"$REVIEW_EVIDENCE/events.jsonl" 2>"$REVIEW_EVIDENCE/stderr.log"
```

Alle Variablen aus der tatsächlichen Umgebung setzen. Agent-/Standaloneoptionen nur nach lokaler Hilfe und wirksamer Policy ergänzen. Prozessbaum, Completion und getrackten Zustand prüfen. Reports nicht in `target/` als einzigen Speicherort belassen. Bestehende passende Automation darf den gleichen Vertrag ausführen.

## 10. Lieferumfang, Git und Definition of Done

Versionskontrolliert: Featurecode/-tests, aktuelle Anleitung/Beispiel, kompakte WORK_QUEUE, stabile Requests und kleine nötige Adapter. Originalreviewlogs/Sessionexports/Buildberichte extern mit Hashbezug sichern statt das Repository mit unübersichtlichen Dateimengen zu füllen. Keine Secrets oder produktiven Daten.

Phasenstatus `PLANNED → IMPLEMENTED → TESTED → REVIEW_PENDING → REVIEWED_ACCEPTED`; alternative Blocker `BLOCKED_TEST`/`BLOCKED_REVIEW_INFRA`. Ein reiner Statuscommit darf auf den vorher tatsächlich geprüften Produktcommit verweisen, ist aber kein neuer Produkt-PASS. Am Schluss exakten geprüften Code-/Doc-SHA und gegebenenfalls späteren Statuscommit getrennt nennen.

**Fertig erst, wenn** F01–F30 im fälligen Gesamtumfang erfüllt sind, alle sechs Phasengates unabhängig akzeptiert wurden, keine fällige Prüfung fehlt und der Schlussreview das gesamte neue Feature bewertet hat. Der Nutzer erhält getestete Beispielaufrufe, Branch-/Basis-/Kandidaten-SHAs, tatsächliche Review-/Testbelege und dokumentierte Grenzen. Kein Push/Merge/Release ausführen.

## 11. Quellen und Herkunft

Die konkreten gelesenen Dateien und Primärquellen stehen mit gepinnten Links in [SOURCE_BASELINE.md](SOURCE_BASELINE.md). Die dort beschriebenen Codebefunde ersetzen keine Produkttests. Die F-Verträge, Ablaufdetails und neuen Klassenentwürfe dieses Plans sind **neue Designentscheidungen dieses Auftrags**, nicht behaupteter Bestandscode.

Übernommen aus den erfolgreichen bisherigen Markdown-Aufträgen: phasenweise Umsetzung, konkrete Verträge, fokussierte Tests, Kandidatencommit vor unabhängiger Prüfung, frische OpenCode-Session, eigene Gegenprobe, maschinengeprüfte Identität und autonome Fortsetzung. Nicht übernommen: fachfremde Migrationsaufträge, pauschale alte Vollgates oder eine erneute Durchführung abgeschlossener Meilensteine.
