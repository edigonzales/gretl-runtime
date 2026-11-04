# Unabhängiger Review P5 – Installierter VS-Code-Texteditor

## 1. Identität, Umfang und Arbeitszustand

Review-ID: `00135986-ec7f-4bdb-859c-40b7d3a053bc`
Phase: P5, alle neun Musskriterien P5-01 bis P5-09 und die GUI-Fälle G01–G10.
Geprüfter Kandidat und HEAD: `4520ee6960d8b0857fad828f972a44950c5aeb09`
Assignmentbase: `185fc6700b5cb2897f724d457610a57f059b3421`
Phasebase: `0212c2feb567abd4a3a9aa77015f18b3e04be398`
Requirements-Digest: `cb2ea98d41987604be60d1a7520c3e9e129d437cbb2b9a4a1d6f5a2e085997d3`
Repository-Rules-Digest: `4b8713835c73d4a4e9bae8e49984b1696fd54dbf7f3e1b74a0eda479cd46bf2b`

OpenCode: 2.2.5; Provider: `deepseek`; API-Modell: `deepseek-flash`; Host:
`api.deepseek.com`; Alias: `DeepSeek-V4.1-Flash-1017`; effektive Basis-URL:
`https://api.deepseek.com`. Beleg: `p2-resume-readiness/identity.json`,
`checkedAt=2026-11-03T13:16:05.719Z`, mit den tatsächlichen Sessionwerten
`actualProvider=deepseek`, `actualModel=deepseek-flash` und der
Modellzuordnung aus der DeepSeek-Preis-/Modellseite. Es wird keine
kryptographische Aussage über Servergewichte behauptet.

Der Review-Worktree steht auf dem genannten Kandidaten. `git status` zeigt nur
die untracked Review-Infrastruktur `?? .opencode/` und `?? opencode.json`; es
gibt keine getrackte Produktänderung. Alle eigenen Schreibvorgänge liegen unter
dem Evidence-Verzeichnis; Produktquellen, Tests, Locks und Spezifikationen
wurden nicht verändert. Es gab keinen Push, keine Veröffentlichung und keinen
produktiven Job.

## 2. Validierung der gelieferten Rootgates

Beide vollständigen Rootgates wurden **nicht** wiederholt. Unabhängig geprüft
wurden die gelieferten Rohmanifeste, alle erfassten Rohkopien, sämtliche
Surefire-/Failsafe-XML-Dateien, deren Testzählungen, die Laufzeiten und die
Kommandologs. Beide Manifeste nennen exakt den Kandidaten
`4520ee6960d8b0857fad828f972a44950c5aeb09`, `dirty=""` und Exit 0.

| Gate | Kommando | Laufzeit (UTC) | Tests | Erfasste Dateien | Fehler/Skips | Manifest |
|---|---|---:|---:|---:|---|---|
| `root-standard` | `./mvnw -B clean verify` | 22:19:24.934–22:25:58.903 | 910 | 634/634 | 0/0/0 | `e05cb5e8d77f4ffb0151e4f4be64bfb33207c7235a12e3d741ce6d7f8c9b76d9` |
| `root-services` | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 22:25:59.172–22:35:28.415 | 966 | 819/819 | 0/0/0 | `5d64d9e1a5dc8604880e21b24a8d0c50b4f35fd8109470fe95b37366028ca425` |

Für beide Gates gilt nach der eigenen programmatischen Prüfung in
`gate-validation.json` und `validate-gates.mjs`:

- Manifest- und Report-Zählungen stimmen überein; die XML-Dateien summieren
  exakt 910 bzw. 966 Tests mit jeweils 0 Failures, 0 Errors und 0 Skips.
- Alle im Manifest erfassten Original-/Kopiepfade sind vorhanden und
  hashgleich; es gibt keine fehlenden oder abweichenden Einträge und keine
  malformed XML-Datei.
- Alle erfassten Zeitstempel liegen im jeweiligen Laufzeitfenster.
- Beide Kommandologs enden mit `BUILD SUCCESS`, enthalten keinen
  `BUILD FAILURE` und keine stille Skip-/Warning-Abschwächung.
- Die LSP-Failsafe-Suiten mit echten stdio-Prozess- und Archivtests sind in
  beiden Rootläufen enthalten. Der gelieferte `p5-texteditor-final-host.log`
  zeigt zusätzlich 2 bestandene Protokoll-Schema-Tests ohne Failures.

Alle elf in `request.json` genannten Artefakte wurden gegen ihre erwartete
SHA-256 geprüft und stimmen überein, darunter:

| Artefakt | SHA-256 |
|---|---|
| `gretl-language-contract-6.0.0-SNAPSHOT.jar` | `48328a9a519e734f59d124c13e2a00592812a09221434ad61c80a9402a16feeb` |
| `gretl-tooling-core-6.0.0-SNAPSHOT.jar` | `d6e6325b78f80a1a7ad182fbfe1e0da91a9d2006fdd20e7d88c9f31bfa0efad4` |
| `gretl-tooling-catalog-6.0.0-SNAPSHOT-catalog.jar` | `89295663d87d448920c867ed7dca3b7ee34d7a86f635188719b120ef0f6d61fd` |
| `operations.json` | `047a7131e9ce4706c5073a8600f38dbe35818623616d9a0025f405a1ddc0d911` |
| `catalog-manifest.json` | `fc69eeed1c4616c8c5e0310e3adec30f4d0a754d3d2da1dee4f32af128a10cab` |
| `gretl-lsp-6.0.0-SNAPSHOT-jar-with-dependencies.jar` | `ea2af71ed56d005e1dbe606113427be7d34d7e53c6f69a65c51128e30692462d` |
| `gretl-lsp/target/tooling-dependencies.json` | `d8780361a2135fe759ac5ae3d3c2c08ff7dde12a064eec7b1185b46ae94aeb8a` |
| `gretl-protocol-v1.schema.json` | `6f95828c38651f825f0ffdd7b21937f8c4e50332fc0f7618b6e70a2d17bc4bf6` |
| `gretl-runtime.vsix` | `0ded06d6be2eefdd8ab40f8b93f06bbe0cc0873ba0304e59f1f5fa248cdb1dae` |
| `distribution-inventory.json` | `3d91be94d80da1802b16490f91deb0b20f5f1b6caf4e09fac38823ec27dabde8` |
| `server/bundle-manifest.json` | `0082b30fac45247ccf9933d910a86aebd808aaadfbaf36d5cb857085d41b6af3` |

Der finale VSIX wurde nach `P5-final-artifact-gui/lifecycle.log` tatsächlich
installiert; dort startet der gebündelte Server mit
`SHA256 ea2af71ed56d005e1dbe606113427be7d34d7e53c6f69a65c51128e30692462d`.
Die installierten Payloadhashes in `qualification.json` und
`distribution-inventory.json` stimmen mit dem Request-Artefakt überein. Der
bekannte Unterschied zwischen dem älteren Entwicklungsbundle
`d3976927…`/`4c952f…` und dem finalen `ea2af71…`/`fc69eeed…` ist durch den
frischen finalen Build und die semantisch identische Katalog-JSON-Reihenfolge
erklärt; der finale Server wurde im selben Profil gestartet und die 18
installierten Providerfälle liefen danach grün.

## 3. Eigene ausgeführte Prüfungen

| Check | Kommando/cwd | Ergebnis | Beleg |
|---|---|---|---|
| `tooling-targeted` | `./mvnw -B -pl gretl-language-contract,gretl-tooling-core,gretl-lsp verify` in `P5-review-1/product` | Exit 0; **185 Java-Tests**; 0 Failures/Errors/Skips | `own-tooling-targeted-summary.json`, Surefire-/Failsafe-XMLs |
| `review-contract` | `node --test tools/gretl-tooling/review.test.mjs` im selben cwd | Exit 0; **32 Tests**, 0 Failures/Skips | `own-review-contract.log`, `own-node-affected.json` |
| `tooling-docs` | `node tools/gretl-tooling/check-docs.mjs` im selben cwd | Exit 0; „Validated 81 exact criteria“, Links/Inputs gültig | `own-tooling-docs.log`, `own-node-affected.json` |
| Extension-Tests | `npm test` in `vscode/gretl-vscode` | Exit 0; **17 Host-/Prozess-/Editfälle**, 0 Failures/Skips | `extension-runs.json`, `extension-npm-test.log` |
| Extension-Paket | `npm run package` im selben Verzeichnis | Exit 0; **2 Inventur-/Tamper-Fälle**, 0 Failures; 17 VSIX-Einträge geprüft | `extension-runs.json`, `extension-npm-package.log` |
| Host-Gegenprobe | `node .../counterprobe-host.mjs` | Exit 0; **5 neue Grenzfälle** grün | `counterprobe-host.json` |

Der gezielte Lauf enthält unter anderem die neuen Fälle für
Diagnostic-Retention, expliziten Einzeljobmodus und den begrenzten
Outline-`ContentModified`-Retry. Es gab keine stillen Skips und keine
abgeschwächten Assertions. Die Rootgates wurden nicht erneut gestartet.

## 4. Sichtprüfung der gelieferten GUI-Belege

Alle G01–G10 sind mit echten Screenshots, Accessibility-Texten, Assertions
und Artefakthashes vorhanden. Wegen der dokumentierten Phasendifferenz werden
unveränderte Fälle transparent wiederverwendet; die geänderten
Diagnose-/Include-/Alternativjob-/Outline-/Restart-/Crash-Pfade liegen mit
Kandidaten-SHA `4520ee6960d8b0857fad828f972a44950c5aeb09` vor. Der finale
VSIX-Smoke und die 18 installierten Providerfälle ergänzen diese nativen
Belege, ersetzen sie aber nicht.

| Fall | Beobachtung | Beleg |
|---|---|---|
| G01 | Eigene Extension-ID `edigonzales.gretl-runtime-vscode`, Version `0.1.0`, installierte VSIX-Karte; gültige Jobvorlage `new-template.groovy` ohne Jobausführung | `P5-candidate-4-gui/cases/G01-installed-identity.png`, `G01-template-saved.png` |
| G02 | Sichtbare Enum-/Feld-/Parameter-/Operationsvorschläge; angenommener Enum-Text `ZIP` im aktuellen Buffer | `P5-candidate-2-gui/cases/G02-enum-suggest.png`, `G02-enum-accepted.png` |
| G03 | Hover über Operation, Feld und Result-Ausgabe mit echter DSL-Dokumentation; Signaturhilfe sichtbar | `P5-candidate-2-gui/cases/G03-result-hover.png`, `G03-signature.png` |
| G04 | Problems mit `RESULT_DEPENDENCY_REQUIRED` an korrekter URI/Range; Diagnose bleibt auch ohne Taskstruktur erhalten; deutsche Anzeige geprüft | `P5-candidate-2-gui/cases/G04-problems-en.png`, `P5-candidate-4-gui/cases/G04-german-diagnostic.png` |
| G05 | Definition/References für Dependency, Result, Parameter, qualifiziertes Subprojektziel und drei Taskreferenzen; keine Auflösung in fremdes Workspaceprojekt | `P5-candidate-2-gui/cases/G05-task-references.png`, weitere G05-Belege |
| G06 | F2-Rename änderte Deklaration und beide Literalreferenzen gemeinsam; Kommentar und Emoji blieben; ein natives Undo stellte alle drei Stellen wieder her, ein Redo wandte sie erneut an; Buffer blieb dirty | `G06-rename-applied.png`, `G06-rename-undo.png`, `G06-rename-redo.png` |
| G07 | Recovertes Dependency-Diagnoseangebot; angewendeter Fix setzte genau eine korrekt gequotete Dependency und die Problems-Liste wurde leer; der veraltete Host-Fix wurde bei geänderter Version ohne Textänderung abgelehnt | `G07-before.png`, `G07-applied.png`, `G07-stale-offer-host.json` |
| G08 | Ungespeicherte Settings-Include-Diagnose `PROJECT_ENTRY_MISSING` am vorhandenen Literal; nach Korrektur zurückgezogen; Dirty-SQL zeigte `SQL_PARAMETER_MISSING`, nach Korrektur ohne Speichern/Neustart leer | `P5-candidate-5-gui/cases/G08-settings-missing-current.png`, `G08-settings-restored-current.png`, `G08-sql-bad-current.png`, `G08-sql-cleared-current.png` |
| G09 | Nach begrenztem Crash/Neustart neue Serverinstanz, `GRETL: Ready`, intakte Dirtybuffer, sichtbare Outline `download`/`query`; alter Prozess beendet, kein Dauerprozess | `P5-candidate-5-gui/cases/G09-final-native.png`, `crash-recovery.png`, `server-lifecycle-with-crashes.log` |
| G10 | Untrusted Workspace zeigt `GRETL: Workspace trust required`/Restricted Mode; fehlender Java-Pfad und fehlendes Bundle zeigen verständliche Fehler; Erholung mit echtem Java25 und identischem JAR funktioniert bei Unicode-/Leerzeichen-/Metazeichenpfaden | `P5-candidate-2-gui/cases/G10-untrusted.png`, `P5-candidate-4-gui/cases/G10-missing-java.png`, `G10-recovery-paths-valid-jdk.png` |

## 5. Bewertung der Musskriterien

| Kriterium | Status | Wesentliche Belege |
|---|---|---|
| P5-01 | erfüllt | Finale `qualification.json`/`distribution-inventory.json`, Bundle-Manifest, VSIX-/JAR-Hashes, Lifecycle-Start mit `ea2af71…`, G01-Screenshot |
| P5-02 | erfüllt | `package.json` mit exakt `job.groovy`/`settings.gretl`, `host.ts`-Sprachkonfiguration, expliziter Alternativjob `new-template.groovy`, G05-Subprojekt, `foreign.groovy` bleibt Groovy |
| P5-03 | erfüllt | G02–G05 und G09 mit echten Tastatur-/Mausbelegen, AX-Texten und Screenshots; Completion, Hover, Signatur, Problems, Navigation, References und Outline sichtbar/sourcegenau |
| P5-04 | erfüllt | G06-Rename/Undo/Redo, G07-QuickFix, `edit-guard.ts`, `extension.ts`, Host-Editfälle, veralteter Fix abgelehnt, keine `fs.writeFile`-Abkürzung |
| P5-05 | erfüllt | G01-Jobtemplate, Status/Output in G09, G10-Fehlerzustände, README zu Java25/lokalen Grenzen, keine eingebauten Geheimnisse |
| P5-06 | erfüllt | G09-Neustart/Crash-Recovery, `crash-1/2/3.json`, `server-lifecycle-with-crashes.log`, `process.ts`, echte Prozess-/Terminierungstests |
| P5-07 | erfüllt | `untrustedWorkspaces: limited`, Trust-/Local-Filesystem-Prüfung in `extension.ts`/`host.ts`, G10-Restricted-Mode, nur Sprache/Trust als Serverkonfiguration |
| P5-08 | erfüllt | G01–G10 mit Screenshots/Assertions/Hashes, finaler VSIX-Smoke, 18 installierte Providerfälle, `gui/index.json` und `gui/files.json` |
| P5-09 | erfüllt | Valdierte Rootgates 910/966, gezielter Lauf 185 Java + 32 Contract, 17 Host + 2 Paket, Docs 81, Dependencyinventur; keine vorgezogene Modelerarbeit |

## 6. Neue eigene Gegenproben

### 6.1 Host-Grenzen (ausgeführt, grün)

**CP-HOST-01 – Exakte Dateigrenze, ungültiges UTF-8, Eltern-Symlink und Lone-Low-Surrogate**

Hypothese: Die diskbasierte Edit-Vorbedingung muss die exakte 4-MiB-Grenze
akzeptieren, 4 MiB + 1 Byte ablehnen, ungültiges UTF-8 niemals als hashbare
Quelle behandeln, einen nach der Capture eingeführten Elternverzeichnis-Symlink
nicht verfolgen und einen Lone-Low-Surrogate verlustfrei UTF-16BE hashen.

Neuheit: Der gelieferte Guard-Test prüft 4 MiB + 1 Byte, einen Datei-Symlink
und einen Lone-High-Surrogate, aber nicht die exakte Grenze, ungültiges UTF-8,
einen **Eltern**-Symlink oder einen Lone-Low-Surrogate.

Ablauf: Bundle von `src/edit-guard.ts` und `src/host.ts` mit dem
Produkt-esbuild; danach fünf isolierte Dateisystem-/Hashfälle.

Beobachtung: Alle fünf Fälle bestanden; `counterprobe-host.json` meldet
`passed: true`. Es gab keine Produktänderung.

Belege: `counterprobe-host.mjs`, `counterprobe-host.json`.

### 6.2 Native GUI-Gegenprobe (blockiert)

Geplant war ein neuer Cross-Projekt-Rename: `sub/job.groovy` öffnen,
`action('child')` per nativem F2 umbenennen, die qualifizierte Referenz
`:sub:child` im Hauptjob prüfen und anschließend Undo/Redo sowie Dirtyzustände
verifizieren. Das ist gegenüber G06 (lokaler Task `download`) und G07
(QuickFix) neu und prüft die Projektgrenze.

Der mandatierte Transport lieferte jedoch keine Antwort. Request `001`
(openFile + observe) wartete 9 Minuten, Request `002` (observe only) wartete
3 Minuten; `gui-bridge/responses/` blieb leer. Die Konsole meldet
`CGSSessionScreenIsLocked=Yes` (siehe `check-lock2.mjs` und
`gui-bridge-transport-blocker.json`). Damit konnte weder eine native Aktion
noch ein neuer Screenshot erzeugt werden. Eine Ersatzlösung über
Provider-API, AppleScript/osascript oder eine andere UI-Automation ist nach
Auftrag ausgeschlossen. Credentials wurden nicht verwendet und die Sperre
wurde nicht umgangen.

Belege: `gui-bridge/requests/001.json`, `002.json`, `wait-001.mjs`,
`wait-002.mjs`, `check-lock2.mjs`, `gui-bridge-transport-blocker.json`.

## 7. Findings und Blocker

Es gibt **kein offenes fachliches Mussfinding** gegen den Kandidaten. Die
gelieferten Rootgates, Artefakte und G01–G10-Belege sind konsistent; die
gezielten Tests und die neue Host-Gegenprobe sind grün. Es wurden keine
Produktdateien, Tests, Locks, Schemata oder Spezifikationen geändert.

Der Review ist dennoch **BLOCKED** durch den Review-Transport: Die
vorgeschriebene eigene native GUI-Gegenprobe konnte nicht ausgeführt werden,
weil die isolierte macOS-Konsole gesperrt ist und der Bridge-Dienst für zwei
dokumentierte Requests keine Antwort erzeugte. Das ist kein Produktfehler,
sondern ein objektiver Infrastrukturblocker. Ohne Entsperren oder einen
funktionierenden Bridge-Dienst ist keine unabhängige native
Tastatur-/Mausprüfung möglich; ein Ersatz durch API-Aufrufe wäre keine
GUI-Abnahme.

**Unblock:** die isolierte Konsole entsperren und die Bridge-Requests bedienen,
danach die neue Cross-Projekt-Rename-Gegenprobe ausführen. Die vollständigen
Rootgates dürfen dabei nicht wiederholt werden.

## 8. Urteil

**BLOCKED**

Die gelieferten Belege stützen alle neun P5-Kriterien, und die eigenen
statischen, Host-, Paket- und Dokumentationsprüfungen bestehen. Die formale
Abnahme ist jedoch nicht möglich, solange die eigene native GUI-Gegenprobe
wegen der gesperrten Konsole und des nicht antwortenden Bridge-Transports
fehlt. Dieses Urteil ist kein P0–P8-Gesamturteil; es gibt keine
Veröffentlichung und keinen produktiven Job.
