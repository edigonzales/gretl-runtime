# P3: Vorkompilierte Scriptfragmente im Job-JAR

Auftrag `script-includes`, Spezifikation 1.3. `assignment_base_sha`: `0bfc8f0fccf43989d751d4f5490202311d200628`; `tooling_base_sha`: `54bb123070fc7bd81aea58b4e1191beecf568cbf`; `base_status`: `MAIN_WITH_ACCEPTED_TOOLING`. Unveränderte `phase_base_sha` / `base_sha`: `ecfc4a7232b3c717fd172a472dc9b00fc77a6267`. Kandidaten-SHA wird nach Commit extern festgehalten.

Sollmenge: S01–S19, mit Schwerpunkt S17/S18 und der SCRIPT-/JAR-Parität. S19 bleibt der unveränderte generische IMAGE-Vertrag; keine IMAGE-Produktdatei geändert, keine neue IMAGE-Qualifikation behauptet. C01–C08 folgen in P4.

Die erlaubte Scriptmenge entspricht exakt dem erfassten Includeabschluss. Explizite Helperquellen kommen zusätzlich hinzu; unbekannte zusätzliche Scripts bleiben PACKAGE_MULTIPLE_SCRIPTS. Captured Bytes liefern Kompilation und Bundlehash. Leere/reine Klassenfragmente haben eigene no-op Scriptklassen. Isolierter Compiler und ausgewählte Runtime verwenden dieselben Typen, keine Host-Classpath-Erweiterung.

Neues internes job.json-Schema 3 enthält definitionMode single/project und Bundleversion 1. Bundlequellen, Klassen, Rollen, virtuelle Namen, kanonische Pfade, Einstiege/Scopes und geordnete Sites/Kanten werden ohne Sourceparser validiert. Alle Scriptklassen werden vor der ersten Instanziierung ohne Initialisierung geladen und auf Basistyp/Konstruktor geprüft. Der gemeinsame Registrierungsweg arbeitet anschliessend ausschliesslich mit Bytecode. Benutzerschemata 1/2/3, interne Altformate 1/2, Dependencyinventar 2 und LSP-Protokoll 1 bleiben erhalten.

Notwendige kumulative Auswahl (JDK 25.0.2):

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-cli -am -Dtest='FragmentJarProcessTest,BundleMetadataTest,ProjectMetadataTest,JobResourcePreparationTest,FragmentCompilerTest,JobCompilerTest,ProjectCompilerTest,JobPackageBuilderTest,ProjectPackageIsolationTest,FragmentLoaderTest,IncludeSourceHashTest,ProjectHashTest,GroovyLoaderTest,GroovyProjectLoaderTest,ProjectScopeTest,GretlProjectParserTest,ApplyDirectiveReaderTest,FragmentGraphResolverTest,StaticSemanticsParityTest,ProjectSourceFrameTest,ProjectSourceLocationTest,CliFragmentProcessTest,CliHelpAndDefinitionTest,CliPathBasesTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Ergebnis der kumulativen Auswahl: 199 Fälle in 24 Suites, null Fehler/Skips. Nach Anpassung der Metadaten-Listengrenze an die bestehende maximale Source-/Scopekombination wurden die betroffenen Bundle-/Fragmentkomponenten nochmals gezielt geprüft; keine niedrigere Grenze für gültige SCRIPT-Abschlüsse.

Die JAR-Prozessprüfung paketiert echte Artefakte, entfernt den Quellbaum und startet java -jar aus leerem fremdem CWD. Sie prüft Marker/Laziness, exakten dekomprimierten Gzipinhalt, getrennte Shared-Scopes/Ressourcen/Cleanup und qualifizierte Resultate, Fragmentfehlerzeile, fehlende Fragmentklasse vor Rootregistrierung sowie alte interne Formate 1/2. Eine Test-Javaagent-Sperre ersetzt SourceUnit-/CompilationUnit-Konstruktoren und Compiler-/Parsemethoden durch AssertionError. Eine positive Kontrolle ruft den Compiler absichtlich auf und muss die Sperre auslösen. Alle eigentlichen JAR-Prozesse laufen mit aktivierter Sperre. Reines Klassenladen ist kein Compileraufruf: Groovy 6 FormatHelper lädt über ResolveVisitor Compilerklassensymbole für seine Laufzeitformatierung; dies wurde mit class+resolve-Log und javap nachgewiesen. Der erste, zu starke Klassenladecheck wurde deshalb durch die tatsächliche Aufrufsperre ersetzt.

Gezielte Metadaten-Negativfälle: unbekannte/gebrochene Versionen, Mischschema, falsche Rollen/Klassen/Namen, doppelte IDs, unsichere Quellen/Edges und fehlende Ziele. Builder-/Classpath-/Mehrscriptregressionen sind enthalten. Neue persistente SCRIPT-Probe für qualifizierte Fragmentresultate mit positiver dependsOn- und negativer after-Kontrolle erfüllt P2-MINOR F1; ProjectScopeTest ist enthalten. P2-MINOR F2: Rohlogs lagen tatsächlich direkt unter evidence/P2-tests.log und evidence/P2-cli.log; zusätzlich nach evidence/P2 kopiert. Der fremde P0-XML-Beleg dort liegt nun ausdrücklich unter historical-P0-not-counted. Originalreview unverändert.

Implementiererlogs und aktuelle XML-Kopien liegen ausserhalb des Branches unter `/Users/stefan/.cache/gretl-script-includes/evidence/P3*`. Reviewer führt die Auswahl selbst aus und verändert einen eigenen relevanten Paketinput bzw. entwickelt eine eigene ausgeführte Include-/JAR-Gegenprobe. Keine vollständigen Root-/Dienstgates, keine Veröffentlichung.
