# GRETL Tooling – Architektur und Implementierungsverträge

**Version 1.0 · 2. November 2026 · verbindlicher Sollentwurf**  
**Zielrepository:** `edigonzales/gretl-runtime`  
**Reihenfolge:** Sprachkern → Language Server → VS-Code-Editor → grafischer Modeler.

Diese Datei beschreibt zu implementierende Klassen, Methoden und Invarianten. Namen mit „neu“ sind **keine Behauptung bereits vorhandener APIs**. Gleichwertige vorhandene Implementierungen dürfen nach dokumentiertem Mapping weiterverwendet werden. Keine leeren Klassen als Erfüllungsnachweis anlegen. Kleine eng zusammengehörige Werttypen dürfen verschachtelte Records/sealed interfaces sein; die Verantwortungsgrenzen, nicht eine künstliche Klassenzahl, sind verbindlich.

Verfahren und Phasengates: [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Konkrete Tests: [TEST_MATRIX.md](TEST_MATRIX.md). Geprüfte Ausgangsquellen: [SOURCE_AUDIT.md](SOURCE_AUDIT.md).

## 1. Produktentscheidungen

### 1.1 Ein Repository, eine aktuelle GRETL-Semantik

Alle neuen Java-Module, der VS-Code-Client und der Modeler liegen im bestehenden Maven-Repository. Der LSP wird **kein** Teilprojekt von `gretl-next`, kein Gradle-Plugin und keine separate GRETL-Ausführungsengine. Der normale Script-, JAR- und IMAGE-Betrieb bleibt unverändert.

`gretl-next` ist ausschliesslich eine einmalig auszuwertende Quellreferenz. Kontrollierte Quellübernahme ist erlaubt, eine aktive Abhängigkeit nicht: weder Maven/Gradle, npm, Tests, type-only Imports, Git-/file-/Siblinglinks, Submodule noch Laufzeitdownloads dürfen eine andere GRETL-Variante voraussetzen. Provenienztexte und Lizenzhinweise sind ausdrücklich keine verbotene Abhängigkeit.

Die Ausführungssemantik bleibt beim bestehenden Runner. Gemeinsame **reine** Namens- und Graphregeln werden ohne Closure-/Handlerbezug herausgelöst und sowohl vom Runner als auch vom Sprachkern verwendet. Nicht die gesamte Runtime in den LSP ziehen, um zwei kleine Namensfunktionen wiederzuverwenden.

### 1.2 Quelltext ist die einzige ausführbare Wahrheit

Die ausführbare Jobdefinition bleibt `job.groovy` beziehungsweise eine ausdrücklich als GRETL-Job geöffnete `.groovy`-Datei. `settings.gretl` bleibt die bestehende deklarative Projektdefinition. Kein zusätzliches fachliches YAML/JSON-Jobformat, kein Camel, kein BPMN und kein verborgenes Generator-Zwischenprodukt.

Der Modeler ist ein alternativer Editor **derselben TextDocuments**. Ein Benutzer kann einen Job grafisch beginnen und später im Text weiterbearbeiten. Änderungen im Text erscheinen wieder im Diagramm. Komplexer Groovy-Code bleibt erhalten und wird bei unzureichendem Verständnis als Codebereich dargestellt, nicht „normalisiert“ oder verworfen.

Ein Layoutwechsel verändert keine Fachsemantik. Eine neue `dependsOn`-Verbindung verändert dagegen bewusst den Quelltext. Diese beiden Arten von Änderung besitzen getrennte Undo-Verträge.

### 1.3 Analyse darf nicht ausführen

Kein `GroovyShell.evaluate`, kein `Script.run`, kein `GroovyJobLoader.load`, kein `gretl check/plan/run`, kein `ctx.run`, kein Handler-/Builderaufruf und keine Plugininitialisierung beim Öffnen, Tippen, Navigieren, Anzeigen oder grafischen Editieren.

Auch `gretl plan` ist **kein** sicherer Live-Parser: Das Definitionsladen kann Groovy-Toplevel-Code ausführen. Der Editor benutzt ausschliesslich den statischen Sprachkern. Fachliche Ausführung von bewusst erzeugten Testjobs ist nur in getrennten Integrationstests mit lokalen Fixtures zulässig.

Ein späterer Run-/Debug-Button ist nicht Bestandteil dieses Auftrags. Es werden keine Laufzeitstatus simuliert und keine leeren Debug-Schnittstellen angelegt.

### 1.4 Festgelegte UI-Basis

Modeler: **React + TypeScript + `@xyflow/react` (React Flow) + `elkjs`** in einer VS-Code-Webview. Nur frei nutzbare tatsächlich lizenzierte Bestandteile; keine Pro-Beispiele oder Cloudservices als versteckte Voraussetzung. Versionen in P0 ermitteln und pinnen. Kein Kaoto-Fork, kein GLSP-Server, kein eigener HTTP-/WebSocketserver und keine zweite Diagrammengine.

React Flow ist ausschliesslich Interaktions-/Darstellungsschicht. ELK berechnet Layout im Worker. Weder Bibliothek noch TypeScript entscheiden über GRETL-Namensauflösung, Ergebniszugriffe, Abhängigkeitserlaubnis oder Codeänderungen.

## 2. Repository- und Abhängigkeitsarchitektur

```text
gretl-runtime/
  gretl-api/                     # vorhanden, Operations-SPI bleibt kompatibel
  gretl-runner/                  # vorhanden, verwendet reine Graphregeln
  gretl-groovy/                  # vorhanden, verwendet reine DSL-/Namensregeln
  ... aktuelle Taskmodule ...

  gretl-language-contract/       # NEU: reine Namen, DSL-Vokabular, Graphsemantik
  gretl-tooling-core/            # NEU: statische Sprache, Katalog, Workspace, Queries, Edits
  gretl-tooling-catalog/         # NEU: Build-Exporter aus aktueller Operations-SPI
  gretl-lsp/                     # NEU: LSP4J, stdio, Host-IO und Protokolladapter
  gretl-tooling-tests/           # NEU: Runtime-/Editor-Paritäts- und Distributionstests

  frontend/gretl-modeler/        # NEU: React-Flow-Ansicht; keine VS-Code-Imports
  vscode/gretl-vscode/           # NEU: dünner VS-Code-Host, eigener Extension-Identifier
  tools/gretl-tooling/           # NEU: kleine Node-Build-/Test-/Reviewhelfer
  docs/implementation/gretl-tooling/
```

Neue Java-Pakete liegen unter `ch.so.agi.gretl.language` beziehungsweise `ch.so.agi.gretl.tooling`. Protokolladapter dürfen unter `ch.so.agi.gretl.lsp` liegen. Keine neuen öffentlichen Typen mit Phasennummern im Namen.

### 2.1 Erlaubte Richtungen

```text
gretl-language-contract  ← gretl-runner, gretl-groovy
         ↑
gretl-tooling-core       ← gretl-lsp
         ↑
gretl-tooling-catalog    → aktuelle gretl-api/Registry/Taskmodule [nur Export-Build]
         ↓
   reines Katalog-JSON    → LSP-Distribution

gretl-tooling-tests      → Tooling + aktuelle Runtime [nur Testscope]
VS-Code-Host             → LSP-Protokoll + internes Modeler-Paket
Modeler                 → neutrale Wire-DTOs + React Flow/ELK
```

`gretl-language-contract` enthält nur Java-Werte und reine Funktionen. Es hängt weder von `gretl-api` noch von Groovy, LSP4J oder einem Toolingmodul ab. Bestehende öffentliche Runtime-Typen bleiben durch Adapter erhalten.

`gretl-tooling-core` darf Groovys **gepinnten Parser** und den vorhandenen kompatiblen JSON-Stack verwenden. LSP4J, VS Code, Taskhandler, Datenbanktreiber, GeoTools, Gradle-API, Runner und Scriptloader dürfen nicht in seiner Produktionsclasspath auftauchen. Das gleiche Verbot der Ausführungsmodule gilt transitiv für die ausgelieferte LSP-Distribution.

Der Katalogexporter darf aktuelle vertrauenswürdige Provider aus diesem Repository beim Build registrieren. Seine schweren Abhängigkeiten dürfen nicht als transitive Abhängigkeiten des LSP eingebunden werden. Der LSP bekommt **nur die generierte JSON-Ressource** und deren Manifest. Eine normale Dependency auf das Exporter-JAR mit allen Taskmodulen ist nicht erlaubt.

Ein internes npm-Workspace-Paket aus **diesem** Repository ist erlaubt. Das Verbot von Sibling-/Workspace-Abhängigkeiten bezieht sich auf andere GRETL-Varianten, nicht auf eine saubere interne Monorepo-Aufteilung.

### 2.2 Build und Distribution

Java 25, Maven-Wrapper und die im tatsächlichen Repository verwendete Groovy-6-Version beibehalten. Keine Previewfeatures. Kein Wechsel des Buildsystems. Node/npm für neue Frontend- und Orchestrierungsarbeit; Lockfiles versionieren. Bestehende Pythonwerkzeuge erhalten, nicht das Pythonverbot aus dem INTERLIS-Beispiel übertragen.

Der unabhängige Java-LSP wird als ausführbares JAR mit korrekt eingebundenen notwendigen Dependencies oder als ZIP mit `bin/` und `lib/` gebaut. Ein kleines startbares JAR ist zu bevorzugen, sofern Servicefiles, Lizenzen und JSON-RPC tatsächlich funktionieren. Maven und Gradle sind zum **Benutzen** des Servers nicht erforderlich.

Die Extension enthält den tatsächlichen Server, Katalog, Schemas und alle Webviewassets. Java 25 darf als dokumentierte externe Voraussetzung benutzt werden; eine neue JRE-Bündelungs-/Native-Image-Initiative ist **nicht** beauftragt. JDKpfad prüfen und verständlich erklären, statt ungefragt Java herunterzuladen.

Erste Extension-ID: `edigonzales.gretl-runtime-vscode`; Command-/Setting-Namespace `gretlRuntime`; Customeditor `gretlRuntime.jobModeler`. In P0 gegen tatsächliche Repositoryinhalte prüfen; bei einer inzwischen existierenden gleichwertigen Runtime-Extension deren eigene Identität konsistent fortführen. Keine stille Übernahme der Gradle-Extension-ID.

Buildmanifest: `schemaVersion`, tatsächlicher Git-Commit, Maven-/Extensionversion, Java-/Groovyversion, Katalogdigest, Wire-Schemaversion und SHA-256 aller ausgelieferten Dateien. Ein Buildmanifest wird **nach** dem Kandidatencommit in Buildausgaben erzeugt; kein eigener Commit-SHA im selben Commitinhalt.

## 3. Gemeinsame Verträge: `gretl-language-contract`

Die folgenden Extraktionen sind klein und verhaltensneutral. Bestehende Runtimefehlercodes, Namensregeln, Registrierungsreihenfolge und Tests bleiben erhalten. Kein pauschales Refactoring aller Runtimeklassen.

### 3.1 `DslVocabulary`

- `taskOptions(): Set<String>`: die implementierten Optionen `dependsOn`, `after`, `description`, `when`, `timeout`.
- `parameterOptions(): Set<String>`: `type`, `required`, `description`, `defaultValue`.
- `parameterTypes(): Set<String>`: tatsächliche unterstützte Typen; Recherchebasis `text`, `boolean`, `integer`, `path`.
- `scopeCapabilities(ScopeKind): ScopeCapabilities`: deklarative Kennzeichnung nach **aktuellem** `GretlScript`, `AbstractScope`, `ActionScope`, `CleanupScope` und `GroovyContextFacade`.

Die Runtime verwendet dieselben Konstanten oder einen kleinen getesteten Adapter. `ScopeCapabilities` darf keine Closures, Factorycallbacks oder Java-Reflection enthalten. Helpernamen nicht aus dem alten Gradle-LSP übernehmen; die aktuelle Runtime entscheidet.

### 3.2 `ProjectNameRules`

```java
ProjectScope parseInclude(String literal);
QualifiedName parseQualified(String literal);
QualifiedName qualifyLocal(ProjectScope scope, String localName);
QualifiedName resolveReference(ProjectScope scope, String spelling);
String renderReference(ProjectScope from, QualifiedName target);
```

`ProjectScope` und `QualifiedName` sind unveränderliche Werte, keine Dateipfade. `:prepare:run` und `:checks:local:run` dürfen nicht durch Dateinamensheuristiken erschlossen werden. Der vorhandene `ProjectPath` bleibt API-kompatibel und delegiert seine reine Namenslogik oder wird über einen präzisen Adapter angeschlossen. Vorhandene Pfadsicherheitsprüfungen bleiben im Host.

Einzeldateimodus und Projektmodus bleiben unterschieden. Bei Projektmodus bestimmt `settings.gretl` die Projektdefaults; `defaults` in einzelnen Jobs werden gemäss aktueller Runtime nicht still zu projektweiten Zielen aufgewertet. Diese Besonderheit durch Paritätstests fixieren.

### 3.3 `PlanSemantics`

Reine, aus dem bestehenden `ExecutionPlanner` extrahierte Auswahl-/Graphfunktion:

```java
PlanSelection plan(DeclaredGraph graph,
                   List<String> requestedTargets,
                   List<String> defaultTargets);
List<GraphProblem> validateDeclaredDependencies(DeclaredGraph graph);
Set<String> dataPredecessors(DeclaredGraph graph, String taskId);
```

`DeclaredGraph` enthält nur IDs, Registrierungsindizes und typisierte Kanten. `PlanSelection` enthält ausgewählte IDs, Reihenfolge und aktive Kanten. Keine `TaskDefinition`, `TaskBody`, Ergebnisse oder UUID-Runtimeobjekte im neutralen Vertrag.

Verbindliche Semantik aus der Recherchebasis:

1. `dependsOn`-Zyklen sind im vollständigen definierten Graphen unzulässig, auch ausserhalb der ausgewählten Ziele.
2. Auswahl folgt nur `dependsOn`, nicht `after`.
3. `after` ordnet nur bereits ausgewählte Tasks; ein Zyklus unter Einschluss von `after` ist für den **ausgewählten** Plan zu bewerten.
4. Ergebniszugriff erfordert direkte oder transitive `dependsOn`-Vorgänger. `after` erteilt kein Ergebniszugriffsrecht.
5. Bei sonst gleicher Topologie bleibt die bisherige Registrierungsreihenfolge massgeblich. Keine implizite parallele Ausführung und keine semantische Umordnung durch Diagrammlayout.

Der Runner adaptiert seine Definition auf diese Werte und mappt Ergebnisse/Fehler zurück. Der statische Kern darf `plan()` nur mit nachweisbar geschlossenem Definitionsgraphen als exakte Aussage verwenden. Bei dynamischer Registrierung liefert er eine ausdrücklich partielle Näherung, keinen behaupteten echten Ausführungsplan.

## 4. Operationskatalog

### 4.1 Datenmodell

`OperationCatalog` enthält `schemaVersion`, `runtimeVersion`, `dslVersion`, `catalogId`, `producers`, `operations` und `coverage`. `catalogId` ist ein Digest der kanonischen fachlichen Nutzlast; nicht rekursiv über sein eigenes Digestfeld berechnen. Buildzeit/Commit stehen im Envelope und verändern nicht die fachliche Identität unnötig.

`OperationDescriptor` enthält:

- stabile `operationId`, `symbol`, Beschreibung/Sprachtexte und Herkunft;
- `List<InputDescriptor> inputs`, `List<OutputDescriptor> outputs`;
- vorhandenes Beispiel, Kategorisierung und belegte optionale Toolinghinweise;
- ein separates `voidResult`, nicht aus fehlenden Labels erraten.

`InputDescriptor`: Name, Beschreibung, Pflichtstatus, vorhandener Default, erlaubte Werte, `ValueShape`, optionale `PathRole`, `sensitive`, Ergänzungsconstraints und Beispiel. `OutputDescriptor`: Name, Beschreibung, `ValueShape`, belegte Nullability. Keine Ausgabe nicht dokumentierter Getter.

`ValueShape` ist ein beschränktes algebraisches Schema: `Text`, `Boolean`, `Integer`, `Decimal`, `Path`, `ListOf`, `MapOf`, `ObjectOf`, `UnionOf`, `Opaque`. Shapes tragen nur tatsächlich belegte Konvertierungsregeln. Aus einem Java-Klassennamen nicht beliebige Stringkonversion oder numerische Subtypkompatibilität behaupten.

Numerische Literale mit möglichem Präzisionsverlust werden auf der Wireebene als typisierte Dezimalstrings transportiert. `Long`-Werte dürfen nicht durch JavaScript `Number` gerundet werden. „Kein Default“ und „Default null“ sind getrennte Zustände.

### 4.2 Exporter und Ergänzungen

**`OperationCatalogExporter`**

```java
OperationCatalog export(Collection<OperationType<?, ?>> operations,
                        CatalogBuildIdentity identity,
                        MetadataSupplements supplements);
void write(OperationCatalog catalog, Path outputDirectory);
```

Er exportiert `OperationType`/`InputField`/`OutputField` aus der aktuellen Runtime. Er ruft **keine** Builderfactory, Handlerfactory oder `convert`-/`assign`-Funktion auf. Insbesondere keine wirkliche Fachverbindung zum Ermitteln möglicher Werte öffnen.

**`RuntimeCatalogBootstrap`**

- `loadBuiltInProviders(TrustedBuildClasspath)` benutzt denselben ausdrücklich bereitgestellten Providerbestand wie die normale Distribution.
- `collectOperations()` liefert alle aktuellen mitgelieferten Operationen. Die recherchierten 44 sind kein dauerhaft hartcodierter Sollwert.
- `main(args)` schreibt Katalog/Manifest und endet bei unvollständigem oder widersprüchlichem Export ungleich 0.

**`MetadataSupplementLoader`**

- `loadFromOwnedResources(classpath)` liest nur statische JSON-Ressourcen `META-INF/gretl/tooling/*.json` aus den aktuellen Taskmodulen.
- `validateAgainstOperations(supplements, operations)` verlangt existierende IDs/Felder; doppelte oder widersprüchliche Angaben scheitern.

Ergänzungen dürfen nur beschreiben, was `InputField` noch nicht ausdrückt, etwa Listenform, Dateipfadrolle, sensible Eingabe, SQL-Templatefunktion und bedingte Feldbeziehungen. Sie dürfen Namen, Pflichtstatus, Defaults und erlaubte Werte nicht parallel überschreiben. Keine zweite vollständig handgepflegte Operationsliste im LSP.

**`CatalogJsonCodec`**, im Core-Katalogpaket:

- `decode(bytes, CatalogReadPolicy): CatalogLoadResult` prüft Schema, Grösse, Eindeutigkeit, erlaubte Werte und Digest.
- `encodeCanonical(catalog): byte[]` ist deterministisch.
- `validate(catalog): List<CatalogProblem>` liefert strukturelle Probleme mit Pfad, ohne Secrets.

**`CatalogRepository`**

- `loadBundled(resource, expectedIdentity)` startet mit geprüftem Buildkatalog.
- `mergeExplicitDescriptors(base, descriptors)` unterstützt ausdrücklich konfigurierte lokale JSON-Beschreibungen eigener Operationen; Kollisionen werden nicht nach Dateireihenfolge gelöst.
- `snapshot()` liefert unveränderlichen `CatalogSnapshot` mit Digest/Generation und Zuständen `AVAILABLE`, `PARTIAL`, `UNAVAILABLE`, `INCOMPATIBLE`.

Eigene Operationen sind über Daten beschreibbar, **nicht** durch automatisches Laden eigener JARs im LSP. Export eigener Provider geschieht separat in einem vertrauenswürdigen Build. Ein fehlender Zusatzdescriptor führt zu „Typinformationen nicht verfügbar“, nicht zu einer erfundenen Runtime-Inkompatibilität.

### 4.3 Paketgrenze nachweisen

Die Serverdistribution muss im Dependencygraph **und im tatsächlichen Archiv** frei von Taskmodulen/Runner/Scriptloader sein. Ein isolierter Start benötigt nur den LSP und Java 25. Den Exporter nie durch eine transitive Maven-Dependency in die Serverdistribution ziehen. Generierung/Reaktor-Reihenfolge durch einen expliziten Datenartefakt-/Ressourcenbuild regeln und in einem frischen Checkout testen.

## 5. Quelltextmodell und sicheres Parsing

### 5.1 Identität und Koordinaten

**`DocumentId`**: URI-basierte Identität, kein bloss normalisierter Anzeigename. Host löst Dateipfadidentität und erlaubte Schemes; Core führt keine versteckten Realpath-Aufrufe aus.

**`SourceStamp`**: `{documentId, contentSha256, bufferVersion?, diskIdentity?, origin, editable}`. Offene VS-Code-Dokumentversion, Dateihash, Workspacegeneration und Snapshot-ID sind verschiedene Werte.

**`SourceText`**: unveränderlicher Originaltext, `LineIndex`, BOM-/Zeilenendeinformationen und Stamp. Keine automatische LF-Normalisierung vor der Positionsberechnung.

**`TextSpan(int startUtf16, int endUtf16)`**: halboffen `[start,end)`, auf Originaltext bezogen. **`StringLiteralSpan`** unterscheidet ganzen Token, Inhalt, Quoteart und Escapeabbildung. Ein Dependency-Quick-Fix weiss dadurch, ob er Anführungszeichen ersetzen darf.

**`LineIndex`**

```java
int offsetAt(TextPosition utf16Position);
TextPosition positionAt(int utf16Offset);
String lineText(int line);
TextSpan contentSpan(int line);
```

CRLF zählt als ein Zeilenwechsel. Emoji/Supplementary Characters, Tabs, BOM und Dateiende werden getestet. Parser-/Lexerkoordinaten können Codepoints statt UTF-16 zählen: `ParserCoordinateMapper` übersetzt nach geprüftem Verhalten der gepinnten Groovyversion; keine pauschale Annahme „Java = überall gleiche Spalte“.

### 5.2 Parserpipeline

**`GretlDocumentParser`**

```java
ParsedDocument parse(SourceText source, ParseOptions options,
                     CancellationToken cancellation);
```

Er orchestriert Lexing, sicheren ASTpfad und tokenbasierte Recovery. Die Datei wird nicht ausgeführt, kompiliert zu Bytecode oder gegen Jobdependencies typgeprüft.

**`SafeGroovySyntaxReader`**

- `readModule(SourceText, ParseBudget): SyntaxReadResult` nutzt Groovys reine Parse-/Convert-Schritte, wie der aktuelle Settingsparser, mit kontrollierter Konfiguration.
- Keine allgemeine `CompilationUnit` mit projektseitigem Transformations-/Serviceclasspath als Abkürzung.
- Kein Grape, `@Grab`, Compiler-Customizer, AST-Testausführung, dynamische Dependencyauflösung oder Projekt-Classloading. Annotationen als Syntax lesen, nicht ausführen.
- Begrenzte Eingabegrösse/Verschachtelung, kein Weiterlaufen in semantische Kompilierphasen. Catch-and-empty ist verboten; Syntaxfehler bleiben mit echten Positionen erhalten.

**`GroovyTokenReader`**

- `lex(SourceText, ParseBudget): TokenIndex` liefert Originalspans und Kanäle für Code, Kommentare und Strings.
- Berücksichtigt einfache/doppelte/triple-quoted, slashy/dollar-slashy Strings, GStringteile, Escapezeichen und unvollständige Tokens gemäss gepinntem Groovyparser.
- Interpolation wird nicht als statischer String behandelt. Kommentare/Strings dürfen keine falschen Taskregistrierungen erzeugen.

**`GretlAstExtractor`**

- `extract(SyntaxReadResult, TokenIndex): SyntaxDocument` erkennt ausschliesslich aktuelle DSL-Kontexte und belegte implizite Receiver.
- `extractTask(call, scope)`, `extractAction(call, scope)`, `extractParameter(call, scope)`, `extractDefaults(call, scope)`, `extractCleanup(call, scope)` behandeln Map-first-Argumente korrekt.
- `extractConfiguration(closure, operationId)` unterscheidet Feldzuweisung von lokaler Variablendeklaration (`def x = ...`) und beliebigen Codeanweisungen.
- `extractInvocation(call, enclosingScope)` erkennt `ctx.run(Operation) { ... }` als **innere Invocation**, nicht als neuen Top-Level-DAGknoten.
- Lokale gleichnamige Methoden/Variablen und fremde Receiver erzeugen keine blind akzeptierten DSL-Elemente. Unklare Bindung wird als unklar markiert.

**`RecoveringDslParser`**

- `recover(TokenIndex, SyntaxReadResult): RecoveryDocument` findet sichere Strukturinseln und unvollständige Köpfe.
- `recoverTaskHeader(...)` funktioniert auch für `task('x', Sql...` und offene Optionsmaps.
- `recoverConfiguration(...)` behält bereits erkannte Zuweisungen bis zur beschädigten Stelle.
- Keine Regex-Klammerzählung über Rawtext, kein `body.indexOf(name)` zur Bereichsermittlung.

**`ParseResultMerger`**

- `merge(ast, recovery, tokens): ParsedDocument` übernimmt genaue ASTinseln und nötige Recoveryinseln ohne doppelte Knoten.
- Alle wirklichen Parserprobleme bleiben erhalten, auch ohne einen einzigen Task.
- Probleme werden über Code/Span/Ursache dedupliziert; keine verdoppelte Scannerfehlerliste.
- Vollständigkeits-/Bearbeitbarkeitsflags je Knoten, nicht nur ein globales `astBased`-Boolean.

### 5.3 Neutraler Syntaxbaum

`ParsedDocument` besitzt: SourceStamp, DocumentKind (`JOB`, `SETTINGS`, `GROOVY_SOURCE`, `SQL`), Tokens, Statements, Probleme und `Coverage`.

Knotenfamilien:

| Knoten | Erforderliche Information |
|---|---|
| `TaskDeclaration` | literaler/unklarer Name, Operationausdruck, Optionen, Body, alle Spans |
| `ActionDeclaration` | Name/Optionen, Codebody als unveränderte Sourceinsel |
| `ParameterDeclaration` | Name, Typ, Beschreibung, required/default sowie deren Spans |
| `DefaultTargetsDeclaration` | **alle** Ziele und jeweilige Literalspans |
| `CleanupDeclaration` | Name/Body, eigener Scope ohne Task-DAGsemantik |
| `SettingsIncludeDeclaration` | Include-Literale, keine ausführbare Settingslogik |
| `FieldAssignment` | Feld-/RHS-/Gesamtspan, erkannter oder opaker Wert |
| `InnerInvocation` | Typ, Konfiguration, Einbettung, kein registrierter Taskname |
| `OpaqueStatement` | Originalspan, Grund, vermutete mögliche Wirkungen |

`ValueExpression` unterscheidet Literal, Liste, Map, `ParameterReference`, `ResultReference`, `ResultFieldReference`, `FileReference`, `WorkPathReference`, `TaskConfigReference`, `SecretReference` und `OpaqueExpression`. Nur erkannte sichere Funktionen werden strukturiert; beliebige Methoden nicht auswerten.

`Coverage` umfasst mindestens `declarations`, `references`, `fieldAssignments`, `syntax` mit `COMPLETE | PARTIAL | UNKNOWN`. Eine Schleife mit dynamischer Taskregistrierung kann Declarationcoverage reduzieren, ohne bekannte unabhängige Tasks aus der Ansicht zu entfernen.

## 6. Workspace, Projekte und Analyse

### 6.1 Snapshotbasierte Versorgung

**`SourceRepository`** ist ein injiziertes Interface:

```java
Optional<SourceText> read(DocumentId id, ReadPolicy policy);
List<DocumentId> listProjectEntries(ProjectLocator locator, ScanBudget budget);
SourceStamp currentStamp(DocumentId id);
```

Der Core enthält keine beliebigen Netzwerkzugriffe. Der LSP-Host liefert einen begrenzten Filesystemadapter plus Bufferoverlay. Schreibmethoden fehlen bewusst.

**`DocumentStore`**

- `open(id, languageId, version, text)` legt unveränderlichen Bufferstand an.
- `applyChanges(id, version, changes)` verarbeitet **alle** Änderungen in Reihenfolge; Full-/Incrementalmodus gemäss ausgehandeltem Vertrag.
- `close(id)` verwirft Overlay, nicht notwendigerweise das gesamte projektrelevante Dokument; anschliessend Diskstand kontrolliert nachladen.
- `capture(ids)` liefert konsistenten Zustand, ohne dass laufende Queries später veränderte Maps sehen.

Versionsrücksprünge oder ungültige Ranges werden nicht still akzeptiert. Geöffnete ungespeicherte Daten gewinnen gegen Disk, auch bei SQL und `settings.gretl`.

**`WorkspaceManager`**

- `addRoot`, `removeRoot`, `updateConfiguration`, `onSourcesChanged` erzeugen neue Generationen.
- `capture(ProjectId)` liefert unveränderliches `WorkspaceCapture` aus Konfiguration, Katalog, Quellen und Projektidentität.
- Unterschiedliche Workspacefolder bilden **getrennte GRETL-Projekte**; identische Tasknamen in zwei Jobs dürfen sich nicht gegenseitig auflösen.
- Überlappende Roots/nested Settings nach einer festen, getesteten Nächste-Settings-Regel behandeln; mehrdeutige Besitzverhältnisse sichtbar ablehnen.

**`AnalysisScheduler`**

- `submit(ChangeSet)` koalesziert pro Projekt/Generation; Dokumentnotifikation selbst macht keine vollständige Analyse oder SQL-IO.
- `analyze(capture, token)` auf begrenztem Executor; maximale parallele Analysen und Queue explizit.
- `publishIfCurrent(snapshot)` prüft Serverinstanz, Projektgeneration, Konfigurationsdigest, Katalogdigest und Sourcevektor.
- `cancelProject`, `close` beenden eigene Arbeit; keine CommonPool-Aufgaben ohne Lifecycle.

Ein Request erfasst früh seinen Snapshot. Er darf nicht das TextDocument der Version N mit Syntaxbereichen der Version N−1 mischen. Alte Ergebnisse können als stale angezeigt werden, nicht als Grundlage einer Mutation.

### 6.2 Projekte und Pfade

**`ProjectResolver`**

```java
ResolvedProject resolve(ProjectRequest request, SourceRepository sources,
                        CancellationToken cancellation);
```

Settingsparse erhält den aktuellen Textbuffer. Reine Parser-/Namenslogik des bestehenden `GretlProjectParser` wiederverwenden oder minimal herauslösen; dessen dateibasierte Einstiegsmethode nicht blind auf einen ungespeicherten Stand anwenden. Deklarative Syntax und Sicherheitsverträge erhalten.

**`PathReferenceResolver`**

- `resolveJobResource(sourceId, literal)` basiert auf dem jeweiligen Script-/Subprojektverzeichnis.
- `describeWorkPath(scope, literal)` erzeugt nur symbolische Information, **kein** Arbeitsverzeichnis.
- `describeExternalPathParameter(parameter)` zeigt die Runtimebasis Start-CWD, behauptet ohne Run-CWD keinen aufgelösten Dateiort.
- Dynamische/interpolierte Pfade werden nicht geraten. Externe Dateien nur nach expliziter erlaubter Lesegrenze; Grössenlimit, reguläre Datei, Symlink-/Traversalprüfung und keine UNC-/HTTP-Netzwerkauflösung beim Tippen.

Tooling darf in der Runtime zulässige externe Pfade als „statisch nicht untersucht“ behandeln. Es darf diese nicht allein wegen seiner engeren Lesepolitik als ungültigen Job deklarieren.

### 6.3 Semantisches Modell

**`SemanticAnalyzer`**

```java
AnalysisSnapshot analyze(WorkspaceCapture capture,
                         List<ParsedDocument> documents,
                         CatalogSnapshot catalog,
                         CancellationToken cancellation);
```

Pipeline: Projektzuordnung → Symboltabelle → Referenzbindung → Feld-/Scopeprüfung → Graphprüfung → Diagnosen → Query-/Editfähigkeiten.

**`SymbolBinder`**

- `declareSymbols(...)`: Tasks, Actions, Parameter, Cleanups und statisch sichere TaskRefvariablen.
- `bindReferences(...)`: lokale/qualifizierte Namen, Defaults, Headerabhängigkeiten, Ergebnis-/Parameterzugriffe.
- `resolveOperationSymbol(...)`: aus Katalog/Scope, nicht Java-Importpflicht für Built-ins.
- Doppelte Namen und Mehrdeutigkeit werden nicht durch „erstes Ergebnis“ aufgelöst.

**`ReferenceIndex`** hält deklarierende und referenzierende Sourceanker getrennt, inklusive `ReferenceKind` und `Resolution = RESOLVED | UNRESOLVED | AMBIGUOUS | DYNAMIC`. `referencesTo(SymbolId)` liefert nur belegte Referenzen und zusätzlich Coverage. Kein Textsuche-Rename in Kommentaren/SQL/Strings, die keine GRETL-Referenz sind.

**`DependencyAnalyzer`** adaptiert einen geschlossenen Graph auf `PlanSemantics`. Für einen partiellen Graph bleiben sichtbare sichere Kanten erhalten; unbekannte dynamische Teile werden als Hinweise dargestellt. Der Modeler zeigt den vollständigen **statisch bekannten Definitionsgraph**, nicht nur den aktuellen CLI-Zielplan. Eine optionale Zielvorschau kennzeichnet ihre Annahmen.

**`ResultAccessAnalyzer`**

- `analyzeAccess(reference, enclosingScope, graph, catalog)` prüft direkte/transitive Datenvorgänger und Operationausgänge.
- `resultOptional` ändert diese Abhängigkeitsregel nicht.
- Actions liefern beliebige Werte; kein erfundenes `.file`-Schema.
- Ein innerer Operationsaufruf ist kein frei adressierbarer Top-Level-Task. Ergebnisse solcher Aufrufe nur entsprechend tatsächlich sicherer lokaler Bindung typisieren.
- Fehlende Datenkante nur dann als sicherer Fehler, wenn die relevante Graph-/Referenzcoverage vollständig ist.

**`ParameterAnalyzer`** prüft Deklaration versus Lesezugriff, doppelte Namen, erforderliche Beschreibung, unterstützte Typen, `required`/`defaultValue` und Toplevel-Leseverbot entsprechend aktueller Runtime. Kein Zugriff auf echte Startparameter oder Secretwerte.

**`ConfigurationAnalyzer`** prüft bekannte Feldnamen, reservierte Namen, eindeutig falsche Literale/Enums und belegte Pflichtfelder. Fehlendes Feld ist bei beliebigem dynamischem Body nicht automatisch ein bewiesener Fehler. Mehrfache Zuweisungen sind nicht ohne Runtimebeleg unzulässig; sie können aber gezieltes grafisches Editieren sperren. Kein Ausführen des echten Converters zur Editorvalidierung.

**`SqlTemplateAnalyzer`**

- `analyze(SqlTemplateInput, SourceRepository, SqlPlaceholderRules)` verwendet die **aktuellen** Feldnamen und Platzhalterregeln von `SqlExecutor`/`DuckDbSqlExecutor`.
- P0 ermittelt diese aus aktueller Referenz/Implementierung; nicht `sqlFiles/sqlParameters` oder eine beliebige `${...}`-Syntax aus `gretl-next` voraussetzen.
- Fehlende Parameterangabe als leere bekannte Map analysieren, soweit die übrige Konfiguration vollständig ist; nicht einfach die gesamte Prüfung überspringen.
- Geöffnete SQL-Buffer, Kommentare/Literale gemäss tatsächlicher Runtime-Platzhaltersemantik, Dateibasis je Script und Dateiänderungsabhängigkeiten berücksichtigen.
- Dynamische Map/Dateiauswahl → partielle Aussage. Kein DBzugriff und keine SQL-Ausführung.

### 6.4 Diagnosen

`ToolingDiagnostic` enthält stabilen sprachneutralen Code, Severity, konkrete Sourceanker, RelatedInformation, strukturierte Daten und ggf. angebotene Fixarten. Codefamilien für Syntax, Projekt, Scope, Operation/Feld, Referenz/Graph und Katalog klar trennen.

DE/EN-Anzeigetexte mit English-Fallback; bestehende geeignete `gretl-i18n`-Ressourcen wiederverwenden, keine globale Localeänderung. Nicht unkontrollierte Groovy-Exceptiontexte mitsamt Quellinhalt/Secrets publizieren. Eine unbekannte Quellposition wird als globale Meldung gekennzeichnet, nicht als vermeintlich präziser Fehler an Zeile 1 ausgegeben.

## 7. Queries und verlustarme Bearbeitung

### 7.1 `LanguageQueries`

Alle Methoden verwenden einen aktuellen `QueryContext` mit `AnalysisSnapshot`, Dokument und Position; keine Query reparst unabhängig im LSPhandler.

```java
List<CompletionProposal> complete(QueryContext context);
Optional<HoverInfo> hover(QueryContext context);
Optional<SignatureInfo> signatureHelp(QueryContext context);
List<SourceAnchor> definitions(QueryContext context);
ReferenceResult references(QueryContext context, boolean includeDeclaration);
List<OutlineNode> documentSymbols(QueryContext context);
List<FileLink> documentLinks(QueryContext context);
List<FoldingRegion> foldingRanges(QueryContext context);
RenamePreparation prepareRename(QueryContext context);
EditPlanningResult rename(QueryContext context, String newName);
List<CodeFixProposal> codeActions(QueryContext context, List<DiagnosticId> ids);
```

Completionkontexte umfassen Operationstyp, Headeroption, Konfigurationsfeld, zulässigen Enum-/Booleanwert, Parametername, Dependencyziel, Ergebnis-Task/-Feld, Jobdateipfad und Settings-Include/Defaults. Nicht in Kommentaren und fremden Groovy-Aufrufen GRETL-Vorschläge injizieren. Keine Importfixes für registrierte Built-in-Operationssymbole.

SignatureHelp zeigt die aktuelle DSL, nicht Gradle-Methodensignaturen. Outline trennt Parameter, Tasks, Actions, Cleanup und innere Invocations. Definition/References sind projektbezogen und sourcegenau.

Vollständige Groovy-/Java-Typinferenz und Whole-file-Formatting sind Nicht-Ziele. Folding und statische Syntaxfärbung sind enthalten; semantische Tokens dürfen ergänzend nur bei tatsächlich implementierter Coverage advertised werden.

### 7.2 Editierbarkeitsentscheidung

**`EditabilityAnalyzer`**

```java
EditCapability assess(AnalysisSnapshot snapshot, ElementId target,
                      EditIntent intent);
```

Ergebnis enthält `ALLOWED | NEEDS_OPEN_DOCUMENTS | REJECTED`, Gründe und Sourceabhängigkeiten. Kein pauschales „AST vorhanden = sicher editierbar“.

Sicher unterstützter Bereich: literale eindeutige Namen/Operationen, eindeutig lokalisierte Optionsmaps und direkte Feldzuweisungen mit erhaltenen Tokenbereichen. Ein komplexer Ausdruck darf unverändert bleiben, während ein anderes sicher getrenntes Feld bearbeitet wird. Mehrfache Zuweisung, unklare Scopes, dynamische Namen, beschädigte Zielregion oder unbekannte Referenzen können genau die betreffende Aktion sperren.

### 7.3 `SemanticEditPlanner`

```java
EditPlanningResult plan(AnalysisSnapshot snapshot,
                        SemanticEditIntent intent,
                        EditStyle style,
                        CancellationToken cancellation);
EditValidation validate(PlannedEdit edit, AnalysisSnapshot current);
```

Gemeinsame Intents für Quick Fixes und späteren Modeler:

- `AddTask(document, insertionAnchor, localName, operationId, initialFields)`;
- `AddAction(document, insertionAnchor, localName)` mit gültigem Code-Template, nicht fingierter Fachoperation;
- `AddParameter(document, declaration)`;
- `SetField(target, field, EditableValue)` / `RemoveField(target, field)`;
- `SetTaskOption(target, option, value)` für belegte strukturierte Werte;
- `AddDependency(producer, consumer, DEPENDS_ON | AFTER)`;
- `RemoveDependency(edgeId)`;
- `BindResult(producer, outputField, consumer, inputField)`;
- `RenameSymbol(symbolId, newLocalName)`;
- `DeleteTask(symbolId)`;
- `SetDefaults(scope, targetIds)`.

Der Core implementiert diese Intents bereits in P3 und testet sie ohne GUI. Der LSP darf sie ab P4 transportieren; der grafische Benutzerpfad wird erst in P7 implementiert.

`EditableValue` ist eine getypte Union aus Literal/Liste/Map/ParameterRef/ResultFieldRef/JobFileRef/WorkPathRef/SecretAliasRef. Kein beliebiger JavaScriptcode und keine vom Webview gelieferte TextEdit-Liste. Ein opaker Ausdruck kann angezeigt/navigiert, aber nicht still als Literal umgeschrieben werden. Ein späterer Raw-Groovy-Formulareditor ist nicht nötig; „Im Quelltext bearbeiten“ genügt für Expertenfälle.

### 7.4 Schreibregeln pro Operation

**Task anlegen:** gültigen freien lokalen Namen und bekannte Operation verlangen. An sicherer Statementgrenze einfügen, bestehende Reihenfolge nicht umsortieren. Pflichtfelder ohne bekannte Vorgabe nicht mit erfundenen fachlichen Werten füllen: Form hält zunächst einen lokalen Entwurf; Erzeugen erst nach erforderlichen Eingaben oder ausdrücklich gekennzeichnetem unvollständigem Entwurf mit echter Diagnose. Für den Abschlussdemojob müssen alle Pflichtwerte gesetzt sein.

**Feld ändern:** nur den RHSspan der eindeutigen Zuweisung ersetzen; bei neuem Feld in den sicheren Body einfügen. Einrückung, Quotes soweit sinnvoll und vorhandenes Zeilenende übernehmen. Kommentare bleiben erhalten. Bei mehreren Zuweisungen keine willkürliche „letzte“ auswählen.

**Abhängigkeit verbinden:** Pfeilrichtung im UI ist Producer → Consumer, erzeugter Text steht im Consumerkopf. Listen/Einzelwertformen korrekt bearbeiten. `after` bleibt `after`; es entsteht kein Ergebniszugriffsrecht. Zyklen/Planbedingungen gemäss `PlanSemantics`, keine vereinfachte UI-Zyklusregel.

**Ergebnis verbinden:** `BindResult` setzt `result('...').feld` im Consumer **und** ergänzt nur bei Bedarf eine direkte `dependsOn`-Kante. Eine bereits ausreichende transitive Abhängigkeit genügt. Beides ist ein zusammengehöriger Editplan. Bekannte Inkompatibilität ablehnen; unbekannte Typkompatibilität nicht als bewiesen kompatibel ausgeben und für eine automatische Portverbindung ablehnen, mit Quelltextfallback.

**Verbindung entfernen:** Nicht still Ergebniszugriffe ungültig machen. Falls nach Entfernen weiterhin eine transitive erlaubte Kette besteht, kann Entfernung zulässig sein. Andernfalls mit konkreten betroffenen Referenzen ablehnen. Feldbindung entfernen entfernt nicht automatisch eine womöglich anderweitig benötigte Dependency.

**Umbenennen:** alle belegten Referenzarten einschliesslich `defaults`, `result`, `resultOptional`, `dependsOn`, `after` und Projektpfaden berücksichtigen. Stringliterale korrekt neu quoten, nicht Quotes verdoppeln. Keine Teilumbenennung bei relevanter dynamischer/unvollständiger Referenzcoverage. Parameterumbenennung nur bei geschlossenem Projektvertrag; externe CLI-Aufrufer können nicht automatisch aktualisiert werden und werden in der Vorschau als externe Schnittstellenänderung genannt.

**Löschen:** nur eindeutig statischer Task ohne eingehende/verbleibende Referenzen und ohne relevante dynamische Unsicherheit. Kein automatisches Cascade-Delete oder Neuberechnen von Defaults. Benachbarte Kommentare ohne klare Besitzzuordnung erhalten. Action-Bodies sind nicht durch ihr Auftreten automatisch grafisch umschreibbar.

**Defaults:** Einzeljob versus Settings-Projektvertrag beachten. Alle ausgewählten Ziele erhalten; niemals ein weiteres Ziel still abschneiden.

### 7.5 Lossless Editengine

**`SourceEditBuilder`**

- `replace(span, expectedOldText, replacement)`;
- `insert(anchor, text)`;
- `deleteOwnedRegion(region, triviaPolicy)`;
- `build(preconditions): PlannedEdit` prüft Spans, Überlappungen, EOL und Preimages.

**`EditVerifier`**

- `applyInMemory(snapshot, edit)` wendet Edits absteigend nach Offset auf eine Kopie an.
- `verifyPreservation(before, after, edit)` beweist unveränderte Bereiche byte-/textgenau; Datei-Encoding beim Diskexport separat berücksichtigen.
- `reparseAndCheck(postImage, expectedSemanticDelta)` bestätigt den beauftragten Unterschied, keine neuen unbeabsichtigten Parsefehler und Erhalt nicht betroffener Symbole.

Keine Whole-file-AST-Prettyprint-Runde. Ein No-op erzeugt keinen Dirtybuffer und keinen Undo-Eintrag. Mehrere Field-/Mapupdates aus einer Benutzerbestätigung bilden genau eine fachliche Texttransaktion.

### 7.6 Precondition- und Racevertrag

`PlannedEdit` enthält Plan-ID, Serverinstanz, Snapshot-ID, Projektgeneration, Katalog-/Konfigurationsdigest, Source-Stamps **aller semantisch relevanten Dateien**, versionierte Änderungen und Zusammenfassung. Ein geändertes `settings.gretl` invalidiert einen Plan auch dann, wenn es nicht selbst editiert wird.

Der Server schreibt niemals Jobdateien. Der Client öffnet für Modeler-Multifileedits alle editierbaren Zieldateien, synchronisiert sie und verlangt bei veränderter Capture einen neu geplanten Edit. Vor Anwendung werden erwartete Bufferstände/Hashes geprüft und der Server validiert die Snapshotpreconditions erneut.

Standard-LSP-Rename/CodeActions liefern `documentChanges` mit Versionen, keine unversionierten `changes` als Ersatz. Für geschlossene Dateien wird der Diskhash vor Rückgabe geprüft; ein unsicherer Zielbestand wird abgelehnt.

**Ehrliche Grenze:** LSP/VS Code bieten keine allgemeine Dateisystemtransaktion gegenüber beliebigen externen Schreibern. Auch eine Hashprüfung ist kein atomarer Compare-and-Swap mit dem Dateisystem. Bekannte Konflikte müssen fail-closed sein; keine absolute Racefreiheit behaupten. Der Modeler nutzt offene TextDocuments und native Textedits, nicht `fs.writeFile` als angeblich stärkeren Schutz. Bei Konflikt neue Analyse statt stiller Teilanwendung oder nachträglichem Überschreiben von Benutzertext.

## 8. LSP-Server

### 8.1 Klassen und Lifecycle

**`GretlServerLauncher`**

- `main(String[])`: Argumente, Java-/Bundle-/Katalogidentität, kontrollierter stdio-Start.
- `launchStdio(InputStream, OutputStream, ServerServices)`: JSON-RPC über stdout, Logs über stderr.
- `terminate(ExitReason)`: eigene Analyse-/IOexecutor und Transport beenden; Prozess endet wirklich. Kein `System.exit` tief in wiederverwendbaren Fachklassen.

**`GretlLanguageServer`**

- `initialize`: Capabilityverhandlung, Locale, Workspacefolders, Positionencoding und Customprotokoll.
- `initialized`: erst dann Watcher-/Clientregistrierungen nach negotiated capabilities.
- `shutdown`: Arbeit ablehnen/abbrechen, Ressourcen deterministisch schliessen, Antwort senden.
- `exit`: korrekter Prozessabschluss auch bei offenem stdin; Exit ohne vorheriges Shutdown separat testen.
- Zustandsautomat `NEW → INITIALIZED → SHUTTING_DOWN → CLOSED`; unzulässige Anfragen strukturiert beantworten.

**`GretlTextDocumentService`** delegiert `didOpen/didChange/didSave/didClose`, Completion, Hover, SignatureHelp, Definition, References, PrepareRename/Rename, DocumentSymbols, DocumentLinks, FoldingRanges und CodeActions an Workspace/Queries. Er enthält keinen DSLparser, kein blockierendes Filesystemlesen und keine globale `lastAnalysis`-Map ohne Versionsvertrag.

**`GretlWorkspaceService`** behandelt Konfigurationsänderungen, beobachtete Dateien und Folderänderungen tatsächlich. Keine no-op-Funktion bei zugleich beworbener Unterstützung.

**`LspDiagnosticMapper`** bildet neutrale Diagnosen in LSPbereiche/RelatedInformation und DE/EN-Nachrichten ab. `publishDiagnostics` enthält bei offenen Quellen die zugehörige Version. Veraltete Veröffentlichungen werden vor dem Senden verworfen.

**`LspQueryMapper`** übersetzt neutrale Queryergebnisse in LSP-DTOs; respektiert Clientcapabilities wie Snippets, Markdown und versionierte WorkspaceEdits. Nicht unterstützte Capabilities liefern sinnvollen eingeschränkten Weg oder klare Ablehnung, keine ungültigen DTOs.

**`LspHostSourceRepository`** besitzt begrenzte Disk-IO, Filewatcher und Trustpolicy. **`ServerConfiguration`** dekodiert erlaubte Felder atomar, behält bei ungültigem Update den letzten gültigen Stand.

### 8.2 Synchronisation und Ausführung von Anfragen

Full Sync ist als erste korrekte Implementierung zulässig; der endgültige Server unterstützt geordnetes Incremental Sync mit sauberen Ranges, wobei auch Full-Change-Ereignisse entsprechend dem Protokoll korrekt verarbeitet werden. Fähigkeiten erst nach vorhandenem Test advertisen.

Anfragen werden an ihren Eingangssnapshot gebunden. Cancellation, schnelle Versionswechsel, Close/Reopen und Serverrestart sind reale Tests. `CompletableFuture` allein ist kein Cancellationkonzept. Nicht unterbrechbare Parserarbeit wird durch begrenzte Eingaben/Parallelität isoliert und ihre obsolete Antwort verworfen; keine unsicheren Threadstopps.

Standard-LSP über stdio genügt. Kein Port für den Modeler. Kein `gretl.getOverview` als ungetyptes Mapobjekt mit ungeprüften URIs weitertragen; einen kleinen versionierten Customvertrag benutzen.

### 8.3 Customprotokoll v1

`experimental.gretlRuntime` enthält implementierte Fähigkeiten: `protocolVersion`, `catalogSchemaVersion`, `graphSchemaVersion`, `graph`, `editPlanning`, `navigation`, `analysisNotifications`. In frühen Phasen nicht implementierte Fähigkeiten false/fehlend.

`GretlLanguageServerApi extends LanguageServer` deklariert echte LSP4J-`@JsonRequest`-Methoden; Registrierung mit einem echten stdio-Prozesstest nachweisen.

| Wiremethode | Java-Methode | Vertrag |
|---|---|---|
| `gretl/catalog` | `catalog(CatalogRequest)` | Versionierter reiner Katalog oder kontrollierter Unavailable-/Mismatchstatus |
| `gretl/graph` | `graph(GraphRequest)` | Snapshotgebundene statische Projektion, Scope/Targets ausdrücklich |
| `gretl/planEdit` | `planEdit(EditRequest)` | Getypter Intent → PlannedEdit oder konkrete Ablehnung; keine Dateischreibwirkung |
| `gretl/validateEdit` | `validateEdit(EditValidationRequest)` | Plan-ID/Source-/Projektpreconditions nochmals gegen aktuellen Snapshot prüfen |
| `gretl/navigation` | `navigate(NavigationRequest)` | Aktuelles Element ↔ exakte Quellposition, kein Namenssuchen im Client |

Notification `gretl/analysisReady`: Serverinstanz, Projekt-ID, Generation, Snapshot-ID, Dokumentversionsvektor, Completeness. Keine vollständige ASTkopie. Ein Graph wird nur bei Bedarf/abonnierter Ansicht projiziert.

Customresponse-Grundform:

```text
protocolVersion, schemaVersion, serverInstanceId,
projectId, workspaceGeneration, snapshotId,
catalogId, sourceStamps[], completeness, notices[], payload
```

64-bit-Revisionswerte als Strings auf der Wireebene. IDs niemals lexikografisch als Zeitstempel vergleichen. Begrenzte Datenmengen, Schema-/IDprüfung, Cancellation. Nur dem Server bekannte Document-/Element-IDs; keine beliebige URL als Downloadauftrag.

## 9. VS-Code-Host

### 9.1 Klassen und Verantwortungen

**`ExtensionController`**

- `activate(context)` registriert Commands, Sprache/Selector, Status und einen Serverclient je Workspacefenster.
- `dispose()` entfernt alle eigenen Listener, Watcher und Panels, stoppt eigenen Server.

**`ServerController`**

- `resolveLaunchPlan(settings, manifest, environment)` prüft Java25, echte Dateien, Artefaktdigests und Trust.
- `start()` startet Argumentarray mit `shell:false`; keine Shellinterpolation von Jobpfaden.
- `restart(reason)` begrenzt, leert alte Server-/Snapshot-IDs, synchronisiert offene Buffer neu.
- `stop()` wartet auf Shutdown/Exit und beendet nur eigene festgehaltene Prozess-IDs, nicht alle Java-/VS-Code-Prozesse.

**`DocumentSelectorController`**

- Erfasst `**/job.groovy` und `**/settings.gretl`, nicht nur `.gradle`.
- Bietet „Aktive Groovy-Datei als GRETL-Job verwenden“ für andere Jobnamen; eine bewusste Sprachzuordnung `gretl-runtime` ist zulässig.
- Bewusst erzeugte ungespeicherte Texttemplates können mit expliziter GRETL-Sprachkennung analysiert werden; ohne Speicherort bleiben jobrelative Pfade unaufgelöst. Der Modeler beginnt mit einer gewählten gespeicherten leeren Jobdatei, nicht mit einer erfundenen temporären Pfadbasis.
- Nicht ungefragt alle Groovy-/Gradledateien übernehmen. Syntaxfärbung darf eine gepinnte lizenzierte Groovy-Grammatik nutzen; das ist keine semantische Parserquelle.

**`ConfigurationBridge`** trennt Serverstart, Sprach-/Projektkonfiguration und UIlayout. Runtime-/Jarpath als sicherheitsrelevante Optionen behandeln. Ungültige Einstellungen überschreiben nicht den letzten funktionsfähigen Startplan.

**`GretlProtocolClient`**

```ts
ensureCompatible(capabilities: unknown): ProtocolCapabilities;
catalog(request, token): Promise<CatalogResponse>;
graph(request, token): Promise<GraphResponse>;
planEdit(request, token): Promise<EditResponse>;
validateEdit(request, token): Promise<EditValidationResponse>;
navigate(request, token): Promise<NavigationResponse>;
onAnalysisReady(listener): Disposable;
```

DTOs aus einem gemeinsamen Schema generieren/prüfen, keine frei driftenden Java-/TSkopien. Ein JSONschema-Check ersetzt keine Fachprüfung im Core.

**`EditPlanApplier`**

- `prepareWritableDocuments(plan)` öffnet benötigte lokale Dateien und fordert nach geänderter Versionslage Neuplanung an.
- `validatePreconditions(plan)` prüft Bufferstände, Serverinstanz, Trust und begrenzte Planlebensdauer.
- `apply(plan)` benutzt genau einen nativen VS-Code-WorkspaceEdit für zusammengehörige Textänderungen; keine eigene Dateischreiblogik.
- `awaitAcceptedAnalysis(plan)` wartet auf passenden neuen Zustand; Fehler wird nicht durch optimistische Graphmutation verdeckt.

Die Implementierung prüft ausdrücklich, welche Versionsgarantien der gepinnte `vscode-languageclient` bei der DTOkonvertierung tatsächlich erhält. Nicht annehmen, dass `vscode.WorkspaceEdit` beliebige LSP-Preconditions automatisch bewahrt. Bekannte Konflikte vor Anwendung ablehnen; die in §7.6 benannte Restgrenze ehrlich dokumentieren.

**`TemplateController`** erzeugt lokale ungespeicherte GRETL-Templates und speichert nur nach Benutzeraktion. Kein Onlinevorlagendownload nötig.

### 9.2 Trust und Remote

Untrusted Workspace: statische Färbung/Information möglich, keine frei konfigurierten Java-/JAR-/Katalogpfade, kein Jobstart. Konservative erste Umsetzung darf den LSP bis Workspace Trust aussetzen; das UI erklärt den Zustand. Keine Behauptung vollständiger Java-Sandbox.

`extensionKind: ["workspace"]`: Server im Workspacehost. Remote-URIs und Webviewasset-URIs über die unterstützten VS-Code-APIs behandeln. Browser-only VS Code ohne Java ist nicht Bestandteil. Die Pflichtprüfung umfasst die tatsächliche Primärplattform und einen Linux-Serverprozess; zusätzliche reale Remote-/Plattformtests als solche ausweisen, keine universelle Freigabe aus einem Crossbuild.

## 10. Graphprojektion und Modeler

### 10.1 Fachlicher Graph

**`JobGraphProjector`**, im Java-Core, bereits in P3 implementieren und ohne GUI testen; P4 transportiert ihn, P6 ergänzt ausschliesslich seine sichtbare Nutzung:

```java
JobGraph project(AnalysisSnapshot snapshot, GraphOptions options,
                 CancellationToken cancellation);
NavigationResult sourceFor(ElementId id, SnapshotId expected);
List<ElementId> elementsAt(DocumentId id, TextPosition position,
                           SnapshotId expected);
```

Knoten: statische Tasks, Actions, sichtbare nicht expandierbare dynamische Codebereiche, Projektcontainer. Parameter und Cleanup erscheinen in separaten Bereichen/Listen und werden nicht zu normalen DAGtasks umgedeutet. Innere Invocations sind optional erklärende Kinder einer Action, nicht zusätzliche orchestrierte Tasks.

Kanten: `DEPENDS_ON`, `AFTER`, separat `RESULT_BINDING` mit konkreten Feldreferenzen. Aus einer `dependsOn`-Kante nicht automatisch eine Dateiverbindung ableiten. `result`-Nutzung ohne erlaubte Kette ist eine Diagnose, keine heimlich hinzuerfundene Dependency.

`GraphNode` besitzt ID, Symbol-/Sourceanker, Kind, Titel, OperationId, `EditCapabilities`, Diagnosesummary und optional referenzierte Felder. IDs sind pro Snapshot eindeutig. Für Viewpersistenz zusätzlich ein eindeutiger stabiler PresentationKey aus Projektidentität/qualifiziertem Namen/Kind; nach unklarer Renameidentität lieber Layout verlieren als fremden Knoten zuordnen.

Graphstatus `COMPLETE | PARTIAL | INVALID`; Views zusätzlich `EMPTY | LOADING | FRESH | STALE | ERROR`. „Statisch vollständig“ ist keine Garantie, dass Datenbankverbindung, Laufzeitparameter oder Dateidaten später gültig sind.

### 10.2 Modelerarchitektur

**`GretlModelerProvider implements vscode.CustomTextEditorProvider`**

- `resolveCustomTextEditor(document, panel, token)` verbindet genau dieses TextDocument mit der Ansicht.
- `priority: option`: normale Textansicht nicht ungefragt ersetzen. „GRETL: Modeler öffnen“ daneben anbieten.
- Bei Projektübersicht explizit Root/Teilprojekt auswählen; keine stille Bindung an die zufällig aktive Datei.

**`ModelerPanelController`** im Extensionhost

- `initialize()` lädt ausschliesslich lokale Assets, CSP, Panel-ID und Handshake.
- `refresh(reason, expectedVersion)` fragt vorhandenen LSP, nicht CLI oder anderen Server.
- `acceptGraph(response, requestSequence)` prüft Session, Requestgeneration und Sourcevektor.
- `handleIntent(message)` validiert schema/IDs/Grösse; delegiert Planen/Anwenden an ProtocolClient/EditPlanApplier.
- `revealSource(elementId)` benutzt serverseitigen Anker.
- `dispose()` schliesst Listener/Workeranforderungen; späte Antworten dürfen kein Panel wiedereröffnen.

**`ModelerSession`**, neutral im Frontend

- `acceptSnapshot(graph)` hält fachlichen Graph unverändert.
- `invalidate(reason)` markiert stale und sperrt semantische Aktionen.
- `setSelection`, `setViewFilter`, `updateLayout` ändern nur ViewState.
- Mehrere Panels teilen gegebenenfalls Serveranalyse, nicht ihre Viewport-/Draftzustände.

**`GraphViewAdapter`**

- `toFlowNodes(graph, viewState)` / `toFlowEdges(...)` bilden DTOs auf React-Flow-Daten ab.
- Fachliche Edgearten bleiben unterscheidbar durch Strichart/Label, nicht nur Farbe.
- Keine GRETL-Analyse aus Labels oder aus gezeichneten Ports.

**`LayoutService`**

- `layout(graph, constraints, signal)` läuft in lokalem ELKworker.
- `resetLayout()` ist ausdrückliche Neuanordnung; gewöhnliche Textänderung erhält eindeutig zuordenbare Positionen.
- Grenzen für Graphgrösse, parallele Jobs und veraltete Workerantworten; keine Blockierung des Extensionhosts.

**`ViewStateStore`**

- `load(key)`, `save(key, state)`, `reconcile(previous, nextGraph)`;
- Ablage in VS-Code-workspaceState, Schema-/Projekt-/Dokumentschlüssel und Grössenlimit.
- Kein `.groovy`-Layoutkommentar, keine fachliche Sidecardatei. Optionaler späterer gemeinsamer Layoutsidecar ist nicht nötig.

### 10.3 Formulare und echte Erstellung

**`OperationPalette`** zeigt alle Katalogoperationen mit Suche/Kategorie/Beschreibung und konkretem Datenverfügbarkeitsstatus. Keine hardcodierte Vier-Task-Auswahl als fertige Palette.

**`OperationForm`**

- `createDraft(operation, node?)` baut einen lokalen Entwurf aus strukturierten Werten auf.
- `renderField(InputDescriptor, DraftValue)` unterstützt Text/Boolean/Integer/Enum/Path/List/Map und belegte Objektformen.
- `validateDraft()` ist UIhilfe; die verbindliche Entscheidung trifft der Core.
- `commitDraft()` sendet einen getypten Intent, nicht erzeugten Groovytext.
- Parameter-/Result-/Datei-/WorkPath-/Secretaliasauswahl sind explizite Wertmodi.

Geheime Werte weder ausführen/auflösen noch in generierten Katalogen oder Layoutstate speichern. Secretaliase können dargestellt werden; tatsächliche Werte werden nicht automatisch gelesen. Freier bereits vorhandener Quelltext wird nicht als sicher geheimnisfrei angenommen; keine pauschalen Source-Dumps in Logs.

**`ConnectionController`**

- `requestDependencyConnect(producerId, consumerId, kind)`;
- `requestResultBinding(outputPort, inputField)`;
- `requestDisconnect(edgeId)`.

Alle fachlichen Prüfungen serverseitig; lokale UI darf offensichtliche inkomplette Gesten abbrechen, aber keine eigene Autoritätsregel pflegen. Bei Ablehnung bleiben Quelltext und Graph unverändert, Meldung nennt Grund und betroffene Stelle.

**`ModelerCommandController`** bietet Task hinzufügen/umbenennen/löschen, Parameter anlegen, Defaults setzen, Feld bearbeiten, Verbindung hinzufügen/entfernen und „Im Quelltext bearbeiten“. Action-/dynamische Bereiche haben sichtbare Einschränkungen. Nicht verfügbare Aktionen sind mit Grund deaktiviert statt scheinbar klickbarer Stubs.

### 10.4 Undo, Save und Roundtrip

Fachliche Änderungen gehen in den nativen VS-Code-Textundo-Stack. Eine Benutzerbestätigung entspricht einem zusammengehörigen Textedit. Undo/Redo wird aus Webviewtastaturereignissen kontrolliert an den richtigen Editor weitergegeben und mit realen GUItests geprüft.

Knotenbewegung/Pan/Zoom verändern nur ViewState. Eine Dragbewegung ist höchstens ein View-Historyeintrag; keine Textänderung, keine Taskumordnung und kein Dirtymarker. Beide Undoarten klar unterscheiden, keine irreführende gemeinsame History behaupten.

Nach jeder Textänderung, einschliesslich Undo, Save/Revert und externer Änderung, folgt derselbe Analyse-/Projektionspfad. Die UI ersetzt nicht ihren Graph durch einen vermeintlich bereits bestätigten lokalen Zustand. Save schreibt den normalen Jobtext, nicht eine Diagrammdatei.

P7 muss einen Job **wirklich grafisch erzeugen**: Parameter anlegen, HttpDownload konfigurieren, Extract hinzufügen, Ergebnisbindung mit passender Dependency, Defaultziel setzen, speichern, erneut öffnen. Derselbe Job wird als normale Runtime-Scriptdatei in einem separaten Fixturetest ausgeführt. Ein Screenshot bestehender Knoten erfüllt diesen Auftrag nicht.

### 10.5 UIqualität und Grenzen

Lokale Assets, CSP ohne frei ausführbaren Workspaceinhalt, kleine `localResourceRoots`, erlaubte Nachrichtentypen und Payloadgrössen. Kein arbitrary `executeCommand`, Pfad, URI oder Shellbefehl aus dem Webview. Beschreibungen als Text/sanitized Markdown, keine Commandlinks aus Kommentaren.

Pan/Zoom/Fit, Node-Drag, Selektion, Tastaturfokus, Light/Dark/HighContrast, verständliche Empty/Partial/Stale-Zustände und abgeschnittene lange Namen vermeiden. Einfache Filter nach Projekt/Taskkind und Diagnosen gehören dazu. SVG/PNG-Export, manuelles Kantenrouting, Workflowausführung und Debugger sind bewusst **nicht** Pflicht dieses Auftrags.

## 11. Leistungs- und Ressourcengrenzen

P0 legt reproduzierbare Fixturegrössen und Messbedingungen fest; keine erfundenen Benchmarks. Verbindliche Prüfkategorien: kleiner Job (ca. 20 Tasks), mittleres Projekt (ca. 250 Tasks über mehrere Dateien), grosser statischer Graph (ca. 1000 Tasks) und beschädigte/dynamische Quellen.

Begrenzte Dateigrössen, Katalog-/Messagegrössen, Projektdateizahl, Rekursion, Workqueue, Snapshots und Viewhistory. Konfigurierbare Grenzen mit dokumentierten Defaults; ein überschrittenes Budget ergibt klare Teil-/Limitdiagnose und nicht scheinbar leeren Erfolg. Performanceziele nach erster Baseline festhalten und später nicht nur zum Bestehen erhöhen.

Stale-Ergebnisse dürfen nicht weiterwirken; Speicher muss nach Close/Rootentfernung/Restart begrenzt zurückgehen. Keine Forderung nach vollständiger inkrementeller Groovykompilierung. Richtiges Caching anhand der tatsächlich berührten Quellen und koaleszierte Analyse reichen für die erste Version.

## 12. Abschlussinvarianten

- Kein anderer GRETL-Stack in Build-/Test-/Runtimeabhängigkeiten des neuen Produkts.
- Keine Jobausführung beim Analysieren oder Modellieren.
- Ein statischer Java-Sprachkern für LSP und Modeler, eine Operationsmetadatenquelle.
- Pure Runtime-/Editor-Namens- und Graphregeln stimmen nachweislich überein.
- Verlustarme aktuelle Sourceedits; unbekannter Code bleibt erhalten.
- Reale installierte VS-Code-GUI und echter stdio-Prozess sind getestet.
- Ein erstellter Job bleibt ein normaler lauffähiger Runtime-Groovyjob.
- Jede Phase und zuletzt der gesamte neue Umfang werden unabhängig geprüft.
