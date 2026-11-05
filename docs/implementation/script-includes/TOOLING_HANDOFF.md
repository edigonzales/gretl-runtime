# Include-/Tooling-Handoff

Der ursprüngliche Runtimeauftrag lieferte eine sichere partielle IDEsicht. Diese
historischen Abnahmen bleiben unverändert. Der anschliessende Include-Tooling-
Ausbau ersetzt für vollständig erfasste statische Includes die frühere immer-
PARTIAL-Grenze; aktuelle Arbeit und Nachweise stehen im
[Include-Tooling-Arbeitsstand](../include-tooling/WORK_QUEUE.md).

## Gemeinsame Syntax und getrennte Identitäten

`gretl-script-syntax` inspiziert nur den bereits konvertierten AST und den originalen
UTF-16-Tokenledger. Runtime-`ApplyDirectiveReader` bleibt kompatible Fassade;
Tooling behält Safe-Classloader, Transform-, Recovery- und Budgethülle. Kein
Runtime-/Runner-/Taskproviderimport in Core/LSP, keine Jobausführung im Tooling.

Eine physische URI besitzt einen Parsebaum, aber mehrere mögliche Anwendungen:
Projekt, tatsächlicher Entry, Scope, physische Quelle, Ressourcenbasis und
Einbindekette. Fragmente werden keine Pseudoprojekte. TaskRef-Aliase bleiben
script-/anwendungslokal. RegistrationEvents erweitern bestätigte Includes an der
Apply-Site depth-first; bestehende PlanSemantics entscheidet Graph/Zielclosure.
Unbekannter Kontrollfluss und Return/Throw/Recovery beweisen keine Vollständigkeit.

## Quellen und Kontexte

Includes lösen relativ zur enthaltenden Quelle auf; file/ctx.file und SQL bleiben
relativ zum Besitzerroot. Buffer haben Vorrang vor Disk. Negative Reads, sichere
Traversalprüfungen und begrenzte Verzeichnis-/Entryinventare gehören zum Capture.
Ein neuer ungeöffneter Besitzer wird durch Inventar-/Currentnessprüfung erkannt.
Refactoringdomäne sind unterstützte Workspace-Einstiege plus ausdrücklich
registrierte Jobs, keine behauptete Kenntnis externer Konsumenten.

LSP-Protokollversion 1 bleibt; experimentell ausgehandeltes Include-Tooling 1
liefert sourceContexts/selectSourceContext und bestätigte Source-Membership.
Ein schon dirty geöffnetes gewöhnliches Groovyfragment wird vor Editfreigabe
synchronisiert, ohne automatische Speicherung oder globale Groovyassoziation.
Auswahl ist an Anwendung, Server, Ownergeneration und Dokumentinkarnation gebunden.
Texteditor und gepinnte Modelerpanels halten nachvollziehbare getrennte Auswahlen.
Originalquellen, Contextlabels, Graph, Formular und Preview verwenden dieselbe Analyse.

## Schreibgrenze und native Anwendung

Vollständige Coverage und Editfähigkeit sind getrennt. EditImpactAnalyzer prüft
zunächst die benötigten Quellen und abschliessend den gesamten tatsächlich
vorgeschlagenen Write-Footprint. Shared/UNKNOWN schreibt nicht automatisch, auch
nicht über native Rename/Quick Fix, generische Setter oder Formularbatches.
Eindeutige unabhängige Quellen bleiben editierbar, ebenso ein eindeutiger Consumer,
der einen bekannten Shared-Produzenten nur liest. Rejections nennen Strukturcode,
betroffene Originalquellen und bekannte Anwendungen.

EditVerifier bindet virtuelle Postimages aus eingefrorenen Quellen/Inventaren im
ursprünglichen Einbinderkontext neu, ohne Host-IO. Bindungs- und ErrorKeyvergleiche
unterscheiden Anwendungen; ein gleichartiger Fehler in einem anderen Scope kann
keinen neuen Fehler kompensieren. Originalranges, Kommentare, EOL und Unicode
bleiben erhalten. Kein Flattening, keine Volltext-Neuschreibung.

Sourcevektoren, Inkarnationen sowie Owner-/Kontextgenerationen gehören zu den
Vorbedingungen. Native Mehrdateienziele werden vor Neuplanung geöffnet und
synchronisiert. Der Host prüft vor und nach asynchroner Konvertierung erneut und
wendet genau einen WorkspaceEdit an. Save/Undo/Redo des Modelers bedienen dessen
tatsächliche Zieldokumente; keine eigene Rückpatchengine und keine behauptete
atomare Filesystemtransaktion. Das gespeicherte Beispiel wird separat in der
Runtime ausgeführt und zweimal dekomprimiert verglichen.

[Featureanleitung](README.md), [typisiertes Beispiel](../../../examples/include-tooling/README.md)
und [Extensionanleitung](../../../vscode/gretl-vscode/README.md) beschreiben den
Benutzerweg. Historische Runtimeberichte und Spezifikationen sind keine neuen
Implementierungsaufträge und werden nicht rückwirkend umgeschrieben.
