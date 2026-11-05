# Unabhängiger Review: GRETL `finalizedBy`

**Fassung 1.0 · 5. November 2026 · Auftrag `task-finalizers`**  
**Zweitagent:** OpenCode 2.x mit DeepSeek-V4.1-Flash-1017

> Du bist der unabhängige Reviewer, nicht Codex. Prüfe den bezeichneten committeten Kandidaten gegen [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Bewerte **Code und Zielerreichung**, führe selbst notwendige Tests aus und entwerfe eine eigene neue Gegenprobe. Keine Produktfixes, Scopeänderungen, gelockerten Assertions oder Selbstfreigaben des Implementierers.

## 1. Prüfidentität und eigener Kontext

Der externe Handoff nennt `assignment_id=task-finalizers`, `spec_version=1.0`, Phase, feste Auftrags-/Phasebase, vollständigen Kandidaten-SHA, Requirement-Digest, Reviewworktree, Kriterienmenge, erlaubte Test-/Probe-/Evidenceorte und tatsächliche Modellmetadaten. Der Digest umfasst genau die drei aktiven normativen Auftragsdateien. Der Request ist kein Urteil.

Prüfe selbst Arbeitsverzeichnis, `git rev-parse HEAD`, Gitstatus und gesamten Phasendiff. Recherchepin `31ca88ef4b4a5a4a219907156886f1da19adc644` ist keine Reset-Anweisung. Vorhandene Runtime-Includes und Include-Tooling sind Ausgangsbasis, kein neuer Implementierungserfolg und keine neu zu prüfende alte Freigabe.

Frische Session und isolierter Kandidatenworktree sind getrennte Voraussetzungen. Tatsächliche OpenCode2-/Provider-/Modell-/Locationidentität aus externen Aufruf-/Sitzungsmetadaten nachweisen. Gewünschtes Modell ist DeepSeek-V4.1-Flash-1017; ein lokal bestätigter Alias ist zulässig, LLM-Selbstauskunft allein nicht. Keine Credentialdateien lesen, Schlüssel exportieren oder Modell wechseln.

Getrackte Produkt-/Test-/Erwartungs-/Konfigurationsdateien im Reviewcheckout bleiben unverändert. Eigene neue Tests extern oder in einer separaten Probekopie **desselben SHA** anlegen; Produkt- und Originaltestbytes identisch halten. Builds dürfen reguläre Artefakte schreiben. Vorher/Nachher-Status prüfen. Kein Commit, Push, Merge, Release, fremder Worktree oder produktiver Job durch dich.

## 2. Richtiger Auftrag, keine geerbten Fremdgates

Die existierende `tools/include-tooling/review.py` ist an einen anderen Auftrag gebunden. Ein unveränderter Aufruf mit IT-Kriterien/P0–P4 wäre falsch. Dasselbe gilt für die alte 81-Kriterien-/Vollgatekopplung des Toolingreviewers. Geeignete Mechanik darf verwendet werden; Auftrag, Digest, Phasen und Testpflichten müssen zu `task-finalizers` passen.

Fachlich gelten F01–F30 und die phasenbezogene Auswahl in Plan §7.1, für P0 W01–W04. Eine frühe Java-Komponentenabnahme behauptet noch keine öffentliche Groovy-/JAR-/GUIunterstützung. Den Implementiererbericht als zu prüfende Behauptung lesen, nicht übernehmen.

Drei gleichwertige Fragen:

1. Funktioniert das fällige öffentlich beobachtbare Phasenziel wirklich?
2. Sind Auswahl, Aktivierung, Fehler/Cancellation, Scopes, Ergebnisse und Toolinggrenzen korrekt und angemessen einfach?
3. Sind die Tests wirksam, tatsächlich ausgeführt und durch deine eigene neue Gegenprobe ergänzt?

Klassenzahlen, grüne Banner und ein vorheriger PASS beantworten diese Fragen nicht.

## 3. Zentrale Semantikfehler, nach denen du aktiv suchst

### 3.1 Auswahl ist nicht Aktivierung

Ein Plan kann F enthalten, ohne dass F ausgeführt werden darf. Owner mit NOT_RUN aktiviert nichts; when-false nach RUNNING dagegen schon. Start-/Setup-/Prädikat-/Konfigurations-/Closegrenze lesen und wirklich testen. Ein Fehler vor Runstart ist kein Ownerabschluss.

Gemeinsamer F läuft genau einmal nach allen ausgewählten Ownern. Ein unausgewählter Owner darf ihn nicht blockieren. Ein niemals erreichter Owner darf ihn nicht alleine auslösen. Explizit gewählter F startet dagegen auch ohne Owner und selektiert diesen nicht rückwärts.

### 3.2 Fallstricke im Drain

Fail-fast für normale Arbeit bleibt bestehen. Nur aktivierte Finalizer und ihre notwendigen Voraussetzungen dürfen weiterlaufen. Eine normale Voraussetzung, die der Abschlusszweig erst später benötigt, darf nicht voreilig NOT_RUN werden; eine unabhängige normale Aufgabe darf durch den Drain nicht wieder freigegeben werden.

Eine fixe Topologieliste plus `continueOnError=true` ist kein ausreichender Scheduler. Prüfe auch die azyklische Aktivierungsblockade aus dem Plan. Keine Hänger, impliziten Kantenlockerungen oder undokumentierten frühen Voraussetzungsstarts akzeptieren.

### 3.3 Echte Datenabhängigkeit bleibt echte Datenabhängigkeit

Finalisierung alleine verlangt keinen Ownererfolg. Ein ausdrücklich zusätzliches `F dependsOn A` verlangt ihn jedoch wie eine normale Voraussetzung; bei FAILED/CANCELLED/NOT_RUN wird F blockiert. Diese bewusste GRETL-Regel ist dokumentiert, keine vollständige Gradlezusage. Nicht still einmal so und einmal anders behandeln.

AFTER und FINALIZED_BY verleihen keine Resultrechte. `resultOptional` darf einen fehlgeschlagenen Produzenten nicht plötzlich in Optional.empty umwandeln. Ein Abschlussreport braucht kein neues globales Result-/Failure-API.

### 3.4 Kontrolle muss durch die gesamte Pipeline stimmen

Prüfe nicht nur den Loop. Verfolge einen tatsächlichen finalisierenden typisierten Task durch `TaskFrame`, `BaseContext`, Konfiguration, `OperationInvoker`, innere Invocation, ResourceClose und `FailureArbiter`.

Ein alter globaler Cancel-/Finishsnapshot darf die neue Abschlussoperation nicht sofort verhindern oder einen Erfolg später umdeuten. Gleichzeitig müssen neue lokale Timeouts, neue Interrupts, ContextClosed und das gemeinsame Abschlussbudget wirken. Globalen Runabbruch nicht rücksetzen. Keine Budgetabschaltung oder unbegrenzt ausgeführten Finalizer.

### 3.5 Ownerheader statt Consumerheader

FINALIZED_BY zeigt A→F, steht aber an **A**. Datenabhängigkeiten zeigen P→C und stehen an C. Suche besonders ternäre „dependsOn, sonst after“-Implementierungen und generische Editpfade. Diagramm, Rename, Kantenlöschung, Headerform und Plan müssen dieselbe Richtung und Deklarationsstelle verwenden.

## 4. Phasenbezogene Prüfaufträge

### P0 – Basis, Baseline und unabhängige Fähigkeit

W01–W04 prüfen: richtige integrierte Mainbasis, eigener Worktree, tatsächliche kleine Plan-/Budget-/Failurebaseline, vorhandene Includeunterstützung, echte neue OpenCode-Session und korrekter neuer Auftrag. Ein Modellping oder gespeicherter alter Review ist keine Bereitschaftsprobe.

Einen geeigneten vorhandenen Basistest selbst ausführen und eine kleine eigene Gegenprobe entwickeln. Noch fehlendes finalizedBy darf erwartungsgemäss abgewiesen werden. Keine neue Produktklasse oder komplette Fachdienstqualifikation verlangen, nur um P0 grösser zu machen.

### P1 – Definition, Planung und reine Zustandsmaschine

Kriterien F01–F09, F17, F23 und F30 im P1-Komponentenumfang prüfen. Keine fertigen DSL-/GUI-/Paketwege vorzeitig verlangen.

Eigene Traceprobe mit Owner-/Finalizer-/Daten-/AFTER-Kombination. Prüfe insbesondere:

- Linkformen, Vorwärtsrefs, Ref-/Definitionownership, Selbst- und Duplikatfehler.
- Normaler Zielabschluss gegenüber erweitertem Kandidatenabschluss; keine rückwärtige Ownerselektion.
- Shared-Finalizerbarriere, NOT_RUN versus reached SKIPPED, Ketten und Doppelrollen.
- Lazy Finalizer-Voraussetzungen und echte Voraussetzungsblockade.
- Vereinigungskreise sowie azyklischer Aktivierungsdeadlock vor Nebenwirkungen.
- Jede Schedulingentscheidung macht Fortschritt; keine wiederholten Starts.
- Ohne Finalizer identische Bestandsplanung; keine Datenclosure aus AFTER/FINALIZED_BY.

Ein gemeinsamer Algorithmus für Vorschau und Runtime ist gut, aber Parität allein kein unabhängiges Orakel. Erwartete Abläufe von Hand begründen und tatsächliche Trace-/Stateassertions prüfen.

### P2 – Echte Ausführung, Fehler und Abbruch

F04–F18, F22/F23 im Runnerumfang sowie F30. Prüfe reale Java-Taskdefinitionen mit tatsächlichen Bodies und OperationInvocations, nicht nur vorgegebene Statusfolgen des Schedulers.

Ownerfehler in Body, Prädikat, Konfiguration, Setup nach RUNNING und ResourceClose korrekt zuordnen. Globale Cleanups nach finaler Taskabschlussphase und in alter Reihenfolge. Context-/Resource-/Secretgrenzen erhalten.

Eine eigene neue Probe muss Fehler/Cancellation mit einer inneren Invocation oder einem Ressourcenschluss verbinden. Ein reiner println-Finalizer genügt nicht. Verifiziere ursprünglichen Primärfehler, weitere Sekundärursachen, fällige Geschwister, ausbleibende normale Arbeit und Task-/Invocationereignisse.

Beim Zeitvertrag unterscheiden: normaler bzw. fachlich fehlgeschlagener Run nutzt Run-/Taskfristen; nach Cancellation/Finish einmaliges gemeinsames completionBudget. Lokaler Finalizer-Timeout darf nicht jedes verbleibende Abschlussbudget logisch sofort canceln. Grace nicht bei jeder Aufgabe neu starten. Keine neue harte Zeitgarantie für nicht kooperierenden Code fordern oder akzeptieren.

finishJob in Owner und Finalizer, Cancellation vor Start und während Ausführung, ursprünglicher Grund, neue Interrupts und endgültige Wiederherstellung prüfen. VM-Fatalfälle nicht zu gewöhnlichen Fehlern umdeuten.

### P3 – DSL, Scope, Paket und öffentliche Belege

F01–F03, F08, F16–F23, F29/F30 im öffentlichen Umfang. Header auf Task/Action, echte Forwardrefs durch Includegrenzen und qualifizierte Subprojektverweise prüfen. Finalizer hat seine eigene Deklarations-/Ressourcenbasis, nicht die des auslösenden Owners.

Wirkliche SCRIPT- und isolierte `java -jar`-Prozesse: Single-/Projectpakete ohne ursprünglichen Quellbaum, gleiche Finalizerwirkung, Quellenpositionen und Report-/Exitsemantik. Introspektion darf keine Finalizer-/Task-/Cleanupbodies ausführen. Alte JARs ohne Feature bleiben startbar.

Eigener Paket-/Fehlergegenfall. Unzulässig sind Nachladen aus zufällig vorhandenem CWD, Testclasspath als Laufzeitersatz, unmotivierte Schemaänderungen oder ein neu im IMAGE eingebautes konkretes Job-JAR.

Echter Cancellationprozess mit nachgewiesenem Ownerstart und kontrolliertem Signal/Stop; nicht vor dem Ownerstart abbrechen und danach einen negativen NOT_RUN-Test als positive Finalizerprobe verkaufen. Plan-/Graphkanten und finaler Bericht müssen deklarierte Owner, aktive Trigger und tatsächlich ausgeführte Rollen unterscheiden.

### P4 – Sprache, Modeler und sichere Sourceänderungen

F19, F23–F28 und F30 im Toolingumfang. Bestehende Includecontexts und Owner-/Footprintguards lesen; nicht von einer älteren pauschal partiellen IDE ausgehen.

Saubere statische Finalizer-/Include-Fixture muss vollständig sein; echte dynamische Gegenkontrolle bleibt partiell. Markerprobe separat, damit ihr opaker Code nicht das Completenessorakel verfälscht. Keine Runtime-/Handlerausführung beim Analysieren/Editieren.

Echte stdio-Queries für Optionen, Completion, Definition, References, Rename und Zielvorschau. FINALIZED_BY nicht als Ergebnisbindung behandeln. Kantenrichtung und Sourceanker prüfen. Detailsliste am Owner ergänzen/entfernen; Kantenlöschung und generische Formular-/Setterwege schreiben denselben richtigen Header.

Rename aktualisiert die gebundenen Referenzen über Dateien. Referenzierter Finalizer nicht still gelöscht. Shared-Footprint, partieller Graph, neue Ownergeneration, geändertes Fragment oder Finalizerliteral zwischen Plan und Apply sperren unsichere Edits. Eine unabhängige lokale erlaubte Änderung darf weiterhin funktionieren. Keine globale Read-only-Ausweichlösung.

Passende installierte VSIX-/Serverartefakte, native WorkspaceEdits, Save und Undo/Redo der tatsächlich geänderten Dokumente nachweisen. Bei geänderter Host-/GUIlogik mindestens eine eigene kleine reale Benutzerpfadgegenprobe ausführen; Codex darf einen mechanischen UItransport bereitstellen, aber nicht Hypothese, Assertion und Urteil vorgeben. Vorhandene kandidatengleiche umfangreichere Belege unabhängig lesen statt pauschal alles doppelt laufen lassen.

### P5 – Kumulative Schlussabnahme

Alle F01–F30 bewerten, nicht bloss die letzten Dokuänderungen. Phasenabnahmen nachvollziehen, relevante neue Tests und eine eigene übergreifende Probe selbst ausführen. Gespeicherte GUI-Fixture unverändert separat mit Runtime laufen lassen, Quellen-/Artefaktidentität beweisen.

Beispiel mit absichtlichem Ownerfehler erwartet Fehlerexit **und** Finalizerbeleg. Report mit ursprünglichem Fehler, genau einmal fälligen Finalizern, NOT_RUN-Aufgaben und globalem Cleanup prüfen. Dokumentation erklärt Listensemantik, zusätzliche Datenvoraussetzungen, Ressourcenlebensdauer und Budgetgrenzen.

Keine komplette alte Dienst-/GUI-/Releasequalifikation verlangen. Fehlende tatsächliche Featurepflicht darf aber nicht `NOT_REQUIRED` genannt werden. Schlusslabel nur bei vollständiger fälliger Abnahme: `TASK_FINALIZERS_WITH_TOOLING_ACCEPTED`.

## 5. Eigene neue Gegenprobe

Mindestens eine selbst entworfene neue Gegenprobe pro Phase. Dokumentiere Fehlerhypothese, reproduzierbaren neuen Input bzw. Ereignisablauf, aus dem Vertrag abgeleitete Erwartung, tatsächlichen Befehl/Exit, fachliche Assertion und Schlussfolgerung. Keine blosse Umbenennung einer Codex-Fixture.

Mögliche Richtungen: ein shared F mit späterem NOT_RUN-Owner; eine Abschlussvoraussetzung, die zugleich im normalen Plan stand; explizites Ziel und Finalizerrolle zugleich; indirekte Finalizerkette nach Prädikatfehler; gemischte AFTER-/Finalizeraktivierungsblockade; innere Operation nach finishJob; lokales Timeout bei verbleibendem Gracebudget; Finalizerlink in einem Sharedfragment; generischer Setter, der versehentlich den Finalizer statt Owner editiert.

Das sind Anregungen, keine vorgefertigten Testorakel. Ein Negativtest besteht nur beim richtigen Fachgrund, nicht bei irgendeinem fehlenden Java/Katalog/Transportfehler. Wo nötig eine positive Gegenkontrolle verwenden.

## 6. Testumfang, Wiederverwendung und Integrität

Plan §8 gilt. Nur notwendige relevante Familien, dafür wirksame Assertions. Keine automatische Root-/GeoTools-/Datenbank-/FTP-/S3-/alte GUIvollmatrix. Nach Scheduler-/Kontrolländerung sind bestehende Budget-/Failure-/Contextregressionen aber tatsächlich betroffen.

Frische XMLs, wirklich ausgeführte Klassen/Methoden, Testanzahl > 0, Exits und Assertions prüfen. Keine alten Reports, unerklärten Skips oder Nulltest-PASS. `-DskipTests` ist nie ein Testnachweis. Eigene Checks müssen denselben Kandidaten verwenden, nicht fremde gleichnamige SNAPSHOT-Artefakte.

Unveränderte aktuelle schwerere Belege darfst du mit SHA-/Artefakt-/Inputnachweis wiederverwenden. Eigene tatsächlich ausgeführte Tests und Gegenprobe bleiben Pflicht. Nach Korrektur die gesamte Phase neu bewerten, aber nur vom Fix betroffene Tests erneut ausführen. Reportprüfung nicht als neue Ausführung ausgeben. Kosmetik nicht zu fachlichen BLOCKERn aufblasen.

NOT_EXECUTED ist eine fällige fehlende Fähigkeit/Prüfung und verhindert die Abnahme. NOT_REQUIRED ist konkret begründete Nichtbetroffenheit. Ein gemeinsamer Algorithmus, dessen zwei Consumer denselben Fehler zeigen, ist kein unabhängiger Paritätsbeweis.

## 7. Bericht und maschinenprüfbares Urteil

Originalbericht und JSON gemäss Plan §9.4 an das externe Ziel schreiben oder vollständig zurückgeben. Nicht auf den Produktbranch committen, keine vorgefüllten Ergebnisse übernehmen. Alle drei normativen Dateien und den neutralen Phasenrequest selbst lesen.

```markdown
# Independent review: task-finalizers / <PHASE> / <FULL_CANDIDATE_SHA>

## Urteil
ABGENOMMEN | NACHARBEIT | NICHT_PRUEFBAR

## Identität
Auftrag/Specversion/Digest, Auftragsbasis, konstante Phasebase,
Kandidat/Worktree, tatsächliche OpenCodeversion, Modellzuordnung,
frische Session und gegebenenfalls separate Probekopie.

## Code und Zielerreichung
Vollständige fällige Kriterienmenge mit konkreter öffentlicher Wirkung.
Bestand, neue Implementierung und Grenzen getrennt.

## Selbst ausgeführte Prüfungen
| Prüfung | Tatsächlicher Befehl | Exit und fachliche Assertion | Rohbeleg |
|---|---|---|---|
| ... | ... | ... | ... |

## Unabhängig geprüfte wiederverwendete Belege
Identität, Deltaargument und warum keine erneute Ausführung fällig war.

## Eigene Gegenprobe
Hypothese, neuer Input/Ablauf, Vertragserwartung, Befehl/Exit,
beobachtete Wirkung und konkrete Assertions.

## Findings
ID / BLOCKER|MAJOR|MINOR, Datei/Methode, Reproduktion,
verletzter Vertrag, minimal notwendiger Fix und betroffene Nachtests.

## Integrität und verbleibende Grenzen
Getrackte Produkt-/Testbytes unverändert? Keine Testlockerung?
NOT_EXECUTED von NOT_REQUIRED getrennt, tatsächliche Artefakte/Plattform.

## Entscheidung
Exakter Phasenumfang freigegeben oder konkrete Nacharbeit.
```

Das JSON nennt dieselbe Identität, `criteria`, `executed_checks`, separat `reused_evidence`, eigene `counterprobes`, `findings` und `required_not_executed`. Alle Belege sind echte nichtleere Dateien bzw. nachvollziehbare Prozessdaten, keine Platzhalter. Der äussere Validator prüft deine Aussagen gegen tatsächliche Tool-/Sessionevents und unveränderte Produktbytes.

**ABGENOMMEN:** exakter Kandidat, alle fälligen Kriterien erfüllt, eigene echte Tests/Gegenprobe, keine offenen BLOCKER/MAJOR oder fehlenden Pflichtbelege. **NACHARBEIT:** reproduzierbarer Vertragsdefekt, notwendiger fehlender Test oder Integritätsverletzung. **NICHT_PRUEFBAR:** erforderlicher Kandidat, Toolchain, Modellidentität oder reale Prüffähigkeit fehlt. Kein Ersatz durch plausible Lektüre oder frühere PASS-Berichte.

## 8. Autorität

Du reparierst nicht selbst und änderst keine Anforderungen. Findings gehen an Codex; neuer Produktcode erzeugt neue SHA und neuen unabhängigen Review. Keine Publikation und keine implizite allgemeine Gradlekompatibilitätszusage.

**Welcher konkrete Auswahl-/Aktivierungs-/Fehler-/Cancellation-/Owner-Editfall könnte diese Phase widerlegen – und habe ich ihn tatsächlich ausgeführt?**
