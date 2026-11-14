# Build und Distribution

## Voraussetzungen

Verwende JDK 25 und den eingecheckten Maven-Wrapper (Maven 3.10.0).
Der Build benötigt Maven Central und die in den POMs deklarierten
INTERLIS-Repositories, das GeoTools-Profil zusätzlich OSGeo.
Docker ist für das vollständige Dienstprofil erforderlich.

```sh
./mvnw -version
./mvnw -B clean verify
```

Der Standardbuild erzeugt auch die Distribution. Die
[Teststrategie](testing.md) erklärt die lokale Testauswahl, vollständige
Maven-Gates und die CI-Qualifikation der Ausführungsmodi. Versionen und
Abhängigkeiten stehen im Root-POM; der
[Modulgraph](../architecture/module-boundaries.md) beschreibt die Grenzen.

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
Compilerisolation erklärt der [Paketierungsvertrag](../guides/packaging.md).
Projekt-JARs und Einzeljob-Schemas 1/2 bleiben getrennt im Deskriptor, verwenden
aber dieselbe Runtime. GeoTools wird nicht in die Standarddistribution aufgenommen.

## VS Code Extension

Maven führt bei `verify` die Hosttests und nach der Paketierung die VSIX-Tests
aus. In `vscode/gretl-vscode` baut `npm run package` die VSIX samt Inventar.
`npm test` prüft den Extension-Host, `npm run test:package` die gebaute VSIX.
Mit `-DskipTests` überspringt Maven beide Testschritte. Typprüfung, Build und
Paketierung laufen weiterhin.

## Dokumentationsprüfung

Nach einem aktuellen Distributionsbuild, aus dem Repository-Root:

```sh
./gretl-dist/target/dist/bin/gretl operations --format adoc --lang de --output docs/reference/generated
python3 -m unittest discover -s tests/docs -v
python3 scripts/check-docs.py
quarto render website/
```

Die 51 generierten Tasktabellen werden gegen die Distribution und die Includes
in `reference.adoc` geprüft. Die [Referenzanleitung](../reference/README.md)
beschreibt den tatsächlichen Thoth-Biblios-Build. Quarto rendert die Website
nach `website/_site/`; [Website-Pflege](../../website/README.md) beschreibt die
Quellen. Generierte Ausgaben und lokale Thoth-Konfigurationen werden ignoriert.
Bei Änderungen an Quickstarts müssen deren Befehle laufen und Ausgabedateien,
Inhalt, Exitstatus und Reports geprüft werden. Ein statischer Linkcheck genügt
für diese Ausführungsnachweise nicht.

Die dokumentierten Quickstarts und beide Renderwege lassen sich gemeinsam
mit einer aktuellen Distribution und dem vorhandenen Thoth-JAR prüfen:

```sh
python3 scripts/check-doc-examples.py --evidence ../gretl-doc-check \
  --thoth ../thoth/thoth-biblios/build/libs/thoth-biblios-0.0.1-SNAPSHOT-all.jar
```

Wähle ein neues Belegverzeichnis. Der Lauf vergleicht auch die erzeugten
Tasktabellen mit den getrackten Referenzquellen.

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

[Laufzeitwerte, Pfade, Secrets und Berichte](../guides/runtime-configuration.md)
stehen im Konfigurationsguide.
