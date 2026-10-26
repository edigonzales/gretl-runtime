# Gate A – unabhängiges Review Phase A (F1–F3)

## Urteil

**FAIL** für den ausdrücklich geprüften Kandidaten
`bc044302829ede67ead27038bfdf2be90d0b0063` gegenüber der Analysebaseline
`288137f116f2d861a1648b5eeaf889d463a5ea6c`.

F1 und F3 bestehen meine Prüfung. F2 hat ein reproduzierbares
Präzedenz-/Konsistenzproblem zwischen reinem Runbudget und lokalem
Taskkindbudget. Phase B/F4 darf für diesen Kandidaten nicht beginnen.

## Blockierendes Finding

### [F2] Beobachtung des abgelaufenen Runbudgets wird nicht als zuerst kontrolliert erkannter Timeout gespeichert

**Schwere:** blockiert Gate A.

`RunControl` ist sowohl das globale `TimeBudget` als auch die globale
Cancellation. Seine öffentlichen Budgetleser sind aber rein lesend:

- `RunControl.remaining()` gibt nach Ablauf `Duration.ZERO` zurück, ohne
  `observeCancel(TIMEOUT)` aufzurufen
  (`gretl-runner/.../RunControl.java:170-177`).
- `RunControl.expired()` gibt nach Ablauf `true` zurück, ebenfalls ohne den
  Timeout als kontrolliert erkannt zu speichern
  (`RunControl.java:179-182`).

Das von `childBudget` gelieferte lokale Taskbudget verhält sich anders:
`remaining()==0` und `expired()==true` rufen jeweils
`observeCancel(TIMEOUT)` auf (`RunControl.java:204-224`).

Dadurch hat derselbe Taskcode unterschiedliche Fehlerpräzedenz, je nachdem ob
er nur das globale Runbudget oder ein lokales Taskbudget erbt:

```groovy
action('probe') { ctx ->
    while (!ctx.budget().expired()) Thread.onSpinWait()
    throw new IOException('failure-after-observed-timeout')
}
```

Mit globalem `--timeout PT0.1S` ist `ctx.budget()` direkt der `RunControl`.
Der Code hat den Ablauf über die öffentliche API kontrolliert festgestellt,
aber `cancellationObserved` bleibt false. Der anschließende echte Fehler wird
in `SequentialJobRunner.executeTask` deshalb als `WorkCompletion.Failure`
klassifiziert (`SequentialJobRunner.java:239-243`). Erst die nachfolgende
Close-Grenze beobachtet den bereits verstrichenen Timeout und hängt ihn dem
Fehler suppressed an. Ergebnis ist fälschlich `FAILED`, Code 10.

Mit `timeout: 'PT0.1S'` auf demselben Task setzt dagegen bereits
`childBudget.expired()` den Timeout. Derselbe nachfolgende Fehler wird
sekundär, Ergebnis `CANCELLED`, Code 20.

Das verletzt die für F2 verlangte konsistente Budget-/Cancellation-Semantik
und die verbindliche Feststellungspräzedenz aus CORE-024/RUN-062: Wird Cancel
kontrolliert zuerst erkannt, bleibt ein nachfolgender Fehler sekundär und das
Ergebnis ist `CANCELLED`/20. Maßgeblich ist ausdrücklich der Zeitpunkt der
kontrollierten Feststellung, nicht eine spätere spekulative Uhrenprüfung. Auch
API-040 beschreibt `remaining()==ZERO`/`expired()==true` als öffentliche
Beobachtung des abgelaufenen geerbten Budgets.

**Eigene Prozessreproduktion:**

- Produktiver Scriptlauncher, kein lokales Tasktimeout, CLI
  `--timeout PT0.1S`: Exit 10; Bericht
  `/tmp/gretl-gate-a-counterprobe/reports/run-budget-error.json` enthält
  `FAILED/10`, primär `reviewer-after-observed-run-timeout`, danach
  `TaskCancelledException(TIMEOUT)` suppressed.
- Derselbe Ablauf mit lokalem `timeout: 'PT0.1S'` und ohne globales Timeout:
  Exit 20; Bericht
  `/tmp/gretl-gate-a-counterprobe/reports/local-budget-error.json` enthält
  `CANCELLED/20` und den nachfolgenden IOException als Nebenfehler.

Die Reproduktion benutzt den regulär gebauten Distributionslauncher und reale
monotone 100-ms-Budgets; sie ist kein Testclasspath-/Failure-Injection-Fall.

**Erwartete Richtung für eine erneute Vorlage (keine Reparatur durch den
Reviewer):** Globale und lokale `TimeBudget`-Beobachtung müssen dieselbe
zustandswirksame Feststellungssemantik haben, ohne die geforderte Reihenfolge
„echter Fehler zuerst, späterer Timeout“ umzudrehen. Dazu gehören Regressionen
für `expired()` und `remaining()==ZERO` auf Run- und Kindbudget sowie die
beiden CORE-024-Reihenfolgen.

## Sonstige Codeprüfung

### F1 – Besitz und Rechte: PASS

- `WorkspaceManager.openRun` legt den run-eigenen UUID-Unterordner exklusiv
  mit `createDirectory` an und ändert einen vorhandenen Work-Root nicht
  pauschal (`WorkspaceManager.java:29-37,81-96`).
- Managerzugehörigkeit und gespeicherte Verzeichnisidentität werden vor
  Retention und Wiederverwendung geprüft
  (`WorkspaceManager.java:40-52,100-142,174-200`). Fremde oder ersetzte
  Run-/Taskbereiche werden nicht übernommen oder traversiert.
- `workPath` bleibt im bekannten Besitzbereich, erstellt neue Eltern privat
  und verweigert Symlinks im erzeugten Pfad
  (`WorkspaceManager.java:144-171`; `BaseContext.java:109-140`).
- Neue Run-/Task-/Invocation-/Cleanupverzeichnisse entstehen auf POSIX mit
  `0700`; Eventdatei und temporärer Bericht vor dem Schreiben mit `0600`
  (`PrivateDiagnosticFiles.java:17-42`, `EventFileSink.java:35-42`,
  `RunReportWriter.java:39-60`). Bestehende Elternrechte bleiben unverändert.
- Keine POM-Änderung, keine neue öffentliche API, kein allgemeines
  Dateisystemframework.

### F2 – übrige Grenzen: grundsätzlich belegt, aber durch Finding nicht freigegeben

- Predicate, Requestkonfiguration, Handlerfactory, Handler-Rückkehr und
  Task-Rückkehr haben kontrollierte Grenzprüfungen
  (`SequentialJobRunner.java:202-243`, `OperationInvoker.java:38-99`).
- `CloseStack` beobachtet Kontrolle vor, zwischen und nach LIFO-Freigaben und
  versucht alle Ressourcen (`CloseStack.java:42-71`).
- `FailureArbiter` erhält frühe echte Arbeits-/Closefehler vor späterer
  Cancellation und zuerst beobachtete Cancellation vor Folgefehlern
  (`FailureArbiter.java:19-90`). Die getestete Close-Reihenfolge ist korrekt.
- Job-Cleanup hat ein separates gemeinsames 30-Sekunden-Abschlussbudget
  (`RunControl.java:26-32`, `CleanupContextImpl.java:35-60`).
- Das blockierende Finding betrifft gerade den noch inkonsistenten
  Beobachtungsweg des direkten globalen `RunControl`-Budgets.

### F3 – kontrollierter Abschluss nach Setupfehlern: PASS

- Fehler beim exklusiven Öffnen des Runbereichs werden nach Runbeginn als
  Primärfehler gespeichert; Tasks werden `NOT_RUN`, statt den Abschluss durch
  eine ungefangene `UncheckedIOException` zu verlieren
  (`SequentialJobRunner.java:35-56,61-116`).
- Task-Workspacefehler werden dem bereits gestarteten Task zugeordnet und
  durch denselben Abschlussweg geführt (`SequentialJobRunner.java:78-104,
  195-200`).
- Cleanupverzeichnisse werden erst bei tatsächlichem `workPath` geöffnet.
  Cleanup ohne eigenen Dateibedarf läuft daher auch bei kaputtem Workspace;
  Dateibedarf scheitert kontrolliert und sekundär
  (`CleanupContextImpl.java:25-33`, `SequentialJobRunner.java:291-330`).
- Primärfehler, Cleanupfolgefehler, Retention, Bericht und genau ein terminales
  Runereignis sind zusammengeführt. Ein erforderlicher Berichtschreibfehler
  wird vor dem terminalen Ereignis erfasst und macht einen sonst erfolgreichen
  Lauf Code 10 (`JobRuntime.java:266-345`).
- Bekannt ungültige Roots vor `gretl.run.started` bleiben korrekt Startfehler
  Code 2 (`JobRuntime.java:230-251,386-400`).

### Architektur und Umfang

Der Diff ändert keine POMs, hält `gretl-api` unverändert JDK-only, führt keine
Logback-Abhängigkeit im Runner/Taskbibliotheken und keine neue Task-Logging-API
ein. Keine Tasks, Publisher-, Native-, Worker-, Daemon- oder
Parallelisierungsarbeit wurde hinzugefügt. F4/MDC/JUL-Kontextzuordnung ist im
Phase-A-Kandidaten nicht vorweggenommen.

## Reviewer- und Sitzungsangaben

- Separate Agentensitzung: `/root/gate_a_review_initial`.
- Im Spawn tatsächlich konfigurierte Modellbezeichnung: `gpt-5.6-sol`.
- Laut Handoff/System ist der Implementierer GPT-6-basiert; ein exakter
  Implementierer-Modellname ist mir nicht bekannt.
- Keine zusätzliche Provider- oder Modellattestierung wird behauptet.

## Gelesene Grundlage

Selbst gelesen beziehungsweise verglichen:

- `/tmp/gretl-hardening-evidence/gate-a-handoff.md`;
- `AGENTS.md`, `docs/spec/AGENTS.md`;
- bestätigter Auftrag
  `docs/implementation/hardening-f1-f4/gretl-runtime-startprompt-f1-f4-neu.md`;
- einschlägige 4.1-Verträge in Kapiteln 03, 04, 06, 09, 14, 15 und 16,
  insbesondere RUN-051/052/060/062/082, OPS-040/041/051, API-030/040,
  CORE-024/025 und LOG-060;
- Dateiumfang und betroffene Produktions-/Testdiffs
  `288137f116f2d861a1648b5eeaf889d463a5ea6c..bc044302829ede67ead27038bfdf2be90d0b0063`;
- tatsächlicher Code, neue Regressionstests,
  `phase-a-implementation.md`, Audit- und Archiv-Evidenz.

Die stale referenzierte, nicht vorhandene `spec.md` und fehlenden Abschnitte
8.1/9/11 wurden nicht erfunden. Grundlage sind die ausgeschriebenen
Anforderungen und Spezifikation 4.1.

## Selbst ausgeführte Prüfungen

### Repository/Evidenz

- `git rev-parse HEAD` →
  `bc044302829ede67ead27038bfdf2be90d0b0063`.
- `git status --short` und
  `git diff --exit-code -- . ':(exclude)target'` → keine Quelländerung.
- `git diff --check 288137f..bc044302` → PASS.
- `git diff --stat`, `git diff --name-status` und gezielte vollständige Diffs
  der Runner-, Job-Runtime- und Loggingdateien wurden geprüft.
- Archiv-SHA256:
  `db2e365917d7114be23c1db9c9e88cb2e72c30ac2a5adceeafcb14a8e2b566f6`,
  identisch zur Übergabe.

### Fokussierte Komponentenregressionen

Mit `JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem`:

```text
./mvnw -B -pl gretl-runner,gretl-logging,gretl-job-runtime -am \
  -Dtest=LocalBudgetRegressionTest,BudgetPrecedenceTest,SetupFailureTest,\
WorkPathOwnershipTest,WorkspaceOwnershipTest,DiagnosticFilePermissionsTest,\
RunReportWriterTest,SetupCompletionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Ergebnis: `BUILD SUCCESS`; Runner 39, Logging 3, Job-Runtime 2, insgesamt
44 Tests, 0 Failures, 0 Errors, 0 Skips.

### Echte Script-/Job-JAR-Prozesse der Autorensuite

```text
./mvnw -B -pl gretl-integration-tests -am \
  -Dtest=HardeningLocalJobIT -Dit.test=HardeningLocalJobIT \
  -Dgretl.test.includes='**/HardeningLocalJobIT.java' \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dfailsafe.failIfNoSpecifiedTests=false verify
```

Ergebnis: `BUILD SUCCESS`. `HardeningLocalJobIT` lief aufgrund der Auswahl je
einmal durch Surefire und Failsafe: jeweils 10 Tests, 0 Failures, 0 Errors,
0 Skips. Enthalten sind produktiver Scriptlauncher und regulär paketierte
Job-JARs.

Ein erster Versuch ohne
`-Dfailsafe.failIfNoSpecifiedTests=false` brach bereits im testlosen Modul
`gretl-dist` wegen des dort nicht passenden `-Dit.test`-Musters ab. Das war
eine modulübergreifende Auswahl des Reviewbefehls, kein Produktfehler; der
korrigierte Lauf oben war grün.

### Eigene Gegenproben außerhalb der Autorensuite

Fixture und Belege liegen unter `/tmp/gretl-gate-a-counterprobe`.

1. Produktiver Scriptlauncher, lokale Aktion, die das Budget ausschließlich
   über `remaining()` bis null beobachtet und danach zurückkehrt: Exit 20,
   Bericht `CANCELLED/20`, Cleanup lief. Dies bestätigt die lokale
   Rückkehrgrenze.
2. Regulär paketiertes Job-JAR mit erstem echtem Closefehler und erst danach
   ablaufendem lokalem Budget: Exit 10, Bericht `FAILED/10`,
   `reviewer-close-first` primär, Timeout suppressed, alle Freigaben und
   Cleanup liefen.
3. In diesen Läufen blieben bestehende Work-Root-Rechte `0755`, neue
   Runordner waren `0700`, Berichte `0600`.
4. Entscheidende Inkonsistenzprobe: identischer Code
   `while (!ctx.budget().expired()) ...; throw IOException` ergab mit reinem
   globalen `--timeout` `FAILED/10`, mit lokalem Tasktimeout `CANCELLED/20`.
   Die Berichte und Ausgaben sind oben im Finding benannt.

Der erste manuelle Scriptversuch ohne vorangestelltes Java-25-`JAVA_HOME`
traf den lokalen Java-21-Launcher und scheiterte vor Produktstart mit
`UnsupportedClassVersionError`. Nach Setzen der übergebenen Toolchain liefen
die beschriebenen Proben. Dies ist kein Kandidatenfehler.

## Bereitgestellte Standardbuild-Evidenz (nicht mein eigener Lauf)

Der vor Reviewstart ausgeführte und archivierte Standardlauf weist laut
`phase-a-audit.json` aus:

- `sourceRevision = bc044302829ede67ead27038bfdf2be90d0b0063`;
- `sourceDirty = false`;
- 523 Tests, 0 Failures, 0 Errors, 0 Skips;
- `python3 scripts/audit-job-evidence.py --source bc044302...` PASS;
- inventarisierte Standarddistribution und reguläre Job-JAR-Operationen.

Ich gebe diesen vollständigen `clean verify` nicht als eigenen Lauf aus. Meine
eigenen Läufe sind die 44 Komponententests, die zweimal 10 gezielten
Hardening-Prozesstests und die zusätzlichen Prozessgegenproben.

## Bekannte Grenzen

- Kooperativer Timeout beendet nicht kooperierende In-Process-Aufrufe nicht
  hart; das entspricht 4.1.
- POSIX-Modi gelten nur auf Dateisystemen mit POSIX-Attributsicht.
- Bereits erfolgte fachliche Wirkungen werden nicht zurückgerollt.
- F4/Phase B ist ausdrücklich nicht geprüft oder freigegeben.

## Schluss

Obwohl Standardbuild, Autorentests und die meisten eigenen Gegenproben grün
sind, ist der Kandidat wegen der reproduzierten global/lokal inkonsistenten
Timeoutfeststellung nicht Gate-A-fähig. **Gate A: FAIL** für
`bc044302829ede67ead27038bfdf2be90d0b0063`.
