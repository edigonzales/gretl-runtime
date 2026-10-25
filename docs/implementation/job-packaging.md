# Jobpaketierung und Migration

Stand: Phase 3, Spezifikation 4.1, Vertrag PKG-019 in
[12 — Jobpaketierung](../spec/12-job-paketierung.md). Der Packager ist ein
Authoring-Werkzeug. Ein erzeugter Job startet mit Java 25 ohne Maven,
Quellcheckout oder installierte GRETL-Distribution.

## Auswahl

| Deskriptor | Auswahl |
| --- | --- |
| `schemaVersion: 1` | Gemeinsame Job-Runtime, alle vier Taskmodule und die 18 Standardfunktionen des qualifizierten Distributionskatalogs. |
| `schemaVersion: 2` | Gemeinsame Job-Runtime plus ausdrücklich gewählte `taskModules`, `standardFunctions` und deklarierte Erweiterungen. |
| Schema 2 mit fehlenden oder leeren `taskModules` | Keine Standardtaskmodule. Actions und explizite Erweiterungen bleiben möglich. |
| Schema 2 ohne `standardFunctions` | Keine Standardfunktionsauswahl. |

Die Modulnamen sind `io` (13 Operationen), `database` (3), `interlis` (27)
und `ilivalidator` (1). Ganze Maven-Artefakte und ihre Laufzeitabhängigkeiten
werden übernommen. Dynamische Tasknamen werden nicht analysiert und Klassen
nicht anhand vermeintlich unbenutzter Operationen entfernt. Das IO-Modul
enthält deshalb auch AWS, FTP und Saxon; das Datenbankmodul enthält DuckDB.
Transitive Engineabhängigkeiten des INTERLIS-Moduls bleiben erhalten. Dessen
PostgreSQL-Formate und ili2pg-Operationen benötigen auch ohne das allgemeine
Datenbankmodul den ausdrücklich deklarierten PostgreSQL-JDBC-Treiber.
`standardFunctions: true` ergänzt das Ressourcenartefakt mit der festgelegten
18er-Auswahl und die vier Funktionsbibliotheken samt Laufzeitabhängigkeiten.

Beispiel für einen schmalen Validatorjob mit lokalen Daten und Modellen:

```json
{
  "schemaVersion": 2,
  "name": "validation",
  "version": "1.0.0",
  "runtimeVersion": "0.1.0-SNAPSHOT",
  "entryScript": "job.groovy",
  "sources": [],
  "resources": ["data", "models"],
  "taskModules": ["ilivalidator"],
  "standardFunctions": false,
  "dependencies": []
}
```

Das ausgelieferte Beispiel `examples/minimal-action/job-package.json` verwendet
Schema 2 ohne Taskmodul. Es wird in `DistributionIT` regulär paketiert und aus
einem fremden Arbeitsverzeichnis gestartet. Die bisherigen SQL- und
IliValidator-Deskriptoren bleiben Schema-1-Kompatibilitätsbeispiele. Die breite
Jobtestsuite verwendet explizite Schema-2-Deskriptoren; die separaten Paketfälle
prüfen zusätzlich jede Familie allein, eine externe Extension und einen
minimalen Job mit Erfolg und fachlichem Fehler.

## Identität und Auflösung

`gretl-dist` erzeugt `package-catalog.json` aus den tatsächlichen Maven-
Dependency-Bäumen. Der Katalog enthält Maven-Identität, Version, Classifier,
aufgelöste Kanten, Eingabedatei und SHA-256. Er verwendet die von Maven für die
vollständige Distribution vermittelten Versionen auch für deren Teilpakete.
Dateinamen sind keine Identitätsquelle. Vor jeder Bündelung werden die
qualifizierten Eingabebytes gegen den Katalog geprüft. CLI und Packager sind
keine Wurzeln einer Job-Runtime-Auswahl.

Zusätzliche `dependencies` sind ausdrücklich als `groupId:artifactId:version`
oder `groupId:artifactId:jar:classifier:version` anzugeben. Bisher nur zufällig
im Host-Classpath vorhandene eigene Erweiterungen müssen jetzt hier deklariert
werden. Kopieren in `lib/extensions` bleibt für den normalen Skriptstart
möglich, ersetzt beim Paketieren aber keine Deklaration.

Der Packager löst externe Wurzeln und ihre Runtime-Abhängigkeiten vor der
Kompilierung durch Maven auf. Der isolierte Groovy-Compiler sieht ausschliesslich
die ausgewählte Laufzeithülle. Fehlende Imports werden auch dann abgewiesen,
wenn der Authoring-Prozess dieselbe Klasse zufällig laden könnte. Das Packen
lädt die Jobdefinition nicht und führt weder Top-Level-Code noch Tasks aus.

Authoring-Properties (bei Distribution über `JAVA_TOOL_OPTIONS`):

| Property | Bedeutung |
| --- | --- |
| `gretl.packager.catalog` | Expliziter qualifizierter Katalog; der Distributionslauncher setzt seinen eigenen Katalog. |
| `gretl.packager.maven` | Maven-Executable, standardmässig `mvn`; beispielsweise absoluter Pfad zum Wrapper. |
| `gretl.packager.localRepository` | Maven-Cache, standardmässig `~/.m2/repository`. |
| `gretl.packager.repositories` | Kommagetrennte zusätzliche Repository-URLs; Maven Central bleibt verfügbar. |
| `gretl.packager.settings` | Optionale Maven-Settings, etwa für Mirrors und Zugangsdaten. |
| `gretl.packager.offline` | `true` verbietet Maven-Netzzugriffe beim Auflösen. |

Unbekannte Artefakte, fehlende oder dynamische Versionen und doppelte Wurzeln
scheitern ausdrücklich. Eine externe Wurzel darf keine abweichende Version
eines bereits qualifizierten Eingabeartefakts verlangen; auch widersprüchliche
transitive Auswahlbytes werden abgewiesen. Allgemeine Maven-Versionsmediation
bleibt bei Maven. Gleiche Ressourcenbytes werden dedupliziert, Services,
Groovy-Extension-Deskriptoren und Lizenzen werden zusammengeführt. Unterschiedliche
Klassen oder sonstige Ressourcen scheitern mit beiden Ursprüngen. Die
bestehende Modellklassen-Ausnahme betrifft ausschliesslich
`ch.interlis:iox-ili` und die eingebettete private Kopie in
`ch.interlis:av2geobau`; iox-ili ist dort kanonisch.

Die aktuelle Entwicklungsdistribution erlaubt ausdrücklich ihre bereits
qualifizierten SNAPSHOT-Eingaben und die bestehende Entwicklungs-Ausnahme für
Erweiterungen. Das ist kein unveränderliches Releaseversprechen für SNAPSHOTs.
Eine qualifizierte Veröffentlichung benötigt feste Versionen und geprüfte
Bytes. `sourceRevision` nennt den tatsächlichen Commit; `sourceDirty` kennzeichnet
lokale Änderungen. Ein Quellarchiv benötigt `-Dgretl.sourceRevision=<40-stellige-SHA>`.

## Artefakt- und Laufnachweise

Jedes Job-JAR enthält `META-INF/gretl/dependencies.json` mit Schema 2,
Descriptorversion, Auswahl, deklarierten Wurzeln und tatsächlich gebündelten
Maven-Eingaben samt SHA-256. Das ist ein Eingabeinventar des zusammengeführten
JARs. Klassen-, Ressourcen- und Serviceprüfungen ergänzen es. Es ersetzt weder
den Hash des fertigen JARs noch den Katalogfingerabdruck im Jobreport.

`SelectedPackageLocalJobIT` und `SelectedPackageArtifactIT` prüfen echte
Download-/Archiv-/XSLT-, PostgreSQL-/Db2Db-/DuckDB-, schmale Validator-,
GeoPackage-/Excel-/Funktions- und neue externe FileSize-Jobs. SQLite und DuckDB
laden dabei ihre nativen Bibliotheken aus dem Paket. Die Extension wird mit
frischer Maven-Identität in einem kontrollierten Repository gebaut; ihre
transitive Bibliothek und ein Groovy-Extension-Modul laufen tatsächlich.
Zusätzliche Host-JARs ändern die Auswahl nicht. Eine fehlende Deklaration
scheitert vor dem Jobstart. Der Quellordner wird vor dem isolierten Lauf gelöscht;
HOME ist leer, PATH enthält keine Buildwerkzeuge, CWD liegt ausserhalb des
Checkouts. Fachliche HTTP-/DB-Dienste bleiben ausdrücklich erreichbar.

Die vollständigen Root-Gates und die CI prüfen weiterhin den gesamten
migrierten Bestand, einschliesslich positiver Ergebnisse für alle 44
Operationen und konkreter fachlicher Negativfälle. Der CI-Audit prüft die
Hashidentität jedes gestarteten JARs, Distributionschecksummen, saubere
Commitprovenienz und alle 44 positiven Operationsnachweise. CI bewahrt Logs,
Reports, Ereignisse, Ergebnisdateien, Eingabeinventare und repräsentative
Pakete. Ein späterer Build mit übersprungenen Tests ersetzt diese Artefakte
nicht. Coverage misst die Test-JVMs; separate Jobprozesse sind nicht instrumentiert.

JAR ist der aktuelle fachliche Prüfweg. IMAGE ist vorbereitet und durch
Vertragstests ohne Produktcontainer geprüft, aber noch nicht ausgeführt.
Der manuelle Workflow `image-qualification.yml` verlangt eine explizite
Image-Referenz und verwendet denselben Szenariobestand mit denselben Assertions.
Die zusätzlichen Packager-Qualifikationen bleiben ausdrücklich JAR-Tests,
die älteren SCRIPT-Regressionen bleiben Skriptprüfungen. Details und Rechte-
/Endpointkonfiguration stehen in [Job-Artefakttests](job-artifact-tests.md).
