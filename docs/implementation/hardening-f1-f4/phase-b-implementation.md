# Phase B – Bibliothekslogscopes

Grundlage ist Spezifikation 1.1 mit SHA-256
`2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.
Gate A wurde für `b7ada7598e2750112836162827c36d15586aacee` vom getrennten
Reviewermodell `gpt-5.6-sol` PASS bewertet; Bericht und erster FAIL bleiben erhalten.

## Umsetzung

Der funktionale `RunEventSink` hat einen No-op-Scopehook. Runner und Invoker binden
Run, Task und Invocation lexikalisch bis nach erforderlicher Ressourcenfreigabe.
Das lokale Taskbudget beginnt weiterhin vor Scopehook, Registrierung und Startereignis.
Job-Cleanup läuft unter Runidentität. Der JobRuntime-Adapter delegiert ausdrücklich
`emit` und `openScope`; Bericht und einmaliges Terminalereignis behalten ihre Reihenfolge.

`MdcExecutionScope` sichert und restauriert nur die fünf reservierten GRETL-Schlüssel.
Fremde Schlüssel bleiben erhalten, auch Änderungen innerhalb eines inneren Scopes.
Doppelschluss ist folgenlos. Falscher Threadschluss verändert dessen MDC nicht,
meldet Loggingdegradation und lässt den Eigentümer später restaurieren.
Bei gewöhnlichen Hook-/MDC-Fehlern bleibt der Fachstatus erhalten. Die Pipeline
verwendet nach einer erkannten MDC-Störung keine unzuverlässige Arbeitsidentität.

Die Bridge erzeugt `LibraryIdentity` aus der MDC-Map des ursprünglichen Logbackevents
und übernimmt dessen `Instant`. Die Pipeline liest beim Schreiben kein Thread-MDC.
Leere, beschädigte und fremde Run-/Arbeitsidentitäten fallen auf Run-only zurück.
Es gibt keine Taskregistry und keine automatische Threadvererbung.
Die bestehende JUL→SLF4J-Brücke erhält einen begrenzten Origin-Guard: ein auf einem
anderen Thread erzeugter JUL-Record darf beim Weiterreichen keine Taskidentität
des Empfängers übernehmen. Die SLF4J-/Logback-Providerwahl bleibt unverändert.

Öffentliche Task-API, DSL, Katalog, Produktionsdependencies und Modulgrenzen sind
unverändert. Der zusätzliche Provider existiert ausschließlich als Testressource,
wird unter JDK 25 kompiliert und als reguläres externes SPI-Artefakt paketiert.

## Tatsächliche enge Nachweise

- `f4-red.log`: vor der Korrektur kompiliert dieselbe Probe und scheitert an Task-ID
  `a` gegenüber `null`.
- `f4-jul-relay-red.log`: zusätzliche Gegenprobe scheitert vor dem Origin-Guard an
  fälschlicher Task-ID `b` statt Run-only.
- `f4-components-all.log`: gesamte Runner-/Groovy-/Logging-/JobRuntime-Komponentensuite
  erfolgreich. Nachträglich ergänzte Prozessproben verwenden denselben Produktpfad.
- `f4-process-development-2.log`: gezielter Reactor-Verify erfolgreich; echter
  Scriptlauncher in privater Distributionskopie plus reguläres Job-JAR. Das JAR läuft
  aus fremdem CWD, nach Löschen seiner Quelle und ohne Maven im Runtime-PATH.

Der Testprovider prüft SLF4J/JUL in Konfiguration, Factory, Handler und Close, zwei
Tasks, zwei innere Aufrufe, gefangenen Fehler, Wiederherstellung des Taskscopes,
Run-only-Cleanup, vorab materialisiertes A-Event während B, Kind-/Poolthreads mit
Latches, ungültigen Snapshot, fremdes MDC und reale Secretmaskierung samt Cause und
Suppressed. Geparst werden die tatsächlichen JSONL-Zeilen und der erforderliche
Runbericht. Paketprüfung bestätigt ServiceLoader und genau einen SLF4J-Provider.

Zusätzliche enge Tests prüfen Fehler/Timeout vor einem separaten Folgerun sowie
No-op- und absichtlich störende Scopehooks ohne Backendabhängigkeit im Runner.
Ein synthetischer Fehlergraph verwendet kurze Stacktraces, damit die bereits
bestehende Recordgrößenbegrenzung das konkrete Cause-/Suppressed-Orakel nicht
legitim kürzt. Die Produktbegrenzungen bleiben erhalten.

## Ausstehende Freigabe

Der eingefrorene Gesamtkandidat benötigt noch Standard- und Vollprofilbuild mit
Artefaktaudits und unabhängiges Gate B. Große Rohartefakte bleiben außerhalb Git.
Alle 62 Prüffallkennungen sind in `acceptance-matrix.md` zugeordnet.
