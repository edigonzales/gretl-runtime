# 12 — Optionale ausführbare Job-JARs

**Version:** 4.1, 18. Oktober 2026.  
**Normativ für:** den optionalen Paketierungsmeilenstein. Die Paketfunktion ist ein eigener Liefermeilenstein; ihre Einzelanforderungen müssen nicht bereits in der ersten Runtime-Iteration umgesetzt werden. Sie verwendet die allgemeinen Verträge aus [03](03-runtime-vertraege.md), [04](04-job-dsl-und-custom-tasks.md) und [06](06-betrieb-und-auslieferung.md). Die Plattformfestlegung in [07](07-plattform-und-versionen.md) bleibt massgebend; die weitere Native-Option steht in [08](08-native-image-strategie.md).  
**Status:** Spezifikation, keine implementierte Funktion. GRETL-Artefaktkoordinaten in Beispielen sind geplante Namen, keine Aussage über heute veröffentlichte Software.

## Ergänzung 4.0 — Gemeinsame API und Beobachtbarkeit

Der Job-Packager benutzt dieselbe öffentliche API aus Kapitel 14 und Compiler-/DSL-Regeln aus Kapitel 15. Eine bisher veranschaulichende `gretllight`-Packageangabe ist durch `ch.so.agi.gretl.runtime` ersetzt. Kein fachlicher Task wird allein für Paketierung zwingend mitgeliefert.

Das tatsächliche End-JAR enthält genau einen Loggingprovider und die qualifizierten einseitigen Brücken. Maven-Shade-Merges müssen Serviceprovider und Groovy-Extension-Metadaten erhalten; sie dürfen nicht versehentlich mehrere SLF4J-Provider zusammenführen. Fehler-/Quellenzeilen und deklarierte Provenienz bleiben erhalten. Das gemeinsame Logschema und die Runtimekonfiguration aus Kapitel 16 gelten auch im JAR. Ein Test muss das fertige Paket aus einem fremden CWD starten und einen echten Fehler mit Cause auslösen, nicht nur dessen `--help` lesen.

Der Paketbuild erzeugt Code und Metadaten, führt jedoch keinen Job aus und erzeugt keine fiktive Run-/Task-Telemetrie für spätere Tagesdaten. Secrets und konkrete Laufzeitparameter werden nicht mitkompiliert. OTel-Exporter sind ein optionaler expliziter Paketbestand, nicht automatisch im MVP vorhanden.

## 1. Empfehlung

### PKG-001 — Optionales Jobartefakt, gemeinsamer Ausführungskern

Ein GRETL-Job soll zwei gleichwertige Ausführungswege haben: direkt als Groovy-Script mit der installierten Runtime und optional als ausführbares Job-JAR. Das JAR enthält einen bestimmten Stand des Jobcodes, der Runtime, der benötigten Custom-Tasks, ihrer Java-Abhängigkeiten sowie ausgewählter statischer Jobressourcen. Parameter, Secrets, Eingangsdaten und Ziele externer Systeme bleiben Laufzeitwerte.

Für die erste Paketfunktion empfehle ich ein **normales ausführbares, mit Maven Shade zusammengesetztes JAR und automatisch vorab kompilierten Groovy-Jobcode**. Das optionale Packen ist der ohnehin gewünschte Buildschritt für ein Auslieferungsartefakt. Der alltägliche Befehl `gretl run job.groovy` bleibt ohne separat auszuführende Kompilation erhalten. Die Entscheidung vermeidet zwei zunächst gleichwertige Job-JAR-Formate und kann bereits einen Teil der Startzeit sparen.

Groovy unterstützt die Kompilation von Scripts in JVM-Bytecode sowie eine eigene Scriptbasisklasse. Die daraus erzeugte Scriptklasse kann weiterhin Laufzeitwerte verwenden; Bytecode-Kompilation bindet keine späteren Jobparameter. [Groovy: groovyc](https://groovy-lang.org/groovyc.html)

Die Paketfunktion ist ein **kleines Auslieferungswerkzeug ausserhalb des Ausführungskerns**. Die erste Umsetzung darf aus einer getesteten Maven-Vorlage plus einem kleinen GRETL-Packager bestehen. Weder ein eigener Dependencyresolver noch ein Pluginportal, ein allgemeines Buildmodell oder ein neu entwickelter Nested-JAR-Classloader sind dafür erforderlich. Der generische `gretl package`-Befehl ruft dieses Werkzeug auf; der Runner muss Maven deshalb nicht beim normalen Jobstart laden.

Für die Implementierungsreihenfolge empfehle ich zuerst eine versionierte Maven-Paketvorlage und die kleine Java-Bibliothek für Deskriptorprüfung, Groovy-Kompilation und Metadaten. Der generische CLI-Befehl wird anschliessend ein dünner Aufruf dieses separaten Authoring-Moduls. Ein eigenes Maven-Plugin ist sinnvoll, falls es die gemeinsame Integration tatsächlich verkleinert; es ist keine zusätzliche Voraussetzung des ersten Packager-Meilensteins. Die normale Task-SPI und der Scheduler erhalten dadurch keine Maven-Abhängigkeit. Die Packagervorlage und ihre verwendeten Maven-Plugin-Versionen sind als Teil des GRETL-Releases festgelegt.

Für den Paketbuild gilt ebenfalls BLD-001 bis BLD-008 aus [07, Abschnitt 6.4](07-plattform-und-versionen.md#64-verbindliche-leitlinie-konventioneller-maven-produktbuild). Shade-Ausführungen bleiben auf Paketierungsartefakte begrenzt; API-, Runner- und Taskmodule sind reguläre Bibliotheks-JARs. Die unten erforderlichen Merges und Kollisionsprüfungen werden nicht als Vereinfachung weggelassen. Ihre wiederverwendbare Logik gehört in kleine testbare Werkzeuge statt lange POM-Scripts oder einen vorsorglich selbst entwickelten Maven-Lifecycle. Diese Präzisierung zieht Lieferstufe C nicht in M0 vor.

## 2. Benutzeroberfläche und Artefaktgrenze

### PKG-010 — CLI und Java-Laufzeit

Die folgende Oberfläche ist als Zielbild geeignet:

```sh
# Üblicher Entwicklungsmodus: kein zusätzlicher Buildbefehl.
gretl run jobs/datenpruefung/job.groovy \
  --target pruefen \
  --config /etc/gretl/runtime.json \
  --param eingabe=/data/eingang.xtf

# Bewusst gewählte Auslieferung eines bestimmten Jobstands.
gretl package jobs/datenpruefung/job-package.json \
  --output dist/datenpruefung.jar

# Betrieb des fertigen Jobs: keine GRETL-Installation, kein Buildwerkzeug.
java -jar dist/datenpruefung.jar \
  --target pruefen \
  --config /etc/gretl/runtime.json \
  --param eingabe=/data/eingang.xtf

# JVM-Optionen gehören vor -jar; Joboptionen dahinter.
java -Xmx2g -jar dist/datenpruefung.jar \
  --config /etc/gretl/runtime.json \
  --param eingabe=/data/eingang.xtf

# Derselbe Planmodus wie bei der normalen Runtime.
java -jar dist/datenpruefung.jar --plan \
  --target pruefen \
  --params-file /etc/gretl/datenpruefung-params.json
```

Im JAR ist genau ein Job festgelegt. Beim Start ist deshalb kein weiterer Jobpfad nötig. `--version` wird aus statischen Paketmetadaten beantwortet, bevor der Job instanziiert oder ein Provider initialisiert wird. `--help` ist dagegen **jobbezogene Hilfe** wie `gretl run job.groovy --help`: Die kompilierte Definition wird geladen, um ihre Parameterbeschreibungen und Ziele anzuzeigen. Taskkonfigurationen, Handler und Cleanup laufen dabei nicht; Pflichtwerte, Secrets und Fachzugänge werden nicht benötigt. Vertrauenswürdiger Script-Toplevelcode wird wie im normalen Definitionspfad ausgeführt. Diese Hilfe verspricht deshalb nicht den noch kürzeren Startpfad der allgemeinen Runtimehilfe. Die Laufzeitoptionen, ihre Prioritäten, Fehlermeldungen und Exitcodes stimmen mit dem normalen Runner überein. Es gibt keine Publisher-spezifische Paket- oder Startoberfläche.

`--plan` führt denselben Registrierungsweg wie der normale Planbefehl aus. Er führt keine Taskkonfiguration oder Taskaktion aus. Beliebiger Script-Toplevelcode kann dabei trotzdem laufen; die allgemeine Einschränkung aus Kapitel 04 bleibt bestehen. Ein Job-JAR nimmt im MVP keinen ersetzenden Scriptpfad und keine zusätzlichen Classpath-/Plugin-JARs entgegen. Änderungen am enthaltenen Code oder Taskbestand verlangen ein neues Paket.

**Ein Fat-JAR enthält keine Java-Laufzeit.** Die Zielmaschine braucht die qualifizierte Java-Version. Ein Container oder ein späteres Paket mit gebündelter Java-Laufzeit ist eine andere Auslieferungsform. Ein JAR ist grundsätzlich JVM-portabel; mitgelieferte JNI-Bibliotheken, externe Programme oder bestimmte Treiber können den tatsächlich unterstützten Plattformumfang einschränken.

Ein `java -jar`-fähiges Archiv startet über den Eintrag `Main-Class` im JAR-Manifest. Das GRETL-Job-JAR verwendet den normalen Classpath, keinen neuen JPMS-Modulgraphen. Mehrversions-JARs haben eigene Regeln für `META-INF/versions` und den Manifest-Eintrag `Multi-Release`; diese dürfen beim Zusammenführen nicht verloren gehen. [Java 25: JAR File Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html)

## 3. Schlanker Paketdeskriptor

### PKG-020 — Explizite Quellen, Ressourcen und Programmabhängigkeiten

Ein zu implementierender Beispielvertrag:

```json
{
  "schemaVersion": 1,
  "name": "datenpruefung",
  "version": "2026.10.17",
  "runtimeVersion": "0.1.0",
  "entryScript": "job.groovy",
  "sources": ["helpers"],
  "resources": ["sql", "models", "templates", "README.md"],
  "dependencies": [
    "ch.so.agi:gretl-tasks-sql:0.1.0",
    "ch.so.agi:gretl-tasks-interlis:0.1.0",
    "ch.so.agi:my-custom-tasks:1.0.0"
  ]
}
```

Die Koordinaten sind illustrative Vorschläge. Alle Dateipfade beziehen sich auf das Verzeichnis des Deskriptors. `entryScript` ist verpflichtend und liegt im ersten Paketvertrag unmittelbar in diesem Verzeichnis, also beispielsweise `job.groovy`, nicht `jobs/job.groovy`. Damit stimmt der Jobroot des Scriptmodus mit der Basis der Paketressourcen überein. Quellen und Ressourcen dürfen keine absoluten Pfade, `..`-Ausbrüche oder Symlinks enthalten; sie bleiben unter dieser gemeinsamen Wurzel. Die Listen `sources`, `resources` und `dependencies` dürfen leer sein. Explizite Dateien und Verzeichnisse genügen im MVP; eine neue Include-/Exclude-Sprache ist unnötig. Zusätzliche Sources sind im ersten Profil Groovy-Quelldateien mit zum gemeinsamen Jobroot passender Packagestruktur. Java-Helfer werden als regulär gebaute Bibliothek angegeben. Die direkte Runtime kann solche Groovy-Helfer ebenfalls unter dem Jobroot finden und automatisch kompilieren; dieses Verhalten wird mit einem importierten Helfer in beiden Modi geprüft. Fehlende Einträge und unbekannte Deskriptorfelder sind Fehler.

Der Packager nimmt **keine Laufzeitkonfiguration und keine Parameterwerte** entgegen. Jobdefaultwerte stehen im normalen Jobvertrag, nicht in einer zweiten Paketsprache. Secrets gehören weder in diesen Deskriptor noch in das JAR. Da vertrauenswürdiger Quellcode natürlich trotzdem Literale enthalten kann, darf das Werkzeug keine automatische Garantie behaupten, sämtliche denkbaren Secrets erkannt zu haben.

Ein Dependency-BOM der gewählten GRETL-Version legt die zusammen qualifizierten Versionen fest. Zusätzliche Custom-Task-Abhängigkeiten sind ausdrücklich angegeben. Der Build löst den vollständigen Bestand auf und dokumentiert tatsächliche Artefakte, Versionen und Hashes. Eine importierte Groovy-Klasse ist kein verlässlicher Algorithmus zur Ermittlung aller benötigten Bibliotheken: Serviceprovider, Reflexion und taskinterne Zweige benötigen oft Klassen, die nicht als sichtbarer Import im Job stehen.

Versionsbereiche, `LATEST`, `RELEASE` und veränderliche SNAPSHOT-Stände sind für ein qualifiziertes Jobartefakt ausgeschlossen. Die aufgelöste transitive Auswahl wird mit dem Paketinventar gespeichert; dieses Inventar ist kein Laufzeitresolver. Der Build meldet Konflikte mit dem freigegebenen GRETL-Bestand und erzwingt nicht still eine zufällig zuerst gefundene Version. Das fertige Einzel-JAR enthält alle für den zugesagten Job benötigten Programm-JAR-Inhalte. Eine installierte Taskbibliothek auf dem Zielrechner ist keine verdeckte Voraussetzung.

Das MVP muss nicht Maven-Repositories in jedem Jobdeskriptor konfigurierbar machen. Ein festgelegtes Buildsetup beziehungsweise Maven-Settings der Organisation reichen. Diese Konfiguration ist ein Buildbelang. Im Betrieb löst das Job-JAR keine weiteren Programmabhängigkeiten auf.

## 4. Paketzeit und Laufzeit

### PKG-030 — Keine Jobausführung beim Packen

| Vorgang | Beim Packen | Beim Start des Job-JARs |
|---|---|---|
| Runtime und Custom-Task-JARs auswählen | Ja; exakt festlegen | Bereits enthalten |
| Programmabhängigkeiten herunterladen | Falls nötig im Build | Nein |
| Groovy-Script und deklarierte Helfer kompilieren | Ja | Keine erneute Kompilation nötig |
| Job-Script instanziieren und `run()` aufrufen | Nein | Ja |
| DAG und Parameterdeklarationen registrieren | Nein | Ja, mit normaler Runtime-Semantik; auch für jobbezogene Hilfe |
| Ausführungsziele wählen | Keine fachliche Auswahl | Defaults beziehungsweise `--target` |
| Konkrete Parameterwerte auflösen | Nein | Bei Ausführung ja; bei jobbezogener Hilfe nein |
| Secrets lesen | Nein | Erst beim benötigten Zugriff |
| Taskkonfiguration mit Laufzeitwerten ausführen | Nein | Beim normalen Taskstart |
| SQL ausführen, HTTP abrufen, INTERLIS exportieren | Nein | Durch ausgewählte Tasks |
| Deklarierte SQL-/ILI-Dateien einpacken | Ja | Bei Bedarf als Ressourcen verwenden |
| Fachmodelle von einem Modellrepository beziehen | Keine implizite Aktion | Falls der Task dies ausdrücklich vorsieht |

Der Build darf **nicht zuerst `check` oder `plan` auf den Job anwenden**, wenn diese Befehle zur Registrierung den Script-Toplevelcode ausführen. Er darf den Compiler benutzen, den Deskriptor prüfen und JAR-Inhalte analysieren. Er darf nicht `GroovyShell.evaluate`, `Script.run`, Custom-Task-Konstruktoren mit Seiteneffekten oder fachliche Requests aufrufen, um den Inhalt des Pakets zu erraten.

Groovy-AST-Transformationen und andere Compilererweiterungen sind selbst ausführbarer Buildcode. Die Zusage lautet deshalb präzise: *Der Packager führt den Job nicht aus und benötigt keine produktiven Fachzugänge.* Sie ist keine Sicherheitsbehauptung, beliebige fremde Compilererweiterungen könnten keine Seiteneffekte haben. Nur der freigegebene Compilerbestand und freigegebene Erweiterungen werden verwendet. Grape beziehungsweise `@Grab` ist für gepackte Jobs nicht Teil des Vertrags; Abhängigkeiten kommen aus dem expliziten Buildbestand.

Wichtig für das Laufzeitmodell: Der kompilierte Scriptbody registriert den DAG **erst beim Jobstart**. Die Closure eines Tasks löst ihre Werte weiterhin erst bei dessen Ausführung auf. Auch eine zur Laufzeit erzeugte Liste von Arbeitselementen bleibt möglich. Das Job-JAR friert Code und Bibliotheken ein, nicht den konkreten Tageslauf.

## 5. Kompilations- und Startmechanik

### PKG-040 — Vorkompilierter dynamischer Groovy-Code

Die Paketkompilation benutzt dieselbe Groovy-Version, dieselbe Scriptbasisklasse, dieselben Standardimporte und dieselben Compilation Customizers wie der Scriptmodus. Sie erzeugt alle Klassen des Jobs, einschliesslich Closureklassen und deklarierter Groovy-Helfer. Das ist keine Umstellung auf `@CompileStatic` und verlangt keinen statisch berechneten DAG.

Ein kleiner allgemeiner Einstiegspunkt liest die eingebetteten Paketmetadaten, verarbeitet die gemeinsamen Optionen und erzeugt die kompilierte Scriptklasse mit einem frischen Laufzeitkontext. Anschliessend ruft er dieselbe Registrierungs- und Ausführungslogik auf wie der Scriptmodus. Das Groovy-Laufzeitsystem bleibt enthalten: Vorkompilierter dynamischer Groovy-Code ist keine Java-Anwendung ohne Groovy-Abhängigkeit.

### PKG-041 — Interne Paketmetadaten

Als interne Metadaten genügen zunächst:

```json
{
  "schemaVersion": 1,
  "name": "datenpruefung",
  "version": "2026.10.17",
  "runtimeVersion": "0.1.0",
  "javaFeatureVersion": 25,
  "entryClass": "job",
  "resourceRoot": "META-INF/gretl/job-resources/",
  "dependencyInventory": "META-INF/gretl/dependencies.json"
}
```

`entryClass` wird vom Build ermittelt; der Packager verlässt sich nicht auf eine vom Benutzer geratene Klasse. Ein vorgeschlagener Ablageort ist `META-INF/gretl/job.json`. Das JAR-Manifest verweist mit `Main-Class` auf den allgemeinen GRETL-Einstiegspunkt. Ursprüngliche Quellpfade und Zeilennummern sollen in Fehlermeldungen erhalten bleiben. Die Quelltexte können zusätzlich als Diagnosematerial enthalten sein; sie werden dadurch nicht automatisch zur Laufzeit neu geladen.

Zur Einordnung: Ein noch einfacheres erstes Experiment könnte den Scripttext nur als Ressource einpacken und beim Start mit dem normalen Loader kompilieren. Das beweist die Verteilbarkeit, verbessert aber diesen Teil der Startzeit nicht. Als verbindlichen ersten Produktvertrag empfehle ich das vorkompilierte Job-JAR; falls das Experiment aus Aufwandgründen vorgeschaltet wird, muss es als Zwischenstand benannt werden.

## 6. SQL, INTERLIS-Modelle und andere Ressourcen

### PKG-050 — Jobressourcen sind vom externen Arbeitsbestand getrennt

Eine Ressource im JAR ist nicht automatisch eine normale Datei. Zahlreiche bestehende Tasks oder aufgerufene Fachbibliotheken erwarten `File` beziehungsweise `Path`. Deshalb erhält jeder tatsächliche Run vor der ersten Taskarbeit einen privaten Arbeitsbereich, in den der Jobressourcenbestand mit seiner relativen Verzeichnisstruktur materialisiert wird. `--help`, `--version` und `--plan` brauchen dafür keine Runarbeitsbereiche. Die normalen Rezepte verwenden `file(...)` erst in gespeicherten Taskblöcken; Registrierung verlangt keinen materialisierten Jobroot. Direkter Toplevelcode, der selbst Fachdateien öffnet, erhält dadurch keine neue Portabilitäts- oder Nebenwirkungszusage. Das kann selektiv erfolgen, muss aber semantisch gleich bleiben.

Die bestehende Bedeutung `file('sql/update.sql')` relativ zum Jobroot bleibt damit erhalten: Im Scriptmodus ist der Jobroot das Jobverzeichnis; im JAR-Modus der materialisierte Ressourcenbestand. Ein Modellpfad kann so weiterhin auf ein echtes Verzeichnis zeigen. ZIP-Einträge dürfen beim Entpacken nicht aus diesem Zielbereich ausbrechen. Symlinks im zu verpackenden Ressourcenbestand werden im ersten Paketprofil mit einer präzisen Diagnose abgelehnt. Automatisches Einsammeln beliebiger Dateien ausserhalb der deklarierten Wurzeln findet nicht statt.

Es gibt eine bewusste Grenze: Der materialisierte Jobroot ist fachlich ein **Lesebestand**. Temporäre Ergebnisse gehen in den Runworkspace. Dauerhafte Ausgaben gehen an explizit konfigurierte Ausgabeorte. Ein alter Job, der seine eigene SQL-Datei überschreibt oder neben der Scriptdatei dauerhaft neue Eingangsdaten erwartet, ist damit nicht automatisch paketkompatibel.

### PKG-051 — Pfadauflösung in beiden Ausführungsmodi

| Ausdruck beziehungsweise Quelle | Verbindliche Basis |
|---|---|
| `file('sql/update.sql')` | Jobroot; im JAR-Modus materialisierter Ressourcenbestand |
| `parameter('eingabe')` mit deklarierter Art `path` | Ursprüngliches Start-CWD des CLI-Prozesses, falls der Parameter relativ ist |
| `workPath('intermediate.gpkg')` | Arbeitsverzeichnis des aktuell ausführenden Tasks |
| Absoluter Pfad in `ctx.file` oder `ctx.path` | Bleibt absolut; keine vorgespiegelte allgemeine Dateisystemsandbox |
| `--config`, `--params-file`, Packager-`--output` | Start-CWD des jeweiligen Befehls |
| Ein bereits als Pfad definierter Eintrag der Runtimekonfiguration, etwa eine Secretdatei | Verzeichnis der Runtimekonfigurationsdatei gemäss Kapitel 06 |
| Stringwert in `--param` oder in der Parameterdatei | Einmal gemäss gemeinsamer Jobdeklaration konvertiert; nur deklarierte `path`-Werte werden zu Pfaden |

Für externe benutzerrelative Eingaben deklariert der Jobautor einen Parameter vom Typ `path` und verwendet `parameter('eingabe')`. Er benötigt dafür keine verketteten Pfadhelfer. `file('sql/update.sql')` bezeichnet dagegen eine statische Datei des Jobs. Das zufällige CWD eines fachlichen Unterprozesses oder ein späterer Verzeichniswechsel verändert die einmal festgelegte Startbasis nicht. Der fortgeschrittene direkte Kontextzugriff `ctx.path(...)` bleibt für bereits vorliegende Textpfade möglich und folgt derselben Startbasis. `ctx.config(alias)` liefert die allgemeine Taskkonfiguration; nicht jeder darin enthaltene String wird automatisch als Datei interpretiert. Taskeigene Pfadfelder folgen ihrem dokumentierten Vertrag.

Ein Jobparameter vom Typ `path` wird unabhängig von seiner Quelle auf das Start-CWD bezogen, also auch bei einem Wert aus der Parameterdatei oder einem Jobdefault. Diese Regel gilt identisch für Script und JAR. Die Quellen von Pfaden sind in OPS-021 abschliessend unterschieden.

Paketressourcen ersetzen keine Laufzeitkonfiguration. Grosse oder täglich wechselnde XTF-/CSV-Dateien gehören typischerweise nicht in das Job-JAR. Verwendet ein Task eine temporär materialisierte Ressource, muss sie bis zum Ende dieses Joblaufs verfügbar bleiben. Die allgemeine Ressourcenfreigabe räumt diesen Arbeitsbestand danach gemäss dem Runtimevertrag auf.

## 7. Shaded JAR oder Nested JAR?

### PKG-060 — Ein normales Einzel-JAR als erstes Paketprofil

| Kriterium | Shaded JAR | Nested JAR mit Launcher |
|---|---|---|
| Start | Standard-Java-Einstieg und normaler Classpath | Zusätzlicher Loader oder explizites Entpacken nötig |
| Abhängigkeiten | Klassen und Ressourcen zusammengeführt | Original-JARs bleiben als getrennte Archive erhalten |
| Gleichnamige Ressourcen | Gezielte Merge-Regeln erforderlich | Können getrennt vorliegen; korrekte Enumeration ist Loaderaufgabe |
| Bibliotheksidentität und Manifeste | Separates Inventar und kontrollierter Merge nötig | Originalstruktur weitgehend erhalten |
| GEO-/JDBC-Integration | Bekannte Maven-Technik, konkret zu prüfen | Zusätzlich besondere URLs, Classloader und Ressourcenauflösung prüfen |
| Eignung für erstes GRETL-Jobpaket | Empfohlen bei qualifiziertem gemeinsamen Classpath | Rückfalloption bei nachgewiesenem Bedarf |

GeoTools warnt in seiner offiziellen Build-FAQ vor dem simplen Überschreiben gleichnamiger Servicebeschreibungen und zeigt Maven Shade als Lösung. Die aktuelle Vorlage ergänzt auch ImageN-Registries. Das ist direkte Evidenz für einen machbaren normalen Fat-JAR-Pfad, keine Garantie für jede denkbare GRETL-Abhängigkeit. [GeoTools Building FAQ](https://docs.geotools.org/stable/userguide/build/faq.html)

Java lädt JARs innerhalb eines JARs nicht durch einen allgemeinen Standardmechanismus. Spring Boot dokumentiert dafür seinen eigenen Loader und seine eigene Archivstruktur. Ein GRETL-Projekt könnte einen vorhandenen Loader verwenden, ohne deswegen einen Webserver zu benötigen. Für das MVP ist dieser zusätzliche Loader aber erst dann gerechtfertigt, wenn der einfachere Paketpfad konkret scheitert. [Spring Boot: Nested JARs](https://docs.spring.io/spring-boot/specification/executable-jar/nested-jars.html)

### PKG-061 — Verbindliche Regeln für die Shade-Vorlage

Die Vorlage muss Servicebeschreibungen mit `ServicesResourceTransformer` und Groovy-Extension-Module mit `GroovyResourceTransformer` behandeln. Groovy-Module verwenden für ihre Descriptoren ein Propertiesformat; blosses zeilenweises Zusammenhängen wie bei gewöhnlichen Java-Serviceprovidern ist dafür keine ausreichende Strategie. `ManifestResourceTransformer` setzt den Einstiegspunkt. [Maven Shade: Resource Transformers](https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html)

Weitere konkrete Regeln sind Implementierungsentscheidungen der Vorlage:

- Bei verwendetem ImageN die passenden Registryressourcen gezielt zusammenführen, insbesondere `META-INF/org.eclipse.imagen.registryFile.imagen` und `META-INF/registryFile.imagen`, sofern im tatsächlichen Bestand vorhanden.
- Keine automatische Minimierung vermeintlich unbenutzter Klassen; Reflexion, JDBC-Provider, Groovy-Erweiterungen und GeoTools-Factories würden sonst zusätzliche Erreichbarkeitsbeweise benötigen.
- Keine pauschale Paketumbenennung. Shading als Zusammenpacken genügt; eine Relocation ist eine gesonderte Kompatibilitätsentscheidung.
- Doppelte Klassen mit unterschiedlichem Inhalt und unbekannte kollidierende fachliche Ressourcen führen zu einem Buildfehler. Identische Dateien können dedupliziert werden. Bekannte absichtliche Überschneidungen erhalten eine dokumentierte Regel. Shade-Warnungen allein beweisen diese strengere Regel nicht; die Vorlage braucht dafür einen vorgelagerten Kollisionscheck.
- Signaturdateien ursprünglicher Einzel-JARs nicht als weiterhin gültige Signaturen des veränderten Archivs ausgeben; Metadaten, Lizenztexte und Hinweise bewusst erhalten. Original-JAR-Manifestattribute werden nicht unkontrolliert übernommen.
- Multi-Release-Inhalte gezielt erhalten und das resultierende Manifest passend setzen. Für einen Classpath-Build fremde `module-info.class`-Dateien nicht zu einem angeblichen gemeinsamen Moduldescriptor zusammenführen.
- Die qualifizierte JAR-Datei selbst testen, nicht nur den ungepackten Maven-Testclasspath.

`ServicesResourceTransformer` allein ist folglich notwendig, aber nicht ausreichend. Die Vorlage bleibt dennoch wesentlich kleiner als ein neues universelles Classloadersystem.

## 8. Parameter und Secrets

### PKG-070 — Ein gemeinsames Parameterschema und gemeinsame Laufzeitwerte

Jobparameter werden einmal im Groovy-Job mit `parameter(...)` deklariert. Beschreibung, Typ, Pflichtstatus und Default gehören zu diesem normalen Jobcode. Der kompilierte Scriptbody registriert diese Metadaten erst beim Definitionsaufruf beziehungsweise Jobstart. Der Packager führt ihn dafür **nicht** aus und erzeugt kein zweites manuell zu pflegendes Schema. Auch ein GUI- oder Editorformat ist keine Voraussetzung für das erste Job-JAR.

CLI und JSON-Dateien transportieren Werte weiterhin als Strings. `--param name=value`, die Trennung am ersten Gleichheitszeichen, Stringwerte in `--params-file`, die geschlossene Liste ausdrücklich übergebbarer Namen, doppelte Schlüsselnamen, Defaults und sämtliche Konversionen sind identisch zu OPS-012. Explizite `--param`-Werte überschreiben Parameterdatei, gemeinsame Runtime-Defaults und Jobdefaults. Ein leerer Wert wird nicht durch einen Default ersetzt. Die Typen `text`, `boolean`, `integer` und `path` werden über denselben Resolver wie im Scriptmodus bereitgestellt; relative Pfade hängen nicht vom Ablageort der Parameterdatei oder des JARs ab.

Fehlende Pflichtwerte und ungültige ausdrücklich übergebene Werte scheitern vor Taskarbeit mit Code 2. `--plan` lässt fehlende Pflichtwerte als offen stehen, `--help` zeigt die deklarierte Bedienung ohne Auflösung konkreter Werte. Ein erst in einer Taskclosure falsch geschriebener Zugriff bleibt ein Fehler dieser Ausführung. Es gibt keine vorgetäuschte statische Analyse aller möglichen Parameterzugriffe in beliebigen Actions.

Laufzeitparameter dürfen sich bei jedem Start desselben JARs ändern. Das JAR braucht dafür weder neu gebaut noch geändert zu werden. Konfigurierte Jobdefaults sind naturgemäss Teil des festgelegten Jobcodes; konkrete Parameterwerte eines späteren Laufs und Secrets werden vom Packager nicht übernommen. Die beigepackte `README.md` darf zusätzliche fachliche Bedienhinweise enthalten, ersetzt aber keine zweite widersprechende Parameterdeklaration.

Secrets werden über die vorhandenen Laufzeitreferenzen auf Umgebungsvariablen oder Secretdateien und `ctx.secret(alias)` beziehungsweise den Taskblockhelper `secret(alias)` geliefert. Ein Datenbankpasswort wird nicht als Paketparameter eingebrannt. Auch ein erst von einem HTTP-Task ermittelter Token bleibt ein normales Laufzeitergebnis und kann von einem folgenden Task verwendet werden. Das Paketformat verändert weder die späte Taskkonfiguration noch die Regeln zur Geheimnisbehandlung.

## 9. Mindestabnahme der Paketfunktion

### PKG-080 — Prüfung des ausgelieferten JARs

Die Abnahme prüft wenige konkrete Risiken:

1. Derselbe Beispieljob läuft im Scriptmodus und aus dem JAR mit zwei unterschiedlichen Parametersätzen und identischer fachlicher Wirkung. Ein Taskinput hängt vom Ergebnis eines vorherigen Tasks ab; die globale CLI-Parametermap wird deshalb nicht nachträglich mutiert. Parameterdatei, explizite CLI-Überschreibung und ein bewusst leerer Wert werden geprüft.
2. Ein Job mit einer absichtlichen sichtbaren Aktion im Script-Toplevelcode erzeugt diese Wirkung beim Packen nicht. Die Wirkung entsteht erst beim expliziten Start. Der Paketbuild läuft ohne Fachzugänge.
3. Das fertige JAR startet mit Java, aber ohne installierte GRETL-Runtime, Maven, Gradle oder befüllten Artefaktcache. Netzwerkzugriffe auf Programmrepositories sind blockiert; der gewählte Testjob benötigt keine externen Fachquellen.
4. Eine SQL-Datei und ein lokales INTERLIS-Modell sind nach Verlagerung des JARs in ein anderes Verzeichnis weiterhin verfügbar. Ein deklarierter `path`-Parameter bleibt relativ zum ursprünglichen Start-CWD. `file` findet weiterhin die Paketressource. Das Original-JAR bleibt unverändert.
5. Ein Custom-Task aus einem eigenen JAR wird ausgeführt. Ein verwendeter JDBC-Treiber wird entdeckt. Wenn das angebotene Jobpaket eine GeoTools-Fachfähigkeit enthält, findet deren GeoTools-Test die tatsächlich benötigten Factories und den vorgesehenen CRS-Provider. Der erste C-Meilenstein verlangt nicht, GeoTools allein für eine vorbereitende Probe einzupacken. Ein reales Groovy-Extension-Modul funktioniert nach dem Zusammenführen.
6. Ein gezielt erzeugter Konflikt zweier verschiedener gleichnamiger Klassen wird bereits beim Build mit beiden Herkunftsartefakten diagnostiziert.
7. Fehler- und Abbruchverhalten, Logs und Exitcodes entsprechen dem normalen Runner. Ein Stacktrace verweist sinnvoll auf den Jobquelltext.
8. Jobhilfe zeigt dieselben Parameter, Typen, Vorgaben und Ziele wie der Scriptmodus, ohne Taskarbeit, Secrets oder konkrete Parameterwerte. Ihre Registrierungsphase ist sichtbar vom paketierungsseitigen Verbot der Jobausführung getrennt; der Paketbuild benötigt auch für diese Hilfe kein `Script.run`.

Diese Tests müssen das ausgelieferte JAR als eigenen Prozess starten. Ob PostgreSQL, H2 oder ein anderer Treiber für den ersten JDBC-Test dient, ergibt sich aus der tatsächlich gewählten Taskreferenz; es braucht nicht vorsorglich eine neue Treibermatrix.

## 10. Verbindung zum späteren Native Image

### PKG-090 — Geschlossener Programmbestand als späterer AOT-Eingang

Das Job-JAR ist bereits ein geschlossener Bestand aus Code, Bibliotheken und Ressourcen. Das ist ein besserer Ausgangspunkt für ein optionales Native Image eines bestimmten Jobs als ein universeller Runner, der später beliebige neue Groovy-Dateien laden soll. Daraus folgt jedoch keine automatische Native-Kompatibilität der enthaltenen Bibliotheken oder aller Groovy-Sprachmittel.

Eine spätere Native-Paketfunktion darf denselben Deskriptor und dieselben Laufzeitparameter verwenden. Sie muss den **vorkompilierten Job** und den tatsächlich verwendeten Taskbestand qualifizieren. Parameter und dynamische Datenwerte dürfen weiterhin zur Laufzeit variieren; nachträglich völlig neue Programmklassen sind eine andere Anforderung. Der erste Meilenstein ist ein korrektes Job-JAR, danach werden Startzeit und Nutzen gemessen. Es besteht kein Anlass, dafür jetzt ein zweites Laufzeitsystem oder einen experimentellen VM-Unterbau in das MVP aufzunehmen.

## 11. Präzise Aussagen für die überarbeitete Spezifikation

- Ein kleines CLI, ein Scriptmodus und optionale Jobartefakte sind miteinander vereinbar.
- Das Packen eines Jobs ist freiwillig; direktes Bearbeiten und Starten der Groovy-Datei bleibt der Standard.
- Ein Paket friert Code und Dependencies ein. Es friert keine Parameterwerte, Geheimnisse oder Tagesdaten ein.
- Vorkompilation spart die erneute Groovy-Kompilation. Sie entfernt weder JVM-Start noch Bibliotheksinitialisierung noch die Groovy-Runtime.
- Publisher kann nach seiner späteren Lieferung als gewöhnliche Custom-Task-Bibliothek enthalten sein, soweit der konkrete Job ihn benötigt. Er ist keine Voraussetzung des ersten Packagers. Es gibt keine spezielle Paketsemantik für seine Geschäftslogik.
- Ein Job-JAR übernimmt den bewusst deklarierten benötigten Taskbestand und dessen Dependencies. Es muss nicht jede inzwischen verfügbare Taskbibliothek der Standarddistribution enthalten.
- Ein erster normaler Fat-JAR-Pfad muss einen zusammen qualifizierten Classpath verwenden. Unverträgliche Bibliotheksstände werden durch die Paketfunktion nicht magisch kompatibel.
- Eine spätere isolierte Taskausführung kann externe Begleitartefakte benötigen. Ein solches Lieferpaket darf dann nicht irreführend als vollständig alleinstehendes Einzel-JAR bezeichnet werden. Das erste Einzel-JAR-Profil muss diesen Fall nicht lösen.

## 12. Quellen

| Aussage | Primärquelle |
|---|---|
| Groovy zu Bytecode, `--basescript`, zusätzliche erzeugte Klassen | [Groovy: groovyc](https://groovy-lang.org/groovyc.html) |
| Einbettung über eigene Scriptbasisklasse und getrenntes Parse/Run | [Groovy: Integration](https://docs.groovy-lang.org/latest/html/documentation/guide-integrating.html) |
| Shade-Service-, Groovy- und Manifest-Transformer | [Maven Shade: Resource Transformers](https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html) |
| GeoTools-Fat-JAR, Services und ImageN-Registries | [GeoTools Building FAQ](https://docs.geotools.org/stable/userguide/build/faq.html) |
| JAR-Manifest, Classpath, Multi-Release-Regeln | [Java 25: JAR File Specification](https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html) |
| Nested JAR braucht eigenen Loader | [Spring Boot: Nested JARs](https://docs.spring.io/spring-boot/specification/executable-jar/nested-jars.html) |

Die volatile GeoTools-FAQ enthält in ihren Beispielen noch ältere konkrete Versionsnummern. Hier wird ihre Verpackungstechnik verwendet; die Auswahl aktueller Java-/GeoTools-Versionen ist bereits separat in der Hauptspezifikation belegt. Nicht aus den Beispielversionsnummern dieser FAQ rückwärts auf die Plattformempfehlung schliessen.
