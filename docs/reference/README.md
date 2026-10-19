# GRETL-Referenzdokumentation

Die Referenz ist AsciiDoc und wird mit [Thoth Biblios](https://github.com/edigonzales/thoth)
gerendert. Sie besteht aus zwei Teilen:

- `reference.adoc` ist die redaktionelle Hauptdatei. Sie enthält die
  Einleitung, Task-Beschreibungen, Beispiele, Ergebnisse und Grenzen.
- `generated/task-*.adoc` enthalten **nur die Property-Tabelle** eines Tasks.
  Sie werden aus den Metadaten der ausgelieferten Task-Bibliotheken erzeugt
  und nicht manuell bearbeitet.

Die Hauptdatei bindet die Tabellen an der passenden Stelle manuell ein:

```adoc
=== SqlExecutor

...redaktioneller Text...

include::generated/task-sqlexecutor.adoc[]
```

## Generieren

Vom Repository-Root:

```sh
./scripts/generate-task-docs.sh
```

Der Generator verwendet die gepackte Scriptdistribution und damit genau den
Taskkatalog, der auch ausgeliefert wird. Test-Extensions erscheinen nicht in der
Referenz.

Alternativ kann die Distribution direkt aufgerufen werden:

```sh
./mvnw -q -pl gretl-dist -am package
./gretl-dist/target/dist/bin/gretl operations \
  --format adoc --lang de --output docs/reference/generated
```

## Neuen Task dokumentieren

1. Task-Metadaten implementieren und in der Distribution registrieren.
2. `./scripts/generate-task-docs.sh` ausführen.
3. In `reference.adoc` einen Abschnitt für den Task ergänzen:
   Beschreibung, Beispiele, Ergebnisse und Grenzen.
4. Die Tabelle am Ende des Abschnitts einbinden:
   `include::generated/task-<anchor>.adoc[]`.
5. `./mvnw verify` ausführen.

Die Distributions-IT prüft den Inhalt der Tabellen und dass jede Tabelle in
`reference.adoc` eingebunden ist und jede Include-Datei existiert. Bei
Abweichungen schlägt `./mvnw verify` fehl.

## Lokal rendern

Voraussetzung ist ein gebautes `thoth-biblios-<version>-all.jar`. Für den
Working Tree einmal `biblios.local.yml.example` nach `biblios.local.yml`
kopieren und den lokalen Repository-Pfad anpassen:

```sh
cd docs/reference
java -jar /pfad/zu/thoth-biblios-<version>-all.jar \
  serve --config biblios.local.yml --use-local-working-tree
```

Die erzeugte Website liegt unter `build/thoth-site/`.

## Reproduzierbar bauen

```sh
cd docs/reference
java -jar /pfad/zu/thoth-biblios-<version>-all.jar \
  build --config biblios.yml --clean
```

Der Build verwendet den eingecheckten Stand. Thoth ist ein externes Werkzeug und
keine Maven-Abhängigkeit dieses Repositories.
