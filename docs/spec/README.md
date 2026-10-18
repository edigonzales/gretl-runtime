# GRETL Runtime — schlanke JVM-Jobruntime und optionale Jobpakete

**Stand:** 18. Oktober 2026  
**Version:** Spezifikationsvorschlag 4.1  
**Umfang:** 19 zusammengehörige Markdown-Dateien  
**Status:** Architektur, Analyse und Implementierungsspezifikation; die neue Runtime ist noch nicht gebaut.

## Ergänzung 4.1 — Maven, bewusst konventionell

**Maven ist als Produktbuildwerkzeug bestätigt.** Verbindlich gilt: ein projektinterner Parent, kleine Modul-POMs, zentral verwaltete Versionen, lokal erklärte Dependencies und nur konkret begründete zusätzliche Plugins oder Profile. Es wird kein zweites internes Buildframework entwickelt. Der vollständige Vertrag BLD-001 bis BLD-008 steht in [07, Abschnitt 6.4](07-plattform-und-versionen.md#64-verbindliche-leitlinie-konventioneller-maven-produktbuild).

`./mvnw verify` bleibt der Standard. CI verwendet denselben Buildweg und aktiviert die nötigen zusätzlichen Integrationsgates ausdrücklich. Die Java-Implementierung der Groovy-Einbettung benötigt nicht allein wegen der `.groovy`-Jobdateien einen zusätzlichen Produktcompiler. Der normale Jobstart und ein fertiges Job-JAR bleiben ohne Maven; die spätere Paketfunktion verwendet Maven nur im ausdrücklich gewählten Authoringpfad.

Die bestehenden 19 Dateien sind fortgeführt, kein weiteres Grundsatzkapitel ist hinzugekommen. Buildregeln, M0-/Agentenauftrag, Paketierungsgrenze und acht Build-Abnahmefälle sind abgeglichen. **DSL, öffentliche API, Runtimezustände, Logging und MVP-Lieferumfang bleiben gegenüber 4.0 unverändert.** Auch die Versionskandidaten wurden in dieser redaktionellen Ergänzung weder neu recherchiert noch qualifiziert.

## Ergebnis der technischen Überarbeitung 4.0

Die Spezifikation ist jetzt auf den kritischen **Klassen- und Methodengrenzen** verbindlich: vollständige JDK-only API, konkrete Runnerrollen, eindeutige Zustands-/Ergebnisformen und technische Sequenzen für Konfiguration, Fachaufruf, Freigabe und Fehler. Eigene Tasks erhalten eine kleine typisierte Factoryhilfe statt eines zusätzlichen Annotationframeworks. Die Anwenderoberfläche aus 3.0 bleibt erhalten.

Logging ist als eigener **MVP-Liefervertrag** ausgebaut: automatische strukturierte Ereignisse, Run-/Task-/Invocation-Korrelation, erhaltene Ursachenketten und Fehler-IDs, Secretfilter, Librarybrücken, Fortschritt/Heartbeat, Provenienz und klare Datei-/Ausgabegrenzen. Kubernetes/OpenShift und Jenkins werden als konkrete Erfassungsumgebungen berücksichtigt. Collector, OTel-Exporter, Metrikserver und Native Image werden nicht zu neuen MVP-Voraussetzungen.

Neu sind [14 — Java-API](14-java-api-vertraege.md), [15 — Kernklassen](15-runner-klassendesign.md), [16 — Logging/Telemetrie](16-logging-und-telemetrie.md) und [17 — Findings/Prüfprotokoll](17-findings-und-pruefprotokoll.md). Die übrigen 15 Dateien wurden mit aktualisiert. API-/Beispielcode wurde aus den finalen Markdown-Dateien kompiliert und mit einem separat gebauten Provider-JAR in 18 Referenzprüfungen ausgeführt. Verwendet wurde das verfügbare JDK 21; dies ist kein Ersatz für die noch offene Produktqualifikation mit Java 25 und Groovy 6.

**Publisher bleibt ausserhalb der MVP-Implementierung und -Auslieferung.** Audit, Zielvertrag und alle neun aufbewahrten Publishertests sind erhalten. Fachliche Zustände bleiben in der Taskbibliothek. Script, optionales Job-Fat-JAR und später ein begrenztes Native-Experiment bleiben die strategische Reihenfolge.

## Weiterhin gültige Entscheidungen aus 3.0

GRETL Light soll eine kleine JVM-Runtime für bearbeitbare Groovy-Jobs werden. Fachanwender kombinieren verständlich dokumentierte Aufgabenbausteine und ändern deren Einstellungen. Entwickler können jederzeit eigene Tasks in Java, Groovy oder einer anderen JVM-Sprache ergänzen. Der Runner führt benannte Schritte aus und liest deren fachliche Konfiguration erst dann, wenn die jeweiligen Vorgänger abgeschlossen sind.

Version 3.0 präzisiert drei Entscheidungen:

1. **Die Standarddistribution enthält einen mit den Releases wachsenden Taskkatalog.** Die Liste des ersten Releases ist keine geschlossene Liste zulässiger Standardtasks. Weitere allgemeine oder eigene Tasks können ganz normal mitgeliefert werden. Ein zusätzliches JAR nutzt dieselbe SPI und dieselbe DSL.
2. **Publisher wird erst nach dem MVP umgesetzt und ausgeliefert.** Die vollständige Bestandsanalyse, sein Zielvertrag und alle allgemeinen Erkenntnisse bleiben Teil dieser Spezifikation. Seine Implementierung, Registrierung, Bereitstellung und eigene E2E-Abnahme blockieren den MVP nicht.
3. **Die Standard-DSL richtet sich an Fachanwender.** Einstellungen verwenden `=`, Tasks erhalten verständliche Namen, Parameter werden einmal beschrieben und vor Taskarbeit geprüft. Normale Rezepte brauchen weder `def` noch `ctx ->`, TaskRefs oder Java-Record-Aufrufe.

Ein erstes Rezept sieht damit so aus:

```groovy
task('tabellenAktualisieren', SqlExecutor) {
    database = 'edit'
    scripts = [file('sql/update.sql')]
}

defaults 'tabellenAktualisieren'
```

Die Datenbankreferenz wird von der Betriebsumgebung bereitgestellt; die SQL-Datei gehört zum Job. Der vollständige Einstieg mit Testdatei, Parametern, Download und Entpacken steht in [13-einstieg-fuer-jobautoren.md](13-einstieg-fuer-jobautoren.md). Alle Beispiele zeigen die zu implementierende Ziel-API.

## Was «Standarddistribution» konkret bedeutet

Die Distribution ist das startfertige Paket aus Runtime, Groovy und einem gemeinsam geprüften Bibliotheksbestand. `SqlExecutor` steht darin als unmittelbar verwendbarer Tasktyp zur Verfügung. «Symbol bereitstellen» bezeichnet diese technische Bereitstellung; es ist weder eine Sonderstellung im Core noch ein Verbot zusätzlicher Tasks.

| Task beziehungsweise Fähigkeit | Lieferstatus, unverändert in 4.1 |
|---|---|
| `HttpDownload`, `Extract`, `SqlExecutor` | Reale Implementierung und Mitlieferung in A. |
| `IliValidator` | Reale Implementierung und Mitlieferung in B. |
| GeoTools-Probe | Technische Entwicklungs-/CI-Prüfung in M0/B; kein öffentlicher Tasktyp und keine Pflicht, dafür unbenutzte GeoTools-JARs mitzuliefern. |
| `Publisher` | Wichtiger späterer gewöhnlicher Custom-Task; Umsetzung und Bereitstellung nach MVP. |
| `XtfToGpkg` | Weitere eigenständige Taskart nach MVP; kein aus einer früheren Namensliste abgeleitetes MVP-Versprechen. |
| Weitere GRETL- oder eigene Fach-Tasks | Können nach ihrer Qualifikation in den Standardbestand, eine Organisationsdistribution oder eine ausdrücklich installierte Erweiterung aufgenommen werden. |

Mitgelieferte Taskbibliotheken liegen unter `lib/tasks`, zusätzliche bereitgestellte Bibliotheken unter `lib/extensions`. Das sind Ablagebereiche desselben qualifizierten Klassenpfads, keine Klassenisolation. Für Anwender ist entscheidend, welche Bausteine ihre installierte Distribution anbietet; das Verpacken von JARs und Abhängigkeiten ist Aufgabe der Entwickler beziehungsweise der Personen, die das Paket bereitstellen. Im Jobstart wird kein Maven- oder Grape-Resolver benötigt.

Ein ausführbares Job-JAR enthält später den bewusst benötigten Bestand dieses Jobs. Es muss nicht jede inzwischen verfügbare Bibliothek der Standarddistribution mitnehmen. Der vollständige Distributionsvertrag steht in [06](06-betrieb-und-auslieferung.md), der Paketvertrag in [12](12-job-paketierung.md).

## Publisher: Erkenntnisse behalten, Lieferung verschieben

**Publisher ist derselbe Erweiterungsfall wie SqlExecutor.** Seine umfangreiche, meinungsstarke Geschäftslogik gehört in seine eigene Taskbibliothek. Sie begründet keinen Publisher-spezifischen Runner, keine besonderen CLI-Befehle und keine zentral überwachten Publikationspflichten.

Der MVP enthält keinen Publisherprovider, keinen Platzhalterhandler und keine eigens für seine noch ausstehende Umsetzung hinzugefügten Dependencies. Seine gespeicherten Zieltests TC-P01 bis TC-P09 gelten für den späteren Port. Sie werden nicht gelöscht und nicht als notwendige MVP-Nachweise ausgegeben.

Aktiv bleiben die daraus gelernten allgemeinen Anforderungen: frische Konfiguration je Aufruf, richtige Ressourcenlebensdauer, normale Fehlerbehandlung, klare Resultate, kontrollierter Abbruch, keine erfundene Rückabwicklung und keine versteckte automatische Wiederholung. Diese Anforderungen werden bereits an unabhängigen Testoperationen sowie den wirklich gelieferten SQL- und INTERLIS-Tasks geprüft.

Die in Version 2.0 aufgehobenen Sonderwege bleiben aufgehoben: keine Befehle `gretl publication inspect` oder `gretl reconcile`, keine Exitcodes 30/31, keine PublicationObligations-/Commit-/Deliveryregistry, kein verpflichtender Workeraufbau, keine neue Publikationsspeicherarchitektur als Teil des Runners. Fachliche Verbesserungen des Publishers sind ein späterer eigenständiger Auftrag.

## DSL: ein verständlicher Standard, eine offene Entwickleroberfläche

Normale Autoren lernen ein kleines Modell: **Schritt benennen, Tasktyp wählen, Einstellungen setzen, Vorgänger angeben.** `result('download').file` bezeichnet eine dokumentierte Ausgabe eines vorherigen Schritts. `parameter('quelle')` liest einen beim Start übergebenen Wert. `file('sql/update.sql')` bezeichnet eine Jobressource, `workPath('daten.zip')` eine Arbeitsdatei. Ein deklarierter Parameter vom Typ `path` übernimmt die Auflösung externer Pfade.

Die späte Konfiguration bleibt unverändert: Der Block eines Schritts wird erst gelesen, wenn dieser an der Reihe ist. Abhängigkeiten stehen vorher im Taskkopf fest. `result(...)` startet keine Tasks und ergänzt keine unsichtbaren Kanten. Es gibt keine zusätzliche lineare Pipeline-Sprache und keine Gradle-Providerwelt hinter dem Gleichheitszeichen.

Parameter- und Taskmetadaten liefern verständliche Hilfe, Vorgaben, dokumentierte Ein- und Ausgaben sowie präzise Fehlermeldungen. `gretl run job.groovy --help` erklärt den konkreten Job; `gretl operations SqlExecutor` den verfügbaren Tasktyp. Die jobbezogene Hilfe lädt die Definition ohne Taskarbeit. Die allgemeine Hilfe `gretl --help` behält ihren kurzen Startpfad ohne Jobladen.

Komplexe Token-, JSON- oder INTERLIS-Logik wird durch kürzere Syntax nicht automatisch anwendergeeignet. Sie gehört bei wiederkehrendem Bedarf in passende, gepflegte Custom-Tasks. Für Entwickler bleiben Actions, gewöhnlicher Groovy-Code und synchrone `ctx.run(...)`-Aufrufe verfügbar. Sie verwenden dieselbe Runtime und dieselben Fehlerregeln. Ein separater Anwenderleitfaden und eine noch durchzuführende kurze Prüfung mit tatsächlichen Fachanwendern sichern diese Zielsetzung ab.

## Strategischer Überbau und Lieferfolge

1. **Standard:** bearbeitbares Groovy-Script mit der installierten JVM-Runtime ausführen; kein separater Jobbuild.
2. **Optional:** einen bestimmten Job samt benötigten Bibliotheken und Ressourcen in ein ausführbares Fat-JAR packen; Parameter, Secrets und Tagesdaten bleiben Laufzeitwerte.
3. **Später optional:** einen fest gebündelten, dafür qualifizierten Job als Native Image bauen.

A und B bilden den Script-MVP. C ist der separate anschliessende Meilenstein für ausführbare Job-JARs. Publisher, XtfToGpkg und weitere Taskports erhalten eigene spätere Liefermeilensteine. Publisher muss weder auf einen Native-Nachweis warten noch zuerst für die generische Paketfunktion implementiert werden.

Die Leitlinien bleiben: kein Gradle im Laufzeitpfad oder in der öffentlichen Java-SPI; sequenzielle Ausführung in einer JVM; kein Taskcache, keine Up-to-date-Prüfung, keine automatische Wiederaufnahme und kein automatischer Vollretry; fachlich neutraler Core. Java 25, Groovy 6 und GeoTools 35.2 bleiben die am 17. Oktober recherchierten Ausgangskandidaten aus [07](07-plattform-und-versionen.md). Die genaue ausgelieferte Kombination muss noch qualifiziert werden. Die kleine GeoTools-Kompatibilitätsprobe bleibt als gesonderter Entwicklungsnachweis erhalten. Solange keiner der gelieferten Tasks GeoTools benötigt, vergrössert sie den installierten Runtimebestand nicht.

## Dateiführung

| Datei | Inhalt |
|---|---|
| [AGENTS.md](AGENTS.md) | Arbeitsanweisung, Lesereihenfolge und Grenzen für einen Coding-Agent. |
| [01-zielbild-und-mvp.md](01-zielbild-und-mvp.md) | Strategischer Überbau, Schichten, offener Katalog und Lieferstufen. |
| [02-publisher-bestandsanalyse.md](02-publisher-bestandsanalyse.md) | Belegter Publisher-Audit; fachliche Befunde und erhaltene allgemeine Schlussfolgerungen. |
| [03-runtime-vertraege.md](03-runtime-vertraege.md) | Ausführung, Parameter, Ergebnisse, Fehler, Abbruch und Ressourcen. |
| [04-job-dsl-und-custom-tasks.md](04-job-dsl-und-custom-tasks.md) | Verbindliche Anwender-DSL, Entwickleröffnung, Custom-Task-SPI und Hookmigration. |
| [05-publisher-zielvertrag.md](05-publisher-zielvertrag.md) | Vollständiger erhaltener Zielvertrag für den späteren gewöhnlichen Publisher-Task. |
| [06-betrieb-und-auslieferung.md](06-betrieb-und-auslieferung.md) | CLI, Hilfen, Parameter, Konfiguration, Pfade, Distribution und Betriebsgrenzen. |
| [07-plattform-und-versionen.md](07-plattform-und-versionen.md) | Java, Groovy, GeoTools und noch zu erbringende Freigabenachweise. |
| [08-native-image-strategie.md](08-native-image-strategie.md) | Schneller Start, vorkompilierter Job und spätere Native-Option. |
| [09-tests-und-abnahme.md](09-tests-und-abnahme.md) | Aktive MVP-Gates, spätere Publisherfälle sowie Fachanwender- und Paketabnahme. |
| [10-implementierungsplan-fuer-coding-agenten.md](10-implementierungsplan-fuer-coding-agenten.md) | Module, Umsetzungsreihenfolge, Lieferobjekte und Definition of Done. |
| [11-quellen-entscheidungen-und-roadmap.md](11-quellen-entscheidungen-und-roadmap.md) | Quellenstände, Entscheidungen, Revisionen und Ausbau. |
| [12-job-paketierung.md](12-job-paketierung.md) | Optionaler Fat-JAR-Build, gemeinsame Parameter und Jobhilfe. |
| [13-einstieg-fuer-jobautoren.md](13-einstieg-fuer-jobautoren.md) | Fachanwenderanleitung mit kleinen Rezepten, Pfaden, Parametern, Hilfe und Fehlerbehebung. |
| [14-java-api-vertraege.md](14-java-api-vertraege.md) | Vollständige Signaturen, Records, Nullability, Ressourcen und typisierte Hilfen. |
| [15-runner-klassendesign.md](15-runner-klassendesign.md) | Klassenrollen, Methodenports, Ergebnismodell und technische Sequenzen. |
| [16-logging-und-telemetrie.md](16-logging-und-telemetrie.md) | MVP-Diagnosevertrag und gezielte spätere Telemetrieprofile. |
| [17-findings-und-pruefprotokoll.md](17-findings-und-pruefprotokoll.md) | Abgleich mit den Findings und tatsächlich durchgeführte Referenz-/Dokumenttests. |

## Lesereihenfolge

Für Fachanwender: **13**, danach die Referenz des benötigten Tasks. API-Implementierung und Publisher-Audit sind kein notwendiger Lernstoff für das Schreiben eines einfachen Jobs.

Für die Architekturentscheidung: **01 → 04 → 06 → 12 → 08 → 07**. Publisher-Befunde und späterer Port: **02 → 05**.

Für die Implementierung: **AGENTS → 01 → 13 → 03 → 04 → 14 → 15 → 16 → 06 → 10 → 09 → 17**; danach die Kapitel der tatsächlich bearbeiteten Fachbibliothek oder Paketierung.

«Normativ» bezeichnet eine Vorgabe für die spätere Implementierung. Es bedeutet weder, dass diese bereits existiert, noch dass die genannten Versionen zusammen produktiv freigegeben wurden.

## Was geprüft wurde

Für Revision 4.1 wurden die neuen Maven-Vorgaben mit Agentenanweisung, Implementierungsplan, Tests und Paketierung abgeglichen. Die Dokument- und Archivprüfung ist in Kapitel 17 separat protokolliert. Die unveränderten API-/Provider-Codeblöcke wurden mit dem 4.0-Paket verglichen; die damaligen Java-Referenztests wurden nicht als neu ausgeführte Maven-Tests ausgegeben. Ein Maven-Produktbuild wurde in dieser Ergänzung nicht ausgeführt.


Die Arbeit umfasst statische Quellcodeanalyse an festgehaltenen Repository-Commits, Primärquellenrecherche, eine erneute Prüfung der Aufgaben- und Distributionsgrenzen sowie eine Entwurfsprüfung der DSL aus Anwendersicht. Die Markdown-Verträge werden auf Konsistenz geprüft. Die reine API-/Providerreferenz und das Zustands-Codefragment wurden kompiliert; die 18 kleinen Referenzprüfungen sind in Kapitel 17 reproduzierbar beschrieben. Die neue Runtime, ihre echte Groovy-/Loggingintegration, der Publisher-Port, ein vollständiges Job-Fat-JAR und ein Native Image sind hier nicht implementiert oder ausgeführt. Auch eine empirische Anwenderprüfung ist noch nicht erfolgt; Kapitel 09 beschreibt die Nachweise für die spätere Umsetzung.
