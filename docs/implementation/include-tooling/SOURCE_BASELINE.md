# Quellenbasis und Abgrenzung: Include-aware Tooling

**5. November 2026 · Begleitbericht zu Fassung 1.0**

Dieser Bericht dokumentiert den untersuchten Ausgangspunkt. Der verbindliche Implementierungsauftrag steht in [EXECUTION_PLAN.md](EXECUTION_PLAN.md), Einstieg in [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md), Zweitreview in [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md). Keine zusätzliche Kriterien- oder Reviewinstanz.

## 1. Referenzstand

| Bezug | Vollständiger SHA | Bedeutung |
|---|---|---|
| Erneut abgefragter Mainkopf | `ae0a2630156280e0e7f6bebf2354884f555d8d7e` | Ausgangspunkt dieses Folgeplans; neuere lokale Deltas sind beim Start zu prüfen. |
| Abgeschlossener Runtime-Include-Produktkandidat | `6f21cc82fe83d675905744b2ece0fff052baa328` | Historischer Ausgangsnachweis, keine Abnahme der neuen IDEfunktionen. |
| Früherer Tooling-Produktkandidat | `54bb123070fc7bd81aea58b4e1191beecf568cbf` | Historische LSP-/Modelerbasis. |

Der Mainkopf wurde über die GitHub-Branch-API erneut gelesen. Die konkrete Codeanalyse baut auf den am selben Mainpin in dieser Unterhaltung gelesenen Runtime-/Toolingdateien auf; zusätzlich wurden SafeGroovySyntaxReader, GretlDocumentParser, EditVerifier, SourceRepository, GretlTextDocumentService, Queryverzeichnis und Repositoryregeln gezielt gelesen. Kein vollständiger Codeaudit und keine Wiederholung der archivierten Test-/Reviewläufe.

## 2. Quellenbefund und neue Entscheidung getrennt

| Beobachteter Ausgangspunkt | Neue Anweisung dieses Auftrags |
|---|---|
| Runtime-ApplyDirectiveReader enthält genaue AST-/Literal-/Helper-/Shadowingregeln und Runtimefehlerabbildung. | Engen neutralen ASTinspector extrahieren; kompatible Runtimefassade und echte Parität erhalten. |
| Tooling-SafeGroovySyntaxReader nutzt Platform-Classloader, Parse/Convert, deaktivierte Transformationen und Recoverygrenzen. | Diese Hülle erhalten; nicht für Wiederverwendung durch einen Runtime-Loader ersetzen. |
| ProjectResolver lädt Einstiege/SQLs, aber noch keine transitive Includeclosure. | Sourceanwendungen/Includeabschluss zusätzlich erfassen; jobs() nicht mit Fragment-Pseudoprojekten füllen. |
| ProjectAnalyzer bindet bisher über Jobdateien; Aliase sind URIbezogen, Ressourcen werden aus Deklarationsdateien abgeleitet. | Anwendungen und Ressourcenbasis explizit modellieren; Shared-Scopes und lokale Bindings trennen. |
| SourceView/WorkspaceManager besitzen bereits Buffervorrang, Captures und Invalidierung. | Diese um vollständige Fragment- und Ownershipabhängigkeiten erweitern, nicht nochmals bauen. |
| EditabilityAnalyzer enthält nun Coverageguards auch für generische Setter/Batchreferenzen. | Guards erhalten; positive eindeutige Include-Edits erst mit Source-/Ownership-/Footprintbeweisen öffnen. |
| EditVerifier analysiert virtuelle Postimages aus einem eingefrorenen SourceRepository ohne neue IO. | Den Kontext-/Owner-/Inventarzustand dabei erhalten; keinen Standalonefallback für Fragmente zulassen. |
| LSP-TextDocumentService prüft Dokumentauswahl und dispatcht Standardqueries über einen URIbezogenen Einstieg. | Sourcekontext zentral resolve, bestätigte Fragmente synchronisieren, eindeutige Standardquerykontexte herstellen. |
| Extensionwatcher nennt bislang job.groovy/settings.gretl/*.sql und der Languageclient gretl-runtime. | Beliebig benannte echte Fragmente gezielt aufnehmen; geschlossene Dateiänderungen beobachten, keine globale Sprachübernahme. |
| Bestehende Modeler-/Edit-Hostkette validiert und wendet native Edits an. | Originalquellen/Kontexte und wirkliche Mehrdateien-Save-/Undoziele durchgängig transportieren. |
| Alten Include-Tests zufolge sollen gültige Includes noch partiell bleiben. | Diese Übergangserwartung durch echte positive statische Funktionen ablösen; negative dynamische/unsichere Fälle behalten. |

`gretl-script-syntax`, `ApplyAstInspector`, `SourceApplication`, `SourceOwnershipIndex`, `EditImpactAnalyzer`, `FragmentDocumentCoordinator` und die vorgeschlagenen Context-/Membershipendpunkte sind **neue Entwurfsentscheidungen**, keine behaupteten vorhandenen APIs. Bezeichnungen an den tatsächlichen Checkout anpassen, Verantwortungen und Verträge erhalten.

## 3. Reviewvorlagen und vorhandene Automation

Die Dateien des abgeschlossenen `script-includes`-Auftrags Fassung 1.3 wurden als Verfahrensvorlagen gelesen. Übernommen werden frischer Kontext, isolierter Worktree, Commit vor Review, genaue SHA-Bindung, Code- und Zielprüfung, eigene Gegenprobe und gezielte Tests. Der vorherige Aufgabenname, die Runtimeimplementierungsphasen und die dauerhafte Partialgrenze werden nicht übernommen.

Der gelesene `WORK_QUEUE.md` verweist auf lokale externe Include-Reviewbelege. Das aktuelle `tools/`-Listing enthält `gretl-tooling/`; ein `tools/script-includes/`-Verzeichnis war am Pin nicht vorhanden. Deshalb wird ein vorhandener generischer Featurelauncher nicht vorausgesetzt. Der bekannte `tools/gretl-tooling/review.mjs` ist an alte Kriterien und Gates gebunden. Lokale geeignete Mechanik kann nach Prüfung wiederverwendet werden; anderenfalls genügt ein kleiner neuer Featureadapter.

Die offizielle OpenCode-V2-Kommandoseite dokumentiert `run`, Modellwahl, JSONausgabe, Dateianhänge, Agentwahl und private Standaloneaufrufe; ihre lokale Verfügbarkeit ist zu prüfen. Die V2-Berechtigungsseite beschreibt geordnete `permissions`-Regeln und warnt vor V1-Feld-/Aktionsnamen. Die offizielle DeepSeek-Seite ordnete beim Abruf `deepseek-flash` der Version `DeepSeek-V4.1-Flash-1017` zu. Das ist eine datierte Aliaszuordnung, kein unveränderlicher Versionspin und kein Nachweis einer hier gestarteten Reviewsession.

## 4. Gepinnte Repositoryquellen

Die Links belegen den Ausgangsstand, nicht die Richtigkeit zukünftiger Änderungen.

- [Maincommit](https://github.com/edigonzales/gretl-runtime/commit/ae0a2630156280e0e7f6bebf2354884f555d8d7e)
- [Repositoryregeln](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/AGENTS.md)
- [Runtime-Includeanleitung](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/docs/implementation/script-includes/README.md)
- [Tooling-Handoff](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/docs/implementation/script-includes/TOOLING_HANDOFF.md)
- [ApplyDirectiveReader](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/fragment/ApplyDirectiveReader.java)
- [FragmentGraphResolver](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/fragment/FragmentGraphResolver.java)
- [GretlDocumentParser](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/syntax/GretlDocumentParser.java)
- [SafeGroovySyntaxReader](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/syntax/SafeGroovySyntaxReader.java)
- [ProjectResolver](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/workspace/ProjectResolver.java)
- [SourceRepository](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/workspace/SourceRepository.java)
- [SourceView](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/workspace/SourceView.java)
- [WorkspaceManager](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/workspace/WorkspaceManager.java)
- [ProjectAnalyzer](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/semantic/ProjectAnalyzer.java)
- [Queryklassen](https://github.com/edigonzales/gretl-runtime/tree/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/query)
- [EditabilityAnalyzer](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/edit/EditabilityAnalyzer.java)
- [EditVerifier](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/edit/EditVerifier.java)
- [ApplyEditCompatibilityTest](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-tooling-core/src/test/java/ch/so/agi/gretl/tooling/edit/ApplyEditCompatibilityTest.java)
- [GretlTextDocumentService](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/GretlTextDocumentService.java)
- [GretlProtocol](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/GretlProtocol.java)
- [Extensionhost](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/vscode/gretl-vscode/src/extension.ts)
- [Modelerprovider](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/vscode/gretl-vscode/src/modeler/provider.ts)
- [Native Editkette](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/vscode/gretl-vscode/src/modeler/edit-plan.ts)
- [Alter Toolingreviewer](https://github.com/edigonzales/gretl-runtime/blob/ae0a2630156280e0e7f6bebf2354884f555d8d7e/tools/gretl-tooling/review.mjs)

## 5. Externe Primärquellen

- [OpenCode 2 Commands](https://opencode.ai/v2/docs/cli/commands/)
- [OpenCode 2 Permissions](https://opencode.ai/v2/docs/permissions/)
- [DeepSeek Modellzuordnung](https://api-docs.deepseek.com/quick_start/pricing/)
- [VS Code Language Server Extension Guide](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide)
- [VS Code API](https://code.visualstudio.com/api/references/vscode-api)
- [LSP-Dokumentationsübersicht 3.17](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)

## 6. Aussagegrenze

Die hier erstellten Markdown-Dateien und die ZIP-Verpackung werden auf interne Konsistenz geprüft. Keine neuen Produkt-, LSP-, Runtime-, JAR- oder GUItests wurden in dieser Recherche ausgeführt. Kein echter zweiter Agent wurde für eine Produktabnahme gestartet. Die unabhängige Ausführung und alle Phasengates sind der ausdrücklich beauftragte nächste Implementierungsprozess.
