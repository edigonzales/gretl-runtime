# Abschlussqualifikation F1–F4

**Beide unabhängigen Gates sind PASS; alle 62 Prüffälle sind belegt.**
Geprüfter Gesamtkandidat: `9d6e26e1d9bfa5916a8d5c5a321d134ca5237447`, beim Build und
Review sauber. Ausgangsstand: `bc044302829ede67ead27038bfdf2be90d0b0063`.
Spezifikation 1.1 blieb unverändert, SHA-256:
`2b5df1aaaaf614184f26b657be1e4b3c567f9ee5c393df09c1faf9d169febeab`.

## Ergebnis je Finding

| Finding | Code und Verhalten | Konkrete Regressionen | Unabhängiger Beleg |
| --- | --- | --- | --- |
| F1 | Bestehende Korrektur ALREADY_FIXED erhalten: `WorkspaceManager`/`PrivateDiagnosticFiles`; keine Änderung bestehender Eltern, exklusiver Besitz neuer Leafs, private Diagnosedateien | alle TC-F1-01..10; `WorkspacePlatformTest`, Ownership-/Retentiontests, `HardeningLocalJobIT`; echter Linuxprozess UID/GID 65534, Symlinkroot, Owner/Group und ZIP-Dateisystem | Gate A PASS; Gate B bestätigt |
| F2 | `RunControl` bleibt bei reinen Abfragen nebenwirkungsfrei; `BaseContext` bindet ausgegebene Handles an Scope/Snapshot; gemeinsamer `FailureArbiter`; konkrete Cancel-Ursachen in Outcomes, Report und Terminalevents; Taskbudget vor Startregistrierung | `ScopedControlTest`, `LocalBudgetRegressionTest`, `BudgetPrecedenceTest`, `CancelCauseReportTest`, reale Script-/JAR-Prozesse | Gate A schliesst R1/R2 und A-F2-01; Gate B bestätigt |
| F3 | `SequentialJobRunner`/`RunStartException`/`JobRuntime`: Leaf-Startfehler Code 2 ohne Run/Cleanup; nach Runbeginn kontrollierter Setupfehler mit `task-setup`, Cleanup und einmaligem Bericht/Abschluss | `StartBoundaryTest`, `SetupFailureTest`, `SetupCompletionTest`, `HardeningLocalJobIT`; kombinierte Reviewerprobe mit zusätzlichem Reportfehler | Gate A schliesst R3; Gate B bestätigt |
| F4 | Backendfreier Scopehook und delegierender JobRuntime-Adapter; `MdcExecutionScope` restauriert eigene Schlüssel; `LibraryIdentity`/Bridge behalten Ursprungssnapshot und Logbackzeit; JUL-Guard verwirft fremde/veraltete Arbeitszuordnung | `LibraryScopeTest`, `LibrarySnapshotTest`, `LoggingScopeTest`; `LibraryScopeLocalJobIT` mit regulärem SPI-JAR, SCRIPT und isoliertem Job-JAR; eigene Reviewerprobe zur JUL-Grenzlebensdauer | Gate B PASS nach Schliessung von GB-F4-01 |

Die vollständige Zuordnung jedes Orakels zu Tests steht in der
[Abnahmematrix](acceptance-matrix.md). Öffentliche Task-API, DSL, Modulrichtung,
Produktdependencies, Taskkatalog und selektive Paketierung sind erhalten.

## Tatsächlich ausgeführte Qualifikation

Host: Temurin **25.0.3+9-LTS**, Repositorywrapper **Maven 3.10.0**.
Alle Befehle liefen am oben genannten eingefrorenen Kandidaten:

```sh
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./mvnw -B clean verify
python3 scripts/audit-job-evidence.py --source 9d6e26e1d9bfa5916a8d5c5a321d134ca5237447
# Standardarchiv gesichert, bevor der folgende clean startete.
JAVA_HOME=/Users/stefan/.sdkman/candidates/java/25.0.3-tem ./mvnw -B -Pgeotools-probe,backing-services clean verify
python3 scripts/audit-job-evidence.py --full --source 9d6e26e1d9bfa5916a8d5c5a321d134ca5237447
```

| Pfad | Tests | Failures / Errors / Skips | Audit |
| --- | ---: | --- | --- |
| Standard | 573 | 0 / 0 / 0 | PASS; 20 Business- und 2 Fixture-Operationen |
| Vollprofil | 628 | 0 / 0 / 0 | PASS; alle 44 bestehenden Businessoperationen plus 2 Fixtures |

Der Vollpfad nutzt echte PostgreSQL/PostGIS-, FTP- und Floci/S3-Dienste sowie die
GeoTools-Probe. Die zusätzliche POSIX-Qualifikation lief netzlos als UID/GID 65534
mit gepinntem Linux-/Temurin-Image (JDK 25.0.4.1+1-LTS). ZIP-FS belegt den portablen
Nicht-POSIX-Pfad. Paketprozesse laufen mit fremdem CWD, ohne ursprüngliche Quelle
und ohne Maven im Runtime-PATH. Der Testprovider gelangt regulär über ServiceLoader
ins Auswahlpaket; genau ein SLF4J-Provider bleibt erhalten.

## Herkunft und Artefakte

- Gate A: Modell `gpt-5.6-sol`, separate Sitzung `/root/gate_a_v11`, PASS für
  `b7ada7598e2750112836162827c36d15586aacee`; [Review](phase-a-review-revision-1.md).
- Gate B: Modell `gpt-5.6-sol`, neue separate Sitzung `/root/gate_b_v11`, PASS für den
  Gesamtkandidaten; [Review](phase-b-review-revision-1.md). Eigenständige Gegenproben,
  echte Produktstarts und individuelle Prüfung beider Qualifikationsarchive.
- Implementierer: nach Laufzeitinstruktion GPT-6-basiert; exakter Providerbezeichner
  unbekannt. Es wird keine Providerattestierung erfunden. Die Modellfamilien sind
  verschieden; dieser Herkunftsvorbehalt steht in den Reviews.

Die frühen FAIL-Berichte bleiben erhalten. Der erste Gate-A-Befund zum Budgetstart
und der erste Gate-B-Befund zur Same-Thread-JUL-Weiterleitung wurden korrigiert,
mit roten/grünen Orakeln erneut getestet und unabhängig nachgeprüft.

Große Rohartefakte liegen ausserhalb Git unter
`/Users/stefan/Downloads/gretl-runtime-hardening-v11-9d6e26e-nachweise`.
Die ursprünglichen Laufpfade `/tmp/gretl-hardening-v11-evidence` bleiben in historischen
Berichten unverändert; die dauerhafte Kopie hat dieselben Dateinamen und Bytes.
Der [Evidenzindex](evidence-index.json) enthält Kandidaten, Befehle, Report-/Manifest-/
Archivhashes und Modell-/Sitzungsbindung.

| Archiv | SHA-256 |
| --- | --- |
| `phase-b-9d6e26e-standard.tar.gz` | `5eb86d245bcb4e4d9cf7c6863a1e0a36b6cab7392db841c02ce90ecee2f932dc` |
| `phase-b-9d6e26e-full.tar.gz` | `31c25a41f258aae292dd759af995f57ff61017f8511d209f5415dfafa2297917` |

Die Roharchive enthalten zusätzlich alte `target`-Berichte der heute nicht aktiven
Module `gretl-basic-tasks` (51) und `gretl-core-tasks` (23); das Standardarchiv enthält
auch vier dort nicht aktive GeoTools-Tests. Dieser historische Archivbeifang ist
**kein Kandidatennachweis**. Gewertet werden ausschließlich die aktiven Reaktormodule:
573 Standard- und 628 Vollprofiltests, wie vom Audit und Reviewer separat geprüft.

Dieser nachfolgende Commit ändert nur Berichte/Evidenzindex und deren Status.
Produkt-, Test-, Build- und Vertragsstand verbleiben am geprüften Kandidaten;
die ausdrückliche Dokumentationsausnahme in Spezifikation 9.4 gilt.

## Verbleibende vertragliche Grenzen

Cancellation bleibt kooperativ. POSIX-Modi werden nur auf POSIX behauptet; ein echter
Windows-ACL-Lauf war nicht gefordert. Automatische Threadkontextübertragung ist nicht
eingeführt. IMAGE-/OpenShift-Produktstarts waren ausserhalb dieses Auftrags; die
vorhandenen Adaptertests sind erhalten. Es gibt keine offenen Pflichtfindings.
