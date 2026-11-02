# GRETL Tooling – Test-, GUI- und Gegenprobenmatrix

**Version 1.0 · 2. November 2026**  
Verbindlich zusammen mit [EXECUTION_PLAN.md](EXECUTION_PLAN.md) und [ARCHITECTURE.md](ARCHITECTURE.md).

Diese Datei legt **zu implementierende und tatsächlich auszuführende** Tests fest. Sie behauptet keine bereits bestandenen Tests. Test-IDs beschreiben Abdeckung, nicht zwingend je eine Java-/TypeScriptklasse. Sinnvolle Parametrisierung ist erwünscht. Ein grüner Stub, Dateiexistenztest oder Screenshot ohne inhaltliches Orakel erfüllt keinen Fall.

## 1. Globale Regeln

Alle Tests benutzen lokale, deterministische Fixtures. Kein produktiver GRETL-Job, kein echter kantonaler DB-/S3-/FTPzugang und kein echtes Secret. Die beiden bestehenden vollständigen Maven-Gates bleiben nach Root-AGENTS verbindlich; diese Matrix ersetzt sie nicht.

Reine Sprachtests führen nicht die Fixtures aus. Runtimeparität darf in **separaten bewusst vertrauenswürdigen Testprozessen** die aktuelle Runtime mit lokalen Fixtures aufrufen. Die Tatsache, dass ein Test die Runtime benutzt, darf keine solche Dependency in die LSP-Produktion einschleusen.

Fixtures tragen klare Erwartung: gültig, syntaktisch beschädigt, semantisch ungültig oder dynamisch nicht vollständig bestimmbar. Für dynamisch nicht bestimmbare Fälle ist ein ehrlicher Partialstatus häufig das richtige Resultat, nicht ein erzwungenes grünes „vollständig gültig“.

Zu jedem Pflichttest: konkretes Orakel, kontrollierter erwarteter Fehlerfall, tatsächliche Run-ID/Produkt-SHA, Toolversionen, Exit, Fehler/Skips. Den Harness mit negativem Canary prüfen. Keine zeitbasierte Erfolgserkennung durch blosses Warten.

## 2. Backend-, Prozess- und Distributionsprüfungen

| ID | Ab Phase | Verantwortungsbereich und Mindestorakel |
|---|---|---|
| T01 | P0 | Katalog aus realem aktuellen Providerbestand: Operation-/Input-/Outputmengen, Typbeschreibung, required, Defaults, erlaubte Werte und Beispiele stimmen mit SPI überein. Zwei Exporte gleicher Quellen ergeben gleiche fachliche Digests. Keine hartcodierte Ewigkeit von 44 Operationen. |
| T02 | P0 | Schema-/Katalog-/Dependency-Negativfälle: falsche Majorversion, doppeltes Symbol, unbekanntes Ergänzungsfeld, falscher Digest und aktive Altdependency werden kontrolliert abgelehnt. Fehlender Katalog ist nicht leerer Erfolg. |
| T03 | P0/P2 | Namens-/Graphvertrag gegen aktuelle Runtime: Include-/Qualifiednamen, Root-/Subprojektbezug, Defaults, Targetauswahl, Datenvorgänger und Reihenfolge stimmen überein. Öffentliche Runtime-API- und Fehlercode-Sentinels erhalten. |
| T04 | P1 | Nichtausführung: Toplevelmarker, @Grab, AST-Test-/Transformationmarker, Settingscode und eigene Provider dürfen bei Parse/Analyse/Query/Graph/Editplanung keine Marker/Verbindungen/Prozesse erzeugen. Runtime-Code nicht automatisch als „Sandbox“ bezeichnen. |
| T05 | P1 | Gültige Syntax: task/action/parameter/defaults/cleanup, Map-first, Namedoptions, mehrere Defaults, Listen/Maps, innere Invocation, TaskRefvariablen und aktuelle Helperkontexte. ASTrollen und echte Spans prüfen. |
| T06 | P1 | Recovery: offene Strings/Closures/Argumente, teilweise geschriebene Operationen/Felder, Fehler ohne Tasks, Kommentare mit Fake-Tasks, Klammern in Strings, slashy/triple/GStrings, gemischte sichere und opake Bereiche. Probleme nicht verlieren oder doppeln. |
| T07 | P1 | Positionsvertrag: LF/CRLF/BOM, leere Datei, letztes Zeichen, Emoji vor Cursor, Tabs, wiederholte Feldnamen, Quotes/Escapes. Parserkoordinaten ↔ UTF-16 roundtrip. |
| T08 | P2 | Snapshot-/Concurrencytests mit deterministischen Barrieren: N-Analyse verspätet nach N+1, Close/Reopen, Buffer über Disk, Katalog-/Settingswechsel, Rootentfernung. Kein Mischstand und kein alter veröffentlichter Edit. |
| T09 | P2 | Projekte: mehrere unabhängige Roots mit gleichen Tasknamen; verschachtelte Includes; qualifizierte Referenzen; fehlende/unsafe Einträge; ungeöffnete Jobs plus Dirtysettings; Einzeljobdefaults gegenüber Projektdefaults. |
| T10 | P2 | Graph: vollständiger dependsOn-Zyklus ausserhalb Targets, targetabhängiger after-Zyklus, unbekannte after-Referenz, echte transitive Datenkette, stabile Auswahlreihenfolge. Partials erhalten Annahmen. |
| T11 | P2 | Resultate: bekannter Producer/Output, falscher Outputname, nur after statt dependsOn, transitive erlaubte Kette, resultOptional ohne Ausnahmeprivileg, Action ohne bekanntes Resultschema, innere Invocation ohne falschen Top-Level-Task. |
| T12 | P2 | Parameter/Scope/Felder: fehlende Beschreibung, doppelte Parameter, Toplevellesen, required+default, falsches Literal, Reservedfeld, lokale Variable versus Konfigurationsfeld, dynamische Konfiguration ohne falsche Pflichtfehlermeldung. |
| T13 | P2 | SQL-/Pfadbezug: zwei gleichnamige sql/update.sql in verschiedenen Subprojekten, ungespeichertes SQL, fehlende Parametermap, tatsächliche Runtimeplatzhalterregeln, dynamische Map, Filewatcherinvalidierung, Budget/Symlink-/Lesepolicy. Keine DBverbindung. |
| T14 | P3 | Queries: konkrete Completionlabels/Ersetzungsspans, Kontextfilter, Field-/Parameter-/Resultvorschläge, Hover/Signaturen mit tatsächlicher Runtime-DSL, Outline/Folding/Filelinks aus Snapshot. |
| T15 | P3 | Navigation/Rename: Definition und alle belegten Referenzen inkl. Settings, defaults, after, resultOptional; Unicode-/Quoteerhaltung; readonly/ambiguous/dynamische Coverage blockiert. Keine Textsuche in Kommentaren. |
| T16 | P3 | Alle Editintents wirklich anwenden, danach neu parsen/binden und erwartetes Semantikdelta prüfen. Nicht editierte Bereiche bleiben exakt; No-op bleibt ohne Änderung; CRLF/Kommentare/opaker Code erhalten. |
| T17 | P3 | Negative Editfälle: Mehrfachzuweisung, unsicherer Receiver, beschädigter Zielspan, stale Hash/Version/Settings/Katalog, überlappende Edits, Delete mit Referenz, Ergebnisentzug durch Disconnect. Nicht nur „Plan existiert“ testen. |
| T18 | P4 | Echtes Serverartefakt via stdin/stdout: JSON-RPC framing, Initializecapabilities, Completion und Customrequests. Kein Direktklassenaufruf als Ersatz. Stdout ohne Banner/ANSI/Logs. |
| T19 | P4 | Prozesslifecycle: shutdown+exit bei weiter offenem stdin beendet; exit ohne shutdown; EOF; falsche Argumente; eigene Executor/Kinder beendet; keine globalen Prozesskills. |
| T20 | P4 | LSP-Sync: mehrere Änderungen je Notification, Full/Incremental, UTF-16-Ranges, Dirty SQL/Settings, invalid versions, close/reopen, Filewatcher-/Folderänderungen. Versionierte Diagnosen. |
| T21 | P4 | LSP-Cancellation/Races: blockierte Analyse wird überholt/abgebrochen, Anfrage vor/nach Change, Rename- und PlanEdits auf veraltetem Snapshot, Restartinstanz, Client ohne einzelne Capabilities. |
| T22 | P4 | Ausgepackter Server ohne Altcheckouts, Runner/Taskmodule/Gradle in Productionclasspath; Archivinhalt und resolved dependency tree prüfen. Falscher Katalog-/Schemapin führt zu verständlichem Fehler. |
| T23 | P5 | Client/Manifest/Selector: tatsächliche Extension-ID, job.groovy/settings.gretl, bewusst andere Jobnamen, fremde Groovyprojekte unverändert, genau ein passender Server und kontrollierter Shutdown. |
| T24 | P5 | Host: Java-/Bundlefehler, Trust, konfigurierter Pfad mit Leerzeichen/Unicode, Shellmetazeichen als ungefährliche Argumente, Serverrestart und native Edit-/Undoanwendung. |
| T25 | P3/P6 | Graphprojektion (Core ab P3, sichtbarer Consumer ab P6): Nodes/Kanten/Labels/Sourceanker und Coverage aus Core; Actions/inner calls korrekt; keine Datenkante aus reinem after; keine Exekutionsstatussimulation. |
| T26 | P6 | Neutraler UIadapter/ELKworker: echte Knotengeometrie, Layoutpersistenz, mehrere Panels, alte Workerantwort, Theme-/Longlabel-/leere Ansichten, view-only Drag. Tests zusätzlich zum Workbench-GUI. |
| T27 | P7 | Forms/Intents: alle unterstützten Shapetypen, Integerpräzision, richtige Wertmodi, Draft/Cancel, Pflichtfelder, Metadatenausfall und Coreablehnung. TS erzeugt keine eigene GRETL-Semantik. |
| T28 | P7 | Durchgehender Editpfad: Message→LSP→Coreplan→native Textänderung→didChange→neuer Graph; stale Antwort, schneller Textedit, zwei Panels, Multi-File, abgebrochene Form. |
| T29 | P8 | Sauberer Build/VSIX/Offlinenutzung: echte mitgelieferte Dateien, Digests/Lizenzen, keine CDN-/Installationsnachladung oder Siblingabhängigkeit, Java25-Voraussetzung verständlich. |
| T30 | P8 | Messbare Ressourcen: kleine/mittlere/grosse und kaputte Fixtures, begrenzte Queues/Snapshots/Worker/Dateilesen, Close/Rootremove/Restart. Dokumentierte Messbedingungen, keine erfundenen Zeiten. |
| T31 | P7 | Der aus dem echten GUIworkflow gespeicherte Groovytext wird **unverändert** als Runtime-SCRIPT gegen lokalen HTTP-/ZIP-Fixture gestartet. Inputhash, Parameter und Ergebnisinhalt beweisen Identität. |
| T32 | P8 | Harnesscanaries: falscher Serverhash, unterschlagener erwarteter Node, unterdrückte Textänderung oder falsches Groovyresultat lässt die zuständige Prüfung wirklich scheitern. Nur Scratchkopien. |
| T33 | P0/P8 | Reviewgate: falsche Phase/SHA/ACmenge/Requirementsdigest, laufender Prozess, fehlende Gegenprobe/GUIbelege, offenes Finding, eigener vorab erzeugter Report, geänderter tracked Tree und Providerwechsel verhindern PASS. |
| T34 | P0/P8 | Dokument-/Schemadrift: lokale Links, vorgeschlagene gegenüber vorhandenen Commands, ACregistry gleich Plantabellen, keine aktiven Verweise auf entfernte historische Specs, aktuelle Installationsschritte. |

## 3. Konkret vorgeschriebene Regressionen

### 3.1 Fehler ohne einzigen Task

```groovy
def quelle =
```

Erwartet: konkrete Syntaxdiagnose auch dann, wenn Recovery keine Tasks findet. Kein leeres fehlerfreies Script. Derselbe Test über reinen Parser, Analyzer und später über stdio. Eine zusätzliche Variante enthält nach der defekten Zeile einen intakten Task: sichere Information und Fehler bleiben beide erhalten.

### 3.2 Kommentar/String mit scheinbarer Registrierung

```groovy
// task('phantom', HttpDownload) { output = '}' }
action('real') {
    def help = "tasks.register('notReal', SqlExecutor) { }"
    def text = 'Ein } ist hier nur Text'
}
defaults 'real'
```

Erwartet: genau eine echte Action, keine Phantomtasks, korrekte Abschlussbereiche. Weitere Fixtures mit triple strings, slashy-/dollar-slashy-Text, Escapes und GStrings. Diese Texte werden niemals ausgeführt.

### 3.3 Aktueller Positionsparameter und Headerdependency

```groovy
task('a', HttpDownload) {
    url = parameter('quelle')
    output = workPath('daten.zip')
}
task('b', Extract, dependsOn: 'a') {
    archive = result('a').file
    compression = 'ZIP'
    outputDirectory = parameter('ziel')
}
defaults 'a', 'b'
```

Die Parameterdeklarationen werden in der vollständigen gültigen Fixture ergänzt. Parsererwartung: Typen HttpDownload/Extract, Headerkante a→b und **zwei** Defaults. Kein Legacy-DSL-Hinweis auf die Zuweisungen. Kein Importzwang für registrierte Symbole.

### 3.4 after ist keine Datenauswahl

Reine Graphfixture: `a after b`, `b after a`, beide ohne dependsOn. Auswahl nur `a` hat keinen aktiven Mischzyklus, Auswahl `a,b` schon. `result('b')` in a ist trotz after nicht erlaubt. Zusätzlicher dependsOn-Zyklus in nicht ausgewählten Tasks bleibt ein Definitionsproblem.

Erwartung mit aktuellem Runtime-`ExecutionPlanner` beziehungsweise dessen neutraler Extraktion abgleichen. Keine UIregel, die jeden sichtbaren after-Zyklus als generell unausführbar erklärt.

### 3.5 Quote-Quick-Fix und wiederholte Namen

Ein unbekanntes Dependencyliteral mit Tippfehler in syntaktisch vollständigem und in recovertem Job wird korrigiert. Nach Anwendung stehen genau die passenden ursprünglichen Quotezeichen um den korrigierten Namen. Varianten mit einfachen/doppelten Quotes, Escapes, zweimal gleichem Wort an anderen Stellen und CRLF.

Erwartet: der tatsächliche neue Text wird wieder geparst; kein `''name''`, keine Änderung am ersten beliebigen `indexOf`-Vorkommen. AST- und Recoverypfad erfüllen denselben Sourcebereichsvertrag.

### 3.6 Dynamischer Bereich ist nicht Löschfreigabe

```groovy
['a', 'b'].each { name ->
    action(name) { }
}
action('summary', dependsOn: 'a') { }
defaults 'summary'
```

Erwartet: dynamische Registrierung erkennbar/als opak markiert; keine Behauptung, a existiere sicher nicht. Kein sicherheitswidriges Rename/Delete aufgrund einer vermeintlich vollständigen Taskliste. Das feststehende `summary` darf angezeigt und zu seiner Quelle navigiert werden.

### 3.7 Lokale Variable und komplexe Konfiguration

```groovy
task('download', HttpDownload) {
    def targetName = 'daten.zip'
    url = parameter('quelle')
    output = workPath(targetName)
}
```

Erwartet: `targetName` ist keine unbekannte Operationseigenschaft. `output` kann als komplexer Wert erhalten bleiben. Ein sicher isolierter `url`-Edit darf möglich bleiben, sofern dessen Referenz-/Spanvertrag bewiesen ist. Ein Modeler darf `output` nicht still in `workPath('targetName')` umschreiben.

### 3.8 Projekt und Scriptbasis

```text
settings.gretl:
  include 'prepare', 'checks:local', 'publish'
  defaults ':publish:run'
prepare/job.groovy
prepare/sql/update.sql
publish/job.groovy
publish/sql/update.sql
```

Erwartet: lokale Ressourcen haben je ihre Scriptbasis. Root-/Subprojektparameter und qualifizierte Referenzen gemäss aktueller Runtime. Dirtysettings vor Disk. Zwei eigenständige gleich aufgebaute Projekte in zwei Workspacefolders dürfen ihre `run`-Tasks nicht vermischen.

### 3.9 Semantischer Edit ist nicht Layout

Node-Drag und Fit dürfen Textversion, Sourcehash und Dirtyzustand nicht verändern. Eine neue semantische Verbindung muss dagegen genau die erwartete Header-/ggf. Feldänderung bewirken. Undo der Verbindung stellt den Text wieder her; View-Undo stellt die Position wieder her. Die UI muss diese Unterschiede sichtbar konsistent behandeln.

### 3.10 Exakter GUI-zu-Runtime-Nachweis

Der GUIworkflow erzeugt einen Job mit Parametern `quelle` (text) und `ziel` (path), HttpDownload, Extract, Resultbindung und Default `entpacken`. Ein Testprozess stellt lokal ein ZIP mit bekanntem Inhalt bereit. Der gespeicherte **tatsächliche** Job wird mit der entpackten aktuellen Runtime und echten Parametern gestartet, nicht durch ein handgeschriebenes „äquivalentes“ Testscript ersetzt.

Der Test prüft vor Start SHA-256 des GUIartefakts, übergibt nur lokale temporäre Pfade/Loopback-URL und kontrolliert Exit, entpackten Inhalt und Cleanup. Artefakt/Hash/Runtime-SHA zusammen belegen. Keine GUI-„Run“-Funktion ist dafür notwendig oder beauftragt.

## 4. Installierte GUI-Szenarien

**Pflichtumgebung:** tatsächliche Primärplattform mit frischem isoliertem Profil, installierter eigener VSIX, echtem mitgeliefertem Java-LSP und echten lokalen Fixtures. Ab P5 prüft B selbst mindestens einen neuen risikorelevanten GUIgegenfall; P8 zwei übergreifende neue Gegenfälle. Alle regulären Fälle gelten zusätzlich nach ihrer Phase.

| ID | Ab Phase | Echte Benutzeraktionen | Pflichtorakel |
|---|---|---|---|
| G01 | P5 | VSIX installieren, Workspace öffnen, job.groovy anklicken. | Richtige Extension-ID/Version, genau eigener Server aus Bundle, keine zufällige alte Extension. |
| G02 | P5 | Gültigen Job tippen, an Operation/Feld/Enum/Parameterposition Suggestion öffnen und übernehmen. | Passende Vorschläge sichtbar; korrekter Text und Cursor; kein Gradlesnippet. |
| G03 | P5 | Über Tasktyp, Konfigurationsfeld und result-Ausgabe hovern; Signatur beim Tippen anzeigen. | Tatsächliche Dokumentation und Runtime-DSL, keine Importsuggestion für Built-in. |
| G04 | P5 | Tippfehler in Task-/Outputreferenz erzeugen, Problems öffnen und Stelle anklicken. | Richtige URI/Range/Code/Version, DE/EN-Anzeige; keine verlorene Diagnose ohne Tasks. |
| G05 | P5 | F12/Referenzen von dependsOn/result/Parameter und qualifiziertem Subprojektziel. | Richtige Deklaration/Referenzmenge, keine Auflösung in anderes Workspaceprojekt. |
| G06 | P5 | Tasknamen per Rename ändern, Ergebnisstellen prüfen, Undo/Redo. | Alle sicheren Referenzen ändern sich zusammen; Kommentare bleiben; Nativeundo. |
| G07 | P5 | Quick Fix auf recovertem Dependencyfehler anwenden, reparierten Job anzeigen. | Genau richtige Quotes/Position, kein Doppelquote; neuer Text tatsächlich geparst. |
| G08 | P5 | Outline öffnen; SQL/Settings ungespeichert ändern; zurück zum Job. | Aktuelle Hierarchie/Diagnosen verwenden Dirtybuffer und richtige Scriptbasis. |
| G09 | P5 | Server bewusst neu starten, danach erneut Completion/Rename; Dirtyjob bleibt offen. | Kein Datenverlust, neue Serverinstanz, alte Antwort verworfen, kein zweiter Dauerprozess. |
| G10 | P5 | Fehlenden Javapfad/Bundle konfigurieren, danach wieder gültigen; untrusted Projekt öffnen. | Verständlicher Zustand; keine verdeckte Nachinstallation/Jobausführung oder Änderung normaler Nutzereinstellungen. |
| G11 | P6 | „Modeler öffnen“ beim aktuellen Job, dann anderen Texteditor fokussieren. | Richtiger angehefteter Quellbezug, echte Webview, kein Fokusdiebstahl/stiller Rootwechsel. |
| G12 | P6 | Taskgraph mit Actions, Subprojekten und verschiedenen Kanten betrachten. | Fachlich korrekte Nodes/Kanten/Labels, Partial-/Cleanup-/Parameterdarstellung nicht verfälscht. |
| G13 | P6 | Knoten per Maus ziehen, Pan/Zoom/Fit, einfache Filter bedienen. | Sichtbare Geometrieänderung; Sourcehash/Version/Dirty unverändert; kein Layoutdruck im Groovytext. |
| G14 | P6 | Knoten/Ergebnisbindung anklicken, Quellstelle öffnen; im Text auswählen und im Diagramm zeigen. | Exakte aktuelle Navigation ohne Rückkopplung/Falschauswahl bei gleichen Namen. |
| G15 | P6 | Syntaxfehler/Dirtyedit während laufendem Layout, Panel schliessen/erneut öffnen. | Stale/Partial korrekt, keine obsolete Mutation, passende erhaltene Viewpositionen. |
| G16 | P6 | Light/Dark/HighContrast, langer Name, leerer Graph, zwei Panels. | Lesbare Controls, keine abgeschnittenen Kerninhalte, unabhängiger Viewstate, sauberes Cleanup. |
| G17 | P7 | Über VS Code eine leere job.groovy anlegen/speichern und öffnen; Parameter, HttpDownload und Extract über Palette/Formulare erstellen. | Tatsächlicher gültiger Groovytext entsteht durch Corepläne; Katalogfelder statt hardcodiertem Demoeditor. |
| G18 | P7 | Text/Enum/Boolean/Liste/Map/Integer und Datei-/Parameter-/WorkPath-Modus ändern; Entwurf abbrechen. | Präziser Wert, kein JS-Zahlrundungsfehler; Cancel verändert nichts; nicht belegte Formen ehrlich gesperrt. |
| G19 | P7 | Dependency mit Pointer ziehen; alternativ after wählen; ungültige Verbindung versuchen. | Consumerheader richtig; after nicht zu dependsOn; Ablehnung ohne Textänderung. |
| G20 | P7 | download.file mit Extract.archive verbinden und Default entpacken setzen. | Ergebnisfeld und nötige Datenkante zusammen; vollständiger Job mit richtigen Defaults. |
| G21 | P7 | Referenzierten Task umbenennen; benutzten Task löschen; nach erlaubter Bereinigung unbenutzten löschen. | Rename vollständig, unzulässiges Delete abgelehnt, erlaubtes Delete eng begrenzt. |
| G22 | P7 | Kante entfernen, deren Ergebnis noch verwendet wird; Fall mit verbleibender transitiver Kette vergleichen. | Erster Fall blockiert, zweiter entsprechend Corevertrag zulässig; keine unsichtbare Referenzbeschädigung. |
| G23 | P7 | Job mit komplexer Action/GString/Kommentar öffnen; ein anderes einfaches Feld grafisch ändern. | Opake Bytes bleiben gleich; Erklärung und „Im Quelltext bearbeiten“ statt verlustreicher Konversion. |
| G24 | P7 | Mehrere semantische Edits, Textundo/redo, danach Node-Drag und Viewundo. | Native Textzustände und Ansichtsverlauf getrennt; pro fachlicher Bestätigung zusammengehöriger Edit. |
| G25 | P7 | Graphjob speichern, schliessen, erneut textuell/grafisch öffnen; erzeugte Datei an Fixturetest übergeben. | Vollständiger Roundtrip; Hash des gespeicherten Artefakts entspricht T31-Input; kein anderes Testscript. |
| G26 | P8 | Zwei unabhängige Roots mit gleichen Tasknamen, rasches Umschalten/Ändern und qualifizierte Referenz. | Keine Crossproject-Auflösung; korrekte Versionen und Query-/Graphzuordnung. |
| G27 | P8 | Form offen lassen, Text/Settings extern bzw. im Editor ändern, alten Entwurf anwenden. | Bekannter Konflikt führt zu Ablehnung/Neuplanung; kein stilles Überschreiben oder partieller Erfolg. |
| G28 | P8 | Server während Modelerarbeit beenden; neu starten; readonly-Datei und falschen Katalog testen. | Dirtytext bleibt; stale Intents ungültig; verständliche Readonly-/Mismatchzustände. |
| G29 | P8 | Isoliertes Paket ohne Altcheckouts/Internet benutzen; grosse Fixture öffnen und Panels schliessen. | Lokale Assets/Server vorhanden; responsive begrenzte Verarbeitung, keine ungebremsten Worker/Lecks. |
| G30 | P8 | Neues zusammengesetztes Reviewerbeispiel: Dirtyedit, Resultbindung, Layout, Undo, Restart und Wiederöffnen. | Durchgehender Source-/Snapshot-/Viewvertrag; tatsächliche Bilder, Source- und Prozessbelege. |

P5 führt G01–G10 aus. P6 ergänzt G11–G16 und einen aktuellen Editorsmoke. P7 ergänzt G17–G25 und relevante Graph-/Editorsmokes. P8 führt G01–G30 vollständig auf der Primärplattform aus. Der Reviewer muss nicht jeden langen GUIlauf doppeln, prüft aber alle fälligen aktuellen Belege und führt selbst neue relevante echte GUIvarianten aus.

## 5. Eigene Gegenproben des Reviewers

Die Beispiele sind Anregungen, kein Ersatz für mindestens eine **selbst entwickelte** neue Gegenprobe je Phase. B darf eine Variante nicht als neu ausgeben, wenn sie nur ein identischer bereits vorhandener Test ist.

| Phase | Geeigneter eigener Gegenfall |
|---|---|
| P0 | Zusätzliche lokale Testoperation mit ungewöhnlichem Output und Default; Katalog-/Gatefälschung im Scratchverzeichnis. |
| P1 | Unvollständiger GString mit Kommentar, Emoji und scheinbarem Tasktext; keine Sourceausführung und noch richtige sichere Insel. |
| P2 | after-Mischzyklus nur für eines von zwei Targets; Dirty-SQL in einem von zwei gleichnamigen Subprojekten. |
| P3 | Mehrfachzuweisung neben komplexem Ausdruck; sichere Änderung an anderem Feld; Quote-/CRLF-/Readonly-Renamegrenze. |
| P4 | Stdio-Rename während kontrolliert verzögerter Analyse und Settingschange; Exit bei offenem stdin. |
| P5 | Tatsächliche GUI in Unicode-/Leerzeichenworkspace, ungespeicherter Job, Restart und anschliessender Quick Fix. |
| P6 | Zwei Panels mit gleicher Taskbeschriftung in verschiedenen Projekten, verspätete Layoutantwort nach Rootwechsel. |
| P7 | Resultbindung über vorhandene transitive Kette, anschliessendes Disconnect/Rename neben opaker Action. |
| P8 | Neue Crossfeature-GUIkombination plus absichtlich falsches Bundle-/Schema-/Evidenceartefakt; Originalkandidat unverändert. |

## 6. Nachweis, dass die Tests Fehler erkennen

Mindestens für Parser-/Edit-/Wire-/GUI-/Reviewgate je eine kontrollierte negative Probe. Beispiele: Syntaxdiagnose unterdrücken, Quotes falsch ersetzen, falschen Server starten, erwartete Änderung auslassen, einen erforderlichen ACeintrag entfernen. Nur in separat benannter Scratchkopie/Fixture, nie im Reviewkandidaten.

Ein negativer Prozess muss vom Harness als erwarteter Fehlschlag erkannt werden. Ein Test, der ungeachtet fehlendem Knoten/fehlender Datei grün bleibt, ist selbst ein Muss-Finding. Screenshots ohne Bildinhaltprüfung und zusammenfassende Testzahlen ohne Fall-/Runidentität reichen nicht.

## 7. Plattform- und Umfangsehrlichkeit

Pflicht: Primärplattform mit realer installierter VS-Code-GUI; zusätzlich echter Linux-Start der ausgelieferten Java-LSP-Distribution, zum Beispiel in einer kontrollierten lokalen Containerumgebung. Root-Backing-Servicegates benötigen ohnehin die dokumentierte Dienstumgebung.

Andere macOS-/Windows-/Linuxarchitekturen und echte VS-Code-Remotehosts nach Möglichkeit prüfen und Ergebnisse getrennt ausweisen. Kein verpflichtender Kauf/Cloudaccount/entfernter Maschinenzugriff und kein pauschaler Testdreiklang aus dem INTERLIS-Beispiel. Nicht getestete Plattformen dürfen nicht als freigegeben beworben werden. Java25 als tatsächliche Voraussetzung klar kommunizieren.
