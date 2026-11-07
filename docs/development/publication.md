# Snapshot-Veröffentlichung auf jars.interlis.guru

**Version:** `6.0.0-SNAPSHOT`\
**Repository:** `https://jars.interlis.guru/snapshots`  
**Server-ID:** `interlis-snapshots`

## 1. Zweck und Grenze

Veröffentlicht werden die produktiven Maven-Modul-JARs, ihre POMs und die
Scriptdistribution. Nicht veröffentlicht werden:

- `examples/file-size-extension` (Fixture),
- `gretl-geotools-probe` (nur Testprofil),
- job-spezifische Ausgaben von `gretl package`.

Ein mit `gretl package` erzeugtes Job-JAR ist an einen Job und seine
Ressourcen gebunden. Es hat keine stabile Maven-Koordinate und wird nicht
automatisch als Produktartefakt veröffentlicht. Für einen konkreten Job ist
ein separater `maven-deploy-plugin:deploy-file`-Aufruf nötig.

## 2. Credentials

Die Zugangsdaten kommen ausschliesslich aus:

```sh
export MAVEN_USERNAME='...'
export MAVEN_PASSWORD='...'
```

`scripts/publish-settings.xml` enthält nur die Platzhalter
`${env.MAVEN_USERNAME}` und `${env.MAVEN_PASSWORD}`. Maven löst sie beim
Deploy auf; die Settings-Datei enthält keine Secrets. Das Passwort wird in
Maven-Ausgaben maskiert.

## 3. Veröffentlichen über GitHub Actions

Im GitHub-Repository müssen unter **Settings → Secrets and variables → Actions**
folgende Repository-Secrets hinterlegt sein:

| Secret | Inhalt |
| --- | --- |
| `MAVEN_USERNAME` | Maven-Benutzername bzw. Reposilite-Tokenkennung |
| `MAVEN_PASSWORD` | Zugehöriges Passwort bzw. Token-Secret |

Der Zugang benötigt Schreibrechte für die veröffentlichten Maven-Pfade unter
`snapshots`. Bereits gültig hinterlegte Secrets weiterverwenden; für diesen
Weg werden keine zusätzlichen Secrets benötigt. Der Workflow
`.github/workflows/publish-snapshots.yml` heisst **verification and snapshots** und
läuft bei Push auf `main`, bei Pull Requests gegen `main` und manuell über
`workflow_dispatch`. Ein manueller Start auf `main` publiziert nach erfolgreichem Verify.

Ablauf:

1. Der Verify-Job führt die konsolidierte Prüfung mit JDK 25, Node und
   Docker aus. Die qualifizierte ZIP- und TAR-Distribution werden zusammen
   mit ihren SHA-256-Prüfsummen als ein Artefakt gesichert.
2. Der Publikationsjob beginnt erst nach erfolgreichem Verify auf `main`
   und prüft die übergebenen Archivbytes. Pull Requests veröffentlichen nie.
3. Der Maven-Deploy publiziert den Reaktor und hängt die übergebenen ZIP-
   und TAR.GZ-Dateien unverändert an `gretl-dist`. Die für diesen Deploy
   aktivierte Option `-Dgretl.dist.reuseQualifiedArchives=true` verhindert
   das erneute Erstellen dieser beiden Archive. Die SHA-256-Prüfung läuft
   vor und nach dem Deploy. Die Testskips gelten nur im Deploy-Job.
4. Nach dem Deploy lädt `scripts/verify-published-runtime.py` beide Archive
   anhand der timestamped Einträge in `maven-metadata.xml`. Der Check
   vergleicht beide SHA-256-Digests mit der Qualifikation und prüft
   ZIP-Inhalt, Runtimeversion und sauberen Quellencommit. Mit genau diesem Launcher wird anschliessend ein neuer
   externer Groovy-Job ohne Paketdeskriptor aus einem fremden Arbeitsverzeichnis
   gestartet. Exit 0, Report `SUCCEEDED` und exakt `snapshot-smoke-ok\n`
   sind Pflicht; sonst scheitert die Action auch nach erfolgreichem Upload.

Manuell starten:

```sh
gh workflow run publish-snapshots.yml --ref main
gh run list --workflow publish-snapshots.yml
gh run watch <run-id>
```

Alternativ im GitHub-UI unter **Actions → verification and snapshots → Run workflow**.

Push, Merge, Release und produktive Veröffentlichung benötigen einen
ausdrücklichen Auftrag. Die lokale Prüfung des Publishwegs verwendet ein
privates Maven-Dateirepository, einen lokalen HTTP-Server und synthetische
Zugangsdaten. Maven erzeugt timestamped Snapshot-Artefakte und
`maven-metadata.xml`. Ein unvollständiger Upload oder fehlgeschlagener
Download-Smoke schlägt den Publish-Job fehl.

## 4. Veröffentlichte Koordinaten

Gruppe: `ch.so.agi.gretl`

- `gretl-runtime-parent`
- `gretl-language-contract`, `gretl-script-syntax` und `gretl-tooling-core`
- `gretl-tooling-catalog` (einschliesslich `catalog`-Classifier)
- `gretl-lsp` (einschliesslich `jar-with-dependencies`-Classifier)
- `gretl-vscode` (POM)
- `gretl-api`
- `gretl-runner`
- `gretl-groovy`
- `gretl-i18n`
- `gretl-logging`
- `gretl-packager`
- `gretl-database-tasks`
- `gretl-io-tasks`
- `gretl-interlis-tasks` (einschliesslich `standard-functions`-Classifier)
- `gretl-cli`
- `gretl-job-runtime`
- `gretl-ilivalidator`
- `gretl-dist`
- `gretl-integration-tests` (Modulmetadaten und Hauptartefakt)

`gretl-dist` wird zusätzlich mit der Scriptdistribution veröffentlicht:

| Classifier | Datei | Typ |
|---|---|---|
| `bin` | `gretl-runtime-6.0.0-SNAPSHOT-bin.zip` | `zip` |
| `bin-tar-gz` | `gretl-runtime-6.0.0-SNAPSHOT-bin.tar.gz` | `tar.gz` |

Beispiel zum Auflösen der ZIP:

```xml
<dependency>
  <groupId>ch.so.agi.gretl</groupId>
  <artifactId>gretl-dist</artifactId>
  <version>6.0.0-SNAPSHOT</version>
  <classifier>bin</classifier>
  <type>zip</type>
</dependency>
```

## 5. Prüfung nach dem Deploy

Remote-Metadaten:

```sh
curl -fsS https://jars.interlis.guru/snapshots/ch/so/agi/gretl/gretl-api/6.0.0-SNAPSHOT/maven-metadata.xml
curl -fsS https://jars.interlis.guru/snapshots/ch/so/agi/gretl/gretl-dist/6.0.0-SNAPSHOT/maven-metadata.xml
```

Die Metadaten müssen eine aktuelle `<timestamp>`/`<buildNumber>` und
`<snapshotVersions>` für POM/JAR/ZIP enthalten.

Distribution herunterladen, prüfen und mit einem externen Job starten
(benötigt Python 3 und JDK 25; `SOURCE_SHA` ist der volle veröffentlichte
Git-Commit, `OUTPUT` ein neues Verzeichnis und `CHECKSUMS` die SHA-256-Liste
aus dem qualifizierten Build):

```sh
SOURCE_SHA=$(git rev-parse HEAD)
OUTPUT=/tmp/gretl-snapshot-check
CHECKSUMS=/pfad/zur/qualifizierten/distribution.sha256
python3 scripts/verify-published-runtime.py \
  --repository https://jars.interlis.guru/snapshots \
  --version 6.0.0-SNAPSHOT --source "$SOURCE_SHA" \
  --checksums "$CHECKSUMS" --output "$OUTPUT"
"$OUTPUT/distribution/gretl-runtime-6.0.0-SNAPSHOT/bin/gretl" run /pfad/job.groovy
```

Der Download verwendet immer den timestamped Dateinamen aus `<snapshotVersions>`;
ein nicht garantiert vorhandener unversionierter ZIP-Alias wird nicht benötigt.
Die lokale ZIP heisst `gretl-runtime-6.0.0-SNAPSHOT-bin.zip`; remote verwendet
Maven den Artefaktnamen `gretl-dist-<timestamped-version>-bin.zip`.
Die vollständige Distribution enthält Launcher und Bibliotheken. Einzelne
Modul-JARs allein sind kein Ersatz für diesen SCRIPT-Einstieg.

Ein lokaler Vorbereitungstest deployt mit `-DaltDeploymentRepository=id::file:///…`
in ein privates Dateirepository und stellt es per lokalem HTTP-Server bereit.
Er benötigt keine echten Repository-Credentials und veröffentlicht nichts remote.
Die regulären Download-Kontrolltests laufen mit
`python3 -m unittest discover -s tests/publication -v`; ihre kleinen ausführbaren
Fixtures prüfen den Kontrollfluss, während die Abnahme zusätzlich eine echte
Distribution nach lokalem Maven-Deploy herunterlädt und ausführt.

## 6. Fehlerfälle

| Symptom | Vorgehen |
|---|---|
| `MAVEN_USERNAME`/`MAVEN_PASSWORD` fehlen | Skript bricht vor dem Netzwerkaufruf ab |
| HTTP 401/403 | Credentials/Server-ID prüfen; keine Secrets ausgeben |
| `distributionManagement` fehlt | Profil `publish-snapshots` aktivieren |
| ZIP/TAR fehlt remote | `build-helper`-Attach und `verify`-Phase prüfen |
| `maven-metadata.xml` veraltet | Deploy erneut ausführen; Snapshot-Update abwarten |
| Test-/Fixture-Artefakt remote | `maven.deploy.skip` im Modul prüfen |

## 8. Sicherheit

- Keine Credentials in Git, POMs, Skripten oder Logs.
- Keine `settings.xml` mit Klartextpasswort einchecken.
- `MAVEN_PASSWORD` nicht in der Prozessliste als Argument übergeben.
- Die Settings-Datei enthält nur Environment-Platzhalter.
