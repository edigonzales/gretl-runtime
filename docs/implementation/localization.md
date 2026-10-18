# Lokalisierung und Diagnosemeldungen

**Status:** Implementiert. Englisch ist Quell- und Fallback-Sprache;
Deutsch wird zusätzlich ausgeliefert.

## Sprachen

- **Englisch** ist die Quell- und Fallback-Sprache.
- **Deutsch** wird als zusätzliche Sprache in der Distribution ausgeliefert.
- Technische Werte wie `event_name`, Diagnosecodes, Feldnamen und IDs bleiben
  sprachneutral.

## Kataloge

`gretl-i18n` enthält:

```text
ch/so/agi/gretl/i18n/messages.properties       Englisch (Quelle)
ch/so/agi/gretl/i18n/messages_de.properties    Deutsch
```

Die Katalogparität wird getestet. Ein nicht unterstütztes Locale fällt auf
Englisch zurück.

## Sprachwahl

Implementierte Priorität:

```text
--lang <en|de>  >  GRETL_LANGUAGE  >  language in runtime.json
  >  OS-Locale  >  Englisch
```

`language` ist ein optionales Feld der Runtimekonfiguration. Ein nicht
unterstützter Wert wird abgelehnt. Deutsch wird bei einer deutschen
OS-Locale automatisch gewählt; sonst gilt Englisch. Die CLI- und Job-JAR-Pfade
verwenden diese Auflösung. Interne Bibliotheks-Defaults bleiben für
Einbettungstests explizit deutsch, sofern kein Kontext übergeben wird.

## Abgedeckte Bereiche

- Event-Titel und Framework-Labels im Textformatter,
- globale CLI-Hilfe und Hilfeüberschriften,
- Task-Kurzbeschreibungen und Task-/Feldbeschreibungen der Standardtasks in
  `operations`,
- strukturierter Fehlerblock (`Code`, `Task`, `Field`, `Allowed fields`,
  `Message`, `Source`, `Cause`),
- die Meldung für unbekannte Task-Konfigurationsfelder
  (`Unknown configuration field ...`),
- CLI-Options- und Aufruffehler (`CliOptions`),
- Top-Level-Fehler von Runtimekonfiguration und Parameterdatei,
- Package-/Check-Ausgaben und weitere CLI-Laufzeitfehler,
- Parameterfehler des Runners (`ParameterResolver`),
- Detailfehler der Runtimekonfiguration (Logging, Secrets, TaskConfig),
- strukturierte Codes der Standard-Task-Exceptions (`HTTP_*`, `SQL_*`,
  `EXTRACT_*`, `ILIVALIDATOR_*`),
- Task-Meldungen von HttpDownload, Extract, SqlExecutor und IliValidator,
- Groovy-Loader-, DSL-, Scope- und ResultView-Meldungen,
- Registry-, Definition- und Provider-Contract-Fehler des Runners,
- Startup-, Metadaten- und Ressourcenfehler des gepackten Job-JARs,
- Packager-Meldungen (Deskriptor, Kompilierung, JAR-Bau),
- englische Formatter-/CLI-Golden-Tests.

## Grenzen

- Eigene Task-Bibliotheken verwenden ihre eigenen Meldungen und können einen
  eigenen Katalog mitbringen; ohne Katalog bleiben sie englisch.
- Interne Bibliotheks-Defaults verwenden für Einbettungstests Deutsch, wenn
  kein Locale-Kontext übergeben wird. CLI, Job-JAR und Runner übergeben die
  aufgelöste Locale explizit.

## Diagnose

Fehler tragen strukturierte Werte getrennt von der Meldung:

```text
Code: UNKNOWN_CONFIGURATION_FIELD
Task: bad
Field: unknownSetting
Allowed fields: url, output, ...
Source: job.groovy:2
```

Die Textdarstellung rekonstruiert diese Werte nicht mehr per Regex. JSON und
Runbericht enthalten dieselben Metadaten.
