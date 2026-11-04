# Unabhängiger Review P4 – GRETL Tooling

## 1. Identität, Umfang und Arbeitszustand

Review-ID: `97b03940-be6f-4968-b66e-8d9a835943e4`
Phase: P4, alle neun Musskriterien P4-01 bis P4-09. Ein P0–P8-Gesamturteil ist nicht fällig.
Kandidat und geprüfter HEAD: `666e812df8394a1f533d534c9c6d134b132898ab`
Assignmentbase: `185fc6700b5cb2897f724d457610a57f059b3421`
Phasebase: `392b6d8ff0191db9b3619c1c5b34fc2e2ee98886`
Requirements-Digest: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`
Repository-Rules-Digest: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`

OpenCode: 2.2.5; Provider: `deepseek`; API-Modell: `deepseek-flash`; Host:
`api.deepseek.com`; Alias: `DeepSeek-V4.1-Flash-1017`; effektive Basis-URL:
`https://api.deepseek.com`. Beleg: `p2-resume-readiness/identity.json`,
`checkedAt=2026-11-03T13:16:05.719Z`, mit den tatsächlichen Sessionwerten
`actualProvider=deepseek`, `actualModel=deepseek-flash` und der
Modellzuordnung aus der DeepSeek-Preis-/Modellseite. Es wird keine
kryptographische Aussage über Servergewichte behauptet.

Umgebung: macOS/darwin arm64, OpenJDK 25.0.2 (Temurin 25.0.2+10-LTS), Node
v22.23.1. P4 verlangt keine GUI; es wurde keine GUI und kein Modelerfrontend
ausgeführt.

Der letzte Statuslauf nach dem gezielten Test (`own-affected-checks.json`)
erfasste HEAD `666e812df8394a1f533d534c9c6d134b132898ab` und als einzige
untracked Einträge `?? .opencode/` sowie `?? opencode.json`. Danach wurden
ausschließlich Evidenzdateien unter dem Evidence-Verzeichnis geschrieben; es
gibt keine Produktänderung, keinen Commit, keinen Push, keine Veröffentlichung
und keinen produktiven Job.

## 2. Validierung der gelieferten Rootgates

Beide vollständigen Rootgates wurden **nicht** wiederholt. Geprüft wurden die
gelieferten Rohmanifeste, die Rohkopien, alle Surefire-/Failsafe-XML-Dateien,
die Zeitstempel und die vollständigen Kommandologs. Beide Manifeste nennen
Kandidat `666e812df8394a1f533d534c9c6d134b132898ab`, `dirty=""` und Exit 0.

| Gate | Kommando | Laufzeit (UTC) | Tests | XML-Suiten | Rohkopien | Fehler/Skips | Log |
|---|---|---|---:|---:|---:|---|---|
| `root-standard` | `./mvnw -B clean verify` | 19:12:48.468–19:19:29.432 | 899 | 175 | 632/632 | 0/0/0 | `BUILD SUCCESS` |
| `root-services` | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 19:19:32.107–19:29:23.079 | 955 | 189 | 817/817 | 0/0/0 | `BUILD SUCCESS` |

Für beide Gates gilt nach `validate-evidence-result.json`:

- Manifest- und Report-Zählungen stimmen überein; die XML-Dateien summieren
  exakt 899 bzw. 955 Tests mit jeweils 0 Failures, 0 Errors und 0 Skips.
- Alle Original- und Kopiepfade sind vorhanden und hashgleich; es gibt keine
  fehlenden oder abweichenden Einträge und keine malformed XML-Datei.
- Alle erfassten Zeitstempel liegen im jeweiligen Laufzeitfenster.
- Beide Logs enden mit `BUILD SUCCESS`, enthalten keinen `BUILD FAILURE` und
  keine Skip-/Warning-Abschwächung.
- Die LSP-Failsafe-Suiten sind in beiden Rootläufen enthalten; der
  Standardlauf enthält die 9 Prozesstests und den Archivtest.

Die Rohmanifeste verweisen auf die Originalpfade unter
`/Users/stefan/.cache/gretl-tooling-review/p4-f1-standard/evidence/...` bzw.
`p4-f1-services/evidence/...`; die geprüften Kopien liegen unter
`p4-standard/evidence/...` und `p4-services/evidence/...`.

Einziger erklärbarer Byteunterschied: Das `catalog-manifest.json` des
Standardlaufs hat durch andere JSON-Schlüsselreihenfolge den Hash
`80baadb7…`, während der Dienstlauf und das Request-Artefakt den Hash
`4c952fc2…` haben. Die semantischen Felder `schemaSha256`,
`operationsSha256`, `catalogId`, `schemaVersion` und `runtimeVersion` sind in
beiden Manifesten identisch; `operations.json` und `operations.schema.json`
sind bytegleich. Das ist kein inhaltlicher Widerspruch der Rootbelege.

Alle elf in `request.json` genannten Artefakte wurden gegen ihre erwartete
SHA-256 geprüft und stimmen überein, darunter:

| Artefakt | SHA-256 |
|---|---|
| `gretl-language-contract-6.0.0-SNAPSHOT.jar` | `48328a9a519e734f59d124c13e2a00592812a09221434ad61c80a9402a16feeb` |
| `gretl-tooling-core-6.0.0-SNAPSHOT.jar` | `f156e3712cedc0106f2a04239ebde5e5022ef85f643149c4b80d6fff5703860f` |
| `gretl-tooling-catalog-6.0.0-SNAPSHOT-catalog.jar` | `f1b6148e5f472c73ce51a59445cb0cb8b7719705ce94a1544aaec91c89499a9e` |
| `operations.json` | `047a7131e9ce4706c5073a8600f38dbe35818623616d9a0025f405a1ddc0d911` |
| `operations.schema.json` | `356d7e9a6b4fa0587a539c6a545e95ba278793c4f8e6cb13b305787912669005` |
| `catalog-manifest.json` | `4c952fc2f6ad293ffeff895afcfa542b28592fd365385ae749b8f0daa76d069a` |
| `gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar` | `fb923c874ca3441df6a8ed92241c3ade7226aba65cbb13c410bd1360bbbdff0c` |
| `gretl-lsp/target/tooling-dependencies.json` | `d8780361a2135fe759ac5ae3d3c2c08ff7dde12a064eec7b1185b46ae94aeb8a` |
| `gretl-protocol-v1.schema.json` | `6f95828c38651f825f0ffdd7b21937f8c4e50332fc0f7618b6e70a2d17bc4bf6` |
| `gretl-language-contract/target/tooling-dependencies.json` | `f05b6a8cde9a42c5d79da723ec5628bdac0f91bdf04855c2c0d84f1193e30a1f` |
| `gretl-tooling-core/target/tooling-dependencies.json` | `752c8cffcce0afffd428478b3fb6d4c042902b9015b6613da54059fbd0eb6fc7` |

## 3. Eigene ausgeführte Prüfungen

| Check | Kommando/cwd | Ergebnis | Beleg |
|---|---|---|---|
| `tooling-targeted` | `./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-lsp verify` in `/Users/stefan/.cache/gretl-tooling-review/P4-review-2/product` | Exit 0; **174 Java-Tests + 34 Node-Tests**; 0 Failures, 0 Errors, 0 Skips | `own-tooling-targeted.log`, `own-affected-checks.json` |
| `tooling-docs` | `node tools/gretl-tooling/check-docs.mjs` im selben cwd | Exit 0; „Validated 81 exact criteria“, Links und Requirement-Inputs gültig | `own-tooling-docs.log`, `own-affected-checks.json` |
| neue Gegenprobe | `node /Users/stefan/.cache/gretl-tooling-review/P4-review-2/evidence/own-counterprobe-inflight-invalid-root.mjs` | Exit 0; Probe `pass: true` | `.mjs`, `.log`, `.json` |

Der gezielte Lauf enthält 164 Surefire-Fälle (7 Contract, 150 Core, 7 Host)
und 10 Failsafe-Fälle (9 `ServerProcessIT`, 1 `ServerArchiveIT`) sowie 32 + 2
Node-Fälle. Die Module wurden gegen die gepinnten Kandidatenabhängigkeiten
gebaut; es gab keine stillen Skips und keine abgeschwächten Assertions.

## 4. Codeprüfung und Reparatur von P4-F1

Die frühere unabhängige P4-Abnahme war ein gültiges FAIL mit dem einzigen
Mussfinding P4-F1: Ein gemischtes Workspacefolder-Ereignis entfernte einen
gültigen Root, bevor die ungültige Addition abgelehnt wurde.

Die Reparatur ist vorhanden und nachvollziehbar:

- `ServerWorkspace.updateRoots` prüft zuerst Größenlimits, normalisierte
  Duplikate, die endgültige Rootmenge und konstruiert für **alle** Additionen
  vor jeder Mutation einen `LspHostSourceRepository`. Damit werden lokales
  Scheme, Verzeichnischarakter, Pfad- und Symlink-Sicherheit vor dem Entfernen
  geprüft.
- Erst danach werden Entfernen/Hinzufügen unter der Acquisition-Sperre
  angewendet; Snapshots und Pläne werden erst nach erfolgreicher Anwendung
  ersetzt. Vorherige Requests werden über die Epoch obsolet. Ein ungültiges
  Ereignis lässt Roots, Snapshotidentität und gespeicherte Pläne unverändert.
- `GretlWorkspaceService.didChangeWorkspaceFolders` leitet das vollständige
  Ereignis an diese Transaktion weiter.
- Die Regression
  `invalidCombinedFolderUpdatesPreserveRootsSnapshotAndRetainedPlans` prüft
  ungültige reguläre Datei, doppelte Addition, Rootlimit, nichtlokalen Root,
  Symlink-Addition, den gültigen kombinierten Wechsel sowie die Erhaltung von
  Snapshot und retained Plan. Sie ist im gezielten Lauf grün.

Zusätzlich geprüft wurden die additiven Core-Anbindungen: Readonly-Erhalt im
`DocumentStore`, stabile innere Graphknoten-IDs im `JobGraphProjector` und
kooperative Quickfix-Cancellation in `LanguageQueries`. Die
Katalog-/Archivgrenze, das getypte Protokollschema und die
Dependency-Ausschlüsse wurden gegen die Artefakte und den gezielten Lauf
konsistent befunden.

## 5. Bewertung der Musskriterien

| Kriterium | Status | Wesentliche Belege |
|---|---|---|
| P4-01 | erfüllt | `gretl-lsp/pom.xml`, `BundledCatalog.java`, `ServerArchiveIT.java`, `ServerProcessIT.java`, LSP-JAR-/Dependency-Hashes, leerer Server-stderr der Gegenprobe |
| P4-02 | erfüllt | `GretlServerLauncher.java`, `GretlLanguageServer.java`, `protocolStatesTrustAndExitWithoutShutdownAreRealProcessBoundaries`, eigene Gegenprobe (Shutdown/Exit bei offenem stdin, Exit 0) |
| P4-03 | erfüllt | `incrementalEventsVersionsCloseReopenAndStalePlansUseTheActualBuffer`, `cancellationAndBurstyChangesCannotPublishObsoleteRepliesAsCurrent`, `HostSchedulingTest`, `DocumentStore` |
| P4-04 | erfüllt | `standaloneWireLifecycleStandardFeaturesAndCustomEdits`, `closedBomFileRenameUsesDisplayCoordinatesAndExplicitNullVersions`, `GretlTextDocumentService`, `LspQueryMapper` |
| P4-05 | erfüllt | `LspDiagnosticMapper`, `LspQueryMapper`, echte Wiretests für URI/Version/Range/RelatedInformation und DE/EN; keine erfundene Zeile-1-Position |
| P4-06 | erfüllt | `ServerWorkspace.updateRoots`, `GretlWorkspaceService`, `invalidCombinedFolderUpdatesPreserveRootsSnapshotAndRetainedPlans`, `dirtySqlSettingsConfigurationAndFolderChangesReachRealAnalysisWithoutExecution`, eigene Gegenprobe |
| P4-07 | erfüllt | `GretlLanguageServerApi`, `GretlLanguageServer`, `GretlProtocol`, `gretl-protocol-v1.schema.json`, `everyTypedIntentIsRegisteredAndProducesAVerifiedPlanOnTheWire` (13 Intentwege) |
| P4-08 | erfüllt | `ServerConfiguration`, `LspHostSourceRepository`, `HostFileWatcher`, `quickFixCrossFileReadonlyAndMessageLimitsRemainFailClosed`, `HostBoundaryTest`; kein Schreib-/Jobstartpfad im Server |
| P4-09 | erfüllt | 9 echte `ServerProcessIT`-Fälle, 1 `ServerArchiveIT`, `HostSchedulingTest`, `HostBoundaryTest`, validierte Rootgates 899/955, gezielter Lauf 174 Java + 34 Node, keine GUI |

## 6. Neue eigene Gegenprobe

**P4-CP2 – In-flight-Anfrage während atomar abgelehntem Workspacefolder-Ereignis**

Hypothese: Ein gemischtes `workspace/didChangeWorkspaceFolders`-Ereignis, das
einen gültigen Root entfernt und gleichzeitig eine ungültige reguläre Datei
hinzufügt, muss vor jeder Mutation abgelehnt werden. Eine bereits laufende
Graphanfrage darf danach keine Antwort gegen den entfernten Root oder eine
neue Snapshotgeneration publizieren; Snapshot und retained Plan müssen
erhalten bleiben; Shutdown/Exit muss den echten Prozess bei offenem stdin mit
Code 0 beenden.

Neuheit: Der gelieferte Regressionstest sendet das Ereignis sequenziell und
prüft danach den Zustand. Diese unabhängige Probe startet stattdessen das
gepackte Kandidaten-JAR in einem fremden Arbeitsverzeichnis, verwendet eine
CRLF-Quelle mit 300 Aktionen, sendet eine `gretl/graph`-Anfrage und unmittelbar
danach das gemischte ungültige Ereignis und beobachtet die tatsächliche
Reihenfolge auf der Leitung.

Ablauf: `initialize` mit gültigem `rootUri`, `initialized`, `didOpen` für
`job.groovy`, Graph- und Plananfrage, dann gleichzeitig eine weitere
Graphanfrage und das gemischte Ereignis mit `removed=[gültiger Root]` und
`added=[reguläre Datei]`. Anschließend erneuter Graph, `gretl/validateEdit`
für den retained Plan, `shutdown` und `exit` bei offenem stdin.

Erwartung: `WORKSPACE_ROOT_UNSAFE` vor der Mutation; die in-flight-Anfrage
wird mit `CONTROL_STATE_CHANGED` abgelehnt oder liefert höchstens den alten
Snapshot; nach dem Ereignis sind Snapshot und Plan unverändert; Exit 0.

Beobachtung: Der Server protokollierte `WORKSPACE_ROOT_UNSAFE`, die in-flight-
Graphanfrage wurde mit `-32801: CONTROL_STATE_CHANGED` abgelehnt, der erneute
Graph sah dieselbe Snapshot-ID und `gretl/validateEdit` meldete
`current=true`. `shutdown`/`exit` beendeten den Prozess bei weiterhin offenem
stdin mit Code 0; der Server-stderr blieb leer.

Belege: `own-counterprobe-inflight-invalid-root.mjs`, `.log`, `.json` sowie
der Server-stderr im temporären `cp-inflight-invalid-root-*`-Verzeichnis.

## 7. Findings und Blocker

Es gibt kein offenes Mussfinding und keinen Blocker. P4-F1 ist durch die
Vorvalidierung, den echten stdio-Regressionstest und die neue unabhängige
in-flight-Gegenprobe behoben. Es wurden keine Produktdateien, Tests, Locks,
Schemata oder Spezifikationen geändert.

## 8. Urteil

**PASS**

P4-01 bis P4-09 sind durch Code, echte stdio-Prozess-/Archivtests, die neue
eigene Gegenprobe und die vollständig validierten frischen Rootgates belegt.
Die gelieferten Rootgates wurden nicht wiederholt; ihre Rohmanifeste,
XML-Zählungen, Zeitstempel, Hashkopien und Kommandologs sind konsistent. Das
Urteil ist ein P4-Urteil, kein P0–P8-Gesamturteil. Der nächste erlaubte Schritt
ist die autonome Fortsetzung mit P5 gemäß den Repository-Regeln; eine
Veröffentlichung ist nicht beauftragt.
