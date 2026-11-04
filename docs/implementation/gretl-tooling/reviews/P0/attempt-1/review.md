# Unabhängiger Review P0 – GRETL Tooling

## 1. Identität

- **Review-ID:** `1587e75e-c728-496f-8ac1-5242649b8e82`
- **Phase:** P0 (neun Musskriterien); kumulativer Umfang noch nicht fällig.
- **OpenCode-Version:** 2.2.5 (Beleg: `readiness/identity.json`, vom Bereitschaftslauf erzeugt).
- **Provider:** `deepseek`; **API-Modell:** `deepseek-flash`;
  **API-Host:** `https://api.deepseek.com`; **offizieller Aliasbeleg:**
  `https://api-docs.deepseek.com/quick_start/pricing/` (Seitentext enthält
  `deepseek-flash` und `DeepSeek-V4.1-Flash-1017`; lokal gesichert unter
  `/Users/stefan/.cache/gretl-tooling-review/P0-review-1/evidence/readiness/official-model-mapping.txt`).
  Die effektive Session ist deepseek/deepseek-flash; eine kryptografische
  Identität der Servergewichte wird nicht behauptet.
- **Assignmentbase / Phasebase:** `185fc6700b5cb2897f724d457610a57f059b3421`
  (Objekt über `git rev-parse <sha>^{commit}` vorhanden).
- **Kandidat:** `5ff6cffcff1d23d1cec00116a8d0a51948945f33`
- **Tatsächlicher HEAD:** `5ff6cffcff1d23d1cec00116a8d0a51948945f33` (detached).
- **Requirements-Digest:** `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`
  (unabhängig über `captureRequirements` nachgerechnet: identisch).
- **Repository-Rules-Digest:** `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`
  (unabhängig nachgerechnet: identisch).
- **Tracked-Zustand vorher/nachher:** beide Male
  `git status --porcelain --untracked-files=no` leer; nur erwartete
  untracked Reviewdateien `.opencode/` und `opencode.json` (scoped Profil).
  Keine Produktquelle, kein Test, kein Lock/Schema/Spec verändert.
- **Artefakte mit Hash (alle vom Request geforderten Werte nachgerechnet, alle OK):**
  contract-JAR `1c6ded4c…dacec5e`, core-JAR `8730097d…77ced78`,
  catalog-Daten-JAR `94384f84…bb29ff3`, `operations.json` `047a7131…dc0d911`,
  `operations.schema.json` `356d7e9a…12669005`,
  `catalog-manifest.json` `7ae40b1f…f534eea`.
- **Umgebung:** macOS 27.0.1 arm64; Temurin JDK 25.0.2 (`maven.compiler.release=25`);
  Maven-Wrapper 3.10.0; Groovy 6.0.0; Jackson 2.17.2; JUnit 5.14.4;
  Node/NPM gemäss Implementierungsbericht 22.23.1/11.18.0; DuckDB 1.5.2 / SQLite 3.53.2
  aus den Testläufen.

## 2. Eigene Ziel-, Code- und Architekturprüfung

Gelesen: EXECUTION_PLAN, ARCHITECTURE, TEST_MATRIX, SOURCE_AUDIT,
ACCEPTANCE_CRITERIA.json, OPENCODE_REVIEW_PROMPT, `implementation/P0.md`, beide
`AGENTS.md`, das Review-Request-/Verdictschema sowie der gesamte Phasendiff
`185fc67…5ff6cff` (33 Dateien, +1641/−96).

- **Modulgrenzen:** `gretl-language-contract` (nur JDK-Werte), `gretl-tooling-core`
  (Jackson + contract), `gretl-tooling-catalog` (Build-Exporter). Der aufgelöste
  Produktionsgraph von core enthält ausschliesslich `gretl-language-contract`,
  `jackson-databind/core/annotations`; contract hat keine Produktionsdependency.
  Der Enforcer bannt Runtime-/Task-/Gradle-/LSP4J-/GeoTools-Module. Kein
  `gretl-lsp`/Frontend-Modul und keine leere Attrappe angelegt.
- **Keine Fremd-GRETL-Dependency:** weder Maven- noch Archivseite; der Daten-JAR
  enthält nur `operations.json`, `operations.schema.json`, `catalog-manifest.json`,
  `operations.sha256` und Maven-Metadaten, keine `.class`/`.jar`. Core-/Contract-JAR
  enthalten keine `ch/so/agi/gretl/runtime/`- oder `org/gradle/`-Einträge.
- **Keine Ausführung beim Export:** Der Exporter nutzt nur SPI-Deskriptoren
  (`inputs/outputs/id/symbol/description/example/resultType/valueType/...`);
  `dataValue` akzeptiert nur JDK-Werte/Enum und wirft für fremde Objekte, ohne
  `toString`/Getter. Der Poison-Test belegt, dass Builder/Handler/Converter/
  Outputreader nicht laufen. `MetadataSupplementLoader` benutzt
  `ServiceLoader.stream()`/`Provider.type()` (keine Provider-Instanziierung).
- **Katalogvertrag:** 44 Operationen aus 4 echten Providern (io, database,
  interlis, ilivalidator), nach `operationId` sortiert, Produzenten sortiert,
  IDs/Symbole eindeutig, `origin ⊆ producers`, `voidResult ⇒ keine Outputs`,
  `hasDefault ⇔ defaultValue != null`. `catalogId` ist ein Digest der kanonischen
  Nutzlast ohne eigenes ID-Feld; ich habe ihn unabhängig über die kanonischen
  Bytes mit geleertem `catalogId` nachgerechnet (identisch). Schema (Draft 2020-12)
  akzeptiert den echten Katalog und lehnt `schemaVersion=2` sowie leere
  Operationsliste ab. Supplemente (sensitive/pathRole/hints, inkl. Liste-von-Path)
  sind auf den echten Daten sichtbar.
- **Pure Contracts:** `ProjectNameRules` ist die reine Logik; `ProjectPath` bleibt
  API-kompatibler Dateisystemadapter (alle bisherigen Methoden delegieren).
  `DslVocabulary` ersetzt die bisherigen Literalmengen in `GretlScript`; die
  Paritätstests prüfen Parametertypen und Scope-Helper gegen die echten
  Runtime-Klassen und erhalten alte Fehlermeldungen. Der absichtlich fehlende
  `secret`-Helper im Cleanup entspricht dem realen `SECRET_NOT_ALLOWED`-Verhalten.
- **Source-/Snapshot-/Graph-/Editverträge:** in P0 nicht implementiert und nicht
  vorgezogen (P1–P3). Das ist korrekt: P0 legt nur Verträge, Katalog und
  Reviewkette an.
- **Reviewkette:** `review.mjs` enthält echte Worktree-/Request-/Gatefunktionen,
  frische Session, Streamdrain, Prozessgruppen-Timeout, Schemas, Identitäts- und
  Evidenzprüfung; `review.test.mjs` deckt falsche SHA/Phase/ACmenge/Digest,
  laufende/untergeordnete Prozesse, Dirty-Tree, vorgefertigten Report, Exit/
  Timeout, Providerwechsel, fehlende Gegenprobe/Checks/Evidenz, Skips, Nulltests,
  offenes Finding und fehlenden Report ab. `cleanupOwnedResources` löscht nur
  eigene Worktrees, kein globales `pkill`.

## 3. Jede fällige Muss-AC

| AC | Status | Code-/Test-/Artefaktbeleg | Finding |
|---|---|---|---|
| P0-01 | erfüllt | `implementation/P0.md` dokumentiert Basen, Toolchain, gelesene Anleitungen, keinen Reset; Worktree sauber, HEAD = Kandidat; Diff `185fc67..5ff6cff` gelesen. | — |
| P0-02 | erfüllt | `readiness/identity.json` (2.2.5, deepseek, deepseek-flash, api.deepseek.com, Aliasbeleg, Sessionmodelle alle deepseek-flash); `readiness/evidence/opencode.jsonl` echter Lauf mit read/shell/`node --test` (1 pass) und `step_finish stop`; `readiness.md`; Mappingtext. Kein Key-Zwang, kein Fallback. | — |
| P0-03 | erfüllt | `tooling-dependencies.json` (core nur contract+Jackson; contract nur Test-JUnit); Enforcer; `inspect-jars.json` (keine Runtime-/Gradle-Klassen, Daten-JAR ohne Code); keine LSP-/UI-Module. | — |
| P0-04 | erfüllt | `ProjectNameRules`, `DslVocabulary`, delegierender `ProjectPath`; `LanguageContractParityTest` (Parametertypen, Scope-Helper, alte Fehlermeldungen) und `ProjectNameRulesTest`; beide im frischen Lauf grün. | — |
| P0-05 | erfüllt | `OperationCatalogExporterTest.fullSpiParityAndDeterminismWithoutHardcodedOperationCount` vergleicht jede Operation/jedes Feld mit der SPI und prüft Umordnungsdeterminismus; echter Katalog: 44 Operationen/4 Provider, sortiert; `operations.json` bytegleich zwischen Request-Artefakt und frischem Build. Keine handgepflegte zweite Liste. | — |
| P0-06 | erfüllt | `CatalogJsonCodec` (Strict-Duplicate, Unknown-Fail, Grössen-/Nestinglimit, Digest, Schema), `CatalogRepository` (atomare Generationen, Kollisionen), Tests für UNAVAILABLE/INCOMPATIBLE/falschen Digest/duplizierte ID/leeren Katalog/Supplementkollision; unabhängige Ajv-Prüfung des echten Schemas. | — |
| P0-07 | erfüllt | Exporter nutzt nur Deskriptoren, Poison-Callbacks werfen, unbekannte Defaultobjekte scheitern ohne `toString`; Daten-JAR ohne Klassen/verschachtelte JARs, konsumierbar mit core (Jackson) ohne Exporterclasspath. | — |
| P0-08 | erfüllt | Frischer gezielter Reaktorlauf (420 Java-Tests + 28 Node-Tests, 0 Skips), Doku-/Dependencycheck frisch; beide Rootgates desselben Kandidaten unabhängig validiert (Manifeste, nachgerechnete 708/760 Tests, 559/742 Dateihashes); MIT-Übernahmeinventur; frühe Bild-/GUIprobe real geöffnet. | — |
| P0-09 | erfüllt | `review.mjs` mit allen geforderten Verantwortungen; 28 negative Node-Tests frisch grün; eigene Gate-Fälschungsprobe gegen das echte Verdictschema (offenes Finding, fehlender Check, falscher Provider, fehlende Gegenprobe, fehlende AC, falscher Report werden abgelehnt). | — |

## 4. Tatsächliche Tests

| Gate/Fall | fällig warum | Kommando/cwd | Exit/Tests/Fehler/Skips | eigener Lauf oder verifizierter aktueller Beleg | Evidence |
|---|---|---|---|---|---|
| tooling-targeted | frische risikotragende Katalog-/Contract-/Runtime-/Reviewtests | `./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-tooling-catalog,gretl-groovy -am verify` in `/Users/stefan/.cache/gretl-tooling-review/P0-review-1/product` | Exit 0, Build SUCCESS; 420 Java-Tests, 0 Fehler, 0 Skips; im core-Testlauf `node --test review.test.mjs` 28 pass/0 fail/0 skipped und `check-docs` 81 | eigener Lauf | `fresh-runs.json`, `analysis-p0.json`, `inspect-jars.json` |
| tooling-docs | exakte Kriterien/Links | `node tools/gretl-tooling/check-docs.mjs` | Exit 0; "Validated 81 exact criteria"; Links/Requirements gültig | eigener Lauf | `fresh-runs.json` |
| tooling-dependencies | aufgelöste Produktionsgraphen und echte Archive | `node tools/gretl-tooling/verify-dependencies.mjs --mode all` | Exit 0; Graphen und Archive verifiziert | eigener Lauf | `fresh-runs.json`, `inspect-jars.json` |
| root-standard | Rootpflichtgate §1.2 | `./mvnw -B clean verify` (Manifest-cwd `/Users/stefan/sources/gretl-runtime`) | Exit 0, 708 Tests, 0/0/0 | verifizierter aktueller Beleg desselben Kandidaten | `p0-cli-fixed-standard/manifest.json`, `analysis-p0.json` |
| root-services | Rootpflichtgate §1.2 mit Diensten/GeoTools | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | Exit 0, 760 Tests, 0/0/0 | verifizierter aktueller Beleg desselben Kandidaten | `p0-cli-fixed-services/manifest.json`, `analysis-p0.json` |

Ich habe die beiden vollständigen Rootgates bewusst nicht doppelt neu gestartet,
sondern – wie §5.3 erlaubt – die Rohbelege desselben Kandidaten unabhängig
validiert: Manifest-SHA/Dirty/argv/Exit, Neusummierung der Surefire/Failsafe-XML
(708 bzw. 760, keine Fehler/Skips) und Neu-Hashing aller 559 bzw. 742 kopierten
Belegdateien. Zusätzlich habe ich das gezielte Reaktorgate, den Doku- und den
Dependencycheck frisch ausgeführt. Python-/Quarto-Dokumentationsläufe waren keine
geforderten Checks; ihre Rohbelege (`documentation.json`, 73 Python-Tests OK,
Links/Render Exit 0) sind auf denselben Kandidaten gebunden.

Nicht zusätzlich nötig: Produkt-GUI/Modeler (P0 verlangt sie ausdrücklich nicht),
LSP-/Protocol-Tests (Module erst P4), P1–P3-Parser/Semantik. Die frühe
Bildfähigkeit wurde statt eines Produkt-GUI-Tests real geprüft.

## 5. Echte GUI und Sichtprüfung (ab P5)

P0 verlangt keine Produkt-GUI. Die verlangte frühe Infrastrukturprobe habe ich
mit dem tatsächlich geöffneten Bild
`readiness/gui-probe.png` geprüft: sichtbares Finder-Fenster mit Titel
`preflight`, Dateiliste `opencode.json`, `probe.test.mjs`, `probe.txt`,
`readiness.md`, `scoped.jsonl`, `scoped.stderr.log`; das passt zu
`image-readiness.md`. Damit ist echte Bildwahrnehmung belegt; Aussagen über eine
Workbench/Webview sind damit ausdrücklich **nicht** verbunden.

## 6. Eigene neue Gegenproben

1. **p0-catalog-forgery** – Hypothese: Eine schemakonforme Byte-Manipulation des
   echten `operations.json` darf nicht als gültiger Katalog durchgehen. Aktion:
   unabhängige Digestberechnung über die kanonischen Bytes mit geleertem
   `catalogId`; dann Mutation eines Beispieltexts. Beobachtung: Original-Digest
   `234434d2…aafdcc57` = `catalogId`; die Fälschung bleibt schemakonform, aber
   Digest `ffe7d252…21783fe4` ≠ `catalogId` und Manifest-Hash ≠ Dateihash ⇒
   erkannt. Kein Duplikat des Exporter-Tests, weil dieser nur zwei In-JVM-Exporte
   vergleicht und die Bytes nicht gegen das Manifest prüft.
2. **p0-gate-forgery** – Hypothese: Ein vorgefertigtes PASS mit korrekten
   Identitäten, aber offenem Muss-Finding, fehlendem Check, falschem Provider,
   fehlender Gegenprobe, nur acht Kriterien oder falschem Report darf nicht
   akzeptiert werden. Aktion: echtes Verdictschema mit Ajv, synthetische
   Positivkontrolle plus sechs Fälschungen. Beobachtung: Positivkontrolle gültig,
   alle sechs Fälschungen abgelehnt.

## 7. Findings/Blocker

Keine offenen Muss-Findings. Keine Blocker.

**Optionale Verbesserung (nicht abnahmerelevant):** `OperationCatalogExporter.write`
serialisiert `catalog-manifest.json` aus einem `Map.of`; dessen Iterationsreihenfolge
ist JVM-lauf-abhängig. Deshalb unterscheidet sich der *Daten-JAR-Hash* eines
frischen Builds (`127ca60b…`) vom Request-Artefakt (`94384f84…`), obwohl
`operations.json` bytegleich ist und die fachliche `catalogId` stabil bleibt. Für
reproduzierbare Artefaktbytes sollten die Manifest-Schlüssel kanonisch sortiert
werden. Das verletzt keine Muss-AC: P0-05 verlangt einen deterministischen
Operationskatalog (erfüllt; `operations.json` bytegleich, `catalogId` stabil),
und die Architektur erlaubt Envelope-Variabilität.

## 8. Urteil

**PASS.** Alle neun P0-Musskriterien sind durch aktuellen Code, wirksame Tests,
echte Artefakte und unabhängig validierte Rootgate-Rohbelege belegt. Die fälligen
gezielten Checks wurden frisch ausgeführt, zwei neue Gegenproben erkennen
Katalog- und Gate-Fälschungen tatsächlich. Tracked-Zustand und Kandidaten-SHA sind
unverändert; es gibt keine offenen Muss-Findings oder fehlenden Pflichtchecks.

**Nächster erlaubter Schritt:** Nach dem äusseren, nach Prozessende festgestellten
Gate und unverändertem Kandidaten darf P1 beginnen.
