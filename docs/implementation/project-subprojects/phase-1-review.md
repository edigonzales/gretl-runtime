# Phase 1 – Independent Review, Korrekturversuch 04

- Phase base SHA: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10`.
- Reviewed candidate SHA: `1935a95f73c45028a04316150e165ec0a61a52a5`.
- Reviewer/Model/Session: unabhängiger Codex-Review-Agent B, eigene Sitzung `/root/independent_phase1_attempt04`. Gemäss Sitzungsanweisung GPT-6-basiert; eine genauere konfigurierte Modellkennung ist nicht offengelegt und wird nicht geraten. B hat die Implementierung und As Berichte nicht geschrieben, keine Codefixes vorgenommen und keinen Implementierungscommit erstellt.
- Reviewabschluss UTC: 2026-10-28T21:17:16.851795+00:00.
- Eigener separater detached Worktree: `/Users/stefan/sources/gretl-runtime-phase-1-review-4`. `git rev-parse HEAD` bestätigt vor und nach den Prüfungen exakt die Kandidaten-SHA; `git symbolic-ref -q HEAD` liefert erwartungsgemäss Exit 1. Der getrackte Arbeitsbaum bleibt sauber. Alle Shellaufrufe benutzen explizit ihren workdir; der gemeinsame Default-CWD wurde nicht gewechselt.
- Vollständig geprüfter Diff: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10..1935a95f73c45028a04316150e165ec0a61a52a5`, alle **24 Dateien und 2638 Additions**, einschliesslich aller acht Commits seit der unveränderten Phasenbasis. Kein verkürzter HEAD~1-Vergleich.
- Eigene externe Evidenzwurzel E: `/Users/stefan/sources/gretl-runtime-phase-1-evidence-20261028/attempt-04/independent-review-evidence/`. Dieser Bericht liegt ausserhalb des Worktrees und von E.

## Identität, Norm und gesamter Phasenumfang

Beide Commitobjekte wurden mit `git cat-file -t` als Commit nachgewiesen, die Vorfahrbeziehung mit `git merge-base --is-ancestor` (Exit 0). Der eigene Checkout entstand durch `git worktree add --detach /Users/stefan/sources/gretl-runtime-phase-1-review-4 1935a95f73c45028a04316150e165ec0a61a52a5` (Exit 0). E/`identity.json` und `final-identity.json` enthalten tatsächliche Befehle, Ausgaben, Exitcodes, CWD, UTC und die bestätigte Identität.

Gelesen und abgeglichen wurden Root-/Spec-AGENTS, die vollständige Subprojekt-Spezifikation **1.1**, besonders §§3, 4.1, 7–9 und 12, ihre Runtime-/DSL-/Paketierungs-/API-/Kernklassen-/Loggingverträge 03/04/12/14/15/16 und die drei Implementierungsverträge `module-boundaries.md`, `testing.md`, `job-artifact-tests.md`. Ergänzend wurden die Betriebsgrenzen aus 06 und die Plattform-/Buildleitlinie aus 07 herangezogen. Phasenbericht, gesamter Evidence-Index, aktueller Handoff und alle drei historischen FAIL-Berichte wurden selbst geprüft. Der akzeptierte Phase-0-Kandidat ist `1ff17246caff404f75303dcc31d2f9814e4bc2aa`; dessen Diff bis zur aktuellen Phasenbasis enthält ausschliesslich `phase-0-review.md`.

Der vollständige Diff liegt in E/`full-phase.diff`, SHA-256 **`0fc676050a61b15622c6af0b26cdfa81952d8818a2574f8dde44b3ef9d4d4418`**. Die acht Commitinventare sind unter E/`commit-<volle-SHA>.txt` erhalten. Geprüft wurden Erstimplementierung `f1f1074c...`, M1-Korrektur `d9831a8...`, reine Berichtpräzisierung `b88ad519...`, M2-Korrektur `95dab513...`, aktuelle M3-/Klammerkorrektur `1935a95...` und die drei separaten Reviewarchivierungen `4c4d8ad...`, `e0783ff...`, `42b8008...`.

Alle sechs neuen Produktklassen, drei neuen Testklassen, beide Meldungskatalogergänzungen, README-Verweise und sämtliche Beispieldateien wurden geprüft. Die 24 Dateien umfassen genau diese Änderungen sowie Phasenbericht, Evidence-Index und die drei historischen Reviewakten. Der Evidence-Index wurde strukturell einschliesslich aller Befehle, historischen Fehlversuche, XML-Verweise, Testzuordnung, Inputhashes und 27 ausdrücklich späteren IDs ausgewertet. Referenzierte XML sind vorhanden. Er behauptet keine selbstreferenzierende Kandidaten-SHA.

## Eigene Pflichtprüfung und zusätzliche Ausführung

Eigene Umgebung: Eclipse Temurin OpenJDK `25.0.2+10-LTS`, Maven `3.10.0` über den unveränderten Wrapper, echtes Groovy `6.0.0` aus dem gebauten Surefire-Classpath, UTF-8, macOS `27.0.1` aarch64, Locale en_US und Europe/Zurich. Tatsächliche Versionsausgaben stehen in E/`identity.json`.

| Eigener Befehl | Exit | Nachweis |
| --- | ---: | --- |
| `./mvnw -B clean verify` | 0 | E/`standard.log`, `standard-start.json`, `standard-result.json`, `reports/`; UTC `2026-10-28T21:09:22.333043+00:00` bis `2026-10-28T21:15:13.628249+00:00` |
| `python3 scripts/audit-job-evidence.py --source 1935a95f73c45028a04316150e165ec0a61a52a5` | 0 | E/`audit.log`, `audit-result.json`, `qualification/`; UTC `2026-10-28T21:15:16.590226+00:00` bis `2026-10-28T21:15:19.532470+00:00` |
| `git diff --check 5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10 1935a95f73c45028a04316150e165ec0a61a52a5` | 0 | E/`identity.json`, keine Whitespacefehler |
| `javac -cp <tatsächlicher Surefire-Classpath> E/IndependentParserProbe.java`; `java -cp E:<derselbe Classpath> IndependentParserProbe E/probe-cases` | 0 / 0 | E/`independent-probes.json`, `probe-0.log`, `probe-1.log`, Quelle; **455 strikte eigene Assertions über 134 echte Dateibäume** |
| Eigene inline Python-Hash-/XML-/Handoffprüfung mit `python3 -` | 0 | E/`foreign-evidence-verification.json`, `foreign-provenance-verification.json`; vollständige Neuverhashung und inhaltlicher Provenienzvergleich |
| `python3 /Users/stefan/sources/gretl-runtime-phase-1-evidence-20261028/attempt-04/independent-review-evidence/archive-own-evidence.py` | 0 | Quelle in E; eigene XML-Auswertung, Audit, direkte JAR-/Outputstichproben und selektive Archivierung, E/`own-evidence-result.json` |

Die vollständigen tatsächlichen Java-argv einschliesslich Classpath, UTC und CWD stehen in E/`independent-probes.json`. Vor der gültigen Probe gab es einen eigenen Authoringfehler beim Schreiben der externen Java-Datei (Python-Quotegrenze), gefolgt von `javac` Exit 2 wegen fehlender Datei. Dieser Versuch ist in E/`probe-authoring-error.json` und `.log` dokumentiert; dabei fand kein Produktlauf statt. Die anschliessend korrekt verfasste Probe kompiliert und besteht alle Assertions ohne Lockerung eines Produktorakels.

Das Pflichtgate lief ohne Skip-Flags, selektive Testflags, Assumptions oder deaktivierte neue Tests. Gezählt wurden ausschliesslich die aktiven Root-POM-Module, keine inaktiven GeoTools-XML. Die **112 aktiven XML** ergeben **626 Tests, 0 Failures, 0 Errors, 0 Skips**: 604 unveränderte bisherige und 22 neue JUnit-Fälle.

| Aktives Modul | Tests | Failures / Errors / Skips |
| --- | ---: | --- |
| gretl-api | 17 | 0 / 0 / 0 |
| gretl-runner | 185 | 0 / 0 / 0 |
| gretl-groovy | 71 | 0 / 0 / 0 |
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

## Pflicht-Testfallmatrix

| ID | Ergebnis | Selbst geprüftes Orakel |
| --- | --- | --- |
| PRJ-001 | PASS | Root zuerst, danach kanonische Includes exakt in expliziter Reihenfolge; keine impliziten Eltern- oder unselektierten Jobs. Wiederholte Ausgabe, Defaultpositionen und immutable Sammlungen. Eigene Probe ergänzt bewusst `z`, `:b:c` in anderer Verzeichnisreihenfolge. |
| PRJ-002 | PASS | Zwei Includes ohne Root, korrektes verschachteltes `b/c/job.groovy`, kein notwendiger Elternjob; untergeordnete Settings werden nicht evaluiert. |
| PRJ-003 | PASS | Leere/ungültige Includes, kanonische Duplikate, wiederholte Direktiven/Defaults und fehlende Skripte liefern konkrete Codes, Originaldatei und tatsächliche Position. Die eigene mehrzeilige Duplikatprobe bestätigt Zeile 2. |
| PRJ-004 | PASS | Dateiaktion, println/System.exit, Variablen, GStrings, Ausdrücke, Listen/Spread, explizite Receiver, Closures, Klassen/Traits/Enums, Methoden, Imports, Labels und Annotationen werden abgewiesen. ASTTest(CONVERSION)/Grab erzeugen keine Marker. Die M1-Originaltokenposition besteht auch mit Kommentar-/Stringdecoys und Unicode. |
| PRJ-005 | PASS | `:a:b` und `:a_b` bleiben verschiedene Identitäten; volle SHA-256 und deterministische gültige virtuelle Namen. Drei tatsächlich durch Groovy 6 generierte und instanziierte harmlose Scriptklassen liefern unabhängig die jeweils richtigen unterschiedlichen Bodywerte. |
| PRJ-006 | PASS | Relative Roots, Leerzeichen, normale/fehlende/nicht reguläre Dateien, Root-Escape und direkte/intermediäre/interne/externe/dangling Symlinks werden geprüft. Eigene Probe akzeptiert exakt 262144 UTF-8-Bytes, verwirft 262145 Bytes und ungültige UTF-8-Surrogatbytes. |
| PRJ-007 | PASS | Nur absolute qualifizierte Defaultsyntax; unbekannte syntaktisch gültige Ziele werden ohne Taskexistenzprüfung erhalten. Jobs mit absichtlichem Toplevelfehler/Exit werden nicht geladen. Originale Defaultpositionen bleiben erhalten. M2/M3 und geklammerte Konstanten sind mit eigener direkter AST-Gegenprüfung erfolgreich. |

## Parsergrenze, Architektur und frühere Findings

`ProjectPath` ist IO-frei und immutable, validiert Segmente zentral und trennt logische IDs von nativen Dateisystempfaden. `ProjectEntry` und `GretlProject` normalisieren absolute Pfade und kopieren Listen/Maps defensiv. Die dokumentierte additive `defaultTargetSources`-Map mit erhaltenem Vierargument-Konstruktor bewahrt die für spätere unbekannte Defaults verlangte Herkunft.

Der Parser begrenzt bereits den Datei-Read auf 256 KiB plus Erkennungsbyte, dekodiert UTF-8 strikt und prüft statisch reale Rootzugehörigkeit, reguläre Dateien und Symlinks. Die definierte Root darf über kanonisierte Elternpfade erreicht werden; innerhalb der gewählten Root werden Settings-/Skript-/Include-Symlinks abgewiesen. Dies ist entsprechend Dokumentation keine atomare Transaktion gegen gleichzeitig ausgetauschte Verzeichnisse.

Settings erhalten ausschliesslich `SourceUnit.parse()`/`convert()`. Keine semantische Analyse, Bytecodekompilation, Instanziierung, `run()`/`evaluate()` oder Jobausführung. Die kleine strukturelle AST-Allowlist bleibt massgeblich. Die neue einmalige `GroovyLangLexer`-Tokenliste wird erst nach der Conversion erstellt. Die Literalprüfung arbeitet nur für bereits erlaubte `ConstantExpression(String)`, sucht mit binärer Suche innerhalb des nativen AST-Quellbereichs und überspringt nur Leerraum-/Kommentar-/NL-/LPAREN-Tokens vor dem Literal. Der exklusive Bereichsabschluss verhindert eine Suche in nachfolgenden Argumenten. StringLiteral muss einfach/doppelt zitiert sein; Triplequotes, Slashy/Dollar-Slashy, GStrings und berechnete Ausdrücke bleiben abgewiesen. AST- und Tokenkoordinaten haben dieselbe Groovy-Herkunft, ohne eigene UTF-16- oder Zeilenumrechnung.

Die eigenen 455 Assertions prüfen Kombinationen aus Nicht-BMP-Kommentaren, CR/LF/CRLF, Shebang, mehrfachen Klammern, Kommentaren und mehrzeiligen Klammerbereichen sowie einfachen/doppelten LF-/CRLF-Continuations. Dabei werden Defaultpositionen direkt mit einer separat erzeugten echten SourceUnit-AST verglichen. Bare CR innerhalb eines Strings nach Backslash bleibt korrekt ein ungültiger Include-/Targetname. Die bereits syntaktisch ungültigen BOM-Präfixe begründen keine zusätzliche erlaubte Sprache.

M1 (positionsloser Modulimport), M2 (Codepoint-/UTF-16-Verwechslung und unbegründete Einzeiligkeitsgrenze) und M3 (abweichendes CR-Zeilensplitting) sind auf diesem Kandidaten unabhängig als behoben bestätigt. Die zusätzliche von A vor dem Kandidaten gefundene Klammerursache ist ebenfalls unabhängig reproduziert und behoben; As diagnostischer Erstlauf ohne Summenassertion wird nicht nachträglich als unabhängiger strikter Erfolg ausgegeben.

Sechs kleine neue Klassen im bestehenden `gretl-groovy` und additive i18n-Meldungen genügen. Keine POM-, Dependency-, Plugin-, Profil-, Wrapper- oder CI-Änderung, kein neues Modul, kein Modulzyklus und keine globale Registry. API, Runner, CLI, JobRuntime, Packager, Logging, Taskmodule sowie bestehende Groovy-Produktklassen und Tests bleiben bytegleich; keine vorhandene fachliche Assertion wurde geändert oder abgeschwächt. Kein ProjectTaskIndex, geteilter Builder, Projektloader, Verzeichnisdispatcher, Projektquellhash, Schema 3 oder Projekt-JAR wurde vorgezogen.

Das Beispiel ist korrekt als zwei unabhängige vorhandene Einzeljobs dokumentiert; der echte Parser/Loader/Runner prüft deren unterschiedliche lokale UTF-8-Inhalte. Der eigene komplette Reactor qualifiziert weiterhin die alten Einzeldatei-/CLI-/Schema-1/2-/JAR-Wege einschliesslich Fehler-, Cancellation-, Cleanup-, Quellenpositions- und Loggingfällen. Die selbst gelesenen `IoLocalJobIT`, `SelectedPackageLocalJobIT`, `SelectedPackageSupport` und `PackageArtifactAssertions` verwenden externe echte Produktprozesse und unabhängige Byte-/XTF-/JDBC-/XLSX-Reader, Provider-/Service-/Inventarassertions und isolierte Starts.

## Fremde Handoff-Evidenz und historische Unverändertheit

As Ausführungen sind fremde Nachweise, keine eigenen Ausführungen von B. Der neu berechnete Handoffhash stimmt mit `phase-1-handoff.sha256`: **`090a5d3df93a8baa57b557d15ddbedf7c3310bad2c58c6f071c106cbd5915b55`**. Alle zehn referenzierten Dateien und die 841 Implementierungsinputs stimmen mit dem Kandidatencheckout überein. Die Inputerfassung `2026-10-28T20:55:32.174773+00:00` liegt vor dem finalen Vorlaufstart `2026-10-28T20:55:32.227769+00:00`.

Beide aktuellen A-Standardarchive enthalten je **3366 neu gehashte Dateien, 112 aktive XML und 626/0/0/0**, Standardlauf und Audit jeweils Exit 0. Manifesthash Vorlauf: `a5264aca39e3ed1b4accee75c6e731cbae698c9728c89bfbc0d2086e7c610659`; Nachlauf: `f23012104541ba1abe88c7f5be443988d2fc01f411fd480635e45e4dfc82abde`. Vorlaufkatalog und Summary weisen korrekt `42b8008c6a8af3c42a4b1e810a16c0130b698620`, dirty=true, aus; Nachlauf beide exakt die Kandidaten-SHA, dirty=false. Für beide Archive wurden zusätzlich alle 82 tatsächlich gestarteten JARs erneut gegen execution-SHA geprüft; je 14 Script-Ausführungen bleiben getrennt.

Auch alle sechs historischen before-/after-Standardarchive von Erstversuch, Versuch 02 und 03 wurden vollständig erneut gehasht: je 3366 Dateien und null Abweichungen. Die drei FAIL-Berichte bleiben bytegleich mit den dokumentierten Hashes `6d7579f24dc3c510cb029c88328f683777421b8f47e4148ba87f3b5383f6c091`, `82f3a1a6906d9b58c2bcb197fa4940fde02f315092ecdff525fef15651e7e6d5`, `012fe34ce2ac6ec41be072b741c1e43ea7d3c90522f1fef7de02d8a9e3103fb9`. Ihre grünen Testläufe ersetzen ihre jeweiligen negativen Urteile nicht. Der historische CR-Zwischenvorlauf mit 625 Fällen und der abgebrochene Dokumentnachlauf mit Exit 143 werden nicht als aktuelle Gates gewertet.

## Eigene JAR-, Output- und Provenienznachweise

Der eigene exakte Audit prüft den sauberen Distributionskatalog, alle Distributionsprüfsummen und jeden gestarteten JAR-Hash und gleicht Prozess-/Reportcodes ab. Er bestätigt **82 JAR- und 14 Script-Ausführungen, kein IMAGE**. Das Standardgate behauptet keine vollständige positive Backing-Service-Qualifikation aller 44 Operationen und keine Coverage der nicht instrumentierten Produktprozesse.

Zusätzlich direkt aus eigenen realen Artefakten gelesen und gehasht:

- IO-Extract-JAR SHA-256 `035aa9f03fdcb5d057bab738656c357b7ab18a3069a60fba53d4a6c07630b008`: interne Jobmetadaten Schema 1, Dependencyinventar Schema 2, tatsächliche Einstiegsklasse, genau ein Logback-SLF4J-Provider und kein CLI-/Packagercode. `output/nested/payload.txt` ist bytegleich zur kanonischen Eingabe, SHA-256 `7d876edbee47051cfe7aa5fa596f5ffbdb441c872000efe83c34aac66ec8fa6f`.
- Minimaler Erfolgs- und Fachfehlerjob starten dasselbe unveränderte JAR SHA-256 `3cfc8b804f826541a7c03635d075b17820b48164bac4b093fa6709e6abb995ab`. Erfolg schreibt exakt `minimal package ü\n`; Fehler meldet Code 10 mit `selected-package-business-failure`; beide schreiben `cleaned`. Gelöschte Quellkopie, fremdes CWD, leeres Maven-HOME und PATH `/gretl-no-build-tools` sind in den jeweiligen Isolationsdaten bestätigt. Metadaten, Inventar, Einstiegsklasse und Provider wurden direkt aus beiden JARs geprüft.

Konkrete Szenariopfade und Assertions stehen in E/`own-evidence-result.json`. E enthält eigene Logs, alle 112 aktiven XML, Qualification-/Auditdaten, ausgewählte tatsächliche JARs, redigierte Logs/Reports und fachliche Outputs sowie externe synthetische Proben. Keine Rohkonfigurationen, Secretdateien oder pauschalen privaten Workspaces wurden kopiert. E/`SHA256SUMS.json` bindet **984 Dateien**, SHA-256 **`d26bbf55a142e4b9177245aaad53e877d2e127c235464911e1596572d4cc83d8`**.

## Findings und nicht qualifizierte Grenzen

Es bestehen im geprüften Phase-1-Umfang **keine offenen CRITICAL-, MAJOR- oder MINOR-Findings**. Die historischen M1–M3 bleiben in ihren ursprünglichen Reviewakten unverändert erhalten und werden ausschliesslich für diesen neuen Kandidaten als behoben bewertet.

B hat `./mvnw -B -Pgeotools-probe,backing-services clean verify` in Phase 1 nicht ausgeführt; dies ist erst Gate 3 des neuen Features. Historische Full-Nachweise sind keine aktuelle eigene Ausführung. Kein realer IMAGE-, Linux-/Windows-, Native-, leerer Authoringcache- oder Produktionsnachweis. Pfadchecks sind keine allgemeine Dateisystemsandbox; ausführbarer Jobcode bleibt vertrauenswürdiger Code.

PRJ-101–115 und PRJ-201–212 sowie gemeinsamer Projektgraph/-Run, Cross-Project-Resultate/-Parameter/-Cleanup/-file, Directory-CLI, Projektquellhash und Projekt-JAR sind ausdrücklich spätere Phasen und hier nicht implementiert oder als fertig bewertet. Phase 2 wurde nicht begonnen und bleibt auch nach diesem Review ausserhalb des aktuellen Auftrags.

## Verdict

**PASS**

Alle sieben Phase-1-Orakel, die harte Settings-/Symlinkgrenze und die alten Regressionen bestehen auf der festen Kandidaten-SHA unabhängig. Das vollständige eigene Standardgate und der exakte Audit sind erfolgreich. Die zusätzlichen direkten AST-/Literal-/Quellenpositionsproben bestätigen M1–M3 und geklammerte Konstanten als behoben; keine wesentliche offene Abweichung wurde gefunden. Diese Freigabe gilt ausschliesslich für `1935a95f73c45028a04316150e165ec0a61a52a5`. Gemäss §9 wird Gate 1 erst durch As unveränderte, separate Archivierung dieses Berichts wirksam; ein reiner Reviewarchivierungscommit ersetzt die geprüfte SHA nicht. Jede nachfolgende Produkt-/Test-/Fixture-/Normänderung verlangt einen neuen Kandidaten und eine neue unabhängige Prüfung.
