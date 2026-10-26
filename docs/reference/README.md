# GRETL-Referenzdokumentation

`reference.adoc` ist die redaktionelle Single-Page-Referenz für Jobautoren und
Betrieb. Sie enthält Einstieg, Installation, Job-DSL, CLI, Betrieb,
Taskbeschreibungen, Beispiele und Ergebnisfelder. Die thematische Übersicht
verlinkt auf 44 alphabetisch geordnete Taskkapitel.

## Quellen und Zuständigkeiten

- Fachliche Beschreibungen, Gestaltung, Prism-Anpassungen und Fonts basieren
  auf [gretl-next](https://github.com/edigonzales/gretl-next/tree/main/docs/reference),
  Ausgangsstand der Hauptdatei: Git-Blob `3250307aaa7e46aac1052ad31cf3a400b324fa27`.
  Alte Gradle-Beispiele und Aussagen werden gegen die Runtimeimplementierung
  geprüft und auf die Spezifikation 4.1 angepasst.
- `generated/task-*.adoc` enthalten ausschliesslich Eingabefeldtabellen aus
  `OperationType`/`InputField` der tatsächlich ausgelieferten Distribution.
  Sie werden nie von Hand bearbeitet. Tasktexte, vollständige Beispiele und
  Ergebnisübersichten stehen redaktionell in der Hauptdatei.
- CLI-Hilfe und Tabellen verwenden dieselben Klartextbeschreibungen aus den
  englischen/deutschen Sprachkatalogen. Keine AsciiDoc-Rollen in diese Texte
  schreiben. Der Renderer ergänzt die technischen Rollen.
- Vollständige Einstiegsbeispiele werden aus der Distribution eingebunden.
  Weitere Rezepte verwenden AsciiDoc-Tags in ausgeführten Integrationsjobs.
  Änderungen an diesen Rezepten werden deshalb mit denselben echten Fixtures
  geprüft. Erklärungsausschnitte werden als solche gekennzeichnet.
- [STYLE_GUIDE.md](STYLE_GUIDE.md) beschreibt Typografie und technische Rollen.

## Tabellen aktualisieren

Voraussetzung ist JDK 25 mit passendem `JAVA_HOME`. Vom Repository-Root:

```sh
./scripts/generate-task-docs.sh
./mvnw verify
```

Der erste Befehl baut die Scriptdistribution und erzeugt jede Tabelle neu.
Alternativ nach einem aktuellen Distributionsbuild:

```sh
./gretl-dist/target/dist/bin/gretl operations \
  --format adoc --lang de --output docs/reference/generated
```

Die Tabellen enthalten Feldname und Typ, Beschreibung, Pflichtstatus sowie
vorhandene Defaults, erlaubte Werte und Feldbeispiele. Eine fehlende
Metadatenvorgabe wird nicht als `null` oder als Default der Fachbibliothek
ausgegeben. Ergebnisfelder bleiben Teil des redaktionellen Taskkapitels.

Die Distributionsprüfung vergleicht Dateimenge und Inhalt mit einer frischen
Generierung und prüft alle Tabellen-Includes. Ein eigener Katalogtest prüft
alle registrierten Task-, Eingabe- und Ergebnisbeschreibungen in beiden
Sprachdateien ohne `ResourceBundle`-Fallback. Renderertests prüfen auch
Tabellenklasse, Sonderzeichen, Vorgaben und Secretmaskierung. Der Definitionscheck
lädt für jede Taskart das erste veröffentlichte Groovy-Beispiel, einschliesslich
der eingebundenen Tags, mit der echten CLI. Er ersetzt nicht die fachlichen
Ausführungsnachweise der Integrationsjobs.

## Lokal mit Thoth rendern

[Thoth Biblios](https://github.com/edigonzales/thoth) bleibt ein externes
Werkzeug und wird keine Maven-Abhängigkeit von GRETL. Benötigt wird ein
gebautes `thoth-biblios-<version>-all.jar` aus `../thoth`. Falls noch nicht vorhanden,
im Thoth-Repository `./gradlew :thoth-biblios:build` ausführen.

Vom GRETL-Repository-Root einmal die ignorierte lokale Konfiguration erstellen:

```sh
cp docs/reference/biblios.local.yml.example docs/reference/biblios.local.yml
```

Darin `content.sources[0].url` auf den absoluten Pfad dieses Repositories
setzen. Anschliessend, hier mit dem vorhandenen Thoth-Snapshot:

```sh
cd docs/reference
java -jar ../../../thoth/thoth-biblios/build/libs/thoth-biblios-0.0.1-SNAPSHOT-all.jar \
  build --config biblios.local.yml --use-local-working-tree --clean
```

`--use-local-working-tree` ist für uncommittierte Änderungen erforderlich.
Die Ausgabe liegt im GRETL-Repository unter `build/thoth-site/`.
Eine Vorschau mit automatischem Neubau startet so:

```sh
java -jar ../../../thoth/thoth-biblios/build/libs/thoth-biblios-0.0.1-SNAPSHOT-all.jar \
  serve --config biblios.local.yml --use-local-working-tree --port 8080
```

Die Referenz ist unter
`http://localhost:8080/gretl-runtime/main/index.html` erreichbar.
Für einen Build aus dem eingecheckten Remote-Stand wird die öffentliche
Konfiguration ohne Working-Tree-Option verwendet:

```sh
java -jar ../../../thoth/thoth-biblios/build/libs/thoth-biblios-0.0.1-SNAPSHOT-all.jar \
  build --config biblios.yml --clean
```

Beide Konfigurationen müssen neben `assets/` liegen, da Thoth die Overrides
relativ zur Konfigurationsdatei auflöst. Eine Konfigurationskopie in `/tmp`
würde die lokalen CSS- und Font-Overrides nicht übernehmen.

## Breite Property-Tabellen

Der GRETL-Renderer setzt die AsciiDoc-Rolle `gretl-task-properties`, die in
HTML als Klasse der Tabelle erscheint. `assets/styles.css` ersetzt über den
vorhandenen Biblios-Asset-Override das Stylesheet dieser Referenz:

- Der Artikel verwendet die verfügbare Inhaltsbreite neben der Navigation.
- Lauftext, Codeblöcke, Hinweise und gewöhnliche Tabellen bleiben maximal `72ch` breit.
- Nur `table.gretl-task-properties` verwendet die volle verfügbare Inhaltsbreite.
- Lange Feldnamen und Beispiele dürfen auf schmalen Viewports umbrechen.

Biblios-Code und globale Biblios-Templates/-Styles werden dafür nicht geändert.
`assets/prism-overrides.css` und `assets/fonts/` übernehmen die Codegestaltung
und Fonts der Vorlage. Gebaute Seiten und Thoth-Caches werden nicht eingecheckt.

## Abnahme und Pflege

1. Tabellen generieren und `./mvnw verify` ausführen.
2. Dienstabhängige Rezepte mit `./mvnw -Pbacking-services verify` prüfen.
   Dafür werden Docker sowie PostgreSQL/PostGIS-, FTP- und S3-Testcontainer benötigt.
3. Den aktuellen Working Tree mit Thoth rendern. Keine ungelösten Includes,
   fehlenden Anker oder defekten lokalen Ressourcen akzeptieren.
4. Bei 1440px, 1024px und 390px Breite kontrollieren: Tabellenklasse vorhanden,
   Property-Tabellen bei ausreichendem Platz breiter als Lauftext, Texte maximal
   `72ch`, kein horizontaler Seitenüberlauf, Navigation ohne Überlagerung,
   Fonts geladen und Codeblöcke mit Copy-Button.
5. Bei neuen Taskarten Kurzbeschreibung, Feldtexte und Ergebnisse in beiden
   Sprachkatalogen vervollständigen und ein alphabetisches Kapitel mit
   Beschreibung, vollständigem Beispiel, Voraussetzungen und Grenzen ergänzen.

Eine ausgeführte lokale Probe ersetzt keine Plattform- oder Anwenderfreigabe.
Die Webseite und die Veröffentlichung der Referenz sind separate Aufgaben.
