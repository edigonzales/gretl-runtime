# P2: SCRIPT-Laden und gemeinsame Definition

Auftrag `script-includes`, Spezifikation 1.3. `assignment_base_sha`: `0bfc8f0fccf43989d751d4f5490202311d200628`; `tooling_base_sha`: `54bb123070fc7bd81aea58b4e1191beecf568cbf`; `base_status`: `MAIN_WITH_ACCEPTED_TOOLING`. Unveränderte `phase_base_sha` / `base_sha`: `00315884b00071989c2c8623fc5f0a5a7e7db39d`. Tatsächlicher Kandidaten-SHA wird nach Commit extern erfasst.

Zu prüfen: S01–S16 im SCRIPT-/Projektbetrieb, einschliesslich der erhaltenen P1-Verträge und Legacykompatibilität. S17–S19 und C01–C08 sind spätere Phasen, keine behauptete Abnahme.

Implementierung: Source-Snapshot vor Registrierung, gemeinsame Kompilation ohne Scriptausführung, frische Bindings je Quelle/Scope, ein Builder/finales Freeze, vorhandene Vorwärtsreferenzen und Resultrechte. Fragment-defaults sind Definitionsfehler. Relative Includes und Ressourcenbasis bleiben getrennt. Originalquellen für frühe und späte Fehler; Snapshotbytes liefern den geladenen Hash. Keine Runtimeabhängigkeit auf Tooling-Core. Zusatzpublic API für Bundle-Snapshot/Compilation ist für die anschliessende Paketintegration vorgesehen.

Ausgeführte gezielte Befehle mit JDK 25.0.2 (kein clean, keine Testskips):

```sh
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-job-runtime -am -Dtest='FragmentLoaderTest,IncludeSourceHashTest,ProjectHashTest,GroovyLoaderTest,GroovyProjectLoaderTest,GretlProjectParserTest,ApplyDirectiveReaderTest,FragmentGraphResolverTest,StaticSemanticsParityTest,ProjectSourceFrameTest,ProjectSourceLocationTest' -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -B -Dmaven.repo.local=/Users/stefan/.cache/gretl-script-includes/m2 -pl gretl-cli -am -Dtest='CliFragmentProcessTest,CliHelpAndDefinitionTest,CliPathBasesTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Ergebnisse: 158 bzw. acht Fälle, null Fehler/Skips. CLI-Process-Test: vier echte Prozesse, Marker für erlaubten Top-Level-Code und verbotene vorzeitige Konfigurations-/Task-/Cleanupausführung; exakter Gzipinhalt nach Dekompression. Logs extern `evidence/P2-tests.log`, `evidence/P2-cli.log`. Der Reviewer führt die vereinigte notwendige Auswahl selbst in seinem detached Worktree aus und entwickelt eine eigene Gegenprobe. Root-Vollgates und externe Services gehören gemäss expliziter Auftragsausnahme nicht zum lokalen P2-Gate.

Bekannte Grenzen: JAR-Integration noch nicht implementiert; Tooling behält den sicheren Partialzustand. Keine zusätzlichen Ablaufkanten durch Includes, kein Bindingexport, keine Metaprogrammierungssandbox. Kein Push/Merge/Release und kein produktiver Job.
