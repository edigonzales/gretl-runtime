# 04 — Job-DSL und eigene Tasks

**Version:** 4.1, 18. Oktober 2026.  
**Normativ für:** die Groovy-Oberfläche, ihre Auswertungszeitpunkte und gleichberechtigte Custom-Task-Bibliotheken. Ausführungssemantik und Fehlercodes stehen verbindlich in [03](03-runtime-vertraege.md). Die Java-Signaturen sind in [14](14-java-api-vertraege.md) vollständig ausgeschrieben; das konkrete Groovy-Klassendesign steht in [15](15-runner-klassendesign.md).

Die Beispiele beschreiben die **zu implementierende Ziel-API**. Sie sind kein bereits verfügbares GRETL-Produkt. Der Coding-Agent erstellt daraus ausführbare Beispieljobs und Vertragstests mit lokalen Testdaten. Produktive Zugangsdaten und die Ausführung produktiver GRETL-Jobs gehören nicht dazu.

Dieses Kapitel beginnt mit dem Anwenderweg. Implementierungsdetails, eigene Aktionen und die Java-Schnittstelle folgen danach. Fachanwender sollen gepflegte Rezepte ändern können, ohne Closuredelegation, Java-Records oder den internen Taskgraph zu kennen.

## 1. Einfache Jobs schreiben und anpassen

### 1.1 SQL-Dateien ausführen

```groovy
task('datenAktualisieren', SqlExecutor) {
    database = 'edit'
    scripts = [file('sql/update.sql')]
}

defaults 'datenAktualisieren'
```

Der Job enthält einen Arbeitsschritt mit dem Namen `datenAktualisieren`. Seine Art ist `SqlExecutor`. Im Block stehen die Einstellungen. `database` bezeichnet eine vorbereitete Datenbankverbindung der Runtimekonfiguration; `file('sql/update.sql')` eine zum Job gehörende SQL-Datei. `defaults` nennt das ohne besondere Zielauswahl auszuführende Ziel.

Für das lauffähige Beispiel werden eine lokale Testdatenbank mit Alias `edit` und die mitgelieferte Datei `sql/update.sql` bereitgestellt. Der Job enthält keine Datenbankpasswörter. Mehrere SQL-Dateien stehen in derselben Liste und werden in ihrer angegebenen Reihenfolge ausgeführt.

### 1.2 Eine ZIP-Datei herunterladen und entpacken

```groovy
parameter('quelle', required: true,
          description: 'Adresse der ZIP-Datei')

task('herunterladen', HttpDownload) {
    url = parameter('quelle')
    output = workPath('daten.zip')
}

task('entpacken', Extract, dependsOn: 'herunterladen') {
    archive = result('herunterladen').file
    compression = 'ZIP'
    outputDirectory = workPath('entpackt')
}

defaults 'entpacken'
```

Der Startwert wird beispielsweise mit `--param quelle=https://example.test/daten.zip` übergeben; die ausführbare Testvorlage verwendet einen lokalen HTTP-Testserver. `dependsOn` sagt, dass `herunterladen` zuerst erfolgreich abgeschlossen sein muss. `result('herunterladen').file` ist die danach verfügbare heruntergeladene Datei. Ein zusätzliches `doFirst` wird dafür nicht benötigt.

`workPath` bezeichnet den temporären Arbeitsbereich des jeweiligen Schritts. Die heruntergeladene Datei bleibt für nachfolgende Schritte bis zum Ende des Runs verfügbar. Diese Vorlage demonstriert Verarbeitung im Arbeitsbereich; dauerhafte Ausgaben werden ausdrücklich an einen dafür konfigurierten externen Ort geschrieben.

Die beiden Taskarten haben dokumentierte Standardlimits: HTTP höchstens 1 GiB, Entpacken höchstens 4 GiB und bei ZIP höchstens 10'000 Einträge. Anwender müssen diese Werte für ein Grundrezept nicht wiederholen. Ein Job kann sie bei konkretem Bedarf mit den Feldern aus Abschnitt 6 überschreiben.

### 1.3 Fünf Regeln für den normalen Anwenderweg

1. Jeder Schritt hat einen eindeutigen Namen und eine Taskart.
2. Einstellungen stehen als `name = wert` im Block.
3. Benötigte Vorgänger stehen bei `dependsOn` im Kopf des Schritts.
4. Die Einstellungen werden ausgewertet, wenn dieser Schritt nach seinen Vorgängern an der Reihe ist.
5. `defaults` nennt das normale Ziel; erforderliche Vorgänger werden mit ausgewählt.

Ein Taskname in Anführungszeichen genügt. Es sind weder eine zusätzliche `def`-Variable noch `ctx ->`, `TaskRef` oder ein Providerwrapper erforderlich. Quellreihenfolge dient der Lesbarkeit; sie ersetzt keine Abhängigkeit. Die Runtime ergänzt keine Vorgänger durch Erraten aus späteren Ergebniszugriffen.

## 2. Parameter, Dateien und Ergebnisse

### DSL-001 — Kanonische Einstellungsschreibweise

Einzelwerte, Listen und ausdrücklich unterstützte Maps werden mit `=` gesetzt:

```groovy
url = parameter('quelle')
output = workPath('daten.zip')
headers = ['Accept': 'application/zip']
```

Methoden wie `url '...'`, wiederholte `header(...)`-Aufrufe und die neue Zuweisungsschreibweise werden nicht als parallele Konfigurations-APIs angeboten. Die Konfiguration bleibt flach. Fachlich begründete Werte wie SQL-Statement-Maps haben ein ausdrücklich beschriebenes kleines Schema; beliebige verschachtelte Closures werden nicht automatisch an Unterbuilder gebunden.

Das `=` schreibt einen Wert in den frischen Konfigurationsscope beim Taskstart. Es führt keine Gradle-Properties, Providerketten oder zusätzliche frühe Auswertung ein. Normale Java-Builder, Requests und die Operations-SPI bleiben frei von Groovytypen.

Die Textkonverter der mitgelieferten Taskfelder akzeptieren `String` und `CharSequence` und erzeugen bei der jeweiligen Zuweisung einen festen String. Damit sind auch im späten Block erzeugte Groovy-Interpolationen verwendbar, ohne einen GString in den Java-Request oder Handler weiterzureichen. Groovys [GString implementiert CharSequence](https://docs.groovy-lang.org/docs/groovy-6.0.0/html/api/groovy/lang/GString.html). Die Konverter der ausdrücklich unterstützten HTTP-Header- und SQL-Statement-Maps normalisieren ihre jeweiligen Textwerte ebenso. Andere beliebige Objekte werden nicht pauschal über `toString()` als gültige URL, Zahl, Pfad oder Fachwert akzeptiert. Diese begrenzte Konversion bedeutet keine rekursive Umwandlung beliebiger Objektgraphen. Die Parameterdatei bleibt gemäss Kapitel 06 reiner Stringtransport und führt keine Interpolation aus.

### DSL-002 — Öffentliche Parameter deklarieren

```groovy
parameter('eingabe', type: 'path', required: true,
          description: 'Zu validierende XTF-Datei')
parameter('zusatzpruefung', type: 'boolean', defaultValue: false,
          description: 'Zusätzliche fachliche Prüfung einschalten')
parameter('anzahl', type: 'integer', defaultValue: 10,
          description: 'Maximale Anzahl fachlicher Datensätze')
parameter('bemerkung', description: 'Optionale Bemerkung zum Lauf')
```

Die Deklarationen stehen im Script-Toplevel und beschreiben die öffentliche Bedienoberfläche des Jobs. `description` ist verpflichtend und darf nicht leer sein. Ohne `type` gilt `text`, ohne `required` gilt `false`. Der Name folgt dem CLI-Vertrag in [06](06-betrieb-und-auslieferung.md). Unbekannte Deklarationsoptionen und doppelte Namen sind Fehler.

| Typ | Wert im laufenden Job | Bedeutung |
|---|---|---|
| `text` | String | Text bleibt Text; optional auch der ausdrücklich übergebene leere String. |
| `boolean` | Boolean | CLI-Text exakt `true` oder `false`. |
| `integer` | Long | Dezimalzahl mit optionalem Vorzeichen innerhalb des vorzeichenbehafteten 64-Bit-Bereichs. |
| `path` | Path | Normalisierter Pfad, bei relativer Angabe gegen das ursprüngliche Startverzeichnis aufgelöst. |

`defaultValue` ist optional. `required: true` zusammen mit einem vorhandenen `defaultValue` ist ein Definitionsfehler. Eine optionale Deklaration ohne bereitgestellten Wert und ohne Vorgabe ergibt `null`. Ein leerer verpflichtender Textwert und leerer Pfadtext sind ungültig. Eine Pathdeklaration prüft weder Existenz noch Dateityp.

Alle öffentlichen Parameter müssen deklariert sein. Die Vorprüfung, Quellenpriorität und Transportregeln stehen in RUN-013 und Kapitel 06: `run` prüft sämtliche verpflichtenden Parameter jobweit vor Taskarbeit; `check` und `plan` dürfen fehlende Pflichtwerte als offen anzeigen. Jobhilfe löst keine Werte oder Secrets auf.

### DSL-003 — Werte erst im laufenden Schritt lesen

Innerhalb des gespeicherten Task-/Action-Blocks liest `parameter('eingabe')` den typisierten Wert. Damit ist bei einem Pathparameter keine zusätzliche Konstruktion `path(parameter('eingabe'))` nötig:

```groovy
parameter('eingabe', type: 'path', required: true,
          description: 'Zu validierende XTF-Datei')

task('validieren', IliValidator) {
    dataFile = parameter('eingabe')
    modelDirectory = file('models')
}

defaults 'validieren'
```

Diese Vorlage enthält ihre eigene Parameterdeklaration und ist als vollständiger Job kopierbar. IliValidator gehört zu Lieferstufe B. Das `models`-Verzeichnis mit dem vollständigen lokalen Modellbestand wird im Beispiel mitgeliefert.

Ein alleinstehendes `parameter('eingabe')` im Toplevel ist kein Wertzugriff. Dort sind nur Deklarationen zulässig; die Ein-Argument-Leseform gehört in den Ausführungsscope. Es gibt keine zweite rohe `ctx.param`-/`ctx.paramOr`-API. Vorgaben stehen in der Deklaration, und alle Zugänge lesen denselben typisierten Parameterbestand.

### DSL-004 — Die drei Pfadbereiche

| Ausdruck | Anwenderbedeutung | Basis |
|---|---|---|
| `file('sql/update.sql')` | Eine Datei beziehungsweise ein Verzeichnis, das zum Job gehört | Scriptverzeichnis oder materialisierte Ressourcen des Job-JARs |
| `parameter('eingabe')` mit Typ `path` | Eine beim Start angegebene externe Datei beziehungsweise ein Verzeichnis | Ursprüngliches Startverzeichnis; absolute Angaben bleiben absolut |
| `workPath('zwischenstand.xtf')` | Temporäre Arbeitsdatei dieses Schritts | Eigener Task- beziehungsweise Aufrufarbeitsbereich |

Für fortgeschrittenen Code bleibt `path(text)` als explizite Auflösung einer erst während des Jobs entstandenen externen Pfadangabe verfügbar. Alle Formen liefern Path; sie öffnen nicht automatisch die Datei. `workPath` stellt den Elternordner bereit, erzeugt aber keine vorgetäuschte vollständige Ausgabe. Absolute Pfade und Traversal sind dort unzulässig. Wiederholte Aufrufe mit demselben Namen liefern denselben Pfad.

Ob eine Operation vorhandene Ausgaben ersetzen darf, regelt die Taskart. Die mitgelieferten HTTP-/Extract-Tasks überschreiben vorhandene Zieldateien nicht still. Eine erzeugte Datei bleibt bis zum Runende zugänglich; der Producer löscht sie nicht schon bei seinem Taskende. Kapitel 06 regelt den Workspace-Lifecycle.

### DSL-005 — Dokumentierte Ausgänge lesen

```groovy
archive = result('herunterladen').file
```

`result` liest nur Ergebnisse aus einer direkten oder transitiven `dependsOn`-Kette. Es startet keinen Task und ergänzt keine Kante. Bei typisierten Tasks ist die zurückgegebene Oberfläche eine lesende Sicht auf die ausdrücklich dokumentierten Ergebnisfelder. Bei Actions bleibt es der normale Rückgabewert, etwa ein String oder eine URI.

Die Hilfe einer Taskart erklärt ihre Ausgänge. Ein Tippfehler wie `.filesize` statt `.bytes` erzeugt eine Meldung mit den verfügbaren Feldern. Die Runtime sucht keine beliebigen Getter und serialisiert keine Ergebnisobjekte. Feldwerte dürfen null sein, wenn die Taskbeschreibung das für den jeweiligen Modus ausdrücklich vorsieht.

`resultOptional('name')` gehört zum fortgeschrittenen Weg. Es liefert nur bei regulärem Skip oder einem erfolgreichen Abschluss ohne Wert ein leeres Optional. Unbekannte Namen, fehlende Abhängigkeiten und echte Fehler werden nicht verborgen. Die vollständigen Regeln stehen in RUN-030 und RUN-033.

## 3. Taskarten, Lieferumfang und Hilfe

### DSL-006 — Eine gemeinsame Erweiterungsschnittstelle

`HttpDownload`, `Extract` oder `SqlExecutor` sind Symbole registrierter Taskarten. Sie sind keine alten Gradle-Taskklassen. Das Script kann sie ohne Import verwenden, weil die ausdrücklich bereitgestellten Bibliotheken vor dem Laden registriert werden.

| Stand | Zugesagte Taskarten beziehungsweise Bedeutung |
|---|---|
| MVP, Lieferstufe A | HttpDownload, Extract, SqlExecutor |
| MVP, Lieferstufe B | Zusätzlich IliValidator |
| Nach dem MVP | Publisher-Port gemäss Kapitel 05; weitere Taskarten wie XtfToGpkg nach eigenem Fachvertrag |
| Weitere mitgelieferte Taskbibliotheken | Können in späteren Releases regulär Bestandteil der Standarddistribution werden |
| Eigene Taskbibliotheken | Werden vor Start ausdrücklich bereitgestellt und erhalten dieselbe DSL, Hilfe und Ausführung wie mitgelieferte Bibliotheken |

Der MVP-Lieferumfang ist **keine dauerhafte Positivliste**. Eine zukünftige Taskart ist nicht deshalb von der Standarddistribution ausgeschlossen, weil sie in dieser ersten Liste fehlt. Welche fachlichen Module ein Release mitliefert, ist eine Auslieferungsentscheidung. Die technische Erweiterungsschnittstelle behandelt alle gleich.

Publisher bleibt strategisch wichtig. Seine Bestandsanalyse, Lernpunkte, Architekturgrenzen und Spezifikation bleiben erhalten. Seine Implementierung, Bereitstellung und Publisherintegrationstests werden aus dem MVP ausgeklammert. Eine MVP-Installation behauptet die Fähigkeit nicht durch ein funktionsloses `Publisher`-Symbol. Dasselbe gilt für XtfToGpkg.

### DSL-007 — Hilfe als Teil der Taskqualität

`gretl operations` zeigt die vorhandenen Taskarten. `gretl operations HttpDownload` zeigt mindestens Beschreibung, Herkunft, Eingabefelder, Pflichtangaben, Vorgaben, erlaubte Auswahlwerte, Ergebnisfelder und ein kleines lauffähiges Beispiel. Es wird keine Task ausgeführt und keine Fachverbindung geöffnet.

`gretl run job.groovy --help` zeigt die deklarierte Bedienoberfläche dieses Jobs. Der Job wird dafür geladen; gespeicherte Tasks und Konfigurationen laufen nicht. Globales `--help` und `--version` bleiben ohne Groovy- und Providerinitialisierung möglich.

Mitgelieferte und eigene Tasks müssen sich an derselben Hilfe beteiligen können. Ein vollständiger Language Server oder eine grafische Joboberfläche gehört nicht zum MVP. Die expliziten Metadaten dürfen später für Referenzseiten, Vorlagen, Completion und Hover genutzt werden. Das verlangt keine zweite Taskbeschreibung in einem anderen Format.

## 4. Registrierung und Auswertungsregeln

### DSL-010 — Zwei Formen benannter Arbeit

Der MVP bietet:

- `task('name', Taskart, ...) { ... }` für eine Fachoperation mit erst beim Taskstart ausgewerteter Konfiguration;
- `action('name', ...) { ... }` für gewöhnlichen ausführbaren Groovy-Code mit einem möglichen Rückgabewert.

Eine Action kann mit `ctx.run(Taskart) { ... }` eine Fachoperation synchron aufrufen. Dieser ausdrückliche Kontextzugang gehört zum fortgeschrittenen Autorenweg; ein einfaches Taskrezept benötigt ihn nicht. Beide Formen verwenden denselben Runner, dieselbe Fehlersemantik und denselben Parameterbestand.

Eine eigene `doFirst`-/`doLast`-API wird nicht eingeführt. Benötigte Vorarbeit steht als eigener benannter Task oder in einer zusammengesetzten Action. Benötigte Nacharbeit gehört ebenfalls zum sichtbaren Ablauf und dessen Erfolgskriterium.

`defaults` akzeptiert eine oder mehrere Tasknamen; TaskRefs sind als Expertenform ebenfalls möglich. Ohne CLI-Ziel und ohne Standardziele ist `run` ein Konfigurationsfehler. Die Runtime rät nicht, welcher Task fachlich das letzte Ziel ist.

### DSL-011 — Optionen im Registrierungskopf

| Option | Typ und Zeitpunkt |
|---|---|
| `dependsOn` | Ein Taskname oder eine Liste von Tasknamen; bei fortgeschrittenem Code auch TaskRefs. Wird beim Laden registriert und nach vollständigem Laden aufgelöst. |
| `after` | Dieselbe Referenzform; reine Reihenfolge zwischen bereits ausgewählten Tasks. |
| `description` | String, beim Laden bekannt. |
| `when` | Gespeicherte Closure mit Ausführungsscope; unmittelbar vor dem Taskbody ausgewertet, Boolean erforderlich. |
| `timeout` | Positive Duration oder unterstützte ISO-8601-Dauer; begrenzt das geerbte Zeitbudget. |

Ein einzelner String wird als genau ein Name behandelt, niemals als Zeichenliste. Unbekannte Optionen und ungültige Listenelemente sind Fehler. Vorwärtsreferenzen über Namen sind erlaubt. Vollständige Auflösung und Zyklusprüfung erfolgen nach dem Laden.

Metadaten und Fachkonfiguration bleiben getrennt. Ein `dependsOn = ...` innerhalb des späten Konfigurationsblocks ändert keinen bereits geplanten Graphen. Ein solcher nicht registrierter Fachfeldname ist dort ein verständlicher Fehler. Die Runtime sucht nicht im AST nach Ergebniszugriffen, um Abhängigkeiten zu ergänzen.

### DSL-012 — Tatsächliche Groovy-Signaturen

Benannte Argumente werden von Groovy als Map übergeben. Die Integration muss deshalb passende Map-first-Überladungen anbieten:

```java
void parameter(Map<String, ?> options, String name);

TaskRef<?> action(String id, Closure<?> body);
TaskRef<?> action(Map<String, ?> options, String id, Closure<?> body);

<C, R> TaskRef<R> task(
    String id, OperationType<C, R> type, Closure<?> configure);

<C, R> TaskRef<R> task(
    Map<String, ?> options, String id,
    OperationType<C, R> type, Closure<?> configure);

void defaults(Object... taskNamesOrRefs);
void cleanup(String name, Closure<?> body);
```

Die Parameterdeklaration benötigt ihre Optionsmap, da mindestens eine Beschreibung erforderlich ist. Die einargumentige Leseform `parameter(String)` wird im Ausführungsscope angeboten und ist keine zweite Toplevel-Deklarationsform.

Die Signaturen gehören zur Groovy-Integration. Die reine Java-SPI enthält keine Closure. Der Ausdruck `task('entpacken', Extract, dependsOn: 'herunterladen') { ... }` muss als echter Groovy-Aufruf gegen diese Signaturen geprüft werden. Die ausgeschriebene Dokumentationsreihenfolge ist kein Ersatz für diesen Test.

### DSL-013 — Kompositer Konfigurationsscope

Die gespeicherte Closure wird für jeden Aufruf kopiert und mit `Closure.DELEGATE_ONLY` an einen frischen `TaskConfigurationScope` gebunden. Dieser Scope verbindet:

1. die vom OperationProvider ausdrücklich beschriebenen Konfigurationsfelder;
2. den festen Satz von Kontexthelpern aus DSL-020;
3. lesende Operationstyp-Symbole und den ausdrücklichen Expertenzugang `ctx`.

Ein gewöhnlicher Java-Builder allein ist deshalb nicht mehr der Closuredelegate. Die Groovy-Fassade vermittelt Feldzuweisungen über registrierte Konversionen und Setter. Sie speichert die konvertierten Werte in einem aufrufgebundenen Bestand, damit ein ausdrücklich gelesenes bekanntes Konfigurationsfeld den bisher gesetzten Wert beziehungsweise seine Vorgabe liefern kann. Es gibt keinen Owner-Fallback ins Script-Binding und keine still neu angelegten globalen Properties.

Die Feldvorgaben werden vor dem Body über denselben Konversions-/Settervertrag angewendet. Jede spätere Zuweisung ersetzt den bisherigen Feldwert; es gibt keine versteckte Listenaddition. Anschliessend werden erforderliche Felder und der vollständige Request geprüft. Feldübergreifende Regeln bleiben beim Builder beziehungsweise Fachadapter. Das Rückgeben einer Map ersetzt den Builder nicht. `return` beendet nur den aktuellen Block und ist kein Skip.

Closureblöcke ohne ausdrücklich benannten Parameter sind die Standardform. Null oder ein expliziter Closureparameter sind erlaubt; bei einem Parameter erhält er die aktuelle Context-Fassade. Mehrere Parameter sind ein DSL-Fehler. Die Runtime unterscheidet dabei auch explizite `{ -> ... }`-Closures von der üblichen impliziten Ein-Parameterform und ruft sie passend auf.

Actions, `when` und Cleanup erhalten einen eigenen Scope mit den für ihre Rolle erlaubten Helfern. Ein aufrufgebundener Kontext wird ausdrücklich übergeben; globale `currentTask`-Variablen oder ein ThreadLocal als alleinige Kontextquelle sind kein benötigter Implementierungsweg.

### DSL-014 — Namen und normale Groovy-Grenzen

Konfigurationszuweisungen werden nur gegen die registrierten Feldnamen aufgelöst. Konfigurationsmethoden ohne `=` werden nicht als zweite Settingoberfläche erraten. Kontexthelper haben feste Namen und Signaturen. Doppelte Feldnamen, unzulässige reservierte Namen und uneindeutige Registrierungen werden mit Herkunftsangabe abgelehnt.

Insbesondere sind `ctx`, `delegate`, `owner`, `thisObject`, `metaClass`, `class` sowie interne Registrierungsnamen keine frei belegbaren Konfigurationsfelder. Die Implementierung dokumentiert ihre vollständige kleine Reservierungsliste. Sie darf nicht vorsorglich beliebige fachliche Namen verbieten, nur weil ein gleichnamiger Helper durch einen eindeutig anderen Methodenaufruf adressierbar wäre.

Normale lexikalische Variablen bleiben Groovy-Variablen: `def url = ...` kann die Konfigurationseigenschaft `url` verdecken. `DELEGATE_ONLY` ändert das nicht. Grundrezepte benötigen keine solchen Variablen. Fortgeschrittene Beispiele verwenden andere lokale Namen oder den ausdrücklichen Kontextzugang. Ein optionaler späterer Linter darf häufige Kollisionen melden, ohne die Runtime als vollständige Sprachsandbox darzustellen.

## 5. Kontext und fortgeschrittene Grundfunktionen

### DSL-020 — Ein gemeinsamer typisierter Kontext

| Normaler Helper | Vertrag |
|---|---|
| `parameter(String)` | Typisierter deklarierter Jobparameter; unbekannter Name ist Fehler, optional fehlender Wert ist null. |
| `result(String)` | Erfolgreiches Vorgängerergebnis nach RUN-030; bei typisiertem Task lesende ResultView. |
| `resultOptional(String)` | Optional mit derselben Sicht; nur regulärer Skip oder erfolgreicher Abschluss ohne Wert ergibt leer. |
| `secret(String)` | Spät aufgelöste maskierte Secretreferenz aus der Runtimekonfiguration. |
| `file(String)` | Path relativ zum Jobroot beziehungsweise zu materialisierten Paketressourcen. |
| `path(String)` | Externer Path relativ zum ursprünglichen Start-CWD; absolute Angaben bleiben absolut. |
| `workPath(String)` | Path im Arbeitsbereich des aktuellen Tasks beziehungsweise Aufrufs. |
| `config(String)` | Unveränderliche Map aus `taskConfig[alias]`; kein Zugriff auf den Runtimekonfigurations-Root oder dessen rohen Parameterbestand. |
| `finishJob(String)` | Erfolgreiches Jobende wegen fehlender weiterer Daten anfordern; RUN-042. |
| `resources()` | Ressourcen-Scope des aktuellen Tasks beziehungsweise Aufrufs. |
| `log()` | Strukturierter Logger mit Run-/Taskkontext. |
| `report(Diagnostic)` | Ausdrücklich freigegebene Diagnose ohne Fachinterpretation durch den Core. |
| `progress(Progress)` | Begrenzter Fortschritt im aktuellen Aufruf; ändert keinen Status. |
| `sensitive(String)` | Dynamischen Geheimniswert vor weiterer Verwendung rungebunden zur Maskierung registrieren und denselben String zurückgeben. |
| `cancellation()` / `budget()` | Kooperatives Abbruchsignal und geerbtes Zeitbudget, vor allem für fortgeschrittene Actions. |

Die Context-Fassade unter `ctx` bietet dieselben Parameter-, Pfad- und Hilfsfunktionen. Zusätzlich besitzt sie `run(OperationType, Closure)` und den Expertenzugriff `result(TaskRef<R>)` beziehungsweise `resultOptional(TaskRef<R>)` auf unveränderte Javawerte. Der Java-Kontext hat für Parameter auch `parameter(String, Class<T>)` mit Laufzeittypprüfung. Die ältere rohe String-API `param`/`paramOr` wird entfernt.

`ctx.run` liefert das tatsächliche Javaresultat des synchronen Aufrufs. Dadurch können Entwickler dessen eigene API verwenden; für die gewöhnliche Verbindung benannter Tasks bleibt `result('name').feld` kanonisch. Bei verschachtelten Aufrufen verwendet die Konfigurationsclosure den neuen Aufrufscope. Ein ausdrücklich eingefangenes äusseres `ctx` bleibt die normale lexikalische äussere Variable; Beispiele rufen darin Pfadhelper deshalb unqualifiziert auf.

### DSL-023 — Logging ohne zusätzliches Anwendergerüst

Einfache Rezepte benötigen keine Loggerkonfiguration. Die Runtime protokolliert Taskname, Phase, Beginn, Ende und Fehler. Fortgeschrittener Code kann `log().info(...)`, `log().warn(...)` oder `progress(...)` verwenden. Der vollständige TaskLogger-Vertrag steht in Kapitel 14, die Ausgabe und Fehlermaskierung in Kapitel 16.

`println` der eigenen Scriptbasisklasse und Ausführungsscopes wird als Anwendernachricht geroutet; der Adapter ergänzt dafür ausdrücklich die passende `println`-Methode. Direkte fremde `System.out`-/`System.err`-Aufrufe werden nicht global abgefangen. Ein vollständig sauberer JSON-Kanal setzt entsprechend geprüfte Bibliothekspfade voraus. Weder ein ERROR-Log noch ein Fortschrittseintrag ersetzt Exception beziehungsweise Fachresultat.

Die Kontexte unterstützen alle allgemeinen Helper nur innerhalb ihrer jeweiligen Lebensdauer. `CleanupScope` besitzt ausdrücklich nur die in API-030/CORE-030 festgelegte Teilmenge und startet keine neue Operation. Ein aufrufgebundenes inneres `ctx.run` erhält seine Identität bereits **vor** Auswertung seiner Konfigurationsclosure, damit dortige Fehler und Logs dem richtigen Aufruf zugeordnet sind.

### DSL-021 — Laufzeitwerte ohne zusätzliche Provider

Ein später Schritt darf URLs, Dateinamen, Zahlen, Listen oder Fachwerte erst nach seinen Vorgängern berechnen. Dafür wird die Konfigurationsclosure spät ausgeführt. Ein `result`-Aufruf erzeugt keinen Provider und keine neue Abhängigkeit.

Auch eine innerhalb dieses Blocks erzeugte Stringinterpolation wird zu diesem Zeitpunkt ausgewertet. Ein schon im Script-Toplevel erzeugter String bekommt nicht nachträglich Zugriff auf ein künftiges Ergebnis. Die Runtime beobachtet keine beliebigen veränderlichen globalen Jobvariablen.

Wiederverwendbare Operationen liefern dokumentierte, möglichst unveränderliche Javawerte. Actions dürfen normale JVM-Werte zurückgeben. Offene Connections, Streams und Datastores sind keine Resultate der mitgelieferten Tasks.

### DSL-022 — Grenzen des Ladens

Code ausserhalb der registrierten Closures läuft beim Laden. Das gilt auch für eine Schleife, welche mehrere Tasks registriert. Solcher Code soll nur Definitionen aus lokalen, bereits bekannten Informationen erzeugen.

Ein frei programmierter Download oder eine Datenbankabfrage im Toplevel hätte auch bei `check`, `plan` und Jobhilfe Nebenwirkungen. Der MVP verspricht keine Sandbox für beliebigen Groovy-Code. Die Runtime selbst führt dort keine Taskarbeit aus und bietet keinen Toplevel-Aufruf von `ctx.run`.

Zur Laufzeit neu entdeckte Dateien werden innerhalb einer bestehenden Action mit synchronen Fachaufrufen verarbeitet. Sie registrieren keine zusätzlichen öffentlichen DAG-Knoten. Ein Paketbuild kompiliert den Job und sammelt ausdrücklich deklarierte Ressourcen; er führt weder den Scriptbody noch einen vermeintlich nebenwirkungsfreien Plan zur Paketerkennung aus.

## 6. Mindestverträge der mitgelieferten Taskarten

Die Felder in diesem Abschnitt sind verbindliche öffentliche Settings der ersten Version. Der Provider beschreibt dieselben Namen, Typen, Vorgaben und Ausgänge für Hilfe und Feldbindung. Zusätzliche Optionen werden nur mit einem eigenen geprüften Vertrag ergänzt; unbekannte Optionen werden nicht still ignoriert.

### DSL-050 — HttpDownload

| Feld | Typ | Vorgabe und Vertrag |
|---|---|---|
| `url` | URI oder konvertierbarer Text | Pflicht; absolute HTTP-/HTTPS-Adresse. |
| `output` | Path | Pflicht; Ziel einer vollständig heruntergeladenen Datei. |
| `method` | `GET` oder `POST` | `GET`. |
| `headers` | Map aus Headernamen und Text-/Secretreferenzwerten | Leere Map; vollständige Mapzuweisung. |
| `bodyText` | Text | Optionaler UTF-8-Body nur für POST. |
| `maxBytes` | Positive Long-Zahl | `1073741824L` = 1 GiB. |

Der Adapter konvertiert URL-Text ausdrücklich zu URI. Ein fehlerhafter Typ, unzulässiges Schema, widersprüchlicher Body oder ungültiger Header wird vor Beginn des HTTP-Aufrufs gemeldet. Headernamen werden bei Konflikten ohne Beachtung ihrer Gross-/Kleinschreibung geprüft. Headerwerte mit Secretreferenzen werden erst beim konkreten Aufruf aufgelöst; Authorization und andere bekannte Secretwerte erscheinen nicht im Log.

Timeout und Cancellation kommen aus dem Aufrufkontext. Der Adapter schreibt zuerst eine eigene temporäre Datei, zählt tatsächlich gelesene Bytes und stellt den Zielpfad erst nach erfolgreichem Abschluss bereit. HTTP-Status ausserhalb 200–299, überschrittenes Limit, abgebrochene Übertragung und unzulässige URI sind Fehler. Das Ziel wird nicht still überschrieben.

Redirects sind im MVP deaktiviert. Eine spätere Erweiterung muss insbesondere die Weitergabe von Authorization an einen anderen Host regeln. POST und ganze Downloads werden nicht automatisch wiederholt.

Javaresultat: `DownloadResult(Path file, URI finalUri, int status, long bytes, String sha256)`. Alle fünf Namen sind als nicht nullierbare Ergebnisfelder registriert. `file` bezeichnet die abgeschlossene Datei, `bytes` ihre tatsächliche Länge. Der Responsebody wird nicht automatisch geloggt.

### DSL-051 — Extract

| Feld | Typ | Vorgabe und Vertrag |
|---|---|---|
| `archive` | Path | Pflicht; vorhandene Eingabedatei. |
| `compression` | `ZIP` oder `GZIP` | Pflicht; keine Erkennung allein aus der Endung. |
| `outputDirectory` | Path | Genau für ZIP erforderlich. |
| `outputFile` | Path | Genau für GZIP erforderlich. |
| `maxExpandedBytes` | Positive Long-Zahl | `4294967296L` = 4 GiB. |
| `maxEntries` | Positive Ganzzahl bis `2147483647` | `10000`; Grenze für ZIP-Einträge einschliesslich Verzeichniseinträge. |

ZIP verlangt `outputDirectory` und kein `outputFile`; GZIP verlangt `outputFile` und kein `outputDirectory`. Die Ausgabepfade sind ausdrücklich. Vorhandene Zieldateien werden nicht still überschrieben.

Pflicht sind die Grössen-/Eintragslimits sowie Schutz vor Traversal, absoluten Pfaden, Symlinks und kollidierenden Dateinamen. Eine ZIP-Bibliothek muss die nötigen Eintragsmetadaten zugänglich machen. Einträge dürfen den erlaubten Ausgabeort nicht verlassen.

Javaresultat: `ExtractResult(Path file, List<Path> files, Path directory, long bytes)`.

| Ergebnisfeld | GZIP | ZIP |
|---|---|---|
| `file` | Abgeschlossene einzelne Ausgabedatei | null |
| `files` | Unveränderliche Liste mit genau dieser Datei | Unveränderliche Liste der abgeschlossenen regulären Dateien; keine Verzeichniseinträge |
| `directory` | null | Ausgabe-Root |
| `bytes` | Tatsächliche entpackte Dateilänge | Summe der tatsächlich geschriebenen regulären Dateilängen |

Die ZIP-Dateiliste ist nach dem relativen Pfad mit `/` als Trenner lexikalisch sortiert; Dateisystemauflistungsreihenfolge bestimmt sie nicht. Ein leeres gültiges ZIP darf eine leere Liste und `bytes=0` liefern. Nullable sind nur `file` und `directory` entsprechend dem Modus. Ein Fehler liefert kein erfolgreiches ExtractResult; unvollständige Arbeitsdateien dürfen für Diagnose verbleiben.

### DSL-052 — SqlExecutor

Der MVP unterstützt PostgreSQL über einen Alias aus der Runtimekonfiguration.

| Feld | Typ | Vertrag |
|---|---|---|
| `database` | Text | Pflicht; Alias einer vorbereiteten PostgreSQL-Verbindung. |
| `scripts` | Liste von Path | Geordnete vollständige UTF-8-Scriptdateien; standardmässig leer. |
| `statements` | Liste ausdrücklich beschriebener Statementwerte | Geordnete PreparedStatements; standardmässig leer. |

Genau eine der beiden Listen muss nicht leer sein. Eine Liste ersetzt die Einstellung vollständig; wiederholte `script(...)`- oder `statement(...)`-Setter sind nicht Teil der DSL.

Die einfache Statementform verwendet Maps mit **genau** den Feldern `sql` und `parameters`:

```groovy
parameter('gemeinde', type: 'integer', required: true,
          description: 'BFS-Nummer der Gemeinde')

task('kennzeichnen', SqlExecutor) {
    database = 'edit'
    statements = [
        [sql: 'UPDATE demo.gemeinde SET geprueft = ? WHERE bfs_nr = ?',
         parameters: [true, parameter('gemeinde')]]
    ]
}

defaults 'kennzeichnen'
```

Der SQL-Adapter konvertiert diese ausdrücklich unterstützte Mapform zu einem `PreparedStatementSpec(String sql, List<?> parameters)`. Sie ist ein kleiner fachlicher Wertvertrag, kein allgemeines Schema für beliebig verschachtelte DSL-Unterbuilder. Unbekannte Mapfelder, fehlende Listen und unzulässige Parameterwerte sind klare Requestfehler. Nach der oben beschriebenen Textnormalisierung bindet die erste Version null, String, Boolean, Byte, Short, Integer, Long und BigDecimal. Weitere Typen benötigen eine ausdrückliche Erweiterung mit eigenen Konversionstests. Schema-/Tabellennamen sind keine bindbaren Datenparameter.

Die Bindung erfolgt positionsgetreu ab JDBC-Index 1: String über `setString`, Boolean über `setBoolean`, Byte über `setByte`, Short über `setShort`, Integer über `setInt`, Long über `setLong`, BigDecimal über `setBigDecimal` und null über `setNull(index, java.sql.Types.NULL)`. Es gibt keine numerische Umwandlung über Text oder Double. Muss PostgreSQL den Typ eines null-Werts aus dem SQL genauer kennen, gibt das vertrauenswürdige Statement ihn beispielsweise mit einem ausdrücklichen CAST vor; die Runtime errät keinen Spaltentyp. BigInteger, Float, Double sowie Datums-/Zeittypen sind damit nicht still Teil dieses ersten Bindervertrags.

Im Scriptmodus gibt es keine Runtime-Textsubstitution. Der Adapter erfindet keinen Regex-SQL-Splitter, sondern übergibt vollständige vertrauenswürdige Scripts über den qualifizierten JDBC-Pfad und verarbeitet alle Resultate beziehungsweise Updatecounts. `psql`-Metakommandos gehören nicht zu JDBC-Scripts.

Eine Operation verwendet eine eigene Connection mit deaktiviertem Auto-commit. Alle Statements beziehungsweise Scripts dieses Aufrufs bilden eine Transaktion, soweit PostgreSQL die verwendeten Statements darin zulässt. Normaler Abschluss führt genau einmal zum Commit. Fehler führen zu einem Rollbackversuch; ein zusätzlicher Rollbackfehler bleibt sichtbar.

Eigene Top-level-Transaktionssteuerung wie BEGIN, COMMIT und ROLLBACK sowie das Ändern des Auto-commit durch den Scriptinhalt sind aus diesem Scriptvertrag ausgeschlossen. Der Adapter behauptet nicht, diese Grenze gegenüber beliebigen SQL-Programmen erzwingen zu können; zur Erkennung wird kein naiver Regexparser eingeführt. Mitgelieferte Scripts und Migrationsfixtures werden auf den Vertrag geprüft. Eigenständige Transaktionssteuerung wäre später eine ausdrücklich andere Capability.

Bei Verbindungsverlust während commit kann der Datenbankausgang unklar bleiben. Der Adapter meldet das in einer eigenen Exception und einer ausdrücklich freigegebenen Diagnose. Er wiederholt nicht automatisch den Aufruf. Der Runner interpretiert den Datenbankstatus nicht; ein unbehandelter Fehler ergibt den allgemeinen Code 10.

Javaresultat: `SqlResult(int executionCount, List<SqlExecutionResult> executions)`. `executionCount` ist die Zahl der übergebenen Script-/Statementeinträge, nicht eine durch SQL-Parsing geschätzte Zahl einzelner Befehle. `executions` enthält in Eingabereihenfolge `SqlExecutionResult(int index, List<Long> updateCounts, int resultSetCount)`, mit nullbasiertem Index. Die beiden obersten Felder sind dokumentierte Ausgänge; enthaltene Listen sind defensiv kopiert. Erzeugte ResultSets werden verarbeitet beziehungsweise verworfen und geschlossen. Ein offener ResultSet oder ein generisches Streaming-Query-API gehört nicht zum MVP.

### DSL-053 — IliValidator

IliValidator gehört zu Lieferstufe B und verwendet die reale Validatorbibliothek in der qualifizierten JVM.

| Feld | Typ | Vertrag |
|---|---|---|
| `dataFile` | Path | Pflicht; vorhandene XTF-Datei. |
| `modelDirectory` | Path | Pflicht; vollständiger lokaler Modellbestand für diesen Transfer. |
| `validationConfig` | Path | Optional; ausdrücklich unterstützte lokale Validierungskonfiguration. |

Die erste Fähigkeit validiert eine Transferdatei mit lokalen Modellen. Sie lädt keine neuen Taskbibliotheken nach. Weitere Engineoptionen oder Modellrepositoryfähigkeiten werden erst mit ihrer ausdrücklichen Unterstützung Bestandteil des Tasks; unbekannte Optionen werden nicht durchgereicht oder ignoriert.

Ein fachlich ungültiger Transfer lässt den Aufruf fehlschlagen und liefert zugängliche Validierungsdiagnosen. Der Handler schreibt einen aufrufbezogenen Prüfbericht in seinen Arbeitsbereich und meldet dessen Pfad bei Erfolg oder Fehler ausdrücklich als Diagnoseartefakt. Das ist ein eigener Adaptervertrag, kein im Core bekanntes Modellformat.

Javaresultat bei Erfolg: `ValidationResult(Path file, Path reportFile, long warnings)`. Alle drei Felder sind als nicht nullierbare Ausgänge registriert. `file` ist die validierte Eingabe, `reportFile` der abgeschlossene Bericht, `warnings` die Zahl der vom Adapter eindeutig erfassten Warnungen. Zählung und fachliche Einstufung werden gegen die echte Engine geprüft. Ein Fehler erzeugt kein vorgetäuschtes erfolgreiches Resultat.

XtfToGpkg, Import und Export können dieselbe SPI verwenden. Sie gehören erst nach dem MVP mit ihren konkreten Requests, Fachtests und Engineversionen zum zugesagten Taskbestand. Eine tatsächlich erforderliche eigene JVM wäre eine geprüfte Eigenschaft des betreffenden Adapters, keine vorsorgliche Runtimepflicht.

## 7. Eigene Aktionen: der fortgeschrittene Autorenweg

### 7.1 Token, Katalog und erst später bekannte Downloadadresse

Das folgende vollständige Beispiel zeigt bewusst fortgeschrittenen Groovy-Code. Es verwendet einen lokalen Testserver mit `access_token` und einem Katalog aus `files` mit `date` und `url`; es ist kein unverändert produktiv lauffähiger Infoflora-Job.

```groovy
import groovy.json.JsonSlurper
import java.nio.file.Files

parameter('tokenUrl', required: true,
          description: 'Adresse des Test-Tokenservices')
parameter('catalogUrl', required: true,
          description: 'Adresse des Test-Dateikatalogs')

action('token') { ctx ->
    def response = ctx.run(HttpDownload) {
        method = 'POST'
        url = parameter('tokenUrl')
        headers = [
            'Authorization': secret('sourceAuth'),
            'Content-Type': 'application/x-www-form-urlencoded'
        ]
        bodyText = 'grant_type=client_credentials'
        output = workPath('token.json')
        maxBytes = 65536L
    }

    def json = new JsonSlurper().parseText(Files.readString(response.file()))
    if (!(json.access_token instanceof String) || json.access_token.isBlank()) {
        throw new IllegalArgumentException('Tokenantwort ohne access_token')
    }
    return ctx.sensitive(json.access_token as String)
}

task('catalog', HttpDownload, dependsOn: 'token') {
    url = parameter('catalogUrl')
    headers = ['Authorization': 'Bearer ' + result('token')]
    output = workPath('catalog.json')
    maxBytes = 1048576L
}

action('selectDownload', dependsOn: 'catalog') {
    def json = new JsonSlurper().parseText(
        Files.readString(result('catalog').file))
    if (!(json.files instanceof List)) {
        throw new IllegalArgumentException('Katalog ohne files-Liste')
    }
    if (json.files.isEmpty()) {
        finishJob('Keine Quelldateien im Katalog')
    }

    def candidates = json.files.collect { item ->
        [date: java.time.LocalDate.parse(item.date as String),
         uri: java.net.URI.create(item.url as String)]
    }
    return candidates.max { it.date }.uri
}

task('download', HttpDownload, dependsOn: 'selectDownload') {
    url = result('selectDownload')
    output = workPath('data.gz')
}

task('unpack', Extract, dependsOn: 'download') {
    archive = result('download').file
    compression = 'GZIP'
    outputFile = workPath('data.csv')
}

defaults 'unpack'
```

Die HTTP-Operation innerhalb der Action liefert ihr unverändertes Javaresultat; daher ist dort `response.file()` ein normaler Entwicklerzugriff. Die benannte Verbindung zum späteren Task verwendet dagegen `result('catalog').file`. Beide lesen dasselbe fachliche Ergebnis über die jeweils dokumentierte Oberfläche.

Tokenantwort, Katalogauswertung und Downloadadresse werden erst während der Ausführung bestimmt. Tokenwerte werden nicht automatisch in den Runbericht aufgenommen. Die kleinen JSON-Antworten haben ausdrückliche Grössenlimits. Gleich datierte Katalogeinträge benötigen für einen produktiven Fachadapter eine festgelegte Auswahlregel; der Testfixture hat eindeutige Datumswerte.

Für reine Fachanwender sollte häufig wiederkehrende Token-/Katalogarbeit in einer gepflegten Taskbibliothek gekapselt werden. Weniger Satzzeichen machen eine solche fachliche Fallunterscheidung nicht automatisch zu einem einfachen Anwenderrezept.

### 7.2 Vor- und Nacharbeit in einem fachlichen Erfolgsschritt

```groovy
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files

parameter('sourceUrl', required: true,
          description: 'Adresse der zu normalisierenden Textdatei')

action('downloadAndNormalize') { ctx ->
    def downloaded = ctx.run(HttpDownload) {
        url = parameter('sourceUrl')
        output = workPath('source.tsv')
    }

    def normalizedFile = workPath('normalized.csv')
    Files.newBufferedReader(
        downloaded.file(), Charset.forName('windows-1252')
    ).withCloseable { reader ->
        Files.newBufferedWriter(
            normalizedFile, StandardCharsets.UTF_8
        ).withCloseable { writer ->
            reader.eachLine { line ->
                writer.write(line.replace('\t', ';'))
                writer.newLine()
            }
        }
    }
    return normalizedFile
}

defaults 'downloadAndNormalize'
```

Dies ist kein allgemeiner TSV-/CSV-Parser. Quote-Regeln, eingebettete Trennzeichen und Zeilenumbrüche benötigen einen geeigneten Formatparser. Ein echtes Migrationsfixture prüft, ob die bisherige einfache Ersetzung fachlich genügt.

Erst nach geschlossenem Writer wird die Ausgabe als Resultat registriert. Scheitert die Konvertierung oder eine erforderliche Freigabe, ist die Action fehlgeschlagen. Ein erfolgreicher Download allein erfüllt ihren Vertrag nicht. Wiederkehrende Umcodierungen gehören zweckmässig in eine eigene Taskart.

## 8. Skip, Fehler und Cleanup

### DSL-040 — Optionaler Schritt

```groovy
parameter('extraCheck', type: 'boolean', defaultValue: false,
          description: 'Zusatzprüfung einschalten')

action('optionalCheck', when: { parameter('extraCheck') }) {
    return 'checked'
}

action('continueWork', dependsOn: 'optionalCheck') {
    def extra = resultOptional('optionalCheck')
    log().info('Zusatzprüfung vorhanden: ' + extra.isPresent())
    return 'done'
}

defaults 'continueWork'
```

`when` wird vor dem Body im Ausführungsscope ausgewertet. Ein false-Predicate produziert keinen Wert. Der normale Nachfolger darf starten und behandelt das optionale Resultat ausdrücklich. Eine optionale Einstellung ohne Boolean-Vorgabe könnte null ergeben; `when` verlangt jedoch tatsächlich Boolean und errät daraus keinen gewünschten Zustand.

### DSL-041 — Job-Cleanup

`cleanup('name') { ... }` registriert beim Laden eine Job-Cleanup-Aktion. Ihr eingeschränkter Scope erlaubt Logging, typisierte Parameter, allgemeine Konfiguration, bekannte Workspacepfade und lokale Ressourcenfreigabe. Er registriert oder startet keine neuen Tasks und besitzt kein `ctx.run`.

Cleanup wird auch nach Fehlern ausgeführt. Fachliche Quittierungen wie das Löschen einer erfolgreich verarbeiteten Quelle bleiben daher normale Erfolgsschritte. Ein `finally` innerhalb einer Action ist für einen lokalen Lebensdauerbereich oft die einfachere Wahl.

Die registrierten Aktionen sind im MVP erforderlich. Ihr unbehandeltes Scheitern wird nach RUN-050/051 als Fehler behandelt. Wer einen rein diagnostischen Aufräumfehler bewusst toleriert, behandelt ihn ausdrücklich im Body und protokolliert eine Warnung. Fehlerursachen bleiben erhalten. Vollständige Gradle-`finalizedBy`-Semantik gehört nicht zum MVP.

### DSL-042 — Normale Fehlerbehandlung und Runtimekontrolle

`ctx.run` propagiert fachliche Exceptions an den aufrufenden Body. Eine Action darf eine dokumentierte Exception bewusst behandeln und danach erfolgreich enden. Es gibt weder für Publisher noch für SQL eine versteckte globale Fehlerpflicht, die `try/catch` nachträglich wirkungslos macht.

Scheitert nach einer gelungenen Operation eine Nachprüfung, ist der ganze umgebende Schritt fehlgeschlagen. Die Runtime behauptet keine Rückabwicklung bereits eingetretener Wirkungen. Unbehandelte Fachfehler und erforderliche Freigabefehler bleiben Fehler.

`finishJob` und Cancellation sind allgemeine Runtimekontrolle. Das erfolgreiche Ende wegen fehlender Daten bleibt im Kontext vermerkt, auch wenn Scriptcode das interne Signal versehentlich fängt. Weitere `ctx.run`-Aufrufe und weitere normale Taskstarts sind dann gesperrt. `finally` und Cleanup laufen; echte Freigabefehler haben Vorrang vor dem beabsichtigten NO_DATA. Die vollständige Präzedenz steht in RUN-042 und RUN-052.

## 9. Publisher: Zielbeispiele nach dem MVP

### DSL-030 — Gewöhnlicher Custom-Task, später geliefert

Die nachfolgenden Beispiele bleiben als Zielvertrag erhalten. Sie setzen eine **nach dem MVP** bereitgestellte Publisherbibliothek voraus. Sie sind keine Abnahmeanforderung der MVP-Distribution.

```groovy
parameter('eingabe', type: 'path', required: true,
          description: 'Zu publizierende XTF-Datei')
parameter('ausgabe', type: 'path', required: true,
          description: 'Publikationsverzeichnis')

action('prepare') {
    def candidate = parameter('eingabe')
    if (!java.nio.file.Files.isRegularFile(candidate)) {
        finishJob('Keine abgeschlossene Eingabedatei vorhanden')
    }
    return candidate
}

task('publish', Publisher, dependsOn: 'prepare') {
    dataFile = result('prepare')
    targetDirectory = parameter('ausgabe')
    dataIdent = 'ch.so.demo'
    modelDirectory = file('models')
    userFormats = false
}

action('reportPublication', dependsOn: 'publish') {
    log().info('Publisher erfolgreich abgeschlossen')
    return result('publish').publishedFiles
}

defaults 'reportPublication'
```

Die Einstellungen und das Resultat gehören der Publisherbibliothek. Der Runner kennt weder `dataIdent` noch Modelle, Regionen oder Formate. Ihre öffentliche API wird über denselben Metadatenvertrag beschrieben wie die eines selbst geschriebenen SqlExecutor-ähnlichen Tasks. Der begrenzte Port steht weiterhin in [05](05-publisher-zielvertrag.md).

Der Pathparameter wird vor dem Run formal aufgelöst, aber nicht auf Existenz geprüft. Deshalb kann die Action eine tatsächlich fehlende Eingabedatei fachlich als NO_DATA einordnen. Der Publisher selbst darf eine fehlende Pflichtdatei weiterhin als Fehler behandeln. Welche Behandlung fachlich passend ist, entscheidet der Jobautor.

### DSL-031 — Publisheraufruf und Nachprüfung zusammenfassen

Die Parameterdeklarationen entsprechen dem vorherigen Beispiel:

```groovy
action('publishAndVerify') { ctx ->
    def published = ctx.run(Publisher) {
        dataFile = parameter('eingabe')
        targetDirectory = parameter('ausgabe')
        dataIdent = 'ch.so.demo'
        modelDirectory = file('models')
        userFormats = false
    }
    // Hier eine zusätzliche fachliche Prüfung; bei Fehler Exception werfen.
    return published
}

defaults 'publishAndVerify'
```

Scheitert die Nachprüfung, ist die Action fehlgeschlagen, obwohl der Publisher bereits etwas verändert haben kann. Fängt der Job dagegen eine dokumentierte Publisherexception bewusst, verarbeitet ihre Fachinformation und kehrt normal zurück, darf die Action erfolgreich sein. Es gibt kein PublicationObligation-Gate.

Eine erforderliche Reparatur kann ein weiterer gewöhnlicher Fachaufruf oder ein späterer gewöhnlicher Wartungsjob sein. Dafür entstehen keine Publisherbefehle in der CLI. Die aus der Bestandsanalyse bekannten Risiken, Grenzen und Portierungsanforderungen bleiben trotz des späteren Liefertermins vollständig fachlich relevant.

## 10. Eigene Taskbibliotheken programmieren

### DSL-060 — Eine reine Java-SPI mit vollständigem Vertrag

Die **einzige normative Java-Definition** einschliesslich Methoden, Generics, Nullability, Exceptions und Konstruktorvalidierung steht in [14 — Java-API](14-java-api-vertraege.md). Dort sind alle 27 Anfangsdateien ausgeschrieben. `OperationHandler<C,R>.execute(C, OperationContext)` und `ConfigBuilder<C>.build()` bleiben die kleine fachliche Grenze. Keine Groovy-, SLF4J-, OpenTelemetry- oder Gradletypen im API-JAR.

`InputField`, `OutputField` und `OperationType` verbinden ausführbare Konversion/Accessor mit Metadaten. Sie werden nicht zusätzlich in einer von Hand gepflegten JSON-Taskliste dupliziert. CLI-Hilfe und DSL lesen dieselben Definitionen. Die Klasse `Fields` reduziert wiederkehrende typisierte Bindung; sie ist eine Convenience-Factory und keine zweite SPI. Keine Annotationsuche, kein Annotation Processor und keine nach Namen reflektierten Setter sind dafür nötig.

Eine Void-Operation deklariert `Void.class`, liefert null und bietet keine Ausgänge. Eine nicht-Void-Operation liefert den deklarierten Resultattyp; ein unerwartetes null ist `NULL_OPERATION_RESULT`. Modebedingt nullable **einzelne Felder** wie bei Extract sind davon unabhängig. `OutputField.read` wird ausschliesslich beim tatsächlichen Zugriff aufgerufen und muss rein lesend bleiben.

### DSL-061 — Kleine eigene Taskbibliothek in einer Quelldatei

Für einen einfachen Task ist nicht zwingend eine Datei je Request, Resultat, Builder und Handler nötig. Die folgende **tatsächlich kompilierte Referenz** bündelt drei kleine öffentliche verschachtelte Typen und einen kleinen Handler in einem Provider. Grössere Fachimplementierungen können dieselben Rollen weiterhin auf mehrere Dateien verteilen.

<!-- java-extension-reference: ExampleProvider.java -->
```java
package example;

import ch.so.agi.gretl.runtime.api.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public final class ExampleProvider implements OperationProvider {
    public record Request(Path input) {}
    public record Result(long bytes) {}

    public static final class Builder implements ConfigBuilder<Request> {
        private Path input;
        public void setInput(Path value) { input = value; }
        @Override public Request build() {
            if (input == null) throw new IllegalArgumentException("input fehlt");
            return new Request(input.toAbsolutePath().normalize());
        }
    }

    @Override public Collection<OperationType<?, ?>> operations() {
        InputField<Request, Path> input = Fields.requiredPath(
            "input", "Datei, deren Grösse gelesen wird", Builder.class, Builder::setInput);
        OutputField<Result, Long> bytes = Fields.output(
            "bytes", "integer", "Anzahl Bytes der Datei", false, Result::bytes);
        return List.of(new OperationType<Request, Result>(
            "example.file-size", "FileSize", "Dateigrösse lesen",
            Request.class, Result.class, Builder::new,
            () -> new OperationHandler<Request, Result>() {
                    @Override
                    public Result execute(Request request, OperationContext ctx)
                            throws Exception {
                        ctx.cancellation().throwIfCancellationRequested();
                        return new Result(Files.size(request.input()));
                    }
                },
            List.of(input), List.of(bytes),
            "task('messen', FileSize) { input = file('input.xtf') }\n"
                + "defaults 'messen'\n"));
    }
}
```

`Fields.requiredPath` kapselt die sichere Builderbindung über den explizit angegebenen Buildertyp. Der Custom-Task-Entwickler schreibt keinen wiederholten ungeprüften Cast. Er kontrolliert weiterhin seinen eigenen fachlichen Request und die Handlerlogik. Es wird kein Framework für beliebige Java-Beans eingeführt.

Die Datei `META-INF/services/ch.so.agi.gretl.runtime.api.OperationProvider` enthält genau:

```text
example.ExampleProvider
```

Ein Anwender benötigt nur:

```groovy
parameter('eingabe', type: 'path', required: true,
          description: 'Datei, deren Grösse gelesen wird')

task('messen', FileSize) {
    input = parameter('eingabe')
}

action('melden', dependsOn: 'messen') {
    log().info('Dateigrösse: ' + result('messen').bytes + ' Bytes')
}

defaults 'melden'
```

Ein getrennt gebautes Erweiterungs-JAR und ein separater Prüfprozess haben ServiceLoader-Entdeckung, Factory-/Konverterverwendung und eine echte Dateigrössenberechnung gegen die API-Referenz geprüft. Das ist **noch kein** Test des endgültigen Groovy-Runners. Die entsprechenden tatsächlichen Nachweise und Grenzen stehen in [17](17-findings-und-pruefprotokoll.md). M2/M3 müssen das hier gezeigte Groovy-Rezept und `gretl operations FileSize` zusätzlich mit dem echten Produkt ausführen.

### DSL-062 — Registrierung, Abhängigkeiten und Sprachgrenze

`OperationProvider` wird mit Java-ServiceLoader aus dem ausdrücklich bereitgestellten Klassenpfad geladen. Er liefert Definitionen, öffnet keine Datenbanken, startet keine Engine und liest keine Secrets. Builder und Handler werden pro Aufruf frisch erzeugt.

Doppelte IDs oder Symbole sind Fehler mit beiden Herkunftsbibliotheken. Classpathreihenfolge bestimmt keinen Gewinner. Die Runtime bietet nicht mehrere konkurrierende Versionen derselben Taskart in einem Run an. Konflikte mit internen Bindings und dokumentierte Reservierungen werden vor Ausführung gemeldet.

Erweiterungs-JARs und ihre Dependencies stehen vor Start bereit. Es gibt kein implizites `@Grab`; Grape wird in der Compilerkonfiguration deaktiviert. Das ist eine Auslieferungsregel, keine Sandbox gegen selbst programmierte Netzaufrufe. Kapitel 06 beschreibt Standarddistribution und Erweiterungsbestand, Kapitel 12 die Auswahl für ein Job-Fat-JAR.

Die reine Java-SPI kennt keine Groovy-Closure. Die Groovy-Fassade bindet Configurationscopes, stellt ResultViews bereit und ergänzt `ctx.run`. Der Java-Invoker verarbeitet einen gebauten Request und Handler. Die Context-Schnittstelle ist kein Dependency-Injection-Container: Sie liefert allgemeine Laufzeitdienste; die Taskbibliothek besitzt ihre JDBC-, Publisher- oder GeoTools-Fachservices selbst.

Eigene Bibliotheken dürfen in Java, Groovy oder einer anderen JVM-Sprache implementiert sein. Wiederverwendbare Bibliotheken werden regulär gebaut. Lokaler einmaliger Groovy-Code bleibt als Action möglich, ohne Providerklasse. Ein gemeinsamer JVM-Klassenpfad muss konsistent sein; ein Maven-Modul allein löst keine Versionskonflikte. Eine getrennte JVM bleibt eine spätere, gezielt geprüfte Adapteroption.

## 11. Migration der beobachteten Hookmuster

Die Zuordnung stammt aus konkreten Jobdateien. Sie ist keine vollständige Zählung sämtlicher Hooks und keine Behauptung automatischer Vollmigration.

| Beobachtetes Muster | Neue Darstellung | Grenze |
|---|---|---|
| Token nach HTTP-Aufruf aus JSON lesen | Action mit synchronem HttpDownload und explizitem Tokenresultat; für Anwender später gepflegte Fachoperation | Lesen und Tokenprüfung gehören zum Erfolg der Action. |
| Download-URL erst aus Katalog bekannt | Spät konfigurierter HttpDownload mit `result('name')` | Explizites `dependsOn` bleibt erforderlich. |
| Zeichensatz und Trennzeichen nach Download ändern | Zusammengesetzte Action oder eigene Transformationsoperation | Downloadsuccess genügt nicht für den fachlichen Gesamterfolg. |
| Datasetnamen vor Import prüfen | Vorprüfung oder Requestvalidierung nach Laufzeitauflösung | Nicht auf eine schwer verständliche Engineexception warten. |
| Verzeichnis vor Facharbeit anlegen | Aufrufarbeitsbereich oder ausdrückliche Vorarbeit | Keine gemeinsamen globalen Tempordner. |
| Bei leerer FTP-Liste alle späteren Tasks deaktivieren | `finishJob` nach erfolgreich bestimmter leerer Liste | Ein Transportfehler ist keine leere Liste. |
| Optionale Arbeit abhängig von Umgebung | `when` mit deklariertem typisiertem Parameter | Ein regulärer Skip beendet nicht den Job. |
| `publishedRegions` weiterreichen | Später ein dokumentierter Ausgang der Publisherbibliothek | Regionensemantik bleibt beim Fachmodul; Publisher wird nach dem MVP geliefert. |
| Unabhängig vom Erfolg aufräumen | Lokales finally oder bewusst gewähltes Cleanup | Quellquittierung bleibt ein normaler Erfolgsschritt. |

Belege am untersuchten Stand:

- [Neophyten: Token, Katalog und Laufzeitdownload](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/afu_neophyten_pub/build.gradle).
- [MOCheckSO: Umcodierung und Trennzeichen im doLast](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_mocheckso/build.gradle).
- [Schutzbauten: Vorprüfung des Datasetnamens](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/afu_schutzbauten_import/build.gradle).
- [DM01-SO: leerer Downloadbestand und onlyIf-Steuerung](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/0_dm01_so/build.gradle).
- [DM01-CH: veröffentlichte Regionen des Vorgängers](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/1_dm01_ch/build.gradle).

Ein mechanisches Entfernen von `doLast` und Kopieren seines Inhalts in beliebige Taskkonfiguration wäre fachlich falsch. Die Runtime braucht typisierte Tasks und gewöhnliche Actions. Eine zusätzliche allgemeine Gradle-Aktionsliste wird dadurch nicht erforderlich.

## 12. Konkrete Akzeptanzpunkte für den Coding-Agent

1. SQL- und ZIP-Grundrezepte laufen als echte Groovy-Scripts ohne `def`, Kontextparameter oder Import der mitgelieferten Tasktypen.
2. Die Zuweisungsschreibweise ist verbindlich; unbekannte Felder erzeugen keine globalen Scriptproperties. Konfigurationsmethoden ohne `=` werden nicht still zusätzlich unterstützt.
3. Ein einzelner `dependsOn`-Name und eine Liste haben dieselbe ausdrücklich normalisierte Bedeutung. Vorwärtsreferenzen und fehlende Vorgänger liefern klare Diagnosen.
4. Alle Parameterdeklarationstypen, Vorgaben, fehlenden Werte und Konfliktfälle entsprechen RUN-013 und Kapitel 06. Ein unbekannter öffentlicher Name eröffnet keinen rohen Nebenkanal.
5. Pflichtparameter werden vor einem Run jobweit geprüft; `check`, `plan` und Jobhilfe führen keine Konfigurationsclosures aus und behandeln fehlende Werte wie beschrieben.
6. Vorgängerwerte werden erst beim Nachfolgerstart gelesen. Ein fehlendes `dependsOn` wird nicht durch einen impliziten Start oder eine nachträgliche Kante repariert.
7. ResultViews bieten nur beschriebene lesende Ausgänge; falsche Feldnamen, unzulässige Zuweisungen und null entgegen einer Feldzusage haben gezielte Diagnosen.
8. `resultOptional` bewahrt die vorhandene Skip-/Fehlersemantik. Ein modebedingt nullierbares Extractfeld macht nicht das gesamte Ergebnis optional leer.
9. Frische Builder, Closurekopien und aufrufgebundene Scopes verhindern Werteübernahme zwischen Tasks, synchronen Aufrufen und Runs. Experten-TaskRefs lesen weiterhin die tatsächlichen Javawerte.
10. `when`, Action, `try/catch`, `finishJob`, Cancellation und Cleanup behalten ihre in Kapitel 03 festgelegte Semantik.
11. Das separate FileSize-Erweiterungs-JAR besitzt dieselbe `=`-Syntax, Parameterübergabe, Ergebnisoberfläche, Hilfe und Fehlerqualität wie eine mitgelieferte Bibliothek.
12. Alle Mindesttaskverträge werden mit echten Dateien beziehungsweise isoliertem PostgreSQL geprüft; HTTP-/ZIP-Limits gelten auch ohne ausdrücklich gesetzte Werte.
13. Ein Job-Fat-JAR verwendet dieselbe DSL und Parametervorprüfung; der Paketbuild führt niemals den Scriptbody aus.
14. Kein MVP-Test benötigt eine Publisherimplementierung. Die klar markierten Publisher-Zielbeispiele und die aus ihm abgeleiteten allgemeinen Runtimeverträge bleiben erhalten.

## 13. Sprachgrundlagen und Grenzen der Aussage

Die Wahl der konkreten Anwenderoberfläche ist eine GRETL-Entwurfsentscheidung und wurde hier nicht als empirisch getestete Benutzerstudie ausgegeben. Ein späterer kurzer Test mit wirklichen Fachanwendern sollte prüfen, ob sie Parameter, Pfade, Vorgänger und typische Fehler anhand der Vorlagen selbständig ändern können.

Technische Sprachgrundlagen sind die offizielle Groovy-Dokumentation zu [DSLs und Scriptbasisklassen](https://docs.groovy-lang.org/latest/html/documentation/core-domain-specific-languages.html), [Closures und Delegationsstrategien](https://docs.groovy-lang.org/latest/html/documentation/core-closures.html) sowie [Properties und Objektorientierung](https://docs.groovy-lang.org/latest/html/documentation/core-object-orientation.html). Sie belegen die verfügbaren Sprachmittel. Die korrekte Bindung der hier spezifizierten GRETL-Oberfläche muss mit den tatsächlich gelieferten Groovy-Versionen und Beispieljobs getestet werden.
