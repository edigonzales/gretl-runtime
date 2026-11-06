# Statisches Tooling

Der Quelltext ist die ausführbare Definition. Editor, Graph und Formular sind
Sichten auf denselben statischen Snapshot. Die [VS-Code-Anleitung](../../vscode/gretl-vscode/README.md)
beschreibt die Benutzeraktionen; der [Include-Guide](../guides/script-includes.md)
erklärt den Laufzeitvertrag.

## Abhängigkeiten und Parsing

`gretl-language-contract` enthält die JDK-basierten Namens-, DSL- und
Planungsregeln. `gretl-script-syntax` teilt die Erkennung von Include-Direktiven
auf dem konvertierten Groovy-AST und den Originaltokens. Die Runtime behält
`ApplyDirectiveReader` als Fassade. Im Tooling verwendet `GretlDocumentParser`
eine isolierte Groovy-Syntaxanalyse und begrenzte Recovery. Der LSP lädt keine
Jobdefinition, ruft weder Check noch Plan der Runtime auf, löst keine Secrets
auf und führt keine Scriptbodies oder Annotationstransformationen aus.

Der [Modulgraph](module-boundaries.md) trennt Core und Server vom Runtimepfad.
`gretl-tooling-catalog` exportiert Runtime-Metadaten beim Build. Der Server
bekommt nur den Katalog als Datenartefakt mit Schema, Identität und Prüfsummen;
keine Taskprovider oder Runtimeabhängigkeiten. Der VSIX enthält Server und
Katalog und benötigt zum Start eine Java-25-JVM.

## Quellen, Anwendungen und vollständige Analyse

`ProjectResolver` und `ToolingIncludeResolver` erfassen unterstützte
Workspace-Einstiege, registrierte Einzeljobs und statische Includes. Offene
Buffer haben Vorrang vor Disk. Begrenzte Verzeichnis- und Entry-Inventare
sowie negative Reads gehören zum Capture. Ein neuer, noch nicht geöffneter
Besitzer kann dadurch einen alten Capture ungültig machen.

Eine physische URI hat einen Parsebaum, kann aber in mehreren Anwendungen
vorkommen. Die Anwendung verbindet Projekt, Entry, Scope, Quelldatei,
Ressourcenbasis und Einbindekette. Includes lösen relativ zur enthaltenden
Quelle auf; SQL und `file` relativ zur Ressourcenbasis der Anwendung.
`ProjectAnalyzer` registriert Deklarationen in Include- und Quellreihenfolge.
TaskRef-Aliase bleiben lokal zur Scriptanwendung. Die gemeinsamen
Planungsregeln entscheiden Zielauswahl und Kanten; Includes fügen keine
Ablaufkanten hinzu.

Ein vollständig erfasster, statisch unterstützter Includejob kann einen
vollständigen Graphen erhalten. Unbekannter Kontrollfluss, Return/Throw,
Recovery, ungültige Quellen und Budgetgrenzen begrenzen diese Aussage.
Unvollständige Ownership oder Analyse wird als solche ausgewiesen. Ein
vollständiger Graph ist keine Schreibfreigabe.

## Kontext, Prüfung und native Anwendung

Bei mehreren Anwendungen derselben Datei wählt der Benutzer einen
Quellkontext. Texteditor und gepinnte Modelerpanels halten getrennte Auswahlen.
Der Kontext ist an Server, Anwendung, Besitzergeneration und
Dokumentinkarnation gebunden. Schon dirty geöffnete Fragmentbuffer werden
vor der Editfreigabe synchronisiert, ohne die Datei zu speichern.

`SemanticEditPlanner` plant getypte Änderungen. `EditImpactAnalyzer` prüft
alle Zielquellen des tatsächlichen Write-Footprints. Shared- oder unbekannte
Quellen bleiben auch bei Rename, Quick Fix, Formularbatches und generischen
Settern schreibgeschützt. Eine eindeutige Quelle kann einen Shared-Produzenten
referenzieren, solange der Edit dessen Datei nicht schreibt. Die
Refactoringdomäne umfasst erfasste Workspace-Einstiege und registrierte Jobs;
unbekannte externe Verbraucher sind nicht erfasst.

`EditVerifier` analysiert virtuelle Postimages aus eingefrorenen Quellen und
Inventaren im ursprünglichen Einbinderkontext. Bindungs- und Fehlervergleiche
unterscheiden Anwendungen. Änderungen verwenden originale UTF-16-Bereiche und
erhalten unbetroffene Kommentare, Zeilenenden und Unicode. Snapshots,
Quellhashes, Bufferinkarnationen sowie Owner- und Kontextgenerationen werden
vor der Anwendung geprüft. Der Host öffnet erforderliche Zielpuffer, plant
danach neu und wendet einen nativen WorkspaceEdit an. Speichern und
Text-Undo/Redo bedienen die tatsächlichen Zieldokumente; Layout bleibt separat.
Das garantiert keine atomare Dateisystemtransaktion gegen fremde Schreiber.

## Protokoll und Betriebsgrenzen

Der [LSP über stdio](../../gretl-lsp/README.md) verwendet das Basisprotokoll und
Katalogschema 1 sowie Graphschema 2. Include-Tooling und Taskfinalizer haben
jeweils Fähigkeitsversion 1. Das ausgehandelte Include-Tooling liefert
`sourceContexts`, `selectSourceContext` und bestätigte Source-Membership.
Ein ausstehender Abgleich liefert `INCLUDE_MEMBERSHIP_PENDING`, sodass der
Host offene Buffer abgleichen kann, bevor eine Mutation angeboten wird.

Nachrichten-, Capture-, Parser-, Snapshot- und Watcherbudgets begrenzen
die Arbeit. Cancellation reicht bis in Analyse und Editprüfung. Für
Paket- und Prozessprüfung gelten die [Buildanleitung](../development/build.md)
und die [Teststrategie](../development/testing.md).
