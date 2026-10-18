# 07 — Plattform, Versionen und Abhängigkeiten

**Spezifikation 4.1, 18. Oktober 2026; unveränderter Stand der Versionsrecherche: 17. Oktober 2026.** Dieses Kapitel legt die Ausgangskombination und deren Prüfauftrag fest. Die Veröffentlichung einer Bibliotheksversion beweist nicht, dass die vollständige GRETL-Kombination bereits funktioniert. Für diese Spezifikation wurde keine Laufzeitqualifikation durchgeführt. Ein gemeinsamer JVM-Prozess bleibt der Standard; separate Worker sind keine Voraussetzung. Die Revision 3.0 grenzt den tatsächlichen MVP-Taskbestand und die spätere Publisher-Lieferung deutlicher ab.

Die Begriffe **MUSS**, **DARF NICHT** und **SOLL** sind normativ. Ein belegtes Bibliotheksproblem kann eine dokumentierte Änderung der Baseline begründen. Es darf nicht durch einen unbemerkten Versionswechsel oder eine unqualifizierte Mischung von Bibliotheken verdeckt werden.

## Ergänzung 4.0: API-Referenz und Loggingbestand

Die Produktbasis bleibt Java 25 mit der tatsächlich qualifizierten Groovy-/Fachbibliothekskombination. Die API-Snippets aus Kapitel 14 wurden mit dem hier verfügbaren **JDK 21** rein syntaktisch/typseitig geprüft. Daraus wird weder ein Java-21-Produktziel noch eine erledigte Java-25-/Groovy-6-Qualifikation abgeleitet.

M0 qualifiziert zusätzlich genau einen SLF4J-2-kompatiblen Provider mit einem passenden aktuellen Logback-Release und den wirklich benötigten Brücken. Exakte Patchversionen und Transitives werden bei der Umsetzung geprüft und inventarisiert; es wird hier keine ungetestete gemeinsame Patchkombination als freigegeben ausgegeben. Mehrere Provider oder gegenläufige JUL-/SLF4J-Brücken sind ein Fehler des Distributionsbestands. Die öffentliche API bleibt dank TaskLogger JDK-only.

Eine Datenbank-/INTERLIS-/GeoTools-Probe umfasst auch ihre verantworteten Logpfade, ServiceLoader-Ressourcen und echte Fehlerursachen. Eine erfolgreiche Klassenladung oder eine leere Testdatei beweist dies nicht. Native- oder OTel-Agenten werden nicht in M0 eingeschleust. Die aktuellen Quellen für Loggingstandards stehen in Kapitel 16, die lokal tatsächlich durchgeführten Referenzprüfungen in Kapitel 17.

## 1. Plattformentscheidung

Der Runner verwendet **Java 25 LTS** und **Groovy 6.0.0**. Für die gesonderte technische Kompatibilitätsprobe und später tatsächlich angebotene GeoTools-Funktionen ist zunächst **GeoTools 35.2** vorgesehen. Der Produktbuild verwendet **Maven 3.10.0 mit Maven Wrapper**. Die Runtime führt im Standardfall einen Job pro Prozess und dessen Tasks sequenziell in derselben JVM aus.

Diese Entscheidung verbindet aktuelle Sprach- und Laufzeitversionen mit einem einfachen Autorenworkflow. Eine Groovy-Datei bleibt direkt ausführbar. Bei Bedarf erzeugt die optionale Paketierung ein ausführbares Job-JAR; dessen Build übersetzt den Job automatisch. Die Paketierung verändert weder die Laufzeitparameter noch die Bedeutung verzögert ausgewerteter Taskkonfigurationen. Der Paketvertrag steht in [12 — Job-Paketierung](12-job-paketierung.md); die allgemeine Ausführung in [06](06-betrieb-und-auslieferung.md), die spätere Native-Option in [08](08-native-image-strategie.md).

Die **vom Core definierte Java-SPI** besitzt keine Gradle-, Groovy-, GeoTools-, JTS- oder INTERLIS-Abhängigkeiten. Das Groovy-Integrationsmodul und die jeweiligen Custom-Task-Bibliotheken tragen ihre eigenen Abhängigkeiten. Diese Modulgrenze erzwingt keine Prozessgrenze und keine JSON-Transportobjekte für sämtliche Tasks.

### 1.1 Verbindliche Ausgangskandidaten

| Bestandteil | Ausgangskandidat am 17.10.2026 | Rolle |
|---|---|---|
| JVM | Eclipse Temurin **25.0.4.1+1** | Gemeinsame Entwicklungs- und Produktionslinie. |
| Groovy | Apache Groovy **6.0.0** | Eingebetteter Compiler und Runtime für Jobskripte; dieselbe Version für paketierte Jobs. |
| GeoTools | **35.2** | Gemeinsam verwalteter Stand der `gt-*`-Module in der vorbereitenden Testprobe; Laufzeitabhängigkeit erst bei tatsächlichem Bedarf eines gelieferten Tasks. |
| Produktbuild | Apache Maven **3.10.0**, über Maven Wrapper | Erstellung, Prüfung und Paketierung des Produkts. |
| Produktmodule einschliesslich Core-SPI und Taskadapter | JDK-25-Toolchain, Java-Releaseziel **25** | Eine Bytecode-Baseline für den MVP. |
| Groovy-Jobbytecode | Explizites Compilerziel **25** | Gleiches Ziel beim direkten Laden und beim optionalen Paketieren. |

**Ausgangskandidat** bedeutet: Die Implementierung beginnt mit diesen Versionen und weist die jeweils zugesagte Kombination nach. Laufzeitfunktionen werden im tatsächlich ausgelieferten Artefakt geprüft. Die vorbereitende GeoTools-Probe wird dagegen mit ihrem gesonderten vollständig ausgewiesenen Testbestand geprüft. Ihr Erfolg macht GeoTools weder zur vorhandenen öffentlichen Taskfähigkeit noch zur Pflichtdependency jedes Runtimepakets. Erst der jeweilige Nachweis erlaubt die entsprechende Unterstützungsaussage.

Beim Implementierungsstart MUSS geprüft werden, ob seit dem Recherchestichtag neue stabile Patchstände oder relevante Sicherheitshinweise erschienen sind. Ein neuer Patch wird ausdrücklich gewählt, fixiert und geprüft. `latest`, automatische Major-Upgrades oder Abhängigkeitsauflösung beim Jobstart sind damit nicht erlaubt.

### 1.2 Vorgehen bei einem belegten Konflikt

Zuerst wird der Konflikt mit dem tatsächlich ausgelieferten Klassenpfad reproduziert. Danach wird die kleinste ursächliche Anpassung an der betroffenen Taskbibliothek, ein geeignetes Patchrelease oder ein begrenzter Fallback geprüft.

| Betroffener Teil | Mögliche begrenzte Reaktion | Bedingung |
|---|---|---|
| Groovy 6 | Groovy **5.1.3** oder ein ausdrücklich qualifizierter Patch dieser Linie | Belegtes DSL-, Compiler- oder Interop-Problem; keine stille Änderung der Jobsemantik. |
| GeoTools 35 | Ausdrücklich qualifizierter Stand von GeoTools 34.x | Benötigte Formate und APIs sind mit diesem Stand geprüft. |
| Einzelne Taskbibliothek auf Java 25 oder im gemeinsamen Klassenpfad | Abhängigkeiten korrigieren; erst wenn nötig eine taskinterne separate JVM | Konkrete Unverträglichkeit, dokumentierter Zusatzaufwand und gezielte Abnahme. Kein allgemeines Workerframework als Voraussetzung. |
| Optionaler nativer Job | Dasselbe Jobpaket auf der JVM ausliefern | Native-Unterstützung ist keine Voraussetzung der fachlichen Freigabe. |

Der in `gretl-next` vorhandene Stand **GeoTools 34.2** ist eine analysierte IST-Version. Er ist kein automatisch qualifizierter Fallback. Am Stichtag ist auch **34.6** veröffentlicht; die benötigten Fähigkeiten wären damit gesondert zu prüfen. [T4], [R3]

Mehrere vollständige Produktvarianten werden im MVP nicht vorsorglich parallel gepflegt. Ein tatsächlich verwendeter Fallback erhält den auslösenden Test, eine begründete Versionsentscheidung und Kriterien für seine spätere Entfernung.

## 2. Begründung und Qualität der Versionsbelege

### 2.1 Java: aktuelles LTS als Produktionslinie

Oracle klassifiziert Java 25 als LTS und Java 27 als Non-LTS. Adoptium weist für Temurin 25 eine Verfügbarkeit mindestens bis September 2031 aus. Diese Projektangabe ersetzt keinen kommerziellen Supportvertrag. [J1], [J2]

**25.0.4.1+1** ist am 17. Oktober 2026 die nachgewiesene aktuelle Patchversion der 25er-Linie. Oracle datiert die Veröffentlichung auf den 18. August; das Temurin-Artefakt wurde am 19. August veröffentlicht. Die nächste reguläre Aktualisierung ist für den **20. Oktober 2026** vorgesehen. **25.0.5 wird deshalb nicht als bereits verfügbare Version behandelt.** [J3], [J4]

Java 27 kann später in einer zusätzlichen Kompatibilitätsprüfung laufen, um kommende Probleme sichtbar zu machen. Dies ist kein MVP-Releasegate. Produkt und öffentliche API DÜRFEN im MVP keine Java-Preview- oder Incubator-Funktion voraussetzen.

### 2.2 Groovy 6: veröffentlichte Version trotz widersprüchlicher Downloadtexte

Die Downloadseite bietet 6.0.0 an, bezeichnet die Linie an anderen Stellen aber noch als nächste Version und nennt zugleich 5.1 als neueste stabile Linie. Die Einordnung als veröffentlichtes Release stützt sich deshalb auf stärkere Belege:

- Das offizielle Changelog nennt **Groovy 6.0.0 mit Veröffentlichungsdatum 22.09.2026** und Groovy 5.1.3 mit Datum 19.09.2026.
- Das Apache-Archiv enthält Distribution und Quellen von 6.0.0, datiert auf den 22.09.2026.
- Der annotierte Tag `GROOVY_6_0_0` und die Versionsdatei bestätigen 6.0.0. Der zugehörige Commit lautet `bc6b3cfd9f8f3397129f8ebbdf85a189d16d7360`. [G1], [G2], [G3], [G6]

Groovy 6 verlangt mindestens JDK 17 und dokumentiert Tests auf JDK 17 bis 27. Die GRETL-DSL verwendet die etablierten Mechanismen für Scripts, Closures, Delegation und Java-Interop. Sie DARF NICHT von den neuen inkubierenden Async-, Makro- oder Native-Funktionen abhängen. [G4]

Groovy 5.1 dokumentiert Tests auf JDK 11 bis 25 sowie Vorabversionen von 26 und 27. Dies begründet den möglichen Fallback bei einem konkreten Problem mit Groovy 6. GeoTools erzwingt diese Rückstufung nicht. [G5]

### 2.3 GeoTools: Mindest-Java und tatsächlicher Releasecode

Das stabile GeoTools-Handbuch gehört am Stichtag zu 35.x. Sein Java-Kapitel nennt Java 17 als Minimum und beschreibt Pull-Request-Tests auf 17 und 21. [T1]

Im **Release-Tag 35.2** existiert zusätzlich `.github/workflows/linux_jdk25.yml`. Der Workflow richtet Temurin 25 ein und konfiguriert einen Maven-Build mit `clean install` und `-Dall`. Der Parent-POM verwendet weiterhin `release=17`. [T2], [T3]

GeoTools 35.2 hat damit eine ausdrückliche Upstream-CI-Konfiguration für Java 25. Sein eigener Bytecode verlangt lediglich Java 17. Die vorhandene CI-Datei beweist weder jeden erfolgreich abgeschlossenen Upstream-Lauf noch die Kompatibilität mit dem zusätzlichen GRETL-Bibliotheksbestand.

GeoTools 35.2 wurde am **16.10.2026** als reguläres Release veröffentlicht. Die Auswahl beruht auf diesem Release und nicht auf einer erwarteten Veröffentlichung. [T4]

**Konsequenz:** GeoTools ist ein wesentlicher Migrationsfaktor, begründet nach den geprüften Quellen aber keine Beschränkung des Runners auf Java 21. Seine APIs, Ressourcen, Provider und transitiven Abhängigkeiten müssen mit der tatsächlich gewählten Integration geprüft werden.

## 3. IST-Baselines und notwendige Aktualisierung

### 3.1 Untersuchte GRETL-Stände

| Quelle | Beobachteter Stand | Bedeutung |
|---|---|---|
| `sogis/gretl`, Commit `a9222dc0ffd08f8f76570e998101db041822df71` | Java-Releaseziel 11; Jackson 2.10.0; ili2c 5.6.8; iox-ili 1.24.4; ilivalidator 1.15.0; ili2db 5.5.1; iox-wkf 2.0.1-SNAPSHOT | Referenz für bestehendes Verhalten und Abhängigkeiten; keine neue Freigabeliste. |
| `gretl-next`, Commit `08bd11e97a54f32f8702d34ff16fb39bec6ea046` | Java-17-Toolchain und Releaseziel 17; GeoTools 34.2; Jackson 2.17.2 | Vorhandene Fachmodule als Ausgangspunkt prüfen. |
| `gretl-next`, GeoTools-Modul | Eigener Workerklassenpfad; Core schliesst GeoTools aus | Beleg der vorhandenen Struktur. Übertragbar ist die Trennung der Fachabhängigkeiten vom Core; der Workermechanismus wird nicht verbindlich übernommen. |

Die Angaben stammen aus Builddateien und Versionsdefinitionen dieser Commits. [R1], [R2], [R3], [R4], [R5]

Im gelesenen Legacy-Build ist GeoTools nicht als direkte Version in der zentralen Tabelle fixiert. Seine effektiv verwendete Version ist aus dem aufgelösten transitiven Graphen zu ermitteln. Eine nicht aus diesen Dateien belegte Legacy-Version wird hier nicht behauptet.

### 3.2 Abhängigkeiten nach tatsächlich benötigtem Umfang erfassen

Für die im MVP verwendeten Taskbibliotheken MUSS der Coding-Agent direkte und transitive Abhängigkeiten mit exakten Versionen erfassen. Hinzu kommen tatsächlich benötigte native Bibliotheken, Plattformvoraussetzungen, Ressourcen und Service-Registrierungen. Ausschlüsse oder erzwungene Versionen werden begründet. Die Arbeit beginnt mit der kleinen Versionsprobe; sie setzt keinen vollständigen Umbau sämtlicher GRETL-Engines voraus.

Der zugesagte MVP-Katalog umfasst HttpDownload, Extract, SqlExecutor und IliValidator. Die technische GeoTools-Probe qualifiziert zusätzlich eine ausdrücklich ausgewiesene Testkombination, ohne daraus einen öffentlichen Tasktyp oder einen zusätzlichen Runtimebestand zu machen. Publisher und XtfToGpkg werden erst nach dem MVP als eigene Taskfähigkeiten geliefert. Dependencies werden weder allein wegen dieser künftigen Tasks noch allein zur Durchführung der GeoTools-Probe in die Standarddistribution aufgenommen. Eine von einem tatsächlich gelieferten Task direkt oder transitiv benötigte Bibliothek bleibt selbstverständlich zu prüfen und auszuliefern.

IST-Pins werden bewusst übernommen oder geändert und für den verwendeten Pfad geprüft. Ein höherer Versionswert einer anderen Dependency ist allein kein Grund, eine globale Versionsänderung über alle Bibliotheken zu erzwingen.

**Bewegliche SNAPSHOTs sind in einem Releasepaket nicht zulässig.** Für das vorhandene `iox-wkf`-SNAPSHOT ist vor dessen Auslieferung ein geeignetes Release oder ein reproduzierbar gebautes Artefakt eines festen Quellcommits mit eindeutiger Identität und Prüfsumme erforderlich. Die erneute Auflösung von `2.0.1-SNAPSHOT` bei jedem Build oder Start erfüllt dies nicht.

## 4. Abhängigkeits- und Modulgrenzen

### 4.1 Reiner Core, normale Taskbibliotheken

Der Core definiert seine SPI und Ausführungskontexte mit eigenen Java-Typen und JDK-Typen. Er hängt nicht transitiv von Gradle, Groovy oder Geobibliotheken ab. Groovy-Einbettung, CLI und fachliche Tasks sind eigene Module. Standardmässig werden deren zur Ausführung benötigte Bibliotheken trotzdem im selben JVM-Prozess verwendet.

Eine Taskimplementierung darf fachliche Typen in eigenen Konfigurations-, Ergebnis- und Hilfsklassen verwenden. Ein GeoTools-Typ in einer taskseitigen Struktur macht ihn nicht automatisch zu einem Core-Typ. Solche Typen koppeln den jeweiligen Verbraucher allerdings an die Taskbibliothek. Für Ergebnisse, die mehrere Tasks verwenden oder dokumentieren sollen, sind stabile Werte und Artefaktreferenzen deshalb oft zweckmässiger. Ein pauschales Verbot solcher Typen oder ein Transport-DTO-Zwang besteht nicht.

Sequenzielle Ausführung verhindert gleichzeitig aktive Tasks innerhalb eines Laufs. Sie entfernt keinen JVM-globalen Bibliothekszustand und ersetzt keine ordentliche Freigabe von Datastores, Connections oder Listenern. Die betroffene Taskbibliothek muss ihre Lebensdauer behandeln. Erst ein belegter Konflikt kann eine gezielte Prozessausführung im Adapter begründen. Dafür wird im MVP kein allgemeiner Workertransport aufgebaut.

### 4.2 Relevante Migrationsbereiche

| Bereich | Belegter Ausgangspunkt | Behandlung |
|---|---|---|
| GeoTools-Module | 35.2 stellt `gt-bom` und `gt-platform-dependencies` bereit | Benötigte `gt-*`-Module auf demselben Versionsstand halten; BOM gezielt anwenden. |
| JavaEE/JakartaEE | GeoTools 35 migriert entsprechende Abhängigkeiten und Namespaces | Verwendete JAXB-, Activation- und Mail-Aufrufer prüfen; ein JAR-Austausch migriert keinen Quellcode. |
| Raster/ImageIO | GeoTools 34 migrierte von JAI zu Eclipse ImageN | Nur zugesagte Funktionen paketieren; zugehörige Provider und Ressourcen im fertigen Paket prüfen. |
| JSON | Das Plattform-POM 35.2 verwaltet Jackson 3 und eine Jackson-2-Linie | Tatsächlich verwendete APIs zuordnen. Keine globale Major-Version für inkompatible Aufrufer erzwingen. |
| Geometrie | GeoTools 35.2 verwendet LocationTech JTS 1.20.0 | Alte `com.vividsolutions`-Pfade gezielt prüfen. Verschiedene Paketnamen sind keine Typkonvertierung. |
| JDBC und native Extensions | Fachbibliotheken stellen eigene Anforderungen | Im gemeinsamen Klassenpfad einen konsistenten, geprüften Treiberbestand verwenden. |
| Discovery | Datastores, CRS-Provider und ImageIO brauchen Registrierungen | `META-INF/services` und Bibliotheksressourcen bei jeder Paketierungsform erhalten. |

Die GeoTools-Wechsel sind durch Upgradeguide und Release-POMs belegt. Ihre Auswirkungen auf GRETL sind daraus abgeleitete Prüfaufträge; es werden keine bereits reproduzierten Fehler behauptet. [T5], [T6], [T7]

### 4.3 Umfang von Groovy und Erweiterungen

Benötigte Groovy-Module werden ausdrücklich deklariert: Core und weitere Module für tatsächlich angebotene Funktionen. Eine pauschale Vollausstattung ist nicht nötig. Sämtliche Groovy-Module müssen denselben Versionsstand haben.

Groovy 4 und neuer verwenden die Maven-Gruppe `org.apache.groovy`. Groovy enthält unter anderem eine ASM-Kopie mit umbenannten Paketen. Ein globales Upgrade von `org.ow2.asm` repariert deshalb nicht allgemein einen veralteten Groovy-Bytecode-Leser. Die Groovy-Version selbst muss zur JVM passen. [G6]

Produkt- und Taskabhängigkeiten stammen beim Jobstart aus der Distribution beziehungsweise dem deklarierten Erweiterungsbestand. Automatische Grape-/Maven-Auflösung beim Ausführen gehört nicht zum MVP. Eigene Custom-Tasks bleiben möglich; neue Klassen werden auf dem JVM-Weg beim Start über den vorgesehenen Erweiterungsvertrag bereitgestellt.

Der Standardkatalog ist releasebezogen und darf wachsen. `lib/tasks` enthält den gemeinsam qualifizierten Releasebestand; `lib/extensions` erlaubt vor dem Start bereitgestellte eigene Bibliotheken samt kompatiblen Dependencies. Beide Bestände verwenden dieselbe SPI und dieselbe DSL. Die Ablage einer Bibliothek als Erweiterung ist keine Pflicht für alle neuen Tasktypen: Weitere GRETL- oder organisatorische Tasks dürfen regulär in eine gemeinsam ausgelieferte Distribution aufgenommen werden. Entwickler und Betrieb stellen diesen Bestand bereit; Fachanwender müssen dafür keine einzelnen JARs oder Mavenkoordinaten verwalten. Der genaue Liefervertrag steht in [01](01-zielbild-und-mvp.md) und [06](06-betrieb-und-auslieferung.md).

## 5. Einheitlicher Bytecodevertrag

Die Build-JVM, das Bytecodeziel und die JVM bei der Ausführung sind verschiedene Grössen. Für den MVP werden sie bewusst auf eine Linie vereinheitlicht:

| Bereich | Toolchain beziehungsweise Compiler | Ziel und Ausführung |
|---|---|---|
| Core-SPI, Runner, CLI, Taskadapter | JDK 25, `javac --release 25` | Java 25 |
| Groovy-Integration und verwendete Produktquellen in Groovy | Festgelegte Groovy-Version auf JDK 25 | Bytecodeziel 25, Ausführung auf Java 25 |
| Direkt geladenes Jobskript | Eingebetteter Groovy-Compiler | Ziel 25; automatische Übersetzung beim Start |
| Optionales Job-JAR | Derselbe Groovy-Compiler beim Paketieren | Ziel 25; übersetzte Script- und Closureklassen im Paket |

`--release` begrenzt neben dem Classfile-Ziel die verwendbare Standardbibliotheks-API. Nur `source` und `target` bieten diese Absicherung nicht in gleicher Weise. [J5]

Für Groovy MUSS die Einbettung `CompilerConfiguration.targetBytecode` ausdrücklich auf 25 sowie das Quellencoding auf UTF-8 setzen. Script-Basisklasse und Compileranpassungen sind beim direkten Laden und beim Paketieren identisch. [G7]

Ein später aus konkretem Anlass verwendeter Prozess auf Java 21 könnte keinen Java-25-Bytecode laden. Er benötigte eine gesondert geeignete Implementierung und Abhängigkeitszusammenstellung. Ein Classloader ersetzt keine andere JVM-Version. Daraus wird heute weder ein Java-21-Ziel für die Core-SPI noch ein vorsorglicher Prozessvertrag abgeleitet.

## 6. Produktbuild und Distribution

### 6.1 Maven als bestätigtes Buildwerkzeug; Ausgangskandidat 3.10.0

Maven 3.10.0 wurde am **01.10.2026** als reguläres Release veröffentlicht und ist am Stichtag die offizielle aktuelle Version; Maven 4 ist noch als Vorabversion ausgewiesen. Konkretes Release und datierte Apache-Distributionen belegen die Veröffentlichung. [B1], [B4]

Der Build MUSS Maven-Distribution und Prüfsumme im Wrapper fixieren, die JDK-25-Toolchain und Releaseziele setzen sowie Compiler-, Test- und Paketierungsplugins mit exakten Versionen deklarieren. Die Java-25-Eignung des tatsächlichen Builds wird geprüft. Groovy-Anwendungsdependency und gegebenenfalls Groovy-Produktcompiler müssen übereinstimmen.

Die Groovy-Einbettung kann überwiegend in Java implementiert werden. Ein zusätzliches Groovy-Maven-Plugin ist nur erforderlich, wenn es für tatsächliche Produktquellen oder die gewählte Buildintegration gebraucht wird.

### 6.2 Direkter Jobstart und optionale Paketierung

Ein normaler Jobaufruf benötigt weder Maven noch Gradle. Der Benutzer startet die ausgelieferte Runtime mit der Groovy-Datei und Parametern; die notwendige interne Übersetzung geschieht automatisch. Groovy unterstützt hierfür Scripts, eigene Script-Basisklassen und Binding. [G8]

Die optionale Aktion `gretl package job-package.json --output job.jar` ist ein ausdrücklicher Buildschritt für ein gewünschtes Lieferartefakt. Sie ist keine Voraussetzung für `gretl run`. Ihr Paketierungs- und Ressourcenvertrag folgt Kapitel 12; die allgemeine Parameterübergabe bleibt Kapitel 06 zufolge identisch. Das fertige Job-JAR benötigt beim Start wiederum kein Maven oder Gradle.

Die neue Core-SPI DARF KEINE `DefaultTask`-Basisklasse oder Gradle-Workerklasse voraussetzen. Ein Produktbuild und die Abhängigkeitsauflösung während der Paketierung sind von der cachefreien ETL-Ausführung getrennt.

Eine eigenständige Groovy-Anwendung wäre auch technisch nicht auf Gradles eingebettete Groovy-Version beschränkt; Gradle beschreibt separate Compiler- und Anwendungsklassenpfade. Diese Tatsache begründet keine Gradle-Abhängigkeit des hier spezifizierten Produkts. [B2], [B3]

### 6.3 Auslieferung und Nachvollziehbarkeit

Ein Releasepaket MUSS ein maschinenlesbares Manifest mit Runtimeversion, Quellcommit, Groovy-Version, enthaltenen JARs und Prüfsummen sowie den angebotenen Plattformen enthalten. Tatsächlich benötigte native Bibliotheken und wesentliche Taskbibliotheksstände werden aufgenommen. Ein Container fixiert zusätzlich sein Basisimage über einen Digest.

Eine zusätzliche Kompatibilitätsprobe besitzt ein eigenes Prüfinventar mit ihrer vollständigen Testzusammenstellung. Ein nur dort vorhandenes GeoTools-Modul wird nicht im Produktmanifest als ausgelieferte Bibliothek geführt. Unbenutzte Testdependencies gelangen nicht aus einem weit gefassten Produktbuildprofil versehentlich in die Standarddistribution oder jedes Job-JAR.

Der reale Taskkatalog wird vom als später geplant dokumentierten Katalog getrennt. Nicht implementierte Typen erscheinen nicht als verfügbare Provider. Insbesondere werden im MVP weder ein Publisher-Artefakt noch ein scheinbar ausführbarer Publisher-Platzhalter registriert. Ein neues Standardtaskmodul erhält seinen eigenen gezielten Qualifikationsnachweis, ohne dadurch alle bisherigen Module zu externen Addons umzudefinieren.

Releaseprüfungen verwenden das **fertige Paket**. Ein erfolgreicher IDE-Test mit zusätzlichen Bibliotheken reicht nicht. Services und Ressourcen müssen in der normalen Distribution und in einem angebotenen Job-JAR funktionieren. Dass dieselben Klassen im Maven-Testklassenpfad vorhanden waren, beweist dies nicht.

### 6.4 Verbindliche Leitlinie: konventioneller Maven-Produktbuild

> **Der Produktbuild bleibt konventionell. Neue Buildabstraktionen, Plugins und Profile benötigen einen konkreten Nutzen. Gemeinsame Einstellungen werden zentralisiert; ein zusätzliches internes Buildframework wird nicht entwickelt.**

Die Maven-Wahl ist am 18. Oktober 2026 ausdrücklich bestätigt worden. Die folgenden Regeln sind neue Entwurfsfestlegungen in Version 4.1, keine Aussage über einen bereits implementierten Build. Sie ergänzen die Versions- und Auslieferungsvorgaben aus 6.1 bis 6.3. Der Stand der Versionsrecherche bleibt unverändert; konkrete Tool- und Pluginversionen werden in M0 qualifiziert. Die Abnahmefälle TC-BLD01 bis TC-BLD08 stehen in [09](09-tests-und-abnahme.md).

#### BLD-001 — Ein Buildweg, über den Wrapper

Maven ist das einzige Produktbuildwerkzeug des neuen Repositories. Ein zweiter Gradle- oder Ant/Ivy-Build für denselben Produktumfang wird nicht parallel gepflegt. Der Repositoryroot enthält genau einen gemeinsamen Maven Wrapper mit fixierter Distribution und Prüfsumme. Entwickler und CI verwenden diesen Einstieg und die festgelegte Java-Toolchain. Ein zusätzliches System-Maven oder ein vorbereitender manueller Modulbuild ist keine Voraussetzung.

Der Wrapper und Maven dürfen beim Produktbuild ihre ausdrücklich konfigurierten Artefaktquellen verwenden. Ein lokales Maven-Dependencycache ist erlaubt und kein ETL-Taskcache. Der Nachweis eines frischen Builds darf sich aber nicht auf unveröffentlichte, nur auf einem Entwicklerrechner zuvor installierte Artefakte stützen. Der direkte Jobstart bleibt unverändert ohne Maven, Gradle, Ivy oder Code-Dependencyauflösung.

#### BLD-002 — Ein eigener Parent, kleine Modul-POMs

Der Root-POM mit `packaging=pom` ist zugleich Aggregator und einziger projektinterner Parent der Produktmodule. Mehrere projektinterne Vererbungsebenen oder eigene Parent-POMs für jede Taskfamilie werden nicht eingeführt. Bestehende Bibliotheks-BOMs können gezielt importiert werden; dafür wird keine weitere Parent-Hierarchie aufgebaut.

Der Parent enthält gemeinsame Java-/Encoding-Einstellungen, zentrale Versionsvorgaben und die tatsächlich gemeinsam benötigte Plugin-Konfiguration. Ein Modul-POM beschreibt vor allem Artefaktname, direkt benötigte Dependencies und begründete modulspezifische Ausführungen. Maven-Standardverzeichnisse wie `src/main/java`, `src/main/resources`, `src/test/java`, `src/test/resources` und `target` werden verwendet. Abweichungen benötigen einen konkreten Grund.

Module folgen den fachlichen und technischen Grenzen aus Kapitel 10. Es entstehen keine leeren Module für spätere Fähigkeiten und kein Modul je Record. Modulabhängigkeiten werden im Reactor deklariert, nicht durch Shellschleifen oder eine selbst gepflegte Buildreihenfolge nachgebildet. Externe Custom-Task-Bibliotheken müssen den Produktparent nicht erben; die öffentliche Java-SPI genügt als Abhängigkeit.

#### BLD-003 — Versionen zentral verwalten, Dependencies lokal deklarieren

Gemeinsame Dependencyversionen stehen in Parent-Properties beziehungsweise gezielten `dependencyManagement`-/BOM-Einträgen. Ein Modul deklariert seine tatsächlich verwendeten direkten Bibliotheken mit passendem Scope selbst. GeoTools-, INTERLIS-, JDBC- und Testbibliotheken werden nicht pauschal als vererbte Parent-Dependencies an API und Runner verteilt.

Pluginversionen und wiederkehrende Einstellungen stehen zentral in `pluginManagement`, soweit sinnvoll. Zusätzliche executions werden nur in den Modulen aktiviert, die sie benötigen; blosse Versionsverwaltung wird nicht als Aktivierungsnachweis ausgegeben. Die tatsächlich wirksamen Compiler-, Test- und Paketierungsbindungen werden am Effective POM geprüft.

Releaseartefakte verwenden exakte, nachvollziehbare Versionen. Konvergenz wird nicht dadurch vorgetäuscht, dass eine Major-Version global über inkompatible Aufrufer erzwungen wird. Änderungen an Konfliktauflösung, Ausschlüssen oder BOM-Importen werden anhand des aufgelösten Dependencybestands und der betroffenen echten Integrationen geprüft. Die bereits beschriebenen Trennungen von Produkt-, GeoTools-Test- und Packagerbestand bleiben erhalten.

#### BLD-004 — Groovy-Jobs erfordern nicht automatisch einen Groovy-Produktcompiler

Die bevorzugte Anfangsimplementierung von SPI, Runner, CLI und Groovy-Einbettung sind gewöhnliche Java-Quellen. Groovy ist im Adapter eine ausdrücklich deklarierte Laufzeitbibliothek. Beispieljobs können als `.groovy`-Testressourcen liegen und durch echte Adaptertests geladen werden. Ihre Existenz allein rechtfertigt kein zusätzliches Groovy-Maven-Plugin und keine Joint-Compilation-Infrastruktur.

Groovy-Produkt- oder Testquellen bleiben zulässig. Erst wenn solche Quellen tatsächlich kompiliert werden, wird die kleinste passende Integration für die betroffenen Module ergänzt. Die festgelegte Groovy-Version und die gemeinsame Compilerkonfiguration für Jobskripts beziehungsweise optionale Jobpakete bleiben massgeblich. Dies ist keine Einschränkung der JVM-Sprache eigener Taskbibliotheken.

#### BLD-005 — Standardlebenszyklus und wenige ausdrückliche Profile

`./mvnw verify` bleibt der dokumentierte Standardbefehl für den implementierten, ohne externe Dienste prüfbaren Umfang. Compiler, Tests, Ressourcen und JAR-Erstellung werden an ihre normalen Lebenszyklusphasen gebunden. Unit-/kompakte Vertragstests verwenden Surefire, getrennte Integrationstests bei Bedarf Failsafe einschliesslich seiner Fehlerprüfung in `verify`. Eine allein aufgerufene `integration-test`-Phase gilt nicht als vollständige Freigabe.

Zusätzliche Profile sind nur für bereits implementierte, sachlich getrennte Prüf- oder Lieferaufgaben zulässig, etwa Infrastrukturintegration, die vorbereitende GeoTools-Probe oder die spätere Paketabnahme. Das sind mögliche Kategorien, kein Auftrag, sofort alle Profile anzulegen. Für jedes Profil werden Zweck, Aufruf, Voraussetzungen und Lieferstufen-Gate dokumentiert. Die vollständige Releaseprüfung aktiviert die erforderlichen Nachweise ausdrücklich. Fehlende Testinfrastruktur ergibt einen nicht erbrachten beziehungsweise gescheiterten Nachweis, keinen stillen Erfolg.

Profile dürfen nicht aufgrund von Rechnernamen, privaten Dateien, zufälligen Umgebungsvariablen oder automatisch erkanntem JDK unbemerkt andere Fachimplementierungen oder Prüfpflichten auswählen. Eine technisch nötige Auswahl plattformspezifischer Artefakte muss explizit ausgewiesen und für die angebotenen Plattformen geprüft sein. Lokale Standardprüfungen dürfen echte Integrationen weder vortäuschen noch als bereits bestanden darstellen.

#### BLD-006 — Keine versteckte Buildprogrammierung

Vor jedem zusätzlichen Plugin, Profil oder eigener Buildabstraktion wird knapp dokumentiert: Welches konkrete Problem löst es, weshalb genügt die vorhandene Standardlösung nicht, und welchen Pflegeaufwand führt es ein? Dafür genügt ein Eintrag in der Build-Anleitung beziehungsweise im Änderungsbeschrieb. Ein umfangreicher Entscheidungsapparat pro Plugin ist nicht verlangt.

Fachliche Logik bleibt in testbarem Java-/JVM-Code. Insbesondere werden Dependencyinventar, Ressourcenkollisionsprüfung und der spätere Packager nicht als lange eingebettete Groovy-/Ant-Scripts im POM entwickelt. Kleine Aufrufe vorhandener testbarer Werkzeuge sind erlaubt; Shell-/CI-Scripts sollen nicht selbst ein zweites Modul-, Versions- oder Dependency-Modell führen.

Kein eigenes Maven-Plugin, keine eigene Lifecycle-/Packaging-Erweiterung, kein Unternehmensparent und kein allgemeines Convention-Framework ohne nachgewiesenen Bedarf. Ein tatsächlich später begründetes Hilfsplugin ist möglich, aber kein Ersatz für eine zunächst einfache Paketvorlage oder gewöhnliche Java-Klasse. Es gibt keine willkürliche maximale Pluginzahl; entscheidend sind konkrete Aufgabe und geringe zusätzliche Komplexität.

#### BLD-007 — Bibliotheken, Distribution und Jobpaketierung getrennt halten

API, Runner und Taskbibliotheken werden regulär als Bibliotheks-JARs gebaut. Sie erhalten nicht pauschal eine geerbte Shade-Ausführung. Die Scriptdistribution bündelt den ausdrücklich qualifizierten Runtimebestand gemäss Kapitel 06. Tests dürfen diesen Bestand nicht durch heimliche zusätzliche Runtimebibliotheken reparieren.

Der optionale Job-Packager aus Kapitel 12 verwendet seine versionierte, gezielt geprüfte Maven-Shade-Vorlage erst in Lieferstufe C. Deren Pluginversionen sind fixiert. Service-, Groovy- und Logging-Ressourcenregeln bleiben vollständig erhalten. Der Kern, der normale Scriptstart und das fertige Job-JAR benötigen dafür keine Maven-Laufzeitdependency. Beim Packen wird der Job nicht zur Ermittlung seines Buildbestands ausgeführt.

Native Image bleibt spätere optionale Arbeit und erzeugt im MVP weder einen Profilbaukasten noch eine zweite Toolchainmatrix. Maven-Aufräumarbeiten ändern weder diese Liefergrenze noch den zurückgestellten Publisher-Port.

#### BLD-008 — Kurze Build-Anleitung und nachvollziehbare Freigabe

Das Implementierungsrepository erhält eine kurze Build-Anleitung mit Voraussetzungen, Standardbefehl, gezieltem Modulbuild, ausdrücklich unterstützten Zusatzprofilen, erzeugten Artefakten und einem knappen Plugin-/Profilverzeichnis. Technische Details dürfen im POM kommentiert sein; eine zweite von Hand gepflegte Versionstabelle ist nicht nötig. CI verwendet dieselben dokumentierten Maven-Aufrufe statt einer separaten Buildimplementierung.

Ein frischer Checkout mit sauberem projektspezifischem Artefaktbestand muss über den Wrapper bis zur jeweils zugesagten Prüfung baubar sein. Netzwerkzugriff auf festgelegte Buildrepositories ist dabei erlaubt; produktive Fachsysteme und persönliche Secrets sind nicht erforderlich. Ein reiner `package`-Erfolg ersetzt keine spätere Integrations- oder Endproduktprüfung. Referenzprüfungen der Dokumentation sind weiterhin von einem tatsächlich erfolgreichen Maven-Produktbuild zu unterscheiden.

Die neue Leitlinie senkt nicht die bestehenden API-, DSL-, Logging-, Secret-, GeoTools- oder Auslieferungsanforderungen. Sie begrenzt die dafür eingesetzte Buildinfrastruktur. Die konkreten Nachweise werden in M0 begonnen und nur für tatsächlich gelieferte spätere Stufen erweitert.

## 7. Kleine reale GeoTools-Probe in einer gesonderten Testzusammenstellung

Die Versionsprobe MUSS GeoTools tatsächlich aufrufen. Ein von **ili2gpkg** erzeugtes GeoPackage belegt den INTERLIS-Pfad, aber nicht GeoTools-Discovery oder eine GeoTools-Koordinatentransformation.

Die Probe bleibt ein kleiner verbindlicher Entwicklungs-/CI-Nachweis in M0/B. Ein gesondertes **Build-/Testprofil** kombiniert dieselben festgelegten JDK-, Groovy-, Runtime- und bereits qualifizierten Taskelemente mit den ausdrücklich zusätzlich ausgewiesenen GeoTools-Modulen und ihren tatsächlich notwendigen transitiven Dependencies. Der Harness beziehungsweise Testtask und der vollständige Testklassenpfad stehen im Prüfbericht. Dies ist kein auswählbares Runtimeworkerprofil und kein Bestandteil der Job-DSL.

Der Test darf keine vorhandenen gemeinsamen Dependencies heimlich gegen andere Versionen austauschen oder zusätzliche, nicht ausgewiesene Fachprovider vom IDE-/Testklassenpfad beziehen. Ein Konflikt zwischen dem gewählten GeoTools-Stand und der festgelegten gemeinsamen Basis wird sichtbar gemeldet und nach Abschnitt 1.2 behandelt. Eine notwendige Änderung der gemeinsamen Basis wird dokumentiert und für die betroffenen ausgelieferten Tasks erneut geprüft. So belegt die Probe die tatsächlich genannte Kombination und keine unbemerkte andere Runtime.

Die Probe registriert keinen öffentlichen Standardtask und begründet keine Aufnahme ihrer unbenutzten Bibliotheken in die Standarddistribution. Ein eigener Testprozess zur sauberen Durchführung ist erlaubt. Es werden weder ein Runtimeprofil `geotools-jvm` noch ein `OperationExecutor`-Workerprotokoll vorausgesetzt. Der Erfolg ist eine vorbereitende Kompatibilitätsaussage, kein Nachweis eines Publisher-Ports und keiner bereits ausgelieferten GeoTools-Fachfähigkeit.

Sobald eine angebotene Taskfähigkeit GeoTools direkt oder transitiv benötigt, werden ihre konkreten Module normal ausgeliefert. Dann gelten zusätzlich die Service-, Ressourcen- und Fachprüfungen am tatsächlich erzeugten Distributions- beziehungsweise Job-JAR-Artefakt. Ein Paket nur für SQL oder IliValidator benötigt ohne eigenen solchen Dependencybedarf kein GeoTools. Ein optional zusätzlich paketierter GeoTools-Harness kann vorbereitend geprüft werden; er ist kein obligatorisches Gate der ersten generischen Job-JAR-Funktion.

Der begrenzte Mindestumfang ist:

1. Eine kleine Punkt-Shapefile mit explizitem Schema und CRS EPSG:2056 schreiben und nach Schliessen des Schreibers erneut lesen.
2. Objektzahl, Geometrien, CRS und festgelegte Attribute einschliesslich Umlauten, einem Nullwert und einem numerischen Wert prüfen. Feldbreiten und Zeichensatz werden bewusst festgelegt.
3. EPSG:4326 zusätzlich auflösen und eine Transformation mit ausdrücklicher Achsenreihenfolge gegen einen dokumentierten unabhängigen Kontrollwert mit begründeter Toleranz prüfen. Nur Hin- und Rücktransformation wäre kein ausreichender Schutz gegen zwei gleichartige Achsenfehler.
4. Datastores und Streams schliessen; Dateien anschliessend erneut öffnen beziehungsweise entfernen können.
5. Tatsächliche JVM-, Groovy-, Runtime-, Task-, GeoTools- und Providerstände sowie die zusätzlichen Testdependencies im Prüfbericht nennen; fehlende Services oder EPSG-Ressourcen müssen sichtbar scheitern.

Das ist eine technische Integrationsprobe, kein Auftrag zu einer neuen allgemeinen Geo-Engineplattform oder einem öffentlichen GeoTools-Task. Sie verlangt keinen Publisher und keinen XtfToGpkg-Port. Weitere Geometrietypen, Rasterpfade oder Formate werden geprüft, sobald ein angebotener Task diese Fähigkeiten zusagt.

## 8. Prüfaufträge für die Freigabe

Die IDs bleiben für die Zuordnung zu [09 — Tests und Abnahme](09-tests-und-abnahme.md) stabil. Geprüft wird die **genaue Artefaktkombination** und der tatsächlich implementierte MVP-Umfang. PLAT-02 nennt zusätzlich ausdrücklich seine vorbereitende Testzusammenstellung; deren Dependencies werden nicht mit dem ausgelieferten Runtimebestand gleichgesetzt.

### PLAT-01 — Script und Custom-Tasks

Das fertige Paket weist direkten Scriptstart, verzögerte Taskkonfiguration und die Weitergabe eines erst während der Ausführung bestimmten Werts nach. Eine eigene Java-Task aus einem zusätzlichen JAR muss funktionieren. Fehlermeldungen erhalten Scriptdatei, Zeile, Task und Ursache soweit verfügbar. DSL-, Ergebnis- und Cleanup-Verhalten richten sich nach Kapitel 03 und 04.

Der Nachweis umfasst die anwenderorientierte Grundform mit Einstellungen über `=`, benannten Schritten, Dateihilfen und deklarierten Parametern ohne obligatorischen Contextparameter. Die gleiche Taskbibliothek muss darin sowie über die vertiefende Action-Oberfläche funktionieren. Parameterhilfe und verständliche Eingabefehler sind Produktfunktionen und dürfen nicht von einem nachträglich installierten Editorplugin abhängen.

Bei mehreren Läufen werden frische Scriptinstanzen und Bindings verwendet. Die Groovy-Dokumentation weist auf die fehlende Threadsicherheit des Bindings hin. [G8] Für ein angebotenes Job-JAR werden dieselben Parameter- und Taskresultatfälle ausgeführt.

### PLAT-02 — GeoTools

Die kleine Probe aus Abschnitt 7 besteht in ihrer ausgewiesenen Entwicklungs-/CI-Testzusammenstellung. Ihr Nachweis bleibt für die gewählte Plattformstrategie verbindlich, obwohl unbenutzte GeoTools-Bibliotheken nicht in der Standarddistribution liegen. Ein Testprotokoll benennt diese Grenze ausdrücklich.

Bei einer tatsächlich gelieferten GeoTools-Fachfähigkeit werden zusätzlich deren Artefakte aus der fertigen Distribution und gegebenenfalls ihrem Job-JAR geprüft. Der erste generische Packager braucht kein GeoTools-Harness-JAR, wenn sein angebotener Bibliotheksbestand GeoTools nicht benötigt. Nur zusätzlich zugesagte Fähigkeiten erhalten zusätzliche Fälle. Eine Vektorprobe behauptet keine qualifizierte Raster-, ImageIO-/ImageN- oder Headless-Unterstützung.

### PLAT-03 — Benötigte Fachbibliotheken und Treiber

Ein kleiner echter INTERLIS-Pfad prüft Modellkompilation und Validation. Die tatsächlich angebotenen Tasks erhalten gezielte Prüfungen ihrer benötigten JDBC-Treiber, Engineaufrufe und Ressourcen. Der MVP verlangt weder einen Publisher-Port noch einen XtfToGpkg- oder zusätzlichen Import-/Exporttask. Diese künftigen Fähigkeiten erhalten eigene gezielte Bibliotheks- und Fachnachweise, sobald sie angeboten werden.

Für den späteren begrenzten Publisherport bleibt erhalten: Seine zugesagte Ausgabe wird fachlich wieder gelesen und mit dem erwarteten Inhalt verglichen. Die entsprechenden TC-P-Fälle in Kapitel 09 sind spätere Modulgates. Vollständige Legacy-Formatabdeckung oder ein neuer Publikationsspeicher werden daraus nicht abgeleitet; die aktuelle MVP-Freigabe wartet nicht auf diese Publisherfälle.

Ein nicht ladbarer Treiber oder eine fehlende benötigte Modellressource muss eine verständliche Fehlermeldung ergeben. JVM-Warnungen über internen oder nativen Zugriff werden eingeordnet. Erforderliche Startoptionen werden mit Begründung für das betroffene Paket festgelegt; beliebige globale `--add-opens`-Sammlungen sind keine Fehlerbehebung.

### PLAT-04 — Distribution und Betrieb

Der MVP liefert zunächst Linux x86-64 mit der vorgesehenen Containerplattform. macOS ARM64 kann zusätzlich als Entwicklungsplattform angeboten werden und wird dann geprüft. Windows oder Native-Binaries erhalten ohne konkrete Prüfung keine Supportzusage.

Das fertige Paket startet Jobs ohne Maven, Gradle oder nachträgliche Codeabhängigkeitsdownloads. Manifest, Provider-/Ressourcen-Discovery, Aufräumverhalten und die Fehlermeldung für eine unpassende JVM stimmen. Wenn tatsächlich ein taskinterner Hilfsprozess ausgeliefert wird, kommen dessen Start-, Fehler- und Abbruchfälle hinzu. Sie sind kein allgemeines Gate für alle Taskimplementierungen.

Fachlich gewünschte Netzwerkzugriffe auf Datenquellen oder Modellrepositories bleiben möglich. Sie sind von unerwarteten Downloads ausführbarer Produktabhängigkeiten zu unterscheiden.

### PLAT-05 — Begrenzte frühe Probe und wiederholbare Upgrades

Vor umfangreicher Taskportierung wird eine kleine Versionsprobe erstellt: direkt geladenes Script, eine eigene Java-Task, ein INTERLIS-Fall und der GeoTools-Fall in seinem gesonderten Testprofil. Damit werden konkrete Plattformprobleme früh sichtbar. Dieser Entwicklungsnachweis ist Teil von M0/B und setzt weder eine Publisher-Implementierung noch unbenutzte GeoTools-JARs in der Standarddistribution voraus. Bei der späteren Publisher-Lieferung werden dessen tatsächlich zusätzliche Bibliotheken und Pfade gesondert geprüft. Der Coding-Agent muss hierfür keine vollständige alternative Engineplattform bauen.

Bei Änderungen an JDK-Patch, Groovy, GeoTools oder einer Fachbibliothek werden die betroffenen Fälle wiederholt. Das Protokoll nennt exakte Versionen, Änderungen und Einschränkungen. Ein Compile-Erfolg oder die Instanzierung einer leeren `GroovyShell` ersetzt die echten Aufrufe nicht. Der Prüfumfang wächst mit den tatsächlich angebotenen Fähigkeiten.

## 9. Einordnung von GraalVM

Groovy 6 beschreibt inkubierende Unterstützung dynamischer Aufrufe in Native Images und nennt GraalVM für JDK **25.0.4 oder neuer** als Voraussetzung. Dies betrifft bekannten vorab kompilierten Code und garantiert nicht die spätere Kompilierung beliebiger neuer Groovy-Dateien innerhalb eines bestehenden Native-Binaries. [G4]

Die strategische Reihenfolge ist: frei ausführbares Script, optionales vorkompiliertes Job-JAR und später gegebenenfalls Native Image dieses festen Jobs. Laufzeitparameter bleiben in allen Varianten dynamisch. Der native Build benötigt einen qualifizierten GraalVM-Stand auf der vorgesehenen JDK-25-Linie; er ist keine MVP-Pflicht. Die Core-SPI bleibt aus Gründen der Modularität rein in Java, ohne daraus einen Native-Workervertrag abzuleiten. Die vollständige begrenzte Entscheidung steht in Kapitel 08.

## 10. Arbeitsauftrag an den Coding-Agent

1. Ausgangsstände am Implementierungsdatum prüfen und konkrete stabile Versionen dokumentieren.
2. Maven Wrapper, JDK-25-Toolchain und Releaseziel 25 einrichten; Core, Groovy-Integration und Taskbibliotheken sauber als Module trennen.
3. Für die zuerst benötigten Taskbibliotheken den tatsächlichen gemeinsamen Abhängigkeitsbestand erfassen und reproduzierbar fixieren.
4. Die kleine Versionsprobe aus PLAT-05 umsetzen, einschliesslich echter INTERLIS-Aufrufe und des gesonderten GeoTools-Testprofils. Dessen zusätzliche Dependencies offen ausweisen und ohne tatsächlichen Runtimebedarf nicht ausliefern.
5. Die konkret zugesagten MVP-Tasks HttpDownload, Extract, SqlExecutor und IliValidator auf dieser Basis ausführen; Taskabhängigkeiten und Ressourcen in der realen Distribution prüfen. Publisher und XtfToGpkg bleiben eigenständige spätere Lieferaufträge.
6. Bei einem Konflikt zuerst dessen konkrete Ursache beheben. Eine taskinterne separate JVM nur als begründete Ausnahme behandeln.
7. Die optionale Job-JAR-Paketierung mit derselben Groovy-Version und Compilerkonfiguration umsetzen und gegen Parameter-, Ressourcen- und Providerfälle prüfen.
8. Native Image als späteren optionalen Buildweg offenhalten. Kein Workerprotokoll, Java-21-SPI-Ziel oder zusätzlicher Forschungszweig ist Voraussetzung dieses Auftrags.

## 11. Quellen

### Java

- **[J1]** [Oracle Java SE Support Roadmap, aktualisiert 15.09.2026](https://www.oracle.com/java/technologies/java-se-support-roadmap.html): Java 25 als LTS, Java 27 als Non-LTS.
- **[J2]** [Eclipse Adoptium: Temurin Support und Release Roadmap](https://adoptium.net/support): belegte aktuelle und nächste Versionen sowie Verfügbarkeitszeiträume.
- **[J3]** [Oracle: konsolidierte Release Notes für JDK 25](https://www.oracle.com/java/technologies/javase/25all-relnotes.html): 25.0.4.1 am 18.08.2026 und nächster CPU-Termin 20.10.2026.
- **[J4]** [Temurin 25.0.4.1+1, tatsächliches Release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1): veröffentlicht am 19.08.2026.
- **[J5]** [JDK 25: javac und die Option `--release`](https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html).

### Groovy

- **[G1]** [Offizielle Groovy-Changelogs mit Veröffentlichungsdaten](https://www.groovy-lang.org/changelogs.html).
- **[G2]** [Apache-Archiv für Groovy 6.0.0](https://archive.apache.org/dist/groovy/6.0.0/).
- **[G3]** [Groovy-6.0.0-Versionsdatei im Release-Tag](https://github.com/apache/groovy/blob/GROOVY_6_0_0/gradle.properties) und [annotiertes Tagobjekt](https://api.github.com/repos/apache/groovy/git/tags/55017a7ff49dfb23f75834619229cb7478ffc664).
- **[G4]** [Groovy 6.0 Release Notes](https://www.groovy-lang.org/releasenotes/groovy-6.0.html), insbesondere JDK requirements und Dynamic Groovy in GraalVM Native Images.
- **[G5]** [Groovy 5.1 Release Notes](https://www.groovy-lang.org/releasenotes/groovy-5.1.html).
- **[G6]** [Groovy Downloadseite und Modulkoordinaten](https://groovy.apache.org/download.html); die widersprüchlichen Stable-/Alpha-Texte wurden bei der Bewertung berücksichtigt.
- **[G7]** [Groovy 6.0.0: CompilerConfiguration](https://docs.groovy-lang.org/docs/groovy-6.0.0/html/api/org/codehaus/groovy/control/CompilerConfiguration.html).
- **[G8]** [Groovy in Anwendungen einbetten](https://groovy-lang.org/integrating.html): Scriptbasisklasse, Shell/Classloader und Binding.

### GeoTools

- **[T1]** [GeoTools 35.x: Java-Installationshandbuch](https://docs.geotools.org/stable/userguide/build/install/jdk.html).
- **[T2]** [JDK-25-CI im Release-Tag 35.2](https://github.com/geotools/geotools/blob/35.2/.github/workflows/linux_jdk25.yml).
- **[T3]** [GeoTools-35.2-Parent-POM mit Releaseziel 17](https://github.com/geotools/geotools/blob/35.2/pom.xml).
- **[T4]** [GeoTools 35.2, veröffentlicht am 16.10.2026](https://github.com/geotools/geotools/releases/tag/35.2) und [Wartungsrelease 34.6](https://github.com/geotools/geotools/releases/tag/34.6).
- **[T5]** [BOM-Anleitung zu GeoTools 35.2](https://github.com/geotools/geotools/blob/35.2/BOM-USAGE.md) und [Modul-BOM](https://github.com/geotools/geotools/blob/35.2/bom/pom.xml).
- **[T6]** [GeoTools-35.2-Plattform-BOM](https://github.com/geotools/geotools/blob/35.2/platform-dependencies/pom.xml), unter anderem JTS 1.20.0, ImageN 0.9.3, ImageIO-Ext 2.1.4, Jackson 3.2.3/2.22.2 und JAXB 4.0.4.
- **[T7]** [GeoTools Upgradeguide](https://docs.geotools.org/stable/userguide/welcome/upgrade.html), insbesondere Jakarta in 35.x und ImageN in 34.x.

### Produktbuild

- **[B1]** [Apache Maven Downloads](https://maven.apache.org/download.cgi): am Stichtag Maven 3.10.0 aktuell, 3.9.16 vorherige stabile Linie und Maven 4 noch Preview.
- **[B2]** [Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html): gelesener Stand 9.8.1; JDK-25-Unterstützung ab 9.1.0 und gesonderte Regeln für Gradle-Groovy-Plugins.
- **[B3]** [Gradle Groovy Plugin](https://docs.gradle.org/current/userguide/groovy_plugin.html): Trennung von Compiler- und Anwendungsklassenpfad.
- **[B4]** [Maven 3.10.0: konkretes Release, veröffentlicht am 01.10.2026](https://github.com/apache/maven/releases/tag/maven-3.10.0), [Apache-Release-Notes](https://maven.apache.org/docs/3.10.0/release-notes.html) und [Apache-Archiv mit den datierten Distributionen](https://archive.apache.org/dist/maven/maven-3/3.10.0/): Veröffentlichung vor dem Recherchestichtag, unabhängig vom wechselnden Inhalt der Downloadseite.

### Analysierte GRETL-Stände

- **[R1]** [Legacy-GRETL: Versionsdefinitionen am untersuchten Commit](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gradle/libs.versions.toml).
- **[R2]** [Legacy-GRETL: Builddatei](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/build.gradle).
- **[R3]** [gretl-next: zentrale Versionen und Java-Toolchain](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/build.gradle).
- **[R4]** [gretl-next: GeoTools-Workerklassenpfad](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-geotools/build.gradle).
- **[R5]** [gretl-next: Core ohne GeoTools](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-core/build.gradle).

[J1]: https://www.oracle.com/java/technologies/java-se-support-roadmap.html
[J2]: https://adoptium.net/support
[J3]: https://www.oracle.com/java/technologies/javase/25all-relnotes.html
[J4]: https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1
[J5]: https://docs.oracle.com/en/java/javase/25/docs/specs/man/javac.html
[G1]: https://www.groovy-lang.org/changelogs.html
[G2]: https://archive.apache.org/dist/groovy/6.0.0/
[G3]: https://github.com/apache/groovy/blob/GROOVY_6_0_0/gradle.properties
[G4]: https://www.groovy-lang.org/releasenotes/groovy-6.0.html
[G5]: https://www.groovy-lang.org/releasenotes/groovy-5.1.html
[G6]: https://groovy.apache.org/download.html
[G7]: https://docs.groovy-lang.org/docs/groovy-6.0.0/html/api/org/codehaus/groovy/control/CompilerConfiguration.html
[G8]: https://groovy-lang.org/integrating.html
[T1]: https://docs.geotools.org/stable/userguide/build/install/jdk.html
[T2]: https://github.com/geotools/geotools/blob/35.2/.github/workflows/linux_jdk25.yml
[T3]: https://github.com/geotools/geotools/blob/35.2/pom.xml
[T4]: https://github.com/geotools/geotools/releases/tag/35.2
[T5]: https://github.com/geotools/geotools/blob/35.2/BOM-USAGE.md
[T6]: https://github.com/geotools/geotools/blob/35.2/platform-dependencies/pom.xml
[T7]: https://docs.geotools.org/stable/userguide/welcome/upgrade.html
[B1]: https://maven.apache.org/download.cgi
[B2]: https://docs.gradle.org/current/userguide/compatibility.html
[B3]: https://docs.gradle.org/current/userguide/groovy_plugin.html
[B4]: https://github.com/apache/maven/releases/tag/maven-3.10.0
[R1]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gradle/libs.versions.toml
[R2]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/build.gradle
[R3]: https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/build.gradle
[R4]: https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-geotools/build.gradle
[R5]: https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-core/build.gradle
