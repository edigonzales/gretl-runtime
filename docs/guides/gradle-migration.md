# GRETL-Gradle-Jobs in die Runtime übertragen

Beginne mit dem fachlichen Datenfluss. Verwende `dependsOn` für benötigte
Ergebnisse und `after` für die Reihenfolge bereits ausgewählter Tasks.
`finalizedBy` eignet sich für Abschlussarbeiten, die auch nach einem Fehler
ausgeführt werden dürfen. Ein Finalizer mit einer fehlgeschlagenen
Datenabhängigkeit bleibt blockiert. Die Run-Retention verwaltet temporäre Dateien.
[Finalizer und Datenabhängigkeiten](task-finalizers.md)

| Gradle-Muster | Runtime |
| --- | --- |
| SQL-Dateien, Schema- und Wertparameter | `SqlExecutor` mit `sqlFiles`, Alias aus `taskConfig` und `sqlParameters`. Jede Map wird in einer eigenen Transaktion ausgeführt. |
| `Copy`, `zipTree` und `Zip` | `Extract` mit relativen Globs, `Files.copy` oder `Files.move` in einer Action, anschliessend `Zip` mit expliziten Quellen. |
| SSH-Plugin für Dateiübertragungen | `SftpList`, `SftpDownload`, `SftpUpload` und `SftpDelete` mit einem [extern konfigurierten Alias](runtime-configuration.md#sftp-verbindungen). |
| Bekannte Liste für Taskfamilien | `for`, `each` oder `collect` bei der Jobdefinition. Eine flache Liste von TaskRefs kann als `dependsOn` dienen. |
| Erst im Lauf verfügbare Dateiliste | Die Liste in einer Action auflösen, sortieren und per `ctx.run` verarbeiten. Fehler eines Aufrufs beenden die Action. |
| HTTP 200 und 204 | Bei 200 Nutzdaten als Ergebnis weiterreichen. Bei 204 mit `finishJob` ohne Daten beenden. |

Beim AV-Export selektiert das ZIP-Ziel seine Entpack-Tasks über Datenabhängigkeiten.
Bei Hoheitsgrenzen und Grundbuchplan gehört die fachliche Nachbearbeitung in
die Erfolgskette vor den nachgelagerten Transfer. Für Meldewesen selektiert
die Löschung den erfolgreichen Verarbeitungsschritt. Sie verwendet die
Remote-Pfade aus dem Downloadergebnis. Gleichnamige Dateiaustausche während
eines Laufs erfordern eine mit dem Lieferanten abgestimmte Übergabe.

`file()` ist relativ zur Jobquelle, `path()` und Pfadparameter zum Start-CWD.
`workPath()` gehört zum aktuellen Task. Gib Pfade über Ergebnisse weiter oder
verwende ausdrücklich konfigurierte gemeinsame Verzeichnisse. Includes geben
lokale Variablen nicht an andere Scripts weiter. Gemeinsame Datensätze können
über eine Ressourcendatei oder einen expliziten Helper eingelesen werden.

Der statische Editor kann dynamische Taskfamilien als teilweise analysiert
anzeigen. `gretl plan` lädt die Definition und zeigt den erzeugten Plan.
Dateizugriffe und Löschungen gehören in Actions. Eine jobweite Fortsetzung
wie Gradles `continueOnFailure` ist für diese Migration nicht abgebildet.

## Lokal starten

Nach dem [Distributionsbuild](../development/build.md) aus dem Repository-Root:

```sh
output=$(mktemp -d)
job=gretl-integration-tests/src/test/resources/jobs/task-family/job.groovy
gretl-cli/build/install/gretl-runtime/bin/gretl run "$job" --param output="$output" --target write_b
gretl-cli/build/install/gretl-runtime/bin/gretl run "$job" --param output="$output"
```

Die Integrationsfixtures erzeugen ihre Archive, Datenbanken und
Dienstkonfigurationen selbst. `IoLocalJobIT` prüft Archivaufbereitung und
HTTP-Status. `SftpExchangeIT` startet einen lokalen OpenSSH-Server.
`Db2DbPostgresIT` prüft die Geometriekette. Die [Testanleitung](../development/testing.md)
beschreibt den gemeinsamen Reaktorlauf.
