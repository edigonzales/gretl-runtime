# M2-Prüfbericht: Groovy-6-Anwenderfassade und CORE-040-Harness

> Historischer Meilensteinbericht: Aussagen und Modulnamen gelten für den hier
> dokumentierten Commit. Aktueller Zuschnitt: [Modulgrenzen](module-boundaries.md).

> **Historischer Bericht.** Dieser Bericht dokumentiert den Stand von M2
> (`0.1.0-M2`, 197 Standard-/201 Profil-Tests). M3 ergänzt CLI, Logging,
> Runbericht und die Standard-Tasks; aktuell ist der
> [M3-Bericht](m3-report.md).

**Stand:** 18. Oktober 2026  
**Basis:** Spezifikation 4.1, Kapitel 04, 09, 14 und 15  
**Projektversion:** `0.1.0-M2`  
**Status:** M2 abgeschlossen; M3 nicht begonnen

## 1. Auftrag und Abgrenzung

M2 sollte die kanonische Groovy-Anwenderform auf den bestehenden Java-Runner
setzen: eine Scriptbasisklasse, Map-first-Methoden, eine frische
Konfigurationsfassade pro Invocation, `ResultView`, späte Closureauswertung,
`ctx.run` und eine ausführbare Referenzprobe gemäss CORE-040. Die echte
Groovy-6-Ausführung war ausdrücklich das offene Implementierungsgate.

Nicht Gegenstand von M2 waren CLI, Runtimekonfiguration, HTTP-/ZIP-/SQL-/
IliValidator-Tasks, Live-PostgreSQL, Produkt-Loggingpipeline, Runreport,
Redaction, Packager, Native Image und Publisher. Diese Grenzen bleiben
unverändert.

## 2. Umgesetzte Bausteine

Neu ist das Produktmodul **`gretl-groovy`** mit 16 Java-Quellen und 16 Klassen.
Es hängt von `gretl-api`, `gretl-runner` und Groovy 6.0.0 ab. `gretl-api` bleibt
JDK-only; `gretl-runner` bleibt frei von Groovy.

### 2.1 Scriptbasis und Laden

- `GretlScript` stellt die Map-first-Methoden `parameter`, `task`, `action`,
  `defaults` und `cleanup` bereit.
- `GroovyJobLoader` erzeugt pro Ladung einen frischen `GroovyClassLoader`, ein
  frisches `Binding` und eine neue `JobDefinition`. Operationstypen werden als
  Symbole gebunden; unbekannte Symbole sind Ladefehler.
- `CompilerSettings` setzt `GretlScript` als Basisklasse, Zielbytecode `25`,
  UTF-8 und deaktiviert die Grape-Transformation
  `groovy.grape.GrabAnnotationTransformation`.
- Der Toplevel-Code registriert Definitionen. Konfigurations-, Action-,
  `when`- und Cleanup-Closures werden nicht beim Laden ausgewertet.
- `DefinitionEnvironment` verbindet Registry, Jobwurzel, Startverzeichnis und
  einen expliziten `UserMessageSink`.

### 2.2 Taskkonfiguration

`TaskConfigurationScope` wird für jede typisierte Invocation und jedes
`ctx.run` frisch erzeugt. Er verbindet:

- die deklarierten `InputField`s der Taskart,
- Defaultwerte, die durch denselben Konverter wie Zuweisungen geführt werden,
- die gespeicherte Closure mit `Closure.DELEGATE_ONLY`,
- die unveränderte ursprüngliche Closure,
- den inneren Invocationkontext für Workspace/Log/Parameter,
- den äusseren Taskkontext ausschliesslich für lesende Ergebnis- und
  Finish-Helfer.

Zuweisungen werden beim Setzen konvertiert und gespeichert. Nach der Closure
wird jedes Feld genau einmal auf den frischen Builder angewendet. Damit gilt
die letzte vollständige Zuweisung; es gibt keine geheime Listenaddition durch
wiederholte Setteraufrufe. Pflichtfelder werden vor dem Builder geprüft.

Unbekannte Felder, reservierte Namen, ungültige Werte und Feldaufrufe wie
`text 'x'` liefern eine `GroovyDslException` mit Task, Feld, verfügbaren Feldern,
Quelldatei/Zeile und Korrekturhinweis. Lokale `def`-Variablen bleiben normale
Groovy-Variablen und werden nicht in Konfigurationsfelder umgebogen.

### 2.3 Context-Fassade und `ctx.run`

`GroovyContextFacade` bietet die allgemeinen Helfer, `result(String)`,
`resultOptional(String)`, `result(TaskRef)`, `finishJob` und
`run(OperationType, Closure)`. Der Runner stellt dafür den schmalen
Java-Bridge-Typ `OperationCaller` bereit; Groovy-Typen bleiben aus der API und
aus dem Runner heraus.

`ctx.run` startet eine neue Invocation mit eigener Identität, eigenem
Invocation-Workspace und eigener Konfigurationsfassade. Der Rückgabewert ist
das unveränderte Java-Resultat. Ein im Actioncode gefangener Invocationfehler
lässt die Action weiterlaufen; ein nicht gefangener Fehler lässt den Task
scheitern.

### 2.4 ResultView

`ResultView` liest ausschliesslich deklarierte `OutputField`s. Es gibt keine
Bean-/Record-Reflexion und keine automatische Serialisierung. Unbekannte
Ausgänge nennen die verfügbaren Felder. Ein nullable Feld darf `null` liefern;
ein `null` eines nicht-nullable Felds ergibt `NULL_OUTPUT_FIELD`. Zugriffe auf
`.class` und `.metaClass` sind Infrastruktur, keine fachlichen Ausgänge.

### 2.5 Action-, `when`- und Cleanup-Scopes

- Action- und `when`-Closures erhalten eine frische `ActionScope`-Fassade und
  dürfen `result`, `ctx.run`, `finishJob`, Parameter, Pfade, Logging und
  Ressourcen verwenden.
- `when` muss einen `Boolean` liefern; `false` führt zu `SKIPPED` ohne Builder
  oder Handler.
- `CleanupScope` bietet Parameter, Pfade, Config, Logging, Budget und lokale
  Ressourcen, aber keine neuen Operationen, keine Ergebnisse und kein
  `finishJob`.
- Die Scriptbasisklasse routet `println` in den expliziten
  `UserMessageSink`; es ist kein Taskstatus und kein gültiges JSON. Direkte
  fremde `System.out`-Ausgabe wird nicht abgefangen.

### 2.6 Quellenpositionen

Registrierungsfehler und Konfigurationsfehler werden mit der tatsächlichen
Scriptdatei und, soweit im Groovy-Stackframe vorhanden, der Zeile geführt.
Technische Reflectionhüllen werden am Adapterübergang entfernt; die
ursprüngliche Ursache bleibt in der Cause-Kette. Die vollständige Scriptdatei
wird nicht geloggt.

## 3. Ausführbare CORE-040-Referenzprobe

Der Harness liegt in `gretl-groovy/src/test` und verwendet ausschliesslich
lokale Fixture-Operationen:

- `Echo` mit Textfeld, Default und Ergebnisausgang,
- `RequiredText` mit Pflichtfeld,
- `FileSize` mit echtem lokalen Dateipfad,
- `Nullable` mit nullable Ergebnisausgang,
- `VoidProbe` ohne Wert,
- `Fail` für einen echten Handlerfehler.

Die Jobdateien unter `src/test/resources/jobs` werden mit der echten
`GretlScript`-Basisklasse, den echten Map-first-Methoden und der echten
Konfigurationsfassade geladen und im echten `SequentialJobRunner` ausgeführt.
Es gibt keine zweite Runtime und keine nachgebaute Fassade.

CORE-040 ist damit für folgende Punkte belegt:

| CORE-040-Punkt | Nachweis |
|---|---|
| Registrierung ohne Auswertung | Laden zählt 0 Builder, 0 Handler |
| Defaultanwendung | `Echo`-Default `default`, Shadow-Test |
| Assignmentkonversion | GString/`CharSequence` zu festem String |
| Einmaliger Konfigurationslauf | Builder-/Handlerzähler genau 1 |
| Stringresultat und ResultView | Ergebnis-/Action-Tests |
| Null-/einparametrige Closures | explizite Nullform, `when-ctx`, `config-ctx` |
| Neue Invocationpfade bei `ctx.run` | Einzel- und Schleifenaufruf, eigene IDs |
| Fehler bei unbekanntem Feld | Feld, Task, erlaubte Namen, Datei/Zeile |
| Frische Scopes in zwei Definitionen | zwei Ladungen, zwei Läufe |
| Kein Stateübertrag gleichnamiger Tasks | wiederholte Läufe derselben Definition |

## 4. Tests und Nachweise

### 4.1 Ausgeführte Befehle

```sh
./mvnw -o clean verify
./mvnw -o -Pgeotools-probe verify
./mvnw -o -pl gretl-groovy -am test
./mvnw -Pgeotools-probe verify -Dmaven.repo.local=/tmp/m2-fresh-final3
```

### 4.2 Ergebnisse

| Prüfung | Ergebnis |
|---|---|
| `./mvnw -o clean verify` | **197 Tests**, 0 Fehler, `BUILD SUCCESS` |
| `./mvnw -o -Pgeotools-probe verify` | **201 Tests**, `BUILD SUCCESS` |
| `./mvnw -o -pl gretl-groovy -am test` | **181 Tests** (17 API + 115 Runner + 49 Groovy) |
| Gezielte GeoTools-Probe | **21 Tests** (17 API + 4 GeoTools) |
| Frisches lokales Maven-Repository | **201 Tests**, `BUILD SUCCESS` |

Die Standardprüfung umfasst 197 Tests:

| Modul | Tests |
|---|---:|
| `gretl-api` | 17 |
| `gretl-runner` | 115 |
| `gretl-groovy` | 49 |
| `gretl-m0-qualification` | 16 |
| **Standard gesamt** | **197** |

Die 49 Groovy-Tests verteilen sich auf:

| Testklasse | Tests | Schwerpunkt |
|---|---:|---|
| `GroovyLoaderTest` | 13 | Laden, frische Bindings, Symbole, Parameter, Optionen, Cleanup |
| `GroovyTaskConfigurationTest` | 15 | Defaults, Konversion, Arity, Shadowing, `when`, Fehler |
| `GroovyResultAndInvocationTest` | 18 | ResultView, Dateien, `ctx.run`, Cleanup-Verbot, Workspace |
| `GroovySmokeTest` | 1 | kanonisches Map-first-End-to-End |
| `GroovyIsolationTest` | 2 | API/Runner/Adapter-Importgrenzen |

### 4.3 Artefakt- und Grenzprüfung

- `gretl-groovy-0.1.0-M2.jar`: 16 Klassen, Manifest
  `Automatic-Module-Name: ch.so.agi.gretl.runtime.groovy`.
- `jdeps` auf den Adapter: `gretl-api`, `gretl-runner`, Groovy 6.0.0 und
  `java.base`.
- `jdeps` auf den Runner: nur `gretl-api` und `java.base`.
- Keine Gradle-, SLF4J-, Logback-, GeoTools-, INTERLIS-, pgJDBC- oder
  OpenTelemetry-Klassen im Adapter-JAR.
- `GroovyIsolationTest` prüft zusätzlich die Importgrenzen der Produktquellen.
- Die M0-Artefakt-, Bytecode-, Logging-, pgJDBC-, INTERLIS- und
  GeoTools-Nachweise laufen unverändert weiter.

## 5. Abdeckung der Groovy-Abnahmefälle

| Fall | Status | Nachweis |
|---|---|---|
| TC-GR01 Map-first Task/Action/Parameter | **bestanden** | `basic.groovy`, `GroovySmokeTest` |
| TC-GR02 Null-/Ein-/Zweiparameter-Closure | **bestanden** | `explicit-zero`, `config-ctx`, `when-ctx`, `arity`; Originalclosure durch Wiederholungslauf geprüft |
| TC-GR03 DELEGATE_ONLY, Setterfehler, `def`-Shadowing | **bestanden** | `unknownfield`, `shadow`, `reserved`, `unknownmethod` |
| TC-GR04 Defaults, Interpolation, Mehrfachzuweisung | **bestanden** | `config.groovy`, letzte Zuweisung, GString |
| TC-GR05 ResultView, unbekanntes und nullable Feld | **bestanden** | `result`, `unknown-output`, `nullable`, direkte ResultView-Tests |
| TC-GR06 Zwei gleichnamige Jobs/Tasks | **bestanden** | zwei Loads, zwei Läufe, frische IDs/Scopes |
| TC-GR07 Fehlerzeile in Konfiguration | **bestanden** | Fehlermeldung mit `unknownfield.groovy:<Zeile>` |
| TC-GR08 Produkt-`println` und fremdes `System.out` | **bestanden** | `println`, `foreign-output` |

## 6. Abdeckung der relevanten DSL-/Datenfälle

| Fall | Status | Anmerkung |
|---|---|---|
| TC-D02 späte Auswertung | **bestanden** | Laden und Planung ohne Builder/Handler; genau ein Lauf beim Taskstart |
| TC-D03 `when=false` | **bestanden** | Skip ohne Builder/Handler |
| TC-D04 Action-null, Void, Nicht-Void-null | **teilweise/bestanden** | Action-null und `VoidProbe` in M2; Nicht-Void-null über M1-Runner-Tests |
| TC-D05 Ergebnis eines übersprungenen Tasks | **bestanden** | `resultOptional` leer, direkter Zugriff Fehler |
| TC-D06 fremde/fehlende Referenz | **bestanden** | unbekannter Name und fehlende `dependsOn`-Kante |
| TC-D07 Schleife mit `ctx.run` | **bestanden** | zwei Invocations, geordnete Rückgaben |
| TC-D08 unbekanntes Eingabefeld | **bestanden** | Task, Feld, verfügbare Felder, Quelle |
| TC-D09 `return` in Konfigurationsclosure | **bestanden** | kein Skip, Request wird gebaut |
| TC-D13 IDs/Stringliste/`dependsOn` | **bestanden** | TaskRef, Einzelstring und Liste |
| TC-D14 deklarierte Java-Ausgänge | **bestanden** | ResultView ohne Beanreflexion |
| TC-D15 frische Fassade/Helper | **bestanden** | frische Scopes, keine Owner-Fallbacks |
| TC-D16 kein vorgezogener Resultatzugriff | **bestanden** | fehlende Kante scheitert auch nach Vorgänger |
| TC-D17 interpolierte Textwerte | **teilweise** | allgemeine Stringkonversion; HTTP-/SQL-Mapfelder folgen mit M3 |
| TC-D01/D10/D12 | **nicht M2** | HTTP, externes Task-JAR, vollständige MVP-Beispiele |

## 7. Bewusste Grenzen und Restrisiken

- **Keine CLI/Tasks:** Die Runtime lädt und führt nur Fixture-Jobs aus. Es gibt
  keine HTTP-, ZIP-, SQL- oder IliValidator-Operation und keinen Live-SQL-Test.
- **Kein Runreport/Logging:** `println` geht an den expliziten
  `UserMessageSink`; die tasklokale Bindung an Produktlogger, RunEvent oder
  JSONReport folgt in M3. Direkte fremde `System.out`-Ausgabe bleibt
  ungerahmt.
- **Keine vollständigen MVP-Beispiele:** TC-D12 ist nicht erfüllt; die
  Fixture-Jobs ersetzen keine Fachtasks.
- **Kein Grape-Netzwerktest:** Die Konfiguration deaktiviert Grape und wird
  geprüft; ein tatsächlicher `@Grab`-Download wird nicht versucht.
- **Dynamische Sprache:** Nicht jede falsche Methode ist statisch prüfbar.
  Laufzeitfehler werden mit Task, Feld, Quelle und Hilfe geführt; das ist kein
  stilles Fallback.
- **Quellenposition bei Compilerfehlern:** Bei reinen Syntaxfehlern kann die
  Zeile nur aus der Compilermeldung stammen; Laufzeit-Konfigurationsfehler
  tragen die Originalzeile.
- **Packager:** SourceFile-/Zeileninformationen werden noch nicht in ein
  Job-Fat-JAR gepackt.

## 8. Fazit

M2 ist abgeschlossen. Die echte Groovy-6-Ausführung der kanonischen
Map-first-Fassade, die späte Konfigurationsauswertung, `ResultView`, `ctx.run`
und die frische Scope-/Binding-Erzeugung sind implementiert und mit 49
Groovy-Tests belegt. Die Standardprüfung besteht mit 197 Tests, das
GeoTools-Profil mit 201 Tests.

Der nächste Schritt ist **M3**: CLI und Runtimekonfiguration, echte
HTTP-/ZIP-/SQL-/IliValidator-Taskadapter, Produkt-Loggingpipeline mit
Redaction und JSON-/Text-Runreport sowie die Ausführung der vollständigen
MVP-Beispiele. M3 wurde in diesem Auftrag nicht begonnen.
