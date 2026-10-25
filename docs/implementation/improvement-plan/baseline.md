# Ausgangslage und Verträge

Erfasst am 25. Oktober 2026 durch Implementierungsagent A.

- Checkout: `/Users/stefan/sources/gretl-runtime`, `origin=https://github.com/edigonzales/gretl-runtime.git`.
- Ausgangscommit: `230f2882bcdde15f552f7959aed157a0a6301fa6`. Gegenüber dem
  analysierten `e668a51b9589fbea9a1e003c8dd18e28726bd430` wurde ausschliesslich der
  Umsetzungsplan hinzugefügt. Die Produktimplementierung ist identisch.
- Zu Beginn lag bereits eine uncommittete Planaktualisierung vor (88 hinzugefügte,
  9 entfernte Zeilen). Diese vom Auftraggeber stammende Fassung mit 34 Kriterien
  bleibt erhalten und ist die Arbeitsgrundlage. Sonst war der Arbeitsbaum sauber.
- Java: Temurin 25.0.2+10-LTS, Maven 3.10.0 über Wrapper, Groovy 6.0.0.
- Plattform: macOS 27.0.1 ARM64, UTF-8, Locale en_US, Europe/Zurich.
  Artefaktjobs setzen UTC, UTF-8 und eine ausdrückliche Sprache.
- Docker Desktop: Engine 29.8.1, Linux/aarch64, 6 CPUs, 11.67 GiB RAM.
- Originalreferenz: lokales Git-Archiv von `sogis/gretl` exakt bei
  `a9222dc0ffd08f8f76570e998101db041822df71`.
- Vergleich: eigener Referenzcheckout `edigonzales/gretl-next` exakt bei
  `08bd11e97a54f32f8702d34ff16fb39bec6ea046` unter
  `/tmp/gretl-improvement-evidence/gretl-next-reference`.
- Historische M0–M7-Berichte sind keine unabhängige Abnahme des Folgeplans.

## Tatsächliche Baseline-Prüfungen

| Befehl aus Repository-Root | Ergebnis |
|---|---|
| `./mvnw -B clean verify` | SUCCESS; 384 Tests, 0 Failures, 0 Errors, 0 Skips |
| `./mvnw -B -Pgeotools-probe,backing-services clean verify` | FAILURE; 416 Tests, 0 Failures, 1 Error, 0 Skips |

Nachweise: `/tmp/gretl-improvement-evidence/baseline/verify.log`,
`profiles.log` sowie gesicherte XML-Reports in `baseline/` und `baseline/profiles/`.
Der Fehler liegt in `FtpTaskFamilyIT.wrongPasswordFailsWithoutLeakingSecret`:
Cleanup kann die FTP-Lease nicht erreichen (Connection refused). Er darf nicht
als Skip behandelt werden und wird im Phasennachweis erneut geprüft.
Es gab auf diesem Stand keine Coverage-Messung.

## Bestätigte Ausgangsfehler

| Vertrag | Codebeleg in e668a51 / 230f288 | Neuer Nachweis |
|---|---|---|
| CSV ohne Tabelle / GPKG mit Tabelle | ValidatorOperations.csv: tableField=true; gpkg: tableField=false | ValidatorLocalJobIT; erzeugte Referenz; OperationMetadataTest |
| modelDirectories mit file(...) | ValidatorOperations verwendet strings statt pfadfähiger Konversion | Vier reale Validatoren, jeweils Path und String; ungültiger Wert vor Engine |
| Ili2gpkgImport.defaultSrsCode | Feld nur in schema-Zweig registriert; GPKG ist kein schema-Zweig | JAR-Import und unabhängige SQLite-Abfrage von gpkg_geometry_columns |
| Db2Db-Cancel | RuntimeException-Catch verpackt TaskCancelledException in DB2DB_EXECUTION | Echte PostgreSQL-Transaktion und Prozessabbruch |
| SIGTERM-Abschluss | Shutdownhook fordert nur Cancel an und wartet nicht | Prozessharness für Script und Job-JAR |
| correlationId | Parser speichert Wert, Startpfad übergibt ihn nicht an LogContext | CLI-, Event- und Reportprüfungen; zwei Läufe; ENV-Vorrangregeln |
| PostGIS/DuckDB | Kein separater Geometry-/SRID-Nachweis | Explizit getestete Ablehnung von geometry/geography vor Übernahme |

## Kompatibilität zu gretl-next

Das vollständige Namens-/Provider-/Resultatinventar steht in
[operation-inventory.json](operation-inventory.json). Die aktuelle öffentliche
Feldfläche ist in `docs/reference/generated/` aus genau diesen Metadaten erzeugt.
Die [Originalzuordnung](original-scenarios.md) erfasst Szenarien und Fixtures.

| Familie | Zugesicherter Runtimevertrag | Bewusste Grenze / Migration |
|---|---|---|
| Alle 44 Operationen | Bestehende IDs, Symbole, Resultatfelder; `=`-DSL; öffentliche Parameterdeklaration | Kein Gradle-Task-/Property-/Provider-API und kein automatisches Erben sämtlicher alter Setter |
| SqlExecutor | PostgreSQL-Alias; genau Scripts oder PreparedStatement-Maps; voller UTF-8-Scripttext; atomare JDBC-Transaktion pro Aufruf | Alte sqlFiles/sqlParameters/sqlParameterSets-/Textsubstitution sind kein Vertragsangebot; Werte als PreparedStatement-Parameter, Wiederholungen als ausdrückliche Tasks/Action |
| IliValidator | Genau eine lokale XTF-Datei, vollständiger lokaler Modellordner, optionale lokale validationConfig; geschützter Report | Alte FileSets, Repositoryauflösung und allgemeine Funktionsoptionen werden nicht pauschal nachgebaut; mehrere Dateien durch mehrere Aufrufe |
| Formatvalidatoren | Metadatenfelder für CSV/JSON/GPKG/SHP mit echten IOX-Readern | tableName gehört nur zu GPKG; modelDirectories erlaubt Path und nichtleere steuerzeichenfreie Repository-Strings |
| ili2db/AV/Format/IO | Die angebotene Feldfläche und Ergebnisse sind massgebend; Originalvarianten werden einzeln zugeordnet | Kein neuer Task für nicht angebotene historische Funktionen; Modell-/Datenrepositoryzugriff bleibt fachlicher Netzbedarf |
| DuckDB-Föderation | CSV/GPKG und PostgreSQL-Tabellen im dokumentierten Umfang | PostGIS geometry/geography als direkte PostgreSQL-Quellspalten ausdrücklich nicht unterstützt; keine stille Konversion zu Text ohne SRID-Vertrag |
| Publisher / Raster / DatabaseDocumentExport | Nicht angeboten | Ausdrücklich ausserhalb des Auftrags; Originalfälle bleiben mit konkretem Grund inventarisiert |

## Übernommene Fixtures in Phase 1

- `jobs/archive/planregister.xml` ist byteidentisch zum Original unter `jobs/Gzip/`;
  der neue Job prüft zusätzlich eine Gzip/Extract-Kette mit unabhängigem Bytevergleich.
- `jobs/validators/` übernimmt die bereits vorhandenen, auf das Runtimeformat
  portierten Originalfixtures für CSV/JSON/GPKG/SHP. Die Formatvalidatoren werden
  mit denselben positiven beziehungsweise negativen Daten ausgeführt. Für den
  negativen CSV-Fall bleiben beide originalen fachlichen Diagnosen verbindlich.
- `jobs/gpkg-import/` übernimmt `Beispiel2.ili` und `Beispiel2a.xtf` aus dem
  vorhandenen ili2db-Testbestand; SQLite prüft Daten und den per DSL gewählten SRID.
- `jobs/database/` ist ein kleiner gleichwertiger PostgreSQL-Taskkettennachweis
  (SqlExecutor → Db2Db → SqlExecutor). Weitere Original-Db2Db-Varianten bleiben
  im Originalinventar sichtbar und sind bis Ende Phase 2 gesondert nachzuweisen.
- `jobs/cancellation/` ist ein neues Runtimefixture für kooperativen Abbruch,
  SIGTERM und Fehlerpräzedenz; Synchronisation erfolgt über Dateien/WatchService.
