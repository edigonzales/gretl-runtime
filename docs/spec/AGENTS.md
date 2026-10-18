# Arbeitsanweisung für Coding-Agenten

**Spezifikationsvorschlag 4.1, 18. Oktober 2026.** Dieses Paket beschreibt eine erst zu implementierende GRETL-Light-Runtime. Es ermächtigt nicht zur Änderung produktiver Jobs oder Systeme.

## Zuerst lesen

1. [README](README.md), [01 — Ziel und Umfang](01-zielbild-und-mvp.md), [13 — Einstieg für Jobautoren](13-einstieg-fuer-jobautoren.md).
2. [03 — Runtime](03-runtime-vertraege.md), [04 — DSL und SPI](04-job-dsl-und-custom-tasks.md), [06 — Betrieb](06-betrieb-und-auslieferung.md).
3. [10 — Implementierung](10-implementierungsplan-fuer-coding-agenten.md), [09 — Abnahme](09-tests-und-abnahme.md), [07 — Plattform](07-plattform-und-versionen.md).
4. Für ausdrücklich spätere Publisherarbeit nach MVP: [02 — Audit](02-publisher-bestandsanalyse.md) und [05 — Taskgrenze](05-publisher-zielvertrag.md).
5. Für optionale Paketierung: [12 — Job-Fat-JAR](12-job-paketierung.md); Native bleibt [08](08-native-image-strategie.md) zufolge spätere Arbeit.

## Technische Pflichtlektüre vor M1/M2

Lies [14 — vollständige API](14-java-api-vertraege.md), [15 — Kernklassen und Sequenzen](15-runner-klassendesign.md) und [16 — Logging](16-logging-und-telemetrie.md). Die Signaturen und Lebensdauerregeln werden nicht durch frei erfundene Ersatzkontexte ersetzt. Die Closure-/Map-first-Probe muss mit der echten Groovy-Produktversion ausgeführt werden. Der reine Java-Referenznachweis aus [17](17-findings-und-pruefprotokoll.md) ersetzt das nicht.

Logging ist MVP-Arbeit ab dem ersten echten Aufruf: eindeutiger Context, Start-/Terminalereignisse, erhaltene Ursachen, Secretfilter, begrenzte Daten und sichere Ausgabe. Kein Konfigurations-/Resultatdump, keine gefälschten Traces und keine OTel-Collectorpflicht. Ein nicht-Void-Handler mit null ist jetzt explizit ein Providervertragsfehler; Action-null und Void bleiben Erfolg ohne Wert.

## Architekturgrenzen

- Der Runner ist fachlich neutral. Publisher ist derselbe Erweiterungsfall wie SqlExecutor. Seine Analyse und Zielverträge bleiben erhalten, aber Implementierung, Registrierung, Bereitstellung und Publisher-E2E sind kein Auftrag des MVP.
- A liefert HttpDownload, Extract und SqlExecutor; B ergänzt IliValidator und den gesonderten testseitigen GeoTools-Kompatibilitätsnachweis. Zusätzliche GeoTools-Testdependencies werden nicht ohne tatsächlichen Bedarf eines gelieferten Tasks in die Distribution aufgenommen. XtfToGpkg ist ebenfalls ein späterer eigener Task. Keine Zukunftstypen als verfügbare Stubs registrieren.
- Die Standarddistribution ist ein offener, releasebezogener Katalog. Eigene und künftige Tasks dürfen regulär mitgeliefert werden und verwenden dieselbe SPI, Syntax und Hilfe. Die Anwender verwalten im Job keine JARs.
- Keine Publisherbefehle, Publikations-Exitcodes, globale Commit-/Deliveryregistry oder versteckte Fehlerpflicht.
- Fachliche Exceptions dürfen bewusst behandelt werden. Nur ein unbehandelter Fehler lässt den umgebenden Task fehlschlagen. Cancellation und `finishJob` sind davon getrennte Runtimekontrolle.
- Gradlefreie Java-SPI; Groovy-Typen nur im DSL-Adapter. Taskbibliotheken besitzen ihre Fachmodelle und Abhängigkeiten selbst.
- Sequenzielle Ausführung in einer JVM zuerst. Kein vorgeschriebenes Workerprotokoll, keine Codecs für beliebige JVM-Resultate und kein universeller Plugin-Isolationscontainer.
- Taskkonfiguration wird einmal beim Taskstart nach den Vorgängern ausgewertet. Keine Evaluation mit Dummywerten in `plan`.
- Die kanonische Anwenderform verwendet `=` für Taskfelder, Stringnamen für Vorgänger, `parameter(...)` und dokumentierte `result(...).feld`-Ausgänge. Keine obligatorischen `def`, `ctx ->` oder TaskRefs in Grundrezepten. Keine zweite lineare Ablauf-DSL und keine automatische DAG-Ermittlung aus Closures.
- Gemeinsame Parameter- und Taskmetadaten speisen Hilfe, Defaultwerte und konkrete Fehler. Die kleine Metadatenfassung ist keine universelle Schemasprache, kein LSP-Auftrag und keine Voraussetzung für eine vollständige GUI. Unbekannte Felder dürfen nicht still im Script-Binding entstehen.
- Alle öffentlichen Jobparameter werden einmal im Job deklariert und über den gemeinsamen Vertrag aus 06 aufgelöst. Jobhilfe lädt die Definition ohne Taskarbeit; globale Hilfe und Version bleiben vor Job- und Providerladen.
- Eine Action ist gewöhnlicher ausführbarer Code. `ctx.run` ruft eine Operation synchron auf; direkte interne Java-Helfer bleiben zulässig.
- Kein Runtimecache, automatischer Vollretry, versteckte Wiederaufnahme oder dynamisches Nachregistrieren von DAG-Knoten.
- Eigene Task-JARs werden vor Start bereitgestellt; `@Grab` ist deaktiviert. Der normale Scriptwechsel braucht keinen Produktbuild.
- Der optionale Packager kompiliert einen Job automatisch zu JVM-Bytecode, führt ihn beim Packen aber nicht aus. Er ist kein Teil des nötigen Script-Laufzeitpfads.
- Kein Native-Erfolg allein aus einem erfolgreichen `--help` oder aus einem nativen Starter, der danach Java aufruft.

## Verbindliche Buildleitlinie

Maven ist entschieden; baue keinen parallelen Gradle- oder Ant/Ivy-Weg. Lies vor M0 BLD-001 bis BLD-008 in [07, Abschnitt 6.4](07-plattform-und-versionen.md#64-verbindliche-leitlinie-konventioneller-maven-produktbuild). Ein Root-Parent und Aggregator, kleine Modul-POMs, Maven-Standardlayout und der gemeinsame Wrapper genügen als Ausgangsstruktur. Zentralisiere Versions-/Pluginvorgaben, aber aktiviere Dependencies und zusätzliche executions nur in den tatsächlich betroffenen Modulen.

Begründe zusätzliche Plugins, Profile und Buildabstraktionen knapp anhand eines konkreten Problems. Kein internes Buildframework, keine langen eingebetteten POM-Scripts und keine Firmenparent-Hierarchie. Groovy bleibt zunächst eine eingebettete Bibliothek; ein Groovy-Produktcompiler wird nur für tatsächlich zu kompilierende Groovy-Quellen ergänzt. Die Sprache eigener Custom-Tasks wird dadurch nicht eingeschränkt.

`./mvnw verify` bleibt der normale Einstieg. Dokumentiere zusätzliche Integrations-/Qualifikationsbefehle und verwende sie ebenso in CI. Keine aufgrund privater Rechnerzustände wechselnden Fachimplementierungen oder still übersprungenen Releasegates. Shade gehört an die ausgewiesene spätere Paketierungsgrenze, nicht vererbt in alle Bibliotheksmodule. Ein Maven-Dependencycache ist erlaubt; weder Cache noch Packager bringen Maven in den normalen Jobstart.

Die Build-Abnahmefälle TC-BLD01 bis TC-BLD08 ergänzen die bestehenden Gates, ersetzen aber keine API-, Logging- oder Fachtests. Publisher, XtfToGpkg und Native bleiben ausserhalb des MVP-Auftrags.

## Umsetzung

Arbeite in den Meilensteinen aus Kapitel 10. Halte A, B und C separat nachweisbar. TC-P01 bis TC-P09 sind erhaltene Zieltests für den späteren Publisher-Port und blockieren diese drei Stufen nicht. Liefere echte Jobs und echte Ergebnisse für die zugesagten Fähigkeiten; benutze lokale Fixtures und Testdatenbanken.

Die vollständigen MVP-Rezepte werden aus tatsächlich ausführbaren Testressourcen dokumentiert. Eine noch ausstehende Prüfung mit Fachanwendern wird als solche benannt und nicht durch den Selbsteindruck des Coding-Agenten ersetzt.

Standardprüfung ist `./mvnw verify`. Verwende die TC-A-/TC-K-/TC-GR-/TC-L-Ergänzungen aus Kapitel 09 für die jeweilige tatsächlich gelieferte Stufe; künftige OTel- und Paketfälle werden nur mit dem betreffenden Profil Pflicht. Die Releasepipeline ergänzt die implementierten Integrations- und Paketierungsprofile. Eine Testfall-ID bezeichnet ein fachliches Orakel, keine Pflicht zu einer Testklasse pro Tabellenzeile.

Beachte die jeweils vorhandenen Anweisungen im Implementierungsrepository. Übernimm keine durch 3.0/4.0/4.1 ersetzten Vorgaben früherer Versionen. Die verbindlichen Signaturen stehen in Kapitel 14; die aktuelle Anwender-DSL bleibt in Kapitel 04. Das betrifft insbesondere die frühere Methodenschreibweise, fehlende Parameterdeklarationen, Publisher in MVP-B und eine vermeintlich geschlossene Standardtaskliste. Insbesondere soll aus dem Publisher-Audit kein neuer Publikationsserver entstehen.

Fehlende Fähigkeiten scheitern ausdrücklich. Ein noch nicht angebotener Publisher braucht keine Stub-Operation; ein GPKG aus ili2gpkg ist kein GeoTools-Nachweis, und ein JAR mit fehlenden Services ist kein fertiges Auslieferungspaket.

Ein Laufzeitfehler kann bereits erfolgte fachliche Änderungen hinterlassen. Berichte Ursache und taskseitig bereitgestellte Diagnosen; erfinde weder Rollbackgarantien noch einen generischen Reparaturalgorithmus.

## Abschlussbericht

Nenne implementierte Funktionen, tatsächlich ausgeführte Prüfungen, nicht ausgeführte Integrationspfade und bekannte Grenzen. Änderungen am Umfang oder an normativen Verträgen werden als Architekturentscheidung dokumentiert. Strategische Optionen sind kein Auftrag, sie ungefragt in den MVP einzubauen.
