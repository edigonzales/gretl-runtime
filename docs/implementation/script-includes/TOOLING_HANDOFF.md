# Include-/Tooling-Handoff

Der Lieferumfang ist Runtime-Includes mit sicherer partieller Toolingsicht.
Der bestehende statische Parser behandelt normales `apply from:` weiterhin als
opaken deklarationswirksamen Code. Die sonst geschlossene Kontrollfixture ist
COMPLETE; mit apply werden Coverage und Graph PARTIAL und der Aufruf bleibt eine
DYNAMIC_CODE-Insel. Defekte oder fehlende Fragmentquellen sind damit nicht validiert.

`ApplyEditCompatibilityTest` reproduzierte Umwege über SetTaskOption(dependsOn/after),
ConfigureTask-Batches und ResultFieldRef-SetField. Die eng begrenzte Korrektur in
`EditabilityAnalyzer` klassifiziert diese Wirkungen wie die bestehenden Graphintents;
rekursiv eingeschlossene Referenzwerte unterliegen demselben Coverageguard.
Gewöhnliche skalare Werte und Datei-/Workspacepfade erhalten keine pauschale
Schreibsperre. Defaults behalten die bestehende Coverage-/Zuständigkeitsprüfung.
Der reale stdio-Test `ApplyIncludeServerIT` prüft Graph, Editentscheidungen,
Nichtausführung und stale Plans nach geändertem Include-Literal. Protokollversion 1
und vorhandene Source-/Snapshot-/Serveridentitäten bleiben erhalten.

Der bestehende Resolver erfasst **keine transitive Fragmentclosure**. Änderungen
nicht erfasster Fragmente besitzen deshalb noch keine zugesicherte kontextsensitive
Invalidierung. Ein allein geöffnetes Shared-Fragment hat keinen bewiesenen
Einbinderkontext. Vollständige Auflösung, Navigation und Mehrdateien-Rename sind
nicht implementiert. Shared-Fragmente im Texteditor bearbeiten.

Für eine spätere vollständige Integration getrennt behandeln:

- Physische Source-ID: kanonischer Pfad relativ zur Definitionroot; virtuelle
  Groovynamen sind injectiv aus UTF-8-Pfadbytes abgeleitet, keine Scope-ID.
- Besitzender Definitionsscope: Einzeljob oder bestehender ProjectPath. Dieselbe
  physische Quelle kann mehrere Scopes mit getrennten Bindings besitzen.
- Include-Basis: die einbindende Quelldatei. Ressourcenbasis von file/ctx.file:
  weiterhin der besitzende Job-/Subprojektroot. Pathparameter: Start-CWD.
- Offene Buffer: vor dem Diskstand verwenden, mit kontextabhängigen Quellvektoren
  und Invalidierung aller betroffenen Einbinder. Kein erratener Einbinder.
- Beliebiger Kontrollfluss: statisch deklarierter Abschluss und tatsächlich
  erreichte Registrierung sind verschieden; konservative Coverage erhalten.

Der Runtime-Reader liefert UTF-16-Bereiche (halb offen), einbasierte Positionen und
Site-IDs. Er bleibt getrennt von Filesystemzugriff und Registrierung. Seine
Ablage in `gretl-groovy` erlaubt keinen Runtimeimport in den Tooling-Core/LSP.
Keine GroovyJobLoader-, Script.run-, Check/Plan-/Handlerausführung im Tooling und
keine zweite Namens- oder Graphsemantik. Neutrale Werte bei einer späteren
Integration gezielt adaptieren, statt eine allgemeine Workspaceengine nachzuliefern.

Abnahmen und noch laufende Nachweise stehen im [Arbeitsstand](WORK_QUEUE.md).
Die neue [Featureanleitung](README.md) beschreibt die Grenzen auch für Anwender.
