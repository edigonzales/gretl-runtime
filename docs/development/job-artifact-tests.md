# Fachliche Jobtests mit SCRIPT, JAR und IMAGE

`ArtifactJobRunner` startet die gebaute Distribution standardmässig mit
externen Groovy-Scripts oder Projekten. Explizite IMAGE-Tests starten den
gleichen Launcher im generischen Container. Explizite JAR-Tests prüfen
vorkompilierte, eigenständige Job-JARs aus einem fremden Arbeitsverzeichnis.
Die direkten `GretlCli.run`-Tests prüfen CLI-Komponenten im Testprozess.

## Einstieg und Pflichtumfang

Starte den folgenden Befehl mit JDK 25, Node 22, Docker und Python 3.12 im Repository-Root.

```sh
python3.12 scripts/verify-runtime.py
```

Der Aufruf startet einen Maven-Reaktor mit Dienst-, GeoTools- und
IMAGE-Profil. Fehlende Docker-, Netzwerk-, Mount- oder Imagevoraussetzungen
führen zu einem Fehler. Der Docker-freie Standardlauf lautet
`./mvnw -B clean verify`.

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
bekommt eine eigene Kopie. Negative Paketfälle prüfen unter anderem
fehlende Fragmente, Ressourcen und ungültige Metadaten. `PackageArtifactAssertions`,
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
ein externes Projekt, PostGIS mit kontrollierter Cancellation, S3, HTTP
und erzwungenen Timeout. Eine zusätzliche
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
Containerpaare. PostgreSQL und S3 verwenden im eigenen Testnetz die
Aliasse `postgres` und `s3`. HTTP bindet alle Interfaces und nutzt
auf macOS `host.docker.internal`, auf Linux `services:host-gateway`.
Hostnetzwerk und vollständige HOME-/Testclasspath-Mounts werden nicht benötigt.

Explizite Cancellation liefert SIGTERM ohne auf das Cleanup zu warten.
Beim Timeout begrenzt `docker stop --time 35` zuerst die Containerlaufzeit;
der Client wird anschliessend begrenzt reap-ed. Exit 137/Force-kill wird
nicht als kontrolliertes CANCELLED ausgegeben. Nur eigene Container und
das eigene markierte Netz werden entfernt.

## Operationsabdeckung und Diagnose

Die [Soll-Liste](../../tests/contracts/operation-baseline.json) enthält
45 Standardoperationen. Nach erfolgreichen JUnit-Assertions sammelt die
Testsuite die in echten SCRIPT-Reports erfolgreich ausgeführten IDs.
Maven prüft die Liste bei `verify` im Dienstprofil. Fehlende IDs oder
ein leeres Ergebnis schlagen den Build fehl.

Lokale SCRIPT- und JAR-Diagnosen liegen unter
`gretl-integration-tests/target/`. Die IMAGE-Arbeitsverzeichnisse liegen
in einem privaten temporären Verzeichnis, das der Wrapper nach dem Lauf
entfernt. JUnit-Reports bleiben im Maven-Modul. Die Tests prüfen die Unveränderlichkeit
der staged SCRIPT- und IMAGE-Eingaben, die Distributionsbytes und die
tatsächlich ausgewählte Image-ID. Der CI-Upload umfasst bei Erfolg die
Bindist und deren SHA-256-Liste. Bei Fehlern enthält das Diagnoseartefakt
betroffene JUnit-Fälle und begrenzte Logauszüge der zugehörigen Produktprozesse.

## CI und Aussagegrenzen

`publish-snapshots.yml` führt `python3.12 scripts/verify-runtime.py` bei Push auf
`main`, bei Pull Requests gegen `main` und bei manuellem Start aus.
Der Reaktor prüft alle 45 Standardoperationen in SCRIPT und führt
gezielte JAR- und IMAGE-Prozesse aus. Der Publish-Job hängt vom
erfolgreichen Verify-Job ab und läuft auf `main`.

Eine Workflowdatei ist kein ausgeführter Remote-CI-Lauf. Prüfbelege
nennen die tatsächlich ausgeführten Gates und Plattformen;
Branch-Protection-Einstellungen bleiben eine administrative Aufgabe.
JaCoCo misst instrumentierte Test-JVMs. Separate Launcher-, Job-JAR- und
Containerprozesse werden fachlich geprüft, ohne erfundene Coverage.
