# GRETL-Referenzdokumentation

`reference.adoc` ist die redaktionelle Single-Page-Referenz für Jobautoren und
Betrieb. Sie enthält Einstieg, Installation, Job-DSL, CLI, Betrieb,
Taskbeschreibungen, Beispiele und Ergebnisfelder. Die thematische Übersicht
verlinkt auf 44 alphabetisch geordnete Taskkapitel.

## Quellen und Zuständigkeiten

- Fachliche Beschreibungen und Beispiele basieren
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
- Schrift, Farben und Codegestaltung verwenden die unveränderten Biblios-Defaults.
  [STYLE_GUIDE.md](STYLE_GUIDE.md) beschreibt technische Rollen und die wenigen Layoutregeln.

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

Beide Konfigurationen müssen neben `assets/` und `templates/` liegen, da Thoth
die Overrides relativ zur Konfigurationsdatei auflöst. Eine Konfigurationskopie
in `/tmp` würde die lokalen Layoutregeln und deren Stylesheet-Link nicht übernehmen.

## Breite Tabellen und Taskbeispiele

Der GRETL-Renderer setzt die AsciiDoc-Rolle `gretl-task-properties`, die in
HTML als Klasse der Tabelle erscheint. `assets/gretl-layout.css` ergänzt nur
Breiten- und Umbruchregeln sowie einen kleinen Abstand für Kapitelanker;
die Standard-Stylesheets werden nicht ersetzt:

- Der Artikel verwendet die verfügbare Inhaltsbreite neben der Navigation.
- Property-Tabellen (`gretl-task-properties`), Ergebnisfeldtabellen
  (`gretl-task-results`) und Codeblöcke im Abschnitt `gretl-task-reference`
  verwenden dieselbe volle Inhaltsbreite. Das gilt auch für die Prism-Toolbar.
- Lauftext, Hinweise und gewöhnliche Tabellen bleiben maximal `72ch` breit.
  Codeblöcke ausserhalb der Taskkapitel behalten ebenfalls diese Begrenzung.
- Beide Referenztabellen verwenden eine erste Spalte von 30%, eine flexible
  Beschreibungsspalte und eine automatisch nach dem Inhalt bemessene Statusspalte.
  «Erforderlich», «Nullable» und die Statuswerte bleiben jeweils in einer Zeile.
- Lange Feldnamen und Beschreibungen dürfen auf schmalen Viewports umbrechen.
  Lange Codezeilen scrollen bei Bedarf innerhalb des Codeblocks.

`templates/layout.ftl` ist eine Kopie des gebündelten Templates aus dem verwendeten
Thoth-Biblios-JAR mit genau einem zusätzlichen Stylesheet-Link auf
`gretl-layout.css` nach dem Standard-Stylesheet. Bei einem Thoth-Update die
Vorlage mit dem gebündelten Template abgleichen und nur diesen Link ergänzen.
Biblios-Code und globale Templates bleiben unverändert. Die standardmässigen
`styles.css`, `prism-overrides.css` und Fonts werden aus Biblios übernommen.
Lokale Schrift-, Farb- oder Prism-Overrides sind nicht vorgesehen.

Nach Änderungen an Overrides sauber mit `build --clean` rendern, damit entfernte
Assets nicht in der Ausgabe zurückbleiben. Gebaute Seiten und Thoth-Caches werden
nicht eingecheckt.

## Abnahme und Pflege

1. Tabellen generieren und `./mvnw verify` ausführen.
2. Dienstabhängige Rezepte mit `./mvnw -Pbacking-services verify` prüfen.
   Dafür werden Docker sowie PostgreSQL/PostGIS-, FTP- und S3-Testcontainer benötigt.
3. Den aktuellen Working Tree mit Thoth rendern. Keine ungelösten Includes,
   fehlenden Anker oder defekten lokalen Ressourcen akzeptieren.
4. Bei 1440px, 1024px und 390px Breite alle 44 Taskkapitel kontrollieren:
   Property- und Ergebnisfeldtabellen sowie Codebeispiele haben dieselbe Breite;
   Statusüberschriften und -werte sind einzeilig. Lauftext und Code ausserhalb
   der Taskkapitel bleiben auf `72ch` begrenzt. Kein horizontaler Seitenüberlauf,
   keine abgeschnittenen Zellinhalte, Navigation ohne Überlagerung,
   Biblios-Standardschriften geladen und Codeblöcke mit dem Standard-Prism-Styling
   und Copy-Button.
5. Bei neuen Taskarten Kurzbeschreibung, Feldtexte und Ergebnisse in beiden
   Sprachkatalogen vervollständigen und ein alphabetisches Kapitel mit
   Beschreibung, vollständigem Beispiel, Voraussetzungen und Grenzen ergänzen.

Eine ausgeführte lokale Probe ersetzt keine Plattform- oder Anwenderfreigabe.
Die Webseite und die Veröffentlichung der Referenz sind separate Aufgaben.
