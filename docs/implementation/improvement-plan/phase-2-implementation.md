# Phase 2 — Implementierungsbericht

Agent A: Implementierungsagent dieser Sitzung. Agent B wird mit frischem
Kontext und eigenem sauberen Checkout beauftragt. Dieser Bericht erteilt kein
PASS. Phase 3 bleibt bis zum ausdrücklichen unabhängigen PASS gesperrt.

Ausgangspunkt: `b0499d3` (Ablage des unabhängigen Phase-1-PASS).
Geprüfter Phase-1-Produktstand: `52675c526a0edad52d7005c1d7e874fb7d3ca276`.
Phase-2-Kandidat: `7db4ab8b97d1aa8adf60bc9b137adf24c65e2a02`.

## Änderungen und Grenzen

Die Taskartefakte sind jetzt IO (13), Datenbank (3), INTERLIS (27) und
IliValidator (1). Die alten Basic-/Core-Module sind ersetzt; die Provider
werden einmal registriert. Fachgruppen, Ressourcen, Tests, Verbraucher und
Distribution wurden gemeinsam verschoben. Der eingefrorene Phase-1-Katalog
prüft weiterhin IDs, DSL-Symbole, Eingaben und Resultatfelder; nur die separat
dokumentierten Java-Typ-/Providernamen werden übersetzt.

Vor dem Verschieben wurden 103 öffentliche Taskklassen und ihr tatsächlicher
`javap -public`-Stand erfasst. [Java-/Maven-Migration](../../migration/phase-2-java-maven.md),
[Klassenabbildung](phase-2-java-moves.json),
[vorherige Einstiegspunkte](phase-2-previous-public-entrypoints.json) und
[Signaturen](phase-2-previous-public-api.txt) dokumentieren den Übergang.
Es wird keine pauschale Binärkompatibilität der konkreten Klassen behauptet.
Die neutrale SPI und das separat gebaute FileSize-Beispiel bleiben erhalten.

`gretl-job-runtime` besitzt nun Jobquelle, Konfiguration, Parameterauflösung,
Planung, Ausführung, Cancellation, Shutdown und Abschluss. Die CLI übersetzt
Startargumente; `JobJarMain` verwendet denselben Ausführungskern direkt.
Job-Runtime besitzt weder direkt noch transitiv CLI oder Packager.
Die [Modulübersicht](../module-boundaries.md), aufgelösten
[Produktionsbäume](phase-2-production-dependencies.json) und gezielten
transitiven Enforcer-Regeln sichern die Grenzen. API bleibt JDK-only;
Runner und Taskbibliotheken besitzen keine versteckten Fach-/Runnerzyklen.

Die allgemeine Formatvalidierung enthält keine feste Organisationsauswahl.
Der kleine Registrierungsadapter verwendet IOX-Funktionsklassen über die
bestehende Property beziehungsweise ServiceLoader. Die IOX-Nativsuche wurde
am verwendeten Bytecode geprüft: Sie verschluckt defekte Erweiterungen und
überschreibt Namenskollisionen. Deshalb prüft der Adapter explizite Auswahl,
Instanziierung, qualifizierte Namen und Kollisionen streng. Die bisherigen
18 Funktionen liegen als reines `standard-functions`-Ressourcenartefakt mit
den vier Funktionsbibliotheken in der Standarddistribution.

Der unabhängig aus dem eigenen Maven-Runtimegraphen zusammengestellte
Validator-Classpath enthält keine dieser Organisationsbibliotheken und
validiert alle vier Formate, einschliesslich konkretem CSV-Negativfall.
Ein Modell mit tatsächlich aufgerufenener HTTP-Zusatzfunktion wird als
JAR sowohl mit CsvValidator als auch mit dem schmalen IliValidator geprüft:
Erfolg, fachliches Nein, fehlende Funktion, defekte Auswahl und doppelte
Namen. IOX behandelt eine fehlende ausgeführte Funktion sonst nur als
Warnung; die Integration wandelt diese konkrete Diagnose in einen
verständlichen Fehler um. Die schmale Validator-DSL bleibt bei drei Feldern.
[Matrix und Grenzen](../validator-functions.md).

Für die Originalfälle waren kleine Korrekturen an vorhandenen Verträgen
nötig: benannte GPKG-Datasets aktivieren Basket-Speicherung, Modelle aus
bestehender MetaConfig werden beim Schemaimport akzeptiert, MetaConfig
akzeptiert den bereits zugesagten Path-Wert, ili2db schreibt seine
Fehlerursache vor dem Schliessen ins Fachlog, und der schmale Validator
erhält die ursprüngliche Objekt-TID in der Diagnose. Neue historische
Optionen wurden dafür nicht ergänzt.

## Originalfälle und echte Artefaktprozesse

[Originalinventar](original-scenarios.json) und Original-Assertions bleiben
erhalten. Die [Zuordnung](phase-2-scenario-mapping.md) erfasst alle 87 Fälle:
81 im Umfang (einschliesslich des historisch inaktiven FTP-Falls mit neuem
lokalem Positivnachweis), sechs ausserhalb: DatabaseDocumentExport,
OracleDriver, zwei Raster- und zwei Publisher-Fälle. Die JSON-Zuordnung
enthält konkrete Testmethoden, Varianten und die ursprünglichen Assertions.

Erhalten sind unter anderem typisierte CSV-/DB-Werte, fetchSize und
Zieltabellenlöschung, vorbereitete skalare/List-Parameter, Geometrie und
SRID, GPKG-/SHP-/DXF-Inhalte, Excel-Zeilen, JSON-Varianten, AV-Dateisets,
INTERLIS-Baskets und Dataset-Slices, Schema-/Import-/Replace-/Update-/
Export-/Delete-/Validate-Pfade, originale lokale ilidata-Repositories,
MetaConfig, NGK-Objektdiagnosen, Parquet und DuckDB-Föderation,
HTTP-Authentifizierung/Form/Multipart/Binärdaten, lokale FTP-Transfers
sowie originale S3-Schlüssel, Inhalte, Kopien, Downloads und Löschungen.

Bewusste Gleichwertigkeiten sind pro Fall beschrieben: unter anderem
native DBF-Booleanwerte statt früherer Stringdarstellung, native
Shapefile-Objekt-IDs, explizite deutsche Locale für die originale
JSON-Dezimaldiagnose, zwei schmale Validator-Tasks statt einer neuen
FileSet-Option und der aktuelle DuckDB-Schemaname. Der historische
S3-Negativfall verwendete seine angeblich falschen Credentials nicht;
hier wird ein konkretes NoSuchBucket geprüft. Floci ist kein IAM-Nachweis.
Die fachlichen Originalorakel werden dadurch nicht zu Startsmokes reduziert.

Die kanonischen Ressourcen werden frisch kopiert, über den regulären
Kandidaten-Packager gebaut und separat mit `java -jar` gestartet.
Die Test-JVM prüft Ergebnisse mit JDBC, unabhängigen Datei-/XTF-Lesern,
FTP- und S3-Clients. Kein Testclasspath wird eingeschleust. Identische
Fixture-Pakete dürfen innerhalb derselben Failsafe-JVM ihren bereits
gebauten JAR-Inhalt wiederverwenden; jeder Jobprozess und seine beschreibbaren
Verzeichnisse sind frisch. Der Paketnachweis dokumentiert diese Wiederverwendung.
Fehlertests verlangen den fachlichen Status und die konkrete Diagnose.

[Fixture-Herkunft](phase-2-fixture-origins.md) und
[Dateimanifest](phase-2-fixture-provenance.json) enthalten 220 Dateien mit
Hashes, darunter 129 bytegleiche Originaldateien. Modell-Snapshots und
Lizenzen sind erfasst. Tests benötigen keine produktiven URLs oder privaten
Modellcaches. Alle 44 Operationen haben erfolgreiche fachliche JAR-Aufrufe;
[Zuordnung und Identitäten](phase-2-operation-job-mapping.md) beruhen auf den
tatsächlichen Prozess-/Jobreports des letzten vollständigen Profillaufs.

## IMAGE-Vertrag

Der ausführbare Docker-Adapter verwendet denselben Request, dieselben Jobs
und dieselben fachlichen Assertions. Explizite Image-Referenz, immutable
Image-ID, Launcher, benannter Netzwerkmodus, nicht-root UID/GID, Mounts,
Pfad-/Endpoint-Übersetzung, CWD, beschreibbare Home-/Temp-/Work-/Output-/
Report-Verzeichnisse, Zieltaskauswahl, Parameter, Environment und read-only
Secrets sind umgesetzt. Docker-Voraussetzungen und Dateieigentümer werden
streng geprüft. Es gibt keinen Fallback oder erfolgreichen Skip.

Die austauschbare Startgrenze wird mit einem aufzeichnenden Fake geprüft,
einschliesslich Exitstatus, Timeout, Stop, Reap und Cleanup. Das sind
Vertragstests, keine Image-Integration. PostGIS, FTP und S3 sind dagegen
reale verpflichtende JAR-Testdienste. IMAGE wurde nicht gestartet.
[Ausführlicher Adaptervertrag](../job-artifact-tests.md).

## Tatsächlich ausgeführte Prüfungen

Toolchain unverändert gegenüber Phase 1: Temurin 25.0.2+10-LTS,
Maven-Wrapper 3.10.0, Groovy 6.0.0, macOS 27.0.1 ARM64,
Docker 29.8.1 mit Linux-ARM64-VM; PostGIS verwendet das bereits qualifizierte
emulierte Image. Kein GRETL-Produktimage wurde gebaut oder gestartet.

Nachweiswurzel: `/tmp/gretl-improvement-evidence/phase2/`. Die vollständigen
Rootlogs, XML, Coverage und Jobartefakte wurden vor dem nächsten clean unter
den gleichnamigen Verzeichnissen gesichert. Entwicklungsläufe mit Fehlern
sind im [Logindex](phase-2-check-log-index.json) ausdrücklich erhalten.
Fehler wurden innerhalb dieser Phase behoben; gezielte Läufe zählen nicht
als Ersatz der beiden vollständigen Abschlusskommandos.

| Prüfung | Ergebnis |
|---|---|
| `./mvnw -B clean verify`, `standard-1` | 455 Tests; 0 Failures, Errors, Skips |
| `./mvnw -B -Pgeotools-probe,backing-services clean verify`, `profiles-1` | 509 Tests; 0 Failures, Errors, Skips |
| `./mvnw -B clean verify`, `standard-2` | 455 Tests; 0 Failures, Errors, Skips |
| Final `./mvnw -B clean verify`, `standard-3` | 456 Tests; 0 Failures, Errors, Skips |
| Final `./mvnw -B -Pgeotools-probe,backing-services clean verify`, `profiles-2` | 510 Tests; 0 Failures, Errors, Skips |

Der letzte Standard- und Profillauf verwenden denselben eingefrorenen
Produkt-/Test-/Buildstand. Nachfolgend wurden nur diese Nachweisdokumente
ergänzt. Der Standardlauf ist Docker-frei, das Vollprofil enthält die
GeoTools-Qualifikation und echte PostGIS-/FTP-/S3-Dienste.

Testanzahl und Coverage sind verschiedene Aussagen. JaCoCo misst die
instrumentierten Maven-Test-JVMs; separat gestartete Packager-, Launcher-
und Jobprozesse sind uninstrumentiert. Die vollständigen Zähler stehen in
[phase-2-coverage.json](phase-2-coverage.json). Der gemeinsame Startpfad
wird fachlich zusätzlich über die JAR-/Skriptprozesse geprüft. Kein neues
Prozentgate und kein aus Testzahlen abgeleiteter Coveragewert.

Der letzte Profillauf dokumentiert 141 echte JAR-Prozesse und vier zusätzliche
Skript-Regressionsprozesse. Alle 44 Operation-IDs besitzen mindestens einen
SUCCEEDED-Aufruf; alle gespeicherten JAR-Prüfsummen wurden erneut berechnet.
Es gibt keine IMAGE-Prozesse.

Die breite Suite packt in Phase 2 noch den bisherigen Vollkatalog.
Die gezielte Artefaktauswahl ist ausdrücklich Phase 3 und wird hier nicht
als bereits erreicht behauptet. Eine CI-Abnahme dieses Phase-2-Kandidaten
wird nicht behauptet; Agent B führt die Pflichtwege selbst aus.

JAR: fachlich verifiziert. IMAGE: vorbereitet und ohne realen Image-Lauf
geprüft; noch nicht ausgeführt. Unabhängiges Urteil: ausstehend.
