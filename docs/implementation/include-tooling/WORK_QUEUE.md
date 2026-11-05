# Include-tooling: Arbeitsstand

**Stand: P0 REVIEWED_ACCEPTED; P1 IMPLEMENTED, finales Kandidatengate anstehend.**
P0-Kandidat `d2dccba942a749a3f270a7dbfbdb11ffe2814a1a` wurde unabhängig
abgenommen und nach vollständigem Prozess-/Sessionabschluss maschinell validiert.
P2 beginnt erst nach unabhängiger P1-Abnahme.

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

Nächste Aktion: finales P1-Gate und neue unabhängige Session am Kandidaten.
