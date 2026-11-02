# Unabhängiger Review P1 – GRETL Tooling

## 1. Identität

- Review-ID: `08277f1b-078b-45fc-bf93-6440ea89a73b`; Phase: `P1`.
- Kumulativer Umfang: die neun P1-Musskriterien `P1-01` bis `P1-09` sowie die betroffenen bereits gelieferten P0-Regressionen. Kein P8-/81-Kriterienlauf; die P8-Kumulativprüfung ist nicht Gegenstand dieses Auftrags.
- OpenCode: `2.2.5` (Binary selbst mit `--version` geprüft; `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/identity-check.json`).
- Effektiver Provider: `deepseek`; API-Modell: `deepseek-flash`; API-Host: `api.deepseek.com`; effektive Base-URL: `https://api.deepseek.com`; Modellversion: `DeepSeek-V4.1-Flash-1017`.
- Offizieller Aliasbeleg: `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/readiness/official-model-mapping.txt` und `identity.json` (Mapping-URL `https://api-docs.deepseek.com/quick_start/pricing/`; `mappingText` enthält `deepseek-flash` und `DeepSeek-V4.1-Flash-1017`). Der Request-`validateRequest`-Check gegen diese Datei war erfolgreich. Kein OpenRouter-/Go-/Zen-/LiteLLM-/Pro-Fallback ist konfiguriert; die effektive Produktprofilkonfiguration steht zusätzlich in `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/product/opencode.json`.
- `assignmentBase`: `185fc6700b5cb2897f724d457610a57f059b3421`; `phaseBase`: `9ad1ca801226066b3baec1d71c973ad8450615df`; `candidateSha`: `ac861d9deb1f623c33d0b38d31ab2030d24aec82`.
- Tatsächlicher Review-HEAD: `ac861d9deb1f623c33d0b38d31ab2030d24aec82` (vollständig, detached Reviewworktree). Beide Basisobjekte existieren; keine Null-SHAs. Der ursprüngliche Gateworktree `/Users/stefan/sources/gretl-runtime` hatte denselben HEAD und einen leeren tracked Status.
- `requirementsDigest`: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`; aus den normativen Dateien selbst nachgerechnet und identisch mit dem Request.
- `repositoryRulesDigest`: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`; ebenfalls nachgerechnet und identisch.
- Tracked Zustand vorher: Reviewworktree tracked leer; nur Harness-Dateien `.opencode/` und `opencode.json` untracked. Nachher: tracked weiterhin leer; dieselben Harness-Dateien untracked. Keine Produktquelle, kein Test, kein Lock, kein Schema und keine Spezifikation wurde geändert.
- Artefakte mit geprüften SHA-256:
  - `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/artifacts/gretl-language-contract-6.0.0-SNAPSHOT.jar` = `1c6ded4cc4a0ded664442319d1d62bb4fffe23bc3643cabb014215f48dacec5e`
  - `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/artifacts/gretl-tooling-core-6.0.0-SNAPSHOT.jar` = `d32f6d4ec957dff2fc2f888c3f4c0715f7712686014811895b35a7a4759295c5`
  - `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/artifacts/gretl-tooling-catalog-6.0.0-SNAPSHOT-catalog.jar` = `315d053bb219f1a661084ec71fa3a54279ffaf51f07f520b96abcb717d569a67`
  - `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/artifacts/operations.json` = `047a7131e9ce4706c5073a8600f38dbe35818623616d9a0025f405a1ddc0d911`
  - `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/artifacts/operations.schema.json` = `356d7e9a6b4fa0587a539c6a545e95ba278793c4f8e6cb13b305787912669005`
  - `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/artifacts/catalog-manifest.json` = `badfb00405776d9a977005d54d9f33f76cf5ee4242f72d9cb6b1b75ea217d047`
- Umgebung: macOS 27.0.1 (aarch64), Java 25.0.2 (Eclipse Temurin), Maven 3.10.0 über Wrapper, Groovy 6.0.0, Node 22.23.1. VS Code/WebDriver: für P1 nicht fällig und nicht verwendet. Docker wird für das ursprüngliche Dienstprofil benötigt; der Dienstgatebeleg wurde mit 794 Tests validiert.
- Gatebelege: `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/p1-depth-standard/manifest.json` und `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/p1-depth-services/manifest.json`; beide SHA-`ac861d9d…`, `dirty=""`, Exit 0, 0 Fehler/Skips, 742 bzw. 794 Tests. Alle 569 bzw. 752 im Manifest erfassten Dateien sind in der Evidence vorhanden und SHA-256-identisch; die Original-Repos waren am Kandidaten tracked clean. Näheres in `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/evidence-verification.log`.
- Eigenes Digestmanifest der Review-/Test-/Gegenprobenbelege: `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/evidence-digests.json`.

## 2. Eigene Ziel-, Code- und Architekturprüfung

Gelesen wurden vollständig: `EXECUTION_PLAN.md`, `ARCHITECTURE.md`, `TEST_MATRIX.md`, `SOURCE_AUDIT.md`, `ACCEPTANCE_CRITERIA.json`, die Reviewanweisung, `request.json`, `implementation/P1.md`, Root-`AGENTS.md`, `docs/implementation/gretl-tooling/AGENTS.md`, das Verdict-/Request-Schema sowie `tools/gretl-tooling/review.mjs`.

Der gesamte Phasendiff `9ad1ca801226066b3baec1d71c973ad8450615df..ac861d9deb1f623c33d0b38d31ab2030d24aec82` umfasst 25 Dateien. Alle neuen Hauptklassen und Tests wurden gelesen, insbesondere `SourceText`, `LineIndex`, `ParserCoordinateMapper`, `TextSpan`, `SafeGroovySyntaxReader`, `GroovyTokenReader`, `LiteralDecoder`, `GretlAstExtractor`, `RecoveringDslParser`, `ParseResultMerger`, `GretlDocumentParser`, `SyntaxDepthGuard`, `SyntaxModel`, `TokenIndex`, `ParseOptions`, `CancellationToken` sowie `StaticParsingSecurityTest`.

Modul-/Classpathprüfung: `gretl-tooling-core` kompiliert gegen `gretl-language-contract`, die gepinnte Groovy-6-Parserbibliothek und Jackson; keine Runner-/Task-/Gradle-/LSP-/GeoTools-Abhängigkeit. `gretl-groovy` erhält nur eine **Testscope**-Dependency auf den Core für die Nichtausführungsgegenproben. `node tools/gretl-tooling/verify-dependencies.mjs --mode all` prüft die aufgelösten Produktionsgraphen und die tatsächlichen Archive; der Lauf war grün. Eine aktive Abhängigkeit auf eine andere GRETL-Variante wurde nicht gefunden.

Nichtausführung: `SafeGroovySyntaxReader` verwendet ausschliesslich `SourceUnit.parse/completePhase/nextPhase/convert`, keinen `CompilationUnit`-Compile, keine Scriptinstanz, keinen `Script.run`, keine Handler-/Builder-/Providerinitialisierung und keinen dynamischen Download. Der `GroovyClassLoader` hat den Platform-ClassLoader als Parent; Grape- und AST-Test-Transformationen sind deaktiviert. `StaticParsingSecurityTest` prüft Toplevelmarker, `@Grab`, `@ASTTest`, statische Initializer, Settings-Ausdruck, Loopback-Netzwerk, Workspace-Provider, globale Transformation und einen poisoned Context-ClassLoader mit echten Positivkontrollen. Der frische Lauf war grün. Das ist ein begrenzter reiner Parse-/Convertpfad, keine behauptete allgemeine Sandbox für spätere Jobausführung.

Source-/Snapshot-/Positionsverträge: `LineIndex` behandelt CRLF als einen Zeilenwechsel, BOM und Emoji bleiben UTF-16-original; `ParserCoordinateMapper` übersetzt Codepoint-Parserkoordinaten in UTF-16-Offsets; `LiteralDecoder` trennt Token-, Inhalts- und Escapebereiche; `TokenIndex` rekonstruiert den Originaltext lückenlos. Eine `indexOf`-Erstfundstellenheuristik gibt es im Parse-/Extraktionspfad nicht (nur EOL-Erkennung in `SourceText.preferredEol` und Zeilenabstände in der Recovery). `GretlDocumentParser` arbeitet auf unverändertem Text, mit expliziten `PARSE_LIMIT`-Zuständen bei Budget-/Rekursionsüberschreitung und `PARTIAL`-Coverage statt leerem Erfolg.

Architekturbeobachtung: Die Syntaxwerte sind neutral, aber `ValueExpression` unterscheidet `TaskConfigReference`/`SecretReference` nicht als eigene Typen; sie werden als `CallValue` mit Namen geführt. Für P1 ist das noch ausreichend, für die in der Architektur genannten späteren Verträge ist es eine optionale Präzisierung, kein P1-Blocker. Die gefundenen Muss-Probleme betreffen die Auswertung von Task-Options-Closures und die Literalgrenze; Details in §6/§7.

## 3. Jede fällige Muss-AC

| AC | Status | Code-/Test-/GUIbeleg | Finding |
|---|---|---|---|
| P1-01 | fulfilled | `StaticParsingSecurityTest` (3 Tests) im frischen `targeted.log`; `SafeGroovySyntaxReader.java`; `GroovyTokenReader.java`; Classpath-/Loopback-Positivkontrollen; `evidence-verification.log` für die Rootgates. Keine Ausführung im Parsepfad. | — |
| P1-02 | fulfilled | `GretlDocumentParserTest.currentDslMapFirstAllDefaultsActionsCleanupAndInnerInvocations` (Map-first, Parameter, Cleanup, mehrere Defaults); `ParserAdversarialTest.allCurrentRepositoryGroovyExamplesParseWithoutExecution` (alle 11 echten Groovyquellen); `GretlAstExtractor.extractDeclaration`. | — |
| P1-03 | **violated** | `GretlAstExtractor.body` erkennt `ctx.run` korrekt in Action-Bodies; die Options-Closure-Auswertung in `extractDeclaration` verwirft aber `parsedOption.invocations()`. Eigene Gegenprobe `P1-WHEN-INNER` ergibt 0 statt 1 innere Invocation. | F2 |
| P1-04 | fulfilled | `GroovyTokenReader` (Kommentar-Callback, String-/GString-Modi, vollständiges Ledger); `GretlDocumentParserTest.commentsQuoteKindsAndInterpolationsCannotInventRegistrations`; `RecoveryAndLiteralContractTest` für Quote-/Escape-Spans. | — |
| P1-05 | fulfilled | `RecoveringDslParser`, `ParseResultMerger`, `GretlDocumentParser`; `GretlDocumentParserTest.errorWithoutTasksSurvivesAndRecoveryKeepsClosedIslands` und `incompleteHeadsAndBodies…`; eigene gemischte Gegenprobe mit sicheren/unsicheren Inseln und echten Spans (PASS). | — |
| P1-06 | fulfilled | `ParseResultMerger.problems` dedupliziert über Code/Span; `GretlDocumentParserTest.errorWithout…` prüft echte Positionen und Duplikatfreiheit; `ParseBoundsAndCoordinatesTest.malformedNoTaskAndGStringCases…` prüft Fehler ohne Tasks. Ein zusätzlicher expliziter Zwei-Fehler-Merger-Test wäre eine sinnvolle Testschärfung (optional). | — |
| P1-07 | fulfilled | `SourceText`, `LineIndex`, `ParserCoordinateMapper`, `LiteralDecoder`; `GretlDocumentParserTest.utf16BomCrLfEmoji…` und `stringLiteralAndContentSpans…`; `ParseBoundsAndCoordinatesTest.realGroovyCodepointPositions…`; `RecoveryAndLiteralContractTest` (CR/CRLF/Repeated Fields). Keine `indexOf`-Erstfundstelle im Parsepfad. | — |
| P1-08 | **violated** | Top-Level-Dynamik/fremde Receiver/Shadowing werden in `GretlAstExtractor.extract` und `ParserAdversarialTest` konservativ behandelt. Für eine fremde Methode in einer Task-Options-Closure (`when: { c -> builder.check() }`) bleibt `Declaration.coverage().references()` jedoch `COMPLETE`; der `unknownReferences`-Signalwert von `parsedOption` wird nicht in die Declaration übernommen. Eigene Gegenprobe `P1-WHEN-FOREIGN`. | F1 |
| P1-09 | **violated** | Parser-/Security-/Recoverytests und beide Rootgates sind grün (targeted 130 Tests; root-standard 742; root-services 794; keine Skips). Die dokumentierte 65 536-UTF-16-Literal-/GStringgrenze wird bei einer **unterminierten** GString am Dateiende aber nicht durchgesetzt: 70 000 Zeichen ergeben nur `SYNTAX_ERROR`, kein `PARSE_LIMIT`. Eigene Gegenprobe `P1-GSTRING-LIMIT`. | F3 |

Alle in dieser Tabelle als Kurzname genannten Belegdateien sind mit absoluten lokalen Pfaden in `verdict.json` sowie in §4, §6 und §7 referenziert.

## 4. Tatsächliche Tests

| Gate/Fall | fällig warum | Kommando/cwd | Exit/Tests/Fehler/Skips | eigener Lauf oder verifizierter aktueller Beleg | Evidence |
|---|---|---|---|---|---|
| root-standard | Root-AGENTS §1.2; vollständiges Standardgate | `./mvnw -B clean verify`; `/Users/stefan/sources/gretl-runtime` (Originalgate am Kandidaten) | Exit 0; 742 Tests; 0 Fehler; 0 Skips | Verifizierter aktueller Beleg; Manifest SHA/Dirty/argv/Counts geprüft; alle 569 captured Dateien SHA-256-identisch; Original-HEAD/Status geprüft | `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/p1-depth-standard/manifest.json`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/p1-depth-standard/command.log`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/evidence-verification.log` |
| root-services | Root-AGENTS §1.2; GeoTools-/Backing-Services-Gate | `./mvnw -B -Pgeotools-probe,backing-services clean verify`; `/Users/stefan/sources/gretl-runtime` | Exit 0; 794 Tests; 0 Fehler; 0 Skips | Verifizierter aktueller Beleg; Manifest SHA/Dirty/argv/Counts geprüft; alle 752 captured Dateien SHA-256-identisch; Original-HEAD/Status geprüft | `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/p1-depth-services/manifest.json`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/p1-depth-services/command.log`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/evidence-verification.log` |
| tooling-targeted | Neue risikotragende Katalog-/Contract-/Parser-/Runtime-Tests | `./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-tooling-catalog,gretl-groovy -am verify`; `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/product` | Exit 0; 130 JUnit-Tests; 0 Fehler; 0 Skips; darin 31 Core-Tests, 3 `StaticParsingSecurityTest`, 28 Node-Gegenproben und die Docs-/Dependency-Checks | Eigener frischer Lauf | `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/targeted.log`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/targeted-summary.json` |
| tooling-docs | Exakte 81 Kriterien, Links, Requirementsdigest | `node tools/gretl-tooling/check-docs.mjs`; Reviewworktree | Exit 0; 81 exakt validiert | Eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/tooling-docs.log`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/tooling-checks.json` |
| tooling-dependencies | Aufgelöste Produktionsgraphen und tatsächliche Archive | `node tools/gretl-tooling/verify-dependencies.mjs --mode all`; Reviewworktree | Exit 0 | Eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/tooling-dependencies.log`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/tooling-checks.json` |
| eigene Gegenproben | P1-Pflicht: mindestens eine neue, risikotragende Gegenprobe | `javac`/`java` gegen die gebauten Core-Klassen; Evidence-Scratch | Exit 1 absichtlich: gemischte Inselprobe PASS; drei Befundproben FAIL (erwartetes Verhalten nicht erreicht) | Eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/counterprobe-run.log`, `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/counterprobe/Counterprobe.java` |

Die vollständigen Rootgates wurden nicht doppelt neu gestartet. Der Auftrag erlaubt die unabhängige Validierung aktueller Rohbelege desselben Kandidaten; genau das wurde getan: Manifestidentität, argv, Exit, Counts, Dirtyzustand, Original-HEAD/Status und **jede** im Manifest erfasste Datei wurden geprüft. Zusätzlich wurden die P1-relevanten Tests im Reviewworktree frisch ausgeführt. Es gibt keine abgeschwächten Assertions, keine `@Disabled` und keine stillen Skips.

## 5. Echte GUI und Sichtprüfung (ab P5)

Für P1 sind keine GUI-Fälle fällig (`requiredGuiCases` leer). Es wurde daher keine VS-Code-/Webview-GUI bedient und kein Screenshot als Ersatz für eine GUI-Prüfung verwendet. Das ist kein Blocker; die GUI-Pflicht beginnt erst mit P5.

## 6. Eigene neue Gegenproben

Alle Proben sind in `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/counterprobe/Counterprobe.java` implementiert und gegen die tatsächlich gebauten Kandidatenklassen ausgeführt; das Rohprotokoll ist `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/evidence/counterprobe-run.log`.

1. **P1-MIXED-ISLANDS** – Hypothese: Eine beschädigte Datei mit Kommentar-/Phantomtext, einer sicheren Task, einem `def broken = ?` und einer danach begonnenen, unterminierten Action liefert exakte sichere Inseln und keine Phantomdeklaration. Aktion: eigene Fixture mit Kommentar/`task`-Text, Emoji-freier Source, gemischten offenen/geschlossenen Bereichen; geprüft wurden `span`, `nameLiteral.token`, Feldwert-Span, `recovered`, `coverage.syntax`. Erwartung: `before` und `after` vorhanden, `phantom` nicht, `PARTIAL`-Syntax, echte Spans. Beobachtung: **PASS**; das Protokoll zeigt die erwarteten Bereiche. Neuheit: kombiniert bewusst Kommentar-Phantom, sichere Task, defekten Ausdruck und offene Action in einer Fixture; keine Kopie eines vorhandenen Tests.
2. **P1-WHEN-FOREIGN** – Hypothese: Ein fremder Receiver in einer `when`-Options-Closure muss die Referenz-Coverage der Declaration senken. Aktion: `task('a', Echo, when: { c -> def tool = new StringBuilder(); tool.append('x'); true }) { input='x' }` parsen. Erwartung: `Declaration.coverage().references() == PARTIAL`. Beobachtung: **FAIL**, tatsächlich `COMPLETE`; die Closure wird als `OpaqueExpression[CLOSURE]` geführt, ihr `unknownReferences`-Signal geht aber nicht in die Declaration-Coverage ein. Neuheit: nutzt die aktuelle `when`-Option und einen lokal gebundenen fremden Receiver; die vorhandenen Tests prüfen nur Action-Bodies (`custom(ctx)`) und bekannte `c.parameter(...)`.
3. **P1-WHEN-INNER** – Hypothese: `ctx.run(Echo) { ... }` in einer `when`-Options-Closure ist eine innere Invocation und keine Top-Level-DAGtask. Aktion: `task('a', Echo, when: { c -> c.run(Echo) { input='y' } }) { input='x' }` parsen. Erwartung: genau eine `InnerInvocation` mit Feld `input`. Beobachtung: **FAIL**, `declaration.invocations().size() == 0`; `parsedOption.invocations()` wird in `extractDeclaration` verworfen. Neuheit: die vorhandenen Tests prüfen `ctx.run` nur im Action-Body.
4. **P1-GSTRING-LIMIT** – Hypothese: Eine 70 000 Zeichen lange, unterminierte GString am Dateiende muss die dokumentierte 65 536-UTF-16-Literal-/GStringgrenze als `PARSE_LIMIT` melden. Aktion: `def s = "${` + 70 000 `x` parsen. Erwartung: `PARSE_LIMIT`. Beobachtung: **FAIL**, nur `SYNTAX_ERROR`; der `gstrings>0`-Abschlusszweig in `GroovyTokenReader` prüft `MAX_LITERAL_UTF16` nicht. Neuheit: die vorhandenen Budgettests verwenden terminierte Strings/GStrings; der malformed-unterminated-Fall ist nicht abgedeckt.

Die gemischte Inselprobe ist bestanden; die drei übrigen Proben sind absichtlich als erwartete-Verhalten-Assertions formuliert und belegen die Findings F1–F3. Sie ersetzen keine vorhandenen Tests, sondern prüfen nicht abgedeckte Pfade.

## 7. Findings/Blocker

### F1 – P1-08 – major – fremde Receiver in Task-Options-Closures werden nicht konservativ in die Coverage übernommen

- Quelle: `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/product/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/syntax/GretlAstExtractor.java`, Zeilen 74–80 (Options-Closure-Schleife) und 78–79 (Coverage-Berechnung).
- Reproduktion: `task('a', Echo, when: { c -> def tool = new StringBuilder(); tool.append('x'); true }) { input='x' }` mit `GretlDocumentParser.parse(SourceText.job(...))`; Gegenprobe `P1-WHEN-FOREIGN`.
- Erwartet: `Declaration.coverage().references() == PARTIAL`, weil `body(c,false)` für den lokal gebundenen fremden Receiver `tool.append('x')` `unknownReferences=true` erkennt.
- Tatsächlich: `COMPLETE`; `refs`/`properties` bleiben leer, das `unknownReferences`-Signal von `parsedOption` wird nicht OR-verknüpft.
- Erforderliche Korrektur: `parsedOption.unknownReferences()` (und analog unbekannte Referenzen aus Nicht-Closure-Optionswerten) in die Declaration-Coverage übernehmen; eine Regression mit fremdem Receiver in `when` ergänzen. Ohne Korrektur können spätere Bindung-/Editstufen eine unvollständige Referenzlage als vollständig behandeln.

### F2 – P1-03 – major – innere `ctx.run`-Aufrufe in Options-Closures gehen verloren

- Quelle: dieselbe Options-Closure-Schleife in `GretlAstExtractor.java` (Zeilen 74–80). `parsedOption.invocations()` wird nicht an `Declaration.invocations()` angehängt.
- Reproduktion: `task('a', Echo, when: { c -> c.run(Echo) { input='y' } }) { input='x' }`; Gegenprobe `P1-WHEN-INNER`.
- Erwartet: eine `InnerInvocation` (Operation `Echo`, Feld `input`), keine zusätzliche Top-Level-DAGtask.
- Tatsächlich: `declaration.invocations().size() == 0`; der Optionswert ist nur `OpaqueExpression[CLOSURE]`.
- Erforderliche Korrektur: `parsedOption.invocations()` (und sinnvolle zugehörige Coverage-/Local-Informationen) in die Declaration übernehmen; Regression mit `ctx.run` in `when` ergänzen. Der Runtime-Pfad `GroovyTaskAdapter.condition` evaluiert die Closure in einem `ActionScope`, in dem `ctx.run` verfügbar ist.

### F3 – P1-09 – minor – unterminierte GString umgeht die dokumentierte Literalgrenze

- Quelle: `/Users/stefan/.cache/gretl-tooling-review/P1-review-1/product/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/syntax/GroovyTokenReader.java`, Zeilen 46–48 (Limit nur für terminierte Token) und 65 (unterminierter `gstrings>0`-Abschluss ohne Längenprüfung).
- Reproduktion: `def s = "${` + 70 000 `x`; Gegenprobe `P1-GSTRING-LIMIT`.
- Erwartet: `PARSE_LIMIT` und partielle/limitierte Analyse, entsprechend `MAX_LITERAL_UTF16=65_536` und der P1.md-Angabe.
- Tatsächlich: nur `SYNTAX_ERROR` am EOF; die 70 000 Zeichen werden als ein GString-Token geführt, ohne Literalbudget. Die globale 1-MiB-Sourcegrenze verhindert zwar eine unbegrenzte Analyse, aber die dokumentierte Literalgrenze wird nicht durchgesetzt und der bestehende `hugeNumbersAndLiterals`-Test deckt den malformed Fall nicht ab.
- Erforderliche Korrektur: Im `gstrings>0`-Abschlusszweig `span.length() > MAX_LITERAL_UTF16` prüfen, `limited=true` und `PARSE_LIMIT` setzen; Regression für die unterminierte Variante ergänzen.

### Optionale Verbesserungen (nicht die Basis des FAIL)

- `HELPERS` enthält `taskConfig`, obwohl der aktuelle Runtime-Scope nur `config(String)` anbietet (`AbstractScope`, `GroovyContextFacade`); `taskConfig` ist derzeit ein Konfigurationsschlüssel, keine Helper-Methode. Eine beabsichtigte Aliasunterstützung sollte gegen die aktuelle Runtime belegt oder entfernt werden.
- `ParseResultMerger` hat keinen direkten Zwei-Fehler-/Überlappungstest; die vorhandene Duplikatprüfung ist auf einer Einzelfehler-Fixture nur begrenzt wirksam. Eine explizite Merger-Regression wäre sinnvoll (P1-06-Testschärfung).
- Die Command-Syntax `task 'x', Echo { ... }` verliert in der AST-Extraktion den Body; die aktuellen Beispiele/Dokumentation verwenden die geklammerte Form. Nur falls Command-Syntax als aktuelle DSL gelten soll, ist das eine Lücke.
- `ValueExpression` bündelt `TaskConfigReference`/`SecretReference` als `CallValue`; für P2/P3 wäre die in der Architektur genannte explizite Typisierung präziser.

Blocker: keine. Identität, Modellpfad, Evidence und alle Pflichtgates waren erreichbar; es fehlt keine Pflichtprüfung.

## 8. Urteil

**FAIL.** Die Musskriterien P1-08 und P1-03 sind durch die reproduzierbaren Options-Closure-Lücken F1/F2 verletzt; P1-09 ist durch die nicht durchgesetzte Literalgrenze F3 verletzt. Alle fälligen Gates selbst sind grün (targeted 130, root-standard 742, root-services 794; Docs/Dependencies grün), und die gemischte Recovery-Gegenprobe besteht; das reicht aber nicht für ein PASS, weil konservative Coverage und innere Invocations an einer aktuellen DSL-Stelle falsch sind. Es gibt kein „PASS mit offenen Muss-Punkten“.

Nächster erlaubter Schritt: F1–F3 in derselben Phase korrigieren, dafür gezielte Regressionen ergänzen, einen neuen Kandidatencommit erzeugen, die vollständigen Rootgates erneut auf diesem Kandidaten ausführen und einen neuen unabhängigen P1-Review anfordern. P2 bleibt bis zu einem gültigen PASS gesperrt. Keine Publikation, kein Push/Merge/Release, keine produktiven Jobs.
