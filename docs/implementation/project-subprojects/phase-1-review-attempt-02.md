# Phase 1 – Independent Review, Korrekturversuch 02

- Phase base SHA: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10`.
- Reviewed candidate SHA: `b88ad519be7a9417ba39749f6b3fae6d729f1e29`.
- Reviewer/Model/Session: unabhängiger Codex-Review-Agent B, Sitzung `/root/independent_phase1_attempt02`. Laut Sitzungsanweisung GPT-6-basiert; eine genauere konfigurierte Modellkennung wird nicht offengelegt und wird nicht geraten. B hat die Implementierung und As Phasenberichte nicht geschrieben.
- Reviewzeitpunkt: 2026-10-28, eigene Ausführung UTC 20:03:18 bis nach 20:10:31; Abschlusszeit in E/`final-identity.json`.
- Eigener separater detached Worktree: `/Users/stefan/sources/gretl-runtime-phase-1-review-2`, vor und nach der Prüfung `git rev-parse HEAD` exakt auf obiger Kandidaten-SHA; `git symbolic-ref -q HEAD` jeweils Exit 1. Getrackter Arbeitsbaum sauber.
- Vollständiger geprüfter Diff: `5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10..b88ad519be7a9417ba39749f6b3fae6d729f1e29`, alle **22 Dateien, 1800 hinzugefügte Zeilen**, einschliesslich Erstimplementierung, Reviewarchivierung und beider Korrekturcommits. Kein verkürzter HEAD~1-Vergleich.
- Eigene externe Evidenzwurzel E: `/Users/stefan/sources/gretl-runtime-phase-1-evidence-20261028/attempt-02/independent-review-evidence/`. Dieser Bericht liegt ausserhalb des Worktrees und ausserhalb von E.

## Identität und vollständige Reviewgrundlage

Beide Commitobjekte existieren (`git cat-file -e <SHA>^{commit}`, je Exit 0); `git merge-base --is-ancestor <phaseBaseSha> <candidateSha>` endet mit 0. B erzeugte den eigenen Checkout mit `git worktree add --detach /Users/stefan/sources/gretl-runtime-phase-1-review-2 b88ad519be7a9417ba39749f6b3fae6d729f1e29` (Exit 0). Alle anschliessenden Shellaufrufe haben diesen Worktree ausdrücklich als workdir; der gemeinsame Default-CWD wurde nicht verändert. B hat keine getrackten Produkt-, Test-, Fixture- oder Spezifikationsdateien geändert und keinen Implementierungscommit erstellt.

Gelesen und abgeglichen wurden Root-/Spec-AGENTS, die vollständige Subprojekt-Spezifikation 1.1 insbesondere §§3, 4.1, 7–9 und 12, ihre Runtime-/DSL-/Paketierungs-/API-/Kernklassen-/Loggingverträge 03/04/12/14/15/16 sowie `module-boundaries.md`, `testing.md` und `job-artifact-tests.md`. Dazu kamen Phasenbericht, Evidence-Index, Handoff und die erste unabhängige Reviewakte. Die dokumentierte additive `defaultTargetSources`-Map ist gegen den vorgeschlagenen Recordvertrag geprüft.

Der gesamte Diff ist zusätzlich in E/`full-phase.diff` gesichert, SHA-256 `54371b47cce7a6d5d47cb5e3a8a7b86b064aa10f1b1ee82ef358ebb2bfccbdad`. Die vollständige Dateiliste und Commitfolge stehen in E/`identity.json`. Selbst geprüft wurden alle sechs neuen Produktklassen, alle drei neuen Testklassen, beide i18n-Ergänzungen, README-Verweise, Formatdokumentation und sämtliche Beispieldateien. Der Evidence-Index wurde strukturell vollständig ausgewertet, einschliesslich Befehlen, Quellen-/Testzuordnung, Archivpfaden, historischen Fehlversuchen und allen ausdrücklich späteren IDs.

Die Phasenbasis enthält nur die separate Archivierung der vorigen akzeptierten Phase-0-Review: Der Diff von `1ff17246caff404f75303dcc31d2f9814e4bc2aa` zur Phasenbasis betrifft ausschliesslich `phase-0-review.md`. Die erste Phase-1-Reviewakte ist unverändert erhalten, SHA-256 `6d7579f24dc3c510cb029c88328f683777421b8f47e4148ba87f3b5383f6c091`. Ihre Freigabe wurde nicht auf diesen Kandidaten übertragen.

## Eigene Pflichtausführung und zusätzliche Proben

Eigene Umgebung: Eclipse Temurin OpenJDK `25.0.2+10-LTS`, Maven `3.10.0` über den unveränderten Wrapper, echtes Groovy `6.0.0`, UTF-8, macOS `27.0.1` aarch64, Locale en_US, Zeitzone Europe/Zurich. Versionsausgaben, SHA-/Detached-/Statusprüfungen, CWD und Exitcodes stehen in E/`identity.json` und `final-identity.json`.

| Eigener Befehl | Exit | Beleg |
| --- | ---: | --- |
| `./mvnw -B clean verify` | 0 | E/`standard.log`, `standard-result.json`, `reports/`; UTC 2026-10-28T20:03:18.869607+00:00 bis 20:10:23.159134+00:00 |
| `python3 scripts/audit-job-evidence.py --source b88ad519be7a9417ba39749f6b3fae6d729f1e29` | 0 | E/`audit.log`, `audit-result.json`, `summary.json`, `jar-executions.json`; UTC 20:10:28.537626 bis 20:10:31.708633 |
| `git diff --check 5bc9d367fb3ea3f1e8643c43d0894e8d6eb1bd10 b88ad519be7a9417ba39749f6b3fae6d729f1e29` | 0 | E/`identity.json`; keine Whitespacefehler |
| `javac -cp <echter Surefire-Classpath> E/IndependentReviewProbe.java`; `java -cp E:<Classpath> IndependentReviewProbe E/probe-cases-final` | 0 / 0 | E/`independent-probes.json`; **154 eigene Assertions**, einschliesslich strikter M1-Gegenprobe |
| `javac ... E/UnicodeProbe.java`; `java ... UnicodeProbe E` | 0 / 1 | E/`unicode-probe.json`; gültige Settings mit Nicht-BMP-Kommentar werden abgewiesen |
| `javac ... E/UnicodeAstProbe.java`; `java ... UnicodeAstProbe` | 0 / 0 | E/`unicode-ast-probe.json`; direkte Groovy-AST-/Codepoint-/UTF-16-Ursachenprobe |
| `javac ... E/LiteralProbe.java`; `java ... LiteralProbe E` | 0 / 1 | E/`literal-probe.json`; sechs zulässige konstante Literalvarianten werden unerwartet abgewiesen |
| `python3 E/verify-handoff.py` | 0 | E/`handoff-verification.json`; sämtliche referenzierten A-Hashes und beide Archivmanifeste unabhängig neu berechnet |
| Eigener Inputindexvergleich und `python3 E/archive-own-evidence.py` | 0 / 0 | E/`input-identity.json`, `evidence-result.json`; 841 Inputs bytegleich, eigene JUnit-/JAR-/Outputprüfung und selektive Archivierung |

Die vollständigen tatsächlichen Java-argv und Classpaths, stdout/stderr, UTC und CWD sind in den genannten JSON-Dateien erhalten. Die Quellen aller externen Proben liegen in E; sie ändern keine Repositorytests. Ein erster eigener Probelauf hatte eine zu enge Hilfsassertion, wonach auch ein dangling Root-Job-Fehler die Settingsdatei statt der tatsächlich betroffenen `job.groovy` nennen müsse. Dieser eigene Probeentwurfsfehler ist unverändert in `probe-authoring-file-assumption.json` dokumentiert. Danach wurden die fachlichen Assertions ohne Änderung am Produkt vollständig ausgeführt. Die neuen Literalfehler wurden dagegen strikt als Fehler belassen.

Der eigene Standardreactor lief ohne Skip-Flags und ohne selektive Testauswahl. Die **112 aktiven JUnit-XML** enthalten **622 Tests, 0 Failures, 0 Errors, 0 Skips**: 604 bestehende und 18 neue Fälle. Gezählt wurden ausschliesslich die aktiven Root-POM-Module; inaktive GeoTools-Reports wurden nicht hinzugezählt.

| Modul | Tests | Failures / Errors / Skips |
| --- | ---: | --- |
| gretl-api | 17 | 0 / 0 / 0 |
| gretl-runner | 185 | 0 / 0 / 0 |
| gretl-groovy | 67 | 0 / 0 / 0 |
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

## Fremde Handoff-Evidenz

As Ausführungen werden ausdrücklich nicht als Bs eigene Tests ausgegeben. Der selbst neu berechnete Handoffhash ist `331bab33e88e4f81f4078b50d79acb0704da04712330493c30a79ebe52aeaa65`; die separate `.sha256` stimmt. Alle referenzierten Dateien stimmen ebenfalls.

Beide aktuellen A-Archive wurden vollständig neu gehasht: je **3366 Dateien**, keine Abweichung; je **112 aktive XML, 622/0/0/0**, Standardlauf und Audit Exit 0. `before-standard/SHA256SUMS.json` hat SHA-256 `b02a64fd15111cf9111803a163581c95391a096e53eb1aedd708b2f2fc9ac247`, `after-standard/SHA256SUMS.json` hat `6c6bf700047b21e925554ec622e72ecb48786a43cd10535a5ea739a2c4a51701`. Der Vorlauf ist korrekt dirty auf `4c4d8ad16bbf9f64fae4ca4a55a6bf0781b97d15`, der Nachlauf sauber auf der exakten aktuellen Kandidaten-SHA. Alle **841** im kurz nach Vorlaufbeginn gesicherten Implementierungsinputs stimmen mit Bs Kandidatencheckout überein.

Der kontrolliert abgebrochene Nachlauf auf `d9831a8dad1630ada6e76cb76f1d74c7372c5707` ist mit Exit 143 als historischer, durch Dokumentpräzisierung ersetzter Lauf aufgeführt und wird nicht als erfolgreiches Gate gewertet. Der abschliessende Nachlauf auf `b88ad519...` ist separat vollständig. Die Zeitpräzisierung im letzten Dokumentcommit betrifft keinen Produkt-/Testcode.

## Pflicht-Testfallmatrix

| ID | Ergebnis | Selbst geprüftes Orakel |
| --- | --- | --- |
| PRJ-001 | PASS | Root zuerst, danach kanonische explizite Reihenfolge; keine impliziten Eltern-/unselektierten Jobs; wiederholte stabile Ergebnisse, immutable Sammlungen und Defaultpositionen. Eigene Probe ergänzt `z`, `a:b` in bewusst umgekehrter Verzeichnisreihenfolge. |
| PRJ-002 | PASS | Zwei Includes ohne Root, verschachtelte `b/c/job.groovy`, kein erforderlicher Elternjob; untergeordnete Settings werden nicht evaluiert. |
| PRJ-003 | PASS | Leere/ungültige Includes, kanonische Duplikate, wiederholte Direktiven und fehlende Jobs liefern konkrete Codes und Originalpositionen; eigene mehrzeilige Duplikatprobe bestätigt beide Quellen. |
| PRJ-004 | PASS | Settings mit Dateiaktion, println/System.exit, Variablen, GStrings, Imports, Klassen/Traits/Enums, Methoden, synchronisiertem Code, assert, Annotationen/ASTTest(CONVERSION) werden ohne Marker abgewiesen. M1 wird mit exakter Originalzeile/-spalte reproduziert und ist behoben. |
| PRJ-005 | PASS | `:a:b` und `:a_b` bleiben verschieden; volle SHA-256, deterministische gültige Namen; echte Groovy-6-Classgenerierung. Eigene Probe instanziiert drei harmlose, verschieden benannte Scripts und prüft ihre unterschiedlichen Rückgabewerte. |
| PRJ-006 | PASS | Relative Roots, reguläre Dateien, Root-Escape, direkte/intermediäre/interne/externe/dangling Symlinks und striktes UTF-8. Eigene Probe prüft exakt 262144 UTF-8-Bytes als gültig, 262145 als Fehler sowie UTF-8-Surrogatbytes als Fehler. |
| PRJ-007 | FAIL | Die vorhandenen engen Fälle zu qualifizierter Defaultsyntax, erhaltener Defaultposition und nicht ausgeführtem Jobcode bestehen. Zusätzliche zulässige qualifizierte Defaults mit Nicht-BMP-Kommentar bzw. escaped Zeilenfortsetzung werden jedoch fälschlich verworfen (M2). |

Die ergänzende gültige Include-Schreibweise mit escaped Zeilenfortsetzung bzw. Nicht-BMP-Kommentar scheitert ebenfalls an M2. Die konkreten negativen Include-/Traversalorakel aus PRJ-003/006 bleiben erfolgreich. Diese Unterscheidung darf den allgemeinen verbindlichen Sprachvertrag aus §3.3 nicht verdecken.

## Architektur, Kompatibilität und Provenienz

Sechs kleine neue Klassen liegen im vorhandenen `gretl-groovy`-Modul; hinzu kommen additive i18n-Meldungen. Keine POM-, Dependency-, Plugin-, Profil-, Wrapper- oder CI-Änderung, kein neues Produktmodul, kein Modulzyklus und keine neue globale Registry. Die bestehenden Groovy-Produktklassen/-tests sowie API, Runner, CLI, JobRuntime, Packager, Logging und Taskmodule bleiben bytegleich. Bestehende Tests und fachliche Assertions wurden weder entfernt noch abgeschwächt. Maven-/Enforcergrenzen laufen im eigenen Reactor erfolgreich.

`ProjectPath` ist immutable und IO-frei, zentralisiert die Segmentvalidierung und trennt logische IDs von Dateisystempfaden. Records normalisieren absolute Pfade und kopieren Sammlungen defensiv. Die additive immutable Map `defaultTargetSources` mit erhaltenem Vierargument-Konstruktor bewahrt die geforderte spätere Defaultdiagnostik und ist dokumentiert begründet.

Settings werden begrenzt gelesen und streng als UTF-8 dekodiert. Symlinks am Root und in den Settings-/Include-Ketten werden vor dem Laden abgewiesen. Der Parser verwendet ausschliesslich `SourceUnit.parse()`/`convert()`, keine semantische Analyse, ausführbare Bytecodeerzeugung, Instanziierung oder Settings-/Jobausführung. Seine kleine AST-Allowlist wird durch den M1-Lexerfix nicht erweitert. Der Lexer liefert erst nach dem bereits erkannten verbotenen Modulimport die Diagnoseposition. Die allgemeine Settings-Sicherheitsgrenze ist durch Codeprüfung und eigene Markerproben bestätigt; M2 ist eine fehlerhafte zusätzliche Ablehnung gültiger Literale, kein nachgewiesener Ausführungsbypass.

Das Beispiel ist klar als zwei bestehende Einzeljobs dokumentiert. Die Suite prüft es mit echtem Parser/Loader/Runner und unterschiedlichen lokalen UTF-8-Inhalten. Keine Directory-CLI, kein ProjectTaskIndex, kein geteilter Builder, keine Projektquelle, kein Schema 3 und kein Projekt-JAR wurden vorgezogen.

Die unveränderten CLI-, Groovy-, Runner-, Packager-, Distributions- und lokalen JAR-Prozessregressionen liefen vollständig. Selbst gelesene `IoLocalJobIT`, `SelectedPackageLocalJobIT` und `SelectedPackageSupport` prüfen echte Bytes, XTF-/Datenbank-/XLSX-Inhalte, fachliche Fehler und isolierte Starts ausserhalb des Jobprozesses. Der eigene Audit prüft den sauberen Distributionskatalog auf exakt `b88ad519...`, alle Distributionsprüfsummen und jeden tatsächlich gestarteten JAR-Hash. Er bestätigt **82 echte JAR- und 14 Script-Ausführungen, keinen IMAGE-Lauf**. Standardgate und JaCoCo werden nicht als vollständige Dienstqualifikation oder Coverage nicht instrumentierter Produktprozesse ausgegeben.

Eigene zusätzlich geöffnete Artefaktstichproben:

- IO-Extract-JAR SHA-256 `f3072e8d759bc4c83f1f4d1e3d04f8f93e02a158184dfe8316adfa86e9183b74`: interne Jobmetadaten Schema 1, Dependencyinventar und tatsächliche Einstiegsklasse; entpackte `nested/payload.txt` bytegleich zur kanonischen Eingabe.
- Minimaler Erfolgs-/Fehlerjob: beide Starts verwenden unverändert dasselbe JAR SHA-256 `93b962b5a63ffee506b9f202310ebc2ba7b6460b7fd257c31078c80c77cd283d`. Erfolg schreibt exakt `minimal package ü\n`; Fehler liefert Code 10 und `selected-package-business-failure`; beide schreiben `cleaned`. Isolationsdaten bestätigen gelöschte Quellkopie, fremdes CWD, leeres Maven-HOME und PATH `/gretl-no-build-tools`. Metadaten/Inventar/Einstiegsklasse wurden direkt aus den JARs geprüft.

Konkrete Verzeichnisse, Hashes, Ausgaben und Assertions stehen in E/`evidence-result.json`. E enthält eigene Logs, alle 112 XML, Qualification-/Auditdaten, ausgewählte echte JARs und Outputs sowie redigierte Reports/Ereignisdateien. Rohkonfigurationen, Secretdateien und pauschale Workspaces wurden nicht kopiert. Das eigene Manifest E/`SHA256SUMS.json` bindet **1183 Dateien**, SHA-256 `bbd8b0c3931f64c2ea3d4b2bf1ce898f2c7ab4879227d10b6694f35874521e55`.

## Offenes Finding

**MAJOR – M2: Die zusätzliche Literalprüfung verwirft zulässige konstante Settingsliterale.**

- Datei: `gretl-groovy/src/main/java/ch/so/agi/gretl/runtime/groovy/project/GretlProjectParser.java:249` und `:251`, aufgerufen bei `:152–153`.
- Reproduktion A: Reguläre Root-`job.groovy` und gültige UTF-8-Settings mit genau `/* 😀 */ defaults ':run'` anlegen und `new GretlProjectParser().parse(root)` aufrufen. `:run` ist syntaktisch qualifiziert; seine Taskexistenz wird in Phase 1 bewusst noch nicht verlangt.
- Beobachtung A: `PROJECT_SETTINGS_UNSUPPORTED` bei `settings.gretl:1:18`. Die entsprechende BMP-Variante `/* ä */ defaults ':run'` wird akzeptiert. Der echte Groovy-6-AST enthält in beiden Fällen dieselbe zulässige `ConstantExpression(String)` mit Spalte 18. Nach dem Nicht-BMP-Zeichen ist Java-UTF-16-Index 17 jedoch ein Leerzeichen; der Codepointoffset 17 ist das öffnende Quote. `substring(column)` verwendet die falsche Indexeinheit und lehnt dadurch den gültigen Inhalt ab. Dasselbe gilt für `include` nach einem solchen Kommentar.
- Reproduktion B (die Backslashes am Zeilenende sind tatsächliche Quellzeichen): `ab/job.groovy` bereitstellen und folgende gültigen Settings parsen:

```groovy
include 'a\
b'
defaults ':ab:\
run'
```

- Beobachtung B: Groovy 6 liefert `ConstantExpression(String)` mit Wert `ab` bzw. `:ab:run`; die Originalposition reicht über zwei Zeilen. Der `lastLine != firstLine`-Check bei Zeile 249 verwirft das Literal trotzdem. Entsprechende doppelt zitierte Includes und CRLF sind ebenfalls reproduziert. Es gibt weder GString noch berechneten Ausdruck oder ausführbaren Code.
- Unabhängige Belege: E/`UnicodeProbe.java`, `unicode-probe.json` (strikter Lauf Exit 1), `UnicodeAstProbe.java`, `unicode-ast-probe.json` (direkte AST-/Indexursache, Exit 0) sowie `LiteralProbe.java`, `literal-probe.json` (sechs gültige Varianten, sechs unerwartete Ablehnungen, Exit 1). Kleine echte UTF-8-Dateibäume bleiben unter E/`unicode-*` und `literal-*` erhalten.
- Vertrag: §3.3 erlaubt Kommentare, UTF-8 und konstante einfach/doppelt zitierte Stringargumente. §4.1 verlangt AST-Allowlistinterpretation und korrekte Defaultsyntax. Die Norm schränkt Kommentare nicht auf BMP ein und verbietet keine escaped Zeilenfortsetzung innerhalb dieser beiden Quoteformen. Beide Reproduktionen erfüllen den festgelegten AST-/String-/Namensvertrag. Die Zusatzprüfung verändert daher die erlaubte Sprache.
- Schwere: Pflichtfunktion akzeptiert gültige Konfiguration nicht; dies ist keine rein kosmetische Diagnosefrage. Die Standardtests decken diese Varianten nicht ab. Es gibt keinen behaupteten Sicherheitsbypass und keine festgestellte Ausführung der Settings.
- Erforderliche Korrektur durch A: Literalquellposition in derselben Indexeinheit wie Groovy zuordnen und die unbegründete Einzeiligkeitsrestriktion beseitigen. Strukturelle AST-Allowlist sowie Ablehnung von GStrings, Ausdrücken, Slashy-/Triplequotes unverändert erhalten. Regressionen mit echten UTF-8-Dateien für Include und Defaults, Nicht-BMP-Kommentare, einfache/doppelte Quotes und LF/CRLF-Zeilenfortsetzungen hinzufügen; Originalpositionen und Nichtausführung weiter prüfen. Danach neuer Kandidat unter derselben Phasenbasis, vollständige Vor-/Nachtests und erneute unabhängige Review. B nimmt keinen Fix vor.

Das frühere M1 ist durch eigene genaue Positions-/Markerassertions behoben. Weitere offene CRITICAL-/MAJOR-Befunde wurden im beschriebenen Umfang nicht gefunden.

## Nicht unabhängig qualifizierte Grenzen

`./mvnw -B -Pgeotools-probe,backing-services clean verify` wurde von B in Phase 1 nicht erneut ausgeführt; dieses zusätzliche Gate ist erst in Phase 3 Pflicht. Historische Phase-0-Full-Nachweise werden nicht als aktuelle Ausführung übernommen. Kein tatsächlicher IMAGE-, Linux-/Windows-, Native-, leerer Authoringcache- oder Produktionsnachweis.

PRJ-101–115 und PRJ-201–212 sowie gemeinsamer Taskgraph/Run, Cross-Project-Resultate/-Parameter/-Cleanup/-file, Directory-CLI, Projektquellhash und Projekt-JAR bleiben spätere Phasen. Sie werden nicht als aktuell fertig oder als fehlende Phase-1-Pflicht bewertet. Die Pfadprüfung ist keine allgemeine Dateisystemsandbox und keine atomare Transaktion gegen parallel ausgetauschte Verzeichnisstrukturen.

## Verdict

**FAIL**

Der eigene vollständige Standardreactor, der exakte Audit, harte Nebenwirkungs-/Symlink-Gegenproben und der M1-Retest sind erfolgreich. Das eigenständig reproduzierte MAJOR-Finding M2 verletzt trotzdem den verbindlichen Phase-1-Sprachvertrag. Für diese Kandidaten-SHA ist Gate 1 daher nicht freigegeben. Es ist ausschliesslich Phase 1 zu korrigieren und neu zu prüfen; Phase 2 bleibt ausserhalb des Auftrags.
