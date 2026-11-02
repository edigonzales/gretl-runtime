# GRETL Runtime – phasenweiser Implementierungs- und Abnahmeplan

**Version 1.0 · 2. November 2026**  
**Agent A:** Codex – Implementierung und autonome Orchestrierung.  
**Agent B:** getrennte OpenCode-2.x-Session mit DeepSeek-V4.1-Flash-1017 über die direkte DeepSeek API.  
**Zielrepository:** `https://github.com/edigonzales/gretl-runtime`.

## 0. Auftrag und Verbindlichkeit

Implementiere einen gemeinsamen statischen GRETL-Sprachkern, darauf einen eigenständigen Java-LSP und zuletzt einen tatsächlich schreibenden grafischen GRETL-Modeler in VS Code. Alles gehört in das aktuelle `gretl-runtime`-Repository. Der LSP und seine Build-/Test-/Distributionspfade dürfen keine andere GRETL-Variante benötigen.

**Reihenfolge zwingend:** P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8. Eine Folgephase beginnt nur nach gültigem unabhängigen PASS auf dem unveränderten aktuellen Kandidaten der Vorphase. Nach PASS ohne routinemässige Benutzerfrage fortsetzen. Ein fachliches FAIL wird in derselben Phase behoben; BLOCKED ist keine Freigabe.

Verbindliche Dokumente:

- [ARCHITECTURE.md](ARCHITECTURE.md): Architektur, Klassen-/Methodenverträge, Sprach-/Editiersemantik.
- [TEST_MATRIX.md](TEST_MATRIX.md): Backend-, Prozess-, GUI- und Negativprüfungen.
- [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md): eigenständiger Prüfauftrag für Agent B.
- [CODEX_STARTPROMPT.md](CODEX_STARTPROMPT.md): operativer Einstieg für Agent A.
- [SOURCE_AUDIT.md](SOURCE_AUDIT.md): Ausgangsstand, Befunde, Quellen und bewusst nicht übernommene Vorlagenanforderungen.
- [ACCEPTANCE_CRITERIA.json](ACCEPTANCE_CRITERIA.json): generierter maschinenlesbarer Index der **81** Kriterien in §4. Inhalt/IDs müssen mit diesem Dokument übereinstimmen; bei Drift keine Freigabe.

Keine konkurrierende neue Generalspezifikation schreiben. Diese Spezifikation umsetzen. Methodennamen sind Sollentwurf; vorhandene gleichwertige Implementierung dokumentiert adaptieren, nicht Doppelimplementierungen nebenan erzeugen.

### 0.1 Unveränderliche Grenzen

1. Groovy und `settings.gretl` bleiben massgeblich. Kein neues ausführbares Jobdateiformat und keine Camel-/Gradle-Laufzeit.
2. Statisches Editieren führt weder Jobs noch Toplevelcode, Settings, Handler, Builder, Plugin-/AST-Transformationscode aus.
3. Ein Java-Sprachkern entscheidet Syntax, Namen, Scopes, Referenzen, Diagnosen, Graphen und Textedits; TypeScript stellt dar und vermittelt Benutzeraktionen.
4. Aktuelle Runtime-API-/DSL-/SCRIPT-/JAR-/IMAGE-/Pfad-/Secret-/Fehlerverträge bleiben bestehen. Kleine reine Extraktionen mit vollständigen Regressionen sind erlaubt, unaufgeforderte Fachsemantikänderungen nicht.
5. Andere GRETL-Repositories sind nur Quellreferenz. Keine aktiven Abhängigkeiten, Aliaspakete, Testorakel zur Laufzeit oder dynamischen Downloads aus ihnen.
6. Modeler bedeutet wirkliche grafische Erstellung und Bearbeitung, nicht nur read-only Preview. Komplexer Groovycode bleibt unverändert und erhält ehrliche Einschränkungen.
7. Keine Publikation, produktiven Jobs, Pushes, Merges, Tags, Workflow-Dispatches oder Änderungen an Credentials/normalen Benutzerprofilen.
8. Keine Selbstfreigabe, keine vorgefertigten Reviewerurteile, keine stillen Testskips und kein „PASS mit offenen Muss-Findings“.

### 0.2 Nicht-Ziele

Keine neue Scheduling-/Kubernetes-/Jenkinsintegration, kein Debugger, keine allgemeine Groovy-/Java-IDE, keine Whole-file-Formatierung, keine grafische Darstellung beliebiger Kontrollflussprogramme, kein SVG/PNG-Export, keine manuelle Kantenrouting-Suite, keine JRE-Bündelungs-/Native-Image-Initiative und keine Marketplaceveröffentlichung. Diese Abgrenzungen stammen aus diesem Auftrag, nicht aus Einschränkungen des INTERLIS-Beispiels. Das normale Jobausführen ausserhalb des Editors bleibt erhalten.

## 1. Ausgangspunkt und Arbeitsbeginn

Recherchierte Referenzen:

- Runtime: `17057baa32c8407a2c3a69f744c6686c8258f78f`.
- Alter LSP als Quellreferenz: `edigonzales/gretl-next`, `08bd11e97a54f32f8702d34ff16fb39bec6ea046`.

Das sind **Recherchepins, keine Resetziele**. Vor Arbeit `pwd`, `git status --porcelain=v1`, `git branch --show-current`, `git rev-parse HEAD`, `git remote -v` und geltende `AGENTS.md` lesen. Tatsächlichen `REPO_DIR` bestimmen, eigenen lokalen Branch verwenden und fremde Änderungen erhalten. Keine fremden uncommitteten Dateien mitcommitten oder wegräumen. Bereits implementierte Teilfunktionen am aktuellen Stand prüfen und integrieren, nicht blind parallel neu anlegen.

Aktiver Dokumenteinstieg ist `docs/README.md`, danach `docs/architecture/runtime-contracts.md`, Modul-/Build-/Test-/Subprojektdokumentation. Historische entfernte `docs/spec`-Pfade nicht als noch vorhandenen Auftrag voraussetzen. Abgeschlossene Cleanup-/Testingaufträge nicht erneut implementieren.

### 1.1 Umgang mit der angehängten Vorlage

Die drei INTERLIS-Markdowns sind Verfahrensvorbild: klare Rollen, unveränderliche Kandidaten, eigene Gegenproben, frische Session, echte GUI und autonome Fortsetzung. **Nicht übernommen:** zwei Produktrepos, INTERLIS-Compiler-/Repositorylogik, Sprotty-Pflicht, die 60 Features/56 ACs/42 GUIcases, Pythonverbot samt fremdem macOS-Guard oder drei zwingende Betriebssystem-GUIumgebungen.

Hier gibt es ein Produktrepository und einen Kandidaten-SHA. Bestehende Pythonharnesses der Runtime sind erlaubt und werden nicht entfernt. Neue Review-/Frontendglue wird mit Node gebaut, um keine zusätzliche Pythonabhängigkeit einzuführen. Ein fremder `without-python.cjs` darf nicht als vorhanden oder nötig behauptet werden.

### 1.2 Bestehende Repository-Gates sind verbindlich

Das beim Audit gelesene Root-`AGENTS.md` verlangt mit JDK25:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Diese Vorgabe wird durch dieses Paket **nicht abgeschwächt**. In inneren Implementierungsschleifen gezielte Tests ausführen. Für die Phasenabnahme zusätzlich die tatsächlich geltenden vollständigen Repository-Gates nachweisen; Belege vor jedem weiteren `clean` ausserhalb löschbarer Buildordner sichern. Der Reviewer muss nicht jede schwere Prüfung doppelt starten, sofern er aktuelle identische Kandidaten-/Artefaktbelege unabhängig prüft und die risikotragenden Tests selbst ausführt.

Falls `AGENTS.md` im tatsächlichen Checkout abweicht, die aktuelle Regel ausdrücklich erfassen und einhalten. Ein Konflikt wird nicht durch stilles Editieren der Regeln gelöst. Keine neue pauschale Regel „alle Plattformen und alle historischen Releasegates immer“ hinzufügen.

### 1.3 Früher Umgebungsnachweis

P0 prüft JDK25, Mavenwrapper, aktuelle Groovyversion, Node/npm, Docker für Pflichtgates, vorhandene Reviewharnesses und OpenCode. Vor grosser Implementierung eine harmlose echte Reviewerbereitschaftsprobe mit Dateien, Testprozess und Berichterstellung durchführen. GUIautomation für spätere Phasen auf der tatsächlichen Primärplattform früh als generische Infrastrukturprobe verifizieren; noch keinen Modeler oder Produkt-LSP vorziehen.

## 2. Lieferstruktur und neue Werkzeuge

Struktur gemäss ARCHITECTURE. Neue Module nur dann anlegen, wenn sie in der aktuellen Phase echte Verantwortung besitzen. Kein Wald leerer zukünftiger Module.

Die Spezifikationen liegen unter `docs/implementation/gretl-tooling/`. Das dortige `AGENTS.md` ersetzt das Root-`AGENTS.md` **nicht**. Agent A ergänzt am Root bei Bedarf nur einen knappen Verweis auf diesen ausdrücklich beauftragten aktiven Auftrag, ohne bestehende Gates zu löschen. Aktuelle `docs/README.md` erst dann um implementierte Toolingfunktionen ergänzen, wenn diese tatsächlich existieren.

### 2.1 Quellübernahme

Vor Übernahme aus `gretl-next`: Lizenz, vollständigen Commit, Originalpfad, Original-SHA-256, neuen Pfad und wesentliche Änderungen in `source-adoption.json` dokumentieren. Originalcopyright erhalten. Keine Behauptung, alle Altklassen seien übernommen, wenn sie neu geschrieben wurden. Ein Audit darf weiterhin den Namen des Altprojekts nennen.

Rein technische Teile von Launcher/Provideraufteilung/Client sind Kandidaten. Alte Gradle-Diagnosen, Parserannahmen, Regexscanner und Quelltextbereiche nicht ungeprüft übernehmen. Details und bekannte Gegenfälle stehen in SOURCE_AUDIT und TEST_MATRIX.

### 2.2 Tatsächliche Testeinstiege

Die folgenden Namen sind **zu implementierende** Verträge, nicht bereits ausgeführte Befehle. Ein existierender gleichwertiger Einstieg darf mit eindeutiger Dokumentation weiterverwendet werden. Keine zwei nahezu gleichen Reviewframeworks aufbauen.

```sh
# Java: gezielt während Umsetzung; vollständige Rootgates bleiben separat Pflicht.
./mvnw -B -pl gretl-tooling-core,gretl-lsp,gretl-tooling-tests -am verify

# Root-npm-workspaces für die internen Frontendmodule, sobald vorhanden.
npm ci
npm run tooling:build
npm run tooling:test:unit
npm run tooling:test:host
npm run tooling:test:gui -- --suite editor
npm run tooling:test:gui -- --suite graph
npm run tooling:test:gui -- --suite modeler
npm run tooling:test:gui -- --suite resilience
npm run tooling:test:gui -- --suite all
npm run tooling:package

# Kleine neue bzw. adaptierte Nodewerkzeuge.
node tools/gretl-tooling/review.mjs preflight --repo "$REPO_DIR"
node tools/gretl-tooling/verify-dependencies.mjs --mode all
node tools/gretl-tooling/check-docs.mjs
```

Vor P4 existiert `gretl-lsp` noch nicht; gezielte Mavenmodullisten dem tatsächlichen Phasenstand anpassen. Unbekannte Suite, fehlende Fixture, null ausgeführte Pflichtfälle oder fehlendes Artefakt müssen ungleich 0 enden. `--passWithNoTests` ist für Pflichtgates verboten.

## 3. Allgemeine Definition eines gültigen Phasenabschlusses

Eine Phase ist erst freigegeben, wenn:

- alle ihre neun Musskriterien mit Code-/Testbelegen erfüllt sind;
- die fälligen gezielten und vollständigen Repository-Gates am bezeichneten Produktstand bestanden sind;
- Codex Code, Tests und kurzen Implementierungsbericht committed hat;
- ein neuer unabhängiger Reviewer im detached Worktree auf genau diesem SHA Ziele, Code, Testwirksamkeit und eigene Gegenfälle geprüft hat;
- dessen `review.md` und `verdict.json` konsistent sind;
- der Reviewerprozess tatsächlich beendet, seine Belege abgeschlossen und beide tracked Trees unverändert sind;
- der Orchestrator den vollständigen Kriterien-/Identitäts-/Belegvertrag geprüft hat.

Bei Nacharbeit bleibt `phaseBase` unverändert. Der neue Kandidat enthält alle kumulativen Änderungen seit Beginn dieser Phase. Ein neues PASS für einen kleinen Reparaturdiff ersetzt nicht die Prüfung der gesamten Phase. P8 prüft zusätzlich den gesamten Auftrag seit `assignmentBase`.

Reviewabschluss ist ein äusseres Gate und kein selbstreferenzieller Test: Der Reviewer beurteilt die fachliche Vollständigkeit; Codex stellt **nach** dessen Ende den tatsächlichen Prozessabschluss fest. Codex darf ein fehlendes fachliches PASS dadurch nicht ersetzen.

## 4. Phasen und Musskriterien

Jede Phase liefert eine echte implementierte Fähigkeit. P0–P3 bleiben vollständig ohne Produkt-GUI/Language-Server. P4/P5 liefern LSP und Texteditor. Erst nach P5 beginnen Graph und Modeler. Gemeinsame Core-Editplanung in P3 ist zulässig und erwünscht; eine vorgezogene Modeleroberfläche nicht.

### P0 – Arbeitsfähige Basis, gemeinsame Verträge und Operationskatalog

**Ziel:** Ein eigenständig testbarer Katalog-/Vertragskern und eine nachgewiesen autonome Reviewkette stehen; kein blosser Inventurbericht.

**Arbeiten:** Aktuellen Checkout/Regeln prüfen; Reviewer- und GUIinfrastrukturpreflight; benötigte Mavenmodule anlegen; DslVocabulary/ProjectNameRules adaptieren; vollständigen Katalog aus aktueller SPI exportieren; Herkunft/Abhängigkeitsgrenzen und Orchestrierung implementieren.

| AC | Verbindliche Bedingung |
|---|---|
| P0-01 | Aktueller HEAD, fremde Änderungen, Toolchain, aktive Dokumentation und Änderungen seit dem Recherchepin sind dokumentiert; kein Reset und keine Wiederausführung abgeschlossener Aufträge. |
| P0-02 | OpenCode 2.x mit vorhandener Anmeldung, direkter DeepSeek API und belegter Flash-1017-Zuordnung führt einen echten unabhängigen Bereitschaftstest aus; keine erzwungene neue Key-ENV und kein Providerfallback. |
| P0-03 | Neue Modul-/Paketgrenzen sind im Build prüfbar; Produktionskern braucht keine andere GRETL-Variante und keine ausführenden Runtime-/Taskmodule; keine leeren späteren LSP-/UIattrappen. |
| P0-04 | Reine DSL-/Projektnameverträge sind implementiert und gegen die aktuelle Runtime verhaltensgleich geprüft; bestehende öffentliche APIs und Fehlermodi bleiben erhalten. |
| P0-05 | Der deterministische Operationskatalog enthält alle aktuell mitgelieferten Operationen, Eingaben, Ausgänge, Defaults und erlaubten Werte aus der SPI; keine handgepflegte zweite Operationsliste. |
| P0-06 | Katalogschema, Digests, Ergänzungsressourcen und Unavailable-/Incompatible-Zustände sind geprüft; unbekannte IDs, Kollisionen oder Schemafehler werden nicht zu leerem Erfolg. |
| P0-07 | Der Export liest keine Secrets, führt keine Handler/Builder/Converter aus und erzeugt nur beschreibende Daten; LSP-Datenartefakt kann später ohne Exporterclasspath konsumiert werden. |
| P0-08 | Zugehörige Tests, Lizenz-/Übernahmeinventur, Dependencyprüfung und geltende vollständige Repository-Gates sind frisch belegt; frühe GUIinfrastrukturprobe dokumentiert reale Grenzen. |
| P0-09 | Der Revieworchestrator besitzt echte Bereitschafts-/Start-/Wait-/Gatefunktionen und negative Tests gegen falsche SHAs, fremde Kriterien, fehlende Belege, laufende Prozesse und vorgefertigtes PASS; keine Selbstfreigabe. |

**Gezielter Testfokus:** T01, T02, T03, T33, T34; noch keine Produkt-GUIprüfung. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P1 – Sicherer Parser, Fehler-Recovery und Sourcebereiche

**Ziel:** Aktuelle GRETL-Quellen und unvollständige Tippzustände werden ohne Ausführung strukturiert gelesen.

**Arbeiten:** SafeGroovySyntaxReader, TokenIndex, CoordinateMapper, ASTextraktion, Recovery und Merge implementieren. Runtime-Groovyversion verwenden. Neutralen Syntaxbaum, Coverage und exakte String-/Editspans liefern.

| AC | Verbindliche Bedingung |
|---|---|
| P1-01 | Reiner Parse-/Convertpfad führt keinen Toplevel-, Settings-, Grab-, AST-Test-, Provider- oder Jobcode aus; Sideeffect- und Classpathgegenproben sind wirksam. |
| P1-02 | Aktuelle task-/action-/parameter-/defaults-/cleanup-Syntax einschliesslich Map-first-Optionen und aller Defaultziele wird korrekt extrahiert. |
| P1-03 | Direkte Feldzuweisungen, lokale Variablen, frei programmierte Actions und innere ctx.run-Aufrufe sind getrennt modelliert; keine Gradle-Methodenpflicht oder erfundene DAGtasks. |
| P1-04 | Tokenisierung versteht Kommentare, Quotearten, GStrings und Klammern; Tasktext in Kommentaren/Strings erzeugt keine Deklarationen. |
| P1-05 | Unvollständige Köpfe, fehlende Klammern und beschädigte Ausdrücke erhalten sichere Strukturinseln und konkrete Coverage; Recovery ersetzt nicht die ganze Datei durch leere Daten. |
| P1-06 | Syntaxprobleme bleiben auch ohne Tasks erhalten, tragen echte Positionen und werden nicht verdoppelt; Parser-/Scannerzusammenführung besitzt explizite Tests. |
| P1-07 | UTF-16-Spans, Parserkoordinaten, CRLF, BOM, Emoji und getrennte Literal-/Inhaltsspans sind korrekt; keine indexOf-Erstfundstellenheuristik. |
| P1-08 | Dynamische Registrierung, fremde Receiver und verschattete DSLnamen werden konservativ markiert; nicht verstandener Groovytext bleibt original erhalten. |
| P1-09 | Parser-/Security-/Recoverytests einschliesslich Altbefund-Gegenfällen und geltende Rootgates bestehen; Grössen-/Rekursionsgrenzen verhindern ungebundene Analyse. |

**Gezielter Testfokus:** T04, T05, T06, T07; noch keine Produkt-GUIprüfung. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P2 – Projekte, Snapshots und statische Semantik

**Ziel:** Der Kern versteht reale Einzeljobs und Subprojekte mit korrekten Abhängigkeiten, Referenzen und ungespeicherten Quellen.

**Arbeiten:** DocumentStore/SourceRepository/WorkspaceManager/Scheduler, ProjectResolver, Binder, ReferenceIndex und Diagnosen implementieren. Reine PlanSemantics aus bestehendem Runner extrahieren und beide Consumer dagegen prüfen.

| AC | Verbindliche Bedingung |
|---|---|
| P2-01 | Unveränderliche Source-/Workspace-/Katalogsnapshots mit Versionsvektor, Bufferoverlay und Generationen verhindern Mischstände; Close/Reopen/Diskänderung sind korrekt. |
| P2-02 | settings.gretl, Includes, Root-/Subprojektjobs, lokale und qualifizierte Namen sowie Projektdefaults entsprechen der tatsächlichen Runtime; Workspacefolder bleiben fachlich getrennt. |
| P2-03 | Tasks, Actions, Parameter und Cleanupnamen/Scopes werden gebunden; doppelte/mehrdeutige/dynamische Referenzen besitzen unterscheidbare Ergebnisse. |
| P2-04 | Gemeinsame PlanSemantics erhält vollständige dependsOn-Zyklusprüfung, reine after-Reihenfolge, targetabhängige Mischzyklen und stabile Reihenfolge; Runtimeparität ist getestet. |
| P2-05 | result/resultOptional verlangen die passende direkte/transitive Datenkette; Ausgabecompletion/-diagnosen beruhen auf dem Producerkatalog, nicht auf erfundenen Actionresultaten. |
| P2-06 | Parameterdeklaration/-lesen, Toplevelverbot, Typen und required/default sowie Feld-/Enum-/Pflichtprüfungen sind scopesicher; dynamische Fälle werden nicht als bewiesene Fehler ausgegeben. |
| P2-07 | file/workPath/externe Parameterpfade werden unterschieden; jobrelative SQLanalyse nutzt aktuelle Runtimefelder/-regeln, Dirty-SQL und Dateiänderungen, ohne DB- oder Secretzugriffe. |
| P2-08 | Partielle Coverage begrenzt Diagnosen/Mutationsrechte; Katalogausfall und unbekannte Fachwerte werden nicht verwechselt; IO/Queue/Projektgrössen sind begrenzt. |
| P2-09 | Snapshot-/Projekt-/Semantik-/SQLtests, unabhängige statisch-versus-Runtimeparität und alle fälligen Repositoryregressionen bestehen. |

**Gezielter Testfokus:** T08, T09, T10, T11, T12, T13; noch keine Produkt-GUIprüfung. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P3 – Language Queries und verlustarme Editplanung

**Ziel:** Editorfunktionen und alle späteren Modelerintents funktionieren bereits als getestete Javafunktionen, ohne Oberfläche.

**Arbeiten:** LanguageQueries, neutralen JobGraphProjector, EditabilityAnalyzer, SemanticEditPlanner, SourceEditBuilder und EditVerifier implementieren. Katalog-/Scopebasierte Vorschläge und konservatives Rename; alle Mutationen als reine Pläne.

| AC | Verbindliche Bedingung |
|---|---|
| P3-01 | Completion unterstützt Operationen, Optionen, Felder, Werte, Parameter, Resultfelder, Dependencies und sichere Pfade mit korrekten Ersetzungsbereichen und ohne Vorschläge in Kommentaren. |
| P3-02 | Hover/SignatureHelp erklären aktuelle DSL und Metadaten; Outline/Folding/Filelinks, Definition/References und die neutrale Graphprojektion verwenden denselben Snapshot. |
| P3-03 | Rename/PrepareRename erfassen alle belegten Referenzarten und Projektformen; dynamische/readonly/unvollständige Ziele führen zu konkreter Ablehnung statt Teilrename. |
| P3-04 | AddTask/AddAction/AddParameter/SetField/RemoveField/SetTaskOption/SetDefaults liefern tatsächlich geprüfte Pläne für den unterstützten statischen Bereich. |
| P3-05 | Abhängigkeits- und Ergebnisbindungen erzeugen die richtigen Header-/Feldänderungen zusammen; after und dependsOn bleiben verschieden, transitive Ketten werden respektiert. |
| P3-06 | Delete/Disconnect sind referenz- und coveragegesichert; keine versteckte Kaskade, keine Entfernung noch benötigter Ergebnisabhängigkeiten. |
| P3-07 | Gezielte Token-/Sourceedits erhalten Kommentare, EOL, opake Codebereiche und unberührte Texte; Stringquotes werden nie verdoppelt; keine Whole-file-Neugenerierung. |
| P3-08 | Jeder Plan hat Source-/Projekt-/Katalogpreconditions, wird in Memory angewendet und erneut geprüft; No-op, stale und fehlerhafte Postimages werden zuverlässig behandelt. |
| P3-09 | Alle Query-/Editintents und angewendeten QuickFixes sind positiv/negativ getestet; echte Runtimebeispiele bleiben erhalten; aktuelle vollständige Repository-Gates bestehen. |

**Gezielter Testfokus:** T14, T15, T16, T17, T25; noch keine Produkt-GUIprüfung. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P4 – Eigenständiger Java-LSP mit echtem stdio-Protokoll

**Ziel:** Ein ausgelieferter Serverprozess bietet den vollständigen beauftragten Textsprachdienst und den getypten Modelervertrag.

**Arbeiten:** LSP4J-Launcher/Lifecycle, Dokument-/Workspaceservices, Mapper, Hostsourceadapter und Customrequests implementieren. Katalog als Daten einbetten; Prozesstests und Archivprüfung.

| AC | Verbindliche Bedingung |
|---|---|
| P4-01 | Startbares unabhängiges Serverartefakt enthält nur erlaubte Dependencies und Katalogdaten, läuft mit Java25 ohne andere GRETL-Checkouts und hält stdout JSON-RPC-rein. |
| P4-02 | Initialize/Initialized/Shutdown/Exit und Fehlerzustände sind korrekt; Shutdown+Exit beendet den wirklichen Prozess auch bei offen gehaltenem stdin und räumt eigene Executor auf. |
| P4-03 | Full-/Incrementaländerungen, mehrere Events, UTF-16, Close/Reopen und Versionsprüfungen sind geordnet; Cancellation und obsolete-result-drop funktionieren unter Last. |
| P4-04 | Completion/Hover/SignatureHelp/Definition/References/PrepareRename/Rename/Symbols/Links/Folding/CodeActions werden tatsächlich über Standard-LSP geliefert, nicht nur im Unitadapter. |
| P4-05 | Diagnosen tragen korrekte URI/Version/Range/RelatedInformation und DE/EN-Anzeige; fehlende Positionen/Metadaten werden nicht zu irreführendem Zeile-1-Erfolg. |
| P4-06 | Configuration-, Workspacefolder- und WatchedFilesmeldungen ändern den echten Analysezustand; ungültige Updates werden atomar abgelehnt, SQL/Settingsänderungen invalidieren richtig. |
| P4-07 | gretl/catalog, graph, planEdit, validateEdit und navigation samt analysisReady sind getypt, versioniert und im tatsächlichen Launcher registriert; keine unimplementierten Capabilities. |
| P4-08 | Readonly-/Trust-/Pfad-/Message-/Budgetregeln und Clientcapabilities werden respektiert; Server schreibt keine Jobdateien und startet keine Jobs/Plugins. |
| P4-09 | Echte stdio-Vertrags-, Race-, Lebenszyklus-, Archiv-/Dependencytests sowie alle fälligen Rootgates bestehen; kein Modelerfrontend vorgezogen. |

**Gezielter Testfokus:** T18, T19, T20, T21, T22; noch keine Produkt-GUIprüfung. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P5 – Installierter VS-Code-Texteditor

**Ziel:** Normale GRETL-Jobentwicklung funktioniert in einer wirklich installierten VSIX, bevor die Modeleroberfläche beginnt.

**Arbeiten:** Dünnen Client, Selector, Start-/Config-/Trust-/Templatecontroller, Native-Editadapter und GUIharness implementieren. Server aus Paket starten, nicht aus zufälligem Siblingbuild.

| AC | Verbindliche Bedingung |
|---|---|
| P5-01 | VSIX besitzt eigene Runtime-Identität, tatsächliches Server-/Katalogbundle und dokumentierte Java25-Voraussetzung; isolierte Installation ohne andere GRETL-Repos funktioniert. |
| P5-02 | job.groovy/settings.gretl und ausdrücklich zugewiesene andere Jobdateien werden unterstützt; normale fremde Groovy-/Gradleprojekte werden nicht ungefragt übernommen. |
| P5-03 | Completion, Hover, Signaturen, Problems, Navigation, References und Outline sind durch echte Tastatur-/Mausaktionen sichtbar und sourcegenau. |
| P5-04 | Rename/QuickFixes arbeiten auf aktuellen Buffern, bewahren native Undo/Redo und Dirtyzustände und lehnen bekannte Konflikte ab; keine fs.writeFile-Abkürzung. |
| P5-05 | Einfaches gültiges Jobtemplate, Status/Output und verständliche Konfigurations-/Java-/Bundlefehler sind bedienbar; keine eingebauten Geheimnisse. |
| P5-06 | Stop/Restart/Crash-Recovery erhält ungespeicherte Texte, entfernt alte Sessions und beendet nur eigene Prozesse ohne Neustartschleife. |
| P5-07 | Workspace Trust, erlaubte Konfiguration und Workspacehost-/Remote-URIgrenzen sind implementiert; keine UI-Felder als Sprachkonfiguration durchgereicht. |
| P5-08 | G01–G10 laufen mit frisch installiertem VSIX und echtem Server; Screenshots plus Assertions/Artefakthashes liegen vor, nicht nur Extension-APIaufrufe. |
| P5-09 | Client-/Host-/Prozess-/GUIprüfungen, Dependencyinventur und fällige Repository-Gates bestehen; weiterhin keine vorgezogene grafische Produktarbeit. |

**Gezielter Testfokus:** T23, T24; GUI G01, G02, G03, G04, G05, G06, G07, G08, G09, G10. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P6 – Interaktiver statischer Jobgraph

**Ziel:** Der Benutzer sieht und navigiert den echten statisch verstandenen Jobgraph; die Grundlage des späteren Schreibeditors ist bedienbar.

**Arbeiten:** Vorhandenen neutralen Java-Graphprojektor an echte Webview anschliessen; React Flow, lokaler ELKworker, Source-Navigation, Viewstate und sichere Lifecycle-/Nachrichtenverträge implementieren.

| AC | Verbindliche Bedingung |
|---|---|
| P6-01 | Der Graph stammt ausschliesslich aus aktuellem Sprachkernsnapshot, enthält alle statisch bekannten Deklarationen und trennt Definitionssicht von targetbezogener Vorschau. |
| P6-02 | Tasks/Actions/Projektcontainer sowie Parameter-/Cleanup-/dynamische Bereiche haben fachlich richtige Darstellung; keine erfundenen parallel laufenden Tasks. |
| P6-03 | dependsOn/after/Resultbindungen sind getrennt, Producer→Consumer korrekt; Partials, unaufgelöste Referenzen und Diagnosen werden ehrlich dargestellt. |
| P6-04 | Echter CustomTextEditor mit option-Priorität, lokalen React-Flow-Nodes und ELKworker funktioniert; kein statisches SVG oder zweiter Server als Ersatz. |
| P6-05 | Node-Drag, Pan/Zoom/Fit, Auswahl und einfache Filter sind tatsächlich bedienbar und verändern weder Source noch Registrierungsreihenfolge/Dirtymarker. |
| P6-06 | Quelle↔Diagramm-Navigation und Liveupdates verwenden exakte aktuelle Identitäten; schnelle Edits/Restart/Panelwechsel verwerfen alte Antworten. |
| P6-07 | Viewstate bleibt getrennt von Fachgraph/Source und wird nur bei eindeutiger Identität übernommen; mehrere Panels und Cleanup funktionieren. |
| P6-08 | CSP, lokale Assets, validierte Nachrichten, sichere Labels sowie Light/Dark/HighContrast und leere/partielle/stale Ansichten sind implementiert und geprüft. |
| P6-09 | G11–G16 und Editor-Smoke bestehen mit aktuellen Bildern/Geometrie-/Sourcebelegen; betroffene Kern-/LSP-/UItests und Pflichtrootgates sind grün. |

**Gezielter Testfokus:** T25, T26; GUI G11, G12, G13, G14, G15, G16. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P7 – Echter grafischer Modeler und Text-Roundtrip

**Ziel:** Ein vollständiger normaler Runtimejob kann grafisch erzeugt und sicher mit Textbearbeitung kombiniert werden.

**Arbeiten:** Palette/Formulare/Intents/EditPlanApplier, Dependency-/Resultports, Defaults/Rename/Delete und echte Roundtrip-/Undo-/Konfliktprüfung implementieren. Keine Clientcodegenerierung.

| AC | Verbindliche Bedingung |
|---|---|
| P7-01 | Aus leerer Datei lassen sich Parameter, HttpDownload, Extract, Ergebnisbindung und Defaultziel grafisch zu einem vollständigen Job erstellen, speichern und wieder öffnen. |
| P7-02 | Palette benutzt alle aktuellen Katalogoperationen; Formulare unterstützen die belegten Wertformen/Enums/Listen/Maps und explizite Parameter-/Datei-/WorkPath-/Secretaliasmodi. |
| P7-03 | Dependencygesten ändern korrekt den Consumerkopf; after-Auswahl und targetabhängige Zyklusregeln bleiben erhalten; abgelehnte Aktionen lassen Source unverändert. |
| P7-04 | Output→Input-Verbindung setzt Ergebnisreferenz und nur nötige Datenabhängigkeit zusammen; Disconnect berücksichtigt verbleibende transitive Ketten und betroffene Referenzen. |
| P7-05 | Umbenennen/Löschen/Defaults verwenden Corepläne und vollständige Referenzprüfung; kein unsicheres Teilrename, Cascade-Delete oder Verlust weiterer Defaultziele. |
| P7-06 | Actions, opake Ausdrücke, dynamische Registrierung und Kommentare bleiben bei umgebenden grafischen Änderungen unverändert; gesperrte Aktionen erklären den Grund und bieten Textnavigation. |
| P7-07 | Fachliche Änderungen benutzen native Textedits/Undo/Redo; Layout ist view-only; Stale-/Dirty-/Multifilekonflikte führen zu Neuplanung/Ablehnung, nicht stillem Überschreiben. |
| P7-08 | Der grafisch erzeugte echte Groovytext läuft in einem separaten lokalen Runtime-SCRIPT-Fixturetest mit geprüftem Ergebnis; kein Jobstart beim normalen Editieren. |
| P7-09 | G17–G25, echte Roundtrip-/Preservation-/Negativtests und geltende vollständige Rootgates bestehen; GUIaktionen und passende Source-/Artefaktzustände sind nachgewiesen. |

**Gezielter Testfokus:** T27, T28, T31; GUI G17, G18, G19, G20, G21, G22, G23, G24, G25. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt die nächste Phase.

### P8 – Resilienz, saubere Distribution und kumulative Abnahme

**Ziel:** Ein installierbares, eigenständig reproduzierbares Gesamtprodukt erfüllt den gesamten Auftrag mit ehrlicher Plattform- und Scopeangabe.

**Arbeiten:** Sauberen Build ohne Altcheckout, Archiv-/Classpathsicherheit, Offline-/Restart-/Budget-/Multirootfälle, komplette aktuelle GUI und Dokumentation prüfen; kumulativen unabhängigen Abschluss durchführen.

| AC | Verbindliche Bedingung |
|---|---|
| P8-01 | Frischer Build und isolierte Installation funktionieren ohne gretl-next/andere GRETL-Varianten; Maven-/npm-Auflösung und tatsächliche Archive enthalten keine versteckten Altdependencies. |
| P8-02 | Ausgelieferte Server-/Katalog-/Schema-/UIartefakte besitzen geprüfte zusammengehörige Digests/Versionen und vollständige Lizenzen; kein Source-Checkout oder Installationsnetzwerk für die Nutzung erforderlich. |
| P8-03 | Gesamte neue Java-/LSP-/Frontend-/Host-/GUI-Suite G01–G30 auf tatsächlicher Primärplattform und alle geltenden Rootgates sind frisch am finalen Kandidaten bestanden. |
| P8-04 | Echter Linux-Java-LSP-Distributionsstart ist belegt; weitere getestete und ungetestete Betriebssystem-/Remoteumfänge sind ehrlich getrennt, keine Universalbehauptung aus Crossbuild. |
| P8-05 | Schnelle Edits, Katalog-/Schemafehler, Crash/Restart, Multiroot, readonly, Workspace Trust und Offline bleiben ohne Datenverlust kontrolliert; G26–G30 sind belegt. |
| P8-06 | Ressourcen-/Last-/Parserbudgettests zeigen begrenzte Queues/Snapshots/Worker und stabile Obsoletebehandlung; Performancewerte sind gemessen, nicht erfunden. |
| P8-07 | Benutzer-/Build-/Architektur-/Protokoll-/Testdokumentation erklärt tatsächliche Funktionen, statische Grenzen, Javaanforderung, Jobausführung ausserhalb des Editors und Fehlerbehebung konsistent. |
| P8-08 | Alle 81 Kriterien sind einzeln auf aktuelle Code-/Testbelege abgebildet; vorherige PASS-Identitäten, erhaltene FAIL-Berichte und kumulativer Assignmentdiff sind nachvollziehbar. |
| P8-09 | Mindestens zwei neue unabhängige übergreifende Gegenfälle und wirksame negative Harness-/Paketproben sind dokumentiert; Kandidat bleibt unverändert, keine Publikation oder produktive Ausführung. |

**Gezielter Testfokus:** T29, T30, T32, T34; GUI G26, G27, G28, G29, G30. Zusätzlich betroffene bereits gelieferte Regressionen und §1.2.

**Phasengate:** Unabhängige Ziel-/Code-/Testprüfung einschliesslich neuer Gegenprobe; erst gültiges PASS nach §6 erlaubt den Abschluss.

## 5. Teststrategie und tatsächliche Evidenz

### 5.1 Vier Ebenen, keine Ersatzbehauptungen

**Kern-/Contracttests:** JUnit prüft Parser, Semantik, Queries, Editpläne und Runtimeparität. **Prozesstests:** echtes ausgeliefertes Javaartefakt über stdio; Direktaufrufe beweisen keine JSON-RPC-Registrierung oder Prozessbeendigung. **Extension-Host-Tests:** echte VS-Code-APIs und Dokumentzustände. **GUItests:** installierte VSIX in einer echten Workbench/Webview mit Tastatur-/Mausaktionen. Diese Ebenen ergänzen sich.

Neue UI-/GUIharnesses erst P5 als Produktintegration. P0 darf generisch Display/Driver/Browserzugriff erproben, damit die spätere unabhängige Prüfung nicht an fehlenden Rechten scheitert.

Vorgesehener GUIweg: ein gepinnter `vscode-extension-tester`-/WebDriver-Harness mit PageObjects und tatsächlicher Webview. Gleichwertiger realer Electronharness ist bei belegtem technischem Hindernis erlaubt, aber keine parallelen unfertigen Frameworks pflegen. Browsertests des neutralen Reactbausteins können Playwright verwenden; sie ersetzen die Workbench nicht.

### 5.2 GUI-Belegregeln

Installation aus dem erzeugten VSIX in isolierten user-data-/extensions-/Testworkspaceverzeichnissen. Nicht das echte Benutzerprofil ändern, nicht die vorhandene Gradleextension als unbemerkten Funktionslieferanten verwenden. Die laufende Extension-ID, Server-/VSIXhash und tatsächliche Prozesse erfassen.

Benutzerbefehle über Palette, Shortcuts, Menüs und echte Pointer-/Keyboardevents. Keine `controller.move()`- oder state-changing `evaluate()`-Abkürzung als Mausdrag. Read-only DOM-/BoundingBoxabfrage ist als Orakel erlaubt. Ein `vscode.executeCompletionItemProvider` ist ein Hosttest, kein Suggestion-GUItest.

Relevante Vorher/Nachher-Screenshots plus strukturierte Assertions, Source-/Bufferhashes und bei P7 tatsächlich gespeicherter Groovytext. Der unabhängige Reviewer öffnet die relevanten Bilder mit einem real verfügbaren Bildwerkzeug; Dateinamen/Abmessungen sind keine Sichtprüfung. Fehlende Bild-/GUIfähigkeit früh konkret blockieren, nicht behaupten.

Linux-Xvfb ist als echte gerenderte Electron-GUI erlaubt. Headless-JSDOM oder künstliche Mockupbilder nicht. Keine beliebigen Sleeps als Erfolg: begrenzt auf tatsächlichen Zustand warten; Browserconsole-/Serverfehler auswerten.

### 5.3 Gateauswahl ohne versteckte Abschwächung

Vor jeder Phase kurz erfassen: veränderte Verantwortung → direkte/transitive Consumer → gezielte Tests → aktuelle Rootpflichtgates → begründet nicht nötige zusätzliche historische Prüfungen. Neue Parser-/PlanSemanticsänderung verlangt echte Runtimeparität. Reines UIlayout verlangt Sourceunverändertheit/GUIregression, keine neue fachliche DBmigration.

Die Rootpflichtgates aus §1.2 bleiben fällig. Keine Tests aus der Abnahme entfernen, nachdem sie fehlgeschlagen sind. Tests dürfen nur aufgrund nachvollziehbarer Scopeentscheidung als nicht zusätzlich nötig gelten; dieser Auftrag selbst wird nicht verkleinert.

Reviewer führt risikotragende Tests selbst frisch aus. Bereits durch Codex ausgeführte vollständige Gates darf er anhand erreichbarer, unveränderter Rohbelege desselben Kandidaten prüfen. P8 verlangt erneut einen unabhängigen echten Server-/Editor-/Modelerpfad und neue übergreifende Gegenfälle.

### 5.4 Belegformat

Evidence ausserhalb des Produktworktrees oder unter ignoriertem, nicht durch `clean` löschbarem Pfad. Pro Testlauf Manifest mit Run-ID, Produkt-SHA, Dirtyzustand, Toolversionen, OS/Architektur, Artifactdigests, Commands als argv/cwd, Start/Ende, Exit, tatsächlich ausgeführten Tests/Fehlern/Skips, Assertions, stdout/stderr und Bild-/Dateipfaden.

Buildcache ist erlaubt; behauptete Tests müssen wirklich gelaufen sein. Alte Logs oder Cachetreffer nicht als neuen Testlauf umetikettieren. Null Tests scheitern. Negative Harnessprobes laufen in Scratchkopien und müssen den behaupteten Fehler tatsächlich erkennen; keine Mutation des Reviewkandidaten.

## 6. Autonome Codex-/OpenCode-Orchestrierung

### 6.1 Rollen, Zustände und Grenzen

Codex ist Agent A und orchestriert. Agent B ist ein neuer OpenCodeprozess mit unabhängiger Session. Kein Codex-Subagent mit gleichem Implementierungskontext als Ersatz. B liest Spezifikation/Quellen/Diff, prüft Ziele und Tests und erfindet eigene Gegenfälle. B darf keine Produktkorrekturen committen und keinen Code liefern, den A unbesehen als bestanden übernimmt.

Operative Zustände: `PLANNED`, `IN_PROGRESS`, `AWAITING_REVIEW`, `PASS`, `FAIL`, `BLOCKED`. Ausschliesslich `PASS` auf aktueller Kandidatenidentität öffnet die Folgephase. BLOCKED nennt `INFRA`, `MODEL_IDENTITY`, `REVIEW_TRANSPORT`, `MISSING_EVIDENCE` oder eine echte nicht autonom auflösbare Scopekollision.

Alle neun Phasen sind bereits beauftragt. Keine routinemässigen Rückfragen nach PASS, keine manuelle Übergabe in die OpenCode-GUI und keine Aufforderung, der Benutzer solle selbst Reviews starten. Ein echter nicht behebbarer Blocker wird nach belegten begrenzten Reparaturversuchen gemeldet; nicht übersprungen.

### 6.2 Provider-/Modellpreflight

Ermittle tatsächlichen OpenCode-2.x-Binärpfad und `--version`, `run --help`, `auth --help`, `models --help`. CLIflags aus der installierten Hilfe übernehmen, keine V1-/V2-Mischsyntax behaupten.

Verbindlich: effektiver Provider `deepseek`, direkter APIhost `api.deepseek.com`, API-Modell `deepseek-flash`, derzeit offiziell zugeordnet zu **DeepSeek-V4.1-Flash-1017**. Recherche am 02.11.2026 bestätigt diese Zuordnung; siehe SOURCE_AUDIT. Bei jedem neuen Reviewlauf lokalen effektiven Profil-/Sessionstand prüfen und die Anbieterzuordnung aktuell bzw. mit nachvollziehbarer Frische dokumentieren. Alias ist kein unveränderlicher Snapshot. Bei nachweislich anderer oder nicht belegbarer Zuordnung BLOCKED; kein stiller Wechsel zu Pro, OpenRouter, Go/Zen, LiteLLM oder anderem Anbieter.

Bestehende gespeicherte GUI-/CLI-Anmeldung verwenden. Kein Zwang, einen vorhandenen funktionierenden Key erneut als `DEEPSEEK_API_KEY` anzulegen. Keine Credentials exportieren, SQLite/Authdateien dumpen oder Tokens/Headers in Logs schreiben. Sichere nicht geheime Profildaten/Sessionmetadaten genügen. Modellselbstauskunft ist kein Identitätsbeleg; eine offizielle Aliaszuordnung plus tatsächlicher Requestprovider/-modellpfad belegen die Konfiguration, nicht kryptografisch die Servergewichte.

Frische Session, keine `--continue`/`--session` der Implementierung. Bevorzugt `--standalone`, **nur nach Hilfeprüfung**. Kein automatisches Anheften an eine laufende Benutzersession. Private Backendprozesse nur auf Loopback/privatem Socket, keine Internetfreigabe, keine globalen Pluginänderungen.

Projektbezogene Rechte so einstellen, dass Dateien gelesen, eigene Tests/GUI gestartet und Evidence geschrieben werden können, ohne laufende Benutzerbestätigung. Produktwrites, Publikation und Zugriff auf nicht benötigte Nutzerdaten einschränken. `--auto` nur falls vorhanden und nur nach expliziten scoped deny-/allow-Regeln; kein pauschales globales „alles erlauben“. Existierende Schutzgrenzen nicht ausschalten. Readonlyinstruktion plus Gitprüfung ist nicht automatisch ein OS-Sandboxbeweis.

### 6.3 Kleine Orchestrierung statt Agentenplattform

Zuerst vorhandene Runtime-Reviewstarter lesen. Sie können wiederverwendet/adaptiert werden, wenn sie diesen Vertrag erfüllen und keine alten Auftragstabellen erzwingen. Neue produktbezogene Optionen nicht durch eine Parallelkopie von 50 kB ungeprüftem Script lösen. Bevorzugt ein schmaler Nodewrapper `tools/gretl-tooling/review.mjs` und kleine Helfer.

Verlangte Verantwortungen/Methoden:

| Funktion | Pflichtverhalten |
|---|---|
| `discoverOpenCode()` | realer Pfad, Version2, verifizierte Hilfe, keine erfundenen Flags |
| `verifyModelIdentity()` | geheime Werte ausschliessen, direkten Provider/Host/Modell und Aliasbeleg erfassen |
| `preflightReviewAccess()` | echte Datei-/Test-/Evidenceprobe, später notwendige GUI/Bildfähigkeit sichtbar |
| `captureRequirements()` | Hashes der normativen Dateien und aktuellen Repositoryregeln sichern |
| `createReviewWorktree(candidateSha)` | detached, eigener absoluter Pfad, richtiger HEAD, sauberer tracked Zustand |
| `buildRequest()` | exakte ACs/Gates aus Registry, echte Basen/Artefakte/Pfade; keine Verdictfelder |
| `startReviewer()` | Argumentarray, richtiger cwd, frische Session, vollständige Logs, sichere Credentials |
| `waitForReviewer()` | tatsächliches Ende/Streamdrain, privates Backend/Kindtests zuordnen, Timeout als Fehler |
| `validateReviewArtifacts()` | Schema, Identitäten, alle ACs, Belege, eigene Gegenprobe, Findings und Prozessabschluss |
| `archiveReview()` | Originalbytes/Hashes von Bericht und Urteil, FAIL/BLOCKED behalten |
| `cleanupOwnedResources()` | nur eigene Prozesse/Worktrees nach gesicherter Evidenz; kein globales pkill |

Bei fehlendem Direct-APIzugriff zunächst bereits konfigurierte lokale Auth-/CLIintegration prüfen. Danach echte nicht autonom behebbare Authgrenze melden; keinen Ersatzprovider nehmen.

### 6.4 Commit-, Worktree- und Startablauf

1. `assignmentBase` beim Auftragsstart als echten SHA festhalten; `phaseBase` bei Phasenbeginn. Keine Null-SHAs.
2. Code/Tests/Implementierungsbericht fertigstellen; gezielte Vorprüfungen ausführen und deren noch vorläufige Identität ehrlich dokumentieren.
3. Nur eigene Änderungen lokal committen. `candidateSha` **danach** ermitteln. Bei fremdem Dirtybaum eigenen isolierten Branch-/Worktreeweg verwenden; nicht fremde Inhalte stagen.
4. Die fälligen abschliessenden Gates **frisch auf dem committed Kandidaten** ausführen, inklusive geltender Rootpflichtgates; Belege vor jedem `clean` extern sichern. Anschliessend finalen Artefaktbuild auf diesem Stand erstellen und Digests sichern. Vor-Commit-Tests sind Vorprüfungen, keine umetikettierte Kandidatenevidenz. Bei Fehler Nacharbeit, neuer Commit und betroffene finale Gates erneut.
5. Reviewworktree detached auf genau `candidateSha` anlegen; keine Quellen während B läuft ändern.
6. Request in frischem Evidenceverzeichnis schreiben, Kriterien vollständig. B arbeitet in Reviewworktree, nicht im Codex-Produktbaum.
7. Prozess starten und wirklich warten. Fehlerstatus auch unter `set -e` oder Pipes korrekt erfassen; nicht `tee` statt OpenCode prüfen.
8. Nach Ende beide HEADs/Hashes/Trackedzustände prüfen und Urteil validieren. Erst dann PASS freigeben.

Beispiel nach nachgewiesener CLIhilfe; **hier nicht ausgeführter Lauf**:

```sh
REVIEW_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/gretl-tooling-review.XXXXXX")
git -C "$REPO_DIR" worktree add --detach "$REVIEW_ROOT/product" "$CANDIDATE_SHA"
REVIEW_DIR="$REVIEW_ROOT/evidence"
mkdir -p "$REVIEW_DIR"
# request.json aus tatsächlichen Daten durch den geprüften Wrapper erzeugen.
# Berechtigungsprofil und Modell-/APIprüfung wurden bereits bestanden.
if (
  cd "$REVIEW_ROOT/product" || exit 1
  "$OPENCODE_BIN" run --standalone \
    --model deepseek/deepseek-flash --format json \
    --file docs/implementation/gretl-tooling/OPENCODE_REVIEW_PROMPT.md \
    --file "$REVIEW_DIR/request.json" \
    "Pruefe diese Phase unabhaengig. Lies alle im Auftrag genannten Spezifikationen. Fuehre eigene Tests und Gegenproben aus. Schreibe review.md und verdict.json in den Evidenceordner. Keine Produktdateien aendern."
) >"$REVIEW_DIR/opencode.jsonl" 2>"$REVIEW_DIR/opencode.stderr.log"; then
  REVIEW_EXIT=0
else
  REVIEW_EXIT=$?
fi
# Erst nach vollständigem Prozessende, Backend-/Testabschluss und Streamdrain prüfen.
```

Ein Node-`spawn` mit argv und explizitem `cwd` ist im produktiven Wrapper vorzuziehen. Das Shellbeispiel ist weder Sandbox noch vollständige Gateimplementierung. Kein `--standalone` verwenden, wenn die tatsächliche CLI es nicht kennt; einen nachgewiesenen privaten frischen Weg wählen oder blockieren.

### 6.5 Requestvertrag

`request.json` besitzt mindestens folgende Struktur. Beispielwerte in echten Requests vollständig ersetzen; `...` ist kein gültiger Nachweis.

```json
{
  "schemaVersion": 1,
  "reviewId": "<neue-echte-id>",
  "phase": "P3",
  "assignmentBase": "<vollstaendiger-sha>",
  "phaseBase": "<vollstaendiger-sha>",
  "candidateSha": "<vollstaendiger-sha>",
  "worktree": "<absoluter-detached-worktree>",
  "evidenceDirectory": "<absoluter-frischer-evidenceordner>",
  "requirementsDigest": "<tatsaechlicher-sha256>",
  "repositoryRulesDigest": "<tatsaechlicher-sha256>",
  "plan": "docs/implementation/gretl-tooling/EXECUTION_PLAN.md",
  "architecture": "docs/implementation/gretl-tooling/ARCHITECTURE.md",
  "testMatrix": "docs/implementation/gretl-tooling/TEST_MATRIX.md",
  "implementationReport": "docs/implementation/gretl-tooling/implementation/P3.md",
  "criteria": ["P3-01", "P3-02", "P3-03", "P3-04", "P3-05", "P3-06", "P3-07", "P3-08", "P3-09"],
  "requiredChecks": [{"id":"<gate-id>","cwd":"<absolut>","argv":["<echtes-kommando>"],"reason":"<auswirkung>"}],
  "requiredGuiCases": [],
  "notRequiredAdditionalChecks": [{"id":"<zusaetzlich-nicht-faellig>","reason":"<begruendung>"}],
  "artifacts": [{"path":"<absolut>","sha256":"<tatsaechlich>","kind":"<katalog-oder-server-oder-vsix>"}],
  "modelIdentityEvidence": "<redigierter-lokaler-belegpfad>"
}
```

P8 enthält `cumulative: true` und **alle** 81 IDs, nicht bloss P8-01–09. B liest tatsächliche Kriterien aus der Spezifikation; A darf nicht durch Weglassen im Request die Prüfung reduzieren. Diskrepanz verhindert Freigabe.

`requirementsDigest` umfasst normativen Plan, Architektur, Testmatrix, Reviewprompt, Kriterienregistry und die beiden Reviewschemas, nicht laufende STATUS-/Evidencefortschreibungen. Deterministische sortierte Dateiliste plus Dateihashes verwenden, keine selbstreferenzielle Manifesthashschleife. Ein geänderter Auftrag benötigt neuen Review, nicht das Wiederverwenden früherer Kriterienfreigaben.

### 6.6 Reviewerurteil und äusseres Gate

B erstellt erst nach eigenen Prüfungen `review.md` und `verdict.json`. Mindestfelder:

```text
schemaVersion, reviewId, phase, assignmentBase, phaseBase, candidateSha,
requirementsDigest, verdict: PASS|FAIL|BLOCKED,
reviewer: {openCodeVersion, provider, model, apiHost, modelMappingEvidence},
criteria: [{id, status: fulfilled|violated|unverified, evidence[], findingIds[]}],
executedChecks[], verifiedCurrentEvidence[], guiEvidence[], counterprobes[],
openMandatoryFindings[], missingRequiredChecks[], report: review.md
```

Prozessinformationen des äusseren Aufrufers stehen separat in `process.json`, nach Prozessende von A/Wrapper geschrieben: tatsächlicher argv/cwd, Start/Ende, Exit, Signal/Timeout, identifizierte Session-/Backendprozesse, abgeschlossene Tests, Reportdigests, vorher/nachher HEAD/Trackedhashes. B behauptet nicht, den zukünftigen Exit seines eigenen noch laufenden Prozesses schon zu kennen.

Gate prüft mindestens:

- tatsächlicher Reviewer- und benötigter Testabschluss, kein laufender Hintergrundjob;
- Exit0 ohne Transportfehler, keine ausstehende Permissionfrage;
- richtiges direktes APIprofil und gültiger Modellbeleg;
- exakter Review-ID-/Phase-/Basis-/Kandidaten-/Requirementsbezug;
- richtige Kriterienmenge, keine Duplikate/fehlenden/ersetzten IDs;
- jede Muss-AC `fulfilled`, alle Pflichtchecks erreichbar und belegt;
- bei P5–P8 echte neue passende GUIbelege, keine alten Bilder;
- mindestens eine **eigene neue** Gegenprobe, P8 mindestens zwei;
- keine offenen Muss-Findings und keine fehlenden Pflichtprüfungen;
- Bericht und JSON konsistent, Originaldateien erst in dieser Session entstanden;
- Kandidatenquellen/Tests/Locks/Specs unverändert vor/nach Review.

Ein HTTP200, erfolgreiches `opencode run`, beliebiges PASS in JSONL oder eine hohe Testzahl genügt nicht. Der Gatechecker bewertet strukturelle Evidenz; er ersetzt nicht Bs fachliches Urteil und behauptet keine kryptografische Unabhängigkeit von LLMs.

### 6.7 Nacharbeit, begrenzte Retries und Archivierung

Fachliches FAIL: Finding reproduzieren, passende RED-Gegenprobe hinzufügen, innerhalb derselben Phase korrigieren, fällige Regression ausführen, neuer Kandidatencommit, neue unabhängige Session. `phaseBase` bleibt gleich. Nicht den unveränderten Kandidaten wiederholt fragen, bis zufällig PASS erscheint.

Transportfehler: höchstens zwei saubere neue Versuche des unveränderten Kandidaten nach konkreter technischer Korrektur, danach BLOCKED. Inhaltliche Fehler sind keine Transportfehler. Infrastruktur kann repariert werden, soweit keine Nutzerrechte/Secrets oder Scopeentscheidung erforderlich sind.

Nach gültigem PASS Originalreview/-verdict unverändert mit Digest archivieren und STATUS in einem reinen Dokucommit fortschreiben. Produkt-SHA bleibt der geprüfte Kandidat. Neue Produkt-/Test-/Build-/Katalog-/Schemakorrektur nach PASS macht neuen Review nötig. Keine additional administrative Reviewphase nur für die Archivierung.

## 7. Berichte und endgültige Lieferung

Eine aktive [STATUS.md](STATUS.md), ein knapper Implementierungsbericht je Phase unter `implementation/` und Originalreviews je Versuch unter `reviews/` genügen. Keine automatisierte Evidenzdateiexplosion im Produkt-Git. Grosse Logs/VSIX/Bilder als lokale/CIartefakte mit Digest; für den Reviewer erreichbar oder frisch reproduzierbar.

Implementierungsbericht: Phase, Basen, tatsächliche Änderungen/Verantwortungen, ACmatrix, übernommene/neue Klassen, Testauswahl und Rootgates, echte Commands/Exits/Testzahlen/Skips, Artefakthashes, relevante GUIbelege, Befunde und nächster erlaubter Schritt. Keine erfundenen Prozentzahlen.

Abschlussbericht nach P8:

- alle Phasen mit geprüften Produkt-SHAs und unabhängigen Urteilen;
- aktuelles Serverartefakt, VSIX, Versions-/Katalogmanifest und Installationsweg;
- jede der 81 ACs mit tatsächlichem Nachweis;
- realer grafischer Erstellungs-/Roundtrip-/Runtime-Fixturepfad;
- Sourceadoption/Lizenzen und nachgewiesene Freiheit von anderen GRETL-Varianten;
- tatsächlich geprüfte Plattformen, Javaanforderung und bekannte statische Grenzen;
- erhaltene Runtimegates und bewusst nicht beauftragte Funktionen;
- keine Publikation, keine produktive Jobausführung und keine Veränderungen fremder Daten.

**Erfolg ist ein benutzbares, sicheres Editorprodukt auf genau einer Runtime-Sprachbasis. Nicht die Anzahl Klassen, Markdownseiten oder Testaufrufe.**
