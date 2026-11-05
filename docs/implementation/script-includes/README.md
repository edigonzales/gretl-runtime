# Scriptfragmente mit `apply from:`

GRETL kann lokale Groovyfragmente in dieselbe Jobdefinition laden:

```groovy
apply from: 'tasks/prepare.groovy'
apply(from: "tasks/verify.groovy")
defaults 'verify'
```

Das [ausführbare Beispiel](../../../examples/script-includes/README.md) komprimiert
synthetische UTF-8-Daten, prüft nach Dekompression jedes Byte und läuft als SCRIPT
oder als vorkompiliertes Job-JAR. Der Einstieg bleibt `gretl run job.groovy`;
auch Check, Plan und Jobhilfe laden denselben Includeabschluss.

## Syntax und Definitionsscope

Erlaubt ist ein direkter Aufruf auf oberster Scriptebene, mit implizitem
Scriptreceiver und genau einem Mapfeld `from`. Dessen Wert ist ein nichtleerer,
konstanter einfach oder doppelt zitierter String. GStrings, Verkettungen,
Variablen, Closures, weitere Schlüssel, Listen, Globs und Remotequellen sind
nicht unterstützt. Ein GRETL-apply in Bedingungen, Schleifen, Methoden oder
Task-/Action-/Cleanupclosures ist ebenfalls ein Definitionsfehler. Ein fremdes
`someObject.apply(...)` wird nicht als Include umgedeutet. Eigene Scriptmethoden
oder Scriptvariablen namens `apply` dürfen den DSL-Namen bei Includejobs nicht
überschatten. Gleichnamige Methoden unabhängiger Helperklassen bleiben möglich.
Reflektion und beliebige Metaprogrammierung bilden keinen unterstützten Includeweg.

Tasks, Actions, Parameter und Cleanup teilen den Definitionsscope des Einstiegs.
In einem Subprojekt `:import` wird `prepare` zu `:import:prepare`; das Fragment
ist kein zusätzliches Subprojekt. Vorwärtsreferenzen auf später registrierte
Tasks funktionieren. `dependsOn`, `after`, `result`, `resultOptional` und
qualifizierte Projektverweise behalten ihre bisherigen Regeln. Insbesondere
verleiht `after` keinen Resultatzugriff und selektiert keine zusätzlichen Tasks.

Die Registrierung erfolgt an der erreichten apply-Stelle, depth-first und in
Quellreihenfolge. Includes erzeugen keine Ablaufkanten. Es gibt einen gemeinsamen
Builder, ein finales Freeze und denselben Runner, Runbericht und Cleanupabschluss.
Der statisch deklarierte Includeabschluss wird trotzdem vollständig vor der
ersten Registrierung gelesen und validiert. Ein Include nach `return` gehört
zum Quellhash, registriert aber nichts, wenn die Ausführung ihn nicht erreicht.

Jede Scriptdatei bekommt eine eigene Instanz und ein eigenes Binding. Imports,
`def`-Variablen, Bindingzuweisungen und TaskRef-Lokalvariablen werden nicht
exportiert. Gemeinsame Werte laufen über GRETL-Parameter und vorhandene
Konfigurationsverträge. Eigene statische Java-/Groovyfelder unterliegen nicht
dieser Bindingisolation.

`defaults` gehört zum Einstieg oder zu `settings.gretl`; in Fragmenten wird es mit
`APPLY_DEFAULTS_NOT_ALLOWED` abgewiesen. Das bisherige Verhalten von Defaults in
echten Subprojekt-Einstiegen bleibt erhalten. Doppelte Task-/Parameter-/Cleanupnamen
werden nicht überschrieben. Dieselbe normalisierte Datei zweimal im selben Scope
ist `APPLY_DUPLICATE`, einschliesslich beider Einbindeketten; Zyklen sind
`APPLY_CYCLE`. Verschiedene Subprojekte dürfen dieselbe physische Fragmentquelle
mit getrennten Instanzen und Bindings verwenden. Echte Projekteinstiege dürfen
nicht zusätzlich als Fragmente importiert werden.

## Zwei Pfadbasen

`apply from:` ist relativ zur einbindenden **Quelldatei**. Aus
`tasks/prepare.groovy` erreicht `apply from: 'local/compress.groovy'` somit
`tasks/local/compress.groovy`.

`file` und `ctx.file` behalten dagegen den **Job-/Subprojektroot** als
Ressourcenbasis: `file('data/input.txt')` in diesem Fragment meint weiterhin die
Daten des besitzenden Jobs. Das gilt auch für Konfiguration, Action, Bedingung,
innere Invocation und Cleanup. `path`-Parameter bleiben Start-CWD-bezogen;
`workPath` bleibt scopebezogen und temporär.

Die Definitionroot ist das Elternverzeichnis des Einzeldateieinstiegs oder der
bisherige Projektroot. `..` darf innerhalb dieser Root normalisieren, etwa von
`import/job.groovy` zu `../shared/tasks.groovy`. Rootausbrüche, absolute/URI-/UNC-/
Laufwerkspfade, Backslashes, Kontrollzeichen, Symlinkkomponenten, fehlende oder
nicht reguläre Dateien werden abgewiesen. Leerzeichen und Unicode sind erlaubt.
Neue Fragmente sind UTF-8 und enden auf `.groovy`. Endliche Grenzen sind 64 Ebenen,
1024 erfasste Quellen, 8 MiB je Fragment und 64 MiB Gesamtbytes bei Includes.
Jobs ohne Includes behalten ihre bisherigen Eingangspfad- und Hashverträge.

## Quellen, Fehler und Pakete

Ein unveränderlicher Snapshot verwendet die einmal erfassten Bytes für
Includeanalyse, Kompilation und Quellhash. Der Hash umfasst kanonische
rootrelative Quellnamen, Bytes aller Einstiege/Fragmente, geordnete Scopes/Kanten
und bei Projekten die Settings. Ein anderer absoluter Checkoutpfad ändert ihn
nicht; eine geänderte verschachtelte Quelle schon. Dies verspricht keinen atomaren
Filesystemsnapshot über mehrere Dateien. Ein neuer Ladevorgang erfasst neue Bytes.
Kompilier-, Registrierungs-, Konfigurations-, Action- und Cleanupfehler nennen die
wirkliche Quelle und Zeile; gleiche Basenames bleiben unterscheidbar.

[Paketdeskriptoren](../job-packaging.md) behalten ihre Schemata 1/2/3. Der Packager
kompiliert den vollständigen Includeabschluss, ohne Scriptbodies zu starten.
`sources` bleibt die Auswahl zusätzlicher Helperquellen; ein nicht referenziertes
zusätzliches Script bleibt ein Fehler. Datenressourcen werden ausdrücklich durch
`resources` ausgewählt. Ein Include nimmt keine benachbarten Daten automatisch auf.

Include-JARs verwenden intern `job.json`-Schema 3, `definitionMode: single|project`
und Bundleversion 1. Validierte Rollen, Klassen, Quellidentitäten und Applykanten
steuern die Bytecoderegistrierung. Zur Laufzeit werden keine Groovyquellen gelesen
oder kompiliert. Alte interne Jobformate 1/2 bleiben lesbar. Dependencyinventar 2,
Operationskatalog und LSP-Protokoll 1 sind davon unabhängig. Das generische IMAGE
führt weiterhin externe Scripts/Projekte aus; es wird kein konkretes Job-JAR ins
Image eingebaut.

Hilfe, Check und Plan führen keine Taskkonfiguration, Tasks oder Cleanups aus.
Vertrauenswürdiger Script-Toplevel kann beim Definitionsladen wie bisher laufen.
Der reine Direktivenleser führt dagegen weder Top-Level-Code noch
Annotationstransformationen aus. JAR-Hilfe/-Plan materialisieren keine Runressourcen.

## Aktuelle IDE-Unterstützung

Der LSP erfasst den statischen Includeabschluss transitiv, verwendet offene Buffer
vor Disk und analysiert Deklarationen anwendungsbezogen in Registrierungsreihenfolge.
Ein vollständig erfasster, sonst statisch vollständiger Includejob erhält einen
vollständigen Graphen mit Originalquellen. Das
[typisierte Toolingbeispiel](../../../examples/include-tooling/README.md) zeigt den
Texteditor-/Modelerweg und eine getrennte tatsächliche Runtimeausführung.

Navigation, Referenzen, Hover und Completion umfassen die Fragmente im gewählten
Scope. Include-Pfadcompletion benutzt begrenzte sicher erfasste Verzeichnislisten;
nicht unterstützte Listen bleiben sichtbar unvollständig. Outline und Ranges
gehören jeweils zur physischen Quelldatei. SQL-/file-Ressourcen kommen weiterhin
vom Besitzerroot, Includes von der enthaltenden Quelle. Der LSP lädt keinen Job,
startet weder Runtime/Check/Plan noch Tasks und führt keine Scriptbodies aus.

Bei genau einem bekannten Besitzer wird dessen Kontext automatisch verwendet.
Bei mehreren Besitzern **GRETL: Select Source Context** wählen. Die Textansicht
besitzt eine bestätigte Auswahl; Modelerpanels halten jeweils ihre eigene Anwendung
mit Entry, Scope und Ressourcenbasis. Navigation aus dem Graphen übernimmt den
gewählten Knotenkontext in die Originaltextansicht. Eine neue, nicht zugeordnete
Groovydatei ist kein impliziter Rootjob; **Use Active File as Runtime Job** registriert
bewusst einen eigenständigen Einstieg. Gewöhnliche Groovy-/Gradledateien behalten
ihre Sprache. Ein bereits dirty geöffnetes Fragment wird ohne Textverlust und ohne
Speichern synchronisiert, bevor der Server einen frischen editierbaren Graphen liefert.

Eindeutige Quellen unterstützen lokale Feld-/Optionsänderungen, Rename über alle
gebundenen Einstiegs-/Fragment-/Settingsstellen, Dependencies, Resultbindings und
Formularbatches. Neue Deklarationen benötigen eine ausdrücklich bestimmte zulässige
Zieldatei; Defaults bleiben im tatsächlichen Entry/Settings. Erforderliche Zielpuffer
vor der Bestätigung öffnen und gegen den aktualisierten Capture neu planen.
Ein nativer WorkspaceEdit erhält Originalranges, Kommentare, CRLF und Unicode.
**Undo text**/**Redo text** bedienen die tatsächlich geänderte Quelldatei;
**Save changed sources** speichert die Zieldateien des letzten Modeleredits.
Layoutspeicherung und Layout-Undo ändern keinen Groovytext.

Shared-Fragmente werden in allen bekannten Anwendungen gelesen, aber nicht
automatisch verändert. Der gesamte Write-Footprint zählt: Auch ein eindeutiger
Task mit einer zu schreibenden Referenz in einer Shareddatei wird abgelehnt.
Unvollständige Ownershipkenntnis ist keine Schreibfreigabe. Ein unabhängiger Edit
in einer bewiesen eindeutigen Datei bleibt möglich; ein eindeutiger Consumer darf
bekannte Shared-Produzenten referenzieren, ohne deren Quelle zu schreiben.
Die Refactoringdomäne umfasst unterstützte Workspace-Einstiege und ausdrücklich
registrierte Jobs; unbekannte externe Konsumenten werden nicht als erfasst behauptet.
Quellhashes, Bufferinkarnationen, Owner-/Kontextgenerationen und eingefrorene
Postimages schützen alle Editwege auch bei neu hinzugefügten ungeöffneten Besitzern.

Dynamischer/opaker Toplevel, Return/Throw, Recovery, defekte Quellen und Limits
bleiben konservativ PARTIAL bzw. diagnostiziert INVALID. Vollständiger statischer
Quellenabschluss beweist keine beliebige Groovy-Kontrollflussausführung.
Nicht beauftragt sind Taskverschiebungen, automatische Fragmentaufteilung,
Dateiumbenennung und globale Sharedrefactorings. Die frühere immer-partielle
IDEgrenze ist der historische Ursprung des
[Runtime-Arbeitsstands](WORK_QUEUE.md); aktuelle Toolingarbeit und Abnahmen stehen
im [Include-Tooling-Arbeitsstand](../include-tooling/WORK_QUEUE.md).

Die [Arbeitsstände und Abnahmen](WORK_QUEUE.md) dokumentieren die lokalen
Featuregates. Sie ersetzen keine Root-, CI- oder Releasequalifikation und keinen
Publikationsauftrag.
