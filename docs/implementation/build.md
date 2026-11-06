# Build und Distribution

## Voraussetzungen und vollständige Gates

JDK 25 muss die aktive JVM sein. Der eingecheckte Maven-Wrapper verwendet
Maven 3.10.0; ein System-Maven ist für den Reactor nicht erforderlich.
Versionen und Dependencies stehen zentral in `pom.xml`, die Modulgrenzen in
[der Modulübersicht](module-boundaries.md).

Der Build benötigt Zugriff auf Maven Central und die in den POMs erklärten
INTERLIS-Repositories. Das optionale GeoTools-Profil verwendet zusätzlich das
OSGeo-Repository. Docker muss für das vollständige Dienstprofil erreichbar sein.
Vom Repository-Root:

```sh
./mvnw -version
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Der Standard-Reactor führt Unit-, Komponenten-, Distributions- und lokale
fachliche SCRIPT-Prozesse und die erhaltenen JAR-Paketverträge aus. Das Vollprofil ergänzt die isolierte GeoTools-Probe
sowie echte PostgreSQL/PostGIS-, FTP- und S3-Prüfungen. Fehlendes Docker ist ein
Fehler. Die [Teststrategie](testing.md) beschreibt Szenarien und Aussagegrenzen.
Teiltests ersetzen diese beiden Gates nicht.

Testzahlen gelten jeweils für den getesteten SHA. Sichere Surefire-/Failsafe-XML,
Konsolenlogs, Jobberichte und Artefaktidentitäten vor dem nächsten `clean`.
Für überprüfte und vor clean gesicherte Modusnachweise verwendet die CI
den Matrixstarter am sauberen Kandidaten:

```sh
scripts/verify-job-matrix.sh --mode SCRIPT --scope full --output ../gretl-script-full
scripts/verify-job-matrix.sh --mode JAR --scope full --output ../gretl-jar-full
scripts/verify-job-matrix.sh --mode IMAGE --scope local --output ../gretl-image-local
scripts/verify-job-matrix.sh --mode ALL --scope full --output ../gretl-all-full
```

IMAGE startet externe Scripts/Projekte im generischen Runtime-Container,
auch local mit Docker. ALL führt drei saubere vollständige Läufe aus,
sichert vor jedem nächsten clean und prüft alle 44 Operationen je Modus.
Der [Image-/OpenShift-Weg](../../gretl-dist/src/main/docker/README.md)
beschreibt Basisdigest, tatsächliche Runtimebytes, UID und Volumes.

Ein gewöhnlicher frischer Standardbuild kann weiterhin direkt auditiert
werden, bevor ein weiterer Build seine Nachweise ersetzt:

```sh
python3 scripts/audit-job-evidence.py --source <vollstaendige-Kandidaten-SHA> --mode SCRIPT --scope local
```

Er erhält eine gemeinsame Maven-Build-Run-ID. Der Matrixstarter setzt
eine UUID und explizite JUnit-Kandidatenbindung; deren Fehlen ist im
Matrixaudit ein Fehler. Der Auditor baut keine Ersatzartefakte. Ein
Vollprofil-Audit verwendet `--scope full` beziehungsweise den erhaltenen
Alias `--full`. Eine lokale Qualifikation ist keine separate Release- oder
Plattformfreigabe. Für die macOS-FTP-Diagnose siehe
[Cleanup-Verfahren](../maintenance/cleanup/README.md).

## Artefakte und Ausführungskontexte

`gretl-dist/target/dist/` enthält den aufgebauten Distributionsroot. Die Archive
liegen unter `gretl-dist/target/gretl-runtime-6.0.0-SNAPSHOT-bin.zip` und
entsprechend `.tar.gz`. Nach Entpacken wird aus dem enthaltenen Root gestartet:

```sh
./bin/gretl --version
./bin/gretl operations
./bin/gretl run examples/minimal-action/job.groovy --help
```

Im Git-Checkout lautet der Launcherpfad dagegen
`./gretl-dist/target/dist/bin/gretl`. Vollständige tatsächlich geprüfte
Script-/JAR-Beispiele stehen im [Root-README](../../README.md).

| Distributionspfad | Inhalt |
| --- | --- |
| `bin/` | Launcher mit Signalweitergabe an die JVM |
| `lib/runtime/` | CLI, Groovy, Runner, API, Logging und Voraussetzungen |
| `lib/tasks/` | Vier fachliche Taskmodule und ihre Dependencies |
| `lib/extensions/` | Standardfunktionen und zusätzliche kompatible Erweiterungen |
| `examples/` | Einzeljobs, minimale Action und Projektpipeline |
| `inventory.json` | Artefakte, Versionen und Quellidentität |
| `package-catalog.json` | Qualifizierte Maven-Identitäten und Eingabehashes |
| `SHA256SUMS` | Prüfsummen der Distributionsdateien |

`gretl package descriptor.json --output job.jar` kompiliert ohne Jobausführung.
Das fertige JAR benötigt Java 25 und Laufzeitparameter. Externe deklarierte
Dependencies werden beim Paketieren durch Maven aufgelöst; die Auswahl und
Compilerisolation erklärt der [Paketierungsvertrag](job-packaging.md).
Projekt-JARs und Einzeljob-Schemas 1/2 bleiben getrennt im Deskriptor, verwenden
aber dieselbe Runtime. GeoTools wird nicht in die Standarddistribution aufgenommen.

## Dokumentationsprüfung

Nach einem aktuellen Distributionsbuild, aus dem Repository-Root:

```sh
./gretl-dist/target/dist/bin/gretl operations --format adoc --lang de --output docs/reference/generated
python3 -m unittest discover -s tests/docs -v
python3 scripts/check-docs.py
quarto render website/
```

Die 44 generierten Tasktabellen werden gegen die Distribution und die Includes
in `reference.adoc` geprüft. Die [Referenzanleitung](../reference/README.md)
beschreibt den tatsächlichen Thoth-Biblios-Build. Quarto rendert die Website
nach `website/_site/`; [Website-Pflege](../../website/README.md) beschreibt die
Quellen. Generierte Ausgaben und lokale Thoth-Konfigurationen werden ignoriert.
Bei Änderungen an Quickstarts müssen deren Befehle laufen und Ausgabedateien,
Inhalt, Exitstatus und Reports geprüft werden. Ein statischer Linkcheck genügt
für diese Ausführungsnachweise nicht.

## Tooling-Distributionsqualifikation

Nach dem vollstaendigen Build prueft
`node tools/gretl-tooling/qualify-server.mjs --jar vscode/gretl-vscode/server/gretl-lsp.jar --evidence /tmp/gretl-server-qualification`
den echten ausgelieferten Java25-LSP ueber stdio: Katalog und Hover,
kleine/mittlere/grosse/kaputte Quellen, Quellbudget, schnelle Edits, Close und
Rootwechsel, Nichtausfuehrung sowie echtes Shutdown/Exit. Das Evidenceverzeichnis
muss neu sein. Die JSON-Ausgabe bindet JAR-Digest und Buildidentitaet und misst
Laufzeiten sowie Prozessspeicher; sie behauptet keine allgemeine Latenzgarantie.

Mit einem vorher lokal bereitgestellten offiziellen Java25-Image kann derselbe
Befehl um `--docker-image eclipse-temurin:25-jre` ergaenzt werden. Die Vorbereitung
`docker pull eclipse-temurin:25-jre` benoetigt eine Registryverbindung. Der
eigentliche Test laeuft mit dem erfassten Image-Digest ohne Netzwerk, mit
schreibgeschuetzten JAR-/Quellmounts, 256 MiB Javaheap und 768 MiB Containerlimit.
`--candidate` verlangt zusaetzlich den exakten sauberen Kandidaten-SHA.

`node tools/gretl-tooling/qualify-runtime-canary.mjs --evidence /tmp/gretl-runtime-oracle-canary`
prueft nach den Rootgates die unveraenderte Oracle des GUI-SCRIPT-Fixtures gegen
einen Kontrolllauf und ein absichtlich falsches Ergebnis. Nur private Kopien des
Tests und des lokalen ZIP-Inputs werden veraendert; der echte Groovyinput und
saemtliche Assertions bleiben erhalten. Die Prozessbelege unterscheiden den
erfolgreichen Runtimeprozess vom erwarteten Assertionfehler der negativen Probe.

Die Workbench-Oracle `vscode/gretl-vscode/scripts/verify-workbench-evidence.mjs`
wertet die begrenzten Leseaufnahmen des separaten Testdrivers gegen explizite
Fixtureerwartungen aus. Ihre Canaries unterschlagen einen erwarteten Node oder
eine Textaenderung und muessen scheitern. Sie ersetzt keine nativen Aktionen und
keine Bildpruefung. Falsche Serverbytes werden separat an einer Scratch-VSIX
durch das wirkliche Inventargate abgewiesen. Alle Belege bleiben vom normalen
Benutzerprofil getrennt.

## Runtimekonfiguration, Pfade und Secrets

Die optionale Ausgabesprache kann per `--lang`, `GRETL_LANGUAGE`, `language`
in `runtime.json` oder OS-Locale gewählt werden; Fallback ist Englisch.
Details stehen in [Lokalisierungsvertrag](../architecture/runtime-contracts.md#lokalisierung).

```json
{
  "schemaVersion": 1,
  "runtime": {
    "workRoot": "/var/tmp/gretl",
    "reportRoot": "/var/log/gretl",
    "keepWork": "on-failure",
    "timeout": "PT1H",
    "jobName": "datenbezug"
  },
  "logging": {
    "format": "text",
    "level": "INFO",
    "eventFile": true,
    "maxRecordBytes": 16384,
    "maxEventFileBytes": 134217728,
    "progressInterval": "PT10S",
    "heartbeatInterval": "PT30S"
  },
  "params": {"environment": "test"},
  "secrets": {
    "dbPassword": {"environment": "GRETL_DB_PASSWORD"},
    "sourceAuth": {"file": "source-auth.txt"}
  },
  "taskConfig": {
    "edit": {
      "jdbcUrl": "jdbc:postgresql://localhost/gretl_test",
      "user": "gretl_test",
      "passwordSecret": "dbPassword"
    }
  }
}
```

Pfadbasen: Job-/CLI-Dateien relativ zum Start-CWD; `runtime`-Pfade und
Secretdateien relativ zum Configordner; `file()` relativ zum Jobroot;
`ctx.path()` relativ zum Start-CWD; `workPath()` im privaten Taskworkspace;
`path`-Parameter relativ zum Start-CWD.

Unbekannte Corefelder, doppelte JSON-Schlüssel, falsche Typen, ungültige
Limits/Intervalle und widersprüchliche Secretquellen sind Code 2. Secrets werden
erst beim Zugriff aufgelöst und vor Logger/Report maskiert.

Pro Secretalias ist genau eine Quelle erlaubt: `{"environment":"GRETL_DB_PASSWORD"}`,
`{"file":"source-auth.txt"}` oder `{"plaintext":"testpasswort"}`. Die Klartextform
ergänzt `schemaVersion: 1`; bestehende Referenzen wie `passwordSecret: "dbPassword"`
und `secret('dbPassword')` bleiben gleich. Es gibt keinen automatischen Fallback.
Secretdateien enthalten den eigentlichen Wert als UTF-8, höchstens 64 KiB und
keine NUL-Zeichen; genau ein abschliessendes LF oder CRLF wird entfernt.
Environment- und Klartextwerte bleiben einschliesslich leerer Strings,
Leerzeichen und Zeilenumbrüche unverändert; keine Variablenersetzung.
`plaintext` steht direkt lesbar in der externen Runtimekonfigurationsdatei;
die Laufzeitmaskierung verschlüsselt diese Datei nicht. Alle Quellen verwenden
dieselbe Registrierung und dieselben Maskierungsgrenzen beim Zugriff.

## Logging und Runbericht

- Konsole: Text (Default) oder JSON auf **stderr**; Hilfe/Plan auf stdout.
- Ereignisdatei: `<runId>.events.jsonl` unter `runtime.reportRoot`, 0600,
  Eventlimit 16 KiB, Dateilimit 128 MiB plus Terminalreserve.
- Runbericht: `<runId>.json` bzw. `--report`, atomar ersetzt, 0600.
- Text/JSON tragen dieselbe Event-/Sequenzidentität; JSON ist ein Objekt pro
  Zeile.
- `--log-format text|json` und `--log-level` überschreiben die Config.
- Bekannte Secrets, dynamische `ctx.sensitive`-Werte und sensible Feldnamen
  werden vor allen verwalteten Senken maskiert.
- HTTP-/SQL-Fehler tragen sichere strukturierte Details; Query, Header,
  Cookies, Body und SQL-Parameter werden nicht geloggt.
- Optional schreibt `GRETL_TERMINATION_MESSAGE_PATH` eine kleine
  Kubernetes-Beendigungsmeldung.
- Frühe CLI-Fehler und Laufzeitfehler verwenden im Textmodus dieselbe Struktur:
  Zeitstempel, Level, Titel, technischer Code und eingerückte Felder wie
  `Meldung`, `Feld`, `Task`, `Quelle` und `Ursache`. JSON/JSONL bleiben
  schema-stabil.

Bibliothekslogs werden über lexikalische Run-/Task-/Invocation-Scopes zugeordnet;
`LibraryScopeLocalJobIT` prüft diesen Vertrag. Heartbeat-Timer und
Progress-Koaleszenz sind nicht umgesetzt; akzeptierte Intervallfelder sind kein
Nachweis für solche Timer. Die Maskierung gilt für verwaltete Log-/Reportsenken.
Ausführbarer eigener Groovy-Code ist keine allgemeine Sandbox.
