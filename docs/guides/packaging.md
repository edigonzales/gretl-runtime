# Jobpaketierung

Aus der [installierten Distribution](../development/build.md) lässt sich ein
Groovy-Job vorkompilieren und als eigenständiges JAR auf Java 25 ausführen.

```sh
bin/gretl package examples/minimal-action/job-package.json --output /tmp/job.jar
java -jar /tmp/job.jar --param output=/tmp/message.txt
```

Das Paket enthält die Standardlaufzeit, alle regulären Taskbibliotheken, die
kompilierten Jobklassen, deklarierte Ressourcen und explizite zusätzliche
Abhängigkeiten. Die Felder `taskModules` und `standardFunctions` wurden entfernt.
Die Paketierung kompiliert den Job, ohne Jobtasks auszuführen.

## Einzelfile und Projekte

Für ein Einzelscript verwenden Schemas 1 und 2 das Feld `entryScript`. Ein
Projekt verwendet Schema 3 und `"settingsFile": "settings.gretl"`. Beide
Formate erfordern `name`, `version` und `runtimeVersion`. Die optionalen
Listen `sources` und `resources` geben Pfade relativ zum Deskriptor an.
Includes und Ressourcen müssen innerhalb des Projektverzeichnisses liegen.

## Externe Abhängigkeiten

`dependencies` akzeptiert genaue Maven-Koordinaten. Der Packager verwendet
für zusätzliche Bibliotheken beim Paketieren den Maven-Resolver über
`mvn`. Das erzeugte Job-JAR läuft eigenständig mit Java 25. Zusätzliche
Bibliotheken werden während der Paketierung auf Klassen- und
Ressourcenkonflikte geprüft.

## Validatorfunktionen

Die Standarddistribution und Job-JARs enthalten die mitgelieferten
Validatorfunktionen. Die einzelnen Java-Taskbibliotheken bleiben eigenständig
nutzbar.
