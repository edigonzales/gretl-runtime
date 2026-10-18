# 05 — Publisher als gewöhnlicher Custom-Task

**Version:** 4.1, 18. Oktober 2026. **Status:** erhaltener Zielvertrag für den ersten Publisher-Port nach dem MVP. **Normativ für:** die schon jetzt gültige Grenze zwischen Runner und Publisher-Modul sowie die spätere Umsetzung dieses abgegrenzten Ports. Der fachliche Backlog in Abschnitt 9 ist ausdrücklich nicht Bestandteil seines ersten Nachweises.

**Liefergrenze:** Die Umsetzung und Bereitstellung des Publishers gehören nicht zum MVP. Es entstehen dort kein Publisher-Code, kein registrierter Platzhalter, kein Publisher-Artefakt und keine ausschliesslich dafür benötigten Dependencies. Die Publisher-Modultests werden beim späteren Liefermeilenstein erforderlich, nicht für die Freigabe von A, B oder C. Sämtliche in diesem Dokument beschriebenen Fachregeln, Erkenntnisse und späteren Tests bleiben erhalten. Publisher bleibt strategisch wichtig und kann nach seinem eigenen Nachweis regulär in die Standarddistribution aufgenommen werden; er ist kein zwangsweise externes Produkt.

Der [Bestandsaudit](02-publisher-bestandsanalyse.md) erklärt die vorhandene Implementierung und ihre Risiken. Er begründet keinen neuen Publikationsdienst im Core. Für Taskausführung und CLI gelten ausschliesslich die allgemeinen Verträge aus [03](03-runtime-vertraege.md), [04](04-job-dsl-und-custom-tasks.md) und [06](06-betrieb-und-auslieferung.md).

## Technische Einordnung 4.0

Der spätere Publisher verwendet ohne Sonderstellung die Java-SPI aus [14](14-java-api-vertraege.md), den Invoker aus [15](15-runner-klassendesign.md) und den Loggingvertrag aus [16](16-logging-und-telemetrie.md). Seine internen Phasen dürfen Fortschritt, Artefakte und konkrete Fehlerdetails melden. Keine Publikationszustände im Core, keine neue CLI und kein automatischer Wiederanlauf entstehen dadurch. Seine Umsetzung und die TC-P-Freigabe bleiben nach dem MVP.

## 1. Architektonische Grenze

**Publisher ist eine normale Taskbibliothek, auf derselben Ebene wie SqlExecutor.** Er verwendet `OperationType`, `ConfigBuilder`, `OperationHandler` und den allgemeinen `OperationProvider`. Seine Konfiguration wird beim Taskstart ausgewertet. Sein Handler führt normalen Java-Code aus und liefert ein taskeigenes Resultat oder eine Exception. Die Standardausführung erfolgt innerhalb des Jobprozesses.

| Im allgemeinen Runner | Im Publisher-Modul |
|---|---|
| Taskregistrierung, Abhängigkeiten und Zielauswahl. | Auswahl und fachliche Bedeutung der Publikationsdaten. |
| Laufzeitparameter, Ergebnisse und Arbeitsbereiche. | Modellauflösung, Validierung, Formate und Archivinhalt. |
| Fehler-, Catch-, Cleanup- und Cancellation-Semantik. | Eigener Erfolgsvertrag, Teilwirkungen und mögliche Wiederherstellung. |
| Allgemeine Logs und `ctx.report(Diagnostic)`. | Fachliche Diagnose, Historie und allfällige Metadatenintegration. |
| Ausführung der registrierten Taskimplementierung. | Bibliotheken, Dateiziele, Transaktionen und interne Komponenten. |

Die Runtime referenziert keine Publisher-Requests, -Resultate, Publikationsgenerationen, Regionen oder SIMI-Typen. Sie besitzt keine Publikationsregistry und prüft keine offenen fachlichen Verpflichtungen. Es gibt keine Publisher-spezifischen CLI-Kommandos, Exitcodes oder Konfigurationsabschnitte. Ein Runtimepaket ohne Publisher-Modul muss andere Jobs unverändert ausführen können.

Eine spätere interne Aufteilung des Publishers darf gewöhnliche Java-Helfer, andere Fachbibliotheken oder begründet einen separaten Prozess verwenden. Ein eigener Host-Koordinator, ein Codec pro Custom-Task und ein Vorbereitungsworker sind keine Voraussetzungen der SPI. Ebenso muss der Jobautor interne Publisher-Schritte nicht als öffentliche DAG-Knoten verkabeln.

## 2. Zweck und Umfang des späteren Portierungsnachweises

Der erste Publisher-Meilenstein nach dem MVP soll belegen, dass auch seine umfangreiche vorhandene Fachlogik über die allgemeine Schnittstelle ausführbar ist. Dafür wird eine kontrollierte Teilmenge des bestehenden Publishers portiert. Es wird weder seine vollständige Funktionspalette noch gleichzeitig ein anderes Publikationsmodell eingeführt. Dieser Meilenstein ist unabhängig von Job-Fat-JAR und Native Image: Die fachliche Priorisierung des Publishers muss auf deren Umsetzung nicht warten.

| Gegenstand | Umfang des Nachweises |
|---|---|
| Quelle | Genau eine lokale XTF-Datei. |
| Modelle | Explizites lokales Modellverzeichnis mit dem für das Fixture erforderlichen Bestand. |
| Validierung | Tatsächlicher ilivalidator-Aufruf; optional eine lokale Validierungskonfiguration. |
| Ausgabe | Transfer-ZIP, Validierungslog, gegebenenfalls Validierungskonfiguration und benötigte Modelldateien gemäss bestehendem Layout. |
| Ziel | Gewöhnliches lokales Dateisystem und ein ausdrücklich gewähltes Zielverzeichnis. |
| Historie | Bestehende `aktuell/`-/`hist/`-Konvention und Tagesbehandlung; keine neue Speicherpolitik. |
| Benutzerformate | `userFormats=false`. |
| Ausführung | Sequenzieller normaler Custom-Task, ohne eigenen Workerzwang. |

DB-Quellen, ITF, Regionen, SFTP, S3, produktive SIMI-Anbindung, GPKG/SHP/DXF/Geobau und automatische Historienbereinigung sind in diesem Publisher-Port nicht angeboten. Auch eine bestehende Hilfsmethode, die solche Fähigkeiten kennt, macht sie nicht automatisch zu unterstützten Optionen. Verlangt die Konfiguration eine nicht angebotene Fähigkeit, scheitert die Requestvalidierung vor dem Beginn vermeidbarer fachlicher Seiteneffekte.

Die technische GeoTools-Qualifikation bleibt ein eigener MVP-Nachweis im gesonderten Entwicklungs-/CI-Profil, ohne öffentlichen Format-Task und ohne allein dadurch zusätzliche JARs in der Standarddistribution zu verlangen. Eine gesonderte XtfToGpkg-Operation ist eine mögliche unabhängige Erweiterung nach dem MVP und benötigt vor ihrer Lieferung einen konkreten Taskvertrag. Beide Nachweise wären kein Beleg, dass der Publisher bereits entsprechende Benutzerformate unterstützt. Umgekehrt muss eine neue Formatoperation nicht erst Teil des Publishers werden, um im Job nutzbar zu sein.

## 3. Vorläufige Task-API

Die API des späteren Ports bleibt flach und benennt unmittelbar benötigte Werte. Sie ist eine neue Gradle-freie Oberfläche; `Publisher` in diesen Beispielen bezeichnet den von seiner später gelieferten Taskbibliothek registrierten Typ, keine alte `DefaultTask`-Klasse. Die Beispiele in diesem Kapitel sind nach dem MVP umzusetzende Zielbeispiele und laufen ohne diese Bibliothek ausdrücklich nicht.

| Einstellung und Typ | Vertrag |
|---|---|
| `dataFile`: `Path` | Erforderliche lesbare reguläre XTF-Datei. |
| `targetDirectory`: `Path` | Erforderliches lokales Ziel-Root; Ausgabe wird darunter anhand von `dataIdent` angelegt. |
| `dataIdent`: `String` | Erforderliche Fachkennung und Namensbestandteil. Punkte sind erlaubt; Pfadtrenner sowie `.` und `..` als ganze Kennung sind unzulässig. |
| `modelDirectory`: `Path` | Erforderliches lesbares lokales Modellverzeichnis für den angebotenen Modellumfang. |
| `validationConfig`: `Path` | Optionale lesbare lokale Konfigurationsdatei. |
| `userFormats`: `boolean` | Default `false`; `true` wird in dieser Teilmenge mit verständlicher Diagnose zurückgewiesen. |

Der Task beschreibt seine Einstellungen, Pflichtangaben, Typen, Defaults und Ergebnisse über denselben Metadatenvertrag wie andere Taskbibliotheken. Die DSL nimmt die Zuweisungen entgegen; der Builder validiert die aufgelösten Werte, erzeugt einen eigenen unveränderlichen Request und hält keine Groovy-Closure oder veränderliche Gradle-Property zurück. Pfade und Kennungen werden vor der ersten Mutation überprüft. Unbekannte Einstellungen sind verständliche Fehler mit Task- und Angabebezug. Mehrere öffentliche Quellvarianten oder ein allgemeiner Store-/Sinkresolver werden nicht vorsorglich ergänzt.

```groovy
parameter('eingabe', type: 'path', required: true,
          description: 'XTF-Datei')
parameter('ausgabe', type: 'path', required: true,
          description: 'Publikationsverzeichnis')

task('publizieren', Publisher) {
    dataFile = parameter('eingabe')
    targetDirectory = parameter('ausgabe')
    dataIdent = 'ch.so.demo'
    modelDirectory = file('models')
    userFormats = false
}

defaults 'publizieren'
```

`file('models')` bezieht sich auf den Jobressourcenbereich. Die beiden deklarierten Pathparameter werden nach dem allgemeinen Vertrag gegen das beim Start festgehaltene Arbeitsverzeichnis aufgelöst; das Ziel liegt ausserhalb eines allfälligen Job-JARs. Derselbe Parametervertrag wird bei Scriptstart und paketiertem Job verwendet. Falls eine lokale Validierungskonfiguration benötigt wird, ergänzt der Job `validationConfig = file('validation.ini')`; sie muss dann tatsächlich vorhanden und lesbar sein.

Wenn ein vorheriger Task die Transferdatei erzeugt, deklariert der Publisher `dependsOn` auf diesen Schritt und verwendet in seiner Konfigurationsclosure dessen Resultat. Ein Ausschnitt für einen späteren Job mit einer passenden Exportbibliothek lautet:

```groovy
task('publizieren', Publisher, dependsOn: 'exportieren') {
    dataFile = result('exportieren').file
    targetDirectory = parameter('ausgabe')
    dataIdent = 'ch.so.demo'
    modelDirectory = file('models')
    userFormats = false
}
```

Der Ausschnitt setzt den zuvor definierten Schritt `exportieren` und den deklarierten Parameter `ausgabe` voraus. Er behauptet weder einen Exporttask noch den Publisher im MVP-Lieferbestand. Die Konfigurationsclosure läuft erst nach dem Producer. Ein zusätzliches `doFirst` ist dafür nicht nötig; die fachliche Reihenfolge bleibt ausdrücklich sichtbar.

## 4. Ausgabe und Resultat

Der Port erhält das relevante bestehende Layout. Unter `targetDirectory/dataIdent/aktuell/` liegt `dataIdent.xtf.zip`. Dieses ZIP enthält die Transferdatei unter `dataIdent.xtf`, `validation.log` und bei entsprechender Konfiguration `validation.ini`. Die ermittelten Modelldateien liegen separat unter `aktuell/meta/`; dort steht auch `publishdate.json`. Die bestehenden Methoden bestätigen diese Trennung zwischen ZIP-Inhalt und Metadatenverzeichnis. [Archivbildung](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L537-L612) · [Metadaten und Dateiersetzung](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L657-L745)

Nach normalem vollständigem Abschluss liefert der Handler einen `PublisherResult` aus seinem eigenen Modul. Er enthält das aktuelle Publikationsverzeichnis und `publishedFiles`: eine unveränderliche Liste der absoluten Pfade der in diesem Aufruf bereitgestellten aktuellen Dateien. Die Auflistung umfasst Archiv und Metadaten; historische Dateien und interne Arbeitsdateien werden nicht als neue Publikationsausgabe ausgegeben. Es werden keine offenen Streams, DirectoryStreams oder sonstigen lebenden Dateisystemressourcen zurückgegeben.

Der spätere Provider beschreibt die Ausgänge `currentDirectory` (`Path`) und `publishedFiles` (`List<Path>`) ausdrücklich über `OutputField` gemäss Kapitel 04. Beide sind bei Erfolg nicht null; `currentDirectory` ist der absolute aktuelle Publikationsordner. Damit funktioniert beispielsweise `result('publizieren').publishedFiles` über dieselbe lesende Ergebnisoberfläche wie `result('download').file`. Diese Metadaten begründen keine neue Speicherung oder automatische Serialisierung der Publikation.

Der erste Port besitzt kein `publishedRegions`-Feld. Er implementiert keinen Regionenmodus und erfindet daher weder eine Region `allparts` noch eine fachlich missverständliche leere Änderungsliste. Sobald Regionen tatsächlich angeboten werden, erhält das Modul einen ausdrücklich dokumentierten Resultatvertrag. Die bestehende Bedeutung „diesmal publizierte Regionen“ muss für Migrationen erhalten beziehungsweise bewusst geändert werden; sie bezeichnet nicht alle weiterhin vorhandenen Regionen.

Ein fehlgeschlagener Task stellt kein normales Erfolgsresultat bereit. Diagnose über bereits erfolgte Wirkungen darf trotzdem vorhanden sein. Die Runtime serialisiert Resultatobjekte nicht automatisch in Logs oder Reports. Ausdrücklich freigegebene Informationen gehen über `ctx.report(Diagnostic)`; es werden nur ausgewählte, geheimnisfreie Details und erlaubte Artefaktverweise übergeben. Ein Resultatpfad beschreibt vorhandene Dateien, keine Zusage ihrer unbegrenzten Unveränderlichkeit.

## 5. Interner Ablauf und Portierungsregeln

Der Handler baut seine Konfiguration für jeden Aufruf neu auf. Er benutzt den bereitgestellten Arbeitsbereich für temporäre Dateien und besitzt alle selbst geöffneten Ressourcen. Modell-/Validierungseinstellungen werden aufrufbezogen erstellt. Logger, Validator, Streams und gegebenenfalls intern verwendete Helfer werden nicht als mutable Taskinstanzen zwischen Jobläufen geteilt.

Der fachliche Ablauf bleibt für die angebotene Teilmenge nachvollziehbar: Eingabe und Optionen prüfen, im bisherigen Stagingbereich vorbereiten, XTF wirklich validieren, Archiv vollständig schreiben, benötigte Modelle und Datumsmetadaten bereitstellen, die vorhandene Tages-/Historienlogik ausführen und das Resultat erst nach normalem Abschluss erzeugen. Konkrete Methodenzerlegung bleibt dem Modul überlassen. Die Engine darf unterstützte Fachbibliotheken unmittelbar aufrufen.

Ein begrenzter Adapter um portierte `PublisherStepOld`-Logik ist zulässig. Zu entfernen beziehungsweise zu ersetzen sind Gradle-Properties, `Project.file`, das gemeinsame `buildDir`, Gradle-Exceptionmapping und eine unnötige Abhängigkeit auf Gradle-Loggererkennung. Das bestehende Erzeugen des Publikationsdatums beim Taskstart bleibt erhalten. Für reproduzierbare Modultests wird die intern verwendete Zeitquelle injizierbar; sie erfordert keine neue fachliche CLI-Option.

Konkrete Ressourcenfehler dürfen beim Port nicht als Erfolg verschwinden. Beispielsweise protokolliert die alte Archivmethode einen Close-Fehler nur. Der Port propagiert einen fehlgeschlagenen Archivabschluss und bewahrt einen bereits bestehenden Primärfehler. Das ist eine begrenzte Korrektur am Task, keine neue Speicherarchitektur. Das tatsächlich geschlossene ZIP wird im Integrationstest vollständig gelesen.

Der unterstützte lokale Modellbestand muss für das Fixture vollständig sein. Ein fehlendes erforderliches Modell führt zu einer verständlichen Fehlermeldung. Der Port lädt dafür keine Taskbibliotheken oder beliebigen Erweiterungen nach. Fachliche Modellrepository-Funktionen ausserhalb des angebotenen lokalen Umfangs bleiben eine spätere Capability.

## 6. Fachliche Risiken bleiben sichtbar

Das bestehende Layout besitzt keine nachgewiesene atomare Gesamtveröffentlichung. Der alte Ablauf verschiebt oder löscht den bisherigen aktuellen Stand und verschiebt danach den neuen Stand. Bei gleichem Tagesdatum wird der bisherige Stand ersetzt. Eine neue Fassade macht daraus weder eine gemeinsame Transaktion noch einen crashsicheren Pointerwechsel. [Bestehende Promotion](https://github.com/sogis/gretl/blob/a9222dc0ffd08f8f76570e998101db041822df71/gretl/src/main/java/ch/so/agi/gretl/steps/PublisherStepOld.java#L694-L724)

Der spätere erste Publisher-Port behauptet deshalb keine neue Atomarität, universelle Wiederholbarkeit oder Unterstützung konkurrierender Writer auf dieselbe Bereitstellung. Er wird mit einem aktiven Writer pro Ziel und unveränderter Eingabe während des Aufrufs qualifiziert. Mehrere voneinander unabhängige Jobs können dieses Ziel trotzdem gleichzeitig wählen; der sequenzielle Runner verhindert das nicht. Eine weitergehende Zusage benötigt Arbeit und Tests im Publisher-Modul oder eine ausdrücklich organisierte betriebliche Serialisierung.

Diese Einschränkung bedeutet nicht, dass Teilfehler nebensächlich wären. Scheitert die Validierung vor der Dateiersetzung, muss die Diagnose diesen frühen Fehler zeigen. Scheitert eine spätere Aktion, darf der Task nicht behaupten, es sei nichts geschrieben worden. Das Modul kann die zuletzt erreichte Phase sowie bekannte Dateipfade melden. Unklarer Zustand wird als solcher beschrieben, ohne einen nicht belegten erfolgreichen Abschluss zu erfinden.

Das bestehende Layout bleibt für den angebotenen Umfang überprüfbar kompatibel. Weitergehende Versprechen für NFS, SFTP, bestehende produktive SIMI-Abläufe oder vollständige Jobmigration folgen daraus nicht. Änderungen an URL-Struktur, Historienpolitik oder fachlicher Bedeutung werden später als eigene Publisher-Änderungen beurteilt.

## 7. Fehler und bewusstes Catch

Eine unbehandelte Exception aus dem Publisher ist derselbe Runtime-Fall wie eine unbehandelte Exception aus SqlExecutor: Task `FAILED`, allgemeiner Taskfehlerexit `10`, keine normalen abhängigen Tasks und kein automatischer Retry. Die Runtime verspricht kein Rollback bereits erfolgter Wirkungen. Eine tatsächliche Cancellation verwendet den allgemeinen Cancellation-Vertrag; sie hebt einen bereits erfolgten Dateischreibvorgang nicht auf.

Ein taskeigener Fehler darf einen verständlichen Fachcode und Informationen zur erreichten Phase tragen. Der Core interpretiert solche Fachcodes nicht und leitet daraus keine besondere Priorität beim Jobabschluss ab. Soll Diagnose einen späteren Fehler im umgebenden Script überleben, gibt der Task sie bei Vorliegen über die allgemeine Berichtsoberfläche frei. Die Freigabe ist ein Diagnosevorgang, keine Registrierung einer vom Core zu erfüllenden Pflicht.

Bei synchronem Aufruf in einer Action gelten normale Sprachregeln. Fängt der Jobautor eine Publisher-Exception, behandelt sie bewusst und beendet den Action-Body normal, kann dieser Task erfolgreich sein. Der fehlgeschlagene innere Aufruf bleibt in der Diagnose erkennbar; der Erfolg des äusseren Bodys ist dadurch nicht widersprüchlich. Es werden zwei unterschiedliche Ausführungsscopes berichtet. Wirft der Autor die Exception weiter, scheitert die Action.

Der Runner darf einen bewusst erfolgreichen Body nicht durch eine versteckte Publisher-Registry nachträglich in `FAILED` umwandeln. Wer nach einem behandelten Fehler weitere Facharbeit startet, definiert diese Entscheidung im Jobcode. Das ist dieselbe Verantwortung wie beim Fangen eines SQL- oder HTTP-Fehlers. Eine automatische fachliche Konsistenzprüfung gehört nicht zur allgemeinen Taskausführung.

Auch optionale und erforderliche Nacharbeiten sind Aufgabe des Taskvertrags. Ein künftiger Publisher darf beispielsweise Historienbereinigung als Warnung behandeln und fehlgeschlagene erforderliche Bereitstellung als Fehler. Die allgemeine Runtime benötigt dazu keine Begriffe wie Publikationsgeneration, Metadatensink oder ausstehende Zustellung.

## 8. Erhaltene Abnahme für die spätere Lieferung dieses Moduls

Die vorgesehenen Testfälle TC-P01 bis TC-P09 werden in [09-tests-und-abnahme.md](09-tests-und-abnahme.md) als spätere Publisher-Abnahme geführt. Sie sind keine Gates für den MVP oder die optionale Paketierung. Sobald der hier spezifizierte Port angeboten wird, müssen sie insbesondere Folgendes unabhängig prüfen:

- Start über denselben Provider-/Handlerpfad wie ein separat geschriebenes anderes Task-JAR; kein Core-Sonderzweig für Publisher.
- Echte erfolgreiche lokale XTF-Validierung; Archivinhalt, Modelldateien und Datumsmetadaten entsprechen dem dokumentierten Layout.
- Fehlendes beziehungsweise ungültiges XTF und fehlendes Modell scheitern verständlich; der bisherige aktuelle Stand bleibt bei einem Fehler vor der Ersetzungsphase unverändert.
- Zwei kontrollierte Läufe prüfen die bisherige Tages-/Historiensemantik mit fester Zeitquelle, ohne daraus neue Crashgarantien abzuleiten.
- Nicht angebotene Optionen werden zurückgewiesen; `userFormats=true` erzeugt keinen scheinbar erfolgreichen Teiloutput.
- Archiv-, Ressourcen- und Cleanupfehler werden korrekt propagiert; notwendige Arbeitsartefakte bleiben bis zur vorgesehenen Nutzung verfügbar.
- Laufzeitresultate können einen normalen Nachfolger konfigurieren. Bewusstes Catch in einer Action verhält sich wie bei jeder anderen Operation.

Der Core wird bereits im MVP mit einem unabhängigen Testtask geprüft, der eine beobachtbare Wirkung erzeugt und danach scheitert. So wird die allgemeine Fehler-/Retrysemantik nicht anhand eines noch nicht gelieferten Publisher-Codes definiert. Die tatsächlichen INTERLIS- und SQL-Pfade prüfen zusätzlich Fachintegration und Ressourcenverhalten. Eine spätere neue Publisher-Garantie erhält eigene Modultests und verändert die Runner-Abnahme nicht automatisch. Das Verschieben der Publisher-Tests ist weder ein bestandener Publisher-Nachweis noch eine Streichung seiner späteren Abnahmepflicht.

## 9. Separater fachlicher Backlog

Der Audit liefert sinnvolle Kandidaten für spätere Publisher-Arbeit. Sie werden nach tatsächlichem Bedarf priorisiert und nicht als bereits beschlossene MVP-Funktionen behandelt.

| Thema | Eigenständig zu entscheidende Arbeit |
|---|---|
| Vollständiger Port | DB, ITF, Regionen, Benutzerformate, entfernte Ziele und SIMI mit vorhandenen Referenzjobs qualifizieren. |
| Regionen | Auswahl, leere Mengen, Teilersatz, veraltete Formate und Vollständigkeit der Modelldateien präzisieren. |
| Historie und Sichtbarkeit | Tagesersetzung, konkurrierende Writer und Wiederherstellung verbessern; bei Bedarf ein anderes Layout prüfen. |
| Metadaten | Reihenfolge von SIMI-Meldung und Beipackzettelbezug klären; tatsächlich mögliche Teilwirkungen und Wiederholungen behandeln. |
| Formate | CRS, numerische Genauigkeit, Textkürzung, Modellprofile und Bibliotheksupdates fachlich testen. |
| Wiederverwendung | Export oder Formatkonvertierung auch als unabhängige Tasks bereitstellen, wenn Jobs sie ohne Publikation benötigen. |

Unveränderliche Generationen, ein Manifest, ein dauerhafter Zustellauftrag oder ein Wiederanlaufverfahren können sich bei dieser Arbeit als zweckmässig erweisen. Sie wären dann Interna beziehungsweise öffentliche Fähigkeiten des Publisher-Moduls mit einem eigenen Vertrag. Der aktuelle MVP fordert keines dieser Verfahren.

Falls eine solche Bibliothek später eine Wartungsoperation anbietet, kann ein gewöhnlicher Groovy-Wartungsjob diese über die Task-SPI aufrufen. Auch dafür entsteht keine spezielle CLI-Syntax. Nach Lieferung des Publishers kann ein darauf angewiesenes Job-Fat-JAR ihn nach den allgemeinen Regeln verpacken; andere Jobs benötigen ihn nicht. Eine mögliche Native-Variante wird anhand der tatsächlich benutzten Bibliotheken geprüft. Beides verleiht dem Publisher keine neue Rolle im Core und ist keine Voraussetzung seiner eigenen fachlichen Lieferung.

