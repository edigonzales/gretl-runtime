# Unabhängiger Review P4 – GRETL Tooling

## 1. Identität

Review-ID: `1d0dac08-f18b-4b1f-9efd-1f387d8752e4`
Phase: P4, alle neun Kriterien P4-01 bis P4-09.
OpenCode: 2.2.5; Provider: `deepseek`; API-Modell: `deepseek-flash`; Host: `api.deepseek.com`; Aliasbeleg: `/Users/stefan/.cache/gretl-tooling-review/p2-resume-readiness/identity.json` (DeepSeek-V4.1-Flash-1017, `actualProvider=deepseek`, `actualModel=deepseek-flash`, `effectiveBaseUrl=https://api.deepseek.com`, checkedAt 2026-11-03T13:16:05.719Z).
Assignmentbase: `185fc6700b5cb2897f724d457610a57f059b3421`; Phasebase: `392b6d8ff0191db9b3619c1c5b34fc2e2ee98886`.
Kandidat und tatsächlicher HEAD: `621255a972888b9fe1e629687ad9400424e19e1f`.
Requirements-Digest: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`; Repository-Rules-Digest: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`.
Tracked-Zustand vor und nach der Prüfung: sauber; nur die bereits vorhandenen untracked Reviewdateien `.opencode/` und `opencode.json`. Kein Produktfile geändert, kein Commit, kein Push, keine Veröffentlichung.

Umgebung: macOS/darwin arm64, OpenJDK 25.0.2 (Temurin 25.0.2+10-LTS), Node v22.23.1. Buildverträge: Java 25, Groovy 6.0.0, LSP4J 1.0.0, Gson 2.15.0. P4 verlangt keine GUI; es wurde keine GUI ausgeführt.

Artefakte (alle gegen `request.json` nachgerechnet):

| Artefakt | SHA-256 |
|---|---|
| `gretl-language-contract-6.0.0-SNAPSHOT.jar` | `48328a9a519e734f59d124c13e2a00592812a09221434ad61c80a9402a16feeb` |
| `gretl-tooling-core-6.0.0-SNAPSHOT.jar` | `f156e3712cedc0106f2a04239ebde5e5022ef85f643149c4b80d6fff5703860f` |
| `gretl-tooling-catalog-6.0.0-SNAPSHOT-catalog.jar` | `94384f845fc41f74ddd1aa8e4b3daa39275be33eac282d8478b97d2b8bb29ff3` |
| `operations.json` | `047a7131e9ce4706c5073a8600f38dbe35818623616d9a0025f405a1ddc0d911` |
| `operations.schema.json` | `356d7e9a6b4fa0587a539c6a545e95ba278793c4f8e6cb13b305787912669005` |
| `catalog-manifest.json` | `7ae40b1f60e66274afbd67ff7a8cda23e3e1ba628b6051dd935f4ac07f534eea` |
| `gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar` | `b810405cbacb5127f4dc12c38f31c6324e24db012303b4ec06ec60d6844a0805` |
| `gretl-protocol-v1.schema.json` | `6f95828c38651f825f0ffdd7b21937f8c4e50332fc0f7618b6e70a2d17bc4bf6` |
| `gretl-language-contract/target/tooling-dependencies.json` | `f05b6a8cde9a42c5d79da723ec5628bdac0f91bdf04855c2c0d84f1193e30a1f` |
| `gretl-tooling-core/target/tooling-dependencies.json` | `752c8cffcce0afffd428478b3fb6d4c042902b9015b6613da54059fbd0eb6fc7` |
| `gretl-lsp/target/tooling-dependencies.json` | `d8780361a2135fe759ac5ae3d3c2c08ff7dde12a064eec7b1185b46ae94aeb8a` |

Der neu gebaute LSP-JAR hat denselben Hash `b810405c…` wie das gelieferte Artefakt; die Gegenprobe lief daher gegen das Kandidatenartefakt.

## 2. Eigene Ziel-, Code- und Architekturprüfung

Gelesen wurden insbesondere `EXECUTION_PLAN.md` (P4-01–P4-09), `ARCHITECTURE.md` (§8 LSP-Server, Lifecycle, Customprotokoll), `TEST_MATRIX.md` (T18–T22), `SOURCE_AUDIT.md`, `ACCEPTANCE_CRITERIA.json`, der P4-Implementierungsbericht, `gretl-lsp/pom.xml`, `gretl-lsp/README.md`, die LSP-Hauptklassen, das Protokollschema, die Hosttests und der reale Prozesstest.

Modulgrenzen: `gretl-lsp` hängt produktiv von `gretl-tooling-core`, LSP4J, Gson und dem reinen Katalogdatenclassifier ab. Der Katalog hat `*:*`-Ausschlüsse und wird nur als Daten gelesen; die Server-Enforcer-Regel und `verify-dependencies.mjs` verbieten `gretl-api`, Runner, Tasks, Gradle, GeoTools und Laufzeitklassen. Der Archive-Test prüft den wirklichen JAR-Inhalt. Es wurden keine Schreib-, Prozessstart- oder Jobausführungspfade im Server gefunden; `System.exit` liegt nur an der Launcher-Grenze.

Lifecycle: `NEW → INITIALIZED → SHUTTING_DOWN → CLOSED` ist als Zustandsautomat umgesetzt; `shutdown`/`exit` beenden den Prozess auch bei offenem stdin, EOF und ungültigen Argumenten werden getestet. Die Host-Executors, Worker, Debounce- und Watcherressourcen werden in `close()` beendet.

Protokoll: Die fünf Customrequests sind in `GretlLanguageServerApi` als LSP4J-`@JsonRequest` registriert; `gretl/analysisReady` ist als Client-Notification registriert. Requestrecords sind versioniert; Revisionen und skalare Zahlenwerte werden als Strings übertragen, fachliche Enums als Namen, Standard-LSP-Enums numerisch. Das Schema wird gegen die Java-Recordfelder und mit positiven/negativen AJV-Fixtures geprüft.

Source-/Snapshot-/Editverträge: `DocumentStore` versioniert Buffer und lehnt stale Versionen ab; `ServerWorkspace` bindet Anfragen an Kontrollsequenz und Snapshotgeneration, begrenzt Worker/Queue/Snapshots und verwirft obsolete Antworten. Readonly- und Symlinkprüfungen erfolgen vor Editplänen; der Server liefert ausschließlich versionierte `documentChanges` und schreibt keine Jobdateien.

## 3. Jede fällige Muss-AC

| AC | Status | Code-/Testbeleg | Finding |
|---|---|---|---|
| P4-01 | erfüllt | `gretl-lsp/pom.xml`, `BundledCatalog.java`, `ServerArchiveIT.java`, `ServerProcessIT.java`, `tooling-targeted.log`/`.json` | — |
| P4-02 | erfüllt | `GretlServerLauncher.java`, `GretlLanguageServer.java`, `ServerProcessIT.java` (`protocolStatesTrustAndExit...`) | — |
| P4-03 | erfüllt | `GretlTextDocumentService.java`, `ServerWorkspace.java`, `ServerProcessIT.java`, `HostSchedulingTest.java` | — |
| P4-04 | erfüllt | `GretlTextDocumentService.java`, `LspQueryMapper.java`, `ServerProcessIT.java`; alle Standardfeatures über echte stdio-Aufrufe | — |
| P4-05 | erfüllt | `LspDiagnosticMapper.java`, `LspQueryMapper.java`, `ServerProcessIT.java` (SQL-Version/Range/DE-EN); RelatedInformation wird capabilityabhängig gemappt | — |
| P4-06 | **verletzt** | `GretlWorkspaceService.java:22-25`, `ServerWorkspace.java:47-53`; Gegenprobe `counterprobe-workspace-folders.json`/`.log` | **P4-F1** |
| P4-07 | erfüllt | `GretlLanguageServerApi.java`, `GretlLanguageServer.java`, `GretlProtocol.java`, `gretl-protocol-v1.schema.json`, `ServerProcessIT.java` (catalog/graph/planEdit/validateEdit/navigation und `gretl/analysisReady`) | — |
| P4-08 | erfüllt | `ServerConfiguration.java`, `LspHostSourceRepository.java`, `HostFileWatcher.java`, `ServerProcessIT.java` (Trust, readonly, Pfad-/Message-Limits), `HostBoundaryTest.java` | — |
| P4-09 | erfüllt | `ServerProcessIT.java` (8), `ServerArchiveIT.java` (1), `HostSchedulingTest.java` (2), `HostBoundaryTest.java` (5), validierte Rootgates, gezielter Lauf | — |

## 4. Tatsächliche Tests

| Gate/Fall | Fällig warum | Kommando/cwd | Exit/Tests/Fehler/Skips | Eigener Lauf oder validierter Beleg | Evidence |
|---|---|---|---|---|---|
| `root-standard` | vollständiges Standardgate | `./mvnw -B clean verify` in `/Users/stefan/.cache/gretl-tooling-review/P4-review-1/product` | Exit 0; 898 Tests; 0 Fehler; 0 Skips | **nicht wiederholt**: geliefertes Manifest, Rohkopien, XML und Log validiert | `p4-standard/manifest.json`, `p4-standard/command.log`, `gate-validation.json` |
| `root-services` | vollständiges GeoTools-/Dienstgate | `./mvnw -B -Pgeotools-probe,backing-services clean verify` im selben cwd | Exit 0; 954 Tests; 0 Fehler; 0 Skips | **nicht wiederholt**: geliefertes Manifest, Rohkopien, XML und Log validiert | `p4-services/manifest.json`, `p4-services/command.log`, `gate-validation.json` |
| `tooling-targeted` | betroffene Core-/Host-/Wire-/Prozesstests | `./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-lsp verify` im selben cwd | Exit 0; 173 Java-Tests + 34 Node-Tests; 0 Fehler; 0 Skips | eigener Lauf (19:04:49–19:05:04Z); LSP-Failsafe 9; JAR-Hash identisch | `tooling-targeted.log`, `tooling-targeted.json` |
| `tooling-docs` | exakte Kriterien und lokale Toolinglinks | `node tools/gretl-tooling/check-docs.mjs` im selben cwd | Exit 0; „Validated 81 exact criteria“; Links gültig | eigener Lauf | `tooling-docs.log`, `tooling-docs.json` |
| eigene Gegenprobe | neuer Protokoll-/Zeitreihenfolgefall | `node /Users/stefan/.cache/gretl-tooling-review/P4-review-1/evidence/counterprobe-workspace-folders.mjs` | Exit 1; Probe ausgeführt; Produktbefund bestätigt | eigener Lauf gegen das gepackte Kandidaten-JAR | `counterprobe-workspace-folders.mjs`, `.log`, `.json` |

Rootbelege: Beide Manifeste tragen den Kandidaten-SHA, `dirty=""`, Exit 0; die Laufzeiten sind 18:41:32–18:47:30Z bzw. 18:47:31–18:57:15Z. Standard: 632/632 Kopien vorhanden und hashgleich, 172 XML-Suiten = 898 Tests. Services: 817/817 Kopien vorhanden und hashgleich, 186 XML-Suiten = 954 Tests. Beide Logs enden mit `BUILD SUCCESS`, enthalten keine Skips; die LSP-Failsafe-Suiten enthalten 9 Tests (8 `ServerProcessIT` + 1 `ServerArchiveIT`). Es gab keine fehlenden oder widersprüchlichen Rootbelege.

## 5. Echte GUI und Sichtprüfung (ab P5)

Für P4 nicht gefordert. Es wurde keine VS-Code-/Webview-GUI gestartet und keine GUI-Aussage getroffen.

## 6. Eigene neue Gegenproben

**P4-CP1 – atomare Workspacefolder-Ablehnung**

Hypothese: Ein `workspace/didChangeWorkspaceFolders`-Ereignis, das einen gültigen Root entfernt und gleichzeitig einen ungültigen Root (reguläre Datei statt Verzeichnis) hinzufügt, muss als ungültiges Update atomar abgelehnt werden; der bisherige Root muss registriert bleiben und weitere Graphanfragen müssen weiter funktionieren.

Neuheit: Der gelieferte `ServerProcessIT` prüft gültiges Entfernen/Hinzufügen sowie ungültige Konfiguration, aber nicht die gemischte Workspacefolder-Aktualisierung mit ungültiger Addition. Die Probe startet das gelieferte JAR in einem fremden Arbeitsverzeichnis und spricht echtes JSON-RPC über stdin/stdout.

Ablauf: `initialize` mit `rootUri` auf ein temporäres Workspaceverzeichnis, `initialized`, `didOpen` für `job.groovy`, dann `workspace/didChangeWorkspaceFolders` mit `removed=[workspace]` und `added=[reguläre Datei]`. Danach `gretl/graph` für den alten `job.groovy`, anschließend `shutdown`/`exit`.

Erwartung: Der Server protokolliert die Ablehnung des ungültigen Roots, lässt den alten Root unangetastet und beantwortet `gretl/graph` erfolgreich.

Beobachtung: Der Server protokolliert `WORKSPACE_ROOT_UNSAFE` (die Ablehnung), hat den alten Root aber bereits entfernt; `gretl/graph` antwortet mit `-32602 SOURCE_OUTSIDE_WORKSPACE`. Der Prozess beendet sich danach sauber mit Code 0 (Shutdown/Exit funktionierte). Damit ist die Zustandsänderung nicht atomar.

Belege: `/Users/stefan/.cache/gretl-tooling-review/P4-review-1/evidence/counterprobe-workspace-folders.mjs`, `.log`, `.json`.

## 7. Findings/Blocker

### P4-F1 – Workspacefolder-Updates werden bei ungültiger Addition nicht atomar abgelehnt (P4-06, Schwere: major)

Quelle: `/Users/stefan/.cache/gretl-tooling-review/P4-review-1/product/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/GretlWorkspaceService.java:22-25`; `/Users/stefan/.cache/gretl-tooling-review/P4-review-1/product/gretl-lsp/src/main/java/ch/so/agi/gretl/lsp/ServerWorkspace.java:47-53`.

Reproduktion:

```sh
node /Users/stefan/.cache/gretl-tooling-review/P4-review-1/evidence/counterprobe-workspace-folders.mjs
```

Das Skript startet `java -jar …/gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar --stdio`, initialisiert mit einem gültigen Root, öffnet `job.groovy` und sendet ein Workspacefolder-Ereignis, das den Root entfernt und eine reguläre Datei als neuen Root hinzufügt.

Erwartet: Das Ereignis wird atomar abgelehnt; der bisherige Root bleibt registriert; `gretl/graph` für den bisherigen Job bleibt erfolgreich.

Tatsächlich: `removeRoot` wird vor der Addition ausgeführt und mutiert `roots`, Snapshots und Pläne sofort; `addRoot` scheitert anschließend an der regulären Datei (IOException → `WORKSPACE_ROOT_UNSAFE`). Es gibt keine Vorvalidierung und keinen Rollback. Der Graph antwortet mit `-32602 SOURCE_OUTSIDE_WORKSPACE`.

Erforderliche Korrektur: Zuerst alle hinzuzufügenden Roots vollständig validieren (Scheme, lokales Verzeichnis, Symlink-/Path-Sicherheit, Duplikate, Rootlimit), erst danach die Änderungen anwenden; bei einem Fehler die vorherige Rootmenge und den Analysezustand unverändert lassen. Dafür einen echten stdio-Regressionstest für die gemischte gültige/ungültige Aktualisierung ergänzen.

## 8. Urteil

**FAIL**

P4-01 bis P4-05, P4-07 bis P4-09 sind durch Code, Tests und die validierten Rootgates belegt. P4-06 ist jedoch verletzt: Ein ungültiges Workspacefolder-Update wird nicht atomar abgelehnt, sondern entfernt vor dem Fehlschlag bereits einen gültigen Root; die eigene echte stdio-Gegenprobe bestätigt den Zustandsverlust.

Nächster erlaubter Schritt: Korrektur der atomaren Workspacefolder-Aktualisierung und Ergänzung des negativen Wire-Tests, danach ein neuer Kandidat und eine erneute unabhängige P4-Prüfung. Die gelieferten Rootgates bleiben gültige Belege für den aktuellen Kandidaten, ersetzen aber keine erneute Prüfung eines geänderten Kandidaten.
