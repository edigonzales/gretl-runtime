# Module und produktive Grenzen

Die Gradle-Projektabhängigkeiten bestimmen
den aktuellen Zuschnitt.

| Artefakt (`ch.so.agi.gretl`, Version `6.0.0-SNAPSHOT`) | Verantwortung | Erlaubte interne Produktionsabhängigkeiten |
| --- | --- | --- |
| gretl-language-contract | Reine Namens-, DSL- und Graphregeln | keine; ausschliesslich JDK |
| gretl-script-syntax | Gemeinsame reine Include-AST-Inspektion und Quellnamen | keine; JDK und Groovy |
| gretl-tooling-core | Sicheres Parsing, Katalogdaten, Workspacecaptures und statische Semantik | Language-Contract, Script-Syntax |
| gretl-tooling-catalog | Vertrauenswuerdiger Buildexport; separates reines Katalogdatenartefakt | API, Tooling-Core, aktuelle Taskbibliotheken |
| gretl-lsp | Eigenstaendiger Java25-stdio-Host fuer Queries, Graph und Editplanung | Tooling-Core, Language-Contract, Script-Syntax; ausschliesslich der isolierte Datenclassifier des Katalogs |
| gretl-api | Neutrale Java-SPI, Metadaten und Kontexte | keine; ausschliesslich JDK |
| gretl-i18n | Meldungskataloge | keine |
| gretl-runner | Planung, sequenzielle Ausführung, Lebensdauer | API, i18n, Language-Contract |
| gretl-groovy | Groovy-6-DSL und Kompilierkonfiguration | API, Runner, i18n, Language-Contract, Script-Syntax |
| gretl-logging | Logging, Telemetrie und Report | API, Runner, i18n |
| gretl-job-runtime | Jobquelle, Parameter, Konfiguration, Ausführung und Abschluss für Skript und JAR | API, Runner, Groovy, Logging, i18n |
| gretl-packager | Authoring: Descriptor, Kompilierung, Ressourcen und JAR | API, Groovy, Logging, i18n |
| gretl-cli | Authoring-Kommandos und Übersetzung von Startargumenten | Job-Runtime, Packager und deren Voraussetzungen |
| gretl-io-tasks | 13 HTTP-, Archiv-, XSLT-, FTP- und S3-Operationen | API, i18n |
| gretl-database-tasks | SqlExecutor, Db2Db, DuckDbSqlExecutor | API, i18n |
| gretl-interlis-tasks | 27 Format-, Validierungs-, ili2db- und AV-Operationen | API, i18n |
| gretl-ilivalidator | Schmaler INTERLIS-Validator mit drei Eingabefeldern | API, i18n |

Taskbibliotheken besitzen ihre Fachbibliotheken selbst. Sie verwenden weder
Runner noch Groovy, CLI, Packager oder Logging-Backend. Der Runner besitzt
keine Taskbibliothek. Die Job-Runtime besitzt weder direkt noch transitiv CLI
oder Packager. `GretlCli` übersetzt seine Jobkommandos in `JobOptions`;
`JobJarMain` verwendet `JobRuntime.executePackaged` unmittelbar. Beide verwenden
dieselbe Konfiguration, Planung, Cancellation, Shutdown- und Abschlusslogik.

Die vier organisationsspezifischen Funktionsbibliotheken liegen ausdrücklich
in der Standarddistribution. Das zusätzliche klassifizierte Ressourcenartefakt
`gretl-interlis-tasks:jar:standard-functions` enthält nur die bisherige
18er-Serviceauswahl, keinen weiteren OperationProvider und keine kopierten
Klassen. Das allgemeine Taskartefakt benötigt dieses Artefakt nicht.
Siehe [Validator-Erweiterungen](../guides/packaging.md#validatorfunktionen).

[Jobtest-Verträge](../development/job-artifact-tests.md) beschreiben Verbraucher und Nachweise.

Die CLI liefert die Standarddistribution. Der [Packager](../guides/packaging.md)
verwendet dieselbe Standardlaufzeit und isoliert den Jobcompiler vom Host.

Der statische Tooling-Core besitzt keine Produktionsabhaengigkeit auf Runner,
Groovyadapter, Taskprovider oder Exporter. Die gemeinsame `PlanSemantics` arbeitet
ausschliesslich mit logischen IDs und Kanten. Der Runner prueft weiterhin die
UUID-Identitaet seiner Referenzen. Der Buildexporter darf Metadaten lesen; der
Core bekommt ausschliesslich validierte Katalogdaten. Hosttests in Groovy und
Database vergleichen statische Aussagen mit der Runtime ueber Testdependencies.

Der Core liest explizit registrierte Quellen ueber eine begrenzte Readpolicy.
Buffer, Settings, SQLs, Katalog und Konfiguration werden unveraenderlich erfasst;
Generationen und Quellenvektoren schuetzen die Publikation vor veralteten
Ergebnissen. Der Dateiadapter verwendet auf der geprueften macOS-Primärplattform
sichere relative Directoryhandles ohne Symlinkfolgen. Nicht unterstuetzte
Dateisystemadapter liefern einen sichtbaren Policyzustand. Eine atomare
Transaktion mit unabhaengigen externen Dateischreibern wird nicht behauptet.

Die gemeinsame Includeerkennung in `gretl-script-syntax` inspiziert nur bereits
konvertierte Groovy-ASTs und Originalkoordinaten. Runtime und Tooling behalten
getrennte Parsehüllen; die Runtimefassade bildet weiterhin ihre bisherigen
Fehler ab. Der Toolingparser behält Platform-Classloader, deaktivierte
Transformationen, Recovery und Budgets. Das Syntaxmodul besitzt keine
Quellen-IO und keine Runtime- oder Tooling-Core-Abhängigkeit. Includeerkennung
allein verspricht noch keine vollständige Closure oder Mehrdateien-Editfähigkeit.
