# Phase 2 – Gemeinsame Script-Projektdefinition

`phaseBaseSha`: `761ffa31ca9314f246fbd1420d72f5dbf1dae7ca`.

Vorherige unabhängige Phase-1-Review: PASS für `1935a95f73c45028a04316150e165ec0a61a52a5`, unverändert und separat archiviert; Arbeitsbaum zu Beginn sauber. Auftrag ausschliesslich Phase 2; Phase 3 wird nicht begonnen.

Implementierung und A-Vorpruefung abgeschlossen. Unabhaengige Review noch ausstehend; keine Freigabe behauptet. Die endgueltige Kandidaten-SHA und ihr Nachtest werden ausserhalb dieses Commits uebergeben, um Selbstreferenz zu vermeiden.

## Vor Eingriff festgehaltene minimale Vertragsanpassung

Der Ist-Code `ParameterDefinition` akzeptiert ausschliesslich lokale Parameternamen. Die neue ausdrücklich normative Projektanforderung (§3.5) verlangt zusätzlich kanonische Namen mit führendem Doppelpunkt. Der Record erhält deshalb eine additive Alternative für qualifizierte ASCII-Segmente; das bisherige Muster bleibt unverändert gültig, einschliesslich Punkt im alten Einzeldateinamen. Keine Änderung am ParameterResolver, Runnerablauf oder an der SPI. Dies ist eine kleine erforderliche Erweiterung des Runner-Datenvertrags, keine zweite Parameterauswertung; gezielte neue Regressionen prüfen die alte und neue Namensgrenze.

Groovy 6.0.0 wurde direkt per javap geprüft: GroovyShell verwendet einen übergebenen GroovyClassLoader mit kompatibler (hier derselben) CompilerConfiguration direkt weiter. Separate Shells mit separaten Bindings können daher denselben Compilerloader verwenden, ohne eigene Projekt-Classloader einzuführen oder Konstruktor-/Toplevelcode doppelt auszuführen. Keine Classloader-Schliessung vor Ende der Closure-Lebensdauer.

## Vor Eingriff festgehaltene Quellenmetadatenanpassung

`FailureInfo` leitet Quellen bisher ausschliesslich aus Java-Stackframes ab. Eine nach Rueckkehr aus einer Closure erzeugte konkrete DSL-Diagnose besitzt bereits eine echte `GroovyDslException.source`, diese geht jedoch im Report verloren (stattdessen Adapter.java). Ein kleiner neutraler Runnervertrag `SourceLocatedFailure` transportiert ausschliesslich diese vorhandene optionale SourceLocation; FailureInfo verwendet sie bevorzugt. Keine Groovy-Abhaengigkeit im Runner, keine SPI-/Event-/Ausfuehrungssemantikaenderung und keine erfundenen Stackframes. Fehlt eine explizite Position, bleibt die vorhandene Stackframe-Ermittlung unveraendert. Eine neue neutrale Runnerprobe und echte Projekt-when-/Konfigurations-/Action-/Cleanup-Proben pruefen dies. Deklarationsposition ist nur der ehrliche Fallback fuer erst nach Closure-Rueckkehr feststellbare Typ-/Pflichtfeldfehler.

Der gemeinsame JobRuntime-Runpfad bindet den Quellhash jetzt einmal vor Arbeitsbeginn, statt ihn erst nach Taskarbeit fuer den Report erneut zu lesen. Damit werden nach einem begonnenen Run entfernte Quellen nicht nachtraeglich zu einem Startfehler ohne Terminalereignis. Das gilt fuer dieselbe gemeinsame Script-/JAR-Orchestrierung; keine neue Laufzeitcache-/Ergebnisregistry. ProjectJobSource.sha256 selbst bleibt ein frischer, deterministischer Hash des aktuellen definierten Bestands. Taskarbeit, Daten, Parameter und beliebige weitere Dateien gehen nicht ein.

Die neue CLI-Prioritaetsprobe findet eine weitere alte lokale Namensgrenze in `RuntimeConfig.params`. Vor deren Anpassung festgehalten: Nur die Parametereingabe muss wie die Deklaration qualifizierte Namen akzeptieren, sonst ist PRJ-108 trotz funktionierender CLI-Einzelwerte nicht erfuellbar. Die bisher fuer Parameter und Secret-Aliase geteilte Regex wird deshalb fuer Parameter additiv erweitert; Secret-Aliase bleiben unveraendert global und lokal benannt. Keine Aenderung der Prioritaets- oder Konversionslogik.


## Gelieferter Stand und Architekturgrenzen

`ProjectTaskIndex` haelt nur definitionseigene Referenzen, Operationstypen und Originalpositionen. Seine Linkliste prueft nach der Registrierung ausschliesslich die Existenz benannter Links mit besserer Originaldiagnose. Builder und Planner bleiben fuer Ref-Identitaet, Aufloesung, Duplikate, vollstaendige Zyklen und Auswahl verantwortlich. Kein zweiter Scheduler oder ResultStore.

`GroovyJobLoader.loadProject` verwendet einen Builder und einen GroovyClassLoader mit derselben CompilerConfiguration. Root und Includes erhalten eigene Bindings, einschliesslich Modell und Operation-Symbolen bereits beim Scriptkonstruktor. Einmaliges finales freeze, danach halten gespeicherte Closures ihren eigenen Scope und die gemeinsame Definition. Es gibt keinen vorzeitig geschlossenen Loader und keinen globalen Projektcache. Die Helper-/@Field-Probe fuehrt noch nach dem Laden echten Code aus.

`JobDslModel` uebersetzt alle Namen mit dem vorhandenen `ProjectPath`, einschließlich Datei-Scope und Parameterzugriff. Cleanup behaelt seinen eingeschraenkten Context; weder result noch ctx.run werden dort neu angeboten. Task- und Result-SPI, Planner, ResultStore, RunInput und Runner-Engine sind unveraendert. Die oben vorher offengelegten kleinen Parameter-/Quellenmetadatenanpassungen sind die einzigen Runner-Vertragsaenderungen.

`ProjectJobSource` ergaenzt den vorhandenen JobSource-Vertrag. Der minimale Dispatcher unterscheidet ausdrueckliche regulaere Datei und Verzeichnis. Eine Projektwurzel braucht ihre eigene Settingsdatei; es gibt keine Suche nach oben. CLI-Projektziele sind absolut qualifiziert. Hilfe und bestehende gemeinsame check/plan/run-Orchestrierung benutzen denselben Definitionspfad. Keine parallele Parameteraufloesung oder Ausfuehrungsengine.

Der Hash ist SHA-256 ueber die Domain `gretl-project-source-v1` mit NUL, danach Settings und Skripte in Registrierungsreihenfolge. Je Datei werden die UTF-8-Bytes des normalisierten relativen Namens und unveraenderte Dateibytes jeweils mit 8-Byte-Laenge eingerahmt. Absolute Root, Daten und nicht eingebundene Quellen gehen nicht ein. Helper-/Ressourceninventarisierung wird damit nicht behauptet. Fehler behalten echte Ursache, Diagnose und begrenzte Originalframes; nur vorhandene virtuelle Dateinamen werden auf ihre Originale zurueckgefuehrt.

Das dokumentierte `examples/project-pipeline` ist die kanonische Testvorlage fuer `ProjectScriptLocalJobIT`. Sie wird in ein Verzeichnis mit Leerzeichen kopiert und durch `gretl-dist/target/dist/bin/gretl` in eigenen Prozessen aus fremdem CWD gestartet. Der Test prueft reale Gzip-/Extract-Ausgabe byteweise, exakte Taskreihenfolge, Zielauswahl, einen Runreport/Start/Terminal und Originalfehlerzeile. Evidenz liegt gesondert unter `target/project-script-evidence`; der bestehende ArtifactJobRunner und seine JAR/IMAGE-Grenze bleiben unveraendert.

Kein Produktmodul, keine neue Dependency und kein Buildprofil wurden eingefuehrt. Keine bisherigen fachlichen Testassertions geaendert, geloescht, verschoben oder abgeschwaecht. Die 626 vorherigen Tests bleiben erhalten; 25 neue JUnit-Faelle ergeben 651 im aktiven normalen Reactor. Schema 3, loadCompiledProject, Projektcompiler, Projektpaketierung und Phase-3-Artefaktkataloge wurden nicht begonnen. Die Phase-3-Backing-Service-/GeoTools-Gesamtqualifikation ist NICHT AUSGEFUEHRT.

## Testfallmatrix

Alle Zeilen wurden gegen echten Produktcode ausgefuehrt. Eine Testmethode kann mehrere Orakel und negative Varianten enthalten.

| ID | Status | Ausfuehrbare Probe / fachliches Orakel |
|---|---|---|
| PRJ-101 | PASS | `GroovyProjectLoaderTest; ProjectScriptLocalJobIT`: Gleiche Dateinamen und lokale run-Namen liefern getrennte kanonische IDs, richtige Bodywerte und isolierte Bindings. |
| PRJ-102 | PASS | `CliProjectRunTest; ProjectScriptLocalJobIT`: Echter HTTP-ZIP-Download/Extract/Import sowie lokale Gzip/Extract-Pipeline; exakte Reihenfolge, eine Run-ID und ein Bericht. |
| PRJ-103 | PASS | `ProjectScopeTest; ProjectScriptLocalJobIT`: Explizites Zwischenziel selektiert nur dependsOn-Vorgaenger; Publikationsausgabe und unverbundene Action bleiben aus. |
| PRJ-104 | PASS | `GroovyProjectLoaderTest; ProjectScopeTest; CliProjectRunTest`: Vorwaertsreferenz und dokumentierte Ergebnisfelder ueber Includes; fehlende Datenkante liefert UNDECLARED_RESULT_DEPENDENCY. |
| PRJ-105 | PASS | `ProjectScopeTest`: after selektiert keinen weiteren Task und erlaubt weder result noch resultOptional. |
| PRJ-106 | PASS | `ProjectScopeTest; GroovyProjectLoaderTest`: Consumer vor Producer geladen, unselektierter Datenzyklus erkannt, unbekannte Links mit Originalquelle; fremde TaskRefs abgewiesen. |
| PRJ-107 | PASS | `ProjectScopeTest; ProjectScriptLocalJobIT`: Zwei gleichnamige sql/update.sql mit unterschiedlichen Bytes; eigener Scope in when, Konfiguration, Action, ctx.file, innerem ctx.run und typisiertem Cleanup. |
| PRJ-108 | PASS | `CliProjectRunTest; GroovyProjectLoaderTest`: Root/lokal/fremd, Defaults, globale Required-Pruefung, Long/Boolean/Path, CLI > Datei > Runtime > Default, globale Config-/Secret-Aliase; unbekannte/explizit lokale Werte scheitern vor Run. |
| PRJ-109 | PASS | `ProjectScopeTest`: Zwei qualifizierte release-Cleanups einmal in umgekehrter Reihenfolge; Erfolg/Fehler/NO_DATA/Cancellation und ein gemeinsamer Abschluss. |
| PRJ-110 | PASS | `CliProjectRunTest; ProjectScriptLocalJobIT`: help/check/plan/mermaid ohne HTTP, Fachausgabe oder Cleanup; Hilfe zeigt qualifizierte Ziele und Parameter. |
| PRJ-111 | PASS | `ProjectScopeTest; ProjectScriptLocalJobIT; unveraenderte CLI/DSL/JAR-Regressionen`: Expliziter Dateipfad ignoriert absichtlich ungueltige Settings; alte Doppelpunkt-Tasknamen und Punkt-Parameter bleiben erlaubt. |
| PRJ-112 | PASS | `GroovyProjectLoaderTest; ProjectSourceLocationTest; ProjectScriptLocalJobIT`: Originalpfad/Zeile bei Registrierung, Compile/Toplevel, when, Feldkonfiguration, Action, Cleanup; Prozessreport und Eventlog mit qualifizierten IDs und genau einem Start/Terminal. |
| PRJ-113 | PASS | `ProjectScopeTest; bestehende Runner-/Cancellation-Integrationsregressionen`: Fail-fast, NOT_RUN, NO_DATA und kontrollierte Cancellation bleiben im gemeinsamen Runner; Cleanup genau einmal. |
| PRJ-114 | PASS | `CliProjectRunTest`: Unbekanntes qualifiziertes Default/CLI-Ziel und unqualifiziertes CLI-Ziel liefern Startfehler Code 2; keine Run-Dateien. |
| PRJ-115 | PASS | `ProjectHashTest`: Identischer Bestand unter anderer absoluter Root gleicher Hash; Settings-/Jobbytes aendern ihn; Daten/nested Settings/unselektierte Jobs nicht. |

PRJ-001 bis PRJ-007: PASS als unveraenderte Parser-/Value-Object-Regressionen im normalen Gate. PRJ-201 bis PRJ-212: NICHT AUSGEFUEHRT (Phase 3).

## Ausgefuehrte Pruefungen und Evidenz

Umgebung: macOS 27.0.1 aarch64, Eclipse Temurin 25.0.2+10-LTS, Wrapper Maven 3.10.0, Groovy 6.0.0. Ausgaben: E/`environment.json`, E = `/Users/stefan/sources/gretl-runtime-phase-2-evidence-20261029`.

`./mvnw -B clean verify`: Exit 0, 651 Tests, 0 Failures, 0 Errors, 0 Skips. Kein Skip-/Selektionsflag im Pflichtgate. Der aktive XML-Bestand ist in E/`before-final-normal/result.json` aufgelistet; Console, XML, Coverage und gehashte freigegebene Artefakte liegen in E/`before-final-normal/`. `python3 scripts/audit-job-evidence.py`: Exit 0; E/`before-final-normal/audit.log` und `qualification/` pruefen die bisherigen echten JAR-/Script-Ausfuehrungen. Die zusaetzlichen Script-Projektprozesse liegen getrennt in E/`before-final-normal/artifacts/gretl-integration-tests/target/project-script-evidence/`, mit Prozesskommando, CWD, UTC, Exitcode, Distributionsmanifesthash, Logs, Berichten und Nutzdateien. Keine konkreten Secretdateien werden archiviert.

Vor dem Commit ausgefuehrte Entwicklungsbefehle mit allen Exitcodes und Zeitpunkten sind vollstaendig in `phase-2-evidence.json` aufgefuehrt. Rote Fruehstaende sind keine versteckten Regressionen: zuerst fehlender Lader bzw. ProjectJobSource; danach Korrekturen neuer Testfixtures fuer ResultSlot.value(), lexikalische Bindings bei DELEGATE_ONLY, reale /private/var-Pfade, Path-Lesen ohne groovy-nio, vorhandenes RequiredText-Symbol, @Field-Zugriff, --keep-work-Wert, Cleanup-Helper und nur fuer run erlaubte Reportoption. Eine Batch-Aenderung scheiterte lokal an Python-Syntax vor Ausfuehrung; anschliessende Patches wurden explizit angewendet. Die neue Prioritaetsprobe fand die echte RuntimeConfig-Namensgrenze; sie wurde minimal korrigiert und erfolgreich wiederholt. Die zusaetzliche Foreign-Ref-Probe hatte zuerst einen falsch erwarteten Code und verwendet jetzt den bestehenden FOREIGN_TASK_REFERENCE. Nach dem ersten gruenen Gesamtlauf wurden ausschliesslich neue CLI-Assertions fuer konkrete Fehlercodes, genaue HTTP-Taskreihenfolge und gemeinsame Run-ID verstaerkt. Die gezielte Wiederholung besteht; das finale erneute Pflichtgate prueft diesen endgueltigen Stand gemeinsam. Keine dieser fruehen roten Ausfuehrungen wird als PASS ausgegeben.

Die exakten Source-/JAR-SHA, XML- und Artefaktpruefsummen stehen in den externen Gatearchiven statt als unvollstaendige Auswahlliste hier. Der Vorlaufkatalog hat absichtlich die Phasenbasis und dirty=true; der nach dem Kandidatencommit geforderte erneute Gesamtlauf muss die konkrete Kandidaten-SHA und dirty=false tragen. A uebergibt erst danach den externen `phase-2-handoff.json` an einen frischen unabhaengigen B in separatem detached Worktree. Der gesamte Diff beginnt unveraendert bei der oben genannten Phasenbasis. Bs Urteil wird unveraendert separat archiviert; dieser Implementierungsbericht behauptet keinen eigenen unabhaengigen PASS.

## Bekannte Grenzen

Keine Windows-Laufzeitumgebung wurde ausgefuehrt; Pfadlogik verwendet Path und normierte logische IDs, die bestehenden plattformneutralen Tests laufen mit. Vertrauenswuerdiges Job-Toplevel und Hilfsklassen sind wie bisher ausfuehrbarer Groovy-Code; keine allgemeine Sandbox. Bei fehlenden Quellenzeilen ist nur eine vorhandene Deklarationsposition ein ehrlicher Fallback. Fremd-/Hintergrundausgaben bleiben den vorhandenen Logginggrenzen unterworfen. Keine produktiven Jobs oder externen Dienste geaendert. Keine offenen fachlichen Findings von A; unabhaengige Beurteilung steht aus.
