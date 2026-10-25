# Module und produktive Grenzen

Stand: Umbauphase 2, Spezifikation 4.1. Die historischen M0–M7-Berichte
beschreiben ihre damaligen Commits. Für den aktuellen Zuschnitt gilt diese Seite.

| Artefakt (`ch.so.agi.gretl`, Version `0.1.0-SNAPSHOT`) | Verantwortung | Erlaubte interne Produktionsabhängigkeiten |
| --- | --- | --- |
| gretl-api | Neutrale Java-SPI, Metadaten und Kontexte | keine; ausschliesslich JDK |
| gretl-i18n | Meldungskataloge | keine |
| gretl-runner | Planung, sequenzielle Ausführung, Lebensdauer | API, i18n |
| gretl-groovy | Groovy-6-DSL und Kompilierkonfiguration | API, Runner, i18n |
| gretl-logging | Logging, Telemetrie und Report | API, Runner, i18n |
| gretl-job-runtime | Jobquelle, Parameter, Konfiguration, Ausführung und Abschluss für Skript und JAR | API, Runner, Groovy, Logging, i18n |
| gretl-packager | Authoring: Descriptor, Kompilierung, Ressourcen und JAR | API, Groovy, Logging, i18n |
| gretl-cli | Authoring-Kommandos und Übersetzung von Startargumenten | Job-Runtime, Packager und deren Voraussetzungen |
| gretl-io-tasks | 13 HTTP-, Archiv-, XSLT-, FTP- und S3-Operationen | API, i18n |
| gretl-database-tasks | SqlExecutor, Db2Db, DuckDbSqlExecutor | API, i18n |
| gretl-interlis-tasks | 27 Format-, Validierungs-, ili2db- und AV-Operationen | API, i18n |
| gretl-ilivalidator | Schmaler INTERLIS-Validator mit drei Eingabefeldern | API, i18n |
| gretl-dist | Vollständige Standarddistribution, Launcher, Katalog | Produktartefakte und ausdrücklich gewählte Standardfunktionen |

Taskbibliotheken besitzen ihre Fachbibliotheken selbst. Sie verwenden weder
Runner noch Groovy, CLI, Packager oder Logging-Backend. Der Runner besitzt
keine Taskbibliothek. Die Job-Runtime besitzt weder direkt noch transitiv CLI
oder Packager. `GretlCli` übersetzt seine Jobkommandos in `JobOptions`;
`JobJarMain` verwendet `JobRuntime.executePackaged` unmittelbar. Beide verwenden
dieselbe Konfiguration, Planung, Cancellation, Shutdown- und Abschlusslogik.

Die Enforcer-Ausführung `production-module-boundary` prüft die **aufgelösten**
transitiven Produktionsabhängigkeiten (compile/runtime/provided) von API,
Runner, vier Taskmodulen und Job-Runtime. Testdependencies bleiben ausserhalb
dieser Verbote. Maven weist Reactorzyklen vor der Ausführung zurück. Die
Abhängigkeitsbäume und unabhängige Prüfung ergänzen diese wenigen Regeln.

Die vier organisationsspezifischen Funktionsbibliotheken liegen ausdrücklich
in der Standarddistribution. Das zusätzliche klassifizierte Ressourcenartefakt
`gretl-interlis-tasks:jar:standard-functions` enthält nur die bisherige
18er-Serviceauswahl, keinen weiteren OperationProvider und keine kopierten
Klassen. Das allgemeine Taskartefakt benötigt dieses Artefakt nicht.
Siehe [Validator-Erweiterungen](validator-functions.md).

[Java-/Maven-Migration](../migration/phase-2-java-maven.md) und
[Jobtest-Verträge](job-artifact-tests.md) beschreiben Verbraucher und Nachweise.
