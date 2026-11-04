# Unabhängige P7-Abnahme – Schreibender Modeler

**Kandidat:** `53f7f3daaf92d002fd75978c25471321fd99a310`  
**PhaseBase:** `230e6ccb977414d6fcb5eff5798a16bb9cd6da81`  
**AssignmentBase:** `185fc6700b5cb2897f724d457610a57f059b3421`  
**Ergebnis:** **PASS**

## 1. Prüfumfang und Methode

Ich habe den committed Kandidaten, den vollständigen P7-Diff, die unveränderlichen Spezifikationen, den Implementierungsbericht sowie die gelieferten Rohbelege geprüft. Die beiden vollständigen Rootgates wurden **nicht erneut ausgeführt**. Ich habe ihre Originalmanifeste, Kommando-/Rohlogs, Test-XMLs, Zeitfenster und SHA-256-Werte unabhängig validiert. Ergänzend habe ich nur betroffene risikotragende Tests und zwei neue Gegenproben ausgeführt:

- einen echten LSP-stdio-Test gegen den finalen Server-JAR,
- einen echten GUI-Fall über die gui-bridge auf dem final installierten VSIX.

Der Arbeitsbaum blieb unverändert. `git rev-parse HEAD` liefert exakt den Kandidaten; `git status --porcelain=v1 --untracked-files=all` zeigt nur die beiden erwarteten, nicht versionierten Review-Agentdateien `.opencode/agents/gretl-independent-review.md` und `opencode.json`. Es gab keine getrackten Änderungen, keine Produktkorrektur, keinen Commit, keinen Push und keine Veröffentlichung.

## 2. Validierte Rootbelege

Die gelieferten Manifeste sind frisch, sauber und dem Kandidaten zugeordnet. Ich habe die Inhalte der erfassten Dateien erneut gehasht und die aufgezeichneten Quellzeiten gegen das jeweilige Laufzeitfenster geprüft (die kopierten Datei-mtimes wurden beim Staging zurückgesetzt; die aufgezeichneten `sourceModifiedAt`-Werte sind gültig).

| Gate | SHA / dirty | argv | Laufzeit | Tests | Fehler | Build |
|---|---|---|---|---|---|---|
| `root-standard` | `53f7f3da…a310`, dirty leer | `./mvnw -B clean verify` | 2026-11-04T10:17:07.086Z–10:23:47.349Z | **918** | 0 Fehler, 0 Failures, 0 Skips | Exit 0, BUILD SUCCESS |
| `root-services` | `53f7f3da…a310`, dirty leer | `./mvnw -B -Pgeotools-probe,backing-services clean verify` | 2026-11-04T10:23:47.624Z–10:33:34.122Z | **974** | 0 Fehler, 0 Failures, 0 Skips | Exit 0, BUILD SUCCESS, Docker/Testcontainers und GeoTools-Probe ausgeführt |

- `root-standard`: 642 erfasste Einträge, 176 Surefire-/Failsafe-Suites, alle Inhalts-Hashes und Zeitangaben gültig.
- `root-services`: 827 erfasste Einträge, 190 Suites, alle Inhalts-Hashes und Zeitangaben gültig.
- Der T31-Test `GraphicalModelerScriptJobIT` ist in beiden Gates vorhanden und bestanden. Der exakte GUI-Quelltext hat SHA-256 `7a551b46f463b2c483c1936b18fbd7f4a5d9c530e481e1f2834842c9be3b7782`, identisch mit dem eingecheckten Runtime-Fixture und mit `gui-provenance.json`. Die Runtime-Identität wurde um 2026-11-04T10:28:35.949Z innerhalb des Dienstfensters geschrieben, verwendet `mode: SCRIPT`, genau einen HTTP-Request und den erwarteten Output-Hash. Die lokale `sourceUrl`-Übersteuerung verändert die Quellbytes nicht.
- Alle **11** im Request gelisteten Artefakte stimmen mit ihren SHA-256-Werten überein. Das finale VSIX `33998c625a87e8f9e32f9c3d8a82cee0b897666be11f2902fb13a4c7cd7af257` und der finale Server `f6acda578e81d806da9a016bdfdad00c67ee35a2640bfee21daa4845f63d0465` stimmen mit dem installierten Extension-Verzeichnis überein.
- Die vier gerenderten Client-Payloads (`extension.cjs`, `view.js`, `view.css`, ELK-Worker) sind zwischen dem letzten Entwicklungspaket und dem finalen Paket byte-identisch. Alle Java-Klassenbytes sind identisch; nur die Mitgliederreihenfolge von `catalog-manifest.json` unterscheidet sich, bei identischen geparsten Katalogdaten. Der laufende Serverprozess verwendet den finalen JAR aus dem privaten finalen Extension-Verzeichnis.

## 3. Betroffene Tests und Dokumentation

| Prüfung | Umfang | Ergebnis |
|---|---|---|
| Core betroffen | 31 Tests aus `GraphFormProjectorTest`, `SemanticEditPlannerTest`, `DependencySourceEditsTest`, `DefaultsSourceEditsTest`, `ReferenceSourceEditsTest`, `EditVerifierTest`, `SnapshotNavigationTest` | 31/31 bestanden, 0 Fehler, 0 Skips |
| LSP betroffen | 12 Host-/Diagnose-Tests plus 15 echte `ServerProcessIT`-Fälle | 27/27 bestanden, 0 Fehler, 0 Skips |
| Node Modeler | 45 Tests aus Edit-Plan, Edit-Guard, Controller, Intent, Connection, View-History und State | 45/45 bestanden, 0 Fehler, 0 Skips |
| `tooling-docs` | `node tools/gretl-tooling/check-docs.mjs` nach `npm ci --ignore-scripts` | Exit 0, 81 exakte Kriterien, Links und Pflichteingaben gültig |

Die Node-Tests liefen im Verzeichnis `vscode/gretl-vscode`; der Katalog wurde aus dem gelieferten Artefakt in die ignorierte `server/operations.json` kopiert, nicht handgeschrieben. Es wurden keine vollständigen Core-/LSP-Suiten wiederholt.

## 4. Kriterien P7-01 bis P7-09

### P7-01 – Vollständiger Job aus leerer Datei

**Erfüllt.** Der leere Modellzustand ist ehrlich als EMPTY dargestellt; die aktuelle Null-Byte-Neuanlage und Speicherung ist separat belegt. Parameter, HttpDownload und Extract wurden über die Palette und Core-Formulare angelegt, danach Ergebnisbindung, `after`-Kante und Defaultziel. Der gespeicherte, geschlossene und wieder geöffnete Job entspricht bytegenau dem Runtime-Fixture; der finale Kaltstart zeigt ihn als FRESH mit den erwarteten Kanten und Labels. Die vollständige Erstellung erfolgte in den Entwicklungsoriginalen, die Null-Byte- und finalen Kaltstartfälle sind getrennte aktuelle Belege.

### P7-02 – Palette, Wertformen und explizite Modi

**Erfüllt.** Die Palette verwendet die aktuellen Katalogoperationen; der aktuelle Kandidat zeigt die Kategorien und die gefilterte I/O-Auswahl. Die Formulare belegen exakte Integer jenseits der JavaScript-Präzision (`9007199254740993`), Boolean-, Enum- und Map-Modus, verschachtelte Listen/Maps sowie Parameter-, Datei-/WorkPath- und Secretalias-Modus. Ein abgebrochener Secretalias-Entwurf lässt die Quelle unverändert. Der finale Client ist byte-identisch mit dem geprüften Entwicklungsclient.

### P7-03 – Dependencygesten, `after` und Zyklen

**Erfüllt.** Pointer-Gesten ändern den Consumerkopf über `DependencySourceEdits`; eine `after`-only-Verbindung erzeugt keine erfundene Datenabhängigkeit. Zyklische und veraltete Entwürfe werden abgelehnt, die Quelle bleibt unverändert. Die Core-Tests decken Kopfänderung, `after`-Erhalt und Zielabhängigkeit ab.

### P7-04 – Ergebnisbindung und transitive Datenkanten

**Erfüllt.** Die Output→Input-Geste setzt die Ergebnisreferenz und nur die nötige Datenabhängigkeit; das Label `file → archive` ist sichtbar. Disconnect blockiert die letzte benötigte Datenkante, erlaubt aber das Entfernen einer direkten Kante, solange die transitive Kette den Input weiter liefert. Die neue GUI-Gegenprobe bestätigt denselben Vertrag auf dem finalen Paket: Ergebnisbindung über eine bestehende Producer→Bridge→Consumer-Kette, ohne redundante direkte Producer-Abhängigkeit und ohne Verlust der `after`-Kante.

### P7-05 – Rename, Delete und Defaults

**Erfüllt.** Der referenzierte Rename aktualisiert alle vier gebundenen Literale und erhält Kommentare; ein referenzierter Delete wird mit sichtbarer Core-Begründung blockiert, ein unreferenzierter Task kann ohne Cascade gelöscht werden. Defaults bleiben erhalten. Der aktuelle Kandidat zeigt die Core-Begründung erneut; die Core-Pläne und vollständige Referenzprüfung sind durch `ReferenceSourceEdits`, `DefaultsSourceEdits` und die betroffenen Tests belegt.

### P7-06 – Opaque Inhalte und gesperrte Aktionen

**Erfüllt.** Die CRLF-Probe ändert genau ein Boolean und erhält CRLF, Kommentare, Emoji, GStrings und Actioncode bytegenau; ein Ausführungsmarker fehlt. Opaque Actions und dynamische Registrierung werden nicht als erfundene Graphknoten interpretiert, sondern mit Quellfallback und Erklärung dargestellt. Gesperrte Aktionen bieten Textnavigation und einen Core-basierten Grund.

### P7-07 – Native Edits, getrennte View-Historie und Konflikte

**Erfüllt.** Fachliche Änderungen laufen als zusammengehörige native `WorkspaceEdit`s; eine Undo-Operation stellt eine atomare Bindung vollständig wieder her. Text-Undo/Redo verändert Layout und Viewport nicht; die separate, begrenzte View-Historie stellt alle Positionen und den Viewport wieder her, ohne die Quelle zu ändern. Stale-, Dirty- und Multifile-Fälle werden neu geplant oder abgelehnt; ein konkurrierender Textwechsel invalidiert den offenen Entwurf und verhindert stilles Überschreiben. Die 45 Node-Tests und die nativen Originale decken diese Invarianten ab.

### P7-08 – Echter Runtime-SCRIPT-Lauf ohne Editier-Jobstart

**Erfüllt.** Der grafisch erzeugte, bytegenau eingecheckte Groovytext läuft in einem separaten lokalen HTTP-/ZIP-`SCRIPT`-Fixture. Der Test ist in beiden Rootgates vorhanden und bestanden; die Runtime-Identität bestätigt denselben Input-Hash, einen HTTP-Request und den erwarteten Output. Normales Editieren startet keinen Job; die CRLF-Probe belegt das Fehlen eines Ausführungsmarkers.

### P7-09 – G17–G25, Roundtrip/Preservation/Negativfälle und Rootgates

**Erfüllt.** Alle neun GUI-Fälle sind über `gui/index.json` abgedeckt; die vollständigen Rootgates, die Preservation- und Negativfälle, der exakte Roundtrip und die neuen Gegenproben sind nachgewiesen. Die GUI-Aktionen und die zugehörigen Source-/Artefaktzustände sind in den Fall-JSONs, AX-Bäumen, Bildern und Hashes dokumentiert.

## 5. G17–G25

| Fall | Belegter Inhalt |
|---|---|
| G17 | Leerer Modeller, Palette, Parameter-/Download-/Extract-Anlage, Ergebnisbindung, Defaultziel, finaler Kaltstart |
| G18 | Exakte Integer, Boolean/Enum/Map, verschachtelte Listen/Maps, Secretalias-Abbruch, explizite Modi |
| G19 | Dependency-Pointer, `after`-only, Zyklusablehnung mit unveränderter Quelle, Stale-Draft |
| G20 | Result-Port-Pointer, atomare Ergebnisbindung mit Datenkante und Label, gespeicherte Defaults |
| G21 | Vier-Literal-Rename, referenzierter Delete mit Core-Grund, unreferenzierter Delete |
| G22 | Letzte Datenkante blockiert, direkte Kante bei verbleibender Kette entfernbar, transitive Quelle |
| G23 | CRLF-/Byte-Preservation, Kommentar-/GString-/Actioncode-Erhalt, Quellfallback für opaque Action |
| G24 | Atomares Binding-Undo, Text-Undo/Redo getrennt von View-Undo/Redo, alle Positionen/Viewport, Stale-Draft |
| G25 | Speichern/Schließen/Wiederöffnen, bytegenauer Runtime-Input, finaler Kaltstart |

Die vollständigen semantischen Originale wurden auf Entwicklungsständen der PhaseBase erzeugt und sind ausdrücklich als `DEVELOPMENT_NOT_FINAL_ACCEPTANCE` mit `sourceDirty: true` und ihren echten VSIX-/Server-Hashes gekennzeichnet. Sie wurden nicht umetikettiert. Die Brücke zum finalen Kandidaten ist separat belegt: byte-identische Client-Payloads, identische Java-Klassenbytes (nur Katalogmanifest-Reihenfolge differiert), finaler installierter Payload, finaler Kaltstart und die neue native GUI-Gegenprobe auf dem finalen Paket. Diese Trennung ist eine dokumentierte Restbeobachtung, kein Widerspruch und kein Pflichtfinding.

## 6. Neue Gegenproben

1. **LSP-stdio, finale Server-JAR:** Eine neue Fixture mit einer bestehenden Producer→Bridge→Consumer-Kette und einer `after`-Kante wurde geöffnet. Die Ergebnisbindung `producer.file → consumer.archive` fügte genau eine `RESULT_BINDING`-Kante hinzu, ließ `after: 'producer'` und `dependsOn: [ 'bridge']` unverändert und erzeugte keine direkte Producer-Abhängigkeit. Quell-Hash vorher `97e19750…3876a`, nachher `18f103d1…dadb2`; ein Result-Edge, null direkte Dependencies, ein After-Edge, keine Duplikate.
2. **Native GUI, finales installiertes VSIX:** Dieselbe Bindung wurde über die echte Modeler-Oberfläche auf einer neuen Fixture ausgeführt, bestätigt und gespeichert. Die gespeicherte Quelle hat `archive = result('producer').file`, `after: 'producer'`, `dependsOn: [ 'bridge']` und keine direkte Producer-Abhängigkeit; der Graph ist FRESH und zeigt beide `dependsOn`-Kanten, `after · order only` und `file → archive`. Ich habe die zurückgegebenen Bilder 006/007 und die AX-Bäume persönlich geprüft; das Dokument ist nach dem Speichern sauber.

## 7. Restbeobachtungen (nicht blockierend)

- Die G17–G25-Originale stammen von ehrlichen, schmutzigen Entwicklungsständen der PhaseBase. Der finale Kandidat ist über identische Client-/Java-Klassenbytes, den finalen installierten Payload, den finalen Kaltstart und die neue GUI-Gegenprobe abgesichert. Die Originale tragen weiterhin ihre Entwicklungsprovenienz.
- Die T31-Provenienz ist ebenfalls Entwicklungsprovenienz; der exakte Quelltext ist eingecheckt und in beiden Rootgates ausgeführt. Die lokale `sourceUrl`-Übersteuerung ist dokumentiert und ändert die Quellbytes nicht.
- Der Fall `nested-values-and-safe-delete` zeichnet `exactSavedOriginalAfterDelete: false` auf. Die beiden Captures zeigen die beabsichtigte Entfernung des unreferenzierten `probe`-Tasks und eine abschließende Leerzeilendifferenz; alle übrigen Deklarationen, Kommentare und `defaults 'extract'` bleiben erhalten. Ich werte das als erwartete Nicht-Identität einer Löschoperation, nicht als Preservation-Verlust.
- `docs/implementation/gretl-tooling/implementation/P7.md` beschreibt noch den Entwicklungsstand („IN PROGRESS“) und den Vorgängerkandidaten mit 917 Standardtests. Die finalen 918/974-Rootgates und der Kandidatenbezug sind durch die Rohbelege belegt; die Berichtsformulierung ist eine Dokumentationsbeobachtung, kein funktionaler Mangel.

## 8. Formales Ergebnis

- **Offene Pflichtfindings:** keine.
- **Fehlende Pflichtprüfungen:** keine.
- **Kriterien P7-01 bis P7-09:** alle erfüllt.
- **GUI-Fälle G17 bis G25:** alle abgedeckt.
- **Verdikt:** **PASS** für `53f7f3daaf92d002fd75978c25471321fd99a310`.

Es wurden keine Produktdateien geändert, keine Rootgates wiederholt, keine Credentials gelesen und keine produktiven Jobs oder Veröffentlichungen ausgeführt.
