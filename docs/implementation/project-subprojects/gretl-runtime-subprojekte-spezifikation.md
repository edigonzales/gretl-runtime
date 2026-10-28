# GRETL Runtime – Subprojekte und projektübergreifender Taskgraph

**Implementierungsspezifikation für einen LLM-Coding-Agenten**  
**Version:** 1.1 · **Stand:** 28. Oktober 2026  
**Repository:** https://github.com/edigonzales/gretl-runtime  
**Analysierter Ausgangscommit:** [`69a55e3ddde25b97325e4463a70d40f75a5e06d4`](https://github.com/edigonzales/gretl-runtime/commit/69a55e3ddde25b97325e4463a70d40f75a5e06d4) (`main`, 27. Oktober 2026)  
**Status:** Soll-Spezifikation; die beschriebenen neuen Funktionen sind am Ausgangscommit **nicht** implementiert.  
**Adressaten:** implementierender Coding-Agent, unabhängiger Review-Agent und Maintainer.  
**Änderung in Version 1.1:** Verbindliches Commit-/Worktree-/Review-Protokoll und präzisierte Übergabe-Prompts; fachliche Featureanforderungen unverändert.

> **Normative Lesart:** „MUSS“ bedeutet verbindlich; „SOLL“ darf nur mit dokumentierter Begründung abweichen. Vorgeschlagene Klassen- und Methodennamen sind der konkrete Implementierungsplan. Eine alternative Signatur ist nur zulässig, wenn Verhalten, Modulgrenzen und Tests unverändert erfüllt sind und die Abweichung im Phasenbericht begründet wird. Der Coding-Agent implementiert ausschliesslich den Umfang dieser Spezifikation. Eine unabhängige LLM-Review ersetzt niemals einen erfolgreichen Testlauf.

---

## 1. Zielbild und Grenzen

### 1.1 Ziel

Eine GRETL-Job-Sammlung soll sich ähnlich wie ein Gradle-Multiprojekt gliedern lassen. Jedes explizit eingebundene Unterverzeichnis enthält ein eigenständiges `job.groovy`. Die gemeinsame, streng deklarative `settings.gretl` definiert die Teilprojekte und die Default-Ziele. Tasks aus allen Teilprojekten werden zu **einer** `JobDefinition`, **einem** DAG, **einem** Run, **einem** `ResultStore` und **einem** Runbericht zusammengeführt.

Zwei Ausführungsformen müssen gleichwertig funktionieren:

1. **Script:** `gretl run <projektverzeichnis>` ohne Maven, Gradle und Kompilierungsschritt für den Jobautor.
2. **JAR:** `gretl package <deskriptor>` erzeugt ein einzelnes ausführbares, vorkompiliertes Job-Fat-JAR für das gesamte Projekt; `java -jar` führt denselben fachlichen DAG aus.

Die vorhandene Ausführung eines einzelnen `job.groovy` muss vollständig erhalten bleiben.

### 1.2 Leitentscheidungen

- **Ein gemeinsamer Builder:** Alle Skripte registrieren in derselben `JobDefinitionBuilder`-Instanz. Erst nach dem letzten Skript erfolgt genau ein `freeze()`.
- **Keine verschachtelten Jobläufe:** Unterprojekte sind Namensräume und Jobdefinitionsquellen, keine eigenständigen Runner innerhalb des Gesamtprojekts.
- **Ein gemeinsamer Runner:** `ExecutionPlanner`, `SequentialJobRunner`, `ResultStore`, Workspace-/Cancellation-/Logging-Mechanik bleiben prinzipiell unverändert.
- **`settings.gretl` ist rein deklarativ:** Eine statische, erlaubnislistenbasierte Groovy-AST-Prüfung; **kein** Ausführen der Projektdatei.
- **Explizite Teilprojekte:** Kein rekursives Durchsuchen nach `job.groovy`, keine impliziten Abhängigkeiten aus Verzeichnisstrukturen.
- **Qualifizierte IDs:** `:download:fetch`, `:validation:check`, `:import:load` sind die internen öffentlichen Task-IDs eines Projektlaufs.
- **Relativität nach Unterprojekt:** `file('sql/update.sql')` in `import/job.groovy` löst unter `import/sql/update.sql` auf.
- **Gleicher Code in beiden Modi:** Die Skript- und JAR-Quelle registrieren mit denselben Regeln und denselben qualifizierten IDs.
- **Keine neuen Produkt-Module ohne begründete Notwendigkeit:** Projektmodell und Parser liegen in `gretl-groovy`, sodass sowohl `gretl-job-runtime` als auch `gretl-packager` sie verwenden können, ohne Modulzyklen zu erzeugen.

### 1.3 Nichtziele

Kein Gradle/Maven innerhalb von GRETL-Jobs; keine Projekt-Buildplugins, kein Dependencyresolver für Teilprojekte, keine Parallelisierung, kein verteilter Scheduler, keine Wiederaufnahme/Retry-Engine, keine getrennten Classloader pro Unterprojekt, keine eigene globale Projektregistrierung, kein LSP-/VSCode-Ausbau, kein Wechsel der bestehenden Jobdateiendung von `.groovy` auf `.gretl`, keine Publisher-/XtfToGpkg-/Native-Image-Arbeit. Kein automatischer IMAGE-E2E-Test, solange die bestehende Produktqualifikation ihn nicht vorsieht.

### 1.4 Verbindliche Regressionen

Folgende Altverträge dürfen **nicht** geändert werden:

- `gretl run|check|plan pfad/job.groovy`, `--target taskname`, `parameter('name')`, `file(...)`, `path(...)`, `workPath(...)`, `result(...)`, `defaults ...`, `cleanup(...)`.
- `gretl package descriptor.json` mit `schemaVersion: 1` oder `2` und `entryScript: 'job.groovy'`.
- Die bestehende SPI `OperationProvider`, die Taskmodule, das Logging- und Exitcode-Schema sowie die Versionierung der Runtime.
- `dependsOn` zieht seine transitiven Vorgänger in den Plan; `after` gibt nur Reihenfolge vor und selektiert keinen Vorgänger.
- `result(...)` darf nur auf einen tatsächlichen `dependsOn`-Vorgänger zugreifen; `resultOptional(...)` darf fehlende deklarierte Abhängigkeiten nicht verschleiern.
- `gretl plan`, `gretl check` und `--help` führen keine Taskkonfiguration, keine Task-Action und kein Cleanup aus; die bekannte Möglichkeit von Groovy-Toplevel-Nebenwirkungen beim **Laden der Jobskripte** bleibt dokumentiert.
- `gretl package` kompiliert, aber führt **weder Job-Toplevelcode noch Tasks aus**.
- Das Job-Fat-JAR läuft ohne Quellcheckout, Maven oder GRETL-Distribution und materialisiert Ressourcen in seinem eigenen Run-Workspace; `keepWork` behält die bisherigen Regeln.

## 2. Ausgangslage im Code (Stand des angegebenen Commits)

| Bestehende Klasse / Datei | Ist-Zustand und relevante Grenze |
|---|---|
| `gretl-groovy/.../GroovyJobLoader.java` | `load(...)` und `loadCompiled(...)` laden jeweils **ein** Skript und geben danach dessen gefrorene `JobDefinition` zurück. |
| `gretl-groovy/.../GretlScript.java` | Registriert Tasks, Parameter, Defaults und Cleanup über `JobDslModel`. |
| `gretl-groovy/.../JobDslModel.java` | Hält je Skript einen Builder und lokale TaskRef-/Operation-Maps; `freeze()` ist derzeit skriptgebunden. |
| `gretl-runner/.../JobDefinitionBuilder.java` | Vergibt **eine** `definitionId`; `freeze()` löst namensbasierte Links auf. |
| `gretl-runner/.../JobDefinition.java` | Verwirft fremde `TaskRef`s; mehrere fertige Definitionen können deshalb nicht naiv zusammengefügt werden. |
| `gretl-runner/.../ExecutionPlanner.java` | Liefert bereits Targetselektion, Zyklusprüfung, stabile topologische Sortierung und den DAG. |
| `gretl-groovy/.../GroovyContextFacade.java` | `result(name)` löst über `JobDslModel.resolveTask(...)` auf; `file(path)` reicht bisher ohne Unterprojektpräfix an den Kontext weiter. |
| `gretl-runner/.../BaseContext.java` | `file(...)` bezieht sich auf **einen** `jobRoot`; `path(...)` auf das ursprüngliche CWD. |
| `gretl-job-runtime/.../JobSource.java` | Kapselt Quellen für Script- und JAR-Modus: `load`, `jobRoot`, `prepareRun`, `sha256` usw. |
| `gretl-job-runtime/.../ScriptJobSource.java` | Steht für genau eine Scriptdatei. |
| `gretl-job-runtime/.../JobRuntime.java` | Akzeptiert bei `jobCommand(JobOptions)` gegenwärtig nur eine reguläre Datei und erzeugt danach bereits einen gemeinsamen Ablauf. |
| `gretl-packager/.../PackageDescriptor.java` | Schemas 1/2, genau ein `entryScript`, Quellen/Ressourcen und ausgewählte Taskmodule. |
| `gretl-packager/.../JobCompiler.java` | `PACKAGE_MULTIPLE_SCRIPTS` verbietet mehrere Groovy-Skriptklassen im kompilierten Job; dies muss für Projektpakete aufgelöst werden. |
| `gretl-packager/.../JobPackageBuilder.java` | Schreibt genau eine `entryClass` in `META-INF/gretl/job.json`; fertigt bereits SHA- und Ressourcennachweise an. |
| `gretl-job-runtime/.../JobJarMain.java` | Kennt `JobMetadata` Schema 1 und `PackagedJobSource` mit einem Einstiegsskript. |
| `gretl-integration-tests/.../ArtifactJobRunner.java` | Qualifiziert echte Job-JARs mit separaten Prozessen, ist momentan auf `job.groovy` als Script-Einstieg ausgerichtet. |

**Zu beachten:** `gretl-runtime` selbst ist ein **Maven-Multimodulprojekt**. Das ist etwas anderes als die neu vorgesehenen **GRETL-Job-Subprojekte**. Für letztere wird kein `pom.xml` eingeführt.

**Massgebliche Lektüre vor dem ersten Commit:** `AGENTS.md`, `docs/spec/AGENTS.md`, `docs/spec/03-runtime-vertraege.md`, `docs/spec/04-job-dsl-und-custom-tasks.md`, `docs/spec/12-job-paketierung.md`, `docs/spec/14-java-api-vertraege.md`, `docs/spec/15-runner-klassendesign.md`, `docs/spec/16-logging-und-telemetrie.md`, `docs/implementation/module-boundaries.md`, `docs/implementation/testing.md`, `docs/implementation/job-artifact-tests.md`. Die neue Spezifikation ergänzt diese Verträge; bei unvereinbarer bestehender Norm ist der Konflikt **vor** einer Vertragsänderung im Phasenbericht offenzulegen.

## 3. Öffentliches Projektformat und DSL-Vertrag

### 3.1 Beispielstruktur

```text
datenimport/
├── settings.gretl
├── job-package.json                  # optional, nur für Paketierung
├── download/
│   ├── job.groovy
│   └── resources/
│       └── beispiel.txt
├── validation/
│   ├── job.groovy
│   └── models/
│       └── Beispiel.ili
└── import/
    ├── job.groovy
    └── sql/
        └── update.sql
```

`settings.gretl`:

```groovy
include 'download', 'validation', 'import'
defaults ':import:load'
```

`download/job.groovy`:

```groovy
parameter('url', required: true, description: 'Quell-URL')

task('fetch', HttpDownload) {
    url = parameter('url')
    output = workPath('download.zip')
}
defaults 'fetch' // gilt beim direkten Einzeldatei-Aufruf
```

`validation/job.groovy`:

```groovy
task('check', Extract, dependsOn: ':download:fetch') {
    archive = result(':download:fetch').file
    compression = 'ZIP'
    outputDirectory = workPath('data')
}
defaults 'check' // gilt beim direkten Einzeldatei-Aufruf nur ohne Cross-Project-Referenzen
```

`import/job.groovy` (als Beispiel für eigenständigen SQL-Schritt; eine echte DB-Konfiguration ist erst zur Laufzeit nötig):

```groovy
task('load', SqlExecutor, dependsOn: ':validation:check') {
    database = 'edit'
    scripts = [file('sql/update.sql')]
}
defaults 'load'
```

Bei einem Projektlauf wird ausschliesslich das Default-Ziel aus `settings.gretl` verwendet; die `defaults` in den Jobskripten bleiben für deren **separaten** Einzeldateilauf relevant. Ein **direkter Einzeldateiaufruf** von `validation/job.groovy` oder `import/job.groovy` schlägt bei deren absoluten projektübergreifenden Abhängigkeiten erwartungsgemäss mit einer unbekannten Taskreferenz fehl; für diese Unterprojekte ist der Projektaufruf mit `--target` zu benutzen. Einzeldateiaufrufe sind nur bei in sich abgeschlossenen Skripten unabhängig ausführbar. Die obige Kette zeigt Namensauflösung, nicht automatisch einen Datenaustausch zwischen `Extract` und `SqlExecutor`: Dieser muss im konkreten Job wie bisher über dokumentierte Ergebnisse, Dateien und Fachkonfiguration erfolgen. **Das auszuliefernde Beispiel unter `examples/` MUSS dagegen mit lokalen/synthetischen Daten ohne vorausgesetzte externe Datenbank erfolgreich lauffähig sein**; fachliche Datenbankoperationen gehören in die separaten Integrationstests.

### 3.2 Aufrufe

```bash
# Projekt: alle von :import:load benötigten Vorgänger
gretl run datenimport

# Projekt: nur Ziel und dessen tatsächliche dependsOn-Vorgänger
gretl run datenimport --target :validation:check

# Projekt: prüfen, Graph anzeigen
gretl check datenimport
gretl plan datenimport --target :import:load --graph mermaid

# Projektparameter: kanonisch qualifizierter Name
gretl run datenimport --param ':download:url=https://example.org/data.zip'

# Einzeldatei: vollständig kompatibel
gretl run datenimport/download/job.groovy --param 'url=https://example.org/data.zip'

# Paketieren und anschliessend ausführen
gretl package datenimport/job-package.json --output /tmp/datenimport.jar
java -jar /tmp/datenimport.jar --param ':download:url=https://example.org/data.zip'
```

- `run`, `check`, `plan` nehmen **Datei oder Verzeichnis** entgegen. Ein Verzeichnis ist nur dann ein Projekt, wenn dort `settings.gretl` liegt. Kein implizites Laden von `job.groovy` bei einem Verzeichnisargument ohne Settings.
- Das explizite `.../download/job.groovy` startet stets den **alten Einzeldateimodus**, selbst wenn die Datei in einem GRETL-Projekt liegt. Eine Referenz auf `:anderesProjekt:task` ist darin nicht automatisch auflösbar.
- `--target` verlangt im Projektmodus einen absoluten qualifizierten Tasknamen (führender Doppelpunkt), etwa `:import:load`. Ein unqualifizierter Projekt-CLI-Targetname ist ein klarer Bedienfehler, keine Mehrdeutigkeitsauflösung.
- `--param` sowie Parameter-JSON/-Konfiguration verwenden für Projektparameter ebenfalls voll qualifizierte Namen; der bisherige Präzedenzvertrag bleibt bestehen.
- Bei `gretl run <projekt> --help` werden alle Skriptdefinitionen geladen, aber keine Tasks ausgeführt. `gretl --version` und die globale Hilfe laden keine Jobs.
- Die `--graph`-Ausgabe muss qualifizierte Tasknamen darstellen. Sie bleibt eine reine Planungsausgabe.

### 3.3 Formale `settings.gretl`-Sprache

Es sind im ersten Release nur zwei Anweisungen zulässig:

```groovy
include 'download', ':etl:transform', 'import'
defaults ':import:load', ':etl:transform:check'
```

- `include` darf **höchstens einmal** vorkommen. Falls vorhanden, benötigt es mindestens einen String und legt Reihenfolge und Einbindung fest. Fehlt `include`, ist ein reines Root-Projekt zulässig, sofern `<root>/job.groovy` existiert.
- `defaults` ist optional, darf höchstens einmal vorkommen und verlangt mindestens einen voll qualifizierten Tasknamen. Fehlt `defaults`, benötigen `run`, `check` und `plan` **ein explizites `--target`**, weil der heutige gemeinsame Planpfad für alle drei Kommandos ein Ziel verlangt. Ohne beides gilt der vorhandene `MISSING_TARGET`/`NO_TARGETS`-Fehlervertrag.
- Es sind nur konstante einfache oder doppelt zitierte Stringargumente erlaubt, keine GStrings/Interpolation, Variablen, Ausdrücke, Closures, Imports, Zuweisungen, Methoden, Schleifen, Annotations oder sonstiger ausführbarer Code.
- `settings.gretl` wird syntaktisch mit dem vorhandenen Groovy-Parser bis zur **AST-Conversion** gelesen; nur AST-Knoten `ExpressionStatement` → `MethodCallExpression` mit implizitem Empfänger und `ArgumentListExpression` aus `ConstantExpression(String)` sind erlaubt. **Niemals** `run()`, `evaluate()`, Instanziieren der Settings-Klasse oder Kompilieren zu ausführbarem Bytecode.
- Kommentare und Leerzeilen sind erlaubt. Fehler enthalten Dateipfad, Zeile, Fehlercode und möglichst Spalte.
- Das Encoding ist UTF-8. Die Settingsdatei darf höchstens 256 KiB gross sein; eine Überschreitung ist ein definierter Fehler und kein stiller Abbruch.
- Nur die rootseitige Settingsdatei gilt. Unterprojekte enthalten in diesem Release **keine** weitere `settings.gretl` mit eigener Evaluierung.
- Die Settingsdatei selbst deklariert **keine** Tasks, Parameter oder Custom-Tasks. Die Parserrepräsentation erhält für `include` und `defaults` die Quellpositionen, damit auch unbekannte Defaults und doppelte Includes auf die tatsächliche Settings-Zeile verweisen.

**Begründung:** Die Settings-Syntax fühlt sich wie Gradle an, ohne einen zweiten unkontrollierten ausführbaren Konfigurationspfad zu schaffen. Die im Jobcode bereits dokumentierten Toplevel-Nebenwirkungen werden dadurch nicht rückwirkend verboten.

### 3.4 Projektpfade und Einbindung

- `include 'download'` ist gleichbedeutend mit `include ':download'`.
- `include ':etl:transform'` bedeutet `<projektroot>/etl/transform/job.groovy`.
- Ein Segment muss auf `[A-Za-z][A-Za-z0-9_-]*` passen. Die reservierten Werte `.`, `..`, ein leeres Segment, Schrägstriche, Backslashes und doppelte/abschliessende Doppelpunkte sind ungültig.
- `:` bezeichnet den optionalen Root-Job `<projektroot>/job.groovy`. Ist diese Datei vorhanden, wird sie automatisch **vor** allen `include`-Einträgen registriert. Die Root-Settingsdatei darf `include ':'` nicht verwenden.
- Elternverzeichnisse werden nicht implizit als Jobs eingebunden: `include ':etl:transform'` bedeutet nicht, dass `etl/job.groovy` existieren oder geladen werden muss.
- Jede eingebundene Nicht-Root-Projekteinheit **muss** eine reguläre `job.groovy` besitzen. Fehlende Dateien sind Fehler, keine übersprungenen Jobs.
- Jedes Projekt wird genau einmal geladen; redundante Namen (`download` und `:download`) sowie doppelte Einträge scheitern deterministisch.
- Reihenfolge: Root, soweit vorhanden, dann exakt die Reihenfolge in `include`; diese Reihenfolge bestimmt die stabilen Registrierungsindizes bei sonst unabhängigen Tasks.
- Projektwurzel und inkludierte Dateien müssen unter derselben aufgelösten realen Root liegen; Symlinks auf Settings-/Skriptpfaden oder in der eingebundenen Verzeichniskette sind abzulehnen. Der Parser prüft dies vor dem Laden. Keine Verzeichnissuche oberhalb der Root.
- Mindestens ein ausführbares Skript (Root oder Include) ist Pflicht. Eine leere `settings.gretl` ist nur bei vorhandenem Root-Job ein gültiges Root-only-Projekt; mangels Settings-Default muss der CLI-Aufruf dann ein Target nennen.

### 3.5 Task-, Ergebnis- und Parameter-Namensräume

| In `import/job.groovy` geschriebener Ausdruck | Bedeutung im Projektmodus |
|---|---|
| `task('load', ...)` | Registrierung als `:import:load` |
| `action('finish', ...)` | Registrierung als `:import:finish` |
| `dependsOn: 'prepare'` | lokale Referenz `:import:prepare` |
| `dependsOn: ':download:fetch'` | absolute Referenz auf anderes Unterprojekt |
| `after: ':validation:check'` | Reihenfolge, **keine** implizite Auswahl |
| `result('prepare')` | `result(':import:prepare')` |
| `result(':download:fetch')` | Ergebnis aus einem anderen Projekt, **nur** bei passender `dependsOn`-Kette |
| `parameter('schema', ...)` | Deklaration `:import:schema` |
| `parameter('schema')` in einer Task-Closure | Lesen von `:import:schema` |
| `parameter(':download:url')` in einer Task-Closure | Explizites Lesen des entsprechend deklarierten Parameters des anderen Projekts |
| `cleanup('cleanup', ...)` | Cleanup-Identität `:import:cleanup` |

- In einem Root-Job sind Namen ebenso qualifiziert, z. B. `:load` oder `:schema`.
- Unqualifizierte Strings innerhalb eines Skripts sind stets **lokal**; nur ein führender `:` ist absolut. Keine implizite Suche in Geschwisterprojekten.
- Lokale Task-, Parameter- und Cleanup-Namen im Projektmodus müssen `[A-Za-z][A-Za-z0-9_-]*` erfüllen, damit qualifizierte IDs eindeutig parsebar bleiben. Insbesondere ist `:` darin verboten. Alte Einzeldatei-Namen werden nicht nachträglich verboten oder umbenannt.
- `defaults` in einem Projekt-Jobskript werden **für den Projektlauf nicht zusammengeführt**; die Projekt-Defaults stehen nur in `settings.gretl`. Im direkten Einzeldateilauf behalten sie ihre alte Bedeutung.
- `cleanup` wird registriert und im gemeinsamen Run einmal mit derselben Semantik wie bislang ausgeführt; keine zusätzlichen Cleanup-Lebenszyklen pro Unterprojekt.
- Custom-Task-Symbole (`HttpDownload`, `SqlExecutor`, eigene `OperationProvider`) werden weiterhin über **eine** gemeinsam geladene `OperationRegistry` bereitgestellt. Keine Projekt-Custom-Task-Registrierung und kein `@Grab`.
- Globale `config('alias')`- und `secret('alias')`-Aliase bleiben global. Eine automatische Umbenennung dieser betrieblichen Aliase wäre inkompatibel.

### 3.6 Dateipfade und Laufzeitressourcen

| Aufruf aus `import/job.groovy` | Script-Projektlauf | Projekt-JAR |
|---|---|---|
| `file('sql/update.sql')` | `<root>/import/sql/update.sql` | `<materialisierte-root>/import/sql/update.sql` |
| `file('../shared/info.json')` | nach Normalisierung relativ zu `import/` | nur, wenn unter der paketierten Ressourcenwurzel enthalten; andernfalls fehlende Ressource |
| `file('/absolut/pfad')` | absolut | absolut, **nicht** ins JAR übersetzt |
| `path('eingabe.xtf')` | ursprüngliches Start-CWD | ursprüngliches Start-CWD |
| `workPath('temp.gpkg')` | eigener Task-Workspace | eigener Task-Workspace |
| `parameter('eingabe')` mit Typ `path` | ursprüngliches Start-CWD | ursprüngliches Start-CWD |

- Die Groovy-Fassade führt die projektrelative Übersetzung aus. Die Java-SPI `ContextView.file(...)` und der gemeinsame Runner müssen dafür **nicht** ihre bisherige globale `jobRoot`-Bedeutung ändern. Auch `ctx.file(...)` in einer Groovy-Closure benutzt die Fassade.
- Hier wird **keine allgemeine Dateisystemsandbox** versprochen. Direkte `java.nio.file.Files`-Aufrufe sind ohnehin möglich. Für portable Pakete sind alle benötigten statischen Jobdateien explizit im Paketdeskriptor aufzuführen.
- Paketressourcen bleiben read-only Fachbestand; fachliche Ausgaben gehören in `workPath` oder einen expliziten externen Zielpfad. `keepWork` gilt weiterhin für den ganzen Run.
- Die `file`-Herkunft muss für Taskkonfigurations-, Action-, `when`- und Cleanup-Closures stimmen. `ctx.run(...)` innerhalb von Actions erbt dieselbe Projekt-Fassade.

### 3.7 Identität, Telemetrie und Fehler

- Ein Projektlauf erhält **eine** `runId`, **einen** `jobName`, **einen** gemeinsamen Taskgraphen, **ein** Eventlog und **einen** Runbericht.
- `jobName` ist zunächst der Name des Projektroot-Verzeichnisses, sofern Runtimekonfiguration/Environment keinen anderen Jobnamen vorgibt. Kein neues `subprojectRunId`-Konzept.
- Qualifizierte Task-IDs müssen in `plan`, Hilfe, Resultaten, Runbericht und `gretl.task.*`-Events enthalten sein.
- Ein Projekt-Quellhash besteht aus den UTF-8-Bytes der `settings.gretl` und der explizit eingebundenen Jobskripte, deterministisch über normalisierte Pfade und Länge/Bytes gebildet. Grosse Eingabedaten oder beliebige Unterordner werden **nicht** vollständig gehasht. Der Quellhash ist kein Ersatz für den Hash des ausgeführten JARs oder dessen Dependency-Inventar.
- Ein Skriptladefehler soll **den Originalpfad und die Originalzeile** nennen; virtuelle, eindeutige Compiler-Dateinamen sind intern und dürfen die nutzbare Fehlersuche nicht verschlechtern.
- Fehlende Projektdatei, ungültige Settings, Symlink-Angriff, fehlende Include-Datei, unbekanntes/dupliziertes Target und nicht auflösbare Abhängigkeiten sind Vorbereitungs-/Definitionsfehler **vor** `run.started`. Exitcode und Berichtserzeugung folgen dem vorhandenen Vertragsmodell; es werden keine fiktiven Task-Events geschrieben.
- Erfolg/Fehler/`NO_DATA`/Cancellation und Cleanup bleiben unverändert; es wird kein projektweites Rollback versprochen.

## 4. Technische Zielarchitektur (Klassen und Methoden)

Die folgenden Signaturen illustrieren die verbindliche Verantwortung. Die Produktionsimplementierung nutzt weiterhin Java 25 und Groovy 6 und vermeidet neue Laufzeitabhängigkeiten ausserhalb des bereits vorhandenen Groovy-Compilers.

### 4.1 Neue Klassen in `gretl-groovy`

**Package:** `ch.so.agi.gretl.runtime.groovy.project`

#### `ProjectPath` (immutable Value Object)

```java
public final class ProjectPath {
    public static ProjectPath root();
    public static ProjectPath parseInclude(String text);
    public static ProjectPath parseQualified(String text);
    public String canonical();              // ":", ":import", ":etl:transform"
    public Path relativeDirectory();        // Path.of("") oder etl/transform
    public String qualifyLocal(String name); // load -> :import:load
    public String resolveReference(String name); // lokal oder absolut
    public boolean isRoot();
}
```

**MUSS:** Segmente validieren, führenden Doppelpunkt normalisieren, Root gesondert behandeln, `equals`/`hashCode` stabil implementieren, Betriebssystemseparatoren nur bei `relativeDirectory()` zulassen. Für `parseQualified` darf kein unqualifizierter Task-/Parametername akzeptiert werden. Der Value Object kennt kein Dateisystem und führt keine IO aus.

#### `ProjectEntry` und `GretlProject` (immutable Records)

```java
public record ProjectEntry(ProjectPath projectPath, Path scriptFile) { }

public record GretlProject(
        Path root, Path settingsFile,
        List<ProjectEntry> entries,
        List<String> defaultTargets) { }
```

**MUSS:** defensive Kopien und normalisierte absolute Pfade; die Root-Skriptdatei ist bei Vorhandensein die erste `ProjectEntry`. `GretlProject` ist **keine** `JobDefinition` und enthält weder Runtime-Zustand noch Task-Ergebnisse.

#### `GretlProjectParser`

```java
public final class GretlProjectParser {
    public GretlProject parse(Path projectDirectory);
    // Package-private, für Parser-Unit-Tests und die unveränderte AST-Validierung:
    ProjectSettings parseSettings(Path settingsFile, String source);
    List<ProjectEntry> resolveEntries(Path root, List<ProjectPath> included);
    void validateContainedRegularFile(Path root, Path candidate, String role);
}
```

**Implementationsschritte:**

1. Projektroot und `settings.gretl` statisch und pfadsicher prüfen.
2. Groovy-AST ohne Scriptausführung erzeugen; erst danach mit Allowlist ausschliesslich `include` und `defaults` interpretieren.
3. Verbotene AST-Knoten samt SourceLocation zurückweisen, statt sie zu ignorieren.
4. Include-Namen kanonisieren, Duplikate und Pfadtraversal erkennen.
5. Auf `job.groovy`-Existenz und Symlinks prüfen; Root-Job optional aufnehmen.
6. Default-Ziele auf Syntax prüfen; deren **Existenz** erst nach vollständiger Taskregistrierung prüfen.
7. Ein unveränderliches `GretlProject` in deterministischer Reihenfolge zurückgeben.

Verwende eine kleine interne `ProjectSettings`-Struktur oder ein package-private Record. Kein allgemeiner „Groovy-Sandbox“-Framework-Aufbau. Eine AST-Prüfung allein rechtfertigt keinen Sicherheitsanspruch für später ausgeführten Jobcode.

#### `ProjectSourceNames`

```java
public final class ProjectSourceNames {
    public static String virtualGroovyName(ProjectPath projectPath);
    public static String entryClassName(ProjectPath projectPath);
}
```

**MUSS:** Jede `job.groovy`, auch bei identischem Originaldateinamen, erhält einen deterministisch eindeutigen virtuellen Skriptdateinamen bzw. daraus entstehenden Classnamen. Empfehlung: eindeutiger, gültiger Java-Bezeichner mit vollständigem SHA-256 der kanonischen Projekt-ID; **nicht** bloss `:` durch `_` ersetzen (`:a_b` und `:a:b` können sonst kollidieren). Diese Zuordnung muss im Script- und JAR-Modus identisch sein. Die tatsächliche Groovy-6-API `CompilationUnit.addSource(String name, String scriptText)` erlaubt virtuelle Quellen; für den Scriptmodus ist die entsprechende `GroovyShell`-Variante mit eigenem Quellenamen zu verwenden. Der Originalpfad bleibt separat in der Quellzuordnung gespeichert.

#### `ProjectTaskIndex` (geteilte Registrierungsreferenzen)

```java
public final class ProjectTaskIndex {
    public void ensureAvailable(String qualifiedId, SourceLocation source);
    public void register(String qualifiedId, TaskRef<?> ref,
                         OperationType<?, ?> operationOrNull,
                         SourceLocation source);
    public TaskRef<?> require(String qualifiedId, String requestingTask);
    public Optional<OperationType<?, ?>> operation(String qualifiedId);
    public List<String> taskIds();
}
```

**MUSS:** Alle `JobDslModel`-Instanzen desselben Projektlaufs teilen diese Registry. In der lokalen Map gespeicherte `TaskRef`s müssen zu **demselben** `JobDefinitionBuilder` gehören. Doppelte kanonische IDs sind **vor** dem `builder.register(...)` mittels `ensureAvailable(...)` mit den beiden Originalquellen abzulehnen; kein „last wins“. Die Registry lebt **nur** während des Definitionsladevorgangs und der gespeicherten Closures dieses Runs, nie global/statisch. Operationstypen müssen für `result(':anderesProjekt:task').feld` bekannt bleiben. Ein `null`-Operationstyp steht für eine `action` und darf kein typisiertes OperationResult vortäuschen.

#### `ProjectSourceLocation` / Quellzuordnung

Klein halten: Eine unveränderliche Map aus virtuellem Skriptnamen → originalem `Path` genügt. Die `SourceLocations`-Utilities sollen Überladungen erhalten, die diesen Namen kennen, z. B. `current(Path original, String virtualName)` und `from(Throwable failure, Path original, String virtualName)`. Die **bestehenden** Single-Script-Signaturen bleiben bestehen. Stackframes und Compilerdiagnosen müssen auf Originalpfad/-zeile zurückgeführt werden.

### 4.2 Bestehende Klassen in `gretl-groovy` erweitern

#### `GroovyJobLoader`

```java
public JobDefinition loadProject(GretlProject project,
                                 DefinitionEnvironment environment);

public JobDefinition loadCompiledProject(
        List<CompiledProjectScript> compiledScripts,
        List<String> qualifiedDefaultTargets,
        DefinitionEnvironment environment,
        ClassLoader classLoader);

// Nur intern:
void registerScript(Script script, JobDslModel model);
```

`CompiledProjectScript` ist ein leichtes, Groovy-modul-eigenes Record mit `ProjectPath`, `entryClass`, originalem relativen Scriptpfad und virtuellem Quellenamen. Es referenziert **keine** Klasse aus `gretl-packager` oder `gretl-job-runtime`.

**Ablauf `loadProject`:** Gemeinsamen `JobDefinitionBuilder(project.root())` und `ProjectTaskIndex` erzeugen; einen `GroovyClassLoader`/Compilerkontext für die Projektdefinition verwenden; für jede `ProjectEntry` ein separates `Binding`, `GretlScript` und `JobDslModel` mit gleicher Builder-/Indexinstanz vorbereiten; Skript mit eindeutigem virtuellen Namen parsen; Toplevel einmal ausführen; **nicht** pro Skript einfrieren; Settings-Defaults qualifizieren und registrieren; **einmal** `builder.freeze()`; die finale `JobDefinition` liefern. Der Classloader muss für die Lebensdauer der gespeicherten Groovy-Closures gültig bleiben (nicht zu früh schliessen). Keine Klassen oder Top-Level-Bindings eines Teilprojekts dürfen unbeabsichtigt in ein anderes lecken.

**Ablauf `loadCompiledProject`:** Derselbe Registrierungsalgorithmus; die in den versionierten JAR-Metadaten enthaltenen `qualifiedDefaultTargets` werden **nach der vollständigen Registrierung vor dem gemeinsamen `freeze()`** übernommen. Scriptklassen werden via `Class.forName(entryClass, ..., classLoader)` geladen statt kompiliert. Dieselben Namensräume/Quellzuordnungen/Default-Regeln. Bei 0 oder doppelten `entryClass` ein Definitionsfehler. Die vorhandenen Methoden `load(Path,...)` und `loadCompiled(String,...)` bleiben unangetastet nutzbar.

**Wichtiger Compiler-Nachweis:** Zwei Teilprojekte mit je `job.groovy`, abweichendem Toplevel-Code und unterschiedlichen Tasks müssen nach dem Laden beide eigenen Definitionen besitzen; es darf weder `job.class` überschrieben noch eine Skriptklasse versehentlich zweimal benutzt werden.

#### `JobDslModel`

Vorgesehene Erweiterung des Konstruktors (der bisherige Konstruktor bleibt für Single-Script-Aufrufe erhalten):

```java
JobDslModel(DefinitionEnvironment environment,
            JobDefinitionBuilder sharedBuilder,
            Path originalScript,
            ProjectPath scope,
            ProjectTaskIndex sharedIndex,
            String virtualSourceName);

String qualifiedTaskId(String name);
String qualifiedParameterId(String name);
String qualifiedCleanupId(String name);
TaskRef<?> resolveTask(String name, String requesterId);
Optional<OperationType<?, ?>> taskOperation(String name);
String resourceRelativePath(String originalPath);
boolean projectMode();
```

- In Projekten werden nur kanonische IDs in `JobDefinitionBuilder` registriert; die Modellinstanz behält ihren lokalen Scope für Ausführung/`result()`/`parameter()`/`file()`.
- `resolveTask` qualifiziert lokale Strings bzw. akzeptiert absolute Referenzen und fragt den gemeinsamen `ProjectTaskIndex` ab. Es muss auch Vorwärtsreferenzen erlauben, weil die Auflösung zur Ausführung bzw. zu `freeze()` erfolgt.
- Ein `TaskRef` aus einer **anderen** geladenen Projektdefinition ist weiterhin verboten. Der gemeinsame Builder darf fremde Referenzen nicht still übernehmen.
- Der alte Single-Script-Konstruktor benutzt unverändert lokale, unqualifizierte IDs, sonst keine Verhaltensänderung.

#### `GretlScript`

Die Signaturen der öffentlichen DSL bleiben gleich. Nur die interne Namensbehandlung ändert sich im Projektmodus:

- `task(...)`/`action(...)`: lokales `id` validieren, in `model.qualifiedTaskId(id)` umsetzen, als `TaskRegistration` registrieren und in geteiltem Index hinterlegen.
- `parameter(Map,...,String)`: lokale Deklaration qualifizieren und über den gemeinsamen Builder registrieren; Parametermetadaten (Typ/Default/Beschreibung) unverändert.
- `defaults(...)`: in Single-Script weiter an Builder übergeben; im Projektmodus **nicht** an Builder übergeben. Das Script darf sie wie bisher deklarieren, die Settings entscheiden über die Gesamtausführung.
- `cleanup(...)`: im Projektmodus Name qualifizieren, Body mit seinem ursprünglichen `JobDslModel` speichern.
- `links(...)`: jeden String lokal oder absolut qualifizieren; übergebene `TaskRef`s unverändert weiterreichen und durch den bestehenden Builder validieren lassen.

**Kein zweiter separater Dependencyplaner:** `JobDefinitionBuilder.freeze()` und `ExecutionPlanner.plan(...)` lösen die qualifizierten Links auf und prüfen Zyklen wie bisher. Falls nötig darf eine kleine, package-private API zum Registrieren kanonischer Defaults ergänzt werden, aber keine neue DAG-Engine.

#### `GroovyContextFacade`

```java
public Object parameter(String name);           // qualifiziert bei Projektmodus
public <T> T parameter(String name, Class<T> type);
public Path file(String path);                  // relativer Unterprojektpfad
public Object result(String name);              // qualifizierter Ref via Index
public Optional<Object> resultOptional(String name);
```

Die Typed-`TaskRef`-Überladungen bleiben wie bisher. `wrapResult(...)` muss den **qualifizierten** Referenznamen zur OperationType-Suche verwenden; insbesondere das typisierte `result(':download:fetch').file` muss funktionieren. `file()` und `ctx.file()` sowie `parameter()` in allen Action-/Task-/Cleanup-Scope-Varianten erhalten dieselbe Projektsemantik. `path`, `workPath`, `config`, `secret`, `ctx.run`, Logging und Cancellation bleiben unverändert.

#### `SourceLocations`

Erweitere ausschliesslich die Quellzuordnung; bestehende public Signaturen bleiben für Single-Script und Tests erhalten. Prüfe insbesondere `GretlScript`, `GroovyTaskAdapter`, `TaskConfigurationScope` und die Fehlerverpackung in `GroovyJobLoader`, damit Compile-, Toplevel-, `when`-, Task- und Cleanup-Fehler auf die ursprüngliche `job.groovy` und Zeilennummer zeigen.

### 4.3 Klassen in `gretl-job-runtime`

#### Neue `ProjectJobSource implements JobSource`

```java
public final class ProjectJobSource implements JobSource {
    public ProjectJobSource(Path projectDirectory, Locale locale);
    public Path displayFile();            // settings.gretl
    public Path jobRoot();                // Projektroot
    public Path prepareRun(Path runRoot); // Projektroot unverändert
    public String jobName();             // Verzeichnisname
    public String sha256();              // deterministischer Projektquellhash
    public JobDefinition load(OperationRegistry registry,
                              Path startCwd, UserMessageSink sink);
}
```

- `load(...)` nutzt `GretlProjectParser` und `GroovyJobLoader.loadProject(...)`.
- Nur Definitionsquellen werden statisch gehasht, nicht komplette Daten- oder Ressourcenverzeichnisse.
- Pfad-/Settingsfehler sind bekannte Start-/Definitionsfehler und werden nicht als undifferenzierter interner Runtimefehler gemeldet.

#### `JobRuntime`

Ändere den existierenden Datei-Selektor `jobCommand(JobOptions)`:

```java
private JobSource sourceFor(Path input, Locale locale);
```

- `Files.isRegularFile(input)` → `ScriptJobSource` (bisheriges Verhalten).
- `Files.isDirectory(input)` **mit** Settings → `ProjectJobSource`.
- Verzeichnis ohne Settings, nicht existierender Pfad und sonstige Pfadtypen → klare Startdiagnose.
- Danach unverändert `jobCommand(JobSource, JobOptions)`, `loadDefinition`, `ParameterResolver`, `ExecutionPlanner`, `run` mit einem `RunInput` und einem `SequentialJobRunner`.
- Verlange im Projektmodus voll qualifizierte `--target`-Werte **vor** dem Planen; der direkte Einzeldateimodus darf dieselbe Prüfung nicht erhalten. Settings-Defaults sind bereits qualifiziert.

#### `JobOptions` und `CliOptions`

Die öffentlichen Records möglichst nicht um zusätzliche Felder aufblasen. Die bestehende `Path job` repräsentiert bereits Datei oder Verzeichnis. Nur Parsing-/Hilfe-Tests anpassen. `GretlCli` soll keine zweite Projekt-Runner-Logik enthalten.

### 4.4 Unveränderte Kernklassen

**Erwartete Null- oder Minimaländerung:** `gretl-api`, Fach-Taskmodule, `ExecutionPlanner`, `SequentialJobRunner`, `ResultStore`, `RunInput`, `WorkspaceManager`, `ParameterResolver`, `OperationRegistry`, grundlegende Logging-Klassen. Falls die Umsetzung diese Klassen wesentlich anfassen muss, im Phasenbericht architektonisch begründen und Regressionstests speziell dafür ergänzen. **Keine** fremden `JobDefinition`s zusammenkopieren und keine künstlichen Runner-in-Runner-Tasks schaffen.

## 5. Paketierung – ein JAR für das Gesamtprojekt

### 5.1 Neuer Paketdeskriptor `schemaVersion: 3`

Schemas 1 und 2 gelten **unverändert** für Einzeldateijobs. Schema 3 ist explizit für Projekte:

```json
{
  "schemaVersion": 3,
  "name": "datenimport",
  "version": "1.0.0",
  "runtimeVersion": "0.1.0-SNAPSHOT",
  "settingsFile": "settings.gretl",
  "sources": [],
  "resources": [
    "download/resources",
    "validation/models",
    "import/sql"
  ],
  "taskModules": ["io", "database", "ilivalidator"],
  "standardFunctions": false,
  "dependencies": []
}
```

**Vertrag:**

- Der Deskriptor liegt im Projektroot. In Schema 3 ist `settingsFile: 'settings.gretl'` Pflicht und **nur** dieser feste Dateiname erlaubt; `entryScript` ist verboten.
- In Schemas 1/2 ist `entryScript` weiterhin Pflicht; `settingsFile` ist verboten. Schema-2-Modulauswahl und Dependency-Katalog bleiben unverändert.
- Alle `job.groovy`-Einstiegsskripte werden aus `settings.gretl` explizit ermittelt, nicht zusätzlich in `sources` deklariert.
- `sources` enthält allenfalls **zusätzliche** Groovy-Helferquellen; `resources` enthält die expliziten, zu materialisierenden Fachdateien relativ zur Projektroot. Keine automatische Aufnahme eines vollständigen Repositorys oder täglicher Dateneingaben.
- Symlinks, unsichere/ausbrechende Pfade, Dateikollisionen und fehlende Ressourcen führen wie bisher zu Fehlern; keine implizite Exfiltration ausserhalb der Projektroot.
- Keine Maven-Abhängigkeit beim Start des verpackten Jobs. Maven-Auflösung bleibt ausschliesslich eine Authoring-Aktion des Packagers.

### 5.2 `PackageDescriptor`

Vertragsänderung:

```java
boolean projectPackage();
Path settingsPath();               // nur Schema 3
Path entryPath();                  // nur Schemas 1/2
```

Der Agent darf diese Methoden als typisierte Varianten/sealed Descriptor-Form gestalten, wenn dadurch Fehlerzustände besser ausgeschlossen und die bestehenden Konstruktoren/Tests kompatibel bleiben. **MUSS:** Schemas 1/2 unverändert akzeptieren, unbekannte Felder weiter zurückweisen, Schema 3 isoliert validieren. Keine Vermischung mit dem **internen** `job.json`-Metadatenschema.

### 5.3 Neuer `ProjectCompiler` und kompilierte Projektstruktur

```java
public final class ProjectCompiler {
    public CompiledProject compile(GretlProject project,
                                   PackageDescriptor descriptor,
                                   List<Path> selectedClasspath);
}

public record CompiledProject(
        List<CompiledProjectEntry> entries,
        Map<String, byte[]> classes,
        List<JobCompiler.SourceFile> sources,
        String projectSha256) { }

public record CompiledProjectEntry(
        ProjectPath projectPath,
        String relativeScriptPath,
        String virtualGroovyName,
        String entryClass) { }
```

**MUSS:** Alle Root-/Unterprojekt-Skripte und Helper-Quellen in einem kontrollierten Groovy-6-CompilationUnit mit **identischem qualifiziertem Classpath** wie der vorhandene Einzeljobcompiler kompilieren. Die SourceNames unterscheiden die ursprünglich identischen `job.groovy`. Für jede deklarierte `ProjectEntry` muss **genau eine** `GretlScript`-Subklasse als Einstiegsklasse nachgewiesen werden. Helper-Quellen dürfen keine zusätzlichen heimlichen Job-Einstiegsskripte beisteuern; doppelte Klassen und gleichnamige Ressourcen scheitern mit Herkunft.

Der Compiler führt **keinen** Groovy-Scriptbody aus. Vorhandene Isolation bei Dependencies und verbotene Groovy-`@Grab`-Mechanismen bleiben wirksam. Der Compiler bildet eine Quellort-Tabelle, sodass selbst bei virtuellem SourceName das ursprüngliche `import/job.groovy:Zeile` berichtbar ist. `JobCompiler` für Schemas 1/2 bleibt funktional unverändert. Gemeinsame Kompiliermechanik darf intern extrahiert, aber nicht doppelt und widersprüchlich implementiert werden.

### 5.4 `JobPackager` und `JobPackageBuilder`

```java
// In JobPackager.packageJob(...) nach Descriptor-/Versionsprüfung:
if (descriptor.projectPackage()) {
    GretlProject project = new GretlProjectParser().parse(descriptor.baseDirectory());
    CompiledProject compiled = new ProjectCompiler().compile(project, descriptor, selectedClasspath);
    return builder.buildProject(descriptor, compiled, output);
}
// Bestehender Einzeljobzweig bleibt bestehen.
```

`JobPackageBuilder` bekommt etwa:

```java
public PackageResult buildProject(PackageDescriptor descriptor,
                                  CompiledProject project,
                                  Path output);
```

Beide Pfade teilen sich **dieselbe** kollisionsprüfende JAR-Assembly, `META-INF/services`-, Groovy-Metadaten-, Lizenz-, Provider-, Dependency- und Checksum-Logik. Kein zweiter, schwächer geprüfter Project-Shade-Prozess. Das JAR enthält alle kompilierten Skriptklassen, explizite Helper, Ressourcen unter `META-INF/gretl/job-resources/<projektpfad>/...` und ein vollständiges Ressourceninventar.

### 5.5 Interne Metadatenversion für Projekt-JARs

**Alte Einzeljob-JARs:** `META-INF/gretl/job.json` **Schema 1** mit `entryClass`/`entryScript` bleibt lesbar.

**Neue Projekt-JARs:** `META-INF/gretl/job.json` **Schema 2**, z. B.:

```json
{
  "schemaVersion": 2,
  "name": "datenimport",
  "version": "1.0.0",
  "runtimeVersion": "0.1.0-SNAPSHOT",
  "javaFeatureVersion": 25,
  "project": true,
  "settingsFile": "settings.gretl",
  "defaultTargets": [":import:load"],
  "entries": [
    {
      "projectPath": ":download",
      "entryScript": "download/job.groovy",
      "entryClass": "<vom-Compiler-ermittelter-eindeutiger-Klassenname>"
    },
    {
      "projectPath": ":validation",
      "entryScript": "validation/job.groovy",
      "entryClass": "<...>"
    },
    {
      "projectPath": ":import",
      "entryScript": "import/job.groovy",
      "entryClass": "<...>"
    }
  ],
  "resourceRoot": "META-INF/gretl/job-resources/",
  "resourceFiles": ["import/sql/update.sql"],
  "sourceFiles": ["settings.gretl", "download/job.groovy", "validation/job.groovy", "import/job.groovy"],
  "projectSha256": "<64-stelliger-Hexwert>",
  "dependencyInventory": "META-INF/gretl/dependencies.json"
}
```

Das obige JSON ist **schematisch**; tatsächliche Ressourcen-/Source-Dateien und Classnamen ergeben sich aus dem Projekt. Alte `entryClass`, `entryScript`, `scriptSha256` sind im Schema 2 nicht gleichzeitig verpflichtend. `dependencyInventory` behält sein bestehendes Format und wird nicht von der Projekt-Metadatenversion umnummeriert.

- Strikter Reader: unbekannte Metadatenfelder nach bestehendem Stil behandeln, Pflichtfelder/Datentypen prüfen, doppelte Projektpfade oder Einstiegsklassen ablehnen und relative Pfade/`resourceFiles` gegen Traversal prüfen.
- Settings und alle Einstiegsskripttexte dürfen für Provenienz im JAR unter `META-INF/gretl/sources/` liegen; sie werden **nicht** beim Start als auszuwertende Originalskripte benötigt.
- `projectSha256` muss Quellidentität deterministisch repräsentieren; der Hash des gebauten JARs bleibt davon unabhängig und wird wie bisher als eigene Artefaktidentität geprüft.

### 5.6 `JobJarMain` / verpackter Projekt-`JobSource`

Die bestehende `JobMetadata`-Verarbeitung wird um Schema 2 erweitert. Eine neue `PackagedProjectJobSource` (ggf. verschachtelt wie `PackagedJobSource`) implementiert `JobSource`:

```java
public JobDefinition load(OperationRegistry registry,
                          Path startCwd, UserMessageSink sink);
public Path prepareRun(Path runRoot) throws IOException;
public String sha256();
public Path displayFile();
```

- `load` verwendet `GroovyJobLoader.loadCompiledProject(...)` mit Liste der Einstiegsklassen **und den Metadaten-Defaults**, **ohne** Groovy-Quellkompilation.
- `prepareRun` nutzt die bestehende sichere Ressourcenmaterialisierung, behält relative Projektverzeichnisse und die Workspace-Retention bei.
- `--version` lädt weiterhin kein Skript und materialisiert keine Ressourcen; `--help`/`--plan` materialisieren ebenfalls keine Ressourcen.
- Vor Ausführung wird die einmalige Projektdarstellung geladen, parameterisiert, geplant und mit dem **gleichen** `SequentialJobRunner` ausgeführt.
- Das neue Paket darf aus einem fremden CWD mit leerem HOME und ohne `mvn` im PATH laufen. Bei fehlender Ressource scheitert der tatsächliche Run mit klarer Ursache und ohne erfundene erfolgreiche Facharbeit.

### 5.7 Testadapter und IMAGE-Vorbereitung

`ArtifactJobRunner` akzeptiert in Projektfixtures künftig einen **Projekt-Verzeichniseinstieg** statt fest `job.groovy`. Bevorzugte minimalinvasive Änderung: ein optionales Eingabefeld `scriptInput` oder `sourceKind` in `Request` plus rückwärtskompatibler Convenience-Konstruktor mit Default `job.groovy`. Keine Kopie des Prozess-/Hash-/Log-/Timeout-Verhaltens in einen zweiten Runner. `ArtifactJobRunner.prepare(...)` verwendet bei Projektfixtures den bereitgestellten Schema-3-Deskriptor statt einen Schema-1-Deskriptor zu generieren. `PackageArtifactAssertions` unterscheidet Metadatenschemas 1 und 2.

Die bestehende IMAGE-Vorbereitung und Tests dürfen nicht beschädigt werden. Ein realer Projekt-IMAGE-Qualifikationslauf ist **kein** Teil dieser Änderung; falls der Adapter erweitert wird, darf er weder still auf JAR zurückfallen noch ein nicht vorhandenes Image als erfolgreich qualifiziert melden.

## 6. Fehlerkatalog und Behandlung

Fehler erhalten stabile maschinenlesbare Codes, präzise deutsche/englische Meldungen, Originalpfade und Ursachen. Bereits existierende Codes werden wiederverwendet, soweit sie denselben Sachverhalt bedeuten. Vorschlag für projektspezifische Codes:

| Fehlercode | Auslöser | Erwartete Behandlung |
|---|---|---|
| `PROJECT_SETTINGS_MISSING` | Verzeichnis ohne `settings.gretl` | Startfehler, kein Run |
| `PROJECT_SETTINGS_INVALID` | Syntaxfehler oder ungültige AST-Struktur | Dateipfad und Zeile, kein Run |
| `PROJECT_SETTINGS_UNSUPPORTED` | Beliebiger ausführbarer/unkannter AST-Knoten, zusätzliche Methoden | Kein Ausführen, klare Diagnose |
| `PROJECT_INCLUDE_INVALID` | Ungültiger Projektname, `..`, Slash, leere Segmente | Kein Run |
| `PROJECT_INCLUDE_DUPLICATE` | Dasselbe Include nach Kanonisierung mehrfach | Beide Werte/Positionen nennen |
| `PROJECT_ENTRY_MISSING` | Einbezogenes `job.groovy` fehlt | Name und erwarteter Pfad |
| `PROJECT_PATH_UNSAFE` | Symlink/Root-Escape bei manifest- oder paketbezogenen Quellen | Vor Ausführung/Paketierung abbrechen |
| `PROJECT_EMPTY` | Weder Root-Skript noch Include-Skript | Kein Run |
| `PROJECT_TARGET_INVALID` | Projekt-CLI-Target nicht absolut qualifiziert | Beispiel `:import:load` zeigen |
| `PROJECT_DUPLICATE_SCRIPT_CLASS` | Nicht-eindeutige virtuelle/kompilierte Entry-Klasse | Verpacken/Laden verhindern |

Weitere unbekannte Tasks und Parametersituationen verwenden möglichst `UNKNOWN_TASK_REFERENCE`, `UNKNOWN_DEFAULT_TARGET`, `UNKNOWN_PARAMETER`, `UNDECLARED_RESULT_DEPENDENCY`, `DEPENDENCY_CYCLE` und bestehende Codes. **Nicht** pauschal alle Projektfehler zu `INTERNAL_ERROR` degradieren. Negative Tests prüfen Codes und fachliche Ursache, nicht nur `exitCode != 0`.

## 7. Verbindliche Teststrategie

### 7.1 Allgemeine Regeln

- Tests sind **Produktionsaufrufe gegen echten Produktcode**. Keine reine Mock-Testreihe als Ersatz für echtes `gretl run`, `gretl package` und `java -jar`.
- Jede Phase ergänzt gezielte Unit-/Komponenten-/Integrationsfälle und führt anschliessend die bestehende normale Gesamtsuite aus: `./mvnw -B clean verify` mit **JDK 25**.
- Tests dürfen nicht still `@Disabled`, `Assumptions` oder Skip-Flags verwenden, um fehlende Fähigkeiten als grün auszuweisen. Ein unbegründet grün ausgeführter No-Op gilt als Fehler.
- Für den finalen vollständigen Qualifikationsnachweis gilt zusätzlich `./mvnw -B -Pgeotools-probe,backing-services clean verify`. Fehlende erforderliche Docker-/Dienstvoraussetzungen blockieren die **finale** Freigabe; sie werden nicht als PASS interpretiert.
- Die bestehende enge JAR/IMAGE-Grenze bleibt verbindlich: echter `java -jar`-Prozess, Prüfung unabhängig erzeugter Dateien und Reports, unveränderlicher gestarteter JAR-Hash. Für IMAGE nur vorhandene Adaptertests, sofern nicht ausdrücklich separat beauftragt.
- Jede Phase enthält auch Negativtests, Rückwärtskompatibilität, deterministische Verzeichnisreihenfolge, Windows-taugliche Pfadverarbeitung und mindestens einen testbaren Fehler mit konkreter Originalquelle.
- Nach jeder Phase MUSS Agent A einen **festen Implementierungscommit** erzeugen. Agent B prüft in einem separaten Worktree genau diese SHA sowie den **vollständigen Phasen-Diff** von `phaseBaseSha` bis `candidateSha` und reproduziert die erforderlichen Tests unabhängig. Fehlende Pflichtprüfungen ergeben `BLOCKED`, niemals einen angenommenen `PASS`. Siehe Abschnitt 9.

### 7.2 Testfallmatrix

**Projektmodell / Parsing (Phase 1)**

| ID | Test / Orakel |
|---|---|
| `PRJ-001` | `include 'a', ':b:c'` erzeugt kanonische Pfade und deterministic order; Root-`job.groovy` wird zuerst aufgenommen. |
| `PRJ-002` | Kein Root-Job, zwei Includes; vorhandene verschachtelte Datei `b/c/job.groovy` wird korrekt erkannt. |
| `PRJ-003` | Leere Include-Aufrufe, doppelte, fehlende und ungültige Includes scheitern mit richtigem Fehlercode und Originalposition. |
| `PRJ-004` | Settings mit `new File(...).text=...`, `println`, `System.exit`, Variablen oder GStrings werden abgelehnt; Markerdatei bleibt **nicht vorhanden**. |
| `PRJ-005` | `include ':a:b'` ist nicht gleich `include ':a_b'`; eindeutige virtuelle Quellnamen/Classnamen. |
| `PRJ-006` | Relative Pfade, fehlende Skripte, Symlinks nach ausserhalb und Root-Escapes werden zurückgewiesen; 256-KiB-Grenze wird geprüft. |
| `PRJ-007` | `defaults` nur als vollständig qualifizierte Strings; kein Eintreten von Taskcode während Parsing. |

**Gemeinsamer Scriptlauf (Phase 2)**

| ID | Test / Orakel |
|---|---|
| `PRJ-101` | Zwei `job.groovy` mit identischem Dateinamen und je gleichnamigem lokalen Task `run` erzeugen verschiedene IDs und führen den jeweils richtigen Code aus. |
| `PRJ-102` | Drei Projekte: `download -> validation -> import`, Topologie und exakte Taskausführungsreihenfolge, nur ein `runId`/Runbericht. |
| `PRJ-103` | Auswahl `--target :validation:check` führt dessen `dependsOn`-Vorgänger aus, nicht nachgelagerte oder unverbundene Tasks. |
| `PRJ-104` | Cross-Project-`result(':download:fetch')` liefert dokumentierte Felder; fehlende `dependsOn`-Kante ergibt `UNDECLARED_RESULT_DEPENDENCY`. |
| `PRJ-105` | `after` zwischen zwei Projekten selektiert den Vorgänger nicht; `result` nur aufgrund von `after` bleibt verboten. |
| `PRJ-106` | Vorwärtsreferenzen über mehrere Includes und vollständige Zykluserkennung, inklusive nicht selektierter `dependsOn`-Zyklen. |
| `PRJ-107` | `file('sql/update.sql')` bei zwei Subprojekten wählt die jeweils **eigene** Datei; `ctx.file` und verschachteltes `ctx.run` erhalten denselben Scope. |
| `PRJ-108` | Root-, Subprojekt- und fremd qualifizierte Parameter: Deklaration, Default, Required, Typkonvertierung und CLI/Parameterdatei/Runtimekonfig-Priorität. |
| `PRJ-109` | `cleanup` aus zwei Unterprojekten: Namen eindeutig, genau einmaliger gemeinsamer Abschluss, Fehler-/Cancellation-Verhalten unverändert. |
| `PRJ-110` | Projekt-`--help`, `check`, `plan --graph mermaid` registrieren korrekt, führen keine Task-/Cleanup-Closures aus; Top-Level-Groovy bleibt wie dokumentiert. |
| `PRJ-111` | Script-Pfad statt Projektverzeichnis startet den bisherigen Single-Job unverändert; existierende CLI- und DSL-Tests laufen ohne Anpassung ihrer fachlichen Erwartungen. |
| `PRJ-112` | Projektreport/Eventlog enthält qualifizierte Tasknamen und korrekte Originalpfade/Zeilen; keine doppelte Start-/Terminal-Telemetrie. |
| `PRJ-113` | Single-Run-Fail-fast, `NO_DATA`, Task-Fehler und kontrollierter Abbruch verhalten sich wie in bestehenden Runner-Tests. |
| `PRJ-114` | Ein unbekanntes qualifiziertes Default oder CLI-Ziel scheitert vor Run; Fehlermeldung ist eindeutig. |
| `PRJ-115` | `ProjectJobSource.sha256` ändert sich bei Settings- oder Jobskriptänderung, ist bei identischem Bestand stabil und umfasst nicht beliebige Eingabedaten. |

**Paketierung / vollständige Integration (Phase 3)**

| ID | Test / Orakel |
|---|---|
| `PRJ-201` | Schema-3-Deskriptor erfolgreich; Schemas 1/2 unverändert. Unbekannte Felder und unzulässige Schema-Mischungen scheitern. |
| `PRJ-202` | Drei gleichnamige `job.groovy` kompilieren zu **verschiedenen** Scriptklassen; im JAR liegen alle Klassen, Ressourcen und Quellen ohne Kollisionen. |
| `PRJ-203` | `gretl package` führt weder Settings noch Skript-Toplevelcode noch Tasks aus; Markerdatei nicht vorhanden. |
| `PRJ-204` | `java -jar` aus fremdem CWD ohne Maven/GRETL/Checkout führt dieselbe DAG-Kette und dieselben fachlichen Ergebnisse aus wie `gretl run <projekt>`. |
| `PRJ-205` | JAR-`--version`, `--help`, `--plan` materialisieren keine Ressourcen, erzeugen keine Taskarbeit; Runtime-`--version` lädt keine Jobklassen. |
| `PRJ-206` | Projekt-JAR behält Unterprojektpfade (`import/sql/...`, `validation/models/...`); Ausführung findet beide realen materialisierten Dateien. |
| `PRJ-207` | Projekt- und Einzeljob-JAR-Metadaten lesbar, Schemas 2 bzw. 1, strikte kaputte Metadaten-/Resource-Tests. |
| `PRJ-208` | Alte Schema-1/2-JARs und ihr `--param`, `--target`, Help/Plan/Run behalten ihre fachlichen Ergebnisse. |
| `PRJ-209` | Fehlerfall in einem Unterprojekt: richtige `:projekt:task`-Identität, Ursache, Exitcode, Runbericht, Cleanup und Workspace-Retention. |
| `PRJ-210` | JAR-Artefakt-SHA, Dependency-Inventar, Compiler-/Classpath-Isolation, Ressourcenkollisionen und Provider-/Service-Merges bleiben qualifiziert. |
| `PRJ-211` | Der aktualisierte `ArtifactJobRunner` kann Projekt- und Single-Job-Fixtures ausführen; IMAGE-Adapter-Bestandstests bleiben grün und es gibt keinen falschen IMAGE-Erfolgsnachweis. |
| `PRJ-212` | Vollständiger `clean verify`-Reactor sowie `-Pgeotools-probe,backing-services` laufen erfolgreich, mit separater JAR-Evidenz und unveränderter fachlicher Operationsabdeckung. |

### 7.3 Testklassen und Fixtures (konkrete Zuordnung)

| Modul | Vorgeschlagene Tests |
|---|---|
| `gretl-groovy` | `GretlProjectParserTest`, `ProjectPathTest`, `ProjectSourceNamesTest`, `GroovyProjectLoaderTest`, `ProjectScopeTest`, `ProjectSourceLocationTest` |
| `gretl-runner` | **Bestehende** `ExecutionPlannerTest`, `JobDefinitionBuilderTest`, `ResultStoreTest`, `SequentialJobRunnerTest`, `ContextAccessTest` als Regression; neue Runner-Produktionslogik nur falls zwingend nötig |
| `gretl-job-runtime` | `ProjectJobSourceTest`, `ProjectRuntimeIntegrationTest`, `ProjectHashTest` |
| `gretl-cli` | `CliProjectOptionsTest`, `CliProjectRunTest`, `CliProjectHelpAndPlanTest`; bestehende `CliDefaultsAndParamsTest`, `CliPathBasesTest`, `CliFailureModesTest` unverändert funktional |
| `gretl-packager` | `ProjectPackageDescriptorTest`, `ProjectCompilerTest`, `ProjectPackageBuilderTest`, `ProjectPackageIsolationTest` |
| `gretl-dist` | Erweiterte `DistributionIT` mit `gretl run <projekt>` und `gretl package <projekt-deskriptor>` |
| `gretl-integration-tests` | `ProjectArtifactLocalJobIT` für echten Projekt-`java -jar`-Lauf; erweitertes `PackageArtifactAssertions`, `ArtifactJobRunner` unter Erhalt vorhandener Regressionen |

Fixtures vorzugsweise unter `gretl-integration-tests/src/test/resources/projects/` und kleine Parser-Unit-Testbäume über `@TempDir`. Mindestens ein vollständiges End-to-End-Fixture hat zwei identische `job.groovy`-Basenamen, zwei gleichnamige lokale Task-IDs, einen verschachtelten Projektnamen, zwei gleichnamige `sql/update.sql` mit **unterschiedlichen, nachweisbaren** Inhalten, Parameter, ein Cross-Project-Result, einen `after`-Test und eine Cleanup-Action.

**Fachliche Orakel:** Vergleiche erzeugte Dateiinhalte, Checksummen oder Testdatenbanktabellen, die ein unabhängiger Reader prüft. Nur Exitcode 0, ein vorhandenes JAR oder `--help` ist kein ausreichender Nachweis. Bei Negativtests prüfe zusätzlich, dass *keine* irreführende Erfolgstelemetrie entsteht.

## 8. Phasenplan mit harten Abnahme-Gates

**Unverrückbare Reihenfolge:** Phase 0 (Baseline) → Implementierungscommit A0 → unabhängige LLM-Review `PASS` für A0 → Phase 1 → Commit A1 → Review `PASS` für A1 → Phase 2 → Commit A2 → Review `PASS` für A2 → Phase 3 → Commit A3 → Review `PASS` für A3. Vor **jedem** Review muss der aktuelle Phasenstand committet sein. Bei `FAIL` wird ausschliesslich dieselbe Phase repariert, neu committet, erneut vollständig getestet und erneut unabhängig geprüft. Bei `BLOCKED` wird die Ursache behoben; kein Fortschritt. Die vollständige Git-Übergabe steht in Abschnitt 9.

### Phase 0 – Baseline und Ist-Nachweise (noch kein Featurecode)

**Ziel:** Einen reproduzierbaren, dokumentierten Ausgangspunkt schaffen.

**Arbeiten:**

1. Repository-Anweisungen und massgebliche Spezifikationen lesen.
2. HEAD-SHA, JDK/Maven-Version, vorhandene POM-Module, öffentliche DSL-Signaturen und bestehenden Teststatus dokumentieren.
3. `./mvnw -B clean verify` unter JDK 25 ausführen. Falls technisch möglich auch den vollständigen Backing-Service-Weg ausführen; Voraussetzungen dokumentieren.
4. Die wichtigsten erwarteten Nulländerungen (`gretl-api`, Runner-Semantik, Taskmodule) explizit inventarisieren.
5. `docs/implementation/project-subprojects/phase-0-baseline.md` und `phase-0-evidence.json` mit Ergebnissen und bekannten Vorfehlern erstellen und **als Phase-0-Kandidat committen** (auch ohne Featurecode). Anschliessend die Tests auf genau dieser Commit-SHA wiederholen und einen separaten LLM-Agenten genau diesen Commit prüfen lassen; der ursprüngliche Ausgangscommit bleibt zusätzlich als Vergleichsbasis dokumentiert. `phase-0-review.md` erst nach der Review übernehmen. Keine Altfehler als später neu entstanden deklarieren.

**Gate 0:** Baselinebericht und Evidenzdatei sind im Kandidatencommit enthalten; die unabhängige Review bezieht sich auf **genau diese Kandidaten-SHA**. Reviewbericht mit `PASS` ist versioniert; keine verdeckten Testfehler, JDK/CI-Voraussetzungen festgehalten. Bei bereits fehlerhafter Baseline ist vor Featureänderungen eine ausdrücklich dokumentierte Entscheidung zum weiteren Vorgehen nötig, **kein** behaupteter PASS.

### Phase 1 – Projektformat, Namensmodell, sichere Parsergrenze

**Ziel:** Vollständig geprüfte, immutable Projektmodelle ohne Änderungen an der Taskausführung.

**Implementieren:**

- `ProjectPath`, `ProjectEntry`, `GretlProject`, `GretlProjectParser`, `ProjectSourceNames` und kleine Projektfehlerklasse/Diagnostik in `gretl-groovy`.
- Strikte deklarative AST-Sprache für `settings.gretl`; Pfadvalidierung, Root-Job, verschachtelte Includes, Duplikate, stabile Reihenfolge, Größenlimit.
- Dokumentiertes Beispiel, aber **noch keine** neue `gretl run <directory>`-Funktion als abgeschlossen behaupten.

**Pflichttests:** `PRJ-001` bis `PRJ-007`, plus bisherige Tests. Insbesondere Settings-Nebenwirkungsprobe und Symlink-Fälle sind hart.

**Gate 1:** Alle Parser-/Value-Object-Tests und `./mvnw -B clean verify` grün; AST führt nichts aus; keine neuen Produktmodule oder Zyklen; Reviewerbericht `PASS`. **Erst dann Phase 2.**

### Phase 2 – Gemeinsame Jobdefinition und ausführbares GRETL-Projekt

**Ziel:** Projekt mit beliebig vielen explizit eingebundenen Skripten als **ein** Script-Run, inklusive `run`, `check`, `plan`, qualifizierten Namen, Ressourcen und Parametern.

**Implementieren:**

- `ProjectTaskIndex`, `GroovyJobLoader.loadProject`, gemeinsamem Builder und gemeinsamen Classloader/registrierten Bindings; `freeze()` nur am Ende.
- Projektmodus in `JobDslModel`, `GretlScript`, `GroovyContextFacade`, `SourceLocations`; Task-/Parameter-/Cleanup-Namen, relative `file()`-Basis und Cross-Project-Resultate.
- `ProjectJobSource` und minimaler Datei-/Verzeichnis-Dispatcher in `JobRuntime`; zielbezogene CLI-Prüfung und Hilfe.
- Deterministischer Projekt-Quellhash, originalgetreue Fehlerquellen und identischer Logging-/Runreport-Vertrag.
- Ein eigenständiges, dokumentiertes Beispielprojekt unter `examples/`.

**Pflichttests:** `PRJ-101` bis `PRJ-115`; vollständige bestehende CLI-, Groovy-, Runner- und Integrationsregressionen. Testfall mit echten Operationen (etwa `HttpDownload`/`Extract` und lokalen Dateien), nicht nur künstlichen Actions.

**Gate 2:** `gretl run|check|plan <projekt>` funktionieren wie spezifiziert; alter Einzeldateimodus ist nachweislich gleichwertig; ein einziger Taskgraph/Runreport; alle erwarteten negativen Fälle scheitern sauber; `./mvnw -B clean verify` grün; unabhängiger Reviewer `PASS`. **Erst dann Phase 3.**

### Phase 3 – Projekt-Fat-JAR, vollständige Artefakttests und Dokumentation

**Ziel:** Projekt-JAR mit allen inkludierten Jobskripten und Ressourcen, korrekt aus isolierter Umgebung ausführbar.

**Implementieren:**

- `PackageDescriptor` Schema 3; Schemas 1/2 ohne Verhaltensänderung.
- `ProjectCompiler`/`CompiledProject` mit eindeutigen virtuellen Skriptklassen, gleicher selektierter Dependencyhülle und ohne Skriptausführung beim Packen.
- `JobPackager`-Dispatch, `JobPackageBuilder.buildProject`, Metadaten Schema 2 und Ressourceninventar.
- `JobJarMain` mit `PackagedProjectJobSource` und einem gemeinsamen JAR-Ausführungsweg.
- Minimalinvasive Erweiterung von `ArtifactJobRunner` und `PackageArtifactAssertions` für Projektfixtures; bestehende IMAGE-Kontrakte unverändert.
- Anwenderdokumentation in `docs/` und `examples/`, vollständige Test- und Review-Evidenz.

**Pflichttests:** `PRJ-201` bis `PRJ-212`, sämtliche Tests aus Phasen 1/2, gesamte bisherige Produktsuite und beide oben dokumentierten Maven-Gates. Realer Fremd-CWD-`java -jar`-Lauf mit isoliertem HOME, ohne Maven/Checkout im Runtimepfad. JAR und Script müssen dieselben unabhängigen fachlichen Assertions bestehen.

**Gate 3 / Abschluss:** Beide Maven-Gesamtgates grün; Projekt- und alte Einzeljob-JARs fachlich qualifiziert; alle geforderten Testartefakte archiviert; unabhängig überprüfter Reviewbericht `PASS`; keine offene Critical-/Major-Abweichung. **Erst dann darf das Feature als fertig gemeldet werden.**

## 9. Unabhängige LLM-Reviews – verbindliches Git-Protokoll

### 9.1 Rollen und Arbeitsverzeichnisse

- **Agent A – Implementierung (z. B. Codex):** Implementiert die **aktuelle** Phase, schreibt Tests, Dokumentation, Phasenbericht und Evidenzübersicht, führt Tests aus und erstellt **vor der Review einen Git-Commit**. Nur A darf Produktcode korrigieren.
- **Agent B – unabhängige Review (z. B. OpenCode mit einem anderen LLM):** Erhält `phaseBaseSha`, `candidateSha`, Spezifikation und Evidenzorte. B prüft selbst Quellcode, **den gesamten Phasen-Diff**, Tests und Artefakte. B arbeitet in einem eigenen, auf `candidateSha` fixierten **detached Git-Worktree**, damit A und B nicht dieselben Dateien verändern. B verändert keinen getrackten Produktcode, keine Tests und keine Spezifikation und erstellt keinen Implementierungscommit. Testgenerierte Dateien unter `target/` sind zulässig.
- **Maintainer:** Entscheidet bei Scope-/Normkonflikten. B kann Fehler beschreiben, aber nicht durch eine stillschweigende Vertragsänderung legitimieren. A darf seine eigene Prüfung nicht als unabhängige Review ausgeben.
- **Keine Simulationsfreigabe:** Ist B nicht verfügbar oder kann ein Pflichttest nicht nachprüfbar laufen, lautet das Gate `BLOCKED`, nicht `PASS`.

### 9.2 Verbindlicher Ablauf je Phase

1. **Phasenbasis festlegen:** Am Beginn von Phase `N` setzt A `phaseBaseSha = git rev-parse HEAD` und schreibt diese SHA in `phase-N-implementation.md` und `phase-N-evidence.json`. Für Phase 0 ist dies der tatsächliche Checkout vor der Baselinearbeit; für spätere Phasen der Stand **nach** Archivierung der vorherigen unabhängigen Review. A verändert die Phasenbasis während einer Korrekturschleife nicht.
2. **Implementieren und vorprüfen:** A ergänzt die vorgesehenen Tests (idealerweise zunächst rot), implementiert nur Phase `N`, führt die Phasentests sowie das vorgeschriebene `./mvnw -B clean verify` aus und dokumentiert die Ausführung. Bei Phase 3 zusätzlich das vollständige Backing-Service-Gate. Keine ungetesteten „fertig“-Behauptungen.
3. **Implementierung committen:** A nimmt Quellcode, Tests, phasenbezogene Dokumentation und die **ohne Selbstreferenz** abgefasste Evidenzübersicht in einen oder mehrere Commits auf. Der letzte Commit ist `candidateSha = git rev-parse HEAD` (vollständige 40-stellige SHA). Es dürfen für die Review keine uncommitteten Änderungen an getrackten Dateien verbleiben. Der Phasenzustand ist dadurch unveränderlich adressierbar.
4. **Exakten Commit nachtesten:** A führt die erforderlichen Tests auf diesem committeten Stand erneut aus und archiviert Logs/JUnit-XML sowie Ausführungszeitpunkt, Exitcodes und `candidateSha` in einem **externen, nicht getrackten Handoff-Artefakt** (etwa `target/project-subprojects-review/phase-N-handoff.json`) oder einem CI-Artefakt, das auf dieselbe SHA zeigt. Der Reviewer führt die Pflichtprüfungen seinerseits unabhängig aus. Wenn der Nachtest Änderungen an getracktem Code oder Tests benötigt, muss A erneut committen und **die neue SHA** übergeben.
5. **Fixe Review-Übergabe:** A übermittelt an B **Phase**, `phaseBaseSha`, `candidateSha`, Handoff-/CI-Evidenz und die vollständige Spezifikation. B prüft, ob `candidateSha` existiert, ob `phaseBaseSha` dessen Vorfahr ist und ob `git diff phaseBaseSha candidateSha` **alle Commits seit Beginn der Phase** enthält. **Nicht nur `HEAD~1..HEAD` prüfen**: Eine Phase kann mehrere Implementierungs- oder Korrekturcommits haben.
6. **Unabhängig reproduzieren:** B checkt `candidateSha` im separaten Worktree aus, bestätigt die SHA mit `git rev-parse HEAD`, liest die relevanten Änderungen und führt die vorgeschriebenen Tests **selbst** aus. B prüft fachliche Orakel, Negativfälle, API-/Modulgrenzen, Regressionen, Logs und die tatsächlichen JAR-Artefakte. Berichte oder PASS-Labels von A allein sind keine Testevidenz.
7. **Explizites Urteil:** B erstellt ausserhalb des geprüften Arbeitsverzeichnisses einen Reviewbericht mit **genau einer** Entscheidung: `PASS`, `FAIL` oder `BLOCKED`; jede Abweichung enthält Schweregrad, `Datei:Zeile`, Begründung und nach Möglichkeit Reproduktionsschritte.
8. **Bei `PASS`:** A übernimmt den **unveränderten** Reviewbericht in `docs/implementation/project-subprojects/phase-N-review.md` und erstellt dafür einen separaten **Review-Archivierungscommit**. Dieser darf **nur** Reviewberichte/Review-Indexdateien betreffen, weder Produktcode noch Tests, Spezifikationsvertrag oder Testdaten. Die akzeptierte Implementierung bleibt ausdrücklich `candidateSha`; der Archivierungscommit ist **nicht** rückwirkend die geprüfte SHA. A legt erst danach die nächste `phaseBaseSha` auf den neuen `HEAD` fest.
9. **Bei `FAIL`:** A legt den Befund als `phase-N-review-attempt-XX.md` ab (nicht überschreiben), korrigiert **nur Phase N**, führt die Tests wieder aus und erstellt einen **neuen Kandidatencommit**. B überprüft den neuen Stand erneut mit dem **unveränderten** `phaseBaseSha` bis zur **neuen** `candidateSha`. Kein Übertragen eines PASS vom alten Commit, kein Vorrücken zu Phase N+1.
10. **Bei `BLOCKED`:** Fehlende Voraussetzungen/Artefakte oder nicht reproduzierbare Pflichtprüfungen werden konkret dokumentiert. A oder der Maintainer beheben den Blocker; falls dazu getrackte Dateien geändert wurden, entsteht eine **neue** Kandidaten-SHA. Die Phase bleibt bis zu einem späteren `PASS` offen.

**Empfohlene Git-Kommandos** (B benutzt einen separaten Pfad; Agenten ersetzen Platzhalter durch echte Werte):

```bash
# Agent A: am Anfang der Phase festhalten
PHASE_BASE_SHA=$(git rev-parse HEAD)

# Agent A: nach Implementation, Tests, Bericht und Evidence-Index
# Dateien gezielt stagen, keinen ungeprüften globalen Commit
# git add <konkrete Pfade>
git commit -m "feat(gretl): phase N project subprojects"
CANDIDATE_SHA=$(git rev-parse HEAD)
git diff --exit-code && git diff --cached --exit-code
# Pflichtgates anschliessend auf diesem committeten Stand erneut ausführen.

# Agent B: in seinem eigenen Review-Arbeitsverzeichnis
# PHASE_BASE_SHA und CANDIDATE_SHA stammen ausschliesslich aus As Übergabe
git merge-base --is-ancestor "$PHASE_BASE_SHA" "$CANDIDATE_SHA"
git diff --stat "$PHASE_BASE_SHA" "$CANDIDATE_SHA"
git diff "$PHASE_BASE_SHA" "$CANDIDATE_SHA"
git worktree add --detach ../gretl-runtime-phase-N-review "$CANDIDATE_SHA"
cd ../gretl-runtime-phase-N-review
git rev-parse HEAD  # MUSS CANDIDATE_SHA entsprechen
./mvnw -B clean verify
# Phase 3: zusätzlich ./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Der Reviewer darf den Prüf-Worktree nach der Review wieder entfernen; dies ändert die geprüfte SHA nicht. Für dieselbe Phase können neue Kandidaten in **neuen** Worktrees geprüft werden. Wenn Agent B auf einem anderen Rechner arbeitet, muss A den Commit für B erreichbar machen (z. B. Branch pushen); das Pushen ändert keine SHA. B darf nicht einen beweglichen Branch-Namen anstelle der angegebenen SHA prüfen.

**Wichtig: Git-Selbstreferenz vermeiden.** Eine Commit-SHA kann nicht als tatsächlicher Wert **in derselben Commit-Datei** stehen, die diese SHA erst definiert. Darum enthalten die vor dem Kandidatencommit getrackten Phasenberichte die bekannte `phaseBaseSha` und die Tests; die **endgültige** `candidateSha` steht erst im externen Handoff und später im unabhängigen Reviewbericht. Ein nachträgliches Editieren der getrackten Evidenzdatei, nur um die eigene SHA einzutragen, würde einen **anderen** Commit erzeugen und macht eine erneute Review erforderlich, sofern es nicht ausschliesslich ein zulässiger Review-Archivierungscommit ist.

**Review-Archivierung und Gültigkeit:** Der Review-Archivierungscommit ist nur dann ohne erneute Code-Review zulässig, wenn die Differenz `candidateSha..reviewArchiveSha` ausschliesslich Reviewberichte/Review-Indexdateien umfasst. **Jede** spätere Änderung an Produktcode, Tests, Testfixtures oder normativem Vertrag nach der geprüften `candidateSha` erfordert einen neuen Kandidaten und eine neue unabhängige Prüfung, bevor die Phase `PASS` bleibt. Git-Diff und die Reviewakte müssen diese Trennung nachweisbar machen.

### 9.3 Nachweise und Dateiverträge

Lege unter `docs/implementation/project-subprojects/` die folgenden versionierten Phasendateien ab:

```text
phase-0-baseline.md
phase-0-evidence.json
phase-0-review.md
phase-1-implementation.md
phase-1-evidence.json
phase-1-review.md
phase-2-implementation.md
phase-2-evidence.json
phase-2-review.md
phase-3-implementation.md
phase-3-evidence.json
phase-3-review.md
phase-N-review-attempt-XX.md     # bei FAIL/BLOCKED, historisch erhalten
```

Jeder **getrackte** Implementierungsbericht enthält: `phaseBaseSha`, beabsichtigte Änderungen, unveränderte Grenzen, die vollständige Testfallmatrix (PASS/FAIL/NICHT AUSGEFÜHRT), tatsächlich ausgeführte Befehle und Exitcodes, Umgebungsdetails, verfügbaren Log-/JUnit-XML-/CI-Artefaktverweis, Paket-/Job-SHA bei Artefakttests, bekannte Grenzen und offene Findings. Die abschliessende `candidateSha` ist **nicht** Bestandteil eines sich selbst referenzierenden Kandidatencommits.

Beispiel für den **getrackten** Evidence-Index vor dem Kandidatencommit:

```json
{
  "phase": 2,
  "phaseBaseSha": "<vollständige SHA des Phasenbeginns>",
  "javaVersion": "25",
  "commandsBeforeCommit": [
    {
      "command": "./mvnw -B clean verify",
      "exitCode": 0,
      "log": "<verfügbarer Archiv-/Logverweis>",
      "junitXml": ["<Pfad oder CI-Artefaktverweis>"]
    }
  ],
  "testCases": {
    "PRJ-101": "PASS",
    "PRJ-102": "PASS"
  },
  "knownLimitations": []
}
```

Das **ungetrackte** `phase-N-handoff.json` bzw. ein äquivalenter CI-/Chat-Handoff enthält die nach dem Commit ermittelte `candidateSha`, die unveränderte `phaseBaseSha`, konkrete Nachtestbefehle mit Exitcodes, Logs/Artefakte sowie Ort und Prüfsummen der Nachweise. Die echte Testfallmap enthält **alle** Pflicht-IDs; obige zwei Einträge sind nur ein Formatbeispiel. Der Handoff muss B erreichbar sein und ist ohne fremde Log-Infrastruktur durch B selbst überprüfbar. Logs/JUnit-XML dürfen nicht mit denen eines anderen Commits verwechselt werden.

**Reviewbericht** (von B selbst geschrieben, von A nur unverändert archiviert):

```markdown
# Phase N – Independent Review
- Phase base SHA: <phaseBaseSha>
- Reviewed candidate SHA: <candidateSha>
- Reviewer/Model/Session:
- Date/time:
- Separate worktree path and verified git rev-parse HEAD:
- Full reviewed diff: <phaseBaseSha>..<candidateSha>
- Exact independently executed test commands, exit codes and evidence paths:
- Required test-case matrix: all phase IDs and outcomes
- Architecture/module boundaries and backward compatibility:
- Negative tests, source locations, provenance, telemetry and JAR evidence:
- Findings: CRITICAL / MAJOR / MINOR (file:line, reproduction, proposed correction)
- What could not be independently verified:
- Verdict: PASS | FAIL | BLOCKED
- Rationale for verdict:
```

### 9.4 Urteilskriterien und Gate-Übernahme

- **PASS:** Alle für Phase `N` verpflichtenden Tests sind auf der **geprüften** SHA nachweisbar erfolgreich; B hat die wesentlichen Prüfungen selbst reproduziert; keine offenen CRITICAL-/MAJOR-Findings; Backing-Service-Gate in Phase 3 erfüllt. Dokumentierte rein kosmetische MINOR-Findings sind zulässig.
- **FAIL:** Nachgewiesene fachliche, architektonische oder Testfehler; fehlende Pflichtfunktion; geschwächte Regressionstests; fehlende oder fingierte Nachweise. A korrigiert und committet neu.
- **BLOCKED:** B, Umgebung, Commit, Pflichtdienste oder Evidenz sind nicht verfügbar, sodass ein verbindlicher Nachweis nicht möglich ist. Kein angenommenes PASS. Ursache und Voraussetzungen zur Entblockierung nennen.
- **Gate-Freigabe:** Ausschliesslich der unverändert archivierte `PASS`-Bericht mit exakter `candidateSha` erlaubt Phase `N+1`. Weder der Review-Archivierungscommit noch ein erfolgreiches Selbstreview von A ersetzen diese Freigabe.

### 9.5 Kontrollfragen für Reviewer B

1. Ist `git rev-parse HEAD` **exakt** die übergebene `candidateSha`? Ist `phaseBaseSha` ein Vorfahr und umfasst `git diff phaseBaseSha candidateSha` die **gesamte** Phase, einschliesslich Korrekturcommits?
2. Werden alle in dieser Phase vorgeschriebenen Fähigkeiten wirklich ausgeführt und nicht nur ein Stub oder ein Methodensignaturtest geprüft?
3. Hat der Code einen einzigen Builder/Runner, oder entstanden mehrere voneinander getrennte Jobs mit gefälschter gemeinsamer Identität?
4. Wurden globale statische Registries, neue Modulzyklen, Build-Framework-Abstraktionen oder versehentliche Änderungen an SPI/Runner/Logging eingeführt?
5. Werden falsche Pfade, `after` statt `dependsOn`, TaskRef-Identitäten, fehlende Parameter und unzulässige Projektsettings tatsächlich abgefangen?
6. Wird Toplevel-Code beim Paketieren definitiv nicht ausgeführt? Sind Classnames auch bei identischen `job.groovy` eindeutige Einstiegsklassen?
7. Sind SourceLocations verständlich und enthalten sie Originalpfad und Zeile? Bleiben Reports maschinenlesbar und secret-sicher?
8. Sind alte Einzeljob-CLI-Aufrufe und Schemata 1/2 in Integrationstests positiv nachgewiesen?
9. Gibt es echte fachliche Assertions für JAR-Jobtests ausserhalb des Jobprozesses? Wurde ein echter JAR-Prozess gestartet?
10. Ist `clean verify` **auf der geprüften SHA** unabhängig ausgeführt? Wurden Testfälle übersprungen oder CI-Artefakte aus einem älteren Commit übernommen? Kann B die eigenen Logs zuordnen?
11. Hat B getrackte Produkt-/Testdateien verändert, oder stammt der Review aus dem Arbeitsverzeichnis von A statt aus einem separaten Worktree?
12. Ist der Umfang klein und konsistent mit Ockham's Razor? Unnötige neue Klassen/Frameworks müssen begründet oder reduziert werden.

## 10. Umsetzungshinweise und Änderungsdisziplin

- **Keine Spekulationsimplementierung:** Erst den aktuellen Code und sein Verhalten lesen; Methodensignaturen und Quellstellen dieser Spezifikation gegen HEAD abgleichen. Bei einer inzwischen geänderten Codebasis Abweichungen vor dem Eingriff dokumentieren.
- **Möglichst wenige neue Klassen:** Die oben vorgeschlagenen Werte/Parser/Index-Klassen rechtfertigen sich durch klar getrennte Aufgaben. Doppelte Resolver, neue generische Plugin-Frameworks oder ein zusätzliches DSL-System sind verboten, wenn vorhandene Mechanik genügt.
- **Keine automatische Fachsemantik:** Projektverzeichnisse definieren keine Reihenfolge; nur `dependsOn`/`after` tun dies. `result` zieht keine Abhängigkeit implizit nach.
- **Keine versteckte Prozess-/Classloader-Isolation:** Alle Projektdefinitionen teilen denselben Registry-/Runtime-Classpath, aber erhalten separate Groovy-Bindings und einen getrennten Projektscope. Der Nutzer darf pro Projekt keinen anderen Taskmodul-Classpath erwarten.
- **Keine neue Mavenpflicht für Jobautoren:** Maven bleibt nur im Runtime-Produktbuild und beim optionalen Package-Vorgang relevant.
- **Eindeutige Scope-Übersetzung:** Implementiere `ProjectPath` einmal und benutze ihn für Registrierung, Links, Resultzugriff, Parameter, Cleanup, CLI und Paketmetadaten. Keine sechs unterschiedlichen, leicht abweichenden Stringpräfix-Regeln.
- **Quellorttreue:** Der virtuelle Groovy-Dateiname ist eine technische Kompilierhilfe. Benutzerfehler müssen auf die ursprüngliche Datei führen; mindestens der Projektpfad und eine sinnvolle Zeile sollen in Meldung/Diagnostik erscheinen.
- **Strikte Testidentität:** Tests laufen unter demselben JDK 25 und über denselben Maven-Wrapper wie die bestehenden Produktgates. Die Existenz des Buildcaches, der Maven-Installation oder anderer Host-JARs darf keinen fehlerhaften Projekt-JAR-Build retten.
- **Dokumentation:** Ergänze eine knappe `docs/implementation/project-subprojects/README.md` und ein funktionierendes Beispiel unter `examples/`. Unterscheide klar Einzeldatei-/Projekt-/JAR-Modus. Verlinke von der Produkt-README. Erkläre den bewussten Nichtumfang (keine echten Gradle-Builds, keine Nested-Runs, `.gretl`-Dateiendung für Jobs noch nicht eingeführt).
- **Bestehende Tests schützen:** Änderung bestehender Tests nur, wenn eine dokumentierte neue Vertragsentscheidung es erfordert. Keine fachlichen Assertions löschen oder abschwächen; Testumbenennung/-verschiebung ist im Phasenbericht nachvollziehbar.
- **Architektur- und Sicherheitsgrenzen:** Keine Bearbeitung produktiver Jobs, keine Konfiguration externer Systeme, keine geheimen Werte in Reports, keine Downloads aus nicht vertrauenswürdigen Jobsettings. Tests mit synthetischen Daten.

## 11. Konkreter Startprompt für Coding-Agent A (Codex)

```text
Du bist Implementierungsagent A im Repository
https://github.com/edigonzales/gretl-runtime.
Lies zuerst AGENTS.md, docs/spec/AGENTS.md und die massgeblichen
Runtime-/DSL-/Packager-/Logging-/Test-Spezifikationen sowie die beigefügte
Spezifikation „GRETL Runtime – Subprojekte und projektübergreifender
Taskgraph“, insbesondere Abschnitte 7–9.

ZIEL: Implementiere genau EINE Phase nach der anderen (0, dann 1, 2, 3).
Starte nur Phase 0. Stelle ohne expliziten unabhängigen PASS auf den
zuletzt eingereichten Implementierungscommit niemals Phase N+1 her.
Du bist NICHT dein eigener Reviewer; du darfst keinen PASS fingieren.

PROTOKOLL JE PHASE:
1. Prüfe, dass die vorherige Phase einen unabhängig verfassten,
   versionierten PASS-Bericht besitzt. Für Phase 0 entfällt dies.
   Merke vor Beginn `phaseBaseSha = git rev-parse HEAD` und schreibe
   diese SHA in den Phasenbericht und den Evidence-Index.
2. Lies die bestehenden Klassen und überprüfe ihre wirklichen Signaturen.
   Implementiere nur die Klassen/Methoden und Tests der aktuellen Phase.
   Behalte Single-Job-CLI/DSL/JAR, API-/Runner-/Logging-Verträge,
   JDK 25, Groovy 6 und Maven-Modulgrenzen bei. Keine Gradle-Integration,
   keine neue Runner-Engine. Schreibe aussagekräftige rote Tests, bevor du
   das Verhalten implementierst, soweit praktisch möglich.
3. Führe die Pflicht-Tests der Phase und ./mvnw -B clean verify aus.
   In Phase 3 zusätzlich
   ./mvnw -B -Pgeotools-probe,backing-services clean verify.
   Erstelle phase-N-implementation.md bzw. für Phase 0 phase-0-baseline.md
   sowie phase-N-evidence.json mit Test-IDs, Befehlen, Exitcodes, Logs,
   fachlichen Orakeln, Fehlern und phaseBaseSha.
4. Committe ALLE zu prüfenden Produktänderungen, Tests und Dokumente
   auf deinem Implementierungsbranch. Lies `candidateSha = git rev-parse HEAD`
   als vollständige 40-stellige SHA. Keine uncommitteten Änderungen an
   getrackten Dateien für die Übergabe. Keine weiteren getrackten Änderungen
   während B genau diesen Commit prüft.
5. Führe die Pflichtgates nach dem Commit auf dieser festen SHA nochmals
   aus. Archiviere konkrete Logs/Testergebnisse in target/ oder CI. Lege
   dort ausserhalb getrackter Dateien ein phase-N-handoff.json mit
   phaseBaseSha, candidateSha, Testbefehlen, Exitcodes und Evidenzpfaden ab.
   Gib B den SHA-Wert, nicht nur einen Branch-Namen. Stelle bei getrennter
   Umgebung sicher, dass der Commit für B erreichbar ist (z. B. push).
   Die eigene SHA darf NICHT durch Bearbeiten des getrackten Evidence-Index
   nachträglich in denselben Commit hineingeschrieben werden.
6. Übergib an den separaten Review-Agenten B:
   Phase N; phaseBaseSha; candidateSha; die Spezifikation;
   Pfad/Link zum Handoff sowie Phasenbericht und Testartefakte.
   B muss den GANZEN Diff phaseBaseSha..candidateSha und den exakten
   Codezustand in einem separaten Git-Worktree prüfen und Tests selbst
   reproduzieren. Warte auf sein ausdrückliches PASS/FAIL/BLOCKED.
7. Bei FAIL: Archiviere den Review als review-attempt, korrigiere N,
   teste vollständig erneut, committe einen NEUEN candidateSha und bitte B
   erneut um Review von derselben phaseBaseSha bis zur NEUEN SHA.
   Bei BLOCKED: beseitige den konkret genannten Blocker und wiederhole
   die erforderliche Übergabe/Prüfung; kein Fortschritt.
8. Erst bei PASS: Übernimm Bs unabhängigen Bericht UNVERÄNDERT als
   docs/implementation/project-subprojects/phase-N-review.md. Committe
   ausschliesslich diese Reviewakte/Review-Indexdateien separat. Keine
   Produktcode-, Test- oder Vertragsänderungen in diesem Archivierungscommit.
   Der akzeptierte Implementierungscommit bleibt candidateSha, nicht der
   Review-Archivierungscommit. Setze für die NÄCHSTE Phase die neue
   phaseBaseSha aus dem dann aktuellen HEAD.

Gib am Ende jeder Phase nur den überprüfbaren Status, phaseBaseSha,
candidateSha, exakte Testbefehle/Exitcodes, Artefaktorte und
Reviewübergabe an. Melde die Phase erst nach unabhängigem PASS als
abgeschlossen. Beginne jetzt mit Phase 0 und erfinde keine Testerfolge.
```

## 12. Reviewprompt für den unabhängigen LLM-Agenten B (OpenCode)

```text
Du bist der von Codex unabhängige Review-Agent B für GRETL Runtime
Subprojekte. Du hast die Implementierung nicht geschrieben. Deine Aufgabe
ist nicht, sie zu reparieren, sondern anhand realer Ausführung und
Codeprüfung die Phase N mit PASS, FAIL oder BLOCKED zu beurteilen.

EINGABEN VON AGENT A:
- Phasennummer N = <N>
- Phase-Basiscommit phaseBaseSha = <VOLLSTÄNDIGE_SHA>
- Implementierungskandidat candidateSha = <VOLLSTÄNDIGE_SHA>
- Vollständige Spezifikation (besonders Testmatrix und Gates von Phase N)
- Phasenbericht, Evidence-Index und unabhängige Test-/CI-Artefaktorte

VERBINDLICHE ARBEITSWEISE:
1. Prüfe, dass beide SHAs existieren, dass phaseBaseSha ein Vorfahr von
   candidateSha ist und dass der vollständige Diff
   `git diff phaseBaseSha candidateSha` geprüft wird.
   NIEMALS auf HEAD~1, aktuelle Branchspitze oder nur den letzten
   Fix-Commit verkürzen.
2. Verwende einen SEPARATEN detached Git-Worktree exakt auf candidateSha,
   z. B. `git worktree add --detach ../gretl-runtime-review-N <candidateSha>`.
   Prüfe dort mit `git rev-parse HEAD` die SHA. Verändere keine getrackten
   Produkt-, Test-, Fixture- oder Spezifikationsdateien. Du darfst
   Tests ausführen und Dateien unter target/ erzeugen. Wenn ein Test
   getrackte Dateien verschmutzt, benenne das als Finding.
3. Lies die relevanten Produktklassen und Änderungen selbst. Gleiche
   Funktionsumfang, Methoden-/Klassendesign, Modulgrenzen,
   Ockham's Razor, Fehlerdiagnosen und Rückwärtskompatibilität mit der
   Spezifikation ab. A's Aussagen oder grüne Label sind kein Beweis.
4. Führe die für Phase N verpflichtenden Tests selbst auf candidateSha
   aus: insbesondere ./mvnw -B clean verify. In Phase 3 zusätzlich
   ./mvnw -B -Pgeotools-probe,backing-services clean verify.
   Prüfe reale positive und negative Fachfälle sowie die Ergebnisse der
   JAR-Prozesse (nicht bloss Exitcode oder --help). Prüfe Log-/JUnit-XML-
   Provenienz, Projekt-/Task-IDs, SourceLocations, Ressourcenpfade,
   `dependsOn` vs `after`, Settings-Sicherheit und alte Einzeljob-JARs.
5. Bewerte ALLE für Phase N relevanten Test-IDs; jede Abweichung mit
   konkretem Datei:Zeile, reproduzierbarem Befund, Schweregrad
   CRITICAL/MAJOR/MINOR und erforderlicher Korrektur festhalten.
6. Schreibe den Reviewbericht AUSSERHALB des geprüften Worktrees oder
   übergib ihn als eigenständiges Artefakt an A. Der Bericht muss die
   exakten phaseBaseSha und candidateSha, die unabhängig ausgeführten
   Kommandos mit Exitcodes, Testfallmatrix, Belege, offene Punkte und
   genau EIN Urteil PASS, FAIL oder BLOCKED enthalten.
   PASS: alle Pflichtgates erfüllt, keine CRITICAL/MAJOR-Findings.
   FAIL: tatsächlicher Fehler oder unerfüllter Vertrag.
   BLOCKED: notwendige Prüfungen mangels Zugriff/Umgebung/Evidenz
   nicht ausführbar. Ungeprüfte Tests NIEMALS als PASS ausgeben.
7. Nimm KEINEN Code-Fix vor. Fordere bei FAIL einen neuen Commit von A;
   bei erneuter Review dieselbe phaseBaseSha, aber die neue candidateSha
   verwenden. Die Freigabe für einen alten Commit darf NICHT auf einen
   veränderten Codezustand übertragen werden.

Erstelle jetzt die unabhängige Review für Phase <N> auf der angegebenen
candidateSha und liefere den vollständigen Reviewbericht.
```

## 13. Definition of Done (Gesamtauftrag)

Das Feature ist **erst** abgeschlossen, wenn:

1. `settings.gretl` nur die festgelegte deklarative Syntax erlaubt; Included Scripts und Root-Job sicher und deterministisch gelesen werden.
2. Mehrere `job.groovy`-Dateien mit gleichen Basenamen und lokalen Tasknamen als eindeutige qualifizierte Tasks in **einer** `JobDefinition` registriert sind.
3. `dependsOn`, `after`, Targetauswahl, Cross-Project-Resultate, Parameter, Cleanup, `file`, `path`, `workPath`, Fehlermeldungen, Planungsgraph und Logging fachlich korrekt bleiben.
4. `gretl run|check|plan <projekt>` ohne Gradle/Maven funktionieren und vorhandene direkte `job.groovy`-Aufrufe unverändert bleiben.
5. Der Schema-3-Packager ein isoliert lauffähiges Projekt-JAR mit allen ausdrücklich deklarierten Ressourcen erzeugt, ohne beim Packen Scriptbody/Tasks auszuführen.
6. Die bestehenden Schema-1/2-Einzeljobpakete samt allen Regressionen weiter funktionieren.
7. Alle Testfall-IDs in ihren jeweiligen Phasen mit überprüfbaren fachlichen Orakeln bestanden sind; die beiden Gesamt-Maven-Gates in Phase 3 grün sind.
8. Jede Phase **vor ihrer Review einen festen Implementierungscommit** besitzt. Agent B prüft den vollständigen Phasen-Diff (`phaseBaseSha..candidateSha`) in einem separaten Worktree, reproduziert Pflicht-Tests und dokumentiert einen unabhängigen `PASS` zur **exakt geprüften** `candidateSha`. Jede Korrektur erzeugt eine neue Kandidaten-SHA und eine neue Review. Offene Pflichtprüfungen sind **BLOCKED**, nicht abgeschlossen.
9. Beispieldaten, Benutzerdokumentation, Phasenberichte, Reviewberichte und Evidenzdateien versioniert sind. Die Reviewakte wird **erst nach dem PASS** in einem separaten, ausschliesslich dokumentierenden Commit archiviert; dieser ersetzt die geprüfte Implementierungs-SHA nicht.
10. Keine unerwartete Änderung der neutralen Java-SPI, keine alternative Runner-Engine und keine neue Buildpflicht bei der normalen Jobentwicklung eingeführt wurde.

**Kernprinzip:** Ein GRETL-Projekt kann aus vielen Dateien und Unterprojekten bestehen, bleibt aber zur Ausführungszeit **ein Job mit einem Taskgraphen**.
