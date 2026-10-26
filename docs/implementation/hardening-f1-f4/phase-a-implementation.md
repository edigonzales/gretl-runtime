# Phase A: Implementierung F1–F3

Dieser Bericht gehört zum Produkt-/Testkandidaten, der ihn erstmals enthält.
Der konkrete geprüfte Commit und Gate-Status werden im separaten
Qualifikations- und Reviewnachweis festgehalten. Phase B ist vor Gate A gesperrt.

F1: `WorkspaceManager` legt Runordner exklusiv an und prüft den Besitz vor
Unterbereichsnutzung und Retention. Vorhandene Roots und Berichtseltern behalten
ihre Rechte. Neue Run-/Task-/Invocation-/Cleanup- und verschachtelte
Arbeitsverzeichnisse entstehen unter POSIX mit `0700`; Diagnose- und temporäre
Berichtdateien mit `0600`, jeweils vor dem Schreiben. `workPath` prüft seine
bekannten Verzeichnisidentitäten und verweigert Symlinks im erzeugten Pfad.
Es wird kein allgemeines Dateisystem- oder Isolationsframework eingeführt.

F2: Predicate, Requestkonfiguration, Handlerfactory, Handler, Rückkehr und
Ressourcenfreigabe beobachten dasselbe geerbte lokale Budget. Kontrolliert
erkannte Cancellation bleibt gespeichert, auch wenn Scriptcode das Signal
fängt. Alle registrierten Freigaben werden weiter versucht. Eine frühere echte
Exception bleibt gegenüber späterem Cancel primär; die Identität einer
unverändert weitergereichten Invocationexception bewahrt diese Reihenfolge
auch im Eltern-Task. Bewusst behandelte Fachfehler erzeugen keine Fehlerpflicht.
Job-Cleanup verwendet weiterhin ein eigenes gemeinsames 30-Sekunden-Budget.
Interrupts werden für den Abschluss geklärt und vor Rückgabe wiederhergestellt.

F3: Gewöhnliche Workspacefehler werden nach Runbeginn als kontrollierte
Fehler abgeschlossen. Noch nicht gestartete Tasks erhalten `NOT_RUN`.
Cleanup benötigt seinen eigenen Ordner erst bei tatsächlichem `workPath` und
kann ohne Dateien auch nach beschädigtem Workspace laufen. Primärursache und
Folgefehler bleiben erhalten. Die gemeinsame Job-Runtime schreibt den Bericht
vor dem einmaligen endgültigen Terminalereignis; ein später Berichtfehler
wird sicher gemeldet und führt bei sonstigem Erfolg zu Code 10.
Bekannt ungültige Startverzeichnisse bleiben gemäss OPS-010 Code 2 ohne
Taskarbeit oder Job-Cleanup.

| Finding | Regressionen | Selbst ausgeführte erste Reproduktion |
| --- | --- | --- |
| F1 | `WorkspaceOwnershipTest`, `WorkPathOwnershipTest`, `DiagnosticFilePermissionsTest`, `RunReportWriterTest` | `f1-red.log`: 6 Verhaltensfehler, keine Testfehler/Skips |
| F2 | `LocalBudgetRegressionTest`, `BudgetPrecedenceTest` | `f2-red.log`: 11 Verhaltensfehler bei 12 Tests, keine Testfehler/Skips |
| F3 | `SetupFailureTest`, `SetupCompletionTest` | `f3-red.log`: ungefangene Workspacefehler und ausgelassenes Cleanup |
| F1–F3 Prozesse | `HardeningLocalJobIT`, Fixture `jobs/hardening` | reguläre JAR-Paketierung und Scriptlauncher werden im Standardbuild geprüft |

Die engen Budgettests verwenden `FakeMonotonicClock`. Prozessfälle warten
kontrolliert auf das tatsächliche monotone Budget; zufällige Sleeps sind kein
Timeoutorakel. I/O-Fehler entstehen durch reguläre Dateien an benötigten
Verzeichnispositionen und durch gezielten Austausch bekannter Pfade.

Zwischenstände sind keine Freigaben: `f2-green.log` war trotz seines
historischen Dateinamens noch rot; `f1-runner-logging-suite.log` und
`phase-a-unit.log` enthalten damals noch offene Integrationsfehler.
`f3-runtime-red.log` verwendete einen zu weit gehenden, später verworfenen
Startfehler-Orakel und zählt nicht als gültiger Regressionsnachweis.
`phase-a-components-revision-2.log` dokumentiert einen korrigierten Testimport.
Die endgültigen grünen Nachweise ersetzen diese Entwicklungsläufe nicht
stillschweigend, sondern werden separat mit Kandidatenidentität abgelegt.

Ein zusätzlicher externer Gegenbeweis (`finish-close-probe.log`) zeigte am
Zwischenkandidaten `9fbf2226ff5b3c0fb92ec3932210141b4290a4d9`, dass
`finishJob` einen früheren Closefehler gegenüber späterer Cancellation
verdeckte. `BudgetPrecedenceTest` enthält diesen Fall nun ausdrücklich.
Der bestehende Arbiter-Test für Cancellation vor Close stellt diese Reihenfolge
jetzt mit dem tatsächlich beobachteten Kontrollsignal dar; Status- und
Nebenursachenassertion bleiben erhalten. Der vollständige Standardbuild dieses
Zwischenkandidaten war grün, ist aber keine Freigabe des später korrigierten
Standes. Dessen vollständige Nachweise werden erneut erhoben.

Die Textdiagnoseprüfung für Berichtfehler fordert jetzt ausdrücklich `--lang de`
an. Das ersetzt ihre frühere Abhängigkeit vom implizit deutschen Bootstrap-
Formatter; Exitcode- und Meldungsassertion werden nicht abgeschwächt. Das neue
strukturierte Berichtfehlerereignis besitzt Titel in beiden Meldungskatalogen.

Die Runtime beendet nicht kooperierende Bibliotheksaufrufe nicht hart.
Bereits erfolgte fachliche Wirkungen werden nicht rückgängig gemacht. POSIX-
Rechte gelten auf entsprechenden Dateisystemen; andere Dateisysteme nutzen
deren native Zugriffsregeln. Die Pfadprüfungen ersetzen keine Sandbox gegen
beliebigen vertrauenswürdigen JVM-Code oder feindliche gleichzeitige OS-Zugriffe.
