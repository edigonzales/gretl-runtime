# Include-tooling: Arbeitsstand

**Stand: P0–P3 REVIEWED_ACCEPTED; P4 implementiert/testet, native Abnahme und kumulativer Review ausstehend.**
P0-Kandidat `d2dccba942a749a3f270a7dbfbdb11ffe2814a1a` wurde unabhängig
abgenommen und nach vollständigem Prozess-/Sessionabschluss maschinell validiert.
P1-Kandidat `fb4664de265d06e3145d2757c1d1aed853d25da1` wurde ohne Findings
unabhängig abgenommen und nach Prozess-/Sessionabschluss maschinell validiert.
P2-Korrektur `a9cd26c3e5b89f1d91aa09be49ae628f18e9b980` ist unabhängig abgenommen und maschinell validiert. P3 wurde danach unabhängig abgenommen; P4 hat nach validiertem Sessionabschluss begonnen.

Basis: `9773c42ddb12a7343cf5f9ad7b6be81b489abb30`, sauberer Main und
`origin/main` (Remote bei Start geprüft). Branch `codex/include-tooling`, eigener
Worktree `/Users/stefan/sources/gretl-runtime-include-tooling`. Recherchepin
`ae0a2630156280e0e7f6bebf2354884f555d8d7e`: nur zwei Dokumentationscommits
seither (Include-Pfade und dieser neue Auftrag), keine Produktdeltas.
Andere Worktrees bleiben unverändert.

## Phasen und nächste Arbeit

- P0: Baseline und autonomer unabhängiger Reviewweg abgenommen (W01–W04).
- P1 (abgenommen): neutrale Include-AST-Inspektion im neuen `gretl-script-syntax`, kompatible
  Runtimefassade, Toolingadapter, Anwendungen und Reihenfolge. Reader-, SafeParser-,
  Loaderfamilie und kleiner echter SCRIPT/JAR-/Paketnachweis.
- P2 (abgenommen): Closure, Sourcecapture, Ownerindex, Context-/Membershipvertrag und Client.
  Resolver-/Workspace-/SQLbasis-/Dirty-/Owner-/stdio-/Hosttests.
- P3: Semantik, originale Navigation, Completion, vollständiger statischer Graph,
  Contextwahl. Query-/Alias-/Graph-/Preview-/stdio- und Runtimeplanparität;
  strukturelle Include-Edits bis P4 separat gesperrt.
- P4: Footprint-/Owner-/Postimageguards, positive Mehrdateien-Edits und Rename,
  Shared-/Stalefälle, installierter nativer VSIX-Weg, gespeicherte Runtimefixture,
  aktuelle Anleitung, kumulativer Review IT01–IT24.

Architektur: `ApplyDirectiveReader` besitzt derzeit die komplexe Erkennung;
`SafeGroovySyntaxReader` bleibt die sichere Tooling-Parsehülle. Nur den
konvertierten AST gemeinsam inspizieren. `ProjectResolver.jobs()` bleibt eine
Liste echter Einstiege; physische Quellen und logische Anwendungen getrennt.
Owner-Ressourcenbasis und Include-Quellbasis ausdrücklich unterscheiden.
Bestehende PlanSemantics und virtuelle Postimages weiterverwenden.

## P0-Belege und Toolchain

JDK Temurin 25.0.2, Mavenwrapper 3.10.0, Node 22.23.1. PATH-OpenCode ist 1.18.22;
verwendet wird die vorhandene isolierte CLI 2.2.5 unter
`/Users/stefan/.cache/gretl-script-includes/opencode-v2/node_modules/.bin/opencode`.
Gespeicherte DeepSeek-Anmeldung und `deepseek/deepseek-flash` vorhanden;
offizielle Modellzuordnung vor jeder Reviewausführung erneut prüfen.
Kein Credentialexport, kein Providerwechsel und keine globale Konfigurationsänderung.

Frische Baseline am Basis-SHA, Exit 0:

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-include-tooling/m2 \
  -pl gretl-groovy,gretl-lsp -am \
  -Dtest=FragmentLoaderTest,ApplyPartialCoverageTest,ApplyEditCompatibilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=ApplyIncludeServerIT \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Rohlog, ausgewertete Testzahlen und originale XMLs vor weiterem clean gesichert:
`/Users/stefan/.cache/gretl-include-tooling/evidence/P0/`.
Eigenes Mavenrepo enthält keine übernommenen GRETL-SNAPSHOTs; alle internen
Abhängigkeiten im Reactor gebaut. Die bestehende Runtime arbeitet mit Includes,
die Toolingsicht ist erwartungsgemäss noch partiell, sichere lokale Edits und
Unsafe-/Staleguards funktionieren über den echten stdio-Server.

Testauswahl gemäss ausdrücklicher Ausnahme in [Plan §8](EXECUTION_PLAN.md#8-testauswahl-und-wiederholung-nach-korrekturen).
Keine Vollgate-/Dienst-/GeoTools-Matrix; CI-/Releasegates unverändert.
Native CUA-Oberflächen und laufendes VS Code bei P0 entdeckt; vorhandener
isolierter VS-Code-Download und Workbenchharness verfügbar. Das ist nur eine
Durchführbarkeitsprüfung, keine vorgezogene GUI-Abnahme.

## Reviewverfahren

[Featureadapter](../../../tools/include-tooling/review.py) übernimmt nur die
geprüfte V2-Aufruf-/Policyform des externen alten Include-Launchers, keine alten
Kriterien. Neue detached Worktrees, neue Sessions, richtige PWD, externer Handoff
nach Kandidatencommit und Digest ausschliesslich der drei aktuellen Auftragsdateien.
Keine vorausgefüllte Abnahme. Originalberichte, Toolereignisse, Sessionexport,
Modellidentität, echte Checks/Gegenprobe und unveränderte getrackte Bytes prüfen.
Reviewrequests unter [review](review/P0-request.md); Kandidaten-SHA und Ergebnis
werden extern erfasst, nach Abnahme hier als getrennte Statusänderung referenziert.

## P1-Kandidat

Neues neutrales Modul mit gemeinsamer ASTinspektion, originalen Helper-/Shadowing-
und Literalregeln, Rollenbefunden, Kontrollflussunsicherheit und Quellnamen.
Runtimefassade bleibt kompatibel; Tooling liefert den schon sicher konvertierten
AST und den vorhandenen UTF-16-Tokenledger. Anwendungen und Ressourcenbasen sind
getrennte Werte; RegistrationSequence erweitert bestätigte Links depth-first an
der Apply-Site. Unaufgelöste Includes bleiben partiell; keine strukturelle
Include-Editfreigabe und noch kein Resolver-/Membership-/Queryversprechen.

Entwicklungsläufe unter `/Users/stefan/.cache/gretl-include-tooling/evidence/P1/`:
Parser-/Runtimefamilien, echte SCRIPT-/JARprozesse und Distribution bereits
erfolgreich. Finale Tests laufen nach Commit am sauberen Kandidaten mit dem
exakten Befehl aus [P1-request](review/P1-request.md). Berichte vor clean gesichert.
Das neue Modul ist transitiv im Server und in Runtime-/Packager-/Distributions-
classpaths enthalten; Enforcer- und Inventarregeln werden erhalten/ergänzt.

P0-Originalreview und Validierung:
`/Users/stefan/.cache/gretl-include-tooling/review/P0-attempt-1/`.
Der unabhängige MINOR P0-M1 wurde durch Ablehnung optimierten Pythons gehärtet.
Zusätzliche negative Adaptertests prüfen falschen Digest, Nulltests, fehlende
Zielklasse und stale XMLs. Ein echter CLIexport in eine reguläre Datei vermeidet
die beobachtete Abschneidung grosser JSONs an einer Pipe; Sessionoutcome und
Idleabschluss sind ebenfalls Pflichtchecks. Diese Adapteränderungen gehören zum
neuen P1-Kandidaten, nicht rückwirkend zum geprüften P0-SHA.

## P2-Kandidat

Transitive Closure im bestehenden Resolver mit einem gemeinsamen SourceView und
Parsecache; physische Quellen, logische Anwendungen, negative Reads und rohe
Pfadprüfungen getrennt. Sichere Directoryhandles prüfen Zwischenkomponenten
**vor** einer `..`-Normalisierung. Owner-/SQLressourcen kommen vom echten Einstieg,
Includeziele von der enthaltenden Quelle. Fragmentdefaults, fehlende Ziele,
Zyklen, doppelte Anwendungen und Limits sind sichtbare strukturierte Befunde.

Der begrenzte Ownerindex benutzt `listProjectEntries` plus explizite Einstiege,
denselben Resolver und denselben Capture. Settings-Einstiege werden nicht doppelt
gezählt. Ungeschlossene/limitierte Inventare bleiben UNKNOWN. Neue Besitzer,
Quellbytes und Inventare entwerten alte Captures auch vor einem Watcherevent.
Ausgeschlossene/nested Workspacewurzeln werden nicht rekursiv inventarisiert.
Postimages erhalten jetzt auch erfasste Inventare, negative Reads, fremde
Besitzerquellen und Bufferinkarnationen ohne neue IO; P4-Bindungs-/Footprintprüfung
und positive strukturelle Include-Edits bleiben ausdrücklich ausstehend.

Neue additive, ausgehandelte Includeversion 1: sourceContexts,
selectSourceContext, sourceMembershipChanged und confirmSourceMembership.
Die zusätzliche Bestätigung verhindert eine schreibbare Zwischenansicht gegen
Disk, während ein vorher dirty geöffnetes normales Groovyfragment erst in die
Synchronisierung aufgenommen wird. Der Client übernimmt nur gemeldete Fragmente,
erhält Text/Dirtyflag, respektiert explizite Sprachwechsel und beobachtet Groovy-
Dateievents ohne globale Sprachassoziation. Normale Fragmentqueries registrieren
keinen Standalonejob; das bestehende explizite Use-File-Kommando tut dies bewusst.
Alte Clients bleiben ohne neue Kontextrequests/Edits begrenzt kompatibel.

Entwicklungsbelege: `/Users/stefan/.cache/gretl-include-tooling/evidence/P2/`.
Ganze kleine Core-/LSP-Unitfamilie und ServerProcessIT, ApplyIncludeServerIT,
IncludeMembershipServerIT; 24 gezielte Hosttests und TypeScriptcheck erfolgreich.
Neu geprüft: Dirty-vor-Membership, Bestätigungssperre, Close/Reopen gleicher
Version, Disk-/Watcherwechsel, explizite Sharedkontexte und unabhängige gepinnte
Queries; unowned Source versus bewusst registrierter Einstieg. Der installierte
VSIX-/native GUI-Abnahmepfad bleibt P4, keine Mockbehauptung als GUI-PASS.
Runtime-/JARbelege von P1 bleiben unverändert, ohne neue Ausführung in P2.

Testmigration: Die Positivfixture ApplyPartialCoverage hat nun ein wirkliches
Fragment; ein separater Missingfall verlangt APPLY_NOT_FOUND und unvollständige
Analyse. Die früher absichtlich ungelesene kaputte Serverfragmentfixture wird
jetzt gelesen und korrekt INVALID diagnostiziert; Nichtausführung und alle
Edit-/Staleassertions bleiben. Hostfixtures liefern nun explizite bounded
Ownerinventare und verbieten deren Wiederbeschaffung während Semantik/Edits.

P1-Originalreview: `/Users/stefan/.cache/gretl-include-tooling/review/P1-attempt-1/`.
P2-Erstreview am `36caba40ddd49fa6aa7ed34f646e95945dea181f`:
**NACHARBEIT**, MAJOR P2-F1. Originalbericht und selbst ausgeführte Gegenprobe:
`/Users/stefan/.cache/gretl-include-tooling/review/P2-attempt-1/`.
Der neu ausgehandelte Client hatte Settings fälschlich als unowned behandelt.
Settings besitzen jetzt einen ausdrücklichen ENTRY-Kontext des bestehenden
Projekts. Ein neuer echter stdio-Test verlangt COMPLETE und identische Projekt-
Tasks mit/ohne Include-Capability; SourceContexts zeigt den Settingsanker.

Weitere eigene Nachprüfung im selben P2-Umfang: New-Job/Use-File merken ihre
explizite Entryrolle und registrieren sie nach Start/Restart; bewusste Standalone-
Registrierung wählt genau diese Rolle. Unbeteiligte Groovyevents entwerten keine
bestehenden Captures und starten keine erneute Projektanalyse. Ältere Membership-
generationen werden ignoriert; Startupupdates werden je Root zusammengeführt.
Der Inventarhost prüft auch die Root der übergebenen Readpolicy.

Korrekturbelege unter `/Users/stefan/.cache/gretl-include-tooling/evidence/P2-fix/`.
Gezielte Nachtests betreffen Workspace/Owner/Context/Host, Semantik-/Defaults-/
Postimagekontrollen und reale Settings-/Membership-/Lifecycle-Serverwege. Die
unveränderten Parser-/Query-/Editfamilienbelege des ersten P2-Laufs bleiben als
solche kenntlich; keine alte SHA wird als frisch geprüft ausgegeben.
P2-Abnahme und validierter Sessionabschluss:
`/Users/stefan/.cache/gretl-include-tooling/review/P2-attempt-3/`.
Session `ses_e549dc130ffe44EPiJVCU00pSx`, ABGENOMMEN ohne Findings.
Attempt 2 enthält ebenfalls fachlich ABGENOMMEN und eine zusätzliche erfolgreiche
Vollfamilie, wurde aber wegen Wrapped-Commands/Verzeichnisbelegen vom strikten
Validator verworfen. Kein Gate darauf gestützt; neue frische Session in Attempt 3
am gleichen unveränderten SHA führte die Commands direkt und eine eigene neue
stdio-Gegenprobe aus. Originale bleiben erhalten.


## P3-Kandidat

Geordnete RegistrationEvents binden Fragmentdeklarationen und datei-/anwendungs-
lokale Aliase. Symbole, Referenzen, Ressourcen und semantische Diagnosen tragen
ihre Anwendung; file/SQL verwenden ausdrücklich deren Ressourcenbasis. Effektive
Include-Coverage ist je Anwendung abgeleitet und überschreibt keine physisch
gecachten Parsergebnisse. Return/Throw/Recovery und opaker Code bleiben partiell.
Graphcontainer gehören zum Entry, Anker zur Originalquelle; keine Include-DAGkante.

QueryContext und ReferenceIndex mischen Shared-Anwendungen nicht. Standard-LSP
und Modeler benutzen die bestätigte Auswahl; Outline/Folding und identische
Includelinks funktionieren auch vor der Auswahl. Der Modeler hält pro Panel die
Anwendungsidentität und erneuert nur diese Tokens. Reveal und Go-to-Definition
übertragen den bekannten Einbinderkontext in die Zieltextansicht. Kontextabhängige
Diagnosen zeigen Scope und Entry. Source-/Form-/Previewprojektionen teilen dieselbe
Analyse und Originalanker.

Pfadcompletion liest ausschliesslich captured, begrenzte Directorylisten; Host
prüft Policy, sichere Handles und Links. Auch offene Quotes erhalten nur einen
lexikalischen Completionumschlag, keine gültige Include-Deklaration. Unsupported
oder fehlende Listen bleiben ausdrücklich incomplete. Erfasste Listen bleiben in
Currentness-/Postimagewegen erhalten; Quotes und vorhandene Escapepräfixe bleiben.

Strukturelle Include-Edits bleiben mit separatem EDIT_INCLUDE_PHASE_GUARD gesperrt;
neue Fragmentwrites in Shared/UNKNOWN werden abgelehnt. Die bisherigen lokal
bewiesenen Entry-Skalarpfade bleiben erhalten; der vollständige endgültige
Footprint-/Owner-/Postimageguard und positive Mehrdateien-Edits sind erst P4.
Keine native GUI-Abnahme in P3 behauptet.

Entwicklung unter `/Users/stefan/.cache/gretl-include-tooling/evidence/P3/`:
ganze kleine Core-/Query-/Editfamilie, StaticSemanticsParityTest und reale
ServerProcess-/Apply-/Membership-/IncludeSemanticServerIT sowie gezielte
Clientfamilie. Der frühere positive always-PARTIAL-Test verlangt jetzt COMPLETE
und originale Fragmenttasks (IT11/IT12); fehlende/kaputte/Throwfälle und sämtliche
Sicherheitsassertions bleiben. Runtimeplanparität vergleicht Registrierungsfolge,
Zielclosure, Datenvorgänger und tatsächliche lokale Ausgabe; typisierte Gzip-
Komplettheit wird zusätzlich über den echten stdio-Server belegt.

P3-Abnahme am `4ab062b8436b77eaf6af0cf6303d812ca3c346a6`:
`/Users/stefan/.cache/gretl-include-tooling/review/P3-attempt-1/`, Session
`ses_e547d91fbffeJupoZjvcyhj5W9`, ABGENOMMEN ohne Findings und maschinell validiert.
240 Java-/Servertests in 43 Klassen, 58 Clienttests, TypeScript- und Bundlebau
am sauberen Kandidaten; originale XMLs/Logs unter `evidence/P3` gesichert.
Der Reviewer führte diese Auswahl und seine eigene Sharedscope-/Alias-/Resource-
Completion-/Referencegegenprobe erfolgreich aus. Eine enge transiente Allowregel
für den bereits beauftragten inneren Nodebefehl wurde im lokalen Reviewprofil
transparent archiviert und zurückgenommen; keine Produkt-/Testbytes änderten sich.


## P4-Kandidat

EditImpactAnalyzer prüft benötigte und tatsächliche physische Write-Footprints.
Shared/UNKNOWN ist auch für native Rename/Quick Fix, generische Setter,
Formularbatches und direkten EditVerifier geschlossen. Rejections nennen
Strukturcode, Originalquellen und bekannte Anwendungen. Die P3-Phasensperre ist
gezielt durch diese Guards ersetzt. Positive Task-/Parameter-Renames über Entry/
Fragmente/Defaults sowie Dependency-/Result-/Form-/Insertionswege sind geprüft.
Ein unabhängiger eindeutiger Edit und ein eindeutiger Consumer mit Shared-Produzent
bleiben positiv; ein eindeutiger Rename-Target mit Shared-Use bleibt negativ.

Ownership trennt vollständiges begrenztes Entryinventar von vollständiger
Quellenanalyse. Eindeutige bekannte Entries behalten ihren lokalen Skalarnachweis,
wenn eine unvollständige fremde Definition sie nach Root-/Entryregeln nicht
konsumieren kann. Eine unvollständige explizite Definition im selben Verzeichnis
verhindert diesen Nachweis. Kein pauschaler Fallback bei unbekanntem Inventar.

Postimages behalten Anwendung, eingefrorene Sources/Inventare und Kontextbindung.
BindingPreservation und ErrorKey unterscheiden Anwendungen; ein SQLfehler mit
gleicher physischer Range kann nicht in einen anderen Scope kompensiert werden.
Owner-/Kontextgeneration sind zusätzliche Editpreconditions. Server- und Hostwege
prüfen sie neben Quellen/Inkarnationen vor und nach asynchroner Konvertierung.
Native Zielpuffer werden vor Neuplanung geöffnet/synchronisiert. Der Modeler
verwendet einen WorkspaceEdit und speichert/undo-/redo-bedient dessen tatsächliche
Zieldokumente. Keine eigene Rückpatchengine, keine automatische Speicherung.

P4-Entwicklungstests unter `/Users/stefan/.cache/gretl-include-tooling/`:
ganze kleine Core-/Editfamilie und Runtimeplanparität grün; neue reale
IncludeEditServerIT für positive Rename-/Form-/Defaults-/Binding-/Quick-Fixwege,
unveränderte Hashes mit neuem ungeöffnetem Besitzer, Shared-Uses und Kontextwechsel.
60 gezielte Clienttests grün. Die neuen Beispielkommandos wurden wirklich
angewendet: `--keep-work` benötigt `always`; korrigierter Lauf dekomprimiert zweimal
und vergleicht alle 97 Originalbytes. Fehlversuch und korrigierter Lauf bleiben
getrennt erhalten. Historische Runtime-/JARgates bleiben unverändert.

Native installierte VSIX-Abläufe und gespeicherte GUI-Fixture-Runtime sind hier
noch NOT_EXECUTED. Nächste Aktion: sauberen Produkt-/Dokumentkandidaten bauen,
getrennte synthetische Testprofile verwenden, beide native Abläufe ausführen und
belegen; bei einem Fix passenden neuen Kandidaten bauen. Danach frische autonome
kumulative OpenCode-/DeepSeekprüfung aller IT01–IT24 seit Auftragsbasis.

Der Reviewer benötigt wegen Host-/GUIänderungen eine eigene neue native Probe.
Dafür stellt der Orchestrator einen kleinen CUA-Dateigateway nur für das isolierte
Code-Testfenster bereit. Der Reviewer entwickelt Hypothese, Aktionen und Assertions
selbst; der Gateway führt nur die gewählten nativen Aktionen aus und speichert
unbearbeitete AX-/PNG-Beobachtungen. Keine menschliche Promptübergabe und keine
Codex-Gegenprobe als Reviewerprobe. Der strikte P4-Validator verlangt reale
Reviewer-Shellereignisse plus eigene Native-Action-Receipts.


P4-Erstkandidat `965bb456f59d8b9f8baa0b8041110b8ca7e04a4c`: gezielte
Java-/Server-, 60 Client- und VSIX-/Inventargates erfolgreich, Profile/Installationen
unter `/Users/stefan/.cache/gretl-include-tooling/gui/P4-965bb45/`.
Native Vorbereitung fand die CUA-Mehrdeutigkeit gleichnamiger Codeinstanzen;
eine bytegleiche eigene Kopie der offiziellen Code-App macht die Testinstanz
gezielt adressierbar. Das historische Fenster wurde nicht bearbeitet.
Der Vorab-Groovybuffer wurde tatsächlich dirty verändert. Der bisherige lesende
Belegtreiber scheiterte vor Produktaktivierung an inspectModeler; er prüft nun die
Commandregistrierung und vermerkt productActive, statt GRETL vorzeitig zu starten.
Dies ist ein Testtreiberfix, keine Produkt-/Runtimeänderung. Der unvollständige
erste GUI-Lauf ist kein PASS und bleibt erhalten. Neue native Abnahme am neuen
sauberen Kandidaten; unveränderte Java-Produktbelege des Erstkandidaten bleiben
als wiederverwendet gekennzeichnet und werden nicht umetikettiert.
