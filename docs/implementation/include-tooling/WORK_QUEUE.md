# Include-tooling: Arbeitsstand

**Stand: P0/P1 REVIEWED_ACCEPTED; P2 NACHARBEIT korrigiert, erneuter Review ausstehend.**
P0-Kandidat `d2dccba942a749a3f270a7dbfbdb11ffe2814a1a` wurde unabhängig
abgenommen und nach vollständigem Prozess-/Sessionabschluss maschinell validiert.
P1-Kandidat `fb4664de265d06e3145d2757c1d1aed853d25da1` wurde ohne Findings
unabhängig abgenommen und nach Prozess-/Sessionabschluss maschinell validiert.
P3 beginnt erst nach unabhängiger P2-Abnahme.

Basis: `9773c42ddb12a7343cf5f9ad7b6be81b489abb30`, sauberer Main und
`origin/main` (Remote bei Start geprüft). Branch `codex/include-tooling`, eigener
Worktree `/Users/stefan/sources/gretl-runtime-include-tooling`. Recherchepin
`ae0a2630156280e0e7f6bebf2354884f555d8d7e`: nur zwei Dokumentationscommits
seither (Include-Pfade und dieser neue Auftrag), keine Produktdeltas.
Andere Worktrees bleiben unverändert.

## Phasen und nächste Arbeit

- P0: reale Baseline erfolgreich; schlanker neuer Reviewadapter, isolierte Session
  und unabhängige Gegenprobe sind jetzt abzuprüfen (W01–W04).
- P1: neutrale Include-AST-Inspektion im neuen `gretl-script-syntax`, kompatible
  Runtimefassade, Toolingadapter, Anwendungen und Reihenfolge. Reader-, SafeParser-,
  Loaderfamilie und kleiner echter SCRIPT/JAR-/Paketnachweis.
- P2: Closure, Sourcecapture, Ownerindex, Context-/Membershipvertrag und Client.
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
Nächste Aktion: Korrekturkandidat committen, gezielt prüfen und in frischer
unabhängiger Session die ganze P2-Phase gegen dieselbe Phasebase bewerten.
