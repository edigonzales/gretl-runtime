# Kumulativer unabhängiger Schlussreview P4 – sichere Include-Edits

Auftrag include-tooling, Spec 1.0. Lies die drei aktiven Auftragsdateien und diesen
Request selbst. Auftragsbasis `9773c42ddb12a7343cf5f9ad7b6be81b489abb30`, feste
P4-Phasebase `4ab062b8436b77eaf6af0cf6303d812ca3c346a6` (P3 abgenommen).
Der Handoff bindet den fertigen Kandidaten nach Commit. **Kumulativ alle IT01–IT24
seit Auftragsbasis beurteilen**, nicht nur den P4-Diff. Frühere Phasenbelege erst
nach Identitäts-/Deltaprüfung wiederverwenden; kein erneuter Runtimeauftrag.

## Neuer Risiko- und Zielumfang

- Echte positive eindeutige Task-/Parameter-Renames über Originaldateien,
  Dependencies, Results, Options-/Feldsetter, Formularbatches und ausdrückliche
  Fragmentinsertionsziele. Defaults nur echter Entry/Settings, auch aus Fragmentpanel.
- EditImpactAnalyzer vorab und endgültiger tatsächlicher Write-Footprint im
  EditVerifier. Keine Writes in Shared/UNKNOWN. Ein eindeutiger Target mit Shared-
  Use bleibt abgelehnt; unabhängige eindeutige Änderungen und eindeutiger Consumer
  mit bekanntem Shared-Produzent bleiben möglich. Strukturierte Ownergründe.
- Vollständig begrenzte Entrydomäne ist getrennt von vollständiger Syntax-/Closure-
  analyse. Ein Entry bleibt nur dann exklusiv beweisbar, wenn jede unvollständige
  Definition ihn nach Root-/Entryregeln nicht konsumieren kann. Unvollständige
  Inventare oder unklare gleichörtliche explizite Definitionen geben nicht frei.
- Virtuelle Postimages erhalten Einbinder-/Kontext-/Source-/Inventardaten ohne IO.
  Bindungs- und Fehlervergleiche unterscheiden Anwendungen; gleiche Code/URI/Range
  in verschiedenen Scopes dürfen neue Fehler nicht kompensieren. Quellenkontexte
  dürfen durch Rohreplacements nicht still gewechselt werden.
- Owner-/Kontextgenerationen, positive/negative Sourceabhängigkeiten und
  Inkarnationen schützen Core, Custom Edit, native Rename und Quick Fix. Native
  Ziele werden vor Neuplanung geöffnet/synchronisiert. Vor und nach asynchroner
  Konvertierung erneut prüfen; ein WorkspaceEdit, kein fs.writeFile-Fallback.
- Modeler-Save/Undo/Redo verwenden tatsächliche Transaktionsziele. Layout bleibt
  getrennt. Navigation trägt den bekannten Kontext in neu geöffnete Fragmente;
  Membership-/Sprachwechsel werden vor Auswahl abgewartet.
- Aktuelle README/Handoff/Extensionanleitung und typisiertes Beispiel unter
  examples/include-tooling, keine rückwirkend geänderten historischen Reports.

## Gezielte Prüfungen und ursprüngliche Belege

Der Handoff nennt die vollständige aktuelle Mavenauswahl: ganze kleine Core-
Parser-/Query-/Graph-/Edit-/Workspacefamilie, StaticSemanticsParityTest, drei LSP-
Unitklassen und reale ServerProcessIT, ApplyIncludeServerIT, IncludeMembershipServerIT,
IncludeSemanticServerIT, IncludeEditServerIT. clean verify mit isoliertem Mavenrepo;
frische XMLs mit >0 Tests und failures/errors/skipped=0 verlangen.

Extra-Commands: npm ci, TypeScript, test:includes (60 bzw. tatsächliche aktuelle
Clienttests), Paketbau/Inventar am Kandidaten. Keine Rootvollgates, Dienst-/GeoTools-
Matrix und keine erneute Loader-/JARmatrix ohne konkreten Anlass. P1-Qualifikation
und unveränderte Runtimeproduktionsbytes sind ausdrücklich frühere Belege;
die gespeicherte native Fixture erhält einen tatsächlichen neuen Runtimeprozess
mit zweimaliger Dekompression und bytegenauem Vergleich.

Lies selbst Originalbilder, native Aktions-/Buffer-/Source-/Versions-/Installations-
belege aus dem Handoff. Ein Mock, Provideraufruf oder Root-stdio-Test ist kein
nativer GUI-PASS. Zwei kleine native Benutzerabläufe nach Plan §7.2 sind Pflicht.

## Eigene Gegenprobe, einschliesslich nativem Benutzerpfad

Entwickle selbst eine neue relevante Interaktionsgegenprobe. Wegen tatsächlicher
Host-/GUIänderungen führe zusätzlich eine eigene kleine **native** Gegenprobe aus.
Der Handoff stellt native-instructions.md und einen mechanischen CUA-Dateigateway
für das isolierte synthetische Code-Testfenster bereit. Du bestimmst Hypothese,
Aktionen und Auswertung; der Orchestrator liefert nur echte native Eingaben und
unbearbeitete AX-/PNG-Beobachtungen. Kein menschlicher Prompttransport, kein
Root-Test als eigene Probe, keine Shell-/CDP-/AppleScript-UI-Synthese.

Dein eigenes externes Probeprogramm über ein echtes Shelltool starten, JSON-
Requests schreiben, passende Responses abwarten, neue sichtbare Zustände lesen
und selbst prüfen. Die native counterprobe benötigt `native_user_path:true` und
die tatsächlich erzeugte native-responses.jsonl als Evidence. Bounded Timeout;
alle eigenen Kinder und die Session vollständig beenden. Wenn der native Weg
nicht funktioniert, ehrlich NOT_EXECUTED/NICHT_PRUEFBAR statt fingierter Abnahme.

## Originalbericht und maschinelle Validierung

Lies tools/include-tooling/review.py validate vor Testbeginn. Jede in
executed_checks/counterprobes genannte command muss wörtlich einem tatsächlich
abgeschlossenen erfolgreichen Shellereignis entsprechen. Maven/npm direkt über
Shell; innere Wrapperkommandos nicht als eigene Shellereignisse ausgeben.
Evidence sind nichtleere reguläre Dateien, keine Verzeichnisse. Rohberichte vor
clean erhalten; tatsächliche innere Negativbefunde und erfolgreiche äussere
Assertions unterscheiden. Unveränderte getrackte Produkt-/Testbytes belegen.

Originale review.md/verdict.json: genaue Handoffidentität, alle IT01–IT24,
Code und Zielerreichung, eigene echte Checks/Gegenproben, verwendete alte Belege,
Findings und required_not_executed. Nur ABGENOMMEN ohne offene BLOCKER/MAJOR und
fehlende Pflichtnachweise erlaubt den Endstatus
STATIC_INCLUDE_AWARE_TOOLING_WITH_GUARDED_SHARED_EDITS. Keine Produktfixes,
Secrets, fremden Worktrees, Publikation oder produktiven Jobs.
