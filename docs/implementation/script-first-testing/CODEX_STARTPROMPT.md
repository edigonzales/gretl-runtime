# Codex-Startprompt – GRETL SCRIPT-first-Testing

Du bist **Agent A: Implementierung und Orchestrierung** im bestehenden Repository `edigonzales/gretl-runtime`. Dieser Prompt ist der ausdrückliche Auftrag, die **vier Phasen TST1, TST2, TST3 und TST4 vollständig und nacheinander** gemäss dem Plan umzusetzen. Du führst Tests aus, startest die unabhängigen Reviews selbst und arbeitest nach jedem belegten PASS autonom weiter. Es genügt nicht, eine weitere Spezifikation, einen Reviewprompt oder bloss vorbereitete Dockerkommandos zu liefern.

## 1. Verbindliche Grundlage und Nutzerentscheidung

Lies und befolge:

- `AGENTS.md` und die aktuellen Regeln betroffener Pfade;
- `docs/implementation/script-first-testing/EXECUTION_PLAN.md` **vollständig**;
- `docs/implementation/script-first-testing/OPENCODE_REVIEW_PROMPT.md`;
- die tatsächlich aktuellen Test-, DSL-, Subprojekt-, Paketierungs-, Laufzeit-, Logging-/Cancellation- und Distributionsverträge der betroffenen Änderungen.

Alle Repositorypfade sind relativ zum Root von `gretl-runtime`.

**SCRIPT ist die primäre und voraussichtlich häufigste Ausführungsart.** Ein wirklicher Test startet die tatsächlich gebaute GRETL-Distribution als eigenen Prozess mit einer externen Groovy-Datei oder einem externen Projektverzeichnis:

```sh
<geprüfte-distribution>/bin/gretl run <externe-jobdatei-oder-projekt>
```

**IMAGE verwendet innerhalb des Containers genau diesen SCRIPT-Weg.** Das Image enthält die generische GRETL-Runtime. Der konkrete Job wird extern eingebunden. **Kein eingebauter Job, kein `java -jar job.jar` als IMAGE-Ersatz und keine Jobpaketierung vor SCRIPT oder IMAGE.** Normale Runtimebibliotheks-JARs im Image sind selbstverständlich zulässig.

**JAR bleibt eine unterstützte und zusätzlich geprüfte Auslieferungsform.** Erhalte alle bisherigen relevanten Paket-, Domain-, Sicherheits-, Subprojekt- und Lebensdauertests. COMMON-Szenarien verwenden gleiche Quellen und gleiche unabhängige fachliche Assertions in SCRIPT, IMAGE und JAR. Ein Modus darf den anderen nicht als Nachweis ersetzen.

Kein Runtime-Rewrite, kein neues allgemeines Test-/Agentenframework, keine neue Pipeline-DSL und kein pauschales Update der Toolchain. Die vorhandene Infrastruktur gezielt weiterentwickeln.

## 2. Arbeitsbeginn

1. Prüfe `pwd`, Git-Status, Zweig, vollständigen HEAD und die Arbeitsregeln. Referenzstand des Plans ist `8eec03184883ce9d585d46a768b647f2b7a19169`, **kein Reset-Ziel**. Neuere lokale Subprojekt-/Bereinigungsarbeiten erhalten; nicht mit fremden laufenden Änderungen konkurrieren oder diese mitcommitten.
2. Ermittle die tatsächlich vorhandenen Klassen und Methoden. Insbesondere Aufrufer von `ArtifactJobRunner`, direkte Prozessstarts, `ImageExecutionAdapter`, `ProjectScriptLocalJobIT`, `ProjectArtifactLocalJobIT`, Pakettests, Audit, Maven-Profile und CI prüfen. Bestehende gleichwertige Umsetzung nachweisen statt neu schreiben.
3. Lies Java-/Maven-/Groovy-Pins und effektive Testeinschlüsse. Verwende den vorhandenen Wrapper. Bestehende Standardprüfung und verfügbare Vollprüfung als echte Baseline durchführen; Fehler und fehlende Voraussetzungen konkret abgrenzen.
4. Erstelle eine kurze `docs/implementation/script-first-testing/STATUS.md` mit Auftragsbasis-SHA, TST1, Zustand und nächster Aktion. Keine separate lange Vorbereitungsphase und keine neue globale Inventarplattform.
5. Führe jetzt den OpenCode-/DeepSeek-Preflight nach Abschnitt 5 dieses Prompts und Plan §12 durch. Dockerzugriff, Mountfähigkeit und Plattform ebenfalls früh feststellen; echte IMAGE-Läufe werden spätestens TST3 Pflicht.
6. Beginne TST1. Unterstützte Projekte sind am untersuchten Stand bereits implementiert; nicht das alte Subprojektprojekt nochmals entwickeln.

Du darfst im vereinbarten Umfang lokale Implementierung, Builds, Tests, eigene Worktrees, lokale Commits, Testimages/-netze und direkte DeepSeek-Reviews selbständig ausführen. Kein automatischer Push, Merge, Release, Image-Push, Produktionszugriff oder globale Credentialänderung. Vorhandene berechtigte CI-Wege darfst du nutzen; fehlende Rechte nicht umgehen.

## 3. Die vier Phasen

### TST1 – Wirklich eigenständiger SCRIPT-Weg

**Kriterien: TST1-01 bis TST1-10.**

- SCRIPT als eindeutigen Modus und Default der gemeinsamen Fachtests implementieren. Kein nachträgliches `boolean script`, das einem JAR-Prepared einen anderen Startmodus gibt.
- Die vorhandene `ArtifactJobRunner`-Infrastruktur weiterentwickeln. Gemeinsame Workspacevorbereitung von `ScriptExecutionAdapter`, `JarExecutionAdapter` und dem bestehenden `ImageExecutionAdapter` trennen; kleine Records/Enums nach Plan §4.
- `JobInput` und `Invocation` tragen explizite Datei-/Projektwahl und vorhandene CLI-Befehle. Kein hartcodiertes `job.groovy` in allen Pfaden. Ungültige Groovy-/Settings-Inhalte müssen den wirklichen Produktprozess erreichen können.
- SCRIPT verwendet den echten Kandidatenlauncher, externe Quellen und keinen Testclasspath. Kein `GretlCli.run()` als Prozessersatz. **Ohne Paketdeskriptor und auch mit einem unbenutzten absichtlich ungültigen Paketdeskriptor erfolgreich laufen.**
- Distribution/Input-Identität, abgesicherte Prozessumgebung, begrenzte Lebensdauer und getrennte Fehlerzustände umsetzen. Bytebasiertes Inputinventar darf nicht vorab den Projektparser ausführen.
- Vorhandene echte Projekt-SCRIPT-Tests erhalten. Alle betroffenen bisherigen Boolean-/Prepared-Aufrufer migrieren. Pakettests explizit JAR halten; keine Assertions verlieren.
- Standardprüfung ohne Docker und fällige JAR-Regression/Audit nach Plan §11.1 durchführen. Kurzer Implementierungsbericht, Kandidatencommit, feste eigene Gates, dann unabhängiger Review.

### TST2 – Vollständige gemeinsame Fachsuite

**Erst nach gültigem TST1-PASS. Kriterien: TST2-01 bis TST2-10.**

- Methoden/Varianten gezielt in COMMON, SCRIPT_CONTRACT, IMAGE_CONTRACT, PACKAGE_CONTRACT oder COMPONENT einteilen. Kleine Testdelta-Tabelle; keine globale Massenlöschung oder Namensbereinigung.
- Bestehende Fachfixtures und unabhängige Ergebnisprüfungen für SCRIPT/JAR gemeinsam nutzen. Vorherige JAR-Artefaktassertions von gemeinsamen fachlichen Assertions trennen.
- Vollständige Operationen, negative Varianten, Projektgraphen, lokale Ressourcen, gleichnamige Subprojekttasks, Teilziele, Parameter, ursprüngliche Quellstellen, Cleanup und Abbruch über echte Prozesse prüfen.
- Reale PostgreSQL/PostGIS-, FTP-, S3- und lokale HTTP-/Dateidienste verwenden. Kein Mocknachweis für eine versprochene echte Dienstintegration.
- `scenarios.json`, modespezifische Evidenz und vorhandenen Audit nach Plan §9 erweitern. Vollständigkeit anhand unabhängig festgelegter Sollfälle plus wirklicher JUnit-Ergebnisse prüfen. Positive Operationen getrennt je Modus, nicht als Vereinigungsmenge.
- Den kleinen Matrixstarter für SCRIPT/JAR local/full implementieren; Nachweise vor jedem `clean` sichern, tatsächliche Exits erhalten. Die Fehlgrün-Proben dieser Phase ausführen.
- Standardprüfung, vollständige SCRIPT- und vollständige JAR-Gates durchführen, committen und unabhängig prüfen lassen.

### TST3 – Wirkliche IMAGE-Ausführung mit externen Quellen

**Erst nach gültigem TST2-PASS. Kriterien: TST3-01 bis TST3-10.**

- Vorhandenen generischen Imagebau nutzen oder den kleinen spezifizierten Dockerfile/Buildweg ergänzen. Kandidatendistribution und tatsächliche Runtimebytes im Image prüfen. Keine testspezifischen Jobs in den Buildkontext aufnehmen.
- Der Container führt den Produktlauncher mit `run /job/...` aus. Teste den tatsächlichen Default-Entrypoint, nicht nur einen Testoverride, der einen defekten Imageentrypoint verdeckt.
- **Dasselbe unveränderte Image** muss einen erst nach dem Imagebau erzeugten Job, einen anderen Job und einen danach geänderten externen Job mit jeweils fest erwarteten Ergebnissen ausführen. Kein Image-/Runtime-Neubau dazwischen.
- Echte non-root Ausführung, schreibgeschützte Jobmounts, beschreibbare Work-/Report-/Output-/Tempbereiche, read-only Rootfall, JNI-/DuckDB-/SQLite-Arbeit und explizite Host-/Containerpfade prüfen.
- Reale Dienstendpoints im eigenen Testnetz, kontrollierten Abbruch, Signal-/Exitverhalten und vollständiges Cleanup der eigenen Ressourcen nachweisen. Keine globale Dockerbereinigung, kein `chmod -R 777` als Lösung.
- Der vorhandene `ImageExecutionAdapter` ist bereits grundsätzlich SCRIPT-basiert. Seine guten Pfad-/Secret-/Endpointregeln erhalten; RecordingEngine-Tests ergänzen, aber nicht als echte Imageabnahme ausgeben.
- Standard- und vollständige SCRIPT/JAR-Regression sowie echtes IMAGE/full und den dokumentierten IMAGE/local-Einstieg durchführen. Fachliche Assertions und Operationsabdeckung separat für IMAGE prüfen. Committen und unabhängig überprüfen lassen.

### TST4 – Reguläre CI und kumulative Gesamtabnahme

**Erst nach gültigem TST3-PASS. Kriterien: TST4-01 bis TST4-08 sowie alle TST1–TST3-Kriterien kumulativ.**

- Standardweg bleibt Dockerfrei und SCRIPT-first. Volle CI-Matrix umfasst SCRIPT, JAR und tatsächliches IMAGE-SCRIPT auf demselben Quellencommit.
- Bestehende Veröffentlichungswege dürfen nicht vor den vorgesehenen Pflichtgates freigegeben werden. Keine nur manuelle Imagequalifikation, keine `continue-on-error`-Kaschierung.
- Alle Negativproben gegen fehlende/falsche/alte Nachweise, vertauschte Modi, falsche Runtime-/Imagebytes, übersprungene Fälle und fehlerhafte Prozessabschlüsse abschliessen.
- Aktive Test-/Betriebs-/Imageanleitungen und Beispiele aktualisieren. Historische Berichte nicht rückwirkend umschreiben; keine unbeteiligte Dokumentationsbereinigung.
- Am finalen unveränderten Kandidaten die frische Standardprüfung und `scripts/verify-job-matrix.sh --mode ALL --scope full` ausführen. Fällige nicht transitiv enthaltene Altgates ergänzen.
- OpenCode führt den kumulativen Review für **alle 38 Kriterien** aus. Dieser letzte Review ist zugleich die Gesamtabnahme, kein zusätzlicher fünfter Verwaltungsreview.

## 4. Commit-/Reviewzyklus

Für jede Phase:

1. Bei behaupteten Fixes einen tatsächlichen Gegenfall reproduzieren, danach korrigieren und GREEN belegen. Keinen künstlichen RED allein aus einem noch nicht existierenden neuen Klassennamen als fachlichen Fehler verkaufen.
2. Eigene vollständige fällige Gates ausführen; Kommandos, Exits, Testanzahlen/Failures/Errors/Skips und tatsächliche Artefakte im kurzen Implementierungsbericht nennen.
3. Code, Tests, Build-/Dokumentationsänderungen und Bericht lokal committen. `BASE_SHA` bleibt die Basis der ganzen Phase, `ASSIGNMENT_BASE_SHA` die des Gesamtauftrags. Kandidaten-SHA erst danach auslesen.
4. Pflichtgates frisch am sauberen Kandidaten durchführen; getrackte Dateien unverändert. Neue Produktkorrektur bedeutet neuer Commit und neue Gates.
5. Eine vollständige `request.json` nach Plan §12.4 schreiben. Reviewoutput ausserhalb von Maven-`target`, damit `mvn clean` Request und Berichte nicht entfernt. Keine Secrets, Platzhalter oder vorgegebenen PASS-Ergebnisse.
6. Eigenen detached Reviewworktree am Kandidaten erstellen und OpenCode mit frischem Kontext starten. Agent B prüft den ganzen Phasen-Diff, nicht nur den letzten Commit; TST4 zusätzlich den Gesamtauftrags-Diff.
7. Auf echtes Prozess-/Sessionende warten. Danach HEAD, Kandidatenunverändertheit, Provider-/Modellnachweis, Bericht, JSON und alle Kriterien prüfen. Kein PASS-Regex auf Zwischenlogs.
8. FAIL: in derselben Phase begründet korrigieren, neuer Kandidat, frischer Review. BLOCKED: autonom behebbare Voraussetzungen herstellen, echte verbleibende Ursache berichten.
9. Gültiges PASS: Reviewerbericht unverändert archivieren, Hash/Pfad/SHA im Status vermerken und **automatisch nächste Phase beginnen**. Keine Benutzerbestätigung nach jeder erfolgreich geprüften Phase verlangen.

Der Reviewer darf keine Produktfixes ausführen. Du darfst seine Berichte nicht umschreiben. Reine Ablage eines unveränderten Reviews/Status braucht keinen neuen Produktreview. Jede Änderung an Produkt, Tests, Build, Fixtures oder fachlichen Kriterien nach PASS erzeugt dagegen einen neuen prüfpflichtigen Kandidaten.

## 5. OpenCode 2.x und direkter DeepSeek-Review

### Preflight und Modellidentität

Prüfe die tatsächliche lokale OpenCode-Version und `run --help`; nutze den absoluten funktionierenden CLI-Pfad. Bestehende GUI-/CLI-Anmeldung verwenden. Ein fehlender Shell-API-Key ist kein Beweis fehlender Authentifizierung. Auth-/Modell-/Providerinformationen ohne Geheimnisse prüfen.

Verbindlich:

```text
OpenCode: 2.x, tatsächlich installiert und überprüft
Modellschlüssel: deepseek/deepseek-flash
API-Modell: deepseek-flash
API-Host: api.deepseek.com
Zielmodell: DeepSeek-V4.1-Flash-1017
```

Aliaszuordnung vor dem Review gegen aktuelle offizielle Angaben und die effektive Konfiguration überprüfen. Nicht aus der Selbstauskunft des Modells ableiten. Kein Proxy/OpenRouter/Go/Zen oder anderes Modell als Fallback. Bei fehlender passender Verfügbarkeit konkret blockieren.

Vor grösserer Arbeit eine harmlose Bereitschaftsprüfung mit derselben privaten Konfiguration durchführen. Projektbezogene Rechte für Lesen, Builds, Testprozesse, eigene Reviewoutputs und später lokale Testcontainer gewährleisten, ohne sämtliche Hostrechte pauschal zu öffnen. Keine Credentialdateien ausgeben oder exportieren.

### Tatsächlicher Aufruf

Bevorzugt den privaten Standaloneweg der **wirklich installierten** V2-Version benutzen. Der vorher gegen `--help` zu prüfende typische Aufruf, im neuen Reviewworktree:

```sh
"$OPENCODE_BIN" run --standalone \
  --model deepseek/deepseek-flash \
  --format json \
  --file docs/implementation/script-first-testing/EXECUTION_PLAN.md \
  --file docs/implementation/script-first-testing/OPENCODE_REVIEW_PROMPT.md \
  --file "$REVIEW_DIR/request.json" \
  "Prüfe die beauftragte Phase unabhängig. Führe die fälligen Gates und eigene Gegenproben aus. Schreibe review.md und verdict.json im übergebenen Reviewverzeichnis."
```

Den vollständigen Ablauf einschliesslich Exitbehandlung findest du in Plan §12.5. Falls ein privater Server nötig ist, ihn selbst zweckgebunden verwalten und die Session vollständig abwarten. Nicht eine fremde GUI-Session übernehmen; kein `--continue` oder Wiederverwenden einer Implementierungssitzung.

Kein automatisches Installieren eines neuen Agentenstacks. Vorhandenen geeigneten Starter verwenden oder einen kleinen Shell-/Node-Helfer nach Plan §12.6 bauen. Der compilerprojektspezifische Python-Guard aus dem Nutzerbeispiel wird hier **nicht** vorausgesetzt. Das vorhandene GRETL-Auditskript darf weiter benutzt werden.

Bei transientem Transportfehler höchstens zwei saubere Wiederholungen desselben unveränderten Kandidaten. Fachliche Findings nicht durch Wiederholung wegwürfeln. Keine laufenden oder abgebrochenen Reviews als erfolgreich deklarieren.

### Annahme des Urteils

Nur vollständig beendete Reviews mit konsistentem `review.md` und `verdict.json` akzeptieren. Prüfe ausdrücklich:

- aktive Phase, vollständige Basis-/Kandidaten-SHAs und tatsächlicher HEAD stimmen;
- Kandidat unverändert, eigener Kontext und richtige OpenCode-/DeepSeek-Konfiguration belegt;
- genau PASS, FAIL oder BLOCKED; kein „PASS ausser fehlender Imageprüfung“;
- alle aus dem Plan fälligen Kriterien enthalten, bei TST4 alle 38;
- Pflichtchecks und neue eigene Gegenproben tatsächlich ausgeführt, Rohbelege korrekt zugeordnet;
- bei PASS keine offenen Muss-Findings oder fehlenden Pflichtnachweise;
- tatsächlicher äusserer Prozess-/Transportabschluss erfolgreich und keine relevanten unbeendeten Kinder.

Ein gestarteter Agent ist keine Abnahme. Fehlt nach angemessenen Versuchen ein notwendiger Zugriff oder Nachweis, den konkreten BLOCKED-Zustand berichten. Nicht heimlich auf eigenes Codex-Selbstreview oder blosses manuelles GUI-Angebot ausweichen.

## 6. Nicht verhandelbare Testregeln

Keine deaktivierten Pflichtfälle, keine stillen Skips, keine pauschal gelockerten Assertions und keine alte Evidenz als neuer Erfolg. Ein absichtlich fachlich fehlschlagender GRETL-Job ist ein **erfolgreicher JUnit-Negativtest nur dann**, wenn erwarteter Exit/Ursache/fachlicher Zustand wirklich geprüft sind. Ein Classpath-/Startfehler ist kein Ersatz für die erwartete fachliche Ablehnung.

Echte Prozessnachweise müssen den gebauten Launcher beziehungsweise das geprüfte generische Image benutzen. Ein umetikettiertes JAR-Resultat darf kein SCRIPT-/IMAGE-Gate erfüllen. STANDARD-SCRIPT darf ohne Docker funktionieren; angefordertes IMAGE darf niemals ohne tatsächliche Containerläufe grün werden.

Jobdateien und Ressourcen bleiben extern; nach Änderung der Quelle sieht der nächste SCRIPT-/IMAGE-Lauf den neuen Inhalt ohne Runtime-/Image-Neubau. Nicht die fachliche Erwartung verändern, damit neue Mounts oder Pfade irgendwie passen. Opaque Strings sind keine Pfade. Ein FTP-Serverpfad wird nicht als lokale Hostdatei umgeschrieben.

Bewahre Unit-/Komponententests, echte Backing Services, alle bestehenden Paketverträge und die Subprojektsemantik. Keine erfundene Codecoverage separater Jobprozesse und keine pauschale Plattformfreigabe aus nur einer lokalen Architektur.

## 7. Abschluss

Nach gültigem TST4-PASS liefere die knappe Abschlussausgabe nach Plan §13.3: neue tatsächlich funktionierende Befehle, geänderte Klassen/Methoden und Profile, getrennte vollständige Modusnachweise, Image-/Runtime-/Sourceidentitäten, alle vier unabhängigen Review-SHAs und ein echtes Beispiel eines geänderten externen Jobs ohne Image-Neubau.

**Beginne jetzt mit TST1 und setze den vollständigen Auftrag bis zur kumulativen TST4-Abnahme autonom fort. Stoppe nur bei Gesamterledigung oder einem konkret nachgewiesenen, nach angemessenen Versuchen nicht selbständig auflösbaren Blocker.**
