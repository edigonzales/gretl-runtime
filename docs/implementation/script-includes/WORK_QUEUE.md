# Arbeitsstand Script-Includes

- Auftrag: `script-includes`, Spezifikation 1.3.
- `assignment_base_sha`: `0bfc8f0fccf43989d751d4f5490202311d200628` (aktualisiertes `origin/main`).
- `tooling_base_sha`: `54bb123070fc7bd81aea58b4e1191beecf568cbf`.
- `base_status`: `MAIN_WITH_ACCEPTED_TOOLING`; Abstammung mit `git merge-base --is-ancestor` geprüft, Produktdiff ausserhalb `docs/` leer.
- Eigener Branch `codex/script-includes`, Worktree `/Users/stefan/sources/gretl-runtime-script-includes`. Ursprünglicher Toolingworktree unverändert.
- Die vier aktiven Auftragsdateien fehlen auf Main und wurden unverändert aus dem ursprünglichen Checkout übernommen. Kein Toolingmerge.
- Lokale Phasengates folgen der ausdrücklichen Testausnahme in Plan §1/8. Root-, CI- und Releasegates bleiben unverändert; keine Releasefreigabe.

| Phase | Status | Offene Arbeit |
| --- | --- | --- |
| P0 | REVIEWED_ACCEPTED | `e9098ec093cd0ed89065aee57aed461a5daf3c47`; unabhängiger Review und maschinelle Validierung PASS. |
| P1 | REVIEW_PENDING | Reader, Snapshot, Includegraph und Hash implementiert; unabhängiger Review ausstehend. |
| P2 | PLANNED | SCRIPT-Loader und gemeinsame Definition. |
| P3 | PLANNED | Kompilation, Metadaten, quellenfreie JAR-Prozesse. |
| P4 | PLANNED | C01–C08, installierte GUI, Dokumentation, kumulative Abnahme. |

P0-Phasebase ist der Main-Ausgangspunkt. Kandidaten-/Review-SHA werden nach Commit im externen Handoff festgehalten, kein selbstreferenzieller SHA.

P0-Baseline: JDK Temurin 25.0.2, Mavenwrapper. Zwei gezielte `GroovyLoaderTest`-Fälle, vier `GroovyProjectLoaderTest`-Fälle, eine temporäre Loaderprobe und eine Apply-Opaque-/Graphprobe: acht Fälle, null Fehler/Skips. Fehlendes `apply` ergibt erwartetes `JOB_LOAD_FAILED` mit fehlender Methodensignatur. Die temporäre Loaderprobe liegt ausschliesslich im externen Evidenzverzeichnis; der grüne Coveragevertrag bleibt als `ApplyPartialCoverageTest` erhalten.

Befehl: `./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-groovy -am -Dtest='GroovyLoaderTest#loadingDoesNotEvaluateConfigurationDefaultsOrHandlers+twoLoadsHaveFreshDefinitionsBindingsAndResultScopes,GroovyProjectLoaderTest,ApplyBaselineProbeTest,ApplyOpaqueBaselineProbeTest' -Dsurefire.failIfNoSpecifiedTests=false test` (temporäre Probenbezeichnungen). Frische XMLs und Log unter `/Users/stefan/.cache/gretl-script-includes/evidence/P0/`. Reactorpfad und eigener Maven-Cache verhindern die Verwendung fremder Projekt-SNAPSHOTs.

Reviewinfrastruktur: PATH-Version 1.18.22 und alternative 1.15.10 ungeeignet; isoliert installiertes `@opencode/cli@2.2.5` meldet `opencode v2.2.5`. Vorhandene DeepSeek-Anmeldung via `auth list --format json` erkannt, `models` enthält `deepseek/deepseek-flash`. Offizielle Modellzuordnung am 4. November 2026 geprüft. Geplanter Review benutzt direkte V2-CLI und eine nur im detached Reviewworktree liegende Policy, keine Änderung des alten Toolingreviewers.

Für P4 ist der vorhandene Weg `vscode/gretl-vscode/scripts/workbench.mjs` mit separatem Testdriver verfügbar; noch nicht ausgeführt. Internes job.json verwendet aktuell Versionen 1/2; Bundleversion 3 ist frei. Noch keine Runtime-Includes implementiert oder abgenommen.

P0-Abnahme: OpenCode 2.2.5 / `deepseek/deepseek-flash`, frische Session `ses_e58a2b462ffeejncaDOuKcj0ZP`; Prozess Exit 0. Sieben eigene JUnit-Fälle des Reviewers und elf Checks der selbst entwickelten Java-Gegenprobe grün. Modellalias gegen offizielle Tageszuordnung und tatsächlichen Sessionexport geprüft. Originale und `validation.json` (PASS) unter `/Users/stefan/.cache/gretl-script-includes/review/P0-attempt-2/evidence/`. Erster Start scheiterte vor dem Review an Agentdiscovery; explizites `PWD` und Inline-V2-Agentkonfiguration lösen dies. Kein Session-Continue. Zwei nicht blockierende Infrastrukturhygienehinweise werden bei folgenden Aufrufen berücksichtigt.

P1-Phasebase: `e9098ec093cd0ed89065aee57aed461a5daf3c47`. Neue APIs unter `runtime.groovy.fragment`, noch ohne Loaderanbindung. AST-Konvertierung ohne semantische Analyse/Transformationen, getrennte Pfadprüfung und IO, pro Scope DFS mit gemeinsamen immutable Sourcebytes, längengeframter Bundlehash. Legacy-Single-/Projekthashes ohne Includes werden aus denselben Snapshotbytes reproduziert. Limits: 64 Ebenen, 1024 Quellen, 8 MiB pro Fragment, 64 MiB Gesamtbytes bei Includes. Direkte Regression: bestehender Settingsparser; keine geänderte gemeinsame Parser-/Hashfunktion. Kleine P1-Fixtures prüfen keine Tasks/Runtimewirkung.


## P1-Reviews und aktueller Korrekturstand

Unveränderte Phasebase bleibt `e9098ec093cd0ed89065aee57aed461a5daf3c47`. Kein P1-PASS und keine P2-Arbeit behauptet. Alle Prozesse endeten regulär; Identität/Digest/Modell und getrackte Integrität sind geprüft. Originale unverändert unter `/Users/stefan/.cache/gretl-script-includes/review/P1-attempt-N/evidence/`, maschineller Status jeweils REJECTED.

| Versuch | Geprüfter SHA | Urteil und relevante Nacharbeit |
| --- | --- | --- |
| 1 | `b1c8991fcbd9a40a705102a4b0164bb33fe2181f` | NACHARBEIT: Helper-eigene apply-Aufrufe und eigene Scriptmethoden ohne Includes erhalten. |
| 2 | `667306c66d949de48b1dcbf219a722099a022605` | NACHARBEIT: pauschale Problemunterdrückung entfernen, ungültige Map-first-Aufrufe weiter ablehnen. |
| 3 | `d6f6aef18e06a51bafcd1005ae9868138e85c105` | NACHARBEIT: geerbte Methoden/Closure-Felder und Map-kompatible Signaturen beachten. |
| 4 | `08f54cb0c0810f5274f8510d6b420cb351957333` | NACHARBEIT: Closure-Signaturen prüfen, blocklokale Variablen nicht als globale Scriptüberschattung behandeln. |
| 5 | `efe3bbcba2651969015648d26c924c4d7a10fa64` | NACHARBEIT: lokale Defaultinterfaces verfolgen und Cast-Wrapper vor der Closure-Signaturprüfung auspacken. |
| 6 | `b2c7c00a08b55559d5811bb7792f5cc5c237057e` | NACHARBEIT: generische Helper-Parameter anhand ihrer Erasure/oberen Schranke prüfen. |

Aktuelle Korrektur: lokale Superklassen/Interfaces werden ohne Klassenladen besucht; unbekannte externe Helper-Typen bleiben bewusst opak. Transparente Closure-/Object-Casts werden einschliesslich Importaliasen berücksichtigt. Methoden und Closure-Literale teilen die Parameterprüfung. Nur direkte Scriptdeklarationen/-zuweisungen und Scriptmethoden gelten als globale Überschattung. Ein unüberschatteter GRETL-apply-Aufruf in einer Scriptmethode bleibt gemäss S02 verboten.

Aktueller gezielter Befehl: `./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-groovy -am -Dtest='ApplyDirectiveReaderTest,FragmentGraphResolverTest' -Dsurefire.failIfNoSpecifiedTests=false test`. Ergebnis: 65 Reader- und 16 Graphfälle, zusammen 81, null Fehler/Skips. Die vorherigen 18 Settingsparserfälle bleiben ein unveränderter alter Beleg, keine neue Ausführung. Red/Green-Logs und XMLs unter `/Users/stefan/.cache/gretl-script-includes/evidence/P1-fixN*`; aktueller Stand `P1-fix6/`. Kein weiterer clean-Lauf hat Berichte gelöscht. Der neue Kandidat braucht weiterhin eine frische unabhängige Session und eigene ausgeführte Gegenprobe.

Sechste Korrektur: Typvariablen aus lokalen Klassen-/Methodendeklarationen werden über ihre oberen Schranken bis zur Erasure verfolgt (ungebunden Object). Derselbe Weg gilt für Arrays/Varargs und Closure-Parameter. Sechs rote Reproduktionen des unabhängigen Prüfers sind grün; String-Schranken und lokale gleichnamige Map-Klassen bleiben negative Kontrollen. Die lokale Map-Klasse wurde zusätzlich mit gewöhnlichem Groovy semantisch als nicht passend nachgewiesen (`P1-fix6-type-binding.log`). Keine Klassensuche oder Auswertung im Reader.
