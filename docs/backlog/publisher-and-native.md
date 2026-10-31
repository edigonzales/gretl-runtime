# Nicht gelieferte Erweiterungen und erhaltene Entscheidungen

**Status:** Backlog, keine Implementierung und kein neuer Arbeitsauftrag.
**Entscheidung:** Die generische Runtime und ihre vorhandenen 44 Operationen
bleiben unabhängig von Publisher, XtfToGpkg und Native Image.
Ein späterer Lieferumfang benötigt einen ausdrücklichen Auftrag und eigene Nachweise.
Die vollständigen ursprünglichen Analysen und Testfälle bleiben über die
[historischen Quellen](../history/README.md) auffindbar.

## Publisher als gewöhnliche Taskbibliothek

Ein erster begrenzter Port soll die normale OperationProvider-/Builder-/Handler-
SPI verwenden, ohne Publisher-CLI, spezielle Runtime-Exitcodes, Regionenregistry
oder automatische Wiederanläufe. Taskkonfiguration erfolgt beim Taskstart.
Ein Runtimepaket ohne Publisher muss weiterhin unabhängig laufen.

Der erhaltene erste Zielumfang ist eine lokale XTF-Datei mit explizitem lokalem
Modellverzeichnis, echter ilivalidator-Prüfung, optionaler Validierungskonfiguration,
lokalem Ziel und `userFormats=false`. Vorläufige Felder sind `dataFile`,
`targetDirectory`, `dataIdent`, `modelDirectory`, `validationConfig`, `userFormats`.
Kennungen erlauben keine Pfadtrenner oder Traversal; unsupported Optionen scheitern
vor vermeidbaren Wirkungen. DB-/ITF-Quellen, Regionen, SFTP/S3, SIMI, Benutzerformate
und automatische Historienbereinigung sind darin nicht angeboten.

Bestehendes Layout: `targetDirectory/dataIdent/aktuell/dataIdent.xtf.zip` enthält
`dataIdent.xtf`, `validation.log` und gegebenenfalls `validation.ini`.
Modelldateien und `publishdate.json` stehen separat unter `aktuell/meta/`.
Die bisherige Tages-/`hist/`-Semantik soll kontrolliert kompatibel bleiben.
Vorgesehene nicht-null Ausgänge sind `currentDirectory` und unveränderliche
`publishedFiles` mit absoluten aktuellen Ausgabe-, nicht historischen Arbeitsdateien.
Ein Regionenresultat wird ohne tatsächlich gelieferten Regionenmodus nicht erfunden.

Der alte Algorithmus belegt keine atomare Gesamtveröffentlichung: Die Promotion
verschiebt/löscht den alten Stand vor dem neuen. Der begrenzte Port erhält einen
Writer pro Ziel und unveränderte Eingabe während des Aufrufs; keine Crash-,
NFS-, Konkurrenz- oder universelle Wiederholbarkeitszusage. Frühe Validierungsfehler
und späte Teilwirkungen werden unterschieden. Normales bewusstes Catch bleibt
dasselbe wie bei SQL oder HTTP; kein Core-Sonderfall überschreibt erfolgreiche Actions.

Ein Adapter um `PublisherStepOld` ist möglich, muss aber Gradle-Properties,
Project-/BuildDir-/Logger-Kopplung ersetzen, aufrufbezogene Validator-/Ressourcen-
zustände verwenden und Archivabschlussfehler propagieren. Primärfehler bleiben
erhalten. Die Testzeitquelle wird injizierbar. Generationsstore, Manifest,
Zustellauftrag oder anderes Publikationslayout wären getrennte Fachentscheidungen.
Auditrisiken bleiben: Logger-/Enginezustand, konkurrierende Writer, Regionsmengen,
SIMI-Reihenfolge, SFTP-Teilwirkungen, CRS/Präzision/DBF-NULL/Encoding/Textkürzung,
Legacy-JTS-Abhängigkeiten und unterschiedliche validation.ini-Auslegung.

Die spätere TC-P01–TC-P09-Abnahme umfasst normalen Providerpfad, echte positive
XTF-Validierung, ZIP-/Modell-/Datumsinhalt, fehlende/ungültige Eingaben und Modelle,
Erhalt des bisherigen Stands vor Ersetzung, zwei kontrollierte Tages-/Historienläufe,
unsupported Optionen, Archiv-/Close-/Cleanupfehler, Resultatweitergabe und bewusstes
Catch. Sie ist weder erledigt noch gestrichen. Vollständiger Port und bessere
Speicher-/Regions-/Metadatengarantien bleiben separate Vorhaben.

## XtfToGpkg

Eine eigenständige Operation benötigt zuerst begrenzte Quell-/Modellauswahl,
Zielinhalt, Ergebnis-, Fehler- und Überschreibvertrag und einen qualifizierten
ili2gpkg-/Treiberbestand. Vorhandene Ili2gpkg-Operationen ersetzen diesen noch
nicht festgelegten Vertrag nicht. Die Erweiterung hängt nicht vom Publisher ab.

## Native Image

**Entscheidung:** Optionaler späterer Versuch für einen ausgewählten festen Job;
kein universeller dynamischer Native-Runner und kein Worker-first-Umbau.
Ein funktionierender JVM-/Job-JAR-Weg wird nicht von einem Native-Versuch blockiert.
Ein Nutzen muss am vollständigen Prozessstart und der echten fachlichen Arbeit
nachgewiesen werden; `--help` allein genügt nicht. Dynamisches Groovy, Reflection,
Services, Ressourcen, native JDBC-/Fachbibliotheken, Fehler-/Logging-/Parameter-
verträge und Zielplattform sind konkret zu qualifizieren. Keine Native-Lieferung
oder Portabilität wird aus dem heutigen JVM-Build abgeleitet. Ein späterer
Publisher wird wie jede andere tatsächlich verwendete Taskbibliothek qualifiziert.

Ein Native-Build darf keine Secrets, aktuellen Jobparameter oder offenen
Verbindungen durch Buildzeitinitialisierung einfrieren. Geänderter Code oder
eine zusätzliche Taskbibliothek benötigt ein neues Binary, neue Laufzeitdaten
nicht automatisch. Falls ein Fachpfad eine externe JVM startet, bleibt diese
Voraussetzung sichtbar. Statische globale Hilfe/Version, erste Task und gesamte
Jobdauer werden getrennt in frischen Prozessen gemessen. Ein kleiner nativer
Launcher oder ein Daemon wären eigene Entscheidungen. Ein späterer Exporter
müsste im kurzlebigen Prozess begrenzt flushen. Die alte Groovy-6-/GraalVM-
Versionsrecherche ist historisch; für einen konkreten Versuch wird die dann
gewählte JDK-25-Kombination neu geprüft. Ein universeller dynamischer Native-
Runner wird daraus nicht abgeleitet.
