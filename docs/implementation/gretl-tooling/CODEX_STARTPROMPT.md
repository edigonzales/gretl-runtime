# Codex-Startprompt – GRETL-Sprachkern, LSP und grafischer Modeler

**Du bist Agent A: Implementierung und autonome Review-Orchestrierung.** Setze diesen Auftrag im bestehenden Repository `edigonzales/gretl-runtime` vollständig in **P0 bis P8** um. Agent B ist eine getrennte neue OpenCode-2.x-Session mit **DeepSeek-V4.1-Flash-1017 über die direkte DeepSeek API**.

Dieser Prompt ist ein **Implementierungsauftrag**, nicht der Auftrag, eine weitere allgemeine Spezifikation zu verfassen. Eine nächste Phase darf erst beginnen, wenn Agent B die aktuelle Phase am unveränderten Kandidaten mit PASS abgenommen hat und der äussere Reviewprozess tatsächlich abgeschlossen und validiert ist. Nach einem gültigen PASS ohne Benutzercheckpoint weiterarbeiten. FAIL bedeutet Nacharbeit derselben Phase; ein nicht autonom behebbares BLOCKED bedeutet belegten Stopp, keine Ersatzfreigabe.

## 1. Zuerst vollständig lesen

Im Verzeichnis `docs/implementation/gretl-tooling/`:

1. [EXECUTION_PLAN.md](EXECUTION_PLAN.md), einschliesslich sämtlicher Phasen-/Reviewregeln;
2. [ARCHITECTURE.md](ARCHITECTURE.md), insbesondere Source-/Snapshot-/Edit- und Modulgrenzen;
3. [TEST_MATRIX.md](TEST_MATRIX.md), nicht nur die Überschriften;
4. [SOURCE_AUDIT.md](SOURCE_AUDIT.md), damit Altbefunde und Sollentscheidungen nicht verwechselt werden;
5. [OPENCODE_REVIEW_PROMPT.md](OPENCODE_REVIEW_PROMPT.md) und [ACCEPTANCE_CRITERIA.json](ACCEPTANCE_CRITERIA.json).

Zusätzlich geltende Root-/Unterverzeichnis-`AGENTS.md`, `docs/README.md`, aktuelle Runtimeverträge, Build-/Test-/Subprojektanleitungen. Historische abgeschlossene Aufträge sind kein neuer Auftrag. Aktuellen Code prüfen, nicht alte Spezifikationen als bereits implementiertes Verhalten annehmen.

## 2. Feststehende Architekturentscheidungen

**Ein Repository.** Java-Kern, Katalogexporter, Java-LSP, Extension und Modeler werden in `gretl-runtime` integriert. Kein neues entferntes Repository erforderlich. Recherchierte Runtimebasis `17057baa32c8407a2c3a69f744c6686c8258f78f`; tatsächlicher lokaler HEAD kann neuer sein. Niemals darauf zurücksetzen.

**Keine andere GRETL-Variante.** `gretl-next` darf für kontrollierte Quellübernahme und Lizenz-/Commit-/Dateihashprovenienz verwendet werden. Danach müssen Build, Tests, Packaging und Nutzung ohne diesen Checkout funktionieren. Kein Alt-JAR, Gradle-API, `gretl-doclet`, Aliaspaket, Siblinglink, type-only Import, Downloadscript oder externer Testhelper aus einem anderen GRETL-Stack. Historische Quellen-/Lizenznennung erhalten.

**Ein statischer Java-Sprachkern.** Gemeinsame reine DSL-/Namens-/Graphregeln in `gretl-language-contract`; Parser/Recovery, Workspace/Snapshots, Katalog, Binder, Queries, Editplanung und Graphprojektion in `gretl-tooling-core`. `gretl-lsp` ist Host-/Protokolladapter. Das Frontend hat keine zweite DSLsemantik.

**Keine Ausführung beim Editieren.** Kein Scriptloader, `gretl plan/check/run`, `Script.run`, GroovyShell oder Handler-/Builder-/Providerinitialisierung aus Workspacecode. Reiner sicherer Parsepfad, kontrollierter Classpath und echte Nichtausführungsgegenproben.

**Katalog aus der Runtime-SPI.** Buildexport aus `OperationType`/Input-/Outputmetadaten, additive geprüfte Datenhinweise für fehlende Toolinginformation. LSP bekommt nur Daten, keine schweren Task-/Runnerdependencies. Keine manuell kopierte Operationsliste.

**Groovy bleibt massgeblich.** `job.groovy` und `settings.gretl` bleiben ausführbare Quellen. Keine zweite YAML-/JSON-Jobdefinition. Gezielte Textedits mit erhaltenen Kommentaren/Zeilenenden; unbekannter Code wird nicht umgeschrieben.

**Modeler ist schreibend.** React + TypeScript + React Flow + ELKworker, lokal in VS-Code-Webview. Taskanlage, Konfiguration, Dependencies, Resultbindungen, Defaults, konservatives Rename/Delete und echter Text-Roundtrip sind Pflicht. Layout bleibt view-only. Kein Kaoto-/GLSPserver, kein Runtime-/Debugprojekt.

## 3. Arbeitsbeginn

Ermittle tatsächlichen Repo- und Arbeitsstand:

```sh
pwd
git status --porcelain=v1
git branch --show-current
git rev-parse HEAD
git remote -v
```

Bewahre fremde Änderungen. Nutze eigenen lokalen Arbeitsbranch bzw. isolierten Arbeitsworktree, ohne Nutzerdateien zu verwerfen oder ungefragt mitzucommitten. `REPO_DIR` ist ein realer absoluter Pfad, keine übernommene `/Users/...`-Annahme.

Prüfe tatsächliches Java25, Mavenwrapper, Groovyversion, Node/npm, Docker sowie vorhandene Reviewwerkzeuge. Die Rootregeln verlangten zum Recherchezeitpunkt beide vollständigen Maven-Gates; diese nicht zugunsten einer bequemeren Teilprüfung entfernen. Gezielte Tests in Arbeitsschleifen ergänzen diese Gates.

Die angehängte INTERLIS-Vorlage ist nur Verfahrensvorbild. **Kein fremdes Pythonverbot, kein `without-python.cjs`, keine Zwei-Repo-Kandidatenstruktur und keine Sprotty-/Readonly-Modelervorgabe übernehmen.** Bestehende Runtime-Pythonharnesses bleiben erlaubt; neue eigene Glue mit Node genügt.

Führe vor grosser Implementierung einen echten OpenCode-/DeepSeek-Bereitschaftstest durch. Prüfe Version2 und tatsächliche `run --help`, vorhandene gespeicherte Anmeldung, wirksamen Provider/Host/Modell und offizielle Aliaszuordnung. Kein neuer `DEEPSEEK_API_KEY` als pauschale Voraussetzung. Keine Credentialdateien/Token ausgeben. Prüfe projektbezogen erlaubtes Dateilesen, eigenen Testprozess und Evidencewrites. Verifiziere früh die spätere echte GUI-/Bilderprüfbarkeit, ohne Produktphasen vorzuziehen.

## 4. Phasen strikt ausführen

### P0 – Arbeitsfähige Verträge, Katalog und Reviewkette

ACs P0-01–09. Aktuelle Quellen-/Lizenz-/Dependencybasis erfassen, reine DSL-/Projektnameverträge und vollständigen deterministischen Katalog liefern. Katalogfehler müssen sichtbar sein. Reviewwrapper implementieren/adaptieren, echte Preflight-/Gategegenproben. P0 ist keine reine Dokumentationsphase. Noch kein Produkt-LSP und kein Modeler.

### P1 – Sicheres Parsing und Recovery

ACs P1-01–09. Aktuelle Groovy-DSL ohne Ausführung lesen. AST/Token/Recovery mit konkreten Bereichen, allen Defaults, Actions/Parametern/Cleanup und inneren Invocations. Fehler ohne Tasks erhalten, Kommentare/Strings korrekt, UTF-16/CRLF/Emoji prüfen. Kein weiterverwendeter Regexscanner als alleinige Strukturautorität.

### P2 – Projekte und Semantik

ACs P2-01–09. Bufferoverlay/Snapshots/Generation, Settings/Subprojekte, Symbols/References, Parameter-/Feld-/Resultregeln und jobrelative SQLanalyse. Reine Graphsemantik mit bestehendem Runner teilen, after nicht mit Datenabhängigkeit verwechseln. Dynamische Unkenntnis nicht in sicher falsche Fehler verwandeln.

### P3 – Queries und Editkern

ACs P3-01–09. Vollständige beauftragte LanguageQueries, neutrale Graphprojektion und alle späteren Modelerintents als reine Javafunktionen implementieren. Geplante Edits wirklich anwenden/reparsen, unberührte Texte prüfen. Referenzen, Quotes, Transitivität, Defaults, Multisourcepreconditions und konservative Editability testen. Noch keine grafische Oberfläche.

### P4 – Echter Java-LSP

ACs P4-01–09. LSP4J/stdin/stdout, echter Lifecycle, Sync/Cancellation, Workspacewatcher, Standardfeatures und versionierter Customvertrag. Distribution ohne Runtimeausführungsmodule. Echter Wire-/Prozesstest, nicht nur direkte Methodenaufrufe. Server muss bei Exit tatsächlich enden.

### P5 – Installierter Texteditor

ACs P5-01–09. Eigene VSIX mit passendem Server/Katalog, Java25-Erkennung, richtige Dateiauswahl, native Edits/Undo, Trust/Restart. G01–G10 als echte installierte Workbenchprüfung. Noch keine Modelerproduktarbeit.

### P6 – Interaktiver Graph

ACs P6-01–09. Coregraph → LSP → React Flow; lokale ELKworker, Selektion/Drag/Pan/Zoom/Filter, Navigation, Teil-/Stalezustände und getrennte Layoutpersistenz. G11–G16 plus Editorsmoke. Keine fachliche Sourceänderung durch Node-Drag.

### P7 – Schreibender Modeler

ACs P7-01–09. Palette/Formulare, Tasks/Parameter, Dependencies und Resultbindungen, Defaults, sichere Rename-/Delete-/Disconnectaktionen. Alle Codeänderungen aus Coreplänen, keine TypeScript-Groovycodegenerierung. G17–G25 und unveränderten GUI-generierten Job im lokalen Runtime-SCRIPT-Fixture prüfen. Komplexer Code und Kommentare bleiben erhalten.

### P8 – Gesamtabnahme

ACs P8-01–09 sowie kumulativ alle 81 Kriterien. Frischer Build ohne Altrepos, zusammengehörige Artefakte, vollständige neue Tests und G01–G30 auf tatsächlicher Primärplattform, Linux-LSP-Prozess, Resilienz/Ressourcen und aktuelle Dokumentation. Ungetestete Plattformen ehrlich abgrenzen. Kein Publish.

## 5. Tests und Arbeitsberichte

Vor jeder Phase: betroffene Verantwortung/Consumer, gezielte Tests und Rootpflichtgates knapp festhalten. Tests nicht nachträglich abwählen, weil sie rot sind. Kein `--passWithNoTests`, kein Catch-and-PASS, keine gelockerten Assertions für eine schwierige Fixture.

Vor jeder weiteren `clean`-Ausführung Evidence sichern. Reale Commands, SHA, Exits, Testzahlen/Skips und Artefakthashes festhalten. Ein erzeugtes Archiv allein beweist weder Startbarkeit noch GUIfunktion. Der installierte GUIharness darf nicht das normale VS-Code-Profil ändern.

Ein aktuelles STATUS.md, ein Implementierungsbericht je Phase und Originalreviewberichte genügen. Keine Dokumentations-/Auditdatenbank als Ersatz für Featurearbeit. Neue Klassen und Gates fachlich benennen, nicht nach Phasennummern.

## 6. Jeder Review läuft autonom

1. Phase fertigstellen, Tests und Implementierungsbericht abschliessen.
2. Eigene Produktänderungen committen, vollständigen Kandidaten-SHA danach ermitteln.
3. Abschliessende Pflichtgates frisch auf dem committed Kandidaten ausführen und sichern; neue Artefakte eindeutig diesem Kandidaten zuordnen, Digests prüfen. Vor-Commit-Vorprüfungen nicht als solche finalen Belege umetikettieren.
4. Detached Reviewworktree auf Kandidat anlegen. `assignmentBase` konstant; `phaseBase` über Reparaturen derselben Phase konstant.
5. `request.json` mit exakten ACs/Gates/Artefakten/Requirementsdigest ausserhalb getrackter Produktdateien erzeugen. Kein vorgefülltes PASS.
6. Neue private OpenCode-2.x-Session mit verifiziertem `deepseek/deepseek-flash`, direktem `api.deepseek.com`, bestehender Anmeldung und geeignetem scoped Berechtigungsprofil starten. Keine manuelle GUIübergabe.
7. Wirklich bis Prozessende/Streamdrain/Ende benötigter Tests warten. Nicht eine laufende Session als Reviewabschluss behandeln.
8. Original `review.md` und `verdict.json`, Modell-/SHA-/Kriterienidentität, eigene Gegenproben, fällige echte GUIbelege und unveränderte tracked Dateien prüfen.
9. Nur wenn alles passt PASS freigeben, Originalbelege archivieren und Status in reinem Dokucommit fortschreiben. Danach automatisch nächste Phase.

Fachliches FAIL zuerst reproduzieren/beheben, neuer Kandidat, neuer Review. Kein Nachfragen beim unveränderten Kandidaten bis zufällig PASS. Technische Transportwiederholung höchstens zweimal nach belegter Korrektur. Nicht autonom behebbares Auth-/Rechte-/Infrastrukturproblem ist BLOCKED mit konkreter Entblockung, keine implizite Benutzerfreigabe.

Der Reviewer prüft **Zielerreichung, Architektur, Implementierung und Wirksamkeit der Tests**, nicht nur grüne Kommandos. Er darf die Anforderungen nicht auf Codex' implementierten Teil verkleinern. Nach jeder Phase mindestens ein eigener neuer Gegenfall, ab P5 auch reale GUIprüfung; P8 mindestens zwei übergreifende neue Gegenfälle.

## 7. Abschluss

Nach P8 tatsächliche Produkt-SHAs, installierbares Serverartefakt/VSIX, alle 81 Kriterien, echte Test-/GUI-/Runtime-Fixturebelege, Sourceadoption/Lizenzen, Dependencyfreiheit, Java-/Plattformgrenzen und bewusst nicht implementierte Nicht-Ziele berichten. Keine fiktiven Tests, kein angeblicher Marketplaceupload.

**Beginne jetzt mit P0. Arbeite nach jedem gültigen unabhängigen PASS selbständig weiter bis P8.**
