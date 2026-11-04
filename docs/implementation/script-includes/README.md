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

## Aktuelle IDE-Unterstützung: sicher partiell

Der vorhandene LSP löst Includes noch nicht transitiv auf. Der Modeler zeigt
bekannte lokale Tasks und den unbekannten Includeanteil als Codebereich; eine
sonst gültige Quelle bleibt `PARTIAL`. Fehlende oder defekte Fragmentinhalte gelten
damit nicht als validiert. Der LSP startet weder Runtime, Check/Plan noch Tasks.

Eindeutig lokale skalare Feldänderungen bleiben möglich. Änderungen, die vollständige
Deklarations-, Referenz- oder Graphabdeckung voraussetzen, werden serverseitig
abgelehnt: unter anderem Rename, DeleteTask, Abhängigkeitsänderungen, BindResult,
referenzbildende Feldwerte sowie entsprechende generische Optionssetter und
Formularbatches. Defaults dürfen unbekannte vorhandene Ziele nicht beiläufig
entfernen. Gewöhnliche Text- und Dateipfadwerte sind keine Taskreferenzen.
Kommentare, Unicode und Zeilenenden werden bei erlaubten lokalen Edits erhalten;
Layoutspeicherung ändert keinen Groovytext. Aktuelle Source-/Snapshotguards gelten
auch bei geändertem Include-Literal und nativer Undo-/Redo-Bearbeitung.

Nicht zugesichert sind Vollgraph, Include-Pfadcompletion, Navigation oder Rename
über Fragmente, Taskverschiebungen zwischen Dateien oder ein erratener
Einbinderkontext für eine allein geöffnete `.groovy`-Datei. Shared-Fragmente bis zu
einer eigenen kontextsensitiven Integration im Texteditor bearbeiten. Änderungen
vom Resolver nicht erfasster Fragmentdateien besitzen noch keine zugesicherte
kontextsensitive Invalidierung. Der [Tooling-Handoff](TOOLING_HANDOFF.md) trennt
physische Quelle, besitzenden Scope und Ressourcenbasis.

Die [Arbeitsstände und Abnahmen](WORK_QUEUE.md) dokumentieren die lokalen
Featuregates. Sie ersetzen keine Root-, CI- oder Releasequalifikation und keinen
Publikationsauftrag.
