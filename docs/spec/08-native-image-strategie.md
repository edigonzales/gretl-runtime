# 08 — Optionale Job-Paketierung und Native Image

**Spezifikation 4.1; Stand: 18. Oktober 2026.** Native Image bleibt eine spätere Option. Dieses Kapitel ersetzt die frühere Festlegung auf separate Fachworker und einen umfangreichen Native-Nachweisplan. Es wurde kein GRETL-Native-Build ausgeführt. Der genaue Job-JAR-Vertrag steht in [12 — Job-Paketierung](12-job-paketierung.md), die Versionsbaseline in [07](07-plattform-und-versionen.md).

## Ergänzung 4.0: derselbe Diagnosevertrag

Eine später angebotene Job-JAR-/Native-Ausführung verwendet denselben Logging-, Fehler-, Parameter- und Quellenvertrag aus Kapitel 16. Ein schneller `--help` beweist weder dynamische Groovyfähigkeit noch korrekte Bibliothekslogs. Ein optionaler Exporter muss im kurzlebigen Programm begrenzt geflusht werden; er begründet keine neue verpflichtende Workerplattform.

In dieser Revision werden weder ein Native Image noch der endgültige Job-Packager gebaut. Die kompilierbare API-Referenz dient der SPI-Konsistenz, nicht als Native-Machbarkeitsbeweis. Java-API- und ServiceLoader-Prüfungen sind in Kapitel 17 ausdrücklich getrennt von den noch offenen Produktgates.

## 1. Entscheidung und Reihenfolge

Der Standardweg bleibt: Groovy-Datei bearbeiten und mit der JVM-Runtime starten. Der Jobautor braucht keinen eigenen Build. Die interne Übersetzung beim Laden erledigt der Runner. Groovy unterstützt getrennte Schritte für das Einlesen eines Scripts und dessen Ausführung sowie die Übergabe aktueller Werte über ein Binding. [N1]

Bei Bedarf wird ein bestimmter Job als ausführbares, versioniertes Paket ausgeliefert. Die strategische Reihenfolge ist:

| Stufe | Zweck | Einordnung |
|---|---|---|
| Groovy-Datei auf der JVM | Jobs direkt entwickeln und ändern | Standardweg des MVP |
| Vorkompilierter Job im ausführbaren Fat-JAR | Job, Runtime und bekannte Bibliotheken gemeinsam ausliefern | Optionale Paketierungsfunktion; eine normative erste JAR-Variante |
| Native Image dieses festen Jobs | Ausführung eines geprüften Jobpakets ohne externe JVM | Späterer optionaler Buildweg |

Ein nativer allgemeiner Runner für beliebige nachträglich geschriebene Groovy-Jobs wird nicht vorausgesetzt. Ein kleiner nativer CLI-Launcher bleibt eine davon unabhängige Idee. Keine der beiden Optionen verlangt einen Workerprozess für jede Custom-Task.

## 2. Was ein schnelles CLI leisten kann

Drei Zeiten müssen unterschieden werden: die Antwort auf allgemeine Hilfe beziehungsweise `--version`, die Zeit bis zur ersten Task und die Gesamtdauer des Jobs. Jobbezogene Hilfe lädt für ihre Parameterbeschreibungen die Definition und wird gesondert vom statischen Hilfepfad gemessen. Eine schnellere Versionsanzeige ist angenehm; sie beweist keine Beschleunigung produktiver ETL-Arbeit.

Zunächst wird die JVM-Anwendung so aufgebaut, dass elementare Argumentauswertung und Hilfe weder Groovy-Jobs auswerten noch Fachbibliotheken initialisieren. Sie benötigen keine GeoTools-Factory-Suche, INTERLIS-Modellzugriffe oder Datenbankverbindungen. Bibliotheken werden erst initialisiert, wenn der ausgewählte Ablauf sie braucht. Diese Trennung ist auch für nachvollziehbare Startfehler nützlich.

Ein kleiner nativer Launcher könnte einfache Befehle direkt beantworten und für `run` den JVM-Runner starten. Beliebige Groovy-Jobs und zusätzliche Custom-Task-JARs bleiben dadurch möglich. JVM-Start, Laden von Groovy und gegebenenfalls Skriptübersetzung fallen anschliessend weiterhin an. Die native Hülle lohnt sich deshalb erst, wenn ihr konkreter Start- oder Auslieferungsvorteil den zusätzlichen Bestandteil rechtfertigt.

Eine bereits laufende JVM könnte Startarbeit über mehrere Aufrufe verteilen. Das wäre eine separate Daemon-Funktion mit Lebensdauer-, Zustands- und Betriebsfragen. Im MVP genügt ein normaler JVM-Prozess pro Joblauf. Experimentelle Mechanismen für nachgeladenen Code oder eine eingebettete zweite VM werden hier nicht weiter untersucht.

## 3. Erste Paketierungsvariante: ein vorkompiliertes Job-Fat-JAR

Die optionale Paketierung soll so verwendbar sein:

```sh
gretl package job-package.json --output import-adressen.jar
java -jar import-adressen.jar --param gemeinde=2601 --param stichtag=2026-10-17
```

Der Aufrufer benötigt eine passende Java-25-Laufzeit. GRETL, Groovy und die deklarierten Taskbibliotheken werden mit dem Paket ausgeliefert. Eine JVM ist nicht automatisch Teil eines Fat-JAR. Ein zusätzliches Runtime-Paket wäre eine getrennte Auslieferungsoption.

### 3.1 Was beim Paketieren geschieht

Der Paketierungsbuild übersetzt die deklarierte Groovy-Jobdatei und ihre Hilfsklassen mit derselben Compilerkonfiguration wie der direkte Runner. Er enthält die erzeugte Script-Klasse und die benötigten Closureklassen, den allgemeinen Job-JAR-Einstiegspunkt, Runtime, Taskbibliotheken und deklarierte Ressourcen. Das Bytecodeziel ist Java 25. Ein manuell erstelltes `main` oder ein separates Maven-Projekt pro Job wird nicht verlangt.

Der Einstiegspunkt erzeugt beim Start eine frische Script-Instanz mit aktuellem Kontext und aktuellen Parametern. Erst dann wird die Jobdefinition ausgewertet. Der allgemeine Launcher liest den vom Build ermittelten `entryClass`-Eintrag in den Paketmetadaten; die Jobklasse wird nicht erraten. Ein direkt auf eine bestimmte Jobklasse verweisender generierter Einstieg kann später den Native-Build vereinfachen, ist aber keine zusätzliche JAR-Variante.

Der Paketierungsbuild DARF den Job nicht ausführen, um Tasks, benötigte Dateien oder Datenbankabhängigkeiten zu entdecken. Er ruft weder die Jobdefinition noch Task-Closures für einen Probelauf auf. Benötigte Bibliotheken und Ressourcen stehen ausdrücklich in der Paketbeschreibung. Compilererweiterungen sind vertrauenswürdige Buildbestandteile; die Paketierung ist keine Sandbox für fremden Buildcode.

### 3.2 Was weiterhin zur Laufzeit geschieht

Vorkompilierung fixiert den Code und spart dessen Übersetzung beim Start. Sie friert keine Werte ein. Gemeinde, Stichtag, Secrets und Eingabedateien stammen weiterhin vom Aufruf beziehungsweise seiner Umgebung. Ein Ausdruck innerhalb einer Task wird weiterhin zum definierten Taskzeitpunkt ausgewertet. SQL-Abfragen und aus Taskresultaten berechnete Variablen bleiben Laufzeitvorgänge.

Vorkompilierung erfordert auch kein allgemeines `@CompileStatic`. Vorab erzeugter Groovy-Bytecode kann dynamische Methodenaufrufe enthalten. Die Job-DSL und ihr Verhalten müssen für freien Scriptstart und Job-JAR übereinstimmen. [N1]

Der Build enthält keine aufgelösten Secrets oder produktiven Datenbankinhalte. Derselbe Job kann mit unterschiedlichen Parametern gestartet werden. Wenn sich sein Code, eine eingeschlossene Ressource oder eine Bibliotheksversion ändert, wird ein neues Paket erstellt.

### 3.3 Skripttext im Fat-JAR als Alternative

Technisch könnte das JAR stattdessen die Groovy-Quelldatei als Ressource enthalten und sie bei jedem Start übersetzen. Das wäre eine einfache Auslieferungsform, würde aber den Groovy-Kompilierschritt beim Start beibehalten.

**Die erste implementierte Job-JAR-Variante dieser Spezifikation verwendet die vorkompilierte Script-Klasse.** Ein zweiter ausführbarer JAR-Modus mit Skriptübersetzung wird im MVP nicht parallel angeboten. Quellen dürfen zu Diagnosezwecken enthalten sein; sie werden beim JAR-Start nicht als ausführbarer Fallback neu kompiliert. Der allgemeine `gretl run`-Modus bleibt unabhängig davon ein normaler Scriptmodus.

### 3.4 Ressourcen und Service-Provider

SQL-Dateien, Vorlagen und mitgelieferte Modelle benötigen einen definierten Zugriff. Ein JAR-interner Pfad ist keine gewöhnliche Datei. Benötigt eine Bibliothek `File` oder `Path`, muss der Paketvertrag die Ressource in ein Arbeitsverzeichnis materialisieren können. Ausgaben liegen ausserhalb des JAR; externe relative Pfade werden ausdrücklich gegen das festgehaltene Start-CWD aufgelöst, Paketressourcen gegen den materialisierten Jobroot.

Bei der Paketierung müssen Servicebeschreibungen, Ressourcen und Provider erhalten bleiben. GeoTools verwendet Factory- und Classloader-Mechanismen; ein erfolgreicher leerer JAR-Start beweist deren Funktion nicht. [N4] Die Regeln für Zusammenführung und Prüfung gehören in Kapitel 12. Dieses Kapitel definiert keinen konkurrierenden JAR-Loader.

## 4. Optional: ein ganzer fester Job als Native Image

Für einen bestimmten Job kennt der Build dessen Code und Bibliotheken. Das entspricht dem regulären Native-Image-Modell: analysiert und übersetzt wird die bekannte Codewelt. Neue Klassen können einem bestehenden Binary nicht einfach wie einer JVM nachgeliefert werden. Dynamische Zugriffe und Ressourcen können zusätzliche Reachability-Metadaten benötigen. [N2]

Der spätere Native-Build verwendet die vorkompilierte Jobklasse und die aufgelösten Abhängigkeiten des Jobpakets. Er verwendet den Einstieg über diese Klasse. Ein Einstiegspunkt, der zuerst unbekannten Groovy-Quelltext zur Laufzeit kompiliert, würde wieder ein anderes Problem eröffnen.

| Zur Buildzeit fest | Weiterhin zur Laufzeit variabel |
|---|---|
| Jobcode und Hilfsklassen | Parameter, Secrets, Stichtage und Gemeinden |
| Custom-Task-Implementierungen und Bibliotheken | Dateien, SQL-Ergebnisse und Taskresultate |
| Benötigte Treiber, Formate und Provider | Auswahl und Kombination der eingebauten Fähigkeiten |

Geänderter Jobcode oder ein zusätzliches Task-JAR erfordern ein neues Binary. Neue Eingabedaten tun dies normalerweise nicht. INTERLIS-Modelle sind zunächst Daten für die Engine. Ob ein konkreter Pfad zusätzliche JVM-Klassen generiert oder neue Implementierungen lädt, muss am ausgewählten Job geprüft werden.

Groovy 6 dokumentiert inkubierende Unterstützung dynamischer Aufrufe in vorab kompiliertem Code in Native Images und verlangt dafür GraalVM für JDK 25.0.4 oder neuer. Das begründet einen späteren Versuch auf einem qualifizierten GraalVM-Stand der JDK-25-Linie. Es garantiert weder beliebige spätere Groovy-Skripte noch die Native-Kompatibilität sämtlicher GeoTools-, INTERLIS- oder Custom-Task-Pfade. [N3]

Geprüft werden am konkreten Paket die benötigten Service-Provider, Reflection, Ressourcen, JDBC-Treiber, nativen Zusatzbibliotheken und die Laufzeitinitialisierung von Engines. Buildzeitinitialisierung darf keine Secrets, aktuellen Jobparameter oder offenen Datenbankverbindungen im Image festhalten. Datei- und Threadzustand in statischen Initialisierungen kann zwischen Build und Ausführung problematisch sein. [N2]

Das Binary gilt für die geprüfte Betriebssystem- und Architekturkombination. Startet eine enthaltene Task weiterhin ein externes JVM-Programm, bleibt dieses eine dokumentierte Voraussetzung; der Gesamtjob wäre damit nicht vollständig JVM-frei.

## 5. Konsequenzen für die Runtime

Fachliche Implementierungen werden von CLI, Groovy-Laden und Scheduler getrennt. Custom-Tasks erhalten ihre Eingaben und ihren Kontext und liefern Ergebnisse oder Fehler. Publisher und SqlExecutor stehen dabei auf derselben Ebene. Die Business-Logik einer Task erzeugt keinen zusätzlichen Native- oder Runtime-Vertrag.

Die Referenzimplementierung führt Tasks sequenziell in derselben JVM aus. Eine belegte Bibliotheksunverträglichkeit kann einen taskinternen Prozessadapter begründen. Native Image allein ist kein Anlass, alle Fachlogik hinter ein Serialisierungsprotokoll zu verschieben.

Die Runtime benötigt lediglich zwei Arten, eine Script-Instanz bereitzustellen: aus einer Quelldatei oder aus einer bereits kompilierten Jobklasse. Der anschliessende Ablauf bleibt gemeinsam: frischer Kontext, Parameter, Jobdefinition, gewählte Tasks, Ergebnisse und Fehlerbehandlung. Dieser begrenzte Architekturpunkt unterstützt die Paketierung unmittelbar und hält Native Image offen.

## 6. Drei kleine Prüfungen und spätere Versuche

1. **Startzeiten erfassen:** denselben kurzen Job direkt und aus seinem JAR ausführen; Hilfe, erste Task und Gesamtdauer getrennt messen. Frische Prozesse und dieselbe Umgebung verwenden. Einen nativen Launcher erst bei relevantem verbleibendem Startaufwand vergleichen.
2. **Job-JAR prüfen:** ein kleiner Job mit eigener Custom-Task, zwei Parametersätzen und einer SQL-Ressource. Aus einem anderen Verzeichnis ohne GRETL-Installation starten und Ergebnisse mit dem Scriptmodus vergleichen. Bei ausgelieferter Paketierungsfunktion ist diese funktionale Prüfung erforderlich; sie ist keine Native-Forschung.
3. **Einen festen Job nativ versuchen:** zuerst einen kleinen vorkompilierten Job, anschliessend genau einen benötigten INTERLIS- oder GeoTools-Pfad. Startzeit, Gesamtdauer, Speicherbedarf und fehlende Fähigkeiten dokumentieren. Hoher Anpassungsaufwand kann die Entscheidung begründen, dieses Paket weiter auf der JVM auszuführen.

Ein Native-Erfolg qualifiziert das geprüfte Jobpaket. Er ist keine Zusage für alle späteren Kombinationen von GRETL-Tasks. Ein fehlender oder erfolgloser Native-Versuch blockiert die Freigabe des JVM-Runners und der Job-JAR-Paketierung nicht.

## Quellen

- **[N1]** [Apache Groovy: Integrating Groovy in a Java application](https://docs.groovy-lang.org/latest/html/documentation/guide-integrating.html), insbesondere `GroovyShell`, `Binding`, eigene Script-Basisklassen und `GroovyClassLoader`.
- **[N2]** [GraalVM: Native Image Compatibility Guide](https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/), insbesondere bekannte Codewelt, Metadaten und Klasseninitialisierung.
- **[N3]** [Apache Groovy: Groovy 6.0 Release Notes](https://groovy-lang.org/releasenotes/groovy-6.0.html), Abschnitt zur inkubierenden Native-Unterstützung; bereits in der Versionsanalyse geprüft.
- **[N4]** [GeoTools: FactoryRegistry](https://docs.geotools.org/stable/javadocs/org/geotools/util/factory/FactoryRegistry.html), Factory-Suche und Classloader-Verhalten.

[N1]: https://docs.groovy-lang.org/latest/html/documentation/guide-integrating.html
[N2]: https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/
[N3]: https://groovy-lang.org/releasenotes/groovy-6.0.html
[N4]: https://docs.geotools.org/stable/javadocs/org/geotools/util/factory/FactoryRegistry.html
