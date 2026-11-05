# Include-tooling: Arbeitsstand

**Stand: P0 TESTED, unabhängiger Review ausstehend.** P1 beginnt erst nach
maschinell geprüfter P0-Abnahme. Keine Include-Produktänderung in P0.

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

Nächste Aktion: P0-Kandidat committen und autonom unabhängig prüfen lassen.
