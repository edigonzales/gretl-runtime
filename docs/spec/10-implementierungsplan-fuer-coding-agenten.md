# 10 — Implementierungsplan für Coding-Agenten

**Version:** Spezifikationsvorschlag 4.1, 18.10.2026.  
**Zweck:** eine kleine, tatsächlich nutzbare Runtime und eine verständliche Autorenoberfläche mit klaren Grenzen implementieren. Dieser Plan ist kein Auftrag, das Runtimeprodukt bereits im Rahmen der Dokumentanalyse zu bauen.

## 1. Auftrag und Liefergrenzen

Die drei Lieferstufen aus [Kapitel 01](01-zielbild-und-mvp.md) werden getrennt behandelt:

- **A:** allgemeiner Runner, anwendergerechte Groovy-Oberfläche, CLI sowie reale HTTP-, Archiv- und SQL-Tasks;
- **B:** reale INTERLIS-Validierung als weiterer gleichberechtigter Task sowie die getrennte technische GeoTools-Kompatibilitätsprobe;
- **C:** optionale Auslieferung eines gewählten Jobs als ausführbares Fat-JAR.

A+B ergeben den beschriebenen GRETL-Script-MVP. C ergänzt eine optionale Paketierungsfähigkeit. **Publisher und XtfToGpkg werden im MVP weder implementiert noch als verfügbare Tasks ausgeliefert.** Die Analysen, strategischen Verträge und allgemeinen Lehren aus dem Publisher bleiben erhalten. Die Implementierung eines neuen Publisher-Adapters ist ein gesonderter Auftrag nach dem MVP; sie ist kein verstecktes Abnahmekriterium von A, B oder C.

Ein Native Image ist ebenfalls keine Aufgabe dieses Plans. Kapitel 08 beschreibt die kleine spätere Entscheidungsgrundlage dafür.

Implementiere zunächst eine funktionierende vertikale Linie. Erweitere sie anschliessend um den nächsten beschriebenen Bedarf. Erforderlich sind verständliche Jobs, eigene Taskbibliotheken und verlässliche Laufzeitwerte. Ein Pub/Sub-System, ein allgemeiner Service-Locator, beliebige Objektcodecs, ein Pluginportal oder eine verpflichtende Workerplattform gehören nicht zum Auftrag.

Ein Coding-Agent darf Routineentscheidungen innerhalb der dokumentierten Grenzen selbst treffen. Ein Wechsel der Sprachsemantik, des Ausführungsmodells oder des Lieferumfangs ist dagegen eine zu dokumentierende Architekturentscheidung. Vertrag, Beispiele und Abnahmekriterien werden dabei gemeinsam aktualisiert.

## 2. Module und Abhängigkeitsrichtung

| Logisches Modul | Verantwortung | Erlaubte Abhängigkeiten |
|---|---|---|
| `gretl-api` | Operations-SPI, Feld-/Ergebnismetadaten, Context, TaskRef, Diagnostic | JDK; keine Gradle-, Groovy- oder Fachbibliotheken. |
| `gretl-runner` | Definition, Parameterauflösung, Plan, sequenzielle Ausführung, Ergebnisse, Fehler, Cleanup | API und notwendige kleine Laufzeitbibliotheken. |
| `gretl-groovy` | Scriptbasisklasse, registrierte Symbole, Anwenderfassade, ResultView, Compilerkonfiguration | API, Runner und ausgewählte Groovy-Module. |
| `gretl-cli` | Argumente, Konfiguration, Job-/Taskhilfe, Pfade, Logs, Report, Einstieg | Runner/Groovy-Adapter; keine konkreten SQL-, INTERLIS- oder Publisher-Typen. |
| Taskbibliotheken in A | HTTP, Archiv und SqlExecutor | API und die jeweils erforderlichen Bibliotheken. |
| Taskbibliothek in B | IliValidator | API und der tatsächlich qualifizierte INTERLIS-Bestand. |
| `gretl-packager` | Optionaler Deskriptor, Kompilation, Maven-Vorlage, Inventar, Einzel-JAR | Gemeinsame Groovy-Compilerkonfiguration und Buildbibliotheken; nur Authoringpfad. |
| Integrationsfixtures | Unabhängige Testoperationen, reale Bibliotheks- und Pakettests | Tatsächlich freizugebende Artefakte; die getrennte GeoTools-Probe besitzt ausdrücklich ausgewiesene zusätzliche Testbibliotheken. |

Die Namen sind Vorschläge für neue Artefakte. Bereits sinnvoll geschnittene vorhandene Module können übernommen werden. Ein Basic-Tasks-Modul kann HTTP und Archiv gemeinsam enthalten; eine einzelne Klasse verlangt nicht automatisch ein eigenes Maven-Modul.

Die Standarddistribution ist eine versionierte Auswahl von Taskbibliotheken. Ihre erste Liste ist keine dauerhafte Obergrenze. Spätere gepflegte Bibliotheken dürfen hinzukommen. Zusätzliche organisationseigene Tasks nutzen dieselbe SPI und dieselbe Joboberfläche. Die Runtime lädt den ausdrücklich bereitgestellten Bestand; sie sucht nicht selbständig im Internet nach unbekannten Symbolen.

Der Core darf nicht vom Publisher abhängen. Auch das Auslassen des Publishers darf keinen Attrappentask, keinen reservierten Spezialpfad und keine Publisher-Prüfung beim Start verursachen. Die optionale Paketierung verwendet denselben Scheduler und denselben Taskkatalogmechanismus wie der Scriptmodus.

## 3. Öffentliche API und Metadaten

Die verbindlichen Java-Signaturen stehen ausschliesslich in [Kapitel 14](14-java-api-vertraege.md); die Anwender-/Groovy-Oberfläche in [Kapitel 04](04-job-dsl-und-custom-tasks.md). Der Coding-Agent legt sie als kompilierbare Java-API an und prüft ihre Verwendung durch eine separat gebaute kleine Erweiterungsbibliothek.

Zum Anfangsbestand gehören:

- `ConfigBuilder<C>` mit `build()`;
- `OperationHandler<C,R>` mit synchronem `execute(request, context)`;
- `OperationType<C,R>` mit ID, Symbol, Beschreibung, Request-/Resulttyp und den beiden Factories;
- `InputField` für die ausdrücklich angebotenen Konfigurationsfelder, ihre Konversion und ihre Zuweisung an den Builder;
- `OutputField` für ausdrücklich angebotene Ergebnisfelder und ihre Leserfunktion;
- `OperationProvider` mit einer Sammlung vollständiger Definitionen;
- `TaskRef<R>` als opaque Referenz zur Jobdefinition;
- `OperationContext` und `TaskContext` mit den dokumentierten allgemeinen Fähigkeiten;
- `CancellationToken`, kleiner ResourceScope und `Diagnostic`.

Die Metadaten sind Teil einer benutzbaren Taskbibliothek. Sie beschreiben mindestens die in Kapitel 04 festgelegten Namen, Typen, Pflichtangaben, Defaults, Beschreibungen und Beispiele. Die CLI-Hilfe und die Groovy-Fassade verwenden dieselben Definitionen. Es gibt keine gesonderte von Hand gepflegte Liste erlaubter Felder nur für die Dokumentation.

Ein Ergebniszugriff wie `result('download').file` wird durch die Groovy-ResultView und das explizite `OutputField` angeboten. Er beruht nicht auf zufälligem Groovy-Zugriff auf private Java-Recordfelder. Ebenso darf die Feldzuweisung `url = ...` nicht beliebige interne Builderproperties beschreiben. Unbekannte Namen und Kollisionen mit reservierten DSL-Namen ergeben konkrete Fehler.

Die Context-Schnittstellen sind kein Service-Locator. Der Java-Context benötigt keine Groovy-Closure. Der Groovy-Adapter ergänzt die direkte Anwenderoberfläche und die programmierbare Form `ctx.run(Type) { ... }`. Beide verwenden denselben Requestbau und Invoker. Ein Handler erhält weder ein Gradle-Projekt noch eine neue Publikationskoordination.

Die verbindliche interne Rollentrennung, wesentlichen Methoden und Datentypen stehen in [Kapitel 15](15-runner-klassendesign.md). Implementiere JobDefinitionBuilder, ExecutionPlanner, ResultStore, OperationInvoker, SequentialJobRunner und FailureArbiter entlang dieser Übergänge. Private Helper dürfen frei geschnitten werden; keine zweite Architektur neben den beschriebenen Ports erzeugen. `TaskStatus` und `ResultSlot` trennen Status und Wertpräsenz. Typisierter nicht-Void-Handler-null ist ein Fehler, Action-null/Void dagegen Erfolg ohne Wert.

Die Diagnoseoberfläche erlaubt fachliche Details, ohne Codes oder Logseverity zur Runentscheidung zu interpretieren. Die Grössenbegrenzungen aus Kapitel 16 werden implementiert; automatische Dumps beliebiger Request-, Ergebnis- oder Exceptionobjekte sind ausgeschlossen.

### 3.1 Taskentwicklung ohne unnötige Wiederholung

Die Klasse `Fields` aus Kapitel 14 stellt kleine typisierte Metadatenfactories bereit. Das neue FileSize-Providerbeispiel in Kapitel 04 ist eine einzige Quelldatei mit verschachtelten Records, Builder und kleinen Handler. Verwende diesen geprüften kleinen Weg; baue dafür kein Annotationscanning, kein Universal-Beanmapping und keinen Codegenerator.

### 3.2 Logging von Beginn an

`TaskLogger` ist Teil der JDK-SPI. `RunEventSink`, Contextanreicherung, `FailureFormatter`, Redaction und ReportWriter werden hinter den allgemeinen Rollen implementiert. SLF4J/Logback gehören in die konkrete Auslieferungsschicht, nicht in öffentliche Taskparameter. Kapitel 16 legt Felder, Kanäle, Grenzen und Verhalten bei Fehlern fest. Die TC-L-Prüfungen aus Kapitel 09 gehören zur gelieferten Fähigkeit; ein nur textuell ausgegebener Throwable ohne Ursachen-/Scopeprüfung erfüllt sie nicht.

Lies vor M1 die Kapitel 14/15 und vor konkreten Adaptern 16. Die hier bereits erfolgreiche Java-Referenzprüfung ist kein Ersatz für Tests der tatsächlichen Produktmodule. Die echte Groovy-6-Bindungsprobe ist als Teil M2 verpflichtend; blosses Parsen von Codeblöcken genügt nicht.

## 4. Gemeinsame Autorenoberfläche

Die erste Referenz für Anwender ist [Kapitel 13](13-einstieg-fuer-jobautoren.md). Seine normale Form ist:

```groovy
parameter('quelle',
    required: true,
    description: 'Adresse der ZIP-Datei')

task('download', HttpDownload) {
    url = parameter('quelle')
    output = workPath('daten.zip')
}

defaults 'download'
```

Diese Form enthält weder obligatorisches `def` noch `ctx ->`. Mehrere Syntaxvarianten werden nicht als gleichrangige Einstiegsoberflächen dokumentiert. In Taskblöcken ist die kanonische Konfiguration eine Zuweisung mit `=`. Allgemeine Helfer werden mit Klammern aufgerufen. Ergebnisse werden über dokumentierte Properties gelesen. Programmierbare Actions bleiben ein anschliessend erklärtes Angebot für geeignete Spezialfälle.

### 4.1 Taskkonfiguration

Die Groovy-Schicht implementiert einen frischen TaskConfigurationScope pro Aufruf. Sie bindet eine Kopie der registrierten Closure und verwendet die in Kapitel 04 festgelegte deterministische Delegation. Alle öffentlichen Zuweisungen laufen über die InputField-Definitionen. Die festen Helfer `parameter`, `result`, `file`, `path`, `workPath` und die weiteren vereinbarten Kontextfunktionen sind eindeutig aufgelöst.

Ein Task kann weiterhin eine TaskRef für programmatischen Gebrauch liefern. Normale Anwenderjobs verwenden String-IDs und benötigen dieses Wissen nicht. Die ID-Form von `result` verwendet dieselbe aktuelle Jobdefinition, Ergebnisablage und Abhängigkeitsprüfung wie TaskRef.

`dependsOn` akzeptiert die vereinbarte einzelne Referenz oder die Liste mehrerer Referenzen. Es wird keine Abhängigkeit durch das vorzeitige Ausführen einer Konfigurationsclosure erraten. Die Taskkonfiguration bleibt bei `check`, `plan` und Jobhilfe unausgeführt und wird im tatsächlichen Lauf genau einmal beim Taskstart ausgewertet.

### 4.2 Parameter

Alle öffentlichen Jobparameter werden mit Beschreibung deklariert. Die Oberfläche unterstützt `text`, `boolean`, `integer` und `path`, den Pflichtstatus sowie einen optionalen Default. Die verbindlichen Konversionsregeln und Prioritäten stehen in Kapitel 06.

Die Implementierung trennt klar:

1. Registrierung und Validierung der Deklarationen, einschliesslich unbekannter Felder, Defaults und unzulässigem `required: true` zusammen mit `defaultValue`;
2. Einlesen der Stringwerte aus Runtimekonfiguration, Parameterdatei und CLI;
3. Validierung bereitgestellter Werte und gemeinsame Konversion;
4. Prüfung sämtlicher jobweiter Pflichtparameter vor Taskarbeit bei `run`;
5. unveränderlichen Zugriff während des Taskstarts.

Ein optionaler fehlender Parameter ohne Default liefert `null`. Ein Default ersetzt nur Abwesenheit, keinen ausdrücklich leeren Wert. Ein `path`-Parameter wird relativ zum ursprünglichen Startverzeichnis aufgelöst und normalisiert; eine allgemeine Existenzprüfung gehört nicht zur Typkonversion.

Unbekannte Parameter aus CLI oder Parameterdatei sind Fehler. Nicht verwendete gemeinsame Einträge aus `runtimeconfig.params` dürfen entsprechend Kapitel 06 unberücksichtigt bleiben. Diese Unterscheidung wird geprüft; eine gemeinsame Betriebskonfiguration soll nicht jeden Job zu denselben Parametern zwingen.

`check` und `plan` verlangen keine noch fehlenden Pflichtwerte, validieren aber bereits angegebene Werte. Die Jobhilfe registriert die Definition und zeigt ihre Angaben, ohne Parameterwerte aufzulösen oder Tasks auszuführen. Die allgemeine Hilfe lädt keinen Job. Die Einschränkung beliebigen vertrauenswürdigen Groovy-Toplevelcodes bleibt in allen betreffenden Befehlen offen dokumentiert.

### 4.3 Hilfe und Fehlermeldungen

`gretl operations` zeigt vorhandene Taskarten. `gretl operations <Symbol>` zeigt deren beschriebene Felder, Defaults, Ergebnisfelder und Beispiel. `gretl run job.groovy --help` zeigt Parameter und Ziele dieses Jobs. Ein unbekannter Typ darf nicht als leerer Platzhalter erscheinen.

DSL-Fehler nennen nach Möglichkeit Jobdatei, Zeile, Task, betroffenes Feld und eine sinnvolle Korrekturhilfe. Die Standardausgabe beginnt nicht mit einem unaufbereiteten Groovy-Stacktrace. Die originale technische Ursache bleibt für weitergehende Diagnose erhalten. Geheimnisse werden weder in Vorschlägen noch in automatisch dargestellten Werten ausgegeben.

Vollständige Laufzeitvalidierung darf nicht durch das vorgezogene Ausführen der Taskclosures simuliert werden. Wo Werte erst später entstehen, benennt der Check diese Grenze.

## 5. Dateiformate und verpflichtende Beispiele

Die Implementierung liefert maschinenprüfbare JSON-Schemata beziehungsweise äquivalente Parservalidierung für Runtimekonfiguration, Stringparametermap und Runbericht. Doppelte JSON-Schlüssel, unbekannte Corefelder und die CLI-Vorrangregeln werden nach Kapitel 06 behandelt. Stufe C ergänzt Paketdeskriptor, eingebettete Jobmetadaten und Dependencyinventar.

Ein coreseitiges Publikationsmanifest-, Receipt-, Delivery- oder Store-Schema gehört nicht dazu. Ein späteres fachliches Publisherformat bleibt in dessen eigener Bibliothek.

Der Beispielbestand hat zwei klar beschriebene Zielgruppen:

| Beispiel | Zweck |
|---|---|
| Einfacher SQL-Job mit lokaler Testdatenbank | Anwender: vorhandenen Baustein konfigurieren und SQL-Ressourcen zuordnen. |
| Download und Extract mit Parametern | Anwender: Eingaben, Pfade, Vorgänger und dokumentierte Ergebnisse. |
| Lokale INTERLIS-Validierung in B | Anwender: reale Fachoperation mit Testtransfer und lokalen Modellen. |
| Lokaler Token-/Katalogfixture | Entwickler: komplexere Laufzeitwerte ohne `doFirst`-/`doLast` prüfen. |
| Action mit Nacharbeit und No-data-Fall | Entwickler: normale Ausführungs- und Fehlersemantik prüfen. |
| Separat gebauter FileSize-Task als Erweiterungs-JAR | Entwickler: dieselbe SPI und Anwenderfassade ohne Runnerneubuild verwenden. |
| Ein vorhandener Job als JAR in C | Gleiche Semantik, Hilfe, Parameter und Pfade im optionalen Paketmodus. |

Token-Parsing oder das Schreiben eines OperationProvider werden nicht zur normalen Jobpflege erklärt. Der separat gebaute FileSize-Task ist ein ausdrücklich mitgeliefertes Entwicklerfixture; er wird dadurch nicht still Teil des zugesagten Standardkatalogs. Ein Publisher-Beispiel darf erst im späteren Publisher-Auftrag als ausführbar bezeichnet werden.

## 6. Meilensteine

### M0 — Tatsächlichen Anfangsbestand qualifizieren

**Ziel:** eine belastbare kleine Ausgangslage, ohne spätere Fachmodule vorwegzunehmen.

Arbeit:

- Bestehende benötigte Quellen und Abhängigkeiten an festen Ständen erfassen; keine ungeprüften SNAPSHOTs übernehmen.
- JDK 25 und die Ausgangsversionen aus Kapitel 07 am Implementierungsdatum prüfen und den tatsächlich verwendeten Bestand festlegen.
- Maven Wrapper, einen Root-Parent/Aggregator und nur die aktuell benötigten Module nach BLD-001 bis BLD-004 anlegen. Maven-Standardlayout, zentrale Versionen und pro Modul ausdrücklich benötigte Dependencies verwenden; keine Parent-Hierarchie oder leeren Zukunftsmodule.
- Eine kurze Repository-Buildanleitung mit Standardaufruf, bewusst aktivierten Zusatzprofilen und Pluginzwecken erstellen. Compiler-/Testbindungen und Dependencybestand anhand Effective POM und aufgelöstem Dependencybaum prüfen; keine Groovy-Buildintegration allein für Job-Testressourcen hinzufügen.
- Eine kleine Groovy-Probe mit eigener Basisklasse und eine unabhängig gebaute Java-Operation kompilieren und starten.
- Die für A sowie die reale Validatorintegration für B tatsächlich verwendeten Bibliotheken auf erforderliche ABI-/Ladefähigkeit prüfen.
- Eine kleine GeoTools-Kompatibilitätsprobe in einem getrennten Testprofil mit derselben JDK-/Groovy-Version und dem qualifizierten Runtimebestand ausführen. Ihre zusätzlichen GeoTools-Bibliotheken und Versionen ausdrücklich ausweisen.

**Nachweis:** Versions- und Artefaktprotokoll sowie Ergebnis der kleinen Proben. Ein GroovyShell-Start allein ist keine komplette Bibliotheksqualifikation. Publisher und XtfToGpkg werden für M0 nicht gebaut. Die getrennte GeoTools-Probe ist ein verbindlicher technischer Nachweis für die gewählten Ausgangsversionen. Sie ist kein öffentlicher Task, kein Beleg für einen vorhandenen Publisher-/Konverterport und kein Grund, ausschliesslich testseitig benötigte GeoTools-Bibliotheken in die Standarddistribution aufzunehmen.

**Zusatznachweis M0:** die API aus Kapitel 14 auf der tatsächlichen Java-25-Toolchain bauen; separat gebautes Provider-JAR verwenden; Loggingprovider-/Bridgebestand inventarisieren. Ein mit JDK 21 gelungener Dokumentreferenztest ist Vorarbeit, nicht M0-Abnahme. BLD-001 bis BLD-008 und die für den Anfangsbestand anwendbaren TC-BLD-Fälle werden mit geprüft. Distributions-/Packagerfälle werden erst in M3 beziehungsweise M6 ausführbar abgenommen; es wird für M0 kein zusätzlicher Zukunftsbuild gebaut.

### M1 — Java-API und sequenziellen Runner implementieren

**Ziel:** den allgemeinen Ablauf unabhängig von Groovy und Fachbibliotheken prüfen.

Arbeit:

- Taskregistrierung, Referenzen, Zielhülle, Zyklusprüfung, `after` und stabile Reihenfolge implementieren.
- Einmalige Ausführung, Ergebnispräsenz und Besitz, normale Exceptions und Fail-fast implementieren.
- Skip, NO_DATA, kontrollierte Cancellation, ResourceScope und Job-Cleanup ergänzen.
- Feld-/Ergebnismetadaten in der API und eine unabhängige Beispieloperation bereitstellen.
- Ereignisse und Outcomes als interne Objekte abbilden; keine fachliche Zustandsregistry und kein Persistenz-/Resume-System einführen.

**Nachweis:** Die passenden Runtime-, Ergebnis- und Fehlerfälle aus Kapitel 09 bestehen. Bewusst behandelte Fachfehler verhalten sich wie gewöhnliche Programmfehler. Der Core läuft ohne Publisher, GeoTools und INTERLIS-Bibliotheken.

**Zusatznachweis M1:** interne Daten-/Methodenrollen und Übergänge gemäss Kapitel 15; Ergebnisstore ohne Statusverlust, feste Fehlervorrangregeln, Contextzustände und automatische Lifecycleereignisse prüfen. Die Log-/Fehler-ID ist kein Fachstatus.

### M2 — Anwenderfassade, Parameter und Laufzeitwerte implementieren

**Ziel:** die einfache dokumentierte DSL mit derselben semantischen Verlässlichkeit wie die Java-API anbieten.

Arbeit:

- Gemeinsame Compilerkonfiguration, Scriptbasisklasse und frisches Binding aufbauen; Grape deaktivieren.
- Map-first-Überladungen für die beschriebenen Task- und Parameteraufrufe implementieren.
- Den TaskConfigurationScope mit `=`-Zuweisungen, reservierten Helfern und konkreten Feldfehlern implementieren.
- ResultView aus beschriebenen OutputFields und sichere String-ID-Referenzen ergänzen.
- Parameterdeklaration, Defaults, Typen, Quellenpriorität und frühe Pflichtprüfung implementieren.
- `task`, `action`, `defaults`, `cleanup`, Bedingungen, explizite Abhängigkeiten und synchrones `ctx.run` auf denselben Invoker führen.
- Quell-/Zeileninformationen erhalten; keine automatische Rekursion in beliebige verschachtelte Builderclosures einführen.

**Nachweis:** Die vollständigen DSL-Beispiele laufen als echte Groovy-Scripts. Dynamische Werte werden erst nach ihren Produzenten gelesen. Plan und Check führen keine Taskclosure aus. Ein Scriptwechsel funktioniert mit derselben gebauten Runtime. Unbekannte Felder, Helperkollisionen und unzulässige Ergebniszugriffe erhalten die beschriebenen Meldungen.

**Zusatznachweis M2:** den echten Groovy-Harness CORE-040 ausführen: Map-first, rehydrate/DELEGATE_ONLY, Arity 0/1, Propertyfehler, ResultView, innere Konfigurationsfehler und Quellenzeilen. `println` über die Produktfassade darf maschinenlesbare Nutzkanäle nicht vermischen. Die unveränderte Anwendergrundform muss getestet werden.

### M3 — CLI, allgemeine Tasks und Stufe A technisch abnehmen

**Ziel:** einen technisch abgenommenen A-Kandidaten mit einer verständlichen Standarddistribution bereitstellen.

Arbeit:

- CLI, Runtimekonfiguration, Pfade, Secrets, Workspace und Runbericht aus Kapitel 06 implementieren.
- Globale Hilfe vor Job-/Providerinitialisierung beantworten; Taskhilfe aus Operationmetadaten und Jobhilfe aus Definitionen erzeugen.
- HTTP, Archiv und den begrenzten SqlExecutor mit echten Bibliotheken umsetzen.
- Die vereinbarten Defaultlimits, Felddeskriptoren und vollständigen Beispiele mit den Adaptern liefern.
- Java-Serviceprovider und einen expliziten Erweiterungsbereich einrichten; das separat gebaute Beispiel-JAR laden.
- Die Scriptdistribution einschliesslich Inventar, Testdateien und Aufrufbeispielen bauen.

**Technischer Nachweis:** Die für A zugeordneten technischen Prüfungen aus Kapitel 09 bestehen. Ein unvollständiger oder unbekannter Jobparameter scheitert vor fachlichen Nebenwirkungen. Die Bausteinhilfe und die tatsächlich akzeptierten Konfigurationsfelder stimmen überein. Der lokale Token-/Katalogfixture belegt komplexere Laufzeitwerte; er bleibt ein Entwicklerbeispiel.

Die öffentliche Anwenderfreigabe verlangt zusätzlich die kurze Prüfung TC-U01 bis TC-U04. Wird A bereits vor B als Produkt ausgeliefert, wird diese in M5 beschriebene Prüfung vor den Abschluss von M3 gezogen. Andernfalls wird M3 als technisch abgenommener Kandidat bezeichnet und die gemeinsame Anwenderfreigabe folgt in M5. Es handelt sich um denselben Nachweis, nicht um eine zusätzliche zweite Anwenderabnahme.

**Zusatznachweis M3:** Text/JSONL, Redaction, Cause-/Suppressedgraph, Scope-/MDC-Restoration, HTTP-/SQL-Details, Datei-/Recordlimits und sink.degraded gegen das gebaute A-Paket prüfen. Keine verpflichtende OTel- oder Kubernetesinstallation.

### M4 — IliValidator und Stufe B integrieren

**Ziel:** eine wichtige reale INTERLIS-Fachoperation durch dieselbe SPI bereitstellen.

Arbeit:

- Die echte Validatorbibliothek mit lokalen Modellen integrieren.
- Unterstützte Einstellungen, Defaults, Diagnosen und Ergebnisfelder beschreiben.
- Gültige und ungültige Testtransfers ausführen; Ressourcenfreigabe und zugängliche Validierungsberichte prüfen.
- Den gemeinsam ausgelieferten Bibliotheksbestand qualifizieren; konkrete Konflikte an der kleinsten passenden Grenze lösen.
- Die getrennte GeoTools-Kompatibilitätsprobe mit dem für B gewählten Versionsbestand ausführen und ihre zusätzlichen Testdependencies im Bericht getrennt nennen.

**Nachweis:** Die für B geltenden Fälle aus Kapitel 09 und 07 bestehen. Dabei werden tatsächliche Validator-/Distributionsnachweise und die getrennte GeoTools-Kompatibilitätsprobe ausdrücklich unterschieden. Die Bausteinliste und -hilfe zeigen den echten IliValidator; die GeoTools-Probe erscheint dort nicht als zusätzlicher Task. A bleibt als eigenständige kleinere Distribution nutzbar.

Publisher, seine Gradle-Fassade und die Erzeugung seiner bisherigen Verzeichnisstruktur sind keine Arbeiten von M4. XtfToGpkg wird ebenfalls nicht als vermeintliche Validator-Nebenfunktion mit ausgeliefert. Die vier zugesagten Standardtasks benötigen nach ihren MVP-Verträgen keine GeoTools-Bibliotheken; diese werden nicht allein für die getrennte Probe in die Distribution gelegt. Die in Kapitel 02 und 05 erhaltenen Analysen werden nicht entfernt oder durch pauschale spätere TODOs ersetzt.

**Zusatznachweis M4:** echte EHI-/Iox-Quelle mit vorhandener Throwable-Ursache prüfen, Feldzuordnung und begrenzte Detailbeispiele testen, Listener nach success/failure exakt freigeben. Fehlertexte dürfen kein ungeprüftes Credential-/Nutzdatendump sein.

### M5 — Script-MVP und Anwenderoberfläche freigeben

**Ziel:** A+B als verständlich verwendbaren Stand bereitstellen.

Arbeit:

- Fertige Distribution und Inventar gegen die vorgesehenen Releaseprüfungen testen.
- Die Beispiele aus Kapitel 13 mit der gebauten Distribution ausführen; Testdateien, Datenbankkonfiguration und lokale Testserver entsprechend liefern.
- Mit einer kleinen Zahl tatsächlicher Fachanwender die in Kapitel 09/13 genannten Pflegeaufgaben beobachten.
- Missverständnisse und Fehlermeldungen korrigieren, ohne unbelegt eine zweite Sprache oder einen GUI-Editor einzuführen.
- Verfügbare und zukünftige Taskarten eindeutig ausweisen; eine später mögliche Publisherintegration nicht als gelieferte Fähigkeit darstellen.
- Einen geeigneten Pilotjob und seine verbleibenden fachlichen Bedingungen dokumentieren. Eine lokale Probe ist keine automatische Freigabe produktiver Umstellungen.

**Nachweis:** Eine weitere Person kann die beschriebenen Beispiele mit dem gelieferten Paket ausführen. Fachanwender können die vereinbarten einfachen Änderungen anhand der Anleitung und Meldungen vornehmen; die Beobachtung und gegebenenfalls verbleibende Hürden sind protokolliert. Die Entwicklerprobe zeigt, dass eine neue Taskbibliothek ohne Runnerneubuild eingebunden werden kann.

Diese Anwenderprüfung ist eine noch zu erfüllende Abnahme. Dokumentation oder ein möglicher späterer LSP dürfen nicht als Beweis ihrer bereits erfolgreichen Durchführung ausgegeben werden.

**Zusatznachweis M5:** ein absichtlich fehlerhaftes Rezept anhand der menschenlesbaren Ausgabe, Taskname und Fehler-ID untersuchen lassen. Tatsächliche Beobachtungen protokollieren; eine von der KI gelesene Anleitung ist keine Fachanwenderstudie.

### M6 — Optionalen Job-Fat-JAR-Packager und Stufe C ausliefern

**Ziel:** einen gewählten Jobstand mit seinen benötigten Bausteinen als ausführbares JAR bereitstellen.

Arbeit:

- Paketdeskriptor und Ressourcenregeln aus Kapitel 12 implementieren.
- Dieselbe Groovy-Compilerkonfiguration verwenden; Script und Helfer vorkompilieren, ohne `Script.run`, `check` oder `plan` zum Ermitteln des Paketinhalts auszuführen.
- Die versionierte Maven-Shade-Vorlage mit erforderlichen Merges, Kollisionsprüfung und Dependencyinventar bauen. BLD-006/007 beachten: normale testbare Packagerklassen, keine vererbte Shade-Ausführung in API-/Taskbibliotheken und kein zusätzliches eigenes Maven-Plugin ohne konkreten Vereinfachungsnachweis.
- Gemeinsamen Einstieg, Ressourcenmaterialisierung, Jobhilfe und dieselben Parameter-/Pfadregeln implementieren.
- `gretl package` als dünnen Einstieg in das getrennte Authoringmodul anbieten.

**Nachweis:** Die Fälle von Stufe C werden am tatsächlich erzeugten JAR geprüft. Es startet mit Java allein, verwendet andere Laufzeitparameter korrekt, benötigt keine weiteren Taskbibliotheken auf dem Zielrechner und stellt dieselbe Jobhilfe bereit. Pflichtparameter werden bei normalem Lauf vor Facharbeit geprüft; fehlende Eingabewerte verhindern die reine Hilfe nicht.

Die Paketabnahme umfasst die vereinbarten SQL- und INTERLIS-Fälle aus dem real gelieferten A-/B-Bestand. Publisher ist dafür keine Voraussetzung. Ein zusätzliches testseitiges GeoTools-JAR-Fixture ist als Vorbereitung zulässig, wird aber nicht zur Voraussetzung für C. Die wirklichen Distributions-/JAR-Servicegates für GeoTools werden erst verbindlich, wenn eine darauf beruhende Fachfähigkeit tatsächlich angeboten wird. Beim Packen läuft keine ETL; beim Start läuft kein Paketbuild. Ein späteres Native-Experiment mit genau einem geeigneten Job ist ein gesonderter, begrenzter Auftrag.

**Zusatznachweis M6:** genau ein Loggingprovider im fertigen JAR, notwendige Service-/Groovy-/Bridge-Ressourcen, Fehlerquellenzeilen und Secretfilter beibehalten. Dieselbe Logkonfiguration und dieselben Runberichte wie im Scriptmodus; keine Facharbeit beim Packen.

## 7. Build- und Prüfablauf

Die lokale Standardprüfung lautet `./mvnw verify`. Sie umfasst kompakte API-, Runtime-, DSL- und Metadatentests sowie die ohne weitere Infrastruktur ausführbaren Beispiele. Konkrete weitere Profile und deren Namen werden im Repository festgelegt. Verbindlich ist die Maven-Leitlinie BLD-001 bis BLD-008 in [07, Abschnitt 6.4](07-plattform-und-versionen.md#64-verbindliche-leitlinie-konventioneller-maven-produktbuild): normale Lifecyclebindungen, ein eigener Parent, kein zweiter Produktbuildweg und keine versteckte CI-/POM-Programmierung.

Die Buildanleitung nennt für jedes wirklich vorhandene Zusatzprofil Zweck, Aufruf, Voraussetzungen und zugehörige Lieferstufe. Maven-Pluginversionen sind fixiert; zusätzliche Ausführungen gelten nur für die benötigten Module. CI ruft dieselben dokumentierten Befehle auf. Ein Infrastrukturprofil muss bis zur Fehlerauswertung in `verify` laufen; fehlende Voraussetzungen dürfen die notwendige Freigabe nicht still passieren lassen. Ein begründetes neues Plugin ist zulässig, ein allgemeines selbst entwickeltes Buildframework nicht.

Die Releasepipeline führt zusätzlich die benötigten realen Taskintegrationen mit lokalem HTTP-Fixture, PostgreSQL und dem zugesagten INTERLIS-Bestand aus. Ein nicht verfügbares Fixture darf nicht als bestandene Integration erscheinen. Das gesonderte GeoTools-Kompatibilitätsprofil gehört zu den technischen Nachweisen von M0/B; es verwendet denselben JDK-/Groovy-/Runtimebestand und nennt seine zusätzlichen Testbibliotheken ausdrücklich. Es beweist weder eine öffentliche GeoTools-Fachfunktion noch deren korrekte Auslieferung. Erst eine später tatsächlich angebotene GeoTools-Fähigkeit benötigt die entsprechenden Provider- und Ressourcenprüfungen gegen ihr fertiges Distributions- beziehungsweise Job-JAR-Artefakt.

Für C kommen die gezielten Endproduktprüfungen hinzu. Ein JAR wird aus einem frischen Prozess und einem anderen Verzeichnis gestartet. Tests nur gegen den vollständigen Maven-Testclasspath können fehlende Ressourcen oder Dependencies verdecken und genügen nicht.

Dokumentationsbeispiele sind prüfbare Vertragsbeispiele. Die Implementierung hält möglichst eine gemeinsame ausführbare Quelle für Beispiel und Prüfung vor oder prüft die extrahierten vollständigen Snippets. Eine zweite händisch korrigierte Testvariante darf Fehler im veröffentlichten Beispiel nicht verdecken. Erklärungsausschnitte werden als solche gekennzeichnet und nicht als eigenständige Jobs behauptet.

Die Abnahmefälle aus [Kapitel 09](09-tests-und-abnahme.md) sind nach ihrer Lieferstufe massgeblich. Zukünftige Publisherfälle bleiben fachlicher Wissensbestand für dessen eigenen Auftrag und werden bei der MVP-Freigabe weder als bestanden noch als fehlender MVP-Test gezählt.

## 8. Definition of Done

Ein Meilenstein ist fertig, wenn seine Verträge implementiert sind, seine vollständigen Beispiele ausführbar sind, seine relevanten Abnahmekriterien bestanden sind und seine Grenzen dokumentiert sind. Ein Placeholderhandler, ein nur als Symbol registrierter Baustein oder ein Erfolg ohne fachliche Wirkung ist kein Nachweis einer gelieferten Fähigkeit.

APIänderungen aktualisieren zusammengehörig Kapitelvertrag, Metadaten, Hilfe, Beispiele und Tests. Der Abschlussbericht trennt implementierten Bestand, tatsächlich ausgeführte technische Prüfungen, beobachtete Anwenderaufgaben und verbleibende Arbeit. Ein schmalerer Nachweis rechtfertigt keine Behauptung vollständiger Jobmigration, transaktionaler Gesamtwirkung oder Native-Kompatibilität.

Besonders zu vermeiden sind:

- zusätzliche Parent-Hierarchien, parallele Produktbuildwege und ein eigenes Convention-/Lifecycle-Framework ohne konkreten Bedarf;
- unbemerkte Profilaktivierung durch private Rechnerzustände, globale Fachdependencies im Parent und lange eingebettete Buildscripts;
- Publisher-Fachzustände oder Spezialbefehle im Core;
- Publisher- oder XtfToGpkg-Implementierung als versteckte MVP-Voraussetzung;
- normale Anwenderjobs, die TaskRef, Contextargumente oder Java-Imports kennen müssen;
- freie unbekannte Konfigurationsproperties statt beschriebener InputFields;
- Ergebniszugriff über zufällige Reflexion statt ausdrücklich angebotener OutputFields;
- automatische Wiederholung nach unbekannten Effekten oder ein globaler Fehlerstatus aus jeder ERROR-Diagnose;
- Grape und Dependencyauflösung beim Jobstart;
- Ausführen des Jobs beim Packen;
- unterschiedliche Parameter- oder Pfadsemantik im Script und JAR;
- vorweggenommene LSP-/GUI-Zusagen oder eine behauptete Anwenderabnahme ohne Beteiligte.

Die allgemeinen Erkenntnisse des Publisheraudits bleiben ausdrücklich erhalten: komplexe Fachoperationen benötigen brauchbare Laufzeitwerte, Ergebnisse, normale Fehlersemantik und geregelten Ressourcenbesitz. Ihr besonderer fachlicher Ablauf bestimmt weiterhin nicht die Architektur des allgemeinen Runners.
