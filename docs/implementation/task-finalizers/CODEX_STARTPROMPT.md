# Startauftrag für Codex: GRETL `finalizedBy`

**Fassung 1.0 · 5. November 2026 · Auftrag `task-finalizers`**

> Diesen Text als ausdrücklichen Implementierungsauftrag an Codex übergeben. Repository: `edigonzales/gretl-runtime`. Die drei normativen Dateien liegen unter `docs/implementation/task-finalizers/`. Der Auftrag betrifft weder eine erneute Include-Implementierung noch die Fortsetzung eines alten Toolingplans.

Du bist **Codex, der verantwortliche Implementierer**. Implementiere taskgebundene Finalizer über **`finalizedBy`** gemäss [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Liefere konkreten Produktcode, gezielte echte Tests, lokale Commits und nach **jeder Phase** eine autonome unabhängige Prüfung durch **OpenCode 2.x mit DeepSeek-V4.1-Flash-1017**. Der Reviewer prüft Code **und Zielerreichung**, führt selbst relevante Tests und eine neue eigene Gegenprobe aus. Keine weitere allgemeine Planung anstelle der Umsetzung.

## 1. Ausgangsbasis und Ziel

Recherchepin: **`31ca88ef4b4a5a4a219907156886f1da19adc644`** auf `main`. Dieser Stand enthält Runtime-Includes und die als abgeschlossen dokumentierte Include-Tooling-Erweiterung. [SOURCE_BASELINE.md](SOURCE_BASELINE.md) trennt die gelesenen Codebefunde von den neuen Designentscheidungen. Neueren Mainstand nach tatsächlicher Deltaprüfung verwenden; der Pin ist keine Reset-Anweisung.

Eigener Branch vorzugsweise `codex/task-finalizers`, eigener Worktree. Schon vorhandenen Featurebestand zuerst lesen und erhalten. Fremde Branches/Worktrees und uncommittete Dateien nicht verändern, stashen, löschen oder umstellen. Kein Push, Release, Merge nach Main oder Ausführen produktiver Jobs.

Zieloberfläche:

```groovy
action('importData', finalizedBy: 'release') {
    println 'Import'
}
action('release') {
    println 'Importbezogene Abschlussarbeit'
}
cleanup('jobEnd') {
    println 'Globale Abschlussarbeit'
}
defaults 'importData'
```

Finalizer sind **normale benannte Tasks/Actions**, nicht globales Cleanup, nicht Closure-only-Callbacks und keine zweite Runnerengine. Vollständiger Auftrag umfasst Planung, Ausführung, Fehler/Cancellation, SCRIPT/JAR, Reports sowie Texteditor und Modeler. Keine neue Parallelisierung, Retryengine oder `shouldRunAfter`.

## 2. Sofortige Schritte

1. `pwd`, Gitstatus, HEAD, Worktrees, aktuelle lokale/Remote-Refs, JDK 25, Mavenwrapper und relevante lokale Testwerkzeuge prüfen. Fremde Änderungen erhalten.
2. Root-`AGENTS.md`, diese Datei, Plan und [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) lesen. Historische Spezifikationen/Reviews sind Hintergrund, keine erneut auszuführenden Aufträge.
3. Tatsächliche Main-/Featurebasis, enthaltene Include-/Toolingfunktionen und Delta zum Recherchepin ermitteln. Eine noch ausstehende alte Toolingfreigabe nicht erfinden.
4. `WORK_QUEUE.md` knapp beginnen: Phase, Basis/Kandidat, erforderliche Testauswahl, Originalbelege, Findings und nächste konkrete Arbeit. Kein neues grosses Kriterien-/Evidenceprodukt.
5. Kleine bestehende Plan-/Failure-/Budgetbaseline und einen vorhandenen Include-Toolingpfad tatsächlich ausführen. Fehlendes `finalizedBy` als erwartete Red-Probe feststellen oder inzwischen vorhandenen Code gegen den Vertrag prüfen. Runtime-`apply` existiert bereits.
6. Vor grösserer Implementierung den realen autonomen OpenCode-Reviewweg beweisen: neue Session, isolierter Kandidatenworktree, gewünschtes Modell, echte Testausführung, eigene Gegenprobe und prüfbarer Abschluss. Ein Modellping reicht nicht.

## 3. Phasen und harte Gates

| Phase | Ergebnis |
|---|---|
| **P0** | Geeignete Basis, kleine reale Baseline und funktionierender autonomer Reviewer. |
| **P1** | Taskbeziehungen, Java-Definitionen, Kandidatenauswahl, Aktivierungs-/Ownerbarrieren und reine Schedulingregeln. |
| **P2** | Tatsächliche Runnerausführung einschliesslich Fehlerdrain, Cancellation, Budgets, inneren Operationen und Ressourcen. |
| **P3** | Groovyheader, Include-/Subprojektscopes, echte SCRIPT-/JAR-Prozesse, Introspektion, Events und Reports. |
| **P4** | Statische Analyse, Navigation/Completion, Modelerkante/-vorschau sowie sichere native Edits. |
| **P5** | Beispiel und Anleitung, fokussierte Interaktionsregression und kumulative unabhängige Schlussabnahme. |

Pro Phase: reproduzierende Probe/Vertragstest → minimale Implementierung → notwendige Regressionen → lokaler Kandidatencommit → **frischer unabhängiger OpenCode-Review am exakten SHA** → Findings durch Codex korrigieren, gezielt testen, neue SHA, neuer Review. Erst nach gültigem `ABGENOMMEN` nächste Phase. `NACHARBEIT`/`NICHT_PRUEFBAR` erlauben keinen Phasensprung. Nach gültigem Gate autonom fortsetzen, nicht nach jeder Phase den Nutzer um Erlaubnis bitten.

Korrekturreview bewertet die ganze Phase gegen ihre unveränderte Phasebase, führt aber nur die potenziell betroffenen Prüfungen neu aus. Schlussreview betrachtet das gesamte Feature seit Auftragsbasis, nicht bloss den letzten Dokucommit.

## 4. Semantik, die du nicht vereinfachen darfst

**F01–F30 des Plans sind verbindlich.** Besonders:

- `A finalizedBy F` ist eine eigene Relation **A → F**. Die Deklaration steht am Owner A. `dependsOn`-/`after`-Mutationen stehen dagegen am Consumer. Kein Richtungsfehler in Plan, Diagnose oder Modeleredit.
- Statische Auswahl und tatsächliche Aktivierung trennen. Ein erreichter Owner aktiviert nach Terminalisierung seine Finalizer auch bei FAILED/CANCELLED/SKIPPED. NOT_RUN aktiviert nichts.
- Geteilte Finalizer höchstens einmal pro Run und erst nach allen **ausgewählten** Ownern; nicht von unausgewählten Ownern blockieren lassen. Ketten und Doppelrollen sind unterstützt.
- Finalizer-Voraussetzungen nur bei tatsächlichem Bedarf ausführen. Fehler stoppt normale Arbeit, aber nicht aktivierte Finalizer oder benötigte Abschlussvoraussetzungen. Zukünftig noch notwendige Voraussetzungen nicht voreilig NOT_RUN markieren.
- Listenreihenfolge ist keine neue Kante. `after` verleiht keine Datenrechte; `finalizedBy` ebenso wenig. Explizites `F dependsOn A` bleibt eine echte Voraussetzung und blockiert F bei Fehler von A; diese Zusatzbedingung nicht still ignorieren.
- Kombinierte Zyklen **und aktivierungsbedingte Blockaden** vor Nebenwirkungen erkennen. Der Plan enthält ein konkretes azyklisches Deadlockbeispiel. Ein gewöhnlicher Toposort reicht nicht.
- Ein gemeinsamer Runner und normale Task-/Operationspipeline. Eigene `when`, Tasktimeout, innere Invocation und ResourceClose müssen auch im Abschlussmodus funktionieren.
- Alte globale Cancel-/Finishflags dürfen fällige Abschlussarbeit nicht sofort erneut abbrechen oder ihr erfolgreiches Outcome verfälschen. Die wirksame Kontrolle bis in `BaseContext`, `OperationInvoker` und `FailureArbiter` weiterreichen; Runursache trotzdem erhalten.
- Bei normalem Lauf und rein fachlichem Fail-fast gelten bestehende Run-/Taskfristen. Nach Cancellation/Finish ein **einmaliges gemeinsames completionBudget** für Abschlussarbeit, globale Cleanups und vorhandenen Shutdownabschluss. Kein frisches Budget pro Finalizer. Grenzen kooperativer Cancellation ehrlich dokumentieren.
- Ursprünglicher Fehler bleibt primär. Weitere Finalizerfehler sind sichtbar und verhindern keinen unabhängigen fälligen Finalizer. Bestehende CANCELLED-/NO_DATA-/FAILED-Prioritäten gemäss Plan erhalten.
- Resultzugriff bleibt `dependsOn`-gebunden; `resultOptional` verschluckt keinen Produzentenfehler. Ownerressourcen sind vor Finalizerstart schon geschlossen.
- Globale Cleanups bleiben global und folgen allen fälligen Finalizern. Keine Finalizer zusätzlich als Cleanup registrieren.

## 5. Architektur und bestehendes Tooling erhalten

Neue reine Auswahl-/Schedulingregeln gehören in den vorhandenen JDK-only-Sprachvertrag, ausführende Kontrolle in den Runner. Keine produktive Runner-/Handlerabhängigkeit im LSP, keine zweite Graphsemantik in TypeScript und kein allgemeines neues Workflowmodul.

`GretlScript`, `JobDslModel` und Projektindex gezielt ergänzen. Aktuelle Include-Scopes, eigene Ressourcenbasis des Finalizers und echte Fragmentquellpositionen erhalten. Bestehende Paketformate nicht ohne tatsächlich geänderten serialisierten Vertrag umnummerieren. Ein normaler alter Job ohne Finalizer muss unverändert funktionieren.

Im Tooling: eigene Finalizerreferenz/-kante, passende Taskcompletion und Navigation, gemeinsame Erfolgsplanvorschau. Ownerdetails müssen Finalizer setzen/entfernen können; eine zusätzliche neue Drag-Geste ist nicht zwingend. Rename aktualisiert auch gebundene Finalizerlinks. Die heutigen Include-Ownership-/Sharedsource-/Footprint-/Snapshotguards bleiben erhalten. Der Generic-Setter-/Batchweg ist keine Ausnahme. Keine pauschale PARTIAL-/Read-only-Lösung für normale statisch bekannte Jobs.

Server-/VSIXfähigkeiten müssen zur neuen Kantenart passen. Ältere Clients dürfen sie weder als AFTER interpretieren noch falsch editieren. Keine blinde Versionserhöhung unabhängiger Katalog-/Jobformate.

## 6. Tests nur im notwendigen Umfang

Die ausdrückliche lokale Testausnahme steht im Plan §1 und §8. Keine automatischen vollständigen Reactor-/Dienst-/GUIgates pro Phase. CI-/Releaseverträge selbst nicht ändern. Bei Scheduler-/Kontrolländerungen sind bestehende Plan-/Budget-/Failure-/Contextfamilien allerdings unmittelbar betroffen und zu prüfen.

FakeClock und kontrollierte Ereignisse statt langer Sleeps. Echte kleine SCRIPT-/JAR-/Shutdownprozesse, echter stdio-LSP und ein kleiner installierter VSIX-Ablauf sind Pflicht, wenn ihre Phase fällig ist. Eine println-Action alleine beweist keinen funktionierenden typisierten Finalizer nach Cancellation. Keine Produktion, keine Fachdienstvollmatrix.

Frische JUnit-/Failsafe-/Nodebelege und tatsächliche Assertions prüfen. `failIfNoSpecifiedTests=false` darf nur unbeteiligte Reactorabhängigkeiten beruhigen; der Zieltest muss wirklich ausgeführt sein. Testskips/Nulltests/alte XMLs sind kein PASS. Artefaktbau mit Testskip ist Buildvorbereitung, kein Nachweis.

Gleichnamige Maven-SNAPSHOTs paralleler Worktrees isolieren. Belege vor clean extern sichern. Nach Fix nur betroffene Tests neu; unveränderte schwere Belege mit überprüfbarer Identität weiterverwenden und nicht als neue Ausführung umetikettieren.

## 7. Autonomer Zweitreview

Am Recherchepin vorhanden: **`tools/include-tooling/review.py` und `test_review.py`**. Geeignete Mechanik daraus wiederverwenden, aber nicht unverändert den alten Auftrag, P0–P4 und IT-Kriterien starten. Ein kleiner Adapter für `task-finalizers`/P0–P5 genügt; alte historische Gates nicht ändern. Vorhandenes Python bzw. vorhandene venv nutzen, keine unnötige Systeminstallation oder Pythonmigration.

Die effektive OpenCode-CLI muss **2.x** sein. Lokale Hilfe, vorhandene Anmeldung und Modellliste prüfen. Gewünschte Version **DeepSeek-V4.1-Flash-1017**; Aliaszuordnung am Ausführungstag aus offizieller Quelle und gegen tatsächliche Sessionmetadaten prüfen. Vorhandene Credentials verwenden, **keine neue Pflicht-ENV für den API-Key**, keine Authdatei lesen/ausgeben, kein stiller Modell-/Anbieterwechsel.

Für jeden Kandidaten neutralen Request, commit, danach externen Handoff mit voller SHA und Digest der drei normativen Dateien. Frischer detached Reviewworktree und frische Session ohne Continue/alte Historie. Begrenzte Policy für Reads, gezielte Tests und eigene externe Proben/Reports; keine Produktfixes, fremden Worktrees, Secrets oder Publikation durch den Reviewer.

Auf wirklichen Abschluss warten. Bericht/JSON/Session-/Testbelege unabhängig maschinell prüfen: richtiger Auftrag `task-finalizers`, Spec `1.0`, Phasebase, Kandidat, Digest, Modell, frische Session, vollständige Kriterienmenge, eigene ausgeführte Tests und Gegenprobe, keine offenen BLOCKER/MAJOR oder fälligen NOT_EXECUTED. Exit 0 oder das Wort PASS genügt nicht. Findings bearbeitest du selbst; keine Selbstfreigabe.

Ein echter Infrastrukturblocker wird nach wenigen gezielten legitimen Versuchen `BLOCKED_REVIEW_INFRA`. Nicht in nächste Phase gehen und keinen manuellen Nutzerreview als angeblich erfolgreiche Autonomie ausgeben.

## 8. Abschluss und Start

Liefere tatsächliche Basis-/Produkt-/Status-SHAs, Branch, getestete Beispielaufrufe, ausgeführte Tests und Originalreviews mit verbleibenden Grenzen. Schlusslabel **`TASK_FINALIZERS_WITH_TOOLING_ACCEPTED`** nur nach kumulativ gültigem P5. Keine Behauptung vollständiger Gradlekompatibilität oder garantierter Finalisierung bei hartem Prozessabbruch.

**Beginne jetzt mit P0.** Prüfe Arbeitsbasis und kleine Baseline, beweise die autonome OpenCode-/DeepSeek-Prüfung und setze danach die Phasen ohne manuelle Übergaben um.
