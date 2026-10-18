# 13 — Einstieg für Jobautoren

**Version:** Spezifikationsvorschlag 4.1, 18.10.2026.  
**Zielgruppe:** Fachanwender, die vorhandene Arbeitsbausteine zu einem Job zusammenstellen und bestehende Jobs anpassen.  
**Status:** Die Beispiele beschreiben die zu implementierende Oberfläche. Eine neue GRETL-Light-Distribution wird durch diese Dokumentation noch nicht bereitgestellt. Die spätere Beispieldistribution muss die genannten lokalen Testdateien und Testdienste mitliefern. Die Beispiele verwenden keine produktiven Zugangsdaten.

## 1. Was du für einen normalen Job wissen musst

Ein Job beschreibt eine fachliche Verarbeitung. Er besteht aus benannten Aufgaben. Für jede Aufgabe wählst du einen vorhandenen Baustein, trägst seine Einstellungen ein und sagst gegebenenfalls, welche andere Aufgabe vorher fertig sein muss.

Du musst dafür keine eigene Java-Klasse schreiben, keine Bibliotheken kompilieren und keine Gradle-Konfiguration kennen. Für die üblichen Anpassungen reichen die hier gezeigten Formen. Wer neue fachliche Logik programmieren möchte, kann später die weitergehenden Möglichkeiten aus [Kapitel 04](04-job-dsl-und-custom-tasks.md) verwenden.

Die wichtigste Schreibregel ist:

```groovy
einstellung = wert
```

Text steht in Anführungszeichen. Innerhalb der geschweiften Klammern stehen die Einstellungen einer Aufgabe. Ausdrücke wie `file('sql/update.sql')` oder `parameter('quelle')` holen einen passenden Wert. In einem normalen Taskblock werden diese Werte erst bestimmt, wenn die Aufgabe an der Reihe ist.

## 2. Ein erster vollständiger Job: eine SQL-Datei ausführen

```groovy
task('tabellenAktualisieren', SqlExecutor) {
    database = 'edit'
    scripts = [file('sql/update.sql')]
}

defaults 'tabellenAktualisieren'
```

Dieser Job bedeutet:

- Die Aufgabe heisst `tabellenAktualisieren`. Diesen Namen vergibst du selbst.
- `SqlExecutor` ist der Name eines vorhandenen Bausteins, der SQL-Dateien ausführt.
- `edit` ist der vereinbarte Name einer bereits eingerichteten Datenbankverbindung.
- Die SQL-Datei liegt im Ordner `sql` beim Job.
- `defaults` legt fest, welche Aufgabe beim normalen Start ausgeführt wird.

`scripts` ist eine Liste. Die eckigen Klammern zeigen diese Liste an, auch wenn sie nur eine Datei enthält. Mehrere Dateien werden in der angegebenen Reihenfolge ausgeführt:

```groovy
scripts = [file('sql/vorbereiten.sql'), file('sql/update.sql')]
```

Die Verbindung `edit` wird vom Betrieb beziehungsweise von der Person eingerichtet, die die Jobumgebung bereitstellt. Im normalen Job stehen deshalb weder der Datenbankserver noch ein Passwort. Der Verbindungsname muss zu dieser bereitgestellten Konfiguration passen.

Für das lokale Lernbeispiel kann `sql/update.sql` folgenden Inhalt besitzen:

```sql
CREATE TABLE IF NOT EXISTS gretl_beispiel (
    id integer PRIMARY KEY,
    beschreibung text NOT NULL
);

INSERT INTO gretl_beispiel (id, beschreibung)
VALUES (1, 'Beispiel erfolgreich ausgeführt')
ON CONFLICT (id) DO UPDATE
    SET beschreibung = EXCLUDED.beschreibung;
```

Dieses SQL-Beispiel gehört zur eigens bereitgestellten Testdatenbank. Ein fachlicher Job verwendet stattdessen die für seinen Datensatz vorgesehene SQL-Datei. Der Baustein führt die Dateien dieses Aufrufs als gemeinsame Datenbanktransaktion aus, soweit die verwendeten SQL-Anweisungen dafür geeignet sind. Bei einem Fehler wird der Lauf als fehlgeschlagen gemeldet; nachfolgende Aufgaben starten nicht einfach weiter.

Die spätere Beispieldistribution muss den Startbefehl mit ihrer passenden Testkonfiguration enthalten. Die allgemeine Form ist:

```sh
gretl run job.groovy --config beispiele/runtime.json
```

## 3. Welche Bausteine stehen zur Verfügung?

Die Standarddistribution enthält die für das jeweilige Release zugesagten Bausteine. Für den MVP sind das:

| Baustein | Aufgabe | Lieferstufe |
|---|---|---|
| `HttpDownload` | Eine Datei über HTTP herunterladen | A |
| `Extract` | Ein ZIP- oder GZIP-Archiv entpacken | A |
| `SqlExecutor` | SQL auf einer eingerichteten PostgreSQL-Datenbank ausführen | A |
| `IliValidator` | Eine INTERLIS-Transferdatei prüfen | B |

Diese Liste ist der erste Lieferumfang, keine dauerhafte Obergrenze der Standarddistribution. Weitere gepflegte Bausteine können mit späteren Releases hinzukommen. Organisationseigene Bausteine können zusätzlich bereitgestellt werden und werden im Job auf dieselbe Weise verwendet.

Der Publisher bleibt ein wichtiger späterer Baustein. Seine Umsetzung und Bereitstellung sind ausdrücklich aus dem MVP ausgeklammert. Seine Bestandsanalyse und sein bereits erarbeiteter fachlicher Zielvertrag bleiben vollständig als Grundlage für die spätere Umsetzung erhalten. `XtfToGpkg` ist ebenfalls kein zugesagter MVP-Baustein; seine konkreten Eingaben, Ergebnisse und unterstützten Funktionen werden für einen späteren Liefermeilenstein gesondert festgelegt. Ein Name in einem zukünftigen Beispiel macht den Baustein noch nicht verfügbar.

Die tatsächlich vorhandenen Bausteine zeigt:

```sh
gretl operations
```

Die Hilfe zu einer bestimmten Taskart zeigt ihre Einstellungen, Pflichtangaben, Defaults, Ergebnisse und ein Beispiel:

```sh
gretl operations HttpDownload
```

Für normale Jobpflege brauchst du keine zusätzlichen JAR-Dateien zusammenzusuchen. Neue Bausteine und ihre Abhängigkeiten werden als Teil einer Distribution oder als eingerichtete Erweiterung bereitgestellt. Erst danach kann ihr Name im Job verwendet werden. Die genaue Bereitstellung ist in [Kapitel 06](06-betrieb-und-auslieferung.md) beschrieben.

## 4. Zwei Aufgaben verbinden: herunterladen und entpacken

Der folgende Job lädt ein ZIP-Archiv herunter und entpackt es in ein beim Start angegebenes Zielverzeichnis:

```groovy
parameter('quelle',
    required: true,
    description: 'Adresse der ZIP-Datei')

parameter('ziel',
    type: 'path',
    required: true,
    description: 'Zielverzeichnis für die entpackten Daten')

task('download', HttpDownload) {
    url = parameter('quelle')
    output = workPath('daten.zip')
}

task('entpacken', Extract, dependsOn: 'download') {
    archive = result('download').file
    compression = 'ZIP'
    outputDirectory = parameter('ziel')
}

defaults 'entpacken'
```

Die Aufgabe `download` verwendet `HttpDownload`. Die Aufgabe `entpacken` verwendet `Extract`. Die selbst vergebenen Namen und die Namen der Bausteine haben unterschiedliche Rollen: Ein Baustein kann in einem Job mehrfach für verschieden benannte Aufgaben verwendet werden.

`dependsOn: 'download'` sagt: Bevor `entpacken` starten kann, muss `download` abgeschlossen sein. Durch diese Abhängigkeit gehört der Download auch zum Lauf, wenn `entpacken` das gewählte Ziel ist.

`result('download').file` bezeichnet die vom Download gelieferte Datei. Du brauchst ihren vorübergehenden vollständigen Pfad nicht selbst zusammenzusetzen. Die Datei ist erst nach dem erfolgreichen Download verfügbar; der Entpacktask greift dann auf dieses Ergebnis zu.

`workPath('daten.zip')` stellt einen Platz für diese Zwischendatei bereit. Der Name ist innerhalb des Arbeitsbereichs dieser Aufgabe eindeutig. Für eine dauerhaft benötigte Ausgabe verwenden wir dagegen den beim Start übergebenen Zielort.

Die mitgelieferten Bausteine haben begrenzte Standardwerte:

| Grenze | Default |
|---|---|
| HTTP-Download | 1 GiB |
| Gesamter entpackter Inhalt | 4 GiB |
| Einträge eines ZIP-Archivs | 10’000 |

Diese Werte müssen nicht in jedem Job wiederholt werden. Bei grösseren Fachdaten zeigt die Bausteinhilfe, wie eine Grenze bewusst angepasst wird. Ein überschrittenes Limit ist ein Fehler; GRETL soll keine unvollständige Datei als erfolgreiches Ergebnis weiterreichen.

Die URL im nachfolgenden Aufruf ist ein Beispiel. Für einen ausführbaren Test wird sie durch die Adresse des mitgelieferten lokalen Testservers ersetzt:

```sh
gretl run job.groovy \
  --param quelle=https://example.org/daten.zip \
  --param ziel=/data/ergebnisse
```

## 5. Parameter: Werte beim Start einsetzen

Ein Parameter ist eine benannte Eingabe des Jobs. Die Deklaration am Anfang erklärt, was der Job erwartet:

```groovy
parameter('quelle',
    required: true,
    description: 'Adresse der ZIP-Datei')
```

Innerhalb einer Aufgabe liefert `parameter('quelle')` den für diesen Lauf angegebenen Wert. Damit kann derselbe Job morgen eine andere Datei verarbeiten, ohne dass sein Quelltext geändert werden muss.

Jeder öffentlich verwendete Jobparameter wird deklariert und erhält eine fachliche Beschreibung. Vier Typen stehen zur Verfügung:

| Typ | Bedeutung | Beispielwert beim Start |
|---|---|---|
| `text` | Text; auch der Standard, wenn kein Typ genannt ist | `--param quelle=https://example.org/daten.zip` |
| `boolean` | Genau `true` oder `false` | `--param zusatzpruefung=true` |
| `integer` | Ganze Zahl im unterstützten Zahlenbereich | `--param jahr=2026` |
| `path` | Datei- oder Verzeichnisort | `--param ziel=/data/ergebnisse` |

Bei einem Pflichtparameter bedeutet `required: true`, dass ein Wert vorhanden sein muss. Ein leerer Pflichttext ist ebenfalls ungültig. GRETL meldet fehlende Pflichtwerte vor der ersten fachlichen Verarbeitung.

Ein Parameter kann stattdessen einen Defaultwert haben:

```groovy
parameter('jahr',
    type: 'integer',
    defaultValue: 2026,
    description: 'Jahr der zu verarbeitenden Daten')
```

Ein beim Start übergebener Wert ersetzt diesen Default. Ohne Übergabe wird 2026 verwendet. `required: true` und `defaultValue` werden nicht kombiniert: Entweder ist eine Eingabe erforderlich oder es gibt einen Ersatzwert.

Wenn weder `required: true` noch ein Default angegeben ist, ist der Parameter optional. Fehlt er, ergibt der Zugriff `null`, also „kein Wert“. Ein normaler Pflichtwert eines Tasks sollte deshalb aus einem erforderlichen Parameter oder einem passenden Default stammen. Das besondere Behandeln einer optionalen Eingabe ist ein weitergehender Jobfall.

Bei einem Dateiparameter prüft GRETL zunächst, ob sich der Wert als Pfad verwenden lässt. Ob eine Eingabedatei tatsächlich vorhanden sein muss oder ein Ausgabeverzeichnis neu angelegt werden darf, prüft der jeweilige Baustein.

### Leerzeichen in einem Parameterwert

Bei einem Start in einer Shell muss ein Argument mit Leerzeichen zusammenbleiben:

```sh
gretl run job.groovy \
  --param quelle=https://example.org/daten.zip \
  --param 'ziel=/data/Meine Ergebnisse'
```

Geheimnisse wie Passwörter werden über die dafür eingerichtete Laufzeitkonfiguration bereitgestellt. Sie gehören nicht in die Defaultwerte eines normalen Jobparameters.

### Hilfe zu den Eingaben eines Jobs

```sh
gretl run job.groovy --help
```

Diese Hilfe zeigt die deklarierten Parameter und Standardziele des Jobs. Dafür werden seine Definitionen geladen, aber keine Tasks ausgeführt und keine Pflichtparameter verlangt. Ein normaler Anwenderjob enthält ausserhalb der Taskblöcke nur solche Definitionen. Beliebiger selbst programmierter Groovy-Code an dieser Stelle kann beim Laden trotzdem ausgeführt werden; die Jobhilfe ist keine Sandbox für fremden Code.

Die allgemeine Hilfe lädt keinen Job:

```sh
gretl --help
```

## 6. Drei Arten von Dateien und Verzeichnissen

Die Unterscheidung hilft, denselben Job an verschiedenen Orten und später auch als verpacktes Job-JAR auszuführen.

| Du meinst … | Du schreibst … | Bedeutung |
|---|---|---|
| „Diese Datei gehört zum Job.“ | `file('sql/update.sql')` | Relativ zum Jobverzeichnis; im Job-JAR aus dem mitgelieferten Ressourcenbestand. |
| „Diesen Ort gibt der Anwender beim Start an.“ | `parameter('ziel')` mit `type: 'path'` | Ein externer Ort. Relative Angaben beziehen sich auf das Verzeichnis, aus dem GRETL gestartet wurde. |
| „Diese Zwischendatei brauche ich während der Verarbeitung.“ | `workPath('daten.zip')` | Im vorübergehenden Arbeitsverzeichnis der laufenden Aufgabe. |

Ein Beispiel: `file('models')` meint den Modellordner, der zusammen mit dem Job bereitgestellt wird. Ein Parameter `input=/data/heute.xtf` meint dagegen eine externe Transferdatei des heutigen Laufs. Der Job muss nicht geändert werden, wenn morgen eine andere Transferdatei verwendet wird.

Zwischendateien bleiben für nachfolgende Aufgaben bis zum Ende des Jobs verfügbar. Nach einem erfolgreichen Lauf wird der normale Arbeitsbereich aufgeräumt. Dateien, die dauerhaft bereitstehen sollen, erhalten deshalb einen ausdrücklich vorgesehenen externen Ausgabeort.

## 7. Vorgänger, Ergebnisse und Standardziel

Für gewöhnliche Jobs reicht `dependsOn`. Ein einzelner Vorgänger steht als Name:

```groovy
dependsOn: 'download'
```

Mehrere Vorgänger stehen in einer Liste:

```groovy
dependsOn: ['download', 'tabellenAktualisieren']
```

Diese Angaben gehören in den Kopf eines Tasks. Die genannten Aufgaben müssen im selben Job tatsächlich definiert sein. Ein fachlicher Zusammenhang wird nicht allein dadurch verbindlich, dass zwei Aufgaben im Text nacheinander stehen.

Im Taskblock kannst du ein dokumentiertes Ergebnis eines Vorgängers verwenden. Beispielsweise liefert `HttpDownload` das Ergebnisfeld `file`:

```groovy
archive = result('download').file
```

Welche Ergebnisfelder andere Bausteine liefern, steht in ihrer Hilfe. GRETL erlaubt nicht, ein Ergebnis aus irgendeinem zufällig früher gelaufenen Task zu lesen: Der betreffende Task muss zur deklarierten Vorgängerkette gehören. Wenn ein Vorgänger übersprungen wurde oder fehlgeschlagen ist, wird kein brauchbarer Dateiwert vorgetäuscht.

Das Standardziel wird ausserhalb der Taskblöcke angegeben:

```groovy
defaults 'entpacken'
```

Beim normalen Start führt GRETL dieses Ziel und seine erforderlichen Vorgänger aus. Unabhängige Aufgaben, die zu keinem gewählten Ziel gehören, werden nicht zusätzlich gestartet. Für besondere Starts kann ein Ziel mit `--target` ausgewählt werden. Jobweit als erforderlich deklarierte Parameter müssen bei `run` auch dann angegeben werden.

## 8. Einen vorhandenen Job anpassen

Die häufigsten Änderungen benötigen keine neue Programmierlogik:

| Änderung | Passende Stelle |
|---|---|
| Andere Quelldatei herunterladen | Parameterwert beim Start ändern. |
| Anderen Ausgabeort verwenden | Den Pfadparameter beim Start ändern. |
| Andere eingerichtete Datenbank verwenden | `database = '...'` anpassen. |
| Zusätzliche SQL-Datei ausführen | Den Eintrag in `scripts = [...]` ergänzen. |
| Einen weiteren vorhandenen Baustein verwenden | Aus seinem dokumentierten Beispiel einen benannten Task ergänzen. |
| Eine neue Aufgabe soll nach einer bestehenden laufen | Im neuen Task den passenden Vorgänger mit `dependsOn` nennen. |
| Eine andere Aufgabe soll das normale Endziel sein | `defaults` anpassen. |

Für eine neue Taskart lies zuerst ihre Hilfe und übernimm das vollständige Grundbeispiel. Taskname, Pflichtfelder, erlaubte Werte und Ergebnisnamen sind dabei wichtiger als möglichst wenige Zeilen.

Wenn für die Anpassung plötzlich JSON-Antworten ausgewertet, Tokens beschafft, Exceptions behandelt oder eigene SQL-Generatoren geschrieben werden müssten, ist das eine Programmieraufgabe. Wiederkehrende solche Arbeit kann in einem Custom-Task zusammengefasst werden, den du anschliessend wieder über seine fachlichen Einstellungen verwendest. Die normale Jobpflege muss dafür keine HTTP- oder Java-Bibliothek erlernen.

## 9. Prüfen und den vorgesehenen Ablauf ansehen

Vor einem Lauf können Definition und Plan geprüft werden:

```sh
gretl check job.groovy
gretl plan job.groovy
```

`check` prüft die Jobdefinition innerhalb seiner dokumentierten Grenzen. `plan` zeigt die ausgewählten Aufgaben und ihre Reihenfolge. Beide führen keine Taskblöcke aus. Noch fehlende Pflichtparameter werden dafür nicht verlangt; bereits angegebene Parameterwerte müssen aber zum deklarierten Typ passen.

Eine URL aus einem vorherigen Task, eine später erzeugte Datei oder das Ergebnis einer Datenbankabfrage kann erst während des tatsächlichen Laufs geprüft werden. Ein erfolgreicher Definitionscheck ist deshalb noch kein Nachweis, dass die Quelldaten vorhanden und fachlich gültig sind.

Wie bei der Jobhilfe wird beim Laden normaler Groovy-Toplevelcode ausgeführt. Fachliche Verarbeitung gehört in die Taskblöcke. Die Grundlage dieses Ablaufs ist in [Kapitel 03](03-runtime-vertraege.md) genauer beschrieben.

## 10. Typische Fehlermeldungen verstehen

Die nachfolgenden Meldungen sind Beispiele für die zu implementierende Diagnosequalität. Entscheidend sind ein verständlicher Grund und ein konkreter Hinweis zur Korrektur.

| Situation | Erwartete Hilfe |
|---|---|
| Eine Eingabe fehlt | „Der erforderliche Parameter `quelle` fehlt: Adresse der ZIP-Datei. Übergabe mit `--param quelle=...`.“ |
| Ein Parametername ist falsch geschrieben | „Der Job kennt den Parameter `qulle` nicht. Meinten Sie `quelle`?“ |
| Eine Einstellung ist falsch geschrieben | „Aufgabe `entpacken`, Datei `job.groovy`, Zeile …: `outputDirecotry` ist unbekannt. Meinten Sie `outputDirectory`?“ |
| Eine Taskart ist nicht vorhanden | „Der Baustein … ist in dieser Distribution nicht verfügbar. Verfügbare Bausteine: `gretl operations`.“ |
| Eine Ergebnisabhängigkeit fehlt | „Aufgabe `entpacken` liest ein Ergebnis von `download`, hat diese Aufgabe aber nicht als Vorgänger. Ergänzen Sie `dependsOn: 'download'`.“ |
| Ein boolescher Parameter enthält einen Tippfehler | „Für `zusatzpruefung` wird `true` oder `false` erwartet. Der angegebene Wert ist ungültig.“ |
| Ein Archiv ist grösser als erlaubt | „Die entpackte Datenmenge überschreitet die erlaubte Grenze. Prüfen Sie die Quelldatei und das dokumentierte Limit.“ |

Die normale Meldung soll zuerst die Aufgabe, die betroffene Einstellung und die Ursache nennen. Technische Fehlerdetails bleiben für die Person verfügbar, die einen Bibliotheks- oder Programmfehler untersucht.

Eine fehlgeschlagene Aufgabe kann bereits etwas bewirkt haben. GRETL verspricht keine Rückabwicklung beliebiger Dateisystem-, Datenbank- und Webdienständerungen als Ganzes. Vor einem erneuten Start ist deshalb der Fehlerhinweis des betreffenden Bausteins massgeblich; der Runner wiederholt fachliche Tasks nicht still automatisch.

## 11. Derselbe Job als fertiges JAR

Die optionale Paketierung kann einen bestimmten Stand des Jobs mit seiner Runtime, den benötigten Bausteinen und den mitgelieferten Ressourcen in ein ausführbares JAR zusammenfassen. Für den Betrieb eines solchen Pakets bleibt die Parameterübergabe gleich:

```sh
java -jar daten-job.jar \
  --param quelle=https://example.org/daten.zip \
  --param ziel=/data/ergebnisse
```

Die passende Java-Laufzeit muss auf dem Rechner vorhanden sein. Eine separate GRETL-Installation oder ein Buildwerkzeug wird für diesen Start nicht benötigt. Das JAR enthält einen festgelegten Jobstand; die Eingabewerte dürfen bei jedem Start anders sein.

Die Jobhilfe ist ebenfalls verfügbar:

```sh
java -jar daten-job.jar --help
```

Ein solches Paket erstellen und Bibliotheksversionen auswählen ist eine gesonderte Bereitstellungsaufgabe. Für die alltägliche Pflege eines direkt gestarteten Scripts ist kein Paketbuild nötig. Die Einzelheiten stehen in [Kapitel 12](12-job-paketierung.md).

## 12. Die wenigen Begriffe

| Begriff | Bedeutung |
|---|---|
| Job | Die gesamte beschriebene Verarbeitung. |
| Task oder Aufgabe | Ein benannter Arbeitsschritt innerhalb des Jobs. |
| Baustein oder Taskart | Eine vorhandene Implementierung, beispielsweise `HttpDownload`. |
| Parameter | Ein benannter Eingabewert, der beim Start angegeben werden kann. |
| Vorgänger | Eine Aufgabe, die vor der aktuellen Aufgabe abgeschlossen sein muss. |
| Ergebnis | Ein vom erfolgreichen Vorgänger gelieferter Wert, beispielsweise eine Datei. |

## 13. Was vor Freigabe dieser Anleitung noch nachzuweisen ist

Alle vollständigen Beispiele müssen gegen die tatsächlich gelieferte Distribution ausgeführt werden. Zur Freigabe gehören ausserdem einige kurze Aufgaben mit echten Fachanwendern: eine Quelle ändern, einen Ausgabeort setzen, eine SQL-Datei austauschen, einen Vorgänger ergänzen und einen absichtlich eingebauten Tippfehler anhand der Meldung korrigieren.

Diese Prüfung ist noch durchzuführen. Eine plausibel aussehende DSL allein beweist nicht, dass ihre Sprache und Fehlermeldungen für die Zielgruppe schon gut genug sind. Die konkreten Abnahmeregeln stehen in [Kapitel 09](09-tests-und-abnahme.md), der Umsetzungsauftrag in [Kapitel 10](10-implementierungsplan-fuer-coding-agenten.md).


## Fehler finden, ohne eine Loggingbibliothek zu kennen

Jeder normale Start meldet automatisch, welcher Schritt gerade läuft und ob er erfolgreich endet. Sie müssen dafür keine Logzeilen in jedes Rezept schreiben. Eine typische **beispielhafte** Fehlermeldung sieht so aus:

```text
ERROR [run=… task=datenAktualisieren] SQL-Ausführung fehlgeschlagen
Datei: sql/update.sql; Eintrag: 1; Phase: EXECUTE; Fehler-ID: e-…
Ursache: angegebene Spalte nicht gefunden; SQLSTATE: 42703
Bericht: gretl-reports/<runId>.json
```

Prüfen Sie zuerst den betroffenen Schritt, die genannte Datei und den ersten konkreten Fehler. Ein nachfolgender Abbruch anderer Schritte ist häufig nur die Folge. Die Runtime meldet einen Fehler nicht automatisch als „Datenfehler“; auch SQL oder Einstellungen können falsch sein. Ein fehlender Abschluss kann zudem einen von aussen beendeten Prozess bedeuten.

Für eine Fehleranfrage sind Jobname, Run-ID, Fehler-ID und die bewusst freigegebenen Berichte hilfreich. Kopieren Sie nicht unbesehen die Runtimekonfiguration, private Tokenantworten oder den gesamten Arbeitsordner in ein Ticket. Die Software maskiert bekannte Geheimnisse, kann aber nicht jede frei programmierte Ausgabe automatisch erkennen.

`gretl run job.groovy --log-format json` bietet den Personen im Betrieb die gleichen Ereignisse strukturiert an. Für das Lesen am Bildschirm bleibt das normale Textformat die Vorgabe. `--log-level DEBUG` dient einer gezielten Diagnose; nicht dauerhaft alle Fachbibliotheken auf DEBUG schalten. Ein wiederholter Start ist nicht automatisch harmlos, weil ein früherer Task bereits Daten verändert haben kann.

Fortgeschrittene Actions dürfen mit `log().info('...')` eine kurze fachliche Nachricht ergänzen. Für normalen Fortschritt und Fehlerberichte müssen Anwender weder SLF4J noch OpenTelemetry oder Kubernetes konfigurieren. Die detaillierten Betriebsregeln stehen in [16](16-logging-und-telemetrie.md).
