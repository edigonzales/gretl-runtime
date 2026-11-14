# GRETL Runtime language server

Java 25 ist erforderlich. Der Server analysiert Quellen statisch; er fuehrt
keine Jobs, Provider, Builder oder Workspace-Plugins aus.

Vom Repository-Root bauen:

```sh
./gradlew :gretl-lsp:check
```

Das eigenstaendige Artefakt ist
`gretl-lsp/build/libs/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar`.
Es enthaelt den Java-Core, LSP4J und den validierten Katalog als Daten, keine
ausfuehrenden Task-/Runnerbibliotheken. Kopieren dieses JARs genuegt zum Start;
ein anderer GRETL-Checkout oder ein Katalogdownload ist nicht erforderlich.

Die [Distributionsprüfung](../docs/development/build.md)
startet das tatsächlich ausgelieferte JAR. Der optionale Linux-Container
bekommt das JAR und kontrollierte Quellen als schreibgeschützte Mounts;
sein Netzwerk ist deaktiviert. Der Beleg gilt für die ausgeführte Plattform.

```sh
java -jar gretl-lsp/build/libs/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar --stdio
```

Dieser Befehl wartet auf einen LSP-Client. stdout enthaelt ausschliesslich
JSON-RPC mit Content-Length-Framing; stderr ist fuer Start-/Transportfehler.
`shutdown` gefolgt von `exit` beendet den Prozess auch bei offenem stdin.

Der Client initialisiert lokale `workspaceFolders` oder `rootUri`, UTF-16 und
`initializationOptions: {"trusted": true, "language": "de"}`. Ohne ausdrueckliches
Trust bleibt Workspaceanalyse gesperrt. Nur `trusted` und `language` (`de`/`en`)
sind erlaubte Sprachkonfiguration; ungueltige Updates lassen den letzten
gueltigen Stand erhalten. Remote-Netzwerk-URIs werden nicht heruntergeladen.
Ungespeicherte explizit gewaehlte GRETL-Buffer brauchen eine eindeutige Root.

Standardfeatures: Incremental/Full-Sync, Completion, Hover, Signaturen,
Definitionen, Referenzen, Outline, Dateilinks, Folding und sichere Rename-/
Quickfix-Edits. Fuer Edits ist `workspace.workspaceEdit.documentChanges` noetig;
Quickfixes setzen CodeAction-Literals voraus. Clientfaehigkeiten fuer Markdown,
hierarchische Symbole und Folding werden beruecksichtigt. Kein Formatter,
Runtime-/Debugstart oder allgemeine Groovy-Typinferenz.

Customrequests: `gretl/catalog`, `gretl/graph`, `gretl/planEdit`,
`gretl/validateEdit`, `gretl/navigation`; Notification `gretl/analysisReady`.
Der [Schema-Vertrag](src/main/resources/gretl-protocol-v1.schema.json) beschreibt
die Requestparameter. Version ist 1. Snapshotantworten enthalten `context`
(Server-/Projekt-/Snapshot-ID, Generation als String, Source-Stamps und
Completeness) sowie ihren typisierten Payload. Fachliche Enums sind Strings;
Standard-LSP-Enums behalten ihre numerischen Protokollwerte. Integer-/Decimal-
Editwerte werden verlustfrei als getypte Scalarstrings uebertragen.

Der Graphpayload ergaenzt die neutralen Nodes/Kanten um eine optionale
`forms`-Projektion desselben Core-Snapshots: gebundene Literal-/Listen-/Mapwerte,
Parameter-/Resultreferenzen, erlaubte Einfuegepositionen und alle Defaultziele.
Opake oder mehrfach belegte Felder tragen einen Grund statt erzeugtem Quelltext.
Der Client sendet nur getypte Coreintents. Eine Bestaetigung plant, prueft und
wendet einen zusammengehoerigen nativen WorkspaceEdit an; sie ersetzt den Graph
erst nach dem normalen Textsync durch eine neue Serverprojektion. Ansichts-
positionen, Filter und deren eigener Undo-Verlauf gehoeren nicht zum Groovytext.

Graphrequests nennen ein bekanntes oder explizit geoeffnetes Dokument und
optional bis zu 32 zusaetzliche lokale `requestedSources` fuer neue SQLpfade.
Ein Editrequest bezieht sich zwingend auf `expectedSnapshotId`; der Modeler
fordert `requireOpenDocuments: true`. Bei geschlossenen Zielen antwortet der
Core mit NEEDS_OPEN_DOCUMENTS. Nach Oeffnen/Synchronisieren neu erfassen und
planen. Ein Plan ist hoechstens 60 Sekunden gespeichert (maximal 32 Plaene).

Edits sind versionierte `documentChanges`; geschlossene Dateien haben explizit
`version: null`. Diskhashes und physische Readonly-/Symlinkkonflikte werden vor
Rueckgabe erneut geprueft. Der Server schreibt keine Jobdateien. Gegen beliebige
externe Schreiber gibt es keine allgemeine Dateisystemtransaktion; der Client
muss bekannte Buffer-/Hashkonflikte vor nativer Anwendung ablehnen.

Grenzen: 2 MiB pro Eingabenachricht, 8 KiB Header, 64 geordnete Controlauftraege,
2 Analyseworker mit 16 wartenden Anfragen, 8 Snapshots, 256 Watchhandles und
4096 Watchdateien. Hinzu kommen die Core-Parser-/Capturebudgets. Bis zu 32
sichere Quickfix-Vorschlaege je Anfrage; Cancellation wird bis in die Editpruefung
weitergereicht. Native Watcher und Client-Watchmeldungen invalidieren dieselben
Captures. [Tooling-Architektur](../docs/architecture/tooling.md) und
[Buildanleitung](../docs/development/build.md) beschreiben Kontext und Paketprüfung.
