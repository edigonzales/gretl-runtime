# Unabhängiger Review P2 – GRETL Tooling

## 1. Identität

- Review-ID: `5963b173-e655-4ddc-8093-57c3a005a786`; Phase: `P2`.
- Umfang: alle neun P2-Musskriterien P2-01–P2-09, die betroffenen P1/P0-Regressionen und der vollständige Phasendiff `fc5ce83a75b81e405bcffdcc017a1b8311ce37c0..40a0261ba613104430a26a3fc3761ef299ef1737` (40 Dateien, 2049 Einfügungen/258 Löschungen). Der Assignmentdiff `185fc6700b5cb2897f724d457610a57f059b3421..HEAD` (115 Dateien) wurde zur Einordnung der kumulativen Consumer gelesen; die kumulative 81-AC-Abnahme bleibt P8.
- OpenCode: `2.2.5` (Beleg: `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/readiness/identity.json`, `process.json`).
- Provider/Modell/Host: `deepseek` / `deepseek-flash` / `api.deepseek.com`; Aliasbeleg `https://api-docs.deepseek.com/quick_start/pricing/` mit `DeepSeek-V4.1-Flash-1017` in `identity.json` (`checkedAt` 2026-11-02T17:04:31.132Z, weniger als 24 h vor diesem Lauf). Der Lauf verwendet `--standalone --agent gretl-independent-review --model deepseek/deepseek-flash`; die Projektkonfiguration pinnt `providers.deepseek.settings.baseURL=https://api.deepseek.com`. **Ehrliche Grenze:** ein separates P2-spezifisches Identitätsartefakt existiert nicht; das gelieferte Readiness-Artefakt ist noch frisch und wird hier als Beleg verwendet, nicht als kryptografischer Nachweis der Servergewichte.
- Assignmentbase: `185fc6700b5cb2897f724d457610a57f059b3421` (per `git rev-parse` vorhanden).
- Phasebase: `fc5ce83a75b81e405bcffdcc017a1b8311ce37c0` (per `git rev-parse` vorhanden).
- Kandidat: `40a0261ba613104430a26a3fc3761ef299ef1737`.
- Tatsächlicher HEAD im Reviewworktree: `40a0261ba613104430a26a3fc3761ef299ef1737`.
- Requirements-Digest: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3` (unabhängig nachgerechnet, identisch).
- Repository-Rules-Digest: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b` (unabhängig nachgerechnet, identisch).
- Tracked-Zustand vorher: sauber; nur die vorbestehenden untracked Harnessdateien `.opencode/` und `opencode.json`. Nachher identisch; `git status --porcelain=v1 --untracked-files=no` leer, `git diff --stat HEAD` leer. Beleg: `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/git-state-after.json`.
- Artefakte (alle gegen die Request-Digests verifiziert):
  - `gretl-language-contract-6.0.0-SNAPSHOT.jar` – `48328a9a519e734f59d124c13e2a00592812a09221434ad61c80a9402a16feeb`
  - `gretl-tooling-core-6.0.0-SNAPSHOT.jar` – `f39f713963459fb6c25f4dd7a6a2df6bb8953494bf75fee99df5a4485fdb4696`
  - `gretl-language-contract/target/tooling-dependencies.json` – `f05b6a8cde9a42c5d79da723ec5628bdac0f91bdf04855c2c0d84f1193e30a1f`
  - `gretl-tooling-core/target/tooling-dependencies.json` – `752c8cffcce0afffd428478b3fb6d4c042902b9015b6613da54059fbd0eb6fc7`
  - `gretl-tooling-catalog-6.0.0-SNAPSHOT-catalog.jar` – `4200992a6f4579547b98981ca87f23f4e7f73349d7c270d44763942b0a908cf2`
  - `catalog/operations.json` – `047a7131e9ce4706c5073a8600f38dbe35818623616d9a0025f405a1ddc0d911`
  - `catalog/operations.schema.json` – `356d7e9a6b4fa0587a539c6a545e95ba278793c4f8e6cb13b305787912669005`
  - `catalog/catalog-manifest.json` – `65c9ae3625965b106b5a01dfb28ad75e7eb69a3f3cbad7f996259e95051098f6`
- Umgebung: macOS/darwin 27.0.0, arm64, 10 CPUs; Java `25.0.2`; Groovy `6.0.0`; Node `22.23.1`; Mavenwrapper des Repositories. VS Code/Driver sind in P2 nicht fällig; Docker wurde für die gelieferten Rootbelege verwendet, nicht von diesem Reviewer gestartet.
- Der Kandidat ändert selbst `tools/gretl-tooling/review.mjs`/`review.test.mjs`, um die Benutzerpräzisierung (keine Wiederholung der Rootgates) im Reviewprofil durchzusetzen. Das ist konsistent mit dem Auftrag; die Rootbefehle wurden von mir nicht ausgeführt.

## 2. Eigene Ziel-, Code- und Architekturprüfung

Gelesen wurden insbesondere:

- P2-Diff vollständig: `DocumentStore`, `SourceRepository`, `SourceView`, `SourceUris`, `FileSourceRepository`, `WorkspaceManager`, `AnalysisScheduler`, `ProjectResolver`, `SettingsDocumentParser`, `SqlSourceSyntax`, `EffectiveOptions`, `SemanticModel`, `ProjectAnalyzer`, `DiagnosticMessages`, `SqlTemplateAnalyzer`, `PlanSemantics`, die Änderungen an `ExecutionPlanner`, `GretlAstExtractor`, `RecoveringDslParser`, `SourceText`, `TaskConfigurationScope`, `DslVocabulary`, `module-boundaries`, `source-adoption.json` sowie alle neuen/geänderten Tests.
- Unveränderte Consumer: `ExecutionPlannerTest`, `StaticSemanticsParityTest`, `StaticSettingsParityTest`, `StaticSqlTemplateParityTest`, `ProjectAnalyzerTest`, `WorkspaceManagerTest`, `AnalysisSchedulerTest`, `DocumentStoreTest`, `SourceViewTest`, `FileSourceRepositoryTest`, `PlanSemanticsTest`.
- Laufzeitgegenproben: `SequentialJobRunner` (when-Prüfung vor `body().execute`), `GroovyTaskAdapter.typedBody` (Konfiguration erst bei Ausführung), `TaskConfigurationScope.buildRequest` (required-Prüfung nach der Closure), `GretlScript` (task/parameter/defaults/cleanup), `JobDslModel` (Qualifizierung), `GretlProjectParser` (Settings/Entries), `Db2DbSupport` (Platzhalter/Substitution), `ParameterDefinition`.

Befunde der Strukturprüfung:

- **Modulgrenzen:** `gretl-language-contract` ist JDK-only; `gretl-tooling-core` hat produktiv nur Groovy 6.0.0, Language-Contract und Jackson 2.17.2. Der frische Dependencygraphenbeleg enthält keine Runner-/Groovy-Adapter-/Task-/LSP-/GeoTools-Dependency. `gretl-groovy`/`gretl-database-tasks` binden Tooling nur mit `test`-Scope ein; der Katalog bleibt Build-/Datenpfad. Keine andere GRETL-Variante, kein Gradle/Doclet, kein dynamischer Download.
- **Keine Ausführung beim Analysieren:** Der P2-Pfad nutzt `SafeGroovySyntaxReader`/`GretlDocumentParser` und reine Daten; `ProjectAnalyzer` ruft keine Builder/Handler/Converter, keine Datenbank, keine Secrets und keine Groovy-Auswertung auf. SQL wird nur als Text gelesen und mit dem echten `Db2DbSupport`-Regexmuster verglichen; der Paritätstest belegt die Übereinstimmung und verbietet DB-/Secret-Zugriffe per Proxy.
- **Snapshots:** `DocumentStore` speichert unveränderliche `SourceText`-Stände mit eigener Bufferinkarnation; `SourceView` friert Buffer, Diskstände und Abwesenheit ein. `WorkspaceManager` verbindet Serverinstanz, Projektgeneration, Root-Konfigurationsgeneration, Kataloggeneration und Quellenvektor; `publishIfCurrent` prüft vor Veröffentlichung erneut Buffer und Diskstempel. Close/Reopen und externe Diskänderung sind getestet; die Grenze gegenüber externen Schreibern wird ehrlich nicht als Transaktion behauptet.
- **Projekte/Defaults:** `settings.gretl` wird deklarativ geparst; Rootjob und Includes behalten die Runtime-Reihenfolge; ein verschachteltes Include erzeugt keinen Elternjob; Projektdefaults kommen nur aus Settings, Einzeljobdefaults werden nicht aufgewertet. Nested Roots werden als eigene Besitzgrenze sichtbar gemacht. Der Paritätstest vergleicht Includes/Defaults/Fehlercodes mit dem echten `GretlProjectParser`.
- **PlanSemantics:** Die Extraktion wurde Zeile für Zeile mit dem alten `ExecutionPlanner` verglichen. dependsOn-Zyklen werden global vor der Targetauswahl geprüft, `after` ordnet nur ausgewählte Knoten, Mischzyklen werden targetabhängig geprüft, die Toposortierung ist über den Registrierungsindex stabil, `after` gewährt keinen Datenzugriff. UUID-/Referenzidentität bleibt im Runner. Der neue iterative Zykluspfad vermeidet Rekursionstiefe; die Semantik der Kantenauswahl bleibt gleich. Eigene Gegenprobe CP1 bestätigt die Targetabhängigkeit zusätzlich.
- **Semantik/Diagnosen:** Tasks und Actions teilen den Task-Namensraum, Parameter und Cleanup bleiben getrennt; doppelte Definitionen bleiben sichtbar, mehrdeutige/dynamische/unaufgelöste Referenzen sind unterscheidbar; `result`/`resultOptional` verlangen die direkte/transitive dependsOn-Kette; Actionresultate erhalten kein erfundenes Schema; innere Invocationen werden nicht zu Top-Level-Tasks; Katalogausfall erzeugt keinen erfundenen UNKNOWN_OPERATION/UNKNOWN_INPUT. Partielle Quellcoverage setzt `Capabilities` und sperrt vollständige Aussagen.
- **SQL/Paths:** `sqlFiles` (DuckDb) und `transfers[].sqlFile` (Db2Db) werden jobrelativ gelesen; Dirty-Buffer gewinnt; `sqlParameters`/`sqlParameterSets` und die echte `${name}`-Substitutionsregel (inkl. Kommentare/Strings) werden abgebildet; fehlende Map und dynamische Map sind unterscheidbar; `file`/`workPath`/`path`/externer Parameterpfad werden symbolisch getrennt; keine DB-/Secretzugriffe.
- **Grenzen:** 512 Quellen/4 Mio. UTF-16-Zeichen pro Capture, 16 Roots, 64 Projekte, 2 Worker/16 Queue/8 Snapshots, 2000 Symbole/16000 Referenzen/4000 Diagnosen, 1 MiB Datei (Settings 256 KiB), 16 MiB Policy-Obergrenze. Diese Grenzen sind im Code und in den Tests sichtbar; eine vollständige Mutations-/Editprüfung ist P3.

## 3. Jede fällige Muss-AC

| AC | erfüllt/verletzt/nicht prüfbar | Code-/Test-/GUIbeleg | Finding |
|---|---|---|---|
| P2-01 | erfüllt | `DocumentStore`, `SourceView`, `WorkspaceManager`, `AnalysisScheduler`; `DocumentStoreTest`, `SourceViewTest`, `WorkspaceManagerTest`, `AnalysisSchedulerTest`; eigener Lauf 75 Core-Tests grün; Rootbelege 798/854 | – |
| P2-02 | erfüllt | `ProjectResolver`, `SettingsDocumentParser`, `ProjectNameRules`; `WorkspaceManagerTest`, `StaticSettingsParityTest`, `ProjectAnalyzerTest.projectIncludes...`; verschachtelte Roots/Defaults/Dirty-Settings geprüft | – |
| P2-03 | erfüllt | `ProjectAnalyzer` Symboltabelle/`Resolution`; `ProjectAnalyzerTest.bindings...`, `duplicatesRemainAmbiguous...`; Namespaces, Alias, dynamische/mehrdeutige Fälle | – |
| P2-04 | erfüllt | `PlanSemantics`, `ExecutionPlanner`-Adapter; `PlanSemanticsTest`, `StaticSemanticsParityTest`, `ExecutionPlannerTest` (191 Runner-Tests grün); eigene CP1 | – |
| P2-05 | erfüllt | `ProjectAnalyzer.validateReferences`/`UNKNOWN_OUTPUT`/`RESULT_DEPENDENCY_REQUIRED`; `ProjectAnalyzerTest.afterNeverGrants...`, `StaticSemanticsParityTest.runtimeResults...`; echte Runtime-Läufe im Hosttest | – |
| P2-06 | **verletzt** | `ProjectAnalyzer.validateFields` (Zeile 118–124) prüft Pflichtfelder unabhängig von `when` und behandelt `LiteralValue(null)` als zugewiesen; eigene statische und echte Runtime-Gegenproben CP3/CP4 | F1, F2 |
| P2-07 | erfüllt | `SqlSourceSyntax`, `SqlTemplateAnalyzer`, `ProjectResolver.acquireSqlFiles`; `ProjectAnalyzerTest.sqlReads...`, `sqlSourcesWithEqualNames...`, `db2dbTransferSql...`, `StaticSqlTemplateParityTest`; echte `Db2DbSupport.substitute`-Parität | – |
| P2-08 | erfüllt | `Capabilities`, `MAX_SYMBOLS`/`MAX_REFERENCES`/`MAX_DIAGNOSTICS`, `SourceView`-/Scheduler-/Workspace-Budgets; `ProjectAnalyzerTest.catalogOutage...`, `SourceViewTest`, `AnalysisSchedulerTest`; keine Verwechslung Katalogausfall/unbekannter Fachwert | – |
| P2-09 | erfüllt | Eigener betroffener Lauf: 419 Java + 32 Node, 0 Fehler/Skips; Rootstandard 798 und Rootservices 854 frisch belegt; `PlanSemanticsTest`, `StaticSemanticsParityTest`, `StaticSettingsParityTest`, `StaticSqlTemplateParityTest` bestanden. Die zusätzliche Parität ist für die Pflichtfeld-/`when`-Fälle nicht vollständig; das ist unter P2-06/F1/F2 erfasst. | – (Pflichtfeldparität siehe P2-06) |

## 4. Tatsächliche Tests

| Gate/Fall | fällig warum | Kommando/cwd | Exit/Tests/Fehler/Skips | eigener Lauf oder verifizierter aktueller Beleg | Evidence |
|---|---|---|---|---|---|
| root-standard | Root-AGENTS §1.2, unverändert Pflicht | `./mvnw -B clean verify`, cwd Produkt-Repo | 0 / 798 / 0 / 0 | **nicht erneut ausgeführt**; frische Rohbelege (Manifest, 587 Captures, Surefire-XML) unabhängig validiert | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/root-gate-validation.log`, `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/p2-map-standard/manifest.json`, `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/README.md` |
| root-services | Root-AGENTS §1.2, Dienste/GeoTools | `./mvnw -B -Pgeotools-probe,backing-services clean verify`, cwd Produkt-Repo | 0 / 854 / 0 / 0 | **nicht erneut ausgeführt**; frische Rohbelege (Manifest, 772 Captures, Surefire-XML) unabhängig validiert; Superset der 798 Standardsuiten + 56 zusätzliche (4 GeoTools + 52 IT) | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/p2-map-services/manifest.json`, `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/root-gate-validation.log` |
| tooling-targeted | betroffene Contract-/Core-/Groovy-/SQL-/Runner-Consumer | `./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-groovy,gretl-database-tasks -am verify`, cwd Reviewworktree | 0 / 419 Java + 32 Node / 0 / 0 | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/targeted-gate-summary.json`, `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/targeted-gate.log` |
| tooling-docs | AC-Registry/Links/Beispiele | `node tools/gretl-tooling/check-docs.mjs`, cwd Reviewworktree | 0 / „Validated 81 exact criteria“, Links gültig | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/docs-check.log` |
| provenance | Übernahme-/Extraktionsbelege | `node .../verify-provenance.mjs` | 0 / 4 Belege | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/provenance.log` |
| CP1 Mischzyklus | P2-04-Zielabhängigkeit | `node .../run-counterprobe.mjs` | 0 / 25 Einzelprüfungen | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/counterprobe-run.log` |
| CP2 Dirty-SQL-Subprojekte | P2-01/02/03/07 | dito | 0 / 18 Einzelprüfungen | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/counterprobe-run.log` |
| CP3/CP4 Required-Feld-Negativproben | P2-06, neu | `node .../run-static-when-probe.mjs`, `node .../run-runtime-when-probe.mjs` | 0 (Prozess), Beobachtung: statisch falsch/fehlend | eigener Lauf | `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/static-when-probe.log`, `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/runtime-when-probe.log` |

**Rootbelege im Detail (unabhängige Validierung, keine Wiederholung):**

- Beide Manifeste nennen exakt `sha=40a0261ba613104430a26a3fc3761ef299ef1737`, `dirty=""`, die exakten Request-argv und `cwd=/Users/stefan/sources/gretl-runtime`; Exit `0`, kein Signal.
- Alle 587 bzw. 772 Capture-Pfade existieren, ihre SHA-256 stimmen mit dem Manifest, und alle `sourceModifiedAt` liegen im jeweiligen Start-/Endfenster. Die Surefire-XML-Summen ergeben exakt 798 bzw. 854 Tests, 0 Failures, 0 Errors, 0 Skips; keine doppelten Suiteklassen, keine Null-Test-Suiten.
- Standard enthält 17 Module und **kein** `gretl-geotools-probe`; Services enthält 18 Module inklusive `gretl-geotools-probe`. Alle 798 Standardsuiten sind in Services unverändert vorhanden; die 56 zusätzlichen Tests sind 4 GeoTools-Probe + 52 IT-Fälle. Der Standard belegt zusätzlich 4 bewusst separat archivierte, außerhalb des Frischefensters liegende `prior-artifacts`-GeoToolsberichte; diese sind nicht in den 798 enthalten.
- `command.log` beider Läufe enthält `BUILD SUCCESS`, keinen `BUILD FAILURE`, keine fehlgeschlagene `Tests run:`-Zeile und keine Skip-Zeile; keine `-DskipTests`-/`maven.test.skip`-Abschwächung.
- Der Requirements-Digest und der Repository-Rules-Digest wurden mit demselben sortierten Datei-/Hashverfahren wie `review.mjs` nachgerechnet und stimmen. Alle acht Request-Artefakte wurden nachgehasht; alle stimmen.
- Die vollständigen Rohbelege liegen zusätzlich byte-identisch unter `/Users/stefan/.cache/gretl-tooling-review/p2-map-standard` und `/Users/stefan/.cache/gretl-tooling-review/p2-map-services`; die Kopien unter `P2-review-1/evidence` wurden gegen dieselben Hashes geprüft (587 + 772 Dateien, 0 Abweichungen; `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/root-copies.log`).

**Begründet nicht zusätzlich nötig:** keine Wiederholung der beiden vollständigen Rootgates (Benutzerpräzisierung; Validierung der vorhandenen Rohbelege). Keine GUI in P2 (`requiredGuiCases=[]`). Keine Windows-/Mac-/Linux-GUIhardware in P2. Der betroffene Mavenlauf und die Dokuprüfung wurden selbst ausgeführt.

## 5. Echte GUI und Sichtprüfung (ab P5)

In P2 nicht fällig. Es wurde keine Produkt-GUI gestartet und kein Screenshot als Beleg verwendet. Die vorhandenen `readiness/gui-probe.png`-Dateien sind P0-Infrastruktur und kein P2-Produktbeleg.

## 6. Eigene neue Gegenproben

**CP1 – targetabhängiger Mischzyklus (neu, nicht Kopie von `PlanSemanticsTest`):**
Graph `t1` (Index 0), `a` (1), `b` (2); Kanten `a --DEPENDS_ON--> t1`, `b --AFTER--> a`, `t1 --DEPENDS_ON--> b`. Erwartung: `validateDeclaredDependencies` ist leer (kein Datenzyklus), Auswahl nur `t1` liefert `[a,t1]` ohne aktiven AFTER-Knoten, Auswahl `t1,b` und Auswahl `b` liefern `PLAN_CYCLE` mit drei Kanten; `dataPredecessors(t1)={a}` (after gibt kein Datenrecht). Beobachtung: alle Assertions bestanden. Neu ist die dritte Targetkombination und die gleichzeitige Prüfung von Datenhülle, aktivem AFTER und Zyklusreport. Beleg: `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/counterprobe-run.log`.

**CP2 – Dirty-SQL in einem von zwei gleichnamigen Subprojekten (neu):**
Settings `include 'a','b'`, Rootjob mit `dependsOn:':a:sql'`, beide Subprojekte mit Task `sql` und `file('sql/update.sql')`, nur `a/sql/update.sql` als Dirty-Buffer mit `${dirtyOnly}`, `b/sql/update.sql` auf Disk mit `${diskOnly}`; `a`-Diskdatei enthält bewusst anderen Text. Erwartung: zwei getrennte `SQL_PARAMETER_MISSING`-Diagnosen, exakte `${...}`-Spans aus dem jeweiligen Text, Dirty-Buffer gewinnt, gleiche lokale Tasknamen lösen nur im eigenen Subprojekt auf, qualifizierte Rootreferenz trifft `:a:sql`; nach Dirty-Änderung an `a` wird der alte Capture ungültig und nur `b` meldet noch einen fehlenden Parameter. Beobachtung: alle Assertions bestanden (18 B-Prüfungen; zusammen mit CP1 25). Beleg: `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/counterprobe-run.log`.

**CP3 – Required-Feld bei `when` (neue negative Probe):**
Hypothese: Ein Task mit `when:{false}` bzw. `when:{parameter('flag')}` (Default `false`) und nicht gesetztem Pflichtfeld ist zur Laufzeit gültig, weil die Konfiguration erst bei Ausführung gebaut wird. Erwartung: keine „bewiesene“ REQUIRED_INPUT-Diagnose (oder mindestens eine ehrliche Teil-/Warnaussage). Beobachtung: Der echte Runtime-Harness liefert `SUCCEEDED`, Task `SKIPPED`, 0 Builder-/Handler-Aufrufe; der statische Analyzer liefert dagegen `REQUIRED_INPUT ERROR` bei `capabilities=...complete=true`. **Widerspruch bestätigt.** Belege: `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/runtime-when-probe.log`, `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/static-when-probe.log`.

**CP4 – Required-Feld mit Literal `null` (neue negative Probe):**
Hypothese: `task('t', RequiredText) { text = null }` ist statisch als ungültig erkennbar; die Laufzeit lehnt es ab. Erwartung: statische Diagnose (REQUIRED_INPUT oder INPUT_TYPE), passend zum Laufzeitfehler. Beobachtung: Laufzeit `FAILED` mit `INVALID_CONFIGURATION_VALUE`; statischer Analyzer liefert **keine** Diagnose (`REQUIRED_INPUT=false`, `capabilities=...complete=true`). **Widerspruch bestätigt.** Belege: `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/runtime-when-probe.log`, `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/evidence/static-when-probe.log`.

CP1/CP2 sind keine identischen Kopien vorhandener Tests (neue Graphform bzw. kombinierte Zwei-Subprojekt-/Dirty-/Qualified-/Invalidation-Fixture). CP3/CP4 sind neue negative Proben mit echtem Runtime-Harness und wurden nicht aus dem Produkt-Testbestand übernommen.

## 7. Findings/Blocker

### F1 – REQUIRED_INPUT wird bei `when`-Tasks als bewiesener Fehler ausgegeben (P2-06, major)

- Quelle: `/Users/stefan/.cache/gretl-tooling-review/P2-review-1/product/gretl-tooling-core/src/main/java/ch/so/agi/gretl/tooling/semantic/ProjectAnalyzer.java`, `validateFields` Zeilen 118–124: die Pflichtfeldprüfung läuft für jeden Task unabhängig von einem `when`-Header und unabhängig davon, ob die Bedingung statisch wahr ist.
- Reproduktion: `P2StaticWhenProbe` analysiert `task('t', RequiredText, when: { false }) { }; defaults 't'` sowie die parametergetriebene Variante mit `defaultValue:false`. `P2RuntimeWhenProbe` lädt und startet dieselben Fixtures über den echten `GroovyHarness`/`SequentialJobRunner`.
- Erwartet: keine REQUIRED_INPUT-ERROR-Aussage für einen Task, dessen `when`-Bedingung den Laufzeitkörper überspringt (oder eine ausdrücklich partielle/warnende Aussage).
- Tatsächlich: statisch `REQUIRED_INPUT ERROR`, `Capabilities[declarationsComplete=true,referencesComplete=true,graphComplete=true,...]`; Laufzeit `SUCCEEDED`, Task `SKIPPED`, 0 Builder-/Handler-Aufrufe. Der Laufzeitvertrag ist in `SequentialJobRunner` Zeilen 235–240 und `GroovyTaskAdapter.typedBody` sichtbar: die Konfiguration wird erst im Taskbody gebaut.
- Erforderliche Korrektur: `when`-Präsenz/-Bedingung in die Pflichtfeldentscheidung einbeziehen; mindestens bei nicht statisch bewiesener Bedingung keine ERROR-Diagnose, sondern Partial-/Warnstatus; RED-Test für Literal-false und parametergetriebene Bedingung gegen den Runtime-Harness. Keine Produktkorrektur durch den Reviewer.

### F2 – Literal `null` erfüllt Pflichtfelder statisch, Laufzeit lehnt ab (P2-06, major)

- Quelle: `ProjectAnalyzer.validateFields` Zeilen 120–124: `assigned.add(field.name())` erfolgt unabhängig vom Wert; die Typ-/Enumprüfung wird für `LiteralValue(null)` übersprungen (`l.value()!=null`), und die Pflichtprüfung sieht das Feld als zugewiesen.
- Reproduktion: `task('t', RequiredText) { text = null }; defaults 't'` in `P2StaticWhenProbe`; dieselbe Fixture in `P2RuntimeWhenProbe`.
- Erwartet: statische Diagnose (REQUIRED_INPUT oder INPUT_TYPE), weil der Wert statisch bekannt und die Laufzeitkonvertierung/-pflichtprüfung nicht erfüllbar ist.
- Tatsächlich: statisch keine Diagnose, `Capabilities` vollständig; Laufzeit `FAILED` mit `INVALID_CONFIGURATION_VALUE` (die required-Prüfung würde bei konvertierbarem Nullwert ebenfalls fehlschlagen). Ein ungültiger Job wird als fehlerfrei dargestellt.
- Erforderliche Korrektur: `LiteralValue(null)` bei required/Type/Shape wie „nicht zugewiesen“ bzw. als inkompatibel behandeln; RED-Test mit `text = null` und Runtime-Vergleich. Keine Produktkorrektur durch den Reviewer.

### Optionale Verbesserungen (kein Muss-Finding, keine Scopeausweitung)

- O1: Bei `CatalogLoadResult.State.PARTIAL` sind `operations` vorhanden, `catalogComplete=false`; `UNKNOWN_INPUT`/`REQUIRED_INPUT` werden trotzdem gegen den unvollständigen Katalog geprüft. Für partielle Kataloge sollten Felddiagnosen ebenfalls partial/unsicher sein (P2-08-Konsistenz).
- O2: `DiagnosticMessages` enthält keine DE/EN-Texte für `PROJECT_*`-Codes; diese erscheinen als Rohcodes. Architektur §6.4 verlangt Anzeigetexte mit englischem Fallback.
- O3: DuckDb verlangt zur Laufzeit `.sql`-Dateien; die statische Analyse liest explizite `file()`-Quellen bewusst auch ohne Endung. Das ist als bewusste Anti-Heuristik dokumentiert, kann aber eine SQL-Diagnose auf einem Job erzeugen, den die Laufzeit schon an der Endung ablehnt. Kein Muss-Finding, aber als Grenze dokumentieren.
- O4: `defaults` ohne Ziele/ohne Defaults erzeugt keine `MISSING_TARGET`-Diagnose, obwohl der Runtime-Plan dann fehlschlägt; optionale Vervollständigung der Targetdiagnostik.

## 8. Urteil

**FAIL.** P2-01, P2-02, P2-03, P2-04, P2-05, P2-07, P2-08 und der Gate-/Testteil von P2-09 sind durch Code, Tests und frische Belege gedeckt. P2-06 ist verletzt: Die Pflichtfeldprüfung weicht in zwei reproduzierbaren, statisch entscheidbaren Fällen von der echten Runtime ab (F1 falscher ERROR bei `when`, F2 fehlende Diagnose bei Literal-`null`). Beides ist keine optionale Lintfrage, sondern eine falsche semantische Aussage des Kerns, die ab P5 als falsche Problems-Anzeige sichtbar würde.

Die Rootgates wurden wie beauftragt **nicht** erneut ausgeführt; ihre frischen Rohbelege (798/854 Tests, 0 Fehler/Skips, exakte SHAs/Hashes/Zeitfenster) wurden unabhängig validiert. Der betroffene 419-Java-/32-Node-Lauf und die Dokuprüfung wurden selbst ausgeführt. Es gibt keine fehlenden Pflichtchecks und keine GUI-Pflicht in P2.

**Nächster erlaubter Schritt:** F1/F2 in P2 beheben, je einen RED-Test mit echtem Runtime-Vergleich ergänzen, betroffene Core-/Groovy-Tests und die fälligen Rootgates auf einem neuen Kandidaten frisch belegen, dann neue unabhängige P2-Review-Session. Keine Publikation, kein Push/Merge/Tag, keine produktiven Jobs; `phaseBase` bleibt unverändert.
