# Phase 1 – Independent Review, Korrekturversuch 03

- Phase base SHA: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10`.
- Reviewed candidate SHA: `95dab5134fe609d7bf34b069fbfea620b7203f56`.
- Reviewer/Model/Session: unabhängiger Codex-Review-Agent B, eigene Sitzung `/root/independent_phase1_attempt03`. Die Sitzungsanweisung bezeichnet den Agenten als GPT-6-basiert; eine genauere konfigurierte Modellkennung ist nicht offengelegt und wird nicht geraten. B hat die Implementierung nicht geschrieben und keinen Codefix vorgenommen.
- Datum: 28. Oktober 2026. Eigener Pflichtlauf UTC `20:30:14.975840` bis `20:37:22.841903`; anschliessende Prüfung und Abschlusszeit in E/`audit-result.json`, `cr-line-result.json` und `final-identity.json` und `closure-identity.json`.
- Separater detached Worktree: `/Users/stefan/sources/gretl-runtime-phase-1-review-3`. `git rev-parse HEAD` bestätigt vor und nach der Prüfung exakt die Kandidaten-SHA; `git symbolic-ref -q HEAD` endet mit 1. Der getrackte Arbeitsbaum ist sauber. Alle Shellaufrufe verwenden ausdrücklich ihren workdir; der gemeinsame Default-CWD wurde nicht gewechselt.
- Vollständiger geprüfter Diff: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10..95dab5134fe609d7bf34b069fbfea620b7203f56`, **23 Dateien, 2179 hinzugefügte Zeilen**, sämtliche sechs Implementierungs-, Korrektur- und Archivierungscommits. Kein HEAD~1-Vergleich.
- Eigene externe Evidenzwurzel E: `/Users/stefan/sources/gretl-runtime-phase-1-evidence-20261028/attempt-03/independent-review-evidence/`. Dieser Bericht liegt ausserhalb des Worktrees und von E.

## Prüfgrundlage und Identität

Beide Commitobjekte wurden mit `git cat-file -t` nachgewiesen, die Vorfahrbeziehung mit `git merge-base --is-ancestor` (Exit 0). Der Worktree wurde mit `git worktree add --detach /Users/stefan/sources/gretl-runtime-phase-1-review-3 95dab5134fe609d7bf34b069fbfea620b7203f56` angelegt (Exit 0). Der Basisvergleich zur akzeptierten Phase-0-SHA `1ff17246caff404f75303dcc31d2f9814e4bc2aa` enthält ausschliesslich deren Reviewakte.

Gelesen und abgeglichen wurden Root-/Spec-AGENTS, die vollständige Subprojekt-Spezifikation 1.1, insbesondere §§3, 4.1, 7–9 und 12, die dortigen Runtime-/DSL-/Paketierungs-/API-/Kernklassen-/Loggingverträge 03/04/12/14/15/16 sowie `module-boundaries.md`, `testing.md` und `job-artifact-tests.md`. Dazu kamen Phasenbericht, Evidence-Index, aktueller Handoff und beide historischen unabhängigen Reviewberichte. Die Build-/Auslieferungs-/Abnahmegrenzen wurden ergänzend gegen 06/07/09/10 geprüft.

Die gesamte Dateiliste und Commitfolge stehen in E/`identity.json`; der vollständige Diff liegt in E/`full-phase.diff`, SHA-256 `b1b2dea185de801111959db2a489c776a57b7bda66b5261ba1d4f694ef439ced`. Selbst geprüft wurden alle sechs neuen Produktklassen, drei neuen Testklassen, beide Meldungskatalogergänzungen, alle Beispiele, README-Verweise, Phasenbericht, Evidence-Index sowie unveränderte historische Reviewakten. Der Evidence-Index wurde einschliesslich aller Befehle, Artefaktverweise, historischen Fehlversuche und 27 ausdrücklich späteren IDs ausgewertet; referenzierte XML sind vorhanden.

## Eigene Ausführung

Eigene Umgebung: Temurin OpenJDK `25.0.2+10-LTS`, Maven `3.10.0` über den unveränderten Wrapper, Groovy `6.0.0`, UTF-8, macOS `27.0.1` arm64/aarch64, Locale en_US, Europe/Zurich. Versions- und Statusausgaben stehen in E/`environment.json` und `final-identity.json`.

| Eigener Befehl | Exit | Beleg |
| --- | ---: | --- |
| `./mvnw -B clean verify` | 0 | E/`clean-verify.log`, `clean-verify-result.json`, `reports/`; UTC 20:30:14 bis 20:37:22 |
| `python3 scripts/audit-job-evidence.py --source 95dab5134fe609d7bf34b069fbfea620b7203f56` | 0 | E/`audit.log`, `audit-result.json`, `qualification/` |
| `git diff --check 5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10 95dab5134fe609d7bf34b069fbfea620b7203f56` | 0 | E/`identity.json`; keine Whitespacefehler |
| `javac -cp <gebauter Distributionsclasspath> -d E/classes E/Probe.java`; `java -cp E/classes:<Classpath> ch.so.agi.gretl.runtime.groovy.project.Probe E/probe-cases-gate` | 0 / 0 | E/`Probe.java`, `probe-compile.log`, `probe.log`, `probe-result.json`; **281 eigene Assertions** |
| `javac ... E/CrAstProbe.java`; `java ... CrAstProbe E/cr-ast-cases` | 0 / 1 | E/`cr-ast-result.json`, `cr-ast.log`; direkte AST-Ursachenprobe, anschliessend unerwartete Ablehnung gültiger CR-Settings |
| `javac ... E/CrLineProbe.java`; `java ... CrLineProbe E/cr-valid-line-cases` | 0 / 1 | E/`cr-line-result.json`, `cr-line.log`; strikte vollständige Gegenprobe mit **3 unerwarteten Ablehnungen** gültiger Settings, siehe M3 |
| `javac ... E/PrefixAstProbe.java`; `java ... PrefixAstProbe E/prefix-ast-cases` | 0 / 1 | E/`prefix-ast-result.json`, `prefix-ast.log`; BOM syntaktisch unzulässig, Shebang mit LF/CRLF akzeptiert, Shebang mit CR reproduziert M3 |
| `python3 E/verify-a-evidence.py` | 0 | E/`a-evidence-verification.json`; fremde Evidenz neu gehasht und geprüft |
| `python3 E/archive-own-evidence.py` | 0 | E/`own-evidence-result.json`; eigene aktive XML, Qualification, echte JAR-/Outputstichproben |

Die tatsächlichen argv, Classpaths, UTC und CWD der externen Java-Proben stehen in den jeweiligen JSON-Dateien; `probe-classpath.txt` enthält ausschliesslich die JARs der in diesem Worktree gebauten Distribution. Die Produktdateien wurden durch die Proben nicht verändert. Frühere Probenentwurfsfehler betreffen nur die externe Probe: ein Python-Quote-Fehler und die zunächst falsche Annahme, Groovy entferne auch Backslash plus einzelnes CR aus einem String. Diese Annahme wurde durch direkte AST-Inspektion widerlegt. Die davon getrennte gültige CR-Zeilentrennung wird unten ausdrücklich als Produktfehler bewertet und nicht als Probenfehler ausgeblendet.

Der volle Pflichtlauf verwendet keine Skip-Flags und keine selektive Testauswahl. Ausschliesslich aktive Root-POM-Module wurden gezählt; keine alten GeoTools-XML. **112 aktive XML, 624 Tests, 0 Failures, 0 Errors, 0 Skips**, davon 604 bestehende und 20 neue Fälle:

| Modul | Tests | Failures / Errors / Skips |
| --- | ---: | --- |
| gretl-api | 17 | 0 / 0 / 0 |
| gretl-runner | 185 | 0 / 0 / 0 |
| gretl-groovy | 69 | 0 / 0 / 0 |
| gretl-i18n | 4 | 0 / 0 / 0 |
| gretl-logging | 38 | 0 / 0 / 0 |
| gretl-packager | 12 | 0 / 0 / 0 |
| gretl-database-tasks | 21 | 0 / 0 / 0 |
| gretl-io-tasks | 54 | 0 / 0 / 0 |
| gretl-cli | 55 | 0 / 0 / 0 |
| gretl-job-runtime | 24 | 0 / 0 / 0 |
| gretl-ilivalidator | 3 | 0 / 0 / 0 |
| gretl-interlis-tasks | 34 | 0 / 0 / 0 |
| examples/file-size-extension | 0 | 0 / 0 / 0 |
| gretl-dist | 13 | 0 / 0 / 0 |
| gretl-integration-tests | 95 | 0 / 0 / 0 |

## Fremde A-Evidenz

As Ausführungen sind keine eigenen Ausführungen von B. Der Handoffhash wurde selbst neu berechnet und stimmt mit seiner separaten `.sha256` überein: `3dbfa6d0d366cdec3ad768b543cc43f541005251b3e9979e64ab5663ce08b8c1`. Alle referenzierten Datei-Hashes stimmen mit den tatsächlich gelesenen Dateien und den Kandidatendokumenten überein.

Beide A-Archive wurden vollständig erneut gehasht: jeweils **3366 Dateien**, keine Abweichung, jeweils **112 XML, 624/0/0/0**, Standardlauf und Audit Exit 0. Manifesthashes: Vorlauf `6293c28b8a5fbbe67fc15219759535b73fb0ea215e83c3eedd1e8998fa7fd0be`, Nachlauf `382707325b1289adb57d8b9193b35f71d54af9867b08aaa3ffbb72285352379b`. Tatsächlicher Katalog und Qualification weisen den Vorlauf korrekt dirty auf `e0783ffbcac664f1be37fe576d09e84e5990775a`, den Nachlauf sauber auf der exakten Kandidaten-SHA aus. Alle **841** vor dem Vorlaufstart erfassten Implementierungsinputs stimmen bytegleich mit Bs Checkout überein; die Erfassungszeit liegt vor dem Vorlaufstart.

Die historischen FAIL-Berichte sind unverändert: Versuch 01 SHA-256 `6d7579f24dc3c510cb029c88328f683777421b8f47e4148ba87f3b5383f6c091`, Versuch 02 `82f3a1a6906d9b58c2bcb197fa4940fde02f315092ecdff525fef15651e7e6d5`. Der dokumentierte frühere Abbruch auf `d9831a8dad1630ada6e76cb76f1d74c7372c5707` bleibt Exit 143 und wird nicht als erfolgreiches Gate gewertet. Keine historische Freigabe wurde übertragen.

## Pflicht-Testfallmatrix

| ID | Ergebnis | Selbst geprüftes Orakel |
| --- | --- | --- |
| PRJ-001 | PASS | Root zuerst, danach kanonische explizite Include-Reihenfolge; keine impliziten Eltern-/unselektierten Jobs; wiederholt stabil, immutable Sammlungen, erhaltene Defaultpositionen. |
| PRJ-002 | PASS | Zwei Includes ohne Root; verschachtelte `a/b/job.groovy`, kein notwendiger Elternjob und keine Ausführung untergeordneter Settings. |
| PRJ-003 | PASS | Leere/ungültige Includes, Duplikate nach Kanonisierung, doppelte Direktiven/Defaults und fehlende Dateien liefern konkrete Codes und Originalpositionen; beide Duplikatquellen bleiben sichtbar. |
| PRJ-004 | PASS | Ausführbare Settings, Dateiaktion, println/System.exit, Variablen, GStrings, Ausdrücke, Klassen/Methoden, Imports und ASTTest(CONVERSION)/Grab bleiben abgelehnt; Marker fehlt. M1 ist mit Originaltokenposition nach Emoji/Kommentaren/Stringdecoys und CRLF unabhängig bestätigt behoben. |
| PRJ-005 | PASS | Root, `:a:b` und `:a_b` bleiben verschieden; gültige deterministische Namen mit voller SHA-256; tatsächliche Groovy-6-Klassengenerierung erzeugt drei verschiedene Klassen. |
| PRJ-006 | PASS | Relative Roots, reguläre Dateien, fehlende Skripte, Symlinks an Root/Settings/Dateien/Verzeichnisketten und Root-Escape geprüft; exakt 256 KiB akzeptiert, Überschreitung und malformed UTF-8 abgelehnt. |
| PRJ-007 | FAIL | Absolute Defaultsyntax, keine vorgezogene Taskexistenzprüfung und keine Jobausführung stimmen in Suite/eigenen Proben. M2 ist für BMP/Nicht-BMP und einfache/doppelte LF-/CRLF-Continuations behoben. Eine weitere gültige Defaultkonfiguration mit erlaubter leerer/commentierter CR-Zeile wird jedoch fälschlich abgewiesen (M3). |

Die sieben engen bestehenden Tests reichen nicht für eine Freigabe: Der allgemeine verbindliche Settings-Sprachvertrag gilt auch für zusätzliche gültige Quelltexte. Der fehlgeschlagene zusätzliche Fall wird deshalb in der Matrix und im Verdict berücksichtigt.

## Architektur und Rückwärtskompatibilität

Sechs kleine neue Produktklassen liegen im bestehenden `gretl-groovy`-Modul; lediglich additive deutsche/englische i18n-Meldungen kommen hinzu. Keine neuen Module, Dependencies, Plugins, Profile, POM-, Wrapper- oder CI-Änderungen. Bestehende API, Runner, CLI, JobRuntime, Packager, Logging, Taskmodule, Groovy-Produktklassen und bisherige Testdateien bleiben bytegleich; keine vorhandene Assertion wurde abgeschwächt.

`ProjectPath` ist IO-frei und immutable, trennt logische IDs von Dateisystempfaden und validiert Segmente zentral. Die Records normalisieren absolute Pfade und kopieren Sammlungen defensiv. Die dokumentierte additive `defaultTargetSources`-Map samt Vierargument-Konstruktor ist gerechtfertigt, um die geforderte spätere Defaultdiagnostik zu ermöglichen.

Der Parser liest begrenzt und dekodiert strikt UTF-8; er prüft reale Rootzugehörigkeit und Symlinks. Ausschliesslich `SourceUnit.parse()`/`convert()`, keine semantische Analyse, Bytecodeerzeugung, Settingsinstanziierung oder Jobausführung. Die AST-Allowlist ist klein. Der M1-Lexerzweig dient erst nach erkannter AST-Verletzung der Positionierung; die M2-Änderung erweitert die Allowlist nicht. M3 ist eine zusätzliche fehlerhafte Ablehnung gültiger Quelle, kein nachgewiesener Ausführungsbypass.

Kein ProjectTaskIndex, geteilter Builder, Loader-Projektmodus, Directory-CLI, Projektquellhash, Schema 3 oder Projekt-JAR wurde vorgezogen. Das Beispiel beschreibt ausdrücklich zwei eigenständige Einzeljobs; Parser und unveränderter Loader/Runner prüfen unterschiedliche echte UTF-8-Inhalte. Phase 2 wurde nicht begonnen.

Die unveränderte komplette Suite qualifiziert bestehende Einzeldatei-/Schema-1/2-/CLI-/JAR-Wege samt Fehler-, Cancellation-, Cleanup-, Quellen- und Loggingfällen. Selbst gelesene `IoLocalJobIT`, `SelectedPackageLocalJobIT`, `SelectedPackageSupport` und der Audit prüfen reale externe Prozesse, fachliche Datei-/Datenbank-/XLSX-Inhalte, Service-/Providerauswahl und isolierte Starts; keine blossen --help-/Exitcode-Tests.

## Echte JARs, Outputs und Provenienz

Der eigene Audit bestätigt den sauberen Distributionskatalog auf exakt der Kandidaten-SHA, prüft Distributionsdateien und jeden gestarteten JAR-Hash erneut und gleicht Prozess-/Reportstatus ab. **82 JAR- und 14 Script-Prozesse, kein IMAGE.** Der Standardlauf wird nicht als vollständige positive Dienstqualifikation aller 44 Operationen oder als Coverage nicht instrumentierter Produktprozesse ausgegeben.

Zusätzlich selbst direkt geöffnete und gehashte Artefakte:

- IO-Extract-JAR `02de0d09e144f0cd1badf3e690eae631fe447a5181ba8b6513a3fc1c7c72dcef`: Jobmetadaten Schema 1, Dependencyinventar Schema 2 und wirkliche Einstiegsklasse vorhanden; entpackte `nested/payload.txt` bytegleich zur kanonischen Eingabe, Output-SHA `7d876edbee47051cfe7aa5fa596f5ffbdb441c872000efe83c34aac66ec8fa6f`.
- Minimaler Erfolg und Fachfehler starten dasselbe unveränderte JAR `5b389d9a6ad8ff3fa993859f2d55ab618f98a42d72a42e342161fd7e4c404043`. Erfolg schreibt exakt `minimal package ü\n`; Fehler liefert Code 10 mit `selected-package-business-failure`; beide schreiben `cleaned`. Isolation belegt gelöschte Quellkopie, fremdes CWD, leeres Maven-HOME und PATH `/gretl-no-build-tools`. Metadaten/Inventar/Einstiegsklasse wurden aus den JARs selbst geprüft.

Konkrete Szenarioverzeichnisse und Assertions stehen in E/`own-evidence-result.json`. Eigene Logs, 112 aktive XML, Qualification-/Auditdaten, Ausführungsmetadaten und ausgewählte tatsächliche JARs/Outputs/Reports sind extern gesichert. Keine Rohkonfiguration, Secretdateien oder pauschalen Workspaces wurden kopiert. E/`SHA256SUMS.json` bindet **1612 Dateien**, SHA-256 `e4d376d04a812c7a1f83ef6eaa9e26eeb387f0638e6609829d5f76aedbe04b4c`.

## Offenes Finding

**MAJOR – M3: Gültige Settings nach einem einzelnen CR werden durch abweichende AST-/Text-Zeilenbehandlung abgewiesen.**

- Datei: `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/GretlProjectParser.java:126` (`source.split("\\r\\n|\\r|\\n", -1)`) und `:247–253` (`simpleQuotedLiteral`), aufgerufen bei `:151–153`.
- Minimale Reproduktion auf dem exakten Kandidaten: reguläre Root-`job.groovy` anlegen und `settings.gretl` mit den UTF-8-Bytes **`0D` + `defaults ':run'` + `0D`** schreiben. Danach `new GretlProjectParser().parse(root)` aufrufen. Es sind eine leere Zeile und eine erlaubte Anweisung, kein String mit escaped CR und keine Taskexistenzprüfung nötig.
- Direkter Groovy-6-Nachweis: `SourceUnit.parse()`/`convert()` liefert genau eine `ExpressionStatement` mit impliziter `MethodCallExpression` namens `defaults`, `ArgumentListExpression` und `ConstantExpression(String)` mit exakt `:run`. Dieser AST erfüllt den festgelegten Sprachvertrag. Groovy liefert für das Literal **Zeile 1, Spalte 11**: ein einzelnes CR erhöht hier die Lexerzeile nicht, sondern bleibt Teil des Spaltenoffsets.
- Tatsächlich: Der Parser trennt dieselbe Originalquelle am CR, wählt für AST-Zeile 1 die leere erste Teilzeile und verwirft das erlaubte Literal bei Zeile 252. Ergebnis **`PROJECT_SETTINGS_UNSUPPORTED`, source 1:11**. Mit LF oder CRLF wird dieselbe gültige Anweisung angenommen. Varianten mit `// heading` oder `/* 😀 */` vor dem CR liefern ebenfalls erlaubte ASTs, werden aber abgewiesen (1:21 bzw. 1:18).
- Eigene Belege: E/`CrLineProbe.java`, `cr-line-result.json`, `cr-line.log`, echte Dateien unter `cr-valid-line-cases/`; Kompilierung Exit 0, strikter Produktlauf Exit 1 wegen **3** unerwarteter Ablehnungen, alle sechs LF-/CRLF-Kontrollen akzeptiert. E/`CrAstProbe.java`/`cr-ast-result.json` dokumentieren die direkte Unterscheidung zur folgenden unzulässigen Variante.
- **Abgrenzung:** Ein Backslash plus einzelnes CR *innerhalb* eines zitierten Strings wird von Groovy nicht wie LF/CRLF entfernt. Der AST-Wert für `a` + Backslash + CR + `b` hat Codepoints `[97, 92, 13, 98]` und ist deshalb kein gültiger Include-Name. Dessen `PROJECT_INCLUDE_INVALID` ist korrekt und kein Finding. M3 betrifft ausschliesslich normale, von Groovy syntaktisch akzeptierte CR-Zeilentrennung vor einem konstanten gültigen Literal.
- Zusätzliche Präfixkontrolle: BOM allein sowie BOM vor LF/CRLF/CR oder Shebang scheitert bereits in `SourceUnit.parse()` mit `MultipleCompilationErrorsException`; daraus wird keine neue erlaubte Syntax abgeleitet. Shebang mit LF/CRLF erzeugt den erlaubten AST und wird korrekt akzeptiert. Shebang mit einzelnem CR erzeugt denselben erlaubten AST (Literalquelle 1:31) und reproduziert M3. E/`PrefixAstProbe.java`, `prefix-ast-result.json` und `prefix-ast.log` halten alle acht Varianten fest.
- Vertrag: §3.3 erlaubt Kommentare und Leerzeilen, UTF-8 und diese konkrete AST-/Literalform; eine Beschränkung auf LF/CRLF ist nicht festgelegt. §4.1 verlangt deren unveränderte AST-Interpretation. Das zusätzliche Text-Splitting darf eine erlaubte AST-Form nicht ablehnen. Es wird keine neue escaped-CR-Stringsemantik verlangt.
- Schwere: Gültige Projektkonfiguration wird von einer Pflichtfunktion abgewiesen; dies ist keine kosmetische Diagnosefrage. Der eigene grüne Reactor deckt den Fall nicht ab und ersetzt ihn nicht.
- Erforderliche Korrektur durch A: Originalquelle und Groovy-Koordinaten für die Literalformprüfung konsistent zuordnen, ohne die AST-Allowlist oder Slashy-/Triplequote-/GString-Ablehnung zu lockern. Gezielte echte UTF-8-Dateiregressionen für Rootdefaults/Includes nach CR, LF und CRLF samt Kommentaren, Originalpositionen und Nichtausführung ergänzen. Danach neuer Kandidat unter unveränderter Phasenbasis mit vollständigen Vor-/Nachgates und neuer unabhängiger Review. B hat keinen Fix vorgenommen.

M1 und die konkret zuvor nachgewiesenen M2-Fälle sind unabhängig als behoben bestätigt. Weitere offene CRITICAL-/MAJOR-Befunde wurden im beschriebenen Umfang nicht gefunden.

## Nicht unabhängig qualifizierte Grenzen

Der volle Aufruf `./mvnw -B -Pgeotools-probe,backing-services clean verify` wurde in Phase 1 nicht erneut ausgeführt; er ist erst in Phase 3 Pflicht. Historische Phase-0-Full-Belege werden nicht als aktuelle Tests ausgegeben. Kein realer IMAGE-, Linux-/Windows-, Native-, leerer Authoringcache- oder Produktionslauf.

PRJ-101–115 und PRJ-201–212, gemeinsamer Projektgraph/Run, Cross-Project-Resultate/-Parameter/-Cleanup/-file, Directory-CLI, Projektquellhash und Projekt-JAR sind spätere Phasen. Sie werden weder als aktuell fertig noch als fehlende Phase-1-Pflicht bewertet. Die Pfadprüfung ist keine allgemeine Dateisystemsandbox und keine atomare Transaktion gegen gleichzeitig ausgetauschte Verzeichnisse.

## Verdict

**FAIL**

Eigener vollständiger Standardreactor, SHA-Audit, Sicherheits-/Symlinkproben und M1-/M2-Retests sind erfolgreich. Das zusätzlich streng reproduzierte MAJOR-Finding M3 verletzt dennoch den verbindlichen Phase-1-Sprachvertrag. Gate 1 ist für diese Kandidaten-SHA nicht freigegeben. A muss ausschliesslich Phase 1 korrigieren und neu prüfen lassen; Phase 2 bleibt ausserhalb des Auftrags.
