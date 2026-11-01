# Unabhängiger Review TST1

Phase / Versuch: **TST1 / 1**

Phasenbasis-SHA: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`

Auftragsbasis-SHA: `520b59be12f7966be46d0eaaca046c6ee3dc04f7`

Geprüfter Kandidaten-SHA: `52ca5bee2e52f7c07106d305e071add1c3c3724a`

Tatsächlicher Review-HEAD und Zustand:
`52ca5bee2e52f7c07106d305e071add1c3c3724a`, detached Review-Worktree,
`git status --porcelain` leer (vor und nach allen Läufen). Der Kandidat wurde
nicht verändert.

OpenCode-Version / Provider / API-Modell / API-Host:
OpenCode CLI `opencode v2.2.5` (`/Applications/OpenCode.app/Contents/Resources/opencode-cli`),
Provider `deepseek`, Modell `deepseek/deepseek-flash`, Host `api.deepseek.com`.

Beleg der aktuellen 1017-Aliaszuordnung: Der OpenCode-Modellkatalog löst
`deepseek/deepseek-flash` auf den Namen `DeepSeek V4.1 Flash 1017`, die
kanonische ID `deepseek/deepseek-v4.1-flash-1017` und den Release-Zeitpunkt
2026-10-17 (1792195200000 ms) auf. Siehe
`evidence/preflight/model-list.json`; die CLI-Version und `run --help` stehen in
`evidence/preflight/opencode-version.txt` bzw. `opencode-run-help.txt`.

Host-/Containerplattform und gegebenenfalls Emulation:
macOS 27.0.1 auf Apple Silicon (aarch64), Temurin JDK 25.0.2, Maven-Wrapper
3.10.0, Node 22.23.1, Python 3.9.6, Quarto 1.9.37; Docker-Server 29.8.1,
`linux/aarch64` (Docker Desktop). Der Vollauf verwendet echte
PostgreSQL/PostGIS-, FTP-, S3- und HTTP-Dienste im lokalen Docker. Die
Testcontainers-PostgreSQL-Images laufen auf diesem Host; eine native
Linux-x86-64-Aussage wird daraus nicht abgeleitet. Details:
`evidence/environment.md`.

Tatsächlich verwendete Runtime-/Input-/Image-/JAR-Identitäten:

- Kandidatendistribution: `package-catalog.json`
  `sourceRevision=52ca5bee2e52f7c07106d305e071add1c3c3724a`,
  `sourceDirty=false`. Standard-/JAR-local-Distribution:
  `SHA256SUMS` SHA-256 `e46c3983574d2656e20058fada954ef102c2eaec1183d5c4719ef38b3abcadf9`;
  Vollprofil-Distribution (GeoTools-Profilerweiterung):
  `565fecabd6b5f4b48897e00ca21ba6c649bb594f4547d9aa764a338546501f85`.
  Der Launcher `bin/gretl` hat in beiden Distributionsvarianten
  `7017ca54c6bc0fb0dc5a89d40b483a414a967c54e7c986095312695860ff67f8`.
- SCRIPT-Jobinput: bytebasierter `input-manifest.json` je Ausführung
  (Baumhash in `execution.json`), inklusive verschachtelter Ressourcen.
- JAR: tatsächlich gestartetes `job.jar` mit SHA-256 in `jobJar.sha256` und
  Nachweis `package-inventory.json`.
- IMAGE: in TST1 nicht als Pflichtmodus ausgeführt; die vorhandene
  Imagevorbereitung blieb durch die 11 Komponententests des
  `ImageExecutionAdapterTest` intakt.

## Kriterienprüfung

| ID | erfüllt / verletzt / nicht prüfbar | eigener Beleg | Finding |
|---|---|---|---|
| TST1-01 | erfüllt | `git diff --stat 520b59b..52ca5be` (28 Dateien, 2 Commits, keine Produktions-Javaänderung); `docs/implementation/script-first-testing/implementation/TST1.md`; `evidence/preflight/model-list.json`; `evidence/preflight/opencode-version.txt` | keines |
| TST1-02 | erfüllt | `ArtifactJobRunner.Mode` (Default `SCRIPT`, `parse` wirft bei leer/unbekannt), `ArtifactJobRunnerTest#modeParserRejectsExplicitInvalidValues` (6 Tests, 0 Fehler); Standardlauf 93 SCRIPT/29 JAR, JAR-local-Lauf 93 JAR/29 SCRIPT beweist Default und Propertywirkung; `Prepared.mode()` stammt ausschliesslich aus `JobLaunch.mode()`, `start` akzeptiert keinen Boolean | keines |
| TST1-03 | erfüllt | `CandidateDistribution` (Reactor-`target/dist`, `SHA256SUMS`-/Katalog-/Symlink-Prüfung, Kandidatenrevision); `ScriptExecutionAdapter.arguments`; `ArtifactJobRunner.start` startet `ProcessBuilder` mit der tatsächlichen `bin/gretl`; Beispiel-`command.json` in `evidence/standard/artifact/job-evidence/external-edit-0-*/command.json`; eigener externer Lauf | keines |
| TST1-04 | erfüllt | `ScriptProcessLocalJobIT#runsWithoutDescriptorThenWithBrokenDescriptorAndEditedSource`; `ArtifactJobRunnerTest#scriptArgumentsHaveNoShellSplittingOrPackagingBoundary`; eigener Gegenprobe-Lauf mit kaputtem, unbenutztem Descriptor ohne `job.jar`/Packager; `bin/gretl package` lehnt denselben Descriptor mit `PACKAGE_DESCRIPTOR_INVALID` ab | keines |
| TST1-05 | erfüllt | `ScriptProcessLocalJobIT#runsWithout...` (Unicode/Leerzeichen im Scriptnamen und Fixturepfad, Quelländerung), `#earlySyntaxMissingFileAndInvalidSettingsReachTheProduct` (Syntax `bad.groovy: 1:`, fehlende Datei, ungültige Settings, Exit 2, kein Report); eigener externer Zweitlauf mit geändertem Script und verschachtelter Ressource | keines |
| TST1-06 | erfüllt | `JobInputSnapshot` (bytebasiert, sortiert, UTF-8/JSON-Kanonisierung, keine Groovy-/Settings-/Descriptoranalyse, nur Root-`job-package.json` ausgenommen); `JobExecutionEvidence.started` schreibt Input-, Runtime- und STARTED-Metadaten vor dem Start; `ArtifactJobRunner.start` prüft Distribution und Input vor dem Prozess; `ArtifactJobRunnerTest#inputInventoryDoesNotParseAndTracksEveryResource` | keines |
| TST1-07 | erfüllt | Keine `scriptInput`-/`start(Prepared, boolean)`-Reste (`grep`); Aufrufer in Cancellation/RuntimeSafety/PlaintextSecret/ProjectArtifact/SelectedPackage migriert; JAR-Verträge in `JarExecutionAdapter`, `PackageArtifactAssertions`, `SelectedPackageSupport` und `ProjectArtifactLocalJobIT` ausdrücklich JAR; Standardlauf 29 JAR-Ausführungen, JAR-local 93, alle grün | keines |
| TST1-08 | erfüllt | Standardlauf mit absichtlich unerreichbarem Docker-Socket: Exit 0, 686 Tests, 0 Failures/Errors/Skips; anschliessender SCRIPT-Audit Exit 0; JAR-local ebenso 686/0/0/0 plus JAR-Audit Exit 0; keine `assumeTrue`/`@Disabled`-Skips; keine abgeschwächte Fachassertion im Phasendiff gefunden | keines |
| TST1-09 | erfüllt | `ArtifactJobRunnerTest#forceTerminationNeverUsesUnboundedWait`; `ScriptProcessLocalJobIT#launchFailureIsRecordedSeparatelyAndSourceChangesAreRejected` (LAUNCH_FAILED, kein erfundener Report, Inputänderung wird abgewiesen); `#controlsPerformNoTaskWorkAndLargeStreamsDoNotDeadlock`; `JobExecutionEvidence.completed` mit getrennten Zuständen `EXITED`/`CANCELLED`/`TIMED_OUT`/`FORCED_TERMINATION`; viele reale Prozesse in allen drei Läufen | keines |
| TST1-10 | erfüllt | Alle Pflichtgates des Reviewers (siehe unten) am festen Kandidaten; eigene neue Gegenprobe `counterprobe/README.md`; Kandidat unverändert; Reviewprozess unabhängig | keines |

## Code- und Zielprüfung

Relevante Klassen/Methoden und Datenfluss:

- `ArtifactJobRunner.Mode` / `configured()` / `parse()`: Default `SCRIPT`, ein
  explizit leerer oder unbekannter Wert wirft, kein stiller JAR-Fallback.
- `Request`/`JobInput`/`Invocation`: typisierte externe Quelle bzw.
  Projektverzeichnis, RUN/CHECK/PLAN/Hilfe; Pfade müssen relativ zum gestagten
  Root bleiben. `Request` kopiert Parameter/Environment/Config defensiv; die
  Convenience-Konstruktion bildet eindeutig auf `script("job.groovy")` + RUN ab.
- `prepareInWorkspace`: kopiert die Fixture in einen eigenen Sourcebereich,
  erstellt getrennte CWD-/Report-/Outputbereiche, erzeugt den Inputsnapshot und
  wählt exakt einen Adapter. SCRIPT/IMAGE berühren keine Paketlogik.
- `ScriptExecutionAdapter`: baut einen `List<String>`-Argumentvektor aus der
  tatsächlichen `bin/gretl`, der ausgewählten Datei bzw. dem Projekt und den
  vorhandenen CLI-Optionen. Kein `sh -c`, keine Stringverkettung; fehlende
  Dateien und Syntaxfehler erreichen den echten Produktlauncher.
- `JarExecutionAdapter`: der einzige Adapter, der `gretl package` aufruft;
  Cache-Schlüssel aus Distributionsmanifest, Inputtree und Deskriptorbytes;
  eigenständige Kopien für Mutationsproben; `PackageArtifactAssertions.verify`.
- `CandidateDistribution`: prüft Reactor-Distribution, `SHA256SUMS`, Katalog,
  Launcher-Ausführbarkeit und optional die Kandidatenrevision/`sourceDirty`.
- `JobInputSnapshot`: reiner Bytebestand, keine inhaltliche Interpretation;
  `job-package.json` nur als bewusst dokumentierte Ausnahme.
- `JobExecutionEvidence`: getrennte Input-, Runtime-, JAR-/Image- und
  Prozessmetadaten, atomisches Schreiben, Zustand nicht `STARTED` bei
  abgeschlossener Ausführung.
- `ImageExecutionAdapter`: vorhandener externer SCRIPT-Einstieg
  (`/job/job.groovy` bzw. `/job`) blieb erhalten; neue Launch-Immuntabilität und
  `GRETL_JAVA_OPTS` ergänzt. Keine echte IMAGE-Qualifikation behauptet.

Wie sind SCRIPT-Vorrang und IMAGE-als-SCRIPT tatsächlich umgesetzt?
SCRIPT ist der konfigurierte Default und startet die gebaute Distribution als
eigenen Hostprozess ohne Paketdescriptor. IMAGE bleibt der vorhandene Adapter,
der denselben externen Quelleneinstieg im Container abbildet, ohne Job-JAR. JAR
bleibt der ausdrücklich gewählte Paketweg. Die Modusbehauptung stammt aus dem
vorbereiteten `JobLaunch`, nicht aus einer späteren Property.

Welche alten fachlichen und Paketverträge sind erhalten?
Die bestehenden Fach-, Projekt-, Cancellation-, Secret-, Runtime-Safety-,
Modul-/Service-, Distributions- und Paketprüfungen wurden nicht entfernt. Der
Standardlauf enthält weiterhin 29 JAR-Ausführungen; der JAR-local-Lauf 93.
`SelectedPackageLocalJobIT`, `ProjectArtifactLocalJobIT` (Paket-/Bytecode-/
Manipulationsfälle), `LibraryScopeLocalJobIT` (JAR-Zweig),
`ImageExecutionAdapterTest` und `DistributionIT` sind grün.

## Eigene Gegenproben

Neue Hypothese und Input:
Ein frisch gebauter Kandidatenlauncher startet eine externe Groovy-Datei
(`aufgabe ü.groovy`, mit Leerzeichen und Unicode) mit einer verschachtelten
Ressource auch dann, wenn daneben ein absichtlich ungültiger, unbenutzter
`job-package.json` liegt. Nach Änderung des Scriptinhalts und der Ressource muss
ein zweiter Lauf ein neues fachliches Ergebnis liefern, ohne die Distribution
neu zu bauen und ohne ein Job-JAR zu erzeugen.

Exaktes Kommando / tatsächlicher Modus:
`<candidate-dist>/bin/gretl run "<job>/aufgabe ü.groovy" --config ... --report ... --log-format json --param resultFile=...`
aus fremden CWDs (`cwd-first`, `cwd-second`), mit einem privaten PATH, der nur
einen `dirname`-Wrapper enthält und in dem der Job selbst `mvn`/`gradle`
ausschliesst. SCRIPT-Modus über den echten Launcher, kein `GretlCli.run()` in
der Test-JVM.

Unabhängig erwartetes Ergebnis:
Erster Lauf `nested-resource-v1::<MARK1>`, zweiter Lauf
`nested-resource-v2::<MARK2>`, jeweils Exit 0 und fachlicher Report
`SUCCEEDED`; unveränderte Distributionsbytes; kein `job.jar`, kein
Packager-Log.

Tatsächlicher Exit, Output, Report und fachlicher Zustand:
Erster Lauf Exit 0, Output
`nested-resource-v1::7c023a88-c36a-469f-8f38-885f2635ec77`; zweiter Lauf
Exit 0, Output
`nested-resource-v2::a0ca1295-0bd4-4200-8821-40bc10b34eb0`; beide Reports
`status=SUCCEEDED`, `exitCode=0`. `SHA256SUMS` vor/nach identisch
(`e46c3983574d2656e20058fada954ef102c2eaec1183d5c4719ef38b3abcadf9`),
`package-catalog.json` `sourceRevision` = Kandidat, `sourceDirty=false`.
`bin/gretl package` mit demselben Descriptor endet erwartungsgemäss mit Exit 2
und `PACKAGE_DESCRIPTOR_INVALID`. Kein `job.jar`/`package.stdout.log`.

Resultat / gegebenenfalls ehrlicher Vorher-RED-Nachher-GREEN-Nachweis:
Die Hypothese wurde bestätigt. Ein RED-Nachweis am Produktcode war nicht
erforderlich, weil kein Fehlerfix behauptet wird; der Gegenfall (Paketgrenze)
wird durch den absichtlich ungültigen Descriptor und den JAR-Paketfehler
belegt. Belege: `counterprobe/README.md`, `counterprobe/first.*`,
`counterprobe/second.*`, `counterprobe/package.*`, `counterprobe/reports/`.

## Ausgeführte Pflichtprüfungen

Alle Befehle wurden im detached Review-Worktree auf dem exakten Kandidaten
ausgeführt. JUnit-Zahlen stammen aus den tatsächlichen Surefire-/Failsafe-XML-
Berichten, nicht aus `BUILD SUCCESS` allein.

| Check | Kommando | Exit | Tests / Failures / Errors / Skips | Beleg |
|---|---|---|---|---|
| standard | `DOCKER_HOST=unix:///tmp/gretl-review-intentionally-unavailable-docker.sock ./mvnw -B clean verify -Dgretl.test.candidateSha=52ca5be... -Dgretl.test.runId=review-TST1-1-...` | 0 | 686 / 0 / 0 / 0 | `evidence/standard/maven.log`, `evidence/standard/junit/` |
| standard-audit | `python3 scripts/audit-job-evidence.py --mode SCRIPT --source 52ca5be...` | 0 | 93 SCRIPT / 29 JAR Ausführungen; 22 positive lokale Operations | `evidence/standard/audit-script.log`, `evidence/standard/qualification/` |
| jar-local | `DOCKER_HOST=...unavailable... ./mvnw -B clean verify -Dgretl.test.executionMode=JAR -Dgretl.test.candidateSha=... -Dgretl.test.runId=...` | 0 | 686 / 0 / 0 / 0 | `evidence/jar-local/maven.log`, `evidence/jar-local/junit/` |
| jar-local-audit | `python3 scripts/audit-job-evidence.py --mode JAR --source 52ca5be...` | 0 | 93 JAR / 29 SCRIPT Ausführungen; 23 positive lokale JAR-Operations | `evidence/jar-local/audit-jar.log`, `evidence/jar-local/qualification/` |
| full | `./mvnw -B -Pgeotools-probe,backing-services clean verify -Dgretl.test.candidateSha=... -Dgretl.test.runId=...` | 0 | 742 / 0 / 0 / 0 | `evidence/full/maven.log`, `evidence/full/junit/` |
| full-audit | `python3 scripts/audit-job-evidence.py --mode SCRIPT --full --source 52ca5be...` | 0 | 170 SCRIPT / 30 JAR; alle 44 Baseline-Operationen positiv in SCRIPT (plus `fixture.scope`) | `evidence/full/audit-script-full.log`, `evidence/full/qualification/` |
| review-validator | `node --test tests/script-first-testing/review-phase.test.mjs` | 0 | 21 / 0 / 0 / 0 | `evidence/orchestration/review-validator.log` |
| docs-generate | `./gretl-dist/target/dist/bin/gretl operations --format adoc --lang de --output docs/reference/generated` | 0 | 44 Dateien, danach `git status` leer | `evidence/docs/docs-generate.log`, `evidence/docs/generated-preview` |
| docs-tests | `python3 -m unittest discover -s tests/cleanup_review -v` | 0 | 73 Tests, OK | `evidence/docs/docs-tests.log` |
| docs-links | `python3 scripts/verify_cleanup_docs.py` | 0 | 197 Ziele, 0 defekt | `evidence/docs/docs-links.log` |
| docs-render | `quarto render website/` | 0 | 4 Seiten, `_site/index.html` | `evidence/docs/docs-render.log` |

Getrennte Szenario-/Operationsabdeckung je fälligem Modus:

- **SCRIPT/local**: 93 Ausführungen, 22 positive lokale Standardoperationen;
  alle lokalen `*JobIT`-Fälle grün.
- **JAR/local**: 93 Ausführungen, 23 positive lokale Operationen; alle
  ausdrücklichen Paket-/Projektverträge grün.
- **SCRIPT/full**: 170 Ausführungen, alle 44 Operationen aus
  `docs/history/contracts/operation-baseline.json` positiv in SCRIPT; der
  zusätzliche Extension-Nachweis `fixture.scope` ist separat gezählt.
- **IMAGE**: In TST1 nicht als vollständiges Gate gefordert und nicht als
  bestanden behauptet. Die bestehende Imagevorbereitung ist durch
  `ImageExecutionAdapterTest` (11 Tests) als Komponentenvertrag erhalten.

Rohreport-/Artefaktpfade und SHA-Zuordnung:
JUnit-XML, Qualifikations-Summaries, Distributionsbytes, JAR-Metadaten und
Job-Evidence liegen unter `evidence/standard`, `evidence/jar-local`,
`evidence/full` und `counterprobe`. Rohkonfigurationen und Secretdateien wurden
bewusst nicht als allgemeine Artefakte archiviert. Die Standard- und
Full-Audits prüften `sourceRevision` und `sourceDirty=false` gegen den
Kandidaten; die tatsächlich gestarteten Launcher-/Distributionsbytes wurden
über `SHA256SUMS` verifiziert.

Eigenständig ausgeführte Läufe versus ausgewertete CI-Läufe:
Alle oben genannten Läufe wurden vom Reviewer selbst ausgeführt. Es wurden
keine CI-Läufe als Ersatz herangezogen. Die Standard-/JAR-local-Läufe liefen
absichtlich ohne erreichbaren Docker-Daemon; der Vollauf mit echten Diensten.

Nicht ausgeführte fällige Prüfung und konkrete Ursache:
Keine TST1-Pflichtprüfung blieb unausgeführt. Eine echte IMAGE-/Docker-
Qualifikation ist erst TST3 Pflicht und wurde hier nicht als TST1-Nachweis
behauptet.

## Findings

Keine offenen Muss-Findings.

Hinweise ohne Muss-Charakter (kein TST1-Kriterium verletzt):

- Der Standard-Audit ohne `--full` verlangt erwartungsgemäss nur die lokalen
  Operationsnachweise; die vollständige 44er-Abdeckung wird im Vollprofil
  geprüft. Das entspricht dem TST1-Zuschnitt; TST2 wird die durchgängige
  Szenariomatrix prüfen.
- `ImageExecutionAdapter.mount(...)` legt für nicht vorhandene generische
  Mountziele weiterhin Verzeichnisse an; Secretdateien werden davor separat als
  reguläre Dateien geprüft. Eine etwaige Härtung ist TST3 (IMAGE_CONTRACT), kein
  TST1-Finding.

## Urteil

**PASS**

Begründung: Alle zehn TST1-Muss-Kriterien sind am exakten Kandidaten durch
eigene Läufe, tatsächliche JUnit-/Audit-/Distributionsbelege und eine neue
unabhängige Gegenprobe belegt. Standard und JAR-local sind ohne Docker grün,
der Vollauf mit echten Diensten ist grün, die Audits verweigern keine Pflicht-
nachweise, und die SCRIPT-/JAR-Trennung ist prozessual und in den Evidenzen
nachvollziehbar.

Offene Muss-Findings: keine.

Fehlende Pflichtnachweise: keine.

Bei TST4: nicht anwendbar (TST1-Prüfung).
