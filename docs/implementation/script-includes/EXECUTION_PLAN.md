# GRETL Runtime: Scriptfragmente mit `apply from:`

**Verbindliche Spezifikation und Ausführungsplan für Codex**  
**Fassung:** 1.3 · 4. November 2026 · aktualisiert nach vollständiger Tooling-Abnahme und Merge in `main`  
**Repository:** `edigonzales/gretl-runtime`  
**Featurebranch:** vorzugsweise `codex/script-includes`  
**Arbeitsbasis:** integriertes `main`, Recherchepin `0bfc8f0fccf43989d751d4f5490202311d200628`; Basisstrategie siehe Abschnitt 4.1  
**Unabhängige Abnahme:** OpenCode **2.x** mit **DeepSeek-V4.1-Flash-1017**  
**Status:** Implementierungsauftrag; die hier verlangte Implementierung und ihre Produkttests wurden mit Erstellung dieser Spezifikation noch nicht ausgeführt.

> „Schritt 2“ bezeichnet in diesem Auftrag das besprochene Einbinden weiterer Groovy-Dateien mit GRETL-Tasks in denselben Job-/Subprojekt-Namensraum. Es bezeichnet weder eine Phase des LSP-Auftrags noch den Dateninfrastruktur-Prototyp. Bestehende Subprojekte bleiben unverändert.

## 1. Auftrag und Rangfolge

Implementiere `apply from: 'tasks/import.groovy'` als bewusst begrenzte, dokumentierte GRETL-DSL. Das Feature muss im normalen SCRIPT-Betrieb, innerhalb von Subprojekten und in vorkompilierten Job-JARs funktionieren. Es erweitert die Definition eines bestehenden Jobs; es startet keinen zweiten Run und erzeugt keinen neuen Subprojektnamensraum.

Arbeite **P0 → P1 → P2 → P3 → P4**. Eine Phase endet erst nach erfolgreichen notwendigen Tests, einem lokalen Kandidatencommit und einer **unabhängigen Abnahme genau dieses SHA**. Erst danach beginnt die nächste Phase. Der zweite Agent überprüft Code **und Zielerreichung**, führt selbst Tests aus und entwickelt eine eigene Gegenprobe. Codex organisiert das ohne manuelles Promptkopieren oder Freigabeklicks des Nutzers.

Diese drei Auftragsdateien bilden den aktiven Auftrag unter `docs/implementation/script-includes/`. Der Plan ist der fachliche Vertrag; `CODEX_STARTPROMPT.md` steuert den Einstieg; `OPENCODE_REVIEW_PROMPT.md` steuert den zweiten Agenten. `BRANCH_REASSESSMENT.md` ergänzt die geprüfte Ausgangslage, ohne weitere versteckte Aufträge zu erzeugen. Fassung 1.3 ersetzt 1.0/1.1/1.2 für zukünftige Arbeit; historische Reviewberichte nicht rückwirkend umschreiben. Die hochgeladenen INTERLIS-Beispiele dienen nur als **Verfahrensvorlage**. Ihre Compiler-, Python-Ablösungs-, Norm-, Browser- und Conformance-Aufträge werden **nicht** übernommen.

**Ausdrückliche Testausnahme für diesen Nutzerauftrag:** Das gelesene Root-`AGENTS.md` verlangt zwei vollständige Reactor-Gates und die allgemeine Dokumentation eine breite Modus-/Dienstqualifikation. Der aktuelle ausdrückliche Nutzerauftrag verlangt dagegen notwendige, sinnvolle, risikobasierte Prüfungen. Für die **lokalen Phasengates dieses Features** gilt deshalb Abschnitt 8. Keine pauschalen vollständigen Builds, keine gesamte 44-Operationen-Matrix und keine Datenbank-/FTP-/S3-Suite nach jeder Phase. Diese Ausnahme einmal in P0 dokumentieren. Bestehende `AGENTS.md`, CI-, Release- und Publikationsgates nicht ändern, deaktivieren oder als bestanden ausgeben. Eine Featureabnahme ist keine Releasefreigabe.

Nicht beauftragt: neues Buildsystem, Gradle-Abhängigkeit, allgemeines Plugin-System, Remote-Includes, vollständige Gradle-Kompatibilität, Parameterübergabe an Fragmente, Variablenexport, vollständige Includeauflösung/Navigation/Rename im LSP oder Modeler, Umstrukturierung des bereits integrierten Toolings, Native Image, Scheduler, Runtime-Parallelisierung oder ein allgemeines Reviewframework. `finalizedBy`, `shouldRunAfter` und andere neue Ablaufmechanismen sind ausdrücklich vertagt. Notwendige kleine Kompatibilitätstests und reproduzierte Guardkorrekturen nach Abschnitt 4 sind dagegen beauftragt. Keine produktiven Jobs ausführen; kein Push, Release oder Merge in `main`/den Toolingbranch.

## 2. Verifizierter Ausgangspunkt und Entscheidungsgrundlage

**Erneut untersuchter Mainstand:** `0bfc8f0fccf43989d751d4f5490202311d200628`, Abruf am **4. November 2026**. Der Commit ist der Merge von Pull Request #2 („Add GRETL language server and graphical modeler“). Der veröffentlichte Toolingzweig endet beim Abschluss-/Archivcommit `c0b65d75fead9e3e017665e01b3b5a69e62f64f9`; dieser Zweig ist keine separate noch zu integrierende Featurevoraussetzung mehr [R23].

Die in Main vorhandene Statusdatei weist **P0–P8 als unabhängig abgenommen** aus. `reviews/P8/attempt-1/validation.json` nennt `PASS` für den Produktkandidaten **`54bb123070fc7bd81aea58b4e1191beecf568cbf`**. Dieser Produkt-SHA, der spätere Archiv-SHA und der Main-Merge-SHA haben verschiedene Rollen; den archivierten Review nicht nachträglich auf den Merge-SHA umetikettieren. Die vorhandene Toolingabnahme belegt das ursprüngliche Tooling, **nicht** das noch zu implementierende Include-Feature [R12].

**Wichtiger Delta-Befund:** Die vollständigen nichtrekursiven Git-Root-Trees von `54bb123070fc7bd81aea58b4e1191beecf568cbf` (Recherchepin der Fassung 1.2) und `0bfc8f0fccf43989d751d4f5490202311d200628` haben ausser `docs/` dieselben Einträge mit denselben Modes/Typen/Object-IDs. Damit sind insbesondere alle Runtime-/Toolingmodule, Tests ausserhalb des Dokumentationsarchivs, Builddateien, Extension und Reviewwerkzeuge inhaltlich unverändert. Die umfangreiche, begrenzte Compare-Dateiliste wurde ausdrücklich **nicht** als vollständiger Beweis für diese Aussage benutzt; massgeblich sind die ungekürzten Root-Tree-Einträge [R24].

**Konsequenz:** Runtime-Semantik S01–S19, technische Architektur und die gezielten Kompatibilitätsprüfungen bleiben bestehen. Wegfallen die provisorische Branchbasis, das Warten auf P8 und eine obligatorische spätere Tooling-Mergeprobe. Alle Arbeiten starten nun auf dem integrierten Mainstand. Dies ist eine Fortschreibung der Spezifikation, keine neue Produktabnahme.

Für dieses Dokumentupdate wurden Main-/Merge-/Tree- und Abschlussquellen geprüft und die Spezifikationsdateien verglichen. **Keine neuen Produkt-, Merge-, LSP- oder GUI-Tests wurden ausgeführt.** Die Einzelbefunde aus v1.2 bleiben aufgrund der identischen Produktbäume anwendbar; ein Codebefund ohne ausgeführte Gegenprobe bleibt ausdrücklich ein Codebefund. [BRANCH_REASSESSMENT.md](BRANCH_REASSESSMENT.md) dokumentiert diesen Abgleich. Neue lokale/Remotecommits beim tatsächlichen Arbeitsbeginn prüfen; kein Reset auf einen Recherchepin.

| Bereich | Vorhandene Klassen / Dateien | Konsequenz |
|---|---|---|
| Runtime-DSL | `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/GretlScript.java`, `JobDslModel`, `GroovyJobLoader`, `DefinitionEnvironment`, `CompilerSettings` | `apply` additiv anschliessen; bestehende Taskregistrierung, Bindings und verzögerte Konfiguration erhalten. |
| Gemeinsame Regeln | `gretl-language-contract`, `DslVocabulary`, `ProjectNameRules`, `PlanSemantics` | Bereits vorhanden; nicht erneut extrahieren oder durch alte lokale Implementierungen ersetzen. |
| Projekt / Quellen | `project/GretlProjectParser`, `ProjectPath`, `ProjectTaskIndex`, `CompiledProjectScript`, `ProjectSourceNames`, `ProjectSourceHash`, `SourceLocations` | Fragmente sind keine zusätzlichen Projekte. Source-Identität und besitzenden Scope trennen. |
| Paket / Jobstart | `JobCompiler`, `ProjectCompiler`, `CompiledProject`, `JobPackageBuilder`, `PackageDescriptor`; `JobJarMain`, `ScriptJobSource`, `ProjectJobSource`, `JobRuntime` | Exakte erlaubte Scriptmenge, kompatible Altformate, gemeinsamer Source-Snapshot und vorkompilierte Fragmente. |
| Statische Analyse | `GretlAstExtractor`, `GretlDocumentParser`, `ProjectResolver`, `ProjectAnalyzer` | `apply` noch nicht transitiv aufgelöst; vorhandener Opaque-/Partial-Vertrag ist der Übergang. |
| Graph / Edits | `JobGraphProjector`, `GraphFormProjector`, `EditabilityAnalyzer`, `SemanticEditPlanner`, `HeaderSourceEdits`, `EditVerifier` | Schreibender Modeler ist vorhanden. Nicht nur Anzeige, sondern alle relevanten Editzugänge prüfen. |
| LSP | `GretlLanguageServerApi`, `GretlProtocol`, `gretl-lsp` | Reale `gretl/graph`, `gretl/planEdit`, `gretl/validateEdit`-Prozessprüfungen sind jetzt möglich und fällig. |
| VS Code | `vscode/gretl-vscode/src/modeler/provider.ts`, `edit-plan.ts`, `src/edit-guard.ts` | Existierenden CustomTextEditor, Sourcevektor-Guards, native Edits und Undo beibehalten. |
| Review | `tools/gretl-tooling/review.mjs` | Mechanik vorhanden, aber Auftrag/81 Kriterien/Vollgates fest verdrahtet. Nicht unverändert als Featureorchestrator verwenden. |

Die aktuelle Modulgrenze bleibt verbindlich: Der statische Tooling-Core und der LSP bekommen **keine Produktionsabhängigkeit auf Runner, Groovy-Runtimeadapter, Taskhandler oder Packager**. `gretl-language-contract` bleibt JDK-only. Testseitige Parität mit der Runtime ist erlaubt. Keine produktive Runtimeabhängigkeit auf `gretl-tooling-core` ergänzen [R17].

Bestehende Paket-Deskriptoren 1/2/3, interne `job.json`-Versionen und **LSP-Protokollversion 1** sind verschiedene Verträge. Eine neue interne Bundleversion darf nicht nebenbei den LSP-Vertrag oder das Operationskatalogschema umnummerieren. Die konkreten aktuellen Metadatenparser vor Implementierung erneut lesen; Abschnitt 5.5 regelt die rückwärtskompatible Erweiterung.

## 3. Öffentlicher Featurevertrag

### 3.1 Zielbeispiel

```text
my-job/
  job.groovy
  tasks/
    prepare.groovy
    verify.groovy
  data/
    input.txt
  job-package.json
```

```groovy
// job.groovy
apply from: 'tasks/prepare.groovy'
apply from: 'tasks/verify.groovy'
defaults 'verify'
```

```groovy
// tasks/prepare.groovy
task('prepare', Gzip) {
    dataFile = file('data/input.txt')
    gzipFile = workPath('input.txt.gz')
}
```

```groovy
// tasks/verify.groovy
action('verify', dependsOn: 'prepare') {
    def archive = result('prepare').file
    if (java.nio.file.Files.size(archive) == 0L) {
        throw new IllegalStateException('Leeres Archiv')
    }
}
```

Die Felder `dataFile`/`gzipFile` und das Ergebnisfeld `.file` entsprechen dem vorhandenen Gzip-/Projektpipelinebeispiel am Recherche-SHA. Der spätere Integrationstest soll zusätzlich den tatsächlichen Dateiinhalt nach Dekompression prüfen; eine nichtleere Datei allein ist dort noch kein ausreichendes fachliches Orakel.

Der Aufruf bleibt `gretl run my-job/job.groovy`. In einem Projekt bleiben die bekannten Aufrufe mit Projektverzeichnis und qualifizierten Zielen bestehen. Es gibt keinen neuen Pflichtschalter.

### 3.2 Syntax und bewusste Grenzen

**S01:** Unterstützt werden genau die semantisch gleichen Formen:

```groovy
apply from: 'tasks/import.groovy'
apply(from: "tasks/import.groovy")
```

Ein direkter Ausdruck auf oberster Scriptebene, impliziter Scriptreceiver, genau ein Map-Eintrag `from`, ein nichtleerer konstanter einfach oder doppelt zitierter String. Kommentare, Einrückung, CRLF und normale zulässige Groovy-Escapes korrekt über Parser/Tokens behandeln. Keine Regex-Quelltextersetzung.

**S02:** Für das neue GRETL-`apply` nicht unterstützt: GStrings, Stringverkettung, Variablen, `file(...)` als Argument, URLs, Globs, Listen, optionale Includes, weitere Map-Schlüssel, Closures sowie Aufrufe innerhalb von `if`, Schleifen, Methoden, Klassen, Task-/Action-/Cleanup-Closures. Ebenfalls kein `apply plugin:`. Solche eindeutig als GRETL-`apply` geschriebenen Formen erhalten eine verständliche Definitionserrordiagnose; sie dürfen nicht im SCRIPT-Weg zufällig funktionieren und erst beim Paketieren scheitern.

Ein fremder Receiver wie `someObject.apply(...)` ist kein GRETL-Include und wird nicht als solches umgedeutet. Lokale Überschattung des neuen Script-DSL-Namens `apply` durch eigene Scriptmethoden/-variablen muss für include-verwendende Jobs ausdrücklich erkannt/abgewiesen werden. Keine globale Verbotsregel für gleichnamige Methoden beliebiger Hilfsklassen. Reflektion und beliebige Groovy-Metaprogrammierung sind kein unterstützter Includeweg und keine zu schaffende Sandbox.

**S03:** Nur lokale reguläre `.groovy`-Dateien. Der gesamte statisch deklarierte Includeabschluss wird **vor der ersten Scriptregistrierung** gelesen und validiert. Der Parser führt weder Top-Level-Code noch Annotationstransformationen aus. Ein Include hinter einem vorzeitigen `return` gehört trotzdem zur statisch deklarierten Quellmenge; seine Registrierung erfolgt aber nur, falls die Ausführung die Anweisung erreicht. Das Feature macht aus beliebigem Top-Level-Groovy keinen vollständig statisch berechenbaren Jobgraphen.

### 3.3 Namensraum und Lebenszyklus

**S04:** Ein Fragment gehört zum selben Definitionsscope wie sein aufrufender Einstieg. Im Einzeldateimodus bleibt `prepare` unqualifiziert; innerhalb `:import` wird derselbe Name `:import:prepare`. Fragmente werden **nicht** als zusätzliche `ProjectEntry`-Subprojekte modelliert.

**S05:** Ein gemeinsamer `JobDefinitionBuilder`, ein finales Freeze, ein Runner, ein Runbericht und die bestehende Run-/Result-/Workspace-/Cleanup-Lebensdauer. `apply` ist keine Taskausführung. Die Registrierung des Fragments geschieht an der `apply`-Stelle, depth-first und in Quellreihenfolge. Bestehende Planungsregeln bestimmen anschliessend die Ausführung; Includes erzeugen keine `dependsOn`-Kanten.

**S06:** Tasks, Actions, Parameter und Cleanup dürfen in Fragmenten deklariert werden. Namensbasierte `dependsOn`, `after`, `result`, `resultOptional` und Parameterzugriffe funktionieren über Dateigrenzen **im selben Scope** und über bereits unterstützte qualifizierte Projektverweise. Vorwärtsreferenzen dürfen nicht von der zufälligen Reihenfolge der geladenen Dateien abhängen. Doppelte Task-/Parameter-/Cleanup-Namen werden nicht überschrieben; bestehende Kollisionsregeln bleiben erhalten und nennen die beteiligten Quellen soweit verfügbar.

**S07:** Jede Scriptdatei bekommt eine eigene Scriptinstanz und ein eigenes Binding. Imports, lokale `def`-Variablen und Bindingzuweisungen werden **nicht** automatisch exportiert. GRETL-Deklarationen teilen den Scope, Groovy-Lokalzustand nicht. Keine globale mutable Bindingmap und kein verstecktes `ext`-System. Die zugesicherte Isolation betrifft Scriptinstanzen, Bindings und GRETL-verwalteten Zustand, nicht beliebige vom Jobautor selbst angelegte statische Java-/Groovy-Felder im gemeinsamen Classloader. Eigene Imports im Fragment sind erlaubt. Gemeinsame Laufzeitwerte laufen über vorhandene Parameter-/Konfigurationsverträge; TaskRef-Lokalvariablen eines anderen Scripts sind nicht sichtbar.

**S08:** `defaults` bleibt beim Einstieg bzw. bei `settings.gretl`. In einer als Fragment geladenen Datei ist eine GRETL-`defaults`-Deklaration ein Fehler `APPLY_DEFAULTS_NOT_ALLOWED`, statt Ziele abhängig von Einbindereihenfolge zu überschreiben. Der bisherige Umgang mit `defaults` in echten Subprojekt-`job.groovy` bleibt unverändert.

**S09:** Dieselbe normalisierte Datei zweimal innerhalb **desselben Definitionsscopes** einzubinden ist ein Fehler, auch über zwei unterschiedliche Includezweige. Kein stilles „include once“. Die Fehlermeldung enthält beide Einbindeketten. Zyklen werden separat mit Kette erkannt, bevor registriert wird. Dieselbe physische Fragmentquelle darf von **unterschiedlichen Subprojekten** benutzt werden; sie wird dann mit getrenntem Binding/Zustand je Scope registriert. Ein echter Projekteinstieg darf nicht zusätzlich als Fragment eines anderen Scopes importiert werden.

### 3.4 Pfade: zwei verschiedene Basen ausdrücklich erhalten

**S10:** `apply from:` ist relativ zur **Quelldatei, welche die Anweisung enthält**. `tasks/import.groovy` mit `apply from: 'common.groovy'` bindet `tasks/common.groovy` ein.

**S11:** `file('...')` und `ctx.file(...)` behalten dagegen die bisherige **Job-/Subprojekt-Ressourcenbasis**. Im obigen Fragment verweist `file('data/input.txt')` auf `my-job/data/input.txt`, nicht auf `my-job/tasks/data/input.txt`. Das gilt gleichermassen in Taskkonfiguration, Action, `when`, innerer Invocation und Cleanup. `path`-Parameter bleiben Start-CWD-bezogen; `workPath` bleibt scopebezogener temporärer Pfad. Kein neuer Helper für fragmentrelative Datendateien in diesem Auftrag.

**S12:** Zulässige Definitionroot ist im Einzeldateimodus das Elternverzeichnis des Einstiegs; im Projektmodus der bisherige Projektroot. `..` im Include darf sich innerhalb dieser Root normalisieren, etwa `import/job.groovy → ../shared/tasks.groovy`. Ein Ausbruch ist verboten. Absolute Pfade, URI-/UNC-/Laufwerkspfade, Backslash-Pfadnotation und NUL/Kontrollzeichen werden abgewiesen. Pfade mit Leerzeichen oder Umlauten funktionieren.

Symlinks in neu aufgelösten Include-Pfadkomponenten, nichtreguläre Dateien, fehlende Dateien und echte Root-Ausbrüche ablehnen. Existierende Eingangspfadverträge von Jobs **ohne** Includes nicht nebenbei verschärfen. Realpath-/Containment-Checks vor bounded Read; keine Zugriffe ausserhalb der erlaubten Definitionroot. Dies schützt die Includeauflösung, nicht beliebigen vertrauenswürdigen Jobcode.

### 3.5 Quellen, Fehler und Reproduzierbarkeit

**S13:** Kompilier-/Registrierungs-/Taskkonfigurations-/Action-/Cleanupfehler zeigen die **wirkliche Fragmentdatei und Zeile**, nicht pauschal den Einstieg. Include-Fehler zeigen die Anweisungsposition und gegebenenfalls die Include-Kette. Gleiche Basenames in verschiedenen Verzeichnissen müssen getrennt bleiben. Vorhandene DE-/EN-Meldungssystematik und strukturierte Codes weiterverwenden.

**S14:** Ein unveränderlicher Source-Snapshot liefert dieselben Bytes für Analyse des Includeabschlusses, Kompilierung und Quellhash. Nicht für Hash und Loader unabhängig Dateien erneut lesen. Eine Dateiänderung während eines späteren Laufs ergibt einen neuen Snapshot; keine stale globale Cacheübernahme. Keine Zusage eines atomaren Filesystemsnapshots über mehrere Dateien, wohl aber intern konsistente Verwendung der einmal erfassten Bytes.

**S15:** Der Quellenhash von include-verwendenden Definitionen umfasst sämtliche Einstieg-/Fragmentbytes, kanonische rootrelative Quellnamen, geordnete Einstieg-/Scopezuordnung und im Projektmodus die Settings. Ein geändertes verschachteltes Fragment muss den Hash ändern. Gleiches Projekt unter anderem absoluten Checkoutpfad oder Start-CWD behält den Hash. Dateinamen und Bytes längengeframed/versioniert hashen, nicht mehrdeutig konkatenieren. Keine erfundene Einbeziehung von Tagesdaten oder sämtlichen Dependencies. Für Jobs **ohne** Includes bleiben bestehende Hashalgorithmen/Metadaten unverändert.

### 3.6 Auslieferungsparität

**S16:** `run`, `check`, `plan` und Jobhilfe laden denselben Includeabschluss. `check`, `plan` und Hilfe führen keine Taskkonfiguration, Tasks oder Cleanups aus; vertrauenswürdiger Script-Toplevel kann wie bisher beim Definitionsladen laufen. Ein eigener rein statischer Include-Reader darf dagegen **niemals** ausführen. Diese beiden Aussagen in Dokumentation und Tests nicht vermischen.

**S17:** Paketieren ermittelt und kompiliert den vollständigen lokalen Includeabschluss, ohne Scriptbodies oder Tasks zu starten. Zur Laufzeit instanziiert das JAR ausschliesslich enthaltene kompilierte Klassen; es liest/kompiliert keine `.groovy`-Dateien vom Benutzerdateisystem nach. JAR-Ausführung aus fremdem CWD nach Entfernen des ursprünglichen Quellbaums muss funktionieren. Ressourcen bleiben ausdrücklich im Deskriptor ausgewählt; Includecode automatisch aufnehmen bedeutet nicht beliebige Datendateien automatisch aufzunehmen.

**S18:** Deskriptorschema 1/2/3 bleibt unverändert bedienbar. `sources` bleibt für Helperquellen; nicht referenzierte zusätzliche Scripts bleiben Fehler. Fragmentklassen nicht mittels pauschalem Entfernen von `PACKAGE_MULTIPLE_SCRIPTS` akzeptieren. Alte paketierte Formate bleiben lesbar; neue include-verwendende Pakete bekommen eine ausdrücklich versionierte interne Darstellung. Keine Änderung am Dependencyinventarschema allein wegen Includes.

**S19:** IMAGE verwendet weiterhin das generische Runtime-Image mit externem SCRIPT/Projekt. Kein neues Job-JAR im Image, kein neues Featureimage und kein anderer Taskausführer.

## 4. Arbeitsbasis und sichere Koexistenz mit dem fertigen Tooling

### 4.1 Verbindliche Basis: integriertes Main

**Neuer Auftrag:** Einen eigenen Branch `codex/script-includes` in einem eigenen Worktree vom aktuellen geeigneten, committierten Mainstand erstellen. Der überprüfte Mainpin ist `0bfc8f0fccf43989d751d4f5490202311d200628`. Neuere Maincommits dürfen verwendet werden, nachdem ihr relevanter Delta gelesen wurde. Nicht von dem früheren Main vor der Toolingintegration oder von einem beliebigen älteren Toolingzwischenstand starten. Der Merge und der Toolingabschluss sind bereits erfolgt; Codex soll weder P8 fortsetzen noch auf eine weitere Toolingfreigabe warten.

In P0 die folgenden Rollen in der kurzen `WORK_QUEUE.md` und im Reviewhandoff unterscheiden:

| Feld / Rolle | Bedeutung |
|---|---|
| `assignment_base_sha` | Tatsächlicher sauberer Main-Ausgangspunkt dieses Featureauftrags; bei neuem Start der oben genannte Merge oder ein geprüfter Nachfolger. |
| `tooling_base_sha` | Bereits unabhängig abgenommener Tooling-Produktkandidat; bei diesem Recherchestand `54bb123070fc7bd81aea58b4e1191beecf568cbf`. Das ist kein neu zu erzeugender Feature-PASS. |
| `base_status` | `MAIN_WITH_ACCEPTED_TOOLING`, nachdem die enthaltene Abstammung tatsächlich geprüft wurde. |
| `phase_base_sha` / `head_sha` | Wie bisher: konstanter Ausgangspunkt der jeweiligen Phase und der genau zu prüfende Featurekandidat. |

Per `git merge-base --is-ancestor` oder einer gleichwertigen belegten Gitprüfung feststellen, dass der integrierte Main-/Toolingstand im gewählten Featurekandidaten enthalten ist. Fehlende Objekte in einem flachen Checkout durch einen zulässigen Lese-Fetch beschaffen, nicht als fehlende Toolingimplementierung interpretieren. Branchname allein ist kein Identitätsnachweis. Den Mainpin nicht ungeprüft erzwingen, wenn Main inzwischen regulär weiterentwickelt wurde.

**Bereits begonnener Include-Featurebranch:** Bestehenden Code und gültige Teilbelege erhalten. Falls er noch nicht auf dem integrierten Main basiert, einmalig dessen tatsächlichen Integrationsbedarf bestimmen und Main lokal in den **eigenen Featurebranch** integrieren. Nur bei diesem realen Bedarf zuerst in einem temporären Worktree erproben. Kein Reset, kein History-Rewrite, kein Stash fremder Änderungen und keine pauschale `ours`-/`theirs`-Lösung. Featureänderungen und Upstreamintegration separat ausweisen; nach der Integration die betroffenen Verträge testen und den aktuellen Kandidaten unabhängig prüfen. Bereits gültige Teilabnahmen nicht allein wegen dieses Dokumentupdates verwerfen oder rückwirkend umschreiben.

**Keine künstliche Schlussintegration:** Ein neu von Main gestarteter Featurebranch benötigt in P4 keinen zweiten Merge mit `codex/gretl-tooling` und keine leere Mergeprobe. Änderungen der Basis während des Auftrags nur bei konkretem Bedarf integrieren; keine automatisch wandernde Arbeitsbasis. Die abschliessenden Kompatibilitätstests bleiben trotzdem erforderlich, weil `apply from:` neue Laufzeit- und Editorinteraktionen einführt.

Alle Schreibarbeiten bleiben im eigenen Feature-/Reviewarbeitsbereich. `main`, der frühere Toolingbranch, fremde Worktrees und historische Abschlussbelege werden nicht verändert. Es gibt keinen Publikationsauftrag.

### 4.2 Schreibgrenzen: klare Ownership, eng begrenzte Kompatibilitätskorrekturen

Unabhängig von der Basis immer geschützt:

- alle fremden Worktrees und deren uncommittierte Dateien, auch ein noch vorhandener früherer Toolingworktree;
- `docs/implementation/gretl-tooling/` einschliesslich Status, Anforderungen, Originalreviews und Evidenz;
- dessen historische Testzahlen, Kriterienregister und Freigaben;
- `tools/gretl-tooling/review.mjs` samt den bestehenden Tooling-Reviewschemas und Vollgateverträgen.

Primäre Featureorte bleiben neue Fragmentklassen/-tests im bestehenden `gretl-groovy`, kleine Loader-/DSL-/Sourceadapter, Packager und Job-Runtime sowie `docs/implementation/script-includes/` und ein eigenes Beispiel. Keine grossen Umbenennungen, Formatierungsläufe oder POM-Neuschreibungen. `GretlScript`-/`TaskConfigurationScope`-Delegationen an `DslVocabulary`, `ProjectPath` an `ProjectNameRules` und `ExecutionPlanner` an `PlanSemantics` bewahren. `apply` erzeugt keine neue Plan-Kantenart.

**Im eigenen Featurebranch in P4 ausdrücklich erlaubt:** neue kleine Include-Kompatibilitätstests in den bestehenden Core-/LSP-/Extension-Testharnesses. Zeigt ein solcher Test eine wirkliche unsichere Bearbeitung, ist eine **minimale, reproduzierbar begründete Guardkorrektur** im vorhandenen Core-/LSP-/Hostpfad beauftragt. Sie erfolgt ausschliesslich im Featurebranch, mit Red/Green, direkter Regression und unabhängigem Review. Erlaubt ist beispielsweise das Schliessen eines nachgewiesenen Coverage-Umwegs, nicht eine Neuentwicklung des Parsers oder Modelers.

**Nicht erlaubt:** vorsorglich alle Toolingdateien umbauen, unbekannte Includeinhalte als vollständig analysiert behandeln, eine allgemeine Fragment-Workspaceengine bauen oder `apply` bloss zur DSL-Menge hinzufügen und dadurch seinen Opaque-Schutz verlieren. Produktionsänderungen im Modelerfrontend nur, falls ein konkreter Clientfehler die vereinbarte sichere Koexistenz verhindert. Fachliche Regeln gehören weiterhin in Java, nicht als zweite Implementierung in TypeScript. Root-POM, npm-Abhängigkeiten, Protokoll und Katalog nur ändern, wenn ein konkret nachgewiesener Bedarf besteht; dieser Auftrag verlangt keine neuen Produktmodule.

Diese eng begrenzte Erlaubnis ersetzt das pauschale Tooling-Schreibverbot aus v1.1. Sie ist **kein Auftrag, die volle Include-Unterstützung im LSP nachzuliefern**.

### 4.3 Produktumfang: Runtime-Unterstützung und sichere Übergangsoberfläche

Der Lieferumfang dieses Auftrags ist:

1. vollständiger Runtime-/Paketvertrag S01–S19;
2. belegte Nichtausführung und verlustarme, ehrlich eingeschränkte Arbeit mit Include-Jobs im vorhandenen Editor/Modeler;
3. präziser Handoff für eine spätere vollständige statische Includeintegration.

Noch **nicht** geliefert werden transitive Fragmentauflösung im Tooling, vollständiger Graph über alle Fragmente, Include-Pfadcompletion, Navigation/Rename quer durch Fragmente, automatische Zuordnung eines allein geöffneten Shared-Fragments oder grafisches Verschieben von Tasks zwischen Dateien. Diese Grenze in Beispiel, README und Schlussübergabe benennen. Der Benutzer kann Includes im Text schreiben und mit der Runtime ausführen; der Modeler darf den bekannten lokalen Anteil anzeigen, aber keine Vollständigkeit vortäuschen.

Die Text-/Direktiven-Schnittstelle des Runtime-Include-Readers bleibt getrennt von Filesystemauflösung und Ausführung. Kein `GroovyJobLoader.load`, `Script.run`, `GroovyShell.evaluate`, `gretl check/plan/run`, Operationshandler oder Compiler-Plugin im LSP. Die aktuelle Ablage in `gretl-groovy` ist **keine Erlaubnis**, das gesamte Modul später in den LSP zu importieren. Bei einer späteren vollständigen Integration kleine neutrale Werte/Regeln gemeinsam verwenden oder durch den vorhandenen sicheren Toolingparser adaptieren; dafür jetzt kein neues allgemeines Sprachframework aufbauen.

### 4.4 Konkrete Anschlussstellen im integrierten Mainstand

Die folgenden Befunde wurden in v1.2 am Produktstand `54bb123070fc7bd81aea58b4e1191beecf568cbf` gelesen. Der Root-Tree-Abgleich bestätigt dieselben Produktdateien im neuen Mainpin `0bfc8f0fccf43989d751d4f5490202311d200628`. Es handelt sich weiterhin um **Codebefunde**, nicht um bereits ausgeführte Include-Kompatibilitätstests [R13–R24].

| Stelle | Tatsächlich gelesener Stand | Konsequenz für Codex |
|---|---|---|
| `GretlAstExtractor.extract(ModuleNode)` | Die DSL-Menge enthält noch kein `apply`; ein normaler Top-Level-Methodenaufruf fällt in `OPAQUE_CODE`, `mayRegisterDeclarations=true`. Deklarations-/Referenzcoverage wird partiell. | Diesen konservativen Pfad mit einem gezielten Apply-Fall beweisen. Nicht durch unvollständige neue Erkennung auf `COMPLETE` hochstufen. |
| `ProjectResolver.resolve(...)`, `JobSource(scope, documentId)` | Die Auflösung erfasst Einzeljob oder Settings-Entries und explizite SQLquellen; keinen transitiven Apply-Abschluss. | Keine gefundenen Fragmenttasks voraussetzen. Ein Sourcevektor erfasst nicht automatisch alle Runtime-Includes. |
| `JobGraphProjector.project(...)` | Opake deklarationswirksame Bereiche werden als `DYNAMIC_CODE` dargestellt; der Gesamtstatus verwendet die Vollständigkeitsflags. | Eine reine gültige Apply-Fixture ohne sonstige Fehler muss `PARTIAL` bleiben und eine Codeinsel erhalten. `INVALID` bei einem separaten echten Fehler bleibt zulässig. |
| `EditabilityAnalyzer.assess(...)` | Spezielle Rename-/Delete-/Dependency-/BindResult-Intents prüfen globale Coverage; lokale Felder werden getrennt beurteilt. | Bestehende Schutzregeln erhalten. Aus ihnen folgt noch kein pauschaler Nachweis für jeden generischen Setter. |
| `SemanticEditPlanner.planConfiguration(...)` und `HeaderSourceEdits.set(...)` | Formularänderungen werden in SetField-/SetTaskOption-Intents übersetzt; `dependsOn`/`after` sind auch über generische Optionssetter darstellbar. | Relevante Umwege separat prüfen. Codelektüre allein beweist weder eine Sicherheitslücke noch deren Abwesenheit im nachfolgenden Verifier. |
| `JobGraphProjector`-Optionsfähigkeit | Die allgemeine Optionsfähigkeit wird mit einer `description`-Änderung ermittelt. | Daraus darf ein Client nicht schliessen, dass jede Graphänderung zulässig sei. Entscheidend bleibt die serverseitige Prüfung des tatsächlichen Intents. |
| `GretlLanguageServerApi` / `GretlProtocol` | Reale Requests `gretl/graph`, `gretl/planEdit`, `gretl/validateEdit`, `gretl/navigation`; Kontext mit Snapshot-/Server-/Sourceidentitäten. Wireversion 1. | Kompatibilitätsfälle über den echten stdio-Server ausführen. Kein neues Parallelprotokoll für Includes. |
| `GretlModelerProvider`, `ModelerPanelController` | Bestehender CustomTextEditor; Reaktionen auf erfasste Quellen, Snapshots, Serverwechsel und native Editergebnisse. | Genau diesen Benutzerweg prüfen, nicht eine isolierte HTMLattrappe als GUI-Nachweis ausgeben. |
| `applyEditPlan(...)` | Planidentität, Guard, serverseitige Validierung, Konvertierung, erneuter Guard und native Anwendung sind getrennt. | Guards nicht umgehen. Ablehnungen müssen ohne Änderung des Texts bleiben. |
| `package.json` | Sprache `gretl-runtime`, Customeditor `gretlRuntime.modeler`, Kommando `gretlRuntime.openModeler`. Automatische Dateinamen: `job.groovy`, `settings.gretl`. | Tatsächliche IDs verwenden. Nicht die frühere Soll-ID `jobModeler` oder eine globale `*.groovy`-Sprachassoziation einführen. |
| `tools/gretl-tooling/review.mjs` | Alte Tooling-Spezpfade/Kriterien, beide Rootvollgates und GUIfallmengen sind fest verdrahtet. | Nur geeignete Mechanik wiederverwenden; nicht unverändert den falschen Auftrag/Tests erzwingen. Abschnitt 9.0 ist verbindlich. |

### 4.5 Konkrete Kompatibilitätsverträge C01–C08

Diese Kriterien ergänzen S01–S19, ohne daraus eine vollständige IDE-Includeimplementierung zu machen.

**C01 – Ehrliche partielle Analyse ohne Ausführung.** Die Quelle mit einem normalen literalen `apply from:` wird im bestehenden statischen Analysepfad nicht ausgeführt. Der bekannte lokale Anteil bleibt sichtbar, der unbekannte Anteil opak. Eine ansonsten gültige Fixture hat partielle Deklarations-/Referenzabdeckung und Graphstatus `PARTIAL`, nicht `COMPLETE`. Der Includeaufruf ist eine Codeinsel, kein neuer ausführbarer Task oder automatisch erzeugter Subprojektcontainer. Fehlende oder absichtlich beschädigte Fragmentinhalte werden vom noch nicht auflösenden LSP nicht als bereits validiert ausgegeben.

**C02 – Keine Umgehung der Referenz-/Graphschutzregeln.** Entscheidungen, die vollständige betroffene Deklarationen/Referenzen voraussetzen, dürfen bei unbekannten Includeinhalten nicht als sicher ausgegeben werden. Prüfe die tatsächlichen Endpunkte für Rename, DeleteTask, Add/RemoveDependency und BindResult. Prüfe zusätzlich die **semantisch gleichwirkenden** generischen/bündelnden Zugänge: `SetTaskOption` für `dependsOn`/`after`, entsprechende `planConfiguration`-Optionsänderungen und referenzbildende `SetField`-Varianten. Ein anderer Methodenname ist keine Ausnahme von derselben Sicherheitsregel. Nicht jeder beliebige Text-/Pfadwert ist eine Taskreferenz; gezielt nach Wirkung klassifizieren.

`SetDefaults` ebenfalls prüfen: bekannte Ziele und Zuständigkeit müssen korrekt sein, unbekannte vorhandene Ziele dürfen nicht beiläufig verschwinden oder als aufgelöst gelten. Nicht ohne Begründung jede lokale Defaultsänderung verbieten; eine Änderung ist nur erlaubt, wenn ihre erforderlichen Voraussetzungen tatsächlich bewiesen sind. Kann der derzeitige partielle Analyzer das nicht, klare Ablehnung statt vorgetäuschter Sicherheit. Alle Ablehnungen erfolgen serverseitig mit einem passenden strukturierten Grund und ohne WorkspaceEdit bzw. ohne native Textänderung. Ein generischer Optionsdialog darf die serverseitige Regel nicht umgehen.

**C03 – Sichere lokale Edits bleiben möglich und verlustarm.** Mindestens eine eindeutig lokale skalare Feldänderung in einem statisch bekannten Task, z. B. `overwrite = true` → `false`, funktioniert weiterhin. Keine pauschale Schreibsperre allein aufgrund `PARTIAL`, sofern das lokale Ziel und sein Feld hinreichend bestimmt sind. Includezeile, andere Quellbereiche, Kommentare, CRLF und Unicode bleiben unverändert; Fragmentdateien werden weder umgeschrieben noch in den Einstieg kopiert. Eine separat gespeicherte Layoutänderung verändert keinen Groovytext. Speichern und native Undo/Redo respektieren den bestehenden Textvertrag.

**C04 – Realer LSP- und Snapshotvertrag.** Der frisch gebaute stdio-Server liefert dieselbe partielle Aussage und dieselben Editentscheidungen wie der Core. `gretl/graph` → `gretl/planEdit` → `gretl/validateEdit` verwendet die wirklichen aktuellen Requestformen. Eine Änderung am geöffneten Einstieg bzw. seinem Include-Literal invalidiert den alten Editplan; eine verspätete Antwort darf keinen alten Text anwenden. Keine Job-/Taskausführung. Nicht bloss einen JSON-DTO-Mock testen.

**C05 – Kleiner installierter GUI-Nachweis.** Im tatsächlich gebauten und in einem isolierten VS-Code-Profil installierten VSIX: einen Einstieg mit Include öffnen, den partiellen Graph/Codebereich erkennen, eine sichere lokale Feldänderung durchführen, eine unsichere Referenz-/Graphänderung nachvollziehbar ablehnen lassen, speichern und Undo/Redo kontrollieren. Quelleninhalt und ausbleibende Ausführungsmarker nachprüfen. Ein zusammenhängender Ablauf mit wenigen Assertions genügt; **nicht** G01–G30 oder die gesamte Browsermatrix erneut starten. Bei technisch fehlender GUIausführung ist dieses Gate `NOT_EXECUTED`, nicht durch Node-Mocks ersetzt.

**C06 – Architekturen und Identitäten bleiben konsistent.** Keine Runtimeausführung im Tooling, keine verbotene transitive Produktionsabhängigkeit, keine zweite Namens-/Graphsemantik und kein neuer Apply-DAGkantentyp. Bestehende Editor-/Command-IDs, Source-/Snapshotguards sowie unabhängige Versionierung von LSP, Katalog und Jobmetadaten bleiben erhalten. Für notwendige kleine Guardfixes direkte Tests statt umfassender Neuarchitektur.

**C07 – Gemeinsamer geprüfter Stand und nachvollziehbare Basis.** Finale Featureabnahme benennt den tatsächlichen integrierten Main-Ausgangspunkt, den darin enthaltenen abgenommenen Tooling-Produkt-SHA, den Featurekandidaten und nur bei einer realen späteren Integration deren Commit. Die Produktartefakte stammen aus diesem Kandidaten. Fremde Arbeitsstände und die ursprüngliche Toolingabnahme bleiben unangetastet. Der vorhandene Main-Merge ersetzt keine Featuretests; ein früherer P8-PASS gilt nicht automatisch für nachträgliche Includeänderungen. Für einen direkt von Main gestarteten Auftrag ist keine zusätzliche Tooling-Mergeprobe erforderlich.

**C08 – Die verbliebenen Toolinggrenzen sind ausdrücklich dokumentiert.** Keine Vollgraph-/Include-Navigation-/Mehrdateien-Rename-Zusage. Eine allein geöffnete beliebige `.groovy`-Datei wird nicht heimlich einem erratenen Einbinder zugeordnet. Für Shared-Fragmente bis zur späteren kontextsensitiven Integration Textbearbeitung verwenden; die eigenständige Modelersicht beweist keine Analyse im Aufrufkontext. Der Handoff trennt physische Source-ID, besitzenden Job-/Subprojektscope und Ressourcenbasis.

### 4.6 Testdesign: Fehlgrün durch ungeeignete Fixtures verhindern

**Coverage-Fixture ohne zusätzlichen opaken Toplevelcode:** Nur `apply` soll die zusätzliche Unvollständigkeit verursachen. Beispiel, an den tatsächlichen Katalog anzupassen:

```groovy
// job.groovy
apply from: 'tasks/extra.groovy'
task('visible', Gzip) {
    dataFile = file('data/input.txt')
    gzipFile = workPath('input.txt.gz')
    overwrite = true
}
action('other') { }
defaults 'other'
```

```groovy
// tasks/extra.groovy
action('hidden', dependsOn: 'visible') { }
```

Kontrolle: dieselbe Einstiegsquelle **ohne** die Apply-Anweisung muss bezüglich des ansonsten geschlossenen Definitions-/Referenzgraphen vollständig analysierbar sein. Die sichere lokale Feldänderung betrifft `visible`. Für eine Coverage-Ablehnung mit zwei bekannten Tasks `visible` und `other` arbeiten, damit nicht schon ein unbekannter Zielname oder eine anderweitige Referenz die Probe erklärt. Für BindResult einen vorhandenen typisierten Testfall adaptieren, statt an Action-Ergebnissen ein erfundenes Schema anzunehmen.

**Nichtausführungs-Fixture getrennt:** Toplevel-/Task-/gegebenenfalls AST-Testmarker verwenden, aber deren eigene opake Konstrukte nicht als Beweis für die Apply-Coverage missbrauchen. Prüfen, dass Marker weder beim statischen Parsing noch beim Öffnen/Rendern/Editieren entstehen. Bei der späteren ausdrücklich ausgelösten Runtimeausführung ist vertrauenswürdiger Toplevelcode weiterhin erlaubt.

**Bytegenaue Editkontrolle:** Vorher-/Nachhertext mit genau dem erlaubten Replacement vergleichen, nicht nur prüfen, dass `apply` irgendwo noch vorkommt. Ein kleiner CRLF-/Unicodefall genügt. Originalfragmente separat hashen und unveränderte Bytes nachweisen. Kein vollständiger Syntaxvarianten-Kreuzprodukttest.

**Grenze der Sourceinvalidierung:** Der aktuelle Toolingresolver lädt die Fragmentclosure noch nicht. Deshalb nicht behaupten oder testen, dass jede Änderung an einer unerfassten Fragmentdatei bereits einen vollständig erfassten Includegraphen invalidiert. Der Graph bleibt unabhängig davon als partiell gekennzeichnet. P4 prüft die bestehenden Guards an tatsächlich erfassten Quellen, insbesondere dem Einstieg und dem Include-Literal. Vollständige Quelle→Einbinder-Invalidierung gehört zur späteren Integration.

### 4.7 Minimaler methodischer Zuschnitt für P4

Vor jeder Produktionsänderung neue passende Probe gegen den tatsächlichen Gesamtpfad ausführen. Besteht die Probe bereits, **nur den Regressionstest ergänzen**, nicht aus Prinzip Produktcode ändern.

- **`EditabilityAnalyzer.assess(...)`:** falls nötig dieselbe Coverage-Voraussetzung für gleichwirkende Intents zentral bestimmen. Kleine private Klassifikation wie `requiresClosedReferences(intent)` ist zulässig; keine zweite Editengine. Alle künftig darauf gestützten Intents im Test sichtbar nennen. Lokale Literale/Descriptions nicht unbegründet miterfassen.
- **`SemanticEditPlanner.planConfiguration(...)`:** bestehende pro-Intent-Prüfung und Postimageverifikation erhalten. Ein Batch mit einer unzulässigen Änderung erzeugt keinen teilweise anwendbaren Plan. Leere oder nur lokale Formulare bleiben entsprechend den bisherigen Verträgen behandelbar.
- **`HeaderSourceEdits` / `FieldSourceEdits` / `DefaultsSourceEdits`:** kein Umgehen zentraler Capabilities. Prüfen, ob der tatsächliche Verifier eine riskante Änderung bereits ablehnt, bevor redundant neue Prüfungen ergänzt werden.
- **`JobGraphProjector.project(...)` / `GraphFormProjector`:** `PARTIAL` und Codeinsel erhalten. Eine allgemeine Optionsfähigkeit kann lediglich das Öffnen eines Dialogs erlauben; massgeblich ist die serverseitige Entscheidung zur konkreten Änderung. Nur bei belegter irreführender Darstellung eng korrigieren.
- **`GretlLanguageServerApi`-/Wireadapter:** bestehende Version und Requests nutzen. Keine neuen Include-Endpunkte erforderlich. Gegen echte Serverprozesse prüfen, dass eine Ablehnung nicht durch einen Transportfehler oder fehlenden Katalog vorgetäuscht wird.
- **`applyEditPlan(...)`, `edit-guard.ts`, `ModelerPanelController`:** native zusammengehörige Edits und erneute Identitätsprüfung beibehalten. Neue kleine Hosttests nur soweit ein konkreter Anpassungspfad berührt wird; keine komplette UI-Neuschreibung.

Diese Methoden sind vorhandene Anschlussstellen. Die Hilfsmethodenbezeichnung `requiresClosedReferences` ist ein Vorschlag, keine Behauptung bereits vorhandenen Codes.

### 4.8 Handoff für eine spätere vollständige Includeintegration

Die eigene Datei `TOOLING_HANDOFF.md` hält die tatsächliche Runtime-Syntax, die beiden Pfadbasen, Source-/Site-IDs und ausgewählte gemeinsame Fixtures fest. Sie benennt mindestens:

- Syntax aus offenem Buffer lesen, nicht aus einem zweiten veralteten Diskstand und niemals evaluiert.
- Eine physische Quelle kann mehrere Anwendungskontexte haben; Schlüssel mindestens aus Projektidentität, besitzendem Scope und Sourceidentität, nicht nur `Map<URI, Scope>`.
- Scope und Ressourcenbasis kommen vom Einbinder; Include-Basis von der einschliessenden Fragmentdatei. `file(...)`- und SQLauflösung nicht versehentlich zum Fragmentordner verschieben.
- Bekannte Registrierungspositionen an Apply-Sites erhalten; Includes sind keine Tasks. Beliebiger Groovy-Kontrollfluss bleibt konservativ, auch wenn die Include-Pfade konstant sind.
- Fragmentänderungen müssen später alle betroffenen Kontexte invalidieren. Aktuelle Snapshots, Bufferoverlays, Sourceinkarnationen und Multi-Root-Grenzen wiederverwenden.
- Mehrfach verwendete Fragmente sind beim Editieren besonders: ein Quelltextedit wirkt auf alle Anwendungen. Kein kontextlokales Rename versprechen, das die gemeinsam verwendete Quelle global ändert.
- Vollständige Darstellung, Navigation und Bearbeitung werden erst nach eigenen Paritäts-/Sicherheitsnachweisen freigegeben. Dieser Auftrag enthält diese Erweiterung nicht.

## 5. Technischer Zuschnitt bis auf Klassen und Methoden

Alle folgenden neuen Namen sind Vorschläge für konkrete Verantwortungen, keine Behauptung vorhandener APIs. P0 bildet sie auf den tatsächlichen Code ab. Kleine zusammengehörige Records dürfen verschachtelt sein; keine Klasse ohne Funktion/Test anlegen. Neue Implementierung vorzugsweise unter `ch.so.agi.gretl.runtime.groovy.fragment`; Basisklassen nur punktuell anschliessen.

### 5.1 Source-Snapshot und reiner Direktivenleser

**`FragmentSource` (neu, unveränderlich):** `sourceId`, rootrelativer normalisierter Pfad, ursprüngliches Label, UTF-8-Rohbytes/decodierter Text, Inhaltshash. Bytearrays defensiv kopieren. Absolute Hostpfade nicht als portable Identität verwenden.

**`ApplyDirective` (neu):** Quelldatei-ID, dekodierter `from`-String, Anweisungsbereich, Literalbereich, stabile Site-ID. Neue Textbereiche mit dokumentierter UTF-16-/Offsetkonvention; bestehende Laufzeitdiagnosen bleiben kompatibel einbasiert. Eine unbekannte Position nicht als präzise Zeile 1 erfinden.

**`ApplyDirectiveReader` (neu):**

```java
DirectiveReadResult read(String sourceId, String sourceText);
```

Groovy Parse/Convert mit kontrolliertem Compiler-/Classloaderkontext bis zum Syntax-AST, nicht zur semantischen Analyse/Bytecodeerzeugung. Bestehenden Settingsparser als Implementierungsreferenz lesen, aber nicht für dieses Feature umbauen. Map-first-Syntax korrekt erkennen; durch AST und Tokenbereiche Literalformen prüfen. Alle eindeutig auf das GRETL-DSL zielenden `apply`-Aufrufe bezüglich erlaubtem Kontext validieren. Kommentare/Strings/fremde Receiver dürfen keine Includes erzeugen. Ergebnis enthält strukturierte Probleme statt geschluckter Parsefehler. Keine Job-Classpath-Discovery, Grape, AST-Testausführung, Projekt-Plugininitialisierung oder `GroovyShell.evaluate`.

**`FragmentSourceAccess` (neu, kleine IO-Grenze):**

```java
FragmentSource readContainedSource(SourceRequest request);
```

Filesystemadapter validiert Root, Pfad, reguläre Datei und Symlinks, liest begrenzt UTF-8 und liefert einmalig Bytes. Kein Netzwerk. Testseitig Memoryadapter zulassen; keine Runtime-Serviceplattform dafür bauen. Bestehende geeignete IO-/Grenzwerthelfer wiederverwenden. Für neue Includegraphen endliche, intern dokumentierte Limits vorsehen, z. B. Tiefe 64, 1'024 Quellen, 8 MiB je neue Fragmentquelle, 64 MiB gesamt; gegen reale Beispiele prüfen. Keine neuen versteckten CLI-Einstellungen und keine nachträgliche Verkleinerung erlaubter alter Einzeljobs.

### 5.2 Graphauflösung und Identität

**`FragmentGraphResolver` (neu):**

```java
ResolvedScriptBundle resolve(DefinitionRoots roots, FragmentSourceAccess access);
```

`DefinitionRoots` enthält Definitionroot, geordnete echte Einstiegsscripts und deren bestehenden Scope. Ein DFS-Besuchsschlüssel ist `(owningScope, sourceId)`, nicht nur der Dateiname. Aktiver Stack erkennt Zyklen; besuchte Quellen pro Scope erkennen doppelte Anwendungen. Ein globaler immutable Sourcebestand darf Bytes/Parsergebnis teilen. Scopezuordnungen, Kanten und Scriptinstanzen bleiben getrennt.

**`ResolvedScriptBundle` (neu):** komplette Source-Snapshots, echte Einstiege, validierte Apply-Sites/Kanten, Source-Map, nachvollziehbare Reihenfolge, Featureflag „enthält Includes“ und Definitionhash. Der Graph gibt **keine Taskreihenfolge** vor.

**`ScriptSourceNames` / `ScriptBundleHash` (neu oder kleine Erweiterungen):**

```java
String virtualGroovyName(String rootRelativeSourceId);
String definitionSha256(ResolvedScriptBundle bundle);
```

Eindeutige deterministische virtuelle Scriptklassennamen auch bei gleichen Basenames. Eine gemeinsame Fragmentquelle nur einmal kompilieren, aber beliebig in zulässigen unterschiedlichen Scopes instanziieren. Keine absolute Checkoutadresse im Namen. Bestehende virtuelle Projektnamen und Hashes ohne Includes behalten. Hashalgorithmusversion benennen und längengeframed hashen.

### 5.3 Registrierungszustand und `apply`

**Wichtige bestehende Falle:** `JobDslModel` hält im Einzeldateimodus eigene TaskRef-/Operationsmaps. Zwei unabhängige Modelle mit nur gemeinsamem Builder würden deshalb dateiübergreifendes `result(...)` nicht automatisch korrekt machen. Ein geteiltes Binding wäre die falsche Reparatur.

Führe den minimal notwendigen **`DefinitionScopeState`** ein oder verwende einen bereits geeigneten lokalen Zustand: gemeinsamer Builder, TaskRef-/Operationsregistrierung, gegebenenfalls bestehender `ProjectTaskIndex`, Scope-ID und final angehängte Definition. Quellen-/Bindinginformationen bleiben pro Scriptinstanz separat. Bestehende Konstruktoren/öffentlichen Loader-Signaturen delegieren kompatibel; kein pauschaler Umbau der Runner-API.

**`FragmentLoadSession` (neu):**

```java
void apply(JobDslModel caller, Map<String, ?> options);
void registerSource(String sourceId, DefinitionScopeState scope);
void finishRegistration(JobDefinition definition);
```

Die Session und `DefinitionScopeState` dürfen im bestehenden Paket `ch.so.agi.gretl.runtime.groovy` liegen, damit das derzeit paketprivate `JobDslModel` nicht für ein neues Unterpaket öffentlich gemacht werden muss. Reader-/Graph-Werte können im Unterpaket `fragment` liegen. Keine unnötig öffentliche mutable API schaffen. Die Session besitzt den vorbereiteten Bundlegraphen, einen Source- oder Bytecode-Scriptfactoryadapter und pro Scope registrierte Source-Instanzen. `apply` darf ausschliesslich eine vorab validierte, zum Caller passende Site/Kante verwenden; kein nachträgliches freies Dateiladen. Unbekannte/dynamisch erschlichene Ziele und Anwendung nach dem Definitionsfreeze ablehnen. Optional neue eindeutige Site-IDs im kompilierten Code nur dann einführen, wenn zur sicheren Zuordnung nötig; keine Quelltextkonkatenation oder umfangreiche AST-Transformationsplattform.

**`GretlScript.apply(Map<String, ?> options)` (kleine Ergänzung):** delegiert an die Definition-Session. Validiert nur passende Optionen; Registrierung ist Load-Time, nicht Runtime-Taskarbeit. Fehlender Sessionkontext ergibt eine klare DSL-Diagnose. Existing Taskoptions-/Parameterlisten möglichst nicht anfassen.

**`GroovyJobLoader` (gezielt erweitern):** bestehende `load`, `loadProject`, `loadCompiled`, `loadCompiledProject` erhalten; neue interne Bundlepfade bzw. additive Overloads nutzen. Source-Scripts mit bestehender Basisklasse/Compilerhülle aus Snapshotbytes kompilieren; Bytecodepfad ausschliesslich vorhandene Klassen instanziieren. Für jede Quelle ein frisches Binding mit passendem Model und Operationssymbolen. Alle Modelle nach gemeinsamem Freeze an dieselbe Definition anschliessen. Loader-/Sessionzustand nicht statisch und nicht zwischen Runs teilen.

### 5.4 Quellenzuordnung und Pfade

Source-Map vom Bundle in bestehende `SourceLocations`-/Builderdiagnostik integrieren. `JobDslModel` muss „Quelle der aktuellen Scriptinstanz“ von „besitzender Job-/Projektscope“ unterscheiden. Ressourcenauflösung bleibt über den besitzenden Scope. Fehler in später ausgeführten Closures müssen noch auf das richtige Fragment zeigen, auch wenn die Include-Registrierung längst beendet ist. Kein globaler ThreadLocal-„current file“-Trick, der nach dem Laden falsche Quellen liefert.

`ScriptJobSource`/`ProjectJobSource` so anbinden, dass Quellenhash und `loadDefinition` denselben Bundle-Snapshot benutzen. Vorhandene Aufrufsignaturen bei Bedarf durch additive interne Fabriken erhalten. Kein Source-Lesen in den reinen Runner verschieben. Fehler in Includeauflösung vor Tasks und ohne neue Runressourcen melden.

### 5.5 Packager und Job-JAR

`JobCompiler`/`ProjectCompiler` konsumieren den gleichen statisch aufgelösten Snapshotvertrag. Helperquellen bleiben eigenständige Quellen; erlaubte Scriptklassen sind **exakt** die registrierten Einstieg-/Fragmentquellen. Nicht gelistete Scripts bleiben Fehler. Kompilierung nutzt weiterhin den selektierten isolierten Classpath und vorhandene Kollisions-/Service-/Lizenzlogik.

`CompiledProjectScript` nicht verbiegen, um Fragmentdateien als Subprojekte auszugeben. Additives **`CompiledScriptBundle`** trägt Quellen-ID → Klassenname, echte Einstiege mit Scope, validierte Apply-Kanten/Sites, Source-Map und Hash. Alte Loaderpfade bleiben lesbar und getestet.

**Interne Metadatenentscheidung:** Für Pakete mit Includes neues internes `META-INF/gretl/job.json`-Schema **3** vorsehen, mit explizitem `definitionMode` (`single`/`project`) und versioniertem Scriptbundle. Ohne Includes weiterhin bisheriges Schema 1/2 schreiben. Das ist **nicht** Deskriptorschema 3 und kein Anlass für ein neues Benutzerschema. Sollte das tatsächliche Repository intern Schema 3 inzwischen belegen, P0 konfliktfrei die nächste freie Version bestimmen und überall konsistent dokumentieren.

Die neue Darstellung muss mindestens Modus, Rootentry/Projekteinstiege, Quellenidentitäten, Klassenabbildung, Include-Kanten/Sites, Source-Map, Defaultzuständigkeit und Definitionhash eindeutig tragen. Bei Projektmodus bleiben Settings-Projektdefaults erhalten. Einzeljobdefaults werden wie bisher vom Einstieg registriert. `JobJarMain` parst strikt: unbekannte Version, doppelte/ungültige IDs, fremde/missing Edgeziele, unzulässige Pfade, mehrfach belegt/fehlend referenzierte Klassen und unzulässige Moduskombinationen sind Startfehler. Keine beliebige Klasse aus Metadaten initialisieren, bevor ihre Identität/Basisklasse geprüft wurde.

Neue Metadatenbehandlung in einen kleinen Codec/Bundleadapter auslagern, statt die bestehende Mainklasse gross umzustrukturieren. Alte öffentliche Konstruktoren bei Record-Erweiterungen über passende Überladungen erhalten oder einen neuen internen Typ wählen. Bestandspakete als Regression wirklich laden, nicht nur JSON wieder serialisieren.

Bei Ausführung keine Quelltextkompilation, kein Filesystem-Fallback auf Checkoutdateien und keine Settingsausführung. Statische Ressourcen erst zur bisherigen Runphase materialisieren; `--help`, `--version`, `--plan` erzeugen deshalb keine Ressourcenkopie. Nachweis mit einem JAR ohne beiliegende `.groovy`-Ressourcen, soweit diese nicht bewusst nur als Provenienz geführt werden; Provenienztexte dürfen niemals Executionsfallback sein.

### 5.6 Diagnosen

Neue Codes mindestens für ungültige Syntax/Kontext, fehlende Datei, unsicheren Pfad, Zyklus, doppelte Anwendung, unerlaubte Fragmentdefaults und unerlaubten Zugriff nach Registrierung. Beispielsweise `APPLY_SYNTAX`, `APPLY_CONTEXT`, `APPLY_NOT_FOUND`, `APPLY_PATH_UNSAFE`, `APPLY_CYCLE`, `APPLY_DUPLICATE`, `APPLY_DEFAULTS_NOT_ALLOWED`, `APPLY_OUTSIDE_DEFINITION`. Bestehende Codes für Task-/Parameterkollisionen, Compilerfehler und Taskfehler nicht unnötig ändern. Codes sprachneutral; DE/EN-Text in vorhandene i18n-Struktur.

Keine Secretwerte oder komplette beliebige Quellen in Fehlermeldungen aufnehmen. Relative Dateipfade, Zeile, Ursache und Include-Kette genügen. Limitüberschreitungen sind explizite Definitionserrordiagnosen, kein StackOverflow oder stiller Teilgraph.

Die konkreten Methoden für die eng begrenzte Toolingkompatibilität stehen in Abschnitt 4.7. Sie sind kein Anlass, den Runtime-Reader in den LSP zu importieren oder die bisher unabhängige Typ-/Versionsarchitektur zu vereinigen.

## 6. Phasen mit harten Gates

Jede Phase hat **einen** unabhängigen Phasenreview. Nicht für jede Datei einen zusätzlichen Review erzeugen. Kleine Implementierungscommits innerhalb einer Phase sind erlaubt; abgenommen wird der abschliessende Kandidat gegen den vorher abgenommenen Phasenstand. Bei Nacharbeit neuer Kandidaten-SHA, gezielte Nachtests und erneute unabhängige Prüfung. Unveränderte Teilprüfungen dürfen zur Risikoanalyse dienen, ersetzen aber nicht die am neuen Kandidaten fälligen Nachweise.

### P0 – Arbeitsbasis, kleine Baseline und autonomer Reviewweg

**Ziel:** Den separaten Featurearbeitsraum auf dem integrierten Mainstand sowie einen tatsächlich funktionierenden zweiten Prüfer haben, bevor grössere Änderungen beginnen.

1. `pwd`, Gitstatus, `git rev-parse HEAD`, `git worktree list --porcelain`, lokale/Remote-Refs, JDK 25 und Wrapper prüfen. Fremde Änderungen und vorhandene fremde Worktrees erhalten. Lesezugriff auf die Remote-Refs darf sie aktualisieren; keine Publikation. Bei vorhandenem Featurebestand daran anknüpfen statt neu beginnen.
2. Integrierten Mainstand nach 4.1 als Basis wählen. Recherchepin: `0bfc8f0fccf43989d751d4f5490202311d200628`; darin enthaltener abgenommener Tooling-Produktkandidat: `54bb123070fc7bd81aea58b4e1191beecf568cbf`. Neueren tatsächlichen Delta prüfen, nicht zurücksetzen. Main-/Featurebasis, enthaltenen Tooling-SHA und Schreibgrenzen erfassen; nach verifizierter Abstammung `base_status: MAIN_WITH_ACCEPTED_TOOLING`. P8 ist abgeschlossen und wird nicht erneut beauftragt.
3. Rootregeln, neue Auftragsdateien, aktuelles Subprojekt-/Paket-/Runtimeverhalten sowie die einschlägigen Code-/Teststellen lesen. Einziger operativer Status ist eine kurze `WORK_QUEUE.md` im Featureordner. `TOOLING_HANDOFF.md` knapp beginnen. Keine neue historische Inventur.
4. Den bisher fehlenden `apply`-Aufruf durch eine kleine temporäre Loaderprobe reproduzieren. Ist er inzwischen implementiert, nur echte Vertragslücken bearbeiten. Eine erwartete alte Ablehnung ist die Baseline, kein dauerhaft rot zu committender Build.
5. Eine repräsentative bestehende Einzeljob-/Projekt-Loaderbaseline und eine kleine statische Opaque-/Graphprobe ausführen. Keine Fachmatrix und kein GUIvolltest. Für P4 nur früh feststellen, welcher vorhandene installierte-GUI-Testweg zur Verfügung steht; jetzt noch keine GUIweiterentwicklung beginnen.
6. OpenCode 2.x und tatsächliche DeepSeek-1017-Zuordnung, vorhandene Anmeldung, neue Session, Reviewworktree, echten harmlosen Testlauf und maschinenprüfbares Ergebnis beweisen. Abschnitt 9.0 beachten: der bestehende Toolingorchestrator verlangt sonst den falschen Auftrag und Vollgates. Nur einen kleinen Featureadapter bzw. direkte bestehende CLImechanik verwenden.
7. Stabile P0-Arbeit committen, vollständigen SHA erst danach in externen Handoff schreiben. Zweiter Agent prüft Basis/Isolation, angemessene Testselektion, neue Reviewmechanik und eine eigene Baselinegegenprobe. P0 benötigt keine neue Produktionsklasse.

**Gate:** integrierter Mainstand als Basis verifiziert, tatsächliche kleine Baseline grün, gültiger unabhängiger P0-Review am genauen SHA; kein Umbiegen fremder Arbeitsräume oder alter Abnahmen. Kein neuer Tooling-Abnahmevorbehalt. Fehlende autonome Reviewfähigkeit ist `BLOCKED_REVIEW_INFRA` und sperrt P1.

### P1 – Statische Includeanalyse, sichere Quellen und Hashvertrag

**Ziel:** Includeabschlüsse vollständig und ohne Script-/Taskausführung ermitteln können.

Implementiere `ApplyDirectiveReader`, Source-Snapshot/IO-Grenze, `FragmentGraphResolver`, stabile Source-IDs und versionierten Bundlehash. Noch kein öffentlich halb funktionierender `apply`-Bypass ohne validierten Graphen. Syntax- und Pfadregeln gemäss S01–S03/S09/S10/S12/S14/S15.

**Notwendige Tests:** zwei erlaubte Syntaxformen; nested Include; gleiche Basenames; Kommentar/String/fremder Receiver; dynamisches bzw. verschachteltes DSL-`apply`; erlaubtes internes `..` und Rootausbruch; Missing/Directory/Symlink; Zyklus und mehrfacher Include im selben Scope; Wiederverwendung in zwei unterschiedlichen Scopes; Markerquelle, deren Toplevel und `@ASTTest` beim **reinen Lesen** nicht laufen; Hashänderung durch nested Fragment und Unabhängigkeit vom Checkoutpfad. Wenige parameterisierte Fälle statt Kreuzprodukt aller Variationen. Ein Limitgrenzfall genügt als Wirksamkeitsnachweis der Begrenzung.

**Regression:** vorhandene Settings-/Projektquellnamens-/Hashprüfungen nur soweit eine gemeinsame Hilfsfunktion angefasst wurde; andernfalls keine erneute ganze Runner-Suite.

**Reviewfokus:** Erkennung ohne Ausführung, keine Regex-Heuristik, korrekte Scopeidentität, keine unbeabsichtigte Veränderung bestehender Jobs. Reviewer erfindet mindestens eine neue Syntax-/Pfad-/Graphgegenprobe.

**Gate:** neue Reader-/Resolver-/Hashverträge grün und unabhängig abgenommen; kein behauptetes bereits fertiges CLI-Feature.

### P2 – Runtime: Einzeljob und Subprojekte

**Ziel:** Dasselbe Fragmentverhalten über tatsächlichen Loader und Runner liefern.

Erweitere `GretlScript`, den minimal geteilten Definitionsscopezustand, `FragmentLoadSession`, Source-/Projektloader und Quellzuordnung. Integriere den konsistenten Snapshot in `ScriptJobSource`/`ProjectJobSource`. Die bestehenden öffentlichen Einstiegssignaturen bleiben nutzbar. Aufgabe und Quellscope nicht verwechseln.

**Notwendige Tests:**

- Ein Einstieg mit zwei Fragmenten und nachweisbarer dateiübergreifender `dependsOn`-/Ergebnisverarbeitung; auch Vorwärtsreferenz. Aufgaben wirklich ausführen, Ergebnisinhalt prüfen.
- Registrierungsreihenfolge vor/zwischen/nach `apply` und verzögerte Taskkonfiguration. Kein `dependsOn` aus reiner Einbindereihenfolge.
- Frische Bindings, eigene Imports/gleichnamige lokale Variablen, Parameternutzung über Fragmente; lokale Variablen anderer Dateien bleiben unsichtbar.
- Doppelter Task/Parameter und `defaults` im Fragment werden korrekt abgewiesen, nicht überschrieben; Cleanup läuft im normalen gemeinsamen Abschluss genau einmal.
- `file`/`ctx.file` behalten Ressourcenbasis aus Task, Action und einem repräsentativen weiteren Scope; **nur zusätzliche Adapterpfade testen, falls sie getrennten Code besitzen**. Fremdes CWD und Pfad mit Leerzeichen. `path`-/`workPath`-Regression unverändert.
- Zwei Subprojekte verwenden dieselbe Fragmentquelle mit getrenntem Zustand und qualifizierten Tasknamen. Cross-Project-Verweis bleibt korrekt. Kein zweiter Runbericht.
- Fehler in verschachteltem Fragment bei Kompilierung sowie Task-/Actionausführung nennt Originaldatei und Zeile; ein weiterer Cleanup-/Konfigurationsfall dort, wo ein anderer Fehleradapter verwendet wird.
- Neue Definition nach Fragmentänderung nutzt neue Bytes/Hash; unveränderte Jobs ohne Includes behalten Identität/Verhalten.

**Prozesssmoke:** Ein neuer kleiner lokaler Fall mit `gretl run`, `check`, `plan` und Jobhilfe. Marker beweisen, dass die drei introspektiven Wege keine Taskkonfiguration/Tasks/Cleanups ausführen. Einen zulässigen Toplevel-Marker nicht fälschlich als Sandboxverletzung behandeln. Prüfe die konkreten Tasknamen/Kanten und eine echte Ausgabe, nicht allein Exitcode 0.

**Reviewfokus:** Gemeinsame Taskauflösung ohne gemeinsames Binding, sourcegenaue Closures, Scope-/Pfadparität und Vorwärtsreferenzen. Eigene Reviewer-Gegenprobe über eine Dateigrenze.

**Gate:** SCRIPT-Einzeljob und SCRIPT-Projekt funktionieren; relevante bestehende Loader-/Scope-/Quelltests grün; unabhängige Abnahme. JAR-Parität ist erst P3 geliefert.

### P3 – Paketierung und quellenfreie Job-JARs

**Ziel:** Alle statisch eingebundenen Scripts mitkompilieren und aus Bytecode mit identischer Semantik laden.

Implementiere die Bundlekompilation und strikt versionierte interne Metadatendarstellung, ohne die Benutzerschemata oder die Auswahl der Taskmodule aufzuweichen. Alte Job-JARs/Metadatenschemata bleiben unterstützt. `sources` ist keine Hintertür für unbekannte Scripts.

**Notwendige Tests:**

1. Einzeljob mit Includes unter Deskriptorschema 2 paketieren und isoliert ausführen. Den breiten Schema-1-Adapter mit gezieltem Komponententest abdecken; eigener schwerer zweiter Prozesslauf nur, falls dort abweichende Logik besteht.
2. Projekt mit Includes und geteilter Fragmentquelle unter Deskriptorschema 3 paketieren und isoliert ausführen. Derselbe fachliche Ausgabevertrag wie im SCRIPT-Test.
3. Vor isoliertem JAR-Lauf Quellbaum nachweisbar unzugänglich machen/entfernen, leeres Arbeits-CWD, nur notwendige Java-/Laufzeitumgebung. Keine `.groovy`-Datei nachladen und keinen Compiler zur Startzeit benutzen. Tatsächlicher `java -jar`-Prozess, keine alleinige Mockprüfung.
4. Marker zeigen: Paketieren führt Scriptbodies/Tasks nicht aus. JAR-`--help`/`--plan` führt keine Tasks/Cleanups aus; Ressourcenmaterialisierung bleibt bis Run aus. Neue Fragmentfehler zeigen weiterhin richtige Quellen/Zeilen.
5. Alte Formate ohne Includes bleiben im vorhandenen Prozess-/Komponententest verwendbar. Keine pauschale Migration alter Job-Metadaten.
6. Gezielt defekte neue Metadaten: unbekannte Version, fehlende Fragmentklasse/Edgeziel, doppelte ID, unsicherer Quellpfad. Start scheitert klar. Ein nicht referenziertes zusätzliches Script in `sources` wird weiterhin abgewiesen.
7. Paket- und SCRIPT-Quellidentität stimmen für denselben Snapshot überein; eine Fragmentänderung ändert sie. Ein Paketlauf erfordert keine frei zugänglichen Entwickler-Helperquellen.

Eine kleine Actionfixture plus ein bereits vorhandenes lokales IO-Operationsbeispiel genügt. Nicht sämtliche Fachoperationen erneut paketieren. Ausgewählte vorhandene Classpath-/Mehrscript-/Metadatenregressionen ausführen, die tatsächlich betroffen sind.

**Reviewfokus:** Kein dynamisches Nachladen, keine falsche „Projekte = Fragmente“-Modellierung, exakte zulässige Scriptmenge, versionsklare Metadaten, Parität und Scopeisolation. Reviewer verändert selbst einen relevanten Paketinput oder eine kleine Includeprobe.

**Gate:** isolierter Einzeljob- und Projekt-JAR-Lauf erfolgreich, Altformatregressionen erhalten, neuer Kandidat unabhängig abgenommen.

### P4 – Sichere Tooling-Koexistenz, Beispiele und Schlussabnahme

**Ziel:** Runtime und vorkompilierte Pakete funktionieren auf dem gemeinsamen Stand mit dem vorhandenen LSP/Modeler; die eingeschränkte IDE-Sicht ist nachweislich sicher und ehrlich.

**Voraussetzung:** P3 akzeptiert; der Kandidat enthält weiterhin die in P0 verifizierte integrierte Mainbasis. Der ursprüngliche Toolingauftrag P0–P8 ist abgeschlossen und wird nicht wieder geöffnet.

1. Featurebasis, Abstammung und aktuellen Kandidaten prüfen. Bei unveränderter Mainbasis unmittelbar mit den Kompatibilitätstests fortfahren; keine zusätzliche Tooling-Mergeprobe erzeugen. Nur einen tatsächlich zwischenzeitlich notwendigen Upstream-/Integrationsdiff separat prüfen. Die gemeinsamen Regeln/Guards und Module erhalten.
2. C01–C04 mit kleinen Core- und echten stdio-LSP-Tests prüfen. Die Coverage-Fixture und den Nichtausführungsfall getrennt halten. Spezielle Intents und gleichwirkende generische Setter/Formularbatches prüfen. Eine lokale skalare Feldänderung muss weiterhin möglich sein. Vorhandene Tests/Harnesses verwenden; vorhandenes korrektes Produktverhalten nicht unnötig umbauen.
3. Nur wenn die Proben einen konkreten Defekt zeigen: minimalen Guard-/Adapterfix nach 4.7 liefern, Regression ergänzen und gezielt testen. Das Schreiben des Fixes ist weiterhin Codex-Aufgabe, nicht Reviewer-Aufgabe. Keine vollständige statische Fragmentengine oder neue GUI-Features vorziehen.
4. Einen kleinen realen GUIablauf C05 am frisch gebauten VSIX im eigenen Profil ausführen: Einstieg öffnen, partielle Ansicht/Codeinsel, lokale Änderung, eine fachlich passende Ablehnung, Speichern/Undo/Redo und bytegenaue Quell-/Fragmentkontrolle. Falls ein wichtiger Unterschied nur auf Wireebene erreichbar ist, dort prüfen; nicht für jeden Setter einen eigenen GUI-Testfall bauen. Die vorhandene Tooling-P8-GUI-Matrix nicht nochmals durchlaufen.
5. Ein kanonisches Beispiel `examples/script-includes/` mit tatsächlichem SCRIPT-/JAR-Vertrag liefern. Passende Distributionskopie nur gemäss vorhandenem Beispielverfahren. Featureanleitung erklärt Syntax, Bindings, zwei Pfadbasen, Defaults-/Duplicate-/Cycle-Regeln, Subprojekte und Pakete sowie ausdrücklich die **partielle** aktuelle IDE-Unterstützung. `docs/README.md`/Referenz nur additiv ändern.
6. Alle S01–S19 und C01–C08 dem tatsächlichen Code/Nachweis zuordnen. Finale Featuretests, unmittelbar betroffene Regressionen, echte Beispielausführung und isolierte Single-/Project-JARs am Schlussstand nachweisen. Ein aktueller unveränderter bereits gesicherter Beleg darf begründet wiederverwendet werden; nach relevanter Code-/Basis-/Artefaktänderung passende Tests frisch ausführen. Keine automatischen Wiederholungen aller fremden Fachgates.
7. IMAGE bleibt externer SCRIPT-/Projektweg. Bei Änderung von Launcher-/Mount-/Containerlogik fokussierter echter Containerlauf erforderlich; andernfalls reicht der begründete Feature-Testumfang, mit ehrlichem IMAGE-Status. Keine neue ALL/full-Verpflichtung und kein JOB-JAR im generischen Image.
8. `TOOLING_HANDOFF.md` und kurze Statusdatei vervollständigen, Links/Beispielkonsistenz mit passenden bestehenden Werkzeugen prüfen. Nicht betroffene Docs-Rendering-/Publikationspipelines nicht reflexhaft neu qualifizieren.
9. Finalen Kandidaten committen; unabhängige frische OpenCode-/DeepSeek-Session prüft **kumulativ** Code und Ziele seit Featurebasis, das Zusammenwirken mit Tooling und eine eigene neue Interaktionsgegenprobe. Eigene relevante Prüfungen des Reviewers sind Pflicht; denselben GUIlanglauf nicht aus Routine doppelt starten, wenn identische aktuelle Rohbelege unabhängig überprüft wurden und der Reviewer eigene Core-/Wire-Gegenproben ausführt. Bei einem GUI-/Hostfix ist eine passende eigene Benutzerpfadgegenprobe erforderlich.

**Gate:** notwendige S- und C-Verträge erfolgreich belegt; akzeptierte gemeinsame Toolingbasis; letzter Code-/Doc-SHA unabhängig `ABGENOMMEN`; keine offenen Feature-BLOCKER/MAJOR und keine fälligen unerledigten Prüfungen. Abschlusslabel `RUNTIME_INCLUDES_WITH_SAFE_PARTIAL_TOOLING` plus tatsächliche Pins. Es bedeutet **nicht** `FULL_INCLUDE_AWARE_IDE`, neue Tooling-P8-Abnahme oder Releasefreigabe. Kein Push/Merge nach `main` oder `codex/gretl-tooling`.

## 7. Kleine Anforderungs-/Nachweismatrix

Die IDs S01–S19 aus Abschnitt 3 und C01–C08 aus Abschnitt 4.5 sind die Abnahmekriterien, keine neue generierte Inventarwelt. Eine einzige Tabelle in `WORK_QUEUE.md` darf sie den tatsächlichen Tests zuordnen.

| Vertrag | Primärer Nachweis | Phase |
|---|---|---|
| S01–S03: Syntax, Grenzen, statisches Lesen | Reader-/Nichtausführungstest plus Loader-Negativfall | P1/P2 |
| S04–S09: gemeinsamer Scope, Isolation, Kollisionen | ausgeführte Single-/Project-Loadertests | P2 |
| S10–S12: Include-/Ressourcenbasis und sichere Pfade | Resolvertests + fremdes CWD im echten Job | P1/P2 |
| S13: Originalquellen | Compiler-/Konfigurations-/Ausführungsdiagnosen | P2/P3 |
| S14–S15: konsistente Bytes und Hash | Snapshot-/Hashmutationsproben | P1/P2/P3 |
| S16: run/check/plan/help | kleiner CLI-Prozessfall mit Markern und Inhaltsassertions | P2/P4 |
| S17–S18: quellenfreies JAR, Formatkompatibilität | zwei isolierte Prozesse + ausgewählte Negativ-/Altformatfälle | P3/P4 |
| S19: IMAGE unverändert | Diffprüfung; echter Smoke nur entsprechend P4 | P4 |
| C01–C03: partielle Analyse und sichere Edits | saubere Apply-/Kontrollfixture, gezielte Core-/Setter-/Batchfälle | P4 |
| C04–C05: tatsächlicher LSP/Editorweg | stdio-Prozess und ein fokussierter installierter VSIX-Ablauf | P4 |
| C06–C08: Grenzen, gemeinsame Basis, Handoff | Diff/Dependency-/Identitätsprüfung, reale Integrationsbelege, dokumentierte Grenzen | P0/P4 |
| Echte unabhängige Abnahme | neue OpenCode-Session, Testlogs, Gegenprobe, exakter SHA | jede Phase |

### 7.1 Phasenrequest und Kriterienmenge

Der Request grenzt den **Phasenumfang** ausdrücklich ein; eine P1-Komponentenabnahme bedeutet noch keine vollständige SCRIPT-/JAR-Lieferung. Für P0 vier kleine Workflowkriterien verwenden: `P0-BASE` (Basis/Isolation), `P0-BASELINE` (wirklich ausgeführte kleine Baseline), `P0-REVIEW` (autonomer unabhängiger Prüflauf) und `P0-TESTSCOPE` (richtiger Auftrag und angemessene Testauswahl). Kein grosses neues Kriterienregister generieren.

| Phase | Im Request abzuprüfender Umfang | Nicht vorzeitig als geliefert ausgeben |
|---|---|---|
| P0 | Die vier P0-Kriterien und verifizierte integrierte Mainbasis | Produktfeature / neue Tooling-P8-Qualifikation |
| P1 | S01–S03, S09–S10, S12, S14–S15 auf Reader-/Resolver-/Snapshotebene | Vollständige Loader-/Runner-/JAR-Wirkung |
| P2 | S01–S16 soweit SCRIPT/Single/Project betroffen; relevante P1-Verträge bleiben erhalten | JAR-/IDE-Abschluss |
| P3 | S13–S18 auf dem Paket-/Bytecodepfad; gemeinsame S04–S12 in repräsentativen Paketfällen | Gemeinsame GUIfreigabe |
| P4 | Kumulativ alle S01–S19 und C01–C08 | Vollständige Include-IDE oder Releasequalifikation |

Bei Nacharbeit bleibt die Phasebase gleich. Der neue Review bewertet die gesamte Phase, nicht nur den letzten Reparaturdiff; tatsächlich erneut ausgeführt werden die risikobezogen notwendigen Prüfungen. Falls wegen eines bereits begonnenen Featurebranches oder einer späteren notwendigen Upstreamübernahme tatsächlich eine Basisintegration stattfindet, einen **separaten Integrations-/Upstreamdiff** ausweisen: dessen gesamte ursprüngliche Funktionalität nicht als neue Featureleistung oder automatisch neu reviewt ausgeben.

Neue Testnamen können z. B. `ApplyDirectiveReaderTest`, `FragmentGraphResolverTest`, `ScriptBundleHashTest`, `GroovyFragmentLoaderTest`, `GroovyProjectFragmentTest`, `FragmentSourceLocationTest`, `FragmentPackageTest`, `ScriptIncludesLocalJobIT` und `PackagedScriptIncludesLocalJobIT` sein. Das ist keine Pflicht, neun neue Klassen zu schaffen: vorhandene passende Klassen/Harnesses verwenden und eng verwandte Fälle bündeln. Testselektion muss tatsächliche Namen verwenden.

## 8. Risikobasierte Testausführung ohne Scheingrün

### 8.1 Grundregeln

Während eines Fixes nur die betroffene Klasse/Fallgruppe. Vor Phasengate alle neuen/berührten Featuretests der Phase plus unmittelbar betroffene Regressionen. In P4 einmal die Featureinteraktionen und realen öffentlichen Wege. Weiterer Umfang nur mit konkreter Begründung: beispielsweise Änderung einer gemeinsam genutzten Pfadauflösung → deren gesamte kleine Scopefamilie, Änderung am globalen Compilerclasspath → entsprechende Isolations-/Packagerregressionen.

Der abgeschlossene Tooling-P8-Review ist ein belegter **Baselinebefund**. Weder sein Status-/Archivcommit noch der bereits erfolgte Merge begründen für diesen Auftrag eine erneute komplette P0–P8-, 81-Kriterien- oder G01–G30-Qualifikation. Umgekehrt ersetzen seine alten Nachweise keine neuen Include-Tests, keine fällige Regression nach verändertem Produktcode und nicht den kleinen C05-Benutzerpfad.

**Nicht erforderlich ohne einschlägige Änderungen:** Datenbankintegration, FTP/S3, alle INTERLIS-/GeoTools-Fälle, vollständige Browser-/GUI-Matrix, Performancebenchmarks, wiederholtes vollständiges `clean verify`, sämtliche historische Agentengates. Der kleine reale C05-GUI-Nachweis in P4 ist davon ausdrücklich ausgenommen und erforderlich. Keine festen Mindestzahlen und kein künstliches Testkreuzprodukt. Bestehende Tests nicht löschen oder abschwächen, nur weil sie gerade nicht selektiert werden.

### 8.2 Maven gezielt verwenden

Die folgenden Aufrufe sind **Muster**, deren Klassen/Pluginkonfiguration Codex in P0 verifizieren muss. Maven-/Java-Versionen nicht dafür ändern:

```sh
# Beispiel: ausgewählte Groovy-Komponententests mit nötigen Reactorabhängigkeiten.
./mvnw -B -pl gretl-groovy -am \
  -Dtest=ApplyDirectiveReaderTest,FragmentGraphResolverTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`failIfNoSpecifiedTests=false` ist nur für Reactorabhängigkeiten ohne diese Testklassen erlaubt. Im **eigentlichen Zielmodul** danach zwingend frische Surefire-XML-Berichte und die erwarteten Klassen/Fälle prüfen: ausgeführt > 0, keine Failures/Errors, keine unerklärten Skips. Ein falscher Klassenfilter darf nie zu einem Phasen-PASS führen. Keine globale `failIfNoTests=false`-Änderung ins POM schreiben.

Für echte Prozess-ITs gezielt `-Dit.test=<tatsächliche Klassen>` und die vorhandene Failsafe-`verify`-Bindung benutzen. Eine erforderliche Distributions-/JAR-Erzeugung ist ein **Buildvoraussetzungsschritt**, kein Testbeweis. `-DskipTests` ist ausschliesslich für ausdrücklich bezeichnetes Artefakt-Bootstrapping erlaubt, niemals als Gate. Ein `verify` mit gefilterten ITs kann trotzdem alle Surefiretests starten; passende Selektion/Modulwahl nach tatsächlicher Pluginverdrahtung wählen, statt unnötig den ganzen Reactor auszuführen.

Bei externen Prozessen nur frisch gebaute Artefakte aus dem aktuellen Kandidaten verwenden. Ein altes installierbares `6.0.0-SNAPSHOT` aus einem anderen Worktree ist kein Nachweis. Bei gleichzeitig laufenden Builds mit identischer Mavenversion eigene lokale Maven-Repositories für Feature/Review verwenden oder einen gleichwertig nachgewiesenen artefaktisolierten Reactorpfad; keine gleichzeitig überschriebenen eigenen SNAPSHOT-Artefakte als Testeingabe. Externe verifizierte Dependencycaches dürfen geteilt werden. Erforderliche Reactorabhängigkeiten korrekt bauen; Maven-Caches für externe Dependencies dürfen normal verwendet werden.

### 8.3 Nachweise

Je Lauf: Revision/Worktree, Kommando, Beginn, Exitcode, tatsächliche JUnitklasse/-methode oder Prozessassertion und Rohberichtpfad. Ausgewählte XML-Berichte müssen zum aktuellen Lauf gehören. Keine alte XML-Datei als Ersatz für einen nicht gestarteten Test. `tee` nicht den Mavenexit verdecken lassen (`set -o pipefail` oder Prozessstatus explizit erfassen). Reports vor `clean` ausserhalb des jeweiligen Buildordners sichern.

Zustände unterscheiden: `PASS`, `FAIL`, `NOT_EXECUTED` (fällig, aber nicht möglich), `NOT_REQUIRED` (für dieses Gate begründet nicht fällig). Ein fehlender Pflichtnachweis blockiert die betreffende Phase. Ein bewusst nicht fälliger Datenbanktest blockiert sie nicht. Ein Infrastrukturabbruch ist kein fachlich erwarteter Negativfall.


### 8.4 Bestehende Tooling-Testwege gezielt nutzen

Die tatsächliche Extension liegt unter `vscode/gretl-vscode`. Ihre `package.json` besitzt `check`, `build`, `test` und `package`; der aktuelle Plan erfindet keine Rootskripte wie `tooling:test:gui` als bereits vorhanden. Ein möglicher enger Nodeaufruf nach notwendigem lokalem Dependency-/Buildsetup ist:

```sh
npm --prefix vscode/gretl-vscode run check
(
  cd vscode/gretl-vscode
  node --test test/edit-guard.test.mjs test/modeler-edit-plan.test.mjs test/modeler-controller.test.mjs
)
```

Die drei genannten Testdateien existieren am Recherchepin. Vor Nutzung ihren tatsächlichen Build-/Fixturebedarf lesen. Nicht bei jeder Java-Readeränderung laufen lassen. Bei unverändertem Host genügt in P4 die passende kleine Auswahl plus C05; bei einem Hostfix zusätzlich seine unmittelbaren Regressionen.

Core-/LSP-Prüfungen nach den tatsächlich vorhandenen JUnit- und stdio-Harnesses auswählen. Neue Fälle können z. B. `ApplyOpaqueCompatibilityTest` und `ApplyToolingProtocolTest` heissen; dies sind **Vorschläge für neue Fälle**, keine vorhandenen ausführbaren Klassennamen. Ein Test muss echte Serverantworten mit passenden Fachgründen prüfen; fehlendes Java, kaputter Katalog oder Protokollfehler sind kein erfolgreicher Negativtest.

Für C05 den im Tooling verwendeten realen VS-Code-/Webview-Testweg wiederverwenden. Testprofil, VSIXdigest, Serverdigest, tatsächliche Plattform, Sourcebytes und Ergebnisse erfassen. Lesende Inspektionsbefehle dürfen Belege liefern, ersetzen aber keine tatsächlich ausgeführte Benutzeraktion. Kein zeitaufwendiger Aufbau eines neuen universellen GUIframeworks.

Bei einer Reparatur nur ihre betroffenen Tests erneut ausführen. Im abschliessenden unabhängigen Review bleiben jedoch sämtliche Phasenziele Gegenstand der Bewertung. Unberührte Belege nur mit explizitem, überprüfbarem Zusammenhang zu unveränderten Produktbytes übernehmen, nicht alte Ergebnisse auf eine neue SHA umetikettieren.

## 9. Autonomer Review mit OpenCode 2.x / DeepSeek Flash 1017

### 9.0 Vorhandene Automation richtig wiederverwenden

Der am Recherchepin vorhandene `tools/gretl-tooling/review.mjs` ist **kein generischer Reviewerlauncher**. Er bindet `docs/implementation/gretl-tooling`, dessen Kriterienregister/Schemas, P0–P8, beide Rootvollgates und bestimmte GUIfallmengen fest ein. Ein unveränderter Aufruf würde diesen Featureauftrag falsch prüfen und die ausdrücklich gewünschte risikobasierte Testauswahl unterlaufen [R22].

Deshalb:

- Den bestehenden Toolingreviewauftrag und seine historischen Gates **nicht ändern**, um das neue Feature passend zu machen.
- Nur tatsächlich wiederverwendbare Start-/Identitäts-/Worktree-/Prozessabschlussmechanik verwenden, wenn das ohne Importnebenwirkungen und ohne alte Kriterienkopplung geht. Andernfalls reicht eine kleine eigene Sequenz bzw. ein enges Hilfsscript unter `tools/script-includes/`. Keine allgemeine neue Reviewplattform und keine Kopie der gesamten alten 81-Kriterien-Engine.
- Feature-Request nennt `assignment_id: script-includes`, `spec_version: 1.3`, Phase, `assignment_base_sha`, unveränderte `phase_base_sha`, tatsächlichen Kandidaten-SHA, Basisstatus, geltende S-/C-Kriterien und die begründet ausgewählten Tests. Requirement-Digest über die drei aktiven Auftragsdateien separat vom Produkt-SHA erfassen. Alten P8-Request niemals als Feature-P4 tarnen.
- Negativ kontrollieren, dass fremde Auftrags-ID, falscher SHA, fremder Specdigest oder eine leere angeblich ausgeführte Pflichtsuite nicht akzeptiert werden. Ein vorhandener geeigneter kleiner Validator genügt. Diese wenigen Infrastrukturgegenproben ersetzen keine fachlichen Produkttests.
- Falls die wiederverwendete Transportmechanik `PASS/FAIL/BLOCKED` statt `ABGENOMMEN/NACHARBEIT/NICHT_PRUEFBAR` verwendet, die eindeutige technische Abbildung dokumentieren und Originalurteil erhalten. Ein Label allein ersetzt niemals Identitäts-/Nachweisprüfung.

Vorhandene lokale OpenCode-/GUI-Hilfen dürfen genutzt werden, aber keine fremden oder historischen Toolingsessions übernehmen, keine globalen Credentials-/Berechtigungsprofile überschreiben und keine fremden Prüfprozesse beenden.

### 9.1 Wirkliche Installation und Modellzuordnung feststellen

Keine geratenen Befehle und kein Selbstreview als Ersatz. Die offizielle **V2**-Dokumentation verwenden [R7–R10]. V1-Serverrezepte, pauschales `/session` und alte Permission-JSONs sind kein ungeprüfter Ersatz für V2.

Codex prüft am lokalen Host:

```sh
command -v opencode
opencode --version
opencode run --help
opencode auth list
opencode models
```

Nur vorhandene unterstützte Optionen verwenden; den vollständigen Binarypfad und tatsächliche **2.x**-Version erfassen. Eine GUI-Version 2.x beweist nicht, dass `opencode` im PATH auch 2.x ist. Falls nur die Desktop-App installiert ist, lokal deren tatsächlich verfügbare CLI bzw. ihren dokumentierten V2-Backendzugang ermitteln. Keine erfundene App-Bundle-Pfadkonstante und keine globale Neuinstallation/Migration ohne Notwendigkeit.

Vorhandene angemeldete OpenCode-Provider/Credentials verwenden. **Keine zusätzliche Pflicht, den DeepSeek-Key in eine ENV-Variable zu kopieren.** Keine Credential-Exports, kein `cat auth.json`, keine Secrets im Handoff oder Transkript. Ein in der GUI funktionierender Zugang soll, soweit die tatsächliche Installation es erlaubt, vom gleichen Benutzer-/Backendkontext benutzt werden.

Bei der Nachprüfung am 4. November 2026 nennt die offizielle DeepSeek-API `deepseek-flash` als API-Modellalias für **DeepSeek-V4.1-Flash-1017**; auch ältere Aliase können auf diese Version zeigen [R10]. Deshalb nicht einfach eine erfundene ID `deepseek/Deepseek-Flash-1017` hartcodieren. Lokalen `provider/model[#variant]`-Wert, Provider-/Endpointzuordnung und tatsächlich gemeldete Modellmetadaten prüfen. `deepseek/deepseek-flash` ist nur ein plausibles **Beispiel**, bis es lokal bestätigt ist. Kein stiller Wechsel auf Pro, anderes Modell oder anderen Anbieter. Ein beweglicher Alias ist kein unveränderlicher Versionspin; im Bericht Alias, beobachtete Zuordnung und Datum nennen. Spätere Aliasänderung erneut prüfen.

### 9.2 Frischer Kontext, genaue Revision, getrennte Artefakte

Ablauf jeder Phase:

1. Codex führt fokussierte Tests aus und erstellt `review/Px-request.md` im eigenen Dokumentationsordner: Anforderungen, `base_sha`, Diffumfang, konkrete Testkommandos/Ergebnisse, bekannte Grenzen. **Keinen eigenen `head_sha` in denselben zukünftigen Commit schreiben.**
2. Code, Tests und stabilen Request lokal committen. Erst jetzt `git rev-parse HEAD` als vollständigen Kandidaten-SHA lesen.
3. Einen frischen detached Reviewworktree **auf diesem SHA** erstellen. Reviewberichte/Handoff/Logs in einem externen, phasen- und SHA-spezifischen Verzeichnis ausserhalb der aktiven Feature-/Toolingworktrees speichern. Verzeichnis nicht durch Maven-`clean` löschen lassen.
4. Neue OpenCode-Session ohne `--continue`, ohne Fortsetzung/Abzweig einer alten Codex-/Reviewsession und ohne importierte Gesprächshistorie. Reviewer bekommt Plan, eigenen Reviewprompt, neutralen Reviewrequest und konkrete SHAs. Der Testbericht des Implementierers ist Behauptung, keine zu übernehmende Wahrheit.
5. OpenCode führt die gezielten Tests **selbst** aus und konstruiert einen zusätzlichen fachlichen Gegenfall. Ergebnisse synchron abwarten, auswerten und anhand SHA/Modell/Testnachweisen prüfen. Codex übernimmt nicht den Reviewerposten.
6. Bei `NACHARBEIT` nur konkrete Findings beheben, Regression ergänzen, neue SHA committen und neue unabhängige Prüfung. Kein „Review gilt für ähnliche Änderungen weiter“.
7. Nur `ABGENOMMEN` am letzten Kandidaten schliesst die Phase. Danach nächste Phase selbständig starten. Kein Nutzerklick als regulärer Phasenschritt.

Eine neue Session am gleichen lokalen Backend ist zulässig; sie muss dennoch auf den richtigen **Reviewworktree** zeigen. Neue Session und isolierter Worktree sind getrennte Anforderungen. Ein Git-Worktree ist keine Betriebssystemsandbox: Berechtigungen und zulässige Shellkommandos bleiben ausdrücklich zu begrenzen.

### 9.3 Praktischer CLI-Pfad

Nach erfolgreicher lokaler `--help`-/Modell-/Berechtigungsprüfung kann der Aufruf so aussehen:

```bash
# Alle Werte hat Codex zuvor aus der tatsächlichen Umgebung bestimmt.
# REVIEW_REQUEST enthält den abgeschlossenen Handoff inkl. vollständigem HEAD_SHA.
(
  cd "$REVIEW_WORKTREE"
  "$OPENCODE_BIN" run \
    --model "$REVIEW_MODEL" \
    --format json \
    --file "$REVIEW_REQUEST" \
    --file docs/implementation/script-includes/OPENCODE_REVIEW_PROMPT.md \
    "Pruefe den angegebenen Commit gegen EXECUTION_PLAN.md. Fuehre notwendige Tests selbst aus, entwickle eine eigene Gegenprobe und liefere den strukturierten Reviewbericht. Keine Implementierungsaenderungen."
) >"$REVIEW_OUTPUT/events.jsonl" 2>"$REVIEW_OUTPUT/stderr.log"
```

`--standalone` ist eine Option, wenn sie lokal unterstützt ist und der private Server die bestehenden Credentials korrekt nutzt. Die laufende GUI/das gemeinsame Backend nicht stoppen oder global umkonfigurieren. `--auto` nur zusammen mit einer vorher geprüften effektiven Reviewpolicy verwenden, die riskante Aktionen ausdrücklich untersagt; es ist keine universelle Sicherheitsoption. Modell-/Agentenlimits müssen genug Raum für echte Tests und Ergebnisrückgabe lassen.

Die Policy darf Lesen im Reviewworktree, erforderlichen Build-/Dependencycachezugriff, die selektierten lokalen Testkommandos sowie Schreiben in `target/` und eigene Probe-/Reportverzeichnisse ohne Rückfragen ermöglichen. Push, Merge in Arbeitsbranches, Release, Credentialexport, Änderungen an fremden Worktrees und getrackten Produktiv-/Erwartungsdateien sind verboten. Shellrechte können Editverbote umgehen; deshalb unveränderte getrackte Dateien zusätzlich vor/nach Review prüfen und zulässige Befehle eng halten. Keine bereits geltenden restriktiveren Nutzer-/Systempolicies überschreiben.

Falls die Desktopinstallation nur über ihr Backend automatisierbar ist, den **tatsächlich installierten V2-API-Vertrag** verwenden (einen `api`-Unterbefehl nur, falls ihn die lokale Hilfe tatsächlich ausweist): neue Session mit korrekter Location, explizites Modell, Nachricht, Abschluss/Status und Ergebnisexport. Kein Blindaufruf alter V1-Endpunkte und keine GUI-Klickautomatisierung als fragiler Normalweg. Eine vorhandene tatsächlich gleichwertige Reviewautomation wiederverwenden; die feste Toolingkopplung aus Abschnitt 9.0 darf dabei nicht eingeschleppt werden. Ein kleiner nötiger Featureadapter ist keine Aufforderung zum Bau eines zweiten allgemeinen Frameworks.

### 9.4 Maschinenprüfbares Urteil ohne neue Plattform

Ein kleiner vorhandener Shell-/Node-/Java-Helfer oder eine direkt ausgeführte Sequenz genügt. Kein neuer Dienst, kein Reviewserver, keine zusätzliche Pythoninstallation. Bestehende Pythonwerkzeuge des Repositories sind nicht zu entfernen; dieser Auftrag ist keine Python-Migration.

Der Reviewer liefert einen lesbaren Bericht und ein kleines JSON-Ergebnis, mindestens:

```json
{
  "assignment_id": "script-includes",
  "spec_version": "1.3",
  "requirements_digest": "ACTUAL_REQUIREMENTS_DIGEST",
  "phase": "P2",
  "assignment_base_sha": "FULL_ASSIGNMENT_BASE_SHA",
  "tooling_base_sha": "FULL_TOOLING_BASE_SHA",
  "base_status": "MAIN_WITH_ACCEPTED_TOOLING",
  "phase_base_sha": "FULL_PHASE_BASE_SHA",
  "base_sha": "FULL_PHASE_BASE_SHA",
  "head_sha": "FULL_CANDIDATE_SHA",
  "reviewed_sha": "FULL_CANDIDATE_SHA",
  "opencode_version": "ACTUAL_2_X_VERSION",
  "provider_model": "ACTUAL_PROVIDER_MODEL",
  "session_id": "ACTUAL_SESSION_ID",
  "verdict": "ABGENOMMEN",
  "requirements_checked": ["S01", "S02", "S03", "S04", "S05", "S06", "S07", "S08", "S09", "S10", "S11", "S12", "S13", "S14", "S15", "S16"],
  "tests": [
    {"command": "ACTUAL_COMMAND", "exit_code": 0, "evidence": "ACTUAL_REPORT"}
  ],
  "counterexample": {
    "authored_by_reviewer": true,
    "executed": true,
    "input": "ACTUAL_INPUT_PATH",
    "observation": "ACTUAL_OBSERVATION"
  },
  "findings": [],
  "required_not_executed": []
}
```

Platzhalter in diesem Format sind **kein** Ergebnis. Der externe Handoff enthält zusätzlich tatsächlichen Worktree, Kandidat, Inputdateien und Reportziel. `base_sha` ist der kompatible Alias derselben unveränderten `phase_base_sha`; unterschiedliche Werte sind ungültig. Den Requirement-Digest deterministisch aus einer nach rootrelativem Pfad sortierten Liste der SHA-256-Werte von `EXECUTION_PLAN.md`, `CODEX_STARTPROMPT.md` und `OPENCODE_REVIEW_PROMPT.md` bilden; diese Eingabeliste ebenfalls sichern. Der reine Auditbericht ist kein zusätzliches Kriterienregister. CLI-Exitcode 0 bedeutet lediglich erfolgreich beendeten Aufruf. Das gezeigte JSON ist ein Formatbeispiel, niemals ein vorab gefülltes Reviewerurteil. Der tatsächliche Auftrag nennt die vollständige phasenbezogene Sollmenge der Kriterien; der Validator lehnt ausgelassene/duplizierte/fremde Kriterien ab. Codex prüft: frische tatsächliche Session, richtige Revision/Location, richtige Modellzuordnung, notwendige erfolgreiche eigene Prüfläufe und nachvollziehbar aktuelle übrige Pflichtbelege, ausgeführte eigene Gegenprobe, keine offenen BLOCKER/MAJOR und keine `required_not_executed`. Ein blosses Wort `ABGENOMMEN` im Transkript reicht nicht. Die vom Modell selbst behauptete Identität zusätzlich gegen echte Sitzungs-/Aufrufmetadaten prüfen.

Mögliche Urteile: `ABGENOMMEN`, `NACHARBEIT`, `NICHT_PRUEFBAR`. Die Toolingbasis ist bereits integriert und abgeschlossen; ein erneut behauptetes Warten auf deren P8-Abnahme ist kein gültiger Featureblocker. Tatsächlich fehlende lokale Gitobjekte oder eine falsche Arbeitsbasis dagegen konkret als Infrastruktur-/Basisproblem beheben und benennen. Fehlende C04-/C05-Pflichtprüfungen sind keine akzeptierte gemeinsame Schlussabnahme. Bei Provider-/Netzfehlern höchstens wenige gezielte Wiederholungen, keine Endlosschleife. Wiederkehrende echte Findings nicht durch unveränderte Neuprompts „wegprüfen“.

**Wenn Autonomie technisch nicht herstellbar ist:** Kandidaten-/Fehlerstatus und die konkrete fehlende Fähigkeit dokumentieren, `BLOCKED_REVIEW_INFRA` setzen und nicht in die nächste Phase gehen. Das ist ein Fehlerzustand, **nicht** der vorgesehene manuelle Normalprozess. Keine vorgetäuschte Abnahme und keine Aufforderung zum manuellen Review als bereits erfolgreicher Auftrag.

## 10. Kleine Artefakte, Git und Definition of Done

Versionskontrolliert nur Featurecode/-tests, aktuelle Featureanleitung, `WORK_QUEUE.md`, `TOOLING_HANDOFF.md` und je Phase ein kompakter Reviewrequest. Rohlogs und unabhängige Reports ausserhalb des Featurebranches bzw. als Buildartefakte aufbewahren. Nicht nach jeder Testmethode neue Evidenzdateien erzeugen.

Phasenstatus: `PLANNED → IMPLEMENTED → TESTED → REVIEW_PENDING → REVIEWED_ACCEPTED`; alternativ `BLOCKED_TEST` oder `BLOCKED_REVIEW_INFRA`. Die in P0 verifizierte Mainbasis wird separat mit `MAIN_WITH_ACCEPTED_TOOLING` erfasst; dieser Basisstatus ist kein bestandenes Featuregate. Testumfang und tatsächliches Review-SHA sichtbar halten. Ein gesonderter Statuscommit darf auf den **vorher geprüften** Implementierungs-SHA verweisen, aber gilt nicht selbst als geprüfter Code. Bei der Schlussübergabe den exakten geprüften finalen Code-/Doc-SHA nennen; spätere Produkt-/Docänderungen erfordern erneute passende Abnahme. Selbstreferenzielle SHA-Dateien vermeiden.

**Abgeschlossen ist das Feature erst, wenn**:

- die Verträge S01–S19 und C01–C08 im beschriebenen Umfang funktionieren und ihre notwendigen Prüfungen ausgeführt sind;
- jede Phase unabhängig akzeptiert ist und eine frische finale Interaktionsprüfung vorliegt;
- keine produktive Logik oder Erwartung nach der letzten Codeabnahme unbemerkt geändert wurde;
- bisherige Einzel-/Projektjobs und alte Paketformate kompatibel bleiben;
- die stabile Toolingbasis enthalten ist, Ownership nicht verletzt wurde, notwendige kleine Guardkorrekturen separat begründet/getestet sind und die weiterhin partielle Include-Sicht ehrlich benannt ist;
- keine Schlüssel/produktiven Daten in Logs, Fixtures oder Commits enthalten sind;
- der Nutzer einen konkreten getesteten Beispielaufruf, Branch/SHAs, Reviewberichte und verbleibende Nicht-Ziele erhält.

## 11. Quellen und Herkunft

**Abrufstand:** 4. November 2026, nach Toolingabschluss und Main-Merge. Neu geprüft wurden Main-/Mergereferenz, vollständige Root-Tree-Identitäten gegenüber v1.2 sowie Status/P8-Bericht/Validierungsdatei. Die unveränderten Produktdateien tragen die zuvor gelesenen Codebefunde weiter; neue Ausführungstests wurden nicht vorgenommen. Eine Quelle mit Status/Review ist ein Repositorybeleg, kein hier wiederholter Testlauf. [BRANCH_REASSESSMENT.md](BRANCH_REASSESSMENT.md) trennt Beleg, Schlussfolgerung und neue Anweisung.

Für die nachfolgend angegebenen Repositorypfade gilt **Mainpin `0bfc8f0fccf43989d751d4f5490202311d200628`**. Der ursprüngliche akzeptierte Tooling-Produktpin `54bb123070fc7bd81aea58b4e1191beecf568cbf` dient der Provenienz; der sehr frühe Mainpin vor Tooling ist keine aktive Arbeitsbasis. Pfade im aktuellen Checkout bzw. mit `git show <PIN>:<PFAD>` lesen. Die unten genannten externen OpenCode-/DeepSeek-Seiten wurden für die Reviewmechanik erneut geprüft; installierte Version und Aliaszuordnung vor dem echten Prüflauf trotzdem erneut feststellen.

- **R1:** Rootregeln: `https://github.com/edigonzales/gretl-runtime/blob/0bfc8f0fccf43989d751d4f5490202311d200628/AGENTS.md`.
- **R2:** Bestehende Subprojekte: `docs/implementation/project-subprojects/README.md` am oben angegebenen Mainpin.
- **R3:** Runtimeverträge: `docs/architecture/runtime-contracts.md` am selben Mainpin.
- **R4:** Paketvertrag und Implementierung: `docs/implementation/job-packaging.md`, `gretl-packager/src/main/java/ch/so/agi/gretl/packager/JobCompiler.java`, `ProjectCompiler.java`, `gretl-job-runtime/src/main/java/ch/so/agi/gretl/job/JobJarMain.java` am Mainpin. Vor Änderungen die tatsächlichen neueren Dateien erneut prüfen.
- **R4a:** Gzip-/Projektpipeline: `examples/project-pipeline/prepare/job.groovy`, `examples/project-pipeline/checks/local/job.groovy`, `docs/reference/generated/task-gzip.adoc` am Mainpin.
- **R5:** Bestehende Test-/Modusqualifikation: `docs/implementation/testing.md` am Mainpin. Keine automatische Feature-Vollgatepflicht daraus ableiten; Abschnitt 1/8 regelt den aktuellen Nutzerauftrag.
- **R6:** Ursprünglicher Tooling-Sollentwurf: `docs/implementation/gretl-tooling/ARCHITECTURE.md`. Tatsächliche aktuelle APIs haben nach Prüfung Vorrang vor alten geplanten Namen.
- **R7:** Offizielle OpenCode-2-Kommandos: `https://opencode.ai/v2/docs/cli/commands/`.
- **R8:** Offizielle V2-Berechtigungen: `https://opencode.ai/v2/docs/permissions/`. Installierte Version/effektive Policy lokal prüfen; nicht V1-`bash`-/Permissionformate raten.
- **R9:** V2-API nur bei erforderlicher Backendintegration anhand der tatsächlichen offiziellen/installierten Schnittstelle; bevorzugt vorhandener geprüfter CLIweg.
- **R10:** DeepSeek-Zuordnung: `https://api-docs.deepseek.com/quick_start/pricing/`; 1017-Release: `https://api-docs.deepseek.com/news/news261017/`. APIalias `deepseek-flash` wird dort beim Abruf `DeepSeek-V4.1-Flash-1017` zugeordnet; beweglichen Alias vor tatsächlichem Review erneut prüfen.
- **R11:** Gradle ist Syntaxinspiration, keine zugesagte vollständige Gradlesemantik: `https://docs.gradle.org/current/userguide/plugins.html`.
- **R12:** Aktueller Status: `https://github.com/edigonzales/gretl-runtime/blob/0bfc8f0fccf43989d751d4f5490202311d200628/docs/implementation/gretl-tooling/STATUS.md`; ergänzend `implementation/P8.md` und `reviews/P8/attempt-1/validation.json` im selben Dokumentationsordner. Der P8-PASS gilt für den dort genannten Produkt-SHA.
- **R13:** Aktuelle Runtime-DSL: `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/GretlScript.java`.
- **R14:** Statische Syntax: `gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/syntax/GretlAstExtractor.java`.
- **R15:** Editierbarkeit/-planung: `gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/edit/EditabilityAnalyzer.java`, `SemanticEditPlanner.java`, `HeaderSourceEdits.java`.
- **R16:** Projektauflösung: `gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/workspace/ProjectResolver.java`.
- **R17:** Tatsächliche Modulgrenzen: `docs/implementation/module-boundaries.md`.
- **R18:** Graphprojektion: `gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/graph/JobGraphProjector.java`.
- **R19:** LSP-Vertrag: `gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/GretlLanguageServerApi.java` und `GretlProtocol.java`.
- **R20:** Extensionidentität und ausführbare Skripte: `vscode/gretl-vscode/package.json`; Testeinstiege in dessen `test/`.
- **R21:** Host/Modeler: `vscode/gretl-vscode/src/modeler/provider.ts` und `edit-plan.ts`.
- **R22:** Tatsächliche Reviewkopplung: `tools/gretl-tooling/review.mjs`, insbesondere `spec`, `captureRequirements`, `phaseCriteria`, `validateRequest`, `buildRequest`.
- **R23:** Main-Merge von Pull Request #2: `https://github.com/edigonzales/gretl-runtime/commit/0bfc8f0fccf43989d751d4f5490202311d200628`; veröffentlichter Toolingabschluss-/Archivpin `c0b65d75fead9e3e017665e01b3b5a69e62f64f9`.
- **R24:** Vollständiger nichtrekursiver Root-Tree-Abgleich, beide Antworten `truncated=false`: `https://api.github.com/repos/edigonzales/gretl-runtime/git/trees/54bb123070fc7bd81aea58b4e1191beecf568cbf` und `https://api.github.com/repos/edigonzales/gretl-runtime/git/trees/0bfc8f0fccf43989d751d4f5490202311d200628`. Ausser `docs/` identische Root-Einträge/Object-IDs; nicht aus der auf eine Teilmenge begrenzten Compare-Dateiliste abgeleitet.
- **Nutzervorlagen:** `CODEX_STARTPROMPT(10).md`, `EXECUTION_PLAN(10).md`, `OPENCODE_REVIEW_PROMPT(10).md`. Übernommen sind schrittweise Umsetzung, zielgerichtete Tests, Commit vor unabhängiger Prüfung, frischer Kontext, eigene Gegenprobe und autonome Fortsetzung. INTERLIS-/Python-Migrationsinhalte bleiben ausserhalb dieses Auftrags.
