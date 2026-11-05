# P4: Kumulative Feature- und Toolingabnahme

Auftrag `script-includes`, Spezifikation 1.3. `assignment_base_sha`: `0bfc8f0fccf43989d751d4f5490202311d200628`; `tooling_base_sha`: `54bb123070fc7bd81aea58b4e1191beecf568cbf`; `base_status`: `MAIN_WITH_ACCEPTED_TOOLING`. Unveränderte `phase_base_sha` / `base_sha`: `9e3ea537fa212c91bd4cbbdebf55463105482a9e`. Kandidaten-SHA erst nach Commit extern erfassen.

**Finale Sollmenge: S01–S19 und C01–C08.** Kumulativen Produkt-/Zieldiff seit assignment_base_sha prüfen; phase_base_sha bleibt der P3-Pin für die Phasenidentität. Frühere Teilabnahmen nicht als automatische Schlussabnahme verwenden. Das Abschlusslabel RUNTIME_INCLUDES_WITH_SAFE_PARTIAL_TOOLING ist erst nach erfolgreicher externer Validierung zulässig.

P4 fügt kleine Core-/Wire-Kompatibilitätstests, einen reproduzierten Guardfix, Anwenderanleitung, Handoff und ein synthetisches IO-Beispiel hinzu. P0–P3 sind unabhängig angenommen; Originale liegen extern. Runtime, Pakete, Tooling und CWD-/Scope-/Sourceverträge müssen zusammen funktionieren. Kein produktiver Job, kein Push/Merge/Release.

## Konkrete Guardkorrektur

ApplyEditCompatibilityTest reproduziert fehlende Ablehnung für SetTaskOption(dependsOn/after), ConfigureTask-Batches und ResultFieldRef-SetField. Der bestehende EditabilityAnalyzer wendet nun die gleiche Referenz-/Graphcoverage auf gleichwirkende generische Zugänge an, auch auf verschachtelte Referenzwerte. Lokale skalare Felder bleiben bearbeitbar; CRLF, Unicode, Kommentare und Includezeile bleiben exakt erhalten. Keine neue Includeengine, keine Produktionsabhängigkeit auf Runtime im Tooling, kein Protokoll-/Katalogwechsel. Defaults behalten vorhandene Voraussetzungen. P3-MINOR zur Ressourcen-Laziness hat direkte persistente Verzeichnisprüfungen erhalten.

## Notwendige Nachweise am Kandidaten

Diese Datei ist ein neutraler Auftrag vor dem endgültigen Artefaktbuild, keine vorweggenommene GUI-/Reviewabnahme. Externer Handoff nennt tatsächliche Befehle, Exitcodes, Kandidat, Artefaktdigests und aktuelle Rohberichte. Vor Abschluss müssen vorhanden und unabhängig geprüft sein:

- Gezielte aktuelle SCRIPT-/JAR-/Legacyregressionen; vier echte SCRIPT-CLI-Prozesse, quellenfreie Single-/Project-JARs, Compilertripwire, bytegenauer IO-Vertrag.
- Core: ApplyPartialCoverageTest und ApplyEditCompatibilityTest, direkt betroffene Header-/Field-/Dependency-/Reference-/Defaults-/Formregressionen. C01–C03 und die neuen Guardumwege.
- Reeller frisch gebauter stdio-Server: ApplyIncludeServerIT prüft PARTIAL/DYNAMIC_CODE, lokale boolesche Änderung, zehn Referenz-/Graph-/Formablehnungen, validateEdit und Staleplan nach Include-Literaländerung, ohne Executionmarker oder Dateischreibungen. Vorhandene unabhängige Modul-/Archivprüfungen prüfen C06.
- Kanonisches Beispiel mit finaler Distribution als SCRIPT und Job-JAR aus fremdem CWD nach Entfernen der Quellkopie: bytegenauer Ausgabevergleich und einmaliger Cleanup, Hilfe/Check/Plan/Run sowie JAR-Hilfe/Plan/Run.
- C05: Aus genau diesem Kandidaten gebauter und im isolierten VS-Code-Profil installierter VSIX. Nativer zusammenhängender UI-Ablauf mit Screenshot-/Bufferbelegen für PARTIAL/Codeinsel, sicheren lokalen Feldedit, nachvollziehbare unsichere Ablehnung, Layout ohne Groovyänderung, Save/Undo/Redo und Marker-/Byteprüfung. Read-only Testdriver darf nur ergänzen. Kein Node-/DOM-Mock als Ersatz und kein erneutes G01–G30-Gate.
- Docs: passende lokale Link-/Codeblock-/Renderprüfungen und echte Beispielkommandos. Unveränderte Auftragsdatei enthält drei Codepfadabkürzungen mit `...`; dies ist im strikten Gesamtlinkchecker als verbleibender Inputbefund ausgewiesen. Keine Requirements-/Checkeränderung für ein grünes Label.

Ausgewählte Befehlsformen (exakte Auswahl/Ergebnisse im externen Handoff):

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-cli -am -Dtest='FragmentJarProcessTest,BundleMetadataTest,ProjectMetadataTest,JobResourcePreparationTest,FragmentCompilerTest,JobCompilerTest,ProjectCompilerTest,JobPackageBuilderTest,ProjectPackageIsolationTest,FragmentLoaderTest,IncludeSourceHashTest,ProjectHashTest,GroovyLoaderTest,GroovyProjectLoaderTest,ProjectScopeTest,GretlProjectParserTest,ApplyDirectiveReaderTest,FragmentGraphResolverTest,StaticSemanticsParityTest,ProjectSourceFrameTest,ProjectSourceLocationTest,CliFragmentProcessTest,CliHelpAndDefinitionTest,CliPathBasesTest,ApplyPartialCoverageTest,ApplyEditCompatibilityTest,SemanticEditPlannerTest,DependencySourceEditsTest,ReferenceSourceEditsTest,GraphFormProjectorTest,HeaderSourceEditsTest,DefaultsSourceEditsTest' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-lsp -am -Dtest='ApplyEditCompatibilityTest,ApplyPartialCoverageTest' -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ApplyIncludeServerIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Der Reviewer führt relevante Core-/Wiretests selbst aus und entwickelt eine neue Interaktionsgegenprobe. Identische aktuelle GUI-Rohbelege darf er unabhängig nachprüfen; ein zusätzlicher GUIlanglauf ist ohne GUI-/Hostfix nicht Pflicht. Fehlende GUI-/Wirebelege sind required_not_executed und verhindern die Schlussabnahme. Kein Behaupten kontextsensitiver Fragmentinvalidierung, Vollgraph, Navigation oder Mehrdateien-Rename. IMAGE-Dateien sind unverändert; kein neuer Containerlauf oder geändertes Imagekonzept behauptet. Fremde Worktrees, historische Toolingbelege und tools/gretl-tooling/review.mjs bleiben geschützt.
