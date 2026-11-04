# Unabhängiger Review P5 – Installierter VS-Code-Texteditor (fortgesetztes Original)

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
Modellzuordnung aus der DeepSeek-Preis-/Modellseite. Der Identitätshash ist
gegenüber der Originalprüfung unverändert
(`5b5f5180de93622c14265dcd7cd0368f2aa468c48366fa842f6bec08b21f6fce`). Es wird
keine kryptographische Aussage über Servergewichte behauptet.

Dies ist die Fortsetzung **desselben** unabhängigen Reviews am unveränderten
Kandidaten. Das Originalurteil war `BLOCKED`, ausschliesslich wegen der noch
fehlenden eigenen nativen GUI-Gegenprobe (Kategorie `REVIEW_TRANSPORT`; siehe
Abschnitt 6.2). Alle fachlichen Kriterien, Rootgates und gezielten Prüfungen
waren bereits bestätigt. Im fortgesetzten Review wurde **kein** Root-, Maven-,
Fach-, GUI-Voll-, npm- oder Pakettest wiederholt; ausgeführt wurde nur die
fehlende native GUI-Gegenprobe über die GUI-Bridge sowie Identitäts-,
Unverändertheits- und Belegzuordnungsprüfungen. Die Originaldateien
`review.md`, `verdict.json`, `process.json` und `validation.json` wurden nicht
angetastet.

Unverändertheitsbeleg der Originalartefakte (Hashes und mtimes in
`resume-1/identity-unchanged-check.json`):

| Originalartefakt | SHA-256 | mtime (UTC) |
|---|---|---|
| `request.json` | `72ebfc66b941213e5732e4d3189d492cd86db38f9c6b4ba4263ac81a2c59222b` | 2026-11-03T22:35:50.546Z |
| `review.md` | `9c1614461c0601ccd0cf17992ecd71e6f209229819b681e425bd9f0dc285f963` | 2026-11-03T23:00:44.292Z |
| `verdict.json` | `caed463dac1a2b5f12e0c4c3821e0e6cedad5a36877d3fe24cec8db633d9a707` | 2026-11-03T23:00:26.775Z |
| `process.json` | `c3f4fc708aad527f38961269859aaaa70dfc2c70d1ef8beefa54e8b8455fd850` | 2026-11-03T23:01:33.632Z |
| `validation.json` | `82d671216a01c8f5609d9d9570b91c640bc0650b020def6893d6c9596cdbc082` | 2026-11-03T23:01:33.844Z |
| `gate-validation.json` | `22c89ab90fcec038b24f8ab54a6c0c03708729bd65f078c688a9f4593a57e682` | 2026-11-03T22:37:19.188Z |

Der Review-Worktree steht auf dem genannten Kandidaten; `git status` zeigt
keine getrackte Änderung, nur die untracked Review-Infrastruktur
`?? .opencode/...` und `?? opencode.json`. Auch der beworbene Produktcheckout
`/Users/stefan/sources/gretl-runtime` steht auf exakt
`4520ee6960d8b0857fad828f972a44950c5aeb09` und ist getrackt sauber. Alle
eigenen Schreibvorgänge dieser Fortsetzung liegen unter
`P5-review-1/evidence/resume-1/`; die neuen GUI-Requests/Antworten liegen
unter `P5-review-1/evidence/gui-bridge/`. Produktquellen, Tests, Locks,
Schemata und Spezifikationen wurden nicht verändert. Es gab keinen Push,
keine Veröffentlichung und keinen produktiven Job.

## 2. Validierung der gelieferten Rootgates

Beide vollständigen Rootgates wurden **nicht** wiederholt. Unabhängig geprüft
waren bereits im Originalreview die gelieferten Rohmanifeste, alle erfassten
Rohkopien, sämtliche Surefire-/Failsafe-XML-Dateien, deren Testzählungen, die
Laufzeiten und die Kommandologs. Beide Manifeste nennen exakt den Kandidaten
`4520ee6960d8b0857fad828f972a44950c5aeb09`, `dirty=""` und Exit 0.

| Gate | Kommando | Laufzeit (UTC) | Tests | Erfasste Dateien | Fehler/Skips | Manifest-SHA-256 |
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

Im fortgesetzten Review wurden diese Belege **gelesen und erneut gehasht,
nicht neu ausgeführt**: Beide Manifest-SHA-256 stimmen weiterhin exakt mit den
in `gate-validation.json` festgehaltenen Werten überein; alle elf in
`request.json` genannten Artefakte stimmen mit ihrer erwarteten SHA-256
überein; alle 295 Dateien der GUI-Inventur stimmen mit `gui/files.json`
überein; die Requirements-/Rules-Digests wurden aus dem Produkt erneut
berechnet und stimmen mit `request.json` überein. Details:
`resume-1/identity-unchanged-check.json`.

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

## 3. Eigene ausgeführte Prüfungen (aus dem Originalreview, nicht wiederholt)

Die folgenden Prüfungen wurden **vor** dem ursprünglichen Blocker in dieser
Review-Session tatsächlich ausgeführt. Im fortgesetzten Review wurden sie
nicht erneut gestartet; ihre Belege sind unverändert vorhanden und werden
weiterhin als eigene, bereits erbrachte Läufe geführt (keine Umetikettierung).

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
und Artefakthashes vorhanden; die 295 Dateien der GUI-Inventur wurden im
fortgesetzten Review erneut hashgeprüft (295/295). Wegen der dokumentierten
Phasendifferenz werden unveränderte Fälle transparent wiederverwendet; die
geänderten Diagnose-/Include-/Alternativjob-/Outline-/Restart-/Crash-Pfade
liegen mit Kandidaten-SHA `4520ee6960d8b0857fad828f972a44950c5aeb09` vor.
Der finale VSIX-Smoke und die 18 installierten Providerfälle ergänzen diese
nativen Belege, ersetzen sie aber nicht.

| Fall | Beobachtung | Beleg |
|---|---|---|
| G01 | Eigene Extension-ID `edigonzales.gretl-runtime-vscode`, Version `0.1.0`, installierte VSIX-Karte; gültige Jobvorlage `new-template.groovy` ohne Jobausführung | `P5-candidate-4-gui/cases/G01-installed-identity.png`, `G01-template-saved.png` |
| G02 | Sichtbare Enum-/Feld-/Parameter-/Operationsvorschläge; angenommener Enum-Text `ZIP` im aktuellen Buffer | `P5-candidate-2-gui/cases/G02-enum-suggest.png`, `G02-enum-accepted.png` |
| G03 | Hover über Operation, Feld und Result-Ausgabe mit echter DSL-Dokumentation; Signaturhilfe sichtbar | `P5-candidate-2-gui/cases/G03-result-hover.png`, `G03-signature.png` |
| G04 | Problems mit `RESULT_DEPENDENCY_REQUIRED` an korrekter URI/Range; Diagnose bleibt auch ohne Taskstruktur erhalten; deutsche Anzeige geprüft | `P5-candidate-2-gui/cases/G04-problems-en.png`, `P5-candidate-4-gui/cases/G04-german-diagnostic.png` |
| G05 | Definition/References für Dependency, Result, Parameter, qualifiziertes Subprojektziel und drei Taskreferenzen; keine Auflösung in fremdes Workspaceprojekt | `P5-candidate-2-gui/cases/G05-task-references.png`, weitere G05-Belege |
| G06 | F2-Rename änderte Deklaration und beide Literalreferenzen gemeinsam; Kommentar und Emoji blieben; ein natives Undo stellte alle drei Stellen wieder her, ein Redo wandte sie erneut an; Buffer blieb dirty | `G06-rename-applied.png`, `G06-rename-undo.png`, `G06-rename-redo.png`; zusätzlich neue eigene Gegenprobe CP-GUI-01 |
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
| P5-04 | erfüllt | G06-Rename/Undo/Redo, G07-QuickFix, `edit-guard.ts`, `extension.ts`, Host-Editfälle, veralteter Fix abgelehnt, keine `fs.writeFile`-Abkürzung; neue eigene native Cross-Subprojekt-Gegenprobe CP-GUI-01 (Requests 004–009) |
| P5-05 | erfüllt | G01-Jobtemplate, Status/Output in G09, G10-Fehlerzustände, README zu Java25/lokalen Grenzen, keine eingebauten Geheimnisse |
| P5-06 | erfüllt | G09-Neustart/Crash-Recovery, `crash-1/2/3.json`, `server-lifecycle-with-crashes.log`, `process.ts`, echte Prozess-/Terminierungstests |
| P5-07 | erfüllt | `untrustedWorkspaces: limited`, Trust-/Local-Filesystem-Prüfung in `extension.ts`/`host.ts`, G10-Restricted-Mode, nur Sprache/Trust als Serverkonfiguration |
| P5-08 | erfüllt | G01–G10 mit Screenshots/Assertions/Hashes, finaler VSIX-Smoke, 18 installierte Providerfälle, `gui/index.json` und `gui/files.json`; neue eigene G06-Laufbelege mit verifizierten PNG-/AX-Hashes |
| P5-09 | erfüllt | Validierte Rootgates 910/966, gezielter Lauf 185 Java + 32 Contract, 17 Host + 2 Paket, Docs 81, Dependencyinventur; neue native GUI-Gegenprobe; unveränderte Artefakt-/Inventarhashes |

## 6. Neue eigene Gegenproben

### 6.1 Host-Grenzen (im Originalreview ausgeführt, grün)

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

### 6.2 Native Cross-Subprojekt-Gegenprobe (neu ausgeführt)

**CP-GUI-01 – Subprojekt-Rename mit qualifizierter Hauptjobreferenz und Undo/Redo**

Hypothese: Ein nativer F2-Rename der Subprojektdeklaration `action('child')`
in `sub/job.groovy` muss die qualifizierte Referenz `:sub:child` im Hauptjob
mitändern. Ein natives Undo im Hauptjob muss beide Stellen zurücksetzen, ein
natives Redo beide wiederherstellen.

Neuheit: Die gelieferten G06-Belege zeigen einen lokalen Task-Rename mit
Undo/Redo im selben Job, G07 zeigt einen QuickFix. Der neue Fall überschreitet
die Subprojektgrenze über eine qualifizierte Literalreferenz und prüft das
dateiübergreifend gekoppelte Undo/Redo in der real installierten Oberfläche.
Er ist damit gegenüber G06/G07 neu und nicht durch die gelieferten Belege
abgedeckt.

Ausgangslage vor der Probe (eigene Dateisystemprüfung vor Request 004):
Hauptjob enthält `action('done', dependsOn:['extract', ':sub:child'])`;
Subjob enthält `action('child')`. Der Hauptjob-Buffer war bereits aus der
früheren GUI-Inventur mit einem ungespeicherten statischen SQL-Overlay-Block
dirty; diese Vorbelegung ist nicht Teil der Gegenprobe.

Ablauf über die mandatierte native GUI-Bridge (Agent A nur Transport; keine
Ersatz-API, kein AppleScript/osascript, keine Credentials):

1. `003` (exaktes Replay meines ursprünglichen Baseline-Requests `001` nach
   der fehlgeschlagenen stale App-Bindung): Subjob öffnen, AX/Screenshot
   erfassen. Ergebnis: `action('child')`, Outline `child`, `GRETL: Ready`.
2. `004`: im Subjob `child` suchen, Escape, F2, `renamedChild` einfügen,
   Return. AX-Meldung: `Successfully renamed 'child' to 'renamedChild'.
   Summary: Made 2 text edits in 2 files`. Screenshot: Deklaration
   `action('renamedChild')`, Outline `renamedChild`.
3. `005`: Hauptjob öffnen und `:sub:renamedChild` suchen. Screenshot: Zeile 12
   lautet `action('done', dependsOn:['extract', ':sub:renamedChild'])`.
4. `006`: natives `Undo` im Hauptjob. Screenshot: Zeile 12 wieder
   `action('done', dependsOn:['extract', ':sub:child'])`.
5. `007`: Subjob nach dem Undo öffnen. Screenshot: wieder `action('child')`,
   Outline `child`.
6. `008`: Hauptjob öffnen, natives `Redo`. Screenshot: Zeile 12 wieder
   `:sub:renamedChild`.
7. `009`: Subjob nach dem Redo öffnen. Screenshot: wieder
   `action('renamedChild')`, Outline `renamedChild`.

Ergebnis: Deklaration und qualifizierte Hauptjobreferenz wurden gemeinsam
geändert; der Kommentar mit Emoji in Zeile 1 des Hauptjobs blieb erhalten; ein
einzelnes natives Undo im Hauptjob setzte beide Dateien zurück, ein einzelnes
natives Redo stellte beide wieder her. Das ist die erwartete, gekoppelte
Cross-File-Semantik und schliesst die einzige im Originalurteil fehlende
Pflichtprüfung.

Alle sieben PNGs (`003`–`009`) und die zugehörigen AX-Texte wurden persönlich
gesichtet. Die von der Bridge deklarierten PNG-/AX-SHA-256 wurden mit eigenen
Berechnungen verifiziert (alle 7/7 stimmen; Details in
`resume-1/identity-unchanged-check.json`).

| Request | Beobachtung | PNG-SHA-256 |
|---|---|---|
| `003` | Baseline Subjob: `action('child')`, Outline `child` | `fac63f7e681fa62e06aad62bb6b0e0fb76e652bd27940794cd1f6b4be8e610c1` |
| `004` | Rename angewendet; AX: 2 Textedits in 2 Dateien | `b96cee494182bba685a38b463af2a6bf586f64f7685356b4e40165d42b97d387` |
| `005` | Hauptjob: `:sub:renamedChild` | `022354d2b6e36b27f7debbc2de54d537360d2a15e455dda604688dc6528fcaac` |
| `006` | Undo im Hauptjob: `:sub:child` | `3f471c293f6ae843c1d99a2d74f1f922db50e9a4f90b786f60dc86c82b776b09` |
| `007` | Subjob nach Undo: `action('child')` | `982a57b99d2182fd030f25b66a3c43bcc2f7a960f0b6f09fc728d40a4b36bce1` |
| `008` | Redo im Hauptjob: `:sub:renamedChild` | `24bd9c17863f118fc5c72709402f70eef30969e054a1a0fb7a5cf01d9432d6a9` |
| `009` | Subjob nach Redo: `action('renamedChild')` | `bf07f1a8870008973d36905950f1577dd339555ca14dda8b23cd9ecd8cefce97` |

Beobachtungsgrenzen (transparent, keine Produktfindings):

- Die eingeblendete Fundstelle im deaktivierten Find-Widget zeigt nach
  Escape/Undo einen veralteten Zähler (`1 of 1`). Sie wurde **nicht** als
  Beweis verwendet; massgeblich sind die Editorinhalte und die AX-Rename-
  Meldung.
- Die isolierte Testfixture wurde während der Probe offenbar durch das
  Auto-Save-/Fokusverhalten der Editorinstanz auf Platte gespeichert: Beide
  bearbeiteten Dateien tragen exakt `mtime=2026-11-04T06:14:15.102Z`, und der
  zuvor nur im Buffer vorhandene SQL-Overlay-Block steht nun auch im
  Hauptjob auf Platte. Deshalb schwankende Unsaved-Badges (3→2→4→2) sind kein
  Beweis; die Gegenprobe stützt sich ausschliesslich auf die sichtbaren
  Editorinhalte und AX-Texte. Diese Speicherung betraf nur die disposable
  Fixture unter `P5-candidate-2-gui`, nicht das Produkt-Repository; alle
  Produktpfade blieben unverändert.
- `001` selbst endete nach dem Entsperren mit dem erwarteten
  Bindungsfehler (`Computer Use is not active … get_app_state`); `002` war
  die erfolgreiche Liveness-Beobachtung; `003` wiederholte exakt die
  `001`-Aktionen. Die Requests `004`–`009` sind meine eigenen, neu gewählten
  Aktionen.

Belege: `gui-bridge/README.md`, `gui-bridge/status.json`,
`gui-bridge/requests/003.json`–`009.json`,
`gui-bridge/responses/003.json`–`009.json` samt `.ax.txt`/`.png`,
`resume-1/identity-unchanged-check.json`.

## 7. Findings und Blockerstatus

Es gibt **kein offenes fachliches Mussfinding** gegen den Kandidaten. Die
gelieferten Rootgates, Artefakte und G01–G10-Belege sind konsistent; die
gezielten Tests und die Host-Gegenprobe sind grün; die neue native
GUI-Gegenprobe ist vollständig und erfolgreich. Es wurden keine
Produktdateien, Tests, Locks, Schemata oder Spezifikationen geändert.

Der ursprüngliche Blocker (`REVIEW_TRANSPORT`: gesperrte macOS-Konsole,
keine Antwort auf Requests `001`/`002`, leeres `responses/`) ist **behoben**.
Nach manueller Entsperrung, Rebind und Bedienung der Bridge sind `002`
(Liveness) und `003` (Replay der Baseline) sowie die eigenen Requests
`004`–`009` beantwortet und ausgewertet. Die einzige fehlende Pflichtprüfung
ist damit erbracht; ein Ersatz durch Provider-API oder andere UI-Automation
war weiterhin nicht nötig und wurde nicht verwendet.

## 8. Urteil

**PASS**

Alle neun P5-Kriterien sind durch die gelieferten frischen Rootgates, elf
hashgleiche Artefakte, 295 hashgleiche GUI-Dateien und G01–G10 gestützt. Die
eigenen gezielten Java-, Node-, Extension-, Paket- und Doku-Prüfungen sowie
die Host-Gegenprobe sind grün. Die ursprünglich fehlende eigene native
GUI-Gegenprobe ist jetzt als Cross-Subprojekt-Rename mit qualifizierter
Hauptjobreferenz und gekoppeltem Undo/Redo in beiden Dateien belegt und wurde
anhand der echten Bilder und AX-Texte selbst bewertet. Es gibt kein offenes
Mussfinding, keine Produktänderung, keine Wiederholung der Rootgates, keine
Veröffentlichung und keinen produktiven Job. Dieses Urteil ist kein
P0–P8-Gesamturteil; das äussere Prozessende und die Modellidentität werden
erst nach dem tatsächlichen Sessionende validiert.
