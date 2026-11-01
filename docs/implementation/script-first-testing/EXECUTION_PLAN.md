# GRETL Runtime – SCRIPT-first-Integrationstests und echte IMAGE-Qualifikation

**Version:** 1.0  
**Stand:** 1. November 2026  
**Repository:** `edigonzales/gretl-runtime`  
**Analysierte Referenzrevision:** `8eec03184883ce9d585d46a768b647f2b7a19169` (`main`, 31.10.2026). Das ist **kein Reset-Ziel**.  
**Implementierung und Orchestrierung:** Agent A / Codex  
**Unabhängige Abnahme jeder Phase:** Agent B / OpenCode 2.x mit DeepSeek-V4.1-Flash-1017 über die direkte DeepSeek API  
**Ablage:** `docs/implementation/script-first-testing/EXECUTION_PLAN.md`  
**Status:** Ausführlicher Implementierungsauftrag. Die hier beschriebenen neuen Gates, Änderungen und Reviews wurden bei der Erstellung dieses Dokuments **nicht ausgeführt**.

> **Leitentscheidung:** Ein gewöhnlicher GRETL-Job ist eine externe Groovy-Datei oder ein externes Projektverzeichnis. SCRIPT startet diesen Job mit dem tatsächlichen GRETL-Launcher in einem eigenen Prozess. IMAGE startet denselben SCRIPT-Weg in einem generischen GRETL-Runtime-Container. Nur JAR paketiert den konkreten Job. SCRIPT ist der primäre Entwicklungs-, Betriebs- und fachliche Testweg; JAR bleibt eine zusätzlich unterstützte und weiterhin geprüfte Auslieferungsform.

## 1. Verbindlicher Auftrag

### 1.1 Ergebnis

Der vorhandene Testansatz wird weiterentwickelt, nicht ersetzt. Klassische Unit- und Komponententests bleiben bestehen. Hinzu kommt eine konsistente fachliche Prozess-Testmatrix mit denselben Jobs, Testdaten und unabhängig formulierten Erwartungen:

| Modus | Tatsächlicher Start | Wo liegt der Job? | Jobpaketierung beim Vorbereiten? |
|---|---|---|---|
| **SCRIPT** | `<geprüfte Distribution>/bin/gretl run <Jobdatei oder Projekt>` | Als externe Quellen und Ressourcen im Szenarioverzeichnis | **Nein** |
| **IMAGE** | Generisches Runtime-Image; darin `/opt/gretl/bin/gretl run /job/...` | Als schreibgeschützt eingebundene externe Quellen und Ressourcen | **Nein** |
| **JAR** | `java -jar <geprüftes Job-Fat-JAR>` | Vorkompiliert und mit deklarierten Ressourcen im Job-JAR | **Ja, ausschliesslich dieser Modus** |

Die Auslieferung der Runtime darf natürlich selbst Java-Bibliotheks-JARs enthalten. „Kein eingebauter Job“ bedeutet **nicht** „keine JAR-Dateien im Image“. Ebenso darf Maven die Runtime bauen und die Tests starten. Verboten ist der **unnötige Paketierungs-/Buildschritt für den jeweiligen SCRIPT-/IMAGE-Job**.

Das gewünschte Testprinzip des ursprünglichen GRETL bleibt erhalten: Testdaten vorbereiten, einen echten Job mit der Anwenderoberfläche starten und anschliessend die tatsächlichen fachlichen Ergebnisse von aussen prüfen. Die Wahl des Startmechanismus darf keine andere fachliche Erwartung erzeugen.

### 1.2 Harte Invarianten

| ID | Verbindliche Regel |
|---|---|
| INV-01 | SCRIPT ist der Default der gemeinsamen fachlichen Jobtests. IMAGE ist SCRIPT im Container, kein Job-JAR im Container. |
| INV-02 | SCRIPT und IMAGE funktionieren ohne `job-package.json`, ohne `gretl package`, ohne vorab kompilierten Job und ohne Maven/Gradle im **Jobprozess**. |
| INV-03 | Der externe Prozess verwendet die tatsächlich gebaute Distribution; kein Aufruf von `GretlCli.run()` in der JUnit-JVM als Ersatz für einen Prozessnachweis. |
| INV-04 | Der gewöhnliche Jobcode und die fachlichen Assertions sind modusunabhängig. Keine drei voneinander abweichenden Fixturebestände. |
| INV-05 | Alle bestehenden relevanten Unit-, Komponenten-, Fach-, Paket-, Sicherheits-, Abbruch- und Subprojekttests bleiben erhalten oder werden nachweislich gleichwertig übertragen. |
| INV-06 | JAR bleibt unterstützt. Paketdeskriptor-Schemas 1/2/3, Single-Job- und Subprojekt-JARs sowie bisherige Isolationsnachweise werden nicht entfernt. |
| INV-07 | Fehlende Docker-/Image-/Netzwerkvoraussetzungen bei einem angeforderten IMAGE-Gate sind Fehler beziehungsweise ein belegter Review-BLOCKED-Zustand, niemals ein Skip oder SCRIPT-/JAR-Fallback. |
| INV-08 | Ein grünes IMAGE-Gate enthält tatsächlich gestartete GRETL-Container und geprüfte fachliche Ergebnisse. RecordingEngine-/Mocktests allein reichen nicht. |
| INV-09 | Kandidatencommit, ausgeführte Runtime, konkrete Jobeingabe und gegebenenfalls Image-/Job-JAR-Identität sind getrennt nachweisbar. Ein SHA-ähnlicher Text ist kein Nachweis. |
| INV-10 | Abbruch, Timeout, fehlgeschlagener Start, fehlender Runbericht und fachlich erwarteter Fehler bleiben unterschiedliche Zustände. |
| INV-11 | Ein Phasenwechsel erfordert erfolgreiche Pflichtprüfungen **und** einen beendeten unabhängigen OpenCode-Review mit gültigem PASS für genau den Kandidaten. |
| INV-12 | Keine Produktionseingriffe, kein automatischer Push/Merge/Release, keine Veröffentlichung von Images, keine Änderung fremder Arbeitsstände oder globaler Credentials. |

### 1.3 Nichtziele und Begrenzung

Keine neue GRETL-Runtime, kein neuer Scheduler, keine zusätzliche Pipeline-DSL, keine Task-SPI-Neuentwicklung und kein neues generisches Testframework. Keine Kubernetes-/OpenShift-Installation, kein Jobportal, keine Native-Image-Umsetzung und keine Änderung fachlicher Operationen ausser einem **reproduzierten, für diesen Auftrag notwendigen** Fehlerfix. Keine beiläufigen Versionsupgrades von Java, Groovy, Maven, Testcontainers oder Fachbibliotheken.

Keine umfassende Bereinigung historischer Dokumente, kein neues SBOM-/Supply-Chain-Projekt und keine vollständige Neuorganisation aller Testnamen. Neue Klassen und Methoden erhalten fachliche Namen, keine `Phase3Test`-/`TST2Helper`-Namen. Bestehende Namen dürfen bei notwendiger Aufteilung gezielt verbessert werden, nicht als eigenständige Nebenbaustelle.

Es wird eine kleine Testausführungsinfrastruktur im vorhandenen Testmodul weiterentwickelt. Sie darf nicht in die produktive Java-SPI oder in ausgelieferte Taskmodule gelangen.

### 1.4 Verhältnis zum mitgelieferten funktionierenden Beispiel

Übernommen werden dessen Rollenaufteilung, autonome Weiterarbeit nach PASS, exakter Kandidatencommit, unveränderte Phasenbasis bei Nacharbeit, eigener Reviewworktree, unabhängige Testausführung, eigene Gegenprobe, direkte DeepSeek-Anbindung und die drei Urteile PASS/FAIL/BLOCKED. [U1–U3]

**Nicht übertragen werden compilerfremde Anforderungen** wie INTERLIS-Compiler-Normprofile, Safari-/SDK-Abnahmen, Gradle-Tasknamen, das Office-Modell oder der dortige macOS-Python-Guard. GRETL besitzt bereits `scripts/audit-job-evidence.py`; dieser Prüfweg bleibt nutzbar. Es gibt in diesem Auftrag **kein neu erfundenes Pythonverbot** und keinen Auftrag, bestehende Pythonwerkzeuge allein aus formalen Gründen umzuschreiben. Neue Revieworchestrierung möglichst mit vorhandenen Mitteln oder einem kleinen Shell-/Node-Helfer, ohne globale `pip`-Installation und ohne neue Agentenplattform. Falls der aktuelle Checkout inzwischen verbindliche weitergehende Regeln enthält, diese lesen und den konkreten Konflikt klären statt still einen Guard abzuschalten.

## 2. Ist-Zustand und Einstieg in den aktuellen Checkout

### 2.1 Gelesene Befunde am Referenzcommit

| Fundstelle | Tatsächlicher Ausgangszustand | Folgerung für den Auftrag |
|---|---|---|
| `ArtifactJobRunner.Mode` | Kennt `JAR` und `IMAGE`; `prepare(Request)` verwendet ohne Property JAR. | SCRIPT als echten Modus einführen; Default ändern. |
| `ArtifactJobRunner.prepare(Request, Mode)` | Der Nicht-IMAGE-Weg erstellt beziehungsweise verwendet ein Job-JAR. | SCRIPT muss vor jeder Paketlogik abzweigen und ohne Paketdeskriptor auskommen. |
| `ArtifactJobRunner.start(Prepared, boolean script, ...)` | SCRIPT ist ein zusätzlicher Boolean nach der Vorbereitung. | Mehrdeutige Kombination „JAR vorbereitet, aber SCRIPT gestartet“ beseitigen. |
| `ArtifactJobRunner.Request.scriptInput` | Unterstützt bereits `job.groovy` und `.` für Projekte. | Subprojekte nicht als noch fehlendes Produktfeature behandeln; die Testoberfläche darauf aufbauen. |
| `ImageExecutionAdapter.prepare(...)` | Baut bereits `... imageId, "run", "/job/job.groovy"` beziehungsweise `"/job"` und verwendet den Produktlauncher. | Der vorhandene IMAGE-Entwurf ist bereits grundsätzlich SCRIPT-basiert. Bewahren und **wirklich ausführen**, nicht unnötig neu erfinden. |
| `ProjectScriptLocalJobIT` | Startet den Distributionslauncher separat, ohne Projektpaketierung; besitzt eigenen Prozess-/Evidenzcode. | Vorhandenen echten SCRIPT-Nachweis erhalten und die doppelte Infrastruktur vorsichtig zusammenführen. |
| `ProjectArtifactLocalJobIT` | Mischt fachliche SCRIPT/JAR-Vergleiche und echte Paketstruktur-/Bytecodetests; einzelne SCRIPT-Versuche beginnen mit `prepare(..., JAR)`. | Methoden einzeln klassifizieren; Fachparität von Paketprüfungen trennen. |
| `scripts/audit-job-evidence.py` | Lehnt IMAGE im bisherigen Pflichtgate ab; zählt positive Operationen nur aus JAR-Läufen. | Audit zusammen mit Moduswechsel aktualisieren, nicht umgehen oder bloss die IMAGE-Sperre löschen. |
| `docs/implementation/testing.md` | Standardprüfung und Backing-Service-Prüfung; IMAGE bisher nicht reguläre Pflichtmatrix. | SCRIPT-first und echte IMAGE-Abnahme in aktive Dokumentation und CI übernehmen. |
| `gretl-dist/.../bin/gretl` | Shelllauncher mit `exec java`, tatsächlichem Runtime-/Task-/Extension-Classpath. | Diesen Launcher testen; keine testseitige Nachbildung des Java-Kommandos im SCRIPT-Modus. |
| `.github/workflows/publish-snapshots.yml` | Führt Standard- und Vollprüfung sowie Artefaktaudit aus; archiviert Evidenz. | Vorhandene Gates erhalten und um modespezifische Qualifikation ergänzen. |

Diese Aussagen sind Quellcodebefunde, **keine Behauptung hier ausgeführter erfolgreicher Tests**. [Q1–Q8]

Wichtig: Nicht sämtliche heutigen SCRIPT-Tests hängen an der Paketierung. `ProjectScriptLocalJobIT` ist ein bereits vorhandener unabhängiger Prozessweg. Der Auftrag beseitigt die Abhängigkeit im gemeinsamen Runner und macht die Prüfung breit und konsistent.

### 2.2 Arbeitsbeginn – Teil von Phase TST1

1. `pwd`, Repository, `git status --short`, vollständigen HEAD und Zweig feststellen; `AGENTS.md` und die aktuellen Regeln der betroffenen Pfade lesen. Lokale Subprojekt-/Bereinigungsänderungen nicht zurücksetzen und nicht ungeprüft mitcommitten.
2. Der Referenzcommit ist Orientierung. Neuere, bereits korrekte Funktionen nachweisen und wiederverwenden. Keine alten Klassen aus diesem Plan wiederherstellen, falls ein laufendes Refactoring sie bereits gleichwertig ersetzt hat.
3. Den Auftrag auf einem eigenen geeigneten Arbeitszweig beziehungsweise klar abgegrenzten aktuellen Zweig bearbeiten. Bei noch laufenden fremden Änderungen an denselben Dateien keine konkurrierenden Eingriffe. Über einen dokumentierten Integrationsstand weiterarbeiten; tatsächliche unvermeidbare Konflikte als Blocker benennen.
4. Nur die betroffenen Test- und Startwege inventarisieren: Aufrufer von `ArtifactJobRunner`, direkte `ProcessBuilder`-Starts, Pakettests, Projektfixtures, vorhandene Image-Build-/Reviewhelfer, aktive CI und Audit. Eine kurze Delta-Tabelle genügt.
5. Java-/Maven-Version, die tatsächlichen Maven-Profile und Testeinschlüsse erfassen. Am gelesenen Stand sind Java 25 und der Wrapper vorgesehen; für Änderungen gelten die aktuellen Repositorypins.
6. OpenCode-/Provider-Preflight nach §12 **vor** grösserer Implementierungsarbeit durchführen. Dockerverfügbarkeit und lokale Daemonanbindung ebenfalls früh prüfen; ein echter IMAGE-Lauf wird spätestens TST3 Pflicht.
7. Bestehende Standardprüfung und, bei vorhandener Dienstumgebung, Vollprüfung als Baseline ausführen. Vorhandene Fehler reproduzierbar abgrenzen. Keine erfundenen grünen Baselines oder still deaktivierten Altfehler.
8. `STATUS.md` mit Auftragsbasis-SHA, aktueller Phase und nächstem Schritt anlegen. Keine zusätzliche lange Phase 0 und kein gesonderter administrativer Vorbereitungsreview.

## 3. Testebenen und gemeinsame Matrix

### 3.1 Die Ebenen dürfen nicht verwechselt werden

| Ebene | Zweck | Zählt als echter fachlicher Prozessnachweis? |
|---|---|---|
| Unit-/Komponententest | Parser, Planner, Konversion, einzelne Operation, Prozess-/Mountvertrag mit RecordingEngine | Nein |
| CLI in Test-JVM | Zusammenspiel über `GretlCli.run()` | Nein; weiterhin wertvolle Komponentenprüfung |
| SCRIPT-Prozesstest | Tatsächlicher Launcher, externe Groovy-Quellen, eigene JVM | Ja |
| IMAGE-Prozesstest | Tatsächlicher generischer Produktcontainer, darin SCRIPT | Ja |
| JAR-Prozesstest | Regulärer Packager und frischer `java -jar`-Prozess | Ja |
| Unabhängiges LLM-Review | Zielerreichung, Diff, selbst ausgeführte Tests und eigene Gegenproben | Eigenes Freigabegate, kein Ersatz für Prozess-/Fachtests |

Der JUnit-Prozess richtet Dienste und Daten ein und prüft Ergebnisse. Der Jobprozess besitzt **keinen Testclasspath**. Datenbank-/Dateileser auf der Testseite dürfen sich geeigneter Bibliotheken bedienen, müssen die erwarteten Werte jedoch unabhängig von der gerade getesteten GRETL-Operation bestimmen.

### 3.2 Testfamilien

Jede vorhandene Testmethode beziehungsweise stabile Szenariovariante erhält bei der kleinen Anfangsinventur genau eine nachvollziehbare Zuordnung:

- **COMMON:** Gleicher fachlicher Job und gleiches fachliches Orakel in SCRIPT, IMAGE und JAR. Dazu gehören die bisherigen fachlichen Operationstests, Erfolgs-/Fehlerfälle und gemeinsame Subprojektsemantik.
- **SCRIPT_CONTRACT:** Nur für Quellenstart sinnvolle Verträge, etwa Lauf ohne Paketdeskriptor, Scriptänderung ohne Paketierung und Groovy-Syntaxfehler zur Startzeit. Soweit im Container gleich relevant, in SCRIPT **und IMAGE**.
- **IMAGE_CONTRACT:** Tatsächliche Mountrechte, UID, Imageidentität, Container-Signalweg und Jobwechsel mit demselben Image.
- **PACKAGE_CONTRACT:** JAR-Inventar, Descriptorversionen, Klassen-/Ressourcen-/Servicezusammenführung, beschädigte JARs, gelöschte Quellen nach Paketierung, explizite Modulauswahl. Immer ausdrücklich JAR.
- **COMPONENT:** Bestehende in-process Tests; keine Umdeklaration zum Prozessnachweis.

COMMON-Testmethoden verwenden den global gewählten Testmodus. PACKAGE_CONTRACT-Methoden wählen JAR explizit. Ein IMAGE-Matrixlauf darf deshalb zusätzlich korrekt bezeichnete Paket-JAR-Tests enthalten; diese dürfen aber **keinen fehlenden IMAGE-Nachweis ersetzen**.

Keine COMMON-Methode darf intern je nach Plattform auf einen anderen Modus wechseln. Keine stillen `assumeTrue(dockerAvailable)`-Skips bei angeforderter IMAGE-/Backing-Service-Matrix.

### 3.3 Umfang der fachlichen Parität

Die vollständigen Matrixläufe müssen den gesamten vorhandenen, gemeinsamen fachlichen Szenariobestand abdecken. Keine Reduktion von IMAGE auf `--help` oder einen einzigen Download und keine dauerhafte Reduktion von SCRIPT auf drei repräsentative Fälle.

Am Referenzstand gibt es 44 Standardoperationen. Massgeblich sind die konkreten Operations-IDs aus dem vorhandenen Inventar, abgeglichen mit der tatsächlich gebauten Standarddistribution. **Nicht** nur `size == 44` prüfen; **nicht** den erwarteten Bestand aus den erfolgreich gelaufenen Operationen ableiten. Neue tatsächlich beauftragte Operationen im aktuellen Checkout zusätzlich berücksichtigen; keine alte Zahl als Obergrenze.

Für jeden der drei vollständigen Modi gilt separat: Alle geforderten COMMON-Szenarien sind ausgeführt, die zugehörigen Assertions erfolgreich und alle erwarteten Standardoperationen besitzen positive fachliche Nachweise. Die Vereinigung der drei Modusmengen genügt nicht.

## 4. Zielstruktur der Testinfrastruktur

Die folgenden neuen Typen sind **Entwurfsvorgaben**, nicht die Behauptung bereits vorhandener APIs. Gleichwertige aktuelle Helfer bevorzugen. Kleine Abweichungen sind zulässig, wenn sie vor Umsetzung dokumentiert werden und dieselben Verträge/Testfälle erhalten; kein freies Redesign und keine zusätzliche öffentliche Produktschnittstelle.

### 4.1 Arbeitsorte

```text
# Bestehendes Testmodul; neue Helfer ausschliesslich testseitig:
gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/
  ArtifactJobRunner.java                 # erweitern, kein zweiter Runner
  ScriptExecutionAdapter.java            # neu: echter Quellenstart
  JarExecutionAdapter.java               # ausgelagerter bisheriger Paketweg
  ImageExecutionAdapter.java             # erhalten/gezielt erweitern
  CandidateDistribution.java             # geprüfte tatsächliche Distribution
  JobInputSnapshot.java                  # bytebasierte Testinput-Identität
  JobExecutionEvidence.java              # kleine gemeinsame Evidenzschicht
  ... bestehende Fachtests und Fixtures ...

gretl-integration-tests/src/test/resources/qualification/
  scenarios.json                        # kleiner deklarativer Sollbestand

# Nur soweit im aktuellen Checkout nicht bereits gleichwertig vorhanden:
gretl-dist/src/main/docker/Dockerfile
scripts/build-runtime-image.sh
scripts/verify-job-matrix.sh
scripts/audit-job-evidence.py             # erweitern statt parallel duplizieren

# Auftrag und kurze Nachweise:
docs/implementation/script-first-testing/
  EXECUTION_PLAN.md
  CODEX_STARTPROMPT.md
  OPENCODE_REVIEW_PROMPT.md
  README.md
  STATUS.md
  implementation/TST1.md ... TST4.md
  reviews/TST1-r1-<shortsha>.md ...
```

Keine Pflicht zu einer separaten Datei für jeden kleinen Record. Records und Enums dürfen verschachtelt bleiben. Keine generische Transport-Plugin-SPI, kein Dependency-Injection-Container für Tests und keine neue JUnit-Engine.

### 4.2 `ArtifactJobRunner.Mode`

```java
enum Mode {
    SCRIPT, IMAGE, JAR;
    static Mode configured();
    static Mode parse(String value);
}
```

`configured()` liest `gretl.test.executionMode`, Default **SCRIPT**. `parse` akzeptiert die dokumentierten Werte, meldet ungültige oder leere explizite Werte verständlich und fällt niemals auf JAR zurück. Ein fehlender Wert und ein explizit ungültiger Wert sind verschieden. Kein nachträgliches Verändern der globalen Property zwischen einzelnen Tests; ein gewählter Matrixmodus gilt für den jeweiligen Test-JVM-Lauf.

Die Modusproperty ist eine **Testkonfiguration**, keine neue Option der produktiven GRETL-CLI.

### 4.3 `JobInput` und `Invocation` im Runner

```java
enum InputKind { SCRIPT_FILE, PROJECT_DIRECTORY }
record JobInput(InputKind kind, String relativePath) {
    static JobInput script(String relativeFile);
    static JobInput project(String relativeDirectory);
    Path resolveAgainst(Path stagedRoot);
}

enum Verb { RUN, CHECK, PLAN }
record Invocation(Verb verb, boolean help, String graphFormat, Path graphOutput) {
    static Invocation run();
    static Invocation check();
    static Invocation plan();
    static Invocation plan(String format, Path output);
    static Invocation jobHelp();
}
```

`JobInput.script("job.groovy")` und `JobInput.project(".")` ersetzen die bisherigen indirekten `scriptInput`-/Boolean-Absprachen. Andere gültige Scriptdateinamen und eine explizite Scriptdatei unterhalb einer Projektwurzel sind zulässig; nicht überall `job.groovy` hartcodieren. Pfade sind relativ zum eigenen gestagten Inputroot, normalisiert und dürfen diesen nicht verlassen. Eine absolute Hostdatei wird nicht als scheinbar portabler Fixturepfad akzeptiert.

Der Helfer prüft **nicht** Groovy-Syntax oder die inhaltliche Gültigkeit von `settings.gretl`. Fehlende ausgewählte Dateien, ungültige Settings und Syntaxfehler müssen als negative Prozessfälle den wirklichen Launcher erreichen können. Ein bestehender Fixture-/Mountroot ist davon getrennt: Der Test darf keine fremden Hostverzeichnisse implizit einbinden.

`Invocation` bildet lediglich vorhandene CLI-Funktionen ab. Kombinationen werden validiert: Graphformat/-ausgabe nur bei PLAN; Jobhilfe führt keine Taskarbeit aus. Für JAR nur die tatsächlich vorhandenen Abbildungen anbieten, etwa RUN, `--plan` und `--help`; ein nicht vorhandenes JAR-CHECK nicht emulieren. Globale Hilfe/Version bleiben einfache Distributionstests, nicht zwingend Teil dieser gemeinsamen Job-API.

### 4.4 `Request`, `Prepared`, `Running`, `Result`

Die bisherigen Parameter-/Environment-/Config-/Endpoint-/Timeout-Felder erhalten. Erweiterter Zuschnitt:

```java
record Request(
    String id, Path jobDirectory, Path directory,
    List<String> targets, Map<String, ?> parameters,
    Map<String, ?> environment, Map<String, Object> config,
    Map<String, Endpoint> endpoints, Duration timeout,
    JobInput input, Invocation invocation) { }

record Prepared(
    Request request, Mode mode, Path source, Path report,
    JobInputSnapshot inputSnapshot, JobLaunch launch) { }

record Running(
    Prepared prepared, Process process, Path out, Path err,
    Instant started, long startedNanos) { }

record Result(
    int code, String out, String err, Path report, Path directory,
    Duration duration, boolean timedOut, boolean aborted,
    boolean forciblyKilled, Mode mode,
    ExecutionIdentityProof identity) { }
```

`ExecutionIdentityProof` ist ein **testinterner** Typ, nicht `runtime.api.ExecutionIdentity`. Er trennt Distribution, Jobeingabe und optional Job-JAR beziehungsweise Image (§9).

`JobLaunch` darf als kleiner sealed Typ mit `ScriptLaunch`, `JarLaunch`, `ImageLaunch` umgesetzt werden. Er enthält die vollständig vorbereiteten Startargumente, die zulässige Environment und die modespezifische Identität; `ImageLaunch` kann die vorhandene `ImageExecutionAdapter.Launch` kapseln. Kein beliebiges Transportpluginmodell.

Verhindere widersprüchliche Zustände: Ein SCRIPT-Prepared besitzt weder ein Job-JAR noch einen Image-Launch; IMAGE besitzt ein Image und einen externen Jobpfad, kein Job-JAR. Die Modusangabe stammt aus dem tatsächlich vorbereiteten Launch, nicht aus einer später auswertbaren Property.

Die Requestwerte defensiv kopieren. `Path` in lokalen Parameter-/Configwerten bis zur jeweiligen Startgrenze als Typ erhalten. Verschachtelte Konfiguration nicht durch vorzeitige JSON-Roundtrips in rohe Strings verwandeln. Keine Konfigurations-/Secretwerte in `toString()`-/Diagnosedumps ausgeben.

### 4.5 Methoden von `ArtifactJobRunner`

| Methode | Verantwortung und Postbedingung |
|---|---|
| `prepare(Request)` | Delegiert an `prepare(request, Mode.configured())`. |
| `prepare(Request, Mode)` | Erst gemeinsame eigene Arbeitsumgebung; danach exakt ein Modusadapter. SCRIPT/IMAGE berühren keine Paketierungslogik. |
| `prepareWorkspace(Request)` | Kopiert normale Fixturedateien in den eigenen Sourcebereich; erstellt getrennte CWD-/Work-/Output-/Reportbereiche. Kein In-place-Run im Originalfixture. |
| `start(Prepared)` | Startet genau den vorbereiteten Launcher/Prozess; schreibt Startmetadaten. Kein `boolean script`, kein heimliches zweites Java-Kommando. |
| `run(Request)` / `run(Request, Mode)` | Prepare → Start → Finish mit verlässlichem Cleanup auch bei Exception. |
| `finish(Running, boolean cancellationRequested)` | Wartet begrenzt; unterscheidet Timeout/Cancel/Force-kill/normalen Exit; sichert vollständige Ausgaben und tatsächliche Identitäten. |
| `cancel(Running)` | Hostprozess kontrolliert beenden beziehungsweise echten Container stoppen; kein `docker kill` als alleiniger Normalweg. |
| `stop(Running)` | Begrenzter kontrollierter Abbruch, danach nötigenfalls forcierter Abbruch und Aufräumen der eigenen Ressourcen. |
| `stop(Process)` | Keine unbeschränkten `waitFor()`-Aufrufe nach Force-kill; Prozess-/Kindprozesszustände explizit erfassen. |
| `process(List<String>, Path, Map<String, ?>, String)` | Gemeinsame sichere Host-Prozesskonstruktion mit bereinigter Environment und Dateiumleitung statt ungeleerter Pipes. |
| `jsonValue(Object)` | Erst an der JSON-/Startgrenze `Path` serialisieren, fachliche Strings unverändert lassen. |

Alle Aufrufer von `start(prepared, true/false)` und die alten widersprüchlichen Prepared-Konstruktionen migrieren. Keine bleibenden Boolean-Kompatibilitätsmethoden, die den Modus wieder unterlaufen. Kleine vorhandene Request-Komfortkonstruktoren dürfen eindeutig auf SCRIPT-Datei/RUN abbilden.

Eine `prepare`-Exception, ein nicht gestarteter Prozess und ein fachlich ungültiger Job sind nicht dasselbe. Der Harness darf einen erwarteten Produktfehler nicht schon vorher durch Einlesen des Projektmodells oder Paketdeskriptors abfangen.

### 4.6 `CandidateDistribution`

```java
final class CandidateDistribution {
    static CandidateDistribution resolve();
    Path root();
    Path launcher();
    String sourceRevision();
    String manifestSha256();
    void verifyFiles();
    void requireRevision(String expectedSha);
}
```

Der Default verweist auf das **in diesem Maven-Reactor frisch gebaute** Distributionsartefakt. Den konkreten Archivpfad über Maven aus Projektversion und Buildverzeichnis an die Tests übergeben; nicht irgendein erstes passendes ZIP aus einem alten Verzeichnis verwenden. Die Distribution einmal in einen eigenen Testbereich entpacken und ihre Dateien gegen `SHA256SUMS`/Katalog prüfen. Alternativ ist für bestehende Prüfpfade die frische `target/dist`-Installation zulässig, sofern dieselbe Artefaktprüfung und eindeutige Reactorzuordnung besteht; mindestens ein Pflichtfall startet die tatsächlich entpackte ZIP-Distribution ausserhalb des Checkouts.

Ein expliziter Testoverride wie `gretl.test.distribution` ist nur zulässig, wenn der Test die Provenienz und tatsächlichen Bytes genauso prüft. Niemals ein zufälliges `gretl` aus PATH oder die globale Benutzerinstallation verwenden. Der Launcher muss vorhanden und auf der geprüften Plattform startbar sein.

Für verbindliche Kandidatengates gilt: `sourceRevision == candidateSha`, `sourceDirty == false`. Entwicklungsläufe dürfen als dirty dokumentiert laufen, erfüllen aber kein Commit-/Reviewgate. Keine Überschreibung von Provenienzfeldern, um einen dirty Build als sauber auszugeben.

Ein Test, der absichtlich eine beschädigte Distributionskopie untersucht, verwendet eine eigens gekennzeichnete negative Kopie und darf die kanonische Kandidatendistribution nicht verändern.

### 4.7 `ScriptExecutionAdapter`

```java
final class ScriptExecutionAdapter {
    static JobLaunch prepare(
        Request request, Path stagedSource, Path report,
        CandidateDistribution distribution);
    static List<String> arguments(
        Request request, Path stagedSource, Path report, Path launcher);
}
```

`prepare` validiert die technische Distribution, baut den echten CLI-Aufruf und liefert einen SCRIPT-Launch. Kein Aufruf von `JobPackager`, kein Descriptorparser, kein temporäres Job-JAR und kein Groovy-Kompilieren in der Test-JVM.

`arguments` verwendet eine `List<String>` und bildet den vorhandenen Bedienvertrag ab, beispielsweise:

```text
/prüfbereich/distribution/bin/gretl
run
/prüfbereich/source/job.groovy
--config
/prüfbereich/runtime.json
--report
/prüfbereich/reports/report.json
--log-format
json
--param
output=/prüfbereich/output/Resultat mit Leerzeichen.txt
```

Ein Argument bleibt ein Argument. Keine Stringverkettung mit Shellquoting und kein `sh -c "gretl ..."`. Für ein Projekt wird das gestagte Projektverzeichnis übergeben. Für CHECK/PLAN/Jobhilfe nur die dort tatsächlich erlaubten Optionen setzen; insbesondere keine Run-only-Optionen aus Bequemlichkeit anhängen.

**Verbindliche Gegenbeweise gegen versteckte Paketierung:**

- Erfolgreicher Lauf mit Jobquelle und Ressourcen, aber ohne `job-package.json`.
- Erfolgreicher Lauf mit absichtlich ungültigem, unbenutztem `job-package.json` in einer isolierten Kopie. Der SCRIPT-Start darf ihn nicht lesen.
- Eine Unit-/Harnessprobe mit einer absichtlich fehlschlagenden Paketierungsgrenze zeigt, dass der SCRIPT-Pfad sie nie aufruft.
- Mindestens ein echter Quellenlauf mit einer funktionsfähigen minimalen Runtime-Environment ohne Maven/Gradle. Der Maven-Prozess, der die Tests ausführt, bleibt davon unberührt. Der Shelllauncher benötigt weiterhin seine tatsächlichen Standardwerkzeuge, etwa `dirname`; kein künstlich leerer PATH, der schon vor Java scheitert.
- Jobquelle zwischen zwei eigenständigen Läufen gezielt ändern: neue fachliche Ausgabe bei unveränderter Runtime-Identität, ohne Paketierung oder Runtime-Neubau.

Der letzte Fall prüft einen geänderten Job bei gleichem Programm. Er verlangt keine unsichere Änderung von Quellen **während** eines laufenden Jobs.

### 4.8 `JarExecutionAdapter`

```java
final class JarExecutionAdapter {
    static JobLaunch prepare(
        Request request, Path stagedSource, Path report,
        CandidateDistribution distribution);
    static Path packageJob(Request request, Path stagedSource,
                           CandidateDistribution distribution);
    static void verifyPackage(Path jar, Path descriptor);
}
```

Übernimmt den bisherigen echten Packagerweg. Bestehende qualifizierte Modulauswahl, Dependency-/Service-/Ressourcenprüfungen und der getrennte `java -jar`-Prozess bleiben. Eine explizite PACKAGE_CONTRACT-Prüfung darf auch in einem SCRIPT-Maven-Lauf JAR wählen; das wird korrekt als JAR protokolliert.

Soweit weiterhin ein Defaultdeskriptor für einfache **JAR-Testfixtures** erzeugt wird, geschieht dies ausschliesslich hier. Projektpakete brauchen den gültigen entsprechenden Deskriptor. Die Fachtests sollen vorzugsweise ihre vorhandenen expliziten Deskriptoren behalten; keine neuen Paketpflichten für SCRIPT daraus ableiten.

Die Testcacheoptimierung für bytegleiche Pakete darf bleiben, aber der Schlüssel muss mindestens die relevanten Job-/Descriptorbytes **und** die tatsächliche Kandidatendistribution/Packageridentität erfassen. Ein Paket aus anderer Runtime, geändertem Descriptor oder geändertem Input darf nicht wiederverwendet werden. Keine Hardlinks teilen, die eine negative JAR-Mutationsprobe nachträglich in-place beschädigen kann. Mutationsfälle erhalten eine echte eigene Kopie beziehungsweise Copy-on-write mit nachgewiesenem Verhalten.

Ein expliziter Mutationsweg wie `prepareExistingJar(request, jar, ...)` ist als kleiner testinterner Helfer zulässig. Er hasht **das tatsächlich gestartete manipulierte JAR** vor Start und kennzeichnet den negativen Zweck. Keine nachträgliche Umschreibung der Referenzdatei, nur damit der Audit wieder einen alten Pfad findet.

### 4.9 `JobInputSnapshot`

```java
record JobInputSnapshot(String treeSha256, List<InputFile> files) {
    static JobInputSnapshot capture(Path root);
    void verifyUnchanged(Path root);
}
record InputFile(String relativePath, long size, String sha256) { }
```

Dieser Helfer ist ein bytebasierter Testinput-Nachweis. Er darf nicht `GretlProjectParser.parse()` aufrufen und keine Groovy-/Settingsdatei ausführen. Auch inhaltlich ungültige Fixtures müssen fingerprintbar sein.

Dateien nach normalisierten relativen Pfaden sortieren, UTF-8 und eine eindeutig abgegrenzte Kanonisierung verwenden, etwa eine versionierte Liste aus Pfad, Länge und SHA-256. Keine ungetrennte Verkettung `path + content`. SQL, Modelle, Templates, Helfer und Subprojektskripte gehören in den Inputbestand, nicht bloss die Rootdatei.

`job-package.json` wird als Paketierungsmetadatum separat erfasst. Für die gemeinsame fachliche Inputidentität darf es ausgeschlossen werden, damit SCRIPT ohne Descriptor und JAR mit demselben fachlichen Input vergleichbar bleiben. Diese eine Ausnahme explizit dokumentieren; keine generelle Ausblendung unbekannter Ressourcen. Laufzeitkonfiguration, Secretdateien und Ausgaben liegen **ausserhalb** des Sourcebestands.

Normale Fixtures enthalten keine Symlinks. Der Kopier-/Inventarcode folgt niemals still einem Link aus dem eigenen Testbereich. Sicherheitsnegative Proben mit Links werden ausdrücklich in einer isolierten Scratchumgebung aufgebaut und dürfen diese Grenze gezielt testen, nicht beliebige Hostdateien inventarisieren.

Snapshot unmittelbar vor Start erfassen und nach Ende erneut vergleichen, wo die Quelle ein Lesebestand ist. Eine absichtlich veränderte Quelle zwischen zwei Starts führt zu zwei unterschiedlichen Snapshots. Die fachliche Runtime-Provenienz im Runbericht kann einen engeren bestehenden Hashvertrag haben; nicht ohne Auftrag durch diesen Testtreehash ersetzen.

### 4.10 `JobExecutionEvidence`

```java
final class JobExecutionEvidence {
    static void writeStarted(Prepared prepared, Instant started,
                             String suiteRunId, String testCaseId);
    static void writeCompleted(Running running, Result result);
    static void writePreparationFailure(Request request, Mode mode,
                                        Throwable cause, String stage);
    static void redactOutputs(Request request, Path stdout, Path stderr);
}
```

Ein gemeinsamer kleiner Writer ersetzt die voneinander abweichenden Prozessmetadaten in `ArtifactJobRunner` und `ProjectScriptLocalJobIT`. Er erzeugt keine fachlichen Erfolgsaussagen. Ein JUnit-Fachtest und der Audit müssen die erwarteten Ergebnisse zusätzlich nachweisen.

Die bisherige Secretredaktion erhalten und auf alle gemeinsam archivierten Ausgaben anwenden. Keine rohe Config-/Environment-/Credentialdatei als CI-Artefakt kopieren. Synthetische Tests mit Klartextsecret-Konfiguration bleiben negative Produktverträge; ihr künstliches Secret darf dennoch nicht in archivierten Logs stehen.

Die Abschlussdatei atomar beziehungsweise erkennbar vollständig schreiben. `STARTED` ohne vollständigen Abschluss, ein fehlender Pflichtreport oder ein nicht beendeter Prozess ist niemals ein erfolgreicher Testbeleg.

## 5. IMAGE: generische Runtime und externer SCRIPT-Job

### 5.1 Verbindlicher Produktcontainervertrag

Das Image wird aus der **aktuellen Kandidatendistribution** hergestellt. Es enthält die Runtime, Standardbibliotheken, erforderliche Betriebssystembibliotheken und den regulären Launcher. Es enthält **nicht** den konkreten zu testenden beziehungsweise zu betreibenden Job als vorab kompilierten Einstiegspunkt.

Erforderlicher Startvertrag:

```text
Image-Entrypoint: /opt/gretl/bin/gretl
Containeraufruf:  run /job/job.groovy
Projektaufruf:   run /job
```

Der Dockerfile-Entrypoint wird in Exec-Form gesetzt. Der vorhandene Shelllauncher verwendet `exec` für Java; Signalweitergabe nicht durch einen neuen Shellwrapper verschlechtern. Vorhandene gleichwertige Imagefiles im aktuellen Checkout verwenden. Fehlt noch ein generischer Runtime-Imagebuild, einen kleinen konventionellen Dockerfile unter `gretl-dist/src/main/docker/` ergänzen; kein Maven-/Gradle-Build beim Containerstart.

Weder `java -jar /job/job.jar` noch ein Startupschritt `gretl package ... && java -jar ...` erfüllt IMAGE. Auch ein Runtime-Image pro Job erfüllt den geforderten Wiederverwendungsnachweis nicht.

Mitgelieferte allgemeine Distributionsbeispiele dürfen Bestandteil der normalen Distribution bleiben. Die Aussage „kein eingebauter Job“ verbietet den **testszenario-/kundenspezifischen** Einstieg, nicht jede `.groovy`-Beispieldatei einer Standarddistribution. Der Beweis erfolgt durch einen erst nach dem Imagebuild erzeugten externen Job mit eigenem Inhalt.

### 5.2 Imagebau und Identität

`scripts/build-runtime-image.sh` ist ein kleines Authoring-/CI-Werkzeug, kein Teil des normalen Jobstarts. Es muss:

1. Eine eindeutig bezeichnete frische Distribution und ihren Quellen-SHA entgegennehmen beziehungsweise aus der Maven-Ausführung erhalten.
2. Distribution und Prüfsummen vor dem Bauen prüfen; nicht anhand eines Tags eine passende Runtime vermuten.
3. Einen minimalen Buildkontext unter eigenen Buildausgaben erstellen. Kein `COPY . .` über das ganze Repository, keine `.git`, Testfixtures, Credentials, Maven-Caches oder privaten Configdateien ins Image.
4. Eine geeignete Java-25-Laufzeitbasis mit **konkret verifiziertem und versioniertem Digest** verwenden. Diesem Plan liegt absichtlich kein erfundener Basisdigest bei. Den vorhandenen Repositorypin bevorzugen, sonst den tatsächlichen kompatiblen Pin in TST3 festlegen und nachweisen. Native Bibliotheken von DuckDB/SQLite und die benötigte Systemumgebung wirklich ausführen.
5. Ohne Registry-Push lokal bauen, die tatsächliche Image-ID mit `docker image inspect` ermitteln und in `gretl-dist/target/image/candidate.json` speichern.
6. Die Distribution im Image unabhängig auslesen/prüfen. Quellenlabel allein genügt nicht: Die Runtime-/Task-/Extensiondateien und Launcherbytes müssen zur gebauten Distribution passen.
7. Quellenrevision, Distributionsmanifesthash, tatsächliche Image-ID, OS/Architektur, Basisreferenz/Digest und Buildkommando ohne Secrets protokollieren.

Ein Tag ist ein bequemer Verweis, keine unveränderliche Identität. Alle eigentlichen Tests starten die ermittelte Image-ID. Eine spätere Umbelegung des Tags darf die getestete Runtime nicht ändern.

Der normale Benutzer darf weiterhin ein bereits gebautes generisches Runtime-Image verwenden. Das **Kandidatentestgate** benötigt jedoch ein Image dieses Kandidaten, nicht ein beliebiges altes veröffentlichtes Image. Ein explizit geliefertes externes Kandidatenimage ist nur mit derselben Byte-/Provenienzprüfung zulässig.

### 5.3 Maven-Integration ohne Dockerzwang für Standardtests

Ein explizites Profil `job-image` aktivieren, das den Imagebau in `gretl-dist` **nach** Erstellung der Distribution und **vor** den Image-Integrationstests ausführt, beispielsweise in `pre-integration-test`. Das vorhandene Maven-Plugin-/Scriptmuster nutzen. Keinen neuen Produktmodulgraphen und keinen eigenen Build-Lifecycle erfinden.

Das Profil setzt beziehungsweise verlangt `gretl.test.executionMode=IMAGE`. Der erzeugte `candidate.json`-Pfad wird an das Testmodul weitergegeben. Die Tests lesen die ID aus dieser Datei, nicht aus einer während des Builds nicht zuverlässig propagierten dynamischen Maven-Property.

`./mvnw -B clean verify` bleibt Dockerfrei. Das heisst nicht, dass es keine Pakettests geben darf; vorhandene lokale PACKAGE_CONTRACT-Tests laufen weiter. Docker wird nur für ausdrücklich aktivierte Backing Services oder IMAGE angefordert.

Ein direkter IMAGE-Aufruf ohne die benötigte Image-/Netzwerkvorbereitung scheitert konkret. Kein implizites Pull eines willkürlichen Images, kein Fallback. Für den bequemen vollständigen Ablauf dient der kleine Matrixstarter aus §10.

### 5.4 `ImageExecutionAdapter` gezielt erweitern

Bestehende `Engine`, `Docker`, `Settings`, `Launch` und `prepare` weiterverwenden. Vorgesehene Ergänzungen:

| Typ/Methode | Auftrag |
|---|---|
| `Settings.configured()` | Eindeutige Kandidatenimage-Metadaten, eigener Netzwerkname, tatsächlicher non-root UID/GID und zulässiger Launcherpfad. Keine alte Defaultannahme „JAR“. |
| `prepare(Request, Path, Settings, Engine)` | Gleiches JobInput-/Invocationmodell wie SCRIPT; externe Inputwurzel, konvertierte lokale Pfade und keine Paketierung. |
| `Launch.start(Path)` | Tatsächlichen `docker run`-Prozess mit eigener Containeridentität starten. |
| `Launch.cancel()` | Kontrollierter Stop des zugehörigen Containers mit begrenzter Grace Period. |
| `Launch.remove()` | Nur eigenen Container entfernen, auch bei Fehlern; idempotent bei bereits entferntem Container. |
| `Docker.inspect(...)` | Begrenzte Laufzeit und sichere Ausgabeerfassung; kein unbeschränktes Pipe-/Wait-Risiko. |
| `mount(...)` | Dateityp und Existenz korrekt unterscheiden; eine fehlende Secretdatei nie als Verzeichnis anlegen. |
| `translateValue(...)` / `translate(...)` | Typisierte Pfade segmentweise über explizite Mounts abbilden; keine pauschale Textersetzung. |
| `translateEndpoint(...)` | Nur bekannte Dienstendpoint-Zuordnungen und dokumentierte URLgrenzen übersetzen; unbekanntes Host-loopback im Container scheitert. |

Die aktuelle `--entrypoint`-Überschreibung darf nicht dazu führen, dass ein fehlerhafter ausgelieferter Default-Entrypoint nie getestet wird. Mindestens die Produktstart-/Jobwechsel-Gegenproben starten das Image mit seinem tatsächlichen Default-Entrypoint; der Standardadapter soll nach verifizierter Imagekonfiguration ebenfalls diesen Weg verwenden. Ein expliziter Launcheroverride bleibt höchstens ein dokumentierter Qualifikationsparameter, nicht ein Reparaturfallback.

### 5.5 Dateisystem und Benutzer

| Hostbestand | Containerziel | Zugriff |
|---|---|---|
| Gestagte externe Quellen und statische Ressourcen | `/job` | read-only |
| Explizite fachliche Ausgaben | `/outputs` | read-write |
| Privater Run-/Taskworkspace | `/work` | read-write |
| Reports/Ereignisse | `/reports` | read-write |
| Fremdes Start-CWD | `/run` | read-write |
| Erzeugte Runtimekonfiguration | `/config/runtime.json` | read-only |
| Explizite Secretdateien | `/secrets/<alias>` | read-only |

Nicht das gesamte Repository, HOME, `~/.m2`, `/var/run/docker.sock` oder globale Zugangsdaten in den **GRETL-Jobcontainer** einbinden. Der Testhost benötigt den Dockerzugang; der Jobcontainer nicht.

UID/GID sind numerisch und nicht root. Auf der lokalen Unixumgebung nach den tatsächlichen Eigentumsverhältnissen wählen; kein pauschales `chmod -R 777`, kein blindes `chown` fremder Daten und keine feste Annahme UID 1001 auf macOS. Schreibverzeichnisse vor Start kontrolliert erstellen. Ein falscher Benutzer beziehungsweise fehlende Rechte müssen als konkrete Fehler erkennbar sein.

HOME und Java-Tempverzeichnis auf eigene beschreibbare Verzeichnisse setzen, etwa `/work/home` und `/work/tmp`. JNI-Ladepfade müssen funktionieren; kein unbegründetes `noexec` auf einem benötigten Native-Tempverzeichnis. Mindestens ein echter Lauf prüft einen read-only Containerroot mit den ausdrücklich vorgesehenen beschreibbaren Mounts und nötigenfalls einem begrenzten `/tmp`-tmpfs. Kein permanenter Schreibzugriff auf `/opt/gretl` als Voraussetzung.

### 5.6 Pfad- und Endpointsemantik

Diese Regeln sind in SCRIPT, IMAGE und JAR zu prüfen, nicht durch eine neue Runtime-Semantik zu ersetzen:

- `file(...)` bezieht sich auf den Job beziehungsweise im Projekt auf das jeweilige Teilprojekt; im JAR-Modus auf den vorhandenen materialisierten Ressourcenbestand.
- Als `path` deklarierte externe Parameter bleiben relativ zum ursprünglichen Start-CWD. IMAGE verwendet dafür `/run`, nicht `/job`.
- Relative Pfade der Runtimekonfiguration und Secretdateien folgen dem bestehenden Configvertrag, nicht beliebig dem Source-CWD.
- `workPath(...)` bleibt der eigene Task-/Aufrufworkspace. Gleichnamige temporäre Dateien zweier Tasks dürfen nicht kollidieren.
- Typisierte lokale `Path`-Werte werden zuerst von der Originalfixturewurzel zur gestagten Kopie und erst dann zum Containerpfad abgebildet. Kein versehentlicher Zugriff auf die ungestagte Originaldatei.
- Longest-prefix bedeutet **Pfadsegmente**, nicht Stringpräfix: `/data/a` ist nicht Elternpfad von `/data/abc`.
- Fachliche Strings wie FTP-Serverpfade, SQL-Literale, URIs, Jobnamen und Korrelations-IDs werden nicht allein wegen eines führenden `/` als Hostdateien interpretiert.
- Host-/Containerendpoints sind explizite Paare. Der Testhost kann etwa einen zufälligen PostgreSQL-Port verwenden, der Jobcontainer `postgres:5432` im eigenen Testnetz. `localhost` im Container ist kein stiller Alias für den Host.

Eine kleine reine `PathMapping`-Hilfsklasse ist zulässig, falls die vorhandenen Methoden sonst unübersichtlich werden. Vorgesehene Methoden: `rebaseFixturePath(Path, Path, Path)`, `toContainer(Path)`, `translateEndpoint(String)`, `translateConfig(Map<String,Object>)`. Kein allgemeines URL-/Dateisystemframework. Zuordnung und Unveränderlichkeit der ursprünglichen Requestkonfiguration mit Tests belegen.

### 5.7 Dienste, Netzwerk und Cleanup

Die bestehenden `PostgresFixture`, `FtpFixture`, `S3Fixture` und HTTP-Fixtures bleiben die fachlichen Dienste. Ihre isolierten Schemas/Remoteverzeichnisse/Buckets erhalten. Dienstcontainer sind unabhängig vom GRETL-Produktcontainer.

Der Matrixstarter erzeugt pro IMAGE-Lauf einen eindeutigen benannten Docker-Testnetzwerkbereich und übergibt ihn an Fixtures und Adapter. Keine Verwendung des Hostnetzwerks als notwendige Voraussetzung. Auf Docker Desktop/macOS und Linux die tatsächlich erreichbaren Host-HTTP-Endpunkte beziehungsweise Containeraliases testen. Die bereits vorhandenen Host-/Image-Endpointpaare nutzen.

Der Starter besitzt nur seine eigenen gelabelten Ressourcen. In `finally`/Shell-`trap` zuerst eigene GRETL-Jobcontainer beenden, dann gegebenenfalls eigene noch vorhandene Dienstcontainer kontrolliert aufräumen und das eigene Netzwerk entfernen. Die normale Testcontainers-Lebensdauer respektieren. Kein globales `docker system prune`, kein Entfernen fremder Netzwerke und kein Stop fremder OpenCode- oder Testprozesse.

Auf Fehler und Signal reagieren, nicht nur auf den erfolgreichen Pfad. Ressourcen-/Container-IDs im privaten Evidenzbereich festhalten, damit der äussere Starter auch nach einem abgebrochenen Testprozess gezielt aufräumen kann. Die Force-kill-Negativprobe darf einen nicht entfernbaren Container nicht als erfolgreichen Lauf umetikettieren.

## 6. Prozesslebensdauer, Fehler und Beobachtbarkeit

### 6.1 Start und Umgebung

Produktprozesse erben keinen Testclasspath. Potenziell störende Hostvariablen wie `CLASSPATH`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`, `JAVA_TOOL_OPTIONS`, `JAVA_OPTS` sowie relevante `GRETL_*`-Overrides kontrolliert behandeln. Explizit für einen Test gesetzte Werte dürfen nach dieser Bereinigung gezielt wieder hinzugefügt werden. Den vorhandenen Launcherparameter `GRETL_JAVA_OPTS` ebenfalls berücksichtigen. Keine Host-JAVA_HOME-Pfade versehentlich per `--env` in den Container übernehmen.

Die Testumgebung darf synthetische Marker-/Secretwerte setzen. Geheimnisse kommen nicht in Shellkommandostring, `command.json`, Imagebuildargumente, Repositorydateien oder den Modellprompt. Docker-Environmentübergabe kann über Namen erfolgen; trotzdem keine unredigierten vollständigen `docker inspect`-Umgebungen archivieren.

Jeder Prozess erhält ein eigenes Arbeitsverzeichnis ausserhalb des Inputs. `stdout` und `stderr` fortlaufend in separate Dateien umleiten oder nachweislich parallel und begrenzt leeren. Ein Test mit grosser Ausgabe muss zeigen, dass der Runner nicht an gefüllten Pipes hängenbleibt. Inspektions-/Buildprozesse gehören ebenfalls zu dieser Betrachtung.

### 6.2 Zustände

| Zustand | Bedeutung | Darf als erfolgreicher Fachtestbeleg zählen? |
|---|---|---|
| `PREPARE_FAILED` | Fixture-/Distributions-/Image-/Mountvorbereitung gescheitert | Nein; ausser als explizit separat geprüfter Infrastruktur-Negativfall |
| `LAUNCH_FAILED` | Prozess konnte nicht gestartet werden | Nein; kein Ersatz für erwarteten fachlichen Fehler |
| `EXITED` | Prozess beendet; tatsächlicher Exit bekannt | Erst nach fachlicher Erwartungsprüfung |
| `CANCELLED` | Kontrollierter Abbruch angefordert und belegt | Ja, für den spezifischen Abbruchtest mit passenden Assertions |
| `TIMED_OUT` | Äusserer Testtimeout überschritten | Für normale Fälle Testfehler; für eigene Harness-Timeoutprobe separat erwartbar |
| `FORCED_TERMINATION` | Nach begrenzter Grace Period forcierter Abbruch nötig | Kein Beleg für erfolgreiche kontrollierte Produkt-Cancellation |

Ein nichtnull Exit wird nicht automatisch Testfehler: Ein syntaktisch ungültiges Script oder ein fachlich ungültiger Datensatz muss den **richtigen** Fehler liefern. Umgekehrt reicht ein beliebiger Fehler nicht als Erfolg eines Negativtests. Prüfungen unterscheiden Bedien-/Definitionsfehler, fachliche Operationsexception, Cancellation und Classpath-/Transportfehler.

### 6.3 Abbruch und Timeouts

Vor dem Abbruch auf einen echten Checkpoint warten, beispielsweise eine gestartete Operation oder eine aktive DB-Abfrage. Kein blindes `sleep(2)` als einziger Nachweis, dass der Job bereits arbeitet. Polling mit Deadline und kontrollierter Wartezeit ist zulässig.

Auf dem Host muss der reale Launcher das Signal an die JVM weitergeben. Im Container muss der normale Entrypoint-/Initweg es an die Runtime weiterreichen. `docker stop` mit Grace Period und äusserer Testdeadline koordinieren; die äussere Deadline muss dem vorgesehenen Abschlussfenster Raum geben.

Nach Abbruch prüfen: Run-/Taskstatus, nicht ausgeführte Nachfolger, tatsächliches Cleanup, Reportabschluss, Dienstzustand und keine übrig gebliebene laufende Job-JVM beziehungsweise Jobcontainer. Bei DB-Abbruch bestehende Rollback-/Transaktionsnachweise erhalten. Keine globale Transaktionsgarantie über bereits erfolgreich abgeschlossene Tasks behaupten.

OS-Prozessstatus und fachlicher Runbericht-Exit werden getrennt gespeichert. Bei Signalen sind unterschiedliche Zahlen möglich; diese nur im **expliziten Abbruchszenario** nach dem bestehenden Vertrag akzeptieren. Nicht für alle Tests pauschal Exitunterschiede ignorieren.

### 6.4 Fehlerberichte und Ursachen

Runbericht und Ereignisse strukturiert parsen statt nur überall `contains("SUCCEEDED")` zu verwenden. Wo bestehende genaue Fachdiagnosen geprüft werden, diese Assertions erhalten. Für Skript-/Subprojektfehler die originale Quelldatei, sinnvolle Zeile, qualifizierte Task-ID und Cause-Kette prüfen.

Ein Fehler vor einem tatsächlichen Run darf nach bestehendem Vertrag ohne Runbericht enden. CHECK, PLAN und Jobhilfe erzeugen keine Taskausführung und keine Runworkspace-Materialisierung. Die jeweilige Erwartung wird im Szenariokatalog hinterlegt; nicht für jedes Kommando pauschal `report.json` verlangen oder dessen Fehlen generell tolerieren.

Die Runtime führt weiterhin vertrauenswürdigen Script-Toplevelcode beim Laden aus. Die Aussage „PLAN/HELP führt keine Taskarbeit aus“ ist **keine Sandboxzusage für beliebigen Toplevelcode**. Nebenwirkungsproben müssen diese Grenze korrekt berücksichtigen. Paketierung führt weiterhin keinen Jobbody aus.

## 7. Migration der bestehenden Tests

### 7.1 Kleine, konkrete Migrationstabelle

TST1/TST2 liefern pro tatsächlich betroffener Methode beziehungsweise parametrisiertem Szenario die Zuordnung:

```text
bisherige Klasse#Methode / Variante
bisheriger tatsächlicher Startweg
neue Testfamilie und Pflichtmodi
weiterverwendete Fixture und fachliche Assertions
bewusst verbleibende technische Modusunterschiede
```

Keine neue Vollinventur aller historischen GRETL-Projekte. Vorhandene ursprüngliche Szenariozuordnungen wiederverwenden; neue Tabelle beschreibt nur den Delta dieses Auftrags.

### 7.2 Konkrete bestehende Klassen

| Bestehender Bereich | Verbindliche Behandlung |
|---|---|
| `IoLocalJobIT`, `ValidatorLocalJobIT`, `FormatLocalJobIT`, `InterlisLocalJobIT`, `DuckDbLocalJobIT`, `FunctionLocalJobIT` | Fachliche Fälle auf den gemeinsamen, standardmässig SCRIPT-basierten Runner beziehen. Gleiche Fixtures und Assertions auch IMAGE/JAR. |
| `DatabaseArtifactIT`, `FormatArtifactPostgresIT`, `InterlisArtifactIT`, `ArtifactServicesIT`, `S3OriginalArtifactIT` | Dienste und unabhängige JDBC-/Datei-/FTP-/S3-Assertions erhalten. COMMON-Fälle in allen drei vollständigen Modi. Namen müssen nicht pauschal geändert werden. |
| `CancellationLocalJobIT`, `HardeningLocalJobIT`, `LibraryScopeLocalJobIT`, `PlaintextSecretLocalJobIT`, `ReferencePathLocalJobIT` | Jede Methode einzeln prüfen. Fach-/Lebensdauerfälle COMMON; explizite Manipulation eines Job-JARs PACKAGE_CONTRACT. Kein blindes Umschalten jeder Methode auf SCRIPT. |
| `ProjectScriptLocalJobIT` | Vorhandene echte Prozess-, Teilziel-, Hilfe-/Plan-, Quellenzeilen- und Single-File-Abgrenzung bewahren; eigenen Launcher-/Evidenzcode durch gemeinsamen Runner ersetzen, soweit gleichwertig. |
| `ProjectArtifactLocalJobIT` | Fachliche Graph-/Ergebnis-/Cleanup-Parität in gemeinsame Projektprozesstests überführen; Bytecode-/Manifest-/Materialisierungs-/beschädigte-JAR-Prüfungen ausdrücklich JAR lassen. |
| `SelectedPackageLocalJobIT`, `SelectedPackageArtifactIT`, `SelectedPackageSupport`, `ExternalPackageFixture`, `PackageArtifactAssertions` | Bleiben Paketqualifikation. Modus immer explizit JAR; keine automatische Interpretation als IMAGE oder SCRIPT. |
| `DistributionIT` | Frische Distribution, Launcher, Extensionentdeckung, Archiv und minimale Pakete weiterprüfen. Mindestens ein Quellenlauf aus entpackter Distribution. |
| `JobRunner`, ältere CLI-/Backing-Service-Komponententests | In-process Charakter benennen; ihre Ergebnisse nicht als zusätzliche eigenständige Prozessbelege zählen. Keine ersatzlose Löschung. |
| `ImageExecutionAdapterTest` | RecordingEngine-Prüfungen für Argumente/Pfade/Fehlergrenzen bleiben; echte Containerprüfungen kommen dazu. |

Beispiel am gelesenen Stand: `ProjectArtifactLocalJobIT.prj202203204206210211SameIndependentBytesAndGraphInScriptAndDetachedJar` enthält sowohl gemeinsame Ergebnis-/Graphprüfungen als auch JAR-Inventarassertions. Diese Verantwortungen trennen, nicht die JAR-Assertions beim Defaultwechsel verlieren. `prj205VersionHelpPlanHaveNoMaterializationAndCompiledClassesNeedNoSourceTexts` bleibt eine Paketvertragsprüfung. Die fachlichen Teile von `prj209...` gehören dagegen auch in SCRIPT/IMAGE. [Q5]

### 7.3 Gemeinsame Orakel statt drei Kopien

Fachliche Erwartungswerte liegen im Test oder in bestehenden unabhängigen Erwartungsdateien. JAR darf nicht die „Wahrheit“ für SCRIPT liefern; ebenso wenig darf SCRIPT die erwartete Datenmenge automatisch für IMAGE erzeugen. Modusparität ist eine **zusätzliche** Prüfung neben festen fachlichen Assertions.

Normalisierung nur für tatsächlich nichtfachliche Differenzen: absolute Workspacewurzel, Host-/Containeradresse, Run-UUID, Zeitstempel und Laufzeitdauer. Operation-IDs, Taskzustände, Reihenfolge wo fachlich festgelegt, fachliche Diagnosen, Ursache, Ergebnisbytes beziehungsweise Datensätze werden nicht weg-normalisiert. Binäre Dateiformate mit variablen Metadaten über unabhängige semantische Leser prüfen, nicht zwangsläufig bytegleich fordern.

Eine kleine gemeinsame Hilfsmethode wie `assertDatabaseRows(Result, Lease)` oder `assertProjectRun(Result, ExpectedProjectRun)` ist erwünscht. Keine allgemeine Erwartungs-DSL und kein riesiger generischer Reportmatcher.

### 7.4 Vorgesehene zusätzliche Testklassen und Methoden

Die Namen sind fachliche Vorschläge; vorhandene gleichwertige Tests integrieren.

| Testklasse | Wichtige Methoden/Prüfaufgaben |
|---|---|
| `ArtifactJobRunnerTest` | `defaultsToScript`, `rejectsUnknownMode`, `neverPackagesScript`, `neverPackagesImage`, `cannotMixPreparedModeAndLaunch`, `preservesPreparationFailure` |
| `ScriptExecutionAdapterTest` | `buildsArgumentVectorWithoutShellQuoting`, `usesSelectedFileAndProjectDirectory`, `doesNotReadPackageDescriptor`, `keepsRelativeParameterBase`, `rejectsUnsupportedInvocationCombination` |
| `JobInputSnapshotTest` | `changesWhenNestedResourceChanges`, `doesNotParseInvalidSettings`, `isStableAcrossAbsoluteLocations`, `doesNotFollowEscapingSymlink` |
| `ScriptProcessLocalJobIT` | `runsWithoutPackageDescriptor`, `ignoresUnusedBrokenDescriptor`, `seesSourceEditWithoutRebuildingRuntime`, `reportsGroovySyntaxFailureAtOriginalSource`, `runsFromUnpackedDistributionWithoutBuildTools` |
| `ProjectProcessLocalJobIT` | `runsOneGraphAcrossSubprojects`, `selectsOnlyDependencyClosure`, `resolvesSameNamedLocalResourcesIndependently`, `preservesCrossProjectResultsAndFailureCause`, `keepsSingleFileInvocationDistinct` |
| `JobProcessLifecycleLocalJobIT` | `drainsLargeOutput`, `cancelsAfterActualCheckpoint`, `distinguishesForcedTimeout`, `recordsLaunchFailureWithoutInventedRunReport` |
| `ImageRuntimeIT` | `runsExternalScriptUsingDefaultEntrypoint`, `reusesOneImageForDifferentJobs`, `runsChangedSourceWithoutImageBuild`, `runsProjectFromReadOnlyMount`, `usesNonRootWritableWorkspaces`, `deliversCancellationAndCleansContainer` |
| `JobEvidenceAuditTest` | `rejectsMissingScenario`, `rejectsRelabelledJarAsScript`, `rejectsStaleRevision`, `rejectsWrongImageDistribution`, `rejectsMissingRequiredReport`, `rejectsForeignOrSkippedJUnitProof` |

Dockerfreie Testklassen dürfen keine echte Docker-Abhängigkeit besitzen. `ImageRuntimeIT` wird **über die Maven-Auswahl im Imageprofil** ausgeführt, nicht über einen standardmässig übersprungenen Test mit `assumeTrue`. Vertragsprüfungen des Imageadapters bleiben dagegen normale Dockerfreie Unit-Tests.

## 8. Verbindliche Testfälle

Die IDs bezeichnen fachliche/technische Orakel, nicht zwingend je eine Java-Methode. Kleine Varianten dürfen parametrisiert werden. In `scenarios.json` konkrete Fixture-, Methoden-/Varianten- und Moduszuordnungen erfassen. Die folgende Matrix ist Mindestabdeckung **zusätzlich zum erhaltenen Bestand**.

### 8.1 Quellenstart und CLI

| ID | Aufbau | Erwartung | Pflichtmodi |
|---|---|---|---|
| S01 | Einfacher Job mit Dateiressource, ohne Descriptor | Echter Launcher; korrektes Ergebnis; kein Job-JAR | SCRIPT, IMAGE |
| S02 | Derselbe Job mit absichtlich kaputtem, unbenutztem Descriptor | Ergebnis unverändert; Descriptor nicht ausgewertet | SCRIPT, IMAGE |
| S03 | Expliziter anderer Scriptname, Leerzeichen/Unicode in Pfaden und Ausgabe | Argumente und UTF-8 korrekt, kein Shellsplit | SCRIPT, IMAGE |
| S04 | Zwei Starts, dazwischen gezielte Änderung des externen Scriptinhalts | Neue erwartete Ausgabe, gleiche Runtime/Image-ID, anderer Inputhash | SCRIPT, IMAGE |
| S05 | Joblauf ohne verfügbare Buildwerkzeuge in der Jobumgebung | Erfolgreicher Lauf; kein Maven-/Gradle-/Packageraufruf | SCRIPT, IMAGE |
| S06 | Groovy-Syntaxfehler | Erwarteter Definitions-/Startfehler mit Quelldatei/Zeile, keine Fachoperation | SCRIPT, IMAGE |
| S07 | Fehlende ausgewählte Jobdatei innerhalb vorhandenem Inputroot | Echter CLI-Fehler, kein künstlich angelegter Ordner und kein JAR-Fallback | SCRIPT, IMAGE |
| S08 | CHECK, PLAN, Jobhilfe mit Taskseitenwirkungsmarker | Keine Taskarbeit; korrekte Hilfe/Graphen; keine Runmaterialisierung | SCRIPT, IMAGE |
| S09 | Relative Pfadparameter bei fremdem CWD | Auflösung gegen Start-CWD, nicht Job- oder Configroot | SCRIPT, IMAGE, JAR soweit RUN |
| S10 | Relative Config-/Secretpfade | Bestehender Configvertrag; kein CWD-/Mountfehler; Secret redigiert | SCRIPT, IMAGE, JAR |
| S11 | Feindliche Host-Classpath-/Java-/GRETL-Overrides | Bereinigung oder ausdrücklich zugelassene Testwerte; kein fremder Testclasspath | SCRIPT, IMAGE, JAR |
| S12 | Tatsächlich entpackte Distribution ausserhalb Checkout | Echter Launcher und korrekte fachliche Ergebnisse | SCRIPT |

### 8.2 Projekte und gemeinsame Ausführung

| ID | Aufbau | Erwartung | Pflichtmodi |
|---|---|---|---|
| P01 | Mehrere über Settings eingebundene Subprojekte | Ein Run, ein DAG, gemeinsame Ergebnisse, qualifizierte Task-IDs | SCRIPT, IMAGE, JAR |
| P02 | Zwei Subprojekte mit gleichem lokalem Task- und SQL-Dateinamen | Keine Kollision, richtige lokale Ressourcen | SCRIPT, IMAGE, JAR |
| P03 | Projektübergreifendes `dependsOn` und `result(...)` | Richtige Reihenfolge und tatsächliche weitergereichte Daten | SCRIPT, IMAGE, JAR |
| P04 | Teilziel mit Vorgängern und unabhängigem Seitentask | Nur bestehende Abhängigkeitshülle ausgeführt; kein Seitentaskmarker | SCRIPT, IMAGE, JAR |
| P05 | Fehlende Referenz und projektübergreifender Zyklus | Konkreter Definitions-/Planfehler; keine Taskarbeit | SCRIPT, IMAGE, JAR soweit Definition im Paket geladen |
| P06 | Gleichnamige lokale Parameter, Rootparameter und Teilziel | Bestehende Namensraum-/Pflichtparameterregeln unverändert | SCRIPT, IMAGE, JAR |
| P07 | Fehler tief im Subprojekt und innere Operationsexception | Originalquelle/Zeile/Cause, Cleanup und Zustände korrekt | SCRIPT, IMAGE, JAR |
| P08 | Explizite einzelne Scriptdatei neben fehlerhaften Settings | Bestehender Single-File-Weg bleibt eigenständig | SCRIPT, IMAGE |
| P09 | Gemeinsame Helper-/Modell-/Templatequellen | Tatsächlich verfügbar, kein zufälliger Checkoutzugriff | SCRIPT, IMAGE, JAR |
| P10 | Unveränderter externer Projektinput ohne Descriptor | Projekt läuft ohne Schema-3-Paketierung | SCRIPT, IMAGE |

Bei P04/P06 keine neuen Regeln über nicht ausgewählte Projektparameter erfinden. Die bereits implementierte Projektsemantik bleibt massgeblich; der Test liefert die nach diesem Vertrag nötigen Werte.

### 8.3 Fachoperationen und unabhängige Ergebnisse

| ID | Aufbau | Erwartung | Pflichtmodi |
|---|---|---|---|
| F01 | Vorhandene HTTP/Curl-/Archiv-/XSLT-Fälle | Requestdetails und unabhängige Ergebnisbytes/-inhalte | SCRIPT, IMAGE, JAR |
| F02 | CSV/JSON/GPKG/SHP-Validatoren positiv/negativ | Richtige fachliche Diagnosen; falscher Classpath zählt nicht als erwarteter Fehler | SCRIPT, IMAGE, JAR |
| F03 | INTERLIS-/ili2db-Fälle einschliesslich Zusatzfunktionen | Vorhandene Modelle/Datensätze/konkrete Fehlermeldungen unverändert | SCRIPT, IMAGE, JAR |
| F04 | Db2Db/SqlExecutor mit Datum, Dezimalzahl, Unicode, Geometrie/SRID | Tatsächliche Daten via unabhängiger JDBC-Verbindung | SCRIPT, IMAGE, JAR |
| F05 | DuckDB/SQLite und formatbezogene native Bibliotheken | Echte Enginearbeit, unabhängige Datensätze/Dateiresultate | SCRIPT, IMAGE, JAR |
| F06 | FTP-Familie und fehlgeschlagener Login | Richtige Remote-Dateien/Bytes; Fehler ursachentreu; Dienst bleibt nutzbar | SCRIPT, IMAGE, JAR |
| F07 | S3-Familie mit isolierten Buckets | AWS-kompatibler Testdienst; Objekte/Bytes/Kopier-/Löschverhalten unabhängig geprüft | SCRIPT, IMAGE, JAR |
| F08 | Jede aktuelle Standardoperations-ID | Eigener positiver fachlicher Nachweis je vollständigem Modus | SCRIPT, IMAGE, JAR |

Die F-Zeilen sind **keine Erlaubnis**, die vielen vorhandenen Einzelvarianten zu acht Smokes zusammenzufassen. Bestehende Varianten und Detailassertions bleiben erhalten.

### 8.4 Prozess, Container und Fehlergrenzen

| ID | Aufbau | Erwartung | Pflichtmodi |
|---|---|---|---|
| L01 | Ausgabe deutlich grösser als typische Pipepuffer auf beiden Streams | Kein Deadlock; vollständige beziehungsweise dokumentiert begrenzte Erfassung | SCRIPT, IMAGE, JAR |
| L02 | Kontrollierter Abbruch nach echtem Checkpoint | Korrekte Run-/Task-/Reportzustände, Cleanup und keine Nachfolgerarbeit | SCRIPT, IMAGE, JAR |
| L03 | Bewusst nicht rechtzeitig beendeter Testprozess | Begrenzter Timeout/Force-kill; kein falsches Produkt-CANCELLED-PASS | Harness; reale repräsentative Host-/IMAGE-Probe |
| L04 | DB-Abbruch während aktiver Abfrage | Bestehender Rollback-/Transaktionsabschluss, keine idle transaction | SCRIPT, IMAGE, JAR |
| L05 | Fehler bei notwendigem Report-/Outputschreiben | Kein falscher Gesamterfolg; tatsächliche Ursache | SCRIPT, IMAGE, JAR |
| L06 | `keepWork` never/always/on-failure und Fehler/Erfolg/NO_DATA | Bestehender Retentionvertrag; Inputquellen bleiben erhalten | SCRIPT, IMAGE, JAR mit modespezifischer Ressourcenlage |
| I01 | Erst nach Imagebau erzeugter Job mit nicht vorher bekanntem Marker | Korrektes Ergebnis über Default-Entrypoint; Input nicht eingebaut | IMAGE |
| I02 | Zwei unterschiedliche Jobs, gleiche Image-ID | Beide korrekte Ergebnisse, kein Rebuild/Job-JAR | IMAGE |
| I03 | Quelle und lokale statische Ressourcen read-only | Lesen funktioniert, absichtliches Schreiben wird abgewiesen; erlaubte Outputs funktionieren | IMAGE |
| I04 | Numerischer non-root Nutzer und read-only Containerroot | Workspace, Report, HOME/Temp und native Operation funktionieren | IMAGE |
| I05 | Falsches Image, fehlender Launcher oder abweichende Distribution | Konkrete Qualifikationsfehler vor behauptetem Fachnachweis | IMAGE-Gate/negative Kopien |
| I06 | Fehlender Dockerdaemon/Netzwerk-/Secretmount | Explizites Scheitern, kein Skip oder anderer Modus | Adapter- und Gate-Negativproben |
| I07 | Host- und Container-Dienstendpoints verschieden | Echte PostgreSQL-/FTP-/S3-/HTTP-Verbindungen funktionieren | IMAGE |
| I08 | Fehler/Signal während Suite | Nur eigene Container/Netzwerke aufgeräumt, keine Leaks und keine Fremdeingriffe | IMAGE |

### 8.5 Paketierung bleibt erhalten

| ID | Aufbau | Erwartung |
|---|---|---|
| J01 | Bestehende Descriptor-Schemas 1/2/3 | Bisheriges Verhalten und klare Fehlermeldungen erhalten |
| J02 | Schmale Modulauswahl, Standardfunktionen und externe Extension | Richtige Bibliotheks-/Serviceauswahl; keine zufälligen Hostdependencies |
| J03 | Quellen nach Paketierung gelöscht, fremdes CWD, leeres HOME ohne Buildwerkzeuge | Job-JAR weiterhin selbständig funktionsfähig |
| J04 | Mehrere gleichnamige Subprojektskripte und Ressourcen | Eindeutige kompilierte Klassen, korrekte Ressourcen und originale Quelldiagnosen |
| J05 | Beschädigtes oder absichtlich verändertes Job-JAR | Richtiger Paket-/Startfehler; Audit hasht tatsächlich gestartetes Artefakt |
| J06 | Paketieren mit Toplevel-Seitenwirkungsmarker | Paketierung führt Job nicht aus; kein erfundener Runreport |

### 8.6 Fehlgrün-Abwehr

| ID | Manipulierte Prüfkopie | Audit-/Gateerwartung |
|---|---|---|
| E01 | Ein erwartetes Szenario fehlt vollständig | FAIL, auch wenn alle vorhandenen Tests grün sind |
| E02 | JAR-Evidenz als SCRIPT oder IMAGE umetikettiert | FAIL wegen unpassender Start-/Artefakt-/Inputbelege |
| E03 | Früherer grüner Report oder fremder Quellen-SHA | FAIL |
| E04 | Image-Label korrekt, tatsächliche Distributionsbytes falsch | FAIL |
| E05 | Pflicht-Runbericht fehlt oder gehört zu anderem Run | FAIL |
| E06 | JUnit-Failure/Error/Skip oder nicht zuordenbare Testmethode | Kein qualifizierter Erfolg |
| E07 | Alle Operationen nur über Vereinigung verschiedener Modi vorhanden | Vollmatrix FAIL |
| E08 | Quelle/SQL nach vorbereiteter Identität unbemerkt verändert | FAIL bei Quellenkonsistenz |
| E09 | Prozess läuft noch oder hat nur STARTED-Datei | Kein abgeschlossener Nachweis |
| E10 | Fehler in Maven/Audit wird von `tee` oder spätem Kommando überdeckt | Äusserer Gateexit bleibt ungleich null |
| E11 | RecordingEngine-Protokoll als realer IMAGE-Lauf ausgegeben | FAIL; reale Prozess-/Image-/Ergebnisbelege fehlen |
| E12 | Erwartungsbestand aus erfolgreichen Ist-Läufen neu erzeugt | Prüfung muss den unabhängigen Sollbestand verwenden; Reduktionsversuch wird sichtbar |

Solche Mutationen ausschliesslich an isolierten Testkopien durchführen. Nicht Kandidatendateien verändern und später heimlich zurücksetzen.

## 9. Nachweise und Erweiterung des vorhandenen Audits

### 9.1 Getrennte Identitäten statt überladenem `sha256`

Die bisherige einzelne Identität bedeutet je nach Modus Script-, JAR- oder Imagehash. Für neue Läufe ein eindeutig versioniertes Evidenzformat verwenden. Beispielstruktur, **kein tatsächlicher Laufnachweis**:

```json
{
  "schemaVersion": 2,
  "suiteRunId": "<eindeutiger Matrixlauf>",
  "executionId": "<eindeutiger Einzelstart>",
  "scenarioId": "<stabile Szenario-ID samt Variante>",
  "testCaseId": "<zuordenbare JUnit-Klasse/Methode/Variante>",
  "testFamily": "COMMON",
  "mode": "IMAGE",
  "executionKind": "SCRIPT",
  "sourceRevision": "<vollstaendiger Kandidaten-SHA>",
  "sourceDirty": false,
  "input": {
    "kind": "PROJECT_DIRECTORY",
    "relativePath": ".",
    "treeSha256": "<Hash des tatsaechlichen fachlichen Inputs>",
    "manifest": "input-manifest.json"
  },
  "runtime": {
    "distributionManifestSha256": "<Hash des geprueften Distributionsmanifests>",
    "launcherRelativePath": "bin/gretl"
  },
  "image": {
    "id": "sha256:<tatsaechliche Image-ID>",
    "os": "linux",
    "architecture": "<tatsaechliche Architektur>",
    "proof": "image-proof.json"
  },
  "process": {
    "state": "EXITED",
    "exitCode": 0,
    "timedOut": false,
    "cancellationRequested": false,
    "forciblyKilled": false,
    "startedAt": "<tatsaechlich>",
    "finishedAt": "<tatsaechlich>"
  },
  "command": "command.json",
  "report": "reports/report.json",
  "events": "reports/<tatsaechliche Datei>.events.jsonl"
}
```

SCRIPT hat `executionKind=SCRIPT`, aber keinen Imageblock und keinen Job-JAR-Block. IMAGE hat ebenfalls `executionKind=SCRIPT`. JAR hat `executionKind=PRECOMPILED_JOB` und einen gesonderten `jobJar`-Block mit Pfad/Hash; kein IMAGE- oder SCRIPT-Label. Technisch nicht vorhandene Felder weglassen, keine erfundenen Nullnachweise.

Nicht denselben Sourcehash als Runtimehash verwenden. Der zusätzliche Inputtreehash ist ein Testnachweis; der vorhandene produktive `source.sha256`-Vertrag wird nicht nebenbei umdefiniert. Bei Pakettests die JAR- und Runtime-Metadaten gegen ihre bestehenden eigenen Verträge prüfen.

### 9.2 Ablage und Schutz

```text
gretl-integration-tests/target/job-evidence/<scenario>-<uuid>/
  source/                         # eigene gestagte Fixture, nicht blind archivieren
  runtime.json                    # privat, nicht als CI-Artefakt hochladen
  image-runtime.json              # falls benötigt; ebenfalls privat
  input-manifest.json
  command.json                    # ohne Secretwerte
  execution.json
  job.stdout.log                  # redigiert
  job.stderr.log                  # redigiert
  reports/report.json             # nur wenn Vertrag ihn vorsieht
  reports/*.events.jsonl
  output/
  job.jar                         # nur JAR
  package-inventory.json          # nur JAR
  image-proof.json                # IMAGE; ausgewählte unsensible Metadaten
```

Build-, Review- und Laufnachweise werden zuordenbar, aber nicht zu einer neuen allgemeinen Zertifizierungsplattform. Keine vollständigen Quellarchive je Review, keine Tokenlogs und keine unkontrollierten Megabyte-Dumps im Gitrepository. Kurze Implementierungs-/Reviewberichte versionieren; Rohlogs/Artefakte im Buildbereich beziehungsweise bestehenden CI-Artefakten.

Der Matrixstarter kopiert die für die Prüfung benötigten **redigierten** Nachweise vor dem nächsten `clean` nach `.gretl-verification/<matrixRunId>/<mode>/` oder in einen explizit übergebenen ausserhalb von `target` liegenden Ordner. Diesen Bereich ignorieren. Jeder Modus erhält eigene Dateien; kein Überschreiben desselben `summary.json` mit dem jeweils letzten Erfolg.

### 9.3 `scenarios.json`: kleiner unabhängiger Sollbestand

Kein neuer ausführbarer Workflowinterpreter. Der Katalog beschreibt nur vorhandene Java-Testfälle und ihre Pflichtabdeckung, beispielsweise:

```json
{
  "schemaVersion": 1,
  "scenarios": [
    {
      "id": "project-pipeline-success",
      "family": "COMMON",
      "scope": "LOCAL",
      "modes": ["SCRIPT", "IMAGE", "JAR"],
      "test": "ch.so.agi.gretl.it.ProjectProcessLocalJobIT#runsOneGraphAcrossSubprojects",
      "fixture": "projects/pipeline",
      "report": "REQUIRED",
      "expectedOutcome": "SUCCEEDED"
    }
  ]
}
```

Das ist ein Schema-/Formbeispiel, **nicht** der vollständige Sollbestand. Der Agent muss den wirklichen Bestand vollständig und nachvollziehbar zuordnen. Varianten wie positiv/negativ, Single/FileSet oder Scalar/List erhalten stabile eigenständige IDs. Eine einzige Klassen-ID genügt nicht, wenn dadurch fehlende Varianten unbemerkt bleiben.

Reportregeln mindestens `REQUIRED`, `ABSENT` und ein eng begründetes `OPTIONAL_BEFORE_RUN`; die letzte Regel darf nicht als Standard für alle Fehlerfälle dienen. Erwartete Exits/Diagnosen bleiben in den Fachtests und bei Bedarf als auditierbare Verträge referenziert. Keine erwarteten Werte aus dem aktuellen Runreport abschreiben.

Der Katalog wird gegen tatsächlich entdeckte/ausgeführte Testfälle und das bestehende Operationsinventar abgeglichen. Entfernen eines Tests muss einen fehlenden Sollfall ergeben; Entfernen eines Katalogeintrags darf nicht automatisch eine bestehende fachliche Verpflichtung beseitigen. Änderungen am Sollumfang sind selbst reviewpflichtig.

### 9.4 `scripts/audit-job-evidence.py` weiterentwickeln

Bestehenden Audit erhalten und erweitern. Vorgesehene kleine Funktionen mit klaren Aufgaben:

```python
def load_execution(path): ...
def verify_distribution(distribution_root, expected_source): ...
def verify_input_manifest(execution, execution_directory): ...
def verify_launch_identity(execution, execution_directory, distribution): ...
def verify_report(execution, execution_directory, scenario_contract): ...
def read_junit_results(report_paths): ...
def require_scenarios(expected, executions, junit_results, mode, scope): ...
def require_operation_coverage(expected_operations, executions, mode): ...
def write_summary(destination, verified_executions, checks): ...
```

Die Namen sind Designvorschläge; bestehende gleichwertige Funktionen nutzen. Standardbibliothek genügt. Kein nachträgliches Aufrufen von Jobs, kein Neubauen von JARs oder Images und kein Ergänzen fehlender Reports durch den Auditor.

Vorgesehene CLI-Erweiterung:

```sh
python3 scripts/audit-job-evidence.py \
  --source "$CANDIDATE_SHA" --mode SCRIPT --scope local --run-id "$RUN_ID"

python3 scripts/audit-job-evidence.py \
  --source "$CANDIDATE_SHA" --mode IMAGE --scope full --run-id "$RUN_ID"
```

`--full` darf als bereits vorhandene gleichbedeutende Schreibweise für den vollen Scope erhalten bleiben. Bestehende CI-Aufrufer im selben Änderungsschritt konsistent migrieren. Ein neues `--evidence-root` zum Prüfen archivierter Modusbelege ist zulässig und muss Pfade sicher auflösen.

Der Audit prüft mindestens:

1. Vollständigkeit und Version der Abschlussnachweise; richtiger Modus, Run und Kandidaten-SHA.
2. Tatsächliche Distribution und Inputdateien beziehungsweise erhaltene geprüfte Inventare. Für IMAGE zusätzlich tatsächliche Image-ID und im Image geprüfte Distributionsbytes.
3. Übereinstimmung von angefordertem und tatsächlich gestartetem Weg: SCRIPT-Launcher plus externe Quellen; IMAGE plus externer SCRIPT-Einstieg; JAR plus konkretes Job-JAR. Keine gleichbedeutende Behandlung von beliebigen Hashstrings.
4. Passende Report-/Eventidentität, dokumentierte Exit-/Abbruchbeziehung und fachliche Laufzustände. Definierte frühe CLI-Fehler ohne Runreport korrekt unterscheiden.
5. Zuordnung zu wirklich ausgeführten, erfolgreichen JUnit-Testfällen. Ein erfolgreicher Kindprozess allein zählt nicht, wenn die nachfolgende fachliche Assertion scheitert. Parametrisierte Methoden müssen stabil zuordenbar sein; keine losen Klassennamensheuristiken.
6. Alle erwarteten Szenariovarianten je Pflichtmodus und Scope. **Nicht** nur „mindestens eine Ausführung“.
7. Positive Standardoperationsnachweise je vollständigem Modus. Zusätzliche explizite Pakettests in IMAGE dürfen die IMAGE-Menge nicht auffüllen.
8. Keine Failures/Errors und keine nicht ausdrücklich zulässigen übersprungenen Pflichttests. Profilbedingt gar nicht beauftragte Tests sind keine ausgeführten Skips.
9. Bestehende Distributions-/Paketinventare, CI-Provenienz und tatsächliche Test-/Coverageausgaben. JaCoCo erfasst weiterhin nur wirklich instrumentierte JVMs; separate Prozesse nicht als gemessene Coverage ausgeben.
10. Rohbelege und Zusammenfassung widersprechen sich nicht. Ein altes oder bloss handgeschriebenes grünes Summary ersetzt keine Prüfung.

Ein solches Evidenzformat macht unabsichtliche Fehlgrünzustände erkennbar; es ist keine kryptografische Garantie gegen einen absichtlich manipulierenden Implementierer. Deshalb bleiben unabhängiges Lesen des Codes, echte eigene Läufe und die Reviewer-Gegenproben notwendig.

## 10. Ausführbare Gates und CI

### 10.1 Befehle im Zielzustand

Die folgenden neuen Script-/Profil-/Auditoptionen sind **zu implementierende Bedienverträge**, keine Behauptung ihrer bisherigen Verfügbarkeit.

```sh
# Alltägliche schnelle Gesamtprüfung, ohne Docker:
./mvnw -B clean verify
# Gemeinsame lokale Fachtests standardmässig SCRIPT;
# vorhandene lokale Paketverträge weiterhin ausdrücklich JAR.

# Vollständige Host-SCRIPT-Qualifikation mit echten Diensten:
scripts/verify-job-matrix.sh --mode SCRIPT --scope full

# Zusätzliche vollständige Job-JAR-Qualifikation:
scripts/verify-job-matrix.sh --mode JAR --scope full

# Tatsächliches generisches Image bauen und darin externe Scripts ausführen:
scripts/verify-job-matrix.sh --mode IMAGE --scope full

# Kumulative Schlussabnahme:
scripts/verify-job-matrix.sh --mode ALL --scope full
```

Zusätzlich muss `--scope local` für isolierte lokale Prozess-/Paketfälle funktionieren. IMAGE-local benötigt selbstverständlich Docker, auch ohne PostgreSQL/FTP/S3. Die Auswahl bezieht sich auf externe **Dienste**, nicht auf den Start des Produktcontainers.

### 10.2 Kleiner Matrixstarter, kein Buildframework

`scripts/verify-job-matrix.sh` erledigt nur Eingabeprüfung, eigene Laufverzeichnisse, gegebenenfalls Netzwerkvorbereitung, die vorhandenen Maven-Gates, Audit, Sicherung der Nachweise und Cleanup. Vorgesehene interne Shellfunktionen:

```text
parse_arguments
preflight
create_run_directory
prepare_image_network
run_mode
audit_mode
preserve_mode_evidence
cleanup_owned_resources
write_matrix_summary
```

SCRIPT/JAR verwenden das bestehende Standard- beziehungsweise Vollprofil; IMAGE ergänzt `job-image`. Beispiel für den inneren Vollaufruf:

```sh
./mvnw -B -Pgeotools-probe,backing-services clean verify \
  -Dgretl.test.executionMode=SCRIPT \
  -Dgretl.test.runId="$RUN_ID"
```

IMAGE verwendet zusätzlich das Imageprofil und den ausdrücklich vorbereiteten Netzwerk-/Userkontext. Den aktuellen effektiven Maven-POM und die Failsafe-Testauswahl prüfen. Die Propertyweitergabe in geforkte JVMs ausdrücklich konfigurieren und testen, nicht nur annehmen.

Für ALL sind getrennte saubere Maven-Läufe pro Modus zulässig und zunächst vorzuziehen. Vor jedem nächsten `clean` den vorherigen geprüften Nachweis sichern. Das kostet zusätzliche Buildzeit, verhindert aber Vermischung und erfordert keine komplizierte Wiederverwendung installierter Reactorartefakte. Spätere Optimierung nur mit erhaltener Identitäts- und Vollständigkeitsprüfung.

Der Starter:

- führt keine Veröffentlichung, keinen Remote-Push und kein Image-Push aus;
- benutzt Bash in einer auf der tatsächlichen lokalen Umgebung vorhandenen Version, ohne unnötige neue Toolvoraussetzungen;
- propagiert Maven-/Audit-/Dockerfehler korrekt, auch bei `tee`; kein letzter erfolgreicher Echo-/Cleanupbefehl darf Fehler verdecken;
- wartet auf alle eigenen Prozesse; legt ein Summary erst nach deren tatsächlichem Ende an;
- räumt nur eindeutig ihm gehörende Ressourcen auf;
- erfasst Plattform/Architektur; behauptet keine nicht ausgeführte Windows-/ARM-/Linux-Parität;
- führt positive Gateprüfungen auf dem unveränderten Kandidaten aus, negative Checkerproben in gesonderten Kopien.

### 10.3 Maven-Testauswahl

Der vorhandene normale Testeinschluss `*JobIT` und der grössere Backing-Service-Einschluss `*IT` dürfen angepasst werden, aber keine bestehende Suite versehentlich verlieren. Besonders prüfen, ob neue `ImageRuntimeIT` im Imageprofil wirklich eingeschlossen wird und im Dockerfreien Standardprofil **gar nicht beauftragt** wird.

PACKAGE_CONTRACT-Fälle bleiben unabhängig vom globalen Primärmodus aktiv. SCRIPT-/IMAGE-spezifische Quellverträge ohne JAR-Entsprechung müssen im Katalog sauber abgegrenzt sein; entweder über Profile passend auswählen oder in einem JAR-Gesamtlauf ausdrücklich als zusätzliche SCRIPT-Vertragsprüfungen kennzeichnen. Das ist keine Erlaubnis für COMMON-Fallbacks.

Die vollständige aktuelle Testmethodenmenge aus Surefire/Failsafe mit der Sollmatrix vergleichen. Keine fest eingetragene Gesamtzahl aus einem alten Bericht als alleinige Abnahme verwenden.

### 10.4 CI-Integration

Die vorhandenen Workflows prüfen. In TST4 eine reguläre CI-Qualifikation für SCRIPT, JAR und IMAGE auf demselben Commit herstellen. Eine GitHub-Actions-Matrix mit separaten Jobs ist möglich; ein sequenzieller Aufruf von ALL ebenfalls. Fehlende Artefakte/Modi dürfen nicht durch `continue-on-error` oder eine erfolgreiche manuelle Extrasuite kaschiert werden.

Verbindlicher Zielzustand:

- normale Änderungen erhalten den Dockerfreien Standardnachweis;
- die reguläre vollständige Kandidatenprüfung umfasst SCRIPT mit Diensten, JAR mit Diensten und echte IMAGE-SCRIPT-Läufe mit Diensten;
- vor einer bestehenden Veröffentlichung müssen diese vorgesehenen Jobs erfolgreich sein;
- `image-qualification.yml` darf als manueller Diagnose-/Explizitimageweg bleiben, ist aber nicht mehr der **einzige** Imageprüfweg;
- jeder Modus archiviert seine eigenen redigierten Reports, Identitäten, Logs, Szenario-/Operationsabdeckung und relevante fachliche Ausgaben;
- ein „Matrix-PASS“ verlangt alle drei Modusnachweise für exakt denselben Quellencommit;
- bestehende Distributions-/Paketqualifikation nicht entfernen, um Zeitlimits einzuhalten. Timeouts begründet anhand tatsächlicher Laufzeiten anpassen; keine pauschalen Testskips.

Die Implementierung darf Workflows lokal ändern, startet aber nur bereits autorisierte Remote-CI-Wege. Eine entfernte Branch-Protection-Änderung ist nicht automatisch erlaubt. Wo ein Maintainer einen Required-Check konfigurieren muss, die konkrete verbleibende administrative Aufgabe benennen; die lokale vollständige technische Qualifikation darf trotzdem sauber nachgewiesen werden. Eine bloss geschriebene Workflowdatei ist kein bereits ausgeführter CI-Lauf.

### 10.5 Plattformumfang

Pflicht sind der tatsächliche lokale Host-SCRIPT-Weg des Entwicklers sowie eine echte Linux-Containerqualifikation mit dokumentierter Architektur. Die bestehenden Linux-x86-64-CI-Gates bleiben erhalten. Auf einem Apple-Silicon-Host lokale ARM64-Ergebnisse und gegebenenfalls emulierte AMD64-Ausführung ehrlich unterscheiden.

Kein zusätzlicher allgemeiner Windows-/Multiarch-Zertifizierungsauftrag. Bestehende Windowslauncher/-tests nicht beschädigen. Wenn ein notwendiges bestehendes Gate nur über eine berechtigte CI-Plattform möglich ist, darf der Reviewer dessen Rohbelege für genau den Kandidaten unabhängig auswerten. Ohne tatsächlichen Plattformnachweis keine pauschale Plattformfreigabe.

## 11. Phasenplan und nicht überspringbare Gates

**Vier Phasen, je ein unabhängiger Review pro akzeptiertem Kandidaten.** Die letzte Phase enthält die kumulative Gesamtabnahme; kein zusätzlicher fünfter Verwaltungsreview. Agent A arbeitet nach jedem gültigen PASS autonom weiter. Ein belegter FAIL führt zu Nacharbeit derselben Phase, ein echter BLOCKED-Zustand verhindert deren Freigabe.

### 11.1 TST1 – Eigenständiger SCRIPT-Weg und eindeutiger Prozessrunner

**Lieferergebnis:** Ein normaler Quellenjob läuft über den gemeinsamen Runner ohne vorherige Jobpaketierung. Bestehende Pakettests bleiben funktionsfähig.

Arbeit: kurzer Baseline-/OpenCode-Preflight; Modus-/Input-/Invocationmodell; eindeutige Prepared-Zustände; tatsächlicher SCRIPT-Adapter; minimale gemeinsame Input-/Runtimeidentität; Migration der direkten Booleanaufrufer; erste echte SCRIPT-Fälle; Auditdefault so anpassen, dass das bisherige Gate weiterhin sinnvoll und nicht durch deaktivierte Prüfungen grün bleibt.

| ID | Muss-Kriterium |
|---|---|
| TST1-01 | Aktueller Checkout, Auftragsbasis, relevante Testdelta-Zuordnung und funktionierender unabhängiger OpenCode-/DeepSeek-Preflight sind belegt. |
| TST1-02 | SCRIPT ist echter konfigurierbarer Default; ungültiger Modus scheitert; Prepared/Start können nicht unterschiedliche Modi behaupten. |
| TST1-03 | SCRIPT benutzt den tatsächlichen Kandidatenlauncher als eigenen Prozess; keine Test-JVM-Abkürzung und kein zufälliger PATH-Launcher. |
| TST1-04 | S01/S02/S05 bestehen: kein Descriptor-/Packager-/Job-JAR-Zwang, einschliesslich echter Negativkontrolle der Paketierungsgrenze. |
| TST1-05 | S03/S04/S06/S07 bestehen: Argumente/Pfade, geänderte Quelle bei gleicher Runtime und richtige frühe Fehler. |
| TST1-06 | Jobinput, Runtimeidentität und tatsächliche Prozessausführung werden getrennt und vor Start erfasst; kein inhaltlicher Parser im Inputfingerprint. |
| TST1-07 | Relevante Boolean-/Prepared-Aufrufer migriert; bestehende JAR-Paket-/Projektverträge bleiben ausdrücklich JAR und erfolgreich. |
| TST1-08 | Standard-Maven-Gate bleibt Dockerfrei; kein stiller Skip und keine abgeschwächte Fachassertion als Folge des Defaultwechsels. |
| TST1-09 | Neuer Runner besitzt Tests für Startfehler, begrenztes Warten und konsistente Abschlussmetadaten; mindestens repräsentative reale Prozesse ausgeführt. |
| TST1-10 | Eigene Pflichtprüfungen und unabhängiger Review am festen Kandidaten bestehen, einschliesslich einer neuen Reviewer-Gegenprobe. |

**Eigenes Mindestgate:** `./mvnw -B clean verify`, danach der angepasste Standardaudit mit erwarteter SCRIPT-Primärabdeckung und unverändert erhaltenen JAR-Verträgen. Zusätzlich lokale gemeinsame Regression einmal explizit JAR ausführen; der tatsächliche Mavenaufruf, Testumfang und Audit müssen im Bericht stehen. `verify-job-matrix.sh` kann bereits in kleiner Form verwendet werden, ist aber erst ab TST2 vollständig verbindlich.

**Reviewer-Gegenprobe:** Einen eigenen neuen Job erst nach dem Distributionsbuild anlegen, einen kaputten unbenutzten Descriptor danebenlegen und mit geändertem Input zweimal über denselben unveränderten Launcher starten. Fachliche Ausgaben selbst prüfen und sicherstellen, dass keine Paketierung Voraussetzung war.

**Nicht vorziehen:** noch keine Pflicht, sämtliche realen IMAGE-Fälle bestanden zu haben. Die bestehende Imagevorbereitung darf aber nicht beschädigt werden.

### 11.2 TST2 – Vollständige Fachtests, Projekte und SCRIPT/JAR-Parität

**Voraussetzung:** gültiges TST1-PASS. **Lieferergebnis:** Alle gemeinsamen fachlichen Szenarien laufen im bevorzugten SCRIPT-Weg und weiterhin im JAR-Weg; gleiche Orakel, vollständige Dienst- und Projektabdeckung.

Arbeit: methodenweise Migration; gemeinsame Fixture-/Oracle-Nutzung; Projekt- und Fehler-/Lebensdauerfälle; vollständige Szenariomatrix; SCRIPT-/JAR-Gates und unabhängige Operationsabdeckung; erste negative Auditproben; schlanker Matrixstarter mit sauberer Evidenzsicherung.

| ID | Muss-Kriterium |
|---|---|
| TST2-01 | Alle betroffenen Methoden/Varianten sind COMMON, SCRIPT_CONTRACT, IMAGE_CONTRACT, PACKAGE_CONTRACT oder COMPONENT zugeordnet; kein verlorenes Alt-Szenario. |
| TST2-02 | Die gemeinsame Fachsuite verwendet dieselben Jobquellen, Testdaten und unabhängigen Assertions für SCRIPT/JAR; keine abweichenden Fachgoldens je Modus. |
| TST2-03 | Sämtliche P01–P10-Verträge soweit in dieser Phase fällig sind erhalten und als echte SCRIPT-/JAR-Prozesse geprüft, inklusive lokaler Ressourcen und Einzeldatei-Abgrenzung. |
| TST2-04 | Vollständiger SCRIPT-Lauf mit echten Backing Services besitzt die erforderlichen positiven Nachweise aller aktuellen Standardoperationen und die bisherigen Negativvarianten. |
| TST2-05 | Vollständiger JAR-Lauf bewahrt dieselbe gemeinsame Fachabdeckung sowie J01–J06; Pakettests werden nicht durch SCRIPT-Ergebnisse ersetzt. |
| TST2-06 | Abbruch, grosse Ausgabe, Fehlstart, Reportfehler, Ressourcen-/Secretpfade und Retention besitzen echte Prozessnachweise; Ursachen/Exits nicht pauschal normalisiert. |
| TST2-07 | `scenarios.json` und Audit prüfen Sollvarianten, tatsächliche JUnit-Ergebnisse sowie Operationsabdeckung getrennt je Modus. |
| TST2-08 | E01/E02/E03/E05/E06/E07/E09/E10 werden mit isolierten Negativkopien geprüft; fehlende oder falsche Nachweise scheitern. |
| TST2-09 | Der Matrixstarter führt SCRIPT/JAR local/full korrekt aus, bewahrt Evidenz vor `clean` und verschleiert keine Fehler. |
| TST2-10 | Vollständige eigene SCRIPT-/JAR-Gates und unabhängiger Review am Kandidaten bestehen; eigene risikorelevante Reviewer-Gegenprobe. |

**Eigenes Mindestgate:** Dockerfreie Standardprüfung, `scripts/verify-job-matrix.sh --mode SCRIPT --scope full` und `scripts/verify-job-matrix.sh --mode JAR --scope full`. GeoTools-/Backing-Service-Gates aus dem vorhandenen vollständigen Profil bleiben enthalten.

**Reviewer-Gegenprobe:** Ein selbst gewähltes Subprojekt mit gleichnamigen Ressourcen und einer echten projektübergreifenden Ergebnisabhängigkeit oder ein neuer Datenbankparameter-/Abbruchfall. Dieselbe feste fachliche Erwartung in SCRIPT und JAR prüfen. Zusätzlich einen erwarteten Szenariobeleg aus einer Auditkopie entfernen; Audit muss scheitern.

### 11.3 TST3 – Tatsächliches generisches IMAGE mit SCRIPT im Container

**Voraussetzung:** gültiges TST2-PASS. **Lieferergebnis:** Ein aus der Kandidatendistribution gebautes generisches Runtime-Image führt externe Jobs und Projekte korrekt aus. Vollständige gemeinsame IMAGE-Fachabdeckung, nicht nur vorbereiteter Adapter.

Arbeit: vorhandenen Imagebau wiederverwenden oder kleinen Dockerfile ergänzen; imageprofil; Identitätsprüfung; reale non-root Mount-/Netzwerk-/Native-/Signaltests; gemeinsame IMAGE-Fachläufe; Image-Gegenbelege und Cleanup.

| ID | Muss-Kriterium |
|---|---|
| TST3-01 | Generisches Kandidatenimage aus geprüfter Distribution, tatsächlich verifizierter Basisreferenz und korrekten Runtimebytes; keine testspezifischen Jobartefakte im Buildkontext. |
| TST3-02 | Tatsächlicher Default-Entrypoint und Adapter führen `gretl run` mit externem Script/Projekt aus; kein Job-JAR- oder Paketierungsfallback. |
| TST3-03 | I01/I02 und S04 bestehen mit identischer Image-ID: nach Build erzeugter Job, zweiter anderer Job und geänderte externe Quelle liefern jeweils erwartete neue Ergebnisse. |
| TST3-04 | Schreibgeschützter Jobbestand, non-root UID/GID, private beschreibbare Work-/Report-/Output-/Tempbereiche und read-only Rootfall funktionieren ohne Rechteaufweitung. |
| TST3-05 | Explizite Host-/Containerpfade und Dienstendpoints funktionieren für echte HTTP-/PostgreSQL-/FTP-/S3-Szenarien; kein Hostnetzwerkzwang. |
| TST3-06 | DuckDB/SQLite und relevante Fachbibliotheken führen tatsächlich Arbeit im Image aus; kein blosses `--version` als Native-Nachweis. |
| TST3-07 | Vollständige COMMON- und fällige SCRIPT-/IMAGE-Verträge besitzen echte Containerläufe; alle Standardoperationen separat IMAGE-positiv nachgewiesen. |
| TST3-08 | Kontrollierter Abbruch und Fehlercleanup wurden echt im Container geprüft; kein hängender Container und kein fremdes Ressourcen-Cleanup. |
| TST3-09 | E04/E08/E11 sowie Image-/Mount-/Voraussetzungsfehler sind negativ geprüft; IMAGE-Gate scheitert ohne echte passende Produktläufe. |
| TST3-10 | Alle eigenen Gates und der unabhängige Review am Kandidaten bestehen; Reviewer führt mindestens einen eigenen externen Job im tatsächlichen Image aus. |

**Eigenes Mindestgate:** Standardprüfung, vollständige SCRIPT- und JAR-Regression sowie `scripts/verify-job-matrix.sh --mode IMAGE --scope full`. IMAGE-local zusätzlich als dokumentierter schneller Einstieg testen. Getestete Plattform und Native-/Emulationslage angeben.

**Reviewer-Gegenprobe:** Eigenen Job mit neuem Marker nach Imagebau erstellen; über den normalen Imageentrypoint starten, danach Inhalt ändern und erneut ohne Imagebau ausführen. Dazu mindestens einen neuen Pfad-/Rechte- oder Abbruchfall und eine falsche-Image-Evidenzkopie prüfen.

Ein funktionierender RecordingEngine-Test erfüllt TST3-02/03/07 ausdrücklich **nicht**.

### 11.4 TST4 – Verbindliche CI-Matrix und kumulative Gesamtabnahme

**Voraussetzung:** gültiges TST3-PASS. **Lieferergebnis:** Alltagspfad, volle Matrix, Nachweise, Dokumentation und CI sind konsistent; genau dieser finale Kandidat ist unabhängig kumulativ geprüft.

Arbeit: reguläre Workflows vollständig verdrahten; alle Audit-/Gate-Negativfälle abschliessen; Anleitung aktualisieren; Standard/Dockerfrei und ALL/full am finalen Commit durchführen; unabhängiger Gesamtreview.

| ID | Muss-Kriterium |
|---|---|
| TST4-01 | Standardweg ist dokumentiert, funktioniert frisch ohne Docker und prüft breite echte SCRIPT-Jobs plus erhaltene Paketverträge. |
| TST4-02 | `--mode ALL --scope full` führt SCRIPT, JAR und IMAGE vollständig aus; jeder Modus besitzt eigene vollständige erfolgreiche Fach-/Operationsnachweise. |
| TST4-03 | CI verdrahtet echte IMAGE-SCRIPT-Qualifikation regulär statt nur als manuellen Optionalweg; Veröffentlichung hängt von den vorgesehenen erfolgreichen Gates ab. |
| TST4-04 | Alle E01–E12-Fehlgrün-Proben sind umgesetzt oder durch nachweislich gleichwertige bestehende Prüfungen abgedeckt. |
| TST4-05 | Evidenz gehört zum finalen Kandidaten; JUnit-/Prozess-/Artefakt-/Input-/Image-Identitäten und Phasenberichte widersprechen sich nicht. |
| TST4-06 | Kein Verlust von bestehenden fachlichen, JAR-, Projekt-, Sicherheits-, Logging-, Cancellation- und Modulgrenzen; keine fiktive Coverage separater Prozesse. |
| TST4-07 | Aktive Dokumentation, Beispielaufrufe, Profile und Workflowartefakte stimmen; lokale/CI-/Containerplattformen sowie nicht ausgeführte Umgebungen ehrlich bezeichnet. |
| TST4-08 | Vollständiges unabhängiges kumulatives OpenCode-PASS für TST1–TST4 am finalen Produkt-SHA, echte Gegenproben und abgeschlossener Reviewprozess. |

**Eigenes Mindestgate:** Frische Standardprüfung und vollständiges ALL/full am unveränderten finalen Commit. Erhaltene bestehende Gates, die nicht transitiv enthalten sind, ausdrücklich ergänzen. Autorisierte CI-Nachweise dieses SHAs können plattformspezifische Läufe ergänzen; keine alte grüne Pipeline umetikettieren.

**Reviewer-Gegenprobe:** Matrix-/Auditkopie manipulieren: beispielsweise einen IMAGE-Fall durch einen gleichnamigen JAR-Fall ersetzen und zusätzlich ein altes Distributionsinventar einschleusen. Der wirkliche Gateweg muss scheitern. Danach mindestens einen eigenen fachlichen Quellenjob über SCRIPT und IMAGE mit festen Erwartungen ausführen.

**Freigabe:** TST4-PASS ist zugleich die Gesamtabnahme. Kein zusätzlicher Review nur für die unveränderte Ablage dieses Berichts.

## 12. Autonomer Codex-/OpenCode-Ablauf

### 12.1 Rollen, Autonomie und genau drei Urteile

**Agent A / Codex** setzt TST1–TST4 nacheinander um. Er führt die eigenen Gates aus, committet den Kandidaten, startet den unabhängigen Reviewer selbst, wertet die Ergebnisse aus und behebt Findings. Nach gültigem PASS beginnt er automatisch die nächste Phase. Das Erstellen eines Reviewprompts oder das Starten eines noch laufenden Prozesses gilt nicht als Abschluss.

**Agent B / OpenCode 2.x + DeepSeek** prüft in einer neuen Sitzung und einem eigenen detached Worktree. Er hat weder Implementierung noch Tests dieser Phase geschrieben. Er untersucht Zielerreichung, relevante vollständige Diffs, aktuellen Code und echte Testresultate; er führt mindestens eine neue risikorelevante Gegenprobe selbst aus. Er verändert den Kandidaten nicht.

| Urteil | Bedeutung und nächste Aktion |
|---|---|
| **PASS** | Sämtliche Muss-Kriterien der Phase sind am exakten Kandidaten erfüllt; Pflichtprüfungen beendet und erfolgreich; eigene Gegenprobe tatsächlich ausgeführt; keine offenen Muss-Findings. Bei TST4 alle 38 Kriterien TST1–TST4 kumulativ. Erst nach technischer Validierung durch Agent A fortfahren. |
| **FAIL** | Mindestens ein Muss-Kriterium ist nachweislich verletzt. Agent A korrigiert dieselbe Phase, erstellt einen neuen Kandidaten und startet einen frischen unabhängigen Review. |
| **BLOCKED** | Mindestens ein nötiger Nachweis ist nicht möglich, etwa wegen fehlender API-Verbindung, Rechte, Docker-Plattform oder abgebrochenem Reviewtransport. Ursache und bereits unternommene Versuche konkret benennen. Kein Phasen-PASS. |

`IN_PROGRESS`, `AWAITING_REVIEW` und `REWORK` sind Arbeitszustände, keine zusätzlichen Urteile. Ein tatsächlicher fehlerhafter Test ist FAIL, ein nachgewiesener nicht erreichbarer Docker-Daemon kann BLOCKED sein. Nicht jeden Produktfehler zur Infrastrukturstörung umdeuten. Ausserhalb des Auftrags liegende Verbesserungsideen getrennt notieren; sie dürfen keine neu erfundenen Muss-Kriterien werden.

Autonomie umfasst lokale Implementierung, Tests, zweckgebundene Worktrees, lokale Commits, lokale Testimages/-netze und den ausdrücklich beauftragten direkten DeepSeek-Review. Sie umfasst **keinen Push, Merge, Release, Image-Push, Produktionszugriff oder Umbau globaler Credentials**. Bereits autorisierte CI-Zugriffe dürfen genutzt werden; fehlende Berechtigungen sind kein Anlass, Schutzgrenzen zu umgehen.

### 12.2 OpenCode-/DeepSeek-Preflight am Anfang von TST1

Vor viel Implementierungsarbeit im tatsächlich verwendeten Profil prüfen:

1. Absoluten Pfad des installierten OpenCode-2-CLI bestimmen. Tatsächliche Version und `run --help` lesen; keine V1-CLI versehentlich verwenden. Kein automatisches Upgrade oder Neuinstallieren auf Verdacht.
2. Vorhandene GUI-/CLI-Anmeldung nutzen. `auth list`, Modellliste und die wirksame Providerkonfiguration prüfen, ohne Schlüssel oder vollständige Credentialdateien auszugeben. **Ein fehlendes `DEEPSEEK_API_KEY` in der Shell beweist keine fehlende Anmeldung.**
3. Den geforderten Provider-/Modell-/Endpointvertrag nachweisen:

   ```text
   OpenCode provider/model: deepseek/deepseek-flash
   API-Modell:             deepseek-flash
   API-Host:               api.deepseek.com
   Zielmodell:             DeepSeek-V4.1-Flash-1017
   CLI:                    tatsächlich installierte OpenCode-2.x-Version
   ```

4. Die offizielle Modellzuordnung und die effektive lokale Konfiguration am Ausführungstag prüfen. `deepseek-flash` ist ein Alias und kein zeitlos unveränderlicher Snapshot. Die bei Erstellung dieses Plans gelesene offizielle Dokumentation ordnet ihn Flash-1017 zu. [E2, E3] Eine andere heute bekannte Aliaszuordnung darf nicht still als 1017 ausgegeben werden. Keine Identitätsbehauptung allein aufgrund einer Antwort des Modells auf „Welches Modell bist du?“.
5. Kein OpenRouter, OpenCode Go/Zen, LiteLLM-Proxy, anderes Modell oder anderer Anbieter als stiller Ersatz. Bei fehlender 1017-Verfügbarkeit konkret `BLOCKED(MODEL_UNAVAILABLE)` berichten, statt eine neue Nutzerentscheidung vorzutäuschen.
6. Projekt-/worktreespezifische Berechtigungen für Lesen, Testausführung, lokale Buildausgaben und Reviewdateien einrichten beziehungsweise vorhandene geeignete Einstellungen verwenden. Spätestens TST3 benötigt der Reviewer Zugriff auf den lokalen Docker-Daemon für die vereinbarten Testcontainer; dieser Zugriff gehört **nicht** in den GRETL-Produktcontainer.
7. Eine kurze harmlose Bereitschaftsprüfung in der tatsächlich später benutzten privaten Ausführungskonfiguration durchführen: Repository lesen, einen ungefährlichen Befehl ausführen und eine Probeantwort in einen Scratch-Ausgabeordner schreiben. So werden fehlende Shell-/Schreibrechte und nicht funktionierende Authentifizierung früh erkannt. Das ist noch kein Phasenreview.

Metadaten genügen für den Preflightnachweis: Pfad/Version, Provider-/Modellschlüssel, API-Host, Datum der Aliasprüfung und Probe-Exit. Keine Schlüssel, Authheader, vollständigen Umgebungsdumps oder Credentials in `STATUS.md`, Prompts, Requests, Logs oder Git. Private Rohdaten nur so lange wie nötig aufbewahren und nicht als allgemeine CI-Artefakte hochladen.

### 12.3 Exakter Commit, Phasenbasis und unveränderlicher Kandidat

Für **jede** Phase:

1. Beim Start der Phase vollständigen `BASE_SHA` festhalten. Er bleibt über alle Korrekturrunden dieser Phase unverändert. `ASSIGNMENT_BASE_SHA` bezeichnet den Ausgangsstand des gesamten Auftrags und bleibt ebenfalls unverändert.
2. Implementieren, gezielt testen, alle fälligen Gates ausführen und einen kurzen Implementierungsbericht erstellen. Code, Tests, Build-/Dokumentationsänderungen und Bericht in einen sauberen **lokalen Kandidatencommit** aufnehmen; keine fremden Änderungen mitcommitten.
3. Erst **nach dem Commit** `CANDIDATE_SHA=$(git rev-parse HEAD)` lesen. Diesen SHA nicht selbstreferenziell in denselben Commit schreiben. Die verbindlichen Nachweise werden am festen Commit frisch ausgeführt; vor dem Commit ausgeführte Entwicklungschecks sind ausdrücklich als solche zu unterscheiden.
4. Die vollständigen fälligen eigenen Gates am Kandidaten in einem sauberen Stand wiederholen. Sie dürfen keine getrackten Produkt-/Testdateien ändern. Muss doch Code korrigiert werden, neuer Kandidat und erneute Pflichtprüfungen. Build-/Reviewausgaben liegen in ignorierten beziehungsweise separaten Verzeichnissen.
5. Für Agent B einen neuen detached Worktree auf exakt `CANDIDATE_SHA` erstellen. Jede neue Reviewrunde erhält eigenen Kontext, eigenen Ausgabeordner, eigene Suite-Run-IDs und eigene Buildverzeichnisse. Maven-Downloadcaches dürfen verwendet werden, aber keine alten `target`-Reports oder Testartefakte als neue Ausführung.
6. Agent B liest `BASE_SHA..CANDIDATE_SHA`, nicht nur `HEAD~1..HEAD`. Bei TST4 zusätzlich `ASSIGNMENT_BASE_SHA..CANDIDATE_SHA`. Der tatsächliche Codezustand und frühere Verträge sind zu prüfen, nicht ausschliesslich der Diff.
7. Während des Reviews bleibt der Kandidat unverändert. Agent A darf schon Gedanken für später notieren, aber keine spätere Phase implementieren und keinen laufenden Reviewer-Checkout verändern. Ein eigener Worktree verhindert Arbeitsverzeichniskollisionen; er ersetzt keine saubere Revisionstrennung.
8. Eigene Reviewer-Gegenproben entstehen in separaten Scratch-Verzeichnissen oder einer weiteren Kopie. Kandidatenquellen/-tests nicht patchen und anschliessend mit Reset „sauber machen“. Ein sinnvoller dauerhafter neuer Regressionstest geht als Finding an Agent A und wird in einem **neuen** Kandidaten geprüft.

Die Phasenbasis der nächsten Phase darf der reine Nachweiscommit nach dem letzten PASS sein. Der geprüfte Produkt-SHA bleibt zusätzlich ausdrücklich dokumentiert. Beim Archivieren eines Reviews keinen neuen Produkt-PASS für den Dokumentationscommit behaupten.

### 12.4 Übergabe mit `request.json`

Agent A erzeugt die Übergabe **ausserhalb von Verzeichnissen, die `mvn clean` löscht**. Empfohlen ist ein privater Ordner neben dem Reviewworktree. Das gesamte Reviewverzeichnis liegt nicht im Produkt-Checkout und nicht im Maven-`target`-Baum. Der Reviewer darf dort Probequellen und Berichte schreiben.

```json
{
  "schemaVersion": 1,
  "assignment": "gretl-script-first-testing",
  "phase": "TST1",
  "attempt": 1,
  "baseSha": "<vollständiger Phasenbasis-SHA>",
  "assignmentBaseSha": "<vollständiger Auftragsbasis-SHA>",
  "candidateSha": "<vollständiger Kandidaten-SHA>",
  "reviewWorktree": "/absolut/zum/eigenen/review-worktree",
  "reviewDirectory": "/absolut/zum/separaten/review-output",
  "plan": "docs/implementation/script-first-testing/EXECUTION_PLAN.md",
  "reviewPrompt": "docs/implementation/script-first-testing/OPENCODE_REVIEW_PROMPT.md",
  "implementationReport": "docs/implementation/script-first-testing/implementation/TST1.md",
  "criteria": ["TST1-01", "TST1-02", "...vollständige fällige Liste..."],
  "requiredChecks": [
    {"id": "standard", "argv": ["./mvnw", "-B", "clean", "verify"], "sourceSha": "<Kandidaten-SHA>"},
    {"id": "phase-additional", "description": "<tatsächliches weiteres Gate aus §11>"}
  ],
  "reviewer": {
    "provider": "deepseek",
    "model": "deepseek-flash",
    "apiHost": "api.deepseek.com",
    "requestedVersion": "DeepSeek-V4.1-Flash-1017"
  }
}
```

Das JSON ist ein **Schema-/Inhaltsbeispiel**, keine fertige Übergabe. Keine `...`-Platzhalter in tatsächlichen Requests belassen. `criteria` enthält TST1/TST2/TST3 jeweils vollständig für die aktive Phase; TST4 enthält alle 38 Kriterien. Bestehende Regressionen bleiben ebenfalls fällig. Der Reviewer leitet die Kriterien selbst aus dem Plan ab; ein unvollständiges Handoff darf seinen Prüfauftrag nicht verkleinern.

`requiredChecks` enthält die tatsächlich ausführbaren Kommandos und die erforderlichen Laufbedingungen. Ein Satz „alle Tests grün“ genügt nicht. Zulässige CI-Belege werden als konkrete Run-/Artefaktreferenzen dieses SHAs ergänzt; kein alter Build und keine frei erfundene ID. Agent A übergibt eigene Logs zur Orientierung, aber sie ersetzen nicht die unabhängige Testausführung.

### 12.5 Privater, tatsächlich beendeter OpenCode-Prozess

Der Ausgangspunkt aus dem funktionierenden Nutzerbeispiel ist ein **privater Standaloneprozess**, sofern die installierte V2-Version ihn für `run` anbietet. Die offizielle CLI-Dokumentation verlangt bei versionabhängigen Flags den Help-Check; sie dokumentiert `run`, Modellauswahl, JSON-Ausgabe und Dateianhänge. [E1] Keine nicht geprüften Flags hinzufügen.

Beispiel nach lokalem Help-/Berechtigungscheck, **kein hier ausgeführter Aufruf**:

```sh
# In Bash, im sauberen Arbeits-Checkout nach Kandidatencommit und eigenen Gates.
# PHASE, ATTEMPT, BASE_SHA und ASSIGNMENT_BASE_SHA stammen aus dem Status.
# OPENCODE_BIN ist der verifizierte absolute Pfad zu OpenCode 2.x.
CANDIDATE_SHA=$(git rev-parse HEAD)
REPO_PARENT=$(dirname "$PWD")
REVIEW_WT="$REPO_PARENT/gretl-review-${PHASE}-${ATTEMPT}-${CANDIDATE_SHA:0:12}"
REVIEW_DIR="$REPO_PARENT/gretl-review-output-${PHASE}-${ATTEMPT}-${CANDIDATE_SHA:0:12}"

# Nicht über ein vorhandenes fremdes Verzeichnis schreiben.
umask 077
mkdir "$REVIEW_DIR"
git worktree add --detach "$REVIEW_WT" "$CANDIDATE_SHA"
# request.json jetzt aus den tatsächlichen Werten erzeugen, ohne Secrets.

if (
  cd "$REVIEW_WT" || exit 1
  "$OPENCODE_BIN" run --standalone \
    --model deepseek/deepseek-flash \
    --format json \
    --file docs/implementation/script-first-testing/EXECUTION_PLAN.md \
    --file docs/implementation/script-first-testing/OPENCODE_REVIEW_PROMPT.md \
    --file "$REVIEW_DIR/request.json" \
    "Prüfe die beauftragte Phase unabhängig am angegebenen Commit. Führe die Pflichtprüfungen und eigene Gegenproben aus. Schreibe review.md und verdict.json ausschliesslich in das übergebene Reviewverzeichnis."
) >"$REVIEW_DIR/opencode.jsonl" 2>"$REVIEW_DIR/opencode.stderr.log"; then
  REVIEW_PROCESS_EXIT=0
else
  REVIEW_PROCESS_EXIT=$?
fi
printf '%s\n' "$REVIEW_PROCESS_EXIT" >"$REVIEW_DIR/process-exit.txt"
# Erst nach diesem Punkt review.md/verdict.json und tatsächlichen HEAD validieren.
```

Der aus dem Beispiel nicht übernommene `without-python.cjs` ist kein GRETL-Werkzeug und wird nicht als existent vorausgesetzt. Die hier benötigten Maven-/Audit-/Dockerprozesse müssen ausführbar sein. Es ist kein Auftrag, einen neuen allgemeinen Agentenguard zu entwickeln. Vorhandene sinnvolle lokale Schutzmechanismen bleiben aktiv.

Falls die installierte OpenCode-Version einen expliziten Server verlangt, einen eigenen privaten, nur lokal erreichbaren Server mit geprüftem Profil verwenden. Bereitschaft prüfen, Session gezielt starten, auf ihren tatsächlichen Abschluss warten und den eigenen Server danach beenden. Keine bestehende fremde GUI-/Implementierungssitzung übernehmen. Keine `--continue`-/alte `--session`-Wiederverwendung aus der Implementierung.

Das einfache Shellbeispiel setzt einen synchron abschliessenden CLI-Vertrag voraus. Gibt eine lokale CLI nur eine Session-ID zurück und beendet sich, muss der Helfer den tatsächlichen Sessionstatus über die **vorhandene dokumentierte** Schnittstelle abwarten. Ein Exit 0 des Startbefehls ist dann nur „Start akzeptiert“, kein Reviewabschluss.

Beide Agenten warten auf alle gestarteten Pflichtprozesse. Bei Pipelines den Exit des tatsächlichen OpenCode-/Maven-/Dockerprozesses auswerten, nicht den von `tee`. Bei Transportfehler oder intern abgebrochener Session höchstens zwei saubere Wiederholungen desselben unveränderten Kandidaten; danach konkretes BLOCKED. Fachliches FAIL nicht durch Wiederholen bis zum zufälligen PASS umgehen. Ein neuer Kontext nach einer begründeten Korrektur ist dagegen der normale Reworkweg.

### 12.6 Kleine technische Reviewvalidierung, kein neues Agentenframework

Vorhandene geeignete Orchestrierung wiederverwenden. Falls keine vorhanden ist, genügt beispielsweise ein **lokaler** `scripts/review-phase.mjs` mit eingebauten Node-Modulen oder ein gleichwertiger kleiner Shell-/Java-Helfer. Er gehört nicht zur GRETL-Produktruntime und braucht keine neuen npm-Abhängigkeiten. Kein eigener LLM-Client: der Modellaufruf erfolgt weiterhin durch OpenCode.

Vorgesehene Verantwortungen bis Methodenlevel:

```javascript
// Entwurf für einen kleinen Node-Helfer; kein bereits implementiertes API.
async function checkOpenCode(binary, cwd) { /* Version/Help, keine Secrets */ }
function validateRequest(request, planCriteria) { /* Schema, SHAs, Pfade, Menge */ }
async function createReviewWorktree(repo, candidateSha, destination) { /* git */ }
async function runReview({ binary, worktree, requestFile, outputDirectory }) {
  /* spawn ohne Shellinterpretation; eigene Logs; wait auf close/Sessionende */
}
async function assertCandidateUnchanged(worktree, candidateSha) {
  /* HEAD, tracked/untracked Zustand ausser genehmigten Buildausgaben */
}
function validateVerdict(request, verdict, reviewText, processResult) {
  /* fail closed; keine eigene Erzeugung eines Reviewer-PASS */
}
async function archiveReview(verdict, sourceDirectory, targetDirectory) {
  /* unveränderte Bytes; Hash/Pfad; keine Produktänderung */
}
```

Die Funktionen sind eine Aufgabenteilung, keine Pflicht zu exakt diesem Dateinamen, wenn gleichwertige geprüfte Helfer bestehen. Teste die Reviewvalidierung mit kleinen synthetischen JSON-/Prozessfällen, klar **als Orchestrierungstests**, niemals als angebliche echte DeepSeek-Reviews.

Mindestens folgende Negativfälle: fehlender Bericht, ungültiges JSON, falscher SHA, falsche Phase, fehlendes Kriterium, doppeltes Kriterium, unbekanntes Urteil, `PASS` mit offenem Pflichtfinding, fehlender Pflichtlauf, falscher Provider/Host, laufende oder abgebrochene Session, veränderter Worktree, Exitfehler trotz PASS im Log. Bei jeder dieser Konstellationen muss der Phasenwechsel unterbleiben. Kein Regex auf das erste Wort „PASS“.

Die inhaltliche Qualität der Reviewbelege bleibt zusätzlich zu lesen: Ein JSON-Feld `fulfilled` ist allein kein Testnachweis. Der Helfer muss keine komplette natürliche Sprache beweisen; er prüft die technischen Mindestbedingungen. Agent A prüft die behaupteten Belegpfade, tatsächlichen Rohreports und Widersprüche zwischen Bericht/JSON.

### 12.7 `verdict.json` und Reviewerbericht

Agent B schreibt nach der tatsächlichen Prüfung:

```json
{
  "schemaVersion": 1,
  "assignment": "gretl-script-first-testing",
  "phase": "TST1",
  "attempt": 1,
  "baseSha": "<vollständiger Phasenbasis-SHA>",
  "assignmentBaseSha": "<vollständiger Auftragsbasis-SHA>",
  "candidateSha": "<vollständiger Kandidaten-SHA>",
  "verdict": "PASS|FAIL|BLOCKED",
  "reviewer": {
    "provider": "deepseek",
    "model": "deepseek-flash",
    "apiHost": "api.deepseek.com",
    "openCodeVersion": "<tatsächlich beobachtete 2.x-Version>",
    "modelMappingEvidence": "<Konfiguration und aktuelle Aliaszuordnung ohne Secrets>"
  },
  "criteria": [
    {"id": "TST1-01", "result": "fulfilled|violated|unverified", "evidence": ["<Belegpfad>"]}
  ],
  "checks": [
    {"id": "standard", "result": "passed|failed|blocked", "evidence": ["<eigener Rohreport dieses SHAs>"]}
  ],
  "counterprobes": [
    {"description": "<selbst entworfene Hypothese>", "evidence": ["<Input, Aufruf und tatsächliches Ergebnis>"]}
  ],
  "openMandatoryFindings": [],
  "missingRequiredChecks": [],
  "report": "review.md"
}
```

Die Alternativen mit `|` sind Schemaerläuterungen und werden in einer echten Ausgabe jeweils durch **einen** gültigen Wert ersetzt. Vollständige Kriterien-/Prüflisten schreiben, nicht nur das Beispiel. SHA-Werte sind vollständige tatsächliche Git-Objekt-IDs; am beschriebenen Repository 40 Hexzeichen.

`review.md` enthält: Phase/Basis/Kandidat/HEAD, tatsächliche Umgebung und Modellkonfiguration, Tabelle aller fälligen Kriterien, gelesene relevante Codepfade, konkrete eigene Testkommandos mit Exits und Testzahlen/Failures/Errors/Skips, neue Gegenproben, fachliche Findings und genau ein Urteil. Bei TST4 kumulative Prüfung ausdrücklich ausweisen. Eigene Beobachtungen von lediglich gelesenen CI-Ergebnissen unterscheiden.

Agent B kann seinen **äusseren** endgültigen Prozess-Exit vor seinem eigenen Ende nicht kennen. Er behauptet ihn nicht im Bericht. Diesen prüft Agent A nach Prozess-/Sessionende getrennt. Ein kurz zuvor bereits geschriebener PASS-Bericht reicht bei abgebrochenem Transport nicht für die automatische Freigabe.

### 12.8 Annahmebedingungen, Rework und Fortsetzung

Ein PASS wird nur akzeptiert, wenn gleichzeitig:

- tatsächlicher Reviewprozess und gegebenenfalls private Session vollständig abgeschlossen sind; kein unbekannter Transportzustand und keine unbeendeten Pflichtprozesse;
- tatsächlicher Review-HEAD gleich `candidateSha`, vollständiger Phasen-Diff geprüft und Kandidat unverändert;
- OpenCode 2.x, geforderter direkter Provider/Modell/API-Host und konkrete Aliasprüfung belegt;
- Bericht und JSON gültig, konsistent und nicht von Agent A selbst geschrieben oder „verbessert“;
- alle aus dem Plan fälligen Kriterien genau einmal enthalten und mit nachvollziehbaren eigenen Belegen als erfüllt beurteilt;
- alle Pflichtgates tatsächlich erfolgreich und am Kandidaten, keine versteckten Skips/Zero-Tests und mindestens eine eigene ausgeführte Gegenprobe;
- keine offenen Muss-Findings oder fehlenden Pflichtnachweise;
- bei TST4 zusätzlich sämtliche früheren Kriterien am finalen Stand erneut kumulativ geprüft.

Bei FAIL Findings reproduzieren und die Ursache in derselben Phase korrigieren. Danach neuer Kandidat, neue feste Gates und neuer unabhängiger Review; `BASE_SHA` bleibt unverändert. Reviewer schreibt niemals den Produktfix selbst. Bei BLOCKED eigenständig behebbare Voraussetzungen herstellen und begrenzt neu versuchen. Verbleibt ein echter externer Blocker, Auftrag an genau dieser Stelle mit nachvollziehbarer Ursache anhalten; keinen erfolgreich abgeschlossenen Phasenstand behaupten.

Nach PASS unverändertes `review.md` unter `reviews/` ablegen, dessen SHA-256 und Kandidaten-SHA in `STATUS.md` vermerken und gegebenenfalls das kleine tatsächliche `verdict.json` mitarchivieren. Rohlogs bleiben in privaten beziehungsweise dafür freigegebenen Evidenzverzeichnissen. Ein reiner Nachweis-/Statuscommit braucht keinen künstlichen zweiten Produktreview. **Jede Änderung an Produkt, Tests, Build, Fixtures, Kriterien oder fachlicher Dokumentation nach PASS** ist dagegen ein neuer prüfpflichtiger Kandidat.

## 13. Ablage, Definition of Done und Abschluss

### 13.1 Dokumente und Pfadbasis

Alle im Auftrag bezeichneten Repositorypfade sind relativ zum Root von `gretl-runtime`, sofern sie nicht ausdrücklich absolut oder dokumentrelativ sind.

```text
docs/implementation/script-first-testing/
  README.md
  EXECUTION_PLAN.md
  CODEX_STARTPROMPT.md
  OPENCODE_REVIEW_PROMPT.md
  STATUS.md                         # von Agent A erzeugt, kurz
  implementation/
    TST1.md
    TST2.md
    TST3.md
    TST4.md
  reviews/
    TST1-r1-<shortsha>.md            # unverändert von Agent B
    TST1-r1-<shortsha>.verdict.json  # optional klein mitarchivieren
    ...
```

Tatsächliche neue Produkt-/Testdateien gehören in ihre vorhandenen Module, nicht in dieses Dokumentationsverzeichnis. Die JSON-Szenariomatrix gehört nach §9.3 in die Testressourcen. Testlogs, Containerlogs, fertige grosse JARs, lokale Secrets und volle OpenCode-Transkripte nicht in Git aufnehmen. Vor jedem `clean` die erforderliche Evidenz nach dem beschriebenen Verfahren sichern.

Die vier mitgelieferten Markdown-Dateien **vor dem Start des Auftrags versionieren**. So prüfen beide Agenten denselben Plan. Nicht die alten Unterprojektprompts überschreiben: dieser Auftrag ist ein klar abgegrenzter Folgeauftrag. Neuere lokale Bereinigungen und Pfadänderungen anhand des tatsächlichen Checkouts berücksichtigen.

### 13.2 Definition of Done

Abgeschlossen ist der Auftrag erst, wenn TST1-01 bis TST1-10, TST2-01 bis TST2-10, TST3-01 bis TST3-10 und TST4-01 bis TST4-08 am finalen geprüften Kandidaten erfüllt sind und der unabhängige kumulative TST4-Review mit tatsächlich beendetem Prozess gültig PASS lautet.

Insbesondere nicht ausreichend sind:

- ein neues Enum mit drei Modi ohne unabhängigen SCRIPT-Vorbereitungsweg;
- ein SCRIPT-Test, der vorher ein Job-JAR braucht;
- ein Image-Test, der nur Dockerargumente zusammenstellt oder ein jobspezifisches JAR startet;
- ein gepinntes Imageetikett ohne Prüfung der tatsächlichen Runtimebytes;
- alle Operationen nur in der **Vereinigung** dreier unvollständiger Modi;
- ein eigener grüner Codex-Bericht ohne unabhängige OpenCode-Prüfung;
- eine fertig geschriebene CI-Datei ohne die fälligen tatsächlichen lokalen/CI-Produktläufe;
- ein Review-PASS, dessen Kandidaten nachträglich verändert wurden.

### 13.3 Abschlussausgabe von Agent A

Kurz, aber überprüfbar berichten:

1. Tatsächliche Einstiegskommandos für SCRIPT, IMAGE und JAR sowie die Standard-/Vollmatrix.
2. Reale neue/geänderte Klassen, Methoden, Profile, Skripte und die bewusst erhaltenen Pakettests.
3. Fachliche Szenario- und Operationsabdeckung **getrennt je Modus**, inklusive tatsächlicher Testzahlen/Failures/Errors/Skips; keine erfundene externe JaCoCo-Coverage.
4. Echte Image-ID, Runtime-/Quellidentität und ein Ergebnisbeispiel mit verändertem externem Job **ohne Image-/Runtime-Neubau**.
5. Die vier geprüften Kandidaten-SHAs, unabhängigen Reviewpfade und Urteile; finaler Produkt-SHA von reinen Nachweiscommits unterscheiden.
6. Tatsächlich eingesetzte Host-/Containerplattformen, bewusste Nichtziele und gegebenenfalls verbleibende externe Blocker. Keine Veröffentlichung behaupten.

## 14. Quellen, Herkunft und Grenzen dieser Spezifikation

**Einordnung:** Die fachlichen Sollentscheidungen, Testmatrix und neu entworfenen Klassen/Methoden sind Vorgaben dieses Auftrags. Die mit Q bezeichneten Quellen belegen den analysierten Ist-Zustand. Die U-Dateien sind das vom Nutzer mitgelieferte funktionierende **Verfahrensbeispiel eines anderen Projekts**, nicht die aktuelle GRETL-Implementierung. Die E-Quellen wurden zur Prüfung der Werkzeug-/Modellbezeichnungen gelesen. Es wurden bei der Dokumenterstellung keine GRETL-Builds, Dockerqualifikationen oder OpenCode-Reviews ausgeführt.

### Nutzerbeispiel

- **U1:** `CODEX_STARTPROMPT(3).md` – Rollen, autonomer Phasenablauf, Commit-/Reviewzyklus und tatsächliches Warten auf OpenCode. SHA-256: `9bb334301fab28028d87dfd73a6060ffb8a2034c8282af8eb831a59f265c3bf7`.
- **U2:** `EXECUTION_PLAN(3).md`, besonders Abschnitte 7–10 – Kriterien, unabhängige Gates, exakter Prüfstand, Providerprüfung, Request-/Verdictvertrag und Fortsetzung. SHA-256: `fb750eee514a526a7dbb231c1625193969ecdcd7c6787500f5df3b5bb008d5dc`.
- **U3:** `OPENCODE_REVIEW_PROMPT(3).md` – Unabhängigkeit, vollständiger Diff, eigene Gegenproben, drei Urteile und Berichte. SHA-256: `53b89ecf9416db01c00f7b22c8596c8492d0a20415b7f0782ddde5dd91c4391f`.

Die Beispiele werden nicht in das GRETL-Paket kopiert. Compiler-/Browser-/Python-Guardpflichten bleiben wie in §1.4 abgegrenzt.

### GRETL-Quellcode am untersuchten Commit

Alle folgenden Links sind auf `8eec03184883ce9d585d46a768b647f2b7a19169` fixiert:

- **Q1 – Gemeinsamer Prozessrunner:** [ArtifactJobRunner.java](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ArtifactJobRunner.java).
- **Q2 – IMAGE-Adapter:** [ImageExecutionAdapter.java](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ImageExecutionAdapter.java).
- **Q3 – Bestehender Artefaktaudit:** [audit-job-evidence.py](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/scripts/audit-job-evidence.py).
- **Q4 – Bereits unabhängiger Projekt-SCRIPT-Weg:** [ProjectScriptLocalJobIT.java](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ProjectScriptLocalJobIT.java).
- **Q5 – Projektpakete und bisher vermischte Parität:** [ProjectArtifactLocalJobIT.java](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/ProjectArtifactLocalJobIT.java).
- **Q6 – Teststrategie und Dienste:** [testing.md](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/docs/implementation/testing.md), [PostgresFixture.java](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/gretl-integration-tests/src/test/java/ch/so/agi/gretl/it/PostgresFixture.java).
- **Q7 – Echter Produktlauncher:** [bin/gretl](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/gretl-dist/src/main/dist/bin/gretl).
- **Q8 – Vorhandene Pflichtprüfungen und Archivierung:** [publish-snapshots.yml](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/.github/workflows/publish-snapshots.yml).
- **Q9 – Referenzrevision und Arbeitsregeln:** [Commit](https://github.com/edigonzales/gretl-runtime/commit/8eec03184883ce9d585d46a768b647f2b7a19169), [AGENTS.md](https://github.com/edigonzales/gretl-runtime/blob/8eec03184883ce9d585d46a768b647f2b7a19169/AGENTS.md).

### Offizielle Werkzeug-/Modellquellen, geprüft am 01.11.2026

- **E1:** [OpenCode V2 – CLI Commands](https://opencode.ai/v2/docs/cli/commands/). Massgeblich für tatsächlich ausführbare Optionen bleibt zusätzlich `run --help` der installierten Version.
- **E2:** [DeepSeek – Model Details](https://api-docs.deepseek.com/quick_start/pricing/). Gelesene Zuordnung: API-Modell `deepseek-flash`, Modellversion DeepSeek-V4.1-Flash-1017, direkter Host `api.deepseek.com`. Beim realen Review erneut prüfen; keine unveränderliche Aliasgarantie.
- **E3:** [DeepSeek-V4.1-Flash-1017 – Veröffentlichung](https://api-docs.deepseek.com/news/news261017/).

**Ende des verbindlichen Umsetzungsplans.**
