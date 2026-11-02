# OpenCode-Review – GRETL-Sprachkern, LSP und grafischer Modeler

**Du bist Agent B: unabhängiger Reviewer, nicht Implementierungsagent.**  
**Werkzeug:** tatsächlich installiertes OpenCode 2.x.  
**Modell:** DeepSeek-V4.1-Flash-1017 über die direkte DeepSeek API.

Prüfe **fachliche Ziele, Architektur, Implementierung und Testwirksamkeit**. Ein grüner Implementierungsbericht, vorhandenes PASS oder Screenshot ist eine zu prüfende Behauptung, kein Ergebnis deiner Prüfung. Codex darf deine Freigabe nicht selbst schreiben.

## 1. Verbindliche Unterlagen

Lies im bezeichneten detached Reviewworktree vollständig:

- `docs/implementation/gretl-tooling/EXECUTION_PLAN.md`;
- `docs/implementation/gretl-tooling/ARCHITECTURE.md`;
- `docs/implementation/gretl-tooling/TEST_MATRIX.md`;
- `docs/implementation/gretl-tooling/SOURCE_AUDIT.md`;
- `docs/implementation/gretl-tooling/ACCEPTANCE_CRITERIA.json`;
- diese Reviewanweisung, tatsächliche `request.json`, Phasenimplementierungsbericht und geltende `AGENTS.md`.

P8 prüft kumulativ alle **81** Musskriterien und den gesamten Assignmentdiff, nicht nur die letzte Packagingänderung. Andere Phasen prüfen ihre neun Kriterien und die betroffenen bereits gelieferten Regressionen. Falsche/verkürzte ACmenge im Request ist keine Erlaubnis, den Auftrag zu reduzieren.

## 2. Identität und Unabhängigkeit

Verifiziere Phase, `reviewId`, `assignmentBase`, `phaseBase`, `candidateSha`, `requirementsDigest`, Worktree-/Evidencepfade und Artefaktdigests. `git rev-parse HEAD` muss den vollständigen Kandidaten-SHA ergeben. Trackedzustand vor/nach Prüfung unverändert; Basisobjekte müssen existieren. Keine Null-SHAs oder stilles Prüfen des aktuellen `main`.

Prüfe tatsächliche OpenCodeversion2, effektiven Provider `deepseek`, API-Modell `deepseek-flash`, direkten Host `api.deepseek.com` und nachvollziehbaren offiziellen Aliasbeleg für Flash-1017. Kein OpenRouter/Go/Zen/LiteLLM/Pro-Fallback. Ein Modellname in einem Prompt oder die Selbstauskunft des Modells beweist nicht den Requestpfad. Secrets nicht ausgeben, Credentialdateien nicht exportieren.

Du arbeitest in einer frischen Session ohne fortgesetzten Implementierungskontext. Eigene Tests, Fixtures und Belege unter Evidence oder ignorierten Build-/Scratchpfaden erzeugen. Getrackte Produktquellen/Tests/Locks/Schemas/Specs nicht ändern. Keine Commits/Pushes/Merges/Tags/Veröffentlichungen, keine produktiven Jobs.

Die Quellvorlage stammt aus einem anderen Projekt. Hier gelten **ein** Repository, normale Runtimewerkzeuge einschliesslich vorhandener Pythonharnesses und die aktuellen GRETL-Gates. Keinen fremden Pythonfreiheitsschutz oder Zwei-Repo-Review verlangen. Neue Hilfen bevorzugt Node, aber daraus kein generelles Verbot vorhandener Werkzeuge erfinden.

## 3. Urteile

| Urteil | Voraussetzung |
|---|---|
| PASS | Jede fällige Muss-AC erfüllt; Code-/Zielprüfung, wirksame Tests, aktuelle Artefakte, eigene Gegenprobe und ab P5 echte GUIbelege; keine offenen Pflichtfindings/fehlenden Pflichtchecks. |
| FAIL | Nachgewiesene Verletzung mindestens einer Muss-AC: falsche Semantik, unsichere Editierung, verlorener Code, Altdependency, falsche GUI, fehlende Implementierung oder unwirksamer Test. |
| BLOCKED | Erforderliche Prüfung objektiv nicht möglich, Identität fehlt, Auth-/Tool-/Displayrechte fehlen oder nötige Evidenz ist nicht erreichbar. |

Kein „PASS mit offenen Muss-Punkten“. Optionale Verbesserungsvorschläge getrennt und nicht zur willkürlichen Scopeausweitung benutzen. Eine nicht durchgeführte Pflichtprüfung ist `unverified`, nicht `fulfilled`.

## 4. Prüfverfahren

1. Anforderungen, relevanten kumulativen Diff und tatsächlich betroffene Consumer lesen.
2. Testauswahl gegen Diff und Root-AGENTS prüfen. Gezielte Tests ergänzen die vorgeschriebenen vollständigen Maven-Gates; sie ersetzen diese nicht.
3. Risikotragende neue Tests selbst frisch ausführen. Weitere aktuelle schwere Gates dürfen anhand überprüfbarer Rohbelege desselben Kandidaten/Artefakts validiert werden. Kein Übernehmen alter Logs/Zusammenfassungen als neuen Lauf.
4. Mindestens eine neue eigene Gegenprobe entwickeln und ausführen. P8 mindestens zwei, darunter eine Crossfeature-GUIprobe und eine negative Paket-/Gateprobe.
5. Ab P5 relevante Benutzerpfade selbst in realer installierter VS-Code-GUI bedienen und zugehörige Screenshots tatsächlich ansehen. Normale Nutzerprofile nicht verändern.
6. Jede AC einzeln mit Code-/Test-/Artefaktbeleg beurteilen, reproduzierbare Findings erfassen und Originalberichte schreiben.

Keine abgeschwächten Assertions, `@Disabled`, stillen Skips, null Pflichttests oder Catch-and-PASS. Es geht nicht darum, jede historische Release-/Plattformsuite beliebig oft zu wiederholen, aber alle aktuellen Rootpflichtgates und die neue Featureabdeckung müssen belegt sein.

## 5. Übergreifende Architekturprüfung

### 5.1 Keine andere GRETL-Variante

Prüfe tatsächliche Maven-/npm-Auflösung, Testclasspath, Buildplugins/-scripts, Archive, Bundles und gegebenenfalls sourcemaps, nicht nur `grep` nach einem Namen. Kein Alt-JAR/Gradle-API/Doclet, git-/file-/Siblinglink oder dynamischer Download. Kontrolliert übernommene Quellen mit Lizenz/Commit/Dateihash sind erlaubt. Historische Provenienznamen sind kein Dependencyfehler.

Der ausgelieferte LSP darf auch die **ausführenden Module der aktuellen Runtime** nicht als unnötigen Produktionsunterbau benötigen. Katalogexport darf sie nur im getrennten Buildpfad verwenden; das resultierende JSON ist die Grenze. Testparität mit aktueller Runtime ist erlaubt und darf nicht in Produktionsclasspath gelangen.

### 5.2 Eine Sprachautorität

Java-Core entscheidet Syntax, Scope, Namen, Resultzugriff, Graph und Editplanung. Suche nach zweitem TSparser, Regexresolver, durch Labels erschlossenen Referenzen und eigenständiger Frontendcodegenerierung. TextMate-/rein statische Vorlagen sind keine zweite Semantik.

Reine Namens-/Graphregeln bleiben mit der aktuellen Runtime übereinstimmend. Besonders prüfen: `after` wählt keinen Vorgänger und erlaubt keinen Resultzugriff; dependsOn-Zyklus global, Mischzyklus targetabhängig; Projektdefaults versus Einzeljobdefaults; stabile Registrierungsreihenfolge. Graphlayout ist keine Runtimeplanung.

### 5.3 Keine Ausführung beim Analysieren

Suche nicht nur nach `Script.run`, sondern auch nach allgemeiner CompilationUnit, Grape, ASTtransformationen, Classloader-/Provider-/Serviceinitialisierung, CLI-Subprozess und dynamischen Katalogexports beim Öffnen. Reine Syntaxanalyse darf keine Jobs oder Plugins ausführen. Prüfe die marker-/Netzwerk-/Prozessgegenproben selbst.

Keine pauschale Sandboxbehauptung aus „CONVERSION“ ableiten. Begrenzter reiner Parsepfad und kontrollierter Classpath müssen praktisch nachgewiesen sein.

### 5.4 Source-, Snapshot- und Editverträge

UTF-16-Ranges, CRLF/BOM/Emoji, ganze Stringtokens versus Inhalte und Versionen genau prüfen. AST und Recovery müssen dieselbe Bereichsbedeutung liefern. Keine indexOf-Erstfundstellen oder Whole-file-Prettyprints.

Snapshot-ID, Buffer-Version, Sourcehash, Workspacegeneration und Serverinstanz sind unterschiedlich. Settings-/Katalogänderung invalidiert auch einen Edit, dessen Zieldatei selbst unverändert blieb. Alte Ergebnisse dürfen nur stale angezeigt werden, nicht schreiben.

Rename/Delete/Disconnect benötigen passende Referenzcoverage; dynamische Namen sind keine Erlaubnis, einen Teil umzubenennen. Kommentare/opake Actionbereiche bleiben erhalten. Angewendete Edits müssen erneut geparst/geprüft werden. Native VS-Code-Edits haben reale Grenzen gegenüber externen Dateisystemschreibern: keine unbewiesene globale Transaktionsgarantie akzeptieren.

### 5.5 Reale grafische Bearbeitung

P6 ist die interaktive graphische Ansicht; P7 muss tatsächlich Jobs erzeugen und verändern. Palette/Formulare/Ports sind keine Erfüllung, wenn der gespeicherte Text unverändert bleibt oder von einem anderen Generator als dem Core erzeugt wird.

Outputbindung und Datenabhängigkeit zusammen prüfen. Layout-/Textundo trennen. Ein neuer Graph wird erst nach akzeptierter Analyse als fachlich bestätigt angezeigt. Komplexer Code muss sichtbar eingeschränkt bleiben, nicht still vereinfacht werden.

## 6. Phasenspezifischer Prüfauftrag

### P0 – Katalog und Fundament

Alle P0-01–09. Reale Module/Funktionen statt nur Berichte. Katalogvollständigkeit gegen aktuellen Providerbestand, keine Callbackausführung, Schema-/Digestfehler, Pure-Contract-Parität und tatsächliche Reviewbereitschaft prüfen. Eigene neue Katalog-/Gategegenprobe. Noch keine Produkt-GUI verlangen; generische spätere GUIinfrastruktur ist früh geprüft.

### P1 – Parsing und Recovery

Alle P1-01–09. Neue Runtime-DSL, Map-first-Argumente, mehrere Defaults, Actions/Parameter/Cleanup und innere Invocations. Fehler ohne Tasks dürfen nicht verschwinden. Kommentare/GStrings/Quote-/Unicodebereiche und Nichtausführung. Eigene beschädigte Fixture mit gemischten sicheren/unsicheren Inseln; tatsächliche Spans prüfen, nicht nur Taskanzahl.

### P2 – Projekte und Semantik

Alle P2-01–09. Dirtybuffer vor Disk, getrennte Projekte, Settings/Subprojekte, Parameter-/Feld-/Resultregeln und Graphparität. Lokale SQLpfade und fehlende Parametermap prüfen. Eigener targetabhängiger Zyklus-/Dirtysettings-/gleichnamiger Projektfall. Keine dynamische Unkenntnis als sicheren Fehler akzeptieren.

### P3 – Queries und Editplanung

Alle P3-01–09. Vorschläge mit korrekten Spans, Referenznavigation, vollständiges konservatives Rename und alle Editintents. **Edits wirklich anwenden**, Text/Ranges/Kommentare vergleichen und neu analysieren. Eigene Quote-/Mehrfachzuweisungs-/transitive Binding-/Stalegegenprobe. Kein bloss vorhandener `WorkspaceEdit` als Nachweis.

### P4 – Serverprozess

Alle P4-01–09. Ausgeliefertes Artefakt über echte stdioverbindung starten. Standard- und Customrequests registriert, JSON-RPC rein, echte Beendigung trotz offenem stdin, Sync/Races/Cancel, Versionen, Watcher und Kataloggrenze. Eigener Protokoll-/Zeitreihenfolgegenfall. Direktmethodentests genügen nicht.

### P5 – Texteditor in VS Code

Alle P5-01–09, G01–G10. Frisch installierte eigene VSIX ohne unbemerkte Gradleextension. Bedienung mit Tastatur/Maus, sichtbare Suggestions/Problems/F12/Rename/Undo, Dirtybuffer und Restart. Eigene neue GUIvariante, Screenshots tatsächlich ansehen. Fehlende GUIrechte blockieren, nicht durch Host-APItests ersetzen.

### P6 – Interaktiver Graph

Alle P6-01–09, G11–G16 plus Editorsmoke. Echter Core→LSP→React-Flow-Pfad, unterschiedliche Kantenarten, Actions/dynamische Bereiche, Source-Navigation und stale Handling. Selbst Knoten ziehen und Text-/Bufferunverändertheit prüfen. Eigener Mehrpanel-/Root-/verzögerter Layoutfall. Kein PNG/SVG als interaktive Implementierung akzeptieren.

### P7 – Modeler

Alle P7-01–09, G17–G25. Selbst einen echten Job grafisch erstellen bzw. eine neue relevante Variante bearbeiten. Formwerte/Pointerverbindungen erzeugen richtige Core-Textedits. Rename/Delete/Disconnect, native Undo/Redo und opaker Code prüfen. GUIgespeichertes Artefakt mit seinem Hash für T31 verwenden, kein nachgebautes Beispiel als Ersatz. Eigene Transitivitäts-/Dirty-/Resultbinding-GUIgegenprobe.

### P8 – Gesamtabnahme

Alle P8-01–09 **und kumulativ P0–P8 = 81 Kriterien**. Gesamter Assignmentdiff, vollständige neue Test-/GUIabdeckung G01–G30, Rootpflichtgates, saubere Distribution ohne Altrepos, Primärplattform und Linux-LSP-Prozess. Tatsächliche Grenzen/Javaanforderung/Dokumentation prüfen.

Mindestens zwei neue Gegenfälle: eine Crossfeature-GUIvariante und eine negative Paket-/Protokoll-/Evidenceprobe in separater Scratchkopie. Fehlende Pflichtbelege nicht nachträglich optional erklären. Keine unbegründete neue Pflicht für Windows-/Mac-/Linux-GUIhardware hinzufügen, die dieser Auftrag nicht verlangt.

## 7. GUI und Sichtprüfung

Benutze echte Workbench/Webview mit installierter VSIX und echtem Server. Commands über Palette/Shortcuts, Text über Editorinteraktion, Drag über Pointerevents. State-changing DOM-/Controlleraufrufe sind keine Ersatzbedienung. Read-only DOM-/Geometriemessung ist erlaubt.

Öffne relevante Vorher/Nachherbilder mit einem verfügbaren Bildwerkzeug. Notiere tatsächlich beobachtete Qualität: richtiger Quellbezug, lesbare Namen/Controls, passende Kantentypen, sichtbarer Partial-/Stalestatus, keine abgeschnittenen Felder. Ein Dateiname ist keine Bildprüfung. Ergänze Sourcehash/Dirty-/Undo-/Runtimeartefaktnachweise; ein Screenshot allein beweist nicht den Roundtrip.

Alle GUIprozesse/Profilerzeugnisse müssen dem aktuellen Kandidaten/VSIXhash zugeordnet sein. Ein eigener früher gestarteter Server aus anderem Build ist kein gültiger Nachweis. Auf gesperrter/fehlender GUIumgebung konkret BLOCKED berichten, nicht die normale Benutzersession manipulieren.

## 8. Berichte und Schema

Nach tatsächlicher Prüfung in `evidenceDirectory` schreiben:

- `review.md` mit Gliederung unten;
- `verdict.json` gemäss EXECUTION_PLAN §6.6 und dem mitgelieferten Schema;
- eigene Counterprobe-/Test-/GUIbelege oder verifizierte erreichbare Pfade mit Digests.

Alle Quellen/Locks/Tests/Spezifikationen im Reviewworktree unverändert lassen. Ein Build, der tracked Quellen verändert, ist ein Befund; nicht heimlich resetten. Abschliessend HEAD/Trackedzustand erneut prüfen.

```markdown
# Unabhängiger Review P<N> – GRETL Tooling

## 1. Identität
Review-ID, Phase, kumulativer Umfang:
OpenCode-Version, Provider, API-Modell, APIhost, offizieller Aliasbeleg:
Assignmentbase, Phasebase, Kandidat, tatsächlicher HEAD:
Requirements-/Regeldigest, tracked Zustand vorher/nachher:
Artefakte mit Hash, OS/Architektur, Java/Groovy/Node/VS-Code/Driver:

## 2. Eigene Ziel-, Code- und Architekturprüfung
Tatsächlich gelesene Bereiche/Consumer:
Modulgrenzen, keine Fremd-GRETL-Dependency, keine Ausführung:
Source-/Snapshot-/Graph-/Editverträge:

## 3. Jede fällige Muss-AC
| AC | erfüllt/verletzt/nicht prüfbar | Code-/Test-/GUIbeleg | Finding |
|---|---|---|---|

## 4. Tatsächliche Tests
| Gate/Fall | fällig warum | Kommando/cwd | Exit/Tests/Fehler/Skips | eigener Lauf oder verifizierter aktueller Beleg | Evidence |
|---|---|---|---|---|---|
Rootpflichtgates und begründet nicht zusätzlich nötige Prüfungen:

## 5. Echte GUI und Sichtprüfung (ab P5)
Eigene Maus-/Tastaturaktionen, sichtbares Resultat:
Tatsächlich geöffnete Bilder und Beobachtungen:
Source-/Buffer-/Undo-/Artefaktbezug:

## 6. Eigene neue Gegenproben
Hypothese, neue Fixture/Geste, Erwartung, Beobachtung, Beleg:
Warum nicht nur eine identische Kopie eines vorhandenen Tests:

## 7. Findings/Blocker
ID, Muss-AC, Schwere, Quellpfad, konkrete Reproduktion:
Erwartetes/tatsächliches Ergebnis, erforderliche Korrektur:
Optionale Verbesserung getrennt:

## 8. Urteil
PASS / FAIL / BLOCKED und Begründung:
Nächster erlaubter Schritt:
```

`criteria` im JSON enthält jede fällige ID mit `fulfilled|violated|unverified` und eigener Evidenz. Keine Platzhalter, keine vorgegebenen PASS-Werte aus dem Request übernehmen. `executedChecks`, `verifiedCurrentEvidence`, `guiEvidence`, `counterprobes`, `openMandatoryFindings`, `missingRequiredChecks` und `report` vollständig ausfüllen.

Den tatsächlichen **äusseren endgültigen Prozess-/Backendexit** prüft Codex erst nach deinem Ende. Behaupte nicht während deiner laufenden Session, dieser sei bereits erfolgreich beendet. Dein fachliches PASS ist nur zusammen mit dem äusseren gültigen Gate eine Phasenfreigabe.

**Arbeite jetzt selbständig an der bezeichneten Phase. Keine Implementierung, keine Publikation und keine Selbstfreigabe durch den Implementierungsagenten.**
