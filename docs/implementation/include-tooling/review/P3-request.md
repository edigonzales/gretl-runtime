# Unabhängiger Review P3 – Include-aware Semantik und Queries

Auftrag include-tooling, Spec 1.0. Lies die drei aktiven Auftragsdateien, diesen
Request und den tatsächlichen Diff selbst. Auftragsbasis
`9773c42ddb12a7343cf5f9ad7b6be81b489abb30`; feste Phasebase
`a9cd26c3e5b89f1d91aa09be49ae628f18e9b980` (P2 unabhängig akzeptiert).
Der externe Handoff nennt den erst nach Commit ermittelten Kandidaten-SHA.

Kriterien im P3-Teilvertrag: IT04, IT05, IT06, IT11–IT16, lesender IT22 und IT23.
Keine vorzeitige Freigabe von P4-Strukturedit-/Footprint-/nativen GUIverträgen.
Prüfe Zielerreichung, reale öffentliche Wege und Regressionen, nicht nur Records.

## Risikotragender Diff

- ProjectAnalyzer bindet depth-first RegistrationEvents, Shared-Aliase pro
  ApplicationId; Symbole/Referenzen/Pfade/Diagnosen tragen Anwendung.
- Effektive Coverage unterdrückt nur bestätigte Includeopazität. Kein COMPLETE
  bei Return/Throw/Recovery, opaker Registrierung oder unvollständiger Closure.
- Entry-/Scopecontainer und Originalanker in Graph/Form/Preview; PlanSemantics
  unverändert. file/SQL benutzen Owner-resourceBase, Include weiterhin Quellbasis.
- QueryContext/Index/Standard-LSP/Nav sind kontextgenau; Shared ohne Auswahl
  wird nicht zufällig aufgelöst. Physische Outline/Folds bleiben lokal verfügbar.
- Begrenzte sichere Pfadlisten sind Teil desselben Captures und der virtuellen
  Postimages. Offene Quotes sind nur lexikalische Completionkontexte. Kein IO in
  Queryklassen; unsupported Listing ist ausdrücklich incomplete. Quotes und
  Escapepräfixe bleiben bei Vorschlägen erhalten.
- Modeler-Panels halten unabhängige Pins, erneuern nur dieselbe Anwendung und
  kennzeichnen Scope/Entry. Editorwahl oder ein anderes Panel ersetzt den Pin
  nicht. Reveal/Definition übernehmen den bekannten Einbinder in die Textansicht.
- Coverage öffnet keine strukturellen Include-Edits: separater Phasenguard.
  Neue Fragment-Skalarwrites mit Shared/UNKNOWN bleiben geschlossen. Bestehende
  lokal bewiesene Entry-Skalarpfade bleiben kompatibel; finale komplette
  Ownership-/Footprintprüfung und positive Mehrdateienedits sind P4.
- Alte ResolvedProject-/Symbol-/Reference-/Diagnostic-Konstruktoren bleiben
  kompatibel; keine Runtimeproduktionsklasse geändert. Runtimeparität ist eine
  echte Testintegration, keine produktive Tooling-Core-Abhängigkeit der Runtime.

## Selbst auszuführende gezielte Prüfungen

Der Handoff enthält den vollständigen tatsächlichen Mavenbefehl mit isoliertem
lokalem Repo: gesamte kleine Corefamilie, StaticSemanticsParityTest, die drei
LSP-Unitklassen und echte ServerProcessIT/ApplyIncludeServerIT/
IncludeMembershipServerIT/IncludeSemanticServerIT; clean verify.

Zusätzliche Commands im Handoff: npm ci, TypeScriptcheck und die betroffene
Clientfamilie (Membership, Pins, Host-/Read-/Editguards, Modelercontroller/-state/
-intents/-resources). Alle originalen XMLs und Nodezahlen prüfen, >0 Tests,
errors/failures/skipped=0. Keine Rootvollgates, Dienstmatrix oder erneute P1-JAR-
Qualifikation ohne konkreten Anlass. Keine GUI-PASSbehauptung: installierte native
Abläufe sind ausdrücklich P4 und hier NOT_EXECUTED, keine Pflichtlücke von P3.

Entwickle und führe selbst eine **neue** relevante Gegenprobe aus, z.B. zwei
Sharedscopes mit Alias-/SQL-/Querykonflikt oder nachträglich verändertem Include.
Keine Implementerprobe als eigene ausgeben. Nutze echte produktive Klassen bzw.
den gebauten stdio-Server, Fixtures/Reports nur extern. Kein Produktfix.

## Abschluss und Format

Lies `tools/include-tooling/review.py` validate vor der Ausführung. Jede in
executed_checks/counterprobes genannte command muss genau ein tatsächlich
abgeschlossenes erfolgreiches Shelltool-Ereignis bezeichnen. Maven/npm direkt
über Shell starten, nicht innere subprocess-Kommandos eines Wrappers als eigene
Shellereignisse behaupten. Evidencearrays enthalten nichtleere reguläre Dateien,
keine Verzeichnisse. Vor einem clean Rohberichte sichern.

Originaler review.md und verdict.json mit vollständiger Kriterienmenge und
Handoffidentität, echten eigenen Tests/Gegenprobe, Findings und ehrlichem Urteil.
Neue autonome Session ohne Continue am exakten SHA. Produkt-/Testbytes vor/nach
prüfen; tatsächlicher Prozess-/Sessionabschluss ist Voraussetzung der Validierung.
P0/P1/P2-Abnahmen dürfen nach Identitätsprüfung als frühere Belege referenziert
werden; P3 als ganze Phase gegen dieselbe Phasebase beurteilen.
