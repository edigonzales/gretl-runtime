# Quellenbasis, Übernahmeentscheidungen und Ausgangsbefunde

**Recherche-/Paketstand: 2. November 2026.** Diese Datei unterscheidet beobachteten Quellstand, aus Code abgeleitete Risiken und neue Sollentscheidungen. Sie ist kein Testbericht einer bereits implementierten Lösung.

## 1. Tatsächliche Grundlage

Die Planung verwendet die in der vorangegangenen Analyse gelesenen LSP-/Runtimequellen und ergänzend erneut gelesene aktuelle Runtime-Repositoryregeln, aktive Dokumentation, Taskkonfigurations- und Graphimplementierung. Beim Paketbau wurden keine Runtime-/LSP-/GUItests ausgeführt und kein Repository verändert. Ein späterer Agent muss den tatsächlichen lokalen HEAD und Änderungen seit den Recherchepins prüfen.

| Quelle | Bezeichneter Stand | Verwendung |
|---|---|---|
| `edigonzales/gretl-runtime` | `17057baa32c8407a2c3a69f744c6686c8258f78f` | Zielcode, aktuelle API/DSL/Graph-/Buildverträge; main-Branchidentität nochmals gelesen. |
| `edigonzales/gretl-next` | `08bd11e97a54f32f8702d34ff16fb39bec6ea046` | Alter LSP als einmalige Quellreferenz; keine künftige Dependency. |
| `CODEX_STARTPROMPT(7).md` | vom Nutzer angehängte Datei | Implementierung statt Neuspezifikation, autonome Revieworchestrierung, echte Kandidaten-/Prozessidentität. |
| `EXECUTION_PLAN(7).md` | vom Nutzer angehängte Datei | Konkrete Musskriterien, Klassen-/Methodenverträge, echte GUIbelege, unabhängige Gates. |
| `OPENCODE_REVIEW_PROMPT(7).md` | vom Nutzer angehängte Datei | Eigene Ziel-/Codeprüfung, Gegenfälle, unveränderte Kandidaten, PASS/FAIL/BLOCKED. |

Die in der Vorlage zusätzlich genannten `FEATURE_AUDIT.md` und `GUI_TEST_MATRIX.md` wurden nicht mitgeliefert. Ihr Inhalt wird nicht als geprüft vorausgesetzt. Dieses Paket liefert eine eigene vollständige TEST_MATRIX und eigene 81 Kriterien.

Die früher genannte URL `sogis/gretl-runtime` war in der vorangegangenen Prüfung nicht abrufbar. Der Nutzer hat für diesen Auftrag ausdrücklich `edigonzales/gretl-runtime` bezeichnet; deshalb wird kein Umbenennen/Transfer angenommen.

## 2. Geprüfte Runtimeanker

Die folgenden Links beziehen sich auf den Recherchepin. In P0 gegen den aktuellen lokalen Stand abgleichen.

| ID | Quelle | Tragender Befund |
|---|---|---|
| R01 | [Root AGENTS.md](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/AGENTS.md) | JDK25, eigener Arbeitsbranch, Schutz fremder Änderungen und zwei vollständige Maven-Gates. |
| R02 | [Aktiver Dokumenteinstieg](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/docs/README.md) | Aktive Runtimeverträge getrennt von historischem Wissen; alte Sollkapitel sind kein neuer Auftrag. |
| R03 | [Runtimeverträge](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/docs/architecture/runtime-contracts.md) | Operations-SPI, späte Konfiguration, echte Datenabhängigkeit vs Reihenfolge, Toplevelrisiko beim Definitionsladen, Scope-/Pfad-/Secret-/Lokalisierungsregeln. |
| R04 | [GretlScript.java](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/GretlScript.java) | Aktuelle task/action/parameter/defaults/cleanup-Oberfläche, Headeroptionen und Verhalten der Defaults im Projektmodus. |
| R05 | [TaskConfigurationScope.java](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/TaskConfigurationScope.java) | Explizite Inputfelder, Defaults, reservierte Felder, Zuweisungspflicht statt Methoden-DSL, Resultzugriffe. |
| R06 | [OperationType.java](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/gretl-api/src/main/java/ch/so/agi/gretl/runtime/api/OperationType.java) | Metadaten und ausführbare Factories sind im Runtimeobjekt verbunden; ein Editor-Katalog muss einen reinen Datentransfer daraus ableiten. |
| R07 | [InputField.java](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/gretl-api/src/main/java/ch/so/agi/gretl/runtime/api/InputField.java) | Name, valueType, required, Default, allowedValues, Beispiel sowie ausführbarer convert/assign-Teil; kein vollständiges UI-JSONschema. |
| R08 | [ExecutionPlanner.java](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/gretl-runner/src/main/java/ch/so/agi/gretl/runtime/runner/ExecutionPlanner.java) | Vollständige dependsOn-Zyklusprüfung, Targethülle nur via dependsOn, after nur im ausgewählten Graph, stabile Toposortierung. |
| R09 | [GretlProjectParser.java](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/GretlProjectParser.java) | Begrenzter deklarativer Settings-AST, sichere Dateiprüfungen; dateibasierter Einstieg muss für Dirtybuffer sauber adaptiert werden. |
| R10 | [CompilerSettings.java](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/CompilerSettings.java) | Groovy-Runtimekonfiguration mit Java25-Ziel und deaktivierter Grape-Transformation; nicht als allgemeine Job-Sandbox verstehen. |
| R11 | [Aktuelle Referenz](https://github.com/edigonzales/gretl-runtime/blob/17057baa32c8407a2c3a69f744c6686c8258f78f/docs/reference/reference.adoc) | Recherchebestand 44 Taskarten, Java25/Groovy6, SCRIPTstart ohne Maven/Gradle, nicht gelieferte Backlogfunktionen getrennt. |
| R12 | [Scripts-Verzeichnis](https://github.com/edigonzales/gretl-runtime/tree/17057baa32c8407a2c3a69f744c6686c8258f78f/scripts) | Vorhandene Pythonharnesses, unter anderem Evidence-/Reviewwerkzeuge. Ein fremdes Pythonverbot wäre keine Erhaltung dieser Basis. |

Zusätzlich in P0 zu lesen: aktuelle Modul-/Build-/Test-/Subprojektanleitungen, `AbstractScope`, `ActionScope`, `CleanupScope`, `GroovyContextFacade`, aktuelle SQL-Operationsdefinitionen/Platzhalterverarbeitung, vorhandene Reviewstarter und Packagingaufgaben. Deren unvollständig gelesene Einzelheiten werden nicht durch erfundene Methodensignaturen oder alte Gradlefeldnamen ersetzt.

## 3. Alte LSP-Basis: Wiederverwendung und notwendige Korrekturen

Diese Befunde wurden aus Quelllektüre abgeleitet. Sie sind vor einer Quellübernahme durch neue gezielte Tests zu reproduzieren; ein bereits grün behaupteter Auditlauf existiert hier nicht.

| ID | Quelle | Befund / Entscheidung |
|---|---|---|
| N01 | [LSP-Build](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/build.gradle) | LSP4J/Jackson/Groovy3 als direkte Implementierungsdeps, Metadaten über Gradle-Docletpipeline. Technische Serverbasis brauchbar; Build-/Katalogpfad neu. |
| N02 | [TaskRegistrationExtractor](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/parser/TaskRegistrationExtractor.java) | Bestehender task-Pfad weist den zweiten positionellen Runtimeoperationstyp nicht passend zu. Neue DSL explizit extrahieren. |
| N03 | [GroovyAstGretlParser](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/parser/GroovyAstGretlParser.java) | defaultTasks statt defaults, erste Stringposition als Default, pauschale Fehlerposition, allgemeine CompilationUnit. Keine unveränderte Übernahme. |
| N04 | [HybridGretlScriptParser](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/scanner/HybridGretlScriptParser.java) | ASTprobleme können bei keinem AST-/Scannertask verloren gehen; eigene Recovery-/Problemzusammenführung nötig. |
| N05 | [LenientGretlScanner](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/scanner/LenientGretlScanner.java) | Regex über Rawtext, rohe Klammerzählung, indexOf-Feldpositionen und Literal-Inhaltsspans. Durch Tokenrecovery ersetzen. |
| N06 | [GretlTextDocumentService](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/server/GretlTextDocumentService.java) | Gute Providertrennung; synchrone Changeanalyse und nicht durchgängig versionsgesicherte CodeActions. Snapshot-/Schedulervertrag erneuern. |
| N07 | [GretlWorkspaceService](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/server/GretlWorkspaceService.java) | Overviewidee brauchbar, aber neue Analyse im Request, manuelle Maps, Config-/Filewatchernotifikationen nur geloggt. Getypte gemeinsame Snapshotdienste. |
| N08 | [LegacyDslRule](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/diagnostics/LegacyDslRule.java) | Mit alten Metadaten Migration von Zuweisung zu Methodenform – für Runtime falsche Richtung. Entfernen statt mit Altkompatibilität belasten. |
| N09 | [GretlCodeActionProvider](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/codeaction/GretlCodeActionProvider.java) | Dependencyfix ersetzt mit Quotes; in Kombination mit Scanner-Inhaltsspans beschädigbare Literale. Editpläne und angewendete Postimagetests nötig. |
| N10 | [SqlParameterRule](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/diagnostics/SqlParameterRule.java) | Workspace-root-basierte Pfade, direkte Diskreads, Analyse nur wenn beide alten SQLfelder vorhanden. Neue Script-/Buffer-/Metadatengrundlage. |
| N11 | [MetadataLoader](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/metadata/MetadataLoader.java) | Fehlende/inkompatible Metadaten fallen teils auf leeren Katalog zurück. Explizite Unavailable-/Incompatible-Zustände. |
| N12 | [VS-Code-Client](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/vscode/gretl-vscode/src/languageServer.ts) | Selector nur *.gradle. Client-/Startideen adaptierbar, aber neue Runtime-Dateierkennung und Packaging. |
| N13 | [LanguageServer](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/server/GretlLanguageServer.java) | Begrenzte Workspace-/Lifecyclebehandlung; wirklichen Prozessabschluss und Customfähigkeiten neu testen. |
| N14 | [Parser-Tests](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/test/java/ch/so/agi/gretl/lsp/parser/GroovyAstGretlParserTest.java) | Nützliche konkrete Grundtests, aber alte Syntax; keine Freigabe für neue Runtime-DSL oder sichere Mutation. |
| N15 | [Scanner-Tests](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-lsp/src/test/java/ch/so/agi/gretl/lsp/scanner/LenientGretlScannerTest.java) | Einige beschädigte Eingaben prüfen nur Taskauffinden; genaue Bereiche/angewendete Fixes zusätzlich erforderlich. |

Kein Prozentwert für „wiederverwendbarer Code“ wird behauptet. Wiederverwendung nach Verantwortung: Launcher/Provideraufteilung und Clientstruktur eher gut; Runtime-Semantik und Source-Editing brauchen gezielte Neubearbeitung. Architekturentscheidungen dieses Pakets sind begründeter Sollentwurf, keine aus dem alten Repo zitierte bereits vorhandene Lösung.

## 4. Aktuell geprüfte offizielle Werkzeugquellen

| ID | Primärquelle | Verwendung und Begrenzung |
|---|---|---|
| W01 | [OpenCode-2-CLI-Commands](https://dev.opencode.ai/v2/docs/cli/commands/) | Dokumentiert run, Modellwahl, JSONausgabe, Dateiargumente und für passende Befehle privaten Standalonemodus. Lokales `--help` bleibt verbindlich; keine Annahme der tatsächlichen installierten Version. |
| W02 | [DeepSeek-Modelldetails](https://api-docs.deepseek.com/quick_start/pricing/) | Beim Abruf Zuordnung `deepseek-flash` → DeepSeek-V4.1-Flash-1017 und direkte Base-URL. Alias kann später wechseln; daher Preflight statt unveränderlicher Modellbehauptung. Preise sind für diesen Auftrag nicht relevant. |
| W03 | [DeepSeek-API-Einstieg](https://api-docs.deepseek.com/quick_start/agent_integrations/) | Bestätigt API-Modellnamen/Host und Flash-1017-Zuordnung; kein Anlass, bestehende Credentials neu zu schreiben. |
| W04 | [VS Code Custom Editor](https://code.visualstudio.com/api/extension-guides/custom-editors) | Textbasierter Customeditor verwendet TextDocument/native Edit- und Save-Lebensdauer; passende Grundlage für Groovy als Source of Truth. |
| W05 | [VS Code Webview](https://code.visualstudio.com/api/extension-guides/webview) | Lokale Ressourcen, Message-/Lifecycle- und Sicherheitskonzepte für den Host. |
| W06 | [React Flow Custom Nodes](https://reactflow.dev/learn/customization/custom-nodes) | Unterstützt eigene interaktive Knoten; Wahl des Frameworks ist Sollentscheidung dieses Pakets, keine behauptete Kaoto-Abhängigkeit. |
| W07 | [LSP-Spezifikation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/) | Standardtransport/-features; konkrete LSP4J-/Clientversionen und Capabilityverhalten sind in P0/P4 zu pinnen und tatsächlich zu testen. |
| W08 | [VS Code API](https://code.visualstudio.com/api/references/vscode-api) | Native Edit-/Dokumentverträge. Nicht als Garantie einer globalen atomaren Transaktion gegenüber externen Dateisystemschreibern missverstehen. |

Es wurde weder auf die lokale OpenCode-Anmeldung noch die echte Benutzer-GUI zugegriffen. Die funktionierende autonome Integration muss der Coding-Agent auf dem tatsächlichen Rechner in P0 beweisen. Dieses Paket liefert den Auftrag und die Gateverträge, nicht einen fiktiven schon gelaufenen Review.

## 5. Neue Entscheidungen gegenüber der Vorlage

| Thema | Vorlage | Dieser Auftrag |
|---|---|---|
| Produkt | INTERLIS-Java plus eigenes Extensionrepo | Ein gretl-runtime-Monorepo, ein Kandidaten-SHA |
| Reihenfolge | Frühe Extension/UMLintegration | Zuerst Core P0–P3, dann LSP/Editor P4–P5, zuletzt Graph/Modeler P6–P8 |
| Diagramm | Sprotty/ELK, fachlich readonly | React Flow/ELK; P7 echte semantische Textbearbeitung |
| Python | Projektbezogen verboten, spezieller Guard | Bestehende Runtime-Pythonharnesses bleiben; neue Glue bevorzugt Node |
| Gates | Risikobasierte Auswahl plus dortige Regeln | Gezielte neue Checks **plus** tatsächlich geltende Runtime-Rootgates |
| Kandidat | Zwei Produkt-SHAs | Ein SHA plus Artefaktdigests/Requirementsdigest |
| Plattform | Drei Pflicht-GUIplattformen und Remote | Tatsächliche Primär-GUIplattform plus Linux-LSP-Prozess, übrige ehrlich ausweisen |
| Umfang | 60 Fähigkeiten, 56 ACs, 42 GUIcases | Eigene 81 ACs, 34 Prüfgruppen, 30 GUIcases |
| Laufzeit | Gebündelte Java25-Runtimes | Java25 extern zulässig; Server/Katalog/UI in VSIX, kein neues Native-/JREprojekt |

## 6. Noch durch den Implementierungsagenten festzulegen

P0 muss konkrete Dependencyversionen aus dem aktuellen Projekt pinnen, genaue aktuelle SQL-/Helperverträge lesen, vorhandene Reviewstarter sinnvoll auswählen und tatsächliche GUI-/Berechtigungsumgebung verifizieren. Diese Punkte sind explizite frühe Arbeiten, keine Lücken, die durch erfundene Fakten zu füllen wären.

Neue Klassen-/Methodensignaturen in ARCHITECTURE sind Sollschnittstellen. Bei einer bereits vorhandenen gleichwertigen Klasse genügt belegtes Mapping und nötige Erweiterung. Eine abweichende interne Klasseneinteilung ist zulässig, solange keine semantische/Modul-/Reviewgrenze abgeschwächt wird. Produktumfang und Musskriterien bleiben unverändert.
