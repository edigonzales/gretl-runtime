# Migration der Core-Tasks

**Stand:** 19. Oktober 2026. Quelle der Taskliste:
`edigonzales/gretl-next`, `gretl-core`-Taskbestand. Die Runtime-Spezifikation
4.1 bleibt massgeblich; neue Taskverträge werden als releasebezogene
Erweiterungen dokumentiert.

## Ausgangslage

Im aktuellen Runtime-Katalog vorhanden:

- `HttpDownload`
- `Curl`
- `Gzip`
- `Extract`
- `FtpList`
- `FtpDownload`
- `FtpUpload`
- `FtpDelete`
- `S3Download`
- `S3Upload`
- `S3Delete`
- `S3Bucket2Bucket`
- `Db2Db`
- `DuckDbSqlExecutor`
- `Csv2Excel`
- `CsvImport`
- `CsvExport`
- `JsonImport`
- `CsvValidator`
- `JsonValidator`
- `SqlExecutor`
- `XslTransformer`
- `IliValidator`

Damit sind **23 von 44** Operationen vorhanden. Aus `gretl-next` fehlen noch
**21 Core-Tasks**. GeoTools, Publisher, XtfToGpkg und Native Image bleiben
ausserhalb dieses Plans.

## Gruppierung und Lieferwellen

| Welle | Gruppe | Tasks | Gemeinsame Basis |
|---|---|---|---|
| 1 | HTTP und einfache Dateien | `Curl` (erledigt), `Gzip` (erledigt), `XslTransformer` (erledigt) | JDK-HTTP, GZIP, Saxon |
| 2 | FTP | `FtpList` (erledigt), `FtpDownload` (erledigt), `FtpUpload` (erledigt), `FtpDelete` (erledigt) | Commons Net |
| 3 | S3 | `S3Download` (erledigt), `S3Upload` (erledigt), `S3Delete` (erledigt), `S3Bucket2Bucket` (erledigt) | AWS SDK v2 |
| 4 | Datenbank | `Db2Db` (erledigt), `DuckDbSqlExecutor` (Föderation erledigt) | JDBC, DuckDB |
| 5 | Tabellarisch | `Csv2Excel` (erledigt), `CsvImport` (erledigt), `CsvExport` (erledigt), `CsvValidator` (Struktur erledigt), `JsonImport` (erledigt), `JsonValidator` (Syntax erledigt) | POI, Jackson, CSV, ilivalidator |
| 6 | Geodaten | `GpkgImport`, `GpkgExport`, `GpkgValidator`, `Gpkg2Shp`, `ShpImport`, `ShpExport`, `ShpValidator` | iox-wkf, JTS, Shapefile-Referenz |
| 7 | INTERLIS/AV | `Ili2pgImportSchema`, `Ili2pgImport`, `Ili2pgExport`, `Ili2pgDelete`, `Ili2pgReplace`, `Ili2pgUpdate`, `Ili2pgValidate`, `Ili2gpkgImport`, `Ili2duckdbImportSchema`, `Ili2duckdbImport`, `Ili2duckdbExport`, `Av2ch`, `Av2geobau`, `Gpkg2Dxf` | ili2pg/ili2gpkg/ili2duckdb, av2ch, av2geobau |

**Katalogstand:** 23 → 30 (W6) →
30 (W6) → 44 (W7).

**Reihenfolge:** HTTP und einfache Dateien zuerst, dann Remote-Familien,
Datenbank, tabellarische Formate, Geodaten und zuletzt INTERLIS/AV. Die
Gruppen folgen gemeinsamen Engines, Abhängigkeiten und Testfixtures, nicht
einer alphabetischen Taskliste.

**Modulzuschnitt:** `Curl` und `Gzip` liegen im bestehenden
`gretl-basic-tasks`; `XslTransformer`, die vier FTP-Tasks und die vier
S3-Tasks liegen im neuen `gretl-core-tasks`. `Db2Db` und
`DuckDbSqlExecutor` sowie die sechs CSV-/JSON-Tasks liegen bei den
bestehenden JDBC-/IO-Tasks in `gretl-basic-tasks`. Die übrigen Gruppen kommen
zunächst ebenfalls in
`gretl-core-tasks` mit Paketen je Gruppe. Ein Split in eigene Remote-/INTERLIS-Module erfolgt erst
bei einem konkreten Dependency- oder Grössenproblem.

## Backing-Service-Abnahme

Der Standardbuild bleibt Docker-frei; echte Backing-Services laufen im Profil
`backing-services` (siehe [`testing.md`](../implementation/testing.md)).

- PostgreSQL/PostGIS: Testcontainers, echtes PostgreSQL 16 mit PostGIS.
- FTP: echter `delfer/alpine-ftp-server`-Container.
- S3: Floci/Testcontainers; seit Welle 3 aktiv.
- PostgreSQL: Db2Db läuft im `backing-services`-Gate gegen einen echten Container.
- DuckDB: dateibasiert/In-Memory; Föderation mit CSV/GPKG/PostgreSQL und Exporten.
- Tabellarisch/JSON: CSV/Excel/JSON-Dateien über H2/PostgreSQL; Validatoren prüfen Struktur/Syntax, noch keine INTERLIS-Modellvalidierung.
- HTTP: Host-Tests mit `HttpServer`; Container erst für Runtime-Image-Pfade.

Eine Task gilt nicht als migriert, wenn sie einen Backing-Service nur mit H2
oder einem Mock testet. Mindestens ein positiver Contract-Pfad und die
kritischen Fehlerfälle müssen gegen den echten Dienst laufen.

## Shapefile-Referenz

Für die Shapefile-Tasks (`ShpImport`, `ShpExport`, `ShpValidator`, `Gpkg2Shp`)
ist das Hop-Plugin [`edigonzales/hop-vector-raster-plugin`](https://github.com/edigonzales/hop-vector-raster-plugin)
eine wichtige Verhaltens- und Testreferenz. Es enthält eine native
Shapefile-Implementierung mit mehreren Korrekturen, die in der Runtime nicht
erneut gemacht werden sollen.

Relevante Stände:

- [`50ec3f8`](https://github.com/edigonzales/hop-vector-raster-plugin/commit/50ec3f81724328eba173d4befec353ea0b097548) – native Shapefile-I/O
- [`b3347b8`](https://github.com/edigonzales/hop-vector-raster-plugin/commit/b3347b82311a20426f53d1b148a6bb26233ffd05) – DBF-NULL-Werte und Encoding-Auswahl
- [`41773f2`](https://github.com/edigonzales/hop-vector-raster-plugin/commit/41773f2c05cb4921d55594e353c59855951655c1) – Writer-Output-Auswahl, Overwrite und Sidecars
- [`095f841`](https://github.com/edigonzales/hop-vector-raster-plugin/commit/095f8412ae45384b7c374f188412b31c63df3cdd) – Reader-/Schema-Verhalten

Die ausführliche Formatbeschreibung steht im
[Handbuch, Kapitel Formate](https://github.com/edigonzales/hop-vector-raster-plugin/blob/main/docs/biblios/user/09-formate.adoc).

### Zu übernehmende Verhaltensregeln

**DBF-NULL-Werte:**

- Mit NUL-Bytes gefüllte Character-Felder sind `NULL`, keine leeren Strings.
- Datums-NULLs wie `00000000`, `       0`, `        ` und `?` sind `NULL`;
  ungültige Daten bleiben Fehler.
- Numerische/Float-NULLs mit `*`-Füllung sind `NULL`; teilweise `*`-Füllung
  ist ein Fehler.
- Leere Character-Felder bleiben leere Strings; führende Leerzeichen bleiben
  erhalten.

**Encoding:**

- Reihenfolge: explizite Einstellung, CPG-Datei, DBF-Codepage (LDID), sonst
  `ISO-8859-1` mit Warnung.
- Keine automatische Erkennung anhand der Textbytes.
- Ungültige Bytefolgen sind ein Fehler; `Latin1` ist ein Alias für
  `ISO-8859-1`, `Latin-1` nicht.
- Writer verwendet standardmässig UTF-8 und schreibt eine CPG-Datei.
- Nicht darstellbare Zeichen und Kürzungen werden gemeldet; Kürzung erfolgt an
  gültigen Zeichengrenzen.

**Geometrie und Schema:**

- Point, MultiPoint, PolyLine und Polygon einschliesslich Multipart und
  Löchern; MultiPatch und heterogene GeometryCollections werden abgelehnt.
- Kurven werden linearisiert; Kurven mit Z/M werden nicht unterstützt.
- Expliziter Typ und Dimension erlauben leere oder ausschliesslich
  NULL-haltige Ausgaben; `AUTO` nutzt die erste nichtleere Geometrie und
  puffert maximal 10'000 anfängliche NULL/EMPTY-Zeilen.
- Z muss bei XYZ/XYZM endlich sein; fehlendes M wird als Esri-NoData
  gespeichert. NULL und EMPTY werden beide Null-Shape; EMPTY wird protokolliert.
- Fehlende PRJ bedeutet unbekanntes CRS mit Warnung; EPSG/WKT weist ein CRS
  zu, reprojiziert aber nicht. Widersprüchliche bekannte SRIDs werden
  abgelehnt.
- SHX ist beim Lesen optional; vorhandene Komponenten und Offsets werden
  geprüft; gelöschte DBF-Zeilen werden übersprungen.

**DBF-Felder:**

- String standardmässig maximal 254 Byte; explizite DBF-Zuordnung möglich.
- Integer-Standardbreite 20; Überlauf ist ein Fehler.
- Number/BigNumber-Standardbreite 33, Skala 15; HALF_UP-Rundung mit Warnung;
  BigDecimal nicht über Double führen.
- Date/Timestamp: DBF enthält nur das Datum; verlorene Uhrzeit wird gemeldet.
- DBF-Feldnamen sind ASCII, maximal zehn Zeichen; Kollisionen erhalten stabile
  Suffixe.

**Writer und Sidecars:**

- Ohne `overwrite` werden vorhandene Zieldateien und Sidecars abgelehnt.
- Mit `overwrite` werden bekannte Sidecars ersetzt und veraltete entfernt.
- Die Veröffentlichung des gesamten Shapefile-Bündels ist kein atomarer
  Austausch; Fehler dürfen aber keine alten Ziele zerstören.
- Keine Append-/Update-Funktion, keine QIX-Erzeugung, keine räumliche
  Reader-Filterung; Komponenten über 2 GiB werden nicht unterstützt.

Diese Regeln sind als Akzeptanzfälle in der Shapefile-Welle zu testen. Die
Implementierung darf die Hop-Klassen als fachliche Referenz nutzen; die
Runtime-Tasks bleiben aber eigenständige `OperationType`s mit der neuen
`=`-DSL. Lizenz- und Herkunftshinweise sind vor einer Codeübernahme zu prüfen.

## Nächste Schritte

1. Welle 1 ist abgeschlossen: `Curl`, `Gzip`, `XslTransformer`.
2. Welle 2 ist abgeschlossen: `FtpList`, `FtpDownload`, `FtpUpload`, `FtpDelete`.
3. Welle 3 ist abgeschlossen: `S3Download`, `S3Upload`, `S3Delete`, `S3Bucket2Bucket`.
4. Welle 4 ist abgeschlossen: `Db2Db` und `DuckDbSqlExecutor` mit
   CSV-/GeoPackage-/PostgreSQL-Quellen, PostgreSQL-Zielen und
   Parquet-/GeoPackage-/XLSX-/PostgreSQL-Exporten.
5. Welle 5 ist abgeschlossen: CSV-/Excel-/JSON-Tasks; die Validatoren prüfen
   Struktur/Syntax. Die INTERLIS-Modellvalidierung bleibt als Ausbaustufe offen.
6. Welle 6 (Geodaten) umsetzen.
7. Bei `ShpImport`/`ShpExport`/`Gpkg2Shp` die Shapefile-Regeln aus diesem
   Dokument als Testfälle übernehmen, bevor die Tasks als migriert gelten.
8. Vor Welle 6 den PostGIS-Tasktest ergänzen.
