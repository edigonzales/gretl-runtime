# Unabhängiger Prüfbericht – Phase 1, Revision 2

Phase: 1 – Funktion und Nachweise absichern  
Prüfdatum: 25. Oktober 2026  
Implementierungsagent: Agent A (`/root`)  
Unabhängiger Prüfagent: Agent B (`/root/phase1_review`), keine Implementierungsbeteiligung  
Ausgangscommit: `230f2882bcdde15f552f7959aed157a0a6301fa6`  
Geprüfter Kandidatencommit: `52675c526a0edad52d7005c1d7e874fb7d3ca276`  
Eigene Nachweise: `/tmp/gretl-phase1-revision-2-review-evidence/`

**Urteil: PASS.** Alle zwölf Phase-1-Kriterien sind erfüllt. Beide verbindlichen Rootprüfungen wurden vom unabhängigen Prüfer auf genau diesem Kandidaten neu ausgeführt und bestehen ohne Fehler oder übersprungene Tests. F1, D1 und F2 sind geschlossen. Phase 2 darf auf Grundlage dieses ausdrücklichen Phase-1-PASS beginnen.

## Prüfstand, Unabhängigkeit und Umfang

Zwei neue, getrennte detached Checkouts wurden vor und nach der Prüfung auf exakt den Kandidaten-SHA und sauberen Arbeitsbaum geprüft:

- Standard: `/tmp/gretl-phase1-review-52675c5-standard`.
- Vollprofil: `/tmp/gretl-phase1-review-52675c5-profiles`.

Keine Produkt-/Testdatei wurde durch den Prüfer geändert. Zusätzliche Programme und eigene Outputs liegen ausschliesslich im externen Nachweisverzeichnis. Die beiden Rootprüfungen liefen parallel mit getrennten Targets und Jobprozessen; ausschliesslich das Profil startete Pflichtdienste. Kein `clean` konnte daher die Reports des anderen Laufs entfernen. Die fertigen Reports, Coverage-Dateien und tatsächlich gestarteten kanonischen JARs wurden nach `standard/` beziehungsweise `profiles/` gesichert. Es gab keine Wiederholung eines fehlgeschlagenen Maven-Prüflaufs.

Beim ersten Startversuch des Profils war das gemeinsame Logverzeichnis wegen einer Parallelisierungsreihenfolge noch nicht vorhanden. Die Shell scheiterte bereits an der Ausgabeumleitung, bevor Maven startete. Nach Anlegen des Verzeichnisses wurde genau ein tatsächlicher Profil-Mavenlauf gestartet. Dies war ein Aufbaufehler des Prüfharnesses, kein fehlgeschlagener oder wiederholter Testlauf.

Repository-Anweisungen, vollständiger aktueller Plan mit 34 Kriterien und die Phase-1-Verträge, besonders Spezifikationen 03, 04, 06, 07, 09, 12, 14, 15 und 16, wurden in der ersten Prüfung selbst gelesen. Der erneute Bytevergleich bestätigt alle 19 Dateien unter `docs/spec/` und den vollständigen Plan als unverändert. Das vollständige Diff zur Ausgangsbasis und das Revisionsdiff zu `df0f00d7f74ea285c7c9eb476389d8cb55ee4aa2` sind geprüft und als `phase1.diff`/`revision.diff` gesichert.

Das Revisionsdiff ändert zwei FTP-Test-/Fixturedateien, die Testanleitung und zwei Berichtsdokumente. **Kein Produktcode und keine POM ändern sich seit dem bereits geprüften F1-korrigierten Kandidaten.** Frühere eigene Zusatzproben unveränderter Verträge werden unten als solche eingeordnet. Die Pflichtbuilds, aktuellen Artefaktaudits, tatsächliche CLI-Hilfe und F2-Probe stammen dagegen aus dieser Revision. A-Berichte wurden nur als Orientierung verwendet.

Der erste historische FAIL-Bericht ist byteidentisch. Beim zweiten fehlt im Produktkandidaten genau eine Tabellenzeile zur nicht ausgeführten GeoTools-Gruppe; der nachfolgende Text weist deren vier fehlende Ausführungen weiterhin ausdrücklich aus. Die externe finale Datei bleibt unverändert. Agent A bestätigte die zu frühe Kopie und ergänzte danach im reinen Dokumentationscommit `a1bb5bbf1c8a833a5e7835a9ed9408b726991e68` genau diese Zeile. Der Produktprüfstand bleibt unverändert. `prior-review-document.diff` belegt die Abweichung. Die historischen FAIL-Urteile werden nicht umgedeutet.

## Prüfumgebung und eigene Befehle

Temurin OpenJDK **25.0.2+10-LTS**, Maven Wrapper **3.10.0**, Groovy **6.0.0**, macOS **27.0.1 ARM64**, UTF-8, Hostlocale en_US, Europe/Zurich. Docker Desktop **4.93.0**, Engine **29.8.1**, Linux ARM64. Jobprozesse verwenden UTC und deutsche Sprache; Testclasspath-/JVM-Injektionsvariablen werden entfernt. Der festgelegte PostgreSQL/PostGIS-AMD64-Testcontainer läuft unter Docker-Desktop-Emulation; die eigene FTP-Probe nutzt das gepinnte ARM64-Testimage. Vollständiger Beleg: `environment.txt`.

Verbindliche, selbst ausgeführte Befehle, jeweils im zugehörigen sauberen Checkout:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Beide enden mit **BUILD SUCCESS**. Vollständige Logs: `standard.log` und `profiles.log`. Zusätzlich selbst ausgeführte externe Prüfprogramme:

```sh
python3 /tmp/gretl-phase1-revision-2-review-evidence/help-probe.py
python3 /tmp/gretl-phase1-revision-2-review-evidence/read_ftp_image.py
python3 /tmp/gretl-phase1-revision-2-review-evidence/ftp_foreground_probe.py
python3 /tmp/gretl-phase1-revision-2-review-evidence/own_output_negative.py
python3 /tmp/gretl-phase1-revision-2-review-evidence/preserve.py standard
python3 /tmp/gretl-phase1-revision-2-review-evidence/preserve.py profiles
python3 /tmp/gretl-phase1-revision-2-review-evidence/audit_original_assertions.py
python3 /tmp/gretl-phase1-revision-2-review-evidence/audit_artifacts.py standard profiles
python3 /tmp/gretl-phase1-revision-2-review-evidence/audit_regressions.py
python3 /tmp/gretl-phase1-revision-2-review-evidence/audit_outcomes.py standard profiles
python3 /tmp/gretl-phase1-revision-2-review-evidence/audit_coverage.py
```

Keine Abnahme verwendet einen nachträglichen Build mit übersprungenen Tests.

## Je Abnahmekriterium

| ID | Ergebnis | Eigener Nachweis; Finding/Korrektur |
|---|---|---|
| P1-01 | PASS | Baseline, 44er-Operationeninventar und abgegrenzte Kompatibilitätsmatrix erneut gegen unveränderte Dokumente geprüft. OperationMetadataTest besteht in beiden neuen Läufen. Zusicherungen und bewusst nicht übernommene Optionen, besonders schmale IliValidator-/SqlExecutor-Verträge, sind getrennt. gretl-next-Referenz: 08bd11e97a54f32f8702d34ff16fb39bec6ea046. |
| P1-02 | PASS | ValidatorLocalJobIT startet neue JARs: gültige CSV ohne Tabelle, GPKG mit Tabelle, fehlende/falsche Tabelle und unzulässiges CSV-Feld. Konkrete Logs/Diagnosen geprüft. Frühere eigene zusätzliche leere/falsche Tabellenwerte ergänzen den unveränderten Codebeleg. |
| P1-03 | PASS | Alle vier Validatorarten mit Path- und String-Repositories auf neuen Artefakten. Ungültige Werte werden vor Enginearbeit konkret abgewiesen. Frühere eigene Steuerzeichenprobe ergänzt den unveränderten Konvertervertrag. |
| P1-04 | PASS | Neue Import-JARs mit 2056 und 21781; unabhängige SQLite-Abfragen prüfen tatsächliche GPKG-Daten/SRID. Eigener neuer Hilfe-JAR bestätigt 2056 und exakte Zeilen. Frühere eigene Polygon-/WKB- und ungültige SRS-Proben bleiben ergänzend belegt. |
| P1-05 | PASS | DatabaseArtifactIT mit realem PostgreSQL/PostGIS: laufende Abfrage als beobachteter Checkpoint, Db2Db bleibt CANCELLED/20, Folgetask NOT_RUN, Zielzeilen/Transaktionen geprüft. Fehlerprioritätsregressionen bestehen. Frühere eigene SQL-Fehler-/Rollback-/Sessionprobe ergänzt dies. |
| P1-06 | PASS | Alle acht CancellationLocalJobIT-Fälle neu: Script/JAR, Interrupt, SIGTERM, Timeout/Reaping und echter Fehler vor SIGTERM. Konsistente Terminaldaten/Reports; OS 143 wird von GRETL 20 getrennt. RunControlTest prüft das gemeinsame 30-s-Budget. Frühere eigene Prozesse mit sekundärem Cleanupfehler ergänzen dies. |
| P1-07 | PASS | CorrelationTest, Konfiguration, Logging-/Redaction- und Reportregressionen erneut erfolgreich. Vorrang, Wertevalidierung und getrennte Läufe belegt. Frühere eigene echte JAR-Fälle für ungültigen ENV-Fallback unter gültiger Konfiguration sowie Provider-Überschreibversuche betreffen unveränderten Code. |
| P1-08 | PASS | Tatsächliche neue CLI-Hilfe unverändert paketiert/gestartet, Daten/SRID unabhängig via SQLite geprüft: F1 geschlossen. Öffentliche DSL-/DB-Artefaktfälle mit konkreten Daten-/Fehlerorakeln. Nicht zugesicherte DuckDB-/PostGIS-Geometrie konkret DUCKDB_GEOMETRY_UNSUPPORTED, kein stiller Verlust. Früherer eigener Geography-/Koordinaten-/SRID-Nachweis ergänzt dies. |
| P1-09 | PASS | Eigene neue Line-/Branchberichte für alle13 produktiven Module, JaCoCo 0.8.14 gepinnt. Aggregation und Grenzen einschliesslich uninstrumentierter Subprozesse unten ausgewiesen. |
| P1-10 | PASS | Standard 413/0/0/0 und Vollprofil 447/0/0/0, beide BUILD SUCCESS. PostgreSQL/PostGIS, S3 und FTP tatsächlich ausgeführt, GeoTools 4 ebenfalls. Kein Pflichtfall übersprungen/deaktiviert. F2 durch neue Fixture, eigenes Image-/Prozessorakel und erfolgreichen Pflichtlauf geschlossen. |
| P1-11 | PASS | Erneut386 Original-Job-/Fixturedateien mit SHA-256/Byteumfang,86 aktive Methoden und ein historisch inaktiver FTP-Fall und 205 Originalassertionen abgeglichen. D1 korrigiert. Keine relevanten Szenarien ohne Ziel/Scope; offene Phase-2-Varianten bleiben ausdrücklich offen. |
| P1-12 | PASS | Gemeinsamer Runner/Fixtures auf neuen Artefakten: Datei-/Transformationsjob, echte DB-Kette, fachlicher Negativfall und korrigierte DSL-Fälle als separate regulär paketierte JARs. Argumente, Prüfsummen und alle 469 Produktklassen aus elf Laufzeitmodulen selbst geprüft. Neue absichtlich falsche/fehlende Outputs und beschädigtes JAR werden zurückgewiesen. |

## F2 – eigene Prüfung der FTP-Korrektur

Gepinntes Testimage:

`docker.io/delfer/alpine-ftp-server@sha256:60bb774d8408d9d4d5c74d05d1c086a34ce192c6c1a142ffac268cac0dbc6fac`.

Der Prüfer hat die tatsächlichen Startdateien aus genau diesem Image gelesen: `docker create` ohne Start, `docker cp` von `/bin/start_vsftpd.sh` und `/etc/vsftpd/vsftpd.conf`, danach Entfernen des nur erstellten Containers. `ftp-image/extraction.json` belegt Status=created, Running=false und PID0. Entrypoint: `/sbin/tini -- /bin/start_vsftpd.sh`. Die Konfiguration enthält background=YES; der Defaultzweig schreibt `pgrep vsftpd | tail -n 1` als überwachte PID und startet pidproxy. Ein kurzlebiger Verbindungsworker kann damit gewählt werden. Dieser Mechanismus ist aus dem gepinnten Dateisystem selbst belegt; der genaue damals ausgewählte PID des bereits entfernten fehlerhaften Containers wird dadurch nicht nachträglich bewiesen.

Der neue explizite Befehl `vsftpd /etc/vsftpd/vsftpd.conf -obackground=NO ...` aktiviert den Custom-command-Zweig nach der unveränderten Benutzerinitialisierung. Dieser macht `exec "$@"`; tini beaufsichtigt damit direkt den langlebigen Foreground-Master. Passive Adresse und ein gemappter passiver Port werden explizit gesetzt. Die Fixture hält den Container final, der Lease-Neustartfallback ist entfernt. Cleanup-Verbindungsfehler bleiben fatal und sichern das Serverlog.

Eine eigene unabhängige ftplib-Probe startet genau dieses gepinnte FTP-Testimage mit dem neuen Foregroundbefehl: dreimal falsche Anmeldung, jeweils konkrete Antwort 530; danach jeweils gültiger Login 230 samt Listing. Container und Master-PID bleiben identisch. Master PID 7, Parent PID 1 unter tini; kein pidproxy, Container anschliessend Running. Kein Neustart während der Probe. Erst nach Abschluss wird der eigene Container gestoppt. Vollständige Belege: `ftp-foreground/evidence.json`, Prozesslisten, Serverlog und Prüfharness.

Der neue dauerhafte Negativtest verlangt FtpTaskException, die konkrete Diagnose `FTP-Anmeldung fehlgeschlagen`, Secretfreiheit auch im Report, danach unabhängiges `lease.list()` und gültigen Runtime-Login auf derselben finalen Fixture. Im eigenen vollständigen Pflichtlauf bestehen alle vier FTP-Tests samt unverändert fatalem Cleanup. Ein beliebiger Start-/Verbindungsfehler darf damit den fachlichen Negativfall nicht ersetzen.

Die optionale 2-s-Docker-Beobachtung der kurzlebigen Maven-FTP-Fixture erfasste keinen Container und wird nicht als Prozessbeleg verwendet. Die vier Tests dauerten 3.470 s. Die separate eigene Foregroundprozess-/Loginprobe ist vollständig gesichert. Endlich viele grüne Läufe beweisen keine allgemeine Flakefreiheit; Startmechanismus, konkrete Umgehung und erfolgreiche Pflichtfälle sind hier belegt.

## F1 und D1 – erneute eigene Regression

`operations Ili2gpkgImport --lang de` wird gegen die neue gebaute Distribution ausgeführt. Sämtliche Bytes nach `Beispiel:` werden unverändert als job.groovy übernommen; keine Zeile, kein Ziel und kein Default wird ergänzt. Regulärer Packager, anschliessend separater java-jar-Prozess aus fremdem Arbeitsverzeichnis ohne Testclasspath. Das tatsächlich ausgegebene Rezept enthält databaseFile, dataFiles, modelDirectories, defaultSrsCode 2056, Outputparameter und defaults run.

Ergebnis OS 0/GRETL SUCCEEDED/0. Eigenes Python-SQLite-Orakel:

- gpkg_geometry_columns exakt boflaechen/form/CURVEPOLYGON/2056.
- boflaechen exakt `(5.0, null, null)` und `(null, 'Gebaeude', null)` für Fläche/Art/Form.
- gebaeudeart exakt die drei Originaleinträge einschliesslich des mehrzeiligen Beschreibungstexts.

Das Original-XTF besitzt keine befüllte optionale Geometrie; dieser Fall behauptet keinen Koordinatentransformationsbeweis. Die eigenständige frühere Polygon-/WKB-Probe ist unten getrennt eingeordnet.

Quellbytes SHA-256: `4a615c556f129a4b7511bd3fcf98dd60482660e9ee13550f107b6a613b3918d8`.  
Tatsächlich gestartetes Hilfe-JAR SHA-256: `81ece14c9ca03a47287caa59904602821f860fc11a24a2c8499273b13bc00697`.

`actual-help-jar/` enthält rohe Hilfeausgabe, bytegleiche Quelle, Descriptor, Paketlogs, JAR, Argumentliste, Runtimekonfiguration, Prozesslogs, Runbericht, SQLite-Datei und Orakel. Manifest und betroffener Produktbytecode wurden mit dem Kandidatenbuild abgeglichen. Der dauerhafte neue Hilfe-JAR-Test besteht in beiden Rootläufen. D1 bleibt geschlossen: historicallyDisabled=true für den historischen FTP-Fall, keine Zählung als aktive Originalabdeckung.

## Originalzuordnung und eigene Gegenbeispiele

Originalreferenz: sogis/gretl@a9222dc0ffd08f8f76570e998101db041822df71, exaktes Archiv unter `/tmp/gretl-improvement-evidence/original-gretl/gretl/src/integrationTest`. Hashes aller 386 Job-/Fixturedateien und referenzierten Java-Tests erneut selbst geprüft.86 aktive Methoden und ein historisch inaktiver Fall sind zugeordnet; alle 205 Assertions mit string-/kommentarsensitivem Parser nochmals gegen Originalcode geprüft. Belege: `inventory-revision-audit.json`, `original-assertions.log`. Im ersten Review waren zusätzlich22 portierte Validatorfixturedateien selbst geprüft: elf bytegleich, elf nur Format-/Whitespaceanpassung. Archiv-XML bytegleich. Fachzweck, Originalerwartung, Ziel und begründeter Scope sind erfasst; volle 44er-JAR-Abdeckung wird noch nicht behauptet.

Folgende selbst gewählten früheren Gegenbeispiele unter `/tmp/gretl-phase1-review-evidence/independent2/` bleiben Evidenz unveränderter Produkt-/Orakelpfade. Sie werden ausdrücklich nicht als neue Befehle dieses Kandidaten ausgegeben:

| Vertragsfamilie | Früherer eigener Gegenbeleg und unabhängige Prüfung |
|---|---|
| Tabellen | table-negatives/: leere/falsche Tabelle, konkrete Konfigurations-/Validierungsdiagnosen. |
| Repositories | repository-control/: Steuerzeichenwert früh konkret abgewiesen. |
| SRS/GPKG | srs-probes/: ungültiger Code und2056; real-geometry/: eigener Polygon-XTF, GPKG-/WKB-Decodierung exakt fünf Koordinaten/HeaderSRID 2056. |
| DB/Geometrie | database3/oracle.json: reale Kette/Zeilen, bad SQL FAILED/10; Geography(Point4326) konkret abgewiesen, Originalkoordinaten/SRID unverändert. Cancellation GRETL 20/OS 143, Rollback und keine offene Session. |
| Shutdown | cancellation-negative/summary.json: Script/JAR mit SIGTERM und sekundärem Cleanupfehler; CANCELLED/20 oder bereits führender Fehler FAILED/10; ein Terminaldatensatz. |
| Korrelation | correlation/: Konfiguration über ungültigem ENV-Fallback, Provider-Überschreibversuch und getrennte Läufe. |
| Orakel | archive-oracle/: falsche erwartete Bytes und gelöschte Ausgabe werden zurückgewiesen; beschädigtes JAR ersetzt keinen fachlichen Negativfall. |

Der erneute Diffvergleich bestätigt unveränderte Produkt-/Orakelpfade; die aktuellen vollständigen Builds bestätigen deren Regressionen auf dem neuen SHA.

Zusätzlich auf dem aktuellen eigenen Hilfeoutput ausgeführt: absichtlich 999 erwartete BoFlaechen statt tatsächlich 2 erzeugen AssertionError. Absichtlich fehlendes GeoPackage wird vom Read-only-SQLite-Leser zurückgewiesen, keine neue Datei angelegt. Absichtlich beschädigtes JAR ergibt OS 1 ohne Runbericht. Das Geschäftsnegativprädikat fordert OS 10, realen FAILED-Report und konkrete erwartete Diagnose; es akzeptiert diesen Launcherfehler nicht. Belege: `own-output-negative/evidence.json` und Launcherlogs.

## Tests und übernommene Regressionen

| Aktiver Reactorbaustein | Standard | Vollprofil |
|---|---:|---:|
| gretl-api | 17 | 17 |
| gretl-basic-tasks | 51 | 51 |
| gretl-cli | 62 | 62 |
| gretl-core-tasks | 23 | 23 |
| gretl-dist | 11 | 11 |
| gretl-geotools-probe | 0 | 4 |
| gretl-groovy | 49 | 49 |
| gretl-i18n | 4 | 4 |
| gretl-ilivalidator | 3 | 3 |
| gretl-integration-tests | 26 | 56 |
| gretl-interlis-tasks | 25 | 25 |
| gretl-logging | 18 | 18 |
| gretl-packager | 8 | 8 |
| gretl-runner | 116 | 116 |
| **Gesamt** | **413** | **447** |

Beide: **0 Failures, 0 Errors, 0 Skips**. Es gibt noch keine abgenommene frühere Phase; bestehende Regressionen der Ausgangsbasis und des ersten Kandidaten bleiben erhalten. `regression-audit.json` vergleicht Testklasse und Testfallname mit den eigenen ersten 412/446 Reports: keine fehlenden Fälle, genau der veröffentlichte Hilfe-JAR-Test hinzugekommen. GeoTools 4 wird diesmal im Profil tatsächlich ausgeführt, nicht aus alten Targets übernommen. Im Standard zählen ausschliesslich aktive Reactormodule. Pflichtdienste wurden nicht durch Assumptions oder Mocks ersetzt.

## Coverage-Messumfang und Grenzen

JaCoCo 0.8.14 erzeugt Line-/Branchberichte je produktivem Modul und ein Aggregat mit allen 13 produktiven Gruppen. XML/HTML beider Läufe sind gesichert. Profilaggregat:

| Produktives Modul | Lines covered/total | Branches covered/total |
|---|---:|---:|
| gretl-api | 110 / 114 | 23 / 26 |
| gretl-runner | 1796 / 2078 | 754 / 1061 |
| gretl-groovy | 498 / 733 | 148 / 238 |
| gretl-i18n | 24 / 31 | 13 / 20 |
| gretl-logging | 923 / 1103 | 385 / 590 |
| gretl-packager | 508 / 698 | 204 / 310 |
| gretl-job-runtime | 0 / 150 | 0 / 52 |
| gretl-dist | 20 / 26 | 5 / 8 |
| gretl-cli | 1080 / 1326 | 379 / 561 |
| gretl-basic-tasks | 2058 / 2647 | 732 / 1240 |
| gretl-core-tasks | 1099 / 1382 | 288 / 488 |
| gretl-ilivalidator | 115 / 175 | 20 / 42 |
| gretl-interlis-tasks | 2824 / 3667 | 1092 / 1825 |

`coverage-summary.json` enthält zusätzlich Standardwerte. Instrumentiert sind Surefire-/Failsafe-JVMs und darin geladene Produktklassen. Separate Distributionslauncher-, Packager- und java-jar-Job-JVMs sind bewusst uninstrumentiert. Daher zeigt besonders job-runtime trotz tatsächlicher JAR-Ausführungen0/150 Lines und0/52 Branches. Subprozesscoverage wird nicht geschätzt oder In-Process-Quoten zugeschlagen. Diese dokumentierte Messgrenze ist weder 100%-Behauptung noch fehlender Artefaktnachweis.

## Jobtest-Szenarien und tatsächliche JAR-Identitäten

Eigener Audit: **53** kanonische separate Ausführungen, davon **45 JAR** und **8 SCRIPT**, zusätzlich der eigene neue Hilfe-JAR. JARs regulär über die gebaute Distribution paketiert und als java-jar gestartet. command.json enthält keinen Testclasspath oder cp/classpath-Ersatz beim Jobstart. Main-Class: ch.so.agi.gretl.job.JobJarMain. Packageraufrufe nutzen erwartungsgemäss den Kandidaten-Distributionsclasspath; diese sind vom eigentlichen Jobprozess getrennt.

Für jedes JAR SHA-256 neu aus tatsächlichen Bytes berechnet und mit execution.json abgeglichen. Alle 469 Produktklassen aus elf Laufzeitmodulen sind in jedem JAR bytegleich zu target/classes des jeweiligen eigenen Checkouts. Packager/Distribution sind keine enthaltenen Joblaufzeitmodule. Keine Identitätsabweichung. Timeout-/Abortfälle sind eigens markiert und auf Reaping/CANCELLED geprüft.

Der Outcomeaudit prüft alle kanonischen Reports, konkrete Geschäftsdiagnosen, genau einen korrelierten Terminaldatensatz, konsistente GRETL-Status/Exitcodes, beabsichtigte OS 143-Signalexits sowie echte Outputs. GPKG-Daten nochmals via SQLite, Original-Gzipbytes und konkrete CSV-Diagnosen geprüft. Die folgenden SHA-Werte bezeichnen tatsächlich gestartete JAR-/Scriptbytes, keine nachträglichen Ersatzartefakte:

| Build | Evidenzverzeichnis/Szenario | Modus | SHA-256 | OS-Exit |
|---|---|---|---|---:|
| standard | cancel-false-false-0c9b09c4-8eeb-4e17-bbe9-3846e83da317 | JAR | `e6ede730db341f08e0de3ae8dc6af8a7245e44c5272da63b700697cb53487df4` | 20 |
| standard | cancel-false-true-e3aaa397-c6b2-4dde-a0f5-c7d78d723fd8 | JAR | `78793375986191792fcd3ce5e736b5e8579d0235b9718f3f8edfe50030a42e99` | 143 |
| standard | cancel-true-false-112777f4-43d1-4379-818c-3f0420e3a192 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 |
| standard | cancel-true-true-44f45b8e-8939-40b7-ad61-bd9421268fb0 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 |
| standard | csv-unsupported-table-b33faf9f-e1d4-4370-86ac-468166efda9a | JAR | `5601fdff52597336b435f9c05e645a9def2b11334411121deab91f6671b8b681` | 10 |
| standard | failed-shutdown-false-4f875444-258f-4f61-a34f-85e8b8da3ab8 | JAR | `d085d2679d18652fe332af3e5eba3826868dedf08c4abc5e0a33fa4719b98f82` | 143 |
| standard | failed-shutdown-true-1a869e40-3df4-4caf-8f79-de245323f6e7 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 |
| standard | gpkg-help-example-299b84e2-3dda-4fec-bcd1-452caf21d151 | JAR | `42994f10f400541bb79e93ae9a170921793198394921700cc1ed5f8ed7dd7222` | 0 |
| standard | gpkg-missing-table-3d6ff6c3-a97a-496a-acf5-f385fb1da4ed | JAR | `73b807e08267c04c44ce17ba1c5b704a6f434c7ff2d805a128287d7678815e6f` | 10 |
| standard | gpkg-srid-c469ea7b-adc0-42d7-8a42-854912817b2d | JAR | `df2376e62596383e8810b1c93ec434202d9d5946b2c6f0e5405761b3f13826de` | 0 |
| standard | gpkg-srid-fa61da07-aeef-4ecf-a4b6-d9ecd8574996 | JAR | `aed660373804bd26f18789c9e5080b6554d0c4564da431a2e234a07897447fd4` | 0 |
| standard | invalid-csv-6f00048a-4d24-454f-afd3-b5a13e42d03f | JAR | `384e27716808e13f953655ad12ab2476a33889c075e78dbdb4d6f71d6c354e1d` | 10 |
| standard | invalid-model-repository-c70171ae-2317-4335-9073-77b5a7f6f00a | JAR | `87c9322a5650c07bf436866aaff166648834ee502a5a434c12f3428b83eabcc2` | 10 |
| standard | original-gzip-c06debdd-5732-4679-98c3-3808d03a1ad4 | JAR | `e7c3b629490f073f7af9d93175dd295a4d135b6eacb946ddac3a6c5e4fe5ec46` | 0 |
| standard | runner-timeout-false-0ed44eae-be5d-4351-bcb3-9be830087541 | JAR | `51e3ce7895ccb4ea9bc7661b372dc87f76689e3895c02545da8144727acdf656` | 143 |
| standard | runner-timeout-true-d156bb5e-26ab-4325-a213-cf058876f0fe | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 |
| standard | valid-csv-path-ea498ae7-d332-4c05-9752-21a1dc83acd1 | JAR | `672d96058a36002632def8d216ad539cf297d4812219af66c7d9b8d2d945f544` | 0 |
| standard | valid-csv-string-60637862-ce21-4f9c-a58a-d973ab8722c7 | JAR | `fa63d4ab85d0faf95fa607c75a59a219a946eb16eebd24065584481436dcec37` | 0 |
| standard | valid-gpkg-path-cfb073f1-9e2a-4c94-bbd7-89b1edd85c5a | JAR | `bfeade93443bb6133bfefb6d8aab7b5d11efefcf6497587277a4b9c7ccf8f8b1` | 0 |
| standard | valid-gpkg-string-6896fc93-c1d6-471e-9737-0edcd3483a74 | JAR | `b6f3b4c8ca08ecac6269ed8bd3095530a9239243bdb0c18fd022772073df04a1` | 0 |
| standard | valid-json-path-093531b5-aad6-45b5-9b66-dce5f2875db6 | JAR | `de2443c2363287032f62246d1f073f63f0960e1d39cf05623e1149b6bc29edc7` | 0 |
| standard | valid-json-string-c27d8975-9d0c-4461-9f1d-3e0e735227dd | JAR | `57afa4ae0710bcba047b769b7325b334b2fe5f2db8f4141eb2b5a04d0a25eda1` | 0 |
| standard | valid-shp-path-4240155c-11d2-4241-94e1-cea0f9ea0fc6 | JAR | `2c8efecd2864f2ed575523f8c88367bc2b7a5b066714905e55f3d19b75968bf0` | 0 |
| standard | valid-shp-string-6e436957-de2f-4a47-b951-8fbf61f782b9 | JAR | `809af6a4742494a05ad04d9b78de0ace1e244b5380a7c395b90c8c5cc39e9688` | 0 |
| standard | wrong-gpkg-table-9f1f57a9-a418-4c46-aca3-48d98825e4fb | JAR | `1a63f5003ace852eb079ecab506b77dca929db1de9afe7f533a0ff7db5a2b60c` | 10 |
| profiles | cancel-false-false-941ea233-5f7f-4e9a-a720-1c5fe60c64a8 | JAR | `2315651daa8311777e7ab377c509c256b0566aa62110cdeaeba0acc39488d16a` | 20 |
| profiles | cancel-false-true-9b2e0bd6-168a-4f03-9236-2b571a47ecba | JAR | `9e133df009419bb06633c28ea2a0941668bf00cef46527076a0f2579abb8e4ee` | 143 |
| profiles | cancel-true-false-96bb66fd-ffcf-4ec3-abdb-ee26264b076a | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 20 |
| profiles | cancel-true-true-dacf72e6-a0f4-498e-81fe-20d88cc3c767 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 |
| profiles | csv-unsupported-table-b87ba473-fa43-4d98-a07d-8aefbad04c73 | JAR | `d18982a4836d7a7c23305e6f08e9dd43abefe01200db76f18c5e16655df5fdee` | 10 |
| profiles | database-cancel-e2a5a7fe-ce90-48d2-9e89-be14502695bd | JAR | `242731908bfcde6f0b3df7f93e3bab5a0a313bdf4a2704f26a1f7a615928832f` | 143 |
| profiles | database-chain-110f6d12-97d3-4e82-9e11-b20dd1a909e1 | JAR | `764b22229f6dfa94c8ccf5a2d48ccc149acc11b678d98624ac25415efa5da764` | 0 |
| profiles | failed-shutdown-false-ca2a9298-0d58-4d0b-899c-7f512353d2aa | JAR | `75e45c6f15db6e86d2262d38a8cb491be98c8a4d9b253d93abd14bfdb2bc9ede` | 143 |
| profiles | failed-shutdown-true-9ff61f11-a0bf-4d9f-92b7-d58de0969d83 | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 |
| profiles | gpkg-help-example-95def1cd-5a06-4542-bf96-8b1173aa8875 | JAR | `eb4b8433aa9f70c346d65ca84aae1edf486f1087a08381f8e563eadec3d2df59` | 0 |
| profiles | gpkg-missing-table-e9c95875-9353-4c26-b438-227c558b966a | JAR | `7781b0cb5d4cf1e684d0d293c485c43dd156d479b82c91590d3af151217530e4` | 10 |
| profiles | gpkg-srid-3629d426-7aa2-4b0c-8499-a1b090015246 | JAR | `2cb45ca3a35198f31ffb6faeac690d823c2403c95f4a884dfeb4cfe00d44b0a4` | 0 |
| profiles | gpkg-srid-bff9731e-9187-4cfc-8d4e-6b61d7c79ec7 | JAR | `c63863697b5c7abeaa483daf53bed694f7bcb4e3eba0c2719dc9b0af4165ef82` | 0 |
| profiles | invalid-csv-1c238d9c-2975-4fa2-b819-440983587317 | JAR | `5267b840f3cc5b7761bf99d218cfe630163b6407f999e8767b3045ac28c4d7e5` | 10 |
| profiles | invalid-model-repository-74075194-cc29-489c-ae9c-5bc24a7b640e | JAR | `a6c3a8e7ccfc25599cddeae3a59550541e08c7b86a885599bac1ccc1462a4087` | 10 |
| profiles | original-gzip-fae3d7d9-f2dd-4ff9-8ca0-15842060cf18 | JAR | `d7de6660b98c6afa1cf5092784d072a2fff9283a97b08cbc4da16286f01066b2` | 0 |
| profiles | postgis-geometry-contract-18debea3-db03-4f44-bd92-e9ee53ecca7a | JAR | `8259274421b095fa6df3051516091ca08b8fc835afcd18caa201a5be5ac04de9` | 10 |
| profiles | runner-timeout-false-94409d52-3d17-42a4-9668-e6544fcb3fe9 | JAR | `1b1825466543bbcc5de8506828fd7565c0cec7eb3b9c88dea24e352e228b7538` | 143 |
| profiles | runner-timeout-true-8478e615-f4cc-4767-ac37-1b28c993ce7a | SCRIPT | `75f9bf9ef596fe6858aa89c99e0a6450ba3610b8591112bc5f45a482b179d375` | 143 |
| profiles | valid-csv-path-c2b50047-a18d-4819-ae1d-eeb18c5be070 | JAR | `bf5d3c4fa4c2473c860c6e18a8761fdd724d3e92ae66f44430875e257f2edce1` | 0 |
| profiles | valid-csv-string-23651857-543e-49bd-9560-dbab8ca3d016 | JAR | `0d193f2835fec9defc580c061ea3112301946fd923ca33d103fe4d506460e617` | 0 |
| profiles | valid-gpkg-path-0f2c57e3-e44f-4aa2-a0cb-05e0e7feee28 | JAR | `b13ee4f60fa63c99c0dce40758b42932e4e1c9d9550e70446b2cd7fe554d5023` | 0 |
| profiles | valid-gpkg-string-26e98aa3-d6fc-4c59-b512-7c536685e2b2 | JAR | `7c2142420c7d73518d052433576fe55591c0758a150fb171eea775177346f6a2` | 0 |
| profiles | valid-json-path-922a0971-e0c8-4904-b93b-9b4ad7ac7daf | JAR | `b78b95dab557284b62dba9e74e973c71ef2ef153510052c1dc7e3d2398dc944b` | 0 |
| profiles | valid-json-string-dc6eb738-c4b1-4997-80dd-44c3deff025f | JAR | `2a098411bf04dfe6dcf5b2167744bf55b17cc3f8af8c9815b17780a466fbe5d2` | 0 |
| profiles | valid-shp-path-f978ad9f-e241-4644-ba3a-e6237c144715 | JAR | `dc1c64a8ef803eeec4ad9b92f7cf13ed315f5ad39f7372c835374c8aea5ad7bd` | 0 |
| profiles | valid-shp-string-aa43b8db-61dc-4486-885f-c73a697b3203 | JAR | `f34cdb3fdcb3c8c8e264d6a7e489146fc28e7460acf5a4d74b4214673cdf5c37` | 0 |
| profiles | wrong-gpkg-table-0b01af43-f221-4f28-95a9-2cbcc0147170 | JAR | `28def6e969b44b014e267170ab080ce080bfb19db51d8f22b6e8963e81854c48` | 10 |

Vollständige Argumente, Quellen, Deskriptoren, JARs, Reports, Prozesslogs und Outputs unter standard/job-evidence/ beziehungsweise profiles/job-evidence/. artifact-audit.json und outcome-audit.json geben die maschinenlesbare Zuordnung einschliesslich Endpunkten, Timeout/Abort und Dauer.

## JAR-Ergebnis und getrennte Image-Vorbereitung

**JAR fachlich verifiziert. IMAGE in Phase 1 vorbereitet und noch nicht ausgeführt. GRETL-Produktimage tatsächlich ausgeführt: nein.**

Phase 1 enthält gemeinsamen Runnervertrag, kanonische Fixtures, separate Prozessausführung, Timeout/Abort/Reaping und vom Transport getrennte Endpunktbeschreibung. Vollständige 44er-JAR-Abdeckung, vier neue Module und implementierter Image-Adapter samt Adaptervertragstests sind Phase-2-Gates und werden hier nicht als fertig abgenommen. Selektive Paketierung bleibt Phase 3. Der ausdrücklich zurückgestellte echte GRETL-Image-E2E ist kein fehlender Phase-1-Pflichtnachweis. Tatsächlich gestartete PostgreSQL/PostGIS-, FTP-, S3-Testdienste und Ryuk sind davon getrennt.

## Offene Findings und Urteil

**Offene Findings gegen Pflichtkriterien: keine.** F1 durch tatsächliche CLI-Hilfe, unveränderten regulären JAR und unabhängiges SQLite-Orakel geschlossen. D1 korrigiert. F2 durch selbst aus gepinntem Image gelesenen Startpfad, eigenes Prozess-/Loginorakel, konkrete stärkere Negativassertionen und vollständig erfolgreichen neuen Pflichtprofillauf geschlossen. Die frühe historische Berichtskopie ist als Dokumentationsabweichung transparent eingeordnet und nachfolgend separat korrigiert.

**Urteil: PASS für Phase 1 auf `52675c526a0edad52d7005c1d7e874fb7d3ca276`.** Alle 12 Kriterien sind nach eigenen Builds, Reports, Artefakten und unabhängigen Orakeln erfüllt. Damit ist Phase 2 freigegeben; deren spätere Gates sind noch nicht erfüllt.
