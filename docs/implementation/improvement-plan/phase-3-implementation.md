# Phase 3 — Implementierung und Paketqualifikation

Agent A, 25. Oktober 2026. Ausgangscommit dieser Phase:
`a8512a3d44ed0746c3aa0eeddcc7fefac274cff2`. Sauberer Produktkandidat:
`27adb5baa3006627e66ce819e345ca70cda477bb`, Branch `codex/improvement-plan`.
Der Kandidat wurde nach den eigenen vollständigen Root-Gates erstellt.
Der anschliessende Bericht verändert ausschliesslich Dokumentation.
Der überholte Zwischenkandidat `751afb861dba3b5bff742612c09321395320677a`
hatte im zusätzlichen Minimalbeispiel-Test und im Evidenzexporter einen falschen
Service-Dateinamen. Die gemeinsame Strukturprüfung war korrekt. Der neue
Kandidat korrigiert Test und Providerliste. Nach beiden erneut ausgeführten
Root-Gates wurde dieser Commit erstellt; der alte CI-Lauf 40225438733 wurde
bewusst abgebrochen und gilt nicht als Abnahme.

Phase 1 wurde am Kandidaten `52675c526a0edad52d7005c1d7e874fb7d3ca276`
unabhängig mit [PASS](phase-1-review.md) abgenommen. Phase 2 wurde nach den
innerhalb derselben Phase behobenen IMAGE-Findings am Kandidaten
`fda17afdc2fc4a8a0b9ee94f7d2922af08da4454` mit [PASS](phase-2-review.md)
abgenommen. Erst danach begann diese Phase. Die früheren Nachweise bleiben
unverändert erhalten. Phase 3 und die kumulative Abnahme benötigen ein eigenes
Urteil eines frischen unabhängigen Agenten B; dieses Dokument ist kein Prüf-PASS.

## Implementierung

PKG-019 legt den versionierten Vertrag fest: Schema 1 behält den qualifizierten
Vollkatalog einschliesslich der 18 Standardfunktionen. Schema 2 wählt komplette
Taskartefakte aus; eine fehlende oder leere Modulauswahl bedeutet keine
Standardtasks. Funktionen werden ausdrücklich gewählt. Der Vertrag wurde vor
der Umsetzung der Auswahl ergänzt. Migration und Authoring-Konfiguration
stehen in [Jobpaketierung](../job-packaging.md).

Die Distribution erzeugt ihren Katalog aus Mavens tatsächlichen aufgelösten
Identitäten und Kanten. Teilpakete verwenden die qualifizierten Versionen
dieser Distribution. Der Packager prüft alle ausgewählten Eingabebytes gegen
ihre SHA-256-Werte. Host-Classpath und Dateinamenheuristiken entscheiden keine
Auswahl. Ein dateinamensunabhängiger Identitätstest, Katalogmanipulation und
der tatsächliche Vergleich zweier unterschiedlich bestückter Authoring-
Classpaths sichern diese Grenze.

Externe Erweiterungen werden mit fixen Maven-Koordinaten vor der Kompilierung
aufgelöst. Maven liefert Runtime-Classpath und JSON-Identitäten. Der Compiler
läuft mit der gewählten Hülle und einem Platform-ClassLoader als Elternlader.
Er übernimmt keine App-Klassen des Authoring-Prozesses. Es wird kein neuer
Runtime-Plugincontainer eingeführt. Die gemeinsame Compilerkonfiguration wird
auch über diese Grenze verwendet. Maven bleibt ausserhalb des Jobstarts.

Der Merger erhält Services, Groovy-Extension-Metadaten, native Ressourcen,
Meldungen und Lizenzen. Unterschiedliche Versionen/Bytes sowie relevante
Ressourcen- und Klassenkonflikte scheitern ausdrücklich. Die erhaltene Ausnahme
für generierte Modellklassen ist auf die Maven-Ursprünge `ch.interlis:iox-ili`
und `ch.interlis:av2geobau` beschränkt. Die kanonische iox-ili-Fassung gewinnt.
`META-INF/gretl/dependencies.json` protokolliert die wirklichen Maven-Eingaben
und ihre Hashes. Ein zusammengeführtes JAR wird zusätzlich strukturell geprüft.

Der vollständige Dienstlauf deckte einen im Vollkatalog verdeckten Fehler auf:
Das INTERLIS-Modul deklarierte den von seinen PostgreSQL-Format- und ili2pg-
Operationen benötigten JDBC-Treiber nicht selbst. Der Treiber ist jetzt eine
eigene Runtime-Abhängigkeit dieses Taskmoduls. Das allgemeine Datenbankmodul
muss dafür nicht mitgewählt werden. Der korrigierte Lauf besteht alle betroffenen
Format- und ili2pg-Fälle.

Die breite Suite verwendet 13 explizite Schema-2-Fixturedeskriptoren. Alle
220 in Phase 2 dokumentierten Fixturedateien bleiben bytegleich; Originaljobs,
Daten, SQL, Modelle und Assertions sind erhalten. Der neue
[Fixture-Nachweis](phase-3-fixture-provenance.json) umfasst 238 Dateien.
Die [ursprüngliche Szenariozuordnung](phase-2-scenario-mapping.md) gilt weiter:
81 Fälle im Umfang, sechs konkret begründete Ausnahmen unter 87 Referenzfällen.
Zusätzliche Paketfälle ersetzen diese breite Suite nicht.

## Eigene Prüfungen und Umgebung

macOS 27.0.1 ARM64; Temurin 25.0.2+10-LTS; Maven Wrapper 3.10.0;
Groovy 6.0.0; JaCoCo 0.8.14. Docker Desktop stellt Docker 29.8.1 auf
Linux ARM64 bereit (6 CPU, 11.67 GiB); das digestgepinnte PostGIS-Image läuft
hier unter AMD64-Emulation. FTP und S3 sind tatsächliche Testcontainer.
Kein GRETL-Produktimage wurde gebaut oder gestartet.

| Tatsächlicher Befehl aus dem Root | Ergebnis |
| --- | --- |
| `./mvnw -B clean verify` | SUCCESS, 470 Tests, 0 Fehler, 0 Errors, 0 Skips; 5:34 Minuten. |
| `./mvnw -B -Pgeotools-probe,backing-services clean verify` | SUCCESS, 525 Tests, 0 Fehler, 0 Errors, 0 Skips; 8:07 Minuten. |
| `python3 scripts/audit-job-evidence.py` | Distributions-/JAR-Hashes, aktive Reactor-Testreports, Eingabeinventare und Coverage kontrolliert. |
| `python3 scripts/audit-job-evidence.py --full` | Zusätzlich alle 44 angebotenen Operationen durch erfolgreiche tatsächliche JAR-Invocations bestätigt. |

Der Standardlauf fand im Hauptcheckout statt, der Dienstlauf im separaten
Worktree `/tmp/gretl-phase3-A-profiles`. 905 Eingabedateien waren zwischen den
Worktrees bytegleich und wurden gegen die Git-Blobs des sauberen Kandidaten
verglichen. Die Entwicklungsbuilds protokollieren ehrlich ihre damaligen
HEADs (`751afb8` im Hauptcheckout, `a8512a3` im Profilworktree) und
`sourceDirty: true`. Sie werden nicht nachträglich als saubere
Commitbuilds umetikettiert. Die CI baut den exakten sauberen Kandidaten separat.

Rohbelege: `/tmp/gretl-improvement-evidence/phase3/standard-revision-1/` und `profiles-revision-1/`,
mit XML-Reports, JaCoCo, Jobartefakten, Reports, Logs, Ergebnisdateien,
Eingabeinventaren und Commitstempeln. Die Hauptlogs liegen daneben als
`standard-revision-1.log` und `profiles-revision-1.log`. Der [Logindex](phase-3-check-log-index.json)
hält auch fehlgeschlagene Entwicklungsprüfungen mit Hash fest. Dazu gehören
die anfängliche Compiler-ClassLoader-Kollision, fehlerhafte Testannahmen über
ResultView/CSV-Export, der relative Pfad im neuen Distributionsbeispiel,
der erste Dienstlauf mit 15 durch den fehlenden PostgreSQL-Treiber verursachten
Fehlern und ein vor Teststart abgebrochener POM-Versionsfehler. Nach der
Korrektur wurden beide ungekürzten Root-Gates erneut ausgeführt.

Der erfolgreiche vollständige Lauf enthält 148 echte JAR-Ausführungen und vier
zusätzliche SCRIPT-Regressionen. [Ausführungen](phase-3-jar-executions.json) und
[Operationszuordnung](phase-3-operation-job-mapping.json) nennen Szenarien,
konkrete Hashes und erfolgreiche Invocations aller 44 Operationen. Die
fachlichen Orakel lesen Ergebnisse ausserhalb des Jobprozesses. Negative Fälle
fordern konkrete fachliche Diagnosen und passenden Status; fehlende Klassen
oder ein beliebiger Startfehler gelten nicht als fachlicher Erfolg.

| Zusätzlicher isolierter Paketfall | Tatsächliche Eingabe-JARs | Fachliches Orakel |
| --- | --- | --- |
| Action, Erfolg und Fehler | je 12 | UTF-8-Ausgabe, konkreter Fachfehler, Cleanup, Korrelation und Status. |
| IO | 45 | HTTP-Antwort, ZIP-Inhalt und ursprüngliche XSLT-Ausgabe; FTP/S3/Saxon-Ressourcen vorhanden. |
| Datenbank | 16 | PostgreSQL-SQL/Db2Db-Werte sowie native DuckDB-CSV-Ausgabe. |
| Schmaler IliValidator | 25 | Lokale Originalvalidierung; keine ili2db-/AV-/POI-Hülle. |
| INTERLIS mit Standardfunktionen | 63 | GeoPackage-Namen über unabhängigen SQLite-Reader, Excel 308/7 und tatsächlicher HTTP-Funktionsaufruf. |
| Neue externe Extension | 14 | FileSize 5 und wirklicher Aufruf des transitiven Groovy-Extension-Moduls. |
| Alter Vollkatalog-Descriptor | vollständiger qualifizierter Katalog | DistributionIT: deklarierte externe FileSize-Extension, Parameter, Hilfe, Plan, Fehler und mehrere echte Starts. |

Die [Paketinventare und Strukturprüfungen](phase-3-package-selections.json)
belegen die ausgewählten und ausgeschlossenen Engines sowie Provider,
Meldungen, Services und Eingabehashes. Die sieben neuen isolierten Läufe löschen
die kompilierte Quellkopie vor dem Start, verwenden fremdes CWD, leeres HOME
und einen PATH ohne Buildwerkzeuge. `isolation.json` hält diese Bedingungen fest.
Der externe Provider wird mit frischer Maven-Identität in einem kontrollierten
Dateirepository gebaut; seine Klasse fehlt dem Testhost. Die fehlende
Deklaration scheitert auch bei absichtlich eingehängten Host-JARs vor dem Start.

[Coverage](phase-3-coverage.json) wird getrennt von den Testzahlen ausgewiesen:
13 aktive Produktmodule, 9 333 gemessene / 5 183 ungemessene Zeilen
(64.29 %), 3 433 / 3 324 Branches (50.81 %). Der JaCoCo-Messraum sind die
instrumentierten Test-JVMs. Separat gestartete Launcher-, Packager- und
Jobprozesse sind nicht instrumentiert; ihre Fachprüfungen erhöhen diese
Messwerte nicht. Es gibt keine neue Prozenthürde. Native-Access-Warnungen und
der bekannte POI/Log4j-StatusLogger-Hinweis sind keine übersprungenen Tests.

## CI und Abnahmematrix

CI des exakten Kandidaten:
[GitHub Actions Run 40226691230](https://github.com/edigonzales/gretl-runtime/actions/runs/40226691230).
Der Lauf und alle Pflichtschritte sind SUCCESS. Ubuntu 24.04.5 LTS x86-64,
Temurin 25.0.4+1, Wrapper Maven 3.10.0: Standard 470 Tests (8:49 Minuten),
vollständige Profile 525 Tests (12:50 Minuten), jeweils 0 Fehler/Errors/Skips.
Beide generierten Distributionen nennen exakt den Kandidaten und
`sourceDirty: false`. Der vollständige CI-Lauf enthält dieselben 148 JAR-
Ausführungen, vier SCRIPT-Regressionen und alle 44 positiven Operationen.

Agent A hat die drei gespeicherten CI-Archive heruntergeladen und ihre
ZIP-Digests gegen die GitHub-Metadaten geprüft. Die XML-Summen wurden ausserhalb
des CI-Audits erneut gezählt. Die echte Distributions-ZIP stimmt mit dem
protokollierten Hash überein; alle 93 Maven-Eingaben und SHA256SUMS-Einträge
wurden in der gespeicherten ZIP erneut kontrolliert. Die sieben gespeicherten
Paket-JARs entsprechen ihren tatsächlichen Ausführungshashes; ihre qualifizierten
Eingaben entsprechen demselben Distributionskatalog. Die zwei frischen externen
Fixtureartefakte sind ausdrücklich zusätzliche Eingaben.
[CI-Nachweis](phase-3-ci-verification.json) nennt Artefakt-IDs, Digests,
Testzahlen, Laufidentität und Paketprüfsummen. Roharchive und Logs liegen unter
`/tmp/gretl-improvement-evidence/phase3/ci-final/`. Eine vorzeitig gegen den noch
unvollständigen Paketdownload ausgeführte private Archivprüfung scheiterte;
nach vollständigem Transfer bestanden Digest- und Inhaltsprüfung. Das war kein
Fehler oder Skip eines Produkt-/CI-Tests.

Die CI veröffentlichte auf dem Arbeitsbranch keine Snapshots. Der manuelle
IMAGE-Workflow wurde nicht aktiviert.

Beide Pflicht-Workflows verwenden die beiden Root-Gates, bewahren den
Standardnachweis vor dem zweiten `clean` und prüfen anschliessend reale
JAR-Hashes, SHA256SUMS, saubere Commitprovenienz sowie alle 44 positiven
Operationsnachweise. Aktive Reactor-Module sind die Grundlage der Zählung;
verwaiste `target`-Verzeichnisse früher entfernter Module werden nicht mitgezählt.
Der Release-Workflow baut nach der Prüfung nicht erneut mit übersprungenen
Tests. Der Push-Workflow veröffentlicht auf diesem Arbeitsbranch keine
Snapshots; der vorhandene Deploy-Schritt bleibt auf `main` beschränkt.

| ID | Implementierter Nachweis für Agent B |
| --- | --- |
| P3-01 | PKG-019, PackageDescriptorTest mit Versions-/Auswahl-/Koordinatengrenzen; echtes Schema-1- und Schema-2-Paket. |
| P3-02 | Maven-Katalog und ResolvedArtifact; umbenannte Eingabe, Prüfsummenfehler und echte Host-Classpath-Gegenprobe. |
| P3-03 | Alle oben genannten Pakete mit unabhängigen Fachorakeln und tatsächlichen Eingabeinventaren. |
| P3-04 | Gesamte 44er-JAR-Suite, SQLite/DuckDB nativ, ServiceLoader, Logback, Groovy-Extension und i18n. |
| P3-05 | Neu gebauter externer Provider samt transitiver Bibliothek vor Kompilierung; fehlende Deklaration kann nicht vom Host ergänzt werden. |
| P3-06 | Top-Level-/Task-Marker beim Packen unberührt; isolierte Starts ohne Quelle/Maven; Fehler und Abbruch aus der breiten Suite bleiben erhalten. |
| P3-07 | DistributionIT bestätigt 44 Operationen und die 18er-Funktionsauswahl; Job-JARs enthalten weder CLI noch Packager oder MavenCli. |
| P3-08 | Beide Root-Gates, CI exakt am Kandidaten, Coverage, Distributions- und tatsächliche JAR-Hashes; frühere Regressionen erhalten. |
| P3-09 | README, Build, Tests, Modulübersicht, Migrations-/Paketvertrag und ausführbares Minimalbeispiel aktualisiert; unabhängiges Urteil folgt separat. |
| P3-10 | Gesamte migrierte Suite in beiden Root-Gates/CI; 148 JAR-Läufe mit allen 44 Operationen, keine Ersetzung durch Paket-Smokes. |
| P3-11 | Strikter IMAGE-Adapter mit nicht startenden Vertragstests; manuell aktivierbarer Workflow mit explizitem Image, Launcher, Benutzer und Dienstnetz. |

Die kumulative Gesamtabnahme aller 34 Kriterien erfolgt unabhängig durch Agent B.
Der [Plan](gretl-runtime-umsetzungsplan.md) und die erhaltenen Phase-1-/Phase-2-
Berichte sind dafür zusätzlich zu diesem Bericht massgeblich.

JAR: fachlich verifiziert. IMAGE: vorbereitet und ohne realen Image-Lauf
geprüft; noch nicht ausgeführt. Die zurückgestellte Produktimagequalifikation
ist kein Skip der hier verpflichtenden PostgreSQL/PostGIS-, FTP- oder
S3-Testdienste. Publisher, gretl-control-*, XtfToGpkg und Native Image bleiben
unverändert ausserhalb des Auftrags.
