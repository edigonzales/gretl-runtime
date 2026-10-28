# GRETL Runtime Umsetzungsplan

Dieser Plan führt die besprochenen Funktionskorrekturen, die verbesserte Testabdeckung und die Bereinigung der Modulgrenzen in **drei aufeinanderfolgenden Phasen** zusammen. Jede Phase liefert einen nutzbaren, geprüften Zwischenstand. Die nächste Phase beginnt erst, wenn ein zweiter, von der Implementierung unabhängiger LLM-Agent die vorherige Phase ausdrücklich mit **PASS** abgenommen hat.

Ausgangsbasis ist `edigonzales/gretl-runtime`, analysierter Commit `e668a51b9589fbea9a1e003c8dd18e28726bd430`. Vergleichsbasis für die Taskverträge ist `edigonzales/gretl-next`, Commit `08bd11e97a54f32f8702d34ff16fb39bec6ea046`. Die echten Jobtests aus `sogis/gretl`, Commit `a9222dc0ffd08f8f76570e998101db041822df71`, bilden zusätzlich die fachliche Referenz für die Integrationstests. Stand des Plans: 25. Oktober 2026, ergänzt um die gemeinsame JAR-/Image-Jobsuite. Die drei Umsetzungsphasen sind bei Erstellung dieses Plans noch nicht ausgeführt.

## Ziel und Umfang

Am Ende sollen die vorhandenen 44 Operationen über die öffentliche DSL verlässlich nutzbar, ihre wesentlichen Fehler- und Abbruchpfade nachgewiesen und die Module verständlich geschnitten sein. Die Standarddistribution enthält weiterhin den vollständigen vereinbarten Katalog. Zusätzlich erzeugt der Packager gezielt zusammengestellte Job-JARs aus vollständigen Taskartefakten und deren Laufzeitabhängigkeiten.

Der Umfang umfasst die gefundenen Fehler an den DSL-Metadaten, Cancellation und Shutdown, Korrelation in den Logs, messbare Testabdeckung, echte Jobs gegen gebaute Artefakte, die IO-/DB-Modulgrenze, die Auswahl der Validator-Zusatzfunktionen, die gemeinsame Jobausführung sowie die dafür erforderlichen Änderungen an Paketierung, Beispielen, Referenz und CI. Dieselben fachlichen Jobtests müssen über einen gemeinsamen Runner als JAR und später mit dem GRETL-Docker-Image ausführbar sein. Die JAR-Ausführung ist verbindlicher Teil dieses Auftrags. Der Image-Ausführungsweg wird vollständig vorbereitet und ohne realen Image-Start geprüft; eine tatsächliche Ausführung der Jobs im GRETL-Image ist noch kein Abnahmeauftrag.

`gretl-control-*`, Publisher, ein neuer XtfToGpkg-Task, neue produktive GeoTools-Tasks, Native Image und ein allgemeines Classloader- oder Prozessisolationssystem bleiben ausserhalb dieses Auftrags. Die vorhandene testseitige GeoTools-Qualifikation bleibt erhalten. Ebenfalls nicht beauftragt sind eine vollständige Nachbildung sämtlicher historischer `gretl-next`-Optionen, ein Modul pro Operation, zusätzliche Betriebsplattformen oder ein Deployment in produktive Umgebungen.

Die bewusst schmalen Verträge von `IliValidator` und `SqlExecutor` werden dokumentiert und geprüft. Eine fehlende alte Option gilt nicht automatisch als Implementierungsauftrag. Zusätzliche Fachfunktionen erfordern einen eigenen ausdrücklich festgelegten Umfang.

Die bestehenden `AGENTS.md`-Dateien und die einschlägigen normativen Spezifikationsabschnitte sind vor der Arbeit zu lesen. Dieser Folgeplan ersetzt die historische Meilensteinreihenfolge für die hier ausdrücklich beauftragten Verbesserungen. Bestehende Runtime-, DSL- und Logging-Verträge bleiben massgeblich; notwendige Änderungen werden begründet und gleichzeitig in der Spezifikation nachvollzogen. [Quellen S1 bis S5]

## Phasenübersicht

| Phase | Zusammenhängendes Ergebnis | Voraussetzung |
| --- | --- | --- |
| 1 Funktion und Nachweise absichern | Bekannte Fehler behoben; Originaltests inventarisiert; gemeinsamer Jobtest-Runner mit echten JAR-Prozessen; Coverage-Berichte verfügbar | Ausgangsstand und Testumgebung festgehalten |
| 2 Modulgrenzen bereinigen | Vier verständliche Taskmodule, auswählbare Validator-Erweiterungen und gemeinsame Jobausführung; breiter migrierter Jobtestbestand und vorbereiteter Image-Adapter | Unabhängiges PASS für Phase 1 |
| 3 Paketierung und Gesamtprodukt abnehmen | Gezielte Job-JARs und vollständige Standarddistribution durch die gesamte JAR-Jobsuite geprüft; Image-Aufruf und CI opt-in vorbereitet; unabhängige Gesamtabnahme | Unabhängiges PASS für Phase 2 |

Baseline, Dokumentation und Tests sind Bestandteil der jeweiligen Phase. Es gibt keine separate Vorbereitungsphase und keine spätere Phase, in die verpflichtende Nachweise verschoben werden dürfen.

## Echte Jobtests mit gemeinsamem JAR und Image Ausführungsweg

### Fachlicher Testbestand aus dem Original

Im Original-GRETL nutzen `jarTest` und `imageTest` denselben Integrationstestbestand. `IntegrationTestUtil` wählt über `GRETL_TESTTYPE` den Ausführungsweg. Der ursprüngliche JAR-Weg prüft das Gradle-Plugin-JAR mit Gradle TestKit; er ist kein `java -jar`-Start. Übernommen werden die fachlichen Szenarien, Daten und Ergebnisprüfungen. Ihre Jobbeschreibungen werden in die neue GRETL-DSL übersetzt. Gradle TestKit und die alte Init-Script-/Classpath-Injektion werden nicht in die neue Runtime übernommen. [Quelle S9]

Für jedes ursprüngliche Szenario wird eine Zuordnung geführt: Originalcommit, Testmethode und Jobverzeichnis, fachlicher Zweck, erwartete Daten und Fehler, neuer Fixture-/Testpfad, benötigte Dienste und Migrationsstatus. Szenarien innerhalb des vereinbarten Funktionsumfangs werden übernommen oder durch einen fachlich gleichwertigen nachgewiesenen Fall abgedeckt. Ausserhalb des Umfangs liegende Fälle erhalten einen konkreten Grund. Historische Testzahlen, auskommentierte Tests und unbrauchbare Zugangsdaten gelten nicht als erhaltene Abdeckung. Ein blosses Verkleinern auf wenige Smokeszenarien erfüllt den Auftrag nicht.

Am Ende von Phase 2 muss jede der 44 angebotenen Operationen in mindestens einem tatsächlich ausgeführten fachlichen JAR-Job vorkommen; kombinierte Jobs dürfen mehrere Operationen abdecken. Zusätzlich bleiben die relevanten Varianten und Negativfälle des Originals erhalten. Bewusste Vertragsabweichungen wie frühere SqlExecutor-Optionen werden über die Kompatibilitätsmatrix abgegrenzt; die Tests verwenden den zugesicherten neuen Vertrag. Es entsteht dadurch keine automatische Verpflichtung, jede alte API nachzubauen.

### Ein Szenario und dieselben Assertions für beide Ausführungswege

Der kanonische Fixture-Bestand liegt im vorhandenen Integrationstestbereich, vorzugsweise unter `gretl-integration-tests/src/test/resources/jobs/<szenario>/`. Ein Fall enthält eine echte `job.groovy`, benötigte SQL-/Modell-/Eingabedateien, Paketbeschreibung soweit erforderlich und separat abgelegte erwartete Ergebnisse. Die Testlogik richtet Dienste ein, startet den Job über den gemeinsamen Runner und prüft danach mit unabhängigen Lesern oder Clients die fachlichen Ergebnisse.

Es gibt keine kopierte zweite Testsammlung für das Image. Jobdefinition, Parameterbedeutung, erwartete Ergebnisse und fachliche Assertions bleiben gleich. Nur Startmechanismus, Pfade und erreichbare Dienstadressen werden durch den jeweiligen Ausführungsadapter umgesetzt. Ein zentraler Modus `gretl.test.executionMode=JAR|IMAGE` mit Default `JAR` genügt; weitere Plugins oder ein allgemeines Testframework sind nicht erforderlich.

| Modus | Testobjekt und Ausführung | Verpflichtung dieses Auftrags |
| --- | --- | --- |
| `JAR` | Fixture-Job über den regulären Packager mit den Artefakten des Kandidaten bauen und in einem separaten Prozess mit `java -jar` ausführen | Implementieren und tatsächlich ausführen; erfolgreicher Pflichtnachweis |
| `IMAGE` | Dieselbe Jobquelle und dieselben Daten über den vorgesehenen Launcher/Entrypoint des konfigurierten GRETL-Produktimages ausführen | Adapter, Konfiguration, nicht startende Vertragstests und späteren CI-Aufruf vorbereiten; den echten GRETL-Image-Lauf jetzt nicht ausführen |

Ein Aufruf von `GretlCli.run()` innerhalb der Test-JVM bleibt ein nützlicher Komponententest, zählt aber nicht als neuer JAR-Artefaktnachweis. Auch ein beliebiges Java-Containerimage mit gemountetem Host-Fat-JAR wäre kein Nachweis für die im GRETL-Produktimage mitgelieferte Runtime. Bereits vorhandene Tests des ausgepackten Skriptlaunchers bleiben zusätzlich erhalten.

### Gemeinsamer Runner und Isolation

Der bestehende Jobtest-Runner wird zu einem kleinen testseitigen Vertrag weiterentwickelt. Die Eingabe beschreibt Szenario-ID, Jobverzeichnis, Zieltasks, Parameter, Environment, Konfiguration/Secrets, Arbeits- und Ausgabeverzeichnisse, Dienstendpoints sowie Timeout. Das Ergebnis liefert mindestens Exitstatus, stdout, stderr, Report-/Ausgabepfade, Dauer, Timeout-/Abbruchstatus und die tatsächlich verwendete Artefaktidentität. Ergebnisse werden erst nach Prozessende und vollständig beendeter Erfassung beider Ausgabeströme ausgewertet.

Jeder Lauf erhält eine frische Arbeitskopie und leere Ergebnisverzeichnisse. Erwartete Dateien liegen ausserhalb der beschreibbaren Ausgaben; übrig gebliebene Dateien dürfen keinen Erfolg vortäuschen. Verpacken, Starten und fachliche Jobfehler sind unterscheidbar. Ein erwarteter Validierungsfehler benötigt den richtigen Status und fachlichen Diagnosegrund; eine fehlende Klasse, ein kaputter Launcher oder ein beliebiger Prozessfehler darf diesen Test nicht erfüllen. Datenbank-Schemata, Buckets und Remoteverzeichnisse werden je Lauf isoliert. Locale, Zeitzone und Encoding werden für die relevanten Datums-/Textfälle ausdrücklich festgelegt.

Der JAR-Prozess erhält keinen eingeschleusten Testclasspath und nutzt die gebauten Kandidatenartefakte. Prozess-Timeouts, Abbruch, Drain/Join der Ausgabeströme, Cleanup und Nachweisaufbewahrung müssen auch im Fehlerfall funktionieren. Logs und Reports werden für fehlgeschlagene Fälle gesichert; Secrets bleiben redigiert.

### Was der Image Adapter jetzt bereits können muss

Der Image-Adapter wird als ausführbarer Adapter implementiert, nicht als leerer Platzhalter. Er erhält eine explizite Image-Referenz über `gretl.test.image`; keine implizite Auswahl von `latest`. Zu konfigurieren sind Job-/Input-Mounts, beschreibbare Output-/Work-/Report-Verzeichnisse, Arbeitsverzeichnis im Container, Benutzer-/Dateirechte, Environment, read-only Secretdateien, Netzwerk und Endpoints der Testdienste. Relative Pfade und Zieltaskauswahl müssen dieselbe Bedeutung wie im JAR-Modus behalten.

Hostpfade werden zentral in Containerpfade übersetzt. Für PostgreSQL, FTP, S3 und lokale HTTP-Fixtures gibt es getrennte erreichbare Endpoints aus Sicht des Hosttests und des GRETL-Containers, etwa veröffentlichte Ports einerseits und Netzwerkalias plus interner Port andererseits. Hardcodiertes `localhost` und pauschales Host-Networking bilden keinen portablen Vertrag. FTP benötigt insbesondere eine passende Konfiguration für passive Verbindungen. Originale Jobdateien werden nicht durch verstreute Stringersetzungen pro Backend angepasst.

Ohne einen echten Container zu starten, werden Befehlsargumente als Argumentliste, Pfade mit Leerzeichen, Mount-/Endpoint-Zuordnung, Parameter-/Secretübergabe, Zieltasks, Reportpfade, Ergebnisabbildung und Timeout-/Stop-Steuerung am Adaptervertrag geprüft. Die Startgrenze muss dazu testseitig austauschbar sein. Diese Prüfungen belegen die Vorbereitung; sie werden nie als erfolgreiche Image-Integration ausgegeben. Bei später ausdrücklich gewähltem Modus `IMAGE` müssen fehlende Image-Referenz, fehlendes Image oder nicht erreichbare Containerlaufzeit klar fehlschlagen. Es gibt keinen stillen Rückfall auf JAR und kein grünes Überspringen.

### GRETL Image und Testdienste getrennt behandeln

Die aufgeschobene GRETL-Image-Ausführung betrifft ausschliesslich das System unter Test. PostgreSQL/PostGIS, FTP und S3 werden schon für die JAR-Jobs als reale lokale Testdienste bereitgestellt, wie im bestehenden Profil `backing-services`. Diese Container bleiben Pflicht. Die vorhandenen Testcontainers-/Floci-Fixtures werden weiterverwendet; es braucht keinen zusätzlichen Infrastrukturstack. Besonders für FTP werden die bereits vorhandenen Runtime-Tests gegen einen lokalen Server erhalten; der historische auskommentierte Test mit externer Adresse ist keine ausreichende Vorlage.

Für die aktuelle Abnahme lautet der Status: **JAR fachlich verifiziert; IMAGE vorbereitet und noch nicht ausgeführt.** Das ausdrücklich zurückgestellte Image-E2E ist kein BLOCKED-Grund für diese drei Phasen. Fehlende JAR-Nachweise, fehlende Pflichtdienste oder fehlende Prüfungen des Image-Adaptervertrags bleiben dagegen FAIL beziehungsweise BLOCKED. Eine spätere Image-Qualifikation ist erst mit Ausführung derselben Jobsuite bestanden.

## Verbindliches Verfahren für jede Phase

### Rollen und Unabhängigkeit

**Agent A implementiert.** Er liest den Auftrag und die Repository-Anweisungen, ändert Code und Tests, führt die Prüfungen aus und erstellt einen Übergabebericht. Er darf seine eigene Phase nicht freigeben.

**Agent B prüft unabhängig.** Er wird für die jeweilige Phase als separater Agent mit frischem Kontext gestartet. Er hat in dieser Phase weder Produktivcode noch die zugehörigen Tests implementiert. Er erhält den Plan, die normativen Verträge, den Ausgangs- und Kandidatencommit sowie den Übergabebericht. Er liest Anforderungen, Code und Diff selbst und ermittelt sein Urteil aus eigenen Nachweisen. Der Bericht von Agent A ist eine Orientierung, kein Erfolgsbeleg.

Eine andere Modellfamilie ist nicht erforderlich. Entscheidend sind getrennte Rollen, eine eigene Prüfung und ein unveränderlicher Prüfstand. Falls im gewählten Werkzeug kein zweiter Agent verfügbar ist, wird eine separate LLM-Sitzung als Prüfer eingesetzt. Ein Rollenwechsel des Implementierungsagenten innerhalb desselben Kontexts genügt nicht.

Agent B arbeitet in einem eigenen sauberen Checkout oder Worktree des Kandidatencommits. Er ändert keine produktiven Dateien im Kandidaten. Zusätzliche Prüfprogramme oder Gegenbeispiele darf er in seiner eigenen Prüfumgebung erstellen; diese werden als Nachweise erhalten. Falls daraus ein dauerhafter Regressionstest nötig wird, übernimmt Agent A ihn in einen neuen Kandidaten, den Agent B erneut prüft.

### Ablauf und Entscheidungsregel

1. Agent A beginnt nur auf einem Stand mit gültiger Abnahme der vorherigen Phase. Für Phase 1 erfasst er die Ausgangsbasis.
2. Agent A setzt alle Arbeiten und Pflichtnachweise der Phase um. Während der Entwicklung sind gezielte Tests ausreichend; vor der Übergabe laufen die vollständigen vorgesehenen Prüfungen.
3. Agent A erstellt einen konkreten Kandidatencommit, protokolliert seinen SHA und übergibt den sauberen Stand an Agent B. Uncommittete Produktänderungen sind kein abnahmefähiger Kandidat.
4. Agent B prüft das vollständige Diff, jeden Abnahmepunkt, die resultierenden Artefakte und die Testberichte. Er führt die Pflichtprüfungen selbst aus. Wenn seine Umgebung dies nicht erlaubt, muss er einen nachprüfbaren CI-Lauf exakt dieses Commits samt Protokollen und Artefakten selbst auswerten; eine Statusmeldung von Agent A reicht nicht.
5. Agent B vergibt genau eines der Urteile `PASS`, `FAIL` oder `BLOCKED`.
6. Bei `FAIL` behebt Agent A die Findings innerhalb derselben Phase. Bei `BLOCKED` wird die fehlende Voraussetzung hergestellt. Danach prüft Agent B einen neuen eindeutig bezeichneten Kandidaten. Die nächste Phase bleibt bis zum PASS gesperrt.

**PASS** bedeutet: Alle Pflichtkriterien der Phase sind erfüllt, alle erforderlichen Prüfungen wurden ausgeführt, und es bestehen keine offenen Findings gegen diese Kriterien. Reine zusätzliche Verbesserungsideen dürfen separat aufgeführt werden, ohne einen Fehler umzubenennen.

**FAIL** bedeutet: Mindestens ein Kriterium ist nachweislich verletzt. **BLOCKED** bedeutet: Mindestens ein Pflichtnachweis ist nicht belastbar möglich, etwa wegen fehlendem Docker für die verpflichtenden Testdienste, ungeeigneter Java-Version, nicht erreichbaren notwendigen Artefakten oder unvollständigen Berichten. Es gibt kein „PASS mit noch ausstehenden verpflichtenden Integrationstests“. Die ausdrücklich aufgeschobene GRETL-Image-Ausführung gehört nicht zu diesen Pflichtnachweisen; ihre Vorbereitung und Vertragstests dagegen schon.

Jede Änderung an produktivem Code, Tests, Abhängigkeiten, Buildkonfiguration oder ausführbaren Beispielen nach dem Review erzeugt einen neuen Kandidaten und verlangt eine erneute Prüfung des geänderten Stands samt relevanten Regressionen. Eine reine Ablage des unveränderten Reviewberichts darf als Dokumentationscommit folgen; der Bericht nennt weiterhin den tatsächlich geprüften Produktcommit. Sobald die nächste Phase beginnt, werden auch die früheren Pflichtnachweise als Regressionen weiter ausgeführt.

Kriterien dürfen nicht nachträglich gelockert, Tests deaktiviert oder Fehler auf eine spätere Phase verschoben werden, um PASS zu erreichen. Eine tatsächliche Änderung des vom Benutzer beauftragten Umfangs ist ausdrücklich zu entscheiden und im Plan zu versionieren.

### Nachweis und Ablage

Im bestehenden Dokumentationsbaum wird ein kleiner Bereich `docs/implementation/improvement-plan/` verwendet. Er enthält den Umsetzungsplan, eine Anforderungs-/Testzuordnung und je Phase einen Implementierungsbericht sowie den getrennt verfassten Prüfbericht. Testlogs, XML-Reports, Coverage und binäre Artefakte werden als CI-Artefakte oder eindeutig zugeordnete Prüfarbeitsdateien abgelegt, nicht als grosse Binärsammlungen im Git-Repository.

Jeder Phasenbericht nennt Ausgangscommit, Kandidatencommit, Agentenrollen, tatsächliche Prüfbefehle, Umgebungsdaten, Ergebnisse und Links auf Nachweise. Jobtestberichte nennen zusätzlich Szenario-ID, Ausführungsmodus, JAR-Prüfsumme beziehungsweise später die aufgelöste Image-ID/Digest, konfigurierte Dienstendpoints ohne Secrets und Ergebnisdateien. Für den noch nicht ausgeführten Image-Modus wird der Vorbereitungsstatus separat erfasst. Der unabhängige Bericht enthält für jedes Kriterium dessen ID, Ergebnis, eigenen Nachweis und gegebenenfalls ein Finding. Anzahl ausgeführter, fehlgeschlagener und übersprungener Tests wird aus den Reports gelesen.

Die historische Zahl von 416 Tests ist ein Ausgangsinventar, keine Mindestquote und kein Coverage-Prozentsatz. Bei einer Umstrukturierung darf sich die Testmethodenzahl ändern; die fachlichen Anforderungen und Orakel müssen erhalten bleiben.

## Phase 1 Funktion und Nachweise absichern

### Ergebnis

Ein verlässlicher Verhaltensstand, gegen den sich das anschliessende Refactoring prüfen lässt. Insbesondere wird die gemeinsame Ausführung erst in Phase 2 verschoben, nachdem Abbruch, Reporting und Korrelation in ihrer heutigen Lage abgesichert sind.

### Arbeiten

**1. Ausgangsbasis und Verträge festhalten.** Aktuellen Arbeitscommit, Java-/Wrapper-Versionen, Testumgebung und bestehende Testresultate erfassen. Falls der aktuelle Repository-Stand vom analysierten Commit abweicht, die Befunde zuerst erneut abgleichen und bereits erledigte Punkte mit Nachweis kennzeichnen. Das Operationeninventar mit Namen, Providerzuordnung und Resultatverträgen für alle 44 Operationen festhalten. Eine knappe Kompatibilitätsmatrix zu `gretl-next` unterscheidet zugesicherte Funktion, bewusst abweichenden Vertrag und tatsächlichen Fehler. Die schmaleren IliValidator-/SqlExecutor-Verträge werden eindeutig erklärt; es bleiben keine unentschiedenen Zusagen in diesem Plan.

**2. Die drei DSL-/Metadatenfehler beheben.** In `ValidatorOperations` die `tableName`-Felder richtig zuordnen: CSV verlangt keine Tabelle, GPKG bietet die nötige Tabellenangabe. `modelDirectories` akzeptiert die dokumentierten Pfade aus `file(...)` und zulässige Repository-Strings. Für `Ili2gpkgImport` wird `defaultSrsCode` über die öffentliche DSL zugänglich. Hilfe, generierte Referenz und ausführbare Beispiele müssen denselben Vertrag zeigen. Gemeinsame Konvertierungen werden gezielt wiederverwendet; es entsteht kein neues allgemeines Utils-Modul.

**3. Abbruch und Shutdown vervollständigen.** `Db2Db` darf `TaskCancelledException` nicht als `DB2DB_EXECUTION` umklassifizieren. Die bestehende Priorität zwischen echtem Fehler und kontrolliert erkanntem Abbruch bleibt erhalten. Der Shutdown-Pfad fordert den kontrollierten Abbruch an und wartet innerhalb des bestehenden gemeinsamen Budgets für Cleanup und Flush auf den Abschluss. Dieses Budget beträgt im MVP 30 Sekunden insgesamt, nicht 30 Sekunden je Ressource oder Ausgabekanal. Der Hook darf weder einen normalen Abschluss doppelt ausführen noch einen Deadlock mit dem laufenden Job erzeugen.

Prozesstests verwenden einen kontrollierbaren kooperativen Task und nachweisbare Synchronisationspunkte statt zufälliger Sleeps. Geprüft werden Scriptstart und ausführbares Job-JAR. Der GRETL-Status `CANCELLED` mit fachlichem Code 20 wird vom Betriebssystemstatus eines SIGTERM-beendeten Prozesses unterschieden. Ein zuvor massgeblicher echter Fehler bleibt `FAILED` mit Code 10. Für SIGKILL oder dauerhaft blockierende Fremdbibliotheken wird keine vollständige Cleanup- oder Reportgarantie erfunden. [Quellen S2, S3]

**4. Korrelation korrekt durchreichen.** `runtime.correlationId` wird in den vorgesehenen strukturierten Laufdaten und im gemeinsamen Logkontext verwendet. `GRETL_CORRELATION_ID` dient entsprechend dem bestehenden Vertrag als Startadapterwert; explizite Runtimekonfiguration hat Vorrang. Es gelten die vorhandenen Grenzen von 256 Zeichen und keinen Steuerzeichen. Kein erfundenes `traceparent` und keine zusätzliche allgemeine Parameterquelle. Die verwandte Auswahl des Jobnamens wird an derselben Startadaptergrenze mitgeprüft. [Quelle S3]

**5. Die tatsächliche öffentliche Grenze testen.** Die Regressionen zu den gefundenen Feldfehlern müssen über die DSL und den öffentlichen Startpfad laufen. Ein direkt aufgebauter Java-Request allein genügt nicht. Je Fehler gibt es einen reproduzierbaren fehlschlagenden Ausgangsfall beziehungsweise einen dokumentierten Baseline-Nachweis sowie positive und sinnvolle negative Prüfungen. Ergebnisdateien und Datenbankzustände werden über unabhängige Leser beziehungsweise Clients geprüft.

Für alle registrierten Operationen wird das Metadateninventar geprüft; für die betroffenen Validator-, Datenbank- und ili2db-Pfade werden die konkreten DSL-Konfigurationen ausgeführt. Vorhandene Integrationstests bleiben erhalten. Der bislang nicht separat belegte PostGIS-Geometriepfad der DuckDB-Föderation erhält einen gezielten Nachweis für den zugesicherten Umfang oder eine ausdrückliche, getestete Ablehnung nicht unterstützter Typen. Keine stillen Datenverluste und keine unbeauftragte Erweiterung auf neue Geometrietypen.

**6. Testabdeckung messbar machen.** Eine für den festgelegten Java-25-Build geeignete, gepinnte JaCoCo-Version liefert Line- und Branch-Berichte pro produktivem Modul. Unit-, Komponenten- und Failsafe-Beiträge werden korrekt zugeordnet und, wo technisch erhoben, zusammen ausgewertet. Separat gestartete JVMs werden instrumentiert oder im Bericht ausdrücklich als nicht gemessene Ausführung ausgewiesen. Eine uninstrumentierte Subprozessprüfung darf nicht als gemessene Codeabdeckung erscheinen.

Es wird keine willkürliche globale Prozenthürde eingeführt. Pflicht sind reproduzierbare Berichte, transparente Messgrenzen und konkrete Regressionen für die hier geänderten Erfolgs-, Fehler- und Abbruchpfade. Jede Anforderung erhält einen Test- oder anderen belastbaren Nachweis; nicht jede Tabellenzeile benötigt eine eigene Testklasse.

**7. Originaljobs inventarisieren und den gemeinsamen JAR-Testweg aufbauen.** Den Bestand unter `sogis/gretl/gretl/src/integrationTest/` gegen die vereinbarten Runtime-Funktionen abgleichen und die Szenariozuordnung anlegen. Der gemeinsame Runner startet echte, regulär paketierte Fixture-Jobs in einem eigenen Prozess. Bis zum Ende dieser Phase laufen mindestens ein lokaler Datei-/Transformationsjob, eine echte Datenbank-Taskkette, ein fachlicher Validierungsfehler sowie die korrigierten DSL-Fälle über diesen Weg. Dabei dürfen vorbereitende Original-SQL-Dateien und Daten übernommen werden; Joberwartungen werden fachlich geprüft. Die gemeinsame Schnittstelle berücksichtigt bereits den späteren Image-Adapter. Seine Implementierung und der vollständige fachliche Port folgen in Phase 2.

### Verbindliche Abnahme

| ID | Erfolgskriterium |
| --- | --- |
| P1-01 | Baseline, vollständiges 44er-Operationeninventar und abgegrenzte Kompatibilitätsmatrix liegen vor; zugesicherte und bewusst nicht übernommene Optionen sind unterscheidbar. |
| P1-02 | Eine gültige CSV wird über die öffentliche DSL ohne `tableName` validiert; GPKG-Validierung mit `tableName` funktioniert; fehlende oder falsche Tabellenangaben ergeben die vorgesehene Diagnose. |
| P1-03 | `modelDirectories` funktioniert in den betroffenen Validatoren sowohl mit den dokumentierten `Path`-Werten als auch mit zulässigen Strings; ungültige Werte werden früh verständlich abgewiesen. |
| P1-04 | Ein über DSL gewählter `defaultSrsCode` bei `Ili2gpkgImport` ist im erzeugten GeoPackage nachweisbar; ein reiner Buildertest gilt nicht als Nachweis. |
| P1-05 | Db2Db-Abbruch bleibt `CANCELLED`; Folgetasks starten nicht; Ressourcen- und Transaktionsverhalten ist gegen eine echte Datenbank geprüft. Die bestehende Fehlerpriorität bleibt erhalten. |
| P1-06 | Kontrollierter Abbruch und SIGTERM sind im separaten Prozess für Skript und Job-JAR geprüft; kooperative Fälle erzeugen konsistente Terminaldaten und Report innerhalb des gemeinsamen Budgets. Das Urteil verwechselt fachlichen Status und OS-Prozessstatus nicht. |
| P1-07 | Korrelation, Vorrang der Runtimekonfiguration, Wertevalidierung und Trennung zweier Läufe sind belegt; Secret-Redaction und vorhandene Log-/Reportverträge bleiben wirksam. |
| P1-08 | Die betroffenen öffentlichen DSL-Pfade, der zugesicherte DuckDB-/PostGIS-Geometriepfad und ihre wesentlichen Fehlerfälle besitzen unabhängige fachliche Orakel; Hilfe und Beispiele stimmen damit überein. |
| P1-09 | Line-/Branch-Berichte pro produktivem Modul sind reproduzierbar; Messumfang und nicht instrumentierte Pfade sind ausdrücklich ausgewiesen. |
| P1-10 | Standardbuild und vollständige Profilprüfung bestehen; Pflichtfälle sind weder übersprungen noch deaktiviert; Agent B hat für den Kandidatencommit PASS erteilt. |
| P1-11 | Der Originalbestand ist mit Test-/Fixture-Provenienz, fachlichem Zweck, Zieltest und begründetem Scope erfasst; keine relevanten Szenarien verschwinden ohne Zuordnung. |
| P1-12 | Gemeinsamer Runner und kanonische Fixtures existieren. Lokaler Datei-/Transformationsjob, Datenbank-Taskkette, fachlicher Negativfall und die korrigierten DSL-Fälle laufen als echte JAR-Prozesse mit unabhängigen Ergebnisprüfungen. In-Process-CLI-Aufrufe zählen dafür nicht. |

### Besondere Aufgaben von Agent B

Agent B prüft die gefundenen Fehler ausdrücklich über die Anwenderschnittstelle, statt sich allein auf die neuen Tests zu verlassen. Er kontrolliert zumindest einen selbst gewählten negativen Fall je korrigierter Vertragsfamilie, den tatsächlichen Inhalt des erzeugten GeoPackage und die Abbruchklassifikation. Er sieht die Prozessprotokolle, Reports und Coverage-Messgrenzen selbst durch. Ein erfolgreicher interner Handleraufruf kann einen fehlgeschlagenen DSL-Aufruf nicht ersetzen.

Für die Jobtests kontrolliert er den tatsächlich gestarteten Prozess und die Identität des gebauten JARs. Er prüft die Originalzuordnung und mindestens einen Fall mit absichtlich fehlendem oder falschem erwarteten Ergebnis, damit die Assertions einen scheinbar erfolgreichen Lauf auch wirklich zurückweisen. Ein beliebiger Startfehler darf keinen erwarteten fachlichen Negativfall bestehen lassen.

## Phase 2 Modulgrenzen bereinigen

### Ergebnis

Die Struktur ist verständlich und die produktiven Abhängigkeiten entsprechen den Verantwortungen. Das in Phase 1 abgesicherte Verhalten bleibt erhalten.

### Verbindlicher Zuschnitt der Taskmodule

| Modul | Inhalt | Anzahl |
| --- | --- | --- |
| `gretl-io-tasks` | HttpDownload, Curl, Gzip, Extract, XslTransformer, vier FTP- und vier S3-Operationen | 13 |
| `gretl-database-tasks` | SqlExecutor, Db2Db, DuckDbSqlExecutor | 3 |
| `gretl-interlis-tasks` | Vorhandene Format-, Formatvalidierungs-, ili2db- und AV-Operationen | 27 |
| `gretl-ilivalidator` | Vorhandene schmale INTERLIS-Validierung | 1 |

`gretl-basic-tasks` und `gretl-core-tasks` werden durch die beiden ersten Module ersetzt. Die produktiven Taskmodule hängen innerhalb des Produkts weiterhin nur von `gretl-api` und `gretl-i18n` ab. Zusätzliche Abhängigkeiten sind ihre tatsächlich benötigten Fachbibliotheken. Runner, Groovy, CLI und Logging-Backend bleiben ausserhalb ihrer produktiven Abhängigkeiten.

### Arbeiten

**1. Module und Pakete verschieben.** POMs, Provider, Service-Dateien, Ressourcen, Tests und Beispiele zusammen umziehen. Unter IO verständliche Pakete für HTTP, Archive, FTP, S3 und XSLT verwenden. Die allgemeinen Datenbankoperationen samt ihrer gemeinsam genutzten Helfer bleiben zusammen. Das INTERLIS-Modul intern in Formate, Validierung, ili2db und AV ordnen; gemeinsam genutzte Engines bleiben interne Implementierung.

Operation-IDs, DSL-Symbole, Resultatfelder und die in Phase 1 festgelegten fachlichen Verträge bleiben stabil. Maven-Koordinaten und öffentliche Java-Pakete werden separat behandelt: Vor dem Verschieben die bisherigen öffentlichen Einstiegspunkte erfassen und eine nachvollziehbare Migration dokumentieren. Dieser Plan verlangt keine pauschale Binärkompatibilität alter konkreter Taskklassen. Die neutrale Task-SPI und das externe Extension-Beispiel müssen weiter funktionieren. Falls begründete Übergangsartefakte benötigt werden, dürfen sie keine zweiten Provider oder kopierten Implementierungsklassen erzeugen.

**2. Fachspezifische Validator-Funktionen auswählbar machen.** Die feste Auswahl der 18 Funktionsklassen und die obligatorischen organisationsspezifischen Funktionsbündel aus der allgemeinen Validierungsintegration lösen. Zuerst vorhandene IOX-Erweiterungsmechanismen prüfen; nur falls nötig einen kleinen Registrierungsadapter ergänzen. Die allgemeine Bibliothek enthält keine fest eingebaute NGK-/Organisationsauswahl.

Die Standarddistribution liefert die bisher zugesicherten Funktionsbibliotheken weiterhin mit und registriert sie über den dokumentierten Erweiterungsweg. Anwender normaler Skriptjobs müssen keine JARs verwalten. Ein Modell ohne Zusatzfunktion lässt sich auf einem Classpath ohne diese Funktionsbündel validieren. Eine tatsächlich verwendete fehlende Funktion führt zu einer eindeutigen Validierungs-/Konfigurationsdiagnose. Eine ausdrücklich ausgewählte, defekte Erweiterung darf nicht still übersprungen werden; doppelte qualifizierte Funktionsnamen werden eindeutig abgewiesen.

Die Regeln gelten für die tatsächlich unterstützten Erweiterungspfade der jeweiligen Validatorart und werden in einer kleinen Matrix festgehalten. Vorhandene Unterschiede zwischen CSV-, JSON-, GPKG-, SHP- und schmaler IliValidator-Integration werden erklärt; dieser Umbau erweitert nicht stillschweigend den gesamten Optionsumfang von `IliValidator`.

**3. Gemeinsame Ausführung in das vorhandene `gretl-job-runtime` verlagern.** Die von Skriptstart und Job-JAR benötigte Ausführungsorchestrierung, Jobquelle, Runtimekonfiguration, Parameterbehandlung und Abschlusslogik erhält dort einen gemeinsamen Startvertrag. Die CLI übersetzt ihre Kommandos und verwendet diesen Vertrag; der Job-JAR-Einstiegspunkt verwendet ihn direkt. Bestehende geeignete Klassen werden verschoben oder angepasst, statt parallele Ausführungswege aufzubauen.

Die gewünschte Abhängigkeitsrichtung lautet: CLI verwendet Job-Runtime und Packager; Job-Runtime verwendet Runner, Groovy und Logging. Job-Runtime hängt weder direkt noch transitiv von CLI oder Packager ab. Der Packager bleibt das Authoring-Werkzeug. API, Runner, Groovy, Logging und i18n behalten ihre grundlegenden Verantwortungen. Es entsteht kein zusätzliches allgemeines `execution-common`-, `runner-model`- oder Pluginframework-Modul.

**4. Alle Verbraucher und Nachweise nachführen.** Distribution, Inventar, Launcher, Paketdeskriptoren, Test-POMs, Referenzgenerator und dokumentierte Import-/Buildbeispiele werden im selben Schritt angepasst. Eine aktuelle Modulübersicht beschreibt Verantwortung und erlaubte Abhängigkeiten. Historische Meilensteinberichte werden als historisch gekennzeichnet, ohne den Entwicklungsverlauf umzuschreiben. Die Architekturgrenzen erhalten wenige gezielte Prüfungen auf den wirksamen produktiven Abhängigkeitsgraphen; Testdependencies werden nicht mit produktiven Abhängigkeiten verwechselt.

**5. Den fachlichen Jobtestbestand vervollständigen und den Image-Weg vorbereiten.** Die in Phase 1 inventarisierten Szenarien innerhalb des Funktionsumfangs vollständig migrieren oder durch gleichwertige nachgewiesene Fälle zuordnen. Jede der 44 Operationen muss in mindestens einem erfolgreich beziehungsweise gezielt fachlich fehlschlagend ausgeführten JAR-Job geprüft werden; für angebotene Erfolgsfunktionen ist mindestens ein erfolgreicher fachlicher Nachweis erforderlich. Neben Taskketten gehören typisierte CSV-Werte und Exportformate, Geometrie/SRID, INTERLIS-Daten und Validierungsfehler, S3-Schlüssel/Inhalte/Löschungen sowie lokale FTP-Transfers dazu. Datenbank-, Datei- und Objektspeicherprüfungen bleiben ausserhalb des Jobprozesses. Im selben Testbereich den Image-Adapter samt den oben beschriebenen Vertragstests implementieren, ohne ein GRETL-Image zu starten. Die neuen Modulnamen dürfen nur die Paketbeschreibungen und Startkonfiguration verändern, nicht die fachlichen Erwartungen abschwächen.

### Verbindliche Abnahme

| ID | Erfolgskriterium |
| --- | --- |
| P2-01 | Die vier Taskartefakte entsprechen dem Zuschnitt 13 / 3 / 27 / 1; die Standarddistribution enthält dieselben 44 Operationen mit stabilen DSL-Namen und Resultatverträgen. |
| P2-02 | Jeder Provider wird genau einmal entdeckt; weder verschobene Ressourcen noch Übergangsartefakte erzeugen doppelte Registrierung oder fehlende Meldungen. |
| P2-03 | API bleibt JDK-only; Runner bleibt fachlich neutral; Taskmodule besitzen keine produktive Abhängigkeit auf Runner, Groovy, CLI oder Logging-Backend; der produktive Modulgraph ist azyklisch. |
| P2-04 | `gretl-job-runtime` besitzt weder direkt noch transitiv eine Abhängigkeit auf CLI oder Packager; Skriptstart und Job-JAR verwenden denselben Ausführungsvertrag. |
| P2-05 | Allgemeine Formatvalidierung funktioniert in einem eigenständig zusammengestellten minimalen Classpath ohne die organisationsspezifischen Funktionsbündel. Das wird nicht nur durch Entfernen eines einzelnen Task-JARs aus einer vollen Distribution simuliert. |
| P2-06 | Die Standarddistribution validiert ein Modell mit tatsächlich ausgeführter Zusatzfunktion korrekt; fehlende benötigte Funktion, defekte ausgewählte Erweiterung und Namenskollision sind verständlich diagnostiziert. |
| P2-07 | Alle Phase-1-Regressionsfälle bestehen weiterhin für Skript und Job-JAR, einschliesslich Parameter, Pfadbasen, Korrelation, Redaktion, Fehler, Abbruch und Report. |
| P2-08 | Das externe FileSize-Beispiel wird gegen die veröffentlichbare API gebaut und als Extension verwendet; Java-/Maven-Migration, Hilfe, Beispiele und aktuelle Modulübersicht sind konsistent. |
| P2-09 | Standardbuild und vollständige Profilprüfung bestehen; die Abhängigkeitsnachweise und die unabhängige Prüfung des Kandidaten ergeben PASS. |
| P2-10 | Alle Original-Szenarien im vereinbarten Funktionsumfang sind migriert oder nachweislich gleichwertig abgedeckt; alle 44 angebotenen Operationen besitzen echte JAR-Jobnachweise. Varianten und Negativfälle bleiben erhalten; es gibt keine unerklärten Auslassungen in der Zuordnung. |
| P2-11 | JAR und IMAGE verwenden dieselben Szenarien und fachlichen Assertions. Der Image-Adapter ist implementiert und seine Start-, Pfad-, Rechte-, Netzwerk-, Parameter-, Secret-, Ergebnis- und Abbruchverträge sind ohne echten GRETL-Image-Start geprüft. |

### Besondere Aufgaben von Agent B

Agent B bewertet den aufgelösten produktiven Abhängigkeitsgraphen und den tatsächlichen Inhalt der Distribution, nicht nur die Modulnamen. Er kontrolliert einen vollständig entkoppelten Validator-Classpath, die aktive Zusatzfunktion und die neue Ausführungsrichtung. Er prüft, ob Umbenennungen als angebliche neue Modularität verkauft werden oder ob weiterhin versteckte transitive Abhängigkeiten bestehen. Die alten Phase-1-Regressionen bleiben unverändert aussagekräftig; angepasste Tests dürfen keine verschwundene Semantik verdecken.

Er vergleicht zusätzlich die Original-Assertions mit den migrierten Szenarien, prüft fachliche Ergebniswerte und die Vollständigkeit der 44er-Zuordnung. Für IMAGE bewertet er ausschliesslich die implementierte Vorbereitung und deren Vertragstests. Er bestätigt keinen erfolgreichen Containerlauf und verlangt ihn für dieses Phasen-PASS auch nicht.

## Phase 3 Paketierung und Gesamtprodukt abnehmen

### Ergebnis

Die neue Modulaufteilung ist bei der Auslieferung nutzbar. Ein Job-JAR enthält die ausgewählten vollständigen Taskartefakte samt ihren benötigten Laufzeitbibliotheken und läuft unabhängig vom Buildcheckout. Die vollständige Standarddistribution bleibt ein eigener, bequem nutzbarer Auslieferungsweg.

### Arbeiten

**1. Auswahlvertrag ausdrücklich festlegen.** Die vorhandenen Paketdeskriptoren bleiben verwendbar. Vor der Implementierung wird in der Spezifikation eindeutig festgehalten, wie bisherige Vollkatalog-Deskriptoren und die neue explizite Auswahl unterschieden werden. Die neue Auswahl wird über eine versionierte Descriptor-Erweiterung angeboten; leere oder fehlende Auswahlen haben eine dokumentierte Bedeutung und führen nie still zur Übernahme der zufälligen Hostumgebung.

Als Zielregel gilt: Der Packager ergänzt die zur angegebenen Runtimeversion gehörende Job-Runtime automatisch. Hinzu kommen die ausdrücklich ausgewählten Taskmodule und zusätzlichen Erweiterungsartefakte. Bestehende Deskriptoren erhalten bei Bedarf den vereinbarten vollständigen Standardkatalog aus einem versionierten Inventar. Welche externen, bisher nur zufällig im Host-Classpath vorhandenen Erweiterungen künftig ausdrücklich angegeben werden müssen, wird als Migration dokumentiert. Es wird keine automatische Vollanalyse dynamischer Groovy-Operationen versprochen.

**2. Die vollständige Abhängigkeitshülle auflösen.** `JobPackager` übernimmt nicht länger ungefiltert `currentClasspath()`. Artefakte werden anhand belastbarer Maven-Identität und Version ausgewählt; Dateinamen-/Substring-Erkennung ersetzt keine Identität. Laufzeitabhängigkeiten, Service-Dateien, Lokalisierungsressourcen, native Bibliotheken und sonstige benötigte Ressourcen werden vollständig mitgenommen. Für alle vom ausgewählten Provider angebotenen Operationen müssen die benötigten Abhängigkeiten vorhanden sein.

Konflikte zwischen Versionen und Ressourcen werden deterministisch behandelt oder mit einer klaren Diagnose abgewiesen. Bestehende notwendige Sonderbehandlungen, etwa bei Modellressourcen, werden geprüft und dokumentiert. Der Umfang wird pro Modul bestimmt; es gibt keine aggressive Entfernung vermeintlich unbenutzter Klassen anhand einzelner Tasknamen.

**3. Auflösung, Kompilierung und Bündelung zusammenführen.** Deklarierte Erweiterungsabhängigkeiten werden vor der Jobkompilierung bereitgestellt und dem Compiler ausdrücklich zur Verfügung gestellt. Ein Job mit einer externen, nicht im Host vorinstallierten Extension muss kompilierbar und ausführbar sein. Der Compiler darf fehlende deklarierte Abhängigkeiten nicht unbemerkt aus der eigenen Umgebung beziehen. Beim Paketieren werden weder Tasks ausgeführt noch deren fachliche Nebenwirkungen ausgelöst.

Maven bleibt ein Werkzeug des Paketierungsschritts. Der spätere Jobstart braucht weder Maven noch den Quellcheckout oder das Nachladen von Java-Artefakten. Fachliche Netzwerkzugriffe eines Jobs und ausdrücklich benötigte Datenbank-/DuckDB-Extensions werden separat beschrieben; daraus wird keine allgemeine Offline-Garantie für jeden Fachjob abgeleitet.

**4. Artefakte anhand repräsentativer Pakete prüfen.** Der minimale gemeinsame Runtimebestand und die vier Taskfamilien werden getrennt verifiziert. Ein Paketinventar nennt enthaltene Artefakte mit Versionen und nachvollziehbaren Prüfsummen. Bei einem zusammengeführten Fat-JAR stammt der Nachweis aus der tatsächlich verwendeten Eingabeliste und wird durch Klassen-/Ressourcenprüfungen ergänzt; ein Blick auf JAR-Dateinamen allein genügt nicht.

| Paketfall | Pflichtnachweis |
| --- | --- |
| Job ohne Standardtask, mit einer einfachen Action | Gemeinsame Ausführung funktioniert ohne Standardtaskartefakte und deren allein dadurch benötigte Fachbibliotheken. |
| IO-Paket | Echter lokaler Download-/Archivjob; FTP/S3/XSLT-Ressourcen und Service-Registrierung sind für das vollständige Modul vorhanden; keine nur durch DB- oder INTERLIS-Tasks begründeten Bibliotheken. AWS und Saxon sind in diesem groben IO-Zuschnitt zulässig. |
| Allgemeines Datenbankpaket | SQL-/Db2Db-Pfad und DuckDB lassen sich aus dem Paket ausführen; keine zusätzlich ausgewählten IO-/INTERLIS-Taskartefakte. DuckDB bleibt Bestandteil dieses Moduls. |
| Schmales IliValidator-Paket | Lokale INTERLIS-Validierung ohne die ausschliesslich durch das grosse INTERLIS-Modul benötigten ili2db-/AV-Komponenten. |
| INTERLIS-Paket | Repräsentativer Format-/ili2db-Pfad, benötigte native Ressourcen und ausgewählte Zusatzfunktion laufen aus dem Paket. |
| Externe Extension | Neu gebautes FileSize- oder gleichwertiges Fixture-Artefakt wird aus einem kontrollierten Testrepository aufgelöst, beim Kompilieren verwendet und im gestarteten Job gefunden. |
| Bisheriger vollständiger Descriptor | Dokumentierter Kompatibilitätsweg erzeugt ein funktionierendes Paket mit dem vereinbarten Standardkatalog. |

Fremde Bibliotheken, die eine ausgewählte Engine tatsächlich transitiv benötigt, werden nicht anhand ihres Namens verboten. Die Abwesenheitsprüfung vergleicht den real aufgelösten Graphen mit der begründeten Auswahl.

**5. Unabhängigkeit der gebauten Artefakte nachweisen.** Paketierung desselben Descriptors aus zwei Hostumgebungen, von denen eine zusätzliche unbeteiligte Bibliotheken enthält, ergibt dieselbe fachliche Artefaktauswahl. Paketjobs starten aus einem fremden Arbeitsverzeichnis und einer Umgebung ohne Checkout und Maven. Erfolgs-, Fehler- und Abbruchfälle prüfen die in Phase 1 festgelegten Log-, Report- und Statusverträge erneut. Unbekannte Artefakte, fehlende Versionen, nicht deklarierte notwendige Extensions und relevante Ressourcen-/Versionskonflikte erzeugen nachvollziehbare Fehler.

**6. CI und aktuelle Dokumentation abschliessen.** Standardbuild, vollständige Profile, reale Distribution und Paketfälle werden in der CI nachweisbar verknüpft. Checksummen und Inventar passen zu genau den getesteten Artefakten. Ein nachfolgendes `package` mit übersprungenen Tests gilt nicht als neue Abnahme. Hilfestellung, Beispiele, Modulübersicht, Kompatibilitätsmatrix, Paketierungsvertrag und Testanleitung beschreiben den finalen Stand. Die vorhandene Abhängigkeit paketierter Integrationstests vom Distributionsbuild wird im Build explizit abgesichert oder der vollständige Reactor bleibt der dokumentierte verbindliche Einstieg. [Quellen S4, S6 bis S8]

**7. Die gemeinsame Jobsuite an die finalen Artefakte binden.** Die gesamte migrierte JAR-Suite aus Phase 2 gegen die finalen Kandidatenartefakte ausführen; die repräsentativen Paketfälle ersetzen diesen breiten Bestand nicht. Tests starten keine veraltete lokale Installation und keine IDE-/Testklassen statt der gebauten JARs. Lokale Jobfälle sind im normalen Docker-freien `verify` erreichbar, dienstabhängige Fälle im vollständigen `backing-services`-Lauf. Das vorhandene Integrationstestmodul entsprechend eindeutig in den Reactor einbinden; es darf bei kombinierten Profilen nicht doppelt aufgenommen werden. Den tatsächlichen Distributions-/Paketbuild als Voraussetzung sicherstellen.

Für später einen expliziten Image-Aufruf und einen manuell aktivierbaren CI-Weg mit Image-Referenz vorbereiten. Diese nutzen denselben Jobkatalog und dieselben Assertions. Der normale Build und die aktuelle Pflichtmatrix wählen ausschliesslich JAR und starten kein GRETL-Image. Ein Imagebuild oder eine Imageausführung ist jetzt kein erforderlicher Nachweis. Die spätere Aktivierung soll über Konfiguration und bereitgestelltes Produktimage möglich sein, ohne die fachlichen Tests umzuschreiben. CI-Artefakte enthalten Testreports, redigierte Prozesslogs, Jobreports, relevante Ergebnisdateien und Artefaktidentitäten. Die Abschlussunterlagen unterscheiden JAR-Erfolg und noch offene Image-Qualifikation ausdrücklich.

### Verbindliche Abnahme

| ID | Erfolgskriterium |
| --- | --- |
| P3-01 | Versionierter Auswahl- und Migrationsvertrag ist implementiert; bestehende vereinbarte Deskriptoren funktionieren; fehlende Auswahl hat eine eindeutige dokumentierte Bedeutung. |
| P3-02 | Host-Classpath-Verunreinigung ändert die Artefaktauswahl nicht; Identität und Version werden zuverlässig ermittelt. |
| P3-03 | Alle Paketfälle aus der Tabelle laufen mit realen Ergebnissen; enthaltene und bewusst ausgeschlossene Abhängigkeiten sind nachgewiesen. |
| P3-04 | ServiceLoader-Registrierung, native Ressourcen und Meldungsressourcen funktionieren; die ausgewählten Provider haben keine fehlenden Laufzeitabhängigkeiten. |
| P3-05 | Eine nicht im Host installierte deklarierte Extension wird vor dem Kompilieren aufgelöst und funktioniert im Paket; fehlende Deklarationen werden nicht aus einem zufälligen Hostbestand kaschiert. |
| P3-06 | Paketieren führt keine Taskarbeit aus. Job-JARs starten ohne Maven und Checkout aus fremdem CWD; fachlich notwendige externe Services sind in den jeweiligen Tests kontrolliert bereitgestellt. |
| P3-07 | Die Standarddistribution enthält weiterhin den vollständigen 44er-Katalog und die vereinbarte Funktionsauswahl. Paketierungswerkzeuge werden nicht als notwendiger Bestandteil der reinen Jobausführung eingebaut. |
| P3-08 | Frühere Abnahmekriterien bestehen als Regressionen; die vollständige CI-Prüfung, Coverage-Auswertung, Checksummen- und Artefaktprüfungen beziehen sich auf den finalen Kandidaten. |
| P3-09 | Alle Pflichtdokumente und ausführbaren Beispiele stimmen mit diesem Stand überein; Agent B hat sowohl Phase 3 als auch die kumulative Gesamtabnahme mit PASS bewertet. |
| P3-10 | Die vollständige migrierte JAR-Jobsuite läuft gegen die finalen Kandidatenartefakte in CI; fachliche Ergebnisse, Logs, Reports, Szenariozuordnung und Artefaktidentitäten sind nachvollziehbar. Die Paket-Smokes ersetzen diesen Bestand nicht. |
| P3-11 | Image-Modus, explizite Image-Referenz und spätere CI-Aktivierung sind dokumentiert und vorbereitet; aktuell wird kein GRETL-Image ausgeführt. Der Abschluss weist JAR als verifiziert und IMAGE als vorbereitet, noch nicht ausgeführt aus. Pflichtprüfungen des Adapters und der JAR-Testdienste sind bestanden. |

### Besondere Aufgaben von Agent B

Agent B baut oder untersucht die finalen Paketartefakte selbst. Er prüft nicht nur deren Grösse oder erfolgreiche Hilfeausgabe, sondern fachliche Ergebnisse, native Ressourcen, Services und Abwesenheit unnötiger Artefakte. Er führt mindestens einen Vergleich mit absichtlich zusätzlich bestücktem Host-Classpath durch und prüft den Jobstart ohne Buildumgebung. Er kontrolliert alle früheren Kriterien erneut anhand des finalen Codes und der Regressionsergebnisse sowie die breite, tatsächlich ausgeführte JAR-Jobsuite. Mit dem PASS ist dieser Umsetzungsauftrag einschliesslich Image-Vorbereitung technisch abgeschlossen. Ein erfolgreicher GRETL-Image-Lauf wird damit nicht behauptet; seine spätere Qualifikation und eine produktive Inbetriebnahme sind gesonderte Schritte.

## Gemeinsame Prüfkommandos und Voraussetzungen

Die folgenden Kommandos gelten für den vollständigen Reactor und werden aus dem Repository-Root gestartet. Voraussetzung sind die festgelegte Java-25-Toolchain, der eingecheckte Maven-Wrapper sowie für das zweite Kommando ein funktionierendes Docker-/Testcontainers-Setup und die benötigten erreichbaren oder vorbereiteten Artefakte.

```bash
./mvnw -B clean verify
```

```bash
./mvnw -B -Pgeotools-probe,backing-services clean verify
```

Beide Konfigurationen sind relevant: Der erste Lauf sichert den normalen Docker-freien Einstieg samt lokalen echten Jobtests, der zweite die zusätzlichen Dienste und die vollständige fachliche JAR-Jobsuite. Der Jobtestmodus ist jeweils standardmässig `JAR`; optional kann er ausdrücklich mit `-Dgretl.test.executionMode=JAR` angegeben werden. Coverage-Erzeugung und die neu ergänzten Phasentests müssen in diesen dokumentierten Wegen erreichbar sein. Verwendet die Umsetzung dafür ein begründetes zusätzliches Coverage-Profil, wird dessen exakter Aufruf hier und in der CI ergänzt, bevor die Phase abgenommen wird.

Für die spätere Image-Qualifikation wird derselbe vollständige Aufruf mit `-Dgretl.test.executionMode=IMAGE -Dgretl.test.image=<explizite-Image-Referenz>` vorbereitet. Diese beiden Properties sind ein zu implementierender Testvertrag. Der Image-Aufruf wird im aktuellen Auftrag nicht ausgeführt und nicht automatisch in die Pflichtmatrix aufgenommen. Ist IMAGE später ausdrücklich ausgewählt, müssen seine Voraussetzungen überprüft werden; fehlende Voraussetzungen dürfen weder einen JAR-Fallback noch einen erfolgreichen Skip auslösen.

Gezielte Modulprüfungen helfen während der Entwicklung, ersetzen aber keinen Phasenabschluss. Insbesondere ist `-pl gretl-integration-tests -am` ohne gesicherten Distributionsbuild allein kein vollständiger Nachweis für paketierte Jobs. Es gibt keine Abnahme mit `-DskipTests`, `-DskipITs`, stillen Assumptions oder deaktivierten Pflichtfällen. Betriebssystemabhängige Signaltests dürfen in einer nicht geeigneten Umgebung ausdrücklich entfallen, müssen dann aber im vorgesehenen Linux-Prüflauf tatsächlich ausgeführt sein.

## Vorlage für den unabhängigen Prüfbericht

```text
Phase:
Prüfdatum:
Implementierungsagent:
Unabhängiger Prüfagent:
Ausgangscommit:
Geprüfter Kandidatencommit:
Prüfumgebung und Toolchain:
Eigene Prüfbefehle oder verifizierter CI-Lauf für diesen Commit:

Je Abnahmekriterium:
- ID:
- Ergebnis: erfüllt / verletzt / nicht prüfbar
- Eigener Nachweis: Testfall, Report, Artefakt oder Codebeleg
- Finding und notwendige Korrektur, falls vorhanden

Übernommene Regressionen früherer Phasen:
Anzahl Tests / Fehler / Skips und Erklärung:
Coverage-Messumfang und Grenzen:
Jobtest-Szenarien und tatsächlich verwendete JAR-Prüfsummen:
JAR-Ergebnis und getrennt davon Image-Vorbereitung:
GRETL-Image tatsächlich ausgeführt: nein / ja mit eigener Evidenz
Offene Findings gegen Pflichtkriterien:
Urteil: PASS / FAIL / BLOCKED
Begründung:
```

## Startanweisungen für die Umsetzung

### Auftrag an den Implementierungsagenten

```text
Setze den GRETL-Runtime-Umsetzungsplan phasenweise um. Beginne mit Phase 1.
Lies zuerst die Repository-Anweisungen und die relevanten normativen Verträge.
Gleiche den aktuellen Commit mit der im Plan genannten Ausgangsbasis ab.

Bearbeite nur die aktuelle Phase und erhalte den abgegrenzten Umfang.
Erstelle die geforderten Regressionen und Nachweise und führe die Pflichtprüfungen aus.
Übernimm die relevanten echten Jobszenarien aus sogis/gretl mit Daten und fachlichen
Assertions. Führe sie über regulär gebaute JARs in separaten Prozessen aus.
Bereite denselben Runner und dieselbe Suite für das GRETL-Docker-Image vor.
Implementiere und prüfe den Image-Adapter ohne tatsächlichen GRETL-Image-Start.
PostgreSQL/PostGIS-, FTP- und S3-Testcontainer bleiben für JAR-Jobs verpflichtend.
Übergib einen sauberen Kandidatencommit mit Bericht an einen separaten LLM-Prüfagenten.

Beginne die nächste Phase ausschliesslich nach dessen ausdrücklichem PASS.
Bei FAIL behebst du alle Findings innerhalb derselben Phase und lässt erneut prüfen.
Bei BLOCKED stellst du zunächst die fehlenden Prüfbedingungen her.
Lockere keine Kriterien und überspringe keine Pflichtnachweise, um PASS zu erreichen.
Am Ende von Phase 3 muss der Prüfer auch die kumulative Gesamtabnahme bestätigen.
Der Abschluss unterscheidet JAR verifiziert und IMAGE vorbereitet, noch nicht ausgeführt.
```

### Auftrag an den unabhängigen Prüfagenten

```text
Prüfe die angegebene Phase des GRETL-Runtime-Umsetzungsplans unabhängig.
Du hast die Änderungen dieser Phase nicht implementiert. Arbeite auf dem genannten
Kandidatencommit in einem separaten sauberen Checkout oder Worktree.

Lies Plan, Repository-Anweisungen, relevante Verträge und das vollständige Diff selbst.
Behandle den Implementierungsbericht als Orientierung und prüfe seine Aussagen nach.
Führe die Pflichtprüfungen aus oder verifiziere einen belastbaren CI-Lauf genau dieses
Commits mitsamt Logs und Artefakten. Prüfe für jeden Pflichtpunkt das reale Ergebnis.
Ergänze gezielt unabhängige Gegenbeispiele für die risikoreichen Änderungen.
Prüfe die Original-Szenariozuordnung und die tatsächliche JAR-Prozessausführung.
Bewerte den Image-Adapter anhand seiner implementierten Vorbereitung und Vertragstests.
Verlange jetzt keinen echten GRETL-Image-Lauf und bestätige auch keinen solchen Erfolg.
Die verpflichtenden Testdienste der JAR-Suite bleiben davon unberührt.

Ändere weder den Produktkandidaten noch die Abnahmekriterien. Melde Findings mit Beleg.
Vergib ausschliesslich PASS, FAIL oder BLOCKED. Nicht ausgeführte Pflichtprüfungen sind
BLOCKED. Prüfe nach Korrekturen den neuen Kandidaten erneut.
In Phase 3 überprüfst du zusätzlich alle früheren Kriterien am finalen Stand.
```

## Quellen zum Ausgangsstand

- **S1 Repository-Anweisungen und Architekturgrenzen:** [AGENTS.md](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/AGENTS.md) und [Arbeitsanweisungen zur Spezifikation](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/docs/spec/AGENTS.md).
- **S2 Runtime und Abbruch:** [Runtime-Verträge](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/docs/spec/03-runtime-vertraege.md) und [Runner-Klassendesign](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/docs/spec/15-runner-klassendesign.md).
- **S3 Logging und Korrelation:** [Logging und Telemetrie](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/docs/spec/16-logging-und-telemetrie.md).
- **S4 Aktuelle Tests und Grenzen:** [Teststrategie](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/docs/implementation/testing.md).
- **S5 Module und Taskmigration:** [Root-POM](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/pom.xml) und [Migrationsmatrix](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/docs/migration/core-tasks.md).
- **S6 Aktuelle Paketierung:** [JobPackager](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/gretl-packager/src/main/java/ch/so/agi/gretl/packager/JobPackager.java), [JobCompiler](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/gretl-packager/src/main/java/ch/so/agi/gretl/packager/JobCompiler.java) und [M7-Bericht](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/docs/implementation/m7-report.md).
- **S7 Distribution und CI:** [Distributions-POM](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/gretl-dist/pom.xml), [Publish-Workflow](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/.github/workflows/publish-snapshots.yml) und [Release-Workflow](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/.github/workflows/release-candidate.yml).
- **S8 Heutige Validator-Erweiterungen:** [InterlisValidationSupport](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/gretl-interlis-tasks/src/main/java/ch/so/agi/gretl/internal/interlis/InterlisValidationSupport.java) und [INTERLIS-POM](https://github.com/edigonzales/gretl-runtime/blob/e668a51b9589fbea9a1e003c8dd18e28726bd430/gretl-interlis-tasks/pom.xml).
- **S9 Original-GRETL-Jobtests:** [Gemeinsamer JAR-/Image-Runner](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/integrationTest/java/ch/so/agi/gretl/util/IntegrationTestUtil.java), [gemeinsame Gradle-Testaufgaben](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/integration-test.gradle), [Job-Fixtures](https://github.com/sogis/gretl/tree/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/integrationTest/jobs), [CSV-Typprüfung](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/integrationTest/java/ch/so/agi/gretl/jobs/CsvImportTest.java), [GeoPackage-Geometrieprüfung](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/integrationTest/java/ch/so/agi/gretl/jobs/GpkgImportTest.java) und [fachlicher CSV-Negativfall](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/integrationTest/java/ch/so/agi/gretl/jobs/CsvValidatorTest.java).
