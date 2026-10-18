# 16 — Logging, Fehlerdiagnose und Telemetrie

**Version:** 4.1, 18. Oktober 2026.  
**Normativ für:** MVP-Logging, strukturierte Ereignisse, Kontext, Fehlerdarstellung, Secretfilter, Runbericht und die Prüfungen LOG/TC-L.  
**Strategisch, nicht MVP-Pflicht:** eigener OpenTelemetry-Export, Traces/Metriken, Collectorinstallation und Backenddashboards.  
**Leitlinie:** gute Diagnosen gehören zum Produkt, eine bestimmte Observability-Plattform nicht. Logging verändert weder Taskstatus noch fachliche Wiederanlaufregeln.

## LOG-001 — Wozu das Log beantworten können muss

Ein Betreiber soll ohne Nachimplementieren eines Parsers für frei formulierte Sätze feststellen können:

- welcher Jobstand, welche Runtime und welche Taskbibliothek tatsächlich lief;
- welcher Task und welcher innere Fachaufruf begannen, endeten oder noch aktiv waren;
- ob Konfiguration, fachliche Arbeit oder Ressourcenfreigabe scheiterte;
- welche ursprüngliche Ursache und welche zusätzlichen Cleanupfehler vorhanden waren;
- welche sichere Quellposition, SQL-Datei, Modell-/Klassenreferenz oder HTTP-Zielidentität betroffen war;
- was über den Ausgang **belegt** ist und was unbekannt bleibt;
- ob das Log selbst unvollständig, gekürzt oder nicht vollständig exportiert wurde.

Dies hilft Menschen und einem späteren read-only Diagnoseagenten. Ein Logeintrag ist keine Anweisung an einen Agenten, kein Beweis für eine fachliche Datenursache und keine Ermächtigung zur automatischen Änderung produktiver Daten. Inhalt fremder Fehlermeldungen bleibt nicht vertrauenswürdige Diagnosedaten.

## LOG-010 — Befund gegenüber bestehendem GRETL

Verglichen wurden feste Quellstände, nicht eine laufende produktive Installation:

| Quelle / Beobachtung | Ableitung für die neue Runtime |
|---|---|
| `GretlLogger` bietet `info(String)`, `debug(String)`, `error(String,Throwable)` und `lifecycle(String)`. Der Kommentar verlangt Start/Ende pro Step. | Den nützlichen Lifecycle erhalten, aber durch den Runner automatisch erzeugen. Strukturfelder und WARN ergänzen. Keine separate Lifecycle-Severity im neuen Modell. |
| `LogEnvironment` wählt eine globale Factory, unter anderem anhand verfügbarer Gradleklassen; Standalonefallback ist DEBUG. | Backend einmal am CLI-Bootstrap explizit konfigurieren. Kein Klassenpfad-Raten, kein implizites verbose Logging sensibler Fachbibliotheken. |
| `Ehi2GretlAdapter` baut Zeile, Objektklasse und IDs zu einem Textpräfix zusammen. | Verfügbare Felder als Struktur erhalten; sensible fachliche Identitäten nach eigener Freigaberegel behandeln. |
| Beim EHI-Fehlerpfad wird `logger.error(..., null)` aufgerufen, obwohl das Event eine Exception besitzen kann. Das Muster besteht auch im geprüften gretl-next-Adapter. | Tatsächliches Throwable an den Logpfad übergeben; Ursache und suppressed-Ausnahmen erhalten. Nicht nur `getMessage()` kopieren. |

Belege: [GretlLogger](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/logging/GretlLogger.java), [LogEnvironment](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/logging/LogEnvironment.java), [EHI-Adapter](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/logging/Ehi2GretlAdapter.java), [gretl-next EHI-Adapter](https://github.com/edigonzales/gretl-next/blob/08bd11e97a54f32f8702d34ff16fb39bec6ea046/gretl-core/src/main/java/ch/so/agi/gretl/logging/Ehi2GretlAdapter.java).

Diese Beobachtungen bedeuten nicht, dass bestehende GRETL-Logs wertlos sind oder nirgendwo zusätzliche Informationen vorkommen. Sie zeigen konkrete Informationen, die am untersuchten API-/Bridgeübergang nicht strukturiert transportiert beziehungsweise verworfen werden.

## LOG-020 — Liefergrenze

| Im Script-MVP A/B | Später optional |
|---|---|
| Textkonsole und JSON-Zeilenformat | OpenTelemetry-SDK/-Agent-Profil |
| Automatische Lifecycleereignisse und Korrelation | OTLP-Export zu vorhandenem Collector |
| Fortschritt und Laufzeitheartbeat | Verteilte Traces über HTTP und nachgewiesene Bibliotheksinstrumentierung |
| Cause-/Suppressed-Ketten, klare Fehlerpositionen | Metriken/Dashboards und Collector-Alarmregeln |
| SLF4J-/JUL-Anbindung, EHI-Anbindung bei B | Besondere Bridges weiterer Taskbibliotheken |
| Lokale Ereignisdatei und abschliessender Runbericht | Externe langfristige Speicherung |
| Betriebsbeispiele für Jenkins und Kubernetes | Installation/Administration einer Clusterlogging-Plattform |

Der normale Start benötigt weder Netzwerkzugriff für Telemetrie noch einen Collector. Kein Hintergrunddienst im Runner und kein zusätzlicher Sidecar pro Job als Voraussetzung. Die vorhandenen CLI-Befehle bleiben allgemein; es entstehen keine Publisher-Reparaturbefehle.

## LOG-021 — Implementierungsweg, keine neue Loggingplattform

Empfehlung für das Implementierungsprofil: **SLF4J 2.x als Backend-Fassade, genau ein kompatibler Logback-Provider**, ein kleiner GRETL-Adapter für den öffentlichen JDK-only `TaskLogger`, ein gemeinsamer Sanitizer und Text-/JSON-Encoder. Die genauen Artefakte werden bei M0 aus den dann geprüften stabilen Versionen gepinnt und inventarisiert; dieses Kapitel behauptet keine bereits gemeinsam getestete neue Patchkombination.

SLF4J unterstützt strukturierte Key-Value-Angaben und die Übergabe einer Ursache. Diese Mechanismen sollen genutzt werden, nicht ein eigener universeller Logger mit beliebigen Objektcodecs. [SLF4J-Handbuch](https://slf4j.org/manual.html)

Taskbibliotheken liefern höchstens die SLF4J-API beziehungsweise den `TaskLogger`-Zugang, **keinen eigenen konkurrierenden Backendprovider und keine globale Logbackkonfiguration**. Ein weiterer Provider im Distributionsbestand ist vor Taskarbeit ein klarer Integrationsfehler. Die gemeinsame Ausgabe darf nicht durch zufällige Classpathreihenfolge wechseln.

`RunEventSink` ist eine interne kleine Schnittstelle für Lifecycleereignisse; sie ist kein Eventbus und keine Steuerungsquelle. Der Runner entscheidet den Outcome unabhängig von ihr. Logger-/Bridgefehler werden als Observabilitydegradation dokumentiert, nicht als nachträglicher fachlicher Fehler.

## LOG-030 — Kanäle und Konfiguration

### Ausgabevertrag

- Alle von GRETL verwalteten Logs gehen auf **stderr**. Kubernetes erfasst auch diesen Strom; stderr ist nicht gleich ERROR-Level.
- Globale Hilfe, Versionsausgabe und bewusst aufgerufene Listen/Planansichten gehen auf stdout. stdout wird nicht gleichzeitig als garantierter JSON-Resultatkanal verwendet.
- Zusätzlich entsteht `<runId>.events.jsonl` im `runtime.reportRoot`, standardmässig als private UTF-8-Datei. Sie liegt ausserhalb des kurzlebigen Taskworkspaces.
- Der kontrollierte Abschluss schreibt `<runId>.json` beziehungsweise `--report`. Dessen Pfad ändert den Ort der Ereignisdatei nicht.
- Textkonsole kann auf Wunsch mehrzeilige eingerückte Stackdetails zeigen; JSON-Ausgabe enthält **genau ein vollständiges JSON-Objekt pro physischer Zeile**. Zeilenumbrüche in Meldungen/Stacks werden escaped.
- Kein ANSI, keine Carriage-return-Fortschrittsanimation und keine dynamischen Konsolenüberschreibungen im JSON-/Containerpfad.

Die Containerlaufzeit stellt stdout/stderr über die CRI-Logpipeline und `kubectl logs` bereit. Sie ersetzt keine externe Retention nach Podverlust. [Kubernetes Logging Architecture](https://kubernetes.io/docs/concepts/cluster-administration/logging/)

### Konfigurationsschema-Erweiterung

Zusätzlicher bekannter Coreabschnitt `logging`, nicht innerhalb von `taskConfig`:

```json
{
  "schemaVersion": 1,
  "logging": {
    "format": "text",
    "level": "INFO",
    "libraryLevels": {},
    "eventFile": true,
    "maxRecordBytes": 16384,
    "maxEventFileBytes": 134217728,
    "progressInterval": "PT10S",
    "heartbeatInterval": "PT30S"
  }
}
```

Dies ist ein Konfigurationsfragment; weitere Coreabschnitte bleiben unverändert. `format` ist `text|json`; Level `TRACE|DEBUG|INFO|WARN|ERROR`. `libraryLevels` ist eine ausdrücklich konfigurierte Map von Loggerpräfix zu Level. Standardbibliothekslevel ist WARN, soweit nicht für einen konkreten Adapter ein geprüfter INFO-Pfad sinnvoll ist. Ein pauschales TRACE aller HTTP-/SQL-Libraries gehört nicht zur Fehleranalysevorlage.

`--log-format text|json` und `--log-level LEVEL` überschreiben diese beiden Einstellungen bei `run`, `check` und `plan`. `--debug` ist keine zweite allgemein eingeführte Aliaswelt. Einmal festgelegte Levels werden nicht während des Runs automatisch eskaliert. Ein optionaler vorgegebener Loggerlevel muss beim tatsächlichen Start inventarisiert werden; keinerlei Secretwerte in Konfigurationsdump.

Defaults: text/INFO lokal, JSON wird in Containerbeispielen **explizit** gesetzt. Kein unzuverlässiges Raten anhand von `KUBERNETES_SERVICE_HOST`. Globale Hilfe/Version benötigen kein Backend-/Groovy-/Providerladen. Frühe Konfigurationsfehler erhalten sichere Bootstrapmeldungen, bei explizitem `--log-format json` bereits JSON.

`runtime.jobName` ist optionaler stabiler Jobname, sonst Scriptbasename. `runtime.correlationId` ist eine optional vom aufrufenden System gelieferte opaque Korrelation, kein selbst gebasteltes traceparent. Beide sind Text bis 256 Zeichen ohne Steuerzeichen. `GRETL_JOB_NAME` und `GRETL_CORRELATION_ID` können diese zwei Werte im Startadapter liefern; explizite Runtimekonfiguration hat Vorrang. Sie ändern keine Taskparameter.

## LOG-031 — Ereignisdatei, Grenzen und Ausfälle

Die Ereignisdatei enthält den strukturierten INFO-Lifecycle auch dann, wenn die Konsole auf WARN steht. Frei formulierte Tasknachrichten folgen dem konfigurierten Level; Librarypräfixe ihrem eigenen Level. TRACE-/DEBUG-Inhalte werden nicht heimlich unabhängig von der Konfiguration archiviert.

Initiale Erstellung von notwendigen Report-/Ereignispfaden wird vor der Facharbeit geprüft. Bei eventFile=true und nicht möglicher Erstellung endet der Start mit Code 2. `eventFile=false` ist eine bewusste Betriebsentscheidung, etwa wenn ein vorhandener zuverlässiger Streamcollector genügt; der kontrollierte Runbericht bleibt erforderlich.

Die Konfigurationswerte sind strikt: maxRecordBytes ist eine ganze Zahl von 4096 bis 65536; maxEventFileBytes eine ganze Zahl von 1048576 bis 1073741824. Format/Level sind die beschriebenen Enums, eventFile Boolean, Präfixe der libraryLevels nichtleer und ohne Steuerzeichen. Beide Intervalle sind positive ISO-8601-Dauern zwischen einer Sekunde und 24 Stunden. Ungültige Werte scheitern vor Runbeginn; keine stillen Grössenüberläufe oder unbegrenzte Einstellung per null.

Default pro Event: maximal 16 KiB UTF-8 einschliesslich Envelope. Default Ereignisdatei: 128 MiB plus eine reservierte 256-KiB-Abschlussreserve. Bei Erreichen des allgemeinen Dateilimits werden normale Folgeereignisse dort nicht mehr geschrieben, Konsole läuft weiter. Ein `gretl.logging.truncated`-Eintrag und `logging.fileTruncated=true` im Bericht weisen die Grenze aus. Terminalereignisse werden soweit möglich in die Reserve geschrieben. Auch deren Verlust bei vollem Dateisystem muss ausdrücklich im Abschlusszustand stehen. Keine Zusage, dass ein beliebig grosser Run in einer begrenzten Datei vollständig bleibt. Der Encoder reduziert zuerst optionale Attribute und redundante Stackframes, danach freie Textlängen; Identität, Severity, Ereignisart und Kürzungskennzeichnung bleiben. Auch bei ausgeschöpfter Envelopegrösse entsteht gültiges JSON, keine abgeschnittene Bytefolge. Stringkürzung respektiert UTF-8-Grenzen; das Limit gilt nach Encoding.

Der MVP verwendet lokale synchrone Ausgabe ohne versteckte asynchrone Verlustqueue. Eine nicht funktionierende Senke führt nicht zu unbegrenzten Retries. I/O-Fehler beim späteren Schreiben deaktivieren nur die betroffene Senke, erzeugen eine begrenzte Warnung auf verbleibenden Kanälen und setzen `logging.degraded=true`. Sie verändern nicht nachträglich den fachlichen Runstatus. Die erforderliche Abschlussbericht-Datei ist davon getrennt: ihr unbehandeltes Schreiben kann gemäss OPS-041 Code 10 ergeben.

Synchrones Schreiben kann bei einer blockierten OS-Pipe warten. Das ist eine dokumentierte Betriebsgrenze, keine harte Timeoutgarantie. Ein später nach Messung eingeführter AsyncAppender benötigt einen expliziten Queue-/Overflow-/Flushvertrag. Insbesondere darf dessen Default nicht still INFO-Lifecyclemeldungen verwerfen; Logback dokumentiert Level-basiertes Verwerfen beim AsyncAppender unter Last. [Logback Appenders](https://logback.qos.ch/manual/appenders.html)

## LOG-040 — Kanonisches JSON-Ereignis

Dieses Schema ist ein **GRETL-JSON-Logformat**, nicht OTLP/JSON. Ein Collector benötigt bei OTLP-Weitergabe eine definierte Feldabbildung.

```json
{
  "schema_version": 1,
  "timestamp": "2026-10-18T05:12:34.567Z",
  "sequence": 42,
  "event_id": "cc35045e-8a2f-4f5e-935e-d59adad4b483:42",
  "level": "INFO",
  "event_name": "gretl.operation.finished",
  "message": "SQL-Dateien erfolgreich ausgeführt",
  "command_id": "cc35045e-8a2f-4f5e-935e-d59adad4b483",
  "run_id": "230305b8-bd98-4b23-a5b8-6c7e8b8ab4a7",
  "job_name": "adressen_import",
  "task_id": "tabellenAktualisieren",
  "invocation_id": "i000001",
  "operation_id": "gretl.sql-executor",
  "phase": "cleanup",
  "outcome": "SUCCEEDED",
  "duration_ms": 1824,
  "resource": {
    "service.name": "gretl",
    "service.version": "0.1.0",
    "deployment.environment.name": "test"
  },
  "attributes": {
    "db.alias": "edit",
    "sql.execution_count": "2"
  }
}
```

`command_id` korreliert auch Scriptladen und Startfehler ohne begonnenen Run. `run_id` kommt erst beim tatsächlichen Run dazu. `sequence` steigt pro Prozessereignis monoton und wird vor den Senkenfiltern vergeben; Lücken bedeuten nicht zwingend Verlust, weil Filter/Rate-Limits wirken können. `event_id` wird daraus gebildet und bleibt in mehreren Senken gleich.

Zeitstempel ist UTC/RFC3339. Dauern sind ganzzahlige Millisekunden aus monotoner Zeitmessung, nicht die Differenz gerundeter Wallclockwerte. Abwesende optionale Felder werden weggelassen, nicht durch leere Strings oder erfundene IDs ersetzt. Kernfelder sind unveränderlich für Tasklogger. Eigene sichere Felder stehen unter `attributes`.

Tasklogger dürfen die vom Runtime-Lifecycle reservierten Ereignisnamen wie `gretl.run.finished` oder `gretl.task.failed` nicht selbst erzeugen. Ihre Standardnachricht heisst `gretl.task.message`; zusätzliche Namen bestehen aus höchstens 128 Zeichen `[A-Za-z0-9_.-]` und sind keine Steuerbefehle. Diese Regel schützt verantwortete APIs, nicht absichtlich frei geschriebene System.out-Texte.

Reservierte Felder: schema_version, timestamp, sequence, event_id, level, event_name, message, command_id, run_id, job_name, task_id, invocation_id, operation_id, phase, outcome, duration_ms, resource, source, error, error_ref, trace_id, span_id, trace_flags. Provider dürfen sie nicht durch ein eigenes Feld überschreiben.

`source` kann sichere Jobdatei, Zeile und Spalte enthalten. `error` ist der begrenzte Fehlergraph aus LOG-050. Folgeereignisse verwenden `error_ref`. `resource` enthält ausdrücklich erlaubte Prozess-/Laufzeitinformationen, nicht beliebige Umgebungsvariablen, Podlabels oder Konfigurationsdaten.

### OpenTelemetry-Abbildung

| GRETL | OTel Logs Data Model |
|---|---|
| timestamp | Timestamp; Collector ergänzt ObservedTimestamp |
| level | SeverityText plus definierte Zahl: TRACE 1, DEBUG 5, INFO 9, WARN 13, ERROR 17 |
| message | Body |
| event_name | EventName |
| resource | Resource attributes |
| trace_id/span_id/trace_flags | Entsprechende Kontextfelder, wenn wirklich vorhanden |
| übrige beschriebene Felder | Log Attributes unter stabilen Namen |

OTel trennt Zeit, Severity, Body, Resource und Attribute. Die Referenz definiert dieses Datenmodell, nicht das konkrete GRETL-Wireformat. [OTel Logs Data Model](https://opentelemetry.io/docs/specs/otel/logs/data-model/)

Für nicht-OTLP-JSON sind `trace_id`, `span_id` und `trace_flags` als Top-Level-Felder vorgesehen. [OTel Trace Context in non-OTLP Formats](https://opentelemetry.io/docs/specs/otel/compatibility/logging_trace_context/)

## LOG-041 — Lifecycle, Phasen und Fortschritt

| Ereignis | Besitzer / Zeitpunkt | Defaultlevel |
|---|---|---|
| `gretl.run.started` | Runner nach gültigem Startsetup | INFO |
| `gretl.task.started` | Vor Predicate/Body, Identität bereits gesetzt | INFO |
| `gretl.task.skipped` | Predicate=false oder finishJob, mit Grund | INFO |
| `gretl.operation.started` | Invoker vor Requestbereitung | INFO |
| `gretl.operation.progress` | Koalesziertes fachliches Progressupdate | INFO |
| `gretl.operation.heartbeat` | Runner lebt, Aufruf noch offen | INFO |
| `gretl.operation.finished` | Handler, Typprüfung und Freigabe erfolgreich | INFO |
| `gretl.operation.failed` | Aufruf wirft zum Caller; kann dort noch behandelt werden | WARN mit Fehlerdetail/-referenz |
| `gretl.task.finished` | Ganzer Task einschliesslich seiner Freigaben erfolgreich | INFO |
| `gretl.task.failed` | Unbehandelter Taskfehler | ERROR, bestehende Fehlerreferenz |
| `gretl.task.cancelled` | Kontrollierter Abbruch | WARN |
| `gretl.cleanup.started/finished/failed` | Jobcleanup mit Name und eigener Dauer | INFO / ERROR bei unbehandeltem Fehler |
| `gretl.run.finished` | Endgültiger kontrollierter Runabschluss mit Exitcode | INFO, WARN bei Cancel, ERROR bei Fehler |

Phasen des generischen Aufrufs: `configuration`, `execution`, `cleanup`. Adapter können zusätzlich fachliche `task.phase`-Werte melden, etwa `connect`, `execute_script`, `commit`, `validate`. Der Core interpretiert sie nicht als neue Lifecyclezustände. Fachliche Phasennamen sind stabil, keine Nutzerdaten.

Ein einfacher Task muss nicht selbst Start-/End-Logs schreiben. Ein dynamisch gestarteter innerer Aufruf wird wie eine normale Operation korreliert. Ein Fehler im Builder erscheint im gleichen Aufruf wie ein Handlerfehler, nur mit anderer Phase. Ein untergeordneter Fehler macht einen bewusst erfolgreichen Catch der Action nicht rückwirkend unwirksam.

`context.progress(Progress)` aktualisiert einen kleinen threadsicheren Snapshot. Erstes Update, Phasenwechsel, letzter Stand und Änderungen nach mindestens progressInterval werden geschrieben. Dazwischen wird koalesziert, nicht je Objekt geloggt. `completed` ist innerhalb einer Phase monoton; eine niedrigere Zahl wird als fehlerhaftes Adapterupdate diagnostiziert und nicht als Rückwärtsfortschritt dargestellt. Unbekannter Totalwert bleibt unbekannt.

Nach heartbeatInterval ohne ausgegebenen Fortschritt schreibt ein einzelner runeigener Timer einen Heartbeat aus dem aktuellen Snapshot: offene Invocation, vergangene Dauer, Zeit seit letzter gemeldeter Änderung und gegebenenfalls Zahlen. Der Heartbeat beweist nur einen noch lebenden Prozess-/Timerpfad, **keinen fachlichen Fortschritt**. Er löst keine Liveness-Neustarts und keine automatische Fehlerklassifikation aus. Timer wird vor Backendshutdown beendet.

## LOG-050 — Ursachenketten und Detailausgabe

Keine Verwendung von bloss `logger.error(e.getMessage())` am Laufzeitübergang. Originalexception am Java-Caller erhalten; Cause und suppressed-Ausnahmen inklusive ihrer Klassen, sanitisierten Meldungen und relevanten Stackframes als Graph erfassen. Zyklen und mehrfach referenzierte Throwables werden per Identität erkannt.

Defaultlimits pro Fehlergraph: höchstens 16 Throwables, 64 Frames pro Throwable, 8 suppressed-Kanten pro Knoten und 2 KiB Meldung pro Knoten, insgesamt innerhalb des Eventlimits. Abgeschnittene Teile tragen explizite Flags und Zähler. Zur Wahrung des Gesamtlimits zuerst wiederholte Frames und redundante Präfixe kürzen; Grundtyp, sichere Primärmeldung, erste relevante Job-/Adapterposition und Existenz einer Nebenursache möglichst erhalten. Kein stilles Entfernen einer Cleanupursache.

Ein `error.id` entsteht beim ersten erfassten Fehler dieser Aufrufkette. Der Detailgraph wird **höchstens einmal pro verantworteter Senke und Fehleridentität** geschrieben. Eine wegen Logschwelle nicht ausgegebene Detailmeldung gilt nicht als zugestellt. Beispiel: die innere WARN-Meldung wird bei Konsolenlevel ERROR gefiltert; der spätere Task-ERROR muss dann dort erstmals die nötigen Details erhalten und darf nicht nur auf eine auf dieser Konsole nie ausgegebene Ursache verweisen. Operation-/Task-/Runabschluss können danach auf dieselbe ID verweisen. Bei neuem Wrapper mit derselben Ursache wird seine Zusatzinformation als eigene oberste Referenz ergänzt, nicht die gesamte Kette dreifach wiederholt. Die Zuordnung bleibt begrenzt und runlokal; keine globale Error-Signature-Datenbank erforderlich. Maximal 1024 abgeschlossene nicht mehr aktive Fehleridentitäten werden gehalten; aktuelle Fehlerketten und die primäre Runursache bleiben geschützt. Ältere Detailhistorie darf aus dem Heap entfernt werden, mit Zählern/Kürzungsanzeige. Die aktuelle Exception muss weiterhin darstellbar sein; nach einer Verdrängung darf ein neuer Detailbeleg entstehen, statt auf nicht vorhandene In-Memory-Daten zu vertrauen.

Gefangene Fehler: Ein gescheiterter innerer Aufruf bleibt diagnostisch sichtbar. Die Action kann dennoch SUCCEEDED sein. Ein künftiger Elternspan darf dann nicht allein wegen dieses Kindes Error werden. OTel empfiehlt, Fehler im Kontext der jeweils beobachteten Operation zu beurteilen und dieselbe Exception nicht mehrfach aufzuzeichnen. Diese SemConv-Empfehlung ist auf der Quellseite noch als Development markiert; der konkrete GRETL-Vertrag steht unabhängig davon hier fest. [OTel Recording Errors](https://opentelemetry.io/docs/specs/semconv/general/recording-errors/)

Der globale ERROR-Zähler ist **nicht** die Zahl fehlgeschlagener Runs. Automatische Alarme verwenden terminalen Runstatus beziehungsweise fehlenden erwarteten Abschluss, nicht ein beliebiges ERROR in einer behandelten inneren Operation.

## LOG-051 — Konkrete Diagnoseverbesserungen der Standardtasks

### HTTP

Melden: Operation, Methode, konfigurierte Zielidentität/Host, gegebenenfalls freigegebener normalisierter Pfad, Statuscode, tatsächlich gelesene Bytes, Dauer und Phase. Nicht melden: vollständige URL mit Query-/Userinfo-/Fragment, Authorization, Cookies, Body, Serverantwort mit Token oder beliebige Header. Bei neuem URL-Template kann auch der Pfad Personen-/Tokeninformationen enthalten; Default deshalb nur Origin/Host, freigegebene Pfade nur ausdrücklich.

Transportfehler ist kein NO_DATA. HTTP-Status != Erfolg wird fachlich im Adapter erklärt, nicht aus einer fremden Logseverity bestimmt. Kein automatischer POST-Retry.

### SqlExecutor

Melden: Datenbankalias, Scriptpfad relativ zum Job, Scriptindex, Hash der tatsächlich gelesenen SQL-Ressource, Dauer, Phase (`connect/execute/commit/rollback`), SQLSTATE/Vendorcode, vorhandene Serverposition sowie fachlich bekannte Statement-/Updatezahlen. SQLSTATE allein unterscheidet Datenfehler und falsch geschriebenen Umbau nicht sicher.

Keine Werte der bindbaren Parameter, keine komplette SQL-Query im Standardlog, keine vollständige JDBC-URL mit Credentials, keine ungefilterte PostgreSQL-DETAIL-Zeile mit Datensatzwerten. Interne SQLException samt Ursache bleibt für kontrollierte Fehlerbehandlung erhalten; deren Logdarstellung muss ebenfalls durch die Redaction-/Datenminimierungspolitik gehen.

Serverpositionen sind nur dann in eine Dateizeile umzurechnen, wenn der qualifizierte Treiberpfad ihre Bezugsbasis eindeutig liefert. Sonst `sql.position` plus `sql.position_basis=driver` ausgeben; keine geschätzte Zeile aufgrund eines naiven Semikolon-Splitters. Scriptbytehash und sichere Dateireferenz erlauben später einen Vergleich mit der tatsächlich ausgeführten Revision.

pgJDBC verwendet JUL für Logging und unterstützt weitere Diagnoseoptionen. Die Auswahl wird im Adapter geprüft; ein Setting in einer JDBC-URL kann laut Dokumentation ein gleichnamiges Properties-Setting übersteuern. Daher Logsicherheit nicht allein mit unkontrolliert übergebenen Verbindungsoptionen behaupten. [pgJDBC Initializing the Driver](https://jdbc.postgresql.org/documentation/use/)

### IliValidator / EHI

Erhalten: Modell-/Klassenbezug, vorhandene Datenzeile, Eventkind, tatsächliche Exception und strukturierte Validierungsstatistik. Objekt-TID/technische ID können fachlich hilfreich, aber sensibel sein. Default: Objektklasse und Zeile; zusätzliche Objektkennungen nur im geschützten Validierungsbericht beziehungsweise nach ausdrücklicher Betriebsfreigabe. Benutzeridentifikatoren nicht pauschal in zentrale Logs senden.

Die EHI-Bridge wird vor dem Fachaufruf mit dessen explizitem Kontext verbunden. Exakte Listenerreferenzen werden nach dem Aufruf im finally entfernt beziehungsweise auf den vorherigen Stand zurückgesetzt. Eine globale Defaultlistenerentfernung ohne Wiederherstellung ist kein per-Invocation-Lifecycle. Mehrfachausführung darf keine Listener akkumulieren oder Logs doppelt schreiben.

Vorhandene Backendexceptions werden tatsächlich übergeben, nicht wie im untersuchten Adapter durch null ersetzt. Ein vollständiger Validierungsbericht wird als geschütztes, aufrufbezogenes Artefakt erstellt. Zentrale Logs enthalten begrenzte Beispiele, Zähler und den sicheren Artefaktverweis, nicht Millionen Datensatzmeldungen. Der Adapter zählt Warnungen/Fehler aus tatsächlichen Engineevents, nicht aus Textmustern wie `contains("error")`.

Samples: maximal 20 Details pro stabilem Eventkind und 200 pro Invocation; Gesamtsummen werden weitergeführt. Die erste technische Exception sowie terminale Zusammenfassung werden nicht durch dieses fachliche Sampling unterdrückt. Kürzung ist ausgewiesen. Ein ungültiger Transfer liefert weiterhin kein erfolgreiches ValidationResult.

## LOG-052 — Brücken und Kontextschutz

SLF4J-/JUL-/EHI-Bridges werden im tatsächlichen Klassenpfad geprüft. Es darf keine Schleife JUL→SLF4J→JUL, SLF4J→Log4j→SLF4J oder OTel-Exporter→Logger→Exporter entstehen. SLF4J beschreibt die Grenzen solcher Legacybrücken. [SLF4J Legacy APIs](https://www.slf4j.org/legacy.html)

Ein öffentlicher `TaskLogger` ist an seine explizite Identität gebunden. Für fremde threadgebundene Logger setzt der Invoker zusätzlich MDC und gegebenenfalls einen unterstützten Librarycontext im aktuellen Thread. Vorherigen MDC-Snapshot sichern und im finally vollständig wiederherstellen, auch beim Builderfehler und geschachtelten Aufruf. Logback-MDC ist threadbezogen; eine automatische Weitergabe in beliebige Threads ist nicht zugesagt. [Logback MDC](https://logback.qos.ch/manual/mdc.html)

Hintergrundlogs ohne verlässlich mitgegebenen Taskkontext erhalten nur Prozess-/Runidentität und `context.scope=unattributed`. Nicht die aktuell zufällig laufende Task hineinraten. Ein eigener Helperthread eines Adapters muss Kontext ausdrücklich übernehmen und vor Aufrufende gejoined werden.

Normales Groovy-`println` der Scriptbasisklasse wird als strukturierte `gretl.script.message`-Nachricht geroutet und durchläuft Redaction. Direkte `System.out`-/`System.err`-Schreibzugriffe oder native Ausgaben werden nicht global mit einem riskanten PrintStream-Hook abgefangen. Sie bleiben Fremdausgaben mit dokumentierter Grenze; die produktiven Standardadapter sollen solche Ausgaben vermeiden beziehungsweise ihre Librarylogger korrekt anbinden. „Alle denkbaren Ausgaben sind garantiert JSON“ wird nicht behauptet.

## LOG-060 — Datenschutz und Secrets

**Zuerst vermeiden, dann maskieren.** Logmetadaten bestehen aus einer Allowlist sicherer Felder. Kein automatischer Dump von Runtimekonfiguration, Umgebungsvariablen, Request, Resultat, HTTP-Antwort oder parametrisierten SQL-Werten.

`SecretValue.toString` ist maskiert. Beim tatsächlichen Reveal registriert der Context den Wert vor Rückgabe. Für dynamische Token gibt es `ctx.sensitive(value)` aus Kapitel 14. Die Registrierung lebt nur bis Runende; Strings im Speicher sind damit nicht sicher löschbar. Ein Token in einer bewusst geschriebenen Antwortdatei wird dadurch nicht verändert.

Redaction erfolgt **vor** Text-/JSON-Encoding, Datei-/Konsolensinks und optionalem Export. Alle Produktpfade verwenden dieselbe Sanitizingstufe. Längste bekannte Secretwerte zuerst ersetzen. Default maximal 256 gleichzeitig registrierte Werte mit je höchstens 64 KiB. Ein darüber hinausgehender `sensitive`-/Secretzugriff wird vor Herausgabe abgelehnt, statt unbemerkt ungeschützt zu bleiben. Bei extrem kurzen Werten zusätzlich feldbasierte Geheimnisregeln anwenden; keine Behauptung semantisch vollständiger Erkennung jeder Kodierung.

Bekannte Schlüsselnamen wie password, authorization, cookie, token und credential werden defensiv gefiltert, aber diese Heuristik ersetzt die Allowlist nicht. URL-Queries/Fragments und JDBC-Credentials werden unabhängig von der Secretregistrierung nicht ausgegeben. Steuerzeichen/Zeilenumbrüche werden sicher encoded; untrusted Logtexte können keine zweite JSON-Zeile oder falsche Lifecyclezeile einschleusen.

Exceptionmeldungen können Nutzdaten enthalten. Für die mitgelieferten SQL-/INTERLIS-/HTTP-Adapter werden genaue sichere Meldungen und ergänzende strukturierte Hinweise erzeugt; ungefilterte Rohdetails gehören, soweit überhaupt benötigt, ausschliesslich in einen explizit geschützten Bericht. Die generische Runtime kennt nicht alle fachlichen Personendaten und kann keine vollständige DLP-Zusage machen. Die fremde Throwableklasse und Causebeziehung können erhalten bleiben, während die Textmeldung als `[sensitive detail omitted]` ausgewiesen wird.

Berichte/Events standardmässig 0600, Arbeitsordner 0700 soweit Plattform/Volume dies unterstützt. Keine automatische Veröffentlichung der Validierungsberichte. Retention, Zugriff und Weiterleitung werden organisatorisch geregelt. OpenTelemetry empfiehlt Datenminimierung und gezielte Verarbeitung sensibler Attribute; ein Hash ist nicht automatisch eine Anonymisierung. [OTel Sensitive Data](https://opentelemetry.io/docs/security/handling-sensitive-data/)

## LOG-070 — Provenienz und Diagnoseagenten

Jeder Runstart enthält Runtimeversion, Runtimebuildrevision, tatsächliches Artefaktinventar beziehungsweise dessen Hash, Jobname, Hash der geladenen Scriptbytes und soweit verfügbar eine ausdrücklich gelieferte Jobrevision. Vom Betreiber gelieferte Revisionen tragen `job.revision_source=provided`; sie werden nicht als kryptografisch geprüfter aktueller Checkout behauptet. Kein Aufruf von `git` in jedem Task.

Ein einzelnes Scripthash ist kein Hash seiner gesamten Abhängigkeitswelt. Das zusätzliche Inventar beschreibt Runtime/Taskbibliotheken, und die tatsächlich verwendeten SQL-/Modellressourcen werden vom entsprechenden Adapter referenziert. Der spätere Packager liefert eine feste Paketidentität mit Ressourceninventar. Es werden keine parameter- oder datenabhängigen sensiblen Hashes als vermeintlich kostenlose Diagnosefelder erzeugt.

Ein Ops-Agent kann damit letzten vergleichbaren Erfolg, ersten Fehler, Jobrevision und SQL-Ressource vergleichen. Der Bericht trennt beobachtete Daten, Hinweise und unbekannte Bedingungen. Ein fehlender `run.finished`-Eintrag kann Prozesskill, Collectorverlust oder Loggingverlust bedeuten; der Agent braucht dann zusätzliche Jenkins-/Kubernetes-Belege. Er darf nicht automatisch „Datenfehler“ oder „Rollback erfolgreich“ behaupten.

## TEL-010 — Kubernetes/OpenShift-Betrieb

### Bereits im MVP sinnvoll

1. Ein Jobprozess als Containerhauptprozess; Launcher verwendet `exec java ...`, damit Signale ankommen.
2. JSON auf stderr; vorhandene Clusterlogging-/Forwardinglösung nutzen, keinen zusätzlichen Collector im Job erzwingen.
3. Korrelation über Run-ID plus Pod-/Namespace-/Container-/Jobidentität aus Downward API beziehungsweise Collector-Enrichment.
4. Ressourcenlimit, kooperatives Timeout und externe Nachfrist getrennt verstehen.
5. Runbericht/Ereignisdatei bei Bedarf in ein beschreibbares Volume und von dort kontrolliert archivieren. `emptyDir` ist nach Podverlust keine Retention.

Ein Collector liest zunächst den Containerlog-Rahmen und setzt gegebenenfalls vom CRI gesplittete Teile wieder zusammen; erst dann wird die Anwendungszeile als JSON interpretiert. Die Aussage „ein Objekt pro Anwendungszeile“ bedeutet nicht, dass jede ungeparste physische Containerlogzeile bereits genau dieses JSON ist. Diese Pipeline wird anhand langer echter Fehlermeldungen geprüft.

Kubernetes kann Podmetadaten ohne eigene API-Anfragen via Downward API bereitstellen. [Downward API](https://kubernetes.io/docs/concepts/workloads/pods/downward-api/)

Erlaubte Metadatenquellen: `GRETL_K8S_NAMESPACE`, `GRETL_K8S_POD_NAME`, `GRETL_K8S_POD_UID`, `GRETL_K8S_JOB_NAME`, `GRETL_CONTAINER_NAME`; auf `resource.k8s.*` beziehungsweise Containerattribute abbilden. Containername wird im Manifest explizit gesetzt, nicht als vermeintlich überall vorhandenes Downward-API-Feld vorausgesetzt. Keine Sammlung aller Labels, Annotations oder Environmentwerte. Der Runner braucht keine Kubernetes-Clientbibliothek und kein RBAC zum Lesen von Pods.

OpenShift: nichtprivilegierter, zufälliger UID muss in den vorgesehenen Volume-/Arbeitsverzeichnissen schreiben können. Keine feste Home-UID oder Schreibrechte unter `/opt/gretl` voraussetzen. Das Image und seine SCC-/Volumeeinbettung werden tatsächlich getestet. Bestehende ClusterLogForwarder-Administration bleibt beim Plattformbetrieb; die aktuelle Red-Hat-Dokumentation verlangt passende Collectorberechtigungen. Dieses Dokument veranlasst keine Clusteränderung. [OpenShift Log Forwarding](https://docs.redhat.com/en/documentation/red_hat_openshift_logging/6.4/html/configuring_logging/configuring-log-forwarding)

### Kleine Beendigungsmeldung

Optionaler Startadapterwert `GRETL_TERMINATION_MESSAGE_PATH` bezeichnet eine explizite beschreibbare Datei. Bei kontrolliertem Ende schreibt der CLI-Abschluss höchstens 3 KiB sichere Kurzmeldung mit runId, status, exitCode und gegebenenfalls errorId/taskId. Keine kompletten Stacks, Secrets oder Datenobjekte. Keine automatische Schreibung nach `/dev/termination-log` ohne Konfiguration.

Kubernetes verwendet `terminationMessagePath`; der Kubelet begrenzt solche Meldungen unter anderem auf 4096 Bytes pro Container und ein podweites Budget. Deshalb kann eine kleine Nachricht hilfreicher als der letzte Ausschnitt eines langen Stacktraces sein. Bei vielen Containern kann selbst die kleine Nachricht noch gekürzt werden. [Pod Failure / Termination Message](https://kubernetes.io/docs/tasks/debug/debug-application/determine-reason-pod-failure/)

### Beispielausschnitt für den Betrieb

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: gretl-demo
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      automountServiceAccountToken: false
      containers:
        - name: gretl
          image: registry.example.invalid/gretl-runtime:QUALIFIED_VERSION
          command: ["/opt/gretl/bin/gretl"]
          args: ["run", "/jobs/job.groovy", "--config", "/config/runtime.json", "--log-format", "json"]
          terminationMessagePath: /reports/termination.log
          terminationMessagePolicy: File
          env:
            - name: GRETL_TERMINATION_MESSAGE_PATH
              value: /reports/termination.log
            - name: GRETL_K8S_POD_UID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.uid
            - name: GRETL_K8S_NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
            - name: GRETL_CONTAINER_NAME
              value: gretl
```

Dies ist bewusst ein Ausschnitt: freigegebenes Image, Jobs, Runtimekonfiguration, Secrets, Volumes, Ressourcen und organisatorische Retention ergänzen. Nicht unverändert deployen. Kein fester `runAsUser` im Beispiel. `backoffLimit: 0` reduziert ungewollte Vollwiederholungen; es garantiert **kein exactly once**. Kubernetes dokumentiert Fälle mehrfacher Programmausführung selbst bei scheinbar einfacher Jobkonfiguration. Jeder neue Versuch erhält eine neue Run-ID; Fachidempotenz bleibt beim Job. [Kubernetes Jobs](https://kubernetes.io/docs/concepts/workloads/controllers/job/)

Ein erfolgreicher Prozessstatus sagt nicht, dass Kubernetes alle Logs bereits dauerhaft exportiert hat. Bei OOMKilled/Exit 137 ist ein zusätzlicher `oc describe`-/Podstatusbeleg erforderlich; 137 allein ist nicht ausschliesslich ein OOM-Beweis. Die App kann ihren eigenen SIGKILL nicht zuverlässig protokollieren. Kein HTTP-Liveness-Server allein für einen endlichen Batchjob und keine Neustarts allein wegen ausgebliebenem fachlichem Progress.

## TEL-020 — Jenkins und vorhandene Logplattformen

Jenkins-Build-Konsolenlogs sind nicht automatisch identisch mit stdout/stderr des Jenkins-Controllercontainers. Startet Jenkins GRETL etwa als untergeordneten Prozess und übernimmt dessen Ausgabe ins Buildlog, muss dessen weiterer Erfassungsweg separat geprüft werden. Ein Collector, der nur Controller-Podlogs liest, beweist noch keine Erfassung aller GRETL-Läufe.

MVP-Weg: verständliche Konsolenausgabe im Build, zusätzlich sichere `gretl-reports/*.json` und `*.jsonl` als Buildartefakte. Niemals pauschal den gesamten Workspace mit Tokenantworten archivieren. Jenkins bietet dazu `archiveArtifacts`; Pipelinekontrollfluss sollte Archivierung auch nach einem normalen Taskfehler versuchen. Ein harter Agent-/Podverlust kann trotzdem die Dateien verlieren. [Jenkins archiveArtifacts](https://www.jenkins.io/doc/pipeline/steps/core/)

Logit.io/OpenSearch oder Grafana/Loki bleiben austauschbare Empfänger. Die App besitzt keine fest verdrahteten Logit-/Grafana-Credentials. Eine vorhandene Forwardinglösung kann die strukturierte Ereignisdatei oder den passenden Prozessstrom erfassen. Pro Logereignis **einen** primären Erfassungsweg wählen; gleichzeitig Console-Tailing und Datei-Tailing ohne Deduplikation erzeugt Doppelzählung. Gleiche event_id erleichtert eine bewusste Deduplikation.

Ein Jenkins-OTel-Plugin kann später CI-Korrelation ergänzen, ist aber keine GRETL-Runtimevoraussetzung. Die offizielle Pluginseite beschreibt diesen separaten Integrationsweg. [Jenkins OpenTelemetry](https://plugins.jenkins.io/opentelemetry)

## TEL-030 — Optionale Traces: vorbereiten, nicht erzwingen

MVP: Run-/Task-/Invocation-IDs. Echte trace_id/span_id-Felder bleiben ohne Instrumentierung **weg**. Weder Run-UUID als trace_id umetikettieren noch einen W3C-Header aus Strings zusammensetzen.

Bei später geliefertem OTel-Adapter ist der Tracebaum: Run → Task → Fachinvocation → tatsächlich instrumentierter HTTP-/DB-Aufruf. Scope beim Öffnen setzen und im finally wiederherstellen. Ein korrekter eigener Span verwendet echte Contextpropagation. Der normale Jobautor muss dafür keine Tracingmethoden aufrufen.

Aufrufender Scheduler-/Gatewaykontext darf nur über einen dokumentierten vertrauenswürdigen Carrier übernommen werden. W3C `traceparent`/`tracestate` verwenden und validieren; ein beliebiges externes `X-Request-ID` bleibt eine separate opaque Korrelation. Kein Credential/SQLparameter in Baggage. Untrusted Internetkontext nicht als kontrollierte interne Identität behandeln. [W3C Trace Context](https://www.w3.org/TR/trace-context/)

Keine doppelte Instrumentierung derselben Ebene durch eigenen Adapter und Javaagent. Ein Javaagent kann Bibliotheksaufrufe beobachten, kennt aber nicht automatisch die GRETL-Tasksemantik. Das konkrete Zusammenspiel wird in einem eigenen Integrationsprofil geprüft. Nicht angenommene globale OTel-Environmentvariablen heimlich aktivieren: ohne geliefertes Profil sind keine Exporter vorhanden.

Start-/Fehlerlogs bleiben auch bei unsampled Traces verfügbar. Trace-Sampling darf keine allgemeinen ERROR-Diagnosen oder Run-Endereignisse entfernen. `forceFlush`/shutdown werden nach Ende der Spans innerhalb der gemeinsamen Nachfrist abgewartet; Erfolg wird nur als lokale SDK-Rückmeldung bezeichnet, nicht als dauerhafter Backendnachweis. [OTel Java SDK](https://opentelemetry.io/docs/languages/java/sdk/)

## TEL-040 — Batch-Metriken und Alarmierung

Ein kurzer Batchprozess kann zwischen zwei Scrapes starten und enden. Deshalb im MVP keine neue HTTP-Metrikendpoint-/Webserverpflicht. Die Run-Endereignisse und Berichte enthalten bereits Dauer, Status und sichere Zähler und können durch vorhandene Infrastruktur ausgewertet werden.

Spätere stabile Metriken: Runs nach Ergebnis, Run-/Taskdauer, letzte kontrollierte Beendigung, letzte erfolgreiche Verarbeitung, Byte-/Objektzahlen der unterstützten Adapter. Die Prometheus-Empfehlung hebt bei Batchjobs insbesondere den Zeitpunkt des letzten Erfolgs und die Dauer hervor. [Prometheus Instrumentation](https://prometheus.io/docs/practices/instrumentation/)

`NO_DATA` ist ein erfolgreicher technischer Durchlauf, aber nicht automatisch eine erfolgreiche **Datenaktualisierung**. Ein Frischealarm muss diese beiden Begriffe unterscheiden. Wo keine neuen Daten erwartbar sind, ist NO_DATA kein Alarm. Erwartete Start-/Endzeit, Geschäftskalender und Frischebedarf gehören in die Betriebskonfiguration, nicht in den allgemeinen Runner.

Die kleine kurzlebige JVM kann keinen dauerhaften `last_success`-Zustand über alle Runs vorhalten. Ein langlebiger Empfänger oder Scheduler berechnet ihn aus bestätigten Endereignissen. Auch verpasste Starts/fehlende Enden lassen sich nur mit einer unabhängigen Erwartungsquelle zuverlässig erkennen.

Labels mit geringer Kardinalität: service, environment, stabiler job_name, operation_id und outcome. **Keine** run_id, trace_id, invocation_id, Objekt-ID, Dateiname, SQLtext oder dynamischer Taskname als Metriklabel. Solche Angaben bleiben bei Logs/Traces. Bei Loki gehören hochkardinale IDs in den Record beziehungsweise Structured Metadata, nicht in Streamlabels. [Loki Label Best Practices](https://grafana.com/docs/loki/latest/get-started/labels/bp-labels/)

Pushgateway oder OTLP-Metrikexport sind mögliche spätere Betriebswege, nicht parallel verpflichtende Features. Bei Pushgateway muss die Lebensdauer alter Serien berücksichtigt werden. Bei OTLP müssen gepufferte Daten vor Prozessende begrenzt geflusht werden. Keine Umstellung auf Daemonbetrieb nur um eine Monitoringkonvention zu erfüllen.

## TEL-050 — Telemetrie darf die Facharbeit nicht korrumpieren

Netzexporte nutzen begrenzte Queues, Deadlines und Dropzähler. Ein unerreichbares Backend darf weder einen unbegrenzten Speicheraufbau noch einen automatischen ETL-Retry verursachen. Telemetrieretry ist nicht Taskretry.

Ein Collector kann Sending Queues, Retry und optional persistente Speicherung verwenden. Auch diese Mechanismen haben Verlustgrenzen bei vollem Speicher, Crash oder abgelaufenen Retryfristen. Die Betriebsdokumentation muss daher Logretention/Verfügbarkeit ausdrücklich planen und darf stdout oder einen erfolgreichen Flush nicht als Exactly-once-Auditlog ausgeben. [OTel Collector Resiliency](https://opentelemetry.io/docs/collector/resiliency/)

Die App bleibt beobachtbar, wenn ein optionaler Export ausfällt: lokales Ereignis, Degradationszähler und Abschlusskennzeichnung. Keine Logs über einen Exporter loggen, dessen eigene Lognachricht wieder denselben Exporter aufruft. Die eigentliche Backendüberwachung liegt ausserhalb des kurzlebigen Jobs.

## LOG-080 — Ergänzungen des Runberichts

`schemaVersion: 1` bleibt die initiale noch nicht veröffentlichte Berichtsrevision. Folgende benannte Abschnitte werden präzisiert:

- `identity`: Run-/Command-ID, Job-/Paketidentität, Runtimeversion und Inventarhash.
- `source`: Script-/Paketbezug und ausdrücklich gelieferte Revision mit Herkunft.
- `execution`: ausgewählte Ziele, terminale Tasks und zusammengefasste Invocations.
- `errors`: eindeutige sichere Fehlerdetails und Referenzen, keine vollständige Reflectionserialisierung.
- `logging`: format, eventFile, degraded, fileTruncated, recordTruncations, sampledValidationDetails und bekannte Ausgabeverluste.
- `telemetry`: im MVP `mode=none`; bei späterem geliefertem Profil tatsächlicher Modus und begrenzter Flushstatus.

Diagnose-/Invocationdetails im Bericht sind begrenzt, damit eine Action mit vielen kleinen Aufrufen nicht unbegrenzt Heap belegt: maximal 10'000 Invocationzusammenfassungen und 1'000 explizite Diagnosen pro Run; vollständige Zähler plus Truncationflags bleiben erhalten. Terminale Taskoutcomes und die primäre Fehlerursache werden nicht aufgrund dieses Invocationlimits weggelassen. Die Ereignisdatei ist der detailliertere zeitliche Verlauf, aber ebenfalls kein unbegrenzter Speicher.

## LOG-090 — Implementierungs- und Prüfschritte

M0: Logging-Backend/Providerbestand mit qualifizieren; EHI-Bridgefähigkeit prüfen, keinen Stack verlieren. M1: Event-/Contextgrenze und deterministische Lifecyclezählung in Java. M2: echte Groovyquellen, `println`, innerer ctx.run-Kontext und Fehlerpositionen. M3: Encoder, Redaction, Datei-/Konsolenpfad, HTTP-/SQLdetails, Last-/Fehlersenkenprüfungen. M4: reale EHI-/Ioxfelder und kontrollierte Listenerlebensdauer. M5: Fehlerbehebung anhand der tatsächlichen Anwenderausgabe. M6: dieselben Logs, Quellen und Provider im Job-JAR. OTel-Backendintegration bleibt späterer eigener Auftrag.

Nachweise: **TC-L01 bis TC-L24** aus Kapitel 09 nach deren Lieferstufenspalte. A/B-Logging ist MVP-Pflicht; C und das spätere OTel-Profil werden nur bei tatsächlicher Lieferung geprüft. Ein optisch schönes Beispiel-JSON oder eine kompilierte Logger-SPI ist kein bestandener Integrationstest. Tatsächlicher Stand dieser Spezifikationsarbeit: [17](17-findings-und-pruefprotokoll.md).
