# GRETL: Include-aware LSP und Modeler

**Verbindliche Spezifikation und Ausführungsplan für Codex**  
**Fassung:** 1.0 · 5. November 2026  
**Auftrag:** `include-tooling`  
**Repository:** `edigonzales/gretl-runtime`  
**Empfohlener Featurebranch:** `codex/include-tooling`  
**Recherchepin:** `ae0a2630156280e0e7f6bebf2354884f555d8d7e` (`origin/main`)  
**Implementierung:** Codex · **unabhängiger Reviewer:** OpenCode 2.x / DeepSeek Flash 1017  
**Status:** Neuer Implementierungsauftrag. Die hier beschriebenen Erweiterungen und Produkttests wurden bei Erstellung der Spezifikation nicht implementiert bzw. ausgeführt.

> Ziel: Ein normaler, statisch beschreibbarer GRETL-Job verliert nicht allein durch `apply from:` seinen vollständigen Graphen, seine Navigation und seine sicher möglichen Bearbeitungsfunktionen. Die IDE führt weiterhin keinen Jobcode aus. Mehrfach verwendete Fragmentquellen werden kontextsensitiv gelesen; Änderungen an gemeinsam verwendeten Quellen werden in dieser Ausbaustufe kontrolliert abgelehnt.

## 1. Auftrag, Rangfolge und Grenzen

### 1.1 Geliefertes Ergebnis

Erweitere das vorhandene Tooling um transitive statische Includeauflösung, korrekt zugeordnete Deklarationen/Referenzen, vollständige Graphen für den unterstützten statischen Fall, Fragmentnavigation und Completion, Synchronisierung ungespeicherter Fragmenttexte sowie sichere Mehrdateien-Edits einschliesslich Rename. Verwende vorhandenen Core, LSP, VS-Code-Client, CustomTextEditor, Katalog und Runtimeverträge. Keine zweite IDE und keine zweite Jobbeschreibung.

Arbeite **P0 → P1 → P2 → P3 → P4**. Jede Phase benötigt erfolgreiche notwendige Tests und einen unabhängigen Review des konkreten Kandidaten durch OpenCode 2.x mit DeepSeek Flash 1017. Der Reviewer beurteilt Code **und Zielerreichung**, führt selbst risikotragende Tests aus und entwickelt eine eigene neue Gegenprobe. Codex organisiert Aufruf, Übergabe, Auswertung und gegebenenfalls Nacharbeit autonom. Erst danach beginnt die nächste Phase.

Die aktiven Auftragsdateien sind `EXECUTION_PLAN.md`, `CODEX_STARTPROMPT.md` und `OPENCODE_REVIEW_PROMPT.md` in diesem Verzeichnis. `SOURCE_BASELINE.md` dokumentiert die Recherche, ist aber kein weiteres Kriterienregister. Der ausdrückliche Nutzerauftrag ist eine Umsetzungsermächtigung, nicht bloss ein Auftrag zur Erstellung weiterer Pläne.

### 1.2 Verhältnis zu den abgeschlossenen Aufträgen

`docs/implementation/gretl-tooling/` und `docs/implementation/script-includes/` bleiben historische Implementierungs-/Reviewquellen. **Nicht erneut P0–P8 oder das abgeschlossene Runtime-Includeprojekt starten.** Runtime-Semantik S01–S19, Job-JAR-Formate, Namensräume und öffentliche Aufrufe bleiben erhalten.

Die bisherigen C01–C08 beschrieben eine bewusst partielle Übergangs-IDE. Dieser Folgeauftrag ersetzt für **gültige, vollständig erfasste statische Includes** gezielt die frühere Erwartung „immer PARTIAL und keine Strukturänderung“. Er ersetzt **nicht** die Nichtausführungs-, Quellen-, Guard-, Verlustfreiheits- und Snapshotverträge. Bereits vorhandene Partial-Tests müssen in positive vollständige und weiterhin negative unvollständige Fälle differenziert werden; sie dürfen nicht einfach gelöscht oder pauschal grün umgeschrieben werden. Historische Reports und deren geprüfte SHAs bleiben unverändert.

### 1.3 Ausdrücklicher risikobasierter Testauftrag

Das gelesene Root-`AGENTS.md` nennt zwei vollständige Maven-Gates. Für die **lokalen Phasen dieses neuen Features** gilt ausdrücklich die vom Nutzer verlangte engere Auswahl nach §8: Featuretests, unmittelbar betroffene Regressionen und wenige reale Integrationswege. Nicht nach jeder Phase oder jedem Review beide vollständigen Rootläufe, sämtliche Operationen, GeoTools-/Datenbank-/FTP-/S3-Prüfungen oder G01–G30 ausführen. `AGENTS.md`, vorhandene CI- und Releasegates werden nicht geändert oder deaktiviert. Ein erforderlicher Artefaktbau ist kein Testnachweis. Featureabnahme ist keine Releasequalifikation.

### 1.4 Nicht beauftragt

Keine neue Include-Runtime, kein `finalizedBy`, keine dynamischen/Remote-Includes, keine Java-/Groovy-Auswertung im Tooling, kein beliebiger Groovy-Interpreter, keine automatische Änderung aller Nutzer-`.groovy`-Dateien, keine Taskverschiebung zwischen Dateien, keine automatische Fragmentaufteilung, kein Dateiumbenennungsrefactoring und kein JAR-Decompiler. Kein neuer Scheduler, kein Native Image, keine zusätzliche GRETL-Codebasis. Kein universelles Review-/Workspaceframework. Kein Push, Merge nach `main`, Release oder produktiver Joblauf.

Globale Refactoringkorrektheit für unbekannte Jobs ausserhalb des erfassten Workspacebereichs wird nicht versprochen. Die konkrete Refactoringdomäne und ihre Grenzen sind in §4.5 festgelegt.

## 2. Verifizierte Ausgangslage und zu lesende Implementierung

Der Recherchepin enthält die Runtime-Includes und den LSP/Modeler. Der abgeschlossene Include-Produktkandidat war `6f21cc82fe83d675905744b2ece0fff052baa328`; frühere Reviews sind keine Abnahme dieses Folgeauftrags. Bei Arbeitsbeginn aktuelle Revision und relevante Deltas prüfen, nicht auf den Recherchepin zurücksetzen.

| Vorhandene Stelle | Beobachteter Vertrag / Anschlussbedarf |
|---|---|
| `runtime.groovy.fragment.ApplyDirectiveReader` | Parse/Convert, genaue Literal-/Kontext-/Helper-/Shadowingregeln, UTF-16-Bereiche und Site-IDs; noch an Runtimekonfiguration und Runtimefehler gebunden. |
| `FragmentGraphResolver`, `FragmentSourceAccess`, `ScriptSourceNames` | Quellenabschluss pro Scope, portable Identitäten, Zyklen/Duplikate, Hash; Runtime nicht erneut entwickeln. |
| `syntax.SafeGroovySyntaxReader`, `GretlDocumentParser`, `GretlAstExtractor` | Sicherer bestehender Toolingparser mit Recovery und Budgets. `apply` bisher opak. |
| `workspace.ProjectResolver` | Echte Projekteinstiege plus SQLquellen; keine transitive Fragmentclosure. `JobSource(scope, documentId)` ist noch kein Verwendungsmodell. |
| `SourceRepository`, `SourceView`, `DocumentStore`, `WorkspaceManager` | Begrenzte Host-IO, Bufferoverlays, Sourcevektoren, Generationen; weiterverwenden. |
| `semantic.ProjectAnalyzer`, `SemanticModel`, `ReferenceIndex` | Deklarationen je Jobdatei; TaskRef-Aliase derzeit URI-bezogen; Ressourcen aus Quelldatei abgeleitet. Beides muss kontextsensitiv werden. |
| `query.LanguageQueries`, `CompletionQueries`, `QueryContext` | Bestehende Texteditorqueries erweitern, keine zweite Queryengine. |
| `JobGraphProjector`, `GraphFormProjector`, `TargetPreviewProjector` | Projektcontainer, Sourceanker, statische Planung und Formularprojektion erhalten. |
| `EditabilityAnalyzer`, `SemanticEditPlanner`, `ReferenceSourceEdits`, `AliasUseGuard`, `EditVerifier`, `EditPreconditions` | Guards und virtuelle Postimages vorhanden. `EditVerifier` analysiert erfasste Daten erneut ohne neue IO. |
| `gretl-lsp/.../GretlProtocol`, `GretlLanguageServer`, `GretlTextDocumentService` | Version-1-Erweiterungsprotokoll, Standard-LSP, Snapshot-/Serveridentitäten. Kontextauswahl und Includeinformationen ergänzen. |
| `vscode/gretl-vscode/src/extension.ts` | Selector `gretl-runtime`; Watcher bisher `**/{job.groovy,settings.gretl,*.sql}`. Beliebig benannte Fragmente brauchen gezielte Zugehörigkeit und Synchronisierung. |
| `src/modeler/provider.ts`, `edit-plan.ts`, `src/edit-guard.ts` | Native WorkspaceEdits, erneute Guards, Panelidentitäten und Undo; nicht umgehen. |
| `ApplyEditCompatibilityTest`, `ApplyIncludeServerIT`, `ApplyPartialCoverageTest` | Bestehende Übergangsnachweise. Positive Include-Kenntnis ergänzen, negative Coveragefälle behalten. |
| `tools/gretl-tooling/review.mjs` | An alten Toolingauftrag gebunden. Kein unveränderter generischer Launcher für diesen Auftrag. |

`tools/script-includes/` war am Recherchepin nicht vorhanden. Die letzte Include-Reviewkette ist in `WORK_QUEUE.md` über externe lokale Belege dokumentiert. **Nicht erfinden**, dass ein generischer Include-Reviewer bereits im Repository liegt. Lokal vorhandene passende Mechanik darf nach Prüfung wiederverwendet werden (§9).

Die vorhandenen Klassen oben sind Quellenbefunde; alle folgenden **neuen** Typen und Methoden sind Implementierungsvorschläge. Codex darf Namen und interne Signaturen eng an den echten Code anpassen, muss aber Verantwortung, Invarianten und Abnahmeziele erhalten. Kein grossflächiger Refactor aus Stilgründen.

## 3. Öffentlicher Funktionsvertrag: IT01–IT24

### IT01 – Runtime bleibt kompatibel

Alle bestehenden zulässigen `apply from:`-Jobs, Subprojekte, Hashes, Quelldiagnosen und Job-JARs behalten ihre Bedeutung. Keine neue Top-Level-Syntax, kein Wechsel der Pfadbasen oder des Bindingverhaltens. Die nötige neutrale Extraktion darf die Runtime nicht an den Tooling-Core oder LSP koppeln. Ältere Paketformate bleiben lesbar; dieser Auftrag ändert keine Metadatenschemata.

### IT02 – Gemeinsame Includeerkennung ohne Ausführung

Runtime und Tooling verwenden dieselbe fachliche Includeerkennung für vollständig parsebare Quellen. Es gibt nicht zwei auseinanderlaufende Implementierungen der Helper-/Shadowing-/Literalregeln. AST-Konvertierung bleibt ohne semantische Klassenauflösung, Scriptinstanziierung, Grape, ASTTest, globale Jobtransformationen, ServiceLoader auf Jobclasspath, Compilerplugins oder Handlersuche. Tooling-Recovery ist erlaubt, aber keine Grundlage für fälschlich vollständige Analyse oder Writes.

### IT03 – Transitive, sichere Quellenauflösung

Erfasse verschachtelte lokale Includes, fehlende Ziele, unsichere Pfade, Zyklen, doppelte Anwendung im selben Scope, unzulässige Fragmentdefaults und als Fragment verwendete Projekteinstiege entsprechend dem implementierten Runtimevertrag. Gleiche Basenames bleiben verschieden. Physische Quelle nur einmal erfassen, Anwendungen getrennt halten. Auflösung ist auf Definitionroot **und** Workspace-Readpolicy begrenzt. Keine URL-/Glob-/Netzwerkauflösung und kein Symlinkfolgen.

Ein fehlendes oder nicht lesbares Ziel reduziert die Vollständigkeit sichtbar; es ist kein erfolgreicher leerer Teilgraph. Fehlermeldung am verursachenden Include-Literal, Ziel und Einbindekette als Related Information. Syntaxfehler im Fragment zusätzlich an dessen tatsächlicher Position.

### IT04 – Quelle und Anwendungskontext getrennt

Die Schlüssel unterscheiden mindestens Projektidentität, echten Einstieg, besitzenden Subprojektscope und physische Source-URI. Ein Shared-Fragment kann z. B. `:north:pack` und `:south:pack` deklarieren, ohne globale Taskkollision. Scriptlokale `def`-/TaskRef-Aliase bleiben **datei- und anwendungslokal**; sie werden nicht an andere Fragmente exportiert. Parameter und namengebundene GRETL-Referenzen teilen den besitzenden GRETL-Scope.

### IT05 – Zwei Pfadbasen, auch für SQL

`apply from:` relativ zur enthaltenden Quelldatei; `file(...)`, `ctx.file(...)` und statisch erfasste SQLressourcen relativ zum besitzenden Job/Subprojekt. `workPath` und CWD-bezogene Parameter behalten ihre eigenen Regeln. Dokumentanker und Ressourcenbasis dürfen niemals dieselbe implizite Variable sein. Auch zwei Anwendungen derselben Quelle mit gleichem `file('data/input.txt')` können verschiedene Ressourcen meinen.

### IT06 – Registrierungsreihenfolge und konservativer Kontrollfluss

Im sicheren geradlinigen Definitionsfall entspricht die Reihenfolge der IDEdeklarationen der Runtime: vor dem Include, depth-first im Fragment, danach weiter im Einbinder. Includes sind keine Task-/Dependencykanten. Die bestehende `PlanSemantics` bestimmt Zielclosure und Reihenfolge.

Ein statisch deklarierter Quellenabschluss beweist nicht, dass alle Registrierungen erreicht werden. Top-Level-`return`, `throw`, Schleifen, Bedingungen, aufgerufene Hilfsmethoden und sonstiger deklarationswirksamer opaker Code dürfen nicht durch Includeauflösung zu `COMPLETE` werden. Es genügt eine konservative Unvollständigkeitsmarkierung; kein neuer allgemeiner Kontrollflussinterpreter. Eindeutig nicht erreichbare oder nur potenzielle Deklarationen nicht als garantiert laufend darstellen. Das Runtime-Hashverhalten für Includes nach `return` bleibt erhalten.

### IT07 – Ungespeicherte Inhalte haben Vorrang

Ein gemeinsamer unveränderlicher Capture verwendet die neuesten bestätigten Bufferoverlays für Einstieg, Settings, Fragmente und erfasste SQLs. Analyse, Navigation, Graph und Editplanung verwenden denselben Capture, keine getrennten Diskreads. Ein offenes neues Fragment ohne gespeicherte Datei darf innerhalb der Readpolicy analysiert werden; Zustand „noch nicht gespeichert“ bleibt sichtbar. Die IDE speichert oder startet es nicht automatisch. `untitled` ohne bewiesene Datei-/Definitionsbasis erlaubt keine erratenen relativen Includes.

### IT08 – Alle betroffenen Kontexte invalidieren

Edit/Save/Close/Create/Delete/Rename einer relevanten Quelle, Änderung eines Include-Literals, Settingsänderung, Workspacewechsel oder Kontextwechsel invalidiert betroffene Captures, Referenzindizes, Graphen und Editpläne. Fehlende Includeziele werden als beobachtete Abwesenheit erfasst, damit ihre spätere Erstellung erkannt wird. Entfernte Includes lösen alte Mitgliedschaften und Diagnosen. Stale Antworten und alte Kontexttokens dürfen nicht publizieren oder editieren.

Eine Shared-Quelle invalidiert **alle** erfassten Anwendungen, nicht nur das zuletzt aktive Panel. Ein neu entdeckter zweiter Besitzer invalidiert auch einen früher geplanten, vermeintlich eindeutigen Edit. Die Integrität hängt deshalb neben Sourcevektoren an einer Ownershipgeneration.

### IT09 – Grenzen und Budgets bleiben ehrlich

Readpolicy, ausgeschlossene/nested Workspacewurzeln, sichere Hostzugriffe, Cancellation, Recovery- und Capturelimits erhalten. Keine rekursive ungegrenzte Suche in beliebigen Ordnern. Die Toolingbudgets dürfen kleiner als die Runtimebudgets sein; dann `PARTIAL`/klarer Analysegrenzhinweis statt „Job ungültig“. Limitierung darf nicht nach erfolgreichem leerem Ergebnis aussehen. Keine Erhöhung sämtlicher Grenzwerte als Reparatur.

### IT10 – Fragment-Language-Support und Dateiwatcher

Tatsächlich erfasste GRETL-Fragmente mit beliebigem `.groovy`-Dateinamen bekommen beim Öffnen Sprachunterstützung und liefern ungespeicherte Änderungen an den Server. Geschlossene Fragmentdateien müssen über Fileevents aktualisiert werden. Ein Watcher für `**/*.groovy` darf Ereignisse melden, ist aber **keine globale Sprachassoziation und kein Auftrag, jede Groovydatei zu parsen**.

Unbeteiligte Groovy-/Gradledateien behalten ihren Modus. Serverseitige Zugehörigkeit, Client-Sprachübergang und neue Dokumentinkarnation müssen zusammenpassen. Ein zuvor bereits geöffneter dirty Groovypuffer darf nicht durch die erste Diskanalyse verloren gehen oder als editierbar aktueller Stand durchrutschen. Keine automatische Speicherung, kein Dateirewrite, kein Profilumbau.

### IT11 – Dateiübergreifende Semantik

Tasks, Actions, Parameter, Defaults, `dependsOn`, `after`, `result`, `resultOptional` und qualifizierte Projektverweise werden im richtigen Anwendungskontext aufgelöst. Vorwärtsreferenzen über Dateien funktionieren. Ergebnisrechte bleiben von `dependsOn` abhängig, nicht von `after` oder Include-Reihenfolge. Lokale TaskRef-Aliase anderer Scripts bleiben unsichtbar. Mehrdeutige/doppelte/dynamische Referenzen bleiben nachvollziehbar unsicher.

### IT12 – Modeler zeigt den vollständigen statisch bekannten Graphen

Eine sonst statisch vollständige Include-Fixture liefert vollständige Deklarations-/Referenz-/Graphcoverage und `COMPLETE`, soweit auch die übrigen bestehenden Statusvoraussetzungen erfüllt sind. Fragmenttasks erscheinen in ihrem **besitzenden** Projektcontainer mit Originaldatei/Position, nicht in erfundenen Subprojekten. Shared-Quelle ergibt je Scope separate Nodes. Graph, Zielvorschau, Quellenansicht und Formularprojektion stimmen überein. Kein temporärer vollständiger Graph aus einem halb synchronisierten Capture.

Opaker/dynamischer Code bleibt partiell und sichtbar. Includebeziehungen dürfen in einer Quellenanzeige erklärt werden, sind aber keine neuen ausführbaren DAGkanten.

### IT13 – Navigation, Hover, Referenzen

`Go to Definition`/DocumentLink am Include-Literal öffnet das tatsächliche Ziel. Namensreferenzen und Ergebnisfelder navigieren in die richtige Fragmentdeklaration. `Find References` umfasst den vollständigen bekannten Analysekontext. Hover nennt bei Bedarf Verwendungskontext und Quelldatei. Document Symbols bleibt dokumentbezogen; nicht sämtliche Fragmentdeklarationen mit falschen Ranges in das Outline der Einstiegsdatei kopieren.

Bei mehreren möglichen Kontexten keine zufällige `findFirst()`-Auflösung. Kontextunabhängig identische Dateilinks dürfen funktionieren; kontextabhängige fachliche Queries benötigen die explizite Auswahl oder eine klar bezeichnete Mehrdeutigkeit.

### IT14 – Completion einschliesslich Include-Pfaden

Task-/Parameter-/Ergebniscompletion umfasst Fragmente im gewählten Scope. Gleiche lokale Namen aus anderen Scopes werden korrekt qualifiziert oder nicht als lokale Alternativen angeboten. Include-Pfadcompletion bleibt innerhalb erlaubter Root/Policy, listet nur passende sichere Verzeichnisse und Groovyquellen, erhält Quote-/Escapeformat und führt keinen Code aus.

Auch ein noch unvollständiges Stringliteral darf begrenzte Pfadvorschläge bekommen, ohne als gültige Include-Deklaration zu gelten. Dateilisting wird im Host/Capture begrenzt beschafft; Queryklassen betreiben keine versteckte IO. Nicht unterstütztes Listing liefert einen sichtbaren begrenzten Zustand, kein behauptetes vollständiges Leerresultat.

### IT15 – Eindeutige Kontextauswahl und Shared-Fragmente

Ein aus einem Job geöffnetes Fragment kennt dessen Kontext. Bei genau einem nachgewiesenen Besitzer darf dieser automatisch gewählt werden. Bei mehreren Besitzern erscheint eine explizite Auswahl mit Einstieg, Scope und Ressourcenbasis; Auswahl ist an Dokumentinkarnation und Ownershipgeneration gebunden. Ein allein geöffnetes, noch nicht zugeordnetes Fragment bleibt syntax-/lokal lesbar, aber ist kein geratenes Rootprojekt.

Das bestehende bewusste Kommando zum Verwenden einer Datei als eigenständiger Runtimejob bleibt möglich; es ist eine **explizite andere Rolle**, nicht die automatische Interpretation jedes Fragments. Pinned Modelerpanels halten ihren eigenen Kontext. Die normale Texteditorsicht besitzt pro Dokument eine nachvollziehbare aktive Kontextauswahl; kein stilles Umschalten durch einen anderen Hintergrundrequest.

### IT16 – Diagnosen sind quellen- und kontextgenau

Fehler zeigen die tatsächliche Source-URI und UTF-16-Range. Includeprobleme zeigen Einbindestelle und Kette. Gemeinsame Quellen können je Verwendung verschiedene semantische Probleme haben; diese dürfen sich beim Publizieren nicht gegenseitig überschreiben. Gleichartige Diagnosen deduplizieren, kontextabhängige Unterschiede kennzeichnen. Ein fehlender Besitzer, Budgetlimit oder unbekannter Kontrollfluss wird nicht als bewiesener Taskfehler ausgegeben. DE/EN-Strukturcodes erhalten.

### IT17 – Sichere Quellenbearbeitung über Dateien

In eindeutig besessenen, vollständig erfassten Quellen müssen die vorhandenen erlaubten lokalen Feld-/Optionsänderungen und strukturellen Intents auch auf Fragmenttasks funktionieren. Edits treffen die Originaldatei, nicht eine zusammenkopierte Darstellung. Neue Deklarationen werden nur in einer ausdrücklich bestimmten zulässigen Zieldatei angelegt; Defaults nur im echten Einstieg/Settings. Keine neue Drag-and-drop-Dateiverschiebung.

Kommentar-/EOL-/Unicodeerhalt, minimale Replacements, bestehende Dirtytexte und nativer Undo bleiben verbindlich. Ein No-op darf keine Dateien umformatieren. Sichere lokale skalare Änderungen in ansonsten partiellen **eindeutig zugeordneten** Quellen bleiben zulässig, sofern bestehende lokale Nachweise genügen.

### IT18 – Rename und Referenzänderungen bei nachgewiesener Eindeutigkeit

Für eindeutige Quellen/Anwendungen: Task-/Parameter-Rename ändert Deklaration und alle bekannten gebundenen Referenzen über Einstiegs-/Fragment-/Settingsdateien, einschliesslich Defaults, Dependencies, After und Ergebniszugriffen. Keine Stringsuche/-ersetzung in Kommentaren, beliebigen Strings oder nicht gebundenem Groovy. Namen, Scope, Kollisionen und nachher unveränderte Bindungen unabhängig prüfen.

Add/RemoveDependency und BindResult funktionieren zwischen Fragmenten desselben Definitionsgraphen; alle gleichwirkenden Wege über generische Setter, rekursive Werte und Formularbatches müssen denselben Sicherheitsvertrag erfüllen. Ein Batch mit einem unzulässigen Anteil liefert keinen teilweise anwendbaren Plan.

### IT19 – Schreibgrenze für gemeinsam verwendete Quellen

In dieser Ausbaustufe gilt: **Kein automatisch geplanter Quelltextedit in einer Quelle mit mehreren Anwendungskontexten oder unvollständiger Ownershipkenntnis.** Das betrifft auch skalare Writes in eine solche Shared-Quelle. Die manuelle Textbearbeitung bleibt offen und die Auswirkungen werden angezeigt.

Entscheidend ist der gesamte **Write-Footprint**, nicht nur die Deklarationsdatei: Ein eindeutiger Task darf nicht umbenannt werden, wenn eine zu ändernde Referenz in einer mehrfach verwendeten Fragmentquelle liegt. Umgekehrt darf eine unabhängige Änderung in einer eindeutig besessenen Datei nicht allein deshalb blockieren, weil irgendein anderes Shared-Fragment im Projekt vorkommt.

Lesende Kontextauswahl macht eine gemeinsame Datei nicht exklusiv. Eine neue Referenz oder Dependency **auf** einen Shared-Produzenten aus einer eindeutigen Consumerdatei darf dagegen erlaubt sein, wenn Bindung, Graphvertrag und ausschliesslich eindeutiger Write-Footprint bewiesen sind; Lesen eines Shared-Symbols ist kein Schreiben seiner Quelle. Ablehnung mit strukturiertem Grund, betroffenen Besitzern und unveränderten Quellen. Kontextübergreifende globale Shared-Refactorings sind ein späterer Auftrag, nicht ein versteckter Mussumfang dieses Projekts.

### IT20 – Postimages ohne neue IO vollständig prüfen

Vor der Ausgabe eines Edits dessen virtuelle Postimages aus dem eingefrorenen Capture neu auflösen/binden. Zugehörigkeit, Ursprungseinstieg, Ressourcenbasis und Kontext bleiben erhalten. Keine neue Dateibeschaffung im Verifier, kein Rückfall auf den letzten Diskstand. Fehlt eine benötigte Quelle/ein Ownershipbeweis, Edit ablehnen und frische Erfassung verlangen.

Neue Fehler, Kollisionen, verlorene Referenzen, geänderte Resultrechte, versehentlich veränderte Defaults und Scopewechsel dürfen nicht unbemerkt bleiben. Bisherige Fehler anderer Quellen dürfen neue Fehler nicht numerisch „aufheben“. Shared-Anwendungen nicht durch einen einzeldateibasierten Reparse verschwinden lassen.

### IT21 – Native Anwendung mit vollständigen Preconditions

Ein Editplan trägt Snapshot-/Server-/Source-/Ownership-/Kontextidentität. Host und Server prüfen vor Anwendung nochmals Aktualität. Mehrere Zielpuffer vor geplanter Mehrdateienänderung öffnen/synchronisieren und erst dann neu planen; keinen Diskplan gegen inzwischen dirty Buffer anwenden. Alle TextEdits als **ein** nativer WorkspaceEdit, mit Versionsbedingungen, anwenden. Keine eigene dateiweise Schreibschleife und keine erfundene atomare Filesystemtransaktion.

Stale Plan nach Fragmentänderung, Kontextwechsel, zweitem Besitzer, Close/Reopen oder Serverrestart wird vor Mutation verworfen. Save/Undo/Redo betreffen die tatsächlich bearbeiteten Dokumente, nicht blind nur die gepinnte Einstiegdatei. Die bestehenden Hostguards bleiben auch nach asynchroner Konvertierung wirksam.

### IT22 – Reale Benutzer- und Runtimewege

Am Schluss ein frisch gebautes, isoliert installiertes VSIX mit echte(m) stdio-LSP prüfen: vollständiger Includegraph, Navigation, dirty Fragmentaktualisierung, eindeutiges Mehrdateien-Rename, native Undo/Redo und Shared-Ablehnung. Anschliessend **denselben gespeicherten Fixturetext** getrennt mit der Runtime ausführen und fachliche Ausgabe prüfen. Editieren selbst startet weder Runtime noch Tasks.

JAR-Prozessregression ist bei Änderung des Runtimeclasspath/neutraler Extraktion fällig; ohne weitere Paketänderung nicht je Phase sämtliche Paketsuiten neu starten. Herkunft der tatsächlichen Server-/VSIX-/Runtimeartefakte und gespeicherten Texte nachweisen, keine alte Installation verwenden.

### IT23 – Modul-, Protokoll- und Paketgrenzen

`gretl-language-contract` bleibt JDK-only. Core/LSP erhalten keine Runtime-, Runner-, Taskprovider- oder Packager-Produktionsabhängigkeit. Gemeinsame Syntax wird eng neutral extrahiert (§4.1), nicht über Runtimeimporte wiederverwendet. Katalog, Jobmetadaten und LSP-Featureversionen bleiben getrennt.

Neue Kontextinformationen und Requests werden explizit ausgehandelt. Alte Clients dürfen keine kontextsensitive Editfähigkeit erhalten, die sie nicht korrekt validieren können. Standard-LSP funktioniert weiterhin; fehlende Featurecapability führt zu ehrlichem begrenztem Verhalten. Das ausgelieferte Server-/Clientbundle und seine Inventar-/Dependencychecks werden angepasst, nicht deaktiviert.

### IT24 – Nachweise, Dokumentation und gezielte Ablösung der Übergangstests

Alle fünf Phasen haben echte autonome unabhängige Abnahmen, zielbezogene Tests und neue Reviewer-Gegenproben. Historische Abnahmen bleiben historische Abnahmen. Laufende Anleitung erklärt vollständige **statische** Includes, Kontextwahl, Shared-Schreibgrenze, Budgets und dynamische Partialfälle. Bestehende gültige Nicht-Include-Jobs bleiben benutzbar. Alte reine Partial-Annahmen nur dort ersetzen, wo dieser Auftrag eine nachgewiesene positive Funktion verlangt (§7.3).

## 4. Architekturentscheidungen

### 4.1 Gemeinsame Syntax ohne Runtimeimport

**Vorgesehene Lösung:** ein einziges kleines neutrales Mavenmodul `gretl-script-syntax` (neu), Package vorzugsweise `ch.so.agi.gretl.syntax`. Es enthält die aus dem heutigen Reader herausgelöste Include-AST-/Literal-/Helper-/Shadowingauswertung und nötige unveränderliche Daten. Erlaubte Abhängigkeiten: JDK und die bereits verwendete Groovyversion; optional reine Werte aus `gretl-language-contract`, soweit konkret nötig. Keine Filesystemauflösung, Runtimefehler, i18n-Service, SourceView, LSP4J, Provider oder Registrierung.

```text
gretl-language-contract               JDK-only, bestehende Namen/Graphregeln

gretl-script-syntax                    gemeinsame rein syntaktische Includeprüfung
  ↑                           ↑
gretl-groovy                   gretl-tooling-core
  Runtimeadapter              sicherer Parser + Workspace + Semantik
                                      ↑
                                  gretl-lsp
                                      ↑
                              vorhandener VS-Code-Client
```

Das neue Modul ist wegen der inzwischen benötigten gemeinsamen fachlichen Erkennung gerechtfertigt; nicht zur allgemeinen Modulreorganisation. Wenn der tatsächliche Startstand bereits einen gleichwertigen neutralen Ort besitzt, diesen verwenden statt ein Duplikat erstellen. Nicht den Groovy-abhängigen Reader in den JDK-only Language-Contract verschieben.

Der Runtime-`ApplyDirectiveReader` bleibt eine kompatible Fassade: bisherige öffentliche Result-/Directive-/Problemformen und Exception-/i18n-/SourceLocation-Abbildung bewahren. Bestehenden sicheren Toolingparser **nicht** durch die schwächere Runtime-Classloaderhülle ersetzen. Im Tooling den bereits sicher konvertierten AST und passende Tokens dem gemeinsamen Inspector übergeben, statt pro Include einen zweiten vollständigen Parse zu starten.

Alle vorhandenen komplexen Helper-/Shadowingregressionen bleiben wirksam. Scriptname `Map.groovy`, JDK-Map-Supertypen, lokale eigene `apply`-Methoden, Importaliase und Unicodeoffsets nicht neu vereinfacht interpretieren. Falls dazu reine Namens-/Positionshelfer bewegt werden müssen, ihre Bytesemantik erhalten und Kompatibilitätsfassaden lassen. Kein unkontrolliertes Klassenladen zur Symbolauflösung.

**Auslieferungspflicht der Extraktion:** Runtime-/Packagerclasspath, isolierter Compilerclasspath, Distribution, Server-Shading und Enforcer-/Archivregeln auf die neue kleine transitive Abhängigkeit prüfen. Nicht annehmen, dass ein grüner Coretest das ausführbare Job-JAR beweist.

### 4.2 Physische Quellen und logische Anwendungen

Vorgeschlagene unveränderliche Kerndaten; tatsächliche Konstruktoren kompatibel überladen, keine unnötige breite öffentliche API schaffen:

```java
record ApplicationId(ProjectId project, URI entryDocument,
                     String ownerScope, URI sourceDocument) {}
record SourceApplication(ApplicationId id, URI resourceBase,
                         SourceRole role, List<IncludeSiteId> includeChain) {}
record IncludeSiteId(URI sourceDocument, int startUtf16) {}
record IncludeLink(ApplicationId caller, IncludeSiteId site,
                   URI targetDocument, SourceApplication target,
                   IncludeResolution resolution) {}
record IncludeClosure(List<SourceApplication> applications,
                      List<IncludeLink> links,
                      List<RegistrationEvent> registrationOrder,
                      boolean sourcesComplete, boolean orderProven) {}
```

`SourceRole`: ENTRY oder FRAGMENT. Syntaxcache nach URI/Inhalt/Parseroptionen; semantische Anwendung nach `ApplicationId`. Dieselbe Quelle kann mehrfach verwendet werden, aber pro Scope nicht doppelt angewendet werden. Site-Offset ist **snapshotlokal**; nicht als dauerhaft stabile UI-ID über vorangehende Textedits verwenden.

`ResolvedProject.jobs()` bleibt die Liste echter Einstiege. Fragmente separat über `applications`/`includeClosure` hinzufügen; nicht als weitere Jobs in die Liste schmuggeln. `documents: Map<URI, ParsedDocument>` darf weiterhin physische Parseergebnisse teilen. Contextabhängige Bindings/Fehler/Resources werden nicht in diesem URIcache gespeichert.

Symbolidentität bleibt innerhalb eines Projekts mit Namespace/qualifiziertem Namen kompatibel. Zusätzlich muss jede Symboldeklaration ihre eindeutige `ApplicationId` tragen oder über eine eindeutige Side-Map erreichbar sein. Ein aus `Anchor.documentId` mittels `findFirst()` abgeleiteter Scope ist unzulässig. TaskRef-Aliasindex nach `(ApplicationId, lokaler Name)`, nicht nur URI.

### 4.3 Quellenaufnahme und Registrierung als verschiedene Graphen

Includeclosure = Quellbeziehungen; Taskgraph = fachliche Dependencies. Beide niemals vermischen. Quelle rekursiv in Quellreihenfolge aufnehmen, aus ihrem Token-/ASTmodell eine geordnete Folge aus Deklaration, Apply-Site und opakem Kontrollbereich liefern. Deklarationsereignisse an der Apply-Site erweitern; dadurch bleiben Registrierungsindizes für unabhängige Tasks korrekt.

Schon bei defektem Kind sichere lokale Parentinformationen behalten, aber Vollständigkeit herabsetzen. Recovery kann ein geschlossenes Include-Literal als potenzielle Quellenreferenz anbieten; sie darf weder unsichere Pfade lesen noch den ganzen Graph vollständig machen. Roleprüfung für Fragmentdefaults gehört zur Anwendung und nicht in einen nur URI-gekeyten gemeinsamen Parsecache.

### 4.4 Ressourcenbasis und reine Postimages

`SourceUris.jobResource(URI source, ...)` wird bisher aus Deklarationsdateien aufgerufen. Ergänze eine klar bezeichnete Auflösung anhand von `SourceApplication.resourceBase`, z. B. `resolveJobResource(URI ownerDirectory, String literal)`, und ersetze die relevanten Fragmentpfade in `ProjectAnalyzer`, SQLbeschaffung, `SqlTemplateAnalyzer`, Completion und Linkqueries. Normale Entryfälle bleiben identisch.

Der EditVerifier muss beim Reparse des eingefrorenen Quellbestands denselben Eigentümer-/Entrykontext und dieselben erfassten Inventoryinformationen verwenden. Defaultkonstruktoren eines `ProjectRequest` dürfen die Ownerauswahl, zusätzliche Quellen oder Scopeinformationen nicht verlieren. Ein neu benötigtes ungesichertes Listing/eine neue Quelle in der Postimageprüfung führt zu Ablehnung, nicht zu verstecktem Hostzugriff.

### 4.5 Ownershipkenntnis und Refactoringdomäne

Nur geöffnete Dateien als Besitzer zu zählen wäre unsicher. Verwende einen **begrenzten statischen Ownerindex** für die unterstützten Einstiege im jeweiligen Workspacebereich: `settings.gretl`/`job.groovy` über den vorhandenen `listProjectEntries`-Vertrag plus ausdrücklich registrierte benutzerdefinierte Einstiegdateien. Settings-Projekteinstiege nicht zusätzlich als eigenständige Singlejobs doppelt zählen; die Projektzuordnung muss dieselbe sein wie im Resolver. Das Runtimeverbot, einen echten Projekteinstieg als Fragment zu importieren, wird innerhalb der jeweiligen Runtime-Projektdefinition geprüft, nicht pauschal gegen sämtliche Entrykandidaten aller fremden Workspaceprojekte. Der Ownerindex darf dadurch eine zulässige Includequelle nicht plötzlich als ungültig erklären; erkannte weitere Verwendungen wirken auf die Editierbarkeit.

Der Index hält Source → Anwendungen, erfasste Einstiege/Verzeichnislistings, beobachtete Abwesenheiten, Vollständigkeit und eine monotone Ownershipgeneration. Er darf durch denselben Resolver lesen, aber keinen zweiten Workspace-/Semantikstack bauen. Fehlende Rootteile, Limits, verbotene Pfade, unbekannte dynamische Einbindungen oder nicht abschliessend prüfbare Owner ergeben **UNKNOWN**, nicht „genau ein Owner“.

Vor einem Mehrdateien-Edit muss im vereinbarten Bereich genügend vollständige Ownershipkenntnis für **jede tatsächlich zu schreibende Quelle** bestehen. Positive Editfälle in einem kleinen gewöhnlichen Workspace müssen diese Voraussetzung erreichen können. Ein absichtlich ungeöffneter zweiter Standardjob mit gemeinsamer Quelle muss erkannt werden. Änderungen an Einstieglisten, Einstellungen, expliziten Rootregistrierungen oder Includeclosure invalidieren den Beweis.

Diese Domäne umfasst die unterstützten/registrierten Workspace-Einstiege, nicht beliebige unbekannte externe Konsumenten oder jede möglicherweise ausführbare `.groovy`-Datei. Diese Grenze dokumentieren. Ein unbekanntes proprietäres Entryformat nicht erraten. Schon nachgewiesene Mehrfachverwendung ausserhalb des aktiven Projekts im selben erfassten Workspace darf nicht ignoriert werden.

Der vollständige Ownerindex muss nicht vor jeder Completion neu gescannt werden. Bounded Capture/Cache mit Generationen wiederverwenden und Schreibbeweise bei Bedarf erwerben. Während Erfassung läuft: lesende Teilinformationen möglich, strukturelle Editfreigabe bleibt gesperrt. Keine frühe Freigabe auf Basis einer vorläufig leeren Ownerliste.

### 4.6 Bewusste Schreibpolitik für diese Lieferung

Schreiboperationen werden pro vollständigem Footprint geprüft. Bei mehreren Anwendungen einer berührten physischen Quelle: `EDIT_SHARED_SOURCE` (Vorschlag). Bei unvollständigem Ownerbeweis: `EDIT_OWNER_COVERAGE`. Bei unvollständiger Semantik: bestehendes `EDIT_REFERENCE_COVERAGE` bzw. ein präziserer bestehender Code.

Das gilt unabhängig davon, ob der Aufruf aus Rename, Quick Fix, Formularbatch, SetTaskOption, BindResult, direktem Core-API oder Modeler stammt. Nur den UIbutton zu deaktivieren genügt nicht. Physische doppelte Replacements gleicher Range/neuen Texts deduplizieren; unterschiedliche beabsichtigte Replacements derselben Range ablehnen. Keine automatische Vervielfältigung eines Shared-Fragments als vermeintliche Reparatur.

**Zwischenphasen schützen:** Vollständige lesende Analyse in P3 darf bestehende Editguards nicht versehentlich öffnen, bevor P4 Kontext-/Footprint-/Postimageprüfungen liefert. Eine explizite noch fehlende Include-Editfähigkeit getrennt von Coverage behandeln. Sichere lokale Skalaredits können über ihren vorhandenen eng begrenzten Pfad bleiben; strukturelle Mehrdateien-Edits erst mit tatsächlich vorhandenem Beweisweg. Am Ende keine künstliche generelle Include-Schreibsperre übrig lassen.

### 4.7 Client-/Serverkontext und kleine Protokollerweiterung

Der Standard-LSP trägt bei Definition/Completion/Rename keinen GRETL-Projektscope im üblichen Request. Ergänze deshalb serverseitige Kontextauswahl und einen schmalen expliziten Hostvertrag, statt beliebige proprietäre Parameter in Standardrequests zu verstecken.

Vorgeschlagene neue, typisierte Erweiterungen:

- `gretl/sourceContexts`: Kontexte/Entryrolle/Zugehörigkeitsstatus einer Source-URI mit Server-/Ownershipidentität.
- `gretl/selectSourceContext`: Auswahl eines **vom Server gelieferten** Contexttokens, gebunden an Dokumentinkarnation und Ownershipgeneration; bestätigt die neue Kontextgeneration.
- `gretl/sourceMembershipChanged`: gebündelte Zugehörigkeitsänderungen zum Synchronisieren tatsächlich verwendeter Fragmente.
- Vorhandene `gretl/graph`, `gretl/navigation`, `gretl/planEdit` um einen optionalen Contexttoken ergänzen, soweit das gepinnte Panel sonst nicht eindeutig ist.

Das sind **neue Vorschläge**, keine heute vorhandenen Endpunkte. Eine bereits gleichwertige API im tatsächlichen Checkout verwenden. Bestehende `GretlProtocol.VERSION=1` kann für rein additive optionale Daten bleiben; ein Featurecapabilityfeld, z. B. `experimental.gretl.includeToolingVersion = 1`, handelt die neue Funktion aus. Falls eine zwingend inkompatible Wireänderung unvermeidbar ist, Version explizit und konsistent erhöhen und alte Clients kontrolliert ablehnen; niemals Semantik unter gleicher Versionsbehauptung still ändern. Kein Protokollwechsel allein für kosmetische Felder.

Contexttokens sind keine Berechtigung zum Lesen fremder Roots. Alle empfangenen URIs/Tokens gegen Readpolicy und aktuelle serverseitige Mitgliedschaften prüfen. Alte Clients ohne Featurecapability bekommen keine kontextabhängigen Mehrdateien-Edits, deren Preconditions sie nicht prüfen können. Native Queries verwenden die bestätigte Auswahl; Modelerpanels pinnen ihren Kontext unabhängig vom Texteditor.

Membership-Synchronisierung: erfasste Diskdateien können bereits im Client ungespeichert geöffnet sein. Der Host erkennt diese beim Empfang einer Mitgliedschaft, überführt nur legitime Fragmente in die GRETL-Synchronisierung und wartet auf neue `didOpen`-/Inkarnationsdaten, bevor er eine für Writes nutzbare frische Ansicht bestätigt. Keine kurzzeitige Bearbeitung gegen die veraltete Diskquelle. Bei Sprachübergängen Pending Requests invalidieren; Benutzertext und Dirtyflag erhalten.

### 4.8 Quellenkontext im Modeler und native Textoperationen

Projektcontainer aus Entry/Scope, nicht aus Fragment-URI berechnen. Nodes zeigen Quelle und Kontext; gleiche physische Definition in zwei Scopes hat verschiedene semantische Nodes. Klick öffnet Originaldatei/Range im gewählten Kontext. Include-Linie braucht keinen neuen ausführbaren Knoten; ein optioneller Quellenhinweis genügt.

Persistente Layoutkeys enthalten Projekt-/Scope-/Symbolidentität und eine sinnvolle Sourcezuordnung, nicht nur Basename oder kurzlebige Snapshot-ID. Keine Port-/Kantenverwechslung bei Shared-Tasks. Native Textedits bleiben im Extensionhost, nicht im Reactfrontend erzeugt.

Der bisherige Panelpfad für Save/Undo/Redo ist an die gepinnte Source gekoppelt. Nach einem Edit in einem Fragment muss der Benutzerpfad die tatsächlich geänderten Dokumente berücksichtigen: Edittransaktion merken, Originalquellen öffnen/fokussieren und vorhandene native Undo-/Redo-Historie verwenden; keine eigene inverse Groovy-Patchengine. Bestehende Labels nur dort präzisieren, wo sonst fälschlich nur der Einstieg gespeichert würde. Ein gestagertes Filesystemsave ist keine garantierte atomare Mehrdateientransaktion; nicht behaupten.

## 5. Technischer Zuschnitt bis auf Klassen und Methoden

Die folgenden neuen Signaturen beschreiben Verantwortungen und Rückgabeverträge; sie sind an tatsächliche Java-/Recordkonventionen anzupassen. Vorhandene APIs kompatibel überladen, nicht pauschal alle Aufrufer umbenennen. Keine neue Klasse nur zum Erfüllen einer Liste.

### 5.1 Neutrale Include-Syntax

**Neu: `ApplyAstInspector` im neutralen Modul.** Kernmethode etwa:

```java
ApplySyntaxResult inspect(ApplySyntaxInput input, Runnable checkCancellation);
```

`ApplySyntaxInput` enthält logische Source-ID, Originaltext, den bereits konvertierten Groovy-AST und einen exakt definierten Token-/Koordinatenadapter. Der Inspector führt keine Parsing-/Classpath-/IOpipeline selbst aus. `ApplySyntaxResult` enthält unveränderliche Directives, Problems, `applySeen`, relevante DSL-Defaultstellen und deklarationswirksame Kontrollflussunsicherheit. `ApplySite` hält dekodierten Pfad, Anweisungs- und Literalspan, Zeile/Spalte und snapshotlokale Site-ID.

**Bestehende Fassade `ApplyDirectiveReader.read(...)`:** ihren Parse/Convert-Einstieg behalten; Inspektion delegieren und neutralen Befund in bestehende `Directive`/`Problem`/`GroovyDslException` abbilden. Fragmentrolle weiterhin bei `read(..., fragment)` durchsetzen. Quellnamen-/Hash-/UTF-16-Verträge byte- bzw. positionsgleich halten. Tests nicht aus der Runtime verschieben und dadurch einen falschen Importzwang erzeugen.

**Tooling: `GretlDocumentParser.parse(...)`:** nach sicherem Parse und Depthguard gemeinsame Inspektion an den vorhandenen AST anschliessen. `SafeGroovySyntaxReader` behält Platform-Classloader, deaktivierte Transformationen, BOMbehandlung und Cancellation. Syntaxfehler bleiben Tooling-Recovery; kein `requireValid()`-Exceptionpfad, der eine halbfertige Editordatei komplett abstürzen lässt.

**`SyntaxModel.ParsedDocument` / `GretlAstExtractor.extract(...)`:** Includes als eigene unveränderliche Quellreferenzdaten führen. Entweder additive `includeSites` plus geordnete Statementereignisse oder eine klar nicht-symbolische Include-Deklaration verwenden. Ein gültiges erkanntes `apply` nicht zusätzlich als unaufgelöste `OPAQUE_CODE`-Insel doppelt zählen. Vollständigkeitsgewinn wird erst nach erfolgreicher Closureauflösung anerkannt. Fremde Receiver/überschattete Helper bleiben gewöhnlicher Groovycode entsprechend dem bestehenden Vertrag.

**Neu: `RegistrationSequence` (kleiner Typ oder Resolverfunktion).**

```java
List<RegistrationEvent> expand(IncludeClosure closure, ParsedSources sources,
                               CancellationToken cancellation);
```

Erweitert nur sichere Deklarations-/Applyereignisse an ihrer Quellposition. Liefert bei unbekanntem Kontrollfluss explizit `orderProven=false`. Keine Taskausführung und keine eigene Planungssemantik.

### 5.2 Includeabschluss und Ownerindex

**Neu: `ToolingIncludeResolver` im Tooling-Core**, beispielsweise:

```java
IncludeClosure resolve(DefinitionContext definition, SourceView sources,
                       ParsedSourceCache parsed, CancellationToken cancellation);
```

`DefinitionContext`: echte Einstiege, Definitionroot, Workspacepolicy und Einstellungen. `SourceView` liefert gemeinsame bereits gecapturete Bytes/Buffers. DFS-Schlüssel pro besitzendem Scope und Source, aktiver Stack für Zyklen, besuchte Anwendungen für Duplikate. Mehrfachverwendung über Scopes zulassen. Rootentry-Import, Fragmentdefaults und unsafe Paths prüfen. Nicht mit `FragmentGraphResolver` aus dem Runtimemodul verlinken; dessen Fachvertrag/Fixtures teilen, Host-IO und Recoverability bleiben unterschiedlich.

Pfadsyntax/Normalisierung soweit sinnvoll als kleine JDK-only Regeln teilen; Sicherheitsprüfung der physischen Pfadkomponenten erfolgt im vorhandenen Hostadapter **vor** einem Read. `..` darf keine vorherige Symlinkkomponente verdecken. Ein nur lexikalisch erlaubter URI ist noch kein sicherer Diskread.

**`ProjectResolver.resolve(...)`:** echte Einstiege wie bisher auflösen, dann Includeclosure aufnehmen; `ResolvedProject` additiv um Anwendungen/Links/Registrierungsereignisse ergänzen. Anschliessend explizite SQLressourcen aus allen Anwendungen mit deren Ressourcenbasis erwerben. `requestedSources` bleibt ausdrücklich begrenzt und ist keine Möglichkeit, einen beliebigen fremden Kontext einzuschmuggeln.

**Neu: `SourceOwnershipIndex`**, klein und an bestehenden WorkspaceManager gebunden:

```java
OwnershipSnapshot captureOwners(URI workspaceRoot, EntryRegistry explicitEntries,
                                CancellationToken cancellation);
OwnershipAssessment assessWriteSet(Set<URI> sourceIds, OwnershipSnapshot snapshot);
Set<ProjectId> affectedBy(Set<URI> changedSources);
```

Snapshot enthält bekannte Anwendungen, Vollständigkeit des unterstützten Entryinventars, Quellen-/Listingabhängigkeiten und Generation. Kein „keine bekannten Treffer = exklusiv“. Die Ownerzählung nutzt dieselben Projekt-/Includeverträge. Partialzustände und Abbruch unter Limits sind Teil des Ergebnisses. Verzeichnis-/Settingsänderung kann neue Besitzer erzeugen und muss vorherige Exklusivitätsbeweise invalidieren.

**`SourceRepository`/Hostadapter:** vorhandenes `read` und `listProjectEntries` verwenden. Für Pfadcompletion nötige **begrenzte** Verzeichnisauflistung additiv einführen, z. B. `listIncludeCandidates(directory, prefix, policy, budget, cancellation)`. Defaultadapter darf explizit unsupported melden, nicht fälschlich vollständige Leere. Liste/Version bzw. Inventorygeneration im Capture speichern; keine verborgene IO aus Completion oder EditVerifier.

### 5.3 Captures, Buffers und Updates

**`SourceView.read(...)` / `freeze()`:** alle erreichbaren erlaubten Quellen und misslungenen Reads erfassen. Bestehendes Buffer-over-Disk-Verhalten erhalten. Kontextzuordnungen nicht als zweite Textkopie pro Anwendung speichern. Sourcecount zählt physische Quellen, eigener begrenzter Application-/Edgecount schützt Mehrfachverwendung; vorhandene Gesamtbudgets verwenden.

**`WorkspaceManager.capture(...)`, `onSourcesChanged(...)`, `isCurrent(...)`, `publishIfCurrent(...)`:** Includeabhängigkeiten, Ownergeneration und Kontextwahl in capture-/publish-Guards aufnehmen. Ein Änderungsburst darf keine veralteten Captures wieder publizieren. Caches nach Projekt und Sourceversion, nicht global nach URI allein. Close/Reopen erzeugt neue Inkarnation, auch bei identischem Text und wieder verwendeter Bufferversion.

**Neu: `SourceContextService` (oder kleiner vorhandener Hostadapter):**

```java
SourceContexts contextsFor(URI source, CancellationToken cancellation);
SelectedContext select(URI source, String contextToken,
                       SourceIncarnation incarnation, long ownerGeneration);
ResolvedQueryContext resolveForQuery(URI source, Optional<String> panelContext);
```

Keine automatische Standalone-Registrierung als Nebenwirkung einer normalen Fragmentquery. Explizite Standaloneauswahl bleibt eigener Vorgang. Auswahl kann bei Verlust der Membership verfallen. Multi-Root-Identitäten und Ausschlüsse beibehalten.

### 5.4 Semantik und Queries

**`ProjectAnalyzer.Pass.run()`:** über geordnete `RegistrationEvent`s/Anwendungen binden, nicht nur über `jobs().documentId()`. Syntaxdiagnosen physisch teilen, kontextabhängige Semantik je Anwendung auswerten. Symbol und Alias tragen `ApplicationId`. Globale Kollisionsprüfung arbeitet weiterhin auf qualifizierten Task-/Parameter-/Cleanupnamen des Projekts, nicht auf Sourcebasenames.

**`resolve(...)`, `validateReferences(...)`, `collectDefaults()`:** Scope über Besitzerkontext, nicht Quellverzeichnis ableiten. Defaults nur echte zuständige Quelle. `file`- und SQLpfade über `resourceBase`, TaskRef-Namen nur lokal in derselben Anwendung auflösen. `ReferenceIndex` enthält vollständige kontextbezogene Uses und originale Anker; Ergebnisse bei Shared-Mehrdeutigkeit nicht mischen.

**`QueryContext`:** um eindeutig gewählte Anwendung bzw. eine geordnete mehrdeutige Kontextmenge erweitern. Alte Konstruktoren dürfen nur dort ohne Kontext funktionieren, wo eine eindeutige Ableitung besteht. Ein QueryContext mit unbekanntem Contexttoken wird nicht still in Rootscope umgedeutet.

**`LanguageQueries.definitions/documentLinks/references/hover/documentSymbols`:** Include-Literale über aufgelöste Links; fachliche Referenzen über kontextbezogenen Index. Dokumentbezogene Outline auf Originalquelle begrenzen. Referenzlisten deduplizieren physisch gleiche Fundstellen, ihre Kontextabhängigkeit aber erhalten. Native Rename muss denselben Guardpfad verwenden wie grafisches Rename.

**`CompletionQueries`:** lokale/qualifizierte Symbole aus gewähltem Kontext und Pfadvorschläge aus captured Listings. Kein Filesystemzugriff und kein willkürlicher Einbinder. Unvollständige Quote ergibt höchstens einen sicheren Completionkontext; nicht synthetisch komplette Syntax vortäuschen.

**`SqlTemplateAnalyzer`:** Sourceanker bleibt Fragment, relative SQLdatei kommt aus Besitzerroot. Zwei Kontexte derselben Quelldeklaration können verschiedene SQLs/Fehler besitzen; Prüfung und Cache müssen das abbilden.

### 5.5 Graph und Formulare

**`JobGraphProjector.project(...)`:** Container nach besitzendem Entry/Scope, Nodeidentität nach Kontext/Symbol, Sourceanker nach physischer Deklaration. Geordnete echte Taskgraphdaten an vorhandene PlanSemantics geben. Includeclosure verursacht keine zusätzliche Taskkante. Symbolquellen und Kontextlabels in additive DTO-Felder bzw. vorhandene Sourceinformationen übertragen.

**`GraphFormProjector`, `TargetPreviewProjector`:** dieselbe Anwendung und dieselben Sourcevektoren verwenden. Formularwerte und `file`-Informationen nicht aus der gepinnten Entrydatei statt der tatsächlichen Quelle auslesen. Graphfähigkeiten sind nicht bloss `COMPLETE`; Ownership-/Footprint-/Editfähigkeit getrennt ausweisen.

### 5.6 Editplanning, Verifikation und Native Apply

**Neu oder eng ergänzt: `EditImpactAnalyzer`.**

```java
EditImpact assess(AnalysisSnapshot snapshot, SemanticEditIntent intent,
                  Map<URI, List<SourceReplacement>> proposedChanges);
```

Zwei Stufen: vorab target-/referenzabhängige Fähigkeiten, danach tatsächlichen vollständigen Write-Footprint vor Freigabe prüfen. Ein deklarationsbasiertes Shared-Verbot allein genügt nicht. Beim Postimagekontext alle erfassten betroffenen Besitzer erhalten. Keine Erlaubnis allein durch den vom Client behaupteten Contexttoken.

**`EditabilityAnalyzer.assess(...)`:** vorhandene semantisch gleichwirkende Intentklassifikation behalten. Vollständige Includes dürfen Referenz-/Graphcoverage erfüllen; Owner-/Footprintguard ist zusätzliche unabhängige Voraussetzung. Lokale skalare Edits in eindeutigen partiellen Quellen bleiben möglich. Bei Shared/UNKNOWN keine SourceWrites.

**`SemanticEditPlanner.plan(...)`, `planConfiguration(...)`:** passende Sourceanwendung explizit verwenden. `SetField`, Optionssetter, Add/RemoveDependency und BindResult aus allen Aufrufwegen prüfen. Keine Teilplansausgabe bei Batchfehler. AddTask/Action/Parameter verlangt ausdrückliche eindeutig besessene Zieldatei; Defaultszuständigkeit prüfen.

**`ReferenceSourceEdits.rename(...)` / `AliasUseGuard`:** Referenzen kontextbezogen sammeln, Schreibweisen mit vorhandenen `ProjectNameRules` rendern. Lokale Aliasvariablen nicht als geteilte GRETL-Namen behandeln. Quellidentische Uses deduplizieren; konkurrierende Ersatztexte ablehnen. Keine implizite Suche/Ersetzung ausserhalb bekannter gebundener Ranges.

**`EditVerifier.verify(...)` / `reanalyze(...)`:** originale Projekt-/Entry-/Kontext-/Ownershipinformationen und eingefrorene Sources/Inventories übernehmen; Postimages über alle Includeanwendungen neu binden. Prüfe erwartete bindungsgenaue Wirkung, nicht bloss Anzahl Fehler oder erhaltene Tasknamen. Das gilt auch für Fehler anderer Kontexte mit gleicher physischer Range. Kontextabhängige Diagnose-/ErrorKey-Vergleiche benötigen die Anwendung zusätzlich zu Code, URI und Range; zwei Scopes dürfen ihre Fehler nicht gegenseitig kompensieren. Neuer IO-Bedarf ist ein kontrollierter Verifikationsfehler.

**`EditPreconditions`, `PlannedEdit`, `SemanticEditPlanner.validate(...)`:** Owner-/Kontextgeneration ergänzen. Veraltete Membership darf nicht durch unveränderte Text-Hashes „gerettet“ werden. Bei Änderung des Renameziels wird ein neues konsistentes Symbolmapping erwartet; nicht alte qualifiedNames in Postconditionvergleichen festschreiben.

**Host `applyEditPlan(...)`, `edit-guard.ts`, Native Rename/Quick Fix:** alle tatsächlich betroffenen Dokumente, positive/negative Sourceabhängigkeiten und Kontextidentity prüfen. Native VersionedTextDocumentEdits verwenden. Vor Konvertierung prüfen, nach asynchroner Konvertierung erneut prüfen. Batchanwendung als ein WorkspaceEdit; Fehler ohne heimlichen Fallback auf fs.writeFile. Für Mehrdateien-Undo-/Save die tatsächlichen Zieldokumente verwenden (§4.8).

### 5.7 LSP und VS-Code-Synchronisierung

**`GretlLanguageServer` Querydispatch / `GretlTextDocumentService`:** vor fachlicher Query Besitzerkontext resolve; nicht für jede Fragmentdatei automatisch neues Singleprojekt anlegen. `didOpen` akzeptiert serverseitig legitimierte Fragmente, nicht beliebige unselektierte Pfade. Bestehende `gretl-runtime`/`gretl`/SQLausnahmen nicht versehentlich entfernen. DocumentLink, Completion, Diagnosepublikation, Rename und CodeActions verwenden dieselbe Contextselection.

**`GretlProtocol` / TS-Wirevalidierung:** neue kleine Context-/Membershiprecords typisiert und begrenzt; optionaler Contexttoken und Featurecapability. Kontext aus dem Server statt frontendseitiger Scopeheuristik. Eigentümerlisten enthalten keine Secrets und keine vollständigen beliebigen Quelltexte. LSP-/Katalog-/Jobbundleversionen nicht koppeln.

**Neu: `FragmentDocumentCoordinator` in der Extension**, beispielsweise:

```typescript
acceptMembership(update: SourceMembershipUpdate): Promise<void>;
synchronizeOpenMember(uri: vscode.Uri, generation: string): Promise<void>;
forgetMembership(uri: vscode.Uri, generation: string): void;
selectContext(document: vscode.TextDocument): Promise<void>;
dispose(): void;
```

Nur tatsächlich gemeldete Workspacequellen über `vscode.languages.setTextDocumentLanguage` in die vorhandene GRETL-Synchronisierung aufnehmen. Vorherige Sprache und eigene Übergänge merken; explizite Nutzerwahl nicht überschreiben. Wiederholte Übergänge zusammenführen, keine didOpen/didClose-Schleife. Bereits dirty Dokumente vor Anzeigen einer editierbar frischen Analysis synchronisieren. Beim Serverrestart Membership neu erwerben; keine alten Tokens/Sourceinkarnationen verwenden.

**`extension.ts` Filewatching:** z. B. Groovy-/Settings-/SQLevents innerhalb der Workspacewurzeln abonnieren, serverseitig relevante Ereignisse begrenzen. Kein rekursiver Parserlauf pro beliebiger `.groovy`-Änderung. Filecreate/-delete können Ownerinventar ändern. Watcher und Membershipsubscriptions bei Dispose/Rootwechsel sauber freigeben.

**`ModelerPanelController`:** Kontext im Panel pinnen; beim Kontextverlust stale/selection-required statt stiller Rootfallback. Nach Navigation in ein Fragment Texteditor-Kontext bewusst bestätigen. Mehrere Panels derselben physischen Sharedquelle mit verschiedenen Kontexteinstellungen dürfen sich nicht gegenseitig umschalten.

## 6. Phasen und verbindliche Abnahmen

### Allgemeiner Zyklus

Reproduzierende Probe / neuer Vertragstest → minimale Änderung → notwendige Regression → lokaler Kandidatencommit → frischer unabhängiger OpenCode-Review am vollen SHA → bei Findings gezielte Nacharbeit mit neuem SHA → erst bei `ABGENOMMEN` fortfahren. Je Phase ein abschliessender Review, nicht je Datei. Bei Nacharbeit neue Session und unveränderte Phasebase; gesamtes Phasenziel prüfen, aber nur betroffene Tests neu ausführen.

### P0 – Bestehende Grundlage und autonome Reviewkette

**Ziel:** Neue Aufgabe eindeutig starten, vorhandene Runtimefunktion nicht nochmals implementieren, kleinen belastbaren Baseline- und Reviewpfad haben.

1. Gitstatus, Worktrees, aktuelle Main-/Featurebasis, JDK25, Mavenwrapper und Node prüfen. Eigenen Worktree/Branch `codex/include-tooling`; fremde Änderungen nicht stashen/resetten/cleanen. Bei vorhandenem Featurebestand fortsetzen.
2. Rootregeln, diese drei Auftragsdateien, Include-README/Handoff und relevante Codepfade lesen. Tatsächliche Basis und Abweichung zum Recherchepin notieren. Keine vollständige historische Test-/Evidenzinventur.
3. Kleine Baseline: ein funktionierender Include-Loaderfall, die derzeit noch partielle Toolinganalyse und eine passende bestehende Edit-/LSPprobe. Baseline muss den tatsächlich vorhandenen Zustand korrekt zeigen; kein „apply fehlt“ aus dem alten Auftrag übernehmen.
4. Architekturentscheidung für neutrale Extraktion und Verantwortung der vorhandenen Klassen knapp in `WORK_QUEUE.md` festhalten. Neue Modulbezeichnung lokal prüfen. Keine vollständige neue Architekturstudie statt Codeauftrag.
5. Lokalen vorhandenen Include-Reviewpfad begrenzt suchen und prüfen; falls nicht portabel vorhanden, schlanke Featureautomation gemäss §9. Faktische OpenCode2-/Modell-/Policy-/Testfähigkeit erproben, kein blosser Modellping.
6. P0 committen und unabhängig testen/reviewen. Eine eigene kleine Gegenprobe des Reviewers belegt Isolation/realen Prüflauf. Kein Produktcode nur für einen künstlichen P0-Umfang.

**P0-Kriterien:** W01 Basis/Worktreeisolation; W02 echte kleine Baseline; W03 autonomer unabhängiger Review; W04 angemessene Testauswahl/korrekter neuer Auftragsdigest. Alle vier müssen abgenommen sein. Fehlende autonome Reviewerfähigkeit sperrt P1 (`BLOCKED_REVIEW_INFRA`).

### P1 – Gemeinsame sichere Syntax und Anwendungsmodell

**Ziel:** Fachliche Includeerkennung teilen, ohne Runtime-/Toolsicherheit zu schwächen; Source-/Anwendungskontext und Ordnungsvertrag implementieren.

Implementiere neutrale ASTinspektion, kompatible Runtimefassade, Toolingparseradapter und unveränderliche Include-/Applicationdaten. Noch keine unvollständige Freigabe struktureller Includes-Edits. Deklarative/opaque Kontrollflussklassifikation bleibt konservativ.

**Notwendige Tests:** vorhandene ApplyDirectiveReader-Regressionsfamilie; beide erlaubten Syntaxformen; unechte `apply` in Kommentar/String/fremdem Receiver; Helper-/Shadowingkontrollen; ungültiger Kontext/Literal; BOM/CRLF/Emoji-Range; sichere Marker/ASTTest/Grape-Nichtausführung im Tooling; gleiche Source in zwei Anwendungskontexten; geordnete Erweiterung an einer Apply-Site; `return`/opaque code verhindert falsches COMPLETE. Bestehende SafeParser- und normale GroovyLoaderregressionen nur, soweit durch Extraktion betroffen.

**Artefaktprobe:** neutrale Modulgrenzen/effektive Dependencies prüfen und einen kleinen bestehenden Include-SCRIPT-/JARweg nach der Extraktion nachweisen. Dies ist fällig, weil der Runtimeclasspath berührt wird. Keine gesamte Operationenmatrix.

**Review:** Runtimeparität, Classloader-/Nichtausführungsgrenzen, keine doppelte Regelimplementierung, Originalrange-/Sourceidentität. Eine eigene Parser-/Shadowing-/Scopegegenprobe.

**Gate:** Komponentenverträge für IT01–IT04, IT06, IT23 nachgewiesen; alte zulässige Runtimejobs erhalten; unabhängiges `ABGENOMMEN`. Noch keine fertige Navigation behaupten.

### P2 – Transitive Captures, Ownership und Fragment-Synchronisierung

**Ziel:** Vollständiger korrekter Quellbestand mit Buffervorrang, richtigen Ressourcenbasen und verlässlicher Invalidierung.

Erweitere Resolver, Capture/SourceView, Ownerindex, Kontextregistry und Client-Membership-/Fileevents. Frameworks/Harnesses weiterverwenden. Datenfluss kann zunächst noch konservative Semantik liefern; strukturelle Include-Edits bleiben bis P4 gesichert gesperrt.

**Notwendige Tests:** verschachtelte Includes und gleiche Basenames; Missing/Cycle/Duplicate/unsafe-path; erlaubtes `..` und Symlink-vor-`..`; gleiche Source in zwei Subprojekten mit verschiedenen Ressourcen/SQLdateien; dirty Child vor Parentcapture; neues ungespeichertes Fragment; Close→Diskfallback; fehlende Datei→Create→neu erkannte Quelle; Delete und Includezielwechsel; Änderung Sharedquelle invalidiert beide Kontexte; ungeöffneter zweiter Standardowner; neuer zweiter Owner invalidiert Exklusivitätsbeweis; Budget-/Multi-Root-Negativfall.

**Client-/Prozessproben:** bestehendes stdio-Harness für Membership, didOpen/didChange und stalen Capture nutzen. Ein schon offener dirty Groovypuffer muss nach Membershipsync korrekt im Server ankommen. Ein unbeteiligtes `build.gradle`/Groovydokument bleibt unberührt. Filewatcher-Update einer geschlossenen Fragmentdatei, Rootwechsel und Serverrestart gezielt testen. Mocktests beweisen keine echte stdio-Synchronisierung; beides passend kombinieren. Noch keine vollständige GUIabnahme.

**Review:** Sourcecapture ohne hidden IO, fehlende Ziele als Abhängigkeiten, Ownershipvollständigkeit, kein globaler Sprachübergriff, keine stale/dirty-Verluste. Eigene Gegenprobe mit Reihenfolge-/Membershipwechsel.

**Gate:** IT03–IT10 und Kontextgrundlagen von IT15/IT21/IT23 belegt; fokussierte Tests und unabhängige Abnahme. Explizit keine reine URI→einScope-Vereinfachung.

### P3 – Vollständige statische Semantik, Navigation und Modelergraph

**Ziel:** Der unterstützte statische Includejob ist lesend vollständig und fachlich konsistent.

Binde die geordneten Anwendungen in ProjectAnalyzer/ReferenceIndex/Queries/Graph/Form/Preview ein. Korrigiere Ressourcen- und SQLkontexte. Liefere explizite Kontextwahl, Include-Pfadcompletion und Originalquellnavigation. Dokumentiere Shared-Lesekontexte; nicht parallel die aufgeschobenen Shared-Writes implementieren.

**Notwendige Tests:** vollständige typisierte Mehrdateienpipeline gegen Kontrolle ohne Includes; gleiche Task-/Parameternamen in zwei Scopes; lokale Aliasvariablen in Sharedfragment dürfen sich nicht vermischen; Aliasexport zwischen Dateien bleibt verboten; Forwardref; dependsOn-Resultrechte/after-Gegenfall; Registerreihenfolge vor/zwischen/nach Includes; qualifizierte References/Defaults; richtige Resource-/SQLdiagnosen; vollständiger Graph/Preview und Originalanker; DocumentSymbols nur eigene Quelle.

**Query-/Hosttests:** echte Standard-LSP Definition/References/Hover/Completion und DocumentLink aus Fragmenten; sichere Include-Pfadcompletion; mehrdeutiges Sharedfragment ohne gewählten Kontext und mit zwei expliziten Kontexten; Graphnavigation in beide Besitzerkontexte; Änderungen in einem dirty Sharedfragment aktualisieren beide Graphen; Endpunktversion/fehlende Capability sauber behandeln. Ein dynamischer Toplevel-/Recoveryfall bleibt partiell.

**Wichtig:** Positivfall nicht durch beliebige Action-/Markerexpressions künstlich partiell machen. Typisierte Tasks mit statischen Feldern verwenden. Nichtausführung separat prüfen. Mindestens eine Runtime-vs-Tooling-Planparitätsprobe auf einem bekannten lokalen Input; kein vollständiger Interpretervergleich.

**Review:** Wahrheit der Completenessflags, keine doppelte Taskgraphsemantik, korrekte Anwendung/Resourcebasis, Querykontext und Originalanker. Eigene Gegenprobe mit Sharedscope oder nachträglich geändertem Include.

**Gate:** IT04–IT06, IT11–IT16, lesender Anteil IT22/IT23 erfüllt, P1/P2-Verträge erhalten. Strukturelle Include-Edits ohne P4-Beweis bleiben kontrolliert abgelehnt. Unabhängige Abnahme vor P4.

### P4 – Sichere Mehrdateien-Edits und gemeinsame Schlussabnahme

**Ziel:** Eindeutig besessene statische Includes können regulär bearbeitet werden; Shared-/unknown-Grenzen sind end-to-end wirksam.

1. Footprint-/Ownership-/Postimageguards einbauen und die bislang fehlende strukturelle Include-Editfähigkeit gezielt freigeben. Runtimeverträge nicht ändern. Core, Standard-LSP und Modeler teilen denselben Guardpfad.
2. Positive Mehrdateien-Task-/Parameter-Renamefälle mit Defaults, dependsOn/after und result; Kollision abweisen. Fragmentfeld-/Options-/Dependency-/Resultedits an Originaldateien. Ein Batch bleibt zusammenhängend. Bytegenaue Preservation mit CRLF/Unicode/Kommentaren, keine Flattenkopie.
3. Sharedziel ablehnen; **auch eindeutiges Target mit Referenz in Shareddatei** ablehnen; unabhängige eindeutige Datei darf editierbar bleiben. Als positive Kontrolle eine erlaubte Dependency/Resultreferenz aus einer eindeutigen Consumerdatei auf einen bekannten Shared-Produzenten prüfen, ohne dessen Quelle zu verändern. Ungeöffneter zweiter Besitzer und später hinzugefügter Besitzer sind Gegenfälle. Unvollständige Ownererfassung darf keine Exklusivität behaupten. Dasselbe für Setter/Formular-/Quick-Fix-Umwege.
4. EditVerifier über alle gecaptureten Anwendungen und eingefrorenen Listen; keine neue IO. Stale Plans nach dirty Fragmentedit, Ownerwechsel, Kontextwechsel und Close/Reopen ablehnen. Positive Edits unter aktuellen Voraussetzungen bleiben möglich.
5. Kleiner installierter VSIX-Abnahmelauf nach §7.2: tatsächliche Produktartefakte, native Aktionen, Mehrdateien-Save/Undo/Redo, Shared-Ablehnung, anschliessend gespeicherte Fixture separat in Runtime ausführen. Reviewer prüft Bilder/Originalbelege selbst und führt eigene relevante Wire-/Coregegenprobe aus; bei geänderter Host-/GUIlogik zusätzlich eigene kleine native Benutzerpfadgegenprobe.
6. Aktuelle Featureanleitung und Beispiel `examples/include-tooling/` oder enges vorhandenes Include-Beispiel aktualisieren. Nicht historische Spez-/Reviewtexte rückwirkend ändern. README/Handoff als aktuelle Benutzerinformation dürfen den neuen Stand beschreiben und den damaligen Partialumfang als historischen Ursprung markieren.
7. Finale Featureinteraktionen und direkt betroffene Regressionen am Kandidaten prüfen; aktuelle identische schwerere Artefaktbelege dürfen begründet wiederverwendet werden. Kein reflexhafter Gesamtlauf. Nötige Package-/Dependencychecks bei neuem Syntaxmodul sind kein optionaler Ersatz durch Unitgrün.
8. Finalen Code-/Doc-Kandidaten committen. **Frischer kumulativer unabhängiger Review seit Auftragsbasis**, alle IT01–IT24. Eigene neue Interaktionsgegenprobe, nicht nur Addition früherer PASS-Berichte. Kein Push/Merge/Release.

**Gate:** alle notwendigen Verträge belegt, keine offenen BLOCKER/MAJOR oder fälligen unerledigten Prüfungen. Abschlusslabel **`STATIC_INCLUDE_AWARE_TOOLING_WITH_GUARDED_SHARED_EDITS`**. Das bedeutet vollständige Unterstützung im festgelegten statischen/eindeutigen Umfang, nicht beliebige Groovy-Interpretation oder globale Sharedrefactorings.

## 7. Prüfdesign und kleine Nachweismatrix

### 7.1 Repräsentative Fixtures statt Testkreuzprodukt

| Fixturefamilie | Wesentliche Aussage |
|---|---|
| A: Entry + zwei einfache Fragmente, typisierte Gzip-Tasks | Vollständiger Graph, Cross-file-Ergebnisse/Parameter, Navigation, Rename, gespeicherter Runtimeoutput. |
| B: Zwei Subprojekte + Sharedfragment + unterschiedliche lokale Daten/SQL | Kontexte, Alias-/Scopeisolation, Ressourcenbasis, Mehrfachnodes, Shared-Schreibgrenze. |
| C: Nested/Missing/Cycle/Duplicate/unsafe-path | Diagnostik, negative Abhängigkeiten, sichere IO; wenige parameterisierte Varianten. |
| D: Reine Nichtausführungsmarker separat von A | Parser/Editor/Graph/Edits führen weder Top-Level/ASTTest noch Tasks aus. |
| E: Dirty-/Membership-/Context-/Ownerwechsel | Sourcevektor, Inkarnation, Invalidierung, stale Ablehnung; kleiner gezielter Burst statt Benchmark. |
| F: Eindeutiges Target, zu ändernder Use in Shareddatei | Vollständiger Write-Footprint statt Target-only-Guard. |
| G: Opaque/return/Recovery/Limit | Ehrlich PARTIAL, keine irrtümliche positive Editfreigabe. |

**Beispiel für A**, an vorhandene Operationsfelder und Repositoryfixtures anlehnen:

```groovy
// job.groovy
apply from: 'tasks/prepare.groovy'
apply from: 'tasks/finish.groovy'
defaults 'finish'
```

```groovy
// tasks/prepare.groovy
task('prepare', Gzip) {
    dataFile = file('data/input.txt')
    gzipFile = workPath('input.txt.gz')
    overwrite = true
}
```

```groovy
// tasks/finish.groovy
task('finish', Gzip, dependsOn: 'prepare') {
    dataFile = result('prepare').file
    gzipFile = workPath('input.txt.gz.gz')
    overwrite = true
}
```

Im echten Runtimeprozess zweimal dekomprimieren und Originalbytes vergleichen, nicht nur Dateiexistenz oder Exit0. Marker nicht in diese Completenessfixture mischen. Parameterszenario als kleine Variante mit bekanntem statischem Parametertyp; Sharedtest verwendet eigene Daten im jeweiligen Besitzerverzeichnis.

Tests erhalten eigene feste Orakel für konkrete Tasknamen, Scope, Ressource, Reihenfolge und Referenzziele. Ein Vergleich zweier Consumer desselben gemeinsamen Inspectors allein ist kein unabhängiger Semantikbeweis. Die bestehenden Runtimefixtures und ein tatsächlicher kleiner Runtimeprozess ergänzen die Erwartungen.

### 7.2 Installierter Benutzerpfad, klein aber vollständig

Ein dediziertes VS-Code-Profil, lokale synthetische Daten, tatsächlich gepacktes VSIX und passender Server aus dem Kandidaten. Bestehende `scripts/workbench.mjs`-/Evidence-/Testdriverwege prüfen und verwenden. Kein normaler Nutzerworkspace und keine alte installierte Erweiterung als Nachweis.

**Ablauf 1 – eindeutiger Job:** Entry öffnen → kompletter Graph mit Fragmenttasks → Include-/Tasknavigation → bereits vor Aufnahme dirty geöffnetes Fragment korrekt synchronisieren → Rename aus Originalfragment über mindestens zwei Dateien → exakt erwartete Replacements → natives Undo und Redo → alle betroffenen Quellen bewusst speichern → gespeicherten Stand getrennt mit Runtime prüfen. Native Texteditorsicht und Modeler müssen denselben Job zeigen.

**Ablauf 2 – Shared und Stale:** Sharedfragment in zwei Scopekontexten anzeigen → erwartete unterschiedliche Ressource/Referenz → strukturellen Write kontrolliert ablehnen → Fragment ändern und beide Kontexte aktualisieren → geplanten eindeutigen Edit durch neue Ownership oder Quellenänderung invalidieren → kein veralteter Text angewendet. Teilweise davon genügt stdio/Hostautomation; mindestens Shared-Kontextanzeige/Ablehnung und tatsächliche Cross-file-Edit-/Undo-/Savebedienung im installierten Benutzerpfad sehen.

Wenige relevante Bilder plus Quell-/Buffer-/Version-/Artefaktnachweise. Native Maus-/Tastatur-/Commandpaletteaktionen über vorhandene Automation; eine reine lesende Inspection ist kein Aktionsnachweis. Keine neue universelle GUIengine. Bei fehlender GUIausführung bleibt dieser Pflichtnachweis `NOT_EXECUTED`, nicht durch Node-Mocks ersetzt. In P0 früh dessen Durchführbarkeit klären, ohne alle GUItests vorzuziehen.

### 7.3 Alte Partial-Tests kontrolliert weiterentwickeln

`ApplyPartialCoverageTest`, `ApplyEditCompatibilityTest` und `ApplyIncludeServerIT` haben bisher gültige Übergangserwartungen. Für sichere vollständig erfasste Fixtures werden sie durch neue positive Erwartungen ergänzt bzw. ihre aktiven Tests bewusst auf den neuen Vertrag umgestellt. Dabei:

1. Originalverhalten am Basisstand nachvollziehbar lassen; historische Reports nicht ändern.
2. Alte Sicherheitsfälle mit wirklich fehlender/defekter/dynamischer/unbekannter Quelle weiterführen.
3. Separate positive Kontrolle mit gültigem Fragmentinhalt und vollständiger Ownerkenntnis verlangen.
4. Bei geändertem Testnamen/Oracle kurz angeben, welcher neue IT-Vertrag die alte „immer partiell“-Erwartung ersetzt.
5. Keine Rejection aufgrund kaputtem Katalog/Transportfehler statt dem beabsichtigten Shared-/Coveragegrund akzeptieren.

Ein Fragment mit `throw new AssertionError('never execute or resolve')` ist als Nichtausführungsprobe weiterhin nützlich, aber **nicht** als normale vollständige Positivfixture. Der neue Resolver darf den Text lesen; er darf ihn nicht ausführen. „Nicht auflösen“ wird gezielt abgelöst, „nicht ausführen“ bleibt bestehen.

### 7.4 Phasenumfang im Reviewrequest

| Phase | Abzuprüfende Kriterien im beschriebenen Teilumfang |
|---|---|
| P0 | W01–W04. |
| P1 | IT01–IT04, IT06, IT23 auf Komponenten-/Extraktionsebene. |
| P2 | IT03–IT10, IT15/IT21/IT23 soweit Capture, Owner, Context und Synchronisierung; IT24 Workflow. |
| P3 | IT04–IT06, IT11–IT16, IT23 und lesende IT22-Integrationen; Schutz bis P4. |
| P4 | Kumulativ IT01–IT24 mit allen endgültigen öffentlichen Wegen. |

Teilabnahmen benennen ihren Teilumfang. Keine vorzeitige JAR-/GUI-/Renamefreigabe allein anhand eines Records oder DTOtests. Eine kompakte Zuordnung in `WORK_QUEUE.md` genügt; kein zweites grosses Kriterienregister generieren.

## 8. Testauswahl und Wiederholung nach Korrekturen

### 8.1 Risikokarte statt pauschalem Vollbuild

| Geänderter Bereich | Fällige direkte Prüfungen | Ohne weiteren Anlass nicht fällig |
|---|---|---|
| Neutrale Extraktion / Includeinspektion | Bestehende Reader-/Helper-/Rangeregressionen, SafeParser, Runtimeadapter, Modulgrenzen, kleiner Include-SCRIPT/JAR-Smoke | Alle fachlichen Taskbibliotheken, Dienstmatrix, ganze GUI. |
| Resolver / Capture / Ownership | Includegraph-/Pfad-/SourceView-/Workspace-/Invalidierungsfälle, relevante SQLkontextprobe | Datenbankserver nur um einen SQLpfad zu binden. |
| Query / Semantik / Graph | Betroffene Query-/Alias-/Graph-/Previewtests, kleiner Runtimeplanvergleich | Alle Operationen und breite Benchmarks. |
| LSP / Membership / Context | Echter stdio-Prozess, relevante Host-/Wire-/Version-/Dirtytests | Mehrfaches komplettes Rendering oder G01–G30. |
| Edit-/Rename-/Footprintguard | Betroffene positive/negative Core-Editfamilie, Postimages, Source-/Ownerpreconditions, stdio und einschlägiger nativer Benutzerpfad | Unveränderte Loader-/Paketfälle bei jedem kleinen Guardfix. |
| Nur Dokumente / Testanweisung | Links, tatsächliche Beispielkommandos soweit geändert, Schemakonsistenz | Produktsuiten ohne Produktänderung. |

Beim breiten Eingriff in einen gemeinsam benutzten Parser oder EditVerifier die **gesamte kleine betroffene Testfamilie** ausführen, nicht nur den neuen Happy Path. Das ist weiterhin gezielt. Ein kompletter Rootlauf ist nur mit konkret dokumentiertem Risiko oder durch bereits bestehende CI-/Releasevorgabe fällig, nicht als automatischer Teil jeder Featurephase.

### 8.2 Reproduzierbare zielgerichtete Kommandos

Kommandos anhand realer POMs/Harnesses prüfen. Folgende Beispiele sind Muster, **keine Behauptung neuer bereits vorhandener Testklassen**:

```sh
./mvnw -B -pl gretl-groovy -am \
  -Dtest=ApplyDirectiveReaderTest,FragmentGraphResolverTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

# Neue Namen erst verwenden, nachdem die Klassen tatsächlich existieren:
./mvnw -B -pl gretl-tooling-core -am \
  -Dtest=IncludeResolutionTest,IncludeOwnershipTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`failIfNoSpecifiedTests=false` ist nur für Reactorvoraussetzungen ohne ausgewählte Tests zulässig. Im Zielmodul frische XMLs mit den erwarteten Klassen/Methoden, ausgeführt >0, Fehler/Errors=0 und keine unerklärten Skips nachweisen. Kein Nulltest-PASS. Bei Failsafe konkrete `-Dit.test`-Namen und tatsächliche Bindungen verwenden; ein `verify` kann zusätzlich Surefiretests starten, daher Module und beide Filter passend wählen. Konfigurationsänderungen zur allgemeinen Testunterdrückung sind verboten.

Für Extensiontests vorhandene `npm run check`, direkte `node --test <betroffene .test.mjs>` und nötige Build-/Packagewege verwenden. Tatsächliche Testdateien lesen, keine erfundenen Rootskripte. `npm run package`/Serverinventar kann nötig sein, um das installierte Produkt zu prüfen; dies ist nicht gleichbedeutend mit Wiederholung aller GUIprüfungen.

### 8.3 Artefaktidentität und Rohberichte

Pro Lauf: Kandidatenrevision, Worktree, tatsächlicher Befehl, Start/Ende, Exitcode, betroffene Testfälle bzw. Prozessassertions, Rohbeleg. Falls Testentwicklung noch dirty ist, als Entwicklung bezeichnen; finale Gates binden sauberen Kandidaten oder exakt dokumentierte unveränderte Produktbytes. Nicht auf neuen SHA umetikettieren.

Eigenes lokales Mavenrepo für gleichzeitig bauende Feature-/Reviewworktrees oder nachgewiesener isolierter Reactorpfad. Keine fremden `6.0.0-SNAPSHOT`-Artefakte als Testeingabe. Externe Dependencycaches dürfen geteilt werden. Reines `-DskipTests`-Bootstrapping ist kein Gate. Neuer neutraler Modulname muss auch in isoliertem Runtime-/Packagerstart auflösbar sein.

Vor erneutem `clean` Reports extern sichern. `tee` darf Exitcodes nicht verdecken (`pipefail` oder direkte Exitübernahme). `PASS`, `FAIL`, `NOT_EXECUTED` und begründetes `NOT_REQUIRED` getrennt führen. Infrastrukturfehler sind keine erfolgreiche fachliche Rejection.

### 8.4 Nacharbeit ohne sinnlose Wiederholungen

Vor einem Fix kurz bestimmen: Welche Assertions/Consumer kann der Diff beeinflussen? Nur diese plus den reproduzierenden Gegentest neu ausführen. Ein unchanged schwerer GUI-/JARbeleg darf wiederverwendet werden, wenn Produkt-/Artefaktbytes, Inputs und Assertions nachvollziehbar unverändert sind. Das ist **kein** neuer Testlauf am neuen SHA.

Der zweite Agent bewertet weiterhin den gesamten Phasenumfang gegen die feste Phasebase. Er prüft die Zuordnung unveränderter Belege und führt selbst die risikotragenden geänderten Prüfungen sowie seine neue Gegenprobe aus. Keine Pflicht, identische komplette Root-/GUIläufe des Implementierers nochmals zu wiederholen. Bei einem tatsächlichen Host-/GUIfix eigene passende native Gegenprobe verlangen, keine fehlende Benutzerpfadprüfung hinter Wiederverwendung verbergen.

## 9. Autonome unabhängige Prüfung

### 9.1 Installation, Modell und vorhandene Mechanik

OpenCode **2.x**, nicht eine zufällig im PATH stehende 1.x-CLI. Vorhandene GUIanmeldung/Credentials des Nutzers weiterverwenden; kein zusätzlicher Pflicht-API-Key in ENV, keine Authdateien ausgeben, keine globale Konfiguration überschreiben. Lokale CLI/Backendfähigkeit real ermitteln:

```sh
command -v opencode
opencode --version
opencode run --help
opencode auth list --format json
opencode models
```

Befehle/Flags nur verwenden, soweit die tatsächliche lokale Hilfe sie unterstützt. Der bisherige Auftrag dokumentiert eine isolierte 2.2.5-Installation und externe Reviewbelege; deren Pfade sind Hinweise, keine garantierten aktuellen Binaries. Ein gefundener bestehender schlanker Launcher darf verwendet werden, wenn seine Identitäts-/Spec-/Kriterienkopplung korrekt ist.

Die offizielle DeepSeek-Modellseite ordnete beim Abruf am 5. November 2026 `deepseek-flash` der Version **DeepSeek-V4.1-Flash-1017** zu. Vor echtem Aufruf erneut prüfen und Datum/Zuordnung sichern. Tatsächlich konfigurierten provider/model-Wert und direkten Endpoint gegen Sessionmetadaten prüfen; beweglichen Alias nicht als unveränderlichen Pin verkaufen. Kein stiller Wechsel auf anderes Modell/anderen Anbieter. Gewünschte Identität muss durch Aufruf-/Sessionmetadaten, nicht LLMselbstauskunft belegt sein.

`tools/gretl-tooling/review.mjs` ist an den alten Auftrag gebunden. **Nicht unverändert dessen 81 Kriterien und Vollgates importieren.** Bevorzugt passende vorhandene lokale Include-Reviewmechanik nach Prüfung wiederverwenden; andernfalls ein kleiner Node-/Shelladapter unter `tools/include-tooling/`, kein neues allgemeines Reviewprodukt. Alte Orchestratoren und historische Reviews unverändert lassen.

### 9.2 Ein autonomer Phasenzyklus

1. Codex implementiert/testet die Phase und schreibt einen stabilen Request mit Phasebase, Scope, Kriterien, echten Tests und Grenzen; noch ohne eigenen künftigen Kandidaten-SHA.
2. Code, Tests und Request lokal committen. **Erst danach** vollständigen SHA ermitteln; externen Handoff erstellen. Keine Selbstreferenz-SHA in den Commit schreiben.
3. Frischen detached Reviewworktree auf exakt diesem SHA erstellen. Rohlogs, Request und Ergebnis in externem phase-/SHA-spezifischem Verzeichnis ausserhalb von `target/` und fremden Worktrees ablegen.
4. Neue OpenCode-Session ohne `--continue` und ohne importierte frühere Sessionhistorie starten. Richtige Location und `PWD` explizit setzen; ein frischer Chat bei falschem Worktree ist nicht unabhängig korrekt.
5. Reviewer liest Plan, Reviewprompt, Phasenrequest und tatsächlichen Diff; führt selbst Tests sowie neue Gegenprobe aus. Codex nimmt seine Rolle nicht ersatzweise wahr.
6. Prozess und Stream vollständig beenden lassen; nur eigene Prozesse verwalten. Urteil, Originalbericht, Sessionidentität und Testbelege prüfen. Kein PASS, solange Stream/Prozess/erforderliche Kinder noch laufen oder das Ergebnis fehlt.
7. Bei `NACHARBEIT` konkrete Findings beheben, gezielte Regression, neuer Kandidat und neue unabhängige Session. Bei gültigem `ABGENOMMEN` selbständig nächste Phase beginnen. Keine regulären manuellen Freigabeklicks oder Prompttransporte durch den Nutzer.

Ein vorbereiteter Test oder ein Modellping erfüllt P0 nicht. P0 muss die tatsächliche Fähigkeit des zweiten Agenten zum Lesen, erlaubten Testlauf, eigenem Gegenfall und Ergebnisexport zeigen.

### 9.3 Aufruf, Policy und kleine Automationsgrenzen

Nach Prüfung der lokalen CLI kann der folgende Aufruf als Vorlage dienen. Alle Variablen stammen aus der tatsächlichen Umgebung; der Handoff nennt den fertigen SHA und den Outputort:

```bash
(
  cd "$REVIEW_WORKTREE"
  export PWD="$REVIEW_WORKTREE"
  "$OPENCODE_BIN" run --standalone \
    --model "$REVIEW_MODEL" \
    --agent "$REVIEW_AGENT" \
    --format json \
    --file "$HANDOFF" \
    --file docs/implementation/include-tooling/OPENCODE_REVIEW_PROMPT.md \
    "Pruefe die bezeichnete Phase am exakten Commit gegen den Include-Tooling-Auftrag. Lies Code und Ziele, fuehre die notwendigen Tests und eine eigene neue Gegenprobe aus. Liefere Originalbericht und strukturiertes Urteil; keine Produktfixes."
) >"$EVIDENCE/opencode.jsonl" 2>"$EVIDENCE/opencode.stderr.log"
```

`--standalone`/`--agent` müssen vorhanden und korrekt eingerichtet sein. Vorhandene Anmeldung auch im privaten Prozess wirklich prüfen. Es ist kein Auftrag, einen neuen Backendserverdienst dauerhaft einzurichten. Eine vorhandene gleichwertige V2-Backendautomation ist zulässig, aber nicht mit geratenen V1-Endpunkten.

Effektive V2-Policy lokal/agentbezogen: Reads im Reviewcheckout, notwendige Dependency-/Buildcachezugriffe, konkrete Test-/Buildkommandos, eigene externe Probe-/Reportdateien ohne Rückfragen. Verboten: Produktfixes, Test-/Expected-Lockerungen, Zugriff auf andere Arbeitsräume/Secrets, Credentialexport, Push/Merge/Release und produktive Jobs. OpenCode-V2 verwendet `permissions` mit `action/resource/effect`, u. a. `shell`, nicht geratenes V1-`bash`. Keine pauschale `--auto`-Freigabe als Sicherheitsersatz; nur nach wirksamer expliziter deny-/allow-Policy, falls überhaupt nötig.

Shellrechte können Dateiverbote umgehen. Deshalb getrackte Produkt-/Testbytes vor/nach Review prüfen; eigenes Testmaterial in separater Probekopie desselben SHA oder externem Probeverzeichnis erzeugen. In einer Probekopie sind zusätzliche Reviewerfixtures erlaubt, Originalprodukt-/Originaltestbytes bleiben identisch. Keine getrackten Fixes im Reviewcheckout.

Timeouts/Abbrüche begrenzt und eindeutig ausweisen, keine Providerretry-Endlosschleife. Fehlende unabhängige Modell-/Ausführungsfähigkeit ergibt `BLOCKED_REVIEW_INFRA`; Kandidat und Ursache erhalten, nicht in nächste Phase übergehen. Ein fehlender GUI-Nachweis ist `BLOCKED_TEST`/`NOT_EXECUTED`, kein fingiertes PASS und keine Anlass zur Nutzerübernahme als normalem Ablauf.

### 9.4 Handoff und maschinenprüfbares Ergebnis

Ein kleines JSON-/Markdownformat genügt. Identitätsfelder im Handoff: `assignment_id=include-tooling`, `spec_version=1.0`, Anforderungsdigest, Phase, Auftragsbasis, feste Phasebase, voller Kandidat, tatsächlicher Worktree, erlaubte Test-/Evidenzorte und phasenbezogene Kriterien. Requirement-Digest deterministisch über eine nach rootrelativem Pfad sortierte Liste aus Pfad und SHA256 der drei aktiven Auftragsdateien bilden; diese Liste sichern. Nicht das gesamte historische Repository als neues Kriterieninventar hashen.

Beispiel des **Formats**, nicht eines vorgefüllten Urteils:

```json
{
  "assignment_id": "include-tooling",
  "spec_version": "1.0",
  "requirements_digest": "ACTUAL_DIGEST",
  "phase": "P3",
  "assignment_base_sha": "FULL_BASE_SHA",
  "phase_base_sha": "FULL_PHASE_BASE_SHA",
  "head_sha": "FULL_CANDIDATE_SHA",
  "reviewed_sha": "FULL_CANDIDATE_SHA",
  "review_worktree": "ACTUAL_PATH",
  "session_id": "ACTUAL_SESSION_ID",
  "opencode_version": "ACTUAL_2_X_VERSION",
  "provider_model": "ACTUAL_PROVIDER_MODEL",
  "model_mapping_evidence": "ACTUAL_EVIDENCE_PATH",
  "verdict": "ABGENOMMEN",
  "criteria": [{"id": "IT11", "status": "fulfilled", "evidence": ["ACTUAL_PATH"]}],
  "executed_checks": [{"command": "ACTUAL_COMMAND", "exit_code": 0, "assertion": "ACTUAL_ASSERTION", "evidence": ["ACTUAL_PATH"]}],
  "reused_evidence": [],
  "counterprobes": [{"authored_by_reviewer": true, "executed": true, "hypothesis": "ACTUAL_HYPOTHESIS", "evidence": ["ACTUAL_PATH"]}],
  "findings": [],
  "required_not_executed": []
}
```

Die tatsächliche Kriterienliste ist die vollständige phasenbezogene Sollmenge aus §7.4, nicht die eine illustrative Zeile. Validator muss falschen Auftrag/Digest/SHA, fehlende/duplizierte/fremde Kriterien, alte/leere Reports und fehlenden echten Reviewerabschluss ablehnen. Für einen erwarteten Negativfall den tatsächlich negativen inneren Prozessstatus und die erfolgreiche äussere Assertion getrennt erfassen; einen beliebigen Exitfehler nicht akzeptieren.

Nur `ABGENOMMEN` ohne offene BLOCKER/MAJOR, ohne fehlende Pflichtnachweise und mit echter eigener Gegenprobe gibt frei. `NACHARBEIT` und `NICHT_PRUEFBAR` geben nicht frei. Transportlabels PASS/FAIL/BLOCKED dürfen eindeutig abgebildet werden, Originalurteil behalten. Prozess-Exit0 oder das Wort „PASS“ im Text genügt nicht.

Codex prüft Modell/Session/SHA zusätzlich gegen wirkliche Aufruf-/Sessionmetadaten und Rohbelege. Keine künstliche Sicherheit durch vom Modell selbst befüllte Identitätsfelder. Ergebnisse vor einem späteren `clean` sichern, Secrets nicht archivieren.

## 10. Artefakte, Abschluss und Zustände

Eine kurze operative `WORK_QUEUE.md` mit aktuellem Stand ganz oben; keine chronologisch widersprüchlichen „noch nicht implementiert“-Aussagen ohne historischen Abschnitt. Neue kompakte Requests je Phase unter `review/`; originale Reports ausserhalb des Featureworktrees bzw. als kontrollierte Buildartefakte. Existierende grosse historische Evidencearchive nicht kopieren oder bereinigen.

Zustände: `PLANNED → IMPLEMENTED → TESTED → REVIEW_PENDING → REVIEWED_ACCEPTED`; alternativ `BLOCKED_TEST` oder `BLOCKED_REVIEW_INFRA`. Eine Status-/Archivierungsänderung kann den vorher geprüften Produkt-SHA nennen; sie wird nicht nachträglich als derselbe Reviewkandidat ausgegeben. Änderungen an produktivem Verhalten nach Abnahme benötigen neue passende Tests und Review.

Definition of Done: IT01–IT24 im bezeichneten Umfang erfüllt; jede Phase unabhängig akzeptiert; gemeinsamer finaler Code-/Doc-SHA kumulativ geprüft; funktionierende lokale Beispiele und native Benutzerpfade; keine Runtime-/Katalog-/Altformatregression; echte dynamische Grenzen und Shared-Schreibsperre dokumentiert; kein ungewollter Code-/Quelldatenverlust; keine produktiven Jobs oder Publikation.

Schlussübergabe nennt Branch, tatsächliche Basis, geprüften Produkt-SHA, gegebenenfalls separaten Statuscommit, Test-/Reviewbelege, Beispielaufrufe und verbleibende bewusste Grenzen. Die Formulierung „vollständig“ bezieht sich auf den vertraglich definierten statischen/eindeutigen Funktionsumfang. Keine Garantie für beliebigen Groovycode, ungeprüfte Plattformen oder externe unbekannte Einbinder.

## 11. Quellen, Herkunft und Aussagegrenze

Ausgangsquellen und konkrete Codebefunde stehen in [SOURCE_BASELINE.md](SOURCE_BASELINE.md), einschliesslich gepinntem Mainstand und den vorhandenen Ansprechpartnerklassen. Neue Architekturentscheidungen, IT-Kriterien und Phasen sind **dieser neue Auftrag**, keine Behauptung bereits vorhandener Implementierung.

Verfahrensvorlagen: die in der Unterhaltung bereitgestellten `CODEX_STARTPROMPT.md` und `OPENCODE_REVIEW_PROMPT.md` aus `script-includes` Fassung 1.3. Übernommen: Commit vor Review, frischer Worktree/Modellkontext, autonome Übergabe, Code **und** Zielerreichung, eigene Gegenprobe, schlanke notwendige Tests. Nicht übernommen: alter Aufgabenname, alte Kriterienmenge und die dauerhafte Partialgrenze als Zielzustand.

Externe technische Referenzen (Abruf 5. November 2026; installierte Versionen beim Start prüfen):

- OpenCode 2 CLI: https://opencode.ai/v2/docs/cli/commands/
- OpenCode 2 Permissions: https://opencode.ai/v2/docs/permissions/
- DeepSeek-Modellzuordnung: https://api-docs.deepseek.com/quick_start/pricing/
- VS-Code-LSP-Integration: https://code.visualstudio.com/api/language-extensions/language-server-extension-guide
- VS-Code-API, insbesondere Languagewechsel/Filewatching/WorkspaceEdit: https://code.visualstudio.com/api/references/vscode-api
- LSP-Strukturen für Dokumentversionen/WorkspaceEdits: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/

Für die Erstellung dieses Dokuments wurden Quellen und Vorlagen gelesen und die Dokumentkonsistenz geprüft. Keine neue GRETL-Implementierung, kein Produktbuild, keine unabhängige OpenCode-Abnahme und kein nativer GUIlauf wurden hier ausgeführt.
