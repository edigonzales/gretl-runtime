# 09 — Tests und Abnahme

**Version:** Spezifikationsvorschlag 4.1, 18. Oktober 2026.  
**Normativ für:** den Nachweis der jeweils angebotenen Lieferstufe. Dieses Kapitel beschreibt noch auszuführende Tests; es berichtet keine bereits bestandene Runtime- oder Bibliotheksqualifikation.

## 1. Abnahmeprinzip

### TEST-001 — Umfang und Orakel

Die Prüfungen folgen den Stufen aus [01](01-zielbild-und-mvp.md): A für den allgemeinen Runner, B für die reale Fachintegration, C für optionale Job-JARs. Publisher-Implementierung und -Auslieferung sind ausdrücklich nach MVP verschoben. Seine unverändert erhaltenen Fälle TC-P01 bis TC-P09 stehen in Abschnitt 9 als Abnahme eines späteren Ports und sind kein Gate für A, B oder C. Die allgemeinen Erkenntnisse aus dem Audit werden bereits durch unabhängige Runtime-, SQL- und INTERLIS-Fälle geprüft.

Jeder Fall nennt eine beobachtbare Erwartung. Eine vorhandene ZIP-Datei, ein Exitcode 0 oder ein erfolgreich geladener Klassenname genügt nur dort, wo genau dies der Vertrag ist. Archive werden gelesen, SQL-Effekte über eine unabhängige Verbindung geprüft und CRS-Ergebnisse mit einem unabhängigen Wert verglichen.

Eine Tabellenzeile ist kein Auftrag zu einer Testklasse. Der Coding-Agent darf verwandte Orakel in einem Fixture prüfen. Tests, die nur die Implementierung spiegeln, sind zu vermeiden. Die entscheidenden Prüfungen laufen gegen die gebaute Distribution beziehungsweise das tatsächlich erzeugte Job-JAR.

### TEST-002 — Echte Bibliotheken und lokale Fixtures

Unit-Tests verwenden kontrollierte Testoperationen für seltene Fehler und Zustandsübergänge. Integrationsprüfungen verwenden reale HTTP-, PostgreSQL-, INTERLIS- und GeoTools-Pfade für den zugesagten Umfang. Ein Testdouble für Publisher belegt nicht dessen Portierung; ein echter Publisherlauf ersetzt nicht die isolierte Prüfung des Cores.

Tests verwenden temporäre Verzeichnisse, einen lokalen HTTP-Server und eine isolierte PostgreSQL-Instanz. Es gibt keine Produktionszugänge oder produktiven Veröffentlichungspfade. Testberichte nennen Versionen und enthalten keine Secrets.

### TEST-003 — Unabhängigkeit des Cores

Die Runtime muss mit einer kleinen unabhängigen Operation vollständig funktionieren, wenn Publisher, GeoTools und INTERLIS gar nicht vorhanden sind. Sie darf weder implizite Fachkonfiguration verlangen noch aufgrund einer Fachbibliothek andere globale Fehlerausgänge erzeugen.

Dies ist ein ausdrückliches Architekturorakel der Revision. Eine zentrale Registry mit nur umbenannten Publikationspflichten würde dieses Orakel verfehlen, auch wenn ihre Java-Typen neutral benannt wären.

## 2. Fixturekatalog

### 2.1 Allgemeine Operationen

Ein lokaler HTTP-Server liefert Token-JSON, Katalog-JSON und eine GZIP-Datei mit bekannten Nutzdaten. Die vom Katalog gelieferte URL enthält eine zufällige Kennung, die vor dessen Antwort unbekannt ist. Der Server zeichnet Methoden, Aufrufreihenfolge und Aufrufzahlen auf. Erfolgs- und Fehlerantworten sowie ein abbrechbarer blockierter Request sind steuerbar.

Archive enthalten eine kleine Datei mit festem erwarteten Inhalt. Negative ZIPs enthalten Pfadtraversal, absolute Pfade, kollidierende Namen oder ein beschädigtes Ende. Eine Sentinel-Datei ausserhalb des vorgesehenen Ziels darf niemals verändert werden.

Die PostgreSQL-Instanz enthält zwei kleine Tabellen. Ein voller UTF-8-Scripttext enthält ein Semikolon innerhalb eines Strings und einen zulässigen Dollar-Quote-Block. Ein zweiter Scripttext löst einen Fehler aus. Eine eigene Kontrollverbindung prüft Commit und Rollback. PreparedStatement-Werte umfassen Umlaute, Apostroph und null. Die Fixtures verwenden keine eigene Top-level-Transaktionssteuerung.

### 2.2 INTERLIS

Ein kleines lokales Modell `GretlMvpFixture_V1` und passende XTF-Dateien decken den angebotenen Modellumfang ab. Sie enthalten beispielsweise:

| Klasse | Erwartete Daten |
|---|---|
| Site | Drei Punkte mit festen IDs und Koordinaten `(2600000.125,1200000.25)`, `(2600010,1200000)`, `(2600010,1200010)`. |
| Site-Attribute | Namen `Äpfel & Öl`, `Mühle`, `Test`; optionales Feld fehlend beziehungsweise gesetzt; Zahlen 12.5, 0 und −3.25. |
| Road | Linie über `(2600000,1200000)`, `(2600010,1200000)`, `(2600010,1200010)`, Länge 20 m. |
| Area | Rechteck mit Kantenlänge 10 m, Fläche 100 m². |

Das Modell wird so definiert, dass die positiven Werte gültig sind. Negative Varianten verletzen genau eine Regel, enthalten ein beschädigtes XTF oder haben ein fehlendes Modell. Das Fixture behauptet keine ITF-, Bogen-, Custom-Function- oder vollständige INTERLIS-Unterstützung.

Für den späteren schmalen Publisher-Port werden zusätzlich tatsächlicher Archivinhalt, Modelle unter `meta/` und das Datumsformat gegen [05](05-publisher-zielvertrag.md) geprüft. Ein erwartetes Outputfile wird nicht während desselben Tests vom zu prüfenden Publisher als eigene Referenz erzeugt.

### 2.3 Fehleroperationen

Kleine unabhängige Operationen bilden die allgemeinen Fälle ab: Wert liefern, erfolgreich ohne Wert enden, Request ablehnen, Exception werfen, eine Datei schreiben und anschliessend scheitern, im Cleanup scheitern und Cancellation kooperativ beobachten.

Die Operation „schreiben und dann scheitern“ ist bewusst kein Publisher. Sie beweist, dass die Runtime Seiteneffekte nicht zurückrollt, keine automatische Wiederholung ausführt und normal behandelte Exceptions nicht heimlich wieder zum Runfehler macht.

### 2.4 Secrets

Ein Sentinel-Secret wird für den jeweiligen Test erzeugt. Reguläre Logs, Runberichte, Paketdeskriptoren und Prozessargumente werden auf ungewollte Offenlegung geprüft. Eine absichtlich heruntergeladene private Tokenantwort darf das Token enthalten; sie wird nicht als öffentliches Diagnoseartefakt freigegeben.

Die Tests prüfen produktverantwortete Ausgabewege. Sie behaupten keine Sandbox gegen einen Job, der bewusst einen entnommenen Secretwert ausgibt.

Die additive Klartextquelle gemäss ADR-4.1-PLAINTEXT wird mit Environment- und Dateiquellen am gemeinsamen Parser-/Resolververtrag geprüft: genau eine Quelle, nur Stringwerte, unveränderte Klartextwerte einschliesslich leerer Strings und Registrierung erst beim Zugriff. Script- und Job-JAR-Nachweise prüfen Aliasverwendung, maskierte Quellenobjekte, Konsole, Ereignisdatei und Runbericht mit einem Sentinelwert. Dabei werden Rohproduktsenken vor der nachträglichen Maskierung durch Testhilfen ausgewertet. Der Image-Testadapter muss Klartextwerte auch dann unverändert transportieren, wenn sie wie Pfade oder URLs aussehen; dieser Vertragstest benötigt keinen Containerstart.

## 3. Allgemeine Runtime — Lieferstufe A

### 3.1 Definition und Plan

| Fall | Szenario | Orakel |
|---|---|---|
| TC-R01 | Ein Ziel mit transitiven Vorgängern | Genau diese Hülle läuft einmal; unabhängige Tasks bleiben unselektiert. |
| TC-R02 | Mehrere gleichzeitig freie Tasks | Stabile Registrierungsreihenfolge. |
| TC-R03 | `after` auf unselektierten Task | Der Vorgänger wird nicht in den Plan gezogen. |
| TC-R04 | `after` zwischen ausgewählten Tasks | Reihenfolge eingehalten; kein Resultatzugriff daraus erlaubt. |
| TC-R05 | Doppelte ID, unbekannte Referenz, Abhängigkeitszyklus | Konkrete Diagnose vor Taskarbeit, Code 2. |
| TC-R06 | Zyklus erst durch ausgewählte `after`-Kante | Kantenarten und Zyklus im Fehlerbericht. |
| TC-R07 | `check` / `plan` mit markierenden Taskclosures | Keine Markierung aus Taskkonfiguration, Body oder Cleanup. |
| TC-R08 | Top-level-Markierung im Script | Beim Laden sichtbar; dokumentierte Grenze von `check`/`plan` bestätigt. |
| TC-R09 | Taskregistrierung im laufenden Body | Verständlicher Fehler, kein teilweise veränderter DAG. |
| TC-R10 | Keine CLI-Ziele und keine Defaults | Klarer Planfehler; kein Raten eines letzten Tasks. |

### 3.2 Späte Werte und eigene Tasks

| Fall | Szenario | Orakel |
|---|---|---|
| TC-D01 | Token → Katalog → ermittelte Download-URL | Genau die erst später bekannte URL wird angefragt; Reihenfolge serverseitig belegt. |
| TC-D02 | Konfigurationsclosure mit Aufrufzähler | Null Auswertungen bei Plan und genau eine beim Taskstart. |
| TC-D03 | `when=false` | Kein Builder-/Handleraufruf, regulärer Skip. |
| TC-D04 | Action-null, explizite Void-Operation und unerwartetes null eines nicht-Void-Handlers | Die ersten beiden sind SUCCEEDED ohne Wert; `result` scheitert und Optional ist leer. Nicht-Void-null ergibt dagegen `NULL_OPERATION_RESULT`, Code 10. |
| TC-D05 | Resultat eines übersprungenen Tasks | Direkter Zugriff scheitert; Optional ist leer. |
| TC-D06 | Unbekannte/fremde Referenz oder Zugriff ohne dependsOn | Fehler, auch wenn der andere Task zufällig früher lief. |
| TC-D07 | Action mit Schleife und `ctx.run` | Synchron, geordnete Resultate, keine neuen DAG-Knoten. |
| TC-D08 | Unbekanntes Eingabefeld / fehlendes Pflichtfeld | Präzise Diagnose mit Task, Quelle, Feld und Korrekturhilfe vor dessen Enginearbeit; kein still neu angelegtes Scriptproperty. |
| TC-D09 | `return` in Konfigurationsclosure | Kein Skip; resultierender Request wird normal validiert. |
| TC-D10 | Separat gebautes FileSize-JAR in Testdistribution | Provider entdeckt; dieselbe `=`-Syntax, passende Hilfe und richtige Dateigrösse ohne Runnerneubuild. |
| TC-D11 | Doppelte Operation-ID oder DSL-Symbol | Fehler nennt beide Herkunftsbibliotheken. |
| TC-D12 | Vollständige Beispiele des zugesagten MVP aus 04 und 13 | Als echte Groovy-Scripts gegen die Runtime ausgeführt; Map-first-Aufrufe, `=` und Delegation funktionieren. |
| TC-D13 | IDs statt vom Autor verwalteter TaskRefs, Einzelstring und Liste bei dependsOn | Identischer Plan; String wird als genau eine ID behandelt; Stringresultate durchlaufen dieselben Zugriffsprüfungen. |
| TC-D14 | Java-Resultat mit ausdrücklich deklarierten Ausgängen | `result('download').file` liefert den korrekten Wert unabhängig von Bean-/Record-Namenskonventionen; unbekannter Ausgang nennt verfügbare Felder; keine automatische Resultatserialisierung. |
| TC-D15 | Frische Taskblockfassade und Helper-/Feldauflösung | Keine Owner-Fallbacks, keine geteilten Builder oder globalen currentTask-Werte; unbekannte Felder und unzulässige Metadatenkonflikte scheitern nachvollziehbar. Lexikalische Variablen werden nicht als durch Delegation umgebogen ausgegeben. |
| TC-D16 | Resultatzugriff im Plan beziehungsweise fehlende dependsOn-Kante | Kein vorgezogener Fachaufruf und keine automatische Kante; bei tatsächlichem falschem Zugriff präziser Fehler mit Korrekturhinweis. |
| TC-D17 | Interpolierter URL-/Headertext und SQL-Textwert im späten Block | Zuweisung erzeugt den richtigen festen Java-String nach den Vorgängern; keine Konfigurationsauswertung bei Plan und kein stilles toString beliebiger Nichttextwerte. |

Ein blosses Parsen der Codefences erfüllt TC-D12 nicht. Die vollständigen MVP-Beispiele werden als ausführbare Testressourcen gepflegt; Änderungen am öffentlichen Vertrag verändern Beispiel und Test gemeinsam. Erklärende Teilausschnitte werden in passende Fixtures eingebettet. Ausdrücklich als nach MVP markierte Publisher-Beispiele werden erst beim späteren Publisher-Port ausführbar abgenommen; sie erzeugen keine versteckte Implementierungspflicht im MVP.

### 3.3 Fehler, Stop und Freigaben

| Fall | Szenario | Orakel |
|---|---|---|
| TC-E01 | Unbehandelte Custom-Task-Exception | Task FAILED, Code 10, keine nachfolgenden normalen Tasks. |
| TC-E02 | Unabhängige Operation schreibt Datei und scheitert | Datei kann existieren, ein Aufruf, kein Rollback- oder Retryversprechen. |
| TC-E03 | Dieselbe Exception in Action bewusst gefangen | Action kann SUCCEEDED sein; innere fehlgeschlagene Invocation bleibt beobachtbar. |
| TC-E04 | ERROR-Diagnostic ohne geworfene Exception | Kein aus Severity abgeleiteter Taskfehler. |
| TC-E05 | Operation erfolgreich, Nachprüfung in Action fehlerhaft | Action FAILED; vorherige Wirkung nicht als ungeschehen ausgegeben. |
| TC-E06 | `finishJob` nach erfolgreich festgestellter leerer Liste | NO_DATA, Code 0, spätere Tasks NOT_RUN, Cleanup läuft. |
| TC-E07 | Finishsignal versehentlich gefangen | Kein weiterer `ctx.run`-/Taskstart; Stopanforderung bleibt wirksam. |
| TC-E08 | Lokales Cleanup scheitert nach gewünschtem NO_DATA | Echter Fehler bleibt sichtbar, Code 10. |
| TC-E09 | Primärfehler und mehrere Cleanupfehler | Primärursache erhalten; alle erforderlichen Freigaben versucht. |
| TC-E10 | Job-Cleanup scheitert nach sonst erfolgreichem Run | Run FAILED, abgeschlossene Taskhistorie unverändert. |
| TC-E11 | Kooperative Cancellation/Deadline | Code 20, keine neue Taskarbeit; spätere Cleanupfehler sekundär. |
| TC-E12 | Fehler vor nachfolgender Cancellation | Fehler wird nicht durch Cancel als Primärursache ersetzt. |
| TC-E13 | Producerdatei im Workspace | Für Consumer nach Producerende lesbar; Aufbewahrung erst am Runende angewandt. |
| TC-E14 | Erst in einer späten Closure ausgeführter Zugriff auf nicht deklarierten Parameternamen | Code 10 mit Konfigurationsdiagnose und Ausführungsphase; Abgrenzung zu frühen Pflicht-/Transportfehlern aus TC-C10. |
| TC-E15 | Lauf ohne Publisher-/INTERLIS-/GeoTools-JARs | Allgemeine Runtime und unabhängige Tasks weiterhin funktionsfähig. |

Ein Test einer blockierten nicht kooperierenden Bibliothek darf die JVM von aussen beenden und die dokumentierte Grenze zeigen. Er darf nicht einen kontrollierten Code 20 oder vollständiges Cleanup nach SIGKILL verlangen. Eine solche zusätzliche Prozessprüfung wird nur aufgenommen, wenn die Betriebseinbettung sie tatsächlich braucht.

## 4. Allgemeine Fachoperationen und CLI — A

| Fall | Szenario | Orakel |
|---|---|---|
| TC-O01 | HTTP GET/POST im lokalen Fixture | Methode, Header, Body, Status und geschlossene Zieldatei stimmen. |
| TC-O02 | HTTP-Fehler, Byte-Limit oder Verbindungsabbruch | Kein erfolgreiches Resultat; POST wird nicht automatisch wiederholt. |
| TC-O03 | ZIP und GZIP mit bekanntem Inhalt | Inhalte vollständig und korrekt; Streamabschluss geprüft. |
| TC-O04 | Traversal, Symlink, Namenskollision, Grösse oder defektes Archiv | Fehler; externe Sentinel-Datei unverändert. |
| TC-O05 | SQL-Scripts mit Semikolon im String und Dollar-Quote | Korrekte Ausführung des vollen Scriptvertrags; kein Regex-Splitting. |
| TC-O06 | SQL-PreparedStatements mit Apostroph/null | Datenwerte korrekt gebunden; kein Templateersatz. |
| TC-O07 | SQL-Fehler beim zweiten Statement | Kontrollverbindung sieht den zugesagten Rollback, keine halbe Erfolgsmeldung. |
| TC-O08 | Connection-/Commitfehlerdiagnose | Eigene SQL-Information im allgemeinen Fehlerbericht; keine SQL-Sonderexitcodes. |
| TC-C01 | Jobdefault, Runtimeparams, `--params-file` und `--param` | Definierter Vorrang, ausdrücklich leerer optionaler Text erhalten, Trennung am ersten `=`. |
| TC-C02 | Doppelte Keys, fehlendes `=`, Nichtstring-JSON | Code 2, keine Taskarbeit. |
| TC-C03 | Jobroot, Start-CWD, Parameterdatei und Configordner verschieden | `file`, deklarierter `path`-Parameter, fortgeschrittener `ctx.path`, `workPath` und Secretdateipfad gemäss Vertrag. |
| TC-C04 | Allgemeines `gretl --help` / `gretl --version` | Kein Jobladen, keine Provider-/Engineinitialisierung. |
| TC-C05 | Secret und dynamischer Token | Kein automatisches Logging oder Serialisieren des Werts. |
| TC-C06 | Neues Workdir, Erfolg, Fehler, `keepWork` | Dateien bis Runende verfügbar, korrekte Aufbewahrung, keine externen Fachdateien gelöscht. |
| TC-C07 | Fertige Distribution ohne Maven/Gradle und Code-Netzzugang | Scriptjob und eigene Taskbibliothek funktionieren. |
| TC-C08 | Runbericht und Scriptfehler | Status, Ursache, Datei und Zeile verständlich; keine Publisherfelder im Schema. |
| TC-C09 | Start ohne Konfigurationsdatei | Defaults aus OPS-013 und deklarierte Jobdefaults, eindeutige Berichte und keine Runverzeichniserzeugung bei Hilfe/Plan. |
| TC-C10 | Fehlender Pflichtparameter, falscher Boolean/Integer/Path, unbekannter CLI-/Dateiparameter | Code 2 vor Taskarbeit; Aufgabe/Parameter verständlich benannt; jobweite Pflicht auch bei Teilziel. Vorhandene ungültige Werte scheitern auch bei check/plan, fehlende Pflichtwerte bleiben dort offen. |
| TC-C11 | Deklarationsfehler und optionale Werte | Unbekannter Typ, doppelte Namen, fehlende Beschreibung, required plus defaultValue und inkompatibler Default sind Definitionsfehler; fehlendes Optional ohne Default ist null, leerer Text ersetzt keinen Default. |
| TC-C12 | Gemeinsame Runtimeparams und Unveränderlichkeit | Undeklarierter gemeinsamer Default wird ignoriert; ausdrücklich unbekannte CLI-/Dateinamen scheitern; kein Zugriff auf undeclared Defaults; typisierte Sicht über den Lauf unverändert. |
| TC-C13 | Jobbezogene Hilfe ohne Werte und Fachzugänge | Deklarierte Typen, Beschreibungen, Defaults und Ziele sichtbar; keine Taskclosure, kein Handler, Secretzugriff, Cleanup oder Runbericht; separate Toplevelmarkierung belegt die ehrliche Registrierungsgrenze. |
| TC-C14 | operations und operations FileSize | Nur tatsächlich verfügbare Typen; gemeinsame Feld-/Ergebnishilfe und Beispiel auch für eigenes JAR. Publisher/XtfToGpkg im MVP nicht registriert; nach hinzugefügter qualifizierter Testbibliothek wächst der Katalog ohne Coreänderung. |

Eine verbindliche Prüfung eines unklaren realen DB-Commits braucht einen kontrollierten Verbindungsfehlerharness. Im ersten SQL-Adapter genügt zur Freigabe seines tatsächlich zugesagten Transaktionsumfangs die Kombination aus realem Commit/Rollback und Fehlerabbildung; es wird keine neue Wiederherstellungsgarantie behauptet. Zusätzliche Fault-Injection wird nur für eine entsprechend angebotene Zusage Pflicht.

## 5. Fachintegration — Lieferstufe B

### 5.1 INTERLIS

| Fall | Szenario | Orakel |
|---|---|---|
| TC-I01 | Gültiges lokales Modell und XTF | Reale Modellkompilation und Validierung erfolgreich. |
| TC-I02 | Fehlender Pflichtwert / ungültiges XTF | Reale Validierungsdiagnose, allgemeiner Taskfehler. |
| TC-I03 | Fehlendes Modell | Kein stiller Ersatz; präzise Diagnose mit Modellbezug. |
### 5.2 GeoTools

Die GeoTools-Probe bleibt ein kleiner **verbindlicher Entwicklungs-/CI-Nachweis in M0/B**, in einem gesonderten Testprofil. Sie läuft mit derselben festgelegten Java-/Groovy-Version und dem qualifizierten Runtime-/Taskbestand, ergänzt um den ausdrücklich inventarisierten GeoTools-Testbestand aus Kapitel 07. Ein eigener Testprozess ist kein Produktworkerprotokoll, der Testharness kein öffentlicher Standardtask. Die zusätzlichen Testdependencies werden nicht allein für diesen Nachweis in die Standarddistribution aufgenommen. Ein erfolgreicher Test belegt die geprüfte Kombination, aber noch keinen Publisher-Port und keinen bereits ausgelieferten GeoTools-Task.

Sobald ein Release tatsächlich eine GeoTools nutzende Fachfähigkeit anbietet, laufen die einschlägigen Fälle zusätzlich gegen **deren fertigen Distributionsbestand**. Dann dürfen fehlende Fachdependencies nicht durch den Testclasspath ergänzt werden. Der Unterschied zwischen vorbereitender Kompatibilitätsprobe und Abnahme einer gelieferten Fähigkeit wird im Bericht ausgewiesen.

Sie schreibt und liest zunächst eine Punkt-Shapefile. Benutzer-IDs, Punktgeometrien, Umlaute, fehlende Werte und Zahlen werden mit bewusst festgelegtem SHP-Schema geprüft. Bekannte Feldbreiten und Formatverluste sind im Expected-Fixture dokumentiert. Weitere Geometrietypen, Datumsabbildungen und Formate folgen erst bei einer entsprechenden angebotenen Taskfähigkeit; die umfangreichere INTERLIS-Fixturebeschreibung erzeugt keine zusätzliche SHP-Pflicht.

Ein unabhängiger grober CRS-Kontrollpunkt ist:

| EPSG:2056 | Erwartete geografische Referenz |
|---|---|
| E = 2’700’000 m, N = 1’100’000 m | Länge 8.7304972222°, Breite 46.0441305556° |

Die Quelle ist das [swisstopo-Dokument zu Näherungsformeln, Dezember 2016, Abschnitt 2](https://www.swisstopo.admin.ch/dam/en/sd-web/KLRCX9XIdXDu/ch1903wgs84-EN.pdf). Die Dezimalwerte sind aus den darin angegebenen Winkeln umgerechnet. Für diesen technischen Integrationscheck gilt `0.00005°` pro Komponente bei ausdrücklich Longitude/Latitude. Der Test dokumentiert die tatsächlich gewählte Transformationsoperation. Dies ist keine Zusage vermessungstechnischer Genauigkeit.

| Fall | Szenario | Orakel |
|---|---|---|
| TC-G01 | SHP schreiben und nach Schliessen lesen | Definierte Objektzahlen, Werte, Geometrien und CRS erhalten. |
| TC-G02 | EPSG:4326 auflösen und Referenzpunkt transformieren | Innerhalb der Toleranz; eine Achsenvertauschung wird nicht akzeptiert. |
| TC-G03 | Ausgabe ausdrücklich in geografischem CRS | Keine pauschale LV95-PRJ; geografische Werte und CRS stimmen. |
| TC-G04 | Datastores/Streams geschlossen | Dateien wieder lesbar und entfernbar. |
| TC-G05 | Benötigter Provider im negativen Testpaket entfernt | Fehler statt ungeprüftem Ersatz-CRS. |

Ein GPKG aus ili2gpkg ist kein Beleg für diese GeoTools-Fähigkeit. Umgekehrt verlangt diese Probe keinen SHP-Publisher. Die Versionsgates PLAT-01 bis PLAT-05 aus Kapitel 07 unterscheiden die vorbereitende Probe vom tatsächlich angebotenen Bestand.

## 6. Job-Fat-JAR — Lieferstufe C

| Fall | Szenario | Orakel |
|---|---|---|
| TC-J01 | Derselbe Job direkt und aus JAR mit zwei Parametersätzen | Gleiche fachliche Wirkung und späte Resultatauswertung. |
| TC-J02 | Toplevel-Schreibmarke im Job beim Paketbuild | Keine Markierung beim Packen; Script wird erst bei Start instanziiert/ausgeführt. |
| TC-J03 | JAR auf Rechner ohne GRETL/Maven/Gradle/Artefaktcache | Start mit Java allein; kein Download von Codeabhängigkeiten. |
| TC-J04 | JAR verlagert, Start-CWD geändert | SQL-/ILI-Ressourcen unter `file`, externe Eingabe als deklarierter path-Parameter korrekt. |
| TC-J05 | Eigene Task-JARs und Groovy-Closure-/Helferklassen | Alle benötigten Klassen enthalten und ausführbar. |
| TC-J06 | JDBC, Groovy-Extension-Modul und gegebenenfalls mitgelieferte GeoTools-Fähigkeit | Tatsächlich nötige Services/Registrierungen im fertigen JAR vorhanden; GeoTools nur beim dafür angebotenen Paketbestand. |
| TC-J07 | Verschiedene gleichnamige Klassen/Ressourcen | Build meldet Konflikt und Herkunft, kein stilles Überschreiben. |
| TC-J08 | Symlink/Traversal oder fehlende Ressource im Deskriptor | Frühe Ablehnung; kein Einsammeln nicht deklarierter Dateien. |
| TC-J09 | Fehler, kontrollierter Cancel, `--plan`, `--help` | Gleiche allgemeine Semantik; jobbezogene Hilfe lädt die Definition und zeigt dieselben Parameter, führt aber keine Taskarbeit oder Secretauflösung aus. `--version` bleibt vor Jobladen. |
| TC-J10 | Paketdeskriptor und Inventar | Feste Versionen/Hashes; keine konkreten Parameter eines späteren Laufs oder Secrets eingebrannt. Deklarierte Jobdefaults bleiben Teil des Jobcodes; kein zweites Parameterschema. |
| TC-J11 | Zweiter Start mit gleichem JAR | JAR unverändert, neuer Runworkspace, keine wiederverwendete Scriptinstanz. |

TC-J06 verwendet nur tatsächlich enthaltene Taskfähigkeiten. Der erste Paketierungsnachweis umfasst ein minimales SQL-Paket und ein Paket mit den lokalen INTERLIS-Ressourcen. Diese müssen nicht für eine rein vorbereitende Probe GeoTools enthalten. Bei einer später angebotenen GeoTools-Fachfähigkeit wird zusätzlich ihr tatsächlicher JAR-Bestand mit dem passenden Harness geprüft. Ein vorgezogener GeoTools-JAR-Versuch ist zulässig, bleibt aber ausdrücklich ein zusätzlicher technischer Versuch und kein Blocker des ersten C-Meilensteins. Ein erfolgreiches minimales JAR beweist umgekehrt keine noch ungeprüfte schwere Fachbibliothek.

Eine Laufzeitmessung darf C begleiten. Ohne konkrete Zielvorgabe wird keine erfundene Millisekundenschwelle als Releasegate benutzt. Native Image ist keine Voraussetzung dieser Tests.

## 7. Migrationsbeispiele und Rückverfolgbarkeit

| Beobachtetes Jobmuster | Neuer Testnachweis |
|---|---|
| Token und später ermittelte Download-URL im Neophytenjob | TC-D01, TC-C05. |
| Umcodierung/Trennzeichenarbeit im MOCheckSO-doLast | Action mit Download und Umformung, vollständig geprüfter Textoutput. |
| Dataset-Vorprüfung vor INTERLIS-Arbeit | Builder- oder Vorprüfungsfehler vor Engineaufruf, TC-D08. |
| Leere Dateiliste beendet weitere Arbeit | TC-E06 bis TC-E08. |
| `publishedRegions` für folgenden Publisher | Noch kein Portbeleg; erst mit tatsächlich implementierter Regionenfähigkeit. |

Die commitgebundenen Quelllinks stehen in Kapitel 04 und 11. Diese Tabelle behauptet keine automatische Migration aller Hooks oder der gesamten Produktionsjobs.

## 8. Abnahmebericht und Freigabe

Der Bericht enthält verwendete Quellcommits, exakte JDK-/Groovy-/Bibliotheksversionen, Distribution-/JAR-Hashes, Plattform und tatsächlich ausgeführte Fälle. Er nennt auch übersprungene Integrationsprüfungen und den Grund. Ein vorhandener Testcode ist kein Nachweis eines grünen Laufs.

| Freigabe | Erforderliche Nachweise |
|---|---|
| A: allgemeiner Runner | Runtime-, DSL-, Fehler-, CLI- und allgemeine Taskfälle; ergänzende API-/Klassendesign-/Groovy-/MVP-Loggingfälle aus Abschnitt 11; unabhängiges Erweiterungs-JAR und geprüfte Fachanwenderrezepte. |
| B: GRETL-Script-MVP | A plus echter IliValidator auf dem Releasebestand und gesonderte, inventarisierte GeoTools-Kompatibilitätsprobe. Keine unbenutzten GT-JARs allein dafür in der Distribution; keine Publisher-Implementierung und keine TC-P-Pflicht. |
| C: ausführbare Job-JARs | Paketierungsfälle für minimale und tatsächlich angebotene schwere Bibliotheksbestände. |
| Publisher nach MVP | Eigener späterer Liefermeilenstein mit TC-P01 bis TC-P09 und Zielvertrag 05; unabhängig von C und Native. |
| Zusätzliche Fachfähigkeit | Ihre eigenen Inputs, Outputs, Fehler und Betriebsgrenzen. |
| Native eines festen Jobs | Späterer gesonderter Nachweis nach Kapitel 08; blockiert A/B/C nicht. |

Nach einem Upgrade werden die von der Änderung betroffenen Nachweise wiederholt; ein neuer Task oder eine neue Bibliothekskombination qualifiziert sich nicht durch alte Berichte. Die Prüfung wird nur erweitert, um eine konkrete neue Zusage oder ein verbleibendes Risiko zu klären.

## 9. Publisher — aufbewahrte Abnahme nach MVP

Dieser Abschnitt erhält die fachliche Portierungsgrundlage vollständig. **Seine Fälle gehören nicht zum MVP-Releasegate.** Das MVP liefert keinen Publisherprovider, kein Publisher-JAR und keine nur für dessen noch ausstehende Umsetzung benötigten Dependencies. Publisher bleibt ein wichtiger späterer gewöhnlicher Custom-Task. Seine Abnahme wird aktiviert, sobald diese Fähigkeit tatsächlich als eigener Liefermeilenstein umgesetzt und angeboten wird.

| Fall | Szenario | Orakel |
|---|---|---|
| TC-P01 | Lokale Einzel-XTF-Publikation | Richtige aktuelle ZIP-Datei, tatsächlicher Transferinhalt, validation.log und Modelle unter `meta/`. |
| TC-P02 | Optionale validation.ini | Richtiger Inhalt im Archiv, tatsächliche Validatoranwendung der geprüften Option. |
| TC-P03 | Zwei Publikationen mit kontrollierten Tagen | Bestehende Tages-/Historiensemantik entspricht Kapitel 05. |
| TC-P04 | Fehler vor Ersetzungsphase | Vorheriger aktueller Stand unverändert; keine behauptete Gesamtrückabwicklung späterer Fehler. |
| TC-P05 | `userFormats=true` oder nicht unterstützte Quelle | Frühe klare Ablehnung, kein scheinbar erfolgreicher Teiloutput. |
| TC-P06 | Fehler beim Archivabschluss / Ressourcenfreigabe | Fehler propagiert; vorhandener Primärfehler bleibt erhalten. |
| TC-P07 | Publisherresultat an normalen Consumer | Normale TaskRef-/dependsOn-Regeln, kein besonderer Schedulerpfad. |
| TC-P08 | Publisherexception bewusst in Action behandelt | Dieselbe Catch-Semantik wie TC-E03. |
| TC-P09 | Zwei Aufrufe in einem Jobprozess | Keine übernommenen Builderwerte, Loggerleaks oder offenen Streams. |

TC-P03 prüft mindestens eine erste Publikation, Ersatz am selben Tag und eine neue Publikation am Folgetag mit vorherigem Stand in der dokumentierten Historie. Eine testseitig injizierte Clock dient dafür; es gibt keine neue Datumsoption im CLI.

Die Tests verlangen weder neue technische Generationen noch Outbox, Reconcile, eine Metadatensenke oder atomare Pointer. Der Port wird für einen Writer pro Ziel und unveränderte Eingabedateien während des Aufrufs freigegeben. Wer später konkurrierende Writer, eine neue Historie oder crashsichere Ersetzung anbietet, ergänzt diese Tests im Publisher-Modul.


## 10. Kurze Abnahme mit Fachanwendern

Die Empfehlung für die vereinfachte DSL ist eine begründete Entwurfsentscheidung, keine bereits durchgeführte Nutzerstudie. Vor Freigabe der Anwenderoberfläche wird mit wenigen tatsächlichen Fachanwendern geprüft, ob die Einstiegsvorlagen aus [13](13-einstieg-fuer-jobautoren.md) ihre beabsichtigte Aufgabe erfüllen. Zwei bis drei Personen ausserhalb des Runtime-Entwicklungsteams genügen für diese erste qualitative Prüfung; sie belegen keine statistische Überlegenheit gegenüber Gradle.

| Fall | Aufgabe mit lokaler Testumgebung | Beobachtbare Erwartung |
|---|---|---|
| TC-U01 | SQL-Rezept kopieren und die vorgegebene Scriptdatei wechseln | Richtige Datei und konfigurierte Datenbank werden ohne Änderungen an SPI, Imports oder Runtimecode gewählt. |
| TC-U02 | ZIP-Rezept auf andere Quelle und anderes Ziel anpassen | Hilfe und Parameter reichen; Herkunft der Download-Datei und Zweck von dependsOn können in eigenen Worten erklärt werden. |
| TC-U03 | Einen Feld- oder Parametertippfehler beheben | Meldung nennt den betroffenen Schritt beziehungsweise Parameter und unterstützt die Korrektur ohne Java-Stacktracekenntnis. |
| TC-U04 | Jobdatei verschieben, externe Eingabe und Standardziel erklären | Unterschied zwischen Jobressource, Eingabepfad, Arbeitsdatei und Startziel ist mit Anleitung nachvollziehbar. |

Die Aufgaben verwenden keine produktiven Datenquellen. Beobachtet werden richtige fachliche Wirkung, benötigte Hilfestellung und Missverständnisse. Es gibt keine erfundene Millisekunden- oder Minutenhürde. Wiederkehrende Blockaden werden vor der Oberflächenfreigabe in Vorlage, Hilfe oder API korrigiert; ein Coding-Agent darf keine erfolgreiche Fachanwenderabnahme behaupten, weil er seine eigenen Beispiele versteht. Fehlende Personenverfügbarkeit wird im Abnahmebericht ausdrücklich als noch offener Nachweis benannt.


## 11. Technische Ergänzungen 4.0: API, Kernklassen und Logging

Diese zusätzlichen Orakel konkretisieren die Findings aus der Spezifikationsprüfung. Bestehende Fixtures dürfen mehrere Orakel gemeinsam prüfen. Tabellenzeilen sind keine Pflicht zu je einer Testklasse. **Referenztests an Dokumentationscode sind von den noch zu implementierenden Produktprüfungen getrennt**, siehe Kapitel 17. TC-P01 bis TC-P09 bleiben vollständig erhalten und nach MVP.

### 11.1 Öffentliche Java-API — A

| Fall | Szenario | Orakel |
|---|---|---|
| TC-A01 | Alle öffentlichen Definitionen aus Kapitel 14 im API-Modul bauen | Auf der Produkttoolchain kompilierbar; kein Gradle/Groovy/SLF4J/OTel/Fachimport in der öffentlichen SPI. |
| TC-A02 | Getrenntes FileSize-JAR über ServiceLoader | Derselbe Provider, `Fields`-Factory und Ergebnisvertrag ohne Runnerneubuild; tatsächliche bekannte Dateigrösse. |
| TC-A03 | InputField-Konverter, Default und falscher Builder | Default und explizite Zuweisung verwenden denselben geprüften Pfad; Fehler statt stiller Konversion oder korruptem Builder. |
| TC-A04 | OperationType-/Diagnostic-Metadaten nach Erstellung verändern | Sammlungen defensiv kopiert; null, Doppelnamen und inkompatible Definitionen werden konkret abgelehnt. |
| TC-A05 | Optionale Parameter und typisierter Contextzugang | Null bleibt bei optionalem Parameter zulässig; falsche Wrapperklasse erzeugt PARAMETER_TYPE_MISMATCH, kein neuer Konverter. |
| TC-A06 | Configmap mit JSON-null und geschachtelten Listen | Tief unveränderliche begrenzte JSON-Struktur; kein unbeabsichtigter NullPointer durch Map.copyOf. |
| TC-A07 | Context ACTIVE/CLOSING/CLOSED | Neue Arbeit nur ACTIVE; bekannte Identität bleibt lesbar; geschlossene Contextdaten nicht für nächsten Task wiederverwenden. |
| TC-A08 | ResourceScope und mehrfach registriertes Objekt | Ein Besitzer, rückwärts und einmalig geschlossen; weitere Ressourcen trotz erstem Fehler geschlossen; kein öffentlicher Gesamt-run.close-Aufruf. |
| TC-A09 | Lazy SecretValue und dynamisches sensitive | Kein Laden durch toString; Maskierung vor kontrollierter Weitergabe; rungebundene Lebensdauer und dokumentierte Limits. |
| TC-A10 | Handlerresultat, Action-null und lazy OutputField | Nicht-Void-null/oberer Typfehler erkannt, Void/Action-null korrekt; Accessor erst bei Zugriff, Producerstatus nicht rückwirkend verändert. |

### 11.2 Kernklassen und Übergänge — A

| Fall | Szenario | Orakel |
|---|---|---|
| TC-K01 | Registryfreeze und verspätete Registrierung | Freeze einmal; spätere Registrierung abgelehnt, Definition und Plan unverändert. |
| TC-K02 | Freie Knoten und unterschiedliche HashMap-Einfügereihenfolge | Kahn-/gleichwertiger Plan bleibt nach Registrierungsindex stabil; nur dependsOn erzeugt Auswahlhülle. |
| TC-K03 | Statusstore vor Beginn, Skip, Void, Wert, Fehler | TaskStatus und ResultSlot unterscheiden alle Fälle; kein Optional allein als Zustandsmodell. |
| TC-K04 | Predicate false und Konfigurationsfehler | False ohne Builder/Handler/Invocation; Konfigurationsfehler besitzt bereits Invocationidentität und Phase configuration. |
| TC-K05 | Handlerwert, danach Closefehler | Wert wird nie als erfolgreiches Producerresultat verfügbar; ursprüngliche Wirkung nicht zurückgerollt. |
| TC-K06 | Innerer Aufruf mit eigenem Konfigurationslog, danach äusserer Log | Neuer Frame vor Requestbereitung; innerer Workspace/Identität korrekt, äusserer Context nach Return/Exception wiederhergestellt. |
| TC-K07 | Finish, echte Fehler, Cancellation und Closefehler in kontrollierter Reihenfolge | Vollständige Tabelle CORE-024 eingehalten; keine Auswertung von Logseverities oder Uhrzeitspekulation. |
| TC-K08 | Mehrere Job-Cleanups nach Erfolg/Fehler/Cancel | Umgekehrte Registrierung, gemeinsames Nachfristbudget, erste echte Ursache erhalten; keine Fachquittierung als generisches Cleanup. |
| TC-K09 | Berichtpfad nach fachlicher Arbeit nicht mehr beschreibbar | Erforderlicher Reportfehler allgemein gemeldet, keine ETL-Wiederholung; vorhandene Primärursache und Fachwirkungen erhalten. |
| TC-K10 | Workspace-Retention und Diagnoseartefakte | Nur eigener Runbereich betroffen; Logs/Reports ausserhalb bleiben; sensible verwahrte Dateien nicht automatisch als öffentliche Artefakte aufgenommen. |

### 11.3 Echte Groovy-Bindungsprobe — A/M2

| Fall | Szenario | Orakel |
|---|---|---|
| TC-GR01 | Map-first Task-/Action-/Parameteraufrufe wie dokumentiert | Echte Groovy-Produktversion ruft die beschriebenen Überladungen auf; nicht bloss AST/Codefence geparst. |
| TC-GR02 | Closure ohne explizite Parameter, mit `{ -> }`, mit `{ ctx -> }`, mit zwei Parametern | 0/1 korrekt ausgeführt, 2 konkret abgelehnt; gespeichertes Original unverändert. |
| TC-GR03 | DELEGATE_ONLY, Setterfehler und lokales def-shadowing | Kein Ownerfallback für unbekannte Settings; gewöhnliche lexikalische Variable bleibt normale Groovyvariable. |
| TC-GR04 | Defaults, Interpolation und mehrfaches Feldsetzen | Einmalige späte Auswertung; feste Java-Strings; letzte vollständige Zuweisung ersetzt vorherige, keine geheime Listenaddition. |
| TC-GR05 | ResultView mit unbekanntem und nullable Feld | Nur dokumentierte Ausgänge; keine private Beanreflexion; NULL des Felds ist nicht fehlendes Producerresultat. |
| TC-GR06 | Zwei gleichnamige Jobs/Tasks nacheinander im Testharness | Frische Definition, Scopes, Binding und Context; keine früheren Ergebnisse oder Loggeridentitäten. |
| TC-GR07 | Tatsächlicher Fehler in Konfigurationszeile statt Registrierungszeile | Richtige Originaldatei und soweit vorhanden Zeile; technische Reflectionhülle darf Ursache nicht verdecken. |
| TC-GR08 | Eigene println-Methode und direkte fremde System.out-Ausgabe | Produktprintln sicher als Usermessage geroutet; fremder Stream ausdrücklich ungerahmt, nicht als Taskstatus oder gültiges JSON ausgegeben. |

### 11.4 Logging und Telemetrie

A ist Pflicht für den generischen MVP; B ergänzt den echten Validatoradapter. C und spätere optionale OTel-Profile gelten nur, wenn sie tatsächlich angeboten werden.

| Fall | Stufe | Szenario und Orakel |
|---|---|---|
| TC-L01 | A | Text und JSON enthalten dieselbe Identität, Severity und Ereignisbedeutung. Jede verantwortete JSON-Zeile ist einzeln parsebar; genau ein LF, korrekt escaped Newline/Steuerzeichen/Umlaut. |
| TC-L02 | A | Hilfe/Planresultat auf stdout, verwaltete Logs auf stderr. Globale Hilfe initialisiert keine Provider/Engines; frühe Fehler bei ausdrücklich gewähltem JSON bleiben strukturiert. |
| TC-L03 | A | Run-/Task-/Invocation-IDs, command_id, event_id und Sequenz konsistent über Konsole und Datei; fehlender Run vor Start nicht durch Dummy-ID ersetzt. |
| TC-L04 | A | Automatische Start-/Terminalevents auch bei Builderfehler, Predicate-Skip und Cleanupfehler; keine vom Custom-Task nachzupflegenden Lifecyclepaare. |
| TC-L05 | A | Innere Fachexception gefangen: innere Invocation FAILED, Action kann SUCCEEDED; ERROR-Diagnostic allein verändert keinen Status und erzeugt keine versteckte Pflicht. |
| TC-L06 | A | Causegraph einschliesslich suppressed, zyklischer Throwableverweise und langer Meldung bleibt begrenzt, verweist nach oben über error_ref und zeigt Kürzung; erste echte Ursache erhalten. Eine unter WARN gefilterte Detailmeldung gilt nicht als auf einer ERROR-Konsole sichtbar; dort muss bei Eskalation erstmals Detail statt unbekannter Referenz erscheinen. |
| TC-L07 | A | Statische Secrets, Lazy-Reveal, dynamischer Token und throwable message in allen verantworteten Senken maskiert; keine Query-/Body-/SQLparameter-/Environmentdumps. |
| TC-L08 | A | Secretkapazität, grosse Diagnose und riesige einzelne Nachricht verletzen keine Speicher-/Recordgrenzen; Verlust/Kürzung ausdrücklich statt still oder rekursiver Loggingfehler. |
| TC-L09 | A | Innerer Context/MDC bei Erfolg und Exception wiederhergestellt. Hintergrund-/nachlaufender Log erhält keine fremde aktuelle Taskidentität; Bridgekontext ohne Besitzer als unattributed. |
| TC-L10 | A | Registry-/Classpathprüfung erkennt mehrere SLF4J-Provider und gegenläufige JUL/SLF4J-Brücken; eine provozierte Librarymeldung wird nicht endlos oder doppelt weitergeleitet. |
| TC-L11 | A | HTTP-Fehler zeigt sichere Origin, Methode, Status, Phase und Bytes; keine Authorization, Query oder Tokenantwort. Langsame/abgebrochene Antwort erzeugt keine fälschliche Erfolgsmeldung. |
| TC-L12 | A | Realer SQL-Fehler zeigt Alias, Scriptidentität/-hash, Eintrag und SQLSTATE soweit verfügbar. Kein kompletter Query-/Parameterdump; unbekannte Treiberposition nicht als erfundene Scriptzeile dargestellt. |
| TC-L13 | B | EHI-/Iox-Event mit vorhandener Exception erhält Cause plus strukturierte Klasse/Zeile/Objektfelder nach Freigabepolitik. Warnungs-/Fehlerzählung gegen echte Engine geprüft. |
| TC-L14 | B | Zweimalige Validatorausführung success/failure: genau eigene Listener installiert/entfernt, kein Verdoppeln und keine weiterhin offenen Reportstreams. |
| TC-L15 | A/B | Zehntausende Fortschritts-/Validierungsereignisse: koaleszierte Ausgabe und dokumentierte Stichproben; Gesamtzähler korrekt, Begrenzungen und fehlende Details im Bericht sichtbar. |
| TC-L16 | A | Heartbeat mit kontrollierter Clock: lebt, aber completed unverändert. Keine Statusverbesserung, kein Liveness-Neustart und kein behaupteter Fortschritt. |
| TC-L17 | A | Kleine testweise gesetzte Dateigrenze/Reserve: Konsole läuft, Datei endet begrenzt, Terminalreserve und logging.fileTruncated sichtbar. Maximaler Recordumfang wird auch mit grossen Metadaten eingehalten. |
| TC-L18 | A | Senke nach Initialisierung I/O-fehlerhaft: begrenzte Ersatzmeldung, logging.degraded, keine Rekursion und kein Fachretry. Initial unbeschreibbarer Pflichtpfad dagegen Code 2. |
| TC-L19 | A | Konsole WARN bei aktivierter Eventdatei: INFO-Lifecycle dort erhalten, kein heimliches vollständiges DEBUG-Archiv. Bibliothekslevel gezielt statt globaler ungeprüfter DEBUG-Eskalation. |
| TC-L20 | A | SIGTERM-/kontrollierter Cancel-Harness und gefüllte Streamausgabe: vorhandene Daten soweit möglich geflusht; SIGKILL-Test erwartet ausdrücklich keinen garantierten Abschluss oder Rollback. |
| TC-L21 | A/B | Downward-/Jenkins-Metadaten nur aus erlaubten Quellen; keine automatische Übernahme aller Labels/Env. Kleine konfigurierte Termination Message bleibt sicher und unter der eigenen Grenze. |
| TC-L22 | A/C | Hash des tatsächlich geladenen Scripts/Pakets und benannter SQL-Ressource geprüft; behauptete Jobrevision als mitgeliefert statt automatisch git-verifiziert gekennzeichnet. |
| TC-L23 | C | Fertiges Job-JAR: genau ein Backend, nötige Services/Brücken erhalten, reale Exception-/Quellenzeilen und Maskierung identisch zum Scriptmodus; kein Logging-Erfolg allein aus --help. |
| TC-L24 | optionales OTel-Profil | Erst bei gelieferter Instrumentierung: gültiger W3C-Kontext und echte trace/span IDs, parent/child Catchstatus, keine doppelte Logexportpipeline, niedrige Labelkardinalität, begrenzter flush/shutdown bei ausgefallenem Collector. Ohne Profil muss telemetry.mode=none ehrlich bleiben. |

Kubernetes-/Jenkins-Erfassungsnachweise verwenden einen ausdrücklich verfügbaren Testbetrieb. Eine bestandene lokale stderr-/JSON-Prüfung wird nicht als erfolgreiche Integration in das tatsächliche OpenShift-Cluster ausgegeben. Es wird kein Clusterzugang allein für die Dokumentenerstellung verlangt und keine privilegierte Collectorinstallation still mit beauftragt.

## 12. Ergänzung 4.1 — Konventioneller Maven-Produktbuild

Die Regeln BLD-001 bis BLD-008 in [07, Abschnitt 6.4](07-plattform-und-versionen.md#64-verbindliche-leitlinie-konventioneller-maven-produktbuild) erhalten die folgenden prüfbaren Orakel. Das sind Build-/Review-Nachweise, keine zusätzlichen Runtimefunktionen. Eine Tabellenzeile verlangt weder ein neues Maven-Plugin noch eine eigene Testklasse. M0 prüft den vorhandenen Anfangsbestand; spätere Artefaktgates gelten erst für die jeweilige Lieferstufe.

| Fall | Zeitpunkt | Szenario und Orakel |
|---|---|---|
| TC-BLD01 | M0, danach Release | Frischer Checkout mit sauberem projektspezifischem Artefaktbestand: der gemeinsame Wrapper verwendet den festgelegten Maven-/JDK-Stand und führt den zugesagten Standardumfang mit `verify` aus. Kein manuell vorinstalliertes GRETL-Modul, privates Parentartefakt oder zweiter Buildweg ist nötig. Zugriff auf dokumentierte Buildrepositories ist erlaubt. |
| TC-BLD02 | M0, bei Moduländerung | POM-/Effective-POM-Review: genau ein projektinterner Produktparent, normale Source-/Outputpfade, deklarierte Reactorabhängigkeiten und nur tatsächlich benötigte Anfangsmodule. Einzelne externe Custom-Task-Fixtures bauen gegen die SPI ohne Pflicht zur Vererbung des Produktparents. |
| TC-BLD03 | M0, bei Dependency-/Pluginänderung | Aufgelöste Dependencybäume und Effective POMs prüfen: feste Versionen, passend aktivierte Plugins, keine global vererbten Fach-/Testdependencies in API/Runner und keine ungeprüft erzwungene Major-Version. Die separate GeoTools-Probe nennt ihren zusätzlichen Bestand. |
| TC-BLD04 | M0/M2 | Java-Produktquellen mit eingebettetem Groovy und `.groovy`-Testressourcen: der Build benötigt dafür keinen zusätzlichen Groovy-Produktcompiler. Werden tatsächlich Groovy-Produkt-/Testquellen eingeführt, ist deren begrenzte Compilerintegration mit Version und betroffenem Modul dokumentiert und geprüft. Die reale DSL-Probe wird dadurch nicht ersetzt. |
| TC-BLD05 | M0, M3/M4, später C | Dokumentierte Profil-/Lifecycle-Aufrufe in CI und lokal vergleichen. Releasegates sind ausdrücklich aktiviert; fehlende Infrastruktur ergibt keinen stillen Erfolg. Getrennte Integrationstests werden bis `verify` geprüft. Private Dateien, Umgebungsvariablen oder automatisch abweichende JDKs schalten nicht unbemerkt andere Fachsemantik frei. |
| TC-BLD06 | Bei Buildänderung | Kurzer begründeter Plugin-/Profilbestand: jede zusätzliche Abstraktion hat eine konkrete Aufgabe. Keine langen eingebetteten POM-Scripts, Shell-Reactoren oder vorsorgliche Lifecycle-/Convention-Frameworks. Inventar-/Packagerlogik liegt in testbaren Werkzeugen, nicht in einer versteckten zweiten Buildspezifikation. |
| TC-BLD07 | M3, M6 nur für C | Tatsächlich gebaute Artefakte prüfen: Bibliotheks-JARs ohne pauschales Shading, Standarddistribution ohne ausschliesslich testseitige GeoTools-JARs, kein Maven/Gradle/Ivy im normalen Jobstart. Das spätere Job-JAR erhält seine qualifizierten Service-/Groovy-/Logging-Merges ohne Maven-Laufzeit; bestehende TC-J-/TC-L-Gates bleiben Pflicht. |
| TC-BLD08 | M0, bei Releaseumfangsänderung | Eine weitere Entwicklungsperson kann anhand der kurzen Buildanleitung Standardprüfung, gezielten Modulbuild und die gelieferten Zusatzgates aufrufen. Voraussetzungen, Befehle und Artefaktorte stimmen mit CI überein; Dokumentreferenztests werden nicht als ausgeführter Maven-Produktbuild ausgegeben. |

Ein einfacher Build darf weder Loggingtests noch Secretprüfung, echte Validatorintegration oder Endproduktnachweise auslassen. Die Regeln begrenzen Infrastrukturkomplexität; sie reduzieren nicht die zuvor festgelegte Qualität. Publisher, XtfToGpkg, Native und noch nicht implementierte Paketierung bleiben aus den aktuellen MVP-Nachweisen ausgeklammert.
