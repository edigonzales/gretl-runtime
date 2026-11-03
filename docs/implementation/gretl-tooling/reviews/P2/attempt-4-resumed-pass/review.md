# Unabhängiger Review P2 – GRETL Tooling

## 1. Identität und Auftrag

- Review-ID: `b4ac5f5b-55c8-475d-b594-79d757093d4b`; Phase `P2`; Kandidat `1a8d9f0a4096295c2e5898bab2593bb3eb34789e`.
- `assignmentBase`: `185fc6700b5cb2897f724d457610a57f059b3421`; `phaseBase`: `fc5ce83a75b81e405bcffdcc017a1b8311ce37c0`.
- Worktree: `/Users/stefan/.cache/gretl-tooling-review/P2-review-4-resume-1/product`; Evidence: `/Users/stefan/.cache/gretl-tooling-review/P2-review-4-resume-1/evidence`.
- Tatsächlicher `git rev-parse HEAD` im Reviewworktree: identisch mit dem Kandidaten. Commit-Zeit `2026-11-03T10:45:06+01:00` (= `2026-11-03T09:45:06Z`). Der Standardlauf beginnt vier Sekunden später, der Dienstlauf nach dessen Ende.
- Vorher/nachher-Trackedzustand: unverändert; `git diff` und `git diff --cached` sind leer. Einzige untracked Dateien sind die vorbestehenden Sessiondateien `.opencode/` und `opencode.json`.
- `requirementsDigest`: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`; unabhängig aus Plan, Architektur, Testmatrix, Reviewprompt, AC-Registry und beiden Schemas nachgerechnet (`digests-own2.mjs`). Identisch.
- `repositoryRulesDigest`: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`; aus Root-AGENTS und Tooling-AGENTS nachgerechnet. Identisch.
- Reviewer: OpenCode `2.2.5`; Provider `deepseek`; Modell `deepseek-flash`; API-Host `api.deepseek.com`. Beleg: `/Users/stefan/.cache/gretl-tooling-review/p2-resume-readiness/identity.json`, inklusive offizieller Aliaszuordnung `DeepSeek-V4.1-Flash-1017` auf `https://api-docs.deepseek.com/quick_start/pricing/`. Ehrliche Grenze: Der Beleg zeigt Provider/Modell/Host und die offizielle Aliaszuordnung, nicht kryptografisch die Servergewichte. Es wurde keine Credentialdatei gelesen oder exportiert.
- Java `25.0.2` (Temurin), Mavenwrapper `3.10.0`, Node `22.23.1`, Groovy `6.0.0`.

## 2. Artefakte

Alle acht im Request genannten Artefakte wurden unabhängig gehasht; jeder Wert stimmt mit dem Request überein.

| Artefakt | SHA-256 |
|---|---|
| `artifacts/gretl-language-contract/target/gretl-language-contract-6.0.0-SNAPSHOT.jar` | `48328a9a519e734f59d124c13e2a00592812a09221434ad61c80a9402a16feeb` |
| `artifacts/gretl-tooling-core/target/gretl-tooling-core-6.0.0-SNAPSHOT.jar` | `87ba3f804d4db792bf013b021866694e992007eccb6865f76a6d066d4fec460b` |
| `artifacts/gretl-language-contract/target/tooling-dependencies.json` | `f05b6a8cde9a42c5d79da723ec5628bdac0f91bdf04855c2c0d84f1193e30a1f` |
| `artifacts/gretl-tooling-core/target/tooling-dependencies.json` | `752c8cffcce0afffd428478b3fb6d4c042902b9015b6613da54059fbd0eb6fc7` |
| `artifacts/gretl-tooling-catalog/target/gretl-tooling-catalog-6.0.0-SNAPSHOT-catalog.jar` | `127ca60b845cf47c3cd2a0905bcda7f26b0470fb9af778290fd8a9601b6a9f1a` |
| `artifacts/gretl-tooling-catalog/target/catalog/operations.json` | `047a7131e9ce4706c5073a8600f38dbe35818623616d9a0025f405a1ddc0d911` |
| `artifacts/gretl-tooling-catalog/target/catalog/operations.schema.json` | `356d7e9a6b4fa0587a539c6a545e95ba278793c4f8e6cb13b305787912669005` |
| `artifacts/gretl-tooling-catalog/target/catalog/catalog-manifest.json` | `d1fb6cc89d7bcf79b18878619eb5a4ef3e2ba915c9ce0be0b1c1ddb436ec0bee` |

## 3. Validierte frische Rootgates (nicht wiederholt)

Die beiden vollständigen Rootgates wurden auftragsgemäss **nicht** erneut ausgeführt. Die vorhandenen Rohbelege wurden unabhängig validiert: Manifestidentität, jede Capture-Datei (Existenz, SHA-256, mtime-Fenster, Duplikate, Nullbytes), jede Surefire-/Failsafe-XML-Summe, Kommando-Log und Artefakthashes.

| Gate | cwd | argv | Exit | Capture | TEST-XML | Tests | Failures | Errors | Skips | Log |
|---|---|---|---|---|---|---|---|---|---|---|
| root-standard | `/Users/stefan/sources/gretl-runtime` | `./mvnw -B clean verify` | 0, kein Signal | 591 | 152 | 811 | 0 | 0 | 0 | `BUILD SUCCESS`, 0 `[ERROR]`, 0 Skip-Zeilen |
| root-services | `/Users/stefan/sources/gretl-runtime` | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 0, kein Signal | 776 | 166 | 867 | 0 | 0 | 0 | `BUILD SUCCESS`, 0 `[ERROR]`, 0 Skip-Zeilen |

- Beide Manifeste nennen exakt `sha=1a8d9f0a4096295c2e5898bab2593bb3eb34789e`, `dirty=""` und die Request-argv. Kein Capture fehlt, keine Hashabweichung, kein Pfad ausserhalb des Laufzeitfensters, kein Duplikat, keine Nullbyte-Datei.
- Standard: `2026-11-03T09:45:10.423Z` bis `09:50:54.887Z`; 152 TEST-XMLs ergeben exakt 811/0/0/0. Der Lauf enthält keinen `gretl-geotools-probe`.
- Services: `2026-11-03T09:50:57.126Z` bis `09:59:07.943Z`; 166 TEST-XMLs ergeben exakt 867/0/0/0. Der Lauf enthält den `gretl-geotools-probe` (`GeoToolsProbeTest`, 4 Tests). Die Profilargumente `geotools-probe,backing-services` sind im Manifest und Log vorhanden.
- Beide Kommandologs enden auf `BUILD SUCCESS`; es gibt keine `BUILD FAILURE`-Zeile, keine `[ERROR]`-Zeile, kein `-DskipTests`, kein `maven.test.skip` und keine positive Skip-Zahl. Die Reporting-/Kommando-Log-Hashes stehen in `final-validation.json`.
- Keine widersprüchlichen oder fehlenden Rootbelege. Damit bestand keine Berechtigung und kein Anlass für einen vollständigen Rebuild.

## 4. Eigene ausgeführte Prüfungen

| Check | Kommando / cwd | Exit | Tests | Failures | Skips | Beleg |
|---|---|---|---|---|---|---|
| tooling-targeted | `./mvnw -B -pl gretl-tooling-catalog,gretl-groovy -am verify`, Reviewworktree | 0 | 527 Java | 0 | 0 | `evidence/targeted-run/command.log`, `summary.json` |
| tooling-docs | `node tools/gretl-tooling/check-docs.mjs`, Reviewworktree | 0 | 1 | 0 | 0 | `evidence/docs-check/check-docs.log`, `summary.json` |
| CP-P2-DUPLICATE-LINK | `node evidence/run-own-counterprobe.mjs` | 0 | 19 Prüfungen | 0 | 0 | `evidence/counterprobe/run.log`, `summary.json` |
| CP-P2-URI-ENUM | `node evidence/run-uri-counterprobe-own.mjs` | 0 | 14 Prüfungen | 0 | 0 | `evidence/counterprobe/uri-run.log`, `uri-summary.json` |
| Root-Evidence-Validierung | `node evidence/final-validation-own.mjs` | 0 | – | 0 | 0 | `evidence/final-validation.json` |

Der betroffene Mavenlauf umfasst 11 Reaktormodule und 527 frische Java-Tests, darunter `PlanSemanticsTest` 5, `StaticSemanticsParityTest` 9, `StaticSettingsParityTest` 4, `CatalogSemanticParityTest` 2 und `StaticSqlTemplateParityTest` 3. Die Dokuprüfung meldet exakt `Validated 81 exact criteria` und `Tooling documentation links and requirement inputs valid`. Die eigene Strukturvalidierung über `tools/gretl-tooling/review.mjs` (`validateReviewArtifacts`) ergab `PASS`; Ergebnis in `evidence/validation.json`.

## 5. Code- und Testprüfung der P2-Kriterien

- **P2-01:** `DocumentStore` hält unveränderliche `SourceText`-Stände mit Bufferinkarnation und Revision; mehrteilige Änderungen sind atomar und rollen vollständig zurück. `SourceView` friert Buffer, Diskstände, Abwesenheit und Limits in einem Capture ein; Buffer gewinnt vor Disk. `WorkspaceManager` verbindet Projekt-, Konfigurations- und Kataloggeneration mit dem Quellenvektor und veröffentlicht nur bei unveränderten Stempeln. `AnalysisScheduler` besitzt einen eigenen begrenzten Executor, verwirft obsolete Tickets und beendet Queue/Worker bei Close. Abgedeckt durch `DocumentStoreTest`, `SourceViewTest`, `WorkspaceManagerTest`, `AnalysisSchedulerTest` und `FileSourceRepositoryTest`.
- **P2-02:** `SettingsDocumentParser` bildet die deklarative Settings-Allowlist auf dem sicheren Parser ab; Includes und Rootjob behalten die Runtime-Reihenfolge, Projektdefaults stammen aus Settings. `ProjectResolver` trennt Root-/Subprojektjobs, lokale und qualifizierte Namen. `StaticSettingsParityTest` vergleicht Includes, Defaults, Fehlercodes, BOM und Nichtausführung mit dem echten `GretlProjectParser`; `WorkspaceManagerTest` und `ProjectAnalyzerTest` decken gleiche lokale Namen in getrennten Subprojekten ab.
- **P2-03:** `ProjectAnalyzer`/`SemanticModel` binden Tasks, Actions, Parameter und Cleanup in getrennten Namensräumen; doppelte, mehrdeutige und dynamische Referenzen haben unterscheidbare `Resolution`-Ergebnisse. Alias-/Named-Refs, dynamische Registrierung und partielle Deklarationen werden konservativ behandelt. Belegt durch `ProjectAnalyzerTest.bindings...`, `duplicatesRemainAmbiguous...`, `guarded...` und die eigene Duplicate-Link-Gegenprobe.
- **P2-04:** `PlanSemantics` prüft vollständige `dependsOn`-Zyklen vor der Targetauswahl, behandelt `after` nur für ausgewählte Knoten, erkennt targetabhängige Mischzyklen, hält die Registrierungsreihenfolge stabil und gewährt `after` keinen Datenzugriff. `ExecutionPlanner` delegiert an dieselbe pure Semantik; `PlanSemanticsTest`, `ExecutionPlannerTest` und `StaticSemanticsParityTest` prüfen Laufzeit-/Statikparität. Die eigene Probe bestätigt zusätzlich Duplicate-Identität und Cross-Kind-Kanten.
- **P2-05:** `ProjectAnalyzer.validateReferences` verlangt für `result`/`resultOptional` die passende direkte/transitive `dependsOn`-Datenkette; `after` allein genügt nicht. Actionresultate erhalten kein erfundenes Schema; Ausgabecompletion und `UNKNOWN_OUTPUT` beruhen auf dem Producerkatalog. `ProjectAnalyzerTest.afterNeverGrants...`, `bindings...` und `StaticSemanticsParityTest` decken die Fälle ab.
- **P2-06:** Parameterdeklaration/-lesen, Toplevelverbot, Typen, `required`/`defaultValue`, Pflichtfelder und Text-Enums sind scopesicher; dynamische/guarded Fälle bleiben Warnungen oder Untererfassung, nicht bewiesene Fehler. Der letzte Commit korrigiert die frühere URI-Enum-Fehlerkennung: `INPUT_ENUM` gilt nur noch für `descriptor.shape().kind()=="Text"`. Der echte Katalog enthält genau sieben `allowedValues`-Felder: fünf `Text`-Enums (`method`, `compression`, `fileType`) und zwei `Opaque`-URI-Schemahinweise (`HttpDownload.url`, `Curl.serverUrl`, jeweils `[http,https]`). `CatalogSemanticParityTest` prüft den echten Katalog und die Runtime-Converter für beide URI-Felder und für gültige/ungültige Text-Enums. Die unabhängige URI-Gegenprobe bestätigt: gültige `https`/`http`-Literale erzeugen keinen `INPUT_ENUM`; `DELETE`/`PATCH` erzeugen je einen Fehler.
- **P2-07:** `SqlTemplateAnalyzer` und `SqlSourceSyntax` bilden die echte `${name}`-Substitutionsregel inklusive Kommentaren/Strings ab; `ProjectResolver` liest `sqlFiles` und `transfers[].sqlFile` jobrelativ. Dirty-SQL gewinnt vor Disk, fehlende und dynamische Maps sind unterscheidbar, und es gibt keine DB-/Secret-Zugriffe. Belegt durch `ProjectAnalyzerTest.sqlReads...`, `sqlSourcesWithEqualNames...`, `parameterSets...`, `db2dbTransfer...` und `StaticSqlTemplateParityTest`.
- **P2-08:** `SemanticModel.Capabilities` und die Coverage-Pfade begrenzen Diagnosen und Mutationsrechte bei partieller Analyse; Katalogausfall erzeugt `CATALOG_UNAVAILABLE` statt erfundener `UNKNOWN_OPERATION`/`UNKNOWN_INPUT`. `DocumentStore`/`SourceView`/`AnalysisScheduler` begrenzen Quellen, Zeichen, Roots, Projekte, Worker, Queue und Snapshots. Abgedeckt durch `ProjectAnalyzerTest.partialCatalogs...`, `guarded...`, `conditionalBodies...` sowie die Queue-/Budget-/Limit-Tests.
- **P2-09:** Der betroffene 527-Test-Lauf, die Dokuprüfung, beide validierten Rootgates und die zwei eigenen Gegenproben sind grün. Die früheren Findings F1/F2 (attempt-1) und F3 (attempt-2) sind durch die aktuellen Tests geschlossen; der URI-Befund F1 aus attempt-3 ist durch den letzten Commit und den echten Katalogtest behoben.

## 6. Eigene neue Gegenproben

### CP-P2-DUPLICATE-LINK – semantische Duplikatidentität und Cross-Kind-Kanten

- Hypothese: `dependsOn:['run', ':a:run']` in einem included Subprojekt muss beide Schreibweisen auf dasselbe Symbol `:a:run` abbilden, genau eine Kante erzeugen und genau ein `DUPLICATE_TASK_LINK` melden. Dieselbe Ziel-ID in `dependsOn` und `after` darf dagegen als zwei unterschiedliche Kantenarten bestehen. Ein Default darf nur den `dependsOn`-Hull auswählen.
- Neuheit: Kein vorhandener Test und keine frühere Gegenprobe kombiniert lokale/qualifizierte Duplikatidentität, Cross-Kind-Duplikate und targetabhängige Planauswahl in einem included Subprojekt.
- Aktion: Synthetisches Projekt `settings.gretl` mit `include 'a','b'`, `:a:run`, `:a:use` (`dependsOn:['run', ':a:run']`, `after:[':b:run', ':b:run']`), `:a:both` (`dependsOn:'run'`, `after:'run'`) und `:b:run`; Analyse über `WorkspaceManager`/`ProjectAnalyzer`, danach `PlanSemantics.plan` für Default `:a:use` und Ziel `:a:both`.
- Erwartung: genau zwei `DUPLICATE_TASK_LINK`-ERROR, beide in `a/job.groovy`; vier deduplizierte Kanten; je eine `DEPENDS_ON`-/`AFTER`-Kante für `use` und `both`; vollständiger Graph; Default-Plan `[:a:run, :a:use]`; `after` liefert keinen Datenprädiktor.
- Beobachtung: Alle 19 Prüfungen bestanden, `COUNTERPROBE PASS`. Belege: `evidence/counterprobe/P2DuplicateLinkCounterprobe.java` (SHA-256 `741ebb0b9e0a2126fbdc5e294319b003b28fc710bb671ae04a81a4b5e9e0056c`), `evidence/counterprobe/run.log` (SHA-256 `38294b7686f51ca71e95d71d0c036a8f77ee3348e8c276b1628e4bb97f35da49`), `evidence/counterprobe/summary.json`.

### CP-P2-URI-ENUM – unabhängige Prüfung des echten Katalogs nach der Korrektur

- Hypothese: Die `allowedValues` von `HttpDownload.url` und `Curl.serverUrl` sind URI-Schemahinweise auf `Opaque`-Shapes, keine vollständigen Literal-Enums. Gültige URL-Literale dürfen deshalb keinen `INPUT_ENUM` erzeugen; `method` bleibt ein echtes `Text`-Enum.
- Neuheit: Die Probe lädt die gelieferte `operations.json` selbst, prüft die Shape-Arten direkt und analysiert gültige und ungültige HttpDownload-/Curl-Tasks in **einem** Capture. Der Produktest deckt das Verhalten separat ab; diese unabhängige Implementierung prüft zusätzlich die Katalogstruktur und die Fehlerursache.
- Aktion: `CatalogRepository.loadBundled` mit der gelieferten `operations.json`; Descriptor-Prüfung für beide Operationen; Job mit `http`/`curl` (gültige URLs, `GET`/`POST`) und `badHttp`/`badCurl` (`DELETE`/`PATCH`) über `WorkspaceManager`/`ProjectAnalyzer`.
- Erwartung: Katalog `AVAILABLE`; `HttpDownload.url`/`Curl.serverUrl` `Opaque` mit `[http,https]`; `method` `Text` mit `[GET,POST]`; genau zwei `INPUT_ENUM`-ERROR an den Literalen `'DELETE'` und `'PATCH'`; keine `INPUT_TYPE`-/`REQUIRED_INPUT`-/`UNKNOWN_INPUT`-Diagnose für die gültigen URL-Felder.
- Beobachtung: Alle 14 Prüfungen bestanden, `URI COUNTERPROBE PASS`. Belege: `evidence/counterprobe/P2UriEnumIndependentCounterprobe.java` (SHA-256 `94c23a197ec0f76e052277f5904d3b9b5a1baa417f638d23b7c2ed4f78f8788a`), `evidence/counterprobe/uri-run.log` (SHA-256 `f7df509aa1f7603dc2a8de4cec09812381b534483427daa415d5a48f8197c6da`), `evidence/counterprobe/uri-summary.json`.

## 7. Findings

**Keine offenen Muss-Findings.** Die früheren Findings sind geschlossen:

- F1/F2 aus attempt-1 (Pflichtfeld bei `when`, Literal-`null`) und F3 aus attempt-2 (guarded Ausführung) sind durch die aktuellen Coverage-/Conditional-Tests und `StaticSemanticsParityTest` abgedeckt.
- F1 aus attempt-3 (URI-`allowedValues` als vollständige Literal-Enummenge) ist durch Commit `1a8d9f0` behoben. Die Korrektur ist eng: nur `shape.kind()=="Text"` löst noch `INPUT_ENUM` aus. Der echte Katalog enthält ausserhalb von `Text` nur die beiden URI-Schemahinweise; die fünf echten Text-Enums bleiben streng geprüft.

Bewusste, nicht als Muss-Finding gewertete Grenzen: Die statische URI-Prüfung validiert nicht die vollständige URI-Semantik (Host, Absolutheit, Userinfo); sie untererfasst damit konservativ, erzeugt aber keine falschen Fehler. Ebenso kann eine explizite SQL-Datei ohne `.sql`-Endung statisch gelesen werden, obwohl die Laufzeit sie später ablehnt; auch das ist eine dokumentierte Untererfassung, kein bewiesener Falschfehler.

## 8. Urteil

**PASS.** Alle neun P2-Musskriterien `P2-01` bis `P2-09` sind erfüllt. Die beiden frischen vollständigen Rootgates des Kandidaten wurden anhand aller Rohbelege unabhängig validiert (811 bzw. 867 Tests, jeweils 0 Failures/Errors/Skips, `BUILD SUCCESS`, saubere Kandidatenidentität und -artefakte) und auftragsgemäss nicht wiederholt. Der betroffene 527-Test-Lauf und die Dokuprüfung bestanden. Beide eigenen neuen Gegenproben bestanden; die URI-Gegenprobe bestätigt die Korrektur des früheren F1 am echten Katalog, und die Duplicate-Link-Gegenprobe prüft zusätzlich bislang ungetestete Graphidentität. Es gibt keine offenen Muss-Findings und keine fehlenden Pflichtprüfungen. Keine Produktdatei wurde geändert, nichts wurde committet, veröffentlicht oder als produktiver Job ausgeführt. Es liefen keine eigenen Hintergrundprozesse.
