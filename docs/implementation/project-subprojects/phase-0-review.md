# Phase 0 – Independent Review

- Phase base SHA: `db1ea316ccc72f946fe244942bae2dc9fb485672`.
- Reviewed candidate SHA: `1ff17246caff404f75303dcc31d2f9814e4bc2aa`.
- Reviewer/Model/Session: unabhängiger Codex-Review-Agent B, GPT-6-basierter Agent, Sitzung `/root/independent_phase0`. B hat weder Implementierung noch A-Berichte erstellt; eigener Reviewkontext und eigene Ausführung.
- Date/time: 2026-10-28T18:47:52.722462+00:00 (UTC).
- Separate worktree: `/Users/stefan/sources/gretl-runtime-phase-0-review-1`. `git rev-parse HEAD` bestätigt exakt die Kandidaten-SHA. `git symbolic-ref -q HEAD` endet erwartungsgemäss mit 1: detached HEAD.
- Full reviewed diff: `db1ea316ccc72f946fe244942bae2dc9fb485672..1ff17246caff404f75303dcc31d2f9814e4bc2aa`.
- Evidenzwurzel: `/Users/stefan/sources/gretl-runtime-phase-0-evidence-20261028`; im Folgenden `E/`. Alle Produkt-/Prüfbefehle wurden ausdrücklich mit dem Arbeitsverzeichnis `/Users/stefan/sources/gretl-runtime-phase-0-review-1` ausgeführt. B hat keine getrackte Datei geändert, keinen Codefix und keinen Implementierungscommit erstellt. Der Worktree bleibt erhalten.

## Umfang und geprüfte Quellen

Geprüft ist ausschliesslich Phase 0, einschliesslich der fünf Arbeiten und Gate 0 aus Abschnitt 8. Die vollständige Subprojekt-Spezifikation 1.1, insbesondere Abschnitte 7–12, Root- und Spec-AGENTS sowie die normativen Kapitel 03, 04, 06, 09, 12, 14, 15 und 16 wurden selbst gelesen. Hinzu kamen Spec-README, Zielbild 01, Autorenweg 13, Implementierungsplan 10, Plattform-/Buildverträge aus 07, `module-boundaries.md`, `testing.md` und `job-artifact-tests.md`.

Beide SHAs existieren; `git merge-base --is-ancestor <Basis> <Kandidat>` endet mit 0. Der gesamte Diff umfasst genau vier Dateien (1816 hinzugefügte, 389 entfernte Zeilen):

1. neue, vom Benutzer bereitgestellte `gretl-runtime-subprojekte-spezifikation.md`;
2. gelöschte, vorher falsch platzierte `gretl-runtime-umsetzungsplan.md`;
3. neuer `phase-0-baseline.md`;
4. neuer `phase-0-evidence.json`.

Alle liegen unter `docs/implementation/project-subprojects/`. Keine Produkt-, Test-, Fixture-, POM-, Wrapper-, Workflow- oder Beispieldatei ändert sich. Der entfernte Plan ist bytegleich unter `docs/implementation/improvement-plan/gretl-runtime-umsetzungsplan.md` erhalten. Zwischen dem analysierten Produktcommit `69a55e3ddde25b97325e4463a70d40f75a5e06d4` und der Phasenbasis liegt ebenfalls ausschliesslich diese damals hinzugefügte Plandatei.

Beleg: `E/independent-full-diff.patch`, SHA-256 `b00c0a3ab6806f277d231116e7feab57661a37ff525b2e9bb9e03de6bc9f7ac8`; `E/independent-source-audit.json`. Die 801 inventarisierten Produkt-/Test-/Builddateien wurden selbst nachgehasht; alle stimmen. Alle 17 getrackten POMs einschliesslich direkter Produktionsabhängigkeiten, Parent und Modullisten wurden gegen das Inventar geparst und abgeglichen: 15 reguläre Module, zusätzlich genau ein GeoTools-Testmodul im ausdrücklichen Profil. Kein Modulzyklus und keine neue Buildabstraktion wird eingeführt.

`git diff --check <Basis>..<Kandidat>` endet mit 2 ausschliesslich wegen der sechs Markdown-Hardbreaks in den Metadatenzeilen 3–8 der bereitgestellten Spezifikation. Dies ist keine Produkt-/Testabweichung.

## Eigene Ausführung und Nachweise

Verwendete Toolchain: Eclipse Temurin OpenJDK `25.0.2+10-LTS`, `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.2-tem`, dessen `bin` vor dem übrigen PATH; Maven Wrapper 3.3.4 mit Maven `3.10.0` (`c43a36b8d67be7e0805a411bc0898af1a51f5472`); Groovy-POMversion `6.0.0`, Zielbytecode 25, UTF-8. Maven meldet macOS 27.0.1/aarch64, Locale en_US, Europe/Zurich. Versionsausgaben und detached/Statusprüfung liegen in `E/independent-standard/environment.json`. Die macOS-Discovery `/usr/libexec/java_home -V` fand keinen registrierten Runtimeeintrag; die ausdrücklich gewählte und tatsächlich benutzte SDKMAN-Toolchain ist separat verifiziert.

| Eigener Befehl | Exitcode / Ergebnis | Nachweis |
| --- | --- | --- |
| `./mvnw -B clean verify` | 0; 604 Tests, 0 Failures, 0 Errors, 0 Skips | `E/independent-standard/console.log`, `result.json`, `reports/`, `coverage/` |
| `python3 scripts/audit-job-evidence.py --source 1ff17246caff404f75303dcc31d2f9814e4bc2aa` | 0; Kandidatenrevision, sourceDirty=false; 82 JAR, 14 SCRIPT, 0 IMAGE | `E/independent-standard/audit.log`, `qualification/`; bestätigter Prozessstatus in `E/independent-audit-result.json` und `independent-audit-confirmation.log` |
| `python3 target/independent-review/archive_audit.py before-standard before-full after-standard after-full independent-standard` | 0; Manifest-, XML-, Report-, Metadaten-, JAR- und Distributionsprüfungen | `E/independent-archive-audit.json`, `.log` |
| `python3 target/independent-review/outcome_audit.py before-standard before-full after-standard after-full independent-standard` | 0; eindeutige Run-/Task-Terminalpaare und Reportstatus | `E/independent-outcome-audit.json`, `.log` |
| `python3 target/independent-review/run_probes.py` | 0 nach Korrektur einer unbegründeten Zusatzassertion; drei reale Prozessaufrufe mit erwarteten Codes 10 / 0 / 0 | `E/independent-probes.log`; Quellen, Argumentlisten und vollständige Ergebnisse unter `E/independent-standard/additional-probes/probes/` |

Der eigene Reactor lief von `2026-10-28T18:31:35.793230+00:00` bis `2026-10-28T18:42:14.204423+00:00`. Die Zählung stammt aus 109 JUnit-XML-Dateien ausschliesslich aktiver Reactormodule; keine alten GeoTools-Reports wurden aufgenommen. Standardtests pro Modul: API 17, Runner 185, Groovy 49, i18n 4, Logging 38, Packager 12, Datenbank 21, IO 54, CLI 55, Job-Runtime 24, IliValidator 3, INTERLIS 34, Distribution 13, Integrationstests 95. Das externe FileSize-Fixture wurde regulär gebaut.

Der Audit wurde nach dem Build ausgeführt und zur ausdrücklichen Sicherung seines eigenen Prozess-Exitcodes nochmals bestätigt; beide Ausgaben stimmen. Die eigene Sicherung enthält 3450 prüfsummengebundene Dateien einschliesslich JAR-Bytes, Reports und Zusatzproben. Ihr Manifest liegt in `E/independent-standard/SHA256SUMS.json`.

## Nachprüfung der A-Evidenz und Provenienz

Alle Dateien aller fünf Archive wurden gegen ihr jeweiliges Manifest selbst gehasht. Die 232 im getrackten Evidence-Index einzeln verlinkten JUnit-Dateien der beiden Vorprüfungen sind vollständig vorhanden und genau die aktiven Reportbestände; Konsolen- und Manifesthashes stimmen (`E/independent-index-audit.json`). Jeder gestartete archivierte JAR wurde gegen seinen Ausführungshash geprüft. Seine eingebetteten Job-/Dependency-Metadaten wurden mit den gesicherten Metadaten verglichen. Alle 93 Katalogartefakte und der vollständige SHA256SUMS-Bestand jedes Distributions-ZIP wurden aus den tatsächlichen ZIP-Bytes geprüft; TAR-/ZIP-Archivhashes stimmen ebenfalls.

| Archiv | Manifestdateien | JUnit-XML | Tests / Failures / Errors / Skips | JAR / SCRIPT / IMAGE | Positive Produktoperationen | Manifest-SHA256 |
| --- | ---: | ---: | --- | --- | --- | --- |
| before-standard | 3330 | 109 | 604 / 0 / 0 / 0 | 82 / 14 / 0 | 21 / 44 | `9088651d65cf89006c100659456fa19b9770370e9a696773f7795f3abcb6cd23` |
| before-full | 4171 | 123 | 660 / 0 / 0 / 0 | 160 / 14 / 0 | 44 / 44 | `9dc4931dcf73688a9b8ebc5d13e23836788e81df307b76305dbbf7c5ed7753bb` |
| after-standard | 3330 | 109 | 604 / 0 / 0 / 0 | 82 / 14 / 0 | 21 / 44 | `427baaa051fee102a5a51a538849851052d1e42060b91ac0b9d63fb684cb254b` |
| after-full | 4171 | 123 | 660 / 0 / 0 / 0 | 160 / 14 / 0 | 44 / 44 | `bb622b01892c75b03dcab37a7eeb0a7472ef7004ac88eec63bbedc623c4b251a` |
| independent-standard | 3450 | 109 | 604 / 0 / 0 / 0 | 82 / 14 / 0 | 21 / 44 | `9f6ff7398b88375a73ec31c333c515295e4151889bd0317a78a15c97c1ecdd69` |

Vorprüfungen sind eindeutig als Basisrevision und `sourceDirty=true` ausgewiesen. Beide Kandidatennachtests und Bs eigener Lauf nennen exakt `1ff17246caff404f75303dcc31d2f9814e4bc2aa` und `sourceDirty=false`. Keine Vorprüfung wird als sauberer Kandidatennachtest ausgegeben. Das Standardgate deckt positiv 21 der 44 Produktoperationen ab; die vollständige Abdeckung wird ausschliesslich aus den Full-Archiven abgeleitet. Dort sind alle 44 Inventar-IDs in erfolgreichen JAR-Invocations belegt, zusätzlich zwei Test-/Extensionoperationen.

A hat nach dem Commit auch `./mvnw -B -Pgeotools-probe,backing-services clean verify` und `python3 scripts/audit-job-evidence.py --full --source 1ff17246caff404f75303dcc31d2f9814e4bc2aa` mit jeweils Exit 0 ausgeführt. B hat diesen optionalen Full-Lauf nicht selbst neu gestartet, sondern Console, 123 aktive XML-Dateien, vier echte GeoTools-Proben, 147 Integrationstestfälle, JAR-/Distributionsbytes, Reportinhalte und Hashes selbst geprüft. `after-full` lief von 18:30:12Z bis 18:44:28Z. Die Dokument-/Toolchainbaseline bleibt damit auch am sauberen Kandidaten zusätzlich qualifiziert.

Der finale externe Handoff enthält beide Kandidatennachtests und `additionalFullRetestComplete=true`. Seine SHA-256 ist `878739964037f4d6f176a0f275996188ac2773bd7b2bdf20f2a4158d89eac4ec`; Sidecarhash, sämtliche verlinkten Dateien und Manifesthashes stimmen. `E/independent-handoff-audit.json` belegt auch die unveränderte Kandidaten-SHA und saubere getrackte Arbeitsbäume von A und B zum Prüfabschluss. Die eigene SHA wird nicht in ihre sie definierende Kandidatendatei zurückgeschrieben.

## Vollständige Phase-0-Matrix

| ID / Phase-0-Arbeit | Ergebnis | Eigener Nachweis |
| --- | --- | --- |
| P0-01 / Arbeit 1 | PASS | Anweisungen und vollständige neue Spezifikation selbst gelesen; Runtime-/DSL-/API-/Logging-/Paket-/Testgrenzen abgeglichen. |
| P0-02 / Arbeit 2 | PASS | Beide feste SHAs und Vorfahrbeziehung; Toolchain tatsächlich ausgeführt; 17 POMs, 15 Standardmodule, öffentliche DSL-Signaturen und Produkt-Ausgangsabweichung selbst geprüft. |
| P0-03 / Arbeit 3, Standard | PASS | Eigener sauberer `clean verify` auf Kandidat, 604/0/0/0; Kandidaten-Audit Exit 0 und archivierte Nachweise. |
| P0-04 / Arbeit 4 | PASS | Gesamter Diff ohne Code-/Test-/Buildänderungen; 801 Fingerprints; API, Runner, vier Taskmodule und Altverträge explizit inventarisiert und regressionsgeprüft. |
| P0-05 / Arbeit 5 | PASS | Baseline/Evidence im Kandidatencommit; Vorprüfungen getrennt; genaue Kandidatennachtests, überprüfbarer externer Handoff und unabhängiger Bericht ohne Git-Selbstreferenz. |
| P0-FULL / zusätzliche Arbeit 3 | PASS | Beide Full-Archive unabhängig inhaltlich/byteweise geprüft, insbesondere saubere Kandidatenrevision, 660/0/0/0 und alle 44 positiven JAR-Operationen. Kein eigener Full-Neulauf behauptet. |
| Gate 0 | Reviewvoraussetzungen erfüllt | Dieser Bericht prüft den festen Kandidaten; die formale Gate-Übernahme erfordert anschliessend die unveränderte versionierte Ablage durch A in einem ausschliesslichen Review-Archivierungscommit. |

Alle 34 neuen Featurefälle bleiben **NICHT AUSGEFÜHRT / spätere Phase**, wie im Kandidatenindex korrekt ausgewiesen:

- Phase 1: PRJ-001, PRJ-002, PRJ-003, PRJ-004, PRJ-005, PRJ-006, PRJ-007.
- Phase 2: PRJ-101, PRJ-102, PRJ-103, PRJ-104, PRJ-105, PRJ-106, PRJ-107, PRJ-108, PRJ-109, PRJ-110, PRJ-111, PRJ-112, PRJ-113, PRJ-114, PRJ-115.
- Phase 3: PRJ-201, PRJ-202, PRJ-203, PRJ-204, PRJ-205, PRJ-206, PRJ-207, PRJ-208, PRJ-209, PRJ-210, PRJ-211, PRJ-212.

## Architektur, Regressionen und negative Fachnachweise

Die tatsächlichen `GretlScript`-Map-first-Überladungen, Parameter-/Action-/Default-/Cleanup-Signaturen, sämtliche angegebenen `GroovyContextFacade`-Methoden und die drei vorhandenen `GroovyJobLoader`-Lademethoden entsprechen der Bestandsaufnahme. Pro Skript existieren weiterhin ein Builder, Binding und lokaler Taskindex; `run()` registriert und danach wird eingefroren. `file` delegiert weiterhin an den globalen Jobroot; `path` an das ursprüngliche CWD. `SourceLocations` arbeitet bislang mit dem Basename und unbekannter Zeile 0. Der Dateiselector `JobRuntime.java:138` verlangt weiterhin eine reguläre Datei. `JobCompiler.java:67` lehnt Mehrfachskripte ab; `PackageDescriptor.java:81` akzeptiert nur Schemas 1/2 und `JobPackageBuilder.java:256` schreibt internes Metadatenschema 1. Es gibt keinen vorgezogenen Projektparser oder Schema-3-Zweig.

Die Java-SPI, fachlich neutrale Runnerplanung und ein gemeinsamer Ausführungspfad bleiben unverändert. Die Quellen von `ExecutionPlanner`, `ResultStore`, `GroovyJobLoader`, `JobDslModel`, `GretlScript`, `GroovyContextFacade`, `CompilerSettings`, `JobSource`, `JobRuntime`, Paketklassen, `ArtifactJobRunner` und ausgewählten Regressionstests wurden unmittelbar geprüft. Keine globale Projektregistry, Runner-in-Runner-Konstruktion, neue Mavenpflicht des Jobstarts oder abgeschwächte Assertion wurde eingeführt.

Die unabhängig neu ausgeführte Suite enthält unter anderem Fremd-TaskRef-/Optional-/after-Regressionen, echte Groovydelegation und Quellenfehler, frühe Parameterdiagnosen, Cancellation, Shutdown, Cleanup und Retention, Secretmaskierung, Schema-1/2-Pakete, extern aufgelöste Extensions, Host-Classpath-Isolation sowie unabhängige Datei-/XLSX-/SQLite-/Transformationsorakel. Bei IO und Auswahlpaketen wurden die tatsächlichen fachlichen Assertions gelesen; ein blosser Exitcode wird nicht als Fachbeweis verwendet.

Zusätzliche selbst entworfene Prozessproben unter `target/independent-review/` belegen:

1. Zwei Actions werden über `after` geordnet; der Zugriff des Consumers auf das Producerresultat scheitert weiterhin mit ResultAccessException und Code 10. Der Report zeigt Producer SUCCEEDED, Consumer FAILED und die konkrete Originaldatei `after-result.groovy:3`.
2. Der reguläre Schema-2-Packager erzeugt ein minimales Job-JAR, ohne den Toplevelmarker anzulegen. Erst der tatsächliche JAR-Start führt den Toplevelcode aus.
3. Das JAR läuft aus fremdem CWD mit leerem HOME und PATH `/no-build-tools`, nachdem Originalskript und Ressource umbenannt wurden. Ein unabhängiger Leser prüft exakt `review resource ü` plus LF. Der JAR-Hash bleibt `4f8eb2b9b7bf07eb25c9720072ce55e80e48c0fac1f7c89f21f56b46f8de531c`.

Die erste Version dieser zusätzlichen Probe erwartete fälschlich bei jeder ResultAccessException ein `errors[].code` im JSON. Sie endete deshalb mit AssertionError, bevor die Paketprobe begann; `E/independent-probes-initial-assertion.log` bleibt erhalten. Der vorhandene Vertrag liefert hier Typ, Meldung und Quelle; der stabile Exceptioncode ist über `ResultAccessException.code()` und den selbst erneut ausgeführten Java-Regressionstest belegt. `DiagnosticCarrier` ist optional. Diese schon historisch dokumentierte Serialisierungsgrenze steht auch in `improvement-plan/phase-1-review-initial.md:48`. Die Zusatzassertion wurde an den tatsächlichen Reportvertrag angepasst; Produktcode, bestehende Tests und Abnahmekriterien wurden nicht geändert. Ein generelles JSON-code-Feld wird nicht als vorhanden ausgegeben.

Der Outcomeaudit prüft je Standardarchiv 92 Reports (48 SUCCEEDED, 30 FAILED, 14 CANCELLED) und je Full-Archiv 170 Reports (119 SUCCEEDED, 36 FAILED, 15 CANCELLED), jeweils genau einen korrelierten `gretl.run.finished`-Datensatz und genau die passenden Task-Start-/Terminalpaare. NOT_RUN wird nicht als ausgeführter Task erfunden. Alle Ereigniszeilen sind parsebares JSON; `telemetry.mode=none` bleibt ehrlich. Vier Ausführungen je Archiv haben keinen Report am kanonischen `reports/report.json`: zwei gezielte frühe Startfehler (Exit 2) und zwei erfolgreiche Hardening-Rechtefälle mit explizit umgeleitetem Reportpfad. Die beiden umgeleiteten Erfolgsreports samt ihren Terminalereignissen wurden zusätzlich selbst geprüft (`E/independent-explicit-reports-audit.json`); sie werden nicht in die Zählung kanonischer Runberichte hineingerechnet. Betriebssystem- und fachliche Abbruchcodes werden getrennt behandelt.

## Findings und Grenzen der Prüfung

Keine offenen CRITICAL-, MAJOR- oder MINOR-Findings gegen die Phase-0-Kriterien. Keine verdeckten Testfehler, gelockerten Kriterien, ausgelassenen Pflichtfälle oder uncommitteten Produktänderungen wurden festgestellt.

Die protokollierten Vorhinweise sind erhalten: javac-/Deprecationhinweise, native DuckDB-/SQLite-Zugriffe, Log4j2-Fallback im Testprozess und GNU-tar-Anforderung für lange Einträge. Sie werden nicht als neue Subprojektregressionen ausgegeben. PostGIS-AMD64-Emulation auf ARM64 und das bestehende nicht digestgepinnte Floci-Image bleiben erklärte Umgebungsgrenzen. Der vorhandene JDK-Patch wird beschrieben; diese reine Baseline ist keine neue Toolchain-/Dependencyaktualisierung oder Produktionsfreigabe.

Nicht unabhängig neu ausgeführt wurden Full-Maven/Backing-Services, Linux-CI, ein echtes GRETL-IMAGE, Windows, Native, ein leerer Maven-Dependencycache, produktive Jobs oder eine Fachanwenderabnahme. Der optionale Full-Lauf wurde anhand sämtlicher erreichbarer Nachweise selbst geprüft. JaCoCo misst Test-JVMs; separate Launcher-/JAR-Prozesse erhalten keine erfundene Coverage. Direkte Fremdausgaben und beliebig kodierte Secrets sind keine allgemeine DLP-/JSON-Garantie. Die zukünftige Settings-Sicherheit, qualifizierte Projekt-IDs, Projektpfade und Projekt-JARs sind noch nicht implementiert und werden nicht abgenommen.

Die nachfolgende unveränderte Versionierung dieses Berichts liegt bei A und ist zum Zeitpunkt dieser externen Erstellung noch ausstehend. Jede nachfolgende Änderung an Produktcode, Tests, Fixtures oder normativem Vertrag benötigt einen neuen Kandidaten und eine erneute unabhängige Review. Der reine Review-Archivierungscommit ersetzt die geprüfte Kandidaten-SHA nicht. Phase 1 wurde durch diesen Review nicht begonnen.

## Urteil

**PASS**

Die dokumentierte Baseline ist im festen Kandidaten vollständig vorhanden, korrekt vom unveränderten Produktstand und von späterer Featurearbeit getrennt und anhand eigener Ausführung reproduziert. Das verpflichtende Standardgate und der Kandidaten-Audit bestehen unabhängig; zusätzliche Full-/Artefaktnachweise wurden inhaltlich und byteweise geprüft. Es bestehen keine offenen wesentlichen Abweichungen gegen Phase 0. Die Gate-Freigabe wird gemäss Abschnitt 9 erst durch die unveränderte separate Archivierung dieses Berichts wirksam.
