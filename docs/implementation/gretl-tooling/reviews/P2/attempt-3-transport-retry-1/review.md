# Unabhängiger Review P2 – GRETL Tooling

## 1. Identität

- Review-ID: `de87cf26-d27e-4669-8a17-89f2fba2f57c`; Phase: `P2`.
- Kumulativer Umfang: alle neun P2-Musskriterien `P2-01`–`P2-09`, die betroffenen P0-/P1-Regressionen und der P2-Diff `fc5ce83a75b81e405bcffdcc017a1b8311ce37c0..566bb3446456523e9a1bd47ccbec7cfc54df02f4`. Der Assignmentdiff `185fc6700b5cb2897f724d457610a57f059b3421..HEAD` wurde zur Einordnung der Consumer gelesen. Die kumulative 81-AC-Abnahme bleibt P8.
- OpenCode: `2.2.5` (Beleg `/Users/stefan/.cache/gretl-tooling-review/P2-review-3/evidence/readiness/identity.json`). Provider: `deepseek`; API-Modell: `deepseek-flash`; API-Host: `api.deepseek.com`; effektive Base-URL `https://api.deepseek.com` zusätzlich in `product/opencode.json` gepinnt. Offizieller Aliasbeleg: `https://api-docs.deepseek.com/quick_start/pricing/` mit `MODEL deepseek-flash` und `MODEL VERSION DeepSeek-V4.1-Flash-1017`; Rohbeleg `/Users/stefan/.cache/gretl-tooling-review/P2-review-3/evidence/readiness/official-model-mapping.txt`. **Ehrliche Grenze:** Das Readiness-Artefakt belegt Provider/Modell/Host und die offizielle Aliaszuordnung, nicht kryptografisch die Servergewichte; der äussere Prozessabschluss wird erst nach dieser Session vom Aufrufer geprüft. Keine Credentialdatei wurde gelesen oder exportiert.
- `assignmentBase`: `185fc6700b5cb2897f724d457610a57f059b3421` (Objekt vorhanden); `phaseBase`: `fc5ce83a75b81e405bcffdcc017a1b8311ce37c0` (Objekt vorhanden); `candidateSha`: `566bb3446456523e9a1bd47ccbec7cfc54df02f4`; tatsächlicher `git rev-parse HEAD` im Reviewworktree: identisch.
- Worktree: `/Users/stefan/.cache/gretl-tooling-review/P2-review-3-transport-retry-1/product`; Evidence: `/Users/stefan/.cache/gretl-tooling-review/P2-review-3-transport-retry-1/evidence`.
- `requirementsDigest`: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`; unabhängig aus Plan, Architektur, Testmatrix, Reviewprompt, AC-Registry und beiden Schemas nachgerechnet (`digests.mjs`), identisch.
- `repositoryRulesDigest`: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`; aus Root-AGENTS und Tooling-AGENTS unabhängig nachgerechnet, identisch.
- Tracked-Zustand vorher/nachher: nur die vorbestehenden untracked Sessiondateien `.opencode/` und `opencode.json`; `git diff` leer, keine getrackte Produkt-, Test-, Lock-, Schema- oder Spezifikationsänderung durch diesen Review.
- Artefakte (alle gegen Request-SHA-256 verifiziert):

| Artefakt | SHA-256 |
|---|---|
| `.../artifacts/gretl-language-contract/target/gretl-language-contract-6.0.0-SNAPSHOT.jar` | `48328a9a519e734f59d124c13e2a00592812a09221434ad61c80a9402a16feeb` |
| `.../artifacts/gretl-tooling-core/target/gretl-tooling-core-6.0.0-SNAPSHOT.jar` | `a7693bd86d7c4e98d12511b66720872ca03d55b12d94295870057ef6e6118e92` |
| `.../artifacts/gretl-language-contract/target/tooling-dependencies.json` | `f05b6a8cde9a42c5d79da723ec5628bdac0f91bdf04855c2c0d84f1193e30a1f` |
| `.../artifacts/gretl-tooling-core/target/tooling-dependencies.json` | `752c8cffcce0afffd428478b3fb6d4c042902b9015b6613da54059fbd0eb6fc7` |
| `.../artifacts/gretl-tooling-catalog/target/gretl-tooling-catalog-6.0.0-SNAPSHOT-catalog.jar` | `49cf8de03b1d4fd5781869f0c207c0bd3c9e77292dd7df1a7081c0e491658095` |
| `.../artifacts/gretl-tooling-catalog/target/catalog/operations.json` | `047a7131e9ce4706c5073a8600f38dbe35818623616d9a0025f405a1ddc0d911` |
| `.../artifacts/gretl-tooling-catalog/target/catalog/operations.schema.json` | `356d7e9a6b4fa0587a539c6a545e95ba278793c4f8e6cb13b305787912669005` |
| `.../artifacts/gretl-tooling-catalog/target/catalog/catalog-manifest.json` | `80baadb727aa0531d0d88386b90262c712c45142a75bea696623fe4719a20931` |

- Umgebung: macOS/darwin `27.0.1`, arm64; Java `25.0.2` (Temurin); Mavenwrapper `3.10.0`; Node `22.23.1`; Groovy `6.0.0`; VS Code/Webview/Driver sind in P2 nicht fällig. Beleg `env.mjs`.

## 2. Eigene Ziel-, Code- und Architekturprüfung

### Tatsächlich gelesene Bereiche/Consumer

- Vollständiger P2-Diff: `DocumentStore`, `SourceRepository`, `SourceView`, `SourceUris`, `FileSourceRepository`, `WorkspaceManager`, `AnalysisScheduler`, `ProjectResolver`, `SettingsDocumentParser`, `SqlSourceSyntax`, `EffectiveOptions`, `ExecutionRegions`, `SemanticModel`, `ProjectAnalyzer`, `DiagnosticMessages`, `SqlTemplateAnalyzer`, `PlanSemantics`, die Änderungen an `ExecutionPlanner`, `GretlAstExtractor`, `TaskConfigurationScope`, `DslVocabulary`, `source-adoption.json` sowie alle neuen/geänderten Tests.
- Unveränderte Consumer/Regressionen gelesen: `WorkspaceManagerTest`, `AnalysisSchedulerTest`, `DocumentStoreTest`, `SourceViewTest`, `FileSourceRepositoryTest`, `ProjectAnalyzerTest`, `PlanSemanticsTest`, `ExecutionPlannerTest`, `StaticSemanticsParityTest`, `StaticSettingsParityTest`, `StaticSqlTemplateParityTest`, `GretlScript`, `TaskConfigurationScope`, `GroovyContextFacade`, `JobDefinitionBuilder`, `ResultView`, `HttpDownloadOperation`, `CurlOperation`.
- Nicht erneut als Vollprüfung gelesen: historische Reviewberichte und STATUS-Redaktionsteile; der funktionale Phasendiff und die betroffenen Produkt-/Testdateien wurden gelesen.

### Modulgrenzen, keine Fremd-GRETL-Dependency, keine Ausführung

- `gretl-language-contract` hat produktiv nur JDK-Abhängigkeiten; `gretl-tooling-core` produktiv nur `groovy` 6.0.0, `gretl-language-contract` und Jackson 2.17.2. Der frische Produktionsgraph `tooling-dependencies.json` enthält keine Runner-, Groovy-Adapter-, Task-, LSP-, GeoTools- oder Fremd-GRETL-Dependency. `gretl-runner` bindet den Contract als Produktionsdependency; `gretl-groovy` und `gretl-database-tasks` binden Tooling ausschliesslich mit `test`-Scope ein. Keine aktive Altabhängigkeit, kein Gradle/Doclet, kein Sibling-/Git-/file-Link und kein dynamischer Download.
- Keine Ausführung beim Analysieren: `ProjectResolver`/`ProjectAnalyzer` verwenden `SafeGroovySyntaxReader`, `GretlDocumentParser`, `SettingsDocumentParser`, reine Katalogdaten und die eingefrorene `SourceView`; keine Builder-, Handler-, Converter-, Provider-, Datenbank- oder Secret-Aufrufe. Der SQL-Paritätstest verbietet DB-/Secret-/Ausführungszugriffe per Proxy. Meine eigene Analyse-Gegenprobe setzt nach `capture` einen IO-Verbotsmarker; der Analyzer hat ihn nicht verletzt.

### Source-/Snapshot-/Graph-/Verträge

- `DocumentStore` speichert unveränderliche `SourceText`-Stände mit Bufferinkarnation und Revision; mehrteilige Änderungen sind atomar und rollen bei Fehlern vollständig zurück. `SourceView` friert Buffer, Diskstände, Abwesenheit und Limits in einem Capture ein; Buffer-vor-Disk ist getestet.
- `WorkspaceManager` verbindet Serverinstanz, Projektgeneration, Root-Konfigurationsgeneration, Kataloggeneration und Quellenvektor; `publishIfCurrent` prüft Buffer und Diskstempel vor der Veröffentlichung erneut. Close/Reopen, externe Diskänderung, Settings-/Katalogwechsel, Rootentfernung und unabhängige Roots sind getestet. Die Grenze gegenüber externen Dateischreibern wird ehrlich nicht als globale Transaktion behauptet.
- `AnalysisScheduler` besitzt einen eigenen begrenzten Executor (kein Commonpool), überschreibt veraltete Tickets, prüft die Publikation erneut und beendet Queue/Worker bei Close.
- `SettingsDocumentParser` bildet die deklarative Settings-Allowlist auf dem sicheren Parser ab; Rootjob/Includes behalten die Runtime-Reihenfolge; Projektdefaults kommen aus Settings, Einzeljobdefaults werden nicht aufgewertet. Die Parität vergleicht Includes/Defaults/Fehlercodes mit dem echten `GretlProjectParser`.
- `PlanSemantics` ist eine reine Extraktion aus `ExecutionPlanner`: vollständige dependsOn-Zyklusprüfung vor Targetauswahl, `after` nur für ausgewählte Knoten, targetabhängige Mischzyklen, stabile Registrierungsreihenfolge; `after` gewährt keinen Datenzugriff. Der Runner behält UUID-/Referenzidentität und Fehlercodes.
- Semantik/Diagnosen: Tasks und Actions teilen den Task-Namensraum; Parameter und Cleanup bleiben getrennt; doppelte/mehrdeutige/dynamische Referenzen sind unterscheidbar; `result`/`resultOptional` verlangen die direkte/transitive dependsOn-Kette; Actionresultate erhalten kein erfundenes Schema; innere Invocationen werden nicht zu Top-Level-Tasks; Katalogausfall erzeugt keinen erfundenen `UNKNOWN_OPERATION`/`UNKNOWN_INPUT`.
- **Gefundene Lücke (F1):** Die Enum-Prüfung in `ProjectAnalyzer.validateFields` wendet `InputDescriptor.allowedValues` auf jeden String-Literalwert an. Für die aktuellen URI-Eingaben `HttpDownload.url` und `Curl.serverUrl` enthält der Katalog jedoch die erlaubten **Schemata** `["http","https"]`, nicht vollständige Literalwerte. Ein gültiger Literal-URL wird deshalb als `INPUT_ENUM`-ERROR gemeldet, obwohl der aktuelle Runtime-Converter ihn akzeptiert. Details unter F1.
- SQL/Paths: `sqlFiles` (DuckDb) und `transfers[].sqlFile` (Db2Db) werden jobrelativ gelesen; Dirty-Buffer gewinnt; `sqlParameters`/`sqlParameterSets` und die echte `${name}`-Substitutionsregel (inkl. Kommentaren/Strings) werden abgebildet; fehlende Map und dynamische Map sind unterscheidbar; `file`/`workPath`/`path`/externer Parameterpfad werden symbolisch getrennt; keine DB-/Secretzugriffe.
- Grenzen: 512 Quellen/4 Mio. UTF-16-Zeichen pro Capture, 16 Roots, 64 Projekte, 2 Worker/16 Queue/8 Snapshots, 2000 Symbole/16000 Referenzen/4000 Diagnosen, 1 MiB Datei (Settings 256 KiB), 16 MiB Policy-Obergrenze.

## 3. Jede fällige Muss-AC

| AC | erfüllt/verletzt/nicht prüfbar | Code-/Test-/GUIbeleg | Finding |
|---|---|---|---|
| P2-01 | erfüllt | `DocumentStore.java`, `SourceView.java`, `WorkspaceManager.java`, `AnalysisScheduler.java`; `DocumentStoreTest`, `SourceViewTest`, `WorkspaceManagerTest`, `AnalysisSchedulerTest`; Rootbelege 809/865 | – |
| P2-02 | erfüllt | `ProjectResolver.java`, `SettingsDocumentParser.java`, `ProjectNameRules.java`; `WorkspaceManagerTest.projectResolution...`, `nestedWorkspaceOwnership...`; `StaticSettingsParityTest`; `ProjectAnalyzerTest.projectIncludes...` | – |
| P2-03 | erfüllt | `ProjectAnalyzer` Symboltabelle/`Resolution`; `ProjectAnalyzerTest.bindings...`, `duplicatesRemainAmbiguous...`; Namespaces, Alias, dynamische/mehrdeutige Fälle | – |
| P2-04 | erfüllt | `PlanSemantics.java`, `ExecutionPlanner.java`; `PlanSemanticsTest`, `StaticSemanticsParityTest`, `ExecutionPlannerTest`; eigene transitive Mischzyklusprobe | – |
| P2-05 | erfüllt | `ProjectAnalyzer.validateReferences`; `ProjectAnalyzerTest.afterNeverGrants...`; `StaticSemanticsParityTest.runtimeResults...`, `typedTaskRefs...` | – |
| P2-06 | **verletzt** | `ProjectAnalyzer.java` Zeile 130: `INPUT_ENUM` prüft `allowedValues` ohne Shape-/URI-Semantik; eigene Gegenprobe `P2CatalogEnumCounterprobe` mit echtem Katalog; Runtime-Kontrolle `P2HttpRuntimeControl` | **F1** |
| P2-07 | erfüllt | `SqlTemplateAnalyzer.java`, `SqlSourceSyntax.java`, `ProjectResolver.acquireSqlFiles`; `ProjectAnalyzerTest.sqlReads...`, `sqlSourcesWithEqualNames...`, `parameterSets...`, `db2dbTransfer...`; `StaticSqlTemplateParityTest` | – |
| P2-08 | erfüllt | `SemanticModel.Capabilities`, `ProjectAnalyzer` Coverage-/Budgetpfade, `AnalysisScheduler`, `DocumentStore`/`SourceView`-Limits; `ProjectAnalyzerTest.partialCatalogs...`, `guarded...`; Queue-/Budgettests | – |
| P2-09 | erfüllt | Eigener Zielauf: 430 Java + 32 Node, 0 Fehler/Skips; Rootstandard 809 und Rootservices 865 anhand der Rohbelege validiert; `check-docs.mjs` grün; `StaticSemanticsParityTest`, `StaticSettingsParityTest`, `StaticSqlTemplateParityTest` bestanden. F1 zeigt eine fehlende Abdeckung des echten URI-Felds; der Befund ist P2-06 zugeordnet. | – |

## 4. Tatsächliche Tests

| Gate/Fall | fällig warum | Kommando/cwd | Exit/Tests/Fehler/Skips | eigener Lauf oder verifizierter aktueller Beleg | Evidence |
|---|---|---|---|---|---|
| root-standard | Root-AGENTS §1.2, unverändert Pflicht | `./mvnw -B clean verify`, cwd `/Users/stefan/sources/gretl-runtime` | 0 / 809 / 0 / 0 | **nicht erneut ausgeführt**; frische Rohbelege unabhängig validiert | `/Users/stefan/.cache/gretl-tooling-review/P2-review-3/evidence/p2-cleanup-standard/manifest.json`, `.../command.log`, `validate-gates.mjs`, `validate-gates.log`, `validate-copies.mjs`, `validate-times.mjs`, `validate-suites.mjs` |
| root-services | Root-AGENTS §1.2, Dienste/GeoTools | `./mvnw -B -Pgeotools-probe,backing-services clean verify`, cwd `/Users/stefan/sources/gretl-runtime` | 0 / 865 / 0 / 0 | **nicht erneut ausgeführt**; frische Rohbelege unabhängig validiert | `/Users/stefan/.cache/gretl-tooling-review/P2-review-3/evidence/p2-cleanup-services/manifest.json`, `.../command.log`, `validate-gates.log` |
| tooling-targeted | betroffene Contract-/Core-/Groovy-/SQL-/Runner-Consumer | `./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-groovy,gretl-database-tasks -am verify`, cwd Reviewworktree | 0 / 430 Java + 32 Node / 0 / 0 | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-3-transport-retry-1/evidence/targeted-run/targeted.log`, `summary.json` |
| tooling-docs | AC-Registry/Links/Beispiele | `node tools/gretl-tooling/check-docs.mjs`, cwd Reviewworktree | 0 / 1 / 0 / 0 | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-3-transport-retry-1/evidence/docs-check/check-docs.log` |
| CP-P2-NEW | neue eigene Semantik-/Dirtybuffer-Gegenprobe | `node .../run-counterprobe.mjs` | 0 / 18 / 0 / 0 | eigener Lauf | `.../counterprobe/run.log`, `.../counterprobe/P2NewCounterprobe.java` |
| CP-P2-CATALOG-ENUM | neue eigene Katalog-/URI-Gegenprobe | `node .../run-enum-counterprobe.mjs` | 0 / 6 / 0 / 0 (Probe bestätigt den Produktfehler) | eigener Lauf | `.../counterprobe/enum-run.log`, `.../counterprobe/P2CatalogEnumCounterprobe.java` |
| CP-P2-HTTP-RUNTIME | Runtime-Kontrolle des URI-Converters | `node .../run-http-control.mjs` | 0 / 1 / 0 / 0; `url.convert=https://...` akzeptiert | eigener Lauf | `.../counterprobe/http-run.log`, `.../counterprobe/P2HttpRuntimeControl.java` |

**Rootbelege im Detail (unabhängige Validierung, keine Wiederholung):**

- Beide Manifeste nennen exakt `sha=566bb3446456523e9a1bd47ccbec7cfc54df02f4`, `dirty=""`, die exakten Request-argv; Exit 0, kein Signal. Der Kandidatencommit liegt mit `2026-11-03 09:14:01 +0100` (= 08:14:01Z) am Standardstart; der Dienststart folgt 08:23:25Z.
- Alle 587 (Standard) bzw. 772 (Services) Capture-Pfade existieren; ihre SHA-256 stimmen mit dem Manifest, und alle 151 bzw. 165 Report-`sourceModifiedAt` liegen im jeweiligen Start-/Endfenster. Die Surefire-/Failsafe-XML-Summen ergeben exakt 809 bzw. 865 Tests, 0 Failures, 0 Errors, 0 Skips; keine Null-Test-Suiten, keine doppelten Suiteklassen. Die Kopien unter `P2-review-3/evidence/p2-cleanup-standard|services` sind byte-identisch zu den Manifest-Hashes.
- Standard enthält 17 getestete Module und **kein** `gretl-geotools-probe`; Services enthält 18 Module inklusive `gretl-geotools-probe`. Der `prior-artifacts`-Baum im Standard-Evidence ist bewusst separat und wurde aus den 809 ausgeschlossen.
- `command.log` beider Läufe enthält `BUILD SUCCESS`, keinen `BUILD FAILURE`, keine `[ERROR]`-Zeile, keine Skip-Zeile und kein `-DskipTests`/`maven.test.skip`.
- Requirements-/Repository-Rules-Digest und alle acht Request-Artefakte wurden unabhängig nachgerechnet; alle stimmen.

**Rootpflichtgates und begründet nicht zusätzlich nötige Prüfungen:** Keine Wiederholung der beiden vollständigen Rootgates (Benutzerpräzisierung; Validierung der vorhandenen Rohbelege). Keine GUI in P2 (`requiredGuiCases=[]`). Keine Windows-/Mac-/Linux-GUIhardware in P2. Der betroffene Mavenlauf und die Dokuprüfung wurden selbst ausgeführt; die Rootbefehle waren im Reviewprofil bewusst nicht als zusätzliche Shellfreigabe hinterlegt.

## 5. Echte GUI und Sichtprüfung (ab P5)

In P2 nicht fällig. Es wurde keine Produkt-GUI gestartet und kein Screenshot als P2-Beleg verwendet. Die vorhandenen `readiness/gui-probe.png`-Dateien sind P0-Infrastruktur und kein P2-Produktbeleg.

## 6. Eigene neue Gegenproben

**CP-P2-NEW – transitiver targetabhängiger Mischzyklus plus Dirty-Settings/Dirty-SQL in zwei Subprojekten (neu):**

- Hypothese: Ein `after`-Mischzyklus darf nur dann als Planzyklus erscheinen, wenn der Zielpfad den transitiven dependsOn-Vorfahren wirklich einschliesst. Gleichnamige Tasks in zwei Subprojekten müssen getrennt bleiben; ein Dirty-Settings-Buffer muss den Diskstand vor einem Edit/Capture schlagen; ein Dirty-SQL-Buffer darf nur im zugehörigen Subprojekt diagnostiziert werden.
- Neue Fixture/Geste: Graph `a --DEPENDS_ON--> b --DEPENDS_ON--> c`, `c --AFTER--> a`, zwei unselected `after`-Knoten; Ziel `a` ist zyklenfrei, Ziel `c` zieht `b,a` transitiv ein und wird `PLAN_CYCLE`. Zusätzlich Projekt mit `:a`/`:b`, gleichen Tasknamen `run`, Settings-Diskstand mit zyklischem Default und Dirty-Settings-Buffer mit sicherem Default; Dirty-SQL `select ${onlyB}, ${missingB}` in `:b` gegen Disk `select ${diskOnly}`.
- Erwartung: Ziel `a` ohne Zyklus; Ziel `c` mit genau einem `PLAN_CYCLE`; `dataPredecessors(c)={a,b}`; getrennte `:a:run`/`:b:run`; nur `:b`-SQL fehlt; Settings-/SQL-Buffer gewinnen; nach Settings-Änderung wird der alte Capture ungültig und der neue Default aktiviert den Mischzyklus.
- Beobachtung: Alle 18 Prüfungen bestanden; `COUNTERPROBE PASS`. Beleg `.../counterprobe/run.log`, Quelle `.../counterprobe/P2NewCounterprobe.java` (SHA-256 `d7460ab06e286af4e5ae8c364f0a8dae82b04817d637ed1a51f1010e9f1e195a`).
- Warum nicht nur eine Kopie: Die vorhandenen Tests decken einen zweiknotigen Mischzyklus und gleiche SQL-Namen ab; diese Probe kombiniert einen **transitiven** Zielpfad, unselected after-Knoten, Dirty-Settings **und** Dirty-SQL in getrennten Subprojekten sowie die Invalidierung desselben Captures.

**CP-P2-CATALOG-ENUM – tatsächlicher Katalog, URI-`allowedValues` (neu, findet F1):**

- Hypothese: `HttpDownload.url = 'https://example.invalid/data.zip'` ist ein gültiger aktueller Runtimewert und darf keinen `INPUT_ENUM`-ERROR erzeugen, weil `allowedValues=["http","https"]` beim URI-Feld nur die erlaubten Schemata beschreibt.
- Neue Fixture/Geste: echten Katalog `operations.json` (SHA-256 `047a7131...`) über `CatalogJsonCodec`/`CatalogRepository` laden; Job `task('download', HttpDownload) { url='https://example.invalid/data.zip'; output=workPath('data.zip'); method='GET' }` über `WorkspaceManager`/`ProjectAnalyzer` analysieren.
- Erwartung: kein `INPUT_ENUM`; `url` ist zugewiesen, `output` ist zugewiesen, `method` ist ein gültiger Text-Enum-Wert.
- Beobachtung: `CatalogJsonCodec.State.AVAILABLE`; `Capabilities` vollständig; genau **ein** `INPUT_ENUM`-**ERROR** an `url` (`TextSpan[39,73]`), Meldung „Literal input is outside the allowed values.“; keine `REQUIRED_INPUT`/`UNKNOWN_INPUT`. Die Runtime-Kontrolle `P2HttpRuntimeControl` ruft `HttpDownloadOperation.type().inputs()` und den echten `url.convert()` auf: `url.convert=https://example.invalid/data.zip class=java.net.URI`; der Converter akzeptiert den Wert.
- Beleg: `.../counterprobe/enum-run.log` (SHA-256 `85c1e4ce...`), `.../counterprobe/P2CatalogEnumCounterprobe.java` (SHA-256 `618f76bf...`), `.../counterprobe/http-run.log` (SHA-256 `6003df22...`), `.../counterprobe/P2HttpRuntimeControl.java`.
- Warum nicht nur eine Kopie: Kein vorhandener P2-Test verwendet den echten Katalog mit `HttpDownload`/`Curl` und literalen URLs; die vorhandenen Fixtures prüfen nur synthetische `Echo`/`DuckDb`-Felder ohne URI-`allowedValues`.

## 7. Findings/Blocker

### F1 – Gültige URI-Literale werden als `INPUT_ENUM`-ERROR gemeldet (P2-06; major)

- Quelle: `/Users/stefan/.cache/gretl-tooling-review/P2-review-3-transport-retry-1/product/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/semantic/ProjectAnalyzer.java` Zeile 130: `INPUT_ENUM` wird für jeden String-Literalwert geprüft, sobald `InputDescriptor.allowedValues()` nicht leer ist. Der echte Katalog führt bei `HttpDownload.url` und `Curl.serverUrl` `shape.kind="Opaque"`, `runtimeType="uri"` und `allowedValues=["http","https"]`; diese Werte sind Schemata, keine vollständigen Literalwerte. Der Runtime-Converter `HttpDownloadOperation.uri(Object)` akzeptiert `https://example.invalid/data.zip` (`.../gretl-io-tasks/src/main/java/ch/so/agi/gretl/tasks/io/http/HttpDownloadOperation.java` Zeilen 116–131); `CurlOperation.uri` analog.
- Reproduktion: `node /Users/stefan/.cache/gretl-tooling-review/P2-review-3-transport-retry-1/evidence/run-enum-counterprobe.mjs`; die Probe lädt `P2-review-3/evidence/artifacts/gretl-tooling-catalog/target/catalog/operations.json` (State `AVAILABLE`) und analysiert den genannten Job. Kontrolle: `node .../run-http-control.mjs` ruft den echten Converter auf und akzeptiert dieselbe URL.
- Erwartet: Für ein `Opaque`/`uri`-Feld darf `allowedValues` nicht als vollständige Literal-Enummenge verwendet werden; ein vollständiger `https`-URL darf keinen `INPUT_ENUM`-ERROR erzeugen. Bei echten Text-Enums (`compression='ZIP'`, `method='GET'`) bleibt die Prüfung zulässig.
- Tatsächlich: `ProjectAnalyzer` liefert `INPUT_ENUM` ERROR an `url`; `Capabilities` meldet `declarationsComplete=true, referencesComplete=true, graphComplete=true, catalogAvailable=true, bounded=true, fieldAssignmentsComplete=true, executionComplete=true`. `HttpDownload` ist eine aktuelle Hauptoperation und wird im P7-Zieldemopfad verwendet; der Fehler wäre ab P5 als falsche Problems-Anzeige sichtbar.
- Erforderliche Korrektur: `INPUT_ENUM` nur für Eingaben anwenden, deren `ValueShape` tatsächlich eine Text-/Enum-Wertemenge beschreibt, oder bei URI-Feldern ausschliesslich das Schema prüfen; `Curl.serverUrl` gleich behandeln. Regressionstest mit dem **echten** Katalog (HttpDownload/Curl) und Runtime-Converter-Parität ergänzen. Keine Produktkorrektur durch den Reviewer.
- Schwere: major (falsche semantische Aussage, kein Datenverlust, keine Ausführung, keine unsichere Editierung).

### Optionale Beobachtungen (kein Muss-Finding)

- O1: `DiagnosticMessages` enthält für die `PROJECT_*`-Codes keine DE/EN-Texte; sie erscheinen als Rohcodes. Für P2 nicht als Pflichtfinding gewertet, aber eine sichtbare UI-Lücke für spätere Phasen.
- O2: `DuckDbSqlExecutor` verlangt zur Laufzeit eine `.sql`-Endung; der statische SQL-Lesepfad akzeptiert eine explizite `file()`-Quelle ohne Endung. Das ist eine bewusst dokumentierte Untererfassung, kein bewiesener Fehler.
- O3: Einige dynamische/arity-Fälle (z. B. `file()` ohne Argument, dynamische Funktionsargumente, `defaultValue:file('x')` bei required) werden konservativ nicht als Fehler behauptet; das ist Untererfassung, nicht Falschfehler. Die P2-ACs verlangen keine vollständige Runtime-Validierung jedes Helperarity-Falls.

## 8. Urteil

**FAIL.** P2-01 bis P2-05, P2-07, P2-08 und P2-09 sind durch Code, Tests und frische Rootbelege weitgehend gedeckt; die früheren Findings F1/F2 (attempt-1) und F3 (attempt-2) sind geschlossen, und die neue transitive Mischzyklus-/Dirtybuffer-Gegenprobe besteht. P2-06 ist jedoch verletzt: Für einen gültigen aktuellen `HttpDownload`-Job mit literalem `https`-URL meldet der Kern einen **bewiesenen** `INPUT_ENUM`-ERROR, obwohl der echte Runtime-Converter denselben Wert akzeptiert. Das ist eine falsche semantische Aussage des Kerns, nicht eine optionale Lintfrage.

Die Rootgates wurden wie beauftragt **nicht** erneut ausgeführt; ihre frischen Rohbelege (809/865 Tests, 0 Fehler/Skips, exakte SHAs/Hashes/Zeitfenster, byte-identische Kopien) wurden unabhängig validiert. Der betroffene 430-Java-/32-Node-Lauf und die Dokuprüfung wurden selbst ausgeführt. Alle eigenen Prozesse sind beendet; es liefen keine Hintergrundjobs.

**Nächster erlaubter Schritt:** F1 beheben (URI-`allowedValues` nicht als vollständige Literal-Enummenge behandeln), einen echten Katalog-/Runtime-Regressionstest für `HttpDownload.url` und `Curl.serverUrl` ergänzen, die fälligen Rootgates auf einem neuen Kandidaten frisch belegen und eine neue unabhängige P2-Review-Session starten. Keine Publikation, kein Push/Merge/Tag, keine produktiven Jobs; `phaseBase` bleibt unverändert.
