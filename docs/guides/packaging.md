# Jobpaketierung

Aus der [installierten Distribution](../development/build.md) lässt sich ein
Groovy-Job vorkompilieren und als eigenständiges JAR auf Java 25 ausführen.

```sh
bin/gretl package examples/minimal-action/job-package.json --output /tmp/job.jar
java -jar /tmp/job.jar --param output=/tmp/message.txt
```

Das Paket enthält die Standardlaufzeit, alle regulären Taskbibliotheken, die
kompilierten Jobklassen, Jobmetadaten, deklarierte Ressourcen und explizite
zusätzliche Abhängigkeiten. Originalquellen, einschliesslich der
Projekteinstellungen, werden nicht automatisch mitgeliefert.
Die Paketierung kompiliert den Job, ohne Jobtasks auszuführen.

## Einzelfile und Projekte

Der Deskriptor verwendet `schemaVersion: 1` und genau eines der Felder
`entryScript` oder `settingsFile`. `name`, `version` und `runtimeVersion`
sind Pflicht. Die optionalen Listen `sources` und `resources` enthalten
Pfade relativ zum Deskriptor. `sources` ergänzt die Kompilierung.
Explizit unter `resources` aufgeführte Dateien wie etwa `.groovy`-Dateien
werden als Ressourcen mitgeliefert. Includes und Ressourcen müssen
innerhalb des Jobverzeichnisses liegen.

```json
{"schemaVersion":1,"name":"single","version":"1","runtimeVersion":"6.0.0-SNAPSHOT","entryScript":"job.groovy"}
```

```json
{"schemaVersion":1,"name":"pipeline","version":"1","runtimeVersion":"6.0.0-SNAPSHOT","settingsFile":"settings.gretl","resources":["prepare/data"]}
```

`META-INF/gretl/job.json` enthält Schema 1, Jobname, Jobversion,
Runtimeversion, die Paketressourcen und ein kompiliertes Bundle. Das
Bundle enthält den Einstieg je Projektpfad, Klassen und Quellzuordnung,
gegebenenfalls Apply-Kanten und Projekt-Default-Ziele. Die Metadaten
erhalten die SHA-256-Quellidentität und erlauben zusammen mit den
kompilierten Klassen die Zuordnung von Diagnosen zu Quelldateien und Zeilen.
`java -jar` lädt die Klassen direkt aus dem JAR.

## Externe Abhängigkeiten

`dependencies` akzeptiert genaue Maven-Koordinaten, zum Beispiel
`"dependencies": ["org.apache.commons:commons-text:1.14.0"]`. Der Packager verwendet
für zusätzliche Bibliotheken beim Paketieren den Maven-Resolver über
`mvn`. Das erzeugte Job-JAR läuft eigenständig mit Java 25. Zusätzliche
Bibliotheken werden während der Paketierung auf Klassen- und
Ressourcenkonflikte geprüft.

## Validatorfunktionen

Die Standarddistribution und Job-JARs enthalten die mitgelieferten
Validatorfunktionen. Die einzelnen Java-Taskbibliotheken bleiben eigenständig
nutzbar.
