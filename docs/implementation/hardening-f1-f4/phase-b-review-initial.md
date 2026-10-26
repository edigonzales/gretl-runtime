# Gegenprüfung Phase B

## Identität und Prüfgegenstand

- Spezifikation: Version 1.1; SHA-256 `2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.
- Analysebaseline: `288137f116f2d861a1648b5eeaf889d463a5ea6c`; dokumentierter neuerer Ausgangsstand: `bc044302829ede67ead27038bfdf2be90d0b0063`.
- Kandidat: `a15f5000cc7e704fb2b6c3c56ba96d17d94be8b5`; Elternstand der Phase-B-Änderung: `7b929895b655f8451a5359c987c689ad686e9363`.
- Sourcezustand: Rootcheckout am Kandidaten ohne `git status --short`-Ausgabe. Eigene Prüfungen in der aus genau diesem SHA erzeugten Archivkopie `/tmp/gretl-gate-b-review.Q6Lvw3`; die dortige einzige Quelländerung ist die unten dokumentierte Reviewer-Testprobe.
- Implementierermodell: GPT-6-basiert laut Handoff; exakter Providername unbekannt und nicht attestiert.
- Reviewermodell/Sitzung: `gpt-5.6-sol`, separate neue Agentensitzung `/root/gate_b_v11`. Damit vom dokumentierten Implementierermodell verschieden; die fehlende Providerattestierung des Implementierers bleibt ein Herkunftsvorbehalt, ist aber nicht der Grund des Urteils.
- JDK: Temurin 25.0.3; Maven Wrapper 3.10.0; macOS/Darwin, APFS. Die zusätzlich bereitgestellte Linux-POSIX-Probe meldet PASS (`phase-b-linux-result.log`).

## Urteil

**FAIL**

Der Kandidat verletzt F4-08/F4-05: Ein zeitversetzt weitergereichter JUL-Record kann auf demselben sequentiellen Thread den inzwischen aktiven Taskscope übernehmen. Gate B ist damit unabhängig vom Ausgang der noch laufenden vollständigen Freigabebuilds nicht erfüllt.

## Selbst ausgeführte Prüfungen

| Test / Befehl | Ergebnis | Evidenz |
|---|---|---|
| Source-/SHA-/Spec-Bindung, Diff `7b929895..a15f5000`, vollständige Anweisungs- und Spezifikationslektüre | Kandidat sauber und verfügbar; Spec-Hash stimmt | `gate-b-code.diff`, dieses Protokoll |
| Enger JDK-25-Reaktorlauf: `LibraryScopeTest,LibrarySnapshotTest,LoggingScopeTest,ScopedControlTest,StartBoundaryTest,SetupFailureTest,WorkspacePlatformTest,CancelCauseReportTest` | BUILD SUCCESS; Runner 27 und Logging 16 Tests, 0 Fehler/Skips | `reviewer-narrow.log` |
| Produktiver Script-/JAR-Pfad: `LibraryScopeLocalJobIT,HardeningLocalJobIT` über `gretl-integration-tests -am verify`, explizit an `a15f5000...` gebunden | BUILD SUCCESS; je Surefire und Failsafe 17 Tests, darunter echter Scriptlauncher und reguläres Job-JAR; 0 Fehler/Skips | `reviewer-product-starts-bound.log` |
| Reviewer-Gegenprobe `sameThreadRelayedJulRecordCannotBorrowLaterTask` | erwartungsgemäss rot: `expected null but was b` | `reviewer-same-thread-jul.log` |
| Erster Archiv-Produktlauf ohne explizite Revision | vor Tests durch die beabsichtigte Source-Archive-Prüfung abgebrochen; mit expliziter Kandidatenbindung erfolgreich wiederholt | `reviewer-product-starts.log`, `reviewer-product-starts-bound.log` |

## Eigene Gegenprobe und Finding

### GB-F4-01 — zeitversetzter JUL-Record auf demselben Thread erbt Task B

- Schwere: blockierend für Gate B.
- Datei/Methode: `gretl-logging/src/main/java/ch/so/agi/gretl/runtime/logging/LogbackBridge.java`, `OriginJulBridge.publish(LogRecord)`.
- Reproduktion: In Scope A wird ein `LogRecord` erzeugt und seine `longThreadID` auf den aktuellen Thread gesetzt. A wird geschlossen. Auf demselben Thread wird Scope B geöffnet und derselbe Record mittels `Logger.log(record)` publiziert.
- Erwartet: F4-08 verlangt für einen asynchron/fremd weitergereichten JUL-Record ohne ausdrücklichen Ursprungssnapshot keine nachträglich erratene Task-/Invocation-ID; zulässig ist run-only.
- Beobachtet: `task_id = "b"` und damit falsche Zuordnung zum Empfängerscope. Die Assertion `assertNull(task_id)` scheitert mit `expected: <null> but was: <b>`.
- Ursache: Der Guard neutralisiert den Arbeitsscope nur, wenn `record.getLongThreadID() != Thread.currentThread().threadId()`. Ein zeitversetzter Record auf demselben Thread ist nicht vom synchron im aktuellen Scope erzeugten Record unterscheidbar und übernimmt daher das aktuelle MDC von B.
- Wirkung: Bibliotheksdiagnosen können dem falschen Task/Invocation zugeordnet werden. Genau diese Falschzuordnung verbieten F4-05, F4-06 und F4-08.
- Erforderliche Korrektur: JUL-Ursprung muss am Erzeugungs-/Publikationsübergang zuverlässig gebunden oder bei nicht beweisbarem Ursprung unabhängig von Thread-ID auf run-only begrenzt werden. Eine Thread-ID-Heuristik allein genügt nicht. Danach dieselbe Gegenprobe sowie bestehende synchrone JUL-, Fremdthread-, deferred-SLF4J- und Produktstarttests erneut ausführen.

Die Gegenprobe wurde ausschliesslich in der isolierten Archivkopie ergänzt. Es wurde kein Produktivcode geändert.

## Abgleich mit Anforderungen und 62 Prüffällen

Die vollständige `acceptance-matrix.md` mit allen 62 IDs wurde gegen Spezifikation und vorhandene Testmethoden gelesen. Die bestehende Suite deckt F1-Rechte/Kollision/Symlink, F2-Handlelebensdauer/Causes/Präzedenz, F3-Start-vs.-Tasksetup und die meisten F4-Scopefälle nachvollziehbar ab. Die selbst ausgeführten engen Läufe bestätigten insbesondere R1-R3 erneut: gespeicherte Handles, reine Budgetabfragen, konkrete `cancelReason`/`cancel_reason`, exklusive Startgrenze und strukturierte Task-Setup-Pfade blieben grün. `HardeningLocalJobIT` kontrollierte reale Reports, JSONL, Marker und POSIX-Dateirechte; `LibraryScopeLocalJobIT` kontrollierte den weiterleitenden `JobRuntime`-Sink in Script und Job-JAR, SLF4J/JUL, Provider-Isolation, Secretmaskierung, Cause/Suppressed und das finale Ereignis.

F4-01 bis F4-07 sind durch Codelektüre und die grünen engen bzw. Produktproben weitgehend belegt: lexikalische Run-/Task-/Invocationscopes, Wiederherstellung reservierter MDC-Werte, fremde MDC-Felder, eventgebundene SLF4J-Snapshots, fremde Threads, Fehlergraph/Redaction und störende Hooks. F4-08 ist jedoch nur für synchrones JUL und für Weitergabe auf einen *anderen* Thread grün. Die unabhängige Same-Thread-Gegenprobe widerlegt den allgemeineren Pflichtvertrag. Damit sind TC-F4-10 und der F4-08-Anteil von TC-G-04 nicht vollständig erfüllt; die Matrix kann am Kandidaten nicht insgesamt PASS sein.

## Artefakt-/Buildprüfung und Grenzen

Der vom Maintainer gestartete Root-Standardbuild lief während der Prüfung noch; Standardaudit/-archiv sowie Vollprofilbuild/-audit/-archiv waren deshalb noch nicht vollständig verfügbar und wurden nicht abschliessend attestiert. Dieser fehlende Abschlussnachweis wird nicht hinter einem PASS verborgen. Er ändert das Urteil nicht: Das reproduzierte Produktfinding erzwingt bereits FAIL. Ein korrigierter neuer Kandidat benötigt erneut den vollständigen Standard- und Vollprofilnachweis samt SHA-/Dirty-/Hash-/Skipprüfung.

Nicht ausgeführt wurde ein echter Windows-/Nicht-POSIX-Lauf; dies ist kein Ersatz für das vorhandene obligatorische Linux/POSIX-Gate. Kein IMAGE-Lauf war nach Spezifikation verlangt.

## Nachtestbedingung

Der Implementierer muss GB-F4-01 beheben und einen neuen Kandidaten-SHA einfrieren. Der Reviewer gibt keine eigene Produktkorrektur frei. Beim Nachtest sind mindestens die rote Same-Thread-JUL-Probe, alle bestehenden F4-Tests, `LibraryScopeLocalJobIT` und `HardeningLocalJobIT` in beiden Prozesseinstiegen sowie beide vollständigen Freigabebuilds und Artefaktaudits am neuen SHA auszuführen und individuell zu prüfen.
