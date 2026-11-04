# Ausführbarer Startprompt für Codex: GRETL-Scriptfragmente

**Fassung 1.3 · 4. November 2026 · direkter Auftrag auf dem integrierten Mainstand nach Toolingabschluss.**

> Diesen Text als ausdrücklichen Implementierungsauftrag übergeben. Zielrepository: `edigonzales/gretl-runtime`. Alle Repositorypfade sind relativ zu dessen Root. Der Auftrag liegt unter `docs/implementation/script-includes/`, nicht im gleichnamigen Dokumentensatz des Toolingprojekts.

Du bist **Codex, der verantwortliche Implementierungsagent**. Implementiere **`apply from: 'tasks/fragment.groovy'`** gemäss [EXECUTION_PLAN.md](EXECUTION_PLAN.md). Liefere konkrete Runtime-/Packageränderungen, notwendige Tests, lokale Commits und nach **jeder Phase** eine autonome unabhängige Prüfung durch **OpenCode 2.x mit DeepSeek-V4.1-Flash-1017**. Keine neue allgemeine Planung anstelle der Umsetzung.

## 1. Umfang und Arbeitsbasis

Das Feature verteilt einen GRETL-Job auf mehrere Groovy-Dateien **im selben Definitionsnamensraum**. Vorhandene Subprojekte bleiben bestehen. LSP und schreibender Modeler sind vollständig umgesetzt und in `main` integriert; der frühere Toolingauftrag wird nicht fortgesetzt.

**Geprüfter Mainpin:** `0bfc8f0fccf43989d751d4f5490202311d200628`, Merge von Pull Request #2. Die Status-/P8-Validierungsdateien in Main belegen den abgeschlossenen P0–P8-Auftrag am Tooling-Produktkandidaten **`54bb123070fc7bd81aea58b4e1191beecf568cbf`**. Der Main-Merge und der ursprüngliche Review-SHA sind verschieden und bleiben als solche dokumentiert. Der Vergleich der vollständigen Git-Root-Trees mit dem Recherchepin aus v1.2 zeigt ausser `docs/` identische Inhalte. Daher bleiben Architektur und Runtime-Verträge bestehen; vereinfacht wird die Basis-/Integrationsplanung.

**Direkt von integriertem Main starten:** eigener Branch `codex/script-includes`, eigener Worktree. Neueren Mainstand nach tatsächlicher Deltaprüfung verwenden; kein Reset auf einen Recherchepin. `assignment_base_sha` bezeichnet den tatsächlichen Main-Ausgangspunkt, `tooling_base_sha` den enthaltenen akzeptierten Tooling-Produktkandidaten. Nach geprüfter Abstammung gilt `base_status: MAIN_WITH_ACCEPTED_TOOLING`. Keine neue P8-Freigabe abwarten und keinen zusätzlichen Merge mit dem alten Toolingbranch verlangen.

Ein bereits begonnener Include-Branch wird erhalten. Nur falls dieser Main noch nicht enthält, den tatsächlichen lokalen Integrationsbedarf nach Plan §4.1 erledigen, relevante Nachtests ausführen und den aktuellen Kandidaten unabhängig prüfen. Kein künstlicher Neustart bereits gültiger Teilphasen. Historische Reviews bleiben an ihre ursprünglichen SHAs gebunden.

Der Lieferumfang bleibt **Runtime-Includes plus sichere partielle IDE-Koexistenz**. Keine vollständige Fragmentauflösung, Completion, Include-Navigation, Mehrdateien-Rename oder automatische Kontextauswahl eines Shared-Fragments im LSP. Kleine nachweislich nötige Editor-/Core-Guards sind in P4 im eigenen Featurebranch erlaubt; eine zweite Toolingimplementierung nicht. `finalizedBy` und weitere neue Ablaufmechanismen bleiben vertagt.

## 2. Sofortige Schritte

1. Arbeitsverzeichnis, `git status --short`, HEAD, `git worktree list --porcelain`, aktuelle lokale/Remote-Refs, JDK 25 und Mavenwrapper prüfen. Fremde Änderungen erhalten. Kein Stash, Reset, Clean oder Branchwechsel in fremden Worktrees.
2. Root-`AGENTS.md`, die drei aktiven Feature-Auftragsdateien und [BRANCH_REASSESSMENT.md](BRANCH_REASSESSMENT.md) lesen. Danach nur die einschlägigen aktuellen Runtime-/Subprojekt-/Paket-/Toolingklassen und Tests. Historische Toolingpläne nicht als erneut auszuführenden Auftrag behandeln.
3. Tatsächlichen Main-/Featurebasis-SHA, enthaltenen Tooling-Produkt-SHA und Abstammung ermitteln. Ist bereits Featurecode vorhanden, daran anknüpfen. Keine Implementierung oder bereits ausgeführte Abnahme ohne Nachweis neu behaupten. Lokale uncommittierte Änderungen sind kein gepinnter Teststand. Die historische P8-Abnahme nicht erneut ausführen.
4. Eine kurze `WORK_QUEUE.md` im eigenen Featureordner führen: Phasenstatus, Basis-/Kandidaten-/Review-SHAs, notwendige Testauswahl und konkret offene Arbeit. `TOOLING_HANDOFF.md` knapp beginnen. Keine neue grosse Kriterien-/Artefaktplattform.
5. Fehlenden `apply`-Aufruf als kleine temporäre Loaderprobe nachweisen bzw. inzwischen vorhandene Funktion gegen den Vertrag prüfen. Kleine Einzeljob-/Projektbaseline und reine Opaque-Probe ausführen. Keine gesamte Fachmatrix.
6. Vor grösserer Implementierung den realen autonomen OpenCode-Reviewweg beweisen: frischer Kontext, richtiger Worktree, erlaubte Testausführung, tatsächliches gewünschtes Modell und prüfbares Ergebnis. Ein Versionsaufruf oder Modell-Ping allein genügt nicht.

## 3. Phasen: erst nach erfolgreichem Gate weiter

| Phase | Ergebnis |
|---|---|
| **P0** | Geeignete getrennte Arbeitsbasis, kleine Baseline und tatsächlich funktionierender autonomer Zweitprüfer. |
| **P1** | Statischer Direktivenleser, sichere Source-Snapshots, Includegraph mit Cycle-/Duplicateprüfung und vollständigem Hash. |
| **P2** | Einzeljobs/Subprojekte im SCRIPT-Betrieb, gemeinsame Taskauflösung bei getrennten Bindings, richtige Pfade/Quellen. |
| **P3** | Mitkompilierte Fragmente, versionsklare interne Metadaten und echte Job-JARs ohne Quellcheckout. |
| **P4** | Gezielte Core-/LSP-/GUI-Koexistenzprüfungen auf der integrierten Basis, Beispiel/Dokumentation und Gesamt-Featureabnahme; keine obligatorische erneute Tooling-Mergeprobe. |

Pro Phase: reproduzierende Probe bzw. neuer Vertragstest → minimale Implementierung → notwendige Regression → lokaler Kandidatencommit → **neue unabhängige OpenCode-Session am exakten Commit** → bei Findings korrigieren und erneut unabhängig prüfen. `NACHARBEIT` oder `NICHT_PRUEFBAR` erlauben keine nächste Phase. Nach gültiger Abnahme selbständig fortsetzen, nicht jeweils den Nutzer um Bestätigung bitten.

Bei einer Korrektur nur potenziell betroffene Tests erneut ausführen; der Reviewer bewertet dennoch die gesamte Phase gegen ihre unveränderte Phasebase. Alte Ergebnisse nicht auf eine neue SHA umetikettieren. Der Schlussreview prüft kumulativ das Feature, nicht nur den letzten Dokumentationscommit.

## 4. Runtime-Verträge unverändert umsetzen

**S01–S19 im Plan sind verbindlich.** Besonders:

- `apply from: 'a.groovy'` bzw. `apply(from: 'a.groovy')`, direkter Top-Level-Aufruf und konstanter String. Keine dynamischen/Remote-/Plugin-Includes; kontrollierter AST-/Tokenreader, kein Regexparser oder freies `evaluate`.
- Ein gemeinsamer GRETL-Definitionsscope und ein finales Freeze/Run; pro Script eigene Instanz und eigenes Binding. Keine implizite Weitergabe lokaler Variablen oder Imports. Geteilter Builder allein reicht nicht, wenn TaskRef-/Operationsmaps weiterhin getrennt bleiben.
- Include relativ zur einschliessenden Quelldatei; `file(...)`/`ctx.file(...)` weiterhin relativ zum besitzenden Job/Subprojekt. Start-CWD-Parameter und Workspaces bleiben unverändert.
- Depth-first-Registrierung an der Apply-Stelle, keine automatisch erfundenen Taskabhängigkeiten. Vorwärtsreferenzen und Ergebnis-/Parameterzugriffe funktionieren über Dateigrenzen. Fragmentdefaults sind verboten; dieselbe Quelle in zwei Subprojekten ist erlaubt, doppelt im selben Scope nicht.
- Ganze statische Includeclosure vor Registrierung prüfen. Zyklen, unsichere Pfade, fehlende/nichtreguläre Dateien und Symlinks im Includeweg nachvollziehbar abweisen. Erlaubtes internes `..` nicht mit Rootausbruch verwechseln.
- Einmal erfasste Sourcebytes für Analyse, Compiler und Hash; echte Fragmentdateien/Zeilen in späteren Fehlern. Ohne Includes alte Hash-/Metadatenverträge erhalten.
- Paketieren führt keine Scriptbodies/Tasks aus. Das fertige JAR verwendet nur enthaltene kompilierte Klassen und funktioniert ohne ursprüngliche Groovydateien. Benutzerdeskriptorschema, internes Jobmetadatenschema, Dependencyinventar und LSP-Protokollversion nicht vermischen.
- Reiner Reader und IDE führen nichts aus. Normales Runtime-`check`/`plan`/Jobhilfe-Definitionsladen darf wie bisher vertrauenswürdigen Toplevelcode ausführen, aber keine Taskkonfiguration, Tasks oder Cleanups. Keine allgemeine Sandbox behaupten.

## 5. LSP und Modeler: sichere Koexistenz statt versteckter Scopevergrösserung

Bestehende Sprach-/Graphdelegationen erhalten. Keine Produktionsabhängigkeit der Runtime auf den Tooling-Core und keine Runtime-/Runner-/Taskhandlerabhängigkeit des LSP. Keine neuen Module nur für eine vermeintlich vollständige gemeinsame Architektur.

Der aktuelle Parser behandelt `apply` als opaken deklarationswirksamen Bereich; `ProjectResolver` lädt die Fragmentclosure noch nicht. Der Graph kann `DYNAMIC_CODE` und `PARTIAL` zeigen. Diese vorhandene Sicherheit ist **zu testen**, nicht aus der Codelektüre als bereits qualifiziert auszugeben.

In P4 die Verträge **C01–C08** prüfen:

- Eine ansonsten geschlossene Fixture mit `apply` bleibt partiell. Die Kontrolle ohne `apply` ist vollständig. Ausführungsmarker in einer **anderen** Fixture verwenden, damit sie nicht selbst den vermeintlichen Apply-Coveragenachweis verursachen.
- Rename/Delete/Dependency-/Resultänderungen ohne notwendige vollständige Kenntnis sperren. Gleichwirkende Wege über `SetTaskOption(dependsOn/after)`, Formularbatch und referenzbildende Feldsetter mitprüfen. Bei Defaults unbekannte Ziele nicht verlieren. Nicht jede lokale Description-/Booleanänderung unnötig verbieten.
- Genau eine sichere lokale Änderung bleibt möglich; Includezeile, übrige Bytes und Fragmente bleiben unverändert. Keine Flatten-/Neuschreibelogik.
- Den echten stdio-Server mit `gretl/graph`, `gretl/planEdit` und `gretl/validateEdit` prüfen; stale Plan nach Einstiegs-/Include-Literaländerung ablehnen.
- Einen kleinen tatsächlichen installierten VSIX-Ablauf ausführen: partielle Sicht, lokale Änderung, passende Ablehnung, Save/Undo/Redo, bytegenaue Kontrolle. Tatsächliche IDs: `gretl-runtime`, `gretlRuntime.modeler`, `gretlRuntime.openModeler`.

Wenn ein Guard bereits korrekt arbeitet, nur den Test ergänzen. Bei reproduzierter Lücke minimal im vorhandenen Java-Core-/LSP-/Hostpfad reparieren, unabhängig reviewen; historische Toolingabschlussdateien und fremde Worktrees bleiben geschützt. Keine vollständige statische Includeintegration vorziehen. Dokumentiere insbesondere, dass unerfasste Fragmentänderungen noch keine zugesicherte kontextsensitive LSPinvalidierung besitzen und ein allein geöffnetes Shared-Fragment keinen bewiesenen Einbinderkontext hat.

## 6. Tests: ausreichend, aber gezielt

Die ausdrückliche Testausnahme dieses Nutzerauftrags steht im Plan §1/8. Die alten lokalen Vollgates werden **nicht nach jeder Featurephase** verlangt; `AGENTS.md`, CI und Releasegates bleiben selbst unverändert. Eine Featureabnahme ist keine Releasefreigabe.

Während Entwicklung betroffene Fälle; am Phasengate neue/berührte Featuretests und direkte Regressionen; am Ende die öffentliche SCRIPT-/JAR-Wirkung und kleinen C01–C08-Interaktionen. Keine pauschale 44-Operations-, PostGIS-, FTP-, S3-, GeoTools- oder komplette GUI-/Browsermatrix. Der konkrete P4-GUI-Smoke ist trotzdem Pflicht.

Surefire-/Failsafe-/Node-Einstiege aus der tatsächlichen Konfiguration verwenden. `failIfNoSpecifiedTests=false` nur für unbeteiligte Reactorabhängigkeiten: im Zielmodul frische ausgeführte Fälle prüfen. Keine alten XMLs, versteckten Skips oder Nulltest-PASS. Artefaktbau mit Testskip ist kein Testbeweis.

Gleichnamige eigene `6.0.0-SNAPSHOT`-Artefakte aus parallel laufenden Worktrees nicht mischen. Eigene Mavenrepositorys oder einen belegbar isolierten Reactorpfad nutzen; externe Dependencycaches dürfen geteilt werden. Jeder Prozess-/GUIlauf benennt seine tatsächlichen aktuellen Artefakte. `NOT_EXECUTED` bedeutet fehlenden Pflichtnachweis, `NOT_REQUIRED` eine begründete Nichtbetroffenheit.

## 7. Autonomer Zweitreview ohne falsche Vollgatekopplung

**Wichtiger Codebefund:** `tools/gretl-tooling/review.mjs` bindet den alten Toolingauftrag, 81 Kriterien, beide Vollbuilds und dessen GUImengen fest ein. **Nicht unverändert verwenden und nicht seine Originalverträge abschwächen.** Nur geeignete Start-/Identitäts-/Prozessmechanik wiederverwenden oder einen kleinen Featureadapter unter `tools/script-includes/` schreiben. Kein zweites allgemeines Reviewframework.

Lokal OpenCode-Binarypfad, tatsächliche **2.x**-Version, `run --help`, vorhandene Anmeldung und verfügbare Modell-ID prüfen. Vorhandene Credentials verwenden, **keine neue Pflicht-ENV für den DeepSeek-Key**, keine Authdatei ausgeben. Die offizielle Zuordnung von `deepseek-flash` zu `DeepSeek-V4.1-Flash-1017` am Ausführungstag erneut verifizieren und gegen tatsächliche Sitzungsmetadaten prüfen. Kein stiller Modell-/Anbieterwechsel.

Für jeden Phasenkandidaten:

1. Vor Commit stabilen Reviewrequest mit Scope, Phasebase, Anforderungen und Tests schreiben, noch ohne eigenen künftigen `head_sha`.
2. Lokal committen; vollen Kandidaten-SHA erst danach im externen Handoff erfassen. Auftrag `script-includes`, Spec `1.3`, Requirement-Digest und tatsächlichen Toolingbasisstatus eindeutig nennen.
3. Frischen detached Worktree am Kandidaten und neue OpenCode-Session ohne `--continue`/Sessionwiederverwendung starten. Reviewer liest Plan, unabhängigen Reviewprompt und neutralen Request selbst.
4. Beschränkte V2-Policy erlaubt nötige Reads, gezielte Tests, Builds und eigene Report-/Probeausgaben ohne Nachfragen; verbietet fremde Worktrees, produktive Jobs, Credentialexport und Publikation. Keine globale Benutzerpolicy überschreiben. Shellrechte und getrackten Zustand zusätzlich prüfen.
5. Auf tatsächlichen Prozess-/Sessionabschluss warten, ursprünglichen Bericht/Metadaten/Rohbelege ausserhalb löschbarer Buildordner sichern. OpenCode muss Code **und Zielerreichung** prüfen, notwendige Tests selbst ausführen und eine eigene neue Gegenprobe entwickeln.
6. Urteil maschinell prüfen: richtige Auftrags-/Spec-/SHA-/Session-/Modellidentität, vollständiger Phasenumfang, echte Nachweise, eigene ausgeführte Gegenprobe, keine offenen BLOCKER/MAJOR, keine fälligen fehlenden Tests. Exit 0 oder ein Wort `ABGENOMMEN` genügt nicht.
7. Findings korrigieren, gezielt nachtesten, neu committen und unabhängig erneut prüfen. Keine Selbstfreigabe. Bei gültigem Urteil nächste Phase beginnen.

Bei echtem Infrastrukturblocker die vorhandenen legitimen CLI-/V2-Wege begrenzt erproben. Danach konkrete Ursache und reproduzierbaren Stand melden, `BLOCKED_REVIEW_INFRA` setzen und nicht weitergehen. Manueller Prompttransport ist nicht der vorgesehene Normalweg; ein fehlender autonomer Review wird nicht als erledigt bezeichnet.

## 8. Abschluss

Liefere den Main-/Featureausgangspunkt, den enthaltenen akzeptierten Tooling-Produkt-SHA, den final geprüften Feature-SHA, funktionierendes Beispiel, tatsächliche Test-/Reviewbelege und klar benannte Grenzen. Schlussstatus **`RUNTIME_INCLUDES_WITH_SAFE_PARTIAL_TOOLING`**, nicht vollständige Include-IDE-Unterstützung. Eine tatsächlich fehlende Basisvoraussetzung oder fällige GUIpflichtprüfung bleibt sichtbar und verhindert die Schlussfreigabe. Ein nur aus alten Anweisungen übernommener P8-Wartezustand ist kein gültiger Blocker.

Kein Push, Release oder Merge nach `main`/`codex/gretl-tooling`. Eine lokale Upstreamintegration in den eigenen Featurebranch ist nur bei dem im Plan beschriebenen tatsächlichen Bedarf zulässig; für einen neuen Main-basierten Auftrag nicht eigens erzeugen. Originalreviews und fremde laufende Arbeit nicht verändern.

**Jetzt P0 beginnen:** tatsächliche Basis und Worktreeisolation bestimmen, kleine Baseline prüfen und den autonomen OpenCode-/DeepSeek-Reviewweg mit der richtigen schlanken Feature-Testauswahl beweisen. Danach phasenweise implementieren.
