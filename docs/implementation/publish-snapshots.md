# Snapshot-Veröffentlichung auf jars.interlis.guru

**Version:** `0.1.0-SNAPSHOT`  
**Repository:** `https://jars.interlis.guru/snapshots`  
**Server-ID:** `interlis-snapshots`

## 1. Zweck und Grenze

Veröffentlicht werden die produktiven Maven-Modul-JARs, ihre POMs und die
Scriptdistribution. Nicht veröffentlicht werden:

- `gretl-m0-qualification` (Test-/Qualifikationsmodul),
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

`docs/implementation/publish-settings.xml` enthält nur die Platzhalter
`${env.MAVEN_USERNAME}` und `${env.MAVEN_PASSWORD}`. Maven löst sie beim
Deploy auf; die Settings-Datei enthält keine Secrets. Das Passwort wird in
Maven-Ausgaben maskiert.

## 3. Veröffentlichen über GitHub Actions

Im GitHub-Repository sind die Actions-Secrets `MAVEN_USERNAME` und
`MAVEN_PASSWORD` hinterlegt. Der Workflow
`.github/workflows/publish-snapshots.yml` heisst **publish artefacts** und
läuft bei **jedem Push** sowie manuell über `workflow_dispatch`.

Ablauf:

1. Java 25 wird bereitgestellt.
2. `./mvnw -B -Pgeotools-probe clean verify` führt die vollständige
   Standard- und GeoTools-Testmatrix aus.
3. Erst danach werden die getesteten Artefakte mit
   `-Ppublish-snapshots -DskipTests -DskipITs deploy` veröffentlicht.

Manuell starten:

```sh
gh workflow run publish-snapshots.yml --ref main
gh run list --workflow publish-snapshots.yml
gh run watch <run-id>
```

Alternativ im GitHub-UI unter **Actions → publish artefacts → Run workflow**.

## 4. Veröffentlichen lokal

Zuerst die normale Prüfung:

```sh
./mvnw -o clean verify
```

Dann der Snapshot-Deploy:

```sh
./scripts/publish-snapshots.sh
```

Das Skript prüft die beiden Environmentvariablen und ruft auf:

```sh
./mvnw -s docs/implementation/publish-settings.xml \
  -Ppublish-snapshots clean deploy
```

Maven erzeugt für `0.1.0-SNAPSHOT` timestamped Snapshot-Artefakte und
aktualisiert `maven-metadata.xml`. Der Deploy bricht bei fehlenden
Credentials, HTTP 401/403, Repositoryfehlern oder einem unvollständigen
Upload ab. Snapshots dürfen nach einer Korrektur erneut veröffentlicht
werden; eine automatische Rückabwicklung gibt es nicht.

## 5. Veröffentlichte Koordinaten

Gruppe: `ch.so.agi.gretl`

- `gretl-runtime-parent`
- `gretl-api`
- `gretl-runner`
- `gretl-groovy`
- `gretl-logging`
- `gretl-packager`
- `gretl-basic-tasks`
- `gretl-cli`
- `gretl-job-runtime`
- `gretl-ilivalidator`
- `gretl-dist`

`gretl-dist` wird zusätzlich mit der Scriptdistribution veröffentlicht:

| Classifier | Datei | Typ |
|---|---|---|
| `bin` | `gretl-runtime-0.1.0-SNAPSHOT-bin.zip` | `zip` |
| `bin-tar-gz` | `gretl-runtime-0.1.0-SNAPSHOT-bin.tar.gz` | `tar.gz` |

Beispiel zum Auflösen der ZIP:

```xml
<dependency>
  <groupId>ch.so.agi.gretl</groupId>
  <artifactId>gretl-dist</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <classifier>bin</classifier>
  <type>zip</type>
</dependency>
```

## 6. Prüfung nach dem Deploy

Remote-Metadaten:

```sh
curl -fsS https://jars.interlis.guru/snapshots/ch/so/agi/gretl/gretl-api/0.1.0-SNAPSHOT/maven-metadata.xml
curl -fsS https://jars.interlis.guru/snapshots/ch/so/agi/gretl/gretl-dist/0.1.0-SNAPSHOT/maven-metadata.xml
```

Die Metadaten müssen eine aktuelle `<timestamp>`/`<buildNumber>` und
`<snapshotVersions>` für POM/JAR/ZIP enthalten.

Distribution herunterladen und prüfen:

```sh
curl -fsS -o /tmp/gretl-runtime-0.1.0-SNAPSHOT-bin.zip \
  "https://jars.interlis.guru/snapshots/ch/so/agi/gretl/gretl-dist/0.1.0-SNAPSHOT/gretl-dist-0.1.0-SNAPSHOT-bin.zip"
unzip -q /tmp/gretl-runtime-0.1.0-SNAPSHOT-bin.zip -d /tmp/gretl-snapshot-check
cd /tmp/gretl-snapshot-check/gretl-runtime-0.1.0-SNAPSHOT
shasum -a 256 -c SHA256SUMS
```

Falls der Repository-Proxy die unversionierte Classifier-Datei nicht direkt
auflöst, den timestamped Dateinamen aus `<snapshotVersions>` der
`maven-metadata.xml` verwenden.

## 7. Fehlerfälle

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
