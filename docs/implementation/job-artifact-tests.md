# Fachliche Jobtests: JAR und vorbereitetes IMAGE

`ArtifactJobRunner` verwendet denselben kanonischen Jobbestand, dieselben
Parameter und dieselben fachlichen Java-Assertions für beide Transporte.
Die unabhängigen Leser/Clients laufen ausserhalb des Jobprozesses. Ergänzende
ältere `GretlCli.run`-Prüfungen bleiben ausdrücklich Komponententests.

Standard: `./mvnw -B clean verify`. Der normale Reactor baut die Distribution
und führt lokale `*JobIT` aus. Vollständig:
`./mvnw -B -Pgeotools-probe,backing-services clean verify`.
Dieser Weg ergänzt echte PostgreSQL/PostGIS-, FTP- und S3-Container sowie die
isolierte GeoTools-Probe. Fehlendes Docker lässt das Dienstprofil scheitern.
Alle 44 Operationen benötigen positive fachliche JAR-Nachweise; erwartete
Negativfälle prüfen Code, konkrete fachliche Ursache und/oder Originaldiagnosen.
Ein Start-/Classpathfehler genügt nicht.

JAR: Jeder Fixture-Build läuft über den regulären aktuellen Distributions-
Packager. Danach startet ein eigener `java -jar`-Prozess ohne Testclasspath.
Innerhalb derselben Test-JVM darf ein bereits regulär gebautes, bytegleiches
kanonisches Fixture-JAR erneut verwendet werden. Pro Ausführung entstehen
eigene Quellkopie, leere Ausgabe, Konfiguration, Prozess und Report. Die
Prüfsumme wird am tatsächlich gestarteten JAR berechnet; die Wiederverwendung
steht in `package-reuse.txt`. Dieser rein testseitige Build spart wiederholtes
Bündeln des Vollkatalogs, ohne einen Produkt-Runtimecache einzuführen.

`target/job-evidence/<Szenario>-<UUID>/` enthält `execution.json` mit Modus,
SHA-256 bzw. Image-ID, Exitcode, Timeout, Abbruch und Dauer, den Aufruf,
getrennte umgeleitete stdout/stderr-Dateien, Reports und unabhängige Ergebnisse.
Dateiumleitung vermeidet ungeleerte Prozesspipes. Die Suite wartet auf das
Prozessende und bereinigt bei Timeout/Abbruch. Secrets liegen in privaten
Dateien, erscheinen nicht als Containerargumentwerte und werden aus den
archivierten Konsolenlogs redigiert. Secretdateien und Rohkonfigurationen
werden nicht als CI-Artefakte hochgeladen.

## IMAGE-Vertrag für die spätere ausdrückliche Qualifikation

Im jetzigen Auftrag wird kein GRETL-Image gestartet. Die aktuelle CI-Matrix
enthält ausschliesslich JAR. `ImageExecutionAdapterTest` prüft Start, Mounts,
Pfade mit Leerzeichen, Arbeitsverzeichnis, Benutzer, Secrets, Endpoints,
Parameter, Zielauswahl, Reportpfad, unveränderte Exitcodes, Timeout und Abbruch
an einer aufzeichnenden Engine-Grenze ohne echten Containerstart.

Ein späterer Aufruf benötigt ausdrücklich:

```sh
./mvnw -B -Pgeotools-probe,backing-services clean verify \
  -Dgretl.test.executionMode=IMAGE \
  -Dgretl.test.image=registry.example/gretl:qualified \
  -Dgretl.test.imageLauncher=/opt/gretl/bin/gretl \
  -Dgretl.test.imageNetwork=gretl-test-network \
  -Dgretl.test.imageUser=1001:1001 \
  -Dgretl.test.imageExtraHost=services:host-gateway
```

Das Image muss eine vollständige zur Kandidatenversion passende Skriptruntime
mit genau diesem absoluten Launcherpfad und dieselbe JVM/Fachumgebung enthalten.
Docker-Daemon, Image und ausdrücklich vorbereitetes benanntes Dienstnetz müssen
vorhanden sein. Der Adapter zieht kein Image nach und fällt nie auf JAR zurück;
fehlende Voraussetzungen sind Fehler. Vor dem Start löst `docker image inspect`
die explizite Referenz in die unveränderliche `sha256:…`-Image-ID auf und startet
genau diese. Ein erfolgreich übersprungener IMAGE-Fall ist nicht vorgesehen.

Benutzer und Gruppe sind numerisch und nicht root; die UID muss das private
Szenarioverzeichnis besitzen und Zugriff auf die Mounts haben. Die private
Dateiumgebung bleibt privat. HOME, TMPDIR sowie die JVM-Properties user.home
und java.io.tmpdir liegen in vorbereiteten beschreibbaren Unterverzeichnissen
von /work; native Bibliotheken und DuckDB-Erweiterungen benötigen keine
Schreibrechte im Image. Der Aufruf verwendet `--rm --init`, Signalweitergabe,
`--user`, das benannte Netz und `/run` als Arbeitsverzeichnis. Docker muss die
lokalen Bind-Mount-Quellen erreichen können; ein fremder Docker-Host ohne diese
Dateien erfüllt die Voraussetzung nicht.

| Hostbereich | Containerpfad | Zugriff |
| --- | --- | --- |
| frische kanonische Quell-/Eingabekopie | /job | nur lesen |
| Ausgabe | /outputs | lesen/schreiben |
| Runtime-Arbeit | /work | lesen/schreiben |
| Reports | /reports | lesen/schreiben |
| Arbeitsverzeichnis | /run | lesen/schreiben |
| übersetzte Runtimekonfiguration | /config/runtime.json | nur lesen |
| ausdrücklich konfigurierte Secretdateien | /secrets/Alias | nur lesen |

Lokale Pfadwerte in Parametern, Environment und Fachkonfiguration werden im
gemeinsamen Java-Request als `java.nio.file.Path` übergeben. JAR serialisiert
sie zu denselben CLI-/JSON-Textwerten; IMAGE übersetzt sie anhand dieser
Mounts. Ein nicht gemounteter absoluter `Path` scheitert. Relative
Parameterpfade bleiben relativ zum jeweiligen Arbeitsverzeichnis.

Strings bleiben Textwerte. Der FTP-Serverpfad `remoteDir=/home/gretl/<lease>`
ist beispielsweise in beiden Modi derselbe String und keine lokale
Mountquelle. Runtimeidentität und die entsprechenden GRETL-ENV-Werte sind
opaque, auch bei führendem `/`. Die normativ bekannten lokalen Pfadfelder
`runtime.workRoot`, `runtime.reportRoot`, Secretdateien und
`GRETL_TERMINATION_MESSAGE_PATH` werden ausdrücklich als Pfade behandelt;
relative Runtimepfade beziehen sich weiterhin auf das Konfigurationsverzeichnis.
Dienstendpoints erhalten
explizite Host-/Image-Paare; localhost-Endpunkte ohne Zuordnung scheitern.
PostgreSQL-, FTP- und S3-Fixtures treten dem benannten Netz als `postgres`, `ftp`
und `s3` bei. FTP verwendet dort die passive Adresse `ftp`; der unabhängige
Hostclient verwendet EPSV. Die lokalen HTTP-Fixtures binden alle Interfaces;
`gretl.test.httpImageHost` ist standardmässig `services`, konfigurierbar für die
erreichbare Hostadresse. `imageExtraHost` stellt bei Bedarf die Docker-
`host-gateway`-Zuordnung her. Es wird kein Hostnetz vorausgesetzt.

`docker stop --time 35` gibt der Runtime ihren begrenzten Abschlusszeitraum.
Timeout/Abbruch beenden und reap-en auch den Docker-Client; verbleibende
Testcontainer werden ausdrücklich entfernt. Normales Ende bewahrt Exitcode,
Ausgaben und Report. Die zusätzlichen ausdrücklich als SCRIPT geführten
Phase-1-Regressionen bleiben Host-Skriptstarts und werden so protokolliert.
Die IMAGE-Fälle starten ausschliesslich den Produktlauncher im Produktimage.
