# Unabhängige Gesamtabnahme P8

**Kandidat:** `54bb123070fc7bd81aea58b4e1191beecf568cbf`
**Assignment-Basis:** `185fc6700b5cb2897f724d457610a57f059b3421`
**Phasenbasis:** `3172c48f712d73f645761b7ec1886eed31f225a5`
**Reviewer:** OpenCode, Provider `deepseek`, Modell `deepseek-flash`, API-Host `api.deepseek.com` (Modellmapping `DeepSeek-V4.1-Flash-1017`), frische Identitätsbelege.
**Ergebnis:** **PASS** — 81 von 81 Kriterien erfüllt, keine offenen Mussbefunde.

## Umfang und Vorgehen

Geprüft wurden der kumulative Diff von der Assignment-Basis bis zum Kandidaten, alle 81 Kriterien aus `ACCEPTANCE_CRITERIA.json`, die P8-Implementierung, die Akzeptanzkarte und die vorhandenen Originalreviews. Die vom Auftrag gelieferten vollständigen Rootgates wurden anhand ihrer Rohbelege unabhängig validiert, nicht wiederholt; kein `clean verify` wurde erneut ausgeführt. Eigene Prüfungen beschränkten sich auf die verlangte Dokumentationsprüfung, die betroffenen risikotragenden Node-Tests, die Validierung der Rootbelege sowie zwei neue Gegenproben.

## Rootgate-Audit

Beide Manifeste wurden mit `verifyCurrentGateManifest` gegen Kandidaten-SHA, exakte Argumentliste, jeden erfassten Dateidigest, Änderungszeit und die rohen Surefire-/Failsafe-XML-Dateien geprüft:

| Gate | Kommando | Tests | Failures | Errors | Skips | Erfasste Dateien | XML |
|---|---|---:|---:|---:|---:|---:|---:|
| `root-standard` | `./mvnw -B clean verify` | 918 | 0 | 0 | 0 | 642 | 176 |
| `root-services` | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 974 | 0 | 0 | 0 | 827 | 190 |

Beide Läufe endeten mit Exit 0 auf `54bb123070fc7bd81aea58b4e1191beecf568cbf`. Die elf angeforderten Artefakte existieren und stimmen mit ihren SHA-256-Werten überein, darunter der finale VSIX `c501014f...`, der LSP-Server `36d8bdd4...`, der Katalog `047a7131...` und das Katalogmanifest `65c9ae36...`.

**Offengelegte Abweichung:** Das Standardgate erzeugte den VSIX `cdec076b...`, das abschliessende Dienstgate `c501014f...`. Beide gehören zum selben Kandidaten; der Unterschied betrifft ausschliesslich die Reihenfolge der Mitglieder im Katalogmanifest innerhalb des JAR. Der finale VSIX entspricht exakt dem angeforderten Artefakt, und der Effektivinhalt von Client und Java-Nutzlast ist identisch. Kein Befund.

## Kriterienprüfung

Alle 81 Kriterien P0-01 bis P8-09 sind auf aktuellen Code, Tests, Rootbelege und unveränderte Originalreviews abgebildet. Die früheren Phasen P0 bis P7 besitzen gültige unabhängige PASS-Nachweise; frühere FAIL-Läufe bleiben erhalten. Für P8 wurden Resilienz, Paketintegrität, Serverneustart, Layoutstress, Dokumentation, Akzeptanzkarte und die neue zusammengesetzte GUI-Gegenprobe geprüft. `implementation/P8.md` und `STATUS.md` führen P8 zu Recht noch als `IN PROGRESS`, und die Akzeptanzkarte steht vor dieser Abnahme auf `MAPPED_PENDING_FINAL_EVIDENCE`; darin liegt keine Selbstfreigabe.

## GUI-Evidenz

143 Bild- und AX-Hashes sowie alle 547 Einträge in `gui/files.json` wurden nachgerechnet. Die 30 GUI-Fälle G01–G30 sind vollständig abgedeckt. G01–G29 wurden als `verified-current` anhand der vorhandenen frischen Aufnahmen und der finalen installierten Nutzlast geprüft. G30 wurde als eigene neue zusammengesetzte native Sequenz über `gui-bridge` ausgeführt:

1. Neue Fixture `project-created/reviewer-compound/job.groovy` erzeugt (Ausgangshash `373346d7...`).
2. Dirty Edit im nativen Editor (Version 2, `62c5c147...`).
3. Modeler-Bindung `fetch/file -> unpack/archive` bestätigt; Buffer enthält genau `archive = result('fetch').file` (Version 3, `2d6b2288...`).
4. Natives `Undo text` stellt exakt den Dirty-Text vor der Bindung wieder her (Version 4, `62c5c147...`).
5. `Reset layout` lässt den Quelltext unverändert.
6. `GRETL: Restart Language Server` ersetzt die Serverinstanz `e2344a11...` durch `2693b140...` und bewahrt den Dirty-Puffer.
7. Schliessen zeigt den nativen Speichern-Dialog; `Don't Save`, erneutes Öffnen lädt die Baseline `373346d7...` ohne Dirty-Marker und ohne Bindungs-Kante.

Der erste Bindungsversuch (Antwort 003) scheiterte mit `Bind result: matches 0`; der Wiederholungsversuch (Antwort 004) war erfolgreich. Der Fehlversuch bleibt als Beleg erhalten und wird nicht als positives Ergebnis gezählt. Die zugehörigen Bilder 001, 002, 005–012 wurden persönlich gelesen.

## Gegenproben

1. **G30 native Verbundfall** (oben): neue Fixture und neue Sequenz aus Dirty Edit, Ergebnisbindung, nativem Undo, Layout, Serverneustart, Schliessen und Wiederöffnen. Erwartung und beobachtetes Verhalten stimmen exakt überein; keine Quelltextkorruption, kein veralteter Zustand.
2. **Doppeltes Bundle-Manifest:** Eine Kopie des finalen VSIX wurde um ein zweites, widersprüchliches `extension/server/bundle-manifest.json` ergänzt. Das ausgelieferte `scripts/inventory.mjs` beendet sich mit Exit 1 und `Duplicate VSIX entries (20 !== 21)`; es wird kein Erfolgsinventar geschrieben. Der Originalhash `c501014f...` blieb unverändert.

## Ausgeführte Prüfungen

- Dokumentationsgate: `node tools/gretl-tooling/check-docs.mjs` — Exit 0.
- Betroffene Review-/Protokolltests: 34 Tests, 0 Fehler, 0 Skips.
- Betroffene Extension-Risikotests (`workbench-evidence`, `modeler-layout`, `modeler-controller`): 25 Tests, 0 Fehler, 0 Skips.
- Paket-/Serverintegritätstests: 3 Tests, 0 Fehler, 0 Skips.
- Eigene Rootbelegvalidierung, G30-Verifikation und Paket-Gegenprobe: Exit 0.
- Eigenes Inventar des finalen VSIX: 20 erwartete Einträge vollständig und konsistent.

## Offenlegungen ohne Befund

- **G13-Bildidentität:** Die G13-Aufnahme verweist auf den vorkorrigierten Kandidaten `b5bead4...`. Die finale GUI-Evidenz stützt sich auf die dokumentierte Effektivinhalt-Identität und die Verifikation der final installierten Nutzlast; die Anleitung verlangt keine Wiederholung unveränderter nativer Fälle nur wegen Quellmetadaten. Es gibt keinen Gegenbeleg für eine abweichende Nutzlast.
- **VSIX-Bytevarianz:** Siehe Rootgate-Audit; nur Manifest-Reihenfolge, finales Artefakt und Effektivinhalt stimmen.
- **P8-Status:** `IN PROGRESS` und `MAPPED_PENDING_FINAL_EVIDENCE` sind vor dieser unabhängigen Abnahme korrekt und werden nicht als Selbstakzeptanz gewertet.

## Befunde

Keine offenen Mussbefunde, keine fehlenden Pflichtprüfungen. **Gesamturteil: PASS.**
