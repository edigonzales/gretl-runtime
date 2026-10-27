# 11 — Quellen, Architekturentscheidungen und Roadmap

**Version:** Spezifikationsvorschlag 4.1, 18. Oktober 2026. **Quellenstand:** 17. Oktober 2026.  
**Zweck:** Quellen, überprüfte Beobachtungen und eigene Entscheidungen auseinanderhalten. Dieses Kapitel erweitert den Umfang aus [01](01-zielbild-und-mvp.md) nicht.

## Ergänzung vom 27. Oktober 2026 — Klartextquelle für Secrets

**ADR-4.1-PLAINTEXT:** Auf ausdrücklichen Auftrag ergänzt `plaintext` die Secretquellen `environment` und `file` unter `schemaVersion: 1`. Die Form lautet `"dbPassword": {"plaintext": "testpasswort"}`. Pro Alias ist genau eine Quelle erlaubt; direkte Strings als Secreteinträge und automatischer Quellenfallback gehören nicht zum Vertrag. Bestehende Aliasse, Taskfelder und `SecretValue` bleiben unverändert nutzbar.

Der String wird einschliesslich leerer Werte unverändert übernommen und beim tatsächlichen Zugriff vor Herausgabe im bestehenden Maskierer registriert. Quellenobjekte dürfen den Klartext nicht über ihre Stringdarstellung offenlegen. Der Wert bleibt direkt lesbar in der externen Runtimekonfigurationsdatei; Maskierung ist keine Verschlüsselung. Diese begrenzte Erweiterung führt weder neue Providermechanismen noch eine Einbettung von Laufzeitsecrets in Job-JARs ein. Der normative Detailvertrag steht in OPS-022 in [06](06-betrieb-und-auslieferung.md); die Abnahme prüft die Rohproduktsenken vor einer nachträglichen Maskierung durch Testhilfen.

## Revision 4.1 — Bestätigte Maven-Wahl und begrenzte Buildinfrastruktur

**ADR-4.1-BUILD:** Maven bleibt das alleinige Produktbuildwerkzeug und ist nun ausdrücklich vom Auftraggeber bestätigt. Die verbindliche Ergänzung BLD-001 bis BLD-008 steht in [07, Abschnitt 6.4](07-plattform-und-versionen.md#64-verbindliche-leitlinie-konventioneller-maven-produktbuild): ein projektinterner Root-Parent/Aggregator, kleine Modul-POMs, zentrale Versionen, lokal erklärte Dependencies, normale Lifecyclebindungen und nur begründete zusätzliche Plugins oder Profile. Ein neues internes Buildframework wird nicht entwickelt.

Der konkrete GRETL-Mehrmodulbuild und die spätere Job-JAR-Paketierung sollen auf einem gemeinsamen konventionellen Buildweg bleiben. Ant/Ivy wird nicht wegen seines Alters verworfen, sondern nicht als zusätzlicher eigener Integrationsweg eingeführt. Gradle bleibt aus der neuen Produktbuildimplementierung und dem Laufzeitpfad heraus. Dies ist die festgehaltene Projektentscheidung, keine pauschale Wertung dieser Werkzeuge.

Die generische Runtime, ihre Java-/Groovy-API, Logging, Taskkatalog und Publisher-Liefergrenze ändern sich nicht. Maven ist nur beim Produktbuild und beim ausdrücklich gewählten späteren Paketbuild nötig. Referenzen, Versionskandidaten und Quellenstände dieser Spezifikation werden durch diese Entscheidung nicht neu verifiziert. M0 bleibt für die tatsächliche Qualifikation zuständig. Die neue Dokumentprüfung ist getrennt in Kapitel 17 festgehalten.

## Revision 4.0 — Präzisierte technische Entscheidungen

- **ADR-4-API:** öffentliche Anfangs-SPI vollständig in Kapitel 14, Namensraum `ch.so.agi.gretl.runtime.api`; Groovy bleibt ausserhalb. Contextlebensdauer, Ressourcenbesitz, Resultatnull-Regeln und Methoden sind verbindlich.
- **ADR-4-CORE:** Klassen-/Methodenrollen aus Kapitel 15 statt beliebiger neuer Runnerarchitekturen. Keine Vorschrift für jede private Hilfsmethode, kein zusätzlicher Workerpool.
- **ADR-4-TASK:** typisierte kleine `Fields`-Factories und kompakter Provider statt einer zweiten Annotations-/Schemawelt. Mitgelieferte und eigene Tasks bleiben gleichgestellt.
- **ADR-4-LOG:** automatische strukturierte Lifecycleereignisse, Fehler-IDs/Causes, maskierte Details und begrenzte lokale JSONL-Datei bereits im MVP. SLF4J 2 mit einem qualifizierten Backend, keine neue generische Loggingplattform.
- **ADR-4-TEL:** OTel-/Backendintegration optional; W3C-Kontext nur echt, keine hochkardinalen IDs als Labels, Kubernetesstdout/-stderr und Jenkins-Konsole getrennt prüfen. Keine privilegierte Clusterintegration als Voraussetzung.

Diese Entscheidungen erweitern die bisherigen Liefergrenzen nicht um Publisher, XtfToGpkg oder Native. Belege des Bestandsloggings und aktuelle Primärquellen sind in Kapitel 16 verlinkt. Die Abschlussprüfung aus Kapitel 17 unterscheidet konsistente Spezifikation, echte API-Referenztests und erst später zu erfüllende Produktgates.

## 1. Quellenstand und Aussagegrenzen

### 1.1 Drei getrennt untersuchte Repositories

| Repository | Gepinnter Untersuchungsstand | Rolle in der Spezifikation |
|---|---|---|
| `sogis/gretl` | [`a9222dc0ffd08f8f76570e998101db041822df71`](https://github.com/sogis/gretl/commit/a9222dc0ffd08f8f76570e998101db041822df71) | Vorhandene Taskfassade, tatsächlich verdrahteter Publisher, begonnene Umstrukturierung, Dokumentation, Tests und Bibliotheksdefinitionen. |
| `sogis/gretljobs` | [`7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8`](https://github.com/sogis/gretljobs/commit/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8) | Reale Jobmuster für Laufzeitwerte, Vor-/Nacharbeit, Ergebnisübergabe und Publisheranforderungen. |
| `edigonzales/gretl-next` | [`08bd11e97a54f32f8702d34ff16fb39bec6ea046`](https://github.com/edigonzales/gretl-next/commit/08bd11e97a54f32f8702d34ff16fb39bec6ea046) | Bereits angelegte Modul-, API- und Workergrenzen als technische Vorarbeit. |

Die Pins bezeichnen untersuchte Quellen. Sie belegen nicht, dass genau diese drei Stände zusammen in Produktion eingesetzt werden. Ein Branchname oder heutiger Repositoryinhalt ersetzt bei einem späteren Vergleich keinen Commitbezug.

**Nachweisumfang:** Es fand eine statische Inspektion von Quellen, Builddateien, Dokumentation und Testcode statt. GRETL-Jobs, GRETL-Tests, die neue Runtime und Native-Builds wurden für diese Spezifikation nicht ausgeführt. Beschriebene mögliche Fehler sind daher begründete Codebefunde und Prüfaufträge; sie sind keine behaupteten reproduzierten Produktionsvorfälle. Die Ziel-API-Beispiele sind zu implementierende Verträge. Diese Abgrenzung gilt auch für Formulierungen wie „Gate“, „unterstützt“ und „qualifiziert“: Die Spezifikation fordert einen Nachweis, sie ersetzt ihn nicht.

### 1.2 Zentrale Belege und ihre Bedeutung

- **Aktiver Publisherpfad:** Die untersuchte [Gradle-Fassade `Publisher`](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/tasks/Publisher.java) verwendet weiterhin [`PublisherStepOld`](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java). Der Klassenname „Old“ ist kein Beleg für unbenutzten Code. Den vollständigen Ablauf und die daraus abgeleiteten Risiken dokumentiert [02 — Bestandsanalyse](02-publisher-bestandsanalyse.md).
- **Vorhandener Strategieentwurf:** [`next_publisher.md`](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/next_publisher.md) beschreibt unter anderem Publikationsrecords, S3 und nachgelagerte Verarbeitung. Der Entwurf ist eine relevante Vorarbeit; S3 oder Netl werden dadurch nicht zu beschlossenen MVP-Abhängigkeiten.
- **Laufzeitwerte und Hooks:** Der [Neophytenjob](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/afu_neophyten_pub/build.gradle) und [MOCheckSO](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_mocheckso/build.gradle) sind konkrete Ausgangspunkte für die in Kapitel 04 ausgearbeiteten Migrationen. Vor-/Nacharbeit bleibt fachlich erforderlich, auch wenn die zusätzliche Gradle-Hook-API entfällt.
- **Fachliche Resultate:** Die Jobs [DM01-SO](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/0_dm01_so/build.gradle) und [DM01-CH](https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/1_dm01_ch/build.gradle) begründen die ausdrückliche Unterscheidung zwischen geänderten Teilen und vollständigem Publikationsbestand. Ihre vollständige Migration gehört weder zum Runtime-MVP noch zum späteren ersten Einzelteil-Publisher-Port.
- **Wiederverwendbare Architekturarbeit:** Die [gretl-next-Architektur](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/docs/architecture.md) und der [GeoTools-Modulbuild](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-geotools/build.gradle) belegen vorhandene Trennungsansätze. Ihre Gradle-Integration wird ersetzt; eine dort verwendete Prozessgrenze wird nicht allein aufgrund ihres Vorhandenseins zum Pflichtmodell der neuen Runtime.

Die detaillierten Quellenverzeichnisse in 02, 04, 07 und 08 bleiben Teil des Nachweises. Dieser Index führt die wichtigsten Begründungsketten zusammen, ohne sämtliche Einzelstellen erneut aufzulisten.

### 1.3 Veröffentlichte Plattformkandidaten

| Bestandteil | Ausgangskandidat | Veröffentlichungsbeleg vor dem Stichtag |
|---|---|---|
| Runner-JVM | Temurin **25.0.4.1+1** | [Konkretes Temurin-Release](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.4.1%2B1), veröffentlicht am **19.08.2026**. |
| Job-Sprache | Groovy **6.0.0** | [Offizielles Changelog](https://www.groovy-lang.org/changelogs.html) und [Apache-Distribution](https://archive.apache.org/dist/groovy/6.0.0/), veröffentlicht am **22.09.2026**. |
| GeoTools-Kompatibilitätsprobe und später benötigte GeoTools-Funktionen | **35.2** | [Konkretes GeoTools-Release](https://github.com/geotools/geotools/releases/tag/35.2), veröffentlicht am **16.10.2026**. |
| Produktbuild | Maven **3.10.0**, über Wrapper | [Konkretes Maven-Release](https://github.com/apache/maven/releases/tag/maven-3.10.0) und [Apache-Archiv](https://archive.apache.org/dist/maven/maven-3/3.10.0/), veröffentlicht am **01.10.2026**. |

Java 25 ist die gewählte LTS-Linie; siehe [Oracle Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html). Das [GeoTools-35.2-POM](https://github.com/geotools/geotools/blob/35.2/pom.xml) hat weiterhin Compilerziel 17, während der Release-Tag bereits einen [JDK-25-Workflow](https://github.com/geotools/geotools/blob/35.2/.github/workflows/linux_jdk25.yml) enthält. Eine Mindestversion und eine konfigurierte CI sind unterschiedliche Belege. Keiner davon qualifiziert automatisch die gesamte GRETL-Kombination.

Kapitel 07 enthält die Versionsentscheidung, Fallbackregeln und Pflichtgates. Die widersprüchlichen Stable-/Alpha-Texte der Groovy-Downloadseite wurden dort mit Releasebelegen abgeglichen. Für die begrenzte spätere Native-Option sind die [Groovy-6-Release-Notes](https://www.groovy-lang.org/releasenotes/groovy-6.0.html) und die [GraalVM-Kompatibilitätsdokumentation](https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/) relevant. Version 4.0 verfolgt weiterhin keinen eigenen Forschungsstrang zu experimentellen VM-Unterbauten. Die optionale Job-JAR-Paketierung ist in Kapitel 12 mit ihren Primärquellen dokumentiert.

## 2. Korrigierte Architekturentscheidungen

Die folgenden Entscheidungen fassen den gültigen Stand zusammen und ersetzen widersprechende Vorgaben aus Version 1.0 und 2.0. Frühere ADR-Nummern werden fortgeführt. Insbesondere ist der in Version 2.0 noch enthaltene Publisher-MVP-Port durch ADR-14 verschoben. Kein Coding-Agent soll daraus einen parallel gültigen alten Implementierungsauftrag ableiten.

| ADR | Seit 3.0 weiterhin gültige Entscheidung | Begründung und Grenze |
|---|---|---|
| ADR-01 | Groovy auf der JVM; direkter Scriptstart als Standard; Jobpaketierung freiwillig. | Ein Jobbuild ist keine Pflicht des Autorenworkflows. Ein optionales Auslieferungsartefakt darf einen eigenen Build haben. |
| ADR-02 | Statischer Plan, Konfiguration erst beim Taskstart, Actions mit synchronen Aufrufen. | Laufzeitwerte brauchen keinen Gradle-Providerbaukasten. Vor-/Nacharbeit bleibt normaler Code. |
| ADR-03 | Java-SPI, sequenzielle gemeinsame JVM zuerst. | Fachmodule bleiben unabhängig vom Core. Getrennte Prozesse werden nur bei konkretem Bedarf eingeführt; keine Codec-/Worker-Vorleistung. |
| ADR-04 | Publisher ist gewöhnlicher Custom-Task wie SqlExecutor. | Komplexe Fachlogik gehört seinem Modul. Der Core benötigt dafür keinen Host-Publikationskoordinator. |
| ADR-05 | Keine vorgeschriebene neue Publikationsspeicherung im Runner-MVP. | Die frühere Pflicht zu Generationen, Pointer und Parent-Hash-Kette entfällt. Ein späterer Publisher-Neuentwurf ist separat zu entscheiden. |
| ADR-06 | Normale Exception-/Catch-Semantik und allgemeine Diagnose. | Die frühere globale Commit-/Deliveryregistry, Publikations-Exitcodes und Reconcile-CLI entfallen ersatzlos. |
| ADR-07 | Kein Taskcache, automatischer Vollretry oder Resume. | Fachzustand bleibt möglich, liegt aber beim Job oder Task. Sequenzielle Ausführung schützt nicht vor unabhängigen externen Schreibern. |
| ADR-08 | Native ist eine spätere Option für einen ausgewählten festen Job. | Kein Native-Worker-first und kein universeller dynamischer Native-Runner als Forschungsziel des MVP. |
| ADR-09 | Ein erster Job-Fat-JAR-Pfad mit normalem vorkompiliertem Groovy-Bytecode. | Eine einzige Produktvariante hält den Packager klein. Keine `@CompileStatic`-Pflicht und keine Jobausführung beim Packen. |
| ADR-10 | Allgemeiner JAR-Launcher, explizite Dependencies/Ressourcen, Parameter erst beim Start. | Keine automatische Ermittlung durch ETL-Ausführung. JVM-Bytecodekompilation fixiert keine späteren Werte. |
| ADR-11 | Maven Shade als erster getesteter Einzel-JAR-Weg. | Etablierte Service-/Ressourcen-Merges; eigener Nested-JAR-Loader nur bei belegtem Bedarf. |
| ADR-12 | Java 25 durchgehend als erste Produktbaseline. | Die frühere Vorleistung eines Java-21-SPI allein für potenzielle Worker entfällt. Eine spätere begründete Ausnahme wird dann separat geschnitten. |
| ADR-13 | Offener, releasebezogener Standardkatalog; alle Taskbibliotheken verwenden dieselbe SPI. | Die erste Aufzählung ist keine dauerhafte Beschränkung. Neue GRETL-Tasks dürfen regulär in die Standarddistribution, eigene Bibliotheken in organisatorische Distributionen aufgenommen werden. Anwender verwalten keine einzelnen JARs; Entwickler und Betrieb liefern den geprüften Bestand. |
| ADR-14 | Publisher-Umsetzung und -Bereitstellung folgen nach dem MVP als eigener wichtiger Liefermeilenstein. | Vollständiger Audit, allgemeine Lehren, Zielvertrag und spätere TC-P-Tests bleiben erhalten. Kein Publisher-Code, registrierter Stub, Artefakt, nur deshalb nötiger Dependencybestand oder Publisher-End-to-end-Gate im MVP. Der spätere Auftrag ist unabhängig von Job-JAR und Native. |
| ADR-15 | Anwenderorientierte Grundform mit `=`-Einstellungen, benannten Schritten und deklarierten Parametern; Action als vertiefende Oberfläche. | Ein einfacher Job verlangt keinen expliziten Context, keine Builder-/TaskRef-Kenntnisse und keine generische Programmierung. Typen, Pflichtangaben, Beschreibungen und Hilfe unterstützen Fachanwender. Beide Formen teilen dieselbe späte Konfiguration und Ausführungssemantik; es entsteht kein zweites Ablaufmodell. |
| ADR-16 | Vorbereitende GeoTools-Probe und tatsächlichen Runtime-Lieferbestand getrennt halten. | Eine kleine verbindliche M0/B-Entwicklungs-/CI-Probe nutzt dieselbe qualifizierte JDK-/Groovy-/Runtime-/Taskbasis plus offen ausgewiesene GeoTools-Testdependencies. Allein dieser Test begründet keine unbenutzten GeoTools-JARs im Standardpaket oder jedem Job-JAR. Ein direkt oder transitiv tatsächlich benötigter GeoTools-Bestand wird hingegen normal ausgeliefert und im konkreten Lieferartefakt geprüft. |

Keine dieser Entscheidungen macht fachliche Teilfehler belanglos. Sie bestimmt, wer deren Bedeutung definieren und behandeln muss. Ein Publisher-Neuentwurf kann ohne Änderung der allgemeinen DSL sehr wohl bessere Sichtbarkeits-, Historien- und Wiederanlaufgarantien liefern.

Für den aktuellen Katalog gilt damit ausdrücklich: A liefert HttpDownload, Extract und SqlExecutor; B ergänzt IliValidator. Die technische GeoTools-Probe bleibt zusätzlich ein verbindlicher vorbereitender Entwicklungsnachweis mit eigenem Testbestand, kein öffentlicher Standardtask und kein Grund für unbenutzte Runtimeabhängigkeiten. XtfToGpkg folgt nach dem MVP mit einem eigenständigen noch zu konkretisierenden Taskvertrag. Publisher folgt nach seinem erhaltenen Vertrag aus Kapitel 05. „Später“ ist keine Festlegung, diese Tasks dauerhaft ausserhalb der Standarddistribution zu halten.

Die anwenderorientierte DSL ist eine begründete Entwurfsentscheidung, noch kein empirischer Usability-Nachweis. Vor Freigabe werden kurze Aufgaben mit der vorgesehenen Zielgruppe geprüft: einen vorhandenen Schritt konfigurieren, eine Pflichtangabe korrigieren, einen Parameter übergeben und einen weiteren vorhandenen Baustein anschliessen. Bibliotheksautoren verwenden weiterhin die vertiefenden Java-/Groovy-Verträge.

### ADR-4.1-PATHS — Rungebundene Paketressourcen und explizite Modellpfade

Entscheidung vom 27. Oktober 2026: Materialisierte Job-JAR-Ressourcen werden erst nach erfolgreicher Definitions-, Parameter- und Planprüfung unter `<workRoot>/<runId>/job-resources/` angelegt. Sie bilden einen Lesebestand innerhalb desselben Besitz- und Retentionvertrags wie Taskarbeitsdateien. Der separate temporäre JAR-Ordner mit bedingungsloser Löschung entfällt. Hilfe und Plan benötigen keine Materialisierung; Setupfehler bleiben Code 2 und räumen ausschliesslich den unvollständigen eigenen Bereich bestmöglich auf.

`modeldir` bei Csv2Excel, Av2ch und Av2geobau akzeptiert additiv einen lokalen `Path` als normalisierten absoluten Pfadtext; bestehender Suchpfadtext bleibt unverändert. Der allgemeine Textkonverter und die Requesttypen bleiben unverändert. Beispiele trennen Jobressourcen (`file`), Zwischenstände (`workPath`) und dauerhafte Ausgaben (deklarierte Pfadparameter). Keine Änderung der öffentlichen Task-SPI, keine neue CLI-Option, kein periodischer Janitor und keine Änderung der Abschlussreihenfolge. Die vollständigen Regeln stehen in OPS-021/OPS-040 und PKG-050/PKG-051.

## 3. Quellen der Paketierungsentscheidung

| Frage | Primärquelle und Bedeutung |
|---|---|
| Kann Groovy einen Job vorab zu Bytecode kompilieren? | [Groovy groovyc](https://groovy-lang.org/groovyc.html): Scriptkompilation und eigene Basisklasse. |
| Bleibt der Start über eine gemeinsame Integration möglich? | [Groovy Integration](https://groovy-lang.org/integrating.html): Scriptbasisklasse, Classloader und Binding. |
| Was muss beim Zusammenpacken von JARs erhalten bleiben? | [Maven Shade Resource Transformers](https://maven.apache.org/plugins/maven-shade-plugin/examples/resource-transformers.html): Services, Groovy-Module und Manifestbehandlung. |
| Ist das für GeoTools ein plausibler Weg? | [GeoTools Build FAQ](https://docs.geotools.org/stable/userguide/build/faq.html): expliziter Fat-JAR-Weg mit Service- und weiteren Ressourcen-Merges. |
| Was bedeutet ein ausführbares JAR? | [Java 25 JAR-Spezifikation](https://docs.oracle.com/en/java/javase/25/docs/specs/jar/jar.html): Main-Class, Manifest und Multi-Release-Regeln. |
| Warum löst ein nativer Launcher keine beliebige Runtimekompilation? | [GraalVM Compatibility](https://www.graalvm.org/latest/reference-manual/native-image/metadata/Compatibility/): statischer Programmbestand und dynamische Mechanismen. |

Die technischen Quellen belegen Mechanismen. Die Auswahl eines separaten optionalen Packagers, die konkreten GRETL-CLI-Namen und der MVP-Zuschnitt sind begründete Architekturvorschläge dieser Spezifikation. Es wurde kein fertiges GRETL-Job-JAR daraus erzeugt.

## 4. Roadmap

### A — Kleine allgemeine Runtime

Zuerst die generische SPI, Planung, Groovy-Bindung, Laufzeitwerte, eigene Tasks, normale Fehler und allgemeine Betriebsoberfläche implementieren. Die Grundoberfläche bietet `=`-Einstellungen, benannte Schritte, deklarierte Parameter, Hilfe und verständliche Fehler; fortgeschrittener Code verwendet dieselbe Ausführung. Der Erfolg wird an unabhängigen Operationen und realen HTTP-/SQL-/Archivpfaden geprüft. Diese Stufe braucht keine Publisherinstallation.

### B — Fachbibliotheken und ausgewählte Migration

Dann den realen IliValidator gegen denselben Kern und den tatsächlich gelieferten Bibliotheksbestand prüfen. Eine kleine verpflichtende GeoTools-Probe klärt bereits in M0/B die Plattformstrategie in einem gesonderten Testprofil mit derselben JDK-/Groovy-/Runtime-/Taskbasis und ausdrücklich zusätzlichen GeoTools-Modulen. Sie ist kein zusätzlicher öffentlicher Standardtask und kein Anlass, unbenutzte GeoTools-JARs in das MVP-Paket aufzunehmen. Eine gemeinsame Dependency wird dabei nicht heimlich ersetzt. A+B bilden den Script-MVP ohne Publisher-Code oder -Bereitstellung.

Pilotjobs werden anhand tatsächlich vorhandener Fähigkeiten gewählt. Ein Job mit SFTP, ITF, Regionen oder produktivem SIMI-Ablauf wird nicht durch Weglassen seiner Anforderungen als bereits migriert bezeichnet. Der Vergleich erfasst fachliche Outputs, Vor-/Nacharbeit, No-data und Fehlerfolgen; ein erfolgreicher Prozessstart reicht nicht.

### C — Optionales Job-Fat-JAR

Einen bestimmten Job mit festen Bibliotheken und statischen Ressourcen paketieren und aus einem sauberen Zielumfeld starten. Parameter, Secrets und externe Daten bleiben variabel. Dieser praktische Auslieferungsmodus wird nach Kapitel 12 unabhängig freigegeben.

Services und Ressourcen werden für die tatsächlich angebotenen Bibliotheksbestände im erzeugten JAR geprüft. GeoTools-spezifische Paketgates werden erst erforderlich, wenn eine gelieferte Fachfähigkeit GeoTools tatsächlich benötigt. Ein optionaler vorbereitender GeoTools-Harness im eigenen Job-JAR ist kein zusätzliches Freigabekriterium des ersten generischen Packagers.

Das JAR ist eine definierte Momentaufnahme des Programms, kein universeller Container für nachträglich neue Taskklassen. Eine Bibliotheksaktualisierung oder Codeänderung erzeugt bei dieser Auslieferungsform ein neues Artefakt. Der Scriptmodus bleibt weiterhin frei änderbar.

### Nach dem MVP — Publisher und weiterer Standardkatalog

Der Publisher erhält wegen seiner Bedeutung einen eigenen Liefermeilenstein auf Basis von Kapitel 05 und den erhaltenen TC-P-Fällen. Er muss weder auf C noch auf Native Image warten. Der erste begrenzte Port, weitere vorhandene Publisherfähigkeiten und ein möglicher fachlicher Neuentwurf bleiben getrennte Aufträge. Ihre Entscheidungen verändern nicht automatisch Core, CLI oder allgemeine Fehlercodes.

Weitere bestehende Tasks und eine unabhängige XtfToGpkg-Operation werden anhand ausgewählter Jobs und eigener begrenzter Verträge portiert. Nach Qualifikation können sie den Standardkatalog erweitern. Organisationsspezifische Bibliotheken können ebenso in eure gemeinsame Distribution aufgenommen werden; eine dauerhafte externe Addonrolle ist keine Voraussetzung.

### Später — Ein begrenzter Native-Versuch

Startzeiten werden am vollständigen Aufruf gemessen. Bei einem konkreten Nutzen kann ein fester kleiner Job nativ gebaut werden. Ein erfolgloser Native-Versuch blockiert keine freigegebene JVM-Ausführung und kein funktionierendes Fat-JAR.

## 5. Vor späteren Erweiterungen zu klären

| Erweiterung | Tatsächlich zusätzlich benötigte Entscheidung |
|---|---|
| Erster Publisher-Port | Eigenen Liefertermin und konkreten Implementierungsauftrag nach Kapitel 05 festlegen; die erhaltenen TC-P-Nachweise tatsächlich erbringen. Kein Zusatzgate für A/B/C. |
| Weitere Publisherfähigkeiten | Quellen, Regionen, Formate, Metadatensysteme und bestehende Resultatbedeutung im Taskmodul. |
| Andere Publisher-Speicherpolitik | Layoutkompatibilität, Leser, Konkurrenz, Crashfolgen und Wiederanlauf im Publisher, nicht als generischer Runtimevertrag. |
| XtfToGpkg | Begrenzte Quell-/Modellauswahl, Zielinhalt, Ergebnis, Fehler- und Überschreibverhalten sowie konkreten ili2gpkg-/Treiberbestand festlegen und prüfen. Unabhängig vom Publisher. |
| Weitere SQL-Funktionen | Eigene Transaktionssteuerung, Queryresultate, Templates und konkrete Sicherheits-/Quotingregeln im SQL-Modul. |
| Getrennte JVM für eine Bibliothek | Nachgewiesener Konflikt, konkreter Datenvertrag, Start-/Abbruchverhalten und vollständiger Lieferbestand. |
| Öffentliche Taskparallelität | Ressourcenbesitz, Threadtauglichkeit, Logger/globaler Zustand, Resultatbesitz und Cancelverhalten. |
| Jobserver oder Daemon | Isolation zwischen Runs, Speicher-/Classloader-Lebensdauer, Wiederverwendung und Betrieb. |
| Erweiterte Parameterfunktionen | Zusätzliche Typen oder Oberflächen erst bei Bedarf ergänzen; die einfache gemeinsame Deklaration, Hilfe und Prüfung für Script und Paket gehören bereits zum MVP. |
| Native eines festen Jobs | Passender Kandidat, vollständiger Code-/Ressourcenbestand, tatsächlicher Vorteil und begrenzte Plattformfreigabe. |

Diese Punkte sind dokumentierter Überbau, keine Aufforderung zu ihrer vorsorglichen Umsetzung. Ein Erweiterungsvorschlag benennt zuerst Nutzen und konkret neue Zusage, dann seinen Vertrag und seinen Nachweis.

## 6. Fortschreibung

Bei Beginn der Implementierung werden Bibliotheksstände erneut geprüft und als konkrete Versionen fixiert. Ein Upgrade nennt die betroffenen Fähigkeiten und wiederholten Tests. Ein Versionsbeleg ist weiterhin kein Laufzeitnachweis.

Neue Erkenntnisse ändern zusammengehörig API, Beispiele, Umfang und Abnahme. Der Vorschlag bleibt dadurch sowohl für Menschen als auch für Coding-Agenten widerspruchsfrei. Insbesondere sind aufgehobene Vorgaben der ersten beiden Versionen keine heimlichen zusätzlichen Anforderungen. Ein nicht gelieferter Publisher wird weder aus dem Strategieziel gestrichen noch als bereits verfügbare Funktion geführt.
