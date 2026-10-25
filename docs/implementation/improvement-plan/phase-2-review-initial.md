# Unabhängiger Prüfbericht – Phase 2, Kandidat 1

Phase: 2 – Modulgrenzen bereinigen  
Prüfdatum: 25. Oktober 2026  
Implementierungsagent: Agent A (`/root`)  
Unabhängiger Prüfagent: Agent B (`/root/phase2_review`), keine Implementierungs- oder Testautorschaft dieser Phase  
Ausgangspunkt: `b0499d3`  
Vorheriger abgenommener Produktstand: `52675c526a0edad52d7005c1d7e874fb7d3ca276` (Phase 1: unabhängiges PASS)  
Geprüfter unveränderlicher Produktkandidat: `7db4ab8b97d1aa8adf60bc9b137adf24c65e2a02`  
Implementierungsbericht: nachfolgender reiner Dokumentationscommit `28d107b3f8a92f557b4980792d65a851584185ab`  
Eigene Nachweise: `/tmp/gretl-phase2-review-evidence/`

**Urteil: FAIL.** Die Modultrennung, der produktive Abhängigkeitsgraph und die breite JAR-Suite sind belastbar nachgewiesen. Die IMAGE-Vorbereitung verletzt jedoch P2-11: Sie verwechselt gültige FTP-Remoteverzeichnisse und opaque Korrelationswerte mit Hostpfaden. Zusätzlich scheitern beide normalen Rootprüfungen in sauberen, gewöhnlich unter `/tmp` angelegten Worktrees an einer falschen GID-Ermittlung im nicht startenden Vertragstest (P2-09/P2-11). Beide Befunde sind unten unabhängig reproduziert. Nach ausschliesslicher Anpassung der Dateigruppe meiner Prüfumgebung bestehen beide vollständigen Rootwege auf dem unveränderten Produktcommit mit 456 beziehungsweise 510 Tests. Das beseitigt den Kandidatenfehler nicht.

Phase 3 ist durch dieses Urteil nicht freigegeben. Ein von A während dieser Prüfung vorbereiteter Fix ist kein Bestandteil dieses Prüfstands und wird hier weder berücksichtigt noch freigegeben.

## Unabhängigkeit, Quellen und Prüfstand

Zwei eigene detached Worktrees wurden auf exakt dem Kandidaten erstellt:

- `/tmp/gretl-phase2-review-standard`
- `/tmp/gretl-phase2-review-profiles`

Beide waren vor den Builds und sind nach Abschluss ohne Git-Änderung. Kein Produktcode, keine Testdatei und kein POM wurden dort geändert. Zusätzliche Programme liegen ausschliesslich im externen Nachweisbereich. Die beiden Rootprüfwege liefen mit getrennten Targets und separaten Prozessen; ein `clean` des einen Laufs konnte den anderen nicht löschen. Reports, Coverage, die vollständigen Distributionen samt Archiven und kanonische Jobnachweise wurden durch Hardlinks nach `standard/` beziehungsweise `profiles/` gesichert. Diese Verknüpfung bewahrt die tatsächlich geprüften Bytes auch bei späterem Entfernen eines Worktrees.

Gelesen und selbst abgeglichen: beide AGENTS-Dateien, vollständiger Umsetzungsplan, Benutzerauftrag, relevante Verträge der Spezifikation 4.1 (insbesondere RUN/OPS/CORE/LOG, DSL/SPI, Maven-Leitlinie und Abnahme), Phase-1-Prüfbericht, Phase-2-Implementierungsbericht, Migration, Modulübersicht, Funktionsmatrix und Jobtestvertrag. Das vollständige Phasendiff wurde nach Verantwortung geprüft. `diff-numstat.txt`, `pom.diff`, `main-diff-classification.json` und `main-normalized-diff.patch` erhalten die Zuordnung. Von 93 betroffenen Main-Java-Dateien im Namensdiff sind 57 nach explizit dokumentiertem Paket-/Typumzug inhaltlich gleich. Die übrigen Änderungen umfassen Providerzuschnitt, Runtimeextraktion, strikte Validatorfunktionsregistrierung und die ausgewiesenen kleinen ili2db-Vertragskorrekturen. Neue/übernommene Fixturebytes wurden separat gehasht. Bericht A und dessen Testzahlen wurden nicht als eigene Nachweise übernommen.

## Eigene Umgebung und vollständige Befehle

Temurin OpenJDK 25.0.2+10-LTS; Maven Wrapper 3.10.0; Groovy 6.0.0; JaCoCo 0.8.14; macOS 27.0.1 ARM64; UTF-8; Hostlocale en_US, Europe/Zurich. Jobprozesse setzen deutsche Sprache und UTC. Docker Desktop 4.93.0 / Engine 29.8.1 / Linux ARM64. Das festgelegte PostGIS-AMD64-Image läuft emuliert. `environment.json` enthält selbst abgefragte Versionen, effektive UID/GID, Worktreeidentität und sauberen Status.

In jedem eigenen Worktree wurde der zugehörige exakte Rootbefehl ausgeführt:

```sh
./mvnw -B clean verify
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

| Lauf | Ergebnis aus XML | Protokoll |
| --- | --- | --- |
| Standard, unveränderte ursprüngliche Dateigruppe 501:0 | Exit 1; 401 Tests, 0 Failures, 6 Errors, 0 Skips; Abbruch vor den fachlichen Failsafe-Jobs | `standard-initial/root.log`, dortige XML |
| Vollprofil, unveränderte ursprüngliche Dateigruppe 501:0 | Exit 1; 401 Tests, 0 Failures, 6 Errors, 0 Skips; derselbe Konstruktorfehler | `profiles-initial/root.log`, dortige XML |
| Standard nach `chgrp -R staff` ausschliesslich auf meinen Worktrees | BUILD SUCCESS; 456 Tests, 0 Failures, 0 Errors, 0 Skips; 12:31 min | `standard.log`, `standard/` |
| Vollprofil nach derselben Dateigruppenanpassung | BUILD SUCCESS; 510 Tests, 0 Failures, 0 Errors, 0 Skips; 16:55 min | `profiles.log`, `profiles/` |

Kein Skip-Flag, kein Assumption-Fallback, kein ausgeschalteter Pflichtfall. Die erste Fehlerserie wird nicht als erfolgreicher Lauf umgedeutet. Die Gruppenanpassung verändert keine Kandidatenbytes; sie ermöglichte die vollständige unabhängige Prüfung der übrigen Kriterien.

Zusätzlich selbst ausgeführt: Maven `dependency:tree` mit `-Dscope=runtime -DoutputType=json`, API-`jdeps`, die erhaltenen Python-Audits für Originale, Diff, Distribution, Regressionen, Artefakte und Ausgaben sowie die unten beschriebenen Java-/Prozessgegenproben. Exakter Dependencybefehl und Ausgabe: `dependency-tree.log`; der Aufruf verwendet `-pl gretl-api,gretl-runner,gretl-io-tasks,gretl-database-tasks,gretl-interlis-tasks,gretl-ilivalidator,gretl-job-runtime -am` und `-DoutputFile=target/review-runtime-tree.json`.

## Pflichtfindings

### F1 – IMAGE deutet fachliche Remote-/Identitätstexte als Hostpfade um

**Betroffen:** P2-11. Kandidatenstellen: `ImageExecutionAdapter.java:122`, `:131–132`, `:142–166`; kanonischer FTP-Aufrufer `ArtifactServicesIT.java:16`.

`translate()` behandelt jeden String mit führendem `/` als lokalen Hostpfad und verlangt eine Mountzuordnung. `prepare()` wendet dies pauschal auf Parameter, Konfigurationsscalars und nicht geheime Environmentwerte an. Der kanonische FTP-Test übergibt aber `remoteDir=/home/gretl/<lease>`: Das ist ein Pfad auf dem FTP-Server. Bereits die Adaptervorbereitung wirft `Unmapped IMAGE path`, bevor ein Containerstart möglich wäre. Dieselbe Ursache zerstört den schon in Phase 1 zugesagten opaque-Korrelationsvertrag: `/ci/review/42` ist als `runtime.correlationId` beziehungsweise `GRETL_CORRELATION_ID` gültig, wird im IMAGE-Adapter jedoch ebenfalls abgewiesen.

Eigene nicht startende Reproduktion, unveränderte Adapterquellen gegen die eigene Kandidatendistribution kompiliert:

- `image-counterexample/ImageRemotePathProbe.java`, `1.stdout.log`: exakt `Unmapped IMAGE path: /home/gretl/review lease`; nur drei Fake-Engine-`inspect`-Aufrufe, keine Start-/Controlaktion. Positive Kontrollen: lokaler Output wird zu `/outputs/a b.txt`, FTP-Endpunkt zu `ftp:21` übersetzt.
- `image-counterexample/ImageOpaqueTextProbe.java`, `opaque.stdout.log`: Ablehnung des Slash-Korrelationswerts aus Config und ENV, ebenfalls ohne Start.
- `common-runtime/opaque-positive.json`: echte SCRIPT- und separate `java -jar`-Gegenkontrolle akzeptieren dieselben Slash-IDs aus beiden Quellen, mit unverändertem fachlichem Ergebnis und korrektem strukturiertem `correlation_id`.

**Notwendige Korrektur:** Lokale Hostpfade, fachliche Remoteverzeichnisse und opaque Textwerte eindeutig unterscheiden; Mount-/Endpointübersetzung nur mit der dazugehörigen Semantik anwenden. Nicht startende Verträge müssen den kanonischen FTP-Request und die gültigen Config-/ENV-Korrelationsfälle sowie weiterhin explizit ungemappte lokale Pfade prüfen. Kein echter GRETL-Image-Lauf wird verlangt.

### F2 – Nicht startende IMAGE-Tests leiten den Nutzer aus der Checkout-Dateigruppe ab

**Betroffen:** P2-09 und P2-11. Kandidatenstelle: `ImageExecutionAdapterTest.java:16–20`.

`currentUser()` liest `unix:uid`/`unix:gid` von `Path.of(".")`. Eine Verzeichnisgruppe ist keine effektive Prozessgruppe. Die neu erzeugten Worktrees erben unter `/tmp` hier normal `501:0` (wheel), während der Testprozess als `501:20` (staff) läuft. `Settings` verwirft die daraus erzeugte Zeichenfolge `501:0` als angeblich unzulässigen Nutzer. Alle sechs Adaptertestinstanzen scheitern im Konstruktor; beide exakten Rootwege brechen ab, obwohl weder Docker noch ein Image für diese Vertragstests benötigt werden.

Eigene Belege:

- Beide ursprünglichen Rootlogs und `TEST-ch.so.agi.gretl.it.ImageExecutionAdapterTest.xml` in `standard-initial/` und `profiles-initial/`: sechs identische Konstruktorfehler.
- `image-counterexample/ImageGroupProbe.java`, `group.stdout.log`: effektiver JDK-UnixSystem-Nutzer `501:20`, gewöhnliches frisches `/tmp`-Verzeichnis `501:0`, reproduzierter Settingsfehler.
- Nach alleiniger Gruppenanpassung der Prüfumgebung laufen alle bestehenden Tests unverändert erfolgreich. Das isoliert die Ursache auf die Ermittlung im Vertragstest.

**Notwendige Korrektur:** Den tatsächlichen Ausführungsnutzer beziehungsweise eine korrekt vorbereitete Testidentität ermitteln, ohne die CWD-Dateigruppe als Prozessgruppe zu interpretieren. Der normale Docker-freie Rootbuild darf nicht von zufälliger Checkout-Gruppenvererbung abhängen. Ein dauerhafter Kandidatenfix und erneute unabhängige Prüfung sind erforderlich.

## Bewertung jedes Phase-2-Kriteriums

| ID | Ergebnis | Eigener Nachweis / Bewertung |
| --- | --- | --- |
| P2-01 | erfüllt | Tatsächliche Distribution enthält vier Task-JARs und vier eindeutige SPI-Provider; eingefrorener 44er-Metadatenvertrag besteht in beiden eigenen Builds, Aufteilung 13 / 3 / 27 / 1. IDs, Symbole, Eingabefelder und Resultatausgänge bleiben erhalten; konkrete Java-Typnamen folgen der dokumentierten Migration. `distribution-audit.json`, OperationMetadataTest-XML, `java-migration-audit.json`. |
| P2-02 | erfüllt | Alle vier Provider genau einmal in den wirklichen Distributions-JARs; das standard-functions-JAR ist ressourcenrein und hat keinen OperationProvider. Es enthält genau die bisherigen 18 IOX-Funktionsklassen. 114 reale Distributionsprüfsummen stimmen; Hilfe, Meldungen und fachliche Jobausgaben funktionieren. `distribution-audit.json`, DistributionIT, Katalogtests, reale JAR-Suite. |
| P2-03 | erfüllt | Selbst aufgelöste Produktionsbäume und API-`jdeps`: API nur java.base; Runner keine externen Dependencies und intern nur API/i18n; Taskmodule intern nur API/i18n, keine transitive Runner-/Groovy-/CLI-/Packager-/Loggingbackendabhängigkeit. Reactor und Graph azyklisch. `production-dependency-trees.json`, `production-dependency-audit.json`, `api-jdeps.txt`, beide Enforcerläufe. Testdependencies wurden getrennt behandelt. |
| P2-04 | erfüllt | Job-Runtime verwendet API/Runner/Groovy/Logging/i18n; weder direkt noch transitiv CLI/Packager. GretlCli übersetzt in JobOptions/executeScript, JobJarMain ruft executePackaged desselben JobRuntime auf. Die wesentlichen früheren jobCommand-/run-/Abschlussmethoden sind nach ausgewiesenen Typumbenennungen inhaltlich identisch. `runtime-move-audit.json`, eigene Runtime-Prozesse und Regressionen. |
| P2-05 | erfüllt | Der eigene neu aufgelöste Runtimegraph des allgemeinen INTERLIS-Taskartefakts enthält keine Organisationsfunktionen und keine Runtime/CLI/Groovy. Alle vier Formatproben bestehen im Rootlauf in eigenständigen Prozessen; zusätzlich eigene, unabhängig aus dem aufgelösten Tree zusammengesetzte CSV-Probe mit ClassNotFound-Nachweisen für Organisationsklasse, Runner, Groovy und CLI. `minimal-validator/`, ValidatorClasspathLocalJobIT-XML. Kein Entfernen eines einzelnen JARs aus einer vollen Distribution. |
| P2-06 | erfüllt | Echte HTTP-Zusatzfunktion wird aus den gebauten JARs in CsvValidator und schmalem IliValidator ausgeführt, durch lokalen Server-Aufrufzähler belegt; HTTP-404 ergibt Fachfehler, fehlende benötigte Funktion, defekte ausdrückliche Auswahl und Namenskollision geben passende Diagnosen. Zusätzlich eigene minimal-CP-Gegenproben: falscher Klassentyp, defekter Serviceeintrag, zwei unterschiedliche Klassen mit gleichem qualifiziertem Funktionsnamen. `minimal-validator/`, FunctionLocalJobIT und tatsächliche additional-function-Jobreports. |
| P2-07 | erfüllt | Alle akzeptierten 413/447 Phase-1-Testfälle sind nach tatsächlichen Klassen-/Providerumzügen weiter vorhanden und grün. Eigenes SCRIPT-/JAR-Prozesspaar belegt zusätzlich Parameterpriorität/Leere, Long, CWD- und Ressourcenbasis, Cleanup, Secretredaction, Fehler10, Korrelation und getrennte IDs. Cancellation/SIGTERM/Fehlerpriorität und echte DB-Rollbackprüfung bestehen. `regression-audit.json`, `common-runtime/`, CancellationLocalJobIT, DatabaseArtifactIT. F1 betrifft die neu vorbereitete IMAGE-Grenze und ändert diesen JAR-/Skriptbefund nicht. |
| P2-08 | erfüllt | Externe FileSize-Extension wird als eigener Reactorbaustein gegen das gewöhnliche veröffentlichbare API-JAR gebaut und als Extension von Distribution/Job-JAR gefunden, mit Hilfe und realem Ergebnis. 103 frühere öffentliche Einstiegsklassen am akzeptierten Phase-1-SHA selbst gegengeprüft; neue Klassen existieren, zwei alte Provider sind ausdrücklich ersetzt. Migration/aktuelle Beispiele/Modulübersicht stimmen mit dem neuen Zuschnitt. `java-migration-audit.json`, DistributionIT, Beispielartefakt. |
| P2-09 | verletzt | Beide exakten Rootwege scheitern zunächst wegen F2 in gewöhnlichen sauberen Worktrees. Nach reiner Gruppenanpassung bestehen 456/510 Tests und alle graphischen/Artefaktnachweise; eine unabhängige Freigabe bleibt wegen F1/F2 ausgeschlossen. Kein fehlender Dienst und kein BLOCKED. |
| P2-10 | erfüllt | Alle 87 Originalfälle zugeordnet: 81 im Umfang einschliesslich eines historisch inaktiven FTP-Falls, sechs konkret begründet ausserhalb. 205 Assertionstexte selbst gegen Originalmethoden geprüft; Zielmethoden vorhanden; 220 Fixturehashes stimmen, 129 Dateien bytegleich zum Original. Alle 44 Operationen haben einen tatsächlich erfolgreichen fachlichen JAR-Nachweis im eigenen Vollprofil. Typen/Geometrien/SRID, Datensätze/Baskets, Exporte, Formatdiagnosen, Original-S3-Inhalte und reale FTP-Transfers sind erhalten. `original-audit.json`, `output-audit.json`, `artifact-audit.json`, eigene XML/Logs. |
| P2-11 | verletzt | Ein gemeinsamer Szenario-/Assertionbestand, ausführbarer Adapter, explizite Image-ID, Mounts/Rechte/Netzwerk/Secrets/Timeout/Stop/Reap und austauschbare Startgrenze sind implementiert. Die existierenden sechs nicht startenden Tests bestehen nach Gruppenanpassung. Sie übersehen aber F1; der kanonische FTP-Request und gültige Identitätstexte erreichen IMAGE nicht. F2 verhindert zudem die gewöhnliche Ausführung dieser Tests. Kein echter GRETL-Containerstart wurde durchgeführt oder für PASS gefordert. |

## Originalorakel und Datenprüfung

Die Originalbasis `sogis/gretl@a9222dc0ffd08f8f76570e998101db041822df71` wurde aus dem vorhandenen unveränderten Archiv selbst gelesen. Der unabhängige Parser maskiert Strings/Kommentare für Strukturgrenzen und vergleicht alle 205 inventarisierten Assertions mit den Originalmethoden; Sourcehashes und alle Zielmethoden wurden ebenfalls geprüft. Ergebnis: 87 vollständige Zuordnungen, keine unerklärte Auslassung. Die sechs Ausnahmen sind DatabaseDocumentExport, Oracle-Treiber, zwei Rasterfälle und zwei Publisherfälle; sie entsprechen dem expliziten Umfang. Der historisch auskommentierte FTP-Fall zählt nicht als historisch aktiver Erfolg, besitzt jetzt aber echte lokale JAR-Fälle.

Die tatsächlichen migrierten Assertions wurden mit den Originalerwartungen verglichen, darunter CSV-Zahlen/Datum/Timestamp/Boolean, exakte CSV-Exportzeilen, JSON-Objekt/Array, Point-Koordinaten und 2056, WKB, DBF-Werte, 109/127 SHP-Records und LV95, Excel-Header/308 Datenzeilen, AV-Inhalt/FileSets, INTERLIS-Modelltext und Daten, Datasetmengen/-slices, NGK-Objektkennung, sämtliche Validatorfehler, HTTP-Auth/Multipart/GZIP-Bytes, S3-Schlüssel/Inhalte/Kopie/Löschung und FTP-Login/Transfer. Die dokumentierten Darstellungsäquivalenzen sind fachlich nachvollziehbar: natives DBF-Boolean T statt alter GeoTools-Stringprojektion, native SHP-Objektzählung o1/o2 statt o2/o3, deutsche Dezimalkommas im JSON-Diagnosetext, zwei schmale Validator-Tasks statt neuer FileSet-Option sowie aktuelle DuckDB-Schemabasis. Der alte S3-Negativtest benutzte seine angeblich falschen Credentials nicht; der konkrete NoSuchBucket-Nachweis ist kein behaupteter IAM-Test.

Zusätzliche eigene unabhängige Dateileser prüfen sieben erhaltene Ergebnisse: GPKG-Tabellen-/Geometrietyp und SRID 2056/21781 samt exakten beiden Boflächenwerten, XLSX-Zeilen über ZIP/XML, native DBF-Recordzahlen, SHP-Header/-Dateilänge und PRJ sowie bytegleiches XML. Falsche erwartete Zeilen und ein fehlendes read-only geöffnetes GPKG werden zurückgewiesen; es wird keine fehlende Datei neu erzeugt. `output-audit.json`.

Der reale AV-Negativreport enthält zusätzlich zur geprüften AV_CONVERT-Kategorie die konkrete IoxException `could not parse file: fubar.itf`. Die fachlichen Validierungs-/FTP-/S3-Negativläufe enthalten ihre konkreten Diagnosen. Eine eigene beschädigte JAR-Probe endet dagegen mit OS 1 ohne Runbericht und erfüllt das fachliche Negativprädikat nicht: `common-runtime/broken-launcher/evidence.json`.

## Übernommene Phase-1-Regressionen

`regression-audit.json` vergleicht die tatsächlichen XML-Fallnamen des final akzeptierten Phase-1-Prüflaufs unter `/tmp/gretl-phase1-revision-2-review-evidence/` mit den eigenen jetzigen Ergebnissen. Keine verschwundene Methode. Dokumentierte Testklassenumzüge und genau zwei umbenannte Providerkatalogmethoden berücksichtigen den geänderten Zuschnitt; die alten SQL-Metadatenassertionen sind im separaten Datenbankprovider-Test erhalten.

| Phase-1-Familie | Eigener aktueller Nachweis |
| --- | --- |
| P1-01, P1-11 | eingefrorenes 44er-Inventar, Kompatibilitätsgrenzen, Original-/Fixtureaudit |
| P1-02, P1-03 | acht Path-/String-Validatorvarianten, fehlende/falsche/unerlaubte tableName-Fälle, früher Fehler für ungültiges Repository |
| P1-04 | echte GPKG-JAR-Jobs für 2056/21781 und veröffentlichte Hilferezeptur; zusätzliche eigene SQLite-Abfragen |
| P1-05 | echter Db2Db-Abbruch: CANCELLED/20, Folgetask NOT_RUN, ursprüngliche Zielzeilen erhalten, keine offene idle-in-transaction-Session |
| P1-06 | acht kooperative/SIGTERM/Timeout-/Cleanup-Fehlerprioritätsfälle für Skript und JAR; OS143 wird von GRETL20 beziehungsweise führendem FAILED10 getrennt |
| P1-07 | bestehende Korrelations-/Redaktionsprüfungen plus acht eigene reale Starts mit Parameter-/Pfad-/Secret-/Fehler-/Slash-ID-Gegenkontrolle |
| P1-08 | öffentliche DSL, tatsächlicher Hilfecode, positive DuckDB-Föderation und konkrete Ablehnung des nicht zugesagten PostGIS-Geometrietyps |
| P1-09 | Line-/Branch-Berichte aller 13 produktiven Gruppen, gleiche offen erklärte Subprozessgrenze |
| P1-10, P1-12 | erfolgreiche vollständige Rootregressionen nach isolierter Umweltanpassung, kanonische echte JAR-Prozesse und unabhängige Ergebnisleser; das frühere Phase-1-PASS bleibt der akzeptierte Ausgang |

## Ausgeführte Tests

Die folgenden Werte stammen ausschliesslich aus eigenen Surefire-/Failsafe-XMLs; Reihenfolge und Anzahl sind keine Coveragequote.

| Modul | Standard | Vollprofil |
| --- | ---: | ---: |
| gretl-api | 17 | 17 |
| gretl-cli | 52 | 52 |
| gretl-database-tasks | 21 | 21 |
| gretl-dist | 11 | 11 |
| gretl-geotools-probe | 0 | 4 |
| gretl-groovy | 49 | 49 |
| gretl-i18n | 4 | 4 |
| gretl-ilivalidator | 3 | 3 |
| gretl-integration-tests | 62 | 112 |
| gretl-interlis-tasks | 31 | 31 |
| gretl-io-tasks | 54 | 54 |
| gretl-job-runtime | 10 | 10 |
| gretl-logging | 18 | 18 |
| gretl-packager | 8 | 8 |
| gretl-runner | 116 | 116 |
| **Gesamt** | **456** | **510** |

Beide vollständigen letzten Läufe: **0 Failures, 0 Errors, 0 Skips**. Die erste Fehlerserie mit jeweils sechs Errors bleibt separat erhalten.

## Coverage-Messumfang und Grenzen

JaCoCo 0.8.14 erzeugt pro produktivem Modul und aggregiert Line-/Branch-XML/HTML. Erfasst werden die instrumentierten Maven-Test-JVMs und darin geladenen Produktklassen. Separate Distributionslauncher-, Packager- und `java -jar`-Jobprozesse sind bewusst uninstrumentiert. Die eigenen Zusatzprozesse werden ebenfalls nicht in die Coveragewerte eingerechnet. Die verschobenen In-Process-Konfigurations-/CLI-Tests erklären die jetzt höhere Messung der gemeinsamen Job-Runtime; die fachlichen Subprozesse werden dadurch nicht nachträglich zu gemessenen Ausführungen. `coverage-summary.json` enthält beide vollständigen Zählerbestände, `standard/` und `profiles/` die originalen Berichte.

Vollprofil, covered / total:

| Produktives Modul | Lines | Branches |
| --- | ---: | ---: |
| gretl-api | 110 / 114 | 23 / 26 |
| gretl-runner | 1796 / 2078 | 754 / 1061 |
| gretl-groovy | 498 / 733 | 148 / 238 |
| gretl-i18n | 24 / 31 | 13 / 20 |
| gretl-logging | 923 / 1103 | 384 / 590 |
| gretl-packager | 508 / 698 | 204 / 310 |
| gretl-job-runtime | 795 / 1118 | 286 / 483 |
| gretl-dist | 20 / 26 | 5 / 8 |
| gretl-cli | 338 / 458 | 104 / 174 |
| gretl-database-tasks | 1208 / 1516 | 418 / 714 |
| gretl-io-tasks | 1947 / 2511 | 602 / 1014 |
| gretl-ilivalidator | 130 / 207 | 28 / 68 |
| gretl-interlis-tasks | 2854 / 3699 | 1112 / 1859 |

## Tatsächliche Artefakte und alle 44 positiven Operationen

Eigene kanonische Starts: Standard **64 JAR + 4 SCRIPT**, Vollprofil **141 JAR + 4 SCRIPT**. Keine IMAGE-Ausführung. JARs stammen vom regulären Packager der jeweils eigenen gebauten Kandidatendistribution. Die aufgezeichneten Jobbefehle verwenden `java -jar`, keinen Testclasspath. Jeder Lauf besitzt neue Quell-/Arbeits-/Ausgabeverzeichnisse und einen eigenen Prozess. Die testseitige Hardlinkwiederverwendung gilt nur für bereits regulär gebaute identische Fixturepakete innerhalb derselben Test-JVM; sie ist als package-reuse dokumentiert. Erwartete Dateien werden nicht aus beschreibbaren Ausgaben übernommen.

Jede tatsächliche JAR-Prüfsumme wurde mit ihren Bytes abgeglichen; bei mehreren Hardlinks wurde derselbe unveränderliche Inode einmal gehasht und jeder einzelne Pfad seinem Nachweis zugeordnet. Standard enthält zehn, Vollprofil vierzehn unterschiedliche JAR-Identitäten. In jedem unterschiedlichen JAR stimmen **alle 487 Produktklassen aus zwölf Modulen** bytegleich mit `target/classes` des zugehörigen eigenen Kandidatenbuilds überein. Das Main-Class-Manifest und alle 93 tatsächlichen Eingabe-JAR-Hashes des eingebetteten Dependencyinventars passen zur eigenen Distribution. Kein Artefakt stammt aus dem Hauptcheckout oder einer IDE. Alle Reports, Terminalereignisse, Quellenhashes und Exitcode-/Abbruchklassifikationen wurden automatisiert abgeglichen; `artifact-audit.json` hat keine Abweichung.

Die Phase-2-Pakete enthalten noch den Vollkatalog einschliesslich CLI-/Packagerklassen. Die für Phase 3 beauftragte gezielte Auswahl und Entfernung entbehrlicher Paketierungsanteile wird hier ausdrücklich noch nicht als erreicht behauptet.

Die Tabelle nennt je Operation eine tatsächlich erfolgreiche JAR-Ausführung aus dem eigenen Vollprofil. Der vollständige Audit enthält sämtliche Ausführungen, Argumentlisten, Serviceendpunkte ohne Secrets, Status, Invocation-IDs, Dauern und Hashes. Die Verzeichnisse liegen unter `profiles/job-evidence/`; Report jeweils `reports/report.json`.

| Operation / ID | Erfolgreiche Invocations | Repräsentativer Lauf | Tatsächlicher JAR-SHA256 |
| --- | ---: | --- | --- |
| Av2ch / `gretl.av2ch` | 2 | `av2ch-set-72721a86-dec6-462b-8fae-7b2e0ce9d624` | `befd8f130f5652c4c2a88764654d98c480a2ea8a929887aa765535311e5d2cd4` |
| Av2geobau / `gretl.av2geobau` | 2 | `av2geobau-set-809a6ab9-1e2d-405a-bf2a-776ba4e78911` | `befd8f130f5652c4c2a88764654d98c480a2ea8a929887aa765535311e5d2cd4` |
| Csv2Excel / `gretl.csv2excel` | 2 | `excel-empty-15b56e9e-55a3-4c64-80f6-0f252f3a46a4` | `befd8f130f5652c4c2a88764654d98c480a2ea8a929887aa765535311e5d2cd4` |
| CsvExport / `gretl.csv-export` | 1 | `format-csv-export-a30afd6d-941b-4114-b87a-ec21e498b113` | `f5a0b5411d7e006de86f38bca7eb3c9354cc9a5ab4fa610f623ccef8027166a0` |
| CsvImport / `gretl.csv-import` | 2 | `format-csv-import-6bb40974-9e37-46c9-b1db-b925d3d0c793` | `f5a0b5411d7e006de86f38bca7eb3c9354cc9a5ab4fa610f623ccef8027166a0` |
| CsvValidator / `gretl.csv-validator` | 3 | `additional-function-18c2a7c6-9e8a-4fc4-a274-c8f03d5389b5` | `4580e284195ddb914c9203b9fe6bd470e83d6c6f09e5efd65ff15fdd59e30076` |
| Curl / `gretl.curl` | 7 | `io-curl-binary-4c375692-d62f-40e4-9b10-4333b6c28a66` | `3d809de1f98ead1d4f9b8b8c06c9f1a0ff6709f7b7f1eb230d5d7bb4b40ed5fd` |
| Db2Db / `gretl.db2db` | 5 | `database-chain-afbcff18-5896-4669-b470-f5330a10fc42` | `33afc7798b1e1f274e31e63a4ad766acbece715b8f02abb2f0571cf03f2dd19e` |
| DuckDbSqlExecutor / `gretl.duckdb-sql-executor` | 2 | `original-duckdb-a5076fd4-d84c-4b88-9a56-0fef056c8c51` | `b850b5518de2621a6af3d386d804851c61bb4e6fad42b3e21d9429bdb2b73183` |
| Extract / `gretl.extract` | 2 | `io-extract-file-4d9677b4-da85-4e20-830d-13debe24978c` | `3d809de1f98ead1d4f9b8b8c06c9f1a0ff6709f7b7f1eb230d5d7bb4b40ed5fd` |
| FtpDelete / `gretl.ftp-delete` | 2 | `ftp-delete-a311a3fe-10a4-4d98-b6d9-26c7502463a8` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| FtpDownload / `gretl.ftp-download` | 1 | `ftp-download-119bec7b-564d-4178-814d-8dbc97935b01` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| FtpList / `gretl.ftp-list` | 1 | `ftp-list-result-a2ffd4e6-123c-4f39-ac57-a7e6a67059ca` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| FtpUpload / `gretl.ftp-upload` | 1 | `ftp-upload-a4b07e05-44d9-4d07-8768-b1cbb99cdfd0` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| Gpkg2Dxf / `gretl.gpkg2dxf` | 1 | `gpkg2dxf-single-ef54044b-251d-475a-9e44-a7901bba92c8` | `befd8f130f5652c4c2a88764654d98c480a2ea8a929887aa765535311e5d2cd4` |
| Gpkg2Shp / `gretl.gpkg2shp` | 1 | `gpkg2shp-single-2e7d12a7-8de1-4986-8b64-2b59a472d334` | `befd8f130f5652c4c2a88764654d98c480a2ea8a929887aa765535311e5d2cd4` |
| GpkgExport / `gretl.gpkg-export` | 2 | `format-gpkg-export-c5929f55-8ee2-4f45-8875-6041f4535903` | `f5a0b5411d7e006de86f38bca7eb3c9354cc9a5ab4fa610f623ccef8027166a0` |
| GpkgImport / `gretl.gpkg-import` | 1 | `format-gpkg-import-169f5cb6-a048-4cd9-88ef-1eba3112d235` | `f5a0b5411d7e006de86f38bca7eb3c9354cc9a5ab4fa610f623ccef8027166a0` |
| GpkgValidator / `gretl.gpkg-validator` | 2 | `valid-gpkg-path-efc3963d-6c29-426d-a65f-d307f8c3582b` | `ab4dd94615f17528b110c85af5b9bb2b210fd690a865a62560b39dce36ad4e51` |
| Gzip / `gretl.gzip` | 1 | `original-gzip-86128288-d95b-4804-8923-57682521408b` | `a2a4760e1954ca92f23a242ed93c83965b435e75b927a7912e410457693b67a1` |
| HttpDownload / `gretl.http-download` | 1 | `io-download-file-4320fe95-49bc-4201-a1a0-aa2ad080df37` | `3d809de1f98ead1d4f9b8b8c06c9f1a0ff6709f7b7f1eb230d5d7bb4b40ed5fd` |
| Ili2duckdbExport / `gretl.ili2duckdb-export` | 1 | `ili-duck-export-single-12377cac-1e8f-45dd-bda2-e69e9e1063e5` | `d4e929d3aea34f7d9aab8c0ce8460cb2d955be231eee711e11447e96efad84fc` |
| Ili2duckdbImport / `gretl.ili2duckdb-import` | 1 | `ili-duck-export-single-12377cac-1e8f-45dd-bda2-e69e9e1063e5` | `d4e929d3aea34f7d9aab8c0ce8460cb2d955be231eee711e11447e96efad84fc` |
| Ili2duckdbImportSchema / `gretl.ili2duckdb-import-schema` | 1 | `ili-duck-export-single-12377cac-1e8f-45dd-bda2-e69e9e1063e5` | `d4e929d3aea34f7d9aab8c0ce8460cb2d955be231eee711e11447e96efad84fc` |
| Ili2gpkgImport / `gretl.ili2gpkg-import` | 5 | `gpkg-help-example-a785a36e-4b1b-4614-b453-aad3302d3a82` | `a3e2ca54219a547925fe773de44cdeffb8cccb8dceaf4217425246b03503b7cc` |
| Ili2pgDelete / `gretl.ili2pg-delete` | 2 | `ili2pg-delete-32942ba9-d633-4c6b-88b8-23b9c2ed4d94` | `38707cd80708e0a86bdb16550cd65e5f02faa7bd5e7d00f91b98cb2e58f2694a` |
| Ili2pgExport / `gretl.ili2pg-export` | 1 | `ili2pg-export-8dfc74f0-b77b-4435-a260-4dd1e7348679` | `38707cd80708e0a86bdb16550cd65e5f02faa7bd5e7d00f91b98cb2e58f2694a` |
| Ili2pgImport / `gretl.ili2pg-import` | 7 | `ili2pg-import-0de76e97-0cc2-4d54-b9f0-de1f394c8285` | `38707cd80708e0a86bdb16550cd65e5f02faa7bd5e7d00f91b98cb2e58f2694a` |
| Ili2pgImportSchema / `gretl.ili2pg-import-schema` | 13 | `ili2pg-schema-1d017e8f-e7a4-4983-8f08-d3bf0d276455` | `38707cd80708e0a86bdb16550cd65e5f02faa7bd5e7d00f91b98cb2e58f2694a` |
| Ili2pgReplace / `gretl.ili2pg-replace` | 4 | `ili2pg-replace-34ef8bbf-4da0-4303-bd6e-567183a30405` | `38707cd80708e0a86bdb16550cd65e5f02faa7bd5e7d00f91b98cb2e58f2694a` |
| Ili2pgUpdate / `gretl.ili2pg-update` | 1 | `ili2pg-update-557065ff-26b4-489c-9896-7666d4c4631b` | `38707cd80708e0a86bdb16550cd65e5f02faa7bd5e7d00f91b98cb2e58f2694a` |
| Ili2pgValidate / `gretl.ili2pg-validate` | 6 | `ili2pg-validate-2bc7b88b-6c34-4103-96f5-e5068bc3661e` | `38707cd80708e0a86bdb16550cd65e5f02faa7bd5e7d00f91b98cb2e58f2694a` |
| IliValidator / `gretl.ilivalidator` | 3 | `additional-function-6a00c957-a25b-409d-8a5e-49d19a55c3e3` | `4580e284195ddb914c9203b9fe6bd470e83d6c6f09e5efd65ff15fdd59e30076` |
| JsonImport / `gretl.json-import` | 2 | `format-json-import-4ece5510-a447-4711-840a-0c209a3bbf44` | `f5a0b5411d7e006de86f38bca7eb3c9354cc9a5ab4fa610f623ccef8027166a0` |
| JsonValidator / `gretl.json-validator` | 2 | `valid-json-path-e797fa4a-4786-47fc-bf28-8dab5e6a6107` | `ab4dd94615f17528b110c85af5b9bb2b210fd690a865a62560b39dce36ad4e51` |
| S3Bucket2Bucket / `gretl.s3-bucket2bucket` | 2 | `original-s3-copy-7788a9ac-b78a-458b-9390-a45c14761624` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| S3Delete / `gretl.s3-delete` | 4 | `original-s3-delete-1fc0ad94-e994-42c7-994b-34bb5b095a43` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| S3Download / `gretl.s3-download` | 4 | `original-s3-download-143cb205-dd8c-40eb-a1c7-279f70c474b0` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| S3Upload / `gretl.s3-upload` | 4 | `original-s3-upload-a1bab44e-1389-4ee2-bb3b-9284a1e07029` | `c0e038f0f4fd479175d7655323383c96ef5b318540e9e1ac0a2d1a37e2cdedba` |
| ShpExport / `gretl.shp-export` | 1 | `format-shp-export-7f1b7390-f425-44af-baac-eb10741434ba` | `f5a0b5411d7e006de86f38bca7eb3c9354cc9a5ab4fa610f623ccef8027166a0` |
| ShpImport / `gretl.shp-import` | 1 | `format-shp-import-d0216dc4-ff3b-4770-8c00-c1c32e2cbb77` | `f5a0b5411d7e006de86f38bca7eb3c9354cc9a5ab4fa610f623ccef8027166a0` |
| ShpValidator / `gretl.shp-validator` | 2 | `valid-shp-path-e903ac8b-87cb-427d-93c6-3b97745e8996` | `ab4dd94615f17528b110c85af5b9bb2b210fd690a865a62560b39dce36ad4e51` |
| SqlExecutor / `gretl.sql-executor` | 4 | `database-chain-afbcff18-5896-4669-b470-f5330a10fc42` | `33afc7798b1e1f274e31e63a4ad766acbece715b8f02abb2f0571cf03f2dd19e` |
| XslTransformer / `gretl.xsl-transformer` | 3 | `io-xsl-file-482707e7-e967-4100-9280-89b6374abe48` | `3d809de1f98ead1d4f9b8b8c06c9f1a0ff6709f7b7f1eb230d5d7bb4b40ed5fd` |

## Reale Testdienste und getrennte IMAGE-Bewertung

Der eigene Vollprofilprozess startet echte lokale PostgreSQL/PostGIS-, FTP- und Floci/S3-Testdienste. `docker-events.jsonl` dokumentiert die Start-/Endereignisse des eigenen Testcontainers-Sessionlabels `a4f9716c-7185-4dd1-abfa-817964541247`; `docker-snapshot.json` verbindet die aktive PostGIS-Fixture mit dem gemappten Hostport. `service-image-identities.json` enthält die selbst abgefragten Image-IDs und Digests:

- PostGIS: `sha256:44126d872ac91993766c341e369c539e8196614321765d36a6f1bab0419a5fa5`, AMD64 emuliert.
- FTP: `sha256:60bb774d8408d9d4d5c74d05d1c086a34ce192c6c1a142ffac268cac0dbc6fac`, ARM64.
- Floci/S3: `sha256:0147b790fee481ba4d74c6bdb09e7df484e05331e19dcb7c9de511a08ca82f03`, ARM64; verwendete Fixture-Referenz floci/floci:latest, hier auf diese lokale ID aufgelöst.

Ryuk und ein Alpine-Testhelfer sind Infrastruktur, keine GRETL-Systeme unter Test. Die Dienste sind durch reale JDBC-/FTP-/S3-Clients und getrennte Fach-JAR-Prozesse geprüft. Schemata, Buckets und FTP-Verzeichnisse sind isoliert und bereinigt. Keine dieser Pflichten wurde wegen des verschobenen IMAGE-E2E ausgelassen.

**JAR: fachlich verifiziert.** Alle 44 Operationen besitzen erfolgreiche echte fachliche JAR-Nachweise; die relevanten Originalvarianten und Negativdiagnosen sind erhalten.

**IMAGE: Adapter implementiert und ohne realen Start geprüft, aber seine Vorbereitung ist wegen F1/F2 noch nicht abnahmefähig. GRETL-Produktimage tatsächlich gebaut oder gestartet: nein.** Die nicht startenden Fake-Engine-Prüfungen sind keine Image-Integration. Ein späterer ausdrücklicher IMAGE-Modus besitzt im Code keinen JAR-Fallback und keinen erfolgreichen Skip. Die zurückgestellte tatsächliche GRETL-Image-Ausführung ist kein BLOCKED-Grund und wurde nicht verlangt.

## Abschluss

Offene Pflichtfindings am geprüften unveränderlichen Kandidaten: **F1 und F2**. Keine weitere Pflichtabweichung aus Modulgraph, Java-/Maven-Migration, Originalorakeln, 44er-JAR-Abdeckung oder Phase-1-Regressionen festgestellt. Sämtliche geforderten Prüfwege konnten belastbar selbst ausgeführt werden; deshalb besteht kein BLOCKED.

**Urteil: FAIL für Phase 2 auf `7db4ab8b97d1aa8adf60bc9b137adf24c65e2a02`.** Die genannten Korrekturen müssen innerhalb Phase 2 in einen neuen sauberen Kandidaten übernommen und unabhängig erneut geprüft werden. Die erfolgreichen JAR-Nachweise lockern die IMAGE-Vorbereitungs- und Rootbuildkriterien nicht.
