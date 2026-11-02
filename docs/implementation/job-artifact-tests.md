# Fachliche Jobtests mit SCRIPT, JAR und IMAGE

`ArtifactJobRunner` verwendet kanonische Quellen, Parameter und dieselben
unabhängigen fachlichen Java-Assertions in drei Modi. **SCRIPT ist der
Default.** Er startet die gebaute Distribution mit einer externen Groovy-Datei
oder einem Projekt. IMAGE startet denselben SCRIPT-Weg im generischen
Runtime-Container. JAR bleibt eine zusätzliche vorkompilierte Auslieferungsform.
Ältere `GretlCli.run`-Tests bleiben ausdrücklich Komponentenprüfungen.

## Einstieg und Pflichtumfang

JDK 25 und der vorhandene Maven-Wrapper sind erforderlich. Vom sauberen
Repository-Root, mit Nachweisen ausserhalb von Maven-`target`:

```sh
# Alltag: Unit-, Komponenten-, Distributions-, lokale SCRIPT- und Pakettests; ohne Docker.
./mvnw -B clean verify

# Vollständige einzelne Modi einschliesslich echter Dienste und GeoTools-Probe.
scripts/verify-job-matrix.sh --mode SCRIPT --scope full --output ../gretl-script-full
scripts/verify-job-matrix.sh --mode JAR --scope full --output ../gretl-jar-full
scripts/verify-job-matrix.sh --mode IMAGE --scope full --output ../gretl-image-full

# Gesamte Qualifikation auf demselben Commit.
scripts/verify-job-matrix.sh --mode ALL --scope full --output ../gretl-all-full

# Lokale Containerfälle ohne die externen PostgreSQL-/FTP-/S3-Dienste.
scripts/verify-job-matrix.sh --mode IMAGE --scope local --output ../gretl-image-local
```

IMAGE/local benötigt Docker für den Produktcontainer. Fehlende Docker-,
Netzwerk-, Mount- oder Imagevoraussetzungen sind Fehler, kein Skip und kein
Fallback auf einen anderen Modus. Vollständige Modi benötigen je alle 44
positiven Standardoperationen. Eine Vereinigungsmenge verschiedener Modi
kann diese Pflicht nicht erfüllen. Teiltests dienen der Entwicklung; sie
ersetzen die jeweils fälligen Phasengates nicht.

## Vorbereitung und echte Prozesse

`JobInput` unterscheidet eine Scriptdatei von einer Projektwurzel;
`Invocation` trägt `run`, `check`, `plan`, Graphausgabe oder Hilfe. Der
Default ist die Datei `job.groovy`, nicht ein eingebauter Job. Ein Projekt
wird ausdrücklich mit `JobInput.project(".")` gewählt. SCRIPT und IMAGE
benötigen keinen Paketdeskriptor und ignorieren einen unbenutzten kaputten
`job-package.json`. Ungültige Groovy-/Settings-Inhalte erreichen den
wirklichen CLI-Prozess; das bytebasierte Inputinventar parst sie nicht vorab.

SCRIPT startet `<geprüfte-distribution>/bin/gretl run <externe-quelle>` ohne
Testclasspath oder Paketierung. JAR verwendet den regulären aktuellen
Distributionspackager und danach einen eigenen `java -jar`-Prozess. Der
Cache berücksichtigt Runtime-, Input- und Descriptorbytes; jede Ausführung
bekommt eine eigene Kopie. Manipulierte JARs werden unter ihrem tatsächlichen
Dateinamen gehasht und archiviert. `PackageArtifactAssertions`,
`SelectedPackage*`, `ProjectArtifactLocalJobIT` und die ursprünglichen
Schema-/Modul-/Toplevel-/Isolationstests bleiben ausdrücklich erhalten.

## Generisches IMAGE und OpenShift

Das Profil `job-image` baut nach der Distribution das generische
[Runtime-Image](../../gretl-dist/src/main/docker/README.md). Sein tatsächlicher
Default-Entrypoint ist `/opt/gretl/bin/gretl`; gestartet wird beispielsweise
`run /job/job.groovy` oder `run /job`. Kein Job-JAR, kein Entrypointoverride
und kein Job im Buildkontext. Der Builder vergleicht die tatsächlichen
`/opt/gretl`-Bytes im Image mit der gebauten Distribution; Labels allein
genügen nicht. Der Adapter verwendet die unveränderliche Image-ID aus
`candidate.json` und zieht beim Jobstart kein anderes Image nach.

| Hostbereich | Containerpfad | Zugriff |
| --- | --- | --- |
| private Quell-/Eingabekopie | `/job` | read-only |
| Ausgabe | `/outputs` | lesen/schreiben |
| Runtime-Arbeit | `/work` | lesen/schreiben |
| Reports | `/reports` | lesen/schreiben |
| fremdes Start-CWD | `/run` | lesen/schreiben |
| übersetzte Runtimekonfiguration | `/config/runtime.json` | read-only |
| ausdrücklich konfigurierte Secretdateien | `/secrets/Alias` | read-only |

Die normalen privaten Bindmounts verwenden die numerische non-root UID/GID
des Hostbenutzers. Root und Jobmount sind read-only, Capabilities entfernt,
`no-new-privileges` gesetzt. Native Bibliotheken benötigen ausführbaren
Tempbereich. Der Adapter setzt HOME/Temp auf vorbereitete private
`/work/home` und `/work/tmp`; das Image selbst verwendet `/work` und `/tmp`,
damit frisch eingehängte leere Volumes funktionieren.

`ImageRuntimeIT` prüft nach Imagebau erzeugte und geänderte externe Jobs
mit unveränderter Image-ID, echte SQLite-/DuckDB-Arbeit, read-only Quellen,
kontrollierte Cancellation und erzwungenen Timeout. Eine zusätzliche
OpenShift-Gegenprobe verwendet unbekannte UID mit Gruppe 0 und frischen
Volumes; siehe [Jobvorlage](../../gretl-dist/src/main/docker/openshift-job.yaml).
Kein passwd-Write, keine privilegierte SCC und keine 777-Reparatur.
Clusteradmission, SELinux-/PVC-Konfiguration und produktive Jobs wurden lokal
nicht ausgeführt. Die Qualifikation benennt ihre tatsächliche Architektur;
ein Multiarch-Basisindex ist keine ausgeführte Freigabe aller Plattformen.

Lokale Pfadwerte im Java-Request sind `Path`; nur diese und die normativ
bekannten Runtime-/Secretpfade werden übersetzt. Relative Parameterpfade
bleiben relativ zum jeweiligen Start-CWD. Strings bleiben Businesswerte:
`remoteDir=/home/gretl/<lease>` ist ein FTP-Serverpfad, keine Mountquelle.
Runtimeidentitäten sind opaque. Endpoints erhalten ausdrückliche Host-/
Containerpaare. PostgreSQL, FTP und S3 verwenden im eigenen Testnetz die
Aliasse `postgres`, `ftp` und `s3`. HTTP bindet alle Interfaces und nutzt
auf macOS `host.docker.internal`, auf Linux `services:host-gateway`.
Hostnetzwerk und vollständige HOME-/Testclasspath-Mounts werden nicht benötigt.

Explizite Cancellation liefert SIGTERM ohne auf das Cleanup zu warten.
Beim Timeout begrenzt `docker stop --time 35` zuerst die Containerlaufzeit;
der Client wird anschliessend begrenzt reap-ed. Exit 137/Force-kill wird
nicht als kontrolliertes CANCELLED ausgegeben. Nur eigene Container und
das eigene markierte Netz werden entfernt.

## Sollbestand, JUnit und Evidenz

Der versionierte [Szenariokatalog](../../gretl-integration-tests/src/test/resources/qualification/scenarios.json)
ordnet alle Methoden/Parameterinstanzen COMMON, SCRIPT_CONTRACT,
IMAGE_CONTRACT, PACKAGE_CONTRACT oder COMPONENT zu. Feste `expectedStarts`
pro Methode und deklarierte Startvarianten sind unabhängig von Ist-Logs.
Eine Katalogreduktion ist sichtbar und wird gegen diese Startzahlen sowie
die vorhandenen Java-Testmethoden geprüft. Es gibt keinen Generator, der
fehlende Ergebnisse in einen kleineren Sollbestand umwandelt.

Pro Start liegen unter `target/job-evidence/<Szenario>-<UUID>/`:
`execution.json`, `command.json`, Inputinventar, getrennte stdout/stderr,
passende Reports/Ereignisse und fachliche Ausgaben. JAR ergänzt tatsächliche
JAR-/Paketinventare, IMAGE den tatsächlichen Container-/Imagebeleg. Die
JUnit-Erweiterung bindet jeden Start an die ausgeführte Methode und Variante.
Eine gültige Kindprozessausführung ohne erfolgreiche unabhängige JUnit-
Assertion ist kein qualifizierter Erfolg.

Der Audit prüft Modus, Run, Quellen-SHA, vollständige Szenarien, tatsächliche
Runtime-/JAR-/Imagebytes, Report-/Eventidentität und Prozesslebensdauer.
Zeitvergleiche behalten volle Nanosekundenpräzision auch auf Python 3.9.
Der gewöhnliche Mavenweg bekommt eine gemeinsame Build-Run-ID; der
Matrixstarter setzt zusätzlich UUID und expliziten Kandidaten-SHA.
Explizite Matrixprüfungen verlangen diese JUnit-Kandidatenbindung.
`--full` bleibt ein Alias des vollen Scopes.

Der Matrixstarter sichert vor jedem `clean` einen kuratierten Bestand,
dessen Auditor ohne Live-Workspace funktioniert. Er enthält auch redigierte
Dienstdiagnosen, den festen Katalog und tatsächlich referenzierte Bytes.
Rohkonfigurationen, Secretdateien, Sourcebäume und Workspaces werden nicht
pauschal veröffentlicht. Ausgaben werden nicht überschrieben. Der Starter
propagiert Maven-/Audit-/Dockerfehler und schreibt `complete.json` erst nach
erfolgreichem Ende und Cleanup. ALL verlangt drei vollständige Nachweise
mit demselben Quellencommit und bytegleichen COMMON-Inputs. Vor dem
Matrixabschluss werden alle drei Archive nach den späteren clean-Läufen
erneut über den wirklichen Audit geprüft.

Gezielte Negativkopien werden so geprüft:

```sh
python3 tests/script-first-testing/test_job_evidence.py \
  --mode IMAGE --scope full --evidence-root ../gretl-all-full/IMAGE/evidence
```

E01–E12 decken fehlende/vertauschte/alte Belege, falsche Bytes, Reports,
JUnit-Failure/Error/Skip, getrennte Operationsmengen, Sourcekonsistenz,
STARTED ohne Abschluss, Maven-/Audit-/Signalfehler, RecordingEngine und
reduzierten Erwartungsbestand ab. Signalproben bereinigen eigene Ressourcen
und kontrollieren, dass ein eigener fremd markierter Kontrollcontainer lebt.

## CI und Aussagegrenzen

Push und Pull Requests rufen über `publish-snapshots.yml` den gemeinsamen
`qualification.yml` auf: Dockerfreier Standard und vollständiges ALL/full.
Snapshot-Publikation sowie Release-Candidate-Auslieferung hängen von beiden
Gates ab. Der Releasepfad verwendet die bereits qualifizierte ZIP-Datei und
bewahrt seine zusätzliche Checksum-/Paketstartprobe. Der manuelle
`image-qualification.yml` dient zusätzlicher IMAGE-Diagnose und ist nicht
der einzige Imageprüfweg. Artefakte sind je Modus/SHA getrennt und kuratiert.

Eine Workflowdatei ist kein ausgeführter Remote-CI-Lauf. Die lokalen
Phasenberichte nennen tatsächlich ausgeführte Gates und Plattformen;
Branch-Protection-Einstellungen bleiben eine administrative Aufgabe.
JaCoCo misst instrumentierte Test-JVMs. Separate Launcher-, Job-JAR- und
Containerprozesse werden fachlich geprüft, ohne erfundene Coverage.
