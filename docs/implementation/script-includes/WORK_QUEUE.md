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
| P1 | REVIEWED_ACCEPTED | `00315884b00071989c2c8623fc5f0a5a7e7db39d`; Versuch 8 und externe Validierung PASS. |
| P2 | REVIEWED_ACCEPTED | `ecfc4a7232b3c717fd172a472dc9b00fc77a6267`; unabhängiger Review und externe Validierung PASS. |
| P3 | REVIEW_PENDING | Bundlekompilation, interne Metadaten 3/Bundle 1 und echte JAR-Prozesse getestet; unabhängiges Review folgt. |
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
| 7 | `1830bf12b5690d7ffed9e30e3dac3f713fcb4cf1` | Bericht ABGENOMMEN mit MINOR zu Quell-IDs; maschinelles Gate REJECTED, da die Gegenprobe mit einer S15-Assertion und Exit 1 endet. |

Aktuelle Korrektur: lokale Superklassen/Interfaces werden ohne Klassenladen besucht; unbekannte externe Helper-Typen bleiben bewusst opak. Transparente Closure-/Object-Casts werden einschliesslich Importaliasen berücksichtigt. Methoden und Closure-Literale teilen die Parameterprüfung. Nur direkte Scriptdeklarationen/-zuweisungen und Scriptmethoden gelten als globale Überschattung. Ein unüberschatteter GRETL-apply-Aufruf in einer Scriptmethode bleibt gemäss S02 verboten.

Aktueller gezielter Befehl: `./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-groovy -am -Dtest='ApplyDirectiveReaderTest,FragmentGraphResolverTest,GretlProjectParserTest' -Dsurefire.failIfNoSpecifiedTests=false test`. Ergebnis: 68 Reader-, 18 Graph- und 18 Settingsparserfälle, zusammen 104, null Fehler/Skips. Die 18 Settingsparserfälle wurden wegen der ergänzten Root-ID-/Pfadnormalisierung erneut ausgeführt. Red/Green-Logs und XMLs unter `/Users/stefan/.cache/gretl-script-includes/evidence/P1-fixN*`; aktueller Stand `P1-final/`. Kein weiterer clean-Lauf hat Berichte gelöscht. Der neue Kandidat braucht weiterhin eine frische unabhängige Session und eigene ausgeführte Gegenprobe.

Sechste Korrektur: Typvariablen aus lokalen Klassen-/Methodendeklarationen werden über ihre oberen Schranken bis zur Erasure verfolgt (ungebunden Object). Derselbe Weg gilt für Arrays/Varargs und Closure-Parameter. Sechs rote Reproduktionen des unabhängigen Prüfers sind grün; String-Schranken und lokale gleichnamige Map-Klassen bleiben negative Kontrollen. Die lokale Map-Klasse wurde zusätzlich mit gewöhnlichem Groovy semantisch als nicht passend nachgewiesen (`P1-fix6-type-binding.log`). Keine Klassensuche oder Auswertung im Reader.

Aktuelle Ergänzungen: Einstieg-/Settings-IDs werden vor Read/Hash kanonisiert. Der Filesystemadapter prüft zuerst die ursprünglichen Pfadkomponenten, damit eine Normalisierung keinen Symlink vor `..` verdeckt. Absolut-/Rootausbruch-IDs werden abgewiesen. Äquivalente Schreibweisen erzeugen gleiche Sourcekeys, Entries, Edges und Hashes. Eine eigene Reproduktion ergänzte JDK-25-SequencedMap: die feste Liste zulässiger Map-Supertypen ist durch reine Metadaten der tatsächlichen JDK-LinkedHashMap-Hierarchie ersetzt (keine Jobklassen). Beide Ergänzungen sind Red/Green belegt, Rohbelege `P1-root-ids-*`/`P1-jdk-map-*`, aktueller vollständiger gezielter Lauf `evidence/P1-final/`. Bericht 7 bleibt unverändert am ursprünglichen SHA; keine gültige neue P1-Abnahme behauptet.

Zusätzliche Namensregression: `Map.groovy` durfte nicht als synthetische Scriptklasse den JDK-Map-Parametertyp verdecken. Die rote Probe `P1-source-name-red.log` reproduziert dies; Reader-Konvertierung und Bundle verwenden jetzt gemeinsam die bereits festgelegte virtuelle Namensfunktion (`ScriptSourceNames`). Probleme/Directives behalten die logische Quell-ID und Originalpositionen. Aktueller Lauf: 104 Fälle, null Fehler/Skips. Diese Produktänderung ist noch unabhängig zu prüfen.

## P1-Abschluss und P2-Kandidat

P1 ist am SHA `00315884b00071989c2c8623fc5f0a5a7e7db39d` unabhängig akzeptiert: Session `ses_e581d923affeIbG27T5ggQB8sO`, 104 eigene JUnit-Fälle und 25 eigene Gegenprobenchecks, null Fehler/Skips. Originalbericht und maschinelles PASS: `/Users/stefan/.cache/gretl-script-includes/review/P1-attempt-8/evidence/`. Frühere Zwischenstände oben bleiben historische, abgewiesene Versuche.

P2-Phasebase ist dieser P1-SHA. Captured Sourcebytes werden gemeinsam vor der Registrierung kompiliert; je Quelle/Scope entstehen eigene Instanz und Binding, gemeinsam bleiben Builder und GRETL-Referenzen. Registrierungsende friert einmal ein. Fragment-defaults werden vor Scriptbodies abgewiesen. Settingsparser liefert für den Bundlehash die bereits geparsten Settingsbytes. Legacyjobs ohne Includes behalten Loader-/Hashpfad. Kompilierte Snapshotklassen haben Vorrang vor gleichnamigen Filesystemhelpern. Leere und reine Klassendateien erhalten einen leeren Scriptbody durch den Compiler-AST, ohne Textverkettung oder Klasseninitialisierung.

Gezielte Tests: 158 Fälle im Groovy-/Jobruntime-Reactor, zusätzlich acht CLI-Fälle. Der neue CLI-Test startet vier reale Java-Prozesse aus fremdem CWD: check, plan, run --help und run. Er prüft Top-Level-Marker, ausbleibende Konfigurations-/Action-/Cleanupwirkung bei Definitionskommandos und nach run den exakt dekomprimierten Gzipinhalt. Keine Dienst-/Vollmatrix ausgeführt. Fehlerquellenproben umfassen Compile, Registrierung, Konfiguration, Bedingung, Action und Cleanup. Eine rote Klassendecoyprobe und rote Quellenproben führten zu Korrekturen; Resultrechte bleiben dependsOn-gebunden.

Logs: `/Users/stefan/.cache/gretl-script-includes/evidence/P2-tests.log` und `P2-cli.log`; Reviewrequest nennt die reproduzierbaren Befehle. P2 ist noch nicht unabhängig abgenommen, P3/P4 bleiben offen. Keine Veröffentlichung.

## P2-Abschluss und P3-Kandidat

P2 ist am SHA `ecfc4a7232b3c717fd172a472dc9b00fc77a6267` unabhängig akzeptiert: Session `ses_e57f8837fffe5Lce0RuVcBwTP6`, 166 eigene JUnit-Fälle und 31 eigene Gegenprobenassertionen, null Fehler/Skips. Originale und maschinelles PASS: `/Users/stefan/.cache/gretl-script-includes/review/P2-attempt-1/evidence/`. Beide MINOR-Hinweise werden in P3 aufgenommen: ProjectScopeTest plus persistente qualifizierte Fragmentresultatprobe (dependsOn erlaubt / after verweigert); P2-Rohlogs zusätzlich im P2-Unterverzeichnis, alte P0-XML ausdrücklich historisch abgelegt. Die früheren P2-Zukunftsaussagen oben beziehen sich auf den damaligen Stand.

P3-Phasebase ist dieser P2-SHA. Interne Jobmetadaten 3 enthalten single/project und ein quellenfreies Bundle 1. Rollen, Klassen, Pfade, virtuelle Namen, Einstieg-/Scopezuordnung, Kanten und Sites werden vor Scriptinstanziierung validiert; alle Klassen werden vor der ersten Konstruktor-/Initializerausführung geprüft. Registrierungssemantik bleibt derselbe gemeinsame Sessionweg wie SCRIPT. Keine Parser-/Compileraufrufe im vorkompilierten Startpfad. Alte interne Jobformate 1/2 und Benutzerschemata 1/2/3 bleiben erhalten; Dependencyinventar und LSP unverändert.

Notwendige kumulative Auswahl: 199 Fälle in 24 Suites, null Fehler/Skips, Befehl im [P3-Request](reviews/P3-request.md). Vier JAR-Testfälle starten Includes unter Einzeljob-Deskriptorschema 1/2 und Projektschema 3 sowie alte interne Einzel-/Projektformate. Jeder Quellbaum wird vor java -jar gelöscht; fremdes leeres CWD, Marker, exakter dekomprimierter Gzipinhalt, geteilte Scopes/Ressourcen/Cleanup, qualifizierte Resultate, echte Fragmentfehlerposition und entfernte Fragmentklasse werden geprüft. Jeder JAR-Prozess läuft unter einem positiv kontrollierten Javaagenten, der Sourcecompilerkonstruktion und -aufrufe scheitern lässt. Blosses Laden von Compilerklassensymbolen durch Groovy 6 FormatHelper ist separat diagnostisch erklärt, nicht als Kompilation gewertet.

Logs: `/Users/stefan/.cache/gretl-script-includes/evidence/P3-final.log`, zusätzliche fokussierte Metadaten-/Fragmentregression `P3-metadata-final.log`. XMLs vor einem weiteren clean extern gesichert. P3 benötigt die frische unabhängige Abnahme des Kandidaten; P4 bleibt offen. IMAGE-Produktcode ist unverändert, keine neue IMAGE-/Releasequalifikation behauptet.
