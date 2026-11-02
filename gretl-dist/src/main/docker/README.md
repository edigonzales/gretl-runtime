# Generisches Runtime-Image und OpenShift

Das [Dockerfile](Dockerfile) kopiert ausschliesslich die geprüfte Runtime-
Distribution. Der Entrypoint ist `/opt/gretl/bin/gretl`; der externe Aufruf
lautet `run /job/job.groovy` oder `run /job`. Der Job wird nicht in ein JAR
paketiert und nicht ins Image eingebaut. Allgemeine Distributionsbeispiele
bleiben Teil der regulären Distribution.

Der lokale Authoring-Aufruf aus dem Repository-Root ist:

```sh
scripts/build-runtime-image.sh \
  --distribution gretl-dist/target/dist --output gretl-dist/target/image
```

Der Builder prüft alle Distributionsbytes, baut ohne Push, ermittelt die
unveränderliche Image-ID und liest `/opt/gretl` aus dem tatsächlichen Image
zur unabhängigen Byteprüfung aus. `candidate.json` enthält Quelle,
Manifesthash, Image-ID, Plattform, Basisdigest und Buildkommando.
Das Maven-Profil `job-image` führt diesen Schritt nach dem Distributionsbuild
und vor den Integrationstests aus. Der Standardbuild benötigt kein Docker.

Der dokumentierte lokale Prüfweg am sauberen Kandidaten ist:

```sh
scripts/verify-job-matrix.sh --mode IMAGE --scope local --output ../gretl-image-local
scripts/verify-job-matrix.sh --mode IMAGE --scope full --output ../gretl-image-full
```

Beide benötigen einen erreichbaren Docker-Daemon und einen non-root
Hostbenutzer für private Bindmounts. Der Starter baut selbst die Runtime,
legt ein eigenes Netz an, prüft tatsächliche Containerläufe und bewahrt
Berichte vor dem nächsten `clean`. FULL umfasst zusätzlich die echten
Backing Services und den GeoTools-Probe. Ausgaben müssen ausserhalb von
Maven-`target` liegen; bereits belegte Modusausgaben werden nicht ersetzt.

Die Basis ist Java 25 auf Ubuntu 24.04, mit einem am 01.11.2026 tatsächlich
aufgelösten OCI-Indexdigest im Dockerfile gepinnt. Die lokale Qualifikation
verwendet Linux/ARM64; ein vorhandener AMD64-Eintrag im Basisindex ist kein
bereits ausgeführter AMD64-Test.

## Beliebige UID statt fester Benutzerannahme

OpenShift kann dem Container eine UID aus dem Namespacebereich zuweisen.
Das Image verwendet als numerischen Standard `10001:0`; es benötigt weder
einen passenden Benutzernamen in `/etc/passwd` noch Schreibrechte darauf.
Die Runtime unter `/opt/gretl` ist lesbar und muss nicht beschreibbar sein.
Nur die eigenen Datenverzeichnisse sind für Gruppe 0 beschreibbar. Dies
folgt den [Red-Hat-Empfehlungen für beliebige UIDs](https://developers.redhat.com/articles/2021/11/11/best-practices-building-images-pass-red-hat-container-certification).

`HOME=/work` und die Java-Eigenschaft `user.home=/work`, `TMPDIR=/tmp` und
`java.io.tmpdir=/tmp` funktionieren auch mit frisch eingehängten leeren
Volumes. JNI benötigt ein ausführbares Temp-Dateisystem: kein `noexec` für
`/tmp` beziehungsweise einen ausdrücklich gewählten Native-Temp-Pfad.
SQLite und DuckDB werden mit unbekannter UID und Gruppe 0 tatsächlich
geprüft; allein `--version` genügt nicht.

Die [Jobvorlage](openshift-job.yaml) setzt keine feste `runAsUser`, keine
privilegierte SCC und keine zusätzlichen Capabilities voraus. Die SCC des
Clusters liefert UID, SELinux-Kontext und zulässige Volumegruppen. Die
Volumes müssen für diese Identität beschreibbar sein; bei persistenten
Volumes müssen deren Eigentums-/Gruppenrechte zum Namespace passen.
`/job` und die Runtimekonfiguration werden read-only eingebunden;
`/work`, `/tmp`, `/reports`, `/outputs` und `/run` sind beschreibbare Volumes.
Die Vorlage gibt dem 30-Sekunden-Abschlussbudget 45 Sekunden Pod-Grace.
Das Beispiel verwendet `emptyDir`; dauerhafte Ergebnisse benötigen einen
passenden PVC oder einen externen fachlichen Export.

Die Vorlage ist vor Verwendung mit einer tatsächlichen veröffentlichten
Image-Referenz nach Digest zu ersetzen. Dieser Auftrag baut und prüft lokal;
er führt weder Registry-Push noch einen Job im OpenShift-Cluster aus.
Die Docker-Gegenprobe deckt beliebige UID, Gruppe 0, read-only Root und
Quelle, `no-new-privileges`, entfernte Capabilities und native Bibliotheken
ab. Eine Clusterzulassung beziehungsweise ein konkreter PVC wurde damit
nicht als ausgeführt ausgegeben.

Minimaler Inhalt für `runtime.json`:

```json
{"schemaVersion":1,"runtime":{"workRoot":"/work","reportRoot":"/reports"},"secrets":{},"taskConfig":{}}
```

Ein einfacher externer `job.groovy` für diese Vorlage:

```groovy
parameter('output', type: 'path', required: true, description: 'External output')
action('write') { java.nio.file.Files.writeString(parameter('output'), 'OpenShift') }
defaults 'write'
```
