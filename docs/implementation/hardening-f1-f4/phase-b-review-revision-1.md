# Gegenprüfung Phase B – Revision 1

## Identität und Prüfgegenstand

- Spezifikation: Version 1.1; SHA-256 `2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.
- Analysebaseline: `288137f116f2d861a1648b5eeaf889d463a5ea6c`; dokumentierter neuerer Ausgangsstand: `bc044302829ede67ead27038bfdf2be90d0b0063`.
- Kandidat: `9d6e26e1d9bfa5916a8d5c5a321d134ca5237447`; Korrektur basiert unmittelbar auf dem initial abgelehnten Kandidaten `a15f5000cc7e704fb2b6c3c56ba96d17d94be8b5`.
- Sourcezustand: Rootcheckout am Kandidaten ohne Ausgabe von `git status --short`. Eigene Builds in der aus genau diesem SHA erzeugten Archivkopie `/tmp/gretl-gate-b-r1.jwE1GH`. Dort wurde nur eine zusätzliche Reviewer-Testmethode ergänzt.
- Implementierermodell: GPT-6-basiert laut Handoff; exakter Providername unbekannt und nicht attestiert.
- Reviewermodell/Sitzung: `gpt-5.6-sol`, separate Sitzung `/root/gate_b_v11`. Die dokumentierten Modellfamilien sind verschieden; die fehlende Providerattestierung des Implementierers bleibt als Herkunftsvorbehalt festgehalten.
- Umgebung: Temurin JDK 25.0.3, Maven Wrapper 3.10.0, macOS/APFS. Verpflichtende Linux/POSIX-Proben liefen zusätzlich unprivilegiert im gepinnten Temurin-Container.
- Der initiale FAIL-Bericht bleibt unverändert in `phase-b-review-initial.md`; diese Revision ersetzt dessen Urteil nur für den neuen Kandidaten.

## Urteil

**PASS**

GB-F4-01 ist am neuen Kandidaten geschlossen. Es wurden keine weiteren blockierenden Findings festgestellt. Standard- und Vollprofilqualifikation sowie Audits sind sauber an denselben SHA gebunden; die Archivmanifeste tragen ebenfalls diesen SHA, enthalten aber den unten ausdrücklich ausgeschlossenen historischen `target`-Beifang.

## Selbst ausgeführte Prüfungen

| Test / Befehl | Ergebnis | Evidenz |
|---|---|---|
| SHA-/Status-/Spec-Prüfung und Diff `a15f5000..9d6e26e1` | sauberer Kandidat; unveränderter Spec-Hash | `gate-b-r1-review-dir.txt`, Repositoryzustand |
| Enge JDK-25-Suite: `LibraryScopeTest,LibrarySnapshotTest,LoggingScopeTest,ScopedControlTest,StartBoundaryTest,SetupFailureTest,WorkspacePlatformTest,CancelCauseReportTest` | Runner 27 + Logging 19 = 46 Tests; 0 Fehler/Errors/Skips | `reviewer-r1-narrow.log` |
| Eigene Probe `julBoundaryTracksNestedIntervalsAndIsRemovedAfterOutermostClose` | grün: äußerer Eintritt, Inner-Scope, Elternrückkehr, frische Records und Entfernung nach äußerstem Close | `reviewer-jul-adversarial-probes.md`, `reviewer-r1-narrow.log` |
| Echte Produktstarts `LibraryScopeLocalJobIT,HardeningLocalJobIT` über Reaktor-`verify`, explizit an `9d6e26e1...` gebunden | Surefire 17 + Failsafe 17; SCRIPT und reguläres Job-JAR grün; 0 Skips | `reviewer-r1-product-starts.log` |
| Linux/POSIX als UID/GID 65534, netzloser gepinnter Container | Rechte, Owner/Group, denied root, Symlinkroot, ZIP-Fallback sowie private Event-/Reportdateien und unveränderte Eltern PASS | `phase-b-revised-linux-result.log`, `phase-b-linux-PosixQualification.log`, `phase-b-linux-DiagnosticPosixQualification.log` |
| Root-Standardbuild `./mvnw -B clean verify` | BUILD SUCCESS in 6:57 | `phase-b-standard.log` |
| Standardaudit | SHA korrekt, `sourceDirty=false`, 573 Tests, 0/0/0, 20 Business- plus 2 Fixture-Operationen | `phase-b-standard-audit.json` |
| Standardarchiv | 3215 Dateien, 18 eindeutige Job-JARs; gespeicherter und selbst berechneter SHA-256 `5eb86d245bcb4e4d9cf7c6863a1e0a36b6cab7392db841c02ce90ecee2f932dc` stimmen überein. Der Helper nahm zusätzlich alte `target`-Caches nicht aktiver Module auf; sie werden nicht als Kandidatenqualifikation gewertet. | `phase-b-standard-archive-result.json`, `phase-b-9d6e26e-standard-manifest.json`, `phase-b-9d6e26e-standard.tar.gz` |
| Vollprofil `./mvnw -B -Pgeotools-probe,backing-services clean verify` | BUILD SUCCESS in 11:03; echte PostgreSQL/S3/FTP-/Paket-/GeoTools-Pfade grün | `phase-b-full.log` |
| Vollaudit | SHA korrekt, `sourceDirty=false`, `full=true`, 628 auditierte Tests, 0/0/0; 44 Business- plus 2 Fixture-Operationen | `phase-b-full-audit.json` |
| Vollarchiv | 3981 Dateien, 23 eindeutige Job-JARs; gespeicherter und selbst berechneter SHA-256 `31c25a41f258aae292dd759af995f57ff61017f8511d209f5415dfafa2297917` stimmen überein. Das Manifest weist zusätzlich 24 Dateien aus historischen `target`-Caches von `gretl-basic-tasks` und `gretl-core-tasks` aus; diese Module gehören nicht zum aktuellen Root-Reaktor. | `phase-b-full-archive-result.json`, `phase-b-9d6e26e-full-manifest.json`, `phase-b-9d6e26e-full.tar.gz` |
| Unabhängiges XML-Orakel im Vollarchiv | 129 TEST-Suites und insgesamt 702 XML-Testausführungen, 0 Failures/Errors/Skips. Davon sind 74 historischer, **nicht gewerteter Archivbeifang** (`gretl-basic-tasks`: 51; `gretl-core-tasks`: 23). Der aktuelle Reaktor ergibt exakt 628, übereinstimmend mit dem Vollaudit. | Root-POM, Vollmanifest und direkte Tar/XML-Auswertung im Review |

## Nachprüfung von GB-F4-01

`MdcExecutionScope` speichert keine Identität in einer globalen Registry. Die nicht vererbte ThreadLocal enthält nur eine JUL-Sequenzgrenze. Jeder Scopeeintritt setzt eine neue Grenze; nach einem inneren Scope wird für den restaurierten Elternscope eine neue Grenze gesetzt; nach dem äußersten Close wird sie entfernt. `OriginJulBridge.publish` akzeptiert Arbeitsidentität nur, wenn sowohl der Besitzerthread als auch das aktuelle Sequenzintervall passen. Ein alter Record fällt auf run-only zurück.

Die Implementiererprobe prüft beide ursprünglichen Fehlerformen: A→B sowie Invocation→restaurierter Taskscope. Ein künstlich zukünftiger Zeitstempel umgeht die Sequenzprüfung nicht; ein wirklich frisch im Zielscope erzeugter JUL-Record behält seine korrekte Identität. Meine zusätzliche Probe prüft ausserdem direkt die Grenzlebensdauer und das Entfernen der ThreadLocal-Belegung. Die reale Providerprobe führt den verzögerten Same-Thread-JUL-Fall in SCRIPT und Job-JAR aus. Alle Orakel sind grün.

## Abgleich der Anforderungen und 62 Prüffälle

Die vollständige `acceptance-matrix.md` wurde erneut gegen Spezifikation, Code, Testquellen und Artefakte geprüft.

- F1-01 bis F1-06: vorhandene Roots/Eltern bleiben unverändert; neue eigene Leafs und Diagnosedateien sind privat; Datei-/Verzeichnis-/Symlinkkollision erwirbt keinen Retentionbesitz. Unprivilegierte Linux-POSIX-Proben und `HardeningLocalJobIT` bestätigen Rechte, Marker und beide Prozesseinstiege.
- F2-01 bis F2-08 sowie R1/R2: lokale Task-/Invocationbudgets, Predicate/Konfiguration/Return/Close-Grenzen, Catch-Bindung, Erstfehlerpräzedenz und gemeinsame Cleanupnachfrist sind erhalten. Gespeicherte Token-/Budgethandles frieren ihren eigenen Abschlusssnapshot ein; reine Abfragen latchen keinen fremden Cancel. `cancelReason` und `cancel_reason` werden in echten Reports/JSONL geprüft.
- F3-01 bis F3-07 sowie R3: abgelehnte exklusive Leaf bleibt Startfehler mit Code 2 ohne Run-/Cleanup-/Retentionbesitz; spätere Task-/Invocationsetupfehler durchlaufen Cleanup und Report genau einmal. Echte JSONL-/Reportfelder tragen `task-setup`.
- F4-01 bis F4-08: Run-/Task-/Invocationscopes sind lexikalisch und reversibel; fremdes MDC bleibt erhalten; Logback übernimmt den Ursprungssnapshot und -zeitpunkt; fremde Threads und alte Same-Thread-JUL-Records erhalten keine falsche Arbeitsidentität; Causes/Suppressed und Secretmaskierung bleiben erhalten; synchrone JUL-Meldungen bleiben exakt einmal richtig korreliert.
- G-01 bis G-06: öffentliche Task-SPI und Modulrichtung bleiben unverändert, `RunEventSink` bleibt Lambda-kompatibel, der terminalfilternde `JobRuntime`-Adapter delegiert `openScope`, genau ein SLF4J-Provider bleibt im Paket, und der externe Testprovider läuft ohne Testclasspath in beiden Produktpfaden.

Damit besitzen alle 62 spezifizierten Prüffälle einen nachvollziehbaren Code-/Test-/Artefaktbeleg. Insbesondere werden verschachtelte/deferred Scopes, MDC/JUL, fremde Threads, fehlerhafte Hooks, Cause-/Secretbehandlung und die tatsächliche JobRuntime-Scopeweiterleitung für SCRIPT/JAR abgedeckt.

## Findings

Keine offenen Findings am Kandidaten `9d6e26e1d9bfa5916a8d5c5a321d134ca5237447`.

## Grenzen und Vorbehalte

- Das Implementierermodell ist nur als GPT-6-basiert dokumentiert; ein exakter Providername ist nicht attestiert. Der Reviewer lief nach Laufzeitkonfiguration als anderes Modell `gpt-5.6-sol` in einer separaten Sitzung.
- Rechte `0700`/`0600` werden nur für POSIX behauptet. Der portable ZIP-Dateisystempfad ist geprüft; ein echter Windows-ACL-Lauf war nicht Bestandteil dieses Gates.
- Cancellation bleibt kooperativ; es wurde kein harter JVM-Threadstopp versprochen oder eingeführt.
- Automatische Threadkontextübertragung wurde bewusst nicht eingeführt. Unbekannte Threads und nicht beweisbare JUL-Ursprünge fallen auf run-only zurück.
- Ein echter IMAGE-/OpenShift-Lauf war nach Spezifikation nicht gefordert; die vorhandenen Adaptertests bleiben erhalten.

Diese Grenzen sind im Vertrag vorgesehen und verhindern das PASS nicht. Die Freigabe gilt exakt für den oben genannten Kandidaten sowie die geprüften Produkt-, Test-, Build- und Vertragsstände. Nachträgliche Änderungen erfordern einen passenden neuen Lauf.

### Evidenzarchiv-Beifang

Der Archivhelper iteriert eine breitere Rootverzeichnisliste als der aktuelle Maven-Reaktor. Deshalb enthalten beide Archive historische `target`-Caches. Im Standardarchiv sind 651 XML-Ausführungen enthalten: 51 aus `gretl-basic-tasks`, 23 aus `gretl-core-tasks` und 4 aus dem im Standardprofil nicht aktiven `gretl-geotools-probe`; nach deren Ausschluss verbleiben exakt die auditierten 573. Im Vollarchiv ist der GeoTools-Probe aktiv; von 702 XML-Ausführungen sind deshalb nur die 74 aus `gretl-basic-tasks` und `gretl-core-tasks` auszuschliessen, womit exakt 628 verbleiben.

Diese historischen Dateien werden ausdrücklich nicht als Ausführung oder Nachweis des Kandidaten behauptet. Die Qualifikation stützt sich auf die aktuellen Buildlogs, die SHA-/Dirty-gebundenen Audits und die Testreports der im Root-POM aktiven Module. Der Beifang ist eine Transparenzschwäche des Evidenzarchivs, aber kein Produkt- oder Testfehler und ändert das PASS nicht; Originalarchive und ihre Hashes bleiben unverändert.
