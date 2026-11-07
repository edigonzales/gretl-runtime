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
läuft bei Push, Pull Request und manuell über `workflow_dispatch`.

Ablauf:

1. Der Verify-Job führt die konsolidierte Prüfung mit JDK 25, Node und
   Docker aus. Die qualifizierte ZIP- und TAR-Distribution werden zusammen
   mit ihren SHA-256-Prüfsummen als ein Artefakt gesichert.
2. Der Publikationsjob beginnt erst nach erfolgreichem Verify auf `main`
   und prüft die übergebenen Archivbytes. Pull Requests veröffentlichen nie.
3. Nur auf `main` bleibt der vorhandene Maven-Deployweg
   `-Ppublish-snapshots -DskipTests -DskipITs deploy` aktiv. Diese Flags betreffen
   ausschliesslich den Deploy nach den Pflichtgates, nicht die Qualifikation.
   Fehlende Credentials werden vor diesem Deploy ausdrücklich gemeldet.
4. Nach dem Deploy lädt `scripts/verify-published-runtime.py` die tatsächlich
   veröffentlichte ZIP anhand des timestamped Eintrags in `maven-metadata.xml`.
   Der Check prüft sämtliche Checksummen, Runtimeversion und sauberen
   Quellencommit. Mit genau diesem Launcher wird anschliessend ein neuer
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

## 4. Veröffentlichen lokal

Ein gesondert autorisierter lokaler Publishaufruf erfolgt über:

```sh
./scripts/publish-snapshots.sh
```

Das Skript prüft zuerst beide Credentialvariablen, führt dann den Dockerfreien
Standard und ALL/full ohne übernommene Skipargumente aus und bewahrt deren
Evidenz unter `.gretl-verification/`. `GRETL_PUBLICATION_EVIDENCE` kann einen
anderen Zielpfad wählen. Erst nach erfolgreichen Gates folgt der bisherige
Deploy mit `publish-settings.xml`, Profil `publish-snapshots` und den
Deploy-Flags `-DskipTests -DskipITs`. Ein Fehler in einem Pflichtgate beendet
das Skript vor jedem Deploy. Zusätzliche Argumente gelten nur für den Deploy.

Push, Merge, Release und Deploy benötigen einen ausdrücklichen Auftrag.
Die Kontrolltests verwenden lokale Ersatzprogramme und synthetische Credentials.

Maven erzeugt für `6.0.0-SNAPSHOT` timestamped Snapshot-Artefakte und
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

## 6. Prüfung nach dem Deploy

Remote-Metadaten:

```sh
curl -fsS https://jars.interlis.guru/snapshots/ch/so/agi/gretl/gretl-api/6.0.0-SNAPSHOT/maven-metadata.xml
curl -fsS https://jars.interlis.guru/snapshots/ch/so/agi/gretl/gretl-dist/6.0.0-SNAPSHOT/maven-metadata.xml
```

Die Metadaten müssen eine aktuelle `<timestamp>`/`<buildNumber>` und
`<snapshotVersions>` für POM/JAR/ZIP enthalten.

Distribution herunterladen, prüfen und mit einem externen Job starten
(benötigt Python 3 und JDK 25; `SOURCE_SHA` ist der volle veröffentlichte
Git-Commit, `OUTPUT` ein neues Verzeichnis):

```sh
SOURCE_SHA=$(git rev-parse HEAD)
OUTPUT=/tmp/gretl-snapshot-check
python3 scripts/verify-published-runtime.py \
  --repository https://jars.interlis.guru/snapshots \
  --version 6.0.0-SNAPSHOT --source "$SOURCE_SHA" --output "$OUTPUT"
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
