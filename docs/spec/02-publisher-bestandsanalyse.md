# 02 — Publisher-Bestandsanalyse und Grenzen der Folgerungen

**Version:** 4.1, 18. Oktober 2026. **Quellenstand:** 17. Oktober 2026. **Status:** abgeschlossene statische Bestandsanalyse. Dieses Dokument trennt überprüften Repository-Code, daraus folgende allgemeine Runtime-Anforderungen und optionale fachliche Verbesserungen am Publisher. Den späteren begrenzten Portierungsnachweis als gewöhnlichen Custom-Task beschreibt [05-publisher-zielvertrag.md](05-publisher-zielvertrag.md); die dafür erhaltenen späteren Modultests stehen in [09-tests-und-abnahme.md](09-tests-und-abnahme.md).

**Korrektur gegenüber Version 1.0:** Die Komplexität des Publishers rechtfertigt keine Sonderstellung in der Runtime. Die frühere Fassung machte daraus zusätzlich einen neuen Publikationsspeicher, einen Abgleichdienst und domainbezogene Core-Zustände. Diese Vorgaben sind zurückgenommen. Vorschläge dieses Audits zu Historie, Sichtbarkeit, Metadaten oder Wiederanlauf gehören zur Weiterentwicklung des Publisher-Moduls; sie sind keine versteckten Anforderungen an den Runner-MVP.

**Lieferentscheidung in Version 3.0:** Publisher bleibt strategisch wichtig. Seine Umsetzung und Bereitstellung folgen jedoch in einem eigenen Meilenstein nach dem MVP. Der Audit und sämtliche fachlichen Zielverträge bleiben erhalten. Im MVP entstehen kein Publisher-Code, kein Publisher-Provider, kein Publisher-Artefakt und keine nur deshalb nötigen Dependencies oder Publisher-End-to-end-Gates. Die allgemeinen Lehren bleiben verbindliche Runtime-Anforderungen und werden an den tatsächlich gelieferten Tasks beziehungsweise unabhängigen Testoperationen geprüft.

Es wurden keine Produktionsjobs ausgeführt und keine Repositories verändert. Die Befunde beruhen auf Code- und Testlektüre. Als mögliche Fehler bezeichnete Fälle sind nicht durch Ausführung reproduziert. Der hier gepinnte Quellstand ist kein Nachweis für den in einer bestimmten Umgebung eingesetzten GRETL-Binary- oder Containerstand.

## Einordnung des Audits in Version 4.0

Dieser Audit bleibt inhaltlich erhalten. Er ist weder ein Auftrag, Publisher bereits im MVP zu implementieren, noch ein Grund für Publisher-Fachzustände im Core. Seine allgemeinen Erkenntnisse werden in [14](14-java-api-vertraege.md), [15](15-runner-klassendesign.md) und [16](16-logging-und-telemetrie.md) präzisiert: gewöhnliche Fachaufrufe, explizite Ergebnisse, Ressourcenbesitz, Ursachenketten, Kontext und begrenzte Fortschrittsdiagnosen. Der Loggingvergleich in Kapitel 16 unterscheidet belegte Eigenschaften der alten Adapter von neuen Entwurfsentscheidungen. Publisher-eigene Tests bleiben spätere Tests.

## 1. Quellenstand und im Repository verdrahtete Implementierung

Prüfdatum: 17. Oktober 2026.

- `sogis/gretl`, Branch `main`, Commit [`a9222dc0ffd08f8f76570e998101db041822df71`](https://github.com/sogis/gretl/commit/a9222dc0ffd08f8f76570e998101db041822df71), Commitdatum 6. Juli 2026.
- `edigonzales/gretl-next`, Branch `main`, Commit [`08bd11e97a54f32f8702d34ff16fb39bec6ea046`](https://github.com/edigonzales/gretl-next/commit/08bd11e97a54f32f8702d34ff16fb39bec6ea046). Dessen vollständiger Repository-Baum enthält keine fachliche Publisher-Implementierung; Treffer mit „PublishedArtifact“ betreffen veröffentlichte GRETL-Bibliotheksartefakte und Testinfrastruktur.
- Für die ergänzenden Jobbeispiele: `sogis/gretljobs`, Branch `main`, Commit [`7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8`](https://github.com/sogis/gretljobs/commit/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8). Jobkonfiguration und GRETL-Quellstand sind getrennte Befunde; ihre gemeinsame produktive Versionierung wurde nicht überprüft.

Die öffentliche Gradle-Klasse heisst im untersuchten GRETL `ch.so.agi.gretl.tasks.Publisher`, nicht `PublisherTask`. Ihre `@TaskAction publishAll()` instanziiert **weiterhin `PublisherStepOld`**. Der Name „Old“ bedeutet daher nicht, dass der Code im geprüften Repository unbenutzt wäre. Die Fassade umfasst rund 450 Zeilen, die Fachimplementierung rund 950. [Fassade, Zeilen 306–392][facade-action]

Die Fassade verwendet Gradle-Eigenschaften, `Project.file`, das Projekt-`buildDir` und Gradle-Exceptionmapping. `PublisherStepOld` selbst importiert keine Gradle-Typen; seine Methoden nehmen unter anderem Java-Werte, `Path`, `Connection`, INTERLIS-Settings und Fachhelfer entgegen. Die Gradle-Fassade kann daher grundsätzlich durch einen normalen Taskadapter ersetzt werden. Indirekte Abhängigkeiten, Logging und Ressourcenlebensdauern bleiben zu prüfen. Aus dieser Trennung folgt keine Notwendigkeit, den fachlichen Publikationsalgorithmus gleichzeitig neu zu entwerfen. [Fassade][facade-action] · [Engine und Imports][old-engine]

Parallel existiert in `sogis/gretl` ein neuer Baum `steps/publisher`. Darin sind einige Komponenten bereits implementiert und getestet. Wesentliche Teile sind aber leere Klassen mit Verantwortlichkeitskommentar: `PublisherRequest`, `PublisherWorkflow`, `CurrentVersionPromoter`, `SimiPublicationWriter` und weitere. `out/metainfo/table/Repository` wirft für Schreiben, Lesen der zu löschenden Publikationen und Löschen jeweils `UnsupportedOperationException`. Dieser Baum ist **eine begonnene Modularisierung, keine fertige Ersatzengine**. [Gerüstklassen][workflow-stub] · [unimplementiertes Repository][metadata-repository]

Die neue `PublisherStep`-Klasse ist ein generischer, sequenzieller Verzeichnis-Operationskoordinator. Sie bekommt Klassenbezeichnungen als Strings, lädt Klassen über `Class.forName`, reflektiert einen parameterlosen Konstruktor und verkettet nummerierte Eingangs-/Ausgangsverzeichnisse. Das ist weder der im geprüften Quellstand durch die Gradle-Fassade ausgeführte Ablauf noch bereits ein vollständiger Publikationsvertrag. [neuer Koordinator][new-coordinator]

### Vorhandener Strategieentwurf

`gretl/next_publisher.md` beschreibt unter anderem Publikationsstände auf S3, Umgebungsseparation und einen späteren Abgleich durch Netl. Als Records sind dort `dataset_ident`, `part_ident`, `modelname`, `published` und `formats` skizziert. Der Text benennt ausdrücklich Fragen und Verbesserungsvorschläge. Aus dem Dokument folgt kein nachgewiesener Nutzerentscheid, S3 und Netl für dieses MVP verbindlich einzusetzen. Es ist eine relevante Vorarbeit für eine mögliche eigenständige Weiterentwicklung des Publishers. [Strategieentwurf][next-publisher]

## 2. Der Publisher ist ein fachlicher Workflow

Der Publisher macht erheblich mehr als einen Datei-Upload. Er verbindet:

1. Auswahl von Dateien oder ili2db-Datasets beziehungsweise Modellen.
2. Optionalen Export aus einer PostgreSQL-Datenbank.
3. Validierung der Transferdatei mit ilivalidator.
4. Ermittlung von Modell-, Topic- und Basketinformationen aus dem Validierungsergebnis.
5. Ableitung zusätzlicher Formate, einschliesslich eines speziellen DM01/Geobau-Pfads.
6. Erstellung der ZIP-Archive mit Transfer- beziehungsweise Benutzerformat, Validierungslog und optionaler Validierungskonfiguration.
7. Sammlung der INTERLIS-Modelldateien.
8. Verwaltung bestehender Teilgebiete bei inkrementellen fachlichen Publikationen.
9. Rückmeldung an SIMI und Bezug eines HTML-Beipackzettels.
10. Wechsel des aktuellen Datenstands und Historisierung.
11. Entfernung bestimmter Benutzerformate aus der Historie sowie Ausdünnung alter Stände.
12. Aufräumen temporärer Dateien und Rückgabe der diesmal publizierten Regionen.

Diese Aufzählung ist durch die Datenbank- und Dateizweige sowie die gemeinsamen Methoden `publishFilePre`, `publishFile`, `publishFilePost` belegt. [Datenbankzweig][old-db] · [Dateizweig][old-file] · [gemeinsame Vorbereitung/Validierung/Publikation][old-common]

**Architekturfolgerung:** Der Publisher bleibt ein gewöhnlicher benannter Fachtask. Die allgemeine Task-Schnittstelle muss normalen Java-/Groovy-Code mit internen Methodenaufrufen, Ressourcen und strukturiertem Ergebnis tragen können. Interne Publisher-Phasen lassen sich im Modul testen und protokollieren; sie werden deshalb weder Core-Funktionen noch öffentliche DAG-Knoten.

Eine zerlegte interne Implementierung ist sinnvoll. Eine DSL, in der jeder Jobautor die Reihenfolge von zehn Publisher-Untertasks selbst richtig kombinieren muss, würde die Komplexität an die falsche Stelle verschieben. Für Fachanwender bedeutet das: Einstellungen, Eingaben, Ausgaben und verständliche Fachfehler sind Teil des Taskvertrags; interne Java-/Groovy-Mechanik und Bibliotheksregistrierung gehören nicht in ihren üblichen Jobablauf.

Der Core kennt Abhängigkeiten, Laufzeitwerte, Taskabschluss, Fehler, Ressourcen und allgemeine Diagnosen. Der Publisher kennt Publikationsziele, Formate, Regionen, SIMI, Historie und allfällige Wiederanlaufverfahren. Diese Grenze gilt ebenso für SqlExecutor und andere Custom-Tasks.

## 3. Öffentliche Konfiguration und mögliche spätere API-Verbesserungen

Die rechte Spalte benennt fachliche Optionen für eine spätere Publisher-API. Sie schreibt weder diese Typnamen noch die betreffenden Fähigkeiten für den Runner oder den ebenfalls nach dem MVP vorgesehenen schmalen Port in Kapitel 05 vor.

| Heutige Eigenschaft | Tatsächliche Bedeutung | Schluss für eine neue API |
|---|---|---|
| `dataIdent` | Identität der Datenbereitstellung und Bestandteil von Ordner-/Dateinamen. | Expliziter fachlicher Schlüssel, validiert und vom physischen Pfad getrennt. |
| `target` | Lokaler Pfad oder SFTP-Endpunkt. | Zieltyp und tatsächliche Sichtbarkeitsgarantien im Publisher dokumentieren; ein `Path` allein verspricht keine Atomarität. |
| `sourcePath` | Transferdatei; im Regionenmodus dient eine stellvertretende Datei zugleich zur Auswahl von Verzeichnis und Endung. | `SingleFileSource` beziehungsweise `FilePartsSource(directory, selection)` ausdrücklich unterscheiden. Keine Dummy-Datei verlangen. |
| `database`, `dbSchema` | PostgreSQL/ili2db-Datenquelle. | Ein `IliDatabaseSource`, mit klarer Connection- und Transaktionsverantwortung. |
| `dataset` | Ein oder mehrere über den ili2c-Modellseparator getrennte Datasetnamen, gemeinsam exportiert. | Auswahlwerte als Liste, ohne Trennzeichenkonvention in Strings. |
| `modelsToPublish` | Modellauswahl, eingeschränkt auf einfache Schemas ohne Basket-Handling. | Expliziter Auswahltyp mit geprüfter Capability. |
| `region` | Regex für Dateibasen oder Datasetnamen; schaltet den Teilgebietsmodus ein. | Auswahl und Publikations-Updatepolitik getrennt modellieren. |
| `regions` | Explizite Liste der zu selektierenden Regionen. | Liste fachlicher Teilkennungen; fehlende gewünschte Teile nicht stillschweigend ignorieren. |
| `publishedRegions` | Nur die diesmal publizierten Regionen; nicht alle im Ziel weiterhin vorhandenen. | `changedParts` und `allParts` im Ergebnis eindeutig trennen. |
| `validationConfig` | ilivalidator-Konfigurationsdatei. | Validierungsanforderung und das verwendete Artefakt dokumentieren. |
| `userFormats` | Bündelentscheidung: bei DM01 Geobau-DXF; sonst GPKG, SHP und DXF. | Explizite Formatliste beziehungsweise klar benanntes Kompatibilitätsprofil. |
| `kgdiService`, `kgdiTokenService` | SIMI-Publikationsmeldung, Beipackzettel und Authentifizierung. | Metadatenbezug und Publikationsbenachrichtigung als getrennte Verträge. |
| `grooming` | Historienausdünnung nach Altersklassen. | Nachgelagerte Wartungsoperation mit eigener Fehlersemantik. |
| `exportModels` | Konfiguration des ili2db-Exports, insbesondere Basismodelle. | Expliziter Bestandteil des DB-Exportprofils; nicht aus dem alten Kommentar ableiten, dass es nur Aufräumen betrifft. |
| `modeldir`, `proxy`, `proxyPort` | Modellsuche und Netzwerkzugriff. | Aufrufbezogene Engine-Konfiguration; gemeinsam genutzte globale Veränderungen vermeiden. |
| `version` | `Date`, standardmässig beim Taskstart gesetzt; Datum wird zum Tagesstand für die Historisierung. | Eindeutige technische Generations-ID von fachlichem Publikationsdatum und Erstellzeit trennen. |

Die Fassade prüft Datei und DB als exklusive Quellen. Im DB-Modus ist genau eine der Auswahlen `modelsToPublish`, `dataset`, `region`, nichtleere `regions` erforderlich. Im Dateimodus wird die Exklusivität von Regex und Liste nicht gleichartig geprüft; der Fachcode kann Regex-Ergebnis und Liste schneiden. Eine neue API sollte diese Unterschiede bewusst harmonisieren. [Konfigurationsprüfung][facade-action]

`modelsToPublish` wird im DB-Zweig gegen im Schema gespeicherte Modelle geprüft; bei aktiviertem Basket-Handling wird die Auswahl zurückgewiesen. Exportformat ITF/XTF und DM01-Spezialfall werden aus den zuerst gelesenen Modellmetadaten ermittelt. Das ist eine Implementierungsannahme, kein allgemeiner Nachweis, dass beliebig gemischte Modelle im Schema korrekt behandelt werden. [DB-Einzel-/Modellauswahl][old-db-models]

## 4. Tatsächliche Reihenfolge und Sichtbarkeit

### 4.1 Vorbereitung und Verarbeitung

Die Engine bildet einen Tagesbezeichner, liest gegebenenfalls die Grooming-Konfiguration und wählt ein verstecktes Stagingverzeichnis unterhalb des Ziel-Roots: `.<datum>-<uuid>`. `publishFilePre` legt die Datenbereitstellung an, prüft Konflikte mit `hist/<datum>` und erstellt das Stagingverzeichnis. Bei Teilgebietsaktualisierungen werden ZIP-Dateien aus `aktuell` in den neuen Stand kopiert. [Staging/Teilgebietsübernahme][old-pre]

Im DB-Zweig wird mit ili2db ohne integrierte Exportvalidierung exportiert. Anschliessend validiert `publishFile` jede Transferdatei separat. Ein negatives Validatorergebnis wirft eine Exception. Die Engine entnimmt dem Validator Modell-, Topic- und Basketangaben und schreibt das Transfer-ZIP. Optional folgen GPKG/SHP/DXF oder Geobau-DXF. [Export/Validierung][old-db] · [Validator und ZIP][old-validate]

Alle ausgewählten Teile müssen diese Schritte erfolgreich durchlaufen, bevor `publishFilePost` aufgerufen wird. Damit liegt bereits eine sinnvolle Vorbereitung über mehrere Teile vor. Es besteht trotzdem keine umfassende Transaktion über externe Systeme.

### 4.2 Abschluss im Ist-Code

`publishFilePost` arbeitet in dieser Reihenfolge:

1. Bisheriges Publikationsdatum aus `aktuell/meta/publishdate.json` lesen.
2. `meta/` im Staging erzeugen und Modelldateien hineinkopieren.
3. Neues `publishdate.json` schreiben.
4. Falls SIMI aktiv ist: **`notifyPublication(publicationDetails)` aufrufen**.
5. Danach den Beipackzettel über **`getLeaflet(dataIdent, date)`** beziehen und schreiben.
6. Bisheriges `aktuell` entweder löschen, wenn der Tagesbezeichner gleich ist, oder nach `hist/<altes-datum>` verschieben.
7. Im soeben historisierten Stand SHP-, GPKG- und DXF-ZIP-Dateien löschen.
8. Neues Stagingverzeichnis nach `aktuell` verschieben.
9. Historie nach Grooming-Regeln ausdünnen.

Das ist der ausgeführte Code. [Abschlussphase, Zeilen 657–745][old-post]

**Dokumentationswiderspruch:** `publisher.qmd` beschreibt im Abschnitt „Ablauf“ die Rückmeldung des Publikationsdatums nach dem Verzeichniswechsel. Der Code meldet vorher; sein Kommentar lautet, dass dies wegen SIMI vor `getLeaflet()` nötig sei. Eine spätere SIMI-Portierung muss diese Abweichung klären und darf die bestehende Dokumentation nicht ungeprüft als Ist-Vertrag kopieren. Der begrenzte Port in Kapitel 05 bietet keine SIMI-Anbindung. [Dokumentierter Ablauf][doc-flow] · [implementierte Meldung][old-notify]

### 4.3 Konsequenz für den künftigen Metadatenvertrag

Ein einfaches Verschieben der heutigen Notification hinter den Commit könnte den heutigen Beipackzettelbezug verändern. Zuerst ist zu klären, ob SIMI eine Beschreibung für eine noch nicht notifizierte Generation aus explizit übergebenen Metadaten erzeugen kann. Geeignete Strategien sind:

- Einen Beipackzettel ohne Mutation des globalen Publikationsstands vorbereiten, dann Daten committen und anschliessend den neuen Stand melden.
- Die erforderlichen Beschreibungsmetadaten vorab lesen und den Beipackzettel lokal generieren.
- Ein unabhängiges fachliches Manifest zur Wahrheit über den publizierten Stand machen und SIMI/Netl nachgelagert abgleichen.
- Für eine Übergangsphase einen klar bezeichneten Legacy-Adapter mit den bestehenden Einschränkungen anbieten.

Welche Strategie umgesetzt wird, ist eine Produkt-/Integrationsentscheidung innerhalb der Publisher-Weiterentwicklung. Das vorliegende Audit belegt die problematische Reihenfolge, aber nicht die verfügbaren serverseitigen Alternativen. Daraus entsteht kein Metadaten- oder Commitvertrag im allgemeinen Runner.

## 5. Teilpublikationen sind fachlicher Zustand, kein Gradle-Build-Cache

Der bestehende Regionenmodus führt faktisch ein **Upsert von Teilgebieten** durch:

- Er übernimmt alle bisherigen ZIPs aus `aktuell`.
- Neu erzeugte ZIPs für ausgewählte Regionen überschreiben gleichnamige übernommene ZIPs im Staging.
- Nicht ausgewählte Regionen bleiben erhalten.
- Metadaten werden für den neuen Stand erneut erstellt.
- `publishedRegions` und die SIMI-Meldung betreffen nur die neu bearbeiteten Regionen.

Dies ist nicht bloss aus Code abgeleitet: Die Dokumentation erklärt es ausdrücklich, und `file_regionsUpdate` prüft, dass Region 2501 erhalten bleibt, während nur die neu publizierte Region 2502 in `publishedRegions` steht. [Dokumentierte Teilupdates][doc-partial] · [Vertragstest][test-partial]

**Schlussfolgerung:** „Wir brauchen kein Caching“ darf das Entfernen von Gradles Build-Cache, Configuration Cache und Up-to-date-Entscheidungen bedeuten. Der Publisher benötigt dennoch Staging, bisherige Publikationsstände und gegebenenfalls Teilgebietsübernahme. Diesen fachlichen Zustand wegzulassen würde das Produktverhalten ändern.

### Entscheidungsbedarf bei einer späteren Überarbeitung des Regionenmodus

1. `REPLACE_ALL`: Die ausgewählte Menge ist die vollständige neue Publikation; nicht enthaltene Teile verschwinden.
2. `MERGE_PARTS`: Ausgewählte Teile ersetzen ihre vorherige vollständige Artefaktmenge; unveränderte Teile bleiben erhalten.
3. `DELETE_PARTS`: Explizites Entfernen bestimmter Teile. Das wäre eine neue fachliche Fähigkeit; sie darf nicht durch „Datei fehlt“ beiläufig eingeführt werden.
4. Leere Auswahl: definierter Fehler oder ausdrücklich konfigurierte erfolgreiche Nichtänderung. Es darf kein leerer „neuer Stand“ allein wegen eines fehlenden Inputs entstehen.
5. Exakte Liste mit fehlenden Teilkennungen: standardmässig verständlicher Fehler; eine tolerante Variante nur bewusst konfiguriert.
6. Ein künftiges Ergebnis kann zwischen angefragten, geänderten, erhaltenen und entfernten Teilen unterscheiden. Die bestehende Bedeutung von `publishedRegions` muss bei einer Portierung erhalten oder ausdrücklich migriert werden; eine leere Liste darf nicht unbemerkt ihre Bedeutung wechseln.

Diese Namen sind mögliche Begriffe eines Publisher-Neuentwurfs. Der MVP führt keine entsprechenden Runner-Enums, Zustandsdienste oder Pflichtfelder ein.

### Konkrete Risiken des heutigen Teilgebietsmodus

Die folgenden Punkte sind Code-Inferenzen und keine nachgewiesenen Produktionsvorfälle:

- **Alte Benutzerformate eines aktualisierten Teils können stehen bleiben.** Wenn ein früher mit Benutzerformaten publizierter Teil später ohne diese Formate aktualisiert wird, kopiert die Vorbereitung weiterhin alle alten ZIPs. Der neue Lauf ersetzt nur die Transferdatei. Die alte GPKG/SHP/DXF-Datei dieses Teils kann dadurch weiterhin angeboten werden. [Kopierlogik][old-pre]
- **Modellmetadaten können unvollständig sein.** `modelFiles` wird pro Region neu angelegt. An `publishFilePost` wird die Liste des letzten bearbeiteten Teils übergeben. Bereits übernommene oder frühere neue Teile können zusätzliche Modelldateien benötigen. Aus homogenen AV-Jobs folgt nicht, dass dies für heterogene Teilmengen ausreichend ist. [Dateischleife][old-file] · [DB-Schleife][old-db]
- **Nicht gefundene explizite Regionen werden geschnitten, nicht einzeln zurückgewiesen.** `retainAll` entfernt unbekannte gewünschte Regionen aus der Ist-Liste. [Filtermethode][old-filter]
- **Leere Auswahl ist inkonsistent behandelt.** Im DB-Regionenpfad bleibt `modelFiles` bei null und wird später iteriert; im Dateipfad beginnt es als leere Liste, so dass eine leere Bearbeitungsmenge weiter gelangen kann. Die beiden Pfade besitzen keinen gemeinsamen ausdrücklichen Leerfallvertrag. [DB-Zweig][old-db] · [Dateizweig][old-file] · [Metadateniteration][old-post]

Ein Manifest mit der vollständigen Publikationsmenge wäre eine mögliche Verbesserung. Vorher sind Nutzen, Kompatibilität und Aufwand gegenüber einer engeren Korrektur der bestehenden Kopier-/Metadatenlogik abzuwägen. Beide Varianten betreffen das Publisher-Modul.

### Zwei reale Jobmuster und ihre Architekturfolgen

#### Schiesslärm: Publisher als Exportumweg

`afu_schiesslaerm_pub` aktualisiert ein einzelnes Schiessplatz-Dataset im Pub-Schema und verwendet anschliessend zwei Publisher. `exportFullXtf` publiziert mit `region = ".*"` und `dataIdent = "full_export"` lokal in das Buildverzeichnis. Der nächste Copy-Task entpackt ausdrücklich `full_export/aktuell/test.full_export.xtf.zip`. Ein zweiter Publisher verarbeitet die entpackte XTF-Datei für die eigentliche Datenbereitstellung mit Benutzerformaten und entfernten Metadaten-/Dateizielen. Die Taskbeschreibung nennt den ersten Publisher selbst einen Workaround. Die README erläutert die beabsichtigte Hilfspublikation. [Schiesslärm-Job][job-schiesslaerm] · [Jobbeschreibung][job-schiesslaerm-readme]

Das Beispiel zeigt eine benötigte unabhängige Operation: **Transferdateien aus einer fachlich ausgewählten DB-Menge exportieren, ohne damit bereits Versionierung, ZIP-/Layoutkonventionen und Publikationsmetadaten auszulösen.** Der künftige Publisher kann dieselbe Exportengine intern nutzen. Der Jobautor muss diese Engine aber auch direkt als Fachtask beziehungsweise synchrone Operation aufrufen können. Das trennt wiederverwendbare Fachlogik von ihrem Publikationsablauf.

Die Formulierung der README darf dabei nicht zu einer unbewiesenen Fähigkeit des bestehenden Codes verallgemeinert werden: Der betrachtete Job selektiert alle Datasets, entpackt aber einen expliziten `test`-Dateinamen. Das ist kein Nachweis für eine allgemeine Merge-to-single-XTF-Implementierung. Eine spätere Export-API muss Auswahl und Zusammenführung separat definieren. Diese DB-Capability gehört nicht zum begrenzten Portierungsnachweis.

#### DM01: Resultatübergabe steuert nachfolgende Publikationen

Im DM01-Job publiziert `0_dm01_so` die diesmal entpackten ITF-Dateien. Die Publisher in `1_dm01_ch` und `2_mopublic` verwenden anschliessend genau `project(':0_dm01_so').tasks.publish.publishedRegions` als Eingabe. Einmal handelt es sich um eine Dateiquelle mit Geobau-Benutzerformat, einmal um eine DB-Quelle. Die Abhängigkeiten laufen über `execProjPubTasks` und vorgelagerte Fachoperationen. [Erster Publisher][job-dm01-so] · [CH-Folgepublikation][job-dm01-ch] · [MOpublic-Folgepublikation][job-dm01-mopublic]

Die gemeinsame README bestätigt, dass diese Resultatliste die zu publizierenden Gemeinden steuert. [DM01-Jobbeschreibung][job-dm01-readme]

**Folgerung für den Runner:** Laufzeitkonfiguration und typisierte Resultate sind echte Anforderungen. Ein nachfolgender Task braucht die tatsächlich erfolgreich geänderte Teilmenge seines Vorgängers, obwohl diese beim Einlesen des Jobs noch nicht feststeht. Das Ergebnis darf erst nach dem definierten Erfolgspunkt des Publishers freigegeben werden; allein das Vorbereiten einer Region genügt nicht. Die Jobabhängigkeit wird beim Einlesen registriert, die daraus abgeleitete Eingabemenge erst beim Start des abhängigen Tasks ermittelt. Dafür ist kein zur Laufzeit umgebauter DAG nötig.

Zudem löscht `0_dm01_so` die heruntergeladenen Quelldateien auf dem FTP-Server erst über eine Abhängigkeit auf die abschliessenden Publikationszweige. Das ist fachliche Quittierung erfolgreicher Verarbeitung und darf nicht zu allgemeinem Cleanup bei Fehler werden. Wenn ein künftiger Publisher Daten bereits committen konnte, aber erforderliche Nacharbeiten noch ausstehen, muss die Erfolgspolitik ausdrücklich bestimmen, ob solche Quittierung freigegeben wird. [Quelllöschung nach Publikationszweigen][job-dm01-so]

Keiner der beiden Jobs ist durch den schmalen Portierungsnachweis bereits vollständig migrierbar. Sie liefern spätere Abnahmemuster für die angebotenen Taskbibliotheken: DB-Export, Teilresultate, Formatableitung, Quittierung und entfernte Ziele.

## 6. Fehlergrenzen und mögliche inkonsistente Zustände

| Fehlerstelle | Im Code bereits erfolgte Wirkung | Risiko beziehungsweise fachlicher Verbesserungsbedarf |
|---|---|---|
| Export oder Validierung vor Abschlussphase | Temporäre Daten, bei Teilupdate bereits kopierte Altartefakte im Staging. | Aktuell bleibt im Normalfall unverändert. Diagnoseartefakte sollten auf Fehler erhalten bleiben können. |
| SIMI meldet Erfolg, Beipackzettelabruf scheitert | Externe Metadaten sind bereits verändert; aktueller Dateistand noch alt. | Eine allgemeine Exception ohne fachliche Details erklärt die Teilwirkung nicht. |
| Altstand wurde nach Historie verschoben, Entfernen von Benutzerformaten scheitert | `aktuell` ist bereits weg; neuer Stand noch im Staging. | Wiederherstellung benötigt Kenntnis des tatsächlich erreichten Zustands. |
| Gleiches Tagesdatum: Altstand gelöscht, neue Promotion scheitert | Alter aktueller Stand wurde gelöscht. | Datenzugriff kann ausfallen; destruktive Ersetzung ist nicht ein atomarer Commit. |
| Move auf `aktuell` erfolgreich, History-Liste/Grooming-Vorbereitung scheitert | Neuer Stand ist sichtbar, Task kann trotzdem fehlschlagen. | Fachliche Diagnose sollte sichtbare Ausgabe und fehlgeschlagene Nacharbeit unterscheiden. |
| Einzelnes historisches Verzeichnis kann nicht gelöscht werden | Neue Publikation ist sichtbar. | Der Ist-Code protokolliert das und läuft weiter; sinnvoll als Wartungswarnung. |
| Aufräumen in einem `finally` scheitert | Primärfehler oder bereits erfolgreicher Commit kann überdeckt werden. | Primärfehler bewahren, Cleanupfehler als unterdrückte Fehler/Warnungen erfassen. |
| Verbindung bricht während externer Mutation ab | Client kennt den endgültigen Serverzustand möglicherweise nicht. | Zustand prüfen; ein fehlendes Erfolgssignal beweist keine ausgebliebene Wirkung. |

Die ersten sieben Zeilen folgen aus der Aufrufreihenfolge und den vorhandenen `finally`-/Catch-Blöcken. Die letzte Zeile ist eine allgemeine Folgerung für externe Commitoperationen; sie ist kein spezifisch nachgewiesener Fehler des SFTP-Providers. [Abschluss/Cleanup][old-post] · [äusserer Cleanup][old-file]

### Keine atomare Gesamtpublikation im aktuellen Code

Es gibt mehrere `Files.move`-/Delete-Schritte, keine explizite Publisher-Sperre, kein Commitjournal und keine Kompensation zwischen SIMI und Dateisystem. `Files.move` wird ohne `ATOMIC_MOVE` aufgerufen. Daraus kann keine allumfassende Atomaritätsgarantie abgeleitet werden. Selbst wenn ein einzelner Rename auf dem verwendeten Dateisystem atomar ist, sind zwei Renames und eine HTTP-Mutation zusammen keine atomare Transaktion. [Promotion][old-promotion]

Eine UUID schützt nur den Namen des Stagingverzeichnisses. Zwei Publisher mit demselben `target + dataIdent` können dennoch denselben vorherigen Stand lesen und später konkurrierend ersetzen. Bei Teilupdates droht dann verlorene Aktualisierung. Soll das Publisher-Modul konkurrierende Writer unterstützen, braucht es dafür einen eigenen geprüften Vertrag, etwa eine Zielsperre oder eine Konfliktprüfung. Der sequenzielle DAG eines einzelnen Jobs löst dieses prozessübergreifende Problem nicht.

### Fachliche Diagnose und allgemeiner Taskstatus

Für den Betrieb macht es einen Unterschied, ob die bisherigen Dateien noch unverändert sind, die neuen Dateien bereits sichtbar sind, nur eine Nacharbeit scheiterte oder der aktuelle Zustand nicht sicher bekannt ist. Das Publisher-Modul kann solche Informationen in eigenen Resultaten und Fehlerdetails beschreiben. Dieses Audit legt dafür keine neue Zustandsmaschine fest.

Der Runner behandelt eine unbehandelte Exception als normalen Taskfehler. Er leitet daraus weder „keine externe Wirkung“ noch „Rollback abgeschlossen“ ab und wiederholt den Task nicht automatisch. Ein Jobautor darf eine Exception eines synchron aufgerufenen Tasks im Action-Body bewusst fangen; normaler Abschluss des Bodys kann dann Erfolg bedeuten. Es gibt keine Publisher-Pflicht, die diesen Erfolg im Core nachträglich überstimmt. Der gleiche Vertrag gilt für SQL-, HTTP- und andere Tasks mit Seiteneffekten.

Diagnose bleibt erhalten, soweit der Task sie über die allgemeine Berichtsoberfläche ausdrücklich freigibt. Eine dauerhaft notwendige Wiederanlaufinformation wäre Besitz des Publisher-Moduls beziehungsweise seines Zielsystems. Eine spezielle Runtime-Registry könnte diese fachliche Wahrheit nach einem Prozessabsturz ohnehin nicht ersetzen.

## 7. Publikationsspeicher: lokales Dateisystem, NFS, SFTP und S3

Die alte API behandelt den lokalen und den SFTP-Speicher weitgehend einheitlich als NIO-`Path`. Das vereinfacht Aufrufe, abstrahiert aber die nötigen Garantien nicht ausdrücklich.

Bei einem späteren Publisher-Neuentwurf sind für jeden unterstützten Zieltyp folgende Fragen zu entscheiden:

- Identität des Ziels und den Geltungsbereich konkurrierender Writer.
- Art und Prüfung einer vollständigen vorbereiteten Generation.
- Möglichkeit eines atomaren Wechsels eines kleinen Verweises oder eine alternative Commitprozedur.
- Vorgehen bei nicht unterstützten atomaren Operationen.
- Nachweis beziehungsweise Ermittlung der tatsächlich wirksamen Generation.
- Cleanup von nicht referenzierten Generationen und Verhalten bei Readers, die noch alte Artefakte lesen.
- Verhalten von Teilupdates und konkurrierenden Änderungen.

**Eine zugesicherte Eigenschaft muss der tatsächlich eingesetzte Zieltyp erfüllen.** Wenn ein künftiger Publisher atomare Ersetzung anbietet, darf er nicht still auf Copy+Delete ausweichen. Eine lokal geprüfte Implementierung ist kein Nachweis derselben Garantie für NFS oder SFTP. Solche Zusagen und die zugehörigen Tests gehören zum jeweiligen Publisher-Zieladapter.

Für eine neue Layoutvariante ist ein kleines `current`-Manifest, das auf eine unveränderliche Generation zeigt, ein sinnvoller Ansatz. Dabei sind die bisherigen Konsumenten zu berücksichtigen: Die heutigen direkten URLs unter `aktuell/` folgen nicht automatisch einem JSON-Verweis. Nötig wäre ein passender Resolver, eine bewusst eingesetzte Symlink-/Pointer-Abbildung oder ein dokumentierter Kompatibilitätsadapter. „Wir ändern intern das Layout“ ist hier keine folgenlose Implementierungsentscheidung.

S3 darf nicht als Verzeichnis-Rename-Speicher modelliert werden. Eine spätere S3-Implementierung würde vorbereitete Objekte unter einer Generation plus expliziten kleinen Commitverweis verwenden und ihre Konkurrenzgarantien separat erfüllen. Der S3/Netl-Entwurf aus dem Repository ist dafür ein möglicher Anschluss, aber kein bereits beschlossener MVP-Zwang.

## 8. Ressourcen, Globals und Parallelität

### 8.1 SFTP-Lebensdauer

Die Fassade erstellt pro SFTP-Aufruf ein `FileSystem`, startet einen Daemonthread, der alle 60 Sekunden `keepAlive` ausführt, und gibt nur einen `Path` zurück. In der Fassade ist weder ein abschliessendes `FileSystem.close()` noch ein Stoppen dieses Threads implementiert. Der Thread beendet sich bei Interrupt oder I/O-Fehler. Ein lang laufender Runner darf diesen Lebenszyklus nicht unverändert übernehmen. [SFTP-Fassade][facade-sftp]

Allgemeine Runner-Folge: Ein Task besitzt einen Arbeitsbereich, einen Kontext und eine einfache Ressourcenhilfe. Der Adapter muss eigene Connections, Streams, Dateisysteme und erzeugte Backgroundaufgaben tatsächlich freigeben. Die Runtime kann vom Task verschwiegene Ressourcen nicht automatisch entdecken. Ein Prozess pro Job begrenzt akkumulierte Leaks, ersetzt aber ordentliche Freigabe nicht.

### 8.2 Datenbankverbindung und Snapshot

`Publisher.publishAll()` übergibt `database.connect()` an die Engine. `Connector` speichert eine Connection zwischen Aufrufen, setzt Auto-Commit auf false und bietet eine eigene `close()`-Methode. Der Publisher ruft diese Methode im untersuchten Code nicht auf. Im gelesenen Plugin wird lediglich Logging initialisiert. [Connection-Übergabe][facade-action] · [Connector][connector] · [Plugin][plugin]

Die Publisher-Orchestrierung definiert keine übergeordnete Isolationsebene oder einen beweisbaren gemeinsamen Snapshot über sämtliche Exporte mehrerer Teile. ili2db erhält die Connection. Ob und wie dessen interne Aufrufe Transaktionen verändern, muss beim Engineadapter geklärt werden. Aus „eine Connection“ allein folgt kein reproduzierbarer konsistenter Stand über alle Quelltabellen und Regionen.

Folge für eine spätere DB-Portierung: den tatsächlichen Snapshotvertrag dokumentieren; beispielsweise operative Ruhe der Quelle, einen getesteten Snapshot-Ablauf oder vorgelagerte Materialisierung. Keine implizite Konsistenzbehauptung aus dem gemeinsamen Runner ableiten.

### 8.3 Globale Schnittstellen

`LogEnvironment` verwaltet eine statische Loggerfactory und versucht beim ersten Zugriff, Gradle über `Class.forName` zu erkennen. `Ehi2GretlAdapter` registriert einen Singletonlistener am globalen EhiLogger und entfernt den Standardlistener. `DbConnector` registriert JDBC-Treiber dynamisch beim DriverManager. [Logging][logging] · [EHI-Adapter][ehi-adapter] · [DB-Helper][db-helper]

Es reicht deshalb nicht, alle Fachtasks für parallel ausführbar zu erklären. Ein MVP mit sequenzieller Taskausführung innerhalb eines Jobprozesses ist pragmatisch. Bei einem nachgewiesenen Konflikt kann ein Taskmodul Bibliothekszugriffe serialisieren oder in einen separaten Prozess verlagern. Daraus folgt weder ein obligatorisches Workerprotokoll für jeden Task noch ein Host-Koordinator für den Publisher. Logs benötigen allgemeinen Run-/Taskkontext; einen Part-Kontext kann das Publisher-Modul selbst ergänzen.

Der gelesene DB-Helper schreibt auf DEBUG die übergebene Passwortvariable in eine Meldung. Es wurden hier keine realen Zugangsdaten ausgegeben. Dies ist ein konkreter Grund, den Helper nicht unverändert als künftigen Runtime-Service zu übernehmen und Geheimnisredaktion in der neuen Basis durchzusetzen. [DB-Helper, Zeilen 63–67][db-helper]

### 8.4 Arbeitsverzeichnisse

Die Gradle-Fassade übergibt das gemeinsame Projekt-`buildDir`. Die Engine verwendet darin unter anderem aus Dataset-/Modellnamen gebildete Dateinamen. Verschiedene Publisher oder Wiederanläufe können sich damit ins Gehege kommen, obwohl ihre entfernten Stagingverzeichnisse eine UUID haben. Die Runtime stellt pro Run und Task beziehungsweise synchronem Aufruf einen Arbeitsbereich bereit. Teilgebiete bekommen innerhalb des Publisher-Moduls bei Bedarf eigene Unterverzeichnisse. Das behebt keine Konkurrenz um ein gemeinsam gewähltes dauerhaftes Publikationsziel.

## 9. GeoTools und Formatsemantik

Die Formatkonvertierung ist keine austauschbare Nebenfunktion ohne fachliche Konsequenzen.

### 9.1 Konverterkette

- XTF → GPKG verwendet ili2gpkg, impliziten Schemaimport, deaktivierte erneute Validierung, aktiviertes Arc-Stroking, Geometrieindex und ili2db-Metadaten. Diese Metadaten braucht insbesondere der nachfolgende DXF-Konverter. [GPKG-Erstellung][old-gpkg]
- GPKG → SHP verwendet `GeoPackageReader`, `ShapeWriter`, GeoTools-Attributtypen und GeoTools-CRS-Factories; die Geometrieklassen kommen aus `org.locationtech.jts`. [SHP-Konverter][shp]
- GPKG → DXF verwendet `GeoPackageReader`, ili2db-Metadaten zu `dxflayer`, `iox_j`-Konvertierung und `com.vividsolutions.jts`. Die DXF-Ausgabe erfolgt in ISO-8859-1. [DXF-Konverter][dxf]
- Für `DM01AVCH24LV95D` wird der Spezialkonverter `Av2geobau` direkt auf ITF verwendet. [DM01-Konverterwahl][old-db]

Damit existieren bereits innerhalb des Publisher-Umfelds zwei JTS-Namensräume. Ein GeoTools-Update entfernt die Legacy-Abhängigkeiten der INTERLIS-/Geobau-Kette nicht automatisch.

### 9.2 Konkrete SHP-Eigenheiten

Der SHP-Konverter:

- Fügt bei Tabellen ohne Geometrie einen synthetischen Punktdescriptor mit SRID 2056 hinzu.
- Liest für vorhandene Geometrien zwar die tatsächliche SRS-ID und fragt die entsprechende CRS-Factory ab.
- Setzt am Writer später einen Default-SRID 2056.
- Überschreibt schliesslich jede `.prj` mit einem festen LV95-WKT, als historischer QGIS-Kompatibilitätsworkaround.
- Wandelt unter anderem BIGINT und NUMERIC in Double um.
- Kürzt Stringwerte mit mehr als 240 Zeichen auf 229 Zeichen plus ` TRUNCATED!`.

Diese Aussagen sind direkt im Code sichtbar. [SHP-Descriptor/Typmapping][shp-descriptors] · [Writer/Kürzung/PRJ][shp-writer]

**Folge für eine Formatportierung:** Das Modul muss seine unterstützten CRS und Verlustregeln dokumentieren. Eine zunächst unterstützte LV95-Teilmenge wäre möglich. Ein allgemeines Versprechen für beliebige CRS, Attribute und Modelle wäre durch den aktuellen Code nicht gedeckt. Der schmale Publisher-Port in Kapitel 05 bietet diese Benutzerformate noch nicht an.

SHP-Prüfungen dürfen nicht bei „ZIP existiert“ enden. Sie müssen mindestens CRS, Koordinaten, Geometrietypen, Attribute und bekannte Konversionsverluste prüfen. Bei modernerer GeoTools-/iox-wkf-Version müssen die alten Workarounds überprüft und gegebenenfalls entfernt werden, anstatt sie automatisch mitzuziehen.

## 10. Wiederverwendung der begonnenen Modularisierung

Die neue Struktur enthält nützliche Ansätze:

- `DataSelection`/`Exporter` unterscheiden dataset/model/topic/basket und können einzeln oder zusammen exportieren. Der Exporter prüft Dateinamenskollisionen nach Sanitisierung und zählt exportierte Objekte. [Exporter][new-exporter]
- `SingleTransferDerivator` arbeitet mit einer expliziten Formatmenge und erzeugt ein GPKG auch nur als Zwischenformat, wenn es für SHP/DXF gebraucht wird. [Derivator][new-derivator]
- `CacheValidator`, Packer/Renamer/Zipper und ihre Tests isolieren Verantwortlichkeiten.
- `CopyOperation` kopiert in bestehende Schemas, berücksichtigt ili2db-Baskets und delegiert den Connection-/Transaktionsbesitz an den Aufrufer. [Schemakopie][schema-copy]

Dennoch muss eine Übernahme selektiv erfolgen:

1. Der neue Workflow und die Veröffentlichungsschicht sind unvollständig.
2. Der reflektive Koordinator lädt Implementierungen über Klassennamen. Das ist keine notwendige Runner-Abstraktion und kein Nachweis, dass diese Implementierungen vollständig sind. Ein Task darf seine internen Klassen gewöhnlich aufrufen; eine Native-Image-Eignung müsste später separat geprüft werden.
3. `Mapper.inferPartIdent` leitet Teile aus Dateinamenspräfixen ab. Bei einer globalen Datei `ch.so.example.xtf.zip` und `publicationIdent=ch.so.example` entsteht nach dem ersten Punkt der Kandidat `ch`; die vorhandenen Tests verwenden dagegen einen Publikationsidentifier ohne Punkte. Dieser Fall ist durch statische Auswertung der Methode erkennbar und muss beim Wiederverwenden getestet beziehungsweise durch explizite IDs vermieden werden. [Mapper][new-mapper] · [Mappertests][new-mapper-test]
4. `SingleTransferDerivator` bereinigt gemeinsame Formatunterverzeichnisse neben der jeweiligen Transferdatei. Falls mehrere Transferdateien im selben Verzeichnis liegen, kann eine spätere Ableitung die früher erzeugten Formatergebnisse löschen. Ein eindeutiger Verzeichnis-/Manifestvertrag pro Part ist nötig. Dies ist eine Code-Inferenz; die vorhandenen Einzeltests decken den beschriebenen Mehrdateifall nicht ab. [Derivator-Verzeichnisbehandlung][new-derivator]
5. `CacheValidator` interpretiert `validation.ini` als Settingsdatei, übersetzt Schlüssel und kann darüber auf eine weitere Validator-Konfiguration verweisen. Der alte Publisher übergibt `validationConfig` direkt als `Validator.SETTING_CONFIGFILE`. Die beiden Konfigurationsverträge sind nicht automatisch identisch. [neuer Validator][new-validator] · [alter Validator][old-validate]
6. Packer/Zipper arbeiten mit Namens- und Nachbarschaftskonventionen. Insbesondere sucht Zipper eine feste `validation.log`, während CacheValidator aus dem Transferdateistamm einen Lognamen ableitet. Ohne ausdrücklichen verbindenden Schritt dürfen diese Komponenten nicht als bereits kompatible End-to-end-Pipeline bezeichnet werden. [Zipper][new-zipper] · [Validator][new-validator]

**Wiederverwendungsregel für die spätere Publisher-Arbeit des Coding-Agenten:** Fachlogik darf mit passenden Tests extrahiert, adaptiert oder begrenzt wiederverwendet werden. Ein Adapter um `PublisherStepOld` ist für den ausdrücklich abgegrenzten Port nach dem MVP zulässig. Er muss seine Gradle-Kopplung, Ressourcen und Abhängigkeiten bearbeiten und darf keine neuen Sicherheits- oder Atomaritätseigenschaften zusichern, die der weiterverwendete Algorithmus nicht erfüllt. Portierung und fachlicher Neuentwurf sind getrennte Änderungen. Diese Regel aktiviert keinen Publisher-Implementierungsauftrag innerhalb des MVP.

## 11. Vorhandene Tests und spätere fachliche Prüffelder

Die bestehenden Tests liefern wertvolle Fachverträge:

- Dateipublikation, Benutzerformate, erste Historie, gleiches Datum überschreiben, Tageskonflikt in Historie und Grooming.
- Regionenauswahl via Regex und Liste sowie Erhalt vorheriger Regionen beim Teilupdate.
- DB-Auswahl, einfache Modelle, Ablehnung der Modellauswahl bei Basket-Handling.
- Formatbestand im aktuellen und historischen Stand; Benutzerformate fehlen bewusst in der Historie.
- SIMI-Payload und Authentifizierungs-/Beipackzettelaufrufe über lokalen Testserver.

[Dateiverträge][test-file] · [DB-Verträge][test-db] · [SIMI-Tests][test-simi]

Die Dateiverträge sind in einer abstrakten Testklasse; im geprüften Baum ist der konkrete Erbe der mit SFTP-Testtag versehene `PublisherStepOldFile2RemoteTest`. Die neuen modularen Tests ersetzen deshalb nicht ohne Weiteres einen automatisch laufenden lokalen End-to-end-Test des alten Dateipublishers. [Remote-Erbe][test-remote]

Ein weiterer statischer Prüfpunkt: `PublisherTestOld` referenziert Jobverzeichnisse `Publisher` und `PublisherRegions`; im geprüften Baum heissen die zugehörigen Verzeichnisse `PublisherOld` und `PublisherRegionsOld`. Ohne Ausführung lässt sich nicht sagen, welcher Testpfad tatsächlich in CI läuft. Die Spezifikation darf vorhandene Testdateien nicht mit einem nachgewiesenen grünen Baselinebuild verwechseln. [Integrationstest][test-integration]

### Fachliche Prüffelder für spätere Erweiterungen

Die folgende Tabelle ist ein Backlog aus dem Audit, keine zusätzliche MVP-Abnahme. Sobald eine entsprechende Fähigkeit angeboten oder geändert wird, muss ihr Taskmodul einen konkreten Vertrag und geeignete Tests besitzen. Die Tabelle schreibt dafür kein neues Speicherlayout und keinen Wiederanlaufdienst vor.

| Themenbereich | Relevante Fälle bei einer späteren Portierung oder Überarbeitung |
|---|---|
| Regionen | Unveränderte Regionen bleiben erhalten; vollständig ersetzte Teile behalten keine veralteten Benutzerformate; fehlende und leere Auswahl besitzen eine dokumentierte Bedeutung. |
| Modelle | Alle für die angebotenen Teile benötigten Modelldateien vorhanden; gleichnamige unterschiedliche Dateien erkannt; Auswahl und tatsächlich verwendete Modelle nachvollziehbar. |
| Sichtbarkeit | Fehler vor und nach Dateiersetzung sowie zwei Publikationen am selben Tag gegen die jeweils zugesagte Semantik prüfen. Atomarität nur behaupten, wenn implementiert und nachgewiesen. |
| Konkurrenz | Zwei Writer auf dasselbe Ziel; Unterstützung oder betriebliche Einschränkung ausdrücklich dokumentieren. Ein einzelner sequenzieller Job ist kein Konkurrenznachweis. |
| Metadaten | SIMI-Erfolg vor Dateifehler, verlorene Antwort, wiederholte Benachrichtigung, Beipackzettelabhängigkeit und spätere Wartungsfehler nachvollziehbar behandeln. |
| Ressourcen | Primärfehler bei zusätzlichem Cleanupfehler erhalten; keine offenen Connections, Streams, SFTP-Sitzungen oder weiterlaufenden Keepaliveaufgaben. |
| Formate | CRS, Geometrien, Attribute, bekannte Verluste, tatsächlicher Archivinhalt und Verhalten nach Bibliotheksupdates prüfen. |
| Migration | Bestehende Dateinamen, URLs unter `aktuell/`, Tageshistorie, Ausgaben und `publishedRegions` mit repräsentativen bisherigen Jobs vergleichen. |

## 12. Erhaltene Lehren und vom MVP getrennter Publisher-Port

Der Publisher liefert erst nach dem MVP einen **eigenständigen begrenzten Portierungsnachweis für dieselbe Custom-Task-SPI**, die auch SqlExecutor und eigene Tasks benutzen. Kapitel [05](05-publisher-zielvertrag.md) hält dafür lokale Einzel-XTF, echte Validierung, Transfer-ZIP, Modelldateien und das bestehende Ausgabe-/Historienlayout fest. `userFormats=false`; DB, Regionen, SIMI, SFTP und automatische Historienbereinigung gehören nicht zu diesem ersten Publisher-Meilenstein. Es entstehen kein neuer Generationsstore und keine zusätzlichen CLI-Kommandos.

Die Anwendung einer vorhandenen Fachengine ist beim späteren Port zulässig, soweit die angebotene Teilmenge kontrolliert portiert, Gradle-frei ausgeliefert und getestet wird. Das Ergebnis ist weder eine vollständige Publisher-Migration noch eine Zusage neuer Atomarität. Aufgedeckte Probleme bleiben dokumentiert und als getrennte fachliche Arbeit geplant. Dieser Liefermeilenstein ist wegen der Bedeutung des Publishers ausdrücklich Teil des strategischen Ziels, aber unabhängig von der optionalen Job-JAR-Paketierung und Native Image.

Die MVP-Distribution liefert HttpDownload, Extract, SqlExecutor und in Stufe B IliValidator. Hinzu kommt die technische GeoTools-Qualifikation. Sie enthält keinen Publisher-Provider und keine Publisher-Platzhalter. Eine eigenständige XtfToGpkg-Operation ist ebenfalls eine spätere Erweiterung mit noch festzulegendem Liefervertrag. Diese bewusste Abgrenzung der ersten Version verhindert nicht, dass beide Tasks später regulär in die Standarddistribution aufgenommen werden.

**GPKG-Erzeugung ist kein ausreichender GeoTools-Kompatibilitätstest.** Sie prüft in diesem Ablauf vor allem die ili2gpkg-/SQLite-Kette. Die Plattformqualifikation benötigt deshalb einen eigenen tatsächlichen GeoTools-/CRS-Nachweis. Dieser Nachweis bleibt unabhängig davon, welche Benutzerformate der Publisher bereits anbietet; die Details stehen in [07](07-plattform-und-versionen.md) und [09](09-tests-und-abnahme.md).

Die tragfähige allgemeine Architektur lässt sich aus dem Audit enger begründen:

| Allgemeine Runtime-Verantwortung | Taskinterne Verantwortung des Publishers |
|---|---|
| Jobregistrierung, Abhängigkeiten und Zielauswahl. | Quellen, Regionen und Publikationsziel auswählen. |
| Konfiguration bei Taskstart und Resultate zwischen Tasks. | Bedeutung von Formaten, `publishedRegions` und sonstigen Ergebnissen festlegen. |
| Normale Fehler-, Catch-, Cancellation- und Cleanup-Semantik. | Fachlichen Erfolg, Teilwirkungen, mögliche Wiederherstellung und Wartung definieren. |
| Pro Aufruf Kontext, Arbeitsbereich und allgemeine Berichtsoberfläche. | INTERLIS-/GeoTools-Engines, Modellbestände, Staging und externe Dienste verwalten. |
| Erweiterung über dieselbe Java-SPI. | Eigene Implementierung und Abhängigkeiten mitliefern; interne Zerlegung selbst wählen. |

Die reine Core-SPI setzt keine Gradle-, Groovy-, GeoTools- oder INTERLIS-Typen voraus. Taskmodule dürfen ihre eigenen Bibliotheken verwenden. Prozessgrenzen und explizite Transporttypen werden erst eingeführt, wenn ein tatsächlich angebotener Ausführungsmodus sie benötigt.

Die allgemeinen Nachweise für späte Konfiguration, Ergebnisübergabe, Teilwirkungen, normales Catch und Ressourcenfreigabe bleiben aktive MVP-Gates. Sie verwenden unabhängige Testoperationen und die real gelieferten HTTP-, SQL- und INTERLIS-Pfade. Die Publisherfälle bleiben als spätere Abnahme erhalten; ihre Verschiebung darf weder als bereits bestandener Test noch als entfallene Anforderung an den späteren Task dargestellt werden.

## 13. Einordnung in Verpackung und optionale Native-Ausführung

Sobald der Publisher geliefert wird, ist er beim Erstellen eines darauf angewiesenen Job-Fat-JARs eine gewöhnliche enthaltene Taskbibliothek. Seine Modelle, Konfigurationen und übrigen Ressourcen müssen nach dem allgemeinen Jobpaketvertrag behandelt werden; dauerhafte Publikationsausgaben liegen ausserhalb des JARs. Andere Job-JARs benötigen den Publisher nicht.

Eine mögliche spätere Native-Variante eines ganzen Jobs muss die tatsächlich verwendeten Taskbibliotheken qualifizieren. Der Publisher-Audit macht weder einen nativen Publisher-Worker notwendig noch einen beliebigen vollständigen Publisher automatisch native-fähig. Die vereinfachte Strategie steht in [08-native-image-strategie.md](08-native-image-strategie.md). Sie verändert weder den späteren fachlichen Publisher-Vertrag noch die allgemeine MVP-Ausführungssemantik.

## Quellenverweise

Die Verweise in diesem Dokument sind auf feste Repository-Commits gebunden. Sie dokumentieren den untersuchten Quellstand, nicht den Zustand einer Produktionsumgebung.

[facade-action]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/tasks/Publisher.java#L306-L392
[old-engine]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L1-L110
[facade-sftp]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/tasks/Publisher.java#L396-L444
[old-db]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L81-L257
[old-db-models]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L166-L244
[old-file]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L417-L511
[old-common]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L537-L745
[old-pre]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L614-L655
[old-validate]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L537-L612
[old-post]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L657-L745
[old-notify]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L683-L691
[old-promotion]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L694-L724
[old-filter]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L271-L274
[old-gpkg]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L384-L405
[doc-flow]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/docs/publisher.qmd#L365-L390
[doc-partial]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/docs/publisher.qmd#L169-L212
[next-publisher]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/next_publisher.md
[workflow-stub]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/PublisherWorkflow.java
[metadata-repository]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/out/metainfo/table/Repository.java
[new-coordinator]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/PublisherStep.java
[connector]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/api/Connector.java#L39-L55
[plugin]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/gradle/GretlPlugin.java
[logging]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/logging/LogEnvironment.java
[ehi-adapter]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/logging/Ehi2GretlAdapter.java#L9-L23
[db-helper]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/util/DbConnector.java#L38-L85
[shp]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/Gpkg2ShpStep.java
[shp-descriptors]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/Gpkg2ShpStep.java#L87-L184
[shp-writer]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/Gpkg2ShpStep.java#L190-L255
[dxf]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/Gpkg2DxfStep.java#L19-L240
[new-exporter]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/in/db/tocache/Exporter.java
[new-derivator]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/cache/derivedformats/SingleTransferDerivator.java
[schema-copy]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/in/db/schemacopy/CopyOperation.java
[new-mapper]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/out/metainfo/table/Mapper.java#L61-L70
[new-mapper-test]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/test/java/ch/so/agi/gretl/steps/publisher/out/metainfo/table/MapperTest.java
[new-validator]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/cache/validation/CacheValidator.java
[new-zipper]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/publisher/cache/pack/Zipper.java
[test-file]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/test/java/ch/so/agi/gretl/steps/AbstractPublisherStepOldTest.java
[test-partial]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/test/java/ch/so/agi/gretl/steps/AbstractPublisherStepOldTest.java#L267-L334
[test-db]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/test/java/ch/so/agi/gretl/steps/PublisherStepOldDb2LocalTest.java
[test-simi]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/test/java/ch/so/agi/gretl/util/SimiSvcClientTest.java
[test-remote]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/test/java/ch/so/agi/gretl/steps/PublisherStepOldFile2RemoteTest.java
[test-integration]: https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/integrationTest/java/ch/so/agi/gretl/jobs/PublisherTestOld.java
[job-schiesslaerm]: https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/afu_schiesslaerm_pub/build.gradle#L93-L148
[job-schiesslaerm-readme]: https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/afu_schiesslaerm_pub/readme.md
[job-dm01-so]: https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/0_dm01_so/build.gradle#L72-L119
[job-dm01-ch]: https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/1_dm01_ch/build.gradle#L6-L32
[job-dm01-mopublic]: https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/2_mopublic/build.gradle#L6-L59
[job-dm01-readme]: https://github.com/sogis/gretljobs/blob/7af5ff9582a1c55a5e0b3ad8703d4a718d42a6b8/agi_av_dm01_mopublic_pub/readme.md
