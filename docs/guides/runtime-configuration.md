# Runtimekonfiguration

Die optionale Ausgabesprache kann per `--lang`, `GRETL_LANGUAGE`, `language`
in `runtime.json` oder OS-Locale gewählt werden; Fallback ist Englisch.
Details stehen in [Lokalisierungsvertrag](../architecture/runtime-contracts.md#lokalisierung).

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
    "eventFile": true,
    "maxRecordBytes": 16384,
    "maxEventFileBytes": 134217728,
    "progressInterval": "PT10S",
    "heartbeatInterval": "PT30S"
  },
  "params": {"environment": "test"},
  "secrets": {
    "dbPassword": {"environment": "GRETL_DB_PASSWORD"},
    "sourceAuth": {"file": "source-auth.txt"}
  },
  "taskConfig": {
    "edit": {
      "jdbcUrl": "jdbc:postgresql://localhost/gretl_test",
      "user": "gretl_test",
      "passwordSecret": "dbPassword"
    }
  }
}
```

Pfadbasen: Job-/CLI-Dateien relativ zum Start-CWD; `runtime`-Pfade und
Secretdateien relativ zum Configordner; `file()` relativ zum Jobroot;
`ctx.path()` relativ zum Start-CWD; `workPath()` im privaten Taskworkspace;
`path`-Parameter relativ zum Start-CWD.

Unbekannte Corefelder, doppelte JSON-Schlüssel, falsche Typen, ungültige
Limits/Intervalle und widersprüchliche Secretquellen sind Code 2. Secrets werden
erst beim Zugriff aufgelöst und vor Logger/Report maskiert.

Pro Secretalias ist genau eine Quelle erlaubt: `{"environment":"GRETL_DB_PASSWORD"}`,
`{"file":"source-auth.txt"}` oder `{"plaintext":"testpasswort"}`. Die Klartextform
ergänzt `schemaVersion: 1`; bestehende Referenzen wie `passwordSecret: "dbPassword"`
und `secret('dbPassword')` bleiben gleich. Es gibt keinen automatischen Fallback.
Secretdateien enthalten den eigentlichen Wert als UTF-8, höchstens 64 KiB und
keine NUL-Zeichen; genau ein abschliessendes LF oder CRLF wird entfernt.
Environment- und Klartextwerte bleiben einschliesslich leerer Strings,
Leerzeichen und Zeilenumbrüche unverändert; keine Variablenersetzung.
`plaintext` steht direkt lesbar in der externen Runtimekonfigurationsdatei;
die Laufzeitmaskierung verschlüsselt diese Datei nicht. Alle Quellen verwenden
dieselbe Registrierung und dieselben Maskierungsgrenzen beim Zugriff.

## Ausgabedateien mit `overwrite = false`

HTTP-, Curl-, S3- und FTP-Downloads sowie Gzip und XSLT veröffentlichen erst
die fertige Datei. Bei `overwrite = false` bleiben vorhandene Ziele erhalten,
auch wenn ein anderer Prozess sie während der Verarbeitung anlegt. Für diese
Publikation muss der Dateisystemprovider einen Hardlink im Zielverzeichnis
anlegen können. Falls das Dateisystem oder die Berechtigungen dies verhindern,
meldet der Task einen Publikationsfehler.

S3 löst den konfigurierten Downloadordner einmal auf. Der Einstieg darf ein
Symlink sein. Bereits vorhandene Symlinks in den aus Objektschlüsseln
abgeleiteten Unterverzeichnissen führen zu einem Pfadfehler. Dies schützt
gegen vorhandene Links zum Prüfzeitpunkt. Gleichzeitige lokale Änderungen
am Verzeichnisbaum liegen ausserhalb dieses Schutzes.

## Curl-Formularuploads

Curl streamt Multipart-Dateiteile direkt aus den angegebenen Dateien. Die
Dateien müssen während der Anfrage stabil bleiben. Dateinamen mit CR, LF
oder NUL werden vor der Anfrage abgewiesen. `maxBytes` begrenzt weiterhin die
Antwort.

## Logging und Runbericht

- Konsole: Text (Default) oder JSON auf **stderr**; Hilfe/Plan auf stdout.
- Ereignisdatei: `<runId>.events.jsonl` unter `runtime.reportRoot`, 0600,
  Eventlimit 16 KiB, Dateilimit 128 MiB plus Terminalreserve.
- Runbericht: `<runId>.json` bzw. `--report`, atomar ersetzt, 0600.
- Text/JSON tragen dieselbe Event-/Sequenzidentität; JSON ist ein Objekt pro
  Zeile.
- `--log-format text|json` und `--log-level` überschreiben die Config.
- Bekannte Secrets, dynamische `ctx.sensitive`-Werte und sensible Feldnamen
  werden vor allen verwalteten Senken maskiert.
- HTTP-/SQL-Fehler tragen sichere strukturierte Details; Query, Header,
  Cookies, Body und SQL-Parameter werden nicht geloggt.
- Optional schreibt `GRETL_TERMINATION_MESSAGE_PATH` eine kleine
  Kubernetes-Beendigungsmeldung.
- Frühe CLI-Fehler und Laufzeitfehler verwenden im Textmodus dieselbe Struktur:
  Zeitstempel, Level, Titel, technischer Code und eingerückte Felder wie
  `Meldung`, `Feld`, `Task`, `Quelle` und `Ursache`. JSON/JSONL bleiben
  schema-stabil.

Bibliothekslogs werden über lexikalische Run-/Task-/Invocation-Scopes zugeordnet;
`LibraryScopeLocalJobIT` prüft diesen Vertrag. Heartbeat-Timer und
Progress-Koaleszenz sind nicht umgesetzt; akzeptierte Intervallfelder sind kein
Nachweis für solche Timer. Die Maskierung gilt für verwaltete Log-/Reportsenken.
Ausführbarer eigener Groovy-Code ist keine allgemeine Sandbox.
