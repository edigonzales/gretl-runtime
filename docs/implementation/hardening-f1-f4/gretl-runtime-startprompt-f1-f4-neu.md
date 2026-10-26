# Auftrag: GRETL Runtime – Korrekturen F1 bis F4

Du arbeitest im Repository `edigonzales/gretl-runtime`.

Implementiere die Korrekturspezifikation unter:

`docs/implementation/hardening-f1-f4/spec.md`

Dies ist ein Implementierungsauftrag, kein Auftrag für eine weitere allgemeine Analyse. Liefere korrigierten Code, aussagekräftige Regressionstests, reproduzierbare Nachweise und die vorgeschriebenen unabhängigen Reviews. Beginne mit Phase A. Phase B darf erst nach bestandenem Gate A beginnen.

## 1. Ausgangslage prüfen

Lies zuerst `AGENTS.md`, die dort referenzierten Repository-Anweisungen und die Korrekturspezifikation vollständig. Ziehe die betroffenen Kapitel der Spezifikation 4.1 sowie die aktuellen Modul-, Logging-, Test- und Paketierungsverträge hinzu.

Die analysierte Ausgangsbasis ist:

`288137f116f2d861a1648b5eeaf889d463a5ea6c`

Erfasse den tatsächlichen Branch, HEAD und vorhandene Arbeitsverzeichnisänderungen. Erhalte Nutzeränderungen. Setze einen neueren Repositorystand nicht auf die Ausgangsbasis zurück.

Prüfe jedes Finding am tatsächlichen Code. Ist es bereits behoben, dokumentiere `ALREADY_FIXED` mit Code- und Testbeleg; implementiere keine künstliche zweite Lösung. Die vorgeschriebenen grünen Nachweise und das unabhängige Review bleiben erforderlich.

Fehlt die Korrekturspezifikation, benenne dies konkret und erfinde keine Ersatzanforderungen.

## 2. Umfang und Architekturgrenzen

Bearbeite ausschliesslich:

- **F1:** Bestehende Verzeichnisrechte unverändert lassen; neue run-eigene Verzeichnisse und Diagnosedateien gezielt schützen.
- **F2:** Lokale Taskbudgets konsistent beobachten und kontrollierte Timeouts zuverlässig behandeln.
- **F3:** Begonnene Runs bei gewöhnlichen Workspace-/Setupfehlern durch Cleanup und den bestmöglichen Berichtabschluss führen.
- **F4:** Bibliothekslogs über SLF4J/JUL dem verlässlich bekannten Task-/Invocation-Kontext zuordnen.

Erhalte die öffentliche Task-API, DSL, Modulgrenzen, den bestehenden Taskkatalog, die fachlichen Tests und die selektive Jobpaketierung. Script und Job-JAR verwenden weiterhin dieselbe Job-Runtime.

`gretl-api` bleibt JDK-only. Runner und Taskbibliotheken erhalten keine Logback-Abhängigkeit. Implementiere keine neue Task-Logging-API und keinen zweiten Loggingstack.

Keine neuen Tasks, Publisher-Implementierung, CLI-Schalter, Daemon-, Worker- oder Parallelisierungsarchitektur, Native-Image-Arbeit oder OpenTelemetry-Exporter. Keine Major-Updates als Nebenarbeit. Ein echter IMAGE-Lauf gehört nicht zum Auftrag; die vorhandene Vorbereitung bleibt erhalten.

Halte die Änderungen klein und lokal. Private Hilfsklassen und Methoden sind dort sinnvoll, wo sie die vorgeschriebenen Verantwortlichkeiten vereinfachen. Entwickle kein allgemeines Datei-, Fehlerworkflow- oder Kontextpropagationsframework.

## 3. Phase A: F1, F2 und F3 implementieren

Arbeite in der Reihenfolge aus Abschnitt 8.1:

1. Erstelle enge rote Reproduktionen für die noch vorhandenen Findings. Sie müssen am beanstandeten Verhalten scheitern, nicht an fehlenden neuen Klassen oder einem Classpathproblem. Verwende bei Bedarf eine separate Baseline-Worktree.
2. Korrigiere F1: kein pauschales `chmod` bestehender Roots oder Report-Elternverzeichnisse. Sichere exklusives Eigentum an neuen Runbereichen und begrenze Retention auf den eigenen Bestand.
3. Korrigiere F2: dieselbe Budget-/Cancellation-Semantik für Predicate, Konfiguration, Handler, Rückkehr und Ressourcenfreigabe. Ein gefangener kontrollierter Timeout hebt den Abbruch nicht auf. Die spezifizierte Fehlerpräzedenz und das gesonderte Cleanupbudget bleiben erhalten.
4. Korrigiere F3: gewöhnliche Setupfehler nach Runbeginn erhalten einen kontrollierten Fehlerabschluss. Cleanup ohne eigenen Dateibedarf darf nicht allein an einem kaputten Workspace scheitern. Bewahre die ursprüngliche Ursache; Folgefehler dürfen sie nicht verdecken.
5. Führe die vorgeschriebenen Unit-, Integrations- und Prozessregressionen aus. Prüfe den produktiven Scriptstart und regulär gebaute Job-JARs, nicht nur Handler im Testclasspath.
6. Führe den Standardbuild und Standardartefaktaudit aus. Dokumentiere einen reproduzierbaren Phase-A-Kandidaten und übergib ihn zur unabhängigen Prüfung.

Verwende kontrollierbare monotone Uhren für enge Budgettests und deterministische I/O-Fehler. Keine zufälligen kurzen Sleeps als alleiniger Timeoutnachweis, keine Root-abhängigen Scheintests und keine produktiven Failure-Injection-Schalter.

Ein verstrichenes Budget verlangt keinen unsicheren harten Threadstopp. Es muss aber spätestens an der nächsten kontrollierten Grenze erkannt werden. Ein fehlgeschlagener Run macht bereits erfolgte fachliche Wirkungen nicht rückgängig.

## 4. Gate A: unabhängige Gegenprüfung

Nach der getesteten Implementierung muss ein **anderes LLM-Modell in einer separaten Sitzung** den Kandidaten gemäss Abschnitt 9 prüfen. Verwende dafür den Reviewerprompt aus Abschnitt 11.

Übergib Spezifikation, Baseline-SHA, Kandidaten-SHA, Buildvoraussetzungen und Roh-Evidenz. Der Reviewer muss den Code und Diff selbst lesen, entscheidende Tests selbst ausführen, eigene Gegenproben untersuchen und `PASS`, `FAIL` oder `BLOCKED` begründen.

Eine zweite Rolle im selben Chat oder ein weiterer Agent mit derselben Modellidentität erfüllt diese Vorgabe nicht. Dokumentiere nur tatsächlich bekannte Modell- und Sitzungsangaben. Erfinde keine Reviews oder Testläufe.

Der Reviewer repariert den Produktivcode nicht selbst. Bei `FAIL` behebst du die Findings und legst den neuen Kandidaten erneut vor. Eine Freigabe gilt nur für den ausdrücklich geprüften Stand.

**Ist kein anderes Modell ausführbar, beende die Arbeit nach dem getesteten Phase-A-Kandidaten mit einem vollständigen Review-Handoff und Status `BLOCKED_REVIEW`. Beginne Phase B nicht.** Ein fehlendes Review verhindert nicht die vorherige Implementierung und Selbstprüfung von Phase A, aber deren Freigabe.

## 5. Phase B: F4 und Gesamtqualifikation

Beginne erst nach einem nachgewiesenen `PASS` für Phase A.

Implementiere F4 entsprechend Abschnitt 6: kleiner backendfreier Scopehook, begrenzte MDC-Anbindung, zuverlässige Wiederherstellung bei Verschachtelung und Fehlern sowie Kontext-Snapshots am Ereignisursprung.

Keine globalen `currentTask`-Felder. Keine Zuordnung einer verspäteten Meldung anhand des dann aktiven Tasks. Erhalte fremde MDC-Schlüssel; unbekannte Threadkontexte bleiben ausdrücklich unbekannt. Konfiguration, Handler, Ressourcenfreigabe und Job-Cleanup müssen die jeweils richtige Identität erhalten.

Prüfe SLF4J, JUL, Ursachenketten, Maskierung und die definierten Thread-/Snapshot-Grenzen. Verwende einen echten paketierten Testprovider. Führe alle Phase-A-Regressionen erneut aus.

Führe am eingefrorenen Kandidaten beide bestehenden Freigabepfade aus:

```sh
CANDIDATE_SHA=$(git rev-parse HEAD)

./mvnw -B clean verify
python3 scripts/audit-job-evidence.py --source "$CANDIDATE_SHA"

# Standard-Evidenz vor dem nächsten clean ausserhalb von target sichern.
./mvnw -B -Pgeotools-probe,backing-services clean verify
python3 scripts/audit-job-evidence.py --full --source "$CANDIDATE_SHA"
```

Der Kandidaten-SHA muss den tatsächlich geprüften Produkt-, Test- und Buildstand identifizieren. Nach relevanten Änderungen sind die betroffenen Nachweise zu erneuern.

Fehlende Dienste oder Infrastruktur sind `NOT_RUN` beziehungsweise `BLOCKED`, kein Erfolg. Keine stillen Skips, kein JAR-/IMAGE-Fallback und keine abgeschwächten Assertions. Der vollständige bestehende fachliche Nachweisbestand bleibt erhalten.

Auch Phase B benötigt die unabhängige Gegenprüfung durch ein anderes Modell. Ohne deren `PASS` ist der Gesamtauftrag nicht abgeschlossen.

## 6. Evidenz und Abschluss

Dokumentiere unter `docs/implementation/hardening-f1-f4/` den Ausgangsstand, Phasenstatus, Implementierungsberichte, Reviewberichte und die Abnahmematrix gemäss Spezifikation. Überschreibe keine historischen Reviews. Grosse Logs und Artefakte gehören in das vorgesehene Evidenzarchiv, nicht als Binärbestand in Git.

Unterscheide deutlich zwischen selbst ausgeführten Prüfungen, überprüfter CI-Evidenz und nicht ausgeführten Nachweisen. Erfasse JDK-/Maven-Version, Befehle, Ergebnisse, Kandidaten-SHA und tatsächlich gestartete Artefakte samt Prüfsummen.

Keine Produktionszugriffe, Secrets in Git, Releases, Deployments oder ungefragten Pushes. Keine automatische Wiederholung bereits ausgeführter Fachoperationen. Verwende veröffentlichende CI-Schritte nicht als Ersatz für reine Qualifikation.

Schliesse mit einem Abgleich pro Finding: Codeänderung, Regressionstest, Prozessnachweis, Reviewurteil und offene Grenze. Sage ausdrücklich, welche Phase abgeschlossen ist und welches Gate gegebenenfalls noch blockiert.

Beginne jetzt mit Repository- und Baselineprüfung. Gib einen kurzen Arbeitsplan und setze anschliessend Phase A um; bleibe nicht beim Plan stehen.
