# GRETL Runtime – Spezifikation zur Bereinigung technischer und dokumentarischer Altlasten

**Version:** 1.2 (OpenCode Desktop 2.2.5 / V2-CLI; Python 3.9+; vorhandene Provider-Anmeldung)  
**Stand:** 31. Oktober 2026  
**Repository:** <https://github.com/edigonzales/gretl-runtime>  
**Analysierter Ausgangsstand:** `main`, Commit `37c6f115980ecabc9a5931afa4bffc9b353f51f2` (29.10.2026)  
**Adressaten:** Implementierungsagent **Codex** (Agent A), unabhängiger Prüfagent **OpenCode + DeepSeek-V4.1-Flash-1017 via DeepSeek API** (Agent B), Maintainer  
**Status:** Verbindlicher Umsetzungsauftrag; **keine** Behauptung, dass die hier beschriebenen Bereinigungen bereits umgesetzt oder geprüft seien.  
**Priorität:** Diese auf den Ist-Zustand bezogene Bereinigungsspezifikation ersetzt für diesen Auftrag historische Implementierungsanweisungen aus `docs/spec/AGENTS.md` und abgeschlossenen Phasenplänen. Sie ersetzt **nicht** die funktionalen Verträge der laufenden Anwendung.

> **Kernregel:** Eine Phase ist erst `PASS`, wenn **(1)** Codex sämtliche vorgesehenen Änderungen und vollständigen eigenen Testgates erfolgreich abgeschlossen hat, **(2)** ein **neuer, unabhängiger OpenCode/DeepSeek-Review** den exakten Implementierungscommit anhand von Code, Diff, Dokumentation, echten Testläufen und den *inhaltlichen Phasenzielen* selbst geprüft und mit `PASS` bewertet hat und **(3)** das Review-Gate die Belege technisch validiert hat. Codex startet diesen Review **selbständig**. Ein bloss grüner Maven-Build, eine eigene Codex-Behauptung, ein Review auf dem falschen Commit oder ein nicht auswertbarer OpenCode-Lauf ist **kein** Abschluss.

---

### Änderungen gegenüber Version 1.1 (Korrektur des Umgebungs-/Reviewvertrags)

- **OpenCode Desktop 2.2.5** ist die vorhandene, erfolgreich verwendete GUI. Eine im Terminal gefundene CLI **1.18.22** ist eine **andere (V1-)Installation**, kein Nachweis für einen veralteten Desktop-Client. Der automatische Review verwendet eine geprüfte **OpenCode-V2-CLI** und versucht zuerst, die vorhandene V2-Anmeldung wiederzuverwenden.
- **Kein zwingendes `DEEPSEEK_API_KEY` in der Shell.** OpenCode V2 verwaltet bereits verbundene Provider-Konten im lokalen Server/SQLite-Speicher. Direkter DeepSeek-Zugang, gewähltes Modell und tatsächlicher erfolgreicher API-Aufruf werden überprüft, aber kein Schlüssel aus dem GUI-Profil extrahiert, exportiert oder dupliziert.
- **Python 3.9+ statt 3.12+.** Der Orchestrator verwendet nur die Standardbibliothek und muss insbesondere mit macOS-Python 3.9.6 laufen; `venv` oder ein neuer Interpreter sind keine Pflicht. Ein `venv` hebt die Interpreter-Version nicht an.
- **OpenCode-V2-Rechte** werden mit `permissions`/`shell`/`subagent` (nicht V1 `permission`/`bash`/`task`) definiert. `cwd` bestimmt den Review-Worktree; Flags werden gegen die V2-CLI geprüft, `--dir` wird nicht vorausgesetzt.
- **Keine unnötige Sperre vor P0:** Codex darf P0-Inventar, Gate-Implementierung und isolierte Tests fertigstellen, bevor die echte DeepSeek-Anmeldung überprüft wird. **Spätestens vor dem ersten echten unabhängigen P0-Review muss die Verbindung funktionieren.** Ohne echten Review-PASS keine P1.

---

## 1. Zielbild und Nichtziele

Das Repository soll den **heutigen** Stand von GRETL Runtime konsistent beschreiben. Historische Spezifikationen, ausgeführte Agentenprompts, überholte Statusmeldungen, alte Meilenstein-/Phasennamen, doppelt gepflegte Beispiele und falsche Webseiteninhalte sollen beseitigt oder eindeutig von der aktiven Dokumentation getrennt werden. Die Entwicklungsgeschichte bleibt durch Git und – **nur wo fachlich sinnvoll** – wenige ausdrücklich historische Abschlussnachweise nachvollziehbar. Neue Entwickler und LLM-Coding-Agenten dürfen die alten Aufträge nicht versehentlich als aktuellen Plan behandeln.

**Erfolg bedeutet:** weniger aktive Dokumente, klarer Einstieg, nachvollziehbarer aktueller Funktionsumfang, technisch korrekte Website, fachliche Namen in Testklassen und Beispieldaten, keine versteckten Verweise auf gelöschte Dateien, unveränderte Runtime-Funktionalität und ein reproduzierbares, automatisiertes unabhängiges Reviewverfahren.

**Nicht Teil dieses Auftrags:** Rewrite der Runtime, Umstellung der DSL, Änderung öffentlicher Operation-IDs, Anpassung der Java-SPI aus fachfremden Gründen, Wechsel der Maven-/Groovy-/JDK-Version, Einführung neuer Tasks, Implementierung von Publisher/XtfToGpkg/Native Image/Control Plane, pauschale Entfernung älterer Paketdeskriptor-Schemas, neue Testinfrastruktur ohne Zusammenhang zur Bereinigung. Keine vermeintliche „Vereinfachung“ darf Sicherheit, Logging, Cancellation, Auslieferung oder die fachlichen Integrationsnachweise verschlechtern.

### 1.1 Harte Invarianten (`INV`)

| ID | Unveränderliche Anforderung |
|---|---|
| INV-01 | **44 vorhandene Operationen**, DSL-Symbole, Ein-/Ausgabefelder, öffentliche Java-SPI, CLI-Befehle und Exitcode-Verträge bleiben erhalten. |
| INV-02 | Paketdeskriptor-Schemas **1, 2 und 3**, ältere Single-Job-JARs und aktuelle Subprojekt-JARs bleiben funktionsfähig; vorhandene Kompatibilitätstests bleiben aktiv. |
| INV-03 | Produktive Modulgrenzen, Maven-Koordinaten und die Standarddistribution werden nicht umstrukturiert, soweit für diese Bereinigung nicht zwingend notwendig. |
| INV-04 | Kein bestehender relevanter Unit-, Komponenten-, JAR-Job-, Backing-Service-, GeoTools- oder Sicherheitsregressionstest wird ersatzlos entfernt; Assertions werden nicht abgeschwächt. **Umbenennen** ist zulässig, Entfernen nur nach bewiesener inhaltlich identischer Abdeckung. |
| INV-05 | `docs/reference/reference.adoc`, `docs/reference/generated/` mit 44 Tasktabellen, Thoth/Biblios-Generierung, Distributions-README und Maven-/CI-Verträge bleiben funktionsfähig und inhaltlich aktuell. |
| INV-06 | Kein realer Produktionsjob, kein Remote-Repository, kein Snapshot-Repository und keine produktive Infrastruktur wird durch den Auftrag verändert. **Kein automatischer Push, Merge oder Release.** |
| INV-07 | Historiendokumente werden nicht heimlich als aktuelle Produktverträge umgedeutet. Nicht umgesetzte Vorhaben bleiben explizit **Backlog / Zukunft**, nie als fertig behauptet. |
| INV-08 | Jeder Abschluss ist an `phaseBaseSha` und `candidateSha` gebunden. Jede Änderung am geprüften Produkt- oder Dokumentationsstand **nach dem Review** verlangt einen erneuten unabhängigen Review; allein der eng begrenzte, unverändert aus dem Review abgeleitete Nachweiscommit ist ausgenommen (vgl. 4.7). |

---

## 2. Ist-Befunde und verpflichtendes Ausgangsinventar

Die folgenden Fundstellen stammen vom oben angegebenen Commit. **Vor der Implementierung** prüft Agent A den Checkout frisch und hält Abweichungen ausdrücklich fest. Kein blindes Löschen aufgrund dieser Liste: erst aktive Referenzen, Tests, Buildskripte und Veröffentlichungspfade prüfen.

| ID | Fundstelle / bestehender Befund | Erwartete Massnahme |
|---|---|---|
| DOC-01 | `AGENTS.md` und `docs/spec/AGENTS.md` erklären Spezifikation 4.1 bzw. einen nicht mehr aktuellen Implementierungsauftrag zur massgeblichen Grundlage. | Aktuelle Anweisungen neu formulieren; keine Pflichtlektüre abgeschlossener Phasen. |
| DOC-02 | `docs/spec/README.md` behauptet weiterhin „die neue Runtime ist noch nicht gebaut“. | Aus aktivem Dokumentationspfad entfernen oder strikt historisch kennzeichnen; nicht als Ist-Aussage belassen. |
| DOC-03 | `docs/implementation/build.md` enthält historisch **416 Tests**; neuester Projekt-Review nennt **672/728** aktive Tests (nur an seinem Commit). | Keine dauerhaften Testzahlen im aktuellen Build-Handbuch; auf reale CI-/Surefire-Reports und SHA verweisen. |
| DOC-04 | `docs/implementation/project-subprojects/README.md` erzählt Phase-1/2/3-Implementierung als laufendes Projekt. | In aktuelle Anleitung für `settings.gretl`, Projektlauf und Schema-3-JAR umarbeiten. |
| DOC-05 | `examples/project-subprojects/README.md`: „Beispiel für Phase 1“, gemeinsamer Projektlauf erst in Phase 2. | Auf aktuelle Funktion korrigieren oder als redundantes Beispiel entfernen, nach Referenzprüfung. |
| DOC-06 | `docs/implementation/improvement-plan/requirement-tests.md` enthält abgeschlossene Phasen-Sperrlogik als vermeintlich aktuelle Zukunft. | Historisieren oder löschen. |
| DOC-07 | `docs/implementation/hardening-f1-f4/gretl-runtime-startprompt-f1-f4-neu.md` enthält einen bereits erledigten Agenten-Startauftrag. | Aus aktiven Dokumenten entfernen; historische Anforderungen nur bei nachgewiesenem Nutzen bewahren. |
| DOC-08 | `docs/implementation/improvement-plan/` (46 Dateien, ca. 1,53 MB), `hardening-f1-f4/` (15 Dateien, ca. 216 KB), `project-subprojects/` (18 Dateien, ca. 540 KB). | Bewertetes Entfernen/Archivieren abgeschlossener Phasenartefakte; keine neue parallele Dokumentationswüste. |
| DOC-09 | `docs/spec/` enthält 19 alte Soll-Dokumente (ca. 623 KB), darunter wertvolle spätere Publisher-/Native-Entscheidungen. | Aktive Verträge extrahieren; echte Zukunftsentscheidungen in Backlog/ADRs; historische Soll-Spezifikation nicht normativ. |
| WEB-01 | `website/features.qmd` hat `Lorem ipsum`, nennt Gradle als Pipeline-Engine und beschreibt nicht vorhandene Control-Plane/Worker. | Korrekte, konkrete Funktionsdarstellung. |
| WEB-02 | `website/getting-started.qmd` enthält `plugins { ... }`, `tasks.register(...)`, `./gradlew executeSql` aus dem alten GRETL. | Gegen einen **ausführbaren** GRETL-Runtime-Quickstart ersetzen. |
| WEB-03 | `website/docs.qmd` hat Platzhalter und erfundene Versionsnavigation; `website/index.qmd` bewirbt eine nicht gelieferte Control Plane. | Korrigieren; nur reale Dokumentationsziele verlinken. |
| WEB-04 | `website/_quarto.yml` und `website/README.md` verwenden „GRETL next“; Name/Domain `next.gretl.app` sind separat zu bewerten, **keine automatische Umbenennung der Domain**. | Sachlich korrekte Produktbezeichnung ohne unnötige Branding-Änderung. |
| NAME-01 | `gretl-integration-tests/.../HardeningLocalJobIT.java`, `jobs/hardening/*`, Descriptor `"name": "hardening"`. | Dauerhafte fachliche Bezeichnungen, ohne Testverlust. |
| NAME-02 | `gretl-dist/src/main/dist/examples/ili-validator/models/GretlM0Fixture.ili`; XTFs und `DistributionIT` referenzieren `GretlM0Fixture`. | Modell-/Datei-/Referenznamen **atomar** neutralisieren. |
| NAME-03 | `gretl-geotools-probe`: `m0-points.*`, `gretl_m0_points`, POM-Text „Isolated M0/B test profile“. | Neutrale Kompatibilitätsbezeichnungen; **Probe selbst behalten**. |
| NAME-04 | `CsvReaderProbeTest`, `FunctionProbe.ili`, weitere `*Probe*`-/`*Fixture*`-Namen. | Nur umbenennen, wenn tatsächlich historisch/irreführend; fachlich richtige Probe-/Fixture-Bezeichnungen behalten. |
| DUP-01 | `examples/` und `gretl-dist/src/main/dist/examples/`: 16 gleichnamige Dateipaare, 14 byteidentisch; `download-extract/job.groovy` und `project-pipeline/README.md` weichen ab. | Gemeinsame Quelle oder **bewusst begründete** Trennung; Drift verhindern. |
| DOC-10 | `README.md` zeigt `./bin/gretl` ohne klarzustellen, dass dies aus dem **entpackten Distributionsroot** gilt (nicht aus dem Git-Checkout). | Ausführungskontexte korrekt ausweisen. |
| DOC-11 | `docs/implementation/m7-report.md`, `m0-...m7-report.md` usw. dokumentieren andere Commits und alte Testzahlen. | Als historische, SHA-gebundene Meilensteinberichte behandeln; keine aktuellen Produktstatusmeldungen daraus ableiten. |

**Nicht als Altlast interpretieren:** `PackageDescriptor`-Schemas 1/2/3; `ImageExecutionAdapter` als noch nicht produktiv getesteter, bewusst vorgesehener Testadapter; generierte Taskreferenz; das optionale GeoTools-Profil; `phase` als **fachlicher Fehler-/Ausführungsphasenbegriff** (z. B. `failurePhase`, `finished.phase()`) – kein Grund zum Umbenennen. Keine Pauschalsuche nach `phase` im gesamten Quelltext mit automatischem Ersatz.

### 2.1 Dokumentationsklassifikation

**Jede** Datei unter `docs/`, ferner `README.md`, `AGENTS.md`, `website/*`, Beispiel-READMEs sowie dokumentierende Dateien in der Distribution erhält **eine** begründete Klasse im maschinenlesbaren Inventar:

- `ACTIVE`: laufend gepflegte, tatsächlich gültige Anwender-, Entwickler-, Architektur-, Betriebs- oder Referenzdokumentation.
- `DECISION`: weiter gültige Designentscheidung bzw. konkret zukünftiger, klar abgegrenzter Backlog. Sie nennt *Entscheidung*, *Status* und *Abgrenzung*.
- `ARCHIVE`: wenigen Fällen vorbehalten: nachvollziehbare endgültige Abnahmen/Baselines/Evidenz, die weiterhin für Reproduzierbarkeit oder Migration benötigt werden. Erkennbar als historisch, mit gültigem Commitbezug. Kein aktiver Agentenauftrag.
- `DELETE`: abgeschlossene Prompts, redundante Iterationsberichte/FAIL-Versuche, veraltete Soll-Texte, entbehrliche Daten-/JSON-Duplikate und nicht mehr sinnvolle Platzhalter, soweit nicht aktiv referenziert oder rechtlich/vertraglich aufzubewahren.

Für `DELETE` und `ARCHIVE` muss Agent A **warum** dokumentieren; `git rm` allein ist kein Qualitätsnachweis. Git-Historie erhält den früheren Inhalt; es ist ausdrücklich **nicht** das Ziel, alle alten Dokumente innerhalb des HEAD erneut unter `docs/archive/` abzulegen. Ein kleines, kuratiertes Archiv ist besser als eine 1:1-Verschiebung des 3,4-MB-Bestandes.

---

## 3. Zielstruktur und Dokumentationsvertrag

Keine erzwungene Grossumstellung aller Pfade. **Zuerst** bestehende aktuelle Dokumente aktualisieren, **dann** die entbehrlichen Phasenartefakte entfernen. Wo eine fachliche Dokumentationsseite fehlt, neue kurze, eindeutig zuständige Seiten vorsehen, etwa:

```text
README.md                         # aktueller Einstieg und reale Beispiele
AGENTS.md                         # aktuelle Agent-Regeln, Tests, Review-Gates
website/                          # nur tatsächlich vorhandene Produktfeatures
examples/                         # kanonische ausführbare Beispiele
docs/
  architecture/                   # geltende Verträge: Module, DSL, Projekte, Paketierung
  development/                    # Build, Tests, Dokumentation, CI
  backlog/                        # noch nicht implementierte Zukunftsentscheidungen
  reference/                      # bestehende AsciiDoc-Referenz + generierte Tabellen
  history/                        # kurze historische Abschluss-/Migrationsnachweise
  maintenance/cleanup/            # diese Spezifikation + aktuelle Review-Metadaten
```

Dies ist eine **logische** Zuordnung, keine Anweisung, unnötig neue Verzeichnisse oder inhaltsgleiche Dateien anzulegen. Alternativ können sinnvolle `docs/implementation/*.md` unter ihrem heutigen Pfad verbleiben, wenn das gesamte Navigations- und Linkbild einfacher ist. **Eine kanonische Stelle je Information**; kein gleichzeitiges „aktuell“ in `docs/spec/`, `docs/implementation/` und `website/`.

### 3.1 Anforderungen an aktive Dokumente

- Produktname konsistent, Version und Funktionen anhand `pom.xml`, OperationRegistry, Beispielausführung, Code und CI nachgewiesen.
- Keine Behauptung „noch nicht gebaut“, „Phase 2 kommt später“, „Tests 416“ als aktuelle Aussage oder erfundene Control Plane.
- Keine stabil klingenden, aber undatierte Testzählungen. Historische Testzahlen nur mit SHA und Datum, klar als historisch ausgewiesen.
- `README.md` erklärt den Unterschied **Git-Checkout**, **entpackte Scriptdistribution**, **Job-JAR**; Pfade und Befehle sind tatsächlich ausführbar.
- `AGENTS.md` enthält nur Regeln für neue Arbeiten: Ist-Stand prüfen, kleinste Änderung, beide Maven-Gates, keine stillen Testskips, keine automatische Veröffentlichung, abgeschlossene Spezifikationen sind historisch. Keine Pflicht, 19 alte Soll-Kapitel vor jeder Änderung zu lesen.
- Wenn `docs/spec/AGENTS.md` archiviert wird: **nicht** unter demselben aktiven Dateinamen `AGENTS.md` als wirksame Coding-Agent-Anweisung liegen lassen; bei Bedarf `historical-agent-instructions.md` nennen und einen historischen Warnhinweis voranstellen.
- Dokumentationslinks (Markdown, AsciiDoc-Includes, Quarto, Startseiten, Buildskripte) müssen auf existierende Ziele zeigen; bei Verschiebungen internen Links/Ankern anpassen.
- Fehlende Funktionen als **nicht vorhanden / nicht umgesetzt** nennen. Publisher und XtfToGpkg gehören weiterhin zum möglichen späteren Arbeitsumfang, nicht als vorhandene Tasks.
- Die Webseite darf das bestehende Taskbeispiel nur verwenden, wenn dessen Felder der gegenwärtigen DSL entsprechen; keine Gradle-Plugin-Syntax oder `./gradlew` als Startweg für die Runtime.

### 3.2 Bewusst erhaltene Dokumentationswerte

Das Verfahren darf wertvolles Wissen nicht auslöschen: vor Entfernung von `docs/spec/` werden **noch relevante** öffentliche Java-API-/Runner-/Logging-/Security-Verträge gegen die tatsächliche Implementierung geprüft und – wo nicht bereits vorhanden – in einen aktuellen technischen Vertrag überführt. Publisher-Zielvertrag/Analyse und Native-Image-Strategie können als *zukünftige* Entwürfe in einem knappen Backlog bleiben. Entscheidungs-/Lizenzherkunft und finale Qualifikationsnachweise müssen auffindbar sein; verschobene Originale sind keine neuen normativen Anforderungen.

---

## 4. Verbindliches agentisches Verfahren (Codex A → OpenCode/DeepSeek B)

### 4.1 Rollen und Unabhängigkeit

**Agent A: Codex** führt Analyse, Inventar, Umsetzung, Tests, Korrektur und lokale Commits aus. Er darf OpenCode via Shell/Python starten, **nicht** den Review selbst schreiben, sich selbst ein `PASS` ausstellen oder die Reviewbewertung nachträglich verändern. Codex liest die Review-Findings und behebt sie vor einem neuen Kandidaten.

**Agent B: OpenCode über die direkte DeepSeek API.** Er erhält die Spezifikation, den Phasenkatalog, den unveränderlichen `phaseBaseSha`, den `candidateSha`, das vollständige kumulative Diff, das Repository und die vorhandenen Testergebnisse. Er arbeitet in einem **neuen detached Git-Worktree auf dem exakten Kandidatencommit**. Er prüft eigenständig Code, Dateilöschungen, Dokumentation, fachliche Zielerreichung und Testaussagen; er kann unabhängige Negativproben und Tests im Review-Worktree ausführen. **Keine Änderung getrackter Dateien, keine Fixes, kein Commit.** Seine eigene Prüfung ist mehr als ein Wiederholen von Codex' Testbefehlen.

**Prüf-Gate (deterministisches Skript)** kontrolliert nach Abschluss die Bindung an Phase/SHA, den tatsächlichen Prozesslauf und dessen unabhängige Testlogs, das verlangte strukturierte Urteil sowie die vollständige Kriterienmatrix. **Nur** ein inhaltlich positiv begründetes und maschinell validiertes `PASS` erlaubt die nächste Phase. Der Gatecode selbst ersetzt **nicht** die fachliche Bewertung durch B.

### 4.2 Ausführungsumgebung: OpenCode Desktop 2.2.5, V2-CLI und Python

**Bekannter lokaler Ausgangspunkt:** Die Benutzerin/der Benutzer verwendet **OpenCode Desktop 2.2.5 (GUI)** und hat DeepSeek darin bereits erfolgreich eingesetzt. Ein früherer Codex-Vorcheck fand dagegen bei `opencode --version` **1.18.22** und bei `python3 --version` **3.9.6**. Dies ist **kein Grund für `BLOCKED vor P0`**: Der gefundene CLI-Befehl kann eine separate alte V1-Installation sein; Python 3.9.6 genügt für einen bewusst kompatibel implementierten Orchestrator.

**Python:**

- Voraussetzung: Python **3.9 oder neuer**, Standardbibliothek, Git, JDK 25, Maven-Wrapper und Docker/Backing-Services gemäss Buildanforderungen. `scripts/cleanup_review.py` und `scripts/verify_cleanup_docs.py` müssen unter **3.9.6** ausführbar und getestet sein.
- Keine Features voraussetzen, die erst ab 3.10/3.11/3.12 vorhanden sind. Für Typannotationen bei Bedarf `from __future__ import annotations` und `typing.Optional` verwenden; Python-Dependencyinstallation vermeiden.
- `venv` ist optional für externe Pakete, jedoch **keine neue Python-Version**. Wird wider Erwarten ein neuer Interpreter fachlich nötig, darf Codex ihn isoliert z. B. mit einem bereits verfügbaren `uv` suchen/bereitstellen; eine Python-Versionsänderung ist ausdrücklich **nicht** der Standardweg. Keine unkontrollierten globalen Paketinstallationen.
- Codex' eigene Laufzeitumgebung hat keine zugesicherte „besondere Python-Version“. Deshalb Interpreter explizit über `sys.executable` bzw. einen ermittelten Pfad weiterreichen und in Testbelegen protokollieren.

**OpenCode V2 statt V1:**

1. `command -v opencode`, `opencode --version`, `opencode run --help` **nur lesend** prüfen. **CLI 1.18.22 ist nicht gleich der Desktop-Version 2.2.5** und darf für diesen Auftrag nicht stillschweigend als V2 gelten.
2. Vorrangig eine **vorhandene OpenCode-V2-CLI** verwenden, möglichst Version **2.2.5** und im selben macOS-Benutzerkontext wie die Desktop-App. Ein vorhandener GUI-Sidecar-Prozess ist kein garantiert öffentlich nutzbarer CLI-Einstieg; dessen internen Pfad oder Serverport **nicht erraten**. Ist ein offiziell nutzbarer V2-CLI-Aufruf vorhanden, diesen nach echten `--version`/`run --help`-Checks einsetzen.
3. Falls nur V1 im `PATH` liegt: eine **separate, offizielle V2-CLI** gezielt ermitteln oder isoliert installieren (z. B. aus der offiziellen V2-Distribution oder `@opencode/cli`; zunächst keine globale Überschreibung der V1-Installation). Optional `OPENCODE_BIN=/absoluter/pfad/zu/opencode` für den Orchestrator unterstützen. Version, Binärpfad und unterstützte Flags sind im P0-Nachweis festzuhalten. Wenn dieser Schritt nicht ohne Berechtigungs-/Integritätskonflikt möglich ist, erst **vor dem echten Review** `BLOCKED` melden.
4. **Die GUI selbst muss nicht ferngesteuert werden.** Für automatisierbare und SHA-gebundene Reviews `opencode run` der V2-CLI verwenden. Ein kompatibler Serverzugang kann genutzt werden, wenn er offiziell dokumentiert, autorisiert und in P0 geprüft ist; keine ungesicherten localhost-Portscans und keine GUI-Klickautomatisierung.

**Authentifizierung – vorhandene Verbindung zuerst:**

- V2 `opencode auth list` (ggf. `--format json` nach `--help`) und `opencode models` im **gleichen Benutzer-/Profilkontext** ausführen. Eine bestehende DeepSeek-Verbindung muss wiederverwendet werden können; `DEEPSEEK_API_KEY` ist **keine** Pflichtvariable und ihr Fehlen **kein** Fehler, solange ein echter nichtinteraktiver DeepSeek-Aufruf funktioniert.
- OpenCode V2 speichert neue Provider-Anmeldungen in der lokalen Server-SQLite-Datenbank, gewöhnlich `~/.local/share/opencode/opencode.db` (mit möglichem anderen Speicherort durch Profil/Kanal/`OPENCODE_DB`); V1 verwendete `auth.json`. Die CLI darf den Pfad via `opencode debug paths db` diagnostizieren, **aber die Datenbank/Datei niemals direkt lesen, exportieren, kopieren oder bearbeiten, um Geheimnisse zu gewinnen**.
- Ein isolierter Codex-Container mit anderem `HOME`/macOS-Benutzerprofil sieht die GUI-Anmeldungen möglicherweise **nicht**. Zuerst gleichen User und V2-Backend prüfen. Nur wenn ein echter API-Smoke-Test trotz des vorhandenen GUI-Logins mangels Zugriff scheitert, **gezielte manuelle Authentifizierung im zugänglichen V2-Profil** anfordern (z. B. `opencode auth login deepseek` in einer interaktiven Sitzung). Niemals den Key im Chat, im Repository oder als CLI-Argument verlangen; Umgebungsvariable ist höchstens eine vom Benutzer gewählte Alternative.
- Keinen globalen OpenCode-Login, kein Account-Switch, kein `service restart` und keine Änderung am GUI-Profil ohne nachvollziehbare Notwendigkeit. Bei unerwarteter Providerkonfiguration zuerst Diagnose mit geschwärzten Metadaten, **keine Zugangsdaten offenlegen**.

**Modell und Proben:**

- Festes Reviewmodell **DeepSeek-V4.1-Flash-1017** über den **direkten DeepSeek-Provider**. Aktuelle API-ID **`deepseek-flash`**, OpenCode-Modell-ID **`deepseek/deepseek-flash`**. `GRETL_REVIEW_MODEL` darf fehlen oder genau `deepseek/deepseek-flash` sein. Kein Fallback auf OpenRouter, OpenCode Go/Console, anderes Flash-Release oder Pro-Modell.
- Die GUI-Modelleinstellung allein ist kein Beleg für die **CLI-Sitzung**. P0 ermittelt V2-Modelleintrag und bestätigt mit **einem echten harmlosen nichtinteraktiven `opencode run`**, dass Provider und Modell erreichbar sind, der Ergebnisstrom vollständig ist und die gewählten Rechte wirken. Keine erneute `/connect`-Anmeldung verlangen, wenn bereits verbunden.
- `opencode run --model deepseek/deepseek-flash --format json "<kurzer-check>"` wird **mit `cwd` im Review-Worktree** gestartet. Flags **nur** verwenden, wenn V2 `opencode run --help` sie anbietet. `--dir` wird nicht vorausgesetzt. Bei vorhandenen gespeicherten V2-Anmeldungen **normalen V2-Service** bevorzugen; `--standalone` nur bei nachgewiesener Eignung und ohne Verlust der gespeicherten Anmeldung. Kein `--auto`.
- P0 prüft die tatsächliche OpenCode-V2-Ereignisstruktur, Session-/Modellidentität und API-Fehler. Ein V2-Erfolg in der Desktop-GUI ersetzt diesen Live-Smoke-Test **nicht**, macht aber eine vorgängige Passwort-/ENV-Pflicht überflüssig.
- Die offizielle DeepSeek-Preisseite ordnet `deepseek-flash` am 31.10.2026 dem Checkpoint **DeepSeek-V4.1-Flash-1017** zu. Der Name ist ein **Alias**, keine permanent eingefrorene Modellgewicht-ID. Bei geänderter/nicht prüfbarer Zuordnung `BLOCKED` und explizite Spezifikationsrevision. Keine Behauptung, das Modellgewicht direkt kryptografisch verifizieren zu können.

**Zeitpunkt von Voraussetzungen:** P0 darf mit vorhandenem Python 3.9.6 und **ohne verfügbaren Live-Key im Codex-Prozess** das Inventar, das Python-Reviewgate, Fake-CLI-Tests und lokale Dokumentationstests implementieren. Vor dem abschliessenden **echten** P0-Smoke-Test/Review müssen V2-CLI, gespeicherte Berechtigung und direkter DeepSeek-Aufruf funktionieren. Fehlende Voraussetzungen verbieten den **P0-PASS**, nicht die vorherige legitime P0-Implementierung.

**Offizielle technische Referenzen (abgerufen 31.10.2026):**
- OpenCode V2 – CLI/Installation: <https://opencode.ai/v2/docs>, <https://opencode.ai/v2/docs/cli/commands>.
- V2-Provider, Zugangsdaten, SQLite-Store: <https://opencode.ai/v2/docs/cli/providers>.
- V2-Berechtigungen (`permissions`, `shell`, `subagent`): <https://opencode.ai/v2/docs/permissions/>.
- V2-Konfiguration und Modelle: <https://opencode.ai/v2/docs/config>, <https://opencode.ai/v2/docs/models/>.
- DeepSeek-Modellmapping: <https://api-docs.deepseek.com/quick_start/pricing/>; Release: <https://api-docs.deepseek.com/news/news261017/>.

### 4.3 Vom Coding-Agenten zu erstellende Automatisierung

**Im Repository anzulegen:**

```text
scripts/cleanup_review.py                # alleiniger autonomer CLI-Review-Einstieg
tests/cleanup_review/                    # Python-stdlib-Unit- und Integrationstests für das Gate
scripts/verify_cleanup_docs.py           # deterministische Dokument-/Link-/Namensprüfungen
docs/maintenance/cleanup/spec-v1.md      # Kopie dieser Spezifikation (kanonischer Pfad im Checkout)
docs/maintenance/cleanup/inventory.json  # Baseline und Entscheidungen je Datei
```

Der genaue Dateiname `tests/cleanup_review/` kann ein Python-`unittest`-Paket sein. **Kein weiteres Workflowframework, keine Maven-Plugin-Implementierung, kein CI-Produktservice, kein zweiter Aufgabenorchestrator.** `cleanup_review.py` verwendet die Standardbibliothek (`subprocess`, `json`, `pathlib`, `tempfile`, `hashlib`, `time`, `signal`, `argparse`, `unittest` für Tests). Er muss als einzelne CLI aus Codex heraus aufrufbar sein und selbständig OpenCode/DeepSeek anstossen.

**Verbindliche CLI (Implementierungsmuster):**

```bash
# Wird von Codex nach erfolgreichem eigenen Test und Commit aufgerufen.
python3 scripts/cleanup_review.py \
  --phase P1 \
  --base <SHA-des-letzten-freigegebenen-Stands> \
  --candidate <unveraenderlicher-vollstaendiger-Commit-SHA> \
  --spec docs/maintenance/cleanup/spec-v1.md
```

`P0`, `P1`, `P2`, `P3` sind die einzigen Phasen-IDs. Das Skript bietet `--help`, einen `--preflight` und Tests gegen ein Fake-OpenCode-Executable. **Ein Live-Review benutzt immer den echten OpenCode-Prozess**; die Test-Doubles dürfen nur für isolierte Gate-Tests dienen.

### 4.3.1 Minimale Klassen-/Methodengrenzen für die Automation

Der Reviewer-Launcher bleibt bewusst **ein kleines Python-Programm**, nicht ein neues Agentenframework. Die folgende Schnittstelle ist eine verbindliche Orientierung für Verantwortung, Testbarkeit und eindeutige Fehlerfälle; funktional äquivalente Benennungen sind nur mit dokumentiertem Grund zulässig:

```python
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
from typing import Literal, Optional

Phase = Literal["P0", "P1", "P2", "P3"]

@dataclass(frozen=True)
class ReviewRequest:
    phase: Phase
    phase_base_sha: str          # 40 hex, bereits akzeptierte Basis
    candidate_sha: str           # 40 hex, exakt geprüfter Commit
    initial_baseline_sha: str    # Projektbaseline, in P3 für Gesamtdiff
    repository_root: Path
    specification_file: Path

@dataclass(frozen=True)
class CommandEvidence:
    argv: tuple[str, ...]
    cwd: Path
    return_code: int
    stdout_path: Path
    stderr_path: Path
    started_at_utc: str
    finished_at_utc: str

# Fachliche Funktionen, von main(...) in dieser Reihenfolge aufgerufen:
def validate_request(request: ReviewRequest) -> None: ...
def allocate_evidence_dir(request: ReviewRequest) -> Path: ...
def create_detached_worktree(request: ReviewRequest, evidence_dir: Path) -> Path: ...
def run_independent_gates(worktree: Path, request: ReviewRequest,
                          evidence_dir: Path) -> list[CommandEvidence]: ...
def discover_opencode_v2(request: ReviewRequest) -> Path: ...
def inspect_v2_auth_and_models(executable: Path, request: ReviewRequest) -> dict: ...
def prepare_opencode_environment(request: ReviewRequest) -> dict[str, str]: ...
def invoke_opencode(worktree: Path, request: ReviewRequest,
                    evidence_dir: Path, environment: dict[str, str]) -> CommandEvidence: ...
def parse_final_review(jsonl_path: Path) -> dict: ...
def evaluate_review(review: dict, request: ReviewRequest,
                    worktree: Path, gate_runs: list[CommandEvidence]) -> None: ...
def verify_worktree_unchanged(worktree: Path, request: ReviewRequest) -> None: ...
def write_gate_result(evidence_dir: Path, request: ReviewRequest,
                      verdict: str, details: dict) -> None: ...
def main(argv: Optional[list[str]] = None) -> int: ...
```

- Jede Funktion hat **eine** Rolle. `main` verwaltet `try/finally`, damit der Worktree auch bei Exception/Timeout entfernt wird, aber externe Evidenz erhalten bleibt. In einem zweiten Prozess ist es nicht erlaubt, `git -C <implementierungs-checkout> reset` oder andere mutierende Befehle auszuführen.
- `validate_request` prüft `git merge-base --is-ancestor`, vollständige SHAs, HEAD-/Cleanliness-Bedingungen und den festen Kriterienkatalog `REQUIRED_IDS` (P3 = Vereinigung aller P0–P3-IDs). Als Ausgangsbasis für P3 dient **zusätzlich** die in P0 eingefrorene anfängliche Baseline, nicht nur der letzte Reviewcommit.
- `run_independent_gates` startet Befehle als **Argumentlisten mit `shell=False`** und `cwd=worktree`; es schreibt pro Gate immutable `command.json`, Logdateien und – vor jedem weiteren Clean – Kopien/Hashes der Test-XML und benötigten Auswertungen. Ein Helper `run_command(argv, cwd, timeout, ...)` kapselt Exit-/Timeout-/Signalbehandlung und kann in Unit-Tests injiziert werden.
- `discover_opencode_v2` trennt explizit V1-CLI und V2-Desktop/CLI, qualifiziert den wirklich verwendeten V2-Binärpfad (`OPENCODE_BIN` optional) und erzwingt keinen Austausch der bestehenden GUI-Installation. `inspect_v2_auth_and_models` fragt **nur** Provider-/Modell-Metadaten ab und prüft die **bereits bestehende V2-Anmeldung** mittels echtem, harmlosen Modellaufruf. Keine Key-Extraktion und **keine** Vorbedingung `DEEPSEEK_API_KEY`.
- `prepare_opencode_environment` erbt notwendige Benutzer-/Profilwerte, erzwingt die **direkte** Modellwahl `deepseek/deepseek-flash`, verwendet eine nachweislich **OpenCode-V2-kompatible** temporäre Berechtigungskonfiguration und weist eine abweichende `GRETL_REVIEW_MODEL`-Kennung zurück. Eine vorhandene GUI-/V2-Anmeldung darf nicht durch ein künstliches anderes `HOME`, `XDG_DATA_HOME` oder einen fremden Provider überschrieben werden. Keine erzwungene API-Key-Variable und kein Rückfall auf V1-`auth.json` oder einen anderen Provider.
- `parse_final_review` parst das **gesamte JSONL** (Grössenlimit und UTF-8-Prüfung), lehnt Error-Events ab, identifiziert eine eindeutige abschliessende Antwort und deren JSON-Marker, entfernt keine widersprechenden Zwischenergebnisse stillschweigend. `evaluate_review` prüft Struktur, exakte Kriterienmenge, fachliche Fundstellen und Prozessprovenienz; frei erfundene Statusfelder gelten nicht als Testbelege.
- `verify_worktree_unchanged` läuft **auch bei FAIL/BLOCKED** in `finally`; zusätzlich werden Git-HEAD, index und getrackte Dateien verglichen. Falls die Prüfung selbst scheitert, darf der Gate-Exitstatus nicht zu PASS werden.
- `write_gate_result` schreibt maschinell lesbare Metadaten mit SHA-256 der Rohlogs und des unverändert übernommenen DeepSeek-Reviewtexts. Ein optionaler `export-review-evidence`-Befehl erzeugt aus diesen Rohdaten die commitfähige, bereinigte Review-Zusammenfassung; er darf keine neuen fachlichen Aussagen erfinden.

**Keine impliziten Nebenwirkungen:** Die Reviewfunktionen dürfen weder einen neuen Commit erstellen noch alte Reviews verändern, in fremde Projekte schreiben oder die globale OpenCode-Konfiguration umstellen. Tests verwenden `tempfile.TemporaryDirectory`, temporäre Fake-Repositories und eine injizierte Fake-CLI; der *echte* Review darf dagegen weder gemockt noch durch Testfixtures simuliert werden.

**Sichere Ausführung (sachlicher Ablauf, keine ungeprüft kopierbare Shell-Magie):**

1. Git-Root, vollständigen `base`/`candidate`-SHA, Existenz der Commits, sauberen Arbeitsstand und lineare Ancestry prüfen. `candidate` muss `base` enthalten; Kandidat darf kein Platzhalter und kein uncommitteter Stand sein. Phase-spezifische erlaubte Dateibereiche prüfen; Codeänderungen ausserhalb des vereinbarten Umfangs explizit zurückweisen.
2. Neues externes Evidenzverzeichnis unter `${TMPDIR:-/tmp}/gretl-runtime-cleanup-review/<phase>/<candidateSha>/attempt-XX/` erstellen, `0700`; der Pfad liegt **ausserhalb** des Produktcheckouts. Nachweise dürfen nicht durch einen nachfolgenden `mvn clean` verloren gehen. Vorhandene Versuche nie überschreiben.
3. `git worktree add --detach <separater-review-pfad> <candidateSha>`; unmittelbar `git rev-parse HEAD`, `git status --porcelain` und `git diff --exit-code` aufzeichnen. Codex' aktives Arbeitsverzeichnis ist **nie** das Review-Arbeitsverzeichnis.
4. Vor der LLM-Sitzung im **Review-Worktree** die beiden obligatorischen Build-Gates **sequenziell** ausführen; bei Erfolg Logs, XML-Testberichte, vollständige Befehle, Exitcodes, SHA und Zeitstempel extern sichern. Zusätzlich ein **redigiertes, gehashtes, nur-lesbares Belegmanifest beider Gates unter `target/cleanup-review-evidence/`** innerhalb des Review-Worktrees ablegen, damit Agent B die früheren Standard-Gates trotz nachfolgendem `mvn clean` inspizieren kann, ohne externe Verzeichnisse freizugeben. Ausfälle bleiben `FAIL`/`BLOCKED`, niemals automatisch `skip` oder `PASS`. Für Phase P0 sind die neuen Python-Unit-Tests ebenfalls auszuführen.
5. Review-Prompt mit **allen Akzeptanzkriterien der aktuellen Phase**, Ausgangs-/Kandidaten-SHA, Spezifikation, Änderungen, gelöschten Dateien und eigenständig geschriebenen Gates formulieren. Für P3 zusätzlich ursprünglichen Inventar-Baseline-SHA, Gesamtdiff seit der Baseline und Nachweise für P0–P2 beifügen. Agent B muss selbst die relevanten Dateien und `git diff <base>..<candidate>` einsehen; Codex' Bericht dient erst danach als sekundäre Quelle.
6. OpenCode **frisch und nichtinteraktiv** mit der tatsächlich geprüften **V2-CLI** per `subprocess` starten; `cwd` ist der neue Review-Worktree. Festes `--model deepseek/deepseek-flash` (DeepSeek-V4.1-Flash-1017), `--format json`, neue Session; **kein** `--continue`, **keine** Modellüberschreibung, **kein** `--auto`. Beispiel (Shell wird im Worktree ausgeführt; in Python `subprocess.run(..., cwd=worktree, shell=False)`):

   ```bash
   opencode run --model deepseek/deepseek-flash \
     --format json \
     "<eindeutiger-Reviewauftrag-mit-SHA-und-Kriterien>"
   ```

   Der automatisierte Launcher verwendet den in P0 verifizierten absoluten `OPENCODE_BIN`-Pfad oder den ermittelten V2-CLI-Pfad und prüft `run --help`. **Kein `--dir` voraussetzen.** Gespeicherte V2-Anmeldung/Profilzugang wiederverwenden. `--standalone` nur verwenden, wenn das konkret getestete V2-Setup damit die bestehende Direktprovider-Anmeldung nutzen kann; normalerweise nicht erforderlich. Ein optional gesetzter `DEEPSEEK_API_KEY` ist lediglich ein erlaubter Zugangsweg, kein Pflichtfeld. **Kein stiller Provider-/Modell-Fallback.**
7. Vollständige, limitierte OpenCode-JSONL-Ereignisse aus stdout sowie separat stderr, Exitstatus, Toolaktionen, Session-ID und Modell-/Versionsinformation **extern** erfassen. Das Format ist Ereignis-JSONL, kein einzelnes JSON-Objekt; Text steht üblicherweise in `type=text`, `part.text`. `type=error`, fehlender Text, unlesbare Events, Output-Truncation, Timeout oder fehlender belastbarer Abschlussnachweis führen zu `BLOCKED`. Die konkrete OpenCode-Version und ihre JSONL-Ereignisse sind in P0 mit einem **echten** API-Probelauf zu qualifizieren. Eine bekannte Klasse von OpenCode-Versionen liefert unter bestimmten Umständen trotz Exitcode 0 unvollständige stdout-Ereignisse; deshalb ist **Exit 0 alleine nie eine Freigabe**. Optionalen Session-Export nur nach expliziter versionsbezogener Validierung heranziehen. Keine Debug-/Reasoning-Dumps veröffentlichen.
8. Nur einen eindeutigen **finalen** maschinenlesbaren Reviewbericht akzeptieren, durch fest definierte Marker `GRETL_REVIEW_JSON_BEGIN`/`GRETL_REVIEW_JSON_END` abgegrenzt. Keine Extraktion irgendeines zufälligen JSON-Objekts aus Toollogs; bei mehreren uneindeutigen Kandidaten `BLOCKED`. Vollständiges Schema prüfen (4.5).
9. Nach dem Review `git status`, Git-HEAD und Änderungen **im Review-Worktree** erneut prüfen; getrackte oder andere unerlaubte Änderungen => `BLOCKED`, alle Änderungen als Befund sichern. Maven-`target/` und andere ignorierte **Testartefakte** sind erlaubt, Änderungen an Quellen nicht. Worktree kontrolliert entfernen; **unabhängige Evidenz bleibt erhalten**.
10. Nur wenn sämtliche maschinellen Sicherheitsbedingungen und das vollständige fachliche Urteil `PASS` sind, Exitcode 0 und `gate-result.json` mit `PASS`; `FAIL` (nachgewiesene Abweichung) Exitcode 10; `BLOCKED` (Provider/Tool/Test/Review/Evidenz nicht verifizierbar) Exitcode 20; interne Gatefehler Exitcode 30. Codex darf bei 10/20/30 **nicht** zur nächsten Phase wechseln.

**Abbruch/Timeout:** Jeder gestartete Kindprozess hat ein explizites, begründetes Zeitlimit. Bei Timeout Prozessgruppe kontrolliert terminieren, Kinder aufräumen, Ursache und Teilbelege erhalten, `BLOCKED`. Geheimnisse vor Persistierung der Logs maskieren. Kein blindes automatisches Wiederholen kostenpflichtiger API-Calls; ein manueller oder Codex-entschiedener neuer Versuch muss eindeutig nummeriert sein.

### 4.4 OpenCode-V2-Rechte und Review-Integrität

Der Review-Agent ist **read-only bezüglich getrackter Dateien**. Der Agent darf Dateien/Gitdiffs lesen und fachlich beurteilen; **Build- und Zusatztests führt der Orchestrator kontrolliert im separaten Review-Worktree aus.** OpenCode benötigt dafür kein allgemeines Recht, beliebige Shellbefehle oder Schreibwerkzeuge auszuführen. Keine `git commit`/`push`/`reset`, kein Löschen/Bearbeiten von Quellbäumen, keine fremden Projektzugriffe, kein Lesen von Credentials.

**OpenCode V2 verwendet `permissions` als geordnete Regel-Liste mit Aktionen `edit`, `shell`, `subagent`, `read`, `glob`, `grep`, `external_directory`; die V1-Felder `permission`, `bash`, `task` sind für diesen Auftrag verboten.** Beispiel für eine vor Einsatz am tatsächlichen V2-Build zu validierende restriktive Konfiguration:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "permissions": [
    {"action":"edit","resource":"*","effect":"deny"},
    {"action":"subagent","resource":"*","effect":"deny"},
    {"action":"external_directory","resource":"*","effect":"deny"},
    {"action":"shell","resource":"*","effect":"deny"},
    {"action":"shell","resource":"git status *","effect":"allow"},
    {"action":"shell","resource":"git diff *","effect":"allow"},
    {"action":"shell","resource":"git show *","effect":"allow"},
    {"action":"shell","resource":"git ls-files *","effect":"allow"},
    {"action":"shell","resource":"git grep *","effect":"allow"},
    {"action":"read","resource":"*","effect":"allow"},
    {"action":"glob","resource":"*","effect":"allow"},
    {"action":"grep","resource":"*","effect":"allow"}
  ]
}
```

Codex legt diese Regeln **nicht** in der persönlichen OpenCode-GUI-Konfiguration ab. Stattdessen wird eine temporäre, versionsqualifizierte Review-Konfiguration benutzt, z. B. eine nur für den Review angelegte `.opencode/opencode.jsonc` im detached Worktree, **ausschliesslich wenn dort keine zu überschreibende Konfiguration existiert**. Vorher sauberen Worktree prüfen, temporäre Konfiguration nachher löschen, anschliessend HEAD, getrackte und nicht erlaubte ungetrackte Dateien vergleichen. Andere offiziell dokumentierte V2-Konfigurationswege sind zulässig, **wenn** ihre Wirksamkeit im P0-Negativtest nachgewiesen ist. Das **redigierte** `target/cleanup-review-evidence/` ist für B lesbar; Rawlogs bleiben geschützt ausserhalb des Worktrees. Beibehaltung des Benutzerprofils und seiner gespeicherten Anmeldung ist wichtiger als eine vermeintlich komplett isolierte `HOME`-Umgebung. Keinen privaten Provider-Account an eine fremde Testkonfiguration kopieren.

**Negative Gateprobe:** Der Agent versucht in einer streng kontrollierten Test-Sandbox ein verbotenes Schreibwerkzeug und einen nicht freigegebenen Shellbefehl zu benutzen; beide müssen von der **effektiven** V2-Policy verhindert werden. Unabhängig davon prüft `verify_worktree_unchanged` Änderungen und Fremdzugriffe. Wenn Rechte nicht technisch wirksam sind: `BLOCKED`; keine allein auf dem Reviewprompt beruhende Read-only-Behauptung. `--auto` nicht verwenden.

### 4.5 Berichtsschema und inhaltliche Abnahme

Die strukturelle Form wird vor der Implementierung als Schema/Validator in `cleanup_review.py` festgelegt und mit Positiv-/Negativfällen getestet. Beispiel (Werte illustrativ):

```json
{
  "schemaVersion": 1,
  "phase": "P1",
  "phaseBaseSha": "<40-hex>",
  "candidateSha": "<40-hex>",
  "verdict": "PASS",
  "scopeComplete": true,
  "requirements": [
    {
      "id": "P1-03",
      "status": "PASS",
      "evidence": [
        {
          "path": "website/getting-started.qmd",
          "observation": "Das Gradle-Beispiel ist ersetzt, und die ausgefuehrten CLI-Befehle passen zur Runtime.",
          "commandOrReference": "<tatsaechlicher-nachweis>"
        }
      ]
    }
  ],
  "independentInspection": {
    "diffReviewed": true,
    "newAndDeletedFilesReviewed": true,
    "criticalPaths": ["README.md", "website/getting-started.qmd"],
    "regressionAssessment": "<begruendete-eigene-Bewertung>",
    "objectivesAssessment": "<begruendete-eigene-Bewertung>"
  },
  "findings": [],
  "openQuestions": [],
  "recommendation": "<knappe-Zusammenfassung>"
}
```

**Strikte Gate-Regeln:**

- `phase`, `phaseBaseSha` und `candidateSha` stimmen exakt mit dem Aufruf; vollständige 40-stellige SHA, keine Kurzhash-Angaben im Schema.
- Jedes für die Phase verlangte **Kriterium einmal**, IDs exakt, alle `status=PASS`, je Kriterium mindestens ein **prüfbarer** Pfad/Symbol/Test oder Diffbeleg; keine blossen Adjektive („sieht gut aus“).
- `scopeComplete=true`, `diffReviewed=true`, `newAndDeletedFilesReviewed=true`; Beurteilungen der **Zielerreichung** und Regressionen müssen sachlich und dateispezifisch sein. Selbstbericht von A allein gilt nicht als Evidenz. Das Gate validiert Pfadexistenz oder bewusst gelöschten Pfad via Git-Diff und plausible Belegreferenzen; es überlässt die fachliche Bewertung B.
- In P3 enthält die Kriterienmatrix zusätzlich die P0-, P1- und P2-Kriterien, weil P3 eine **kumulative** Abschlussprüfung ist; diese Einträge sind neu zu beurteilen, nicht als alten PASS zu übernehmen.
- `findings` mit Schwere `BLOCKER/HIGH` oder nicht geschlossene `MEDIUM`-Abweichungen verhindern `PASS`. `LOW`-Hinweise können nur mit expliziter Begründung offen bleiben. `openQuestions` dürfen bei `PASS` keine unentscheidbaren Pflichtkriterien enthalten.
- Der Reviewer benennt **auch die gelöschten und umbenannten** Dateien, nicht nur den neuen Code. Der Gatevalidator vergleicht `git diff --name-status --find-renames <base>..<candidate>` mit dem geprüften Dateiumfang; keine völlig ungeprüften Änderungen.
- Ein unerreichbarer DeepSeek-Endpunkt, ein anderes Modell als **DeepSeek-V4.1-Flash-1017** (OpenCode-Kennung `deepseek/deepseek-flash`, direkte API-ID `deepseek-flash`), eine ungeklärte oder geänderte Modell-Aliaszuordnung, Testskips, fehlendes Docker beim Vollgate, nicht parsbares JSONL, unvollständige Bewertungsmatrix, neue nicht aufgelöste Links oder eine unklare Modellantwort ergeben **nie PASS**.
- Modellbewertung wird als **externer Prüfbeleg** gespeichert. Sie darf nicht von Codex selbst erzeugt oder „repariert“ werden; Codex darf lediglich eine automatisiert erstellte, bytegetreu übernommene, klar als unabhängige Review ausgewiesene Zusammenfassung committen.

### 4.6 Eigenständige Tests des Reviewverfahrens

Verpflichtende `unittest`-Fälle für `cleanup_review.py` (**Fake-OpenCode nur hierfür**): erfolgreich gebundenes `PASS`; falscher SHA; falsche Phase; fehlendes Kriterium; fehlender Diff-/Löschungsnachweis; `PASS` mit `BLOCKER`; leeres `objectivesAssessment`; leere Antwort bei Prozess-Exit 0; `error`-Event; ungültige JSONL-Zeile; Stream-Ende ohne gesicherten Abschluss; Provider 401/429/Timeout; falsch gewähltes Modell; abweichendes `GRETL_REVIEW_MODEL`; falscher API-Provider oder Modell-Alias; unbekannte Review-ID; getrackte Änderung im Review-Worktree; fehlendes Docker / Build-Exit != 0; Prozessunterbrechung; verwaister Worktree; Wiederverwendung der gespeicherten OpenCode-V2-Anmeldung ohne `DEEPSEEK_API_KEY`; Ablehnung einer V1-CLI 1.18.22; `OPENCODE_BIN`-Pfad und V2-Berechtigungsregeln; isolierter Benutzerkontext ohne GUI-Login ergibt vor dem echten Review `BLOCKED`; neue Reviewversuche überschreiben alte nicht. Positive und negative Testausführung müssen dokumentiert sein.

**Echter Smoke-Test in P0:** Das Skript ruft OpenCode mit der **direkten DeepSeek API und `deepseek/deepseek-flash` (1017)** tatsächlich auf, prüft eine harmlose, eindeutig erwartete Kurzantwort, verwertbare JSON-Ereignisse, Session-/Modelldaten, API-/Providerzuordnung, Exitcode und die wirksamen Toolverbote. Dieser kleine Preflight ist **noch kein unabhängiger Phase-P0-Review**. Danach folgt der separate reale P0-Review des Implementationcommits. Keine Fake-Antwort darf den echten P0-PASS ersetzen.

### 4.7 Commit-, Review- und Freigabeprotokoll

Für jede Phase gilt:

```text
A: letzter freigegebener Basiscommit (phaseBaseSha)
       |
       v
B: Codex implementiert, testet und commitet Kandidat C (candidateSha)
       |
       v
C: Neues Worktree@C → unabhängige Maven-/Python-Gates → OpenCode/DeepSeek@C
       |                              |
       | FAIL / BLOCKED               | PASS + maschinell verifizierter Review
       v                              v
   Codex korrigiert,              Nur Review-Nachweiscommit R
   neuer Kandidat C2              (keine Produkt-/sonstige Dokumentänderung)
   und neuer Review@C2                 |
                                      v
                             nächste Phase ab R
```

**Nach erfolgreichem PASS:** Codex darf einen **separaten, ausschliesslich dokumentierenden** Commit `R` anlegen, der nur `docs/maintenance/cleanup/reviews/Pn-<candidate7>-review.md` und eine kleine passende JSON-Metadatendatei enthält. Beides wird **automatisch aus der geprüften externen Evidenz abgeleitet**, ist an `candidateSha` und Digest gebunden und enthält **keine** neu erfundene Freigabe. Das Hilfsskript prüft `git diff C..R` auf exakt diese Whitelist; keinerlei Produkt-, Build-, Website- oder sonstige Dokumentänderungen. Falls diese Regel nicht erfüllt ist, muss auch R nochmals unabhängig geprüft werden. Bei der nächsten Phase ist `R` der Ausgangscommit; der Reviewer verifiziert die reine Nachweisnatur von R. Kein `git push`.

**Bei FAIL:** Codex analysiert Findings, verändert nur zulässige Quelldateien, führt beide Gates erneut aus, erstellt einen **neuen Commit** und startet **neue** OpenCode-Sitzung mit gleichem ursprünglich freigegebenem `phaseBaseSha` und neuem vollständigem `candidateSha`. Frühere Reviewversuche/Evidenz bleiben unverändert extern erhalten. Keine maximale Wiederholungszahl „wegrechnen“: bei nicht behebbaren Blockern sauber mit `BLOCKED` beenden und die konkrete Ursache ausgeben. **Nie** auf den nächsten Phasenauftrag springen.

**Abnahme des Kandidaten** ist ausschliesslich durch den Review auf C gedeckt; die Dokumentausnahme R ist eng begrenzt. Damit ist für jeden Produktions-/Dokumentationsstand nachvollziehbar, von welchem unabhängigen Modell und welchen Gates er tatsächlich überprüft wurde.

---

## 5. Phasenplan und harte Kriterien

**Reihenfolge:** P0 → P1 → P2 → P3. Nicht parallelisieren; vor jeder Phase `git status`, SHA und die Freigabe der Vorphase kontrollieren. **Jede** Phase verlangt *beide* Maven-Gates und den **unabhängigen** OpenCode/DeepSeek-PASS. Python-/Website-/Doku-Gates kommen phasenbezogen hinzu. Voraussetzung Docker, Java 25 und Zugang zu den erforderlichen Maven-Repositories; fehlen diese, Phase `BLOCKED`, kein gekürztes „PASS“.

### Phase P0 – Ist-Inventar, Schutzregeln und autonome Reviewstrecke

**Ziel:** Risiken erfassen und die automatische unabhängige Review-Infrastruktur real funktionieren lassen, **bevor** aktive Dokumente und Code gelöscht/umbenannt werden. Keine Runtime-/Website-Bereinigung in P0.

**Umsetzung:**

1. Checkout-Stand und Testbasis erfassen: `git rev-parse HEAD`, Module, Produktionstypen/Operation-IDs, Deskriptor-Schema-Tests, bestehende Testklassen, die aktuelle Dokumentations- und Website-Liste; Baselines in `inventory.json`, nach Dateipfad sortiert. Enthält Zuordnung `ACTIVE/DECISION/ARCHIVE/DELETE`, Begründung, Referenzen/Nutzer und geplante Phase. Keine Ergebnisse auf Basis blosser Dateinamen unterstellen.
2. `scripts/cleanup_review.py` und seine `unittest`-Suite nach §4 realisieren. Mindestens die in 4.6 genannten Negativfälle automatisieren. `--preflight` prüft JDK/Maven/Docker/Git/Python 3.9+, unterscheidet die vorhandene V1-CLI (1.18.22) von OpenCode V2 (Desktop 2.2.5), sucht die **V2-CLI**, prüft deren Direktprovider-Verbindung anhand gespeicherter Authentifizierung und Live-Smoke-Test; **kein ENV-Key-Zwang**. Die Berechtigungssyntax wird gegen die tatsächlich benutzte **V2-CLI** qualifiziert. Fehlende V2-Credentials blockieren erst den echten Review, nicht Inventar und Skriptimplementierung.
3. `scripts/verify_cleanup_docs.py` beginnt als **reiner Auditor**: lokale Markdown-/Quarto-/AsciiDoc-Ziele und Includes erfassen, dokumentieren, kaputte Verweise melden; zunächst Baseline bekannte Fehler explizit ausweisen. Keine pauschale Linkunterdrückung. Für aktive Quellen präzise Allowlist historischer Ausnahmen; keine Netzwerkanfragen zur Prüfung externer URLs erforderlich.
4. Neue Spezifikation kanonisch unter `docs/maintenance/cleanup/spec-v1.md` ablegen. Vorhandene `AGENTS.md` bleiben bis P1 unberührt; bei Konflikt gilt diese aktuelle Aufgaben-Spezifikation nur **für den Cleanupauftrag**.
5. Nach grünem Codex-Test und Commit den **echten** OpenCode/DeepSeek-Review aus `cleanup_review.py` auf dem P0-Kandidaten starten. B prüft, ob die Automation selbst korrekt sperrt, reviewt Parser/Prozessisolation und die Negativtests; ein Fake-Review oder selbstgeschriebener PASS ist unzulässig.

**Pflichtkriterien:**

| ID | Abnahmebedingung |
|---|---|
| P0-01 | Inventar erfasst den gesamten bekannten dokumentierenden Bestand, Klassifikation ist begründet und reproduzierbar; gültiger Ausgangscommit. |
| P0-02 | Eigenständiges Python-Reviewgate startet OpenCode/DeepSeek **wirklich** per API, mit korrekt beobachtetem Modell und isoliertem Worktree. |
| P0-03 | JSONL-Parser und Review-Schema sperren bei **allen** Negativfällen und akzeptieren genau den gültigen Positivfall. |
| P0-04 | Echte unabhängige Testgates sind an den korrekten SHA gebunden; unbekannte/neue Dateien können nicht unbemerkt durchrutschen. |
| P0-05 | Secrets, OpenCode-Rechte, Logs und Worktrees werden sauber behandelt, keine versteckten Writes/Pushes. |
| P0-06 | Beiderseitige vollständige Maven-Gates PASS; **zusätzlicher** unabhängiger OpenCode/DeepSeek-Review prüft Zielerreichung von P0 und gibt separat PASS. |

### Phase P1 – Aktuelle Dokumentation, Agentenanweisungen und Webseite

**Ziel:** Benutzer und neue Coding-Agenten sehen eine **zutreffende** Beschreibung der aktuellen Runtime. Keine veralteten Quickstarts und keine als aktuell maskierte Vergangenheit.

**Umsetzung:**

1. `README.md` aktualisieren: aktueller Umfang, Minimalbeispiel, funktionierender Checkout-Start, Distributions- und JAR-Start, echte Einschränkungen und Links. Kein unbelegter Release-/Linux-/Native-Status. `AGENTS.md` auf Ist-Code, Build-/Review-Gates und künftige Aufgaben ausrichten; in `docs/spec/AGENTS.md` bis P2 unmissverständlich einen kurzen **historischen Warnhinweis** platzieren, der von historischen Prompts nicht wieder aufgehoben werden darf.
2. `docs/implementation/build.md`, `testing.md`, `module-boundaries.md`, `job-packaging.md`, `project-subprojects/README.md` und andere tatsächlich aktive Seiten an Code/POM/Tests abgleichen; alte Testzahlen und „Phase X wird noch kommen“ entfernen. Technische Verträge nicht ohne Beleg abschwächen. Release-/Pilotstatus nur soweit belegbar.
3. `website/features.qmd`, `getting-started.qmd`, `docs.qmd`, `index.qmd` fachlich neu schreiben/korrigieren; `website/_quarto.yml` auf korrekte Navigation prüfen. Keine Lorem-ipsum-Texte, keine Gradle-Pipeline-Syntax, kein nicht implementierter Scheduler/Worker. **Bestehende Marke/Domain nicht spekulativ ersetzen.**
4. Quickstart nicht nur syntaktisch prüfen, sondern die dargestellten Kommandos in einer vorbereiteten Standarddistribution **wirklich** ausführen (lokale, synthetische Datei/SQL ohne echte Credentials); erwartete Ausgaben unabhängig prüfen. Quarto-Webseitenbuild als Pflichtgate ausführen (`quarto render website/` oder der nachweislich korrekte äquivalente Aufruf). Falls Quarto nicht installiert ist, den fehlenden Nachweis als `BLOCKED` behandeln, nicht stillschweigend mit einem statischen Linkcheck ersetzen. Die statische Quarto-/Linkprüfung bleibt zusätzlich obligatorisch.
5. Eine kleine *aktuelle* Navigationsseite („current docs vs history“) erstellen bzw. bestehende passende Stelle verwenden. Alte Soll-Pläne noch nicht unkontrolliert löschen; ihre Rolle bereits klar markieren.

**Pflichtkriterien:**

| ID | Abnahmebedingung |
|---|---|
| P1-01 | Root-README und `AGENTS.md` sind aktuell, widerspruchsfrei und enthalten lauffähige Checkout-/Distribution-/JAR-Befehle. |
| P1-02 | Build, Test, Modul-/Paket- und Subprojektanleitungen beschreiben implementiertes Verhalten und verweisen auf Tests/Code statt historischer Zahlen. |
| P1-03 | Die vier Web-Hauptseiten enthalten keine falsche Gradle-/Control-Plane-Behauptung und keine Platzhalter; Quickstart wurde tatsächlich ausgeführt. |
| P1-04 | Aktive lokale Links, AsciiDoc-Includes, Quarto-Ziele, Referenzgenerierung und Website-Navigation sind gültig. |
| P1-05 | Fehlende künftige Funktionen sind klar von realen 44 Operationen getrennt; kein Funktions-/CLI-Vertragsbruch. |
| P1-06 | Standard- und Voll-Maven-Gate plus unabhängiger OpenCode/DeepSeek-Review sind PASS; B überprüft **inhaltlich die Richtigkeit der Texte und Quickstarts**. |

### Phase P2 – Historische Spezifikationen, Reviews und Dokumentationsballast

**Ziel:** Aktive Dokumentation radikal entlasten, ohne gültige Entscheidungen, Nachweise oder funktionierende Links zu verlieren. Diese Phase ist **kein** pauschales Verschieben sämtlicher alter Dateien nach `docs/archive/`.

**Umsetzung:**

1. Sämtliche `inventory.json`-Einträge `DELETE`/`ARCHIVE` anhand Referenzsuche prüfen (`git grep`, Buildskripte, AsciiDoc-Includes, Quarto, GitHub-Workflows, Beispiel-Paketierung, Task-Tests). Ein maschinenlesbarer `cleanup-actions.json`-Bericht listet vorherigen Pfad, Aktion `KEEP/REWRITE/MOVE/DELETE`, Grund, abhängige Links und Ersatzpfad; für Delete Absicherung durch Git-Historie.
2. `docs/spec/` aus dem **normativen** Pfad entfernen: verbleibende konkrete API-/Runner-/Logging-/DSL-Verträge in aktueller Architektur dokumentieren; offene Publisher-/Native-Entwürfe knapp im Backlog als **nicht umgesetzt** sichern; historisch erforderliche Quellen gezielt in `docs/history/` referenzieren. Alte Coding-Agent-Startpläne und ursprüngliche Soll-Spezifikation nicht aktiv stehen lassen.
3. Unter `docs/implementation/hardening-f1-f4/`, `improvement-plan/`, `project-subprojects/` abgeschlossene Zwischen-Implementierungs- und Reviewversuche, wiederholte Startprompts und ungenutzte redundante Evidenz entfernen. Einen **kleinen** finalen Abschluss-/Entscheidungsnachweis pro Arbeitskomplex mit geprüften SHAs und Verlinkung zur Git-Historie behalten, wenn seine Reproduzierbarkeit dies verlangt; keine alten „Phase gesperrt“-Statusmeldungen im aktiven Dokumentationsbaum.
4. `docs/implementation/m0-report.md` ... `m7-report.md`, Migrationsberichte sowie `docs/migration/` sinnvoll klassifizieren. Historische Berichte haben ein Datum/SHA und dürfen keine aktuellen Testzahlen ersetzen. Lizenz-/Modellherkünfte und Referenzgenerator nicht beschädigen.
5. Auf veraltete Referenzen **in aktiven** Dokumenten und Testskripten prüfen. Historische Referenzen innerhalb ausdrücklich archivierter Abschlussnachweise dürfen auf Git-SHA-Permalinks zeigen. Überflüssige JSON-Evidenzdateien nur entfernen, wenn keine aktuelle Auswertung sie benötigt; Tests/Fixtures/Skripte nicht aus diesem Anlass mitlöschen.
6. `verify_cleanup_docs.py` muss jetzt für aktive Dokumente als **hartes Gate ohne unbekannte Ausnahmen** laufen: relative Markdownlinks, AsciiDoc-Includes, Quarto-Ziele, Verzeichnisse und lokale Codebeispielreferenzen. Ein Negativtest entfernt ein vorhandenes Ziel und muss erwartungsgemäss scheitern.

**Pflichtkriterien:**

| ID | Abnahmebedingung |
|---|---|
| P2-01 | Jede historische Dokumentationsdatei ist klassifiziert und die Entscheidung für Löschen/Archivieren ist begründet. |
| P2-02 | Kein abgeschlossener Agentenprompt, alter Freigabe-/Phasen-Sollzustand oder historisches `AGENTS.md` tritt weiterhin als aktuelle Anweisung auf. |
| P2-03 | Wesentliche technische Verträge, Lizenz-/Herkunftsnachweise und echte spätere Backlogentscheidungen sind weiterhin auffindbar; Git-SHAs nachvollziehbar. |
| P2-04 | Aktiver Dokumentationsbestand wurde messbar vereinfacht, nicht bloss 1:1 in ein neues Verzeichnis verschoben; Vorher-/Nachher-Dateizahlen und Bytes mit Begründung. |
| P2-05 | Alle aktiven internen Links/Includes/Quarto-Ziele bestehen; Ref-/Generierungs-/Distributions-Tests bleiben grün. |
| P2-06 | Standard- und Voll-Maven-Gate plus unabhängiger OpenCode/DeepSeek-Review PASS; B überprüft **gelöschte** Dokumente selbst und dass keine relevanten Informationen verlorengingen. |

### Phase P3 – Phasennamen in Tests/Fixtures, Beispiel-Drift und Gesamtbereinigung

**Ziel:** Fachlich dauerhafte Test-/Fixture-Namen, konsistente Beispiele und keine bekannten Altlasten mehr ausser bewusst belassener Historie. **Produktiven Code nur ändern, wenn zwingend für einen Test-/Modellnamen notwendig.**

**Umsetzung:**

1. `HardeningLocalJobIT` und `jobs/hardening/` mit Descriptor-/Fixture-Referenzen in **fachliche** Namen (z. B. `RuntimeSafetyLocalJobIT`, `jobs/runtime-safety/`) umbenennen. Die Prüfung von Dateiberechtigungen, Timeout, Cancellation, Setupfehlern, ursprünglicher Fehlerursache, Cleanup und Reports bleibt vollständig aktiv. Bei sinnvoller Aufteilung müssen Testfälle/Assertions mindestens gleichwertig erhalten bleiben.
2. INTERLIS-Testmodell `GretlM0Fixture.ili` und **seine `MODEL`-Deklaration** in einen dauerhaften Modellnamen umbenennen (Vorschlag `GretlValidationExample`), dazu `valid.xtf`, `invalid.xtf`, XTF-Header-/Klassen-Tags, Modellpfad, `DistributionIT`, Verpackungsbeispiele und alle Referenzen konsistent anpassen. Testmodell bewusst weiterhin gültiges INTERLIS 2.3; echte EHI-Validierung und Erfolg-/Negativtest nachweisen. **Keine** Änderung an fachlichen Kundenmodellen.
3. `gretl-geotools-probe` als eigenes optionales Profil **behalten**; `m0-points.*`, `gretl_m0_points` und POM-/Javadoc-Phasenetiketten fachlich neutralisieren. CRS, DBF/SHX und Negativklassenpfad-Tests nicht schwächen.
4. Weitere `Probe`, `Fixture`, `Legacy`, `Selected` etc. nur nach dokumentierter Prüfung umbenennen. Beispielsweise `CsvReaderProbeTest` ist möglicherweise ein realer Bibliotheks-Kompatibilitätstest; ein Wort „Probe“ allein ist kein Löschbeweis. Produktive Klassen `Db2Shp`, `Shp2Db` sowie `SourceLocation` in mehreren Modulen **nicht** aus kosmetischen Gründen umbauen.
5. Kanonische Beispielquellen festlegen: identische Skripte möglichst **einmal** pflegen und gezielt in die Distribution kopieren (wenn ohne zusätzlichen Buildballast realisierbar). Mindestens die heutige Duplikatdifferenz von `download-extract/job.groovy` und `project-pipeline/README.md` explizit klären. Unterschiedliche READMEs für Git-Root und Distribution sind **zulässig**, sofern die Differenz durch den Ausführungskontext motiviert ist. Einen deterministischen Drift-Test hinzufügen, der nicht begründete Skript-Duplikate meldet.
6. Globale Restprüfung über **alle produktiven Java-Dateinamen, Testklassen, INTERLIS-/XTF-Modellnamen, aktive Dokumentation, Website, Beispiellinks**. Phasenmarker ausserhalb historischer Nachweise dürfen nur mit expliziter fachlicher Begründung verbleiben (z. B. Fehlerphase `failurePhase`); keine nicht begründeten `M0`-/`Phase2`-/`Hardening`-Bezeichnungen in veröffentlichten Beispielen.
7. Vergleich der aktuellen Operation-IDs/Schema-Tests mit P0-Baseline, laufende Dokumentationsgenerierung, vollständige P3-Regression, SHA-gebundener unabhängiger **kumulativer** Review über P0–P3. Dafür zusätzlich `git diff <initialBaselineSha>..<candidateSha>` bereitstellen und tatsächlich prüfen; nicht nur `git diff <phaseBaseSha>..<candidateSha>`. B muss die Reviewnachweise aller Vorphasen einsehen und in seinem **P3-Bericht sämtliche Kriterien P0-01 bis P3-06 erneut beurteilen**. B kontrolliert insbesondere Nichtziele, die früheren Änderungen und mögliche ausgelassene Altlasten. Abschlussbericht gibt nur tatsächlich verifizierte Aussagen wieder.

**Pflichtkriterien:**

| ID | Abnahmebedingung |
|---|---|
| P3-01 | `Hardening*`-Test/Fixture fachlich neutral; gleiche oder höhere relevante Testabdeckung, keine entfernte Assertion. |
| P3-02 | `GretlM0Fixture` durchgängiger fachlicher Name in `.ili`, `.xtf`, Java-Tests und Distribution; reale positive/negative Validierung PASS. |
| P3-03 | GeoTools-„M0“-Marker bereinigt, optionale Probe weiterhin aktiv und beide GeoTools-Gates nachgewiesen. |
| P3-04 | Nicht begründete Duplikate sind behoben oder dokumentiert; Drift-Prüfung und beide Beispiel-Ausführungskontexte PASS. |
| P3-05 | Statische Restinventur vollständig, alle offenen historischen Bezeichnungen klassifiziert; keine versehentlichen public API-/Schema-/Operation-Änderungen. |
| P3-06 | Vollständige Referenzgenerierung, Link-/Quickstart-Prüfungen sowie Standard-/Voll-Maven-Gates PASS; unabhängiger OpenCode/DeepSeek-Review prüft **kumulativ P0–P3** mit echtem Code-/Doku-Abgleich und gibt PASS. |

---

## 6. Test- und Qualitätsvertrag

### 6.1 Obligatorische Produktgates – in **jeder** Phase, von A und unabhängig erneut von B

```bash
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

- **Kein** `-DskipTests`, `-DskipITs`, `--fail-never`, `-Dtest=...` als Ersatz für diese beiden Gates; kein stillschweigend ausgelassenes Profil, keine Ausnahmen bei fehlendem Docker. Spezial- und schnelle Tests sind **zusätzlich**, nicht alternativ.
- Tests pro Worktree und sequenziell; Evidenz aus dem **ersten** `clean` sichern, **bevor** das zweite `clean` startet.
- Je Lauf: JVM/Maven/OS, SHA, genauer Befehl, Exitcode, Surefire/Failsafe-Zahlen und Skips, Berichte, Artefaktidentität, Zeitstempel, tatsächlicher Testbestand. **Keine** übernommene Testzahl eines früheren Commits als Ist-Nachweis.
- Falls das bestehende Projekt eigene Artefaktaudit-Tools nutzt: Standard `python3 scripts/audit-job-evidence.py --source <candidateSha>` und Voll `python3 scripts/audit-job-evidence.py --full --source <candidateSha>` (sofern weiterhin laut aktueller Projektanleitung gültig) mit ausführen. Nach Phase P2 muss ihr Pfad/Verhalten aktuell bleiben.
- Ein Test, der nur den Text „PASS“ ausgibt, ohne die Anwendung oder das Dokumentationsziel zu prüfen, zählt **nicht** als sachlicher Nachweis.

### 6.2 Zusätzliche deterministische Checks

- `python3 -m unittest discover -s tests/cleanup_review -v` (P0–P3).
- `python3 scripts/verify_cleanup_docs.py` im strikten Modus (spätestens P2–P3), mit unit-getestetem Negativfall für kaputten Link und Quarto-Ziel.
- Dynamisch extrahierte/verglichene öffentliche Operation-IDs und Deskriptor-Schemaakzeptanz; P0-Baseline darf nicht stillschweigend neu geschrieben werden.
- Prüfung der Website-Quickstarts anhand realer Distribution und synthetischer Daten, Ausgabedateien inkl. Inhalt/Exitstatus.
- Umfangskontrolle für jede `git rm`-/`git mv`-Änderung; Referenzen und nachweislich aktuelle Dokumentation nach der Bereinigung.
- Keine unerwarteten CI-/POM-/Pluginänderungen (ausser minimale, geprüfte Anpassungen für kanonische Beispiele und Dokumentationsprüfungen).

### 6.3 Vergleich vorher/nachher

Der P3-Abschlussbericht enthält **keine** subjektive „alles sauber“-Behauptung, sondern:

| Kennzahl / Frage | Pflichtnachweis |
|---|---|
| Anzahl/Bytes aktive Dokumentationsdateien vorher/nachher | maschinell aus Git, getrennt von bewusst historischen Nachweisen |
| Anzahl gezielt gelöschter/archivierter/überarbeiteter Dateien | `cleanup-actions.json` + `git diff --name-status` |
| Restliche Phasenbegriffe in Java-/Testnamen, Fixtures, Website | Kontext- und Pfadliste, begründete Rest-Ausnahmen |
| Operation-IDs/Deskriptor-Schemas vor/nachher | erzeugter maschinenlesbarer Vergleich + Tests |
| Referenzgenerator/Quarto/CLI-Quickstarts | genaue Befehle + echte Ergebnisse |
| Standard-/Volltests | tatsächliche XML-Zählung pro SHA; 0 Failures/Errors, keine unzulässigen Skips |
| Codex-/DeepSeek-Gates | Kandidat-/Review-SHAs, Provider, konkrete OpenCode-Version, unabhängige Belege und Bewertung aller Kriterien |

---

## 7. Review-Fragen: Was Agent B **selbstständig** nachweisen muss

**Nicht ausreichend:** „Build erfolgreich“, „Codex hat umgesetzt“, „Tests existieren“, „Dokumentation klingt gut“, „Phase 2 ist fertig“. Agent B muss für **jedes** Akzeptanzkriterium zeigen, **welche** Änderung das Ziel erreicht, **warum** sie richtig ist, welche Regression sie auslösen könnte und wie diese ausgeschlossen wurde.

Zusätzlich in **jeder** Phase:

1. Exakten `phaseBaseSha..candidateSha` und alle geänderten/gelöschten Dateien selbst inspizieren; relevante Nachbarklassen/Referenzen und aktuelle Ausführungspfade lesen; ungewöhnliche Diff-Lücken melden.
2. Suchen nach **nicht** erledigten Anforderungen und nach unbeabsichtigten Änderungen ausserhalb des Auftrags; mindestens eine konträre, realistische Negativ-/Gegenprobe pro kritischem Themenblock erwägen und nach Möglichkeit ausführen.
3. Dokumentation gegen Source/Tasks/CI prüfen, nicht gegen frühere Markdown-Solltexte; Webseite/Quickstart gegen echte Ausführung bewerten.
4. Gelöschte historische Dateien daraufhin prüfen, ob wesentliche Verträge, künftige Entscheidungen, Testdaten, Lizenz-/Audit-Herkunft oder aktive Referenzen verloren gehen.
5. Testergebnisse **selbst unabhängig erzeugen**; eigene Beobachtungen von Codex' Bericht unterscheiden. Bei `PASS` benennen, welche konkreten Risiken übrig bleiben und warum sie ausserhalb der Phase liegen.
6. In P3 zusätzlich **kumulativer Review** über alle Phasen und ein vollständiger Abgleich mit dem ursprünglichen Altlasten-Inventar – nicht bloss der P3-Diff.

Ein Reviewer darf bei Unsicherheit `FAIL` oder `BLOCKED` melden. Er **muss** `FAIL` ausgeben, wenn ein Pflichtziel nach seiner Prüfung nicht erreicht ist, auch wenn alle Tests grün sind.

---

## 8. Autonome Ausführung und Korrekturschleife für Codex

**Startprompt (direkt in Codex verwendbar):**

> Du bist Agent A im Repository `edigonzales/gretl-runtime`. Implementiere die vollständige Bereinigung gemäss `docs/maintenance/cleanup/spec-v1.md`, vom tatsächlichen aktuellen Checkout aus, **P0 → P1 → P2 → P3**. Alte `docs/spec/AGENTS.md` und abgearbeitete Agentenpläne sind **keine** aktuellen Arbeitsaufträge; behalte die funktionalen Verträge und die invarianten API-/Testanforderungen. Beginne mit einer sauberen Baseline und einem eigenen Arbeitsbranch. Arbeite strikt phasenweise. Nach **jeder** Phase: ausnahmslos beide Maven-Gates und die phasenbezogenen Zusatztests ausführen, Ergebnisse extern sichern, vollständigen Implementierungscommit erstellen; anschliessend **selbständig** `python3 scripts/cleanup_review.py --phase Px --base <phaseBaseSha> --candidate <candidateSha> --spec docs/maintenance/cleanup/spec-v1.md` ausführen. Dieses Skript muss mit der **OpenCode-V2-CLI** im selben verfügbaren Benutzerprofil wie die Desktop-App (V2.2.5; ggf. `OPENCODE_BIN`) den tatsächlichen unabhängigen OpenCode-Prozess mit **DeepSeek-V4.1-Flash-1017 über die direkte DeepSeek API** (`--model deepseek/deepseek-flash`, API-ID `deepseek-flash`) starten, dessen eigene Checks durchführen und den **inhaltlichen** Review samt SHA-Matrix validieren. Der Reviewer darf nicht nur Testergebnisse abnicken. Ohne maschinell validiertes **unabhängiges PASS** keine Folgephase. Bei `FAIL` Findings konkret beheben, neue Tests, neuer Commit, neue OpenCode-Sitzung; bei `BLOCKED` keine Folgephase beginnen und die Ursache nennen. **Vor P0 kein Abbruch allein wegen `python3` 3.9.6 oder fehlender `DEEPSEEK_API_KEY`-Umgebungsvariable**; die bestehende OpenCode-V2-Authentifizierung zuerst prüfen. Falls nur V1-CLI im PATH liegt, eine geeignete V2-CLI isoliert ermitteln, ohne die GUI-Installation zu überschreiben. Nach `PASS` nur den erlaubten Nachweiscommit erstellen, dann nächste Phase. Keine manuellen „Bitte starte OpenCode“-Handoffs, kein eigener Codex-Review als Ersatz, kein Push/Merge/Release. Nach P3 kumulativen unabhängigen PASS und vollständige Restinventur liefern. Halte bestehende Tests, Operation-IDs, Package-Schemas und den Produktcode stabil.

**Konkreter technischer Start, nachdem diese Datei in den Checkout kopiert wurde:**

```bash
# Vorhandene OpenCode-V2-Verbindung prüfen, nicht neuen API-Key verlangen:
opencode --version
opencode auth list
opencode models

# Optional nur falls der Befehl im PATH weiterhin auf V1 zeigt:
# OPENCODE_BIN=/absoluter/pfad/zur/offiziellen/opencode-v2-cli

# Codex implementiert P0 mit dem vorhandenen Python 3.9.6; ein echter
# DeepSeek-Aufruf ist erst vor der verbindlichen P0-Abnahme erforderlich.
python3 --version
```

**Wichtig:** Codex muss bei diesen beispielhaften Befehlen selbst den richtigen V2-CLI-Pfad wählen. Ein sichtbares `opencode --version` von `1.18.22` bedeutet **nicht**, dass die vorhandene Desktop-GUI `2.2.5` veraltet oder neu anzumelden ist. Im Startprompt muss Codex den Zugang bevorzugt über dieselbe gespeicherte V2-Anmeldung qualifizieren.

**Review-Prompt-Template (von `cleanup_review.py` mit echten SHAs/Kriterien erzeugen):**

> Du bist der unabhängige Agent B in OpenCode, **DeepSeek-V4.1-Flash-1017 über den direkten DeepSeek-API-Provider**, OpenCode-Modell `deepseek/deepseek-flash`. Du hast **keine** Implementierungsbeteiligung. Geprüfte Phase: `<PHASE>`, Basis `<FULL_BASE_SHA>`, Kandidat `<FULL_CANDIDATE_SHA>`; Scope und alle Pflichtkriterien stehen in `docs/maintenance/cleanup/spec-v1.md`. Prüfe eigenständig `git status`, `git diff --name-status`, `git diff <BASE>..<CANDIDATE>`, relevante Source-/Test-/Doku-Dateien und insbesondere gelöschte Inhalte. Die beiden Maven-Testgates sind in diesem separaten Worktree bereits unabhängig ausgeführt worden, du sollst ihre echten Berichte lesen und fachliche Zielerreichung zusätzlich selbst prüfen; mache zusätzliche gezielte Checks, wo sinnvoll. Überprüfe **jede** Pflichtanforderung der aktuellen Phase, alle Invarianten und in P3 kumulativ P0–P3. Gib `FAIL`, wenn ein Ziel nicht erreicht ist, auch bei grünen Tests; `BLOCKED` bei unzureichender Evidenz. Ändere keine Datei. Gib als **einzigen endgültigen Reviewtext** zwischen den Markern `GRETL_REVIEW_JSON_BEGIN` und `GRETL_REVIEW_JSON_END` ein vollständiges gültiges JSON gemäss §4.5 aus, mit exakten SHAs, je Kriterium eigener Begründung und prüfbaren Fundstellen. Keine allgemeinen Floskeln, keine erfundenen Testergebnisse, keine Selbstfreigabe des Implementierungsagenten.

**Abschlussausgabe von Codex:** für P0/P1/P2/P3 jeweils Basis-, Kandidaten- und Nachweiscommit, eigene Gates, unabhängiger Reviewer (OpenCode-Version, direkter DeepSeek-Provider, API-Modell-ID `deepseek-flash`, nachgewiesene offizielle Zuordnung zu DeepSeek-V4.1-Flash-1017), konkretes Urteil, überprüfte Ziele und verbleibende Risiken; am Ende Liste aller relevanten Löschungen/Umbenennungen, Link-/Website- und Testnachweise. Kein Schein-PASS bei fehlendem DeepSeek-Key oder nicht ausgeführtem Container-Test.

---

## 9. Nicht verhandelbare Abschlussdefinition

Das Vorhaben ist **erst** abgeschlossen, wenn **P0, P1, P2 und P3 nacheinander und jeweils durch den selbständig gestarteten unabhängigen OpenCode/DeepSeek-Review** freigegeben wurden, die Dokumentation/Website den Ist-Zustand korrekt wiedergeben, historische Dokumente gezielt entfernt oder klar historisiert wurden, fachlich irreführende Namen und ungeklärte Beispiel-Duplikate bereinigt sind, die beabsichtigte Runtime samt Kompatibilität unverändert funktioniert und **beide vollständigen Buildgates für den finalen Kandidaten** in Implementierungs- und unabhängiger Reviewumgebung nachweislich grün sind.

**Fail closed:** Fehlt **irgendein** verpflichtender Nachweis, ist der Status `FAIL` oder `BLOCKED` und Codex beendet **nicht** erfolgreich. Es ist explizit verboten, die Spezifikation, Tests oder Freigaberegeln im Laufe der Implementierung bloss zur Erzeugung eines `PASS` abzuschwächen. Jede sachliche Änderung dieses Lastenhefts erfordert einen separat sichtbaren, begründeten Versionswechsel **vor** ihrer Anwendung.
