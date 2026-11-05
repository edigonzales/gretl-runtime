# Taskgebundene Finalizer

`finalizedBy` verknüpft einen Task oder eine Action mit gewöhnlichen benannten
Tasks/Actions für den Abschluss dieses Owners. [Das lokale Beispiel](../../../examples/task-finalizers/README.md)
läuft mit der Distribution und als vorkompiliertes Job-JAR. Die IDE führt dabei
keinen Jobcode aus.

```groovy
action('importData', finalizedBy: ['release', 'record']) { /* Arbeit */ }
action('release') { /* taskgebundener Abschluss */ }
action('record', after: 'release') { /* ausdrücklich danach */ }
defaults 'importData'
```

Erklärungsausschnitt: Die ausführbaren vollständigen Quellen stehen im Beispiel.
Strings, gebundene TaskRefs und flache Listen verwenden dieselben Scope- und
Namensregeln wie `dependsOn` und `after`. Vorwärtsverweise über lokale Includes
sind erlaubt. Fehlende, fremde, doppelte und Selbstverweise sind Definitionsfehler;
`null` und eine leere Liste deklarieren keine Finalizer. Innere einzelne
Operationsaufrufe besitzen diese Headeroption nicht.

## Auswahl und Aktivierung

Ein Ownerziel wählt seine Finalizer und deren echte `dependsOn`-Voraussetzungen
als Kandidaten mit aus. Eine Kandidatur allein startet keinen Task. Aktiviert wird
nach einem erreichten Ownerabschluss: SUCCEEDED, SKIPPED (auch `when: { false }`),
FAILED oder CANCELLED. Ein Owner, der vor RUNNING wegen fehlgeschlagener
Voraussetzung, Fail-fast oder Abbruch NOT_RUN bleibt, aktiviert nichts.

Ein gemeinsamer Finalizer läuft höchstens einmal und wartet auf alle ausgewählten
Owner, auch wenn ein späterer Owner NOT_RUN wird. Nicht ausgewählte Owner werden
weder rückwärts ausgewählt noch abgewartet. Ein direktes Finalizerziel läuft als
normales Ziel ohne seinen Owner. Ist ein Task sowohl normal ausgewählt als auch
Finalizer, wird er einmal ausgeführt; beide tatsächlichen Rollen bleiben sichtbar.
Finalizer können selbst Finalizer aktivieren.

Eine Liste gibt keine zusätzliche Reihenfolge zwischen ihren Elementen vor.
Mit `after` wird eine Reihenfolge zwischen bereits ausgewählten Kandidaten
festgelegt; mit `dependsOn` eine echte Voraussetzung. Fehlgeschlagene echte
Voraussetzungen blockieren auch Finalizer. Zyklen und nicht auflösbare
Aktivierungs-/Reihenfolgebarrieren werden vor dem Run abgewiesen
(`PLAN_CYCLE`, `FINALIZER_ACTIVATION_DEADLOCK`).

## Ergebnisse, Ressourcen und Fehler

`finalizedBy` und `after` erteilen **keinen Ergebniszugriff**. Auch
`resultOptional(owner)` braucht eine direkte oder transitive `dependsOn`-Kette.
Wer ausdrücklich vom Owner abhängt, benötigt dessen erfolgreichen Abschluss;
ein solcher Finalizer ist damit kein unbedingter Fehlerabschluss.

Vor der Aktivierung sind die Ressourcen des Owners geschlossen. Ein Finalizer
hat einen eigenen Taskscope und benötigt eigene Ressourcen. `file(...)` verwendet
seine eigene Job-/Subprojektressourcenbasis; ein Include verschiebt diese nicht
automatisch zum Fragmentverzeichnis. Vorhandene Scope-/Quellzuordnung bleibt
auch im JAR erhalten.

Nach einem normalen Taskfehler werden aktivierte Finalizer weiter abgearbeitet;
unabhängige Finalizer können nach einem Finalizerfehler fortfahren. Die erste
fachliche Ursache bleibt primär, spätere Fehler werden sekundär berichtet.
Globale `cleanup`-Aktionen folgen nach dem Finalizerdrain in umgekehrter
Registrierungsreihenfolge. Sie haben keinen Task-Ergebniszugriff und starten
keine Tasks oder inneren Operationsaufrufe.

Nach kooperativem Abbruch oder `finishJob` erhalten aktivierte Abschlussaufgaben
einen frischen lokalen Kontrollzustand: ein bereits behandelter Abbruch verhindert
sie nicht sofort wieder. Eigener Tasktimeout und neue Abbruchsignale gelten
weiter. Finalizer und globales Cleanup teilen das einmal begonnene, begrenzte
Abschlussbudget von 30 Sekunden. Bei gewöhnlichem Erfolg/Fehler gelten weiterhin
die Run-/Taskbudgets. Das ist keine Ausführungsgarantie bei SIGKILL, JVM-Absturz
oder nicht kooperierendem Fremdcode und kein Rollback.

## Pläne, Berichte und IDE

`check`, Jobhilfe und `plan` führen keine Taskkonfiguration, Prädikate, Tasks,
Finalizer oder Cleanups aus. Vertrauenswürdiger SCRIPT-Top-Level-Code kann beim
Definitionsladen weiterhin laufen. Der Textplan und Mermaid/PlantUML zeigen
`finalizedBy` als Owner → Finalizer; normale Auswahl und bedingte Kandidatur sind
getrennt. Der Plan beschreibt den nominalen Erfolgsablauf, keinen beobachteten Run.

Reportschema 1 wird additiv um Planbeziehungen und Finalisierungsangaben ergänzt:
`normalSelected`, `declaredOwners`, `activatingOwners`, `executedRoles` sowie
Primär-/Sekundärfehlerrefs. Rollen sind `NORMAL`, `FINALIZER` und
`FINALIZER_PREREQUISITE`. Status und NOT_RUN-Grund erklären die tatsächliche
Ausführung. Der Event `gretl.finalizer.activated` nennt die Aktivierung;
Ergebnisobjekte werden dadurch nicht serialisiert.

Im [VS-Code-Modeler](../../../vscode/gretl-vscode/README.md) bietet ein Owner unter
**Details → Edit owner finalizers** die Auswahl benannter Tasks/Actions an.
**Add dependency** besitzt ausserdem eine ausdrückliche Finalizerart;
**Disconnect selected edge** entfernt nur die gewählte Beziehung am Ownerheader.
Rename aktualisiert gebundene Links, Delete verweigert noch referenzierte Ziele.
TaskRefs bleiben gebundene Referenzen; dynamischer Code bleibt ehrlich partiell.

Vollständig bekannte Includejobs bleiben vollständig analysierbar. Sharedquellen
sind kontextsensitiv lesbar, automatische Änderungen benötigen aber einen
vollständig bekannten exklusiven Schreibfootprint. Eine Kontextwahl erteilt
keine Schreibberechtigung. Geänderte Fragment-/Header-/Kontextstände verwerfen
alte Pläne vor einer Mutation. Änderungen, Save und Undo/Redo verwenden native
VS-Code-WorkspaceEdits in den tatsächlich betroffenen Dokumenten. Den gespeicherten
Job separat mit der Runtime ausführen.

LSP-Basisprotokoll und Operationskatalog bleiben Version 1. Die neuen Graphdaten
verwenden Graphschema 2 und `taskFinalizersVersion=1`; die passende VSIX prüft
beides. Paketdeskriptoren, Include-Bundleformat und Quellhashvertrag bleiben
unverändert. [Anforderungen und Phasen](EXECUTION_PLAN.md) sowie
[Arbeitsstand und Nachweise](WORK_QUEUE.md) dokumentieren die Featurequalifikation.
