# M0-Prüfbericht — qualifizierter Anfangsbestand

**Datum:** 18. Oktober 2026  
**Meilenstein:** M0 gemäss `docs/spec/10-implementierungsplan-fuer-coding-agenten.md`  
**Spezifikationsbasis:** 4.1  
**Ergebnis:** M0 ist für den beschriebenen Anfangsbestand abgeschlossen.
Die Proben und Nachweise sind ausgeführt; die späteren Meilensteine M1–M6
bleiben ausdrücklich offen.

> **Historischer Bericht:** Die M0-Läufe verwendeten die Projektversion
> `0.1.0-M0`. Der M1-Arbeitsbaum hat die Projektversion auf `0.1.0-M1`
> angehoben; die M0-Nachweise und Artefaktbezeichnungen in diesem Bericht
> bleiben als damaliger Stand dokumentiert.

## 1. Ausgangslage

Das Repository enthielt bei Auftragsbeginn ausser `AGENTS.md`, `README.md`,
`LICENSE` und `docs/spec/` keine Produktquellen, Builddateien oder
Testinfrastruktur. Es gab daher keine bestehende Runtimeimplementierung, die
hätte übernommen oder ersetzt werden können. Der Anfangsbestand wurde neu
angelegt; die Spezifikation 4.1 und ihre Kapitel 03/04/06/07/09/10/14/15/16/17
waren massgeblich.

**Quellstand:** initialer Repository-Commit `decf56f` plus der nicht
committete M0-Arbeitsbaum dieses Auftrags.

## 2. Tatsächlich verwendete Toolchain und Bibliotheken

### 2.1 Buildplattform

| Bestandteil | Verwendete Version | Beleg / Entscheidung |
|---|---|---|
| JDK | Eclipse Temurin **25.0.4.1+1** (`25.0.4.1+1-LTS`) | Adoptium-API; Tar.gz-SHA-256 `61979887f7506a24a57439ff99adb8b3a7fc89977d9cfe3b8984f58a981b7b9d` geprüft; `java -version` im Build. |
| Maven | Apache Maven **3.10.0** über den Repository-Wrapper | Maven-Central-Metadaten; Wrapper 3.3.4 `only-script`. Zip-SHA-256 `1f6d9909266510f039f59aa0e13dcd2c66da85f043e56276e41f2918f8bddaff`; Apache-SHA-512 `22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337` ebenfalls geprüft. |
| Maven Wrapper | 3.3.4, Typ `only-script` | `.mvn/wrapper/maven-wrapper.properties`, `mvnw`, `mvnw.cmd`. |
| Betriebssystem des Nachweises | macOS 15.8.1, ARM64 | Entwicklungsplattform; Linux x86-64 ist die spätere Produktplattform und nicht in M0 abgenommen. |

Die Standardprüfung wurde immer mit `JAVA_HOME` auf Temurin 25.0.4.1+1 und dem
Wrapper ausgeführt. Ein Java-21-Lauf und ein System-Maven-3.9.6-Lauf wurden
zusätzlich als Negativproben ausgeführt und vom Enforcer mit der vorgesehenen
Meldung abgelehnt.

### 2.2 Produkt- und Testbibliotheken

| Zweck | Artefakt | Version | Scope / Verwendung |
|---|---|---|---|
| API (JDK-only) | – | – | keine externen Compile-Abhängigkeiten |
| Tests API | `org.junit.jupiter:junit-jupiter` | 5.14.4 | test |
| Groovy-Einbettung | `org.apache.groovy:groovy` | 6.0.0 | test (M0-Probe; Produktadapter folgt M2) |
| SLF4J-Fassade | `org.slf4j:slf4j-api` | 2.0.20 | test (M0-Inventar) |
| Logging-Provider | `ch.qos.logback:logback-classic` / `logback-core` | 1.6.5 | test (genau ein Provider) |
| JUL-Brücke | `org.slf4j:jul-to-slf4j` | 2.0.20 | test (explizite Installation geprüft) |
| PostgreSQL-Treiber | `org.postgresql:postgresql` | 42.7.14 | test, nur Laden/ABI |
| Validator B | `ch.interlis:ilivalidator` | 1.15.0 | test, reale lokale Validierung |
| Validator-Transitives | `ch.interlis:ili2c-core` / `ili2c-tool` | 5.6.8 | test, transitiv aus ilivalidator |
| Validator-Transitives | `ch.interlis:iox-ili` | 1.24.4 | test, transitiv aus ilivalidator |
| Validator-Transitives | `ch.interlis:iox-api`, `ch.ehi:ehibasics`, `antlr:antlr` | 1.0.4 / 1.4.1 / 2.7.7 | test, transitiv |
| GeoTools-Probe | `org.geotools:gt-main`, `gt-referencing`, `gt-shapefile`, `gt-epsg-hsql` | 35.2 | test, **nur** im Profil `geotools-probe` |
| Geometrie | `org.locationtech.jts:jts-core` | 1.20.0 | test, transitiv über GeoTools-BOM |
| EPSG-Datenbank | `org.hsqldb:hsqldb` | 2.7.4 | test, transitiv über `gt-epsg-hsql` |
| Weitere GeoTools-Transitives | u. a. ImageN 0.9.3, Jackson 3.2.3, EJML 0.41, RE2J 1.8, jgridshift 1.3 | – | test, nur GeoTools-Profil |

Die vollständigen aufgelösten Bäume sind mit
`./mvnw dependency:tree` reproduzierbar. Die GeoTools-Testabhängigkeiten sind
weder Compile-Abhängigkeit noch Bestandteil der Standarddistribution; sie
liegen ausschliesslich im separaten Profilmodul.

Für die Validatorprobe bleiben bewusst die von `ilivalidator` 1.15.0
deklarierten Transitiven `ili2c` 5.6.8 und `iox-ili` 1.24.4 gewählt. Die
INTERLIS-Repository-Metadaten nennen zwar neuere Stände (u. a. `ili2c` 5.6.9,
`iox-ili` 1.24.5); diese wurden für M0 nicht ungeprüft über die geprüfte
Validator-Kombination gelegt. Eine spätere B-Integration darf sie nur mit
eigener Prüfung wählen. Für JUnit bleibt M0 auf der stabilen 5.x-Linie
(5.14.4); eine JUnit-6-Migration ist für den Anfangsbestand nicht nötig und
wurde nicht vorweggenommen.

### 2.3 Maven-Plugins

`maven-clean-plugin` 3.5.0, `maven-resources-plugin` 3.5.0,
`maven-compiler-plugin` 3.16.0, `maven-surefire-plugin` 3.6.0,
`maven-jar-plugin` 3.5.1, `maven-install-plugin` 3.2.0,
`maven-deploy-plugin` 3.2.0, `maven-enforcer-plugin` 3.6.3.

Alle Pluginversionen sind im Root-POM zentral fixiert. Es gibt kein
Groovy-Maven-Plugin, kein Shade-Plugin und keine eigene Build-/Lifecycle-
Erweiterung. Die Begründung jedes aktiven Plugins steht in
`docs/implementation/build.md`.

## 3. Erzeugte Module und Artefakte

| Modul / Artefakt | Inhalt | Ergebnis |
|---|---|---|
| Root `ch.so.agi.gretl:gretl-runtime-parent:0.1.0-M0` | Aggregator und einziger projektinterner Parent | POM |
| `gretl-api` | 27 öffentliche API-Dateien aus Kapitel 14 | `gretl-api/target/gretl-api-0.1.0-M0.jar` |
| `examples/file-size-extension` | separater `OperationProvider` samt ServiceLoader-Metadaten, **ohne** Produktparent | `examples/file-size-extension/target/file-size-extension-0.1.0-M0.jar` |
| `gretl-m0-qualification` | reine Testbaugruppe; kein Verteilungsartefakt | kein JAR (`skipIfEmpty`), Testberichte vorhanden |
| `gretl-geotools-probe` | nur mit `-Pgeotools-probe` im Reactor | kein Standardartefakt; Testberichte vorhanden |

Das API-JAR enthält genau 27 `.class`-Dateien, Bytecode-Major **69** (Java 25),
`Automatic-Module-Name: ch.so.agi.gretl.runtime.api`. `jdeps` meldet
ausschliesslich `java.base`. Das Erweiterungs-JAR enthält den Provider und
seine kleinen geschachtelten Typen, aber keine API-Klassen und keine
Fremdbibliotheken; seine ServiceLoader-Datei enthält genau
`example.ExampleProvider`.

## 4. Ausgeführte Befehle und Ergebnisse

Alle Befehle wurden mit JDK 25 und dem Repository-Wrapper ausgeführt.
Die Standardprüfung ist reproduzierbar:

```sh
./mvnw clean verify
```

### 4.1 Standardumfang

| Prüfung | Ergebnis |
|---|---|
| `gretl-api` | **17 Tests**, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| `gretl-m0-qualification` | **16 Tests**, 0 Fehler, 0 Fehlschläge, 0 übersprungen |
| Gesamt | **33 Tests**, `BUILD SUCCESS` |
| Erweiterungsmodul | baut separat als JAR, keine Tests |

Frischer Nachweis ohne projektspezifischen Artefaktbestand:

```sh
./mvnw clean verify -Dmaven.repo.local=/tmp/m0-fresh-final
```

Ergebnis: `BUILD SUCCESS`, dieselben 33 Tests. Maven, Plugins und
Bibliotheken wurden ausschliesslich aus den dokumentierten Repositories
aufgelöst. Es waren keine vorinstallierten GRETL-Artefakte oder ein privates
Parentartefakt nötig.

Zusätzlich wurde derselbe Umfang mit bereits aufgelöstem lokalem
Dependencycache **offline** geprüft:

```sh
./mvnw -o clean verify
```

Ergebnis: 33 Tests, `BUILD SUCCESS`. Die Standardprüfung benötigt damit keine
externen Dienste; nur der erstmalige Build benötigt die dokumentierten
Repositories. Der spätere normale Jobstart ist davon getrennt und in M0 noch
nicht implementiert.

### 4.2 GeoTools-Profil

```sh
./mvnw -Pgeotools-probe -pl gretl-geotools-probe -am verify
```

Ergebnis: **17 API-Tests + 4 GeoTools-Tests = 21 Tests**, 0 Fehler,
`BUILD SUCCESS`. Auch mit einem frischen lokalen Repository
(`/tmp/m0-fresh-final-gt`) erfolgreich. Zusätzlich wurde das Profil offline
mit dem aufgelösten Dependencycache geprüft
(`./mvnw -o -Pgeotools-probe -pl gretl-geotools-probe -am verify`, 21 Tests,
`BUILD SUCCESS`). Der vollständige Profil-Reactorbuild
`./mvnw -Pgeotools-probe verify` lief ebenfalls erfolgreich (37 Tests gesamt).
Die vier GeoTools-Fälle decken ab:

1. SHP schreiben und nach Schliessen wieder lesen; drei Punkte, explizites
   Schema, CRS EPSG:2056, Umlaute, Nullwert, Zahlen; Feldbreiten und UTF-8
   bewusst gesetzt.
2. Unabhängiger Kontrollpunkt E=2'700'000, N=1'100'000 nach EPSG:4326:
   Länge `8.7304972222`, Breite `46.0441305556`, Toleranz `0.00005` je
   Komponente. Die Reihenfolge Longitude/Latitude wird ausdrücklich geprüft.
   Referenz: [swisstopo-Näherungsformeln, Dezember 2016](https://www.swisstopo.admin.ch/dam/en/sd-web/KLRCX9XIdXDu/ch1903wgs84-EN.pdf),
   Abschnitt 2; die Dezimalwerte wurden aus den dort angegebenen Winkeln
   umgerechnet.
3. Explizite geografische Ausgabe (EPSG:4326) schreiben/lesen; keine
   pauschale LV95-Deklaration.
4. Negativprozess ohne `gt-epsg-hsql`/HSQLDB im Klassenpfad: `CRS.decode`
   scheitert sichtbar mit einer Authority-/NoSuchAuthorityCode-Meldung; kein
   stiller Ersatz-CRS. Datastores werden geschlossen, Dateien sind danach
   lesbar und entfernbar.

Zusätzliche, nur im Profil sichtbare Testmodule und ihre Transitiven sind in
Abschnitt 2.2 und in `docs/implementation/build.md` ausgewiesen. Ein
GPKG-Erzeugnis oder Publisherpfad wurde nicht als GeoTools-Nachweis verwendet.

### 4.3 Weitere ausgeführte Nachweise

| Befehl | Ergebnis |
|---|---|
| `./mvnw -pl gretl-api test` | 17 Tests erfolgreich |
| `./mvnw -pl examples/file-size-extension -am package` | Erweiterungs-JAR erfolgreich |
| `./mvnw -pl gretl-api -am install -DskipTests` + `./mvnw -f examples/file-size-extension/pom.xml package` | Erweiterung baut allein gegen die installierte API, ohne Produktparent |
| `JAVA_HOME=…/21.0.7-tem ./mvnw -pl gretl-api validate` | erwarteter Fehlschlag: Enforcer verlangt Java 25 |
| System-Maven 3.9.6 mit JDK 25: `mvn -pl gretl-api validate` | erwarteter Fehlschlag: Enforcer verlangt Maven 3.10.0 |
| `./mvnw -pl gretl-api dependency:tree -Dscope=compile` | nur das API-Artefakt, keine Compile-Abhängigkeiten |
| `./mvnw -pl gretl-api dependency:tree` | nur JUnit 5.14.4 und dessen Testtransitives |
| `jdeps --multi-release 25 --ignore-missing-deps -summary gretl-api/…jar` | `-> java.base` |
| `jar tf` / `unzip -p` der JARs | 27 API-Klassen; ServiceLoader-Eintrag genau `example.ExampleProvider`; keine API-Klassen im Erweiterungs-JAR |

## 5. Abgleich mit M0 und den anwendbaren TC-BLD-Fällen

| Anforderung | Nachweis | Status |
|---|---|---|
| Keine ungeprüften SNAPSHOTs; feste Stände | Alle externen Versionen sind fest; Projektversion `0.1.0-M0` ist kein SNAPSHOT. | erfüllt |
| JDK 25 und Ausgangsversionen prüfen | Temurin 25.0.4.1+1, Groovy 6.0.0, GeoTools 35.2, Maven 3.10.0 tatsächlich verwendet. | erfüllt |
| Maven Wrapper, Root-Parent, benötigte Module | Ein Wrapper, ein Parent, vier tatsächlich benötigte Module; keine leeren Zukunftsmodule. | erfüllt |
| Buildanleitung, Effective POM, Dependencybaum | `docs/implementation/build.md`; Effective POM und Bäume geprüft. | erfüllt |
| Kein Groovy-Produktcompiler nur für Jobfixtures | Kein Groovy-Plugin; Groovy ist eingebettete Testbibliothek; `.groovy`-Probe wird zur Laufzeit kompiliert. | erfüllt |
| Kleine Groovy-Probe mit eigener Basisklasse | Externe `.groovy`-Datei, `M0ProbeScriptBase`, Map-first-Signaturen, DELEGATE_ONLY, gespeicherte Closures, spätere Auswertung, Reload nach Änderung. | erfüllt |
| Unabhängig gebaute Java-Operation | FileSize-Provider als separates JAR über ServiceLoader, echte temporäre Datei, tatsächliche Bytezahl und Metadaten. | erfüllt |
| ABI-/Ladefähigkeit A | JDK-only API; pgJDBC 42.7.14 geladen, JDBC-Service registriert, URL akzeptiert; keine Live-DB in M0. | erfüllt (Laden/ABI) |
| Reale Validatorintegration B vorbereiten | ilivalidator 1.15.0 kompiliert ein lokales Modell und validiert gültige/ungültige XTF; echte Diagnosemeldung „Attribute name requires a value“ erhalten. | erfüllt (Bibliotheksprobe, kein Taskadapter) |
| GeoTools-Probe im getrennten Profil | `-Pgeotools-probe`, vier Tests, zusätzliche Dependencies offen ausgewiesen. | erfüllt |
| API auf Java 25 bauen | 27 Dateien mit `--release 25`, Major 69. | erfüllt |
| Separates Provider-JAR | Ja; Providerklasse ist auf dem Testklassenpfad nicht ladbar, nur aus dem JAR. | erfüllt |
| Loggingprovider/-bridges inventarisieren | Genau ein Provider (`LogbackServiceProvider`); kein Legacy-Binder, keine Log4j-API/Bridge; JUL-Brücke explizit installiert und auf genau eine Weiterleitung geprüft; Cause-/Suppressed-Kette erhalten. | erfüllt (Inventar) |
| Publisher/XtfToGpkg nicht bauen | Keine Module, Provider, Dependencies oder Stubs vorhanden. | erfüllt |
| M0 nicht mit M1/M2 vermischen | Kein Runner, keine Planung, keine Produkt-DSL, keine CLI, keine Fachtaskadapter. | erfüllt |

### TC-BLD

| Fall | Ergebnis |
|---|---|
| **TC-BLD01** | Erfüllt: frischer lokaler Repositorybestand, gemeinsamer Wrapper, Maven 3.10.0/JDK 25, `clean verify` erfolgreich ohne vorinstallierte GRETL-Artefakte. |
| **TC-BLD02** | Erfüllt: genau ein projektinterner Parent; Standardlayout; Erweiterungsmodul ohne Parent; Modulreihenfolge/Reactor; keine leeren Zukunftsmodule. |
| **TC-BLD03** | Erfüllt: feste Versionen; Effective POM/Dependencybäume; API ohne Compile-/Fach-/Logging-Dependencies; GeoTools nur testseitig im Profil. |
| **TC-BLD04** | Erfüllt: keine Groovy-Buildintegration; Groovy 6.0.0 ist eingebettete Bibliothek; `.groovy`-Testressource wird real ausgeführt. |
| **TC-BLD05** | Für M0 erfüllt: `verify` ist der Standard; ein zusätzliches Profil ist dokumentiert; keine übersprungenen Tests; keine heimliche Umgebungsaktivierung. |
| **TC-BLD06** | Erfüllt: kurzer Pluginbestand mit konkretem Zweck; keine Buildscripts, kein eigenes Maven-Plugin, keine Convention-Plattform. |
| **TC-BLD07** | Für M0 **nicht anwendbar**: Es gibt noch keine Standarddistribution und keinen Packager. Kein Shading, keine JAR-Bündelung. Wird erst in M3/M6 geprüft. |
| **TC-BLD08** | Erfüllt: Buildanleitung mit Voraussetzungen, Standardbefehl, Modulbuild, Profil, Artefaktorten und Pluginzwecken. |

## 6. Art der Nachweise: Kompilation, Metadaten, Klassenladen, reale Ausführung

Die Prüfungen unterscheiden bewusst vier Stufen:

| Stufe | Was tatsächlich geprüft wurde | Was damit **nicht** bewiesen ist |
|---|---|---|
| **Quellkompilation** | 27 API-Dateien mit `javac --release 25` und `-Xlint:all`; Erweiterungsquelle mit Java 25; Groovy-Skript mit Zielbytecode 25. | Keine Laufzeitsemantik von Planung, Context oder DSL. |
| **Metadatenprüfung** | Effective POM, Dependencybäume, JAR-Manifeste, ServiceLoader-Datei, JAR-Klassenzahl, Bytecode-Major, `jdeps` nur `java.base`. | Eine korrekte ServiceLoader-Datei allein beweist keinen erfolgreichen Provideraufruf. |
| **Klassenladen** | ServiceLoader findet `ExampleProvider` nur aus dem separaten JAR; pgJDBC/JDBC-Service; GeoTools-/EPSG-Klassen; Groovy-6-Compiler; Logback-Provider. | Klassenladen beweist keine fachliche Funktion, keine Transaktion, keine CRS-Korrektheit. |
| **Reale Ausführung** | FileSize-Handler liest die tatsächliche Bytezahl einer temporären UTF-8-Datei; Groovy-Skript lädt/registriert und wertet gespeicherte Closures aus; ilivalidator validiert gültige/ungültige XTF; GeoTools schreibt/liest SHP und transformiert den Kontrollpunkt; Logback erhält Cause/Suppressed; JUL-Brücke leitet genau einmal weiter. | Keine vollständige Distribution, kein Jobstart, kein CLI-/Reportpfad, kein echter SQL-/HTTP-/Publisherpfad. |

Insbesondere ist ein erfolgreicher Java-21-Referenztest aus Kapitel 17
**nicht** als M0-Produktnachweis verwendet worden. Alle genannten
Produktprüfungen liefen auf Java 25.

## 7. Groovy-, Erweiterungs- und Loggingproben im Detail

### Groovy-Probe

Die Probe ist ausdrücklich **kein M2-DSL-Adapter**. Sie verwendet:

- eine eigene, nur im Testbaum liegende Scriptbasisklasse
  `M0ProbeScriptBase`,
- eine externe, echte `.groovy`-Datei unter Testressourcen,
- die echte Groovy-6.0.0-Bibliothek mit `CompilerConfiguration`,
  `scriptBaseClass`, UTF-8 und `targetBytecode=25`,
- die dokumentierten Map-first-Formen `parameter('quelle', required: true, …)`
  und `task('messen', 'FileSize', dependsOn: 'vorbereiten') { … }`,
- `Closure.rehydrate` und `Closure.DELEGATE_ONLY` mit frischem
  Konfigurationsscope,
- gespeicherte Closures, die beim Laden nicht und erst beim Aufruf genau
  einmal ausgewertet werden,
- einen geänderten Scripttext, der mit einem neuen Groovy-ClassLoader erneut
  geladen und ausgeführt wird, ohne das Produkt neu zu bauen,
- Fehlerfälle für unbekannte Felder und Closure-Arität > 1.

Die Probe prüft ausserdem, dass der erzeugte Script-Bytecode Major 69 hat.
Sie ersetzt weder die vollständige M2-DSL noch Quellzeilen-/Fehlerqualität,
`ctx.run`, Parameterauflösung oder den echten Runner.

### FileSize-Erweiterung

Die Erweiterung ist ein separates Maven-Projekt ohne Produktparent. Sie
deklariert `gretl-api` als `provided`, enthält nur `example.ExampleProvider`
und die ServiceLoader-Datei und wird als eigenes JAR gebaut. Der Test:

- schliesst die Providerklasse auf dem Testklassenpfad aus,
- lädt sie ausschliesslich über einen `URLClassLoader` aus dem JAR,
- entdeckt sie über `ServiceLoader<OperationProvider>`,
- prüft Metadaten (ID, Symbol, Beschreibung, Pflichtfeld, Beispiel, Ausgang),
- erzeugt eine echte temporäre UTF-8-Datei mit Umlauten,
- baut den Request über `InputField.convert`/`assign` und den Builder,
- führt den Handler aus und vergleicht die gelieferte Bytezahl mit
  `Files.readAllBytes(...).length` und `Files.size(...)`,
- wiederholt den Aufruf mit einer zweiten Datei und prüft frische
  Builder-/Handlerinstanzen.

Damit ist der Nachweis nicht aus Testquellen, sondern aus dem separat gebauten
JAR erbracht.

### Logging-Inventar

Verwendet werden SLF4J-API 2.0.20 und genau ein Provider, Logback Classic/Core
1.6.5 (`ch.qos.logback.classic.spi.LogbackServiceProvider`). Logback 1.6.5
deklariert transitiv SLF4J 2.0.19; die direkte, geprüfte Bindung ist 2.0.20,
und der Provider-/Cause-/Bridge-Test besteht damit. Der Test stellt fest:

- genau ein `SLF4JServiceProvider`,
- kein Legacy-`StaticLoggerBinder`, keine Log4j-API, kein Log4j-zu-SLF4J,
- keine doppelte JUL-Weiterleitung nach expliziter Installation der
  `jul-to-slf4j`-Brücke,
- Cause- und Suppressed-Kette sind im Logback-`ThrowableProxy` vollständig
  erhalten (IOException als Cause, IllegalArgumentException als suppressed).

**Noch nicht geliefert** sind der produktive `TaskLogger`-Adapter, die
Wire-Konfiguration des Backends, Redaction, JSON-Ereignisdatei, Runbericht,
MDC-Restoration und die EHI-Anbindung. Diese gehören in M1/M3/M4 und wurden
nicht durch einen konkurrierenden Loggervertrag vorweggenommen.

## 8. A/B-Bibliotheken, Loggingbrücken und offene Anbindungen

### A

Die für A vorgesehenen HTTP- und Archivpfade können mit JDK-Bordmitteln
(`java.net.http`, `ZipInputStream`, `GZIPInputStream`) umgesetzt werden; in M0
wurde dafür noch kein Taskadapter gebaut. Der PostgreSQL-Pfad wurde mit
pgJDBC 42.7.14 geladen: Treiberklasse, JDBC-Service, URL-Akzeptanz und
Versionsmetadaten wurden geprüft. Eine reale Datenbankverbindung,
Transaktionen, Rollback und SQL-Skriptausführung sind ausdrücklich M3.

### B

ilivalidator 1.15.0 (mit ili2c 5.6.8, iox-ili 1.24.4 und ihren Transitiven)
wurde real aufgerufen. Ein lokales INTERLIS-2.3-Modell und zwei XTF-Fixtures
wurden verwendet; der gültige Transfer bestand, der ungültige scheiterte mit
der echten Meldung „Attribute name requires a value“. Damit sind
Modellkompilation, Validatorausführung und die Bibliothekskette auf Java 25
qualifiziert. **Nicht** implementiert sind der IliValidator-Taskadapter, die
EHI-/Iox-Listeneranbindung, Warnungszählung und der aufrufbezogene
Validierungsbericht; das ist M4.

### Logging- und JUL-/EHI-Anbindungen

- **JUL:** Die JUL→SLF4J-Brücke `jul-to-slf4j` 2.0.20 ist vorhanden und wurde
  getestet. Sie muss später beim Produktbootstrap ausdrücklich installiert
  werden (`SLF4JBridgeHandler.removeHandlersForRootLogger()` und
  `SLF4JBridgeHandler.install()`), nicht allein durch Classpathzufall. pgJDBC
  und GeoTools protokollieren über JUL; vor einer echten SQL-/GeoTools-
  Taskauslieferung sind deren JUL-Pfade gezielt zu prüfen und zu filtern.
- **EHI/Iox:** ilivalidator nutzt den EHI-/Iox-Logger, nicht SLF4J. Eine
  EHI→`TaskLogger`-Anbindung ist **offen** und gehört in M4. Sie muss pro
  Invocation einen exakten Listener installieren, die originale Exception
  übernehmen, nach Erfolg/Fehler im `finally` exakt entfernen und keine
  globalen Listener akkumulieren. Die M0-Probe hat diese Anbindung bewusst
  nicht vorweggenommen.
- **Mehrfachprovider/Bridgeschleifen:** Auf dem M0-Qualifikationsklassenpfad
  wurde genau ein Provider gefunden. Es gibt keine gegenläufige
  Log4j-/SLF4J-Brücke. Ein JCL- oder Log4j-1.x-Bridge ist nicht enthalten und
  wird nur bei konkretem Bedarf einer gelieferten Bibliothek ergänzt. Die
  Aussage gilt für den geprüften Testklassenpfad, nicht für eine spätere
  Release-Distribution; diese Prüfung wird in M3/M6 wiederholt.

## 9. Verbleibende Risiken und nicht erbrachte Nachweise

### Bewusst ausserhalb von M0

- **M1:** Es gibt noch keinen `SequentialJobRunner`, keine Planung,
  Registry, Context-Lebensdauer, ResultStore- oder FailureArbiter-Semantik.
  Die API ist kompiliert und metadata-/isolationsseitig geprüft, nicht als
  Laufzeit ausgeführt.
- **M2:** Es gibt keine produktive Groovy-DSL, keine Parameterauflösung,
  keine Quellzeilen-/Fehlerqualität und keinen `ctx.run`-Adapter. Die
  Groovy-Probe ist ein klar gekennzeichneter Testharness.
- **M3:** Es gibt keine CLI, keine Runtimekonfiguration, keine HTTP-/Archiv-/
  SQL-Taskadapter, keine Live-PostgreSQL-Prüfung, keine Redaction, keine
  JSON-Ereignisdatei und keinen Runbericht.
- **M4:** Es gibt keinen IliValidator-Taskadapter und keine EHI-Anbindung;
  die reale Validatorprobe ersetzt diese Integration nicht.
- **M6/C:** Es gibt kein Job-Fat-JAR, keinen Packager und keine
  Distributionsmanifest-/Prüfsummenprüfung.
- **Publisher/XtfToGpkg:** nicht implementiert, nicht registriert, nicht
  mitgeliefert.
- **Native Image:** nicht begonnen.
- **Fachanwenderprüfung:** nicht durchgeführt; ein Coding-Agent darf sie
  nicht behaupten.

### Technische Restrisiken

- Die GeoTools-Probe ist eine Vektor-/CRS-Punktprobe. Raster-, ImageIO-/
  ImageN-, vollständige Format- oder Headless-Unterstützung sind nicht
  qualifiziert.
- `gt-metadata` 35.2 verwendet `sun.misc.Unsafe::invokeCleaner`; die JVM warnt
  auf Java 25. Die Probe läuft, aber die spätere Entfernung dieser API ist ein
  beobachtetes Upstream-Risiko.
- ilivalidator 1.15.0 bringt eine ältere JAXB-2.3-Kette mit. Die reale
  Validierung lief auf Java 25; spätere Validator-/Distributionspakete müssen
  diese Kette erneut prüfen.
- Die M0-Loggingprüfung ist ein Testklassenpfad-Inventar, keine
  Distributionsprüfung. Mehrfachprovider und Brücken müssen am späteren
  Releasepaket wiederholt werden.
- Der Maven-Build kontaktiert für die Testproben zusätzliche Repositories
  (`jars.interlis.ch`, optional OSGeo). Ein nicht verfügbares Repository
  führt zu einem ehrlichen Fehlschlag, nicht zu einem stillen Skip.
- Die Standardprüfung benötigt keine Live-Dienste; dadurch sind echte
  HTTP-/SQL-/Validator-Adapterpfade noch nicht abgenommen.
- Ein grüner `verify`-Lauf ist kein Nachweis der späteren Anwenderfreigabe
  oder der Linux-Produktionsdistribution.

## 10. Fazit und Stoppgrenze

**M0 ist vollständig abgeschlossen.** Der tatsächliche Anfangsbestand ist
qualifiziert: Java-25-Toolchain, Maven-Wrapper/Parent, die vollständige
öffentliche API, die externe Erweiterung, die Groovy-6-Einbettung, die
A/B-Bibliotheksproben, das Logginginventar und die getrennte GeoTools-Probe
wurden gebaut und real ausgeführt. Alle 33 Standardtests und alle 4
GeoTools-Profiltests bestehen; die Negativproben für Java 21 und System-Maven
scheitern wie vorgesehen.

M1 wurde nicht begonnen. Der nächste konkrete Schritt wäre die Implementierung
der in Kapitel 15 beschriebenen Kernrollen (`OperationRegistry`,
`JobDefinitionBuilder`, `ExecutionPlanner`, `ResultStore`,
`OperationInvoker`, `SequentialJobRunner`, `FailureArbiter`) und ihrer
Java-25-Tests gemäss M1. Dieser Schritt wird im Rahmen dieses Auftrags
ausdrücklich nicht ausgeführt.
