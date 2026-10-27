# 06 — Betrieb, CLI und Auslieferung

**Version:** Spezifikationsvorschlag 4.1, 18. Oktober 2026.  
**Normativ für:** den allgemeinen Script-Runner. Die optionale Jobpaketierung verwendet denselben Laufzeitvertrag; ihr Build ist in [12](12-job-paketierung.md) beschrieben.

## 1. Betriebsmodell

### OPS-001 — Ein Prozess pro Job

Ein CLI-Aufruf startet einen Job in einer frischen JVM. Der Taskgraph läuft sequenziell. Jenkins, ein Containeraufruf oder ein Kubernetes-/OpenShift-Job können diesen Prozess starten und dessen Exitcode auswerten. Ein integrierter Scheduler, Webserver oder Daemon gehört nicht zum MVP.

Tasks laufen zunächst im gemeinsamen qualifizierten Klassenpfad. Ein gesonderter Bibliotheksprozess wird nur als begründete Eigenschaft eines konkreten Adapters eingeführt. Es gibt keine allgemeine Workerinstallation als Voraussetzung für einen normalen Job.

### OPS-002 — Erste Plattform

Die erste verbindliche Freigabe erfolgt auf Linux x86-64 mit Java 25 und dem vorgesehenen Container-/Serverumfeld. Weitere Plattformen benötigen ihre passenden Bibliotheks-, Dateisystem- und Treibertests. Java-Quellcodeportabilität allein beweist keine Portabilität mitgelieferter JNI- oder Datenbankkomponenten.

Die Plattformentscheidung und exakten Ausgangsversionen stehen in Kapitel 07. Ein fertig angebotenes Paket nennt seinen tatsächlich geprüften Umfang.

## 2. Allgemeine CLI

### OPS-010 — Befehle

| Aufruf | Bedeutung |
|---|---|
| `gretl --help` | Allgemeine Hilfe ohne Jobladen. |
| `gretl --version` | Statische Runtimeversion, erforderliche Java-Linie und Paketidentität. |
| `gretl operations` | Tatsächlich verfügbare Tasktypen mit Kurzbeschreibung und Herkunft anzeigen. |
| `gretl operations SqlExecutor` | Eingabefelder, Vorgaben, Ergebnisfelder und ein kopierbares Beispiel für diesen verfügbaren Tasktyp anzeigen. |
| `gretl run job.groovy --help` | Jobdefinition laden und deren deklarierte Parameter, Beschreibungen, Typen, Vorgaben und Ziele erklären; keine Taskarbeit ausführen. |
| `gretl check job.groovy` | Script laden und die statisch verfügbare Definition prüfen. |
| `gretl plan job.groovy` | Ziele und Abhängigkeiten planen; keine Tasks ausführen. |
| `gretl run job.groovy` | Gewählten Job ausführen. |
| `gretl package job-package.json --output job.jar` | Optional installiertes Authoring-Modul aufrufen; erst Lieferstufe C. |

Es gibt keine Publisher-, SQL- oder Formatbefehle. Ein fachlicher Wartungsablauf ist zum Beispiel `gretl run jobs/publisher-maintenance.groovy`; welche Arbeit dieses Script tut, bestimmt seine Taskbibliothek. Die Runtime implementiert dafür weder besondere Optionen noch einen CLI-Pluginmechanismus.

Die **allgemeinen** Aufrufe `gretl --help` und `gretl --version` werden vor Groovy-Laden und Serviceproviderinitialisierung beantwortet. Ein Versionsaufruf öffnet keine Datenbank und lädt keinen GeoTools-Datastore. `operations` darf Providerdefinitionen lesen, führt aber keine Handler aus. Provider dürfen bei Registrierung keine Facharbeit starten. Ein unbekannter Tasktyp wird mit Code 2 und verfügbaren ähnlichen Namen gemeldet. Ein noch nicht geliefertes Symbol erscheint nicht als verfügbarer Platzhalter.

**Jobbezogene Hilfe ist bewusst ein anderer Umfang:** `gretl run job.groovy --help` lädt dieselbe Definition wie `check`, zeigt die darin registrierten Metadaten und endet ohne Taskstart, Parameterwertauflösung, Secrets, Cleanup oder Runbericht. Die Hilfe benötigt keine Pflichtparameter und keine funktionierenden Fachzugänge. Sie führt aber wie jeder Definitionsaufruf vertrauenswürdigen Script-Toplevelcode aus; diese Grenze wird nicht verschwiegen. Fehler in Syntax, Registrierung oder Metadatendefinitionen bleiben Code 2. Dafür gibt es keine zweite statische Parameterschemadatei und keinen AST-Parser, der beliebige Groovy-Logik nachbilden müsste. Globale Hilfe und Versionsausgabe behalten ihren kurzen Startpfad.

`check` und `plan` sind nicht uneingeschränkt nebenwirkungsfrei: Script-Toplevelcode wird geladen beziehungsweise ausgeführt. Die präzise Grenze steht in RUN-012. Sie prüfen Parameterdeklarationen sowie vorhandene Werte; fehlende Pflichtwerte werden als noch offen angezeigt und verhindern diese beiden Definitionsbefehle nicht. Zukünftige Taskresultate werden nicht durch Dummywerte ersetzt. Dynamische Taskkonfigurationen bleiben ungeprüft, bis der jeweilige Task an der Reihe ist.

### OPS-011 — Laufzeitoptionen

Die folgenden Optionen gelten soweit sinnvoll für `run`, `check` und `plan`. Ein Befehl lehnt nicht anwendbare Optionen ab; zum Beispiel setzt `check` keine Jobdeadline in Gang.

| Option | Vertrag |
|---|---|
| `--target <task>` | Wiederholbar für explizite Ziele; sonst Scriptdefaults. |
| `--config <file>` | Eine explizite UTF-8-JSON-Runtimekonfiguration. Keine Suche nach beliebigen Dateien in Elternverzeichnissen. |
| `--params-file <file>` | Eine JSON-Map mit Stringwerten. |
| `--param <name=value>` | Wiederholbare ausdrückliche Parameterüberschreibung. |
| `--work-dir <dir>` | Wurzel für neue private Runarbeitsbereiche. |
| `--report <file>` | Expliziter abschliessender JSON-Runbericht bei `run`; sonst der konfigurierte Standardort. |
| `--timeout <duration>` | Positives ISO-8601-Zeitbudget, kooperativ durchgesetzt. |
| `--log-format <format>` | `text` oder `json`; gleiche Ereignisse, unterschiedliche Konsolendarstellung. |
| `--log-level <level>` | `TRACE`, `DEBUG`, `INFO`, `WARN` oder `ERROR`; Konsolen-/Nachrichtenschwelle gemäss Kapitel 16. |
| `--keep-work <mode>` | `never`, `on-failure` oder `always`; Standard `on-failure`. |

Unbekannte Optionen oder fehlende Werte sind Fehler vor dem Run. Optionale maschinenlesbare Ausgaben für `plan` oder `operations` dürfen über eine explizite Ausgabedatei ergänzt werden. Der MVP verspricht keinen von beliebigem Script-`println` unbeschädigten JSON-stdout-Kanal. Der definierte Runbericht ist eine Datei, keine Mischung aus Fachlogs und Ergebnis-JSON.

Beispiel:

```sh
gretl run jobs/datenbezug/job.groovy --help

gretl plan jobs/datenbezug/job.groovy --target entpacken

gretl run jobs/datenbezug/job.groovy --param quelle=https://daten.example.invalid/daten.zip --param ziel=/data/eingang --report /var/log/gretl/datenbezug-run.json
```

Alle gezeigten neuen CLI-Aufrufe sind Zielverträge; dieses Dokument behauptet keine bereits verfügbare Implementierung. Die Beispieladresse ist durch eine eigene Datenquelle beziehungsweise im Test durch den lokalen Fixture-Server zu ersetzen. Shellzeilen mit Leerzeichen in einem Wert benötigen die übliche Shellquotierung, etwa `--param 'ziel=/data/Geodaten aktuell'`.

### OPS-012 — Parameterregeln

CLI und JSON-Dateien **transportieren** Jobparameter als Strings. Die einmal im Job deklarierte Parameterbeschreibung bestimmt, welchen Werttyp der laufende Job erhält. Die öffentliche DSL dafür steht in [04](04-job-dsl-und-custom-tasks.md). `--param` trennt am ersten Gleichheitszeichen; `a=x=y` bedeutet Name `a`, Wert `x=y`. Ein fehlendes Gleichheitszeichen oder leerer Name ist ein Fehler. `name=` ist ein ausdrücklich leerer String und bedeutet nicht «verwende den Default».

Namen folgen `[A-Za-z][A-Za-z0-9_.-]*`. Doppelte Namen innerhalb derselben Quelle werden abgelehnt, einschliesslich doppelter JSON-Objektschlüssel. Ein höherer Wert darf dagegen einen vorhandenen Wert aus einer niedrigeren Quelle ersetzen.

Vorrang, von niedrig nach hoch:

1. `defaultValue` in der Jobdeklaration, falls vorhanden.
2. `params` in der expliziten Runtimekonfiguration, für im Job deklarierte Namen.
3. Inhalt von `--params-file`.
4. Explizite `--param`-Werte.

Alle öffentlich übergebbaren Jobparameter werden beim Laden deklariert. Ein unbekannter Name in `--param` oder `--params-file` ist Code 2; die Meldung nennt den Namen und einen passenden deklarierten Namen, wenn dieser eindeutig nahe liegt. Ein Job ohne Parameterdeklarationen akzeptiert keine zusätzlichen Jobparameter. Die gemeinsame Runtimekonfiguration darf dagegen Defaults für mehrere Jobs enthalten: Ein dortiger `params`-Eintrag, den dieser Job nicht deklariert, wird ignoriert und ist im Job nicht über einen Hintereingang abrufbar. Die strukturellen String- und Schlüsselnamenregeln gelten auch für solche gemeinsamen Einträge. Diese bewusst unterschiedliche Behandlung verhindert sowohl stille Tippfehler in konkreten Jobaufrufen als auch eine Pflicht zu einer eigenen Runtimekonfigurationsdatei je Job.

Der kleine gemeinsame Konverter liefert:

| Deklarierter Typ | Erlaubter Transportwert und Ergebnis |
|---|---|
| `text` (Standard) | Unveränderter String; ein optionaler Text darf leer sein. Ein erforderlicher Text darf nicht leer sein. Es erfolgt kein stilles Trimmen. |
| `boolean` | Genau `true` oder `false`; Ergebnis `Boolean`. Werte wie `yes`, `1` oder ein Tippfehler werden nicht still als false interpretiert. |
| `integer` | Dezimalzahl gemäss `[+-]?[0-9]+` im Bereich von Java `long`; Ergebnis `Long`. Keine Dezimalbrüche, Exponenten oder Überläufe. |
| `path` | Nichtleerer Dateisystempfad; Ergebnis ein normalisierter `Path`, relative Angaben bezogen auf das ursprüngliche Start-CWD. Kein Zugriff auf Dateiinhalt und keine Vorwegnahme der taskseitigen Existenzprüfung. |

`defaultValue` ist ein beim Registrieren bereits bekannter einfacher String oder ein zum Typ passender Boolean-/Ganzzahlwert. Für `path` ist der Default eine Pfadzeichenfolge mit derselben Start-CWD-Regel. Das Ausführen von transportiertem Text als Ausdruck, Closures als Default, eigene Konverter und eine allgemeine Schemasprache gehören nicht zum MVP. `required: true` und `defaultValue` gleichzeitig sind ein Definitionsfehler; ein vorhandener Default macht die ausdrückliche Übergabe entbehrlich. Ohne Default und ohne Pflicht liefert der deklarierte optionale Parameter bei Abwesenheit `null`. Der Zugriff auf einen nicht deklarierten Namen bleibt ein Fehler und liefert nicht einfach null.

Die Deklarationen einschliesslich Typ und Default werden bei jedem Definitionsaufruf geprüft. Nach Zielplanung, aber **vor Beginn des Runs**, werden die tatsächlichen Werte einmal aufgelöst und typisiert. Bei `run` müssen alle jobweit erforderlichen Werte vorhanden sein, auch bei Wahl eines einzelnen Teilziels. Fehlende Pflichtwerte, ungültige vorhandene Werte und unbekannte ausdrücklich übergebene Namen ergeben Code 2, ohne einen Download oder eine Datenbankoperation zu starten. `check` und `plan` prüfen vorhandene Werte ebenfalls, lassen fehlende Pflichtwerte jedoch als offen stehen. Sie benötigen keine externen Daten, um einen Pfadparameter zu akzeptieren. Hilfe zeigt nur Deklarationen und Defaults; sie löst keine konkreten Parameter- oder Secretwerte auf.

Die typisierte Sicht ist pro Run unveränderlich. `parameter('name')` im Taskblock beziehungsweise `ctx.parameter('name')` im fortgeschrittenen Code greift darauf zu. Laufzeitwerte aus HTTP, Dateilisten oder Vorgängertasks werden weiterhin als lokale Werte beziehungsweise Taskresultate weitergereicht. Parameterdeklaration bedeutet nicht, künftige Taskausgaben vorab zu berechnen oder die globale Parametermap zu mutieren.

Fachliche Regeln bleiben beim Task: Ob eine URL zulässig, eine XTF gültig oder ein Ausgabeverzeichnis beschreibbar ist, entscheidet dessen dokumentierter Vertrag. Ein erst in einer späten Konfigurationsclosure auftretender falscher Parameterzugriff oder ungültiger dynamischer Fachwert ist weiterhin Code 10 in Phase EXECUTION. Die frühe Parameterprüfung darf nicht als Vorabprüfung beliebigen Groovy-Codes ausgegeben werden.

Es gibt keine implizite `${...}`-Auswertung in JSON und keine automatische Ausführung eines Werts als Groovy-Ausdruck. Umgebungsvariablen sind explizite Secretquellen oder werden im vertrauenswürdigen fortgeschrittenen Code bewusst gelesen. Öffentliche Parameter und ihre sichtbaren Defaults sind nicht für Passwörter oder Tokens gedacht; dafür gilt OPS-022.

### OPS-013 — Start ohne Konfigurationsdatei

`gretl run job.groovy` ist ohne `--config` zulässig. Dann gelten folgende Defaults: keine Runtime-Parameterüberschreibungen, leere Secret- und Taskkonfigurationsmaps; kein Jobtimeout; `keepWork=on-failure`; `workRoot` als `gretl-work` unter Javas `java.io.tmpdir`; `reportRoot` als `gretl-reports` unter dem beim Start festgehaltenen CWD. Jeder Lauf erhält einen frischen privaten `<runId>`-Arbeitsbereich. Der Standardbericht heisst `<runId>.json`, die Ereignisdatei `<runId>.events.jsonl` im Reportroot. Standardlogging ist menschenlesbares Textformat auf stderr mit INFO; die zusätzliche Ereignisdatei ist JSON Lines. `logging.eventFile=false` deaktiviert nur diese Datei, nicht den erforderlichen Abschlussbericht. Begrenzungen, Dateirechte und Ausfallverhalten stehen in Kapitel 16.

Ist `--report` gesetzt, ersetzt dessen Pfad nur das Ziel des Abschlussberichts; die Ereignisdatei bleibt im Reportroot. Pflichtverzeichnisse und deren Schreibbarkeit werden vor Taskbeginn geprüft. Ein Fehler dabei ist Code 2 und startet keine Facharbeit. `--help`, `--version`, `check` und `plan` erzeugen keine Runarbeitsbereiche oder Runberichte. Dieselben Defaults gelten beim Start eines Job-JARs; sie beziehen sich nicht auf den Ablageordner des JARs.

## 3. Runtimekonfiguration

### OPS-020 — Allgemeines Schema

```json
{
  "schemaVersion": 1,
  "runtime": {
    "workRoot": "/var/tmp/gretl",
    "reportRoot": "/var/log/gretl",
    "keepWork": "on-failure",
    "timeout": "PT1H",
    "jobName": "datenbezug"
  },
  "logging": {
    "format": "text",
    "level": "INFO",
    "eventFile": true
  },
  "params": {
    "environment": "test"
  },
  "secrets": {
    "dbPassword": {"environment": "GRETL_DB_PASSWORD"},
    "sourceAuth": {"file": "/run/secrets/source-auth"}
  },
  "taskConfig": {
    "processingDb": {
      "jdbcUrl": "jdbc:postgresql://localhost/gretl_test",
      "user": "gretl_test",
      "passwordSecret": "dbPassword"
    }
  }
}
```

Der Core kennt `runtime`, `logging`, die transportierten Stringwerte und deklarierten Parametertypen, Secretquellen und benannte allgemeine Konfigurationsabschnitte. Er kennt keine Datenbank-, Publikationsstore- oder Metadatensenken-Schemata. Die Bedeutung des Beispiels `processingDb` gehört SqlExecutor. Dessen Einstellung `database = 'processingDb'` kann den Abschnitt über den Operationskontext lesen und selbst validieren.

`ctx.config(alias)` liefert die unveränderliche JSON-Struktur aus `taskConfig`. Unterstützte Werte sind JSON-Scalars, Maps und Listen. Ein fehlender Alias ist eine klare Diagnose. Felder in einem taskseitigen Abschnitt werden vom zugehörigen Task validiert; der Core kann deren Fachschema nicht erraten. Unbekannte Felder in den vom Core selbst definierten Ebenen sind Fehler.

Die oberste Whitelist ist `schemaVersion`, `runtime`, `logging`, `params`, `secrets`, `taskConfig`. Unter `runtime` sind `workRoot`, `reportRoot`, `keepWork`, `timeout` sowie die optionalen `jobName` und `correlationId` erlaubt. Der vollständige `logging`-Feld-/Defaultvertrag steht in LOG-030; unbekannte Corefelder bleiben Fehler. `GRETL_JOB_NAME` und `GRETL_CORRELATION_ID` sind genau beschriebene optionale Startadapterwerte; Runtimekonfiguration hat Vorrang. Sie eröffnen keine neue allgemeine Jobparameterquelle.

Die Runtimekonfiguration wird vor Taskbeginn eingelesen und strukturell geprüft. Secrets werden erst bei Verwendung aufgelöst. Dass ein optionaler, nicht ausgeführter Task eine Secretreferenz enthält, erzwingt noch keinen Zugriff auf die Secretquelle.

### OPS-021 — Dateipfade und Bases

| Pfad | Basis |
|---|---|
| Jobdatei und allgemeine CLI-Dateioptionen | Ursprüngliches Start-CWD |
| Pfadfelder unter `runtime` und eine Secretdatei | Ordner der Runtimekonfigurationsdatei |
| `file(relative)` im Taskblock | Jobroot: Scriptordner beziehungsweise materialisierte Paketressourcen |
| `ctx.path(relative)` im fortgeschrittenen Code | Ursprüngliches Start-CWD, beim Start einmal festgehalten |
| `workPath(relative)` im Taskblock | Arbeitsbereich des aktuellen Tasks oder Aufrufs |
| `parameter(name)` bei deklariertem Typ `path` | Ursprüngliches Start-CWD, unabhängig von Parameterdatei, Jobdatei oder JAR-Ablageort |
| Parameter vom Typ `text` | Unveränderter Text; keine automatische Pfadinterpretation |
| Taskseitige JSON-Felder | Vertrag des Fachadapters; keine automatische Umdeutung aller Strings |

Absolut angegebene Pfade bleiben absolut. `file` und der fortgeschrittene `ctx.path` sind keine allgemeine Sandbox. Die gleichnamigen Kontextzugriffe folgen denselben Regeln wie die Helfer im Taskblock. Der Jobroot ist für mitgelieferte Beispiele ein Lesebestand; erzeugte Zwischenstände gehören in den Workspace.

`workPath` akzeptiert nur relative Pfade innerhalb seines eigenen Bereichs, verhindert Traversal und unzulässige Symlinks in seinem erzeugten Pfad und erstellt benötigte Elternverzeichnisse. Die Runtime darf dadurch weder ausserhalb des Arbeitsbereichs schreiben noch eine fertige Ausgabedatei vortäuschen.

### OPS-022 — Geheimnisse

Eine Secretquelle ist genau eine der drei Formen `{"environment": "GRETL_DB_PASSWORD"}`, `{"file": "./secrets/db-password.txt"}` oder `{"plaintext": "testpasswort"}`. `plaintext` ergänzt `schemaVersion: 1` additiv gemäss ADR-4.1-PLAINTEXT in [11](11-quellen-entscheidungen-und-roadmap.md). Pro Alias ist genau eines dieser Felder zulässig; fehlende Quellen, Kombinationen, unbekannte Felder, `null` und andere Werttypen als Strings sind Konfigurationsfehler. Es gibt keinen automatischen Quellenfallback. Fehlende Environmentvariablen oder Dateien werden erst beim tatsächlichen Zugriff mit Alias und Quelle, ohne Wert, gemeldet.

Der erste Dateivertrag ist UTF-8, höchstens 64 KiB, keine NUL-Zeichen; ein abschliessendes LF beziehungsweise CRLF wird entfernt, sonst bleiben Bytes beziehungsweise Zeichen erhalten. Relative Secretdateipfade beziehen sich auf den Configordner gemäss OPS-021. Environment- und `plaintext`-Werte werden unverändert übernommen, einschliesslich leerer Strings, Leerzeichen und Zeilenumbrüche; es gibt keine Variablenersetzung. Weitere Secretprovider sind spätere austauschbare Implementierungen.

Bei `plaintext` steht der Wert direkt lesbar in der Runtimekonfigurationsdatei. Die Laufzeitmaskierung verschlüsselt diese Datei nicht. Diese Datei bleibt eine externe Laufzeiteingabe und wird nicht in Jobcode oder Paketmetadaten übernommen. Die Stringdarstellung einer Secretquelle muss Klartextwerte auch innerhalb einer Map maskieren.

`ctx.secret(alias)` liefert unabhängig von der Quellenart ein maskiertes SecretValue mit ausdrücklich kontrollierter Übergabe an den Fachadapter. Der reale Wert wird erst beim erforderlichen Aufruf entnommen. Auch ein bereits eingelesener `plaintext`-Wert wird erst bei diesem Zugriff im rungebundenen Maskierer registriert; die Grenzen aus LOG-060 gelten für alle drei Quellen. `toString()` liefert eine Maskierung. Unkontrollierte Stringkonkatenation mit einem SecretValue ist daher keine geeignete Headerkonstruktion; Adapter können benannte Secretwerte direkt akzeptieren.

Die vollständigen SecretValue-/Context-Verträge stehen in API-030/API-050 und Kapitel 16. Ein über `ctx.secret` entnommener Wert wird vor seiner Übergabe an die Fachbibliothek im rungebundenen Maskierer registriert. Ein zur Laufzeit erhaltenes Token kann mit `ctx.sensitive(token)` ebenso registriert werden. Das ist kein DLP-Schutz gegen bewusst kodierten oder direkt per System.out ausgegebenen Geheimnisinhalt.

Passwörter und Tokens werden nicht automatisch in Logs, Berichte, CLI-Argumente oder Paketmetadaten geschrieben. Ein dynamisch abgefragter Token darf als Resultat im Prozess bestehen; die Ergebnisablage serialisiert ihn nicht. Fachbibliotheken dürfen ihr eigenes Logging nicht ungeprüft auf DEBUG mit allen Requestfeldern betreiben.

## 4. Distribution und Custom-Bibliotheken

### OPS-029 — Ein mit den Releases wachsender Taskkatalog

Die **Standarddistribution** ist das startfertige GRETL-Paket dieses Releases: Runtime, Groovy und ein gemeinsam geprüfter Bestand von Taskbibliotheken. «Stellt ein Symbol bereit» bedeutet für den Jobautor beispielsweise, dass `SqlExecutor` unmittelbar als Tasktyp verwendbar ist. Er muss keine alte Gradleklasse importieren, keinen Provider registrieren und kein Mavenartefakt im Job angeben. Das Symbol bezeichnet den dokumentierten Aufgabenbaustein; die registrierende Bibliothek verwendet dieselbe öffentliche SPI wie jede eigene Bibliothek.

Die Liste des ersten Releases ist **keine geschlossene Liste zulässiger Standardtasks**. Neue allgemeine GRETL-Tasks dürfen nach Implementierung, Dokumentation und Qualifikation ganz normal in spätere Standarddistributionen aufgenommen werden. Eure Organisation kann zusätzlich ein gemeinsames GRETL-Paket beziehungsweise Containerimage mit eigenen Fachbibliotheken bereitstellen. Auch darin finden Anwender die Tasktypen unmittelbar vor. Es gibt keine Vorgabe, dass alle künftigen Tasks ausserhalb der Standarddistribution leben müssen.

| Bestandteil | Zugehörigkeit im Lieferplan 3.0 |
|---|---|
| `HttpDownload`, `Extract`, `SqlExecutor` | Umsetzung und Mitlieferung in A. |
| `IliValidator` | Umsetzung und Mitlieferung in B; A und B ergeben den Script-MVP. |
| GeoTools-Probe | Kleine verbindliche Kompatibilitätsprüfung im gesonderten Entwicklungs-/CI-Profil in M0/B; kein öffentliches Tasksymbol und kein Grund, unbenutzte JARs mitzuliefern. |
| `Publisher` | Nach MVP umsetzen, prüfen und regulär bereitstellen; Analyse und Zielvertrag sind bereits Teil der Spezifikation. |
| `XtfToGpkg` | Weitere eigenständige Taskart nach MVP; die frühere blosse Nennung ersetzt keinen zugesagten Request-/Resultatvertrag. |
| Weitere allgemeine oder eigene Tasks | Nach Qualifikation Bestandteil einer künftigen Standard- oder Organisationsdistribution oder ausdrücklich installierte Erweiterung. |

Ein Freigabekatalog nennt je angebotenem Task die Version, Unterstützung und Herkunft. Zukunftsideen stehen in der Roadmap, nicht als aufrufbare Provider im Katalog. Die MVP-Distribution enthält weder einen Publisherstub noch gezielt für seine noch nicht gelieferte Umsetzung hinzugefügte Dependencies. Eine Bibliothek, die unabhängig davon tatsächlich für einen gelieferten Task benötigt wird, bleibt durch diesen eigenen Bedarf begründet. Die vorbereitende GeoTools-Probe verwendet einen getrennt ausgewiesenen Testbestand. Ihre zusätzlichen JARs gehören nicht allein wegen dieser Probe in die MVP-Distribution; bei einer später angebotenen GeoTools-Fachfähigkeit wird deren tatsächlicher Paketbestand separat qualifiziert.

### OPS-030 — Einfacher gemeinsamer Klassenpfad

Eine praktische Scriptdistribution enthält:

- Launcher `bin/gretl`;
- Runtime-/CLI-/Groovy-JARs unter `lib/runtime`;
- freigegebene Task-JARs und deren Dependencies unter `lib/tasks`;
- einen ausdrücklich dokumentierten Erweiterungsbereich `lib/extensions`;
- Versions-/Artefaktinventar, Beispiele und Konfigurationsbeschreibung.

Der Launcher startet die JVM mit diesem bekannten Bestand in einem gemeinsamen Anwendungsklassenpfad. Ein Java-Classpath-Wildcard expandiert keine beliebigen Unterverzeichnisse; die tatsächlich verwendeten Verzeichnisse sind ausdrücklich Teil des Startvertrags. Ein neuer Extensionbestand steht vor dem nächsten Start bereit.

Die Person, die die Distribution erstellt oder betreibt, nimmt für einen eigenen Task dessen JAR und die nötigen kompatiblen Dependencies in den dokumentierten Bereich `lib/extensions` auf. Mit dem Release ausgelieferte Bibliotheken stehen unter `lib/tasks`. Ein lokales Extension-JAR wird dadurch nicht automatisch Teil des nächsten öffentlichen GRETL-Releases; die Aufnahme in einen gemeinsamen Releasebestand ist eine bewusste Paketierungsentscheidung. Diese Erweiterung braucht keinen Runnerquellcodewechsel. Der Erweiterungstest benutzt eine isolierte Testdistribution. Hot-reload und zusätzliche JAR-Auswahl aus einem laufenden Job sind nicht vorgesehen.

Der Build vermeidet unbeabsichtigte doppelte Klassen und unpassende Bibliotheksversionen. Registrykonflikte werden zusätzlich beim Start geprüft. Ein separates Dateiverzeichnis ist keine Klassenisolation; konkurrierende Versionen derselben Java-Klasse benötigen eine echte Lösung und keinen blossen anderen Ordnernamen.

### OPS-031 — Inventar und Produktbuild

Die Distribution enthält Runtimeversion, Quellcommit, Java-Anforderung, Groovy-Module, Taskartefakte, Dependencyversionen und Prüfsummen. Native Libraries und externe Programme stehen mit ihren Plattformgrenzen ebenfalls im Inventar.

Maven Wrapper mit der in Kapitel 07 ausgewählten Version baut und prüft das Produkt. Die bestätigte Buildleitlinie aus [07, Abschnitt 6.4](07-plattform-und-versionen.md#64-verbindliche-leitlinie-konventioneller-maven-produktbuild) verlangt einen konventionellen Reactorbuild ohne zusätzliche interne Buildplattform. Standardprüfung und ausdrücklich nötige Zusatzgates werden in der Repository-Buildanleitung dokumentiert und identisch von CI verwendet. Ein freigegebener Jobstart lädt keine Codeabhängigkeiten aus Repositories nach. Fachlich benötigte Daten- oder Modellrepositoryzugriffe bleiben davon getrennt.

Optional installiert die Authoringdistribution zusätzlich den Packager. Ein normaler Runtimeaufruf lädt dessen Maven-Infrastruktur nicht. Das fertige Job-Fat-JAR enthält wiederum keine Buildwerkzeuge. Die exakte Paketierung, Services und Ressourcen stehen in Kapitel 12.

## 5. Arbeitsbereiche, Logs und Berichte

### OPS-040 — Workspace-Lifecycle

Jeder Run erhält eine eindeutige Run-ID und einen neu angelegten privaten Unterordner. Unter Linux ist er nur für den ausführenden Benutzer zugänglich. Tasks und synchrone Invocations erhalten eigene Unterbereiche.

Dateiresultate bleiben bis zum Runende erhalten. Bei Erfolg werden Arbeitsdateien gemäss `keepWork` entfernt; bei Fehler oder kontrolliertem Abbruch bleiben sie standardmässig für Diagnose bestehen. Eine explizite Aufbewahrungsentscheidung darf keine dauerhaften fachlichen Ausgaben löschen.

Die automatische Workspacebereinigung traversiert ausschliesslich den eigenen Runbereich. Externe Ausgabeziele, Datenbankbestände und taskeigene Publikationshistorien liegen ausserhalb dieses Besitzvertrags. Der Runner löscht keinen Fachbestand aufgrund allgemeiner Logretention.

Tokenantworten können sensible Arbeitsdateien sein. `on-failure` erfordert deshalb begrenzten Zugriff und eine organisierte spätere Aufbewahrungsregel. Der Runbericht verlinkt nicht automatisch jede Workspace-Datei und lädt keine Inhalte irgendwohin hoch.

### OPS-041 — Berichtsformat

Die Runtime schreibt fortlaufende strukturierte Ereignisse und am kontrollierten Ende einen JSON-Runbericht. Das vorgeschlagene Schema trägt `schemaVersion: 1` und enthält:

- Run-ID, Runtime-/Paketversion, Start/Ende, Runstatus und Exitcode;
- Scriptidentität beziehungsweise eingebettete Jobidentität;
- angeforderte Ziele und den ausgewählten Plan;
- Taskstatus, Zeiten, Stop-/Skipgründe und Invocations;
- allgemeine Fehlerketten und ausdrücklich gemeldete Diagnosen;
- Pfade auf ausdrücklich freigegebene Diagnoseartefakte.

Parameterwerte, Secrets und beliebige Resultatobjekte fehlen standardmässig. Namen und Herkunft von Parametern dürfen für Diagnose erfasst werden. Scriptfehler sollen Quelle und Zeile behalten, auch im gepackten Job.

Ein abschliessender Bericht wird über eine temporäre Datei ersetzt, damit gewöhnliche Leser kein halb geschriebenes JSON erhalten. Dies ist keine zugesicherte Powerfail-Dauerhaftigkeit. Scheitert erforderliches Schreiben des Berichts nach erfolgreicher Facharbeit, wird der Lauf mit einem allgemeinen Fehler gemeldet; bereits erfolgte Facharbeit wird dadurch nicht rückgängig.

### OPS-042 — Verbindlicher Logging- und Telemetrievertrag

[Kapitel 16](16-logging-und-telemetrie.md) definiert das vollständige MVP-Verhalten: Text- und JSON-Konsolendarstellung auf stderr, begrenzte JSONL-Ereignisdatei, automatische Run-/Task-/Invocation-Zuordnung, Ursachen-/Suppressed-Ketten mit Fehler-IDs, Secretfilter vor allen verantworteten Senken, Bibliotheksbrücken, Fortschritt, Heartbeats und Provenienz. Ein erforderlicher Abschlussbericht ist von nicht garantierter Telemetriezustellung getrennt.

Fachdiagnosen dürfen ihren eigenen Zustandsbegriff enthalten. Der Core serialisiert die ausdrücklich freigegebenen Details ohne Fachinterpretation und bestimmt keinen Erfolg aus Logseverity. Loggingpfade dürfen keine Konfigurationsclosure vorzeitig ausführen. Direkte Ausgaben fremder Bibliotheken werden weder als Runstatus noch als JSON-Runbericht interpretiert.

### OPS-043 — Berichtserweiterung und Abschlusspfad

Der Schema-1-Runbericht aus OPS-041 erhält die in LOG-080 beschriebenen Pflichtbereiche `logging` und `telemetry`, Quell-/Paketnachweise sowie eine begrenzte Fehler-/Invocationübersicht. Das Schema ist noch unveröffentlicht; die Erweiterung erzeugt keinen behaupteten bestehenden Kompatibilitätsbruch. `telemetry.mode=none` bedeutet keine aktive OTel-Instrumentierung, nicht fehlende lokale Logs.

Konsolenschwelle und Ereignisdatei besitzen die dokumentierte Filterpolitik. Nach Erreichen der Dateigrenze bleiben Konsole, begrenzter Terminalbereich und Bericht verfügbar; Kürzungen sind sichtbar. Der Runbericht speichert weder alle freien Lognachrichten noch sämtliche Nutzdaten. REPORT- und LOG-Grenzen sind eigene Dokumentationsverträge, keine generischen Fachschemas.

### OPS-044 — Kubernetes und Jenkins

Das Kubernetes-/OpenShift-Betriebsbild, Downward-API-Metadaten, SIGTERM-/SIGKILL-Grenzen und die optionale kleine Termination Message stehen in TEL-010. Das Produkt benötigt dafür keine Kubernetes-API-Berechtigung und installiert keinen Collector. Der Containerstarter muss Java als korrekt signalisierten Hauptprozess ausführen; eine Shell ohne exec ist kein geeigneter Default.

Jenkins-Buildkonsolen und Controllercontainerlogs sind verschiedene Erfassungsquellen. Die Betriebsabnahme prüft den tatsächlichen GRETL-Buildlogpfad. Archiviert werden nur bewusst ausgewählte Reports; private Token-/Arbeitsdateien sind ausgeschlossen. Der Runbericht ist kein Ersatz für externe Langzeitretention. OTel-Export und weitere Backendintegration bleiben optional und müssen eigene geprüfte Betriebsprofile erhalten.

## 6. Fehlerbetrieb

### OPS-050 — Allgemeine Reaktion

| Ausgang | Allgemeine Reaktion des Aufrufers |
|---|---|
| Code 0 | Erfolgreicher Abschluss beziehungsweise ausdrücklich NO_DATA. |
| Code 2 | Startkonfiguration oder Jobdefinition korrigieren. |
| Code 10 | Fehlerbericht und Diagnose des betroffenen Tasks auswerten. |
| Code 20 | Kontrollierten Abbruch prüfen; ein Restart braucht weiterhin den Fachvertrag. |
| Code 40 | Runnerdefekt untersuchen; vorhandene Fachdiagnosen behalten. |
| Prozess ohne kontrollierten Abschluss | Unvollständiger Nachweis; kein behaupteter fachlicher Zustand. |

Ein Nichtnull-Exit ist keine allgemeine Zusage, dass der komplette Job gefahrlos wiederholt werden kann. Ein SQL-, HTTP- oder Publisher-Task kann bereits externe Wirkungen erzeugt haben. Automatischer Scheduler-Retry wird nur eingerichtet, wenn der konkrete Job beziehungsweise seine Operationen dafür einen tragfähigen Fachvertrag haben.

Allfällige Idempotenzschlüssel, Wiederanlaufdateien oder Reparaturtasks gehören in die zuständige Bibliothek beziehungsweise den Job. Die Runtime bietet dafür keine speziellen CLI-Zustände.

### OPS-051 — Timeout und harter Abbruch

`--timeout` ist ein kooperatives Budget. Die mitgelieferten Adapter implementieren dazu ihre Bibliothekstimeouts. Ein Scheduler oder Container kann zusätzlich eine harte Prozessfrist setzen. Die nachfolgende Beendigung einer blockierten JVM kann Cleanup und Bericht verhindern.

Der Runner behauptet deshalb keinen erfolgreichen Ressourcenabschluss nach SIGKILL und kein Zurückrollen externer Transaktionen. Ein Native-Launcher vor dieser JVM ändert diese Grenze nicht. Wenn ein einzelner Adapter eine stärkere kontrollierte Prozessgrenze benötigt, wird diese gesondert umgesetzt und geprüft.

## 7. Freigabekriterien

Die erste Distribution muss ohne Gradle/Maven zur Laufzeit starten, eigene Taskbibliotheken ausführen, die gewählten Versionspfade real prüfen und verständliche Fehler liefern. Für den Script-MVP kommen die Fachintegrationen aus Lieferstufe B hinzu. Für C wird derselbe Vertrag aus dem tatsächlich erzeugten Job-JAR geprüft.

Eine Freigabe nennt bewusst den unterstützten Taskumfang und die bekannte Abbruchgrenze. Der Script-MVP liefert HttpDownload, Extract, SqlExecutor und IliValidator. Publisher-Implementierung, Publisher-Artefakte und Publisher-E2E-Nachweise sind ein späterer Liefermeilenstein. Die allgemeinen aus seiner Analyse abgeleiteten Verträge werden bereits im MVP an den tatsächlich gelieferten Tasks geprüft. Ein Publisher-Wartungsdienst, ein universelles Workerprotokoll oder ein Native Image ist keine MVP-Voraussetzung.
