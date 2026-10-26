# Anforderungs- und Testzuordnung

Die Tabelle ist eine Zuordnung, keine Abnahme. Ein Kriterium ist erst mit den
vollständigen Prüfläufen und dem unabhängigen Urteil zum bezeichneten Kandidaten
bestanden. Frühere Phasennachweise werden in jeder Folgephase wiederholt.

| ID | Ausführbarer Nachweis / zusätzlicher Beleg |
|---|---|
| P1-01 | baseline.md; operation-inventory.json; OperationMetadataTest; Original-/Kompatibilitätsmatrix |
| P1-02 | ValidatorLocalJobIT: CSV ohne Tabelle, GPKG mit Tabelle, falsche/fehlende Tabelle, CSV weist Tabelle zurück |
| P1-03 | ValidatorLocalJobIT: CSV/JSON/GPKG/SHP mit Path und String; ungültiger Repositorywert vor Enginearbeit |
| P1-04 | ValidatorLocalJobIT: echte Ili2gpkgImport-JARs; unabhängiges SQLite-Orakel für 2056/21781 und Fachzeilen |
| P1-05 | DatabaseArtifactIT: aktive PostgreSQL-Abfrage als Checkpoint, SIGTERM, CANCELLED/20, Rollback, NOT_RUN, keine offene Transaktion; erhaltene FailureArbiter-Tests |
| P1-06 | CancellationLocalJobIT: Script und JAR, Interrupt und SIGTERM, genau ein Cleanup/Runterminal, JSON-Bericht; vorheriger Fehler bleibt FAILED/10; RunControlTest prüft gemeinsames unabhängiges Budget |
| P1-07 | CorrelationTest; vorhandene LoggingPipelineTest-/RuntimeConfigTest-/Redaction-Regressionen; LogContext und RunReportWriter |
| P1-08 | ValidatorLocalJobIT und DatabaseArtifactIT; DUCKDB_GEOMETRY_UNSUPPORTED mit unverändertem PostGIS-Ausgang; generierte Referenz und kanonische Jobs |
| P1-09 | JaCoCo-Berichte in Modul-targets und jacoco-aggregate; Messgrenze dokumentiert; Subprozesse ausdrücklich uninstrumentiert |
| P1-10 | Zwei vollständige Root-Prüfbefehle; gesicherte XML-Reports; unabhängiger Prüfbericht mit PASS erforderlich |
| P1-11 | original-scenarios.json/.md: fester Originalcommit, Methoden und Assertionen, Fixture-Hashes und Scope; Abgleich des Fortschritts im Implementierungsbericht |
| P1-12 | ArtifactJobRunner; jobs/archive, validators, gpkg-import, database; externe Prozesse ohne Testclasspath; unabhängige Datei-/JDBC-Orakel und execution.json mit JAR-SHA-256 |

Phase 2 und Phase 3 bleiben gesperrt, bis die jeweilige Vorphase ein ausdrückliches
unabhängiges PASS erhalten hat. Der vollständige 44er-JAR-Port und der ausführbare
Image-Adapter sind Phase 2; gezielte Paketartefakte und Gesamtabnahme sind Phase 3.
